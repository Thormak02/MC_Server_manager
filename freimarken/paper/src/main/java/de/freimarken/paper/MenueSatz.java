package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze des Hauptmenues.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}.
 *
 * <p><b>Je Eintrag ZWEI Saetze, Name und Erklaerung</b>, und beide sind
 * {@link Satz.Art#SATZ} und keine {@code Art.WORT}: Der Anzeigename eines Gegenstands ist
 * ein {@code Component} und darf die Farbwelt tragen. Plan 11.3 nennt die Luecke, um die
 * es hier geht - "Anzeigenamen und Beschreibungen von Gegenstaenden uebersetzt Paper
 * nicht, und genau dort sitzen bei Truhenmenues die meisten Texte. Die muessen beim Bauen
 * des Menues selbst aufgeloest werden." Genau das tut {@link Menuehand}.
 *
 * <p><b>Elf Eintraege, und ihre Auswahl steht im Programm.</b> Welche Befehle es gibt,
 * weiss das Programm; wie sie heissen und was sie erklaeren, gehoert dem Betreiber. Die
 * Begruendung steht bei {@link Menuetafel}.
 */
enum MenueSatz implements Satz {

    /**
     * Der Titel der Truhe.
     *
     * <p>Nach Plan 9a.1 traegt jedes Menue den Namen des Ortes im Titel. Eine zweite
     * Zeile hat eine Truhe nicht - die Erklaerung steht darum an jedem Eintrag.
     */
    TITEL("menue.titel", Art.SATZ),

    /** Die Zeile unter jedem Eintrag, die den Befehl nennt. */
    KLICK("menue.klick", Art.SATZ, "befehl"),

    /** Auf der Konsole gibt es kein Menue - sie hat kein Inventar. */
    KEINE_KONSOLE("menue.keine-konsole", Art.SATZ),

    /** Das Menue liess sich nicht oeffnen - ein unbekannter Gegenstand in der Tafel. */
    GESTOERT("menue.gestoert", Art.SATZ),

    PARZELLE_NAME("menue.eintrag.parzelle.name", Art.SATZ),
    PARZELLE_ERKLAERUNG("menue.eintrag.parzelle.erklaerung", Art.SATZ),
    MARK_NAME("menue.eintrag.mark.name", Art.SATZ),
    MARK_ERKLAERUNG("menue.eintrag.mark.erklaerung", Art.SATZ),
    RANG_NAME("menue.eintrag.rang.name", Art.SATZ),
    RANG_ERKLAERUNG("menue.eintrag.rang.erklaerung", Art.SATZ),
    BERUF_NAME("menue.eintrag.beruf.name", Art.SATZ),
    BERUF_ERKLAERUNG("menue.eintrag.beruf.erklaerung", Art.SATZ),
    MARKT_NAME("menue.eintrag.markt.name", Art.SATZ),
    MARKT_ERKLAERUNG("menue.eintrag.markt.erklaerung", Art.SATZ),
    LADEN_NAME("menue.eintrag.laden.name", Art.SATZ),
    LADEN_ERKLAERUNG("menue.eintrag.laden.erklaerung", Art.SATZ),
    FREISTADT_NAME("menue.eintrag.freistadt.name", Art.SATZ),
    FREISTADT_ERKLAERUNG("menue.eintrag.freistadt.erklaerung", Art.SATZ),
    WERDER_NAME("menue.eintrag.werder.name", Art.SATZ),
    WERDER_ERKLAERUNG("menue.eintrag.werder.erklaerung", Art.SATZ),
    WOLKE_NAME("menue.eintrag.wolke.name", Art.SATZ),
    WOLKE_ERKLAERUNG("menue.eintrag.wolke.erklaerung", Art.SATZ),
    HILFE_NAME("menue.eintrag.hilfe.name", Art.SATZ),
    HILFE_ERKLAERUNG("menue.eintrag.hilfe.erklaerung", Art.SATZ),
    REGELN_NAME("menue.eintrag.regeln.name", Art.SATZ),
    REGELN_ERKLAERUNG("menue.eintrag.regeln.erklaerung", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    MenueSatz(String pfad, Art art, String... platzhalter) {
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
}
