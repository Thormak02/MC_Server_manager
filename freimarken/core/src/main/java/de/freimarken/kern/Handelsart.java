package de.freimarken.kern;

/**
 * Welche der zwei Taten an einem Kistenshop - immer aus Sicht des KUNDEN.
 *
 * <p><b>Die Sicht steht im Namen, und das ist eine Lehre.</b> Am 08.11.2026 waren die
 * beiden Preisrichtungen in {@code Shoptext} vertauscht: {@code verkauftWare()} heisst
 * "der Shop verkauft", also kauft der Kunde - und der Satz, der daran hing, sagte
 * "Kunden kaufen". Gefunden hat es eine Probe und nicht die Bildtafel. Ein Wert
 * {@code KAUF} haette dieselbe Falle wieder aufgestellt, denn jeder Handel ist
 * gleichzeitig ein Kauf und ein Verkauf - nur nicht fuer denselben Menschen.
 *
 * <p>Der Kunde ist die Sicht, weil der Kunde klickt: Rechtsklick ist
 * {@link #KUNDE_KAUFT}, Linksklick {@link #KUNDE_VERKAUFT}. So steht es bei Lumia, und
 * so steht es im Kopf von {@link Kistenshop}.
 *
 * <p><b>Wozu der Typ ueberhaupt gebraucht wird:</b> Die Marktgebuehr ist je Lage
 * einstellbar [entschieden 08.11.2026], und die Lage ist Shopart mal Handelsart. Ohne
 * diesen Typ waere die Richtung ein {@code boolean} an einer Stelle und ein Vergleich
 * von Preisen an der anderen.
 */
public enum Handelsart {

    /** Der Kunde KAUFT - er zahlt, der Shop gibt Ware. Rechtsklick. */
    KUNDE_KAUFT,

    /** Der Kunde VERKAUFT - er gibt Ware, der Shop zahlt. Linksklick. */
    KUNDE_VERKAUFT;

    /**
     * Ob dieser Shop in dieser Richtung ueberhaupt handelt.
     *
     * <p>Die EINE Stelle, an der die Handelsart auf die zwei Preise des Shops trifft.
     * Ohne sie stuende an jeder Stelle, die handelt, ein eigenes
     * {@code art == KUNDE_KAUFT ? shop.verkauftWare() : shop.kauftWare()} - und eines
     * davon waere irgendwann verdreht. Genau so ist es am 08.11.2026 schon einmal
     * passiert.
     */
    public boolean moeglichAn(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Ohne Shop gibt es keinen Handel");
        }
        // Der Kunde kauft, WENN der Shop verkauft. Das ist die Verdrehung, die hier an
        // einer Stelle steht und darum nur einmal falsch sein kann.
        return this == KUNDE_KAUFT ? shop.verkauftWare() : shop.kauftWare();
    }

    /**
     * Was eine Stufe dieser Tat kostet - der Preis, um den es geht.
     *
     * <p>Beim Kauf der Kaufpreis, beim Verkauf der Verkaufpreis. Steht hier und nicht
     * beim Aufrufer, aus demselben Grund wie {@link #moeglichAn}.
     */
    public long stufenpreis(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Ohne Shop gibt es keinen Preis");
        }
        return this == KUNDE_KAUFT ? shop.kaufpreis() : shop.verkaufpreis();
    }
}
