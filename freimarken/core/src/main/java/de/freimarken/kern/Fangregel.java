package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Was an den Haken geht - die Wahl aus der Angeltabelle (Plan 4.1, Schritt 10).
 *
 * <p>Reine Rechnerei ohne Bukkit UND OHNE ZUFALL: Beide Wuerfe kommen als Zahl herein.
 * Das ist dieselbe Entscheidung wie bei {@code Tagesgrenze} und der Uhr - ein Zufall im
 * Kern waere eine Probe, die manchmal gruen ist.
 *
 * <p><b>Die Reihenfolge der Tabelle ist SORTIERT und nicht die der Datei</b>, und das ist
 * die wichtigste Zeile dieser Klasse: {@code Map.copyOf} sagt keine Reihenfolge zu, und
 * sie ist je JVM-Lauf sogar eine ANDERE (die Abbildung streut mit einem Startwert, der
 * beim Start gewuerfelt wird). Ohne Sortierung gaebe derselbe Wurf heute ein
 * Schneckenhaus und morgen ein Meeresherz - und eine Probe, die einen Wurf gegen einen
 * Fang stellt, waere mal gruen und mal rot, ohne dass sich etwas geaendert hat.
 */
public final class Fangregel {

    private Fangregel() {
    }

    /**
     * Der Fang zu diesen zwei Wuerfen - oder LEER, wenn es bei Vanilla bleibt.
     *
     * <p>Zwei Wuerfe und nicht einer, weil es zwei Fragen sind: Greift die Tabelle
     * ueberhaupt ({@code haeufigkeitProzent}), und wenn ja, welche Zeile. Ein einziger
     * Wurf ueber beides waere nicht falsch, aber die Haeufigkeit liesse sich dann nicht
     * mehr unabhaengig von der Tabelle aendern - wer eine Zeile hinzufuegt, verschoebe
     * die Haeufigkeit mit.
     *
     * @param prozentWurf   0 bis 99 - unter der Haeufigkeit greift die Tabelle
     * @param gewichtsWurf  0 bis {@link Angelwerte#gewichtsumme} minus 1
     * @return der Gegenstandsname, oder leer fuer "es bleibt beim Vanillafang"
     */
    public static Optional<String> fang(Angelwerte werte, int prozentWurf,
                                        long gewichtsWurf) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Tabelle");
        }
        if (!werte.greift() || prozentWurf < 0 || prozentWurf >= werte.haeufigkeitProzent()) {
            return Optional.empty();
        }
        long summe = werte.gewichtsumme();
        if (gewichtsWurf < 0L || gewichtsWurf >= summe) {
            // Ein Wurf ausserhalb der Summe ist ein Fehler des Aufrufers, aber kein
            // Grund zu werfen: Diese Regel laeuft im Hauptfaden bei jedem Fang. Es
            // bleibt bei Vanilla, und das ist die Richtung, in der nichts kaputtgeht.
            return Optional.empty();
        }
        long gelaufen = 0L;
        for (Map.Entry<String, Integer> zeile : sortiert(werte)) {
            gelaufen += zeile.getValue();
            if (gewichtsWurf < gelaufen) {
                return Optional.of(zeile.getKey());
            }
        }
        // Unerreichbar, solange gewichtsumme und die Schleife dieselbe Tabelle lesen -
        // und sie tun es. Kein Wurf, sondern derselbe Rueckfall wie oben.
        return Optional.empty();
    }

    /**
     * Die Tabelle in STABILER Reihenfolge - nach dem Namen des Gegenstands.
     *
     * <p>Siehe den Klassenkopf: Ohne diese Zeile haengt der Fang an der Streuung einer
     * Abbildung, und die ist je JVM-Lauf eine andere.
     */
    private static List<Map.Entry<String, Integer>> sortiert(Angelwerte werte) {
        List<Map.Entry<String, Integer>> zeilen =
                new ArrayList<>(werte.gewichte().entrySet());
        zeilen.sort(Map.Entry.comparingByKey());
        return zeilen;
    }

    /**
     * Wie oft dieser Fang unter hundert Griffen in die Tabelle kommt - fuer den Bericht.
     *
     * <p>In Promille und nicht in Prozent: Das Meeresherz hat bei der ausgelieferten
     * Tabelle einen Anteil von 1 von 83, und das sind 12 Promille - als Prozentzahl
     * stuende dort eine 1, und daneben eine 1 fuer den Schwamm mit dem vierfachen
     * Gewicht. Der Betreiber soll am Bericht sehen, was er eingestellt hat.
     *
     * <p>Die HAEUFIGKEIT ist dabei eingerechnet: Greift die Tabelle nur bei jedem
     * fuenften Fang, ist der Anteil eines Fangs an ALLEN Faengen ein Fuenftel dessen,
     * was sein Gewicht sagt.
     */
    public static long promilleVonAllem(Angelwerte werte, String fang) {
        if (!werte.greift() || !werte.gewichte().containsKey(fang)) {
            return 0L;
        }
        return 1000L * werte.gewichte().get(fang) * werte.haeufigkeitProzent()
                / (werte.gewichtsumme() * 100L);
    }
}
