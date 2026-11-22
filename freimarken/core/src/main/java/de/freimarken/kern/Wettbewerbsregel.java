package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wer gewonnen hat und wer was bekommt - die Rechnung des Angelwettbewerbs (Plan 14).
 *
 * <p>Reine Rechnerei ohne Bukkit und ohne Zufall: Punkte herein, Rangliste und Preise
 * heraus. Damit ist die teuerste Frage des ganzen Teils - wer bekommt wie viel - ohne
 * laufenden Server durchlaufbar.
 *
 * <p><b>Die Sortierung ist STABIL, und sie muss es sein.</b> Bei Punktgleichheit
 * entscheidet die Spielerkennung. Das ist willkuerlich - aber eine unbestimmte
 * Reihenfolge waere schlimmer: Dieselben Punkte ergaeben je Serverlauf einen anderen
 * Sieger, und eine Probe darauf waere mal gruen und mal rot. Wer den Zufall nicht will,
 * macht seine Punktetabelle feiner; dann gibt es seltener einen Gleichstand.
 */
public final class Wettbewerbsregel {

    private Wettbewerbsregel() {
    }

    /**
     * Die Rangliste eines Laufs - der mit den meisten Punkten zuerst.
     *
     * <p>Zeilen mit 0 Punkten stehen MIT drin: Sie sind angemeldet und haben gezahlt,
     * also gehoeren sie in die Liste. Ein Platz mit 0 Punkten kann trotzdem einen Preis
     * bekommen, wenn sonst niemand mitgespielt hat - und das ist richtig: Er hat
     * eingezahlt.
     */
    public static List<Punktzeile> rangliste(List<Punktzeile> zeilen) {
        if (zeilen == null) {
            throw new IllegalArgumentException("Ohne Zeilen gibt es keine Rangliste");
        }
        List<Punktzeile> sortiert = new ArrayList<>(zeilen);
        // Punkte ABSTEIGEND, dann die Kennung AUFSTEIGEND - siehe den Klassenkopf.
        sortiert.sort(Comparator.comparingInt(Punktzeile::punkte).reversed()
                .thenComparing(zeile -> zeile.spieler().toString()));
        return List.copyOf(sortiert);
    }

    /**
     * Was im Topf liegt: alle Startgelder plus der Zuschuss.
     *
     * <p>Die EINGEZAHLTEN Betraege und nicht "Teilnehmer mal Startgeld" - siehe
     * {@link Punktzeile}. Aendert der Betreiber das Startgeld mitten im Lauf, zahlte die
     * zweite Rechnung mehr aus, als hereinkam.
     *
     * <p>Laeuft bei {@link Long#MAX_VALUE} an und nicht darueber: Eine uebergelaufene
     * Summe waere NEGATIV, und ein negativer Topf verteilte negative Preise - also
     * Abbuchungen bei den Siegern.
     */
    public static long topf(List<Punktzeile> zeilen, long zuschuss) {
        if (zeilen == null) {
            throw new IllegalArgumentException("Ohne Zeilen gibt es keinen Topf");
        }
        if (zuschuss < 0L) {
            throw new IllegalArgumentException("Ein Zuschuss kann nicht negativ sein: "
                    + zuschuss);
        }
        long summe = zuschuss;
        for (Punktzeile zeile : zeilen) {
            if (summe > Long.MAX_VALUE - zeile.eingezahlt()) {
                return Long.MAX_VALUE;
            }
            summe += zeile.eingezahlt();
        }
        return summe;
    }

    /**
     * Wer wie viel bekommt - die Preise eines Laufs.
     *
     * <p><b>Abgerundet wird, und der Rest bleibt im Nichts.</b> Bei einem Topf von 1.000
     * und Anteilen von 50/30/20 geht alles auf; bei 999 bleiben zwei Mark uebrig. Die
     * werden NICHT verteilt: Jede Verteilung eines Rests braucht eine Regel, welcher
     * Platz ihn bekommt, und jede solche Regel ist eine Entscheidung, die niemand
     * getroffen hat. Zwei Mark, die niemand bekommt, sind eine Senke - und eine Senke
     * ist in dieser Wirtschaft nie das Problem (Plan 9.3).
     *
     * <p>Mehr Plaetze als Teilnehmer: Die ueberzaehligen Anteile fallen weg. Bei zwei
     * Teilnehmern und drei Anteilen bleiben 20 Prozent im Topf - dasselbe wie oben, und
     * aus demselben Grund.
     *
     * @param rangliste die sortierte Liste aus {@link #rangliste}
     * @param topf      was zu verteilen ist
     * @param anteile   die Prozentsaetze, erster Eintrag ist der erste Platz
     * @return je Spieler sein Preis, in der Reihenfolge der Plaetze; nur wer etwas
     *         bekommt, steht darin
     */
    public static Map<UUID, Long> preise(List<Punktzeile> rangliste, long topf,
                                         List<Integer> anteile) {
        if (rangliste == null || anteile == null) {
            throw new IllegalArgumentException("Ohne Rangliste und Anteile gibt es keine"
                    + " Preise");
        }
        if (topf < 0L) {
            throw new IllegalArgumentException("Ein Topf kann nicht negativ sein: "
                    + topf);
        }
        Map<UUID, Long> preise = new LinkedHashMap<>();
        int plaetze = Math.min(rangliste.size(), anteile.size());
        for (int platz = 0; platz < plaetze; platz++) {
            // Erst teilen, dann malnehmen - und die Reste getrennt dazu: Ein nacktes
            // topf * anteil laeuft bei einem grossen Topf ueber, und ein
            // uebergelaufener Preis waere negativ.
            long anteil = anteile.get(platz);
            long preis = topf / 100L * anteil + topf % 100L * anteil / 100L;
            if (preis > 0L) {
                preise.put(rangliste.get(platz).spieler(), preis);
            }
        }
        return Map.copyOf(preise);
    }

}
