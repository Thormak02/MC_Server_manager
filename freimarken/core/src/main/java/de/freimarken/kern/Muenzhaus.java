package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Das Muenzhaus - Kontostand, Verlauf und der Katalog in Goldmark (Plan 2.1).
 *
 * <p><b>Der Kauf hat ZWEI Stufen</b>, wie {@code /beruf verlassen} und
 * {@code /freistadt gruenden}: {@link #pruefe} sagt, ob es gehen wuerde, und
 * {@link #kaufe} tut es. Der Posten wird getippt, er kostet Goldmark, und verkauft wird
 * hier nichts - wer bei vier aehnlich klingenden Namen den falschen vervollstaendigt,
 * soll nicht schon bezahlt haben.
 *
 * <p><b>Die Reihenfolge des Kaufs ist die Sache selbst</b>, und sie ist die des
 * Kistenshops ({@code Shophandel}: "Erst der Vorrat, dann der Platz, dann das Geld. Eine
 * Absage muss sagen, was fehlt."): Posten, Bedingung, PLATZ, Geld, Lieferung. Ein voller
 * Rucksack sagt damit ab, BEVOR sich etwas bewegt. Andersherum gebaut - zuerst zahlen -
 * stuenden in dem haeufigsten Fehlschlag, den es hier gibt, zwei Zeilen im Goldbuch fuer
 * einen Kauf, der nie stattgefunden hat: ausgerechnet in dem Verlauf, den dieser
 * Bauschritt baut.
 *
 * <p><b>Dieses Haus nimmt NICHTS zurueck.</b> Die Naht zum Rucksack ({@link Vorrat})
 * vergleicht nur die ART eines Gegenstandes - eine verzauberte Spitzhacke und eine blanke
 * sind fuer sie dasselbe, und {@code Vorratshand} sagt selbst, dass diese Milde fuer
 * Handel nicht gilt. Ein Rueckweg per {@code nimm} koennte also dem Spieler sein
 * verzaubertes Schwert nehmen und ihm das gelieferte blanke lassen, und keine Probe saehe
 * es: Eine Attrappe des Vorrats rechnet mit Zahlen und kennt keine Gestalt. Darum wird
 * vorher nach dem Platz gefragt, und wenn danach trotzdem etwas schiefgeht, verliert der
 * SERVER ein paar Blumen und nicht der Spieler sein Schwert.
 *
 * <p><b>Ohne Bukkit:</b> Was ein Spieler traegt, kommt als Zahlen durch den
 * {@link Vorrat} herein; was er geleistet hat, als Karte durch die
 * {@link Bedingungsmessung}. Damit ist jeder Zweig ohne laufenden Server durchlaufbar,
 * auch der teuerste - die Lieferung, die zur Haelfte ankommt.
 *
 * <p><b>Nebenlaeufigkeit ist kein Thema</b>, wie bei {@link Wechselbuch}: Befehle laufen
 * im Hauptfaden. Einen Wettlaufriegel wie bei den Herausforderungen braucht der Kauf
 * ausserdem nicht - es gibt nichts Einmaliges zu schuetzen, jeder Posten ist beliebig oft
 * zu kaufen.
 *
 * <p><b>Der Wechseldeckel steht hier nicht.</b> Er gehoert der Wechselstube
 * ({@link Wechselwerte}, {@link Wechselbuch}), und die Tafel des Muenzhauses ruft ihn
 * dort ab, statt ihn ein zweites Mal zu rechnen.
 */
public final class Muenzhaus {

    // Die Bedingungsmessung stand bis zum 20.11.2026 HIER, als verschachtelte
    // Schnittstelle. Mit /rang bekam sie den zweiten Leser, und eine Naht, die zwei
    // Klassen brauchen, gehoert keiner von beiden - sie steht jetzt fuer sich
    // (Bedingungsmessung). Der Name im Konstruktor ist derselbe geblieben.

    /** Was aus einem Kauf wird. */
    public enum Bescheid {

        /** Gekauft: Das Geld ist weg, die Gegenstaende sind da. */
        GEKAUFT,

        /**
         * Alles stimmt - es fehlt nur die Bestaetigung.
         *
         * <p>Die Antwort von {@link Muenzhaus#pruefe}, und sie ist der Grund fuer diese
         * zweite Stufe: Der Posten wird GETIPPT, er kostet Goldmark, und verkauft wird
         * hier nichts. Dieselbe Sicherung wie bei {@code /beruf verlassen} und
         * {@code /freistadt gruenden}; dort steht auch die Begruendung, warum eine Tat
         * ohne Preis sie NICHT bekommt.
         *
         * <p>Aus {@link Muenzhaus#kaufe} kommt dieser Bescheid nie - dort ist er der
         * Durchgang zum Buchen.
         */
        BEREIT,

        /** Es gibt kein Muenzhaus - der Schalter ist aus oder der Katalog ist leer. */
        AUS,

        /** Diesen Posten kennt der Katalog nicht. */
        UNBEKANNT,

        /** Eine Bedingung ist nicht erfuellt - welche, steht im Entscheid. */
        BEDINGUNG_FEHLT,

        /** Nicht genug Goldmark. Nichts ist passiert. */
        ZU_WENIG_GOLDMARK,

        /**
         * Im Rucksack ist zu wenig Platz - gefragt VOR der Zahlung.
         *
         * <p>Darum ist auch hier nichts passiert: kein Geld bewegt, kein Gegenstand
         * geliefert, keine Zeile im Buch.
         */
        KEIN_PLATZ,

        /**
         * Kasse oder Messung haben geschwiegen - und es ist NICHTS passiert.
         *
         * <p>Dieser Bescheid kommt aus {@link #pruefe}, also von VOR der Zahlung, und er
         * ist auch die Antwort auf einen misslungenen Blick in Tafel oder Verlauf. Dass
         * er und {@link #ERSTATTET} zwei Faelle sind, ist am 20.11.2026 in der Pruefrunde
         * aufgefallen: Mit einem einzigen Bescheid las ein Spieler "Dein Geld ist
         * zurueck", wenn er nur die Tafel aufgeschlagen hatte.
         */
        GESTOERT,

        /**
         * Es war abgebucht, und das Geld ist zurueck.
         *
         * <p>Dieselbe Zusage wie bei {@code Wechselbuch.GESTOERT}, und hier mit einer
         * Einschraenkung, die ausgesprochen gehoert: Was von der Lieferung schon
         * angekommen war, bleibt beim Spieler. Zurueckgeholt wird es nicht - siehe den
         * Klassenkopf.
         */
        ERSTATTET
    }

    /**
     * Das Ergebnis eines Kaufs.
     *
     * @param bescheid       was daraus wurde
     * @param posten         welcher Posten gemeint war, falls es ihn gibt
     * @param preis          was er kostet, in Goldmark
     * @param stand          der Goldmarkstand - bei {@link Bescheid#GEKAUFT} der DANACH,
     *                       sonst der unveraenderte
     * @param fehlend        welche Bedingungen offen sind; leer, wenn keine fehlt
     * @param fehlenderPlatz wie viele Stueck nicht hineinpassen; 0, wenn alles passt
     */
    public record Entscheid(Bescheid bescheid, Optional<Muenzwerte.Posten> posten,
                            long preis, long stand, List<Rangregel.Bedingungsstand> fehlend,
                            int fehlenderPlatz) {

        public Entscheid {
            fehlend = List.copyOf(fehlend);
        }

        /** Wie viel Goldmark noch fehlt - nie negativ. */
        public long fehlt() {
            return Math.max(0L, preis - stand);
        }

        public boolean gekauft() {
            return bescheid == Bescheid.GEKAUFT;
        }

        /** Eine Absage ohne Posten und ohne Zahlen. */
        static Entscheid nein(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), 0L, 0L, List.of(), 0);
        }

        /** Eine Absage, die ihren Posten und den unveraenderten Stand mitnimmt. */
        static Entscheid nein(Bescheid bescheid, Muenzwerte.Posten posten, long stand) {
            return new Entscheid(bescheid, Optional.of(posten), posten.preis(), stand,
                    List.of(), 0);
        }
    }

    /**
     * Eine Zeile der Tafel: der Posten und was ihm aus Sicht DIESES Spielers entgegensteht.
     *
     * @param posten    der Posten selbst
     * @param fehlend   welche Bedingungen ihm fehlen; leer heisst: offen fuer ihn
     * @param bezahlbar ob sein Goldmarkstand reicht
     */
    public record Zeile(Muenzwerte.Posten posten,
                        List<Rangregel.Bedingungsstand> fehlend,
                        boolean bezahlbar) {

        public Zeile {
            fehlend = List.copyOf(fehlend);
        }

        /** Ob er diesen Posten nehmen darf - Geld ist eine andere Frage. */
        public boolean erfuellt() {
            return fehlend.isEmpty();
        }

        /** Ob er ihn jetzt sofort kaufen koennte. */
        public boolean kaufbar() {
            return erfuellt() && bezahlbar;
        }
    }

    /**
     * Die Tafel des Muenzhauses - Stand und Katalog in einem Blick.
     *
     * @param werte    die GELTENDEN Werte; aus ihnen kommt, ob ueberhaupt verkauft wird
     * @param goldmark der Goldmarkstand des Spielers
     * @param zeilen   je Posten eine Zeile, in der Reihenfolge der Datei
     */
    public record Tafel(Muenzwerte werte, long goldmark, List<Zeile> zeilen) {

        public Tafel {
            zeilen = List.copyOf(zeilen);
        }
    }

    /**
     * Ein Blick ins Goldbuch: der Stand und die letzten Buchungen.
     *
     * <p>Beides in EINER Auskunft, weil beides zusammen angezeigt wird - und weil zwei
     * Abfragen zwei Augenblicke sind: Der Kopf nennte sonst einen Stand, zu dem die
     * Zeilen darunter nicht passen.
     */
    public record Konto(long goldmark, List<Buchung> buchungen) {

        public Konto {
            buchungen = List.copyOf(buchungen);
        }
    }

    private final Goldkasse gold;
    private final Wirtschaftsstand wirtschaft;
    private final Bedingungsmessung messung;

    /**
     * @param gold       die Goldkasse - dieselbe, auf der die Wechselstube steht. Zwei
     *                   waeren zwei Konten in derselben Waehrung
     * @param wirtschaft der GETEILTE Stand, damit {@code /freimarken neuladen} den
     *                   Katalog erreicht - ein eigener machte das Neuladen stumm
     * @param messung    woran eine Bedingung gemessen wird; siehe
     *                   {@link Bedingungsmessung}
     */
    public Muenzhaus(Goldkasse gold, Wirtschaftsstand wirtschaft,
                     Bedingungsmessung messung) {
        if (gold == null || wirtschaft == null || messung == null) {
            throw new IllegalArgumentException("Das Muenzhaus braucht Goldkasse,"
                    + " Wirtschaftsstand und Bedingungsmessung");
        }
        this.gold = gold;
        this.wirtschaft = wirtschaft;
        this.messung = messung;
    }

    /**
     * Die GELTENDEN Werte - beim Gebrauch gefragt und nicht beim Bauen gemerkt.
     *
     * <p>Sonst erreichte {@code /freimarken neuladen} den Katalog nicht: Das Haus stuende
     * dann noch mit den Preisen des Starts da.
     */
    public Muenzwerte werte() {
        return wirtschaft.werte().muenzhaus();
    }

    /**
     * Die Tafel fuer diesen Spieler - leer, wenn Kasse oder Messung geschwiegen haben.
     *
     * <p>Ein leeres {@link Optional} und keine halbe Tafel: Eine Zeile "du hast 12
     * Goldmark" aus einem misslungenen Blick waere eine Zusage, die der naechste Kauf
     * bricht.
     */
    public Optional<Tafel> tafel(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Eine Tafel ohne Spieler");
        }
        try {
            Muenzwerte werte = werte();
            long stand = gold.guthaben(spieler);
            Map<Bedingungsart, Long> gemessen = messung.fuer(spieler, werte.gebraucht());
            List<Zeile> zeilen = new ArrayList<>();
            for (Muenzwerte.Posten posten : werte.katalog()) {
                zeilen.add(new Zeile(posten,
                        Rangregel.fehlend(posten.bedingungen(), gemessen),
                        stand >= posten.preis()));
            }
            return Optional.of(new Tafel(werte, stand, zeilen));
        } catch (RuntimeException fehler) {
            return Optional.empty();
        }
    }

    /**
     * Der Blick ins Goldbuch - leer, wenn die Kasse geschwiegen hat.
     *
     * <p>Durchgereicht und nicht die Kasse nach aussen gegeben, wie
     * {@code Freistadtbuch.kassenverlauf}: Die Goldkasse ist die einzige Tuer zum Goldbuch
     * und traegt den Riegel gegen eine Zeile im falschen Buch. Zwei Tueren dorthin waeren
     * eine zu viel.
     */
    public Optional<Konto> konto(UUID spieler, int hoechstens) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ein Konto ohne Spieler");
        }
        try {
            return Optional.of(new Konto(gold.guthaben(spieler),
                    gold.verlauf(spieler, hoechstens)));
        } catch (RuntimeException fehler) {
            return Optional.empty();
        }
    }

    /**
     * Einen Posten kaufen - Posten, Bedingung, Platz, Geld, Lieferung.
     *
     * <p>Bei {@link Bescheid#GEKAUFT} ist das Geld weg und alles angekommen. Bei
     * {@link Bescheid#AUS}, {@link Bescheid#UNBEKANNT},
     * {@link Bescheid#BEDINGUNG_FEHLT}, {@link Bescheid#KEIN_PLATZ},
     * {@link Bescheid#ZU_WENIG_GOLDMARK} und {@link Bescheid#GESTOERT} ist NICHTS
     * passiert - kein Geld, kein Gegenstand, keine Zeile. Nur bei
     * {@link Bescheid#ERSTATTET} war abgebucht und ist das Geld zurueck; was von der
     * Lieferung schon angekommen war, bleibt beim Spieler.
     *
     * <p><b>Der Stand wird VOR dem Abbuchen gelesen</b>, und das ist keine zweite
     * Pruefung: Der Riegel ist {@code abbuchen} selbst, es bucht nur bei Deckung. Gelesen
     * wird fuer die ABSAGE - "du hast 3, es kostet 5" ist eine Antwort, "zu wenig" ist
     * keine.
     */
    public Entscheid kaufe(UUID spieler, String schluessel, Vorrat vorrat) {
        Entscheid gepruefft = pruefe(spieler, schluessel, vorrat);
        if (gepruefft.bescheid() != Bescheid.BEREIT) {
            return gepruefft;
        }
        Muenzwerte.Posten posten = gepruefft.posten().orElseThrow();
        long stand = gepruefft.stand();
        boolean bezahlt = false;
        try {
            if (!gold.abbuchen(spieler, posten.preis(), Buchungsgrund.MUENZHAUS_KAUF)) {
                // Der RIEGEL - und nicht die Lesung in pruefe. Sie ist fuer die ABSAGE
                // da ("du hast 3, es kostet 5"); hier entscheidet die Kasse, und zwar in
                // einer Transaktion mit ihrer Buchung. Zwischen beiden liegt im
                // Hauptfaden nichts, aber eine zweite Quelle fuer dieselbe Frage waere
                // eine Luecke, auf die sich niemand verlassen soll.
                return Entscheid.nein(Bescheid.ZU_WENIG_GOLDMARK, posten, stand);
            }
            bezahlt = true;
            int nichtAngekommen = liefere(posten, vorrat);
            if (nichtAngekommen > 0) {
                // Nach einer genauen Platzrechnung kann das nicht eintreten - also ist es
                // ein Programm- oder Weltfehler, und dann wird erstattet. Was angekommen
                // ist, bleibt: Zurueckholen hiesse nach ART nehmen, und das kann das
                // bessere Exemplar des Spielers treffen.
                gold.gutschreiben(spieler, posten.preis(),
                        Buchungsgrund.MUENZHAUS_ERSTATTET);
                return new Entscheid(Bescheid.ERSTATTET, Optional.of(posten),
                        posten.preis(), stand, List.of(), nichtAngekommen);
            }
            return new Entscheid(Bescheid.GEKAUFT, Optional.of(posten), posten.preis(),
                    stand - posten.preis(), List.of(), 0);
        } catch (RuntimeException fehler) {
            if (bezahlt) {
                // Der Wurf kann aus dem Inventar oder aus der Kasse kommen, und in beiden
                // Faellen hat der Spieler schon gezahlt. Goldmark hat genau zwei Quellen
                // (Plan 8.1); ohne diese Zeile verschwindet sie endgueltig.
                gold.gutschreiben(spieler, posten.preis(),
                        Buchungsgrund.MUENZHAUS_ERSTATTET);
            }
            // ERSTATTET nur, wenn wirklich abgebucht war: Ein Wurf aus der Lesung des
            // Standes hat kein Geld bewegt, und dann ist "Dein Geld ist zurueck" eine
            // Buchung, die der Spieler vergeblich sucht.
            return Entscheid.nein(bezahlt ? Bescheid.ERSTATTET : Bescheid.GESTOERT,
                    posten, stand);
        }
    }

    /**
     * Alles pruefen, nichts tun - die erste Stufe des Kaufs.
     *
     * <p>Antwortet {@link Bescheid#BEREIT}, wenn der Kauf gelingen wuerde; sonst mit genau
     * der Absage, die auch {@link #kaufe} geben wuerde. Gebraucht wird sie fuer die
     * RUECKFRAGE: Der Spieler soll Name, Preis und Inhalt lesen, bevor er bestaetigt - und
     * er soll nicht nach einer Bestaetigung erfahren, dass ihm eine Bedingung fehlt.
     *
     * <p><b>Die Reihenfolge ist die von {@code Shophandel.warumNicht}</b>: erst was der
     * Katalog hergibt, dann die Bedingung, dann der PLATZ, dann das Geld. "Eine Absage
     * muss sagen, was fehlt" - und sie muss es sagen, bevor sich etwas bewegt.
     */
    public Entscheid pruefe(UUID spieler, String schluessel, Vorrat vorrat) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ein Kauf ohne Spieler");
        }
        if (vorrat == null) {
            throw new IllegalArgumentException("Ohne Vorrat gibt es nichts zu liefern");
        }
        Muenzwerte werte = werte();
        if (!werte.greift()) {
            return Entscheid.nein(Bescheid.AUS);
        }
        Optional<Muenzwerte.Posten> gesucht = werte.posten(schluessel);
        if (gesucht.isEmpty()) {
            // Der Tippfehler landet hier und nicht bei Brigadier: Das Argument ist ein
            // freies Wort, damit dieser Zweig ihn ueberhaupt erreicht und mit einem
            // deutschen Satz antworten kann.
            return Entscheid.nein(Bescheid.UNBEKANNT);
        }
        Muenzwerte.Posten posten = gesucht.get();
        try {
            Map<Bedingungsart, Long> gemessen = messung.fuer(spieler, werte.gebraucht());
            List<Rangregel.Bedingungsstand> fehlend =
                    Rangregel.fehlend(posten.bedingungen(), gemessen);
            long stand = gold.guthaben(spieler);
            if (!fehlend.isEmpty()) {
                return new Entscheid(Bescheid.BEDINGUNG_FEHLT, gesucht, posten.preis(),
                        stand, fehlend, 0);
            }
            int fehlenderPlatz = vorrat.fehlenderPlatz(posten.gegenstaende());
            if (fehlenderPlatz > 0) {
                return new Entscheid(Bescheid.KEIN_PLATZ, gesucht, posten.preis(), stand,
                        List.of(), fehlenderPlatz);
            }
            if (stand < posten.preis()) {
                return Entscheid.nein(Bescheid.ZU_WENIG_GOLDMARK, posten, stand);
            }
            return Entscheid.nein(Bescheid.BEREIT, posten, stand);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, posten, 0L);
        }
    }

    /** Legt alles hinein und meldet, wie viele Stueck NICHT angekommen sind. */
    private static int liefere(Muenzwerte.Posten posten, Vorrat vorrat) {
        int fehlt = 0;
        for (var zeile : posten.gegenstaende().entrySet()) {
            int angekommen = vorrat.lege(zeile.getKey(), zeile.getValue());
            fehlt += Math.max(0, zeile.getValue() - angekommen);
        }
        return fehlt;
    }
}
