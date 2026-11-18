package de.freimarken.kern;

/**
 * Die Geometrie von Wolkenwerder: wo ein Feld liegt, wo Leere ist, und wie die Startinsel
 * darin aussieht.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft - wie {@link Inselplan}, {@link Stadtfeldplan}
 * und {@link Rasterplan}. Derselbe Plan beantwortet vier Fragen, die sonst an vier Stellen
 * auseinanderlaufen wuerden: Was erzeugt der Weltgenerator? Wem gehoert der Block, auf den
 * jemand klickt? Was setzt das Plugin hin, wenn jemand eine Insel nimmt? Und wie weit
 * reicht die Weltgrenze ({@link Wolkengrenze})?
 *
 * <p><b>Der Unterschied zu Hafenwerder ist nicht die Rechnung, sondern wer baut</b>
 * [18.11.2026]. Dort backt der Weltgenerator jede Insel in das Gelaende: Wer eine kauft,
 * kauft etwas, das schon daliegt. Hier erzeugt der Generator NICHTS ausser dem
 * Ankunftsfeld, und die Startinsel setzt das Plugin beim Nehmen. Drei Gruende, und jeder
 * allein wuerde reichen:
 *
 * <ul>
 *   <li><b>Die Starttruhe.</b> Ein {@code ChunkData} traegt keine Blockwesen - eine Truhe
 *       mit Inhalt ist im Weltgenerator nicht zu setzen. Sie ist aber der Anfang des
 *       Spiels: ohne Lava und Eis gibt es keinen Kobbelstein und damit keinen Stein.
 *   <li><b>Das Zuruecksetzen.</b> Eine Skyblockinsel muss sich neu aufsetzen lassen - wer
 *       seine Lava verliert, ist sonst auf Lebenszeit festgefahren, und sein Feld mit ihm.
 *       Wer aufsetzen kann, kann auch zuruecksetzen; es ist dieselbe Hand.
 *   <li><b>Die Leere kostet nichts.</b> Gebackene Inseln hiessen 1.443 Inseln voller
 *       Bloecke, die vorgeneriert werden muessten, damit niemand beim Ankommen in einen
 *       halb erzeugten Himmel laeuft. Eine leere Welt entsteht im Nichts.
 * </ul>
 *
 * <p>Aufbau einer Rasterzelle bei Feld 128 und Leere 384 (Rasterweite 512), auf einer
 * Achse:
 *
 * <pre>
 *     0 .. 127   WOLKENFELD (128 Bloecke, das Eigentum)
 *   128 .. 511   LEERE      (384 Bloecke, niemandem gehoerend und unbebaubar)
 * </pre>
 *
 * <p><b>512 ist kein Zufall</b>: Eine Rasterzelle ist damit genau eine Regionsdatei
 * (32x32 Chunks), und ein Feld beginnt an deren Ecke. Was einer Insel gehoert, liegt
 * damit in EINER Datei - fuer eine Sicherung, fuer ein Zuruecksetzen und fuer die
 * Punktzahl, die ueber die Bloecke einer Insel laeuft, ist das der Unterschied zwischen
 * einer Datei und vier.
 *
 * <p><b>384 Bloecke Leere, und nicht 128 wie das Meer von Hafenwerder</b>: Dort soll man
 * hinueberschwimmen koennen, hier soll man den Nachbarn nicht einmal SEHEN. Bei der
 * ueblichen Sichtweite von zwoelf Chunks (192 Bloecke) ist das erfuellt - wer am Rand
 * seines Feldes steht, sieht Himmel. Eine Bruecke verbietet ohnehin die Schutzwache: In
 * der Leere darf niemand bauen, auch nicht der Nachbar.
 *
 * <p><b>Keine Barriere</b>, anders als bei den Stadtfeldern. Dort traegt die Leere nicht,
 * und wer hineinfaellt, faellt bis zur Weltunterkante - darum die unsichtbare Wand. Hier
 * ist genau das die Spielregel: In einer Skyblockwelt ist der Fall ins Nichts der Tod, den
 * jeder kennt und mit dem jeder rechnet. Eine Wand um jede Insel waere ein Kaefig und
 * naehme dem Spiel seinen einzigen Schrecken.
 *
 * <p><b>Feld 0/0 ist das ANKUNFTSFELD</b> und gehoert niemandem: Dort landet das Tor aus
 * Altfurt (Plan 2.1, "Wolkenturm") und dort steht der Erscheinungspunkt der Welt. Ein Tor
 * braucht ein Ziel, und in einer leeren Welt ist "irgendwo" kein Ziel, sondern ein Fall.
 */
public final class Wolkenplan {

    /** Was an einer Stelle liegt - auf der Ebene des EIGENTUMS. */
    public enum Feld {
        /** Das Eigentum einer Wolkeninsel: 128x128 Bloecke, von der Unterkante aufwaerts. */
        WOLKENFELD,
        /** Die Leere zwischen den Feldern. Gehoert niemandem, bebaubar ist sie nicht. */
        LEERE
    }

    /**
     * Was an einer Stelle der Startinsel liegt.
     *
     * <p>Die Gestalt, nicht das Material: Welcher Block daraus wird, steht in
     * {@code Wolkenboden} - und zwar zweimal, denn das Ankunftsfeld traegt dieselbe
     * Gestalt aus anderem Stoff.
     */
    public enum Stueck {
        /** Luft. Der Normalfall dieser Welt. */
        NICHTS,
        /** Die oberste Schicht der Plattform - darauf wird gelaufen und gepflanzt. */
        WIESE,
        /** Die Schichten darunter. Endlich, und das ist der Reiz: Erde waechst nicht nach. */
        ERDE,
        /** Der Stamm des Baumes. */
        STAMM,
        /** Seine Krone. Daraus kommen die Setzlinge fuer jeden weiteren Baum. */
        LAUB,
        /** Die Starttruhe. Was darin liegt, steht in der config.yml und nicht hier. */
        TRUHE,
        /** Das Gelaender - nur auf dem Ankunftsfeld, siehe {@link Insel#ANKUNFT}. */
        GELAENDER
    }

    /**
     * Welche der beiden Inseln gemeint ist.
     *
     * <p>Beide haben dieselbe Plattform, und das ist Absicht: Wer ankommt, steht auf
     * genau dem, was er sich gleich selbst nehmen kann. Verschieden sind nur die drei
     * Dinge, die dem Eigentuemer gehoeren - Baum, Truhe - und das eine, das nur der
     * oeffentliche Platz braucht: ein Gelaender.
     */
    public enum Insel {
        /**
         * Die Insel eines Spielers: Plattform, Baum, Starttruhe.
         *
         * <p>Ohne Gelaender, und das ist der Reiz der Welt: Der Fall ins Nichts ist die
         * Regel, mit der jeder rechnet, und wer sich sichern will, baut sich die Mauer
         * selbst. Ausserdem stuende ein Ring um die Plattform genau dort im Weg, wo Lava
         * und Wasser hingehoeren.
         */
        START,
        /**
         * Das Ankunftsfeld: dieselbe Plattform aus Stein, mit Gelaender und ohne Truhe.
         *
         * <p>Das Gelaender steht hier und nur hier, weil dies der einzige Platz ist, den
         * niemand umbauen darf (die Schutzwache verbietet es) - und weil ein Sturz in die
         * Leere die Sachen kostet, mit denen jemand gerade aus Altfurt angekommen ist. Ein
         * Baum waere Laub, das niemand ernten darf, und eine Truhe eine, die niemand
         * oeffnen kann.
         */
        ANKUNFT
    }

    /** Das Feld, das niemandem gehoert - auf beiden Achsen. */
    public static final int ANKUNFTSFELD = 0;

    /**
     * Die Kante eines Wolkenfeldes in Bloecken.
     *
     * <p>Genauso gross wie ein Inselfeld in Hafenwerder, und das ist eine Entscheidung
     * gegen die naheliegende: Eine gratis Insel darf nicht mehr Flaeche sein als eine
     * bezahlte. Dass sie trotzdem der grosszuegigere Besitz ist, liegt am Inhalt - in
     * Hafenwerder bekommt man 11.364 Bloecke Land geschenkt, hier eine Plattform von
     * 81 Bloecken und ansonsten Platz.
     */
    public static final int WOLKENFELD_KANTE = 128;

    /** Die Leere zwischen zwei Feldern - drei Felder breit, siehe Klassenkopf. */
    public static final int LEERE_BREITE = 384;

    /**
     * Die Kante der Startplattform - neun mal neun.
     *
     * <p>Eine ungerade Zahl, damit es einen Mittelblock gibt: Die Plattform liegt um die
     * {@link Feldrechnung#feldMitte} herum, und bei gerader Kante laege sie um einen
     * halben Block schief.
     *
     * <p><b>Neun und nicht fuenf</b>: Lava und Wasser brauchen Platz nebeneinander, und
     * zwischen beiden muss man stehen koennen, ohne in die Lava zu treten. Auf 5x5 steht
     * schon der Baum im Weg, und der erste Handgriff des Spiels waere, ihn abzureissen.
     * Neun und nicht fuenfzehn: 243 Bloecke Erde sind endlich, und daran haengt das ganze
     * Spiel - wer Erde verschwendet, bekommt keine neue.
     */
    public static final int PLATTFORM_KANTE = 9;

    /** Wie viele Schichten die Plattform hat: eine Wiese und darunter zwei Erde. */
    public static final int PLATTFORM_TIEF = 3;

    /** Wo der Baum steht, gemessen vom Mittelblock der Plattform. */
    public static final int BAUM_X = -2;

    /** Wo der Baum steht, gemessen vom Mittelblock der Plattform. */
    public static final int BAUM_Z = -2;

    /** Wie viele Stammbloecke der Baum hat. */
    public static final int STAMM_HOCH = 5;

    /** Wo die Starttruhe steht, gemessen vom Mittelblock der Plattform. */
    public static final int TRUHE_X = 2;

    /** Wo die Starttruhe steht, gemessen vom Mittelblock der Plattform. */
    public static final int TRUHE_Z = 2;

    private final Feldrechnung rechnung;
    private final int plattformKante;

    /**
     * @param rechnung       das Feldraster, Feldkante und Leerebreite
     * @param plattformKante die Kante der Startplattform; ungerade und so klein, dass sie
     *                       in das Feld passt
     */
    public Wolkenplan(Feldrechnung rechnung, int plattformKante) {
        if (plattformKante < 3) {
            throw new IllegalArgumentException("Die Plattform muss mindestens 3 Bloecke"
                    + " breit sein, nicht " + plattformKante);
        }
        if (plattformKante % 2 == 0) {
            throw new IllegalArgumentException("Die Plattformkante muss ungerade sein,"
                    + " damit sie einen Mittelblock hat, nicht " + plattformKante);
        }
        if (plattformKante > rechnung.feldKante()) {
            throw new IllegalArgumentException("Eine Plattform von " + plattformKante
                    + " Bloecken passt nicht in ein Feld von " + rechnung.feldKante());
        }
        this.rechnung = rechnung;
        this.plattformKante = plattformKante;
    }

    /** Der Plan von Wolkenwerder: 128er Felder, 384 Bloecke Leere, 9x9 Plattform. */
    public static Wolkenplan wolkenwerder() {
        return new Wolkenplan(new Feldrechnung(WOLKENFELD_KANTE, LEERE_BREITE),
                PLATTFORM_KANTE);
    }

    /** Die Rasterrechnung dieses Plans - die eine Quelle fuer Feldnummer und Mitte. */
    public Feldrechnung rechnung() {
        return rechnung;
    }

    public int feldKante() {
        return rechnung.feldKante();
    }

    public int leereBreite() {
        return rechnung.zwischenraum();
    }

    public int plattformKante() {
        return plattformKante;
    }

    /** Wie weit die Plattform vom Mittelblock nach jeder Seite reicht. */
    public int plattformHalb() {
        return (plattformKante - 1) / 2;
    }

    /** Kantenlaenge einer Rasterzelle: Wolkenfeld plus Leere. */
    public int rasterWeite() {
        return rechnung.rasterWeite();
    }

    /**
     * Wem dieser Block gehoeren KANN - Wolkenfeld oder Leere.
     *
     * <p>Beide Achsen einzeln, und die Leere gewinnt: Nur wo auf BEIDEN Achsen ein Feld
     * liegt, ist es ein Wolkenfeld. Was sonst ueber Eck liegt, ist Leere.
     */
    public Feld feldBei(int x, int z) {
        return rechnung.imFeld(x) && rechnung.imFeld(z) ? Feld.WOLKENFELD : Feld.LEERE;
    }

    /** Die Feldnummer dieser Weltkoordinate auf einer Achse - siehe {@link Feldrechnung}. */
    public int feldNummer(int wert) {
        return rechnung.feldNummer(wert);
    }

    /** Die kleinste Weltkoordinate dieses Feldes auf einer Achse. */
    public int feldAnfang(int nummer) {
        return rechnung.feldAnfang(nummer);
    }

    /** Die Mitte eines Feldes auf einer Achse - dort steht die Plattform. */
    public int feldMitte(int nummer) {
        return rechnung.feldMitte(nummer);
    }

    /**
     * Was an dieser Stelle der Startinsel liegt - gemessen vom Mittelblock der Plattform.
     *
     * <p><b>Die eine Frage, die das Aufsetzen je Block stellt</b>, und sie beschreibt die
     * ganze Startinsel. Der Nullpunkt ist der Mittelblock der WIESE: {@code dy == 0} ist
     * sie selbst, negative Werte liegen darunter, positive darueber.
     *
     * <p>Alles, was nicht genannt ist, ist {@link Stueck#NICHTS} - also auch jeder Block
     * ausserhalb der Plattform. Das Aufsetzen laeuft darum ueber einen Quader, der ein
     * Stueck groesser ist als die Plattform selbst, und schreibt alles, was es bekommt:
     * So ist das Zuruecksetzen dieselbe Hand wie das Aufsetzen, und Reste eines alten
     * Baumes bleiben nicht stehen.
     *
     * @param insel welche der beiden Inseln gemeint ist
     * @param dx    Bloecke oestlich des Mittelblocks
     * @param dy    Bloecke ueber der Wiese
     * @param dz    Bloecke suedlich des Mittelblocks
     */
    public Stueck stueckBei(Insel insel, int dx, int dy, int dz) {
        int halb = plattformHalb();
        boolean aufDerPlattform = Math.abs(dx) <= halb && Math.abs(dz) <= halb;
        if (dy <= 0) {
            if (!aufDerPlattform || dy <= -PLATTFORM_TIEF) {
                return Stueck.NICHTS;
            }
            return dy == 0 ? Stueck.WIESE : Stueck.ERDE;
        }
        if (insel == Insel.ANKUNFT) {
            boolean amRand = aufDerPlattform
                    && (Math.abs(dx) == halb || Math.abs(dz) == halb);
            return dy == 1 && amRand ? Stueck.GELAENDER : Stueck.NICHTS;
        }
        if (dy == 1 && dx == TRUHE_X && dz == TRUHE_Z) {
            return Stueck.TRUHE;
        }
        if (dx == BAUM_X && dz == BAUM_Z && dy <= STAMM_HOCH) {
            return Stueck.STAMM;
        }
        return laub(dx - BAUM_X, dy, dz - BAUM_Z);
    }

    /**
     * Die Krone des Baumes, gemessen vom Stamm aus.
     *
     * <p>Ein gewoehnlicher kleiner Eichenbaum, von Hand nachgebaut und nicht von Minecraft
     * erzeugt: {@code World.generateTree} wuerfelt die Gestalt, und zwei Inseln waeren
     * dann verschieden. Hier ist jede dieselbe - und dasselbe Zuruecksetzen ergibt
     * denselben Baum.
     *
     * <pre>
     *   dy 4, 5   5x5 ohne die vier Ecken
     *   dy 6      3x3
     *   dy 7      ein Kreuz aus fuenf Bloecken
     * </pre>
     */
    private static Stueck laub(int dx, int dy, int dz) {
        int weit = Math.max(Math.abs(dx), Math.abs(dz));
        boolean ecke = Math.abs(dx) == 2 && Math.abs(dz) == 2;
        boolean laub = switch (dy) {
            case 4, 5 -> weit <= 2 && !ecke;
            case 6 -> weit <= 1;
            case 7 -> Math.abs(dx) + Math.abs(dz) <= 1;
            default -> false;
        };
        return laub ? Stueck.LAUB : Stueck.NICHTS;
    }

    /** Wie hoch die Startinsel ueber ihrer Wiese aufragt - die Spitze des Baumes. */
    public int hoeheUeberWiese() {
        return STAMM_HOCH + 2;
    }

    /** Wie tief sie unter ihre Wiese reicht. */
    public int tiefeUnterWiese() {
        return PLATTFORM_TIEF - 1;
    }

    /**
     * Ob dieses Feld das ANKUNFTSFELD ist - Feld 0/0, und es gehoert niemandem.
     *
     * <p>Dort landet das Tor aus Altfurt. Nehmen laesst es sich darum nicht; die Pruefung
     * steht an EINER Stelle, damit nicht der Befehl das eine und der Schutz das andere
     * meint.
     */
    public boolean istAnkunftsfeld(int feldX, int feldZ) {
        return feldX == ANKUNFTSFELD && feldZ == ANKUNFTSFELD;
    }

    /**
     * Die Mitte des Ankunftsfeldes auf einer Achse - der Erscheinungspunkt der Welt.
     *
     * <p>Damit die Null nicht ein zweites Mal im Generator steht: Dort hiesse es
     * {@code feldMitte(0)}, und wer das Ankunftsfeld eines Tages verschiebt, aendert dann
     * die eine Stelle und uebersieht die andere.
     */
    public int ankunftsMitte() {
        return feldMitte(ANKUNFTSFELD);
    }
}
