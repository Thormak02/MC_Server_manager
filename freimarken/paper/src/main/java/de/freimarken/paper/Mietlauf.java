package de.freimarken.paper;

import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietregel;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Mietwerte;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Der taegliche Lauf der Ladenmiete - abbuchen, warnen, raeumen.
 *
 * <p>Plan 7.1 in einem Satz: "Miete wird taeglich abgebucht. Bei Zahlungsausfall: Warnung,
 * dann Rueckgabe. Der Kisteninhalt wandert in ein Abholfach und wird NIE geloescht."
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Mietbuch} und {@link Tagesbonus}. Das
 * Spiel kommt nur ueber {@link Truhe} herein - eine Schnittstelle mit zwei Methoden, weil
 * das Lesen und das Leeren einer Kiste in verschiedenen Augenblicken passieren muessen
 * (siehe die Reihenfolge der Raeumung unten).
 *
 * <p><b>Gezahlt wird unabhaengig davon, ob der Mieter da ist.</b> Das ist der ganze Zweck:
 * Waere die Abbuchung an das Einloggen gebunden, waere Wegbleiben der Weg, einen Stand
 * kostenlos zu halten - und die Halle stuende voll mit Laeden, die niemand bezahlt. Der
 * Lauf faehrt darum im Spielzeittakt mit und geht ueber ALLE Vertraege.
 *
 * <p><b>Es wird hoechstens EIN Tag berechnet, nie nachgefordert.</b> Ein Server, der drei
 * Tage aus war, bucht beim naechsten Start eine Miete und nicht drei. In diesen drei Tagen
 * konnte niemand seinen Laden benutzen; eine Nachforderung traefe genau den Spieler, der
 * nichts dafuer kann - samt der Ausfaelle, die ihn dann raeumen.
 *
 * <p><b>Der Waechter ueber "heute schon bezahlt" ist das BUCHUNGSBUCH</b>, wie beim
 * {@link Tagesbonus} und bei der Ladenlizenz. Gefragt wird {@link Kasse#summeSeit} mit
 * {@link Buchungsgrund#LADEN_MIETE} seit dem Tagesbeginn, und zwar auf {@code == 0}: Eine
 * AUSGABE steht im Buch NEGATIV, also waere ein Vorzeichenvergleich hier immer falsch oder
 * immer wahr. Keine Reihenfolge von Abstuerzen kann damit zweimal am selben Tag abbuchen.
 *
 * <p><b>Die Ausfallzahl steht dagegen am VERTRAG und nicht im Buch</b> - die eine Stelle,
 * an der das Buchungsbuch nicht die Wahrheit ist. Aus dem Buch waere sie zu rechnen ("an
 * wie vielen der letzten Tage fehlt eine Mietzeile"), und ein Server, der fuenf Tage aus
 * war, hat fuenf Tage ohne Mietzeile: Die Rechnung raeumte danach jeden Laden der Halle.
 * Die gezaehlte Zahl entsteht nur, wenn ein Lauf wirklich stattfand und das Geld wirklich
 * fehlte.
 *
 * <p><b>Die Reihenfolge der Raeumung ist die, die keine Gegenstaende verdoppeln kann</b>,
 * und sie ist in dieser Reihenfolge Pflicht und nicht Geschmack:
 * <ol>
 *   <li>Truhe LESEN - noch nichts veraendern.</li>
 *   <li>Alles ins Abholfach legen. Das ist der Schritt, der scheitern kann, und er ist
 *       der einzige, der dauerhaft wirkt. Scheitert er, bricht die Raeumung ab: Der
 *       Vertrag bleibt stehen, die Truhe bleibt voll, und der naechste Lauf versucht es
 *       wieder. Genau dafuer prueft {@link Mietregel#raeumungsreif} mit {@code >=} und
 *       nicht mit {@code ==}.</li>
 *   <li>Vertrag loeschen.</li>
 *   <li>Truhe leeren. Der Inhalt liegt jetzt im Fach; waere die Truhe noch voll, wuerde
 *       der naechste Mieter dieselben Gegenstaende ein zweites Mal finden.</li>
 * </ol>
 * Die andere Reihenfolge - erst leeren, dann ins Fach - verliert bei einem Abbruch
 * dazwischen die ganze Ladung, und das ist der eine Satz, den das Abholfach nicht brechen
 * darf. Bleibt ein Rest: Bricht der Server zwischen Schritt 3 und 4 ab, oder wird der
 * Chunk danach nicht mehr gespeichert, steht die Ware im Fach UND in der Truhe. Dagegen
 * steht keine Reihenfolge, sondern nur, dass die Einheit dann keinem Mieter gehoert -
 * und wer eine freie Einheit mietet, bekommt eine geleerte Truhe (siehe TODO zum
 * Ladenschutz, ohne den dieser Rest offen bleibt).
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Gerufen wird aus dem Spielzeittakt, und eine
 * Ausnahme dort waere nicht eine verlorene Miete, sondern die Spielzeit aller Spieler bis
 * zum naechsten Neustart.
 */
final class Mietlauf {

    /** Was aus einem Vertrag an diesem Tag geworden ist. */
    enum Ausgang {
        /** Die Miete ist abgebucht. */
        BEZAHLT,
        /** Heute stand schon eine Mietzeile im Buch - nichts zu tun. */
        SCHON_BEZAHLT,
        /** Das Geld fehlte; der Ausfall ist gezaehlt, der Laden bleibt noch. */
        GEWARNT,
        /** Zu viele Ausfaelle - der Laden ist weg, die Kaution VERFALLEN. */
        GERAEUMT,
        /** Die Mietdauer ist um, ohne dass verlaengert wurde - die Kaution kommt zurueck. */
        ABGELAUFEN,
        /** Die Datenbank hat geschwiegen. Steht im Protokoll, wird wiederholt. */
        GESTOERT
    }

    /**
     * Was mit einem Vertrag passiert ist - eine Zeile je Laden.
     *
     * @param vertrag        der Vertrag, wie er VOR dem Lauf aussah. Bei
     *                       {@link Ausgang#GEWARNT} steht die neue Ausfallzahl in
     *                       {@link #ausfaelle} und nicht hier
     * @param betrag         die abgebuchte Miete, oder bei {@link Ausgang#GEWARNT}, was
     *                       gefehlt haette
     * @param ausfaelle      die Ausfallzahl NACH diesem Tag
     * @param bisRaeumung    wie viele Ausfaelle noch gehen, bevor geraeumt wird. 0 heisst:
     *                       der naechste raeumt
     * @param kautionZurueck was dem Spieler gutgeschrieben wurde - nur beim Ablauf, beim
     *                       Raeumen immer 0
     * @param insFach        wie viele Stapel ins Abholfach gewandert sind
     */
    record Posten(Ausgang ausgang, Mietvertrag vertrag, long betrag, int ausfaelle,
                  int bisRaeumung, long kautionZurueck, int insFach) {
    }

    /**
     * Die Ladentruhe - die eine Naht zum Spiel.
     *
     * <p>Zwei Methoden und nicht eine, die liest und leert: Zwischen beiden liegt der
     * Schreibzug ins Abholfach, und der kann scheitern. Eine Methode, die beides tut,
     * machte die Reihenfolge aus dem Klassenkopf unmoeglich.
     */
    interface Truhe {

        /**
         * Was in der Truhe dieser Einheit liegt - ohne sie zu veraendern.
         *
         * <p>Eine leere Liste ist der haeufigste Fall und kein Fehler: Die meisten
         * verlassenen Laeden haben eine leere Truhe. Eine leere Liste ist auch die
         * Antwort, wenn die Welt nicht geladen ist oder an der Stelle keine Truhe steht -
         * die Umsetzung klagt dann ins Protokoll, aber sie wirft nicht. Ein Wurf hier
         * liesse einen Laden fuer immer unraeumbar.
         *
         * <p>Ein einzelner Stapel, der sich nicht in ein {@link Abholgut} fassen laesst,
         * wird von der Umsetzung UEBERGANGEN und protokolliert - nicht geworfen. Sonst
         * verhinderte ein einziger unmoeglicher Gegenstand die Raeumung der ganzen
         * Einheit, und der Laden stuende fuer immer da.
         */
        List<Abholgut> lese(String einheit);

        /**
         * Leert die Truhe dieser Einheit.
         *
         * <p>Wird erst gerufen, wenn der Inhalt dauerhaft im Abholfach liegt. Wirft nicht:
         * Was hier schiefgeht, ist eine Truhe, die noch voll ist - unschoen, aber nichts
         * ist verloren.
         */
        void leere(String einheit);
    }

    private final Wirtschaftsstand wirtschaft;
    private final Mietablage ablage;
    private final Abholablage fach;
    private final Kasse kasse;
    private final Truhe truhe;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * Der Tag, an dem zuletzt vollstaendig gelaufen wurde - {@code null} vor dem ersten.
     *
     * <p>Nur im Arbeitsspeicher, und das ist Absicht: Nach einem Neustart laeuft der erste
     * Takt wieder, und die Schranke gegen eine zweite Abbuchung ist dann das Buchungsbuch
     * und nicht dieser Merker. Eine Spalte dafuer waere eine zweite Wahrheit ueber
     * "bezahlt", die nach dem ersten Absturz von der ersten abweicht.
     *
     * <p>Gesetzt wird er nur nach einem STOERUNGSFREIEN Lauf. War die Datenbank weg, soll
     * der naechste Takt es erneut versuchen - eine verlorene Tagesmiete waere sonst
     * endgueltig, und doppelt abbuchen kann der Wiederholversuch nicht.
     */
    private LocalDate zuletzt;

    Mietlauf(Wirtschaftsstand wirtschaft, Mietablage ablage, Abholablage fach,
             Kasse kasse, Truhe truhe, Clock uhr, Logger protokoll) {
        this.wirtschaft = wirtschaft;
        this.ablage = ablage;
        this.fach = fach;
        this.kasse = kasse;
        this.truhe = truhe;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der Taktschlag: laeuft, wenn der Tag gewechselt hat - sonst nicht.
     *
     * <p>Die Tagesabfrage steht VOR dem Lesen der Vertraege, und das ist keine Feinheit:
     * Der Takt schlaegt jede Minute, und ein Lauf fragt je Vertrag zweimal die Datenbank.
     * Ohne diese Schranke waeren das bei 80 Laeden 160 Abfragen in der Minute - fuer
     * nichts, denn das Buchungsbuch sagt an 1.439 von 1.440 Minuten des Tages "schon
     * bezahlt".
     *
     * @return die Posten des Laufs, oder eine LEERE Liste, wenn heute schon gelaufen wurde
     */
    List<Posten> imTakt() {
        LocalDate heute = heute();
        if (heute.equals(zuletzt)) {
            return List.of();
        }
        List<Posten> posten = laufe(heute);
        boolean gestoert = false;
        for (Posten einer : posten) {
            if (einer.ausgang() == Ausgang.GESTOERT) {
                gestoert = true;
            }
        }
        if (!gestoert) {
            zuletzt = heute;
        }
        return posten;
    }

    /**
     * Ein Lauf ueber alle Vertraege - fuer den Takt und fuer die Probe.
     *
     * <p>Die Reihenfolge ist die von {@link Mietablage#alle}, also die der
     * Einheitennamen. Das ist kein Geschmack: Hier wird Geld abgebucht, und nach einem
     * Abbruch mitten im Lauf soll nachrechenbar sein, wer schon gezahlt hat.
     */
    List<Posten> laufe(LocalDate heute) {
        List<Mietvertrag> vertraege;
        try {
            vertraege = ablage.alle();
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Der taegliche Lauf der Ladenmiete konnte die"
                    + " Vertraege nicht lesen - heute wurde keine Miete abgebucht. Der"
                    + " naechste Takt versucht es erneut.", fehler);
            return List.of(new Posten(Ausgang.GESTOERT, null, 0L, 0, 0, 0L, 0));
        }
        List<Posten> posten = new ArrayList<>();
        for (Mietvertrag vertrag : vertraege) {
            posten.add(einVertrag(vertrag, heute));
        }
        return posten;
    }

    /** Ein einzelner Vertrag - die ganze Regel dieses Teils. */
    private Posten einVertrag(Mietvertrag vertrag, LocalDate heute) {
        Mietwerte werte = wirtschaft.werte().laeden();
        try {
            // Der Ablauf steht VOR der Abbuchung, und das ist der Unterschied zwischen
            // "sein Vertrag war zu Ende" und "er konnte nicht zahlen": Am Tag nach
            // laeuftBis wird nicht mehr abgebucht. Wer bis dahin jeden Tag gezahlt hat,
            // soll nicht am letzten Tag auch noch einen Ausfall bekommen.
            if (Mietregel.abgelaufen(vertrag.laeuftBis(), heute)) {
                return beenden(vertrag, Ausgang.ABGELAUFEN);
            }
            // Das Buchungsbuch ist der Zeuge - siehe den Klassenkopf. GLEICH NULL und
            // nicht ein Vorzeichen: Eine AUSGABE steht negativ im Buch.
            Instant tagesbeginn =
                    Tagesgrenze.tagesbeginn(heute, wirtschaft.werte().zeit().zone());
            if (kasse.summeSeit(vertrag.mieter(), tagesbeginn, Buchungsgrund.LADEN_MIETE)
                    != 0L) {
                return new Posten(Ausgang.SCHON_BEZAHLT, vertrag, 0L,
                        vertrag.ausfaelle(), uebrig(vertrag.ausfaelle(), werte), 0L, 0);
            }
            if (kasse.abbuchen(vertrag.mieter(), vertrag.miete(),
                    Buchungsgrund.LADEN_MIETE)) {
                return bezahlt(vertrag, werte);
            }
            return nichtBezahlt(vertrag, werte);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Die Ladenmiete fuer \"" + vertrag.einheit()
                    + "\" (" + vertrag.mieter() + ") liess sich heute nicht abrechnen -"
                    + " der naechste Takt versucht es erneut.", fehler);
            return new Posten(Ausgang.GESTOERT, vertrag, vertrag.miete(),
                    vertrag.ausfaelle(), uebrig(vertrag.ausfaelle(), werte), 0L, 0);
        }
    }

    /** Bezahlt - und die Ausfaelle sind weg. */
    private Posten bezahlt(Mietvertrag vertrag, Mietwerte werte) {
        Mietvertrag ohne = vertrag.ohneAusfaelle();
        // Der Vergleich auf dieselbe Kennung spart den Schreibzug, und das ist der
        // haeufigste Tag: Alle zahlen, niemand ist in Verzug, und dann soll der Lauf
        // nicht jede Zeile der Halle neu schreiben. ohneAusfaelle gibt dafuer this
        // zurueck, wenn schon keine da sind.
        if (ohne != vertrag) {
            ablage.schreibe(ohne);
            protokoll.info("Ladenmiete \"" + vertrag.einheit() + "\": "
                    + vertrag.miete() + " Mark bezahlt - die " + vertrag.ausfaelle()
                    + " offenen Ausfaelle sind damit erledigt.");
        }
        return new Posten(Ausgang.BEZAHLT, vertrag, vertrag.miete(), 0,
                werte.ausfaelleBisRaeumung(), 0L, 0);
    }

    /** Nicht bezahlt - warnen oder raeumen. */
    private Posten nichtBezahlt(Mietvertrag vertrag, Mietwerte werte) {
        Mietvertrag mitAusfall = vertrag.mitAusfall();
        if (!Mietregel.raeumungsreif(mitAusfall.ausfaelle(),
                werte.ausfaelleBisRaeumung())) {
            ablage.schreibe(mitAusfall);
            protokoll.info("Ladenmiete \"" + vertrag.einheit() + "\": "
                    + vertrag.miete() + " Mark fehlen (" + mitAusfall.ausfaelle()
                    + ". Ausfall). Geraeumt wird beim "
                    + werte.ausfaelleBisRaeumung() + ".");
            return new Posten(Ausgang.GEWARNT, vertrag, vertrag.miete(),
                    mitAusfall.ausfaelle(),
                    uebrig(mitAusfall.ausfaelle(), werte), 0L, 0);
        }
        return beenden(mitAusfall, Ausgang.GERAEUMT);
    }

    /**
     * Beendet einen Vertrag - die Raeumung und der Ablauf in EINER Methode.
     *
     * <p>Die zwei Wege unterscheiden sich in genau einer Sache: ob die Kaution
     * zurueckkommt. Alles andere - Truhe lesen, ins Fach legen, Vertrag loeschen, Truhe
     * leeren - ist dasselbe, und zwei Methoden daneben waeren zwei Reihenfolgen, von denen
     * eine irgendwann anders ist. Die Reihenfolge selbst steht im Klassenkopf.
     *
     * <p>Beim ABLAUF kommt die Kaution zurueck [entschieden 08.11.2026]: Der Mieter hat
     * jeden Tag gezahlt, und der Vertrag endete so, wie er abgeschlossen war. Die Kaution
     * ist das Pfand gegen das Weglaufen, nicht gegen das Nicht-Verlaengern. Beim RAEUMEN
     * verfaellt sie - das ist ihr ganzer Zweck (Plan 7.1: zurueck nur "bei ordentlicher
     * Rueckgabe"), und sie macht das Weglaufen teurer als das Kuendigen.
     */
    private Posten beenden(Mietvertrag vertrag, Ausgang ausgang) {
        String herkunft = "Laden " + vertrag.einheit();
        List<Abholgut> inhalt = truhe.lese(vertrag.einheit());
        // Schritt 2 der Reihenfolge, und der einzige, der dauerhaft wirkt. Scheitert er,
        // bricht die Raeumung ab: Der Vertrag bleibt stehen, die Truhe bleibt voll, und
        // der naechste Lauf versucht es wieder. Das Zuruecknehmen steckt in lege() selbst
        // (alles oder nichts), hier ist nur zu entscheiden, dass es dann NICHT weitergeht.
        try {
            fach.lege(vertrag.mieter(), herkunft, uhr.instant(), inhalt);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Der Inhalt der Truhe von \"" + vertrag.einheit()
                    + "\" (" + inhalt.size() + " Stapel) liess sich nicht ins Abholfach"
                    + " von " + vertrag.mieter() + " legen. Der Vertrag bleibt stehen und"
                    + " die Truhe bleibt voll - geraeumt wird beim naechsten Lauf. Nichts"
                    + " ist verloren.", fehler);
            Mietwerte werte = wirtschaft.werte().laeden();
            return new Posten(Ausgang.GESTOERT, vertrag, vertrag.miete(),
                    vertrag.ausfaelle(), uebrig(vertrag.ausfaelle(), werte), 0L, 0);
        }
        ablage.loesche(vertrag.einheit());
        // Schritt 4: Jetzt liegt der Inhalt im Fach. Waere die Truhe noch voll, faende der
        // naechste Mieter dieselben Gegenstaende ein zweites Mal.
        truhe.leere(vertrag.einheit());
        long zurueck = ausgang == Ausgang.ABGELAUFEN ? kautionZurueck(vertrag) : 0L;
        protokoll.info("Laden \"" + vertrag.einheit() + "\" von " + vertrag.mieter()
                + " ist " + (ausgang == Ausgang.ABGELAUFEN
                        ? "abgelaufen (Kaution " + zurueck + " Mark zurueck)"
                        : "geraeumt (" + vertrag.ausfaelle() + " Ausfaelle, Kaution "
                                + vertrag.kaution() + " Mark verfallen)")
                + " - " + inhalt.size() + " Stapel im Abholfach.");
        return new Posten(ausgang, vertrag, 0L, vertrag.ausfaelle(), 0, zurueck,
                inhalt.size());
    }

    /**
     * Gibt die Kaution zurueck - und schweigt nicht, wenn es scheitert.
     *
     * <p>Nach dem Loeschen und nicht davor, dieselbe Richtung wie bei
     * {@code Mietbuch.kuendigen}: Beim Geldfluss ZUM Spieler wird erst der Anspruch
     * weggenommen und dann gezahlt. Die andere Reihenfolge liesse nach einem Abbruch das
     * Geld UND den Laden - und das waere wiederholbar.
     */
    private long kautionZurueck(Mietvertrag vertrag) {
        if (!vertrag.mitKaution()) {
            return 0L;
        }
        try {
            kasse.gutschreiben(vertrag.mieter(), vertrag.kaution(),
                    Buchungsgrund.LADEN_KAUTION_ZURUECK);
            return vertrag.kaution();
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Die Kaution von " + vertrag.kaution()
                    + " Mark fuer " + vertrag.mieter() + " (abgelaufener Laden \""
                    + vertrag.einheit() + "\") liess sich nicht zurueckzahlen - der"
                    + " Vertrag ist bereits geloescht. Von Hand gutzuschreiben.", fehler);
            return 0L;
        }
    }

    /** Wie viele Ausfaelle noch gehen - nie unter 0, auch bei gesenkter Schranke. */
    private static int uebrig(int ausfaelle, Mietwerte werte) {
        return Math.max(0, werte.ausfaelleBisRaeumung() - ausfaelle);
    }

    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), wirtschaft.werte().zeit().zone());
    }
}
