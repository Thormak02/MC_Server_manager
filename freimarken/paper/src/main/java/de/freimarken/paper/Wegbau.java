package de.freimarken.paper;

import de.freimarken.kern.Gruppenflaeche;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import java.util.Collection;
import java.util.List;
import org.bukkit.Material;

/**
 * Der Boden einer Gruppe: den Weg fuellen und den Weg zurueckbauen.
 *
 * <p>Hier wird nichts entschieden und nichts gerechnet. Welche Koordinate was wird, sagt
 * die {@link Gruppenflaeche} im Kern; welcher Block wohin gehoert und wie eine Stelle zu
 * werten ist, sagt {@link Stadtboden}. Diese Klasse laeuft die Koordinaten ab und zaehlt.
 *
 * <p>Die Welt erreicht sie ueber den {@link Weltgriff} und nicht ueber {@code World} -
 * damit sind die Blockzahlen ohne Server pruefbar.
 *
 * <p>Zaehlen und Herstellen laufen dieselbe Spalte ({@link #ueberDieSpalte}) mit denselben
 * zwei Sollwerten. Sonst nennt die Warnung eine Zahl und das Bestaetigen nimmt etwas
 * anderes mit - und das ist hier besonders teuer, weil das eine die Zusage ist und das
 * andere die Tat.
 *
 * <p>Die Ablage fuehrt, die Welt folgt - deshalb laeuft der Fuellauftrag immer NACH dem
 * Besitzbuch. Gefuellte Bloecke ohne Recht darauf waeren das Fehlerbild, das wie ein
 * Rechtefehler aussieht und keiner ist.
 */
final class Wegbau {

    /**
     * Was das Auftrennen dieser Flaeche in der Welt anrichtet.
     *
     * <p>Zwei Zahlen und nicht eine, weil es zwei verschiedene Dinge sind: Das eine ist
     * ein Verlust, das andere nicht.
     *
     * @param verschwindet Bloecke, die dort stehen und danach weg sind
     * @param verfuellt    Fehlstellen unter der Flaeche, die der Weg wieder zumacht
     */
    record Aufwand(int verschwindet, int verfuellt) {

        boolean istNichts() {
            return verschwindet == 0 && verfuellt == 0;
        }
    }

    /** Eine Hoehe in der Spalte, mit beiden Sollwerten. */
    @FunctionalInterface
    private interface AufDerHoehe {

        /**
         * @param ist      was dort jetzt liegt
         * @param gefuellt was dort liegt, solange die Gruppe besteht
         * @param zurueck  was der Zurueckbau dort hinschreibt
         */
        void an(int y, Material ist, Material gefuellt, Material zurueck);
    }

    private final Rasterplan plan;
    private final Weltgriff welt;

    Wegbau(Rasterplan plan, Weltgriff welt) {
        this.plan = plan;
        this.welt = welt;
    }

    /**
     * Legt die Naht der Gruppe: Fugen werden Bauland, die Bandenden werden zugemauert.
     *
     * <p>Angefasst werden genau die zwei Schichten, die der Generator je Feldart
     * verschieden setzt: die Oberflaeche und der Block darueber. Alles andere bleibt, wie
     * es ist - unter einem Weg liegt dieselbe Erde wie unter einer Parzelle, und was
     * jemand darueber gebaut hat, ist nicht im Weg.
     *
     * <p>Geschrieben wird stur {@code mitGruppe}. Welche Stelle Bauland wird und welche
     * Mauer, hat die {@link Gruppenflaeche} schon entschieden; hier noch einmal zu
     * unterscheiden hiesse, dieselbe Regel zweimal zu haben.
     *
     * @param schonGelegt die Gruppen, die es vor dieser Zusammenlegung schon gab - deren
     *                    Fugen sind Bauland, auf dem gebaut wurde, und bleiben unberuehrt
     * @return wie viele Bloecke sich geaendert haben
     */
    int fuelle(List<Parzelle> felder, Collection<List<Parzelle>> schonGelegt) {
        int[] geaendert = {0};
        Gruppenflaeche.aus(felder).ueberDieNaht(plan, flaechen(schonGelegt),
                (x, z, ohneGruppe, mitGruppe) -> {
                    geaendert[0] += setze(x, Stadtboden.BODEN, z,
                            Stadtboden.oberflaeche(mitGruppe));
                    geaendert[0] += setze(x, Stadtboden.BODEN + 1, z,
                            Stadtboden.darueber(mitGruppe));
                });
        return geaendert[0];
    }

    /**
     * Was das Auftrennen kosten wuerde - die Zahlen fuer die Warnung.
     *
     * <p>Absichtlich nicht "wie viele Bloecke aendert der Zurueckbau": Der Weg selbst kommt
     * ja zurueck, das sind bei zwei Feldern immer dieselben 596 Bloecke. Gezaehlt wird nur,
     * was dem Spieler gehoert - direkt nach dem Zusammenlegen also nichts.
     *
     * <p>Die Mauerstuecke an den Bandenden bleiben aussen vor: Dort durfte nie jemand
     * bauen, die Mauerkrone darueber hat das Zusammenlegen selbst gesetzt, und der
     * Zurueckbau fasst dort nur diese zwei Schichten an.
     *
     * @param bleibend die Gruppen, die danach noch bestehen - deren Naht bleibt liegen und
     *                 kostet nichts. Beim Herausloesen eines einzelnen Feldes ist das der
     *                 Rest des Grundstuecks, beim ganzen Auftrennen nichts.
     */
    Aufwand aufwandBeimAuftrennen(List<Parzelle> felder,
                                  Collection<List<Parzelle>> bleibend) {
        int[] zahl = {0, 0};
        Gruppenflaeche.aus(felder).ueberDieNaht(plan, flaechen(bleibend),
                (x, z, ohneGruppe, mitGruppe) -> {
                    if (mitGruppe != Rasterplan.Feld.PARZELLE) {
                        return;
                    }
                    ueberDieSpalte(x, z, mitGruppe, ohneGruppe,
                            (y, ist, gefuellt, zurueck) -> {
                        switch (Stadtboden.wertung(ist, gefuellt, zurueck)) {
                            case VERSCHWINDET -> zahl[0]++;
                            case WIRD_VERFUELLT -> zahl[1]++;
                            case UNVERAENDERT -> { }
                        }
                    });
                });
        return new Aufwand(zahl[0], zahl[1]);
    }

    /**
     * Baut den Weg zurueck - und raeumt dafuer, was auf ihm steht.
     *
     * <p>Zurueckgeschrieben wird stur {@code ohneGruppe} - dieselbe Stelle, die das
     * Fuellen als {@code mitGruppe} bekommen hat. Deshalb verschwindet auch das
     * Mauerstueck am Bandende wieder und gibt die Querstrasse frei.
     *
     * <p>Das Raeumen ist der Preis des Auftrennens, nicht ein Nebenschaden: Ohne ihn
     * stuende eine Mauer quer ueber einer oeffentlichen Strasse, die niemand mehr
     * entfernen darf. Die Warnung nennt die Zahlen vorher, und bestaetigen muss der
     * Spieler mit einem zweiten Befehl.
     *
     * <p>Geraeumt wird nur ueber den Fugen. Ein Mauerstueck am Bandende war oeffentlicher
     * Weg: Dort durfte niemand bauen oder graben, und was trotzdem steht - eine Laterne der
     * Stadt etwa -, gehoert nicht dem Eigentuemer. Es mit abzureissen waere Schaden an
     * fremdem Werk, und gewarnt haette davor nichts, weil die Zaehlung diese Stellen
     * auslaesst.
     *
     * @param bleibend die Gruppen, die danach noch bestehen - deren Naht bleibt Bauland.
     *                 Das ist dieselbe Auslassung wie beim Fuellen, nur in die andere
     *                 Richtung: Loest ein Feld sich heraus, kommt nur der Weg dorthin
     *                 zurueck, nicht jeder Weg im Grundstueck.
     * @return wie viele Bloecke sich geaendert haben
     */
    int baueZurueck(List<Parzelle> felder, Collection<List<Parzelle>> bleibend) {
        int[] geaendert = {0};
        Gruppenflaeche.aus(felder).ueberDieNaht(plan, flaechen(bleibend),
                (x, z, ohneGruppe, mitGruppe) -> {
                    if (mitGruppe == Rasterplan.Feld.PARZELLE) {
                        ueberDieSpalte(x, z, mitGruppe, ohneGruppe,
                                (y, ist, gefuellt, zurueck) ->
                                        geaendert[0] += setze(x, y, z, zurueck));
                        return;
                    }
                    geaendert[0] += setze(x, Stadtboden.BODEN + 1, z,
                            Stadtboden.darueber(ohneGruppe));
                    geaendert[0] += setze(x, Stadtboden.BODEN, z,
                            Stadtboden.oberflaeche(ohneGruppe));
                });
        return geaendert[0];
    }

    /** Die Flaechen dieser Gruppen - leere Listen fallen weg. */
    private static List<Gruppenflaeche> flaechen(Collection<List<Parzelle>> gruppen) {
        return gruppen.stream()
                .filter(gruppe -> !gruppe.isEmpty())
                .map(Gruppenflaeche::aus)
                .toList();
    }

    /**
     * Laeuft eine Spalte der Fuge ab und nennt je Hoehe beide Sollwerte.
     *
     * <p>Die eine Stelle, an der steht, welche Hoehen eine Fuge ausmachen - einmal zum
     * Zaehlen und einmal zum Herstellen. Zwei Formulierungen waeren genau der Fehler, der
     * sich erst im Nachhinein zeigt.
     *
     * <p>Nach oben ab dem hoechsten Block, mindestens aber ab der Schicht ueber dem Boden:
     * Ueber einer abgegrabenen Spalte liegt der hoechste Block UNTER der Oberflaeche, und
     * ohne das Mindestmass fiele genau die Schicht aus, um die es geht.
     *
     * <p>Nach unten bis zur ersten Schicht, die schon stimmt. Bei unberuehrtem Boden ist
     * das ein einziger Blick, bei einem Schacht laeuft sie bis zu dessen Grund, und ein
     * Fremdblock darin haelt sie nicht auf. Was sie NICHT findet, ist ein Hohlraum unter
     * einer stehengelassenen Decke: Liegt auf 61 wieder Erde, bricht sie dort ab. Den Keller
     * darunter zu finden hiesse, jede Spalte bis zur Weltunterkante zu lesen - 126 Bloecke
     * statt einem, bei vier Feldern ueber 230.000 nur fuer diesen Fall.
     */
    private void ueberDieSpalte(int x, int z, Rasterplan.Feld mitGruppe,
                                Rasterplan.Feld ohneGruppe, AufDerHoehe arbeit) {
        int unten = welt.unten();
        int oben = Math.max(welt.oben(x, z), Stadtboden.BODEN + 1);
        for (int y = oben; y >= Stadtboden.BODEN; y--) {
            arbeit.an(y, welt.bei(x, y, z),
                    Stadtboden.soll(y, mitGruppe, unten),
                    Stadtboden.soll(y, ohneGruppe, unten));
        }
        for (int y = Stadtboden.BODEN - 1; y >= unten; y--) {
            Material ist = welt.bei(x, y, z);
            Material zurueck = Stadtboden.soll(y, ohneGruppe, unten);
            if (ist == zurueck) {
                return;
            }
            arbeit.an(y, ist, Stadtboden.soll(y, mitGruppe, unten), zurueck);
        }
    }

    /**
     * Setzt einen Block, falls dort nicht schon der richtige steht.
     *
     * @return 1, wenn sich etwas geaendert hat, sonst 0
     */
    private int setze(int x, int y, int z, Material soll) {
        if (welt.bei(x, y, z) == soll) {
            return 0;
        }
        welt.setze(x, y, z, soll);
        return 1;
    }
}
