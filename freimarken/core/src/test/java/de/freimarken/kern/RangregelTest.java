package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welcher Rang zusteht - die Rechnung ohne Welt und ohne Uhr.
 *
 * <p>Die Messwerte stehen hier als Karte, und genau darum ist diese Probe moeglich: Im
 * Spiel kommen sie aus der Spielzeitmessung, dem Besitzbuch und der Kasse - drei Stellen,
 * die alle einen Server brauchen.
 */
@DisplayName("Die Regel der Rangleiter")
class RangregelTest {

    private static final long STUNDE = Bedingungsart.SEKUNDEN_JE_STUNDE;

    private static Map<Bedingungsart, Long> gemessen(Object... paare) {
        Map<Bedingungsart, Long> karte = new EnumMap<>(Bedingungsart.class);
        for (int stelle = 0; stelle < paare.length; stelle += 2) {
            karte.put((Bedingungsart) paare[stelle], ((Number) paare[stelle + 1]).longValue());
        }
        return karte;
    }

    @Test
    @DisplayName("Die Datei spricht Stunden, die Messung Sekunden")
    void stundenInSekunden() {
        // Die Umrechnung steht an EINER Stelle (Bedingungsart.inMesseinheit). Stuende sie
        // am Rand, haetten zwei Messer sie verschieden gemacht - und zwei Spieler mit
        // derselben Zeit verschiedene Raenge.
        var siedler = new Rangwerte.Stufe("siedler",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L), Map.of(), List.of());

        assertTrue(Rangregel.erfuellt(siedler,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 5 * STUNDE)));
        assertFalse(Rangregel.erfuellt(siedler,
                        gemessen(Bedingungsart.TAETIGE_STUNDEN, 5 * STUNDE - 1)),
                "eine Sekunde zu wenig ist zu wenig");
    }

    @Test
    @DisplayName("Eine Stufe ohne Bedingung ist immer erfuellt")
    void ohneBedingung() {
        var reisender = new Rangwerte.Stufe("reisender", Map.of(), Map.of(), List.of());

        assertTrue(Rangregel.erfuellt(reisender, Map.of()));
        assertTrue(Rangregel.erfuellt(reisender, null));
    }

    @Test
    @DisplayName("Ein fehlender Messwert zaehlt als 0 und nicht als egal")
    void fehlenderMesswert() {
        // Der Unterschied ist der ganze Riegel: "egal" verschenkte den Rang an jeden, bei
        // dem eine Messstelle gerade nichts geliefert hat.
        var siedler = new Rangwerte.Stufe("siedler",
                Map.of(Bedingungsart.PARZELLEN, 1L), Map.of(), List.of());

        assertFalse(Rangregel.erfuellt(siedler, Map.of()));
        assertFalse(Rangregel.erfuellt(siedler,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 100 * STUNDE)));
    }

    @Test
    @DisplayName("Alle Bedingungen einer Stufe muessen erfuellt sein, nicht eine")
    void alleBedingungen() {
        var buerger = new Rangwerte.Stufe("buerger",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 40L, Bedingungsart.BERUF, 1L),
                Map.of(), List.of());

        assertFalse(Rangregel.erfuellt(buerger,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 40 * STUNDE)));
        assertFalse(Rangregel.erfuellt(buerger, gemessen(Bedingungsart.BERUF, 1)));
        assertTrue(Rangregel.erfuellt(buerger,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 40 * STUNDE,
                        Bedingungsart.BERUF, 1)));
    }

    @Test
    @DisplayName("Gestiegen wird in der Reihenfolge - kein Ueberspringen")
    void keinUeberspringen() {
        // Wer die Buergerstunden hat, aber nie eine Parzelle genommen hat, bleibt
        // Reisender. Das ist keine Haerte, sondern die einzige Lesart, in der eine LEITER
        // eine Leiter ist - und wer Ueberspringen will, schreibt die untere Bedingung in
        // die obere Stufe.
        var werte = Rangwerte.VORGABE;

        var steht = Rangregel.zustehend(werte,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 100 * STUNDE,
                        Bedingungsart.BERUF, 1));

        assertEquals("reisender", steht.orElseThrow().schluessel(),
                "ohne Parzelle fehlt die Sprosse darunter");
    }

    @Test
    @DisplayName("Der hoechste erreichbare Rang gewinnt")
    void hoechsterRang() {
        var werte = Rangwerte.VORGABE;

        assertEquals("reisender", Rangregel.zustehend(werte, Map.of())
                .orElseThrow().schluessel());
        assertEquals("siedler", Rangregel.zustehend(werte,
                        gemessen(Bedingungsart.TAETIGE_STUNDEN, 5 * STUNDE,
                                Bedingungsart.PARZELLEN, 1))
                .orElseThrow().schluessel());
        assertEquals("buerger", Rangregel.zustehend(werte,
                        gemessen(Bedingungsart.TAETIGE_STUNDEN, 40 * STUNDE,
                                Bedingungsart.PARZELLEN, 1, Bedingungsart.BERUF, 1))
                .orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Abgeschaltet oder leer steht gar kein Rang zu")
    void abgeschaltet() {
        var alles = gemessen(Bedingungsart.TAETIGE_STUNDEN, 100 * STUNDE,
                Bedingungsart.PARZELLEN, 9, Bedingungsart.BERUF, 1);

        assertTrue(Rangregel.zustehend(Rangwerte.VORGABE.mitAn(false), alles).isEmpty());
        assertTrue(Rangregel.zustehend(new Rangwerte(true, List.of()), alles).isEmpty());
    }

    @Test
    @DisplayName("Der Aufstieg geht nur nach oben - kein Abstieg")
    void keinAbstieg() {
        // Sonst verloere ein Buerger seinen Rang, weil er seine Parzelle verkauft hat -
        // und mit ihm die Obergrenze, unter der sein uebriger Besitz steht.
        var werte = Rangwerte.VORGABE;
        var ohneParzelle = gemessen(Bedingungsart.TAETIGE_STUNDEN, 100 * STUNDE,
                Bedingungsart.BERUF, 1);

        assertEquals("reisender",
                Rangregel.zustehend(werte, ohneParzelle).orElseThrow().schluessel(),
                "die Rechnung selbst sagt die Wahrheit");
        assertTrue(Rangregel.aufstieg(werte, "buerger", ohneParzelle).isEmpty(),
                "der Aufstieg nimmt dem Buerger nichts weg");
    }

    @Test
    @DisplayName("Derselbe Rang ist kein Aufstieg")
    void keinAufstiegAufSichSelbst() {
        var werte = Rangwerte.VORGABE;
        var siedlerzeug = gemessen(Bedingungsart.TAETIGE_STUNDEN, 5 * STUNDE,
                Bedingungsart.PARZELLEN, 1);

        assertTrue(Rangregel.aufstieg(werte, "siedler", siedlerzeug).isEmpty());
        assertEquals("siedler", Rangregel.aufstieg(werte, "reisender", siedlerzeug)
                .orElseThrow().schluessel());
        assertEquals("reisender", Rangregel.aufstieg(werte, null, Map.of())
                .orElseThrow().schluessel(), "wer noch keinen hat, bekommt den untersten");
    }

    @Test
    @DisplayName("Ein Rang, den die Leiter nicht mehr kennt, zaehlt wie keiner")
    void unbekannterRang() {
        // Der Betreiber hat die Stufe aus der Datei genommen. Der Spieler steigt dann auf
        // das, was er heute erreicht - statt ohne jeden Rang dazustehen.
        var werte = Rangwerte.VORGABE;

        assertEquals("siedler",
                Rangregel.aufstieg(werte, "knappe",
                                gemessen(Bedingungsart.TAETIGE_STUNDEN, 5 * STUNDE,
                                        Bedingungsart.PARZELLEN, 1))
                        .orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Was fehlt, steht in der Reihenfolge der Aufzaehlung - und rechnet")
    void fehlend() {
        var buerger = new Rangwerte.Stufe("buerger",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 40L, Bedingungsart.PARZELLEN, 2L),
                Map.of(), List.of());

        var offen = Rangregel.fehlend(buerger,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 10 * STUNDE,
                        Bedingungsart.PARZELLEN, 2));

        assertEquals(1, offen.size(), offen.toString());
        assertEquals(Bedingungsart.TAETIGE_STUNDEN, offen.get(0).art());
        assertEquals(40 * STUNDE, offen.get(0).verlangt(), "verlangt in Sekunden");
        assertEquals(30 * STUNDE, offen.get(0).rest());
        assertEquals(List.of(), Rangregel.fehlend(buerger,
                        gemessen(Bedingungsart.TAETIGE_STUNDEN, 40 * STUNDE,
                                Bedingungsart.PARZELLEN, 2)),
                "erfuellt heisst: nichts fehlt");
    }

    @Test
    @DisplayName("stand nennt JEDE Bedingung - auch die erfuellte")
    void standNenntAuchErfuellte() {
        // DER UNTERSCHIED ZU fehlend(), und der einzige: Ohne die erfuellte Zeile waere
        // diese Probe auch gruen, wenn stand() nur fehlend() weiterreichte.
        var buerger = new Rangwerte.Stufe("buerger",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 10L,
                        Bedingungsart.PARZELLEN, 2L),
                Map.of(), List.of());

        var stand = Rangregel.stand(buerger,
                gemessen(Bedingungsart.TAETIGE_STUNDEN, 12 * STUNDE,
                        Bedingungsart.PARZELLEN, 1L));

        assertEquals(2, stand.size(), "beide Bedingungen, nicht nur die offene: "
                + stand);
        assertEquals(Bedingungsart.TAETIGE_STUNDEN, stand.get(0).art(),
                "die Reihenfolge kommt aus der Aufzaehlung");
        assertTrue(stand.get(0).erfuellt());
        assertEquals(10 * STUNDE, stand.get(0).verlangt(),
                "verlangt steht in der MESSEINHEIT - die Datei sprach von 10 Stunden");
        assertEquals(12 * STUNDE, stand.get(0).gemessen());
        assertEquals(0L, stand.get(0).rest());
        assertFalse(stand.get(1).erfuellt());
        assertEquals(1L, stand.get(1).rest(), "eine Parzelle fehlt noch");
    }

    @Test
    @DisplayName("stand nennt NUR, was in der Datei steht")
    void standNenntNurEingetragene() {
        // Eine Art ohne Zeile ist keine Bedingung dieser Stufe. Stuende sie mit 0 in der
        // Liste, las der Spieler eine Huerde, die es nicht gibt - und auf der hoechsten
        // Stufe fuenf davon.
        var siedler = new Rangwerte.Stufe("siedler",
                Map.of(Bedingungsart.BERUF, 1L), Map.of(), List.of());

        var stand = Rangregel.stand(siedler, gemessen(Bedingungsart.GUTHABEN, 500L));

        assertEquals(1, stand.size(), stand.toString());
        assertEquals(Bedingungsart.BERUF, stand.get(0).art());
        assertEquals(0L, stand.get(0).gemessen(),
                "ein fehlender Messwert zaehlt als 0 - wie bei fehlend()");
    }

    @Test
    @DisplayName("fehlend ist stand ohne die erfuellten - und rechnet nicht zweimal")
    void fehlendIstStandGefiltert() {
        var buerger = new Rangwerte.Stufe("buerger",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 10L,
                        Bedingungsart.PARZELLEN, 2L),
                Map.of(), List.of());
        var messwerte = gemessen(Bedingungsart.TAETIGE_STUNDEN, 12 * STUNDE,
                Bedingungsart.PARZELLEN, 1L);

        var offen = Rangregel.fehlend(buerger, messwerte);
        var alle = Rangregel.stand(buerger, messwerte);

        assertEquals(1, offen.size(), "nur die offene: " + offen);
        assertEquals(alle.get(1), offen.get(0),
                "dieselbe Zeile, dieselben Zahlen - gefiltert und nicht neu gerechnet");
    }

    @Test
    @DisplayName("Die Reihenfolge von fehlend ist FEST, nicht die einer Karte")
    void fehlendSortiert() {
        // Map.copyOf sagt keine Reihenfolge zu, und sie ist je Lauf der Maschine eine
        // andere. Eine Anzeige, die heute so und morgen anders herum steht, sieht fuer
        // den Spieler nach einem Fehler aus.
        var stufe = new Rangwerte.Stufe("alles",
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 1L,
                        Bedingungsart.ANWESENHEIT_STUNDEN, 1L,
                        Bedingungsart.PARZELLEN, 1L,
                        Bedingungsart.BERUF, 1L,
                        Bedingungsart.GUTHABEN, 1L),
                Map.of(), List.of());

        var offen = Rangregel.fehlend(stufe, Map.of());

        assertEquals(List.of(Bedingungsart.values()),
                offen.stream().map(Rangregel.Bedingungsstand::art).toList(),
                "genau die Reihenfolge der Aufzaehlung");
    }

    @Test
    @DisplayName("Gebraucht wird nur, was in der Leiter wirklich vorkommt")
    void gebraucht() {
        // Wer misst, soll nicht alles messen muessen: Steht nirgends eine Bedingung auf
        // das Guthaben, braucht der Takt die Kasse nicht zu fragen.
        assertEquals(List.of(Bedingungsart.TAETIGE_STUNDEN, Bedingungsart.PARZELLEN,
                        Bedingungsart.BERUF),
                Rangregel.gebraucht(Rangwerte.VORGABE));
        assertEquals(List.of(), Rangregel.gebraucht(Rangwerte.VORGABE.mitAn(false)));
        assertEquals(List.of(), Rangregel.gebraucht(null));
    }

    @Test
    @DisplayName("Die naechste Sprosse richtet sich nach dem AUFGESCHRIEBENEN Rang")
    void naechsteSprosse() {
        // Wer unter die Bedingung seines eigenen Ranges gefallen ist, soll nicht lesen,
        // er muesse ihn sich noch einmal verdienen.
        var werte = Rangwerte.VORGABE;

        assertEquals("siedler", Rangregel.naechste(werte, "reisender")
                .orElseThrow().schluessel());
        assertTrue(Rangregel.naechste(werte, "buerger").isEmpty());
        assertEquals("reisender", Rangregel.naechste(werte, null)
                .orElseThrow().schluessel());
        assertTrue(Rangregel.naechste(werte.mitAn(false), "reisender").isEmpty());
    }

    @Test
    @DisplayName("Ohne Stufe oder Werte gibt es nichts zu rechnen")
    void riegel() {
        // DIE UMWANDLUNG IST PFLICHT, seit es die zweite Tuer gibt [Teil G]: erfuellt
        // und fehlend nehmen eine Stufe ODER eine blosse Bedingungskarte, und eine
        // nackte null passt auf beide. Benannt ist hier die Stufe - die KARTE darf leer
        // sein, und ihre eigene Null hat ihre eigene Probe darunter.
        assertThrows(IllegalArgumentException.class,
                () -> Rangregel.erfuellt((Rangwerte.Stufe) null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Rangregel.fehlend((Rangwerte.Stufe) null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Rangregel.erfuellt((Map<Bedingungsart, Long>) null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Rangregel.fehlend((Map<Bedingungsart, Long>) null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Rangregel.zustehend(null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangregel.Bedingungsstand(null, 1L, 0L));
    }

    @Test
    @DisplayName("Nur das Guthaben ist GELD - und der Schalter zwingt die naechste Art")
    void nurGuthabenIstGeld() {
        // Die Frage gibt es, weil jeder Betrag, den ein Spieler liest, aus
        // waehrung.betrag kommt: mit Tausenderpunkt und mit dem Wort des Betreibers.
        // Vorher stand die Zahl der Bedingungszeile nackt da, und "Mark" fest in der
        // Sprachdatei - derselbe Betrag hiess zwei Zeilen hoeher "50.000 Mark".
        assertTrue(Bedingungsart.GUTHABEN.istGeld());
        for (Bedingungsart art : Bedingungsart.values()) {
            if (art != Bedingungsart.GUTHABEN) {
                assertFalse(art.istGeld(), art + " ist kein Geld und darf keinen"
                        + " Tausenderpunkt tragen");
            }
        }
    }
}
