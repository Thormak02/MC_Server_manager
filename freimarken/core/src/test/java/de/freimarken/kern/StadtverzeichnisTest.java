package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Namen der Freistaedte fuer das Vorschlagsfenster.
 *
 * <p>Geprueft wird hier, was ein Spieler beim Tippen SIEHT - und zwei Dinge, die ein
 * Blick in den Quelltext nicht hergibt: dass {@link Stadtverzeichnis#erneuere} wirklich
 * ERSETZT (ein Verzeichnis, das nur waechst, schlaegt geloeschte Staedte noch Jahre
 * danach vor), und dass der Anfang eines Wortes gemeint ist und nicht seine Mitte.
 */
@DisplayName("Das Verzeichnis der Stadtnamen")
class StadtverzeichnisTest {

    private final Stadtverzeichnis verzeichnis = new Stadtverzeichnis();

    @Test
    @DisplayName("Ab Werk kennt es nichts - und schlaegt nichts vor")
    void leerSchlaegtNichtsVor() {
        assertEquals(0, verzeichnis.anzahl());
        assertEquals(List.of(), verzeichnis.vorschlaege(""));
        assertEquals(List.of(), verzeichnis.vorschlaege("neu"));
    }

    /**
     * Vorgeschlagen wird der ANFANG, nicht die Mitte.
     *
     * <p><b>An dieser Probe ist das Gegenbeispiel die ganze Arbeit, und es hat zwei
     * Anlaeufe gebraucht.</b> {@code vorschlaege} laeuft ueber {@code tailSet(klein)}
     * und bricht beim ersten Namen ab, der den Anfang nicht traegt. Daraus folgen zwei
     * Bedingungen, und beide muessen erfuellt sein, sonst prueft die Probe NICHTS:
     *
     * <ul>
     *   <li>Das Gegenbeispiel muss hinter dem Anfang SORTIEREN. {@code "altneustadt"}
     *       traegt das Wort, liegt aber vor {@code "neu"} - es kommt im {@code tailSet}
     *       gar nicht vor. (Erster Anlauf, gefunden bei der Lesesuche.)</li>
     *   <li>Zwischen dem Anfang und dem Gegenbeispiel darf KEIN Nicht-Treffer liegen,
     *       sonst bricht die Schleife vorher ab. {@code "nordheim"} zwischen
     *       {@code "neustadt"} und {@code "oberneustadt"} tat genau das. (Zweiter
     *       Anlauf, gefunden von der Einspritzungsrunde - die Probe war gruen, obwohl
     *       {@code startsWith} durch {@code contains} ersetzt war.)</li>
     * </ul>
     *
     * <p>Darum stehen die Nicht-Treffer hier NICHT dabei: Dass ein {@code "nordheim"}
     * nicht vorgeschlagen wird, haelt {@link #mehrereTrefferAlphabetisch()}. Zwei Fragen
     * in einer Probe haben sich hier gegenseitig blind gemacht.
     */
    @Test
    @DisplayName("Vorgeschlagen wird der ANFANG, nicht die Mitte")
    void nurDerAnfangZaehlt() {
        fuelle("neustadt", "oberneustadt");

        // "oberneustadt" TRAEGT das Wort neu und sortiert direkt dahinter - und steht
        // trotzdem nicht da. Wer hier contains statt startsWith nimmt, baut ein
        // Fenster, in dem die halbe Welt steht, sobald jemand drei Buchstaben tippt.
        assertEquals(List.of("neustadt"), verzeichnis.vorschlaege("neu"));

        // Dieselbe Frage am kuerzest moeglichen Fall: ein Buchstabe, zwei Namen, und
        // der zweite traegt ihn hinten. Hier kann kein Abbruch dazwischenkommen.
        Stadtverzeichnis knapp = new Stadtverzeichnis();
        knapp.nimmAuf("a");
        knapp.nimmAuf("ba");
        assertEquals(List.of("a"), knapp.vorschlaege("a"));
    }

    @Test
    @DisplayName("Mehrere Treffer kommen alle - und alphabetisch")
    void mehrereTrefferAlphabetisch() {
        // Absichtlich NICHT in der Reihenfolge eingegeben, in der sie erwartet werden.
        fuelle("neustadt", "nordheim", "neu", "zwickau");

        assertEquals(List.of("neu", "neustadt"), verzeichnis.vorschlaege("neu"));
        assertEquals(List.of("neu", "neustadt", "nordheim"),
                verzeichnis.vorschlaege("n"));
    }

    @Test
    @DisplayName("Ein leerer Anfang schlaegt ALLES vor - wer Tab drueckt, will sehen")
    void leererAnfangSchlaegtAllesVor() {
        fuelle("zwickau", "neustadt", "altdorf");

        assertEquals(List.of("altdorf", "neustadt", "zwickau"),
                verzeichnis.vorschlaege(""));
        // Und keine Obergrenze: Die Kappung bei etwa fuenfzig traefe genau den
        // Spieler, dessen Stadt an einundfuenfzigster Stelle steht.
        List<String> viele = new ArrayList<>();
        for (int nummer = 100; nummer < 500; nummer++) {
            viele.add("stadt" + nummer);
        }
        verzeichnis.erneuere(viele);
        assertEquals(400, verzeichnis.vorschlaege("").size(),
                "399 Staedte passen in die Welt - alle muessen vorgeschlagen werden");
    }

    @Test
    @DisplayName("Die Schreibweise ist egal - auf beiden Seiten")
    void schreibweiseIstEgal() {
        verzeichnis.nimmAuf("  NeuStadt  ");

        // Eingebbar ist alles, gespeichert wird klein - wie in Stadtname.genormt.
        assertEquals(List.of("neustadt"), verzeichnis.vorschlaege("NEU"));
        assertEquals(List.of("neustadt"), verzeichnis.vorschlaege("  neu "));
        assertEquals(1, verzeichnis.anzahl());
    }

    @Test
    @DisplayName("Derselbe Name zweimal bleibt EIN Eintrag")
    void derselbeNameNurEinmal() {
        verzeichnis.nimmAuf("neustadt");
        verzeichnis.nimmAuf("Neustadt");

        assertEquals(1, verzeichnis.anzahl(), "sonst stuende die Stadt doppelt im"
                + " Fenster, und der Spieler suchte den Unterschied");
    }

    @Test
    @DisplayName("erneuere ERSETZT - ein weggefallener Name verschwindet")
    void erneuereErsetzt() {
        fuelle("neustadt", "altdorf");
        assertEquals(2, verzeichnis.anzahl());

        // Der tragende Riegel. Plan 16 laesst eine Stadt nach zwoelf Monaten ohne
        // Besuch verfallen; stuende ihr Name danach weiter im Fenster, schluege der
        // Server eine Reise vor, die mit "gibt es nicht" endet.
        verzeichnis.erneuere(List.of("altdorf"));

        assertEquals(List.of("altdorf"), verzeichnis.vorschlaege(""));
        assertEquals(1, verzeichnis.anzahl());
    }

    @Test
    @DisplayName("Leere Namen und null werfen nicht - sie werden uebersprungen")
    void leeresWirdUebersprungen() {
        List<String> mitLuecken = Arrays.asList("neustadt", null, "   ", "");

        verzeichnis.erneuere(mitLuecken);

        assertEquals(List.of("neustadt"), verzeichnis.vorschlaege(""));
        verzeichnis.nimmAuf(null);
        verzeichnis.nimmAuf("");
        assertEquals(1, verzeichnis.anzahl());
    }

    @Test
    @DisplayName("erneuere mit null leert - und wirft nicht mitten im Start")
    void erneuereMitNullLeert() {
        fuelle("neustadt");

        verzeichnis.erneuere(null);

        assertTrue(verzeichnis.vorschlaege("").isEmpty());
    }

    private void fuelle(String... namen) {
        for (String name : namen) {
            verzeichnis.nimmAuf(name);
        }
    }
}
