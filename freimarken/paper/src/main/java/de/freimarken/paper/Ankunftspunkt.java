package de.freimarken.paper;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Die Standprobe: Wo in dieser Welt kann ein Spieler ankommen, ohne Schaden zu nehmen?
 *
 * <p>Gebraucht, weil der Erscheinungspunkt einer Welt nichts verspricht.
 * {@code World#getSpawnLocation} gibt einen Punkt, nicht festen Boden; nur Minecraft
 * selbst sucht beim Beitritt ein 21x21-Feld ab, ein Teleport aus einem Plugin sucht
 * nichts. Und {@code paper-api} 26.3 hat keine Methode dafuer - gesucht und nicht
 * gefunden.
 *
 * <p>Gemessen am 02.11.2026 in Altfurt: <b>0/0 liegt unter Wasser</b> - Boden auf y 58,
 * Wasser bis y 62. Genau dorthin setzt {@code Weltenbau} bei einer frischen Welt den
 * Erscheinungspunkt, wenn der vorige ausserhalb der Grenze lag. Ohne diese Probe erscheint
 * dort jeder Ankommende unter Wasser.
 *
 * <p>Drei Fragen an die Welt, und die Aufteilung hat einen Grund: Ob ein Block <b>fest</b>
 * ist, weiss nur Bukkits Blockregistrierung - das ist ein Aufruf und steht deshalb im
 * {@link Weltgriff}. Ob er <b>schadet</b>, ist eine Tabelle, und die steht hier, damit sie
 * geprueft werden kann. Die Entscheidung selbst - Ring fuer Ring, Boden, Kopfraum - ist
 * reine Rechnerei und ohne Server pruefbar.
 */
final class Ankunftspunkt {

    /**
     * Was die Probe von einer Welt wissen muss - und nicht mehr.
     *
     * <p>Mit einem {@code World} waere die Probe nur auf einem laufenden Server pruefbar,
     * und dann nicht am Wasserloch, nicht am Hang und nicht auf einer Wiese.
     */
    interface Weltgriff {

        /** Der Block an dieser Stelle. */
        Material bei(int x, int y, int z);

        /**
         * Ob dieser Block Platz einnimmt - ob man auf ihm stehen kann und nicht durch ihn
         * hindurch.
         *
         * <p>Eine Frage an die Blockregistrierung und keine Tabelle: Es gibt tausend
         * Bloecke, und eine Aufzaehlung waere am Tag ihrer Entstehung veraltet.
         */
        boolean fest(int x, int y, int z);

        /**
         * Die Hoehe des hoechsten festen Blocks in dieser Spalte - Fluessigkeit
         * uebergangen.
         */
        int bodenHoehe(int x, int z);
    }

    /** Wo die Fuesse stehen. */
    record Stand(int x, int y, int z) {
    }

    /**
     * Bloecke, an denen ein Ankommender Schaden nimmt oder einsinkt.
     *
     * <p>Bewusst kurz und bewusst unvollstaendig: Eine Tabelle aller schaedlichen Bloecke
     * gaebe es nicht, und eine, die es zu sein vorgibt, waere die schlechtere Antwort.
     * Hier stehen die, die ein Erscheinen sofort verderben - als Boden wie als Kopfraum.
     * Wasser gehoert dazu: Es ist nicht fest, und ohne diesen Eintrag waere der gemessene
     * Fall von Altfurt 0/0 ein gueltiger Ankunftsplatz.
     */
    private static final Set<Material> SCHAEDLICH = EnumSet.of(
            Material.WATER, Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE);

    /** Wie weit die Probe nach aussen sucht, wenn der Wunschpunkt nicht taugt. */
    static final int RINGE = 8;

    private Ankunftspunkt() {
    }

    /** Ob dieser Block ein Erscheinen verdirbt. */
    static boolean schaedlich(Material art) {
        return art != null && SCHAEDLICH.contains(art);
    }

    /**
     * Ein Platz zum Ankommen, so nah am Wunschpunkt wie moeglich.
     *
     * <p>In Ringen nach aussen, damit die Antwort die naechstgelegene ist und nicht die
     * erste, die eine Schleife findet. Wer nichts findet, gibt nichts zurueck - eine
     * geratene Stelle waere schlimmer als eine Absage, weil sie wie ein Erfolg aussieht.
     */
    static Optional<Stand> suche(Weltgriff griff, int wunschX, int wunschZ) {
        for (int ring = 0; ring <= RINGE; ring++) {
            Optional<Stand> gefunden = imRing(griff, wunschX, wunschZ, ring);
            if (gefunden.isPresent()) {
                return gefunden;
            }
        }
        return Optional.empty();
    }

    private static Optional<Stand> imRing(Weltgriff griff, int mitteX, int mitteZ, int ring) {
        if (ring == 0) {
            return inSpalte(griff, mitteX, mitteZ);
        }
        for (int x = mitteX - ring; x <= mitteX + ring; x++) {
            for (int z = mitteZ - ring; z <= mitteZ + ring; z++) {
                // Nur der Rand des Quadrats - das Innere hatten die kleineren Ringe.
                if (Math.abs(x - mitteX) != ring && Math.abs(z - mitteZ) != ring) {
                    continue;
                }
                Optional<Stand> gefunden = inSpalte(griff, x, z);
                if (gefunden.isPresent()) {
                    return gefunden;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Taugt GENAU dieser Ort, ohne Suche?
     *
     * <p>{@code y} ist die Fusshoehe. Drei Bedingungen, und jede hat ein Fehlerbild:
     * fester Boden darunter (sonst faellt der Ankommende), zwei durchquerbare Bloecke ab
     * der Fusshoehe (sonst steckt er in der Wand), und nirgends davon etwas Schaedliches
     * (sonst brennt oder ertrinkt er). Ein Grasbueschel in Fusshoehe ist ausdruecklich
     * erlaubt - sonst taugte keine Wiese.
     *
     * <p>Diese Methode und nicht {@link #suche} ist das Richtige, wenn der Zielort schon
     * feststeht - bei {@code /zurueck} und bei {@code /tpa}. Die ringweise Suche springt
     * auf {@link Weltgriff#bodenHoehe}, und das heisst: Ein Rueckweg aus einer Hoehle
     * endete an der Oberflaeche, und eine Todesstelle neben einem Lavasee im Nether
     * endete auf dem Netherdach. Genau das ist der bei EssentialsX belegte Fehler
     * (Ausgabe 3318, lange als „wontfix" gefuehrt). Wo ein Ort gemeint ist, wird geprueft
     * und abgesagt, nicht gesucht.
     */
    static boolean taugt(Weltgriff griff, int x, int y, int z) {
        if (!griff.fest(x, y - 1, z) || schaedlich(griff.bei(x, y - 1, z))) {
            return false;
        }
        for (int hoehe = 0; hoehe <= 1; hoehe++) {
            if (griff.fest(x, y + hoehe, z) || schaedlich(griff.bei(x, y + hoehe, z))) {
                return false;
            }
        }
        return true;
    }

    /** Taugt die hoechste Stelle dieser Spalte? */
    private static Optional<Stand> inSpalte(Weltgriff griff, int x, int z) {
        int fuesse = griff.bodenHoehe(x, z) + 1;
        return taugt(griff, x, fuesse, z)
                ? Optional.of(new Stand(x, fuesse, z))
                : Optional.empty();
    }

    /**
     * Der Weltgriff einer wirklichen Welt.
     *
     * <p>{@code OCEAN_FLOOR} und nicht {@code MOTION_BLOCKING}: Letzteres zaehlt Wasser
     * mit, und dann waere der „Boden" eines Sees seine Oberflaeche.
     */
    static Weltgriff griffAuf(World welt) {
        return new Weltgriff() {
            @Override
            public Material bei(int x, int y, int z) {
                return welt.getBlockAt(x, y, z).getType();
            }

            @Override
            public boolean fest(int x, int y, int z) {
                return welt.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public int bodenHoehe(int x, int z) {
                return welt.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
            }
        };
    }

    /**
     * Der Platz zum Ankommen in dieser Welt - gemessen, nicht geglaubt.
     *
     * <p>Mittig auf dem Block: Ein Teleport genau auf die Blockkante laesst einen Spieler
     * gelegentlich in der Wand stecken.
     */
    static Optional<Location> fuer(World welt, int wunschX, int wunschZ) {
        return suche(griffAuf(welt), wunschX, wunschZ)
                .map(stand -> new Location(welt, stand.x() + 0.5, stand.y(),
                        stand.z() + 0.5));
    }
}
