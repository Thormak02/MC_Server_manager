package net.mcsm.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Ziel fuer /lobby (leer, wenn dieser Server selbst die Lobby ist).
    private String lobbyHost = "";
    private int lobbyPort = 25565;
    private int lobbyId = 0;         // Server-ID der Lobby (fuer die Vorab-Pruefung bei /lobby)
    private String lobbyVelocityName = "";   // != leer -> internes Velocity-Umschalten (Connect)

    // BungeeCord/Velocity-Plugin-Message-Kanal (Velocity fuehrt "Connect" server-seitig aus).
    // Moderner, namespaced Name (aeltere "BungeeCord"-Schreibweise wird von neuem Paper abgelehnt).
    private static final String BUNGEE_CHANNEL = "bungeecord:main";

    // Live-Status je Server (key -> "online" | "sleeping" | "offline"). Ein Hintergrund-
    // Task pingt regelmaessig ueber das lokale Gateway und fuellt den Cache; das Menue
    // faerbt danach die Eintraege. So sieht man, ob ein Server gerade schlaeft.
    private final Map<String, String> statusCache = new ConcurrentHashMap<>();
    private boolean statusPingEnabled = true;
    private int statusIntervalTicks = 160;  // ~8s

    // Presence-Bridge: spiegelt Avatare der anderen Instanz (Hub). Nur aktiv, wenn in der
    // config eingeschaltet UND packetevents vorhanden ist (sonst bleibt das Feld null).
    private PresenceBridge presenceBridge;
    private boolean bridgeEnabled = false;
    private String bridgeHost = "127.0.0.1";
    private int bridgePort = 25606;
    private String bridgeToken = "";
    // Vorab-Pruefung (Graceful Rejection): fragt den Manager VOR jedem Wechsel, ob der
    // Spieler auf dem Ziel landen darf. Der Manager ist die einzige Wahrheitsquelle -
    // so sagt diese Lobby exakt dasselbe wie der Python-Hub.
    private JoinCheck joinCheck;
    // Laufende Pruefungen - verhindert, dass Klick-Spam mehrere Abfragen parallel startet.
    private final java.util.Set<UUID> checking = ConcurrentHashMap.newKeySet();
    // Letzte HARTE Absage je Spieler UND Ziel ("uuid|serverId"). Wer in einer Portal-Region steht,
    // loest sonst bei JEDEM Schritt eine neue Abfrage samt Nachricht aus. Ein anderes Ziel
    // bleibt dabei sofort waehlbar - gedrosselt wird nur die Wiederholung derselben Absage.
    private final Map<String, Long> lastRejection = new ConcurrentHashMap<>();
    // Offene Rueckfragen ("uuid|serverId" -> Zeitpunkt). Ein zweiter Klick innerhalb des
    // Fensters fragt mit override=true und setzt sich damit ueber den Client-Abgleich
    // hinweg. Bewusst GETRENNT von lastRejection: dort wuerde der Cooldown genau den
    // zweiten Klick schlucken, der den Wechsel rettet.
    private final Map<String, Long> pendingConfirm = new ConcurrentHashMap<>();
    private static final long CONFIRM_WINDOW_MS = 60_000L;
    // Eine Rueckfrage darf nicht im selben Augenblick beantwortet werden, in dem sie
    // gestellt wurde - sonst zaehlt ein Doppelklick oder ein Schritt als Zustimmung,
    // bevor der Spieler den Text ueberhaupt lesen konnte.
    private static final long CONFIRM_MIN_AGE_MS = 1_000L;
    // Wann zuletzt eine Rueckfrage gestellt wurde ("uuid|serverId"). Drosselt das
    // WIEDERHOLEN: wer in einer Portal-Region steht, loest sonst bei jedem Schritt
    // eine neue Abfrage samt Chatzeilen aus.
    private final Map<String, Long> lastAsked = new ConcurrentHashMap<>();
    // Laufende Menue-Abfragen. Eigenes Set statt checking, damit ein offenes Menue
    // keinen Transfer blockiert (und umgekehrt).
    private final java.util.Set<UUID> guiChecking = ConcurrentHashMap.newKeySet();

    private boolean peaceful = false;   // Lobby: kein Schaden/PvP/Rueckstoss + keine Spieler-Kollision
    private boolean resetOnJoin = false;  // Lobby: bei (Re-)Join an den Welt-Spawn + Adventure (ausser Ops)

    static final class ServerEntry {
        int id;         // Server-ID im Manager (0 = unbekannt -> keine Vorab-Pruefung)
        String key;
        String display;
        String host;
        int port;       // Transfer-Ziel (Gateway-Port)
        int pingPort;   // Status-Ping direkt (kein Gateway-Hop)
        String velocityName = "";  // != leer -> Velocity-Backend: intern umschalten (Connect)
        Material material = Material.GRASS_BLOCK;
        int slot = -1;
        boolean sleep = false;
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
        // Ausgehenden BungeeCord-Kanal registrieren -> Velocity-internes Umschalten (Connect).
        getServer().getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        // Tab-Vervollstaendigung fuer /server auf Server-Aliase setzen (sonst
        // schlaegt Bukkit Spielernamen vor).
        for (String cmd : new String[] {"server", "hub", "servers", "lobby", "mcsmlobby"}) {
            PluginCommand pc = getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }
        getLogger().info("MCSMLobby aktiv: " + servers.size()
            + " Server, " + regions.size() + " Portal-Regionen.");

        // Live-Status im Hintergrund pollen (async, blockiert nie den Server-Thread).
        if (statusPingEnabled) {
            getServer().getScheduler().runTaskTimerAsynchronously(
                this, this::pingAll, 40L, statusIntervalTicks);
        }

        // Presence-Bridge starten (nur wenn eingeschaltet + packetevents-Klassen vorhanden).
        if (bridgeEnabled && !bridgeToken.isEmpty()) {
            try {
                presenceBridge = new PresenceBridge(this, bridgeHost, bridgePort, bridgeToken);
                presenceBridge.start();
                getLogger().info("Presence-Bridge aktiv -> " + bridgeHost + ":" + bridgePort);
            } catch (Throwable t) {
                presenceBridge = null;
                getLogger().warning("Presence-Bridge nicht gestartet (packetevents fehlt?): " + t);
            }
        }
    }

    @Override
    public void onDisable() {
        if (presenceBridge != null) {
            try {
                presenceBridge.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    private void pingAll() {
        for (ServerEntry e : servers.values()) {
            statusCache.put(e.key, pingStatus(e.host, e.pingPort));
        }
    }

    /**
     * Server-List-Ping direkt auf 127.0.0.1:&lt;pingPort&gt; (Server bzw. dessen
     * Sleep-Proxy sitzt dort) - kein Gateway-Hop, kein DNS/Hairpin. Liefert
     * "online" / "sleeping" / "offline".
     */
    private String pingStatus(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 1500);
            s.setSoTimeout(1500);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            writeVarInt(hs, 0x00);
            writeVarInt(hs, 767);              // Protokollversion (beliebig fuer Status)
            writeString(hs, host);             // Gateway routet nach diesem Hostnamen
            hs.write((port >> 8) & 0xFF);
            hs.write(port & 0xFF);
            writeVarInt(hs, 1);                // next_state = status
            writePacket(out, hs.toByteArray());

            ByteArrayOutputStream req = new ByteArrayOutputStream();
            writeVarInt(req, 0x00);            // status request
            writePacket(out, req.toByteArray());

            readVarInt(in);                     // Paketlaenge (ignoriert)
            readVarInt(in);                     // Paket-ID (0x00)
            int jsonLen = readVarInt(in);
            byte[] buf = new byte[Math.max(0, Math.min(jsonLen, 64 * 1024))];
            readFully(in, buf);
            String json = new String(buf, StandardCharsets.UTF_8);
            if (json.contains("Schlaeft") || json.contains("Sleeping")) {
                return "sleeping";
            }
            return "online";
        } catch (Exception ex) {
            return "offline";
        }
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeString(OutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeVarInt(framed, body.length);
        framed.write(body);
        out.write(framed.toByteArray());
        out.flush();
    }

    private static int readVarInt(InputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("EOF");
            }
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 35) {
                throw new IOException("VarInt zu lang");
            }
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) {
                throw new IOException("EOF");
            }
            off += r;
        }
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
        lobbyHost = c.getString("lobby.host", "");
        lobbyPort = c.getInt("lobby.port", 25565);
        lobbyVelocityName = c.getString("lobby.velocity_name", "");
        lobbyId = c.getInt("lobby.id", 0);
        statusPingEnabled = c.getBoolean("status.enabled", true);
        statusIntervalTicks = Math.max(40, c.getInt("status.interval_seconds", 8) * 20);
        bridgeEnabled = c.getBoolean("bridge.enabled", false);
        bridgeHost = c.getString("bridge.host", "127.0.0.1");
        bridgePort = c.getInt("bridge.port", 25606);
        bridgeToken = c.getString("bridge.token", "");
        joinCheck = c.getBoolean("join_check.enabled", false)
            ? new JoinCheck(c.getString("join_check.host", "127.0.0.1"),
                            c.getInt("join_check.port", 25607),
                            c.getString("join_check.token", ""),
                            c.getInt("join_check.timeout_ms", 5000))
            : null;
        peaceful = c.getBoolean("peaceful", false);
        resetOnJoin = c.getBoolean("reset_on_join", false);

        // WICHTIG: servers ist eine LISTE, nicht eine Map mit Alias als Schluessel.
        // Bukkit-YAML behandelt '.' im Schluessel als Pfad-Trenner, d.h. ein Alias
        // wie "1.21.11-spigot" wuerde sonst in verschachtelte Sektionen zerfallen
        // (1 -> 21 -> 11-spigot) und nie geladen. Als Listen-Wert bleibt er intakt.
        for (Map<?, ?> raw : c.getMapList("servers")) {
            try {
                ServerEntry e = new ServerEntry();
                e.id = raw.get("id") instanceof Number ? ((Number) raw.get("id")).intValue() : 0;
                e.key = str(raw.get("key")).toLowerCase(Locale.ROOT);
                e.display = raw.get("display") != null ? str(raw.get("display")) : e.key;
                e.host = str(raw.get("host"));
                e.port = raw.get("port") instanceof Number ? ((Number) raw.get("port")).intValue() : 25565;
                e.pingPort = raw.get("ping_port") instanceof Number
                    ? ((Number) raw.get("ping_port")).intValue() : e.port;
                e.velocityName = raw.get("velocity_name") != null ? str(raw.get("velocity_name")) : "";
                e.slot = raw.get("slot") instanceof Number ? ((Number) raw.get("slot")).intValue() : -1;
                e.sleep = Boolean.TRUE.equals(raw.get("sleep"));
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

    /**
     * EINZIGER Trichter fuer jeden Serverwechsel (Kompass-GUI, /server, Schild, Portal).
     * Prueft erst beim Manager, transferiert nur bei gruenem Licht.
     */
    private void doTransfer(Player p, String key) {
        doTransfer(p, key, true);
    }

    /**
     * @param deliberate true, wenn der Spieler den Wechsel BEWUSST ausgeloest hat
     *                   (Menue-Klick, /server, Schild). Beim Hineinlaufen in eine
     *                   Portal-Region ist er false - eine Bewegung darf keine
     *                   Rueckfrage beantworten.
     */
    private void doTransfer(Player p, String key, boolean deliberate) {
        ServerEntry e = servers.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
        if (e == null) {
            p.sendMessage(color("&cUnbekannter Server: &e" + key));
            return;
        }
        if (onCooldown(p)) {
            return;
        }
        guardedTransfer(p, e.id, e.display, deliberate, () -> performTransfer(p, e));
    }

    private void performTransfer(Player p, ServerEntry e) {
        lastTransfer.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(color(transferMsg.replace("%server%", e.display)));
        // Velocity-Backend -> INTERN umschalten (kein Client-Transfer -> keine Re-Auth zum
        // online-mode-Proxy -> kein "target server is in online mode"-Fehler). Sonst nativer Transfer.
        if (e.velocityName != null && !e.velocityName.isEmpty()) {
            if (!connectViaProxy(p, e.velocityName)) {
                p.sendMessage(color("&cWechsel fehlgeschlagen. Bitte erneut versuchen."));
            }
            return;
        }
        try {
            p.transfer(e.host, e.port);
        } catch (Throwable t) {
            p.sendMessage(color("&cTransfer fehlgeschlagen. Braucht Client 1.20.5+."));
            getLogger().warning("transfer() fehlgeschlagen fuer " + p.getName() + ": " + t);
        }
    }

    /** Liegt der Zeitstempel zu ``key`` weniger als einen Cooldown zurueck? */
    private boolean isRecent(Map<String, Long> stamps, String key, long now) {
        Long stamp = stamps.get(key);
        return stamp != null && now - stamp < cooldownMs;
    }

    private boolean onCooldown(Player p) {
        Long last = lastTransfer.get(p.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownMs;
    }

    /**
     * Beim Manager nachfragen und den Wechsel nur ausfuehren, wenn er erlaubt ist.
     *
     * <p>Die Abfrage laeuft ASYNCHRON (TCP im Main-Thread wuerde den Server einfrieren),
     * der Wechsel selbst dann wieder synchron - Bukkit-API gehoert in den Main-Thread.
     * Bei einer Absage bleibt der Spieler genau da, wo er ist: in dieser Lobby. Er
     * bekommt den Grund als Nachricht und kann sofort etwas anderes waehlen (kein
     * Cooldown, denn es hat ja kein Wechsel stattgefunden).
     *
     * <p>Zwei Arten von Nein: eine HARTE Absage (Ban, Whitelist, voll) bleibt stehen,
     * eine RUECKFRAGE aus dem Client-Abgleich hebt der naechste Klick auf. Der Brand
     * ist ein freier String vom Client - er darf niemanden endgueltig aussperren.
     */
    private void guardedTransfer(Player p, int serverId, String display, boolean deliberate,
                                 Runnable transfer) {
        JoinCheck check = joinCheck;
        if (check == null || serverId <= 0) {
            transfer.run();   // keine Pruefung moeglich -> durchlassen (fail-open)
            return;
        }
        final UUID id = p.getUniqueId();
        final String rejectKey = id + "|" + serverId;
        final long now = System.currentTimeMillis();

        // Eine Rueckfrage beantwortet NUR eine bewusste Handlung, und erst nach einer
        // Sekunde. Ohne diese beiden Bedingungen liefert ein Schritt in einer
        // Portal-Region (onMove feuert bei jedem Blockwechsel) den "zweiten Klick"
        // selbst - der Spieler waere transferiert, bevor er die Frage lesen konnte.
        final boolean override = TransferGuard.mayOverride(
            pendingConfirm.get(rejectKey), now, deliberate,
            CONFIRM_MIN_AGE_MS, CONFIRM_WINDOW_MS);
        if (!override && (isRecent(lastRejection, rejectKey, now)
                || isRecent(lastAsked, rejectKey, now))) {
            return;   // gerade erst abgesagt oder gefragt -> nicht nochmal fragen/spammen
        }
        if (!checking.add(id)) {
            return;   // fuer diesen Spieler laeuft schon eine Pruefung -> Klick-Spam ignorieren
        }
        // ERST JETZT verbrauchen: ein verworfener Klick darf die Rueckfrage nicht aufessen.
        if (override) {
            pendingConfirm.remove(rejectKey);
        }
        // Beides VOR dem Async-Task holen: Bukkit-API gehoert in den Main-Thread
        // (p.getName() wurde hier bisher off-thread gelesen).
        final String pname = p.getName();
        final String brand = ClientInfo.brandOf(p);
        try {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                JoinCheck.Result result = check.check(serverId, pname, brand, override);
                try {
                getServer().getScheduler().runTask(this, () -> {
                    checking.remove(id);
                    if (!p.isOnline()) {
                        return;   // waehrend der Abfrage ausgeloggt
                    }
                    if (result.allowed) {
                        lastRejection.remove(rejectKey);
                        pendingConfirm.remove(rejectKey);
                        lastAsked.remove(rejectKey);
                        if (!result.note.isEmpty()) {
                            p.sendMessage(color("&e" + result.note));   // Hinweis, haelt nie auf
                        }
                        transfer.run();
                        return;
                    }
                    String reason = result.reason;
                    if (reason.isEmpty()) {
                        reason = result.confirm
                            ? ("Dein Client passt moeglicherweise nicht zu " + display + ".")
                            : (display + " ist gerade nicht erreichbar.");
                    }
                    if (result.confirm) {
                        // AUSDRUECKLICH NICHT in lastRejection - sonst schluckt der
                        // Cooldown genau den zweiten Klick, der hier angeboten wird.
                        // lastAsked drosselt nur das WIEDERHOLEN der Frage.
                        long asked = System.currentTimeMillis();
                        p.sendMessage(color("&e" + reason));
                        p.sendMessage(color(deliberate
                            ? "&eNochmal klicken, um es trotzdem zu versuchen."
                            : "&eWaehle ihn im Menue, um es trotzdem zu versuchen."));
                        pendingConfirm.put(rejectKey, asked);
                        lastAsked.put(rejectKey, asked);
                        return;
                    }
                    lastRejection.put(rejectKey, System.currentTimeMillis());
                    p.sendMessage(color("&c" + reason));
                });
                } catch (Throwable t) {
                    // Scheduler weg (Plugin faehrt gerade runter) -> Sperre loesen,
                    // sonst koennte dieser Spieler nach einem Reload nichts mehr anklicken.
                    checking.remove(id);
                }
            });
        } catch (Throwable t) {
            // Scheduler weg (Plugin faehrt runter) -> lieber ungeprueft durchlassen.
            checking.remove(id);
            transfer.run();
        }
    }

    /** Spieler ueber Velocity intern auf ein Backend schieben (BungeeCord 'Connect'). */
    private boolean connectViaProxy(Player p, String velocityName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(velocityName);
            p.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
            return true;
        } catch (Throwable t) {
            getLogger().warning("Connect (" + velocityName + ") fehlgeschlagen fuer " + p.getName() + ": " + t);
            return false;
        }
    }

    private void goLobby(Player p) {
        if (lobbyHost == null || lobbyHost.isEmpty()) {
            p.sendMessage(color("&7Du bist bereits in der Lobby."));
            return;
        }
        if (onCooldown(p)) {
            return;
        }
        // Auch der Rueckweg wird geprueft: ist die Lobby gerade nicht erreichbar, soll der
        // Spieler das lesen und hier bleiben - statt beim Transfer ins Leere zu fliegen.
        guardedTransfer(p, lobbyId, "Die Lobby", true, () -> performLobbyTransfer(p));
    }

    private void performLobbyTransfer(Player p) {
        lastTransfer.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(color(transferMsg.replace("%server%", "&bLobby")));
        if (lobbyVelocityName != null && !lobbyVelocityName.isEmpty()) {
            if (!connectViaProxy(p, lobbyVelocityName)) {
                p.sendMessage(color("&cWechsel fehlgeschlagen. Bitte erneut versuchen."));
            }
            return;
        }
        try {
            p.transfer(lobbyHost, lobbyPort);
        } catch (Throwable t) {
            p.sendMessage(color("&cTransfer fehlgeschlagen. Braucht Client 1.20.5+."));
            getLogger().warning("Lobby-transfer() fehlgeschlagen fuer " + p.getName() + ": " + t);
        }
    }

    /** Deterministische Slot -> Server-Zuordnung (gleich in openGui und onGuiClick). */
    private Map<Integer, ServerEntry> computeSlots() {
        int size = guiRows * 9;
        Map<Integer, ServerEntry> layout = new LinkedHashMap<>();
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int auto = 0;
        for (ServerEntry e : servers.values()) {
            int slot;
            if (e.slot >= 0 && e.slot < size && !used.contains(e.slot)) {
                slot = e.slot;
            } else {
                while (auto < size && used.contains(auto)) {
                    auto++;
                }
                if (auto >= size) {
                    break;
                }
                slot = auto;
            }
            used.add(slot);
            layout.put(slot, e);
        }
        return layout;
    }

    /**
     * Server-Auswahl oeffnen - vorher einmal fragen, welche Ziele nicht zum Client passen.
     *
     * <p>Die Abfrage laeuft ASYNCHRON (TCP im Main-Thread wuerde den Server einfrieren),
     * das Inventar wird danach im Main-Thread geoeffnet. Faellt die Antwort leer aus
     * (Fehler, Timeout, altes Backend, kein Brand), sieht das Menue exakt aus wie vorher.
     */
    private void openGui(Player p) {
        JoinCheck check = joinCheck;
        final int[] ids = menuServerIds();
        if (check == null || ids.length == 0) {
            openGuiNow(p, Collections.<String, String>emptyMap());
            return;
        }
        final UUID id = p.getUniqueId();
        if (!guiChecking.add(id)) {
            // Laeuft schon eine Abfrage -> lieber SOFORT ohne Marker oeffnen, als den
            // Klick stumm zu verschlucken. Die Marker sind Kosmetik, das Menue nicht.
            openGuiNow(p, Collections.<String, String>emptyMap());
            return;
        }
        final String pname = p.getName();
        final String brand = ClientInfo.brandOf(p);   // Bukkit-API -> hier, nicht off-thread
        try {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                Map<String, String> found;
                try {
                    found = check.fitCheck(ids, pname, brand);
                } catch (Throwable t) {
                    found = Collections.emptyMap();
                }
                final Map<String, String> fits = found;
                try {
                    getServer().getScheduler().runTask(this, () -> {
                        guiChecking.remove(id);
                        if (p.isOnline()) {
                            openGuiNow(p, fits);
                        }
                    });
                } catch (Throwable t) {
                    guiChecking.remove(id);   // Scheduler weg -> Menue faellt diesmal aus
                }
            });
        } catch (Throwable t) {
            guiChecking.remove(id);
            openGuiNow(p, Collections.<String, String>emptyMap());
        }
    }

    /** Server-IDs des Menues (0 = unbekannt -> keine Pruefung moeglich). */
    private int[] menuServerIds() {
        List<Integer> ids = new ArrayList<>();
        for (ServerEntry e : computeSlots().values()) {
            if (e.id > 0) {
                ids.add(e.id);
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /**
     * Inventar bauen und oeffnen. Nur aus dem Main-Thread.
     *
     * @param fits Server-ID (als String) -&gt; Kurzhinweis. Fehlender Eintrag = passt.
     */
    private void openGuiNow(Player p, Map<String, String> fits) {
        int size = guiRows * 9;
        Inventory inv = Bukkit.createInventory(null, size, guiTitle);
        for (Map.Entry<Integer, ServerEntry> slotEntry : computeSlots().entrySet()) {
            ServerEntry e = slotEntry.getValue();
            String state = statusCache.get(e.key);  // null = noch nicht gepingt
            String warn = e.id > 0 ? fits.get(String.valueOf(e.id)) : null;
            if (warn != null && warn.isEmpty()) {
                warn = null;
            }
            // Graues Glas statt des Server-Materials: sichtbar anders, aber weiter
            // klickbar - es ist eine Warnung, keine Sperre.
            ItemStack item = new ItemStack(warn != null ? Material.GRAY_STAINED_GLASS_PANE : e.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(statusDot(state) + e.display));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7" + e.host + ":" + e.port));
                lore.add(color(statusLine(state, e.sleep)));
                if (warn != null) {
                    lore.add(color("&7" + warn));
                }
                lore.add(color("&aKlick zum Verbinden"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slotEntry.getKey(), item);
        }
        p.openInventory(inv);
    }

    private String statusDot(String state) {
        if ("online".equals(state)) {
            return "&a● ";
        }
        if ("sleeping".equals(state)) {
            return "&d● ";
        }
        if ("offline".equals(state)) {
            return "&c● ";
        }
        return "&7● ";
    }

    private String statusLine(String state, boolean sleep) {
        if ("online".equals(state)) {
            return "&aOnline";
        }
        if ("sleeping".equals(state)) {
            return "&dSchlaeft &7– Beitritt weckt ihn (kurz warten)";
        }
        if ("offline".equals(state)) {
            return sleep ? "&dSchlaeft &7– Beitritt weckt ihn" : "&cOffline";
        }
        return "&7Status wird geprueft ...";
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

    /**
     * Absagen und offene Rueckfragen eines Spielers vergessen.
     *
     * <p>"Dir fehlt das Modpack" ist die eine Absage, die der Spieler selbst beheben
     * kann - sie darf einen Rejoin nicht ueberleben. Wer neu hereinkommt, faengt bei
     * null an statt gegen einen Cooldown von vorhin zu laufen.
     */
    private void clearGuards(UUID id) {
        String prefix = id + "|";
        lastRejection.keySet().removeIf(k -> k.startsWith(prefix));
        pendingConfirm.keySet().removeIf(k -> k.startsWith(prefix));
        lastAsked.keySet().removeIf(k -> k.startsWith(prefix));
        checking.remove(id);
        guiChecking.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        clearGuards(p.getUniqueId());
        // (Re-)Join: immer an den Welt-Spawn + Adventure. Operatoren ausgenommen, damit sie die
        // Lobby bauen koennen, ohne bei jedem Login zum Spawn gezogen/in Adventure gesetzt zu werden.
        if (resetOnJoin && !p.isOp()) {
            try {
                Location spawn = p.getWorld().getSpawnLocation().clone().add(0.5, 0.0, 0.5);
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
            } catch (Throwable ignored) {
            }
        }
        if (peaceful) {
            addToNoCollisionTeam(p);   // Spieler koennen sich nicht schubsen
        }
        if (!compassEnabled) {
            return;
        }
        p.getInventory().setItem(compassSlot, compassItem());
        if (!servers.isEmpty()) {
            p.sendMessage(color("&7Rechtsklick mit dem &bKompass&7 oder &e/server <name>&7 zum Wechseln."));
        }
        // Dem neuen Spieler alle bereits gespiegelten Fremd-Avatare zeigen.
        if (presenceBridge != null) {
            try {
                presenceBridge.showAllTo(p);
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastTransfer.remove(id);
        clearGuards(id);
        if (presenceBridge != null) {
            try {
                presenceBridge.onLocalQuit(e.getPlayer());
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        // Friedliche Lobby: KEIN Schaden fuer Spieler (deckt PvP, Fall, Ertrinken, Mobs ab).
        // Das Abbrechen des Damage-Events unterbindet auch den Rueckstoss beim Zuschlagen.
        // server.properties pvp=false stoppt nur den Schaden, nicht das Schlagen/Rueckstossen.
        // Nur wenn peaceful (= Lobby), damit Gameplay-Server normal kaempfen koennen.
        if (peaceful && e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (peaceful) {
            e.setCancelled(true);   // kein Hunger in der Lobby
        }
    }

    private void addToNoCollisionTeam(Player p) {
        // Spieler-Kollision (gegenseitiges Schubsen) via Scoreboard-Team abschalten.
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = sb.getTeam("mcsm_lobby");
            if (team == null) {
                team = sb.registerNewTeam("mcsm_lobby");
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }
            if (!team.hasEntry(p.getName())) {
                team.addEntry(p.getName());
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        // Lokale Anzeige mit einheitlichem [Lobby]-Praefix (wie im Hub + wie eingehende
        // Bridge-Nachrichten) -> jede Nachricht sieht ueberall gleich aus. %1$s=Name, %2$s=Text.
        try {
            e.setFormat("[Lobby] <%1$s> %2$s");
        } catch (Throwable ignored) {
        }
        if (presenceBridge != null) {
            try {
                presenceBridge.onLocalChat(e.getPlayer().getName(), e.getMessage());
            } catch (Throwable ignored) {
            }
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
        // Robust ueber den geklickten Slot aufloesen (nicht ueber den Anzeigenamen,
        // der jetzt einen Status-Punkt traegt).
        ServerEntry entry = computeSlots().get(e.getRawSlot());
        if (entry != null) {
            ((Player) e.getWhoClicked()).closeInventory();
            doTransfer((Player) e.getWhoClicked(), entry.key);
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
                // false: Hineinlaufen ist keine bewusste Zustimmung. Sonst beantwortete
                // der naechste Schritt die Rueckfrage, die der erste ausgeloest hat.
                doTransfer(e.getPlayer(), r.target, false);
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
            } else if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
                // Zeigt in 10 Sekunden auf dem Live-Host, ob der Brand hinter
                // Velocity/ViaProxy ueberhaupt bis hierher durchkommt.
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Nur fuer Spieler.");
                    return true;
                }
                String brand = ClientInfo.brandOf((Player) sender);
                sender.sendMessage(color("&7Client-Brand: &f"
                    + (brand.isEmpty() ? "(leer - nicht verfuegbar)" : brand)));
            } else {
                sender.sendMessage(color("&7MCSMLobby &f- /mcsmlobby reload &7| &f/mcsmlobby debug"));
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        Player p = (Player) sender;
        if (cmd.equals("lobby")) {
            goLobby(p);
            return true;
        }
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
