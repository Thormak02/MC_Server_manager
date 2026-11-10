package de.freimarken.kern;

/**
 * Die Freie Truhe - wie viel ein Spieler an einem Tag herausnehmen darf.
 *
 * <p>Eine offene Truhe in der Markthalle (Plan 7.2): jeder darf hineinlegen, jeder darf
 * herausnehmen. Kostet nichts, schafft Gemeinschaft, entsorgt Ueberschuss.
 *
 * <p><b>Der Deckel ist das Einzige, was sie von einer gewoehnlichen Kiste
 * unterscheidet</b>, und er steht im Plan mit seinem Grund: "damit sie nicht zum
 * Muellhaufen oder zum Selbstbedienungsladen wird". Zwei Zahlen tragen das - dieser
 * Deckel und das taegliche Leeren.
 *
 * <h2>Gemessen wird der UNTERSCHIED und nicht der Klick</h2>
 *
 * <p><b>Das ist die tragende Entscheidung, und sie kommt aus einer Lehre dieses
 * Projekts.</b> Beim Kistenshop steht sie im Klartext: "Das Nehmen hat zehn Wege
 * (Links-, Rechts-, Umschaltklick, Ziehen, Zahlentasten, zweite Hand, Doppelklick,
 * Wurf), und jede vergessene Art ist ein Loch." Dort wurde das Oeffnen darum GANZ
 * verweigert. Hier kann es nicht verweigert werden - Hineinlegen und Herausnehmen sind
 * der Zweck.
 *
 * <p>Also wird nicht gezaehlt, WAS geklickt wurde, sondern was am Ende FEHLT: Beim
 * Oeffnen wird der Inhalt gezaehlt, beim Schliessen wieder, und die Differenz ist die
 * Entnahme. Das ist gegen alle zehn Wege dicht, weil es keinen davon ansieht.
 *
 * <p>Zwei Dinge muessen dafuer gelten, und sie stehen bei der Wache: Es darf nur EINER
 * zugleich hineinsehen (sonst zaehlt der eine, was der andere genommen hat), und kein
 * TRICHTER darf daran (sonst zaehlt die Differenz eine Maschine mit).
 *
 * <p><b>Das Erste gilt nur, WENN gezaehlt wird</b> ({@link Truhenwerte#zaehlt()}). Ohne
 * Deckel und ohne Verbotsliste gibt es nichts zu zaehlen, und dann duerfen alle gemeinsam
 * hineinsehen [entschieden 10.11.2026]: Eine Bedingung, deren Rechnung nicht laeuft, ist
 * keine Zusage mehr, sondern eine zugehaltene Gemeinschaftskiste. Das Zweite gilt immer -
 * es haengt nicht an der Zaehlung, sondern daran, dass die Truhe Spielern gehoert.
 *
 * <h2>Was er BEHAELT, wird angerechnet</h2>
 *
 * <p>Wer mehr nimmt, als er darf, bekommt den Ueberschuss beim Schliessen
 * zurueckgelegt - die Plaetze sind frei, er hat sie gerade geleert. Angerechnet wird
 * darum nicht, was er genommen hat, sondern was nach dem Zuruecklegen bei ihm BLEIBT.
 *
 * <p><b>Der Grund ist ein Loch, das sonst offen stuende:</b> Wer die genommene Ware
 * fallen laesst, bevor er die Truhe schliesst, kann nichts zurueckgelegt bekommen - und
 * haette den Deckel umgangen. Weil angerechnet wird, was er behaelt, zaehlt genau dieser
 * Fall VOLL gegen seinen Tag. Er kommt damit einmal ueber den Deckel und ist fuer heute
 * fertig.
 */
public final class Truhenregel {

    private Truhenregel() {
    }

    /**
     * Was beim Schliessen zu tun ist.
     *
     * @param erlaubt wie viele Stueck er behalten darf
     * @param zurueck wie viele Stueck zurueck in die Truhe gehoeren
     */
    public record Abrechnung(int erlaubt, int zurueck) {

        public Abrechnung {
            if (erlaubt < 0 || zurueck < 0) {
                throw new IllegalArgumentException("Weder " + erlaubt + " erlaubte noch "
                        + zurueck + " zurueckzulegende Stueck koennen negativ sein");
            }
        }

        /** Ob ueberhaupt etwas zurueckzulegen ist - sonst wird die Truhe nicht angefasst. */
        public boolean legtZurueck() {
            return zurueck > 0;
        }
    }

    /**
     * Rechnet eine Entnahme ab.
     *
     * <p>Gibt eine Abrechnung und wirft nicht: Ein Spieler, der zu viel genommen hat, ist
     * der Normalfall beim ersten Mal und kein Fehler des Programms.
     *
     * @param deckel     wie viele Stueck am Tag erlaubt sind;
     *                   {@link Truhenwerte#KEIN_DECKEL} heisst: so viele er will
     * @param heuteSchon wie viele Stueck er heute schon behalten hat
     * @param genommen   wie viele Stueck jetzt fehlen - 0 oder weniger heisst, er hat
     *                   nichts genommen (oder etwas hineingelegt), und dann gibt es
     *                   nichts abzurechnen
     */
    public static Abrechnung abrechnen(int deckel, int heuteSchon, int genommen) {
        if (deckel < Truhenwerte.KEIN_DECKEL) {
            throw new IllegalArgumentException("Ein Deckel von " + deckel + " ist keiner"
                    + " - erlaubt ist " + Truhenwerte.KEIN_DECKEL + " (kein Deckel) oder"
                    + " mehr");
        }
        if (heuteSchon < 0) {
            throw new IllegalArgumentException("Er kann heute nicht " + heuteSchon
                    + " Stueck behalten haben");
        }
        if (genommen <= 0) {
            // Nichts genommen, oder etwas hineingelegt. Das Hineinlegen ist der halbe
            // Zweck dieser Truhe und wird nicht gezaehlt: Gedeckelt ist die ENTNAHME.
            return new Abrechnung(0, 0);
        }
        if (deckel == Truhenwerte.KEIN_DECKEL) {
            return new Abrechnung(genommen, 0);
        }
        int frei = Math.max(0, deckel - heuteSchon);
        int erlaubt = Math.min(genommen, frei);
        return new Abrechnung(erlaubt, genommen - erlaubt);
    }

    /**
     * Ob dieser Spieler heute ueberhaupt noch etwas nehmen darf.
     *
     * <p>Die Frage VOR dem Oeffnen, und sie ist mehr als Hoeflichkeit: Wer am Deckel ist,
     * soll die Truhe gar nicht aufbekommen. Sonst nimmt er, sieht es im Rucksack liegen,
     * schliesst - und bekommt alles wieder weggenommen. Das sieht aus wie ein Fehler des
     * Servers.
     *
     * <p>Hineinlegen bleibt damit auch versperrt, und das ist der Preis. Er ist tragbar:
     * Wer heute sechzehn Stueck herausgetragen hat, kann morgen schenken.
     */
    public static boolean darfNochNehmen(int deckel, int heuteSchon) {
        return abrechnen(deckel, heuteSchon, 1).erlaubt() > 0;
    }

    /**
     * Wie viele Stueck heute noch gehen - fuer die Auskunft an den Spieler.
     *
     * @return bei {@link Truhenwerte#KEIN_DECKEL} die Zahl {@code -1}: "unbegrenzt" ist
     *         keine Zahl, und eine riesige waere eine, die niemand liest. Der Satzbau
     *         fragt darum {@code deckel} selbst und waehlt zwei verschiedene Saetze -
     *         dieselbe Form wie beim Tagesdeckel des Bundesmarktes
     */
    public static int heuteNochFrei(int deckel, int heuteSchon) {
        if (deckel == Truhenwerte.KEIN_DECKEL) {
            return -1;
        }
        return abrechnen(deckel, heuteSchon, deckel).erlaubt();
    }
}
