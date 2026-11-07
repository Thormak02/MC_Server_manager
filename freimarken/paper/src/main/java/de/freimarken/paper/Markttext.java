package de.freimarken.paper;

import de.freimarken.kern.Ankaufliste;
import de.freimarken.kern.Deckelregel;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Verkaufsregel;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen von {@code /markt} - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Beruftext} fuer die Berufe:
 * Jeder Zweig von {@link MarktBefehle} holt sich hier seine Zeilen und schickt sie nur
 * hinaus. Dadurch ist jedes Wort, jede Farbe und jede Reihenfolge ohne Server pruefbar -
 * und {@code BildGleichTest} haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link MarktSatz}.
 */
final class Markttext {

    private Markttext() {
    }

    /**
     * Das Sortiment mit Preisen und was heute noch frei ist.
     *
     * @param heuteSchon was dieser Spieler heute schon aus Verkaeufen bekommen hat
     */
    static List<Component> listeZeilen(Sprache sprache, Marktwerte werte,
                                       long heuteSchon) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(MarktSatz.KOPF, Einsatz.keine()));
        Ankaufliste ankauf = werte.ankauf();
        if (ankauf.leer()) {
            // Eine eigene Zeile und nicht eine leere Liste: Ein Spieler, der nur die
            // Ueberschrift sieht, haelt es fuer einen Fehler.
            zeilen.add(sprache.eineZeile(MarktSatz.LEER, Einsatz.keine()));
            return zeilen;
        }
        // EINE Zeile aus Worten, zusammengefuegt mit dem Trenner aus der Datei -
        // dasselbe Muster wie bei den Spezialisierungen in Beruftext. Der Trenner darf
        // leer sein; dann stossen die Namen aneinander. Haesslich, aber die Entscheidung
        // des Betreibers, und dabei verstummt nichts.
        String trenner = sprache.wort(MarktSatz.ZEILEN_TRENNER, Einsatz.keine());
        List<String> stuecke = new ArrayList<>();
        for (String material : ankauf.materialien()) {
            stuecke.add(sprache.wort(MarktSatz.ZEILE, Einsatz.von("material", material)
                    .mit("preis", sprache.pfennige(ankauf.pfennige(material)))));
        }
        zeilen.add(sprache.eineZeile(MarktSatz.SORTIMENT,
                Einsatz.von("liste", String.join(trenner, stuecke))));
        // Was heute noch geht - in MARK, denn der Deckel haengt am Gebuchten. Gerechnet
        // von Deckelregel und nicht hier: Dieselbe Rechnung wie beim Buchen, sonst nennt
        // die Anzeige einen anderen Rest als der Verkauf zulaesst.
        long frei = Deckelregel.erlaubt(werte.tagesdeckel(), heuteSchon,
                werte.tagesdeckel());
        zeilen.add(sprache.eineZeile(MarktSatz.FREI,
                Einsatz.von("betrag", sprache.betrag(frei))));
        return zeilen;
    }

    /**
     * Die Zeile zu einem geglueckten Verkauf.
     *
     * <p>GEZAEHLT nach den Stuecken: "1 Stein verkauft" und "5 Stein verkauft" sind zwei
     * Formen, und der Satz entscheidet selbst, welche er nimmt.
     */
    static List<Component> verkauftZeilen(Sprache sprache, String material,
                                          Verkaufsregel.Verkauf verkauf) {
        // {anzahl} wird MITGEGEBEN und nicht von bau() eingesetzt - gemessen: Fehlt
        // es, faellt Sprache auf den rohen Wortlaut zurueck, und der Spieler liest die
        // Klammern. Die "eins"-Form nennt es nicht, darum faellt das erst bei zwei auf.
        return sprache.bau(MarktSatz.VERKAUFT, verkauf.stuecke(),
                Einsatz.von("anzahl", Sprache.deutscheZahl(verkauf.stuecke()))
                        .mit("material", material)
                        .mit("betrag", sprache.betrag(verkauf.mark())));
    }

    /**
     * Warum nichts geschehen ist.
     *
     * <p>EIN Satz je Grund und nicht ein gemeinsamer: "Der Bund kauft das nicht" und
     * "dafuer gibt es keine ganze Mark" sind zwei verschiedene Auskuenfte, und die
     * zweite verlangt eine andere Tat als die erste.
     */
    static List<Component> absageZeilen(Sprache sprache, MarktSatz grund,
                                        String material) {
        Einsatz werte = grund.platzhalter().contains("material")
                ? Einsatz.von("material", material)
                : Einsatz.keine();
        // bau und nicht eineZeile: markt.absage.kauft-nicht traegt eine Umbruchmarke -
        // die Absage sagt WAS nicht geht und daneben, wo das Sortiment steht. Ein
        // eineZeile daran waere ein Wurf, sobald der Betreiber die Datei so laesst, wie
        // sie ausgeliefert ist.
        return sprache.bau(grund, 1L, werte);
    }
}
