package de.freimarken.paper;

import de.freimarken.kern.Blocktat;
import java.util.Set;

/**
 * Die Saetze des Blockbuchs - was {@code /freimarken blockbuch} antwortet.
 *
 * <p>Ein eigener Bereich und keine Konstanten in {@link BundSatz}: Zwoelf Dateien statt
 * einer, eine je Bereich, damit zwei gleichzeitig arbeitende Haende nie dieselbe Datei
 * teilen - dieselbe Begruendung wie bei {@link LadenSatz}. Die BESCHREIBUNG des Befehls
 * bleibt dagegen in {@link BundSatz}, denn dort stehen alle Befehlsbeschreibungen, und
 * sie werden nur beim Start gelesen.
 *
 * <p><b>Die Tat ist ein WORT und kein Teil der Zeile</b>, und das ist die Entscheidung
 * dieses Bereichs. Drei vollstaendige Zeilensaetze - einer je Tat - waeren drei
 * Abschriften derselben Form, und beim vierten Wort liefen sie auseinander. So steht die
 * FORM der Zeile an einer Stelle, und was darin eingesetzt wird, kommt ueber
 * {@link #tatwort}.
 *
 * <p><b>Gelungen und abgewiesen sind ZWEI Zeilensaetze</b>, und das ist kein Widerspruch
 * dazu: Sie unterscheiden sich nicht im Wort, sondern im ganzen Bild - der abgewiesene
 * Versuch soll rot sein und ein Zeichen tragen, damit ein Dutzend davon beim Durchlesen
 * auffaellt. Das ist genau der Fund, nach dem die Moderation sucht, und eine Zeile, die
 * sich nur in einem eingesetzten Wort unterscheidet, faellt nicht auf.
 */
enum BlockSatz implements Satz {

    /** Die Ueberschrift der Abfrage an einem Block. */
    KOPF_ORT("blockbuch.kopf-ort", Art.SATZ, "ort", "anzahl"),

    /** Die Ueberschrift der Abfrage nach einem Spieler. */
    KOPF_SPIELER("blockbuch.kopf-spieler", Art.SATZ, "spieler", "anzahl"),

    /**
     * Eine Zeile einer GELUNGENEN Tat.
     *
     * <p>Der Ort steht hier NICHT drin: Bei der Abfrage an einem Block ist er die
     * Ueberschrift, und zwanzig Zeilen mit derselben Koordinate waeren zwanzigmal
     * dasselbe. Fuer die Abfrage nach einem Spieler gibt es {@link #ZEILE_MIT_ORT}.
     */
    ZEILE("blockbuch.zeile", Art.SATZ, "zeit", "spieler", "tat", "block"),

    /**
     * Eine Zeile einer ABGEWIESENEN Tat.
     *
     * <p>Ein eigener Satz und nicht ein eingesetztes Wort: Der abgewiesene Versuch soll
     * sich im ganzen Bild unterscheiden, damit ein Dutzend davon beim Durchlesen
     * auffaellt. Eine Zeile, die sich nur in einem Wort unterscheidet, tut das nicht -
     * und genau diese Zeilen sind der Fund, bei dem gar nichts passiert ist.
     */
    ZEILE_ABGEWIESEN("blockbuch.zeile-abgewiesen", Art.SATZ, "zeit", "spieler", "tat",
            "block"),

    /** Eine Zeile MIT Ort - fuer die Abfrage nach einem Spieler. */
    ZEILE_MIT_ORT("blockbuch.zeile-mit-ort", Art.SATZ, "zeit", "ort", "tat", "block"),

    /** Eine abgewiesene Zeile mit Ort. */
    ZEILE_MIT_ORT_ABGEWIESEN("blockbuch.zeile-mit-ort-abgewiesen", Art.SATZ, "zeit",
            "ort", "tat", "block"),

    /**
     * An diesem Block steht nichts.
     *
     * <p>Eine eigene Zeile und nicht eine leere Liste: Ein Spieler, der nur die
     * Ueberschrift sieht, haelt es fuer einen Fehler. Und der Satz muss sagen, WARUM
     * nichts da sein kann - die Welt kann abgewaehlt sein, oder die Frist hat die Zeile
     * geholt. Ohne diesen Hinweis sucht der Moderator den Fehler im Plugin.
     */
    LEER_ORT("blockbuch.leer-ort", Art.SATZ, "ort"),

    /** Zu diesem Spieler steht nichts im Buch. */
    LEER_SPIELER("blockbuch.leer-spieler", Art.SATZ, "spieler"),

    /**
     * Der Spieler sieht keinen Block an.
     *
     * <p>Erreichbar bei jedem Blick in den Himmel und darum kein Randfall. Der Satz sagt
     * die REICHWEITE mit: Wer auf einen Block dreissig Meter weit schaut, soll nicht
     * raten, ob das Blockbuch leer ist oder der Blick zu weit ging.
     */
    KEIN_BLOCK("blockbuch.kein-block", Art.SATZ, "reichweite"),

    /**
     * Das Blockbuch ist abgeschaltet.
     *
     * <p>Eine eigene Absage, weil sonst "hier steht nichts" die Antwort waere - und die
     * ist in diesem Fall irrefuehrend: Es steht nichts da, weil nichts aufgeschrieben
     * WIRD, nicht weil nichts geschehen ist. Genau der Unterschied, den ein Moderator
     * wissen muss, bevor er jemanden freispricht.
     */
    AUS("blockbuch.aus", Art.SATZ),

    /**
     * Die Ablage hat geschwiegen.
     *
     * <p>Auch das ist nicht "hier steht nichts": Ein Netzfehler darf nicht aussehen wie
     * ein freigesprochener Spieler.
     */
    GESTOERT("blockbuch.gestoert", Art.SATZ),

    /** Den genannten Spieler kennt der Server nicht. */
    UNBEKANNT("blockbuch.unbekannt", Art.SATZ, "name"),

    /**
     * Wie ein Zeitpunkt in einer Zeile aussieht.
     *
     * <p>Ein EIGENES Muster und nicht {@code zeit.verlauf-muster}: Jenes laesst das Jahr
     * weg, weil zehn Verlaufszeilen zehnmal dasselbe Jahr trugen. Hier ist die Frist
     * einstellbar bis zu zehn Jahren, und dann ist das Jahr genau die Angabe, die
     * fehlt. Zwei Bereiche, die sich ein Muster teilen, koennen es nicht beide richtig
     * haben.
     */
    ZEIT_MUSTER("blockbuch.zeit-muster", Art.MUSTER),

    /** Das Wort fuer eine gesetzte Tat. */
    TAT_SETZEN("blockbuch.tat.setzen", Art.WORT),

    /** Das Wort fuer eine abgebaute Tat. */
    TAT_ABBAUEN("blockbuch.tat.abbauen", Art.WORT),

    /** Das Wort fuer eine benutzte Tat. */
    TAT_BENUTZEN("blockbuch.tat.benutzen", Art.WORT);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    BlockSatz(String pfad, Art art, String... platzhalter) {
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
     * <p>Jeder ist eine ganze Zeile an einen Moderator oder ein Wort darin. Eine leere
     * Ueberschrift liesse die Liste ohne Anfang, eine leere Zeile verschwiege eine Tat,
     * und ein leerer Leer-Satz machte den Befehl zu einem, der nichts tut - also zu
     * einem, der aussieht wie ein Freispruch.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }

    /**
     * Das Wort zu einer Tat.
     *
     * <p>Ein {@code switch} ueber die Aufzaehlung und keine Karte: Kommt eine Tat dazu,
     * ist das hier ein Uebersetzungsfehler und nicht ein stummes Loch - dieselbe
     * Entscheidung wie bei {@link BerufSatz#taetigkeitswort}.
     */
    static BlockSatz tatwort(Blocktat tat) {
        if (tat == null) {
            throw new IllegalArgumentException("Ohne Tat gibt es kein Wort");
        }
        return switch (tat) {
            case SETZEN -> TAT_SETZEN;
            case ABBAUEN -> TAT_ABBAUEN;
            case BENUTZEN -> TAT_BENUTZEN;
        };
    }
}
