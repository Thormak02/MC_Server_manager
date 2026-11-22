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
 * @param bonus         was der Tagesbonus gibt und wie die Serie ihn wachsen laesst -
 *                      aus demselben Grund im selben Satz
 * @param handel        die Gebuehrensaetze des Kistenhandels. Ab Werk beide 0, und
 *                      trotzdem hier: Eine Zahl, die erst eingebaut wird, wenn man sie
 *                      braucht, wird unter Druck eingebaut
 * @param freistaedte   was eine eigene Stadt kostet und wie viele einer halten darf
 * @param inseln        was eine Meerinsel kostet und wie viele einer halten darf - aus
 *                      demselben Grund im selben Satz wie die Freistaedte
 * @param angeln        die Angeltabelle von Hafenwerder. Hier aus dem schaerfsten der
 *                      Gruende: Sie wird bei JEDEM Fang gelesen, und zwar im Hauptfaden
 * @param wolken        Preis, Obergrenze und Biome von Wolkenwerder - und ab Teil C auch
 *                      die Erzausbeute des Kobbelsteingenerators. EIN Bestandteil fuer
 *                      diese ganze Welt und nicht vier: Jeder weitere hier kostet
 *                      einundzwanzig Abwandlerzeilen, jeder weitere IN
 *                      {@link Wolkenwerte} nur die eigenen
 * @param raenge        die Rangleiter - Stufen, Bedingungen und was eine Stufe erlaubt
 * @param wechsel       die Kurse und der Wechseldeckel der Goldmark (Plan 8.1). Hier aus
 *                      demselben Grund wie der Markt: Ein Neuladen tauscht den GANZEN
 *                      Satz als EIN Stueck, und wer mitten in einem Tausch den neuen Kurs
 *                      mit dem alten Deckel bekaeme, tauschte zu etwas, das niemand
 *                      eingestellt hat
 * @param anmeldung     die Anmeldung im Buergeramt (Plan 10.1) - ob sie verlangt wird
 *                      und welche Fassung der Regeln heute gilt
 * @param muenzhaus     der Katalog des Muenzhauses (Plan 2.1) - was es fuer Goldmark
 *                      gibt, zu welchem Preis und unter welcher Bedingung. Neben und
 *                      nicht IN {@link Wechselwerte}, obwohl das nur die eigenen Zeilen
 *                      gekostet haette: Ein Satz, der Wechselwerte heisst und einen
 *                      Warenkatalog haelt, luegt ueber seinen Inhalt
 */
public record Wirtschaftswerte(Preisstaffel preise, long startguthaben, int hoechstzahl,
                               int gruppenFelder, String rangWerbung, Zeitwerte zeit,
                               Berufswerte berufe, Verdienstwerte verdienst,
                               Marktwerte markt, Messwerte messung,
                               Bonuswerte bonus, Mietwerte laeden,
                               Shopwerte handel, Truhenwerte truhe,
                               Freistadtwerte freistaedte, Inselwerte inseln,
                               Angelwerte angeln, Wolkenwerte wolken,
                               Rangwerte raenge, Wechselwerte wechsel,
                               Muenzwerte muenzhaus, Anmeldewerte anmeldung) {

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

    /**
     * Die ausgelieferte Parzellenzahl ohne besonderes Recht - die Zahl des REISENDEN.
     *
     * <p><b>Eine Eins und nicht die Vier aus Plan 9</b> [20.11.2026, Schritt 12 Teil H]:
     * Dieser Wert ist eine UNTERGRENZE FUER JEDEN. {@code Rangzahl} nimmt die hoechste
     * Zahl aus Recht UND Datei; stand hier die Buergerzahl, bekam sie jeder Reisende, und
     * der ganze Aufstieg auf dieser Grenze war wirkungslos - ohne dass irgendwo etwas rot
     * wurde. Die Vier steht jetzt dort, wo sie hingehoert: in der obersten Stufe der
     * Leiter ({@link Rangwerte#VORGABE_LEITER}).
     *
     * <p>Wer keine Raenge will, schaltet {@code raenge.an} aus und traegt hier die Zahl
     * ein, die fuer jeden gelten soll. Dann ist diese Zahl die ganze Regel.
     */
    public static final int VORGABE_HOECHSTZAHL = 1;

    /**
     * Die ausgelieferte Felderzahl je Gruppe ohne besonderes Recht - Reisendenzahl.
     *
     * <p>Aus demselben Grund eine Eins wie {@link #VORGABE_HOECHSTZAHL}, und hier ist die
     * Folge sogar sichtbarer: Eine Gruppe aus EINEM Feld ist keine Gruppe - der Reisende
     * kann also gar nicht zusammenlegen, und genau das ist der Rangvorteil des Siedlers.
     */
    public static final int VORGABE_GRUPPEN_FELDER = 1;

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
            Verdienstwerte.VORGABE, Marktwerte.VORGABE, Messwerte.VORGABE,
            Bonuswerte.VORGABE, Mietwerte.VORGABE, Shopwerte.VORGABE,
            Truhenwerte.VORGABE, Freistadtwerte.VORGABE, Inselwerte.VORGABE,
            Angelwerte.VORGABE, Wolkenwerte.VORGABE, Rangwerte.VORGABE,
            Wechselwerte.VORGABE, Muenzwerte.VORGABE, Anmeldewerte.VORGABE);

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
        if (bonus == null) {
            throw new IllegalArgumentException("Ohne Bonuswerte gibt es keinen"
                    + " Tagesbonus");
        }
        if (laeden == null) {
            throw new IllegalArgumentException("Ohne Mietwerte gibt es keine Ladenmiete"
                    + " und keine Mietdauer");
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
        if (handel == null) {
            throw new IllegalArgumentException("Ohne Handelsgebuehren gibt es keinen"
                    + " Wirtschaftsstand - wer keine erheben will, nimmt"
                    + " Shopwerte.VORGABE mit zwei Nullen");
        }
        if (truhe == null) {
            throw new IllegalArgumentException("Ohne Truhenwerte gibt es keinen"
                    + " Tagesdeckel der Freien Truhe - wer nicht deckeln will, nimmt"
                    + " Truhenwerte.VORGABE.mitEntnahme(Truhenwerte.KEIN_DECKEL)");
        }
        if (freistaedte == null) {
            throw new IllegalArgumentException("Ohne Freistadtwerte gibt es keinen"
                    + " Gruendungspreis - wer nicht gruenden lassen will, nimmt"
                    + " Freistadtwerte.VORGABE.mitHoechstzahl(0)");
        }
        if (inseln == null) {
            throw new IllegalArgumentException("Ohne Inselwerte gibt es keinen Preis fuer"
                    + " eine Meerinsel - wer keine vergeben will, nimmt"
                    + " Inselwerte.VORGABE.mitHoechstzahl(0)");
        }
        if (angeln == null) {
            throw new IllegalArgumentException("Ohne Angelwerte gibt es keine"
                    + " Angeltabelle - wer sie abschalten will, nimmt"
                    + " Angelwerte.VORGABE.mitAn(false)");
        }
        if (wolken == null) {
            throw new IllegalArgumentException("Ohne Wolkenwerte gibt es keine"
                    + " Skyblockinsel - wer keine vergeben will, nimmt"
                    + " Wolkenwerte.VORGABE.mitHoechstzahl(0)");
        }
        if (raenge == null) {
            throw new IllegalArgumentException("Ohne Rangwerte gibt es keine Leiter -"
                    + " eine LEERE Leiter ist erlaubt und heisst: keine Raenge.");
        }
        if (wechsel == null) {
            throw new IllegalArgumentException("Ohne Wechselwerte gibt es keine Kurse"
                    + " und keinen Wechseldeckel - wer den Rueckweg schliessen will,"
                    + " nimmt Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU)");
        }
        if (anmeldung == null) {
            throw new IllegalArgumentException("Ohne Anmeldewerte gibt es keine Anmeldung"
                    + " - wer sie nicht will, nimmt"
                    + " new Anmeldewerte(false, Anmeldewerte.VORGABE_FASSUNG)");
        }
        if (muenzhaus == null) {
            throw new IllegalArgumentException("Ohne Muenzwerte gibt es kein Muenzhaus -"
                    + " wer nichts verkaufen will, nimmt"
                    + " Muenzwerte.VORGABE.mitAn(false)");
        }
    }

    /**
     * Dieselben Werte mit einer anderen Preisstaffel.
     *
     * <p><b>Warum es diese einundzwanzig Abwandler gibt:</b> Dieser Satz ist mit jedem
     * Bauschritt um einen Teil gewachsen - erst die Zeitwerte, dann die Berufe, dann der
     * Verdienst, der Markt, die Messung, der Bonus, die Laeden, der Handel, die Freie
     * Truhe, die Freistaedte, die Inseln, das Angelgut, Wolkenwerder, die Raenge, die
     * Wechselstube, jetzt das Muenzhaus. Jede Probe, die ihn
     * mit ALLEN Teilen baut, bricht bei jedem
     * weiteren, und zwar nicht, weil ihre Frage falsch waere, sondern weil ein Teil
     * dazugekommen ist, der sie nichts angeht. Beim Verdienst waren das elf Stellen in
     * fuenf Dateien; mit einem Abwandler nennt eine Probe genau das, was sie veraendert,
     * und schweigt ueber den Rest.
     */
    public Wirtschaftswerte mitPreisen(Preisstaffel andere) {
        return new Wirtschaftswerte(andere, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderem Startguthaben. */
    public Wirtschaftswerte mitStartguthaben(long anderes) {
        return new Wirtschaftswerte(preise, anderes, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderer Parzellenhoechstzahl. */
    public Wirtschaftswerte mitHoechstzahl(int andere) {
        return new Wirtschaftswerte(preise, startguthaben, andere, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderer Gruppengroesse. */
    public Wirtschaftswerte mitGruppenFeldern(int andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, andere,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderer Rangwerbung. */
    public Wirtschaftswerte mitWerbung(String andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                andere, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Zeitwerten. */
    public Wirtschaftswerte mitZeit(Zeitwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, andere, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Berufswerten. */
    public Wirtschaftswerte mitBerufen(Berufswerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, andere, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Verdienstwerten. */
    public Wirtschaftswerte mitVerdienst(Verdienstwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, andere, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Marktwerten. */
    public Wirtschaftswerte mitMarkt(Marktwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, andere, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Messwerten. */
    public Wirtschaftswerte mitMessung(Messwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, andere, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Bonuswerten. */
    public Wirtschaftswerte mitBonus(Bonuswerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, andere, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Mietwerten. */
    public Wirtschaftswerte mitLaeden(Mietwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, andere,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Handelsgebuehren. */
    public Wirtschaftswerte mitHandel(Shopwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                andere, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Zahlen der Freien Truhe. */
    public Wirtschaftswerte mitTruhe(Truhenwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, andere, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Zahlen der Freistaedte. */
    public Wirtschaftswerte mitFreistaedten(Freistadtwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, andere, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Zahlen der Meerinseln. */
    public Wirtschaftswerte mitInseln(Inselwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, andere, angeln, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit einer anderen Angeltabelle. */
    public Wirtschaftswerte mitAngeln(Angelwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, andere, wolken, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit anderen Wolkenwerten. */
    public Wirtschaftswerte mitWolken(Wolkenwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, andere, raenge,
                wechsel, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit einer anderen Anmeldung. */
    public Wirtschaftswerte mitAnmeldung(Anmeldewerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, muenzhaus, andere);
    }

    /** Dieselben Werte mit anderem Muenzhauskatalog. */
    public Wirtschaftswerte mitMuenzhaus(Muenzwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                wechsel, andere, anmeldung);
    }

    /** Dieselben Werte mit anderen Kursen und anderem Wechseldeckel. */
    public Wirtschaftswerte mitWechsel(Wechselwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, raenge,
                andere, muenzhaus, anmeldung);
    }

    /** Dieselben Werte mit einer anderen Rangleiter. */
    public Wirtschaftswerte mitRaengen(Rangwerte andere) {
        return new Wirtschaftswerte(preise, startguthaben, hoechstzahl, gruppenFelder,
                rangWerbung, zeit, berufe, verdienst, markt, messung, bonus, laeden,
                handel, truhe, freistaedte, inseln, angeln, wolken, andere,
                wechsel, muenzhaus, anmeldung);
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
