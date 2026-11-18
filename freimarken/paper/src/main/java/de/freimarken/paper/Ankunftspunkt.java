package de.freimarken.paper;

import de.freimarken.kern.Quader;
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
     * Wie tief die Suche nach einer Torhoehe geht - tiefer gibt es keine Welt.
     */
    static final int WELTBODEN = -64;

    /**
     * Was an diesem Tor nicht stimmt - oder {@code null}, wenn es begehbar ist.
     *
     * <p><b>Ein Tor ist ein Loch in einer Wand</b>, und darum laeuft hier nicht
     * {@link #suche}: Die springt auf {@link Weltgriff#bodenHoehe} und findet damit den
     * STURZ ueber der Oeffnung, nicht den Boden darin. Am 02.11.2026 hat sie deshalb
     * alle sieben Tore einer gerade gebauten Wand fuer unbegehbar erklaert. Geprueft
     * wird darum Saeule fuer Saeule GENAU an der eingetragenen Hoehe - das ist
     * {@link #taugt}, dieselbe Regel wie bei {@code /zurueck} und {@code /tpa}.
     *
     * <p>Warum hier und nicht im Plugin: Dort war sie privat in {@code FreimarkenPlugin}
     * und ohne laufenden Server zu keiner Probe zu bringen - der Fund vom 18.11.2026
     * (siehe {@link #vorschlag}) ist genau in dieser blinden Ecke entstanden.
     */
    static String torklage(Weltgriff griff, Quader tor) {
        for (int x = tor.vonX(); x <= tor.bisX(); x++) {
            for (int z = tor.vonZ(); z <= tor.bisZ(); z++) {
                if (!griff.fest(x, tor.vonY() - 1, z)) {
                    return "unter " + x + "/" + tor.vonY() + "/" + z + " ist kein Boden -"
                            + " wer hier durchgeht, faellt. " + vorschlag(griff, tor);
                }
                // Der Boden selbst darf nicht schaden. Ohne diese Frage waere ein Tor
                // auf einem Magmablock klaglos, und der Vorschlag darunter wuerde eine
                // Hoehe nennen, die diese Pruefung gerade noch durchgelassen hat -
                // zwei Regeln fuer dieselbe Sache, die sich widersprechen.
                if (schaedlich(griff.bei(x, tor.vonY() - 1, z))) {
                    return "unter " + x + "/" + tor.vonY() + "/" + z + " liegt "
                            + griff.bei(x, tor.vonY() - 1, z) + " - wer hier durchgeht,"
                            + " nimmt Schaden. " + vorschlag(griff, tor);
                }
                for (int hoehe = 0; hoehe <= 1 && tor.vonY() + hoehe <= tor.bisY(); hoehe++) {
                    int y = tor.vonY() + hoehe;
                    if (griff.fest(x, y, z)) {
                        // Hier KEIN Vorschlag mit Koordinaten: Der Boden darunter traegt
                        // ja, also nennt die Suche dieselbe Hoehe wieder - ein Rat, der
                        // ins Leere fuehrt. Zu tun ist etwas anderes.
                        return "bei " + x + "/" + y + "/" + z + " steht "
                                + griff.bei(x, y, z) + " im Tor. Die Oeffnung muss frei"
                                + " sein - entweder die Bloecke entfernen oder den Quader"
                                + " dorthin legen, wo der Durchgang ist.";
                    }
                    if (schaedlich(griff.bei(x, y, z))) {
                        return "bei " + x + "/" + y + "/" + z + " steht "
                                + griff.bei(x, y, z) + " im Tor.";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Welche Hoehe das Tor haette haben sollen - oder dass es keine gibt.
     *
     * <p><b>GEMESSEN AM 18.11.2026, und das war ein Fehler mit Ansage:</b> Vorher suchte
     * diese Methode nur den ersten festen Block UNTER der eingetragenen Fusshoehe, und
     * zwar in der einen Saeule, die gerade aufgefallen war. Im heutigen Altfurt liegt
     * die ganze Torreihe im Meer - Boden auf y 51, Wasser darueber. Der Rat lautete
     * "Passend waere: von [86, 51, 0] bis [88, 53, 0]", und wer ihn befolgte, bekam beim
     * naechsten Start die naechste Klage: "bei 86/51/0 steht WATER im Tor." Ein
     * Vorschlag, der in die naechste Klage fuehrt, ist schlimmer als keiner, weil er wie
     * eine Antwort aussieht.
     *
     * <p>Darum wird jetzt die GANZE Oeffnung gefragt, mit derselben Regel, an der sie
     * nachher gemessen wird ({@link #taugt} fuer jede Saeule): Boden darunter, Fuss und
     * Kopf frei und nichts Schaedliches. Wo keine solche Hoehe liegt, sagt der Satz das -
     * eine Zahl zu nennen waere geraten.
     */
    private static String vorschlag(Weltgriff griff, Quader tor) {
        for (int y = tor.vonY(); y > WELTBODEN; y--) {
            if (oeffnungTaugt(griff, tor, y)) {
                return "Passend waere: von [" + tor.vonX() + ", " + y + ", " + tor.vonZ()
                        + "] bis [" + tor.bisX() + ", " + (y + 2) + ", " + tor.bisZ() + "]";
            }
        }
        return "Unter dem Tor liegt bis zum Weltboden keine Hoehe, auf der die ganze"
                + " Oeffnung frei und trocken waere - hier muss gebaut werden.";
    }

    /** Ob die ganze Oeffnung auf dieser Fusshoehe begehbar ist - jede Saeule. */
    private static boolean oeffnungTaugt(Weltgriff griff, Quader tor, int fussHoehe) {
        for (int x = tor.vonX(); x <= tor.bisX(); x++) {
            for (int z = tor.vonZ(); z <= tor.bisZ(); z++) {
                if (!taugt(griff, x, fussHoehe, z)) {
                    return false;
                }
            }
        }
        return true;
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
