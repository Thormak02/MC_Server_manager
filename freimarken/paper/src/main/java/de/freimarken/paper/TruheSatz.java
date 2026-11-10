package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Freien Truhe (Plan 7.2).
 *
 * <p>Ein eigener Bereich und keine Zeilen bei {@link ShopSatz}: Eine Freie Truhe ist kein
 * Laden. Sie nimmt kein Geld, sie hat keinen Besitzer, und ihre einzige Grenze ist eine
 * Stueckzahl je Tag.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {stueck}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Jeder dieser Saetze ist die Folge einer Entscheidung vom 10.11.2026</b>, und zwei
 * davon sind Paare mit zwei Blickwinkeln: {@link #OFFEN} gegen {@link #OFFEN_FREI} - mit
 * Deckel eine Zahl, ohne Deckel ein eigener Satz, denn "noch 0 Stueck" waere das
 * Gegenteil der Wahrheit. Und {@link #BEHALTEN} gegen {@link #ZURUECK}: Das eine sagt,
 * was er mitnimmt, das andere, was er dalaesst.
 */
enum TruheSatz implements Satz {

    /**
     * Beim Oeffnen: wie viele Stueck heute noch gehen.
     *
     * <p>VOR dem Nehmen und nicht danach, und das ist die Entscheidung dieses Satzes: Wer
     * erst beim Schliessen erfaehrt, dass er am Deckel ist, hat die Ware schon in der
     * Hand - und bekommt sie weggenommen. Das sieht aus wie ein Fehler des Servers.
     */
    OFFEN("truhe.offen", Art.SATZ, "frei"),

    /**
     * Dasselbe ohne Deckel - ein eigener Satz und keine Zahl.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@code markt.ohne-deckel}:
     * {@code freie-truhe.entnahme-je-tag: 0} heisst kein Deckel, und eine riesige Zahl
     * laese sich nicht lesen.
     */
    OFFEN_FREI("truhe.offen-frei", Art.SATZ),

    /**
     * Die Truhe bleibt zu: Heute ist die Tagesmenge schon heraus.
     *
     * <p>Nennt den DECKEL und nicht den Rest: Der Rest ist 0, und eine 0 beantwortet die
     * Frage nicht, die der Spieler jetzt hat ("wie viel darf ich ueberhaupt?").
     */
    DECKEL("truhe.deckel", Art.SATZ, "deckel"),

    /**
     * Jemand anders sieht gerade hinein.
     *
     * <p>Die Zusage, auf der die ganze Zaehlung steht: EINER zur Zeit. Gezaehlt wird die
     * Differenz zwischen Oeffnen und Schliessen, und bei zwei Zuschauern waere diese
     * Differenz nicht die Entnahme eines Spielers, sondern die von zweien - angerechnet
     * bekaeme sie der, der zuletzt schliesst.
     */
    BESETZT("truhe.besetzt", Art.SATZ),

    /**
     * Der Tagesstand liess sich nicht lesen oder nicht schreiben.
     *
     * <p>Ein eigener Satz und kein stilles Oeffnen: Ohne den Tagesstand ist der Deckel
     * offen, und eine Truhe ohne Deckel ist bei einer Stoerung genau das, was man nicht
     * will. Beim SCHLIESSEN heisst dieselbe Stoerung, dass alles zurueckgeht - sonst
     * stuende am Ende eine leere Truhe und kein Zaehler.
     */
    GESTOERT("truhe.gestoert", Art.SATZ),

    /** Beim Schliessen: was er behalten hat und was heute noch frei ist. */
    BEHALTEN("truhe.behalten", Art.SATZ, "stueck", "frei"),

    /**
     * Beim Schliessen: was ueber dem Deckel lag und zurueckgelegt wurde.
     *
     * <p>Mit der ZAHL des Deckels, nicht nur mit der Zahl der Stuecke: Wer liest "7 Stueck
     * gehen zurueck", sucht den Fehler; wer liest "mehr als 16 am Tag geht nicht", kennt
     * die Regel.
     */
    ZURUECK("truhe.zurueck", Art.SATZ, "stueck", "deckel"),

    /**
     * Ein Gegenstand, der dort nicht hineingehoert, kommt zurueck.
     *
     * <p>Erkannt und zurueckgegeben wird BEIM SCHLIESSEN und nicht beim Hineinlegen -
     * dieselbe Entscheidung wie bei der Zaehlung und mit demselben Grund: In eine offene
     * Kiste legt man mit Linksklick, Rechtsklick, Shift-Klick, Zahlentaste und durch
     * Ziehen ueber mehrere Felder. Sieben Wege zur selben Tat, und ein Programm, das
     * sechs davon kennt, hat kein Verbot.
     */
    VERBOTEN("truhe.verboten", Art.SATZ, "ware");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    TruheSatz(String pfad, Art art, String... platzhalter) {
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
     * <p>Dieselbe Hausregel wie in {@link MarktSatz} und {@link ShopSatz}: Jeder ist die
     * EINZIGE Antwort auf eine Tat. Wer die Truhe abschalten will, nimmt die Stelle aus
     * der {@code config.yml} - ein leerer Satz liesse die Truhe stumm arbeiten, und
     * zurueckgelegte Ware ohne Grund sieht aus wie ein Diebstahl des Servers.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
