package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wechselbuch;
import de.freimarken.kern.Wechselwerte;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zeilen der Wechselstube - die WAHL und die EINHEIT.
 *
 * <p>Welche Worte dastehen, prueft {@code BildGleichTest} zeichengleich gegen die
 * ausgelieferte Vorlage. Hier stehen zwei andere Fragen:
 * <ul>
 *   <li>WELCHER Satz zu welchem Bescheid gehoert - das entscheidet das Programm.
 *   <li>Welche EINHEIT an welcher Zahl steht. Beide Waehrungen sind im Programm ein
 *       {@code long}, und eine verwechselte Zeile zeigte "12 Mark", wo einer 12 Goldmark
 *       hat - beim Kurs des Plans das Tausendfache daneben. Darum vergleichen diese
 *       Proben das BILD und nicht Zahlen.
 * </ul>
 */
@DisplayName("Die Zeilen der Wechselstube")
class TauschtextTest {

    private static final Sprache SPRACHE = Sprachprobe.spracheAusDemJar();

    private static Wechselbuch.Entscheid entscheid(Wechselbuch.Bescheid bescheid,
                                                   long anzahl, long mark,
                                                   long nochMoeglich, long stand,
                                                   long markstand) {
        return new Wechselbuch.Entscheid(bescheid, anzahl, mark, nochMoeglich, stand,
                markstand);
    }

    @Test
    @DisplayName("Die Tafel hat fuenf Zeilen und nennt beide Kurse")
    void tafelMitOffenemRueckweg() {
        var auskunft = new Wechselbuch.Auskunft(Wechselwerte.VORGABE, 12L, 0L, 12L);

        List<Component> zeilen = Tauschtext.tafelZeilen(SPRACHE, auskunft);

        assertEquals(5, zeilen.size(), Sprachprobe.bild(zeilen));
        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.contains("12 Goldmark"), bild);
        assertTrue(bild.contains("1.000 Mark"), "der Hinwegkurs: " + bild);
        assertTrue(bild.contains("800 Mark"), "der Rueckwegkurs: " + bild);
        assertTrue(bild.contains("10.000 Mark"), "der Deckel in Mark: " + bild);
    }

    @Test
    @DisplayName("Ohne Rueckweg steht die Absage statt der Deckelzeile")
    void tafelMitGeschlossenemRueckweg() {
        // Ein EIGENER Satz und nicht "noch 0 moeglich": Ein erreichter Deckel ist morgen
        // wieder frei, ein geschlossener Rueckweg nicht. Wer das verwechselt, laesst
        // Spieler jeden Tag neu probieren.
        var zu = Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU);
        var auskunft = new Wechselbuch.Auskunft(zu, 3L, 0L, 0L);

        String bild = Sprachprobe.bild(Tauschtext.tafelZeilen(SPRACHE, auskunft));

        assertEquals(5, Tauschtext.tafelZeilen(SPRACHE, auskunft).size(), bild);
        assertTrue(bild.contains("geschlossen"), bild);
        assertFalse(bild.contains("Heute kannst du noch"), bild);
        // Die Kurse stehen trotzdem da: Der HINWEG ist offen, und ohne ihn liest ein
        // Spieler eine Wechselstube, die nichts kann.
        assertTrue(bild.contains("1.000 Mark"), bild);
    }

    @Test
    @DisplayName("Jeder Bescheid bekommt seine EIGENE Zeile - keine zweimal")
    void jederBescheidEigeneZeile() {
        // DIE PROBE, DIE EIN VERTAUSCHEN FAENGT. Dass jeder Zweig da ist, sichert der
        // Uebersetzer (Schalter ohne default). Was er NICHT sichert: dass GEKAUFT nicht
        // den Satz von VERKAUFT nimmt - und beide Zeilen saehen fuer sich richtig aus.
        Set<String> bilder = new LinkedHashSet<>();
        for (Wechselbuch.Bescheid bescheid : Wechselbuch.Bescheid.values()) {
            var antwort = entscheid(bescheid, 3L, 3_000L, 7L, 5L, 2_999L);
            String bild = Sprachprobe.bild(
                    Tauschtext.entscheidZeilen(SPRACHE, Wechselwerte.VORGABE, antwort));
            assertFalse(bild.isBlank(), bescheid + " sendet nichts");
            assertTrue(bilder.add(bild), bescheid + " benutzt eine Zeile, die es schon"
                    + " gibt: " + bild);
        }
        assertEquals(Wechselbuch.Bescheid.values().length, bilder.size());
    }

    @Test
    @DisplayName("Gekauft: die Anzahl in Goldmark, der Preis in Mark")
    void gekauftTraegtBeideEinheiten() {
        // DIE EINHEITENPROBE. Vertauscht jemand sprache.goldmark und sprache.betrag,
        // liest der Spieler "3 Mark fuer 3.000 Goldmark gekauft" - und das ist diese
        // Probe, nicht die Zahl.
        var antwort = entscheid(Wechselbuch.Bescheid.GEKAUFT, 3L, 3_000L, 0L, 3L, 7_000L);

        String bild = Sprachprobe.bild(
                Tauschtext.entscheidZeilen(SPRACHE, Wechselwerte.VORGABE, antwort));

        assertEquals("&a3 Goldmark fuer 3.000 Mark gekauft. Du hast jetzt 3 Goldmark.",
                bild);
    }

    @Test
    @DisplayName("Verkauft: die Anzahl in Goldmark, der Erloes in Mark")
    void verkauftTraegtBeideEinheiten() {
        var antwort = entscheid(Wechselbuch.Bescheid.VERKAUFT, 5L, 4_000L, 7L, 0L,
                4_000L);

        assertEquals("&a5 Goldmark fuer 4.000 Mark zurueckgewechselt. Du hast jetzt"
                        + " 0 Goldmark.",
                Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                        Wechselwerte.VORGABE, antwort)));
    }

    @Test
    @DisplayName("Zu wenig Mark: die Absage nennt den Preis UND was fehlt")
    void zuWenigMarkNenntDieLuecke() {
        // Die Luecke wird im Entscheid gerechnet, aus Preis und Stand - nicht hier. Ohne
        // sie muesste ein Spieler zwei Zahlen voneinander abziehen, um zu wissen, wie
        // weit er noch ist.
        var antwort = entscheid(Wechselbuch.Bescheid.ZU_WENIG_MARK, 3L, 3_000L, 0L, 0L,
                2_999L);

        assertEquals("&cDas kostet 3.000 Mark - dir fehlen 1 Mark.",
                Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                        Wechselwerte.VORGABE, antwort)));
    }

    @Test
    @DisplayName("Zu wenig Goldmark: beide Zahlen in GOLDMARK")
    void zuWenigGoldmark() {
        var antwort = entscheid(Wechselbuch.Bescheid.ZU_WENIG_GOLDMARK, 3L, 2_400L, 12L,
                2L, 0L);

        assertEquals("&c3 Goldmark hast du nicht - du hast 2 Goldmark.",
                Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                        Wechselwerte.VORGABE, antwort)));
    }

    @Test
    @DisplayName("Der Deckel nennt die Zahl, die noch GEHT - und nicht die abgelehnte")
    void deckelNenntDasMoegliche() {
        // DIE FALLE DIESER ZEILE: entscheid.mark() ist, was der WUNSCH gebracht haette -
        // hier 16.000 Mark fuer zwanzig Goldmark, und genau die gehen nicht. Die Markzahl
        // der Zeile kommt darum aus dem KURS, gerechnet auf das Moegliche.
        var antwort = entscheid(Wechselbuch.Bescheid.DECKEL_ERREICHT, 20L, 16_000L, 12L,
                20L, 0L);

        String bild = Sprachprobe.bild(
                Tauschtext.entscheidZeilen(SPRACHE, Wechselwerte.VORGABE, antwort));

        assertEquals("&cHeute gehen nur noch 12 Goldmark (9.600 Mark) -"
                + " der Wechseldeckel.", bild);
        assertFalse(bild.contains("16.000"),
                "die abgelehnte Summe darf nicht in der Zeile stehen: " + bild);
    }

    @Test
    @DisplayName("Ist heute nichts mehr moeglich, steht der ANDERE Deckelsatz")
    void deckelVollHatSeinenEigenenSatz() {
        // Zwei Saetze fuer denselben Bescheid, und die Wahl steht in Tauschtext: "nur
        // noch 0 Goldmark" waere die Zeile, die ein Spieler nicht versteht.
        var antwort = entscheid(Wechselbuch.Bescheid.DECKEL_ERREICHT, 1L, 800L, 0L, 5L,
                0L);

        assertEquals("&cHeute ist der Wechseldeckel von 10.000 Mark erreicht."
                        + " Morgen wieder.",
                Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                        Wechselwerte.VORGABE, antwort)));
    }

    @Test
    @DisplayName("Der Deckelsatz nennt den EINGESTELLTEN Deckel, nicht die Vorgabe")
    void deckelsatzNenntDasEingestellte() {
        // Die Zahl kommt aus den hereingegebenen Werten und nicht aus Wechselwerte.VORGABE
        // - sonst laese ein Betreiber, der den Deckel gesenkt hat, im Spiel die alte Zahl.
        var antwort = entscheid(Wechselbuch.Bescheid.DECKEL_ERREICHT, 1L, 800L, 0L, 5L,
                0L);

        String bild = Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                Wechselwerte.VORGABE.mitDeckel(5_000L), antwort));

        assertTrue(bild.contains("5.000 Mark"), bild);
        assertFalse(bild.contains("10.000"), bild);
    }

    @Test
    @DisplayName("Ein geschlossener Rueckweg und eine Stoerung sind zwei Zeilen")
    void rueckwegZuUndStoerung() {
        String zu = Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                Wechselwerte.VORGABE,
                entscheid(Wechselbuch.Bescheid.RUECKWEG_ZU, 1L, 800L, 0L, 5L, 0L)));
        String gestoert = Sprachprobe.bild(Tauschtext.entscheidZeilen(SPRACHE,
                Wechselwerte.VORGABE,
                entscheid(Wechselbuch.Bescheid.GESTOERT, 1L, 0L, 0L, 0L, 0L)));

        assertTrue(zu.contains("geschlossen"), zu);
        assertTrue(gestoert.contains("nichts passiert"),
                "eine Stoerung muss sagen, dass NICHTS geschehen ist - sonst sucht der"
                        + " Spieler sein Geld: " + gestoert);
    }
}
