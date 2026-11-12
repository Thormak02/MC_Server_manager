package de.freimarken.kern;

/**
 * Ein Rechteck aus ganzen Grundstuecks-ZELLEN - die Flaeche eines Grundstuecks.
 *
 * <p><b>In Zellen und nicht in Bloecken</b>, und das ist der ganze Entwurf: Plan 6.1
 * verlangt fuer {@code darfBauen} eine billige Abfrage, weil sie bei JEDEM gesetzten
 * Block laeuft, und nennt den Quadervergleich dort ausdruecklich als das Teurere. In
 * Zellen gerechnet wird daraus wieder eine Ganzzahlfrage auf vier Grenzen - und der
 * Spieler merkt davon nichts, denn er bekommt genau den Zuschnitt, den er sich
 * abgesteckt hat, nur auf Chunkkanten gerundet.
 *
 * <p><b>Keine Hoehe</b>, anders als {@link Quader}: Ein Grundstueck gehoert von der
 * Weltunterkante bis zum Himmel. Eine Hoehengrenze waere ein zweiter Besitzer ueber dem
 * ersten - und die Frage, wem der Block an der Grenze gehoert, haette dann keine
 * Antwort, die ein Spieler erraten kann.
 *
 * <p><b>Immer normiert</b>: {@code von} ist stets die kleinere Zahl. Der Spieler steckt
 * zwei Ecken ab, und in welcher Reihenfolge er das tut, darf die Flaeche nicht aendern.
 * Normiert wird im Konstruktor und nicht beim Vergleichen - sonst muesste jede Stelle,
 * die vergleicht, es wieder tun, und eine von ihnen taete es nicht.
 */
public record Zellquader(int vonX, int vonZ, int bisX, int bisZ) {

    public Zellquader {
        if (vonX > bisX || vonZ > bisZ) {
            throw new IllegalArgumentException("Ein Zellquader wird normiert gebaut:"
                    + " von (" + vonX + "/" + vonZ + ") bis (" + bisX + "/" + bisZ
                    + ") - nimm Zellquader.zwischen(...), das dreht die Ecken selbst");
        }
    }

    /**
     * Der Quader zwischen zwei beliebig gesetzten Ecken.
     *
     * <p>Die EINE Stelle, an der gedreht wird. Wer selbst sortiert, baut die zweite -
     * und die naechste Ecke kommt aus einem Befehl, in dem jemand sie vergessen hat.
     */
    public static Zellquader zwischen(int einX, int einZ, int andererX, int andererZ) {
        return new Zellquader(Math.min(einX, andererX), Math.min(einZ, andererZ),
                Math.max(einX, andererX), Math.max(einZ, andererZ));
    }

    /** Ein Quader aus genau einer Zelle. */
    public static Zellquader eineZelle(int zelleX, int zelleZ) {
        return new Zellquader(zelleX, zelleZ, zelleX, zelleZ);
    }

    /** Ob diese Zelle darin liegt - die Frage des heissen Pfades. */
    public boolean enthaelt(int zelleX, int zelleZ) {
        return zelleX >= vonX && zelleX <= bisX && zelleZ >= vonZ && zelleZ <= bisZ;
    }

    /**
     * Ob zwei Quader sich ueberschneiden.
     *
     * <p>Die Frage beim Anlegen, und sie muss sie BEIDE Richtungen gleich beantworten:
     * Dass {@code a.schneidet(b)} und {@code b.schneidet(a)} dasselbe sagen, ist hier
     * keine Hoeflichkeit - der Aufrufer prueft ein neues Grundstueck gegen alle
     * vorhandenen, und welches von beiden er zuerst nennt, haengt an der Reihenfolge der
     * Ablage.
     */
    public boolean schneidet(Zellquader anderer) {
        return vonX <= anderer.bisX && bisX >= anderer.vonX
                && vonZ <= anderer.bisZ && bisZ >= anderer.vonZ;
    }

    /** Ob dieser Quader den anderen ganz umfasst. */
    public boolean umfasst(Zellquader anderer) {
        return vonX <= anderer.vonX && bisX >= anderer.bisX
                && vonZ <= anderer.vonZ && bisZ >= anderer.bisZ;
    }

    /** Zellen auf der X-Achse. */
    public int breite() {
        return bisX - vonX + 1;
    }

    /** Zellen auf der Z-Achse. */
    public int tiefe() {
        return bisZ - vonZ + 1;
    }

    /** Wie viele Zellen das Grundstueck umfasst - die Groesse, nach der es kostet. */
    public int zellen() {
        return breite() * tiefe();
    }

    /**
     * Die Flaeche in Bloecken.
     *
     * <p>{@code long}, nicht {@code int}: Ein Feld hat 256 Zellen zu 256 Bloecken, also
     * 65.536 - das passt noch. Aber diese Methode rechnet fuer JEDEN Quader, und ein
     * fehlerhaft weiter waere sonst still negativ.
     */
    public long bloecke() {
        return (long) zellen() * Stadtfeldplan.ZELLE_KANTE * Stadtfeldplan.ZELLE_KANTE;
    }

    /**
     * Wie der Zuschnitt in einer Chatzeile aussieht.
     *
     * <p>In ZELLEN und nicht in Bloecken: Der Spieler hat Zellen abgesteckt, und eine
     * Zeile mit Blockkoordinaten liesse ihn rechnen, ob er wirklich das bekommen hat,
     * was er wollte. Die Blockmasse nennt die Groessenzeile daneben.
     */
    public String alsText() {
        return vonX + "/" + vonZ + " bis " + bisX + "/" + bisZ;
    }
}
