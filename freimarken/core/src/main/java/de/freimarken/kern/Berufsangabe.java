package de.freimarken.kern;

import java.util.Locale;

/**
 * Ein Beruf, wie er nach dem Lesen der Einstellungsdatei aussieht - Schluessel und Rolle.
 *
 * <p>Nur Zeichenketten, und darum im Kern und nicht im Paper-Modul: {@code Weltangabe}
 * liegt dort, weil sie {@code GameMode} und {@code World.Environment} traegt, also
 * Bukkit-Typen. Ein Beruf traegt nichts davon - er ist ein Name und die Angabe, ob er
 * selbst ein Grundberuf ist oder zu einem gehoert.
 *
 * <p><b>Die Rolle steht hier und nirgends sonst.</b> Ob {@code steinmetz} eine
 * Spezialisierung ist, entscheidet allein die Datei: Ein {@code switch} im Programm waere
 * eine zweite Wahrheit, und bei einem dreizehnten Beruf waere sie die falsche. Dieselbe
 * Entscheidung wie bei der Richtung eines Buchungsgrundes.
 *
 * <p><b>Kleingeschrieben beim BAUEN und nicht beim Vergleichen.</b> Sonst traegt dieser
 * Satz zwei Darstellungen desselben Namens, und nur eine davon wird geprueft - dieselbe
 * Falle wie bei {@link Wirtschaftswerte} und ihrer Rangwerbung. Wer {@code Bergmann} in
 * die Datei schreibt, bekommt {@code bergmann}, und zwar ueberall.
 *
 * <p><b>Was ein Beruf EINBRINGT, steht hier nicht.</b> Das kommt mit Teil E; bis dahin
 * sagt diese Angabe nur, wer welchen halten darf.
 *
 * @param schluessel  der Name in der Datei und in der Ablage, kleingeschrieben
 * @param grundberuf  der Grundberuf, zu dem dieser gehoert - oder {@code null}, wenn
 *                    dies selbst ein Grundberuf ist
 */
public record Berufsangabe(String schluessel, String grundberuf) {

    /**
     * Die laengste erlaubte Schluessellaenge.
     *
     * <p><b>Dieselbe Zahl wie die Spalte in der Ablage</b> ({@code VARCHAR(32)}), und
     * darum hier geprueft: MariaDB schneidet einen laengeren Namen stumm ab. Zwei Berufe,
     * die sich erst ab dem 33. Zeichen unterscheiden, fielen dann in der Datenbank
     * zusammen - und ein Spieler verliert einen, ohne dass irgendwo etwas klagt.
     */
    public static final int HOECHSTENS_ZEICHEN = 32;

    public Berufsangabe {
        schluessel = gepruefterName(schluessel, "Ein Beruf ohne Schluessel");
        if (grundberuf != null) {
            grundberuf = gepruefterName(grundberuf,
                    "Der Grundberuf von " + schluessel + " ist leer");
            if (grundberuf.equals(schluessel)) {
                throw new IllegalArgumentException("Der Beruf " + schluessel
                        + " ist sein eigener Grundberuf. Ein Grundberuf laesst das Feld"
                        + " leer; ein Verweis auf sich selbst waere ein Kreis, und die"
                        + " Platzzaehlung der Regel kaeme zweimal.");
            }
        }
    }

    /**
     * Was ein Berufsschluessel tragen darf.
     *
     * <p>Kein Unterstrich und kein PUNKT, anders als bei einem Weltnamen: Der Punkt ist in
     * der Sprachdatei der Trenner, und {@code beruf.name.berg.mann} waere dort eine Ebene
     * tiefer statt ein Name. Der Unterstrich faellt mit, weil ein Schluessel an drei Orten
     * dasselbe Zeichen sein muss und zwei Schreibweisen fuer ein Wort an irgendeinem
     * davon auseinanderlaufen.
     */
    private static final String MUSTER = "[a-z][a-z0-9-]*";

    /**
     * Die Namensregel als FRAGE - fuer den Leser der Einstellungsdatei.
     *
     * <p>Hier und nicht zweimal: Der Kompaktkonstruktor wirft, und ein Leser der
     * config.yml darf nicht werfen - ein Tippfehler in einem von zwanzig Berufen darf
     * keinen Serverstart mitreissen. Zwei Fassungen derselben Regel wichen eines Tages ab,
     * und dann nahme der Leser einen Namen an, den der Konstruktor danach ablehnt: Der
     * Start braeche mit einer Ausnahme statt mit einer Klage. Dieselbe Bauart und derselbe
     * Grund wie bei {@link Zeitwerte#brauchbarerTakt}.
     *
     * <p>Zwei Fragen und nicht eine, damit die Klage sagen kann, WAS nicht stimmt: Die
     * Laenge nennt eine Zahl, die Zeichen nennen die erlaubten.
     */
    public static boolean brauchbareLaenge(String roh) {
        return roh != null && !roh.isBlank()
                && roh.trim().length() <= HOECHSTENS_ZEICHEN;
    }

    /** Die zweite Haelfte derselben Regel - siehe {@link #brauchbareLaenge}. */
    public static boolean brauchbareZeichen(String roh) {
        return roh != null && roh.trim().toLowerCase(Locale.ROOT).matches(MUSTER);
    }

    /**
     * Pruefung und Kleinschreibung in einem - fuer beide Felder dieselbe Regel.
     *
     * <p>Zwei Abschriften derselben Pruefung waeren zwei Orte fuer dieselbe Falle, und die
     * zweite bekaeme die naechste Verschaerfung nicht mit.
     */
    private static String gepruefterName(String roh, String klage) {
        if (roh == null || roh.isBlank()) {
            throw new IllegalArgumentException(klage + " - ein Name ist Pflicht");
        }
        String sauber = roh.trim().toLowerCase(Locale.ROOT);
        if (sauber.length() > HOECHSTENS_ZEICHEN) {
            throw new IllegalArgumentException("Der Berufsschluessel \"" + sauber
                    + "\" ist " + sauber.length() + " Zeichen lang, erlaubt sind "
                    + HOECHSTENS_ZEICHEN + ". Laenger schneidet die Ablage stumm ab.");
        }
        if (!sauber.matches(MUSTER)) {
            throw new IllegalArgumentException("Der Berufsschluessel \"" + sauber
                    + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern und"
                    + " Bindestrich, und beginnen muss er mit einem Buchstaben. Er steht"
                    + " in der Ablage, in der Sprachdatei und im getippten Befehl - drei"
                    + " Orte, die alle dasselbe Zeichen lesen muessen.");
        }
        return sauber;
    }

    /** Ob dieser Beruf zu einem Grundberuf gehoert. */
    public boolean istSpezialisierung() {
        return grundberuf != null;
    }
}
