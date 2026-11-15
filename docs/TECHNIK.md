# OttonienBundles: technische Dokumentation

OttonienBundles ist ein Paper-Plugin für Minecraft Java 1.21.11 (Java 21). Es erlaubt normalen und gefärbten Vanilla-Bündeln, mehrere Ottonien-Items aufzunehmen, auch wenn jedes dieser Items im Inventar die Stackgröße 1 hat. Das Plugin berechnet bei jedem Einpacken den freien Platz exakt und erlaubt gemischte Inhalte. Das Bündel-Item, seine Vorschau und die normale Bedienung liefert weiterhin Minecraft. Spieler brauchen einen unveränderten Client.

Stand: Teilstand. Die Platzberechnung, die Konfiguration, die Erkennung der Klick-Gesten und die Umwandlung der Bündelinhalte sind durch automatische Tests abgedeckt. Die Klickbehandlung und alle Wege, auf denen ein Item ein Bündel verlässt, sind am Servercode von Paper 1.21.11 Build 132 belegt (siehe "Am Servercode belegt"). Ein vollständiger Durchlauf mit einem echten Client steht noch aus (siehe Client-Testtabelle).

Gegenüber der ursprünglichen Spezifikation gelten diese Änderungen:

- Schema: Statt `bundleCapacity` je Item (Stück pro vollem Bündel) gibt es eine globale `bundleSize` in Punkten und je Item `size` (Punkte pro Stück). `bundleCapacity` wird nicht mehr gelesen und ist ein Konfigurationsfehler mit Hinweis auf die Umrechnung.
- Wirkung auf alle Bündel: Bei `bundleSize` ungleich 64 übernimmt das Plugin Einpacken und Entnehmen für jedes Bündel und jedes Item, auch für reine Vanilla-Inhalte. Bei `bundleSize` 64 bleiben Bündel ohne konfigurierte Items bei Vanilla.
- Form im Bündel: Damit der Füllbalken des unveränderten Clients stimmt, trägt ein Item im Bündel eine angepasste Stackgröße, gleiche Items werden dort zu einem Eintrag zusammengefasst. Außerhalb des Bündels hat das Item wieder exakt seinen ursprünglichen Zustand. Einzelheiten stehen unter "Eingelagerte Form".

Die ursprüngliche Erwartungstabelle gilt bei `bundleSize` 64 mit den umgerechneten Werten unverändert und ist als Unit-Test hinterlegt.

## Installation

1. Das Plugin mit `gradlew.bat build` (Windows) oder `./gradlew build` bauen. Die JAR liegt danach unter `build/libs/OttonienBundles-1.0.0.jar`.
2. Die JAR in den Ordner `plugins` eines Paper-Servers 1.21.11 legen. Zielbuild ist Paper 1.21.11 Build 132.
3. Server starten. Beim ersten Start entsteht `plugins/OttonienBundles/bundles.json` mit Beispielwerten. Bestehende Dateien werden nie überschrieben.

OttonienItems ist nicht erforderlich. Das Plugin hat kein `depend` und liest nur den Schlüssel `ottonienitems:item_id` direkt am ItemStack.

## Konfiguration: bundles.json

```json
{
  "bundleSize": 64,
  "items": {
    "ottonien:erbsen": {"size": 4},
    "ottonien:salz": {"size": 8}
  }
}
```

Ein Bündel hat `bundleSize` Punkte Platz. Jedes Stück eines Items kostet Punkte. Die Belegung ist die Summe der Punkte geteilt durch `bundleSize`.

`bundleSize` ist eine ganze Zahl von 1 bis 96. Fehlt das Feld oder ist es `null`, gilt 64, das entspricht Vanilla. Die Obergrenze 96 folgt aus dem Füllbalken des Clients: 96 ist die größte Stackgröße, die ein Eintrag im Bündel tragen darf, ein Stück für einen Punkt lässt sich also höchstens als 1/96 darstellen. Bei größeren Bündeln zeigte der Balken "voll", obwohl der Server noch annimmt (am Client mit `bundleSize` 128 beobachtet). Siehe "Wann der Balken nur genähert ist".

`size` sind die Punkte pro Stück, eine ganze Zahl von 1 bis 1000000. Fehlt der Eintrag oder das Feld oder ist der Wert `null`, gelten die Vanilla-Kosten. Ein `size` über `bundleSize` ist gültig, das Item passt dann nie in ein Bündel. Der Wert `0`, ein leerer String, Zahlen außerhalb der Grenzen, Dezimalzahlen wie `8.0`, andere Typen, unbekannte Felder, doppelte JSON-Schlüssel und kaputtes JSON sind Fehler.

`bundleCapacity` aus früheren Versionen gibt es nicht mehr. Steht das Feld noch in der Datei, ist das ein Fehler mit dem Hinweis `size = 64 / bundleCapacity`, und die Datei wird nicht aktiviert. Aus `{"bundleCapacity":16}` wird `{"size":4}`.

Die Schlüssel in `items` müssen nur nicht leer sein. Sie werden exakt als Zeichenkette mit dem PDC-Wert verglichen. Es gibt keine Normalisierung und keine automatische Ergänzung eines Namespaces.

Die Kosten eines Stücks folgen dieser Reihenfolge:

1. Lässt Vanilla den Gegenstand nicht in einem Bündel zu (zum Beispiel Shulker-Kisten), bleibt er ausgeschlossen, auch bei vorhandenem Eintrag.
2. Sonderregeln: Ein verschachteltes Bündel kostet 4 Punkte plus die Punkte seines Inhalts. Ein Bienenstock oder Bienennest mit Bienen kostet 64 Punkte.
3. Gibt es einen Eintrag mit `size`, kostet jedes Stück `size` Punkte.
4. Sonst gelten die Vanilla-Kosten `64 / effektive Stackgröße` als exakter Bruch: 1 Punkt für ein Item mit Stackgröße 64, 4 Punkte bei Stackgröße 16, 64 Punkte für ein nicht stapelbares Item.

Beispiele: Bei `bundleSize` 64 verhalten sich Items ohne Eintrag genau wie in Vanilla. Bei `bundleSize` 96 passen 96 Steine, 24 Erbsen (`size` 4) oder 1 Schwert in ein Bündel. Bei `bundleSize` 32 passen 32 Steine oder 8 Erbsen, ein Schwert passt nie.

Beim Einpacken gilt: passende Menge = min(angebotene Menge, floor(freie Punkte / Punkte pro Stück)), mindestens 0. Die Entscheidung läuft mit exakten Brüchen nach der Konfiguration, der PDC-ID und der ursprünglichen Stackgröße, nur die Anzeige rundet. Die Stackgröße, die ein Item im Bündel trägt, dient allein der Anzeige im Client und geht in die Entscheidung nicht ein.

Ein Reload prüft die Datei vollständig, bevor er sie aktiviert. Bei einem Fehler bleibt die bisherige Konfiguration aktiv. Bestehende Items und Inventare werden beim Reload nicht verändert. Ein Bündel wird erst beim nächsten Einpacken oder Entnehmen an die neuen Regeln angepasst (siehe "Eingelagerte Form"). Wird ein Bündel durch eine kleinere `bundleSize` oder ein größeres `size` überfüllt, bleibt sein Inhalt entnehmbar, und es passt erst wieder etwas hinein, wenn genug Platz frei ist. Ist beim Serverstart die Datei ungültig, laufen nur Vanilla-Regeln, bis ein Reload erfolgreich war, und die Fehler stehen in der Konsole.

### Schlüssel: PDC-ID oder Item-Modell

Ein Schlüssel in `items` ist entweder der PDC-Wert `ottonienitems:item_id` oder die Modell-ID aus der Komponente `item_model`, so wie sie im Resourcepack verwendet wird, zum Beispiel `ottonien:item/erbsen`. Das Plugin sucht zuerst nach der PDC-ID und nur dann nach dem Modell, wenn die PDC-ID keinen Eintrag hat. Berücksichtigt wird nur ein ausdrücklich gesetztes `item_model`; das Standardmodell eines Materials (etwa `minecraft:bread`) zählt nicht. Teilen sich mehrere Items dasselbe Modell, gilt für alle derselbe Eintrag. Beide Werte werden exakt verglichen. `/ottonienbundle inspect` zeigt für jeden Eintrag ID und Modell.

```json
{
  "bundleSize": 96,
  "items": {
    "ottonien:item/erbsen": { "size": 4 },
    "ottonien:item/salz": { "size": 8 }
  }
}
```

### Hinweis zu älteren Item-Plugins

Ein älteres Ottonien-Item-Plugin trägt denselben Schlüssel `ottonienitems:item_id` (STRING), speichert aber nach seinem Quellcode nur den nackten Slug, zum Beispiel `erbsen` statt `ottonien:erbsen`. Dieser Beleg stammt aus dem Quellcode und nicht aus einem tatsächlich erzeugten Item. Seine Items setzen außerdem ein `item_model` der Form `ottonien:item/<slug>`, über das sie sich ebenfalls erfassen lassen. Weil der Wert exakt verglichen wird, trifft ein solches Altitem den Eintrag `ottonien:erbsen` nicht und folgt dem Vanilla-Fallback. Ein Admin kann bewusst einen eigenen Schlüssel `erbsen` in `bundles.json` eintragen, um solche Items zu erfassen. `/ottonienbundle inspect` zeigt den rohen PDC-Wert, damit sich das prüfen lässt.

## Befehle und Permissions

| Befehl | Permission | Standard | Verhalten |
|---|---|---|---|
| `/ottonienbundle reload` | `ottonienbundles.reload` | Operatoren | Lädt und validiert `bundles.json`, auch über die Konsole. Bei Fehler bleibt die alte Konfiguration aktiv. |
| `/ottonienbundle inspect` | `ottonienbundles.inspect` | Operatoren | Zeigt für das Bündel in der Haupthand die belegten Punkte von `bundleSize`, die Belegung in Prozent und das Gewicht, aus dem der Client seinen Balken berechnet. Je Eintrag: Menge, rohe ID, gesetztes Item-Modell, Quelle der Regel (Eintrag, Vanilla, Sonderregel), Punkte je Stück und gesamt, die Stackgröße im Bündel und außerhalb sowie ob der Balken für diesen Eintrag exakt oder genähert ist. Nur lesend, nur für Spieler. |
| `/ottonienbundle debug [on\|off]` | `ottonienbundles.debug` | Operatoren | Schaltet Diagnosezeilen für Bündelklicks und Rückwandlungen in der Serverkonsole ein oder aus, auch über die Konsole. Ohne Argument wird umgeschaltet. Standard ist aus, der Zustand übersteht keinen Neustart. |

Für den normalen Gebrauch von Bündeln braucht kein Spieler eine Permission.

## Verhalten im Spiel

Bei `bundleSize` 64 übernimmt das Plugin das Einpacken, wenn das Bündel oder das angebotene Item (auch verschachtelt) von einem Konfigurationseintrag betroffen ist, und das Entnehmen aus solchen Bündeln. Alle anderen Bündel laufen unverändert über Vanilla. Bei `bundleSize` ungleich 64 übernimmt das Plugin Einpacken und Entnehmen für jedes Bündel und jedes Item. Ein Bündel, in dem noch Einträge in eingelagerter Form aus einer früheren Konfiguration liegen, behandelt das Plugin in jedem Fall selbst.

In einem vom Plugin verwalteten Bündel entnimmt das Plugin jeden Eintrag selbst, auch Einträge ohne Umwandlung. Ein solches Bündel kann für Vanilla mehr als voll sein. Vanillas eigene Entnahme in einen Slot, der das Item nicht annimmt, legt den Rest per `tryInsert` zurück ins Bündel, was in diesem Zustand fehlschlägt und das Item löschen würde.

Als Einpack-Geste gilt, wie in Vanilla seit 1.21.2, ein einfacher Linksklick mit dem Bündel auf der einen und einem Item auf der anderen Seite (Bündel am Cursor und Item im Slot oder umgekehrt). Liegt auf beiden Seiten ein Bündel, nimmt das Bündel am Cursor das Bündel im Slot auf. Als Entnahme-Geste gilt ein einfacher Rechtsklick mit dem Bündel auf der einen und nichts auf der anderen Seite (Bündel im Slot und leerer Cursor, oder Bündel am Cursor und leerer Slot). Ein Rechtsklick mit Items auf beiden Seiten tauscht. Shift-Klick, Zifferntasten, Ziehen, Doppelklick und Wegwerfen packen nie ein und entnehmen nie.

Die Entscheidung fällt allein anhand des Klicktyps und des Zustands auf dem Server. Die von Paper gemeldete `InventoryAction` wird nicht ausgewertet, weil sie `NOTHING` lautet, sobald Vanilla das Bündel für voll hält. Das Plugin sperrt die Vanilla-Aktion, schreibt Bündel und Gegenseite gemeinsam und sendet danach den Serverzustand an den Client zurück. Klicks, die andere Plugins schon abgebrochen haben, werden ignoriert.

Übernimmt das Plugin den Klick und liegt der angeklickte Slot in einem Ergebnis- oder Rüstungsslot, lehnt das Plugin das Einpacken ab. Vanilla schützt diese Slots mit Prüfungen, die die Paper-API nicht anbietet. Aus demselben Grund legt das Plugin einen entnommenen Eintrag nur in Slots ab, die jedes Item annehmen: Lager- und Schnellleistenslots von Spielerinventar, Kiste, Endertruhe, Fass, Shulker-Kiste, Spender, Werfer und Trichter sowie das Handwerksfeld. In anderen Slots (zum Beispiel Ofen, Braustand, Amboss, Pferd) wird der Klick abgelehnt, das Item lässt sich über den Cursor entnehmen.

Klicks im Inventar des Kreativmodus (`ClickType.CREATIVE`) werden nicht abgefangen, siehe "Bekannte Clientgrenzen".

Das Plugin schreibt keine eigene Zeile in den Tooltip, siehe "Keine eigene Tooltipzeile".

## Eingelagerte Form

Der Client berechnet seinen Füllbalken aus der Stackgröße jedes Eintrags: Ein Stück belegt `1/Stackgröße`. Damit er dasselbe Ergebnis erhält wie das Plugin, trägt ein Item im Bündel die Stackgröße `n` mit `1/n = Kosten / bundleSize`, also `n = bundleSize / Kosten`. Bei `bundleSize` 64 liegen sechzehn Erbsen (`size` 4) als ein Eintrag mit Menge 16 und Stackgröße 16 im Bündel, der Balken zeigt nach der ersten Erbse 1/16 und nach der sechzehnten voll. Bei `bundleSize` 128 tragen Erbsen die Stackgröße 32.

Hat ein Item von sich aus schon diese Stackgröße, wird es nicht umgewandelt. Bei `bundleSize` 64 betrifft die Umwandlung deshalb nur Items mit Eintrag, alle üblichen Vanilla-Items bleiben unberührt.

Als Stackgröße im Bündel verwendet das Plugin nur die Teiler von 960 bis 99: 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 16, 20, 24, 30, 32, 40, 48, 60, 64, 80 und 96. Der Grund steht unter "Überlaufschutz". Ist `bundleSize / Kosten` keine dieser Zahlen, nimmt das Plugin die nächstgrößere. Der Balken zeigt dann etwas weniger als die wahre Belegung und nie "voll", solange der Server noch Platz sieht. Liegt der ideale Wert über 96, bleibt nur 96, und der Balken zeigt mehr als die wahre Belegung.

### Keine eigene Tooltipzeile

Das Plugin schreibt keine eigene Zeile mehr in den Tooltip. Die frühere Zeile `Belegt: …` und der zwischenzeitliche Textbalken mit Prozentzahl wurden auf Wunsch entfernt; Bündel, die eine solche Zeile noch tragen, verlieren sie bei der nächsten Interaktion. Der Füllstand ist im Spiel nur am Balken des Clients ablesbar. Wo dieses Dokument noch `Belegt: …` als Erwartung nennt, gilt stattdessen die exakte Angabe aus `/ottonienbundle inspect`.

### Wann der Balken nur genähert ist

Die Entscheidung des Servers und `/ottonienbundle inspect` sind immer exakt. Der Balken des Clients ist in diesen Fällen nur eine Näherung:

- `bundleSize / Kosten` ist größer als 96. Mit der Obergrenze 96 für `bundleSize` tritt das nur noch bei Items auf, deren Vanilla-Kosten unter einem Punkt liegen (Stackgröße 99). Der Balken zeigt dann etwas mehr als die wahre Belegung.
- `bundleSize / Kosten` ist keine der verwendbaren Stackgrößen, zum Beispiel `size` 9 bei `bundleSize` 64 (ideal 7,1, verwendet 8) oder `bundleSize` 100 mit `size` 2 (ideal 50, verwendet 60). Der Balken zeigt zu wenig.
- Ein Item, dessen eigene Stackgröße kein Teiler von 960 ist (zum Beispiel 99 oder 50 durch ein anderes Plugin), wird auch bei `bundleSize` 64 auf die nächste verwendbare Größe umgestellt (99 wird 96, 50 wird 60).
- Items mit Haltbarkeit können nur die Stackgröße 1 tragen, das Spiel erlaubt keine stapelbaren Items mit Haltbarkeit. Bei `bundleSize` 96 kostet ein Schwert 64 Punkte, also zwei Drittel des Bündels, der Balken zeigt nach einem Schwert aber schon voll. Der Server nimmt danach noch Items für 32 Punkte an.
- Ein verschachteltes Bündel zählt für den Client immer 1/16 plus seinen Inhalt, ein Bienenstock mit Bienen immer ein ganzes Bündel. Bei `bundleSize` ungleich 64 weicht das von 4 beziehungsweise 64 Punkten ab.
- Überfüllte Bündel: Der Balken endet bei voll.
- Bis zur nächsten Interaktion bei Bündeln, deren Regeln sich durch einen Reload geändert haben, und bei Bündeln, die im Kreativ-Inventar befüllt wurden.

`/ottonienbundle inspect` nennt für jeden Eintrag, ob der Balken exakt oder genähert ist.

### Merker für den ursprünglichen Zustand

Den ursprünglichen Zustand merkt sich das Item selbst im PDC unter `ottonienbundles:original_max_stack` (INTEGER): der ausdrücklich gesetzte Wert der Komponente oder `0`, wenn die Komponente nicht gesetzt war. Beim Verlassen des Bündels wird der Marker entfernt und die Komponente exakt zurückgesetzt. `ottonienitems:item_id` und alle anderen Daten bleiben unberührt. Das entnommene Item ist damit `isSimilar` zu einem frisch erzeugten und stapelt sich mit ihm. Das Plugin prüft diese Umwandlung bei jedem Serverstart an Testitems und meldet das Ergebnis in der Konsole ("Selbsttest eingelagerte Form").

Nicht umgewandelt werden Bündel selbst, Bienenstöcke mit Bienen, Items mit Haltbarkeit und Items, deren Stackgröße schon passt. Für sie gilt die Punkteregel unverändert.

Wege aus dem Bündel:

- Rechtsklick im Inventar: Das Plugin gibt den im Tooltip per Mausrad ausgewählten Eintrag aus, ohne Auswahl den ersten. Aus einem Eintrag in eingelagerter Form gibt es höchstens so viele Stück aus, wie die ursprüngliche Stackgröße erlaubt, bei Erbsen also eines. Der Rest bleibt an derselben Stelle im Bündel. Ein Eintrag ohne Umwandlung verlässt das Bündel wie in Vanilla als Ganzes. Die Auswahl steht in der Serverkomponente, wird aber weder von `BundleMeta` noch von Papers `BundleContents` angeboten. Das Plugin liest sie deshalb über Reflection aus dem Serverobjekt (`net.minecraft.world.item.component.BundleContents.getSelectedItem()`). Das ist die einzige versionsgebundene Stelle. Die JAR ist dafür als Mojang-benannt gekennzeichnet (`paperweight-mappings-namespace: mojang`). Schlägt der Zugriff auf einer anderen Version fehl, warnt das Plugin einmal und entnimmt immer den ersten Eintrag.
- Ausleeren aus der Hand und Zerstörung eines Bündels, das als Item auf dem Boden liegt: Vanilla wirft die Einträge so aus, wie sie gespeichert sind. Jedes neue Item-Entity wird beim Entstehen zurückgewandelt und nach der ursprünglichen Stackgröße in mehrere Entities aufgeteilt (`ItemSpawnEvent`, davor `PlayerDropItemEvent`).
- Kreativ-Inventar: Der Client legt den Eintrag selbst in einen Slot. Der gemeldete Stack wird zurückgewandelt, überzählige Stücke kommen ins Inventar oder fallen beim Spieler zu Boden.
- Sicherheitsnetz: Ein markiertes Item außerhalb eines Bündels wird außerdem beim Aufheben durch Spieler, Tiere und Trichter, bei jedem Inventarklick auf Cursor und angeklicktem Slot und beim Betreten des Servers im Spielerinventar erkannt und zurückgewandelt. Dabei ändert sich die Gesamtmenge nie.

Tod, Verschieben, Weitergeben und Färben eines Bündels bewegen das Bündel als Ganzes, sein Inhalt bleibt eingelagert.

Anpassung an geänderte Regeln: Ein Reload schreibt nichts um. Beim nächsten Einpacken oder Entnehmen bringt das Plugin die Einträge des betroffenen Bündels, auch in verschachtelten Bündeln, auf den aktuellen Stand. Einträge im alten Format (einzelne Einträge mit ursprünglicher Stackgröße) werden umgewandelt und zusammengefasst. Bei geänderter `bundleSize` oder geändertem `size` erhalten die Einträge die neue Stackgröße, und Einträge, deren Menge sie übersteigt, werden geteilt. Braucht ein Eintrag keine Umwandlung mehr, wird er zurückgewandelt. Es geht dabei nie etwas verloren, ein dadurch überfülltes Bündel bleibt entnehmbar. Hat sich bei einem Entnahme-Klick der Inhalt auf diese Weise geändert, verwirft das Plugin diesen einen Klick, weil die Auswahl des Clients nicht mehr zu den Einträgen passt. Der nächste Klick entnimmt normal.

## Überlaufschutz

Vanilla rechnet das Gewicht eines Bündels mit Brüchen aus zwei `int` (`org.apache.commons.lang3.math.Fraction`). Der Nenner ist das kleinste gemeinsame Vielfache aller Stackgrößen im Bündel. Mit beliebigen Stackgrößen läuft er über. Ein Probelauf gegen die Serverklassen von Paper 1.21.11 Build 132 mit sechs Einträgen der Stackgrößen 97, 89, 83, 79, 73 und 71 hat das belegt:

- `new BundleContents(List)` wirft `java.lang.ArithmeticException: overflow: mulPos`. Diesen Konstruktor verwenden `CraftMetaBundle.applyToItem` (jedes Schreiben über die Bukkit-API) und der Netzwerk-Codec beim Lesen.
- Der Speicher-Codec lehnt das Bündel beim Laden mit `Excessive total bundle weight` ab. Das ganze Bündel-Item lässt sich dann nicht mehr lesen, sein Inhalt wäre verloren.
- `BundleContents.Mutable.tryInsert` wirft `overflow: mul`. `BundleContents.getMaxAmountToAdd` wirft ebenfalls, CraftBukkit ruft es im Klick-Handler noch vor dem `InventoryClickEvent` auf.

Das Plugin schließt den Überlauf für jedes Bündel aus, das es schreibt:

1. Im Bündel tragen Items nur Stackgrößen, die 960 teilen (siehe "Eingelagerte Form"). Der Nenner jeder Summe teilt damit 960, gleich wie viele Einträge und Verschachtelungen es gibt. Der Anteil 1/16 eines verschachtelten Bündels passt dazu.
2. Das Vanilla-Gewicht eines Bündels, das das Plugin schreibt, ist höchstens 2000. Das größte Zwischenergebnis in Vanillas Rechnung entsteht, wenn zwei solche Bündel gegeneinander geprüft werden: 960 · 960 · 2000 = 1 843 200 000, das liegt unter 2 147 483 647.
3. `bundleSize` ist auf 96 begrenzt (Grund: Füllbalken, siehe oben). Das billigste Stück kostet 64/99 Punkte, es passen also höchstens 148 Stück hinein. Das Gewicht bleibt damit auch nach einem ungünstigen Reload weit unter 2000.
4. Vor jedem Schreiben prüft das Plugin das Ergebnis (`VanillaWeight.isSafe`: alle Stackgrößen in jeder Tiefe verwendbar, Gewicht höchstens 2000, Verschachtelung unter 32). Schlägt die Prüfung fehl, lehnt es den Klick ab und lässt das Bündel unverändert. Die Debug-Ausgabe nennt dann "Überlaufschutz".

Ein Probelauf mit 2035 Einträgen, Gewicht 4031/2, gemischten Nennern, Verschachtelung und einem angebotenen Item der Stackgröße 99 lief ohne Ausnahme durch Konstruktor, Speicher-Codec, `getMaxAmountToAdd` und `removeOne`. Der Netzwerk-Codec ließ sich außerhalb eines laufenden Servers nicht ausführen und ist nur am Bytecode geprüft (er ruft denselben Konstruktor auf).

Nicht abgedeckt: Bündel, die das Plugin nie anfasst (bei `bundleSize` 64 ohne konfigurierte Items), und Bündel, die über das Kreativ-Inventar oder Befehle mit ungewöhnlichen Stackgrößen befüllt werden. Dieses Risiko besteht in Vanilla auch ohne das Plugin.

## Am Servercode belegt

Geprüft wurde die gepatchte Server-JAR von Paper 1.21.11 Build 132 (`versions/1.21.11/paper-1.21.11.jar`, Mojang-Namen) mit `javap -c`.

- `AbstractContainerMenu.doClick` bildet Taste 0 auf `ClickAction.PRIMARY` und Taste 1 auf `ClickAction.SECONDARY` ab und ruft `tryItemClickBehaviourOverride` auf. Dort wird zuerst das Item am Cursor gefragt (`overrideStackedOnOther`), danach das Item im Slot (`overrideOtherStackedOnMe`).
- `BundleItem.overrideStackedOnOther` packt nur bei `PRIMARY` und nicht leerem Slot ein (`BundleContents.Mutable.tryTransfer`). Bei `SECONDARY` und leerem Slot legt es ein Item ab. `BundleItem.overrideOtherStackedOnMe` packt nur bei `PRIMARY` und nicht leerem Cursor ein (`tryInsert`) und entnimmt bei `SECONDARY` und leerem Cursor. Ein Rechtsklick auf ein Item packt also nie ein.
- `ServerGamePacketListenerImpl.handleContainerClick` feuert das `InventoryClickEvent` für jeden Klick außer dem Ziehen, unabhängig von der ermittelten Aktion. Für einen Linksklick mit Bündel meldet es `PICKUP_ALL_INTO_BUNDLE`, `PICKUP_SOME_INTO_BUNDLE`, `PLACE_ALL_INTO_BUNDLE` oder `PLACE_SOME_INTO_BUNDLE`, und `NOTHING`, wenn `BundleContents.getMaxAmountToAdd` 0 liefert. Nach einer Stack-1-Erbse ist das Vanilla-Gewicht 1, die zweite Erbse kommt deshalb als `LEFT` mit `NOTHING` an. Ein Ziehen über genau einen Slot wandelt der Server in einen normalen Klick um, der dasselbe Event auslöst.
- Nach einem abgebrochenen Event gleicht derselbe Handler den Client über `broadcastChanges` beziehungsweise `broadcastFullState` mit dem Serverzustand ab. Cursor und Slot im abgebrochenen Event zu schreiben ist damit zulässig.
- `BundleContents.getWeight` rechnet mit `ItemStack.getMaxStackSize()`, also mit der Komponente `max_stack_size` einschließlich des Item-Defaults. `CraftItemStack.getMaxStackSize()` liefert denselben Wert.
- `BundleContents.canItemBeInBundle` verlangt nur ein nicht leeres Item, dessen `Item.canFitInsideContainerItems()` wahr ist. Die einzige Überschreibung steht in `BlockItem` und schließt Shulker-Kisten aus. Das entspricht `Tag.SHULKER_BOXES`.
- `CraftMetaBundle.applyToItem` erzeugt den Inhalt mit `new BundleContents(List)`. Dieser Konstruktor prüft kein Höchstgewicht. Der Speicher-Codec (`checkAndCreate`) lehnt nur ab, wenn die Bruchrechnung überläuft ("Excessive total bundle weight"), der Netzwerk-Codec ruft denselben Konstruktor ohne Prüfung auf. Ein Probelauf gegen die Serverklassen hat ein Bündel mit 16 Einträgen `rabbit_stew` (Stackgröße 1, Gewicht 16/1) über `ItemStack.CODEC` geschrieben und identisch wieder gelesen. Vanilla liefert für ein solches Bündel `getMaxAmountToAdd` 0 und entnimmt mit `removeOne` alle 16 Einträge einzeln.
- `BundleItem.onUseTick` leert ein Bündel in der Hand Stück für Stück, solange die Benutzen-Taste gehalten wird. Die Tooltipzeile wird deshalb zusätzlich beim Ende des Benutzens aktualisiert.
- `ServerGamePacketListenerImpl.handleSetCreativeModeSlot` löst für das Kreativ-Inventar nur ein `InventoryCreativeEvent` mit dem fertigen Stack aus und übernimmt danach `event.getCursor()`.

Wege, auf denen ein Eintrag ein Bündel verlässt. Eine Suche über alle Klassen der Server-JAR zeigt, dass außer `BundleItem` nur Datenpaket-Lootfunktionen (`ContainerComponentManipulators`), Prädikate, die Tooltip-Daten und Papers Paketfilter auf `BundleContents` zugreifen.

| Weg | Fundstelle im Servercode | Abdeckung im Plugin |
|---|---|---|
| Rechtsklick auf Bündel im Slot, Cursor leer | `BundleItem.overrideOtherStackedOnMe`, Zweig `SECONDARY`: `Mutable.removeOne`, Ergebnis an den Cursor | `BundleListener.extract`, Event abgebrochen, Plugin entnimmt selbst |
| Rechtsklick mit Bündel am Cursor auf leeren Slot | `BundleItem.overrideStackedOnOther`, Zweig `SECONDARY`: `removeOne`, `Slot.safeInsert`, Rest per `tryInsert` zurück | `BundleListener.extract`, nur in Slots, die jedes Item annehmen, sonst abgelehnt |
| Auswahl per Mausrad | `handleBundleItemSelectedPacket` ruft `AbstractContainerMenu.setSelectedBundleItemIndex`. Kein Event, es verlässt nichts das Bündel. `removeOne` nimmt den ausgewählten Eintrag, sonst Index 0 | Auswahl wird beim Entnehmen gelesen (`StoredItems.selectedIndex`) |
| Ausleeren aus der Hand | `BundleItem.onUseTick` und `dropContent`: `removeOneItemFromBundle`, dann `Player.drop`. `LivingEntity.drop` feuert `PlayerDropItemEvent` und ruft `Level.addFreshEntity`; bei abgebrochenem Event wandert der Stack des Entities zurück ins Inventar | `StoredFormGuard.onPlayerDrop` und `onItemSpawn` |
| Zerstörung des Bündel-Entities | `BundleItem.onDestroyed` ruft `ItemUtils.onContainerDestroyed`, das je Eintrag ein `ItemEntity` mit `addFreshEntity` erzeugt | `StoredFormGuard.onItemSpawn` |
| Jedes neue Item-Entity | `ServerLevel.addEntity` ruft `CraftEventFactory.doEntityAddEventCalling`, das für jedes `ItemEntity` `callItemSpawnEvent` auslöst | `StoredFormGuard.onItemSpawn` |
| Kreativ-Inventar | `handleSetCreativeModeSlot`, siehe oben | `StoredFormGuard.onClick` ersetzt den gemeldeten Stack |
| Tod, Fallenlassen, Behälter, Färben | Das Bündel bleibt ein Item, `BundleContents` wird nicht angefasst | nichts nötig |

Am laufenden Testserver (Build 132) ohne Client geprüft: Der Selbsttest beim Start besteht (Umwandlung, identische Rückwandlung, zusammengefasster Eintrag im Bündel, Auswahlindex lesbar). Ein per Befehl erzeugtes markiertes Item-Entity mit 16 Stück wird zu 16 Entities mit je einem Stück ohne Marker. Ein markiertes Item mit ursprünglich nicht gesetzter Komponente und 8 Stück bleibt ein Entity mit 8 Stück ohne Marker. Ein per Befehl erzeugtes Bündel-Entity mit einem eingelagerten Eintrag von 16 Stück und 5 Stöcken ergibt nach `/damage` 17 Entities, keines davon markiert.

## Bekannte Clientgrenzen

- Der Füllbalken des unveränderten Clients ist in den unter "Wann der Balken nur genähert ist" genannten Fällen nur eine Näherung. `/ottonienbundle inspect` stimmt immer.
- Der Client hält eine Erbse außerhalb des Bündels weiter für ein Item mit Stackgröße 1. Er sagt deshalb beim Einpacken in ein nicht leeres Bündel voraus, dass nichts passiert, und spielt gegebenenfalls den Ton für "passt nicht". Der Server packt trotzdem ein und korrigiert die Anzeige sofort. Beim Entnehmen sagt der Client den ganzen Eintrag voraus, der Server gibt die erlaubte Menge aus und korrigiert ebenfalls.
- Im Inventar des Kreativmodus rechnet der Client den Klick selbst aus und sendet dem Server nur die geänderten Slots. Lehnt der Client das Einpacken ab, sendet er nichts, das der Server auswerten könnte. Dort gelten deshalb die Vanilla-Gewichte, auch bei `bundleSize` ungleich 64: Eine Erbse von außen füllt das Bündel. Legt der Client dort ein Item mit Regel selbst ins Bündel, liegt es zunächst im alten Format darin und wird bei der nächsten Interaktion außerhalb des Kreativ-Inventars umgewandelt. Die Punkteregeln greifen im Survival- und Abenteuermodus sowie in jedem geöffneten Behälter (Kiste, Werkbank und so weiter), auch im Kreativmodus. Das Verhalten des Clients ist hier aus dem Protokoll abgeleitet und noch nicht am Client nachgestellt.
- Dass der Client den Linksklick auch dann sendet, wenn er das Bündel für voll hält, ist am Servercode nicht zu belegen und muss der Clienttest zeigen. Die Debug-Ausgabe macht es sichtbar.

## Debug-Ausgabe für den Clienttest

`/ottonienbundle debug on` schreibt für jeden Klick, an dem ein Bündel beteiligt ist, eine Zeile in die Serverkonsole: Spielmodus, `ClickType`, `InventoryAction`, Art der Ansicht, Slottyp, Slotnummern, Cursor, Slot-Item und die Entscheidung des Plugins (eingepackt, entnommen mit Eintrag und Auswahl, abgelehnt, an Regeln angepasst, Vanilla). Jede Rückwandlung durch das Sicherheitsnetz erzeugt eine eigene Zeile mit dem Auslöser. Beispiele für das Format:

```text
[OttonienBundles] [debug] Name (SURVIVAL) click=LEFT action=NOTHING view=CRAFTING slotType=CONTAINER rawSlot=10 slot=10 cursor=1xBUNDLE[max=1][Einträge=1] slotItem=1xRABBIT_STEW[id=ottonien:erbsen][max=1] -> CURSOR_BUNDLE_TAKES_SLOT: eingepackt 1 von 1 als 1xRABBIT_STEW[id=ottonien:erbsen][max=16][eingelagert, original max=1], belegt 8 von 64 Punkten, Vanilla-Gewicht 1/8
[OttonienBundles] [debug] Name (SURVIVAL) click=RIGHT action=PICKUP_FROM_BUNDLE view=CRAFTING slotType=CONTAINER rawSlot=9 slot=9 cursor=leer slotItem=1xBUNDLE[max=1][Einträge=1] -> SLOT_BUNDLE_GIVES_CURSOR: entnommen 1 von 2 aus Eintrag 0 (Auswahl -1), 2xRABBIT_STEW[id=ottonien:erbsen][max=16][eingelagert, original max=1] -> 1xRABBIT_STEW[id=ottonien:erbsen][max=1], belegt 4 von 64 Punkten, Vanilla-Gewicht 1/16
[OttonienBundles] [debug] Rückwandlung (ItemSpawnEvent): 16xRABBIT_STEW[max=16] -> 16 x 1xRABBIT_STEW[max=1]
```

"Vanilla-Gewicht" ist der Wert, aus dem der Client seinen Balken berechnet. Stimmt er mit belegten Punkten geteilt durch `bundleSize` überein, ist der Balken exakt.

Erscheint bei einem Klick keine Zeile, hat der Server kein Klick-Event erhalten. Steht dort `click=CREATIVE`, lief der Klick über das Kreativ-Inventar.

## Tests

`gradlew.bat build` führt die Unit-Tests aus (Punkteberechnung inklusive aller Zeilen der Erwartungstabelle bei `bundleSize` 64 sowie Fälle mit `bundleSize` 128 und 32, Schema-Validierung einschließlich des Fehlers für `bundleCapacity`, Bruchrechnung, Erkennung der Einpack- und der Entnahme-Geste, Wahl der Stackgröße im Bündel, Überlaufgrenze, Umwandlung, Rückwandlung, Teilen und Anpassung nach einem Reload). Die Berechnung liegt im Paket `de.ottonien.bundles.core` und läuft ohne Bukkit-Laufzeit.

### Client-Testtabelle

Ein erster Clienttest am 2026-11-14 auf Build 132 meldete, dass ein Bündel nach einer Erbse voll war. Belegte Ursache im Plugin: Es behandelte den Rechtsklick als Einpack-Geste und den Linksklick nur bei einer `*_INTO_BUNDLE`-Aktion. Die erste Erbse lief so über das Plugin, die zweite kam als `LEFT` mit `NOTHING` an und wurde Vanilla überlassen, das sie ablehnte. Das ist korrigiert. Danach kamen die eingelagerte Form, damit auch der Balken stimmt, und das Punktesystem mit `bundleSize` hinzu. Laut Serverlog stand der Spieler in dieser Sitzung außerdem im Kreativmodus, wo das Kreativ-Inventar die Regeln des Plugins grundsätzlich nicht erreicht (Punkt 1e). Der Test ist nach diesen Änderungen noch nicht wiederholt worden.

Punkte mit "offen" brauchen einen echten Client. Beim Ausführen bitte Ist, Paper-Build, Clientversion und Ergebnis eintragen und vorher `/ottonienbundle debug on` setzen.

| Nr. | Soll | Ist | Paper-Build | Clientversion | Ergebnis |
|---|---|---|---|---|---|
| 1a | Gecrafteter Bündel funktioniert im Survival-Inventar vollständig | offen | offen | offen | offen |
| 1b | Bündel aus dem Kreativinventar funktioniert im Survival-Inventar vollständig | offen | offen | offen | offen |
| 1c | Bündel per `/give @s minecraft:bundle` funktioniert | offen | offen | offen | offen |
| 1d | Gefärbtes Bündel in allen drei Varianten wie oben | offen | offen | offen | offen |
| 1e | Direktes Befüllen im Kreativ-Menü, Ergebnis und Abweichungen dokumentiert | Am Servercode: nur `InventoryCreativeEvent`, Plugin greift nicht ein, es gelten Vanilla-Gewichte. Am Client offen | 132 | offen | offen |
| 2a | 17 Erbsen: 16 passen, die 17. bleibt zurück, Bündel 100 %, ein Eintrag mit Menge 16 | offen | offen | offen | offen |
| 2b | Nach einer Entnahme lässt sich der freie Platz erneut füllen | offen | offen | offen | offen |
| 2c | Klickrichtung Bündel am Cursor, Item im Slot | offen | offen | offen | offen |
| 2d | Klickrichtung Item am Cursor, Bündel im Slot | offen | offen | offen | offen |
| 2e | Beide Richtungen im Zustand "Vanilla zeigt voll" | offen | offen | offen | offen |
| 2f | Pro Richtung Klicktyp, Slot, Cursor, InventoryAction und Eventlieferung erfasst | Am Servercode: `LEFT`, Aktion `*_INTO_BUNDLE` oder `NOTHING` bei "Vanilla voll", Event wird immer geliefert. Am Client offen (Debug-Ausgabe) | 132 | offen | offen |
| 3a | 8 Erbsen + 4 Salz bzw. 8 Erbsen + 2 Erbsensuppen: genau voll | offen | offen | offen | offen |
| 3b | 6 Erbsen + 2 Salz: es passen noch 6 Erbsen oder 3 Salz | offen | offen | offen | offen |
| 3c | 8 Erbsen + 64 Amethystscherben: 32 aufgenommen, 32 bleiben | offen | offen | offen | offen |
| 3d | Apfelkuchen, Zwiebel, normales Ragout je 100 % | offen | offen | offen | offen |
| 3e | Leeres Innenbündel 1/16, Innenbündel mit 4 Erbsen 5/16 | offen | offen | offen | offen |
| 3f | Amethyst, Salz, Zwiebel und Ragout werden unterschieden | offen | offen | offen | offen |
| 3g | Frisches Testsalz mit `maxStackSize: 64`: Stapel mit 64 gibt höchstens 8 ab | offen | offen | offen | offen |
| 4a | Einzelentnahme per Vorschau, Scrollauswahl und Reihenfolge; aus einem Erbsen-Eintrag kommt je Klick eine Erbse | Am Servercode und im Selbsttest: Auswahlindex lesbar. Am Client offen | 132 | offen | offen |
| 4b | Ausleeren aus der Hand, Metadaten (Name, Modell, ID) aller Items erhalten, keine markierten Items am Boden | Rückwandlung neuer Item-Entities am Testserver per Befehl belegt. Am Client offen | 132 | offen | offen |
| 4c | Volles Inventar, schnelle Klicks, Wechsel der Inventaransicht ohne Verlust oder Duplikate | offen | offen | offen | offen |
| 4d | Verschieben, Droppen, Weitergeben, Relog und Serverneustart erhalten Inhalt und IDs | offen | offen | offen | offen |
| 4e | Eingelagerte Einträge überstehen Speichern, Paket und Entnahme | Zusammengefasster Eintrag mit Menge 16 im Selbsttest am Server belegt, Speicher-Codec gegen die Serverklassen, Paketweg nur am Bytecode. Am Client offen | 132 | offen | offen |
| 5a | Reload mit fehlendem Feld, `null`, ungültigem Wert, altem `bundleCapacity` | Am Testserver: Start mit alter Datei meldet je Eintrag den Fehler mit Umrechnung, es gelten Vanilla-Regeln; Reload mit neuer Datei erfolgreich. Übrige Fälle als Unit-Tests | 132 | nicht relevant | teilweise |
| 5b | Reload mit größerem `size` oder kleinerer `bundleSize`, überfülltes Bündel lässt sich entleeren | offen | offen | offen | offen |
| 5c | Per Befehl überfülltes Bündel lässt sich entleeren | offen | offen | offen | offen |
| 5d | Kontrollen mit umbenannten und normalen Vanilla-Items ohne Konfiguration | offen | offen | offen | offen |
| 5e | Tooltipzeile `Belegt` bei `bundleSize` 64 nur bei wirksamer Regel, fremde Lore bleibt erhalten | offen | offen | offen | offen |
| 6a | Platzberechnung und Konfigurationsfälle als automatische Tests | `gradlew.bat build` grün, 99 Tests | nicht relevant | nicht relevant | bestanden |
| 7 | Test mit beiden JARs, danach mit deaktiviertem OttonienItems: Items werden weiter erkannt, OttonienBundles startet | offen | offen | offen | offen |
| 8a | Füllbalken des Clients zeigt den Plugin-Füllstand: 1/16 nach einer Erbse, voll nach sechzehn, auch gemischt (8 Erbsen + 4 Salz voll) | offen | offen | offen | offen |
| 8b | Entnommene Erbse stapelt sich nicht falsch und ist von einer frisch vergebenen nicht zu unterscheiden (Tooltip, erneutes Einpacken, `inspect`) | Im Selbsttest am Server: `isSimilar` und `equals` zum Original. Am Client offen | 132 | offen | offen |
| 8c | Bündel mit Erbsen als Item zerstören (Feuer, Kaktus): Erbsen liegen einzeln und unmarkiert am Boden | Am Testserver per Befehl belegt (17 Entities, keines markiert). Am Client offen | 132 | offen | offen |
| 8d | Altes Bündel mit 16 Einzel-Einträgen wird beim nächsten Klick zu einem Eintrag | Unit-Test. Am Client offen | offen | offen | offen |
| 8e | Reload `size` 4 auf 8 bei 16 Erbsen: nächster Klick teilt in 8 + 8, Bündel 200 %, alles entnehmbar; Eintrag entfernt: Einträge wieder einzeln | Unit-Tests. Am Client offen | offen | offen | offen |
| 8f | Entnahme im Kreativ-Inventar hinterlässt kein markiertes Item | offen | offen | offen | offen |
| 9a | `bundleSize` 96, Reload: 96 Steine passen (ein Eintrag, Stackgröße im Bündel 96), der 97. bleibt zurück, `Belegt: 100 %`, Balken exakt | Rechnung als Unit-Test. Am Client offen | offen | offen | offen |
| 9b | `bundleSize` 96: 24 Erbsen passen, die 25. nicht, ein Eintrag mit Stackgröße 24, Balken exakt (halb voll nach 12) | Am Client offen | offen | offen | offen |
| 9c | `bundleSize` 96: 1 Schwert passt, das zweite nicht. `Belegt: 66,7 %`, Balken zeigt schon voll (dokumentierte Näherung), 32 Steine passen noch dazu | Am Client offen | offen | offen | offen |
| 9d | `bundleSize` 96: Entnahme von Steinen und Schwert per Rechtsklick in beide Richtungen, auch bei Balken "voll", ohne Verlust | Am Client mit `bundleSize` 128 (vor Einführung der Obergrenze) für Holz, Erbsen und Rüben beobachtet, Debug-Log 2026-11-15 | 132 | 1.21.11 | Teilweise bestanden |
| 9e | `bundleSize` 32: 32 Steine oder 8 Erbsen passen, ein Schwert und ein Apfelkuchen nie | Rechnung als Unit-Test. Am Client offen | offen | offen | offen |
| 9f | Zurück auf `bundleSize` 64: ein mit 96 Steinen gefülltes Bündel zeigt `Belegt: 150 %`, nimmt nichts an, alles entnehmbar; nach dem Leeren wieder reines Vanilla-Verhalten | Unit-Tests für die Anpassung. Am Client offen | offen | offen | offen |
| 9g | `/ottonienbundle inspect` zeigt Punkte belegt/gesamt, je Eintrag Punkte je Stück und Quelle (Eintrag, Vanilla, Sonderregel) | offen | offen | offen | offen |
