package de.freimarken.kern;

import static de.freimarken.kern.Besitzbuch.Bescheid.AUSSERHALB;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private Kasse kasse;
    private Besitzbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(REICH);
        buch = new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse);
    }

    /** Ein Buch, dessen Spieler genau so viel Geld haben wie angegeben. */
    private Besitzbuch buchMitGuthaben(long guthaben) {
        kasse = new SpeicherKasse(guthaben);
        return new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse);
    }

    // --- Nehmen -----------------------------------------------------------------

    @Test
    @DisplayName("Die erste Parzelle ist gratis und gehoert danach dem Spieler")
    void ersteIstGratis() {
        Besitzbuch armesBuch = buchMitGuthaben(0L);

        var entscheid = armesBuch.nehmen(WELT, anna, 10, 10, VIELE);

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(0L, entscheid.kosten(), "Die erste kostet nichts - auch ohne Guthaben");
        assertEquals(anna, entscheid.parzelle().orElseThrow().eigentuemer());
        assertEquals(1, lager.anzahlVon(WELT, anna));
        assertEquals(0L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Die zweite kostet, und ohne Geld gibt es sie nicht")
    void zweiteKostet() {
        Besitzbuch knappesBuch = buchMitGuthaben(24_999L);
        knappesBuch.nehmen(WELT, anna, 10, 10, VIELE);

        var zuArm = knappesBuch.nehmen(WELT, anna, 100, 10, VIELE);
        assertEquals(ZU_WENIG_MARK, zuArm.bescheid());
        assertEquals(25_000L, zuArm.kosten(), "Der Preis gehoert in die Absage");
        assertEquals(1, lager.anzahlVon(WELT, anna), "Nichts angelegt");
        assertEquals(24_999L, kasse.guthaben(anna), "Eine Absage kostet nichts");

        kasse.gutschreiben(anna, 1L);
        var reicht = knappesBuch.nehmen(WELT, anna, 100, 10, VIELE);
        assertEquals(ERTEILT, reicht.bescheid());
        assertEquals(25_000L, reicht.kosten());
    }

    @Test
    @DisplayName("Der Preis wird wirklich abgebucht, nicht nur genannt")
    void preisWirdAbgebucht() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);
        assertEquals(REICH, kasse.guthaben(anna), "Die erste war gratis");

        buch.nehmen(WELT, anna, 100, 10, VIELE);
        assertEquals(REICH - 25_000L, kasse.guthaben(anna));

        buch.nehmen(WELT, anna, 200, 10, VIELE);
        assertEquals(REICH - 25_000L - 50_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Eine vergebene Parzelle bleibt beim Eigentuemer")
    void schonVergeben() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);

        var entscheid = buch.nehmen(WELT, bert, 20, 20, VIELE);

        assertEquals(SCHON_VERGEBEN, entscheid.bescheid());
        assertEquals(anna, entscheid.parzelle().orElseThrow().eigentuemer(),
                "Die Absage soll sagen, wem sie gehoert");
        assertEquals(0, lager.anzahlVon(WELT, bert));
        assertEquals(REICH, kasse.guthaben(bert), "und sie kostet ihn nichts");
    }

    @Test
    @DisplayName("Auf Weg und Mauer gibt es nichts zu nehmen")
    void nichtAufWegUndMauer() {
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, 67, 10, VIELE).bescheid());
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, 64, 10, VIELE).bescheid());
        assertEquals(0, lager.anzahl());
        assertEquals(REICH, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Die Rangobergrenze haelt - auch bei vollem Konto")
    void grenzeHaelt() {
        buch.nehmen(WELT, anna, 10, 10, 2);
        buch.nehmen(WELT, anna, 100, 10, 2);
        long nachZwei = kasse.guthaben(anna);

        var dritte = buch.nehmen(WELT, anna, 200, 10, 2);

        assertEquals(GRENZE_ERREICHT, dritte.bescheid());
        assertEquals(2, lager.anzahlVon(WELT, anna));
        assertEquals(nachZwei, kasse.guthaben(anna), "die Absage kommt vor der Kasse");
    }

    @Test
    @DisplayName("Die Staffel zaehlt je Spieler, nicht je Server")
    void staffelJeSpieler() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);
        buch.nehmen(WELT, anna, 100, 10, VIELE);

        // Berts erste ist trotzdem gratis.
        var bertsErste = buch.nehmen(WELT, bert, 200, 10, VIELE);
        assertEquals(ERTEILT, bertsErste.bescheid());
        assertEquals(0L, bertsErste.kosten());
        assertEquals(REICH, kasse.guthaben(bert));
    }

    @Test
    @DisplayName("Scheitert die Ablage, kommt das Geld zurueck")
    void geldZurueckWennAblageScheitert() {
        // Der Fall im Betrieb: Zwei Spieler nehmen im selben Augenblick dieselbe Parzelle,
        // der eindeutige Schluessel in der Datenbank laesst den zweiten auflaufen. Dann
        // darf nicht das Geld weg und die Parzelle nicht da sein.
        Besitzbuch sperrigesBuch = new Besitzbuch(Stadtgrenze.marktstadt(), new StoerLager(), kasse);
        sperrigesBuch.nehmen(WELT, anna, 10, 10, VIELE);
        long vorher = kasse.guthaben(anna);

        assertThrows(IllegalStateException.class,
                () -> sperrigesBuch.nehmen(WELT, anna, 100, 10, VIELE));

        assertEquals(vorher, kasse.guthaben(anna), "Gegenbuchung fehlt");
    }

    // --- Schutz -----------------------------------------------------------------

    @Test
    @DisplayName("Nur Eigentuemer und Mitbauer duerfen bauen")
    void schutz() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);

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
        buch.nehmen(WELT, anna, 10, 10, VIELE);

        assertFalse(buch.darfBauen(WELT, anna, 64, 10), "Mauer");
        assertFalse(buch.darfBauen(WELT, anna, 67, 10), "Weg");
        assertFalse(buch.darfBauen(WELT, anna, 100, 10), "fremde, freie Parzelle");
    }

    @Test
    @DisplayName("Eine aufgegebene Parzelle schuetzt niemanden mehr")
    void schutzEndetMitDemBesitz() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);
        buch.aufgeben(WELT, anna, 10, 10);

        assertFalse(buch.darfBauen(WELT, anna, 10, 10));
        assertTrue(buch.parzelleAn(WELT, 10, 10).isEmpty());
    }

    // --- Mitbauer ---------------------------------------------------------------

    @Test
    @DisplayName("Nur der Eigentuemer vergibt Mitbauerrechte")
    void nurEigentuemerVergibt() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);

        var fremd = buch.mitbauerHinzufuegen(WELT, bert, 10, 10, clara);
        assertEquals(NICHT_EIGENTUEMER, fremd.bescheid());
        assertFalse(buch.darfBauen(WELT, clara, 10, 10));
    }

    @Test
    @DisplayName("Doppelt eintragen und leer austragen werden benannt, nicht geschluckt")
    void doppelteEintraege() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);
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
        buch.nehmen(WELT, anna, 10, 10, VIELE);
        buch.mitbauerHinzufuegen(WELT, anna, 10, 10, bert);
        assertTrue(buch.darfBauen(WELT, bert, 10, 10));

        assertEquals(ERTEILT, buch.mitbauerEntfernen(WELT, anna, 10, 10, bert).bescheid());
        assertFalse(buch.darfBauen(WELT, bert, 10, 10));
    }

    // --- Aufgeben ---------------------------------------------------------------

    @Test
    @DisplayName("Aufgeben gibt kein Geld zurueck - sonst waere die Staffel umgehbar")
    void aufgebenOhneRueckzahlung() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);
        buch.nehmen(WELT, anna, 100, 10, VIELE);
        assertEquals(2, lager.anzahlVon(WELT, anna));
        long bezahlt = kasse.guthaben(anna);

        var weg = buch.aufgeben(WELT, anna, 10, 10);
        assertEquals(ERTEILT, weg.bescheid());
        assertEquals(0L, weg.kosten(), "keine Rueckzahlung");
        assertEquals(1, lager.anzahlVon(WELT, anna));
        assertEquals(bezahlt, kasse.guthaben(anna), "auch nicht an der Kasse");

        // Die naechste kostet wieder den Preis der zweiten, nicht der dritten.
        var naechste = buch.nehmen(WELT, anna, 200, 10, VIELE);
        assertEquals(25_000L, naechste.kosten());
    }

    @Test
    @DisplayName("Fremden Besitz gibt niemand auf")
    void fremdesAufgeben() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);

        assertEquals(NICHT_EIGENTUEMER, buch.aufgeben(WELT, bert, 10, 10).bescheid());
        assertTrue(buch.parzelleAn(WELT, 10, 10).isPresent());
    }

    // --- Stadtgrenze ------------------------------------------------------------

    @Test
    @DisplayName("Jenseits der Grenze gibt es nichts - und es kostet auch nichts")
    void jenseitsDerGrenze() {
        // Block 1491 ist der erste Baulandblock der Parzelle 21, die es nicht mehr gibt.
        var entscheid = buch.nehmen(WELT, anna, 1491, 0, VIELE);

        assertEquals(AUSSERHALB, entscheid.bescheid());
        assertEquals(0L, entscheid.kosten());
        assertTrue(entscheid.parzelle().isEmpty());
        assertEquals(0, lager.anzahlVon(WELT, anna), "Es darf nichts angelegt worden sein");
        assertEquals(REICH, kasse.guthaben(anna), "Es darf nichts abgebucht worden sein");
    }

    @Test
    @DisplayName("Die aeusserste Parzelle im Osten und Sueden geht noch")
    void aeussersteParzelleGehtNoch() {
        var entscheid = buch.nehmen(WELT, anna, 1483, 1483, VIELE);

        assertEquals(ERTEILT, entscheid.bescheid());
        var parzelle = entscheid.parzelle().orElseThrow();
        assertEquals(20, parzelle.x());
        assertEquals(20, parzelle.z());
    }

    @Test
    @DisplayName("Die aeusserste Parzelle im Westen und Norden geht auch - negative Nummern")
    void aeussersteParzelleNegativ() {
        var entscheid = buch.nehmen(WELT, anna, -1491, -1491, VIELE);

        assertEquals(ERTEILT, entscheid.bescheid());
        var parzelle = entscheid.parzelle().orElseThrow();
        assertEquals(-21, parzelle.x());
        assertEquals(-21, parzelle.z());
    }

    @Test
    @DisplayName("Eine draussen schon vergebene Parzelle meldet den Eigentuemer, nicht die Grenze")
    void altbestandDraussenBehaeltSeinenEigentuemer() {
        // So etwas entsteht, wenn die Grenze erst nach dem Verkauf gezogen wird.
        lager.anlegen(WELT, 99, 99, bert, 0L);

        var entscheid = buch.nehmen(WELT, anna, 99 * 71, 99 * 71, VIELE);

        assertEquals(SCHON_VERGEBEN, entscheid.bescheid());
        assertEquals(bert, entscheid.parzelle().orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Die Grenze enteignet nicht: draussen wird weiter geschuetzt und gebaut")
    void grenzeEnteignetNicht() {
        lager.anlegen(WELT, 99, 99, bert, 0L);

        assertTrue(buch.parzelleAn(WELT, 99 * 71, 99 * 71).isPresent());
        assertTrue(buch.darfBauen(WELT, bert, 99 * 71, 99 * 71));
        assertFalse(buch.darfBauen(WELT, anna, 99 * 71, 99 * 71));
        // Und aufgeben kann er sie auch - sonst liesse sich draussen nie aufraeumen.
        assertEquals(ERTEILT, buch.aufgeben(WELT, bert, 99 * 71, 99 * 71).bescheid());
    }

    @Test
    @DisplayName("Weg und Mauer bleiben KEINE_PARZELLE - auch am aeussersten Rand")
    void wegBleibtWeg() {
        // Mitten in der Stadt.
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, 67, 0, VIELE).bescheid());
        // Der aeusserste Westweg liegt innerhalb der Grenze, gehoert aber zur Rasterzelle
        // -22, die es als Parzelle nicht mehr gibt. Die Antwort muss trotzdem "das ist ein
        // Weg" sein und nicht "hier endet die Stadt" - sonst steht der Spieler auf einem
        // begehbaren Weg und bekommt gesagt, er sei draussen.
        assertEquals(KEINE_PARZELLE, buch.nehmen(WELT, anna, -1495, 0, VIELE).bescheid());
    }

    /**
     * Ein Lager, das beim zweiten Anlegen aufgibt - wie die Datenbank, wenn ihr eindeutiger
     * Schluessel greift. Nur dafuer da, die Gegenbuchung pruefbar zu machen.
     */
    private static final class StoerLager implements BesitzLager {

        private final SpeicherLager echt = new SpeicherLager();
        private boolean schonEinmal;

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            return echt.finde(welt, x, z);
        }

        @Override
        public List<Parzelle> vonSpieler(String welt, UUID spieler) {
            return echt.vonSpieler(welt, spieler);
        }

        @Override
        public int anzahlVon(String welt, UUID spieler) {
            return echt.anzahlVon(welt, spieler);
        }

        @Override
        public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
            if (schonEinmal) {
                throw new IllegalStateException("Parzelle ist bereits vergeben: " + x + "/" + z);
            }
            schonEinmal = true;
            return echt.anlegen(welt, x, z, eigentuemer, preis);
        }

        @Override
        public Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer) {
            return echt.mitbauerSetzen(id, mitbauer);
        }

        @Override
        public void entfernen(long id) {
            echt.entfernen(id);
        }
    }
}
