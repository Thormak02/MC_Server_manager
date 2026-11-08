package de.freimarken.kern;

/**
 * Was ein Handel am Kistenshop kostet und einbringt - die ganze Rechnung, an einer Stelle.
 *
 * <p><b>Hier liegen die drei Raender, an denen die Vorbild-Plugins Geld erzeugt haben</b>
 * (Plan 7.4). Jede Regel dieser Klasse ist die Antwort auf einen belegten Fehler:
 *
 * <ul>
 *   <li><b>Kein Teilhandel.</b> ChestShop PR #522 "Fix zero partial price exploit": Bei
 *       Teilhandel wurde der skalierte Preis auf zwei Stellen gerundet und konnte 0
 *       werden - kostenlos kaufen. Hier geht ein Handel GANZ oder gar nicht, und darum
 *       gibt es keinen skalierten Preis, der runden koennte.</li>
 *   <li><b>Ganze Mark, keine Bruchteile.</b> Der Preis steht als {@code long} in der
 *       Zeile und wird mit {@code Math.multiplyExact} vervielfacht. Was nicht teilbar
 *       ist, wird nicht geteilt.</li>
 *   <li><b>Die Gebuehr wird vom VERKAEUFER abgezogen und nie aufgeschlagen.</b> Der Preis
 *       auf dem Schild ist, was der Kunde zahlt. Ein Aufschlag an der Kasse waere die
 *       haeufigste Beschwerde - und bei einem Aufschlag muesste der Kunde eine Zahl
 *       aufbringen, die nirgends steht.</li>
 * </ul>
 *
 * <p><b>Die Gebuehr wird ABGERUNDET, und das ist die sichere Richtung.</b> Bei 5 % von 99
 * Mark sind es 4 und nicht 5: Der Staat bekommt weniger, der Verkaeufer mehr. Die andere
 * Rundung koennte bei kleinen Betraegen den ganzen Erloes verschlucken - 5 % von 1 Mark
 * aufgerundet ist 1, und der Verkaeufer bekaeme 0 fuer seine Ware. Das ist genau die
 * Form, in der ChestShops Rundung zum Nulltarif wurde, nur in der anderen Richtung.
 */
public final class Shopregel {

    /**
     * Die hoechste Gebuehr: 50 Prozent.
     *
     * <p>Keine 100: Eine Gebuehr, die den ganzen Erloes nimmt, ist keine Gebuehr, sondern
     * eine Enteignung - und ein Betreiber, der sie aus Versehen einstellt, merkt es erst,
     * wenn die ersten Spieler aufhoeren zu handeln. Die Haelfte ist schon schmerzhaft
     * hoch und laesst den Vertipper auffallen.
     */
    public static final int HOECHSTE_GEBUEHR_PROZENT = 50;

    private Shopregel() {
    }

    /**
     * Was ein Handel bewegt.
     *
     * @param preis   was der Kunde zahlt (beim Kauf) oder was der Shop ansetzt (beim
     *                Ankauf) - immer die volle Summe fuer die ganze Menge
     * @param gebuehr was davon an den Staat geht
     * @param erloes  was beim Verkaeufer ankommt: {@code preis - gebuehr}
     * @param stueck  wie viele Gegenstaende den Besitzer wechseln
     */
    public record Handel(long preis, long gebuehr, long erloes, int stueck) {

        public Handel {
            if (preis < 1L) {
                throw new IllegalArgumentException("Ein Handel ueber " + preis
                        + " Mark ist keiner - wo nichts gezahlt wird, wird nicht"
                        + " gehandelt");
            }
            // Eine Gebuehr ueber dem Preis liesse den Erloes negativ werden - der
            // Verkaeufer zahlte fuer seinen Verkauf. Erreichbar ist das bei einer
            // Gebuehr bis 50 Prozent nicht; die Schranke steht gegen den
            // Programmierfehler, nicht gegen die Einstellung.
            if (gebuehr < 0L || gebuehr > preis) {
                throw new IllegalArgumentException("Eine Gebuehr von " + gebuehr
                        + " auf einen Preis von " + preis + " ist keine - sie liesse"
                        + " den Verkaeufer fuer seinen Verkauf zahlen");
            }
            if (erloes != preis - gebuehr) {
                throw new IllegalArgumentException("Die Rechnung geht nicht auf: " + preis
                        + " minus " + gebuehr + " ist nicht " + erloes);
            }
            if (stueck < 1) {
                throw new IllegalArgumentException("Ein Handel ueber " + stueck
                        + " Stueck ist keiner");
            }
        }
    }

    /**
     * Rechnet einen Handel - die eine Stelle, an der Preis und Gebuehr entstehen.
     *
     * <p><b>Die Menge ist ein VIELFACHES der Shopmenge.</b> Ein Shop, der 16 Stueck fuer
     * 100 Mark anbietet, handelt 16, 32, 48 - nicht 17. Das ist nicht Strenge, sondern
     * die Vermeidung des Teilpreises: Sobald eine Menge zwischen zwei Stufen liegt, muss
     * ein Preis geteilt werden, und genau dort hat ChestShop sein kostenloses Kaufen
     * gehabt.
     *
     * @param shopmenge   wie viele Stueck eine Stufe umfasst
     * @param stufenpreis was eine Stufe kostet
     * @param stufen      wie viele Stufen gehandelt werden - 1 beim einfachen Klick,
     *                    mehr beim Sneak-Klick
     * @param gebuehrProzent wie viel Prozent an den Staat gehen, 0 bis
     *                    {@link #HOECHSTE_GEBUEHR_PROZENT}
     * @throws IllegalArgumentException wenn eine Zahl keine ist oder der Preis ueberlaeuft
     */
    public static Handel rechne(int shopmenge, long stufenpreis, int stufen,
            int gebuehrProzent) {
        if (shopmenge < 1 || shopmenge > Kistenshop.GROESSTE_MENGE) {
            throw new IllegalArgumentException("Eine Shopmenge von " + shopmenge
                    + " ist keine - erlaubt ist 1 bis " + Kistenshop.GROESSTE_MENGE);
        }
        if (stufenpreis < 1L || stufenpreis > Kistenshop.HOECHSTER_PREIS) {
            throw new IllegalArgumentException("Ein Stufenpreis von " + stufenpreis
                    + " ist keiner - erlaubt ist 1 bis " + Kistenshop.HOECHSTER_PREIS);
        }
        if (stufen < 1) {
            throw new IllegalArgumentException("Ein Handel ueber " + stufen
                    + " Stufen ist keiner");
        }
        if (!brauchbareGebuehr(gebuehrProzent)) {
            throw new IllegalArgumentException("Eine Gebuehr von " + gebuehrProzent
                    + " Prozent ist keine - erlaubt ist 0 bis "
                    + HOECHSTE_GEBUEHR_PROZENT);
        }
        // multiplyExact und nicht *, an BEIDEN Stellen - aber nur eine davon kann
        // wirklich ueberlaufen, und das ist beim Schreiben der Probe gemessen worden:
        //
        // Der PREIS kann nicht. Bei hoechstens 10 Millionen je Stufe und hoechstens 2,1
        // Milliarden Stufen sind es 2,1 mal 10^16, und ein long haelt 9,2 mal 10^18. Die
        // Grenzen oben schliessen den Fall aus; das multiplyExact steht hier als
        // Guertel, falls eine der zwei Grenzen eines Tages steigt.
        //
        // Die STUECKZAHL kann: 3.456 mal 2,1 Milliarden laeuft als int ueber, und ein
        // uebergelaufener int ist NEGATIV. Geworfen wird dann hier, wo der Fall
        // entsteht - nicht erst im Satz Handel, dessen Meldung vom Ueberlauf nichts
        // sagt.
        long preis = Math.multiplyExact(stufenpreis, (long) stufen);
        int stueck = Math.multiplyExact(shopmenge, stufen);
        // Abgerundet - siehe den Klassenkopf. Erst vervielfachen, dann teilen: Die andere
        // Reihenfolge rundete je Stufe und verloere bei 20 Stufen bis zu 20 Mark.
        long gebuehr = preis * gebuehrProzent / 100L;
        return new Handel(preis, gebuehr, preis - gebuehr, stueck);
    }

    /**
     * Wie viele STUFEN in diesen Handel passen - fuer den Sneak-Klick.
     *
     * <p>Lumia handelte beim Sneak-Klick "einen ganzen Stack". Was das genau ist, haengt
     * an drei Grenzen: wie viel der Kunde bezahlen kann, wie viel der Shop hat und wie
     * viel noch Platz findet. Diese Rechnung nimmt die KLEINSTE davon - und nie mehr als
     * ein Stapel, weil mehr in keinen Klick gehoert.
     *
     * <p>Gibt 0 zurueck, wenn nicht einmal eine Stufe geht. Der Aufrufer macht daraus
     * eine Absage, die sagt, WAS fehlt; diese Rechnung sagt nur, wie viel geht.
     *
     * @param stufenpreis was eine Stufe kostet
     * @param guthaben    was der Zahler hat
     * @param vorrat      wie viele STUECK der Gebende hat
     * @param platz       wie viele STUECK der Nehmende aufnehmen kann
     * @param shopmenge   wie viele Stueck eine Stufe umfasst
     */
    public static int passendeStufen(long stufenpreis, long guthaben, int vorrat,
            int platz, int shopmenge) {
        if (stufenpreis < 1L || shopmenge < 1) {
            throw new IllegalArgumentException("Ohne Stufenpreis und Shopmenge gibt es"
                    + " keine Stufen");
        }
        long nachGeld = guthaben / stufenpreis;
        int nachVorrat = Math.max(0, vorrat) / shopmenge;
        int nachPlatz = Math.max(0, platz) / shopmenge;
        long moeglich = Math.min(nachGeld, Math.min(nachVorrat, nachPlatz));
        // Nie mehr als ein Stapel je Klick. Ohne diese Grenze raeumte ein Sneak-Klick
        // eine volle Doppelkiste in einem Zug - und der Kunde haette 54 Stapel, von denen
        // 18 in seinem Inventar Platz finden und der Rest auf dem Boden laege.
        return (int) Math.min(moeglich, einStapelInStufen(shopmenge));
    }

    /** Ob diese Gebuehr eine ist - 0 bis {@link #HOECHSTE_GEBUEHR_PROZENT}. */
    public static boolean brauchbareGebuehr(int prozent) {
        return prozent >= 0 && prozent <= HOECHSTE_GEBUEHR_PROZENT;
    }

    /**
     * Wie viele Stufen ein Stapel von 64 Stueck umfasst - mindestens eine.
     *
     * <p>Bei einer Shopmenge ueber 64 ist eine Stufe schon mehr als ein Stapel. Dann ist
     * die Antwort 1 und nicht 0: Ein Sneak-Klick soll nie WENIGER handeln als ein
     * einfacher.
     */
    private static int einStapelInStufen(int shopmenge) {
        return Math.max(1, 64 / shopmenge);
    }
}
