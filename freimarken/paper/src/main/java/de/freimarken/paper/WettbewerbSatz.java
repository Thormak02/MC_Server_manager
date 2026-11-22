package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze des Angelwettbewerbs in Altfurt (Plan 14).
 *
 * <p><b>Die SIEGERLISTE ist eine Zeile, die sich wiederholt</b>, und keine Liste in der
 * Sprachdatei: {@link #PLATZ} wird je Platz einmal gebaut. Eine Liste haette eine Zahl
 * von Schluesseln, die der Betreiber bestimmt, und jeder Schluessel dieser Datei muss zu
 * einem Satz des Programms gehoeren ({@code VorlageTest} 2) - dieselbe Entscheidung wie
 * bei {@link RegelnSatz}, nur umgekehrt geloest, weil hier Werte eingesetzt werden.
 *
 * <p><b>Die ANSAGEN gehen an alle</b> - Fensteroeffnung und Siegerliste. Sie sind der
 * Grund, ueberhaupt hinzugehen: Ein Wettbewerb, von dem niemand erfaehrt, hat keine
 * Teilnehmer. Wer sie nicht will, laesst sie leer ({@link Satz#LEER_ERLAUBT}) - dann
 * sagt nur noch {@code /wettbewerb}, was laeuft.
 */
enum WettbewerbSatz implements Satz {

    /** Die Ueberschrift von {@code /wettbewerb}. */
    KOPF("wettbewerb.kopf", Art.SATZ),

    /** Die Zeile, wenn gerade ein Fenster offen ist. */
    OFFEN("wettbewerb.offen", Art.SATZ, "bis", "topf", "teilnehmer"),

    /** Die Zeile, wenn gerade keines offen ist - mit dem naechsten Termin. */
    GESCHLOSSEN("wettbewerb.geschlossen", Art.SATZ, "beginn"),

    /** Der eigene Stand, wenn man mitmacht. */
    EIGENER_STAND("wettbewerb.eigener-stand", Art.GEZAEHLT, "punkte", "platz"),

    /** Der Hinweis, wie man mitmacht - er nennt den Zweig und das Startgeld. */
    MITMACHEN("wettbewerb.mitmachen", Art.SATZ, "startgeld"),

    /** Die Ueberschrift der Rangliste. */
    RANGLISTE_KOPF("wettbewerb.rangliste.kopf", Art.SATZ),

    /** Eine Zeile der Rangliste - je Platz einmal gebaut. */
    PLATZ("wettbewerb.rangliste.platz", Art.SATZ, "platz", "name", "punkte"),

    /** Die Zeile, wenn noch niemand dabei ist. */
    RANGLISTE_LEER("wettbewerb.rangliste.leer", Art.SATZ),

    /** Angemeldet - das Startgeld ist gezahlt. */
    ANGEMELDET("wettbewerb.angemeldet", Art.SATZ, "startgeld", "topf"),

    /** Er war schon dabei. */
    SCHON_DABEI("wettbewerb.schon-dabei", Art.SATZ),

    /** Gerade laeuft keiner - mit dem naechsten Termin. */
    KEIN_LAUF("wettbewerb.kein-lauf", Art.SATZ, "beginn"),

    /** Das Geld reicht nicht - mit beiden Zahlen, damit es eine Antwort ist. */
    ZU_WENIG("wettbewerb.zu-wenig", Art.SATZ, "startgeld", "guthaben"),

    /** Der Wettbewerb laeuft gar nicht. */
    AUS("wettbewerb.aus", Art.SATZ),

    /** Eine Ablage oder eine Kasse hat geworfen. */
    GESTOERT("wettbewerb.gestoert", Art.SATZ),

    /** Die Konsole kann sich nicht anmelden. */
    NUR_SPIELER("wettbewerb.nur-spieler", Art.SATZ),

    /**
     * Die ANSAGE an alle, wenn das Fenster aufgeht.
     *
     * <p>{@link Satz#LEER_ERLAUBT}: Wer sie nicht will, laesst sie weg. Dann erfaehrt
     * man vom Wettbewerb nur ueber {@code /wettbewerb} - das ist eine denkbare
     * Serverform und kein Versehen.
     */
    ANSAGE_BEGINN("wettbewerb.ansage.beginn", Art.SATZ, Satz.LEER_ERLAUBT,
            "bis", "startgeld"),

    /** Die Ansage des Ergebnisses an alle. */
    ANSAGE_ENDE("wettbewerb.ansage.ende", Art.SATZ, Satz.LEER_ERLAUBT,
            "teilnehmer", "topf"),

    /**
     * Eine Siegerzeile in der Ansage - je Gewinner einmal gebaut.
     *
     * <p>Mit {@code preis}, und das ist der Unterschied zu {@link #PLATZ}: Die
     * Rangliste zeigt Punkte, die Ansage zeigt, was dabei herauskam.
     */
    ANSAGE_SIEGER("wettbewerb.ansage.sieger", Art.SATZ, Satz.LEER_ERLAUBT,
            "platz", "name", "punkte", "preis"),

    /** Die Ansage, wenn niemand dabei war. */
    ANSAGE_NIEMAND("wettbewerb.ansage.niemand", Art.SATZ, Satz.LEER_ERLAUBT),

    /** Der eigene Gewinn - an den Sieger allein, auch wenn er gerade nicht da ist. */
    GEWONNEN("wettbewerb.gewonnen", Art.SATZ, "platz", "punkte", "preis"),

    /**
     * Die sieben Wochentage - fuer die Zeile "der naechste ist am ...".
     *
     * <p>SIEBEN feste Saetze und keine offene Satzfamilie: Die Wochentage sind
     * abgeschlossen, jeder steht in der ausgelieferten Vorlage, und {@code VorlageTest}
     * haelt sie darum in beide Richtungen. Eine offene Familie braeuchte vier
     * Pflichtstellen und waere die Loesung fuer ein Problem, das es hier nicht gibt.
     *
     * <p>{@link Art#WORT} und kein Satz: Sie werden in andere Zeilen eingesetzt und
     * tragen deshalb keinen Farbtag - er ueberschriebe die Farbe der Zeile, in der sie
     * landen.
     *
     * <p>Genannt werden sie nur, wenn der Wettbewerb NICHT taeglich laeuft. Bei sieben
     * Tagen ist "um 18:00" die ganze Auskunft.
     */
    TAG_MONTAG("wettbewerb.tag.montag", Art.WORT),
    TAG_DIENSTAG("wettbewerb.tag.dienstag", Art.WORT),
    TAG_MITTWOCH("wettbewerb.tag.mittwoch", Art.WORT),
    TAG_DONNERSTAG("wettbewerb.tag.donnerstag", Art.WORT),
    TAG_FREITAG("wettbewerb.tag.freitag", Art.WORT),
    TAG_SAMSTAG("wettbewerb.tag.samstag", Art.WORT),
    TAG_SONNTAG("wettbewerb.tag.sonntag", Art.WORT);


    private final String pfad;
    private final Art art;
    private final boolean leerErlaubt;
    private final Set<String> platzhalter;

    WettbewerbSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    WettbewerbSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
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

    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
