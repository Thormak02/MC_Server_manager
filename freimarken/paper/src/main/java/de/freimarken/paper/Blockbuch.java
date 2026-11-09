package de.freimarken.paper;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockzeile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sammelt Blockzeilen und schreibt sie gesammelt - nie im Ereignis.
 *
 * <p><b>Im Ereignis wird nur gesammelt.</b> Keine Netzrunde, kein Datenbankschreibzug,
 * kein zweiter Faden - dieselbe Regel, die sich dieses Projekt bei {@link Verdienstbuch}
 * gegeben hat, und beim Blockbuch ist sie noch zwingender: Zwanzig Spieler im Tagebau sind
 * ohne Weiteres mehrere hundert Bloecke je Sekunde, und jeder einzelne waere ein
 * {@code INSERT} mit zwei Netzrunden auf der EINEN Verbindung, die die Datenschicht haelt
 * - im Hauptfaden des Spielservers. Der Server stockte sichtbar, und zwar genau dann, wenn
 * viel passiert.
 *
 * <p><b>Ein Stapel und keine Warteschlange mit eigenem Faden.</b> Ein zweiter Faden
 * committete auf derselben Verbindung die halbe Transaktion des Hauptfadens mit -
 * dieselbe Begruendung, die {@link Spielzeitwache} ausbuchstabiert.
 *
 * <p><b>Was nicht geschrieben werden kann, wird verworfen - laut.</b> Das ist die
 * wichtigste Entscheidung dieser Klasse. Faellt die Datenbank aus, waechst der Stapel
 * sonst unbegrenzt, bis dem Server der Arbeitsspeicher ausgeht: aus einem Netzfehler
 * wuerde ein Serverabsturz. Die andere Richtung - Bloecke verweigern, solange nicht
 * geschrieben werden kann - waere ein Spielserver, den ein Netzfehler anhaelt, und das
 * fuer ein Buch, das in diesem Augenblick niemand liest. Ein Protokoll ist kein Geld:
 * {@link Blockbuchablage} sagt darum ausdruecklich NICHT zu, dass jede Tat darin steht.
 */
final class Blockbuch {

    /**
     * Wie viele Zeilen der Stapel hoechstens haelt.
     *
     * <p>Gemessen begruendet: Ein Spieler baut mit Effizienz V etwa zwanzig Bloecke je
     * Sekunde ab, zwanzig Spieler also vierhundert. Der Takt schlaegt jede Sekunde, der
     * Stapel ist im Betrieb also dreistellig. Zwanzigtausend sind damit rund eine Minute
     * Vollbetrieb bei stehender Datenbank - lang genug, dass ein kurzer Netzaussetzer
     * nichts kostet, und kurz genug, dass kein Arbeitsspeicher volllaeuft.
     *
     * <p>Erreicht der Stapel die Grenze, wird er GANZ verworfen und nicht halb: Ein
     * halber Stapel hiesse, dass die Luecke im Buch nicht am Anfang des Ausfalls liegt,
     * sondern irgendwo mitten darin - und dann sieht eine Abfrage aus wie eine
     * vollstaendige Antwort, obwohl sie es nicht ist.
     */
    static final int HOECHSTER_STAPEL = 20_000;

    private final Blockbuchablage ablage;
    private final Logger protokoll;

    private final List<Blockzeile> stapel = new ArrayList<>();

    /**
     * Wie viele Zeilen seit dem letzten gelungenen Schreibzug verworfen wurden.
     *
     * <p>Gezaehlt, damit die Meldung die Zahl nennen kann - und nur EINE Meldung je
     * Ausfall: Eine Zeile je verworfenem Stapel fuellte bei stehender Datenbank das
     * Serverprotokoll im Minutentakt, und dann gehen die uebrigen Meldungen darin unter.
     */
    private long verworfen;

    Blockbuch(Blockbuchablage ablage, Logger protokoll) {
        if (ablage == null || protokoll == null) {
            throw new IllegalArgumentException("Ablage und Protokoll sind Pflicht");
        }
        this.ablage = ablage;
        this.protokoll = protokoll;
    }

    /**
     * Legt eine Zeile auf den Stapel.
     *
     * <p><b>Es wird NICHT mehr gefragt, ob diese Tat aufzuschreiben ist</b> - das hat die
     * {@link Blockwache} schon entschieden, bevor sie die Zeile gebaut hat. Zwei Stellen,
     * die dieselbe Weissliste fragen, laufen auseinander, und hier waere der Preis der
     * hoehere: Diese Methode laeuft je Block.
     */
    void merke(Blockzeile zeile) {
        if (zeile == null) {
            throw new IllegalArgumentException("Eine Zeile, die nichts ist, gehoert nicht"
                    + " ins Blockbuch");
        }
        if (stapel.size() >= HOECHSTER_STAPEL) {
            verworfen += stapel.size();
            stapel.clear();
            protokoll.warning("Das Blockbuch konnte seit einer Weile nicht schreiben und"
                    + " hat " + verworfen + " Zeilen verworfen. Der Spielserver laeuft"
                    + " weiter - ein Protokoll ist kein Geld -, aber das Buch hat jetzt"
                    + " eine Luecke. Der Grund steht in der Meldung davor; meist ist es"
                    + " die Datenbank.");
        }
        stapel.add(zeile);
    }

    /**
     * Schreibt den Stapel und leert ihn - gerufen vom Takt.
     *
     * <p>Faengt seinen eigenen Fehler: Der Takt laeuft im Hauptfaden, und ein Wurf von
     * hier landete als Stapelspur im Serverprotokoll, je Sekunde erneut, ohne dass ein
     * Mensch den Grund sieht. Geleert wird in JEDEM Fall - sonst waere der naechste
     * Schlag derselbe Fehlschlag mit einer Zeile mehr.
     *
     * @return wie viele Zeilen geschrieben wurden; 0 ist der Normalfall auf einem ruhigen
     *         Server
     */
    int gibAb() {
        if (stapel.isEmpty()) {
            return 0;
        }
        List<Blockzeile> abzugeben = List.copyOf(stapel);
        stapel.clear();
        try {
            int geschrieben = ablage.schreibe(abzugeben);
            if (verworfen > 0L) {
                protokoll.info("Das Blockbuch schreibt wieder. Waehrend des Ausfalls sind"
                        + " " + verworfen + " Zeilen verloren gegangen.");
                verworfen = 0L;
            }
            return geschrieben;
        } catch (RuntimeException fehler) {
            verworfen += abzugeben.size();
            // FEINE Meldung und keine schwere: Der Spielserver laeuft weiter, und nichts
            // ist falsch - es fehlt etwas. Eine schwere Meldung je Sekunde machte das
            // Protokoll unlesbar, und die Zusammenfassung kommt, sobald es wieder geht.
            protokoll.log(Level.FINE, "Blockbuch nicht schreibbar - " + abzugeben.size()
                    + " Zeilen verworfen", fehler);
            return 0;
        }
    }

    /** Wie viele Zeilen auf dem Stapel liegen - fuer die Proben und fuer das Abschalten. */
    int imStapel() {
        return stapel.size();
    }

    /** Wie viele Zeilen seit dem letzten gelungenen Schreibzug verloren sind. */
    long verworfen() {
        return verworfen;
    }
}
