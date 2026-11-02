package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel, die jede Weltgrenze bindet - ohne Welt, ohne Raster.
 *
 * <p>{@link StadtgrenzeTest} prueft dieselbe Regel noch einmal durch die Brille der
 * Marktstadt, also mit Rundung auf Rasterzellen. Hier steht sie nackt: Die uebrigen
 * Welten aus Plan 4 haben kein Raster, und ihre Grenze muss trotzdem halten.
 */
class GrenzregelTest {

    /** Wie bei einer gewoehnlichen Welt: irgendein Mindestmass, hier 100 Bloecke. */
    private static final long MINDESTENS = 100L;
    private static final long RUECKFALL = 800L;

    private static long wirksam(long ausEinstellung, double inDerWelt, boolean bestaetigt) {
        return Grenzregel.wirksam(ausEinstellung, inDerWelt, MINDESTENS, RUECKFALL, bestaetigt);
    }

    @Test
    @DisplayName("Eine frische Welt bekommt, was in der Einstellung steht")
    void frischeWelt() {
        // Das Hoechstmass IST die Vorgabe einer frischen Welt. Es heisst nicht "riesig",
        // sondern "hier wurde noch nie eine Grenze gesetzt".
        assertEquals(800L, wirksam(800, Grenzregel.HOECHSTMASS, false));
    }

    @Test
    @DisplayName("Nach aussen geht immer")
    void nachAussenGehtImmer() {
        assertEquals(1500L, wirksam(1500, 1000.0, false));
    }

    @Test
    @DisplayName("Nach innen nie - auch wenn jemand die Zahl kleiner macht")
    void nachInnenNie() {
        // Der Kern der Regel: Was heute drin liegt, liegt auch danach drin. Sonst stuende
        // ein bezahltes Grundstueck ueber Nacht vor der Tuer.
        assertEquals(2000L, wirksam(500, 2000.0, false));
    }

    @Test
    @DisplayName("Mehr als das Doppelte braucht eine Bestaetigung")
    void sprungBrauchtBestaetigung() {
        assertEquals(1000L, wirksam(5000, 1000.0, false), "ohne Bestaetigung bleibt es");
        assertEquals(5000L, wirksam(5000, 1000.0, true), "mit Bestaetigung geht es");
    }

    @Test
    @DisplayName("Genau das Doppelte ist noch kein Sprung")
    void genauDasDoppelteGehtOhne() {
        // Die Schranke steht bei "mehr als", nicht bei "mindestens". Eine Grenze zu
        // verdoppeln ist der normale Weg zu wachsen; erst darueber wird es ein Vertipper.
        assertEquals(2000L, wirksam(2000, 1000.0, false));
    }

    @Test
    @DisplayName("Eine unbrauchbare Einstellung faellt auf den Rueckfall zurueck")
    void unbrauchbareEinstellung() {
        // Zu klein, um eine Grenze zu sein - und die Welt hat noch keine.
        assertEquals(RUECKFALL, wirksam(10, Grenzregel.HOECHSTMASS, false));
        // Groesser, als eine Weltgrenze annimmt.
        assertEquals(RUECKFALL, wirksam((long) Grenzregel.HOECHSTMASS + 1,
                Grenzregel.HOECHSTMASS, false));
    }

    @Test
    @DisplayName("Eine von Hand verstellte Minigrenze blockiert nicht")
    void minigrenzeInDerWelt() {
        // In der Welt steht etwas, das keine Grenze mehr ist. Dann gilt die Einstellung -
        // sonst bliebe die Welt fuer immer auf dem Unsinn stehen, den jemand gesetzt hat.
        assertEquals(800L, wirksam(800, 10.0, false));
    }

    @Test
    @DisplayName("Der Bestaetigungstext nennt beide Zahlen")
    void bestaetigungstext() {
        // Er muss genau so in der Einstellungsdatei stehen - deshalb ist er eine Zusage
        // und keine blosse Meldung.
        assertEquals("2989 -> 8000", Grenzregel.sprungText(8000, 2989.0));
    }

    @Test
    @DisplayName("Gilt die eingetragene Zahl, gibt es nichts zu klagen")
    void keineKlageOhneGrund() {
        assertNull(Grenzregel.klage("welten.altfurt", 2000, 2000, 2000,
                Grenzregel.MINDESTKANTE));
    }

    @Test
    @DisplayName("Beim Verkleinern wird keine Bestaetigung verlangt - sie wirkt nicht")
    void verkleinernOhneFalschenRat() {
        // Das fruehere Fehlerbild: Ein Betreiber traegt kante: 800 ein, wo 2000 steht.
        // Die Meldung nannte ihm "sprung-bestaetigt: 2000 -> 800", er trug es ein,
        // startete neu - und bekam dieselbe Meldung. Der Text wird nur im Sprung-Zweig
        // gelesen; Verkleinern ist nie moeglich.
        String klage = Grenzregel.klage("welten.altfurt", 800, 2000.0, 2000,
                Grenzregel.MINDESTKANTE);

        assertTrue(klage.contains("nie nach innen"), klage);
        assertTrue(!klage.contains("sprung-bestaetigt"),
                "beim Verkleinern darf kein Rat stehen, der ins Leere fuehrt: " + klage);
    }

    @Test
    @DisplayName("Beim Sprung nach oben steht der Text da, der ihn freigibt")
    void sprungMitRat() {
        String klage = Grenzregel.klage("welten.altfurt", 8000, 2000.0, 2000,
                Grenzregel.MINDESTKANTE);

        assertTrue(klage.contains("welten.altfurt.sprung-bestaetigt: 2000 -> 8000"), klage);
    }

    @Test
    @DisplayName("Eine unbrauchbare Zahl wird als solche benannt")
    void unbrauchbareZahlWirdBenannt() {
        String klage = Grenzregel.klage("welten.altfurt", 8, 2000.0, 2000,
                Grenzregel.MINDESTKANTE);

        assertTrue(klage.contains("keine Grenze zu machen"), klage);
        assertTrue(!klage.contains("sprung-bestaetigt"), klage);
    }

    @Test
    @DisplayName("Brauchbar ist, was Minecraft annimmt - und mehr als ein Kaefig")
    void brauchbareKanten() {
        assertTrue(!Grenzregel.brauchbar(0));
        assertTrue(!Grenzregel.brauchbar(31));
        assertTrue(Grenzregel.brauchbar(32));
        assertTrue(Grenzregel.brauchbar((long) Grenzregel.HOECHSTMASS));
        assertTrue(!Grenzregel.brauchbar((long) Grenzregel.HOECHSTMASS + 1));
    }
}
