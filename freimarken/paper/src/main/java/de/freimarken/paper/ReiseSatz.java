package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze des Reisens - {@code /warp}, {@code /zurueck} und die Aufwaermzeit.
 *
 * <p>Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die vier Dateien dieses Schrittes sind
 * {@code Reise}, {@code Aufwaermzeit}, {@code RueckwegBefehle} und {@code WarpBefehle},
 * und alle vier sind umgezogen.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz sein
 * {@code {ziel}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs sind DREI Baeume in EINER Aufzaehlung.</b> Der
 * Hauptteil steht unter {@code reise.} und nicht unter dem Befehl, den der Spieler tippt -
 * anders als bei {@code anfrage.} oder {@code spielzeit.}, und mit Absicht: Dieselbe Reise
 * wird von FUENF Befehlen ausgeloest ({@code /warp}, das Tor am Bundesmarkt,
 * {@code /zurueck}, {@code /tpaccept} und {@code /freimarken parzelle}), und jeder von
 * ihnen laeuft durch {@link Reise} und {@link Aufwaermzeit}. Ein Baum je Befehl waere
 * derselbe Satz an fuenf Orten; der Betreiber aenderte einen und wunderte sich, warum die
 * anderen vier weiter das Alte sagen.
 *
 * <p>Die EIGENEN Saetze von {@code /warp} und {@code /zurueck} - ihre Absagen und
 * Auskuenfte - stehen darum unter {@code warp.} und {@code zurueck.}: Sie liest nur, wer
 * genau diesen Befehl tippt, und wer sie umformuliert, soll damit nicht die Absage des
 * Tores treffen. Dieselbe Aufzaehlung traegt sie trotzdem, weil es eine Datei je
 * Bauschritt gibt und nicht eine je Baum.
 *
 * <p><b>{@code {ziel}} ist EIN Name fuer EINEN Wert</b>, und er heisst darum nicht
 * {@code {welt}}. Sechs Saetze setzen ihn ein, und es ist ueberall derselbe Wert: Er
 * wandert von {@code Reise.fuehre} ueber {@code Aufwaermzeit.beginne} in
 * {@code Lauf.wohin()}. Gefuellt wird er fuenferlei - mit einem kleingeschriebenen
 * Weltnamen ({@code /warp}, Tor), mit einem SPIELERNAMEN ({@code /tpaccept}), mit
 * {@link #ZURUECK_LETZTER_ORT} ({@code /zurueck}) und mit der Aufschrift "Parzelle 3/-4"
 * ({@code /freimarken parzelle}). Welche dieser Fuellungen noch aus dem PROGRAMM kommt
 * und nicht aus der Datei, sagt die Marke "HALB IN KRAFT (Reiseziel)" im Kopf von
 * {@code reise} - und {@code VorlageTest} 10f laesst sie nicht stehen, wenn ihr Grund
 * wegfaellt. Nur {@link #SCHON_DA} und
 * {@link #KEIN_PLATZ_AM_ERSCHEINUNGSPUNKT} tragen garantiert einen Weltnamen, weil es sie
 * allein in der Welt-Fassung von {@code fuehre} gibt. Zwei Platzhalternamen waeren
 * trotzdem falsch: Sie erzeugten eine Unterscheidung, die das Programm nicht hat.
 *
 * <p><b>Genau EIN {@link Art#GEZAEHLT}</b>, naemlich {@link #ABKLINGZEIT}, und kein
 * {@link Art#HANDBALKEN}: Keine Sendestelle dieser vier Dateien benutzt
 * {@code sendActionBar} - das kommt erst mit {@code Schutzwache} und {@code WacheSatz}.
 * Kein {@link Art#MUSTER}: In diesen vier Dateien gibt es kein Datum.
 *
 * <p><b>DREI {@link Art#WORT}</b>, und alle drei im zweiten Zug: die zwei Trenner der
 * Weltenliste ({@link #WARP_ZIELE_TRENNER}, {@link #WARP_KEIN_ZIEL_TRENNER}) und die
 * Aufschrift, die {@code /zurueck} durchgibt ({@link #ZURUECK_LETZTER_ORT}). Die zwei
 * Trenner sind die einzigen Schluessel des ganzen Projekts, die LEER sein duerfen - sie
 * senden keine Zeile.
 *
 * <p><b>Einzug und Spalte gibt es nicht, Umbruchmarken schon.</b> Gemessen: Keine
 * Zeile dieser vier Dateien beginnt mit einem Leerzeichen - es gibt also keinen
 * Einzug zu uebernehmen. (Mit einem BUCHSTABEN beginnen sie nicht alle: Die dritte
 * Zeile von {@link #WARP_ZIELE} faengt mit einem Schraegstrich an.) Zwei Schluessel tragen dafuer Umbruchmarken -
 * {@link #WARP_ZIELE} drei Zeilen, {@link #WARP_KEIN_ZIEL} zwei -, weil ihre Zeilen immer
 * zusammen hinausgehen; dort zieht der ZEILENBAU mit um und nicht bloss die Farbe.
 */
enum ReiseSatz implements Satz {

    // --- Die zwei Absagen, die es nur in der WELT-Fassung gibt -----------------

    /**
     * Er ist schon in der Zielwelt - an den Reisenden, der nirgends hinkommt.
     *
     * <p>Grau und keine Warnfarbe: Es ist nichts kaputt. {@code {ziel}} ist hier
     * garantiert ein Weltname, denn die Wache dazu vergleicht Welten
     * ({@code Reise.fuehre(Player, World, String, Quader)}); die Ort-Fassung hat sie
     * nicht. "Du bist schon in Bert." kann es im Spiel also nicht geben.
     */
    SCHON_DA("reise.schon-da", Art.SATZ, "ziel"),

    /**
     * Am Erscheinungspunkt der Zielwelt ist kein Platz - ein BETREIBERFEHLER.
     *
     * <p>Der einzige Satz dieser zwei Dateien, der dem Spieler sagt, dass er selbst
     * nichts tun kann. Daneben bleibt im Programm ein {@code protokoll.warning} mit dem
     * Weltschluessel, den Spawn-Koordinaten und dem Rat zu {@code /setworldspawn}: Eine
     * Diagnose darf nicht von der Datei abhaengen, deren Zustand sie meldet.
     *
     * <p>Nicht zu verwechseln mit {@link #HINDERNIS_KEIN_PLATZ_MEHR}, mit dem er die
     * Woerter "kein Platz zum Stehen" teilt. Die Schuld liegt anders: Hier taugt der
     * Erscheinungspunkt einer ganzen WELT nicht, dort hat sich an einem gewollten,
     * EINZELNEN Ort etwas geaendert.
     */
    KEIN_PLATZ_AM_ERSCHEINUNGSPUNKT("reise.kein-platz-am-erscheinungspunkt", Art.SATZ,
            "ziel"),

    // --- Die fuenf Hindernisse: was gegen DIESEN Teleport spricht ---------------
    //
    // Fuenf Schluessel, obwohl jeder dieser Saetze an ZWEI Sendeorten hinausgeht:
    // Reise.pruefe laeuft zweimal - vor der Wartezeit und wenn es losgeht -, und beide
    // Aufrufer schicken ihr Ergebnis durch dieselbe reine Methode. Zehn Schluessel waeren
    // derselbe Satz an zwei Orten, und GrenzeTest 30 bliebe gruen, weil beide benutzt
    // wuerden - genau der Fall, den das Javadoc von AnfragenSatz.NIEMAND beschreibt.

    /**
     * Die Zielwelt ist entladen - an den Reisenden.
     *
     * <p>Das Wort "mehr" ist Absicht und unterscheidet diesen Satz von den zwei
     * darunter: Die Welt WAR da, als der Ort gemerkt wurde. Beim Abschreiben nicht
     * stillschweigend angleichen.
     */
    HINDERNIS_WELT_NICHT_GELADEN("reise.hindernis.welt-nicht-geladen", Art.SATZ),

    /** Er sitzt auf einem Reittier - an den Reisenden. */
    HINDERNIS_ZU_PFERD("reise.hindernis.zu-pferd", Art.SATZ),

    /**
     * Der Ort liegt ueber oder unter der Welthoehe - an den Reisenden.
     *
     * <p>EIN Satz fuer BEIDE Richtungen: Die Wache dazu ist eine Oder-Verzweigung, und
     * "zu tief" und "zu hoch" lesen heute zeichengleich dasselbe. Zwei Schluessel waeren
     * ein Satz, den das Spiel nicht hat.
     */
    HINDERNIS_AUSSERHALB_DER_WELTHOEHE("reise.hindernis.ausserhalb-der-welthoehe",
            Art.SATZ),

    /**
     * Der Ort liegt jenseits der Weltgrenze - an den Reisenden.
     *
     * <p>Unterscheidet sich von {@link #HINDERNIS_AUSSERHALB_DER_WELTHOEHE} in genau
     * EINEM Wort, und trotzdem ein eigener Schluessel mit dem GANZEN Satz. Ein
     * gemeinsamer Satz mit einem {@code {grund}}-Wortstueck liesse den Betreiber das Wort
     * faerben, aber den Satz nicht umstellen - so entschieden im Javadoc von
     * {@link AnfragenSatz#ABLEHNEN_ABGELEHNT}. Dass die zwei nicht gegeneinander
     * vertauscht sind, haelt {@code SendewegTest} 33ac; dass die richtige Wache den
     * richtigen Schluessel waehlt, {@code GrenzeTest} 31k.
     */
    HINDERNIS_AUSSERHALB_DER_WELTGRENZE("reise.hindernis.ausserhalb-der-weltgrenze",
            Art.SATZ),

    /**
     * Am Zielort steht kein tragfaehiger Boden mehr mit Platz darueber.
     *
     * <p>Abgesagt und nicht verschoben: Der Ort war gemeint. Darum ist dieser Satz eine
     * Nachricht und keine Entschuldigung - vergleiche
     * {@link #KEIN_PLATZ_AM_ERSCHEINUNGSPUNKT}.
     */
    HINDERNIS_KEIN_PLATZ_MEHR("reise.hindernis.kein-platz-mehr", Art.SATZ),

    // --- Was auf dem Weg noch gesagt wird --------------------------------------

    /**
     * Angeleinte Tiere bleiben zurueck - an den Reisenden, und KEINE Absage.
     *
     * <p>Die einzige gelbe Zeile dieses Bereichs, und die einzige, nach der es
     * weitergeht: Die Reise laeuft danach durch. Darum keine Warnfarbe und kein
     * Gegensatz-Satz - beim Weltwechsel ohne Tier und beim Tier ohne Weltwechsel sagt
     * niemand etwas.
     */
    LEINE_REISST("reise.leine-reisst", Art.SATZ),

    /**
     * Der Server hat den Teleport abgelehnt - an den Reisenden.
     *
     * <p>Gesendet aus dem Rueckruf von {@code teleportAsync}, also erst, nachdem der
     * Spieler "stehen bleiben" gelesen und auch stehen geblieben ist. Daneben bleibt ein
     * {@code protokoll.warning} im Programm.
     *
     * <p>Teilt mit {@link #ABBRUCH_BEWEGT} und {@link #ABBRUCH_SCHADEN} die Wendung
     * "Reise nach {ziel}", ist aber ein anderer Sachverhalt: Hier hat der SERVER
     * abgelehnt, dort hat der SPIELER die Wartezeit verloren.
     */
    NICHT_GEKLAPPT("reise.nicht-geklappt", Art.SATZ, "ziel"),

    /**
     * Zwei Reisen zu schnell hintereinander - an den Reisenden, mit der Restzeit.
     *
     * <p>{@link Art#GEZAEHLT}, weil bis zu diesem Schritt ein Ternaer im Programm
     * zwischen " Sekunde." und " Sekunden." entschied - beide Formen standen also schon
     * im Spiel, und es musste keine erfunden werden. Die Wahl trifft jetzt
     * {@code Sprachbuch.wortlaut} an {@code anzahl == 1L}.
     *
     * <p><b>Keine Nullform</b>, und das ist gerechnet und nicht geschaetzt: Der Rest
     * wird aufgerundet, und die Wache {@code rest <= 0} in
     * {@code Reise.zuSchnellHintereinander} gibt vorher {@code false} zurueck. Die Zahl
     * liegt damit zwischen 1 und 3 - {@code ZWISCHEN_ZWEI_REISEN} sind drei Sekunden.
     * {@code Art.GEZAEHLT} kennt ohnehin nur eins und viele.
     *
     * <p><b>Nicht zusammenlegen mit {@code parzelle.abklingzeit}</b>, mit dem er die
     * ersten 19 Zeichen teilt ("Einen Augenblick - "): Es sind zwei verschiedene
     * Abklingzeiten - die gegen das Nachladen von Chunks und die der Wegabschaetzung -,
     * und ausserdem ist die Schreibung verschieden (hier klein "- noch ", dort "Noch "
     * nach einem Punkt). Ein gemeinsamer Schluessel liesse einen Reisenden den Satz ueber
     * die Wegabschaetzung lesen.
     */
    ABKLINGZEIT("reise.abklingzeit", Art.GEZAEHLT, "anzahl"),

    // --- Die Aufwaermzeit ------------------------------------------------------

    /**
     * Die Wartezeit hat begonnen - an den Reisenden, mit Dauer und Ziel.
     *
     * <p>{@code {sekunden}} ist ein WERT und keine Zahl im Wort: Dieselbe Konstante
     * {@code Aufwaermzeit.DAUER} rechnet auch die Takte aus, auf die gewartet wird. Wer
     * die 3 als Wort in die Datei schreibt, spaltet eine Wahrheit in zwei - der Satz
     * luegt an dem Tag, an dem {@code DAUER} sich aendert, waehrend die Wartezeit
     * gehorcht.
     *
     * <p><b>Kein {@link Art#GEZAEHLT}</b>, und das ist das heutige Bild: "Nach 1
     * Sekunden" waere falsch, kommt aber nicht vor, weil {@code DAUER} fest auf drei
     * Sekunden steht. Eine erfundene Einzahlform waere eine Aenderung am Spielbild, die
     * niemand bestellt hat.
     */
    WARTEZEIT("reise.wartezeit", Art.SATZ, "sekunden", "ziel"),

    /**
     * Die Wartezeit ist abgebrochen, weil er seinen Bereich verlassen hat.
     *
     * <p>Das Gegenstueck ist {@link #ABBRUCH_SCHADEN}: dieselbe Sendestelle, ein anderes
     * Ereignis. Zwei Schluessel mit je GANZEM Satz und nicht einer mit einem
     * {@code {grund}}-Wortstueck - dieselbe Entscheidung wie bei den zwei Hindernissen,
     * die sich in einem Wort unterscheiden. Welcher Zweig welchen nimmt, haelt
     * {@code GrenzeTest} 31l.
     *
     * <p>Fuer das ABMELDEN gibt es absichtlich keinen dritten Schluessel: Dort wird ohne
     * Meldung abgebrochen, denn es liest sie niemand mehr.
     */
    ABBRUCH_BEWEGT("reise.abbruch.bewegt", Art.SATZ, "ziel"),

    /**
     * Die Wartezeit ist abgebrochen, weil er Schaden genommen hat.
     *
     * <p>Gegenstueck zu {@link #ABBRUCH_BEWEGT}. Dieser Zweig besteht eigens, weil
     * Bewegung allein eine Luecke liesse: Wer eingekeilt steht und geschlagen wird,
     * bewegt sich durch Rueckstoss nicht immer einen ganzen Block weit.
     */
    ABBRUCH_SCHADEN("reise.abbruch.schaden", Art.SATZ, "ziel"),

    // --- /warp: was NUR dieser Befehl sagt -------------------------------------
    //
    // Unter warp. und nicht unter reise., und das ist die Grenze, die der Kopf dieser
    // Datei zieht: Die Saetze darueber gehoeren allen fuenf Befehlen, die eine Reise
    // ausloesen, diese hier gehoeren diesem einen. Ein Betreiber, der /warp umformuliert,
    // soll damit nicht die Absage des Tores am Bundesmarkt treffen.

    /**
     * Die Auskunft auf das nackte {@code /warp} - DREI Chatzeilen in EINEM Schluessel.
     *
     * <p>Ueberschrift (gold), Weltenliste (aqua), Hinweis (grau), getrennt von zwei
     * Umbruchmarken. Drei Schluessel waeren bequemer und falsch: Die drei Zeilen gehen
     * immer zusammen hinaus, nie einzeln, und erst in EINEM Schluessel kann der Betreiber
     * sie auch UMSTELLEN statt bloss umfaerben - Worte, Farben und Aufbau. Dass drei
     * Farben in einem Schluessel wirken, haelt {@code Sprache.teile}: Geteilt wird am
     * BAUM, und jedes Stueck behaelt den Stil, der auf es wirkt.
     *
     * <p>Der Hinweis traegt {@code \<ziel>} als ZEICHEN - in der Datei mit zwei
     * Backslashes davor, sonst waere es beim Laden eine Klage mit Zeile und Spalte. Das
     * Wort darin ist der Name des Befehlsarguments; wird es im Programm umbenannt, gehoert
     * es in der Datei mitgeaendert. Dieselbe halbe Kopplung ist bei
     * {@link SpielzeitSatz#KEINE_KONSOLE} schon bewusst eingegangen worden.
     *
     * <p>{@code {liste}} ist die Weltenliste der {@code config.yml}, mit
     * {@link #WARP_ZIELE_TRENNER} zusammengefuegt. Ihre REIHENFOLGE bleibt im Programm
     * ({@code Warpverzeichnis}): Sie ist die der Datei, und das ist eine Zusage aus Plan
     * 4.3 und kein Aussehen.
     */
    WARP_ZIELE("warp.ziele", Art.SATZ, "liste"),

    /**
     * Was zwischen zwei Weltnamen in {@link #WARP_ZIELE} steht - heute zwei Leerzeichen.
     *
     * <p>Einer von GENAU ZWEI Schluesseln des Projekts, die leer sein DUERFEN, und der
     * Grund ist nicht Bequemlichkeit: Ein Trenner ist kein Satz. Er sendet keine
     * Chatzeile, er laesst zwei Eintraege aneinanderstossen - haesslich, aber nichts,
     * wovor eine Klage warnen muesste. Und ohne diese Erlaubnis koennte der heutige
     * Trenner gar nicht in der Datei stehen, denn zwei Leerzeichen sind fuer
     * {@code String.isBlank} leer: Die ausgelieferte Vorlage wuerde beim Laden werfen und
     * jede Probe mitreissen, die sie liest.
     *
     * <p>{@link Art#WORT} und darum ohne Farbtag: Der Trenner geht als WERT in den Satz
     * und ist dort ein Blatt.
     */
    WARP_ZIELE_TRENNER("warp.ziele-trenner", Art.WORT, Satz.LEER_ERLAUBT),

    /**
     * Die Absage auf einen Namen, den es nicht gibt - ZWEI Chatzeilen, rot und grau.
     *
     * <p>Ein Schluessel und nicht zwei, aus demselben Grund wie bei {@link #WARP_ZIELE}:
     * Die rote Absage und die graue Liste darunter gehen immer zusammen hinaus.
     *
     * <p><b>{@code {eingabe}} und nicht {@code {ziel}}, und das ist die ganze Sicherung
     * dieses Bereichs.</b> Hier steht der GETIPPTE Name; in {@link #WARP_WELT_FEHLT} steht
     * der AUFGELOESTE. Die zwei sind im Programm an einer Stelle gleichzeitig greifbar und
     * haben denselben Typ - vertauscht uebersetzt es anstandslos, und sichtbar waere es
     * allein an der Schreibweise. Zwei verschiedene Wertnamen machen daraus einen Bruch
     * des Platzhaltervertrags, den {@code VorlageTest} 3 sieht. {@code {ziel}} hiesse
     * ausserdem wie das Befehlsargument und damit wie BEIDE Werte.
     *
     * <p>{@code {eingabe}} kommt ROH vom Spieler und ist trotzdem gefahrlos: Der WORTLAUT
     * wird zuerst geparst, der Wert DANACH als Blatt eingesetzt
     * ({@code Sprache.ersetze}) - ein {@code <red>} darin steht dem Spieler woertlich im
     * Chat. Gegen eine zweite Chatzeile schuetzt nicht die Sprachmaschine, sondern
     * Brigadier: {@code StringArgumentType.word()} laesst keinen Umbruch durch.
     * {@code SendewegTest} 33ah haelt beide Haelften.
     *
     * <p>Beginnt mit {@code "Dorthin geht"} wie drei Zeilen unter
     * {@code reise.hindernis} - vier Ursachen, vier Schluessel, ein gemeinsamer
     * Satzanfang.
     */
    WARP_KEIN_ZIEL("warp.kein-ziel", Art.SATZ, "eingabe", "liste"),

    /**
     * Was zwischen zwei Weltnamen in {@link #WARP_KEIN_ZIEL} steht - Komma und Leerzeichen.
     *
     * <p>Ein eigener Schluessel und nicht derselbe wie {@link #WARP_ZIELE_TRENNER}, weil
     * die zwei heute VERSCHIEDEN sind. Das ist kein Versehen, das man beim Umzug
     * begradigt: Die Auskunft steht fuer sich und darf breit sein, diese Liste haengt an
     * einer roten Absage und soll eine Zeile bleiben. Ein gemeinsamer Trenner naehme dem
     * Betreiber genau diese Unterscheidung.
     */
    WARP_KEIN_ZIEL_TRENNER("warp.kein-ziel-trenner", Art.WORT, Satz.LEER_ERLAUBT),

    /**
     * Der Name steht in der {@code config.yml}, die Welt ist nicht entstanden.
     *
     * <p>{@code {welt}} ist der AUFGELOESTE Name - immer klein, immer aus der Datei. Warum
     * er nicht {@code {eingabe}} heisst, steht bei {@link #WARP_KEIN_ZIEL}.
     *
     * <p>Nicht zusammenlegen mit {@link #HINDERNIS_WELT_NICHT_GELADEN}: Fuer den Spieler
     * ist beides „das Ziel ist nicht zu haben", fuer den Betreiber sind es zwei Ursachen -
     * hier ein Eintrag ohne Welt (hat beim Start geklagt), dort eine Welt, die zur
     * Laufzeit entladen ist. An diesem Unterschied entscheidet er, ob er in die
     * {@code config.yml} oder in den laufenden Server schaut.
     */
    WARP_WELT_FEHLT("warp.welt-fehlt", Art.SATZ, "welt"),

    // --- /zurueck: eine Absage und ein Wortstueck ------------------------------

    /**
     * Es ist nichts gemerkt - an den Tipper, und sonst an niemanden.
     *
     * <p>EIN Satz gegen KEINEN Satz und nicht zwei Saetze gegeneinander: Hat der Spieler
     * einen gemerkten Ort, sagt dieser Befehl gar nichts, sondern reist. Darum ein
     * einzelner Schluessel und kein Paar.
     *
     * <p>Grau und nicht rot: Es ist nichts kaputt, der Spieler war nur noch nirgends.
     */
    ZURUECK_KEIN_ORT_GEMERKT("zurueck.kein-ort-gemerkt", Art.SATZ),

    /**
     * Die Aufschrift, die {@code /zurueck} als Reiseziel durchgibt.
     *
     * <p>KEIN gesendeter Satz, sondern das {@link Art#WORT}, das in FREMDE Saetze geht -
     * in die vier unter {@code reise.}, die {@code {ziel}} einsetzen. Es ist eine der
     * fuenf Fuellungen, die der Kopf dieser Datei aufzaehlt, und bis zu diesem Schritt
     * stand es als Zeichenkette im Programm.
     *
     * <p><b>Die Schiefheit zieht mit um und wird nicht begradigt.</b> Mit dieser Wendung
     * liest ein Spieler „Nach 3 Sekunden geht es nach deinen letzten Ort" - so ist es
     * ausgeliefert, und der Massstab dieses Schrittes ist zeichengleich. Neu ist allein,
     * dass der Betreiber sie jetzt beheben KANN: Vorher haette er dazu das Programm
     * gebraucht. {@code SendewegTest} 33ag haelt fest, dass es genau die Wendung ist, mit
     * der 33ad und 33ae die vier Saetze unter {@code reise.} pruefen.
     *
     * <p>Darf NICHT leer sein, anders als die zwei Trenner: Eine leere Aufschrift liesse
     * den Spieler „geht es nach  - bis dahin stehen bleiben" lesen, und das ist ein
     * kaputter Satz und kein enger Spalt.
     */
    ZURUECK_LETZTER_ORT("zurueck.letzter-ort", Art.WORT);

    private final String pfad;
    private final Art art;
    private final boolean darfLeerSein;
    private final Set<String> platzhalter;

    ReiseSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    ReiseSatz(String pfad, Art art, boolean darfLeerSein, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.darfLeerSein = darfLeerSein;
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

    @Override
    public boolean darfLeerSein() {
        return darfLeerSein;
    }
}
