package de.freimarken.kern;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Zeile im Blockbuch: wer wann wo was getan hat - und ob es gelungen ist.
 *
 * <p><b>{@link #gelungen} ist das Feld, das dieses Buch von einem Protokoll
 * unterscheidet.</b> Ein Buch, das nur die gelungenen Taten kennt, beantwortet die
 * Moderationsfrage nicht: Wer wissen will, ob jemand an einer fremden Parzelle
 * herumprobiert hat, sucht genau die ABGEWIESENEN Versuche - ein Dutzend davon in einer
 * Minute ist der Fund, und zwar einer, bei dem gar nichts passiert ist. Darum traegt die
 * Zeile Tat UND Ausgang und nicht eine vierte Tat namens "abgewiesen", die nicht mehr
 * sagen koennte, WAS abgewiesen wurde.
 *
 * <p><b>Der Block steht als NAME darin</b> ({@code oak_door}, {@code chest}) und nicht
 * als Zahl: Eine Materialnummer waere ohne das Spiel nicht lesbar, und die Nummern
 * verschieben sich zwischen Spielfassungen. Derselbe Weg wie in der Lohnliste, und
 * dieselbe Begruendung wie bei {@link Blockort#welt()} - eine Zeile in der Datenbank soll
 * sich ohne Uebersetzungstabelle lesen lassen.
 *
 * @param id        die Nummer, die die Ablage vergibt, oder {@link #OHNE_NUMMER}, solange
 *                  die Zeile nur im Arbeitsspeicher steht
 * @param zeitpunkt wann - in UTC, wie ueberall in diesem Projekt
 * @param spieler   wer
 * @param ort       wo
 * @param tat       was
 * @param gelungen  ob es durchging; {@code false} heisst, ein Schutz hat abgebrochen
 * @param block     der Name des Blocks, kleingeschrieben
 */
public record Blockzeile(long id, Instant zeitpunkt, UUID spieler, Blockort ort,
                         Blocktat tat, boolean gelungen, String block) {

    /**
     * Die Nummer einer Zeile, die noch nicht geschrieben ist.
     *
     * <p>Eine 0 ist hier eindeutig und keine Doppelbedeutung: {@code AUTO_INCREMENT}
     * beginnt bei 1, also kann keine geschriebene Zeile diese Nummer tragen. Anders als
     * beim Startwert einer Welt, wo 0 ein gueltiger Wert ist und darum ein
     * {@code Long} stehen muss.
     */
    public static final long OHNE_NUMMER = 0L;

    public Blockzeile {
        if (id < OHNE_NUMMER) {
            throw new IllegalArgumentException("Eine Zeilennummer ist nie negativ: " + id);
        }
        if (zeitpunkt == null) {
            throw new IllegalArgumentException("Eine Blockzeile ohne Zeitpunkt - genau"
                    + " der ist die Frage, die an sie gestellt wird");
        }
        if (spieler == null) {
            throw new IllegalArgumentException("Eine Blockzeile ohne Spieler");
        }
        if (ort == null) {
            throw new IllegalArgumentException("Eine Blockzeile ohne Ort");
        }
        if (tat == null) {
            throw new IllegalArgumentException("Eine Blockzeile ohne Tat");
        }
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Eine Blockzeile ohne Block - auch Luft"
                    + " hat einen Namen");
        }
    }

    /**
     * Eine Zeile, wie sie im Ereignis entsteht - noch ohne Nummer.
     *
     * <p>Die Nummer vergibt die Ablage beim Schreiben. Dass die Wache sie nicht erfindet,
     * ist mehr als Ordnung: Zwei Wachen, die beide zaehlen, vergeben dieselbe Nummer
     * zweimal, und dann holt eine Abfrage nach Nummer die falsche Zeile.
     */
    public static Blockzeile neu(Instant zeitpunkt, UUID spieler, Blockort ort,
                                 Blocktat tat, boolean gelungen, String block) {
        return new Blockzeile(OHNE_NUMMER, zeitpunkt, spieler, ort, tat, gelungen, block);
    }

    /** Dieselbe Zeile mit der Nummer, die die Ablage vergeben hat. */
    public Blockzeile mitNummer(long vergeben) {
        if (vergeben < 1L) {
            throw new IllegalArgumentException("Eine vergebene Nummer beginnt bei 1: "
                    + vergeben);
        }
        return new Blockzeile(vergeben, zeitpunkt, spieler, ort, tat, gelungen, block);
    }
}
