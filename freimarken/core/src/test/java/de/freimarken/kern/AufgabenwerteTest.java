package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Herausforderungen als Liste - Rechnerei ohne Bukkit. */
@DisplayName("Die Aufgabenwerte von Wolkenwerder")
class AufgabenwerteTest {

    private static Aufgabenwerte.Aufgabe aufgabe(String schluessel) {
        return new Aufgabenwerte.Aufgabe(schluessel, Map.of("cobblestone", 64), 50L);
    }

    @Test
    @DisplayName("Die REIHENFOLGE der Datei bleibt die der Liste")
    void reihenfolgeBleibt() {
        // Von leicht nach schwer ist eine Aussage des Betreibers ueber sein Spiel. Eine
        // sortierte Liste waere die Tafel in alphabetischer Folge - und jeder Spieler
        // faenge mit dem Baecker an.
        Aufgabenwerte werte = new Aufgabenwerte(true,
                List.of(aufgabe("zuletzt"), aufgabe("mitte"), aufgabe("anfang")));

        assertEquals(List.of("zuletzt", "mitte", "anfang"), werte.schluessel());
    }

    @Test
    @DisplayName("Eine Aufgabe wird kleingeschrieben und beschnitten abgelegt")
    void schluesselWirdAbgelegt() {
        // Der Schluessel steht an DREI Orten: in der Ablage, in der Sprachdatei und im
        // getippten Befehl. Alle drei lesen ihn kleingeschrieben.
        Aufgabenwerte.Aufgabe aufgabe =
                new Aufgabenwerte.Aufgabe("  Erster-Stein  ",
                        Map.of("  Cobblestone  ", 64), 50L);

        assertEquals("erster-stein", aufgabe.schluessel());
        assertEquals(Map.of("cobblestone", 64), aufgabe.verlangt());
    }

    @Test
    @DisplayName("Die Aufgabe zu einem Schluessel - egal wie geschrieben")
    void aufgabeFinden() {
        Aufgabenwerte werte = new Aufgabenwerte(true, List.of(aufgabe("erster-stein")));

        assertEquals("erster-stein",
                werte.aufgabe("ERSTER-STEIN").orElseThrow().schluessel());
        assertEquals("erster-stein",
                werte.aufgabe(" erster-stein ").orElseThrow().schluessel());
        assertEquals(Optional.empty(), werte.aufgabe("gibt-es-nicht"));
        assertEquals(Optional.empty(), werte.aufgabe(null));
    }

    @Test
    @DisplayName("Abgeschaltet oder leer greift die Liste nicht")
    void greiftNicht() {
        assertTrue(Aufgabenwerte.VORGABE.greift());
        assertFalse(Aufgabenwerte.VORGABE.mitAn(false).greift());
        assertFalse(Aufgabenwerte.VORGABE.mitListe(List.of()).greift(),
                "eine leere Liste ist erlaubt und heisst: kein Fortschritt");
    }

    @Test
    @DisplayName("Derselbe Schluessel zweimal ist ein Programmfehler")
    void doppelterSchluessel() {
        // In der Ablage waeren das EINE Zeile: Wer die erste erfuellt, hat die zweite mit
        // erfuellt - und die Belohnung der zweiten bekaeme er nie.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte(true,
                        List.of(aufgabe("erster-stein"), aufgabe("erster-stein"))));
        assertTrue(wurf.getMessage().contains("zweimal"), wurf.getMessage());
    }

    @Test
    @DisplayName("Ein Schluessel mit Punkt, Leerzeichen oder Grossbuchstaben taugt nicht")
    void schluesselmuster() {
        // Der Punkt waere in der Sprachdatei eine Schachtelung, das Leerzeichen im Befehl
        // ein zweites Wort. Beides faellt sonst erst im Spiel auf - und zwar als Zeile,
        // die es nicht gibt.
        assertThrows(IllegalArgumentException.class, () -> aufgabe("erster.stein"));
        assertThrows(IllegalArgumentException.class, () -> aufgabe("erster stein"));
        assertThrows(IllegalArgumentException.class, () -> aufgabe("1stein"),
                "beginnen muss er mit einem Buchstaben");
        assertThrows(IllegalArgumentException.class, () -> aufgabe(""));
        assertThrows(IllegalArgumentException.class,
                () -> aufgabe("a".repeat(Aufgabenwerte.HOECHSTE_SCHLUESSELLAENGE + 1)),
                "so lang ist die Spalte in der Ablage");

        assertTrue(Aufgabenwerte.brauchbarerSchluessel("erster-stein"));
        assertTrue(Aufgabenwerte.brauchbarerSchluessel("erster_stein"),
                "der Unterstrich geht auch - die Gegenstaende daneben tragen ihn");
        assertTrue(Aufgabenwerte.brauchbarerSchluessel("  Erster-Stein  "),
                "GROSS geschrieben taugt er AUCH, und das ist kein Versehen: Diese"
                        + " Pruefung fragt, ob aus dem Getippten ein Schluessel WIRD -"
                        + " der Leser der config.yml normalisiert ihn. Dass ein PFAD"
                        + " in der Sprachdatei schon normalisiert sein muss, prueft"
                        + " Aufgabenname.passt zusaetzlich; dieselbe Teilung wie bei"
                        + " Berufsangabe.brauchbareZeichen und Berufsname.passt");
        assertFalse(Aufgabenwerte.brauchbarerSchluessel("Erster Stein"));
        assertFalse(Aufgabenwerte.brauchbarerSchluessel(null));
    }

    @Test
    @DisplayName("Eine Aufgabe ohne Gegenstaende und ein Lohn ausserhalb werfen")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("leer", Map.of(), 50L),
                "das waere Geld fuer das Tippen eines Befehls");
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("leer", null, 50L));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("lohn", Map.of("stone", 1), -1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("lohn", Map.of("stone", 1),
                        Aufgabenwerte.HOECHSTE_BELOHNUNG + 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("menge", Map.of("stone", 0), 1L),
                "wer eine Zeile nicht will, nimmt sie heraus");
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte.Aufgabe("menge",
                        Map.of("stone", Aufgabenwerte.HOECHSTE_ANZAHL + 1), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte(true, null));
    }

    @Test
    @DisplayName("Mehr Aufgaben als die hoechste Stufe sind ein Programmfehler")
    void zuVieleAufgaben() {
        // Die Stufe IST die Zahl der erfuellten Aufgaben. Eine Zeile oberhalb der
        // hoechsten Stufe waere eine, deren Erfuellung die Ausbeute nicht mehr aendert.
        List<Aufgabenwerte.Aufgabe> zuViele = new java.util.ArrayList<>();
        for (int i = 0; i <= Aufgabenwerte.HOECHSTE_ZAHL; i++) {
            zuViele.add(aufgabe("nummer" + i));
        }

        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenwerte(true, zuViele));
    }

    @Test
    @DisplayName("Die ausgelieferte Liste hat ZEHN Aufgaben - eine je Erzstufe und mehr")
    void vorgabe() {
        // Die Zahl ist kein Zufall: ancient_debris steht in der Erztabelle ab Stufe 8,
        // und ohne zehn Aufgaben waere das ein Erz, das in dieser Welt niemand bekommt.
        // Genau diese Verbindung nennt der Startbericht.
        assertEquals(10, Aufgabenwerte.VORGABE_LISTE.size());
        assertTrue(Aufgabenwerte.VORGABE_LISTE.size()
                        >= Erzwerte.VORGABE_TABELLE.values().stream()
                                .mapToInt(Erzwerte.Erzzeile::abStufe).max().orElse(0),
                "die hoechste Mindeststufe der Erztabelle muss erreichbar sein");
        assertEquals("erster-stein", Aufgabenwerte.VORGABE_LISTE.get(0).schluessel(),
                "die leichteste zuerst - sie ist mit dem Generator allein zu loesen");
        for (Aufgabenwerte.Aufgabe aufgabe : Aufgabenwerte.VORGABE_LISTE) {
            assertTrue(aufgabe.belohnung() > 0L,
                    aufgabe.schluessel() + " ohne Lohn - erlaubt, aber nicht gewollt");
            assertFalse(aufgabe.verlangt().isEmpty());
        }
    }

    @Test
    @DisplayName("Die Aufgabenliste liegt im EINEN Wirtschaftssatz")
    void imWirtschaftssatz() {
        // Der Grund, aus dem sie dort liegt: Ein Neuladen tauscht den ganzen Satz als EIN
        // Stueck - die Liste, die Erztabelle und der Preis gehoeren zusammen.
        assertEquals(Aufgabenwerte.VORGABE, Wirtschaftswerte.VORGABE.wolken().aufgaben());

        Aufgabenwerte eigene = Aufgabenwerte.VORGABE.mitAn(false);
        Wirtschaftswerte geaendert = Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(eigene));

        assertEquals(eigene, geaendert.wolken().aufgaben());
        assertEquals(Erzwerte.VORGABE, geaendert.wolken().erze(),
                "und die Erztabelle daneben bleibt stehen");
    }
}
