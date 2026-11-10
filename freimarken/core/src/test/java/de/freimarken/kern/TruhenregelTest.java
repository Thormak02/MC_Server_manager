package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Freie Truhe - was ein Spieler behalten darf und was zurueckgeht.
 *
 * <p>Die Rechnung ist klein, und ihre Entscheidungen sind es nicht: Angerechnet wird, was
 * er BEHAELT, und das Hineinlegen zaehlt gar nicht. An beiden haengt, ob der Deckel haelt.
 */
@DisplayName("Die Freie Truhe")
class TruhenregelTest {

    @Test
    @DisplayName("Unter dem Deckel darf er alles behalten")
    void unterDemDeckel() {
        var abrechnung = Truhenregel.abrechnen(16, 0, 7);

        assertEquals(7, abrechnung.erlaubt());
        assertEquals(0, abrechnung.zurueck());
        assertFalse(abrechnung.legtZurueck(), "dann wird die Truhe nicht angefasst");
    }

    @Test
    @DisplayName("Ueber dem Deckel geht der Ueberschuss zurueck")
    void ueberDemDeckel() {
        // Er nimmt dreissig, darf aber nur sechzehn: vierzehn gehen zurueck in die
        // Truhe. Die Plaetze sind frei - er hat sie gerade geleert.
        var abrechnung = Truhenregel.abrechnen(16, 0, 30);

        assertEquals(16, abrechnung.erlaubt());
        assertEquals(14, abrechnung.zurueck());
        assertTrue(abrechnung.legtZurueck());
    }

    @Test
    @DisplayName("Der Deckel zaehlt den GANZEN Tag und nicht den Griff")
    void deckelZaehltDenTag() {
        // Drei Griffe zu je sieben Stueck: Der dritte bekommt nur noch zwei. Ohne den
        // Tagesstand waere der Deckel ein Deckel je Griff, und wer zehnmal hingeht,
        // traegt zehnmal sechzehn davon.
        assertEquals(7, Truhenregel.abrechnen(16, 0, 7).erlaubt());
        assertEquals(7, Truhenregel.abrechnen(16, 7, 7).erlaubt());
        assertEquals(2, Truhenregel.abrechnen(16, 14, 7).erlaubt());
        assertEquals(5, Truhenregel.abrechnen(16, 14, 7).zurueck());
        assertEquals(0, Truhenregel.abrechnen(16, 16, 7).erlaubt());
        assertEquals(7, Truhenregel.abrechnen(16, 16, 7).zurueck());
    }

    @Test
    @DisplayName("Wer ueber dem Deckel steht, bekommt nichts - und nicht negativ")
    void ueberDemDeckelStehen() {
        // Erreichbar, wenn der Betreiber den Deckel SENKT: Wer heute schon zwanzig hat
        // und der Deckel steht auf sechzehn, darf nichts mehr. Ohne das Math.max waere
        // "frei" negativ, und erlaubt waere es auch - ein negativer Aufschlag auf dem
        // Weg einer Entnahme.
        var abrechnung = Truhenregel.abrechnen(16, 20, 5);

        assertEquals(0, abrechnung.erlaubt());
        assertEquals(5, abrechnung.zurueck());
    }

    @Test
    @DisplayName("HINEINLEGEN zaehlt nicht - gedeckelt ist die Entnahme")
    void hineinlegenZaehltNicht() {
        // Die Differenz ist dann 0 oder negativ. Der halbe Zweck dieser Truhe ist das
        // Verschenken, und ein Deckel darauf machte aus der Gemeinschaftstruhe eine
        // Abgabestelle mit Quote.
        for (int gelegt : new int[] {0, -1, -64}) {
            var abrechnung = Truhenregel.abrechnen(16, 0, gelegt);
            assertEquals(0, abrechnung.erlaubt(), "gelegt: " + gelegt);
            assertEquals(0, abrechnung.zurueck(), "und nichts geht zurueck");
        }
    }

    @Test
    @DisplayName("OHNE Deckel darf er alles behalten")
    void ohneDeckel() {
        var abrechnung = Truhenregel.abrechnen(Truhenwerte.KEIN_DECKEL, 500, 3_456);

        assertEquals(3_456, abrechnung.erlaubt());
        assertEquals(0, abrechnung.zurueck());
    }

    @Test
    @DisplayName("Ob er noch nehmen darf, ist EINE Frage und kein Vergleich")
    void darfNochNehmen() {
        // Die Frage VOR dem Oeffnen: Wer am Deckel ist, soll die Truhe gar nicht
        // aufbekommen - sonst nimmt er, sieht es im Rucksack liegen, schliesst, und
        // bekommt alles wieder weggenommen. Das sieht aus wie ein Fehler des Servers.
        assertTrue(Truhenregel.darfNochNehmen(16, 0));
        assertTrue(Truhenregel.darfNochNehmen(16, 15));
        assertFalse(Truhenregel.darfNochNehmen(16, 16));
        assertFalse(Truhenregel.darfNochNehmen(16, 99), "nach einer Deckelsenkung");
        assertTrue(Truhenregel.darfNochNehmen(Truhenwerte.KEIN_DECKEL, 10_000),
                "ohne Deckel immer");
    }

    @Test
    @DisplayName("Was heute noch frei ist, nennt die Zahl - oder minus eins")
    void heuteNochFrei() {
        assertEquals(16, Truhenregel.heuteNochFrei(16, 0));
        assertEquals(9, Truhenregel.heuteNochFrei(16, 7));
        assertEquals(0, Truhenregel.heuteNochFrei(16, 16));
        assertEquals(0, Truhenregel.heuteNochFrei(16, 20), "und nie negativ");
        // MINUS EINS fuer "unbegrenzt": "Unbegrenzt" ist keine Zahl, und eine riesige
        // waere eine, die niemand liest. Der Satzbau fragt darum den Deckel selbst und
        // waehlt zwei verschiedene Saetze - dieselbe Form wie beim Bundesmarkt.
        assertEquals(-1, Truhenregel.heuteNochFrei(Truhenwerte.KEIN_DECKEL, 7));
    }

    @Test
    @DisplayName("Ein negativer Deckel und ein negativer Tagesstand sind keine")
    void unmoeglicheZahlenWerfen() {
        assertThrows(IllegalArgumentException.class,
                () -> Truhenregel.abrechnen(-1, 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> Truhenregel.abrechnen(16, -1, 5));
    }
}
