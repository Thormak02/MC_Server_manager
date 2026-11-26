package de.freimarken.kern;

/**
 * Was ein Rueckweg hoechstens anfassen darf - Radius, Zeitfenster und Bloecke.
 *
 * <p><b>Eine eigene Werteklasse und kein Anbau an {@link Blockbuchwerte}</b>, und das hat
 * zwei Gruende, die beide handfest sind. Der erste: Jene Werte fragt die Wache bei JEDEM
 * Blockereignis - bei einer Sprengung hunderte Mal in einem Augenblick -, und sie
 * beantworten eine andere Frage ("was wird aufgeschrieben"). Diese hier liest ein
 * Moderationsbefehl, einmal, wenn er getippt wird. Der zweite ist der Konstruktor: Jener
 * nimmt bereits sieben Werte, und drei weitere Zahlen daneben waeren genau die Stelle, an
 * der die naechste Zahl stumm am falschen Platz landet.
 *
 * <p><b>Die Blockgrenze ist KEINE Bremse, sondern ein Riegel</b> - und das ist die
 * Entscheidung, auf die es hier ankommt. Ein Rueckweg, der an seiner Grenze abschneidet,
 * ist nicht bloss unvollstaendig, er ist FALSCH: Die Abfrage gibt die aeltesten Zeilen
 * zuerst, und schneidet die Grenze zwischen zwei Zeilen desselben Blocks, so nimmt dieser
 * Lauf die aeltere (richtig) und merkt sie - der zweite Lauf findet dann nur noch die
 * juengere und stellt deren Vorzustand her, also den ZWISCHENSTAND. Genau den vermeidet
 * {@link Rueckrollplan} mit seiner "aelteste je Ort"-Regel, und ein Lauf in Haeppchen
 * fuehrte ihn durch die Hintertuer wieder ein. Darum sagt der Befehl bei Ueberschreitung
 * ab und nennt die Zahl; verkleinern muss der Betreiber.
 */
public record Rueckweggrenzen(int hoechsterRadius, int hoechsteMinuten,
                              int hoechsteBloecke) {

    /** Wie weit ein Rueckweg hoechstens reicht, in Bloecken um den Mittelpunkt. */
    public static final String PFAD_RADIUS = "blockbuch.rueckweg.hoechster-radius";

    /** Wie weit ein Rueckweg hoechstens zurueckgreift, in Minuten. */
    public static final String PFAD_MINUTEN = "blockbuch.rueckweg.hoechste-minuten";

    /** Wie viele Zeilen ein Rueckweg hoechstens verarbeitet. */
    public static final String PFAD_BLOECKE = "blockbuch.rueckweg.hoechste-bloecke";

    /**
     * Fuenfzig Bloecke.
     *
     * <p>Ein Grief ist fast immer oertlich: ein gesprengtes Haus, eine abgeholzte Allee,
     * eine ausgeraeumte Wand. Fuenfzig Bloecke um den Moderator deckt das und faengt den
     * Fall, in dem jemand aus Versehen eine halbe Stadt umkehrt.
     */
    public static final int VORGABE_RADIUS = 50;

    /**
     * Zweihundertfuenfzig Bloecke als Obergrenze.
     *
     * <p>Nicht gegen den Betreiber, der wirklich weit zurueckrollen will - der traegt es
     * ein. Die Grenze faengt die Null, die aus 50 eine 500 macht: Ein Kasten mit Radius
     * 500 hat ueber eine Milliarde Bloecke, und die Abfrage dahinter lief ueber die
     * ganze Tabelle.
     */
    public static final int HOECHSTER_RADIUS = 250;

    /**
     * Ein Tag.
     *
     * <p>So lange, wie eine Beschwerde braucht, um anzukommen: Wer morgens liest, dass
     * nachts etwas passiert ist, kommt mit einem Tag hin. Laenger ist selten gemeint und
     * trifft schnell auch das, was andere seither rechtmaessig gebaut haben.
     */
    public static final int VORGABE_MINUTEN = 1440;

    /**
     * Ein Jahr als Obergrenze.
     *
     * <p>Die echte Grenze ist ohnehin die Aufbewahrungsfrist - was weg ist, kommt nicht
     * zurueck. Diese hier faengt nur die Zahl, bei der die Rechnung
     * {@code jetzt.minus(minuten)} ueberlaeuft.
     */
    public static final int HOECHSTE_MINUTEN = 525_600;

    /**
     * Fuenftausend Zeilen.
     *
     * <p>Der Lauf setzt Bloecke im HAUPTFADEN des Spielservers - dort wird ein Befehl
     * ausgefuehrt. Fuenftausend {@code setBlockData} ohne Physik sind ein kurzes Stocken;
     * fuenfzigtausend waeren ein Stillstand, und zwar einer, den der Betreiber selbst
     * ausgeloest hat, waehrend er einen Schaden behebt.
     *
     * <p>Grosszuegig genug fuer den Fall, fuer den es den Rueckweg gibt: Eine TNT-Ladung
     * von zwei Dutzend Bloecken reisst ein paar hundert Zeilen, ein gesprengtes Haus
     * selten mehr als tausend.
     */
    public static final int VORGABE_BLOECKE = 5_000;

    /** Fuenfzigtausend als Obergrenze - darueber ist es ein Serverstillstand. */
    public static final int HOECHSTE_BLOECKE = 50_000;

    /** Die Grenzen, die ohne eine Zeile in der {@code config.yml} gelten. */
    public static final Rueckweggrenzen VORGABE = new Rueckweggrenzen(
            VORGABE_RADIUS, VORGABE_MINUTEN, VORGABE_BLOECKE);

    public Rueckweggrenzen {
        if (!brauchbarerRadius(hoechsterRadius)) {
            throw new IllegalArgumentException("Ein Radius von " + hoechsterRadius
                    + " Bloecken ist keine Grenze - erlaubt ist 1 bis "
                    + HOECHSTER_RADIUS);
        }
        if (!brauchbareMinuten(hoechsteMinuten)) {
            throw new IllegalArgumentException("Ein Fenster von " + hoechsteMinuten
                    + " Minuten ist keine Grenze - erlaubt ist 1 bis "
                    + HOECHSTE_MINUTEN);
        }
        if (!brauchbareBloecke(hoechsteBloecke)) {
            throw new IllegalArgumentException("Eine Grenze von " + hoechsteBloecke
                    + " Bloecken ist keine - erlaubt ist 1 bis " + HOECHSTE_BLOECKE);
        }
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTER_RADIUS}. */
    public static boolean brauchbarerRadius(long bloecke) {
        return bloecke >= 1L && bloecke <= HOECHSTER_RADIUS;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTE_MINUTEN}. */
    public static boolean brauchbareMinuten(long minuten) {
        return minuten >= 1L && minuten <= HOECHSTE_MINUTEN;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTE_BLOECKE}. */
    public static boolean brauchbareBloecke(long zeilen) {
        return zeilen >= 1L && zeilen <= HOECHSTE_BLOECKE;
    }

    /**
     * Ob dieser getippte Radius erlaubt ist.
     *
     * <p>Die Frage am WERT und nicht am Befehl: So liegt die Grenze an derselben Stelle
     * wie ihre Zahl, und ein zweiter Zweig (etwa einer, der aus dem Manager kommt) kann
     * sie nicht vergessen.
     */
    public boolean erlaubterRadius(int getippt) {
        // NULL IST ERLAUBT und heisst der eine Block in der Mitte - anders als
        // bei hoechsterRadius, wo eine 0 die stille Form von "der Befehl findet
        // nie etwas" waere. Ein Moderator, der genau einen Block zuruecksetzen
        // will, soll dafuer keinen Umweg brauchen.
        return getippt >= 0 && getippt <= hoechsterRadius;
    }

    /** Ob dieses getippte Zeitfenster erlaubt ist. */
    public boolean erlaubteMinuten(int getippt) {
        return getippt >= 1 && getippt <= hoechsteMinuten;
    }

    /**
     * Wie die Grenzen in der Datei stuenden - fuer den Bericht beim Start.
     *
     * <p>Damit der Betreiber im Protokoll sieht, was WIRKLICH gilt, und nicht nur, was er
     * glaubt eingetragen zu haben. Aus den Daten gebaut, nicht abgeschrieben.
     */
    public String wieEingetragen() {
        return "hoechstens " + hoechsterRadius + " Bloecke Radius, "
                + hoechsteMinuten + " Minuten zurueck, " + hoechsteBloecke
                + " Zeilen je Lauf";
    }
}
