package de.freimarken.kern;

import static de.freimarken.kern.Besitzbuch.Bescheid.ERTEILT;
import static de.freimarken.kern.Besitzbuch.Bescheid.GRENZE_ERREICHT;
import static de.freimarken.kern.Besitzbuch.Bescheid.KEINE_PARZELLE;
import static de.freimarken.kern.Besitzbuch.Bescheid.NICHT_EIGENTUEMER;
import static de.freimarken.kern.Besitzbuch.Bescheid.NICHT_EINGETRAGEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_EINGETRAGEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_VERGEBEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.ZU_WENIG_MARK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Spielregeln rund um Parzellen - ohne Server, ohne Datenbank, ohne Attrappe.
 *
 * <p>Alle Koordinaten sind WELTkoordinaten. Bei Raster 64+7 liegt Parzelle 0/0 auf
 * 0..63, die Mauer auf 64 und 70, der Weg dazwischen.
 */
class BesitzbuchTest {

    private static final String WELT = "freimarken:marktstadt";
    private static final int VIELE = 99;
    private static final long REICH = 10_000_000L;

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();
    private final UUID clara = UUID.randomUUID();

    private SpeicherLager lager;
    private Besitzbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        buch = new Besitzbuch(Rasterplan.marktstadt(), lager);
    }

    // --- Nehmen -----------------------------------------------------------------

    @Test
    @DisplayName("Die erste Parzelle ist gratis und gehoert danach dem Spieler")
    void ersteIstGratis() {
        var entscheid = buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(0L, entscheid.kosten(), "Die erste kostet nichts - auch ohne Guthaben");
        assertEquals(anna, entscheid.parzelle().orElseThrow().eigentuemer());
        assertEquals(1, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Die zweite kostet, und ohne Geld gibt es sie nicht")
    void zweiteKostet() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        var zuArm = buch.nehmen(WELT, anna, 100, 10, VIELE, 24_999L);
        assertEquals(ZU_WENIG_MARK, zuArm.bescheid());
        assertEquals(25_000L, zuArm.kosten(), "Der Preis gehoert in die Absage");
        assertEquals(1, lager.anzahlVon(WELT, anna), "Nichts angelegt");

        var reicht = buch.nehmen(WELT, anna, 100, 10, VIELE, 25_000L);
        assertEquals(ERTEILT, reicht.bescheid());
        assertEquals(25_000L, reicht.kosten());
    }

    @Test
    @DisplayName("Eine vergebene Parzelle bleibt beim Eigentuemer")
    void schonVergeben() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        var entscheid = buch.nehmen(WELT, bert, 20, 20, VIELE, REICH);

        assertEquals(SCHON_VERGEBEN, entscheid.bescheid());
        assertEquals(anna, entscheid.parzelle().orElseThrow().eigentuemer(),
                "Die Absage soll sagen, wem sie gehoert");
        assertEquals(0, lager.anzahlVon(WELT, bert));
    }

    @Test
    @DisplayName("Auf Weg und Mauer gibt es nichts zu nehmen")
    void nichtAufWegUndMauer() {
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, 67, 10, VIELE, REICH).bescheid());
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, 64, 10, VIELE, REICH).bescheid());
        assertEquals(0, lager.anzahl());
    }

    @Test
    @DisplayName("Die Rangobergrenze haelt - auch bei vollem Konto")
    void grenzeHaelt() {
        buch.nehmen(WELT, anna, 10, 10, 2, REICH);
        buch.nehmen(WELT, anna, 100, 10, 2, REICH);

        var dritte = buch.nehmen(WELT, anna, 200, 10, 2, REICH);

        assertEquals(GRENZE_ERREICHT, dritte.bescheid());
        assertEquals(2, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Die Staffel zaehlt je Spieler, nicht je Server")
    void staffelJeSpieler() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);
        buch.nehmen(WELT, anna, 100, 10, VIELE, REICH);

        // Berts erste ist trotzdem gratis.
        var bertsErste = buch.nehmen(WELT, bert, 200, 10, VIELE, 0L);
        assertEquals(ERTEILT, bertsErste.bescheid());
        assertEquals(0L, bertsErste.kosten());
    }

    // --- Schutz -----------------------------------------------------------------

    @Test
    @DisplayName("Nur Eigentuemer und Mitbauer duerfen bauen")
    void schutz() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        assertTrue(buch.darfBauen(WELT, anna, 10, 10));
        assertTrue(buch.darfBauen(WELT, anna, 63, 63), "ueberall auf der eigenen Parzelle");
        assertFalse(buch.darfBauen(WELT, bert, 10, 10));

        buch.mitbauerHinzufuegen(WELT, anna, 10, 10, bert);
        assertTrue(buch.darfBauen(WELT, bert, 10, 10));
        assertFalse(buch.darfBauen(WELT, clara, 10, 10));
    }

    @Test
    @DisplayName("Weg, Mauer und freie Parzellen gehoeren niemandem")
    void niemandBautAufWegen() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        assertFalse(buch.darfBauen(WELT, anna, 64, 10), "Mauer");
        assertFalse(buch.darfBauen(WELT, anna, 67, 10), "Weg");
        assertFalse(buch.darfBauen(WELT, anna, 100, 10), "fremde, freie Parzelle");
    }

    @Test
    @DisplayName("Eine aufgegebene Parzelle schuetzt niemanden mehr")
    void schutzEndetMitDemBesitz() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);
        buch.aufgeben(WELT, anna, 10, 10);

        assertFalse(buch.darfBauen(WELT, anna, 10, 10));
        assertTrue(buch.parzelleAn(WELT, 10, 10).isEmpty());
    }

    // --- Mitbauer ---------------------------------------------------------------

    @Test
    @DisplayName("Nur der Eigentuemer vergibt Mitbauerrechte")
    void nurEigentuemerVergibt() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        var fremd = buch.mitbauerHinzufuegen(WELT, bert, 10, 10, clara);
        assertEquals(NICHT_EIGENTUEMER, fremd.bescheid());
        assertFalse(buch.darfBauen(WELT, clara, 10, 10));
    }

    @Test
    @DisplayName("Doppelt eintragen und leer austragen werden benannt, nicht geschluckt")
    void doppelteEintraege() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);
        buch.mitbauerHinzufuegen(WELT, anna, 10, 10, bert);

        assertEquals(SCHON_EINGETRAGEN,
                buch.mitbauerHinzufuegen(WELT, anna, 10, 10, bert).bescheid());
        assertEquals(SCHON_EINGETRAGEN,
                buch.mitbauerHinzufuegen(WELT, anna, 10, 10, anna).bescheid(),
                "Der Eigentuemer steht ohnehin drauf");
        assertEquals(NICHT_EINGETRAGEN,
                buch.mitbauerEntfernen(WELT, anna, 10, 10, clara).bescheid());
    }

    @Test
    @DisplayName("Ein ausgetragener Mitbauer darf sofort nicht mehr bauen")
    void austragenWirktSofort() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);
        buch.mitbauerHinzufuegen(WELT, anna, 10, 10, bert);
        assertTrue(buch.darfBauen(WELT, bert, 10, 10));

        assertEquals(ERTEILT, buch.mitbauerEntfernen(WELT, anna, 10, 10, bert).bescheid());
        assertFalse(buch.darfBauen(WELT, bert, 10, 10));
    }

    // --- Aufgeben ---------------------------------------------------------------

    @Test
    @DisplayName("Aufgeben gibt kein Geld zurueck - sonst waere die Staffel umgehbar")
    void aufgebenOhneRueckzahlung() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);
        buch.nehmen(WELT, anna, 100, 10, VIELE, REICH);
        assertEquals(2, lager.anzahlVon(WELT, anna));

        var weg = buch.aufgeben(WELT, anna, 10, 10);
        assertEquals(ERTEILT, weg.bescheid());
        assertEquals(0L, weg.kosten(), "keine Rueckzahlung");
        assertEquals(1, lager.anzahlVon(WELT, anna));

        // Die naechste kostet wieder den Preis der zweiten, nicht der dritten.
        var naechste = buch.nehmen(WELT, anna, 200, 10, VIELE, REICH);
        assertEquals(25_000L, naechste.kosten());
    }

    @Test
    @DisplayName("Fremden Besitz gibt niemand auf")
    void fremdesAufgeben() {
        buch.nehmen(WELT, anna, 10, 10, VIELE, 0L);

        assertEquals(NICHT_EIGENTUEMER, buch.aufgeben(WELT, bert, 10, 10).bescheid());
        assertTrue(buch.parzelleAn(WELT, 10, 10).isPresent());
    }
}
