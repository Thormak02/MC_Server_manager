package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Die Mietwerte")
class MietwerteTest {

    @Test
    @DisplayName("Die Vorgabe ist die Tafel aus Plan 7.1")
    void vorgabeIstDiePlanTafel() {
        // Die vier Zahlen EINZELN und nicht gegen VORGABE verglichen: Sonst wuerde die
        // Probe nur sagen, dass die Vorgabe sich selbst gleicht.
        assertEquals(500L, Mietwerte.VORGABE.miete());
        assertEquals(5_000L, Mietwerte.VORGABE.kaution());
        assertEquals(50_000L, Mietwerte.VORGABE.lizenz());
        assertEquals(7, Mietwerte.VORGABE.dauerTage());
    }

    @Test
    @DisplayName("Die Pfade heissen wie in der config.yml")
    void pfade() {
        assertEquals("laeden.miete-je-tag", Mietwerte.PFAD_MIETE);
        assertEquals("laeden.kaution", Mietwerte.PFAD_KAUTION);
        assertEquals("laeden.lizenz", Mietwerte.PFAD_LIZENZ);
        assertEquals("laeden.mietdauer-tage", Mietwerte.PFAD_DAUER);
    }

    @Test
    @DisplayName("Der volle Zeitraum ist die Miete von 7 Tagen")
    void vollerZeitraum() {
        assertEquals(3_500L, Mietwerte.VORGABE.vollerZeitraum());
    }

    @Test
    @DisplayName("Die Lizenz zahlt nur, wer sie noch nicht hat")
    void abschlusskosten() {
        assertEquals(55_000L, Mietwerte.VORGABE.abschlusskosten(false), "beim ersten Mal");
        assertEquals(5_000L, Mietwerte.VORGABE.abschlusskosten(true), "danach nur Kaution");
    }

    @Test
    @DisplayName("Ohne Kaution und ohne Lizenz sagen die Fragen das")
    void fragenAntwortenAufNull() {
        Mietwerte ohne = Mietwerte.VORGABE.mitKaution(0L).mitLizenz(0L);

        assertFalse(ohne.mitKaution());
        assertFalse(ohne.kostetLizenz());
        assertTrue(Mietwerte.VORGABE.mitKaution());
        assertTrue(Mietwerte.VORGABE.kostetLizenz());
        assertEquals(0L, ohne.abschlusskosten(false), "dann kostet der Abschluss nichts");
    }

    @Test
    @DisplayName("Jeder Wither aendert GENAU seinen Wert")
    void witherAendernNurEinen() {
        // Elf Zusicherungen und nicht vier: Bei einem Wither, der die falsche Stelle
        // belegt, waere eine Probe, die nur den geaenderten Wert liest, gruen.
        Mietwerte miete = Mietwerte.VORGABE.mitMiete(900L);
        assertEquals(900L, miete.miete());
        assertEquals(5_000L, miete.kaution());
        assertEquals(50_000L, miete.lizenz());
        assertEquals(7, miete.dauerTage());

        Mietwerte kaution = Mietwerte.VORGABE.mitKaution(9_000L);
        assertEquals(500L, kaution.miete());
        assertEquals(9_000L, kaution.kaution());

        Mietwerte lizenz = Mietwerte.VORGABE.mitLizenz(900L);
        assertEquals(900L, lizenz.lizenz());
        assertEquals(5_000L, lizenz.kaution());

        // 14 und nicht 7: Seit dem 08.11.2026 IST 7 die Vorgabe, und ein Wither, der
        // auf seinen eigenen Ausgangswert setzt, ist auch dann gruen, wenn er nichts tut.
        Mietwerte dauer = Mietwerte.VORGABE.mitDauer(14);
        assertEquals(14, dauer.dauerTage());
        assertEquals(500L, dauer.miete());
    }

    @Test
    @DisplayName("Eine Kaution UNTER der Tagesmiete wird abgewiesen")
    void kautionUnterMieteGehtNicht() {
        // Das Paar, das jeder Wert FUER SICH erlaubt waere: 400 Mark Kaution sind eine
        // gueltige Zahl, 500 Mark Miete auch - zusammen kostet das Weglaufen weniger als
        // ein Miettag, und die Kaution bewirkt das Gegenteil von dem, wofuer sie da ist.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 400L, 50_000L, 30, 3));

        assertTrue(wurf.getMessage().contains("billigere Weg"),
                "die Meldung sagt, WAS daran falsch ist: " + wurf.getMessage());
    }

    @Test
    @DisplayName("Eine Kaution GENAU in Hoehe der Miete geht")
    void kautionGleichMieteGeht() {
        // Der Rand des Paares: Ein > statt >= haette hier eine Zahl verboten, die genau
        // aufgeht.
        assertEquals(500L, new Mietwerte(500L, 500L, 0L, 30, 3).kaution());
        assertTrue(Mietwerte.brauchbaresPaar(500L, 500L));
        assertFalse(Mietwerte.brauchbaresPaar(499L, 500L));
    }

    @Test
    @DisplayName("Gar KEINE Kaution ist erlaubt - das ist eine Entscheidung")
    void keineKautionIstErlaubt() {
        // Die 0 muss durch, obwohl sie unter jeder Miete liegt: "ohne Kaution" ist eine
        // Entscheidung des Betreibers und kein Versehen. Eine Fassung, die nur
        // kaution >= miete prueft, waere bei der Probe darueber gruen und wuerde hier
        // einen Start mit kaution: 0 verhindern.
        Mietwerte ohne = new Mietwerte(500L, 0L, 50_000L, 30, 3);

        assertEquals(0L, ohne.kaution());
        assertTrue(Mietwerte.brauchbaresPaar(0L, 500L));
    }

    @Test
    @DisplayName("Eine Miete von 0 wird abgewiesen, eine Lizenz von 0 nicht")
    void nullenAnDerRichtigenStelle() {
        // Der Unterschied ist die WIRKUNG: Eine Miete von 0 waere ein Laden, den der
        // taegliche Lauf nie beruehrt - also einer, der sich nie raeumen laesst. Eine
        // Lizenz von 0 heisst nur, dass das Recht genuegt.
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(0L, 5_000L, 50_000L, 30, 3));
        assertEquals(0L, new Mietwerte(500L, 5_000L, 0L, 30, 3).lizenz());
    }

    @Test
    @DisplayName("Negative Betraege und eine Dauer von 0 werden abgewiesen")
    void unbrauchbareWerte() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(-1L, 5_000L, 50_000L, 30, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, -1L, 50_000L, 30, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, -1L, 30, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, 50_000L, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, 50_000L, 367, 3));
    }

    @Test
    @DisplayName("Betraege ueber dem Hoechstwert werden abgewiesen")
    void ueberDemHoechstwert() {
        long zuViel = Mietwerte.HOECHSTER_BETRAG + 1L;

        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(zuViel, zuViel, 50_000L, 30, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, zuViel, 50_000L, 30, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, zuViel, 30, 3));
        // Der erlaubte Rand geht noch durch, samt der Kaution, die ihn halten muss.
        assertEquals(Mietwerte.HOECHSTER_BETRAG,
                new Mietwerte(Mietwerte.HOECHSTER_BETRAG, Mietwerte.HOECHSTER_BETRAG,
                        0L, 1, 3).miete());
    }

    @Test
    @DisplayName("Die fuenfte Zahl ist die Ausfallschranke - Vorgabe 3")
    void ausfallschranke() {
        assertEquals(3, Mietwerte.VORGABE.ausfaelleBisRaeumung());
        assertEquals("laeden.ausfaelle-bis-raeumung", Mietwerte.PFAD_AUSFAELLE);

        Mietwerte andere = Mietwerte.VORGABE.mitAusfaellen(5);
        assertEquals(5, andere.ausfaelleBisRaeumung());
        // Der Wither fasst GENAU seinen Wert an - wie die vier anderen.
        assertEquals(500L, andere.miete());
        assertEquals(5_000L, andere.kaution());
        assertEquals(50_000L, andere.lizenz());
        assertEquals(7, andere.dauerTage());
    }

    @Test
    @DisplayName("Eine Schranke von 0 oder ueber einem Jahr wird abgewiesen")
    void schrankeHatGrenzen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, 50_000L, 7, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietwerte(500L, 5_000L, 50_000L, 7, 367));
        assertEquals(1, new Mietwerte(500L, 5_000L, 50_000L, 7, 1)
                .ausfaelleBisRaeumung());
    }

    @Test
    @DisplayName("Der Verlust bis zur Raeumung ist Miete mal Schranke")
    void verlustBisRaeumung() {
        // Die Zahl fuer die Rechenprobe: Wer "1.500 Mark bis zur Raeumung" neben einer
        // Kaution von 500 liest, sieht, dass das Weglaufen billiger ist als das Zahlen.
        assertEquals(1_500L, Mietwerte.VORGABE.verlustBisRaeumung());
        assertEquals(2_500L, Mietwerte.VORGABE.mitAusfaellen(5).verlustBisRaeumung());
    }
}
