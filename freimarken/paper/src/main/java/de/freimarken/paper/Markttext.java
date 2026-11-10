package de.freimarken.paper;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Seitenregel;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen von {@code /markt} - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Beruftext} fuer die Berufe:
 * {@link MarktBefehle} holt sich hier seine Zeilen und schickt sie nur hinaus. Dadurch
 * ist jedes Wort, jede Farbe und jede Reihenfolge ohne Server pruefbar - und
 * {@code BildGleichTest} haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link MarktSatz}.
 *
 * <p><b>Gezeigt werden die STAENDE und nicht eine Liste aus der Datei</b> [10.11.2026].
 * Die Tafel {@code bundesmarkt.ankauf} ist gefallen: "es kann ja nur ge- und verkauft
 * werden, wofuer es ein Schild gibt." Diese Naht bekommt darum Kistenshops herein und
 * keine Materialtabelle - und die Rechnung, welche davon auf welche Seite fallen, steht
 * in {@link Seitenregel}, geteilt mit {@code /shop liste}.
 */
final class Markttext {

    private Markttext() {
    }

    /**
     * Eine Seite der Staende des Bundes - Kopf, Zeilen, Hinweis.
     *
     * <p><b>Die LEERE Liste kommt zuerst</b>, und sie ist hier keine Randerscheinung:
     * Solange kein Schild steht, kauft und verkauft der Bund nichts, und das ist der
     * Normalzustand eines frischen Servers. Ein Kopf mit "0 Staende" und nichts darunter
     * liesse einen Spieler nach der Liste suchen.
     *
     * @param staende  alle Staende, geordnet - die Reihenfolge ist die der Ablage
     *                 ({@code Kistenshop.NACH_STELLE})
     * @param seite    welche Seite gewuenscht ist, ab 1
     * @param jeSeite  wie viele Staende auf eine Seite gehen
     * @param heuteSchon was dieser Spieler heute schon aus Verkaeufen bekommen hat
     */
    static List<Component> listeZeilen(Sprache sprache, List<Kistenshop> staende,
            int seite, int jeSeite, Marktwerte werte, long heuteSchon) {
        List<Component> zeilen = new ArrayList<>();
        if (staende.isEmpty()) {
            zeilen.add(sprache.eineZeile(MarktSatz.LEER, Einsatz.keine()));
            return zeilen;
        }
        int seiten = Seitenregel.seiten(staende.size(), jeSeite);
        if (!Seitenregel.gibtEs(seite, staende.size(), jeSeite)) {
            zeilen.add(sprache.eineZeile(MarktSatz.KEINE_SEITE,
                    Einsatz.von("seite", seite).mit("seiten", seiten)));
            return zeilen;
        }
        zeilen.add(sprache.eineZeile(MarktSatz.KOPF,
                Einsatz.von("anzahl", staende.size()).mit("seite", seite)
                        .mit("seiten", seiten)));
        int von = Seitenregel.ersteZeile(seite, staende.size(), jeSeite);
        int bis = Seitenregel.letzteZeile(seite, staende.size(), jeSeite);
        for (Kistenshop stand : staende.subList(von, bis)) {
            zeilen.add(sprache.eineZeile(MarktSatz.ZEILE, zeilenwerte(sprache, stand)));
        }
        if (Seitenregel.gehtWeiter(seite, staende.size(), jeSeite)) {
            zeilen.add(sprache.eineZeile(MarktSatz.WEITER,
                    Einsatz.von("naechste", seite + 1)));
        }
        // Und was heute noch zu holen ist - oder dass es keine Grenze gibt. ZWEI Saetze
        // und nicht eine Zahl: "noch 0 Mark" waere bei einem abgeschalteten Deckel das
        // Gegenteil der Wahrheit, und eine riesige Zahl liest niemand.
        zeilen.add(werte.gedeckelt()
                ? sprache.eineZeile(MarktSatz.FREI, Einsatz.von("betrag",
                        sprache.betrag(Math.max(0L, werte.tagesdeckel() - heuteSchon))))
                : sprache.eineZeile(MarktSatz.OHNE_DECKEL, Einsatz.keine()));
        return zeilen;
    }

    /** Die acht Werte einer Standzeile - die zwei Preisstuecke eingesetzt. */
    private static Einsatz zeilenwerte(Sprache sprache, Kistenshop stand) {
        return Einsatz.von("welt", stand.schildWelt())
                .mit("x", stand.schildX())
                .mit("y", stand.schildY())
                .mit("z", stand.schildZ())
                .mit("menge", stand.menge())
                .mit("ware", stand.ware().name())
                // DIE ZWEI BLICKWINKEL, und sie sind hier schon einmal vertauscht
                // worden (Shoptext, 08.11.2026): verkauftWare() heisst "der Shop
                // verkauft", also KAUFT der Kunde. Wer die zwei tauscht, bekommt an
                // einem Stand, der nur ankauft, die Zeile "Kauf 0 Mark".
                .mit("kauf", preisstueck(sprache, MarktSatz.KAUF,
                        stand.verkauftWare(), stand.kaufpreis()))
                .mit("verkauf", preisstueck(sprache, MarktSatz.VERKAUF,
                        stand.kauftWare(), stand.verkaufpreis()));
    }

    /**
     * Ein Preisstueck - oder NICHTS, wenn der Stand in dieser Richtung nicht handelt.
     *
     * <p>Dasselbe Mittel wie {@code parzelle.liste.mitbauer}: Ein leerer Wert
     * verschwindet rueckstandslos, und darum stehen die Trenner in den Stuecken. Ein
     * Stand, der nur ankauft, hat so gar keine Kaufangabe - statt einer "0 Mark", die
     * {@code Shopschild} als {@code PREIS_BEIDE_NULL} abweist.
     */
    private static String preisstueck(Sprache sprache, MarktSatz satz, boolean handelt,
            long preis) {
        return handelt
                ? sprache.wort(satz, Einsatz.von("preis", sprache.betrag(preis)))
                : "";
    }
}
