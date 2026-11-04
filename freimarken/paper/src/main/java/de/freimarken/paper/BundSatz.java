package de.freimarken.paper;

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
 * <p>Die acht eigenen Spielersaetze von {@code zeigeStand} und {@code zeigePreis}
 * (Schluesselebene {@code stand.} und {@code preis.}) kommen mit dem letzten Bauschritt
 * hierher. Sie fehlen hier mit Absicht: Ein Schluessel, den keine Sendestelle benutzt,
 * macht {@code GrenzeTest.jederSchluesselWirdGesendet} rot - und das ist der Zweck jener
 * Probe.
 */
enum BundSatz implements Satz {

    BEFEHL_FREIMARKEN("befehl.beschreibung.freimarken"),
    BEFEHL_PARZELLE("befehl.beschreibung.parzelle"),
    BEFEHL_MARK("befehl.beschreibung.mark"),
    BEFEHL_WARP("befehl.beschreibung.warp"),
    BEFEHL_ZURUECK("befehl.beschreibung.zurueck"),
    BEFEHL_TPA("befehl.beschreibung.tpa"),
    BEFEHL_TPAHERE("befehl.beschreibung.tpahere"),
    BEFEHL_TPACCEPT("befehl.beschreibung.tpaccept"),
    BEFEHL_TPDENY("befehl.beschreibung.tpdeny");

    private final String pfad;

    /**
     * Alle neun sind {@link Art#WORT}, und das ist kein Versehen:
     * {@code verzeichnis.register(knoten, beschreibung, kurzformen)} nimmt eine
     * Zeichenkette und kein Component (FreimarkenPlugin:427-458). Ein Farbtag darin
     * stuende dem Spieler woertlich in {@code /help} - also ist er eine Klage.
     */
    BundSatz(String pfad) {
        this.pfad = pfad;
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return Art.WORT;
    }

    @Override
    public Set<String> platzhalter() {
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        return false;
    }

    /** Die neun in der Reihenfolge, in der sie angemeldet werden. */
    static List<Satz> beschreibungen() {
        return List.of(values());
    }
}
