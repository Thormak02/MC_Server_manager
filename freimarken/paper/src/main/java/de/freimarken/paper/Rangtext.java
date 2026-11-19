package de.freimarken.paper;

import de.freimarken.kern.Rangwerte;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Rangleiter - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Bonustext} fuer den Tagesbonus:
 * Wer eine Zeile braucht, holt sie hier und schickt sie nur hinaus. Dadurch ist jedes
 * Wort, jede Farbe und jede Verzweigung ohne Server pruefbar.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link RangSatz}. Was hier bleibt, ist die WAHL - ob es noch eine Stufe darueber gibt
 * und ob der Betreiber den Ausblick ueberhaupt will. Das weiss nur das Programm.
 */
final class Rangtext {

    private Rangtext() {
    }

    /**
     * Die Nachricht ueber einen Aufstieg - eine oder zwei Zeilen.
     *
     * <p>Die erste Zeile ist der Aufstieg selbst und kommt immer. Die zweite ist der
     * Ausblick, und sie hat drei moegliche Gestalten:
     * <ul>
     *   <li>Es gibt eine Stufe darueber - {@link RangSatz#NAECHSTER} mit ihrem
     *       Schluessel.
     *   <li>Es gibt keine mehr - {@link RangSatz#HOECHSTE}.
     *   <li>Der Betreiber hat die betreffende Zeile LEER gelassen - dann faellt sie weg.
     *       Das ist seine Entscheidung und kein verstummter Satz; die Begruendung steht
     *       an {@link RangSatz}.
     * </ul>
     *
     * <p><b>EINE Liste und nicht zwei einzeln gesendete Zeilen</b> - dieselbe Lehre wie
     * bei {@code /playtime}: Eine einzeln gesendete Zeile faellt bei einem Umbau heraus,
     * ohne dass eine Probe rot wird.
     *
     * <p><b>Die Pruefung auf leer steht zweimal da, und das ist Absicht</b> [19.11.2026]:
     * Zusammengefasst in eine Hilfsmethode lief der Satz als VARIABLE in
     * {@code sprache.eineZeile} - und dann kann Tafel 10h ihn nicht bauen und nicht
     * pruefen, ob er eine Umbruchmarke traegt. Tafel 10i hat genau das gemeldet. Eine
     * Umbruchmarke in einem Satz, der als EINE Zeile geholt wird, wirft im Betrieb; zwei
     * sichtbare Konstanten sind diese Doppelung wert.
     *
     * @param werte die GELTENDE Leiter; aus ihr kommt, ob es eine Stufe darueber gibt.
     *              Hereingegeben und nicht hier geholt, damit diese Rechnung ohne
     *              Wirtschaftsstand prueffaehig bleibt
     * @param rang  der Schluessel der erreichten Stufe
     */
    static List<Component> aufstiegZeilen(Sprache sprache, Rangwerte werte, String rang) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(RangSatz.AUFGESTIEGEN,
                Einsatz.von("rang", rang)));
        var darueber = werte.naechste(rang);
        if (darueber.isPresent()) {
            // leerGelassen pruefen und NICHT einfach senden: Ein leerer Wortlaut waere
            // eine leere Chatzeile, und die liest sich wie ein Fehler.
            if (!sprache.leerGelassen(RangSatz.NAECHSTER)) {
                zeilen.add(sprache.eineZeile(RangSatz.NAECHSTER,
                        Einsatz.von("rang", darueber.get().schluessel())));
            }
        } else if (!sprache.leerGelassen(RangSatz.HOECHSTE)) {
            zeilen.add(sprache.eineZeile(RangSatz.HOECHSTE, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }
}
