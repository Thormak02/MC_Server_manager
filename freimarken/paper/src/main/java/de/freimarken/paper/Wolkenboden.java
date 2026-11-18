package de.freimarken.paper;

import de.freimarken.kern.Wolkenplan;
import org.bukkit.Material;

/**
 * Welcher Block auf welcher Hoehe von Wolkenwerder liegt.
 *
 * <p>Das Geschwister von {@link Inselboden}, {@link Feldboden} und {@link Stadtboden}, und
 * aus demselben Grund eine eigene Stelle: Hier gibt es von Anfang an ZWEI Schreiber - der
 * Weltgenerator backt das Ankunftsfeld, und das Plugin setzt jede Startinsel beim Nehmen
 * und beim Zuruecksetzen. Zwei Tabellen waeren das Fehlerbild, das niemand sucht: eine
 * zurueckgesetzte Insel, die anders aussieht als eine neue.
 *
 * <p><b>Die Hoehe 64 ist die einzige Zahl dieser Welt ohne Zwang.</b> In Hafenwerder
 * folgt der Boden aus der Wasserlinie, in der Marktstadt aus dem Gelaende; hier gibt es
 * weder Wasser noch Gelaende. Gewaehlt ist darum die Mitte: 384 Bloecke hat die Welt in
 * der Hoehe, und von 64 aus sind es 128 nach unten und 256 nach oben - Platz fuer einen
 * Turm und Platz fuer das, was darunter haengt. Wer die Zahl aendert, verschiebt jede
 * Insel; schon gesetzte bleiben, wo sie sind.
 *
 * <p>Die ganze Startinsel, von oben gelesen:
 *
 * <pre>
 *   71      LAUB          die Spitze der Krone
 *   65..70  STAMM + LAUB  der Baum
 *   65      TRUHE         die Starttruhe, auf der Wiese
 *   64      WIESE         9x9 Gras - darauf wird gelaufen
 *   62..63  ERDE          zwei Schichten, und sie wachsen nicht nach
 *   sonst   LUFT          die ganze uebrige Welt
 * </pre>
 */
final class Wolkenboden {

    /** Die Hoehe der Wiese - siehe Klassenkopf. */
    static final int BODEN = 64;

    private Wolkenboden() {
    }

    /** Die unterste Hoehe, die eine Insel beruehrt. */
    static int untersteHoehe(Wolkenplan plan) {
        return BODEN - plan.tiefeUnterWiese();
    }

    /** Die oberste Hoehe, die eine Insel beruehrt - die Spitze des Baumes. */
    static int obersteHoehe(Wolkenplan plan) {
        return BODEN + plan.hoeheUeberWiese();
    }

    /**
     * Welcher Block aus einem Stueck wird.
     *
     * <p>Zwei Tabellen in einer, denn die beiden Inseln sind dieselbe Gestalt aus
     * verschiedenem Stoff: Die Startinsel ist Gras und Erde, das Ankunftsfeld Steinziegel
     * und Stein. Was es auf der jeweils anderen nicht gibt, ist LUFT - und nicht ein
     * Wurf: {@link Wolkenplan#stueckBei} fragt nie danach, und ein Riegel, den nichts
     * ausloest, ist gruen mit und ohne.
     */
    static Material block(Wolkenplan.Insel insel, Wolkenplan.Stueck stueck) {
        return switch (insel) {
            case START -> aufDerStartinsel(stueck);
            case ANKUNFT -> aufDemAnkunftsfeld(stueck);
        };
    }

    /**
     * Die Startinsel: Gras, Erde, eine Eiche und eine Truhe.
     *
     * <p>Eiche und kein anderes Holz, weil ihr Laub Setzlinge UND Aepfel gibt - der
     * Setzling ist der zweite Baum, der Apfel das erste Essen. Mit Birke oder Fichte
     * waere die Insel ohne Nahrung, und das Spiel begaenne mit Hunger.
     */
    private static Material aufDerStartinsel(Wolkenplan.Stueck stueck) {
        return switch (stueck) {
            case NICHTS, GELAENDER -> Material.AIR;
            case WIESE -> Material.GRASS_BLOCK;
            case ERDE -> Material.DIRT;
            case STAMM -> Material.OAK_LOG;
            case LAUB -> Material.OAK_LEAVES;
            case TRUHE -> Material.CHEST;
        };
    }

    /**
     * Das Ankunftsfeld: Steinziegel, Stein und ein Gelaender.
     *
     * <p>Stein und nicht Gras, und das ist eine Nachricht an den Ankommenden: Dies ist
     * ein Platz und keine Insel. Steinziegel sind dasselbe Material wie die Mauern der
     * Marktstadt ({@link Stadtboden}) - was in Freimarken gebaut und nicht gewachsen ist,
     * sieht gleich aus.
     */
    private static Material aufDemAnkunftsfeld(Wolkenplan.Stueck stueck) {
        return switch (stueck) {
            case NICHTS, STAMM, LAUB, TRUHE -> Material.AIR;
            case WIESE -> Material.STONE_BRICKS;
            case ERDE -> Material.STONE;
            case GELAENDER -> Material.STONE_BRICK_WALL;
        };
    }
}
