package de.freimarken.paper;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Baustil;
import de.freimarken.kern.Dachart;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Stadtplan;
import de.freimarken.kern.Torhausplan;
import java.util.List;
import org.bukkit.Material;

/**
 * Setzt ein Torhaus in Altfurts Stadtmauer - samt den Dioramen hinter den Portalen.
 *
 * <p><b>Die Baustoffe kommen aus den Tafeln, die schon da sind</b>, und das ist Absicht:
 * Das Mauerwerk ist dieselbe Mischung wie die Stadtmauer, der Boden des Durchgangs
 * dieselbe wie die Radialgasse, die er fortsetzt, und das Dach dasselbe Kupfer wie auf
 * den Bauten der Stadt. Der Betreiber hat es so verlangt: "generell soll ein
 * zusammenhengendes bild entstehen." Eine eigene Tafel fuer das Torhaus waere eine
 * zweite Stelle, an der jemand eine andere Farbe einstellt.
 *
 * <p>Nur drei Bloecke sind dem Torhaus eigen, und jeder hat seinen Grund:
 * {@link #PFEILER} an den Ecken, damit die Kante eines Turms sichtbar ist;
 * {@link #SCHEIBE} vor dem Diorama, damit man hineinsieht, aber nichts herauskommt; und
 * {@link #HINTERGRUND} dort, wo eine Welt kein Diorama hat.
 *
 * <p>Ohne Physik wie aller Aufbau in Altfurt: Tausend Bloecke, die ihre Nachbarn
 * benachrichtigen, halten den Server an - und eine Treppe, die faellt, ist kein Dach.
 */
record Torhausaufbau(Pflasterstoffe pflaster, Baustoffe stoffe) {

    /** Die Ecken der Tuerme - anderer Stein, damit die Kante sichtbar ist. */
    static final Material PFEILER = Material.POLISHED_DEEPSLATE;

    /**
     * Die Scheibe vor dem Diorama.
     *
     * <p>GEWOEHNLICHES Glas und nicht getoentes: Getoentes laesst kein Licht durch,
     * und dann liegt das Diorama im Dunkeln - man sieht nichts von der anderen Welt.
     * So leuchtet das Lavafeld des Netherbildes in die Torhalle, und das ist kein
     * Fehler, sondern der Zweck: Man SIEHT, wohin man geht.
     *
     * <p><b>Und sie ist ein Riegel, kein Schmuck</b>: Ohne Scheibe waere beim Nether
     * Lava eine Armlaenge von einem Spieler entfernt, der auf die Reise wartet.
     */
    static final Material SCHEIBE = Material.GLASS;

    /** Was hinter einer Nische steht, deren Welt kein Diorama hat. */
    static final Material HINTERGRUND = Material.POLISHED_DEEPSLATE;

    /** Der Baustil, dessen Dachstoff das Torhaus nimmt - Kupfer wie die Tuerme. */
    static final Baustil STIL = Baustil.SCHIEFER;

    Torhausaufbau {
        if (pflaster == null || stoffe == null) {
            throw new IllegalArgumentException("Ohne Pflaster und Baustoffe gibt es kein"
                    + " Torhaus");
        }
    }

    /**
     * Was beim Setzen herauskam.
     *
     * @param bloecke  wie viele Bloecke gesetzt wurden
     * @param nischen  wie viele Portalnischen offen stehen
     * @param dioramen wie viele davon ein Diorama hinter sich haben
     */
    record Ergebnis(int bloecke, int nischen, int dioramen) {
    }

    /**
     * Ob dieses Torhaus schon steht - gefragt an EINEM Block.
     *
     * <p>Gefragt wird die Ecke des linken Turms eine Ebene ueber dem Boden, und zwar auf
     * PFEILER: Dieselbe Linie wie bei {@code Altfurtaufbau.stehtSchon}. Wer es neu setzen
     * will, nimmt diesen einen Block weg.
     */
    boolean stehtSchon(Torhausplan plan, Weltgriff griff) {
        if (plan == null || griff == null) {
            throw new IllegalArgumentException("Ohne Plan und Welt gibt es keine Antwort");
        }
        Quader bereich = plan.bereich();
        return griff.bei(bereich.vonX(), Stadtplan.BODEN_Y + 1, bereich.vonZ()) == PFEILER;
    }

    /**
     * Setzt das Torhaus.
     *
     * <p>Die Reihenfolge ist dieselbe wie bei einem Haus und aus demselben Grund: Erst
     * die Schale, dann die Blockdaten. Eine Dachtreppe wird erst zur Treppe, wenn ihr
     * Block steht.
     *
     * @param dioramaLinks  die Bloecke hinter der linken Nische - leer heisst Mauerwerk
     * @param dioramaRechts dasselbe rechts
     */
    Ergebnis setze(Torhausplan plan, Weltgriff griff, Altfurtaufbau.Feingriff fein,
            List<Material> dioramaLinks, List<Material> dioramaRechts) {
        if (plan == null || griff == null || fein == null || dioramaLinks == null
                || dioramaRechts == null) {
            throw new IllegalArgumentException("Ohne Plan, Welt, Feingriff und zwei"
                    + " Dioramen gibt es nichts zu setzen");
        }
        Quader bereich = plan.bereich();
        boolean laengsIstX = plan.seite() == Richtung.NORD
                || plan.seite() == Richtung.SUED;
        int mitteLaengs = laengsIstX ? Stadtplan.MITTE_X : Stadtplan.MITTE_Z;
        int gesetzt = 0;
        int nischen = 0;
        for (int x = bereich.vonX(); x <= bereich.bisX(); x++) {
            for (int z = bereich.vonZ(); z <= bereich.bisZ(); z++) {
                for (int y = bereich.vonY(); y <= bereich.bisY(); y++) {
                    Torhausplan.Stueck stueck = plan.stueckBei(x, y, z);
                    // WELCHE SEITE: Das Diorama links gehoert zur Nische mit dem
                    // kleineren Laengsmass. Gerechnet und nicht geraten - sonst sieht
                    // man beim Nether den Hochwald.
                    boolean links = (laengsIstX ? x : z) < mitteLaengs;
                    Material block = block(stueck, x, z,
                            links ? dioramaLinks : dioramaRechts);
                    griff.setze(x, y, z, block);
                    gesetzt++;
                    if (stueck == Torhausplan.Stueck.NISCHE) {
                        nischen++;
                    }
                    Richtung bergauf = plan.dachzeigt(x, y, z);
                    if (bergauf != null) {
                        fein.setzeDachtreppe(x, y, z, bergauf, block);
                    }
                }
            }
        }
        int dioramen = (dioramaLinks.isEmpty() ? 0 : 1)
                + (dioramaRechts.isEmpty() ? 0 : 1);
        return new Ergebnis(gesetzt, nischen, dioramen);
    }

    /** Der Block zu einem Stueck an dieser Stelle. */
    Material block(Torhausplan.Stueck stueck, int x, int z, List<Material> diorama) {
        if (stueck == null || diorama == null) {
            throw new IllegalArgumentException("Ohne Stueck und Diorama gibt es keinen"
                    + " Block");
        }
        return switch (stueck) {
            // DER BODEN IST DIE RADIALGASSE, die hier endet - sie laeuft ins Torhaus
            // hinein, also hoert ihr Pflaster nicht an der Schwelle auf.
            case BODEN -> mitRueckfall(Stadtplan.Lage.RADIALGASSE, x, z);
            case MAUER, ZINNE -> mitRueckfall(Stadtplan.Lage.MAUER, x, z);
            case PFEILER -> PFEILER;
            case DACH -> stoffe.block(STIL, Dachart.KUPFER, Bauplan.Stueck.DACH);
            case FIRST -> stoffe.block(STIL, Dachart.KUPFER, Bauplan.Stueck.FIRST);
            case SCHEIBE -> SCHEIBE;
            case DIORAMA -> diorama.isEmpty() ? HINTERGRUND
                    : diorama.get(Stadtplan.mischung(x, z, diorama.size()));
            case NISCHE, LUFT -> Material.AIR;
        };
    }

    /**
     * Der Block einer Pflasterlage - oder Mauerwerk, wenn sie keine Mischung hat.
     *
     * <p>Der Rueckfall ist nicht Zierde: Ein Betreiber kann eine Mischung leeren, und
     * ein Torhaus mit Loechern darin waere ein Loch in der Stadtmauer.
     */
    private Material mitRueckfall(Stadtplan.Lage lage, int x, int z) {
        Material block = pflaster.block(lage, x, z);
        return block == null ? HINTERGRUND : block;
    }
}
