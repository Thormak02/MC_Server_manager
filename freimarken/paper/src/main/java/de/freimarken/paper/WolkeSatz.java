package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Wolkeninseln von Wolkenwerder (Plan 4.1, Schritt 11).
 *
 * <p>Ein eigener Bereich und keine Zeilen bei {@link WerderSatz}, obwohl beide Welten
 * Inseln vergeben. Der Grund steht in den Saetzen selbst: Eine Meerinsel KAUFT man dort,
 * wo man steht, und sie liegt schon da. Eine Wolkeninsel bekommt man ZUGETEILT, sie
 * entsteht erst in diesem Augenblick, und sie kostet nichts. Wer die zwei Welten in einen
 * Bereich legte, bekaeme Saetze mit einem {@code {preis}}, der immer 0 ist, und einen
 * Satz "die Insel, auf der du stehst" fuer eine Welt, in der man nirgends steht.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {feld}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Mitbauer haben hier keine eigene Liste</b>, anders als bei den Parzellen: Sie
 * stehen in der Zeile der Insel, zu der sie gehoeren ({@link #ZEILE_MITBAUER}). Bei
 * hoechstens ein paar Inseln je Spieler ist das die kuerzere Auskunft - und eine zweite
 * Liste waere ein zweiter Ort, an dem derselbe Name steht.
 */
enum WolkeSatz implements Satz {

    /**
     * Die Ueberschrift ueber den eigenen Inseln - mit ihrer Zahl.
     *
     * <p>Gezaehlt, und die Null gehoert dazu: Wer keine Insel hat, liest statt der Liste
     * den Satz {@link #LEER} - die Null erscheint also nie im Spiel. Aufgeschrieben wird
     * sie trotzdem (Probe 11b verlangt sie), und das ist richtig: Wer den Zweig eines
     * Tages umbaut, sieht an der Tafel, welche Form dann herauskaeme.
     */
    KOPF("wolke.kopf", Art.GEZAEHLT, "anzahl"),

    /** Eine Zeile je Insel - die Feldnummer ist ihre Adresse. */
    ZEILE("wolke.zeile", Art.SATZ, "feld"),

    /**
     * Dieselbe Zeile, wenn Mitbauer eingetragen sind.
     *
     * <p>Ein zweiter Satz und kein Platzhalter, der manchmal leer ist: Ein
     * {@code Mitbauer: } ohne Namen dahinter ist eine Zeile, die aussieht wie ein Fehler.
     */
    ZEILE_MITBAUER("wolke.zeile-mitbauer", Art.SATZ, "feld", "mitbauer"),

    /**
     * Du hast keine Insel.
     *
     * <p>Diese Zeile MUSS es geben: Wer nur die Ueberschrift sieht, haelt es fuer einen
     * Fehler - und am ersten Tag ist das der Normalfall. Sie nennt den Befehl, denn das
     * ist die Frage, die als naechste kommt. Den PREIS nennt sie NICHT: Ab Werk ist er 0,
     * und "kostet 0 Mark" ist keine Auskunft.
     */
    LEER("wolke.leer", Art.SATZ),

    /** Du darfst noch eine nehmen - mit der Zahl, die dein Rang erlaubt. */
    NOCH_FREI("wolke.noch-frei", Art.GEZAEHLT, "hoechstzahl"),

    /**
     * Du hast so viele, wie dein Rang erlaubt.
     *
     * <p>Ein eigener Satz neben {@link #NOCH_FREI}, und er ist die Antwort auf die Frage,
     * die sonst offen bliebe: Warum kann ich keine weitere nehmen? Die Zahl steht darin,
     * damit ein Spieler sie seinem Rang zuordnen kann.
     */
    VOLL("wolke.voll", Art.GEZAEHLT, "hoechstzahl"),

    /**
     * Die Insel ist genommen und bezahlt - mit Feld, Preis und dem Rest auf dem Konto.
     *
     * <p>Der Rest wird GERECHNET und nicht ein zweites Mal gefragt, wie bei der
     * Meerinsel: Zwischen Abbuchung und Antwort kann ein Tagesbonus liegen, und dann
     * nennte die Zeile eine Zahl, die sich nicht aus ihren eigenen Teilen ergibt.
     */
    GENOMMEN("wolke.genommen", Art.SATZ, "feld", "preis", "danach"),

    /**
     * Dieselbe Nachricht, wenn die Insel GRATIS war.
     *
     * <p>Zwei Saetze und nicht einer mit einem Preis von 0: Plan 6.2 nennt die
     * Skyblockinsel als einen der zwei gratis Einstiege, und "fuer 0 Mark gekauft" ist
     * kein deutscher Satz. Welcher gilt, entscheidet {@code Wolkentext} und nicht der
     * Befehl - genau wie bei der Meerinsel.
     */
    GENOMMEN_GRATIS("wolke.genommen-gratis", Art.SATZ, "feld"),

    /**
     * Die Insel ist eingetragen, aber die Bloecke stehen nicht.
     *
     * <p>Der Satz fuer den Fall, den dieses Projekt nirgends sonst hat: Hier gehoert das
     * Gelaende nicht dem Weltgenerator, sondern dem Plugin - und wenn die Welt in diesem
     * Augenblick nicht geladen ist, gehoert dem Spieler ein Feld aus Luft. Er muss es
     * ERFAHREN, denn reparierbar ist es (Aufsetzen und Zuruecksetzen sind dieselbe Hand),
     * aber nicht von selbst.
     */
    AUFBAU_GESTOERT("wolke.aufbau-gestoert", Art.SATZ, "feld"),

    /** Du hast schon so viele, wie dein Rang erlaubt. */
    ABSAGE_GRENZE("wolke.absage.grenze", Art.GEZAEHLT, "hoechstzahl"),

    /**
     * Die Zeile aus der {@code config.yml} unter der Absage an der Grenze.
     *
     * <p>Nur die FARBE ist hier des Betreibers, der Wortlaut steht in
     * {@code hinweise.rang-werbung}. Gebaut wird sie in {@link Rangwerbung}.
     */
    RANG_WERBUNG("wolke.rang-werbung", Art.SATZ, "werbung"),

    /**
     * Die Welt ist voll - jedes Feld ist vergeben.
     *
     * <p>Bei 1.443 Feldern weit weg, und doch der Zustand, in dem ein Spieler sonst eine
     * Zusage ohne Insel bekaeme. Der Satz nennt keinen Ausweg, denn der Spieler hat
     * keinen - er gehoert dem Betreiber (eine groessere Weltkante).
     */
    ABSAGE_WELT_VOLL("wolke.absage.welt-voll", Art.SATZ),

    /**
     * Das Geld reicht nicht - mit dem Preis und dem, was fehlt.
     *
     * <p>Ab Werk kostet eine Insel nichts, dieser Satz erscheint also nur, wenn der
     * Betreiber einen Preis gesetzt hat. Er steht trotzdem hier und nicht erst dann: Ein
     * Zweig ohne Satz sendet GAR NICHTS, und das faellt genau an dem Tag auf, an dem der
     * Preis gesetzt wird.
     */
    ABSAGE_ZU_WENIG("wolke.absage.zu-wenig", Art.SATZ, "preis", "fehlt"),

    /** Die Ablage hat nicht geantwortet. Nichts ist passiert, auch nicht am Konto. */
    ABSAGE_GESTOERT("wolke.absage.gestoert", Art.SATZ),

    /**
     * Es gibt keine Wolkenwelt.
     *
     * <p>Der Fall, in dem der Betreiber {@code welten.wolkenwerder} nicht in seine
     * config.yml uebernommen hat - eine vorhandene Datei wird beim Update nie
     * ueberschrieben. Ohne diesen Satz antwortete der Befehl GAR NICHT, und der Spieler
     * tippte ihn wieder.
     */
    ABSAGE_KEINE_WELT("wolke.absage.keine-welt", Art.SATZ),

    /** Du hast keine Insel, auf die du reisen oder Mitbauer setzen koenntest. */
    KEINE_INSEL("wolke.keine-insel", Art.SATZ),

    /** Ein Mitbauer ist dazugekommen. */
    MITBAUER_DAZU("wolke.mitbauer.dazu", Art.SATZ, "spieler"),

    /** Ein Mitbauer ist weg. */
    MITBAUER_WEG("wolke.mitbauer.weg", Art.SATZ, "spieler"),

    /**
     * Der stand schon drin.
     *
     * <p>Ein eigener Satz und nicht dieselbe Bestaetigung wie beim Dazukommen: Wer
     * "hinzugefuegt" liest, obwohl nichts geschehen ist, haelt eine Liste fuer laenger,
     * als sie ist - und sucht den Fehler beim naechsten Mal an der falschen Stelle.
     */
    MITBAUER_SCHON("wolke.mitbauer.schon", Art.SATZ, "spieler"),

    /** Der stand nicht drin. Dieselbe Begruendung wie bei {@link #MITBAUER_SCHON}. */
    MITBAUER_NICHT_DABEI("wolke.mitbauer.nicht-dabei", Art.SATZ, "spieler"),

    /**
     * Diesen Spieler kennt der Server nicht.
     *
     * <p>Gefragt wird nach einem NAMEN, und Namen sind veraenderlich. Ein stilles
     * Nichtstun waere die schlimmere Antwort: Der Besitzer glaubte, sein Freund duerfe
     * bauen, und jener stuende vor einer Absage ohne Grund.
     */
    MITBAUER_UNBEKANNT("wolke.mitbauer.unbekannt", Art.SATZ, "spieler"),

    /** Sich selbst eintragen geht nicht - der Eigentuemer darf ohnehin alles. */
    MITBAUER_SELBST("wolke.mitbauer.selbst", Art.SATZ),

    /**
     * Die Insel, auf der du stehst, gehoert einem anderen.
     *
     * <p>Mit seinem Namen, denn das ist die Auskunft, die fehlt: Wer hier Mitbauer setzen
     * will, ist entweder am falschen Ort oder meint die falsche Insel - und in einer Welt
     * ohne Wege kommt man nur auf eine fremde Insel, wenn man geflogen ist.
     */
    MITBAUER_FREMD("wolke.mitbauer.fremd", Art.SATZ, "eigentuemer"),

    // ================================================== Die Herausforderungen [Teil D]

    /**
     * Die Ueberschrift der Tafel - wie viele von wie vielen erfuellt sind.
     *
     * <p>Ein SATZ und kein GEZAEHLTER, obwohl eine Zahl darin steht: Gezaehlt wird hier
     * nicht EIN Ding in Ein- und Mehrzahl, sondern zwei Zahlen nebeneinander gestellt -
     * "3 von 10". Eine Mehrzahlform davon waere eine Form je Kombination.
     */
    AUFGABEN_KOPF("wolke.aufgaben.kopf", Art.SATZ, "erfuellt", "gesamt"),

    /** Eine offene Herausforderung - mit dem, was sie verlangt, und ihrem Lohn. */
    AUFGABEN_OFFEN("wolke.aufgaben.offen", Art.SATZ, "name", "verlangt", "belohnung"),

    /**
     * Dieselbe Zeile, wenn es kein Geld dafuer gibt.
     *
     * <p>Zwei Saetze und kein Platzhalter, der manchmal leer ist - dieselbe Antwort wie
     * bei {@link #ZEILE_MITBAUER}: Ein "fuer " ohne Betrag dahinter sieht aus wie ein
     * Fehler. Und eine Aufgabe ohne Lohn ist erlaubt: Sie zahlt in Stufe statt in Mark.
     */
    AUFGABEN_OFFEN_OHNE_LOHN("wolke.aufgaben.offen-ohne-lohn", Art.SATZ, "name",
            "verlangt"),

    /**
     * Eine erfuellte Herausforderung.
     *
     * <p>OHNE die verlangten Gegenstaende: Was weg ist, ist weg, und die Zeile soll
     * zeigen, was noch zu tun ist. Eine vollstaendige Liste machte die Tafel doppelt so
     * lang, und zwar genau fuer den Spieler, der sie nicht mehr braucht.
     */
    AUFGABEN_FERTIG("wolke.aufgaben.fertig", Art.SATZ, "name"),

    /**
     * In dieser Welt gibt es keine Herausforderungen.
     *
     * <p>Der Schalter {@code wolken.aufgaben.an} steht auf {@code false} oder die Liste
     * ist leer. Ein stummer Befehl waere hier das Schlimmste: Der Spieler haelt die Tafel
     * fuer kaputt und tippt sie wieder.
     */
    AUFGABEN_AUS("wolke.aufgaben.aus", Art.SATZ),

    /** Erfuellt - mit dem Namen, dem Lohn und der neuen Stufe. */
    ABGABE_ERTEILT("wolke.abgabe.erteilt", Art.SATZ, "name", "belohnung", "stufe"),

    /** Dieselbe Zusage ohne Lohn - siehe {@link #AUFGABEN_OFFEN_OHNE_LOHN}. */
    ABGABE_ERTEILT_OHNE_LOHN("wolke.abgabe.erteilt-ohne-lohn", Art.SATZ, "name",
            "stufe"),

    /**
     * Es fehlt etwas - und zwar WAS.
     *
     * <p>Die Zahl ist das FEHLENDE und nicht das Verlangte: Wer 40 von 64 Kobbelsteinen
     * hat, will wissen, dass 24 fehlen. Das Verlangte steht in der Tafel.
     */
    ABGABE_FEHLT("wolke.abgabe.fehlt", Art.SATZ, "name", "fehlt"),

    /** Diese Herausforderung steht auf dieser Insel schon. */
    ABGABE_SCHON("wolke.abgabe.schon", Art.SATZ, "name"),

    /**
     * Diese Herausforderung gibt es nicht.
     *
     * <p>Mit dem getippten Wort, denn das ist die Auskunft, die fehlt: In der Liste steht
     * etwas anderes, und ein Spieler, der sich vertippt hat, soll seinen Tippfehler lesen.
     */
    ABGABE_UNBEKANNT("wolke.abgabe.unbekannt", Art.SATZ, "name"),

    /**
     * Die Insel, auf der du stehst, gehoert einem anderen - und du bist kein Mitbauer.
     *
     * <p>Ein eigener Satz und nicht {@link #MITBAUER_FREMD}: Dort geht es um das
     * Eintragen, das NUR der Eigentuemer darf; hier um das Abgeben, das jeder Mitbauer
     * darf. Zwei verschiedene Regeln mit einem Satz zu beschreiben hiesse, dass der eine
     * Satz in einem der beiden Faelle falsch ist.
     */
    ABGABE_FREMD("wolke.abgabe.fremd", Art.SATZ, "eigentuemer"),

    /**
     * Gezaehlt war genug da, genommen wurde zu wenig.
     *
     * <p>Der Fall liegt zwischen zwei Augenblicken - was ein Spieler dazwischen wegwirft,
     * ist weg. Alles Genommene ist zurueckgegeben, und nichts ist eingetragen. Ein stummes
     * Nichts waere hier besonders schlecht: Der Spieler saehe seine Gegenstaende noch und
     * die Aufgabe offen und hielte den Befehl fuer kaputt.
     */
    ABGABE_WEGGEKOMMEN("wolke.abgabe.weggekommen", Art.SATZ, "name"),

    // ================================================== Punkte und Rangliste [Teil E]

    /**
     * Die Zaehlung laeuft - mit der Zahl der Chunks.
     *
     * <p>Die Zahl steht darin, weil sie die Wartezeit erklaert: Ein Feld hat 64 Chunks,
     * und bei einem je Takt sind das gut drei Sekunden. Ohne diese Zeile haelt ein Spieler
     * den Befehl fuer wirkungslos und tippt ihn wieder - und der zweite Versuch laeuft in
     * "hier zaehlt schon jemand".
     */
    PUNKTE_LAEUFT("wolke.punkte.laeuft", Art.SATZ, "chunks"),

    /** Die Zaehlung ist fertig - mit der Punktzahl. */
    PUNKTE_FERTIG("wolke.punkte.fertig", Art.SATZ, "punkte"),

    /**
     * Zu frueh - mit der Wartezeit in Sekunden.
     *
     * <p>Die Abklingzeit haengt an der INSEL und nicht am Spieler: Gezaehlt wird ein Feld,
     * und zehn Mitbauer koennten es sonst zehnmal hintereinander zaehlen lassen.
     */
    PUNKTE_ZU_FRUEH("wolke.punkte.zu-frueh", Art.SATZ, "sekunden"),

    /** Auf dieser Insel zaehlt schon jemand. */
    PUNKTE_LAEUFT_SCHON("wolke.punkte.laeuft-schon", Art.SATZ),

    /** Es gibt keine Punktetabelle - dann waere jede Insel 0 wert. */
    PUNKTE_AUS("wolke.punkte.aus", Art.SATZ),

    /** Die Ueberschrift der Rangliste. */
    RANGLISTE_KOPF("wolke.rangliste.kopf", Art.SATZ),

    /**
     * Eine Zeile der Rangliste.
     *
     * <p>Mit der STUFE neben den Punkten, und das ist der ganze Witz dieser Liste: Die
     * Punkte sagen, was gebaut wurde, die Stufe, was geleistet wurde. Eine Liste nur nach
     * Punkten waere eine Liste der Fleissigsten, und eine nur nach Stufe eine zweite
     * Aufgabentafel.
     */
    RANGLISTE_ZEILE("wolke.rangliste.zeile", Art.SATZ, "rang", "spieler", "feld",
            "punkte", "stufe"),

    /** Es gibt noch keine Insel in der Liste. */
    RANGLISTE_LEER("wolke.rangliste.leer", Art.SATZ),

    // ================================================== Biom und Zuruecksetzen [Teil F]

    /** Welches Biom deine Insel hat - und was ein Wechsel kostet. */
    BIOM_STAND("wolke.biom.stand", Art.SATZ, "biom", "preis"),

    /** Die Biome zur Wahl - eine Zeile unter dem Stand. */
    BIOM_WAHL("wolke.biom.wahl", Art.SATZ, "biome"),

    /** Gewechselt und bezahlt. */
    BIOM_GESETZT("wolke.biom.gesetzt", Art.SATZ, "biom", "preis", "danach"),

    /** Dieselbe Zusage, wenn der Wechsel gratis ist. */
    BIOM_GESETZT_GRATIS("wolke.biom.gesetzt-gratis", Art.SATZ, "biom"),

    /**
     * Dieses Biom wird nicht angeboten - mit der Liste derer, die es gibt.
     *
     * <p>Die Liste gehoert in DIESE Absage: Ein Spieler, der sich vertippt hat, soll nicht
     * erst einen zweiten Befehl brauchen, um die Namen zu lesen.
     */
    BIOM_NICHT_ANGEBOTEN("wolke.biom.nicht-angeboten", Art.SATZ, "biom", "biome"),

    /** In dieser Welt gibt es keine Biomwahl - {@code wolken.biome} ist leer. */
    BIOM_KEINE_WAHL("wolke.biom.keine-wahl", Art.SATZ),

    /** Das Geld reicht nicht. */
    BIOM_ZU_WENIG("wolke.biom.zu-wenig", Art.SATZ, "preis", "fehlt"),

    /**
     * Die Insel gehoert einem anderen.
     *
     * <p>Ein eigener Satz neben {@link #ABGABE_FREMD}: Abgeben darf jeder Mitbauer, das
     * Biom wechseln NUR der Eigentuemer - es kostet Geld und stellt die ganze Insel um.
     */
    BIOM_NICHT_DEINE("wolke.biom.nicht-deine", Art.SATZ, "eigentuemer"),

    /**
     * Die Warnung vor dem Zuruecksetzen - sie verlangt das zweite Wort.
     *
     * <p>Dieselbe Bauart wie beim Aufgeben einer Parzelle: Zurueckgesetzt wird die
     * STARTINSEL, und was an ihrer Stelle steht, ist danach weg. Ein Befehl, der das ohne
     * Rueckfrage tut, ist einer, den man genau einmal versehentlich tippt.
     */
    ZURUECK_WARNUNG("wolke.zuruecksetzen.warnung", Art.SATZ),

    /** Die Startinsel steht wieder. */
    ZURUECK_ERTEILT("wolke.zuruecksetzen.erteilt", Art.SATZ, "feld");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    WolkeSatz(String pfad, Art art, String... platzhalter) {
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
     * Keiner dieser Saetze darf leer sein.
     *
     * <p>Dieselbe Hausregel wie in {@link WerderSatz}: Jeder ist die EINZIGE Antwort auf
     * einen getippten Befehl. Wer die Wolkeninseln abschalten will, setzt
     * {@code wolken.hoechstzahl: 0} oder nimmt das Recht weg - ein leerer Satz liesse den
     * Befehl stumm arbeiten, und ein Spieler, der nichts liest, tippt ihn wieder.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
