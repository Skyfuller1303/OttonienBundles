package de.ottonien.bundles;

import de.ottonien.bundles.core.BundleConfigParser;
import de.ottonien.bundles.core.BundleRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Holds the active rule snapshot and replaces it only after a fully validated parse. */
public final class BundleConfigManager {

    public record LoadOutcome(boolean success, int bundleSize, int entryCount, List<String> errors) {
    }

    private final Path file;
    private volatile BundleRules rules = BundleRules.vanilla();

    public BundleConfigManager(Path file) {
        this.file = file;
    }

    public BundleRules rules() {
        return rules;
    }

    public Path file() {
        return file;
    }

    /** Reads, validates and, only on success, activates the file. On failure the old snapshot stays active. */
    public LoadOutcome load() {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new LoadOutcome(false, rules.bundleSize(), rules.sizes().size(),
                    List.of("Datei " + file + " konnte nicht gelesen werden: " + e));
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        BundleConfigParser.Result result = BundleConfigParser.parse(text);
        if (!result.ok()) {
            return new LoadOutcome(false, rules.bundleSize(), rules.sizes().size(), result.errors());
        }
        rules = new BundleRules(result.bundleSize(), result.sizes());
        return new LoadOutcome(true, result.bundleSize(), result.sizes().size(), List.of());
    }
}
