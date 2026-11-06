package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Blockmarkenablage einhalten muss - im Arbeitsspeicher und im Chunk.
 *
 * <p>Gleiche Bauart wie {@link BerufeVertrag} und {@link SpielzeitVertrag}: Die Umsetzung
 * bringt ihre eigene Testklasse mit und erbt diese Pruefungen. Hier ist das besonders
 * wichtig, denn die zweite Umsetzung rechnet mit Bitmustern in einer Weltdatei - eine
 * Probe, die nur die Mengenfassung sieht, beweist davon nichts.
 *
 * <p><b>Die tragende Zusage ist die erste:</b> Eine Marke wird gesetzt und nie geloescht,
 * und {@link Blockmarken#markiere} sagt, ob sie NEU war. An dieser Antwort haengt in
 * Teil E die Auszahlung - sie ist der Unterschied zwischen "ein Block zahlt einmal" und
 * "ein Block zahlt bei jedem Setzen".
 *
 * <p><b>Und die unauffaelligste ist die Nachbarschaft:</b> Zwei Bloecke, die sich nur in
 * einer Achse um eins unterscheiden, sind zwei verschiedene Marken. Die Chunkfassung legt
 * sie als Bits in dasselbe Feld, und eine verrutschte Rechnung traegt die Marke eine Ebene
 * hoeher ein - im Spiel sieht man das nie, denn beide Antworten sind plausibel.
 */
public abstract class BlockmarkenVertrag {

    /** Eine Welt, die es in den Proben gibt. */
    protected static final String WELT = "freimarken:marktstadt";

    /** Eine frische, leere Ablage. */
    protected abstract Blockmarken marken();

    /**
     * Ein Ort in der Welt {@link #WELT}.
     *
     * <p>Die Zahlen sind mit Absicht keine Vielfachen von 16: So liegt der Ort mitten in
     * einem Chunk und mitten in einem Abschnitt, und ein Vorzeichenfehler in der Rechnung
     * faellt auf.
     */
    protected static Blockort ort(int x, int y, int z) {
        return new Blockort(WELT, x, y, z);
    }

    @Test
    @DisplayName("Eine unberuehrte Position ist nicht markiert")
    void unberuehrtIstNichtMarkiert() {
        assertFalse(marken().istMarkiert(ort(7, 71, 13)));
    }

    @Test
    @DisplayName("Die erste Marke ist NEU, die zweite nicht")
    void dieErsteMarkeIstNeu() {
        Blockmarken marken = marken();
        Blockort ort = ort(7, 71, 13);

        // Daran haengt die Auszahlung: Das erste Setzen zahlt, jedes weitere nicht.
        assertTrue(marken.markiere(ort), "die erste Marke ist neu");
        assertFalse(marken.markiere(ort), "die zweite ist es nicht");
        assertTrue(marken.istMarkiert(ort));
    }

    @Test
    @DisplayName("Eine Marke wird NIE geloescht - es gibt keinen Weg dazu")
    void eineMarkeBleibt() {
        Blockmarken marken = marken();
        Blockort ort = ort(7, 71, 13);
        marken.markiere(ort);

        // Der Vertrag hat gar keine Methode zum Loeschen, und das ist die Zusage selbst:
        // Loeschte das Abbauen die Marke, waere Setzen-Abbauen-Setzen die
        // Gelddruckmaschine des Baumeisters - mit einer einzigen Position.
        assertTrue(marken.istMarkiert(ort));
        assertFalse(marken.markiere(ort));
        assertTrue(marken.istMarkiert(ort), "und sie steht noch");
    }

    @Test
    @DisplayName("Nachbarn in JEDER Achse sind verschiedene Marken")
    void nachbarnSindVerschieden() {
        Blockmarken marken = marken();
        marken.markiere(ort(7, 71, 13));

        // Die unauffaelligste Falle der Chunkfassung: Sie legt die drei Achsen in EINE
        // Zahl, und eine verrutschte Verschiebung traegt die Marke eine Ebene hoeher ein.
        // Im Spiel ist das nicht zu sehen - beide Antworten sind plausibel.
        assertFalse(marken.istMarkiert(ort(8, 71, 13)), "ein Block weiter in x");
        assertFalse(marken.istMarkiert(ort(6, 71, 13)), "und zurueck");
        assertFalse(marken.istMarkiert(ort(7, 72, 13)), "eine Ebene hoeher");
        assertFalse(marken.istMarkiert(ort(7, 70, 13)), "und tiefer");
        assertFalse(marken.istMarkiert(ort(7, 71, 14)), "ein Block weiter in z");
        assertFalse(marken.istMarkiert(ort(7, 71, 12)), "und zurueck");
        assertTrue(marken.istMarkiert(ort(7, 71, 13)), "nur der eine ist markiert");
    }

    @Test
    @DisplayName("Dieselben Zahlen in einer anderen Welt sind eine andere Marke")
    void weltenTrennen() {
        Blockmarken marken = marken();
        marken.markiere(ort(7, 71, 13));

        assertFalse(marken.istMarkiert(new Blockort("freimarken:hochwald", 7, 71, 13)),
                "Hochwald und Marktstadt teilen die Koordinaten, nicht die Marken");
    }

    @Test
    @DisplayName("Negative Koordinaten liegen richtig - auch unter Null")
    void negativeKoordinaten() {
        Blockmarken marken = marken();
        // Alle drei Achsen negativ, und y unter Null: Die Welt beginnt bei -64, und eine
        // Division mit / statt floorDiv liesse die tiefste Blockschicht mit der ersten
        // darueber zusammenfallen. Das waere eine Marke, die zwei Bloecke entwertet.
        Blockort tief = ort(-5, -37, -19);

        assertTrue(marken.markiere(tief));
        assertTrue(marken.istMarkiert(tief));
        assertFalse(marken.istMarkiert(ort(-5, -36, -19)), "eine Ebene hoeher");
        assertFalse(marken.istMarkiert(ort(-5, -38, -19)), "und tiefer");
        assertFalse(marken.istMarkiert(ort(-4, -37, -19)));
        assertFalse(marken.istMarkiert(ort(-5, -37, -18)));
    }

    @Test
    @DisplayName("Die Ecken eines Abschnitts: 0 und 15 in jeder Achse")
    void eckenEinesAbschnitts() {
        Blockmarken marken = marken();
        // Acht Ecken EINES Abschnitts, alle verschieden. Eine Rechnung, die nur mit 16
        // multipliziert statt zu verschieben, oder eine, die den Rest falsch bildet,
        // laesst zwei Ecken zusammenfallen - und zwei Bloecke teilen eine Marke.
        List<Blockort> ecken = List.of(
                ort(0, 64, 0), ort(15, 64, 0), ort(0, 64, 15), ort(15, 64, 15),
                ort(0, 79, 0), ort(15, 79, 0), ort(0, 79, 15), ort(15, 79, 15));

        for (Blockort ecke : ecken) {
            assertTrue(marken.markiere(ecke), "jede Ecke ist neu: " + ecke);
        }
        for (Blockort ecke : ecken) {
            assertTrue(marken.istMarkiert(ecke), ecke.toString());
        }
        // Und der erste Block des naechsten Abschnitts ist frei.
        assertFalse(marken.istMarkiert(ort(0, 80, 0)));
        assertFalse(marken.istMarkiert(ort(16, 64, 0)));
        assertFalse(marken.istMarkiert(ort(0, 64, 16)));
    }

    @Test
    @DisplayName("Zwei Chunks weit auseinander stoeren sich nicht")
    void weiteOrteStoerenSichNicht() {
        Blockmarken marken = marken();
        Blockort weit = ort(1_000_013, 250, -999_987);

        assertTrue(marken.markiere(weit));
        assertTrue(marken.istMarkiert(weit));
        assertFalse(marken.istMarkiert(ort(7, 71, 13)));
    }

    @Test
    @DisplayName("Mehrere auf einmal - und gezaehlt werden nur die NEUEN")
    void mehrereAufEinmal() {
        Blockmarken marken = marken();
        Blockort unten = ort(7, 71, 13);
        Blockort oben = ort(7, 72, 13);
        marken.markiere(unten);

        // Eine Tuer sind zwei Bloecke, und sie darf einmal zahlen und nicht zweimal.
        // Steht die eine Haelfte schon, zahlt nur die andere.
        assertEquals(1, marken.markiereAlle(List.of(unten, oben)));
        assertTrue(marken.istMarkiert(oben));
        assertEquals(0, marken.markiereAlle(List.of(unten, oben)), "beide stehen jetzt");
        assertEquals(0, marken.markiereAlle(List.of()), "eine leere Liste ist erlaubt");
    }

    @Test
    @DisplayName("Ohne Ort und ohne Liste gibt es keine Marke")
    void pflichtenGelten() {
        Blockmarken marken = marken();

        assertThrows(IllegalArgumentException.class, () -> marken.istMarkiert(null));
        assertThrows(IllegalArgumentException.class, () -> marken.markiere(null));
        // null heisst "nicht gelesen", eine leere Liste heisst "nichts zu markieren".
        assertThrows(IllegalArgumentException.class, () -> marken.markiereAlle(null));
    }
}
