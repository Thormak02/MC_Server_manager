package de.freimarken.kern;

/**
 * Eine Blockposition - Welt und drei ganze Zahlen, ohne Bukkit.
 *
 * <p>Die Welt als SCHLUESSEL ({@code freimarken:marktstadt}) und nicht als Objekt, aus
 * demselben Grund wie bei {@link Besitzbuch}: So ist der Ort ein Wert, der sich
 * vergleichen, in eine Menge legen und ohne Server pruefen laesst.
 *
 * <p>Die Y-Achse gehoert dazu, anders als bei einer Parzelle. Eine Parzelle ist eine
 * Saeule - wer oben baut, baut auf derselben Parzelle wie der, der unten graebt. Eine
 * MARKE haengt dagegen an genau einem Block: Ein gesetzter Block eine Ebene hoeher ist
 * eine andere Marke, sonst wuerde ein einziger gesetzter Block eine ganze Saeule
 * entwerten.
 *
 * @param welt der Weltschluessel, kleingeschrieben wie {@code World.key().asString()}
 * @param x    Weltkoordinate
 * @param y    Hoehe
 * @param z    Weltkoordinate
 */
public record Blockort(String welt, int x, int y, int z) {

    /** Wie viele Bloecke ein Abschnitt hat - 16 mal 16 mal 16. */
    public static final int BLOECKE_JE_ABSCHNITT = 4096;

    /**
     * Nur die Welt wird geprueft, und sie wird NICHT zurechtgebogen.
     *
     * <p>Kein Trimmen und kein Kleinschreiben: Der Schluessel kommt aus
     * {@code World.key().asString()} und ist dort schon normalisiert. Ein zweiter Ort,
     * der ihn zurechtbiegt, biegt ihn eines Tages anders - und dann liegt die Marke in
     * einer Welt, die niemand fragt.
     */
    public Blockort {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ein Blockort ohne Welt");
        }
    }

    /** Der Chunk, in dem dieser Block liegt - 16 mal 16 Bloecke. */
    public int chunkX() {
        return Math.floorDiv(x, 16);
    }

    public int chunkZ() {
        return Math.floorDiv(z, 16);
    }

    /**
     * Der Abschnitt, in dem dieser Block liegt - 16 Bloecke hoch.
     *
     * <p>Minecraft fuehrt einen Chunk in Abschnitten von 16 Bloecken Hoehe, und die Welt
     * beginnt bei -64. Der Abschnitt ist darum negativ, solange der Block unter Null
     * liegt; {@link Math#floorDiv} rechnet das richtig, eine Division mit {@code /}
     * nicht - {@code -1 / 16} ist 0 und nicht -1, und damit faellt die tiefste
     * Blockschicht mit der ersten darueber zusammen.
     */
    public int abschnittY() {
        return Math.floorDiv(y, 16);
    }

    /**
     * Die Stelle dieses Blocks IM Abschnitt - eine Zahl von 0 bis 4095.
     *
     * <p>{@code y} oben, dann {@code z}, dann {@code x} - dieselbe Reihenfolge, in der
     * Minecraft die Bloecke eines Abschnitts selbst fuehrt. Die Reihenfolge ist frei
     * waehlbar, aber sie muss fuer immer dieselbe bleiben: Sie steht in der Weltdatei,
     * und eine andere Reihenfolge liest alte Marken an falschen Stellen.
     */
    public int stelleImAbschnitt() {
        return (Math.floorMod(y, 16) << 8) | (Math.floorMod(z, 16) << 4)
                | Math.floorMod(x, 16);
    }
}
