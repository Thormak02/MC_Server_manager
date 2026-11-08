package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietregel;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Mietwerte;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Ladenmiete - die eine Stelle, die mietet, verlaengert und kuendigt.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Tagesbonus} und {@link Spielzeitbuch}:
 * Was ohne Server pruefbar ist, soll es auch sein. Das Spiel liefert nur eine UUID und
 * einen Namen herein.
 *
 * <p><b>Das Buchungsbuch ist der Beleg dafuer, dass die Lizenz bezahlt ist.</b> Dieselbe
 * Entscheidung wie beim Tagesbonus und aus demselben Grund: Es gibt keine Spalte "hat
 * Lizenz" und keinen zweiten Ort. Gefragt wird {@link Kasse#summeSeit} mit
 * {@link Buchungsgrund#LADEN_LIZENZ} seit Anbeginn. Eine eigene Spalte waere eine zweite
 * Wahrheit, die nach dem ersten Absturz von der ersten abweicht - und dann zahlt ein
 * Spieler 50.000 Mark ein zweites Mal.
 *
 * <p><b>Die Reihenfolge der Schritte ist in JEDER Tat die, die kein Geld erzeugen
 * kann.</b> Das ist die tragende Entscheidung, und sie laeuft in die zwei Richtungen
 * verschieden:
 * <ul>
 *   <li><b>Beim Mieten fliesst Geld WEG</b>: erst abbuchen, dann den Vertrag schreiben.
 *       Bricht es dazwischen ab, hat der Spieler gezahlt und keinen Laden - und genau
 *       dafuer gibt es {@link Buchungsgrund#LADEN_ERSTATTET}, dieselbe Antwort wie beim
 *       Parzellenkauf. Die andere Reihenfolge waere schlimmer: Vertrag zuerst, dann
 *       abbuchen, und ein Abbruch dazwischen liesse einen Laden stehen, fuer den niemand
 *       Kaution gezahlt hat.</li>
 *   <li><b>Beim Kuendigen fliesst Geld HER</b>: erst den Vertrag loeschen, dann die
 *       Kaution gutschreiben. Bricht es dazwischen ab, hat der Spieler seinen Laden
 *       verloren und seine Kaution nicht bekommen - das steht als {@code SEVERE} im
 *       Protokoll und ist von Hand zu heilen. Die andere Reihenfolge waere schlimmer: Wer
 *       erst gutschreibt und dann loeschen will, hat nach einem Abbruch das Geld UND den
 *       Laden - und kann es wiederholen.</li>
 * </ul>
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Jede Tat antwortet mit einem Bescheid, auch
 * wenn die Datenbank schweigt. Ein Wurf aus einem Befehl waere fuer den Spieler eine rote
 * Zeile ohne Sinn.
 */
final class Mietbuch {

    /**
     * Was aus einer Tat geworden ist.
     *
     * <p>EINE Aufzaehlung fuer alle drei Taten, wie bei {@code Besitzbuch.Bescheid}: Die
     * Absagen {@link #KEIN_LADEN} und {@link #GESTOERT} treffen mehrere Taten, und zwei
     * Aufzaehlungen mit demselben Wort darin liefen auseinander. Jede Tat gibt nur ihre
     * eigene Teilmenge zurueck; welche, steht an ihrer Methode.
     */
    enum Bescheid {
        /** Gemietet. */
        GEMIETET,
        /** Verlaengert. */
        VERLAENGERT,
        /** Ordentlich gekuendigt - die Kaution geht zurueck. */
        GEKUENDIGT,
        /** Diesen Stand gibt es nicht; der Name stand nicht in der {@code config.yml}. */
        KEINE_EINHEIT,
        /** Der Stand ist vermietet - an jemand anderen. */
        SCHON_VERMIETET,
        /** Der Spieler hat schon einen Laden (Plan 7.1: einer je Spieler). */
        SCHON_EIN_LADEN,
        /** Der Spieler hat keinen Laden - nichts zu verlaengern, nichts zu kuendigen. */
        KEIN_LADEN,
        /** Noch zu frueh: Es ist mehr als eine Mietdauer uebrig. */
        NOCH_ZU_FRUEH,
        /** Das Guthaben reicht nicht fuer Kaution und Lizenz. */
        ZU_WENIG_GELD,
        /** Die Datenbank hat geschwiegen. Steht im Protokoll. */
        GESTOERT
    }

    /**
     * Das Ergebnis von {@link #mieten}.
     *
     * @param vertrag  bei {@link Bescheid#GEMIETET} der neue, bei
     *                 {@link Bescheid#SCHON_EIN_LADEN} der VORHANDENE - damit die Absage
     *                 sagen kann, welcher Laden dem Spieler schon gehoert. Sonst
     *                 {@code null}
     * @param kosten   was die Tat gekostet hat; bei {@link Bescheid#ZU_WENIG_GELD}, was
     *                 sie gekostet HAETTE
     * @param lizenz   ob die Lizenz in {@code kosten} steckt, also ob dies der erste
     *                 Laden dieses Spielers ueberhaupt war
     * @param guthaben was der Spieler hatte, als gefragt wurde
     */
    record Abschluss(Bescheid bescheid, Mietvertrag vertrag, long kosten, boolean lizenz,
                     long guthaben) {
    }

    /**
     * Das Ergebnis von {@link #verlaengern}.
     *
     * @param vertrag  bei {@link Bescheid#VERLAENGERT} der verlaengerte, bei
     *                 {@link Bescheid#NOCH_ZU_FRUEH} der unveraenderte. Sonst {@code null}
     * @param restTage wie viele Tage der Vertrag in {@code vertrag} noch gilt - nach der
     *                 Verlaengerung, beziehungsweise vor der abgelehnten
     */
    record Verlaengerung(Bescheid bescheid, Mietvertrag vertrag, long restTage) {
    }

    /**
     * Das Ergebnis von {@link #kuendigen}.
     *
     * @param einheit        welcher Laden gekuendigt wurde, oder {@code null}
     * @param kautionZurueck was zurueckgegeben wurde - die HINTERLEGTE Kaution aus der
     *                       Vertragszeile und nicht die aus der {@code config.yml}
     */
    record Kuendigung(Bescheid bescheid, String einheit, long kautionZurueck) {
    }

    private final Wirtschaftsstand wirtschaft;
    private final Ladenverzeichnis verzeichnis;
    private final Mietablage ablage;
    private final Kasse kasse;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * @param wirtschaft  woher Zone und Mietwerte kommen - beim GEBRAUCH gefragt und nicht
     *                    in ein Feld kopiert, damit ein Neuladen sie erreicht
     * @param verzeichnis die Ladeneinheiten aus der {@code config.yml} - beim Start
     *                    gelesen und danach unveraenderlich
     */
    Mietbuch(Wirtschaftsstand wirtschaft, Ladenverzeichnis verzeichnis,
             Mietablage ablage, Kasse kasse, Clock uhr, Logger protokoll) {
        this.wirtschaft = wirtschaft;
        this.verzeichnis = verzeichnis;
        this.ablage = ablage;
        this.kasse = kasse;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Mietet eine Ladeneinheit.
     *
     * <p>Moegliche Bescheide: {@link Bescheid#GEMIETET}, {@link Bescheid#KEINE_EINHEIT},
     * {@link Bescheid#SCHON_EIN_LADEN}, {@link Bescheid#SCHON_VERMIETET},
     * {@link Bescheid#ZU_WENIG_GELD}, {@link Bescheid#GESTOERT}.
     *
     * <p>Die Reihenfolge der Absagen ist die der Kosten: Erst der Nachschlag im
     * Verzeichnis (kein Netz), dann zwei Abfragen, dann das Buchungsbuch, dann das
     * Guthaben.
     */
    Abschluss mieten(UUID wer, String name) {
        if (wer == null || name == null || name.isBlank()) {
            return new Abschluss(Bescheid.KEINE_EINHEIT, null, 0L, false, 0L);
        }
        // NUR getrimmt und NICHT kleingeschrieben - und selbst das trim ist gemessen
        // wirkungslos. Zweimal am 08.11.2026 eingespritzt, zweimal blieb alles gruen:
        // Jede Stelle dahinter normiert selbst. Ladenverzeichnis.mitNamen ist
        // nachsichtig, Mietvertrag normiert im Pruefteil, SpeicherMiete und MariaMiete
        // bilden ihren Schluessel selbst. Ein toLowerCase hier waere eine vierte
        // Wahrheit.
        //
        // Warum das trim trotzdem dasteht: Es bestimmt, was im PROTOKOLL erscheint, wenn
        // ein Abschluss scheitert. Was der SPIELER in der Absage liest, ist ohnehin sein
        // getippter Text - den gibt Miettext aus und nicht dieser Wert.
        String einheit = name.trim();
        if (verzeichnis.mitNamen(einheit) == null) {
            return new Abschluss(Bescheid.KEINE_EINHEIT, null, 0L, false, 0L);
        }
        Mietwerte werte = wirtschaft.werte().laeden();
        // NUR die Kaution wird gezaehlt, nicht die Lizenz - siehe erstatte().
        long kautionGezahlt = 0L;
        try {
            Mietvertrag schon = ablage.fuerMieter(wer);
            if (schon != null) {
                // Der vorhandene Vertrag wandert mit: Die Absage soll sagen, WELCHER
                // Laden ihm schon gehoert - sonst sucht er ihn.
                return new Abschluss(Bescheid.SCHON_EIN_LADEN, schon, 0L, false,
                        kasse.guthaben(wer));
            }
            if (ablage.fuerEinheit(einheit) != null) {
                return new Abschluss(Bescheid.SCHON_VERMIETET, null, 0L, false,
                        kasse.guthaben(wer));
            }
            // Das Buchungsbuch ist der Beleg - siehe den Kopf der Klasse.
            //
            // Gefragt wird auf GLEICH NULL und nicht auf ein Vorzeichen: Eine AUSGABE
            // steht im Buch negativ (SpeicherKasse schreibt -betrag), eine EINNAHME
            // positiv. Ein "> 0" waere hier also immer falsch und ein "<= 0" immer wahr -
            // und das Zweite hat in der ersten Probenrunde genau zugeschlagen: Die Lizenz
            // wurde bei jedem zweiten Laden erneut verlangt. "Keine Zeile da" ist die
            // Frage, die gemeint ist, und sie kennt kein Vorzeichen.
            boolean lizenzFaellig = werte.kostetLizenz() && kasse.summeSeit(wer,
                    Instant.EPOCH, Buchungsgrund.LADEN_LIZENZ) == 0L;
            long kosten = werte.abschlusskosten(!lizenzFaellig);
            long guthaben = kasse.guthaben(wer);
            if (guthaben < kosten) {
                return new Abschluss(Bescheid.ZU_WENIG_GELD, null, kosten, lizenzFaellig,
                        guthaben);
            }

            // Erst das Geld, dann der Vertrag - siehe den Kopf der Klasse.
            if (lizenzFaellig) {
                if (!kasse.abbuchen(wer, werte.lizenz(), Buchungsgrund.LADEN_LIZENZ)) {
                    return new Abschluss(Bescheid.ZU_WENIG_GELD, null, kosten,
                            lizenzFaellig, kasse.guthaben(wer));
                }
            }
            if (werte.mitKaution()
                    && !kasse.abbuchen(wer, werte.kaution(), Buchungsgrund.LADEN_KAUTION)) {
                // Zwischen der Guthabenfrage und hier hat etwas anderes gebucht.
                // Zurueck geht nichts, denn nichts Rueckgaengiges ist geflossen: Eine
                // etwa gezahlte Lizenz BLEIBT bezahlt und gilt beim naechsten Versuch.
                return new Abschluss(Bescheid.ZU_WENIG_GELD, null, kosten, lizenzFaellig,
                        kasse.guthaben(wer));
            }
            if (werte.mitKaution()) {
                kautionGezahlt = werte.kaution();
            }

            LocalDate heute = heute();
            Mietvertrag vertrag = new Mietvertrag(einheit, wer, heute,
                    Mietregel.laeuftBisAb(heute, werte.dauerTage()), werte.miete(),
                    werte.kaution());
            ablage.schreibe(vertrag);
            return new Abschluss(Bescheid.GEMIETET, vertrag, kosten, lizenzFaellig,
                    kasse.guthaben(wer));
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Mieten von \"" + einheit + "\" fuer " + wer
                    + " ist gescheitert", fehler);
            erstatte(wer, kautionGezahlt, "Mieten gescheitert");
            return new Abschluss(Bescheid.GESTOERT, null, 0L, false, 0L);
        }
    }

    /**
     * Verlaengert den Laden dieses Spielers.
     *
     * <p>Moegliche Bescheide: {@link Bescheid#VERLAENGERT}, {@link Bescheid#KEIN_LADEN},
     * {@link Bescheid#NOCH_ZU_FRUEH}, {@link Bescheid#GESTOERT}.
     *
     * <p><b>Das Verlaengern kostet nichts</b>, und das ist kein Versehen: Gezahlt wird
     * TAEGLICH (Plan 7.1), vom Lauf aus Teil C. Die Laufzeit sagt nicht, wofuer bezahlt
     * ist, sondern wann der Vertrag OHNE ZUTUN endet - sie ist der Griff gegen den
     * Spieler, der aufhoert zu spielen und noch Geld auf dem Konto hat. Eine Gebuehr
     * darauf waere eine zweite Miete fuer dieselben Tage.
     *
     * <p>Die vereinbarte Miete UND die hinterlegte Kaution bleiben stehen - der
     * Bestandsschutz aus Plan 1943. Wer den neuen Satz will, kuendigt und mietet neu.
     */
    Verlaengerung verlaengern(UUID wer) {
        if (wer == null) {
            return new Verlaengerung(Bescheid.KEIN_LADEN, null, 0L);
        }
        try {
            Mietvertrag vertrag = ablage.fuerMieter(wer);
            if (vertrag == null) {
                return new Verlaengerung(Bescheid.KEIN_LADEN, null, 0L);
            }
            int dauer = wirtschaft.werte().laeden().dauerTage();
            LocalDate heute = heute();
            if (!Mietregel.darfVerlaengern(vertrag.laeuftBis(), heute, dauer)) {
                return new Verlaengerung(Bescheid.NOCH_ZU_FRUEH, vertrag,
                        Mietregel.restTage(vertrag.laeuftBis(), heute));
            }
            Mietvertrag laenger = vertrag.bis(
                    Mietregel.verlaengert(vertrag.laeuftBis(), heute, dauer));
            ablage.schreibe(laenger);
            return new Verlaengerung(Bescheid.VERLAENGERT, laenger,
                    Mietregel.restTage(laenger.laeuftBis(), heute));
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Verlaengern fuer " + wer + " ist gescheitert",
                    fehler);
            return new Verlaengerung(Bescheid.GESTOERT, null, 0L);
        }
    }

    /**
     * Kuendigt den Laden dieses Spielers - die ORDENTLICHE Rueckgabe.
     *
     * <p>Moegliche Bescheide: {@link Bescheid#GEKUENDIGT}, {@link Bescheid#KEIN_LADEN},
     * {@link Bescheid#GESTOERT}.
     *
     * <p>Zurueck geht die Kaution aus der VERTRAGSZEILE und nicht die aus der
     * {@code config.yml}: Wer mit 5.000 gemietet hat, bekommt 5.000, auch wenn in der
     * Datei inzwischen 9.000 steht. Und beim Raeumen wegen Zahlungsausfall (Teil C) faellt
     * diese Gutschrift ausdruecklich NICHT - das ist der ganze Zweck der Kaution.
     *
     * <p>Was mit dem INHALT der Ladentruhe geschieht, entscheidet dieser Teil nicht. Das
     * Abholfach kommt mit Teil C, und bis dahin bleibt die Truhe, wo sie ist.
     */
    Kuendigung kuendigen(UUID wer) {
        if (wer == null) {
            return new Kuendigung(Bescheid.KEIN_LADEN, null, 0L);
        }
        Mietvertrag vertrag;
        try {
            vertrag = ablage.fuerMieter(wer);
            if (vertrag == null) {
                return new Kuendigung(Bescheid.KEIN_LADEN, null, 0L);
            }
            // Erst loeschen, dann gutschreiben - siehe den Kopf der Klasse.
            ablage.loesche(vertrag.einheit());
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Kuendigen fuer " + wer + " ist gescheitert",
                    fehler);
            return new Kuendigung(Bescheid.GESTOERT, null, 0L);
        }
        if (!vertrag.mitKaution()) {
            return new Kuendigung(Bescheid.GEKUENDIGT, vertrag.einheit(), 0L);
        }
        try {
            kasse.gutschreiben(wer, vertrag.kaution(),
                    Buchungsgrund.LADEN_KAUTION_ZURUECK);
        } catch (RuntimeException fehler) {
            // SEVERE und nicht WARNING: Der Laden ist weg und das Geld nicht da. Von
            // Hand zu heilen, und niemand merkt es, wenn es nicht im Protokoll steht.
            // Gemeldet wird GEKUENDIGT, denn gekuendigt IST - eine Absage hier liesse
            // den Spieler glauben, er habe noch einen Laden.
            protokoll.log(Level.SEVERE, "Kaution von " + vertrag.kaution()
                    + " Mark fuer " + wer + " (Laden \"" + vertrag.einheit()
                    + "\") konnte nicht zurueckgezahlt werden - der Vertrag ist bereits"
                    + " geloescht. Von Hand gutzuschreiben.", fehler);
            return new Kuendigung(Bescheid.GEKUENDIGT, vertrag.einheit(), 0L);
        }
        return new Kuendigung(Bescheid.GEKUENDIGT, vertrag.einheit(), vertrag.kaution());
    }

    /** Der Vertrag dieses Spielers, oder {@code null} - fuer die Liste und die Auskunft. */
    Mietvertrag vertragVon(UUID wer) {
        if (wer == null) {
            return null;
        }
        try {
            return ablage.fuerMieter(wer);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Mietvertrag von " + wer + " nicht lesbar",
                    fehler);
            return null;
        }
    }

    /** Wie viele Tage dieser Vertrag noch gilt - gerechnet in der eingestellten Zone. */
    long restTage(Mietvertrag vertrag) {
        return Mietregel.restTage(vertrag.laeuftBis(), heute());
    }

    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), wirtschaft.werte().zeit().zone());
    }

    /**
     * Gibt die KAUTION zurueck - und schweigt nicht, wenn auch das scheitert.
     *
     * <p><b>Die Lizenz kommt ausdruecklich NICHT mit zurueck</b>, und das ist ein Befund
     * aus der ersten Probenrunde: Erstattet man sie, steht sie danach als bezahlt im Buch,
     * ohne dass Geld geblieben waere - der naechste Laden desselben Spielers waere gratis.
     *
     * <p>Und das ist nicht nur das kleinere Uebel, sondern das Richtige: Die Kaution
     * haengt an dem Vertrag, der nicht zustande kam, also muss sie zurueck. Die Lizenz
     * haengt an keinem Laden - sie ist einmalig und gilt weiter. Wer den Abschluss
     * wiederholt, zahlt nur noch die Kaution, die er gerade zurueckbekommen hat.
     */
    private void erstatte(UUID wer, long betrag, String warum) {
        if (betrag <= 0L) {
            return;
        }
        try {
            kasse.gutschreiben(wer, betrag, Buchungsgrund.LADEN_ERSTATTET);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Erstattung von " + betrag + " Mark an " + wer
                    + " ist gescheitert (" + warum + ") - von Hand gutzuschreiben.",
                    fehler);
        }
    }
}
