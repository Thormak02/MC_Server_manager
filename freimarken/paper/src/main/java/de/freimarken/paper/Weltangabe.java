package de.freimarken.paper;

import de.freimarken.kern.Quader;
import org.bukkit.GameMode;
import org.bukkit.World;

/**
 * Eine Welt, so wie sie in der {@code config.yml} steht.
 *
 * <p>Plan 4.2 verlangt es ausdruecklich: „Welten kommen aus einer Konfigurationsliste,
 * nicht aus dem Code. Eine neue Welt ist ein Eintrag plus Generator, kein Umbau." Bis zum
 * 01.11.2026 stand genau eine Welt im Quelltext, mit dem Schluessel fest verdrahtet.
 *
 * @param name             der Schluessel unter {@code freimarken:}, etwa {@code marktstadt}
 * @param umgebung         Oberwelt, Nether oder Ende
 * @param art              woher die Bloecke kommen
 * @param kante            Wunschkante der Weltgrenze in Bloecken; 0 heisst: keine setzen
 * @param sprungBestaetigt der Text, der einen Sprung ueber das Doppelte freigibt
 * @param spielmodus       in welchem Modus ein Spieler diese Welt betritt
 * @param tor              das Tor am Bundesmarkt, das HIERHER fuehrt - oder {@code null}
 */
record Weltangabe(String name, World.Environment umgebung, Weltangabe.Art art,
                  int kante, String sprungBestaetigt, GameMode spielmodus, Quader tor) {

    /**
     * Woher die Bloecke kommen.
     *
     * <p>Drei und nicht beliebig viele: Jede Art ist ein Stueck Programm, das es geben
     * muss. Ein vierter Eintrag in der Datei kann keinen Generator erfinden - deshalb
     * nennt die Liste die Arten, nicht Klassennamen.
     */
    enum Art {
        /** Das Parzellenraster der Marktstadt. */
        RASTER,
        /** Gewoehnliches Minecraft - Gelaende, Erze, Baeume, Tiere. */
        GEWOEHNLICH,
        /** Leere. Fuer Wolkenwerder, dessen Startinsel mit Schritt 11 kommt. */
        LEER
    }

    /** Ob fuer diese Welt ueberhaupt eine Grenze gesetzt werden soll. */
    boolean hatGrenze() {
        return kante > 0;
    }

    /**
     * Ob ein Tor am Bundesmarkt hierher fuehrt.
     *
     * <p>Das Tor steht am Eintrag der ZIELwelt und nicht in einer eigenen Liste. Plan 4.3
     * verlangt genau das: Eine neue Welt bleibt „ein Eintrag plus Generator" und ist nicht
     * ein Eintrag plus ein vergessenes Tor. Und die Tore koennen so nicht von der
     * Weltenliste abweichen - es gibt nur eine Liste.
     */
    boolean hatTor() {
        return tor != null;
    }
}
