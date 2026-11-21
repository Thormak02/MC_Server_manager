package de.freimarken.paper;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Der Klick in einem Menue des Plugins - und die Absage aller anderen.
 *
 * <p><b>Abgesagt wird JEDER Klick in einem Menue, auch der auf ein leeres Fach.</b> Die
 * Gegenstaende darin sind Knoepfe und keine Ware: Wer einen herausnehmen koennte, haette
 * sich aus einem Hilfetext einen Diamanten gemacht. Dieselbe Liste von Wegen wie beim
 * Shopschutz - Linksklick, Rechtsklick, Umschaltklick, Zahlentasten, Doppelklick,
 * Tausch mit der zweiten Hand - geht durch dasselbe Ereignis; das ZIEHEN ueber mehrere
 * Faecher ist ein eigenes und darum ein zweiter Empfaenger.
 *
 * <p><b>Ein Klick fuehrt einen BEFEHL aus.</b> Das Menue gewaehrt nie etwas selbst: Es
 * tippt fuer den Spieler, und Recht, Geld und Ort prueft derselbe Befehlszweig, den er
 * auch von Hand erreicht. Die Begruendung steht bei {@link Menuetafel}.
 *
 * <p><b>Erst schliessen, dann tippen.</b> Ein Befehl, der selbst ein Menue oeffnet
 * ({@code /menu} aus einem Menue heraus), liefe sonst gegen ein offenes - und Bukkit
 * schickt dafuer ein Schliessereignis mitten in die Ausfuehrung. Dieselbe Reihenfolge
 * macht der Spieler von Hand: Truhe zu, dann tippen.
 */
final class Menuewache implements Listener {

    private final Logger protokoll;

    Menuewache(Logger protokoll) {
        if (protokoll == null) {
            throw new IllegalArgumentException("Ohne Protokoll keine Menuewache");
        }
        this.protokoll = protokoll;
    }

    @EventHandler(ignoreCancelled = true)
    public void beimKlicken(InventoryClickEvent ereignis) {
        Optional<Menuetafel> tafel = Menuehalter.tafelVon(ereignis.getInventory());
        if (tafel.isEmpty()) {
            return;
        }
        // ZUERST absagen, dann handeln: Ein Rueckweg nach einer Ausnahme im Zweig darunter
        // liesse den Knopf in der Hand des Spielers.
        ereignis.setCancelled(true);
        if (!(ereignis.getWhoClicked() instanceof Player wer)) {
            return;
        }
        // getRawSlot und nicht getSlot, und das ist der EINZIGE Riegel gegen einen Klick
        // ausserhalb des Menues: Jenes zaehlt ueber beide Inventare, dieses im geklickten
        // - ein Klick auf Fach 37 des RUCKSACKS waere mit getSlot Fach 10 des MENUES, und
        // der Spieler fuehrte einen Befehl aus, den er nicht angeklickt hat. Ein Fach
        // ausserhalb der Tafel (oder -1 fuer einen Klick neben das Fenster) findet keinen
        // Eintrag, und dann geschieht nichts.
        //
        // <p>Hier stand bis zum 21.11.2026 eine ZWEITE Frage - ob das GEKLICKTE Inventar
        // das Menue ist. Sie sagte denselben Fall ab und machte diese hier unpruefbar:
        // Mit ihr davor laeuft kein Klick mehr durch, bei dem rohes und umgerechnetes
        // Fach auseinandergehen, und ein Tausch der beiden Methoden blieb gruen. Zwei
        // Riegel fuer denselben Fall verdecken sich, also ist der zweite heraus -
        // MenuewacheTest 3 haelt den Fall jetzt wirklich.
        Optional<Menuetafel.Eintrag> eintrag = tafel.get().imFach(ereignis.getRawSlot());
        if (eintrag.isEmpty() || !eintrag.get().tut()) {
            return;
        }
        String befehl = eintrag.get().befehl();
        wer.closeInventory();
        protokoll.log(Level.FINE, "Menue: " + wer.getName() + " tippt " + befehl);
        wer.performCommand(befehl);
    }

    /**
     * Das ZIEHEN ueber mehrere Faecher - ein eigenes Ereignis und darum ein eigener
     * Empfaenger.
     *
     * <p>Ohne diesen Zweig blieb der haeufigste Weg offen, einen Knopf aus einem Menue zu
     * holen: Maus halten und ueber die Faecher ziehen. Derselbe Fund steht beim
     * Shopschutz, und dort hat er einen eigenen Absatz.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimZiehen(InventoryDragEvent ereignis) {
        if (Menuehalter.istMenue(ereignis.getInventory())) {
            ereignis.setCancelled(true);
        }
    }
}
