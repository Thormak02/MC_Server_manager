package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Wachen - Bauschutz, Ankunft und Portale.
 *
 * <p>Acht Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind
 * {@code Schutzwache}, {@code Ankunftswache} und {@code Portalwache}; umgezogen ist die
 * erste im ersten Zug, die zwei anderen im zweiten. Dazu kam eine vierte,
 * {@link Schutztext} - sie baut die vier Wortlaute des Bauschutzes ohne Server, weil
 * der Klasseninitialisierer der Wache nach {@code Bukkit.server} greift.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz sein
 * {@code {eigentuemer}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs ist {@link Art#HANDBALKEN}, und er wird hier zum
 * ersten Mal benutzt.</b> Die Schutzwache schreibt nicht in den Chat, sondern ueber den
 * Gegenstandsgurt - {@code sendActionBar} nimmt EIN Component und keine Liste. Darum
 * tragen die vier Saetze unter {@code schutz} diese Art und keinen {@link Art#SATZ}: Eine
 * Umbruchmarke darin ist beim Laden eine Klage mit Zeile ({@link Sprachliste}), und die
 * zweite Zeile verschwindet nicht stumm. Bis zum ersten Zug stand diese Zusage nur in der
 * Theorie da, denn keine Konstante trug die Art.
 *
 * <p><b>Die drei Saetze des zweiten Zuges sind dagegen Chatzeilen und also
 * {@link Art#SATZ}.</b> Beide Wachen senden mit {@code sendMessage}, dort ist eine Liste
 * moeglich - und {@link #PORTAL_KEIN_AUSGANG} nutzt das: Er traegt eine Umbruchmarke und
 * ist damit der erste Satz dieses Bereichs mit zwei Zeilen.
 *
 * <p><b>Vier Saetze und nicht einer mit einem Wortstueck.</b> Beim Bauschutz waehlt eine
 * einzige Sendestelle zwischen ihnen ({@code Schutzwache.grund}), und sie unterscheiden
 * sich nicht bloss in einem Wort: Drei sind gelb und sagen, wie es hier steht, der vierte
 * ist rot und nennt einen Menschen. Ein gemeinsamer Satz mit einem eingesetzten Wortstueck
 * koennte die Farbe nicht mitwaehlen, und der Betreiber koennte die vier Lagen nicht
 * einzeln umstellen.
 *
 * <p><b>Und umgekehrt zwei Saetze statt eines bei der Ankunft, aber EINER statt zweier am
 * Portal.</b> Dasselbe Maass, zweimal anders ausgefallen: Die zwei Saetze der
 * Ankunftswache haengen an zwei verschiedenen Ereignissen und gehen nie zusammen hinaus -
 * ein Schluessel waere ein Satz, der zwei Sachen sagt. Die zwei Zeilen am Portal gehen
 * immer zusammen hinaus, und nur darum stehen sie in einem.
 *
 * <p><b>Genau EIN Platzhalter, und er gehoert einem ANDEREN Menschen als dem Leser:</b>
 * {@code {eigentuemer}} in {@link #SCHUTZ_FREMDE_PARZELLE}. Dieselbe Fehlerklasse wie bei
 * {@code AnfragenBefehle} und {@code /tpaccept} - vertauscht liest ein Besucher seinen
 * EIGENEN Namen und haelt die Parzelle fuer seine. Gegen den Tausch steht
 * {@code GrenzeTest} 31r: Es paart jede Wache mit dem Satz, den sie waehlt, und liest
 * mit, dass der Wert aus {@code eigentuemer()} kommt.
 *
 * <p>Kein {@link Art#GEZAEHLT}, kein {@link Art#WORT} und kein {@link Art#MUSTER}: In
 * diesem Zug gibt es keine Zahl, kein eingesetztes Wortstueck und kein Datum.
 *
 * <p><b>Einzug gibt es hier nicht, einen Umbruch genau einmal.</b> Gemessen: Keine der
 * sieben Zeilen beginnt mit einem Leerzeichen, und eine Umbruchmarke ist in einem
 * Handbalken ueberhaupt verboten - sie steht allein in {@link #PORTAL_KEIN_AUSGANG}. Was
 * dieser Bereich dem Betreiber gibt, sind Worte und Farbe, bei
 * {@link #SCHUTZ_FREMDE_PARZELLE} auch der Farbwechsel MITTEN im Satz und am Portal der
 * Zeilenbau.
 */
enum WacheSatz implements Satz {

    // --- Die Schutzwache: der Handbalken, wenn eine Hand abgewiesen wird -------

    /**
     * Er steht auf Weg oder Mauer - die gehoeren niemandem.
     *
     * <p>Keine Absage an eine Person, sondern eine Auskunft ueber den Ort: Darum gelb und
     * nicht rot. Gesendet hoechstens einmal je zwei Sekunden und Spieler
     * ({@code Schutzwache.MELDEPAUSE_MS}) - ohne diese Drossel wird aus Abbauhalten ein
     * Regen von Handbalken.
     */
    SCHUTZ_WEG_UND_MAUER("wache.schutz.weg-und-mauer", Art.HANDBALKEN),

    /**
     * Die Parzelle ist frei - mit dem Befehl, der sie holt.
     *
     * <p>Der einzige der vier Saetze, der einen Befehl nennt. Teilt die Woerter
     * {@code /parzelle nehmen} mit {@code parzelle.bescheid.noch-frei}: Wer den Befehl
     * umbenennt, aendert beide Zeilen.
     */
    SCHUTZ_PARZELLE_FREI("wache.schutz.parzelle-frei", Art.HANDBALKEN),

    /**
     * Die Stelle ist Bauland im Raster, liegt aber ausserhalb der Stadtgrenze.
     *
     * <p>Beginnt mit denselben 25 Zeichen wie {@code parzelle.nehmen.ausserhalb}, ist
     * aber ein anderer Fall und eine andere Farbe: Dort hat jemand
     * {@code /parzelle nehmen} getippt und bekommt die Grenzen genannt, hier hat er bloss
     * eine Hand gehoben.
     */
    SCHUTZ_AUSSERHALB("wache.schutz.ausserhalb", Art.HANDBALKEN),

    /**
     * Die Parzelle gehoert einem anderen Menschen - und der wird genannt.
     *
     * <p><b>Der erste {@link Art#HANDBALKEN} mit einem Farbwechsel MITTEN im Satz</b>, und
     * nicht der erste eingebettete Farbtag ueberhaupt - mehrere Zeilen unter
     * {@code parzelle} tragen schon einen, etwa {@code parzelle.nehmen.schon-vergeben}.
     * Rot ist der Kopf, weiss der
     * Name, und das Leerzeichen dazwischen gehoert zum ROTEN Stueck. Gemessen: Steht es
     * hinter dem Wechsel, ist das Bild im Spiel nicht zu unterscheiden, die
     * Alt-Serialisierung schon - {@code BildGleichTest} 11 faengt also auch diesen
     * Zeichentausch.
     *
     * <p>{@code {eigentuemer}} ist ein Name aus dem Benutzerzwischenspeicher. Ist er dort
     * nicht zu finden, setzt das Programm die ersten acht Zeichen der UUID ein; einen
     * eigenen Wortlaut dafuer gibt es nicht, weil es ihn vor dem Umzug auch nicht gab.
     */
    SCHUTZ_FREMDE_PARZELLE("wache.schutz.fremde-parzelle", Art.HANDBALKEN, "eigentuemer"),

    /**
     * Der Laden eines anderen - der Handbalken des Ladenschutzes [Teil C].
     *
     * <p>ZWEI Werte, und der Name der Einheit ist der wichtigere: In einer Markthalle
     * stehen die Staende nebeneinander, und ein Besucher soll lesen, an welchem er steht.
     * "Das ist der Laden von Anna" allein liesse ihn raten, ob er gerade an halle-3 oder
     * halle-4 greift.
     */
    SCHUTZ_FREMDER_LADEN("wache.schutz.fremder-laden", Art.HANDBALKEN, "laden",
            "mieter"),

    /**
     * Ein Stand, den niemand gemietet hat.
     *
     * <p>Hier darf AUCH niemand bauen, und das ist die Entscheidung, die dieser Satz
     * tragen muss: Eine freie Einheit gehoert niemandem und damit nicht jedem. Der Satz
     * nennt darum den Weg - mieten -, sonst liest ein Spieler eine Absage ohne Ausweg.
     */
    SCHUTZ_LADEN_FREI("wache.schutz.laden-frei", Art.HANDBALKEN),

    /**
     * Die Mietvertraege sind gerade nicht nachsehbar.
     *
     * <p>Ein EIGENER Satz und nicht "der Laden ist frei": Bei einer stummen Datenbank
     * weiss niemand, wem der Stand gehoert, und beide anderen Saetze waeren eine
     * Behauptung. Ein Spieler, der das liest, weiss, dass er es spaeter noch einmal
     * versuchen kann - und ein Betreiber, der es gemeldet bekommt, sieht im Protokoll
     * nach.
     */
    SCHUTZ_LADEN_GESTOERT("wache.schutz.laden-gestoert", Art.HANDBALKEN),

    // --- Die Ankunftswache: zwei Anlaesse, zwei Chatzeilen ---------------------

    /**
     * Ein Bett oder ein Anker hat KEINEN Spawnpunkt gesetzt.
     *
     * <p>Der einzige Hinweis an dieser Stelle, und darum darf er nicht leer sein: Der
     * Zweig schaltet die Vanilla-Meldung ausdruecklich ab, weil sie hier eine Luege
     * waere. Ohne diesen Satz tut ein Bett scheinbar nichts.
     *
     * <p>Ein {@link Art#SATZ} und kein Handbalken: Das ist eine CHATZEILE. Gemessen
     * ergibt sie genau eine, obwohl der Wortlaut im Quelltext ueber drei Zeilen mit
     * {@code +} zusammengesetzt war - ein Umbruch im Quelltext ist keiner im Chat.
     * Festgehalten in {@code SendewegTest} 33ak.
     */
    ANKUNFT_KEIN_SPAWNPUNKT("wache.ankunft.kein-spawnpunkt", Art.SATZ),

    /**
     * Die betretene Welt ist Abenteuer - was dort gilt und wo gebaut wird.
     *
     * <p>Nur in DIESE Richtung: Der Weg zurueck nach Ueberleben sagt nichts, weil dort
     * nichts verboten wird. Ein zweiter Satz dafuer gab es vor dem Umzug nicht, und ein
     * Umzug erfindet keinen.
     *
     * <p>Derselbe Empfaengertyp, dieselbe Farbe und dieselbe Datei wie
     * {@link #ANKUNFT_KEIN_SPAWNPUNKT} - darum sind die zwei vertauschbar, und darum
     * paart {@code GrenzeTest} 31s jeden Anlass mit dem Satz, den er waehlt.
     */
    ANKUNFT_ABENTEUER("wache.ankunft.abenteuer", Art.SATZ),

    // --- Die Portalwache: eine Absage aus zwei Chatzeilen ----------------------

    /**
     * Ein selbstgebautes Nether- oder Endportal fuehrt nirgends hin.
     *
     * <p><b>ZWEI Chatzeilen in EINEM Schluessel</b>, getrennt von einer Umbruchmarke:
     * rot die Absage, grau der Weg darunter. Sie gehen immer zusammen hinaus - zwischen
     * den zwei Sendungen stand kein {@code if}, kein {@code return} und keine Schleife,
     * die Abklingzeit wird einmal davor gefragt. Dieselbe Abwaegung wie bei
     * {@code ReiseSatz.WARP_KEIN_ZIEL}, und derselbe Preis: Der Betreiber kann die zwei
     * Zeilen nicht mehr einzeln abschalten, dafuer kann er sie umstellen und nicht
     * bloss umfaerben.
     *
     * <p><b>Der Schwanz " - oder durch ein Tor am Bundesmarkt." steht wortgleich auch in
     * {@code ReiseSatz.WARP_ZIELE}.</b> Bis zu diesem Zug kam er dort aus der Datei und
     * hier aus dem Programm, und darum trug {@code warp.ziele} die Marke
     * {@code HALB IN KRAFT (Torhinweis):}; mit diesem Zug ist sie weg. Zwei Blaetter mit
     * demselben Schwanz und zwei verschiedenen Anfaengen bleiben zwei - der Betreiber
     * darf sie auseinanderentwickeln, und keine Probe haelt sie mehr gleich.
     */
    PORTAL_KEIN_AUSGANG("wache.portal.kein-ausgang", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    WacheSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    /**
     * Keiner dieser Saetze darf leer sein.
     *
     * <p>Kein Konstruktor mit einem Wahrheitswert, anders als in {@link ReiseSatz}: Dort
     * gibt es zwei Trenner, die als WERT in einen anderen Satz gehen und darum leer
     * bleiben duerfen. Hier ist jeder Satz eine eigene Sendung - leer bliebe der
     * Handbalken dunkel, und der Spieler saehe nur, dass seine Hand nichts tut.
     *
     * <p>Fuer {@link #ANKUNFT_KEIN_SPAWNPUNKT} ist das noch mehr als die Hausregel: Der
     * Zweig schaltet die Vanilla-Meldung ausdruecklich ab, dieser Satz ist also der
     * EINZIGE Hinweis. Leer gelassen, legte sich ein Spieler schlafen und bekaeme eine
     * leere Chatzeile - und beim naechsten Tod eine Ueberraschung.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
