package de.freimarken.kern;

/**
 * Die Geometrie der Freistaedte: Wo ist Stadtfeld, wo Barriere, wo Leere.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft - dasselbe Vorgehen wie bei
 * {@link Rasterplan}. Derselbe Plan beantwortet drei Fragen, die sonst an drei Stellen
 * auseinanderlaufen wuerden: Was erzeugt der Weltgenerator? Wem gehoert der Block, auf
 * den jemand klickt? Wo steht ein Spieler, der mit {@code /warp freistadt} ankommt?
 *
 * <p>Aufbau einer Rasterzelle bei Feld 256 und Leere 32 (Kantenlaenge 288):
 *
 * <pre>
 *     0 .. 255   STADTFELD  (256 Felder, Bauland der Stadt)
 *         256    BARRIERE   (die Ostwand DIESES Feldes)
 *   257 .. 286   LEERE      (30 Felder ohne jeden Block)
 *         287    BARRIERE   (die Westwand des naechsten Feldes)
 * </pre>
 *
 * <p><b>Die Nummer bleibt dabei bei DIESEM Feld</b>: {@link #feldNummer} gibt fuer die
 * ganze Zelle 0..287 dieselbe Zahl, auch fuer die Westwand der Nachbarin bei 287 - genau
 * wie {@code Rasterplan.parzelleX} auf dem Weg das Feld davor nennt. Wer eine FLAECHE
 * aufspannen will (eine Stadt raeumen, ein Grundstueck zuruecksetzen), darf darum nicht
 * von {@code feldAnfang(n)} bis {@code feldAnfang(n + 1) - 1} rechnen: Der letzte Block
 * dieser Spanne ist die Wand der Nachbarin, und wer sie loescht, oeffnet ihr die Seite
 * zur Leere. Das Bauland allein ist {@code feldAnfang(n)} bis
 * {@code feldAnfang(n) + feldKante - 1}.
 *
 * <p><b>Die Barriere steht in der LEERE und nicht auf dem Bauland</b> - dieselbe
 * Entscheidung wie bei der Mauer der Marktstadt, die auf dem Weg steht: Wer 256
 * verspricht, sollte 256 liefern. Ein Stadtfeld ist damit volle 256x256 bebaubar.
 *
 * <p><b>Warum ueberhaupt eine Barriere</b> [entschieden 11.11.2026]: Die Leere trennt die
 * Staedte, aber sie trennt sie nur, solange niemand hineinfaellt und niemand hinueber
 * baut. Barrierebloecke sind unsichtbar, unzerstoerbar und ohne Unterlage - sie halten
 * beides auf, ohne die Sicht zu verstellen.
 *
 * <p><b>Feld 0/0 ist das ANKUNFTSFELD</b> und gehoert niemandem: Dort landet das Tor aus
 * Altfurt, dort steht der Verteiler zu den oeffentlichen Staedten. Ein Tor braucht ein
 * Ziel, und "irgendwo in der Leere" ist keins.
 */
public final class Stadtfeldplan {

    /** Was an einer Stelle liegt. */
    public enum Feld {
        /** Bauland einer Freistadt. */
        STADTFELD,
        /** Der unsichtbare Rand um ein Stadtfeld - steht in der Leere. */
        BARRIERE,
        /** Nichts. Kein Block, kein Boden, kein Weg. */
        LEERE
    }

    /** Das Feld, das niemandem gehoert - auf beiden Achsen. */
    public static final int ANKUNFTSFELD = 0;

    /** Die Kante eines Stadtfeldes in Bloecken - Plan 6.2: rund 256x256. */
    public static final int FELD_KANTE = 256;

    /**
     * Die Leere zwischen zwei Feldern, Barrieren eingerechnet.
     *
     * <p>Zweiunddreissig, und das ist gemessen und nicht gegriffen: Ein Spieler springt
     * ohne Hilfsmittel knapp vier Bloecke weit, mit Schwung und Trank etwas mehr. Dreissig
     * freie Bloecke sind jenseits von allem, was ohne Bruecke geht - und bruecken kann
     * niemand, weil ausserhalb eines Feldes das Bauen verboten ist.
     */
    public static final int LEERE_BREITE = 32;

    /**
     * Die Kante einer GRUNDSTUECKSZELLE - die kleinste Einheit, die vergeben wird.
     *
     * <p><b>Sechzehn, also genau ein Chunk</b>, und das ist mehr als ein rundes Mass:
     * Ein Grundstueck aus ganzen Zellen liegt damit nie halb in einem Chunk. Was das
     * eines Tages wert ist, zeigt der Blick auf die Nachbarschaft - Chunkladen,
     * Trichter ueber die Grenze, das Entladen eines Gebiets: alles rechnet in Chunks.
     *
     * <p>Und es ist die richtige Groesse fuer ein Stadthaus. Wer mehr will, bekommt
     * mehrere Zellen in einem Rechteck; ein Feld traegt 16 x 16 = 256 davon.
     */
    public static final int ZELLE_KANTE = 16;

    private final int feldKante;
    private final int leereBreite;

    /**
     * @param feldKante   Kantenlaenge eines Stadtfeldes, mindestens 1
     * @param leereBreite Breite der Leere samt beider Barrieren, mindestens 2
     */
    public Stadtfeldplan(int feldKante, int leereBreite) {
        if (feldKante < 1) {
            throw new IllegalArgumentException(
                    "Die Feldkante muss mindestens 1 sein, nicht " + feldKante);
        }
        if (leereBreite < 2) {
            throw new IllegalArgumentException(
                    "Die Leere braucht Platz fuer zwei Barrieren, also mindestens 2,"
                            + " nicht " + leereBreite);
        }
        this.feldKante = feldKante;
        this.leereBreite = leereBreite;
    }

    /** Der Plan der Freistaedte: 256er Felder, 32 Bloecke Leere. */
    public static Stadtfeldplan freistaedte() {
        return new Stadtfeldplan(FELD_KANTE, LEERE_BREITE);
    }

    public int feldKante() {
        return feldKante;
    }

    public int leereBreite() {
        return leereBreite;
    }

    /** Kantenlaenge einer Rasterzelle: Stadtfeld plus Leere. */
    public int rasterWeite() {
        return feldKante + leereBreite;
    }

    /**
     * Was an dieser Weltkoordinate liegt.
     *
     * <p>Die beiden Achsen werden einzeln eingeordnet und dann zusammengefuehrt, und die
     * Reihenfolge ist eine Entscheidung: <b>Die LEERE gewinnt immer.</b> Eine Barriere
     * mitten in der Leere waere eine unsichtbare Wand, an der niemand steht und die
     * nichts begrenzt - sie stuende quer durch den Himmel zwischen vier Staedten.
     *
     * <p>Zwei Stadtfeldachsen ergeben Bauland; was danach uebrig bleibt, ist der Rand
     * eines Feldes und damit Barriere - auch ueber Eck, denn eine Ecke ohne Pfosten
     * waere genau die Luecke, durch die jemand hinausgeht.
     */
    public Feld feldBei(int x, int z) {
        Feld aufX = aufAchse(x);
        Feld aufZ = aufAchse(z);
        if (aufX == Feld.LEERE || aufZ == Feld.LEERE) {
            return Feld.LEERE;
        }
        if (aufX == Feld.STADTFELD && aufZ == Feld.STADTFELD) {
            return Feld.STADTFELD;
        }
        return Feld.BARRIERE;
    }

    private Feld aufAchse(int wert) {
        int imRaster = Math.floorMod(wert, rasterWeite());
        if (imRaster < feldKante) {
            return Feld.STADTFELD;
        }
        if (imRaster == feldKante || imRaster == rasterWeite() - 1) {
            return Feld.BARRIERE;
        }
        return Feld.LEERE;
    }

    /**
     * Die Feldnummer dieser Weltkoordinate auf einer Achse.
     *
     * <p>Auch in der Leere beantwortbar, und dort nennt sie das Feld DAVOR - wie
     * {@code Rasterplan.parzelleX} auf dem Weg. Wer wissen will, ob ueberhaupt ein Feld
     * gemeint ist, fragt {@link #feldBei}: Eine Zeile aus der Leere heraus zu schreiben
     * traefe immer den kleineren der beiden Nachbarn, also unter Umstaenden eine fremde
     * Stadt.
     */
    public int feldNummer(int wert) {
        return Math.floorDiv(wert, rasterWeite());
    }

    /** Die kleinste Weltkoordinate dieses Feldes auf einer Achse. */
    public int feldAnfang(int nummer) {
        return nummer * rasterWeite();
    }

    /**
     * Die Mitte eines Feldes auf einer Achse - der Ankunftspunkt.
     *
     * <p>Bei gerader Kante gibt es keine echte Mitte; genommen wird der kleinere der
     * beiden mittleren Bloecke. Wichtig ist nicht, WELCHER, sondern dass immer derselbe
     * herauskommt: Ein Warp, der mal hierhin und mal dorthin setzt, ist keiner.
     */
    public int feldMitte(int nummer) {
        return feldAnfang(nummer) + (feldKante - 1) / 2;
    }

    /**
     * Ob die Grundstueckszellen in diesem Mass AUFGEHEN.
     *
     * <p><b>Eine Frage und kein Riegel im Konstruktor</b>, und das ist ein Nachtrag mit
     * Grund [12.11.2026]: Ein Plan, in dem die Zellen nicht aufgehen, ist fuer die WELT
     * voellig gueltig - die Barriere, die Leere und der Ankunftspunkt rechnen alle in
     * Bloecken. Nur GRUNDSTUECKE kann er nicht tragen. Ein Wurf im Konstruktor nahm
     * darum zwei Proben die Grundlage, die die Barriere bei kleinster Leere messen
     * ({@code new Stadtfeldplan(256, 2)}), und das zu Recht: 258 ist kein Vielfaches
     * von 16, aber jeder Block dieser Leere ist trotzdem richtig eingeordnet.
     *
     * <p>Gefragt wird sie dort, wo Grundstuecke entstehen - dann einmal beim Bauen und
     * nicht bei jedem Zuschnitt. Das ist der Riegel am engsten Verbraucher.
     *
     * <p><b>Beide Masse muessen aufgehen, und zwar aus zwei verschiedenen Gruenden:</b>
     * die Feldkante, sonst liegt die letzte Zelle eines Feldes halb auf der Barriere und
     * ein Grundstueck darauf halb in der Leere; die Rasterweite, sonst beginnt das
     * naechste Feld mitten in einer Zelle - und DIESE Zelle gehoerte zwei Staedten, also
     * haetten zwei Besitzer auf denselben Bloecken Recht. Genau das koennte die
     * Punktabfrage nicht mehr entscheiden.
     */
    public boolean zellenGehenAuf() {
        return feldKante % ZELLE_KANTE == 0 && rasterWeite() % ZELLE_KANTE == 0;
    }

    /**
     * Die Zellnummer dieser Weltkoordinate auf einer Achse.
     *
     * <p><b>Global gezaehlt und nicht je Feld</b>, und das ist eine Entscheidung: Eine
     * feldrelative Nummer (0 bis 15) braeuchte bei jeder Frage das Feld dazu, und zwei
     * Zahlen, die nur zusammen etwas bedeuten, werden irgendwann einzeln benutzt. Eine
     * globale Zellnummer ist fuer sich eindeutig - wie die Feldnummer daneben.
     *
     * <p>In der Leere beantwortbar wie {@link #feldNummer}, und mit derselben Vorsicht:
     * Wer wissen will, ob ueberhaupt Bauland gemeint ist, fragt {@link #feldBei}.
     */
    public int zelleVon(int wert) {
        return Math.floorDiv(wert, ZELLE_KANTE);
    }

    /** Die kleinste Weltkoordinate dieser Zelle auf einer Achse. */
    public int zellenAnfang(int zelle) {
        return zelle * ZELLE_KANTE;
    }

    /** Die groesste Weltkoordinate dieser Zelle auf einer Achse - einschliesslich. */
    public int zellenEnde(int zelle) {
        return zellenAnfang(zelle) + ZELLE_KANTE - 1;
    }

    /** Wie viele Zellen auf eine Feldkante gehen. */
    public int zellenJeKante() {
        return feldKante / ZELLE_KANTE;
    }

    /** Die kleinste Zellnummer dieses Feldes auf einer Achse. */
    public int ersteZelle(int feldNummer) {
        return zelleVon(feldAnfang(feldNummer));
    }

    /** Die groesste Zellnummer dieses Feldes auf einer Achse - einschliesslich. */
    public int letzteZelle(int feldNummer) {
        return ersteZelle(feldNummer) + zellenJeKante() - 1;
    }

    /**
     * Ob diese Zelle ganz im Bauland eines Feldes liegt.
     *
     * <p>Gefragt wird der ANFANG der Zelle und ihr Ende, nicht ihre Mitte: Eine Zelle,
     * die halb auf der Barriere liegt, gaebe es nur bei einem Mass, das nicht passt -
     * und genau das verbietet der Konstruktor. Die Frage bleibt trotzdem stehen, weil
     * sie am Feldrand das Verlassen des Feldes faengt.
     */
    public boolean istBauzelle(int zelleX, int zelleZ) {
        return feldBei(zellenAnfang(zelleX), zellenAnfang(zelleZ)) == Feld.STADTFELD
                && feldBei(zellenEnde(zelleX), zellenEnde(zelleZ)) == Feld.STADTFELD;
    }

    /**
     * Ob dieses Feld das ANKUNFTSFELD ist - Feld 0/0, und es gehoert niemandem.
     *
     * <p>Hier landet das Tor aus Altfurt, hier steht der Verteiler zu den oeffentlichen
     * Staedten. Gruenden laesst es sich darum nicht; die Pruefung steht an EINER Stelle,
     * damit nicht der Befehl das eine und der Schutz das andere meint.
     */
    public boolean istAnkunftsfeld(int feldX, int feldZ) {
        return feldX == ANKUNFTSFELD && feldZ == ANKUNFTSFELD;
    }

    /**
     * Die Mitte des Ankunftsfeldes auf einer Achse - der Erscheinungspunkt der Welt.
     *
     * <p>Damit die Null nicht ein zweites Mal im Generator steht: Dort hiess es
     * {@code feldMitte(0)}, und wer das Ankunftsfeld eines Tages verschiebt, aendert
     * dann die eine Stelle und uebersieht die andere.
     */
    public int ankunftsMitte() {
        return feldMitte(ANKUNFTSFELD);
    }
}
