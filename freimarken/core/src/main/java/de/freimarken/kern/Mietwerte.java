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
 * @param kaution   was beim Mieten hinterlegt wird. Bei ordentlicher Rueckgabe kommt sie
 *                  zurueck, bei Raeumung wegen Zahlungsausfall nicht (Plan 7.1)
 * @param lizenz    was die Ladenlizenz EINMALIG kostet - danach darf der Spieler immer
 *                  wieder mieten, ohne sie erneut zu zahlen
 * @param dauerTage wie viele Tage ein Abschluss und jede Verlaengerung gelten
 */
public record Mietwerte(long miete, long kaution, long lizenz, int dauerTage) {

    public static final String PFAD_MIETE = "laeden.miete-je-tag";

    public static final String PFAD_KAUTION = "laeden.kaution";

    public static final String PFAD_LIZENZ = "laeden.lizenz";

    public static final String PFAD_DAUER = "laeden.mietdauer-tage";

    /** 500 Mark am Tag - die Zahl aus Plan 7.1. */
    public static final long VORGABE_MIETE = 500L;

    /** 5.000 Mark Kaution - zehn Mietstage, also ein sichtbarer Verlust bei Raeumung. */
    public static final long VORGABE_KAUTION = 5_000L;

    /** 50.000 Mark fuer die Lizenz - die einzige Zahl der Tafel mit [belegt]. */
    public static final long VORGABE_LIZENZ = 50_000L;

    /**
     * 30 Tage - lang genug, dass niemand alle zwei Tage verlaengern muss, und kurz
     * genug, dass ein verlassener Laden nicht ein Jahr lang blockiert steht.
     *
     * <p>Die Dauer ist NICHT der Zahlungstakt: Gezahlt wird taeglich (Plan 7.1, Teil C).
     * Die Dauer sagt, wann der Vertrag ohne Zutun endet - das ist der Griff gegen den
     * Spieler, der aufhoert zu spielen, aber noch Geld auf dem Konto hat.
     */
    public static final int VORGABE_DAUER = 30;

    /** Dieselbe Obergrenze wie bei allen Geldzahlen - dieselbe Rechnung. */
    public static final long HOECHSTER_BETRAG = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    public static final Mietwerte VORGABE = new Mietwerte(VORGABE_MIETE, VORGABE_KAUTION,
            VORGABE_LIZENZ, VORGABE_DAUER);

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
        return new Mietwerte(andere, kaution, lizenz, dauerTage);
    }

    /** Dieselben Werte mit anderer Kaution. */
    public Mietwerte mitKaution(long andere) {
        return new Mietwerte(miete, andere, lizenz, dauerTage);
    }

    /** Dieselben Werte mit anderer Lizenz. */
    public Mietwerte mitLizenz(long andere) {
        return new Mietwerte(miete, kaution, andere, dauerTage);
    }

    /** Dieselben Werte mit anderer Dauer. */
    public Mietwerte mitDauer(int andere) {
        return new Mietwerte(miete, kaution, lizenz, andere);
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
