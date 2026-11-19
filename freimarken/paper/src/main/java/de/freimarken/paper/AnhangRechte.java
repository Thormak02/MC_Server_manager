package de.freimarken.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

/**
 * Der {@link Rechtegriff} mit Bukkits {@code PermissionAttachment} darunter.
 *
 * <p><b>Warum ein Anhaengsel und keine Rechteverwaltung</b> [entschieden 18.11.2026]:
 * LuckPerms und seine Verwandten legen ihre Daten in einer eigenen {@code .db} ab, und
 * {@code app/services/player_data_service.py} des Managers fasst solche Dateien beim
 * UUID-Umbau bewusst NICHT an. Der Rang waere nach einem Umbau still weg - der Spieler
 * stuende wieder als Reisender da, ohne dass irgendwo etwas rot wuerde. Mit einer eigenen
 * Tabelle und einem Anhaengsel haelt der Rang an einer Stelle, die dieses Projekt selbst
 * umzieht.
 *
 * <p><b>Ein Anhaengsel je Spieler, und es wird ERSETZT und nicht ergaenzt.</b> Beim
 * zweiten Setzen wird das alte abgeraeumt und ein neues gehaengt. Ein zweites daneben
 * liesse die alten Zahlen stehen, und eine Leiter, die je eine Zahl senkt, wirkte dann
 * nie - der Spieler behielte die hoehere, weil {@code Rangzahl} die hoechste nimmt.
 *
 * <p><b>Die Merkliste wird beim Verlassen geleert.</b> Das Anhaengsel selbst geht ohnehin
 * mit dem Spieler; die Liste hier tut es nicht und wuechse sonst, solange der Server
 * laeuft - ein Eintrag je Beitritt, auch fuer Leute, die seit Wochen weg sind.
 */
final class AnhangRechte implements Rechtegriff {

    private final Plugin plugin;

    /** Wem dieser Griff gerade etwas gibt. */
    private final Map<UUID, PermissionAttachment> anhaenge = new HashMap<>();

    AnhangRechte(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Ohne Plugin gibt es kein Anhaengsel -"
                    + " Bukkit haengt Rechte immer an ein bestimmtes");
        }
        this.plugin = plugin;
    }

    @Override
    public synchronized void setze(Player spieler, Set<String> rechte) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu setzen");
        }
        // ZUERST abraeumen: Das alte Buendel enthaelt die niedrigeren Zahlen, und die
        // duerfen nicht neben den neuen stehen bleiben.
        loese(spieler);
        if (rechte == null || rechte.isEmpty()) {
            return;
        }
        PermissionAttachment anhang = spieler.addAttachment(plugin);
        for (String recht : rechte) {
            anhang.setPermission(recht, true);
        }
        anhaenge.put(spieler.getUniqueId(), anhang);
    }

    @Override
    public synchronized void loese(Player spieler) {
        if (spieler == null) {
            return;
        }
        PermissionAttachment alt = anhaenge.remove(spieler.getUniqueId());
        if (alt == null) {
            return;
        }
        try {
            alt.remove();
        } catch (IllegalStateException schonWeg) {
            // Bukkit wirft, wenn das Anhaengsel nicht mehr haengt - etwa weil der Spieler
            // den Server in der Zwischenzeit verlassen hat. Das ist kein Fehler, sondern
            // genau der Zustand, den diese Methode herstellen soll. Ein Durchreichen
            // riss frueher oder spaeter einen Beitritt ab.
        }
    }
}
