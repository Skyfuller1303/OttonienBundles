package de.ottonien.bundles;

import de.ottonien.bundles.core.BundleRules;
import de.ottonien.bundles.core.ExtractGesture;
import de.ottonien.bundles.core.InsertGesture;
import de.ottonien.bundles.core.StackView;
import de.ottonien.bundles.core.StoredEntries;
import de.ottonien.bundles.core.VanillaWeight;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * Takes over insertion into and removal from every bundle the plugin's point rules apply to: all bundles
 * if {@code bundleSize} differs from 64, otherwise only bundles that involve a configured item. Everything
 * else stays with vanilla; what vanilla hands out in stored form (see {@link StoredItems}) by other ways
 * is corrected by {@link StoredFormGuard}.
 *
 * <p>Verified against the Paper 1.21.11 build 132 server code: the insertion gesture is a plain left click
 * (see {@link InsertGesture}), and {@code ServerGamePacketListenerImpl.handleContainerClick} fires the
 * {@link InventoryClickEvent} for it even when vanilla considers the bundle full. In that case the reported
 * action is {@code NOTHING}, so the action is never used for the decision.
 */
public final class BundleListener implements Listener {

    private final JavaPlugin plugin;
    private final BundleConfigManager config;
    private final DebugLog debug;

    public BundleListener(JavaPlugin plugin, BundleConfigManager config, DebugLog debug) {
        this.plugin = plugin;
        this.config = config;
        this.debug = debug;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack slotItem = event.getCurrentItem();
        boolean cursorBundle = BukkitStackView.isBundle(cursor);
        boolean slotBundle = BukkitStackView.isBundle(slotItem);
        if (!cursorBundle && !slotBundle) {
            return;
        }
        if (event.getClick() == ClickType.CREATIVE) {
            // The creative inventory screen simulates the click on the client and only sends the resulting
            // stacks. There is no click the server could re-evaluate, so vanilla weights apply there.
            debug(event, player, "nicht abgefangen: Kreativ-Inventar, Client entscheidet (Vanilla-Gewichte)");
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            debug(event, player, "Vanilla: Klick außerhalb");
            return;
        }
        int slot = event.getSlot();
        boolean cursorEmpty = BukkitStackView.isEmpty(cursor);
        boolean slotEmpty = BukkitStackView.isEmpty(slotItem);

        InsertGesture insert = InsertGesture.classify(event.getClick() == ClickType.LEFT,
                cursorBundle, cursorEmpty, slotBundle, slotEmpty);
        ExtractGesture extract = ExtractGesture.classify(event.getClick() == ClickType.RIGHT,
                cursorBundle, cursorEmpty, slotBundle, slotEmpty);
        String gesture = "keine Bündel-Geste";
        String decision = null;
        if (insert == InsertGesture.CURSOR_BUNDLE_TAKES_SLOT) {
            gesture = insert.name();
            decision = insert(event, player, clicked, slot, cursor, slotItem, true);
        } else if (insert == InsertGesture.SLOT_BUNDLE_TAKES_CURSOR) {
            gesture = insert.name();
            decision = insert(event, player, clicked, slot, slotItem, cursor, false);
        } else if (extract == ExtractGesture.CURSOR_BUNDLE_GIVES_SLOT) {
            gesture = extract.name();
            decision = extract(event, player, clicked, slot, cursor, true);
        } else if (extract == ExtractGesture.SLOT_BUNDLE_GIVES_CURSOR) {
            gesture = extract.name();
            decision = extract(event, player, clicked, slot, slotItem, false);
        }
        if (decision != null) {
            debug(event, player, gesture + ": " + decision);
            if (event.isCancelled()) {
                return;
            }
        } else {
            debug(event, player, "Vanilla: " + gesture);
        }
        // Vanilla path (plain swaps, bundles and entries without rule): only refresh the tooltip line
        // afterwards. A result slot is computed by its menu and is never written to.
        scheduleRefresh(player, clicked, event.getSlotType() == InventoryType.SlotType.RESULT ? -1 : slot);
    }

    private static boolean isProtected(InventoryType.SlotType slotType) {
        return slotType == InventoryType.SlotType.RESULT || slotType == InventoryType.SlotType.ARMOR;
    }

    /**
     * Slots the plugin may put a removed item into itself. Vanilla asks {@code Slot.mayPlace}, which the API
     * does not expose, so only slots that accept every item are allowed: storage and hotbar slots of plain
     * storage inventories and the crafting grid.
     */
    private static boolean acceptsAnyItem(Inventory inventory, InventoryType.SlotType slotType) {
        InventoryType type = inventory.getType();
        if (slotType == InventoryType.SlotType.CRAFTING) {
            return type == InventoryType.CRAFTING || type == InventoryType.WORKBENCH;
        }
        if (slotType != InventoryType.SlotType.CONTAINER && slotType != InventoryType.SlotType.QUICKBAR) {
            return false;
        }
        return switch (type) {
            case PLAYER, CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, DISPENSER, DROPPER, HOPPER -> true;
            default -> false;
        };
    }

    private static void place(Player player, Inventory clicked, int slot, boolean onCursor, ItemStack stack) {
        if (onCursor) {
            player.setItemOnCursor(stack);
        } else {
            clicked.setItem(slot, stack);
        }
    }

    /**
     * Writes cursor and slot as one operation. If the second write fails, both are put back and the
     * exception is passed on.
     */
    private static void writeBoth(Player player, Inventory clicked, int slot, ItemStack newCursor, ItemStack newSlot) {
        ItemStack originalCursor = player.getItemOnCursor().clone();
        ItemStack originalSlot = clicked.getItem(slot) == null ? null : clicked.getItem(slot).clone();
        try {
            player.setItemOnCursor(newCursor);
            clicked.setItem(slot, newSlot);
        } catch (RuntimeException e) {
            player.setItemOnCursor(originalCursor);
            clicked.setItem(slot, originalSlot);
            throw e;
        }
    }

    /**
     * True if the plugin has to manage this bundle: the bundle size differs from vanilla, a configured item
     * is inside, or an entry is still in stored form from an earlier configuration.
     */
    private static boolean managed(ItemStack bundle, BundleRules rules) {
        return rules.appliesToEveryBundle()
                || rules.involvesConfigRule(new BukkitStackView(bundle))
                || StoredItems.containsMarked(bundle);
    }

    /** See {@link VanillaWeight}: the plugin never writes a bundle vanilla's arithmetic could overflow on. */
    private static boolean safeForVanilla(ItemStack bundle) {
        return VanillaWeight.isSafe(new BukkitStackView(bundle).bundleContents());
    }

    private static final String UNSAFE = "abgelehnt: Bündel läge außerhalb der Sicherheitsgrenze für das "
            + "Vanilla-Gewicht (Überlaufschutz)";

    /**
     * Performs the insertion according to the plugin's point rules. The bundle is first brought in line
     * with the current rules (older formats, changed bundle size or item size).
     *
     * @param bundleOnCursor true if the bundle is the cursor stack and the offered item is in the slot
     * @return a short description of what was done, or null if nothing was touched. The event is cancelled
     *         whenever the plugin took the click over; if it is not cancelled, vanilla continues.
     */
    private String insert(InventoryClickEvent event, Player player, Inventory clicked, int slot,
                          ItemStack bundle, ItemStack offered, boolean bundleOnCursor) {
        BundleRules rules = config.rules();
        BukkitStackView offeredView = new BukkitStackView(offered);
        if (!(bundle.getItemMeta() instanceof BundleMeta)
                || (!managed(bundle, rules) && !rules.involvesConfigRule(offeredView))) {
            return null;
        }
        boolean protectedSlot = isProtected(event.getSlotType());
        boolean bundleWritable = bundleOnCursor || !protectedSlot;
        try {
            ItemStack migrated = bundleWritable ? StoredItems.normalizeBundle(bundle, rules) : null;
            if (migrated != null && !safeForVanilla(migrated)) {
                event.setCancelled(true);
                scheduleResync(player);
                return UNSAFE + ", Bündel unverändert";
            }
            ItemStack current = migrated != null ? migrated : bundle;
            if (migrated != null) {
                BundleLore.update(migrated, rules);
            }
            if (!managed(current, rules) && !rules.involvesConfigRule(offeredView)) {
                // Nothing is left for the plugin to decide (the entry was removed from the configuration):
                // vanilla continues on the updated bundle.
                place(player, clicked, slot, bundleOnCursor, current);
                return "Bündel an aktuelle Regeln angepasst, Einpacken läuft über Vanilla";
            }

            event.setCancelled(true);
            String note = migrated != null ? " (Bündel an aktuelle Regeln angepasst)" : "";
            if (protectedSlot) {
                // Vanilla guards these slots with Slot.mayPickup / Slot.allowModification, which the API does
                // not expose. Refusing is the only choice that can neither lose items nor bypass a rule.
                if (migrated != null) {
                    place(player, clicked, slot, bundleOnCursor, migrated);
                }
                scheduleResync(player);
                return "abgelehnt: geschützter Slot (" + event.getSlotType() + ")" + note;
            }
            List<ItemStack> contents = BukkitStackView.contentsOf(current);
            int accepted = rules.acceptableAmount(BukkitStackView.viewAll(contents), offeredView);
            ItemStack piece = accepted > 0 ? StoredItems.storedPiece(offered, rules) : null;
            ItemStack newBundle = accepted > 0 ? StoredItems.withContents(current,
                    StoredEntries.insertFront(contents, piece, accepted, StoredItems.adapter(rules))) : null;
            if (accepted <= 0 || !safeForVanilla(newBundle)) {
                if (migrated != null) {
                    place(player, clicked, slot, bundleOnCursor, migrated);
                }
                scheduleResync(player);
                return (accepted <= 0
                        ? "abgelehnt: kein Platz oder nicht zulässig (" + pointsText(rules, contents) + ")"
                        : UNSAFE) + note;
            }
            BundleLore.update(newBundle, rules);

            int remaining = offered.getAmount() - accepted;
            ItemStack newOffered = null;
            if (remaining > 0) {
                newOffered = offered.clone();
                newOffered.setAmount(remaining);
            }
            if (bundleOnCursor) {
                writeBoth(player, clicked, slot, newBundle, newOffered);
            } else {
                writeBoth(player, clicked, slot, newOffered, newBundle);
            }
            scheduleResync(player);
            return "eingepackt " + accepted + " von " + offered.getAmount() + " als " + describe(piece) + ", "
                    + pointsText(rules, BukkitStackView.contentsOf(newBundle)) + note;
        } catch (RuntimeException e) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.WARNING, "Einlagern in Bündel fehlgeschlagen, Zustand zurückgesetzt", e);
            scheduleResync(player);
            return "Fehler: " + e;
        }
    }

    private static String pointsText(BundleRules rules, List<ItemStack> contents) {
        List<StackView> views = BukkitStackView.viewAll(contents);
        return "belegt " + rules.points(views) + " von " + rules.bundleSize() + " Punkten, Vanilla-Gewicht "
                + VanillaWeight.weight(views);
    }

    /**
     * Takes the selected entry out of a bundle the plugin manages. An entry in stored form leaves as the
     * original item and only with as many pieces as its original stack limit allows; vanilla would hand out
     * the whole entry with the stack limit it carries inside the bundle. Other entries leave as a whole.
     *
     * <p>The plugin also takes over for entries that are not in stored form, because a managed bundle can
     * weigh more than 1 for vanilla. In that state vanilla's own removal into a slot that refuses the item
     * ({@code BundleItem.overrideStackedOnOther}: {@code removeOne}, {@code Slot.safeInsert}, then
     * {@code tryInsert} of the rest) cannot put the item back and would delete it.
     *
     * @param bundleOnCursor true if the bundle is on the cursor and the entry goes into the empty slot
     * @return a short description, or null if nothing was touched (see {@link #insert})
     */
    private String extract(InventoryClickEvent event, Player player, Inventory clicked, int slot,
                           ItemStack bundle, boolean bundleOnCursor) {
        if (!bundleOnCursor && isProtected(event.getSlotType())) {
            return null; // vanilla refuses to modify a bundle in such a slot (Slot.allowModification)
        }
        BundleRules rules = config.rules();
        if (!(bundle.getItemMeta() instanceof BundleMeta) || !managed(bundle, rules)) {
            return null;
        }
        try {
            ItemStack migrated = StoredItems.normalizeBundle(bundle, rules);
            if (migrated != null) {
                // The entries changed, so the client's selection no longer points at a known entry.
                event.setCancelled(true);
                scheduleResync(player);
                if (!safeForVanilla(migrated)) {
                    return UNSAFE + ", Bündel unverändert. Der Inhalt lässt sich durch Ausleeren aus der Hand "
                            + "entnehmen";
                }
                BundleLore.update(migrated, rules);
                place(player, clicked, slot, bundleOnCursor, migrated);
                return "Bündel an aktuelle Regeln angepasst, Klick verworfen (erneut klicken)";
            }
            List<ItemStack> contents = BukkitStackView.contentsOf(bundle);
            if (contents.isEmpty()) {
                return null;
            }
            int selected = StoredItems.selectedIndex(bundle, plugin.getLogger());
            int index = selected >= 0 && selected < contents.size() ? selected : 0;
            ItemStack entry = contents.get(index);
            event.setCancelled(true);
            if (bundleOnCursor && !acceptsAnyItem(clicked, event.getSlotType())) {
                scheduleResync(player);
                return "abgelehnt: Zielslot " + clicked.getType() + "/" + event.getSlotType()
                        + " nimmt nicht jedes Item an";
            }
            StoredEntries.Removal<ItemStack> removal = StoredEntries.take(contents, index, StoredItems.adapter(rules));
            ItemStack newBundle = StoredItems.withContents(bundle, removal.remaining());
            BundleLore.update(newBundle, rules);
            if (bundleOnCursor) {
                writeBoth(player, clicked, slot, newBundle, removal.taken());
            } else {
                writeBoth(player, clicked, slot, removal.taken(), newBundle);
            }
            scheduleResync(player);
            return "entnommen " + removal.taken().getAmount() + " von " + entry.getAmount() + " aus Eintrag "
                    + index + " (Auswahl " + selected + "), " + describe(entry) + " -> "
                    + describe(removal.taken()) + ", " + pointsText(rules, removal.remaining());
        } catch (RuntimeException e) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.WARNING, "Entnahme aus Bündel fehlgeschlagen, Zustand zurückgesetzt", e);
            scheduleResync(player);
            return "Fehler: " + e;
        }
    }

    private void debug(InventoryClickEvent event, Player player, String decision) {
        if (!debug.isEnabled()) {
            return;
        }
        debug.log(player.getName() + " (" + player.getGameMode() + ")"
                + " click=" + event.getClick() + " action=" + event.getAction()
                + " view=" + event.getView().getType() + " slotType=" + event.getSlotType()
                + " rawSlot=" + event.getRawSlot() + " slot=" + event.getSlot()
                + " cursor=" + describe(event.getCursor()) + " slotItem=" + describe(event.getCurrentItem())
                + " -> " + decision);
    }

    private static String describe(ItemStack stack) {
        if (BukkitStackView.isEmpty(stack)) {
            return "leer";
        }
        StringBuilder out = new StringBuilder().append(stack.getAmount()).append('x').append(stack.getType());
        String id = BukkitStackView.rawCustomId(stack);
        if (id != null) {
            out.append("[id=").append(id).append(']');
        }
        out.append("[max=").append(stack.getMaxStackSize()).append(']');
        if (StoredItems.isMarked(stack)) {
            out.append("[eingelagert, original max=").append(StoredItems.originalMaxStackSize(stack)).append(']');
        }
        if (BukkitStackView.isBundle(stack)) {
            out.append("[Einträge=").append(BukkitStackView.contentsOf(stack).size()).append(']');
        }
        return out.toString();
    }

    private void scheduleResync(Player player) {
        player.updateInventory();
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private void scheduleRefresh(Player player, Inventory clicked, int slot) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            BundleRules rules = config.rules();
            ItemStack cursor = player.getItemOnCursor();
            if (BukkitStackView.isBundle(cursor)) {
                ItemStack copy = cursor.clone();
                if (BundleLore.update(copy, rules)) {
                    player.setItemOnCursor(copy);
                }
            }
            ItemStack inSlot = slot < 0 ? null : clicked.getItem(slot);
            if (BukkitStackView.isBundle(inSlot)) {
                ItemStack copy = inSlot.clone();
                if (BundleLore.update(copy, rules)) {
                    clicked.setItem(slot, copy);
                }
            }
        });
    }

    /** Refresh the tooltip line of a bundle in hand after a use (emptying), the contents come from vanilla. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null || !BukkitStackView.isBundle(event.getItem())) {
            return;
        }
        scheduleHandRefresh(event.getPlayer(), hand);
    }

    /**
     * Vanilla empties a bundle in hand piece by piece while the use key is held
     * ({@code BundleItem.onUseTick}), so the line is refreshed once more when the player stops.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        if (!BukkitStackView.isBundle(event.getItem())) {
            return;
        }
        scheduleHandRefresh(event.getPlayer(), EquipmentSlot.HAND);
        scheduleHandRefresh(event.getPlayer(), EquipmentSlot.OFF_HAND);
    }

    private void scheduleHandRefresh(Player player, EquipmentSlot hand) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack current = player.getInventory().getItem(hand);
            if (BukkitStackView.isBundle(current)) {
                ItemStack copy = current.clone();
                if (BundleLore.update(copy, config.rules())) {
                    player.getInventory().setItem(hand, copy);
                }
            }
        });
    }
}
