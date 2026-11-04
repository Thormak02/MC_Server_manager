package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Tagesgrenze ist die Rechnung, an der alles haengt, was je "je Tag" heisst.
 *
 * <p>Geprueft wird deshalb mit drei Zonen und ueber beide Zeitumstellungen - genau die
 * Faelle, in denen eine Rechnung mit 86.400 Sekunden je Tag richtig aussieht und falsch ist.
 */
class TagesgrenzeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");

    /** Die Summe der Anteile - die eine Zusage, auf die der Aufrufer baut. */
    private static long summe(List<Tagesgrenze.Tagesanteil> anteile) {
        return anteile.stream().mapToLong(Tagesgrenze.Tagesanteil::sekunden).sum();
    }

    @Test
    @DisplayName("Dieselbe Sekunde ist in drei Zonen an drei verschiedenen Tagen")
    void dreiZonenDreiTage() {
        // Das ist die Probe, die die ganze Tagesgrenze traegt: Wer den Tag in UTC rechnet,
        // nennt um 23:30 Uhr Ortszeit noch den Vortag.
        Instant abend = Instant.parse("2026-11-02T23:30:00Z");

        assertEquals(LocalDate.of(2026, 11, 3), Tagesgrenze.tagVon(abend, BERLIN),
                "in Berlin ist es schon nach Mitternacht");
        assertEquals(LocalDate.of(2026, 11, 2), Tagesgrenze.tagVon(abend, UTC));
        assertEquals(LocalDate.of(2026, 11, 3), Tagesgrenze.tagVon(abend, AUCKLAND),
                "in Auckland ist es Mittag des naechsten Tages");
    }

    @Test
    @DisplayName("Ein Takt ueber Mitternacht landet auf beiden Tagen")
    void ueberMitternacht() {
        Instant von = Instant.parse("2026-11-02T22:59:30Z");
        Instant bis = von.plusSeconds(60);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(2, anteile.size(), anteile.toString());
        assertEquals(new Tagesgrenze.Tagesanteil(LocalDate.of(2026, 11, 2), 30L),
                anteile.get(0));
        assertEquals(new Tagesgrenze.Tagesanteil(LocalDate.of(2026, 11, 3), 30L),
                anteile.get(1));
        assertEquals(60L, summe(anteile));
    }

    @Test
    @DisplayName("Die Summe der Anteile ist genau die verstrichene Zeit, auch mit Rest")
    void summeStimmtAuchMitRest() {
        // Hier faellt eine Rechnung durch, die jeden Tagesanteil einzeln abrundet: 29,6 s
        // vor und 30,4 s nach der Grenze ergaeben 29 + 30 = 59 statt 60.
        Instant von = Instant.parse("2026-11-02T22:59:30.400Z");
        Instant bis = von.plusSeconds(60);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(60L, summe(anteile),
                "die Summe muss die verstrichene Zeit sein, nicht weniger: " + anteile);
        assertEquals(29L, anteile.get(0).sekunden());
        assertEquals(31L, anteile.get(1).sekunden());
    }

    @Test
    @DisplayName("Die Nacht der Umstellung nach vorn hat 23 Stunden")
    void dreiundzwanzigStundenTag() {
        LocalDate umstellung = LocalDate.of(2026, 3, 29);
        Instant von = Tagesgrenze.tagesbeginn(umstellung, BERLIN);
        Instant bis = Tagesgrenze.tagesbeginn(umstellung.plusDays(1), BERLIN);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(1, anteile.size(), anteile.toString());
        assertEquals(23L * 3600L, anteile.getFirst().sekunden(),
                "eine Rechnung mit 86.400 Sekunden je Tag faellt hier durch");
        assertEquals(umstellung, anteile.getFirst().tag());
    }

    @Test
    @DisplayName("Die Nacht der Umstellung zurueck hat 25 Stunden")
    void fuenfundzwanzigStundenTag() {
        LocalDate umstellung = LocalDate.of(2026, 10, 25);
        Instant von = Tagesgrenze.tagesbeginn(umstellung, BERLIN);
        Instant bis = Tagesgrenze.tagesbeginn(umstellung.plusDays(1), BERLIN);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(1, anteile.size(), anteile.toString());
        assertEquals(25L * 3600L, anteile.getFirst().sekunden());
    }

    @Test
    @DisplayName("Eine Spanne ueber mehrere Tage und ueber die Umstellung geht nicht daneben")
    void mehrereTageUeberDieUmstellung() {
        // Ein Server, der drei Tage ohne Takt lief, ist ein Fehlerfall - aber die Regel
        // darf daran nicht zerbrechen.
        Instant von = Instant.parse("2026-10-24T20:17:03Z");
        long dauer = 3L * 86_400L + 1_234L;
        Instant bis = von.plusSeconds(dauer);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(dauer, summe(anteile), "die Summe bleibt die Spanne: " + anteile);
        assertEquals(4, anteile.size(), anteile.toString());
        for (Tagesgrenze.Tagesanteil anteil : anteile) {
            assertTrue(anteil.sekunden() > 0L, "kein Anteil ist leer: " + anteil);
        }
        assertTrue(anteile.get(1).sekunden() > anteile.get(2).sekunden(),
                "der 25-Stunden-Tag muss laenger sein als der Tag danach: " + anteile);
    }

    @Test
    @DisplayName("Die Tage kommen in zeitlicher Reihenfolge, der erste zuerst")
    void reihenfolge() {
        Instant von = Instant.parse("2026-11-01T12:00:00Z");
        Instant bis = Instant.parse("2026-11-04T06:00:00Z");

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(List.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                        LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4)),
                anteile.stream().map(Tagesgrenze.Tagesanteil::tag).toList());
    }

    @Test
    @DisplayName("Eine zurueckgestellte Uhr ergibt keine negative Zeit, sondern nichts")
    void nieNegativ() {
        Instant jetzt = Instant.parse("2026-11-03T10:00:00Z");

        assertEquals(List.of(), Tagesgrenze.teile(jetzt, jetzt.minusSeconds(3_600), BERLIN));
        assertEquals(List.of(), Tagesgrenze.teile(jetzt, jetzt, BERLIN),
                "eine Spanne ohne Dauer ist kein Tagesanteil");
    }

    @Test
    @DisplayName("Weniger als eine Sekunde ergibt keinen Anteil")
    void unterEinerSekunde() {
        Instant von = Instant.parse("2026-11-03T10:00:00Z");

        assertEquals(List.of(), Tagesgrenze.teile(von, von.plusMillis(500), BERLIN),
                "eine Zeile ohne Inhalt gehoert nicht in die Ablage");
    }

    @Test
    @DisplayName("Endet die Spanne genau auf Mitternacht, bekommt der neue Tag keine Zeile")
    void endeGenauAufDerGrenze() {
        Instant bis = Tagesgrenze.tagesbeginn(LocalDate.of(2026, 11, 3), BERLIN);
        Instant von = bis.minusSeconds(3_600);

        List<Tagesgrenze.Tagesanteil> anteile = Tagesgrenze.teile(von, bis, BERLIN);

        assertEquals(1, anteile.size(), anteile.toString());
        assertEquals(LocalDate.of(2026, 11, 2), anteile.getFirst().tag());
        assertEquals(3_600L, anteile.getFirst().sekunden());
    }

    @Test
    @DisplayName("Der Tagesbeginn ist der erste Augenblick des Tages in dieser Zone")
    void tagesbeginn() {
        assertEquals(Instant.parse("2026-11-02T23:00:00Z"),
                Tagesgrenze.tagesbeginn(LocalDate.of(2026, 11, 3), BERLIN));
        assertEquals(Instant.parse("2026-11-03T00:00:00Z"),
                Tagesgrenze.tagesbeginn(LocalDate.of(2026, 11, 3), UTC));
    }

    @Test
    @DisplayName("Die Klartextzeile nennt Ortszeit und UTC nebeneinander")
    void klartextzeile() {
        Instant jetzt = Instant.parse("2026-11-03T10:00:00Z");

        String zeile = Tagesgrenze.tageswechselText(jetzt, BERLIN);

        assertTrue(zeile.contains("Europe/Berlin"), zeile);
        assertTrue(zeile.contains("04.11.2026 00:00 Ortszeit"), zeile);
        assertTrue(zeile.contains("03.11.2026 23:00 UTC"), zeile);
    }

    @Test
    @DisplayName("Steht die Zone auf UTC, nennt die Zeile zweimal dieselbe Uhrzeit")
    void utcVerraetSichSelbst() {
        // Keine Pruefung faengt UTC statt Europe/Berlin - beides sind gueltige Zonen. Wer
        // die Zeile liest, sieht den Fehler.
        String zeile = Tagesgrenze.tageswechselText(
                Instant.parse("2026-11-03T10:00:00Z"), UTC);

        assertTrue(zeile.contains("04.11.2026 00:00 Ortszeit = 04.11.2026 00:00 UTC"), zeile);
    }

    @Test
    @DisplayName("Am Umstellungstag nennt die Zeile die Laenge des Tages")
    void umstellungstagStehtInDerZeile() {
        String kurz = Tagesgrenze.tageswechselText(
                Instant.parse("2026-03-29T10:00:00Z"), BERLIN);
        String lang = Tagesgrenze.tageswechselText(
                Instant.parse("2026-10-25T10:00:00Z"), BERLIN);
        String normal = Tagesgrenze.tageswechselText(
                Instant.parse("2026-11-03T10:00:00Z"), BERLIN);

        assertTrue(kurz.contains("23 Stunden"), kurz);
        assertTrue(lang.contains("25 Stunden"), lang);
        assertFalse(normal.contains("Stunden"),
                "an einem gewoehnlichen Tag steht der Zusatz nicht da: " + normal);
    }

    @Test
    @DisplayName("Ohne Zeitpunkt und ohne Zone gibt es keinen Tag")
    void pflichtangaben() {
        Instant jetzt = Instant.parse("2026-11-03T10:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> Tagesgrenze.tagVon(null, BERLIN));
        assertThrows(IllegalArgumentException.class, () -> Tagesgrenze.tagVon(jetzt, null));
        assertThrows(IllegalArgumentException.class,
                () -> Tagesgrenze.teile(null, jetzt, BERLIN));
        assertThrows(IllegalArgumentException.class,
                () -> Tagesgrenze.teile(jetzt, null, BERLIN));
        assertThrows(IllegalArgumentException.class,
                () -> Tagesgrenze.tagesbeginn(null, BERLIN));
        assertThrows(IllegalArgumentException.class,
                () -> Tagesgrenze.tageswechselText(jetzt, null));
    }

    @Test
    @DisplayName("Die Spanne eines ganzen gewoehnlichen Tages ist 24 Stunden")
    void gewoehnlicherTag() {
        LocalDate tag = LocalDate.of(2026, 11, 3);

        assertEquals(Duration.ofHours(24),
                Duration.between(Tagesgrenze.tagesbeginn(tag, BERLIN),
                        Tagesgrenze.tagesbeginn(tag.plusDays(1), BERLIN)));
    }
}
