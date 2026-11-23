package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die EINE Stelle, die entscheidet, ob ein angefangenes Wort passt.
 *
 * <p>Zusammengelegt aus drei zeichengleichen Fassungen (Warps, Berufe, Bauten). Diese
 * Proben halten genau das Verhalten fest, das jene drei hatten - sonst waere die
 * Zusammenlegung eine stille Verhaltensaenderung in drei Befehlen.
 */
@DisplayName("Die Vorschlaege zu einem angefangenen Wort")
class NamensvorschlagTest {

    private static final List<String> NAMEN =
            List.of("buergeramt", "bundesratshaus", "zunfthaus");

    @Test
    @DisplayName("Ein leerer Anfang schlaegt ALLES vor - in der Reihenfolge der Datei")
    void leererAnfangAlles() {
        // Wer die Tabulatortaste drueckt, ohne etwas getippt zu haben, will sehen, was es
        // gibt. Und die Reihenfolge ist die der Datei und nicht alphabetisch: Der
        // Betreiber hat sie so geschrieben.
        assertEquals(NAMEN, Namensvorschlag.zu(NAMEN, ""));
        assertEquals(NAMEN, Namensvorschlag.zu(NAMEN, null));
        assertEquals(NAMEN, Namensvorschlag.zu(NAMEN, "   "));
    }

    @Test
    @DisplayName("Ein Anfang schneidet - und zwar nur am ANFANG")
    void anfangSchneidet() {
        assertEquals(List.of("buergeramt", "bundesratshaus"),
                Namensvorschlag.zu(NAMEN, "bu"));
        assertEquals(List.of("zunfthaus"), Namensvorschlag.zu(NAMEN, "zunf"));
        assertEquals(List.of("bundesratshaus"), Namensvorschlag.zu(NAMEN, "bund"));
        // NICHT in der Mitte: "haus" steht in zwei Namen, beginnt aber keinen. Wer das
        // aendert, aendert es fuer Warps, Berufe und Bauten gleichzeitig.
        assertEquals(List.of(), Namensvorschlag.zu(NAMEN, "haus"));
        assertEquals(List.of(), Namensvorschlag.zu(NAMEN, "x"));
    }

    @Test
    @DisplayName("Grossbuchstaben und Leerzeichen hindern nicht")
    void grossUndLeerzeichen() {
        assertEquals(List.of("zunfthaus"), Namensvorschlag.zu(NAMEN, "ZUNF"));
        assertEquals(List.of("zunfthaus"), Namensvorschlag.zu(NAMEN, " Zunf "));
        // Auch ein grossgeschriebener NAME wird gefunden - die drei alten Fassungen
        // verglichen nur die Eingabe klein und haetten ihn verloren.
        assertEquals(List.of("Altfurt"),
                Namensvorschlag.zu(List.of("Altfurt"), "altf"));
    }

    @Test
    @DisplayName("Die Antwort ist unveraenderlich und haengt nicht an der Liste")
    void antwortIstFest() {
        List<String> raus = Namensvorschlag.zu(NAMEN, "bu");

        assertThrows(UnsupportedOperationException.class, () -> raus.add("mehr"));
        // Eine Liste mit einem Loch darin ist kein Wurf: Sie kann aus einer Datei
        // kommen, und ein fehlender Name ist kein Grund, die Vervollstaendigung zu
        // sprengen.
        assertEquals(List.of("buergeramt"),
                Namensvorschlag.zu(Arrays.asList("buergeramt", null), "bu"));
        assertEquals(List.of(), Namensvorschlag.zu(null, "bu"));
    }
}
