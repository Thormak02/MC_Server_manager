package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.SpeicherTeam;
import de.freimarken.kern.Teambuch;
import de.freimarken.kern.Teamwerte;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /team} GETIPPT - die Liste, die Vergabe und jede Absage.
 *
 * <p><b>Eine Zahltafel sagt nichts darueber, ob ein Zweig antwortet.</b> Genau das ist
 * der Zweck dieser Datei: Ohne sie waere {@code /team zeigen} eine Zeile, die uebersetzt
 * und angemeldet ist - und die im Spiel in Brigadiers englischem "Unknown or incomplete
 * command" endet, weil ihr eigenes {@code executes} fehlt. Dieses Projekt hat das schon
 * zweimal gemessen, zuletzt bei {@code /lotterie ziehen}.
 *
 * <p><b>Getippt wird auf der KONSOLE</b>, und damit faellt der Rechteriegel weg: Sie darf
 * alles, und das ist Absicht (siehe {@code Teambuch.gib}). Was der Riegel tut, prueft
 * {@code TeambuchTest} eine Schicht tiefer - mit Rechtemengen, ohne Server und ohne
 * Spieler. Hier wird gemessen, was der BEFEHL daraus macht: welche Zeile der Betreiber
 * liest und was danach in der Ablage steht.
 *
 * <p>Die ZEIT und die ZONE kommen von aussen, festgenagelt: Eine Probe mit
 * {@code Instant.now()} nennte in ihrer Auskunft jeden Tag ein anderes Datum.
 */
@DisplayName("/team getippt")
class TeamBefehlTest {

    private static final UUID ANNA = Spielerprobe.kennungAus(701);

    private static final UUID BERT = Spielerprobe.kennungAus(702);

    private static final String ANNA_NAME = "Anna";

    private static final String BERT_NAME = "Bert";

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 11:00 UTC - in Berlin ist das 12:00, und genau darin steckt die Zonenprobe. */
    private static final Instant MITTAGS = Instant.parse("2026-11-24T11:00:00Z");

    /**
     * Die zwei Namen, die dieser Server kennt - die Naht statt {@code Bukkit}.
     *
     * <p>{@code getOfflinePlayer} erfindet fuer einen unbekannten Namen eine frische
     * Kennung, und dann liefe die Absage "kennt dieser Server nicht" nie. Genau deshalb
     * ist die Namenssuche eine Naht und kein statischer Aufruf.
     */
    private static final Map<String, UUID> BEKANNT = new LinkedHashMap<>(Map.of(
            ANNA_NAME, ANNA, BERT_NAME, BERT));

    private SpeicherTeam ablage;
    private Sprache sprache;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        ablage = new SpeicherTeam();
        sprache = Sprachprobe.spracheAusDemJar();
    }

    @Test
    @DisplayName("1 - /team nennt die Ueberschrift und sagt, dass niemand im Team ist")
    void leeresTeam() throws CommandSyntaxException {
        List<String> zeilen = tippe("team");

        assertEquals(2, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Das Team des Freimarkenbunds"), zeilen.get(0));
        // Der Hinweis auf /team gib gehoert dazu: Eine leere Liste ohne ihn sieht aus wie
        // ein Befehl, der nicht funktioniert.
        assertTrue(zeilen.get(1).contains("/team gib"), zeilen.get(1));
    }

    @Test
    @DisplayName("2 - /team rollen nennt jede Rolle mit der ZAHL ihrer Rechte")
    void rollenListe() throws CommandSyntaxException {
        List<String> zeilen = tippe("team rollen");

        // Fuenf Rollen plus Ueberschrift. Die Namen kommen aus der ausgelieferten
        // Sprachdatei und nicht aus dem Programm - stuende hier "moderator" klein, waere
        // die Zeile team.rolle.moderator nicht gefunden worden.
        assertEquals(6, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("Helfer"), zeilen.get(1));
        // DREI seit Teil C: tpa, sofort, meldungen. Die Zahl steht hier und nicht
        // als Teamwerte.VORGABE...rechte().size() - eine Erwartung aus der
        // gepruefen Quelle faengt kein Vertauschen der Zeilen.
        assertTrue(zeilen.get(1).contains("3 Rechte"), zeilen.get(1));
        assertTrue(zeilen.get(3).contains("Verwalter"), zeilen.get(3));
        // SECHZEHN seit Teil B: die zehn von vorher plus strafen.stumm,
        // .rauswurf, .bann, .aufheben, .dauerhaft und .team. Die letzten zwei sind
        // die, die den Verwalter vom Moderator trennen (Plan 12c.2) - und darum ist
        // diese Zahl die, an der man eine verrutschte Rolle merkt.
        assertTrue(zeilen.get(3).contains("16 Rechte"), zeilen.get(3));
    }

    @Test
    @DisplayName("3 - /team rollen sagt es, wenn dieser Server keine Rollen hat")
    void rollenListeOhneRollen() throws CommandSyntaxException {
        List<String> zeilen = tippe("team rollen", ohneRollen());

        assertEquals(2, zeilen.size(), zeilen.toString());
        // Und nennt die Stelle in der Datei: Wer "keine" liest, soll wissen, wo sie
        // stehen muessten.
        assertTrue(zeilen.get(1).contains(Teamwerte.PFAD_ROLLEN), zeilen.get(1));
    }

    @Test
    @DisplayName("4 - /team gib vergibt, und die Ablage haelt es")
    void gibVergibt() throws CommandSyntaxException {
        List<String> zeilen = tippe("team gib " + ANNA_NAME + " moderator");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("ist jetzt Moderator"), zeilen.get(0));
        // Die Antwort allein genuegt nicht: Eine Zusage ohne Zeile ist ein Moderator, der
        // beim naechsten Beitritt wieder keiner ist.
        assertEquals(List.of("moderator"), ablage.lies(ANNA).rollen());
        assertTrue(ablage.lies(ANNA).zeilen().get(0).vergeber().isEmpty(),
                "die Konsole hat keine UUID");
        assertEquals(MITTAGS, ablage.lies(ANNA).zeilen().get(0).seit(),
                "die Zeit kommt aus der eingespeisten Uhr und nicht aus der Ablage");
    }

    @Test
    @DisplayName("5 - /team zeigen nennt Rolle, Zeit und Vergeber")
    void zeigenNenntAlles() throws CommandSyntaxException {
        ablage.gib(ANNA, "moderator", MITTAGS, BERT);

        List<String> zeilen = tippe("team zeigen " + ANNA_NAME);

        assertEquals(2, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("Moderator"), zeilen.get(1));
        // 12:00 und nicht 11:00: Die Zone ist Europe/Berlin, der Zeitpunkt steht in UTC.
        // Ohne die Umrechnung lase der Betreiber eine Stunde daneben - genau der Fehler,
        // der in diesem Projekt am 10.11.2026 im Blockbuch gefunden wurde.
        assertTrue(zeilen.get(1).contains("24.11.2026 12:00"), zeilen.get(1));
        assertTrue(zeilen.get(1).contains(BERT_NAME), zeilen.get(1));
    }

    @Test
    @DisplayName("6 - /team zeigen nennt die Konsole mit ihrem Wort")
    void zeigenNenntDieKonsole() throws CommandSyntaxException {
        ablage.gib(ANNA, "moderator", MITTAGS, null);

        List<String> zeilen = tippe("team zeigen " + ANNA_NAME);

        // Und keine erfundene Null-UUID: Die stuende dort als Spieler, den es nicht gibt,
        // und jemand suchte ihn.
        assertTrue(zeilen.get(1).contains("der Konsole"), zeilen.get(1));
    }

    @Test
    @DisplayName("7 - /team zeigen WARNT bei einer Rolle, die die Datei nicht kennt")
    void zeigenWarntBeiWeggefallenerRolle() throws CommandSyntaxException {
        ablage.gib(ANNA, "hausmeister", MITTAGS, null);

        List<String> zeilen = tippe("team zeigen " + ANNA_NAME);

        // DIE ZEILE, DIE DEN BEFEHL RECHTFERTIGT: Ein Moderator, der nichts darf, und
        // eine Auskunft, die seine Rolle brav auflistet, ist das Fehlerbild, gegen das
        // dieser Satz gebaut ist.
        assertEquals(3, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(2).contains("hausmeister"), zeilen.get(2));
        assertTrue(zeilen.get(2).contains("/team nimm"),
                "und sagt, wie man sie wegbekommt: " + zeilen.get(2));
    }

    @Test
    @DisplayName("8 - /team nimm entzieht, und die Ablage ist danach leer")
    void nimmEntzieht() throws CommandSyntaxException {
        ablage.gib(ANNA, "moderator", MITTAGS, null);

        List<String> zeilen = tippe("team nimm " + ANNA_NAME + " moderator");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("nicht mehr Moderator"), zeilen.get(0));
        assertFalse(ablage.lies(ANNA).imTeam());
    }

    @Test
    @DisplayName("9 - /team nimm raeumt auch eine weggefallene Rolle weg - und sagt es")
    void nimmRaeumtWeggefalleneRolle() throws CommandSyntaxException {
        ablage.gib(ANNA, "hausmeister", MITTAGS, null);

        List<String> zeilen = tippe("team nimm " + ANNA_NAME + " hausmeister");

        // Ein EIGENER Satz: Dasselbe Ergebnis aus einem anderen Grund. Der Betreiber hat
        // einen Altbestand aufgeraeumt und soll das lesen - sonst sucht er die Rolle in
        // seiner Datei und findet sie nicht.
        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("nur noch in der Datenbank"), zeilen.get(0));
        assertFalse(ablage.lies(ANNA).imTeam());
    }

    @Test
    @DisplayName("10 - ein zweites Geben meldet HATTE_SCHON und aendert nichts")
    void zweitesGeben() throws CommandSyntaxException {
        ablage.gib(ANNA, "moderator", MITTAGS, null);

        List<String> zeilen = tippe("team gib " + ANNA_NAME + " moderator");

        assertTrue(zeilen.get(0).contains("ist schon Moderator"), zeilen.get(0));
        assertEquals(MITTAGS, ablage.lies(ANNA).zeilen().get(0).seit(),
                "und die erste Vergabezeit steht noch da");
    }

    @Test
    @DisplayName("11 - /team nimm ohne Rolle meldet HATTE_NICHT")
    void nimmOhneRolle() throws CommandSyntaxException {
        List<String> zeilen = tippe("team nimm " + ANNA_NAME + " moderator");

        assertTrue(zeilen.get(0).contains("ist nicht Moderator"), zeilen.get(0));
    }

    @Test
    @DisplayName("12 - eine unbekannte Rolle wird abgewiesen, mit der Liste dazu")
    void unbekannteRolle() throws CommandSyntaxException {
        List<String> zeilen = tippe("team gib " + ANNA_NAME + " moderaator");

        assertTrue(zeilen.get(0).contains("moderaator"), zeilen.get(0));
        // Die Liste gehoert dazu: Der haeufigste Fall ist ein Vertipper, und wer die
        // richtigen Namen gleich mitliest, tippt nicht noch einmal daneben.
        assertTrue(zeilen.get(0).contains("Moderator"), zeilen.get(0));
        assertFalse(ablage.lies(ANNA).imTeam());
    }

    @Test
    @DisplayName("13 - ein unbekannter Spieler wird abgewiesen, VOR der Rolle")
    void unbekannterSpieler() throws CommandSyntaxException {
        // Beides falsch: der Name UND die Rolle. Gelesen werden soll der NAME - jener ist
        // der Fehler, der sonst eine Rolle an einen Geisterspieler vergibt. Dieselbe
        // Reihenfolge wie bei Betreibergeld.buche, und aus demselben Grund.
        List<String> zeilen = tippe("team gib Niemand moderaator");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Niemand"), zeilen.get(0));
        assertTrue(zeilen.get(0).contains("kennt dieser Server nicht"), zeilen.get(0));
    }

    @Test
    @DisplayName("14 - ohne Rollen in der Datei sagt /team gib es und vergibt nichts")
    void gibOhneRollen() throws CommandSyntaxException {
        List<String> zeilen = tippe("team gib " + ANNA_NAME + " moderator",
                ohneRollen());

        assertTrue(zeilen.get(0).contains(Teamwerte.PFAD_ROLLEN), zeilen.get(0));
        assertFalse(ablage.lies(ANNA).imTeam());
    }

    @Test
    @DisplayName("15 - /team nennt jeden Menschen EINMAL, mit allen seinen Rollen")
    void listeNenntJedenEinmal() throws CommandSyntaxException {
        ablage.gib(ANNA, "moderator", MITTAGS, null);
        ablage.gib(ANNA, "baumeister", MITTAGS, null);
        ablage.gib(BERT, "helfer", MITTAGS, null);

        List<String> zeilen = tippe("team");

        // Ueberschrift plus ZWEI Zeilen - nicht drei. Ein Verwalter mit drei Rollen ist
        // eine Person und nicht drei.
        assertEquals(3, zeilen.size(), zeilen.toString());
        String ihre = zeilen.stream().filter(zeile -> zeile.contains(ANNA_NAME))
                .findFirst().orElseThrow();
        assertTrue(ihre.contains("Baumeister") && ihre.contains("Moderator"),
                "beide Rollen in EINER Zeile: " + ihre);
    }

    @Test
    @DisplayName("16 - jeder Zweig antwortet, auch ohne sein Argument")
    void jederZweigAntwortet() throws CommandSyntaxException {
        // OHNE das eigene executes je Literal endet jeder dieser fuenf in Brigadiers
        // englischem "Unknown or incomplete command" - eine rote Zeile fuer eine Lage,
        // in der der Betreiber nur ein Argument vergessen hat.
        assertFalse(tippe("team zeigen").isEmpty(), "team zeigen ohne Namen");
        assertFalse(tippe("team gib").isEmpty(), "team gib ohne alles");
        assertFalse(tippe("team gib " + ANNA_NAME).isEmpty(),
                "team gib mit Namen, ohne Rolle");
        assertFalse(tippe("team nimm").isEmpty(), "team nimm ohne alles");
        assertFalse(tippe("team nimm " + ANNA_NAME).isEmpty(),
                "team nimm mit Namen, ohne Rolle");
    }

    @Test
    @DisplayName("17 - die Rollen werden vervollstaendigt, und zwar die der DATEI")
    void vervollstaendigung() {
        Spielerprobe betreiber = new Spielerprobe("Chef", Spielerprobe.kennungAus(703),
                TeamBefehle.RECHT, TeamBefehle.RECHT_VERGEBEN);

        List<String> vorschlaege = baum(Teamwerte.VORGABE)
                .vorschlaege("team gib " + ANNA_NAME + " ", betreiber);

        // Die der Datei und nicht die des Spielers: Letzteres braeuchte einen
        // Datenbankzugriff im Vervollstaendigungsfaden, und der laeuft nebenlaeufig zum
        // Hauptfaden. Ein Vorschlagsgeber am falschen Knoten zeigt im Quelltext dasselbe
        // Bild wie einer am richtigen.
        //
        // ALPHABETISCH und nicht in der Reihenfolge der Datei - GEMESSEN am 25.11.2026:
        // Brigadier sortiert seine Vorschlaege selbst, und daran ist von hier nichts zu
        // aendern. Die Liste steht darum ausgeschrieben da und nicht als
        // Teamwerte.VORGABE.schluessel(): Eine Erwartung, die aus der gepruefen Quelle
        // kommt, faengt kein Vertauschen - und eine, die der Reihenfolge der Datei
        // folgte, waere rot, obwohl alles richtig ist.
        assertEquals(List.of("baumeister", "eventleiter", "helfer", "moderator",
                "verwalter"), vorschlaege);
        // Die MENGE ist dieselbe wie in der Datei - das ist die Zusage, die wirklich
        // dieser Klasse gehoert: Vorgeschlagen wird genau, was team.rollen nennt.
        assertEquals(new java.util.TreeSet<>(Teamwerte.VORGABE.schluessel()),
                new java.util.TreeSet<>(vorschlaege));
    }

    @Test
    @DisplayName("18 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe ohne = new Spielerprobe("Gast", Spielerprobe.kennungAus(704));

        // Brigadier unterscheidet "gibt es nicht" und "darf ich nicht" NICHT - beide
        // enden in derselben Ausnahme. Das ist die bekannte Unschoenheit, und fuer ein
        // Betreiberwerkzeug ist sie die richtige: Ein Spieler soll nicht einmal erfahren,
        // dass es den Befehl gibt.
        assertThrows(CommandSyntaxException.class,
                () -> baum(Teamwerte.VORGABE).tippe("team", ohne));
    }

    @Test
    @DisplayName("19 - das Lese-Recht allein oeffnet gib und nimm NICHT")
    void leseRechtOeffnetNichtDasVergeben() throws CommandSyntaxException {
        Spielerprobe nurLesen = new Spielerprobe("Helfer",
                Spielerprobe.kennungAus(705), TeamBefehle.RECHT);
        Befehlsprobe baum = baum(Teamwerte.VORGABE);

        // Die Liste darf er sehen - dafuer hat er das Recht.
        assertEquals(1, baum.tippe("team", nurLesen));
        // Vergeben nicht. ZWEI Rechte und nicht eines: Das Ansehen ist eine Auskunft, das
        // Vergeben ein Eingriff - dieselbe Trennung wie bei /laden und /laden mieten.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("team gib " + ANNA_NAME + " moderator", nurLesen));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("team nimm " + ANNA_NAME + " moderator", nurLesen));
    }

    @Test
    @DisplayName("20 - ohne Buch, Sprache, Naht, Uhr, Zone oder Protokoll kein Befehl")
    void riegel() {
        Teambuch buch = new Teambuch(ablage, () -> Teamwerte.VORGABE);
        TeamBefehle.Namenssuche suche = BEKANNT::get;
        TeamBefehle.Namensgeber namen = wer -> ANNA_NAME;
        TeamBefehle.Uhr uhr = () -> MITTAGS;
        Logger stumm = Logger.getLogger("TeamBefehlRiegel");

        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(null, sprache, suche, namen, uhr, () -> BERLIN,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, null, suche, namen, uhr, () -> BERLIN,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, sprache, null, namen, uhr, () -> BERLIN,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, sprache, suche, null, uhr, () -> BERLIN,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, sprache, suche, namen, null, () -> BERLIN,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, sprache, suche, namen, uhr, null, stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamBefehle(buch, sprache, suche, namen, uhr, () -> BERLIN,
                        null));
    }

    /** Ein Server ohne Teamrollen - der Aus-Zustand, und er braucht keinen Schalter. */
    private static Teamwerte ohneRollen() {
        return Teamwerte.VORGABE.mitRollen(List.of());
    }

    /** Tippt mit der Vorgabe des Programms als Rollenliste. */
    private List<String> tippe(String eingabe) throws CommandSyntaxException {
        return tippe(eingabe, Teamwerte.VORGABE);
    }

    /**
     * Tippt den Befehl als KONSOLE und gibt zurueck, was sie liest.
     *
     * <p>Die Konsole und kein Spieler: Ein Spieler braeuchte
     * {@code getEffectivePermissions}, und das verlangt angemeldete Rechte auf einem
     * laufenden Server. Was der Rechteriegel entscheidet, prueft {@code TeambuchTest} -
     * dort ist die Rechtemenge ein Parameter und kein Serverzustand.
     */
    private List<String> tippe(String eingabe, Teamwerte rollen)
            throws CommandSyntaxException {
        Befehlsprobe baum = baum(rollen);
        baum.tippeAufDerKonsole(eingabe);
        return baum.konsoleBild();
    }

    private Befehlsprobe baum(Teamwerte rollen) {
        return new Befehlsprobe(List.of(new TeamBefehle(
                new Teambuch(ablage, () -> rollen), sprache,
                BEKANNT::get, TeamBefehlTest::nameVon, () -> MITTAGS, () -> BERLIN,
                Logger.getLogger("TeamBefehlProbe-" + UUID.randomUUID())).team()));
    }

    /**
     * Der Rueckweg von der Kennung zum Namen - die zweite Naht.
     *
     * <p>Eine unbekannte Kennung gibt ihre ersten Zeichen, wie
     * {@link Spielername#von} im Spiel. Damit faellt keine Zeile aus, nur weil ein
     * Spieler umbenannt wurde.
     */
    private static String nameVon(UUID wer) {
        for (Map.Entry<String, UUID> eintrag : BEKANNT.entrySet()) {
            if (eintrag.getValue().equals(wer)) {
                return eintrag.getKey();
            }
        }
        return wer.toString().substring(0, Spielername.ZEICHEN_DER_KENNUNG);
    }
}
