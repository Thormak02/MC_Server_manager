package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /hilfe}.
 *
 * <p>Die BESCHREIBUNG eines Befehls steht NICHT hier: Sie steht in
 * {@code befehl.beschreibung.*} und damit genau einmal - dieselbe Zeile, die Papers
 * eigene Befehlsliste zeigt. Ein zweiter Satz je Befehl waere ein zweiter Ort fuer
 * dasselbe Wort, und einer von beiden veraltet.
 *
 * <p>Die Gruppennamen sind SAETZE und keine Namen im Programm: Wie die Bereiche heissen,
 * gehoert dem Betreiber. WELCHE Gruppen es gibt und welcher Befehl in welche gehoert,
 * gehoert dem Programm - das steht in {@link Hilfeplan}.
 */
enum HilfeSatz implements Satz {

    /** Die Ueberschrift der Liste. */
    KOPF("hilfe.kopf", Art.SATZ),

    /** Grund, bauen, wohnen. */
    GRUPPE_GRUND("hilfe.gruppe.grund", Art.SATZ),

    /** Geld, Handel, Shops. */
    GRUPPE_GELD("hilfe.gruppe.geld", Art.SATZ),

    /** Rang und Beruf - was ein Spieler selbst ist. */
    GRUPPE_SELBST("hilfe.gruppe.selbst", Art.SATZ),

    /** Reisen und Treffen. */
    GRUPPE_REISEN("hilfe.gruppe.reisen", Art.SATZ),

    /** Die eigenen Welten: Freistaedte, Hafenwerder, Wolkenwerder. */
    GRUPPE_WELTEN("hilfe.gruppe.welten", Art.SATZ),

    /** Der Bund selbst: Menue, Hilfe, Regeln, Verwaltung. */
    GRUPPE_BUND("hilfe.gruppe.bund", Art.SATZ),

    /**
     * Eine Zeile der Liste: der Befehl und was er tut.
     *
     * <p>{@code beschreibung} kommt aus {@code befehl.beschreibung.*} - und weil das ein
     * {@link Art#WORT} ist, traegt es kein Farbtag. Gefaerbt wird hier.
     */
    ZEILE("hilfe.zeile", Art.SATZ, Set.of("befehl", "beschreibung")),

    /**
     * Die Zeile unter der Liste.
     *
     * <p>{@link Satz#LEER_ERLAUBT}: Wer sie nicht will, laesst sie weg.
     */
    FUSS("hilfe.fuss", Art.SATZ, Set.of(), Satz.LEER_ERLAUBT);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    HilfeSatz(String pfad, Art art) {
        this(pfad, art, Set.of(), false);
    }

    HilfeSatz(String pfad, Art art, Set<String> platzhalter) {
        this(pfad, art, platzhalter, false);
    }

    HilfeSatz(String pfad, Art art, Set<String> platzhalter, boolean leerErlaubt) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = platzhalter;
        this.leerErlaubt = leerErlaubt;
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
        return leerErlaubt;
    }
}
