package de.freimarken.kern;

/**
 * Die Zahlen der Ladenmiete - was ein Laden kostet und wie lange ein Vertrag laeuft.
 *
 * <p>Dieselbe Bauart wie {@link Bonuswerte} und {@link Messwerte}: ein Satz, eine Vorgabe
 * im Programm, zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Die Ladenmiete ist die staerkste Geldsenke des Servers</b> (Plan 7.1: "laufend,
 * sichtbar, freiwillig"), und darum ist sie die einzige dieser Zahlen, die den laufenden
 * Vertrag NICHT mehr erreicht. Plan 1943 nennt sie "anpassbar mit Bestandsschutz - gilt
 * nur fuer NEUE Vertraege"; umgesetzt ist das in {@link Mietvertrag#miete()}, der seine
 * eigene Miete traegt. Was hier steht, ist der Satz fuer den NAECHSTEN Abschluss.
 *
 * <p>Die drei Betraege kommen aus der Tafel in Plan 7.1: Miete 500 am Tag [empfohlen],
 * Kaution 5.000 [empfohlen], Lizenz 50.000 einmalig [belegt].
 *
 * @param miete     was ein Ladentag kostet, in ganzen Mark
 * @param kaution   was beim Mieten hinterlegt wird. Beim Kuendigen und beim Ablaufen
 *                  kommt sie zurueck, bei Raeumung wegen Zahlungsausfall nicht
 *                  (Plan 7.1)
 * @param lizenz    was die Ladenlizenz EINMALIG kostet - danach darf der Spieler immer
 *                  wieder mieten, ohne sie erneut zu zahlen
 * @param dauerTage wie viele Tage ein Abschluss und jede Verlaengerung gelten
 * @param ausfaelleBisRaeumung bei wie vielen Tagen ohne Zahlung IN FOLGE der Laden
 *                  geraeumt wird. Bei 3 also: erster Ausfall warnt, zweiter warnt,
 *                  dritter raeumt - es gibt zwei Warnungen und nicht drei. Die Zahl
 *                  heisst nach dem, was sie zaehlt, und nicht nach der Wirkung; "drei
 *                  Warnungen" waere die haeufigste Fehllesung einer Einstellung, die
 *                  darueber entscheidet, wann jemand seinen Laden verliert
 */
public record Mietwerte(long miete, long kaution, long lizenz, int dauerTage,
                        int ausfaelleBisRaeumung) {

    public static final String PFAD_MIETE = "laeden.miete-je-tag";

    public static final String PFAD_KAUTION = "laeden.kaution";

    public static final String PFAD_LIZENZ = "laeden.lizenz";

    public static final String PFAD_DAUER = "laeden.mietdauer-tage";

    public static final String PFAD_AUSFAELLE = "laeden.ausfaelle-bis-raeumung";

    /** 500 Mark am Tag - die Zahl aus Plan 7.1. */
    public static final long VORGABE_MIETE = 500L;

    /** 5.000 Mark Kaution - zehn Mietstage, also ein sichtbarer Verlust bei Raeumung. */
    public static final long VORGABE_KAUTION = 5_000L;

    /** 50.000 Mark fuer die Lizenz - die einzige Zahl der Tafel mit [belegt]. */
    public static final long VORGABE_LIZENZ = 50_000L;

    /**
     * 7 Tage [entschieden 08.11.2026 - vorher 30].
     *
     * <p><b>Die Dauer ist NICHT der Zahlungstakt und auch nicht der Preis.</b> Gezahlt
     * wird taeglich (Plan 7.1, Teil C), und die Miete steht je TAG in der Datei: Ein
     * Abschluss ueber 7 Tage kostet dieselben 500 Mark am Tag wie einer ueber 30, und
     * Kaution und Lizenz aendern sich nicht. Kuerzer heisst also nicht guenstiger,
     * sondern nur: Der Vertrag endet frueher OHNE ZUTUN.
     *
     * <p>Genau darum ist die Zahl klein. Sie ist der Griff gegen den Spieler, der
     * aufhoert zu spielen und noch Geld auf dem Konto hat - der zahlt weiter und haelt
     * seinen Stand, bis die Dauer ablaeuft. Bei 30 Tagen stand eine Markthalle mit
     * wenigen Staenden einen Monat lang voll mit Laeden, in denen nichts passiert; bei 7
     * wird ein verlassener Stand nach einer Woche wieder mietbar. Wer spielt, merkt die
     * Zahl nicht: Verlaengern kostet nichts und geht ab dem zweiten Tag.
     *
     * <p>Der Preis dafuer ist genannt und gewollt: Wer eine Woche nicht hereinschaut,
     * verliert seinen Laden. Weg ist deswegen nichts - die Kaution kommt bei Ablauf
     * zurueck, und seine Ware liegt im Abholfach (Teil C).
     */
    public static final int VORGABE_DAUER = 7;

    /**
     * Drei Tage ohne Zahlung, dann ist der Laden weg.
     *
     * <p>Gerechnet gegen die Mietdauer von 7 Tagen: Drei Ausfaelle sind knapp die Haelfte
     * eines Vertrags, also merkt ein Spieler, der jeden zweiten Tag hereinschaut, die
     * Warnung noch rechtzeitig. Eine 1 raeumte den Laden am ersten Tag mit knapper Kasse,
     * und zwar ohne dass der Spieler die Warnung je gelesen haette - Warnung und Raeumung
     * fielen in denselben Augenblick. Eine 7 waere die ganze Mietdauer: Dann raeumt
     * immer der Ablauf, und der Verzug waere eine Regel ohne Wirkung.
     *
     * <p>Wer nie online ist, verliert seinen Stand so nach drei Tagen statt nach sieben -
     * und das ist der Zweck. Verloren ist dabei nichts: Die Ware liegt im Abholfach.
     */
    public static final int VORGABE_AUSFAELLE = 3;

    /** Dieselbe Obergrenze wie bei allen Geldzahlen - dieselbe Rechnung. */
    public static final long HOECHSTER_BETRAG = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    public static final Mietwerte VORGABE = new Mietwerte(VORGABE_MIETE, VORGABE_KAUTION,
            VORGABE_LIZENZ, VORGABE_DAUER, VORGABE_AUSFAELLE);

    public Mietwerte {
        if (miete < 1L || miete > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Die Miete ist " + miete
                    + " und liegt nicht zwischen 1 und " + HOECHSTER_BETRAG
                    + ". Eine 0 waere ein Laden, den der taegliche Lauf nie beruehrt -"
                    + " wer Laeden verschenken will, nimmt eine 1.");
        }
        if (kaution < 0L || kaution > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Die Kaution ist " + kaution
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist erlaubt und heisst: ohne Kaution.");
        }
        if (lizenz < 0L || lizenz > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Die Lizenz ist " + lizenz
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist erlaubt und heisst: wer das Recht hat, darf mieten.");
        }
        if (!Mietregel.brauchbareDauer(dauerTage)) {
            throw new IllegalArgumentException("Eine Mietdauer von " + dauerTage
                    + " Tagen ist keine - erlaubt ist 1 bis " + Mietregel.HOECHSTE_DAUER
                    + ".");
        }
        if (!Mietregel.brauchbareSchranke(ausfaelleBisRaeumung)) {
            throw new IllegalArgumentException("Eine Schranke von " + ausfaelleBisRaeumung
                    + " Ausfaellen ist keine - erlaubt ist 1 bis "
                    + Mietregel.HOECHSTE_SCHRANKE + ". Bei 0 waere jeder Vertrag"
                    + " raeumungsreif, auch der heute abgeschlossene.");
        }
        // Zwei Werte, die JEDER FUER SICH erlaubt sind und zusammen nicht - dieselbe
        // Bauart wie Grund- und Hoechstbetrag in Bonuswerte.
        //
        // Die Kaution hat genau EINE Aufgabe: Sie soll das Weglaufen teurer machen als
        // das Kuendigen. Liegt sie unter einer Tagesmiete, dann verliert der Spieler, der
        // seinen Laden einfach verfallen laesst, weniger als einen Miettag - und die
        // Raeumung ist der billigere Weg. Dann steht in der Datei eine Kaution, die genau
        // das Gegenteil von dem tut, wofuer sie da ist.
        //
        // Die 0 bleibt erlaubt: "keine Kaution" ist eine Entscheidung und kein Versehen.
        if (!brauchbaresPaar(kaution, miete)) {
            throw new IllegalArgumentException("Die Kaution " + kaution
                    + " liegt unter der Tagesmiete " + miete
                    + ". Dann kostet das Weglaufen weniger als ein Miettag, und die"
                    + " Raeumung wird der billigere Weg - die Kaution bewirkt das"
                    + " Gegenteil. Erlaubt ist 0 (ohne Kaution) oder mindestens "
                    + miete + ".");
        }
    }

    /** Dieselben Werte mit anderer Miete - fuer die Proben und den Leser. */
    public Mietwerte mitMiete(long andere) {
        return new Mietwerte(andere, kaution, lizenz, dauerTage, ausfaelleBisRaeumung);
    }

    /** Dieselben Werte mit anderer Kaution. */
    public Mietwerte mitKaution(long andere) {
        return new Mietwerte(miete, andere, lizenz, dauerTage, ausfaelleBisRaeumung);
    }

    /** Dieselben Werte mit anderer Lizenz. */
    public Mietwerte mitLizenz(long andere) {
        return new Mietwerte(miete, kaution, andere, dauerTage, ausfaelleBisRaeumung);
    }

    /** Dieselben Werte mit anderer Dauer. */
    public Mietwerte mitDauer(int andere) {
        return new Mietwerte(miete, kaution, lizenz, andere, ausfaelleBisRaeumung);
    }

    /** Dieselben Werte mit anderer Ausfallschranke. */
    public Mietwerte mitAusfaellen(int andere) {
        return new Mietwerte(miete, kaution, lizenz, dauerTage, andere);
    }

    /** Ob eine Kaution hinterlegt wird - fuer die Meldungen und die Startzeile. */
    public boolean mitKaution() {
        return kaution > 0L;
    }

    /** Ob die Lizenz Geld kostet - fuer die Meldungen und die Startzeile. */
    public boolean kostetLizenz() {
        return lizenz > 0L;
    }

    /** Was ein ganzer Mietzeitraum an Miete kostet - fuer die Auskunft vor dem Mieten. */
    public long vollerZeitraum() {
        return Mietregel.vollerZeitraum(miete, dauerTage);
    }

    /**
     * Was der Spieler an Miete verliert, bevor geraeumt wird.
     *
     * <p>Fuer die Startzeile und fuer die Rechenprobe: Wer hier "1.500 Mark bis zur
     * Raeumung" neben einer Kaution von 500 liest, sieht, dass die Kaution das Weglaufen
     * nicht teurer macht als das Zahlen - und keine Zahlengrenze sieht das.
     */
    public long verlustBisRaeumung() {
        return Math.multiplyExact(miete, (long) ausfaelleBisRaeumung);
    }

    /** Was ein Abschluss im Augenblick kostet: Kaution, und die Lizenz nur beim ersten. */
    public long abschlusskosten(boolean lizenzSchonBezahlt) {
        return lizenzSchonBezahlt ? kaution : kaution + lizenz;
    }

    /**
     * Ob Kaution und Miete zusammen taugen.
     *
     * <p>Eigene Methode und nicht nur ein Zweig im Pruefteil: Der Leser der
     * {@code config.yml} muss dasselbe Paar pruefen koennen, BEVOR er den Satz baut -
     * sonst waere seine einzige Art, die Frage zu stellen, das Abfangen einer Ausnahme.
     */
    public static boolean brauchbaresPaar(long kaution, long miete) {
        return kaution == 0L || kaution >= miete;
    }
}
