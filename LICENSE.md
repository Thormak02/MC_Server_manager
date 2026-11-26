# Alle Rechte vorbehalten

Copyright (c) 2026 Friedrich Dietrich. Alle Rechte vorbehalten.

Dieses Verzeichnis ist **offen zu lesen und nicht offen zu benutzen.**

Der Quelltext liegt oeffentlich, damit man ihn **ansehen, nachlesen und pruefen** kann.
Er ist ausdruecklich **keine** freie oder offene Software: Es gibt keine MIT-, keine
GPL-, keine Creative-Commons-Lizenz und keine stillschweigende Erlaubnis. Wer hier eine
Lizenzdatei erwartet, die Nutzung erlaubt, findet mit Absicht keine.

## Was untersagt ist

Jede Verwendung durch Dritte ist untersagt, in jeglicher Form und ohne vorherige
schriftliche Erlaubnis. Das umfasst insbesondere:

- den Quelltext oder Teile davon **zu benutzen, zu uebersetzen, zu betreiben** oder in
  ein eigenes Vorhaben zu uebernehmen - auch verändert, auch in Teilen, auch nur eine
  einzelne Datei oder Methode,
- ihn **weiterzugeben, zu veroeffentlichen, zu spiegeln** oder anders zugaenglich zu
  machen,
- ihn als **Vorlage, Grundlage oder Trainingsmaterial** zu verwenden,
- die mitgelieferten Daten, Weltdateien, Texte, Einstellungsdateien und Baupläne zu
  uebernehmen.

Ansehen, Lesen und das Anlegen einer Kopie innerhalb von GitHub (Fork) sind von den
Nutzungsbedingungen der Plattform gedeckt und damit moeglich, solange dieses Verzeichnis
dort oeffentlich liegt. **Alles, was darueber hinausgeht, ist es nicht** - und eine
Plattformkopie ist keine Nutzungserlaubnis.

Wer etwas davon braucht, soll fragen. Eine Antwort kostet nichts.

## Was dieser Vorbehalt NICHT umfasst

Dieser Vorbehalt gilt nur fuer das, was in diesem Verzeichnis **selbst geschrieben**
wurde. Er beansprucht keine Rechte an fremder Arbeit, die hier mitliegt oder beim Bauen
geholt wird. Namentlich:

| Was | Wo | Unter welcher Lizenz |
|---|---|---|
| Gradle Wrapper | `freimarken/gradle/wrapper/gradle-wrapper.jar` und `gradle-wrapper.properties` | Apache License 2.0 (Gradle Inc.) |
| Schriftarten IBM Plex Mono und Manrope | `app/web/static/fonts/` | SIL Open Font License - der Text liegt dort als `LICENSE.txt` |
| Paper-, Bukkit- und Adventure-Schnittstellen | nur zum Uebersetzen eingebunden, nicht mitgeliefert | GPL-3.0 beziehungsweise MIT, bei ihren Urhebern |
| Python-Pakete aus `requirements.txt` | nicht mitgeliefert, beim Einrichten geholt | je Paket eigene Lizenz |
| MariaDB-Treiber und weitere Java-Abhaengigkeiten | nicht mitgeliefert, beim Bauen geholt | je Abhaengigkeit eigene Lizenz, der Treiber LGPL-2.1 |
| Minecraft selbst, seine Weltformate und Blocknamen | Minecraft wird nicht mitgeliefert | Mojang/Microsoft, nach deren Bedingungen |

Fuer jede dieser Zeilen gilt die dort genannte Lizenz und nicht dieser Vorbehalt.

## Zur mitgelieferten `MCSMLobby.jar`

In `app/assets/lobby_plugin/` liegt neben dem Quelltext (`src/net/mcsm/lobby/`) auch
eine gebaute `MCSMLobby.jar`. Das ist Absicht - der Manager legt sie auf einen
Lobby-Server, und ein Bauschritt dafuer waere ein Werkzeug mehr, das niemand hat.

Geprueft am 26.11.2026: Die Jar enthaelt **dreizehn Eintraege, alle aus
`net/mcsm/lobby/`**, dazu `plugin.yml` und `config.yml`. Es liegt **keine einzige fremde
Klasse** darin, insbesondere nichts von PacketEvents - jenes steht als `softdepend` in
der `plugin.yml` und wird zur Laufzeit vom Server geladen, nicht hier mitgegeben. Mit
dieser Jar wird also kein fremder Code weitergegeben.

## Wenn diese Datei und die Wirklichkeit auseinandergehen

Dann gilt die Wirklichkeit, und diese Datei ist zu berichtigen. Kommt eine fremde Datei
ins Verzeichnis, gehoert sie in die Tabelle oben - und zwar, bevor sie eingecheckt wird.

---

# All rights reserved

Copyright (c) 2026 Friedrich Dietrich. All rights reserved.

**This repository is open to read, not open to use.**

The source is public so that it can be **inspected, read and reviewed**. It is
explicitly **not** free or open-source software: there is no MIT, GPL or Creative
Commons licence here, and no implied permission. The absence of a permissive licence
file is deliberate.

**Any use by third parties is prohibited in any form** without prior written
permission. This includes using, compiling, running, or incorporating the code or any
part of it into another project - modified or not, in whole or in part, down to a single
file or method; redistributing, publishing or mirroring it; using it as a template,
basis or training material; and taking over the bundled data, world files, texts,
configuration files and plans.

Viewing, reading and creating a copy within GitHub (a fork) are covered by the
platform's terms of service for as long as this repository is public there. **Anything
beyond that is not** - and a platform fork is not a licence to use.

If you need any of it, just ask. An answer costs nothing.

**This reservation covers only what was written here.** It claims no rights over
third-party work that is bundled or fetched at build time - see the table in the German
part above, which names each item and the licence that governs it instead.
