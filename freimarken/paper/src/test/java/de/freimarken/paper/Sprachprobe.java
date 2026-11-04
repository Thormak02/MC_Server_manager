package de.freimarken.paper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Das Werkzeug der sechs Sprachproben - damit keine von ihnen es doppelt baut.
 *
 * <p>Zwei Dinge stehen hier und nirgends sonst: der Weg zum ausgelieferten Buch (jede
 * Probe, die das BILD prueft, muss durch dieselbe Ressource gehen wie der Server) und der
 * Vergleichsmassstab. Verglichen wird ueber die ALT-Serialisierung und nicht ueber
 * {@code Component.equals}: Die Baumform unterscheidet sich (ein Elternteil mit Kindern
 * gegen eine Kette von {@code append}), und nur das Bild zaehlt.
 */
final class Sprachprobe {

    private Sprachprobe() {
    }

    /** Das ausgelieferte Buch - aus derselben Ressource, die der Server liest. */
    static Sprachbuch ausDemJar() {
        return Sprachliste.ausDemJar();
    }

    static Sprachstand standAusDemJar() {
        return new Sprachstand(ausDemJar());
    }

    static Sprache spracheAusDemJar() {
        return new Sprache(standAusDemJar());
    }

    /** Der rohe Text der ausgelieferten Vorlage. */
    static String vorlagentext() {
        return Sprachliste.ressource(Sprachliste.VORLAGE);
    }

    /** Der rohe Text der ausgelieferten, absichtlich leeren eigenen Datei. */
    static String eigenertext() {
        return Sprachliste.ressource(Sprachliste.DATEI);
    }

    /**
     * Das BILD eines Components - mit {@code &} statt des Paragrafenzeichens.
     *
     * <p>Das Paragrafenzeichen waere in dieser Quelldatei ein Zeichen, das die Hausregel
     * nicht erlaubt, und in einer Fehlermeldung waere es unlesbar. Ersetzt wird es nach
     * der Serialisierung, also aendert es am Vergleich nichts.
     */
    static String bild(Component gebaut) {
        return LegacyComponentSerializer.legacySection().serialize(gebaut)
                .replace((char) 0xa7, '&');
    }

    /** Das Bild mehrerer Zeilen - eine Zeile je Sendung, mit Umbruch dazwischen. */
    static String bild(List<Component> zeilen) {
        List<String> bilder = new ArrayList<>();
        zeilen.forEach(zeile -> bilder.add(bild(zeile)));
        return String.join("\n", bilder);
    }

    static String nurText(Component gebaut) {
        return PlainTextComponentSerializer.plainText().serialize(gebaut);
    }

    /** Ein Satz fuer eine Probe - fuer Faelle, deren Schluessel erst ein spaeterer Schritt anlegt. */
    record Probesatz(String pfad, Satz.Art art, Set<String> platzhalter,
                     boolean darfLeerSein) implements Satz {

        static Probesatz von(String pfad, Satz.Art art, String... platzhalter) {
            return new Probesatz(pfad, art, Set.of(platzhalter), false);
        }
    }

    /**
     * Ein Buch aus Pfad-Wert-Paaren - die Vorlage fuer eine Probe mit eigenen Schluesseln.
     *
     * @param paare Pfad, Wortlaut, Pfad, Wortlaut, ...
     */
    static Sprachbuch buch(List<Satz> schluessel, String... paare) {
        Map<String, String> saetze = new LinkedHashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            saetze.put(paare[i], paare[i + 1]);
        }
        Map<String, DateTimeFormatter> muster = new LinkedHashMap<>();
        for (Satz satz : schluessel) {
            if (satz.art() == Satz.Art.MUSTER && saetze.containsKey(satz.pfad())) {
                muster.put(satz.pfad(), DateTimeFormatter.ofPattern(
                        saetze.get(satz.pfad()), Locale.GERMANY));
            }
        }
        return new Sprachbuch(saetze, Set.of(), muster);
    }

    /**
     * Eine {@code sprache.yml} mit genau diesen Bytes im Probeordner.
     *
     * <p>Bytes und nicht Text: Die Kodierungsprobe muss ein Byte schreiben koennen, das
     * gar kein gueltiges UTF-8 ist - mit einer Zeichenkette ginge das nicht, und ein
     * Umlaut in dieser Quelldatei waere ausserdem gegen die Hausregel.
     */
    static File mitBytes(Path ordner, byte[] rohe) throws IOException {
        Files.write(ordner.resolve(Sprachliste.DATEI), rohe);
        return ordner.toFile();
    }

    static File mitInhalt(Path ordner, String text) throws IOException {
        return mitBytes(ordner, text.getBytes(StandardCharsets.UTF_8));
    }
}
