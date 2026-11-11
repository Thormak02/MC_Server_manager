package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Zusagen ueber das Schema, die der QUELLTEXT hergibt - nicht das Verhalten.
 *
 * <p>Dieselbe Bauart wie {@code GrenzeTest} im Spielmodul und aus demselben Grund: Ein
 * Schemaschritt hat ZWEI Pflichtstellen, und die zweite wird vergessen. Die erste ist die
 * Tabelle in {@code SCHRITTE}; sie faellt sofort auf, denn ohne sie scheitert jede
 * Abfrage. Die zweite ist eine {@code DELETE}-Zeile in {@link Datenbank#leereAlles()},
 * und ihr Fehlen faellt NICHT auf: Jede Probe laeuft, nur wandern Zeilen von einer Probe
 * in die naechste. Das Ergebnis ist ein Fehlschlag, der von der REIHENFOLGE der Proben
 * abhaengt - die unangenehmste Sorte, weil sie einzeln gruen sind.
 *
 * <p>Geprueft wird am Quelltext und nicht an der laufenden Datenbank: Diese Probe soll
 * auch ohne Zugangsdaten etwas sagen. Die Fassung mit Datenbank gaebe dieselbe Antwort
 * erst, nachdem jemand sie mit Zeilen gefuellt hat, und dann waere sie selbst die Probe,
 * die aufraeumen muss.
 */
@DisplayName("Schema")
class SchemaTest {

    private static final String UNTERPFAD = "src/main/java/de/freimarken/daten";

    /**
     * Die Tabelle, die mit Absicht NICHT geleert wird.
     *
     * <p>{@code schema_stand} haelt, welche Schritte erledigt sind. Wer sie leert, laesst
     * {@code migriere()} beim naechsten Lauf jeden Schritt erneut versuchen - und die
     * ersten drei tragen kein {@code IF NOT EXISTS}, also waere die Folge ein harter
     * Fehler mitten in einer Probenreihe.
     */
    private static final Set<String> NICHT_LEEREN = Set.of("schema_stand");

    /**
     * Je Schemaschritt eine Pruefsumme - MASCHINELL aus einem absichtlich roten Lauf
     * und nicht abgeschrieben.
     *
     * <p>Angehaengt wird unten, nie dazwischen, genau wie bei den Schritten selbst.
     * Warum es diese Liste gibt, steht in {@link #schritteSindUnveraenderlich}.
     */
    private static final int[] PRUEFSUMMEN = {
        -1492321989, // Schritt 1
        -1843925192, // Schritt 2
        -2030805759, // Schritt 3
        -1819239078, // Schritt 4
        -306895694, // Schritt 5
        1152151801, // Schritt 6
        -1670142385, // Schritt 7
        -1763604121, // Schritt 8
        470625965, // Schritt 9
        1364407328, // Schritt 10
        1271430570, // Schritt 11
        -670762847, // Schritt 12
        -673074704, // Schritt 13
        741949855, // Schritt 14
        1795794151, // Schritt 15
        -1684371841, // Schritt 16
        -1321860805, // Schritt 17
        175182761, // Schritt 18
        -1457172908, // Schritt 19
    };

    private static String datenbanktext() {
        Path hier = Path.of("").toAbsolutePath();
        for (Path kandidat = hier; kandidat != null; kandidat = kandidat.getParent()) {
            for (Path versuch : new Path[] {
                    kandidat.resolve(UNTERPFAD),
                    kandidat.resolve("daten").resolve(UNTERPFAD)}) {
                Path datei = versuch.resolve("Datenbank.java");
                if (Files.isRegularFile(datei)) {
                    try {
                        return Files.readString(datei);
                    } catch (IOException fehler) {
                        throw new AssertionError("Datenbank.java nicht lesbar: " + datei,
                                fehler);
                    }
                }
            }
        }
        throw new AssertionError("Datenbank.java nicht gefunden - gesucht ab " + hier);
    }

    @Test
    @DisplayName("Jede Tabelle des Schemas wird von leereAlles() auch GELEERT")
    void jedeTabelleWirdGeleert() {
        // DIE ZWEITE PFLICHTSTELLE EINES SCHEMASCHRITTES, und sie ist die, die vergessen
        // wird. Ohne sie wandern Zeilen von einer Probe in die naechste; bei einer
        // Tabelle mit eindeutigem Schluessel ist die Folge nicht nur ein falscher Stand,
        // sondern ein harter Wurf beim naechsten Anlegen. Beim Blockbuch ist sie
        // besonders heimtueckisch: Seine Proben zaehlen, was eine Abfrage MIT GRENZE
        // zurueckgibt, und fremde Zeilen am selben Ort schieben die eigenen aus der
        // Antwort heraus.
        //
        // DIE LISTE KOMMT AUS DEM QUELLTEXT: Eine von Hand gepflegte bliebe bei der
        // naechsten Tabelle stehen, und dann ist genau die neue ungeleert.
        String text = datenbanktext();

        var angelegt = new TreeSet<String>();
        Matcher sucher = Pattern
                .compile("CREATE TABLE (?:IF NOT EXISTS )?([a-z_]+) \\(")
                .matcher(text);
        while (sucher.find()) {
            angelegt.add(sucher.group(1));
        }
        assertFalse(angelegt.isEmpty(),
                "keine einzige CREATE TABLE gefunden - dann prueft diese Probe nichts");
        assertTrue(angelegt.size() > 5,
                "nur " + angelegt.size() + " Tabellen gefunden (" + angelegt + ") - das"
                        + " Muster passt offenbar nicht mehr auf den Quelltext, und eine"
                        + " Probe, die nur die Haelfte sieht, ist schlimmer als keine");

        var geleert = new TreeSet<String>();
        Matcher loescher = Pattern.compile("DELETE FROM ([a-z_]+)").matcher(text);
        while (loescher.find()) {
            geleert.add(loescher.group(1));
        }

        var vergessen = new TreeSet<>(angelegt);
        vergessen.removeAll(geleert);
        vergessen.removeAll(NICHT_LEEREN);
        assertEquals(Set.of(), vergessen,
                "diese Tabellen legt das Schema an, aber leereAlles() leert sie nicht."
                        + " Keine Probe wird davon rot - Zeilen wandern nur von einer"
                        + " Probe in die naechste, und der Fehlschlag haengt dann an"
                        + " ihrer REIHENFOLGE: " + vergessen);
    }

    @Test
    @DisplayName("leereAlles() leert keine Tabelle, die es nicht gibt")
    void keinLeerenInsLeere() {
        // Die andere Richtung, und sie faengt den Vertipper: Ein DELETE FROM blockbuchh
        // laesst jede Probe scheitern, die leereAlles() ruft - aber nur, wenn eine
        // Datenbank da ist. Ohne Zugangsdaten sind jene Proben uebersprungen, und dann
        // bliebe der Fehler bis zum naechsten Lauf auf einer echten Datenbank liegen.
        String text = datenbanktext();

        var angelegt = new TreeSet<String>();
        Matcher sucher = Pattern
                .compile("CREATE TABLE (?:IF NOT EXISTS )?([a-z_]+) \\(")
                .matcher(text);
        while (sucher.find()) {
            angelegt.add(sucher.group(1));
        }

        var geleert = new TreeSet<String>();
        Matcher loescher = Pattern.compile("DELETE FROM ([a-z_]+)").matcher(text);
        while (loescher.find()) {
            geleert.add(loescher.group(1));
        }
        assertFalse(geleert.isEmpty(),
                "kein einziges DELETE FROM gefunden - dann prueft diese Probe nichts");

        var unbekannt = new TreeSet<>(geleert);
        unbekannt.removeAll(angelegt);
        assertEquals(Set.of(), unbekannt,
                "leereAlles() leert Tabellen, die das Schema nie anlegt - vermutlich ein"
                        + " Vertipper. Sichtbar wird er nur auf einer echten Datenbank,"
                        + " und dort reisst er jede Probe mit: " + unbekannt);
    }

    @Test
    @DisplayName("Ein gelaufener Schemaschritt ist UNVERAENDERLICH")
    void schritteSindUnveraenderlich() {
        // DER FUND VOM 10.11.2026, und er ist eine stille Falle: migriere() fuehrt nur
        // Schritte ab dem gespeicherten Stand aus. Wer einen BESTEHENDEN Schritt
        // aendert, aendert an einer laufenden Datenbank nichts - die Tabelle steht
        // dort, wie sie am ersten Tag angelegt wurde. Auf einer frischen Datenbank
        // wirkt dieselbe Aenderung sofort, und die Probendatenbank ist beim ersten
        // Lauf frisch. Die Folge: Die Proben sagen etwas ueber ein Schema, das auf
        // keinem laufenden Server steht.
        //
        // Gemessen: Eine Einspritzung, die ON DELETE CASCADE aus dem Schritt der
        // Freigabetabelle entfernte, lief vollstaendig gruen durch - 2615 Proben, keine
        // rot. Nicht weil die Regel ungeprueft war, sondern weil die Aenderung die
        // Datenbank nie erreichte.
        //
        // Darum eine Pruefsumme je Schritt. Wer einen Schritt aendert, wird hier rot
        // und liest, was stattdessen zu tun ist: einen NEUEN Schritt anhaengen. Wer
        // einen anhaengt, fuegt eine Zeile zu - und zwar genau EINE, unten.
        //
        // Gehasht wird mit zusammengezogenem Leerraum: Eine Umformatierung aendert das
        // SQL nicht und soll darum nicht rot werden. Jedes geaenderte WORT wird es.
        // Erst ALLE fehlenden sammeln und dann einmal klagen: Wer zwei Schritte
        // anhaengt, soll beide Zeilen bekommen und nicht zweimal bauen muessen.
        if (Datenbank.SCHRITTE.size() > PRUEFSUMMEN.length) {
            StringBuilder fehlende = new StringBuilder();
            for (int nummer = PRUEFSUMMEN.length + 1;
                    nummer <= Datenbank.SCHRITTE.size(); nummer++) {
                fehlende.append(System.lineSeparator())
                        .append("        ").append(summe(nummer))
                        .append(", // Schritt ").append(nummer);
            }
            throw new AssertionError("Fuer "
                    + (Datenbank.SCHRITTE.size() - PRUEFSUMMEN.length)
                    + " Schemaschritt(e) fehlt die Pruefsumme. Diese Zeilen gehoeren"
                    + " unten in SchemaTest.PRUEFSUMMEN:" + fehlende);
        }
        for (int nummer = 1; nummer <= Datenbank.SCHRITTE.size(); nummer++) {
            assertEquals(PRUEFSUMMEN[nummer - 1], summe(nummer),
                    "Schemaschritt " + nummer + " ist GEAENDERT worden. Das wirkt auf"
                            + " keiner laufenden Datenbank: migriere() fuehrt nur"
                            + " Schritte ab dem gespeicherten Stand aus, und dieser ist"
                            + " dort erledigt. Wer eine Spalte, einen Schluessel oder"
                            + " eine Regel braucht, haengt einen NEUEN Schritt an"
                            + " (ALTER TABLE ... IF NOT EXISTS). Ist die Aenderung"
                            + " trotzdem gewollt - etwa ein Schritt, der noch auf keinem"
                            + " Server lief -, dann gehoert die neue Zahl hierher.");
        }
        assertEquals(Datenbank.SCHRITTE.size(), PRUEFSUMMEN.length,
                "mehr Pruefsummen als Schritte: Ein Schritt ist ENTFERNT worden, und"
                        + " damit verschieben sich alle Nummern danach. Eine bestehende"
                        + " Datenbank haelt die alten Nummern fuer erledigt und"
                        + " ueberspringt dann die falschen Schritte.");
    }

    /** Der Schritttext mit zusammengezogenem Leerraum - so, wie gehasht wird. */
    private static int summe(int nummer) {
        return Datenbank.SCHRITTE.get(nummer - 1)
                .replaceAll("\\s+", " ").trim().hashCode();
    }

    @Test
    @DisplayName("Jeder Schemaschritt ab dem vierten traegt IF NOT EXISTS")
    void neueSchritteSindWiederholbar() {
        // Ein Schritt muss einen zweiten Lauf ueberleben: Zwei Server auf derselben
        // Datenbank, ein abgebrochener Start, eine Probenreihe neben dem Betrieb - dann
        // laeuft migriere() ein zweites Mal. Die ersten Schritte stammen aus der Zeit
        // davor und bleiben, wie sie sind; ab dem vierten ist es Pflicht.
        //
        // Gezaehlt und nicht benannt: Die Zahl der alten Schritte ist fest, die der
        // neuen waechst.
        String text = datenbanktext();
        int ohne = 0;
        Matcher sucher = Pattern.compile("CREATE TABLE ([a-z_]+) \\(").matcher(text);
        while (sucher.find()) {
            ohne++;
        }
        assertEquals(3, ohne,
                "genau drei Schemaschritte duerfen ohne IF NOT EXISTS stehen - die aus"
                        + " der Zeit vor dieser Regel. Ein neuer ohne es scheitert beim"
                        + " zweiten Lauf von migriere(), und das trifft zwei Server auf"
                        + " derselben Datenbank oder einen abgebrochenen Start.");
    }
}
