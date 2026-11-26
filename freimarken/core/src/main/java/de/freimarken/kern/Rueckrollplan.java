package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Was zu tun ist, um einen Eingriff zurueckzunehmen - gerechnet, nicht ausgefuehrt.
 *
 * <p><b>Die ganze Rechnung ohne eine Zeile Bukkit</b>, und das ist der Grund, warum es
 * diese Klasse gibt: {@code createBlockData} und {@code setBlockData} werfen ohne
 * laufenden Server (dieselbe Grenze wie bei {@code Material.isSolid}, gemessen am
 * 09.11.2026). Stuende die Rechnung bei der Ausfuehrung, waere sie in keiner Probe
 * erreichbar - und eine Rueckrollrechnung, die niemand pruefen kann, ist eine Zusage.
 * Hier kommen Zeilen herein und eine Liste von Setzungen heraus; was damit geschieht,
 * entscheidet der Aufrufer.
 *
 * <p><b>Je Koordinate die AELTESTE Zeile.</b> Sie traegt den Zustand, der vor dem
 * Eingriff dastand - und genau der soll wiederkommen. Hat ein Griefer einen Block
 * abgebaut und danach etwas anderes hingesetzt, stellt die aelteste Zeile den
 * ursprunglichen Block wieder her; die jungere waere der Zwischenstand, und der sieht
 * wie ein geglueckter Rueckweg aus, ohne einer zu sein.
 *
 * <p><b>Verbraucht werden trotzdem ALLE Zeilen</b> ({@link Plan#nummern}), nicht nur die
 * aelteste je Koordinate. Sonst saehe ein zweiter Lauf die uebrigen und stellte den
 * Zwischenstand her - also genau das, was der erste Lauf vermieden hat.
 *
 * <p><b>Was NICHT zurueckgerollt wird</b>, und zwar ausdruecklich: Der Inhalt von
 * Behaeltern, Schildzeilen, Wesen (Rahmen, Gemaelde), Fluessigkeitsausbreitung und
 * Kolben. Das Blockbuch haelt sie nicht, und ein Rueckweg, der sie erfindet, ist
 * schlimmer als einer, der sie weglaesst. Eine Truhe kommt leer an ihre Stelle zurueck.
 */
public final class Rueckrollplan {

    /** Der Zustandstext fuer Luft - was bei einer zurueckgenommenen Setzung dasteht. */
    public static final String LUFT = "minecraft:air";

    private Rueckrollplan() {
    }

    /**
     * Eine Stelle und der Zustand, der dort wieder hinkommt.
     *
     * @param ort      wo
     * @param zustand  der Vanilla-Zustandstext, den der Aufrufer setzen soll - immer
     *                 gefuellt, nie leer
     * @param wegnehmen ob dies eine ZURUECKGENOMMENE SETZUNG ist, also etwas
     *                 weggenommen wird. Entscheidet die Reihenfolge, nicht die Wirkung
     */
    public record Setzung(Blockort ort, String zustand, boolean wegnehmen) {

        public Setzung {
            if (ort == null) {
                throw new IllegalArgumentException("Eine Setzung ohne Ort");
            }
            if (zustand == null || zustand.isBlank()) {
                throw new IllegalArgumentException("Eine Setzung ohne Zustand - auch"
                        + " Luft hat einen (" + LUFT + ")");
            }
        }
    }

    /**
     * Der fertige Plan.
     *
     * @param setzungen was zu setzen ist, in der Reihenfolge, in der es gesetzt werden
     *                  MUSS
     * @param nummern   die Zeilen, die dieser Plan verbraucht - sie sind danach als
     *                  zurueckgenommen zu merken
     * @param gelesen   wie viele Zeilen hereinkamen; fuer die Meldung an den Betreiber
     */
    public record Plan(List<Setzung> setzungen, List<Long> nummern, int gelesen) {

        public Plan {
            setzungen = List.copyOf(setzungen);
            nummern = List.copyOf(nummern);
        }

        /** Ob es nichts zu tun gibt - der haeufigste Fall, und kein Fehler. */
        public boolean leer() {
            return setzungen.isEmpty();
        }
    }

    /**
     * Rechnet den Plan aus den Zeilen eines Bereichs.
     *
     * <p>Die Reihenfolge der Eingabe ist gleichgueltig - hier wird selbst sortiert. Das
     * ist Absicht und nicht Misstrauen gegen die Ablage: Eine Rechnung, die von der
     * Sortierung ihres Aufrufers abhaengt, ist in einer Probe gruen und im Spiel falsch,
     * sobald jemand eine zweite Abfrage dazubaut.
     *
     * @param zeilen die Zeilen des Bereichs; {@code null}-Eintraege werden uebergangen
     */
    public static Plan aus(List<Blockzeile> zeilen) {
        if (zeilen == null) {
            throw new IllegalArgumentException("Ohne Zeilen gibt es keinen Plan - eine"
                    + " LEERE Liste ist erlaubt und ergibt einen leeren Plan");
        }
        List<Blockzeile> sortiert = new ArrayList<>();
        for (Blockzeile zeile : zeilen) {
            if (zeile != null && zaehlt(zeile)) {
                sortiert.add(zeile);
            }
        }
        // AELTESTE zuerst, und bei gleicher Millisekunde die kleinere Nummer: Zwei Taten
        // in derselben Millisekunde gibt es wirklich - eine Sprengung schreibt hunderte
        // Zeilen in einem Augenblick -, und ohne die Nummer als zweites Merkmal waere
        // die Reihenfolge dann dem Zufall ueberlassen.
        sortiert.sort(Comparator.comparing(Blockzeile::zeitpunkt)
                .thenComparingLong(Blockzeile::id));

        Map<Blockort, Blockzeile> aeltesteJeOrt = new LinkedHashMap<>();
        List<Long> nummern = new ArrayList<>();
        for (Blockzeile zeile : sortiert) {
            // putIfAbsent haelt die ERSTE und damit aelteste Zeile je Ort.
            aeltesteJeOrt.putIfAbsent(zeile.ort(), zeile);
            if (zeile.id() != Blockzeile.OHNE_NUMMER) {
                nummern.add(zeile.id());
            }
        }

        List<Setzung> setzungen = new ArrayList<>();
        aeltesteJeOrt.forEach((ort, zeile) -> setzungen.add(setzungFuer(ort, zeile)));
        // DIE REIHENFOLGE IST TEIL DES PLANS, und sie ist zweigeteilt:
        //
        // Erst WEGNEHMEN, von oben nach unten. Wer einen Turm von unten wegnimmt, laesst
        // den Rest fallen - Sand und Kies kommen dann an einer Stelle an, an der sie
        // nie standen, und der Rueckweg hat den Schaden vergroessert.
        //
        // Dann WIEDERHERSTELLEN, von unten nach oben. Eine Fackel, eine Tuer, ein
        // Schild braucht den Block, an dem sie haengt - kommt sie zuerst, faellt sie im
        // selben Augenblick wieder ab.
        setzungen.sort(Comparator
                .comparing((Setzung s) -> !s.wegnehmen())
                .thenComparingInt(s -> s.wegnehmen() ? -s.ort().y() : s.ort().y())
                .thenComparingInt(s -> s.ort().x())
                .thenComparingInt(s -> s.ort().z()));
        return new Plan(setzungen, nummern, sortiert.size());
    }

    /**
     * Ob diese Zeile fuer einen Rueckweg zaehlt.
     *
     * <p>Zwei Faelle fallen heraus, und beide aus demselben Grund: Sie haben die Welt
     * nicht geaendert.
     *
     * <ul>
     *   <li><b>Misslungen</b> ({@code gelungen == false}): Ein Schutz hat abgebrochen,
     *       der Block steht noch. Diese Zeilen sind der wertvollste Teil des Blockbuchs
     *       fuer die Moderation - und fuer einen Rueckweg sind sie nichts.</li>
     *   <li><b>{@link Blocktat#BENUTZEN}</b>: Eine geoeffnete Truhe ist kein Eingriff in
     *       die Welt. Sie bleibt im Buch stehen, auch nach einem Rueckweg: Die Frage
     *       "wer war in meiner Truhe" wird durch einen Rueckweg nicht beantwortet.</li>
     * </ul>
     */
    private static boolean zaehlt(Blockzeile zeile) {
        return zeile.gelungen() && zeile.tat() != Blocktat.BENUTZEN;
    }

    /**
     * Die Setzung zu einer Zeile - mit dem Rueckfall fuer alte Zeilen.
     *
     * <p><b>Der Rueckfall ist der interessante Teil</b>: Zeilen aus der Zeit vor dem
     * 26.11.2026 haben keinen Vorzustand, denn die Spalte gab es nicht. Sie sind
     * trotzdem brauchbar, nur groeber:
     *
     * <ul>
     *   <li>Eine SETZUNG wird zu Luft. Das ist in den meisten Faellen richtig und im
     *       Garten falsch - wer in Wasser gebaut hat, bekommt Luft statt Wasser
     *       zurueck.</li>
     *   <li>Ein ABBAU oder eine SPRENGUNG wird zum Block ohne Zustand. Die Treppe kommt
     *       wieder, ihre Blickrichtung nicht.</li>
     * </ul>
     *
     * <p>Beides ist besser als nichts und schlechter als eine neue Zeile - darum nennt
     * der Befehl dem Betreiber, WIE VIELE Zeilen ohne Vorzustand dabei waren.
     */
    private static Setzung setzungFuer(Blockort ort, Blockzeile zeile) {
        boolean wegnehmen = zeile.tat() == Blocktat.SETZEN;
        if (zeile.hatVorzustand()) {
            return new Setzung(ort, zeile.vorzustand(), wegnehmen);
        }
        return new Setzung(ort, wegnehmen ? LUFT : "minecraft:" + zeile.block(),
                wegnehmen);
    }

    /**
     * Wie viele Zeilen des Plans ohne Vorzustand auskommen mussten.
     *
     * <p>Die Zahl gehoert in die Meldung an den Betreiber: Ein Rueckweg, der zur Haelfte
     * aus Rueckfaellen besteht, stellt Bloecke ohne Blickrichtung wieder her, und das
     * sieht man dem Ergebnis an. Wer es vorher weiss, wundert sich nicht.
     */
    public static int ohneVorzustand(List<Blockzeile> zeilen) {
        if (zeilen == null) {
            return 0;
        }
        int ohne = 0;
        for (Blockzeile zeile : zeilen) {
            if (zeile != null && zaehlt(zeile) && !zeile.hatVorzustand()) {
                ohne++;
            }
        }
        return ohne;
    }
}
