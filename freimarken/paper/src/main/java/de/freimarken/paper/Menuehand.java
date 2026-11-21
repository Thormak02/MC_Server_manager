package de.freimarken.paper;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Die Bukkit-Haelfte des Menues: Inventar, Gegenstaende, Anzeigenamen.
 *
 * <p>Diese Klasse ist die NAHT. {@code Bukkit.createInventory} braucht einen laufenden
 * Server, {@code new ItemStack} greift in Minecrafts Gegenstandsverzeichnis, und
 * {@code Material.matchMaterial} liest dasselbe Verzeichnis - keine Zeile davon laeuft in
 * einer Probe. Dahinter rechnet {@link Menuetafel} mit Zahlen und Zeichenketten, und
 * darum ist jede Lage des Menues pruefbar: ein Klick ins Leere, ein Klick auf ein fremdes
 * Fach, zwei Eintraege auf einem Fach.
 *
 * <p><b>Der Anzeigename kommt aus der Sprachdatei</b>, und das ist die Luecke aus Plan
 * 11.3: Paper uebersetzt Gegenstandsnamen nicht. Also wird jeder hier aufgeloest - mit
 * {@code displayName(Component)} und {@code lore(List)}, nicht mit einem Klartextnamen,
 * damit die Farbwelt gilt.
 *
 * <p><b>Die Kursivschrift muss ausdruecklich weg.</b> Minecraft setzt den Anzeigenamen
 * eines umbenannten Gegenstands kursiv; Adventure laesst sie stehen, wenn der Satz nichts
 * dazu sagt. {@code decoration(ITALIC, false)} am Component ist die Stelle, an der das
 * entschieden wird - sonst sieht jedes Menue aus wie ein umbenanntes Namensschild.
 */
final class Menuehand {

    private final Sprache sprache;
    private final Logger protokoll;

    Menuehand(Sprache sprache, Logger protokoll) {
        if (sprache == null || protokoll == null) {
            throw new IllegalArgumentException("Die Menuehand braucht Sprache und"
                    + " Protokoll");
        }
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Baut das Menue und oeffnet es - oder sagt ab, wenn ein Gegenstand fehlt.
     *
     * <p>Ein unbekannter Gegenstandsname ist ein BAUFEHLER und kein Betriebsfall: Die
     * Tafel steht im Programm. Trotzdem wird hier nicht geworfen, sondern geklagt und
     * abgesagt - ein Wurf mitten im Befehl liesse den Spieler mit einer roten englischen
     * Zeile stehen, und das Protokoll nennt den Namen, den es nicht gibt.
     *
     * <p><b>Gebaut wird die Tafel FUER DIESEN Spieler</b> ({@link Menuetafel#fuerRechte}):
     * Ein Eintrag, dessen Recht ihm fehlt, kommt gar nicht erst in den Halter. Die
     * Entscheidung faellt in der Tafel und nicht hier - hinter {@code createInventory}
     * kommt keine Probe mehr hin.
     *
     * @return ob das Menue offen ist
     */
    boolean oeffne(Player wer, Menuetafel tafel) {
        // GEFILTERT, und zwar vor dem Halter: Was hier hineingeht, liest die Wache beim
        // Klick wieder heraus. Ein Fach, das der Spieler nicht haben darf, ist damit
        // nicht nur leer, sondern unbekannt - sonst klickte er auf das leere Fach und
        // bekaeme doch Brigadiers englische Zeile.
        Menuetafel sichtbar = tafel.fuerRechte(wer::hasPermission);
        Menuehalter halter = new Menuehalter(sichtbar);
        Inventory inventar = Bukkit.createInventory(halter, sichtbar.faecher(),
                sprache.eineZeile(sichtbar.titel(), Einsatz.keine()));
        halter.setzeInventar(inventar);
        for (Menuetafel.Eintrag eintrag : sichtbar.eintraege()) {
            Material art = Material.matchMaterial(eintrag.gegenstand());
            if (art == null || !art.isItem()) {
                // isItem gefragt und nicht nur auf null geprueft: Es gibt Materialien,
                // die nur BLOCK sind (potatoes), und new ItemStack wirft dafuer. Dieselbe
                // Lehre steht an der Naht des Muenzhauses.
                protokoll.log(Level.SEVERE, "Das Menue nennt den Gegenstand "
                        + eintrag.gegenstand() + " in Fach " + eintrag.fach()
                        + ", und den gibt es nicht (oder er ist kein Gegenstand)."
                        + " Das Menue bleibt zu.");
                return false;
            }
            inventar.setItem(eintrag.fach(), gegenstand(art, eintrag));
        }
        wer.openInventory(inventar);
        return true;
    }

    /** Ein Gegenstand mit Namen und Erklaerung - beide aus der Sprachdatei. */
    private ItemStack gegenstand(Material art, Menuetafel.Eintrag eintrag) {
        ItemStack stapel = new ItemStack(art);
        ItemMeta beschriftung = stapel.getItemMeta();
        beschriftung.displayName(ohneKursiv(sprache.eineZeile(eintrag.name(),
                Einsatz.keine())));
        List<Component> zeilen = new java.util.ArrayList<>();
        zeilen.add(ohneKursiv(sprache.eineZeile(eintrag.erklaerung(), Einsatz.keine())));
        if (eintrag.tut()) {
            zeilen.add(ohneKursiv(sprache.eineZeile(MenueSatz.KLICK,
                    Einsatz.von("befehl", eintrag.befehl()))));
        }
        beschriftung.lore(zeilen);
        stapel.setItemMeta(beschriftung);
        return stapel;
    }

    /**
     * Dieselbe Zeile ohne Kursivschrift.
     *
     * <p>Minecraft setzt den Namen eines umbenannten Gegenstands kursiv, und Adventure
     * laesst das stehen, solange der Satz nichts dazu sagt. Entschieden wird es hier und
     * nicht in der Sprachdatei: Ein {@code <italic:false>} in achtundzwanzig Saetzen waere
     * achtundzwanzigmal dieselbe Entscheidung, und wer einen Satz dazuschreibt, vergisst
     * sie.
     */
    private static Component ohneKursiv(Component zeile) {
        return zeile.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                false);
    }

}
