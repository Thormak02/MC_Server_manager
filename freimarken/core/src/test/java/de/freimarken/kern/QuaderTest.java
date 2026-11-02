package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Quader - und die zwei Rechenfehler, die bei einem Tor weh tun. */
class QuaderTest {

    @Test
    @DisplayName("Beide Ecken gehoeren dazu")
    void eckenGehoerenDazu() {
        Quader tor = new Quader(80, 72, 2, 82, 74, 4);

        assertTrue(tor.enthaelt(80, 72, 2), "die eine Ecke");
        assertTrue(tor.enthaelt(82, 74, 4), "die andere Ecke");
        assertTrue(tor.enthaelt(81, 73, 3), "die Mitte");
        assertFalse(tor.enthaelt(79, 73, 3), "einen Block davor");
        assertFalse(tor.enthaelt(83, 73, 3), "einen Block dahinter");
        assertFalse(tor.enthaelt(81, 75, 3), "einen Block darueber");
        assertEquals(27, tor.bloecke());
    }

    @Test
    @DisplayName("Vertauschte Ecken werden sortiert, nicht abgelehnt")
    void vertauschteEckenWerdenSortiert() {
        // Ein Betreiber, der von und bis vertauscht, hat sich vertippt und keinen leeren
        // Quader gemeint. Ein leerer Quader waere ein Tor, das nie ausloest - und das
        // merkt niemand.
        Quader verdreht = new Quader(82, 74, 4, 80, 72, 2);

        assertEquals(new Quader(80, 72, 2, 82, 74, 4), verdreht);
        assertTrue(verdreht.enthaelt(81, 73, 3));
    }

    @Test
    @DisplayName("Ein einzelner Block ist ein Quader")
    void einBlock() {
        Quader punkt = new Quader(5, 64, 5, 5, 64, 5);

        assertEquals(1, punkt.bloecke());
        assertTrue(punkt.enthaelt(5, 64, 5));
        assertFalse(punkt.enthaelt(5, 65, 5));
    }

    @Test
    @DisplayName("Zwei Tore nebeneinander sind erlaubt, zwei uebereinander nicht")
    void schnittIstEinGemeinsamerBlock() {
        // Beruehren genuegt nicht: Zwei Tore Wand an Wand sind ein Torhaus. Gemeint ist
        // ein Block, der zu BEIDEN gehoert - dann entscheidet die Reihenfolge der
        // Eintraege, wohin ein Spieler reist, und das ist kein Entschluss, sondern Zufall.
        Quader links = new Quader(80, 72, 2, 82, 74, 4);
        Quader daneben = new Quader(83, 72, 2, 85, 74, 4);
        Quader ueberlappt = new Quader(82, 72, 2, 84, 74, 4);

        assertFalse(links.schneidet(daneben), "Wand an Wand ist kein Schnitt");
        assertFalse(daneben.schneidet(links), "und das gilt in beide Richtungen");
        assertTrue(links.schneidet(ueberlappt), "ein gemeinsamer Block genuegt");
        assertTrue(ueberlappt.schneidet(links));
        assertTrue(links.schneidet(links), "mit sich selbst immer");
    }

    @Test
    @DisplayName("Ein Schnitt nur in der Hoehe ist keiner")
    void hoeheZaehltMit() {
        // Zwei Tore an derselben Stelle, eines ueber dem anderen: erlaubt. Ein Tor im
        // Keller und eines im Obergeschoss sind zwei Tore.
        Quader unten = new Quader(80, 64, 2, 82, 66, 4);
        Quader oben = new Quader(80, 72, 2, 82, 74, 4);

        assertFalse(unten.schneidet(oben));
    }

    @Test
    @DisplayName("Ein Tor hinter der Weltgrenze faellt auf, bevor jemand davorsteht")
    void torMussInnerhalbDerGrenzeLiegen() {
        // Altfurt: Kante 800 um 0/0, also Bloecke -400..399.
        Chunkfeld altfurt = Chunkfeld.ausGrenze(0, 0, 800);

        assertTrue(new Quader(80, 72, 2, 82, 74, 4).liegtIn(altfurt));
        assertTrue(new Quader(-400, 0, -400, 399, 0, 399).liegtIn(altfurt), "genau am Rand");
        assertFalse(new Quader(398, 72, 0, 401, 74, 0).liegtIn(altfurt), "einen Block zu weit");
        assertFalse(new Quader(-401, 72, 0, -399, 74, 0).liegtIn(altfurt), "nach Westen zu weit");
    }

    @Test
    @DisplayName("Die Mitte wird abgerundet und nennt einen Block, den es gibt")
    void mitte() {
        assertEquals(81, new Quader(80, 72, 2, 82, 74, 4).mitteX());
        assertEquals(73, new Quader(80, 72, 2, 82, 74, 4).mitteY());
        // Gerade Kantenlaenge: die Mitte liegt zwischen zwei Bloecken, abgerundet wird zum
        // kleineren. Wichtig nur, dass es ein Block IM Quader ist.
        Quader gerade = new Quader(80, 72, 2, 81, 72, 3);
        assertTrue(gerade.enthaelt(gerade.mitteX(), gerade.mitteY(), gerade.mitteZ()));
        Quader negativ = new Quader(-5, 72, -5, -4, 72, -4);
        assertTrue(negativ.enthaelt(negativ.mitteX(), negativ.mitteY(), negativ.mitteZ()),
                "auch im Negativen - floorDiv und nicht die ganzzahlige Division");
    }

    @Test
    @DisplayName("Der Text nennt beide Ecken")
    void text() {
        assertEquals("80/72/2 bis 82/74/4", new Quader(80, 72, 2, 82, 74, 4).alsText());
    }
}
