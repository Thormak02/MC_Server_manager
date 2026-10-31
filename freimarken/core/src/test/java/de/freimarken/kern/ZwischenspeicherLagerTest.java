package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Zwischenspeicher muss zwei Dinge zugleich sein: schnell und ehrlich.
 *
 * <p>Deshalb wird er hier gegen denselben Vertrag geprueft wie jedes andere Lager - und
 * zusaetzlich daraufhin, dass er nach einer Aenderung nichts Altes mehr ausliefert.
 */
class ZwischenspeicherLagerTest {

    /** Eine Uhr, die nur weitergeht, wenn der Test es sagt. */
    private static final class StellUhr extends Clock {
        private Instant jetzt = Instant.parse("2026-10-31T12:00:00Z");

        void weiter(Duration wie) {
            jetzt = jetzt.plus(wie);
        }

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
            return jetzt;
        }
    }

    /** Ein Lager, das mitzaehlt, wie oft wirklich nachgesehen wurde. */
    private static final class ZaehlLager implements BesitzLager {
        private final BesitzLager echt = new SpeicherLager();
        final AtomicInteger abfragen = new AtomicInteger();

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            abfragen.incrementAndGet();
            return echt.finde(welt, x, z);
        }

        @Override
        public List<Parzelle> vonSpieler(String welt, UUID spieler) {
            return echt.vonSpieler(welt, spieler);
        }

        @Override
        public int anzahlVon(String welt, UUID spieler) {
            return echt.anzahlVon(welt, spieler);
        }

        @Override
        public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
            return echt.anlegen(welt, x, z, eigentuemer, preis);
        }

        @Override
        public Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer) {
            return echt.mitbauerSetzen(id, mitbauer);
        }

        @Override
        public void entfernen(long id) {
            echt.entfernen(id);
        }
    }

    private static final String WELT = "freimarken:marktstadt";

    @Nested
    @DisplayName("Haelt den Lagervertrag ein")
    class Vertrag extends BesitzLagerVertrag {
        @Override
        protected BesitzLager lager() {
            return new ZwischenspeicherLager(new SpeicherLager());
        }
    }

    @Test
    @DisplayName("Wiederholtes Nachsehen fragt das echte Lager nur einmal")
    void spartAbfragen() {
        ZaehlLager unten = new ZaehlLager();
        ZwischenspeicherLager speicher = new ZwischenspeicherLager(unten);

        for (int i = 0; i < 50; i++) {
            speicher.finde(WELT, 0, 0);
        }

        assertEquals(1, unten.abfragen.get(),
                "50 Blockpruefungen duerfen nicht 50 Abfragen ausloesen");
    }

    @Test
    @DisplayName("Auch ein leeres Ergebnis wird gemerkt - freie Parzellen sind der Normalfall")
    void merktAuchLeere() {
        ZaehlLager unten = new ZaehlLager();
        ZwischenspeicherLager speicher = new ZwischenspeicherLager(unten);

        assertTrue(speicher.finde(WELT, 5, 5).isEmpty());
        assertTrue(speicher.finde(WELT, 5, 5).isEmpty());

        assertEquals(1, unten.abfragen.get());
    }

    @Test
    @DisplayName("Nach dem Anlegen liefert der Zwischenspeicher sofort den neuen Stand")
    void anlegenLeert() {
        ZaehlLager unten = new ZaehlLager();
        ZwischenspeicherLager speicher = new ZwischenspeicherLager(unten);
        UUID anna = UUID.randomUUID();

        assertTrue(speicher.finde(WELT, 1, 1).isEmpty(), "erst frei");
        speicher.anlegen(WELT, 1, 1, anna, 0L);

        assertEquals(anna, speicher.finde(WELT, 1, 1).orElseThrow().eigentuemer(),
                "Ein veralteter Eintrag hiesse: der Besitzer kann auf seiner eigenen "
                        + "Parzelle nicht bauen");
    }

    @Test
    @DisplayName("Nach Mitbauer-Aenderung und Entfernen ebenso")
    void schreibenLeertImmer() {
        ZwischenspeicherLager speicher = new ZwischenspeicherLager(new SpeicherLager());
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        Parzelle parzelle = speicher.anlegen(WELT, 2, 2, anna, 0L);
        speicher.finde(WELT, 2, 2);

        speicher.mitbauerSetzen(parzelle.id(), Set.of(bert));
        assertTrue(speicher.finde(WELT, 2, 2).orElseThrow().darfBauen(bert));

        speicher.entfernen(parzelle.id());
        assertTrue(speicher.finde(WELT, 2, 2).isEmpty());
    }

    @Test
    @DisplayName("Ein Eintrag verfaellt - sonst waere ein zweiter Server dauerhaft falsch")
    void eintragVerfaellt() {
        ZaehlLager unten = new ZaehlLager();
        StellUhr uhr = new StellUhr();
        ZwischenspeicherLager speicher =
                new ZwischenspeicherLager(unten, uhr, Duration.ofSeconds(30));

        speicher.finde(WELT, 3, 3);
        speicher.finde(WELT, 3, 3);
        assertEquals(1, unten.abfragen.get());

        uhr.weiter(Duration.ofSeconds(31));
        speicher.finde(WELT, 3, 3);
        assertEquals(2, unten.abfragen.get(), "nach dem Verfall wird neu gefragt");
    }

    @Test
    @DisplayName("Leeren von aussen vergisst alles")
    void leeren() {
        ZwischenspeicherLager speicher = new ZwischenspeicherLager(new SpeicherLager());
        speicher.finde(WELT, 4, 4);
        assertEquals(1, speicher.eintraege());

        speicher.leeren();
        assertEquals(0, speicher.eintraege());
    }
}
