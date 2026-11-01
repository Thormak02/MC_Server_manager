package de.freimarken.kern;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Die Flaeche, die eine Gruppe aus Rasterzellen bedeckt - und ihre Fugen.
 *
 * <p>Eine Fuge ist eine Weltkoordinate im Inneren der Gruppe, auf der KEIN Bauland liegt:
 * das Band aus Mauer und Weg zwischen zwei Feldern, und die Kreuzung, wo vier Felder
 * zusammenstossen. Genau diese Flaeche wird beim Zusammenlegen gefuellt und beim
 * Auftrennen zurueckgebaut.
 *
 * <p>Die Klasse steht im Kern und nicht im Paper-Teil, obwohl nur der Paper-Teil Bloecke
 * setzt. Grund: Die Frage "welche Koordinaten gehoeren dazu" ist reine Geometrie und damit
 * ohne Server pruefbar - und sie ist die Frage, bei der ein Fehler teuer ist. Wer sie im
 * Blockschreiber beantwortet, kann sie nur noch auf einem laufenden Server pruefen, und
 * dort faellt eine Zelle zu viel erst auf, wenn der Nachbar sich beschwert.
 *
 * <p>Entschieden wird hier nichts: {@link Rasterplan#imInneren} ist die eine Regel, und
 * diese Klasse laeuft nur die Koordinaten ab, bei denen sie zu fragen ist.
 */
public final class Gruppenflaeche {

    /** Arbeit an einer Fuge. */
    @FunctionalInterface
    public interface AnDerFuge {

        /**
         * @param weltX Weltkoordinate, nicht Parzellennummer
         * @param weltZ Weltkoordinate, nicht Parzellennummer
         * @param feld  was der Rasterplan hier vorsieht - Mauer oder Weg, nie Bauland
         */
        void an(int weltX, int weltZ, Rasterplan.Feld feld);
    }

    private final int vonX;
    private final int vonZ;
    private final int bisX;
    private final int bisZ;
    private final Set<Long> zellen;

    private Gruppenflaeche(int vonX, int vonZ, int bisX, int bisZ, Set<Long> zellen) {
        this.vonX = vonX;
        this.vonZ = vonZ;
        this.bisX = bisX;
        this.bisZ = bisZ;
        this.zellen = zellen;
    }

    /**
     * Die Flaeche, die diese Felder bedecken.
     *
     * @param felder die Felder einer Gruppe, mindestens eines - alle auf dem Raster
     * @throws IllegalArgumentException wenn keine Felder uebergeben wurden. Laut und nicht
     *                                 als leere Flaeche: Eine Gruppe ohne Felder gibt es
     *                                 nicht, und "nichts zu tun" waere beim Zurueckbau
     *                                 genau das falsche Ergebnis - der Weg bliebe
     *                                 Bauland, ohne dass es jemand merkt.
     */
    public static Gruppenflaeche aus(Collection<Parzelle> felder) {
        if (felder.isEmpty()) {
            throw new IllegalArgumentException("Eine Gruppenflaeche ohne Felder gibt es nicht");
        }
        int vonX = Integer.MAX_VALUE;
        int vonZ = Integer.MAX_VALUE;
        int bisX = Integer.MIN_VALUE;
        int bisZ = Integer.MIN_VALUE;
        Set<Long> zellen = new HashSet<>();
        for (Parzelle feld : felder) {
            vonX = Math.min(vonX, feld.x());
            vonZ = Math.min(vonZ, feld.z());
            bisX = Math.max(bisX, feld.x());
            bisZ = Math.max(bisZ, feld.z());
            zellen.add(schluessel(feld.x(), feld.z()));
        }
        return new Gruppenflaeche(vonX, vonZ, bisX, bisZ, Set.copyOf(zellen));
    }

    /** Wie viele Rasterzellen die Gruppe hat. */
    public int felder() {
        return zellen.size();
    }

    /** Ob diese Rasterzelle zur Gruppe gehoert. Passt auf {@link Rasterplan.ZellenPruefung}. */
    public boolean gehoert(int zelleX, int zelleZ) {
        return zellen.contains(schluessel(zelleX, zelleZ));
    }

    /**
     * Ruft die Arbeit fuer jede Fuge der Gruppe auf - einmal je Weltkoordinate.
     *
     * <p>Abgelaufen wird nur der Kasten um die Felder, und darin wird Bauland sofort
     * uebersprungen. Das ist der Grund, warum ein Zusammenlegen keinen Chunk laedt, in dem
     * nichts zu tun ist: Bei vier Feldern im Quadrat sind das rund 18.000 Rechenschritte,
     * aber nur rund 1.800 Koordinaten, die ueberhaupt einen Block anfassen.
     *
     * <p>Der Kasten endet bewusst am letzten Bauland und nicht an der Zellkante: Das Band
     * dahinter liegt zwischen der letzten Zelle und der naechsten, die nicht dazugehoert -
     * also aussen. Es mitzunehmen waere nicht falsch, nur Arbeit ohne Ergebnis.
     */
    public void ueberDieFugen(Rasterplan plan, AnDerFuge arbeit) {
        int bisWeltX = plan.ursprungX(bisX) + plan.parzellenKante() - 1;
        int bisWeltZ = plan.ursprungZ(bisZ) + plan.parzellenKante() - 1;
        for (int x = plan.ursprungX(vonX); x <= bisWeltX; x++) {
            for (int z = plan.ursprungZ(vonZ); z <= bisWeltZ; z++) {
                Rasterplan.Feld feld = plan.feldBei(x, z);
                if (feld == Rasterplan.Feld.PARZELLE || !plan.imInneren(x, z, this::gehoert)) {
                    continue;
                }
                arbeit.an(x, z, feld);
            }
        }
    }

    /**
     * Zwei Nummern in einer Zahl - ohne ein Objekt je Zelle.
     *
     * <p>Die Maske ist Pflicht: Ohne sie wuerde das Vorzeichen eines negativen z die
     * oberen 32 Bit fuellen, und die Zelle -1/-1 waere dieselbe wie 0/-1. Die Marktstadt
     * hat negative Nummern von -21 an, das traefe also die Haelfte der Stadt.
     */
    private static long schluessel(int zelleX, int zelleZ) {
        return ((long) zelleX << 32) | (zelleZ & 0xFFFFFFFFL);
    }
}
