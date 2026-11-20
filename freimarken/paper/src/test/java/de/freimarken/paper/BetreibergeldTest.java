package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.SpeicherKasse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Geld von Hand - die Regel, ohne Server.
 *
 * <p>Jeder der fuenf Ausgaenge bekommt seine Probe, und zwei davon sind der Grund, aus
 * dem es diese Klasse gibt: Ein unbekannter Name darf NICHT buchen (die Kasse legt sonst
 * ein Konto samt Startguthaben an), und ein ungedeckter Abzug darf nicht als Erfolg
 * gemeldet werden.
 */
@DisplayName("Geld von Hand")
class BetreibergeldTest {

    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());

    private SpeicherKasse kasse;

    @BeforeEach
    void vorher() {
        kasse = new SpeicherKasse(0L, UHR);
    }

    /** Die Namenssuche der Proben: genau ein bekannter Name. */
    private static Betreibergeld.Namenssuche nur(String bekannt, UUID kennung) {
        return name -> bekannt.equals(name) ? kennung : null;
    }

    @Test
    @DisplayName("Eine positive Zahl schreibt gut - und nennt den Stand danach")
    void positivSchreibtGut() {
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", 500L);

        assertEquals(Betreibergeld.Ausgang.GUTGESCHRIEBEN, ergebnis.ausgang());
        assertEquals(500L, ergebnis.betrag(), "der Betrag OHNE Vorzeichen");
        assertEquals(500L, ergebnis.stand());
        assertEquals(500L, kasse.guthaben(ANNA));
        // NEUESTE ZUERST, und die EROEFFNUNG gehoert dazu: Die Kasse eroeffnet das
        // Konto bei der ersten Buchung selbst und schreibt dafuer ihre eigene Zeile -
        // "keine Geldbewegung ohne Zeile im Buch" gilt auch fuer das Startguthaben.
        assertEquals(List.of(Buchungsgrund.BETREIBER_GUTSCHRIFT,
                        Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "ein EIGENER Grund: Mehrere vorhandene werden als Marke gelesen"
                        + " (\"ist heute schon ein Bonus gezahlt?\")");
    }

    @Test
    @DisplayName("Ein Minus zieht ab - und bucht mit dem anderen Grund")
    void minusZiehtAb() {
        kasse.gutschreiben(ANNA, 1_000L, Buchungsgrund.BESTAND);

        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", -400L);

        assertEquals(Betreibergeld.Ausgang.ABGEZOGEN, ergebnis.ausgang());
        assertEquals(400L, ergebnis.betrag(), "der Betrag OHNE Vorzeichen - die Richtung"
                + " steht im Ausgang und im Grund");
        assertEquals(600L, ergebnis.stand());
        assertEquals(List.of(Buchungsgrund.BETREIBER_ABZUG, Buchungsgrund.BESTAND,
                        Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "neueste zuerst - der Abzug steht oben");
    }

    @Test
    @DisplayName("Ein unbekannter Name bucht NICHT - und legt kein Konto an")
    void unbekannterNameBuchtNicht() {
        // DER WICHTIGSTE DER FUENF: getOfflinePlayer(String) erfindet fuer einen
        // unbekannten Namen eine frische Kennung, und gutschreiben legt dafuer ein Konto
        // samt Startguthaben an. Ein Tippfehler schickte das Geld auf ein Geisterkonto,
        // und nichts waere aufgefallen.
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Ann", 500L);

        assertEquals(Betreibergeld.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
        assertEquals("Ann", ergebnis.spieler(), "der getippte Name gehoert in die"
                + " Antwort - sonst sucht der Betreiber den Fehler anderswo");
        assertEquals(0L, ergebnis.betrag());
        assertEquals(0L, kasse.guthaben(ANNA));
        assertTrue(kasse.verlauf(ANNA, 10).isEmpty(), "keine Buchung");
    }

    @Test
    @DisplayName("Der NAME wird vor dem Betrag geprueft")
    void nameVorBetrag() {
        // Wer sich im Namen vertippt UND eine 0 schreibt, soll den Namen lesen: Jener
        // ist der Fehler, der auf ein Geisterkonto fuehrt.
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Ann", 0L);

        assertEquals(Betreibergeld.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
    }

    @Test
    @DisplayName("0 Mark ist keine Buchung")
    void nullIstKeineBuchung() {
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", 0L);

        assertEquals(Betreibergeld.Ausgang.NICHTS_ZU_BUCHEN, ergebnis.ausgang());
        assertTrue(kasse.verlauf(ANNA, 10).isEmpty(),
                "eine Zeile ueber 0 Mark im Buch ist eine Zeile ohne Geldbewegung");
    }

    @Test
    @DisplayName("Ein ungedeckter Abzug meldet es - und bucht nichts")
    void ungedeckterAbzug() {
        kasse.gutschreiben(ANNA, 100L, Buchungsgrund.BESTAND);

        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", -500L);

        assertEquals(Betreibergeld.Ausgang.NICHT_GEDECKT, ergebnis.ausgang());
        assertEquals(500L, ergebnis.betrag(), "was er wollte - damit der Betreiber die"
                + " Zahl mit dem Stand vergleichen kann");
        assertEquals(100L, ergebnis.stand(), "und der Stand, der nicht reicht");
        assertEquals(100L, kasse.guthaben(ANNA), "nichts passiert");
        assertEquals(List.of(Buchungsgrund.BESTAND, Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "keine Zeile vom abgewiesenen Abzug - false heisst: nichts passiert");
    }

    @Test
    @DisplayName("Eine stumme Kasse wirft weiter - der Aufrufer protokolliert")
    void stummeKasseWirftWeiter() {
        // Kein Rueckfall: Der Betreiber haelt die Korrektur sonst fuer erledigt und die
        // naechste fuer eine zweite.
        Kasse stumm = new Kasse() {
            @Override
            public long guthaben(UUID spieler) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public boolean eroeffne(UUID spieler) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public List<Buchung> verlauf(UUID spieler, int hoechstens) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> Betreibergeld.buche(stumm, nur("Anna", ANNA), "Anna", 500L));
    }

    @Test
    @DisplayName("Kasse und Namenssuche sind Pflicht")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.buche(null, nur("Anna", ANNA), "Anna", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.buche(kasse, null, "Anna", 1L));
    }

    @Test
    @DisplayName("Die Schranke ist eine Zahl, die kein Konto ueberlaufen laesst")
    void schrankeIstTechnisch() {
        // Keine Spielzahl: Sie faengt den Tippfehler mit drei Nullen zu viel. Zwei
        // Buchungen in dieser Groesse muessen sich noch addieren lassen.
        assertTrue(Betreibergeld.HOECHSTENS > 0L);
        assertTrue(Long.MAX_VALUE / Betreibergeld.HOECHSTENS > 1_000_000L,
                "sonst kann eine Reihe solcher Buchungen die Summe im Buch sprengen");
    }

    private static List<Buchungsgrund> gruende(List<Buchung> zeilen) {
        return zeilen.stream().map(Buchung::grund).toList();
    }
}
