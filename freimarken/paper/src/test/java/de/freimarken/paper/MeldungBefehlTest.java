package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Meldeablage;
import de.freimarken.kern.Meldebuch;
import de.freimarken.kern.Meldestand;
import de.freimarken.kern.Meldewerte;
import de.freimarken.kern.Meldung;
import de.freimarken.kern.SpeicherMeldungen;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /meldung} GETIPPT - die Liste, die Einzelansicht, das Erledigen und das
 * Hinspringen.
 *
 * <p><b>Diese Datei traegt zwei Deckungen, die anderswo zugesagt sind:</b> Sowohl
 * {@code Sprachschluessel.familienDeckung} als auch {@code SendewegTest} 33x erklaeren
 * die vier Standworte fuer selbstbescheinigend und verweisen auf <i>diese</i> Probe -
 * <b>jeder</b> Stand muss hier durch die echte Zeile aus der ausgelieferten Vorlage
 * gehen. Ohne {@link #jederStandHatSeinWort()} waere das eine Zusage ohne Bau, und ein
 * Stand ohne Wort stuende im Spiel mit seinem Pfad da.
 *
 * <p>Getippt wird als SPIELER mit Recht, nicht auf der Konsole: {@code /meldung} verlangt
 * fuer das Nehmen und Erledigen eine Kennung, und die Konsole hat keine. Was die Konsole
 * darf, prueft {@link #konsoleKannNichtNehmen()}.
 */
@DisplayName("/meldung getippt")
class MeldungBefehlTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final UUID ANNA = Spielerprobe.kennungAus(801);

    private static final UUID BERT = Spielerprobe.kennungAus(802);

    private static final String WELT = "altfurt";

    private SpeicherMeldungen ablage;
    private Sprache sprache;
    private Spielerprobe moderator;
    private List<String> gereist;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        ablage = new SpeicherMeldungen();
        sprache = Sprachprobe.spracheAusDemJar();
        moderator = new Spielerprobe("Chef", Spielerprobe.kennungAus(803),
                MeldungBefehle.RECHT);
        gereist = new ArrayList<>();
    }

    @Test
    @DisplayName("1 - /meldung ohne Meldung sagt, dass nichts anliegt")
    void leereListe() throws CommandSyntaxException {
        List<String> zeilen = tippe("meldung");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("keine Meldung"), zeilen.get(0));
    }

    @Test
    @DisplayName("2 - die Liste nennt Nummer, Namen, Grund, Alter und Stand")
    void listeNenntAlles() throws CommandSyntaxException {
        lege("baut auf meiner Parzelle", MITTAGS.minusSeconds(3L * 3600L));

        List<String> zeilen = tippe("meldung");

        // Kopf, eine Zeile, Hinweis.
        assertEquals(3, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("Anna"), zeilen.get(1));
        assertTrue(zeilen.get(1).contains("Bert"), zeilen.get(1));
        assertTrue(zeilen.get(1).contains("baut auf meiner Parzelle"), zeilen.get(1));
        // Das ALTER und nicht der Zeitpunkt: Was einen Moderator interessiert, ist wie
        // lange eine Meldung liegt - das Datum muesste er erst abziehen.
        assertTrue(zeilen.get(1).contains("vor 3 Std."), zeilen.get(1));
        assertTrue(zeilen.get(1).contains("offen"), zeilen.get(1));
    }

    @Test
    @DisplayName("3 - ein langer Grund wird in der LISTE gekuerzt, mit Auslassungszeichen")
    void langerGrundWirdGekuerzt() throws CommandSyntaxException {
        String lang = "x".repeat(Meldetext.GRUND_IN_DER_LISTE + 20);
        lege(lang, MITTAGS);

        List<String> listenzeile = tippe("meldung");

        // MIT Zeichen am Ende, damit kein Moderator glaubt, der Grund sei kurz: Ein stumm
        // abgeschnittener Satz liest sich wie ein vollstaendiger, und dann oeffnet er die
        // Meldung nicht, in der die Haelfte steht.
        assertTrue(listenzeile.get(1).contains("…"), listenzeile.get(1));
        assertFalse(listenzeile.get(1).contains(lang),
                "der ganze Grund gehoert nicht in die Liste");
    }

    @Test
    @DisplayName("4 - die Einzelansicht zeigt den GANZEN Grund und den Ort")
    void einzelansicht() throws CommandSyntaxException {
        String lang = "y".repeat(Meldetext.GRUND_IN_DER_LISTE + 20);
        long nummer = lege(lang, MITTAGS).nummer();

        List<String> zeilen = tippe("meldung " + nummer);

        // Kopf, Wer, Grund, Ort - vier Zeilen bei einer offenen Meldung.
        assertEquals(4, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(2).contains(lang),
                "hier ist Platz, und genau dafuer oeffnet man sie einzeln");
        assertTrue(zeilen.get(3).contains(WELT), zeilen.get(3));
        assertTrue(zeilen.get(3).contains("10") && zeilen.get(3).contains("-60"),
                "der Tatort: " + zeilen.get(3));
    }

    @Test
    @DisplayName("5 - Bearbeiter und Vermerk werden WEGGELASSEN, nicht leer gesendet")
    void bearbeiterUndVermerkNurWennVorhanden() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();

        // JE SCHRITT EIN FRISCHER LESER, und das ist nicht Umstaendlichkeit: Die
        // Mitschrift der Spielerprobe SAMMELT ueber ihre ganze Lebenszeit. Mit einem
        // Leser fuer alle drei Schritte zaehlte die zweite Zusage 4+1+5 Zeilen - gemessen
        // am 25.11.2026, und die Probe war rot mit einer Zahl, die nichts bedeutet.
        assertEquals(4, zeige(nummer, 1).size(), "offen: vier Zeilen");

        tippe("meldung " + nummer + " annehmen");
        assertEquals(5, zeige(nummer, 2).size(),
                "in Arbeit: eine Zeile mehr fuer den Bearbeiter");

        tippe("meldung " + nummer + " abschliessen angesprochen");
        List<String> fertig = zeige(nummer, 3);

        // Eine Zeile "Bearbeiter:" ohne Namen saehe aus wie ein Fehler, und bei einer
        // offenen Meldung gibt es keinen. Darum weglassen statt leer senden.
        assertEquals(6, fertig.size(), fertig.toString());
        assertTrue(fertig.get(5).contains("angesprochen"), fertig.get(5));
    }

    @Test
    @DisplayName("6 - annehmen setzt den Bearbeiter, und der zweite liest SEINEN Namen")
    void annehmenUndSchonGenommen() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();
        assertTrue(tippe("meldung " + nummer + " annehmen").get(0)
                .contains("bei dir"), "die Vorbereitung");

        Spielerprobe zweiter = new Spielerprobe("Zweiter",
                Spielerprobe.kennungAus(804), MeldungBefehle.RECHT);
        List<String> zeilen = tippe("meldung " + nummer + " annehmen", zweiter);

        // Der NAME ist der Zweck des Satzes: Ein blosses "zu spaet" liesse den zweiten
        // Moderator ratlos. Mit dem Namen weiss er, wen er fragen kann.
        assertTrue(zeilen.get(0).contains("Chef"), zeilen.get(0));
    }

    @Test
    @DisplayName("7 - abschliessen OHNE Vermerk geht, und der Melder liest das Ersatzwort")
    void abschliessenOhneVermerk() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();

        assertTrue(tippe("meldung " + nummer + " abschliessen").get(0)
                .contains("abgeschlossen"));

        // Eine klare Sache soll sich ohne Worte erledigen lassen - ein Pflichtfeld fuehrt
        // zu "ok" und nicht zu besseren Vermerken. Der Melder liest dann das Ersatzwort.
        Meldung fertig = ablage.lies(nummer).orElseThrow();
        assertEquals("", fertig.vermerk());
        assertEquals(Meldestand.ABGESCHLOSSEN, fertig.stand());
    }

    @Test
    @DisplayName("8 - abweisen ist ein eigener Weg mit eigener Zeile")
    void abweisen() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();

        List<String> zeilen = tippe("meldung " + nummer + " abweisen nichts gefunden");

        assertTrue(zeilen.get(0).contains("abgewiesen"), zeilen.get(0));
        assertTrue(zeilen.get(0).contains("Anna"),
                "der Melder steht darin - er bekommt die Antwort: " + zeilen.get(0));
        Meldung fertig = ablage.lies(nummer).orElseThrow();
        assertEquals(Meldestand.ABGEWIESEN, fertig.stand());
        assertEquals("nichts gefunden", fertig.vermerk(),
                "der Vermerk ist ein greedyString - ein Satz, kein Wort");
    }

    @Test
    @DisplayName("9 - hin reist an den Ort des MELDERS")
    void hinReistZumTatort() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();

        List<String> zeilen = tippe("meldung " + nummer + " hin");

        assertEquals(List.of(WELT + " 10/-60/-20"), gereist,
                "die Reisehand hat genau einmal den Tatort bekommen");
        assertTrue(zeilen.get(0).contains(WELT), zeilen.get(0));
    }

    @Test
    @DisplayName("10 - gibt es die Welt nicht mehr, sagt der Befehl das")
    void hinOhneWelt() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();
        // Die Reisehand sagt nein - so, wie es nach einem Kornmark-Reset ist.
        baum(Meldewerte.VORGABE, false).tippe("meldung " + nummer + " hin", moderator);

        assertTrue(moderator.chatBild().get(0).contains("gibt es nicht mehr"),
                moderator.chatBild().get(0));
        assertEquals(List.of(), gereist, "und gereist wird NICHT");
    }

    @Test
    @DisplayName("11 - eine unbekannte Nummer wird in jedem Zweig abgewiesen")
    void unbekannteNummer() throws CommandSyntaxException {
        for (String zweig : List.of("meldung 99", "meldung 99 annehmen",
                "meldung 99 abschliessen", "meldung 99 abweisen", "meldung 99 hin")) {
            List<String> zeilen = tippe(zweig);
            assertTrue(zeilen.get(0).contains("99"),
                    zweig + " nennt die Nummer nicht: " + zeilen);
        }
    }

    @Test
    @DisplayName("12 - jeder Zweig antwortet, auch ohne sein Argument")
    void jederZweigAntwortet() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();

        // Die Literale annehmen/abschliessen/abweisen/hin haben ihr eigenes executes.
        // Ohne sie endet /meldung 1 abschliessen in Brigadiers englischem "Unknown or
        // incomplete command" - und zwar GENAU dann, wenn der Moderator keinen Vermerk
        // schreiben wollte.
        assertFalse(tippe("meldung").isEmpty(), "meldung ohne alles");
        assertFalse(tippe("meldung " + nummer).isEmpty(), "nur die Nummer");
        assertFalse(tippe("meldung " + nummer + " abschliessen").isEmpty(),
                "abschliessen ohne Vermerk");
        assertFalse(tippe("meldung " + nummer + " abweisen").isEmpty(),
                "abweisen ohne Vermerk");
    }

    @Test
    @DisplayName("13 - DIE DECKUNG: jeder Meldestand hat sein Wort in der Vorlage")
    void jederStandHatSeinWort() {
        // Diese Probe ist an ZWEI Stellen zugesagt - in Sprachschluessel.familienDeckung
        // und in SendewegTest 33x. Beide erklaeren die vier Standworte fuer
        // selbstbescheinigend und verweisen hierher. Ohne sie waere das eine Zusage ohne
        // Bau, und ein Stand ohne Wort stuende im Spiel mit seinem Pfad da.
        for (Meldestand stand : Meldestand.values()) {
            String wort = Meldetext.standwort(sprache, stand);
            assertFalse(wort.isBlank(), stand + " hat kein Wort");
            assertFalse(wort.startsWith("meldung."),
                    stand + " liefert seinen PFAD statt eines Wortes: " + wort);
        }
        // Und sie muessen sich UNTERSCHEIDEN: Waeren zwei gleich, koennte die Liste die
        // Angabe ebenso gut weglassen - und ein Moderator saehe nicht, was schon jemand
        // genommen hat.
        assertEquals(Meldestand.values().length,
                java.util.Arrays.stream(Meldestand.values())
                        .map(stand -> Meldetext.standwort(sprache, stand))
                        .distinct().count(),
                "zwei Staende mit demselben Wort");
    }

    @Test
    @DisplayName("14 - das Alter steht in vier Groessenordnungen, und sie unterscheiden sich")
    void alterInVierGroessenordnungen() {
        assertEquals("soeben", Meldetext.alter(sprache, MITTAGS, MITTAGS));
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(300L), MITTAGS)
                .contains("5"), "fuenf Minuten");
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(7200L), MITTAGS)
                .contains("2"), "zwei Stunden");
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(3L * 86400L), MITTAGS)
                .contains("3"), "drei Tage");
        // Eine Dauer in der ZUKUNFT gibt "soeben" und keine negative Zahl: Das kommt bei
        // einer verstellten Uhr vor, und "vor -3 Minuten" ist keine Auskunft.
        assertEquals("soeben", Meldetext.alter(sprache, MITTAGS.plusSeconds(600L),
                MITTAGS));
    }

    @Test
    @DisplayName("15 - 59 Minuten sind Minuten, 60 sind eine Stunde")
    void grenzeZwischenMinutenUndStunden() {
        // Die GRENZE selbst, und zwar von beiden Seiten: Ohne diese Probe waeren > und
        // >= gleichwertig, solange keine Probe die Gleichheit trifft.
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(59L * 60L), MITTAGS)
                .contains("59"), "59 Minuten");
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(60L * 60L), MITTAGS)
                .contains("Std."), "60 Minuten sind eine Stunde");
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(23L * 3600L), MITTAGS)
                .contains("Std."), "23 Stunden");
        assertTrue(Meldetext.alter(sprache, MITTAGS.minusSeconds(24L * 3600L), MITTAGS)
                .contains("Tg."), "24 Stunden sind ein Tag");
    }

    @Test
    @DisplayName("16 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe ohne = new Spielerprobe("Gast", Spielerprobe.kennungAus(805));

        assertThrows(CommandSyntaxException.class,
                () -> baum(Meldewerte.VORGABE, true).tippe("meldung", ohne));
    }

    @Test
    @DisplayName("17 - die Konsole kann nicht nehmen - 'in Arbeit bei der Konsole' sagt nichts")
    void konsoleKannNichtNehmen() throws CommandSyntaxException {
        long nummer = lege("Vorfall", MITTAGS).nummer();
        Befehlsprobe baum = baum(Meldewerte.VORGABE, true);

        baum.tippeAufDerKonsole("meldung " + nummer + " annehmen");

        // Die KONSOLE liest hier, nicht ein Spieler - darum ihre eigene Mitschrift.
        assertTrue(baum.konsoleBild().get(0).contains("Spieler"),
                baum.konsoleBild().get(0));
        assertEquals(Meldestand.OFFEN, ablage.lies(nummer).orElseThrow().stand(),
                "und es ist NICHTS passiert");
    }

    @Test
    @DisplayName("18 - die Liste zeigt hoechstens so viele Zeilen wie eingestellt")
    void listeHaeltDieSeitenlaenge() throws CommandSyntaxException {
        for (int nummer = 0; nummer < 5; nummer++) {
            ablage.lege(ANNA, BERT, "Vorfall " + nummer, WELT, 10, -60, -20,
                    MITTAGS.plusSeconds(nummer));
        }

        baum(Meldewerte.VORGABE.mitZeilenJeSeite(2), true).tippe("meldung", moderator);

        // Kopf, zwei Zeilen, Hinweis. Die Zahl kommt aus der DATEI - sonst waere die
        // Seitenlaenge eine Zahl, die nur ein Neubau aendert.
        assertEquals(4, moderator.chatBild().size(), moderator.chatBild().toString());
    }

    @Test
    @DisplayName("19 - ohne Buch, Sprache, Namensgeber, Reisehand, Uhr oder Protokoll nichts")
    void riegel() {
        Meldebuch buch = new Meldebuch(ablage, () -> Meldewerte.VORGABE,
                MeldungBefehle.RECHT);
        Meldetext.Namensgeber namen = wer -> "x";
        MeldungBefehle.Reisehand reise = (a, b, c, d, e, f) -> true;
        MeldungBefehle.Uhr uhr = () -> MITTAGS;
        Logger stumm = Logger.getLogger("MeldungBefehlRiegel");

        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(null, sprache, namen, reise, uhr, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(buch, null, namen, reise, uhr, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(buch, sprache, null, reise, uhr, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(buch, sprache, namen, null, uhr, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(buch, sprache, namen, reise, null, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new MeldungBefehle(buch, sprache, namen, reise, uhr, null));
    }

    /**
     * Zeigt eine Meldung mit einem FRISCHEN Leser - und gibt nur dessen Zeilen.
     *
     * <p>Die Mitschrift der {@link Spielerprobe} sammelt ueber ihre ganze Lebenszeit.
     * Wer in einer Probe mehrmals liest, braucht darum je Schritt einen eigenen Leser;
     * sonst zaehlt die zweite Zusage die Zeilen der ersten mit. Die {@code nummer}
     * unterscheidet die Leser, damit auch ihre Kennungen verschieden sind.
     */
    private List<String> zeige(long meldung, int schritt) throws CommandSyntaxException {
        Spielerprobe leser = new Spielerprobe("Leser" + schritt,
                Spielerprobe.kennungAus(810 + schritt), MeldungBefehle.RECHT);
        return tippe("meldung " + meldung, leser);
    }

    /** Legt eine Meldung von Anna gegen Bert an. */
    private Meldung lege(String grund, Instant wann) {
        return ablage.lege(ANNA, BERT, grund, WELT, 10, -60, -20, wann);
    }

    private List<String> tippe(String eingabe) throws CommandSyntaxException {
        return tippe(eingabe, moderator);
    }

    private List<String> tippe(String eingabe, Spielerprobe wer)
            throws CommandSyntaxException {
        baum(Meldewerte.VORGABE, true).tippe(eingabe, wer);
        return wer.chatBild();
    }

    /**
     * Der Baum.
     *
     * @param reisegeht ob die Reisehand gelingt; {@code false} stellt eine Welt dar, die
     *                  es nicht mehr gibt - etwa nach einem Kornmark-Reset
     */
    private Befehlsprobe baum(Meldewerte werte, boolean reisegeht) {
        return new Befehlsprobe(List.of(new MeldungBefehle(
                new Meldebuch(ablage, () -> werte, MeldungBefehle.RECHT), sprache,
                MeldungBefehlTest::nameVon,
                (wer, welt, x, y, z, ziel) -> {
                    if (!reisegeht) {
                        return false;
                    }
                    gereist.add(welt + " " + x + "/" + y + "/" + z);
                    return true;
                },
                () -> MITTAGS,
                Logger.getLogger("MeldungBefehlProbe-" + UUID.randomUUID())).meldung()));
    }

    /** Die Namensnaht - zwei bekannte Kennungen, sonst die ersten Zeichen. */
    private static String nameVon(UUID wer) {
        if (ANNA.equals(wer)) {
            return "Anna";
        }
        if (BERT.equals(wer)) {
            return "Bert";
        }
        if (Spielerprobe.kennungAus(803).equals(wer)) {
            return "Chef";
        }
        return wer.toString().substring(0, Spielername.ZEICHEN_DER_KENNUNG);
    }
}
