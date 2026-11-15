package de.ottonien.bundles.core;

import java.util.ArrayList;
import java.util.List;

/**
 * List operations on the entries of a bundle in their "stored form".
 *
 * <p>While an item lies in a bundle the plugin manages, it carries the stack limit chosen by
 * {@link StoredSize} and, if that differs from its own, a marker that remembers its original stack limit.
 * The unmodified client then computes the share of the bundle the plugin assigns to the piece, so its fill
 * bar is correct. When the item
 * leaves the bundle it is restored exactly. This class contains the arithmetic of that scheme; what
 * "stored" and "restored" mean for a concrete item is supplied by the {@link Adapter}.
 */
public final class StoredEntries {

    /** Access to the item type {@code E}. Implementations never modify an entry in place. */
    public interface Adapter<E> {

        int amount(E entry);

        /** A copy of the entry with the given amount. */
        E withAmount(E entry, int amount);

        /** Effective stack limit of the entry as it currently is. */
        int maxStack(E entry);

        /** True if both are the same item apart from the amount. */
        boolean similar(E a, E b);

        /** True if the entry carries the marker of the stored form. */
        boolean marked(E entry);

        /**
         * The stack limit the entry has to carry while stored, or null if it has to be in its original form
         * (its original stack limit already matches, or it cannot be converted).
         */
        Integer targetCapacity(E entry);

        /** A copy in stored form with the given stack limit. An existing marker is kept. */
        E toStored(E entry, int capacity);

        /** A copy of a marked entry in its original form, without the marker. */
        E restored(E entry);
    }

    /** Result of taking pieces out of an entry. */
    public record Removal<E>(List<E> remaining, E taken) {
    }

    private StoredEntries() {
    }

    /** Splits a total into stack sizes of at most {@code max}, largest first. */
    public static List<Integer> split(int total, int max) {
        int limit = Math.max(1, max);
        List<Integer> out = new ArrayList<>();
        for (int left = total; left > 0; left -= limit) {
            out.add(Math.min(limit, left));
        }
        return out;
    }

    /**
     * Brings every entry in line with the current rules. Entries are converted to the stored form with the
     * stack limit the rules ask for (this migrates older formats and follows a changed bundle size or item
     * size), entries that no longer need one are restored. Entries exceeding their stack limit are split, equal entries are
     * combined. The total amount of every item is preserved and nothing is dropped, even if the result
     * exceeds the capacity of the bundle. Entries that are neither marked nor governed by a rule are passed
     * through untouched.
     */
    public static <E> List<E> normalize(List<E> entries, Adapter<E> adapter) {
        List<E> out = new ArrayList<>(entries.size());
        for (E entry : entries) {
            int amount = adapter.amount(entry);
            if (amount <= 0) {
                continue;
            }
            Integer capacity = adapter.targetCapacity(entry);
            E base;
            if (capacity != null) {
                boolean upToDate = adapter.marked(entry) && adapter.maxStack(entry) == capacity;
                base = upToDate ? entry : adapter.toStored(entry, capacity);
            } else if (adapter.marked(entry)) {
                base = adapter.restored(entry);
            } else {
                out.add(entry);
                continue;
            }
            append(out, base, amount, adapter);
        }
        return out;
    }

    private static <E> void append(List<E> out, E base, int amount, Adapter<E> adapter) {
        int max = Math.max(1, adapter.maxStack(base));
        int left = amount;
        for (int i = 0; i < out.size() && left > 0; i++) {
            E existing = out.get(i);
            int room = max - adapter.amount(existing);
            if (room > 0 && adapter.similar(existing, base)) {
                int add = Math.min(room, left);
                out.set(i, adapter.withAmount(existing, adapter.amount(existing) + add));
                left -= add;
            }
        }
        for (int size : split(left, max)) {
            out.add(adapter.withAmount(base, size));
        }
    }

    /**
     * Puts {@code amount} pieces of {@code piece} into a copy of the entries the way vanilla does: an equal
     * entry with room is topped up and moved to the front, otherwise a new entry is placed at the front.
     */
    public static <E> List<E> insertFront(List<E> entries, E piece, int amount, Adapter<E> adapter) {
        List<E> out = new ArrayList<>(entries);
        int max = Math.max(1, adapter.maxStack(piece));
        int left = amount;
        while (left > 0) {
            int found = -1;
            for (int i = 0; i < out.size(); i++) {
                E existing = out.get(i);
                if (adapter.amount(existing) < max && adapter.similar(existing, piece)) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                E existing = out.remove(found);
                int add = Math.min(max - adapter.amount(existing), left);
                out.add(0, adapter.withAmount(existing, adapter.amount(existing) + add));
                left -= add;
            } else {
                int add = Math.min(max, left);
                out.add(0, adapter.withAmount(piece, add));
                left -= add;
            }
        }
        return out;
    }

    /**
     * Takes the entry at {@code index} out of the bundle. An entry in stored form leaves in its original
     * form: vanilla would hand out the whole entry, here only as many pieces leave as the original stack
     * limit allows, and the rest stays in the bundle at the same position. An entry that is not in stored
     * form leaves as a whole, exactly as in vanilla.
     *
     * @throws IllegalArgumentException if the index is out of range
     */
    public static <E> Removal<E> take(List<E> entries, int index, Adapter<E> adapter) {
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException("index " + index + " outside 0.." + (entries.size() - 1));
        }
        E entry = entries.get(index);
        List<E> remaining = new ArrayList<>(entries);
        if (!adapter.marked(entry)) {
            remaining.remove(index);
            return new Removal<>(remaining, entry);
        }
        E original = adapter.restored(entry);
        int amount = adapter.amount(entry);
        int out = Math.min(amount, Math.max(1, adapter.maxStack(original)));
        if (amount - out > 0) {
            remaining.set(index, adapter.withAmount(entry, amount - out));
        } else {
            remaining.remove(index);
        }
        return new Removal<>(remaining, adapter.withAmount(original, out));
    }

    /**
     * Releases a marked stack that is found outside of a bundle: the original item, split into stacks its
     * original limit allows. The sum of the amounts equals the amount of the input.
     */
    public static <E> List<E> release(E marked, Adapter<E> adapter) {
        E original = adapter.restored(marked);
        List<E> out = new ArrayList<>();
        for (int size : split(adapter.amount(marked), adapter.maxStack(original))) {
            out.add(adapter.withAmount(original, size));
        }
        return out;
    }
}
