package de.freimarken.kern;

/**
 * Welche Weltgrenze wirklich gilt - die eine Stelle, an der ueber das Verkleinern
 * entschieden wird.
 *
 * <p>Eine Weltgrenze wird nie nach innen geschoben; Plan 12a.6 fuehrt sie als nur lesbar.
 * Eine bezahlte Parzelle darf nicht vor die Tuer geraten, nur weil jemand eine Zahl in
 * einer Datei kleiner macht. Deshalb gilt der groessere Wert aus Einstellung und dem, was
 * in der Welt steht.
 *
 * <p>Nach oben gibt es die Gegenrichtung: Mehr als eine Verdopplung ist genauso
 * unumkehrbar wie eine Verkleinerung - die neu eingeschlossene Flaeche wird vorgeneriert
 * und belegt Platte - und braucht deshalb eine zweite Bestaetigung.
 *
 * <p>Die Regel stand bis zum 01.11.2026 in {@link Stadtgrenze} und damit am Rasterplan der
 * Marktstadt. Sie gilt aber fuer jede Welt; Altfurt, Kornmark und die Werder haben
 * dieselbe Frage und kein Raster. Jetzt steht sie einmal hier, und die Marktstadt legt nur
 * noch ihre Rundung auf ganze Rasterzellen darueber.
 */
public final class Grenzregel {

    /** Bukkits Hoechstmass fuer die Weltgrenze - und die Vorgabe einer frischen Welt. */
    public static final double HOECHSTMASS = 59_999_968.0;

    /**
     * Unter diesem Mass ist eine Weltgrenze keine mehr - zwei Chunks.
     *
     * <p>Nicht 0: Eine Grenze von 8 Bloecken waere ein eingesperrter Spieler, und das
     * sieht in der Einstellungsdatei aus wie ein Tippfehler, weil es einer ist. Die
     * Marktstadt hat ein eigenes, groesseres Mindestmass - eine Rasterzelle, sonst gaebe
     * es keine Parzelle; siehe {@link Stadtgrenze#mindestKante}.
     */
    public static final long MINDESTKANTE = 32;

    private Grenzregel() {
    }

    /**
     * Die Kante, die ab jetzt gilt.
     *
     * @param ausEinstellung    was in der Einstellungsdatei steht
     * @param inDerWelt         was die Welt heute als Kante fuehrt
     * @param mindestens        unterhalb dessen ist die Angabe keine Grenze mehr
     * @param rueckfall         was gilt, wenn die Einstellung unbrauchbar ist
     * @param sprungBestaetigt  ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static long wirksam(long ausEinstellung, double inDerWelt, long mindestens,
                               long rueckfall, boolean sprungBestaetigt) {
        long gewuenscht = brauchbar(ausEinstellung, mindestens) ? ausEinstellung : rueckfall;

        // Die Vorgabe einer frischen Welt heisst: Hier wurde noch nie eine Grenze gesetzt.
        // Exakt vergleichen und nicht gegen eine runde Million - sonst gaelte eine von Hand
        // auf zwei Millionen gesetzte Grenze als ungesetzt und wuerde verkleinert.
        if (inDerWelt >= HOECHSTMASS) {
            return gewuenscht;
        }
        long gesetzt = (long) Math.min(Math.max(inDerWelt, 0.0), HOECHSTMASS);

        // Unterhalb des Mindestmasses ist keine Grenze: Wer sie von Hand auf 10 stellt, hat
        // nichts eingeschlossen, was zu schuetzen waere.
        if (gesetzt < mindestens) {
            return gewuenscht;
        }
        if (Sprungregel.grosserSprungNachOben(gesetzt, gewuenscht) && !sprungBestaetigt) {
            return gesetzt;
        }
        return Math.max(gewuenscht, gesetzt);
    }

    /**
     * Der Text, mit dem ein Sprung ueber das Doppelte bestaetigt wird, etwa "2989 -> 8000".
     *
     * <p>Reicht nur durch: Die Entscheidung "mehr als Faktor 2" und die Form des Textes
     * stehen seit dem 02.11.2026 in {@link Sprungregel}, weil die Geldwerte in der
     * Einstellungsdatei dieselbe Rueckfrage brauchen. Zwei Abschriften derselben Regel
     * waeren zwei Orte, an denen sie sich auseinanderentwickeln kann - und der Betreiber
     * saehe erst daran, dass ein eingetragener Text an einer Stelle nicht mehr passt.
     */
    public static String sprungText(long ausEinstellung, double inDerWelt) {
        return Sprungregel.sprungText((long) inDerWelt, ausEinstellung);
    }

    /**
     * Was dem Betreiber zu sagen ist, wenn nicht gilt, was er eingetragen hat - oder
     * {@code null}, wenn seine Zahl genau so gilt.
     *
     * <p>In einer Hand, weil es drei verschiedene Lagen sind und sich genau darin
     * unterscheiden, was der Betreiber jetzt tun kann. Vorher stand ein Text fuer alle
     * drei da und haengte an jede denselben Rat: {@code sprung-bestaetigt}. Beim
     * Verkleinern fuehrt der ins Leere - eine Bestaetigung gibt den Sprung nach oben
     * frei und kann an einer Verkleinerung nichts aendern, weil die nie moeglich ist.
     * Wer die Meldung befolgte, trug den genannten Text ein, startete neu und sah
     * dieselbe Meldung wieder.
     *
     * @param pfad der Eintrag in der Einstellungsdatei OHNE {@code .kante}, etwa
     *             {@code welten.altfurt}
     */
    public static String klage(String pfad, long ausEinstellung, double inDerWelt,
                               long wirksam, long mindestens) {
        if (wirksam == ausEinstellung) {
            return null;
        }
        if (!brauchbar(ausEinstellung, mindestens)) {
            return pfad + ".kante ist " + ausEinstellung + " - damit ist keine Grenze zu"
                    + " machen (erlaubt sind " + mindestens + " bis " + (long) HOECHSTMASS
                    + "). Es gilt " + wirksam + ".";
        }
        if (wirksam > ausEinstellung) {
            return pfad + ".kante sagt " + ausEinstellung + ", in der Welt stehen "
                    + (long) inDerWelt + " - eine Weltgrenze wird nie nach innen"
                    + " geschoben, es bleiben " + wirksam + ". Eine Bestaetigung aendert"
                    + " daran nichts; wer wirklich verkleinern will, muss die Welt selbst"
                    + " anfassen und weiss dann auch, was dabei vor die Tuer geraet.";
        }
        return pfad + ".kante sagt " + ausEinstellung + ", in der Welt stehen "
                + (long) inDerWelt + " - mehr als eine Verdopplung ist genauso"
                + " unumkehrbar wie eine Verkleinerung und braucht deshalb eine zweite"
                + " Bestaetigung. Es gilt " + wirksam + ". Fuer den Sprung: " + pfad
                + ".sprung-bestaetigt: " + sprungText(ausEinstellung, inDerWelt);
    }

    /**
     * Ob aus dieser Zahl ueberhaupt eine Weltgrenze werden kann.
     *
     * <p>Oeffentlich, damit die Einstellungsdatei an derselben Regel geprueft wird, nach
     * der hier gerechnet wird. Sonst prueft das Lesen gegen eine Grenze und das Setzen
     * gegen eine andere - und Bukkit wirft beim Setzen mitten im Serverstart.
     */
    public static boolean brauchbar(long kante) {
        return brauchbar(kante, MINDESTKANTE);
    }

    /**
     * Dasselbe mit einem eigenen Mindestmass - fuer Welten, die mehr brauchen als zwei
     * Chunks.
     *
     * <p>Die Freistaedtewelt ist so eine: Unter zwei Stadtfeldern und einer Leere, also
     * 608 Bloecken, ergibt {@code Feldgrenze.ausKante} keine Welt mehr und wirft. Ohne
     * diese Fassung pruefte der Weltenbau gegen 32 und rechnete gegen 608 - und der
     * Server bliebe mitten im Start stehen.
     */
    public static boolean brauchbar(long kante, long mindestens) {
        return kante >= mindestens && kante <= HOECHSTMASS;
    }
}
