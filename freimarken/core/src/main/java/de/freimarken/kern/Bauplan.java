package de.freimarken.kern;

/**
 * Welche Stelle eines Platzhalterbaus was wird - die Regel, und zwar ohne Bukkit.
 *
 * <p>Hier steht die Geometrie und kein Block: Welcher {@code Material}-Wert zu einem
 * {@link Stueck} gehoert, sagt die Bauhand im Plugin. Dieselbe Trennung wie bei
 * {@link Wolkenplan} und {@code Wolkenboden} - und sie ist der Grund, warum diese Regel
 * ohne laufenden Server zu pruefen ist.
 *
 * <p><b>Gefragt wird mit ABSOLUTEN Koordinaten, und eine Stelle ausserhalb ist ein
 * Programmfehler</b>: Wer den Quader abschreitet, kommt nie daneben. Ein {@code LUFT} als
 * Antwort auf eine Stelle ausserhalb waere die bequeme Luege - sie machte einen Zaehler um
 * eins daneben unsichtbar, und genau dieser Fehler ist in diesem Haus dreimal in einer
 * Spaltenlogik gesessen (siehe {@code Weltgriff}).
 *
 * <p><b>Die TUER gewinnt ueber die WAND</b>, denn sie liegt in ihr. Die Reihenfolge der
 * Fragen ist damit Teil der Regel und nicht Geschmack.
 *
 * <p><b>Die Tuer ist hoechstens zwei Bloecke hoch, aber nie bis ins Dach</b>: Bei einem Bau
 * von genau drei Bloecken Hoehe sind Boden, eine Reihe Wand und Dach - eine zwei Bloecke
 * hohe Oeffnung frasse die Dachkante. Dann ist die Tuer einen Block hoch, und das ist
 * richtig: Ein Haus, das man nur geduckt betritt, ist besser als eines mit einem Loch im
 * Dach.
 */
public final class Bauplan {

    /** Was an einer Stelle des Quaders steht. */
    public enum Stueck {
        /** Die unterste Ebene - sie liegt auf dem Boden der Ebene. */
        BODEN,
        /** Die Aussenwand zwischen Boden und Dach. */
        WAND,
        /** Die oberste Ebene. */
        DACH,
        /** Die Oeffnung in der Wand, auf der Seite, die der Bau nennt. */
        TUER,
        /** Das Innere. Es wird MITGESETZT, damit Bauen und Zuruecksetzen dasselbe sind. */
        LUFT
    }

    private Bauplan() {
    }

    /**
     * Was an dieser Stelle des Baus steht.
     *
     * @throws IllegalArgumentException wenn die Stelle nicht im Bau liegt
     */
    public static Stueck stueckBei(Platzhalterbau bau, int x, int y, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau gibt es keine Stelle");
        }
        if (!bau.enthaelt(x, y, z)) {
            throw new IllegalArgumentException("Die Stelle " + x + "/" + y + "/" + z
                    + " liegt nicht im Bau \"" + bau.schluessel() + "\" ("
                    + bau.bereich().alsText() + ") - wer den Quader abschreitet, kommt"
                    + " nie daneben.");
        }
        if (istTuer(bau, x, y, z)) {
            return Stueck.TUER;
        }
        Quader bereich = bau.bereich();
        if (y == bereich.vonY()) {
            return Stueck.BODEN;
        }
        if (y == bereich.bisY()) {
            return Stueck.DACH;
        }
        if (x == bereich.vonX() || x == bereich.bisX()
                || z == bereich.vonZ() || z == bereich.bisZ()) {
            return Stueck.WAND;
        }
        return Stueck.LUFT;
    }

    /**
     * Eine Stelle mit einer Richtung - wohin ein Block zeigt, der eine Richtung traegt.
     *
     * <p>Im Kern und ohne Bukkit: {@code BlockFace} ist eine Bukkit-Sache, und die
     * Zuordnung dazu steht im Plugin. Hier steht nur, WO etwas sitzt und WOHIN es weist.
     */
    public record Stelle(int x, int y, int z, Richtung zeigt) {
    }

    /**
     * Die oberste Ebene der Tuer - hoechstens zwei Bloecke hoch und nie bis ins Dach.
     *
     * <p>Oeffentlich, weil eine Probe die Grenze sonst nachrechnen muesste, und eine
     * nachgerechnete Erwartung ist keine.
     */
    public static int tuerObersteEbene(Platzhalterbau bau) {
        Quader bereich = bau.bereich();
        return Math.min(bereich.vonY() + 2, bereich.bisY() - 1);
    }

    /**
     * Wie viele Bloecke hoch die Oeffnung ist - eins oder zwei.
     *
     * <p>Eine echte Tuer braucht ZWEI: Sie besteht aus zwei Haelften, und eine Haelfte
     * allein ist kein Block, den Minecraft stehen laesst. Bei einem Bau von genau drei
     * Bloecken Hoehe bleibt die Oeffnung darum ein Loch in der Wand - das ist richtig,
     * denn eine zwei Bloecke hohe Tuer frasse dort die Dachkante.
     */
    public static int tuerHoehe(Platzhalterbau bau) {
        return tuerObersteEbene(bau) - bau.bereich().vonY();
    }

    /**
     * Die untere Haelfte der Tuer samt der Richtung, in die sie zeigt - oder
     * {@code null}, wenn die Oeffnung fuer eine Tuer zu niedrig ist.
     *
     * <p>Sie zeigt NACH AUSSEN, in die Seite, die der Bau nennt: Eine Tuer, die nach
     * innen zeigt, sieht von der Strasse aus falsch herum aus, und ihr Griff saesse auf
     * der falschen Seite.
     *
     * <p>{@code null} und kein Wurf: Ein zu niedriger Bau ist kein Fehler, sondern eine
     * Huette. Der Aufrufer setzt dann keine Tuer, und die Oeffnung bleibt, was sie ist.
     */
    public static Stelle tuer(Platzhalterbau bau) {
        if (tuerHoehe(bau) < 2) {
            return null;
        }
        Quader bereich = bau.bereich();
        return switch (bau.tuer()) {
            case NORD -> new Stelle(bereich.mitteX(), bereich.vonY() + 1, bereich.vonZ(),
                    Richtung.NORD);
            case SUED -> new Stelle(bereich.mitteX(), bereich.vonY() + 1, bereich.bisZ(),
                    Richtung.SUED);
            case WEST -> new Stelle(bereich.vonX(), bereich.vonY() + 1, bereich.mitteZ(),
                    Richtung.WEST);
            case OST -> new Stelle(bereich.bisX(), bereich.vonY() + 1, bereich.mitteZ(),
                    Richtung.OST);
        };
    }

    /**
     * Wo das Namensschild haengt samt der Richtung, in die es zeigt - oder {@code null},
     * wenn der Bau zu niedrig dafuer ist.
     *
     * <p><b>INNEN, und das ist eine Entscheidung mit Flaechenfolge</b> [23.11.2026]:
     * Aussen laege es einen Block JENSEITS des Quaders - und damit ausserhalb von allem,
     * was gegen die Tore, die Ladeneinheiten und die Nachbarbauten geprueft ist. Ein
     * Schild, das in ein fremdes Haus ragt, waere genau der Fehler, den die
     * Ueberschneidungspruefung verhindert. Innen haengt es an einem echten Wandblock,
     * reisst kein Loch in die Schale und bleibt im geprueften Quader.
     *
     * <p>Es sitzt ueber der Tuer, einen Block in den Raum hinein, und zeigt INS Zimmer -
     * also in die Gegenrichtung der Tuerseite. Wer durch die Oeffnung tritt, liest es.
     *
     * <p>Gebraucht werden dafuer vier Ebenen: Boden, zwei Tuer, eine fuer das Schild, und
     * darueber muss noch Dach sein. Darunter gibt es keines - wieder {@code null} und
     * kein Wurf.
     */
    public static Stelle schild(Platzhalterbau bau) {
        Stelle tuer = tuer(bau);
        if (tuer == null) {
            return null;
        }
        Quader bereich = bau.bereich();
        int hoehe = tuerObersteEbene(bau) + 1;
        if (hoehe > bereich.bisY() - 1) {
            return null;
        }
        Richtung zeigt = gegenrichtung(bau.tuer());
        return switch (bau.tuer()) {
            case NORD -> new Stelle(tuer.x(), hoehe, bereich.vonZ() + 1, zeigt);
            case SUED -> new Stelle(tuer.x(), hoehe, bereich.bisZ() - 1, zeigt);
            case WEST -> new Stelle(bereich.vonX() + 1, hoehe, tuer.z(), zeigt);
            case OST -> new Stelle(bereich.bisX() - 1, hoehe, tuer.z(), zeigt);
        };
    }

    /**
     * Die Gegenrichtung - im Kern und nicht im Plugin.
     *
     * <p>Sie steht hier und nicht in {@link Richtung}, weil sie bisher niemand gebraucht
     * hat: Eine Methode dort, die nur diese Klasse ruft, waere eine Zusage an alle fuer
     * den Gebrauch von einem. Wird sie ein zweites Mal gebraucht, wandert sie.
     */
    private static Richtung gegenrichtung(Richtung richtung) {
        return switch (richtung) {
            case NORD -> Richtung.SUED;
            case SUED -> Richtung.NORD;
            case WEST -> Richtung.OST;
            case OST -> Richtung.WEST;
        };
    }

    private static boolean istTuer(Platzhalterbau bau, int x, int y, int z) {
        Quader bereich = bau.bereich();
        if (y <= bereich.vonY() || y > tuerObersteEbene(bau)) {
            return false;
        }
        return switch (bau.tuer()) {
            case NORD -> z == bereich.vonZ() && x == bereich.mitteX();
            case SUED -> z == bereich.bisZ() && x == bereich.mitteX();
            case WEST -> x == bereich.vonX() && z == bereich.mitteZ();
            case OST -> x == bereich.bisX() && z == bereich.mitteZ();
        };
    }
}
