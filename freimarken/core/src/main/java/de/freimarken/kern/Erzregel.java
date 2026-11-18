package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Was aus Lava und Wasser wird - die Wahl aus der Erztabelle (Plan 4.1, Schritt 11).
 *
 * <p>Zeile fuer Zeile die {@link Fangregel}, und aus denselben Gruenden: reine Rechnerei
 * ohne Bukkit UND OHNE ZUFALL (beide Wuerfe kommen als Zahl herein), und die Tabelle wird
 * SORTIERT gelesen - {@code Map.copyOf} sagt keine Reihenfolge zu, und sie ist je
 * JVM-Lauf eine andere. Ohne Sortierung gaebe derselbe Wurf heute Kohle und morgen
 * Diamant, und eine Probe waere mal gruen und mal rot, ohne dass sich etwas geaendert
 * hat.
 *
 * <p><b>Der Unterschied zur Fangregel ist die STUFE</b>, und sie wirkt an zwei Stellen:
 * Sie entscheidet, wie oft ueberhaupt getauscht wird ({@link Erzwerte#prozentFuer}), und
 * welche Zeilen mitspielen ({@link Erzwerte.Erzzeile#abStufe}). Beides zusammen ist die
 * "steigende Erzausbeute" aus Plan 4.1 - und sie steigt in zwei Richtungen, in der Menge
 * und in der Guete.
 */
public final class Erzregel {

    private Erzregel() {
    }

    /**
     * Das Erz zu diesen zwei Wuerfen - oder LEER, wenn es bei Kobbelstein bleibt.
     *
     * <p>Zwei Wuerfe und nicht einer, wie bei der Fangregel: Greift die Tabelle
     * ueberhaupt, und wenn ja, welche Zeile. Ein einziger Wurf ueber beides liesse die
     * Haeufigkeit nicht mehr unabhaengig von der Tabelle aendern.
     *
     * @param stufe        die Insel-Stufe - die Zahl ihrer erfuellten Herausforderungen
     * @param prozentWurf  0 bis 99 - unter {@link Erzwerte#prozentFuer} wird getauscht
     * @param gewichtsWurf 0 bis {@link Erzwerte#gewichtsumme} minus 1
     * @return der Gegenstandsname, oder leer fuer "es bleibt Kobbelstein"
     */
    public static Optional<String> erz(Erzwerte werte, int stufe, int prozentWurf,
                                       long gewichtsWurf) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Tabelle");
        }
        if (prozentWurf < 0 || prozentWurf >= werte.prozentFuer(stufe)) {
            return Optional.empty();
        }
        long summe = werte.gewichtsumme(stufe);
        if (summe <= 0L) {
            // Keine Zeile erreicht diese Stufe. Kommt vor, wenn die niedrigste Zeile
            // eine Mindeststufe traegt - dann bleibt es bei Kobbelstein, obwohl der
            // Prozentwurf getroffen hat.
            return Optional.empty();
        }
        if (gewichtsWurf < 0L || gewichtsWurf >= summe) {
            // Ein Wurf ausserhalb der Summe ist ein Fehler des Aufrufers, aber kein
            // Grund zu werfen: Diese Regel laeuft im Hauptfaden bei jedem gebildeten
            // Block. Es bleibt bei Kobbelstein, und das ist die Richtung, in der nichts
            // kaputtgeht.
            return Optional.empty();
        }
        long gelaufen = 0L;
        for (Map.Entry<String, Erzwerte.Erzzeile> zeile : sortiert(werte, stufe)) {
            gelaufen += zeile.getValue().gewicht();
            if (gewichtsWurf < gelaufen) {
                return Optional.of(zeile.getKey());
            }
        }
        // Unerreichbar, solange gewichtsumme und die Schleife dieselbe Tabelle und
        // dieselbe Stufe lesen - und sie tun es.
        return Optional.empty();
    }

    /**
     * Die Zeilen dieser Stufe in STABILER Reihenfolge - nach dem Namen des Erzes.
     *
     * <p>Gefiltert UND sortiert an einer Stelle, denn beides muss zu
     * {@link Erzwerte#gewichtsumme} passen: Liefe die Summe ueber mehr Zeilen als die
     * Schleife, zeigte ein hoher Wurf auf eine Zeile, die es nicht gibt - und das Ergebnis
     * waere der Rueckfall, also Kobbelstein, und zwar genau bei den seltensten Erzen.
     */
    private static List<Map.Entry<String, Erzwerte.Erzzeile>> sortiert(Erzwerte werte,
                                                                      int stufe) {
        List<Map.Entry<String, Erzwerte.Erzzeile>> zeilen = new ArrayList<>();
        for (var zeile : werte.tabelle().entrySet()) {
            if (zeile.getValue().abStufe() <= stufe) {
                zeilen.add(zeile);
            }
        }
        zeilen.sort(Map.Entry.comparingByKey());
        return zeilen;
    }

    /**
     * Wie oft dieses Erz bei dieser Stufe unter tausend gebildeten Bloecken kommt.
     *
     * <p>In Promille und mit der Haeufigkeit eingerechnet - wie
     * {@link Fangregel#promilleVonAllem}, und aus demselben Grund: Der Betreiber soll am
     * Bericht sehen, was er eingestellt hat, und bei einem Gewicht von 1 unter 144 stuende
     * als Prozentzahl eine 0.
     */
    public static long promilleBeiStufe(Erzwerte werte, String erz, int stufe) {
        Erzwerte.Erzzeile zeile = werte.tabelle().get(erz);
        if (zeile == null || zeile.abStufe() > stufe) {
            return 0L;
        }
        long summe = werte.gewichtsumme(stufe);
        if (summe <= 0L) {
            return 0L;
        }
        return 1000L * zeile.gewicht() * werte.prozentFuer(stufe) / (summe * 100L);
    }
}
