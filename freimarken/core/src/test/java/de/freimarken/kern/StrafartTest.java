package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Arten einer Strafe - und was fuer jede gelten muss.
 *
 * <p><b>Jede Probe hier laeuft ueber {@code values()}</b> und zaehlt keine Konstanten
 * auf. Das ist Absicht: Eine vierte Art ist dann von selbst mitgeprueft, und zwar in
 * demselben Augenblick, in dem sie entsteht. Eine Liste von Hand waere die Stelle, an der
 * sie fehlt - und was hier fehlt, fehlt am Ende in einem Riegel.
 */
class StrafartTest {

    @Test
    @DisplayName("Jede Art hat ein Kuerzel, und jedes Kuerzel ist eindeutig")
    void kuerzelSindEindeutig() {
        Set<String> gesehen = new HashSet<>();

        for (Strafart art : Strafart.values()) {
            String kuerzel = art.kuerzel();
            assertFalse(kuerzel == null || kuerzel.isBlank(),
                    art + " hat kein Kuerzel");
            assertEquals(kuerzel.toLowerCase(java.util.Locale.ROOT), kuerzel,
                    art + ": das Kuerzel wird getippt und steht in der config.yml -"
                            + " kleingeschrieben");
            assertTrue(gesehen.add(kuerzel),
                    "das Kuerzel \"" + kuerzel + "\" kommt zweimal vor - dann trifft"
                            + " ausWort die falsche Art, und ein Zweig von /strafe ist"
                            + " unerreichbar");
        }
    }

    @Test
    @DisplayName("Jedes Kuerzel findet seine Art wieder")
    void kuerzelFindetZurueck() {
        for (Strafart art : Strafart.values()) {
            assertSame(art, Strafart.ausWort(art.kuerzel()).orElseThrow(),
                    art + ": das Kuerzel muss zurueckfinden, sonst ist der Zweig von"
                            + " /strafe nicht erreichbar");
            assertSame(art, Strafart.ausWort("  " + art.kuerzel().toUpperCase(
                    java.util.Locale.ROOT) + "  ").orElseThrow(),
                    art + ": Rand und Schreibweise sind gleichgueltig");
        }
    }

    @Test
    @DisplayName("Ein unbekanntes Wort gibt leer und keinen Fehler")
    void unbekanntesWortGibtLeer() {
        assertTrue(Strafart.ausWort("verwarnung").isEmpty());
        assertTrue(Strafart.ausWort("").isEmpty());
        assertTrue(Strafart.ausWort(null).isEmpty());
    }

    @Test
    @DisplayName("brauchtDauer ist dasselbe wie giltFort - fuer JEDE Art")
    void brauchtDauerGleichGiltFort() {
        // Zwei Namen fuer dieselbe Frage, weil sie an den Aufrufstellen verschiedenes
        // bedeutet. Waeren es zwei FELDER, liesse sich eines umstellen, ohne dass das
        // andere es merkt - und dann hat eine Art eine Dauer, nach der niemand fragt.
        for (Strafart art : Strafart.values()) {
            assertEquals(art.giltFort(), art.brauchtDauer(),
                    art + ": was fortwirkt, braucht eine Dauer, und umgekehrt");
        }
    }

    @Test
    @DisplayName("Was nicht fortwirkt, sperrt auch nichts")
    void wasNichtFortwirktSperrtNichts() {
        // Eine Art, die den Chat sperrt, aber nicht fortwirkt, waere eine Sperre fuer
        // die Dauer eines Augenblicks - sie wuerde nie gefragt und waere damit stumm
        // wirkungslos.
        for (Strafart art : Strafart.values()) {
            if (art.giltFort()) {
                continue;
            }
            assertFalse(art.sperrtChat(), art + " wirkt nicht fort und sperrt doch den"
                    + " Chat - diese Sperre wuerde nie gefragt");
            assertFalse(art.sperrtBeitritt(), art + " wirkt nicht fort und sperrt doch"
                    + " den Beitritt");
        }
    }

    @Test
    @DisplayName("Jede fortwirkende Art sperrt genau eines - Chat oder Tuer")
    void fortwirkendeSperrtGenauEines() {
        // Eine fortwirkende Art, die NICHTS sperrt, waere eine Strafe ohne Wirkung:
        // Sie stuende im Protokoll, und der Spieler merkte nichts. Eine, die BEIDES
        // sperrt, braeuchte keinen Chat-Riegel - wer nicht hereinkommt, schreibt nicht.
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            assertTrue(art.sperrtChat() ^ art.sperrtBeitritt(),
                    art + " sperrt " + (art.sperrtChat() ? "Chat" : "")
                            + (art.sperrtBeitritt() ? " Tuer" : "")
                            + " - genau eines muss es sein");
        }
    }

    @Test
    @DisplayName("Es gibt genau eine Art, die den Beitritt sperrt - die Bannliste")
    void genauEineSperrtDieTuer() {
        // Plan 12c.4 nennt EINE oeffentliche Liste, und Strafbuch.bannliste gibt genau
        // eine Art. Eine zweite beitrittssperrende Art waere eine, die in der Liste
        // fehlt - und dann steht jemand gesperrt da, den die Liste nicht nennt.
        int sperrend = 0;
        for (Strafart art : Strafart.values()) {
            if (art.sperrtBeitritt()) {
                sperrend++;
            }
        }
        assertEquals(1, sperrend, "genau eine Art sperrt die Tuer; sonst zeigt"
                + " Strafbuch.bannliste nicht alle Gesperrten");
    }

    @Test
    @DisplayName("Die Reihenfolge der Konstanten ist die des Weges, nicht des Alphabets")
    void reihenfolgeIstDerWeg() {
        // Gespeichert wird der NAME und nicht ordinal(). Diese Probe haelt die
        // Reihenfolge trotzdem fest: Sie ist die Reihenfolge, in der /strafe die Zweige
        // anbietet, und sie geht von der mildesten zur haertesten.
        assertEquals("[STUMM, RAUSWURF, BANN]",
                java.util.Arrays.toString(Strafart.values()));
    }
}
