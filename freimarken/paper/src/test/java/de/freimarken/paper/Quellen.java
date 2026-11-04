package de.freimarken.paper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Die Quelldateien dieses Moduls - fuer die Proben, die den Quelltext selbst lesen.
 *
 * <p>Drei Proben tun das, und alle drei aus demselben Grund: Sie bewachen eine Zusage, die
 * kein Typ traegt - "Farbe nur noch hier", "gesendet nur noch hier", "kein geteiltes
 * Zahlformat". Eine solche Probe ist nur so stark, wie sie wirklich Dateien findet; genau
 * deshalb gibt es daneben {@code GrenzeTest.quellenWurdenGefunden}, die rot wird, wenn
 * diese Suche leer laeuft.
 *
 * <p>Das Arbeitsverzeichnis einer Gradle-Probe ist das Projektverzeichnis, also
 * {@code .../freimarken/paper}. Gesucht wird trotzdem aufwaerts: Wer die Proben aus dem
 * Wurzelprojekt startet, soll keine leere, gruene Suite bekommen.
 */
final class Quellen {

    private static final String UNTERPFAD = "src/main/java/de/freimarken/paper";

    private Quellen() {
    }

    /** Der Ordner mit den Quelldateien, oder {@code null}, wenn er nicht gefunden wurde. */
    static Path ordner() {
        Path hier = Path.of("").toAbsolutePath();
        for (Path kandidat = hier; kandidat != null; kandidat = kandidat.getParent()) {
            Path versuch = kandidat.resolve(UNTERPFAD);
            if (Files.isDirectory(versuch)) {
                return versuch;
            }
            Path ueberPaper = kandidat.resolve("paper").resolve(UNTERPFAD);
            if (Files.isDirectory(ueberPaper)) {
                return ueberPaper;
            }
        }
        return null;
    }

    /** Alle {@code .java}-Dateien des Moduls, nach Namen geordnet. */
    static List<Path> alle() {
        Path ordner = ordner();
        if (ordner == null) {
            return List.of();
        }
        try (Stream<Path> lauf = Files.list(ordner)) {
            List<Path> dateien = new ArrayList<>(lauf
                    .filter(pfad -> pfad.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(pfad -> pfad.getFileName().toString()))
                    .toList());
            return List.copyOf(dateien);
        } catch (IOException nichtLesbar) {
            throw new UncheckedIOException(nichtLesbar);
        }
    }

    /** Der Text einer Quelldatei, mit allem - auch den Kommentaren. */
    static String mitKommentaren(Path datei) {
        try {
            return Files.readString(datei);
        } catch (IOException nichtLesbar) {
            throw new UncheckedIOException(nichtLesbar);
        }
    }

    /** Der Text einer Quelldatei, wie die Tore ihn lesen: ohne Kommentare. */
    static String text(Path datei) {
        return ohneKommentare(mitKommentaren(datei));
    }

    /**
     * Derselbe Text OHNE Kommentare - das, was die Tore lesen.
     *
     * <p>Gemessener Grund: {@code GrenzeTest.jederSchluesselWirdGesendet} nahm eine
     * Nennung im Javadoc als Benutzung. Ein Schluessel konnte damit vollstaendig aus dem
     * Programm verschwinden, solange sein Name irgendwo in einem Kommentar stand - der
     * Betreiber aendert dann seine Zeile in der Vorlage, und im Spiel aendert sich nichts.
     * Dasselbe galt fuer die Zaehlung der Sendestellen und fuer die Suche nach dem harten
     * Geldwort in {@code VorlageTest} 10e.
     *
     * <p>Darum ein Abtaster und kein Regex: Ein {@code //} in einer Zeichenkette ist kein
     * Kommentaranfang, und ein Regex ueber Blockkommentare frisst alles zwischen zwei
     * Sternenpaaren in verschiedenen Zeichenketten. Was ein Kommentar war, wird zu
     * nichts - nur seine Zeilenumbrueche bleiben stehen, damit zwei Bezeichner nicht
     * aneinanderkleben und eine Zeilennummer weiter stimmt.
     */
    static String ohneKommentare(String text) {
        StringBuilder rein = new StringBuilder(text.length());
        int stelle = 0;
        while (stelle < text.length()) {
            char zeichen = text.charAt(stelle);
            if (zeichen == '"' || zeichen == '\'') {
                stelle = uebernimmLiteral(text, stelle, rein);
            } else if (zeichen == '/' && naechstes(text, stelle) == '/') {
                while (stelle < text.length() && text.charAt(stelle) != '\n') {
                    stelle++;
                }
            } else if (zeichen == '/' && naechstes(text, stelle) == '*') {
                stelle = ueberspringeBlock(text, stelle + 2, rein);
            } else {
                rein.append(zeichen);
                stelle++;
            }
        }
        return rein.toString();
    }

    /**
     * Uebernimmt eine Zeichenkette oder ein Zeichenliteral WOERTLICH.
     *
     * <p>Mit Textblock ({@code """}): Dessen Inhalt darf keine Kommentarsuche ausloesen,
     * und ein einzelnes Anfuehrungszeichen darin ist kein Ende. Die Tafeln von
     * {@code SendewegTest} sind Textbloecke, und in ihnen stehen Schraegstriche.
     *
     * @return die Stelle hinter dem Literal
     */
    private static int uebernimmLiteral(String text, int anfang, StringBuilder hinein) {
        char rand = text.charAt(anfang);
        String ende = rand == '"' && text.startsWith("\"\"\"", anfang)
                ? "\"\"\"" : String.valueOf(rand);
        int stelle = anfang + ende.length();
        hinein.append(text, anfang, stelle);
        while (stelle < text.length()) {
            char zeichen = text.charAt(stelle);
            if (zeichen == '\\') {
                // Ein maskiertes Anfuehrungszeichen beendet nichts.
                hinein.append(zeichen);
                stelle++;
                if (stelle < text.length()) {
                    hinein.append(text.charAt(stelle));
                    stelle++;
                }
                continue;
            }
            if (text.startsWith(ende, stelle)) {
                hinein.append(ende);
                return stelle + ende.length();
            }
            hinein.append(zeichen);
            stelle++;
        }
        return stelle;
    }

    /**
     * Ueberspringt einen Blockkommentar und laesst ein Leerzeichen und seine
     * Zeilenumbrueche stehen.
     *
     * <p>Das Leerzeichen ist nicht Zierde: {@code eins/* dazwischen *}{@code /zwei} ohne
     * es ergibt den einen Namen {@code einszwei}, und die Suche nach {@code zwei} findet
     * ihn dann nicht - gemessen von {@code QuellenTest} 35f.
     *
     * @return die Stelle hinter dem Kommentar
     */
    private static int ueberspringeBlock(String text, int anfang, StringBuilder hinein) {
        hinein.append(' ');
        for (int stelle = anfang; stelle < text.length(); stelle++) {
            if (text.startsWith("*/", stelle)) {
                return stelle + 2;
            }
            if (text.charAt(stelle) == '\n') {
                hinein.append('\n');
            }
        }
        return text.length();
    }

    private static char naechstes(String text, int stelle) {
        return stelle + 1 < text.length() ? text.charAt(stelle + 1) : '\0';
    }

    /** Wie oft eine Zeichenfolge in einer Datei steht. */
    static int zaehle(String text, String folge) {
        int anzahl = 0;
        for (int stelle = text.indexOf(folge); stelle >= 0;
                stelle = text.indexOf(folge, stelle + folge.length())) {
            anzahl++;
        }
        return anzahl;
    }
}
