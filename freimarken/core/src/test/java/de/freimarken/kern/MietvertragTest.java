package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Der Mietvertrag")
class MietvertragTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);
    private static final LocalDate ENDE = LocalDate.of(2026, 12, 6);
    private static final UUID ANNA = UUID.randomUUID();

    @Test
    @DisplayName("Haelt seine sechs Angaben")
    void haeltSeineAngaben() {
        Mietvertrag vertrag = new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 5_000L);

        assertEquals("stand-1", vertrag.einheit());
        assertEquals(ANNA, vertrag.mieter());
        assertEquals(HEUTE, vertrag.beginn());
        assertEquals(ENDE, vertrag.laeuftBis());
        assertEquals(500L, vertrag.miete());
        assertEquals(5_000L, vertrag.kaution());
    }

    @Test
    @DisplayName("Der Name der Einheit wird klein und ohne Rand gefuehrt")
    void nameWirdNormiert() {
        // Derselbe Griff wie in Ladeneinheit: Der Name kommt aus der config.yml, und dort
        // tippt ein Mensch. Ohne das waere "Stand-1" ein zweiter Vertrag ueber denselben
        // Laden.
        Mietvertrag vertrag = new Mietvertrag("  Stand-1  ", ANNA, HEUTE, ENDE, 500L, 5_000L);

        assertEquals("stand-1", vertrag.einheit());
    }

    @Test
    @DisplayName("Die vereinbarte Miete UEBERLEBT das Verlaengern")
    void verlaengernLaesstDieMieteStehen() {
        // Der Bestandsschutz aus Plan 1943, und das ist die tragende Zusage dieses Teils.
        // Eine Fassung, die beim Verlaengern die Miete aus den Mietwerten nimmt, waere
        // hier rot - und im Betrieb wuerde sie einem Spieler, der fuer 500 gemietet hat,
        // bei der ersten Verlaengerung den neuen Satz unterschieben.
        Mietvertrag vertrag = new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 5_000L);

        Mietvertrag spaeter = vertrag.bis(ENDE.plusDays(30));

        assertEquals(500L, spaeter.miete(), "die VEREINBARTE Miete, nicht die aus der"
                + " config.yml");
        assertEquals(5_000L, spaeter.kaution(), "und die HINTERLEGTE Kaution - sie ist"
                + " schon gezahlt, eine Verlaengerung fordert sie nicht neu");
        assertEquals(ENDE.plusDays(30), spaeter.laeuftBis());
        // Beginn und Mieter wandern mit: Eine Verlaengerung ist kein neuer Vertrag.
        assertEquals(HEUTE, spaeter.beginn());
        assertEquals(ANNA, spaeter.mieter());
        assertEquals("stand-1", spaeter.einheit());
    }

    @Test
    @DisplayName("bis() aendert NUR das Ende")
    void bisAendertNurDasEnde() {
        // Die Gegenprobe zur Zeile darueber: Der neue Vertrag muss sich vom alten
        // unterscheiden, und zwar genau in der einen Sache. Ohne diese Zeile waere ein
        // bis(), das sich selbst zurueckgibt, gruen.
        Mietvertrag vertrag = new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 5_000L);

        Mietvertrag spaeter = vertrag.bis(ENDE.plusDays(1));

        assertNotEquals(vertrag, spaeter);
        assertEquals(vertrag, spaeter.bis(ENDE), "und wieder zurueck ist derselbe");
    }

    @Test
    @DisplayName("gehoert() unterscheidet die Spieler")
    void gehoertUnterscheidet() {
        Mietvertrag vertrag = new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 5_000L);

        assertTrue(vertrag.gehoert(ANNA));
        assertFalse(vertrag.gehoert(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Ein Ende am Beginn ist erlaubt - ein Tag Mietdauer")
    void einTagIstErlaubt() {
        // Der Rand, und er ist gueltig: laeuftBis ist eingeschlossen, also ist "heute bis
        // heute" genau ein Miettag. Eine Pruefung mit isAfter statt isBefore waere hier
        // rot.
        Mietvertrag vertrag = new Mietvertrag("stand-1", ANNA, HEUTE, HEUTE, 500L, 5_000L);

        assertEquals(HEUTE, vertrag.laeuftBis());
        assertFalse(Mietregel.abgelaufen(vertrag.laeuftBis(), HEUTE));
    }

    @Test
    @DisplayName("Ein Ende VOR dem Beginn wird abgewiesen")
    void endeVorBeginnGehtNicht() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, HEUTE, HEUTE.minusDays(1), 500L, 5_000L));

        // Der TEXT und nicht nur der Typ: Dieser Vertrag waere am Tag seines Abschlusses
        // schon abgelaufen, und der taegliche Lauf wuerde einen gerade bezahlten Laden
        // raeumen. Wer die Zeile liest, soll das erfahren.
        assertTrue(wurf.getMessage().contains("am Tag des Abschlusses"),
                "die Meldung sagt, WARUM das schlimm ist: " + wurf.getMessage());
    }

    @Test
    @DisplayName("Ohne Einheit, Mieter oder Tage geht nichts")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag(null, ANNA, HEUTE, ENDE, 500L, 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("   ", ANNA, HEUTE, ENDE, 500L, 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", null, HEUTE, ENDE, 500L, 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, null, ENDE, 500L, 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, HEUTE, null, 500L, 5_000L));
    }

    @Test
    @DisplayName("Eine Kaution von 0 geht, eine negative nicht")
    void kautionNullGehtNegativNicht() {
        // Der Unterschied zur Miete, und er hat einen Grund: Eine Miete von 0 waere ein
        // Laden, der sich nie raeumen laesst. Eine Kaution von 0 heisst nur, dass der
        // Betreiber keine verlangt - das ist eine Entscheidung. Negativ waere sie eine
        // Rueckzahlung, und die ist keine Hinterlegung.
        Mietvertrag ohne = new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 0L);

        assertEquals(0L, ohne.kaution());
        assertFalse(ohne.mitKaution());
        assertTrue(new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, 1L).mitKaution());
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 500L, -1L));
    }

    @Test
    @DisplayName("Eine Miete von 0 wird abgewiesen")
    void mieteNullGehtNicht() {
        // Eine 0 waere ein Laden, den der taegliche Lauf nie beruehrt - also ein Vertrag,
        // der nie ablaeuft, weil nie eine Zahlung ausfaellt. Das ist keine
        // Gratismiete, sondern ein Laden, der sich nicht mehr raeumen laesst.
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, 0L, 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new Mietvertrag("stand-1", ANNA, HEUTE, ENDE, -1L, 5_000L));
    }
}
