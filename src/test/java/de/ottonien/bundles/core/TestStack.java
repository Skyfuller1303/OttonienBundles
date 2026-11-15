package de.ottonien.bundles.core;

import java.util.List;

/** Plain test double for {@link StackView}. */
record TestStack(int amount, String customId, boolean vanillaAllowed, boolean isBundle,
                 List<StackView> bundleContents, boolean hasBees, int effectiveMaxStackSize)
        implements StackView {

    static TestStack item(int amount, String customId, int maxStack) {
        return new TestStack(amount, customId, true, false, List.of(), false, maxStack);
    }

    static TestStack erbsen(int amount) {
        return item(amount, "ottonien:erbsen", 1);
    }

    static TestStack salz(int amount) {
        return item(amount, "ottonien:salz", 1);
    }

    static TestStack erbsensuppe(int amount) {
        return item(amount, "ottonien:erbsensuppe", 1);
    }

    static TestStack apfelkuchen(int amount) {
        return item(amount, "ottonien:apfelkuchen", 1);
    }

    static TestStack zwiebel(int amount) {
        return item(amount, "ottonien:zwiebel", 1);
    }

    static TestStack ragout(int amount) {
        return item(amount, null, 1);
    }

    static TestStack amethyst(int amount) {
        return item(amount, null, 64);
    }

    static TestStack bundle(StackView... contents) {
        return new TestStack(1, null, true, true, List.of(contents), false, 1);
    }

    static List<StackView> repeat(int count, TestStack one) {
        return java.util.Collections.nCopies(count, (StackView) one);
    }
}
