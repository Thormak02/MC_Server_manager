package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Rangwerte;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung hinter einer Rangobergrenze - der Teil, der ohne Server auskommt.
 *
 * <p>Welche Rechte ein Spieler hat, weiss nur der Server. Was aus zwei Zahlen wird, nicht:
 * Genau deshalb steht die Addition in einer eigenen Methode und nicht mitten in der
 * Abfrage der Rechte.
 *
 * <p>Die Probe hiess bis zum 18.11.2026 {@code ParzellenBefehleTest}. Mit den Meerinseln
 * gilt dieselbe Rechnung fuer drei Grenzen - Parzellen, Gruppenfelder, Inseln -, und sie
 * steht darum in {@link Rangzahl}.
 */
class RangzahlTest {

    @BeforeAll
    static void serverStellen() {
        // Die Rundreise braucht einen Spieler, und ein Spieler braucht einen Server.
        Serverprobe.einmalSetzen();
    }

    @Test
    @DisplayName("Der Zuschlag kommt oben drauf, er ersetzt nicht")
    void zuschlagKommtDrauf() {
        // Der ganze Sinn der Sache: Eine Mitgliedschaft belohnt den, der sich schon etwas
        // erspielt hat. Wuerde sie die Zahl ersetzen, haette ein Reisender am ersten Tag
        // so viele Parzellen wie ein Buerger nach vierzig Stunden.
        assertEquals(6, Rangzahl.mitZuschlag(4, 2));
        assertEquals(3, Rangzahl.mitZuschlag(1, 2), "auch unten auf der Leiter");
    }

    @Test
    @DisplayName("Ohne Zuschlag bleibt es bei der Grundlage")
    void ohneZuschlagUnveraendert() {
        assertEquals(4, Rangzahl.mitZuschlag(4, 0));
    }

    @Test
    @DisplayName("Ein negativer Zuschlag nimmt nichts weg")
    void negativerZuschlagNimmtNichts() {
        // Ein Recht wie freimarken.extra.parzellen.-3 ist ein Tippfehler, kein Entzug.
        // Rechte sollen geben; wegnehmen tut die Vorgabe in der Konfiguration.
        assertEquals(4, Rangzahl.mitZuschlag(4, -3));
    }

    @Test
    @DisplayName("Zusammen laufen sie nicht ueber")
    void keinUeberlauf() {
        // Jede der beiden Zahlen wird bei der Rechteauswertung auf Integer.MAX_VALUE
        // gekappt. Addiert ergaebe das ohne Riegel eine NEGATIVE Obergrenze - und damit
        // haette ausgerechnet der mit den meisten Rechten gar keine Parzelle mehr.
        int ergebnis = Rangzahl.mitZuschlag(Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertTrue(ergebnis > 0, "eine Obergrenze darf nie negativ werden");
        assertEquals(Integer.MAX_VALUE, ergebnis);
    }

    @Test
    @DisplayName("Eine Grenze wird zu freimarken.<name>.<zahl>")
    void grenzeWirdZumRechtenamen() {
        var knoten = Rangzahl.knoten(Rangwerte.VORGABE.buendel("siedler"));

        assertTrue(knoten.contains("freimarken.parzellen.2"), knoten.toString());
        assertTrue(knoten.contains("freimarken.gruppe.2"), knoten.toString());
    }

    @Test
    @DisplayName("Ein Recht bekommt die Vorsilbe des Programms")
    void rechtBekommtDieVorsilbe() {
        var knoten = Rangzahl.knoten(Rangwerte.VORGABE.buendel("buerger"));

        // In der Datei steht laden.mieten ohne Vorsilbe - Rangwerte.Stufe verbietet sie
        // dort ausdruecklich, weil sie vom Programm kommt. HIER kommt sie dazu, und das
        // ist die eine Stelle dafuer.
        assertTrue(knoten.contains("freimarken.laden.mieten"), knoten.toString());
        assertTrue(knoten.contains("freimarken.freistadt.gruenden"), knoten.toString());
    }

    @Test
    @DisplayName("RUNDREISE: Was knoten schreibt, liest ausRecht wieder")
    void rundreise() {
        // DIE PROBE, DIE DIESE DATEI RECHTFERTIGT. Ein Schreiber und ein Leser derselben
        // Gestalt an zwei Stellen laufen eines Tages auseinander - geschrieben wird
        // freimarken.parzelle.2, gelesen freimarken.parzellen., und der Rang wirkt
        // einfach nicht. Rot wird dabei nichts, und im Spiel sieht es aus wie die Regel.
        var knoten = Rangzahl.knoten(Rangwerte.VORGABE.buendel("buerger"));
        var spieler = new Spielerprobe("Anna", Spielerprobe.kennungAus(81),
                knoten.toArray(new String[0])).spieler();

        assertEquals(4, Rangzahl.ausRecht(spieler, "freimarken.parzellen.",
                "freimarken.extra.parzellen.", 1));
        assertEquals(4, Rangzahl.ausRecht(spieler, "freimarken.gruppe.",
                "freimarken.extra.gruppe.", 1));
        assertEquals(1, Rangzahl.ausRecht(spieler, "freimarken.inseln.",
                "freimarken.extra.inseln.", 1));
        assertEquals(1, Rangzahl.ausRecht(spieler, "freimarken.wolken.",
                "freimarken.extra.wolken.", 1));
        assertTrue(spieler.hasPermission("freimarken.laden.mieten"),
                "und die reinen Rechte ebenso");
    }

    @Test
    @DisplayName("Die ZUSCHLAEGE kommen nicht aus dem Rang")
    void keineZuschlaegeAusDemRang() {
        var knoten = Rangzahl.knoten(Rangwerte.VORGABE.buendel("buerger"));

        // Sie gehoeren einer Mitgliedschaft und nicht einem Rang. Wer sie hier mit
        // vergaebe, haette sie zweimal - und mitZuschlag zaehlte sie doppelt.
        for (String name : knoten) {
            assertTrue(!name.startsWith("freimarken.extra."),
                    "ein Zuschlag aus dem Rang: " + name);
        }
    }

    @Test
    @DisplayName("Ein leeres Buendel gibt keine Knoten")
    void leeresBuendelGibtNichts() {
        assertEquals(Set.of(), Rangzahl.knoten(Rangwerte.Buendel.leer()));
        assertEquals(Set.of(), Rangzahl.knoten(null));
    }

    @Test
    @DisplayName("Eine eigene Grenze aus einer eigenen Leiter kommt genauso durch")
    void eigeneLeiterEigeneKnoten() {
        var eigene = Rangwerte.VORGABE.mitLeiter(List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of(), List.of()),
                new Rangwerte.Stufe("haendler", Map.of(Bedingungsart.GUTHABEN, 500L),
                        Map.of("inseln", 3), List.of("shop.bund"))));

        assertEquals(Set.of("freimarken.inseln.3", "freimarken.shop.bund"),
                Rangzahl.knoten(eigene.buendel("haendler")));
    }
}
