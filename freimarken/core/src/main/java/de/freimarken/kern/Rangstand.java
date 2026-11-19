package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Auf welcher Stufe ein Spieler steht - und seit wann.
 *
 * <p><b>Ein Stand OHNE Rang ist der Normalfall und kein Fehler.</b> Wer nie aufgestiegen
 * ist, hat keine Zeile in der Ablage, und dann steht hier {@code null}. Was dann gilt,
 * sagt die Leiter und nicht dieser Satz: Es ist die unterste Stufe. Die Trennung ist
 * dieselbe wie bei {@link Berufsstand}, der die ROLLE eines Berufes nicht kennt - eine
 * Ablage, die die unterste Stufe selbst einsetzte, muesste die Leiter kennen, und bei
 * einer geaenderten Datei stuende in der Datenbank die Stufe von gestern.
 *
 * <p><b>Rang und Zeitpunkt sind untrennbar</b>, beide oder keiner. Ein Rang ohne
 * Zeitpunkt waere eine Zeile, der die Spalte {@code erreicht_am} fehlt - die gibt es in
 * der Tabelle nicht, und sie darf es im Programm auch nicht geben, sonst schreibt eine
 * Stelle eine Zeile, die die Datenbank ablehnt. Ein Zeitpunkt ohne Rang waere ein
 * Aufstieg auf nichts.
 *
 * @param spieler    wessen Stand das ist
 * @param rang       der Schluessel der erreichten Stufe, kleingeschrieben wie in der
 *                   Datei - oder {@code null} fuer "noch nie aufgestiegen"
 * @param erreichtAm wann er diese Stufe erreicht hat, oder {@code null} zusammen mit dem
 *                   Rang
 */
public record Rangstand(UUID spieler, String rang, Instant erreichtAm) {

    public Rangstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keinen Rangstand");
        }
        if ((rang == null) != (erreichtAm == null)) {
            // Beides oder keines. Ein halber Stand kaeme aus einer Zeile, die es in der
            // Tabelle nicht geben kann - und er liefe erst beim Schreiben auf, als
            // Datenbankfehler mitten im Spiel.
            throw new IllegalArgumentException("Rang und Zeitpunkt gehoeren zusammen:"
                    + " rang=" + rang + ", erreicht_am=" + erreichtAm + ". Wer nie"
                    + " aufgestiegen ist, hat KEINEN von beiden - dann gilt die unterste"
                    + " Stufe der Leiter.");
        }
        if (rang != null) {
            rang = rang.trim().toLowerCase(Locale.ROOT);
            if (!rang.matches(Rangwerte.MUSTER)) {
                throw new IllegalArgumentException("Der Rang \"" + rang + "\" von "
                        + spieler + " taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                        + " Unterstrich und Bindestrich, und beginnen muss er mit einem"
                        + " Buchstaben.");
            }
            if (rang.length() > Rangwerte.HOECHSTE_SCHLUESSELLAENGE) {
                // Dieselbe Naht wie in Rangwerte.Stufe: Die Spalte ist VARCHAR(32).
                // Hier noch einmal, weil ein Stand auch aus der ABLAGE kommt und nicht
                // nur aus der Leiter - und eine Datenbank, die von Hand gefuellt wurde,
                // ist keine Leiter.
                throw new IllegalArgumentException("Der Rang \"" + rang + "\" von "
                        + spieler + " ist laenger als "
                        + Rangwerte.HOECHSTE_SCHLUESSELLAENGE
                        + " Zeichen - so lang ist die Spalte in der Ablage.");
            }
        }
    }

    /**
     * Der Stand eines Spielers, von dem die Ablage nichts weiss.
     *
     * <p>Das ist jeder, der noch nie aufgestiegen ist - also auch jeder, der gerade zum
     * ersten Mal ankommt. Fuer ihn gilt die unterste Stufe.
     */
    public static Rangstand leer(UUID spieler) {
        return new Rangstand(spieler, null, null);
    }

    /** Ob er je aufgestiegen ist. Sonst gilt die unterste Stufe der Leiter. */
    public boolean aufgestiegen() {
        return rang != null;
    }

    /**
     * Auf welcher Stufe er steht - mit der untersten als Rueckfall.
     *
     * <p>Die EINE Stelle, an der aus "keine Zeile" eine Stufe wird. Haette jeder Leser
     * seinen eigenen Rueckfall, waere der erste, der ihn vergisst, ein Spieler ohne jedes
     * Rangrecht - und das sieht im Spiel aus wie die Regel.
     *
     * <p><b>Ein Rang, den die Leiter nicht mehr kennt, faellt ebenfalls zurueck.</b> Wer
     * eine Stufe aus der Datei nimmt, laesst damit Zeilen stehen, die auf sie zeigen;
     * ohne Rueckfall bekaemen gerade die Aufgestiegenen gar nichts. Dass es passiert ist,
     * muss der Aufrufer melden - hier ist dafuer kein Platz, und still bliebe es sonst.
     */
    public Rangwerte.Stufe stufeIn(Rangwerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Leiter gibt es keine Stufe");
        }
        if (rang != null) {
            var gefunden = werte.stufe(rang);
            if (gefunden.isPresent()) {
                return gefunden.get();
            }
        }
        return werte.unterste().orElse(null);
    }

    /**
     * Ob die Leiter den abgelegten Rang noch kennt.
     *
     * <p>Fuer die Meldung, die {@link #stufeIn} nicht machen kann. {@code false} heisst:
     * In der Ablage steht eine Stufe, die es in der Datei nicht mehr gibt.
     */
    public boolean bekanntIn(Rangwerte werte) {
        return rang == null || werte.stufe(rang).isPresent();
    }
}
