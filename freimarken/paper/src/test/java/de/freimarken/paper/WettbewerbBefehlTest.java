package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherWettbewerb;
import de.freimarken.kern.Wettbewerbsbuch;
import de.freimarken.kern.Wettbewerbswerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /wettbewerb} GETIPPT - Stand, Rangliste, Anmeldung und jede Absage.
 *
 * <p>Eine Zahltafel sagt nichts darueber, ob ein Zweig antwortet: Ohne diese Datei waere
 * {@code /wettbewerb anmelden} eine Zeile, die uebersetzt und angemeldet ist - und die im
 * Spiel in Brigadiers englischem "Unknown or incomplete command" endet, weil ihr eigenes
 * {@code executes} fehlt.
 *
 * <p><b>Die UHR ist hier festgenagelt</b>: Jede Probe sagt, ob sie im Fenster steht oder
 * davor. Eine echte Uhr machte diese Proben zwischen 18:00 und 19:00 gruen und sonst rot.
 */
@DisplayName("/wettbewerb getippt")
class WettbewerbBefehlTest {

    private static final UUID ANNA = Spielerprobe.kennungAus(414);

    private static final UUID BERT = Spielerprobe.kennungAus(415);

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final LocalDate SONNTAG = LocalDate.of(2026, 11, 22);

    private static final Instant IM_FENSTER =
            SONNTAG.atTime(18, 30).atZone(BERLIN).toInstant();

    private static final Instant VOR_DEM_FENSTER =
            SONNTAG.atTime(12, 0).atZone(BERLIN).toInstant();

    private Spielerprobe anna;
    private SpeicherKasse kasse;
    private SpeicherWettbewerb ablage;
    private Wirtschaftsstand stand;

    private static Wettbewerbswerte werte(Set<DayOfWeek> tage) {
        return new Wettbewerbswerte(true, LocalTime.of(18, 0), Duration.ofMinutes(60),
                tage, 250L, 0L, List.of(50, 30, 20), 1, Map.of("heart_of_the_sea", 100));
    }

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        anna = new Spielerprobe("Anna", ANNA, WettbewerbBefehle.RECHT);
        kasse = new SpeicherKasse(0L);
        ablage = new SpeicherWettbewerb();
        kasse.eroeffne(ANNA);
        kasse.eroeffne(BERT);
        kasse.gutschreiben(ANNA, 1_000L, Buchungsgrund.BESTAND);
        stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitWettbewerb(werte(EnumSet.allOf(DayOfWeek.class)))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
    }

    /** Der Baum mit einer FESTEN Uhr - siehe den Klassenkopf. */
    private Befehlsprobe baum(Instant jetzt) {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        return new Befehlsprobe(List.of(new WettbewerbBefehle(buch,
                Sprachprobe.spracheAusDemJar(), () -> jetzt,
                // Die Namensnaht als KARTE und nicht Spielername::von: Jener Aufruf
                // braucht einen Server, und die Rangliste nennt Namen.
                wer -> wer.equals(ANNA) ? "Anna" : "Bert").wettbewerb()));
    }

    /** Die LETZTE Zeile, die dieser Spieler gelesen hat - chatBild() leert nicht. */
    private static String letzte(Spielerprobe wer) {
        List<String> alles = wer.chatBild();
        assertFalse(alles.isEmpty(), "der Spieler hat gar nichts gelesen");
        return alles.get(alles.size() - 1);
    }

    @Test
    @DisplayName("1 - /wettbewerb im offenen Fenster zeigt Stand, Hinweis und Rangliste")
    void standImFenster() throws CommandSyntaxException {
        assertEquals(1, baum(IM_FENSTER).tippe("wettbewerb", anna));

        assertEquals(List.of(
                "&#c6a15bDer Angelwettbewerb von Altfurt",
                "&#73bf96Der Wettbewerb laeuft bis 19:00. &#a5b4bfIm Topf: "
                        + "&#c6a15b0 Mark&#a5b4bf von 0 Teilnehmern.",
                "&#6e7a82Mitmachen mit /wettbewerb anmelden (250 Mark)",
                "&#edf1f3Die Rangliste",
                "&#6e7a82  Noch ist niemand dabei."),
                anna.chatBild());
    }

    @Test
    @DisplayName("2 - vor dem Fenster steht der naechste Termin da")
    void standVorDemFenster() throws CommandSyntaxException {
        assertEquals(1, baum(VOR_DEM_FENSTER).tippe("wettbewerb", anna));

        assertTrue(anna.chatBild().stream()
                        .anyMatch(z -> z.contains("Der naechste beginnt um 18:00")),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("3 - laeuft er nur an einzelnen Tagen, nennt der Termin die TAGE")
    void terminMitTagen() throws CommandSyntaxException {
        // Sonst wartet jemand am Montag auf einen Wettbewerb, der erst am Freitag ist.
        stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitWettbewerb(werte(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));

        baum(VOR_DEM_FENSTER).tippe("wettbewerb", anna);

        assertTrue(anna.chatBild().stream()
                        .anyMatch(z -> z.contains("18:00 (Freitag/Sonntag)")),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("4 - /wettbewerb anmelden zahlt und nennt den Topf")
    void anmelden() throws CommandSyntaxException {
        assertEquals(1, baum(IM_FENSTER).tippe("wettbewerb anmelden", anna));

        assertEquals("&#73bf96Du bist dabei - 250 Mark gezahlt."
                + " &#a5b4bfIm Topf: &#c6a15b250 Mark", letzte(anna));
        assertEquals(750L, kasse.guthaben(ANNA));
        assertTrue(ablage.zeile(ANNA, SONNTAG).isPresent());
    }

    @Test
    @DisplayName("5 - die zweite Anmeldung sagt es und kostet nichts")
    void zweiteAnmeldung() throws CommandSyntaxException {
        Befehlsprobe baum = baum(IM_FENSTER);
        baum.tippe("wettbewerb anmelden", anna);
        long nachher = kasse.guthaben(ANNA);

        baum.tippe("wettbewerb anmelden", anna);

        assertEquals("&#e3c57eDu bist schon angemeldet.", letzte(anna));
        assertEquals(nachher, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("6 - ohne offenes Fenster nennt die Absage den naechsten Termin")
    void anmeldenOhneFenster() throws CommandSyntaxException {
        assertEquals(1, baum(VOR_DEM_FENSTER).tippe("wettbewerb anmelden", anna));

        assertEquals("&#e3c57eGerade laeuft kein Wettbewerb. Der naechste beginnt um"
                + " 18:00.", letzte(anna));
        assertEquals(1_000L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("7 - bei zu wenig Geld stehen BEIDE Zahlen da")
    void zuWenigGeld() throws CommandSyntaxException {
        SpeicherKasse arm = new SpeicherKasse(0L);
        arm.eroeffne(ANNA);
        arm.gutschreiben(ANNA, 100L, Buchungsgrund.BESTAND);
        kasse = arm;

        baum(IM_FENSTER).tippe("wettbewerb anmelden", anna);

        assertEquals("&#d9564dDas Startgeld ist 250 Mark - du hast 100 Mark.",
                letzte(anna));
        assertEquals(100L, arm.guthaben(ANNA));
    }

    @Test
    @DisplayName("8 - die Rangliste zeigt Namen und Punkte, der eigene Stand den Platz")
    void rangliste() throws CommandSyntaxException {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        buch.meldeAn(ANNA, IM_FENSTER);
        kasse.gutschreiben(BERT, 1_000L, Buchungsgrund.BESTAND);
        buch.meldeAn(BERT, IM_FENSTER);
        buch.zaehleFang(BERT, IM_FENSTER, "heart_of_the_sea");
        buch.zaehleFang(ANNA, IM_FENSTER, "cod");

        baum(IM_FENSTER).tippe("wettbewerb", anna);

        List<String> gelesen = anna.chatBild();
        assertTrue(gelesen.contains("&#a5b4bf  1. Bert - 100"), gelesen.toString());
        assertTrue(gelesen.contains("&#a5b4bf  2. Anna - 1"), gelesen.toString());
        assertTrue(gelesen.contains("&#a5b4bfDu hast 1 Punkt und liegst auf Platz 2."),
                gelesen.toString());
    }

    @Test
    @DisplayName("9 - ein ausgeschalteter Wettbewerb sagt es in BEIDEN Zweigen")
    void ausgeschaltet() throws CommandSyntaxException {
        stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitWettbewerb(werte(EnumSet.allOf(DayOfWeek.class)).mitAn(false))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        Befehlsprobe baum = baum(IM_FENSTER);

        baum.tippe("wettbewerb", anna);
        assertEquals("&#e3c57eIn Altfurt wird gerade nicht gewertet.", letzte(anna));

        baum.tippe("wettbewerb anmelden", anna);
        assertEquals("&#e3c57eIn Altfurt wird gerade nicht gewertet.", letzte(anna));
        assertEquals(1_000L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("10 - die KONSOLE sieht den Stand, kann sich aber nicht anmelden")
    void konsole() throws CommandSyntaxException {
        Befehlsprobe baum = baum(IM_FENSTER);

        assertEquals(1, baum.tippeAufDerKonsole("wettbewerb"));
        List<String> gelesen = baum.konsoleBild();
        assertTrue(gelesen.contains("&#c6a15bDer Angelwettbewerb von Altfurt"),
                gelesen.toString());
        assertFalse(gelesen.stream().anyMatch(z -> z.contains("Mitmachen mit")),
                "die Konsole hat kein Konto - ein Hinweis zum Mitmachen waere unsinnig: "
                        + gelesen);

        assertEquals(1, baum.tippeAufDerKonsole("wettbewerb anmelden"));
        assertTrue(baum.konsoleBild().stream()
                        .anyMatch(z -> z.contains("Von der Konsole geht das nicht")),
                baum.konsoleBild().toString());
    }

    @Test
    @DisplayName("11 - ohne das Recht geht der Befehl gar nicht")
    void ohneRecht() {
        Spielerprobe ohne = new Spielerprobe("Ohne", Spielerprobe.kennungAus(416));

        assertThrows(CommandSyntaxException.class,
                () -> baum(IM_FENSTER).tippe("wettbewerb", ohne));
    }

    @Test
    @DisplayName("12 - ein unbekannter Zweig endet nicht stumm")
    void unbekannterZweig() {
        assertThrows(CommandSyntaxException.class,
                () -> baum(IM_FENSTER).tippe("wettbewerb anmeld", anna));
    }

    @Test
    @DisplayName("13 - ohne seine vier Teile ist der Befehl nicht zu bauen")
    void pflichtangaben() {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        Sprache sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class,
                () -> new WettbewerbBefehle(null, sprache, () -> IM_FENSTER,
                        wer -> "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new WettbewerbBefehle(buch, null, () -> IM_FENSTER, wer -> "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new WettbewerbBefehle(buch, sprache, null, wer -> "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new WettbewerbBefehle(buch, sprache, () -> IM_FENSTER, null));
    }
}
