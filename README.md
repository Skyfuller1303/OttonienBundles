# OttonienBundles

Paper-Plugin für Minecraft Java 1.21.11, mit dem Vanilla-Bündel eine frei einstellbare Menge an Items aufnehmen. Jedes Item kostet eine festgelegte Anzahl Punkte, das Bündel hat eine einstellbare Gesamtgröße. So passen zum Beispiel mehrere nicht stapelbare Items in ein Bündel, ohne dass sich das Item selbst verändert.

Spieler brauchen weder Mod noch Resourcepack. Bedienung, Vorschau und Füllbalken bleiben die von Minecraft.

## Funktionen

- **Gesamtgröße einstellbar:** `bundleSize` legt fest, wie viele Punkte ein Bündel fasst (1 bis 96, Standard 64 wie in Vanilla).
- **Kosten je Item:** `size` legt fest, wie viele Punkte ein Stück eines Items kostet. Items ohne Eintrag kosten wie in Vanilla `64 / Stackgröße` Punkte.
- **Erkennung über PDC-ID oder Item-Modell:** Ein Eintrag passt entweder auf den PDC-Wert `ottonienitems:item_id` oder auf die `item_model`-ID, wie sie im Resourcepack steht (zum Beispiel `ottonien:item/erbsen`).
- **Exakte Berechnung:** Ob ein Item passt, entscheidet der Server mit exakten Brüchen. Gemischte Inhalte sind möglich.
- **Passender Füllbalken:** Items im Bündel tragen intern eine angepasste Stackgröße, damit der unveränderte Client den Füllstand richtig anzeigt. Beim Herausnehmen erhalten sie exakt ihren ursprünglichen Zustand zurück.
- **Sicherer Reload:** Die Konfiguration wird vollständig geprüft, bevor sie aktiv wird. Bei einem Fehler bleibt die alte aktiv.

## Voraussetzungen

- Paper 1.21.11 (getestet mit Build 132)
- Java 21

Es gibt keine Abhängigkeit zu anderen Plugins.

## Installation

1. JAR bauen (siehe [Bauen](#bauen)) oder aus einem Release herunterladen.
2. `OttonienBundles-<version>.jar` in den Ordner `plugins` des Servers legen.
3. Server starten. Beim ersten Start entsteht `plugins/OttonienBundles/bundles.json`. Eine vorhandene Datei wird nie überschrieben.

## Konfiguration

`plugins/OttonienBundles/bundles.json`:

```json
{
  "bundleSize": 96,
  "items": {
    "ottonien:erbsen": { "size": 4 },
    "ottonien:item/salz": { "size": 8 }
  }
}
```

| Feld | Bedeutung |
|---|---|
| `bundleSize` | Punkte, die ein Bündel fasst. Ganze Zahl von 1 bis 96. Fehlt das Feld, gilt 64. |
| `items` | Einträge je Item. Der Schlüssel ist eine PDC-ID oder eine Item-Modell-ID. |
| `size` | Punkte pro Stück. Ganze Zahl von 1 bis 1000000. Fehlt der Wert, gelten die Vanilla-Kosten. |

Mit diesem Beispiel passen in ein Bündel 96 Steine, 24 Erbsen oder 12 Salz, oder eine Mischung davon.

Reihenfolge der Regeln:

1. Was Vanilla nicht in Bündeln erlaubt (zum Beispiel Shulker-Kisten), bleibt ausgeschlossen.
2. Ein Bündel im Bündel kostet 4 Punkte plus seinen Inhalt, ein Bienenstock mit Bienen 64 Punkte.
3. Passt die PDC-ID auf einen Eintrag, gilt dessen `size`. Sonst wird das gesetzte `item_model` geprüft.
4. Sonst gelten die Vanilla-Kosten.

Die Obergrenze 96 kommt vom Füllbalken des Clients: Größere Bündel würden schon "voll" anzeigen, während der Server noch Items annimmt.

Nach Änderungen lädt `/ottonienbundle reload` die Datei neu. Bestehende Bündel werden bei der nächsten Interaktion angepasst.

## Befehle

| Befehl | Permission | Beschreibung |
|---|---|---|
| `/ottonienbundle reload` | `ottonienbundles.reload` | Lädt und prüft `bundles.json`. Funktioniert auch in der Konsole. |
| `/ottonienbundle inspect` | `ottonienbundles.inspect` | Zeigt für das Bündel in der Haupthand die belegten Punkte und je Eintrag ID, Modell, Regel und Kosten. |
| `/ottonienbundle debug [on\|off]` | `ottonienbundles.debug` | Schreibt Diagnosezeilen zu Bündelklicks in die Konsole. Nach einem Neustart wieder aus. |

Alle Permissions sind standardmäßig nur für Operatoren freigegeben. Für das normale Benutzen von Bündeln braucht niemand eine Permission.

## Bekannte Grenzen

- **Füllbalken teils nur genähert:** Der Server entscheidet immer exakt, der Balken des Clients nicht in jedem Fall. Items mit Haltbarkeit (Werkzeuge, Waffen) lassen den Balken zu früh voll erscheinen, wenn `bundleSize` über 64 liegt: Bei 96 zeigt ein Schwert schon voll an, obwohl noch 32 Punkte frei sind. Genaue Werte zeigt `/ottonienbundle inspect`.
- **Kreativ-Inventar:** Im Inventar des Kreativmodus berechnet der Client Klicks selbst. Dort gelten die Vanilla-Gewichte. In geöffneten Behältern und im Survival-Modus greifen die Regeln des Plugins.
- **Keine Prozentanzeige im Tooltip:** Das Plugin ändert den Tooltip nicht. Der Füllstand ist nur am Balken zu sehen.

## Bauen

```sh
./gradlew build        # Linux/macOS
gradlew.bat build      # Windows
```

Die JAR liegt danach unter `build/libs/`. Der Build führt auch die Unit-Tests aus. Die Berechnung liegt im Paket `de.ottonien.bundles.core` und lässt sich ohne Server testen.

## Weitere Dokumentation

[docs/TECHNIK.md](docs/TECHNIK.md) beschreibt Details: eingelagerte Form, Überlaufschutz, alle Wege aus dem Bündel, die am Servercode geprüften Stellen, das Format der Debug-Ausgabe und die Client-Testtabelle.
