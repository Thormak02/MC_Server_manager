package de.freimarken.paper;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;

/**
 * Setzt die Platzhalterbauten in Altfurt - die Hand, die dort die Haeuser macht.
 *
 * <p>Bis zum 23.11.2026 war das Handarbeit, und TODO.md sagte es woertlich: "Programm
 * setzt keine Bloecke in Altfurt". Der Mangel daran ist nicht die Muehe, sondern die
 * Haltbarkeit: Ein von Hand gesetztes Haus ueberlebt keinen Weltverlust, und keine Probe
 * haelt, dass der Quader in der {@code config.yml} zu dem passt, was wirklich steht.
 *
 * <p><b>Geschrieben wird der GANZE Quader, auch die Luft darin</b> - dieselbe tragende
 * Entscheidung wie bei {@code Wolkenaufbau}: Damit sind Aufsetzen und Zuruecksetzen
 * dieselbe Hand. Wer nur die Schale setzte und das Innere ausliesse, hinterliesse beim
 * zweiten Lauf, was vorher dort stand - und ein Haus saehe anders aus als das andere.
 *
 * <p>Die Bloecke gehen OHNE Physik in die Welt ({@code Weltgriff#setze}): Bei neun
 * Haeusern sind das fast sechstausend Bloecke, und wenn jeder davon seine Nachbarn
 * benachrichtigt, steht der Server.
 *
 * <p>Der {@code Weltgriff} kommt JE AUFRUF und nicht als Feld - wie bei
 * {@code Wolkenaufbau}: Die Welt kann beim Start gefehlt haben und spaeter da sein, und
 * ein gemerkter Griff auf eine entladene Welt waere ein Griff ins Leere.
 */
final class Altfurtaufbau {

    /**
     * Tuer und Schild setzen - die zweite Bukkit-Naht dieser Klasse.
     *
     * <p>Getrennt vom {@link Weltgriff}, weil es etwas anderes ist: Jener setzt einen
     * Block, dies setzt einen Block MIT DATEN und beschreibt ein Blockwesen. Eine Tuer
     * traegt eine Richtung, eine Haelfte und ein Scharnier; ein Schild traegt Zeilen.
     *
     * <p>Und sie MUSS eine Naht sein - dieselbe Lage wie beim {@code Truhengriff} der
     * Wolkeninsel: {@code Material.createBlockData()} geht ueber
     * {@code Bukkit.getServer()} und wirft ohne laufenden Server. Waere sie keine Naht,
     * liesse sich das Setzen von keiner Probe durchlaufen.
     *
     * <p>Hereingegeben werden eine {@link Richtung}, ein {@link Material} und ZEILEN
     * als Text - keine Blockdaten und keine fertigen Bauteile. Dieselbe Linie wie beim
     * {@code Truhengriff}, der Namen und Anzahlen nimmt: Alles andere zoege Bukkit in
     * die Geometrie des Kerns.
     */
    interface Feingriff {

        /**
         * Setzt eine Tuer: BEIDE Haelften, bei {@code y} die untere.
         *
         * <p>Zwei Bloecke und nicht einer: Eine halbe Tuer ist nichts, was Minecraft
         * stehen laesst. Ohne Physik, wie alles hier - mit Nachbarschaftsmeldungen fiele
         * sie, noch waehrend die Wand daneben entsteht.
         */
        void setzeTuer(int x, int y, int z, Richtung zeigt, Material art);

        /** Haengt ein Wandschild auf und beschreibt seine Vorderseite. */
        void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                List<String> zeilen);

        /** Der Griff auf die echte Welt. */
        record AufDerWelt(World welt) implements Feingriff {

            @Override
            public void setzeTuer(int x, int y, int z, Richtung zeigt, Material art) {
                // JE HAELFTE EIN EIGENES Blockdatum und nicht zweimal dasselbe Objekt:
                // setBlockData kopiert nicht zwingend, und eine geteilte Fassung haette
                // am Ende zwei obere Haelften.
                BlockData unten = haelfte(art, zeigt, Bisected.Half.BOTTOM);
                BlockData oben = haelfte(art, zeigt, Bisected.Half.TOP);
                if (unten == null || oben == null) {
                    // Kein Wurf: Der Leser hat den Block schon geprueft und beklagt. Das
                    // Haus steht dann, nur die Tuer nicht - und ein Wurf hier liesse den
                    // Betreiber mit einem halben Altfurt und einer Ausnahme im Protokoll
                    // stehen.
                    return;
                }
                welt.getBlockAt(x, y, z).setBlockData(unten, false);
                welt.getBlockAt(x, y + 1, z).setBlockData(oben, false);
            }

            private static BlockData haelfte(Material art, Richtung zeigt,
                    Bisected.Half haelfte) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof Door tuer)) {
                    return null;
                }
                tuer.setFacing(blockseite(zeigt));
                tuer.setHalf(haelfte);
                // LINKS und zu: Ein Platzhalter mit offener Tuer sieht aus wie einer, in
                // dem jemand war.
                tuer.setHinge(Door.Hinge.LEFT);
                tuer.setOpen(false);
                return tuer;
            }

            @Override
            public void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                    List<String> zeilen) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof WallSign wandschild)) {
                    return;
                }
                wandschild.setFacing(blockseite(zeigt));
                Block block = welt.getBlockAt(x, y, z);
                block.setBlockData(wandschild, false);
                if (!(block.getState() instanceof Sign schild)) {
                    // Kann nur vorkommen, wenn zwischen dem Setzen und dieser Zeile etwas
                    // anderes dort steht - dieselbe Lage wie bei der Truhe der Insel.
                    return;
                }
                var seite = schild.getSide(Side.FRONT);
                for (int zeile = 0; zeile < zeilen.size() && zeile < SCHILDZEILEN;
                        zeile++) {
                    seite.line(zeile, Component.text(zeilen.get(zeile)));
                }
                // force=true, applyPhysics=false: Das Schild haengt an der Wand, und eine
                // Physikmeldung liesse es fallen, bevor der Nachbarblock steht.
                schild.update(true, false);
            }

            /** Die Bukkit-Seite zu einer Richtung - die EINE Stelle dieser Zuordnung. */
            private static BlockFace blockseite(Richtung zeigt) {
                return switch (zeigt) {
                    case NORD -> BlockFace.NORTH;
                    case OST -> BlockFace.EAST;
                    case SUED -> BlockFace.SOUTH;
                    case WEST -> BlockFace.WEST;
                };
            }
        }
    }

    /** So viele Zeilen traegt ein Schild - mehr nimmt Minecraft nicht an. */
    static final int SCHILDZEILEN = 4;

    private final Baustoffe stoffe;

    Altfurtaufbau(Baustoffe stoffe) {
        if (stoffe == null) {
            throw new IllegalArgumentException("Ohne Baustoffe gibt es keinen Bau");
        }
        this.stoffe = stoffe;
    }

    Baustoffe stoffe() {
        return stoffe;
    }

    /**
     * Ob dieser Bau schon steht - gefragt an EINEM Block.
     *
     * <p>Gefragt wird die SOCKELecke eine Ebene ueber dem Boden und NICHT der Boden selbst:
     * Altfurt ist eine Ebene, dort liegt auf Bodenhoehe ueberall schon etwas. Der Boden
     * koennte also nie zwischen "gebaut" und "unberuehrt" unterscheiden, die Wand sehr
     * wohl - ueber dem Boden einer Ebene ist Luft.
     *
     * <p><b>Es ist EIN Block, und das ist die Grenze dieser Frage</b>: Ein Haus, dem
     * jemand die Rueckwand abgetragen hat, gilt hier als stehend. Das ist die gewollte
     * Richtung - der naechste Start soll nicht ueber die Einrichtung eines Betreibers
     * hinwegbauen. Wer neu setzen will, nimmt diesen einen Block weg.
     */
    boolean stehtSchon(Platzhalterbau bau, Weltgriff griff) {
        if (bau == null || griff == null) {
            throw new IllegalArgumentException("Ohne Bau und Welt gibt es keine Antwort");
        }
        Quader bereich = bau.bereich();
        return griff.bei(bereich.vonX(), bereich.vonY() + 1, bereich.vonZ())
                == stoffe.block(bau.stil(), bau.dach(), Bauplan.Stueck.SOCKEL);
    }

    /**
     * Setzt diesen Bau - den ganzen Quader, Luft eingerechnet, dann Tuer und Schild.
     *
     * <p><b>Die Reihenfolge ist Teil der Sache</b>: Erst die Schale, dann die Tuer, dann
     * das Schild. Die Schale schreibt an der Tuerstelle Luft und an der Schildstelle
     * auch - wer die Tuer vorher setzte, haette sie danach weggeraeumt. Dieselbe
     * Begruendung wie bei der Truhe der Wolkeninsel: "Die Truhe wird erst zur Truhe, wenn
     * ihr Block steht."
     *
     * @param zeilen was auf dem Schild steht - leer heisst: kein Schild
     * @return wie viele Bloecke gesetzt wurden; Tuer und Schild zaehlen mit
     */
    int setze(Platzhalterbau bau, Weltgriff griff, Feingriff fein,
            List<String> zeilen) {
        if (bau == null || griff == null || fein == null) {
            throw new IllegalArgumentException("Ohne Bau, Welt und Feingriff gibt es"
                    + " nichts zu setzen");
        }
        Quader bereich = bau.bereich();
        int gesetzt = 0;
        for (int x = bereich.vonX(); x <= bereich.bisX(); x++) {
            for (int z = bereich.vonZ(); z <= bereich.bisZ(); z++) {
                for (int y = bereich.vonY(); y <= bereich.bisY(); y++) {
                    Material block = stoffe.block(bau.stil(), bau.dach(),
                            Bauplan.stueckBei(bau, x, y, z));
                    griff.setze(x, y, z, block);
                    gesetzt++;
                }
            }
        }
        Bauplan.Stelle tuer = Bauplan.tuer(bau);
        if (tuer != null && stoffe.tuer() != Material.AIR) {
            fein.setzeTuer(tuer.x(), tuer.y(), tuer.z(), tuer.zeigt(), stoffe.tuer());
            gesetzt += 2;
        }
        Bauplan.Stelle schild = Bauplan.schild(bau);
        if (schild != null && !zeilen.isEmpty() && stoffe.schild() != Material.AIR) {
            fein.setzeSchild(schild.x(), schild.y(), schild.z(), schild.zeigt(),
                    stoffe.schild(), zeilen);
            gesetzt++;
        }
        return gesetzt;
    }
}
