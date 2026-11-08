package de.freimarken.kern;

import java.util.List;
import java.util.UUID;

/**
 * Die Kistenshops - eine Zeile je Schild.
 *
 * <p>Sechs Versprechen, auf die sich der Handel verlaesst und die der {@code ShopVertrag}
 * fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Der Schluessel ist die SCHILDSTELLE</b>, nicht die Kiste. An einer Doppelkiste
 *       koennen zwei Schilder haengen, die zwei verschiedene Waren handeln - eines auf
 *       jeder Haelfte. Mit der Kiste als Schluessel waere das unmoeglich, und ein Laden
 *       mit einer Ware je Kiste braeuchte doppelt so viel Platz.</li>
 *   <li><b>{@link #anDerKiste} ist der heisse Pfad und muss es aushalten.</b> Er
 *       beantwortet die Frage "ist das eine Shopkiste", und die wird bei jedem Trichter,
 *       jeder Explosion und jedem Kolben gestellt - viele Male je Sekunde. Eine Umsetzung
 *       ueber das Netz MUSS einen Zwischenspeicher davor haben; welchen, entscheidet die
 *       Schicht darueber.</li>
 *   <li><b>Es liegt kein Geld darin.</b> Was gezahlt wurde, steht im Buchungsbuch und nur
 *       dort. Die zwei Preise hier sind keine Zahlungen, sondern ein Angebot.</li>
 *   <li><b>Es liegt keine WARE darin.</b> Der Bestand ist der Inhalt der Kiste in der
 *       Welt, und nur er. Eine gespeicherte Stueckzahl waere eine zweite Wahrheit, die
 *       beim ersten Trichter von der ersten abweicht - und dann verkauft ein Shop, was
 *       nicht da ist. Die {@link Warenprobe} sagt nur, WAS gehandelt wird.</li>
 *   <li><b>Kein Shop und keine Shopkiste sind {@code null} beziehungsweise leer</b> und
 *       kein Fehler: Die allermeisten Kisten des Servers sind keine Shops, und genau
 *       diese Antwort wird am haeufigsten gebraucht.</li>
 *   <li><b>Die Ablage entscheidet nie, ob ein Handel erlaubt ist.</b> Sie haelt Zeilen.
 *       Was ein Preis darf, steht in {@link Shopregel}; ob die Ware passt, entscheidet
 *       die Schicht am Spiel.</li>
 * </ul>
 */
public interface Shopablage {

    /**
     * Der Shop an dieser Schildstelle.
     *
     * @return der Shop, oder {@code null}, wenn dort keiner ist
     * @throws IllegalArgumentException wenn die Welt {@code null} oder leer ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    Kistenshop anDemSchild(String welt, int x, int y, int z);

    /**
     * Alle Shops, deren KISTE an dieser Stelle steht - der heisse Pfad.
     *
     * <p>Eine Liste und kein einzelner Shop: An einer Doppelkiste koennen zwei Schilder
     * haengen. Und die Frage lautet im Betrieb meist nur "ist die Liste leer" - fuer den
     * Trichterschutz, den Kistenschutz und die Explosion.
     *
     * @return die Shops, oder eine leere Liste. Leer ist der Normalfall
     * @throws IllegalArgumentException wenn die Welt {@code null} oder leer ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Kistenshop> anDerKiste(String welt, int x, int y, int z);

    /**
     * Die Shops dieses Spielers, nach Schluessel geordnet.
     *
     * <p>Fuer die Auskunft, was einem gehoert. Bundshops gehoeren niemandem und stehen in
     * keiner dieser Listen.
     *
     * @throws IllegalArgumentException wenn der Spieler {@code null} ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Kistenshop> vonSpieler(UUID besitzer);

    /**
     * Alle Shops, nach Schluessel geordnet - fuer die Suche nach einer Ware.
     *
     * <p>Die Reihenfolge ist zweimal hintereinander dieselbe. Das ist keine Zierde: Die
     * Suche zeigt die ersten Treffer, und eine wechselnde Reihenfolge zeigte bei
     * derselben Frage verschiedene Laeden.
     *
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Kistenshop> alle();

    /**
     * Schreibt den Shop - setzend, mit der Schildstelle als Schluessel.
     *
     * @throws IllegalArgumentException wenn der Shop {@code null} ist
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    void schreibe(Kistenshop shop);

    /**
     * Loescht den Shop an dieser Schildstelle - das Abbauen des Schildes.
     *
     * @return {@code true}, wenn dort einer war. {@code false} ist kein Fehler: Ein
     *         Schild, das zweimal gemeldet wird - vom Abbau und vom Herabfallen -, soll
     *         nicht das zweite Mal werfen
     * @throws IllegalArgumentException wenn die Welt {@code null} oder leer ist
     * @throws IllegalStateException wenn nicht geloescht werden konnte
     */
    boolean loesche(String welt, int x, int y, int z);
}
