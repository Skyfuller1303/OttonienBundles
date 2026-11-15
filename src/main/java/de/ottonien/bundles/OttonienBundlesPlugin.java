package de.ottonien.bundles;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;

public final class OttonienBundlesPlugin extends JavaPlugin {

    private BundleConfigManager config;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        config = new BundleConfigManager(getDataFolder().toPath().resolve("bundles.json"));
        if (!Files.exists(config.file())) {
            saveResource("bundles.json", false);
        }
        BundleConfigManager.LoadOutcome outcome = config.load();
        if (outcome.success()) {
            getLogger().info("bundles.json geladen: bundleSize " + outcome.bundleSize() + ", "
                    + outcome.entryCount() + " Item-Eintrag/Einträge.");
        } else {
            getLogger().severe("bundles.json ist ungültig, es gelten bis zur Korrektur und zu einem erfolgreichen "
                    + "'/ottonienbundle reload' nur Vanilla-Regeln:");
            for (String error : outcome.errors()) {
                getLogger().severe(" - " + error);
            }
        }

        java.util.List<String> problems = StoredItems.selfTest(getLogger());
        if (problems.isEmpty()) {
            getLogger().info("Selbsttest eingelagerte Form bestanden: Umwandlung, identische Rückwandlung, "
                    + "zusammengefasster Eintrag im Bündel und Auswahlindex.");
        } else {
            getLogger().severe("Selbsttest eingelagerte Form fehlgeschlagen:");
            for (String problem : problems) {
                getLogger().severe(" - " + problem);
            }
        }

        DebugLog debugLog = new DebugLog(getLogger());
        getServer().getPluginManager().registerEvents(new StoredFormGuard(debugLog), this);
        getServer().getPluginManager().registerEvents(new BundleListener(this, config, debugLog), this);
        BundleCommand command = new BundleCommand(config, debugLog);
        PluginCommand pluginCommand = getCommand("ottonienbundle");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }
}
