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
     * <p>Eine feste Liste und nicht beliebig viele: Jede Art ist ein Stueck Programm, das
     * es geben muss. Ein weiterer Eintrag in der Datei kann keinen Generator erfinden -
     * deshalb nennt die Liste die Arten, nicht Klassennamen.
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
        FLACH,

        /**
         * Stadtfelder in der Leere - die Welt der Freistaedte [11.11.2026].
         *
         * <p>Ein festes Raster aus Feldern zu 256x256, durch Leere getrennt und mit
         * unsichtbarem Rand: {@code StadtfelderGenerator} und {@code Stadtfeldplan}.
         * Eine Stadt ist damit ein ORT und kein Nachbar - hinein kommt man nur mit
         * {@code /warp freistadt <name>}.
         *
         * <p>Der Startwert ist hier ohne Wirkung - wie bei {@link #RASTER},
         * {@link #LEER} und {@link #FLACH}: Das Gelaende kommt aus der Rechnung und
         * nicht aus einer Zahl.
         */
        STADTFELDER,

        /**
         * Gleiche Inseln in gleichem Abstand, dazwischen Meer - Hafenwerder
         * [18.11.2026].
         *
         * <p>Ein festes Raster aus Feldern zu 128x128, durch 128 Bloecke offenes Meer
         * getrennt: {@code InselnGenerator} und {@code Inselplan}. Das FELD ist das
         * Eigentum, und darin liegt die Insel mit einem Wasserring um sich herum - ihr
         * Hafen. Keine Barriere wie bei den Stadtfeldern: Hinueberschwimmen soll man
         * duerfen, nur bauen nicht.
         *
         * <p>Plan 4.1 sagte "Gelaende: natuerlich gewachsen", 6.2 einen Festpreis von
         * 75.000 Mark. Beides zusammen geht nicht - die eine Insel laege im Flachwasser,
         * die naechste halb im Berg, und EIN Preis kann nicht fuer beide derselbe sein.
         * Darum ist jede Insel DIESELBE Insel, nur anderswo; dieselbe Entscheidung wie am
         * 11.11.2026 bei den Freistaedten.
         *
         * <p>Der Startwert ist hier ohne Wirkung - das Gelaende kommt aus der Rechnung.
         */
        INSELN,

        /**
         * Leere mit einem Raster und einem einzigen Boden - Wolkenwerder [18.11.2026].
         *
         * <p>Ein festes Raster aus Feldern zu 128x128, durch 384 Bloecke Leere
         * getrennt: {@code WolkenGenerator} und {@code Wolkenplan}. Der Generator
         * erzeugt davon GENAU EIN Feld - das Ankunftsfeld mit seiner Steinplattform -,
         * und jede Startinsel setzt danach das Plugin.
         *
         * <p><b>Warum das nicht {@link #LEER} ist</b>, obwohl die Welt fast ganz daraus
         * besteht: Drei Dinge haengen an der Art und nicht am Inhalt. Erstens rundet
         * {@code Weltenbau.rastermass} die Weltgrenze auf ganze Felder - ohne das liefe
         * sie mitten durch Inselfelder. Zweitens braucht ein Ankommender einen festen
         * Block unter den Fuessen, sonst fuehrt dorthin keine Reise. Drittens traegt der
         * Biomgeber die Farbtafel, aus der die Biomwahl spaeter waehlt - und die wird
         * genau einmal beim Anlegen gesetzt.
         *
         * <p>Der Startwert ist hier ohne Wirkung - es gibt kein Gelaende.
         */
        WOLKEN
    }

    /** Ob fuer diese Welt ueberhaupt eine Grenze gesetzt werden soll. */
    boolean hatGrenze() {
        return kante > 0;
    }

    /**
     * Ob es in dieser Welt ueberhaupt etwas vorzugenerieren GIBT.
     *
     * <p>Vorgeneriert wird mit Chunky, und der Startbericht nennt jede Welt, die noch
     * nicht fertig ist. Das ist richtig fuer jede Welt mit Gelaende - und falsch fuer
     * eine aus Leere: Dort gibt es nichts zu erzeugen ausser Luft, und doch waeren die
     * neun Stichprobenchunks erst nach einem Chunky-Lauf "fertig". Die Folge waere eine
     * Warnung bei JEDEM Start, die nie verschwindet - und nach drei Wochen liest niemand
     * mehr die naechste, die wirklich neu ist.
     *
     * <p>Fuer {@link Art#WOLKEN} gilt dasselbe: Der Generator legt ein einziges Feld an,
     * und jede Insel darin entsteht in dem Augenblick, in dem jemand sie nimmt. 390.000
     * leere Chunks vorzuerzeugen hiesse, Platz auf der Platte fuer Luft zu belegen.
     *
     * <p>Ein Verzweig ueber ALLE Arten und ohne {@code default}, aus demselben Grund wie
     * bei {@link #startwertWirkt}: Eine neue Art soll sich nicht stillschweigend in die
     * eine oder die andere Richtung einsortieren.
     */
    boolean vorzugenerieren() {
        return switch (art) {
            case RASTER, GEWOEHNLICH, FLACH, STADTFELDER, INSELN -> true;
            case LEER, WOLKEN -> false;
        };
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
     * <p>Nur bei {@link Art#GEWOEHNLICH}. Beim {@link Art#RASTER} kommt das Gelaende aus
     * dem Parzellengenerator, bei {@link Art#LEER} aus der Leere, bei
     * {@link Art#STADTFELDER} und {@link Art#INSELN} aus der Rechnung, und eine
     * {@link Art#FLACH}e Ebene ist eine Ebene - gleich welche Zahl darueber steht.
     *
     * <p><b>Diese Frage steht hier und nicht an ihren drei Aufrufstellen</b>, und das
     * ist am 09.11.2026 im Startlauf teuer geworden: Der Startwert wurde fuer eine
     * flache Welt richtig NICHT gesetzt, die Abweichungsklage kam aber trotzdem -
     * "die Welt hat 8765839788193927876, die Liste sagt 110926001". Eine Klage ueber
     * etwas, das Absicht ist, nimmt jeder Leser beim naechsten Mal nicht mehr ernst.
     *
     * <p><b>Ein Verzweig ueber ALLE Arten und keine Kette von Ungleichheiten</b>
     * [18.11.2026]: Jene stand hier bis zur vierten Art und war eine Falle in die
     * gefaehrliche Richtung - eine neu dazugekommene Art galt stillschweigend als
     * startwertfaehig, und die Klage vom 09.11. waere wiedergekommen. Ein Verzweig ohne
     * {@code default} laesst sich nicht erweitern, ohne die Frage zu beantworten: Er
     * uebersetzt sich sonst nicht.
     */
    boolean startwertWirkt() {
        return switch (art) {
            case GEWOEHNLICH -> true;
            case RASTER, LEER, FLACH, STADTFELDER, INSELN, WOLKEN -> false;
        };
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
