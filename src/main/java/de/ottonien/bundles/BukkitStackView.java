package de.ottonien.bundles;

import de.ottonien.bundles.core.StackView;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Beehive;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** {@link StackView} backed by a real Bukkit {@link ItemStack}. Reads only, never mutates the stack. */
public final class BukkitStackView implements StackView {

    /** The only interface to OttonienItems: PDC key ottonienitems:item_id, type STRING. */
    @SuppressWarnings("deprecation")
    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("ottonienitems", "item_id");

    private final ItemStack stack;

    public BukkitStackView(ItemStack stack) {
        this.stack = stack;
    }

    public static List<StackView> viewAll(List<ItemStack> stacks) {
        List<StackView> views = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) {
            views.add(new BukkitStackView(s));
        }
        return views;
    }

    public static boolean isBundle(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && Tag.ITEMS_BUNDLES.isTagged(stack.getType());
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /** Raw PDC value, exactly as stored. */
    public static String rawCustomId(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    /** Stored entries of a bundle, or an empty list. */
    public static List<ItemStack> contentsOf(ItemStack bundle) {
        if (isBundle(bundle) && bundle.getItemMeta() instanceof BundleMeta meta) {
            return meta.getItems();
        }
        return List.of();
    }

    public ItemStack stack() {
        return stack;
    }

    @Override
    public int amount() {
        return stack.getAmount();
    }

    @Override
    public String customId() {
        String id = rawCustomId(stack);
        return id == null || id.isEmpty() ? null : id;
    }

    @Override
    public String itemModel() {
        return rawItemModel(stack);
    }

    /** Explicitly set item_model component, or null if the material's default model is used. */
    public static String rawItemModel(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasItemModel()) {
            return null;
        }
        NamespacedKey model = meta.getItemModel();
        return model == null ? null : model.asString();
    }

    @Override
    public boolean vanillaAllowed() {
        return !stack.getType().isAir() && !Tag.SHULKER_BOXES.isTagged(stack.getType());
    }

    @Override
    public boolean isBundle() {
        return isBundle(stack);
    }

    @Override
    public List<StackView> bundleContents() {
        return viewAll(contentsOf(stack));
    }

    @Override
    public boolean hasBees() {
        if (!Tag.BEEHIVES.isTagged(stack.getType())) {
            return false;
        }
        if (stack.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()) {
            BlockState state = meta.getBlockState();
            return state instanceof Beehive hive && hive.getEntityCount() > 0;
        }
        return false;
    }

    @Override
    public int effectiveMaxStackSize() {
        // An entry in stored form carries its bundle capacity as stack limit; the vanilla fallback weight
        // has to use the limit the item has outside of a bundle.
        return StoredItems.originalMaxStackSize(stack);
    }

    @Override
    public int carriedMaxStackSize() {
        return stack.getMaxStackSize();
    }
}
