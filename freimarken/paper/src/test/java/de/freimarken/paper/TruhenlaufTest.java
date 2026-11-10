package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherTruhe;
import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Truhenwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der taegliche Lauf der Freien Truhe - leeren, raeumen, und wann nicht.
 *
 * <p>Die beiden tragenden Zusagen sind VERNEINUNGEN, und beide kosten einen Tag, wenn sie
 * fehlen:
 * <ul>
 *   <li><b>Eine OFFENE Truhe wird nicht geleert</b>, und der Tagesmerker bleibt dann
 *       stehen. Sonst waere der ganze Inhalt die Entnahme des Spielers, der gerade
 *       hineinsieht.</li>
 *   <li><b>Geraeumt wird NICHT, solange noch eine Truhe voll ist.</b> Die Zeilen von
 *       gestern sind der Tagesdeckel von gestern.</li>
 * </ul>
 */
@DisplayName("Der taegliche Lauf der Freien Truhe")
class TruhenlaufTest {

    /**
     * Die Werte, gegen die diese Probe rechnet - 16 Stueck am Tag, zwei Verbote.
     *
     * <p>Eigene Zahlen und NICHT {@link Truhenwerte#VORGABE}: Ab Werk gilt seit dem
     * 10.11.2026 "kein Deckel, nichts verboten", und eine Probe, die am Deckel oder am
     * Verbot rechnet, braucht beides. Haengt sie an der Vorgabe, prueft sie die Vorgabe
     * statt der Regel - und die naechste Entscheidung des Betreibers bricht ein halbes
     * Dutzend Proben, die mit ihr nichts zu tun haben.
     */
    private static final Truhenwerte WERTE =
            new Truhenwerte(16, Set.of("written_book", "shulker_box"));

    private static final String WELT = "freimarken:altfurt";
    private static final Blockort EINE = new Blockort(WELT, 72, 72, 20);
    private static final Blockort ZWEITE = new Blockort(WELT, 80, 72, 20);
    private static final UUID ANNA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 10);
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private final List<String> protokollzeilen = new ArrayList<>();
    private final Truhenablage ablage = new SpeicherTruhe();

    @Test
    @DisplayName("Der erste Takt des Tages leert und raeumt, der zweite tut nichts")
    void einmalAmTag() {
        ablage.merke(ANNA, HEUTE.minusDays(1), 16);
        Leerprobe leerung = new Leerprobe(Map.of(EINE, 42));
        Truhenlauf lauf = lauf(leerung, EINE);

        var erste = lauf.imTakt();
        var zweite = lauf.imTakt();

        assertEquals(42, erste.geleert());
        assertEquals(1, erste.zeilen(), "die Zeile von gestern");
        assertFalse(erste.spaeter());
        assertEquals(0, zweite.geleert(), "der Merker haelt den zweiten Takt an");
        assertEquals(List.of(EINE), leerung.gerufen, "und ruft die Truhe nicht zweimal");
        assertEquals(0, ablage.behalten(ANNA, HEUTE.minusDays(1)));
    }

    @Test
    @DisplayName("Eine OFFENE Truhe haelt den Merker - der naechste Takt versucht es wieder")
    void offeneTruheHaeltDenMerker() {
        // DIE TRAGENDE PROBE. Wuerde der Merker trotzdem gesetzt, kaeme der naechste
        // Versuch erst MORGEN - und bis dahin liegt der Inhalt von gestern in einer
        // Truhe, deren Tageszeilen geloescht sind. Jeder duerfte dann noch einmal
        // nehmen.
        ablage.merke(ANNA, HEUTE.minusDays(1), 16);
        Leerprobe leerung = new Leerprobe(Map.of(EINE, Truhenlauf.SPAETER));
        Truhenlauf lauf = lauf(leerung, EINE);

        var erste = lauf.imTakt();

        assertTrue(erste.spaeter());
        assertEquals(0, erste.zeilen(), "geraeumt wird erst, wenn die Truhe leer ist");
        assertEquals(16, ablage.behalten(ANNA, HEUTE.minusDays(1)),
                "die Zeile von gestern steht noch");

        leerung.wirdLeer(EINE, 7);
        var zweite = lauf.imTakt();

        assertEquals(7, zweite.geleert(), "der naechste Takt holt es nach");
        assertEquals(1, zweite.zeilen());
        assertFalse(zweite.spaeter());
    }

    @Test
    @DisplayName("Zwei Truhen: die eine offen, die andere wird geleert")
    void zweiTruhenEineOffen() {
        // Keine "alles oder nichts": Die geschlossene Truhe soll leer werden, auch wenn
        // an der anderen jemand steht. Nur das RAEUMEN wartet - und das ist richtig,
        // denn solange irgendwo noch der Inhalt von gestern liegt, gilt auch noch der
        // Deckel von gestern.
        Leerprobe leerung = new Leerprobe(Map.of(EINE, Truhenlauf.SPAETER, ZWEITE, 9));
        Truhenlauf lauf = lauf(leerung, EINE, ZWEITE);

        var ergebnis = lauf.imTakt();

        assertEquals(9, ergebnis.geleert());
        assertTrue(ergebnis.spaeter());
        assertEquals(List.of(EINE, ZWEITE), leerung.gerufen, "beide werden gefragt");
    }

    @Test
    @DisplayName("Eine Stoerung beim Raeumen haelt den Merker ebenfalls")
    void stoerungBeimRaeumenHaeltDenMerker() {
        Leerprobe leerung = new Leerprobe(Map.of(EINE, 5));
        Truhenlauf lauf = new Truhenlauf(buch(stolpernd(), EINE), leerung, protokoll());

        var ergebnis = lauf.imTakt();

        assertEquals(5, ergebnis.geleert(), "geleert wurde trotzdem");
        assertTrue(ergebnis.spaeter());

        var zweite = lauf.imTakt();

        assertEquals(List.of(EINE, EINE), leerung.gerufen,
                "ein zweites Leeren einer leeren Truhe kostet nichts");
        assertTrue(zweite.spaeter());
    }

    @Test
    @DisplayName("OHNE Truhe wird trotzdem geraeumt - sonst waeren die Zeilen unloeschbar")
    void ohneTruheWirdGeraeumt() {
        // Wer die letzte Stelle aus der config.yml nimmt, will seine alten Tageszeilen
        // nicht fuer immer behalten. Dieselbe Entscheidung wie bei der Blockraeumung, die
        // auch dann laeuft, wenn das Blockbuch nichts mehr schreibt.
        ablage.merke(ANNA, HEUTE.minusDays(1), 16);
        Leerprobe leerung = new Leerprobe(Map.of());

        var ergebnis = lauf(leerung).imTakt();

        assertEquals(0, ergebnis.geleert());
        assertEquals(1, ergebnis.zeilen());
        assertEquals(List.of(), leerung.gerufen);
    }

    @Test
    @DisplayName("Der HEUTIGE Tag bleibt stehen - geraeumt wird ausschliessend")
    void heuteBleibtStehen() {
        ablage.merke(ANNA, HEUTE, 3);
        ablage.merke(ANNA, HEUTE.minusDays(1), 16);

        var ergebnis = lauf(new Leerprobe(Map.of(EINE, 0)), EINE).imTakt();

        assertEquals(1, ergebnis.zeilen());
        assertEquals(3, ablage.behalten(ANNA, HEUTE),
                "wer heute schon drei genommen hat, hat sie nach dem Lauf noch");
    }

    @Test
    @DisplayName("Ein Lauf ohne Inhalt und ohne Zeilen schreibt NICHTS ins Protokoll")
    void stillerLauf() {
        // Sonst stuende in jedem Protokoll jede Nacht eine Zeile mit drei Nullen, und die
        // Zeilen, die etwas sagen, gingen darin unter.
        lauf(new Leerprobe(Map.of(EINE, 0)), EINE).imTakt();

        assertEquals(List.of(), protokollzeilen);
    }

    @Test
    @DisplayName("Ein Lauf, der etwas getan hat, sagt es mit beiden Zahlen")
    void lauterLauf() {
        ablage.merke(ANNA, HEUTE.minusDays(1), 16);

        lauf(new Leerprobe(Map.of(EINE, 42)), EINE).imTakt();

        assertEquals(1, protokollzeilen.size(), protokollzeilen.toString());
        assertTrue(protokollzeilen.getFirst().contains("42 Stueck weg"),
                protokollzeilen.toString());
        assertTrue(protokollzeilen.getFirst().contains("1 Tageszeilen"),
                protokollzeilen.toString());
    }

    @Test
    @DisplayName("Ohne Buch, Leerung oder Protokoll gibt es keinen Lauf")
    void pflichtenGelten() {
        Truhenbuch buch = buch(ablage, EINE);
        Leerprobe leerung = new Leerprobe(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenlauf(null, leerung, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenlauf(buch, null, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenlauf(buch, leerung, null));
    }

    private Truhenlauf lauf(Truhenlauf.Leerung leerung, Blockort... stellen) {
        return new Truhenlauf(buch(ablage, stellen), leerung, protokoll());
    }

    private Truhenbuch buch(Truhenablage welche, Blockort... stellen) {
        return new Truhenbuch(Truhenverzeichnis.ausStellen(List.of(stellen)), welche,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitTruhe(WERTE)),
                UHR, protokoll());
    }

    /** Eine Ablage, deren Raeumen scheitert - die zweite Stoerungsrichtung. */
    private Truhenablage stolpernd() {
        return new Truhenablage() {
            @Override
            public int behalten(UUID spieler, LocalDate tag) {
                return 0;
            }

            @Override
            public void merke(UUID spieler, LocalDate tag, int stueck) {
            }

            @Override
            public int raeume(LocalDate vorTag) {
                throw new IllegalStateException("Nicht raeumbar (Probe)");
            }
        };
    }

    /**
     * Die Leerung als Attrappe - sie schreibt mit, WELCHE Stelle gefragt wurde.
     *
     * <p>Die Liste der Aufrufe ist der eigentliche Gegenstand mehrerer Proben: Eine
     * Stelle, die zweimal gefragt wird, waere ein doppeltes Leeren; eine, die gar nicht
     * gefragt wird, eine Truhe, die nie leer wird.
     */
    private static final class Leerprobe implements Truhenlauf.Leerung {

        private final Map<Blockort, Integer> antworten;
        private final List<Blockort> gerufen = new ArrayList<>();

        private Leerprobe(Map<Blockort, Integer> antworten) {
            this.antworten = new HashMap<>(antworten);
        }

        /** Aus einer Truhe, die gerade offen war, wird eine, die sich leeren laesst. */
        private void wirdLeer(Blockort ort, int stueck) {
            antworten.put(ort, stueck);
        }

        @Override
        public int leere(Blockort ort) {
            gerufen.add(ort);
            return antworten.getOrDefault(ort, 0);
        }
    }

    private Logger protokoll() {
        Logger eigener = Logger.getLogger("Truhenlaufprobe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz.getLevel() + ": " + satz.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }
}
