package de.freimarken.kern;

/**
 * Welche Stelle eines Baus was wird - die Form eines Hansehauses, und zwar ohne Bukkit.
 *
 * <p>Hier steht die Geometrie und kein Block: Welcher {@code Material}-Wert zu einem
 * {@link Stueck} gehoert, sagt die Tabelle im Plugin. Dieselbe Trennung wie bei
 * {@link Wolkenplan} und {@code Wolkenboden} - und sie ist der Grund, warum diese Form
 * ohne laufenden Server zu pruefen ist.
 *
 * <p><b>GIEBELSTAENDIG</b> [23.11.2026]: Ein Hansehaus steht mit dem Giebel zur Gasse,
 * und der Giebel ist ein TREPPENGIEBEL - das Kennzeichen schlechthin. Daraus folgt die
 * ganze Rechnung dieser Klasse: Die Hoehe des Giebels ist die halbe Front, denn er zieht
 * sich je Ebene um einen Block auf jeder Seite ein, bis oben ein Block uebrig ist - der
 * First. Eine Front von sieben Bloecken gibt drei Giebelstufen, eine von siebzehn gibt
 * acht.
 *
 * <p><b>Gefragt wird mit ABSOLUTEN Koordinaten, und eine Stelle ausserhalb ist ein
 * Programmfehler</b>: Wer den Quader abschreitet, kommt nie daneben. Ein {@code LUFT} als
 * Antwort auf eine Stelle ausserhalb waere die bequeme Luege - sie machte einen Zaehler um
 * eins daneben unsichtbar, und genau dieser Fehler ist in diesem Haus dreimal in einer
 * Spaltenlogik gesessen (siehe {@code Weltgriff}).
 *
 * <p><b>Die Reihenfolge der Fragen ist Teil der Form</b> und nicht Geschmack: Die TUER
 * gewinnt ueber die Wand, denn sie liegt in ihr; der GIEBEL gewinnt ueber alles, was
 * darunter waere, denn oberhalb des Dachansatzes gibt es keine Wand mehr.
 */
public final class Bauplan {

    /**
     * So hoch ist ein Geschoss: Schwelle, zwei Ebenen Gefach mit Fenster, Rahm.
     *
     * <p>Vier, und das ist keine runde Zahl, sondern die kleinste, in der ein Fenster
     * zwischen zwei Balkenbaendern Platz hat. Bei drei saesse der Rahm auf dem Fenster.
     */
    public static final int GESCHOSSHOEHE = 4;

    /**
     * Jede wievielte Spalte der Wand ein stehender Balken ist.
     *
     * <p>Drei: Balken, zwei Gefach, Balken. Bei zwei waere die Wand ein Gitter, bei vier
     * verliert das Fachwerk seinen Takt.
     */
    public static final int BALKENTAKT = 3;

    /** Was an einer Stelle des Baus steht. */
    public enum Stueck {
        /** Die unterste Ebene - der Fussboden, er liegt auf dem Boden der Ebene. */
        BODEN,
        /** Der Sockel: die erste Ebene ueber dem Boden, rundum. */
        SOCKEL,
        /** Ein stehender oder liegender Balken des Fachwerks. */
        BALKEN,
        /** Das Gefach zwischen den Balken - die Flaeche der Wand. */
        GEFACH,
        /** Eine Fensteroeffnung. */
        FENSTER,
        /** Die Oeffnung in der Wand, auf der Seite, die der Bau nennt. */
        TUER,
        /** Die Laterne neben der Tuer. */
        LATERNE,
        /** Eine Stufe des Treppengiebels - die zwei schmalen Seiten ueber dem Dachansatz. */
        GIEBELSTUFE,
        /** Die Dachflaeche - die zwei langen Seiten ueber dem Dachansatz. */
        DACH,
        /** Der First ganz oben. */
        FIRST,
        /** Luft. Sie wird MITGESETZT, damit Bauen und Zuruecksetzen dasselbe sind. */
        LUFT
    }

    private Bauplan() {
    }

    /**
     * Die unterste Ebene des Giebels - dort beginnt das Dach.
     *
     * <p>So gerechnet, dass der First genau auf der obersten Ebene des Quaders sitzt: Der
     * Giebel braucht {@code (front - 1) / 2} Stufen, und die zieht man von oben ab.
     * Oeffentlich, weil eine Probe die Zahl sonst nachrechnen muesste - und eine
     * nachgerechnete Erwartung ist keine.
     */
    public static int dachansatz(Platzhalterbau bau) {
        return bau.bereich().bisY() - giebelstufen(bau);
    }

    /** Wie viele Stufen der Treppengiebel hat - die halbe Front, abgerundet. */
    public static int giebelstufen(Platzhalterbau bau) {
        return (bau.front() - 1) / 2;
    }

    /**
     * Die oberste Ebene der Tuer - hoechstens zwei Bloecke hoch und nie in den Giebel.
     *
     * <p>Sie sitzt ueber dem Sockel: Die unterste Wandebene ist der Sockel, die Tuer
     * beginnt darueber. Ein Bau, bei dem dafuer kein Platz ist, bekommt keine Tuer.
     */
    public static int tuerObersteEbene(Platzhalterbau bau) {
        return Math.min(bau.bereich().vonY() + 3, dachansatz(bau) - 1);
    }

    /** Die unterste Ebene der Tuer - gleich ueber dem Sockel. */
    public static int tuerUntersteEbene(Platzhalterbau bau) {
        return bau.bereich().vonY() + 2;
    }

    /** Wie viele Bloecke hoch die Oeffnung ist - null, eins oder zwei. */
    public static int tuerHoehe(Platzhalterbau bau) {
        return Math.max(0, tuerObersteEbene(bau) - tuerUntersteEbene(bau) + 1);
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
     * Die untere Haelfte der Tuer samt der Richtung, in die sie zeigt - oder
     * {@code null}, wenn die Oeffnung fuer eine Tuer zu niedrig ist.
     *
     * <p>Sie zeigt NACH AUSSEN, in die Seite, die der Bau nennt: Eine Tuer, die nach
     * innen zeigt, sieht von der Gasse aus falsch herum aus, und ihr Griff saesse auf der
     * falschen Seite.
     */
    public static Stelle tuer(Platzhalterbau bau) {
        if (tuerHoehe(bau) < 2) {
            return null;
        }
        Quader bereich = bau.bereich();
        int mitte = querMitte(bau);
        int unten = tuerUntersteEbene(bau);
        return switch (bau.tuer()) {
            case NORD -> new Stelle(mitte, unten, bereich.vonZ(), Richtung.NORD);
            case SUED -> new Stelle(mitte, unten, bereich.bisZ(), Richtung.SUED);
            case WEST -> new Stelle(bereich.vonX(), unten, mitte, Richtung.WEST);
            case OST -> new Stelle(bereich.bisX(), unten, mitte, Richtung.OST);
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
     * Ueberschneidungspruefung verhindert.
     *
     * <p>Es sitzt ueber der Tuer, einen Block in den Raum hinein, und zeigt INS Zimmer.
     */
    public static Stelle schild(Platzhalterbau bau) {
        Stelle tuer = tuer(bau);
        if (tuer == null) {
            return null;
        }
        Quader bereich = bau.bereich();
        int hoehe = tuerObersteEbene(bau) + 1;
        if (hoehe > dachansatz(bau) - 1) {
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
     * den Gebrauch von einem.
     */
    private static Richtung gegenrichtung(Richtung richtung) {
        return switch (richtung) {
            case NORD -> Richtung.SUED;
            case SUED -> Richtung.NORD;
            case WEST -> Richtung.OST;
            case OST -> Richtung.WEST;
        };
    }

    /** Was an dieser Stelle des Baus steht.
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
        Quader bereich = bau.bereich();
        if (y == bereich.vonY()) {
            return Stueck.BODEN;
        }
        if (y > dachansatz(bau)) {
            return imGiebel(bau, x, y, z);
        }
        return inDerWand(bau, x, y, z);
    }

    /**
     * Ueber dem Dachansatz: Treppengiebel an den schmalen Seiten, Dachflaeche an den
     * langen, Luft dazwischen, First oben.
     *
     * <p>Je Ebene zieht sich der Bau um einen Block auf JEDER Seite der Front ein. Was
     * davon uebrig bleibt, ist an den Giebelwaenden eine Stufe und an den Laengsseiten die
     * Dachflaeche; der Raum dazwischen ist der Dachboden und bleibt leer.
     */
    private static Stueck imGiebel(Platzhalterbau bau, int x, int y, int z) {
        Quader bereich = bau.bereich();
        int einzug = y - dachansatz(bau);
        int quer = quer(bau, x, z);
        int querVon = querVon(bau) + einzug;
        int querBis = querBis(bau) - einzug;
        if (quer < querVon || quer > querBis) {
            return Stueck.LUFT;
        }
        boolean amGiebel = tief(bau, x, z) == tiefVon(bau)
                || tief(bau, x, z) == tiefBis(bau);
        if (querVon == querBis) {
            // Oben ist EIN Block uebrig - und der ist First, auch an der Giebelwand: Ein
            // Firstbalken, der an zwei Stellen abbricht, saehe aus wie ein Loch.
            return Stueck.FIRST;
        }
        if (amGiebel) {
            return Stueck.GIEBELSTUFE;
        }
        if (quer == querVon || quer == querBis) {
            return Stueck.DACH;
        }
        return Stueck.LUFT;
    }

    /**
     * Unterhalb des Dachansatzes: Sockel, Fachwerk, Fenster, Tuer - und innen Luft.
     *
     * <p>Die Reihenfolge der Fragen ist die Form: Tuer vor Laterne vor Balken vor Fenster
     * vor Gefach. Wer sie dreht, bekommt eine Laterne in der Tuer oder ein Fenster im
     * Balken.
     */
    private static Stueck inDerWand(Platzhalterbau bau, int x, int y, int z) {
        Quader bereich = bau.bereich();
        boolean aussen = x == bereich.vonX() || x == bereich.bisX()
                || z == bereich.vonZ() || z == bereich.bisZ();
        if (!aussen) {
            return Stueck.LUFT;
        }
        if (istTuer(bau, x, y, z)) {
            return Stueck.TUER;
        }
        if (istLaterne(bau, x, y, z)) {
            return Stueck.LATERNE;
        }
        if (y == bereich.vonY() + 1) {
            return Stueck.SOCKEL;
        }
        if (istBalken(bau, x, y, z)) {
            return Stueck.BALKEN;
        }
        if (istFenster(bau, x, y, z)) {
            return Stueck.FENSTER;
        }
        return Stueck.GEFACH;
    }

    /**
     * Balken: die vier Ecksaeulen, jede dritte Spalte, und je Geschoss ein liegendes Band.
     */
    private static boolean istBalken(Platzhalterbau bau, int x, int y, int z) {
        Quader bereich = bau.bereich();
        boolean ecke = (x == bereich.vonX() || x == bereich.bisX())
                && (z == bereich.vonZ() || z == bereich.bisZ());
        if (ecke) {
            return true;
        }
        // Das liegende Band schliesst jedes Geschoss ab - und der Dachansatz ist das
        // oberste: Darauf sitzt das Dach.
        int ueberSockel = y - (bereich.vonY() + 1);
        if (y == dachansatz(bau) || (ueberSockel > 0 && ueberSockel % GESCHOSSHOEHE == 0)) {
            return true;
        }
        // Stehende Balken im Takt, von der Ecke aus gezaehlt.
        int laengs = laengs(bau, x, z);
        return laengs % BALKENTAKT == 0;
    }

    /**
     * Fenster: in jedem Geschoss die zwei Ebenen zwischen den Baendern, und zwar nur
     * zwischen zwei stehenden Balken - also wo {@code laengs % BALKENTAKT} nicht 0 ist.
     *
     * <p>Das ergibt den Takt "Balken, Fenster, Fenster, Balken" und damit die
     * Fensterbaender, die ein Hansehaus hat.
     */
    private static boolean istFenster(Platzhalterbau bau, int x, int y, int z) {
        int ueberSockel = y - (bau.bereich().vonY() + 1);
        if (ueberSockel <= 0) {
            return false;
        }
        int imGeschoss = ueberSockel % GESCHOSSHOEHE;
        return imGeschoss == 2 || imGeschoss == 3;
    }

    /** Die Tuer: mittig in der Front, ueber dem Sockel, hoechstens zwei Bloecke hoch. */
    private static boolean istTuer(Platzhalterbau bau, int x, int y, int z) {
        if (tuerHoehe(bau) <= 0 || y < tuerUntersteEbene(bau)
                || y > tuerObersteEbene(bau)) {
            return false;
        }
        Quader bereich = bau.bereich();
        return anDerFront(bau, x, z) && quer(bau, x, z) == querMitte(bau);
    }

    /** Die Laterne: in der Front, einen Block neben der Tuer, auf Augenhoehe. */
    private static boolean istLaterne(Platzhalterbau bau, int x, int y, int z) {
        if (tuerHoehe(bau) <= 0 || y != tuerObersteEbene(bau)) {
            return false;
        }
        if (!anDerFront(bau, x, z)) {
            return false;
        }
        int abstand = quer(bau, x, z) - querMitte(bau);
        return abstand == -2 || abstand == 2;
    }

    /** Ob diese Stelle in der Wand mit der Tuer liegt. */
    private static boolean anDerFront(Platzhalterbau bau, int x, int z) {
        Quader bereich = bau.bereich();
        return switch (bau.tuer()) {
            case NORD -> z == bereich.vonZ();
            case SUED -> z == bereich.bisZ();
            case WEST -> x == bereich.vonX();
            case OST -> x == bereich.bisX();
        };
    }

    /** Die Koordinate QUER zur Tiefe - also entlang der Front. */
    private static int quer(Platzhalterbau bau, int x, int z) {
        return switch (bau.tuer()) {
            case NORD, SUED -> x;
            case OST, WEST -> z;
        };
    }

    /** Die Koordinate ENTLANG der Tiefe - von der Front nach hinten. */
    private static int tief(Platzhalterbau bau, int x, int z) {
        return switch (bau.tuer()) {
            case NORD, SUED -> z;
            case OST, WEST -> x;
        };
    }

    private static int querVon(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.vonX();
            case OST, WEST -> b.vonZ();
        };
    }

    private static int querBis(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.bisX();
            case OST, WEST -> b.bisZ();
        };
    }

    private static int querMitte(Platzhalterbau bau) {
        return Math.floorDiv(querVon(bau) + querBis(bau), 2);
    }

    private static int tiefVon(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.vonZ();
            case OST, WEST -> b.vonX();
        };
    }

    private static int tiefBis(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.bisZ();
            case OST, WEST -> b.bisX();
        };
    }

    /**
     * Der Abstand von der naechsten Ecke ENTLANG der Wand, in der die Stelle liegt.
     *
     * <p>Von der Ecke aus gezaehlt und nicht vom Weltnullpunkt: Sonst haette ein Haus bei
     * x=48 einen anderen Balkentakt als eines bei x=49, und zwei gleich gebaute Haeuser
     * saehen verschieden aus.
     */
    private static int laengs(Platzhalterbau bau, int x, int z) {
        Quader b = bau.bereich();
        if (x == b.vonX() || x == b.bisX()) {
            return Math.min(z - b.vonZ(), b.bisZ() - z);
        }
        return Math.min(x - b.vonX(), b.bisX() - x);
    }
}
