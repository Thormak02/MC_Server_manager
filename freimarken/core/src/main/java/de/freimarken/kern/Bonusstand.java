package de.freimarken.kern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Serie eines Spielers - alles, was der Tagesbonus sich merken muss.
 *
 * <p><b>Kein Geld darin</b>, und das ist die tragende Entscheidung dieses Teils: Ob der
 * Bonus heute schon gezahlt wurde, steht NICHT hier, sondern im Buchungsbuch. Dort ist die
 * Wahrheit ueber Geld, und nur dort kann keine Reihenfolge von Abstuerzen eine zweite
 * Zahlung am selben Tag erzeugen. Was hier liegt, ist eine Zaehlung von Tagen - wenn sie
 * einmal verlorengeht, ist eine Serie zu kurz und kein Geld entstanden.
 *
 * <p>Gespeichert wird ein DATUM und kein Zeitpunkt (Plan 8.8). Der Tag kommt aus
 * {@link Tagesgrenze#tagVon} und damit aus der eingestellten Zone; ein Zeitpunkt haette
 * eine zweite Zone und waere beim Vergleich zweier Tage genau die Falle, gegen die die
 * Tagesgrenze gebaut ist.
 *
 * @param spieler       wessen Serie
 * @param letzterTag    der letzte Tag mit einem Bonus, oder {@code null} fuer einen
 *                      Spieler, der noch keinen hatte
 * @param serie         wie viele Tage die laufende Serie hat; 0 nur zusammen mit
 *                      {@code letzterTag == null}
 * @param laengsteSerie die laengste Serie, die dieser Spieler je hatte - sie bleibt
 *                      stehen, wenn die laufende reisst. Sie steht hier und nicht in einer
 *                      Rechnung ueber die Vergangenheit, weil die Vergangenheit nicht
 *                      gespeichert ist: Aus einem Datum und einer Zahl ist nicht mehr
 *                      herauszulesen, wie lang eine frueher gerissene Serie war
 */
public record Bonusstand(UUID spieler, LocalDate letzterTag, int serie,
                         int laengsteSerie) {

    public Bonusstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Serie");
        }
        if (serie < 0 || laengsteSerie < 0) {
            throw new IllegalArgumentException("Eine Serie kann nicht negativ sein: "
                    + serie + " / " + laengsteSerie);
        }
        if (serie > laengsteSerie) {
            throw new IllegalArgumentException("Die laufende Serie " + serie
                    + " ist laenger als die laengste " + laengsteSerie
                    + " - dann ist die laengste nicht nachgefuehrt worden, und eine"
                    + " Anzeige nennte zwei Zahlen, von denen die groessere die kleinere"
                    + " ist.");
        }
        if (letzterTag == null && serie != 0) {
            throw new IllegalArgumentException("Eine Serie von " + serie + " ohne letzten"
                    + " Tag gehoert zu nichts - ohne Tag ist sie nicht fortzusetzen und"
                    + " nicht zu brechen.");
        }
        if (letzterTag != null && serie == 0) {
            throw new IllegalArgumentException("Ein letzter Tag mit einer Serie von 0 ist"
                    + " ein halber Stand: Am " + letzterTag + " wurde gezahlt, also war"
                    + " die Serie mindestens 1.");
        }
    }

    /** Ein Spieler, der noch keinen Bonus hatte - jeder faengt bei nichts an. */
    public static Bonusstand leer(UUID spieler) {
        return new Bonusstand(spieler, null, 0, 0);
    }

    /**
     * Derselbe Stand, nach einem Bonus an diesem Tag mit dieser Serie.
     *
     * <p>Fuehrt die laengste Serie MIT - das ist die eine Stelle, an der sie wachsen kann.
     * Zwei Stellen, die dieselbe Hoechstmarke fortschreiben, laufen auseinander, und die
     * zweite merkt es nie: Eine zu kleine Hoechstmarke sieht aus wie ein Spieler, der
     * frueher weniger gespielt hat.
     */
    public Bonusstand nachZahlung(LocalDate tag, int neueSerie) {
        if (tag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Zahlung");
        }
        if (neueSerie < 1) {
            throw new IllegalArgumentException("Eine Zahlung ist mindestens der erste"
                    + " Serientag, nicht " + neueSerie);
        }
        return new Bonusstand(spieler, tag, neueSerie,
                Math.max(laengsteSerie, neueSerie));
    }

    /** Ob dieser Spieler schon einmal einen Bonus hatte. */
    public boolean schonDagewesen() {
        return letzterTag != null;
    }
}
