package de.freimarken.kern;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein Stapel, der im Abholfach liegt - Teil C von Schritt 8.
 *
 * <p>Das Abholfach ist die Antwort auf den einen Satz aus Plan 7.1, der keine Ausnahme
 * kennt: <b>"Der Kisteninhalt wandert in ein Abholfach und wird NIE geloescht."</b> Ein
 * geraeumter Laden darf einen Spieler Geld kosten - die verfallene Kaution ist genau
 * dafuer da -, aber niemals seine Gegenstaende. Wer nach drei Wochen Urlaub
 * zurueckkommt, findet seinen Stand vermietet und seine Ware vollstaendig im Fach.
 *
 * <p><b>Der Zeitpunkt und nicht der Tag</b> - und das ist der Unterschied zum
 * {@link Mietvertrag}, der mit {@link java.time.LocalDate} rechnet. Ein Vertrag ist eine
 * Abrede ueber Tage; ein eingelegter Stapel ist ein Ereignis. Wird dieselbe Truhe zweimal
 * an einem Tag geraeumt, soll die Reihenfolge lesbar bleiben.
 *
 * @param id         die Nummer der Zeile. Sie entsteht in der Ablage und steigt; der
 *                   Spieler nennt sie, wenn er ein einzelnes Stueck holen will
 * @param empfaenger wem der Stapel gehoert. Das ist der frueere Mieter und nicht, wer
 *                   die Ware hineingelegt hat - ein Kunde, der in einer fremden Truhe
 *                   etwas liegen laesst, bekommt es nicht
 * @param herkunft   woher der Stapel kommt, in Worten: "Laden halle-1". Freier Text, weil
 *                   das Fach ab Teil D auch die Freie Truhe bedient - eine Aufzaehlung
 *                   waere dann eine Aenderung an dieser Datei fuer jede neue Quelle
 * @param eingelegt  wann - fuer die Anzeige und fuer die Reihenfolge
 * @param gut        der Stapel selbst
 */
public record Abholstueck(long id, UUID empfaenger, String herkunft, Instant eingelegt,
                          Abholgut gut) {

    /** Was in eine Chatzeile passt, neben Beschriftung und Datum. */
    public static final int LAENGSTE_HERKUNFT = 48;

    public Abholstueck {
        // 0 ist keine Nummer, und das ist mit Absicht hart: Eine Ablage, die beim
        // Schreiben vergisst, eine Nummer zu setzen, liefert sonst ein Fach voller
        // Stuecke mit der Nummer 0 - und der Spieler kann keines davon einzeln holen,
        // weil jede Abfrage nach der 0 alle trifft.
        if (id < 1L) {
            throw new IllegalArgumentException("Ein Abholstueck mit der Nummer " + id
                    + " ist keines - die Nummer entsteht in der Ablage und beginnt bei 1");
        }
        if (empfaenger == null) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Empfaenger ist"
                    + " eines, das niemand holen kann");
        }
        if (herkunft == null || herkunft.isBlank()) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Herkunft ist eines,"
                    + " bei dem der Spieler raten muss, welcher Laden das war");
        }
        herkunft = herkunft.trim();
        if (herkunft.length() > LAENGSTE_HERKUNFT) {
            throw new IllegalArgumentException("Die Herkunft \"" + herkunft + "\" ist "
                    + herkunft.length() + " Zeichen lang - hoechstens "
                    + LAENGSTE_HERKUNFT + " passen in eine Zeile.");
        }
        if (eingelegt == null) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Zeitpunkt ist eines,"
                    + " dessen Reihenfolge niemand kennt");
        }
        if (gut == null) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Stapel ist keines");
        }
    }

    /** Ob dieses Stueck diesem Spieler gehoert. */
    public boolean gehoert(UUID wer) {
        return empfaenger.equals(wer);
    }

    /** Was drin ist, in Worten - der kurze Weg an {@link #gut} vorbei. */
    public String beschriftung() {
        return gut.beschriftung();
    }

    /** Der Stapel als Text, wie die Schicht darueber ihn geschrieben hat. */
    public String inhalt() {
        return gut.inhalt();
    }
}
