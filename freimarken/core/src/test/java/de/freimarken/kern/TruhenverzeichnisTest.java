package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wo die Freien Truhen stehen - und wo keine steht.
 *
 * <p>Die tragende Zusage ist die VERNEINUNG: Jede Stelle, die nicht eingetragen ist, ist
 * keine Freie Truhe. Waere sie es, duerfte jeder Spieler aus jeder Kiste der Welt nehmen -
 * und zwar still, denn das Nehmen aus einer Kiste sieht im Spiel wie das Nehmen aus einer
 * Kiste aus.
 */
@DisplayName("Die Stellen der Freien Truhen")
class TruhenverzeichnisTest {

    private static final Blockort HALLE = new Blockort("freimarken:altfurt", 62, 72, 8);

    @Test
    @DisplayName("Ohne Eintrag gibt es KEINE Freie Truhe")
    void ohneEintragKeineTruhe() {
        // Die sichere Richtung, und darum gibt es keine geratene Vorgabe: Eine geratene
        // Koordinate zeigte in unerschlossenes Gelaende, und dort stuende dann eine
        // Kiste, aus der jeder nehmen darf.
        Truhenverzeichnis leer = Truhenverzeichnis.ohneTruhen();

        assertFalse(leer.gibtTruhen());
        assertEquals(0, leer.anzahl());
        assertEquals(List.of(), leer.alle());
        assertFalse(leer.istFreieTruhe(HALLE));
    }

    @Test
    @DisplayName("Eine Truhe wird an ihrer STELLE erkannt - der Block daneben nicht")
    void truheAnIhrerStelle() {
        Truhenverzeichnis eine = Truhenverzeichnis.ausStellen(List.of(HALLE));

        assertTrue(eine.gibtTruhen());
        assertEquals(1, eine.anzahl());
        assertTrue(eine.istFreieTruhe(HALLE));
        assertFalse(eine.istFreieTruhe(new Blockort(HALLE.welt(), HALLE.x(),
                HALLE.y() + 1, HALLE.z())), "eine Ebene hoeher ist eine andere Kiste");
        assertFalse(eine.istFreieTruhe(new Blockort(HALLE.welt(), HALLE.x() + 1,
                HALLE.y(), HALLE.z())), "und der Block daneben auch");
        assertFalse(eine.istFreieTruhe(new Blockort("freimarken:marktstadt", HALLE.x(),
                HALLE.y(), HALLE.z())), "und dieselbe Stelle in einer anderen Welt");
        assertFalse(eine.istFreieTruhe(null), "und nichts ist keine Truhe");
    }

    @Test
    @DisplayName("Mehrere Truhen stehen gleichzeitig - jede fuer sich")
    void mehrereTruhen() {
        // Der Plan nennt eine; wer zwei aufstellt, bekommt zwei - und trotzdem nur EINEN
        // Deckel, denn der zaehlt je Spieler und steht bei den Truhenwerte.
        Blockort zweite = new Blockort(HALLE.welt(), 70, 72, 8);
        Truhenverzeichnis beide = Truhenverzeichnis.ausStellen(List.of(HALLE, zweite));

        assertEquals(2, beide.anzahl());
        assertTrue(beide.istFreieTruhe(HALLE));
        assertTrue(beide.istFreieTruhe(zweite));
    }

    @Test
    @DisplayName("Dieselbe Stelle zweimal ist EINE Truhe")
    void doppelteStelleIstEine() {
        // Zweimal dieselbe Zeile ist ein Tippfehler, aber keiner, der etwas kaputt macht:
        // Gefragt wird nur, OB an einer Stelle eine Truhe steht. Der Leser beklagt die
        // doppelte Zeile, das Verzeichnis legt sie zusammen - sonst stuende in der
        // Startmeldung "2 Freie Truhen", und der Betreiber suchte die zweite.
        Truhenverzeichnis eine = Truhenverzeichnis.ausStellen(List.of(HALLE, HALLE));

        assertEquals(1, eine.anzahl());
        assertEquals(List.of(HALLE), eine.alle());
    }

    @Test
    @DisplayName("Die Reihenfolge der Datei bleibt stehen")
    void reihenfolgeBleibt() {
        // Fuer die Startmeldung und fuer /freimarken pruefen: Wer seine Zeilen in der
        // Antwort in einer anderen Ordnung wiederfindet als in seiner Datei, vergleicht
        // sie nicht.
        Blockort zweite = new Blockort(HALLE.welt(), 70, 72, 8);
        Blockort dritte = new Blockort(HALLE.welt(), 5, 72, 8);

        assertEquals(List.of(dritte, HALLE, zweite),
                Truhenverzeichnis.ausStellen(List.of(dritte, HALLE, zweite)).alle());
    }

    @Test
    @DisplayName("Ohne Liste und mit einer Luecke darin gibt es kein Verzeichnis")
    void ohneListeKeinVerzeichnis() {
        // "Keine Truhe" und "keine Angabe" sind zwei verschiedene Dinge: Das eine ist
        // eine Entscheidung des Betreibers (ohneTruhen), das andere ein Fehler des
        // Lesers - und ein null in der Liste waere eine Stelle, die jede Frage nach
        // "steht hier eine Truhe" mit einer Ausnahme beantwortet.
        assertThrows(IllegalArgumentException.class,
                () -> Truhenverzeichnis.ausStellen(null));
        assertThrows(IllegalArgumentException.class,
                () -> Truhenverzeichnis.ausStellen(Arrays.asList(HALLE, null)));
    }

    @Test
    @DisplayName("Die Liste ist ABGESCHRIEBEN - eine Aenderung von aussen wirkt nicht")
    void listeIstAbgeschrieben() {
        // Der Leser baut seine Liste waehrend des Lesens auf und gibt sie herein. Behielte
        // das Verzeichnis genau diese Liste, aenderte ein Leser, der danach weiterliest,
        // ein Verzeichnis, das schon ausgegeben ist.
        List<Blockort> meine = new ArrayList<>(List.of(HALLE));
        Truhenverzeichnis eine = Truhenverzeichnis.ausStellen(meine);

        meine.clear();

        assertTrue(eine.istFreieTruhe(HALLE), "die Truhe bleibt, wo sie war");
        assertThrows(UnsupportedOperationException.class, () -> eine.alle().add(HALLE));
    }

    @Test
    @DisplayName("Gleich ist, was dieselben Stellen traegt")
    void gleichheit() {
        // Gebraucht vom Neuladebericht: Ob sich etwas geaendert hat, ist ein Vergleich -
        // und ohne equals waere die Antwort immer "geaendert", auch bei einer Datei, die
        // niemand angefasst hat.
        assertEquals(Truhenverzeichnis.ausStellen(List.of(HALLE)),
                Truhenverzeichnis.ausStellen(List.of(HALLE)));
        assertEquals(Truhenverzeichnis.ausStellen(List.of(HALLE)).hashCode(),
                Truhenverzeichnis.ausStellen(List.of(HALLE)).hashCode());
        assertEquals(Truhenverzeichnis.ohneTruhen(),
                Truhenverzeichnis.ausStellen(List.of()));
        assertNotEquals(Truhenverzeichnis.ausStellen(List.of(HALLE)),
                Truhenverzeichnis.ohneTruhen());
    }
}
