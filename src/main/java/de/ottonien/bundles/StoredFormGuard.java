package de.ottonien.bundles;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Makes sure that no item in stored form (see {@link StoredItems}) stays outside of a bundle.
 *
 * <p>The regular way out, a right click in an inventory, is handled by {@link BundleListener} before
 * vanilla acts. The remaining ways hand the entry out the way it is stored: emptying a bundle in hand and
 * the destruction of a bundle item entity both end in a new item entity, and the creative inventory lets
 * the client place the entry directly. These are caught here. Every correction restores the original item
 * and splits it into stacks its original limit allows; the total amount never changes.
 */
public final class StoredFormGuard implements Listener {

    private final DebugLog debug;

    public StoredFormGuard(DebugLog debug) {
        this.debug = debug;
    }

    /**
     * Restores a marked item entity in place and spawns the pieces that exceed the original stack limit
     * next to it.
     *
     * @return true if the entity was changed
     */
    private boolean release(Item item, String cause) {
        ItemStack stack = item.getItemStack();
        if (!StoredItems.isMarked(stack)) {
            return false;
        }
        List<ItemStack> pieces = StoredItems.release(stack);
        item.setItemStack(pieces.get(0));
        for (ItemStack extra : pieces.subList(1, pieces.size())) {
            item.getWorld().dropItem(item.getLocation(), extra, spawned -> {
                spawned.setVelocity(item.getVelocity());
                spawned.setPickupDelay(item.getPickupDelay());
                spawned.setThrower(item.getThrower());
                spawned.setOwner(item.getOwner());
            });
        }
        debug.log("Rückwandlung (" + cause + "): " + describe(stack) + " -> " + pieces.size() + " x "
                + describe(pieces.get(0)));
        return true;
    }

    /** Every new item entity: bundle emptied in hand, destroyed bundle entity, any other drop. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        release(event.getEntity(), "ItemSpawnEvent");
    }

    /**
     * Fires before the spawn event. Handled separately because a plugin that cancels the drop gets the
     * stack of the entity put back into the inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        release(event.getItemDrop(), "PlayerDropItemEvent");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (release(event.getItem(), "EntityPickupItemEvent")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (release(event.getItem(), "InventoryPickupItemEvent")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (StoredItems.isMarked(stack)) {
                List<ItemStack> pieces = StoredItems.release(stack);
                inventory.setItem(slot, pieces.get(0));
                hand(player, pieces.subList(1, pieces.size()));
                debug.log("Rückwandlung (Join, Slot " + slot + ") bei " + player.getName() + ": "
                        + describe(stack) + " -> " + pieces.size() + " x " + describe(pieces.get(0)));
            }
        }
    }

    /**
     * Runs before {@link BundleListener}. A marked stack on the cursor or in the clicked slot is restored
     * and the click is discarded. In the creative inventory the client reports the stack it wants to put
     * into the slot; that stack is replaced.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (event.getClick() == ClickType.CREATIVE) {
            if (StoredItems.isMarked(cursor)) {
                List<ItemStack> pieces = StoredItems.release(cursor);
                event.setCursor(pieces.get(0));
                hand(player, pieces.subList(1, pieces.size()));
                debug.log("Rückwandlung (Kreativ-Inventar) bei " + player.getName() + ": " + describe(cursor)
                        + " -> " + pieces.size() + " x " + describe(pieces.get(0)));
            }
            return;
        }
        boolean changed = false;
        if (StoredItems.isMarked(cursor)) {
            List<ItemStack> pieces = StoredItems.release(cursor);
            player.setItemOnCursor(pieces.get(0));
            hand(player, pieces.subList(1, pieces.size()));
            debug.log("Rückwandlung (Cursor) bei " + player.getName() + ": " + describe(cursor) + " -> "
                    + pieces.size() + " x " + describe(pieces.get(0)));
            changed = true;
        }
        Inventory clicked = event.getClickedInventory();
        ItemStack inSlot = event.getCurrentItem();
        if (clicked != null && event.getSlotType() != InventoryType.SlotType.RESULT && StoredItems.isMarked(inSlot)) {
            List<ItemStack> pieces = StoredItems.release(inSlot);
            clicked.setItem(event.getSlot(), pieces.get(0));
            hand(player, pieces.subList(1, pieces.size()));
            debug.log("Rückwandlung (Slot " + event.getSlot() + ") bei " + player.getName() + ": "
                    + describe(inSlot) + " -> " + pieces.size() + " x " + describe(pieces.get(0)));
            changed = true;
        }
        if (changed) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /** Gives stacks to the player; what does not fit is dropped at the player's position. */
    private static void hand(Player player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private static String describe(ItemStack stack) {
        return stack.getAmount() + "x" + stack.getType() + "[max=" + stack.getMaxStackSize() + "]";
    }
}
