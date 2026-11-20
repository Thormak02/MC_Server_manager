package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Zahlen der Wechselstube - der Rundlaufriegel und die drei Schranken. */
@DisplayName("Die Wechselwerte")
class WechselwerteTest {

    @Test
    @DisplayName("Die Vorgabe ist genau das, was Plan 8.1 entschieden hat")
    void vorgabeIstDerPlan() {
        // Zeichengenau gegen den Plan und nicht gegen die eigenen Konstanten: Eine Probe,
        // die VORGABE_HIN gegen markJeGoldmark vergleicht, ist mit jeder Zahl gruen.
        assertEquals(1_000L, Wechselwerte.VORGABE.markJeGoldmark());
        assertEquals(800L, Wechselwerte.VORGABE.markZurueck());
        assertEquals(10_000L, Wechselwerte.VORGABE.tagesdeckel());
    }

    @Test
    @DisplayName("Die Zusage des Plans: 400.000 Mark brauchen mindestens 40 Tage")
    void vierzigTageFuerEineFreistadt() {
        // DIE RECHENPROBE zu den drei Zahlen oben, und sie ist der Grund, warum sie so
        // stehen: Plan 8.1 verspricht "pay to fast, nicht pay to win" mit genau dieser
        // Rechnung. Wer den Deckel hochdreht, soll hier aufschlagen und nicht erst, wenn
        // am dritten Tag die erste gekaufte Freistadt steht.
        long gruendungspreis = Freistadtwerte.VORGABE_GRUENDUNGSPREIS;
        long tage = gruendungspreis / Wechselwerte.VORGABE_DECKEL;

        assertEquals(400_000L, gruendungspreis, "der Plan nennt 400.000 Mark");
        assertEquals(40L, tage, "bei " + Wechselwerte.VORGABE_DECKEL + " Mark am Tag"
                + " braucht eine Freistadt " + tage + " Tage - der Plan verspricht 40");
    }

    @Test
    @DisplayName("Ein Rueckweg, der MEHR bringt als der Hinweg kostet, kommt nicht zustande")
    void rundlaufAbgewiesen() {
        // DER RIEGEL DIESES BAUSCHRITTES: 1.000 Mark hinein, eine Goldmark, 1.200 Mark
        // heraus - und das beliebig oft. Jede einzelne Buchung ist dabei richtig, und die
        // Summenprobe des Buchungsbuchs bestaetigt jeden Stand.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Wechselwerte(1_000L, 1_200L, 10_000L));

        assertTrue(fehler.getMessage().contains("Rundlauf"), fehler.getMessage());
        assertTrue(fehler.getMessage().contains("200"),
                "die Meldung muss den GEWINN je Goldmark nennen: " + fehler.getMessage());
    }

    @Test
    @DisplayName("Derselbe Kurs in beide Richtungen ist erlaubt - knapp")
    void gleicherKursErlaubt() {
        // Die Gegenrichtung zum Riegel, und sie sitzt genau auf der Grenze: Ein Riegel
        // auf >= waere mit der Probe darueber genauso gruen und verboete eine Einstellung,
        // die nur verlustfrei ist und kein Geld erzeugt.
        Wechselwerte gleich = new Wechselwerte(1_000L, 1_000L, 10_000L);

        assertEquals(1_000L, gleich.erloes(1L));
        assertEquals(1_000L, gleich.kaufpreis(1L));
    }

    @Test
    @DisplayName("Ein Kurs von 0 kommt in keiner Richtung durch")
    void kursNullAbgewiesen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wechselwerte(0L, 0L, 10_000L));
        var rueck = assertThrows(IllegalArgumentException.class,
                () -> new Wechselwerte(1_000L, 0L, 10_000L));

        // Die Meldung muss den WEG nennen, den der Betreiber wirklich meint: Wer den
        // Rueckweg schliessen will, setzt den Deckel auf 0 und nicht den Kurs.
        assertTrue(rueck.getMessage().contains("Tagesdeckel"), rueck.getMessage());
    }

    @Test
    @DisplayName("Ein Kurs ueber der Schranke laeuft auf - sonst liefe das Produkt ueber")
    void kursUeberDerSchranke() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wechselwerte(Wechselwerte.HOECHSTER_KURS + 1L, 800L, 10_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Wechselwerte(-1L, -1L, 10_000L));
    }

    @Test
    @DisplayName("Das groesste erlaubte Produkt laeuft nicht ueber")
    void groesstesProduktBleibtPositiv() {
        // Die Schranken von HOECHSTER_KURS und HOECHSTE_ANZAHL sind kein Geschmack,
        // sondern Platz. Ein uebergelaufenes Produkt waere NEGATIV - also eine
        // Gutschrift beim Kaufen.
        Wechselwerte gross = new Wechselwerte(Wechselwerte.HOECHSTER_KURS,
                Wechselwerte.HOECHSTER_KURS, Wechselwerte.HOECHSTER_DECKEL);

        assertTrue(gross.kaufpreis(Wechselwerte.HOECHSTE_ANZAHL) > 0L,
                "das groesste Produkt muss positiv bleiben");
        assertTrue(gross.erloes(Wechselwerte.HOECHSTE_ANZAHL) > 0L);
    }

    @Test
    @DisplayName("Ein Deckel von 0 heisst: der Rueckweg ist zu")
    void deckelNullSchliesstDenRueckweg() {
        Wechselwerte zu = Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU);

        assertFalse(zu.rueckwegOffen());
        assertEquals(0L, zu.freiHeute(0L));
        assertEquals(0L, zu.nochMoeglichHeute(0L));
        // UND DER HINWEG BLEIBT OFFEN: Eine 0 schliesst den Rueckweg und nicht die
        // Wechselstube. Wer beides verwechselt, nimmt dem Betreiber die Moeglichkeit,
        // Goldmark erspielbar zu halten (Plan 8.4).
        assertEquals(1_000L, zu.kaufpreis(1L));
    }

    @Test
    @DisplayName("Ein Deckel ueber der Schranke oder unter 0 kommt nicht zustande")
    void deckelAusserhalb() {
        assertThrows(IllegalArgumentException.class,
                () -> Wechselwerte.VORGABE.mitDeckel(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> Wechselwerte.VORGABE.mitDeckel(Wechselwerte.HOECHSTER_DECKEL + 1L));
    }

    @Test
    @DisplayName("Kaufpreis und Erloes rechnen je Goldmark - und sind nicht dasselbe")
    void kaufpreisUndErloes() {
        assertEquals(5_000L, Wechselwerte.VORGABE.kaufpreis(5L));
        assertEquals(4_000L, Wechselwerte.VORGABE.erloes(5L));
    }

    @Test
    @DisplayName("Ein Tausch ueber 0 oder ueber die Schranke ist keiner")
    void anzahlAusserhalb() {
        assertThrows(IllegalArgumentException.class,
                () -> Wechselwerte.VORGABE.kaufpreis(0L));
        assertThrows(IllegalArgumentException.class,
                () -> Wechselwerte.VORGABE.erloes(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> Wechselwerte.VORGABE.kaufpreis(Wechselwerte.HOECHSTE_ANZAHL + 1L));
    }

    @Test
    @DisplayName("Der Deckel wird in Goldmark ABGERUNDET - zwoelf und nicht zwoelf Komma fuenf")
    void deckelWirdAbgerundet() {
        // 10.000 Mark Deckel, 800 Mark je Goldmark: Zwoelf gehen (9.600 Mark), die
        // dreizehnte waere 10.400 und damit darueber. Eine halbe Goldmark gibt es nicht.
        assertEquals(12L, Wechselwerte.VORGABE.nochMoeglichHeute(0L));
        assertEquals(10_000L, Wechselwerte.VORGABE.freiHeute(0L));
    }

    @Test
    @DisplayName("Ein Rest unter einem Kurs ist keine Goldmark mehr")
    void restUnterEinemKurs() {
        // 9.600 schon gewechselt, 400 Mark frei - das ist weniger als eine Goldmark.
        assertEquals(400L, Wechselwerte.VORGABE.freiHeute(9_600L));
        assertEquals(0L, Wechselwerte.VORGABE.nochMoeglichHeute(9_600L));
    }

    @Test
    @DisplayName("Mehr verbraucht als erlaubt gibt 0 und keine negative Zahl")
    void ueberdeckelGibtNull() {
        // Nicht erreichbar, solange nur diese Regel bucht - aber ein geaenderter Deckel
        // macht aus einem gefuellten Tag sofort einen uebervollen, und eine negative
        // Antwort waere dann eine Gutschrift auf dem Weg einer Grenze.
        assertEquals(0L, Wechselwerte.VORGABE.freiHeute(20_000L));
        assertEquals(0L, Wechselwerte.VORGABE.nochMoeglichHeute(20_000L));
    }

    @Test
    @DisplayName("Die Abwandler lassen genau das stehen, was sie nicht aendern")
    void abwandler() {
        Wechselwerte andere = Wechselwerte.VORGABE.mitKursen(2_000L, 1_500L);

        assertEquals(2_000L, andere.markJeGoldmark());
        assertEquals(1_500L, andere.markZurueck());
        assertEquals(10_000L, andere.tagesdeckel());
        assertEquals(1_000L, Wechselwerte.VORGABE.markJeGoldmark(),
                "der Abwandler darf die Vorgabe nicht anfassen");
    }
}
