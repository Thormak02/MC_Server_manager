package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Ziehung - welcher Wurf welchen Gewinn trifft.
 *
 * <p>Alle Wuerfe kommen hier als Zahl herein, genau wie im Programm: Ein Zufall in diesen
 * Proben waere eine Probe, die manchmal gruen ist.
 */
@DisplayName("Losregel")
class LosregelTest {

    /** Drei Gewinne mit den Gewichten 10, 20 und 30 - Summe 60. */
    private static Lotteriewerte dreiGewinne() {
        return werteMit(List.of(
                new Lotteriewerte.Gewinn("a-klein", Lotteriewerte.Art.MARK, 10, 50L, "",
                        ""),
                new Lotteriewerte.Gewinn("b-mittel", Lotteriewerte.Art.MARK, 20, 100L, "",
                        ""),
                new Lotteriewerte.Gewinn("c-gross", Lotteriewerte.Art.MARK, 30, 200L, "",
                        "")));
    }

    private static Lotteriewerte werteMit(List<Lotteriewerte.Gewinn> gewinne) {
        return new Lotteriewerte(true, 1, 20, 500L, 200, 250_000L, gewinne);
    }

    @Test
    @DisplayName("Jeder Wurf trifft seinen Abschnitt - die Raender genau")
    void wurfTrifftAbschnitt() {
        Lotteriewerte werte = dreiGewinne();

        // Sortiert nach Schluessel: a-klein (10), b-mittel (20), c-gross (30).
        // Die GRENZEN sind das Wichtige: Ein Vergleich mit <= statt < verschiebt jeden
        // Abschnitt um eins, und mitten im Abschnitt faellt das nie auf.
        assertEquals("a-klein", Losregel.ziehe(werte, 0L).orElseThrow().schluessel());
        assertEquals("a-klein", Losregel.ziehe(werte, 9L).orElseThrow().schluessel());
        assertEquals("b-mittel", Losregel.ziehe(werte, 10L).orElseThrow().schluessel());
        assertEquals("b-mittel", Losregel.ziehe(werte, 29L).orElseThrow().schluessel());
        assertEquals("c-gross", Losregel.ziehe(werte, 30L).orElseThrow().schluessel());
        assertEquals("c-gross", Losregel.ziehe(werte, 59L).orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Die REIHENFOLGE der Liste aendert die Ziehung nicht")
    void reihenfolgeIstEgal() {
        // DIE tragende Probe dieser Klasse. Gezogen wird aus der sortierten Tabelle, und
        // zwar weil die Reihenfolge der Liste aus ConfigurationSection.getKeys kommt -
        // dass Bukkit sie erhaelt, sagt Bukkit nirgends zu. Ohne die Sortierung gaebe
        // derselbe Wurf nach einem Bukkit-Umbau einen anderen Gewinn.
        //
        // Geprueft wird mit der UMGEKEHRTEN Liste: Sie unterscheidet sich von der oberen
        // in GENAU der Sache, um die es geht, und in keiner anderen.
        List<Lotteriewerte.Gewinn> umgekehrt =
                new ArrayList<>(dreiGewinne().gewinne());
        java.util.Collections.reverse(umgekehrt);
        Lotteriewerte andersSortiert = werteMit(umgekehrt);

        assertEquals("c-gross",
                andersSortiert.gewinne().get(0).schluessel(),
                "die Liste muss WIRKLICH umgedreht sein, sonst prueft der Satz nichts");

        for (long wurf = 0L; wurf < 60L; wurf++) {
            assertEquals(Losregel.ziehe(dreiGewinne(), wurf).orElseThrow().schluessel(),
                    Losregel.ziehe(andersSortiert, wurf).orElseThrow().schluessel(),
                    "Wurf " + wurf + " traf in der umgedrehten Liste einen anderen Gewinn");
        }
    }

    @Test
    @DisplayName("Jeder Gewinn ist von irgendeinem Wurf erreichbar")
    void jederGewinnIstErreichbar() {
        // Die Gegenprobe zur Abschnittsrechnung: Ein Fehler in der laufenden Summe kann
        // eine Zeile unerreichbar machen, und eine Probe, die nur einzelne Wuerfe prueft,
        // sieht das nicht.
        Lotteriewerte werte = dreiGewinne();
        List<String> getroffen = new ArrayList<>();

        for (long wurf = 0L; wurf < werte.gewichtsumme(); wurf++) {
            String schluessel = Losregel.ziehe(werte, wurf).orElseThrow().schluessel();
            if (!getroffen.contains(schluessel)) {
                getroffen.add(schluessel);
            }
        }

        assertEquals(3, getroffen.size(), "getroffen wurden: " + getroffen);
    }

    @Test
    @DisplayName("Ein Wurf ausserhalb der Summe gibt LEER und wirft nicht")
    void wurfAusserhalb() {
        // Kein Wurf, sondern leer: Diese Regel laeuft im Hauptfaden. Der Aufrufer darf
        // dann nichts abbuchen - das prueft LotteriebuchTest.
        Lotteriewerte werte = dreiGewinne();

        assertEquals(Optional.empty(), Losregel.ziehe(werte, 60L));
        assertEquals(Optional.empty(), Losregel.ziehe(werte, -1L));
        assertEquals(Optional.empty(), Losregel.ziehe(werte, Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Eine Lotterie, die nicht greift, zieht nichts")
    void greiftNicht() {
        assertEquals(Optional.empty(), Losregel.ziehe(dreiGewinne().mitAn(false), 0L));
        assertEquals(Optional.empty(), Losregel.ziehe(werteMit(List.of()), 0L));
    }

    @Test
    @DisplayName("Ohne Werte ist das ein Wurf - das ist ein Programmfehler")
    void ohneWerte() {
        assertThrows(IllegalArgumentException.class, () -> Losregel.ziehe(null, 0L));
    }

    @Test
    @DisplayName("Promille: der Anteil am Ganzen, nicht am Rest")
    void promille() {
        Lotteriewerte werte = dreiGewinne();

        // 10 von 60 sind 166 Promille, 20 von 60 sind 333, 30 von 60 sind 500.
        assertEquals(166L, Losregel.promilleVon(werte, "a-klein"));
        assertEquals(333L, Losregel.promilleVon(werte, "b-mittel"));
        assertEquals(500L, Losregel.promilleVon(werte, "c-gross"));
    }

    @Test
    @DisplayName("Ein unbekannter Schluessel hat 0 Promille")
    void promilleUnbekannt() {
        assertEquals(0L, Losregel.promilleVon(dreiGewinne(), "gibt-es-nicht"));
        assertEquals(0L, Losregel.promilleVon(null, "a-klein"));
    }

    @Test
    @DisplayName("Der seltenste Gewinn hat nicht 0 Promille - darum Promille und nicht Prozent")
    void seltensterIstSichtbar() {
        // Der Grund fuer die Einheit: Das Meeresherz der Vorgabe hat Gewicht 2 von 1000.
        // Als PROZENTzahl stuende dort eine 0 - und eine 0 fuer den wertvollsten Gewinn
        // der Tabelle verbirgt genau das, was der Betreiber sehen soll.
        long promille = Losregel.promilleVon(Lotteriewerte.VORGABE, "meeresherz");

        assertTrue(promille > 0L,
                "der seltenste Gewinn der Vorgabe muss im Bericht sichtbar sein, nicht 0");
        assertEquals(0L, promille * 100L / 1000L,
                "als Prozentzahl waere er 0 - genau deshalb steht er in Promille da");
    }

    @Test
    @DisplayName("Der mittlere Markgewinn rechnet gewichtet - nicht als Durchschnitt")
    void mittlererMarkgewinn() {
        // Gewichtet: (10*50 + 20*100 + 30*200) / 60 = (500 + 2000 + 6000) / 60 = 141.
        // Ein ungewichteter Durchschnitt waere (50+100+200)/3 = 116 - deutlich daneben,
        // und zwar zu klein. Eine Lotterie koennte damit als Senke erscheinen, die eine
        // Quelle ist.
        assertEquals(141L, Losregel.mittlererMarkgewinn(dreiGewinne()));
    }

    @Test
    @DisplayName("Gegenstaende und Goldmark zaehlen beim Mittelwert NICHT mit")
    void nurMarkZaehlt() {
        // Eine benannte Luecke: Was ein Diamant in Mark wert ist, weiss nur der Markt.
        // Geprueft wird, dass sie wirklich besteht - sonst stuende im Bericht eine Zahl,
        // die Stueckzahlen mit Mark addiert.
        Lotteriewerte werte = werteMit(List.of(
                new Lotteriewerte.Gewinn("mark", Lotteriewerte.Art.MARK, 1, 100L, "", ""),
                new Lotteriewerte.Gewinn("gold", Lotteriewerte.Art.GOLDMARK, 1, 9_999L,
                        "", ""),
                new Lotteriewerte.Gewinn("ding", Lotteriewerte.Art.GEGENSTAND, 1, 64L,
                        "diamond", "")));

        assertEquals(33L, Losregel.mittlererMarkgewinn(werte),
                "100 von 3 Gewichten - die 9999 Goldmark und die 64 Diamanten sind keine"
                        + " Mark");
    }

    @Test
    @DisplayName("Der Mittelwert laeuft nie ueber - er bleibt bei der Hoechstzahl stehen")
    void mittelwertLaeuftNichtUeber() {
        // Jede Zeile fuer sich ist gedeckelt, die SUMME ueber die Tabelle nicht. Eine
        // uebergelaufene Summe waere NEGATIV und saehe aus wie eine Lotterie, die Geld
        // einsammelt - dieselbe Falle wie bei Preisstaffel.gesamtpreisFuer.
        List<Lotteriewerte.Gewinn> viele = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            viele.add(new Lotteriewerte.Gewinn("gross-" + i, Lotteriewerte.Art.MARK,
                    Lotteriewerte.HOECHSTES_GEWICHT,
                    Lotteriewerte.Gewinn.HOECHSTER_GELDGEWINN, "", ""));
        }

        long mittel = Losregel.mittlererMarkgewinn(werteMit(viele));

        assertTrue(mittel > 0L, "der Mittelwert war " + mittel + " - negativ heisst"
                + " uebergelaufen, und das sieht aus wie eine Einnahme");
    }

    @Test
    @DisplayName("Eine Lotterie, die nicht greift, hat Mittelwert 0")
    void mittelwertAusgeschaltet() {
        assertEquals(0L, Losregel.mittlererMarkgewinn(dreiGewinne().mitAn(false)));
        assertEquals(0L, Losregel.mittlererMarkgewinn(null));
    }

    @Test
    @DisplayName("Ein einziger Gewinn trifft bei jedem Wurf")
    void einzigerGewinn() {
        Lotteriewerte werte = werteMit(List.of(
                new Lotteriewerte.Gewinn("einzig", Lotteriewerte.Art.MARK, 1, 50L, "",
                        "")));

        assertEquals("einzig", Losregel.ziehe(werte, 0L).orElseThrow().schluessel());
        assertEquals(Optional.empty(), Losregel.ziehe(werte, 1L),
                "bei Gewichtsumme 1 ist 1 schon ausserhalb");
    }
}
