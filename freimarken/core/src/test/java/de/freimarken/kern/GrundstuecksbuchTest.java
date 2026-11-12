package de.freimarken.kern;

import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ERTEILT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.GESTOERT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.KEINE_STADT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.KEIN_GRUNDSTUECK;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_DEINE_STADT;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_DEIN_GRUNDSTUECK;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.NICHT_EIN_FELD;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.UEBERSCHNEIDUNG;
import static de.freimarken.kern.Grundstuecksbuch.Bescheid.ZU_GROSS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private Grundstuecksbuch buch;
    private Parzelle annastadt;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        ablage = new SpeicherGrundstuecke();
        buch = new Grundstuecksbuch(Feldgrenze.freistaedte(), lager, ablage,
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
                ablage, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
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
                ablage, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
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
                new Lagerattrappe.Stumm(), ablage,
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
                () -> new Grundstuecksbuch(schief, lager, ablage,
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
}
