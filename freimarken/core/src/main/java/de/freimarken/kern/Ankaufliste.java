package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Was der Bundesmarkt ankauft und zu welchem Preis - in Pfennigen je Stueck.
 *
 * <p>Dieselbe Bauart wie {@link Lohntafel} und aus demselben Grund: Die Tabelle steht in
 * der {@code config.yml}, nicht im Programm, und der Betreiber stellt daran die Wirtschaft
 * ein. Eine eigene Klasse und nicht die Lohntafel mitbenutzt, weil der Schluessel ein
 * anderer ist - dort Beruf, Taetigkeit und Name, hier nur das Material. Eine Tafel mit
 * leeren Feldern waere schwerer zu lesen als zwei kleine Tabellen.
 *
 * <p><b>Kleines Sortiment, niedrige Preise</b> (Plan 8.2). Die niedrigen Preise sind die
 * Senke: Der Bund zahlt weniger als ein Spieler im Laden, und genau diese Spanne ist der
 * Anreiz, miteinander zu handeln statt mit dem Server. Darum gibt es hier KEINE
 * Marktgebuehr - die fuenf Prozent aus der Senkentabelle gehoeren zum Spielerhandel in
 * der Markthalle (Schritt 8). Zwei Senken auf denselben Verkauf waeren eine zu viel, und
 * die zweite erklaert sich niemandem.
 *
 * <p><b>Was NICHT in der Liste steht, kauft der Bund nicht.</b> Das ist die sichere
 * Richtung und zugleich die Aussage des Plans: ein kleines Sortiment. Ein Rueckfall auf
 * "alles zu einem Grundpreis" machte jeden Gegenstand zu Geld, auch den, der nur in einer
 * Farm anfaellt.
 */
public final class Ankaufliste {

    /** Eine Zeile der Ankaufliste. */
    public record Ankauf(String material, long pfennige) {

        public Ankauf {
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("Ein Ankauf ohne Material");
            }
            if (pfennige < 1L) {
                throw new IllegalArgumentException("Der Ankaufpreis fuer " + material
                        + " ist " + pfennige + " Pfennige. Unter 1 ist kein Ankauf,"
                        + " sondern eine Annahme - und was der Bund nicht kaufen soll,"
                        + " steht gar nicht in der Liste.");
            }
            // Die Obergrenze steht bei der REGEL und nicht hier: Sie ist dort
            // ausgerechnet (ein Inventar voll mal Preis muss in einen long passen), und
            // zwei Zahlen fuer dieselbe Schranke liefen auseinander.
            if (pfennige > Verkaufsregel.HOECHSTER_PREIS) {
                throw new IllegalArgumentException("Der Ankaufpreis fuer " + material
                        + " ist " + pfennige + " Pfennige und damit ueber "
                        + Verkaufsregel.HOECHSTER_PREIS + " - dort laeuft die Rechnung"
                        + " ueber, und ein uebergelaufener Betrag ist negativ.");
            }
            material = material.trim().toLowerCase(Locale.ROOT);
        }
    }

    private final Map<String, Long> preise;

    /**
     * Die Reihenfolge der Datei.
     *
     * <p>{@code Map.copyOf} verliert sie, und das faellt genau dort auf, wo jemand sie
     * liest: {@code /markt liste} nennt das Sortiment, und ohne diese Liste stuende es in
     * einer Ordnung, die sich von Lauf zu Lauf aendert. Dieselbe Falle wie bei
     * {@link Lohntafel} und {@link Berufsverzeichnis}, und dieselbe Loesung.
     */
    private final List<String> reihenfolge;

    private Ankaufliste(LinkedHashMap<String, Long> geordnet) {
        this.preise = Map.copyOf(geordnet);
        this.reihenfolge = List.copyOf(geordnet.keySet());
    }

    /**
     * Baut die Liste. Eine zweite Zeile zum selben Material gewinnt NICHT.
     *
     * <p>Die erste gilt, wie bei {@link Lohntafel}: Zwei gleiche Schluessel in einer
     * YAML-Ebene sind beim Leser schon eine Klage, und eine stillschweigende
     * Ueberschreibung waere die schlechtere Antwort.
     */
    public static Ankaufliste ausAnkaeufen(List<Ankauf> ankaeufe) {
        if (ankaeufe == null) {
            throw new IllegalArgumentException("Ohne Ankaeufe gibt es keine Liste; eine"
                    + " leere Liste ist erlaubt und heisst: der Bund kauft nichts");
        }
        LinkedHashMap<String, Long> gesammelt = new LinkedHashMap<>();
        for (Ankauf ankauf : ankaeufe) {
            if (ankauf != null) {
                gesammelt.putIfAbsent(ankauf.material(), ankauf.pfennige());
            }
        }
        return new Ankaufliste(gesammelt);
    }

    /** Wie viele Zeilen die Liste traegt - fuer den Bericht beim Start. */
    public int anzahl() {
        return preise.size();
    }

    /** Ob der Bund gar nichts kauft. */
    public boolean leer() {
        return preise.isEmpty();
    }

    /**
     * Was EIN Stueck dieses Materials einbringt, in Pfennigen.
     *
     * @return 0, wenn der Bund es nicht kauft - und das ist eine Antwort und kein Fehler
     */
    public long pfennige(String material) {
        if (material == null) {
            return 0L;
        }
        Long preis = preise.get(material.trim().toLowerCase(Locale.ROOT));
        return preis == null ? 0L : preis;
    }

    /** Das Sortiment in der Reihenfolge der Datei - fuer {@code /markt liste}. */
    public List<String> materialien() {
        return reihenfolge;
    }

    @Override
    public boolean equals(Object andere) {
        return andere instanceof Ankaufliste liste && preise.equals(liste.preise);
    }

    @Override
    public int hashCode() {
        return preise.hashCode();
    }

    @Override
    public String toString() {
        return "Ankaufliste[" + preise.size() + " Materialien]";
    }
}
