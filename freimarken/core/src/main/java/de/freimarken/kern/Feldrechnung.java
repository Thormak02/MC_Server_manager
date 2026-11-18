package de.freimarken.kern;

/**
 * Die Rechnung eines Feldrasters: gleich grosse Felder mit Zwischenraum, endlos gedacht.
 *
 * <p>Zwei Welten rechnen so, und beide aus demselben Grund: Die Freistaedte
 * ({@link Stadtfeldplan}, Felder zu 256 mit 32 Bloecken Leere) und Hafenwerder
 * ({@link Inselplan}, Inseln zu 128 mit 128 Bloecken Meer). Was dazwischen liegt, ist
 * verschieden - dort eine unsichtbare Wand und Nichts, hier offenes Wasser -, aber die
 * FRAGEN sind wortgleich: In welchem Feld liegt dieser Block, wo faengt es an, wo ist
 * seine Mitte, und wie weit muss eine Weltgrenze reichen, damit kein Feld angeschnitten
 * wird.
 *
 * <p><b>Warum das eine eigene Stelle ist und keine Abschrift</b> [17.11.2026]: Eine der
 * Antworten ist nicht offensichtlich, und an ihr haengt der Warp. {@link #feldMitte} nimmt
 * bei gerader Kante den KLEINEREN der beiden mittleren Bloecke; welcher, ist beliebig,
 * aber es muss immer derselbe sein - ein Warp, der mal hierhin und mal dorthin setzt, ist
 * keiner. Und {@link #felderFuer} rundet auf eine GERADE Feldzahl ab, damit die Felder
 * symmetrisch um den Ursprung liegen. Zwei Abschriften dieser beiden Zeilen waeren zwei
 * Orte, an denen sie auseinanderlaufen koennen.
 *
 * <p>Nicht hier steht, WAS im Zwischenraum liegt, wie breit er mindestens sein muss und
 * ob das Feld eine innere Gestalt hat. Das ist die Sache des Plans, der diese Rechnung
 * haelt: Bei den Stadtfeldern braucht die Leere Platz fuer zwei Barrieren, bei den Inseln
 * traegt das Feld eine Kuestenlinie.
 *
 * @param feldKante    Kantenlaenge eines Feldes in Bloecken, mindestens 1
 * @param zwischenraum Bloecke zwischen zwei Feldern, mindestens 1
 */
public record Feldrechnung(int feldKante, int zwischenraum) {

    public Feldrechnung {
        if (feldKante < 1) {
            throw new IllegalArgumentException(
                    "Die Feldkante muss mindestens 1 sein, nicht " + feldKante);
        }
        if (zwischenraum < 1) {
            throw new IllegalArgumentException(
                    "Der Zwischenraum muss mindestens 1 sein, nicht " + zwischenraum);
        }
    }

    /** Kantenlaenge einer Rasterzelle: Feld plus Zwischenraum. */
    public int rasterWeite() {
        return feldKante + zwischenraum;
    }

    /**
     * Wo diese Weltkoordinate innerhalb ihrer Rasterzelle liegt - 0 bis
     * {@code rasterWeite() - 1}.
     *
     * <p>{@code floorMod} und nicht {@code %}: Im Negativen gibt der Rest-Operator
     * negative Zahlen, und damit laege der Block -1 nicht am Ende der Zelle davor,
     * sondern nirgends. Die Welt geht aber in beide Richtungen.
     */
    public int imRaster(int wert) {
        return Math.floorMod(wert, rasterWeite());
    }

    /** Ob diese Weltkoordinate auf einer Achse im FELD liegt und nicht dazwischen. */
    public boolean imFeld(int wert) {
        return imRaster(wert) < feldKante;
    }

    /**
     * Die Feldnummer dieser Weltkoordinate auf einer Achse.
     *
     * <p>Auch im Zwischenraum beantwortbar, und dort nennt sie das Feld DAVOR - wie
     * {@code Rasterplan.parzelleX} auf dem Weg. Wer wissen will, ob ueberhaupt ein Feld
     * gemeint ist, fragt {@link #imFeld}: Eine Zeile aus dem Zwischenraum heraus zu
     * schreiben traefe immer den kleineren der beiden Nachbarn.
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

    /** Die kleinste Kante, die noch eine Welt ergibt: zwei Felder und ein Zwischenraum. */
    public int mindestKante() {
        return 2 * rasterWeite() + zwischenraum;
    }

    /**
     * Wie viele Felder je Achse in diese Wunschkante passen - abgerundet, immer gerade.
     *
     * <p><b>Die Zahl in der Einstellungsdatei ist ein Wunsch und kein Mass</b> [gefunden
     * 11.11.2026 bei den Freistaedten]: 6.000 ist kein Vielfaches von 288. Eine Grenze von
     * 6.000 um 0/0 schnitt 84 Felder an - und schlimmer: Sie lief mitten durch Feld 10,
     * dessen Mitte bei 3.007 liegt. Ein Warp auf dieses Feld haette den Spieler VOR die
     * eigene Weltgrenze gesetzt, in eine Welt, aus der ihn der Rand zurueckdraengt.
     *
     * <p><b>Gerade muss die Zahl sein</b>, damit die Felder symmetrisch um den Ursprung
     * liegen und die Mitte wirklich {@link #weltmitte} ist. Bei ungerader Zahl laege das
     * Fenster um ein ganzes Feld schief, und Feld 0/0 - das Ankunftsfeld beider Welten -
     * saesse nicht mehr in der Mitte.
     *
     * <p>Unterhalb von {@link #mindestKante} kommt 0 heraus, also eine Welt ohne ein
     * einziges Feld. Diese Lage faengt der Aufrufer ab, bevor er herkommt; hier zu werfen
     * brauchte einen Grund, den es nicht gibt - die Rechnung ist richtig.
     */
    public int felderFuer(int gewuenschteKante) {
        return 2 * ((gewuenschteKante - zwischenraum) / (2 * rasterWeite()));
    }

    /**
     * Die Kantenlaenge der Weltgrenze bei dieser Feldzahl - ein Durchmesser, kein Radius.
     *
     * <p>Ein Zwischenraum MEHR als Felder: Er liegt zwischen je zwei Feldern und ausserdem
     * als Ring aussen herum. Damit liegt jedes erlaubte Feld vollstaendig innen, samt
     * seinem Rand auf allen vier Seiten.
     */
    public double weltkante(int felderJeAchse) {
        return (double) rasterWeite() * felderJeAchse + zwischenraum;
    }

    /**
     * Die Mitte der Weltgrenze auf beiden Achsen - immer die halbe Zwischenraumbreite
     * nach Westen.
     *
     * <p>Unabhaengig davon, wie viele Felder die Grenze umfasst: Feld 0 beginnt bei 0 und
     * der Ring davor ist ebenso breit wie jeder andere Zwischenraum. Deshalb bleibt die
     * Mitte bei jeder spaeteren Erweiterung gleich, und Erweitern ist rein additiv - was
     * heute drin liegt, liegt auch danach drin.
     */
    public double weltmitte() {
        return -zwischenraum / 2.0;
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Rings. */
    public int kleinsterBlock(int vonFeld) {
        return feldAnfang(vonFeld) - zwischenraum;
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Rings. */
    public int groessterBlock(int bisFeld) {
        return feldAnfang(bisFeld) + rasterWeite() - 1;
    }
}
