package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Rangwerte;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zeilen des Aufstiegs - die WAHL, nicht die Worte.
 *
 * <p>Welche Worte dastehen, prueft {@code BildGleichTest} zeichengleich gegen die
 * ausgelieferte Vorlage. Hier steht nur, WELCHER Satz zutrifft und wann einer wegfaellt -
 * und das ist der Teil, den das Programm entscheidet.
 */
@DisplayName("Die Zeilen der Rangleiter")
class RangtextTest {

    private static final Sprache SPRACHE = Sprachprobe.spracheAusDemJar();

    @Test
    @DisplayName("Ein Aufstieg mit einer Stufe darueber gibt ZWEI Zeilen")
    void mitAusblick() {
        List<Component> zeilen = Rangtext.aufstiegZeilen(SPRACHE, Rangwerte.VORGABE,
                "siedler");

        assertEquals(2, zeilen.size(), Sprachprobe.bild(zeilen));
        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.contains("siedler"), bild);
        // Die zweite Zeile nennt die Stufe DARUEBER und nicht die erreichte. Dieselbe
        // Aufgabe wie {morgen} beim Tagesbonus: der Grund, weiterzuspielen.
        assertTrue(bild.contains("buerger"), bild);
    }

    @Test
    @DisplayName("Auf der obersten Stufe steht der andere Satz")
    void obersteStufe() {
        List<Component> zeilen = Rangtext.aufstiegZeilen(SPRACHE, Rangwerte.VORGABE,
                "buerger");

        assertEquals(2, zeilen.size(), Sprachprobe.bild(zeilen));
        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.contains("hoechsten Stufe"), bild);
        // Ein eigener Satz und nicht das Weglassen: Wer oben ankommt, soll es LESEN und
        // es nicht aus einer fehlenden Zeile schliessen muessen.
        assertFalse(bild.contains("Als naechstes"), bild);
    }

    @Test
    @DisplayName("Die erste Zeile ist IMMER der Aufstieg selbst")
    void aufstiegImmerZuerst() {
        for (String rang : Rangwerte.VORGABE.schluessel()) {
            List<Component> zeilen = Rangtext.aufstiegZeilen(SPRACHE,
                    Rangwerte.VORGABE, rang);
            assertTrue(Sprachprobe.bild(zeilen.get(0)).contains("Du bist jetzt " + rang),
                    "die erste Zeile von " + rang + ": "
                            + Sprachprobe.bild(zeilen.get(0)));
        }
    }

    @Test
    @DisplayName("Ein LEER gelassener Ausblick faellt weg - er wird nicht leer gesendet")
    void leererAusblickFaelltWeg() {
        // Die leere Zeile IST der Schalter, und zwar an der Stelle, an der auch die Worte
        // stehen. Gesendet waere sie eine leere Chatzeile, und die liest sich wie ein
        // verschluckter Satz.
        Sprache ohneAusblick = Sprachprobe.mitZeilen(Map.of(
                RangSatz.NAECHSTER.pfad(), "",
                RangSatz.HOECHSTE.pfad(), ""));

        assertEquals(1, Rangtext.aufstiegZeilen(ohneAusblick, Rangwerte.VORGABE,
                "siedler").size(), "nur der Aufstieg selbst");
        assertEquals(1, Rangtext.aufstiegZeilen(ohneAusblick, Rangwerte.VORGABE,
                "buerger").size(), "auch oben");
    }

    @Test
    @DisplayName("Die Stufe darueber kommt aus der UEBERGEBENEN Leiter")
    void ausDerUebergebenenLeiter() {
        var eigene = Rangwerte.VORGABE.mitLeiter(List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of(), List.of()),
                new Rangwerte.Stufe("haendler", Map.of(Bedingungsart.GUTHABEN, 500L),
                        Map.of(), List.of())));

        String bild = Sprachprobe.bild(
                Rangtext.aufstiegZeilen(SPRACHE, eigene, "gast"));

        // Wer die Vorgabe fest einbaute, nennte auf einem Server mit eigenen Raengen eine
        // Stufe, die es dort nicht gibt - und der Spieler spielte auf ein Ziel zu, das
        // niemand erreichen kann.
        assertTrue(bild.contains("haendler"), bild);
        assertFalse(bild.contains("siedler"), bild);
    }
}
