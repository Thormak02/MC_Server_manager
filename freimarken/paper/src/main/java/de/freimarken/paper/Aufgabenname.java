package de.freimarken.paper;

import de.freimarken.kern.Aufgabenwerte;
import java.util.Locale;
import java.util.Set;

/**
 * Der Name EINER Herausforderung - der zweite Satz, der kein Platz in einer Aufzaehlung
 * hat.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Berufsname}: Welche
 * Herausforderungen es gibt, steht in der {@code config.yml}, und der Betreiber darf dort
 * eine hinzufuegen. Ein {@code enum} kann das nicht - es waere ein Satz, den niemand
 * anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p><b>Und darum ist dieser Satz nicht in {@code Sprachschluessel.alle()}</b>: Jene Liste
 * verlangt, dass JEDER Satz in der ausgelieferten Vorlage steht, und eine Aufgabe, die der
 * Betreiber erst morgen eintraegt, kann dort nicht stehen. Geprueft wird stattdessen
 * umgekehrt - beim Start wird gefragt, ob jede Aufgabe der {@code config.yml} eine Zeile
 * hat.
 *
 * <p><b>Der Unterschied zum Beruf</b>: Eine Aufgabe ohne Wort laesst Freimarken NICHT
 * ausbleiben, und sie faellt auch nicht weg - sie BLEIBT abgebbar und zeigt ihren Pfad.
 * Der Start meldet es laut.
 *
 * <p>Beides hat seinen Grund. Ein Beruf ist Pflichtteil des Spiels, und ohne ihn verdient
 * niemand etwas; eine Herausforderung ist eine von zehn in EINER Welt, und ein verweigerter
 * Start waere die teuerste Antwort auf einen Tippfehler. Und WEGFALLEN waere hier das
 * Schlimmere: Die Stufe einer Insel ist die Zahl der erfuellten Aufgaben, und eine
 * fehlende Zeile in der Sprachdatei machte eine Stufe dauerhaft unerreichbar - und damit
 * ein Erz, das niemand mehr bekommt. Ein haesslicher Name ist der kleinere Schaden.
 *
 * @param schluessel die Aufgabe, kleingeschrieben wie in der Datei
 */
record Aufgabenname(String schluessel) implements Satz {

    /** Die Vorsilbe - sie steht hier und in {@link Sprachschluessel} nirgends sonst. */
    static final String VORSILBE = "wolke.aufgabe.";

    Aufgabenname {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Aufgabenname ohne Aufgabe");
        }
    }

    @Override
    public String pfad() {
        return VORSILBE + schluessel;
    }

    @Override
    public Art art() {
        // Ein WORT und kein SATZ, wie der Berufsname: Der Name wird in andere Saetze
        // eingesetzt - in die Tafel, in die Zusage, in jede Absage. Ein Farbtag darin
        // ueberschriebe die Farbe der Zeile, in der er landet, und zwar je Zeile anders.
        return Art.WORT;
    }

    @Override
    public Set<String> platzhalter() {
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        // Ein leerer Name waere eine Luecke mitten in einem Satz: "Du hast  erfuellt."
        return false;
    }

    /**
     * Ob dieser Pfad zu einem Aufgabennamen gehoert.
     *
     * <p>Geprueft wird auch der REST hinter der Vorsilbe, mit derselben Regel, die der
     * Leser der {@code config.yml} anwendet - sonst waere {@code wolke.aufgabe.Erster}
     * ein gueltiger Pfad fuer eine Aufgabe, die es nie geben kann, und eine Zeile, die
     * nicht wirkt, meldete niemand. Derselbe Fund wie bei {@link Berufsname#passt}.
     */
    static boolean passt(String pfad) {
        if (pfad == null || !pfad.startsWith(VORSILBE)) {
            return false;
        }
        String rest = pfad.substring(VORSILBE.length());
        return Aufgabenwerte.brauchbarerSchluessel(rest)
                && rest.equals(rest.trim().toLowerCase(Locale.ROOT));
    }

    /** Aus einem passenden Pfad wieder der Satz - siehe {@link #passt}. */
    static Aufgabenname ausPfad(String pfad) {
        return new Aufgabenname(pfad.substring(VORSILBE.length()));
    }
}
