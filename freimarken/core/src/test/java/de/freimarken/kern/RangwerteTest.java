package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Rangleiter - was in der Datei stehen darf und was nicht. */
@DisplayName("Die Werte der Rangleiter")
class RangwerteTest {

    private static Rangwerte.Stufe stufe(String name, Map<Bedingungsart, Long> bedingungen,
                                         Map<String, Integer> zahlen, List<String> rechte) {
        return new Rangwerte.Stufe(name, bedingungen, zahlen, rechte);
    }

    @Test
    @DisplayName("Eine vollstaendige Stufe kommt vollstaendig an")
    void vollstaendig() {
        var eine = stufe("Siedler ", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                Map.of("Parzellen", 2), List.of("Laden.Mieten"));

        assertEquals("siedler", eine.schluessel(), "getrimmt und kleingeschrieben");
        assertEquals(5L, eine.bedingungen().get(Bedingungsart.TAETIGE_STUNDEN));
        assertEquals(2, eine.zahl("parzellen").orElseThrow());
        assertEquals(List.of("laden.mieten"), eine.rechte());
        assertFalse(eine.ohneBedingung());
    }

    @Test
    @DisplayName("Ohne Bedingung steht die Stufe jedem zu - so ist der unterste Rang")
    void ohneBedingung() {
        assertTrue(stufe("reisender", Map.of(), Map.of("parzellen", 1), List.of())
                .ohneBedingung());
    }

    @Test
    @DisplayName("Eine Bedingung mit 0 ist eine Zeile, die nie wirkt - und wird abgewiesen")
    void bedingungNull() {
        // Sie waere im Betrieb von einer erfuellten nicht zu unterscheiden: Der Betreiber
        // sieht eine Zeile, der Spieler steigt ohne sie auf. Wer keine Bedingung will,
        // laesst die Zeile weg.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> stufe("siedler", Map.of(Bedingungsart.PARZELLEN, 0L),
                        Map.of(), List.of()));
        assertTrue(fehler.getMessage().contains("zwischen 1 und"), fehler.getMessage());
    }

    @Test
    @DisplayName("Jede Bedingungsart hat ihre eigene Obergrenze")
    void obergrenzeJeArt() {
        // Je Art eine eigene Schranke und keine gemeinsame: 100.000 Stunden sind ein
        // Vertipper, 100.000 Mark sind es nicht. Geprueft wird GENAU an der Grenze, damit
        // die Probe nicht gruen bliebe, wenn jemand "<=" zu "<" macht.
        int geprueft = 0;
        for (Bedingungsart art : Bedingungsart.values()) {
            geprueft++;
            long hoechster = art.hoechsterWert();
            assertEquals(hoechster,
                    stufe("x", Map.of(art, hoechster), Map.of(), List.of())
                            .bedingungen().get(art),
                    art + ": der hoechste Wert muss noch gehen");
            assertThrows(IllegalArgumentException.class,
                    () -> stufe("x", Map.of(art, hoechster + 1L), Map.of(), List.of()),
                    art + ": einer darueber nicht mehr");
        }
        assertEquals(Bedingungsart.values().length, geprueft,
                "jede Art ist angesehen - eine neue faellt sonst durch dieses Netz");
    }

    @Test
    @DisplayName("Ein unbekannter Grenzname wird abgewiesen und nennt die bekannten")
    void unbekannteGrenze() {
        // Ein frei getippter Name ergaebe ein Recht, das niemand liest. Der Betreiber
        // saehe eine Zeile in der Datei und im Spiel keine Wirkung - der teuerste aller
        // stillen Fehler, weil er nach einer Einstellung aussieht.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> stufe("siedler", Map.of(), Map.of("heimatpunkte", 3), List.of()));
        assertTrue(fehler.getMessage().contains("kennt das Programm nicht"),
                fehler.getMessage());
        for (String bekannt : Rangwerte.BEKANNTE_ZAHLEN) {
            assertTrue(fehler.getMessage().contains(bekannt),
                    "die Klage muss sagen, was es gibt: " + fehler.getMessage());
        }
    }

    @Test
    @DisplayName("Ein Recht MIT Vorsilbe wird abgewiesen - mit der Loesung in der Klage")
    void rechtMitVorsilbe() {
        // Stillschweigend abzuschneiden waere schlechter: Wer "freimarken.laden.mieten"
        // tippt, hat eine Vorstellung davon, wie das Recht heisst, und die soll
        // berichtigt werden.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> stufe("siedler", Map.of(), Map.of(),
                        List.of("freimarken.laden.mieten")));
        assertTrue(fehler.getMessage().contains("laden.mieten"), fehler.getMessage());
    }

    @Test
    @DisplayName("Ein Recht mit Leerzeichen oder Grossbuchstaben wird kleingeschrieben")
    void rechtGetrimmt() {
        assertEquals(List.of("beruf.spezialisierung"),
                stufe("buerger", Map.of(), Map.of(), List.of(" Beruf.Spezialisierung "))
                        .rechte());
        assertThrows(IllegalArgumentException.class,
                () -> stufe("buerger", Map.of(), Map.of(), List.of("laden mieten")),
                "ein Leerzeichen mitten im Namen waere im Spiel ein zweites Recht");
    }

    @Test
    @DisplayName("Dasselbe Recht zweimal steht einmal in der Stufe")
    void doppeltesRecht() {
        assertEquals(List.of("laden.mieten"),
                stufe("siedler", Map.of(), Map.of(),
                        List.of("laden.mieten", "LADEN.MIETEN")).rechte());
    }

    @Test
    @DisplayName("Ein zu langer Schluessel wird abgewiesen - so lang ist die Spalte")
    void schluessellaenge() {
        String gerade = "a".repeat(Rangwerte.HOECHSTE_SCHLUESSELLAENGE);
        assertEquals(gerade, stufe(gerade, Map.of(), Map.of(), List.of()).schluessel());
        assertThrows(IllegalArgumentException.class,
                () -> stufe("a".repeat(Rangwerte.HOECHSTE_SCHLUESSELLAENGE + 1),
                        Map.of(), Map.of(), List.of()));
    }

    @Test
    @DisplayName("Zwei Stufen mit demselben Schluessel waeren EINE Zeile in der Ablage")
    void doppelteStufe() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Rangwerte(true, List.of(
                        stufe("reisender", Map.of(), Map.of(), List.of()),
                        stufe("Reisender", Map.of(Bedingungsart.PARZELLEN, 1L),
                                Map.of(), List.of()))));
        assertTrue(fehler.getMessage().contains("zweimal"), fehler.getMessage());
    }

    @Test
    @DisplayName("Die unterste Stufe darf keine Bedingung haben")
    void untersteOhneBedingung() {
        // Haette sie eine, stuende ein neuer Spieler auf KEINER Stufe - also ohne jedes
        // Recht, das die Leiter vergibt, und mit genau den Grenzen, die die config.yml
        // ohnehin setzt. Das sieht im Spiel aus wie ein kaputter Rang.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Rangwerte(true, List.of(
                        stufe("reisender", Map.of(Bedingungsart.PARZELLEN, 1L),
                                Map.of(), List.of()))));
        assertTrue(fehler.getMessage().contains("ersten Beitritt"), fehler.getMessage());
    }

    @Test
    @DisplayName("Eine LEERE Leiter ist erlaubt und heisst: keine Raenge")
    void leereLeiter() {
        var ohne = new Rangwerte(true, List.of());

        assertFalse(ohne.greift());
        assertTrue(ohne.unterste().isEmpty());
        assertEquals(List.of(), ohne.schluessel());
    }

    @Test
    @DisplayName("Abgeschaltet greift die Leiter nicht, bleibt aber lesbar")
    void abgeschaltet() {
        var aus = Rangwerte.VORGABE.mitAn(false);

        assertFalse(aus.greift());
        assertEquals(3, aus.leiter().size(), "die Zeilen stehen weiter in der Datei");
    }

    @Test
    @DisplayName("Die Leiter kennt ihre Reihenfolge: Nummer, naechste, unterste")
    void reihenfolge() {
        var werte = Rangwerte.VORGABE;

        assertEquals(0, werte.nummer("reisender"));
        assertEquals(2, werte.nummer("BUERGER"), "kleingeschrieben gefragt wie abgelegt");
        assertEquals(-1, werte.nummer("gibtesnicht"));
        assertEquals("reisender", werte.unterste().orElseThrow().schluessel());
        assertEquals("buerger", werte.naechste("siedler").orElseThrow().schluessel());
        assertTrue(werte.naechste("buerger").isEmpty(), "ueber der obersten ist nichts");
        assertEquals(List.of("reisender", "siedler", "buerger"), werte.schluessel());
    }

    @Test
    @DisplayName("Ein Rueckschritt in der Leiter wird GEMELDET, nicht geworfen")
    void ungereimtheiten() {
        // Im Spiel faellt er gar nicht auf: Rangzahl nimmt die hoechste Zahl aus Recht
        // UND Datei, ein Rang, der weniger gibt, wirkt darum einfach nicht. Ein Wurf
        // waere trotzdem falsch - er naehme dem Betreiber eine Entscheidung, die seine
        // ist.
        var schief = new Rangwerte(true, List.of(
                stufe("reisender", Map.of(), Map.of("parzellen", 4), List.of()),
                stufe("siedler", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                        Map.of("parzellen", 2), List.of())));

        var saetze = schief.ungereimtheiten();

        assertEquals(1, saetze.size(), saetze.toString());
        assertTrue(saetze.get(0).contains("wirkungslos"), saetze.get(0));
        assertTrue(saetze.get(0).contains("parzellen"), saetze.get(0));
        // UND DER SATZ MUSS STIMMEN. Bis zum 19.11.2026 stand hier "ein Aufstieg nimmt
        // hier etwas weg" - seit buendel() kumulativ sammelt, nimmt er nichts weg, er
        // hebt nur nichts mehr. Wer den alten Satz liest, sucht einen Schaden, den es
        // nicht gibt, und uebersieht den, den es gibt: eine Stufe ohne jede Wirkung.
        assertEquals(4, schief.buendel("siedler").zahlen().get("parzellen"),
                "der Siedler behaelt die 4 des Reisenden - seine 2 hebt nichts");
    }

    @Test
    @DisplayName("Eine obere Bedingung UNTER der unteren ist wirkungslos und wird gemeldet")
    void wirkungsloseBedingung() {
        var schief = new Rangwerte(true, List.of(
                stufe("reisender", Map.of(), Map.of(), List.of()),
                stufe("siedler", Map.of(Bedingungsart.TAETIGE_STUNDEN, 40L),
                        Map.of(), List.of()),
                stufe("buerger", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                        Map.of(), List.of())));

        var saetze = schief.ungereimtheiten();

        assertEquals(1, saetze.size(), saetze.toString());
        assertTrue(saetze.get(0).contains("wirkungslos"), saetze.get(0));
    }

    @Test
    @DisplayName("Die ausgelieferte Leiter ist in sich stimmig")
    void ausgelieferteLeiter() {
        var werte = Rangwerte.VORGABE;

        assertTrue(werte.greift());
        assertEquals(List.of("reisender", "siedler", "buerger"), werte.schluessel());
        assertEquals(List.of(), werte.ungereimtheiten(),
                "die mitgelieferte Leiter darf sich nicht selbst widersprechen: "
                        + werte.ungereimtheiten());
        assertTrue(werte.unterste().orElseThrow().ohneBedingung());
        // UND SIE BELOHNT NICHT DAS DASITZEN [Nutzer, 18.11.2026]: Ueber dem untersten
        // Rang verlangt jede Stufe etwas, das mehr ist als Anwesenheit.
        int geprueft = 0;
        for (Rangwerte.Stufe stufe : werte.leiter().subList(1, werte.leiter().size())) {
            geprueft++;
            assertFalse(stufe.bedingungen().containsKey(Bedingungsart.ANWESENHEIT_STUNDEN),
                    stufe.schluessel() + " haengt an blosser Anwesenheit");
            assertTrue(stufe.bedingungen().size() >= 2,
                    stufe.schluessel() + " verlangt nur eine einzige Sache");
        }
        assertEquals(werte.leiter().size() - 1, geprueft,
                "jede Stufe ueber der untersten ist angesehen");
    }

    @Test
    @DisplayName("Die Leiter hat eine Obergrenze - sonst ist es eine Liste")
    void zuVieleStufen() {
        List<Rangwerte.Stufe> zuViele = new java.util.ArrayList<>();
        for (int nummer = 0; nummer <= Rangwerte.HOECHSTE_STUFEN; nummer++) {
            zuViele.add(stufe("rang" + nummer, Map.of(), Map.of(), List.of()));
        }

        assertThrows(IllegalArgumentException.class, () -> new Rangwerte(true, zuViele));
    }

    @Test
    @DisplayName("Ein Buendel sammelt die Rechte von UNTEN bis zur erreichten Stufe")
    void buendelSammeltVonUnten() {
        var buendel = Rangwerte.VORGABE.buendel("buerger");

        // DER FALL, DER DIE ENTSCHEIDUNG ERZWUNGEN HAT: buerger nennt laden.mieten nicht
        // noch einmal - er nennt, was NEU dazukommt. Zaehlte nur die erreichte Stufe,
        // naehme der Aufstieg zum Buerger dem Spieler seinen Laden weg, und niemand
        // meldete es als Fehler: Die Absage im Spiel ist dieselbe wie bei einem, der das
        // Recht nie hatte.
        assertTrue(buendel.rechte().contains("laden.mieten"),
                "der Buerger behaelt, was der Siedler gab: " + buendel.rechte());
        assertEquals(List.of("laden.mieten", "freistadt.gruenden",
                        "beruf.spezialisierung"), buendel.rechte(),
                "von unten nach oben, ohne Doppelte");
    }

    @Test
    @DisplayName("Bei den Zahlen gilt die HOECHSTE, auch wenn sie unten steht")
    void buendelNimmtDieHoechsteZahl() {
        var schief = new Rangwerte(true, List.of(
                stufe("reisender", Map.of(), Map.of("parzellen", 4), List.of()),
                stufe("siedler", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                        Map.of("parzellen", 2, "inseln", 3), List.of())));

        var buendel = schief.buendel("siedler");

        // Dieselbe Regel, die Rangzahl ohnehin anwendet: Es sind Rechte, und die hoechste
        // Zahl gewinnt. Eine Stufe, die weniger gibt, hebt damit nichts - sie nimmt aber
        // auch nichts weg.
        assertEquals(4, buendel.zahlen().get("parzellen"));
        assertEquals(3, buendel.zahlen().get("inseln"), "was nur oben steht, kommt mit");
    }

    @Test
    @DisplayName("Die unterste Stufe gibt nur sich selbst")
    void buendelDerUntersten() {
        var buendel = Rangwerte.VORGABE.buendel("reisender");

        assertEquals(List.of(), buendel.rechte(), "der Reisende gibt kein Recht");
        assertEquals(1, buendel.zahlen().get("parzellen"));
        assertFalse(buendel.leerIst(), "eine Zahl ist auch etwas");
    }

    @Test
    @DisplayName("Ein unbekannter Rang gibt ein leeres Buendel und wirft NICHT")
    void buendelEinesUnbekannten() {
        // Er kommt aus der ABLAGE, und dort kann eine Stufe stehen, die aus der Datei
        // genommen wurde. Ein Wurf hier waere ein Absturz beim Beitritt. Den Rueckfall
        // auf die unterste Stufe macht Rangstand.stufeIn - an EINER Stelle.
        assertTrue(Rangwerte.VORGABE.buendel("fuerst").leerIst());
        assertTrue(Rangwerte.VORGABE.buendel(null).leerIst());
    }

    @Test
    @DisplayName("Ist die Leiter aus, gibt auch ein gueltiger Rang nichts")
    void buendelWennAus() {
        // Sonst wirkte raenge.an: false nur auf den AUFSTIEG und nicht auf das, was
        // schon Abgestiegene halten - der Schalter waere halb an, und genau das meldet
        // eine halb wirkende Einstellung nirgends.
        assertTrue(Rangwerte.VORGABE.mitAn(false).buendel("buerger").leerIst());
    }

    @Test
    @DisplayName("Ohne Leiter gibt es keine Werte")
    void riegel() {
        assertThrows(IllegalArgumentException.class, () -> new Rangwerte(true, null));
        assertThrows(IllegalArgumentException.class,
                () -> stufe(null, Map.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> stufe("  ", Map.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> stufe("Mit Leerzeichen", Map.of(), Map.of(), List.of()));
    }
}
