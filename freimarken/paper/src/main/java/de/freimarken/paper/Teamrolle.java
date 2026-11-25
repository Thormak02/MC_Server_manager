package de.freimarken.paper;

import de.freimarken.kern.Teamwerte;
import java.util.Locale;
import java.util.Set;

/**
 * Der Name EINER Teamrolle - wie {@link Berufsname} ein Satz als WERT und nicht als
 * Konstante.
 *
 * <p>Die dritte offene Satzfamilie dieses Projekts, nach {@link Berufsname} und
 * {@link Aufgabenname}, und aus demselben Grund: Welche Rollen es gibt, steht in der
 * {@code config.yml}, und der Betreiber darf dort eine hinzufuegen. Ein {@code enum}
 * waere ein Satz, den niemand anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p><b>Darum ist dieser Satz nicht in {@code Sprachschluessel.alle()}</b>: Diese Liste
 * verlangt, dass JEDER Satz in der ausgelieferten Vorlage steht, und eine Rolle, die der
 * Betreiber erst morgen eintraegt, kann dort nicht stehen. Geprueft wird stattdessen
 * umgekehrt - beim Start wird gefragt, ob jede Rolle der {@code config.yml} eine Zeile
 * hat.
 *
 * <p><b>Ohne die Zeile bleibt Freimarken aber NICHT aus</b>, und das ist der Unterschied
 * zu {@link Berufsname}. Ein Beruf ohne Namen steht einem Spieler im Zunfthaus vor der
 * Nase; eine Rolle ohne Namen steht in {@code /team}, und das liest nur, wer Rollen
 * vergibt. Ein verweigerter Start wegen einer fehlenden Zeile in einem Betreiberwerkzeug
 * waere ein Server, der nicht laeuft, weil eine Verwaltungsliste haesslich aussieht.
 * Gemeldet wird es beim Start, laut und mit der Vorlage im Satz.
 *
 * @param schluessel die Rolle, kleingeschrieben wie in der Datei
 */
record Teamrolle(String schluessel) implements Satz {

    /** Die Vorsilbe - sie steht hier und in {@link Sprachschluessel} nirgends sonst. */
    static final String VORSILBE = "team.rolle.";

    Teamrolle {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Rollenname ohne Rolle");
        }
    }

    @Override
    public String pfad() {
        return VORSILBE + schluessel;
    }

    @Override
    public Art art() {
        // Ein WORT und kein SATZ: Der Name wird in andere Saetze eingesetzt - in die
        // Liste, in jede Zusage und in jede Absage. Ein Farbtag darin ueberschriebe die
        // Farbe der Zeile, in der er landet, und zwar je Zeile anders.
        return Art.WORT;
    }

    @Override
    public Set<String> platzhalter() {
        // Keiner. Ein Name ist ein Wort und kein Satz mit Einsetzungen.
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        // Ein leerer Name waere eine Luecke mitten in einem Satz: "Du hast  vergeben."
        return false;
    }

    /**
     * Ob dieser Pfad zu einem Rollennamen gehoert.
     *
     * <p>Geprueft wird auch der REST hinter der Vorsilbe, mit derselben Regel, die der
     * Leser der {@code config.yml} anwendet - sonst waere {@code team.rolle.Moderator}
     * ein gueltiger Pfad fuer eine Rolle, die es nie geben kann, und eine Zeile, die
     * nicht wirkt, meldete niemand. Dasselbe, was an {@link Berufsname#passt} am
     * 23.11.2026 gemessen wurde: Ohne diesen Vergleich stand sie im Bericht unter
     * "deine" - also als eine Zeile, die gilt.
     */
    static boolean passt(String pfad) {
        if (pfad == null || !pfad.startsWith(VORSILBE)) {
            return false;
        }
        String rest = pfad.substring(VORSILBE.length());
        return !rest.isBlank()
                && rest.length() <= Teamwerte.HOECHSTE_SCHLUESSELLAENGE
                && rest.matches(Teamwerte.MUSTER)
                && rest.equals(rest.trim().toLowerCase(Locale.ROOT));
    }

    /** Aus einem passenden Pfad wieder der Satz - siehe {@link #passt}. */
    static Teamrolle ausPfad(String pfad) {
        return new Teamrolle(pfad.substring(VORSILBE.length()));
    }
}
