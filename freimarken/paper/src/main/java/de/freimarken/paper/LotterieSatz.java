package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Lotterie in Altfurt (Plan 14).
 *
 * <p><b>JE GEWINNART EIN SATZ</b>, und das ist die Entscheidung dieses Bereichs: Mark,
 * Goldmark und Gegenstand haben jeweils ihre eigene Gewinnzeile. Ein gemeinsamer Satz mit
 * einem Platzhalter fuer die Einheit waere kuerzer gewesen, aber die Saetze lesen sich
 * verschieden - "800 Mark" ist ein Betrag, "ein Fischerhut von Altfurt" ist ein Ding, und
 * drei Goldmark sind etwas, das man sonst kaufen muss. Derselbe Zuschnitt wie bei
 * {@link BonusSatz}, der auch drei Zeilen fuer drei Faelle hat.
 *
 * <p><b>Der NAME des Gewinns kommt aus der {@code config.yml} und ist KEIN Satz.</b> Das
 * ist der Unterschied zu {@link Berufsname} und {@link Aufgabenname}, die offene
 * Satzfamilien sind: Was ein Gewinn heisst, steht bei seiner Definition
 * ({@code lotterie.gewinne.<name>.name}) und gehoert dorthin - es ist Teil der
 * Gewinnbeschreibung und nicht der Uebersetzung. Hier kommt er als Platzhalter
 * {@code gewinn} herein. Damit braucht ein neuer Gewinn KEINE Zeile in der Sprachdatei,
 * und ein Betreiber, der zwanzig Gewinne eintraegt, muss nicht zwanzig Saetze pflegen.
 *
 * <p>Der PREIS steht in jeder Gewinnzeile mit darin, auch wenn er 0 war: Das gratis Los
 * ist der Kern der Einrichtung, und wer liest "gratis gezogen", weiss, dass er morgen
 * wieder eines bekommt.
 */
enum LotterieSatz implements Satz {

    /** Die Ueberschrift von {@code /lotterie}. */
    KOPF("lotterie.kopf", Art.SATZ),

    /**
     * Das Wort, das anstelle des Preises steht, wenn das Los gratis war.
     *
     * <p>GEMESSEN am 22.11.2026 und dann gebaut: Ohne diesen Satz stand in der
     * Gewinnzeile eines gratis Loses "(Los: 0)" - genau die Zeile, gegen die der ganze
     * Abschnitt gebaut ist. Ein Preis von 0 ist kein Preis, sondern eine Gabe, und die
     * hat ein Wort.
     *
     * <p>{@link Art#WORT} und kein Satz: Es wird in die drei Gewinnzeilen EINGESETZT,
     * wie {@code waehrung.betrag}. Darum traegt es auch keinen Farbtag - er
     * ueberschriebe die Farbe der Zeile, in der es landet.
     */
    GRATIS_WORT("lotterie.gratis-wort", Art.WORT),

    /**
     * Die Standzeile, wenn das naechste Los gratis ist.
     *
     * <p>{@code frei} ist die Zahl der noch freien Lose des Tages, {@code hoechste} die
     * Tagesgrenze. Ein eigener Satz und nicht {@link #STAND_PREIS} mit einer 0: "kostet
     * 0 Mark" ist eine Zeile, die niemand so schreiben wuerde.
     */
    STAND_GRATIS("lotterie.stand.gratis", Art.SATZ, "gezogen", "hoechste"),

    /** Die Standzeile mit Preis. */
    STAND_PREIS("lotterie.stand.preis", Art.SATZ, "preis", "gezogen", "hoechste"),

    /** Die Standzeile, wenn die Tagesgrenze erreicht ist. */
    STAND_GENUG("lotterie.stand.genug", Art.SATZ, "hoechste"),

    /** Die Zeile darunter: was die naechsten Lose kosten wuerden. */
    STAND_STAFFEL("lotterie.stand.staffel", Art.SATZ, "staffel"),

    /** Der Hinweis, wie man zieht - er nennt den Zweig des Befehls. */
    STAND_HINWEIS("lotterie.stand.hinweis", Art.SATZ),

    /** Mark gewonnen. */
    GEZOGEN_MARK("lotterie.gezogen.mark", Art.SATZ, "gewinn", "menge", "preis"),

    /** Goldmark gewonnen - {@link Art#GEZAEHLT}, denn eine Goldmark ist nicht drei. */
    GEZOGEN_GOLDMARK("lotterie.gezogen.goldmark", Art.GEZAEHLT, "gewinn", "menge",
            "preis"),

    /**
     * Einen Gegenstand gewonnen - {@link Art#GEZAEHLT}, denn ein Diamant ist nicht zwei.
     *
     * <p>{@code gewinn} ist der Anzeigename aus der {@code config.yml}, falls einer
     * dasteht, sonst der Gegenstandsname. Was im Rucksack landet, ist in beiden Faellen
     * dasselbe - der Name ist die Auskunft, nicht die Sache.
     */
    GEZOGEN_GEGENSTAND("lotterie.gezogen.gegenstand", Art.GEZAEHLT, "gewinn", "menge",
            "preis"),

    /** Das Geld reicht nicht - mit beiden Zahlen, damit es eine Antwort ist. */
    ZU_WENIG("lotterie.zu-wenig", Art.SATZ, "preis", "guthaben"),

    /** Fuer heute ist Schluss. */
    HEUTE_GENUG("lotterie.heute-genug", Art.SATZ, "hoechste"),

    /**
     * Der Rucksack ist zu voll.
     *
     * <p>{@code fehlt} ist die Zahl der Stuecke, die nicht hineinpassen - und
     * {@link Art#GEZAEHLT}, denn bei einem fehlenden Platz steht dort eine 1. Das Los ist
     * dabei NICHT verbraucht; das sagt dieser Satz, damit niemand glaubt, er haette eines
     * verloren.
     */
    KEIN_PLATZ("lotterie.kein-platz", Art.GEZAEHLT, "fehlt"),

    /**
     * Bezahlt, aber der Gewinn kam nicht an - das Geld ist zurueck.
     *
     * <p>Nach einer genauen Platzrechnung kann das nicht eintreten, also ist es ein
     * Programm- oder Weltfehler. Der Satz steht trotzdem hier und nicht nur im Protokoll:
     * Dem Spieler fehlt in diesem Augenblick ein Gewinn, und er soll lesen, dass sein
     * Geld zurueck ist.
     */
    ERSTATTET("lotterie.erstattet", Art.SATZ, "preis"),

    /** Eine Ablage oder eine Kasse hat geworfen - nichts ist passiert. */
    GESTOERT("lotterie.gestoert", Art.SATZ),

    /** In Altfurt wird gerade nicht gezogen. */
    AUS("lotterie.aus", Art.SATZ),

    /**
     * Die Konsole kann kein Los ziehen.
     *
     * <p>Gelesen und nicht verborgen, wie bei {@code AnmeldeSatz.NUR_SPIELER}: Ein
     * Betreiber, der den Zweig nicht findet, weil sein Knoten verborgen ist, sucht an der
     * falschen Stelle.
     */
    NUR_SPIELER("lotterie.nur-spieler", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    LotterieSatz(String pfad, Art art, String... platzhalter) {
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
     * Kein Satz dieses Bereichs darf leer sein.
     *
     * <p>Jeder davon ist eine Antwort auf eine Tat des Spielers - ein leerer waere ein
     * Befehl, der nichts sagt, und der sieht aus wie ein kaputtes Plugin.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
