package de.ottonien.bundles;

import de.ottonien.bundles.core.BundleRules;
import de.ottonien.bundles.core.StoredEntries;
import de.ottonien.bundles.core.StoredSize;
import de.ottonien.bundles.core.UnitWeight;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The "stored form" of items inside bundles on real {@link ItemStack}s.
 *
 * <p>While an item lies in a bundle the plugin manages, it carries the {@code max_stack_size} that makes
 * the unmodified client compute the share of the bundle the piece occupies under the plugin's point rules
 * (see {@link StoredSize}). Items whose own stack limit already has that value are not touched. The original state of the
 * component is remembered in the item's PDC under {@code ottonienbundles:original_max_stack} (INTEGER; the
 * explicit value of the component, or 0 if the component was not set). Restoring removes the marker and
 * puts the component back exactly, so a restored item is {@code isSimilar} to a freshly created one.
 */
public final class StoredItems {

    /** PDC marker of the stored form. */
    @SuppressWarnings("deprecation")
    public static final NamespacedKey MARKER = new NamespacedKey("ottonienbundles", "original_max_stack");

    /** Marker value for "the item had no explicit max_stack_size component". */
    public static final int UNSET = 0;

    private static volatile boolean selectionWarningLogged;

    private StoredItems() {
    }

    public static boolean isMarked(ItemStack stack) {
        return !BukkitStackView.isEmpty(stack)
                && stack.getPersistentDataContainer().has(MARKER, PersistentDataType.INTEGER);
    }

    /**
     * Stack limit the item has outside of a bundle: the remembered original for a marked item, otherwise
     * the limit it currently carries.
     */
    public static int originalMaxStackSize(ItemStack stack) {
        Integer remembered = stack.getPersistentDataContainer().get(MARKER, PersistentDataType.INTEGER);
        if (remembered == null) {
            return stack.getMaxStackSize();
        }
        return remembered == UNSET ? stack.getType().getMaxStackSize() : remembered;
    }

    /** A copy in stored form with the given stack limit. An existing marker is kept. */
    public static ItemStack toStored(ItemStack stack, int capacity) {
        ItemStack out = stack.clone();
        ItemMeta meta = out.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(MARKER, PersistentDataType.INTEGER)) {
            pdc.set(MARKER, PersistentDataType.INTEGER, meta.hasMaxStackSize() ? meta.getMaxStackSize() : UNSET);
        }
        meta.setMaxStackSize(capacity);
        out.setItemMeta(meta);
        return out;
    }

    /** A copy of a marked item in its original form. Unmarked items are returned as a plain copy. */
    public static ItemStack restored(ItemStack stack) {
        ItemStack out = stack.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) {
            return out;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer remembered = pdc.get(MARKER, PersistentDataType.INTEGER);
        if (remembered == null) {
            return out;
        }
        pdc.remove(MARKER);
        meta.setMaxStackSize(remembered == UNSET ? null : remembered);
        out.setItemMeta(meta);
        return out;
    }

    /**
     * Stack limit the item has to carry inside a bundle under the given rules, or null if it has to be in
     * its original form. The limit is chosen by {@link StoredSize} from the item's cost in points. Null is
     * returned if the original stack limit already is that value, and for everything that cannot be
     * converted: bundles, items vanilla does not allow in bundles, bee containers with bees (vanilla
     * weighs them as a whole bundle whatever their stack limit) and damageable items, which the game does
     * not allow to be stackable.
     */
    public static Integer targetCapacity(ItemStack stack, BundleRules rules) {
        if (BukkitStackView.isEmpty(stack) || BukkitStackView.isBundle(stack)) {
            return null;
        }
        BukkitStackView view = new BukkitStackView(stack);
        if (!view.vanillaAllowed() || view.hasBees()) {
            return null;
        }
        UnitWeight cost = rules.resolve(view);
        if (!cost.allowed()) {
            return null;
        }
        int target = StoredSize.choose(cost.weight(), rules.bundleSize());
        if (target == originalMaxStackSize(stack)) {
            return null;
        }
        if (target > 1) {
            if (stack.getType().getMaxDurability() > 0) {
                return null;
            }
            if (stack.getItemMeta() instanceof Damageable damageable && damageable.hasMaxDamage()) {
                return null;
            }
        }
        return target;
    }

    /** True if the bundle holds an entry in stored form, at any nesting depth. */
    public static boolean containsMarked(ItemStack bundle) {
        return containsMarked(bundle, 0);
    }

    private static boolean containsMarked(ItemStack bundle, int depth) {
        for (ItemStack entry : BukkitStackView.contentsOf(bundle)) {
            if (isMarked(entry)) {
                return true;
            }
            if (depth < BundleRules.MAX_DEPTH && BukkitStackView.isBundle(entry) && containsMarked(entry, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    public static StoredEntries.Adapter<ItemStack> adapter(BundleRules rules) {
        return new StoredEntries.Adapter<>() {
            @Override
            public int amount(ItemStack entry) {
                return entry.getAmount();
            }

            @Override
            public ItemStack withAmount(ItemStack entry, int amount) {
                ItemStack out = entry.clone();
                out.setAmount(amount);
                return out;
            }

            @Override
            public int maxStack(ItemStack entry) {
                return entry.getMaxStackSize();
            }

            @Override
            public boolean similar(ItemStack a, ItemStack b) {
                return a.isSimilar(b);
            }

            @Override
            public boolean marked(ItemStack entry) {
                return isMarked(entry);
            }

            @Override
            public Integer targetCapacity(ItemStack entry) {
                return StoredItems.targetCapacity(entry, rules);
            }

            @Override
            public ItemStack toStored(ItemStack entry, int capacity) {
                return StoredItems.toStored(entry, capacity);
            }

            @Override
            public ItemStack restored(ItemStack entry) {
                return StoredItems.restored(entry);
            }
        };
    }

    /**
     * The offered item as it has to be stored: converted if the rules ask for another stack limit, a bundle
     * with its own entries brought in line, otherwise a plain copy.
     */
    public static ItemStack storedPiece(ItemStack offered, BundleRules rules) {
        if (BukkitStackView.isBundle(offered)) {
            ItemStack normalized = normalizeBundle(offered, rules);
            return normalized != null ? normalized : offered.clone();
        }
        Integer capacity = targetCapacity(offered, rules);
        return capacity == null ? offered.clone() : toStored(offered, capacity);
    }

    /**
     * Brings the entries of a bundle, including nested bundles, in line with the current rules (see
     * {@link StoredEntries#normalize}).
     *
     * @return the updated copy of the bundle, or null if nothing had to change
     */
    public static ItemStack normalizeBundle(ItemStack bundle, BundleRules rules) {
        return normalizeBundle(bundle, rules, 0);
    }

    private static ItemStack normalizeBundle(ItemStack bundle, BundleRules rules, int depth) {
        if (!BukkitStackView.isBundle(bundle) || !(bundle.getItemMeta() instanceof BundleMeta)) {
            return null;
        }
        List<ItemStack> contents = BukkitStackView.contentsOf(bundle);
        if (contents.isEmpty()) {
            return null;
        }
        List<ItemStack> prepared = new ArrayList<>(contents.size());
        for (ItemStack entry : contents) {
            ItemStack inner = depth < BundleRules.MAX_DEPTH && BukkitStackView.isBundle(entry)
                    ? normalizeBundle(entry, rules, depth + 1) : null;
            prepared.add(inner != null ? inner : entry);
        }
        List<ItemStack> normalized = StoredEntries.normalize(prepared, adapter(rules));
        if (normalized.equals(contents)) {
            return null;
        }
        return withContents(bundle, normalized);
    }

    /** A copy of the bundle holding exactly the given entries. */
    public static ItemStack withContents(ItemStack bundle, List<ItemStack> entries) {
        ItemStack out = bundle.clone();
        BundleMeta meta = (BundleMeta) out.getItemMeta();
        meta.setItems(entries);
        out.setItemMeta(meta);
        return out;
    }

    /** A marked stack found outside of a bundle as original items, split by the original stack limit. */
    public static List<ItemStack> release(ItemStack marked) {
        return StoredEntries.release(marked, adapter(BundleRules.vanilla()));
    }

    /**
     * Index of the entry the player selected in the bundle preview by scrolling, or -1 if none is selected.
     * Vanilla removes this entry on a right click (index 0 without a selection).
     *
     * <p>The index is part of the server-side component but is exposed neither by {@link BundleMeta} nor by
     * Paper's {@code BundleContents}. It is therefore read from the server object behind the Paper wrapper
     * ({@code net.minecraft.world.item.component.BundleContents.getSelectedItem()}, Mojang names, verified
     * on Paper 1.21.11 build 132). If that fails, -1 is returned and a warning is logged once.
     */
    public static int selectedIndex(ItemStack bundle, Logger logger) {
        try {
            Object data = bundle.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (data == null) {
                return -1;
            }
            Object handle = data.getClass().getMethod("getHandle").invoke(data);
            return (Integer) handle.getClass().getMethod("getSelectedItem").invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!selectionWarningLogged) {
                selectionWarningLogged = true;
                logger.log(Level.WARNING, "Die Scroll-Auswahl im Bündel ist auf diesem Server nicht lesbar. "
                        + "Entnommen wird immer der erste Eintrag.", e);
            }
            return -1;
        }
    }

    /**
     * Exercises conversion, restoration and the selection lookup on synthetic items against the running
     * server. Returns a list of problems, empty if everything behaves as required.
     */
    public static List<String> selfTest(Logger logger) {
        List<String> problems = new ArrayList<>();
        try {
            ItemStack explicit = ItemStack.of(Material.RABBIT_STEW);
            ItemMeta meta = explicit.getItemMeta();
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(BukkitStackView.ITEM_ID_KEY, PersistentDataType.STRING,
                    "selbsttest:explizit");
            explicit.setItemMeta(meta);
            checkRoundTrip(explicit, 16, problems);

            ItemStack implicit = ItemStack.of(Material.AMETHYST_SHARD);
            meta = implicit.getItemMeta();
            meta.getPersistentDataContainer().set(BukkitStackView.ITEM_ID_KEY, PersistentDataType.STRING,
                    "selbsttest:implizit");
            implicit.setItemMeta(meta);
            checkRoundTrip(implicit, 8, problems);

            ItemStack unmarkedOnly = ItemStack.of(Material.STICK);
            ItemStack back = restored(toStored(unmarkedOnly, 4));
            if (!back.equals(unmarkedOnly) || back.getItemMeta().hasMaxStackSize()) {
                problems.add("Item ohne PDC ist nach der Rückwandlung nicht identisch: " + back);
            }

            ItemStack entry = toStored(explicit, 16);
            entry.setAmount(16);
            ItemStack bundle = withContents(ItemStack.of(Material.BUNDLE), List.of(entry));
            List<ItemStack> stored = BukkitStackView.contentsOf(bundle);
            if (stored.size() != 1 || stored.get(0).getAmount() != 16 || !isMarked(stored.get(0))) {
                problems.add("Bündel hält den zusammengefassten Eintrag nicht: " + stored);
            }
            Object data = bundle.getData(DataComponentTypes.BUNDLE_CONTENTS);
            Object handle = data.getClass().getMethod("getHandle").invoke(data);
            Object selected = handle.getClass().getMethod("getSelectedItem").invoke(handle);
            if (!Integer.valueOf(-1).equals(selected)) {
                problems.add("Auswahlindex eines frischen Bündels ist " + selected + " statt -1");
            }
            List<ItemStack> released = release(entry);
            if (released.size() != 16 || !released.stream().allMatch(explicit::equals)) {
                problems.add("Freigabe von 16 Stück ergibt nicht 16 Originale: " + released);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.log(Level.SEVERE, "Selbsttest der eingelagerten Form ist abgebrochen", e);
            problems.add("Ausnahme: " + e);
        }
        return problems;
    }

    private static void checkRoundTrip(ItemStack original, int capacity, List<String> problems) {
        ItemStack stored = toStored(original, capacity);
        if (!isMarked(stored) || stored.getMaxStackSize() != capacity) {
            problems.add("Umwandlung setzt Marker oder Stackgröße " + capacity + " nicht: " + stored);
        }
        if (originalMaxStackSize(stored) != original.getMaxStackSize()) {
            problems.add("Gemerkte Stackgröße " + originalMaxStackSize(stored) + " weicht vom Original "
                    + original.getMaxStackSize() + " ab");
        }
        ItemStack again = toStored(stored, capacity + 1);
        ItemStack back = restored(again);
        if (isMarked(back) || !back.isSimilar(original) || !back.equals(original)
                || back.getMaxStackSize() != original.getMaxStackSize()
                || back.getItemMeta().hasMaxStackSize() != original.getItemMeta().hasMaxStackSize()
                || !original.getItemMeta().getPersistentDataContainer().getKeys()
                        .equals(back.getItemMeta().getPersistentDataContainer().getKeys())) {
            problems.add("Rückwandlung ist nicht identisch zum Original: " + back + " statt " + original);
        }
    }
}
