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
    MITBAUER_FREMD("wolke.mitbauer.fremd", Art.SATZ, "eigentuemer");

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
