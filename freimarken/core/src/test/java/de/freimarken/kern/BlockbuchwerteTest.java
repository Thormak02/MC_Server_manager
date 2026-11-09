package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die eine Regel darueber, was ins Blockbuch kommt - und die Vererbung je Welt. */
@DisplayName("Blockbuchwerte")
class BlockbuchwerteTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String MARKTSTADT = "freimarken:marktstadt";

    /** Ein Block, der in der Vorgabeliste steht - damit benutzen ueberhaupt wirkt. */
    private static final String TRUHE = "chest";

    private static Blockbuchwerte.Auswahl auswahl(boolean auchAbgewiesen,
                                                  Blocktat... taten) {
        Set<Blocktat> menge = EnumSet.noneOf(Blocktat.class);
        for (Blocktat tat : taten) {
            menge.add(tat);
        }
        return new Blockbuchwerte.Auswahl(menge, auchAbgewiesen);
    }

    /** Werte mit der Blockliste des Programms - Frist und Happen sind hier gleichgueltig. */
    private static Blockbuchwerte werte(Blockbuchwerte.Auswahl vorgabe,
                                        Map<String, Blockbuchwerte.Auswahl> jeWelt) {
        return new Blockbuchwerte(30, 500, vorgabe, jeWelt,
                Blockbuchwerte.VORGABE_BENUTZBAR, Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);
    }

    @Test
    @DisplayName("Nur die Taten der Liste kommen ins Buch")
    void nurDieTatenDerListe() {
        Blockbuchwerte.Auswahl nurSetzen = auswahl(true, Blocktat.SETZEN);

        assertTrue(nurSetzen.schreibt(Blocktat.SETZEN, true));
        assertFalse(nurSetzen.schreibt(Blocktat.ABBAUEN, true),
                "abbauen steht nicht in der Liste");
        assertFalse(nurSetzen.schreibt(Blocktat.BENUTZEN, true));
    }

    @Test
    @DisplayName("Ohne abgewiesen kommt nur die GELUNGENE Tat ins Buch")
    void ohneAbgewiesenNurGelungene() {
        Blockbuchwerte.Auswahl ohne = auswahl(false, Blocktat.SETZEN);

        assertTrue(ohne.schreibt(Blocktat.SETZEN, true));
        assertFalse(ohne.schreibt(Blocktat.SETZEN, false),
                "derselbe Aufruf, NUR der Ausgang ist anders - und er entscheidet");
    }

    @Test
    @DisplayName("abgewiesen schaltet keine Tat zu, die nicht in der Liste steht")
    void abgewiesenIstKeineHintertuer() {
        // Die wichtigste Verzweigung der Regel: "abgewiesen" wirkt QUER zu den Taten. Wer
        // benutzen abgewaehlt hat, will auch den abgewiesenen Truhenzugriff nicht - sonst
        // waere das Wort eine Hintertuer, die genau die teure Tat zurueckholt.
        Blockbuchwerte.Auswahl ohneBenutzen = auswahl(true, Blocktat.SETZEN,
                Blocktat.ABBAUEN);

        assertFalse(ohneBenutzen.schreibt(Blocktat.BENUTZEN, false));
        assertFalse(ohneBenutzen.schreibt(Blocktat.BENUTZEN, true));
        assertTrue(ohneBenutzen.schreibt(Blocktat.SETZEN, false),
                "bei einer Tat AUS der Liste zaehlt abgewiesen sehr wohl");
    }

    @Test
    @DisplayName("Eine leere Auswahl schreibt nichts und ist erlaubt")
    void leereAuswahlSchreibtNichts() {
        // Die leere Liste ist der Weg, eine Welt auszunehmen. Sie darf nicht werfen -
        // EnumSet.copyOf tut das bei einer leeren Sammlung, und genau daran waere der
        // Betreiber gescheitert, der den Nether ausnimmt.
        Blockbuchwerte.Auswahl keine = auswahl(true);

        assertTrue(keine.leer());
        for (Blocktat tat : Blocktat.values()) {
            assertFalse(keine.schreibt(tat, true), tat + " darf nicht geschrieben werden");
            assertFalse(keine.schreibt(tat, false));
        }
        assertEquals("[abgewiesen]", keine.wieEingetragen());
    }

    @Test
    @DisplayName("Eine Welt ohne eigene Liste erbt die Vorgabe")
    void weltOhneListeErbtDieVorgabe() {
        Blockbuchwerte werte = werte(auswahl(true, Blocktat.SETZEN),
                Map.of(MARKTSTADT, auswahl(true, Blocktat.BENUTZEN)));

        assertTrue(werte.schreibt(ALTFURT, Blocktat.SETZEN, true, "stone"),
                "altfurt hat keine eigene Liste und folgt der Vorgabe");
        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, TRUHE));
        assertTrue(werte.schreibt(MARKTSTADT, Blocktat.BENUTZEN, true, TRUHE),
                "die Marktstadt hat eine eigene Liste, und die gilt STATT der Vorgabe");
        assertFalse(werte.schreibt(MARKTSTADT, Blocktat.SETZEN, true, "stone"),
                "eine eigene Liste ersetzt die Vorgabe und ergaenzt sie nicht");
    }

    @Test
    @DisplayName("Eine Welt mit leerer Liste ist ausgenommen - trotz Vorgabe")
    void leereListeNimmtDieWeltAus() {
        Blockbuchwerte werte = werte(auswahl(true, Blocktat.SETZEN, Blocktat.ABBAUEN),
                Map.of("freimarken:nether", auswahl(false)));

        assertTrue(werte.schreibt(ALTFURT, Blocktat.ABBAUEN, true, "stone"));
        assertFalse(werte.schreibt("freimarken:nether", Blocktat.ABBAUEN, true, "stone"),
                "die leere Liste heisst \"hier nichts\" und ist kein Rueckfall auf die"
                        + " Vorgabe");
    }

    @Test
    @DisplayName("Eine unbekannte Welt bekommt die Vorgabe, nicht Schweigen")
    void unbekannteWeltBekommtDieVorgabe() {
        // Eine neu gebaute Welt soll nicht stumm ohne Buch dastehen. Das ist der ganze
        // Zweck der Vererbung.
        Blockbuchwerte werte = werte(auswahl(true, Blocktat.ABBAUEN), Map.of());

        assertTrue(werte.schreibt("freimarken:brandneu", Blocktat.ABBAUEN, true, "dirt"));
    }

    @Test
    @DisplayName("Der Weltschluessel wird kleingeschrieben verglichen")
    void weltschluesselKleingeschrieben() {
        Blockbuchwerte werte = werte(auswahl(false),
                Map.of("FREIMARKEN:ALTFURT", auswahl(true, Blocktat.SETZEN)));

        assertTrue(werte.schreibt(ALTFURT, Blocktat.SETZEN, true, "stone"),
                "World.key() liefert klein - eine gross getippte Zeile muss trotzdem"
                        + " greifen, sonst wirkt sie nie");
    }

    @Test
    @DisplayName("Beim BENUTZEN entscheidet die Blockliste mit")
    void blocklisteGiltBeimBenutzen() {
        // Ohne diese Liste schriebe jeder Rechtsklick auf Erde eine Zeile - und wer mit
        // einem Block in der Hand baut, erzeugte zwei Zeilen fuer eine Tat.
        Blockbuchwerte werte = new Blockbuchwerte(30, 500,
                auswahl(true, Blocktat.BENUTZEN), Map.of(), Set.of("chest", "barrel"), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);

        assertTrue(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "chest"));
        assertTrue(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "barrel"));
        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "dirt"),
                "derselbe Aufruf, NUR der Block ist anders - und er entscheidet");
        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, false, "dirt"),
                "auch der ABGEWIESENE Klick auf Erde bleibt draussen");
    }

    @Test
    @DisplayName("Die Blockliste gilt NUR beim Benutzen, nicht beim Setzen und Abbauen")
    void blocklisteGiltNurBeimBenutzen() {
        // Wer ein Haus abreisst, reisst Bretter ab, keine Truhen. Eine Blockliste, die
        // auch fuers Abbauen gaelte, schriebe genau die Taten nicht auf, nach denen
        // gefragt wird.
        Blockbuchwerte werte = new Blockbuchwerte(30, 500,
                auswahl(true, Blocktat.SETZEN, Blocktat.ABBAUEN, Blocktat.BENUTZEN),
                Map.of(), Set.of("chest"), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);

        assertTrue(werte.schreibt(ALTFURT, Blocktat.ABBAUEN, true, "oak_planks"));
        assertTrue(werte.schreibt(ALTFURT, Blocktat.SETZEN, true, "dirt"));
        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "oak_planks"));
    }

    @Test
    @DisplayName("Eine leere Blockliste schaltet benutzen ganz ab")
    void leereBlocklisteSchaltetBenutzenAb() {
        Blockbuchwerte werte = new Blockbuchwerte(30, 500,
                auswahl(true, Blocktat.BENUTZEN), Map.of(), Set.of(), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);

        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "chest"));
        assertFalse(werte.schreibtIrgendwo(),
                "benutzen ist eingetragen, die Blockliste ist leer - also schreibt diese"
                        + " Einstellung NICHTS, und das muss sichtbar sein");
    }

    @Test
    @DisplayName("Blocknamen werden kleingeschrieben verglichen")
    void blocknamenKleingeschrieben() {
        Blockbuchwerte werte = new Blockbuchwerte(30, 500,
                auswahl(true, Blocktat.BENUTZEN), Map.of(), Set.of("CHEST"), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);

        assertTrue(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, "chest"),
                "die Wache liefert klein - eine gross getippte Zeile muss greifen");
        assertTrue(werte.istBenutzbar("Chest"));
    }

    @Test
    @DisplayName("Ein WeltNAME als Schluessel wird abgewiesen - er traefe nie")
    void weltnameAlsSchluesselWirdAbgewiesen() {
        // DER WALL GEGEN DEN FEHLER VOM 09.11.2026. Die config.yml schreibt "altfurt",
        // die Welt traegt "freimarken:altfurt". Eine Karte nach NAMEN trifft im Ereignis
        // nie - der Vergleich mit World.key().asString() ist immer ungleich -, und das
        // Blockbuch bliebe fuer diese Welt stumm leer. Niemand koennte sagen, warum.
        //
        // Ein Wurf und keine stille Vorgabe, obwohl dieses Projekt sonst die sichere
        // Richtung waehlt: Das ist kein Vertipper des Betreibers, sondern ein Fehler im
        // Programm, an einer Naht. Er darf nie in den Betrieb kommen.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Blockbuchwerte(30, 500, auswahl(true, Blocktat.SETZEN),
                        Map.of("altfurt", auswahl(true, Blocktat.SETZEN)),
                        Set.of(), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN));

        assertTrue(wurf.getMessage().contains("altfurt"),
                "die Meldung muss den Namen nennen: " + wurf.getMessage());
        assertTrue(wurf.getMessage().contains("freimarken:altfurt"),
                "und sie muss sagen, wie es richtig waere: " + wurf.getMessage());
    }

    @Test
    @DisplayName("Ein echter Schluessel mit Namensraum geht durch")
    void echterSchluesselGehtDurch() {
        // Die andere Richtung, damit der Wall nicht zu breit ist: Was World.key()
        // wirklich liefert, muss durchkommen. Ohne diese Probe koennte der Wall jeden
        // Schluessel abweisen, und keine Probe saehe den Unterschied.
        Blockbuchwerte werte = werte(auswahl(false),
                Map.of(ALTFURT, auswahl(true, Blocktat.SETZEN)));

        assertTrue(werte.schreibt(ALTFURT, Blocktat.SETZEN, true, "stone"));
    }

    @Test
    @DisplayName("schreibtIrgendwo sagt, ob ueberhaupt etwas aufgeschrieben wird")
    void schreibtIrgendwo() {
        assertTrue(werte(auswahl(true, Blocktat.SETZEN), Map.of()).schreibtIrgendwo());
        assertFalse(werte(auswahl(true), Map.of()).schreibtIrgendwo(),
                "nur abgewiesen und keine Tat schreibt nichts");
        assertTrue(werte(auswahl(true),
                Map.of(MARKTSTADT, auswahl(true, Blocktat.BENUTZEN)))
                .schreibtIrgendwo(), "eine einzige Welt mit einer Tat genuegt");
    }

    @Test
    @DisplayName("Die Vorgabe des Programms schreibt Setzen, Abbauen und Abgewiesene")
    void vorgabeDesProgramms() {
        Blockbuchwerte werte = Blockbuchwerte.VORGABE;

        assertEquals(Blockbuchwerte.VORGABE_FRIST_TAGE, werte.fristTage());
        assertTrue(werte.schreibt(ALTFURT, Blocktat.SETZEN, true, "stone"));
        assertTrue(werte.schreibt(ALTFURT, Blocktat.ABBAUEN, false, "stone"),
                "die abgewiesenen gehoeren zur Vorgabe - sie sind der Fund, bei dem"
                        + " nichts passiert ist");
        assertFalse(werte.schreibt(ALTFURT, Blocktat.BENUTZEN, true, TRUHE),
                "benutzen fehlt mit Absicht: die haeufigste Tat, nach der niemand fragt");
        assertTrue(werte.istBenutzbar(TRUHE),
                "die Blockliste steht aber bereit - wer benutzen einschaltet, hat sie");
    }

    @Test
    @DisplayName("Eine unbrauchbare Frist oder ein unbrauchbarer Happen wird abgewiesen")
    void unbrauchbareZahlenWerdenAbgewiesen() {
        assertFalse(Blockbuchwerte.brauchbareFrist(0));
        assertFalse(Blockbuchwerte.brauchbareFrist(Blockbuchwerte.HOECHSTE_FRIST_TAGE + 1));
        assertTrue(Blockbuchwerte.brauchbareFrist(1));
        assertTrue(Blockbuchwerte.brauchbareFrist(Blockbuchwerte.HOECHSTE_FRIST_TAGE));
        assertFalse(Blockbuchwerte.brauchbarerHappen(0));
        assertFalse(Blockbuchwerte.brauchbarerHappen(
                Blockbuchwerte.HOECHSTER_HAPPEN + 1));

        assertThrows(IllegalArgumentException.class,
                () -> new Blockbuchwerte(0, 500, auswahl(true), Map.of(), Set.of(), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Blockbuchwerte(30, 0, auswahl(true), Map.of(), Set.of(), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN));
        assertThrows(IllegalArgumentException.class,
                () -> new Blockbuchwerte(30, 500, auswahl(true), Map.of(), null, Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN));
    }

    @Test
    @DisplayName("Die erlaubten Namen kommen aus der Aufzaehlung - samt abgewiesen")
    void erlaubteNamenAusDerAufzaehlung() {
        // Aus der Aufzaehlung und nicht abgeschrieben: Sonst schweigt die Klage des
        // Lesers ueber genau die Tat, die jemand neu dazubaut.
        List<String> namen = Blocktat.erlaubteNamen();

        assertEquals(Blocktat.values().length + 1, namen.size(),
                "jede Tat und das Wort abgewiesen");
        for (Blocktat tat : Blocktat.values()) {
            assertTrue(namen.contains(tat.schluessel()),
                    tat + " fehlt in der Liste der erlaubten Namen");
        }
        assertTrue(namen.contains(Blocktat.NAME_ABGEWIESEN));
    }

    @Test
    @DisplayName("abgewiesen ist KEINE Tat - sonst verloere die Zeile, was abgewiesen war")
    void abgewiesenIstKeineTat() {
        assertNull(Blocktat.ausSchluessel(Blocktat.NAME_ABGEWIESEN),
                "das Wort steht in derselben Liste, ist aber keine Tat - eine vierte"
                        + " Konstante ABGEWIESEN koennte nicht mehr sagen, WAS"
                        + " abgewiesen wurde");
    }
}
