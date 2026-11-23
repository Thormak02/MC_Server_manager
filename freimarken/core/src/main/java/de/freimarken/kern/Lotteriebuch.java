package de.freimarken.kern;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Lotterie in Altfurt - ein Los ziehen (Plan 14).
 *
 * <p>Reine Rechnerei ohne Bukkit: Der Rucksack kommt ueber {@link Vorrat} herein, die
 * Uhr als {@link Instant}, der Wurf als Zahl. Dadurch ist jeder Zweig ohne laufenden
 * Server durchlaufbar - auch der teuerste, die Erstattung nach einer halben Lieferung.
 *
 * <p><b>Die Reihenfolge ist tragend, und sie ist eine andere als beim Muenzhaus.</b>
 * Dort gilt Posten, Bedingung, Platz, Geld, Lieferung. Hier gilt Grenze, Ziehung, Platz,
 * <b>ZAEHLUNG</b>, Geld, Auszahlung - und die Zaehlung steht vor dem Geld, weil das erste
 * Los am Tag gratis ist:
 * <ul>
 *   <li>Wuerde erst gezahlt und dann gezaehlt, waere beim GRATIS-Los die Zaehlung der
 *       einzige Riegel. {@code abbuchen(0)} gelingt immer - faellt danach die Zaehlung
 *       aus, zieht derselbe Spieler endlos gratis. Das ist eine Geldquelle.</li>
 *   <li>So herum kostet ein Ausfall zwischen Zaehlung und Zahlung dem Spieler EIN Los.
 *       Das ist ein Verlust, und von zwei Fehlern nimmt man den, der nichts erzeugt.</li>
 * </ul>
 *
 * <p>Warum die Zahl ueberhaupt in einer eigenen Ablage steht und nicht im Buchungsbuch
 * wie beim Tagesbonus, steht in {@link Losstand} - kurz: Das gratis Los bucht 0 Mark,
 * und danach ist {@link Kasse#summeSeit} weiter 0.
 */
public final class Lotteriebuch {

    private final Kasse mark;
    private final Goldkasse gold;
    private final Losablage lose;
    private final Wirtschaftsstand wirtschaft;

    /**
     * @param mark       das Markkonto - daraus wird das Los bezahlt
     * @param gold       das Goldkonto - ein GOLDMARK-Gewinn landet dort. Eine eigene
     *                   Kasse und nicht dieselbe: {@code pruefeWaehrung} wirft sonst
     * @param lose       wie viele Lose heute schon gezogen sind; die einzige Stelle, die
     *                   das weiss
     * @param wirtschaft der GETEILTE Stand, damit {@code /freimarken neuladen} die
     *                   Lotterie erreicht - ein eigener machte das Neuladen stumm
     */
    public Lotteriebuch(Kasse mark, Goldkasse gold, Losablage lose,
                        Wirtschaftsstand wirtschaft) {
        if (mark == null || gold == null || lose == null || wirtschaft == null) {
            throw new IllegalArgumentException("Die Lotterie braucht Markkasse,"
                    + " Goldkasse, Losablage und Wirtschaftsstand");
        }
        this.mark = mark;
        this.gold = gold;
        this.lose = lose;
        this.wirtschaft = wirtschaft;
    }

    /**
     * Die GELTENDEN Werte - beim Gebrauch gefragt und nicht beim Bauen gemerkt.
     *
     * <p>Sonst erreichte {@code /freimarken neuladen} die Lotterie nicht: Sie stuende
     * dann noch mit der Gewinntabelle des Serverstarts da.
     */
    public Lotteriewerte werte() {
        return wirtschaft.werte().lotterie();
    }

    /**
     * Welcher Tag gerade gilt.
     *
     * <p>Die Zone wird beim GEBRAUCH geholt und nie in ein Feld kopiert - sonst hielte
     * die Lotterie den Stand des Serverstarts und ein Neuladen wirkte nie. Dieselbe
     * Zeile steht in {@code Wechselbuch}, {@code Verfallslauf} und {@code Besuchslauf}.
     */
    public LocalDate tagVon(Instant jetzt) {
        ZoneId zone = wirtschaft.werte().zeit().zone();
        return Tagesgrenze.tagVon(jetzt, zone);
    }

    /**
     * Was ein Los jetzt kosten wuerde und ob eines geht - ohne etwas zu tun.
     *
     * <p>Fuer die Auskunft und fuer die ABSAGE: "Du hast 300, das Los kostet 500" ist
     * eine Antwort, "zu wenig" ist keine. Der Riegel gegen ein unbezahltes Los ist
     * trotzdem {@code abbuchen} selbst und nicht diese Lesung - siehe
     * {@link #ziehe(UUID, Instant, long, Vorrat)}.
     */
    public Auskunft frage(UUID spieler, Instant jetzt) {
        if (spieler == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Spieler und Zeitpunkt gibt es keine"
                    + " Auskunft");
        }
        Lotteriewerte w = werte();
        LocalDate tag = tagVon(jetzt);
        int schonHeute = lose.lies(spieler, tag).loseHeute();
        long preis = w.preisFuerNaechstes(schonHeute);
        return new Auskunft(w.greift(), schonHeute, w.hoechsteLoseJeTag(), preis,
                w.nochEinsHeute(schonHeute), mark.guthaben(spieler));
    }

    /**
     * Ein Los ziehen.
     *
     * <p>Bei {@link Bescheid#GEZOGEN} ist gezaehlt, bezahlt und ausgezahlt. Bei
     * {@link Bescheid#AUS}, {@link Bescheid#HEUTE_GENUG}, {@link Bescheid#KEIN_PLATZ},
     * {@link Bescheid#ZU_WENIG_MARK} und {@link Bescheid#GESTOERT} ist nichts passiert,
     * mit einer benannten Ausnahme: Bei {@code ZU_WENIG_MARK} und {@code GESTOERT} KANN
     * das Los schon gezaehlt sein, wenn der Ausfall nach der Zaehlung kam. Das ist der
     * bewusste Verlust aus dem Klassenkopf.
     *
     * <p>Bei {@link Bescheid#ERSTATTET} war bezahlt und ist der Lospreis zurueck; der
     * Gewinn kam dann nicht oder nur halb an, und was angekommen ist, bleibt beim
     * Spieler. Zurueckholen hiesse nach ART nehmen, und das kann das bessere Exemplar
     * des Spielers treffen ({@link Vorrat}).
     *
     * @param wurf   0 bis {@link Lotteriewerte#gewichtsumme} minus 1 - der Zufall liegt
     *               beim Aufrufer, nie hier
     * @param vorrat der Rucksack; darf {@code null} sein, solange die Tabelle keinen
     *               Gegenstandsgewinn enthaelt
     */
    public Entscheid ziehe(UUID spieler, Instant jetzt, long wurf, Vorrat vorrat) {
        if (spieler == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Spieler und Zeitpunkt gibt es kein"
                    + " Los");
        }
        Lotteriewerte w = werte();
        if (!w.greift()) {
            return Entscheid.nein(Bescheid.AUS, 0L, 0);
        }
        LocalDate tag = tagVon(jetzt);
        int schonHeute;
        try {
            schonHeute = lose.lies(spieler, tag).loseHeute();
        } catch (RuntimeException fehler) {
            // Ohne die Zahl ist der Preis nicht zu bestimmen - und ein Los zum falschen
            // Preis ist schlimmer als keines.
            return Entscheid.nein(Bescheid.GESTOERT, 0L, 0);
        }
        if (!w.nochEinsHeute(schonHeute)) {
            return Entscheid.nein(Bescheid.HEUTE_GENUG, 0L, schonHeute);
        }
        long preis = w.preisFuerNaechstes(schonHeute);
        if (mark.guthaben(spieler) < preis) {
            // Fuer die ABSAGE, nicht als Riegel - der ist abbuchen selbst.
            return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis, schonHeute);
        }
        Optional<Lotteriewerte.Gewinn> gezogen = Losregel.ziehe(w, wurf);
        if (gezogen.isEmpty()) {
            // Ein Wurf ausserhalb der Gewichtsumme ist ein Fehler des Aufrufers. Hier
            // wird deshalb NICHTS gezaehlt und NICHTS gebucht: Ein Los ohne Gewinn waere
            // bezahlter Betrug.
            return Entscheid.nein(Bescheid.GESTOERT, preis, schonHeute);
        }
        Lotteriewerte.Gewinn gewinn = gezogen.get();

        // PLATZ VOR ALLEM ANDEREN - vor der Zaehlung und vor dem Geld. Ein voller
        // Rucksack ist der haeufigste Fehlschlag; wer ihn erst nach der Zaehlung
        // bemerkt, nimmt dem Spieler sein Los fuer nichts.
        if (gewinn.art() == Lotteriewerte.Art.GEGENSTAND) {
            if (vorrat == null) {
                return Entscheid.nein(Bescheid.GESTOERT, preis, schonHeute);
            }
            int fehlt = vorrat.fehlenderPlatz(
                    Map.of(gewinn.gegenstand(), (int) gewinn.menge()));
            if (fehlt > 0) {
                return new Entscheid(Bescheid.KEIN_PLATZ, Optional.of(gewinn), preis,
                        schonHeute, fehlt);
            }
        }

        int nunGezogen;
        try {
            nunGezogen = lose.zaehleEins(spieler, tag);
        } catch (RuntimeException fehler) {
            // Ungezaehlt wird nicht gezogen: Beim gratis Los waere das der Weg zu
            // beliebig vielen - siehe den Klassenkopf.
            return Entscheid.nein(Bescheid.GESTOERT, preis, schonHeute);
        }

        boolean bezahlt = false;
        try {
            if (!mark.abbuchen(spieler, preis, Buchungsgrund.LOS_GEZOGEN)) {
                // Der RIEGEL. Das Los ist jetzt gezaehlt und nicht bezahlt - der Spieler
                // hat eines verloren. Benannt und nicht verschwiegen: Der Bescheid sagt
                // mit nunGezogen, dass gezaehlt wurde.
                return new Entscheid(Bescheid.ZU_WENIG_MARK, Optional.of(gewinn), preis,
                        nunGezogen, 0);
            }
            bezahlt = true;
            int nichtAngekommen = zahleAus(spieler, gewinn, vorrat);
            if (nichtAngekommen > 0) {
                // Nach einer genauen Platzrechnung kann das nicht eintreten - also ist es
                // ein Programm- oder Weltfehler, und dann wird der LOSPREIS erstattet.
                // Was angekommen ist, bleibt: Zurueckholen hiesse nach ART nehmen.
                mark.gutschreiben(spieler, preis, Buchungsgrund.LOS_ERSTATTET);
                return new Entscheid(Bescheid.ERSTATTET, Optional.of(gewinn), preis,
                        nunGezogen, nichtAngekommen);
            }
            return new Entscheid(Bescheid.GEZOGEN, Optional.of(gewinn), preis,
                    nunGezogen, 0);
        } catch (RuntimeException fehler) {
            if (bezahlt) {
                // Der Wurf kann aus dem Inventar oder aus einer Kasse kommen, und in
                // beiden Faellen hat der Spieler schon gezahlt.
                mark.gutschreiben(spieler, preis, Buchungsgrund.LOS_ERSTATTET);
            }
            // ERSTATTET nur, wenn wirklich abgebucht war: Ein Wurf vor dem Abbuchen hat
            // kein Geld bewegt, und dann ist "Dein Geld ist zurueck" eine Buchung, die
            // der Spieler vergeblich sucht.
            return new Entscheid(bezahlt ? Bescheid.ERSTATTET : Bescheid.GESTOERT,
                    Optional.of(gewinn), preis, nunGezogen, 0);
        }
    }

    /**
     * Zahlt den Gewinn aus und meldet, wie viele Stueck NICHT ankamen.
     *
     * <p>Ein Schalter ohne {@code default}: Wer der {@link Lotteriewerte.Art} einen
     * vierten Wert hinzufuegt, wird vom Uebersetzer gefragt, wie er ausgezahlt wird. Eine
     * Vorgabe daneben waere ein Gewinn, der stumm verschwindet.
     */
    private int zahleAus(UUID spieler, Lotteriewerte.Gewinn gewinn, Vorrat vorrat) {
        return switch (gewinn.art()) {
            case MARK -> {
                mark.gutschreiben(spieler, gewinn.menge(),
                        Buchungsgrund.LOTTERIE_GEWINN);
                yield 0;
            }
            case GOLDMARK -> {
                gold.gutschreiben(spieler, gewinn.menge(),
                        Buchungsgrund.LOTTERIE_GOLDGEWINN);
                yield 0;
            }
            case GEGENSTAND -> {
                int stueck = (int) gewinn.menge();
                int angekommen = vorrat.lege(gewinn.gegenstand(), stueck);
                yield stueck - angekommen;
            }
        };
    }

    /** Wie ein Zug ausgegangen ist. */
    public enum Bescheid {

        /** Gezaehlt, bezahlt, ausgezahlt. */
        GEZOGEN,

        /** Die Lotterie laeuft nicht oder hat keine Gewinne. */
        AUS,

        /** Die Tagesgrenze ist erreicht. */
        HEUTE_GENUG,

        /** Der Gewinn ist ein Gegenstand, und der Rucksack ist zu voll. */
        KEIN_PLATZ,

        /**
         * Das Geld reicht nicht.
         *
         * <p>Kam die Absage von der Kasse und nicht von der Lesung, ist das Los schon
         * GEZAEHLT - {@link Entscheid#loseHeute} sagt es.
         */
        ZU_WENIG_MARK,

        /** Bezahlt, aber der Gewinn kam nicht an - der Lospreis ist zurueck. */
        ERSTATTET,

        /** Eine Ablage oder eine Kasse hat geworfen; nichts ist bezahlt. */
        GESTOERT
    }

    /**
     * Was ein Los jetzt kostet - ohne etwas zu tun.
     *
     * @param laeuft         ob die Lotterie ueberhaupt etwas tut
     * @param loseHeute      wie viele der Spieler heute schon gezogen hat
     * @param hoechsteLose   die Tagesgrenze; 0 heisst unbegrenzt
     * @param preis          was das naechste Los kostet; 0 heisst gratis
     * @param nochEines      ob heute noch eines geht
     * @param guthaben       was der Spieler hat - fuer die Absage
     */
    public record Auskunft(boolean laeuft, int loseHeute, int hoechsteLose, long preis,
                           boolean nochEines, long guthaben) {
    }

    /**
     * Wie ein Zug ausgegangen ist, mit allem, was die Meldung braucht.
     *
     * @param bescheid       der Ausgang
     * @param gewinn         was gezogen wurde - leer, wenn gar nicht gezogen wurde
     * @param preis          was das Los gekostet hat beziehungsweise gekostet haette
     * @param loseHeute      wie viele Lose der Spieler an diesem Tag nun hat. Bei einem
     *                       Fehlschlag VOR der Zaehlung ist das die alte Zahl, danach die
     *                       neue - der Unterschied ist die einzige Stelle, an der sichtbar
     *                       wird, ob ein Los verbraucht wurde. Bei {@link Bescheid#AUS}
     *                       und bei einem Lesefehler steht hier 0, weil dann gar nicht
     *                       gelesen wurde; eine erfundene Zahl waere schlimmer als eine
     *                       offensichtlich leere
     * @param fehlenderPlatz wie viele Stueck nicht in den Rucksack passten; 0 sonst
     */
    public record Entscheid(Bescheid bescheid, Optional<Lotteriewerte.Gewinn> gewinn,
                            long preis, int loseHeute, int fehlenderPlatz) {

        public Entscheid {
            if (bescheid == null) {
                throw new IllegalArgumentException("Ohne Bescheid ist nichts entschieden");
            }
            if (gewinn == null) {
                throw new IllegalArgumentException("Kein Gewinn ist Optional.empty und"
                        + " nicht null");
            }
            if (preis < 0L) {
                throw new IllegalArgumentException("Ein Los kann nicht weniger als nichts"
                        + " kosten: " + preis);
            }
            if (loseHeute < 0 || fehlenderPlatz < 0) {
                throw new IllegalArgumentException("Weder Lose noch fehlender Platz"
                        + " koennen negativ sein: " + loseHeute + " / " + fehlenderPlatz);
            }
            if (bescheid == Bescheid.GEZOGEN && gewinn.isEmpty()) {
                throw new IllegalArgumentException("Ein gezogenes Los ohne Gewinn ist"
                        + " bezahlter Betrug - die Meldung koennte nicht sagen, was der"
                        + " Spieler bekommen hat.");
            }
            if (bescheid == Bescheid.GEZOGEN && loseHeute < 1) {
                throw new IllegalArgumentException("Ein gezogenes Los muss gezaehlt sein,"
                        + " aber loseHeute ist " + loseHeute + ".");
            }
        }

        /** Ein Fehlschlag ohne Gewinn und ohne fehlenden Platz. */
        static Entscheid nein(Bescheid bescheid, long preis, int loseHeute) {
            return new Entscheid(bescheid, Optional.empty(), preis, loseHeute, 0);
        }

    }
}
