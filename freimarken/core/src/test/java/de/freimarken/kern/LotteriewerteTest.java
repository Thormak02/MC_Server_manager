package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Lotteriewerte - Staffel, Grenzen und was eine Gewinnzeile sein darf.
 *
 * <p><b>Die Zahlen kommen aus der Probe und nicht aus der VORGABE</b>, wo es um die
 * Rechnung geht: Sonst bricht jede Preisaenderung ein halbes Dutzend Proben, und wer sie
 * dann nachzieht, prueft nur noch, dass die Vorgabe die Vorgabe ist.
 */
@DisplayName("Lotteriewerte")
class LotteriewerteTest {

    /** Eine Staffel mit runden Zahlen: erstes Los gratis, dann 500 und Verdopplung. */
    private static Lotteriewerte staffel() {
        return new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));
    }

    @Test
    @DisplayName("Das erste Los ist gratis, das zweite kostet den Grundpreis")
    void erstesGratis() {
        Lotteriewerte werte = staffel();

        assertEquals(0L, werte.preisFuerNaechstes(0), "noch keines gezogen - gratis");
        assertEquals(500L, werte.preisFuerNaechstes(1), "eines gezogen - der Grundpreis");
    }

    @Test
    @DisplayName("Jedes weitere Los verdoppelt - ausgeschriebene Staffel")
    void staffelVerdoppelt() {
        Lotteriewerte werte = staffel();

        // AUSGESCHRIEBEN und nicht gerechnet: Eine Probe, die ihre Erwartung mit
        // derselben Formel bildet wie das Geprueefte, faengt keinen Vorzeichenfehler und
        // keine verschobene Schleifengrenze.
        assertEquals(1_000L, werte.preisFuerNaechstes(2));
        assertEquals(2_000L, werte.preisFuerNaechstes(3));
        assertEquals(4_000L, werte.preisFuerNaechstes(4));
        assertEquals(8_000L, werte.preisFuerNaechstes(5));
        assertEquals(16_000L, werte.preisFuerNaechstes(6));
        assertEquals(32_000L, werte.preisFuerNaechstes(7));
        assertEquals(64_000L, werte.preisFuerNaechstes(8));
        assertEquals(128_000L, werte.preisFuerNaechstes(9));
    }

    @Test
    @DisplayName("Der Hoechstpreis klemmt - und bleibt geklemmt")
    void hoechstpreisKlemmt() {
        Lotteriewerte werte = staffel();

        // 256.000 waere die naechste Verdopplung; geklemmt wird auf 250.000.
        assertEquals(250_000L, werte.preisFuerNaechstes(10));
        assertEquals(250_000L, werte.preisFuerNaechstes(11));
        assertEquals(250_000L, werte.preisFuerNaechstes(100));
    }

    @Test
    @DisplayName("Auch bei tausend Losen wird der Preis nie negativ")
    void keinUeberlauf() {
        // Die Falle, gegen die der Deckel in JEDER Runde klemmt: Wer erst
        // durchmultipliziert und danach klemmt, hat bei genug Runden einen
        // uebergelaufenen und damit NEGATIVEN Preis - und ein negativer Preis ist eine
        // Gutschrift beim Kauf. Geprueft mit dem groessten erlaubten Deckel und der
        // groessten erlaubten Steigerung, also genau dort, wo es brechen muesste.
        Lotteriewerte werte = new Lotteriewerte(true, 0, 0, 1L,
                Lotteriewerte.GROESSTE_STEIGERUNG, Lotteriewerte.HOECHSTER_HOECHSTPREIS,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));

        for (int lose = 0; lose <= 1_000; lose++) {
            long preis = werte.preisFuerNaechstes(lose);
            assertTrue(preis >= 0L,
                    "bei " + lose + " Losen war der Preis " + preis);
            assertTrue(preis <= Lotteriewerte.HOECHSTER_HOECHSTPREIS,
                    "bei " + lose + " Losen war der Preis " + preis);
        }
    }

    @Test
    @DisplayName("Steigerung 100 heisst: jedes Los kostet gleich viel")
    void steigerungHundert() {
        Lotteriewerte werte = new Lotteriewerte(true, 1, 0, 300L, 100, 300L,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));

        assertEquals(0L, werte.preisFuerNaechstes(0));
        assertEquals(300L, werte.preisFuerNaechstes(1));
        assertEquals(300L, werte.preisFuerNaechstes(9));
    }

    @Test
    @DisplayName("Ohne gratis Los kostet schon das erste den Grundpreis")
    void ohneGratisLos() {
        Lotteriewerte werte = new Lotteriewerte(true, 0, 0, 500L, 200, 250_000L,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));

        assertEquals(500L, werte.preisFuerNaechstes(0), "keines gratis - sofort bezahlt");
        assertEquals(1_000L, werte.preisFuerNaechstes(1));
    }

    @Test
    @DisplayName("Drei gratis Lose verschieben die ganze Staffel")
    void dreiGratisLose() {
        // Die Schleifengrenze haengt an freieLoseJeTag, nicht an einer festen 1 - das ist
        // die Stelle, an der ein abgeschriebener Preisstaffel-Code falsch waere.
        Lotteriewerte werte = new Lotteriewerte(true, 3, 0, 500L, 200, 250_000L,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));

        assertEquals(0L, werte.preisFuerNaechstes(0));
        assertEquals(0L, werte.preisFuerNaechstes(1));
        assertEquals(0L, werte.preisFuerNaechstes(2));
        assertEquals(500L, werte.preisFuerNaechstes(3), "das vierte ist das erste bezahlte");
        assertEquals(1_000L, werte.preisFuerNaechstes(4));
    }

    @Test
    @DisplayName("Eine negative Loszahl ist ein Wurf und keine 0")
    void negativeLoszahl() {
        assertThrows(IllegalArgumentException.class,
                () -> staffel().preisFuerNaechstes(-1));
    }

    @Test
    @DisplayName("Die Tagesgrenze sagt, wann Schluss ist - 0 heisst unbegrenzt")
    void tagesgrenze() {
        Lotteriewerte mitGrenze = staffel();
        assertTrue(mitGrenze.nochEinsHeute(0));
        assertTrue(mitGrenze.nochEinsHeute(19));
        assertFalse(mitGrenze.nochEinsHeute(20), "zwanzig sind erlaubt, das 21. nicht");
        assertFalse(mitGrenze.nochEinsHeute(100));

        Lotteriewerte ohneGrenze = new Lotteriewerte(true, 1, 0, 500L, 200, 250_000L,
                List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 10L,
                        "", "")));
        assertTrue(ohneGrenze.nochEinsHeute(1_000_000), "0 heisst unbegrenzt");
    }

    @Test
    @DisplayName("Die Gewichtsumme ist der Nenner jeder Ziehung")
    void gewichtsumme() {
        Lotteriewerte werte = new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L,
                List.of(
                        new Lotteriewerte.Gewinn("a", Lotteriewerte.Art.MARK, 7, 10L, "", ""),
                        new Lotteriewerte.Gewinn("b", Lotteriewerte.Art.MARK, 3, 10L, "", ""),
                        new Lotteriewerte.Gewinn("c", Lotteriewerte.Art.MARK, 90, 10L, "",
                                "")));

        assertEquals(100L, werte.gewichtsumme());
    }

    @Test
    @DisplayName("Eine leere Tabelle und ein ausgeschalteter Schalter greifen beide nicht")
    void greiftNicht() {
        assertFalse(new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L, List.of()).greift(),
                "ohne Gewinne gibt es nichts zu ziehen");
        assertFalse(staffel().mitAn(false).greift(), "ausgeschaltet ist ausgeschaltet");
        assertTrue(staffel().greift());
    }

    @Test
    @DisplayName("Die Staffel im Klartext nennt die gratis Lose als gratis")
    void staffelAlsText() {
        // Die Rechenprobe des Berichts. Sie ist die einzige Schranke gegen 50 statt 500 -
        // keine Zahlengrenze faengt das, beide sind plausibel.
        assertEquals("1. gratis - 2. 500 - 3. 1000 - 4. 2000 - 5. 4000",
                staffel().staffelAlsText(5));
    }

    @Test
    @DisplayName("Eine Tagesgrenze unter der Zahl der gratis Lose wird abgewiesen")
    void grenzeUnterGratis() {
        // Sonst waere ein Teil der gratis Lose nie zu ziehen, und niemand erfuehre,
        // welcher. Der Leser faengt das vorher ab und klagt; hier ist der letzte Riegel.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte(true, 5, 3, 500L, 200, 250_000L,
                        List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK,
                                1, 10L, "", ""))));
        assertTrue(wurf.getMessage().contains("nie zu ziehen"), wurf.getMessage());
    }

    @Test
    @DisplayName("Ein Hoechstpreis unter dem Grundpreis wird abgewiesen")
    void deckelUnterGrundpreis() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte(true, 1, 20, 500L, 200, 100L,
                        List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK,
                                1, 10L, "", ""))));
    }

    @Test
    @DisplayName("Eine Steigerung unter 100 Prozent wird abgewiesen - sie waere ein Rabatt")
    void steigerungUnterHundert() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte(true, 1, 20, 500L, 99, 250_000L,
                        List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK,
                                1, 10L, "", ""))));
        assertTrue(wurf.getMessage().contains("BILLIGER"), wurf.getMessage());
    }

    @Test
    @DisplayName("Ein Deckel ueber der Rechengrenze wird abgewiesen")
    void deckelUeberDerRechengrenze() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte(true, 1, 20, 500L, 200,
                        Lotteriewerte.HOECHSTER_HOECHSTPREIS + 1L,
                        List.of(new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK,
                                1, 10L, "", ""))));
    }

    @Test
    @DisplayName("Zwei Gewinne mit demselben Schluessel werden abgewiesen")
    void doppelterSchluessel() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L, List.of(
                        new Lotteriewerte.Gewinn("a", Lotteriewerte.Art.MARK, 1, 10L, "",
                                ""),
                        new Lotteriewerte.Gewinn("a", Lotteriewerte.Art.MARK, 5, 20L, "",
                                ""))));
        assertTrue(wurf.getMessage().contains("zweimal"), wurf.getMessage());
    }

    @Test
    @DisplayName("Die Tabelle im Satz ist eine KOPIE - keine Hintertuer in den Stand")
    void tabelleIstKopie() {
        List<Lotteriewerte.Gewinn> veraenderlich = new ArrayList<>();
        veraenderlich.add(new Lotteriewerte.Gewinn("a", Lotteriewerte.Art.MARK, 1, 10L,
                "", ""));
        Lotteriewerte werte = new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L,
                veraenderlich);

        veraenderlich.add(new Lotteriewerte.Gewinn("b", Lotteriewerte.Art.MARK, 999, 10L,
                "", ""));

        assertEquals(1, werte.gewinne().size(),
                "der Satz wird bei JEDER Ziehung gelesen - eine veraenderliche Liste"
                        + " darin waere eine Hintertuer in den Stand");
        assertEquals(1L, werte.gewichtsumme());
    }

    @Test
    @DisplayName("Ein Gegenstandsgewinn ohne Gegenstand wird abgewiesen")
    void gegenstandOhneNamen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.GEGENSTAND, 1, 1L,
                        "", ""));
    }

    @Test
    @DisplayName("Ein Geldgewinn MIT Gegenstand wird abgewiesen - eine Angabe waere stumm")
    void geldMitGegenstand() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.MARK, 1, 10L,
                        "diamond", ""));
    }

    @Test
    @DisplayName("Ein Geldgewinn mit Anzeigenamen wird abgewiesen")
    void geldMitNamen() {
        // Ein Betrag hat keinen Namen, auf dem er stehen koennte. Ohne diesen Riegel
        // stuende der Name in der Datei und waere im Spiel nie zu sehen.
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.GOLDMARK, 1, 3L,
                        "", "Goldschatz"));
    }

    @Test
    @DisplayName("Mehr Stueck als ein Rucksack fasst wird abgewiesen")
    void zuVieleStueck() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.GEGENSTAND, 1,
                        Lotteriewerte.Gewinn.HOECHSTE_STUECKZAHL + 1L, "diamond", ""));
        // Und genau ein voller Rucksack geht noch.
        assertEquals(Lotteriewerte.Gewinn.HOECHSTE_STUECKZAHL,
                new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.GEGENSTAND, 1,
                        Lotteriewerte.Gewinn.HOECHSTE_STUECKZAHL, "diamond", "").menge());
    }

    @Test
    @DisplayName("Ein Geldgewinn ueber der Hausgrenze wird abgewiesen")
    void zuVielGeld() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.MARK, 1,
                        Lotteriewerte.Gewinn.HOECHSTER_GELDGEWINN + 1L, "", ""));
    }

    @Test
    @DisplayName("Ein Gewinn von 0 wird abgewiesen - die Niete ist eine MARK-Zeile")
    void gewinnVonNichts() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.MARK, 1, 0L, "",
                        ""));
        assertTrue(wurf.getMessage().contains("Niete"), wurf.getMessage());
    }

    @Test
    @DisplayName("Ein Gewicht von 0 wird abgewiesen - die Zeile greift nie")
    void gewichtVonNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriewerte.Gewinn("x", Lotteriewerte.Art.MARK, 0, 10L, "",
                        ""));
    }

    @Test
    @DisplayName("Der Anzeigename ist der Name im Spiel - sonst der Gegenstand")
    void nameImSpiel() {
        assertEquals("Fischerhut von Altfurt",
                new Lotteriewerte.Gewinn("hut", Lotteriewerte.Art.GEGENSTAND, 1, 1L,
                        "leather_helmet", "Fischerhut von Altfurt").imSpiel());
        assertEquals("diamond",
                new Lotteriewerte.Gewinn("d", Lotteriewerte.Art.GEGENSTAND, 1, 1L,
                        "diamond", "").imSpiel());
        assertEquals("trost",
                new Lotteriewerte.Gewinn("trost", Lotteriewerte.Art.MARK, 1, 50L, "",
                        "").imSpiel());
    }

    @Test
    @DisplayName("Die ausgelieferte Lotterie ist eine SENKE und keine Quelle")
    void vorgabeIstEineSenke() {
        // Plan 9.3, und die wichtigste Zusage ueber die ausgelieferten Zahlen: Bringt ein
        // Los im Mittel mehr, als die ersten Lose kosten, ist die Lotterie eine
        // Geldmaschine - und das faellt an keiner anderen Stelle auf, weil jeder einzelne
        // Zug anders ausgeht.
        //
        // Verglichen wird mit dem Preis des DRITTEN Loses: Das erste ist gratis, das
        // zweite kostet 500, und ab dem dritten zahlt ein Vielzieher mehr, als er im
        // Mittel bekommt. Dass die ersten beiden Lose eine Gabe sind, ist gewollt.
        long mittel = Losregel.mittlererMarkgewinn(Lotteriewerte.VORGABE);
        long drittes = Lotteriewerte.VORGABE.preisFuerNaechstes(2);

        assertTrue(mittel < drittes,
                "im Mittel " + mittel + " Mark je Los, aber das dritte kostet " + drittes
                        + " - andernfalls ist die Lotterie eine Geldquelle");
    }

    @Test
    @DisplayName("Jeder Gegenstandsgewinn der Vorgabe nennt einen Gegenstand")
    void vorgabeIstVollstaendig() {
        for (Lotteriewerte.Gewinn gewinn : Lotteriewerte.VORGABE_GEWINNE) {
            if (gewinn.art() == Lotteriewerte.Art.GEGENSTAND) {
                assertFalse(gewinn.gegenstand().isBlank(),
                        gewinn.schluessel() + " ist ein Gegenstand ohne Namen");
            } else {
                assertTrue(gewinn.gegenstand().isEmpty(),
                        gewinn.schluessel() + " ist kein Gegenstand, nennt aber einen");
            }
        }
    }
}
