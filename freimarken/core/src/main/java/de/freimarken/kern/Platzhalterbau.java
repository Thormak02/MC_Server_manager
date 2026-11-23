package de.freimarken.kern;

import java.util.Locale;

/**
 * Ein Platzhalterbau in Altfurt - ein Name, ein Quader und die Seite mit der Tuer.
 *
 * <p>Die zehn Gebaeude aus Plan 2.1 sind bis zum 23.11.2026 Handarbeit gewesen, und genau
 * das war ihr Mangel: Ein von Hand gesetztes Haus ueberlebt keinen Weltverlust, und keine
 * Probe haelt, dass der Quader in der {@code config.yml} zu dem passt, was wirklich steht.
 * Darum stehen die Quader in der Datei und das Plugin setzt die Bauten - dieselbe
 * Entscheidung wie bei {@link Rundgangsstation} und bei den Toren der Welten.
 *
 * <p><b>Ein Bau ist mindestens drei mal drei mal drei</b>, und das ist keine willkuerliche
 * Zahl: Darunter hat er kein Inneres. Ein Quader von zwei Bloecken Kante ist aussen Wand
 * und innen nichts - er saehe aus wie ein Klotz, und die Tuer fuehrte in die Wand
 * dahinter.
 *
 * <p><b>Die Tuer ist eine Himmelsrichtung und keine Koordinate</b>: Welche Spalte der Wand
 * die Oeffnung wird, rechnet {@link Bauplan} aus der Mitte. Eine Koordinate in der Datei
 * waere eine zweite Wahrheit - sie stuende neben dem Quader und muesste mit ihm wandern.
 *
 * <p>Dieselbe Grenze wie bei einer Rundgangsstation ({@code 64 * 64 * 64}): Jeder dieser
 * Bauten soll eine Station werden koennen, und eine Grenze, die nur an einer der zwei
 * Stellen gilt, laesst einen Betreiber einen Bau eintragen, der als Station abgewiesen
 * wird.
 */
public record Platzhalterbau(String schluessel, Quader bereich, Richtung tuer,
                             Baustil stil, Dachart dach) {

    /** So lang darf ein Name hoechstens sein - wie bei {@link Rundgangsstation}. */
    public static final int HOECHSTE_SCHLUESSELLAENGE = 32;

    /** Erlaubte Namen: kleingeschrieben, beginnt mit einem Buchstaben. */
    public static final String MUSTER = "[a-z][a-z0-9_-]*";

    /** Die kleinste Kante, bei der ein Bau ein Inneres hat. */
    public static final int KLEINSTE_KANTE = 3;

    /** Dieselbe Grenze wie bei einer Rundgangsstation - siehe Klassenkopf. */
    public static final int HOECHSTE_BLOECKE = 64 * 64 * 64;

    public Platzhalterbau {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Bau ohne Namen ist kein Bau");
        }
        if (bereich == null) {
            throw new IllegalArgumentException("Ein Bau ohne Quader ist kein Bau: "
                    + schluessel);
        }
        if (tuer == null) {
            throw new IllegalArgumentException("Ein Bau ohne Tuerseite ist ein Klotz: "
                    + schluessel);
        }
        if (stil == null || dach == null) {
            throw new IllegalArgumentException("Ein Bau ohne Stil oder ohne Dachart hat"
                    + " keine Baustoffe: " + schluessel);
        }
        schluessel = schluessel.trim().toLowerCase(Locale.ROOT);
        if (!schluessel.matches(MUSTER)) {
            throw new IllegalArgumentException("Der Name \"" + schluessel + "\" passt"
                    + " nicht zu " + MUSTER + " - erlaubt sind Kleinbuchstaben, Ziffern,"
                    + " Bindestrich und Unterstrich, und der erste muss ein Buchstabe"
                    + " sein.");
        }
        if (schluessel.length() > HOECHSTE_SCHLUESSELLAENGE) {
            throw new IllegalArgumentException("Der Name \"" + schluessel + "\" ist"
                    + " laenger als " + HOECHSTE_SCHLUESSELLAENGE + " Zeichen - so einen"
                    + " tippt niemand.");
        }
        int kanteX = bereich.bisX() - bereich.vonX() + 1;
        int kanteY = bereich.bisY() - bereich.vonY() + 1;
        int kanteZ = bereich.bisZ() - bereich.vonZ() + 1;
        if (kanteX < KLEINSTE_KANTE || kanteY < KLEINSTE_KANTE
                || kanteZ < KLEINSTE_KANTE) {
            throw new IllegalArgumentException("Der Bau \"" + schluessel + "\" ist "
                    + kanteX + "x" + kanteY + "x" + kanteZ + " - unter "
                    + KLEINSTE_KANTE + " Bloecken je Kante hat er kein Inneres, und die"
                    + " Tuer fuehrte in die Wand dahinter.");
        }
        if (bereich.bloecke() > HOECHSTE_BLOECKE) {
            throw new IllegalArgumentException("Der Bau \"" + schluessel + "\" hat "
                    + bereich.bloecke() + " Bloecke, erlaubt sind " + HOECHSTE_BLOECKE
                    + " - so viele waeren keine Station mehr.");
        }
    }

    /** Liegt diese Stelle im Bau? Beide Ecken eingeschlossen. */
    public boolean enthaelt(int x, int y, int z) {
        return bereich.enthaelt(x, y, z);
    }

    /**
     * Wie breit die Seite ist, in der die Tuer steht - die FRONT.
     *
     * <p>Aus ihr rechnet {@link Bauplan} den Giebel: Ein Hansehaus steht giebelstaendig,
     * und die Hoehe seines Giebels ist die halbe Front.
     */
    public int front() {
        return switch (tuer) {
            case NORD, SUED -> bereich.bisX() - bereich.vonX() + 1;
            case OST, WEST -> bereich.bisZ() - bereich.vonZ() + 1;
        };
    }

    /** Wie tief der Bau ist - von der Front nach hinten. */
    public int tiefe() {
        return switch (tuer) {
            case NORD, SUED -> bereich.bisZ() - bereich.vonZ() + 1;
            case OST, WEST -> bereich.bisX() - bereich.vonX() + 1;
        };
    }

    /** Wie hoch er ist, Boden und First eingerechnet. */
    public int hoehe() {
        return bereich.bisY() - bereich.vonY() + 1;
    }

    /** Wie viele Bloecke der Bau umfasst - Luft eingerechnet. */
    public long bloecke() {
        return bereich.bloecke();
    }
}
