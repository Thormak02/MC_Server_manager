package de.freimarken.paper;

import de.freimarken.kern.Berufsangabe;
import java.util.Locale;
import java.util.Set;

/**
 * Der Name EINES Berufes - der einzige Satz, der kein Platz in einer Aufzaehlung hat.
 *
 * <p>Alle anderen Saetze stehen als Konstante in einer der zwoelf Bereichsaufzaehlungen, und
 * das geht hier nicht: Welche Berufe es gibt, steht in der {@code config.yml}, und der
 * Betreiber darf dort einen hinzufuegen. Ein {@code enum} kann das nicht - es waere ein
 * Satz, den niemand anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p>Darum ist dies ein {@link Satz} als WERT und nicht als Konstante. Der Gewinn ist, dass
 * {@link Sprache} und {@link Sprachbuch} davon nichts wissen muessen: Sie fragen einen Satz
 * nach seinem Pfad, und dieser hier kennt seinen. Die EINE Stelle, die es wissen muss, ist
 * {@link Sprachschluessel#zuPfad} - dort wird aus einem Pfad wieder ein Satz, und nur dort
 * steht die Vorsilbe.
 *
 * <p><b>Und darum ist dieser Satz nicht in {@code Sprachschluessel.alle()}</b>: Diese Liste
 * verlangt, dass JEDER Satz in der ausgelieferten Vorlage steht, und ein Beruf, den der
 * Betreiber erst morgen eintraegt, kann dort nicht stehen. Geprueft wird stattdessen
 * umgekehrt - beim Start wird gefragt, ob jeder Beruf der {@code config.yml} eine Zeile
 * hat, und ohne die bleibt Freimarken aus. Lieber ein verweigerter Start als ein Zunfthaus,
 * in dem "bergmann" steht.
 *
 * @param schluessel der Beruf, kleingeschrieben wie in der Datei
 */
record Berufsname(String schluessel) implements Satz {

    /** Die Vorsilbe - sie steht hier und in {@link Sprachschluessel} nirgends sonst. */
    static final String VORSILBE = "beruf.name.";

    Berufsname {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Berufsname ohne Beruf");
        }
    }

    @Override
    public String pfad() {
        return VORSILBE + schluessel;
    }

    @Override
    public Art art() {
        // Ein WORT und kein SATZ: Der Name wird in andere Saetze eingesetzt - in die
        // Liste, in den Stand, in jede Zusage und in jede Absage. Ein Farbtag darin
        // ueberschriebe die Farbe der Zeile, in der er landet, und zwar je Zeile anders.
        return Art.WORT;
    }

    @Override
    public Set<String> platzhalter() {
        // Keiner. Ein Name ist ein Wort und kein Satz mit Einsetzungen; waere hier einer
        // erlaubt, muesste jede der elf Stellen, die den Namen einsetzt, ihn kennen.
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        // Ein leerer Name waere eine Luecke mitten in einem Satz: "Du hast  gewaehlt."
        return false;
    }

    /**
     * Ob dieser Pfad zu einem Berufsnamen gehoert.
     *
     * <p>Geprueft wird auch der REST hinter der Vorsilbe, mit derselben Regel, die der
     * Leser der {@code config.yml} anwendet: Sonst waere {@code beruf.name.Bergmann} ein
     * gueltiger Pfad fuer einen Beruf, den es nie geben kann - und eine Zeile, die nicht
     * wirkt, meldete niemand.
     */
    static boolean passt(String pfad) {
        if (pfad == null || !pfad.startsWith(VORSILBE)) {
            return false;
        }
        String rest = pfad.substring(VORSILBE.length());
        // Der Rest muss SCHON normalisiert sein und nicht erst normalisierbar: Ein Pfad
        // wird woertlich nachgeschlagen. beruf.name.Bergmann waere sonst ein gueltiger
        // Satz, den niemand je fragt - Beruftext fragt nach beruf.name.bergmann, und die
        // Zeile des Betreibers wirkte nie. Gemessen in SprachlisteTest 21h: Ohne diesen
        // Vergleich wurde sie angenommen und stand im Bericht unter "deine" - also als
        // eine Zeile, die gilt.
        return Berufsangabe.brauchbareLaenge(rest) && Berufsangabe.brauchbareZeichen(rest)
                && rest.equals(rest.trim().toLowerCase(Locale.ROOT));
    }

    /** Aus einem passenden Pfad wieder der Satz - siehe {@link #passt}. */
    static Berufsname ausPfad(String pfad) {
        return new Berufsname(pfad.substring(VORSILBE.length()));
    }
}
