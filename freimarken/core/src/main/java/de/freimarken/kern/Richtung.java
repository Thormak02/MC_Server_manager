package de.freimarken.kern;

/**
 * Die vier Himmelsrichtungen auf dem Raster.
 *
 * <p>Steht im Kern, weil die Zuordnung "Norden ist negatives Z" einmal festliegen muss.
 * In Minecraft ist das so, und wer sie an zwei Stellen aufschreibt, hat sie irgendwann
 * an einer davon umgedreht.
 *
 * <p>Zugleich ist diese Aufzaehlung die Definition von "benachbart": Fuer jeden Wert gilt
 * {@code |dx| + |dz| == 1}, also orthogonal und nie diagonal. Dadurch ist "nicht
 * benachbart" kein Bescheid, den jemand lesen koennte, sondern eine Eingabe, die es gar
 * nicht gibt. Diagonal waere auch sachlich falsch: Dort liegt keine gemeinsame Kante,
 * sondern eine Wegkreuzung.
 */
public enum Richtung {

    NORD("nord", 0, -1),
    OST("ost", 1, 0),
    SUED("sued", 0, 1),
    WEST("west", -1, 0);

    private final String wort;
    private final int dx;
    private final int dz;

    Richtung(String wort, int dx, int dz) {
        this.wort = wort;
        this.dx = dx;
        this.dz = dz;
    }

    /** Wie die Richtung im Befehl geschrieben wird - umlautfrei, damit sie tippbar bleibt. */
    public String wort() {
        return wort;
    }

    /** Verschiebung der Parzellennummer auf der X-Achse. */
    public int dx() {
        return dx;
    }

    /** Verschiebung der Parzellennummer auf der Z-Achse. */
    public int dz() {
        return dz;
    }
}
