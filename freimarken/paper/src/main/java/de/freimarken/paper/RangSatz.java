package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Rangleiter.
 *
 * <p>Eine Datei je Bereich, wie bei {@link BonusSatz}: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Datei dieses Schrittes ist {@link Rangtext}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}.
 *
 * <p><b>Die Eigenheit dieses Bereichs: der Rangname ist ein PLATZHALTER und kein Satz.</b>
 * Welche Stufen es gibt, entscheidet der Betreiber in der {@code config.yml}; eine
 * Aufzaehlung hier koennte sie darum nicht kennen. Was in {@code {rang}} landet, ist der
 * Schluessel aus der Datei - {@code siedler}, {@code buerger} oder wie der Betreiber ihn
 * genannt hat. Das ist dieselbe Naht wie bei den Berufen, deren Namen auch aus der Datei
 * kommen und nicht aus einer Aufzaehlung.
 *
 * <p><b>Warum zwei der drei Saetze LEER sein duerfen</b> [19.11.2026]: Der Aufstieg selbst
 * MUSS sichtbar sein - wer neue Rechte bekommt und nicht liest, warum, haelt sie fuer
 * einen Zufall. Ob danach noch steht, was als naechstes kommt, ist Geschmack: Der eine
 * Betreiber will den Ausblick als Anreiz, der andere eine einzige Zeile. Dafuer braucht es
 * keinen Schalter in der {@code config.yml} - die leere Zeile IST der Schalter, und zwar
 * an der Stelle, an der auch die Worte stehen.
 */
enum RangSatz implements Satz {

    /**
     * Der Aufstieg selbst - die eine Zeile, die nicht fehlen darf.
     *
     * <p>{@code rang} ist der Schluessel der NEUEN Stufe. Kein {@code vorher}: Was er
     * vorher war, weiss der Spieler, und eine Zeile "vom Reisenden zum Siedler" liest
     * sich wie ein Formular. Wer sie trotzdem will, nimmt {@code {rang}} und schreibt
     * den Rest dazu.
     *
     * <p>NICHT leer erlaubt: Ein Aufstieg, der stumm faellt, ist der Fall, in dem ein
     * Spieler ploetzlich mehr darf und es nie erfaehrt - dieselbe Begruendung, aus der
     * der Tagesbonus seine Zeile nicht abschalten laesst.
     */
    AUFGESTIEGEN("raenge.aufgestiegen", Art.SATZ, "rang"),

    /**
     * Was als naechstes kommt - gesendet unmittelbar nach dem Aufstieg.
     *
     * <p>{@code rang} ist der Schluessel der Stufe DARUEBER. Dieselbe Aufgabe wie
     * {@code {morgen}} beim Tagesbonus: der Grund, weiterzuspielen.
     *
     * <p>{@link Satz#LEER_ERLAUBT} - siehe den Klassenkopf.
     */
    NAECHSTER("raenge.naechster", Art.SATZ, Satz.LEER_ERLAUBT, "rang"),

    /**
     * Es gibt keine Stufe mehr darueber.
     *
     * <p>Ein eigener Satz und nicht das Weglassen von {@link #NAECHSTER}: Wer die oberste
     * Stufe erreicht, soll das lesen und nicht aus einer fehlenden Zeile schliessen. Ohne
     * Platzhalter - der erreichte Rang steht schon in der Zeile darueber.
     *
     * <p>{@link Satz#LEER_ERLAUBT} - siehe den Klassenkopf.
     */
    HOECHSTE("raenge.hoechste", Art.SATZ, Satz.LEER_ERLAUBT),

    // --- /rang: die Auskunft ueber den eigenen Stand [20.11.2026] -----------

    /**
     * Der Kopf von {@code /rang}.
     *
     * <p><b>Warum es diesen Befehl gibt:</b> Bis zum 20.11.2026 sah ein Spieler seinen
     * Rang nur im Augenblick des Aufstiegs und beim Beitritt. Nachsehen konnte er ihn
     * nicht - und die gelbe Zeile unter jeder Absage ("Mehr erlaubt ein hoeherer Rang.")
     * verwies damit auf etwas, das er nirgends finden konnte.
     */
    SCHAU_KOPF("raenge.schau.kopf", Art.SATZ),

    /** Auf welcher Stufe er steht. {@code rang} ist der Schluessel aus der Datei. */
    SCHAU_JETZT("raenge.schau.jetzt", Art.SATZ, "rang"),

    /**
     * In der Ablage steht eine Stufe, die die Leiter nicht mehr kennt.
     *
     * <p>Ein EIGENER Satz, und er ist der Grund, warum die Tafel den eingetragenen
     * Schluessel ueberhaupt mitbringt: {@code Rangstand.stufeIn} faellt auf die unterste
     * Stufe zurueck - richtig, aber als Auskunft eine stille Rueckstufung. Wer "du bist
     * reisender" liest, meldet nichts, und die Zeile bleibt fuer immer so stehen.
     * {@code rang} ist der eingetragene, {@code gilt} der geltende Schluessel.
     */
    SCHAU_UNBEKANNT("raenge.schau.unbekannt", Art.SATZ, "rang", "gilt"),

    /** Die naechste Stufe - darunter stehen ihre Bedingungen. */
    SCHAU_NAECHSTE("raenge.schau.naechste", Art.SATZ, "rang"),

    /**
     * Was er von dieser Bedingung schon hat - erfuellt.
     *
     * <p>ZWEI Saetze und nicht einer mit einem Platzhalter fuer das Haekchen: Der
     * Unterschied ist die FARBE, und die gehoert in den Satz. Ein Betreiber, der beides
     * gleich will, schreibt zweimal dasselbe hin - ein Betreiber, der es verschieden
     * will, koennte es mit einem Satz nicht.
     *
     * <p>Die Zeile DARUEBER - was verlangt ist - kommt aus dem Muenzhaus
     * ({@code mark.muenzhaus.bedingung.<art>}): dieselbe Frage, dieselbe Antwort. Eine
     * zweite Wortfamilie dafuer waere eine Abschrift, und zwei Abschriften laufen
     * auseinander.
     */
    SCHAU_STAND_ERFUELLT("raenge.schau.stand-erfuellt", Art.SATZ, "stand"),

    /** Dasselbe, noch offen - siehe {@link #SCHAU_STAND_ERFUELLT}. */
    SCHAU_STAND_OFFEN("raenge.schau.stand-offen", Art.SATZ, "stand"),

    /**
     * Der Hinweis auf {@code /rang leiter} unter der Auskunft.
     *
     * <p>{@link Satz#LEER_ERLAUBT}: Wer die Zeile nicht will, laesst sie weg - wie beim
     * Ausblick nach dem Aufstieg.
     */
    SCHAU_FUSS("raenge.schau.fuss", Art.SATZ, Satz.LEER_ERLAUBT),

    /** Der Kopf von {@code /rang leiter}. */
    LEITER_KOPF("raenge.leiter.kopf", Art.SATZ),

    /**
     * Eine Stufe, die er hat - DREI Saetze fuer drei Zustaende.
     *
     * <p>Erreicht heisst "an oder unter seiner Stufe" und wird aus der NUMMER gerechnet,
     * nicht aus den Bedingungen: Wer befoerdert wurde, behaelt seinen Rang auch dann,
     * wenn der Betreiber die Bedingungen danach heraufsetzt.
     */
    LEITER_ERREICHT("raenge.leiter.erreicht", Art.SATZ, "rang"),

    /** Die Stufe, auf der er steht. */
    LEITER_JETZT("raenge.leiter.jetzt", Art.SATZ, "rang"),

    /** Eine Stufe, die noch vor ihm liegt. */
    LEITER_OFFEN("raenge.leiter.offen", Art.SATZ, "rang"),

    /**
     * Auf diesem Server gibt es keine Raenge.
     *
     * <p>{@code raenge.an: false} oder eine leere Leiter. Ein eigener Satz und keine
     * leere Antwort: Ein Befehl, der nichts sagt, sieht kaputt aus.
     */
    AUS("raenge.aus", Art.SATZ),

    /**
     * Die Ablage hat nicht geantwortet.
     *
     * <p>Kein Rueckfall auf die unterste Stufe: Der saehe aus wie eine Auskunft und waere
     * eine Vermutung - und ein Spieler, der sich fuer einen Reisenden haelt, fragt beim
     * Team nach seinem verschwundenen Rang.
     */
    GESTOERT("raenge.gestoert", Art.SATZ),

    /** Auf der Konsole gibt es keinen Rang - sie ist kein Spieler. */
    KEINE_KONSOLE("raenge.keine-konsole", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    RangSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    RangSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
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
