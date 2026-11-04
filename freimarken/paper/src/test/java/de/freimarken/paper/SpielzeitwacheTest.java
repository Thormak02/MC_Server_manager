package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Umrechnung des eingestellten Taktes in Serverticks.
 *
 * <p>Das Einzige an der Wache, was ohne Server zu pruefen ist - und genau die Stelle, an
 * der ein Faktor 20 danebengehen kann. Alles andere dort ist Bukkit: Zeitgeber, Beitritt,
 * Abmelden. Was gerechnet wird, liegt im {@link Spielzeitbuch}.
 */
class SpielzeitwacheTest {

    @Test
    @DisplayName("20 Ticks sind eine Sekunde - aus 60 s werden 1200 Ticks")
    void sekundenWerdenTicks() {
        assertEquals(1200L, Spielzeitwache.ticks(Duration.ofSeconds(60)));
        assertEquals(20L, Spielzeitwache.ticks(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("Die Grenzen des eingestellten Taktes ergeben brauchbare Tickzahlen")
    void dieGrenzenGehenAuf() {
        // Ein Zeitgeber mit 0 Ticks Abstand lief bei jedem Tick; einer mit einer Zahl, die
        // ueberlaeuft, lief nie wieder. Beides faellt hier auf.
        long kuerzester = Spielzeitwache.ticks(
                Duration.ofSeconds(Zeitwerte.KUERZESTER_TAKT_SEKUNDEN));
        long laengster = Spielzeitwache.ticks(
                Duration.ofSeconds(Zeitwerte.LAENGSTER_TAKT_SEKUNDEN));

        assertEquals(200L, kuerzester);
        assertEquals(12_000L, laengster);
    }

    @Test
    @DisplayName("Ohne Takt gibt es keinen Zeitgeber")
    void ohneTaktKeinZeitgeber() {
        assertThrows(IllegalArgumentException.class, () -> Spielzeitwache.ticks(null));
    }
}
