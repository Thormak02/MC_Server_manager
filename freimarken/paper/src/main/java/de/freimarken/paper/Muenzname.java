package de.freimarken.paper;

import de.freimarken.kern.Muenzwerte;
import java.util.Locale;
import java.util.Set;

/**
 * Der Name EINES Katalogpostens - der dritte Satz, der keinen Platz in einer Aufzaehlung
 * hat.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Berufsname} und
 * {@link Aufgabenname}: Welche Posten es gibt, steht in der {@code config.yml}, und der
 * Betreiber darf dort einen hinzufuegen. Ein {@code enum} kann das nicht - es waere ein
 * Satz, den niemand anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p><b>Und darum ist dieser Satz nicht in {@code Sprachschluessel.alle()}</b>: Jene Liste
 * verlangt, dass JEDER Satz in der ausgelieferten Vorlage steht, und ein Posten, den der
 * Betreiber erst morgen eintraegt, kann dort nicht stehen. Geprueft wird stattdessen
 * umgekehrt - beim Start wird gefragt, ob jeder Posten der {@code config.yml} eine Zeile
 * hat.
 *
 * <p><b>Ein Posten ohne Wort faellt NICHT weg</b>, wie bei der Herausforderung und anders
 * als beim Beruf: Er bleibt kaufbar und zeigt seinen Pfad. Ein haesslicher Name ist der
 * kleinere Schaden - ein weggefallener Posten waere Kosmetik, die niemand mehr bekommt,
 * und der Spieler suchte den Fehler bei sich.
 *
 * @param schluessel der Posten, kleingeschrieben wie in der Datei
 */
record Muenzname(String schluessel) implements Satz {

    /** Die Vorsilbe - sie steht hier und in {@link Sprachschluessel} nirgends sonst. */
    static final String VORSILBE = "muenzhaus.posten.";

    Muenzname {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Postenname ohne Posten");
        }
    }

    @Override
    public String pfad() {
        return VORSILBE + schluessel;
    }

    @Override
    public Art art() {
        // Ein WORT, wie der Berufs- und der Aufgabenname: Der Name wird in andere Saetze
        // eingesetzt - in die Tafel, in die Rueckfrage, in jede Absage. Ein Farbtag darin
        // ueberschriebe die Farbe der Zeile, in der er landet, und zwar je Zeile anders.
        return Art.WORT;
    }

    @Override
    public Set<String> platzhalter() {
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        // Ein leerer Name waere eine Luecke mitten in einem Satz: "Du hast  gekauft."
        return false;
    }

    /**
     * Ob dieser Pfad zu einem Postennamen gehoert.
     *
     * <p>Geprueft wird auch der REST hinter der Vorsilbe, mit derselben Regel, die der
     * Leser der {@code config.yml} anwendet - sonst waere {@code muenzhaus.posten.Hut}
     * ein gueltiger Pfad fuer einen Posten, den es nie geben kann, und eine Zeile, die
     * nicht wirkt, meldete niemand. Derselbe Fund wie bei {@link Berufsname#passt}.
     */
    static boolean passt(String pfad) {
        if (pfad == null || !pfad.startsWith(VORSILBE)) {
            return false;
        }
        String rest = pfad.substring(VORSILBE.length());
        return Muenzwerte.brauchbarerSchluessel(rest)
                && rest.equals(rest.trim().toLowerCase(Locale.ROOT));
    }

    /** Aus einem passenden Pfad wieder der Satz - siehe {@link #passt}. */
    static Muenzname ausPfad(String pfad) {
        return new Muenzname(pfad.substring(VORSILBE.length()));
    }
}
