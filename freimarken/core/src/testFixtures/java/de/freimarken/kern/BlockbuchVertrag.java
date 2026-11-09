package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Blockbuchablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code AbholVertrag}: Die eine sortiert eine Liste mit einem
 * Vergleicher, die andere schickt ein {@code ORDER BY} - und genau an so einer Stelle
 * laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Das wichtigste Versprechen dieses Vertrags ist die RICHTUNG der Grenze</b>
 * ({@link #grenzeNimmtDieNeuesten}). Eine Lesemethode, die aufsteigend sortiert und dann
 * abschneidet, liefert die zwanzig AELTESTEN Zeilen - und verschweigt genau das, was
 * gerade passiert ist. Der Fehler ist nicht zu sehen: Die Antwort ist die richtige Laenge,
 * sie ist sortiert, sie enthaelt echte Zeilen. Nur die Frage, die gestellt wurde, war eine
 * andere.
 *
 * <p><b>Alle Zeitpunkte sind millisekundengenau</b>, ohne Nanosekunden. Die Spalte in
 * MariaDB ist {@code DATETIME(3)}; ein Zeitpunkt mit Nanosekunden kaeme gerundet zurueck,
 * und dann waere jede Probe dieses Vertrags auf der einen Fassung rot und auf der anderen
 * gruen - ein Unterschied, der nichts ueber die Regel sagt.
 */
public abstract class BlockbuchVertrag {

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    protected static final Instant WANN = Instant.parse("2026-11-09T14:30:00Z");

    /** Ein Ort, an dem etwas geschieht. */
    protected static final Blockort ORT =
            new Blockort("freimarken:altfurt", 62, -60, 8);

    /** Eine frische, leere Ablage. */
    protected abstract Blockbuchablage ablage();

    /** Eine Zeile mit unterscheidbaren Werten. */
    protected static Blockzeile tat(Instant wann, UUID wer, Blockort wo, Blocktat was,
                                    boolean gelungen, String block) {
        return Blockzeile.neu(wann, wer, wo, was, gelungen, block);
    }

    @Test
    @DisplayName("Ein leeres Buch ist eine leere Liste und kein Fehler")
    void leerIstKeinFehler() {
        Blockbuchablage ablage = ablage();

        assertTrue(ablage.amOrt(ORT, 20).isEmpty());
        assertTrue(ablage.vonSpieler(UUID.randomUUID(), 20).isEmpty());
    }

    @Test
    @DisplayName("Was aufgeschrieben wurde, kommt zurueck - alle sechs Werte")
    void hinUndZurueck() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertEquals(1, ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "oak_log"))));

        List<Blockzeile> gelesen = ablage.amOrt(ORT, 20);
        assertEquals(1, gelesen.size());
        Blockzeile zeile = gelesen.get(0);
        assertTrue(zeile.id() >= 1L,
                "die Nummer entsteht in der Ablage: " + zeile.id());
        assertEquals(WANN, zeile.zeitpunkt());
        assertEquals(anna, zeile.spieler());
        assertEquals(ORT, zeile.ort());
        assertEquals(Blocktat.ABBAUEN, zeile.tat());
        assertTrue(zeile.gelungen());
        assertEquals("oak_log", zeile.block());
    }

    @Test
    @DisplayName("Ein ABGEWIESENER Versuch kommt als abgewiesen zurueck")
    void abgewiesenBleibtAbgewiesen() {
        // Der Fund der Moderation ist der Versuch, bei dem NICHTS passiert ist. Geht das
        // Merkmal beim Schreiben verloren - etwa weil eine Fassung die Spalte nicht
        // setzt und die Datenbank 0 einsetzt -, dann sieht jede Zeile wie ein Erfolg aus
        // oder wie ein Fehlschlag, und beides beantwortet die Frage falsch.
        Blockbuchablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        ablage.schreibe(List.of(
                tat(WANN, bert, ORT, Blocktat.SETZEN, false, "stone")));

        List<Blockzeile> gelesen = ablage.amOrt(ORT, 20);
        assertEquals(1, gelesen.size());
        assertFalse(gelesen.get(0).gelungen(),
                "die Zeile wurde als ABGEWIESEN geschrieben");
    }

    @Test
    @DisplayName("Die NEUESTE Zeile steht vorn - zweimal hintereinander gleich")
    void neuesteZuerst() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // In der Reihenfolge geschrieben, in der es geschah - die Antwort dreht sie um.
        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.SETZEN, true, "zuerst"),
                tat(WANN.plusSeconds(60), anna, ORT, Blocktat.ABBAUEN, true, "dann"),
                tat(WANN.plusSeconds(120), anna, ORT, Blocktat.SETZEN, true, "zuletzt")));

        assertEquals(List.of("zuletzt", "dann", "zuerst"), bloecke(ablage.amOrt(ORT, 20)));
        assertEquals(List.of("zuletzt", "dann", "zuerst"), bloecke(ablage.amOrt(ORT, 20)),
                "zweimal dieselbe Frage, zweimal dieselbe Reihenfolge");
        assertEquals(List.of("zuletzt", "dann", "zuerst"),
                bloecke(ablage.vonSpieler(anna, 20)),
                "beide Lesewege sortieren gleich - sonst zeigt derselbe Vorfall je nach"
                        + " Frage eine andere Reihenfolge");
    }

    @Test
    @DisplayName("Die Grenze nimmt die NEUESTEN Zeilen, nicht die aeltesten")
    void grenzeNimmtDieNeuesten() {
        // DAS WICHTIGSTE VERSPRECHEN DIESES VERTRAGS. Wer aufsteigend sortiert und dann
        // abschneidet, bekommt eine Antwort der richtigen Laenge, richtig sortiert, aus
        // echten Zeilen - und verschweigt genau das, was gerade passiert ist. Ein
        // Moderator vor einem frischen Loch bekaeme die Bauarbeiten vom Vormonat.
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.SETZEN, true, "alt"),
                tat(WANN.plusSeconds(60), anna, ORT, Blocktat.ABBAUEN, true, "mittel"),
                tat(WANN.plusSeconds(120), anna, ORT, Blocktat.SETZEN, true, "neu")));

        assertEquals(List.of("neu", "mittel"), bloecke(ablage.amOrt(ORT, 2)),
                "zwei von drei Zeilen - und zwar die zwei NEUESTEN");
        assertEquals(List.of("neu"), bloecke(ablage.amOrt(ORT, 1)));
        assertEquals(List.of("neu", "mittel"), bloecke(ablage.vonSpieler(anna, 2)),
                "dieselbe Richtung auf dem anderen Leseweg");
    }

    @Test
    @DisplayName("Gleichzeitige Zeilen stehen stabil - die spaeter geschriebene vorn")
    void gleichzeitigBleibtStabil() {
        // Die Spalte ist DATETIME(3), also millisekundengenau, und zwanzig Bloecke in
        // einem Tick sind ein normaler Abbau mit Effizienz V. Ohne einen Tiebreaker
        // waere die Reihenfolge dieser drei Zeilen beliebig - und eine Abfrage mit
        // Grenze schnitte zweimal verschieden ab.
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "erster"),
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "zweiter"),
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "dritter")));

        assertEquals(List.of("dritter", "zweiter", "erster"),
                bloecke(ablage.amOrt(ORT, 20)),
                "bei gleicher Millisekunde entscheidet die Nummer, und die spaeter"
                        + " geschriebene Zeile ist die neuere");
        assertEquals(List.of("dritter", "zweiter"), bloecke(ablage.amOrt(ORT, 2)),
                "die Grenze schneidet damit stabil ab");
    }

    @Test
    @DisplayName("amOrt zeigt NUR diesen Block - auch eine Ebene hoeher ist ein anderer")
    void ortIstGenauDieserBlock() {
        // Nicht die Saeule und nicht der Chunk: Eine Parzelle ist eine Saeule, eine
        // Blockzeile haengt an genau einem Block. Wer oben eine Tuer setzt, hat nicht den
        // Boden darunter abgebaut, und die Antwort darf die beiden nicht vermischen.
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        Blockort darueber = new Blockort(ORT.welt(), ORT.x(), ORT.y() + 1, ORT.z());
        Blockort danebenWelt = new Blockort("freimarken:marktstadt", ORT.x(), ORT.y(),
                ORT.z());

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "hier"),
                tat(WANN, anna, darueber, Blocktat.SETZEN, true, "darueber"),
                tat(WANN, anna, danebenWelt, Blocktat.SETZEN, true, "andere_welt")));

        assertEquals(List.of("hier"), bloecke(ablage.amOrt(ORT, 20)));
        assertEquals(List.of("darueber"), bloecke(ablage.amOrt(darueber, 20)));
        assertEquals(List.of("andere_welt"), bloecke(ablage.amOrt(danebenWelt, 20)));
    }

    @Test
    @DisplayName("vonSpieler zeigt NUR seine Taten")
    void spielerIstGenauDieser() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.ABBAUEN, true, "von_anna"),
                tat(WANN, bert, ORT, Blocktat.ABBAUEN, true, "von_bert")));

        assertEquals(List.of("von_anna"), bloecke(ablage.vonSpieler(anna, 20)));
        assertEquals(List.of("von_bert"), bloecke(ablage.vonSpieler(bert, 20)));
    }

    @Test
    @DisplayName("Zweimal schreiben schreibt DAZU und ersetzt nicht")
    void zweimalSchreibenSchreibtDazu() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(List.of(tat(WANN, anna, ORT, Blocktat.SETZEN, true, "erst")));
        ablage.schreibe(List.of(
                tat(WANN.plusSeconds(60), anna, ORT, Blocktat.ABBAUEN, true, "dann")));

        assertEquals(List.of("dann", "erst"), bloecke(ablage.amOrt(ORT, 20)));
    }

    @Test
    @DisplayName("Eine leere Liste zu schreiben tut nichts und wirft nicht")
    void leerSchreibenTutNichts() {
        // Der haeufigste Fall von allen: Der Takt schlaegt jede Sekunde, und auf einem
        // ruhigen Server ist der Stapel leer.
        Blockbuchablage ablage = ablage();

        assertEquals(0, ablage.schreibe(List.of()));
        assertTrue(ablage.amOrt(ORT, 20).isEmpty());
    }

    @Test
    @DisplayName("Aufraeumen loescht die ALTEN Zeilen und laesst die neuen stehen")
    void aufraeumenNimmtNurAlte() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        Instant grenze = WANN.plusSeconds(90);

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.SETZEN, true, "alt"),
                tat(WANN.plusSeconds(60), anna, ORT, Blocktat.ABBAUEN, true, "auch_alt"),
                tat(WANN.plusSeconds(120), anna, ORT, Blocktat.SETZEN, true, "neu")));

        assertEquals(2, ablage.raeumeAuf(grenze, 100));
        assertEquals(List.of("neu"), bloecke(ablage.amOrt(ORT, 20)));
    }

    @Test
    @DisplayName("Aufraeumen haelt den Happen ein - und nimmt die AELTESTEN zuerst")
    void aufraeumenHaeltDenHappenEin() {
        // Der Happen ist die ganze Vorsorge gegen den Stillstand. Dass er die aeltesten
        // Zeilen zuerst nimmt, ist die zweite Haelfte: Reicht er dauerhaft nicht, bliebe
        // sonst gerade der aelteste Bestand fuer immer liegen - also genau das, was die
        // Frist loeschen soll.
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(List.of(
                tat(WANN, anna, ORT, Blocktat.SETZEN, true, "aeltest"),
                tat(WANN.plusSeconds(60), anna, ORT, Blocktat.ABBAUEN, true, "mittel"),
                tat(WANN.plusSeconds(120), anna, ORT, Blocktat.SETZEN, true, "jung")));

        assertEquals(2, ablage.raeumeAuf(WANN.plusSeconds(1000), 2),
                "hoechstens zwei, obwohl drei faellig sind");
        assertEquals(List.of("jung"), bloecke(ablage.amOrt(ORT, 20)),
                "die zwei AELTESTEN sind weg");

        assertEquals(1, ablage.raeumeAuf(WANN.plusSeconds(1000), 2),
                "der naechste Schlag nimmt den Rest");
        assertTrue(ablage.amOrt(ORT, 20).isEmpty());
    }

    @Test
    @DisplayName("Aufraeumen ohne faellige Zeile loescht nichts und ist kein Fehler")
    void aufraeumenOhneFaelligeTutNichts() {
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(List.of(tat(WANN, anna, ORT, Blocktat.SETZEN, true, "neu")));

        assertEquals(0, ablage.raeumeAuf(WANN, 100),
                "genau am Zeitpunkt ist noch nicht AELTER als er");
        assertEquals(List.of("neu"), bloecke(ablage.amOrt(ORT, 20)));
    }

    @Test
    @DisplayName("Eine Grenze unter 1 wird abgewiesen - auf jedem Weg")
    void grenzeUnterEinsWirdAbgewiesen() {
        // Die Grenze ist Pflicht, weil dies die groesste Tabelle des Projekts ist. Eine
        // 0 waere die stille Variante von "ohne Grenze": Die Antwort kaeme leer zurueck,
        // und der Befehl zeigte "hier ist nichts geschehen".
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.amOrt(ORT, 0));
        assertThrows(IllegalArgumentException.class, () -> ablage.amOrt(ORT, -1));
        assertThrows(IllegalArgumentException.class, () -> ablage.vonSpieler(anna, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.raeumeAuf(WANN, 0));
    }

    @Test
    @DisplayName("Ohne Ort, Spieler, Liste oder Zeitpunkt wird abgewiesen")
    void ohnePflichtangabeWirdAbgewiesen() {
        Blockbuchablage ablage = ablage();

        assertThrows(IllegalArgumentException.class, () -> ablage.amOrt(null, 20));
        assertThrows(IllegalArgumentException.class, () -> ablage.vonSpieler(null, 20));
        assertThrows(IllegalArgumentException.class, () -> ablage.schreibe(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.raeumeAuf(null, 20));
    }

    @Test
    @DisplayName("Eine Zeile, die nichts ist, wird abgewiesen - und zwar VOR dem Schreiben")
    void leereZeileWirdAbgewiesen() {
        // VOR dem Schreiben: Sonst stuenden die guten Zeilen schon im Buch, waehrend der
        // Aufrufer eine Ausnahme bekommt und annimmt, es sei nichts geschehen - und beim
        // naechsten Versuch stuenden sie doppelt da.
        Blockbuchablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        List<Blockzeile> mitLuecke = new ArrayList<>();
        mitLuecke.add(tat(WANN, anna, ORT, Blocktat.SETZEN, true, "gut"));
        mitLuecke.add(null);

        assertThrows(IllegalArgumentException.class, () -> ablage.schreibe(mitLuecke));
        assertTrue(ablage.amOrt(ORT, 20).isEmpty(),
                "die gute Zeile davor darf nicht geschrieben sein");
    }

    /** Die Blocknamen in der Reihenfolge der Antwort - so liest sich eine Erwartung. */
    protected static List<String> bloecke(List<Blockzeile> zeilen) {
        List<String> namen = new ArrayList<>();
        for (Blockzeile zeile : zeilen) {
            namen.add(zeile.block());
        }
        return namen;
    }
}
