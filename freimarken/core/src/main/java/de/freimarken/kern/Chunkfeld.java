package de.freimarken.kern;

import java.util.List;

/**
 * Welche Chunks eine Weltgrenze einschliesst - und was ein Vorgenerator dafuer braucht.
 *
 * <p>Reine Rechnerei wie {@link Rasterplan} und {@link Stadtgrenze}, bewusst ohne
 * Minecraft. Der Anlass ist eine Zeile aus Plan Abschnitt 4, die ohne diese Klasse jeder
 * von Hand nachrechnen muesste: „Ein spawnzentriertes Werkzeug braeuchte um 32/32 den
 * Radius 1.530, sonst fehlt der ganze Westring." Die Marktstadt liegt um -3,5/-3,5, und
 * ein Vorgenerator, der Mitte und Radius vom Spawn nimmt, laesst genau deshalb einen
 * Streifen aus - der faellt erst auf, wenn ein Spieler hinlaeuft und das Gelaende
 * waehrend des Spiels entsteht.
 *
 * <p>Deshalb rechnet diese Klasse Mitte und Radius AUS DER GRENZE, die wirklich gesetzt
 * ist, und nicht aus dem, was in einer Einstellungsdatei steht.
 *
 * <pre>
 *   Grenze Kante k um Mitte m   schliesst die Bloecke  floor(m - k/2) .. ceil(m + k/2) - 1
 *   Chunk c                     belegt die Bloecke     16c .. 16c + 15
 *   Regionsdatei r              belegt die Chunks      32r .. 32r + 31
 * </pre>
 *
 * <p>Nach aussen gerundet, nie nach innen: Ein Chunk zu viel kostet Platte, ein Chunk zu
 * wenig kostet den Rand - und der Rand ist das, was ein Spieler als Erstes sieht.
 *
 * @param blockVonX kleinste Weltkoordinate innerhalb der Grenze auf der X-Achse
 * @param blockBisX groesste Weltkoordinate innerhalb der Grenze auf der X-Achse
 * @param blockVonZ kleinste Weltkoordinate innerhalb der Grenze auf der Z-Achse
 * @param blockBisZ groesste Weltkoordinate innerhalb der Grenze auf der Z-Achse
 */
public record Chunkfeld(int blockVonX, int blockBisX, int blockVonZ, int blockBisZ) {

    /** Bloecke je Chunk auf einer Achse. */
    public static final int CHUNKKANTE = 16;

    /** Chunks je Regionsdatei auf einer Achse - eine Datei fasst 32 x 32. */
    public static final int REGIONKANTE = 32;

    public Chunkfeld {
        if (blockBisX < blockVonX || blockBisZ < blockVonZ) {
            throw new IllegalArgumentException("Ein Feld ohne Ausdehnung: "
                    + blockVonX + ".." + blockBisX + " / " + blockVonZ + ".." + blockBisZ);
        }
    }

    /**
     * Das Feld zu einer Weltgrenze.
     *
     * <p>Beide Achsen getrennt, weil {@code WorldBorder.setCenter} zwei Werte nimmt. Die
     * Marktstadt setzt auf beiden -3,5, die uebrigen Welten 0 - aber eine Klasse, die das
     * voraussetzt, gibt irgendwann eine falsche Antwort, ohne dass es auffaellt.
     *
     * @param kante die Kantenlaenge der Grenze - ein Durchmesser, kein Radius
     */
    public static Chunkfeld ausGrenze(double mitteX, double mitteZ, double kante) {
        if (kante <= 0 || !Double.isFinite(kante)) {
            throw new IllegalArgumentException("Keine Kante: " + kante);
        }
        double halb = kante / 2.0;
        return new Chunkfeld(
                (int) Math.floor(mitteX - halb), (int) Math.ceil(mitteX + halb) - 1,
                (int) Math.floor(mitteZ - halb), (int) Math.ceil(mitteZ + halb) - 1);
    }

    /** Das Feld der Marktstadt - aus ihrer gerechneten Grenze, nicht aus einer Zahl. */
    public static Chunkfeld ausStadtgrenze(Stadtgrenze grenze) {
        return ausGrenze(grenze.mitte(), grenze.mitte(), grenze.kante());
    }

    public int chunkVonX() {
        return Math.floorDiv(blockVonX, CHUNKKANTE);
    }

    public int chunkBisX() {
        return Math.floorDiv(blockBisX, CHUNKKANTE);
    }

    public int chunkVonZ() {
        return Math.floorDiv(blockVonZ, CHUNKKANTE);
    }

    public int chunkBisZ() {
        return Math.floorDiv(blockBisZ, CHUNKKANTE);
    }

    /** Wie viele Chunks zu erzeugen sind. */
    public long chunks() {
        return (chunkBisX() - chunkVonX() + 1L) * (chunkBisZ() - chunkVonZ() + 1L);
    }

    /**
     * Wie viele Regionen das Feld selbst beruehrt - eine UNTERGRENZE, keine Dateizahl.
     *
     * <p>Am 02.11.2026 gemessen, weil ich das Gegenteil angenommen hatte: Altfurt mit den
     * Zielchunks -25..25 deckt 4 Regionen ab, auf der Platte lagen danach <b>16</b>
     * Dateien (Regionen -2..1 je Achse). Der Grund ist die Erzeugung selbst - ein Chunk am
     * Rand braucht seine Nachbarn, und eine Regionsdatei entsteht, sobald ein einziger
     * Chunk in ihr angefasst wird. Fuer die Marktstadt heisst das: 36 Regionen im Feld,
     * auf der Platte sind rund 64 Dateien zu erwarten.
     *
     * <p>Deshalb taugt die Zahl zum Abschaetzen des Platzbedarfs, aber <b>nicht</b> als
     * Pruefung, ob eine Welt fertig vorgeneriert ist: Mehr Dateien als hier stehen sind
     * der Normalfall, nicht ein Fehler. Wer die Vollstaendigkeit wissen will, fragt die
     * Chunks selbst - dafuer gibt es {@link #stichprobe()}.
     */
    public long regionenImFeld() {
        long x = Math.floorDiv(chunkBisX(), REGIONKANTE)
                - Math.floorDiv(chunkVonX(), REGIONKANTE) + 1L;
        long z = Math.floorDiv(chunkBisZ(), REGIONKANTE)
                - Math.floorDiv(chunkVonZ(), REGIONKANTE) + 1L;
        return x * z;
    }

    /**
     * Welchen Radius ein Werkzeug braucht, das um DIESEN Punkt arbeitet.
     *
     * <p>Das ist die Zeile aus Plan Abschnitt 4 als Rechnung: Fuer die Marktstadt ergibt
     * {@code radiusUm(32, 32)} die 1.530, {@code radiusUm(-4, -4)} dagegen 1.494. Ein
     * Werkzeug mit dem falschen Mittelpunkt erzeugt also entweder 5 % mehr Flaeche als
     * noetig oder - schlimmer - laesst eine Seite aus.
     */
    public int radiusUm(double mitteX, double mitteZ) {
        double weit = Math.max(
                Math.max(mitteX - blockVonX, blockBisX - mitteX),
                Math.max(mitteZ - blockVonZ, blockBisZ - mitteZ));
        return (int) Math.ceil(weit);
    }

    /**
     * Der ganzzahlige Mittelpunkt, der den kleinsten Radius ergibt.
     *
     * <p>Noetig, weil die Grenze der Marktstadt um -3,5 liegt und ein Werkzeug wie Chunky
     * ganze Zahlen nimmt. Die Mitte des Blockbereichs ist die richtige Antwort, nicht die
     * gerundete Grenzmitte: Aus -3,5 wuerde kaufmaennisch -3, und das kostet auf der
     * Westseite zwei Bloecke Radius mehr als -4.
     */
    public int empfohleneMitteX() {
        return Math.floorDiv(blockVonX + blockBisX, 2);
    }

    /** Siehe {@link #empfohleneMitteX()}. */
    public int empfohleneMitteZ() {
        return Math.floorDiv(blockVonZ + blockBisZ, 2);
    }

    /** Der Radius zum empfohlenen Mittelpunkt. */
    public int empfohlenerRadius() {
        return radiusUm(empfohleneMitteX(), empfohleneMitteZ());
    }

    /**
     * Neun Punkte, an denen sich zeigt, ob eine Welt vorgeneriert ist.
     *
     * <p>Eine Stichprobe und kein Beweis - das steht absichtlich auch in den Namen, die
     * sie tragen. Vier Ecken sind dabei, weil ein Werkzeug, das einen KREIS erzeugt statt
     * eines Quadrats, in der Mitte und an den Kantenmitten fertig aussieht und genau an
     * den Ecken fehlt. Das ist bei Chunky eine Einstellung
     * ({@code shape square} gegen {@code shape circle}) und damit ein Vertipper weit.
     */
    public List<Punkt> stichprobe() {
        int mx = (chunkVonX() + chunkBisX()) / 2;
        int mz = (chunkVonZ() + chunkBisZ()) / 2;
        return List.of(
                new Punkt("Mitte", mx, mz),
                new Punkt("Ecke Nordwest", chunkVonX(), chunkVonZ()),
                new Punkt("Ecke Nordost", chunkBisX(), chunkVonZ()),
                new Punkt("Ecke Suedwest", chunkVonX(), chunkBisZ()),
                new Punkt("Ecke Suedost", chunkBisX(), chunkBisZ()),
                new Punkt("Rand West", chunkVonX(), mz),
                new Punkt("Rand Ost", chunkBisX(), mz),
                new Punkt("Rand Nord", mx, chunkVonZ()),
                new Punkt("Rand Sued", mx, chunkBisZ()));
    }

    /** Ein Punkt der Stichprobe, in Chunkkoordinaten. */
    public record Punkt(String name, int chunkX, int chunkZ) {
    }
}
