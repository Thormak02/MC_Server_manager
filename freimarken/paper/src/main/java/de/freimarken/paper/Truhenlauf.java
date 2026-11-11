package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Leert die Freien Truhen und raeumt die Tageszeilen - einmal am Tag, am Spielzeittakt.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Mietlauf} und {@link Blockraeumung}:
 * WANN geleert wird, entscheidet dieser Lauf; WIE eine Kiste leer wird, steht hinter der
 * Naht {@link Leerung}. Darum ist der Tageswechsel ohne Server pruefbar.
 *
 * <p><b>Warum ueberhaupt geleert wird</b> (Plan 7.2): Eine Truhe, in die jeder legen darf,
 * ist nach einer Woche voll - und zwar mit dem, was niemand wollte. Ein taegliches Leeren
 * macht aus dem Muellhaufen wieder eine Gabe: Wer etwas verschenkt, verschenkt es an
 * HEUTE, und wer etwas sucht, findet es oder eben nicht.
 *
 * <p><b>Eine offene Truhe wird NICHT geleert</b>, und das ist die tragende Entscheidung
 * dieses Laufs. Die {@link Truhenwache} rechnet die Entnahme als Unterschied zwischen
 * Oeffnen und Schliessen; wuerde dazwischen der Lauf die Kiste leeren, waere der ganze
 * Inhalt die Entnahme dieses Spielers - er bekaeme einen Tagesdeckel fuer Ware, die er
 * nie gesehen hat, und der Rest ginge als "Ueberschuss" aus seinem Rucksack zurueck.
 * Darum: Wer gerade hineinsieht, haelt den Lauf auf, und der Tagesmerker bleibt stehen -
 * dieselbe Form wie beim begrenzten Happen der {@link Blockraeumung}.
 *
 * <p><b>Geraeumt wird AUCH, wenn es keine Truhe mehr gibt</b>, und darum fragt dieser
 * Lauf nicht zuerst nach Stellen. Wer die letzte Stelle aus der {@code config.yml} nimmt,
 * will seine alten Tageszeilen nicht fuer immer behalten - und sie waeren sonst genau
 * das: unloeschbar, weil niemand mehr raeumt. Dieselbe Entscheidung wie bei der
 * {@link Blockraeumung}, die auch dann laeuft, wenn das Blockbuch nichts mehr schreibt.
 *
 * <p><b>Erst leeren, dann raeumen.</b> Die Zeilen von gestern sind der Tagesdeckel von
 * gestern; wer sie loeschte, bevor die Truhe leer ist, oeffnete jedem den Deckel genau in
 * dem Augenblick, in dem noch der alte Inhalt darin liegt.
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Gerufen wird aus dem Spielzeittakt, und
 * eine Ausnahme dort waere nicht eine verlorene Leerung, sondern die Spielzeit aller
 * Spieler bis zum naechsten Neustart - dieselbe Begruendung wie bei {@link Mietlauf}.
 */
final class Truhenlauf {

    /**
     * Wie eine Kiste leer wird - die Naht zum Spiel.
     *
     * <p>Eine Zahl und kein {@code void}: Der Betreiber soll im Protokoll lesen, wie viel
     * jede Nacht verschwindet. Eine Truhe, die Tag fuer Tag 500 Stueck schluckt, ist
     * entweder beliebt oder ein Muellschlucker - beides will man wissen.
     */
    interface Leerung {

        /**
         * Leert die Truhe an dieser Stelle.
         *
         * @return wie viele STUECK verschwunden sind, oder {@link #SPAETER}, wenn es
         *         gerade nicht geht - weil jemand hineinsieht oder die Welt nicht geladen
         *         ist
         */
        int leere(Blockort ort);
    }

    /** Die Antwort "jetzt nicht" aus {@link Leerung#leere}. */
    static final int SPAETER = -1;

    /** Was ein Taktschlag getan hat. */
    record Ergebnis(int geleert, int zeilen, boolean spaeter) {

        /** Der Normalfall: heute ist schon geleert, es war nichts zu tun. */
        static Ergebnis nichtsZuTun() {
            return new Ergebnis(0, 0, false);
        }
    }

    private final Truhenbuch buch;
    private final Leerung leerung;
    private final Logger protokoll;

    /**
     * Der Tag, an dem zuletzt VOLLSTAENDIG geleert wurde - nur im Arbeitsspeicher.
     *
     * <p>Keine Spalte in der Datenbank, dieselbe Entscheidung wie beim {@link Mietlauf}
     * und bei der {@link Blockraeumung}: Ein Merker, der einen Neustart ueberlebt, waere
     * eine zweite Wahrheit ueber "heute erledigt".
     *
     * <p><b>EIN NEUSTART KOSTET DEN INHALT DER TRUHE</b>, und das ist gewollt: Der erste
     * Takt nach dem Start findet einen leeren Merker und leert. Der andere Weg waere
     * schlimmer - ein Merker, der beim Start auf "heute schon erledigt" stuende, liesse
     * einen jede Nacht neu gestarteten Server NIE leeren, und die Freie Truhe waere ein
     * Lager. Verloren geht dabei Ware, die in derselben Nacht ohnehin verschwunden waere;
     * die TAGESZEILEN bleiben stehen, denn geraeumt wird nur, was vor heute liegt.
     */
    private LocalDate zuletzt;

    /**
     * Auf welchen Tag sich die zwei Merker darunter beziehen.
     *
     * <p>Getrennt von {@link #zuletzt}: Jener heisst "heute ist ALLES erledigt", dieser
     * nur "diese Merker gehoeren zu diesem Tag". Ein Tag, der angefangen und nicht
     * fertig geworden ist, braucht beides.
     */
    private LocalDate tag;

    /**
     * Welche Stellen heute schon geleert sind - ein Merker JE STELLE.
     *
     * <p><b>Der Fund der Lesesuche vom 10.11.2026.</b> Vorher gab es nur den Tagesmerker
     * fuer alle Stellen: Meldete EINE Stelle SPAETER, leerte der naechste Takt auch alle
     * anderen erneut - und damit das, was Spieler seitdem hineingelegt haben. Die alte
     * Begruendung ("ein zweites Leeren einer leeren Truhe kostet nichts") gilt nur, wenn
     * zwischen zwei Takten nichts geschieht; der Takt steht ab Werk auf 60 Sekunden, und
     * dazwischen liegt das halbe Spielgeschehen.
     *
     * <p>Eine Doppeltruhe steht mit BEIDEN Haelften darin und wird darum zweimal gefragt.
     * Das ist richtig und kostet nichts: Die zweite Haelfte ist nach der ersten leer.
     */
    private final Set<Blockort> geleerteStellen = new HashSet<>();

    /**
     * Ob der "NICHT fertig"-Satz heute schon im Protokoll steht.
     *
     * <p>Ohne diese Entprellung schreibt ein dauerhaft blockierter Lauf bei jedem Takt
     * eine Zeile - 1.440 am Tag, bei {@code takt-sekunden: 10} achttausend. Dieselbe
     * Antwort wie {@code Truhenleerung.schonBeklagt} eine Schicht tiefer.
     */
    private boolean spaeterGemeldet;

    Truhenlauf(Truhenbuch buch, Leerung leerung, Logger protokoll) {
        if (buch == null || leerung == null || protokoll == null) {
            throw new IllegalArgumentException("Buch, Leerung und Protokoll sind Pflicht");
        }
        this.buch = buch;
        this.leerung = leerung;
        this.protokoll = protokoll;
    }

    /**
     * Der Taktschlag: leert, wenn heute noch nicht geleert wurde.
     *
     * <p>Die Tagesabfrage steht VOR dem Leeren, und das ist keine Feinheit: Der Takt
     * schlaegt je nach Einstellung alle zehn bis sechshundert Sekunden, und an fast allen
     * Schlaegen ist nichts zu tun.
     */
    Ergebnis imTakt() {
        LocalDate heute = buch.heute();
        if (heute.equals(zuletzt)) {
            return Ergebnis.nichtsZuTun();
        }
        neuerTag(heute);
        int geleert = 0;
        boolean spaeter = false;
        for (Blockort ort : buch.stellen()) {
            if (geleerteStellen.contains(ort)) {
                // HEUTE SCHON GELEERT. Ein zweites Leeren waere kein leerer Griff,
                // sondern ein Griff in die Gaben, die seitdem hineingelegt wurden.
                continue;
            }
            int weg = leerung.leere(ort);
            if (weg == SPAETER) {
                // Jemand sieht hinein, oder die Welt ist nicht geladen. Der naechste
                // Takt versucht es wieder - und weil der Merker nicht gesetzt wird, ist
                // das in einer Minute und nicht morgen.
                spaeter = true;
                continue;
            }
            geleerteStellen.add(ort);
            geleert += weg;
        }
        if (spaeter) {
            // Die ZEILEN bleiben auch stehen: Sie sind der Tagesdeckel von gestern, und
            // wer sie loescht, waehrend noch alter Inhalt in der Truhe liegt, oeffnet
            // jedem den Deckel auf genau diesen Inhalt.
            nenne(geleert, 0, true);
            return new Ergebnis(geleert, 0, true);
        }
        int zeilen = buch.raeume();
        if (zeilen < 0) {
            // Geraeumt wurde nicht - also ist heute NICHT erledigt. Die Truhen sind zwar
            // leer, aber die Zeilen von gestern stehen noch; der naechste Takt raeumt
            // sie. Die Truhen laesst er dabei in Ruhe: Sie stehen in geleerteStellen.
            nenne(geleert, 0, true);
            return new Ergebnis(geleert, 0, true);
        }
        zuletzt = heute;
        nenne(geleert, zeilen, false);
        return new Ergebnis(geleert, zeilen, false);
    }

    /**
     * Stellt die Merker auf diesen Tag - und raeumt die von gestern weg.
     *
     * <p>Gerufen bei JEDEM Takt, der ueberhaupt etwas tun will. Was gestern geleert war,
     * ist heute wieder zu leeren; und die Zeile, die gestern schon geschrieben wurde,
     * darf heute wieder geschrieben werden.
     */
    private void neuerTag(LocalDate heute) {
        if (heute.equals(tag)) {
            return;
        }
        tag = heute;
        geleerteStellen.clear();
        spaeterGemeldet = false;
    }

    /**
     * Die Protokollzeile - nur, wenn wirklich etwas geschehen ist.
     *
     * <p>Ein Lauf, der eine leere Truhe leert, schreibt nichts: Sonst stuende in jedem
     * Protokoll jede Nacht eine Zeile mit drei Nullen, und die Zeilen, die etwas sagen,
     * gingen darin unter.
     */
    private void nenne(int geleert, int zeilen, boolean spaeter) {
        boolean getan = geleert > 0 || zeilen > 0;
        if (!getan && (!spaeter || spaeterGemeldet)) {
            // Nichts geschehen - und der "NICHT fertig"-Satz steht heute schon da. Ohne
            // diese zweite Haelfte schreibt ein blockierter Lauf bei jedem Takt dieselbe
            // Zeile, und die Zeilen, die etwas sagen, gehen darin unter.
            return;
        }
        if (spaeter) {
            spaeterGemeldet = true;
        }
        protokoll.info("Freie Truhe geleert: " + geleert + " Stueck weg, " + zeilen
                + " Tageszeilen geraeumt."
                + (spaeter ? " NICHT fertig - jemand sieht hinein, oder das Raeumen ist"
                        + " gestoert. Der naechste Takt versucht es wieder." : ""));
    }
}
