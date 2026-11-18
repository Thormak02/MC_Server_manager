package de.freimarken.kern;

import java.util.List;
import java.util.Optional;

/**
 * Das Fenster einer Feldrasterwelt: wie viele Felder sie hat, wie weit ihre Grenze reicht
 * und welches Feld noch darin liegt.
 *
 * <p>{@link Feldrechnung} rechnet ein Raster, das endlos gedacht ist - Feldnummer, Anfang,
 * Mitte. Hier kommt das hinzu, was eine WELT daraus macht: eine Feldzahl, und mit ihr ein
 * Rand. Beides zusammen beantwortet die Fragen, die der Weltenbau, der Warp und jede
 * Besitzpruefung stellen.
 *
 * <p><b>Warum das eine eigene Stelle ist</b> [18.11.2026]: {@code Feldgrenze} (Freistaedte,
 * seit dem 11.11.) und {@code Inselgrenze} (Hafenwerder, seit dem 18.11.) waren bis auf
 * drei Dinge dieselbe Klasse - den Plantyp, den Rueckfall aus der Einstellungsdatei und
 * das Wort fuer das, was gezaehlt wird. Mit Wolkenwerder waere es die dritte Abschrift
 * geworden, und damit drei Orte, an denen {@link #von}, {@link #bis} und {@link #mitte}
 * eines Tages auseinanderlaufen. Die drei Klassen bleiben - ein Weltenbau, der
 * {@code Wolkengrenze} sagt, liest sich besser als einer mit drei gleich aussehenden
 * Fenstern -, aber sie rechnen nicht mehr selbst.
 *
 * <p>Nicht hier steht, was im Zwischenraum liegt, welche Kante die Datei ausliefert und
 * wie das Gezaehlte heisst. Das ist die Sache der Welt: Bei den Freistaedten sind es
 * Staedte, bei Hafenwerder Inseln, bei Wolkenwerder Wolkeninseln.
 *
 * @param rechnung      das Feldraster - Feldkante und Zwischenraum
 * @param felderJeAchse wie viele Felder auf einer Achse liegen; mindestens 2 und gerade
 */
public record Feldfenster(Feldrechnung rechnung, int felderJeAchse) {

    public Feldfenster {
        if (rechnung == null) {
            throw new IllegalArgumentException("Ohne Feldrechnung gibt es kein Fenster.");
        }
        if (felderJeAchse < 2) {
            throw new IllegalArgumentException("Ein Fenster braucht mindestens 2 Felder je"
                    + " Achse, nicht " + felderJeAchse);
        }
        if (felderJeAchse % 2 != 0) {
            // Nur bei gerader Zahl liegen die Felder symmetrisch um den Ursprung, und nur
            // dann ist die Mitte wirklich weltmitte() - siehe Feldrechnung.felderFuer.
            // Bei ungerader Zahl saesse Feld 0/0, das Ankunftsfeld jeder dieser Welten,
            // nicht mehr in der Mitte.
            throw new IllegalArgumentException("Die Feldzahl je Achse muss gerade sein,"
                    + " nicht " + felderJeAchse);
        }
    }

    /**
     * Das Fenster zu einer gewuenschten Kante - abgerundet auf ganze Felder und auf eine
     * gerade Feldzahl ({@link Feldrechnung#felderFuer}).
     *
     * @param feldwort wie die Felder dieser Welt heissen, fuer die Fehlermeldung
     * @throws IllegalArgumentException wenn die Kante keine zwei Felder traegt oder
     *                                  oberhalb dessen liegt, was eine Weltgrenze annimmt
     */
    public static Feldfenster ausKante(Feldrechnung rechnung, int gewuenschteKante,
                                       String feldwort) {
        int mindestens = rechnung.mindestKante();
        if (gewuenschteKante < mindestens) {
            throw new IllegalArgumentException("Die Kante muss mindestens " + mindestens
                    + " sein, damit zwei " + feldwort + " hineinpassen, nicht "
                    + gewuenschteKante);
        }
        if (gewuenschteKante > Grenzregel.HOECHSTMASS) {
            throw new IllegalArgumentException("Eine Weltgrenze nimmt hoechstens "
                    + (long) Grenzregel.HOECHSTMASS + " an, nicht " + gewuenschteKante);
        }
        return new Feldfenster(rechnung, rechnung.felderFuer(gewuenschteKante));
    }

    /**
     * Welche Kante wirklich gilt - die eine Stelle, an der ueber das Verkleinern
     * entschieden wird.
     *
     * <p>Eine Weltgrenze wird nie nach innen geschoben. Ein bezahltes Feld darf nicht vor
     * die Tuer geraten, nur weil jemand eine Zahl in einer Datei kleiner macht. Deshalb
     * gilt der groessere Wert aus Einstellung und dem, was in der Welt steht.
     *
     * @param ausEinstellung   was in der Einstellungsdatei steht
     * @param inDerWelt        was die Welt heute als Kante fuehrt
     * @param rueckfall        was gilt, wenn die Zahl in der Datei unbrauchbar ist
     * @param sprungBestaetigt ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static int wirksameKante(Feldrechnung rechnung, int ausEinstellung,
                                    double inDerWelt, int rueckfall,
                                    boolean sprungBestaetigt) {
        return (int) Grenzregel.wirksam(ausEinstellung, inDerWelt, rechnung.mindestKante(),
                rueckfall, sprungBestaetigt);
    }

    /** Kleinste Feldnummer auf beiden Achsen. */
    public int von() {
        return -felderJeAchse / 2;
    }

    /** Groesste Feldnummer auf beiden Achsen. */
    public int bis() {
        return felderJeAchse / 2 - 1;
    }

    /** Wie viele Felder die Welt insgesamt hat. */
    public long felderZahl() {
        return (long) felderJeAchse * felderJeAchse;
    }

    /**
     * Wie viele Felder zu vergeben sind - alle ausser dem Ankunftsfeld.
     *
     * <p>Gefragt wird dabei, ob das Ankunftsfeld ueberhaupt innerhalb der Grenze liegt -
     * sonst zoege eine sehr kleine Welt ein Feld ab, das sie gar nicht hat.
     *
     * @param ankunftsfeld die Feldnummer des Ankunftsfeldes auf beiden Achsen
     */
    public long felderOhneAnkunft(int ankunftsfeld) {
        return felderZahl() - (enthaeltFeld(ankunftsfeld, ankunftsfeld) ? 1 : 0);
    }

    /**
     * Das naechste freie Feld, von der Mitte aus ringweise nach aussen gesucht.
     *
     * <p><b>Von innen nach aussen</b>, und das ist eine Entscheidung ueber die ganze Welt:
     * Die Felder liegen nah beieinander, Wege bleiben kurz, und die Welt waechst wie ein
     * Dorf. Bei Zufall laegen die ersten zwanzig Felder ueber tausende Bloecke verstreut -
     * Platten voller erzeugter Chunks dazwischen, denn erzeugt wird nur, wo jemand ist.
     *
     * <p><b>In Java gesucht und nicht mit wiederholten Schreibversuchen</b>: Bei
     * dreihundert vergebenen Feldern waeren das dreihundert fehlgeschlagene INSERTs, jeder
     * mit seinem eigenen Rundlauf zur Datenbank. Der eindeutige Schluessel der Ablage
     * bleibt trotzdem der Riegel - diese Suche ist die Abkuerzung, nicht die Sicherung.
     *
     * <p><b>Hier und nicht in einem Buch</b> [18.11.2026]: Die Suche stand im
     * Freistadtbuch, und das Wolkenbuch braucht sie Zeile fuer Zeile genauso - in einer
     * Welt aus Leere kann niemand auf dem Feld STEHEN, das er haben will. Zwei
     * Abschriften waeren zwei Orte, an denen die Reihenfolge der Ringe auseinanderlaeuft,
     * und dann vergaebe die eine Welt ihre Felder anders als die andere.
     *
     * @param belegte      die Felder, die schon vergeben sind, je als {@code [x, z]}; was
     *                     ausserhalb des Fensters liegt, wird uebergangen
     * @param ankunftsfeld die Feldnummer des Ankunftsfeldes auf beiden Achsen - es gehoert
     *                     niemandem und wird nie vergeben
     * @return das Feld als {@code [x, z]}, oder leer, wenn die Welt voll ist
     */
    public Optional<int[]> freiesFeld(List<int[]> belegte, int ankunftsfeld) {
        boolean[][] belegt = new boolean[felderJeAchse][felderJeAchse];
        for (int[] feld : belegte) {
            if (enthaeltFeld(feld[0], feld[1])) {
                belegt[feld[0] - von()][feld[1] - von()] = true;
            }
        }
        int weiteste = Math.max(bis(), -von());
        for (int ring = 0; ring <= weiteste; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    // Nur der RAND des Rings - was weiter innen liegt, war schon dran.
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
                        continue;
                    }
                    if (!enthaeltFeld(x, z) || (x == ankunftsfeld && z == ankunftsfeld)) {
                        continue;
                    }
                    if (!belegt[x - von()][z - von()]) {
                        return Optional.of(new int[] {x, z});
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Liegt dieses Feld in der Welt? */
    public boolean enthaeltFeld(int feldX, int feldZ) {
        return feldX >= von() && feldX <= bis() && feldZ >= von() && feldZ <= bis();
    }

    /**
     * Liegt das Feld an dieser Weltkoordinate in der Welt?
     *
     * <p>Antwortet ueber die FELDNUMMER und nicht ueber den Block: Ein Feld, das zur
     * Haelfte draussen laege, ist damit von vornherein unmoeglich. Achtung - der
     * Zwischenraum am oestlichen Rand liegt innerhalb der Grenze, gehoert aber zum Feld
     * davor. Wer eine Meldung fuer den Spieler baut, fragt deshalb erst den Plan seiner
     * Welt und erst danach hier.
     */
    public boolean enthaelt(int weltX, int weltZ) {
        return enthaeltFeld(rechnung.feldNummer(weltX), rechnung.feldNummer(weltZ));
    }

    /** Die Kantenlaenge der Weltgrenze - ein Durchmesser, kein Radius. */
    public double kante() {
        return rechnung.weltkante(felderJeAchse);
    }

    /** Die Mitte der Weltgrenze auf beiden Achsen - immer die halbe Zwischenraumbreite. */
    public double mitte() {
        return rechnung.weltmitte();
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Rings. */
    public int kleinsterBlock() {
        return rechnung.kleinsterBlock(von());
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Rings. */
    public int groessterBlock() {
        return rechnung.groessterBlock(bis());
    }
}
