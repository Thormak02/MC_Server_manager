package de.freimarken.kern;

/**
 * Die Geometrie der Marktstadt: Wo ist Parzelle, wo Mauer, wo Weg.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft. Derselbe Plan beantwortet drei Fragen, die
 * sonst an drei Stellen auseinanderlaufen wuerden: Was erzeugt der Weltgenerator? Wem
 * gehoert der Block, auf den jemand klickt? Welche Flaeche raeumt ein Zuruecksetzen?
 *
 * <p>Aufbau einer Rasterzelle bei Parzelle 64 und Weg 7 (Kantenlaenge 71):
 *
 * <pre>
 *   0 .. 63   Parzelle   (64 Felder, voll bebaubar)
 *       64    Mauer      (Rand zur eigenen Parzelle)
 *   65 .. 69  Weg        (5 Felder begehbar)
 *       70    Mauer      (Rand zur naechsten Parzelle)
 * </pre>
 *
 * <p>Die Parzelle bleibt damit volle 64x64 - die Mauer steht auf dem Weg, nicht auf dem
 * Bauland. Wer 64 verspricht, sollte 64 liefern.
 */
public final class Rasterplan {

    /** Welche Art Feld an einer Stelle liegt. */
    public enum Feld {
        /** Bauland. */
        PARZELLE,
        /** Umrandung einer Parzelle. */
        MAUER,
        /** Begehbarer Weg zwischen den Parzellen. */
        WEG
    }

    private final int parzellenKante;
    private final int wegBreite;

    /**
     * @param parzellenKante Kantenlaenge der Parzelle in Bloecken, mindestens 1
     * @param wegBreite      Breite des Wegs inklusive beider Mauern, mindestens 2
     */
    public Rasterplan(int parzellenKante, int wegBreite) {
        if (parzellenKante < 1) {
            throw new IllegalArgumentException(
                    "Parzellenkante muss mindestens 1 sein, nicht " + parzellenKante);
        }
        if (wegBreite < 2) {
            throw new IllegalArgumentException(
                    "Der Weg braucht Platz fuer zwei Mauern, also mindestens 2, nicht "
                            + wegBreite);
        }
        this.parzellenKante = parzellenKante;
        this.wegBreite = wegBreite;
    }

    /** Der Plan der Marktstadt: 64er Parzellen, 7 Bloecke Weg. */
    public static Rasterplan marktstadt() {
        return new Rasterplan(64, 7);
    }

    public int parzellenKante() {
        return parzellenKante;
    }

    public int wegBreite() {
        return wegBreite;
    }

    /** Kantenlaenge einer Rasterzelle: Parzelle plus Weg. */
    public int rasterWeite() {
        return parzellenKante + wegBreite;
    }

    /**
     * Was an dieser Weltkoordinate liegt.
     *
     * <p>Die beiden Achsen werden einzeln eingeordnet und dann zusammengefuehrt: Ein Weg
     * gewinnt immer, damit sich Wege kreuzen koennen, statt an jeder Kreuzung eine Mauer
     * stehen zu haben. Zwei Parzellenachsen ergeben Bauland, alles uebrige ist Mauer.
     */
    public Feld feldBei(int x, int z) {
        Feld aufX = aufAchse(x);
        Feld aufZ = aufAchse(z);
        if (aufX == Feld.WEG || aufZ == Feld.WEG) {
            return Feld.WEG;
        }
        if (aufX == Feld.PARZELLE && aufZ == Feld.PARZELLE) {
            return Feld.PARZELLE;
        }
        return Feld.MAUER;
    }

    private Feld aufAchse(int wert) {
        if (!zwischenZellen(wert)) {
            return Feld.PARZELLE;
        }
        int imRaster = Math.floorMod(wert, rasterWeite());
        if (imRaster == parzellenKante || imRaster == rasterWeite() - 1) {
            return Feld.MAUER;
        }
        return Feld.WEG;
    }

    /**
     * Ob diese Koordinate im Band aus Mauer und Weg liegt.
     *
     * <p>Dort grenzen zwei Zellen aneinander: die, die {@link #parzelleX} nennt, und die
     * naechste. Auf Bauland ist es nur eine. Dieser Unterschied traegt
     * {@link #imInneren} - und er ist auch der Grund, warum ein Schreibbefehl seine Zeile
     * nie aus {@code parzelleX} eines Wegfeldes ziehen darf: Er traefe immer die
     * kleinere der beiden Nachbarzellen, also unter Umstaenden fremdes Grundstueck.
     */
    public boolean zwischenZellen(int wert) {
        return Math.floorMod(wert, rasterWeite()) >= parzellenKante;
    }

    /** Ob eine Rasterzelle zu einer betrachteten Flaeche gehoert. */
    @FunctionalInterface
    public interface ZellenPruefung {

        boolean gehoert(int zelleX, int zelleZ);
    }

    /**
     * Liegt diese Weltkoordinate im INNEREN einer Flaeche aus Rasterzellen?
     *
     * <p>Anliegend sind EINE Zelle auf Bauland, ZWEI auf einem Streifen zwischen zwei
     * Zellen und VIER auf einer Kreuzung. Innen liegt die Koordinate nur, wenn alle
     * anliegenden Zellen dazugehoeren.
     *
     * <p>Alle, nicht irgendeine: Bei der L-Form aus 0/0, 1/0 und 0/1 liegt die Kreuzung
     * zwischen allen vieren - aber die vierte Zelle 1/1 ist fremdes Grundstueck. Wuerde
     * dort gefuellt, bekaeme der Nachbar einen Grasfleck vor die Tuer, ueber den jeder
     * auf sein Grundstueck sieht.
     *
     * <p>Der Aussenring einer Gruppe bleibt damit von selbst Weg: Dort liegt jenseits des
     * Bandes immer eine Zelle, die nicht dazugehoert. Das ist gewollt - ohne ihn waere
     * die Gruppe von aussen nicht mehr abgegrenzt.
     */
    public boolean imInneren(int weltX, int weltZ, ZellenPruefung gehoertDazu) {
        int vonX = parzelleX(weltX);
        int vonZ = parzelleZ(weltZ);
        int bisX = zwischenZellen(weltX) ? vonX + 1 : vonX;
        int bisZ = zwischenZellen(weltZ) ? vonZ + 1 : vonZ;
        for (int x = vonX; x <= bisX; x++) {
            for (int z = vonZ; z <= bisZ; z++) {
                if (!gehoertDazu.gehoert(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Nummer der Parzelle auf der X-Achse; auch negativ, Parzelle 0 beginnt bei x=0. */
    public int parzelleX(int x) {
        return Math.floorDiv(x, rasterWeite());
    }

    /** Nummer der Parzelle auf der Z-Achse. */
    public int parzelleZ(int z) {
        return Math.floorDiv(z, rasterWeite());
    }

    /** Kleinste X-Koordinate der Parzelle mit dieser Nummer. */
    public int ursprungX(int parzelleX) {
        return parzelleX * rasterWeite();
    }

    /** Kleinste Z-Koordinate der Parzelle mit dieser Nummer. */
    public int ursprungZ(int parzelleZ) {
        return parzelleZ * rasterWeite();
    }

    /** Mitte der Parzelle auf der X-Achse - der Punkt, auf den man teleportiert. */
    public int mitteX(int parzelleX) {
        return ursprungX(parzelleX) + parzellenKante / 2;
    }

    /** Mitte der Parzelle auf der Z-Achse. */
    public int mitteZ(int parzelleZ) {
        return ursprungZ(parzelleZ) + parzellenKante / 2;
    }

    /** Wie viele Bloecke eine Parzelle bedeckt - fuer Preis und Aufwandsabschaetzung. */
    public long flaeche() {
        return (long) parzellenKante * parzellenKante;
    }
}
