package de.freimarken.kern;

/**
 * Alle Stammwerte der Wirtschaft in einem Stueck - ein Schnappschuss.
 *
 * <p>Unveraenderlich und als EIN Satz und nicht als acht einzelne Werte: Wer rechnet,
 * sieht immer einen in sich stimmigen Stand, auch wenn mitten in der Rechnung neu geladen
 * wird. Mit acht Feldern koennte eine Abbuchung den alten Preis nehmen und die Meldung
 * danach den neuen nennen - ein Fehlerbild, das niemand meldet, weil es nur aussieht wie
 * ein Rechenfehler.
 *
 * <p>Die {@link #VORGABE} steht im PROGRAMM und nicht nur in der mitgelieferten Datei.
 * {@code saveDefaultConfig()} schreibt eine vorhandene Datei nie um; auf einem Server, der
 * schon lief, fehlen neue Zeilen also einfach. Der Rueckfall muss deshalb da sein, wo er
 * nicht fehlen kann.
 *
 * @param preise        was Parzellen und das Zusammenlegen kosten
 * @param startguthaben was ein Spieler beim Eroeffnen seines Kontos mitbringt
 * @param hoechstzahl   wie viele Parzellen ohne besonderes Recht erlaubt sind
 * @param gruppenFelder wie viele Felder eine Gruppe ohne besonderes Recht haben darf
 * @param rangWerbung   die Zeile, die angehaengt wird, wenn ein Rang die Grenze setzt
 * @param zeit          Tagesgrenze, Takt und Untaetigkeitsgrenze fuer die Spielzeit; warum
 *                      diese drei hier liegen und nicht daneben, steht in {@link Zeitwerte}
 * @param berufe        Wechselgebuehr, Sperrfrist und die Zahl der Plaetze je Rolle; sie
 *                      liegen hier aus demselben Grund wie die Zeitwerte - sie werden bei
 *                      JEDEM Berufsbefehl gebraucht, siehe {@link Berufswerte}
 * @param verdienst     Tagesdeckel, abnehmender Ertrag und die ganze Lohntafel. Hier aus
 *                      demselben Grund wie die zwei davor - und mit dem schaerfsten: Die
 *                      Tafel wird bei JEDEM abgebauten und JEDEM gesetzten Block
 *                      gefragt. Zwei Zellen fuer Lohn und Minderung koennten mitten in
 *                      einer Rechnung auseinanderlaufen
 * @param markt         Tagesdeckel und Sortiment des Bundesmarktes. Hier aus demselben
 *                      Grund wie die drei davor: Ein Neuladen tauscht den GANZEN Satz als
 *                      ein Stueck, und wer mitten in einem Verkauf einen neuen Preis aus
 *                      einem alten Deckel bekaeme, verkaufte zu etwas, das niemand
 *                      eingestellt hat
 * @param messung       woran die Messstelle Taetigkeit erkennt. Hier und nicht
 *                      daneben, aus demselben Grund wie bei Zeit, Berufen und
 *                      Markt: Ein Neuladen tauscht den ganzen Satz als EIN Stueck
 */
public record Wirtschaftswerte(Preisstaffel preise, long startguthaben, int hoechstzahl,
                               int gruppenFelder, String rangWerbung, Zeitwerte zeit,
                               Berufswerte berufe, Verdienstwerte verdienst,
                               Marktwerte markt, Messwerte messung) {

    /**
     * Die Schluessel in der Einstellungsdatei - hier und nicht an jeder Stelle, die klagt.
     *
     * <p>Drei Stellen nennen denselben Pfad: der Leser in seiner Klage, der Befehl in der
     * Zeile "alt -&gt; neu" und der Block dessen, was nicht angefasst wird. Liefen sie
     * auseinander, nennte die Klage einen Schluessel, den es in der Datei nicht gibt -
     * der Betreiber suchte dann an der falschen Zeile.
     */
    public static final String PFAD_GRUNDPREIS = "wirtschaft.parzelle.grundpreis";

    public static final String PFAD_ZUSAMMENLEGUNG = "wirtschaft.parzelle.zusammenlegung-je-feld";

    public static final String PFAD_BESTAETIGT = "wirtschaft.sprung-bestaetigt";

    public static final String PFAD_HOECHSTZAHL = "parzellen.hoechstzahl";

    public static final String PFAD_GRUPPEN_FELDER = "parzellen.gruppe-hoechstzahl";

    public static final String PFAD_RANG_WERBUNG = "hinweise.rang-werbung";

    public static final String PFAD_STARTGUTHABEN = "kasse.startguthaben";

    /** Das ausgelieferte Startguthaben. */
    public static final long VORGABE_STARTGUTHABEN = 50_000L;

    /** Die ausgelieferte Parzellenzahl ohne besonderes Recht. */
    public static final int VORGABE_HOECHSTZAHL = 4;

    /** Die ausgelieferte Felderzahl je Gruppe ohne besonderes Recht. */
    public static final int VORGABE_GRUPPEN_FELDER = 4;

    /** Die ausgelieferte Rangwerbung. */
    public static final String VORGABE_RANG_WERBUNG = "Mehr erlaubt ein hoeherer Rang.";

    /**
     * Das hoechste Startguthaben, mit dem spaetere Gutschriften nicht ueberlaufen.
     *
     * <p>Die Haelfte des Zahlenraums bleibt fuer alles, was ein Spieler noch verdient.
     * Keine Balancegrenze - die macht die Faktor-2-Rueckfrage, und die faengt jeden
     * realistischen Tippfehler lange vorher.
     */
    public static final long HOECHSTES_STARTGUTHABEN = Long.MAX_VALUE / 2;

    /** Genau das, was ohne Einstellungsdatei gelten wuerde. */
    public static final Wirtschaftswerte VORGABE = new Wirtschaftswerte(Preisstaffel.VORGABE,
            VORGABE_STARTGUTHABEN, VORGABE_HOECHSTZAHL, VORGABE_GRUPPEN_FELDER,
            VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE, Berufswerte.VORGABE,
            Verdienstwerte.VORGABE, Marktwerte.VORGABE, Messwerte.VORGABE);

    public Wirtschaftswerte {
        if (preise == null) {
            throw new IllegalArgumentException("Ohne Preisstaffel gibt es keine Wirtschaft");
        }
        if (zeit == null) {
            throw new IllegalArgumentException("Ohne Zeitwerte gibt es keine Tagesgrenze");
        }
        if (berufe == null) {
            throw new IllegalArgumentException("Ohne Berufswerte gibt es keine"
                    + " Wechselgebuehr und keine Sperrfrist");
        }
        if (verdienst == null) {
            throw new IllegalArgumentException("Ohne Verdienstwerte gibt es keinen Lohn"
                    + " und keinen Tagesdeckel");
        }
        if (markt == null) {
            throw new IllegalArgumentException("Ohne Marktwerte gibt es keinen Ankauf"
                    + " und keinen zweiten Tagesdeckel");
        }
        if (messung == null) {
            throw new IllegalArgumentException("Ohne Messwerte gibt es keine Messstelle");
        }
        if (!brauchbaresStartguthaben(startguthaben)) {
            throw new IllegalArgumentException("Startguthaben " + startguthaben
                    + " liegt nicht zwischen 0 und " + HOECHSTES_STARTGUTHABEN);
        }
        if (!brauchbareAnzahl(hoechstzahl)) {
            throw new IllegalArgumentException("hoechstzahl muss mindestens 1 sein, nicht "
                    + hoechstzahl);
        }
        if (!brauchbareAnzahl(gruppenFelder)) {
            throw new IllegalArgumentException("gruppe-hoechstzahl muss mindestens 1 sein,"
                    + " nicht " + gruppenFelder);
        }
        // Hier getrimmt und nicht beim Gebrauch: Sonst geschieht es an jeder Stelle, die
        // den Text anzeigt, und eine davon wird es vergessen.
        rangWerbung = rangWerbung == null ? "" : rangWerbung.trim();
    }

    /**
     * Dieselben Werte mit einer anderen Preisstaffel.
     *
     * <p><b>Warum es diese neun Abwandler gibt:</b> Dieser Satz ist mit jedem Bauschritt
     * um einen Teil gewachsen - erst die Zeitwerte, dann die Berufe, dann der Verdienst,
     * jetzt der Markt. Jede Probe, die ihn mit ALLEN Teilen baut, bricht bei jedem
     * weiteren, und zwar nicht, weil ihre Frage falsch waere, sondern weil ein Teil
     * dazugekommen ist, der sie nichts angeht. Beim Verdienst waren das elf Stellen in
     * fuenf Dateien; mit einem Abwandler nennt eine Probe genau das, was sie veraendert,
     * und schweigt ueber den Rest.
     */
    public Wirtschaftswerte mitPreisen(Preisstaffel andere) {
        return new Wirtschaftswerte(andere, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderem Startguthaben. */
    public Wirtschaftswerte mitStartguthaben(long anderes) {
        return new Wirtschaftswerte(preise, anderes, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderer Parzellenhoechstzahl. */
    public Wirtschaftswerte mitHoechstzahl(int andere) {
        return new Wirtschaftswerte(preise, startguthaben, andere, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderer Gruppengroesse. */
    public Wirtschaftswerte mitGruppenFeldern(int andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, andere,
                rangWerbung, zeit, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderer Rangwerbung. */
    public Wirtschaftswerte mitWerbung(String andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                andere, zeit, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderen Zeitwerten. */
    public Wirtschaftswerte mitZeit(Zeitwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, andere, berufe, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderen Berufswerten. */
    public Wirtschaftswerte mitBerufen(Berufswerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, andere, verdienst, markt, messung);
    }

    /** Dieselben Werte mit anderen Verdienstwerten. */
    public Wirtschaftswerte mitVerdienst(Verdienstwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, andere, markt, messung);
    }

    /** Dieselben Werte mit anderen Marktwerten. */
    public Wirtschaftswerte mitMarkt(Marktwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, andere, messung);
    }

    /** Dieselben Werte mit anderen Messwerten. */
    public Wirtschaftswerte mitMessung(Messwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, andere);
    }

    /** Ob aus dieser Zahl ein Startguthaben werden kann; 0 heisst: mit nichts anfangen. */
    public static boolean brauchbaresStartguthaben(long betrag) {
        return betrag >= 0 && betrag <= HOECHSTES_STARTGUTHABEN;
    }

    /**
     * Ob aus dieser Zahl eine Obergrenze werden kann.
     *
     * <p>Mindestens 1 und nicht 0: Eine 0 hiesse, niemand darf eine Parzelle - und in
     * einer Einstellungsdatei sieht das aus wie ein Tippfehler, weil es einer ist. Wer
     * das Parzellensystem abschalten will, nimmt das Recht weg.
     *
     * <p>Die Regel nimmt einen {@code long}, obwohl das Feld ein {@code int} ist, und
     * zwar genau deshalb: Die Datei kennt die {@code int}-Grenze nicht. Wer die Zahl
     * vorher auf {@code int} verkuerzt, prueft nicht mehr den getippten Wert, sondern
     * dessen untere 32 Bit - aus -2.147.483.649 wird dabei 2.147.483.647, und eine
     * unbegrenzte Parzellenzahl gaelte stumm. Geprueft wird die Zahl aus der Datei.
     */
    public static boolean brauchbareAnzahl(long anzahl) {
        return anzahl >= 1 && anzahl <= Integer.MAX_VALUE;
    }
}
