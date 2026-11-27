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
    TAT_BENUTZEN("blockbuch.tat.benutzen", Art.WORT),

    /**
     * Das Wort fuer eine Sprengung - an den Leser des Blockbuchs.
     *
     * <p>Ein eigenes Wort und nicht das von {@link #TAT_ABBAUEN}: In der Zeile
     * steht oft niemand als Taeter, und "niemand baute ab" ist ein anderer Satz
     * als "hier ist etwas explodiert".
     */
    TAT_SPRENGEN("blockbuch.tat.sprengen", Art.WORT),

    /**
     * Das Wort fuer Feuer - Abbrennen UND Ausbreitung.
     *
     * <p>Ein Wort fuer zwei Ereignisse, weil die Tat eine ist: Fuer die Moderation
     * ist beides "Feuer war hier".
     */
    TAT_FEUER("blockbuch.tat.feuer", Art.WORT),

    /** Das Wort fuer ein Wesen, das einen Block geaendert hat. */
    TAT_WESEN("blockbuch.tat.wesen", Art.WORT),

    /**
     * Das Wort fuer einen Kolben, der einen Block bewegt hat.
     *
     * <p>Im Buch steht diese Tat NUR, wenn sie angehalten wurde - siehe
     * {@link de.freimarken.kern.Blocktat#nurAbgewiesen}. Das Wort muss darum nicht
     * erklaeren, dass es geklappt hat; die Zeile sagt "abgewiesen" ohnehin dazu.
     */
    TAT_KOLBEN("blockbuch.tat.kolben", Art.WORT),

    /** Das Wort fuer Wasser oder Lava - wie {@link #TAT_KOLBEN} nur angehalten. */
    TAT_FLUESSIGKEIT("blockbuch.tat.fluessigkeit", Art.WORT),

    /**
     * Die Ueberschrift der Vorschau auf eine Rueckrollung.
     *
     * <p>NENNT ALLE DREI ANGABEN - Ort, Radius und Fenster. Was der Betreiber
     * bestaetigt, ist genau dieser Kasten; stuende nur "Rueckweg:" da, bestaetigte er
     * eine Zahl, die er sich gemerkt zu haben glaubt.
     */
    RUECKWEG_KOPF("blockbuch.rueckweg.kopf", Art.SATZ, "ort", "radius", "minuten"),

    /**
     * Was die Rueckrollung taete, in Zahlen.
     *
     * <p>Wegnehmen und Herstellen GETRENNT, und das ist die Angabe, auf die es
     * ankommt: Eine Rueckrollung, die fast nur wegnimmt, war eine Bauerei; eine, die
     * fast nur herstellt, war ein Abriss. Der Betreiber sieht an den zwei Zahlen,
     * ob er den Vorfall erwischt hat, den er meint.
     */
    RUECKWEG_ZAHLEN("blockbuch.rueckweg.zahlen", Art.SATZ, "zeilen", "wegnehmen",
            "herstellen"),

    /**
     * Wie viele Zeilen ohne Vorzustand auskommen muessen.
     *
     * <p>Eine eigene Zeile, weil sie das ERGEBNIS beschreibt und nicht den Umfang:
     * Diese Stellen bekommen den Block zurueck, aber nicht seine Blickrichtung, und
     * wo in Wasser gebaut wurde, kommt Luft. Wer es vorher weiss, wundert sich
     * nicht - und kann sich gegen die Rueckrollung entscheiden.
     */
    RUECKWEG_ROH("blockbuch.rueckweg.roh", Art.SATZ, "anzahl"),

    /**
     * Die Zeile zum Bestaetigen - fertig zum Abtippen.
     *
     * <p>Der ganze Befehl steht darin, samt Welt und Koordinaten. Das ist die
     * Sicherung gegen einen Schritt zwischen Warnung und Bestaetigung: Ohne den Ort
     * im Befehl rollte die Bestaetigung den Kasten um die Stelle zurueck, an der der
     * Betreiber JETZT steht - und ein Schritt waehrend des Lesens traefe den
     * falschen. Dieselbe Bauart wie bei {@code /freimarken parzelle aufgeben}.
     */
    RUECKWEG_BEFEHL("blockbuch.rueckweg.befehl", Art.SATZ, "befehl"),

    /** In diesem Kasten und Fenster steht nichts, was zurueckzunehmen waere. */
    RUECKWEG_LEER("blockbuch.rueckweg.leer", Art.SATZ, "ort", "minuten"),

    /**
     * Zu viele Zeilen - und darum wird NICHT zurueckgerollt.
     *
     * <p><b>Eine Absage und keine Bremse</b>, und der Satz muss den Grund nennen:
     * Ein Lauf in Haeppchen waere nicht bloss unvollstaendig, er waere FALSCH. Die
     * Abfrage gibt die aeltesten Zeilen zuerst; schneidet die Grenze zwischen zwei
     * Zeilen desselben Blocks, nimmt dieser Lauf die aeltere und merkt sie, und der
     * naechste stellt dann den Zwischenstand her. Der Betreiber muss darum
     * verkleinern - das kann ihm niemand abnehmen.
     */
    RUECKWEG_ZU_VIELE("blockbuch.rueckweg.zu-viele", Art.SATZ, "zeilen", "grenze"),

    /** Der Radius liegt ueber der eingestellten Grenze. */
    RUECKWEG_ZU_WEIT("blockbuch.rueckweg.zu-weit", Art.SATZ, "radius",
            "hoechstens"),

    /** Das Zeitfenster liegt ueber der eingestellten Grenze. */
    RUECKWEG_ZU_LANG("blockbuch.rueckweg.zu-lang", Art.SATZ, "minuten",
            "hoechstens"),

    /**
     * Die genannte Welt gibt es nicht - und der Satz nennt die, die es gibt.
     *
     * <p>Die Liste gehoert dazu: Der Befehl wird auch von der Konsole getippt, und
     * dort gibt es keine Vorschlaege. Ohne sie raet der Betreiber zwischen
     * {@code altfurt} und {@code freimarken:altfurt}.
     */
    RUECKWEG_UNBEKANNTE_WELT("blockbuch.rueckweg.unbekannte-welt", Art.SATZ, "welt",
            "welten"),

    /** Die Rueckrollung ist gelaufen. */
    RUECKWEG_FERTIG("blockbuch.rueckweg.fertig", Art.SATZ, "gesetzt", "zeilen"),

    /**
     * Wie viele Bloecke dieser Server nicht setzen konnte.
     *
     * <p>Nur gesendet, wenn es welche gab - und dann ist es die wichtigste Zeile des
     * Ergebnisses: Dort steht noch, was stehen geblieben ist. Die Zeilen sind
     * trotzdem gemerkt, sonst versuchte jeder weitere Lauf dasselbe wieder.
     */
    RUECKWEG_MISSLUNGEN("blockbuch.rueckweg.misslungen", Art.SATZ, "anzahl"),

    /**
     * Die Vorschau am eigenen Standort geht nur mit einem Koerper.
     *
     * <p>Eine gelesene Absage und kein {@code requires} - dieselbe Begruendung wie
     * bei {@link #KEIN_BLOCK}: Der Betreiber auf der Konsole soll den WEG lesen, der
     * ihm offensteht, und nicht "unbekannter Befehl". Fuer ihn gibt es den Zweig mit
     * Welt und Koordinaten, und genau den nennt dieser Satz.
     */
    RUECKWEG_KEIN_ORT("blockbuch.rueckweg.kein-ort", Art.SATZ),

    /**
     * Die getippte Hoehe gibt es in dieser Welt nicht.
     *
     * <p>Erreichbar, sobald jemand die Hoehe TIPPT - und das ist in Altfurt schnell
     * passiert: Der Boden liegt dort bei y -61, die Unterkante bei -64. Ohne diesen
     * Satz wuerfe die Kastenrechnung, und der Betreiber bekaeme eine rote
     * Konsolenmeldung statt einer Auskunft.
     */
    RUECKWEG_KEINE_HOEHE("blockbuch.rueckweg.keine-hoehe", Art.SATZ, "hoehe",
            "unten", "oben");

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
            case SPRENGEN -> TAT_SPRENGEN;
            case FEUER -> TAT_FEUER;
            case WESEN -> TAT_WESEN;
            case KOLBEN -> TAT_KOLBEN;
            case FLUESSIGKEIT -> TAT_FLUESSIGKEIT;
        };
    }
}
