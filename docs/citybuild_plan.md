# Freimarken — Anforderungen und Umsetzung

> **Der Freimarkenbund — eine Welt freier Städte.**
> Historisch deutsch inspirierter CityBuild mit Grundstücksstadt, eigenen
> Stadtgründungen, Handel, Farmwelten und gemeinschaftlichem Fortschritt.

Stand: 30.10.2026 · Paper 26.3 (Build 231) · Java 25 · Velocity 4.3.1 · MariaDB

**Gegenstück zu diesem Dokument:** [`Freimarken_Serverkonzept.pdf`](Freimarken_Serverkonzept.pdf)
(Konzeptstand 30.10.2026) ist die **Gestaltungsrichtlinie** — Branding, Namen, Farbwelt,
Stil. Dieses Dokument sagt, wie daraus ein laufender Server wird.

**Rangfolge bei Widersprüchen** [entschieden]: Was der Projektinhaber direkt festlegt, hat
Vorrang vor dem PDF. Das PDF ist Richtlinie, nicht Beschluss.

---

## 1. Wie dieses Dokument zu lesen ist

| Marke | Bedeutung |
|---|---|
| **[entschieden]** | Du hast es festgelegt. Wird nicht mehr diskutiert. |
| **[empfohlen]** | Mein Vorschlag mit Begründung — änderbar. |
| **[offen]** | Muss noch entschieden werden. Gesammelt in [Abschnitt 16](#16-offene-punkte). |
| **[belegt]** | Aus dem Webarchiv des alten Lumia — als Vorbild, nicht als Vorlage. |
| **[ungeprüft]** | Fremdsoftware-Stand, der für 26.3 nicht belegt ist. |

**Lumia ist Inspiration, nicht Bauplan.** Der Server lief 2014–2018 auf Minecraft 1.8 bis
1.10.2; das meiste davon ist technisch überholt. Was bleibt, sind die Ideen, die ihn
besonders machten — die Besitzleiter, der Spielermarkt, zwei Währungen, die
Anfängerfreundlichkeit — und ein paar erprobte Zahlenverhältnisse. Alles andere ist neu.

Die Grabung im Webarchiv steht in [Anhang A](#anhang-a-das-vorbild); 123 Originalseiten
liegen lokal unter `data/lumia-archiv/`.

---

## 2. Namen [entschieden]

| Element | Name |
|---|---|
| Server | **Freimarken** |
| Gesamtgebiet | **Der Freimarkenbund** |
| Hauptstadt (Welt mit Spawn) | **Altfurt** |
| Hauptspawn (Platz in Altfurt) | **Bundesmarkt** |
| Grundstücksstadt (Raster) | **Marktstadt** |
| Spielergründungen | **Freistädte** |
| Farmwelt | **Kornmark** |
| Wildnis / Freibau | **Hochwald** |
| Inselwelt (Meer) | **Hafenwerder** |
| Skyblock | **Wolkenwerder** |
| Spielwährung | **Mark** |
| Premiumwährung | **Goldmark** |
| Erspielbare Ränge | Reisender → Siedler → Bürger |
| Premium-Ränge | Förderer, Stifter |

Zwei Dinge macht der Name von selbst richtig, und ich nutze sie durchgehend: *Mark* ist
zugleich Grenzland und Münze, und *Werder* heißt Insel — Hafenwerder und Wolkenwerder sind
dieselbe Sache in Wasser und Wolke.

### 2.1 Die Gebäude in Altfurt

Aus dem Konzept-PDF [entschieden, dort als Vorschlag geführt] — und jedes Haus hat eine
Aufgabe im Spiel, nicht nur eine Fassade:

| Gebäude | Wofür | Was dort technisch passiert |
|---|---|---|
| **Bundesratshaus** | Stadtgründung und Verwaltung | `/freistadt gruenden`, Teleporter zu allen Freistädten, Bündnisse |
| **Bürgeramt** | Grundstücksverwaltung | Erste Parzelle, Zusammenlegen, Mitbauer — **und die Annahme von Regeln und Nutzungsbedingungen beim ersten Beitritt** |
| **Markthalle** | Spielerhandel und Läden | Mietbare Ladeneinheiten, Ladenverzeichnis, Abholfach |
| **Zunfthaus** | Berufe | Beruf wählen, Stand ansehen, Jobwand mit privaten Aufträgen |
| **Münzhaus** | Bank und Währung | Kontostand, Verlauf, Wechsel Mark ↔ Goldmark, Wechseldeckel sichtbar |
| **Kaufmannskontor** | Aufträge und Auktionen | `/markt`, Suchlauf über alle Läden |
| **Hafen von Altfurt** | Tor nach Hafenwerder | Insel nehmen, Bootsrennen, Angelwettbewerbe |
| **Kornpforte** | Tor nach Kornmark | Farmwelt, Reset-Ankündigung |
| **Forsttor** | Tor zum Hochwald | Freibau, Sicherung kaufen |
| **Wolkenturm** | Tor nach Wolkenwerder | Skyblock-Insel nehmen, Inselrangliste |

Dazu [empfohlen]: die **Freie Truhe** in der Markthalle (siehe [7.2](#72-die-freie-truhe-entschieden)).

### 2.2 Farbwelt [entschieden]

| Farbe | Wert | Verwendung |
|---|---|---|
| **Bundesrot** | `#8C3431` | Offizielles, Warnungen, Bundesratshaus |
| **Markgold** | `#C6A15B` | Währung, Preise, Premium, Hervorhebung |
| **Forstgrün** | `#395948` | Natur, Hochwald, Kornmark, Bestätigungen |
| **Schiefer** | `#333B40` | Grundton, Menühintergründe, Fließtext |

Die vier Farben wiederholen sich in Schildern, Menüs, Rangfarben und offiziellen Gebäuden.
Für Chat und Menüs heißt das: vier benannte Farben in der Sprachdatei, keine freihändigen
Farbcodes im Code — sonst driftet das Erscheinungsbild binnen eines Monats auseinander.

---

## 3. Zielbild

```
Gateway (25565, Hostname-Routing, Wake-on-Join)          ← bestehend
 ├── Haupt-Lobby ──Transfer──► freimarken.<domain>        ← führt auch zu 26.3-Paper, ATM10 …
 └── freimarken.<domain> ──► Freimarken (Paper 26.3)
      ├── altfurt        Hauptstadt: Bundesmarkt, Bundesratshaus, Markthalle, …
      ├── marktstadt     Rasterparzellen 64×64, zusammenlegbar
      ├── freistaedte    Spielergründungen, natürliches Gelände
      ├── hafenwerder    Inseln im Meer
      ├── wolkenwerder   Skyblock
      ├── hochwald       Freibau, Sicherung kostet
      ├── kornmark       Ressourcen, Reset je Version
      └── nether / end   Farmen
```

**Ein Server, viele Welten** [entschieden]. Kein zweites Backend geplant, realistisch
höchstens 20 Spieler — die Architektur wird trotzdem so gebaut, dass ein Vielfaches
möglich bleibt ([Abschnitt 14](#14-wachstum)).

### 3.1 Adresse

Vorerst eine Subdomain der Hauptadresse — Standard bleibt `mc.friedrich-dietrich.de`,
Freimarken hängt als eigener Hostname darunter. [entschieden]

Perspektivisch bekommt Freimarken eine **eigene Domäne**; der Manager soll das je Server
und je Umgebung können (siehe TODO). Drei Dinge hängen daran und sind beim Umstellen zu
beachten:

- Der **Resource-Pack-Link** steht in `server.properties` und kommt heute aus der globalen
  `public_base_url`. Bekommt Freimarken eine eigene Domäne, sollte er aus *dieser* kommen —
  sonst lädt der Spieler sein Paket von einer fremd aussehenden Adresse.
- Die **Spielerseite** ([12b](#12b-die-spielerseite-entschieden)) gehört unter dieselbe
  Domäne wie der Spielserver. Ein Zertifikat braucht sie, der Spielserver nicht —
  Minecraft spricht kein TLS.
- Ein **Domänenwechsel ändert keine UUIDs** und damit keinen Besitz. Er ist jederzeit
  möglich, anders als E1 — nur alte Lesezeichen der Spieler gehen ins Leere.

**Geteiltes Inventar gilt im gesamten Freimarkenbund einschließlich Bundesmarkt, aber nicht
darüber hinaus** [entschieden]. Damit funktionieren Events, die Freie Truhe und Geschäfte am
Spawn mit demselben Inventar wie das Bauen in der Marktstadt. Die Lobby ist davon ohnehin
getrennt — sie führt auch zu ganz anderen Servern.

---

## 4. Welten

Größenpaket **Ausgewogen** [entschieden]. Eine Grenze lässt sich später nach außen
schieben, nach innen nicht — deshalb jetzt etwas Luft.

| Welt | Radius | Kante | Spielmodus | Besonderheit |
|---|---|---|---|---|
| `altfurt` | 400 | 800 | Abenteuer | Gebaut: Bundesmarkt und die Gebäude aus [2.1](#21-die-gebäude-in-altfurt), Farmen, Mine |
| `marktstadt` | 1.494,5 | 2.989* | Überleben | 1.764 Parzellen zu 64×64, Weg 7, Nummern −21 bis 20 |
| `freistaedte` | 3.000 | 6.000 | Überleben | ~320 Freistädte, natürliches Gelände |
| `hafenwerder` | 2.500 | 5.000 | Überleben | Archipel, **eigenes Angelgut** |
| `wolkenwerder` | Raster | — | Überleben | Leerwelt, **eigene Skyblock-Mechaniken** |
| `hochwald` | 2.500 | 5.000 | Überleben | Freibau, Sicherung kostet |
| `kornmark` | 1.000 | 2.000 | Überleben | Reset je Minecraft-Version |
| `nether`, `end` | 1.000 | 2.000 | Überleben | Farmen |

\* Die Einstellung nennt weiter den Wunsch **3.000**; das Plugin rundet ihn auf ganze
Rasterzellen ab (71 = 64 + 7) und auf eine gerade Zellenzahl. Daraus werden 42 Parzellen
je Achse und eine Kante von 42 × 71 + 7 = **2.989** um die Mitte **−3,5/−3,5** — genau die
Außenkante des umlaufenden Mauer-Weg-Rings. Die runden 3.000 dürfen nicht als Kante
gesetzt werden: Sie schlössen Bauland der Parzellen 21 und −22 ein, die es nicht gibt.
Die Grenze steht in `welten.marktstadt.kante`, wird bei **jedem** Start neu gesetzt und
wandert nur nach außen — beim Start gilt immer der größere Wert aus Einstellung und
Welt. Ein Sprung über das Doppelte braucht eine zweite Bestätigung ([12a.6](#12a6-was-anpassbar-ist--und-was-nicht)).

Vorgenerierung grob 3–4 Stunden, Platzbedarf 15–20 GB. **Erst Grenze setzen, dann
vorgenerieren** — nie umgekehrt. Für die Marktstadt: Mitte −3,5/−3,5, Radius 1.495,
35.344 Chunks (−94…93 je Achse), 36 Regionen. Ein spawnzentriertes Werkzeug bräuchte um
32/32 den Radius 1.530, sonst fehlt der ganze Westring.

Diese Zahlen rechnet seit dem 02.11.2026 `Chunkfeld` im Kern nach, und `ChunkfeldTest`
nagelt sie fest — sie standen vorher nur in diesem Text. Zwei Berichtigungen aus dem
Nachrechnen und aus einer Messung:

- **36 Regionen sind nicht 36 Dateien.** Gemeint ist, welche Regionen das Feld selbst
  berührt. Auf der Platte liegen mehr, weil ein Chunk am Rand seine Nachbarn braucht und
  eine Regionsdatei entsteht, sobald ein einziger Chunk in ihr angefasst wird. Gemessen an
  Altfurt: Zielchunks −25…25, 4 Regionen im Feld, **16 Dateien** auf der Platte. Für die
  Marktstadt sind rund 64 zu erwarten. Wer die Dateizahl als Prüfung auf Vollständigkeit
  nimmt, bekommt einen Fehlalarm.
- **Die Summe über alle acht Welten: 424.040 Chunks, 432 Regionen im Feld.** Davon tragen
  Freistädte, Hafenwerder und Hochwald allein 338.568 — sie bestimmen das Zeitfenster, die
  übrigen fünf sind Minuten.

### 4.1 Die zwei Inselwelten [entschieden]

Beide, und jede mit eigenem Zweck — sonst ist eine davon überflüssig:

| | **Hafenwerder** | **Wolkenwerder** |
|---|---|---|
| Was | Gekaufte Insel im Meer | Skyblock-Start |
| Preis | 75.000 Mark | gratis |
| Gelände | Natürlich gewachsen | Leere, eine Startinsel |
| Eigener Reiz | **Eigene Angeltabelle** mit Fängen, die es sonst nirgends gibt; Bootsrennen; Handelsrouten | **Mechaniken, die nur dort gelten**: Kobbelstein-Generator mit steigender Erzausbeute, Insel-Stufe als Fortschritt, Herausforderungen, Biom wählbar |
| Rolle | Ruhiger Rückzug mit Status | Eigener Spielmodus, eigener Einstieg |

Der Trick ist, dass die Reize **nicht übertragbar** sind: Das besondere Angelgut gibt es nur
im Meer, der Generator nur in der Leere. Dann hat jede Welt einen Grund, besucht zu werden,
statt nur anders auszusehen. [empfohlen]

### 4.2 Erweiterbar und portierbar [entschieden]

- Welten kommen aus einer **Konfigurationsliste**, nicht aus dem Code. Eine neue Welt ist
  ein Eintrag plus Generator, kein Umbau. **Gebaut am 01.11.2026**: Je Eintrag `art`
  (`raster`, `gewoehnlich`, `leer`), `umgebung` (`normal`, `nether`, `ende`) und `kante`.
  Die Reihenfolge in der Datei ist die Reihenfolge beim Anlegen — die Marktstadt steht
  oben, damit ein Fehlschlag an ihr auffällt, bevor Minuten in die übrigen Welten
  geflossen sind.

  Die Entscheidungen dabei, alle aus demselben Grund — eine falsch erzeugte Welt ist
  nicht zurückzunehmen:

  - **`art` wird nie geraten, und sie hängt am Namen — in beide Richtungen.** Das Raster
    trägt allein `marktstadt`, und `marktstadt` trägt nichts anderes. Eine Vorgabe
    `gewoehnlich` machte aus der Marktstadt beim nächsten Start eine Welt mit Hügeln und
    Bäumen, mitten in den bezahlten Parzellen; eine *zweite* Rasterwelt bekäme Mauern und
    Wege, aber kein Besitzbuch und keine Schutzwache — eine Parzellenstadt, in der jeder
    überall baut. Steht beim Raster etwas anderes, entsteht die Marktstadt **gar nicht**
    und Freimarken bleibt aus. Das ist hier das mildere Ende: Ein Plugin, das nicht
    startet, ist zu reparieren; eine Marktstadt mit Bäumen in den Parzellen nicht.
  - **Ein Tippfehler kostet eine Welt, nicht alle.** Statt einer Ausnahme mitten im Start
    sammelt das Lesen Klagen; was klagt, wird übersprungen, der Rest entsteht. Dasselbe
    beim Anlegen: Fällt eine Welt um, wird sie benannt und übersprungen. Flog es vorher
    durch, kam der Start nie bis zur Schutzwache — und die Marktstadt stand geladen da,
    voller Parzellen, in denen jeder bauen durfte.
  - **Die Grenzregel gilt jetzt für jede Welt.** Sie stand in `Stadtgrenze` und damit am
    Raster der Marktstadt; Altfurt, Kornmark und die Werder haben dieselbe Frage und kein
    Raster. Jetzt steht sie einmal im Kern (`Grenzregel`), und die Marktstadt legt nur
    noch ihre Rundung auf ganze Rasterzellen darüber. Dort steht auch das Mindestmaß und
    die Klage — **eine Einstellung, ein Leser**: Die Kante der Marktstadt wurde vorher an
    zwei Stellen mit zwei Rückfällen gelesen, und bei einem Vertipper standen zwei
    widersprüchliche Meldungen desselben Starts untereinander.
  - **Jede Welt trägt ihren Ausweis.** Diese Welten stehen in keiner Serverliste, sie
    entstehen beim Start — „noch nicht da“ hieß also *jedes Mal*, und damit wurde ein mit
    `/setworldspawn` gesetzter Erscheinungspunkt bei jedem Neustart überschrieben und ein
    verschobener Grenzmittelpunkt auf 0/0 zurückgeholt — was die Grenze nach innen schiebt,
    obwohl die Kante nie kleiner wird. Jetzt steht die `art` im `PersistentDataContainer`
    der Welt und damit in ihrer Weltdatei: *neu* heißt, dass Freimarken sie zum ersten Mal
    sieht. Weicht der Ausweis ab, wird geklagt; mehr ist von dort nicht möglich, denn
    lesbar ist er erst, wenn die Welt geladen **ist**. Den teuren Fall fängt deshalb das
    Lesen der Liste ab, bevor eine Welt angefasst wird.
  - **Die Kante wird gelesen, nicht geraten.** `kante: "6000"` war stillschweigend 0 — eine
    Welt ohne Grenze, in der Spieler beliebig weit hinauslaufen und endlos Gelände
    erzeugen, und die Vorgenerierung lief gegen eine Grenze, die nie gesetzt wurde.
    `kante: 6.000` liest YAML als 6, wie die Kommentare dieser Datei Tausender selbst
    schreiben. Geprüft wird jetzt der Typ und die Spanne, und zwar an derselben Regel, nach
    der gesetzt wird; ein Wert jenseits des Höchstmaßes ließ Bukkit vorher mitten im Start
    werfen.
  - **Was das Plugin mitbringt und in der Datei fehlt, wird benannt.** Eine vorhandene
    `config.yml` wird beim Update nie überschrieben, und Bukkit mischt die Vorgaben aus der
    Jar beim Zählen der Einträge nicht ein — beim Lesen eines einzelnen Schlüssels aber
    doch. Auf einem Server, der schon lief, entstand damit genau die Marktstadt und sonst
    nichts, ohne eine Zeile im Protokoll.

  Nicht in der ausgelieferten Liste: **Wolkenwerder**. Eine Welt aus Leere, in der jeder
  Ankommende fällt — die Startinsel kommt mit Schritt 11.
- **Kornmark wird bei jeder neuen Minecraft-Version zurückgesetzt** und mit neuem Startwert
  vorgeneriert — so kommen neue Blöcke und Biome ins Spiel, ohne gebaute Welten anzufassen.
- Gebaute Welten (Altfurt, Marktstadt, Freistädte, beide Werder) werden **nie**
  zurückgesetzt.
- **Weltordner seit Paper 26.1**: Eine zur Laufzeit erzeugte Welt liegt **nicht** im
  Serverwurzelverzeichnis. Gemessen am Testserver:

  ```
  world/dimensions/freimarken/marktstadt/
  ```

  Der Namensraum ist der Plugin-Name, der Schlüssel der Weltname. Das trifft deinen
  Manager unmittelbar: Sicherung, Dateieditor, Größenanzeige und ein Zurücksetzen einzelner
  Welten müssen dort hineinsehen. Ein Sicherungsmuster, das `marktstadt` im
  Wurzelverzeichnis erwartet, sichert **nichts**.
- Deshalb gehört in die Datenbank der **Weltschlüssel** (`freimarken:marktstadt`), nicht
  der Weltname — sonst wandert später jeder Fremdschlüssel mit.
- **Die Weltgrenze liegt nicht in der `level.dat`**, sondern in
  `<level-name>/dimensions/<namensraum>/<welt>/data/minecraft/world_border.dat`. Ein
  Sicherungs- oder Umzugsmuster, das sie in der `level.dat` sucht, findet sie nicht. Das
  Plugin setzt sie bei jedem Start neu, ein Verlust heilt sich also — aber nur, solange
  die Einstellung stimmt.

### 4.3 Wie man zwischen den Welten reist [entschieden 02.11.2026]

Acht Welten stehen, und ohne diesen Abschnitt kommt niemand hin. In eine **Welt** führen
**zwei Wege, kein dritter**:

- **Portale am Bundesmarkt.** Der Hauptspawn in Altfurt bekommt ein Portal je Welt. Das ist
  der Weg, den ein Neuer findet, ohne einen Befehl zu kennen — er sieht, wohin es geht, und
  läuft hin. Ein Portal ist außerdem ein Ort: Man kann sich dort verabreden, es beschriften
  und darüber ein Dach bauen. Ein Befehl ist nichts davon.
- **`/warp <name>`** für den, der den Weg schon kennt. Die Rückfahrt ist derselbe Befehl,
  von überall aus.

**Ein `/warp`, und die Weltnamen sind gesperrt.** Abschnitt 11.1 führt `/warp` als
*spielereigenes* Werkzeug, dessen Zahl mit dem Rang steigt (Siedler 1, Bürger 3, bezahlte
Ränge mehr). Öffentliche Weltenwarps und eigene Warps liegen damit im **selben
Namensraum** — und das ist Absicht: Ein Spieler tippt, wo er hin will, und muss keine
Kategorie kennen. Damit es trägt, gilt:

- Die acht Weltnamen sind **reserviert**. Niemand kann einen eigenen Warp `kornmark` nennen;
  der Versuch wird abgelehnt und benennt den Grund. Andernfalls verlöre ein Spieler mit
  einem unglücklich benannten Warp den Zugang zu einer Welt, oder — schlimmer — ein anderer
  bekäme ihn.
- Öffentliche Warps werden **nicht gesetzt, sondern abgeleitet**: Sie kommen aus der
  Weltenliste in der `config.yml`, nicht aus einer zweiten Liste. Eine neue Welt ist damit
  weiter *ein Eintrag plus Generator* ([4.2](#42-erweiterbar-und-portierbar-entschieden))
  und nicht ein Eintrag plus ein vergessener Warp.
- Die Vervollständigung zeigt zuerst die Welten, dann die eigenen Warps. Wer `/warp k`
  tippt, meint fast immer Kornmark.

**Wo ein Warp landet, entscheidet die Welt, nicht der Zufall.** Jede Welt braucht einen
Ankunftspunkt, und der ist ihr Erscheinungspunkt — derselbe, den ein Betreiber mit
`/setworldspawn` setzt und den das Plugin seit dem 02.11.2026 nicht mehr bei jedem Start
überschreibt ([4.2](#42-erweiterbar-und-portierbar-entschieden)). Eine zweite Liste von
Ankunftspunkten wäre eine zweite Wahrheit.

**Gebaut am 02.11.2026** (Schritt 3, Teil C). Was dabei entschieden oder gemessen wurde:

- **Angekommen wird über `setRespawnWorld(altfurt)`** nach dem Weltenbau. Das macht
  Altfurt zum Erscheinungsort für jeden, der neu ist. Der Aufruf muss *hinter* den
  Weltenbau, sonst fällt die Dimensionssuche auf die Überwelt zurück. Er ist dauerhaft (er
  schreibt in die `level.dat` der Hauptwelt), also nennt das Protokoll die vorige Welt —
  gemessen stand sie auf `freimarken:marktstadt`.
- **Einen Zuhörer braucht nur der Fall daneben**: Wer in einer Welt ausgeloggt war, die es
  nicht mehr gibt, bekommt von Paper hart `minecraft:overworld` eingesetzt — mit den *alten*
  Koordinaten. `PlayerJoinEvent` käme dafür zu spät, `PlayerLoginEvent` hat keinen
  Ortssetzer. Bleibt `AsyncPlayerSpawnLocationEvent`. Dort läuft **keine** Standprobe: Sie
  liest Blöcke, und das heißt Chunks laden — aus einem fremden Faden nicht erlaubt.
- **Der Erscheinungspunkt verspricht keinen Boden.** `World#getSpawnLocation` gibt einen
  Punkt; nur Minecraft selbst sucht beim Beitritt ein 21×21-Feld ab, ein Teleport aus einem
  Plugin sucht nichts, und `paper-api` 26.3 hat dafür keine Methode. Gemessen: **0/0 in
  Altfurt liegt unter Wasser** (Boden y 58, Wasser bis y 62) — genau dorthin setzt
  `Weltenbau` bei einer frischen Welt den Spawn, wenn der vorige außerhalb der Grenze lag.
  Jede Reise läuft deshalb durch eine Standprobe: tragender Boden, zwei durchquerbare
  Blöcke darüber, nichts Schädliches. Ein Grasbüschel ist ausdrücklich erlaubt — die erste
  Fassung verlangte Luft und hätte jede Wiese abgelehnt.

  **Geprüft wird immer, gesucht nur am Erscheinungspunkt einer Welt.** Dort ist kein
  bestimmter Ort gemeint („nach Kornmark", nicht „auf diesen Block"), also darf ringweise
  bis acht Blöcke weit gesucht werden. Wo der Ort feststeht — `/zurueck`, `/tpa`,
  `/freimarken parzelle` — wird **abgesagt statt verschoben**: Eine Suche sprang auf den
  höchsten Block der Säule und schickte damit einen Rückweg aus einer Höhle an die
  Oberfläche und eine Todesstelle neben einem Lavasee aufs Netherdach. Genau das ist der
  bei EssentialsX belegte Fehler (Ausgabe 3318, lange als „wontfix" geführt).
- **Das Plugin verschiebt keinen Erscheinungspunkt.** Es misst ihn beim Start und gibt bei
  Bedarf ein kopierfertiges `setworldspawn` aus. Ein Programm, das ihn verschiebt,
  verschiebt bei jedem Start, wo alle ankommen — unumkehrbar, weil der alte Punkt danach
  nirgends mehr steht.
- **Ein Tor ist ein Quader, kein Portalblock.** Die Quader stehen am Eintrag der *Ziel*welt
  in der `config.yml`; beim Lesen wird geprüft, dass keines in der Ankunftswelt selbst
  steht, keines außerhalb ihrer Grenze liegt und keine zwei sich Blöcke teilen — sonst
  entschiede die Reihenfolge der Einträge, wohin ein Spieler reist. Gesetzte
  `NETHER_PORTAL`- oder `END_GATEWAY`-Blöcke wären das Gegenteil: Sie erzeugen von sich aus
  Portale und Chunks in anderen Welten und löschen sich selbst wieder.
- **Der Torzuhörer prüft als erstes `hasExplicitlyChangedBlock`.** `PlayerMoveEvent` feuert
  belegbar schon bei zehn Grad Kopfdrehung, bei völlig unveränderten Koordinaten. Advanced
  Portals filtert an dieser Stelle nachweislich nicht; Multiverse-Portals hat dasselbe
  Problem seit 2011.
- **Die Tore werden beim Start gegen das Gelände gemessen.** Die ausgelieferten Werte sind
  an *einem* Altfurt eingemessen (flachstes trockenes 32×32 um den Ursprung: **56/−4 bis
  87/27**, Boden y 70–72, durchgehend `red_sand`; Torreihe auf z = 0, sieben Durchgänge je
  drei Blöcke breit, y 71–73). Ein anderer Startwert, ein anderes Gelände — und die Tore
  lägen im Berg oder in der Luft. Geprüft wird deshalb je Säule: trägt der Block unter dem
  Tor, und ist der Durchgang in Fuß- und Kopfhöhe frei? **Nicht** mit der Standprobe: Die
  sucht den höchsten festen Block und findet damit den Sturz über der Öffnung — so hat sie
  am 02.11.2026 alle sieben Tore einer gerade gebauten Wand für unbegehbar erklärt.
- **Beide Wege laufen durch eine Klasse** (`Reise`). Zwei Fassungen derselben Prüfungen
  hieße, dass eine davon ohne Abklingzeit oder ohne Standprobe reist, und gemerkt würde es
  an dem Tag, an dem jemand unter Wasser erscheint. Die Abklingzeit kommt zuletzt: Sie
  antwortet und vermerkt in einem Zug, und wer sie vorher fragt, verbrennt die Sperre für
  eine Reise, die danach abgelehnt wird.
- **Zu Pferd geht es nicht.** `teleportAsync` gibt über Weltgrenzen hinweg `false` zurück,
  und `RETAIN_VEHICLE` ist seit 1.21.10 abgekündigt und wirkt nicht. Gesagt wird es vorher;
  das Ergebnis des Teleports wird außerdem ausgewertet — sonst bliebe der Spieler stehen und
  hätte „Unterwegs" gelesen. Angeleinte Tiere bleiben zurück, mit einer Zeile Hinweis.
- **Selbstgebaute Nether- und Endportale sind gesperrt** [entschieden]. Ein Portal in
  Altfurt führt *nicht* nach `freimarken:nether`, sondern nach `minecraft:the_nether` —
  einer Welt ohne Grenze, ohne Schutzwache und ohne Vorgenerierung. Gesperrt wird an drei
  Stellen (Anzünden, Betreten, Reise), jede mit einer Meldung, die den gültigen Weg nennt:
  Ohne Meldung ist das Fehlerbild ein Rahmen, der sich nicht anzünden lässt, und das sieht
  wie ein Serverfehler aus. Das Ende ließe sich auch nicht sauber umlenken — der Rückweg
  feuert kein `PlayerPortalEvent`, und die Obsidianplattform entstünde als Narbe im
  Haupt-Ende.
- **Abenteuer in ganz Altfurt** [entschieden], mit dem Recht `freimarken.spielmodus.frei`
  für Baumeister und Betreiber. Altfurt wird nie zurückgesetzt; solange dort Überleben
  gilt, kann jeder Besucher im Rohbau graben, und jedes Loch bleibt. Minecraft kennt keinen
  Spielmodus je Welt, also setzt ihn ein Zuhörer beim Betreten — und nur, wenn er abweicht,
  denn jedes Setzen räumt das Flugrecht ab.

**Noch nicht gebaut**: Die zehn Gebäude aus [2.1](#21-die-gebäude-in-altfurt) sind von Hand
zu bauen. Von Hand ist in einer Welt, die nie zurückgesetzt wird, die billigere Richtung —
jedes Haus ist sofort änderbar. Bis dahin sind die Tore unsichtbare Türen in einer Wand.
Ebenfalls offen: spielereigene Warps (`/warp setzen`) samt der Absage auf einen reservierten
Namen — sie gehören zur Rangleiter ([11.1](#111-sprechende-befehle-mit-vervollständigung));
ein Preis für die Reise, denn der Plan nennt an keiner Stelle einen Betrag; und Reittiere
mitzunehmen, was eine Zustandsmaschine wäre und kein Reiseweg.

### 4.3a Sterben, zurückkehren, besuchen [entschieden 02.11.2026]

**Kein eigener Spawnpunkt im Bett.** Wer stirbt, erscheint **immer in Altfurt** — egal, wo
er gestorben ist. Bett, Respawn-Anker und `/spawnpoint` setzen keinen Punkt mehr; das Recht
`freimarken.spawnpunkt.setzen` öffnet allein `/spawnpoint` für den Betreiber. Geschlafen
werden darf weiter, die Nacht wird übersprungen — gesperrt ist nur das Setzen des Punktes.

Das braucht **zwei** Haken, nicht einen, und das ist gemessen: `setRespawnWorld` wirkt nur
im Standardzweig, ein vorhandener Punkt aus Bett, Anker oder Befehl gewinnt davor. Der
erste Haken (`PlayerSetSpawnEvent`) verhindert, dass einer gesetzt wird; der zweite
(`PlayerRespawnEvent`, nur bei `DEATH`) biegt um, was trotzdem gesetzt wurde. Der zweite
allein wäre technisch genug — aber dann sagte das Spiel beim Schlafen weiterhin „Respawn
point set", und das wäre eine Lüge. Die Vanilla-Meldung wird deshalb ausdrücklich
abgeschaltet und durch eine eigene ersetzt.

`END_PORTAL` bleibt unangetastet: Wer durch das Rückportal aus dem Ende geht, will aus dem
Ende heraus und nicht nach Altfurt. Ein Filter, der das verwechselt, ist der typische
Fehler solcher Zuhörer.

Der Preis dieser Entscheidung ist benannt: Wer im Nether oder in Kornmark stirbt, steht
danach in einer anderen Welt und muss über ein Tor, `/warp` oder `/zurueck` zurück. Das ist
der Grund, warum es `/zurueck` gibt.

**`/zurueck` (`/back`)** führt an den Ort, an dem der Spieler zuletzt war: vor einer Reise,
vor einem Teleport, oder an die Stelle, an der er gestorben ist. Ohne Ausnahme für einen
Tod durch einen anderen Spieler — die Gegenfassung ist verbreitet, braucht aber eine
Abfrage des Töters, ein Umgehungsrecht und eine Erklärung, und solange es keinen Kampfmodus
und keine Arena gibt, wäre das Vorbauen.

Gemerkt wird an **einer** Stelle: in `Reise`, vor jedem Teleport — und zusätzlich beim Tod,
denn der läuft nicht durch `Reise`. Das erspart einen Zuhörer auf `PlayerTeleportEvent` und
damit die ganze Frage, welche Teleportursachen zählen; ein Anti-Cheat, das einen Spieler
zurücksetzt, meldet `PLUGIN` und hätte den Rückweg sonst überschrieben. Dass zweimal
`/zurueck` wieder am Ausgangspunkt endet, steht nirgends als Regel — es folgt daraus.

Der Rückweg lebt **nur im Speicher** und wird beim Abmelden vergessen. Ein Rückweg, der
einen Neustart überlebt, führt Tage später an eine Stelle, von der niemand mehr weiß, warum
sie gemerkt wurde.

**`/tpa <spieler>`** fragt „ich komme zu dir", **`/tpahere <spieler>`** lädt ein „komm zu
mir"; beantwortet wird beides mit `/tpaccept` oder `/tpdeny`.

Die Trennlinie zum Missbrauch liegt **nicht in der Richtung**, sondern darin, dass **wer
bewegt wird, zustimmt**: bei `/tpa` der Fragende selbst (er hat ja gefragt), bei `/tpahere`
der Eingeladene (er nimmt an). Niemand wird gegen seinen Willen gezogen. Was es deshalb
trotzdem nicht gibt, ist `/tpaall`: Ein Befehl, der alle zieht, wird von einem Einzigen
benutzt und von allen anderen erduldet.

Wer wandert und wer bleibt, steht in der **Anfrage** und nicht im Befehl — so muss
`/tpaccept` die Richtung nicht erraten, und sie wird an einer Stelle entschieden. Der
Zielort wird erst beim Annehmen geholt: Dann ist er frisch, und sein Chunk ist geladen, weil
der Bleibende darin steht. Eine Anfrage steht eine Minute. Mehrere Fragende an denselben
Spieler bleiben **alle** stehen, und `/tpaccept` nennt, wen es angenommen hat — die neueste
zu nehmen und die übrigen wegzuwerfen hieße, dass eine Anfrage lautlos verschwindet. „Komm
zu mir" und „ich komme zu dir" sind zwei verschiedene Fragen an dieselbe Person, und beide
darf man stellen.

Beides darf **jeder** (`freimarken.zurueck`, `freimarken.tpa`, Vorgabe an). Bei `/tpa` gibt
es nichts zu rationieren, weil das Ziel zustimmen muss; eine Rangbindung ist jederzeit ohne
Codeänderung nachzuholen, indem das Recht auf eine Gruppe gelegt wird.

**Jeder Teleport hat drei Sekunden Wartezeit** [entschieden 02.11.2026]. Bewegung **und**
Schaden brechen sie ab. Der Grund ist nicht die Platte, sondern das Spiel: Ohne sie ist
jeder Teleport ein Fluchtweg — wer verfolgt wird, tippt `/warp altfurt` und ist weg, und
ein Kampf hat dann keinen Ausgang mehr, sondern nur noch einen Abbruch. Die Abklingzeit
zwischen zwei Reisen hilft dagegen nicht: Sie begrenzt, wie *oft* man reist, nicht *ob* man
mitten im Kampf verschwindet.

Drei Dinge daran sind nicht beliebig:

- **Bleiben, nicht stillstehen.** Abgebrochen wird, wer den Bereich verlässt, in dem er
  angefangen hat — nicht, wer sich darin bewegt. Bei einem Befehl ist das der eine Block,
  auf dem er steht; **bei einem Tor der ganze Torquader**. Man läuft in ein Tor hinein, und
  der nächste Schritt liegt noch darin: Eine Regel „ein Block" bräche genau bei dem Schritt
  ab, mit dem man angekommen ist, und niemand käme je durch. So verhält sich ein Tor wie
  ein Portal — ein Netherportal verlangt vier Sekunden Aufenthalt, und das hat noch
  niemandem erklärt werden müssen.
- **Schaden bricht auch ab.** Bewegung allein ließe eine Lücke: Wer eingekeilt in einer Ecke
  steht und geschlagen wird, bewegt sich durch Rückstoß nicht immer einen ganzen Block
  weit — und genau dann wäre der Teleport doch ein Fluchtweg.
- **Geprüft wird zweimal**, vor der Wartezeit und wenn es losgeht. In drei Sekunden kann der
  Boden am Ziel abgebaut, Lava geflossen oder die Welt entladen worden sein, und die erste
  Prüfung sagt darüber nichts mehr aus. Der Rückweg wird erst beim Sprung gemerkt — ein
  abgebrochenes Aufwärmen soll keinen hinterlassen.

`freimarken.sofort` (Vorgabe: Betreiber) überspringt die Wartezeit. Das ist kein Vorrecht,
sondern Werkzeugpflege: `/freimarken parzelle` soll nicht abbrechen, weil man sich dabei
umsieht.

**Kostenlos, bis es Zahlen gibt.** [1032](#8-geldsenken) führt „Reparatur, Teleport,
weitere Heimatpunkte" als laufende Geldsenke, nennt aber keinen Betrag, und
[4.4](#44-wie-vorgeneriert-wird-entschieden-02112026) erklärt einen erfundenen Preis
ausdrücklich für falsch. Ein Preis für `/zurueck` und `/tpa` kommt, wenn die Kasse läuft
und es Zahlen aus dem Betrieb gibt.

**Heimatpunkte sind davon unberührt.** Der Plan führt sie durchgängig als *selbstgesetzte
Reiseziele* ([11.1](#111-sprechende-befehle-mit-vervollständigung), [9](#9-ränge)), an
keiner Stelle als Erscheinungsort nach dem Tod. „Kein Spawnpunkt im Bett" nimmt niemandem
einen Heimatpunkt.

---

### 4.4 Wie vorgeneriert wird [entschieden 02.11.2026]

**Mit Chunky, und zwar ausschließlich über Konsolenbefehle.** Kein Eintrag in
`build.gradle.kts`, kein `depend` oder `softdepend` in der `plugin.yml`, keine Zeile Code
gegen `chunky-common`. Das Plugin bleibt damit so frei von Fremdcode wie heute, und
Freimarken startet auf einem Server ohne Chunky vollständig — dann nennt
`/freimarken vorgenerieren` eben nur Zahlen und Stichprobe.

Chunky hat zwar eine Java-API (`ChunkyAPI` über Bukkits ServicesManager), aber sie steht
auf Version 0 und kündigt Brüche an; außerdem ist Chunky GPL-3.0, und was das für ein
dagegen übersetztes Plugin bedeutet, ist nicht geklärt. Ein Konsolenbefehl hat keine
dieser Fragen.

**Warum nicht selbst gebaut**, obwohl Plan [12a.6](#12a6-was-anpassbar-ist--und-was-nicht)
„Vorgenerierung anstoßen" als Fähigkeit des eigenen Managers führt: Das Schwierige ist
nicht das Erzeugen, sondern die Drosselung. Die Paper-API sagt zu `getChunkAtAsync`
nirgends, wie schnell ein Chunk kommt, nennt keine Obergrenze für offene Anfragen und
keine Reihenfolge — wer das selbst baut, baut Rückstau, Entladedisziplin,
Ticklängen-Regelung und einen absturzfesten Zeiger. Das ist in Chunky gelöst.

**Angestoßen wird nur auf Befehl** — nichts läuft von selbst. Die Vorgenerierung dauert
Stunden und belegt Gigabyte; ein Start, der das von sich aus beginnt, überrascht den
Betreiber beim ersten Start nach einer neuen Welt. Stattdessen nennt das Plugin in **einer**
Zeile beim Start die Welten, die nicht vorgeneriert sind, und zeigt auf den Befehl.

**Das Plugin schreibt die Befehle, statt sie dem Betreiber zu überlassen.**
`/freimarken vorgenerieren` gibt je Welt eine Zeile zum Kopieren. Drei Gründe, jeder ein
Fehler, der sonst unbemerkt bliebe:

- **Die Mitte ist nicht der Spawn.** Mit Radius 1.495 um 32/32 fehlen der Marktstadt die
  Chunkspalten −94 und −93 — der Westring plus 28 Blöcke der Parzellenspalte −21. Das
  sieht man erst, wenn ein Spieler hinläuft und Gelände während des Spiels entsteht.
- **Die Zahl in der Datei ist nicht die Grenze.** `config.yml` sagt 3.000, wirksam sind
  2.989; die Grenze wandert nur nach außen, ein kleinerer Wert landet nur im Protokoll.
  Mitte und Radius kommen deshalb aus der **gemessenen** `WorldBorder`.
- **Chunky kennt keine Namensräume.** Es gibt den Weltnamen roh an
  `Server#getWorld(String)`. Der Bukkit-Name einer namensräumigen Welt ist
  `freimarken_marktstadt` mit Unterstrich, nicht `freimarken:marktstadt` mit Doppelpunkt —
  am 02.11.2026 am Probeserver gemessen. Das Plugin prüft zur Laufzeit, dass der Name
  wirklich dieselbe Welt zurückliefert, und zeigt sonst **keine** Zeile. Ein Befehl, der
  ins Leere läuft oder die falsche Welt trifft, ist schlimmer als keiner.

**Alles in einem `chunky start`** und nicht in fünf Befehlen (`world`, `center`, `shape`,
`radius`, `start`). Die fünf hinterlassen einen Zustand, der zwischen zwei Welten
stehenbleibt: Wer bei der zweiten Welt den Radius vergisst, generiert mit dem der ersten,
und das meldet niemand.

**Einrichtung**, einmalig: Chunky 1.5.3 (Paper 26.1–26.3, Java 25) nach `plugins/`, und in
`plugins/Chunky/config.yml` `continue-on-restart: true`. Ab Werk steht der auf `false`, und
Chunky schreibt den Fortschritt nur beim Verlassen seiner Schleife — ein Absturz nach zwei
Stunden kostet zwei Stunden. Ein Update-Prüfer ist nicht eingebaut, die Fassung steht
deshalb in der `TODO.md`.

**Nacheinander, klein nach groß, ohne Spieler.** Chunky hält je Welt nur *eine* gespeicherte
Aufgabe, und parallel teilen sich acht Welten dieselben Fäden und dieselbe Platte.

**Geprüft wird an den Chunks, nicht an Chunkys Meldung.** `/freimarken vorgenerieren` nimmt
eine Stichprobe von neun Punkten — Mitte, vier Kantenmitten und **vier Ecken**. Die Ecken
sind der Zweck: Ein Werkzeug, das einen Kreis erzeugt statt eines Quadrats, sieht in der
Mitte und an den Kantenmitten fertig aus. Das ist bei Chunky eine Einstellung und damit
einen Vertipper weit. Gelesen wird mit `getChunkGenerationStateAsync`, das den Kopf der
Regionsdatei liest, ohne einen Chunk zu laden; `OUTDATED` ist dabei das maschinell lesbare
Zeichen für einen Minecraft-Versionswechsel und damit für den Kornmark-Reset aus
[13](#13-betrieb).

**Gemessen am 02.11.2026** (Probeserver, Altfurt, 2.553 Chunks): 20 Sekunden, **120
Chunks/s**, **13,7 KB je Chunk**. Hochgerechnet auf alle 424.040 Chunks wären das etwa eine
Stunde und 5,6 GB. Die 3–4 Stunden und 15–20 GB oben bleiben als obere Schranke stehen:
Altfurt ist flach und klein, und Hochwald mit Bäumen, Hafenwerder mit Meer und der Nether
sind nicht gemessen.

**Der Ausstieg, falls Chunky einmal nicht nachzieht** — und das ist genau dann am
wahrscheinlichsten, wenn man es braucht, denn Kornmark wird bei jeder neuen
Minecraft-Fassung neu vorgeneriert: Ein Minimalpumpwerk im Plugin ersetzt den Lauf. **Eine**
offene Anfrage, `getChunkAtAsync(x, z, true, false, rückruf)`, im Rückruf
`unloadChunk(x, z, true)`, kein `addPluginChunkTicket`, kein `setChunkForceLoaded`, Zeiger
in der MariaDB, Abbruch in `onDisable`. Mit nur einer offenen Anfrage entfällt die ganze
schwierige Hälfte, und Kornmarks 15.876 Chunks sind selbst bei 20 Chunks/s in 13 Minuten
durch.

---

## 5. Entscheidungen, die danach feststehen

| # | Thema | Stand |
|---|---|---|
| E1 | **Spieler-Identität** | **Nur über Velocity**, modernes Forwarding, Backend-Port in der Firewall zu. Echte Mojang-UUIDs. [entschieden] — siehe [5.1](#51-e1-im-detail) |
| E2 | Parzellengröße / Weg | 64×64, Weg 7 [entschieden] |
| E3 | Weltnamen | siehe [Abschnitt 4](#4-welten) [entschieden] |
| E4 | Weltgrenzen | Paket „Ausgewogen" [entschieden] |
| E5 | Geteiltes Inventar | ganzer Freimarkenbund, Lobby getrennt [entschieden] |
| E6 | Währungen | Mark, Goldmark [entschieden] |
| E7 | Datenhaltung | **MariaDB von Anfang an**, Zugriff hinter einer Schnittstelle [entschieden] |
| E8 | Lizenz | kein GPL-Code im eigenen Plugin → freie Lizenzwahl [entschieden] |

### 5.1 E1 im Detail

Das ist die folgenreichste Festlegung des ganzen Projekts, deshalb ausgeschrieben:

| Stelle | Einstellung |
|---|---|
| Velocity | `online-mode = true`, `player-info-forwarding-mode = modern`, Forwarding-Geheimnis gesetzt |
| Freimarken (Backend) | `online-mode = false`, `velocity.enabled = true` in `paper-global.yml`, dasselbe Geheimnis |
| Firewall | **Backend-Port von außen gesperrt** — nur der Proxy darf ihn erreichen |

Der Backend-Port ist der eigentliche Punkt. `online-mode = false` heißt: Der Server glaubt,
was ihm gesagt wird. Kommt die Verbindung vom Proxy, ist das die echte Mojang-UUID; käme sie
direkt, wäre es eine aus dem Namen berechnete — **derselbe Spielername, eine andere UUID,
fremde Grundstücke**. Die Firewall ist hier kein Härtungsschritt, sondern Teil der
Funktionsweise.

Folgerichtig: Ein zweites Backend (etwa Wolkenwerder) bekommt dieselbe Behandlung, und
kein Server des Freimarkenbunds ist je direkt erreichbar.

**Zu E7:** Du hattest recht — wenn die Architektur ohnehin auf Wachstum ausgelegt sein soll,
ist der spätere Umstieg reine Zusatzarbeit. MariaDB direkt. Die Abstraktion bleibt trotzdem,
sie kostet nichts und hält dir die Tür offen.

**Zu E8:** Weil dein Code keine GPL-Klassen importiert, gehört dein Plugin dir allein —
beliebige Lizenz, keine Veröffentlichungspflicht, keine Altlast. Der Preis ist, dass große
Blockoperationen selbst geschrieben werden. Endliche Arbeit statt dauerhafter Bindung.

---

## 6. Die Besitzleiter

Der Kern. **Ein** System, **ein** Datenmodell — nicht fünf Plugins mit fünf Datenbanken.

### 6.1 Modell

```
Besitz
 ├── id, art (parzelle | freistadt | werder | ladeneinheit)
 ├── welt, bereich (Quader oder Chunk-Menge)
 ├── eigentuemer (UUID), mitbauer[], gaeste[]
 ├── erworben_am, preis, zustand (aktiv | gekündigt | verfallen)
 ├── eltern_id        ← Grundstück in einer Freistadt zeigt auf die Freistadt
 └── flags            ← bauen, kisten, pvp, mobs, feuer, betreten
```

`eltern_id` ist der Trick: Ein Grundstück in einer Freistadt ist derselbe Datensatz wie eine
Parzelle in der Marktstadt, nur mit Elternteil. Der Spielermarkt — Stadtbesitzer vergeben
eigene Grundstücke zu eigenen Preisen — fällt damit aus dem Modell heraus und ist keine
Sonderlocke. [empfohlen]

**Gebaut am 31.10.2026, mit einer Abweichung** [entschieden]: `art`, `eltern_id` und
`zustand` sind da; **`bereich` ist es nicht**. Die Rasterparzelle bleibt die Atomeinheit
mit `x`/`z`, und der Bereich einer Gruppe ergibt sich aus ihren Kindern. Grund:
`darfBauen` läuft bei **jedem** gesetzten und abgebauten Block und ist heute eine
Punktabfrage auf den Index `(welt, x, z)`; ein Quadervergleich wäre dort spürbar teurer.
Für Besitz, der nicht am Raster hängt — Freistädte im natürlichen Gelände, Werder-Inseln —
kommt der Quader später als **eigene** Spalte, die nur dort gefüllt wird. `x`/`z` sind
dafür schon NULL-fähig.

Wem ein Gruppenkind gehört: **sich selbst.** Jede Zeile trägt Eigentümer und Mitbauer
vollständig, `eltern_id` ist eine Schicht darüber und kein Verweis zum Nachschlagen —
sonst würde aus jeder Blockprüfung ein zweiter Gang zur Datenbank. Der Preis dafür ist
die Doppelung, und die wird beim **Schreiben** gehalten: Gruppe und Kinder in einer
Transaktion. Bis Schritt 6 das baut, verweigert das Besitzbuch Einzeländerungen an einem
Kind (`TEIL_EINER_GRUPPE`) — Verweigern ist die zurückholbare Antwort.

Eine Gruppe aus drei Parzellen zählt als **drei**, nicht als eine: Gezählt werden
Rasterzellen, weil sie Fläche belegen. Sonst wäre Zusammenlegen eine Geldmaschine —
die Preisstaffel verdoppelt (25.000 ≪ *besessen*−1), und die Rangobergrenze ließe sich
durch Zusammenlegen umgehen.

### 6.2 Stufen und Preise

Preise in **Mark**. Verhältnisse an Lumia angelehnt [belegt], Beträge modernisiert.

| Stufe | Welt | Größe | Preis |
|---|---|---|---|
| **1. Parzelle** | Marktstadt | 64×64 | **gratis** |
| 2. Parzelle | Marktstadt | 64×64 | 25.000 |
| 3. Parzelle | Marktstadt | 64×64 | 50.000 |
| jede weitere | Marktstadt | 64×64 | Verdopplung, Obergrenze je Rang |
| **Zusammenlegen** | Marktstadt | — | 10.000 × Felderzahl der neuen Gruppe |
| **Skyblock-Insel** | Wolkenwerder | Raster | **gratis** |
| **Meerinsel** | Hafenwerder | ~128×128 | 75.000 |
| **Freistadt** | Freistädte | ~256×256 | 400.000 |
| Grundstück in einer Freistadt | Freistädte | **vom Eigentümer** | **vom Eigentümer** [entschieden] |

Zwei Einstiege sind gratis: die erste Parzelle und die Skyblock-Insel. Wer nur bauen will,
zahlt nie etwas. [empfohlen]

**Auflagen für eine Freistadt** [empfohlen, nach Lumias Regel]: Erst wenn sie Grundstücke
*und* eine Regelwand hat, bekommt sie einen Teleporter im Bundesratshaus und darf Läden
lizenzieren. Das verhindert Geisterstädte, die Fläche blockieren.

### 6.3 Zusammenlegen [entschieden]

- Nur benachbarte Parzellen, nur bei gleichem Eigentümer — sonst Anfrage an den Nachbarn,
  die er annehmen muss.
- Der Weg dazwischen verschwindet und wird mit Parzellenboden gefüllt.
- Gebühr steigt mit der Gruppengröße: eine gute Senke, weil nur zahlt, wer viel besitzt.
- Obergrenze der Gruppengröße **pro Rang**.
- **Auftrennen muss möglich sein**, sonst ist jeder Fehlgriff endgültig.

**Stand 31.10.2026 — die Hälfte, die man nicht sieht, ist gebaut** [entschieden]:
Kern, Ablage und Schutz tragen Gruppen vollständig. Kein Befehl kann heute eine Gruppe
erzeugen, für Spieler ändert sich also nichts — aber jede Regel darunter ist geprüft.

Drei Festlegungen, die dabei fielen:

- **Benachbart heißt orthogonal**, nie diagonal — diagonal liegt keine gemeinsame
  Kante, sondern eine Kreuzung. Die Aufzählung `Richtung` ist zugleich die Definition:
  für jeden Wert gilt |dx| + |dz| = 1, also gibt es „nicht benachbart" als Eingabe gar
  nicht.
- **Zur Gruppe gehört, was im Inneren liegt** (`Rasterplan.imInneren`): Ein Block
  gehört dazu, wenn **alle** anliegenden Rasterzellen zur Gruppe gehören — eine auf
  Bauland, zwei auf einem Band, vier auf einer Kreuzung. Der Außenring bleibt damit von
  selbst Weg, und bei einer L-Form bleibt die Kreuzung draußen, weil dort fremdes
  Grundstück anliegt.
- **Zwei Fragen, zwei Zugänge**: `parzelleAn` beantwortet „auf welcher Rasterzelle stehe
  ich" und gilt weiter nur auf Bauland; `zustaendigerBesitz` beantwortet „wem gehört
  dieser Block" und schlägt das Gruppeninnere zu. Ein Schreibbefehl darf nie den
  zweiten Weg für Koordinaten benutzen: Auf einem Band liefert `floorDiv` immer die
  **kleinere** Nachbarzelle, also womöglich das Nachbargrundstück.

**Stand 01.11.2026 — Befehle und Weltarbeit, der Schritt ist fertig** [entschieden]:
`/parzelle zusammenlegen <nord|ost|sued|west>` und `/parzelle auftrennen`, beide mit
Bestätigung samt Parzellennummer, und die Wegfläche wird gefüllt bzw. zurückgebaut.

- **Beim Auftrennen kommt der Weg zurück und räumt dabei, was darauf steht**
  [entschieden]. Die Warnung nennt die Zahl der Blöcke vorher. Die beiden Alternativen
  waren schlechter: „erst räumen, sonst Abbruch" fährt den Spieler bei einem großen
  Gebäude fest und macht die Gruppe faktisch endgültig; „Weg bleibt bebaut, nur das
  Eigentum fällt" stellt eine Mauer quer über eine öffentliche Straße, die danach
  niemand mehr entfernen darf.
- **Zwei Obergrenzen, nicht eine**: `freimarken.parzellen.<n>` begrenzt, wie viel
  jemand besitzt — das bremst das Flächenhorten; `freimarken.gruppe.<n>`, wie viel er
  davon zu *einem* Grundstück verbindet — das entscheidet, wie groß ein einzelnes
  Bauwerk werden kann. Beide lesen dieselbe Rechteauswertung.
- **`Stadtboden` ist die eine Blocktabelle.** Welcher Block auf Bauland, Mauer und Weg
  liegt, stand im Weltgenerator — solange der der einzige Schreiber war, reichte das.
  Mit dem Zurückbau gibt es einen zweiten, und zwei Tabellen wären das Fehlerbild, das
  niemand sucht: Wer den Weg auf ein anderes Pflaster umstellt, bekäme nach jedem
  Auftrennen einen Streifen in der alten Farbe mitten in der Stadt, für immer.
- **Die Ablage führt, die Welt folgt.** Beides zusammen lässt sich nicht sichern — eine
  Welt kennt keine Transaktion. Scheitert das Blocksetzen, gilt die Ablage: Der Spieler
  darf dort bauen, es sieht nur noch nicht danach aus. Das ist die harmlose Hälfte des
  Fehlers; andersherum wäre das Geld weg und das Grundstück nicht da.
- **Die Nahtgeometrie liegt im Kern** (`Gruppenflaeche`), nicht im Blockschreiber. Ob
  eine Koordinate dazugehört, ist die Frage, bei der ein Fehler teuer ist, und im Kern
  ist sie ohne Server prüfbar — eine Zelle zu viel fällt sonst erst auf, wenn der
  Nachbar sich beschwert. Eine Fuge zwischen zwei Feldern sind 448 Blöcke Oberfläche
  plus 128 Mauerkronen, dazu 2 × 5 Mauerstücke an den Enden: zusammen 596
  Schreibvorgänge; vier Felder im Quadrat rund 2.400.
- **Die Mauer schließt den Ring, wo der Weg geschluckt wird** [1.11., aus dem
  Spieltest]. Im Rasterplan gewinnt der Weg immer, damit sich Wege kreuzen können
  statt an jeder Kreuzung vor einer Mauer zu enden. Am Ende einer Fuge trifft die
  Querstraße auf die Mauerreihe der Parzellen — eine Achse sagt Weg, also liegt dort
  Pflaster. Vor dem Zusammenlegen ist das richtig, die Straße läuft hindurch; danach
  läuft sie gegen Bauland. Ohne das Mauerstück hatte das neue Grundstück als einziges
  in der Stadt an beiden Enden ein fünf Blöcke breites Loch in der Umrandung.
  `Gruppenflaeche` läuft deshalb über die ganze **Naht** und nennt je Koordinate
  beides: was dort ohne die Gruppe läge und was mit ihr liegen muss. Füllen schreibt
  das eine, Zurückbauen das andere — ein Durchlauf, nicht zwei, denn zwei könnten
  sich um eine Koordinate unterscheiden, und dann bliebe nach dem Auftrennen ein
  Mauerstück quer auf einer öffentlichen Straße stehen.
- **Geprüft wird das Bild, nicht der Rechenweg.** Die Probe im Kern fragt für jede
  Gruppenform: Liegt neben Bauland nur Bauland oder Mauer, nie Weg? Das ist genau das
  gemeldete Fehlerbild, und es ist die einzige Form der Prüfung, die es gefunden
  hätte — die Blockzahlen stimmten vorher alle.
- **Das Füllen ist wiederholbar, weil es wiederholt läuft** [1.11.]. Ein Feld an eine
  bestehende Gruppe anzuhängen ist erlaubt, und danach füllt der Befehl die Naht der
  *ganzen* Gruppe. Die alte Fuge liegt mitten darin — sie ist seit dem ersten
  Zusammenlegen Bauland, und auf Bauland wird gebaut. Noch einmal gefüllt, wird das
  Erdgeschoss zu Gras und die Schicht darüber verschwindet: 448 Stellen je Band, ohne
  Warnung und ohne dass etwas herausfällt. `ueberDieNaht` bekommt deshalb die Flächen
  *vor* dieser Zusammenlegung mit und lässt aus, was dort schon dasselbe Ergebnis
  hatte.

  Beantworten kann das nur die Ablage, nicht die Welt: Gras auf einer Fuge sieht gleich
  aus, ob es das Füllen hingelegt hat oder der Spieler. Verglichen wird das Ergebnis
  und nicht die Teilnahme — beim vierten Feld wird aus einem zugemauerten Bandende die
  Kreuzung, und dort muss die Mauer weg.
- **Zählen und Herstellen laufen dieselbe Spalte** (`Wegbau.ueberDieSpalte`), aber mit
  **zwei** Sollwerten je Höhe. Vorher zählte die Warnung ab einem Block über dem Boden,
  der Zurückbau schrieb aber auch die Oberfläche und verfüllte darunter. Wer das Gras
  aufbricht und eine Kiste ebenerdig einsetzt oder ein Becken aushebt, bekam „es geht
  nichts verloren" zu lesen und verlor es beim Bestätigen doch. Zwei Formulierungen
  derselben Sache sind hier besonders teuer, weil die eine die Zusage ist und die
  andere die Tat.
- **Die Wertung einer Stelle ist eine eigene Regel** (`Stadtboden.wertung`), und sie
  unterscheidet drei Dinge, die der erste Anlauf in eine Zahl geworfen hatte
  [1.11., aus der eigenen Prüfung]:

  - **Was die Stadt selbst gelegt hat, ist kein Verlust.** Das Füllgras gehört dazu —
    und zwar auch, wenn es zu Erde verrottet ist. Vanilla macht das von selbst, sobald
    etwas Lichtdichtes darauf steht, und ein Schaf frisst es ebenso weg. Ohne diese
    Ausnahme meldet eine frisch zusammengelegte, völlig unberührte Fläche nach einer
    Weile „dabei verschwinden 448 Blöcke": eine Zahl, die sich ohne Spielerhandlung
    bewegt.
  - **Was der Zurückbau nicht anfasst, wird nicht genannt.** Pflastert jemand die Fuge
    mit dem Straßenblock der Stadt, lässt der Zurückbau ihn liegen — ihn zu melden
    wäre eine Warnung vor einer Tat, die nicht stattfindet. Deshalb braucht die
    Zählung beide Sollwerte: das Bild mit der Gruppe und das ohne sie.
  - **Eine Fehlstelle verschwindet nicht, sie wird verfüllt.** Über einem leeren Becken
    stand „dabei verschwinden 4.928 Blöcke", und das Gegenteil war wahr: Der Zurückbau
    *setzt* diese Blöcke. Die Warnung nennt beides getrennt, Verlust in Rot und
    Verfüllung in Grau.
- **Die Paper-Schicht hat jetzt Tests**, und das ist die eigentliche Lehre aus diesem
  Schritt. Drei Durchgänge hintereinander fanden Fehler im Wegbau, keinen davon durch
  einen Test — jeder wurde nachgerechnet. Der Grund war nicht Nachlässigkeit, sondern
  dass `Wegbau` eine `World` nahm: Damit konnte nur ein laufender Server antworten.

  - `Stadtboden` prüft sich ohne Hilfsmittel, weil Blocktabelle und Wertung reine
    Funktionen von Material zu Material sind. Eine Einschränkung kam dabei gleich mit
    heraus: `Material.isAir()` geht über die Blockregistrierung und wirft ohne Server,
    also fragt `Stadtboden.istLuft` die drei Luftarten direkt ab.
  - `Wegbau` erreicht die Welt jetzt über den **`Weltgriff`** — vier Methoden:
    Unterkante, höchster Block, lesen, setzen. Damit lässt sich die Marktstadt in einer
    Map nachstellen (`Probewelt`), und die Blockzahlen sind vorrechenbar statt
    ablesbar. Die Abstraktion existiert allein für diese Prüfbarkeit, und sie ist es
    wert: Die stärkste Probe vergleicht nicht Zahlen, sondern die Welt — was gefüllt
    und wieder zurückgebaut wurde, muss hinterher Block für Block dem Generator
    entsprechen. Eine Stelle zu viel fällt mit Koordinate auf, während sich Zahlen
    ausgleichen können.
**Stand 01.11.2026 — Teil D, einzeln auftrennen** [entschieden, aus dem Spieltest]:
`/parzelle auftrennen` löst das Feld heraus, auf dem der Spieler steht; der Rest
bleibt ein Grundstück. `/parzelle auftrennen alles` trennt wie bisher die ganze Gruppe.

- **Das Standfeld und nicht eine Richtung.** Beim Zusammenlegen braucht es die
  Richtung, weil ein Feld dazukommt, das noch nicht dabei ist. Beim Auftrennen ist das
  Feld schon da, und der Spieler steht darauf — ein Argument wäre eine Angabe, die er
  sowieso durch seinen Standort macht. Auf dem gefüllten Weg gilt dieselbe Regel wie
  beim Zusammenlegen: Dort ist nicht entschieden, welches Feld gemeint ist.
- **Ein tragendes Feld löst sich nicht heraus** [entschieden]. Wer die Mitte einer
  Reihe herausnimmt, ließe zwei Hälften zurück, die sich nicht mehr berühren — und die
  Fuge zwischen ihnen wäre Bauland ohne Verbindung zum eigenen Grundstück, mitten auf
  einer Straße, die wieder öffentlich sein müsste. Die beiden Alternativen waren
  schlechter: „zerfällt in zwei Grundstücke" wirft neue Gruppennummern und die Frage
  auf, wie sich der bezahlte Preis aufteilt; „dann wird eben ganz aufgetrennt" nimmt
  dem Spieler mehr, als er wollte — genau der Grund, aus dem dieser Teil entstand.
  Geprüft wird das im Kern (`Gruppenflaeche.bleibtGanzOhne`), über Kanten und nicht
  über Ecken: Zwei Felder, die sich nur an einer Ecke berühren, haben keine Fuge
  zwischen sich, dort liegt die Kreuzung, und die gehört allen.
- **Bei zwei Feldern wird ganz aufgetrennt.** Was übrig bliebe, wäre ein einzelnes
  Feld, und eine Gruppe aus einem Feld gibt es nicht: Sie verändert in der Welt nichts
  und wäre nur eine Zeile, die niemand mehr los wird. Der Befehl sagt es vorher und
  tut dann dasselbe wie `alles`.
- **Die Weltarbeit ist dieselbe Auslassung wie beim Füllen, nur umgekehrt.**
  `ueberDieNaht` bekommt die Flächen, die *bleiben* — und nennt genau die Stellen, die
  wegfallen. Löst sich ein Feld heraus, kommt nur der Weg dorthin zurück; die Fugen im
  Rest des Grundstücks bleiben Bauland. Ohne das müsste der Spieler sie neu bezahlen,
  nur weil er ein Feld abgegeben hat. Dieselbe Methode trägt damit beide Richtungen.
- **Die Ablage bekommt eine zweite Operation** (`BesitzLager.ausGruppeLoesen`), neben
  dem Auflösen der ganzen Gruppe. Getrennt und nicht als Sonderfall: Das eine entfernt
  die Gruppenzeile, das andere lässt sie stehen. Der Preis der Gruppe bleibt, wie er
  ist — beim Auftrennen gibt es kein Geld zurück, und eine Rückrechnung wäre eine
  zweite Preisregel neben der Staffel.
- **Der Rückweg legt, was die bleibende Gruppe braucht — nicht den Rasterplan**
  [1.11., aus der eigenen Prüfung]. Der erste Anlauf kannte nur zwei Fälle: Wollte eine
  andere Fläche dasselbe, wurde die Stelle ausgelassen; sonst schrieb er den reinen
  Rasterwert zurück. Der dritte Fall fehlte, und er ist der häufigste überhaupt: Die
  bleibende Gruppe braucht MAUER, wo die aufgelöste PARZELLE hatte.

  Das passiert an den **Armen einer Kreuzung**. Im Quadrat ist die ganze Kreuzung
  Bauland. Löst sich ein Feld heraus, wird sie wieder öffentlich — und an den beiden
  Armen, die zum gelösten Feld zeigen, braucht das Restgrundstück seine Umrandung.
  Der Rasterplan sagt dort WEG, weil der Weg immer gewinnt. Wer den zurückschreibt,
  lässt zwei fünf Blöcke breite Tore offen, durch die die Straße ebenerdig ins Bauland
  läuft: dasselbe Fehlerbild wie am Bandende, nur eine Ecke weiter. Erschwerend: Die
  Stelle liegt nach dem Lösen nicht mehr im Inneren der Gruppe, also hätte der
  Eigentümer sie nicht einmal selbst zumauern dürfen.

  `ueberDieNaht` fragt deshalb die anderen Flächen nach ihrem Anspruch
  (`beiDenAnderen`), und `ohneGruppe` heißt nicht mehr „was der Rasterplan vorsieht",
  sondern „was hier liegt, wenn DIESE Gruppe nicht besteht". Das Auslassen fällt damit
  als eigene Regel weg — eine Stelle wird übersprungen, wenn beide Seiten dasselbe
  wollen, und das ist dieselbe Prüfung. Bauland gewinnt vor Mauer, wenn zwei Gruppen
  dieselbe Stelle beanspruchen.

  Gefunden hat das keine Probe, sondern eine Nachrechnung: Von allen zusammenhängenden
  Formen aus zwei bis sechs Feldern im 3×3-Fenster waren 148 von 476 möglichen
  Herauslösungen betroffen, immer in der Richtung „Pflaster, wo Mauer sein muss".
  Der Spieltest fand es nicht, weil die getestete Gruppe eine L-Form ohne Kreuzung war.
  Gesichert ist es jetzt zweifach: im Kern über den Anspruch an allen zehn
  Armkoordinaten, und in der Welt über das stärkste Orakel, das es hier gibt — ein
  Quadrat, aus dem ein Feld gelöst wird, muss Block für Block aussehen wie ein L, das
  man frisch gefüllt hat.
- **Die Gruppenbefehle hatten gar keine Proben.** Zusammenlegen und Auftrennen waren
  im Besitzbuch ungetestet — nur die Ablage hatte ihren Vertrag. Das erklärt, warum
  die Fehler dieses Schritts alle durch Nachrechnen gefunden wurden. Mit Teil D gibt es
  die ersten sieben.

**Stand 01.11.2026 — was die Meldungen sagen** [aus dem Prüflauf über den Befehlspfad]:

- **Drei verschiedene Lagen, drei verschiedene Sätze.** „Hier ist keine Parzelle" stimmt
  auf dem Weg und vor der Stadtgrenze. Auf freiem Bauland ist es falsch: Die Parzelle
  gibt es, sie gehört nur noch niemandem — und `/parzelle info` sagt an genau derselben
  Stelle „frei" und nennt einen Preis. Wer auf denselben Block zwei Antworten bekommt,
  sucht den Fehler bei sich. Deshalb `NOCH_FREI`, und die Unterscheidung steht in
  `hindernis` — an einer Stelle, nicht bei den sieben Aufrufern: Sechs davon kommen über
  `zustaendigerBesitz` und kennen das Feld unter sich gar nicht.
- **Zweimal zusammenlegen ist kein Hindernis.** Wer denselben Befehl zweimal nimmt, weil
  er die Meldung im Chatgewimmel übersehen hat, las vorher „Beide Felder gehoeren schon
  zu einer Gruppe" — als stünde etwas im Weg. Es ist seine. Zwei getrennte Bescheide:
  `SCHON_ZUSAMMEN` für dieselbe Gruppe, `SCHON_IN_GRUPPE` für zwei verschiedene, und nur
  der zweite nennt den Ausweg (eine auftrennen, Felder einzeln anhängen).
- **Wo ein Rang die Grenze setzt, steht die Zahl dabei** — „dein Rang erlaubt nicht mehr"
  lässt offen, was er denn erlaubt. Und darunter eine Zeile aus der Konfiguration
  (`hinweise.rang-werbung`), die sagt, wie es auf diesem Server weitergeht.
- **Werben ja, einladen nein.** `/parzelle info` zeigte auf einer freien Parzelle Preis
  und „/parzelle nehmen" auch dann, wenn der Rang längst ausgereizt war — eine
  Aufforderung, die drei Sekunden später mit einer Absage endet. Jetzt bleibt der Preis
  stehen (er ist wahr und interessant), die Aufforderung verschwindet, und an ihre Stelle
  tritt die Grenze mit Zahl plus die Werbezeile. Das ist die Trennlinie: zeigen, was es
  kostet, und sagen, wie man dahin kommt — aber nicht zu etwas auffordern, das gerade
  nicht geht.
- **Die Werbezeile ist nicht fest eingebaut.** Was ein Spieler tun muss, um mehr zu
  dürfen, weiß nur der Server — ein eingebauter Hinweis auf einen Shop, den es dort nicht
  gibt, wäre schlechter als gar keiner. Die Vorgabe sagt deshalb nur, was ohnehin wahr
  ist, und sie steht als Konstante im Programm und nicht nur in der `config.yml`:
  `saveDefaultConfig()` schreibt die Datei nur, wenn es sie noch nicht gibt, und ein
  Server, der schon läuft, bekäme sonst ausgerechnet gar keinen Hinweis.
- **Angehängt wird nur, wo der Rang der Grund ist.** Wer zu wenig Geld hat oder auf dem
  Weg steht, bekommt keine Werbung: Sie würde ihm etwas verkaufen wollen, das sein
  Problem nicht löst.

- **Gegengeprüft mit einer Mutationsprobe.** Ein grüner Test, der nicht rot werden
  kann, ist schlimmer als kein Test — genau das waren zwei der ersten Proben: Die eine
  verglich ein Material mit sich selbst, die andere benutzte die geprüfte Regel als
  eigenes Orakel und wäre bei entfernter Auslassung mit null Zusicherungen grün
  durchgelaufen. Beide sind ersetzt. Danach wurde jede Regel, die in diesem Schritt
  einmal falsch war, nacheinander wieder kaputtgemacht: Mindesthöhe der Spalte,
  Gras-Ausnahme, die Tat-fasst-es-nicht-an-Bedingung, Fehlstelle als Verlust,
  Grundgestein, Räumschutz der Mauerstücke, die Auslassung und ihr Ergebnisvergleich.
  Alle acht werden gefangen.
- **Grenze des Zurückbaus, bewusst so:** Nach unten läuft die Spalte bis zur ersten
  Schicht, die schon stimmt. Einen Hohlraum unter einer stehengelassenen Decke findet
  sie deshalb nicht — liegt auf 61 wieder Erde, bricht sie dort ab. Ihn zu finden hieße,
  jede Spalte bis zur Weltunterkante zu lesen: 126 Blöcke statt einem, bei vier Feldern
  über 230.000 nur für diesen Fall. Ein versiegelter Keller unter der wieder
  öffentlichen Straße stört niemanden; ein Loch, in das man fällt, wäre etwas anderes,
  und das findet sie.
- **Geräumt wird nur über den Fugen.** Ein Mauerstück am Bandende war öffentlicher Weg;
  dort durfte der Eigentümer nie bauen, und was trotzdem steht — eine Laterne der Stadt
  etwa — gehört nicht ihm. Die Zählung ließ diese Stellen schon aus, der Zurückbau
  nicht: Er hätte fremdes Werk abgerissen, und gewarnt hätte davor nichts.
- **Das Grundgestein gehört in dieselbe Tabelle** wie Erde und Stein. Der Zurückbau
  stellt eine abgegrabene Spalte wieder her und läuft dabei bis zur Weltunterkante; mit
  zwei Tabellen stünde dort danach Stein, und die Stadt hätte eine Stelle, an der man
  aus der Welt fällt. Nach unten bricht die Schleife jetzt ab, sobald sie findet, was
  dort hingehört — nicht beim ersten festen Block. Sonst blieb hinter einer Glasplatte
  im Schacht ein Hohlraum unter einer Straße, die niemand mehr aufgraben darf.
- **Offen: ein abgebrochener Zurückbau lässt sich nicht nachholen.** Beim Füllen ist
  ein Abbruch die harmlose Hälfte — die Ablage führt, der Spieler darf bauen, es sieht
  nur noch nicht danach aus. Beim Auftrennen ist die Gruppe in der Ablage schon
  aufgelöst: Bleibt die Welt stehen, liegt Gras auf öffentlichem Weg, und kein Befehl
  schreibt es zurück. Das wäre ein Befehl, der die Welt der Ablage nachzieht — eine
  eigene Entscheidung, nicht Teil dieses Schritts.
- **Derselbe Leseweg überall**: `/parzelle info` und die Begründung der Schutzwache
  lasen die Rasterzelle und hätten auf gefülltem Weg „gehört allen" gesagt — während
  dieselbe Wache die fremde Hand gerade abgewiesen hatte. Beide lesen jetzt
  `zustaendigerBesitz`, wie `darfBauen`.

---

## 7. Markthalle, Läden und Handel

### 7.1 Die Markthalle

- Ladeneinheit = ein Quader in `laeden.einheiten` der `config.yml`, der Mietvertrag
  eine Zeile in der Tabelle `ladenmiete` [präzisiert 08.11.2026]. **Nicht** als Besitz der
  Art `ladeneinheit` in `parzellen`, wie hier zuerst stand: Die Einheit ist von Hand
  gebaute Einrichtung und kein gekaufter Besitz, und eine Parzellenzeile daneben wäre ein
  zweiter Ort für dieselbe Geometrie. Der Vertrag braucht zudem Spalten, die `parzellen`
  nicht hat — Miete, Kaution und Ablauftag —, und 99 von 100 Zeilen dort bräuchten sie
  nie.
- Miete wird täglich abgebucht. Bei Zahlungsausfall: Warnung, dann Rückgabe. Der
  Kisteninhalt wandert in ein **Abholfach** und wird **nie** gelöscht. [erledigt
  08.11.2026 — Teil C]. Vier Entscheidungen kamen beim Bauen dazu:
  - **Abgebucht wird unabhängig von der Anwesenheit.** Wäre die Zahlung an das
    Einloggen gebunden, wäre Wegbleiben der Weg, einen Stand kostenlos zu halten — und
    die Halle stünde voll mit Läden, die niemand bezahlt. Der Lauf fährt im
    Spielzeittakt mit (Plan 8.8 verbietet einen zweiten Zeitgeber) und geht über ALLE
    Verträge. Nachgefordert wird nie: Ein Server, der drei Tage aus war, bucht beim
    nächsten Start eine Miete und nicht drei.
  - **Gezählt werden Ausfälle IN FOLGE, und die Zahl steht am VERTRAG** — nicht im
    Buchungsbuch, das sonst immer die Wahrheit über Geld ist. Aus dem Buch wäre sie zu
    rechnen („an wie vielen der letzten Tage fehlt eine Mietzeile"), und ein Server, der
    fünf Tage aus war, hat fünf Tage ohne Mietzeile: Die Rechnung räumte danach jeden
    Laden der Halle. Drei Ausfälle räumen (`laeden.ausfaelle-bis-raeumung`), eine
    Zahlung setzt auf 0 zurück.
  - **Die Kaution kommt beim ABLAUF zurück** und verfällt nur beim Räumen wegen
    Zahlungsausfall. Wer abgelaufen ist, hat jeden Tag gezahlt und nichts falsch
    gemacht; die Kaution ist das Pfand gegen das Weglaufen, nicht gegen das
    Nicht-Verlängern.
  - **Das Abholfach wird mit einem BEFEHL geleert** (`/laden abholfach`) und nicht in
    einem Truhenfenster. Ein offenes Fenster ist ein Zustand zwischen Spieler und
    Datenbank, und wer es im falschen Augenblick schließt, hätte die Ware im Inventar
    UND in der Datenbank — in einer Wirtschaft mit Bundesmarkt ist ein verdoppelter
    Diamantblock sofort Geld. Geholt wird Stapel für Stapel: erst löschen, dann geben,
    und was sich nicht zurückverwandeln lässt, bleibt liegen und steht im Protokoll.
- **Der Ladenschutz** [erledigt 08.11.2026]. Bis dahin waren die Ladeneinheiten reine
  Geometrie: Jeder konnte in jedem Stand bauen und jede Ladentruhe leerräumen, denn die
  `Schutzwache` gilt nur für die Marktstadt und Altfurt ist eine andere Welt. Damit war
  das Mieten eine Buchung ohne Wirkung. Gebaut als **zweite Frage an `darfHandeln`** und
  nicht als eigene Wache: Jene deckt fünfzehn Ereignisarten ab — Blöcke setzen und
  abbauen, Eimer, Feuer, Bilderrahmen, Rüstungsständer, Fahrzeuge, Leinen, Türklinken,
  Behälter —, dreizehn davon über dasselbe `unterbinden()`. Eine zweite Liste wäre
  irgendwann anders, und genau davor warnt die Doku der Schutzwache im ersten Absatz.
  Vier Entscheidungen:
  - **Ein freier Stand ist für JEDEN gesperrt**, nicht für jeden offen. Der Quader ist von
    Hand gebaute Einrichtung; was ein Spieler dort hinterlässt, hinterlässt er dem
    nächsten Mieter. Das trägt zugleich die Räumungsreihenfolge aus Teil C: An eine Truhe
    ohne Vertrag kommt niemand heran.
  - **Keine Mitbauerliste** wie bei den Parzellen. Ein Laden ist ein Geschäft und kein
    Grundstück; wer mithelfen soll, bekommt Waren in die Hand und nicht den Schlüssel.
  - **Das Teamrecht `freimarken.bauen.ueberall` gilt auch im Laden** — sonst müsste ein
    Betreiber kündigen, um aufzuräumen.
  - **Das Erlaubnis-Urteil der Ortsregel ist in ihrer Welt endgültig.** Steht die
    Markthalle in der Marktstadt — `laeden.welt` erlaubt das —, liegt eine Ladeneinheit
    auf einer Parzelle, und zwei Regeln sagen etwas über dieselbe Stelle. Dann gilt die
    Einheit: Der Betreiber hat sie dort hingesetzt.
- **Noch offen: die Truhe beim VERMIETEN leeren.** Das schließt das letzte
  Duplikationsfenster (ein Absturz zwischen dem Löschen des Vertrags und dem Leeren der
  Truhe) und ist ohnehin richtig, denn ein neuer Mieter soll die Ware des Vorgängers nicht
  erben.
- Ein Laden je Spieler, damit nicht drei Leute die Halle besitzen.
- **Ladenverzeichnis**: `/markt suchen <Gegenstand>` zeigt Anbieter, Preis und Weg. Ohne das
  läuft man durch 80 Läden — der häufigste Grund, warum Einkaufszentren sterben.
- **Verkauft wird am SCHILD und nicht per Befehl** [entschieden 08.11.2026]. Ein Schild
  je Gegenstand, an einer Kiste, wie ein gewöhnlicher Shop — der **Kistenshop** ist damit
  die Standardform des Verkaufs, in der Markthalle und überall sonst. Das gilt für den
  Ankauf des Bundes (`/markt verkaufen` fällt damit weg; `/markt liste` bleibt) und ist
  die Linie für jeden Handel, den ein Spieler anbietet.
- **Handeln darf jeder, immer und überall** [entschieden 08.11.2026]. Kein Rang, kein
  Recht und kein Ort beschränkt den Handel: Wer vor einem Kistenshop steht, kauft dort.
  Das ist **keine Nebenbemerkung, sondern die Vorgabe für den Ladenschutz** — und die
  Unterscheidung, die er treffen muss:
  - **Am Schild handeln**: jeder, auch im Laden eines anderen. Sonst hätte ein
    Kistenshop keine Kunden, und die ganze Markthalle wäre eine Reihe verschlossener
    Räume.
  - **Die Kiste öffnen, füllen oder leeren**: nur der Mieter. Es ist sein Warenlager;
    wer hineingreifen darf, braucht den Shop nicht.
  - **Bauen, abbauen und Blöcke bearbeiten**: nur der Mieter, in seiner Einheit.
  Ein Schutz, der pauschal „nichts in fremden Einheiten" sagt, erfüllt die dritte Zeile
  und bricht die erste — und das fällt erst auf, wenn der erste Kunde nichts kaufen
  kann. Der Grund ist kein technischer: Ein Markt,
  den man von überall bedienen kann, ist ein Menü. Wer einmal weiß, dass der Befehl
  geht, geht nie wieder hin — und damit ist das gebaute Gebäude Zierde und niemand
  trifft dort jemanden. Der Markt soll **beliebig erweiterbar und anpassbar** sein: ein
  neues Schild hinstellen genügt, das Sortiment steht weiter in `bundesmarkt.ankauf`.
  Zahlen, Tagesdeckel und Buchung bleiben, wie [7, Teil F](#7-markthalle-läden-und-handel)
  sie gebaut hat; dazu kommen eine Wache auf das Anklicken und das Lesen der zwei
  Schildzeilen. Eigener Schritt.
- Die **Jobwand** im Zunfthaus: Spieler hängen private Aufträge aus. Billige Idee mit viel
  Wirkung [belegt].

| Posten | Betrag |
|---|---|
| Ladenmiete | 500 Mark/Tag [empfohlen] |
| Kaution | 5.000 Mark, bei ordentlicher Rückgabe zurück [empfohlen] |
| Ladenlizenz (einmalig) | 50.000 Mark [belegt] |
| Mietdauer je Abschluss | 7 Tage, Verlängerung kostenlos [entschieden 08.11.2026] |

Die Ladenmiete ist die **stärkste Geldsenke des Servers**: laufend, sichtbar, freiwillig.

Alle vier Zahlen stehen seit dem 08.11.2026 in der `config.yml` unter `laeden:`
[erledigt]. Drei Festlegungen kamen beim Bauen dazu, und jede hat einen Grund, den die
Tafel nicht hergibt:

- **Miete und Kaution stehen in der VERTRAGSZEILE**, nicht nur in der Datei. Das ist der
  Bestandsschutz, den [8.7](#87-die-stammwerte-und-das-neuladen-erledigt-02112026)
  verlangt: Eine Erhöhung trifft nur neue Verträge — auch über eine Verlängerung hinweg.
  Bei der Kaution kommt ein zweiter Grund dazu: Sie **verfällt** bei Räumung, ohne
  zurückgezahlt zu werden. Wäre die gehaltene Summe nur als „eingezahlt minus
  zurückgezahlt" aus dem Buchungsbuch zu rechnen, bliebe die verfallene stehen — und beim
  nächsten ordentlichen Kündigen bekäme der Spieler sie mit heraus.
- **Dass die Lizenz bezahlt ist, belegt das Buchungsbuch** (Grund `laden_lizenz`) und
  keine Spalte. Eine zweite Wahrheit wiche nach dem ersten Absturz von der ersten ab, und
  dann zahlt ein Spieler 50.000 Mark ein zweites Mal. Scheitert ein Abschluss nach dem
  Abbuchen, kommt darum nur die **Kaution** zurück: Sie hängt an dem Vertrag, der nicht
  zustande kam, die Lizenz hängt an keinem Laden.
- **Verlängern kostet nichts** und geht, sobald weniger als eine Mietdauer übrig ist.
  Gezahlt wird täglich; eine Gebühr auf das Verlängern wäre eine zweite Miete für dieselben
  Tage. Die Dauer sagt nicht, wofür bezahlt ist, sondern wann ein Vertrag **ohne Zutun**
  endet — sie ist der Griff gegen den Spieler, der aufhört zu spielen und noch Geld auf dem
  Konto hat. Dieselbe Zahl ist zugleich die Schranke, also liegt das Ende nie weiter als
  zwei Dauern minus einen Tag voraus, und es braucht keine zweite Einstellung dafür.
- **Die Dauer ist 7 Tage und nicht 30** [entschieden 08.11.2026]. Sie ist kein Preis: Die
  Miete steht je TAG, also kosten 7 Tage dieselben 500 Mark am Tag wie 30, und Kaution und
  Lizenz bleiben. Kurz ist sie, weil die Markthalle wenige Stände hat — bei 30 Tagen stünde
  sie einen Monat lang voll mit Läden, in denen nichts passiert. Wer spielt, merkt die Zahl
  nicht; wer eine Woche wegbleibt, verliert den Stand, bekommt aber die Kaution zurück und
  seine Ware aus dem Abholfach. Die Schranke liegt damit bei 13 Tagen.

### 7.2 Die Freie Truhe [entschieden]

Eine offene Truhe in der Markthalle: jeder darf hineinlegen, jeder darf herausnehmen. Kostet
nichts, schafft Gemeinschaft, entsorgt Überschuss.

Damit sie nicht zum Müllhaufen oder zum Selbstbedienungsladen wird [empfohlen]: Entnahme
gedeckelt (etwa 16 Gegenstände je Spieler und Tag), Inhalt wird täglich geleert, keine
geschriebenen Bücher und keine Shulkerkisten — sonst wandert Besitz hinein, der dort nicht
hingehört. Alles wird protokolliert.

### 7.3 Spielerhandel [entschieden]

Direkter Tausch mit **Bestätigungsfenster**: beide sehen das Angebot des anderen, beide
bestätigen, die Übergabe passiert in einem Schritt. Fallen, gegen die ausdrücklich gebaut
wird: Angebot nach der Bestätigung ändern, Verbindungsabbruch mitten im Tausch, volles
Inventar beim Empfänger, Fenster offen lassen und die Gegenstände anderweitig loswerden.

### 7.4 Kistenshops [entschieden 08.11.2026]

**Die Standardform des Verkaufs**: ein Schild an einer Kiste, in der Markthalle und
überall sonst, wo ein Spieler bauen darf. Kein Befehl, kein Menü — man geht hin und
klickt.

#### Das Vorbild, belegt

Lumia nutzte **ChestShop-3**; die Wikiseite vom 11.01.2016 (`w09_chestshops.html`) nennt
das Format genau [belegt]:

| Zeile | Inhalt |
|---|---|
| 1 | nichts oder der eigene Name |
| 2 | Anzahl der Gegenstände je Handel |
| 3 | `B[Preis]:S[Preis]` |
| 4 | die Item-ID — oder `?`, um sie aus der Kiste zu erkennen |

Bedienung [belegt]: **Rechtsklick kauft, Linksklick verkauft, Sneak handelt einen ganzen
Stapel.** Truhe oder Redstonetruhe, einzeln oder doppelt; das Schild an, über, unter oder
neben der Kiste, nicht diagonal und höchstens einen Block entfernt. Zum Handeln war kein
Befehl nötig — nur `/iinfo`, um die Item-ID nachzusehen.

**Das wird übernommen, mit drei Abweichungen**, und jede hat einen Grund aus dem heutigen
Spiel:

- **Die Item-ID wird ein Materialname.** IDs gibt es seit 1.13 nicht mehr.
- **Das `?` ist der Normalweg, nicht die Ausnahme.** Siehe unten: Verzauberte Ausrüstung
  passt nicht auf ein Schild.
- **Preise sind ganze Mark.** Keine Dezimalstellen, kein `K`/`M`-Suffix, keine
  wissenschaftliche Notation — siehe die Dupe-Abwehr.

#### Was handelbar ist [entschieden]

**Auch verzauberte, benannte und beschädigte Gegenstände.** Voll verzauberte Ausrüstung
und verzauberte Bücher sollen verkäuflich sein — das ist der halbe Reiz eines
Spielermarktes.

Daraus folgt die tragende Entscheidung dieses Abschnitts: **Die Ware steht in der
Datenbankzeile, nicht auf dem Schild.** ChestShop schreibt einen Itemcode in Zeile 4 und
führt dafür vier offene Fehler („Not enough space in sign for some Enchanted Book",
#416; #606, #611 Fehler beim Erzeugen des Namens für verzauberte Items; #305 die
Verzauberung steht gar nicht auf dem Schild). Ein Schild hat fünfzehn Zeichen je Zeile;
eine Spitzhacke mit fünf Verzauberungen passt dort nie hinein.

Stattdessen: **Ein Exemplar der Ware in die Kiste legen, `?` aufs Schild.** Das Plugin
nimmt den Stapel als **Warenprobe**, speichert ihn in der Zeile — dieselbe Technik, mit
der das Abholfach aus [Teil C](#71-die-markthalle) seine Stapel hält — und schreibt einen
lesbaren Namen aufs Schild. Das Schild ist dann Bedienoberfläche, die Zeile ist die
Wahrheit. Ein getippter Materialname bleibt möglich für einfache Waren (`STONE`); dann
ist die Probe ein unverändertes Exemplar.

#### Die Marktgebühr [entschieden]

**Erst einmal keine** — beim Spielerhandel und am Bundesmarkt. Beide Sätze stehen aber in
der `config.yml` und sind einstellbar, denn [8.2](#82-quellen-und-senken) führt die
Marktgebühr als laufende Senke, und ob sie gebraucht wird, zeigt erst der Betrieb.

Steht ein Satz über 0, trägt ihn der **Verkäufer**: Das Schild sagt 100, der Käufer zahlt
100, der Verkäufer bekommt 95. Der Preis auf dem Schild ist, was man zahlt — ein
Aufschlag an der Kasse wäre die häufigste Beschwerde.

#### Die Dupe-Abwehr

Das ist der Teil, der aus fremden Fehlern gelernt ist. Die dokumentierten
Vervielfältigungen von ChestShop, QuickShop und ShopChest entstehen fast **nie** in der
Handelsrechnung, sondern an drei Rändern:

**1. Jedes echte Ding, das zur Zierde in die Welt gesetzt wird.** QuickShop warnt in der
eigenen `config.yml`: „Display items above shop containers. WARNING: May cause item
duplication in some environments." Belegte Fälle: ein Hopper-Minecart saugt die
schwebenden Items ein und sie sind „exactly the same as the original" (#1736); mit
Wasserstrom lassen sie sich farmen (#1840); sie vermehren sich über Wochen auf sieben bis
acht je Shop und bleiben nach dem Entfernen liegen (#2086); dasselbe Muster bei
ShopChest (#11). Und die Abwehr bekam ihren eigenen Schaden: Die Erkennung markierter
Display-Items löschte **echte** Items aus Spielerinventaren (#1594).

→ **Es gibt keine Display-Items und keine Hologramme.** Das Schild trägt den Text, und
damit entfällt diese ganze Fehlerklasse. Was ein Shop anbietet, liest man auf dem Schild.

**2. Jeder Weg zur Kiste, der nicht durch den Shop-Code läuft.** ChestShop #538: Der
Trichterschutz hängt an `InventoryMoveItemEvent`, und mit Papers
`disable-move-event: true` feuert das Event nicht — **nichts** wird blockiert.
QuickShop #2165: Ein Cache hielt bis zu drei Minuten die alte Antwort „hier ist kein
Shop", also zogen Trichter, die **vor** der Shop-Erstellung lagen, weiter ab. #2069: Ein
per Redstone abgeschalteter Trichter galt nach `getState()` wieder als aktiv. #2041 und
#2539: Hopper-Minecarts und untergeschobene Trichter leeren Shops, an deren Kiste der
Spieler über das Claim-Plugin gar nicht herankäme.

→ **Kein Trichter zieht aus einer Shopkiste — nie, auch nicht der des Besitzers.**
Hineinlegen bleibt erlaubt: Eine Farm, die den eigenen Laden füllt, ist gewollt. Die
Richtung ist asymmetrisch und damit ohne Sonderfall zu prüfen. Dazu gehört die
Weltregelseite, die [6](#6-schutz) als offen benennt: Explosion, Kolben und Fluss.

**3. Rundung und Zahlenparsing.** ChestShop PR #522 „Fix zero partial price exploit": Bei
Teilhandel wurde der skalierte Preis auf zwei Stellen gerundet und konnte **0** werden —
kostenlos kaufen. ChestShop #30 (2014): das `E` in einem Preis als Dupe; die
Preis-Regex erlaubt `[.e]` bis heute. QuickShop #1902: `1E99` legte das Schild, aber
keinen Shop dahinter — Schild abbaubar, also ein freies Schild; und sehr hohe Preise
fielen auf **10** zurück, zu dem Spieler dann kauften.

→ **Preise sind ganze Mark zwischen 1 und einer Obergrenze, als Ziffern geschrieben.**
Kein `e`, kein `K`, kein Punkt, kein Komma. **Kein Teilhandel**: Was nicht ganz geht,
geht nicht — die Absage nennt, was fehlt. Dieselbe Linie wie beim Mieten
([7.1](#71-die-markthalle)): Eine Rechnung, die kein Geld erzeugen kann, ist einer
bequemen vorzuziehen.

**Dazu drei Schranken, die sich bei den Vorbildern bewährt haben:**

- **Verkaufspreis nie über dem Kaufpreis.** ChestShop:
  `BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE: true`, Begründung in der eigenen
  Config: „This prevents newbies from creating shops that would be exploited." Wäre S
  über B, kauft man im selben Shop billig und verkauft teuer zurück — eine
  Gelddruckmaschine aus Versehen.
- **Klick-Entprellung**, 250 ms je Spieler (ChestShops Wert). Ohne sie wird aus einem
  gehaltenen Mausknopf eine Reihe halber Buchungen.
- **Shulker-Kisten und Bündel sind kein Shop-Block.** QuickShop #1889: Ein Ankaufshop für
  Shulker-Boxen **mit einer Shulker-Box als Shopblock** ließ bei anderer Farbe Shulker in
  Shulker — gegen das Vanilla-Verhalten. Shop-Blöcke sind Kiste, Redstonetruhe und Fass;
  Ofen und Spender nicht (QuickShops eigene Warnliste nennt sie: mehrere Slots,
  Redstone-Zugriff).

#### Was es NICHT gibt, und warum

- **Kein Ankauf und Verkauf getrennt.** Beide Preise stehen auf einem Schild (`B … : S …`),
  wie bei Lumia. QuickShop verlangt für beides zwei Shops — das ist eine Doppelkiste je
  Ware und in einer Markthalle mit wenigen Ständen nicht tragbar.
- **Keine Shop-Rollen.** QuickShop #2539 ist eine Sicherheitslücke aus genau diesem
  Gedanken: Die Rolle `staff` durfte `set_shoptype`, konnte den Shop von Verkauf auf
  Ankauf umstellen und das Konto des Besitzers leeren. Ein Laden hat einen Besitzer;
  wer mithelfen soll, bekommt Waren in die Hand.
- **Kein eigenes Handelsprotokoll.** Jeder Handel ist eine Buchung im Buchungsbuch
  ([8.6](#86-das-buchungsbuch-erledigt-02112026)), und das ist mehr als ChestShop hat
  (nur Logzeilen) und sauberer als eine zweite Tabelle. ChestShop #465 wurde
  geschlossen, **weil der Melder kein Log liefern konnte** — diese Lage kann hier nicht
  entstehen.

#### Die Spielschicht [gebaut 08.11.2026]

Das Anlegen, der Schutz und der Trichter. Der **Handel selbst** — kaufen und verkaufen am
Schild — kommt im nächsten Schritt; die Handelstür in `Schutzwache.Handelsklick` steht
darum weiter auf `KEIN_HANDEL`.

**Anlegen am `SignChangeEvent`.** Davor steht eine Vorfrage, `Shopschild.istGemeint`: Ein
`B` oder `S` mit etwas dahinter in Zeile 3 heißt „hier ist ein Shop gemeint". Ohne sie
bekäme jedes Wegweiserschild die Absage „Zeile 2 ist keine Zahl" — `lies` antwortet auf
ein gewöhnliches Schild mit einem Fehler, denn es hat keine Menge. Die **Zahl** dahinter
ist absichtlich *nicht* Teil der Vorfrage: Wer sich vertippt hat, soll seine Absage mit
der Hilfe lesen und nicht ein stumm stehengelassenes Schild bekommen.

**Acht Absagen für das Schild, nicht eine.** Jede nennt, was falsch ist, und die zweite
Zeile sagt, wie es richtig geht. ChestShops „You don't have permission" deckt sieben
verschiedene Fälle ab und hat dafür ein Forum voller Fragen. Die Zeilennummer kommt aus
`Shopschild.lies` und steht nicht im Wortlaut.

**Drei Lücken der Vorbilder, geschlossen:**

1. **Die Kistenstelle wird eigens geprüft.** Ein Schild hängt auf eigenem Grund, die Kiste
   steht einen Block weiter auf fremdem — dann verkaufte dieser Shop die Ware eines
   anderen. ChestShop deckt das nur über Fremdplugins ab (`CHECK_ACCESS_FOR_SHOP_USE`,
   LWC, WorldGuard); QuickShop #2539 nennt denselben Fall von der anderen Seite. Hier ist
   die Frage Teil des Anlegens, und sie geht an **dieselbe** `Schutzwache`, die über jeden
   Blockklick entscheidet (`Shopzugriff`) — eine zweite Rechtefrage daneben liefe
   auseinander.
2. **Der Trägerblock ist mitgeschützt.** Ein Schild fällt, wenn sein Träger verschwindet,
   und dann steht ein Shop in der Datenbank, dessen Schild es nicht mehr gibt. ChestShop
   fasst das mit einem eigenen `handlePhysicsBreak` **nach** dem Fall nach; hier fällt
   nichts. Weniger Code und kein Zeitfenster.
3. **Das Schild wird gewachst** (`setWaxed`). Seit Minecraft 1.20 darf jeder ein
   unbewachstes Schild bearbeiten — ein Kunde könnte an einem fremden Shop den Preis
   ändern. Wachs ist Vanilla: Es wirkt auch ohne dieses Plugin und gegen den Besitzer
   selbst. Der zweite Riegel ist, dass die Wache jedes Beschreiben einer Stelle mit Shop
   abweist, gleich von wem — die Wahrheit steht in der Datenbank, nicht auf dem Holz. Das
   ist auch QuickShops eigene Warnung zu `allow-owner-break-shop-sign`.

**Neun Ereignisse**, und jedes nennt einen Weg, auf dem ein Shopblock verschwinden kann:
`SignChangeEvent`, `BlockBreakEvent`, `EntityExplodeEvent`, `BlockExplodeEvent`,
`BlockBurnEvent` (Schilder und Kisten sind aus Holz), `BlockPistonExtendEvent`,
`BlockPistonRetractEvent`, `EntityChangeBlockEvent`, `LeavesDecayEvent` und
`InventoryMoveItemEvent`. Explosionen **kürzen die Blockliste** statt abzubrechen: Ein
abgebrochenes Explosionsereignis wäre ein Schutzzauber für das halbe Viertel.
`BlockFromToEvent` und `BlockPhysicsEvent` fehlen mit Begründung — Flüssigkeiten entfernen
weder Schild noch Kiste, und das allgemeine Physikereignis feuert vielhundertfach je
Sekunde.

**Der Trichter ist unsymmetrisch.** Nur `getSource()` wird geprüft: Kein Trichter zieht aus
einer Shopkiste — nie, auch nicht der des Besitzers. Hineinlegen bleibt erlaubt, denn so
wird ein Laden beliefert. Eine Ausnahme für den Besitzer wäre genau die Lücke aus
QuickShop #2539 („they can place a hopper underneath it") mit einer Besitzerprüfung davor,
und die läuft ins Leere, sobald jemand die Kiste des Nachbarn beliefert.

**Beide Hälften einer Doppelkiste sind geschützt**, auch die ohne Schild. Sie teilen ein
Inventar: Ein Trichter unter der anderen Hälfte zieht dieselben Gegenstände, und wer die
freie Hälfte abbaut, bekommt deren Hälfte der Ware. Die Falle dabei ist
`Inventory.getLocation()` — bei einer Doppelkiste gibt es die **Mitte** zwischen den
Hälften, also eine Stelle mit halben Koordinaten, an der kein Block steht und nie ein Shop
hängt. Der Weg geht über `getHolder(false)`.

**Der Warenname wird gekürzt, aber nie verglichen.** `NETHERITE_PICKAXE` hat siebzehn
Zeichen; hart gekürzt stünde „NETHERITE_PICKA" auf dem Schild, also wird das letzte
Wortstück genommen („PICKAXE"). Dass dabei zwei Materialien denselben Kurznamen bekommen
können, ist harmlos — *solange niemand den Namen zum Vergleichen benutzt*. Was eine Ware
ist, entscheidet `Warenprobe.inhalt`. Ein `+` davor heißt „mehr als ihr Material"
(verzaubert, benannt, beschädigt) und ist die halbe Antwort auf ChestShop #305.

**Was nicht geprüft ist:** alles mit Bukkit — `Shopware` (Gegenstand ↔ Text), die neun
Ereignisse, das Wachsen, die Doppelkiste, der Trichter. Dafür stehen Quelltexttafeln
(`ShopwacheTafel`), und die sagen selbst, dass sie Text prüfen und nicht Wirkung. Eine
wirkliche Probe bräuchte einen Probeserver mit Blöcken, einem Spieler und einem Trichter.

---

## 8. Wirtschaft

### 8.1 Zwei Währungen [entschieden]

| | **Mark** | **Goldmark** |
|---|---|---|
| Woher | Berufe, Verkäufe, Events, Tagesbonus | Echtgeld **oder** Tausch aus Mark |
| Wofür | Parzellen, Freistädte, Inseln, Mieten, Flags | Kosmetik, Bequemlichkeit, Rangfarben, Haustiere |
| Rückweg in Echtgeld | — | **nicht vorgesehen** |

**Wechsel: beide Richtungen, abwärts gedeckelt** [entschieden]

```
Mark       ────────►  Goldmark     frei
Goldmark  ────────►  Mark          höchstens 10.000 Mark/Tag, Kurs 20 % schlechter
```

Damit ist alles Gekaufte auch erspielbar — und Echtgeld beschleunigt spürbar, ohne dass eine
Freistadt an einem Nachmittag kaufbar wäre: 400.000 Mark brauchen auf diesem Weg
**mindestens 40 Tage**. „Pay to fast", nicht „pay to win".

**Kurse** [entschieden]:

| Richtung | Kurs |
|---|---|
| Mark → Goldmark | 1.000 Mark = 1 Goldmark, unbegrenzt |
| Goldmark → Mark | 1 Goldmark = 800 Mark, höchstens 10.000 Mark/Tag |
| Echtgeld → Goldmark | **1 € = 5 Goldmark** (eine Goldmark = 20 Cent) |

Die Goldmark ist damit eine **grobe Einheit** — Preise werden in Zehnern genannt, nicht in
Hunderten:

| Was | Goldmark | Echtgeld | Erspielt (1.000 Mark = 1 GM) |
|---|---|---|---|
| Partikel, Hut, Rangfarbe | 2–5 | 0,40–1 € | 2.000–5.000 Mark |
| Zusätzlicher Heimatpunkt | 5 | 1 € | 5.000 Mark |
| Haustier | 10 | 2 € | 10.000 Mark |

Kosmetik ist bewusst billig: eine kleine Freude, über die niemand lange nachdenkt, und
nach ein bis zwei Spielstunden ohnehin verdient.

**Die beiden Unterstützerränge sind etwas anderes** [entschieden]: Sie sind **monatliche
Mitgliedschaften**, nur gegen Echtgeld, nicht erspielbar.

| Mitgliedschaft | Preis |
|---|---|
| **Förderer** | 10 € je Monat |
| **Stifter** | 20 € je Monat |

### 8.4 Warum das die Zusage nicht bricht

„Alles ist auch über das Spielen erreichbar" galt bis zum 01.11.2026 für den gesamten
**Inhalt**. Seitdem gilt es mit einer benannten Ausnahme [entschieden]:

> Eine Mitgliedschaft bündelt Dinge, die es **einzeln auch für Goldmark gibt** — und
> Goldmark ist erspielbar. Davon ausgenommen und **allein den bezahlten Rängen
> vorbehalten**: der Zuschlag auf Parzellen und auf Felder je Grundstück, zusätzliche
> Heimatpunkte und zusätzliche Warps. Alles Übrige bleibt erspielbar; gekauft werden
> sonst nur das Bündel, seine Bequemlichkeit und das Abzeichen.

Praktisch heißt das: Partikel, Hüte, Haustiere und Rangfarben bleiben einzeln im Münzhaus
erhältlich — wer spielt statt zu zahlen, kommt an jedes einzelne Stück heran. Der
Zuschlag, die zusätzlichen Heimatpunkte und die zusätzlichen Warps dagegen nicht: Sie sind
der Inhalt, für den bezahlt wird. Und das Abzeichen „Förderer" bleibt ohnehin unerspielbar
— ein Dankeschön für Unterstützung lässt sich nicht erarbeiten, sonst wäre es keins.

**Die Begründung [entschieden, 01.11.2026]**: Vier Parzellen sind 135 × 135 Blöcke und
mehr — nach Einschätzung des Serverbesitzers absurd viel Platz für ein einzelnes
Bauvorhaben. Wer mehr will, will nicht gewinnen, sondern bauen. Das Angebot richtet sich
ausdrücklich an Vielspieler und an die, die den Server tragen, und zählt damit zu
Bequemlichkeit und nicht zu Macht.

**Was eine Mitgliedschaft weiterhin niemals enthalten darf**: höhere Verdienstsätze,
günstigere Preise, Vorrang beim Support, Vorteile in Wettbewerben, Rechte auf fremdem
Besitz. Sobald einer dieser Punkte dazukäme, wäre es Pay-to-Win — unabhängig vom Preis.

**Der eine Punkt, an dem sich das reiben kann** — hier festgehalten, damit er nicht
verloren geht: Heimatpunkte und Warps lassen sich beliebig vermehren, Fläche nicht. Die
Marktstadt hat 1.764 Parzellen, siehe [4](#4-welten); was ein Zahler bindet, steht keinem
anderen mehr zur Verfügung, und ein Stifter bindet mit 8 Parzellen doppelt so viel wie ein
Bürger ohne Mitgliedschaft. Solange die Stadt halb leer ist, merkt das niemand — wird sie
voll, ist das die erste Stelle zum Nachjustieren. Sie lässt sich nachjustieren, weil die
Zahl in einem Recht steht und nicht im Programm.

### 8.5 Wenn eine Mitgliedschaft ausläuft

Das muss **vor** dem ersten verkauften Monat feststehen, sonst wird es der schlimmste Tag
des Servers. [entschieden]

| Was | Beim Auslaufen |
|---|---|
| Abzeichen, Rangfarbe, Namensanzeige | erlischt |
| Partikel, Haustier-Erscheinungen aus dem Bündel | ruhen — wieder da, sobald verlängert wird |
| Zusätzliche Heimatpunkte aus dem Bündel | werden **inaktiv, nicht gelöscht**; der Spieler kann sie nicht nutzen, verliert sie aber nicht |
| **Parzellen, Freistädte, Inseln, Läden, Geld, Gebautes** | **bleiben unangetastet** |
| **Zuschlag auf Parzellen und Felder je Grundstück** | die Grenze sinkt wieder — wer darüber liegt, **behält alles**, kann aber nichts Neues nehmen und nichts mehr zusammenlegen, bis er wieder darunter ist |
| Einzeln für Goldmark gekaufte Kosmetik | bleibt, sie gehört dem Spieler |

Die letzte Zeile ist der Grund, warum das Bündel nur bündeln darf: Was einzeln gekauft
wurde, ist Besitz; was die Mitgliedschaft leiht, ist geliehen. Beides zu vermischen
erzeugt genau die Enttäuschung, die man nicht wieder einfängt.

Sieben Tage vor Ablauf eine Erinnerung, beim Ablauf eine Nachricht mit dem, was ruht.

Dieselbe Größenordnung in Echtgeld wie beim Vorbild (VIP 10 €, VIP+ 20 €, VIP++ 30 €)
[belegt] — und jeder Posten bleibt in erreichbarer Spielzeit. Damit ist die Zusage „immer
auch über das Spielen erreichbar" nicht nur behauptet, sondern nachrechenbar.
Mengenbonus ab 10 € [empfohlen]: 10 % mehr Goldmark.

**Gegenprobe zum Pay-to-Win.** Eine Freistadt kostet 400.000 Mark. Über den Abwärtstausch
sind das 500 Goldmark — **100 €, und trotzdem mindestens 40 Tage**, weil der Wechseldeckel
von 10.000 Mark je Tag greift. Wer stattdessen spielt, ist bei 2.000–5.000 Mark je Stunde nach
80–200 Stunden da. Der Geldweg ist also weder billig noch schnell, und genau so soll es
sein: Die Besitzleiter ist der Server, sie darf nicht an der Kasse abkürzbar sein.

Soll der Geldweg noch enger werden, ist der Wechseldeckel der Hebel — 5.000 Mark je Tag
ergäben 80 Tage statt 40. Der Kurs selbst bleibt davon unberührt. [empfohlen: bei 10.000
belassen und beobachten]

> Das Konzept-PDF nennt als Gestaltungsgrundsatz, Mark und Goldmark sollten nicht
> automatisch ineinander umtauschbar sein. Direkt entschieden wurde dagegen der Tausch in
> beide Richtungen mit Deckel nach unten — und das gilt (siehe Rangfolge in
> [Abschnitt 1](#1-wie-dieses-dokument-zu-lesen-ist)).

### 8.2 Quellen und Senken

Jede CityBuild-Wirtschaft kippt nach einem halben Jahr, wenn die Senken fehlen.

**Quellen**, alle gedeckelt: Berufe mit abnehmendem Ertrag · Verkauf an den Bundesmarkt
(kleines Sortiment, niedrige Preise) · Tagesbonus · Events · Herausforderungen.

**Senken**, gleichzeitig — einzelne reichen nie:

| Senke | Betrag | Art |
|---|---|---|
| Ladenmiete | 500/Tag | laufend ← stärkste |
| Ladenlizenz | 50.000 | einmalig |
| Weitere Parzellen | ab 25.000, verdoppelnd | einmalig |
| Zusammenlegung | 10.000 × Felder | einmalig |
| Meerinsel / Freistadt | 75.000 / 400.000 | einmalig |
| Hochwald-Sicherung | 5.000 | einmalig |
| Flags (Mobs aus, Betreten) | 25.000 / 50.000 | einmalig |
| Marktgebühr beim Verkauf | 5 % | laufend |
| Reparatur, Teleport, weitere Heimatpunkte | klein | laufend |

**Kalibrierung** [entschieden 01.11.2026]: Ein **Spieler** verdient 2.000–5.000 Mark je
Spielstunde — nicht jeder Beruf einzeln. Dann ist die zweite Parzelle nach 5–10 Stunden
erreichbar, eine Meerinsel nach 15–35, eine Freistadt nach 80–200. Die Freistadt soll ein
Jahresziel sein, kein Wochenendprojekt.

Bis zum 01.11. stand hier „ein Beruf". Das war mit [9.1](#91-berufe-im-zunfthaus-entschieden)
nicht vereinbar: Dort hält jeder zwei Grundberufe plus eine Spezialisierung, und mit der
Lesart „je Beruf" wären alle drei Zeitangaben oben falsch — ebenso die Gegenprobe in
[8.5](#85-wenn-eine-mitgliedschaft-ausläuft), die mit demselben Satz rechnet. Daraus folgt
zweierlei: Die zwei Grundberufe teilen sich dieses Einkommen, und der **Verdienstdeckel zählt
je Spieler**, nicht je Beruf — sonst käme er über zwei Berufe doppelt.

**Zwei Deckel, zwei Wörter** [03.11.2026]: Der Plan nannte beide „Tagesdeckel" — den
**Verdienstdeckel** (was ein Spieler an einem Tag höchstens erarbeiten kann; das ist der hier)
und den **Wechseldeckel** (die 10.000 Mark je Tag, die aus Goldmark zurückfließen dürfen,
[8.1](#81-zwei-währungen-entschieden)). Sie haben verschiedene Beträge, verschiedene Gründe und
verschiedene Hebel; gemeinsam haben sie nur die Tagesgrenze. Ein Wort für beide heißt, dass
eines Tages der falsche gebaut oder der falsche verstellt wird.

**Die Tagesgrenze** [entschieden 01.11.2026, gebaut 03.11.2026 —
[8.8](#88-die-spielzeit-und-die-tagesgrenze-erledigt-03112026)] — sie gilt für Tagesbonus,
Verdienstdeckel, Wechseldeckel, Ladenmiete und die Freie Truhe: **Mitternacht Ortszeit**, die
Zone als Wert in der `config.yml` mit Vorgabe `Europe/Berlin`. Ein Bonus, der um 01:00
umspringt, erklärt sich niemandem. Die Zeitstempel in der Datenbank bleiben UTC; nur die
Tagesgrenze ist eine konfigurierte Zone, und gespeichert wird je Spieler ein Datum und kein
Zeitpunkt.

### 8.3 Was Echtgeld kaufen darf [entschieden]

**Kisten und Schlüssel nur aus Spielzeit, Ereignissen und Herausforderungen — nie aus dem
Shop.** Mojangs Nutzungsbedingungen führen „gambling" ausdrücklich als unzulässig; eine
Lootbox gegen Echtgeld fällt darunter.

Vorgesehen: Kosmetik, Haustiere, Partikel, Rangfarben, zusätzliche Heimatpunkte,
Namensanzeige, Förderer- und Stifter-Abzeichen. Und Goldmark selbst — mit dem Deckel aus
[8.1](#81-zwei-währungen-entschieden).

---

### 8.6 Das Buchungsbuch [erledigt 02.11.2026]

Jede Geldbewegung steht in einer Zeile: wer, wie viel, **warum**, wann und was danach
dastand. `/mark verlauf` zeigt die letzten zehn; aus derselben Tabelle kommt später der
Wirtschaftsbericht aus [12b](#12b-die-spielerseite-entschieden).

**Drei Zusagen, und die dritte hält die ersten zwei zusammen:**

1. Keine Geldbewegung ohne Zeile.
2. Keine Zeile ohne Geldbewegung — ein abgelehntes Abbuchen hinterlässt nichts.
3. **Für ein eröffnetes Konto ist die Summe aller Buchungen sein Stand.**

Die dritte ist die einzige Probe, die *beide* Lügen sichtbar macht, ohne jede einzelne
Buchungsart zu kennen. Sie steht deshalb im `KasseVertrag` und gilt für jede Umsetzung —
auch für die Kasse im Arbeitsspeicher, denn führte nur eine das Buch, prüften die Tests
etwas anderes als der Server tut.

**Der Grund ist Pflichtparameter, ohne Vorgabewert.** Ein Vorgabe-Grund wäre genau die
Lücke, die man sucht, wenn im Buch eines Tages „unbekannt" steht; so zwingt der Übersetzer
jeden künftigen Buchungsweg zur Angabe. Er ist eine Aufzählung und kein Freitext, denn ein
Freitext spaltet dieselbe Senke in Tippfehler auf und der Bericht könnte nie eine Summe
bilden. **Die Richtung gehört an den Grund**, nicht an das Vorzeichen allein: Die erste
Parzelle ist gratis — eine Buchung über 0 Mark, also ohne Vorzeichen, und trotzdem eine
Ausgabe.

In der Datenbank steht der Grund als ASCII-Schlüssel, die deutschen Wörter stehen im
Quelltext. Wer die Wortwahl in die Spalte schriebe, könnte sie später nicht mehr ändern,
ohne alte Zeilen unlesbar zu machen.

**Buchung und Zeile liegen in einer Transaktion.** Das ist der Grund, warum das Buch in der
Kasse geführt wird und nicht in einer Schicht darüber: Nur dort, wo geschrieben wird,
lassen sich beide zusammenhalten. Scheitert das `UPDATE` an der Deckung, wird
zurückgerollt — „nichts ist passiert" heißt dann wirklich nichts. Der Stand nach der
Buchung kommt in derselben Anweisung aus der schon gesperrten Kassenzeile
(`INSERT … SELECT`), ohne zweiten Gang nach Java.

**Die Eröffnungsbuchung reparierte einen alten Fehler** — den dritten der vier Punkte, die
am 01.11.2026 als „vor Schritt 7 zu klären" notiert waren: Die Kassenzeile entstand erst
bei der ersten Buchung, und bis dahin kam das Guthaben bei *jedem Blick* neu aus der
`config.yml`. Wer den Wert dort änderte, änderte damit rückwirkend das Guthaben jedes
Spielers, der noch nichts gekauft hatte. Jetzt friert der Betrag im Augenblick der
Eröffnung ein — beim Beitritt oder bei der ersten Buchung, je was zuerst kommt.

**Der Altbestand kam als eigene Zeile ins Buch**, mit dem Grund `bestand` und nicht
`eroeffnung`: Was vor dem Buch geschah, wissen wir nicht. Diese Zeile behauptet nur etwas
über das Jetzt und erfindet keine Geschichte. Vor der Wanderung wurden das Testgeld
zurückgesetzt und die Testparzellen gelöscht, damit die Nulllinie des späteren Berichts
nicht aus erfundenen Mark besteht [entschieden 02.11.2026].

Nicht dabei und mit Absicht: Goldmark, Wechselkurs, Wechseldeckel, ein Preis für Teleports,
ein Veranlasser bei Zahlungen zwischen Spielern, und Blättern im Verlauf. Jedes davon
kommt mit seinem eigenen Schritt.

---

### 8.7 Die Stammwerte und das Neuladen [erledigt 02.11.2026]

Jede Zahl der Wirtschaft steht in der `config.yml`, und `/freimarken neuladen` nimmt sie im
Betrieb an. Das ist die Zwischenstufe zum Dashboard aus [12a](#12a4-das-dashboard):
Was dort später als Feld mit Begründung steht, steht hier als Kommentar an der Zeile, in die
der Betreiber tippt.

**Ein Schnappschuss, eine Zelle, ein Leser.** Die fünf Stammwerte liegen in *einem*
unveränderlichen Wert, nicht in fünf Feldern — wer rechnet, sieht immer einen in sich
stimmigen Stand. Gehalten wird er in genau *einer* veränderlichen Zelle, und jede Stelle,
die mit Geld rechnet, fragt sie im Augenblick des Gebrauchs.

Darin liegt der ganze Trick: **Neuladen baut nichts neu.** Es tauscht den Inhalt einer Zelle.
Beim Neuladen wird kein Zuhörer angemeldet, kein Zeitgeber gestellt, kein Befehl eingehängt,
keine Verbindung getauscht — also gibt es auch nichts abzumelden, und nichts kann doppelt
dastehen. Ein Neuladen, das Objekte neu baut, hätte ohnehin nichts bewirkt: Die Knoten des
Befehlsbaums zeigen weiter auf das alte Objekt.

**Dieser Satz gilt für das Neuladen und nicht für den Start** [präzisiert 03.11.2026]. Seit
[8.8](#88-die-spielzeit-und-die-tagesgrenze-erledigt-03112026) meldet der Start einen Zuhörer
an und stellt einen Zeitgeber; die Aufzählung oben sagt, was ein *Neuladen* nicht tut. Die
Unterscheidung ist kein Wortklauben, sondern der Grund, warum die Taktlänge nicht neuladbar
ist: Sie steckt im gestellten Zeitgeber, und ein Neuladen, das sie änderte, müsste einen
zweiten stellen — nach dem zweiten Neuladen schrieben drei Zeitgeber dieselbe Minute. Wer „kein
Zeitgeber" dagegen als Verbot liest, baut den nächsten wiederkehrenden Vorgang gar nicht.

**Drei Schranken liegen auf jedem Geldwert**, und alle drei haben denselben Grund: Ein zu
billiger Preis macht Mark, und die liegen binnen Minuten auf vielen Konten — zurückzuholen
nur mit einer Rücksicherung des ganzen Servers.

1. **Roh gelesen und am Typ abgelehnt.** Ein Punkt trennt in YAML keine Tausender: aus
   `25.000` wird 25. Anführungszeichen machen aus einer Zahl Text, und daraus liest Bukkit
   stillschweigend eine 0.
2. **Der Bereich — gerechnet, nicht geschätzt.** Der Preis der letzten Stufe ist
   `grundpreis << 40`, und das muss in einen `long` passen. Darüber wäre der Preis negativ,
   also eine Gutschrift beim Kauf.
3. **Mehr als Faktor 2 braucht eine zweite Bestätigung** — in *beide* Richtungen. Bei der
   Weltgrenze ist nur der Weg nach oben unumkehrbar; bei Geld hängt die gefährliche Richtung
   am Wert: beim Preis nach unten, beim Startguthaben nach oben. Verglichen wird **immer
   gegen den ausgelieferten Wert** und nie gegen den laufenden. Sonst verlangte dieselbe
   Datei nach einem Neustart einen anderen Text, und ein eingetragener Wert gälte plötzlich
   nicht mehr, ohne dass jemand etwas getan hätte.

**Was keine Schranke fängt, ist der Wert im Bereich, der trotzdem falsch ist** — 2.500 statt
25.000. Beide Zahlen sind plausibel. Deshalb steht bei jedem Start und jedem Neuladen die
Staffel im Klartext: „2. Parzelle 25000 - 3. 50000 - 4. 100000". Wer dort „2. Parzelle 25"
liest, sieht den Vertipper. Das ist Erkennung statt Verhinderung, und mehr ist ohne einen
Wächter auf den Umsatz nicht zu haben — den gibt es erst, wenn es Umsatz zu beobachten gibt.

**Beim Start gilt der Rückfall, beim Neuladen alles-oder-nichts.** Den Parzellenkauf wegen
eines Tippfehlers zu sperren nimmt den Spielern mehr, als der Tippfehler wert ist; der
Rückfall ist hier kein ungeprüfter Wert, sondern die ausgelieferte Balance. Beim Neuladen
gilt die harte Regel: Eine halb übernommene Preisliste ist der schlechteste Zustand, denn
dann gelten Zahlen aus zwei Ständen gegeneinander.

**Jede Antwort endet mit dem Block „Nur beim Start gelesen"** — auch wenn sich nichts
geändert hat, und auch wenn eine Klage das Übernehmen verhindert hat. Er nennt Datenbank,
Welten, Ankunftswelt, Startguthaben und die Zahlen, die im Programm bleiben
(Parzelle 64, Weg 7, Boden 62, Deckel 40), jede mit ihrem Grund. Das ist das Gegenmittel
gegen das häufigste Fehlerbild eines Neuladens: Der Befehl meldet Erfolg, und ein Teil wirkt
erst nach dem Neustart. Jede Zeile nennt, was **gilt** — nicht, was in der Datei steht; genau
dort sitzt das gefährlichste Missverständnis, weil ein Betreiber die Datenbank nachträgt und
erwartet, dass sie greift.

**Das Startguthaben wird gelesen, verglichen und gemeldet, aber nicht übernommen.** Die
Kassen geben den Wert für ein noch nicht eröffnetes Konto als Guthaben zurück; ein neuer Wert
im Betrieb änderte also rückwirkend, was jeder Spieler mitbringt, der noch nie gebucht hat —
genau der Fehler, den [8.6](#86-das-buchungsbuch-erledigt-02112026) behoben hat. Ein
Bestandsschutz dafür ist Klasse 2 nach [12a.6](#12a6-was-anpassbar-ist--und-was-nicht) und
ein eigener Schritt.

Kein eigenes Recht: `freimarken.verwalten`, derselbe Zweigschutz wie `/freimarken welt`.
Ein `freimarken.neuladen` wäre genau der zweite Ort für Rechte, gegen den
[12a](#12a4-das-dashboard) argumentiert.

**Die Dauerprüfung** gehört zu diesem Schritt und ist bei jeder künftigen Änderung zu
wiederholen: `getConfig()` darf nur in `onEnable` und in `neulade()` stehen. Jede neue
Lesestelle in einem Befehl oder einem Zuhörer wäre ein Wert, der sich hinter dem Rücken des
Standes ändert — und das ist der belegte Weg, auf dem ein Neuladen halb wirkt.

**Die Datei selbst wird gelesen, nicht nur ihre Pfade.** `reloadConfig()` meldet einen
Lesefehler nicht: Bei kaputtem YAML fängt Bukkit die Ausnahme, protokolliert sie und liefert
eine *leere* Konfiguration, an der die Vorgaben aus der Jar hängen. Jeder Pfad liest dann
brav einen gültigen Wert — der Leser findet nichts zu klagen, und die Alles-oder-nichts-Sperre
greift nicht. Ein Tabulator in der Datei wurde so zu einem grünen
„Übernommen: grundpreis 200000 → 25000", also halber Preis für jeden Kauf bis zum Neustart;
eine gelöschte Datei lief genauso durch, nur ganz ohne Protokollzeile. Deshalb wird die Datei
vor jedem Lesen ein zweites Mal geladen, ohne Vorgaben und mit gesehener Ausnahme: Beim
Neuladen wird dann nichts übernommen, beim **Start bleibt Freimarken aus**. Das ist dieselbe
Entscheidung wie bei einer unerreichbaren Datenbank — lieber kein Parzellensystem als ein
falsch bepreistes.

**Der Deckel deckt die Stufe, nicht die Summe.** Schranke 2 ist für
`grundpreis << 40` gerechnet. Die Summe über alle Stufen ist ein Vielfaches davon und sprengt
den `long` weit innerhalb des erlaubten Bereichs: Bei 349.526 Mark Grundpreis zeigte
`/freimarken preis 64` einen negativen Betrag in Milliardenhöhe — ein Befehl, der jedem
Spieler offensteht. Enger rechnen hilft nicht, weil die Summe mit jeder weiteren Parzelle
wächst; eine feste Grenze für jede Anzahl kann es nicht geben. Die Summe läuft deshalb bei
`Long.MAX_VALUE` an: Eine stehengebliebene Höchstzahl ist als Unsinn sichtbar, eine negative
Zahl sieht aus wie ein Guthaben.

**Geprüft wird die Zahl aus der Datei, nicht ihre unteren 32 Bit.** Eine Obergrenze ist ein
`int`, die Datei kennt diese Grenze nicht. Wer vor der Prüfung verkürzt, prüft einen anderen
Wert: aus `-2147483649` wird `2147483647`, und eine unbegrenzte Parzellenzahl gälte stumm.
Die Regel nimmt darum einen `long`.

Nicht dabei und mit Absicht: die Datenbank als Quelle der Stammdaten (das kommt mit dem
Dashboard, und der eine Leser ist genau die Stelle, an der sie einträte), der gespeicherte
Vorwert und das Zurück in einem Klick (dafür bräuchte es einen Ort, an den das Plugin
schreibt — und `saveConfig()` würde die Kommentare der Datei antasten), und Schlüssel für
Werte, die es noch nicht gibt: Goldmark, Kurse, Mieten, Verdienstsätze, Wechsel- und
Verdienstdeckel. Eine Zeile Anleitung für nichts ist schlechter als keine.

---

### 8.8 Die Spielzeit und die Tagesgrenze [erledigt 03.11.2026]

Der Server zählt, wie lange jemand da war, und schreibt es je Spieler und je Tag fort. Damit
existiert die letzte fehlende Voraussetzung für den Aufstieg aus [9](#9-ränge-entschieden) und
für alles, was „je Tag" heißt — Tagesbonus, Verdienstdeckel, Ladenmiete, Freie Truhe. Teil C
baut die Messung und keinen Verbraucher: Bis Schritt 12 wird gezählt und nichts daran geknüpft.

**Gegengelesen am 04.11.2026.** Drei Urteilslinsen — Geld, Plan, Proben — haben den gebauten
Stand mit Fehlerfall und Belegstelle angegriffen: 17 Einwände, alle behoben. Eine zweite Runde
gegen die Nacharbeit selbst fand sieben weitere; die vier mittleren sind behoben, einer der
drei kleinen ist als Nebenwirkung mit erledigt, zwei stehen am Ende dieses Abschnitts. Was
dabei genauer geworden ist, steht unten als datierter Zusatz bei der Zusage, die er betrifft —
die alten Sätze bleiben stehen, damit lesbar bleibt, was sich geändert hat, und nicht nur das
Ergebnis.

**In der Datenbank steht verstrichene Zeit, niemals ein laufender Zeitpunkt.** Jede Minute wird
ein Zuschlag auf die Zeile dieses Spielers und dieses Tages addiert. Daraus folgt dreierlei,
und jedes einzelne wäre sonst eine eigene Fehlerquelle: Ein Absturz hinterlässt keine offene
Sitzung, es gibt kein Aufräumen beim Start, und zwei Serverprozesse könnten dieselbe Zeile
widerspruchsfrei füllen. Der Preis ist ein Verlust von höchstens einer Taktlänge, wenn der
Prozess hart endet. Eine gespeicherte Startmarke wäre billiger zu schreiben und beliebig falsch
zu lesen: Nach einem Absturz sähe der nächste Start eine Sitzung, die über Tage läuft.

**Alles Schreiben läuft im Hauptfaden**, obwohl Datenbankarbeit dort den Server anhält. Der
Grund ist [8.6](#86-das-buchungsbuch-erledigt-02112026): Geldbuchung und Buchzeile hängen an
einer Transaktion über eine einzige Verbindung. Ein zweiter Faden, der auf derselben Verbindung
eine Spielminute wegschreibt, bestätigt dabei die halb geschriebene Buchung des Hauptfadens
mit. Eine verlorene Spielminute merkt niemand; eine halbe Geldbuchung bricht die Zusage, die
das ganze Buch zusammenhält. Hier liegt deshalb auch der erste wiederkehrende Zeitgeber des
Projekts, und er fängt jeden Fehler in seinem Körper ab: Eine durchgelassene Ausnahme beendet
bei dieser Zeitplanung *alle* weiteren Ausführungen. Der Verlust wäre dann nicht eine Minute,
sondern alles bis zum nächsten Neustart — und keine Zeile im Protokoll würde es sagen.

**Den Tag rechnet das Plugin, nicht die Datenbank.** Die Verbindung steht auf UTC, damit die
Zeitstempel eindeutig sind; dürfte sie den Tag bestimmen, bekäme ein Betreiber in Berlin die
ersten zwei Stunden jedes Tages auf den Vortag gebucht. Der Tag ist damit das einzige Datum im
ganzen Schema, das nicht UTC ist, sondern in der eingestellten Zone gerechnetes Ortsdatum.
Dieser Satz steht auch am Schema selbst: Wer ihn nicht kennt, rechnet die Spalte eines Tages
„zurück".

**Ein Zuschlag über Mitternacht wird geteilt** und nicht dem neuen Tag ganz zugeschlagen —
sonst zählte dieselbe Minute am alten Tag zu wenig und am neuen zu viel, und am alten Tag
hängen Deckel und Bonus. Geteilt wird über die aufgelaufene Zeit und nicht Tag für Tag, weil
jeder einzeln abgerundete Tagesanteil an jeder Tagesgrenze eine Sekunde verlieren würde. Die
Rechnung trägt über die Umstellungstage, an denen ein Tag 23 oder 25 Stunden hat: Eine Zone ist
kein fester Versatz, und wer so rechnet, liegt zweimal im Jahr daneben.

**Die Zone ist ein Wert in der Datei, und es gibt nur eine.** `zeit.zone`, Vorgabe
`Europe/Berlin`. Vorher fragte der Verlauf des Buchungsbuchs die Systemzone des Rechners — zwei
Quellen für dieselbe Frage, und auf einem Rechner mit anderer Systemzone hätten `/mark verlauf`
und die Tagesgrenze verschiedene Tage genannt. Ein fester Versatz wie `UTC+2` wird abgewiesen,
und die Klage nennt den Grund: Er kennt keine Sommerzeit, die Tagesgrenze läge ab dem letzten
Sonntag im März um 01:00.

Zone und Taktlänge wirken **erst nach einem Neustart**. Ein Zonenwechsel mitten am Tag
verschöbe die Tagesgrenze, und der laufende Tag hätte je nach Augenblick zwei verschiedene
Längen; die Taktlänge steckt im gestellten Zeitgeber, und ein Neuladen, das sie änderte, müsste
einen zweiten stellen. Beide stehen deshalb im Block „Nur beim Start gelesen" aus
[8.7](#87-die-stammwerte-und-das-neuladen-erledigt-02112026), und keine Zeile dort darf
behaupten, der Wert aus der Datei *gelte* — genau der Fehler, den das Startguthaben an
derselben Stelle schon einmal erzwungen hat. Die Untätigkeitsgrenze dagegen wird bei jedem Takt
neu gefragt, es wird nichts gebaut und nichts angemeldet, also wirkt sie sofort. Damit ist die
Zone ein weiterer Wert der Art, die [12a.6](#12a6-was-anpassbar-ist--und-was-nicht) in keiner
ihrer drei Klassen führt: änderbar, aber erst nach einem Neustart. Dort fehlt eine vierte
Klasse, und `kasse.startguthaben`, die Datenbankadresse und die Ankunftswelt gehören mit
hinein.

**Keine Zeile behauptet Geltung — und wer nicht neuladbar ist, steht an einer Stelle**
[04.11.2026]. Der Satz oben stand schon vorher hier; die Umsetzung hielt ihn nicht. Für eine
fehlende Zeile schrieb der Leser „Sie gelten aus den Vorgaben des Plugins", und zwei Zeilen
tiefer nannte der Block „in der Datei steht Europe/Berlin" — obwohl in der Datei gar keine
Zeile stand. Dieselbe Antwort nannte damit zwei verschiedene Tagesgrenzen, und der Betreiber
suchte eine Zeile, die es nicht gibt. Entschieden ist jetzt, wer was wissen kann: Nur der
**Leser** sieht, ob ein Wert aus der Datei kam oder stumm aus der Jar zurückfiel, also sagt
allein er etwas über Geltung — und auch er nur über neuladbare Schlüssel („es gilt 4", richtig
beim Start wie beim Neuladen). Der **Halter** sieht einen Wert und nicht seine Herkunft, also
sagt er „es gilt weiter America/New_York, neu gelesen wurde Europe/Berlin". Zusammengelesen
ergeben die zwei Zeilen die ganze Wahrheit, und keine von beiden behauptet mehr, als sie weiß.
Welche Schlüssel nicht neuladbar sind, steht seitdem genau einmal — in der Klasse, die es
entscheidet und die den Block schreibt; Leser, Block und zwei Proben ziehen daraus mit. Wer
einen vierten Wert nicht neuladbar macht, trägt ihn dort ein und nirgends sonst.

Eine gültige und trotzdem falsche Zone fängt keine Schranke: `UTC` ist eine Zone wie
`Europe/Berlin`. Deshalb nennt das Protokoll beim Start den nächsten Tageswechsel in Ortszeit
**und** in UTC. Stehen dort am Nachmittag zwei gleiche Uhrzeiten, ist die Zone falsch
eingestellt. Erkennung statt Verhinderung, dieselbe Antwort wie bei der Preisstaffel in
[8.7](#87-die-stammwerte-und-das-neuladen-erledigt-02112026).

**Was gezählt wird — und was nicht.** Gezählt wird Anwesenheit mit Lebenszeichen. Wer eine
eingestellte Zeit lang keines gibt (`zeit.untaetig-minuten`, Vorgabe 10 Minuten; die 0 schaltet
die Regel ab), zählt nicht mit, und die verworfene Zeit wird auch später nicht nachgezahlt —
sonst wäre die Schranke eine Verzögerung und keine Schranke. Sie schließt den Nachtlang im
Startbildschirm aus, der ohne jede Absicht passiert. Gegen Absicht ist sie **keine Mauer**: Ein
Makro oder ein beschwertes Tastenbrett erzeugt Lebenszeichen, und damit sind die vierzig
Stunden bis Bürger im Stehen erreichbar. Innerhalb dieses Schritts ist das nicht zu reparieren;
es bräuchte eine Betrachtung des Verhaltens — Abbauleistung, Wiederholungsmuster, geöffnete
Oberflächen —, also die Messstelle aus Teil G und keinen Zeitgeber. Wer „~5 h" und „~40 h" in
[9](#9-ränge-entschieden) liest, liest deshalb eine gemessene **Anwesenheit** und keine
gemessene Tätigkeit. Tragbar ist das, weil am Rang nur Fläche und Bequemlichkeit hängen und
keine Macht über andere; wer daran einmal etwas aufhängt, das Mitspieler betrifft, braucht
zuerst die Messstelle.

**Die Grenze ist eine Stufe, und eine Stufe darf nicht kürzer sein als der Takt**
[04.11.2026]. Ein verworfener Takt fällt *ganz* — und daraus folgt eine Schranke, die vorher
fehlte: Bei `zeit.takt-sekunden: 600` und `zeit.untaetig-minuten: 1` genügte eine Minute
Stillstand am Ende des Takts, um die 540 aktiv gespielten Sekunden davor mitzuverlieren, bei
jedem Takt erneut und ohne eine Zeile im Protokoll, denn ein untätiger Ausgang ist nicht
meldepflichtig. Beide Werte lagen in ihren erlaubten Bereichen; geprüft wurde jeder nur für
sich. Erlaubt ist jetzt allein eine Grenze ab der Taktlänge, in ganzen Minuten aufgerundet —
oder die 0 als Ausschalter. Die Regel steht einmal im Kern und wird an drei Stellen gefragt,
weil es drei Wege zu diesem Paar gibt: Ein Wertesatz mit unpassendem Paar kommt gar nicht mehr
zustande, der Leser der Datei klagt mit Grund und kleinster passender Zahl und bleibt bei den
10 Minuten, und ein Neuladen lässt die alte Grenze stehen, wenn sie zum *laufenden* Takt nicht
passt. Der letzte Fall ist nicht konstruiert: Wer Takt und Grenze gemeinsam verkürzt, hat ein
stimmiges Paar in der Datei und ein unstimmiges im Betrieb, weil die Taktlänge erst nach dem
Neustart wirkt. Der Block „Nur beim Start gelesen" sagt das dann mit Grund; nach dem Neustart
gilt beides zusammen. Und weil die Stufe nur über *eine* Taktlänge zugunsten des Spielers
wirkt, darf keine Spanne länger werden als ein Takt — das ist die Bedingung, an der der
nächste Zusatz hängt.

**Die Spielzeit hat keinen Zeugen** — anders als das Geld. Im Buchungsbuch fällt eine verlorene
oder doppelte Zeile an der Zusage „Summe aller Buchungen ist der Stand" auf, ohne dass die
Probe eine einzige Buchungsart kennen muss. Einer verlorenen Minute widerspricht nichts, einer
doppelt gezählten auch nicht. Daraus folgen zwei Regeln. Erstens wird ein fehlgeschlagener
Schreibversuch **nicht wiederholt**: Ein zweiter Versuch mit derselben Liste könnte dieselbe
Minute zweimal addieren, und das ist der Fehler, der nie auffällt. Zweitens steht jeder Verlust
mit Sekunden und Spielerzahl im Protokoll — was sich nicht nachrechnen lässt, muss zumindest
nachlesbar sein. Aus demselben Grund gibt es **kein Setzen von Hand**, auch nicht für den
Betreiber: Ein absoluter Wert hätte weder einen Grund noch einen Prüfer in einer Spalte. Wer
Stunden eintragen will, braucht dafür zuerst das Gegenstück zum Buchungsbuch und nicht einen
zweiten Schreibweg.

**Eine unerreichbare Datenbank hält den Hauptfaden an — der Takt macht daraus keinen
Dauerzustand** [04.11.2026]. Ein Schreibversuch in eine tote Datenbank kostet rund fünf
Sekunden: Gültigkeitsprüfung der Verbindung, dann die Aufbaufrist. Vor diesem Schritt fror ein
toter Datenbankrechner den Server nur, wenn ein Spieler handelte; mit dem ersten
wiederkehrenden Zeitgeber tut er es von selbst, jede Taktlänge erneut — bei der kürzesten
erlaubten Taktlänge die halbe Wanduhr. Und ein Proxy-Neustart, der vierzig Abmeldungen in einen
Tick legt, summierte vierzig Aufbaufristen über die Frist des Serverwächters: Der Server wäre
genau beim Abrechnen abgeschossen worden. Nach einem Fehlschlag setzt der Takt deshalb einige
Schläge lang das **Schreiben** aus, und in diesem Fenster versucht es auch kein Abgang mehr.
**Gerechnet wird weiter** — das ist der Kern, und es ist die Lehre aus einem eigenen Fehlgriff:
Setzte auch das Rechnen aus, blieb die Marke stehen, die Spanne wüchse über mehrere
Taktlängen, und dann träfe die Untätigkeitsstufe aus dem Zusatz oben genau das, was sie
verhindern soll — aktiv gespielte Zeit, stumm. Jetzt rückt die Marke in jedem Schlag vor, die
Zuschläge warten, und der nächste erfolgreiche Zug nimmt sie mit; wiederholt wird dabei nichts,
denn geschrieben war nichts. Verloren ist allein der eine Takt, der wirklich versucht wurde.
Der Preis steht an anderer Stelle: Während des Fensters liegen gemessene Sekunden nur im
Arbeitsspeicher. Ein geordnetes `/stop` nimmt sie mit — das Abschalten fragt die Sperre bewusst
nicht, es ist ein Aufruf für alle offenen Sitzungen und damit höchstens eine Aufbaufrist —, ein
harter Absturz in diesem Fenster verliert sie. Die Zahl der ausgesetzten Schläge ist aus der
Klemme abgeleitet und steht **nicht** in der `config.yml`: Ein Schlüssel dafür wäre ein
Schalter, mit dem ein Betreiber sich stumm Spielzeit wegwerfen kann, und er bräuchte eine
zweite Querprüfung gegen die Klemme — also dieselbe Falle, die der Zusatz oben für die
Untätigkeitsgrenze gerade geschlossen hat.

**Es wird kein Altbestand übernommen.** Minecraft führt selbst eine Spielzeit, aber sie ist für
jedes Plugin beschreibbar, hängt nicht an der Welt und verdoppelt sich, wenn eine Welt kopiert
wird. Ein Altbestand aus dieser Quelle wäre eine erfundene Geschichte — dieselbe Frage hat
[8.6](#86-das-buchungsbuch-erledigt-02112026) mit einer eigenen Zeile „bestand" beantwortet,
die nur etwas über das Jetzt behauptet; hier gibt es nichts, worüber eine solche Zeile etwas
Wahres sagen könnte. Jeder fängt bei 0 an, und die Anzeige sagt genau das — sonst sieht ein
Spieler mit zweihundert Stunden eine kleine Zahl und meldet einen Fehler.

**Die Anzeige rechnet die laufende Sitzung mit**, die Datenbank kennt sie nicht. Wer eben
beigetreten ist, läse sonst eine Null neben dem Satz „wird gezählt". Sie hängt an
`/freimarken spielzeit [<spieler>]` und nicht an einem eigenen `/spielzeit`: Die Befehlstabelle
in [11.1](#111-sprechende-befehle-mit-vervollständigung) nennt ihn nicht, und das Projekt baut
keinen Hauptbefehl, bevor der Plan ihn führt. Sobald der Eintrag da ist, ist daraus ein eigener
Befehl samt Zweitnamen `/playtime` — eine Zeile, derselbe Inhalt. Die fremde Spielzeit zeigt
nur, wer gerade da ist: Zwei ihrer Zeilen gibt es für einen Abgemeldeten gar nicht, und eine
Namensauflösung über Abgemeldete fragt im Zweifel bei Mojang nach und hält dabei den Server an.
Eine zweite, schwächere Auflösung daneben zu bauen wäre schlechter als eine Absage, die den
Grund nennt.

**Was eine dritte Ablage erbt, steht im Vertrag und nicht daneben** [04.11.2026]. Die
Schnittstelle der Ablage nennt sechs Zusagen und sagt, eine geteilte Vertragsprobe prüfe sie
für jede Umsetzung. Zwei davon standen nicht darin: „eine Liste in einem Aufruf, alles oder
nichts" wurde je Umsetzung einmal daneben geprüft, und bei einem Überlauf warfen die beiden
Fassungen verschiedene Ausnahmen — dieselbe Eingabe, zwei Verhalten hinter einer Schnittstelle.
Beides steht jetzt im Vertrag, der Überlauf damit auch mit festgelegtem Typ, und die zwei
Kopien sind weg. Das zählt, weil die Schnittstelle eine dritte Umsetzung ankündigt (die
Rangliste aus [12b.3](#12b3-was-darauf-ist)): Sie erbt die Proben, statt sie zu wiederholen
oder zu vergessen. Dazu gehört auch, dass die Anzeige jeden ihrer Sätze wirklich an einer
Stelle hält — vorher wurde eine Zeile einzeln gesendet, und eine einzeln gesendete Zeile fällt
beim Umbau in `/playtime` heraus, ohne dass eine Probe rot wird.

Nicht dabei und mit Absicht: der Rangaufstieg selbst samt Schwelle und Befehl — die Messung ist
seine letzte fehlende Voraussetzung, das Vergeben eines Rechts kommt mit Schritt 12, und
[15](#15-baureihenfolge) macht „Reisender steigt von selbst" ohnehin zum Abnahmekriterium dort;
ein Schlüssel in der `config.yml` für diese Schwelle (dieselbe Regel wie in
[8.7](#87-die-stammwerte-und-das-neuladen-erledigt-02112026): keine Zeile Anleitung für etwas,
das es noch nicht gibt); Tagesbonus und Verdienstdeckel, die beide auf dieser Tagesgrenze
aufsetzen (Teil E); eine Rangliste und ein Verlauf über alle Tage (Schritt 12b — beides braucht
eine zweite Leseart, und das ist eine Entscheidung im Kern und keine Datenbankfrage); und ein
zweiter Zeitgeber für irgendetwas anderes.

Nicht dabei und mit Absicht, aus der Nacharbeit [04.11.2026]: eine Schranke, die die
Untätigkeitsgrenze gegen das ganze Sperrfenster statt gegen eine Taktlänge prüft — sie hätte
das erlaubte Paar 600 s und 10 min verboten und bei langem Takt eine Stunde Untätigkeitsgrenze
erzwungen, also dem Betreiber eine Einstellung weggenommen, um einen Entwurfsfehler zu decken;
ein eigener Schreibversuch je Taktlänge beim Abmelden — mit den wartenden Zuschlägen verliert
ein Abgang im Sperrfenster nichts, und jeder Versuch kostete erneut eine Aufbaufrist im
Hauptfaden; ein Schlüssel für die Zahl der ausgesetzten Schläge (Begründung oben); und eine
Zusage darüber, was beim *Lesen* einer überlaufenden Summe geschieht — aus diesem Schritt ist
der Fall nicht erreichbar, weil jede Spanne auf zehn Taktlängen geklemmt wird, und gemessen hat
ihn niemand, also steht er nicht im Vertrag. Zwei kleine Textfunde bleiben offen, und sie
stehen hier, damit sie nicht verlorengehen: Die Anzeige nennt weiter die Taktlänge als Abstand („das geschieht alle 60 s"), was im
Sperrfenster zu kurz ist — die genannte *Menge* stimmt dort, die Häufigkeit nicht; und der
Kommentar zu `zeit.takt-sekunden` nennt die rund fünf Sekunden Stillstand als Zahl, obwohl sie
aus zwei Fristen der Datenbankschicht folgt und mit ihnen wandert.

---

## 9. Ränge [entschieden]

```
Erspielbar     Reisender  →  Siedler  →  Bürger
Premium        Förderer      Stifter
```

Die Mitgliedschaften stehen **neben** der Leiter, nicht darüber. Sie geben Kosmetik,
Bequemlichkeit, Dank — und seit dem 01.11.2026 einen **Zuschlag auf die Parzellenzahl und
auf die Felder je Grundstück** [entschieden]. Keine Macht über andere: kein Vorteil beim
Support, keine Rechte auf fremdem Besitz, nach Lumias Regel [belegt] gelten sie nur für
einen selbst.

Der Zuschlag **ersetzt den erspielten Rang nicht, er kommt obendrauf**. Wer am ersten Tag
zahlt, hat 1 + 2 Parzellen, nicht 4 — die vierzig Stunden bis Bürger spart ihm niemand.
Gedacht ist er als Extra für die, die ohnehin viel spielen, und genau so ist er gebaut:
als eigenes Recht (`freimarken.extra.parzellen.<n>`, `freimarken.extra.gruppe.<n>`), das
addiert wird. Als zweite absolute Zahl wäre aus dem Zuschlag eine Abkürzung geworden.

Der Zuschlag ist **den bezahlten Rängen vorbehalten** und nicht für Goldmark zu haben —
die Ausnahme von der Regel in [8.4](#84-warum-das-die-zusage-nicht-bricht), dort mit
Begründung. Was sie enthalten dürfen und was beim
Auslaufen passiert, steht in [8.4](#84-warum-das-die-zusage-nicht-bricht) und
[8.5](#85-wenn-eine-mitgliedschaft-ausläuft).

| Rang | Wie | Was dazukommt [entschieden] |
|---|---|---|
| **Reisender** | beim ersten Beitritt | 1 Parzelle, 1 Heimatpunkt, Markthalle besuchen |
| **Siedler** | ~5 h Spielzeit + Rundgang abgeschlossen | 2 Parzellen, 3 Heimatpunkte, eigener Warp, Laden mieten, Zusammenlegen bis 2 Felder |
| **Bürger** | ~40 h + eigene Parzelle bebaut | 4 Parzellen, 5 Heimatpunkte, 3 Warps, Freistadt gründen, Zusammenlegen bis 4 Felder |
| **Förderer** | Mitgliedschaft, 10 €/Monat | Rangfarbe, Haustier, Partikel, +2 Heimatpunkte, Kornmark-Vorabzugang, **+2 Parzellen, +1 Feld je Grundstück** — Kosmetik auch einzeln für Goldmark; Zuschlag, Heimatpunkte und Warps nur hier |
| **Stifter** | Mitgliedschaft, 20 €/Monat | wie Förderer, aber **+4 Parzellen und +2 Felder je Grundstück**, dazu Abzeichen, Namensanzeige, Platz auf der Stiftertafel in Altfurt |

Der Spielzeit-Aufstieg läuft automatisch — ohne Antrag, ohne Team. Gezählt wird die Zeit seit
dem 03.11.2026 ([8.8](#88-die-spielzeit-und-die-tagesgrenze-erledigt-03112026)), der Aufstieg
selbst kommt mit Schritt 12. Die Stundenzahlen oben sind **gemessene Anwesenheit und keine
gemessene Tätigkeit**: Was diese Messung zusagen kann und was nicht, steht in 8.8 — wer an den
Rang etwas hängt, das Mitspieler betrifft, muss das dort zuerst lesen.

### 9.1 Berufe im Zunfthaus [entschieden]

Zwölf Berufe: sechs Grundberufe, jeder mit einer Spezialisierung ab dem Rang Bürger.

| Grundberuf | Verdient an | Spezialisierung (ab Bürger) | Verdient zusätzlich an |
|---|---|---|---|
| **Bergmann** | Erze, Stein, Tiefe | **Steinmetz** | veredelten Bausteinen, Statuen |
| **Holzfäller** | Holz, Aufforstung | **Zimmermann** | Balken, Türen, Fachwerk |
| **Bauer** | Acker, Tiere, Zucht | **Müller** | Nahrung, Vorratshaltung |
| **Fischer** | Fang — Bonus auf Hafenwerder | **Reeder** | Seehandel, Bootsrennen |
| **Jäger** | Monster, Beute | **Gerber** | Leder, Rüstung |
| **Baumeister** | gesetzten Blöcken | **Architekt** | großen Bauten, Auftragsbau |

**Regeln** [empfohlen]:

- **Zwei Grundberufe gleichzeitig**, dazu **eine** Spezialisierung. Niemand kann alles —
  das ist der Grund, warum Spieler miteinander handeln.
- Berufswechsel kostet Mark und hat eine Sperrfrist, sonst wird je nach Aufgabe
  umgeschaltet.
- **Abnehmender Ertrag**: Derselbe Block bringt innerhalb einer Stunde immer weniger. Ohne
  das ist jede Mobfarm eine Gelddruckmaschine.
- **Ein Block zahlt nur einmal** [entschieden 01.11.2026]. Ein von einem Spieler gesetzter
  Block wird markiert und bringt beim Abbauen nichts; der Baumeister zahlt fürs Setzen, der
  Bergmann nur für ungesetzten Boden. Sonst ist Setzen-und-Abbauen eine Gelddruckmaschine,
  und zwar eine, die jeder in der ersten Stunde findet — beide Berufe darf man gleichzeitig
  halten, und abnehmender Ertrag dämpft den Kreis, hebt ihn nicht auf. **Die Markierung muss
  stehen, bevor die erste Mark aus Arbeit fließt**: Alles, was vorher gesetzt wurde, gilt
  danach für immer als gewachsener Boden.
- **Verdienstdeckel je Spieler**, großzügig genug, dass ihn normales Spielen nicht erreicht.
  Bis zum 03.11.2026 stand hier „je Beruf" — entschieden ist seit dem 01.11.2026 das
  Gegenteil ([8.2](#82-quellen-und-senken)), weil ein Deckel je Beruf über zwei Grundberufe
  doppelt käme.
- Spezialisierungen sind in der Konfiguration angelegt — eine zweite je Grundberuf ist
  später ein Eintrag, kein Umbau.

Die Berufe sind gleichzeitig die wichtigste **Geldquelle** und damit der Hebel für die
Kalibrierung aus [8.2](#82-quellen-und-senken): 2.000–5.000 Mark je Spielstunde.

---

## 9a. Gestaltungsregeln [entschieden]

Aus dem Konzept-PDF, hier auf die Umsetzung übersetzt:

1. **Historische Sprache, klare Bedienung.** Zunfthaus, Bürgeramt und Kornpforte schaffen
   Atmosphäre — die Funktion muss trotzdem sofort verständlich sein. Praktisch: Der
   historische Name steht am Gebäude und im Menütitel, die *Erklärung* steht direkt
   darunter in Alltagssprache. Befehle bleiben sachlich (`/freistadt gruenden`), nicht
   altertümelnd.
2. **Städte als Mittelpunkt.** Marktstadt ist der direkte Einstieg, Freistädte sind das
   langfristige Ziel. Jede Funktion, die nicht auf dieses Gefälle einzahlt, ist Beiwerk.
3. **Eine gemeinsame visuelle Identität.** Die vier Farben aus [2.2](#22-farbwelt-entschieden)
   überall — auch in Menüs und Rangfarben.
4. **Faire Wirtschaft.** Mark im regulären Spiel, Goldmark als gesonderte Premiumebene
   ohne erzwungene finanzielle Vorteile.
5. **Historisch inspiriert statt museal.** Fachwerk, Gassen, Stadtmauern, Handwerkerviertel
   — aber keine behauptete Epoche. Was das Spiel besser macht, darf modern sein; die Menüs
   sind Formulare, keine Schriftrollen.

---

## 10. Einstieg — „anfängerfreundlich" als Entwurfsregel

Das war Lumias Selbstbeschreibung [belegt] und wird hier zur Bauregel.

1. **Das Bürgeramt.** Wer zum ersten Mal ankommt, meldet sich dort an: Regeln und
   Nutzungsbedingungen werden als Formular angenommen [entschieden], nicht als Textwand
   weggeklickt. Erst danach öffnet sich der Bundesmarkt. Die Anmeldung im Bürgeramt ist
   gleichzeitig die Aufnahme in den Bund — Fiktion und Technik fallen zusammen.
2. **Der Rundgang durch Altfurt.** Ein Weg mit Stationen: Bürgeramt, Zunfthaus (Beruf
   wählen), Markthalle mit Freier Truhe, Münzhaus, Bundesratshaus, dann durch das Stadttor
   in die Marktstadt. Am Ende: die erste Parzelle und ein Startguthaben. Ein Ort, kein
   Textfenster.
3. **Die erste Parzelle ist gratis und in fünf Minuten erreicht** — ohne Team, ohne Ticket,
   ohne Wartezeit. Genau hier scheiterte das Vorbild: dort legte ein Teammitglied jede Region
   von Hand an [belegt].
4. **Kein Diebstahl, kein PvP** in Marktstadt, Freistädten und auf den Werdern. Nur Hochwald
   und Arena.
5. **Ein sichtbares nächstes Ziel** — Seitenleiste mit Kontostand, Beruf und dem nächsten
   Schritt.

---

## 11. Befehle und Oberfläche [entschieden]

### 11.1 Sprechende Befehle mit Vervollständigung

Alle Befehle laufen über Brigadier (Paper-Lifecycle-API): echte Argumenttypen, serverseitige
Tab-Vervollständigung, falsche Eingaben werden im Client rot markiert, Rechteprüfung je
Zweig. Umlautfrei, damit sie sich tippen lassen; kurze Alias daneben.

| Befehl | Unterbefehle (Auswahl) |
|---|---|
| `/parzelle` (`/p`) | `nehmen`, `info`, `mitbauer <spieler>`, `zusammenlegen <richtung>`, `auftrennen`, `verkaufen <preis>`, `aufgeben` |
| `/freistadt` (`/stadt`) | `gruenden <name>`, `einladen <spieler>`, `grundstueck anbieten <preis>`, `kasse`, `steuer`, `bund <name>` *(Formular im Bundesratshaus)* |
| `/werder` | `hafen nehmen`, `wolke nehmen`, `stufe`, `besuchen <spieler>` |
| `/laden` | `mieten`, `verlaengern`, `kuendigen`, `abholfach` *(alle vier erledigt 08.11.2026)* |
| `/markt` | `suchen <gegenstand>`, `anbieten <preis>`, `meine` — **kein `verkaufen`**: verkauft wird am Schild ([7.1](#71-die-markthalle)) |
| `/konto` | (Stand), `verlauf`, `zahlen <spieler> <betrag>`, `tauschen <betrag>` |
| `/zuhause` (`/home`) | `<name>`, `setzen <name>`, `entfernen <name>`, `liste` |
| `/warp` | `<name>`, `setzen <name>`, `entfernen <name>` |
| `/zurueck` (`/back`) | — — führt an den letzten Ort: vor Reise, vor Teleport, oder zur Todesstelle ([4.3a](#43a-sterben-zurückkehren-besuchen-entschieden-02112026)) |
| `/tpa` | `<spieler>` — „ich komme zu dir" |
| `/tpahere` (`/tpaher`, `/einladen`) | `<spieler>` — „komm zu mir"; der Eingeladene stimmt zu |
| `/tpaccept` (`/tpaaccept`, `/annehmen`) | — |
| `/tpdeny` (`/tpdecline`, `/ablehnen`) | — |
| `/beruf` | `liste`, `waehlen <beruf>`, `verlassen <beruf>`, `stand` |
| `/menu` | öffnet das Hauptmenü |
| `/hilfe`, `/regeln` | — |

Eigene Heimatpunkte und eigene Warps gehören zur Rangleiter: Ihre Zahl steigt mit dem Rang,
zusätzliche sind über Goldmark erreichbar.

**Jeder Befehl hat beide Namen, deutsch und englisch** [entschieden 02.11.2026]. Viele
Spieler kommen von englischsprachigen Servern und tippen `/plot` und `/balance`, bevor sie
`/parzelle` lesen; eine Fehlmeldung auf einen Befehl, den jeder kennt, kostet den ersten
Eindruck, ein zweiter Name kostet eine Zeile. Gebaut: `/parzelle` auch als `/plot`, `/mark`
auch als `/balance` und `/bal`, `/zurueck` auch als `/back`.

Davon unberührt ist die **Sprache der Meldungen**. Sie hängt an der Sprachdatei aus
[13](#13-betrieb) — heute stehen über hundert Farb- und Textstellen direkt im Quelltext.
Sobald sie in einer Sprachdatei liegen, wird die Sprache **je Spieler wählbar**; bis dahin
sind die Meldungen deutsch und die Befehlsnamen zweispurig. Die Reihenfolge ist Absicht:
Zwei Namen sind eine Zeile, zwei Sprachen sind ein Schritt.

### 11.2 Menüs statt Befehle

Paper 26.3 bringt eine **Dialog-API**: echte Formulare mit Textfeld, Schieber, Häkchen und
Knöpfen. Eine Stadtgründung ist damit ein ausgefülltes Formular, keine Befehlszeile mit sechs
Argumenten. Für Listen — meine Parzellen, freie Ladeneinheiten, Bürgerliste — bleiben
Truhenmenüs über die MenuType-API. Beides ohne Fremdbibliothek. [entschieden]

Zwei bekannte Stolpersteine: Ein Dialogknopf wirkt standardmäßig **genau einmal** und sieht
danach kaputt aus — die Wiederverwendbarkeit muss ausdrücklich gesetzt werden. Und ein Dialog
ist nur Eingabe; geprüft wird serverseitig danach.

### 11.3 Alle Texte in einer Sprachdatei

Keine Zeichenketten im Code, sondern Schlüssel plus Sprachdatei über
`Component.translatable` und einen `TranslationStore`. Dann liegt die gesamte Beschilderung
in einer Datei, die dein Manager bearbeitbar machen kann.

*Bekannte Lücke:* Anzeigenamen und Beschreibungen von **Gegenständen** übersetzt Paper nicht
— und genau dort sitzen bei Truhenmenüs die meisten Texte. Die müssen beim Bauen des Menüs
selbst aufgelöst werden.

---

## 12. Eigenbau

### 12.1 Haltung zu Abhängigkeiten [entschieden]

Keine Fremdabhängigkeiten, die später einschränken — weder durch Lizenz noch durch Altlast.
Praktisch heißt das drei Ringe:

| Ring | Was | Regel |
|---|---|---|
| **Plattform** | Paper, Velocity, MariaDB | Gesetzt. |
| **Nachbarn** | Anti-Cheat, Logbuch, Sperrsystem, Live-Karte | Laufen *daneben*. Dein Code bindet sich **nicht** an sie, sie sind jederzeit austauschbar. |
| **Eigener Code** | Besitz, Wirtschaft, Ränge, Rechte, Schutz, Menüs | Nur Paper-API. Keine GPL-Importe. |

Folge: **Rechte und Regionsschutz baust du selbst**, denn sie sind Teil deines Datenmodells.
„Darf dieser Spieler hier bauen?" ist bei einem Raster reine Arithmetik und bei einer
Freistadt ein Chunk-Nachschlag — kein Grund für ein fremdes Flag-System. Dasselbe gilt für
Blockoperationen beim Leeren und Zusammenlegen: selbst geschrieben, stapelweise über Ticks
verteilt, statt eine GPL-Bibliothek einzubinden.

**Lesen statt einbinden**: PlotSquared (Zusammenlegen, Weltgenerator), Towny (Städte, Bürger,
Steuern), AreaShop (Mietverträge), BentoBox (Inselraster). Alles offener Code, alles
lehrreich — aber nichts davon im eigenen Jar.

### 12.2 Aufbau

```
core/    reines Java, keine Bukkit-Abhängigkeit
         Preisstaffeln, Zusammenlegungsregeln, Mietfristen, Bürgerrechte, Kontobuch,
         Wechselkurs, Wechsel- und Verdienstdeckel — alles, was wirklich kaputtgehen kann
paper/   dünner Adapter: übersetzt zwischen Spielwelt und Kern
```

Der Grund ist dein Testanspruch: **MockBukkit überspringt Tests stillschweigend**, statt sie
rot zu machen — wer eine strenge Python-Suite gewohnt ist, wird davon systematisch getäuscht.
Der Kern dagegen ist mit nacktem JUnit so prüfbar wie dein Python-Code. MockBukkit (MIT,
26.3-Linie vorhanden) nur für die Adapterschicht — und die Zahl übersprungener Tests im Build
sichtbar machen.

### 12.3 Baukette

- **Gradle ≥ 9.1** (praktisch 9.8.x). Gradle 8 bricht mit Java 25 ab, und die Meldung sieht
  nach einem Projektfehler aus.
- `compileOnly("io.papermc.paper:paper-api:26.3.build.231-stable")` — das Wort `build` muss
  im Versionsstring stehen, sonst löst Gradle auf eine andere Spielversion auf.
- **Kein `paperweight`, kein `reobfJar`.** Mojang liefert seit 26.1 **keine verschleierten
  Server-Jars mehr**, Paper hat den Remapper entfernt. Jede Anleitung mit `reobfJar` erzeugt
  ein Jar, das nicht lädt. Das Vorhaben braucht nirgends NMS.
- Shading nur für eigene Bibliotheken, Plugin-ID **`com.gradleup.shadow`** (die alte ist
  tot). Paper-API, Adventure und Guava **nie** mitpacken.
- `--release 25` beim Kompilieren, nicht nur `-source`/`-target`.
- Adventure 5 ab Paper 26.2 ist der spürbarste Bruch gegenüber 1.21 — Codebeispiele aus der
  1.21-Zeit kompilieren beim Textbau nicht mehr.

**Für deinen Manager:** Der javac-Pfad trägt, solange nur `paper-api` gebraucht wird; für
Datenbank und HTTP lohnt der Umstieg auf Gradle — ohne Daemon, sonst hält Gradle unter
Windows Dateien offen und deine Sicherung scheitert mit Zugriffsfehlern, die nach einem ganz
anderen Problem aussehen.

### 12.3a Zwei Randbedingungen, die beim Aufsetzen auffielen

Beides gemessen, nicht recherchiert — und beides steht in keiner Anleitung:

**1. Gradle läuft nicht über einen UNC-Pfad.** `cmd.exe` akzeptiert `\\FriedrichNAS\...`
nicht als Arbeitsverzeichnis und weicht stillschweigend nach `C:\Windows` aus:

```
UNC-Pfade werden nicht unterstuetzt.
Stattdessen wird das Windows-Verzeichnis als aktuelles Verzeichnis gesetzt.
> Directory 'C:\Windows' does not contain a Gradle build.
```

Das trifft `gradle.bat` **und** `gradlew.bat`, also jeden Build. Lösung: über das bereits
vorhandene Netzlaufwerk bauen — `Z:` zeigt dauerhaft auf `\\FriedrichNAS\FriedrichNAS`,
das Projekt liegt damit unter `Z:\mc_server_manager\mc_server_manager\freimarken`.
**Wenn der Manager später selbst Gradle aufruft, muss er denselben Weg nehmen** — mit einem
UNC-Pfad scheitert er mit einer Meldung, die nach einem Projektfehler aussieht.

**2. Das systemweite `JAVA_HOME` zeigt auf Java 8.** Gradle nimmt `JAVA_HOME`, nicht das
`java` aus dem Suchpfad, und bricht ab: *„Gradle requires JVM 17 or later to run. Your build
is currently configured to use JVM 8."* Für den Build wird es deshalb je Aufruf gesetzt.
Dauerhaft umzustellen wäre sauberer, betrifft aber alles andere auf dem Rechner — eine
Entscheidung für sich. [offen]

**Gemessen** beim Aufsetzen: erster Build 48 s, inkrementell 11 s (Konfigurations-Cache
greift). Die Dateiüberwachung kann Windows auf der Freigabe nicht einrichten, deshalb
`org.gradle.vfs.watch=false` — sonst steht bei jedem Build eine Warnung, die echte
Warnungen verdeckt.

### 12.4 Ausliefern

Unter Windows hält ein laufender Server seine Plugin-Jar **offen**; Überschreiben scheitert.
Der vorgesehene Weg ist der Ordner **`plugins/update/`** — neue Jar dort ablegen, Paper
übernimmt sie beim nächsten Start. Genau der Griff für deinen Manager, und dein Schlafmodus
liefert das Neustartfenster ohnehin.

**`/reload` ist keine Option.** Sobald ein Plugin die Lifecycle-API nutzt — und das tut jedes
mit modernen Befehlen —, schaltet Paper den Reload ab. Betriebsregel: **Code über Neustart,
Konfiguration über einen eigenen Befehl.** Alles, was man häufig ändert — Texte, Preise,
Mietdauern, Wechselkurs — gehört deshalb in Konfiguration, nicht in den Code.

---

## 12a. Verwaltung und Anbindung an den Manager [entschieden]

Alles, was hier an Zahlen steht — Preise, Mieten, Kurse, Verdienstsätze, Rangrechte,
Mietdauern, Texte — ist eine **Stellschraube**, keine Konstante. Sie gehört in ein
Dashboard, nicht in den Quelltext.

### 12a.1 Zwei Arten von Daten

Die Unterscheidung bestimmt den ganzen Aufbau:

| | **Stammdaten** | **Spieldaten** |
|---|---|---|
| Was | Preise, Kurse, Wechsel- und Verdienstdeckel, Rangrechte, Berufssätze, Mietdauern, Texte, Weltgrenzen | Wem gehört was, Kontostände, Mietverträge, Spielzeit, Mitgliedschaften |
| Wer ändert | **der Mensch**, selten, bewusst | **das Spiel**, ständig, automatisch |
| Wer schreibt | Manager | Plugin |
| Wer liest | Plugin beim Start und auf Befehl | Manager, nur zur Anzeige |

### 12a.2 Die drei Regeln der Anbindung

1. **Stammdaten: der Manager schreibt, das Plugin liest.** Änderungen landen in der
   Datenbank; das Plugin liest sie beim Start und auf `/freimarken neuladen`. Schläft der
   Server gerade, liegt die Änderung einfach da und greift beim nächsten Start — kein
   Sonderfall, kein Fehlerzustand.
2. **Spieldaten: der Manager liest direkt, ändert aber nur über das Plugin.** Wer einen
   Kontostand in der Datenbank korrigiert, während das Plugin ihn im Speicher hält, hat ihn
   beim nächsten Schreiben wieder verloren. Korrekturen laufen deshalb als Befehl an den
   laufenden Server — dann aktualisiert das Plugin seinen Zwischenspeicher und protokolliert
   mit.
3. **Jede Änderung wird protokolliert, mit Grund.** Wer, wann, was, warum. Bei Geld und
   Besitz ist das keine Formalie: Es ist der Unterschied zwischen „der Admin hat es
   korrigiert" und „mir fehlen 50.000 Mark".

### 12a.3 Der Weg, der schon existiert

Für Regel 2 braucht es **keine neue Schnittstelle**. Dein Manager kann bereits Befehle an
die Serverkonsole schicken — derselbe Weg, den der KI-Assistent für `befehl_ausfuehren`
nutzt. Damit entfällt ein HTTP-Dienst im Plugin, ein zusätzlicher Port, eine zweite
Authentifizierung und ein zweiter Ort für Rechte.

```
Mensch ──► Manager-Dashboard ──► MariaDB (Stammdaten)
                    │                      ▲
                    │                      │ liest beim Start / auf Befehl
                    └──► Konsolenbefehl ──► Freimarken-Plugin ──► MariaDB (Spieldaten)
                                                                        │
         Manager liest Spieldaten nur lesend für die Anzeige ◄──────────┘
```

Umgekehrt braucht das Plugin **einen** Griff in Richtung Manager: „starte Server X", damit
ein Spieler beim Wechsel in ein künftiges zweites Backend „Server startet…" sieht statt
„Unable to connect". Das ist ein kleiner, abgegrenzter HTTP-Aufruf — nicht der Hauptweg.

### 12a.4 Das Dashboard

Ein eigener Bereich „Freimarken" im bestehenden Manager — er bringt Anmeldung, Rollen,
Protokoll und Oberfläche schon mit:

| Seite | Inhalt |
|---|---|
| **Wirtschaft** | Parzellenpreise und Staffelung, Zusammenlegungsgebühr, Mieten, Kaution, Lizenzen, Flag-Preise, Marktgebühr |
| **Währung** | Kurse beide Richtungen, Wechseldeckel, Goldmark-Preis, Mengenbonus — mit Schranken und Verlauf, siehe [12a.6](#12a6-was-anpassbar-ist--und-was-nicht) |
| **Ränge** | Schwellen, was jeder Rang freischaltet, Mitgliedschaftsinhalte |
| **Berufe** | Sätze je Beruf, abnehmender Ertrag, Verdienstdeckel je Spieler, Spezialisierungen |
| **Welten** | Grenzen, Reset-Zeitpunkte, Vorgenerierung anstoßen |
| **Texte** | die Sprachdatei, direkt bearbeitbar |
| **Besitz** | wer besitzt was — mit Suche; Aktionen: übertragen, freigeben, jeweils mit Grund |
| **Konten** | Kontostände und Verlauf; Korrektur nur mit Grund |
| **Mitgliedschaften** | wer, seit wann, bis wann, aus welcher Zahlung |
| **Protokoll** | alles, was über das Dashboard geändert wurde |

**Rechte** [empfohlen]: Lesen ab Moderator, Stammdaten ändern nur Admin, Geld- und
Besitzkorrekturen nur Super-Admin — dieselbe Abstufung, die der Manager schon kennt.

Der **KI-Assistent** bekommt dafür Werkzeuge nach demselben Muster wie bei den
VanillaTweaks-Paketen: lesend ohne Bestätigung, verändernd nur als Vorschlag mit
Bestätigung, und der Satz im Dialog kommt vom Manager.

### 12a.6 Was anpassbar ist — und was nicht

Grundsatz [entschieden]: **Alles, was sich sinnvoll anpassen lässt, ist im Dashboard
anpassbar** — Preise, Mieten, Gebühren, Kurse, Wechsel- und Verdienstdeckel, Goldmark-Preis,
Mengenbonus, Verdienstsätze, Rangschwellen, Mietdauern, Texte. Nichts davon steht im Quelltext.

Drei Klassen, weil sie unterschiedlich behandelt werden müssen:

| Klasse | Beispiele | Wirkung |
|---|---|---|
| **Frei anpassbar** | Preise für Neukäufe, Kurse, Wechsel- und Verdienstdeckel, Gebühren, Verdienstsätze, Texte, Rangschwellen | sofort bzw. beim nächsten Neuladen |
| **Anpassbar mit Bestandsschutz** | Ladenmiete, Mitgliedschaftspreis | gilt für **neue** Verträge; laufende behalten ihren Satz bis zur Verlängerung und sehen den neuen vorher |
| **Nur lesbar, mit Begründung** | Parzellengröße und Wegbreite, Weltnamen, Weltgrenze nach unten, geteilte Inventare, Spieler-Identität (E1) | nicht änderbar — der Weltgenerator und bestehende Daten hängen daran |

Die dritte Klasse ist der eigentliche Punkt. Diese Werte *sehen* aus wie Einstellungen, sind
aber Struktur: Wer die Parzellengröße im laufenden Betrieb ändert, hat kein neues Raster,
sondern ein kaputtes. Sie stehen deshalb im Dashboard — **sichtbar, aber gesperrt, mit dem
Satz, warum**. Eine gesperrte Einstellung mit Begründung ist freundlicher als eine fehlende,
weil sie die Frage beantwortet, bevor sie gestellt wird.

**Bestandsschutz** ist kein Luxus. Wird die Ladenmiete von 500 auf 800 erhöht und gilt das
sofort für alle, fühlt sich jeder Ladenbesitzer übervorteilt — zu Recht. Läuft die Erhöhung
erst bei der Verlängerung an und wird vorher angezeigt, ist es eine Preisanpassung. Derselbe
Betrag, ein völlig anderer Vorgang.

**Schranken gegen Vertipper** [empfohlen]:

- Jeder Zahlenwert hat einen plausiblen Bereich. Ein Kurs von 1 statt 1.000 würde die
  Wirtschaft in Minuten ruinieren, und niemand merkt es, bevor es zu spät ist.
- Große Sprünge (mehr als Faktor 2) brauchen eine zweite Bestätigung mit dem alten und dem
  neuen Wert im Klartext.
- Der **vorherige Wert bleibt gespeichert** — ein Zurück ist ein Klick, keine Rekonstruktion.
- Jede Änderung ins Protokoll: wer, wann, von was auf was, warum.

**Geldwerte brauchen eine Historie.** Ändert sich ein Kurs, behalten vergangene Buchungen den
Kurs, zu dem sie stattfanden. Sonst erzählt der Wirtschaftsbericht aus
[12b.3](#12b3-was-darauf-ist) rückwirkend eine andere Geschichte als die, die passiert ist.

### 12a.5 Zahlungen und Mitgliedschaften

Der Manager ist die richtige Stelle dafür — er hat eine öffentliche Adresse, einen
Zeitplaner und läuft auch dann, wenn der Spielserver schläft.

1. Zahlungsanbieter meldet eine Zahlung an den Manager (Webhook).
2. Manager legt die Mitgliedschaft an und schreibt den Anspruch in die Stammdaten.
3. Manager schickt dem laufenden Server einen Befehl — das Plugin schaltet frei. Schläft er,
   greift es beim Start.
4. Der Zeitplaner des Managers prüft täglich auf abgelaufene Mitgliedschaften und entzieht
   sie nach den Regeln aus [8.5](#85-wenn-eine-mitgliedschaft-ausläuft).

**Das Plugin spricht nie mit dem Zahlungsanbieter.** Es weiß nur, wer bis wann Mitglied ist.
Damit bleibt alles Geldbezogene in genau einem Programm, und ein Fehler im Spiel kann keine
Zahlung auslösen.

---

## 12b. Die Spielerseite [entschieden]

Eine öffentliche Website für die Spieler: Statistik, Shop, Städte, Karte, Chronik.

### 12b.1 Warum ein eigenes Programm

Die Spielerseite ist **nicht** ein weiterer Bereich im Manager, sondern ein eigener Dienst
mit eigenem Prozess und eigenem Datenbankzugang. Der Grund ist nüchtern: Der Manager kann
Server starten und stoppen, Dateien schreiben, Welten löschen und Kontostände ändern. Die
Spielerseite ist von außen erreichbar. Beides in einem Prozess hieße, dass jede Lücke in der
öffentlichen Hälfte unmittelbar neben der Serversteuerung sitzt.

| | Manager | Spielerseite |
|---|---|---|
| Erreichbar | intern | öffentlich |
| Darf | alles | fast nichts |
| Datenbankrecht | Lesen und Schreiben | **Lesen** auf Spieldaten, Schreiben nur auf eigene Tabellen |
| Anmeldung | Benutzerkonten mit Rollen | Minecraft-Konto über Einmalcode |

Gemeinsam ist nur die Datenbank — und dort hat die Spielerseite einen **eigenen Benutzer mit
Leserecht**. Sie kann Besitz und Konten anzeigen, aber nicht ändern. Was ein Spieler
tatsächlich auslösen darf (Stadtbeschreibung bearbeiten, Mitgliedschaft kündigen), läuft über
ihre eigenen Tabellen oder über den Manager.

### 12b.2 Anmeldung ohne Passwort

```
Im Spiel:   /verknuepfen          →  "Dein Code: 4F2K-9XQ1, gültig 10 Minuten"
Auf der Seite: Code eingeben      →  Konto verknüpft, fertig
```

Kein Passwort, keine Registrierung, keine Mailadresse. Der Code beweist, dass derjenige
gerade mit diesem Minecraft-Konto auf dem Server steht — besser als jedes selbstgebaute
Anmeldeverfahren, und es gibt nichts zu stehlen, was es nicht ohnehin gibt. [empfohlen]

Die Sitzung läuft über ein normales Sitzungstoken. Für den Shop ist eine Mailadresse nötig
(Rechnung, Kündigungsbestätigung) — die wird erst beim ersten Kauf abgefragt, nicht vorher.

### 12b.3 Was darauf ist

| Bereich | Inhalt |
|---|---|
| **Mein Profil** | Spielzeit, Rang und Fortschritt zum nächsten, Kontostand, Berufe mit Stufen, Abzeichen, meine Parzellen und Städte |
| **Statistik** | Eigene Zahlen über die Zeit: verdiente Mark, abgebaute Blöcke, Fänge, besuchte Welten |
| **Ranglisten** | Spielzeit, Vermögen, Insel-Stufe, größte Freistadt, Beruf-Bestenlisten — jeweils mit Zeitraum |
| **Städteverzeichnis** | **Jede Freistadt bekommt eine eigene Seite**: Name, Beschreibung und Bild vom Besitzer gestaltet, Bürgerliste, Bündnis, freie Grundstücke mit Preis, Position auf der Karte |
| **Markt** | Das Ladenverzeichnis der Markthalle von außen durchsuchbar: wer verkauft was zu welchem Preis — Vorratsstand inklusive |
| **Live-Karte** | BlueMap eingebettet, mit Freistädten und Bündnissen als Ebenen |
| **Chronik** | Ein Zeitstrahl des Servers, automatisch gefüllt: Stadt gegründet, Bündnis geschlossen, Rekord gebrochen, Event gelaufen. Billig zu bauen, erstaunlich beliebt |
| **Wirtschaftsbericht** | Geldmenge im Umlauf, was durch Senken verschwand, Durchschnittspreise. Macht die Wirtschaft nachvollziehbar — und zeigt dir früh, wenn sie kippt |
| **Events** | Kalender, Anmeldung, Ergebnisse vergangener Wettbewerbe |
| **Galerie** | Screenshots der Spieler, Bauwettbewerbe mit Abstimmung |
| **Shop** | siehe unten |
| **Moderation** | Bannliste mit Grund und Dauer, Entbannungsantrag, eigener Meldungsstand |
| **Regeln, Team, Kontakt** | Teamliste mit Rollen, Impressum, Pflichttexte |

Der **Wirtschaftsbericht** ist nicht nur Schmuck: Er ist dein Frühwarnsystem. Wenn die
Geldmenge je Spieler über Monate steigt, greifen die Senken aus
[8.2](#82-quellen-und-senken) nicht — und das sieht man dort, bevor man es im Spiel merkt.

### 12b.4 Shop und Kündigung

- Der Shop zeigt Goldmark-Pakete und die beiden Mitgliedschaften.
- **Ausgelöst** wird der Kauf auf der Spielerseite, **abgewickelt** über den
  Zahlungsanbieter, **gebucht** im Manager (Webhook, siehe
  [12a.5](#12a5-zahlungen-und-mitgliedschaften)). Die Spielerseite fasst nie Geld an.
- **Die Kündigung der Mitgliedschaft gehört auf dieselbe Seite** — in Deutschland ist eine
  leicht erreichbare Kündigungsschaltfläche Pflicht, und sie darf nicht schwerer zu finden
  sein als der Abschluss.
- Laufzeit, nächste Abbuchung und Kündigungsstand stehen im Profil, nicht in einer Mail.

### 12b.5 Technisch

- Eigener Dienst (FastAPI, dieselbe Sprache wie der Manager), eigener Prozess, eigener
  Hostname.
- Eigener Datenbankbenutzer: `SELECT` auf Spieldaten, `INSERT/UPDATE` nur auf
  Sitzungen, Stadtbeschreibungen, Galerie, Anmeldungen.
- **Zwischenspeicher großzügig**: Ranglisten und Statistiken werden minütlich erneuert, nicht
  bei jedem Aufruf. Eine öffentliche Seite darf den Spielserver nicht ausbremsen.
- Die Seite funktioniert auch, wenn der Spielserver schläft — sie liest aus der Datenbank,
  nicht aus dem Spiel. Nur der Verknüpfungscode braucht einen laufenden Server.
- Inhalte von Spielern (Stadtbeschreibung, Galerie) sind **nicht vertrauenswürdig**:
  escapen, Bildgrößen begrenzen, Meldefunktion, und keine Verweise ohne Prüfung.

---

## 12c. Moderation [entschieden]

### 12c.1 Zwei Aufgaben, die oft verwechselt werden

| | **Spielmoderation** | **Serverbetrieb** |
|---|---|---|
| Regelt | Menschen | Maschinen |
| Tut | bannen, stummschalten, Meldungen abarbeiten, Grief zurückrollen | starten, neu starten, Logs lesen, Dateien ändern, sichern |
| Werkzeug | **das Plugin, im Spiel** | der Manager |
| Braucht Managerzugang | **nein** | ja |

Das ist die wichtigste Zeile der Tabelle: **Spielmoderatoren brauchen keinen Zugang zum
Manager.** Wer Streit schlichtet und Griefing zurückrollt, braucht dafür keine Konsole, keinen
Dateieditor und keine Sicherungen — er braucht Rechte *im Spiel*. Damit erledigt sich auch
die Sorge, Freimarken müsse zur Moderation von den anderen Servern getrennt werden: Die
Trennung entsteht dadurch, dass Moderatoren im Manager gar nicht vorkommen.

Wer doch Managerzugang braucht — jemand, der mit neu startet und Logs liest —, bekommt
**eine** Berechtigungszeile auf Freimarken mit Konsole und Neustart, ohne Dateirechte. Das
kann der Manager heute schon: Die Serverliste zeigt Nicht-Super-Admins nur Server, auf die
sie eine Berechtigung haben.

### 12c.2 Teamrollen im Spiel

Nach dem Vorbild [belegt], auf eigene Namen gebracht:

| Rolle | Darf |
|---|---|
| **Helfer** | Meldungen annehmen und weiterreichen, Fragen beantworten, zu Spielern springen |
| **Moderator** | zusätzlich stummschalten, kicken, zeitlich bannen, Grundstücke einsehen, Grief zurückrollen |
| **Verwalter** | zusätzlich dauerhaft bannen, Besitz übertragen, Freistädte auflösen |
| **Eventleiter** | Events anlegen, Preise ausschütten — keine Strafrechte |
| **Baumeister** | Baurechte in Altfurt und auf Eventflächen — keine Strafrechte |

Getrennte Rollen für Strafen und Bauen sind Absicht: Die beiden Aufgaben ziehen
unterschiedliche Leute an, und niemand braucht beides.

### 12c.3 Werkzeuge, die ins Plugin gehören

- **Melden**: `/melden <spieler> <grund>` legt eine Meldung an. Moderatoren sehen eine
  Liste, springen hin, bearbeiten sie, schließen sie mit einem Vermerk ab. Der Melder
  bekommt eine Rückmeldung — sonst meldet beim zweiten Mal niemand mehr.
- **Stummschalten, kicken, bannen** — jeweils mit **Dauer und Grund**, und der Grund wird
  dem Betroffenen angezeigt. Eine Strafe, die niemand erklärt, erzeugt nur Wiederkehr.
- **Einsehen** eines Grundstücks ohne Baurecht: hinsehen dürfen, anfassen nicht.
- **Zurückrollen** eines Griefs über das Logbuch (Prism läuft als Nachbar,
  siehe [13](#13-betrieb)) — begrenzt auf Zeitraum und Spieler, nie flächig.
- **Alles protokolliert**, mit Grund und Bearbeiter. Bei Besitzübertragungen und
  dauerhaften Bannen zusätzlich im Manager sichtbar.

Zwei Regeln dazu [empfohlen]:

1. **Rechte, nicht Operator.** Moderationswerkzeuge hängen an Rollenrechten, nicht an
   `/op`. Ein Operator umgeht jeden Schutz und jedes Protokoll — als Moderationsmittel ist
   das ein Freibrief, kein Werkzeug.
2. **Kein Vorteil beim Support für zahlende Mitglieder** [belegt aus dem Vorbild]. Keine
   schnellere Bearbeitung, keine Sonderbehandlung. Sobald Geld die Reihenfolge bestimmt,
   ist die Mitgliedschaft kein Dankeschön mehr.

### 12c.4 Was auf der Spielerseite sichtbar wird

- **Bannliste** mit Grund und Dauer — öffentlich, wie im Vorbild.
- **Entbannungsantrag** als Formular.
- **Eigener Meldungsstand**: Was ist aus meiner Meldung geworden?
- **Teamliste** mit Rollen, damit klar ist, wen man ansprechen kann.

Öffentliche Bannliste und sichtbarer Meldungsstand sind unbequem — sie zwingen zu
Begründungen, die standhalten. Genau deshalb gehören sie dazu.

---

## 13. Betrieb

- **Vorgenerierung** vor dem ersten Spielertag, für jede Welt mit fester Grenze.
- `pause-when-empty-seconds=0` in jeder `server.properties`: dein Schlafmodus übernimmt das,
  und Minecrafts eigene Pause hält den Port offen — alles, was pingt, hält den Server dann
  für wach.
- **Logbuch** mit Rollback-Weg. CoreProtect verweigert auf 26.3 den Start; Prism ist die
  gepflegte Wahl [ungeprüft im Detail].
- **Anti-Cheat**, sobald Freunde von Freunden kommen — das sind Fremde. Läuft als Nachbar,
  nicht als Abhängigkeit.
- **Sperrsystem** mit Begründung und Dauer.
- **Grenzen pro Chunk** gegen Redstone- und Trichterlawinen — angekündigt, **bevor** die
  erste Freistadt verkauft ist.
- **spark** ist in Paper enthalten: messen statt raten.
- **Live-Karte**: BlueMap (3D, im Browser) statt des alten Dynmap-Ansatzes [entschieden].
- **Versionswechsel**: Kornmark zurücksetzen, neuer Startwert, neu vorgenerieren — gebaute
  Welten bleiben unangetastet [entschieden].
- Sicherungen macht dein Manager. Was fehlt: eine **Wiederherstellungsprobe** — eine
  Sicherung, die nie zurückgespielt wurde, ist eine Vermutung. Die MariaDB gehört mit hinein.

---

## 14. Wachstum

Heute ein Server, höchstens 20 Spieler [entschieden]. Was trotzdem jetzt richtig gebaut wird,
damit später nichts umgebaut werden muss:

| Regel | Warum jetzt |
|---|---|
| **Keine Annahme „ein Server"** | Jeder Wert, der nur im Arbeitsspeicher lebt — Kontostände, Besitzlisten, Mietfristen — ist in dem Moment falsch, in dem ein zweites Backend startet. Datenbank ist die Wahrheit, Speicher nur Zwischenspeicher mit Verfall. |
| **MariaDB von Anfang an** | Kein Umzug, keine Migrationsnacht. |
| **Welt ≠ Code** | Welten kommen aus Konfiguration; eine Welt auf einen eigenen Server zu verschieben ist dann ein Eintrag, kein Umbau. |
| **Spieler-Identität festgelegt (E1)** | Ändert sie sich später, zeigt aller Besitz auf UUIDs, die es nicht mehr gibt. |

Erst wenn ein zweites Backend kommt — etwa Wolkenwerder als eigener Server oder Minispiele —
bekommt der Freimarkenbund einen eigenen Velocity, und die Haupt-Lobby transferiert dorthin.

---

## 15. Baureihenfolge

Jeder Schritt endet mit etwas, in dem man herumlaufen kann. Keine Fristen.

| # | Schritt | Fertig, wenn … |
|---|---|---|
| 0 | E1 bestätigen, Namen festschreiben | sie hier eingetragen sind |
| 1 | ~~Paper 26.3, Java 25, Gradle-Gerüst, leeres Plugin mit einem Befehl~~ **erledigt 31.10.2026** | ~~Server startet und der Befehl antwortet~~ — Plugin lädt auf Build 231, `/freimarken preis 5` antwortet „400.000 Mark" aus dem Kernmodul |
| 2 | ~~MariaDB, Schema, Migrationen, Datenzugriff hinter Schnittstelle~~ **erledigt 31.10.2026** | ~~ein Testdatensatz übersteht einen Neustart~~ — MariaDB als Dienst auf dem Server-PC, Zugriff aus dem LAN; `Datenbank` mit nummerierten Schema-Schritten, `MariaLager` und `MariaKasse` hinter `BesitzLager`/`Kasse`. 26 Tests gegen die echte Datenbank, darunter Überleben eines Verbindungsabbruchs, der eindeutige Schlüssel gegen Doppelvergabe und UTC-Rückgabe. Am 31.10. auch im Plugin gelaufen: Paper 26.3/231 lädt den mitgelieferten Treiber im Plugin-Klassenlader, `migriere()` läuft, `/freimarken` meldet „Ablage MariaDB". Tests liegen seither in einem eigenen Schema `freimarken_test`; `leereAlles()` verweigert jedes andere |
| 3 | ~~Welten anlegen, Grenzen, Reisen~~ **erledigt 02.11.2026** — Vorgenerierung angestoßen, Gebäude aus [2.1](#21-die-gebäude-in-altfurt) von Hand | ~~man kann durch alle Welten laufen~~ — Acht Welten aus der `config.yml` ([4.2](#42-erweiterbar-und-portierbar-entschieden)), Grenzen aus einer Regel im Kern, Vorgenerierung mit Chunky samt gerechneten Befehlen ([4.4](#44-wie-vorgeneriert-wird-entschieden-02112026)), Reisen über sieben Tore und `/warp` ([4.3](#43-wie-man-zwischen-den-welten-reist-entschieden-02112026)), Sterben, `/zurueck` und `/tpa` ([4.3a](#43a-sterben-zurückkehren-besuchen-entschieden-02112026)), Spielmodus je Welt. Drei adversariale Prüfläufe mit 88 Agenten: 28 bestätigte Befunde in Teil A, alle behoben; die übrigen zwei lieferten Baupläne, die zwei eigene Entwürfe korrigiert haben (ringweise Suche am festen Zielort; eine zweite Tauschmethode im Rückweg). 342 Tests. *Offen ist nur Handarbeit: die acht Generierläufe auf dem Spielserver und die zehn Platzhalterbauten in Altfurt* |
| 4 | ~~**Weltgenerator für die Marktstadt** — Raster, Wege, Grenzen~~ **erledigt 31.10.2026** | ~~das Raster steht~~ — `Rasterplan` im Kern (8 Tests ohne Server), `MarktstadtGenerator` im Plugin; am Testserver geprüft: Parzellenmitte Gras, Mauer Steinziegel, Weg glatter Stein, Krone Halbstufe |
| 5 | Besitz-Kern: Parzelle nehmen, Schutz, Mitbauer — **fertig 31.10.2026** | zwei Spieler, einer baut, der andere nicht — *Code steht: `Besitzbuch` mit allen Regeln (Preisstaffel, Obergrenze, Mitbauer, Aufgeben ohne Rückzahlung), `Schutzwache` mit einer Regel für alle 14 Schutzereignisse, `/parzelle` und `/mark`. 89 Tests grün, Startprobe auf einem echten Paper-26.3-Server durch. Danach eine adversariale Prüfung mit 41 Agenten: 9 bestätigte Befunde, alle behoben — darunter fehlender Schutz gegen Schaden an Rüstungsständern und Rahmeninhalt, Kisten-Loren, ein Besitzschutz, der bei Datenbankausfall nach außen offen scheiterte, und Tests, die das Spielschema leerten. Durchgang mit zwei Spielern am 31.10. gelaufen: Der Schutz greift auf fremdem Grund und sagt es in der Aktionsleiste, Mitbauer schaltet frei und wieder zu, die zweite Parzelle wurde fuer 25.000 Mark bezahlt, und Parzelle -1/0 beweist die negative Seite der Rasterrechnung. Jeder Schritt in der Datenbank gegengeprueft* |
| 6 | ~~Zusammenlegen und Auftrennen mit Gebühr~~ **erledigt 01.11.2026** | ~~zwei Parzellen werden eine, der Weg verschwindet~~ — `/parzelle zusammenlegen <richtung>` und `/parzelle auftrennen [alles]`, Gebühr 10.000 je Feld der neuen Gruppe. Die Geometrie liegt im Kern (`Gruppenflaeche`), die Blocktabelle an einer Stelle (`Stadtboden`), die Weltarbeit hinter einem 4-Methoden-Griff (`Weltgriff`), damit sie ohne Server prüfbar ist. Vier Runden Fix-und-Nachweis mit je einem adversarialen Prüflauf: elf bestätigte Befunde, alle behoben, jeder mit Probe und Mutationsnachweis. Drei Spieltests am 1.11. bestanden — zuletzt das Quadrat mit 19 von 19 nachgemessenen Koordinaten. 231 Tests |
| 7 | ~~Wirtschaft: Berufe, Bundesmarkt-Ankauf, Tagesbonus~~ **erledigt 07.11.2026** — in ACHT Teilen und nicht sieben: A Buchungsbuch und B Stammwerte (02.11.), C Spielzeit und Tagesgrenze (03.11.), D Berufe waehlen (06.11.), E Verdienen samt Blockmarken (06.11.), F Bundesmarkt-Ankauf, G Messstelle und H Tagesbonus (07.11.). Siehe [8.6](#86-das-buchungsbuch-erledigt-02112026), [8.7](#87-die-stammwerte-und-das-neuladen-erledigt-02112026), [8.8](#88-die-spielzeit-und-die-tagesgrenze-erledigt-03112026) und [9.1](#91-berufe-im-zunfthaus-entschieden) | ~~man kann Mark verdienen und ausgeben~~ — 154 Lohnsaetze mit drei Bremsen (Stufenminderung, harte Stundenmenge, Tagesdeckel), 56 Ankaufpreise am Bundesmarkt mit eigenem Deckel, eine Messstelle, die neben der Anwesenheit die TAETIGE Zeit zaehlt, und ein Tagesbonus mit Serie. 1.662 Proben; 117 Einspritzungen, alle gefangen. Spieltest am 07.11. bestanden: Kontostand 50.548 Mark, und die Summe aller Buchungen ergibt ihn genau |
| 8 | Markthalle, Mietverträge, Abholfach, Freie Truhe | ein Laden läuft ab und wird sauber zurückgegeben |
| 9 | Freistädte: gründen, Grundstücke vergeben, Bürger, Kasse | ein Spieler verkauft einem anderen ein Grundstück |
| 10 | Hafenwerder mit eigener Angeltabelle | eine Insel lässt sich sichern, der Fang unterscheidet sich |
| 11 | Wolkenwerder mit Skyblock-Mechaniken | Generator, Insel-Stufe, Herausforderungen laufen |
| 12 | Ränge, Goldmark, Wechsel mit Wechseldeckel, Kosmetik | Reisender → Siedler steigt von selbst |
| 12a | **Dashboard im Manager**: Wirtschaft, Währung, Ränge, Berufe, Texte | ein Preis lässt sich ändern, ohne das Plugin neu zu bauen |
| 13 | Bürgeramt-Anmeldung, Rundgang, Menüs, Sprachdatei, Farbwelt | ein Neuling kommt ohne Hilfe zur ersten Parzelle |
| 14 | Kleine Spiele in Altfurt | Parkour, Lotterie, Angelwettbewerb laufen |
| 15 | Spielerhandel, Haustiere, Live-Karte, Event-Kalender | siehe [Anhang B](#anhang-b-übernommen-aus-dem-vorbild-entschieden) |
| 16 | Mitgliedschaften: Zahlungsanbieter, Webhook, Ablaufprüfung | eine Mitgliedschaft läuft ab und entzieht sauber, ohne Besitz anzufassen |
| 16a | **Moderation**: Rollen, Melden, Stummschalten, Bannen mit Grund, Protokoll | eine Meldung wird angelegt, bearbeitet und beantwortet |
| 17 | **Spielerseite**: Verknüpfung, Profil, Ranglisten, Städteverzeichnis, Karte | ein Spieler verknüpft sein Konto und sieht seine Zahlen |
| 18 | Spielerseite: Shop, Kündigung, Wirtschaftsbericht, Chronik | ein Kauf läuft durch und lässt sich auf derselben Seite kündigen |

---

## 16. Offene Punkte

Das Konzept-PDF nennt als nächste Entscheidungen: Rangrechte, Grundstücksgrößen,
Stadtgründungsregeln, Wechselkurse, Shopangebote, Weltgestaltung und die Serverversion.
Davon sind durch die Rückfragen bereits entschieden: Grundstücksgrößen (64×64),
Weltgestaltung (Größenpaket), Serverversion (Paper 26.3) und der Wechselkurs.

Inzwischen ebenfalls entschieden: E1 (Identität), Rangrechte, Berufe, Goldmark-Preis.

Zwei Kleinigkeiten habe ich entschieden statt nachzufragen, weil beide **umkehrbar** sind —
widersprich, wenn du es anders willst:

- **Kein Preisdeckel in Freistädten** [empfohlen]. Der Stadtbesitzer setzt seine Preise
  frei; Wucher regelt sich von selbst, weil daneben die Marktstadt mit gratis erster
  Parzelle liegt. Statt eines Deckels zeigt das Bürgeramt **alle** Angebote mit Preis — wer
  zu teuer ist, steht sichtbar unten. Ein Deckel wäre leicht nachzurüsten, eine zerstörte
  Preisfreiheit nicht.
- **Auflagen für eine Freistadt** [empfohlen], wie in [6.2](#62-stufen-und-preise): mindestens
  ein vergebenes Grundstück und eine Regelwand, sonst kein Teleporter im Bundesratshaus und
  keine Ladenlizenz. Dazu eine **Verfallsregel**: Wird eine Freistadt zwölf Monate lang von
  niemandem betreten und hat keine Bürger, fällt sie nach zwei Warnungen zurück — sonst
  blockieren tote Städte Fläche, die es nur einmal gibt.

Echte offene Punkte bleiben damit nur noch inhaltliche beim Bauen:

- **Startguthaben** für einen neuen Reisenden — Vorschlag beim Bau von Schritt 13.
- **Verdienstsätze je Beruf** im Einzelnen — Vorschlag beim Bau von Schritt 7, kalibriert
  auf 2.000–5.000 Mark je Spielstunde.

---

## Anhang A: Das Vorbild

Vom alten Lumia sind **324 Seiten** im Webarchiv erhalten; **123 Seiten und 7 Bilder** liegen
lokal unter `data/lumia-archiv/`. Bewusst dort und nicht im Repository: in den Forenseiten
stehen Klarnamen von damals, `data/` ist in `.gitignore`.

| Übernommen | Nicht übernommen |
|---|---|
| Besitzleiter mit Spielermarkt | Ticketsystem als Kaufweg — Spieler konnten **nie selbst** sichern, ein Teammitglied legte jede Region von Hand an. Das war der Engpass des Servers. |
| Zwei Währungen, Premium auch erspielbar | Vote-Kopplung — jetzt Echtgeld plus Tausch |
| Ränge als Bequemlichkeit, nie als Macht | Rangnamen |
| Einkaufszentrum mit Mietläden, Jobwand | Minispiele als eigene Server |
| Tutorialort statt Textfenster | 1.8-Technik insgesamt |
| Preisverhältnisse (Stadt ≈ 5× Insel) | absolute Beträge |

Wichtigste Originalseiten: `w22_welten.html` (Weltenliste und Preise), `w08_befehle.html`
(~60 Befehle, 09.01.2016), `vip_befehle.html`, `shop_vip_pakete.html`, `R_faq2015.html` und
`R_faq2016.html` (Regeln), `w09_chestshops.html`, `w13_regionen.html`,
`w16_customcrafting.html`, `n09_staedte.html`, `c02_invictus.html` … `c11_elementia.html`
(acht Bündnisnamen), `bilder/` (Spawn, Stadtwächter, User-Städte, Werbetafeln, Skyblock).

## Anhang B: Übernommen aus dem Vorbild [entschieden]

Alles eigener Code, alles nach dem Kern:

- **Haustiere mit Lizenz** — einmalige Lizenz am Bundesmarkt, danach beliebig viele Tiere
  zähmen. Begleiter mit Stufen, handelbar.
- **Spielerhandel mit Bestätigungsfenster** — siehe [7.3](#73-spielerhandel-entschieden).
- **Fertigkeiten** nach Art von mcMMO — eigenes, schlankes System statt Fremdplugin, damit
  es an Berufe und Ränge andockt.
- **Heirat** — kleines, hübsches Feature mit gemeinsamem Heimatpunkt.
- **Bündnisse zwischen Freistädten** — gemeinsame Kasse, gemeinsamer Teleporter,
  Bündnis-Chat. Im Vorbild entstanden daraus acht benannte Reiche [belegt].
- **Drachenflüge als Transport** — Schnellreise zwischen Altfurt und den Welten, als
  Erlebnis statt als Befehl.
- **Live-Karte** — BlueMap, im Browser, mit Freistädten und Bündnissen.
- **Event-Kalender mit eigenem Rang** — Eventmanager als Teamrolle, Termine im Spiel und im
  Browser.

## Anhang C: Rechtliches

Kleingewerbe ist angemeldet; Impressum und ein Teil der Pflichttexte kommen von
`friedrich-dietrich.de` [entschieden].

**Monatliche Mitgliedschaften** [entschieden] sind rechtlich mehr als ein einmaliger
Kauf: Dauerschuldverhältnis mit Kündigungsmöglichkeit, Kündigungsschaltfläche,
Preisänderungen nur mit Ankündigung, und die Widerrufsbelehrung muss die wiederkehrende
Zahlung abdecken. Das gehört geprüft, bevor der erste Monat verkauft wird.

Was für den Verkauf von Goldmark und Mitgliedschaften zusätzlich dazugehört:

- **AGB** für Spielbetrieb, Verkauf digitaler Inhalte **und laufende Mitgliedschaften**
- **Widerrufsrecht** bei digitalen Inhalten samt ausdrücklichem Verzicht vor Ausführung
- **Jugendschutz** — Zustimmung Erziehungsberechtigter bei Minderjährigen
- **Datenschutzerklärung**, die auch Spielerdaten, Logbuch und Live-Karte abdeckt
- **Zahlungsanbieter** sowie Umsatzsteuer- bzw. Kleinunternehmerregelung
- **Mojangs Nutzungsbedingungen** im Originaltext — insbesondere das Verbot von
  Glücksspielmechanik gegen Echtgeld

Das ist keine Rechtsberatung. Vor dem ersten verkauften Rang gehört es geprüft.
