package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Rasterplan;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Blocktabelle der Stadt und die Wertung einer Stelle.
 *
 * <p>Die erste Probe in der Paper-Schicht, und sie steht hier, weil genau hier zwei Fehler
 * sassen, die kein Kerntest finden konnte: Die Warnung vor dem Auftrennen zaehlte das Gras
 * mit, das das Fuellen selbst gelegt hatte, und sie nannte Bloecke, die der Zurueckbau gar
 * nicht anfasst. Beides ist eine Frage von Material gegen Material - kein Server
 * notwendig.
 */
class StadtbodenTest {

    /** Die Weltunterkante der Marktstadt. */
    private static final int UNTEN = -64;

    @Test
    @DisplayName("Die Spalte liegt in einer Tabelle, von der Krone bis zum Grundgestein")
    void dieSpalteInEinerTabelle() {
        assertEquals(Material.AIR, Stadtboden.soll(100, Rasterplan.Feld.PARZELLE, UNTEN));
        assertEquals(Material.AIR, Stadtboden.soll(Stadtboden.BODEN + 1,
                Rasterplan.Feld.PARZELLE, UNTEN));
        assertEquals(Material.STONE_BRICK_SLAB, Stadtboden.soll(Stadtboden.BODEN + 1,
                Rasterplan.Feld.MAUER, UNTEN), "die Mauer hat eine Krone");
        assertEquals(Material.GRASS_BLOCK, Stadtboden.soll(Stadtboden.BODEN,
                Rasterplan.Feld.PARZELLE, UNTEN));
        assertEquals(Material.SMOOTH_STONE, Stadtboden.soll(Stadtboden.BODEN,
                Rasterplan.Feld.WEG, UNTEN));
        assertEquals(Material.STONE_BRICKS, Stadtboden.soll(Stadtboden.BODEN,
                Rasterplan.Feld.MAUER, UNTEN));
        assertEquals(Material.DIRT, Stadtboden.soll(Stadtboden.ERDE_AB,
                Rasterplan.Feld.WEG, UNTEN));
        assertEquals(Material.STONE, Stadtboden.soll(Stadtboden.ERDE_AB - 1,
                Rasterplan.Feld.WEG, UNTEN));
    }

    @Test
    @DisplayName("Auf der Weltunterkante liegt Grundgestein, nicht Stein")
    void unterkanteIstGrundgestein() {
        // Der Zurueckbau stellt eine abgegrabene Spalte bis hier unten wieder her. Stuende
        // dort danach Stein, haette die Stadt eine Stelle, an der man aus der Welt faellt.
        assertEquals(Material.BEDROCK, Stadtboden.soll(UNTEN, Rasterplan.Feld.WEG, UNTEN));
        assertEquals(Material.STONE, Stadtboden.soll(UNTEN + 1, Rasterplan.Feld.WEG, UNTEN));
    }

    @Test
    @DisplayName("Unter der Oberflaeche ist das Feld gleichgueltig")
    void unterDerOberflaecheGleich() {
        // Unter einem Weg liegt dieselbe Erde wie unter einer Parzelle - deshalb fasst das
        // Fuellen nur die zwei oberen Schichten an. Verglichen wird gegen WEG, also nur
        // die beiden anderen Feldarten: WEG gegen WEG waere nichts.
        for (int y = Stadtboden.BODEN - 1; y >= UNTEN; y -= 7) {
            for (Rasterplan.Feld feld : new Rasterplan.Feld[] {
                    Rasterplan.Feld.PARZELLE, Rasterplan.Feld.MAUER}) {
                assertEquals(Stadtboden.soll(y, Rasterplan.Feld.WEG, UNTEN),
                        Stadtboden.soll(y, feld, UNTEN), "Hoehe " + y + ", Feld " + feld);
            }
        }
    }

    @Test
    @DisplayName("Verrottetes Gras ist kein Verlust des Spielers")
    void verrottetesGrasIstKeinVerlust() {
        // Der Fall, der ohne diese Probe erst im Spiel auffaellt: Gras wird zu Erde, sobald
        // etwas Lichtdichtes darauf steht - oder ein Schaf vorbeikommt. Ohne die Ausnahme
        // meldet eine unberuehrte Gruppe nach einer Weile "dabei verschwinden 448 Bloecke".
        assertEquals(Stadtboden.Wertung.UNVERAENDERT, Stadtboden.wertung(
                Material.DIRT, Material.GRASS_BLOCK, Material.SMOOTH_STONE));
        assertEquals(Stadtboden.Wertung.UNVERAENDERT, Stadtboden.wertung(
                Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.SMOOTH_STONE));
        assertTrue(Stadtboden.istOderWird(Material.DIRT, Material.GRASS_BLOCK));
        // Umgekehrt nicht: Wer Erde hinlegt, bekommt deswegen kein Gras gutgeschrieben.
        assertEquals(Stadtboden.Wertung.VERSCHWINDET, Stadtboden.wertung(
                Material.GRASS_BLOCK, Material.DIRT, Material.SMOOTH_STONE));
    }

    @Test
    @DisplayName("Was der Zurueckbau nicht anfasst, wird nicht gemeldet")
    void keineWarnungVorEinerTatDieAusbleibt() {
        // Der Spieler pflastert die Fuge mit dem Strassenblock der Stadt. Der Zurueckbau
        // laesst ihn liegen, weil dort schon das Richtige steht - ihn als Verlust zu
        // melden hiesse, vor einer Tat zu warnen, die nicht stattfindet.
        assertEquals(Stadtboden.Wertung.UNVERAENDERT, Stadtboden.wertung(
                Material.SMOOTH_STONE, Material.GRASS_BLOCK, Material.SMOOTH_STONE));
        // Dasselbe eine Schicht hoeher: eine Stufe, wo die Mauerkrone hinkommt.
        assertEquals(Stadtboden.Wertung.UNVERAENDERT, Stadtboden.wertung(
                Material.STONE_BRICK_SLAB, Material.AIR, Material.STONE_BRICK_SLAB));
        // Auf der Mauerreihe derselben Fuge wird es dagegen wirklich ersetzt.
        assertEquals(Stadtboden.Wertung.VERSCHWINDET, Stadtboden.wertung(
                Material.SMOOTH_STONE, Material.GRASS_BLOCK, Material.STONE_BRICKS));
    }

    @Test
    @DisplayName("Eine Fehlstelle verschwindet nicht, sie wird verfuellt")
    void fehlstelleWirdVerfuellt() {
        // Ueber einem ausgehobenen Becken stand "dabei verschwinden 4.928 Bloecke" - und
        // das Gegenteil war wahr: Der Zurueckbau SETZT diese Bloecke.
        assertEquals(Stadtboden.Wertung.WIRD_VERFUELLT, Stadtboden.wertung(
                Material.AIR, Material.GRASS_BLOCK, Material.SMOOTH_STONE));
        assertEquals(Stadtboden.Wertung.WIRD_VERFUELLT, Stadtboden.wertung(
                Material.AIR, Material.DIRT, Material.DIRT));
    }

    @Test
    @DisplayName("Was der Spieler gebaut hat, verschwindet")
    void gebautesVerschwindet() {
        // Ueber der Flaeche: dort ist nach dem Fuellen Luft, also ist jeder Block Werk des
        // Spielers.
        assertEquals(Stadtboden.Wertung.VERSCHWINDET, Stadtboden.wertung(
                Material.OAK_PLANKS, Material.AIR, Material.AIR));
        // Ebenerdig eingesetzt: der Fall, der vorher ungezaehlt blieb und trotzdem verloren
        // ging.
        assertEquals(Stadtboden.Wertung.VERSCHWINDET, Stadtboden.wertung(
                Material.CHEST, Material.GRASS_BLOCK, Material.SMOOTH_STONE));
        // Und ein Fremdblock im Schacht unter der Flaeche.
        assertEquals(Stadtboden.Wertung.VERSCHWINDET, Stadtboden.wertung(
                Material.GLASS, Material.STONE, Material.STONE));
    }

    @Test
    @DisplayName("Luft ueber der Flaeche ist nichts")
    void luftIstNichts() {
        assertEquals(Stadtboden.Wertung.UNVERAENDERT, Stadtboden.wertung(
                Material.AIR, Material.AIR, Material.AIR));
    }

    @Test
    @DisplayName("Luft heisst Luft, auch in ihren zwei Sonderformen")
    void luftInAllenFormen() {
        // Hoehlenluft und Leerenluft kommen in der Marktstadt nicht vor - aber die Frage
        // "ist hier nichts" entscheidet darueber, ob eine Stelle als Verlust oder als
        // Fuellung gilt, und sie darf nicht an einer Spielart von Luft scheitern.
        assertTrue(Stadtboden.istLuft(Material.AIR));
        assertTrue(Stadtboden.istLuft(Material.CAVE_AIR));
        assertTrue(Stadtboden.istLuft(Material.VOID_AIR));
        assertFalse(Stadtboden.istLuft(Material.GRASS_BLOCK));
        assertEquals(Stadtboden.Wertung.WIRD_VERFUELLT, Stadtboden.wertung(
                Material.CAVE_AIR, Material.STONE, Material.STONE));
    }

    @Test
    @DisplayName("Zwei ueber dem Boden ist schon Himmel")
    void zweiUeberDemBodenIstHimmel() {
        // Die Kante, an der die Spaltentabelle von den zwei Generatorschichten auf Luft
        // umschlaegt. Ein Block daneben, und der Zurueckbau liesse die Mauerkrone stehen
        // oder raeumte sie, wo sie hingehoert.
        assertEquals(Material.STONE_BRICK_SLAB,
                Stadtboden.soll(Stadtboden.BODEN + 1, Rasterplan.Feld.MAUER, UNTEN));
        assertEquals(Material.AIR,
                Stadtboden.soll(Stadtboden.BODEN + 2, Rasterplan.Feld.MAUER, UNTEN));
    }
}
