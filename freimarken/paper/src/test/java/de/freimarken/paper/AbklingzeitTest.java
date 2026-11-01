package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Sperre vor der teuren Abschaetzung - ohne Server und ohne Uhr.
 *
 * <p>Die Zeit kommt als Zahl herein. Eine Probe, die {@code Thread.sleep} braucht, um
 * eine Regel zu zeigen, prueft am Ende die Uhr des Rechners und nicht die Regel.
 */
class AbklingzeitTest {

    private static final long SEKUNDE = 1_000_000_000L;

    private final Abklingzeit sperre = new Abklingzeit(Duration.ofSeconds(3));
    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    @Test
    @DisplayName("Das erste Mal geht immer durch")
    void dasErsteMalGehtDurch() {
        assertEquals(0L, sperre.restNs(anna, 1000 * SEKUNDE));
    }

    @Test
    @DisplayName("Gleich danach noch einmal: gesperrt, mit Restzeit")
    void gleichDanachGesperrt() {
        sperre.restNs(anna, 1000 * SEKUNDE);

        // Eine Sekunde spaeter fehlen noch zwei.
        assertEquals(2 * SEKUNDE, sperre.restNs(anna, 1001 * SEKUNDE));
    }

    @Test
    @DisplayName("Die Sperre schiebt sich nicht weiter, wenn man dagegenlaeuft")
    void dauerfeuerVerlaengertNicht() {
        sperre.restNs(anna, 1000 * SEKUNDE);
        for (int i = 1; i < 30; i++) {
            // Zehntelsekunden-Takt, wie ein festgehaltener Knopf.
            assertTrue(sperre.restNs(anna, 1000 * SEKUNDE + i * SEKUNDE / 10) > 0L,
                    "waehrend der Sperre muss jede Anfrage warten lassen");
        }

        // Nach genau drei Sekunden ist sie vorbei - und nicht erst drei Sekunden nach dem
        // letzten Versuch. Sonst koennte sich ein Spieler selbst dauerhaft aussperren.
        assertEquals(0L, sperre.restNs(anna, 1003 * SEKUNDE));
    }

    @Test
    @DisplayName("Zwei Spieler stoeren einander nicht")
    void zweiSpielerUnabhaengig() {
        assertEquals(0L, sperre.restNs(anna, 1000 * SEKUNDE));

        assertEquals(0L, sperre.restNs(bert, 1000 * SEKUNDE), "Bert war noch nie dran");
        assertTrue(sperre.restNs(anna, 1001 * SEKUNDE) > 0L, "Anna aber schon");
    }

    @Test
    @DisplayName("Abgelaufene Vermerke verschwinden von selbst")
    void abgelaufenesVerschwindet() {
        sperre.restNs(anna, 1000 * SEKUNDE);
        sperre.restNs(bert, 1000 * SEKUNDE);
        assertEquals(2, sperre.vermerkt());

        // Nur Bert kommt wieder. Annas Vermerk ist abgelaufen und muss weg sein - sonst
        // waechst die Karte mit jedem Spieler, der den Befehl je benutzt hat.
        sperre.restNs(bert, 1004 * SEKUNDE);

        assertEquals(1, sperre.vermerkt());
    }
}
