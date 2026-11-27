package de.freimarken.kern;

import java.util.List;
import java.util.UUID;

/**
 * Das Geld, das einer Freistadt gehoert - und nicht ihrem Besitzer.
 *
 * <p>Bis Teil E gab es das nicht: Die 400.000 Mark der Gruendung verschwanden beim Bund,
 * der Grundstueckspreis ging von Spieler zu Spieler, und die Marktgebuehr verschwand
 * ebenfalls. Eine Stadt war eine Flaeche mit einem Namen, kein Gemeinwesen mit einem
 * Haushalt.
 *
 * <p><b>Dieselbe Tabelle wie die Spielerkonten</b>, und das ist die ganze Bauart: Die
 * Kasse kennt nur Kennungen, keine Spieler - {@code kassen} hat zwei Spalten und keinen
 * Fremdschluessel auf irgendeine Spielerliste. Damit bekommt die Stadtkasse den Stand,
 * das Buchungsbuch, den Verlauf und die Summenprobe geschenkt, ohne eine einzige neue
 * Zeile im Schema. Eine zweite Geldtabelle waere eine zweite Wahrheit ueber Geld, und
 * die Regel "keine Geldbewegung ohne Zeile im Buch" gaelte dann nur noch fuer die eine
 * Haelfte.
 *
 * <p><b>Warum es diese Klasse trotzdem gibt</b>, statt die Kennung einfach irgendwo
 * hineinzureichen: Eine Stadtkennung in einer Spalte namens {@code spieler} ist genau
 * die Naht, an der jemand das Falsche liest - dieselbe Lage, in der Teil D das Wort
 * "Buerger" nicht zweimal belegen wollte. Hier ist die eine Stelle, die aus einer Stadt
 * ein Konto macht, und sie nimmt eine {@link Parzelle} und nie eine nackte
 * {@link UUID}: Wer kein Stadtobjekt in der Hand hat, kann hier nichts buchen.
 *
 * <p><b>Das Startguthaben ist die Falle dieses Bauschritts</b>, und darum steht ein
 * Riegel im Konstruktor. {@code Kasse.guthaben} gibt fuer ein unbekanntes Konto das
 * Startguthaben zurueck, und {@code stelleKontoSicher} legt JEDES Konto mit diesem
 * Betrag an - gerufen nicht nur von {@code eroeffne}, sondern von jeder Buchung. Mit der
 * Spielerkasse (ab Werk 50.000 Mark) haette die erste Gutschrift an eine Stadt ihr
 * 50.000 Mark geschenkt, und bei 399 Staedten waeren das nahezu zwanzig Millionen Mark
 * aus dem Nichts - lautlos, denn jede einzelne Buchung sieht richtig aus. Die Stadtkasse
 * braucht deshalb eine Kasse OHNE Startguthaben, und sie prueft das selbst, beim Start,
 * mit einer Frage und laut.
 */
public final class Stadtkasse {

    private final Kasse kasse;

    /**
     * @param kasse eine Kasse mit Startguthaben 0 - siehe die Klassenbeschreibung
     * @throws IllegalArgumentException wenn die Kasse ein Startguthaben vergibt; das
     *                                  waere geschenktes Geld in jeder Stadt
     */
    public Stadtkasse(Kasse kasse) {
        if (kasse == null) {
            throw new IllegalArgumentException("Die Stadtkasse braucht eine Kasse");
        }
        // DER RIEGEL GEGEN GESCHENKTES GELD, und er ist eine FRAGE und keine Zusage im
        // Kommentar: Eine Kennung, die noch nie ein Konto hatte, muss 0 ergeben. Ein
        // Zufallswert aus 122 Bit ist dafuer sicher genug - und er legt nichts an, denn
        // guthaben() liest nur.
        //
        // Beim START und nicht bei der ersten Buchung: Ein Betreiber, der das Plugin
        // falsch verdrahtet bekommt, soll es in der Startmeldung lesen und nicht drei
        // Wochen spaeter an einer Stadt mit 50.000 Mark, die niemand eingezahlt hat.
        long aufUnbekanntem = kasse.guthaben(UUID.randomUUID());
        if (aufUnbekanntem != 0L) {
            throw new IllegalArgumentException("Die Stadtkasse braucht eine Kasse ohne"
                    + " Startguthaben, diese gibt " + aufUnbekanntem + " Mark. Sonst"
                    + " bekaeme jede Stadt diesen Betrag geschenkt, sobald das erste"
                    + " Geld bei ihr ankommt - und zwar lautlos.");
        }
        this.kasse = kasse;
    }

    /**
     * Die Kontonummer dieser Stadt - die EINE Stelle, die aus einer Stadt ein Konto
     * macht.
     *
     * <p>Sie kommt aus der Stadtzeile und wird nicht gerechnet. Eine aus der
     * {@code id} abgeleitete Nummer waere der naheliegende Weg und ein Fehler:
     * AUTO_INCREMENT vergibt nach einem Neustart wieder, was hinten frei geworden ist,
     * und dann erbt eine neu gegruendete Stadt das Konto einer entfernten - Guthaben und
     * Verlauf eingeschlossen.
     *
     * @throws IllegalArgumentException wenn das keine Freistadt ist; nur eine Stadt hat
     *                                  eine Kasse, und der Riegel dafuer steht schon in
     *                                  {@link Parzelle}
     */
    public static UUID konto(Parzelle stadt) {
        if (stadt == null) {
            throw new IllegalArgumentException("Ohne Stadt kein Konto");
        }
        if (!stadt.istFreistadt()) {
            throw new IllegalArgumentException("Nur eine Freistadt hat eine Stadtkasse,"
                    + " nicht " + stadt.art().schluessel() + ": " + stadt.id());
        }
        return stadt.kasse();
    }

    /** Was in der Kasse dieser Stadt liegt. */
    public long stand(Parzelle stadt) {
        return kasse.guthaben(konto(stadt));
    }

    /**
     * Geld fliesst der Stadt zu.
     *
     * <p>Ohne Rueckgabewert, wie {@link Kasse#gutschreiben}: Eine Gutschrift kann nicht
     * abgelehnt werden.
     */
    public void einzahlen(Parzelle stadt, long betrag, Buchungsgrund grund) {
        kasse.gutschreiben(konto(stadt), betrag, grund);
    }

    /**
     * Geld aus der Kasse nehmen - wenn es gedeckt ist.
     *
     * <p>Gibt {@code false} zurueck, wenn nicht genug darin liegt, und hinterlaesst dann
     * nichts. Wer hier ein {@code true} erwartet, weil er gerade eingezahlt hat, hat
     * eine Nebenlaeufigkeit uebersehen und soll das sehen.
     */
    public boolean abbuchen(Parzelle stadt, long betrag, Buchungsgrund grund) {
        return kasse.abbuchen(konto(stadt), betrag, grund);
    }

    /**
     * Die letzten Buchungen dieser Stadt, die neueste zuerst.
     *
     * <p>Der Verlauf ist der Grund, aus dem die Stadtkasse ein richtiges Konto ist und
     * keine Zahl an der Stadtzeile: Ein Stadtbesitzer soll sehen, WOHER das Geld kam -
     * sonst ist die Kasse ein Betrag ohne Geschichte, und bei der ersten Abweichung
     * glaubt ihm niemand.
     */
    public List<Buchung> verlauf(Parzelle stadt, int hoechstens) {
        return kasse.verlauf(konto(stadt), hoechstens);
    }

    /**
     * Was diese Stadt seit {@code ab} aus diesem Grund bewegt hat.
     *
     * <p>Gebraucht fuer eine Frage, die kein Betrag beantwortet: HAT diese Stadt
     * etwas einmal getan? Beim Stadtrecht ist die Buchung der Nachweis - gekauft
     * heisst "es gibt eine Zeile dieses Grundes". Siehe
     * {@code Freistadtbuch.hatStadtrecht}.
     *
     * <p>Hier und nicht am {@code Kasse}-Zugang des Aufrufers: Wer Stadtgeld fragt,
     * fragt die Stadtkasse - sonst braeuchte jeder Aufrufer das Konto selbst, und
     * {@link #konto} waere nicht mehr die eine Stelle, die es bildet.
     *
     * <p>ACHTUNG beim Lesen des Ergebnisses: Eine Ausgabe steht NEGATIV im Buch. Wer
     * wissen will, ob es die Zeile gibt, fragt {@code != 0} und nicht {@code > 0}.
     */
    public long summeSeit(Parzelle stadt, java.time.Instant ab,
                          Buchungsgrund grund) {
        return kasse.summeSeit(konto(stadt), ab, grund);
    }

    /**
     * Das Konto der Stadt anlegen, falls es das noch nicht gibt.
     *
     * <p>Gerufen beim Gruenden. Notwendig ist es nicht - die erste Buchung legt das
     * Konto ohnehin an -, aber es macht den Unterschied zwischen "diese Stadt hat 0
     * Mark" und "diese Stadt hat noch nie ein Konto gehabt" im Buch sichtbar. Und es ist
     * die Zeile, an der eine falsch verdrahtete Kasse auffaellt, bevor echtes Geld
     * fliesst.
     *
     * @return {@code true}, wenn das Konto jetzt entstanden ist
     */
    public boolean eroeffne(Parzelle stadt) {
        return kasse.eroeffne(konto(stadt));
    }
}
