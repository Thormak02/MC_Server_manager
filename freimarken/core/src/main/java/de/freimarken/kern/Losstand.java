package de.freimarken.kern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Wie viele Lose ein Spieler an einem Tag gezogen hat.
 *
 * <p><b>Hier liegt Geld</b>, und das ist der Unterschied zu {@link Bonusstand}. Dort
 * steht ausdruecklich kein Geld, weil das Buchungsbuch entscheidet, ob der Bonus heute
 * schon fiel - diese Zahl hier KANN das Buchungsbuch nicht ersetzen: Das erste Los ist
 * gratis, seine Buchung ist 0 Mark, und {@link Kasse#summeSeit} bleibt danach 0. "Noch
 * keines gezogen" und "das gratis Los ist weg" sind aus dem Buch also nicht zu
 * unterscheiden, und auch mit Betraegen waere es nicht besser: Zwei Lose zu je 500 Mark
 * sehen aus wie eines zu 1000.
 *
 * <p>Daraus folgt die Reihenfolge, in der {@link Lotteriebuch} arbeitet: ZUERST zaehlen,
 * dann zahlen, dann liefern. Geht diese Zeile verloren, zieht der Spieler ein zweites
 * gratis Los - das ist eine Geldquelle. Geht die Lieferung verloren, fehlt ihm ein
 * Gewinn - das ist ein Verlust. Von zwei Fehlern nimmt man den, der nichts erzeugt.
 *
 * <p>Gespeichert wird ein DATUM und kein Zeitpunkt, wie bei {@link Bonusstand}: Der Tag
 * kommt aus {@link Tagesgrenze#tagVon} und damit aus der eingestellten Zone.
 *
 * @param spieler   wer gezogen hat
 * @param tag       an welchem Tag - nach der eingestellten Zone, nicht nach UTC
 * @param loseHeute wie viele Lose an diesem Tag, nie negativ
 */
public record Losstand(UUID spieler, LocalDate tag, int loseHeute) {

    public Losstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Lose");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Lose - welcher Tag"
                    + " gilt, sagt Tagesgrenze.tagVon und nicht die Ablage");
        }
        if (loseHeute < 0) {
            throw new IllegalArgumentException("Eine Zahl gezogener Lose kann nicht"
                    + " negativ sein: " + loseHeute);
        }
    }

    /** Ein Spieler, der heute noch nicht gezogen hat. */
    public static Losstand leer(UUID spieler, LocalDate tag) {
        return new Losstand(spieler, tag, 0);
    }

    /**
     * Derselbe Stand mit einem Los mehr.
     *
     * <p>Laeuft bei {@link Integer#MAX_VALUE} an und nicht darueber: Eine
     * uebergelaufene Zahl waere NEGATIV, und der Konstruktor wuerfe dann mitten im
     * Spiel. Erreichbar ist das nur ohne Tagesgrenze in der Einstellungsdatei, aber
     * eine Zahl, die anlaeuft, ist in jedem Fall besser als ein Wurf.
     */
    public Losstand mitEinemMehr() {
        return new Losstand(spieler, tag,
                loseHeute == Integer.MAX_VALUE ? loseHeute : loseHeute + 1);
    }
}
