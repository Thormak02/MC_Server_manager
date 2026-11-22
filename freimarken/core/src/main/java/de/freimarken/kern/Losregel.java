package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Was im Los ist - die Wahl aus der Gewinntabelle (Plan 14).
 *
 * <p>Reine Rechnerei ohne Bukkit UND OHNE ZUFALL, genau wie {@link Fangregel}: Der Wurf
 * kommt als Zahl herein. Ein Zufall im Kern waere eine Probe, die manchmal gruen ist.
 *
 * <p><b>Gezogen wird aus der SORTIERTEN Tabelle</b>, und das ist die wichtigste Zeile
 * dieser Klasse - dieselbe wie in {@link Fangregel}, aber aus einem anderen Grund.
 * {@link Lotteriewerte#gewinne} ist eine {@code List}, und {@code List.copyOf} sagt ihre
 * Reihenfolge durchaus zu; die Frage ist, woher die Liste kommt. Sie entsteht in
 * {@code Lotterieliste} aus {@code ConfigurationSection.getKeys(false)}, und DASS die
 * Reihenfolge der Datei dort erhalten bleibt, sagt Bukkit nirgends zu - es ist heute so,
 * weil hinter der Fassade eine {@code LinkedHashMap} liegt.
 *
 * <p>Ohne Sortierung haengt also die Ziehung an einer undokumentierten Eigenschaft einer
 * fremden Bibliothek: Derselbe Wurf gaebe nach einem Bukkit-Umbau einen anderen Gewinn,
 * und eine Probe darauf waere mal gruen und mal rot, ohne dass sich hier etwas geaendert
 * haette. Der Preis der Sortierung ist, dass die Reihenfolge in der Einstellungsdatei
 * fuer die Ziehung bedeutungslos ist - fuer den Betreiber aendert das nichts, denn die
 * Haeufigkeit macht das Gewicht und nicht der Platz in der Liste.
 */
public final class Losregel {

    private Losregel() {
    }

    /**
     * Der Gewinn zu diesem Wurf.
     *
     * <p>EIN Wurf und nicht zwei, anders als bei {@link Fangregel#fang}: Dort gibt es
     * zwei Fragen (greift die Tabelle ueberhaupt, und welche Zeile), weil der Fang auch
     * Vanilla bleiben kann. Ein Los hat diese zweite Moeglichkeit nicht - es ist gezogen
     * und bezahlt, also gibt es einen Gewinn. Die NIETE ist deshalb eine Zeile der
     * Tabelle und kein leeres Ergebnis: Wer nichts gewinnen soll, gewinnt den
     * Trostbetrag, und das steht sichtbar in der Datei.
     *
     * @param werte die Lotterie
     * @param wurf  0 bis {@link Lotteriewerte#gewichtsumme} minus 1
     * @return der Gewinn, oder leer, wenn die Tabelle nicht greift oder der Wurf
     *         ausserhalb liegt
     * @throws IllegalArgumentException wenn keine Werte hereinkommen
     */
    public static Optional<Lotteriewerte.Gewinn> ziehe(Lotteriewerte werte, long wurf) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Lotterie");
        }
        if (!werte.greift()) {
            return Optional.empty();
        }
        long summe = werte.gewichtsumme();
        if (wurf < 0L || wurf >= summe) {
            // Ein Wurf ausserhalb der Summe ist ein Fehler des Aufrufers, aber kein
            // Grund zu werfen - dieselbe Haltung wie in Fangregel. Leer heisst hier
            // "kein Los", und der Aufrufer darf dann nichts abbuchen.
            return Optional.empty();
        }
        long gelaufen = 0L;
        for (Lotteriewerte.Gewinn gewinn : sortiert(werte)) {
            gelaufen += gewinn.gewicht();
            if (wurf < gelaufen) {
                return Optional.of(gewinn);
            }
        }
        // Unerreichbar, solange gewichtsumme und die Schleife dieselbe Tabelle lesen -
        // und sie tun es.
        return Optional.empty();
    }

    /**
     * Die Tabelle in STABILER Reihenfolge - nach dem Schluessel des Gewinns.
     *
     * <p>Siehe den Klassenkopf: Ohne diese Zeile haengt die Ziehung daran, dass Bukkit
     * die Reihenfolge der Einstellungsdatei erhaelt - und das sagt Bukkit nirgends zu.
     *
     * <p>Der Schluessel und nicht der Anzeigename: Der Schluessel ist in der Tabelle
     * eindeutig (der Konstruktor von {@link Lotteriewerte} weist Doppelte ab), ein
     * Anzeigename ist es nicht. Zwei Zeilen mit demselben Sortierwert waeren wieder eine
     * unbestimmte Reihenfolge, nur eine Ebene tiefer.
     */
    private static List<Lotteriewerte.Gewinn> sortiert(Lotteriewerte werte) {
        List<Lotteriewerte.Gewinn> zeilen = new ArrayList<>(werte.gewinne());
        zeilen.sort(java.util.Comparator.comparing(Lotteriewerte.Gewinn::schluessel));
        return zeilen;
    }

    /**
     * Wie oft dieser Gewinn unter tausend Losen kommt - fuer den Bericht.
     *
     * <p>In Promille und nicht in Prozent, aus demselben Grund wie bei
     * {@link Fangregel#promilleVonAllem}: Das Meeresherz hat bei der ausgelieferten
     * Tabelle ein Gewicht von 2 unter 1000, und als Prozentzahl stuende dort eine 0.
     * Der Betreiber soll am Bericht sehen, was er eingestellt hat - eine 0 fuer den
     * seltensten und wertvollsten Gewinn der Tabelle verbirgt genau das.
     */
    public static long promilleVon(Lotteriewerte werte, String schluessel) {
        if (werte == null || !werte.greift()) {
            return 0L;
        }
        for (Lotteriewerte.Gewinn gewinn : werte.gewinne()) {
            if (gewinn.schluessel().equals(schluessel)) {
                return 1000L * gewinn.gewicht() / werte.gewichtsumme();
            }
        }
        return 0L;
    }

    /**
     * Was ein Los im Mittel einbringt, in Mark - fuer den Bericht.
     *
     * <p>Die einzige Zahl, an der ein Betreiber sieht, ob seine Lotterie eine SENKE ist:
     * Liegt der Mittelwert ueber dem Preis der ersten Lose, ist sie eine Geldquelle, und
     * dann laeuft die Wirtschaft voll (Plan 9.3). Die Zahl steht deshalb im Bericht des
     * Lesers, nicht nur in einer Probe.
     *
     * <p>GEGENSTAENDE und GOLDMARK zaehlen hier NICHT mit, und das ist eine Luecke mit
     * Absicht: Was ein Diamant in Mark wert ist, weiss nur der Markt, und ein geschaetzter
     * Kurs im Bericht waere eine Zahl, die niemand nachrechnen kann. Der Bericht nennt
     * sie deshalb getrennt, als Promille - siehe {@link #promilleVon}.
     */
    public static long mittlererMarkgewinn(Lotteriewerte werte) {
        if (werte == null || !werte.greift()) {
            return 0L;
        }
        long summe = 0L;
        for (Lotteriewerte.Gewinn gewinn : werte.gewinne()) {
            if (gewinn.art() != Lotteriewerte.Art.MARK) {
                continue;
            }
            // Jede Zeile fuer sich ist gedeckelt (Gewinn.HOECHSTER_GELDGEWINN), die
            // SUMME ueber die Tabelle ist es nicht - dieselbe Aufteilung wie bei
            // Preisstaffel.gesamtpreisFuer. Eine stehengebliebene Hoechstzahl im Bericht
            // ist als Unsinn sichtbar; eine uebergelaufene waere NEGATIV und sieht aus
            // wie eine Lotterie, die Geld einsammelt.
            long teil = gewinn.menge();
            if (teil > Long.MAX_VALUE / gewinn.gewicht()) {
                return Long.MAX_VALUE;
            }
            teil *= gewinn.gewicht();
            if (summe > Long.MAX_VALUE - teil) {
                return Long.MAX_VALUE;
            }
            summe += teil;
        }
        return summe / werte.gewichtsumme();
    }
}
