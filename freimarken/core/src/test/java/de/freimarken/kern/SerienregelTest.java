package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die eine Stelle, die entscheidet, wie lang eine Serie ist.
 *
 * <p>Die Serie ist der Teil des Tagesbonus, der REGELMAESSIGES Spielen belohnt. Geprueft
 * wird darum vor allem der Rand: der erste Tag, die gerade noch haltende Luecke, die
 * erste reissende - und der Tag, der in der Vergangenheit liegt, weil die Tagesgrenze
 * eine eingestellte Zone ist.
 */
@DisplayName("Die Serienregel")
class SerienregelTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    /** Die strenge Vorgabe: Nur wer gestern da war, setzt fort. */
    private static final int STRENG = 2;

    @Test
    @DisplayName("Ohne letzten Tag beginnt die Serie bei 1")
    void ersterBonusUeberhaupt() {
        Serienregel.Urteil urteil = Serienregel.urteile(null, HEUTE, 0, STRENG);

        assertEquals(Serienregel.Ausgang.NEU_BEGONNEN, urteil.ausgang());
        assertEquals(1, urteil.serie());
        assertTrue(urteil.zahlbar());
    }

    @Test
    @DisplayName("Ohne letzten Tag wird eine stehengebliebene Serie NICHT gelesen")
    void ohneTagKeinVorlauf() {
        // Eine Zahl ohne ihren Tag gehoert zu nichts. Waere sie gelesen, bekaeme ein
        // Spieler mit einer kaputten Zeile einen geschenkten Vorlauf - und zwar genau
        // den Betrag, den die Serie an diesem Tag traegt.
        Serienregel.Urteil urteil = Serienregel.urteile(null, HEUTE, 99, STRENG);

        assertEquals(1, urteil.serie());
    }

    @Test
    @DisplayName("Wer GESTERN da war, setzt fort")
    void gesternSetztFort() {
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE.minusDays(1), HEUTE, 4,
                STRENG);

        assertEquals(Serienregel.Ausgang.FORTGESETZT, urteil.ausgang());
        assertEquals(5, urteil.serie());
    }

    @Test
    @DisplayName("Wer einen Tag verpasst hat, faengt neu an")
    void einTagLueckeReisst() {
        // Der Rand der strengen Vorgabe: Luecke 2 heisst ein verpasster Tag.
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE.minusDays(2), HEUTE, 20,
                STRENG);

        assertEquals(Serienregel.Ausgang.NEU_BEGONNEN, urteil.ausgang());
        assertEquals(1, urteil.serie(),
                "zwanzig Tage sind weg - das ist der Preis der strengen Lesart");
    }

    @Test
    @DisplayName("Mit Luecke 3 wird ein verpasster Tag verziehen")
    void eineLueckeWirdVerziehen() {
        // Dieselbe Lage, andere Einstellung: Vorgestern da, heute wieder - und die Serie
        // haelt. Ohne diese Probe stuende die freundliche Lesart nur im Kommentar.
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE.minusDays(2), HEUTE, 20, 3);

        assertEquals(Serienregel.Ausgang.FORTGESETZT, urteil.ausgang());
        assertEquals(21, urteil.serie());
        // Zwei verpasste Tage reissen dann aber doch.
        assertEquals(1, Serienregel.urteile(HEUTE.minusDays(3), HEUTE, 20, 3).serie());
    }

    @Test
    @DisplayName("Heute ist schon bezahlt - die Serie bleibt stehen")
    void heuteSchonBezahlt() {
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE, HEUTE, 7, STRENG);

        assertEquals(Serienregel.Ausgang.SCHON_BEZAHLT, urteil.ausgang());
        assertFalse(urteil.zahlbar());
        assertEquals(7, urteil.serie(),
                "die alte Zahl, damit eine Anzeige sie zeigen kann, ohne den Ausgang zu"
                        + " kennen");
    }

    @Test
    @DisplayName("Ein Tag in der ZUKUNFT haelt die Serie an, statt sie zu verlaengern")
    void tagInDerZukunft() {
        // Nicht konstruiert: Die Tagesgrenze ist eine eingestellte Zone. Wer sie von
        // Europe/Berlin auf Pacific/Auckland stellt, verschiebt den Tageswechsel um einen
        // halben Tag - und der naechste Spieler erlebt "gestern". Gezahlt wird dann
        // NICHTS, und verloren ist ein Tag.
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE.plusDays(1), HEUTE, 7,
                STRENG);

        assertEquals(Serienregel.Ausgang.SCHON_BEZAHLT, urteil.ausgang());
        assertFalse(urteil.zahlbar());
    }

    @Test
    @DisplayName("Eine lange Luecke reisst auch bei grosszuegiger Einstellung")
    void langeLueckeReisstImmer() {
        assertEquals(1, Serienregel.urteile(HEUTE.minusDays(365), HEUTE, 100, 7).serie());
    }

    @Test
    @DisplayName("Eine Serie von 0 mit vorhandenem Tag faengt bei 1 an und nicht bei 0")
    void kaputteSerieWirdGeheilt() {
        // Nur aus einer kaputten Zeile erreichbar. Heute ist dann der ERSTE Tag und nicht
        // der nullte - ein Bonus fuer Serientag 0 waere ein Betrag, den Bonusregel
        // verweigert.
        Serienregel.Urteil urteil = Serienregel.urteile(HEUTE.minusDays(1), HEUTE, 0,
                STRENG);

        assertEquals(1, urteil.serie());
    }

    @Test
    @DisplayName("Ohne heute gibt es kein Urteil")
    void ohneHeute() {
        assertThrows(IllegalArgumentException.class,
                () -> Serienregel.urteile(HEUTE, null, 1, STRENG));
    }

    @Test
    @DisplayName("Eine Luecke von 1 wird verweigert - sie liesse die Serie fuer JEDEN reissen")
    void lueckeEinsVerweigert() {
        // Die eine Schranke, die wirklich etwas faengt: Bei 1 ist luecke >= 1 fuer jeden
        // wahr, der nicht heute schon bezahlt wurde - auch fuer den, der gestern da war.
        // Die Serie stuende immer auf 1, und man suchte den Fehler in der Belohnung.
        assertThrows(IllegalArgumentException.class,
                () -> Serienregel.urteile(HEUTE.minusDays(1), HEUTE, 5, 1));
        assertFalse(Serienregel.brauchbareLuecke(1));
        assertFalse(Serienregel.brauchbareLuecke(0));
        assertFalse(Serienregel.brauchbareLuecke(-1));
    }

    @Test
    @DisplayName("Die Luecke reicht von 2 bis zu einem Jahr")
    void lueckeImBereich() {
        assertTrue(Serienregel.brauchbareLuecke(2));
        assertTrue(Serienregel.brauchbareLuecke(Serienregel.HOECHSTE_LUECKE));
        // Darueber waere die Serie keine mehr: Wer einmal im Jahr kommt, haette eine
        // ununterbrochene - und die Zahl daneben verspricht Regelmaessigkeit.
        assertFalse(Serienregel.brauchbareLuecke(Serienregel.HOECHSTE_LUECKE + 1));
    }

    @Test
    @DisplayName("Ein Urteil ohne Ausgang und mit negativer Serie kommt nicht zustande")
    void urteilBrauchtBeides() {
        assertThrows(IllegalArgumentException.class,
                () -> new Serienregel.Urteil(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Serienregel.Urteil(Serienregel.Ausgang.FORTGESETZT, -1));
    }

    @Test
    @DisplayName("Dreissig Tage in Folge ergeben eine Serie von dreissig")
    void dreissigTageInFolge() {
        // Der Weg, den ein Spieler wirklich geht - und die Probe, die faengt, wenn die
        // Luecke um eins daneben gerechnet wird: Bei einem Fehler in der Richtung
        // stuende hier 15 oder 1.
        int serie = 0;
        LocalDate letzter = null;
        for (int tag = 0; tag < 30; tag++) {
            LocalDate dieser = HEUTE.minusDays(29L - tag);
            Serienregel.Urteil urteil = Serienregel.urteile(letzter, dieser, serie,
                    STRENG);
            assertTrue(urteil.zahlbar(), "Tag " + tag + " muss zahlbar sein");
            serie = urteil.serie();
            letzter = dieser;
        }

        assertEquals(30, serie);
    }
}
