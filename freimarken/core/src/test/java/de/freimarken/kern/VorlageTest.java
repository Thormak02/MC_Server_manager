package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Grundriss einer Bauvorlage - und wie die Stadt sie einpasst.
 *
 * <p>Die Erwartungen sind gerechnet und nicht aus {@link Stadtplan} geholt: Eine Vorlage
 * von 7 Breite und 9 Tiefe muss eine Parzelle von genau 7 mal 9 bekommen, sonst passt
 * die Datei nicht hinein und der Bauplan rechnet an ihrer Stelle.
 */
@DisplayName("Die Bauvorlagen der Stadt")
class VorlageTest {

    @Test
    @DisplayName("Ein Name wird ein Dateiname - darum wird er geprueft")
    void derName() {
        assertEquals("haus-schmal", new Vorlage("haus-schmal", 7, 14, 9).datei());

        // KEIN PFAD aus dem Namen: Ein Schraegstrich oder ein Punkt fuehrte aus dem
        // Ordner heraus - dieselbe Falle wie beim Weltnamen.
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new Vorlage("../geheim", 7, 14, 9)).getMessage().contains("passt"));
        assertThrows(IllegalArgumentException.class,
                () -> new Vorlage("haus.nbt", 7, 14, 9));
        assertThrows(IllegalArgumentException.class,
                () -> new Vorlage("Haus", 7, 14, 9));
        assertThrows(IllegalArgumentException.class, () -> new Vorlage("", 7, 14, 9));
        assertThrows(IllegalArgumentException.class, () -> new Vorlage(null, 7, 14, 9));
        assertThrows(IllegalArgumentException.class,
                () -> new Vorlage("a".repeat(Platzhalterbau.HOECHSTE_SCHLUESSELLAENGE + 1),
                        7, 14, 9));
    }

    @Test
    @DisplayName("Unter drei Bloecken hat ein Haus kein Inneres")
    void zuKlein() {
        assertThrows(IllegalArgumentException.class, () -> new Vorlage("haus", 2, 14, 9));
        assertThrows(IllegalArgumentException.class, () -> new Vorlage("haus", 7, 2, 9));
        assertThrows(IllegalArgumentException.class, () -> new Vorlage("haus", 7, 14, 2));
        // Drei geht - dieselbe Grenze wie bei einem eingetragenen Bau.
        assertEquals(3, new Vorlage("haus", 3, 3, 3).breite());
        assertEquals(Platzhalterbau.KLEINSTE_KANTE, 3,
                "die Grenze kommt von Platzhalterbau und steht nicht zweimal da");
    }

    @Test
    @DisplayName("Giebelstaendig heisst: tiefer als breit")
    void giebelstaendig() {
        assertTrue(new Vorlage("haus", 7, 14, 9).giebelstaendig(), "7 breit, 9 tief");
        assertTrue(new Vorlage("haus", 9, 14, 9).giebelstaendig(), "gleich zaehlt mit");
        assertFalse(new Vorlage("halle", 11, 14, 9).giebelstaendig(),
                "11 breit, 9 tief - das ist traufstaendig");
        assertEquals(63, new Vorlage("haus", 7, 14, 9).grundflaeche());
    }

    @Test
    @DisplayName("Die Stadt uebergeht eine Vorlage, die in kein Band passt")
    void passtInEineZeile() {
        // DIE GRENZE IST DAS BAND OHNE DEN SPIELRAUM DES BOGENS [24.11.2026]: Seit
        // die Gassen ausbiegen, braucht jedes Haus KRUMM_TIEFE Bloecke, um dem Bogen zu
        // folgen. Eine Vorlage ist ein starrer Grundriss - sie laesst sich nicht
        // duennermachen und stuende sonst in der Gasse. Die Grenze steht nur an EINER
        // Stelle, naemlich in passtInEineZeile.
        int tiefstens = Stadtplan.ZEILE_TIEFE - Stadtplan.KRUMM_TIEFE;
        assertTrue(Stadtplan.passtInEineZeile(
                new Vorlage("haus", 7, 14, tiefstens)), "genau so tief");
        assertFalse(Stadtplan.passtInEineZeile(
                new Vorlage("haus", 7, 14, tiefstens + 1)), "einen tiefer");
        // UND DAS GANZE BAND IST ZU TIEF - genau das war die Grenze bis zum Bogen.
        assertFalse(Stadtplan.passtInEineZeile(
                new Vorlage("haus", 7, 14, Stadtplan.ZEILE_TIEFE)),
                "das ganze Band laesst dem Bogen keinen Platz");
        assertFalse(Stadtplan.passtInEineZeile(null), "ohne Vorlage");

        // UND SIE WIRD WIRKLICH UEBERGANGEN, nicht nur beklagt.
        Stadtplan plan = Stadtplan.altfurt(List.of(
                new Vorlage("gut", 7, 14, 9),
                new Vorlage("zutief", 7, 14, Stadtplan.ZEILE_TIEFE + 3)));
        assertEquals(1, plan.vorlagen().size(), "nur die brauchbare bleibt");
        assertEquals("gut", plan.vorlagen().get(0).datei());
    }

    @Test
    @DisplayName("Mit Vorlagen passt die Parzelle GENAU - sonst setzt die Datei nicht")
    void parzellenPassenGenau() {
        // DREI TIEFEN BIS ZUR SCHRANKE: tiefer als ZEILE_TIEFE - KRUMM_TIEFE darf
        // eine Vorlage nicht sein, sonst stuende sie im Bogen der Gasse.
        List<Vorlage> vorlagen = List.of(
                new Vorlage("schmal", 5, 12, 7),
                new Vorlage("mittel", 7, 15, 8),
                new Vorlage("breit", 9, 16,
                        Stadtplan.ZEILE_TIEFE - Stadtplan.KRUMM_TIEFE));
        Stadtplan plan = Stadtplan.altfurt(vorlagen);

        Map<String, Vorlage> nachName = new HashMap<>();
        vorlagen.forEach(v -> nachName.put(v.datei(), v));

        assertTrue(plan.haeuser().size() > 100, "eine Stadt: " + plan.haeuser().size());
        Map<String, Integer> wieOft = new HashMap<>();
        for (Platzhalterbau bau : plan.haeuser()) {
            // JEDES Haus traegt eine Vorlage - sonst waere es gerechnet, obwohl
            // Vorlagen eingetragen sind.
            assertTrue(bau.vorlage() != null, bau.schluessel() + " hat keine Vorlage");
            Vorlage v = nachName.get(bau.vorlage());
            assertTrue(v != null, bau.schluessel() + " nennt die unbekannte Vorlage "
                    + bau.vorlage());
            wieOft.merge(bau.vorlage(), 1, Integer::sum);

            // UND DIE PARZELLE HAT GENAU DIE MASSE DER DATEI. Die Breite liegt laengs
            // der Gasse, die Tiefe in das Band hinein - welche Achse das ist, haengt an
            // der Tuerseite.
            Quader q = bau.bereich();
            boolean laengsIstX = bau.tuer() == Richtung.NORD || bau.tuer() == Richtung.SUED;
            int laengs = laengsIstX ? q.bisX() - q.vonX() + 1 : q.bisZ() - q.vonZ() + 1;
            int tief = laengsIstX ? q.bisZ() - q.vonZ() + 1 : q.bisX() - q.vonX() + 1;
            assertEquals(v.breite(), laengs, bau.schluessel() + ": die Breite muss zur"
                    + " Vorlage passen");
            assertEquals(v.tiefe(), tief, bau.schluessel() + ": die Tiefe muss zur"
                    + " Vorlage passen");
            assertEquals(v.hoehe(), q.bisY() - q.vonY() + 1, bau.schluessel()
                    + ": die Hoehe muss zur Vorlage passen");
        }
        // JEDE Vorlage kommt vor - eine, die nie gesetzt wird, waere ein Eintrag ohne
        // Wirkung.
        assertEquals(3, wieOft.size(), "alle drei Vorlagen werden gesetzt: " + wieOft);
        for (Vorlage v : vorlagen) {
            assertTrue(wieOft.getOrDefault(v.datei(), 0) > 10,
                    v.datei() + " wird nur " + wieOft.get(v.datei()) + " mal gesetzt");
        }
    }

    @Test
    @DisplayName("Ohne Vorlagen bleibt alles gerechnet - der Rueckfall wird nicht entfernt")
    void ohneVorlagen() {
        Stadtplan gerechnet = Stadtplan.altfurt();
        assertEquals(List.of(), gerechnet.vorlagen());
        for (Platzhalterbau bau : gerechnet.haeuser()) {
            assertTrue(bau.vorlage() == null, bau.schluessel() + " traegt eine Vorlage");
        }
        // Und die gerechneten Masse sind die der Tafel.
        List<Integer> breiten = new ArrayList<>();
        for (Platzhalterbau bau : gerechnet.haeuser()) {
            breiten.add(bau.front());
        }
        for (int breite : breiten) {
            assertTrue(Stadtplan.BREITEN.contains(breite), "Breite " + breite);
        }
        assertThrows(IllegalArgumentException.class, () -> new Stadtplan(2, null));
    }

    @Test
    @DisplayName("Vorlagen aendern die Haeuser, nicht die Strassen")
    void nurDieHaeuser() {
        // DIE GASSEN BLEIBEN, WO SIE SIND: Der Betreiber wollte "die wege gerne weiter
        // selber" - eine Vorlage darf die Stadt nicht umlegen.
        Stadtplan ohne = Stadtplan.altfurt();
        Stadtplan mit = Stadtplan.altfurt(List.of(new Vorlage("haus", 9, 16, 11)));

        assertEquals(ohne.aussenRing(), mit.aussenRing(), "dieselbe Stadtkante");
        assertEquals(ohne.umriss(), mit.umriss(), "derselbe Umriss");
        int verschieden = 0;
        for (int r = 0; r <= ohne.aussenRing(); r += 7) {
            Stadtplan.Lage eine = ohne.lageBei(Stadtplan.MITTE_X + r,
                    Stadtplan.MITTE_Z + 8);
            Stadtplan.Lage andere = mit.lageBei(Stadtplan.MITTE_X + r,
                    Stadtplan.MITTE_Z + 8);
            if (eine != andere) {
                verschieden++;
            }
        }
        assertEquals(0, verschieden, "keine Lage aendert sich durch eine Vorlage");
    }
}
