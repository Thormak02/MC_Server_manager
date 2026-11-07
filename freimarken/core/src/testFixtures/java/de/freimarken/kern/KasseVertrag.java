package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Kasse einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link BesitzLagerVertrag}: Die Umsetzung bringt ihre eigene
 * Testklasse mit und erbt diese Pruefungen. Dadurch wird eine Umsetzung, die sich anders
 * verhaelt als die andere, in einem Testlauf sichtbar und nicht im Betrieb.
 */
public abstract class KasseVertrag {

    /**
     * Der Grund, nach dem die Tagessumme meistens gefragt wird.
     *
     * <p>Eine Abkuerzung und kein zweiter Begriff: {@code summeSeit} nimmt den Grund von
     * aussen, weil es ZWEI Tagesdeckel gibt - einen auf die Arbeit (Teil E) und einen auf
     * den Verkauf an den Bundesmarkt (Teil F).
     */
    protected static final Buchungsgrund ARBEIT = Buchungsgrund.BERUF_VERDIENST;

    /** Das Startguthaben, mit dem die geprueften Kassen aufgesetzt werden. */
    protected static final long START = 50_000L;

    /** Kurz fuer die Proben: eine Ausgabe und eine Einnahme. */
    private static final Buchungsgrund KAUF = Buchungsgrund.PARZELLE_GEKAUFT;
    private static final Buchungsgrund ERSTATTUNG = Buchungsgrund.PARZELLE_ERSTATTET;

    /** Eine frische, leere Kasse mit {@link #START} als Startguthaben. */
    protected abstract Kasse kasse();

    @Test
    @DisplayName("Ein unbekannter Spieler hat das Startguthaben")
    void unbekannterHatStart() {
        assertEquals(START, kasse().guthaben(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Abbuchen senkt das Guthaben um genau den Betrag")
    void abbuchenSenkt() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, 20_000L, KAUF));

        assertEquals(START - 20_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Ein ungedeckter Betrag wird nicht halb abgebucht")
    void ungedecktAendertNichts() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertFalse(kasse.abbuchen(anna, START + 1, KAUF));

        assertEquals(START, kasse.guthaben(anna),
                "nach einem abgelehnten Abbuchen muss der Stand unveraendert sein");
    }

    @Test
    @DisplayName("Genau das Guthaben laesst sich abbuchen, bis auf null")
    void genauAufgebraucht() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, START, KAUF));

        assertEquals(0L, kasse.guthaben(anna));
        assertFalse(kasse.abbuchen(anna, 1L, KAUF), "von null geht nichts mehr ab");
    }

    @Test
    @DisplayName("Null abbuchen geht immer - auch von einem leeren Konto")
    void nullGehtImmer() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        assertTrue(kasse.abbuchen(anna, START, KAUF));

        assertTrue(kasse.abbuchen(anna, 0L, KAUF),
                "sonst muesste jede Gratis-Regel den Sonderfall selbst kennen");
        assertEquals(0L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Gutschreiben hebt das Guthaben, auch fuer einen unbekannten Spieler")
    void gutschreibenHebt() {
        Kasse kasse = kasse();
        UUID neu = UUID.randomUUID();

        kasse.gutschreiben(neu, 1_000L, ERSTATTUNG);

        assertEquals(START + 1_000L, kasse.guthaben(neu));
    }

    @Test
    @DisplayName("Jeder Spieler hat sein eigenes Konto")
    void kontenSindGetrennt() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, 30_000L, KAUF));

        assertEquals(START - 30_000L, kasse.guthaben(anna));
        assertEquals(START, kasse.guthaben(bert));
    }

    @Test
    @DisplayName("Ein negativer Betrag ist ein Fehler, keine verkappte Gegenbuchung")
    void negativFliegtAuf() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> kasse.abbuchen(anna, -1L, KAUF));
        assertThrows(IllegalArgumentException.class, () -> kasse.gutschreiben(anna, -1L, ERSTATTUNG));

        assertEquals(START, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Die Summe aller Buchungen ist der Kontostand")
    void summeIstDerStand() {
        // Die eine Zusicherung, die BEIDE Luegen sichtbar macht - eine Buchung ohne Zeile
        // und eine Zeile ohne Buchung -, ohne jede einzelne Buchungsart zu kennen. Alles
        // andere in dieser Klasse prueft Teile; das hier haelt das Ganze zusammen.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        kasse.eroeffne(anna);
        assertTrue(kasse.abbuchen(anna, 20_000L, KAUF));
        kasse.gutschreiben(anna, 5_000L, ERSTATTUNG);
        assertTrue(kasse.abbuchen(anna, 0L, KAUF));
        assertFalse(kasse.abbuchen(anna, 999_999L, KAUF), "ungedeckt, also nichts");

        long summe = kasse.verlauf(anna, 100).stream().mapToLong(Buchung::betrag).sum();

        assertEquals(kasse.guthaben(anna), summe,
                "Summe aller Buchungen muss der Kontostand sein");
    }

    @Test
    @DisplayName("Jede Buchung hinterlaesst genau eine Zeile - mit Grund, Betrag und Stand")
    void jedeBuchungEineZeile() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.eroeffne(anna);

        assertTrue(kasse.abbuchen(anna, 20_000L, KAUF));

        Buchung letzte = kasse.verlauf(anna, 1).get(0);
        assertEquals(KAUF, letzte.grund());
        assertEquals(-20_000L, letzte.betrag(), "eine Ausgabe steht negativ im Buch");
        assertEquals(START - 20_000L, letzte.standNachher());
        assertEquals(anna, letzte.spieler());

        kasse.gutschreiben(anna, 5_000L, ERSTATTUNG);

        Buchung gutschrift = kasse.verlauf(anna, 1).get(0);
        assertEquals(ERSTATTUNG, gutschrift.grund());
        assertEquals(5_000L, gutschrift.betrag(), "eine Einnahme steht positiv im Buch");
        assertEquals(START - 15_000L, gutschrift.standNachher());
    }

    @Test
    @DisplayName("Ein abgelehntes Abbuchen hinterlaesst keine Zeile")
    void abgelehntesAbbuchenSchreibtNichts() {
        // Das Buch fuehrt Geldbewegungen. Eine Absage ist keine - und eine Zeile ohne
        // Bewegung braeche die Summenprobe, die das Buch zusammenhaelt.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.eroeffne(anna);
        int vorher = kasse.verlauf(anna, 100).size();

        assertFalse(kasse.abbuchen(anna, START + 1, KAUF));

        assertEquals(vorher, kasse.verlauf(anna, 100).size());
    }

    @Test
    @DisplayName("Eroeffnen ist mehrfach aufrufbar und legt nur eine Eroeffnungsbuchung an")
    void eroeffnenNurEinmal() {
        // Die Ankunftswache ruft es bei JEDEM Beitritt. Ein zweiter Eintrag waere
        // erfundenes Geld.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.eroeffne(anna), "das erste Mal eroeffnet");
        assertFalse(kasse.eroeffne(anna), "danach nicht mehr");
        assertFalse(kasse.eroeffne(anna));

        List<Buchung> verlauf = kasse.verlauf(anna, 100);
        assertEquals(1, verlauf.size());
        assertEquals(Buchungsgrund.EROEFFNUNG, verlauf.get(0).grund());
        assertEquals(START, verlauf.get(0).betrag());
        assertEquals(START, verlauf.get(0).standNachher());
    }

    @Test
    @DisplayName("Auch eine Buchung ohne Eroeffnung traegt das Buch - der Stand kommt mit")
    void buchungOhneEroeffnung() {
        // Ein unbekannter Spieler hat das Startguthaben und kann es ausgeben. Wuerde die
        // Buchung das Konto nicht eroeffnen, enthielte der Stand das Startguthaben und
        // das Buch nicht - die Summenprobe waere schon beim ersten Kauf falsch.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, 1_000L, KAUF));

        long summe = kasse.verlauf(anna, 100).stream().mapToLong(Buchung::betrag).sum();
        assertEquals(kasse.guthaben(anna), summe);
        assertEquals(2, kasse.verlauf(anna, 100).size(), "Eroeffnung und Kauf");
    }

    @Test
    @DisplayName("Der Verlauf nennt die neueste Zeile zuerst und haelt die Obergrenze ein")
    void verlaufReihenfolgeUndGrenze() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.eroeffne(anna);
        for (int i = 0; i < 5; i++) {
            kasse.gutschreiben(anna, 100L, ERSTATTUNG);
        }

        List<Buchung> drei = kasse.verlauf(anna, 3);

        assertEquals(3, drei.size());
        assertEquals(START + 500L, drei.get(0).standNachher(), "die neueste zuerst");
        assertEquals(START + 400L, drei.get(1).standNachher());
        assertEquals(START + 300L, drei.get(2).standNachher());
    }

    @Test
    @DisplayName("Eine Obergrenze unter eins ist ein Fehler")
    void verlaufBrauchtEineObergrenze() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> kasse.verlauf(anna, 0));
        assertThrows(IllegalArgumentException.class, () -> kasse.verlauf(anna, -1));
    }

    // --- Die Tagessumme aus Arbeit --------------------------------------------

    @Test
    @DisplayName("Ohne Arbeit ist die Tagessumme 0")
    void ohneArbeitKeineTagessumme() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.gutschreiben(anna, 5_000L, ERSTATTUNG);
        assertTrue(kasse.abbuchen(anna, 1_000L, KAUF));

        // Genau BERUF_VERDIENST und kein anderer Grund: Der Tagesdeckel deckelt Arbeit
        // und nicht ein Geschenk, eine Erstattung oder das Startguthaben. Zaehlte er
        // alles, waere ein Spieler nach einer Parzellenerstattung fuer den Rest des Tages
        // gesperrt.
        assertEquals(0L, kasse.summeSeit(anna, Instant.EPOCH, ARBEIT));
    }

    @Test
    @DisplayName("Was aus Arbeit kam, zaehlt die Tagessumme - und nur das")
    void arbeitZaehlt() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        kasse.gutschreiben(anna, 300L, Buchungsgrund.BERUF_VERDIENST);
        kasse.gutschreiben(anna, 200L, Buchungsgrund.BERUF_VERDIENST);
        kasse.gutschreiben(anna, 9_000L, ERSTATTUNG);

        assertEquals(500L, kasse.summeSeit(anna, Instant.EPOCH, ARBEIT));
    }

    @Test
    @DisplayName("Jeder Spieler hat seine eigene Tagessumme")
    void jederSeineTagessumme() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        kasse.gutschreiben(anna, 700L, Buchungsgrund.BERUF_VERDIENST);

        assertEquals(700L, kasse.summeSeit(anna, Instant.EPOCH, ARBEIT));
        assertEquals(0L, kasse.summeSeit(bert, Instant.EPOCH, ARBEIT));
    }

    @Test
    @DisplayName("Der Zeitpunkt grenzt ab - was davor liegt, zaehlt nicht mehr")
    void derZeitpunktGrenztAb() {
        // Daran haengt die Tagesgrenze: Gefragt wird ab Mitternacht Ortszeit, und was
        // gestern verdient wurde, darf den heutigen Deckel nicht fuellen. Ein Zeitpunkt
        // in der Zukunft muss darum 0 ergeben und nicht alles.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.gutschreiben(anna, 400L, Buchungsgrund.BERUF_VERDIENST);

        assertEquals(400L, kasse.summeSeit(anna, Instant.EPOCH, ARBEIT));
        assertEquals(0L, kasse.summeSeit(anna,
                Instant.now().plus(Duration.ofDays(1L)), ARBEIT));
    }

    @Test
    @DisplayName("Die Abgrenzung liegt auf der Minute - nicht auf dem Ortsversatz")
    void abgrenzungAufDieMinute() {
        // Eine halbe Stunde statt eines ganzen Tages, und das ist der Zweck: Legte die
        // Datenschicht die Zone der JVM ueber die UTC-Spalte, verschobe sich die Grenze
        // um den Ortsversatz - in Europa um eine oder zwei Stunden. Dann griffe der
        // Tagesdeckel eine Stunde zu frueh oder zu spaet, und zwar nur fuer die Spieler,
        // die in dieser Stunde arbeiten. Mit einem Tag Abstand faellt das nicht auf.
        //
        // Gefragt wird um den Zeitpunkt der BUCHUNG und nicht um Instant.now(): Die
        // Kasse in MariaDB bekommt ihre Zeit von der Datenbank, und zwei Uhren, die
        // nicht genau gleich stehen, machten diese Probe wackelig statt scharf.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        kasse.gutschreiben(anna, 400L, Buchungsgrund.BERUF_VERDIENST);
        Instant gebuchtUm = kasse.verlauf(anna, 1).get(0).zeitpunkt();

        assertEquals(400L,
                kasse.summeSeit(anna, gebuchtUm.minus(Duration.ofMinutes(30L)),
                        ARBEIT));
        assertEquals(0L,
                kasse.summeSeit(anna, gebuchtUm.plus(Duration.ofMinutes(30L)),
                        ARBEIT));
    }

    @Test
    @DisplayName("Jeder Grund hat seine EIGENE Tagessumme")
    void jederGrundSeineSumme() {
        // Die Zusage, auf der der zweite Tagesdeckel steht: Teil E deckelt die Arbeit,
        // Teil F den Verkauf an den Bundesmarkt. Zaehlte eine Summe beide, waere jeder
        // der zwei Deckel durch die ANDERE Taetigkeit zu fuellen - wer am Markt verkauft,
        // koennte danach nicht mehr arbeiten.
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        kasse.gutschreiben(anna, 300L, Buchungsgrund.BERUF_VERDIENST);
        kasse.gutschreiben(anna, 700L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        kasse.gutschreiben(anna, 5_000L, ERSTATTUNG);

        assertEquals(300L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.BERUF_VERDIENST));
        assertEquals(700L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.BUNDESMARKT_VERKAUF));
        // Und ein Grund ohne Buchung ist 0 und nicht die Summe der anderen. NICHT
        // EROEFFNUNG: Darunter steht das Startguthaben, und das ist eine Buchung -
        // gemessen, nicht vermutet.
        assertEquals(0L, kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.PARZELLE_GEKAUFT));
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Zeitpunkt gibt es keine Tagessumme")
    void tagessummePflichten() {
        Kasse kasse = kasse();

        assertThrows(IllegalArgumentException.class,
                () -> kasse.summeSeit(null, Instant.EPOCH, ARBEIT));
        assertThrows(IllegalArgumentException.class,
                () -> kasse.summeSeit(UUID.randomUUID(), null, ARBEIT));
        // Und ohne Grund: Eine Tagessumme ueber ALLE Gruende waere ein Deckel, den ein
        // Geschenk fuellt.
        assertThrows(IllegalArgumentException.class,
                () -> kasse.summeSeit(UUID.randomUUID(), Instant.EPOCH, null));
    }

    @Test
    @DisplayName("Viele Buchungen hintereinander ergeben die Summe")
    void vieleBuchungen() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            assertTrue(kasse.abbuchen(anna, 1_000L, KAUF));
            kasse.gutschreiben(anna, 400L, ERSTATTUNG);
        }

        assertEquals(START - 6_000L, kasse.guthaben(anna));
    }
}
