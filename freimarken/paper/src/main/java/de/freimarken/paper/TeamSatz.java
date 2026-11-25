package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /team} - Teamrollen vergeben und nachsehen (Plan 12c.2).
 *
 * <p><b>Der NAME einer Rolle ist KEIN Satz dieser Aufzaehlung</b>, sondern ein
 * {@link Teamrolle} - eine offene Satzfamilie, wie bei {@link Berufsname}. Der Grund
 * steht dort: Welche Rollen es gibt, entscheidet die {@code config.yml}, und eine
 * Aufzaehlung im Programm koennte das nicht.
 *
 * <p><b>Je Bescheid des {@code Teambuch} ein Satz</b>, und das ist die Entscheidung
 * dieses Bereichs. Ein gemeinsamer "nicht moeglich"-Satz waere kuerzer gewesen, aber die
 * acht Bescheide sind acht verschiedene Lagen, und der Betreiber muss nach jeder an einer
 * anderen Stelle nachsehen: in der Datei, in seiner eigenen Rechteliste, im Protokoll
 * oder gar nicht. Eine Absage, die nicht sagt, wo man nachsieht, erzeugt eine Rueckfrage.
 *
 * <p><b>Alle Saetze dieses Bereichs sind BETREIBERTEXTE</b> und werden von keinem
 * gewoehnlichen Spieler gelesen - {@code /team} haengt an {@code freimarken.team}, und
 * das ist {@code default: op}. Sie stehen trotzdem in der Sprachdatei und nicht im
 * Programm: Ein Betreiber, der seine Rollen auf englische Namen umstellt, soll auch die
 * Zeilen darum herum umstellen koennen, und ein zweisprachiges Werkzeug ist ein halb
 * uebersetztes.
 */
enum TeamSatz implements Satz {

    /** Die Ueberschrift von {@code /team}. */
    KOPF("team.kopf", Art.SATZ),

    /**
     * Eine Zeile der Teamliste: ein Mensch mit allen seinen Rollen.
     *
     * <p>{@code rollen} ist die fertig zusammengesetzte Aufzaehlung ihrer Namen. Ein
     * Satz je Rolle waere bei einem Verwalter mit drei Rollen drei Zeilen fuer eine
     * Person - und die Liste soll zeigen, WER im Team ist, nicht wie oft.
     */
    ZEILE("team.zeile", Art.SATZ, "spieler", "rollen"),

    /** Niemand hat eine Rolle. */
    LEER("team.leer", Art.SATZ),

    /** Die Ueberschrift von {@code /team rollen}. */
    ROLLEN_KOPF("team.rollen.kopf", Art.SATZ),

    /**
     * Eine Zeile mit einer Rolle und der ZAHL ihrer Rechte.
     *
     * <p>Die Zahl und nicht die Namen - dieselbe Entscheidung wie in
     * {@code Teamliste.rechenprobe}: Eine Rolle hat bis zu einem Dutzend Rechte, und eine
     * Zeile, die alle nennt, liest niemand mehr. Wer es genau wissen will, liest die
     * Datei; {@link Art#GEZAEHLT}, denn ein Recht ist nicht zwei.
     */
    ROLLEN_ZEILE("team.rollen.zeile", Art.GEZAEHLT, "rolle", "rechte"),

    /**
     * Dieser Server hat keine Rollen eingetragen.
     *
     * <p>Derselbe Wortlaut waere fuer {@code /team rollen} und fuer eine Absage beim
     * Vergeben falsch: Dort ist es die Antwort auf eine Frage, hier die Absage auf eine
     * Tat. Darum zwei Saetze - siehe {@link #KEIN_TEAM}.
     */
    ROLLEN_LEER("team.rollen.leer", Art.SATZ),

    /** Die Ueberschrift von {@code /team zeigen}. */
    ZEIGEN_KOPF("team.zeigen.kopf", Art.SATZ, "spieler"),

    /**
     * Eine Rolle dieses Spielers, mit Vergabezeit und Vergeber.
     *
     * <p>{@code von} ist der Name des Vergebers oder das Wort aus
     * {@link #VON_KONSOLE_WORT}. Die ZEIT steht mit darin, weil die Frage, die diesen
     * Befehl ausloest, fast immer "seit wann" oder "von wem" ist.
     */
    ZEIGEN_ZEILE("team.zeigen.zeile", Art.SATZ, "rolle", "seit", "von"),

    /**
     * Das Wort, das anstelle des Vergebers steht, wenn es die Konsole war.
     *
     * <p>{@link Art#WORT} und kein Satz: Es wird in {@link #ZEIGEN_ZEILE} EINGESETZT,
     * wie {@code waehrung.betrag}. Darum traegt es keinen Farbtag - er ueberschriebe die
     * Farbe der Zeile, in der es landet.
     */
    VON_KONSOLE_WORT("team.zeigen.von-konsole-wort", Art.WORT),

    /**
     * Der Hinweis hinter einer Rolle, die die Datei nicht mehr kennt.
     *
     * <p>Eine EIGENE Zeile und nicht ein Wort in {@link #ZEIGEN_ZEILE}: Das ist die
     * Auskunft, die den Befehl ueberhaupt rechtfertigt. Ein Moderator, der nichts darf,
     * und ein {@code /team zeigen}, das seine Rolle brav auflistet, ist genau das
     * Fehlerbild, gegen das dieser Satz gebaut ist.
     */
    ZEIGEN_UNBEKANNT("team.zeigen.unbekannt", Art.SATZ, "rolle"),

    /** Dieser Spieler hat keine Rolle. */
    ZEIGEN_LEER("team.zeigen.leer", Art.SATZ, "spieler"),

    /** Die Rolle ist vergeben. */
    VERGEBEN("team.vergeben", Art.SATZ, "spieler", "rolle"),

    /** Er hatte sie schon - nichts ist geschehen. */
    HATTE_SCHON("team.hatte-schon", Art.SATZ, "spieler", "rolle"),

    /** Die Rolle ist entzogen. */
    GENOMMEN("team.genommen", Art.SATZ, "spieler", "rolle"),

    /** Er hatte sie nicht - nichts ist geschehen. */
    HATTE_NICHT("team.hatte-nicht", Art.SATZ, "spieler", "rolle"),

    /**
     * Entzogen, und die Datei kennt die Rolle nicht mehr.
     *
     * <p>Ein eigener Satz und nicht {@link #GENOMMEN}: Es ist dasselbe Ergebnis aus einem
     * anderen Grund, und der Betreiber soll lesen, dass er damit einen ALTBESTAND
     * aufgeraeumt hat. Sonst sucht er die Rolle in seiner Datei und findet sie nicht.
     */
    GENOMMEN_UNBEKANNT("team.genommen-unbekannt", Art.SATZ, "spieler", "rolle"),

    /**
     * Es gibt keine Rolle dieses Namens - mit der Liste der vorhandenen.
     *
     * <p>Die Liste gehoert dazu: Der haeufigste Fall ist ein Vertipper, und wer die
     * richtigen Namen gleich mitliest, tippt nicht noch einmal daneben.
     */
    UNBEKANNTE_ROLLE("team.unbekannte-rolle", Art.SATZ, "rolle", "rollen"),

    /**
     * Dem Vergebenden fehlen Rechte, die die Rolle gibt.
     *
     * <p>{@code fehlende} ist die vollstaendige Liste. Das ist der RIEGEL aus
     * {@code Teambuch}, und die Absage nennt ihn beim Namen: Wer eine Vollmacht
     * weitergeben will, die er nicht hat, soll lesen, welche das ist - sonst haelt er es
     * fuer einen Fehler des Befehls.
     */
    ZU_WENIG_RECHTE("team.zu-wenig-rechte", Art.SATZ, "rolle", "fehlende"),

    /** Dieser Server hat ueberhaupt keine Rollen - Absage auf eine Vergabe. */
    KEIN_TEAM("team.kein-team", Art.SATZ),

    /**
     * Den Spieler kennt dieser Server nicht.
     *
     * <p>Eine Rolle wird auch an Abwesende vergeben - das ist der Normalfall, denn man
     * macht niemanden zum Moderator, waehrend er zusieht. Darum wird nach dem OFFLINE
     * bekannten Spieler gesucht, und diese Absage trifft nur den, der noch nie hier war.
     */
    UNBEKANNTER_SPIELER("team.unbekannter-spieler", Art.SATZ, "name"),

    /** Eine Ablage hat geworfen - nichts ist passiert. */
    GESTOERT("team.gestoert", Art.SATZ),

    /**
     * Das Muster fuer die Vergabezeit in {@link #ZEIGEN_ZEILE}.
     *
     * <p>{@link Art#MUSTER}, wie {@code blockbuch.zeit-muster}: Es wird beim Laden in
     * einen {@code DateTimeFormatter} uebersetzt, und ein kaputtes Muster ist dann eine
     * Klage mit Zeilennummer statt eines Wurfs mitten im Befehl.
     */
    ZEIT_MUSTER("team.zeit-muster", Art.MUSTER),

    /**
     * Der Trenner zwischen zwei Rollennamen in einer Aufzaehlung.
     *
     * <p>{@link Art#WORT} und mit {@link Satz#LEER_ERLAUBT}, wie die zwei Trenner der
     * Weltenliste in {@link ReiseSatz}: Ein Trenner sendet keine Zeile, sondern laesst
     * zwei Eintraege aneinanderstossen, und wer seine Rollen untereinander statt
     * nebeneinander will, braucht ihn leer.
     */
    TRENNER_WORT("team.trenner-wort", Art.WORT, LEER_ERLAUBT);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    TeamSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    TeamSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.leerErlaubt = leerErlaubt;
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
     * Nur der Trenner darf leer sein.
     *
     * <p>Jeder andere Satz dieses Bereichs ist eine Antwort auf eine Tat oder eine Frage
     * des Betreibers - ein leerer waere ein Befehl, der nichts sagt, und der sieht aus
     * wie ein kaputtes Plugin.
     */
    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
