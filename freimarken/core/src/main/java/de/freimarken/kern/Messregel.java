package de.freimarken.kern;

import java.util.Set;

/**
 * Die EINE Stelle, die entscheidet, ob ein Taktschlag als TAETIG zaehlt.
 *
 * <p>Warum es diese Stelle gibt, steht in Plan 8.8: Die Untaetigkeitsgrenze aus Teil C ist
 * eine Hoeflichkeitsregel und "gegen Absicht keine Mauer" - ein Makro oder ein beschwertes
 * Tastenbrett erzeugt Lebenszeichen, und damit sind die vierzig Stunden bis Buerger im
 * Stehen erreichbar. Was dort gemessen wird, ist ANWESENHEIT. Diese Klasse misst daneben
 * die TAETIGKEIT, damit Schritt 12 eine Zahl hat, an der eine Rangschwelle haengen kann,
 * ohne im Stehen erreichbar zu sein.
 *
 * <p><b>Gemessen wird VIELFALT und nicht Menge.</b> Das ist die tragende Entscheidung.
 * Eine Menge - "wie viele Bloecke in der Stunde" - laesst sich von einem Makro beliebig
 * hochdrehen und trifft dabei genau die ehrlichen Spieler, die viel arbeiten. Die Vielfalt
 * laesst sich nicht hochdrehen: Ein Makro gibt immer DASSELBE Zeichen, weil es genau eine
 * Tastenfolge wiederholt. Damit ist das "Wiederholungsmuster" aus Plan 8.8 erfasst, ohne
 * Zeitabstaende zu messen - und ohne eine Zahl, die der naechste Bauschritt "anpasst".
 *
 * <p><b>Diese Klasse greift NICHT ein.</b> Sie liefert eine zweite Zahl und sonst nichts:
 * kein Verdienst wird gesperrt, keine Zeit verworfen, kein Spieler gemeldet. Der Grundsatz
 * dahinter steht zweimal im Plan - bei der falschen Zeitzone (8.8) und beim Vertipper in
 * der Preisstaffel (8.7): Erkennung statt Verhinderung. Eine Schranke traefe zwangslaeufig
 * auch den ehrlichen Spieler mit einer eintoenigen Arbeit, und was sie dann wegnimmt, holt
 * ihm niemand zurueck.
 *
 * <p><b>Untermessen ist die sichere Richtung.</b> Wer einen Takt lang nur ueber das Meer
 * segelt, gibt ein einziges Lebenszeichen und zaehlt nicht als taetig, obwohl er spielt.
 * Das ist hingenommen und nicht uebersehen: Dieselbe Abwaegung wie beim Schreiben der
 * Spielzeit, wo sie im Plan ausgeschrieben steht - "zu viel Spielzeit gibt einen Rang zu
 * frueh, zu wenig nur zu spaet". Wem das zu streng ist, setzt
 * {@link Messwerte#PFAD_VERLANGTE_ARTEN} auf 1; dann genuegt EIN Zeichen, und die Messung
 * sagt nur noch "hat ueberhaupt etwas getan".
 */
public final class Messregel {

    private Messregel() {
    }

    /**
     * Ob dieser Taktschlag als taetig zaehlt.
     *
     * <p>Gezaehlt werden nur Arten, die in {@link Messwerte#arten()} stehen - eine Art, die
     * der Betreiber herausgenommen hat, ist fuer diese Rechnung nicht vorhanden. Darum
     * wird hier gefiltert und nicht beim Sammeln: Ein Neuladen soll die Liste erreichen,
     * ohne dass die schon gesammelten Zeichen des laufenden Taktes neu bewertet werden
     * muessten.
     *
     * @param gegeben welche Lebenszeichen der Spieler in diesem Takt gegeben hat; darf leer
     *                sein, {@code null} gilt als leer
     * @param werte   die Zahlen aus der {@code config.yml}
     * @return {@code true}, wenn mindestens {@link Messwerte#verlangteArten()}
     *         VERSCHIEDENE zaehlende Arten dabei sind
     */
    public static boolean taetig(Set<Lebenszeichen> gegeben, Messwerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es kein Urteil");
        }
        return zaehlende(gegeben, werte) >= werte.verlangteArten();
    }

    /**
     * Wie viele zaehlende Arten dabei sind - die Zahl hinter {@link #taetig}.
     *
     * <p>Eigene Methode, weil der Bericht an den Betreiber sie BRAUCHT: "zwei von zwei"
     * sagt ihm, wie knapp es war, und "eins von zwei" sagt ihm, welche Art fehlt. Ein
     * blosses ja/nein waere eine Zusage, die er nicht nachlesen kann.
     */
    public static int zaehlende(Set<Lebenszeichen> gegeben, Messwerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es nichts zu zaehlen");
        }
        if (gegeben == null || gegeben.isEmpty()) {
            return 0;
        }
        int zahl = 0;
        for (Lebenszeichen art : gegeben) {
            if (art != null && werte.arten().contains(art)) {
                zahl++;
            }
        }
        return zahl;
    }

    /**
     * Ob ein Spieler weit genug gekommen ist, damit {@link Lebenszeichen#BEWEGEN} zaehlt.
     *
     * <p>Die Entscheidung liegt HIER und nicht bei der Wache, obwohl die Wache die
     * Koordinaten hat: Es ist eine Regel und keine Messung, und sie ist ohne Server
     * pruefbar. Dieselbe Trennung wie bei {@link Spielzeitregel} und der
     * {@code Spielzeitwache}.
     *
     * <p><b>Ein Weltwechsel zaehlt immer.</b> Ein Abstand zwischen zwei Welten hat keine
     * Bedeutung - die Koordinaten des Nethers sind nicht die von Altfurt. Wer die Welt
     * wechselt, ist offensichtlich nicht an eine Wand gelehnt, und eine Rechnung, die zwei
     * Welten verrechnet, gaebe bei gleichen Koordinaten in beiden Welten eine 0 heraus und
     * waere damit falsch in der unsicheren Richtung.
     *
     * @param andereWelt ob Anfang und Ende des Taktes in verschiedenen Welten liegen
     * @param bloecke    der Abstand zwischen Anfang und Ende in Bloecken; bei einem
     *                   Weltwechsel bedeutungslos und darf 0 sein
     */
    public static boolean kamWeitGenug(boolean andereWelt, double bloecke, Messwerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Strecke");
        }
        if (andereWelt) {
            return true;
        }
        return bloecke >= werte.mindestStrecke();
    }

    /**
     * Ob diese Zahl verlangter Arten zu dieser Zahl vorhandener Arten passt.
     *
     * <p>Zwei Werte, die JEDER FUER SICH erlaubt sind und zusammen nicht: Wer drei
     * verschiedene Arten verlangt und nur zwei zaehlen laesst, hat eine Messstelle gebaut,
     * die niemals jemanden als taetig fuehrt - und zwar stumm, denn eine Null unterscheidet
     * sich nicht von "hier spielt niemand". Dieselbe Bauart wie
     * {@link Zeitwerte#brauchbareGrenzeZumTakt}: Die Regel steht einmal im Kern, und sowohl
     * der Wertesatz als auch der Leser der Datei fragen sie.
     *
     * @param verlangt  wie viele verschiedene Arten ein taetiger Takt braucht
     * @param vorhanden wie viele Arten ueberhaupt zaehlen
     */
    public static boolean brauchbareArtenzahl(int verlangt, int vorhanden) {
        return verlangt >= 1 && verlangt <= vorhanden;
    }
}
