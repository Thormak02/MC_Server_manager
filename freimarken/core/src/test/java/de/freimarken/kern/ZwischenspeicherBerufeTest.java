package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Zwischenspeicher vor der Berufsablage - dieselben Zusagen, nur schneller.
 *
 * <p>Gleiche Bauart wie {@link ZwischenspeicherLagerTest}: Der geteilte Vertrag prueft ihn
 * wie jede andere Ablage, und darunter stehen die Proben, die nur ein Zwischenspeicher
 * braucht.
 *
 * <p><b>Warum es ihn gibt:</b> Seit Teil E fragt der Verdienst bei JEDEM abgebauten und
 * JEDEM gesetzten Block, welche Berufe ein Spieler haelt. Eine Datenbankabfrage je Block
 * waere derselbe Fehler, den der Zwischenspeicher vor dem Lager schon einmal geloest hat.
 *
 * <p><b>Die tragende Probe ist {@link #schreibenLeertAlles()}:</b> Ein
 * {@code /beruf waehlen} muss sofort wirken. Ohne die Leerung verdiente ein Spieler bis
 * zu einer halben Minute lang nach seinem alten Beruf - und das ist der Fehler, den
 * niemand meldet, weil er sich von selbst behebt.
 */
@DisplayName("Zwischenspeicher vor den Berufen")
class ZwischenspeicherBerufeTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    @Nested
    @DisplayName("Haelt den Berufsvertrag ein")
    class Vertrag extends BerufeVertrag {
        @Override
        protected Berufsablage ablage() {
            return new ZwischenspeicherBerufe(new SpeicherBerufe());
        }
    }

    /** Eine Ablage, die mitzaehlt, wie oft wirklich gelesen wurde. */
    private static final class Zaehlende implements Berufsablage {
        private final Berufsablage echt = new SpeicherBerufe();
        private int gelesen;

        @Override
        public Berufsstand lies(UUID spieler) {
            gelesen++;
            return echt.lies(spieler);
        }

        @Override
        public boolean waehle(Antrag antrag, Berufsstand erwartet) {
            return echt.waehle(antrag, erwartet);
        }

        @Override
        public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
            return echt.verlasse(antrag, erwartet);
        }
    }

    @Test
    @DisplayName("Der zweite Blick kostet keine Abfrage")
    void zweiterBlickIstFrei() {
        // Das ist der ganze Zweck: Ein Spieler, der hundert Bloecke abbaut, fragt hundert
        // Mal nach seinen Berufen - und die Ablage sieht davon eine Frage.
        Zaehlende dahinter = new Zaehlende();
        var speicher = new ZwischenspeicherBerufe(dahinter,
                Clock.fixed(MITTAGS, ZoneOffset.UTC), Duration.ofSeconds(30L));
        UUID anna = UUID.randomUUID();

        for (int mal = 0; mal < 100; mal++) {
            speicher.lies(anna);
        }

        assertEquals(1, dahinter.gelesen);
        assertEquals(1, speicher.groesse());
    }

    @Test
    @DisplayName("Ein Schreibzug leert ALLES - ein neuer Beruf wirkt sofort")
    void schreibenLeertAlles() {
        Zaehlende dahinter = new Zaehlende();
        var speicher = new ZwischenspeicherBerufe(dahinter,
                Clock.fixed(MITTAGS, ZoneOffset.UTC), Duration.ofSeconds(30L));
        UUID anna = UUID.randomUUID();
        assertEquals(List.of(), speicher.lies(anna).berufe());

        assertTrue(speicher.waehle(new Berufsablage.Antrag(anna, "bergmann", MITTAGS),
                speicher.lies(anna)));

        // Die Uhr steht - ohne Leerung kaeme hier der alte, leere Stand. Und zwar eine
        // halbe Minute lang: Der Spieler waehlt seinen Beruf und verdient weiter nichts.
        assertEquals(List.of("bergmann"), speicher.lies(anna).berufe());
        assertEquals(1, speicher.groesse(), "nach dem Lesen steht genau einer drin");
    }

    @Test
    @DisplayName("Auch ein Verlassen leert - und auch ein gescheiterter Schreibzug")
    void verlassenUndFehlschlagLeerenAuch() {
        Zaehlende dahinter = new Zaehlende();
        var speicher = new ZwischenspeicherBerufe(dahinter,
                Clock.fixed(MITTAGS, ZoneOffset.UTC), Duration.ofSeconds(30L));
        UUID anna = UUID.randomUUID();
        speicher.waehle(new Berufsablage.Antrag(anna, "bergmann", MITTAGS),
                Berufsstand.leer(anna));
        Berufsstand veraltet = speicher.lies(anna);
        speicher.verlasse(new Berufsablage.Antrag(anna, "bergmann", MITTAGS), veraltet);

        assertEquals(List.of(), speicher.lies(anna).berufe());

        // Ein Schreibzug gegen einen veralteten Stand tut nichts und gibt false - und
        // leert TROTZDEM. Im finally und nicht nur bei Erfolg: Ein Wurf aus der
        // Datenschicht laesst offen, ob geschrieben wurde, und ein Speicher, der dann den
        // alten Stand weiterreicht, waere die unauffaelligste Art, falsch zu sein.
        int vorher = dahinter.gelesen;
        assertFalse(speicher.verlasse(
                new Berufsablage.Antrag(anna, "bergmann", MITTAGS), veraltet));
        speicher.lies(anna);
        assertTrue(dahinter.gelesen > vorher, "der naechste Blick geht an die Ablage");
    }

    @Test
    @DisplayName("Nach dem Verfall wird neu gelesen")
    void nachDemVerfallNeuGelesen() {
        // Kommt ein zweiter Server dazu, sieht dieser hier eine fremde Aenderung
        // spaetestens nach der Verfallszeit. Ohne Verfall waere er dann dauerhaft falsch.
        Zaehlende dahinter = new Zaehlende();
        Instant[] jetzt = {MITTAGS};
        Clock laufende = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return jetzt[0];
            }
        };
        var speicher = new ZwischenspeicherBerufe(dahinter, laufende,
                Duration.ofSeconds(30L));
        UUID anna = UUID.randomUUID();

        speicher.lies(anna);
        speicher.lies(anna);
        assertEquals(1, dahinter.gelesen);

        jetzt[0] = MITTAGS.plusSeconds(31L);
        speicher.lies(anna);

        assertEquals(2, dahinter.gelesen);
    }

    @Test
    @DisplayName("Ohne Ablage, Uhr und Verfallszeit gibt es keinen Zwischenspeicher")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZwischenspeicherBerufe(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ZwischenspeicherBerufe(new SpeicherBerufe(), null,
                        Duration.ofSeconds(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ZwischenspeicherBerufe(new SpeicherBerufe(),
                        Clock.systemUTC(), null));
    }
}
