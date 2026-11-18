package de.freimarken.kern;

/**
 * Die Geometrie von Hafenwerder: Wo eine Insel liegt, wo offenes Meer ist, wo die Kueste.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft - wie {@link Stadtfeldplan} und
 * {@link Rasterplan}. Derselbe Plan beantwortet vier Fragen, die sonst an vier Stellen
 * auseinanderlaufen wuerden: Was erzeugt der Weltgenerator? Wem gehoert der Block, auf den
 * jemand klickt? Wo steht ein Spieler, der in Hafenwerder ankommt? Und wie weit reicht die
 * Weltgrenze ({@link Inselgrenze})?
 *
 * <p><b>Die Entscheidung dahinter</b> [18.11.2026]: Plan 4.1 sagte fuer Hafenwerder
 * "Gelaende: natuerlich gewachsen" und 6.2 "Meerinsel ~128x128, 75.000 Mark". Dasselbe
 * sagte der Plan fuer die Freistaedte, und am 11.11.2026 wurde daraus ein festes Raster
 * auf gebauter Welt - mit drei Gruenden, die hier wortgleich gelten:
 *
 * <ul>
 *   <li>Ohne gemeinsame Kante gibt es keinen Grenzstreit und niemanden, der an die Mauer
 *       des Nachbarn baut. Zwischen zwei Inseln liegt offenes Meer.
 *   <li>Die Zugehoerigkeit eines Blocks muss eine PUNKTABFRAGE bleiben: Sie laeuft bei
 *       jedem gesetzten Block, und ein Quadervergleich waere spuerbar teurer.
 *   <li>Gebaut statt gewachsen, weil sonst die eine Insel im Flachwasser und die naechste
 *       halb im Berg laege - und EIN Festpreis von 75.000 Mark kann nicht fuer beide
 *       derselbe sein. Jede Insel ist darum DIESELBE Insel, nur anderswo.
 * </ul>
 *
 * <p>Aufbau einer Rasterzelle bei Insel 128 und Meer 128 (Rasterweite 256), auf einer
 * Achse:
 *
 * <pre>
 *     0 .. 127   INSELFELD    (128 Bloecke, das Eigentum - Land UND eigenes Wasser)
 *   128 .. 255   OFFENES_MEER (128 Bloecke, niemandem gehoerend und unbebaubar)
 * </pre>
 *
 * <p><b>Das Feld ist das Eigentum, nicht die Insel.</b> Wer eine Meerinsel kauft, bekommt
 * 128x128 Bloecke, und darin liegt die Insel mit einem Wasserring um sich herum: Das ist
 * sein Hafen, seine Bucht, sein Platz fuer einen Steg. Nur so bleibt die Zugehoerigkeit
 * eine Punktabfrage auf {@code (welt, x, z)} - eine Kuestenlinie als Eigentumsgrenze
 * waere bei jedem Block eine Formel, und bei jedem aufgeschuetteten Block eine andere.
 *
 * <p><b>Es gibt keine Barriere</b>, und das ist der eine Unterschied zu den Stadtfeldern.
 * Dort trennt Leere, und Leere traegt nur, solange niemand hineinfaellt - darum die
 * unsichtbare Wand. Hier trennt Wasser, und Wasser traegt von selbst: Hinueberschwimmen
 * soll man duerfen (Plan 4.1 nennt Bootsrennen und Handelsrouten), nur bauen nicht. Das
 * verbietet die Schutzwache und nicht die Welt.
 *
 * <p><b>Feld 0/0 ist die ANKUNFTSINSEL</b> und gehoert niemandem: Dort landet das Tor aus
 * Altfurt (Plan 2.1, "Hafen von Altfurt"). Ein Tor braucht ein Ziel, und "irgendwo im
 * Meer" ist keins.
 */
public final class Inselplan {

    /** Was an einer Stelle liegt - auf der Ebene des EIGENTUMS. */
    public enum Feld {
        /** Das Eigentum einer Meerinsel: 128x128 Bloecke, Land und eigenes Wasser. */
        INSELFELD,
        /** Das Meer zwischen den Inseln. Gehoert niemandem, bebaubar ist es nicht. */
        OFFENES_MEER
    }

    /** Was an einer Stelle liegt - auf der Ebene des GELAENDES. */
    public enum Gestalt {
        /** Inselinneres: Wiese ueber Erde. */
        LAND,
        /** Der Sandstreifen am Wasser - rundherum gleich breit. */
        STRAND,
        /** Wasser, gleich ob im eigenen Hafen oder auf offener See. */
        WASSER
    }

    /** Das Feld, das niemandem gehoert - auf beiden Achsen. */
    public static final int ANKUNFTSINSEL = 0;

    /** Die Kante eines Inselfeldes in Bloecken - Plan 6.2: rund 128x128. */
    public static final int INSEL_KANTE = 128;

    /**
     * Das offene Meer zwischen zwei Inselfeldern.
     *
     * <p>So breit wie eine Insel selbst, und das ist eine Zahl mit zwei Gruenden. Zum
     * einen soll keine Insel den Eindruck einer NACHBARIN machen: Wer vom Strand aus
     * hinuebersieht, sieht Wasser und nicht den Zaun des Naechsten. Zum anderen ist das
     * Meer die einzige Flaeche dieser Welt, auf der niemand bauen darf - es ist das
     * oeffentliche Wasser des Archipels, und ein schmaler Kanal waere keines.
     */
    public static final int MEER_BREITE = 128;

    /**
     * Wie weit das Land vom Inselmittelpunkt reicht, an der Wasserlinie gemessen.
     *
     * <p>Sechsundfuenfzig von vierundsechzig: An der breitesten Stelle reicht die Insel
     * von Block 9 bis Block 118 eines Feldes, ist also 110 Bloecke breit, und es bleiben
     * neun Bloecke eigenes Wasser zum Rand des Feldes - in den Ecken mehr. Diese Bloecke
     * sind kein Rest, sondern der Hafen, und sie sind der Grund, warum die Insel das Feld
     * nie beruehrt: Ein Strand, der genau auf der Eigentumsgrenze endet, waere ein
     * Strand, an dem jeder Schritt ins Wasser ein Schritt in fremdes Gebiet ist.
     *
     * <p>Aus derselben Zahl folgt die Tiefe des Meeres, siehe {@link #meerTiefe}.
     */
    public static final int KUESTE_RADIUS = 56;

    /** Wie breit der Sandstreifen am Wasser ist - vier Bloecke, ein Schritt und ein Ruf. */
    public static final int STRAND_BREITE = 4;

    /**
     * Die Form der Insel: {@code |dx|^4 + |dz|^4 <= r^4}.
     *
     * <p><b>Vier und nicht zwei</b>, denn zwei waere ein Kreis, und ein Kreis in einem
     * Quadrat verschenkt die Ecken: Gemessen gibt der Kreis 9.660 Bloecke Land, die
     * vierte Potenz 11.364 - rund ein Sechstel mehr, und zwar an den vier Stellen, an
     * denen sonst gebaut wuerde. Mit vier entsteht ein gerundetes Quadrat.
     *
     * <p>Vier und nicht acht, obwohl acht noch einmal 550 Bloecke mehr gaebe: Bei acht
     * ist die Form von einem Quadrat kaum zu unterscheiden (11.916 von 12.100), und eine
     * quadratische Insel ist keine.
     */
    public static final int KUESTENFORM = 4;

    private final Feldrechnung rechnung;
    private final int kuesteRadius;
    private final int strandBreite;

    /**
     * @param rechnung     das Feldraster, Feldkante und Meerbreite
     * @param kuesteRadius wie weit das Land vom Mittelpunkt reicht; muss in das Feld
     *                     passen und einen Strand tragen
     * @param strandBreite Breite des Sandstreifens, mindestens 1 und schmaler als das Land
     */
    public Inselplan(Feldrechnung rechnung, int kuesteRadius, int strandBreite) {
        if (kuesteRadius < 1) {
            throw new IllegalArgumentException(
                    "Der Kuestenradius muss mindestens 1 sein, nicht " + kuesteRadius);
        }
        if (2 * kuesteRadius > rechnung.feldKante()) {
            // Sonst reicht das Land ueber das eigene Feld hinaus - die Insel laege zum
            // Teil im offenen Meer, das niemandem gehoert, und der Nachbar sechzehn
            // Bloecke weiter koennte auf fremdem Land stehen.
            throw new IllegalArgumentException("Eine Insel mit Radius " + kuesteRadius
                    + " passt nicht in ein Feld von " + rechnung.feldKante()
                    + " Bloecken - sie braucht hoechstens die halbe Feldkante.");
        }
        if (strandBreite < 1 || strandBreite >= kuesteRadius) {
            throw new IllegalArgumentException("Der Strand muss mindestens 1 Block breit"
                    + " und schmaler als die Insel (" + kuesteRadius + ") sein, nicht "
                    + strandBreite);
        }
        this.rechnung = rechnung;
        this.kuesteRadius = kuesteRadius;
        this.strandBreite = strandBreite;
    }

    /** Der Plan von Hafenwerder: 128er Inselfelder, 128 Bloecke Meer, Radius 56. */
    public static Inselplan hafenwerder() {
        return new Inselplan(new Feldrechnung(INSEL_KANTE, MEER_BREITE), KUESTE_RADIUS,
                STRAND_BREITE);
    }

    /** Die Rasterrechnung dieses Plans - die eine Quelle fuer Feldnummer und Mitte. */
    public Feldrechnung rechnung() {
        return rechnung;
    }

    public int feldKante() {
        return rechnung.feldKante();
    }

    public int meerBreite() {
        return rechnung.zwischenraum();
    }

    public int kuesteRadius() {
        return kuesteRadius;
    }

    public int strandBreite() {
        return strandBreite;
    }

    /** Kantenlaenge einer Rasterzelle: Inselfeld plus Meer. */
    public int rasterWeite() {
        return rechnung.rasterWeite();
    }

    /**
     * Wie tief das Meer ist, von der Wasserlinie bis zum Grund.
     *
     * <p><b>Abgeleitet und keine eigene Zahl</b>, und darin steckt die ganze Gestalt der
     * Insel unter Wasser: Der Hang faellt um einen Block, sooft er einen Block nach aussen
     * geht. Bei Radius 56 im 128er Feld sind das acht Bloecke bis zum Feldrand - und genau
     * dort, acht Bloecke tief, ist der Meeresgrund.
     *
     * <p>Das ist die Zahl, die alles zusammenhaelt: Der Fuss der Insel endet GENAU an der
     * Eigentumsgrenze, und von dort bis zum Nachbarfeld ist der Grund eben. Eine eigene
     * Tiefe daneben waere entweder eine senkrechte Riffwand (zu tief) oder ein Fuss, der
     * ins fremde Wasser laeuft (zu flach).
     */
    public int meerTiefe() {
        return rechnung.feldKante() / 2 - kuesteRadius;
    }

    /**
     * Wem dieser Block gehoeren KANN - Inselfeld oder offenes Meer.
     *
     * <p>Beide Achsen einzeln, und das Meer gewinnt: Nur wo auf BEIDEN Achsen ein Feld
     * liegt, ist es ein Inselfeld. Was sonst ueber Eck liegt, ist Meer - es gibt keinen
     * dritten Zustand wie die Barriere der Stadtfelder.
     */
    public Feld feldBei(int x, int z) {
        return rechnung.imFeld(x) && rechnung.imFeld(z)
                ? Feld.INSELFELD
                : Feld.OFFENES_MEER;
    }

    /** Die Feldnummer dieser Weltkoordinate auf einer Achse - siehe {@link Feldrechnung}. */
    public int feldNummer(int wert) {
        return rechnung.feldNummer(wert);
    }

    /** Die kleinste Weltkoordinate dieses Feldes auf einer Achse. */
    public int feldAnfang(int nummer) {
        return rechnung.feldAnfang(nummer);
    }

    /** Die Mitte eines Feldes auf einer Achse - der Ankunftspunkt. */
    public int feldMitte(int nummer) {
        return rechnung.feldMitte(nummer);
    }

    /**
     * Was an dieser Weltkoordinate OBEN liegt: Inselinneres, Strand oder Wasser.
     *
     * <p>Im offenen Meer immer Wasser, und auf einem Inselfeld das, was die Kuestenlinie
     * sagt. Gemessen wird in HALBEN Bloecken vom wahren Feldmittelpunkt aus - siehe
     * {@link #abstandHalb}.
     */
    public Gestalt gestaltBei(int x, int z) {
        if (feldBei(x, z) == Feld.OFFENES_MEER) {
            return Gestalt.WASSER;
        }
        int dx = abstandHalb(x);
        int dz = abstandHalb(z);
        if (!innerhalb(dx, dz, kuesteRadius)) {
            return Gestalt.WASSER;
        }
        return innerhalb(dx, dz, kuesteRadius - strandBreite)
                ? Gestalt.LAND
                : Gestalt.STRAND;
    }

    /**
     * Ob an dieser Stelle in dieser TIEFE unter der Wasserlinie noch Insel liegt.
     *
     * <p>Der Hang: Je einen Block tiefer reicht die Insel einen Block weiter nach aussen.
     * Bei {@link #meerTiefe} ist er am Rand des eigenen Feldes angekommen und hoert dort
     * auf - tiefer geht es nicht, dort liegt der Meeresgrund.
     *
     * <p>Die Tiefe 0 ist die Wasserlinie selbst und damit gleichbedeutend mit
     * {@link #gestaltBei}: Was dort nicht Wasser ist, ist Insel.
     *
     * @param tiefe Bloecke unter der Wasserlinie, 0 oder mehr
     */
    public boolean inselInTiefe(int x, int z, int tiefe) {
        if (tiefe < 0 || tiefe > meerTiefe()) {
            return false;
        }
        if (feldBei(x, z) == Feld.OFFENES_MEER) {
            return false;
        }
        return innerhalb(abstandHalb(x), abstandHalb(z), kuesteRadius + tiefe);
    }

    /**
     * Wie tief unter der Wasserlinie der oberste Block dieser Spalte liegt.
     *
     * <p><b>Die eine Frage, die der Weltgenerator je Spalte stellt</b>, und sie beschreibt
     * die ganze Welt mit einer Zahl zwischen 0 und {@link #meerTiefe}: 0 ist das
     * Inselinnere, 1 bis 7 der Hang unter Wasser, 8 der Meeresgrund. Eine Spalte ist
     * damit immer dieselbe Folge - Grundgestein, Stein, Erde, ein Oberflaechenblock und
     * darueber Wasser bis zur Wasserlinie -, und nur ihre Hoehe aendert sich.
     *
     * <p>Das offene Meer und der Grund einer Bucht sind GLEICH tief, und das ist kein
     * Zufall, sondern {@link #meerTiefe}: Der Fuss der Insel endet genau am Rand des
     * eigenen Feldes, und von dort bis zum Nachbarfeld ist der Grund eben. Auch die vier
     * ECKEN eines Inselfeldes erreicht der Fuss nicht - die Kuestenlinie ist ein
     * gerundetes Quadrat, kein Quadrat -, und dort geht das eigene Wasser ohne Stufe in
     * das offene ueber.
     *
     * <p>Gesucht wird von oben, weil der Hang nach unten nur WAECHST: Die erste Tiefe,
     * in der hier Insel liegt, ist der Gipfel.
     */
    public int tiefeDerOberflaeche(int x, int z) {
        for (int tiefe = 0; tiefe < meerTiefe(); tiefe++) {
            if (inselInTiefe(x, z, tiefe)) {
                return tiefe;
            }
        }
        return meerTiefe();
    }

    /**
     * Der Abstand dieser Weltkoordinate vom wahren Mittelpunkt ihres Feldes, in HALBEN
     * Bloecken.
     *
     * <p><b>Halbe Bloecke, weil die Feldkante gerade ist</b> und es deshalb keinen
     * Mittelblock gibt: Bei 128 liegt die Mitte zwischen 63 und 64. Ganze Bloecke vom
     * Block 63 aus gezaehlt machten die Insel um einen Block unsymmetrisch - acht Bloecke
     * Hafen auf der einen Seite, sieben auf der anderen -, und ihr Fuss reichte auf der
     * einen Seite einen Block ins fremde Wasser. In halben Bloecken geht es auf: Der
     * erste Block eines Feldes hat -127, der letzte +127.
     */
    private int abstandHalb(int wert) {
        int imFeld = rechnung.imRaster(wert);
        return 2 * imFeld - (rechnung.feldKante() - 1);
    }

    /**
     * Ob dieser Punkt innerhalb der Kuestenlinie zu diesem Radius liegt.
     *
     * <p>{@code |dx|^4 + |dz|^4 <= r^4} in halben Bloecken - der Radius wird dafuer auf
     * dieselbe Skala gebracht. Die vierte Potenz ist skaleninvariant, die Form bleibt
     * also dieselbe wie in ganzen Bloecken.
     *
     * <p>{@code long} und nicht {@code int}: 127 zur vierten ist rund 260 Millionen, zwei
     * davon zusammen ueber 520 Millionen - das passt noch, aber der naechste Plan mit
     * einer groesseren Insel liefe ueber. Ein Ueberlauf hier ergaebe eine Insel mit
     * Loechern, und zwar nur an den Ecken.
     */
    private static boolean innerhalb(int dxHalb, int dzHalb, int radius) {
        long grenze = 2L * radius - 1L;
        return hoch4(dxHalb) + hoch4(dzHalb) <= hoch4(grenze);
    }

    private static long hoch4(long wert) {
        long quadrat = wert * wert;
        return quadrat * quadrat;
    }

    /**
     * Ob dieses Feld die ANKUNFTSINSEL ist - Feld 0/0, und sie gehoert niemandem.
     *
     * <p>Hier landet das Tor aus Altfurt. Nehmen laesst sie sich darum nicht; die Pruefung
     * steht an EINER Stelle, damit nicht der Befehl das eine und der Schutz das andere
     * meint.
     */
    public boolean istAnkunftsinsel(int feldX, int feldZ) {
        return feldX == ANKUNFTSINSEL && feldZ == ANKUNFTSINSEL;
    }

    /**
     * Die Mitte der Ankunftsinsel auf einer Achse - der Erscheinungspunkt der Welt.
     *
     * <p>Damit die Null nicht ein zweites Mal im Generator steht: Dort hiesse es
     * {@code feldMitte(0)}, und wer die Ankunftsinsel eines Tages verschiebt, aendert
     * dann die eine Stelle und uebersieht die andere.
     */
    public int ankunftsMitte() {
        return feldMitte(ANKUNFTSINSEL);
    }
}
