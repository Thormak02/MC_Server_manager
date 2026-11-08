package de.freimarken.paper;

import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Kistenshop;
import net.kyori.adventure.text.Component;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Der Klick an einem Kistenshop - Rechtsklick kauft, Linksklick verkauft.
 *
 * <p>So hat Lumia es gemacht, und das ist der ganze Grund fuer die zwei Maustasten: Kein
 * Befehl, kein Menue, keine zweite Kiste je Richtung. Mit Schleichtaste ein ganzer
 * Stapel.
 *
 * <p><b>Diese Klasse ist AUCH die Handelstuer</b> ({@link Schutzwache.Handelsklick}), und
 * das ist keine Sparsamkeit: Die Tuer fragt, OB hier ein Shop steht, der Handel braucht
 * WELCHER - dieselbe Frage mit zwei Antwortformen. Zwei Klassen dafuer waeren zwei Wege
 * zu derselben Antwort, und dann oeffnete die Tuer eines Tages an einer Stelle, an der
 * nicht gehandelt wird. Die Schutzwache verlaesst sich darauf: Was hier {@code true}
 * sagt, darf JEDER anklicken, ueberall.
 *
 * <p><b>Die Tuer antwortet nur am SCHILD, nie an der Kiste</b> - siehe
 * {@link #istHandelsschild}. Das ist der teuerste Fehler, der hier moeglich waere: Sagte
 * sie auch fuer die Shopkiste ja, oeffnete jeder Kunde mit einem Rechtsklick die fremde
 * Kiste und raeumte den Bestand aus, in der Markthalle am Ladenschutz vorbei.
 *
 * <p><b>Der Klick wird VERBRAUCHT, sobald ein fremder Shop am Schild haengt</b> - beide
 * Ergebnisse auf {@code DENY}. Das loest zwei Dinge auf einmal:
 *
 * <ul>
 *   <li>Der Linksklick ist in Minecraft der ANFANG eines Abbaus. Ohne das Deny schlaegt
 *       Lumias Verkaeufer bei jedem Verkauf auf das Schild.</li>
 *   <li>Der Rechtsklick mit einem Block in der Hand SETZT einen Block. Darum darf die
 *       Tuer in {@link Schutzwache} nicht mehr nach der Hand fragen: Wer Stein verkaufen
 *       will, haelt Stein - und die haeufigste Ware ist genau die, die ein Werkzeugtest
 *       aussperrt. Der Schutz liegt jetzt in der WIRKUNG und nicht in der Verweigerung:
 *       Der Handel findet statt, die Hand bleibt untaetig.</li>
 * </ul>
 *
 * <p>Am EIGENEN Shop wird nicht gehandelt, und darum wird der Klick dort auch nicht
 * verbraucht: Der Linksklick des Besitzers bleibt ein Abbau wie bisher. Das Schild eines
 * FREMDEN Shops ist damit per Linksklick nicht mehr zu brechen - abgebaut wird es ueber
 * die Kiste oder den Traegerblock, die {@code Shopwache} beide kennt.
 *
 * <p><b>Nur die HAUPTHAND</b> - siehe {@link #nurHaupthand}.
 *
 * <p><b>Was hier NICHT geprueft ist:</b> alles, was einen Server braucht - dass
 * {@code Tag.ALL_SIGNS} ein Schild trifft, dass ein Klick wirklich zweimal kommt, dass
 * {@code DENY} den Abbau verhindert. Dafuer stehen Quelltexttafeln, und die pruefen Text
 * und nicht Wirkung. Die Entscheidungen selbst - welcher Bescheid, welche Reihenfolge,
 * welche Buchung - liegen in {@link Shophandel} und sind ohne Server pruefbar.
 */
final class Shopklick implements Listener, Schutzwache.Handelsklick {

    private final Shophandel handel;
    private final Sprache sprache;

    Shopklick(Shophandel handel, Sprache sprache) {
        if (handel == null) {
            throw new IllegalArgumentException("Ohne Shophandel ist diese Tuer eine, die"
                    + " aufgeht und hinter der nichts ist - jeder duerfte jedes Schild"
                    + " anklicken, und gehandelt wuerde nicht.");
        }
        this.handel = handel;
        this.sprache = sprache;
    }

    // --- Die Handelstuer ------------------------------------------------------

    @Override
    public boolean istHandel(Player spieler, Block block) {
        // Erst die Blockart, dann der Shop: Jene ist ein Mengenvergleich und laeuft bei
        // JEDEM Klick auf JEDEN Block, dieser ist eine Map im Arbeitsspeicher. Dieselbe
        // Reihenfolge der Kosten wie in Shopwache.betroffen.
        boolean schild = Tag.ALL_SIGNS.isTagged(block.getType());
        return istHandelsschild(schild, schild ? shopAn(block) : null);
    }

    /**
     * Die Antwort der Handelstuer - ohne Bukkit und darum pruefbar.
     *
     * <p><b>Beide Tatsachen muessen stimmen, und die erste ist die wichtigere.</b> Ein
     * Shop hat zwei Bloecke: sein Schild und seine Kiste. Nur das SCHILD ist die
     * Bedienung; die Kiste ist der Vorrat, und die oeffnet der Besitzer. Waere diese
     * Methode mit einem Shop allein zufrieden, haette jeder Kunde einen Rechtsklick weit
     * Zugriff auf den ganzen Bestand - und in der Markthalle liefe das am Ladenschutz
     * vorbei, der genau das verhindert.
     *
     * <p>Darum nimmt sie den Schildblock als eigene Tatsache und nicht etwa den Block:
     * So kann eine Probe das NEIN fuer "kein Schildblock, obwohl dort ein Shop steht"
     * ohne Server festhalten. Das ist das staerkere Nein als eines an einem Attrappenblock
     * - es sagt, dass es einen Kistenpfad gar nicht gibt.
     *
     * <p>Eine eigene Methode und kein Zweig in {@link #istHandel}, und das ist der Befund
     * der Einspritzungskampagne vom 08.11.2026: Im Zweig waere die Bedingung nur ueber
     * einen laufenden Server erreichbar, also blieb jede Einspritzung darauf gruen.
     * Derselbe Schnitt wie bei {@code Schutzwache.alsHandelDurchlassen}.
     *
     * @param istSchildblock ob der angeklickte Block ein Schild ist
     * @param shopAmSchild   der Shop an GENAU diesem Block, oder {@code null}
     */
    static boolean istHandelsschild(boolean istSchildblock, Kistenshop shopAmSchild) {
        return istSchildblock && shopAmSchild != null;
    }

    // --- Der Klick ------------------------------------------------------------

    /**
     * Rechtsklick kauft, Linksklick verkauft.
     *
     * <p><b>{@code EventPriority.HIGH} und {@code ignoreCancelled}</b>, also NACH der
     * Schutzwache: Jene entscheidet, ob ein Klick ueberhaupt durchgeht, und ihre Tuer
     * laesst einen Shopklick durch. Diese Reihenfolge haengt damit an einer Prioritaet
     * und nicht an der Zeilenlage zweier {@code registerEvents} - eine Reihenfolge, die
     * am Startablauf haengt, faellt beim ersten Umstellen um.
     *
     * <p>Der {@code DENY}-Gurt dazu: Auch wenn ein anderes Plugin oder ein kuenftiger
     * Zweig den Block abgesagt hat, wird hier nicht gehandelt. Das haengt an keiner
     * Annahme darueber, wie {@code isCancelled} auf die zwei Ergebnisse abbildet - es
     * fragt sie selbst.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void beimKlicken(PlayerInteractEvent ereignis) {
        Handelsart art = artVon(ereignis.getAction());
        if (art == null || !nurHaupthand(ereignis.getHand())) {
            return;
        }
        Block block = ereignis.getClickedBlock();
        if (block == null || ereignis.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        if (!Tag.ALL_SIGNS.isTagged(block.getType())) {
            return;
        }
        String welt = Shopware.weltVon(block);
        if (welt == null) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        Shophandel.Ausgang ausgang = handel.handle(spieler.getUniqueId(), welt,
                block.getX(), block.getY(), block.getZ(), art, spieler.isSneaking());
        if (ausgang.verbraucht()) {
            // Getrennt abgesagt, wie in Schutzwache.beimKlicken: der Block, damit aus
            // dem Linksklick kein Abbau wird, und die Hand, damit der Rechtsklick mit
            // Blockware darin nichts setzt. Erst NACH dem Handel: Ein Deny davor waere
            // auch am eigenen Schild gesetzt, und dann waere es unzerbrechlich.
            ereignis.setUseInteractedBlock(Event.Result.DENY);
            ereignis.setUseItemInHand(Event.Result.DENY);
        }
        for (Component zeile : Shoptext.handelBalken(sprache, ausgang)) {
            spieler.sendActionBar(zeile);
        }
    }

    /**
     * Welche Tat dieser Klick ist - oder {@code null}, wenn keine.
     *
     * <p>Rechtsklick kauft, Linksklick verkauft. {@code PHYSICAL} (Druckplatte,
     * Stolperdraht) und die Klicks in die Luft sind keine von beiden und fallen hier
     * heraus - das ist auch der Grund, warum die Handpruefung erst DANACH kommt: Ein
     * {@code PHYSICAL}-Ereignis hat gar keine Hand, und ein Riegel darauf am
     * Methodenkopf legte den Druckplattenschutz still.
     */
    private static Handelsart artVon(Action tat) {
        if (tat == Action.RIGHT_CLICK_BLOCK) {
            return Handelsart.KUNDE_KAUFT;
        }
        if (tat == Action.LEFT_CLICK_BLOCK) {
            return Handelsart.KUNDE_VERKAUFT;
        }
        return null;
    }

    /**
     * Ob dieser Klick von der HAUPTHAND kommt.
     *
     * <p><b>Ein Klick kann zweimal gemeldet werden, einmal je Hand</b>, wenn die erste
     * Meldung die Benutzung nicht verbraucht hat. Ohne diese Zeile buchte ein Klick
     * zweimal und bewegte zweimal die Ware - der Kunde sieht einen Klick und zahlt
     * zweimal.
     *
     * <p><b>Der sicher erreichbare Weg dorthin ist der SNEAK-Klick</b>, also genau die
     * Geste, mit der ein Stapel gehandelt wird: Bei gedrueckter Schleichtaste mit etwas
     * in der Hand ueberspringt der Spielstand die Blockbenutzung und versucht nur den
     * Gegenstand; verbraucht der nichts, folgt der Versuch mit der zweiten Hand. Dann
     * waere die Doppelbuchung am teuersten.
     *
     * <p>Ob auch ein gewoehnlicher Rechtsklick auf ein gewachstes Schild ein zweites
     * Ereignis nachschickt, ist Vanilla-Verhalten und hier NICHT nachpruefbar - es
     * entscheidet sich daran, was der Spielstand als Ergebnis zurueckgibt, und dessen
     * Quelle liegt nicht in diesem Projekt. Die Zeile ist in beiden Faellen richtig und
     * kostet nichts.
     *
     * <p>{@code null} heisst nein. Eine Hand ohne Seite gibt es nur, wo gar nicht
     * geklickt wurde.
     */
    static boolean nurHaupthand(EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND;
    }

    /** Der Shop an genau diesem Schildblock - oder {@code null}. */
    private Kistenshop shopAn(Block block) {
        String welt = Shopware.weltVon(block);
        return welt == null ? null
                : handel.amSchild(welt, block.getX(), block.getY(), block.getZ());
    }
}
