package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Was eine Taetigkeit an einem Material einbringt - in Pfennigen, je Beruf.
 *
 * <p>Der Schluessel hat DREI Teile: Beruf, Taetigkeit, Material. Alle drei sind noetig,
 * und zwar jeder aus eigenem Grund:
 * <ul>
 *   <li><b>Beruf</b>, weil derselbe Block verschiedenen Berufen verschieden viel bringt -
 *       der Bergmann lebt von Stein, der Baumeister setzt ihn.</li>
 *   <li><b>Taetigkeit</b>, weil derselbe Beruf an derselben Art beides tun kann: Der
 *       Holzfaeller faellt Baeume UND forstet auf. Ohne diese Ebene bekaeme ein
 *       Baumeister, der Stein abbaut, den Lohn fuers Setzen.</li>
 *   <li><b>Material</b>, weil darin die ganze Kalibrierung steckt.</li>
 * </ul>
 *
 * <p><b>Der HOECHSTE Lohn gilt, nicht die Summe.</b> Ein Spieler haelt zwei Grundberufe,
 * und beide koennen fuer dieselbe Art einen Satz tragen. Summiert waere ein Block zweimal
 * bezahlt - und der Plan sagt ausdruecklich, dass die zwei Grundberufe sich das Einkommen
 * TEILEN und nicht verdoppeln. Der hoechste Satz ist dabei die freundliche Lesart: Wer
 * zwei passende Berufe haelt, bekommt den besseren, nicht den schlechteren.
 *
 * <p>Alles kleingeschrieben, und zwar hier - an EINER Stelle. Material heisst in Bukkit
 * {@code STONE}, in der Einstellungsdatei steht {@code stone}, und zwei Stellen, die
 * denselben Namen zurechtbiegen, biegen ihn eines Tages verschieden.
 */
public final class Lohntafel {

    private final Map<String, Long> saetze;

    /**
     * Die Schluessel in der Reihenfolge der DATEI - ein eigenes Feld, und zwar notwendig.
     *
     * <p>{@code Map.copyOf} verliert die Reihenfolge, und das faellt nur dort auf, wo
     * jemand sie liest: {@link #materialien} nennt die Materialien einer Taetigkeit fuer
     * den Bericht beim Start, und ohne diese Liste stuenden sie dort in einer Ordnung, die
     * sich von Lauf zu Lauf aendern kann. Gefunden hat das die Probe dazu - dieselbe
     * Falle wie bei {@link Berufsverzeichnis}, und dieselbe Loesung.
     */
    private final List<String> reihenfolge;

    private Lohntafel(LinkedHashMap<String, Long> geordnet) {
        this.saetze = Map.copyOf(geordnet);
        this.reihenfolge = List.copyOf(geordnet.keySet());
    }

    /** Eine Zeile der Lohnliste. */
    public record Lohn(String beruf, Taetigkeit taetigkeit, String material,
                       long pfennige) {

        public Lohn {
            if (beruf == null || beruf.isBlank()) {
                throw new IllegalArgumentException("Ein Lohn ohne Beruf");
            }
            if (taetigkeit == null) {
                throw new IllegalArgumentException("Ein Lohn ohne Taetigkeit");
            }
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("Ein Lohn ohne Material");
            }
            if (pfennige < 0L) {
                throw new IllegalArgumentException("Der Lohn fuer " + material + " ist "
                        + pfennige + " Pfennige. Ein negativer Lohn waere eine Gebuehr"
                        + " fuers Arbeiten, und die gehoert zu den Senken.");
            }
            beruf = beruf.trim().toLowerCase(Locale.ROOT);
            material = material.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Baut die Tafel. Eine zweite Zeile zum selben Schluessel gewinnt NICHT.
     *
     * <p>Die erste gilt, wie bei {@link Berufsverzeichnis}: Der Leser der
     * Einstellungsdatei kann eine Dublette gar nicht liefern - zwei gleiche Schluessel in
     * einer YAML-Ebene sind dort schon eine Klage -, und eine stillschweigende
     * Ueberschreibung waere die schlechtere Antwort.
     */
    public static Lohntafel ausLoehnen(List<Lohn> loehne) {
        if (loehne == null) {
            throw new IllegalArgumentException("Ohne Loehne gibt es keine Tafel; eine"
                    + " leere Liste ist erlaubt und heisst: nichts bringt etwas ein");
        }
        LinkedHashMap<String, Long> gesammelt = new LinkedHashMap<>();
        for (Lohn lohn : loehne) {
            if (lohn != null) {
                gesammelt.putIfAbsent(
                        schluessel(lohn.beruf(), lohn.taetigkeit(), lohn.material()),
                        lohn.pfennige());
            }
        }
        return new Lohntafel(gesammelt);
    }

    /** Wie viele Zeilen die Tafel traegt - fuer den Bericht beim Start. */
    public int anzahl() {
        return saetze.size();
    }

    public boolean leer() {
        return saetze.isEmpty();
    }

    /**
     * Der Satz fuer genau einen Beruf, oder 0.
     *
     * <p>0 und kein {@code null}: Was nicht in der Liste steht, bringt nichts ein. Das ist
     * der Normalfall - die Liste nennt die Materialien, die zahlen, und nicht die
     * tausend, die es nicht tun.
     */
    public long pfennige(String beruf, Taetigkeit taetigkeit, String material) {
        if (beruf == null || taetigkeit == null || material == null) {
            return 0L;
        }
        return saetze.getOrDefault(schluessel(beruf.trim().toLowerCase(Locale.ROOT),
                taetigkeit, material.trim().toLowerCase(Locale.ROOT)), 0L);
    }

    /**
     * Der HOECHSTE Satz unter den gehaltenen Berufen - die Antwort fuer die Regel.
     *
     * <p>Siehe den Klassenkopf: nicht die Summe. Ein Block zahlt einmal.
     */
    public long hoechsterLohn(Set<String> berufe, Taetigkeit taetigkeit,
                              String material) {
        if (berufe == null) {
            throw new IllegalArgumentException("Ohne Berufe gibt es nichts zu verdienen;"
                    + " eine leere Menge ist erlaubt und heisst: kein Beruf, kein Lohn");
        }
        long beste = 0L;
        for (String beruf : berufe) {
            beste = Math.max(beste, pfennige(beruf, taetigkeit, material));
        }
        return beste;
    }

    /** Alle Materialien, die diese Taetigkeit bezahlt - fuer den Bericht und die Proben. */
    public List<String> materialien(Taetigkeit taetigkeit) {
        List<String> gefunden = new ArrayList<>();
        if (taetigkeit == null) {
            return List.copyOf(gefunden);
        }
        String mitte = "/" + taetigkeit.schluessel() + "/";
        for (String schluessel : reihenfolge) {
            int stelle = schluessel.indexOf(mitte);
            if (stelle >= 0) {
                String material = schluessel.substring(stelle + mitte.length());
                if (!gefunden.contains(material)) {
                    gefunden.add(material);
                }
            }
        }
        return List.copyOf(gefunden);
    }

    /**
     * Zwei Tafeln mit denselben Zeilen sind GLEICH.
     *
     * <p>Noetig und keine Zierde: {@link Verdienstwerte} ist ein Satz, {@link Wirtschaftswerte}
     * ebenso, und beide vergleichen ihre Teile. Ohne diese zwei Methoden verglichen sie die
     * Tafel ueber die Objektgleichheit - dann waere die ausgelieferte Datei nie gleich der
     * Vorgabe des Programms, obwohl Zeile fuer Zeile dasselbe darin steht, und die Probe,
     * die genau das zusichert, waere rot ohne Befund.
     */
    @Override
    public boolean equals(Object andere) {
        return andere instanceof Lohntafel tafel && saetze.equals(tafel.saetze);
    }

    @Override
    public int hashCode() {
        return saetze.hashCode();
    }

    @Override
    public String toString() {
        return "Lohntafel mit " + saetze.size() + " Zeilen";
    }

    /**
     * Der Schluessel der Tabelle.
     *
     * <p>Mit Schraegstrich getrennt, weil ein Beruf nur Kleinbuchstaben, Ziffern und
     * Bindestrich tragen darf ({@link Berufsangabe}) und ein Materialname Buchstaben und
     * Unterstrich - der Schraegstrich kommt in keinem von beiden vor und kann darum nicht
     * zwei verschiedene Schluessel zu einem machen.
     */
    private static String schluessel(String beruf, Taetigkeit taetigkeit,
                                     String material) {
        return beruf + "/" + taetigkeit.schluessel() + "/" + material;
    }
}
