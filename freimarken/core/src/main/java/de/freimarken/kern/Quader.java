package de.freimarken.kern;

/**
 * Ein Kasten aus Bloecken - beide Ecken eingeschlossen.
 *
 * <p>Gebraucht fuer die Tore am Bundesmarkt (Plan 4.3): Ein Tor ist der Raum, den ein
 * Spieler durchschreitet, und damit ein Quader. Reine Rechnerei wie {@link Rasterplan} und
 * {@link Chunkfeld}, bewusst ohne Minecraft - denn die beiden Fehler, die hier weh tun,
 * sind Rechenfehler:
 *
 * <ul>
 *   <li>Zwei Tore, die sich <b>ueberschneiden</b>. Der Spieler reist dann in die falsche
 *       Welt, und der Grund ist unsichtbar: Beide Eintraege sehen fuer sich richtig aus.
 *   <li>Ein Tor, das <b>hinter der Weltgrenze</b> liegt. Dort kommt niemand hin, und das
 *       Tor fehlt stumm.
 * </ul>
 *
 * <p>Beides ist ohne Server entscheidbar, also gehoert es hierher und nicht in einen
 * Zuhoerer, der es erst merkt, wenn ein Spieler davorsteht.
 *
 * <p>Die Ecken werden beim Anlegen sortiert. Ein Betreiber, der {@code von} und {@code bis}
 * vertauscht, hat sich vertippt und keinen leeren Quader gemeint.
 */
public record Quader(int vonX, int vonY, int vonZ, int bisX, int bisY, int bisZ) {

    public Quader {
        if (vonX > bisX) {
            int hilf = vonX;
            vonX = bisX;
            bisX = hilf;
        }
        if (vonY > bisY) {
            int hilf = vonY;
            vonY = bisY;
            bisY = hilf;
        }
        if (vonZ > bisZ) {
            int hilf = vonZ;
            vonZ = bisZ;
            bisZ = hilf;
        }
    }

    /** Liegt dieser Block im Quader? */
    public boolean enthaelt(int x, int y, int z) {
        return x >= vonX && x <= bisX
                && y >= vonY && y <= bisY
                && z >= vonZ && z <= bisZ;
    }

    /**
     * Haben die beiden Quader einen Block gemeinsam?
     *
     * <p>Beruehren genuegt nicht - zwei Tore nebeneinander sind erlaubt. Gemeint ist ein
     * Block, der zu beiden gehoert: Dann entscheidet die Reihenfolge der Eintraege, wohin
     * ein Spieler reist, und das ist keine Entscheidung, sondern ein Zufall.
     */
    public boolean schneidet(Quader anderer) {
        return vonX <= anderer.bisX && bisX >= anderer.vonX
                && vonY <= anderer.bisY && bisY >= anderer.vonY
                && vonZ <= anderer.bisZ && bisZ >= anderer.vonZ;
    }

    /**
     * Liegt der ganze Quader waagerecht innerhalb dieses Feldes?
     *
     * <p>Nur waagerecht, weil ein {@link Chunkfeld} aus einer Weltgrenze kommt und eine
     * Weltgrenze keine Hoehe hat. Die Hoehe prueft die Welt selbst, wenn sie geladen ist.
     */
    public boolean liegtIn(Chunkfeld feld) {
        return vonX >= feld.blockVonX() && bisX <= feld.blockBisX()
                && vonZ >= feld.blockVonZ() && bisZ <= feld.blockBisZ();
    }

    /** Wie viele Bloecke der Quader umfasst. */
    public long bloecke() {
        return (bisX - vonX + 1L) * (bisY - vonY + 1L) * (bisZ - vonZ + 1L);
    }

    /** Die Mitte, abgerundet - der Punkt, den eine Meldung nennt. */
    public int mitteX() {
        return Math.floorDiv(vonX + bisX, 2);
    }

    /** Siehe {@link #mitteX()}. */
    public int mitteY() {
        return Math.floorDiv(vonY + bisY, 2);
    }

    /** Siehe {@link #mitteX()}. */
    public int mitteZ() {
        return Math.floorDiv(vonZ + bisZ, 2);
    }

    /** Wie der Quader in einer Meldung aussieht: {@code 80/72/2 bis 82/74/4}. */
    public String alsText() {
        return vonX + "/" + vonY + "/" + vonZ + " bis " + bisX + "/" + bisY + "/" + bisZ;
    }
}
