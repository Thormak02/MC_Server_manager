package de.freimarken.paper;

import de.freimarken.kern.Truhenwerte;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Freien Truhe - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Markttext} fuer den Bundesmarkt:
 * {@link Truhenwache} holt sich hier ihre Zeilen und schickt sie nur hinaus. Dadurch ist
 * jedes Wort, jede Farbe und jede Reihenfolge ohne Server pruefbar - und
 * {@code BildGleichTest} haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link TruheSatz}.
 *
 * <p><b>Die Fallunterscheidung "mit Deckel oder ohne" steht an EINER Stelle</b>, naemlich
 * hier, und nicht bei der Wache: Sonst faellt sie zweimal - beim Oeffnen und beim
 * Schliessen -, und eine der beiden Stellen entscheidet sie eines Tages anders.
 */
final class Truhentext {

    private Truhentext() {
    }

    /**
     * Die Zeile beim Oeffnen: wie viele Stueck heute noch gehen.
     *
     * @param nochFrei wie viele Stueck noch gehen; bei {@code -1} gibt es keinen Deckel,
     *                 und das ist die Zahl, die {@code Truhenregel.heuteNochFrei} fuer
     *                 diesen Fall liefert
     */
    static Component beimOeffnen(Sprache sprache, int nochFrei) {
        if (nochFrei < 0) {
            return sprache.eineZeile(TruheSatz.OFFEN_FREI, Einsatz.keine());
        }
        return sprache.eineZeile(TruheSatz.OFFEN, Einsatz.von("frei", nochFrei));
    }

    /** Die Truhe bleibt zu, weil die Tagesmenge heraus ist - mit der Zahl des Deckels. */
    static Component amDeckel(Sprache sprache, Truhenwerte werte) {
        return sprache.eineZeile(TruheSatz.DECKEL,
                Einsatz.von("deckel", werte.entnahmeJeTag()));
    }

    /** Ein anderer sieht gerade hinein - die Zusage "einer zur Zeit". */
    static Component besetzt(Sprache sprache) {
        return sprache.eineZeile(TruheSatz.BESETZT, Einsatz.keine());
    }

    /** Der Tagesstand liess sich nicht lesen oder nicht schreiben. */
    static Component gestoert(Sprache sprache) {
        return sprache.eineZeile(TruheSatz.GESTOERT, Einsatz.keine());
    }

    /**
     * Die Zeilen beim Schliessen - keine, eine oder zwei.
     *
     * <p><b>KEINE Zeile, wenn er nichts genommen hat</b>, und das ist mehr als
     * Hoeflichkeit: Wer etwas HINEINLEGT, bekommt sonst eine Zeile "0 Stueck behalten" -
     * und das ist der halbe Zweck dieser Truhe, nicht ein Vorgang, der eine Meldung
     * braucht.
     *
     * <p><b>Die Rueckgabezeile steht VOR der Bilanz</b>: Erst der Grund, dann die Zahl.
     * Umgekehrt liest ein Spieler "16 Stueck behalten" und sucht danach die sieben, die
     * fehlen.
     *
     * @param behalten was er mitnimmt
     * @param zurueck  was in die Truhe zurueckgelegt wurde - 0, wenn nichts
     * @param nochFrei was danach heute noch geht; {@code -1} heisst: kein Deckel
     */
    static List<Component> beimSchliessen(Sprache sprache, Truhenwerte werte,
            int behalten, int zurueck, int nochFrei) {
        List<Component> zeilen = new ArrayList<>();
        if (zurueck > 0) {
            zeilen.add(sprache.eineZeile(TruheSatz.ZURUECK,
                    Einsatz.von("stueck", zurueck)
                            .mit("deckel", werte.entnahmeJeTag())));
        }
        if (behalten > 0 && nochFrei >= 0) {
            // OHNE Deckel schweigt die Bilanz: Dort ist "heute noch frei" keine Zahl,
            // und eine Zeile "7 Stueck behalten" allein sagt einem Spieler nichts, was
            // er nicht in seinem Rucksack sieht.
            zeilen.add(sprache.eineZeile(TruheSatz.BEHALTEN,
                    Einsatz.von("stueck", behalten).mit("frei", nochFrei)));
        }
        return zeilen;
    }

    /**
     * Ein Gegenstand, der dort nicht hineingehoert, kommt zurueck.
     *
     * @param ware der Name, den der Spieler im Tooltip liest - er steht NICHT in der
     *             Sprachdatei, aus demselben Grund wie die Materialnamen des
     *             Bundesmarktes: Vierzig Worte dafuer waeren eine zweite Abschrift einer
     *             Liste, die der Betreiber in der {@code config.yml} fuehrt
     */
    static Component verboten(Sprache sprache, String ware) {
        return sprache.eineZeile(TruheSatz.VERBOTEN, Einsatz.von("ware", ware));
    }
}
