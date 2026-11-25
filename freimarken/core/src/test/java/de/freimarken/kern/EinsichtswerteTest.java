package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Frist der Einsicht - und die zwei Grenzen, die sie zur Bremse machen. */
class EinsichtswerteTest {

    @Test
    @DisplayName("Die Vorgabe ist fuenf Minuten und klaglos")
    void vorgabeIstFuenfMinuten() {
        assertEquals(300L, Einsichtswerte.VORGABE.dauerSekunden());
        assertTrue(Einsichtswerte.VORGABE.ungereimtheiten().isEmpty(),
                Einsichtswerte.VORGABE.ungereimtheiten().toString());
    }

    @Test
    @DisplayName("Zu kurz und zu lang werden abgewiesen")
    void grenzenWerdenAbgewiesen() {
        // Unter zehn Sekunden ist der Modus abgelaufen, bevor der Moderator die Kiste
        // erreicht; ueber einer Stunde macht die Zahl aus der Bremse das Gegenteil.
        assertThrows(IllegalArgumentException.class, () -> new Einsichtswerte(0L));
        assertThrows(IllegalArgumentException.class, () -> new Einsichtswerte(9L));
        assertThrows(IllegalArgumentException.class, () -> new Einsichtswerte(-1L));
        assertThrows(IllegalArgumentException.class, () -> new Einsichtswerte(3601L));
        assertEquals(10L, new Einsichtswerte(10L).dauerSekunden(), "genau zehn geht");
        assertEquals(3600L, new Einsichtswerte(3600L).dauerSekunden(),
                "und genau eine Stunde auch");
    }

    @Test
    @DisplayName("brauchbareDauer sagt dasselbe wie der Konstruktor")
    void brauchbareDauerStimmtMitDemKonstruktor() {
        // Zwei Wahrheiten darueber, was brauchbar ist, waeren eine Klage beim Laden
        // und eine Zahl, die der Leser vorher durchgelassen hat.
        for (long sekunden : new long[] {-1L, 0L, 9L, 10L, 300L, 3600L, 3601L}) {
            boolean erlaubt = Einsichtswerte.brauchbareDauer(sekunden);
            if (erlaubt) {
                assertEquals(sekunden, new Einsichtswerte(sekunden).dauerSekunden(),
                        sekunden + " gilt als brauchbar");
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> new Einsichtswerte(sekunden),
                        sekunden + " gilt als unbrauchbar - dann muss der Konstruktor"
                                + " werfen");
            }
        }
    }

    @Test
    @DisplayName("Eine lange Frist wird GEMELDET, nicht geklagt")
    void langeFristWirdGemeldet() {
        Einsichtswerte werte = Einsichtswerte.VORGABE.mitDauer(1800L);

        assertTrue(werte.ungereimtheiten().stream()
                        .anyMatch(satz -> satz.contains("vergisst")),
                "ein Moderator, der es vergisst, sieht so lange in jede fremde Kiste: "
                        + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("Eine sehr kurze Frist wird auch gemeldet")
    void kurzeFristWirdGemeldet() {
        Einsichtswerte werte = Einsichtswerte.VORGABE.mitDauer(15L);

        assertFalse(werte.ungereimtheiten().isEmpty(),
                "15 Sekunden reichen selten fuer Hinspringen und Umsehen");
    }

    @Test
    @DisplayName("Die Vorgabe liegt zwischen den beiden Meldungen")
    void vorgabeLiegtDazwischen() {
        // Die Gegenrichtung zu den zwei Proben darueber: Waere die Vorgabe selbst
        // auffaellig, stuende bei JEDEM Start eine Berichtzeile - und beim dritten Mal
        // liest sie niemand mehr.
        assertTrue(Einsichtswerte.VORGABE.dauerSekunden() > 30L);
        assertTrue(Einsichtswerte.VORGABE.dauerSekunden() < 1800L);
    }
}
