package de.freimarken.kern;

/**
 * Die Zahlen des Tagesbonus - was er gibt und wie die Serie ihn wachsen laesst.
 *
 * <p>Dieselbe Bauart wie {@link Marktwerte} und {@link Messwerte}: ein Satz, eine Vorgabe
 * im Programm, und zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Der Bonus ist die kleinste der drei Quellen</b> (Plan 8.2: "Quellen, alle
 * gedeckelt: Berufe mit abnehmendem Ertrag, Verkauf an den Bundesmarkt, Tagesbonus"). Er
 * soll Regelmaessigkeit belohnen und nicht Arbeit ersetzen, darum liegt selbst der
 * Hoechstbetrag weit unter dem, was eine Spielstunde Arbeit bringt (2.000 bis 5.000 Mark
 * nach Plan 8.2).
 *
 * <p><b>Gedeckelt ist er zweifach</b>, und beide Deckel braucht es: einmal am Tag - das
 * besorgt das Buchungsbuch, nicht diese Zahlen - und nach oben durch
 * {@link #hoechstbetrag}. Ohne den zweiten liefe eine lange Serie davon: Bei 100 Mark je
 * Serientag waere der Bonus nach einem Jahr groesser als ein Tagesverdienst, und der
 * Betreiber hat die Zahl eingestellt, als die Serien kurz waren.
 *
 * @param grundbetrag     was der erste Tag einer Serie bringt, in ganzen Mark
 * @param jeSerientag     was jeder weitere Tag der Serie dazulegt, in ganzen Mark
 * @param hoechstbetrag   woran die Serie aufhoert zu wachsen
 * @param reisstAbLuecke  ab welcher Luecke in Tagen die Serie reisst - siehe
 *                        {@link Serienregel}
 * @param mindestMinuten  wie viele TAETIGE Minuten des Tages der Spieler gegeben haben
 *                        muss, bevor der Bonus faellt; 0 heisst "beim Einloggen"
 */
public record Bonuswerte(long grundbetrag, long jeSerientag, long hoechstbetrag,
                         int reisstAbLuecke, long mindestMinuten) {

    public static final String PFAD_GRUNDBETRAG = "tagesbonus.grundbetrag";

    public static final String PFAD_JE_SERIENTAG = "tagesbonus.je-serientag";

    public static final String PFAD_HOECHSTBETRAG = "tagesbonus.hoechstbetrag";

    public static final String PFAD_LUECKE = "tagesbonus.serie-reisst-ab-luecke";

    public static final String PFAD_MINDEST_MINUTEN = "tagesbonus.mindest-minuten";

    /**
     * 500 Mark am ersten Tag - rund zehn Minuten Arbeit.
     *
     * <p>Gerechnet gegen die Rolle: Sichtbar genug, dass das Einloggen sich lohnt, und zu
     * wenig, um einen Arbeitstag zu ersetzen. Bei 2.000 bis 5.000 Mark je Spielstunde aus
     * Arbeit (Plan 8.2) ist das ein Zehntel einer Stunde.
     */
    public static final long VORGABE_GRUNDBETRAG = 500L;

    /** 100 Mark je Serientag - nach sechzehn Tagen ist der Hoechstbetrag erreicht. */
    public static final long VORGABE_JE_SERIENTAG = 100L;

    /**
     * 2.000 Mark - ein Zehntel des Verdienstdeckels.
     *
     * <p>Dieselbe Rechnung wie beim Bundesmarkt, nur strenger: Der Markt verwertet
     * Erarbeitetes und darf ein Viertel sein, der Bonus verlangt gar nichts und bleibt
     * darum bei einem Zehntel. Erreicht wird er am sechzehnten Tag einer Serie - lang
     * genug, dass er sich wie ein Ziel anfuehlt, und kurz genug, dass ein Spieler ihn
     * erlebt.
     */
    public static final long VORGABE_HOECHSTBETRAG = 2_000L;

    /** Die Serie reisst, wer einen Tag verpasst - die strenge und uebliche Lesart. */
    public static final int VORGABE_LUECKE = 2;

    /**
     * 0 Minuten - der Bonus faellt beim EINLOGGEN.
     *
     * <p>So gewollt: Das taegliche Einloggen soll belohnt werden, und eine Wartezeit
     * davor macht aus einer Belohnung eine Auflage. Wer das Einloggen allein nicht
     * belohnen will, setzt hier Minuten ein - und dann zaehlen TAETIGE Minuten aus Teil G
     * und nicht Anwesenheit, denn Anwesenheit ist im Stehen zu haben.
     */
    public static final long VORGABE_MINDEST_MINUTEN = 0L;

    /** Derselbe Hoechstwert wie bei den anderen Geldzahlen - dieselbe Rechnung. */
    public static final long HOECHSTER_BETRAG = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    /** Hoechstens einen Tag lang warten - darueber waere es kein Tagesbonus mehr. */
    public static final long HOECHSTE_MINDEST_MINUTEN = 1_440L;

    public static final Bonuswerte VORGABE = new Bonuswerte(VORGABE_GRUNDBETRAG,
            VORGABE_JE_SERIENTAG, VORGABE_HOECHSTBETRAG, VORGABE_LUECKE,
            VORGABE_MINDEST_MINUTEN);

    public Bonuswerte {
        if (!brauchbarerBetrag(grundbetrag)) {
            throw new IllegalArgumentException("Der Grundbetrag ist " + grundbetrag
                    + " und liegt nicht zwischen 1 und " + HOECHSTER_BETRAG
                    + ". Eine 0 hiesse, dass es keinen Bonus gibt - wer ihn abschalten"
                    + " will, soll das sagen und nicht eine 0 einsetzen.");
        }
        if (jeSerientag < 0L || jeSerientag > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Der Zuschlag je Serientag ist "
                    + jeSerientag + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist erlaubt und heisst: jeder Tag bringt dasselbe.");
        }
        if (!brauchbarerBetrag(hoechstbetrag)) {
            throw new IllegalArgumentException("Der Hoechstbetrag ist " + hoechstbetrag
                    + " und liegt nicht zwischen 1 und " + HOECHSTER_BETRAG + ".");
        }
        // Zwei Werte, die JEDER FUER SICH erlaubt sind und zusammen nicht: Ein
        // Hoechstbetrag unter dem Grundbetrag heisst, dass der erste Tag schon gekuerzt
        // wird - und dann steht in der Datei eine Zahl, die nie gilt. Dieselbe Bauart wie
        // bei Takt und Untaetigkeitsgrenze in Teil C.
        if (hoechstbetrag < grundbetrag) {
            throw new IllegalArgumentException("Der Hoechstbetrag " + hoechstbetrag
                    + " liegt unter dem Grundbetrag " + grundbetrag
                    + ". Dann gilt der Grundbetrag nie, und seine Zeile in der Datei ist"
                    + " eine Angabe ohne Wirkung.");
        }
        if (!Serienregel.brauchbareLuecke(reisstAbLuecke)) {
            throw new IllegalArgumentException("Die Serie kann nicht ab einer Luecke von "
                    + reisstAbLuecke + " reissen - erlaubt ist 2 bis "
                    + Serienregel.HOECHSTE_LUECKE + ".");
        }
        if (!brauchbareMindestMinuten(mindestMinuten)) {
            throw new IllegalArgumentException("Die Mindestzeit ist " + mindestMinuten
                    + " Minuten und liegt nicht zwischen 0 und "
                    + HOECHSTE_MINDEST_MINUTEN + ". Einen ganzen Tag lang zu warten"
                    + " hiesse, dass der Bonus genau am Tageswechsel faellt - also nie.");
        }
    }

    /** Dieselben Werte mit anderem Grundbetrag - fuer die Proben und den Leser. */
    public Bonuswerte mitGrundbetrag(long anderer) {
        return new Bonuswerte(anderer, jeSerientag, hoechstbetrag, reisstAbLuecke,
                mindestMinuten);
    }

    /** Dieselben Werte mit anderem Zuschlag je Serientag. */
    public Bonuswerte mitJeSerientag(long anderer) {
        return new Bonuswerte(grundbetrag, anderer, hoechstbetrag, reisstAbLuecke,
                mindestMinuten);
    }

    /** Dieselben Werte mit anderem Hoechstbetrag. */
    public Bonuswerte mitHoechstbetrag(long anderer) {
        return new Bonuswerte(grundbetrag, jeSerientag, anderer, reisstAbLuecke,
                mindestMinuten);
    }

    /** Dieselben Werte mit anderer Luecke. */
    public Bonuswerte mitLuecke(int andere) {
        return new Bonuswerte(grundbetrag, jeSerientag, hoechstbetrag, andere,
                mindestMinuten);
    }

    /** Dieselben Werte mit anderer Mindestzeit. */
    public Bonuswerte mitMindestMinuten(long andere) {
        return new Bonuswerte(grundbetrag, jeSerientag, hoechstbetrag, reisstAbLuecke,
                andere);
    }

    /** Ob der Bonus erst nach einer Wartezeit faellt - fuer die Startmeldung. */
    public boolean wartetAufTaetigkeit() {
        return mindestMinuten > 0L;
    }

    public static boolean brauchbarerBetrag(long mark) {
        return mark >= 1L && mark <= HOECHSTER_BETRAG;
    }

    public static boolean brauchbareMindestMinuten(long minuten) {
        return minuten >= 0L && minuten <= HOECHSTE_MINDEST_MINUTEN;
    }
}
