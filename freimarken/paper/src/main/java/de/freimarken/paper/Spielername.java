package de.freimarken.paper;

import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Der Name hinter einer Kennung - die EINE Stelle, die ihn holt.
 *
 * <p>Angelegt am 08.11.2026, weil es dieselbe Regel schon ZWEIMAL gab:
 * {@code Schutzwache.namen} und {@code Ladenschutz.namen} standen Zeichen fuer Zeichen
 * gleich da, und mit dem Shopschutz waere eine dritte Kopie dazugekommen. Das ist der
 * Nutzergrundsatz "alle Wege zur selben Tat": erst alle Pfade suchen, dann die Regel an
 * EINER Stelle bauen.
 *
 * <p>Drei Aufrufer haetten sonst drei Ruecksicherungen gehabt, und eine Aenderung - etwa
 * acht Zeichen auf zwoelf - waere an zwei von drei Stellen hereingegangen. Der
 * Unterschied faellt bei einem Spieler auf, den der Server noch nie gesehen hat, also
 * selten und beim Falschen.
 *
 * <p><b>Eine eigene Datei und keine Methode in {@link Schutzwache}.</b> Der
 * Klasseninitialisierer der Wache baut {@code HEIKLE_GRUPPEN} aus {@code Tag.FLOWER_POTS}
 * und fragt damit {@code Bukkit.server}; ein Aufruf von dort riss jede Probe ohne Server
 * in einen {@code ExceptionInInitializerError}, bevor sie ein Zeichen vergleichen kann.
 * Derselbe Grund, aus dem es {@link Schutztext} gibt. Hier wird {@code Bukkit} erst IN
 * der Methode gefragt, also traegt die Klasse ohne Server.
 */
final class Spielername {

    /**
     * So viele Zeichen der Kennung treten an die Stelle eines fehlenden Namens.
     *
     * <p>Acht sind die erste Gruppe einer UUID. Sie genuegen, damit ein Betreiber im
     * Protokoll denselben Spieler wiedererkennt, und sind kurz genug fuer einen
     * Handbalken.
     */
    static final int ZEICHEN_DER_KENNUNG = 8;

    private Spielername() {
    }

    /**
     * Der Name zu dieser Kennung, oder acht Zeichen der Kennung selbst.
     *
     * <p>Ein Spieler, den der Server noch nie gesehen hat, hat keinen Namen - und "null"
     * auf einem Handbalken sagt einem Menschen nichts. Deshalb nie {@code null} und nie
     * leer: Was hier herauskommt, geht unmittelbar in einen Satz.
     *
     * @param wer die Kennung, oder {@code null} - dann der Text fuer "niemand", damit ein
     *            Bundshop oder ein freier Stand keinen Wurf ausloest
     */
    static String von(UUID wer) {
        if (wer == null) {
            // Nur fuer den Fall, dass ein Aufrufer die Besitzerfrage nicht vorher stellt.
            // Der Shopschutz tut es (ein Bundshop bekommt einen eigenen Satz ohne Namen),
            // aber ein Wurf an dieser Stelle waere eine Wache, die beim Schuetzen stirbt.
            return "-";
        }
        String name = Bukkit.getOfflinePlayer(wer).getName();
        return name != null ? name : wer.toString().substring(0, ZEICHEN_DER_KENNUNG);
    }
}
