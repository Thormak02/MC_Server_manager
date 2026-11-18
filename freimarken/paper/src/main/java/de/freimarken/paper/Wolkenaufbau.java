package de.freimarken.paper;

import de.freimarken.kern.Wolkenplan;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

/**
 * Setzt eine Startinsel in die Leere - die Hand, die in Wolkenwerder das Gelaende macht.
 *
 * <p>In jeder anderen Welt dieses Projekts tut das der Weltgenerator. Hier kann er es
 * nicht, und der Grund steht im {@link Wolkenplan}: Ein {@code ChunkData} traegt keine
 * Blockwesen, und eine Truhe mit Inhalt ist der Anfang des ganzen Spiels. Also setzt das
 * Plugin die Insel in dem Augenblick, in dem jemand sie nimmt.
 *
 * <p><b>Geschrieben wird der GANZE Quader, auch die Luft darin</b>, und das ist die
 * tragende Entscheidung dieser Klasse: Damit ist Aufsetzen und Zuruecksetzen dieselbe
 * Hand. Wer nur die Insel setzte und die Luft ausliesse, hinterliesse beim Zuruecksetzen
 * die Reste des alten Baumes - und die eine Insel saehe anders aus als die andere.
 *
 * <p>Zehn mal neun mal neun sind 810 Bloecke, und sie gehen OHNE Physik in die Welt
 * ({@link Weltgriff#setze}): Mit Nachbarschaftsmeldungen fiele der Sand, floesse das
 * Wasser und das Laub verschwaende, noch waehrend gesetzt wird.
 */
final class Wolkenaufbau {

    /**
     * Die Truhe fuellen - die eine Bukkit-Naht dieser Klasse.
     *
     * <p>Getrennt vom {@link Weltgriff}, weil es etwas anderes ist: Jener setzt Bloecke,
     * dies fuellt ein Blockwesen. Und sie MUSS eine Naht sein - {@code new ItemStack(...)}
     * wirft ohne laufenden Server (es greift in das Gegenstandsverzeichnis), und damit
     * waere das Aufsetzen von keiner Probe durchlaufbar. Dieselbe Lage wie beim Stapelbau
     * der {@link Angelwache}.
     *
     * <p>Hereingegeben werden NAMEN und Anzahlen und keine Gegenstaende - alles andere
     * zoege Bukkit in den Kern dieser Rechnung.
     */
    interface Truhengriff {

        /**
         * Legt diese Gegenstaende in die Truhe an dieser Stelle.
         *
         * <p>Ersetzend: Was vorher darin lag, ist danach weg. Das ist beim Zuruecksetzen
         * der Sinn, und beim Aufsetzen liegt ohnehin nichts darin.
         */
        void fuelle(int x, int y, int z, Map<String, Integer> gut);

        /** Der Griff auf die echte Welt. */
        record AufDerWelt(World welt) implements Truhengriff {

            @Override
            public void fuelle(int x, int y, int z, Map<String, Integer> gut) {
                if (!(welt.getBlockAt(x, y, z).getState() instanceof Container truhe)) {
                    // Kann nur vorkommen, wenn zwischen dem Setzen des Blocks und dieser
                    // Zeile etwas anderes dort steht. Kein Wurf: Die Insel ist dann da,
                    // nur die Truhe nicht - und ein Wurf hier liesse den Spieler mit
                    // einer halben Insel und einer Ausnahme im Protokoll stehen.
                    return;
                }
                var inhalt = truhe.getInventory();
                inhalt.clear();
                // SORTIERT, und das ist kein Schmuck: Map.copyOf sagt keine Reihenfolge
                // zu, und sie ist je JVM-Lauf eine andere. Ohne das Sortieren laege das
                // Eis heute auf Platz eins und morgen auf Platz sieben - und "jede Insel
                // ist dieselbe Insel" waere nach jedem Neustart ein wenig unwahrer.
                for (var zeile : new TreeMap<>(gut).entrySet()) {
                    Material art = Material.getMaterial(
                            zeile.getKey().trim().toUpperCase(Locale.ROOT));
                    if (art == null) {
                        // Der Leser hat jeden Namen schon geprueft und beklagt; hier ist
                        // nichts mehr zu melden, und ein Wurf kostete die ganze Truhe.
                        continue;
                    }
                    inhalt.addItem(new ItemStack(art, zeile.getValue()));
                }
                truhe.update();
            }
        }
    }

    private final Wolkenplan plan;

    /**
     * @param plan die Geometrie - dieselbe, die der Weltgenerator und das Buch benutzen
     */
    Wolkenaufbau(Wolkenplan plan) {
        this.plan = plan;
    }

    /** Die Geometrie, nach der gesetzt wird. */
    Wolkenplan plan() {
        return plan;
    }

    /**
     * Setzt eine Insel auf dieses Feld.
     *
     * @param feldX   die Feldnummer, nicht die Blockkoordinate
     * @param feldZ   die Feldnummer
     * @param insel   welche der beiden Inseln - Startinsel oder Ankunftsplattform
     * @param inhalt  was in die Starttruhe gehoert; bei einer Insel ohne Truhe unbenutzt
     * @param griff   die Welt, in der gesetzt wird - JE AUFRUF und nicht als Feld: Sie
     *                kann beim Start gefehlt haben und spaeter da sein, und ein
     *                gemerkter Griff auf eine entladene Welt waere ein Griff ins Leere
     * @param truhen  die Hand, die die Truhe fuellt - aus derselben Welt
     * @return wie viele Bloecke gesetzt wurden - die ganze Spanne, Luft eingerechnet
     */
    int setze(int feldX, int feldZ, Wolkenplan.Insel insel, Map<String, Integer> inhalt,
              Weltgriff griff, Truhengriff truhen) {
        int mitteX = plan.feldMitte(feldX);
        int mitteZ = plan.feldMitte(feldZ);
        int halb = plan.plattformHalb();
        int gesetzt = 0;
        int truheX = 0;
        int truheY = 0;
        int truheZ = 0;
        boolean truheGesetzt = false;
        for (int dx = -halb; dx <= halb; dx++) {
            for (int dz = -halb; dz <= halb; dz++) {
                for (int dy = -plan.tiefeUnterWiese(); dy <= plan.hoeheUeberWiese();
                        dy++) {
                    Wolkenplan.Stueck stueck = plan.stueckBei(insel, dx, dy, dz);
                    int x = mitteX + dx;
                    int y = Wolkenboden.BODEN + dy;
                    int z = mitteZ + dz;
                    griff.setze(x, y, z, Wolkenboden.block(insel, stueck));
                    gesetzt++;
                    if (stueck == Wolkenplan.Stueck.TRUHE) {
                        // Gemerkt und nicht gleich gefuellt: Die Truhe wird erst zur
                        // Truhe, wenn ihr Block steht - und dieser Durchgang setzt noch
                        // Bloecke neben ihr.
                        truheX = x;
                        truheY = y;
                        truheZ = z;
                        truheGesetzt = true;
                    }
                }
            }
        }
        if (truheGesetzt) {
            truhen.fuelle(truheX, truheY, truheZ, inhalt);
        }
        return gesetzt;
    }

    /** Wo ein Spieler auf dieser Insel stehen soll - mitten auf der Wiese. */
    int mitteX(int feldX) {
        return plan.feldMitte(feldX);
    }

    /** Wo ein Spieler auf dieser Insel stehen soll - mitten auf der Wiese. */
    int mitteZ(int feldZ) {
        return plan.feldMitte(feldZ);
    }

    /** Die Hoehe, auf der er dabei steht - ein Block ueber der Wiese. */
    static int stehhoehe() {
        return Wolkenboden.BODEN + 1;
    }
}
