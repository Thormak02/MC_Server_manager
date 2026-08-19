package net.mcsm.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MCSMLobby - begehbare Transfer-Lobby fuer den MC Server Manager.
 *
 * Schickt Spieler per Vanilla-Transfer-Paket (MC 1.20.5+) an einen Zielserver.
 * Der Zielserver muss {@code accept-transfers=true} haben (setzt der Manager fuer
 * Gateway-Server automatisch). Funktioniert fuer JEDEN Servertyp (Vanilla, Forge,
 * Fabric, Spigot, Paper ...), weil der Client sich einfach direkt neu verbindet.
 *
 * Trigger:
 *   - Kompass (Rechtsklick) -> GUI mit allen Servern
 *   - /server &lt;name&gt;, /hub, /servers
 *   - Schild: Zeile 1 {@code [server]}, Zeile 2 Server-Key -> Rechtsklick
 *   - Portal-Regionen (Quader in der config) -> reinlaufen transferiert ("begehbar")
 *
 * Die config.yml wird vom Manager aus den Gateway-Routen erzeugt.
 */
public class MCSMLobby extends JavaPlugin implements Listener, TabCompleter {

    private final Map<String, ServerEntry> servers = new LinkedHashMap<>();
    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, Long> lastTransfer = new java.util.HashMap<>();

    private String guiTitle = "Server auswaehlen";
    private int guiRows = 3;
    private boolean compassEnabled = true;
    private int compassSlot = 4;
    private String compassName = "&bServer-Auswahl &7(Rechtsklick)";
    private String transferMsg = "&aVerbinde zu &e%server%&a...";
    private long cooldownMs = 3000L;

    static final class ServerEntry {
        String key;
        String display;
        String host;
        int port;
        Material material = Material.GRASS_BLOCK;
        int slot = -1;
    }

    static final class Region {
        String world;
        int x1, y1, z1, x2, y2, z2;
        String target;

        boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
                return false;
            }
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        // Tab-Vervollstaendigung fuer /server auf Server-Aliase setzen (sonst
        // schlaegt Bukkit Spielernamen vor).
        for (String cmd : new String[] {"server", "hub", "servers", "mcsmlobby"}) {
            PluginCommand pc = getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }
        getLogger().info("MCSMLobby aktiv: " + servers.size()
            + " Server, " + regions.size() + " Portal-Regionen.");
    }

    @SuppressWarnings("unchecked")
    private void load() {
        servers.clear();
        regions.clear();
        reloadConfig();
        FileConfiguration c = getConfig();

        guiTitle = color(c.getString("gui.title", guiTitle));
        guiRows = Math.max(1, Math.min(6, c.getInt("gui.rows", guiRows)));
        compassEnabled = c.getBoolean("compass.enabled", compassEnabled);
        // Auf gueltige Hotbar-/Inventar-Indizes klemmen (0..40), sonst wirft
        // setItem() im PlayerJoin-Handler bei jedem Login.
        compassSlot = Math.max(0, Math.min(40, c.getInt("compass.slot", compassSlot)));
        compassName = c.getString("compass.name", compassName);
        transferMsg = c.getString("messages.transfer", transferMsg);
        cooldownMs = Math.max(0L, c.getLong("cooldown_ms", cooldownMs));

        // WICHTIG: servers ist eine LISTE, nicht eine Map mit Alias als Schluessel.
        // Bukkit-YAML behandelt '.' im Schluessel als Pfad-Trenner, d.h. ein Alias
        // wie "1.21.11-spigot" wuerde sonst in verschachtelte Sektionen zerfallen
        // (1 -> 21 -> 11-spigot) und nie geladen. Als Listen-Wert bleibt er intakt.
        for (Map<?, ?> raw : c.getMapList("servers")) {
            try {
                ServerEntry e = new ServerEntry();
                e.key = str(raw.get("key")).toLowerCase(Locale.ROOT);
                e.display = raw.get("display") != null ? str(raw.get("display")) : e.key;
                e.host = str(raw.get("host"));
                e.port = raw.get("port") instanceof Number ? ((Number) raw.get("port")).intValue() : 25565;
                e.slot = raw.get("slot") instanceof Number ? ((Number) raw.get("slot")).intValue() : -1;
                Material m = Material.matchMaterial(
                    raw.get("material") != null ? str(raw.get("material")) : "GRASS_BLOCK");
                e.material = m != null ? m : Material.GRASS_BLOCK;
                if (!e.key.isEmpty() && !e.host.isEmpty()) {
                    servers.put(e.key, e);
                }
            } catch (Exception ex) {
                getLogger().warning("Ungueltiger Server-Eintrag uebersprungen: " + ex.getMessage());
            }
        }

        for (Map<?, ?> raw : c.getMapList("regions")) {
            try {
                Region r = new Region();
                r.world = String.valueOf(raw.get("world"));
                List<Integer> min = (List<Integer>) raw.get("min");
                List<Integer> max = (List<Integer>) raw.get("max");
                r.x1 = min.get(0); r.y1 = min.get(1); r.z1 = min.get(2);
                r.x2 = max.get(0); r.y2 = max.get(1); r.z2 = max.get(2);
                r.target = String.valueOf(raw.get("target")).toLowerCase(Locale.ROOT);
                if (servers.containsKey(r.target)) {
                    regions.add(r);
                }
            } catch (Exception ex) {
                getLogger().warning("Ungueltige Portal-Region uebersprungen: " + ex.getMessage());
            }
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void doTransfer(Player p, String key) {
        ServerEntry e = servers.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
        if (e == null) {
            p.sendMessage(color("&cUnbekannter Server: &e" + key));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastTransfer.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastTransfer.put(p.getUniqueId(), now);
        p.sendMessage(color(transferMsg.replace("%server%", e.display)));
        try {
            p.transfer(e.host, e.port);
        } catch (Throwable t) {
            p.sendMessage(color("&cTransfer fehlgeschlagen. Braucht Client 1.20.5+."));
            getLogger().warning("transfer() fehlgeschlagen fuer " + p.getName() + ": " + t);
        }
    }

    private void openGui(Player p) {
        int size = guiRows * 9;
        Inventory inv = Bukkit.createInventory(null, size, guiTitle);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int auto = 0;
        for (ServerEntry e : servers.values()) {
            int slot;
            if (e.slot >= 0 && e.slot < size && !used.contains(e.slot)) {
                slot = e.slot;
            } else {
                // Auto-Platzierung: freien Slot suchen, belegte (auch explizite)
                // ueberspringen, damit kein Icon ueberschrieben wird.
                while (auto < size && used.contains(auto)) {
                    auto++;
                }
                if (auto >= size) {
                    break;
                }
                slot = auto;
            }
            used.add(slot);
            ItemStack item = new ItemStack(e.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(e.display));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7" + e.host + ":" + e.port));
                lore.add(color("&aKlick zum Verbinden"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }
        p.openInventory(inv);
    }

    private ItemStack compassItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(compassName));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!compassEnabled) {
            return;
        }
        Player p = e.getPlayer();
        p.getInventory().setItem(compassSlot, compassItem());
        if (!servers.isEmpty()) {
            p.sendMessage(color("&7Rechtsklick mit dem &bKompass&7 oder &e/server <name>&7 zum Wechseln."));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;

        // Kompass -> GUI
        if (right && compassEnabled && e.getItem() != null
            && e.getItem().getType() == Material.COMPASS) {
            e.setCancelled(true);
            openGui(p);
            return;
        }

        // Schild [server] / Key
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Block b = e.getClickedBlock();
            if (b.getState() instanceof Sign) {
                Sign sign = (Sign) b.getState();
                String line0 = ChatColor.stripColor(sign.getLine(0)).trim().toLowerCase(Locale.ROOT);
                if (line0.equals("[server]")) {
                    String key = ChatColor.stripColor(sign.getLine(1)).trim();
                    e.setCancelled(true);
                    doTransfer(p, key);
                }
            }
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        String title;
        try {
            title = e.getView().getTitle();
        } catch (Throwable t) {
            return;
        }
        if (title == null || !title.equals(guiTitle)) {
            return;
        }
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        for (ServerEntry entry : servers.values()) {
            if (ChatColor.stripColor(color(entry.display)).equals(name)) {
                ((Player) e.getWhoClicked()).closeInventory();
                doTransfer((Player) e.getWhoClicked(), entry.key);
                return;
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (regions.isEmpty() || e.getTo() == null) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        for (Region r : regions) {
            if (r.contains(to)) {
                doTransfer(e.getPlayer(), r.target);
                return;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("mcsmlobby")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")
                && sender.hasPermission("mcsmlobby.admin")) {
                load();
                sender.sendMessage(color("&aMCSMLobby neu geladen: &e" + servers.size() + "&a Server."));
            } else {
                sender.sendMessage(color("&7MCSMLobby &f- /mcsmlobby reload"));
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        Player p = (Player) sender;
        if (cmd.equals("hub") || cmd.equals("servers")) {
            openGui(p);
            return true;
        }
        if (cmd.equals("server")) {
            if (args.length < 1) {
                p.sendMessage(color("&7Server: &f" + String.join(", ", servers.keySet())));
                openGui(p);
                return true;
            }
            doTransfer(p, args[0]);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // Nur fuer /server die Server-Aliase vorschlagen; sonst leere Liste
        // (verhindert die Bukkit-Standardvervollstaendigung mit Spielernamen).
        if (command.getName().equalsIgnoreCase("server") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String key : servers.keySet()) {
                if (key.startsWith(prefix)) {
                    out.add(key);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
