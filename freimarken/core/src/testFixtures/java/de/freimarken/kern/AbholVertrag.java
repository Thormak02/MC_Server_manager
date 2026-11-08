package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Abholablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code MietVertrag}: Die eine zaehlt eine Karte durch, die andere
 * schickt ein {@code SELECT COUNT(*)} - und genau an so einer Stelle laufen zwei
 * Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Das erste Versprechen ist der Kern dieses Vertrags</b>: Nichts wird von selbst
 * geloescht. Dafuer kann keine Probe stehen, die auf eine Jahresfrist wartet - also
 * pruefen diese Proben die zwei Wege, auf denen ein Stueck ueberhaupt verschwinden kann
 * ({@link #nimmLoeschtGenauDieGenannten}, {@link #unbekannteNummerIstKeinFehler}), und
 * dass das Einlegen nichts wegnimmt ({@link #zweimalEinlegenLegtDazu}).
 */
public abstract class AbholVertrag {

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    protected static final Instant WANN = Instant.parse("2026-11-08T14:30:00Z");

    /** Eine frische, leere Ablage. */
    protected abstract Abholablage ablage();

    /** Ein Stapel mit unterscheidbarem Inhalt. */
    protected static Abholgut gut(String was) {
        return new Abholgut(was, "inhalt-von-" + was);
    }

    @Test
    @DisplayName("Ein leeres Fach ist eine leere Liste und kein Fehler")
    void leerIstKeinFehler() {
        Abholablage ablage = ablage();

        assertTrue(ablage.fuerSpieler(UUID.randomUUID()).isEmpty());
        assertEquals(0, ablage.anzahlFuer(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Was eingelegt wurde, kommt zurueck - alle fuenf Werte")
    void hinUndZurueck() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("64x Stein")));

        List<Abholstueck> seine = ablage.fuerSpieler(anna);
        assertEquals(1, seine.size());
        Abholstueck stueck = seine.get(0);
        assertTrue(stueck.id() >= 1L, "die Nummer entsteht in der Ablage: " + stueck.id());
        assertEquals(anna, stueck.empfaenger());
        assertEquals("Laden halle-1", stueck.herkunft());
        assertEquals(WANN, stueck.eingelegt());
        assertEquals("64x Stein", stueck.beschriftung());
        assertEquals("inhalt-von-64x Stein", stueck.inhalt());
    }

    @Test
    @DisplayName("Das Fach eines Spielers zeigt NUR seine Stuecke")
    void nurSeine() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("Annas Stein")));
        ablage.lege(bert, "Laden halle-2", WANN, List.of(gut("Berts Holz")));

        assertEquals(List.of("Annas Stein"), beschriftungen(ablage, anna));
        assertEquals(List.of("Berts Holz"), beschriftungen(ablage, bert));
        assertEquals(1, ablage.anzahlFuer(anna));
    }

    @Test
    @DisplayName("Das AELTESTE Stueck steht vorn - zweimal hintereinander gleich")
    void aeltestesZuerst() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("zuerst"), gut("dann")));
        ablage.lege(anna, "Laden halle-1", WANN.plusSeconds(60), List.of(gut("zuletzt")));

        // Die REIHENFOLGE und nicht bloss die Menge: Wer ein Fach in mehreren Griffen
        // leert, bekaeme sonst beim zweiten Griff andere Stapel angezeigt als beim
        // ersten. Dieselbe Liste zweimal gelesen - eine Umsetzung ohne ORDER BY ist hier
        // nicht zwingend rot, aber die Probe steht, weil sie es sein KANN.
        assertEquals(List.of("zuerst", "dann", "zuletzt"), beschriftungen(ablage, anna));
        assertEquals(List.of("zuerst", "dann", "zuletzt"), beschriftungen(ablage, anna));
    }

    @Test
    @DisplayName("Jedes Stueck bekommt eine EIGENE Nummer")
    void eigeneNummern() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("eins"), gut("zwei")));

        List<Abholstueck> seine = ablage.fuerSpieler(anna);
        // Zwei Stapel aus EINEM Einlegen, und das ist der gefaehrliche Fall: Eine
        // Umsetzung, die die Nummer einmal je Aufruf zieht statt einmal je Stapel, gibt
        // beiden dieselbe - und dann loescht das Holen des einen auch den anderen.
        assertNotEquals(seine.get(0).id(), seine.get(1).id());
    }

    @Test
    @DisplayName("Zweimal einlegen legt DAZU und ersetzt nicht")
    void zweimalEinlegenLegtDazu() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("alt")));
        ablage.lege(anna, "Laden halle-1", WANN.plusSeconds(1), List.of(gut("neu")));

        assertEquals(2, ablage.anzahlFuer(anna));
    }

    @Test
    @DisplayName("Eine leere Liste einzulegen tut nichts und wirft nicht")
    void leerEinlegenGehtDurch() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // Der haeufigste Fall beim Raeumen: Die Truhe eines verlassenen Ladens ist leer.
        // Wuerfe das, muesste jeder Aufrufer vorher fragen - und einer vergisst es.
        ablage.lege(anna, "Laden halle-1", WANN, List.of());

        assertEquals(0, ablage.anzahlFuer(anna));
    }

    @Test
    @DisplayName("nimm loescht GENAU die genannten Nummern und sagt wie viele")
    void nimmLoeschtGenauDieGenannten() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.lege(anna, "Laden halle-1", WANN,
                List.of(gut("eins"), gut("zwei"), gut("drei")));
        List<Abholstueck> seine = ablage.fuerSpieler(anna);

        int weg = ablage.nimm(anna, List.of(seine.get(0).id(), seine.get(2).id()));

        assertEquals(2, weg);
        // Das MITTLERE bleibt, und das ist der Zweck dieser Auswahl: Eine Umsetzung, die
        // "von bis" loescht statt der genannten Nummern, nimmt hier alle drei.
        assertEquals(List.of("zwei"), beschriftungen(ablage, anna));
    }

    @Test
    @DisplayName("Eine unbekannte Nummer wird uebergangen und ist kein Fehler")
    void unbekannteNummerIstKeinFehler() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("eins")));
        long seine = ablage.fuerSpieler(anna).get(0).id();

        // Derselbe Griff zweimal - zwei Fenster, die dasselbe Stueck holen. Das zweite
        // darf nicht werfen, es bekommt nur nichts. Genau diese 0 ist die Erlaubnis, die
        // der Aufrufer braucht: Er gibt heraus, was er geloescht hat.
        assertEquals(1, ablage.nimm(anna, List.of(seine)));
        assertEquals(0, ablage.nimm(anna, List.of(seine)));
        assertEquals(0, ablage.anzahlFuer(anna));
    }

    @Test
    @DisplayName("Ein FREMDES Stueck wird nicht genommen")
    void fremdesStueckBleibtLiegen() {
        // Das vierte Versprechen, und es ist kein Misstrauen gegen Spieler, sondern eine
        // Rechnung: MariaDB vergibt die Nummern mit AUTO_INCREMENT, und dieser Zaehler
        // beginnt nach einem Neustart bei MAX(id) + 1. Wurde die hoechste Zeile vorher
        // abgeholt, wird ihre Nummer ein zweites Mal vergeben - und dann holt ein alt
        // gewordenes Fenster die Ware eines fremden Spielers, ohne dass irgendwo eine
        // Pruefung fehlt. Der Empfaenger in der Abfrage ist die Schranke dagegen.
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.lege(bert, "Laden halle-2", WANN, List.of(gut("Berts Holz")));
        long berts = ablage.fuerSpieler(bert).get(0).id();

        assertEquals(0, ablage.nimm(anna, List.of(berts)));
        assertEquals(List.of("Berts Holz"), beschriftungen(ablage, bert));
    }

    @Test
    @DisplayName("Eine leere Liste zu nehmen loescht nichts")
    void leerNehmenLoeschtNichts() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.lege(anna, "Laden halle-1", WANN, List.of(gut("eins")));

        assertEquals(0, ablage.nimm(anna, List.of()));
        assertEquals(1, ablage.anzahlFuer(anna));
    }

    @Test
    @DisplayName("Eine Nummer, die keine ist, wird abgewiesen")
    void keineNummerWirdAbgewiesen() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimm(anna, List.of(0L)));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimm(anna, List.of(-1L)));
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Liste wird abgewiesen")
    void nullWirdAbgewiesen() {
        Abholablage ablage = ablage();

        assertThrows(IllegalArgumentException.class, () -> ablage.fuerSpieler(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.anzahlFuer(null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimm(null, List.of(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimm(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(UUID.randomUUID(), "Laden halle-1", WANN, null));
    }

    @Test
    @DisplayName("Ein Stueck mit zwei Zeilen Inhalt kommt Zeichen fuer Zeichen zurueck")
    void langerInhaltBleibtGleich() {
        Abholablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        // Was die Schicht darueber schreibt, ist Text aus Base64 - aber eine Umsetzung,
        // die ihn trimmt, Anfuehrungszeichen verschluckt oder bei 255 Zeichen abschneidet,
        // macht aus einem Gegenstand einen, der nicht mehr zu lesen ist. Darum stehen hier
        // ein langer Inhalt UND die Zeichen, an denen Spalten und Abfragen scheitern.
        StringBuilder lang = new StringBuilder("  'x' \"y\" \\z% _ ");
        while (lang.length() < 600) {
            lang.append("AbCd+/=0123456789");
        }
        String inhalt = lang.toString();

        ablage.lege(anna, "Laden halle-1", WANN,
                List.of(new Abholgut("1x Buch", inhalt)));

        assertEquals(inhalt, ablage.fuerSpieler(anna).get(0).inhalt());
    }

    private static List<String> beschriftungen(Abholablage ablage, UUID wer) {
        List<String> namen = new ArrayList<>();
        for (Abholstueck stueck : ablage.fuerSpieler(wer)) {
            namen.add(stueck.beschriftung());
        }
        return namen;
    }
}
