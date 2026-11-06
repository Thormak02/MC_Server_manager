package de.freimarken.kern;

import java.util.List;

/**
 * Die Zahlen des Verdienens - Deckel und abnehmender Ertrag.
 *
 * <p>Dieselbe Bauart wie {@link Berufswerte} und {@link Zeitwerte}: ein Satz, eine
 * Vorgabe im PROGRAMM, und zu jeder Zahl eine Frage, die der Leser der
 * Einstellungsdatei stellen kann, ohne zu werfen.
 *
 * <p><b>Gerechnet wird in PFENNIGEN</b>, also in Hundertsteln einer Mark. Der Grund ist
 * eine Rechnung: Ein Spieler soll 2.000 bis 5.000 Mark je Spielstunde verdienen (Plan
 * 8.2), und in einer Stunde Arbeit baut er einige tausend Bloecke ab - das sind ein bis
 * zweieinhalb Mark je Block. Die Kasse kennt nur ganze Mark; ohne Hundertstel muesste
 * gewoehnlicher Stein 0 zahlen, und die ganze Spanne haenge an der Erzdichte statt an der
 * Arbeit. Gesammelt wird der Rest je Spieler, gebucht werden nur ganze Mark.
 *
 * <p><b>Der abnehmende Ertrag in Stufen mit Boden</b>, und zwar je Spieler und BLOCKART:
 * Die ersten {@link #vollBis} Bloecke einer Art zahlen voll, danach senkt jede Stufe von
 * {@link #stufe} Bloecken den Satz auf {@link #faktorProzent} des vorherigen, nie unter
 * {@link #bodenProzent}. Der Plan gibt dafuer einen einzigen Satz her - "derselbe Block
 * bringt innerhalb einer Stunde immer weniger, ohne das ist jede Mobfarm eine
 * Gelddruckmaschine" -, also steht die Form hier und die Zahlen in der Datei.
 *
 * <p>Je BLOCKART und nicht je Position: Eine Mobfarm liefert immer dieselbe Art an
 * wechselnden Orten, und genau sie ist der benannte Grund fuer die Regel. Je Position
 * waere sie wirkungslos.
 *
 * @param tagesdeckel   wie viele Mark ein Spieler an einem Tag hoechstens aus Arbeit
 *                      bekommt. Je SPIELER und nicht je Beruf - sonst kaeme er ueber zwei
 *                      Grundberufe doppelt (Plan 8.2)
 * @param vollBis       wie viele Bloecke einer Art in der gleitenden Stunde voll zahlen
 * @param stufe         wie viele Bloecke eine Stufe breit ist
 * @param faktorProzent auf wie viel Prozent des vorherigen Satzes jede Stufe senkt
 * @param bodenProzent  unter welchen Anteil des vollen Satzes es nie faellt
 * @param loehne        was jede Taetigkeit an jedem Material einbringt. Die TAFEL gehoert
 *                      in denselben Satz wie die Zahlen, nicht daneben: Ein Neuladen
 *                      tauscht beides als EIN Stueck, und wer mitten in der Rechnung
 *                      einen neuen Lohn aus einer alten Minderung bekaeme, verdiente
 *                      einen Augenblick lang etwas, das niemand eingestellt hat
 */
public record Verdienstwerte(long tagesdeckel, int vollBis, int stufe, int faktorProzent,
                             int bodenProzent, Lohntafel loehne) {

    public static final String PFAD_TAGESDECKEL = "verdienst.tagesdeckel";

    public static final String PFAD_VOLL_BIS = "verdienst.minderung.voll-bis";

    public static final String PFAD_STUFE = "verdienst.minderung.stufe";

    public static final String PFAD_FAKTOR = "verdienst.minderung.faktor-prozent";

    public static final String PFAD_BODEN = "verdienst.minderung.boden-prozent";

    /** Der Abschnitt mit den Loehnen - je Beruf, Taetigkeit und Material. */
    public static final String PFAD_LOEHNE = "verdienst.loehne";

    /**
     * 20.000 Mark am Tag.
     *
     * <p>Gerechnet gegen die Kalibrierung: Bei 2.000 bis 5.000 Mark je Spielstunde sind
     * das vier bis zehn Stunden Arbeit AN EINEM TAG. Wer das erreicht, farmt - und der
     * Deckel soll "grosszuegig genug sein, dass ihn normales Spielen nicht erreicht"
     * (Plan 9.1). Zwei Stunden waeren zu knapp fuer einen Samstag, hunderttausend waeren
     * kein Deckel.
     */
    public static final long VORGABE_TAGESDECKEL = 20_000L;

    /**
     * Die ersten 200 Bloecke einer Art zahlen voll.
     *
     * <p>Gerechnet gegen das normale Spielen: Ein Haus sind einige hundert Bloecke, aber
     * selten zweihundert derselben ART in einer Stunde. Ein Stollen in den Stein geht
     * darueber hinaus - und genau dort soll die Minderung greifen.
     */
    public static final int VORGABE_VOLL_BIS = 200;

    public static final int VORGABE_STUFE = 200;

    public static final int VORGABE_FAKTOR_PROZENT = 50;

    /**
     * Zehn Prozent, und nicht null.
     *
     * <p>Ein Boden von 0 waere eine unsichtbare Wand: Ab einer Zahl, die niemand kennt,
     * passiert gar nichts mehr, und der Spieler sucht den Fehler bei sich. Ein Zehntel
     * ist spuerbar wenig und bleibt eine Antwort.
     */
    public static final int VORGABE_BODEN_PROZENT = 10;

    /** Der grosszuegigste Deckel, der sich noch rechnen laesst. */
    public static final long HOECHSTER_TAGESDECKEL = Long.MAX_VALUE / 4L;

    /** Wie viele Bloecke eine Stufe hoechstens breit sein darf. */
    public static final int HOECHSTE_STUFE = 1_000_000;

    /**
     * Die Vorgabe traegt eine LEERE Lohntafel - und das ist Absicht.
     *
     * <p>Jede andere Vorgabe dieses Projekts steht im Programm, damit sie nicht fehlen
     * kann. Eine Lohntafel mit vierzig Zeilen dort waere aber eine zweite Abschrift der
     * ausgelieferten Datei, und zwei Abschriften derselben Tabelle laufen auseinander -
     * genau wie beim Abschnitt {@code welten:}, der ebenfalls keinen Rueckfall im
     * Programm hat.
     *
     * <p>Fehlt die Liste, verdient also niemand etwas. Das ist die sichere Richtung: Ein
     * geratener Lohn macht Geld, das keine Ruecksicherung zurueckholt. Der Leser der
     * Einstellungsdatei sagt es laut, und der Start nennt die Zahl der gelesenen Zeilen.
     */
    public static final Verdienstwerte VORGABE = new Verdienstwerte(VORGABE_TAGESDECKEL,
            VORGABE_VOLL_BIS, VORGABE_STUFE, VORGABE_FAKTOR_PROZENT,
            VORGABE_BODEN_PROZENT, Lohntafel.ausLoehnen(List.of()));

    public Verdienstwerte {
        if (loehne == null) {
            throw new IllegalArgumentException("Ohne Lohntafel gibt es keinen Verdienst;"
                    + " eine leere Tafel ist erlaubt und heisst: nichts bringt etwas ein");
        }
        if (!brauchbarerDeckel(tagesdeckel)) {
            throw new IllegalArgumentException("Tagesdeckel " + tagesdeckel + " liegt"
                    + " nicht zwischen 1 und " + HOECHSTER_TAGESDECKEL
                    + ". Eine 0 hiesse, niemand verdient etwas - wer die Berufe"
                    + " abschalten will, laesst die Lohnliste leer.");
        }
        if (!brauchbareAnzahl(vollBis)) {
            throw new IllegalArgumentException("voll-bis ist " + vollBis + " - erlaubt"
                    + " ist 1 bis " + HOECHSTE_STUFE + ". Eine 0 hiesse, dass schon der"
                    + " erste Block gemindert ist.");
        }
        if (!brauchbareAnzahl(stufe)) {
            throw new IllegalArgumentException("stufe ist " + stufe + " - erlaubt ist 1"
                    + " bis " + HOECHSTE_STUFE + ". Eine 0 teilte die Rechnung durch"
                    + " null.");
        }
        if (!brauchbarerFaktor(faktorProzent)) {
            throw new IllegalArgumentException("faktor-prozent ist " + faktorProzent
                    + " - erlaubt ist 1 bis 99. Bei 100 mindert nichts, bei 0 faellt der"
                    + " Satz in einem Schritt auf den Boden; beides ist keine Stufe.");
        }
        if (!brauchbarerBoden(bodenProzent)) {
            throw new IllegalArgumentException("boden-prozent ist " + bodenProzent
                    + " - erlaubt ist 1 bis 100. Eine 0 waere eine unsichtbare Wand: Ab"
                    + " einer Zahl, die niemand kennt, passiert gar nichts mehr.");
        }
    }

    public static boolean brauchbarerDeckel(long mark) {
        return mark >= 1L && mark <= HOECHSTER_TAGESDECKEL;
    }

    public static boolean brauchbareAnzahl(long zahl) {
        return zahl >= 1L && zahl <= HOECHSTE_STUFE;
    }

    /**
     * Ein Faktor von 1 bis 99 Prozent.
     *
     * <p>Nicht 100: Dann mindert nichts, und die Regel waere stumm aus - wer sie
     * abschalten will, setzt {@code voll-bis} hoch. Nicht 0: Dann faellt der Satz in
     * einem Schritt auf den Boden, und die Stufen daneben waeren eine Zierde.
     */
    public static boolean brauchbarerFaktor(long prozent) {
        return prozent >= 1L && prozent <= 99L;
    }

    public static boolean brauchbarerBoden(long prozent) {
        return prozent >= 1L && prozent <= 100L;
    }
}
