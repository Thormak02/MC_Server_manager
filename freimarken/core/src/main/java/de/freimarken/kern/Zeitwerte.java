package de.freimarken.kern;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Die drei Werte, aus denen Spielzeit gemessen wird - ein Schnappschuss wie
 * {@link Preisstaffel}.
 *
 * <p>Ein WERT und keine drei einzelnen Felder, aus demselben Grund wie bei den Preisen: Wer
 * einen Takt abrechnet, braucht Zone, Taktlaenge und Untaetigkeitsgrenze in sich stimmig.
 * Mit drei Feldern koennte ein Neuladen mitten in der Rechnung die Grenze tauschen und die
 * Protokollzeile danach die alte nennen.
 *
 * <p><b>Warum diese drei in {@link Wirtschaftswerte} liegen und nicht neben ihm.</b> Die
 * Zone und die Taktlaenge sind NICHT neuladbar (siehe {@link Wirtschaftsstand#uebernimm}) -
 * und genau deshalb koennte man sie wie {@code datenbank.url} oder {@code reisen.*} direkt
 * in {@code onEnable} lesen und draussen halten. Der Unterschied ist, WO sie gebraucht
 * werden: {@code datenbank.url} wird einmal beim Start benutzt, die Zone bei jedem Takt, bei
 * jeder Spielzeitanzeige und in {@code /mark verlauf}. Ein Wert, der an vielen Stellen im
 * Spiel gebraucht wird, gehoert in die eine Zelle, die beim Gebrauch gefragt wird - sonst
 * halten vier Stellen ihre eigene Kopie, und "nur EINE Quelle fuer die Zone" ist nach dem
 * naechsten Bauschritt nicht mehr wahr. Dasselbe gilt fuer {@code kasse.startguthaben}: auch
 * es ist nicht neuladbar und liegt trotzdem hier, weil die Kassen es bei jeder Abfrage
 * brauchen. Nicht neuladbar zu sein ist also kein Grund, draussen zu liegen; der Grund ist
 * allein, ob der Wert nur beim Verdrahten oder waehrend des Spiels gebraucht wird.
 *
 * <p>Die {@link #VORGABE} steht im PROGRAMM und nicht nur in der mitgelieferten Datei -
 * dieselbe Begruendung wie bei {@link Wirtschaftswerte#VORGABE}.
 *
 * <p>Zone und Uhr bleiben zwei getrennte Dinge: Hier steht nur die Zone, der Zeitpunkt kommt
 * aus einer eingespeisten {@link java.time.Clock}. Ein {@code Clock.withZone(zone)} waere mit
 * der einzigen Pruefuhr des Projekts nicht pruefbar, denn die liefert fest UTC. Dass die Zone
 * als eigener Wert herumgereicht wird, macht {@code Verlaufszeile.text(Buchung, ZoneId)}
 * schon vor.
 *
 * <p><b>Die Untaetigkeitsgrenze darf nicht kuerzer sein als der Takt.</b> Die Grenze ist eine
 * STUFE und kein Schnitt: Reisst sie, faellt der GANZE Takt weg und nicht nur der untaetige
 * Teil davon - {@link Spielzeitregel} gibt dann {@code UNTAETIG} mit 0 Sekunden zurueck. Bei
 * {@code takt-sekunden: 600} und {@code untaetig-minuten: 1} wirft ein Takt deshalb 540 aktiv
 * gespielte Sekunden weg, und zwar stumm: Ein verworfener Takt ist Alltag und schreibt keine
 * Protokollzeile. Darum verbietet der Konstruktor dieses Paar, und
 * {@link #brauchbareGrenzeZumTakt} laesst den Leser der Einstellungsdatei vorher fragen,
 * damit er klagen und auf die Vorgabe zurueckfallen kann, statt den Konstruktor werfen zu
 * lassen.
 *
 * <p>Diese Zusage deckt genau EINE Taktlaenge - mehr kann sie nicht, denn die Stufe trifft,
 * was der Aufrufer ihr als Spanne vorlegt. Dass eine Spanne nie laenger wird, ist darum
 * Pflicht dessen, der sie bildet: Spielzeitbuch im paper-Modul rechnet seine Takte auch dann
 * aus, wenn es sie nach einem Fehlschlag der Ablage nicht schreibt. Liesse es die Marke
 * stehen, waere diese Zusage fuer die Dauer der Sperre falsch, und ein einziger untaetiger
 * Takt warf mehrere Taktlaengen aktiv gespielter Zeit weg.
 *
 * <p>Die andere denkbare Abhilfe - nur den untaetigen TEIL der Spanne abzuziehen - waere
 * falsch, und zwar nachpruefbar: Gemessen wird mit {@code Player.getIdleDuration()}, und das
 * nennt allein den AUGENBLICKLICHEN Stand der Untaetigkeit, nicht ihren Verlauf innerhalb des
 * Takts. Wer 60 s Untaetigkeit abliest, weiss nicht, ob der Spieler eine Minute am Ende
 * stillstand oder zwischendurch dreimal zwanzig Sekunden. Ein Abzug gaebe also eine
 * Genauigkeit vor, die die Messgroesse nicht hat - und aus der zugesagten Stufe wuerde genau
 * der Schnitt, den die Einstellungsdatei ausschliesst.
 *
 * @param zone         die Zone, in der ein Tag beginnt und endet - die Tagesgrenze. Nicht
 *                     UTC: die Datenbanksitzung steht auf UTC, und dort ist zwischen 00:00
 *                     und 02:00 Ortszeit noch der Vortag
 * @param takt         in welchem Abstand die gemessene Zeit fortgeschrieben wird. Entscheidet
 *                     NUR, wie oft geschrieben wird, nicht wie viel gezaehlt wird - gerechnet
 *                     wird immer die Differenz zweier Uhrablesungen. GANZE Sekunden
 * @param untaetigNach ab welcher Untaetigkeit ein Takt nicht mehr zaehlt; {@link Duration#ZERO}
 *                     heisst: Abwesenheit zaehlt mit. GANZE Minuten, und nicht kuerzer als
 *                     der Takt
 */
public record Zeitwerte(ZoneId zone, Duration takt, Duration untaetigNach) {

    /**
     * Die Schluessel in der Einstellungsdatei - hier und nicht an jeder Stelle, die klagt.
     *
     * <p>Dieselbe Begruendung wie bei {@link Wirtschaftswerte#PFAD_GRUNDPREIS}: Der Leser
     * nennt den Pfad in seiner Klage, der Befehl in der Zeile "alt -&gt; neu" und der Block
     * dessen, was nicht angefasst wird. Liefen sie auseinander, suchte der Betreiber an der
     * falschen Zeile.
     */
    public static final String PFAD_ZONE = "zeit.zone";

    public static final String PFAD_TAKT = "zeit.takt-sekunden";

    public static final String PFAD_UNTAETIG = "zeit.untaetig-minuten";

    /** Die ausgelieferte Tagesgrenze. */
    public static final ZoneId VORGABE_ZONE = ZoneId.of("Europe/Berlin");

    /** Der ausgelieferte Taktabstand in Sekunden. */
    public static final long VORGABE_TAKT_SEKUNDEN = 60L;

    /** Die ausgelieferte Untaetigkeitsgrenze in Minuten. */
    public static final long VORGABE_UNTAETIG_MINUTEN = 10L;

    /**
     * Der kuerzeste erlaubte Takt in Sekunden.
     *
     * <p>Darunter schreibt der Takt oefter, als jede Anzeige unterscheidet - zehn Schreibzuege
     * je Minute und Spieler fuer eine Zahl, die in Minuten angezeigt wird.
     */
    public static final long KUERZESTER_TAKT_SEKUNDEN = 10L;

    /**
     * Der laengste erlaubte Takt in Sekunden.
     *
     * <p>Darueber kostet ein Absturz mehr als zehn Minuten je anwesendem Spieler. Der Takt ist
     * damit die eine Zahl, mit der der Betreiber Schreiblast gegen Verlust bei einem Absturz
     * tauscht: bei 60 verliert ein Absturz bis zu 1 Minute je Spieler, bei 600 bis zu 10.
     *
     * <p>Das gilt, solange die Ablage annimmt. Scheitert ein Schreibzug, setzt der Takt das
     * Schreiben mehrere Schlaege lang aus - gerechnet wird weiter, geschrieben nicht -, und ein
     * Absturz in diesem Fenster kostet ein Mehrfaches dieser Zusage. Wie viele Schlaege das
     * sind, entscheidet {@code Spielzeitbuch.AUSSETZER_NACH_FEHLSCHLAG} im Paper-Modul; wer die
     * Zahl dort aendert, aendert diese Zusage mit - und die gleichlautende im Absatz zu
     * {@link #PFAD_TAKT} in der ausgelieferten {@code config.yml}.
     */
    public static final long LAENGSTER_TAKT_SEKUNDEN = 600L;

    /**
     * Die laengste erlaubte Untaetigkeitsgrenze in Minuten - ein voller Tag.
     *
     * <p>Darueber waere die Regel ohnehin aus, und dafuer gibt es die 0.
     */
    public static final long LAENGSTE_UNTAETIGKEIT_MINUTEN = 1440L;

    /** Genau das, was ohne Einstellungsdatei gelten wuerde. */
    public static final Zeitwerte VORGABE = new Zeitwerte(VORGABE_ZONE,
            Duration.ofSeconds(VORGABE_TAKT_SEKUNDEN),
            Duration.ofMinutes(VORGABE_UNTAETIG_MINUTEN));

    public Zeitwerte {
        if (zone == null) {
            throw new IllegalArgumentException("Ohne Zone gibt es keine Tagesgrenze");
        }
        if (takt == null || untaetigNach == null) {
            throw new IllegalArgumentException("Takt und Untaetigkeitsgrenze sind Pflicht");
        }
        // GANZE Sekunden, GANZE Minuten - und zwar hier und nicht erst beim Lesen der Datei.
        // Sonst haelt dieser Satz zwei Darstellungen desselben Wertes, von denen nur die
        // abgeschnittene geprueft und angezeigt wird: Duration.ofSeconds(30) als Grenze kam
        // durch, weil toMinutes() 0 ergibt, und danach widersprachen sich
        // untaetigkeitZaehltMit() (die Regel ist AN), untaetigMinuten() (0) und der
        // Spielertext ("gezaehlt wird bis 0 min"). Dasselbe beim Takt: 10_400 Millisekunden
        // bestanden brauchbarerTakt(10), die Klemme rechnete danach mit 10 x 10,4 s und der
        // Zeitgeber mit 200 Ticks, also 10 s. Zwei Rechnungen aus einem Wert.
        if (takt.getNano() != 0) {
            throw new IllegalArgumentException("Ein Takt wird in ganzen Sekunden gemessen,"
                    + " hier bleibt ein Rest: " + takt);
        }
        if (!brauchbarerTakt(takt.toSeconds())) {
            throw new IllegalArgumentException("Takt " + takt.toSeconds()
                    + " s liegt nicht zwischen " + KUERZESTER_TAKT_SEKUNDEN + " und "
                    + LAENGSTER_TAKT_SEKUNDEN);
        }
        if (untaetigNach.getNano() != 0 || untaetigNach.toSeconds() % 60L != 0L) {
            throw new IllegalArgumentException("Eine Untaetigkeitsgrenze wird in ganzen"
                    + " Minuten gemessen, hier bleibt ein Rest: " + untaetigNach);
        }
        if (!brauchbareUntaetigkeit(untaetigNach.toMinutes())) {
            throw new IllegalArgumentException("Untaetigkeitsgrenze "
                    + untaetigNach.toMinutes() + " min liegt nicht zwischen 0 und "
                    + LAENGSTE_UNTAETIGKEIT_MINUTEN);
        }
        // Die Querpruefung zuletzt, damit ihre Meldung nicht eine Zahl nennt, die schon
        // einzeln unbrauchbar ist.
        if (!brauchbareGrenzeZumTakt(takt.toSeconds(), untaetigNach.toMinutes())) {
            throw new IllegalArgumentException("Untaetigkeitsgrenze "
                    + untaetigNach.toMinutes() + " min ist kuerzer als der Takt von "
                    + takt.toSeconds() + " s. Die Grenze ist eine Stufe und kein Schnitt:"
                    + " Dann faellt der GANZE Takt weg, auch die aktiv gespielte Zeit darin."
                    + " Entweder " + PFAD_UNTAETIG + " mindestens auf "
                    + Math.ceilDiv(takt.toSeconds(), 60L) + " setzen oder " + PFAD_TAKT
                    + " kuerzen; 0 schaltet die Regel ganz ab");
        }
    }

    /**
     * Ob aus dieser Zahl ein Takt werden kann.
     *
     * <p>Oeffentlich und auf {@code long}, aus demselben Grund wie
     * {@link Wirtschaftswerte#brauchbareAnzahl}: Die Einstellungsdatei wird an derselben
     * Regel geprueft, nach der hier gerechnet wird, und zwar an der getippten Zahl und
     * nicht an ihren unteren 32 Bit.
     */
    public static boolean brauchbarerTakt(long sekunden) {
        return sekunden >= KUERZESTER_TAKT_SEKUNDEN && sekunden <= LAENGSTER_TAKT_SEKUNDEN;
    }

    /**
     * Ob aus dieser Zahl eine Untaetigkeitsgrenze werden kann.
     *
     * <p>0 ist erlaubt und heisst ausdruecklich: Abwesenheit zaehlt mit - der ehrliche
     * Ausschalter fuer die Regel. Das ist bewusst anders als bei
     * {@link Wirtschaftswerte#brauchbareAnzahl}, wo eine 0 verboten ist: Dort hiesse 0
     * "niemand darf eine Parzelle", also etwas, das niemand tippen wollte. Hier hiesse eine
     * verbotene 0, dass es keinen Weg gibt, die Regel abzuschalten - und dann baut sich der
     * Betreiber den Ausschalter selbst, indem er 1440 eintraegt. Eine 0, die etwas Sinnvolles
     * bedeutet, ist besser als eine Zahl, die als Umweg benutzt wird.
     */
    public static boolean brauchbareUntaetigkeit(long minuten) {
        return minuten >= 0 && minuten <= LAENGSTE_UNTAETIGKEIT_MINUTEN;
    }

    /**
     * Ob diese Untaetigkeitsgrenze zu diesem Takt passt - die eine Frage, die keiner der
     * beiden Werte allein beantworten kann.
     *
     * <p>Beide Zahlen koennen in ihrem Bereich liegen und zusammen trotzdem Spielzeit
     * vernichten: Die Grenze ist eine STUFE und kein Schnitt, also faellt bei 600 s Takt und
     * 1 min Grenze der GANZE Takt weg, sobald der Spieler die letzte Minute stillsteht - 540
     * aktiv gespielte Sekunden, ohne Protokollzeile. Die ausfuehrliche Begruendung samt dem
     * Grund, warum nicht nur der untaetige Teil abgezogen wird, steht im Klassenkopf.
     *
     * <p>Oeffentlich und auf {@code long}, aus demselben Grund wie {@link #brauchbarerTakt}:
     * Der Leser der Einstellungsdatei MUSS diese Frage auf den roh gelesenen Zahlen stellen
     * koennen, damit er klagen und auf die Vorgabe zurueckfallen kann. Den Konstruktor werfen
     * zu lassen waere der Fehler, gegen den der ganze Leser gebaut ist: Eine getippte Zahl
     * darf das Plugin nicht am Starten hindern.
     *
     * <p>Diese Frage ERGAENZT {@link #brauchbarerTakt} und {@link #brauchbareUntaetigkeit}
     * und ersetzt sie nicht - der Leser fragt alle drei.
     *
     * @param taktSekunden    der Taktabstand in Sekunden
     * @param untaetigMinuten die Untaetigkeitsgrenze in Minuten; 0 heisst "Regel aus" und
     *                        passt deshalb zu jedem Takt
     */
    public static boolean brauchbareGrenzeZumTakt(long taktSekunden, long untaetigMinuten) {
        if (untaetigMinuten == 0L) {
            return true;
        }
        // Aufgerundet, weil die Grenze nur in ganzen Minuten einstellbar ist: Zu einem Takt
        // von 90 s genuegen 2 Minuten, 1 Minute nicht. ceilDiv und nicht (x + 59) / 60,
        // damit auch eine unsinnig grosse getippte Zahl nicht ueberlaeuft.
        return untaetigMinuten >= Math.ceilDiv(taktSekunden, 60L);
    }

    /** Der Takt in Sekunden - fuer Protokoll und Klage, damit es nicht drei Schreibweisen gibt. */
    public long taktSekunden() {
        return takt.toSeconds();
    }

    /** Die Untaetigkeitsgrenze in Minuten, aus demselben Grund. */
    public long untaetigMinuten() {
        return untaetigNach.toMinutes();
    }

    /** Ob Untaetigkeit mitzaehlt, also ob die Regel ausgeschaltet ist. */
    public boolean untaetigkeitZaehltMit() {
        return untaetigNach.isZero();
    }
}
