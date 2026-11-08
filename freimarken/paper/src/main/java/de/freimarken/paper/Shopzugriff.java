package de.freimarken.paper;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Server;

/**
 * Ob ein Spieler an einer Stelle handeln darf - gefragt bei DERSELBEN Wache wie jeder
 * Blockklick.
 *
 * <p><b>Eine eigene Klasse und kein Lambda in der Verdrahtung</b>, und das ist nicht
 * Geschmack: Hier stehen drei Entscheidungen, von denen jede einzeln falsch sein kann -
 * was bei einem abgemeldeten Spieler gilt, was bei einer nicht geladenen Welt, und
 * welche Wache ueberhaupt gefragt wird. In einem Lambda waere das ein Absatz mitten im
 * Start, den niemand mehr liest.
 *
 * <p><b>{@code Schutzwache.darfHandeln} und keine eigene Rechtefrage.</b> Das ist der
 * Grund, warum diese Klasse existiert: Dahinter liegt genau der Entscheid, der ueber
 * jeden gesetzten Block, jede geoeffnete Kiste und jeden Hebel faellt - Parzellen,
 * Weg und Mauer, Stadtgrenze, Ladenschutz und das Teamrecht. Eine zweite Frage daneben
 * liefe auseinander, und dann duerfte jemand einen Shop anlegen, wo er nicht bauen darf.
 *
 * <p><b>Gefragt wird nach der KISTENSTELLE</b>, nicht nach dem Schild - siehe den Kopf
 * von {@link Shopbuch}. Dass er am Schild bauen durfte, hat die Wache schon entschieden,
 * als er es setzte; die Kiste kann einem anderen gehoeren.
 */
final class Shopzugriff implements Shopbuch.Zugriff {

    private final Server server;
    private final Shopware waren;
    private final Schutzwache wache;

    Shopzugriff(Server server, Shopware waren, Schutzwache wache) {
        this.server = server;
        this.waren = waren;
        this.wache = wache;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Bei jedem Zweifel NEIN.</b> Drei Wege fuehren hierher zu einem {@code false},
     * und keiner ist ein Fehler des Programms: Der Spieler hat den Server zwischen dem
     * Beschreiben des Schildes und dieser Frage verlassen, die Welt ist nicht geladen,
     * oder die Wache sagt nein. Die ersten zwei sind Augenblicksfaelle - dann entsteht
     * kein Shop, und der Spieler kann es wieder versuchen. Ein {@code true} im Zweifel
     * waere dagegen ein Shop an einer Kiste, die einem anderen gehoert.
     */
    @Override
    public boolean darf(UUID wer, String welt, int x, int y, int z) {
        Player spieler = server.getPlayer(wer);
        if (spieler == null) {
            return false;
        }
        // Die Weltaufloesung geht durch Shopware und nicht an ihr vorbei: Das ist die
        // Stelle, die den Weltschluessel kennt, und zwei Aufloesungen daneben waeren zwei
        // Antworten auf dieselbe Frage.
        Block block = waren.blockAn(welt, x, y, z);
        if (block == null) {
            return false;
        }
        return wache.darfHandeln(spieler, new Location(block.getWorld(), x, y, z));
    }
}
