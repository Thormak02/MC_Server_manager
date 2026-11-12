package de.freimarken.kern;

import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ANGEBOTEN;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.EIGENES_GRUNDSTUECK;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ERTEILT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.GEKAUFT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.GESTOERT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.KEINE_STADT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.KEIN_ANGEBOT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.KEIN_GRUNDSTUECK;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_DEINE_STADT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_DEIN_GRUNDSTUECK;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_EIN_FELD;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.PREIS_UNGUELTIG;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.UEBERSCHNEIDUNG;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ZU_GROSS;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ZU_WENIG_MARK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer ein Grundstueck zuschneiden darf, und wo - ohne Server, ohne Datenbank.
 *
 * <p>Gerechnet wird mit dem ausgelieferten Mass: Felder von 256 Bloecken, Zellen von 16.
 * Die Koordinaten kommen aus dem Plan und stehen nicht als Literale da - sonst braeche
 * jede Aenderung an der Feldkante ein halbes Dutzend Proben, die mit der Kante nichts zu
 * tun haben.
 */
@DisplayName("Das Grundstuecksbuch")
class GrundstuecksbuchTest {

    private static final String WELT = "freimarken:freistaedte";

    /** Ein Feld, das nicht das Ankunftsfeld ist - und nicht 1/1, damit X != Z. */
    private static final int FELD_X = 2;
    private static final int FELD_Z = 1;

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherGrundstuecke ablage;
    private SpeicherKasse kasse;
    private Grundstuecksbuch buch;
    private Parzelle annastadt;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        ablage = new SpeicherGrundstuecke();
        // REICH: Teil C2 bewegt kein Geld, aber ab C3 kauft hier jemand. Ein knappes
        // Guthaben liesse Proben scheitern, die mit Geld nichts zu tun haben.
        kasse = new SpeicherKasse(10_000_000L);
        buch = new Grundstuecksbuch(Feldgrenze.freistaedte(), lager, ablage, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        annastadt = lager.freistadtAnlegen(WELT, FELD_X, FELD_Z, anna, 400_000L,
                "annastadt");
    }

    /** Eine Ecke auf der Zelle (zx|zz) INNERHALB von Annas Feld. */
    private Blockecke ecke(int zelleImFeldX, int zelleImFeldZ) {
        Stadtfeldplan plan = buch.plan();
        return new Blockecke(
                plan.zellenAnfang(plan.ersteZelle(FELD_X) + zelleImFeldX) + 3,
                plan.zellenAnfang(plan.ersteZelle(FELD_Z) + zelleImFeldZ) + 3);
    }

    // =========================================================== Zuschneiden geht

    @Test
    @DisplayName("Die Stadtbesitzerin schneidet zu - und das Grundstueck liegt richtig")
    void zuschneidenGeht() {
        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 1));

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(6, entscheid.zellen(), "3 x 2 Zellen");
        Grundstueck neu = entscheid.grundstueck().orElseThrow();
        assertEquals(annastadt.id(), neu.stadtId(), "es haengt an IHRER Stadt");
        assertEquals(anna, neu.eigentuemer(),
                "dem Stadtbesitzer und nicht niemandem - sonst waere es gesperrt,"
                        + " bis es verkauft ist");
        assertEquals(0L, neu.preis(), "das eigene Feld zu teilen kostet nichts");
        assertEquals(1, buch.inStadt(annastadt.id()).size());
    }

    @Test
    @DisplayName("Die Reihenfolge der Ecken ist egal")
    void eckenreihenfolgeIstEgal() {
        Grundstuecksbuch.Entscheid vorwaerts =
                buch.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 1));
        assertEquals(ERTEILT, vorwaerts.bescheid());
        Zellquader erste = vorwaerts.grundstueck().orElseThrow().flaeche();
        buch.loeseAuf(WELT, anna, ecke(1, 1).x(), ecke(1, 1).z());

        Grundstuecksbuch.Entscheid rueckwaerts =
                buch.schneideZu(WELT, anna, ecke(2, 1), ecke(0, 0));

        assertEquals(ERTEILT, rueckwaerts.bescheid());
        assertEquals(erste, rueckwaerts.grundstueck().orElseThrow().flaeche(),
                "beide Richtungen ergeben DENSELBEN Zuschnitt");
    }

    @Test
    @DisplayName("Eine einzelne Zelle ist ein gueltiges Grundstueck")
    void eineZelleGeht() {
        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, anna, ecke(5, 5), ecke(5, 5));

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(1, entscheid.zellen());
        assertEquals(256L, entscheid.grundstueck().orElseThrow().flaeche().bloecke(),
                "ein Chunk");
    }

    @Test
    @DisplayName("Ein ganzes Feld als EIN Grundstueck geht - ab Werk ohne Grenze")
    void ganzesFeldGeht() {
        // Die Entscheidung fuer die Preisfreiheit aus Plan 16: Wer 400.000 Mark zahlt,
        // darf seine Stadt zuschneiden, wie er will - auch als ein Anwesen.
        int letzte = buch.plan().zellenJeKante() - 1;

        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, anna, ecke(0, 0), ecke(letzte, letzte));

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(256, entscheid.zellen());
    }

    // ============================================================== und geht nicht

    @Test
    @DisplayName("Zwei Ecken in VERSCHIEDENEN Feldern gehen nicht")
    void nichtUeberZweiFelder() {
        Stadtfeldplan plan = buch.plan();
        Blockecke imNachbarfeld = new Blockecke(
                plan.feldMitte(FELD_X + 1), plan.feldMitte(FELD_Z));

        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, anna, ecke(0, 0), imNachbarfeld);

        // Ein Zuschnitt ueber zwei Felder hat keinen Eigentuemer, sondern zwei - und
        // dazwischen liegt Leere, die niemandem gehoert.
        assertEquals(NICHT_EIN_FELD, entscheid.bescheid());
        assertTrue(buch.inStadt(annastadt.id()).isEmpty());
    }

    @Test
    @DisplayName("Eine Ecke in der LEERE geht nicht - auch wenn sie zum Feld zaehlt")
    void keineEckeInDerLeere() {
        // Der heikle Fall: feldNummer nennt in der Leere das Feld DAVOR. Beide Ecken
        // haetten damit dieselbe Feldnummer, und ohne die Bauzellen-Pruefung waere der
        // Zuschnitt erteilt - mit einer Zelle in der Leere.
        Stadtfeldplan plan = buch.plan();
        int inDerLeere = plan.feldAnfang(FELD_X) + Stadtfeldplan.FELD_KANTE + 8;

        Grundstuecksbuch.Entscheid entscheid = buch.schneideZu(WELT, anna, ecke(0, 0),
                new Blockecke(inDerLeere, ecke(0, 0).z()));

        assertEquals(NICHT_EIN_FELD, entscheid.bescheid());
    }

    @Test
    @DisplayName("Auf dem ANKUNFTSFELD schneidet niemand zu")
    void nichtAufDemAnkunftsfeld() {
        Stadtfeldplan plan = buch.plan();
        Blockecke aufDemAnkunftsfeld = new Blockecke(plan.feldMitte(0), plan.feldMitte(0));

        assertEquals(NICHT_EIN_FELD, buch.schneideZu(WELT, anna, aufDemAnkunftsfeld,
                aufDemAnkunftsfeld).bescheid());
    }

    @Test
    @DisplayName("Auf einem FREIEN Feld steht keine Stadt, die teilen koennte")
    void keineStadtAufFreiemFeld() {
        Stadtfeldplan plan = buch.plan();
        Blockecke aufFreiemFeld = new Blockecke(plan.feldMitte(5), plan.feldMitte(5));

        assertEquals(KEINE_STADT, buch.schneideZu(WELT, anna, aufFreiemFeld,
                aufFreiemFeld).bescheid());
    }

    @Test
    @DisplayName("In einer FREMDEN Stadt schneidet niemand zu")
    void nichtInFremderStadt() {
        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, bert, ecke(0, 0), ecke(1, 1));

        assertEquals(NICHT_DEINE_STADT, entscheid.bescheid());
        assertTrue(buch.inStadt(annastadt.id()).isEmpty());
    }

    @Test
    @DisplayName("Ein MITBAUER der Stadt schneidet nicht zu - nur der Besitzer")
    void mitbauerSchneidetNichtZu() {
        lager.mitbauerSetzen(annastadt.id(), java.util.Set.of(bert));

        // Mitbauen und Vergeben sind zwei Dinge: Wer bauen darf, soll nicht Flaechen
        // verkaufen koennen, die ihm nicht gehoeren.
        assertEquals(NICHT_DEINE_STADT,
                buch.schneideZu(WELT, bert, ecke(0, 0), ecke(1, 1)).bescheid());
    }

    @Test
    @DisplayName("Zwei Grundstuecke auf derselben Zelle gehen nicht")
    void keineUeberschneidung() {
        assertEquals(ERTEILT, buch.schneideZu(WELT, anna, ecke(0, 0), ecke(3, 3))
                .bescheid());

        // Um genau EINE Zelle ueberlappend - der Fall, den eine zu grosszuegige
        // Pruefung durchlaesst.
        Grundstuecksbuch.Entscheid entscheid =
                buch.schneideZu(WELT, anna, ecke(3, 3), ecke(5, 5));

        assertEquals(UEBERSCHNEIDUNG, entscheid.bescheid());
        assertEquals(1, buch.inStadt(annastadt.id()).size(), "es bleibt bei einem");
        // Und daneben geht es.
        assertEquals(ERTEILT, buch.schneideZu(WELT, anna, ecke(4, 4), ecke(5, 5))
                .bescheid());
    }

    @Test
    @DisplayName("Die Zellengrenze gilt - und die Absage nennt die wirkliche Zahl")
    void zellengrenzeGilt() {
        // Die Probe setzt ihre Zahl SELBST und verlaesst sich nicht auf die Vorgabe:
        // Die liegt bei 256, also ohne Grenze, und mit ihr waere diese Probe gruen,
        // ohne etwas zu pruefen.
        Grundstuecksbuch eng = new Grundstuecksbuch(Feldgrenze.freistaedte(), lager,
                ablage, kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(Freistadtwerte.VORGABE.mitHoechstzellen(4))));

        Grundstuecksbuch.Entscheid entscheid =
                eng.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 2));

        assertEquals(ZU_GROSS, entscheid.bescheid());
        assertEquals(9, entscheid.zellen(),
                "die Absage nennt die wirkliche Zahl - der Spieler soll wissen, wie"
                        + " weit er darueber ist");
        // Genau an der Grenze geht es: 2 x 2 = 4.
        assertEquals(ERTEILT, eng.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1))
                .bescheid());
    }

    @Test
    @DisplayName("Eine 0 bei der Zellengrenze heisst OHNE Grenze")
    void nullHeisstOhneGrenze() {
        Grundstuecksbuch ohne = new Grundstuecksbuch(Feldgrenze.freistaedte(), lager,
                ablage, kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(Freistadtwerte.VORGABE.mitHoechstzellen(0))));
        int letzte = ohne.plan().zellenJeKante() - 1;

        assertEquals(ERTEILT, ohne.schneideZu(WELT, anna, ecke(0, 0),
                ecke(letzte, letzte)).bescheid());
    }

    // ================================================================= Aufloesen

    @Test
    @DisplayName("Aufgeloest - und die Flaeche ist wieder zu haben")
    void aufloesenGeht() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 2));

        Grundstuecksbuch.Entscheid entscheid =
                buch.loeseAuf(WELT, anna, ecke(1, 1).x(), ecke(1, 1).z());

        assertEquals(ERTEILT, entscheid.bescheid());
        assertEquals(9, entscheid.zellen(), "der Entscheid nennt, was wegfaellt");
        assertTrue(buch.inStadt(annastadt.id()).isEmpty());
        assertEquals(ERTEILT, buch.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 2))
                .bescheid(), "die Flaeche ist wieder frei");
    }

    @Test
    @DisplayName("Wo kein Grundstueck liegt, ist nichts aufzuloesen")
    void nichtsAufzuloesen() {
        assertEquals(KEIN_GRUNDSTUECK,
                buch.loeseAuf(WELT, anna, ecke(0, 0).x(), ecke(0, 0).z()).bescheid());
    }

    @Test
    @DisplayName("Der STADTBESITZER loest kein fremdes Grundstueck auf")
    void stadtbesitzerLoestNichtFremdesAuf() {
        // Die tragende Zusage fuer Teil C3: Koennte der Stadtbesitzer ein verkauftes
        // Grundstueck einziehen, haette der Kaeufer fuer nichts gezahlt. Hier
        // vorbereitet und schon geprueft - die Ablage erlaubt einen anderen
        // Eigentuemer, auch wenn erst C3 ihn setzt.
        Grundstueck bertsLand = ablage.anlegen(WELT, annastadt.id(),
                Zellquader.eineZelle(buch.plan().ersteZelle(FELD_X),
                        buch.plan().ersteZelle(FELD_Z)), bert, 5_000L);

        Grundstuecksbuch.Entscheid entscheid =
                buch.loeseAuf(WELT, anna, ecke(0, 0).x(), ecke(0, 0).z());

        assertEquals(NICHT_DEIN_GRUNDSTUECK, entscheid.bescheid());
        assertEquals(1, buch.inStadt(annastadt.id()).size());
        assertEquals(bertsLand.id(), buch.inStadt(annastadt.id()).get(0).id());
    }

    // ============================================================== Stoerungen

    @Test
    @DisplayName("Eine stumme Ablage heisst GESTOERT - und nichts ist geschehen")
    void stummeAblageStoert() {
        Grundstuecksbuch stumm = new Grundstuecksbuch(Feldgrenze.freistaedte(),
                new Lagerattrappe.Stumm(), ablage, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        assertEquals(GESTOERT,
                stumm.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1)).bescheid());
        assertTrue(ablage.alle(WELT).isEmpty(), "geschrieben wurde nichts");
    }

    @Test
    @DisplayName("Ohne Welt und ohne Spieler wird nichts zugeschnitten")
    void ohneWeltUndOhneSpieler() {
        assertEquals(Grundstuecksbuch.Bescheid.KEINE_STADTWELT,
                buch.schneideZu("", anna, ecke(0, 0), ecke(1, 1)).bescheid());
        assertEquals(Grundstuecksbuch.Bescheid.KEINE_STADTWELT,
                buch.schneideZu(WELT, null, ecke(0, 0), ecke(1, 1)).bescheid());
        assertEquals(Grundstuecksbuch.Bescheid.KEINE_STADTWELT,
                buch.loeseAuf(null, anna, 0, 0).bescheid());
    }

    @Test
    @DisplayName("Ein Mass, in dem die Zellen nicht aufgehen, traegt kein Buch")
    void unpassendesMassTraegtKeinBuch() {
        // Der Riegel am engsten Verbraucher: Dieses Buch ist die einzige Stelle, die in
        // Zellen rechnet. Ein Plan mit schiefem Mass ist fuer die WELT gueltig - die
        // Barriere rechnet in Bloecken -, nur Grundstuecke kann er nicht tragen.
        Feldgrenze schief = Feldgrenze.ausKante(new Stadtfeldplan(100, 32), 1000);

        assertThrows(IllegalArgumentException.class,
                () -> new Grundstuecksbuch(schief, lager, ablage, kasse,
                        new Wirtschaftsstand(Wirtschaftswerte.VORGABE)));
    }

    // ================================================================== Auskunft

    @Test
    @DisplayName("anStelle findet das Grundstueck an einer BLOCKkoordinate")
    void anStelleFindet() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(2, 2));

        // Die Frage, die die Wache stellt: Sie hat einen Block und keine Zelle.
        Optional<Grundstueck> gefunden = buch.anStelle(WELT, ecke(1, 1).x(),
                ecke(1, 1).z());

        assertTrue(gefunden.isPresent());
        assertEquals(anna, gefunden.get().eigentuemer());
        // Und eine Zelle weiter, ausserhalb, nichts.
        assertTrue(buch.anStelle(WELT, ecke(3, 3).x(), ecke(3, 3).z()).isEmpty());
    }

    @Test
    @DisplayName("Die Liste nennt alle Grundstuecke der Stadt")
    void listeNenntAlle() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));
        buch.schneideZu(WELT, anna, ecke(4, 4), ecke(5, 5));
        buch.schneideZu(WELT, anna, ecke(8, 8), ecke(9, 9));

        List<Grundstueck> alle = buch.inStadt(annastadt.id());

        assertEquals(3, alle.size());
        assertTrue(alle.stream().allMatch(eines -> eines.gehoert(anna)));
    }

    // ============================================ Anbieten und Kaufen [Teil C3]

    /** Was der Kern meldet, wenn ein Erloes nicht ankommt - hier mitgeschrieben. */
    private final List<String> verloreneErloese = new ArrayList<>();

    /** Die Naht fuer verlorene Erloese, die alles aufschreibt. */
    private Grundstuecksbuch.VerlorenerErloes mitschrift() {
        return (wer, betrag, fehler) ->
                verloreneErloese.add(wer + " fehlen " + betrag);
    }

    /** Ein zugeschnittenes Grundstueck von Anna, angeboten zu diesem Preis. */
    private Grundstueck annasAngebot(long preis) {
        Grundstuecksbuch.Entscheid zuschnitt =
                buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));
        assertEquals(ERTEILT, zuschnitt.bescheid());
        Grundstuecksbuch.Entscheid angebot = buch.biete(WELT, anna, ecke(0, 0).x(),
                ecke(0, 0).z(), preis);
        assertEquals(ANGEBOTEN, angebot.bescheid());
        return ablage.anZelle(WELT, buch.plan().zelleVon(ecke(0, 0).x()),
                buch.plan().zelleVon(ecke(0, 0).z())).orElseThrow();
    }

    @Test
    @DisplayName("Angeboten, zurueckgezogen - und nur der Besitzer darf das")
    void anbietenUndZurueckziehen() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));
        int x = ecke(0, 0).x();
        int z = ecke(0, 0).z();

        Grundstuecksbuch.Entscheid angebot = buch.biete(WELT, anna, x, z, 7_500L);

        assertEquals(ANGEBOTEN, angebot.bescheid());
        assertEquals(7_500L, angebot.preis(), "der Entscheid nennt den Preis");
        assertEquals(1, buch.angebote(WELT).size());

        // Ein FREMDER zieht nicht zurueck und bietet nicht an.
        assertEquals(NICHT_DEIN_GRUNDSTUECK,
                buch.biete(WELT, bert, x, z, 1L).bescheid());
        assertEquals(7_500L, buch.angebote(WELT).get(0).angebotspreis(),
                "der Preis steht unveraendert");

        assertEquals(ANGEBOTEN,
                buch.biete(WELT, anna, x, z, Grundstueck.KEIN_ANGEBOT).bescheid());
        assertTrue(buch.angebote(WELT).isEmpty());
    }

    /**
     * Der Entscheid traegt das Grundstueck NACH der Schreibung.
     *
     * <p>Eine eigene Probe neben der darueber, weil sie eine andere Frage stellt: Jene
     * prueft die ABLAGE, diese den ENTSCHEID. Beide koennen einzeln falsch sein, und
     * eine von beiden war es: Der Entscheid gab das Grundstueck zurueck, wie es VORHER
     * aussah. Die Naht unterscheidet an ihm, ob angeboten oder zurueckgezogen wurde, und
     * las darum beides vertauscht - "zurueckgezogen" nach einem Angebot und umgekehrt.
     * Keine Probe der Ablage haette das gesehen.
     */
    @Test
    @DisplayName("Der Entscheid traegt das Grundstueck, wie es NACHHER ist")
    void entscheidTraegtDenNeuenStand() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));
        int x = ecke(0, 0).x();
        int z = ecke(0, 0).z();

        Grundstueck angeboten = buch.biete(WELT, anna, x, z, 7_500L)
                .grundstueck().orElseThrow();

        assertTrue(angeboten.stehtZumVerkauf(),
                "nach dem Anbieten steht es zum Verkauf - auch im Entscheid");
        assertEquals(7_500L, angeboten.angebotspreis());

        Grundstueck zurueck = buch.biete(WELT, anna, x, z, Grundstueck.KEIN_ANGEBOT)
                .grundstueck().orElseThrow();

        assertFalse(zurueck.stehtZumVerkauf(),
                "nach dem Zuruecknehmen steht es nicht mehr zum Verkauf");
        // Und alles andere ist unveraendert: Ein Wandler, der nebenbei den Besitzer
        // oder die Flaeche mitnimmt, waere an der Frage oben nicht zu merken.
        assertEquals(angeboten.id(), zurueck.id());
        assertEquals(angeboten.eigentuemer(), zurueck.eigentuemer());
        assertEquals(angeboten.flaeche(), zurueck.flaeche());
        assertEquals(angeboten.preis(), zurueck.preis());
    }

    @Test
    @DisplayName("Ein negativer Preis ist keiner")
    void negativerPreisIstKeiner() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));

        // Ein Grundstueck, fuer dessen Abnahme der Verkaeufer ZAHLT, waere eine
        // Geldquelle, die jeder beliebig oft anzapfen koennte.
        assertEquals(PREIS_UNGUELTIG,
                buch.biete(WELT, anna, ecke(0, 0).x(), ecke(0, 0).z(), -1L).bescheid());
        assertTrue(buch.angebote(WELT).isEmpty());
    }

    @Test
    @DisplayName("DAS ABNAHMEKRITERIUM: ein Spieler verkauft einem anderen ein Grundstueck")
    void dasAbnahmekriterium() {
        // Plan 15, Schritt 9. Der erste Kauf dieses Projekts, bei dem das Geld nicht
        // beim Bund landet, sondern bei einem anderen Spieler.
        annasAngebot(7_500L);
        long annaVorher = kasse.guthaben(anna);
        long bertVorher = kasse.guthaben(bert);

        Grundstuecksbuch.Entscheid kauf = buch.kaufe(WELT, bert, ecke(0, 0).x(),
                ecke(0, 0).z(), mitschrift());

        assertEquals(GEKAUFT, kauf.bescheid());
        assertEquals(7_500L, kauf.preis());
        // Das GRUNDSTUECK ist uebertragen ...
        Grundstueck danach = buch.anStelle(WELT, ecke(0, 0).x(), ecke(0, 0).z())
                .orElseThrow();
        assertEquals(bert, danach.eigentuemer());
        assertFalse(danach.stehtZumVerkauf(), "und es steht nicht mehr zum Verkauf");
        // ... und das GELD ist geflossen, in beide Richtungen.
        assertEquals(bertVorher - 7_500L, kasse.guthaben(bert), "Bert hat gezahlt");
        assertEquals(annaVorher + 7_500L, kasse.guthaben(anna), "Anna hat bekommen");
        // Negativ im Buch, weil es eine AUSGABE ist - darum auf die Summe gefragt.
        assertEquals(-7_500L, kasse.summeSeit(bert, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_GEKAUFT));
        assertEquals(7_500L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_VERKAUFT));
        assertTrue(verloreneErloese.isEmpty(), "nichts ist verloren gegangen");
    }

    @Test
    @DisplayName("Ein verschenktes Grundstueck wechselt, ohne dass Geld fliesst")
    void verschenktWechseltOhneGeld() {
        annasAngebot(0L);
        long annaVorher = kasse.guthaben(anna);
        long bertVorher = kasse.guthaben(bert);

        assertEquals(GEKAUFT, buch.kaufe(WELT, bert, ecke(0, 0).x(), ecke(0, 0).z(),
                mitschrift()).bescheid());

        assertEquals(bert, buch.anStelle(WELT, ecke(0, 0).x(), ecke(0, 0).z())
                .orElseThrow().eigentuemer());
        assertEquals(annaVorher, kasse.guthaben(anna));
        assertEquals(bertVorher, kasse.guthaben(bert));
    }

    @Test
    @DisplayName("Ohne Angebot, am eigenen Grundstueck und ohne Geld wird nicht gekauft")
    void dreiAbsagenBeimKauf() {
        buch.schneideZu(WELT, anna, ecke(0, 0), ecke(1, 1));
        int x = ecke(0, 0).x();
        int z = ecke(0, 0).z();

        // 1. Es steht nicht zum Verkauf.
        assertEquals(KEIN_ANGEBOT, buch.kaufe(WELT, bert, x, z, mitschrift())
                .bescheid());

        buch.biete(WELT, anna, x, z, 7_500L);

        // 2. Am EIGENEN wird nicht gekauft - dieselbe Sperre wie am eigenen Shop.
        assertEquals(EIGENES_GRUNDSTUECK, buch.kaufe(WELT, anna, x, z, mitschrift())
                .bescheid());

        // 3. Das Guthaben reicht nicht - und die Absage nennt den PREIS, damit der
        //    Spieler ausrechnen kann, was ihm fehlt.
        SpeicherKasse arm = new SpeicherKasse(100L);
        Grundstuecksbuch armesBuch = new Grundstuecksbuch(Feldgrenze.freistaedte(),
                lager, ablage, arm, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        Grundstuecksbuch.Entscheid zuWenig = armesBuch.kaufe(WELT, bert, x, z,
                mitschrift());
        assertEquals(ZU_WENIG_MARK, zuWenig.bescheid());
        assertEquals(7_500L, zuWenig.preis());
        assertEquals(100L, arm.guthaben(bert), "und sein Geld ist noch da");
        assertEquals(anna, buch.anStelle(WELT, x, z).orElseThrow().eigentuemer(),
                "das Grundstueck gehoert weiter Anna");
    }

    @Test
    @DisplayName("Scheitert der Besitzerwechsel, kommt das Geld des Kaeufers ZURUECK")
    void gescheiterterWechselErstattet() {
        // SCHRITT 2 der Reihenfolge. Das Geld des Kaeufers ist dann schon weg - und muss
        // zurueck, sonst zahlt er fuer nichts. Genau dieser Fall tritt im Betrieb ein,
        // wenn zwei Kaeufer gleichzeitig zugreifen: Der zweite laeuft am Riegel auf.
        Grundstuecksattrappe stumm = new Grundstuecksattrappe.StummBeimWechsel();
        Grundstueck angeboten = stumm.anlegen(WELT, annastadt.id(),
                Zellquader.eineZelle(buch.plan().zelleVon(ecke(0, 0).x()),
                        buch.plan().zelleVon(ecke(0, 0).z())), anna, 0L);
        stumm.anbieten(angeboten.id(), 7_500L);
        Grundstuecksbuch stummesBuch = new Grundstuecksbuch(Feldgrenze.freistaedte(),
                lager, stumm, kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        long bertVorher = kasse.guthaben(bert);

        Grundstuecksbuch.Entscheid kauf = stummesBuch.kaufe(WELT, bert,
                ecke(0, 0).x(), ecke(0, 0).z(), mitschrift());

        assertEquals(GESTOERT, kauf.bescheid());
        assertEquals(bertVorher, kasse.guthaben(bert),
                "sein Geld ist zurueck - abgebucht UND erstattet");
        assertEquals(-7_500L, kasse.summeSeit(bert, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_GEKAUFT), "die Abbuchung steht im Buch");
        assertEquals(7_500L, kasse.summeSeit(bert, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_ERSTATTET), "und die Gegenbuchung auch");
        assertTrue(verloreneErloese.isEmpty(),
                "ein verlorener Erloes ist das NICHT - der Verkauf hat nie begonnen");
    }

    @Test
    @DisplayName("Scheitert der ERLOES, gilt der Kauf trotzdem - und es wird gemeldet")
    void gescheiterterErloesGiltTrotzdem() {
        // SCHRITT 3, der schlimmste Fall. Zurueckgenommen wird er NICHT: Ein Rueckweg
        // muesste den Wechsel aufheben und das Kaeufergeld zurueckbuchen, und scheitert
        // dabei das Zuruecknehmen beim Verkaeufer, haetten BEIDE Geld und niemand das
        // Grundstueck - Geld aus dem Nichts. Ein Verlust ist heilbar, eine Schoepfung
        // nicht.
        annasAngebot(7_500L);
        Kassenattrappe.StummBeimGutschreiben stummeKasse =
                new Kassenattrappe.StummBeimGutschreiben(10_000_000L);
        Grundstuecksbuch mitStummerKasse = new Grundstuecksbuch(
                Feldgrenze.freistaedte(), lager, ablage, stummeKasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        Grundstuecksbuch.Entscheid kauf = mitStummerKasse.kaufe(WELT, bert,
                ecke(0, 0).x(), ecke(0, 0).z(), mitschrift());

        assertEquals(GEKAUFT, kauf.bescheid(), "der Kauf GILT");
        assertEquals(bert, buch.anStelle(WELT, ecke(0, 0).x(), ecke(0, 0).z())
                .orElseThrow().eigentuemer(), "das Grundstueck ist uebertragen");
        assertEquals(1, verloreneErloese.size(),
                "und der Aufrufer erfaehrt es - genau EINMAL: " + verloreneErloese);
        assertTrue(verloreneErloese.get(0).contains("7500"), verloreneErloese.get(0));
    }

    @Test
    @DisplayName("Die Gebuehr wird dem Verkaeufer abgezogen - und nur ueber null")
    void gebuehrTraegtDerVerkaeufer() {
        // Abgezogen wird IMMER dem, der das Geld bekommt - nie als Aufschlag auf den
        // Preis. Der Preis im Angebot ist, was der Kaeufer zahlt.
        Grundstuecksbuch mitGebuehr = new Grundstuecksbuch(Feldgrenze.freistaedte(),
                lager, ablage, kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(Freistadtwerte.VORGABE
                                .mitVerkaufsgebuehr(5))));
        annasAngebot(1_000L);
        long annaVorher = kasse.guthaben(anna);
        long bertVorher = kasse.guthaben(bert);

        Grundstuecksbuch.Entscheid kauf = mitGebuehr.kaufe(WELT, bert, ecke(0, 0).x(),
                ecke(0, 0).z(), mitschrift());

        assertEquals(GEKAUFT, kauf.bescheid());
        assertEquals(50L, kauf.gebuehr(), "5 Prozent von 1.000");
        assertEquals(bertVorher - 1_000L, kasse.guthaben(bert),
                "der Kaeufer zahlt den PREIS, nicht Preis plus Gebuehr");
        assertEquals(annaVorher + 950L, kasse.guthaben(anna),
                "der Verkaeufer bekommt 1.000 und zahlt 50");
        assertEquals(-50L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_GEBUEHR));
    }

    @Test
    @DisplayName("Eine ABGERUNDETE Gebuehr von null erzeugt KEINE Buchungszeile")
    void abgerundeteNullOhneZeile() {
        // Bei 5 Prozent auf 19 Mark ist die Gebuehr abgerundet 0 - und eine Zeile ueber
        // 0 Mark waere eine Zeile ohne Geldbewegung. Geprueft wird die ZAHL der Zeilen
        // und nicht die Summe: Eine Summe von 0 kaeme auch bei einer Zeile heraus.
        Grundstuecksbuch mitGebuehr = new Grundstuecksbuch(Feldgrenze.freistaedte(),
                lager, ablage, kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(Freistadtwerte.VORGABE
                                .mitVerkaufsgebuehr(5))));
        annasAngebot(19L);

        Grundstuecksbuch.Entscheid kauf = mitGebuehr.kaufe(WELT, bert, ecke(0, 0).x(),
                ecke(0, 0).z(), mitschrift());

        assertEquals(0L, kauf.gebuehr());
        assertEquals(0L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.GRUNDSTUECK_GEBUEHR));
        assertTrue(kasse.verlauf(anna, 20).stream().noneMatch(
                        zeile -> zeile.grund() == Buchungsgrund.GRUNDSTUECK_GEBUEHR),
                "keine Zeile, nicht nur eine Summe von null");
    }

    @Test
    @DisplayName("Ein zweiter Kauf auf derselben Flaeche findet kein Angebot mehr")
    void keinZweiterKauf() {
        annasAngebot(1_000L);
        UUID clara = UUID.randomUUID();
        assertEquals(GEKAUFT, buch.kaufe(WELT, bert, ecke(0, 0).x(), ecke(0, 0).z(),
                mitschrift()).bescheid());
        long claraVorher = kasse.guthaben(clara);

        Grundstuecksbuch.Entscheid zweiter = buch.kaufe(WELT, clara, ecke(0, 0).x(),
                ecke(0, 0).z(), mitschrift());

        assertEquals(KEIN_ANGEBOT, zweiter.bescheid());
        assertEquals(claraVorher, kasse.guthaben(clara), "sie hat nichts gezahlt");
        assertEquals(bert, buch.anStelle(WELT, ecke(0, 0).x(), ecke(0, 0).z())
                .orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Wo nichts liegt, ist nichts zu kaufen und nichts anzubieten")
    void nichtsZuKaufen() {
        assertEquals(KEIN_GRUNDSTUECK,
                buch.kaufe(WELT, bert, ecke(0, 0).x(), ecke(0, 0).z(), mitschrift())
                        .bescheid());
        assertEquals(KEIN_GRUNDSTUECK,
                buch.biete(WELT, anna, ecke(0, 0).x(), ecke(0, 0).z(), 1L).bescheid());
    }

}
