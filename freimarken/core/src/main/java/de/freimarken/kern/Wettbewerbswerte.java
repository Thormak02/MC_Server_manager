package de.freimarken.kern;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Der Angelwettbewerb in Altfurt - wann er laeuft, was er kostet und was er auszahlt
 * (Plan 14).
 *
 * <p><b>DIE ERSTE UHRZEIT DIESES PROJEKTS.</b> Bis zum 22.11.2026 kannte das ganze
 * Plugin keine {@link LocalTime} und keinen {@link DayOfWeek} - es gab nur die
 * {@link Tagesgrenze} (welcher TAG ist heute) und Dauern in ganzen Sekunden. Ein Fenster
 * "von 18:00 bis 19:00" ist etwas Neues, und daraus folgen drei Dinge, die hier
 * ausdruecklich stehen:
 * <ul>
 *   <li><b>Die Zone kommt von aussen</b>, bei jeder Frage neu - aus {@code zeit.zone},
 *       derselben Einstellung, an der auch die Tagesgrenze haengt. Zwei Zonen fuer
 *       dieselbe Frage waeren genau der Fehler, gegen den {@link Tagesgrenze} gebaut
 *       ist.</li>
 *   <li><b>Ein Fenster kann ueber Mitternacht gehen</b> (23:30 plus 60 Minuten). Dann
 *       gehoert es zum VORTAG, und wer nur den heutigen Tag prueft, haelt es fuer
 *       geschlossen. {@link #laufAm} prueft deshalb beide.</li>
 *   <li><b>In der Nacht der Zeitumstellung gibt es Uhrzeiten zweimal oder nie.</b>
 *       {@code ZonedDateTime} entscheidet das selbst und verschiebt eine
 *       nichtexistierende Zeit nach vorn; das ist hier richtig und bleibt unangetastet -
 *       ein Wettbewerb, der in einer Nacht im Jahr eine Stunde spaeter beginnt, ist
 *       besser als einer, der ausfaellt.</li>
 * </ul>
 *
 * <p><b>Der Topf kommt aus den Startgeldern PLUS einem Zuschuss</b> - die Entscheidung
 * des Betreibers vom 22.11.2026. Ab Werk ist der Zuschuss 0: Dann ist der Wettbewerb eine
 * reine Umverteilung und keine Geldquelle, und die Summe, die hineingeht, ist die Summe,
 * die herauskommt. Wer gratis spielen lassen will, setzt {@code startgeld: 0} und einen
 * Zuschuss - dann ist es eine Quelle, und das soll er sehen.
 *
 * @param an                 ob der Wettbewerb ueberhaupt laeuft
 * @param beginn             zu welcher ORTSZEIT das Fenster aufgeht
 * @param dauer              wie lange es offen bleibt
 * @param tage               an welchen Wochentagen; leer heisst "an keinem"
 * @param startgeld          was die Teilnahme kostet, in Mark; 0 heisst gratis
 * @param zuschuss           was der Betreiber je Lauf dazugibt, in Mark; 0 heisst nichts
 * @param verteilungProzent  wie der Topf auf die Plaetze geht - erster Eintrag ist der
 *                           erste Platz. Die Summe darf 100 nicht ueberschreiten
 * @param punkteJeFang       Punkte fuer einen Fang, der in keiner Punktzeile steht
 * @param punkte             Gegenstandsname auf Punkte - fuer die seltenen Faenge
 */
public record Wettbewerbswerte(boolean an, LocalTime beginn, Duration dauer,
                               Set<DayOfWeek> tage, long startgeld, long zuschuss,
                               List<Integer> verteilungProzent, int punkteJeFang,
                               Map<String, Integer> punkte) {

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "wettbewerb.an";

    /** Der Schluessel der Beginnzeit. */
    public static final String PFAD_BEGINN = "wettbewerb.beginn";

    /** Der Schluessel der Dauer in Minuten. */
    public static final String PFAD_DAUER = "wettbewerb.dauer-minuten";

    /** Der Schluessel der Wochentage. */
    public static final String PFAD_TAGE = "wettbewerb.tage";

    /** Der Schluessel des Startgelds. */
    public static final String PFAD_STARTGELD = "wettbewerb.startgeld";

    /** Der Schluessel des Zuschusses. */
    public static final String PFAD_ZUSCHUSS = "wettbewerb.zuschuss";

    /** Der Schluessel der Verteilung auf die Plaetze. */
    public static final String PFAD_VERTEILUNG = "wettbewerb.verteilung-prozent";

    /** Der Schluessel der Punkte fuer einen gewoehnlichen Fang. */
    public static final String PFAD_PUNKTE_JE_FANG = "wettbewerb.punkte-je-fang";

    /** Der Schluessel der Punktetabelle. */
    public static final String PFAD_PUNKTE = "wettbewerb.punkte";

    /**
     * Die kuerzeste Dauer - eine Minute.
     *
     * <p>Darunter waere das Fenster kuerzer als der Takt der Spielzeitwache, mit dem es
     * geschlossen wird: Es ginge auf und zu, ohne dass ein Spieler es sieht.
     */
    public static final Duration KUERZESTE_DAUER = Duration.ofMinutes(1);

    /**
     * Die laengste Dauer - 24 Stunden.
     *
     * <p>Darueber ueberlappten sich zwei Laeufe desselben Wochentags, und dann waere
     * nicht zu sagen, zu welchem ein Fang gehoert.
     */
    public static final Duration LAENGSTE_DAUER = Duration.ofHours(24);

    /** Das hoechste Punktgewicht einer Zeile - gegen den Vertipper. */
    public static final int HOECHSTE_PUNKTE = 1_000_000;

    /** Der hoechste Geldbetrag - dieselbe Hausgrenze wie bei Bonus und Lotterie. */
    public static final long HOECHSTER_BETRAG = Freistadtwerte.HOECHSTER_BETRAG;

    /** Ab Werk geht das Fenster um 18:00 Ortszeit auf. */
    public static final LocalTime VORGABE_BEGINN = LocalTime.of(18, 0);

    /** Ab Werk bleibt es eine Stunde offen. */
    public static final Duration VORGABE_DAUER = Duration.ofMinutes(60);

    /**
     * Ab Werk an JEDEM Tag.
     *
     * <p>Der Betreiber hat "taeglich zur festen Uhrzeit" als Vorgabe bestimmt; welche
     * Tage es sind, bleibt frei einstellbar. Eine Vorgabe von Freitag bis Sonntag waere
     * eine Entscheidung ueber seinen Server, die er nicht getroffen hat.
     */
    public static final Set<DayOfWeek> VORGABE_TAGE =
            Set.copyOf(EnumSet.allOf(DayOfWeek.class));

    /** Was die Teilnahme ab Werk kostet. */
    public static final long VORGABE_STARTGELD = 250L;

    /**
     * Was der Betreiber ab Werk dazugibt: NICHTS.
     *
     * <p>Damit ist der Wettbewerb ab Werk eine reine Umverteilung - die Summe, die
     * hineingeht, ist die Summe, die herauskommt. Jeder Zuschuss darueber ist eine
     * Geldquelle neben Verdienst und Tagesbonus, und die soll der Betreiber bewusst
     * einstellen und nicht vorfinden.
     */
    public static final long VORGABE_ZUSCHUSS = 0L;

    /** Die ausgelieferte Verteilung: Haelfte, drei Zehntel, ein Fuenftel. */
    public static final List<Integer> VORGABE_VERTEILUNG = List.of(50, 30, 20);

    /** Ein gewoehnlicher Fang ist ab Werk einen Punkt wert. */
    public static final int VORGABE_PUNKTE_JE_FANG = 1;

    /**
     * Die ausgelieferte Punktetabelle - die seltenen Faenge von Hafenwerder.
     *
     * <p>Sie nennt dieselben Gegenstaende wie {@link Angelwerte#VORGABE_FAENGE}, aber
     * sie ist eine EIGENE Tabelle und nicht aus jener gerechnet. Zwei Gruende: Die
     * Angeltabelle darf leer sein (dann angelt Hafenwerder Vanilla, und ein Wettbewerb
     * ueber ihre Zeilen haette nichts zu werten), und Punkte sind eine andere Frage als
     * Haeufigkeit - der Betreiber soll einen haeufigen Fang hoch bewerten koennen, wenn
     * er will.
     *
     * <p>Was NICHT dasteht, ist {@link #punkteJeFang} wert. Ein Wettbewerb funktioniert
     * damit auch ohne jede Zeile hier - und auch in einer Welt, in der die Angeltabelle
     * aus ist.
     */
    public static final Map<String, Integer> VORGABE_PUNKTE = Map.ofEntries(
            Map.entry("heart_of_the_sea", 100),
            Map.entry("sponge", 25),
            Map.entry("nautilus_shell", 10),
            Map.entry("prismarine_crystals", 8),
            Map.entry("brain_coral_block", 8),
            Map.entry("horn_coral_block", 8),
            Map.entry("prismarine_shard", 5),
            Map.entry("sea_pickle", 5),
            Map.entry("tropical_fish", 3));

    /** Die Werte, die das Programm mitbringt. */
    public static final Wettbewerbswerte VORGABE = new Wettbewerbswerte(
            true, VORGABE_BEGINN, VORGABE_DAUER, VORGABE_TAGE, VORGABE_STARTGELD,
            VORGABE_ZUSCHUSS, VORGABE_VERTEILUNG, VORGABE_PUNKTE_JE_FANG, VORGABE_PUNKTE);

    public Wettbewerbswerte {
        if (beginn == null) {
            throw new IllegalArgumentException("Ohne Beginnzeit gibt es kein Fenster");
        }
        if (dauer == null || dauer.compareTo(KUERZESTE_DAUER) < 0
                || dauer.compareTo(LAENGSTE_DAUER) > 0) {
            throw new IllegalArgumentException("Die Dauer ist " + dauer + " und liegt"
                    + " nicht zwischen " + KUERZESTE_DAUER + " und " + LAENGSTE_DAUER
                    + ". Kuerzer als der Takt der Spielzeitwache ginge das Fenster auf"
                    + " und zu, ohne dass ein Spieler es sieht; laenger als ein Tag"
                    + " ueberlappten sich zwei Laeufe desselben Wochentags.");
        }
        if (tage == null) {
            throw new IllegalArgumentException("Ohne Tage gibt es kein Fenster - eine"
                    + " LEERE Menge ist erlaubt und heisst: an keinem Tag");
        }
        tage = Set.copyOf(tage);
        if (startgeld < 0L || startgeld > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Das Startgeld ist " + startgeld
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist erlaubt und heisst: die Teilnahme ist gratis.");
        }
        if (zuschuss < 0L || zuschuss > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Der Zuschuss ist " + zuschuss
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG + ".");
        }
        if (verteilungProzent == null || verteilungProzent.isEmpty()) {
            throw new IllegalArgumentException("Ohne Verteilung gibt es keine Preise -"
                    + " mindestens der erste Platz braucht seinen Anteil");
        }
        List<Integer> anteile = new ArrayList<>();
        int summe = 0;
        for (Integer anteil : verteilungProzent) {
            if (anteil == null || anteil < 1 || anteil > 100) {
                throw new IllegalArgumentException("Ein Anteil der Verteilung ist "
                        + anteil + " und liegt nicht zwischen 1 und 100 Prozent. Ein"
                        + " Anteil von 0 ist ein Platz ohne Preis - der gehoert aus der"
                        + " Liste heraus.");
            }
            summe += anteil;
            anteile.add(anteil);
        }
        if (summe > 100) {
            throw new IllegalArgumentException("Die Anteile ergeben " + summe
                    + " Prozent und damit mehr als den Topf. Weniger als 100 ist"
                    + " erlaubt - was uebrigbleibt, bleibt bei denen, die eingezahlt"
                    + " haben, und ist damit eine Senke.");
        }
        verteilungProzent = List.copyOf(anteile);
        if (punkteJeFang < 0 || punkteJeFang > HOECHSTE_PUNKTE) {
            throw new IllegalArgumentException("Die Punkte je Fang sind " + punkteJeFang
                    + " und liegen nicht zwischen 0 und " + HOECHSTE_PUNKTE + ". Eine 0"
                    + " ist erlaubt und heisst: nur die Faenge aus der Punktetabelle"
                    + " zaehlen.");
        }
        if (punkte == null) {
            throw new IllegalArgumentException("Ohne Punktetabelle gibt es keinen"
                    + " Wettbewerb - eine LEERE Tabelle ist erlaubt und heisst: jeder"
                    + " Fang zaehlt gleich");
        }
        Map<String, Integer> kopie = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> zeile : punkte.entrySet()) {
            if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                throw new IllegalArgumentException("Ein Fang ohne Namen steht in der"
                        + " Punktetabelle - der Name ist der Gegenstand.");
            }
            if (zeile.getValue() == null || zeile.getValue() < 1
                    || zeile.getValue() > HOECHSTE_PUNKTE) {
                throw new IllegalArgumentException("Die Punkte von " + zeile.getKey()
                        + " sind " + zeile.getValue() + " und liegen nicht zwischen 1 und "
                        + HOECHSTE_PUNKTE + ". Eine 0 ist eine Zeile, die nichts tut -"
                        + " was nichts zaehlen soll, gehoert aus der Tabelle heraus und"
                        + " bekommt dann punkte-je-fang.");
            }
            kopie.put(zeile.getKey(), zeile.getValue());
        }
        punkte = Map.copyOf(kopie);
    }

    /** Ob dieser Wettbewerb ueberhaupt etwas tut. */
    public boolean greift() {
        return an && !tage.isEmpty() && (punkteJeFang > 0 || !punkte.isEmpty());
    }

    /** Was ein Fang dieses Gegenstands wert ist. */
    public int punkteFuer(String gegenstand) {
        if (gegenstand == null) {
            return 0;
        }
        return punkte.getOrDefault(gegenstand, punkteJeFang);
    }

    /**
     * Der Lauf, zu dem dieser Zeitpunkt gehoert - oder leer, wenn gerade keiner laeuft.
     *
     * <p><b>Geprueft werden ZWEI Tage</b>, und das ist die tragende Zeile dieser Methode:
     * Ein Fenster, das um 23:30 aufgeht und 60 Minuten offen bleibt, reicht bis 00:30 des
     * naechsten Tages. Wer um 00:10 nur den heutigen Tag prueft, findet das Fenster
     * geschlossen - und der Wettbewerb endete um Mitternacht, ohne dass es irgendwo
     * steht.
     *
     * <p>Der TAG des Laufs ist dabei der Tag seines BEGINNS. Ein Fang um 00:10 gehoert
     * also zum Lauf des Vortags, und das ist die einzige Zuordnung, die ueber Mitternacht
     * zusammenhaelt.
     *
     * @param jetzt der Zeitpunkt
     * @param zone  die eingestellte Zone - bei jeder Frage neu zu holen, nie gemerkt
     */
    public Optional<Lauf> laufAm(java.time.Instant jetzt, ZoneId zone) {
        if (jetzt == null || zone == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt und Zone gibt es kein"
                    + " Fenster");
        }
        if (!greift()) {
            return Optional.empty();
        }
        LocalDate heute = Tagesgrenze.tagVon(jetzt, zone);
        // Der heutige Lauf, dann der von gestern - siehe den Methodenkopf.
        for (LocalDate tag : List.of(heute, heute.minusDays(1))) {
            Optional<Lauf> lauf = laufFuer(tag, zone);
            if (lauf.isPresent() && lauf.get().umfasst(jetzt)) {
                return lauf;
            }
        }
        return Optional.empty();
    }

    /**
     * Der Lauf eines bestimmten Tages - unabhaengig davon, ob er gerade laeuft.
     *
     * <p>Leer, wenn an diesem Wochentag keiner stattfindet.
     */
    public Optional<Lauf> laufFuer(LocalDate tag, ZoneId zone) {
        if (tag == null || zone == null) {
            throw new IllegalArgumentException("Ohne Tag und Zone gibt es kein Fenster");
        }
        if (!tage.contains(tag.getDayOfWeek())) {
            return Optional.empty();
        }
        // atZone und nicht atStartOfDay plus Dauer: In der Nacht der Zeitumstellung gibt
        // es Uhrzeiten zweimal oder nie, und ZonedDateTime entscheidet das selbst.
        ZonedDateTime beginnt = tag.atTime(beginn).atZone(zone);
        return Optional.of(new Lauf(tag, beginnt.toInstant(),
                beginnt.plus(dauer).toInstant()));
    }

    /** Dieselben Werte mit anderem Schalter. */
    public Wettbewerbswerte mitAn(boolean anders) {
        return new Wettbewerbswerte(anders, beginn, dauer, tage, startgeld, zuschuss,
                verteilungProzent, punkteJeFang, punkte);
    }

    /** Dieselben Werte mit anderem Fenster - fuer Proben und den Leser. */
    public Wettbewerbswerte mitFenster(LocalTime anderer, Duration andereDauer,
                                       Set<DayOfWeek> andereTage) {
        return new Wettbewerbswerte(an, anderer, andereDauer, andereTage, startgeld,
                zuschuss, verteilungProzent, punkteJeFang, punkte);
    }

    /** Dieselben Werte mit anderem Geld. */
    public Wettbewerbswerte mitGeld(long anderesStartgeld, long andererZuschuss,
                                    List<Integer> andereVerteilung) {
        return new Wettbewerbswerte(an, beginn, dauer, tage, anderesStartgeld,
                andererZuschuss, andereVerteilung, punkteJeFang, punkte);
    }

    /** Dieselben Werte mit anderer Punktetabelle. */
    public Wettbewerbswerte mitPunkten(int jeFang, Map<String, Integer> andere) {
        return new Wettbewerbswerte(an, beginn, dauer, tage, startgeld, zuschuss,
                verteilungProzent, jeFang, andere);
    }

    /**
     * Ein Lauf des Wettbewerbs - sein Tag und seine beiden Raender.
     *
     * @param tag    der Tag, an dem er BEGONNEN hat; auch fuer ein Fenster, das bis in
     *               den naechsten reicht
     * @param beginn der Augenblick, in dem das Fenster aufgeht
     * @param ende   der Augenblick, in dem es zugeht - nicht mehr dabei
     */
    public record Lauf(LocalDate tag, java.time.Instant beginn,
                       java.time.Instant ende) {

        public Lauf {
            if (tag == null || beginn == null || ende == null) {
                throw new IllegalArgumentException("Ein Lauf braucht Tag, Beginn und"
                        + " Ende");
            }
            if (!ende.isAfter(beginn)) {
                throw new IllegalArgumentException("Ein Lauf endet nach seinem Beginn,"
                        + " nicht " + ende + " nach " + beginn);
            }
        }

        /**
         * Ob dieser Zeitpunkt im Fenster liegt.
         *
         * <p>Der Beginn gehoert dazu, das Ende NICHT. Sonst gehoerte ein Fang in der
         * Umstellungssekunde zu zwei Laeufen - und bei einer Dauer von genau 24 Stunden
         * waere das jeder Tag.
         */
        public boolean umfasst(java.time.Instant augenblick) {
            return !augenblick.isBefore(beginn) && augenblick.isBefore(ende);
        }

        /** Der Schluessel dieses Laufs in der Ablage - der Tag seines Beginns. */
        public LocalDate schluessel() {
            return tag;
        }
    }
}
