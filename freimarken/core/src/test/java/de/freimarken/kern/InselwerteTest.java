package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was eine Meerinsel kostet und wie viele einer halten darf.
 *
 * <p>Zwei Zahlen, und beide Grenzen tragen eine Entscheidung: Die 0 ist bei BEIDEN
 * erlaubt und bedeutet etwas Verschiedenes - beim Preis "umsonst", bei der Zahl "gar
 * keine". Eine Probe, die nur die Mitte prueft, sieht genau das nicht.
 */
@DisplayName("Die Zahlen der Meerinseln")
class InselwerteTest {

    @Test
    @DisplayName("Die Vorgabe ist 75.000 Mark und eine Insel je Spieler")
    void vorgabe() {
        assertEquals(75_000L, Inselwerte.VORGABE.preis(), "Plan 6.2");
        assertEquals(1, Inselwerte.VORGABE.hoechstzahl());
    }

    @Test
    @DisplayName("Die Obergrenze der Zahl ist, was die Welt hergibt")
    void hoechsteZahl() {
        // 323 ist Inselgrenze.hafenwerder().inselnZahl() - 18 Felder je Achse, die
        // Ankunftsinsel abgezogen. Eine Obergrenze darueber waere eine, die nie greift.
        assertEquals(Inselgrenze.hafenwerder().inselnZahl(), Inselwerte.HOECHSTE_ZAHL);
        assertEquals(323, Inselwerte.HOECHSTE_ZAHL);
    }

    @Test
    @DisplayName("Eine 0 ist bei beiden erlaubt - und heisst zweimal etwas anderes")
    void dieNullIstErlaubt() {
        assertEquals(0L, Inselwerte.VORGABE.mitPreis(0L).preis(),
                "umsonst - wer das Recht hat, nimmt eine Insel ohne Geld");
        assertEquals(0, Inselwerte.VORGABE.mitHoechstzahl(0).hoechstzahl(),
                "gar keine - der Weg, die Inseln abzuschalten, ohne das Recht"
                        + " anzufassen");
    }

    @Test
    @DisplayName("Was nicht in den Bereich passt, wird abgewiesen")
    void riegel() {
        assertThrows(IllegalArgumentException.class, () -> new Inselwerte(-1L, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselwerte(Inselwerte.HOECHSTER_BETRAG + 1L, 1));
        assertThrows(IllegalArgumentException.class, () -> new Inselwerte(75_000L, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselwerte(75_000L, Inselwerte.HOECHSTE_ZAHL + 1));
    }

    @Test
    @DisplayName("Die Wandler lassen die andere Zahl stehen")
    void wandler() {
        // Der Fehler, den diese Probe faengt: Ein Wandler, der beim Umbauen das zweite
        // Feld auf seine Vorgabe zurueckfallen laesst. Im Spiel setzte der Betreiber
        // dann die Hoechstzahl und bekaeme den Preis zurueckgesetzt.
        Inselwerte eigene = new Inselwerte(100_000L, 5);

        assertEquals(5, eigene.mitPreis(1L).hoechstzahl());
        assertEquals(100_000L, eigene.mitHoechstzahl(9).preis());
    }

    @Test
    @DisplayName("Die Inselwerte liegen im EINEN Wirtschaftssatz")
    void imWirtschaftssatz() {
        // Der Grund, aus dem sie dort liegen: Ein Neuladen tauscht den ganzen Satz als
        // EIN Stueck. Mit einem Feld daneben koennte eine Abbuchung den alten Preis
        // nehmen und die Meldung danach den neuen nennen.
        assertEquals(Inselwerte.VORGABE, Wirtschaftswerte.VORGABE.inseln());

        Wirtschaftswerte anders = Wirtschaftswerte.VORGABE.mitInseln(
                Inselwerte.VORGABE.mitPreis(1L));

        assertEquals(1L, anders.inseln().preis());
        assertEquals(Wirtschaftswerte.VORGABE.freistaedte(), anders.freistaedte(),
                "und der Nachbarblock bleibt stehen - ein Wandler, der ihn vergisst,"
                        + " setzte beim Preiswechsel die ganze Stadtordnung zurueck");
    }
}
