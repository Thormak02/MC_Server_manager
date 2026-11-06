package de.freimarken.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Die Saetze von {@code /freimarken} selbst - und die Befehlsbeschreibungen.
 *
 * <p>Die neun {@code befehl.beschreibung.*} ziehen in Schritt 1 um und nicht spaeter, und
 * zwar aus einem Probengrund: {@link Sprachstand#NUR_BEIM_START} sichert in
 * {@code SprachstandTest} die GENAUE Menge zu. Eine Probe ueber eine leere Liste sichert
 * nichts zu, und niemand sieht sie sich danach noch einmal an.
 *
 * <p>Mit Bauschritt 7 sind die eigenen Spielersaetze von {@code zeigeStand} und
 * {@code zeigePreis} dazugekommen, Schluesselebenen {@code stand.} und {@code preis.} -
 * der letzte Textumzug des Projekts. Hier standen sie vorher als Fehlstelle angekuendigt,
 * und zwar mit der Zahl ACHT. Nachgezaehlt sind es NEUN: Zwei der sieben Sendestellen
 * trugen je einen Ternaer, also je zwei Wortlaute.
 *
 * <p><b>Zwei Arten in dieser Aufzaehlung - darum traegt jede Konstante ihre eigene.</b>
 * Die neun Beschreibungen sind {@link Art#WORT}, und das ist kein Versehen:
 * {@code verzeichnis.register(knoten, beschreibung, kurzformen)} nimmt eine Zeichenkette
 * und kein Component (FreimarkenPlugin:529-564). Ein Farbtag darin stuende dem Spieler
 * woertlich in {@code /help} - also ist er eine Klage. Die neun Spielerzeilen sind
 * {@link Art#SATZ} und tragen Farbe. Welche Konstante welche Art hat, nagelt
 * {@code GrenzeTest} fest: Eine vertauschte Art faellt sonst erst dem Spieler auf, als
 * Tag mitten im Text oder als Wurf aus {@link Sprache#wort}.
 */
enum BundSatz implements Satz {

    // --- Die neun Befehlsbeschreibungen: Art.WORT, nur beim Start -----------
    BEFEHL_FREIMARKEN("befehl.beschreibung.freimarken", Art.WORT),
    BEFEHL_PARZELLE("befehl.beschreibung.parzelle", Art.WORT),
    BEFEHL_MARK("befehl.beschreibung.mark", Art.WORT),
    BEFEHL_WARP("befehl.beschreibung.warp", Art.WORT),
    BEFEHL_ZURUECK("befehl.beschreibung.zurueck", Art.WORT),
    BEFEHL_TPA("befehl.beschreibung.tpa", Art.WORT),
    BEFEHL_TPAHERE("befehl.beschreibung.tpahere", Art.WORT),
    BEFEHL_TPACCEPT("befehl.beschreibung.tpaccept", Art.WORT),
    BEFEHL_TPDENY("befehl.beschreibung.tpdeny", Art.WORT),
    BEFEHL_BERUF("befehl.beschreibung.beruf", Art.WORT),

    // --- Das nackte /freimarken: vier oder fuenf Chatzeilen -----------------
    STAND_KOPF("stand.kopf", Art.SATZ),
    STAND_VERSION("stand.version", Art.SATZ, "version", "java", "minecraft"),
    STAND_ABLAGE_FLUECHTIG("stand.ablage-fluechtig", Art.SATZ),
    STAND_ABLAGE_MARIADB("stand.ablage-mariadb", Art.SATZ),
    STAND_BEFEHLE("stand.befehle", Art.SATZ),
    STAND_BEFEHLE_VERWALTUNG("stand.befehle-verwaltung", Art.SATZ),

    // --- /freimarken preis: zwei Chatzeilen, zwei Lagen ---------------------
    PREIS_GRATIS("preis.gratis", Art.SATZ),
    PREIS_KOSTET("preis.kostet", Art.SATZ, "nummer", "betrag"),
    PREIS_AUSGEGEBEN("preis.ausgegeben", Art.SATZ, "betrag");

    /** Der Anfang jedes Pfades, der nur beim Start gilt - siehe {@link #beschreibungen}. */
    private static final String BESCHREIBUNG = "befehl.beschreibung.";

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    BundSatz(String pfad, Art art, String... platzhalter) {
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

    @Override
    public boolean darfLeerSein() {
        return false;
    }

    /**
     * Die neun Befehlsbeschreibungen, in der Reihenfolge, in der sie angemeldet werden.
     *
     * <p><b>Gefiltert und nicht {@code values()}</b> - und der Unterschied ist seit
     * Bauschritt 7 kein Geschmack mehr, denn diese Liste IST
     * {@link Sprachstand#NUR_BEIM_START}. Gaebe sie alle Konstanten zurueck, waeren auch
     * die neun Spielerzeilen von {@code /freimarken} nicht mehr neuladbar: Der Betreiber
     * aendert seine {@code sprache.yml}, {@code /freimarken neuladen} meldet Erfolg, und
     * im Spiel steht weiter der alte Wortlaut.
     *
     * <p>Gefiltert wird am PFAD und nicht an einer Namensliste. Eine Liste von Hand waere
     * bei einem zehnten Befehl stumm falsch - dieselbe Begruendung, aus der
     * {@link Sprachschluessel} seine Familiendeckung mechanisch baut.
     */
    static List<Satz> beschreibungen() {
        List<Satz> nurBeschreibungen = new ArrayList<>();
        for (BundSatz satz : values()) {
            if (satz.pfad.startsWith(BESCHREIBUNG)) {
                nurBeschreibungen.add(satz);
            }
        }
        return List.copyOf(nurBeschreibungen);
    }
}
