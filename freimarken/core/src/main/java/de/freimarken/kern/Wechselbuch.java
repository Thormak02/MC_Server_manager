package de.freimarken.kern;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Wechselstube: Mark in Goldmark und zurueck, mit dem Wechseldeckel (Plan 8.1).
 *
 * <p>Die erste Stelle des Projekts, die ZWEI Buecher anfasst - das Markbuch und das
 * Goldbuch. Daraus kommen die drei Entscheidungen, die hier tragen:
 *
 * <p><b>Erst abbuchen, dann gutschreiben - in BEIDEN Richtungen.</b> Es ist die
 * Reihenfolge, die kein Geld erzeugen kann, und sie ist im Projekt belegt
 * ({@code SHOP_ERSTATTET}, {@code WOLKE_ERSTATTET}): Bricht es zwischen den zwei
 * Buchungen ab, hat der Spieler gezahlt und nichts bekommen - und dann kommt sein Geld
 * mit einem EIGENEN Grund zurueck. Die andere Reihenfolge waere schlimmer: Dort entstuende
 * Geld, und zwar in einer Waehrung, die niemand nachzaehlt.
 *
 * <p><b>Eine Erstattung ist kein Wechsel.</b> {@link Buchungsgrund#WECHSEL_MARK_ZURUECK}
 * steht neben {@link Buchungsgrund#WECHSEL_MARK_HER}, obwohl beide dasselbe tun - Mark
 * gutschreiben. Der Unterschied ist der Deckel: Er zaehlt seine Tagessumme aus
 * {@code WECHSEL_MARK_HER}, und eine misslungene Hinrichtung (die gar nicht gedeckelt
 * ist) duerfte ihn nicht fuellen.
 *
 * <p><b>Der Rueckweg KUERZT nicht, er sagt ab.</b> Das ist der Unterschied zum
 * Verdienstdeckel und zum Markt: Dort wird gekuerzt, weil ein abgebauter Block sich nicht
 * zuruecknehmen laesst - der Spieler hat die Arbeit schon getan. Hier hat er nichts getan;
 * er hat eine Zahl genannt. Wer zwanzig Goldmark tauschen will und nur zwoelf darf, soll
 * das LESEN und selbst entscheiden, ob er zwoelf nimmt - und nicht hinterher merken, dass
 * acht noch bei ihm liegen.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Biombuch} und {@code Tagesbonus}: Das
 * Spiel liefert eine Kennung und eine Zahl herein, und was herauskommt, ist ein
 * {@link Entscheid}. Nebenlaeufigkeit ist kein Thema, weil Befehle im Hauptfaden laufen -
 * der Blick auf die Tagessumme und die Buchung danach liegen im selben Takt.
 */
public final class Wechselbuch {

    /** Was aus dem Wunsch wird, zu wechseln. */
    public enum Bescheid {

        /** Goldmark gekauft: Die Mark sind weg, die Goldmark sind da. */
        GEKAUFT,

        /** Goldmark zurueckgewechselt: Die Goldmark sind weg, die Mark sind da. */
        VERKAUFT,

        /** Das Markguthaben reicht fuer den Kaufpreis nicht. */
        ZU_WENIG_MARK,

        /** So viele Goldmark hat der Spieler nicht. */
        ZU_WENIG_GOLDMARK,

        /** Der Betreiber hat den Rueckweg geschlossen ({@code wechsel.tagesdeckel: 0}). */
        RUECKWEG_ZU,

        /** Heute ist nicht mehr so viel moeglich - der Wechseldeckel. */
        DECKEL_ERREICHT,

        /** Eine Kasse hat geschwiegen; was abgebucht war, ist zurueck. */
        GESTOERT
    }

    /**
     * @param anzahl       um wie viele Goldmark es ging
     * @param mark         was dabei geflossen ist (bei {@link Bescheid#GEKAUFT} und
     *                     {@link Bescheid#VERKAUFT}) oder fliessen WUERDE (bei
     *                     {@link Bescheid#ZU_WENIG_MARK} und
     *                     {@link Bescheid#DECKEL_ERREICHT})
     * @param nochMoeglich wie viele Goldmark heute noch zurueckgewechselt werden duerfen -
     *                     nur beim Rueckweg gefuellt
     * @param stand        der GOLDMARKstand nach dieser Antwort, soweit er zu haben war
     * @param markstand    der MARKstand nach dieser Antwort. Er steht hier wegen genau
     *                     einer Absage: {@link Bescheid#ZU_WENIG_MARK} muss sagen, was
     *                     FEHLT, und das ist der Preis minus diesem Stand. Ihn in der
     *                     Textschicht noch einmal zu holen waere ein zweiter Blick in
     *                     einem anderen Augenblick - und dann nennt die Absage eine
     *                     Luecke, die es beim Rechnen nicht gab
     */
    public record Entscheid(Bescheid bescheid, long anzahl, long mark, long nochMoeglich,
                            long stand, long markstand) {

        /**
         * Was dem Spieler an Mark fehlt - nur bei {@link Bescheid#ZU_WENIG_MARK}
         * sinnvoll.
         *
         * <p>Hier gerechnet und nicht in der Textschicht: Dieselbe Rechnung an jeder
         * Stelle, die sie anzeigt, waere dieselbe Rechnung mehrmals - und eine davon
         * nimmt eines Tages den Erloes statt des Preises.
         */
        public long fehlt() {
            return Math.max(0L, mark - markstand);
        }

        public boolean geflossen() {
            return bescheid == Bescheid.GEKAUFT || bescheid == Bescheid.VERKAUFT;
        }
    }

    /**
     * Was die Tafel der Wechselstube zeigt - und was Plan 2.1 "Wechseldeckel sichtbar"
     * nennt.
     *
     * @param werte        die Kurse und der Deckel, wie sie JETZT gelten
     * @param goldmark     was der Spieler hat
     * @param heuteSchon   wie viele Mark er heute schon zurueckgewechselt hat
     * @param nochMoeglich wie viele Goldmark heute noch gehen
     */
    public record Auskunft(Wechselwerte werte, long goldmark, long heuteSchon,
                           long nochMoeglich) {
    }

    private final Kasse mark;
    private final Goldkasse gold;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;

    /**
     * @param mark       das Markkonto des Spielers - dieselbe Kasse, die auch {@code /mark}
     *                   zeigt
     * @param gold       das Goldmarkkonto. Der eigene TYP ist hier der Riegel: Mit zwei
     *                   Feldern vom Typ {@link Kasse} saehen die vier Buchungen dieser
     *                   Klasse gleich aus, und eine Verwechslung buchte Mark in die
     *                   Goldmark
     * @param wirtschaft woher Kurse, Deckel und Zone kommen - beim GEBRAUCH gefragt und
     *                   nicht in ein Feld kopiert, damit ein Neuladen sie erreicht
     * @param uhr        die Wanduhr, fuer die Tagesgrenze des Deckels
     */
    public Wechselbuch(Kasse mark, Goldkasse gold, Wirtschaftsstand wirtschaft, Clock uhr) {
        if (mark == null || gold == null || wirtschaft == null || uhr == null) {
            throw new IllegalArgumentException("Ohne Markkasse, Goldkasse,"
                    + " Wirtschaftsstand und Uhr gibt es keine Wechselstube");
        }
        this.mark = mark;
        this.gold = gold;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
    }

    /** Was JETZT gilt. Beim Gebrauch zu fragen, nicht zu merken. */
    public Wechselwerte werte() {
        return wirtschaft.werte().wechsel();
    }

    /**
     * Kauft Goldmark fuer Mark - der Hinweg, ungedeckelt.
     *
     * <p>Ungedeckelt, weil er Mark VERNICHTET: Je mehr hier gewechselt wird, desto weniger
     * Mark sind im Umlauf. Gedeckelt ist nur der Rueckweg.
     *
     * @param anzahl wie viele Goldmark - mindestens 1, hoechstens
     *               {@link Wechselwerte#HOECHSTE_ANZAHL}
     * @throws IllegalArgumentException bei einer Anzahl ausserhalb dieser Grenzen. Ein
     *                                  Wurf und keine Absage: Der Befehl begrenzt sein
     *                                  Argument schon im Befehlsbaum, also ist alles
     *                                  andere ein Fehler im Programm und keine Eingabe
     */
    public Entscheid kaufe(UUID spieler, long anzahl) {
        pruefe(spieler);
        Wechselwerte werte = werte();
        long preis = werte.kaufpreis(anzahl);
        try {
            if (!markAb(spieler, preis, Buchungsgrund.WECHSEL_MARK_WEG)) {
                return new Entscheid(Bescheid.ZU_WENIG_MARK, anzahl, preis, 0L,
                        gold.guthaben(spieler), mark.guthaben(spieler));
            }
            try {
                gold.gutschreiben(spieler, anzahl, Buchungsgrund.WECHSEL_GOLDMARK_HER);
            } catch (RuntimeException fehler) {
                // Die Mark sind weg und die Goldmark nicht da - also kommt der Preis
                // zurueck, mit EIGENEM Grund: Diese Zeile darf den Wechseldeckel des
                // Rueckwegs nicht fuellen, denn der Hinweg hat keinen.
                markZu(spieler, preis, Buchungsgrund.WECHSEL_MARK_ZURUECK);
                return new Entscheid(Bescheid.GESTOERT, anzahl, 0L, 0L, 0L, 0L);
            }
            return new Entscheid(Bescheid.GEKAUFT, anzahl, preis, 0L,
                    gold.guthaben(spieler), mark.guthaben(spieler));
        } catch (RuntimeException fehler) {
            return new Entscheid(Bescheid.GESTOERT, anzahl, 0L, 0L, 0L, 0L);
        }
    }

    /**
     * Wechselt Goldmark zurueck in Mark - der Rueckweg, gedeckelt.
     *
     * <p>Die Reihenfolge ist die KOSTENREIHENFOLGE: erst rechnen, dann den Deckel fragen,
     * zuletzt Geld anfassen. Der Deckel wird VOR dem Abbuchen gefragt - andersherum waere
     * die Goldmark schon weg, und die Absage muesste sie zurueckgeben.
     *
     * @see #kaufe fuer die Begruendung des Wurfs bei einer unmoeglichen Anzahl
     */
    public Entscheid verkaufe(UUID spieler, long anzahl) {
        pruefe(spieler);
        Wechselwerte werte = werte();
        long erloes = werte.erloes(anzahl);
        if (!werte.rueckwegOffen()) {
            return new Entscheid(Bescheid.RUECKWEG_ZU, anzahl, erloes, 0L,
                    0L, 0L);
        }
        try {
            long heuteSchon = heuteSchon(spieler);
            if (erloes > werte.freiHeute(heuteSchon)) {
                return new Entscheid(Bescheid.DECKEL_ERREICHT, anzahl, erloes,
                        werte.nochMoeglichHeute(heuteSchon), gold.guthaben(spieler),
                        mark.guthaben(spieler));
            }
            if (!gold.abbuchen(spieler, anzahl, Buchungsgrund.WECHSEL_GOLDMARK_WEG)) {
                return new Entscheid(Bescheid.ZU_WENIG_GOLDMARK, anzahl, erloes,
                        werte.nochMoeglichHeute(heuteSchon), gold.guthaben(spieler),
                        mark.guthaben(spieler));
            }
            try {
                markZu(spieler, erloes, Buchungsgrund.WECHSEL_MARK_HER);
            } catch (RuntimeException fehler) {
                // Die Goldmark sind weg und die Mark nicht da - sie kommen zurueck, mit
                // eigenem Grund. Zwei Zeilen im Goldbuch sind besser als ein Spieler, der
                // fuer nichts bezahlt hat.
                gold.gutschreiben(spieler, anzahl,
                        Buchungsgrund.WECHSEL_GOLDMARK_ZURUECK);
                return new Entscheid(Bescheid.GESTOERT, anzahl, 0L, 0L, 0L, 0L);
            }
            return new Entscheid(Bescheid.VERKAUFT, anzahl, erloes,
                    werte.nochMoeglichHeute(heuteSchon + erloes), gold.guthaben(spieler),
                    mark.guthaben(spieler));
        } catch (RuntimeException fehler) {
            return new Entscheid(Bescheid.GESTOERT, anzahl, 0L, 0L, 0L, 0L);
        }
    }

    /**
     * Die Tafel der Wechselstube - Stand, Kurse und was heute noch geht.
     *
     * @return leer, wenn eine Kasse geschwiegen hat. Dann zeigt der Befehl eine Stoerung
     *         und keine halbe Tafel: Eine Tafel mit "noch 12 moeglich" aus einem
     *         misslungenen Blick waere eine Zusage, die der naechste Tausch bricht
     */
    public Optional<Auskunft> auskunft(UUID spieler) {
        pruefe(spieler);
        Wechselwerte werte = werte();
        try {
            long heuteSchon = werte.rueckwegOffen() ? heuteSchon(spieler) : 0L;
            return Optional.of(new Auskunft(werte, gold.guthaben(spieler), heuteSchon,
                    werte.nochMoeglichHeute(heuteSchon)));
        } catch (RuntimeException fehler) {
            return Optional.empty();
        }
    }

    /**
     * Abbuchen im MARKBUCH - durch den Riegel.
     *
     * <p>Die Gegenseite zu dem Riegel, den {@link Goldkasse} fuer das Goldbuch haelt.
     * Diese Klasse ist die einzige, die BEIDE Buecher anfasst, und damit die einzige
     * Stelle, an der eine Zeile im falschen Buch landen kann: Vier Aufrufe, zwei Kassen,
     * und die Gruende unterscheiden sich nur in einem Wort. Ein {@code WECHSEL_GOLDMARK_*}
     * im Markbuch waere eine Zeile, die richtig aussieht und von der falschen Waehrung
     * erzaehlt.
     */
    private boolean markAb(UUID spieler, long betrag, Buchungsgrund grund) {
        pruefeMark(grund);
        return mark.abbuchen(spieler, betrag, grund);
    }

    /** Gutschreiben im MARKBUCH - siehe {@link #markAb}. */
    private void markZu(UUID spieler, long betrag, Buchungsgrund grund) {
        pruefeMark(grund);
        mark.gutschreiben(spieler, betrag, grund);
    }

    private static void pruefeMark(Buchungsgrund grund) {
        if (!grund.gehoertIn(Buchungsgrund.Buch.MARK)) {
            throw new IllegalArgumentException("Der Grund " + grund + " gehoert in das "
                    + grund.buch() + "-Buch und nicht in das Markbuch.");
        }
    }

    /**
     * Was heute schon zurueckgewechselt wurde - aus dem BUCHUNGSBUCH.
     *
     * <p>Dieselbe Bauart wie beim Tagesbonus, bei der Ladenmiete und bei den zwei anderen
     * Tagesdeckeln: Das Buch ist die Wahrheit ueber Geld, und eine zweite Tabelle daneben
     * waere eine zweite Wahrheit, die nach dem ersten Absturz von der ersten abweicht.
     *
     * <p>Gezaehlt wird im MARKBUCH und nicht im Goldbuch, denn der Deckel ist in Mark
     * bemessen (Plan 8.1: "hoechstens 10.000 Mark/Tag"). Waere er in Goldmark gezaehlt,
     * verschoebe jede Kursaenderung ihn stumm mit.
     */
    private long heuteSchon(UUID spieler) {
        ZoneId zone = wirtschaft.werte().zeit().zone();
        LocalDate heute = Tagesgrenze.tagVon(uhr.instant(), zone);
        Instant ab = Tagesgrenze.tagesbeginn(heute, zone);
        return mark.summeSeit(spieler, ab, Buchungsgrund.WECHSEL_MARK_HER);
    }

    private static void pruefe(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keinen Wechsel");
        }
    }
}
