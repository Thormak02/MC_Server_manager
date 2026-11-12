package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wer Stadtbuerger einer Freistadt ist - die Regel, an EINER Stelle.
 *
 * <p><b>Stadtbuerger und nicht Buerger</b> [12.11.2026]: Der Rang nach rund vierzig
 * Spielstunden heisst in diesem Projekt schon Buerger - in {@code config.yml}, in der
 * {@code plugin.yml} und in {@code Messregel}. Das hier ist etwas voellig anderes: Der
 * Rang kommt aus Spielzeit, die Stadtbuergerschaft aus Grundbesitz. Zwei Bedeutungen fuer
 * ein Wort in einem Programm, das in sechs Monaten jemand liest, sind die Naht, an der
 * jemand das Falsche liest - darum traegt das Neue ueberall seinen eigenen Namen, im
 * Quelltext wie im Spieltext.
 *
 * <p><b>ABGELEITET und keine Mitgliedschaft</b>: Es gibt keine Tabelle
 * {@code stadt_buerger}, keinen Beitritt und keinen Austritt. Stadtbuerger ist, wem ein
 * Grundstueck in dieser Stadt gehoert - das ist der Wortlaut des Bauplans, und es hat drei
 * Folgen, die eine eigene Tabelle nicht haette: Die Liste kann nicht veralten, Kauf,
 * Geschenk, Weiterverkauf und Aufloesen wirken von selbst, und die Spielerseite aus
 * Schritt 17 bekommt sie aus den Spalten, die schon dastehen. Ehrenbuerger ohne
 * Grundstueck liessen sich spaeter ADDITIV nachlegen; diese Entscheidung verbaut nichts.
 *
 * <p><b>Der STADTBESITZER ist kein Stadtbuerger seiner eigenen Stadt</b>, und das ist die
 * Festlegung, von der Teil E abhaengt: Jedes frisch zugeschnittene Grundstueck gehoert
 * zunaechst ihm ({@link Grundstuecksbuch#schneideZu}), damit die Flaeche nicht brachliegt,
 * bis ein Kaeufer kommt. Zaehlte er mit, haette jede Stadt nach dem ERSTEN Zuschnitt fuer
 * immer einen Buerger, und die Verfallsregel aus Plan 16 - zwoelf Monate ohne Besuch und
 * ohne Buerger - waere toter Text. So fallen Buergerzahl und die Auflage "ein VERGEBENES
 * Grundstueck" zusammen, und Teil E bekommt beide aus einer Frage.
 *
 * <p><b>Ein MITBAUER ist kein Stadtbuerger.</b> Die Trennung steht schon in
 * {@link Grundstueck}: {@code gehoert} ist der Eigentuemer, {@code darfBauen} auch der
 * Mitbauer. Ein geliehenes Baurecht ist kein Besitz - und mit der anderen Entscheidung
 * koennte ein Stadtbesitzer seine Stadt gegen den Verfall immunisieren, indem er einen
 * Freund eintraegt. Ein Wort, und die Stadt lebt zwoelf Monate laenger.
 *
 * <p><b>Eine Klasse mit statischen Regeln und kein Zustand</b>: Die Buergerschaft ist eine
 * RECHNUNG aus zwei Dingen, die anderswo stehen - der Stadtzeile und den Grundstuecken
 * darin. Wer sie hier haelt, hielte eine zweite Wahrheit.
 */
public final class Stadtbuergerschaft {

    private Stadtbuergerschaft() {
    }

    /**
     * Ein Stadtbuerger und die Zahl seiner Grundstuecke in dieser Stadt.
     *
     * <p>Die ZAHL und nicht der Zeitpunkt: "Buerger seit" waere aus
     * {@code erworbenAm} nicht ehrlich zu lesen - ein Kauf setzt den Zeitpunkt neu (Teil
     * C3), und wer sein erstes Grundstueck weiterverkauft und ein zweites behaelt, waere
     * ploetzlich jung zugezogen. Die Zahl dagegen stimmt immer.
     */
    public record Buerger(UUID wer, int grundstuecke) {

        public Buerger {
            if (wer == null) {
                throw new IllegalArgumentException("Ein Buerger ohne Kennung ist keiner");
            }
            if (grundstuecke < 1) {
                throw new IllegalArgumentException("Wer kein Grundstueck in der Stadt hat,"
                        + " ist dort auch kein Buerger: " + grundstuecke);
            }
        }
    }

    /**
     * Zaehlt jemand, dem in dieser Stadt Grund gehoert, als Stadtbuerger?
     *
     * <p><b>Die Frage fuer den heissen Pfad</b>, und sie ist der Grund, warum diese
     * Methode getrennt von {@link #aus} dasteht: Die Bauregel faellt bei jedem gesetzten
     * Block und darf keine Liste holen. Sie fragt die Ablage nach dem einen Spieler und
     * legt die Antwort hier hinein - damit laufen BEIDE Wege durch dieselbe Regel. Mit
     * einer eigenen Bedingung im Freistadtbuch waeren es zwei, und eines Tages saehe die
     * Liste jemanden, den die Wache nicht sieht.
     *
     * @param stadt         die Stadtzeile - nur ihr Eigentuemer wird gelesen
     * @param wer           der Spieler; {@code null} ist niemals Buerger
     * @param hatGrundDort  ob ihm in dieser Stadt ein Grundstueck gehoert
     */
    public static boolean istBuerger(Parzelle stadt, UUID wer, boolean hatGrundDort) {
        if (stadt == null) {
            throw new IllegalArgumentException("Ohne Stadt gibt es keine Buergerschaft -"
                    + " ein freies Feld hat keine Buerger, und das ist keine Frage,"
                    + " sondern ein Programmierfehler");
        }
        return wer != null && hatGrundDort && !stadt.gehoert(wer);
    }

    /**
     * Die Stadtbuerger einer Stadt, aus ihren Grundstuecken gerechnet.
     *
     * <p><b>Entdoppelt</b>: Wer zwei Grundstuecke hat, ist EIN Buerger mit zwei
     * Grundstuecken und steht einmal in der Liste. Die Ablage liefert Grundstuecke, keine
     * Spielermenge - und eine Liste, die jemanden zweimal nennt, laesst den Stadtbesitzer
     * seine Einwohner falsch zaehlen.
     *
     * <p><b>Geordnet nach dem AELTESTEN Grundstueck</b>, bei gleichem Zeitpunkt nach der
     * Kennung. Wer am laengsten dabei ist, steht oben; die Kennung ist der zweite
     * Schluessel, damit die Reihenfolge nicht von der Laune der Ablage abhaengt. Nach
     * NAMEN zu ordnen kann der Kern nicht - hier gibt es keine Spielernamen, und im SQL
     * stehen nur Kennungen.
     *
     * @param stadt         die Stadtzeile
     * @param grundstuecke alle Grundstuecke DIESER Stadt - was nicht dazugehoert, wird
     *                     nicht geprueft, sondern gezaehlt; die Auswahl trifft der Rufer
     */
    public static List<Buerger> aus(Parzelle stadt, List<Grundstueck> grundstuecke) {
        if (grundstuecke == null) {
            throw new IllegalArgumentException("Ohne Grundstuecke ist die Liste nicht"
                    + " leer, sondern unbekannt - und das sind zwei verschiedene Dinge");
        }
        // Zwei Tabellen in einem Durchgang: die Zahl und das aelteste Grundstueck je
        // Spieler. Mit zwei Durchgaengen liefen sie bei einer Aenderung auseinander.
        Map<UUID, Integer> zahl = new LinkedHashMap<>();
        Map<UUID, java.time.Instant> aeltestes = new LinkedHashMap<>();
        for (Grundstueck eines : grundstuecke) {
            UUID wer = eines.eigentuemer();
            if (!istBuerger(stadt, wer, true)) {
                continue;
            }
            zahl.merge(wer, 1, Integer::sum);
            java.time.Instant bisher = aeltestes.get(wer);
            if (bisher == null || eines.erworbenAm().isBefore(bisher)) {
                aeltestes.put(wer, eines.erworbenAm());
            }
        }
        List<Buerger> buerger = new ArrayList<>(zahl.size());
        for (Map.Entry<UUID, Integer> eintrag : zahl.entrySet()) {
            buerger.add(new Buerger(eintrag.getKey(), eintrag.getValue()));
        }
        buerger.sort(Comparator
                .comparing((Buerger einer) -> aeltestes.get(einer.wer()))
                .thenComparing(einer -> einer.wer().toString()));
        return List.copyOf(buerger);
    }
}
