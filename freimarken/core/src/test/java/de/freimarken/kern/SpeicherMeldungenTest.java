package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Meldevertrag - und drei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link MeldeVertrag}
 * und nicht hier: Sonst erbt die MariaDB-Fassung sie nicht.
 */
@DisplayName("Meldungen im Arbeitsspeicher")
class SpeicherMeldungenTest {

    @Nested
    @DisplayName("Haelt den Meldevertrag ein")
    class Vertrag extends MeldeVertrag {
        @Override
        protected Meldeablage ablage() {
            return new SpeicherMeldungen();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts - auch nicht den Nummernzaehler")
    void zweiAblagenTeilenNichts() {
        UUID anna = UUID.randomUUID();
        SpeicherMeldungen erste = new SpeicherMeldungen();
        erste.lege(anna, UUID.randomUUID(), "erster Vorfall", "altfurt", 0, -60, 0,
                MeldeVertrag.MITTAGS);

        SpeicherMeldungen zweite = new SpeicherMeldungen();

        // Eine statische Karte oder ein statischer Zaehler waere hier der naheliegende
        // Fehler, und er faellt in einer Probenreihe nie auf. Im Betrieb waere er eine
        // Meldung, die ein Wiedereinschalten des Plugins ueberlebt - oder, beim
        // Zaehler, eine Nummer, die nach dem Neustart weiterzaehlt, obwohl die
        // Meldungen weg sind.
        assertEquals(0, zweite.offeneVon(anna));
        assertEquals(1L, zweite.lege(anna, UUID.randomUUID(), "frischer Vorfall",
                "altfurt", 0, -60, 0, MeldeVertrag.MITTAGS).nummer(),
                "die zweite Ablage faengt bei 1 an");
    }

    @Test
    @DisplayName("Die Nummer wird nach einem Entzug NICHT wiederverwendet")
    void nummerKommtNichtWieder() {
        SpeicherMeldungen ablage = new SpeicherMeldungen();
        UUID anna = UUID.randomUUID();

        long erste = ablage.lege(anna, UUID.randomUUID(), "erster", "altfurt", 0, -60, 0,
                MeldeVertrag.MITTAGS).nummer();
        long zweite = ablage.lege(anna, UUID.randomUUID(), "zweiter", "altfurt",
                0, -60, 0, MeldeVertrag.SPAETER).nummer();

        // Der Unterschied zu einer AUTO_INCREMENT-Spalte, die nach einem Neustart wieder
        // bei MAX(nummer)+1 anfaengt. Hier ist er folgenlos, weil es kein Loeschen gibt -
        // die Probe haelt fest, dass der Zaehler nur hochzaehlt, damit niemand das
        // Gegenteil fuer eine Zusage haelt.
        assertEquals(erste + 1L, zweite);
    }

    @Test
    @DisplayName("Der zurueckgegebene Stand haelt keine Verbindung zur Karte")
    void standIstEineAbschrift() {
        SpeicherMeldungen ablage = new SpeicherMeldungen();
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung offen = ablage.lege(anna, UUID.randomUUID(), "Vorfall", "altfurt",
                0, -60, 0, MeldeVertrag.MITTAGS);
        List<Meldung> vorher = ablage.offene(10);

        assertTrue(ablage.schreibe(offen.inArbeitVon(moderator, MeldeVertrag.SPAETER),
                Meldestand.OFFEN), "die Vorbereitung");

        // Die Liste ist eine Antwort und kein Fenster in die Karte. Gaebe offene() die
        // innere Liste heraus, waere jede Liste, die ein Befehl eine Sekunde lang haelt,
        // nach dem naechsten Schreibzug eine andere - und ein Vergleich von vorher und
        // nachher immer gleich.
        assertEquals(Meldestand.OFFEN, vorher.get(0).stand(),
                "die alte Liste aendert sich nicht mit");
        assertEquals(Meldestand.IN_ARBEIT, ablage.offene(10).get(0).stand());
        assertThrows(UnsupportedOperationException.class, () -> vorher.add(null),
                "und sie laesst sich auch nicht von aussen erweitern");
    }
}
