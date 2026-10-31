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
| **Münzhaus** | Bank und Währung | Kontostand, Verlauf, Wechsel Mark ↔ Goldmark, Tagesdeckel sichtbar |
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
35.344 Chunks (−94…93 je Achse), 36 Regionsdateien. Ein spawnzentriertes Werkzeug
bräuchte um 32/32 den Radius 1.530, sonst fehlt der ganze Westring.

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
  ein Eintrag plus Generator, kein Umbau.
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

---

## 7. Markthalle, Läden und Handel

### 7.1 Die Markthalle

- Ladeneinheit = Besitz der Art `ladeneinheit` mit Mietzins und Ablaufdatum.
- Miete wird täglich abgebucht. Bei Zahlungsausfall: Warnung, dann Rückgabe. Der
  Kisteninhalt wandert in ein **Abholfach** und wird **nie** gelöscht. [empfohlen]
- Ein Laden je Spieler, damit nicht drei Leute die Halle besitzen.
- **Ladenverzeichnis**: `/markt suchen <Gegenstand>` zeigt Anbieter, Preis und Weg. Ohne das
  läuft man durch 80 Läden — der häufigste Grund, warum Einkaufszentren sterben.
- Die **Jobwand** im Zunfthaus: Spieler hängen private Aufträge aus. Billige Idee mit viel
  Wirkung [belegt].

| Posten | Betrag |
|---|---|
| Ladenmiete | 500 Mark/Tag [empfohlen] |
| Kaution | 5.000 Mark, bei ordentlicher Rückgabe zurück [empfohlen] |
| Ladenlizenz (einmalig) | 50.000 Mark [belegt] |

Die Ladenmiete ist die **stärkste Geldsenke des Servers**: laufend, sichtbar, freiwillig.

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

„Alles ist auch über das Spielen erreichbar" gilt weiterhin — aber für den **Inhalt**, nicht
für das Abzeichen. Die Regel, die das trägt [entschieden]:

> Eine Mitgliedschaft bündelt ausschließlich Dinge, die es **einzeln auch für Goldmark
> gibt** — und Goldmark ist erspielbar. Gekauft werden das Bündel, die Bequemlichkeit des
> Bündels und das Abzeichen. Nie der einzige Zugang zu etwas.

Praktisch heißt das: Partikel, Hüte, Haustiere, Rangfarben und zusätzliche Heimatpunkte
bleiben einzeln im Münzhaus erhältlich. Wer spielt statt zu zahlen, kommt an jedes
einzelne Stück heran — nur nicht an das Abzeichen „Förderer", und das ist auch richtig so:
Ein Dankeschön für Unterstützung lässt sich nicht erarbeiten, sonst wäre es keins.

**Was eine Mitgliedschaft niemals enthalten darf**: mehr Parzellen, mehr Fläche, höhere
Verdienstsätze, günstigere Preise, Vorrang beim Support, Vorteile in Wettbewerben. Sobald
einer dieser Punkte dazukäme, wäre es Pay-to-Win — unabhängig vom Preis.

### 8.5 Wenn eine Mitgliedschaft ausläuft

Das muss **vor** dem ersten verkauften Monat feststehen, sonst wird es der schlimmste Tag
des Servers. [entschieden]

| Was | Beim Auslaufen |
|---|---|
| Abzeichen, Rangfarbe, Namensanzeige | erlischt |
| Partikel, Haustier-Erscheinungen aus dem Bündel | ruhen — wieder da, sobald verlängert wird |
| Zusätzliche Heimatpunkte aus dem Bündel | werden **inaktiv, nicht gelöscht**; der Spieler kann sie nicht nutzen, verliert sie aber nicht |
| **Parzellen, Freistädte, Inseln, Läden, Geld, Gebautes** | **bleiben unangetastet** |
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
sind das 500 Goldmark — **100 €, und trotzdem mindestens 40 Tage**, weil der Tagesdeckel von
10.000 Mark je Tag greift. Wer stattdessen spielt, ist bei 2.000–5.000 Mark je Stunde nach
80–200 Stunden da. Der Geldweg ist also weder billig noch schnell, und genau so soll es
sein: Die Besitzleiter ist der Server, sie darf nicht an der Kasse abkürzbar sein.

Soll der Geldweg noch enger werden, ist der Tagesdeckel der Hebel — 5.000 Mark je Tag
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

**Kalibrierung** [empfohlen]: Ein Beruf trägt 2.000–5.000 Mark je Spielstunde. Dann ist die
zweite Parzelle nach 5–10 Stunden erreichbar, eine Meerinsel nach 15–35, eine Freistadt nach
80–200. Die Freistadt soll ein Jahresziel sein, kein Wochenendprojekt.

### 8.3 Was Echtgeld kaufen darf [entschieden]

**Kisten und Schlüssel nur aus Spielzeit, Ereignissen und Herausforderungen — nie aus dem
Shop.** Mojangs Nutzungsbedingungen führen „gambling" ausdrücklich als unzulässig; eine
Lootbox gegen Echtgeld fällt darunter.

Vorgesehen: Kosmetik, Haustiere, Partikel, Rangfarben, zusätzliche Heimatpunkte,
Namensanzeige, Förderer- und Stifter-Abzeichen. Und Goldmark selbst — mit dem Deckel aus
[8.1](#81-zwei-währungen-entschieden).

---

## 9. Ränge [entschieden]

```
Erspielbar     Reisender  →  Siedler  →  Bürger
Premium        Förderer      Stifter
```

Die Mitgliedschaften stehen **neben** der Leiter, nicht darüber. Sie geben Kosmetik,
Bequemlichkeit und Dank — keine Macht. Nach Lumias Regel [belegt]: kein Vorteil beim
Support, und Rechte gelten nur für einen selbst. Was sie enthalten dürfen und was beim
Auslaufen passiert, steht in [8.4](#84-warum-das-die-zusage-nicht-bricht) und
[8.5](#85-wenn-eine-mitgliedschaft-ausläuft).

| Rang | Wie | Was dazukommt [entschieden] |
|---|---|---|
| **Reisender** | beim ersten Beitritt | 1 Parzelle, 1 Heimatpunkt, Markthalle besuchen |
| **Siedler** | ~5 h Spielzeit + Rundgang abgeschlossen | 2 Parzellen, 3 Heimatpunkte, eigener Warp, Laden mieten, Zusammenlegen bis 2 Felder |
| **Bürger** | ~40 h + eigene Parzelle bebaut | 4 Parzellen, 5 Heimatpunkte, 3 Warps, Freistadt gründen, Zusammenlegen bis 4 Felder |
| **Förderer** | Mitgliedschaft, 10 €/Monat | Rangfarbe, Haustier, Partikel, +2 Heimatpunkte, Kornmark-Vorabzugang — alles auch einzeln für Goldmark erhältlich |
| **Stifter** | Mitgliedschaft, 20 €/Monat | wie Förderer, dazu Abzeichen, Namensanzeige, Platz auf der Stiftertafel in Altfurt |

Der Spielzeit-Aufstieg läuft automatisch — ohne Antrag, ohne Team.

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
- **Tagesdeckel** je Beruf, großzügig genug, dass ihn normales Spielen nicht erreicht.
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
| `/laden` | `mieten`, `verlaengern`, `kuendigen`, `abholfach` |
| `/markt` | `suchen <gegenstand>`, `anbieten <preis>`, `meine` |
| `/konto` | (Stand), `verlauf`, `zahlen <spieler> <betrag>`, `tauschen <betrag>` |
| `/zuhause` (`/home`) | `<name>`, `setzen <name>`, `entfernen <name>`, `liste` |
| `/warp` | `<name>`, `setzen <name>`, `entfernen <name>` |
| `/zurueck` (`/back`) | — |
| `/beruf` | `liste`, `waehlen <beruf>`, `verlassen <beruf>`, `stand` |
| `/menu` | öffnet das Hauptmenü |
| `/hilfe`, `/regeln` | — |

Eigene Heimatpunkte und eigene Warps gehören zur Rangleiter: Ihre Zahl steigt mit dem Rang,
zusätzliche sind über Goldmark erreichbar.

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
         Wechselkurs und Tagesdeckel — alles, was wirklich kaputtgehen kann
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
| Was | Preise, Kurse, Tagesdeckel, Rangrechte, Berufssätze, Mietdauern, Texte, Weltgrenzen | Wem gehört was, Kontostände, Mietverträge, Spielzeit, Mitgliedschaften |
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
| **Währung** | Kurse beide Richtungen, Tagesdeckel, Goldmark-Preis, Mengenbonus — mit Schranken und Verlauf, siehe [12a.6](#12a6-was-anpassbar-ist--und-was-nicht) |
| **Ränge** | Schwellen, was jeder Rang freischaltet, Mitgliedschaftsinhalte |
| **Berufe** | Sätze je Beruf, abnehmender Ertrag, Tagesdeckel, Spezialisierungen |
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
anpassbar** — Preise, Mieten, Gebühren, Kurse, Tagesdeckel, Goldmark-Preis, Mengenbonus,
Verdienstsätze, Rangschwellen, Mietdauern, Texte. Nichts davon steht im Quelltext.

Drei Klassen, weil sie unterschiedlich behandelt werden müssen:

| Klasse | Beispiele | Wirkung |
|---|---|---|
| **Frei anpassbar** | Preise für Neukäufe, Kurse, Tagesdeckel, Gebühren, Verdienstsätze, Texte, Rangschwellen | sofort bzw. beim nächsten Neuladen |
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
| 3 | Welten anlegen, Grenzen, Vorgenerierung, Altfurt als Rohbau (Gebäude aus [2.1](#21-die-gebäude-in-altfurt) als Platzhalter) | man kann durch alle Welten laufen — *Marktstadt steht, übrige Welten offen* |
| 4 | ~~**Weltgenerator für die Marktstadt** — Raster, Wege, Grenzen~~ **erledigt 31.10.2026** | ~~das Raster steht~~ — `Rasterplan` im Kern (8 Tests ohne Server), `MarktstadtGenerator` im Plugin; am Testserver geprüft: Parzellenmitte Gras, Mauer Steinziegel, Weg glatter Stein, Krone Halbstufe |
| 5 | Besitz-Kern: Parzelle nehmen, Schutz, Mitbauer — **fertig 31.10.2026** | zwei Spieler, einer baut, der andere nicht — *Code steht: `Besitzbuch` mit allen Regeln (Preisstaffel, Obergrenze, Mitbauer, Aufgeben ohne Rückzahlung), `Schutzwache` mit einer Regel für alle 14 Schutzereignisse, `/parzelle` und `/mark`. 89 Tests grün, Startprobe auf einem echten Paper-26.3-Server durch. Danach eine adversariale Prüfung mit 41 Agenten: 9 bestätigte Befunde, alle behoben — darunter fehlender Schutz gegen Schaden an Rüstungsständern und Rahmeninhalt, Kisten-Loren, ein Besitzschutz, der bei Datenbankausfall nach außen offen scheiterte, und Tests, die das Spielschema leerten. Durchgang mit zwei Spielern am 31.10. gelaufen: Der Schutz greift auf fremdem Grund und sagt es in der Aktionsleiste, Mitbauer schaltet frei und wieder zu, die zweite Parzelle wurde fuer 25.000 Mark bezahlt, und Parzelle -1/0 beweist die negative Seite der Rasterrechnung. Jeder Schritt in der Datenbank gegengeprueft* |
| 6 | Zusammenlegen und Auftrennen mit Gebühr | zwei Parzellen werden eine, der Weg verschwindet |
| 7 | Wirtschaft: Berufe, Bundesmarkt-Ankauf, Tagesbonus — *das Konto selbst gibt es schon: `Kasse` mit Startguthaben, Abbuchen und Gutschreiben, in MariaDB mit der Deckungsprüfung in der Abfrage. Es fehlen Verdienen, Buchungsprotokoll, Goldmark und Wechselkurs* | man kann Mark verdienen und ausgeben |
| 8 | Markthalle, Mietverträge, Abholfach, Freie Truhe | ein Laden läuft ab und wird sauber zurückgegeben |
| 9 | Freistädte: gründen, Grundstücke vergeben, Bürger, Kasse | ein Spieler verkauft einem anderen ein Grundstück |
| 10 | Hafenwerder mit eigener Angeltabelle | eine Insel lässt sich sichern, der Fang unterscheidet sich |
| 11 | Wolkenwerder mit Skyblock-Mechaniken | Generator, Insel-Stufe, Herausforderungen laufen |
| 12 | Ränge, Goldmark, Wechsel mit Tagesdeckel, Kosmetik | Reisender → Siedler steigt von selbst |
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
