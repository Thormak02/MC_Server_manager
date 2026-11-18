package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was an den Haken geht - die Wahl aus der Angeltabelle.
 *
 * <p>Die Probe setzt ihre Tabelle SELBST und rechnet nicht gegen die ausgelieferte: Eine
 * Probe, die an der Vorgabe haengt, prueft die Vorgabe statt der Regel - und bricht bei
 * jeder Aenderung der Tabelle ein halbes Dutzend Mal ein.
 *
 * <p><b>Die wichtigste Probe ist {@link #derWurfMeintImmerDASSELBE()}</b>: Ohne eine
 * stabile Reihenfolge haengt der Fang an der Streuung einer Abbildung, und die ist je
 * JVM-Lauf eine andere. Dann waere jede Probe hier manchmal gruen.
 */
@DisplayName("Die Wahl aus der Angeltabelle")
class FangregelTest {

    /** Drei Zeilen, Summe 10 - so lassen sich die Grenzen einzeln benennen. */
    private static final Angelwerte DREI = new Angelwerte(true, 100, Map.of(
            "aal", 5, "butt", 3, "cod", 2));

    @Test
    @DisplayName("Jeder Wurf trifft die Zeile, auf die er faellt")
    void jederWurfTrifftSeineZeile() {
        // Sortiert nach Namen: aal 0..4, butt 5..7, cod 8..9. Die Grenzen stehen einzeln
        // da, weil genau dort der Fehler sitzt - ein <= statt < verschiebt jede Zeile um
        // eins, und bei einer Zeile mit Gewicht 1 verschwindet sie ganz.
        assertEquals(Optional.of("aal"), Fangregel.fang(DREI, 0, 0L));
        assertEquals(Optional.of("aal"), Fangregel.fang(DREI, 0, 4L));
        assertEquals(Optional.of("butt"), Fangregel.fang(DREI, 0, 5L));
        assertEquals(Optional.of("butt"), Fangregel.fang(DREI, 0, 7L));
        assertEquals(Optional.of("cod"), Fangregel.fang(DREI, 0, 8L));
        assertEquals(Optional.of("cod"), Fangregel.fang(DREI, 0, 9L));
    }

    @Test
    @DisplayName("JEDE Zeile ist erreichbar - auch die mit Gewicht 1")
    void jedeZeileIstErreichbar() {
        // Die Zeile mit dem kleinsten Gewicht ist die, die ein Fehler zuerst
        // verschwinden laesst - und im Spiel faellt das nie auf: Das Meeresherz hat
        // Gewicht 1, und wer es nie faengt, haelt sich fuer unglueklich.
        Angelwerte werte = new Angelwerte(true, 100, Map.of(
                "haeufig", 999, "selten", 1));
        Map<String, Integer> getroffen = new TreeMap<>();

        for (long wurf = 0L; wurf < werte.gewichtsumme(); wurf++) {
            getroffen.merge(Fangregel.fang(werte, 0, wurf).orElseThrow(), 1,
                    Integer::sum);
        }

        assertEquals(Map.of("haeufig", 999, "selten", 1), getroffen,
                "jeder Wurf genau einmal, und die Zahl je Zeile ist ihr Gewicht");
    }

    @Test
    @DisplayName("Der Wurf meint immer DASSELBE - die Reihenfolge ist sortiert")
    void derWurfMeintImmerDASSELBE() {
        // DER TRAGENDE SATZ DIESER KLASSE: Map.copyOf sagt keine Reihenfolge zu, und sie
        // ist je JVM-Lauf sogar eine ANDERE (die Abbildung streut mit einem Startwert,
        // der beim Start gewuerfelt wird). Geprueft wird darum, dass die Wahl der
        // SORTIERUNG folgt - und zwar mit zwei Tabellen, die dieselben Zeilen in
        // verschiedener Reihenfolge hereingeben.
        Map<String, Integer> eineRichtung = new LinkedHashMap<>();
        eineRichtung.put("zander", 1);
        eineRichtung.put("aal", 1);
        Map<String, Integer> andereRichtung = new LinkedHashMap<>();
        andereRichtung.put("aal", 1);
        andereRichtung.put("zander", 1);

        assertEquals(Optional.of("aal"),
                Fangregel.fang(new Angelwerte(true, 100, eineRichtung), 0, 0L));
        assertEquals(Optional.of("aal"),
                Fangregel.fang(new Angelwerte(true, 100, andereRichtung), 0, 0L));
        assertEquals(Optional.of("zander"),
                Fangregel.fang(new Angelwerte(true, 100, eineRichtung), 0, 1L));
    }

    @Test
    @DisplayName("Die Haeufigkeit entscheidet, OB die Tabelle greift")
    void dieHaeufigkeitEntscheidetZuerst() {
        Angelwerte jederFuenfte = new Angelwerte(true, 20, Map.of("aal", 1));

        assertTrue(Fangregel.fang(jederFuenfte, 0, 0L).isPresent(), "Wurf 0 von 100");
        assertTrue(Fangregel.fang(jederFuenfte, 19, 0L).isPresent(), "Wurf 19 - der"
                + " letzte, der noch greift");
        assertFalse(Fangregel.fang(jederFuenfte, 20, 0L).isPresent(), "Wurf 20 - hier"
                + " bleibt es bei Vanilla, und ein <= statt < gaebe hier einen Fang zu"
                + " viel");
        assertFalse(Fangregel.fang(jederFuenfte, 99, 0L).isPresent());
    }

    @Test
    @DisplayName("Eine ausgeschaltete, leere oder nie greifende Tabelle gibt NICHTS")
    void keineTabelleKeinFang() {
        assertFalse(Fangregel.fang(DREI.mitAn(false), 0, 0L).isPresent(),
                "der Schalter");
        assertFalse(Fangregel.fang(DREI.mitHaeufigkeit(0), 0, 0L).isPresent(),
                "Haeufigkeit 0 - der zweite Weg zu Vanilla");
        assertFalse(Fangregel.fang(DREI.mitFaengen(Map.of()), 0, 0L).isPresent(),
                "eine LEERE Tabelle ist erlaubt und heisst dasselbe");
    }

    @Test
    @DisplayName("Ein Wurf ausserhalb der Summe gibt NICHTS - und wirft nicht")
    void wurfAusserhalbDerSumme() {
        // Diese Regel laeuft im Hauptfaden bei jedem Fang. Ein Wurf, der nicht passt,
        // ist ein Fehler des Aufrufers - aber kein Grund, einen Angelzug mit einer
        // Ausnahme zu beenden. Es bleibt bei Vanilla, und das ist die Richtung, in der
        // nichts kaputtgeht.
        assertFalse(Fangregel.fang(DREI, 0, 10L).isPresent(), "genau die Summe");
        assertFalse(Fangregel.fang(DREI, 0, 1000L).isPresent());
        assertFalse(Fangregel.fang(DREI, 0, -1L).isPresent());
        assertFalse(Fangregel.fang(DREI, -1, 0L).isPresent(), "auch der Prozentwurf");
    }

    @Test
    @DisplayName("Ohne Werte wirft es - das ist ein Bauunfall, kein Betriebsfall")
    void ohneWerteWirftEs() {
        assertThrows(IllegalArgumentException.class, () -> Fangregel.fang(null, 0, 0L));
    }

    @Test
    @DisplayName("Der Anteil in Promille rechnet die HAEUFIGKEIT mit")
    void promilleRechnetDieHaeufigkeitMit() {
        // Die Zahl, die ein Spieler merkt: nicht der Anteil innerhalb der Tabelle,
        // sondern der an ALLEN Faengen. Bei jedem fuenften Griff in eine Tabelle, in
        // der eine Zeile die Haelfte hat, kommt diese Zeile auf jeden zehnten Fang.
        Angelwerte haelfte = new Angelwerte(true, 20, Map.of("aal", 1, "butt", 1));

        assertEquals(100L, Fangregel.promilleVonAllem(haelfte, "aal"));
        assertEquals(0L, Fangregel.promilleVonAllem(haelfte, "gibtsnicht"));
        assertEquals(0L, Fangregel.promilleVonAllem(haelfte.mitAn(false), "aal"),
                "und ausgeschaltet ist der Anteil null, nicht die halbe Wahrheit");
    }

    @Test
    @DisplayName("Die ausgelieferte Tabelle laesst jeden Fang vorkommen")
    void dieVorgabeIstVollstaendigErreichbar() {
        // Die EINE Probe, die absichtlich an der Vorgabe haengt - und sie prueft nicht
        // die Zahlen, sondern dass keine Zeile unerreichbar ist. Eine Zeile, die nie
        // kommt, ist eine Einstellung, die nie wirkt.
        Angelwerte werte = Angelwerte.VORGABE;
        java.util.Set<String> gesehen = new java.util.TreeSet<>();

        for (long wurf = 0L; wurf < werte.gewichtsumme(); wurf++) {
            gesehen.add(Fangregel.fang(werte, 0, wurf).orElseThrow());
        }

        assertEquals(werte.gewichte().keySet(), gesehen);
        assertTrue(gesehen.contains("heart_of_the_sea"),
                "auch das Meeresherz mit Gewicht 1 - der Reiz der ganzen Tabelle");
    }
}
