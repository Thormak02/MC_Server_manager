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
 * @param startwert        der Startwert des Gelaendes, oder {@code null} fuer einen
 *                         zufaelligen. Wirkt NUR beim Anlegen - eine bestehende Welt
 *                         behaelt ihren, und das ist nicht zu aendern
 * @param kante            Wunschkante der Weltgrenze in Bloecken; 0 heisst: keine setzen
 * @param sprungBestaetigt der Text, der einen Sprung ueber das Doppelte freigibt
 * @param spielmodus       in welchem Modus ein Spieler diese Welt betritt
 * @param tor              das Tor am Bundesmarkt, das HIERHER fuehrt - oder {@code null}
 */
record Weltangabe(String name, World.Environment umgebung, Weltangabe.Art art,
                  Long startwert, int kante, String sprungBestaetigt,
                  GameMode spielmodus, Quader tor) {

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
        LEER,
        /**
         * Superflach - eine Grasebene ohne Berge, Hoehlen und Wasser.
         *
         * <p><b>Fuer Welten, in denen das Gelaende stoert</b> [09.11.2026]. Jeder von
         * Hand eingemessene Quader - die sieben Tore, die Ladeneinheiten - braucht
         * ebenen Boden, und gewoehnliches Gelaende gibt ihn nur mit Glueck: Ein
         * gewuerfelter Startwert gab am 02.11.2026 eine rote Wueste mit 2 Bloecken
         * Spanne, ein anderer am 09.11. Bergland mit 29. Auf einer flachen Welt ist
         * die Frage gar nicht da.
         *
         * <p>Der Startwert ist hier ohne Wirkung - wie bei {@link #RASTER} und
         * {@link #LEER}: Eine Ebene ist eine Ebene, gleich welche Zahl darueber
         * steht. {@code Weltenliste.pruefeStartwerte} nimmt diese Arten darum aus.
         */
        FLACH
    }

    /** Ob fuer diese Welt ueberhaupt eine Grenze gesetzt werden soll. */
    boolean hatGrenze() {
        return kante > 0;
    }

    /**
     * Ob das Gelaende dieser Welt WIEDERHOLBAR ist.
     *
     * <p><b>Ein {@code Long} und kein {@code long}, denn 0 ist ein gueltiger
     * Startwert.</b> Bei {@link #kante} heisst 0 "keine Grenze"; hier waere dasselbe
     * ein Gelaende, das niemand mehr einstellen kann. {@code null} heisst "kein
     * Startwert gesetzt", und dann wuerfelt Minecraft einen.
     *
     * <p><b>Warum das ueberhaupt gebraucht wird.</b> Jeder von Hand eingemessene
     * Quader - die sieben Tore am Bundesmarkt, die Ladeneinheiten der Markthalle -
     * ist eine Zahl in einer Datei, und das Gelaende ist es nicht. Ohne festen
     * Startwert gehoeren diese Zahlen zu GENAU EINER zufaellig erzeugten Welt: Wird
     * sie geloescht, sind sie wertlos, und niemand kann sie nachmessen. Am
     * 09.11.2026 war genau das der Fall - die am 02.11. eingemessene Welt war weg,
     * und mit ihr die Grundlage der sieben Torquader.
     */
    boolean wiederholbar() {
        return startwert != null && startwertWirkt();
    }

    /**
     * Ob bei dieser Art ein Startwert ueberhaupt etwas aendert.
     *
     * <p>Beim {@link Art#RASTER} kommt das Gelaende aus dem Parzellengenerator, bei
     * {@link Art#LEER} aus der Leere, und eine {@link Art#FLACH}e Ebene ist eine
     * Ebene - gleich welche Zahl darueber steht.
     *
     * <p><b>Diese Frage steht hier und nicht an ihren drei Aufrufstellen</b>, und das
     * ist am 09.11.2026 im Startlauf teuer geworden: Der Startwert wurde fuer eine
     * flache Welt richtig NICHT gesetzt, die Abweichungsklage kam aber trotzdem -
     * "die Welt hat 8765839788193927876, die Liste sagt 110926001". Eine Klage ueber
     * etwas, das Absicht ist, nimmt jeder Leser beim naechsten Mal nicht mehr ernst.
     */
    boolean startwertWirkt() {
        return art != Art.RASTER && art != Art.LEER && art != Art.FLACH;
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
