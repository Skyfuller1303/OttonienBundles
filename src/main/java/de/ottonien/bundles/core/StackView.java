package de.ottonien.bundles.core;

import java.util.List;

/**
 * Minimal view of an item stack for the capacity calculation. It keeps the calculation independent of the
 * Bukkit runtime so it can be unit tested.
 */
public interface StackView {

    int amount();

    /** Raw value of the PDC key {@code ottonienitems:item_id}, or null if absent. Never normalised. */
    String customId();

    /**
     * Explicitly set {@code item_model} component as namespaced ID (e.g. {@code ottonien:item/erbsen}), or
     * null if the item uses the default model of its material. Used as second configuration key when the
     * PDC ID has no entry.
     */
    default String itemModel() {
        return null;
    }

    /** Whether vanilla allows this item inside a bundle at all (false for shulker boxes, air, ...). */
    boolean vanillaAllowed();

    boolean isBundle();

    /** Stored entries of a bundle. Empty for non-bundles. */
    List<StackView> bundleContents();

    /** True for a beehive or bee nest that contains bees. */
    boolean hasBees();

    /**
     * Effective stack size the item has outside of a bundle, including the max_stack_size component and its
     * defaults. For an entry in stored form this is the remembered original, not the size it carries.
     */
    int effectiveMaxStackSize();

    /**
     * Stack size the stack carries right now. Inside a bundle this is what vanilla and the client use for
     * their own weight; it differs from {@link #effectiveMaxStackSize()} for entries in stored form.
     */
    default int carriedMaxStackSize() {
        return effectiveMaxStackSize();
    }
}
