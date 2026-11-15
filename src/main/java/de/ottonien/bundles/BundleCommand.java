package de.ottonien.bundles;

import de.ottonien.bundles.core.BundleRules;
import de.ottonien.bundles.core.Fraction;
import de.ottonien.bundles.core.Occupancy;
import de.ottonien.bundles.core.StackView;
import de.ottonien.bundles.core.StoredSize;
import de.ottonien.bundles.core.UnitWeight;
import de.ottonien.bundles.core.VanillaWeight;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /ottonienbundle reload | inspect | debug. Inspect is strictly read-only. */
public final class BundleCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_INSPECT_DEPTH = 4;

    private static final String USAGE = " <reload|inspect|debug [on|off]>";

    private final BundleConfigManager config;
    private final DebugLog debugLog;

    public BundleCommand(BundleConfigManager config, DebugLog debugLog) {
        this.config = config;
        this.debugLog = debugLog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 1 && sub.equals("reload")) {
            reload(sender);
        } else if (args.length == 1 && sub.equals("inspect")) {
            inspect(sender);
        } else if ((args.length == 1 || args.length == 2) && sub.equals("debug")) {
            debug(sender, args.length == 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : null);
        } else {
            send(sender, "Verwendung: /" + label + USAGE);
        }
        return true;
    }

    /** Toggles the diagnostic log line per bundle click. The state is not persisted across restarts. */
    private void debug(CommandSender sender, String value) {
        if (!sender.hasPermission("ottonienbundles.debug")) {
            send(sender, "Dir fehlt die Permission ottonienbundles.debug.");
            return;
        }
        boolean target;
        if (value == null) {
            target = !debugLog.isEnabled();
        } else if (value.equals("on")) {
            target = true;
        } else if (value.equals("off")) {
            target = false;
        } else {
            send(sender, "Verwendung: /ottonienbundle debug [on|off]");
            return;
        }
        debugLog.setEnabled(target);
        send(sender, "Debug-Ausgabe für Bündelklicks ist jetzt " + (target ? "an" : "aus")
                + ". Die Zeilen stehen in der Serverkonsole.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("ottonienbundles.reload")) {
            send(sender, "Dir fehlt die Permission ottonienbundles.reload.");
            return;
        }
        BundleConfigManager.LoadOutcome outcome = config.load();
        if (outcome.success()) {
            send(sender, "bundles.json neu geladen: bundleSize " + outcome.bundleSize() + ", "
                    + outcome.entryCount() + " Item-Eintrag/Einträge aktiv.");
        } else {
            send(sender, "Reload fehlgeschlagen, die bisherige Konfiguration bleibt aktiv (bundleSize "
                    + outcome.bundleSize() + ", " + outcome.entryCount() + " Item-Eintrag/Einträge). Fehler:");
            for (String error : outcome.errors()) {
                send(sender, " - " + error);
            }
        }
    }

    private void inspect(CommandSender sender) {
        if (!sender.hasPermission("ottonienbundles.inspect")) {
            send(sender, "Dir fehlt die Permission ottonienbundles.inspect.");
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "inspect kann nur von einem Spieler ausgeführt werden.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!BukkitStackView.isBundle(held)) {
            send(sender, "In der Haupthand liegt kein Bündel.");
            return;
        }
        BundleRules rules = config.rules();
        BukkitStackView view = new BukkitStackView(held);
        List<StackView> contents = view.bundleContents();
        Fraction points = rules.points(contents);
        boolean managed = rules.appliesToEveryBundle() || rules.involvesConfigRule(view);
        send(sender, "Bündel " + held.getType() + ": " + points + " von " + rules.bundleSize()
                + " Punkten belegt (" + Occupancy.formatPercent(rules.occupancy(contents)) + "), "
                + contents.size() + " Eintrag/Einträge, Plugin-Regeln wirksam: " + (managed ? "ja" : "nein")
                + ", Gewicht für den Balken des Clients: " + VanillaWeight.weight(contents));
        describe(sender, rules, contents, 1);
    }

    private void describe(CommandSender sender, BundleRules rules, List<StackView> entries, int depth) {
        String indent = "  ".repeat(depth);
        for (StackView entry : entries) {
            BukkitStackView v = (BukkitStackView) entry;
            String rawId = BukkitStackView.rawCustomId(v.stack());
            String model = BukkitStackView.rawItemModel(v.stack());
            UnitWeight w = rules.resolve(entry);
            StringBuilder line = new StringBuilder(indent).append(entry.amount()).append(" x ")
                    .append(v.stack().getType())
                    .append(", ID: ").append(rawId == null ? "keine" : "'" + rawId + "'")
                    .append(", Modell: ").append(model == null ? "Standard" : "'" + model + "'")
                    .append(", Quelle: ").append(w.kind()).append(" (").append(w.detail()).append(")");
            if (w.allowed()) {
                line.append(", Punkte je Stück: ").append(w.weight())
                        .append(", Punkte gesamt: ").append(w.weight().times(entry.amount()));
            }
            if (!entry.isBundle() && w.allowed() && !entry.hasBees()) {
                int carried = entry.carriedMaxStackSize();
                line.append(", Stackgröße im Bündel ").append(carried);
                if (StoredItems.isMarked(v.stack())) {
                    line.append(" (eingelagert, außerhalb ").append(entry.effectiveMaxStackSize()).append(")");
                }
                line.append(StoredSize.isExact(w.weight(), rules.bundleSize(), carried)
                        ? ", Balken exakt" : ", Balken genähert");
            }
            send(sender, line.toString());
            if (entry.isBundle()) {
                if (depth >= MAX_INSPECT_DEPTH) {
                    send(sender, indent + "  (weitere Verschachtelung nicht angezeigt)");
                } else {
                    describe(sender, rules, entry.bundleContents(), depth + 1);
                }
            }
        }
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("ottonienbundles.reload") && "reload".startsWith(args[0].toLowerCase())) {
                out.add("reload");
            }
            if (sender.hasPermission("ottonienbundles.inspect") && "inspect".startsWith(args[0].toLowerCase())) {
                out.add("inspect");
            }
            if (sender.hasPermission("ottonienbundles.debug") && "debug".startsWith(args[0].toLowerCase())) {
                out.add("debug");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")
                && sender.hasPermission("ottonienbundles.debug")) {
            for (String option : List.of("on", "off")) {
                if (option.startsWith(args[1].toLowerCase())) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
