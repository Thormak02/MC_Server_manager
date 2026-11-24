package de.freimarken.kern;

/**
 * Die Form eines Torhauses in Altfurts Stadtmauer - und der Ort seiner Portalnischen.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft - dasselbe Vorgehen wie bei
 * {@link Stadtplan} und {@link Bauplan}.
 *
 * <p><b>Warum ein Torhaus und keine Haeuserform</b> [entschieden 23.11.2026]: Die vier
 * Tortuerme der ersten Fassung waren 3 Bloecke breit und 16 bis 22 hoch. Der Betreiber
 * nannte sie "zu hoch und zu duenn, das passt ueberhaupt nicht ins stadtbild" und
 * entschied: 15 breit, 11 hoch, mit Ecktuermen. Und die Stadtmauer soll GESCHLOSSEN
 * sein, die Weltportale sollen IN den Torhaeusern stehen - "mit einem passenden
 * hintergrund hinter dem portal, also zb. beim nether teleport ein kleines nether
 * diorama".
 *
 * <p>Aufbau von vorn gesehen, 15 Bloecke breit und 11 hoch. Links und rechts je ein
 * Turm von vier Bloecken Breite, dazwischen der Durchgang von sieben:
 *
 * <pre>
 *   ##         ##     Hoehe 10: nur die Tuerme, mit Zinnen
 *   ###  /=\  ###     Hoehe  9: First - er laeuft DURCH die Mauer
 *   ### /===\ ###     Hoehe  8: Dach zieht sich in der BREITE ein
 *   ###/=====\###     Hoehe  7: Dach beginnt - von der Stadt sieht man den Giebel
 *   #############     Hoehe  6: Decke ueber dem Durchgang
 *   ###       ###     Hoehe  5: oberste Ebene des Durchgangs
 *   ###       ###
 *   ###  N N  ###     Hoehe  3: oberste Ebene der Nischen (N = Portal)
 *   ###  N N  ###
 *   ###  N N  ###     Hoehe  1: unterste Ebene der Nischen und des Durchgangs
 *   #############     Hoehe  0: Boden
 * </pre>
 *
 * <p>Und von oben, {@code l} laengs der Mauer und {@code q} durch sie hindurch
 * ({@code q=0} ist die Seite zur STADT, {@code q=8} die nach draussen):
 *
 * <pre>
 *        l=0 .. 3        l=4 .. 10          l=11 .. 14
 *   q=0  Turm            Durchgang (offen)  Turm
 *   q=1  NISCHE+Bild     Durchgang          NISCHE+Bild
 *   q=3  NISCHE+Bild     Durchgang          NISCHE+Bild
 *   q=5  Turm            Durchgang (Ende)   Turm
 *   q=6  Turm            MAUER              Turm
 *   q=8  Turm            MAUER              Turm
 * </pre>
 *
 * <p><b>Der Durchgang endet an einer Wand</b>, und das ist der ganze Sinn: Die Mauer
 * bleibt geschlossen. Man geht in das Torhaus hinein, nicht hindurch - und findet darin
 * links und rechts je ein Portal in eine andere Welt.
 *
 * <p><b>Hinter jeder Nische steht eine Scheibe und dahinter das Diorama</b>: Man sieht
 * durch das Portal in die Zielwelt. Die Scheibe ist nicht Schmuck, sondern ein Riegel -
 * ohne sie waere beim Nether Lava eine Armlaenge von einem Spieler entfernt, der auf die
 * Reise wartet. Und hinter dem Diorama liegt noch eine Ebene MAUERWERK: Der Turm ist
 * vier Bloecke breit, seine Aussenflaeche grenzt an die Gasse am Wehrgang, und ein
 * Diorama, das bis dorthin reichte, liesse seine Lava in die Gasse laufen.
 *
 * <p><b>Die Nische IST das Tor</b>: Ihre drei mal drei Bloecke sind der Quader, in dem
 * die Torwache einen Spieler findet. {@link #nische} nennt ihn, und die
 * {@code config.yml} traegt genau diesen Quader bei der Welt ein - zwei Stellen mit
 * derselben Zahl, aber die Datei bleibt die Wahrheit: Wer sein Portal verschiebt,
 * bekommt die Nische dort, wo er es hinlegt, und eine Klage, wenn es nicht ins Torhaus
 * passt.
 */
public final class Torhausplan {

    /** Was an einer Stelle des Torhauses steht. */
    public enum Stueck {
        /** Der Boden - die unterste Ebene, sie ERSETZT den Boden der Ebene. */
        BODEN,
        /** Mauerwerk. */
        MAUER,
        /** Die Ecke eines Turms - anderer Stein, damit die Kante sichtbar ist. */
        PFEILER,
        /** Eine Dachtreppe. */
        DACH,
        /** Ein voller Dachblock - unter einer Treppe und auf dem First. */
        FIRST,
        /** Ein Zahn der Brustwehr auf einem Turm. */
        ZINNE,
        /** Die Oeffnung einer Portalnische - hier steht der Spieler. */
        NISCHE,
        /** Die Scheibe hinter der Nische - man sieht hindurch, aber nichts kommt heraus. */
        SCHEIBE,
        /** Der Hintergrund hinter der Scheibe, in den Bloecken der Zielwelt. */
        DIORAMA,
        /** Luft - der Durchgang, die Zinnenluecken, der Raum unter dem Dach. */
        LUFT
    }

    /** Die Breite eines Torhauses laengs der Mauer - Ansage des Betreibers. */
    public static final int BREITE = 15;

    /** Die Tiefe eines Torhauses durch die Mauer hindurch. */
    public static final int TIEFE = 9;

    /** Die Hoehe eines Torhauses - Ansage des Betreibers. */
    public static final int HOEHE = 11;

    /** Die Breite eines der beiden Tuerme. */
    public static final int TURM_BREITE = 4;

    /** Die Breite des Durchgangs zwischen den Tuermen. */
    public static final int DURCHGANG_BREITE = BREITE - 2 * TURM_BREITE;

    /** Wie tief der Durchgang in das Torhaus hineinfuehrt, bis zur Wand. */
    public static final int DURCHGANG_TIEFE = 6;

    /**
     * Die Hoehe des Durchgangs ueber dem Boden.
     *
     * <p>Fuenf, damit die TUERME ueber dem Dach stehen: Bei elf Ebenen Gesamthoehe
     * braucht der Durchgang fuenf, die Decke eine, das Dach drei - und die oberste
     * bleibt den Tuermen allein. In der gewaehlten Vorlage ragen sie ueber den
     * First, und das ist der Unterschied zwischen einem Torhaus und einem Schuppen
     * mit Loch.
     */
    public static final int DURCHGANG_HOEHE = 5;

    /** Die Breite einer Portalnische - dieselbe wie die eines Weltentors. */
    public static final int NISCHE_BREITE = 3;

    /** Die Hoehe einer Portalnische. */
    public static final int NISCHE_HOEHE = 3;

    /** Wie tief die Nische im Turm sitzt, von der Stadtseite aus gemessen. */
    public static final int NISCHE_AB_QUER = 1;

    /** Ab welcher Ebene das Dach ueber dem Durchgang beginnt - ueber der Decke. */
    public static final int DACH_AB = DURCHGANG_HOEHE + 2;

    /**
     * Wie viele Ebenen das Dach hat.
     *
     * <p>Drei, und darueber bleibt die oberste Ebene den Tuermen: {@code DACH_AB}
     * plus drei ist zehn, und zehn ist die letzte von elf. Ein Dach, das bis ganz
     * oben liefe, verdeckte die Brustwehr der Tuerme.
     */
    public static final int DACH_EBENEN = HOEHE - DACH_AB - 1;

    private final Richtung seite;
    private final int innenRing;

    /**
     * @param seite     auf welcher Seite der Stadt das Torhaus steht - die Richtung von
     *                  der Mitte nach draussen
     * @param innenRing der Ring, auf dem die Stadtseite des Torhauses liegt
     */
    public Torhausplan(Richtung seite, int innenRing) {
        if (seite == null) {
            throw new IllegalArgumentException("Ein Torhaus ohne Seite steht nirgends");
        }
        if (innenRing < 1) {
            throw new IllegalArgumentException(
                    "Ein Torhaus auf Ring " + innenRing + " stuende in der Mitte");
        }
        this.seite = seite;
        this.innenRing = innenRing;
    }

    /** Das Torhaus einer Seite in dieser Stadt - es sitzt im aeusseren Mauerring. */
    public static Torhausplan inDerMauer(Stadtplan plan, Richtung seite) {
        if (plan == null) {
            throw new IllegalArgumentException("Ohne Stadtplan gibt es keine Mauer");
        }
        return new Torhausplan(seite, plan.aussenRing() - TIEFE + 1);
    }

    /** Auf welcher Seite der Stadt dieses Torhaus steht. */
    public Richtung seite() {
        return seite;
    }

    /** Der Ring, auf dem die Stadtseite des Torhauses liegt. */
    public int innenRing() {
        return innenRing;
    }

    /** Der Quader, den dieses Torhaus einnimmt. */
    public Quader bereich() {
        int[] eine = welt(0, 0, 0);
        int[] andere = welt(BREITE - 1, TIEFE - 1, HOEHE - 1);
        return new Quader(Math.min(eine[0], andere[0]), Math.min(eine[1], andere[1]),
                Math.min(eine[2], andere[2]), Math.max(eine[0], andere[0]),
                Math.max(eine[1], andere[1]), Math.max(eine[2], andere[2]));
    }

    /**
     * Der Quader einer Portalnische - genau der Bereich, in dem die Torwache greift.
     *
     * @param links die Nische auf der Seite des kleineren Laengsmasses; {@code false}
     *              nimmt die gegenueberliegende
     */
    public Quader nische(boolean links) {
        int l = links ? TURM_BREITE - 1 : BREITE - TURM_BREITE;
        int[] eine = welt(l, NISCHE_AB_QUER, 1);
        int[] andere = welt(l, NISCHE_AB_QUER + NISCHE_BREITE - 1, NISCHE_HOEHE);
        return new Quader(Math.min(eine[0], andere[0]), Math.min(eine[1], andere[1]),
                Math.min(eine[2], andere[2]), Math.max(eine[0], andere[0]),
                Math.max(eine[1], andere[1]), Math.max(eine[2], andere[2]));
    }

    /**
     * Die Richtung, in die ein Spieler in dieser Nische SCHAUT - auf die Scheibe zu.
     *
     * <p>Gebraucht fuer das Schild neben dem Portal und fuer die Scheibe selbst.
     */
    public Richtung nischeZeigt(boolean links) {
        boolean laengsIstX = seite == Richtung.NORD || seite == Richtung.SUED;
        if (laengsIstX) {
            return links ? Richtung.WEST : Richtung.OST;
        }
        return links ? Richtung.NORD : Richtung.SUED;
    }

    /**
     * Was an dieser Stelle der Welt steht.
     *
     * @throws IllegalArgumentException wenn die Stelle nicht im Torhaus liegt
     */
    public Stueck stueckBei(int x, int y, int z) {
        int[] oertlich = oertlich(x, y, z);
        if (oertlich == null) {
            throw new IllegalArgumentException(x + "/" + y + "/" + z
                    + " liegt nicht im Torhaus " + bereich().alsText());
        }
        return stueck(oertlich[0], oertlich[1], oertlich[2], x, z);
    }

    /** Die Form in oertlichen Maßen - die eigentliche Rechnung. */
    private Stueck stueck(int l, int q, int h, int x, int z) {
        if (h == 0) {
            return Stueck.BODEN;
        }
        boolean imTurm = l < TURM_BREITE || l >= BREITE - TURM_BREITE;
        if (h == HOEHE - 1 && imTurm) {
            // DIE BRUSTWEHR der Tuerme - jeder zweite Block, wie auf der Stadtmauer.
            // Dieselbe Regel und nicht eine zweite: Das Torhaus steht IN der Mauer, und
            // zwei verschiedene Zinnenmuster nebeneinander sieht man.
            return Stadtplan.zinne(x, z) ? Stueck.ZINNE : Stueck.LUFT;
        }
        if (imTurm) {
            return imTurmStueck(l, q, h);
        }
        return inDerMitteStueck(l, q, h);
    }

    private Stueck imTurmStueck(int l, int q, int h) {
        boolean links = l < TURM_BREITE;
        int zumDurchgang = links ? TURM_BREITE - 1 - l : l - (BREITE - TURM_BREITE);
        boolean inDerNischenhoehe = h >= 1 && h <= NISCHE_HOEHE
                && q >= NISCHE_AB_QUER && q < NISCHE_AB_QUER + NISCHE_BREITE;
        if (inDerNischenhoehe) {
            // Von der Durchgangsseite nach hinten: Oeffnung, Scheibe, Diorama -
            // und dann MAUER.
            //
            // DIE LETZTE EBENE IST MAUERWERK, und das ist ein Riegel und keine
            // Zierde: Der Turm ist vier Bloecke breit, und seine aeussere Flaeche
            // grenzt an die Gasse am Wehrgang. Reichte das Diorama bis dorthin,
            // liefe die Lava des Netherbildes in die Gasse - ein Diorama muss
            // DICHT sein, sonst ist es ein Loch in der Stadtmauer.
            if (zumDurchgang == 0) {
                return Stueck.NISCHE;
            }
            if (zumDurchgang == 1) {
                return Stueck.SCHEIBE;
            }
            if (zumDurchgang == 2) {
                return Stueck.DIORAMA;
            }
            return Stueck.MAUER;
        }
        boolean ecke = (l == 0 || l == BREITE - 1) && (q == 0 || q == TIEFE - 1);
        return ecke ? Stueck.PFEILER : Stueck.MAUER;
    }

    private Stueck inDerMitteStueck(int l, int q, int h) {
        if (h <= DURCHGANG_HOEHE && q < DURCHGANG_TIEFE) {
            return Stueck.LUFT;
        }
        if (h < DACH_AB) {
            return Stueck.MAUER;
        }
        if (h >= DACH_AB + DACH_EBENEN) {
            // Ueber dem Dach: Hier stehen nur noch die Tuerme.
            return Stueck.LUFT;
        }
        // DAS DACH ZIEHT SICH IN DER BREITE EIN, nicht in der Tiefe: Damit laeuft
        // der First DURCH die Mauer hindurch, und von der Stadt aus sieht man einen
        // GIEBEL - so steht es in der Vorlage, die der Betreiber gewaehlt hat. Zog
        // sich das Dach in der Tiefe ein, saehe man von vorn ein Pultdach, dessen
        // Flaeche nach hinten wegkippt.
        int einzug = h - DACH_AB;
        int von = TURM_BREITE + einzug;
        int bis = BREITE - 1 - TURM_BREITE - einzug;
        if (l < von || l > bis) {
            return Stueck.LUFT;
        }
        // Die Kante einer Stufe ist die Treppe, alles dahinter ist voll - sonst waere
        // das Dach innen hohl und von unten sieht man hindurch.
        boolean kante = l == von || l == bis;
        return kante && einzug + 1 < DACH_EBENEN ? Stueck.DACH : Stueck.FIRST;
    }

    /**
     * In welche Richtung eine Dachtreppe BERGAUF zeigt - oder {@code null}, wo keine
     * liegt.
     *
     * <p>Dieselbe Sache wie {@code Bauplan.dachzeigt} und aus demselben gemessenen
     * Grund: Eine Treppe ohne Blockdaten zeigt nach Norden, und ein Dach aus lauter
     * nordwaerts zeigenden Treppen ist von drei Seiten falsch. {@code facing} zeigt bei
     * Minecraft BERGAUF, zum First hin.
     *
     * <p>Das Dach zieht sich in der BREITE ein, also zeigt die Treppe der linken Kante
     * nach rechts und die der rechten nach links - in Weltrichtungen uebersetzt.
     */
    public Richtung dachzeigt(int x, int y, int z) {
        int[] oertlich = oertlich(x, y, z);
        if (oertlich == null) {
            return null;
        }
        int l = oertlich[0];
        int h = oertlich[2];
        if (stueck(l, oertlich[1], h, x, z) != Stueck.DACH) {
            return null;
        }
        boolean linkeKante = l == TURM_BREITE + (h - DACH_AB);
        boolean laengsIstX = seite == Richtung.NORD || seite == Richtung.SUED;
        if (laengsIstX) {
            return linkeKante ? Richtung.OST : Richtung.WEST;
        }
        return linkeKante ? Richtung.SUED : Richtung.NORD;
    }

    /** Oertliche Masse zu einer Weltstelle, oder {@code null} ausserhalb. */
    private int[] oertlich(int x, int y, int z) {
        int h = y - Stadtplan.BODEN_Y;
        if (h < 0 || h >= HOEHE) {
            return null;
        }
        boolean laengsIstX = seite == Richtung.NORD || seite == Richtung.SUED;
        int laengs = laengsIstX ? x - (Stadtplan.MITTE_X - (BREITE - 1) / 2)
                : z - (Stadtplan.MITTE_Z - (BREITE - 1) / 2);
        if (laengs < 0 || laengs >= BREITE) {
            return null;
        }
        int quer = switch (seite) {
            case NORD -> Stadtplan.MITTE_Z - innenRing - z;
            case SUED -> z - (Stadtplan.MITTE_Z + innenRing);
            case WEST -> Stadtplan.MITTE_X - innenRing - x;
            case OST -> x - (Stadtplan.MITTE_X + innenRing);
        };
        if (quer < 0 || quer >= TIEFE) {
            return null;
        }
        return new int[] {laengs, quer, h};
    }

    /** Die Weltstelle zu oertlichen Massen. */
    private int[] welt(int l, int q, int h) {
        boolean laengsIstX = seite == Richtung.NORD || seite == Richtung.SUED;
        int laengsAb = (laengsIstX ? Stadtplan.MITTE_X : Stadtplan.MITTE_Z)
                - (BREITE - 1) / 2;
        int y = Stadtplan.BODEN_Y + h;
        return switch (seite) {
            case NORD -> new int[] {laengsAb + l, y, Stadtplan.MITTE_Z - innenRing - q};
            case SUED -> new int[] {laengsAb + l, y, Stadtplan.MITTE_Z + innenRing + q};
            case WEST -> new int[] {Stadtplan.MITTE_X - innenRing - q, y, laengsAb + l};
            case OST -> new int[] {Stadtplan.MITTE_X + innenRing + q, y, laengsAb + l};
        };
    }
}
