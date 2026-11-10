package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zahlen der Freien Truhe - und was eine 0 bedeutet.
 *
 * <p>Zwei Zusagen tragen hier alles andere: Die 0 ist ERLAUBT und heisst "kein Deckel",
 * und ohne eingetragene Stelle gibt es die Truhe gar nicht. Beides laesst sich leicht in
 * die andere Richtung bauen, und beide Richtungen sind still: Ein verbotener 0-Deckel
 * waere eine Klage, die niemand liest, und eine geratene Stelle eine Kiste, aus der jeder
 * nehmen darf.
 */
@DisplayName("Die Zahlen der Freien Truhe")
class TruhenwerteTest {

    @Test
    @DisplayName("Ab Werk gilt KEIN Deckel und KEIN Verbot")
    void abWerkOhneGrenze() {
        // ENTSCHIEDEN AM 10.11.2026 nach dem Spieltest: Plan 7.2 empfiehlt 16 Stueck und
        // zwei Verbote, der Betreiber wollte beides nicht - die Truhe steht am Spawn, und
        // was darin liegt, hat jemand freiwillig hineingelegt. Beide Zahlen stehen hier,
        // weil genau hier auffaellt, wenn jemand eine Empfehlung zurueckschreibt.
        assertEquals(Truhenwerte.KEIN_DECKEL, Truhenwerte.VORGABE.entnahmeJeTag());
        assertFalse(Truhenwerte.VORGABE.gedeckelt());
        assertEquals(Set.of(), Truhenwerte.VORGABE_VERBOTEN);
        assertEquals(Set.of(), Truhenwerte.VORGABE.verboten());
        assertFalse(Truhenwerte.VORGABE.istVerboten("written_book"),
                "auch das geschriebene Buch darf hinein");
    }

    @Test
    @DisplayName("GEZAEHLT wird nur mit Deckel oder mit Verboten - sonst gar nicht")
    void zaehltNurMitGrund() {
        // DIE TRAGENDE ENTSCHEIDUNG VOM 10.11.2026: "man soll auch gemeinsam
        // hereinschauen koennen". Das "einer zur Zeit" ist die Bedingung der Zaehlung,
        // und gezaehlt wird fuer zwei Dinge - den Deckel und die Rueckgabe verbotener
        // Gegenstaende. Fehlen beide, gibt es nichts zu zaehlen und niemanden
        // auszusperren.
        assertFalse(Truhenwerte.VORGABE.zaehlt(), "ab Werk wird nicht gezaehlt");
        assertTrue(new Truhenwerte(16, Set.of()).zaehlt(), "ein Deckel zaehlt");
        assertTrue(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of("written_book"))
                .zaehlt(), "ein Verbot zaehlt AUCH - die Rueckgabe braucht denselben"
                        + " Unterschied wie der Deckel");
        assertTrue(new Truhenwerte(16, Set.of("written_book")).zaehlt());
        assertFalse(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of()).zaehlt(),
                "erst wenn BEIDES fehlt, ist es eine gewoehnliche offene Kiste");
    }

    @Test
    @DisplayName("Die 0 ist erlaubt und heisst KEIN Deckel")
    void nullHeisstKeinDeckel() {
        // Dieselbe Form wie bei bundesmarkt.tagesdeckel [entschieden 10.11.2026]: eine
        // ZAHL und kein true/false, denn Bukkit liest jedes Wort ausser "true" stumm als
        // false. Waere die 0 verboten, muesste der Betreiber eine riesige Zahl eintragen,
        // um den Deckel abzuschalten - und die stuende dann in der Auskunft an den
        // Spieler.
        // Ausgegangen wird von einem WIRKLICHEN Deckel und einer WIRKLICHEN Verbotsliste:
        // Ab Werk gilt beides nicht mehr, und eine Probe, die von der Vorgabe ausgeht,
        // vergliche hier 0 mit 0 und eine leere Liste mit einer leeren.
        Truhenwerte gedeckelt = new Truhenwerte(16, Set.of("written_book"));
        Truhenwerte ohne = gedeckelt.mitEntnahme(Truhenwerte.KEIN_DECKEL);

        assertTrue(Truhenwerte.brauchbareEntnahme(Truhenwerte.KEIN_DECKEL));
        assertTrue(gedeckelt.gedeckelt(), "sonst prueft die Zeile darunter nichts");
        assertEquals(0, ohne.entnahmeJeTag());
        assertFalse(ohne.gedeckelt());
        assertEquals(Set.of("written_book"), ohne.verboten(),
                "der Deckel schaltet die Truhe nicht ab - das tun die Stellen");
    }

    @Test
    @DisplayName("Eine negative und eine unmoegliche Entnahme werden abgewiesen")
    void unmoeglicheEntnahme() {
        assertFalse(Truhenwerte.brauchbareEntnahme(-1));
        assertFalse(Truhenwerte.brauchbareEntnahme(Truhenwerte.HOECHSTE_ENTNAHME + 1));
        assertThrows(IllegalArgumentException.class,
                () -> Truhenwerte.VORGABE.mitEntnahme(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Truhenwerte.VORGABE.mitEntnahme(Truhenwerte.HOECHSTE_ENTNAHME + 1));
        assertTrue(Truhenwerte.brauchbareEntnahme(Truhenwerte.HOECHSTE_ENTNAHME),
                "die Grenze selbst ist erlaubt - mehr als eine Doppelkiste nicht");
    }

    @Test
    @DisplayName("Eine EINGETRAGENE Verbotsliste wirkt - und nur sie")
    void eingetrageneListeWirkt() {
        // Die Vorgabe ist leer, also wird am eingetragenen Fall geprueft. Ohne diese
        // Probe waere der ganze Rueckweg ungedeckt, bis ihn jemand im Spiel vermisst -
        // und genau so ist der Verlust vom 10.11.2026 gefunden worden.
        Truhenwerte werte = Truhenwerte.VORGABE.mitVerboten(
                Set.of("written_book", "red_shulker_box"));

        assertEquals(2, werte.verboten().size());
        assertTrue(werte.istVerboten("written_book"));
        assertTrue(werte.istVerboten("red_shulker_box"));
        assertFalse(werte.istVerboten("stone"));
        assertFalse(werte.istVerboten("writable_book"),
                "das LEERE Buch ist ein eigenes Material - es traegt keinen Verfasser");
    }

    @Test
    @DisplayName("Verglichen wird kleingeschrieben - in beide Richtungen")
    void kleingeschriebenVerglichen() {
        // Die Liste kommt aus der Datei und der Name aus der Spielschicht. Schriebe
        // einer von beiden gross, faende der Vergleich nie etwas - und zwar STUMM: "nichts
        // verboten" sieht aus wie "alles erlaubt".
        Truhenwerte werte = Truhenwerte.VORGABE.mitVerboten(Set.of("WRITTEN_BOOK",
                " Elytra "));

        assertTrue(werte.istVerboten("written_book"), "gross eingetragen, klein gefragt");
        assertTrue(werte.istVerboten("WRITTEN_BOOK"), "und gross gefragt");
        assertTrue(werte.istVerboten("elytra"), "mit Leerzeichen eingetragen");
        assertTrue(werte.istVerboten(" elytra "), "und mit Leerzeichen gefragt");
        assertFalse(werte.istVerboten(null));
    }

    @Test
    @DisplayName("Leere Eintraege in der Verbotsliste fallen weg")
    void leereEintraegeFallenWeg() {
        // Eine Zeile "- " in der Datei ist kein Material. Bliebe sie stehen, waere die
        // Liste um einen Eintrag laenger, der nie etwas trifft - und die Zahl in der
        // Startmeldung logen.
        Truhenwerte werte = Truhenwerte.VORGABE.mitVerboten(Set.of("stone", "", "  "));

        assertEquals(Set.of("stone"), werte.verboten());
    }

    @Test
    @DisplayName("Ohne Verbotsliste gibt es keine Werte - nichts ist keine LEERE Liste")
    void ohneListeKeineWerte() {
        // "Alles erlaubt" und "keine Angabe" sind zwei verschiedene Dinge: Das eine ist
        // eine Entscheidung des Betreibers, das andere ein Fehler des Lesers.
        assertThrows(IllegalArgumentException.class, () -> new Truhenwerte(16, null));
    }

    @Test
    @DisplayName("Die Verbotsliste ist ABGESCHRIEBEN - sie laesst sich nicht aendern")
    void listeIstAbgeschrieben() {
        // Der Leser baut seine Menge waehrend des Lesens auf und gibt sie herein. Behielte
        // der Satz genau diese Menge, aenderte ein zweites Neuladen die Werte eines
        // Standes, der sie schon ausgegeben hat.
        Set<String> meine = new LinkedHashSet<>(Set.of("stone"));
        Truhenwerte werte = Truhenwerte.VORGABE.mitVerboten(meine);

        meine.clear();

        assertTrue(werte.istVerboten("stone"), "das Verbot bleibt, wo es war");
        assertThrows(UnsupportedOperationException.class,
                () -> werte.verboten().add("dirt"));
    }
}
