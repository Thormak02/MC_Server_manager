package de.freimarken.kern;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Ein Mietvertrag ueber eine Ladeneinheit - Teil B von Schritt 8.
 *
 * <p><b>Der Vertrag traegt SEINE EIGENE Miete.</b> Das ist die tragende Entscheidung
 * dieses Teils, und sie kommt aus Plan 1943: Die Ladenmiete ist "anpassbar mit
 * Bestandsschutz" - eine Erhoehung gilt nur fuer NEUE Vertraege. Stuende die Miete nur in
 * der {@code config.yml}, traefe jede Aenderung sofort jeden laufenden Mieter, und zwar
 * stumm: Er hat fuer 500 am Tag gemietet und zahlt ploetzlich 2.000.
 *
 * <p><b>Der Tag und nicht der Zeitpunkt</b> - wie beim Tagesbonus und aus demselben Grund
 * (Plan 8.8): Gespeichert wird je Zeile ein Datum. Die Tagesgrenze ist eine eingestellte
 * Zone, und ein Zeitpunkt haette eine zweite daneben.
 *
 * <p>Was NICHT hier steht: die Zahl der Warnungen nach einem Zahlungsausfall. Die gehoert
 * zum taeglichen Lauf und kommt mit Teil C - samt ihrer eigenen Spalte. Eine Zahl, die
 * heute immer 0 waere, ist eine Zusage ohne Inhalt.
 *
 * @param einheit   der Name der Ladeneinheit - klein, wie in der {@code config.yml}
 * @param mieter    wem der Laden gehoert
 * @param beginn    der Tag des Abschlusses, in der eingestellten Zone
 * @param laeuftBis der letzte Tag, an dem der Vertrag gilt - EINGESCHLOSSEN. Am Tag
 *                  danach ist er abgelaufen
 * @param miete     die vereinbarte Miete je Tag, in ganzen Mark. Eingefroren beim
 *                  Abschluss; siehe oben
 * @param kaution   was beim Abschluss WIRKLICH hinterlegt wurde, in ganzen Mark. Auch
 *                  eingefroren, und aus einem zweiten Grund: Bei einer Raeumung wegen
 *                  Zahlungsausfall VERFAELLT die Kaution, ohne zurueckgezahlt zu werden.
 *                  Waere sie nur aus dem Buchungsbuch zu rechnen (eingezahlt minus
 *                  zurueckgezahlt), bliebe diese Differenz stehen - und beim naechsten
 *                  ordentlichen Kuendigen bekaeme der Spieler die verfallene Kaution mit
 *                  heraus. 0 ist erlaubt und heisst: ohne Kaution gemietet
 */
public record Mietvertrag(String einheit, UUID mieter, LocalDate beginn,
                          LocalDate laeuftBis, long miete, long kaution) {

    public Mietvertrag {
        if (einheit == null || einheit.isBlank()) {
            throw new IllegalArgumentException("Ein Mietvertrag ohne Ladeneinheit ist"
                    + " keiner");
        }
        einheit = einheit.trim().toLowerCase(Locale.ROOT);
        if (mieter == null) {
            throw new IllegalArgumentException("Ein Mietvertrag ohne Mieter ist keiner");
        }
        if (beginn == null || laeuftBis == null) {
            throw new IllegalArgumentException("Ein Mietvertrag braucht Beginn und Ende -"
                    + " ohne sie ist er nicht abzulaufen und nicht zu verlaengern");
        }
        // Ein Ende VOR dem Beginn waere ein Vertrag, der am Tag seines Abschlusses schon
        // abgelaufen ist. Der faellt hier auf und nicht im taeglichen Lauf, wo er einen
        // Laden raeumen wuerde, den gerade jemand bezahlt hat.
        if (laeuftBis.isBefore(beginn)) {
            throw new IllegalArgumentException("Der Vertrag ueber \"" + einheit
                    + "\" laeuft bis " + laeuftBis + " und beginnt erst am " + beginn
                    + " - am Tag des Abschlusses waere er schon abgelaufen.");
        }
        if (miete < 1L) {
            throw new IllegalArgumentException("Eine Miete von " + miete + " ist keine."
                    + " Wer Laeden verschenken will, setzt die Miete in der config.yml"
                    + " niedrig - eine 0 waere ein Vertrag, den der taegliche Lauf nie"
                    + " beruehrt.");
        }
        // Die Kaution darf 0 sein - "ohne Kaution" ist eine Entscheidung des Betreibers.
        // Negativ waere sie eine Rueckzahlung, und die ist keine Hinterlegung.
        if (kaution < 0L) {
            throw new IllegalArgumentException("Eine Kaution von " + kaution
                    + " ist keine. 0 heisst: ohne Kaution gemietet.");
        }
    }

    /** Was bei ordentlicher Kuendigung zurueckgeht - genau das Hinterlegte. */
    public boolean mitKaution() {
        return kaution > 0L;
    }

    /** Ob dieser Vertrag diesem Spieler gehoert. */
    public boolean gehoert(UUID wer) {
        return mieter.equals(wer);
    }

    /**
     * Derselbe Vertrag mit einem neuen Ende - das Verlaengern.
     *
     * <p>Miete UND Kaution bleiben die vereinbarten. Eine Verlaengerung ist kein neuer Vertrag: Wer
     * fuer 500 gemietet hat, verlaengert fuer 500, auch wenn in der Datei inzwischen
     * 2.000 steht. Wer das anders will, kuendigt und mietet neu - dann gilt der neue
     * Satz, und das ist seine Entscheidung.
     */
    public Mietvertrag bis(LocalDate neuesEnde) {
        return new Mietvertrag(einheit, mieter, beginn, neuesEnde, miete, kaution);
    }
}
