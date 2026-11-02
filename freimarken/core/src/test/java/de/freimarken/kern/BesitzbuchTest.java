package de.freimarken.kern;

import static de.freimarken.kern.Besitzbuch.Bescheid.AUF_DEM_WEG;
import static de.freimarken.kern.Besitzbuch.Bescheid.AUSSERHALB;
import static de.freimarken.kern.Besitzbuch.Bescheid.ERTEILT;
import static de.freimarken.kern.Besitzbuch.Bescheid.GRENZE_ERREICHT;
import static de.freimarken.kern.Besitzbuch.Bescheid.HAELT_ZUSAMMEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.KEINE_GRUPPE;
import static de.freimarken.kern.Besitzbuch.Bescheid.KEINE_PARZELLE;
import static de.freimarken.kern.Besitzbuch.Bescheid.NICHT_EIGENTUEMER;
import static de.freimarken.kern.Besitzbuch.Bescheid.NICHT_EINGETRAGEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.NOCH_FREI;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_EINGETRAGEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_IN_GRUPPE;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_VERGEBEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.SCHON_ZUSAMMEN;
import static de.freimarken.kern.Besitzbuch.Bescheid.TEIL_EINER_GRUPPE;
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

    /**
     * Der Wirtschaftsstand aller Proben hier: genau die ausgelieferten Werte.
     *
     * <p>Damit pruefen die festen Betraege in dieser Datei weiter die VORGABE und nicht
     * eine beliebige Staffel. Dass die Rechnung auch mit anderen Zahlen stimmt, prueft
     * {@link PreisstaffelTest}.
     */
    private static final Wirtschaftsstand VORGABE =
            new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

    private SpeicherLager lager;
    private Kasse kasse;
    private Besitzbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(REICH);
        buch = new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse, VORGABE);
    }

    /**
     * Die Mitte der Parzelle n auf einer Achse - bei Raster 64 + 7 liegt Parzelle 1 auf
     * 71..134.
     */
    private static int mitte(int parzelle) {
        return parzelle * 71 + 32;
    }

    /**
     * Eine Reihe aus {@code felder} Parzellen von 0/0 nach Osten, zu einer Gruppe
     * verbunden.
     */
    private void reiheAlsGruppe(int felder) {
        for (int i = 0; i < felder; i++) {
            lager.anlegen(WELT, i, 0, anna, 0L);
        }
        for (int i = 0; i < felder - 1; i++) {
            assertEquals(ERTEILT, buch.zusammenlegen(WELT, anna, mitte(i), 32,
                    Richtung.OST, VIELE).bescheid(), "Feld " + i + " mit " + (i + 1));
        }
    }

    /** Ein Buch, dessen Spieler genau so viel Geld haben wie angegeben. */
    private Besitzbuch buchMitGuthaben(long guthaben) {
        kasse = new SpeicherKasse(guthaben);
        return new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse, VORGABE);
    }

    // --- Herausloesen -----------------------------------------------------------

    @Test
    @DisplayName("Ein Randfeld loest sich, das Grundstueck bleibt")
    void randfeldLoestSich() {
        reiheAlsGruppe(3);

        var entscheid = buch.herausloesen(WELT, anna, mitte(2), 32);

        assertEquals(ERTEILT, entscheid.bescheid());
        assertFalse(lager.finde(WELT, 2, 0).orElseThrow().hatElternteil(),
                "das geloeste Feld ist eigenstaendig");
        assertEquals(2, buch.gruppenFelder(WELT, mitte(0), 32).size(),
                "die anderen zwei bleiben zusammen");
    }

    @Test
    @DisplayName("Zweimal dieselbe Richtung: die beiden sind schon dasselbe Grundstueck")
    void zweimalZusammenlegenIstKeinHindernis() {
        // Aus dem Spieltest: Ein Spieler legt zusammen, sieht die Meldung im Chatgewimmel
        // nicht und nimmt denselben Befehl noch einmal. Frueher las er dann, beide Felder
        // gehoerten "schon zu einer Gruppe" - als stuende etwas im Weg. Es ist seine.
        reiheAlsGruppe(3);

        var entscheid = buch.zusammenlegen(WELT, anna, mitte(0), 32, Richtung.OST, VIELE);

        assertEquals(SCHON_ZUSAMMEN, entscheid.bescheid());
        assertEquals(0L, entscheid.kosten(), "und es kostet nichts");
        assertEquals(3, buch.gruppenFelder(WELT, mitte(0), 32).size(), "nichts veraendert");
    }

    @Test
    @DisplayName("Zwei verschiedene Gruppen lassen sich nicht vereinigen")
    void zweiGruppenBleibenZwei() {
        // Derselbe Spieler, ein paar Sekunden spaeter: Er hat zwei Paare gebaut und will
        // sie in der Mitte verbinden. Das geht mit Absicht nicht - und die Meldung muss
        // eine andere sein als beim Fall oben, sonst sucht er den Fehler bei sich.
        for (int i = 0; i < 4; i++) {
            lager.anlegen(WELT, i, 0, anna, 0L);
        }
        assertEquals(ERTEILT,
                buch.zusammenlegen(WELT, anna, mitte(0), 32, Richtung.OST, VIELE).bescheid());
        assertEquals(ERTEILT,
                buch.zusammenlegen(WELT, anna, mitte(2), 32, Richtung.OST, VIELE).bescheid());

        var entscheid = buch.zusammenlegen(WELT, anna, mitte(1), 32, Richtung.OST, VIELE);

        assertEquals(SCHON_IN_GRUPPE, entscheid.bescheid());
        assertEquals(2, buch.gruppenFelder(WELT, mitte(0), 32).size(), "die eine bleibt");
        assertEquals(2, buch.gruppenFelder(WELT, mitte(2), 32).size(), "die andere auch");
    }

    @Test
    @DisplayName("Das tragende Feld loest sich nicht - sonst laege das Grundstueck in zwei Teilen")
    void tragendesFeldLoestSichNicht() {
        // Die Mitte einer Reihe. Ohne sie beruehren sich die Reste nicht mehr, und die
        // Fuge zwischen ihnen waere Bauland ohne Verbindung zum eigenen Grundstueck.
        reiheAlsGruppe(3);

        assertEquals(HAELT_ZUSAMMEN, buch.herausloesen(WELT, anna, mitte(1), 32).bescheid());
        assertEquals(3, buch.gruppenFelder(WELT, mitte(0), 32).size(), "nichts passiert");
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Bei zwei Feldern wird ganz aufgetrennt")
    void zweiFelderWerdenGanzAufgetrennt() {
        // Was uebrig bliebe, waere ein einzelnes Feld - und eine Gruppe aus einem Feld
        // gibt es nicht. Sie veraendert in der Welt nichts und waere nur eine Zeile, die
        // niemand mehr los wird.
        reiheAlsGruppe(2);

        assertEquals(ERTEILT, buch.herausloesen(WELT, anna, mitte(0), 32).bescheid());

        assertFalse(lager.finde(WELT, 0, 0).orElseThrow().hatElternteil());
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
        assertTrue(buch.gruppenFelder(WELT, mitte(0), 32).isEmpty(), "keine Gruppe mehr");
    }

    @Test
    @DisplayName("Ohne Gruppe gibt es nichts herauszuloesen")
    void ohneGruppeNichtsHerauszuloesen() {
        lager.anlegen(WELT, 0, 0, anna, 0L);

        assertEquals(KEINE_GRUPPE, buch.herausloesen(WELT, anna, mitte(0), 32).bescheid());
        // Auf 9/0 ist freies Bauland, und das ist NICHT "hier ist keine Parzelle":
        // Die Parzelle gibt es, sie gehoert nur noch niemandem.
        assertEquals(NOCH_FREI, buch.herausloesen(WELT, anna, mitte(9), 32).bescheid());
    }

    @Test
    @DisplayName("Freies Bauland, Weg und ausserhalb sind drei verschiedene Antworten")
    void dreierleiNichts() {
        // Frueher sagten alle drei "Hier ist keine Parzelle". Auf freiem Bauland ist das
        // falsch - /parzelle info nennt dieselbe Stelle "frei" und bietet sie zum Kauf an.
        // Wer auf denselben Block zwei verschiedene Antworten bekommt, sucht den Fehler
        // bei sich.
        assertEquals(NOCH_FREI, buch.zusammenlegen(WELT, anna, mitte(3), 32,
                Richtung.OST, VIELE).bescheid(), "freies Bauland in der Stadt");
        assertEquals(KEINE_PARZELLE, buch.zusammenlegen(WELT, anna, 67, 32,
                Richtung.OST, VIELE).bescheid(), "auf dem oeffentlichen Weg");
        assertEquals(KEINE_PARZELLE, buch.zusammenlegen(WELT, anna, mitte(100), 32,
                Richtung.OST, VIELE).bescheid(), "weit vor der Stadtgrenze");
    }

    @Test
    @DisplayName("Auf dem gefuellten Weg ist nicht entschieden, welches Feld gemeint ist")
    void aufDemWegIstNichtsEntschieden() {
        // Dieselbe Regel wie beim Zusammenlegen: Hier naehme floorDiv die kleinere
        // Nachbarzelle, und das waere womoeglich das falsche Feld.
        reiheAlsGruppe(2);

        assertEquals(AUF_DEM_WEG, buch.herausloesen(WELT, anna, 67, 32).bescheid());
        assertEquals(2, buch.gruppenFelder(WELT, mitte(0), 32).size(), "nichts passiert");
    }

    @Test
    @DisplayName("Ein Fremder loest nichts heraus")
    void fremderLoestNichtsHeraus() {
        reiheAlsGruppe(3);

        assertEquals(NICHT_EIGENTUEMER,
                buch.herausloesen(WELT, bert, mitte(2), 32).bescheid());
        assertEquals(3, buch.gruppenFelder(WELT, mitte(0), 32).size());
    }

    @Test
    @DisplayName("Das Pruefen allein aendert nichts")
    void pruefenAendertNichts() {
        reiheAlsGruppe(3);

        assertEquals(ERTEILT, buch.pruefeHerausloesen(WELT, anna, mitte(2), 32).bescheid());
        assertEquals(HAELT_ZUSAMMEN,
                buch.pruefeHerausloesen(WELT, anna, mitte(1), 32).bescheid());
        assertEquals(3, buch.gruppenFelder(WELT, mitte(0), 32).size(),
                "die Gruppe steht unveraendert");
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

        kasse.gutschreiben(anna, 1L, Buchungsgrund.PARZELLE_ERSTATTET);
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
        Besitzbuch sperrigesBuch = new Besitzbuch(Stadtgrenze.marktstadt(), new StoerLager(), kasse, VORGABE);
        sperrigesBuch.nehmen(WELT, anna, 10, 10, VIELE);
        long vorher = kasse.guthaben(anna);

        assertThrows(IllegalStateException.class,
                () -> sperrigesBuch.nehmen(WELT, anna, 100, 10, VIELE));

        assertEquals(vorher, kasse.guthaben(anna), "Gegenbuchung fehlt");
    }

    @Test
    @DisplayName("Steht die Gruppe schon, holt ein Lesefehler die Gebuehr NICHT zurueck")
    void keineGutschriftNachDemFestschreiben() {
        // Der gefaehrlichere Zwilling der Probe darueber. Dort scheitert das Schreiben,
        // und die Gegenbuchung ist richtig. Hier gelingt es - und erst der Lesezugriff
        // danach faellt aus, weil die Verbindung abreisst. Wer die Gebuehr dann
        // zurueckbucht, verschenkt ein Grundstueck, und es merkt niemand: In der Ablage
        // sieht alles richtig aus, nur bezahlt hat es keiner.
        NachDemSchreibenLager lager = new NachDemSchreibenLager();
        Besitzbuch buch = new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse, VORGABE);
        buch.nehmen(WELT, anna, mitte(0), 32, VIELE);
        buch.nehmen(WELT, anna, mitte(1), 32, VIELE);
        long vorher = kasse.guthaben(anna);

        assertThrows(IllegalStateException.class,
                () -> buch.zusammenlegen(WELT, anna, mitte(0), 32, Richtung.OST, VIELE));

        assertTrue(lager.stehtDieGruppe(WELT, 0, 0), "die Gruppe ist festgeschrieben");
        assertEquals(vorher - Preisstaffel.VORGABE.zusammenlegungsGebuehr(2),
                kasse.guthaben(anna),
                "die Gebuehr muss abgebucht bleiben - die Gruppe steht ja");
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

    // --- Teil einer Gruppe ------------------------------------------------------

    /** Ein Buch, dessen Lager jede Parzelle als Kind der Gruppe 42 ausgibt. */
    private Besitzbuch buchMitGruppenkind() {
        return new Besitzbuch(Stadtgrenze.marktstadt(), new KindLager(lager), kasse, VORGABE);
    }

    @Test
    @DisplayName("Ein Kind einer Gruppe laesst sich nicht einzeln aufgeben")
    void kindLaesstSichNichtAufgeben() {
        lager.anlegen(WELT, 0, 0, anna, 0L);
        Besitzbuch buchMitKind = buchMitGruppenkind();

        var entscheid = buchMitKind.aufgeben(WELT, anna, 10, 10);

        assertEquals(TEIL_EINER_GRUPPE, entscheid.bescheid());
        assertTrue(buchMitKind.parzelleAn(WELT, 10, 10).isPresent(),
                "Nach einer Absage darf nichts entfernt worden sein");
    }

    @Test
    @DisplayName("Ein Mitbauer auf einem Gruppenkind wird auf die Gruppe geschrieben")
    void mitbauerGehenAnDieGruppe() {
        lager.anlegen(WELT, 0, 0, anna, 0L);
        Besitzbuch buchMitKind = buchMitGruppenkind();

        // Das Kind traegt Eigentuemer und Mitbauer vollstaendig, damit darfBauen eine
        // Abfrage ohne Umweg bleibt - geschrieben wird trotzdem an der Gruppe, und von
        // dort schreibt das Lager auf alle Kinder durch. Das Lagerdoppel wirft, wenn
        // jemand stattdessen auf das Kind schreibt.
        assertEquals(ERTEILT,
                buchMitKind.mitbauerHinzufuegen(WELT, anna, 10, 10, bert).bescheid());
    }

    @Test
    @DisplayName("Auch das Austragen laeuft ueber die Gruppe")
    void mitbauerAustragenGehtAnDieGruppe() {
        lager.anlegen(WELT, 0, 0, anna, 0L);
        lager.mitbauerSetzen(lager.finde(WELT, 0, 0).orElseThrow().id(), Set.of(bert));
        Besitzbuch buchMitKind = buchMitGruppenkind();

        assertEquals(ERTEILT,
                buchMitKind.mitbauerEntfernen(WELT, anna, 10, 10, bert).bescheid());
    }

    @Test
    @DisplayName("Der Waechter kommt nach dem Eigentum - ein Fremder liest weiterhin das")
    void fremderLiestNichtEigentuemer() {
        lager.anlegen(WELT, 0, 0, anna, 0L);
        Besitzbuch buchMitKind = buchMitGruppenkind();

        // bert ist weder Eigentuemer noch darf er die Gruppe anfassen. Die Antwort soll
        // die sein, die ihn betrifft, nicht die ueber den inneren Aufbau des Besitzes.
        assertEquals(NICHT_EIGENTUEMER,
                buchMitKind.aufgeben(WELT, bert, 10, 10).bescheid());
    }

    @Test
    @DisplayName("Eine eigenstaendige Parzelle laesst sich weiterhin aufgeben")
    void eigenstaendigeGehtWeiterhin() {
        buch.nehmen(WELT, anna, 10, 10, VIELE);

        assertEquals(ERTEILT, buch.aufgeben(WELT, anna, 10, 10).bescheid());
        assertTrue(buch.parzelleAn(WELT, 10, 10).isEmpty());
    }

    /**
     * Ein Lager, das jede gefundene Parzelle als Kind einer Gruppe ausgibt.
     *
     * <p>Es gibt heute keinen Schreibweg, der eine Kindzeile erzeugt - deshalb dieses
     * Doppel. Es prueft die Regel, bevor es die Lage gibt, in der sie gebraucht wird.
     */
    private static final class KindLager implements BesitzLager {

        private static final long GRUPPE = 42L;

        private final BesitzLager echt;
        private long zuletztGeschriebenAuf = Parzelle.KEIN_ELTERNTEIL;

        private KindLager(BesitzLager echt) {
            this.echt = echt;
        }

        private static Parzelle alsKind(Parzelle p) {
            return new Parzelle(p.id(), p.welt(), p.x(), p.z(), p.eigentuemer(),
                    p.mitbauer(), p.preis(), p.erworbenAm(), p.art(), GRUPPE, p.zustand());
        }

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            return echt.finde(welt, x, z).map(KindLager::alsKind);
        }

        @Override
        public List<Parzelle> vonSpieler(String welt, UUID spieler) {
            return echt.vonSpieler(welt, spieler).stream().map(KindLager::alsKind).toList();
        }

        @Override
        public int anzahlVon(String welt, UUID spieler) {
            return echt.anzahlVon(welt, spieler);
        }

        @Override
        public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
            return alsKind(echt.anlegen(welt, x, z, eigentuemer, preis));
        }

        @Override
        public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
            // Geschrieben werden darf - aber nur auf die GRUPPE, nie auf das Kind.
            if (id != GRUPPE) {
                throw new AssertionError("Geschrieben wurde auf " + id
                        + " statt auf die Gruppe " + GRUPPE);
            }
            zuletztGeschriebenAuf = id;
        }

        @Override
        public void entfernen(long id) {
            throw new AssertionError("Ein Gruppenkind darf nie entfernt werden");
        }

        @Override
        public List<Parzelle> kinderVon(long gruppeId) {
            return echt.kinderVon(gruppeId);
        }

        @Override
        public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                                 Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
            return echt.gruppeSetzen(gruppeId, welt, eigentuemer, mitbauer, gebuehr, felder);
        }

        @Override
        public void auftrennen(long gruppeId) {
            echt.auftrennen(gruppeId);
        }

        @Override
        public void ausGruppeLoesen(long feldId) {
            echt.ausGruppeLoesen(feldId);
        }
    }

    /**
     * Ein Lager, dem der Schreibvorgang GELINGT und das erst beim Lesen danach aufgibt -
     * wie eine Datenbank, deren Verbindung zwischen Festschreiben und Nachlesen abreisst.
     *
     * <p>Das Gegenstueck zum {@link StoerLager}: Dort scheitert das Schreiben, und die
     * Gegenbuchung ist richtig. Hier ist sie falsch, denn die Gruppe steht schon.
     */
    private static final class NachDemSchreibenLager implements BesitzLager {

        private final SpeicherLager echt = new SpeicherLager();
        private boolean geschrieben;

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            if (geschrieben) {
                throw new IllegalStateException("Datenbankfehler beim Suchen einer Parzelle");
            }
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
            return echt.anlegen(welt, x, z, eigentuemer, preis);
        }

        @Override
        public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
            echt.mitbauerSetzen(id, mitbauer);
        }

        @Override
        public void entfernen(long id) {
            echt.entfernen(id);
        }

        @Override
        public List<Parzelle> kinderVon(long gruppeId) {
            if (geschrieben) {
                throw new IllegalStateException("Datenbankfehler beim Lesen einer Gruppe");
            }
            return echt.kinderVon(gruppeId);
        }

        @Override
        public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                                 Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
            long id = echt.gruppeSetzen(gruppeId, welt, eigentuemer, mitbauer, gebuehr, felder);
            // Ab hier ist sie festgeschrieben. Was jetzt kommt, darf nichts mehr zurueckholen.
            geschrieben = true;
            return id;
        }

        @Override
        public void auftrennen(long gruppeId) {
            echt.auftrennen(gruppeId);
        }

        @Override
        public void ausGruppeLoesen(long feldId) {
            echt.ausGruppeLoesen(feldId);
        }

        boolean stehtDieGruppe(String welt, int x, int z) {
            return echt.finde(welt, x, z).orElseThrow().hatElternteil();
        }
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
        public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
            echt.mitbauerSetzen(id, mitbauer);
        }

        @Override
        public void entfernen(long id) {
            echt.entfernen(id);
        }

        @Override
        public List<Parzelle> kinderVon(long gruppeId) {
            return echt.kinderVon(gruppeId);
        }

        @Override
        public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                                 Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
            // Wie die Datenbank, wenn der Wettlaufriegel greift: Ein zweiter Spieler hat
            // dasselbe Feld gerade angehaengt. Macht die Gegenbuchung pruefbar.
            throw new IllegalStateException("Feld laesst sich nicht anhaengen");
        }

        @Override
        public void auftrennen(long gruppeId) {
            echt.auftrennen(gruppeId);
        }

        @Override
        public void ausGruppeLoesen(long feldId) {
            echt.ausGruppeLoesen(feldId);
        }
    }

    @Test
    @DisplayName("Nach einem Neuladen kostet der naechste Kauf den NEUEN Preis")
    void neuerPreisWirktSofort() {
        // Die Kernzusage des Neuladens, und sie hatte keine Probe: Besitzbuch haelt den
        // Stand als final-Feld und holt den Preis erst beim Gebrauch. Wuerde es ihn im
        // Konstruktor auspacken, bliebe nach jedem /freimarken neuladen der alte Preis
        // gueltig - ein Neuladen, das Erfolg meldet und nichts aendert.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var eigenesBuch = new Besitzbuch(Stadtgrenze.marktstadt(), lager,
                new SpeicherKasse(REICH), stand);
        eigenesBuch.nehmen(WELT, anna, 10, 10, VIELE);

        assertEquals(25_000L, eigenesBuch.nehmen(WELT, anna, 100, 10, VIELE).kosten());

        stand.uebernimm(new Wirtschaftswerte(new Preisstaffel(60_000L, 10_000L),
                Wirtschaftswerte.VORGABE_STARTGUTHABEN, Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER, ""));

        // Dritte Parzelle: 60.000 mal 2 - der neue Grundpreis, nicht der alte.
        assertEquals(120_000L, eigenesBuch.nehmen(WELT, anna, 200, 10, VIELE).kosten(),
                "der Kauf muss den Stand von JETZT nehmen");
    }
}
