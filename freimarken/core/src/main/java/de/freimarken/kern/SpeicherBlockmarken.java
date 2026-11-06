package de.freimarken.kern;

import java.util.HashSet;
import java.util.Set;

/**
 * Die Blockmarken im Arbeitsspeicher - fuer die Proben.
 *
 * <p><b>Anders als bei den uebrigen Ablagen ist das NICHT die fluechtige Betriebsfassung.</b>
 * Es gibt keine fluechtige: Die echte Fassung legt die Marke in den Chunk, und ein Chunk
 * wird mit der Welt gespeichert - auch auf einem Server ohne Datenbank. Die Marken
 * ueberleben den Neustart also immer, und zwar in derselben Datei wie der Block, zu dem sie
 * gehoeren. Genau das ist der Grund fuer diese Wahl: Ein Weltzuruecksetzer und ein Absturz
 * nehmen Block UND Marke mit, eine Marke in der Datenbank liefe auseinander.
 *
 * <p>Diese Fassung hier ist die zweite Umsetzung desselben Vertrags, damit
 * {@code BlockmarkenVertrag} beide prueft - und damit eine Regel aus Teil E ohne Bukkit
 * pruefbar bleibt.
 *
 * <p><b>Eine Menge und keine Zaehlung.</b> Eine Marke hat keinen Zeitpunkt und keinen
 * Spieler; sie ist da oder nicht. Der Rueckgabewert von {@link #markiere} kommt darum
 * geschenkt: {@link Set#add} sagt selbst, ob der Ort neu war.
 */
public final class SpeicherBlockmarken implements Blockmarken {

    private final Set<Blockort> markiert = new HashSet<>();

    @Override
    public synchronized boolean istMarkiert(Blockort ort) {
        return markiert.contains(pflicht(ort));
    }

    @Override
    public synchronized boolean markiere(Blockort ort) {
        return markiert.add(pflicht(ort));
    }

    /** Wie viele Marken stehen - nur fuer die Proben. */
    public synchronized int anzahl() {
        return markiert.size();
    }

    private static Blockort pflicht(Blockort ort) {
        if (ort == null) {
            throw new IllegalArgumentException("Ohne Ort gibt es keine Marke");
        }
        return ort;
    }
}
