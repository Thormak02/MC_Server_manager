package de.freimarken.kern;

import java.util.Locale;

/**
 * Wie eine Freistadt heissen darf.
 *
 * <p>Eine eigene Stelle und keine Zeile im Befehl, aus demselben Grund wie bei
 * {@link Berufsangabe}: Der Name wird an mehreren Stellen gebraucht - beim Gruenden, beim
 * Warp, beim Nachschlagen in der Ablage -, und zwei Abschriften derselben Regel laufen
 * auseinander. Dann naehme der Befehl einen Namen an, den die Ablage danach ablehnt.
 *
 * <p><b>Ein Stadtname ist Spielereingabe</b>, und das ist der Unterschied zu allen
 * anderen Namen im Projekt: Weltnamen und Berufsschluessel stehen in einer Datei, die nur
 * der Betreiber schreibt. Hier tippt ein Fremder, und was er tippt, landet in einer
 * Datenbankspalte, in einem Befehlsbaum und in einer Chatzeile. Darum wird nicht
 * geprueft, was verboten ist, sondern aufgezaehlt, was erlaubt ist.
 */
public final class Stadtname {

    /**
     * Was ein Stadtname tragen darf.
     *
     * <p>Wortgleich zu {@link Berufsangabe}: kein Unterstrich und kein PUNKT. Der Punkt
     * ist in der Sprachdatei der Trenner, und ein Name mit Punkt stuende dort eine Ebene
     * tiefer statt als Name. Der Unterstrich faellt mit, weil zwei Schreibweisen fuer
     * dasselbe Wort an irgendeiner der drei Stellen auseinanderlaufen.
     *
     * <p>Der erste Buchstabe muss ein Buchstabe sein. Sonst gaebe es eine Stadt namens
     * {@code 10}, und in einer Zeile wie "Freistadt 10" waere nicht mehr zu sehen, ob das
     * ein Name oder eine Feldnummer ist.
     */
    private static final String MUSTER = "[a-z][a-z0-9-]*";

    /**
     * Die laengste erlaubte Laenge - dieselbe wie die Spaltenbreite.
     *
     * <p>Zweiunddreissig, weil die Spalte {@code VARCHAR(32)} ist. Eine Regel, die mehr
     * zulaesst als die Spalte traegt, verschiebt die Absage von einer lesbaren Zeile in
     * eine abgeschnittene Zeichenkette - oder in einen Wurf aus dem Treiber.
     */
    public static final int HOECHSTENS_ZEICHEN = 32;

    /**
     * Die kuerzeste erlaubte Laenge.
     *
     * <p>Drei, und das ist eine Entscheidung gegen das Abgreifen: Ein- und zweistellige
     * Namen sind knapp, leicht zu tippen und darum als Erstes weg. Wer sie zulaesst,
     * bekommt in der ersten Woche 1.332 Staedte, die {@code a} bis {@code zz} heissen und
     * deren Besitzer sie nie betritt - und 399 Felder gibt es nur einmal.
     */
    public static final int MINDESTENS_ZEICHEN = 3;

    private Stadtname() {
    }

    /**
     * Die Namensregel als FRAGE, erste Haelfte: die Laenge.
     *
     * <p>Zwei Fragen und nicht eine, damit die Absage sagen kann, WAS nicht stimmt - die
     * Laenge nennt zwei Zahlen, die Zeichen nennen die erlaubten. Dieselbe Bauart und
     * derselbe Grund wie bei {@link Berufsangabe#brauchbareLaenge}.
     */
    public static boolean brauchbareLaenge(String roh) {
        if (roh == null || roh.isBlank()) {
            return false;
        }
        int laenge = roh.trim().length();
        return laenge >= MINDESTENS_ZEICHEN && laenge <= HOECHSTENS_ZEICHEN;
    }

    /** Die zweite Haelfte derselben Regel - siehe {@link #brauchbareLaenge}. */
    public static boolean brauchbareZeichen(String roh) {
        return roh != null && roh.trim().toLowerCase(Locale.ROOT).matches(MUSTER);
    }

    /**
     * Der Name, wie er gespeichert wird: ohne Rand und klein.
     *
     * <p>Genormt und nicht roh, damit {@code Neustadt} und {@code neustadt} dieselbe Stadt
     * sind. Ohne das waere der eindeutige Schluessel in der Datenbank kein Riegel: Er
     * vergliche Zeichen fuer Zeichen, und ein zweiter Spieler bekaeme denselben Namen in
     * anderer Schreibweise.
     *
     * <p>{@link Locale#ROOT} ausdruecklich: Ohne das macht ein tuerkisches Gebietsschema
     * aus {@code ISTADT} ein {@code ıstadt} mit punktlosem i, und der Name waere auf genau
     * einem Rechner der Welt ein anderer.
     *
     * @throws IllegalArgumentException wenn der Name die Regel nicht erfuellt - wer
     *                                  normiert, hat vorher gefragt
     */
    public static String genormt(String roh) {
        if (!brauchbareLaenge(roh) || !brauchbareZeichen(roh)) {
            throw new IllegalArgumentException("Kein brauchbarer Stadtname: " + roh);
        }
        return roh.trim().toLowerCase(Locale.ROOT);
    }
}
