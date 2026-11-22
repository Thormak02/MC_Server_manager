package de.freimarken.kern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Was ein Spieler in einem Lauf des Angelwettbewerbs erreicht hat - und was er dafuer
 * gezahlt hat.
 *
 * <p><b>Das EINGEZAHLTE Startgeld steht hier mit drin</b>, und das ist die tragende
 * Entscheidung dieses Teils: Der Topf ist die Summe der Startgelder plus dem Zuschuss, und
 * wer ihn aus "Zahl der Teilnehmer mal Startgeld" rechnet, rechnet falsch, sobald der
 * Betreiber das Startgeld mitten im Lauf aendert ({@code /freimarken neuladen} wirkt
 * sofort). Dann zahlte der Wettbewerb mehr aus, als eingezahlt wurde - oder weniger, und
 * der Rest verschwaende.
 *
 * <p>Eine Zeile entsteht bei der ANMELDUNG, mit 0 Punkten. Das ist auch die Antwort auf
 * "wer macht mit": Wer keine Zeile hat, sammelt keine Punkte - sonst wuerde einem Angler
 * ungefragt Startgeld abgebucht, nur weil er im Fenster an einem Teich stand.
 *
 * @param spieler    wer
 * @param lauf       welcher Lauf - der Tag seines BEGINNS, auch bei einem Fenster, das
 *                   bis in den naechsten Tag reicht
 * @param punkte     seine Punkte, nie negativ
 * @param eingezahlt was er an Startgeld gezahlt hat, nie negativ
 */
public record Punktzeile(UUID spieler, LocalDate lauf, int punkte, long eingezahlt) {

    public Punktzeile {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Punkte");
        }
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Punkte - welcher"
                    + " Lauf gilt, sagt Wettbewerbswerte.laufAm und nicht die Ablage");
        }
        if (punkte < 0) {
            throw new IllegalArgumentException("Punkte koennen nicht negativ sein: "
                    + punkte);
        }
        if (eingezahlt < 0L) {
            throw new IllegalArgumentException("Ein Startgeld kann nicht negativ sein: "
                    + eingezahlt);
        }
    }

    /** Eine frische Anmeldung - noch ohne Punkte. */
    public static Punktzeile angemeldet(UUID spieler, LocalDate lauf, long startgeld) {
        return new Punktzeile(spieler, lauf, 0, startgeld);
    }

    /**
     * Dieselbe Zeile mit mehr Punkten.
     *
     * <p>Laeuft bei {@link Integer#MAX_VALUE} an und nicht darueber: Eine
     * uebergelaufene Zahl waere NEGATIV, und der Konstruktor wuerfe dann mitten im
     * Spiel - bei einem Fang, der nichts kaputtmachen soll.
     */
    public Punktzeile mitMehr(int dazu) {
        if (dazu < 0) {
            throw new IllegalArgumentException("Ein Fang bringt keine negativen Punkte: "
                    + dazu);
        }
        int neu = punkte > Integer.MAX_VALUE - dazu ? Integer.MAX_VALUE : punkte + dazu;
        return new Punktzeile(spieler, lauf, neu, eingezahlt);
    }
}
