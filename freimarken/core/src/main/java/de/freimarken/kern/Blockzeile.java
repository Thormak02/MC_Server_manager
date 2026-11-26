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
 * @param vorzustand was an dieser Stelle stand, BEVOR die Tat geschah - als
 *                  Vanilla-Text, etwa
 *                  {@code minecraft:oak_stairs[facing=east,half=bottom]}, oder
 *                  {@code ""}. Genau das stellt ein Rueckweg wieder her, und
 *                  darum ist es der Zustand VOR der Tat und nicht danach: Bei
 *                  einer Setzung ist es das, was ueberbaut wurde (Luft, Wasser,
 *                  Gras), bei einem Abbau und einer Sprengung der Block selbst.
 *                  Was NACH der Tat dasteht, braucht niemand - bei einer Setzung
 *                  steht es in {@code block}
 */
public record Blockzeile(long id, Instant zeitpunkt, UUID spieler, Blockort ort,
                         Blocktat tat, boolean gelungen, String block,
                         String vorzustand) {

    /**
     * Die Nummer einer Zeile, die noch nicht geschrieben ist.
     *
     * <p>Eine 0 ist hier eindeutig und keine Doppelbedeutung: {@code AUTO_INCREMENT}
     * beginnt bei 1, also kann keine geschriebene Zeile diese Nummer tragen. Anders als
     * beim Startwert einer Welt, wo 0 ein gueltiger Wert ist und darum ein
     * {@code Long} stehen muss.
     */
    public static final long OHNE_NUMMER = 0L;

    /**
     * Die Kennung fuer "kein Spieler" - eine Weltursache.
     *
     * <p><b>Warum es sie braucht</b> [26.11.2026]: Mit {@link Blocktat#SPRENGEN}
     * kommen Zeilen ohne Taeter ins Buch. Ein Creeper gehoert niemandem, und ein
     * Bett, das im Nether hochgeht, auch nicht. Die Spalte ist
     * {@code NOT NULL} - also braucht dieser Fall einen EIGENEN Wert, und nicht
     * den Spieler, der zufaellig in der Naehe stand.
     *
     * <p>Die Nullkennung und keine erfundene: Sie ist die einzige, die mit
     * Sicherheit niemandem gehoert - Minecraft vergibt Spielerkennungen aus
     * Zufall oder aus dem Namen, und beide treffen sie nie.
     *
     * <p>Wer sie anzeigt, muss sie UEBERSETZEN. Eine Zeile, die
     * {@code 00000000-0000-0000-0000-000000000000} nennt, beantwortet die Frage
     * "wer war das" schlechter als ein Satz, der sagt: niemand, das war die Welt.
     */
    public static final UUID NIEMAND = new UUID(0L, 0L);

    /** Ob diese Zeile keinem Spieler zuzuordnen ist. */
    public boolean ohneTaeter() {
        return NIEMAND.equals(spieler);
    }

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
        // Leer und nicht null, wie bei jedem Text dieses Projekts, der fehlen
        // DARF: Ein Leser, der gegen null fragt, uebersieht den leeren Fall - und
        // umgekehrt. Hier ist das besonders teuer, denn eine Zeile aus der Zeit vor
        // dem 26.11.2026 hat keinen Zustand, und der Rueckweg muss sie trotzdem
        // lesen koennen.
        vorzustand = vorzustand == null ? "" : vorzustand;
    }

    /** Ob diese Zeile den Vorzustand traegt, den ein Rueckweg braucht. */
    public boolean hatVorzustand() {
        return !vorzustand.isBlank();
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
        return neu(zeitpunkt, spieler, ort, tat, gelungen, block, "");
    }

    /**
     * Dieselbe Zeile MIT Vorzustand - der Weg, den der Rueckweg braucht.
     *
     * <p><b>Zwei Fabrikmethoden und nicht eine mit acht Werten</b> [26.11.2026]:
     * Vierzehn Stellen rufen die kurze, und die meisten von ihnen gehen den
     * Zustand nichts an - eine Probe ueber das Aufraeumen braucht keine Treppe mit
     * Blickrichtung. Die kurze ist damit kein Rueckfall, sondern die Aussage
     * "dieser Zustand ist hier gleichgueltig".
     */
    public static Blockzeile neu(Instant zeitpunkt, UUID spieler, Blockort ort,
                                 Blocktat tat, boolean gelungen, String block,
                                 String vorzustand) {
        return new Blockzeile(OHNE_NUMMER, zeitpunkt, spieler, ort, tat, gelungen,
                block, vorzustand);
    }

    /** Dieselbe Zeile mit der Nummer, die die Ablage vergeben hat. */
    public Blockzeile mitNummer(long vergeben) {
        if (vergeben < 1L) {
            throw new IllegalArgumentException("Eine vergebene Nummer beginnt bei 1: "
                    + vergeben);
        }
        return new Blockzeile(vergeben, zeitpunkt, spieler, ort, tat, gelungen,
                block, vorzustand);
    }
}
