package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /beruf}.
 *
 * <p>Acht Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@code Beruftext} und
 * {@code BerufeBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {name}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs: Der Name eines Berufes steht NICHT hier.</b> Er hat
 * eine eigene Datei, {@link Berufsname}, und zwar weil er kein fester Schluessel ist -
 * welche Berufe es gibt, entscheidet die {@code config.yml}, und der Betreiber darf dort
 * einen hinzufuegen. Eine Konstante je Beruf waere ein Plugin, das man neu bauen muss, um
 * einen Beruf anzulegen. Jeder Satz hier, der {@code {name}} einsetzt, bekommt sein Wort
 * von dort.
 *
 * <p><b>Kein eigenes Wort fuer eine DAUER.</b> Die Sperrfrist wird mit
 * {@code Spielzeittext.dauer} geschrieben, also mit denselben vier Formen wie jede andere
 * Dauer dieses Plugins ({@code spielzeit.dauer.*}). Ein zweiter Satz "{stunden} Stunden"
 * waere eine zweite Rechnung fuer dieselbe Frage, und zwei Rechnungen laufen eines Tages
 * auseinander - dann liest ein Spieler an einer Stelle "2 h 05 min" und an der anderen
 * "2 Stunden" fuer dieselbe Frist.
 *
 * <p><b>Kein {@link Art#GEZAEHLT} und kein {@link Art#HANDBALKEN}</b>, und das ist
 * nachgezaehlt. Vier Saetze tragen eine Zahl, und keiner braucht dafuer zwei Formen: Die
 * Platzzeilen nennen zwei Zahlen ({@code {gehalten}} von {@code {erlaubt}}) und lesen
 * sich in beiden Faellen gleich, und die zwei Absagen zu vollen Plaetzen stellen die Zahl
 * in eine Klammer ("kein Platz mehr fuer einen Grundberuf (erlaubt: 2)") statt sie an ein
 * gezaehltes Hauptwort zu haengen. Letzteres war der erste Entwurf - "Du haeltst schon 1
 * Spezialisierungen" - und die Wahl stand dann zwischen einer erfundenen Einzahlform und
 * einem falschen Satz. Die Klammer loest beides, und die Datei bleibt bei einer Zeile je
 * Absage. {@code sendActionBar} benutzt {@code /beruf} nicht; ein Beruf wird im Chat
 * gewaehlt und nicht im Augenwinkel.
 *
 * <p><b>Marke {@code HALB IN KRAFT (Verdienst):}</b> Solange Teil E fehlt, sagt kein Satz
 * dieses Bereichs, was ein Beruf EINBRINGT. Das ist keine Luecke in der Datei, sondern
 * eine Luecke im Spiel - und solange sie besteht, muss die Datei es sagen, damit der
 * Betreiber nicht nach einer Zeile sucht, die es noch nicht gibt.
 */
enum BerufSatz implements Satz {

    // --- /beruf liste ----------------------------------------------------------

    /** Die Ueberschrift ueber der Liste aller Berufe. */
    LISTE_KOPF("beruf.liste.kopf", Art.SATZ),

    /** Ein Grundberuf, zu dem es keine Spezialisierung gibt. */
    LISTE_BERUF("beruf.liste.beruf", Art.SATZ, "name"),

    /**
     * Ein Grundberuf MIT seinen Spezialisierungen.
     *
     * <p>Zwei Saetze und nicht einer mit leerem Wert: Ein Grundberuf ohne Spezialisierung
     * bekaeme sonst einen Gedankenstrich und dahinter nichts, und der Betreiber koennte
     * das nicht abstellen, ohne den Satz fuer alle anderen zu verstuemmeln.
     */
    LISTE_BERUF_MIT("beruf.liste.beruf-mit", Art.SATZ, "name", "spezialisierungen"),

    /**
     * Was zwischen zwei Spezialisierungen steht.
     *
     * <p>{@link Satz#LEER_ERLAUBT}, wie die zwei Trenner der Weltenliste: Ein Trenner
     * sendet keine Zeile, sondern laesst zwei Namen aneinanderstossen. Wer ihn leer
     * laesst, bekommt "steinmetzjuwelier" - haesslich, aber seine Entscheidung und kein
     * verstummter Satz.
     */
    LISTE_TRENNER("beruf.liste.trenner", Art.WORT, Satz.LEER_ERLAUBT),

    /**
     * Es gibt gar keine Berufe.
     *
     * <p>Erreichbar bleibt dieser Satz nur fuer einen Server, der die Liste erst noch
     * fuellt: Ist sie leer, bleibt Freimarken aus - aber {@code /beruf liste} laeuft dann
     * auch nicht. Er steht trotzdem in der Datei, weil ein Satz, den das Plugin kennt,
     * dort stehen MUSS; und weil die Liste mit Teil E auch wieder kuerzer werden kann.
     */
    LISTE_LEER("beruf.liste.leer", Art.SATZ),

    /** Die Fusszeile der Liste: wie man einen waehlt. */
    LISTE_FUSS("beruf.liste.fuss", Art.SATZ),

    // --- /beruf stand ----------------------------------------------------------

    /** Die Ueberschrift ueber dem eigenen Stand. */
    STAND_KOPF("beruf.stand.kopf", Art.SATZ),

    /**
     * Er hat noch nie einen Beruf gewaehlt.
     *
     * <p>Nicht dasselbe wie "du haeltst gerade keinen": Wer alles abgelegt hat, hat eine
     * leere Liste UND eine laufende Sperrfrist, und der liest {@link #STAND_FRIST} dazu.
     */
    STAND_KEINER("beruf.stand.keiner", Art.SATZ),

    /** Ein gehaltener Grundberuf. */
    STAND_GRUNDBERUF("beruf.stand.grundberuf", Art.SATZ, "name"),

    /** Eine gehaltene Spezialisierung - mit dem Grundberuf, auf dem sie aufbaut. */
    STAND_SPEZIALISIERUNG("beruf.stand.spezialisierung", Art.SATZ, "name", "grundberuf"),

    /**
     * Wie viele Grundberufsplaetze belegt sind.
     *
     * <p>Je Rolle ein eigener Satz, nicht einer mit vier Zahlen: So kann der Betreiber die
     * zwei Zeilen umstellen, und so steht nirgends eine Zeile ueber Spezialisierungen bei
     * einem Server, der keine hat.
     */
    STAND_PLAETZE_GRUNDBERUFE("beruf.stand.plaetze.grundberufe", Art.SATZ, "gehalten",
            "erlaubt"),

    /** Dasselbe fuer die Spezialisierungen. */
    STAND_PLAETZE_SPEZIALISIERUNGEN("beruf.stand.plaetze.spezialisierungen", Art.SATZ,
            "gehalten", "erlaubt"),

    /**
     * Die Sperrfrist laeuft noch - mit der Restzeit.
     *
     * <p>Die Zahl kommt aus {@code Berufsregel.restfrist}, derselben Rechnung, die auch
     * die Absage benutzt. Zwei Rechnungen fuer dieselbe Frist liefen eines Tages
     * auseinander, und der Spieler las eine Zahl, nach der er trotzdem abgewiesen wird.
     */
    STAND_FRIST("beruf.stand.frist", Art.SATZ, "dauer"),

    /** Was ein Wechsel kostet - damit es nicht erst in der Absage steht. */
    STAND_GEBUEHR("beruf.stand.gebuehr", Art.SATZ, "betrag"),

    // --- Die zwei Zusagen ------------------------------------------------------

    /** Gewaehlt. Ohne Betrag: Waehlen ist immer gratis. */
    GEWAEHLT("beruf.gewaehlt", Art.SATZ, "name"),

    /** Abgelegt - mit dem Betrag, der dafuer gebucht wurde. */
    VERLASSEN("beruf.verlassen.getan", Art.SATZ, "name", "betrag"),

    /**
     * Die Rueckfrage VOR dem kostenpflichtigen Ablegen - wie bei {@code /parzelle aufgeben}.
     *
     * <p>{@code {name}} ist das WORT, {@code {schluessel}} das, was er tippen muss. Zwei
     * Werte und nicht einer: Der Satz enthaelt die Befehlszeile zum Abschreiben, und dort
     * steht der Schluessel aus der {@code config.yml}; im Text davor steht das Wort aus
     * dieser Datei. Mit nur einem Wert waere entweder die Befehlszeile falsch oder der
     * Text technisch.
     */
    VERLASSEN_WARNUNG("beruf.verlassen.warnung", Art.SATZ, "name", "schluessel", "betrag",
            "dauer"),

    // --- Die Absagen, eine je Ausgang des Urteils ------------------------------

    /**
     * Diesen Beruf gibt es nicht - mit der Eingabe, wie sie getippt wurde.
     *
     * <p>{@code {eingabe}} und nicht {@code {name}}: Hier gibt es kein Wort aus der
     * Sprachdatei, denn es gibt den Beruf nicht. Der Spieler soll lesen, was er getippt
     * hat.
     */
    ABSAGE_UNBEKANNT("beruf.absage.unbekannt", Art.SATZ, "eingabe"),

    /** Er haelt ihn schon. */
    ABSAGE_SCHON_GEHALTEN("beruf.absage.schon-gehalten", Art.SATZ, "name"),

    /** Er haelt ihn nicht - es gibt nichts abzulegen. */
    ABSAGE_NICHT_GEHALTEN("beruf.absage.nicht-gehalten", Art.SATZ, "name"),

    /** Alle Grundberufsplaetze sind besetzt - mit der Platzgrenze. */
    ABSAGE_GRUNDBERUFE_VOLL("beruf.absage.grundberufe-voll", Art.SATZ, "erlaubt"),

    /** Alle Spezialisierungsplaetze sind besetzt - mit deren eigener Grenze. */
    ABSAGE_SPEZIALISIERUNG_VOLL("beruf.absage.spezialisierung-voll", Art.SATZ, "erlaubt"),

    /**
     * Ihm fehlt das Recht, ueberhaupt eine Spezialisierung zu halten.
     *
     * <p>OHNE den Namen des Rechts: Ein Spieler kann mit {@code freimarken.beruf.*}
     * nichts anfangen, und wer ihm das Recht gibt, liest es in der Datei des
     * Rechteplugins. Die Rangwerbung aus {@code hinweise.rang-werbung} haengt der Satz
     * nicht selbst an - sie gehoert in den Satz, wenn der Betreiber sie dort will.
     */
    ABSAGE_OHNE_RECHT("beruf.absage.ohne-recht", Art.SATZ),

    /** Ihm fehlt der Grundberuf, den diese Spezialisierung voraussetzt - sein Wort. */
    ABSAGE_GRUNDBERUF_FEHLT("beruf.absage.grundberuf-fehlt", Art.SATZ, "name"),

    /**
     * Der Grundberuf bleibt, solange seine Spezialisierung steht - mit DEREN Wort.
     *
     * <p>{@code {name}} ist hier die Spezialisierung und nicht der Beruf, den er ablegen
     * wollte: Sie ist das, was er zuerst ablegen muss. Der Name des Grundberufes stuende
     * schon in seinem eigenen Befehl.
     */
    ABSAGE_TRAEGT_SPEZIALISIERUNG("beruf.absage.traegt-spezialisierung", Art.SATZ, "name"),

    /** Die Sperrfrist laeuft noch - dieselbe Zahl wie in {@link #STAND_FRIST}. */
    ABSAGE_SPERRFRIST("beruf.absage.sperrfrist", Art.SATZ, "dauer"),

    /** Das Geld reicht nicht - die Gebuehr und was er hat. */
    ABSAGE_GELD_FEHLT("beruf.absage.geld-fehlt", Art.SATZ, "betrag", "guthaben"),

    // --- Drei Absagen, die nicht aus dem Urteil kommen -------------------------

    /**
     * Die Konsole hat keinen Beruf.
     *
     * <p>Sie bekommt keine Absage, die wie ein unbekannter Befehl klingt, sondern den
     * Grund: Ein Beruf haengt an einem Spieler. Grau und nicht rot - es ist nichts kaputt.
     */
    ABSAGE_KEINE_KONSOLE("beruf.absage.keine-konsole", Art.SATZ),

    /**
     * Zwei Befehle in derselben Sekunde - der Stand, auf dem geurteilt wurde, gilt nicht
     * mehr.
     *
     * <p>Das ist die sichtbare Seite der Zusage "geschrieben wird gegen einen erwarteten
     * Stand": Die Ablage hat NICHTS getan, und der Spieler soll es noch einmal versuchen.
     * Ohne diesen Satz schwiege der Befehl - und ein Spieler, der nichts liest, haelt ihn
     * fuer kaputt.
     */
    ABSAGE_GLEICHZEITIG("beruf.absage.gleichzeitig", Art.SATZ),

    /**
     * Die Ablage antwortet nicht.
     *
     * <p>Wie {@code spielzeit.nicht-lesbar} und aus demselben Grund: Die Zahl fehlt, der
     * Server laeuft weiter, und der Spieler soll nicht raten, warum nichts kommt. Im
     * Protokoll steht daneben die Ausnahme mit der UUID.
     */
    ABSAGE_NICHT_LESBAR("beruf.absage.nicht-lesbar", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    BerufSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    BerufSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
        this.leerErlaubt = leerErlaubt;
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
        return leerErlaubt;
    }
}
