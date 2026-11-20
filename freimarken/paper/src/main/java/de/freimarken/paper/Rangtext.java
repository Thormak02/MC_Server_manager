package de.freimarken.paper;

import de.freimarken.kern.Rangregel;
import de.freimarken.kern.Rangschau;
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

    /**
     * Die Auskunft auf das nackte {@code /rang} - Kopf, Stand, naechste Stufe, Fuss.
     *
     * <p><b>Jede Bedingung bekommt ZWEI Zeilen</b>: was verlangt ist und was er hat. Die
     * erste kommt aus dem Muenzhaus ({@code mark.muenzhaus.bedingung.<art>}), weil es
     * dieselbe Frage ist - eine zweite Wortfamilie waere eine Abschrift. Die zweite ist
     * neu, und sie hat zwei Gestalten: erfuellt und offen.
     *
     * <p><b>Die Zahlen werden in die EINHEIT DER DATEI zurueckgerechnet</b>
     * ({@link de.freimarken.kern.Bedingungsart#inDerDatei}): Der Stand steht in Sekunden,
     * und ungerechnet gezeigt stuende hier "du hast 43200 Stunden". Ein GELDbetrag kommt
     * aus der Waehrung, jede andere Zahl nackt - welche Art Geld ist, sagt die Art selbst.
     *
     * <p>Kein Schalter auf eine Variable: Jeder Satz steht als Konstante da, damit Tafel
     * 10h ihn bauen und auf eine Umbruchmarke pruefen kann. Dieselbe Entscheidung wie in
     * {@link #aufstiegZeilen}.
     */
    static List<Component> schauZeilen(Sprache sprache, Rangschau.Tafel tafel) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(RangSatz.SCHAU_KOPF, Einsatz.keine()));
        if (tafel.unbekannterEintrag().isPresent()) {
            zeilen.add(sprache.eineZeile(RangSatz.SCHAU_UNBEKANNT,
                    Einsatz.von("rang", tafel.unbekannterEintrag().get())
                            .mit("gilt", tafel.jetzt().schluessel())));
        } else {
            zeilen.add(sprache.eineZeile(RangSatz.SCHAU_JETZT,
                    Einsatz.von("rang", tafel.jetzt().schluessel())));
        }
        if (tafel.naechste().isPresent()) {
            zeilen.add(sprache.eineZeile(RangSatz.SCHAU_NAECHSTE,
                    Einsatz.von("rang", tafel.naechste().get().schluessel())));
            for (Rangregel.Bedingungsstand eine : tafel.stand()) {
                zeilen.addAll(verlangtZeilen(sprache, eine));
                if (eine.erfuellt()) {
                    zeilen.add(sprache.eineZeile(RangSatz.SCHAU_STAND_ERFUELLT,
                            Einsatz.von("stand", zahl(sprache, eine, eine.gemessen()))));
                } else {
                    zeilen.add(sprache.eineZeile(RangSatz.SCHAU_STAND_OFFEN,
                            Einsatz.von("stand", zahl(sprache, eine, eine.gemessen()))));
                }
            }
        } else if (!sprache.leerGelassen(RangSatz.HOECHSTE)) {
            zeilen.add(sprache.eineZeile(RangSatz.HOECHSTE, Einsatz.keine()));
        }
        if (!sprache.leerGelassen(RangSatz.SCHAU_FUSS)) {
            zeilen.add(sprache.eineZeile(RangSatz.SCHAU_FUSS, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die ganze Leiter - je Stufe eine Zeile in einem von drei Zustaenden.
     *
     * <p>{@code jetzt} wird ZUERST gefragt: Die Stufe, auf der er steht, ist auch
     * erreicht, und in der anderen Reihenfolge stuende sie als "erreicht" da wie jede
     * andere.
     */
    static List<Component> leiterZeilen(Sprache sprache, Rangschau.Tafel tafel) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(RangSatz.LEITER_KOPF, Einsatz.keine()));
        for (Rangschau.Sprosse sprosse : tafel.leiter()) {
            Einsatz werte = Einsatz.von("rang", sprosse.stufe().schluessel());
            if (sprosse.jetzt()) {
                zeilen.add(sprache.eineZeile(RangSatz.LEITER_JETZT, werte));
            } else if (sprosse.erreicht()) {
                zeilen.add(sprache.eineZeile(RangSatz.LEITER_ERREICHT, werte));
            } else {
                zeilen.add(sprache.eineZeile(RangSatz.LEITER_OFFEN, werte));
            }
        }
        return List.copyOf(zeilen);
    }

    /** Es gibt keine Rangleiter - eine Zeile, damit der Befehl nicht stumm bleibt. */
    static List<Component> ausZeilen(Sprache sprache) {
        return List.of(sprache.eineZeile(RangSatz.AUS, Einsatz.keine()));
    }

    /** Die Ablage hat nicht geantwortet. */
    static List<Component> gestoertZeilen(Sprache sprache) {
        return List.of(sprache.eineZeile(RangSatz.GESTOERT, Einsatz.keine()));
    }

    /** Auf der Konsole gibt es keinen Rang. */
    static List<Component> keineKonsoleZeilen(Sprache sprache) {
        return List.of(sprache.eineZeile(RangSatz.KEINE_KONSOLE, Einsatz.keine()));
    }

    /** Die Zeile mit dem, was VERLANGT ist - aus der Wortfamilie des Muenzhauses. */
    private static List<Component> verlangtZeilen(Sprache sprache,
                                                  Rangregel.Bedingungsstand eine) {
        long ausDerDatei = eine.art().inDerDatei(eine.verlangt());
        return sprache.bau(Sprachschluessel.bedingung(eine.art()), ausDerDatei,
                Einsatz.von("zahl", zahl(sprache, eine, eine.verlangt())));
    }

    /**
     * Eine Zahl dieser Bedingung, in der Einheit der Datei und mit Waehrung, wo sie
     * hingehoert.
     *
     * <p>Dieselbe Rechnung fuer das Verlangte und fuer den Stand - zweimal hingeschrieben
     * stuende das Guthaben eines Tages einmal mit und einmal ohne Waehrungswort da.
     */
    private static String zahl(Sprache sprache, Rangregel.Bedingungsstand eine,
                               long inMesseinheit) {
        long ausDerDatei = eine.art().inDerDatei(inMesseinheit);
        return eine.art().istGeld() ? sprache.betrag(ausDerDatei)
                : String.valueOf(ausDerDatei);
    }
}
