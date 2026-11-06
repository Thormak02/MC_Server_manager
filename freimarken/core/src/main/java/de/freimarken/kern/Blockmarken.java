package de.freimarken.kern;

import java.util.List;

/**
 * Welche Blockpositionen ein Spieler gesetzt hat - der Vertrag, den beide Umsetzungen
 * halten.
 *
 * <p>Vier Zusagen, und jede steht hier, weil ihr Gegenteil eine Gelddruckmaschine ergibt
 * oder eine Marke, die niemand mehr zuordnen kann:
 *
 * <ol>
 *   <li><b>Eine Marke wird gesetzt und NIE geloescht.</b> Das ist die ganze Regel "ein
 *       Block zahlt nur einmal": Eine markierte Position bringt in BEIDE Richtungen
 *       nichts mehr - weder das Abbauen noch ein zweites Setzen. Loeschte das Abbauen die
 *       Marke, waere Setzen-Abbauen-Setzen die Gelddruckmaschine des Baumeisters, und
 *       zwar mit einer einzigen Position.
 *       <p>Dass nur gesetzt und nie geloescht wird, erspart ausserdem das halbe Projekt:
 *       Eine Marke muss keiner Weltregel folgen. Kolben, Wasser, Explosionen und
 *       Fallsand koennen den Block wegnehmen, ohne dass Freimarken es sieht - zurueck
 *       bleibt eine Marke ohne Block. Die kostet einen Spieler hoechstens eine
 *       Auszahlung; sie druckt kein Geld. Das ist die harmlose Richtung, und nur deshalb
 *       braucht dieser Schritt keine Weltregelwache.</li>
 *   <li><b>Die Marke haengt an EINEM Block</b>, nicht an einer Saeule und nicht an einer
 *       Parzelle. Sonst entwertete ein einziger gesetzter Block alles darunter.</li>
 *   <li><b>{@link #markiere} sagt, ob die Marke NEU ist.</b> Daran haengt die Auszahlung
 *       in Teil E: Das erste Setzen zahlt, jedes weitere nicht. Ohne diese Antwort
 *       muesste der Aufrufer vorher fragen und danach schreiben - und zwei Spieler, die
 *       im selben Augenblick dieselbe Position fuellen, bekaemen beide Geld.</li>
 *   <li><b>Ein leerer Anfangszustand ist der RICHTIGE.</b> Es wird nichts nachgetragen:
 *       Was vor dieser Markierung gesetzt wurde, gilt fuer immer als gewachsener Boden.
 *       So steht es im Plan, und es ist dieselbe Linie wie bei der Spielzeit, die auch
 *       keinen Altbestand erfindet.</li>
 * </ol>
 *
 * <p><b>Kein Zeitpunkt und kein Spieler.</b> Die Marke sagt nur "hier hat jemand gesetzt",
 * nicht wer und nicht wann. Das ist Absicht und keine Sparsamkeit: Ein Spielername je
 * Block waere das Logbuch, und das Logbuch ist ein austauschbarer Nachbar, an den sich
 * dieser Code nicht binden darf. Wer die Geschichte will, fragt ihn.
 */
public interface Blockmarken {

    /** Ob an dieser Position schon einmal ein Spieler gesetzt hat. */
    boolean istMarkiert(Blockort ort);

    /**
     * Setzt die Marke.
     *
     * @return {@code true}, wenn sie NEU ist - dann zahlt Teil E. {@code false} heisst:
     *         stand schon da, und es ist nichts geschehen
     */
    boolean markiere(Blockort ort);

    /**
     * Mehrere Positionen auf einmal - fuer ein Bett, eine Tuer, eine grosse Blume.
     *
     * <p>Eine eigene Methode und nicht eine Schleife beim Aufrufer: Die Umsetzung im Chunk
     * kann alle Positionen EINES Abschnitts mit einem Schreibzug erledigen, und ein Bett
     * liegt immer im selben Abschnitt. Der Rueckgabewert ist die Zahl der NEUEN Marken,
     * damit Teil E eine Tuer einmal und nicht zweimal zahlt.
     */
    default int markiereAlle(List<Blockort> orte) {
        if (orte == null) {
            throw new IllegalArgumentException("Nichts zu markieren heisst eine leere"
                    + " Liste, nicht null");
        }
        int neue = 0;
        for (Blockort ort : orte) {
            if (markiere(ort)) {
                neue++;
            }
        }
        return neue;
    }
}
