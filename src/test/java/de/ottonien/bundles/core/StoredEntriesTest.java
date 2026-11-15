package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StoredEntriesTest {

    /**
     * Test item. {@code max} is the stack limit it currently carries, {@code originalMax} is the remembered
     * limit of a marked item (null if not marked).
     */
    record Item(String id, int amount, int max, Integer originalMax) {
    }

    /** Adapter with replaceable rules, standing in for bundles.json. */
    static final class Rules implements StoredEntries.Adapter<Item> {
        final Map<String, Integer> capacities = new HashMap<>();

        Rules(Map<String, Integer> capacities) {
            this.capacities.putAll(capacities);
        }

        @Override
        public int amount(Item e) {
            return e.amount();
        }

        @Override
        public Item withAmount(Item e, int amount) {
            return new Item(e.id(), amount, e.max(), e.originalMax());
        }

        @Override
        public int maxStack(Item e) {
            return e.max();
        }

        @Override
        public boolean similar(Item a, Item b) {
            return a.id().equals(b.id()) && a.max() == b.max()
                    && java.util.Objects.equals(a.originalMax(), b.originalMax());
        }

        @Override
        public boolean marked(Item e) {
            return e.originalMax() != null;
        }

        @Override
        public Integer targetCapacity(Item e) {
            return capacities.get(e.id());
        }

        @Override
        public Item toStored(Item e, int capacity) {
            return new Item(e.id(), e.amount(), capacity, marked(e) ? e.originalMax() : e.max());
        }

        @Override
        public Item restored(Item e) {
            return new Item(e.id(), e.amount(), e.originalMax(), null);
        }
    }

    private static final Item PEA = new Item("ottonien:erbsen", 1, 1, null);
    private static final Item SHARD = new Item("minecraft:amethyst_shard", 1, 64, null);

    private static Item pea(int amount) {
        return new Item(PEA.id(), amount, 1, null);
    }

    private static Item storedPea(int amount, int capacity) {
        return new Item(PEA.id(), amount, capacity, 1);
    }

    private static int total(List<Item> items, String id) {
        return items.stream().filter(i -> i.id().equals(id)).mapToInt(Item::amount).sum();
    }

    private final Rules rules = new Rules(Map.of("ottonien:erbsen", 16, "ottonien:salz", 8));

    @Test
    void splitCoversTheTotal() {
        assertEquals(List.of(8, 8), StoredEntries.split(16, 8));
        assertEquals(List.of(8, 8, 1), StoredEntries.split(17, 8));
        assertEquals(List.of(1, 1, 1), StoredEntries.split(3, 1));
        assertEquals(List.of(), StoredEntries.split(0, 8));
    }

    @Test
    void conversionRemembersTheOriginalLimitAndRestoreReturnsTheSameItem() {
        Item stored = rules.toStored(PEA, 16);
        assertEquals(16, stored.max());
        assertTrue(rules.marked(stored));
        assertEquals(PEA, rules.restored(stored));
    }

    @Test
    void insertingPeasOneByOneBuildsOneStoredEntry() {
        List<Item> contents = new ArrayList<>();
        Item piece = rules.toStored(PEA, 16);
        for (int i = 0; i < 16; i++) {
            contents = StoredEntries.insertFront(contents, piece, 1, rules);
        }
        assertEquals(List.of(storedPea(16, 16)), contents);
    }

    @Test
    void insertedEntryMovesToTheFrontLikeVanilla() {
        List<Item> contents = List.of(new Item(SHARD.id(), 3, 64, null), storedPea(2, 16));
        List<Item> out = StoredEntries.insertFront(contents, rules.toStored(PEA, 16), 1, rules);
        assertEquals(List.of(storedPea(3, 16), new Item(SHARD.id(), 3, 64, null)), out);
    }

    @Test
    void insertOpensANewEntryWhenTheExistingOneIsAtItsLimit() {
        List<Item> out = StoredEntries.insertFront(List.of(storedPea(16, 16)), rules.toStored(PEA, 16), 2, rules);
        assertEquals(List.of(storedPea(2, 16), storedPea(16, 16)), out);
    }

    @Test
    void takeHandsOutOnlyWhatTheOriginalLimitAllows() {
        StoredEntries.Removal<Item> removal = StoredEntries.take(List.of(storedPea(16, 16)), 0, rules);
        assertEquals(pea(1), removal.taken());
        assertEquals(List.of(storedPea(15, 16)), removal.remaining());
    }

    @Test
    void takeRemovesTheEntryWhenNothingIsLeft() {
        List<Item> contents = List.of(new Item(SHARD.id(), 5, 64, null), storedPea(1, 16));
        StoredEntries.Removal<Item> removal = StoredEntries.take(contents, 1, rules);
        assertEquals(pea(1), removal.taken());
        assertEquals(List.of(new Item(SHARD.id(), 5, 64, null)), removal.remaining());
    }

    @Test
    void takeWithALargerOriginalLimitHandsOutTheWholeEntry() {
        Item salt = new Item("ottonien:salz", 8, 8, 64);
        StoredEntries.Removal<Item> removal = StoredEntries.take(List.of(salt), 0, rules);
        assertEquals(new Item("ottonien:salz", 8, 64, null), removal.taken());
        assertTrue(removal.remaining().isEmpty());
    }

    @Test
    void takeHandsOutAnUnmarkedEntryAsAWholeLikeVanilla() {
        Item shards = new Item(SHARD.id(), 40, 64, null);
        StoredEntries.Removal<Item> removal = StoredEntries.take(List.of(storedPea(2, 16), shards), 1, rules);
        assertEquals(shards, removal.taken());
        assertEquals(List.of(storedPea(2, 16)), removal.remaining());
    }

    @Test
    void takeRejectsBadIndexes() {
        assertThrows(IllegalArgumentException.class, () -> StoredEntries.take(List.of(storedPea(1, 16)), 1, rules));
        assertThrows(IllegalArgumentException.class, () -> StoredEntries.take(List.of(storedPea(1, 16)), -1, rules));
    }

    @Test
    void oldFormatWithSixteenSingleEntriesIsMigrated() {
        List<Item> old = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            old.add(pea(1));
        }
        assertEquals(List.of(storedPea(16, 16)), StoredEntries.normalize(old, rules));
    }

    @Test
    void upToDateBundleIsLeftUnchanged() {
        List<Item> contents = List.of(storedPea(5, 16), new Item(SHARD.id(), 10, 64, null));
        assertEquals(contents, StoredEntries.normalize(contents, rules));
    }

    @Test
    void reloadWithSmallerCapacitySplitsEntriesAndKeepsEverything() {
        List<Item> contents = List.of(storedPea(16, 16));
        rules.capacities.put("ottonien:erbsen", 8);
        List<Item> out = StoredEntries.normalize(contents, rules);
        assertEquals(List.of(storedPea(8, 8), storedPea(8, 8)), out);
        assertEquals(16, total(out, PEA.id()), "overfilled bundle keeps its contents");
    }

    @Test
    void reloadWithLargerCapacityCombinesEntries() {
        List<Item> contents = List.of(storedPea(8, 8), storedPea(3, 8));
        rules.capacities.put("ottonien:erbsen", 32);
        assertEquals(List.of(storedPea(11, 32)), StoredEntries.normalize(contents, rules));
    }

    @Test
    void removedRuleRestoresEntriesToSingleOriginalStacks() {
        List<Item> contents = List.of(storedPea(3, 16), new Item(SHARD.id(), 10, 64, null));
        rules.capacities.remove("ottonien:erbsen");
        assertEquals(List.of(pea(1), pea(1), pea(1), new Item(SHARD.id(), 10, 64, null)),
                StoredEntries.normalize(contents, rules));
    }

    @Test
    void removedRuleWithALargerOriginalLimitCombinesAgain() {
        List<Item> contents = List.of(new Item("ottonien:salz", 8, 8, 64), new Item("ottonien:salz", 2, 8, 64));
        rules.capacities.remove("ottonien:salz");
        assertEquals(List.of(new Item("ottonien:salz", 10, 64, null)), StoredEntries.normalize(contents, rules));
    }

    @Test
    void itemsWithoutRuleAndEmptyEntriesAreNotTouched() {
        Item odd = new Item("minecraft:stick", 3, 1, null);
        List<Item> contents = List.of(odd, new Item(SHARD.id(), 0, 64, null), SHARD);
        assertEquals(List.of(odd, SHARD), StoredEntries.normalize(contents, rules));
    }

    @Test
    void normalizeIsIdempotent() {
        List<Item> mixed = List.of(pea(1), storedPea(4, 8), pea(1), new Item(SHARD.id(), 7, 64, null));
        List<Item> once = StoredEntries.normalize(mixed, rules);
        assertEquals(List.of(storedPea(6, 16), new Item(SHARD.id(), 7, 64, null)), once);
        assertEquals(once, StoredEntries.normalize(once, rules));
    }

    @Test
    void releaseOutsideABundleSplitsWithoutLossOrDuplication() {
        List<Item> out = StoredEntries.release(storedPea(16, 16), rules);
        assertEquals(16, out.size());
        assertTrue(out.stream().allMatch(pea(1)::equals));
        assertEquals(List.of(new Item("ottonien:salz", 8, 64, null)),
                StoredEntries.release(new Item("ottonien:salz", 8, 8, 64), rules));
    }
}
