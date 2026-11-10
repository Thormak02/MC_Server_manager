package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.Truhenregel;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Truhenwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Entscheidungen der Freien Truhe - ohne Bukkit, Plan 7.2.
 *
 * <p>Dieselbe Rolle wie {@link Shopbuch} fuer die Kistenshops: Was DARF ein Spieler,
 * steht hier; WIE die Gegenstaende wandern, steht in der {@link Truhenwache}. Darum ist
 * jede Entscheidung dieses Bereichs ohne Server pruefbar.
 *
 * <p><b>Die Werte kommen vom HALTER und nicht aus einem Abbild</b>
 * ({@link Wirtschaftsstand}): Wer den Deckel in der {@code config.yml} aendert und
 * {@code /freimarken neuladen} ruft, soll ihn beim naechsten Griff spueren. Ein
 * Abbild im Feld wuerde nie wieder aufgefrischt - und genau dieser Fehler ist am
 * 10.11.2026 zweimal gefunden worden.
 *
 * <p><b>Die STELLEN kommen dagegen aus einem festen Verzeichnis</b>, das nur beim Start
 * gelesen wird. Das ist kein Versehen: Eine Stelle, die sich waehrend eines offenen
 * Fensters aendert, rechnete die Entnahme gegen den Inhalt einer anderen Kiste ab.
 *
 * <p><b>Jede Stoerung der Ablage fuehrt zur sicheren Seite, und die ist in den zwei
 * Richtungen verschieden:</b> Beim OEFFNEN wird abgewiesen - ohne den Tagesstand ist der
 * Deckel offen, und das ist bei einer Stoerung genau das, was man nicht will. Beim
 * SCHLIESSEN wird alles zurueckgelegt - sonst stuende am Ende eine leere Truhe und kein
 * Zaehler. Keine der beiden Richtungen wirft nach aussen: Eine Ausnahme im
 * Ereignisfaden waere eine Fehlermeldung im Chat und eine Kiste in unklarem Zustand.
 */
final class Truhenbuch {

    /** Ob und warum die Truhe aufgeht. */
    enum Bescheid {
        /** Sie geht auf. */
        OFFEN,
        /** Die Tagesmenge ist heraus. */
        DECKEL,
        /** Der Tagesstand liess sich nicht lesen. */
        GESTOERT
    }

    /**
     * Was beim Oeffnen gilt: der Bescheid und was heute noch geht.
     *
     * @param nochFrei wie viele Stueck heute noch gehen; {@code -1} heisst "kein Deckel"
     *                 (so nennt es {@link Truhenregel#heuteNochFrei}), und bei einem
     *                 anderen Bescheid als {@link Bescheid#OFFEN} ist die Zahl 0
     */
    record Zutritt(Bescheid bescheid, int nochFrei) {
    }

    /**
     * Was beim Schliessen gilt.
     *
     * <p><b>{@code schonVorher} steht mit in der Bilanz</b>, und das ist kein Beiwerk:
     * Was am Ende WIRKLICH beim Spieler bleibt, weiss erst der Aufrufer - zwischen dieser
     * Rechnung und dem Ende liegt der Griff in zwei Kisten, und der kann weniger bewegen,
     * als er sollte. Mit dieser Zahl rechnet {@link #nochFrei} den Rest des Tages aus der
     * WIRKLICHEN Entnahme, ohne die Ablage ein zweites Mal zu fragen.
     *
     * @param behalten    was er mitnehmen darf
     * @param zurueck     was zurueck in die Truhe gehoert
     * @param schonVorher was er heute vor diesem Griff schon behalten hatte
     * @param gestoert    ob die Ablage geschwiegen hat - dann geht alles zurueck
     */
    record Bilanz(int behalten, int zurueck, int schonVorher, boolean gestoert) {
    }

    private final Truhenverzeichnis verzeichnis;
    private final Truhenablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;
    private final Logger protokoll;

    Truhenbuch(Truhenverzeichnis verzeichnis, Truhenablage ablage,
               Wirtschaftsstand wirtschaft, Clock uhr, Logger protokoll) {
        if (verzeichnis == null || ablage == null || wirtschaft == null || uhr == null
                || protokoll == null) {
            throw new IllegalArgumentException("Verzeichnis, Ablage, Wirtschaftsstand,"
                    + " Uhr und Protokoll sind Pflicht");
        }
        this.verzeichnis = verzeichnis;
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /** Ob an dieser Stelle eine Freie Truhe steht. */
    boolean istFreieTruhe(Blockort ort) {
        return verzeichnis.istFreieTruhe(ort);
    }

    /** Ob es ueberhaupt eine gibt - sonst wird die Wache gar nicht angemeldet. */
    boolean gibtTruhen() {
        return verzeichnis.gibtTruhen();
    }

    /** Alle Stellen, in der Reihenfolge der Datei - fuer den taeglichen Lauf. */
    List<Blockort> stellen() {
        return verzeichnis.alle();
    }

    /** Die Zahlen, frisch vom Halter gefragt. */
    Truhenwerte werte() {
        return wirtschaft.werte().truhe();
    }

    /**
     * Ob dieses Material nicht in die Truhe gehoert.
     *
     * <p>Durch das Buch und nicht an ihm vorbei, obwohl es eine Zeile ist: Die Wache
     * fragt es an zwei Stellen, und zwei Wege zu derselben Antwort liefen auseinander.
     */
    boolean istVerboten(String material) {
        return werte().istVerboten(material);
    }

    /**
     * Der heutige Tag unter der eingestellten Tagesgrenze.
     *
     * <p>Die ZONE kommt aus dem {@link Wirtschaftsstand} und nicht aus der Systemzeit -
     * dieselbe Begruendung wie bei {@link Markttag}: Wann ein Tag beginnt, ist eine
     * Einstellung ({@code zeit.zone}), und ein Deckel, der nach einer anderen Zone
     * rechnet als der Tagesbonus, geht an einem Tag im Jahr um eine Stunde daneben.
     */
    LocalDate heute() {
        Zeitwerte zeit = wirtschaft.werte().zeit();
        return Tagesgrenze.tagVon(uhr.instant(), zeit.zone());
    }

    /**
     * Darf dieser Spieler die Truhe oeffnen - und wie viel geht heute noch?
     *
     * <p>Die Frage VOR dem Oeffnen, und das ist eine Entscheidung ueber das Spielgefuehl:
     * Wer am Deckel ist, soll die Truhe gar nicht aufbekommen. Sonst nimmt er, sieht es
     * im Rucksack liegen, schliesst - und bekommt alles wieder weggenommen.
     */
    Zutritt zutritt(UUID wer) {
        Truhenwerte werte = werte();
        if (!werte.gedeckelt()) {
            // DER TAGESSTAND GEHOERT DEM DECKEL und nicht der Zaehlung. Ohne Deckel
            // entscheidet die gelesene Zahl nichts: Truhenregel.abrechnen(0, schon,
            // genommen) gibt fuer JEDES schon (genommen, 0), darfNochNehmen ist immer
            // wahr, heuteNochFrei immer -1. Gefragt wird die Ablage darum nicht - sonst
            // hielte eine Stoerung der Datenbank eine Truhe zu, die keinen Deckel hat.
            //
            // Nicht zaehlt(): Eine VERBOTSLISTE braucht den Unterschied zwischen Oeffnen
            // und Schliessen, aber keinen Tagesstand [gefunden von der Lesesuche am
            // 10.11.2026 - vorher stand hier zaehlt(), und dann sperrte ein
            // Datenbankausfall die Gemeinschaftstruhe mit der Begruendung, ein
            // Tagesstand sei nicht lesbar, den diese Einstellung nicht hat].
            //
            // Die Zahl kommt aus der Regel und nicht als -1 von Hand: Was "kein Deckel"
            // heisst, soll an einer Stelle stehen.
            return new Zutritt(Bescheid.OFFEN,
                    Truhenregel.heuteNochFrei(werte.entnahmeJeTag(), 0));
        }
        int schon;
        try {
            schon = ablage.behalten(wer, heute());
        } catch (RuntimeException fehler) {
            // Die sichere Seite ist hier das Abweisen: Ohne den Tagesstand ist der
            // Deckel offen. Protokolliert mit Grund, damit der Betreiber die Stoerung
            // sieht - der Spieler liest nur, dass es gerade nicht geht.
            protokoll.log(Level.SEVERE, "Tagesstand der Freien Truhe nicht lesbar fuer "
                    + wer + " - die Truhe bleibt zu", fehler);
            return new Zutritt(Bescheid.GESTOERT, 0);
        }
        if (!Truhenregel.darfNochNehmen(werte.entnahmeJeTag(), schon)) {
            return new Zutritt(Bescheid.DECKEL, 0);
        }
        return new Zutritt(Bescheid.OFFEN,
                Truhenregel.heuteNochFrei(werte.entnahmeJeTag(), schon));
    }

    /**
     * Was von der Entnahme bleibt - und was zurueckgeht.
     *
     * <p>Rechnet nur; GESCHRIEBEN wird erst mit {@link #merke}, und zwar genau die Zahl,
     * die nach dem Zuruecklegen wirklich beim Spieler bleibt. Die Trennung ist nicht
     * Form: Zwischen Rechnung und Schreiben liegt der Griff in zwei Kisten, und der kann
     * weniger bewegen, als er sollte.
     *
     * @param genommen wie viele Stueck jetzt in der Truhe FEHLEN - 0 oder weniger heisst,
     *                 er hat nichts genommen (oder etwas hineingelegt)
     */
    Bilanz beimSchliessen(UUID wer, int genommen) {
        Truhenwerte werte = werte();
        if (!werte.gedeckelt()) {
            // OHNE DECKEL GIBT ES NICHTS ABZURECHNEN, also auch nichts zu lesen -
            // dieselbe Begruendung wie in zutritt(). Hier war der Schaden groesser: Eine
            // Stoerung gab Bilanz(0, genommen, 0, true), und die Wache nahm dem Spieler
            // die ganze Entnahme wieder aus dem Rucksack und legte sie zurueck. Dazu las
            // er "mehr als 0 am Tag nimmt niemand mit" - einen Deckel, den es nicht gibt
            // [gefunden von der Lesesuche am 10.11.2026].
            return new Bilanz(Math.max(0, genommen), 0, 0, false);
        }
        int schon;
        try {
            schon = ablage.behalten(wer, heute());
        } catch (RuntimeException fehler) {
            // Beim Schliessen ist die sichere Seite die andere: alles zurueck. Haette
            // er behalten duerfen, waere die Truhe leer und kein Zaehler gesetzt - und
            // der naechste Spieler fand eine leere Truhe und einen offenen Deckel.
            protokoll.log(Level.SEVERE, "Tagesstand der Freien Truhe nicht lesbar fuer "
                    + wer + " - die Entnahme geht zurueck", fehler);
            return new Bilanz(0, Math.max(0, genommen), 0, true);
        }
        var abrechnung = Truhenregel.abrechnen(werte.entnahmeJeTag(), schon, genommen);
        return new Bilanz(abrechnung.erlaubt(), abrechnung.zurueck(), schon, false);
    }

    /**
     * Was nach diesem Griff heute noch geht - fuer die Zeile an den Spieler.
     *
     * <p>Gerechnet aus dem Tagesstand VOR dem Griff und dem, was wirklich geblieben ist.
     * Nicht aus dem, was erlaubt WAR: Wer seine Ware vor dem Schliessen wegwirft, behaelt
     * mehr, als zurueckgelegt werden konnte - und soll dann auch den kleineren Rest
     * lesen.
     *
     * @return {@code -1}, wenn es keinen Deckel gibt - so nennt es
     *         {@link Truhenregel#heuteNochFrei}, und {@link Truhentext} macht daraus den
     *         eigenen Satz
     */
    int nochFrei(int schonVorher, int behalten) {
        return Truhenregel.heuteNochFrei(werte().entnahmeJeTag(),
                Math.max(0, schonVorher) + Math.max(0, behalten));
    }

    /**
     * Schreibt auf, was wirklich beim Spieler geblieben ist.
     *
     * <p>AUFSCHLAGEND, also darf dieser Aufruf nicht wiederholt werden - die Begruendung
     * steht an {@link Truhenablage#merke}. Eine 0 wird gar nicht geschrieben: Das waere
     * eine Zeile ohne Anlass, und die Ablage weist sie ab.
     *
     * <p>Wirft nicht. Eine verlorene Zeile kostet hoechstens einen offenen Deckel; eine
     * Ausnahme im Ereignisfaden kostet die Kiste, in der die Gegenstaende gerade liegen.
     *
     * <p><b>OHNE DECKEL wird gar nicht geschrieben</b>, so wie auch nicht gelesen wird:
     * Die Zeile beantwortet genau eine Frage ("wie viel hat er heute schon?"), und die
     * stellt nur der Deckel. Ohne ihn waere sie ein Schreiben ohne Leser, das der
     * naechtliche Lauf wieder wegraeumen muss - und die Stoerungsmeldung darunter spraeche
     * von einem Deckel, den es nicht gibt. Der Preis ist bekannt und klein: Wer einen
     * Deckel mitten am Tag einschaltet, gibt allen den Tag neu.
     */
    void merke(UUID wer, int behalten) {
        if (behalten < 1 || !werte().gedeckelt()) {
            return;
        }
        try {
            ablage.merke(wer, heute(), behalten);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Entnahme aus der Freien Truhe nicht schreibbar: "
                    + behalten + " Stueck fuer " + wer + " - die Ware ist beim Spieler,"
                    + " der Tagesdeckel steht damit zu hoch offen", fehler);
        }
    }

    /**
     * Raeumt die Zeilen von vor heute - gerufen vom taeglichen Lauf.
     *
     * @return wie viele Zeilen verschwunden sind, oder {@code -1} bei einer Stoerung
     */
    int raeume() {
        try {
            return ablage.raeume(heute());
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Alte Zeilen der Freien Truhe nicht raeumbar -"
                    + " sie stehen noch da und beantworten keine Frage mehr", fehler);
            return -1;
        }
    }
}
