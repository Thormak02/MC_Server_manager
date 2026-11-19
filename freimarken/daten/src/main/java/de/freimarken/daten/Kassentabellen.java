package de.freimarken.daten;

/**
 * Welche Tabellen eine Kasse benutzt - der Satz, der eine WAEHRUNG ausmacht.
 *
 * <p><b>Warum es das gibt</b> [19.11.2026, Schritt 12 Teil E]: Die Goldmark ist eine
 * zweite Waehrung, und sie bekommt eine EIGENE Kassentabelle und ein EIGENES Buch. Bis
 * hierher standen alle Tabellennamen fest in den SQL-Saetzen von {@link MariaKasse} - acht
 * Vorkommen in sieben Zeichenketten.
 *
 * <p><b>Warum eine zweite Tabellenmenge und nicht eine Waehrungsspalte</b> [entschieden
 * 18.11.2026]: Zwei Waehrungen in einer Tabelle addieren sich zu einem Stand, und die
 * tragende Zusage des Buchungsbuchs - "fuer ein eroeffnetes Konto ist die Summe aller
 * Buchungen sein Stand" - wuerde diesen Stand dann BESTAETIGEN, obwohl er zwei Dinge
 * zusammenwirft. Die Probe waere gruen, und das Geld waere falsch. Mit zwei
 * Tabellenmengen gilt die Zusage je Waehrung, und die Probe prueft sie zweimal.
 *
 * <p><b>Und keine zweite Umsetzung:</b> {@link MariaKasse} bleibt EINE Klasse. Eine Kopie
 * fuer die Goldmark waere eine zweite Fassung der Transaktionsfuehrung - und zwei
 * Fassungen derselben Sache weichen eines Tages ab. Dass die Goldmarktabellen wirklich
 * tragen, prueft darum der geteilte {@code KasseVertrag} ein zweites Mal.
 *
 * <p><b>Der Unterschied zur Staedtekasse:</b> Jene liegt ausdruecklich auf DERSELBEN
 * Tabelle wie die Spielerkonten ({@code Stadtkasse}, Klassenkopf) - eine Stadtkennung und
 * eine Spielerkennung sind nie dieselbe, und es ist dieselbe Waehrung. Hier ist es eine
 * ANDERE Waehrung, und genau das ist der Grund fuer die Trennung.
 *
 * <p>Die Namen kommen ausschliesslich aus den Konstanten dieser Klasse und nie aus einer
 * Einstellung - sie landen ungeschuetzt im SQL. Der Riegel im Konstruktor ist trotzdem
 * da: Er faengt den Vertipper eines kuenftigen Bauschrittes, und zwar beim Start und
 * nicht bei der ersten Buchung.
 *
 * @param konten        die Tabelle mit einer Zeile je Konto
 * @param buch          die Tabelle mit einer Zeile je Buchung
 * @param betragsspalte die Spalte in {@code konten}, die den Stand haelt
 */
public record Kassentabellen(String konten, String buch, String betragsspalte) {

    /** Was ein Tabellen- oder Spaltenname sein darf - diese Namen gehen ungeschuetzt ins SQL. */
    private static final String MUSTER = "[a-z][a-z_]{0,31}";

    /** Die Mark: die Waehrung des Alltags, auf den Tabellen aus Schritt 3 und 7. */
    public static final Kassentabellen MARK =
            new Kassentabellen("kassen", "buchungen", "mark");

    /**
     * Die Goldmark: die Premiumwaehrung, auf eigenen Tabellen (Schritt 32 und 33).
     *
     * <p>Die Betragsspalte heisst {@code goldmark} und nicht {@code mark}. Derselbe Name
     * waere der kuerzere Weg - die SQL-Saetze unterschieden sich dann nur im
     * Tabellennamen - und eine Luege, auf die jeder spaetere Leser hereinfaellt: Wer eine
     * Spalte {@code mark} liest, haelt den Wert fuer Mark.
     */
    public static final Kassentabellen GOLDMARK =
            new Kassentabellen("goldkassen", "goldbuchungen", "goldmark");

    public Kassentabellen {
        pruefe("konten", konten);
        pruefe("buch", buch);
        pruefe("betragsspalte", betragsspalte);
        if (konten.equals(buch)) {
            // Konten und Buch in einer Tabelle waere ein Stand, der seine eigene
            // Geschichte ueberschreibt - und die Summenprobe liefe dann gegen sich selbst.
            throw new IllegalArgumentException("Konten und Buch duerfen nicht dieselbe"
                    + " Tabelle sein: " + konten);
        }
    }

    private static void pruefe(String rolle, String name) {
        if (name == null || !name.matches(MUSTER)) {
            throw new IllegalArgumentException("Der Name fuer " + rolle + " taugt nicht: "
                    + name + ". Erlaubt sind Kleinbuchstaben und Unterstriche, beginnend"
                    + " mit einem Buchstaben, hoechstens 32 Zeichen - diese Namen gehen"
                    + " ungeschuetzt in das SQL.");
        }
    }
}
