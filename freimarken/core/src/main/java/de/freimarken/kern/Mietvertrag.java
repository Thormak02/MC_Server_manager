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
 * <p><b>Die Ausfallzahl steht am Vertrag und nicht im Buchungsbuch</b> [Teil C], und das
 * ist die zweite tragende Entscheidung. Aus dem Buch waere sie zu rechnen - "an wie vielen
 * der letzten Tage fehlt eine Mietzeile" -, und bei Geld ist das Buch sonst immer die
 * Wahrheit. Hier aber nicht: Ein Server, der fuenf Tage aus war, hat fuenf Tage ohne
 * Mietzeile, und die Rechnung aus dem Buch raeumte danach jeden Laden der Halle. Die
 * gezaehlte Zahl entsteht nur, wenn ein Lauf wirklich stattgefunden hat und das Geld
 * wirklich fehlte. Ein Serverausfall kostet damit niemanden seinen Stand.
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
 * @param ausfaelle wie viele Tage die Miete nicht bezahlt werden konnte. Jeder Ausfall
 *                  ist eine Warnung; beim Erreichen von
 *                  {@code laeden.ausfaelle-bis-raeumung} wird geraeumt. Eine erfolgreiche
 *                  Zahlung setzt die Zahl auf 0 zurueck - gezaehlt werden Ausfaelle IN
 *                  FOLGE und nicht die des ganzen Vertrags. Wer einmal knapp war und
 *                  danach wieder zahlt, soll nicht Wochen spaeter dafuer geraeumt werden
 */
public record Mietvertrag(String einheit, UUID mieter, LocalDate beginn,
                          LocalDate laeuftBis, long miete, long kaution, int ausfaelle) {

    /**
     * Ein frischer Vertrag - ohne Ausfaelle.
     *
     * <p>Die Abkuerzung fuer den Abschluss und fuer jede Probe, die sich fuer die
     * Ausfaelle nicht interessiert. <b>Nicht</b> fuer eine Ablage, die eine gespeicherte
     * Zeile zurueckbaut: Die muss den vollen Weg nehmen, sonst setzt sie die Ausfaelle
     * jedes Mal stumm auf 0 - und dann zaehlt nichts jemals hoch, und kein Laden wird je
     * geraeumt. Dagegen steht {@code MietVertrag.ausfaelleUeberlebenDieAblage}.
     */
    public Mietvertrag(String einheit, UUID mieter, LocalDate beginn, LocalDate laeuftBis,
            long miete, long kaution) {
        this(einheit, mieter, beginn, laeuftBis, miete, kaution, 0);
    }

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
        // Negative Ausfaelle waeren ein Guthaben an Tagen, und das gibt es nicht. Eine
        // Obergrenze steht hier NICHT: Die Schranke ist die eingestellte Zahl, und die
        // kann der Betreiber im Betrieb erhoehen - dann stehen Vertraege da, deren
        // Ausfaelle ueber der alten Schranke liegen. Die wirft dann nicht, sie raeumt.
        if (ausfaelle < 0) {
            throw new IllegalArgumentException("Eine Ausfallzahl von " + ausfaelle
                    + " ist keine - 0 heisst: alles bezahlt.");
        }
    }

    /** Was beim Kuendigen und beim Ablaufen zurueckgeht - genau das Hinterlegte. */
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
        // Die Ausfaelle reisen MIT, und das ist gewollt: Verlaengern ist kein Bezahlen.
        // Wer zwei Tage nicht zahlen konnte und dann verlaengert, hat damit keinen Tag
        // gutgemacht - sonst waere das Verlaengern der kostenlose Weg, die Raeumung
        // beliebig weit wegzuschieben, ohne je Miete zu zahlen.
        return new Mietvertrag(einheit, mieter, beginn, neuesEnde, miete, kaution,
                ausfaelle);
    }

    /**
     * Derselbe Vertrag mit einem Ausfall mehr - die Warnung.
     *
     * <p>Ob damit geraeumt wird, entscheidet nicht der Vertrag, sondern
     * {@link Mietregel#raeumungsreif}: Die Schranke steht in der {@code config.yml}, und
     * eine Zahl, die sich selbst fuer zu hoch erklaert, haette sie doppelt.
     */
    public Mietvertrag mitAusfall() {
        return new Mietvertrag(einheit, mieter, beginn, laeuftBis, miete, kaution,
                ausfaelle + 1);
    }

    /**
     * Derselbe Vertrag ohne Ausfaelle - nach einer erfolgreichen Zahlung.
     *
     * <p>Gibt {@code this} zurueck, wenn schon keine da sind. Das ist kein Sparen an
     * Objekten, sondern die Erlaubnis fuer den Aufrufer, den Rueckgabewert auf
     * Gleichheit zu pruefen und den Schreibzug zu sparen: Der haeufigste Tag ist der, an
     * dem alle zahlen, und dann soll der taegliche Lauf nicht jede Zeile der Halle neu
     * schreiben.
     */
    public Mietvertrag ohneAusfaelle() {
        if (ausfaelle == 0) {
            return this;
        }
        return new Mietvertrag(einheit, mieter, beginn, laeuftBis, miete, kaution, 0);
    }

    /** Ob gegen diesen Vertrag Ausfaelle stehen. */
    public boolean inVerzug() {
        return ausfaelle > 0;
    }
}
