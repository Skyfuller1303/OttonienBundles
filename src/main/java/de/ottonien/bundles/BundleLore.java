package de.ottonien.bundles;

import de.ottonien.bundles.core.BundleRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The plugin no longer writes a tooltip line of its own; the fullness is shown by the client's bar only.
 * This class removes the line that earlier versions wrote. That line is identified by the plain text
 * remembered in the item's PDC under {@code ottonienbundles:occupancy_line}; other lore lines are never
 * touched.
 */
public final class BundleLore {

    @SuppressWarnings("deprecation")
    private static final NamespacedKey LINE_KEY = new NamespacedKey("ottonienbundles", "occupancy_line");

    private BundleLore() {
    }

    /**
     * Removes the line an earlier version wrote onto the given bundle. Returns true if the stack was
     * modified in place.
     */
    public static boolean update(ItemStack bundle, BundleRules rules) {
        if (!BukkitStackView.isBundle(bundle)) {
            return false;
        }
        ItemMeta meta = bundle.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String stored = pdc.get(LINE_KEY, PersistentDataType.STRING);
        if (stored == null) {
            return false;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (int i = 0; i < lore.size(); i++) {
            if (PlainTextComponentSerializer.plainText().serialize(lore.get(i)).equals(stored)) {
                lore.remove(i);
                break;
            }
        }
        pdc.remove(LINE_KEY);
        meta.lore(lore.isEmpty() ? null : lore);
        bundle.setItemMeta(meta);
        return true;
    }
}
