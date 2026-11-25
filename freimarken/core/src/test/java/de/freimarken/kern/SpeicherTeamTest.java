package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Teamvertrag - und drei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link TeamVertrag} und
 * nicht hier: Sonst erbt die MariaDB-Fassung sie nicht. Was hier steht, gilt nur fuer die
 * Karten im Arbeitsspeicher.
 */
@DisplayName("Teamrollen im Arbeitsspeicher")
class SpeicherTeamTest {

    @Nested
    @DisplayName("Haelt den Teamvertrag ein")
    class Vertrag extends TeamVertrag {
        @Override
        protected Teamablage ablage() {
            return new SpeicherTeam();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt. Im Betrieb waere
        // er ein Moderator, dessen Rolle ein Wiedereinschalten des Plugins ueberlebt -
        // und die bliebe dann auch nach dem Entzug.
        UUID anna = UUID.randomUUID();
        SpeicherTeam erste = new SpeicherTeam();
        erste.gib(anna, "moderator", TeamVertrag.MITTAGS, null);

        assertFalse(new SpeicherTeam().lies(anna).imTeam());
    }

    @Test
    @DisplayName("Der zurueckgegebene Stand haelt keine Verbindung zur Karte")
    void standIstEineAbschrift() {
        SpeicherTeam team = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        team.gib(anna, "moderator", TeamVertrag.MITTAGS, null);
        Teamstand vorher = team.lies(anna);

        team.gib(anna, "baumeister", TeamVertrag.SPAETER, null);

        // Der Stand ist eine Antwort und kein Fenster in die Karte. Gaebe lies() die
        // innere Liste heraus, waere jeder Stand, den ein Befehl eine Sekunde lang
        // haelt, nach dem naechsten Schreibzug ein anderer - und ein Vergleich von
        // vorher und nachher immer gleich.
        assertEquals(List.of("moderator"), vorher.rollen(),
                "der alte Stand aendert sich nicht mit");
        assertEquals(List.of("baumeister", "moderator"), team.lies(anna).rollen());
        assertThrows(UnsupportedOperationException.class,
                () -> vorher.zeilen().add(null),
                "und er laesst sich auch nicht von aussen erweitern");
    }

    @Test
    @DisplayName("Eine leere Huelle bleibt nach dem Entzug nicht liegen")
    void keineLeereHuelleNachEntzug() {
        SpeicherTeam team = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        team.gib(anna, "moderator", TeamVertrag.MITTAGS, null);
        team.gib(bert, "helfer", TeamVertrag.MITTAGS, null);

        assertTrue(team.nimm(anna, "moderator"));

        // Diese Fassung haelt zwei Karten uebereinander; die innere wird leer, und die
        // aeussere behielte ihren Schluessel. Die MariaDB-Fassung hat das Problem nicht -
        // dort verschwindet die letzte Zeile. Darum steht die Probe hier und nicht im
        // Vertrag; dass die LISTE danach stimmt, prueft der Vertrag fuer beide.
        assertEquals(List.of(bert),
                team.alle().stream().map(Teamstand::spieler).toList(),
                "anna ist ganz weg, bert steht noch da");
    }
}
