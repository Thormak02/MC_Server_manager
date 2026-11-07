package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Zahlen des Tagesbonus und ihre Grenzen. */
@DisplayName("Die Bonuswerte")
class BonuswerteTest {

    @Test
    @DisplayName("Die Vorgabe traegt die ausgelieferte Staffel")
    void vorgabe() {
        assertEquals(500L, Bonuswerte.VORGABE.grundbetrag());
        assertEquals(100L, Bonuswerte.VORGABE.jeSerientag());
        assertEquals(2_000L, Bonuswerte.VORGABE.hoechstbetrag());
        assertEquals(2, Bonuswerte.VORGABE.reisstAbLuecke());
        assertEquals(0L, Bonuswerte.VORGABE.mindestMinuten());
        assertFalse(Bonuswerte.VORGABE.wartetAufTaetigkeit(),
                "ab Werk faellt der Bonus beim EINLOGGEN - das taegliche Einloggen soll"
                        + " belohnt werden, und eine Wartezeit davor macht aus einer"
                        + " Belohnung eine Auflage");
    }

    @Test
    @DisplayName("Der Hoechstbetrag liegt WEIT unter einem Tagesverdienst")
    void bonusErsetztKeineArbeit() {
        // Die Rechenprobe des Entwurfs: Plan 8.2 fuehrt den Bonus als die kleinste der
        // drei Quellen. Waere er so gross wie ein Arbeitstag, waere er eine zweite
        // Arbeit - und zwar eine, die gar nichts verlangt.
        assertTrue(Bonuswerte.VORGABE.hoechstbetrag()
                        <= Verdienstwerte.VORGABE_TAGESDECKEL / 5L,
                "der Hoechstbetrag " + Bonuswerte.VORGABE.hoechstbetrag()
                        + " muss hoechstens ein Fuenftel des Verdienstdeckels "
                        + Verdienstwerte.VORGABE_TAGESDECKEL + " sein");
        // Und kleiner als der Markt, der Erarbeitetes verwertet und darum mehr darf.
        assertTrue(Bonuswerte.VORGABE.hoechstbetrag() < Marktwerte.VORGABE_TAGESDECKEL,
                "der Bonus verlangt nichts und muss darum unter dem Markt bleiben");
    }

    @Test
    @DisplayName("Ein Grundbetrag von 0 kommt nicht zustande")
    void grundbetragNichtNull() {
        // Eine 0 hiesse, dass der Bonus eine Nachricht ueber 0 Mark schickt. Wer ihn
        // nicht will, soll das sagen muessen.
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitGrundbetrag(0L));
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitGrundbetrag(-1L));
        assertFalse(Bonuswerte.brauchbarerBetrag(0L));
        assertTrue(Bonuswerte.brauchbarerBetrag(1L));
        assertTrue(Bonuswerte.brauchbarerBetrag(Bonuswerte.HOECHSTER_BETRAG));
        assertFalse(Bonuswerte.brauchbarerBetrag(Bonuswerte.HOECHSTER_BETRAG + 1L));
    }

    @Test
    @DisplayName("Ein Zuschlag von 0 ist erlaubt und heisst: jeder Tag bringt dasselbe")
    void zuschlagDarfNullSein() {
        Bonuswerte flach = Bonuswerte.VORGABE.mitJeSerientag(0L);

        assertEquals(0L, flach.jeSerientag());
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitJeSerientag(-1L));
    }

    @Test
    @DisplayName("Ein Hoechstbetrag UNTER dem Grundbetrag kommt nicht zustande")
    void unmoeglichesPaar() {
        // Zwei Werte, die JEDER FUER SICH erlaubt sind: 5.000 ist ein gueltiger
        // Grundbetrag, 2.000 ein gueltiger Hoechstbetrag. Zusammen gilt der Grundbetrag
        // nie, und seine Zeile in der Datei ist eine Angabe ohne Wirkung.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Bonuswerte(5_000L, 100L, 2_000L, 2, 0L));

        assertTrue(wurf.getMessage().contains("5000")
                        && wurf.getMessage().contains("2000"),
                "der Wurf muss BEIDE Zahlen nennen: " + wurf.getMessage());
        // Gleich ist erlaubt: Dann waechst nichts, und das ist eine Einstellung.
        assertEquals(2_000L, new Bonuswerte(2_000L, 100L, 2_000L, 2, 0L).hoechstbetrag());
    }

    @Test
    @DisplayName("Die Luecke muss zur Serienregel passen")
    void lueckeAusDerSerienregel() {
        // Die Regel steht EINMAL im Kern, und beide fragen sie: dieser Satz und der
        // Leser der Datei. Eine zweite Fassung hier liefe mit der ersten auseinander.
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitLuecke(1));
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitLuecke(Serienregel.HOECHSTE_LUECKE + 1));
        assertEquals(7, Bonuswerte.VORGABE.mitLuecke(7).reisstAbLuecke());
    }

    @Test
    @DisplayName("Die Mindestzeit reicht von 0 bis zu einem Tag ohne den Tageswechsel")
    void mindestzeitImBereich() {
        assertTrue(Bonuswerte.brauchbareMindestMinuten(0L));
        assertTrue(Bonuswerte.brauchbareMindestMinuten(10L));
        assertTrue(Bonuswerte.brauchbareMindestMinuten(
                Bonuswerte.HOECHSTE_MINDEST_MINUTEN));
        assertFalse(Bonuswerte.brauchbareMindestMinuten(-1L));
        // Ueber einem Tag faellt der Bonus nach dem Tageswechsel - also nie.
        assertFalse(Bonuswerte.brauchbareMindestMinuten(
                Bonuswerte.HOECHSTE_MINDEST_MINUTEN + 1L));
        assertThrows(IllegalArgumentException.class,
                () -> Bonuswerte.VORGABE.mitMindestMinuten(-1L));
    }

    @Test
    @DisplayName("Erst eine Mindestzeit ueber 0 laesst den Bonus warten")
    void wartetErstAbEinerMinute() {
        assertFalse(Bonuswerte.VORGABE.mitMindestMinuten(0L).wartetAufTaetigkeit());
        assertTrue(Bonuswerte.VORGABE.mitMindestMinuten(1L).wartetAufTaetigkeit());
    }

    @Test
    @DisplayName("Die Abwandler lassen die anderen Werte stehen")
    void abwandlerAendernNurEins() {
        Bonuswerte andere = Bonuswerte.VORGABE.mitMindestMinuten(15L);

        assertEquals(15L, andere.mindestMinuten());
        assertEquals(Bonuswerte.VORGABE.grundbetrag(), andere.grundbetrag());
        assertEquals(Bonuswerte.VORGABE.jeSerientag(), andere.jeSerientag());
        assertEquals(Bonuswerte.VORGABE.hoechstbetrag(), andere.hoechstbetrag());
        assertEquals(Bonuswerte.VORGABE.reisstAbLuecke(), andere.reisstAbLuecke());
    }

    @Test
    @DisplayName("Die Pfade liegen im Abschnitt tagesbonus")
    void pfade() {
        for (String pfad : new String[] {Bonuswerte.PFAD_GRUNDBETRAG,
                                         Bonuswerte.PFAD_JE_SERIENTAG,
                                         Bonuswerte.PFAD_HOECHSTBETRAG,
                                         Bonuswerte.PFAD_LUECKE,
                                         Bonuswerte.PFAD_MINDEST_MINUTEN}) {
            assertTrue(pfad.startsWith("tagesbonus."),
                    pfad + " gehoert in den Abschnitt tagesbonus");
        }
    }
}
