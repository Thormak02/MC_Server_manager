package net.mcsm.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Presence-Bridge (Projektion): spiegelt Spieler der ANDEREN Lobby-Instanz (dem Python-Hub,
 * wo die modded Spieler sind) als Fake-Avatare in DIESE Paper-Lobby - und meldet umgekehrt
 * die lokalen Paper-Spieler an den Manager-Bus. So sehen sich Vanilla- und modded-Spieler
 * gegenseitig, obwohl sie auf getrennten Servern sind.
 *
 * Anbindung: eine TCP-Verbindung zum Manager (127.0.0.1:&lt;port&gt;, Token-Auth), zeilenweises
 * JSON. Rendering der Fremd-Avatare via packetevents (Fake-Player-Entities). Bewegung wird
 * per Teleport-Paket + HeadLook nachgezogen; der Client interpoliert.
 *
 * WICHTIG (Rendering-Regeln, aus der Recherche): fuer JEDEN Avatar zuerst einen
 * Player-Info-Eintrag (mit Textur) senden, DANN die Entity spawnen, DANN das
 * "displayed skin parts"-Metadaten-Byte (0x7F) setzen - sonst rendert der Skin/die
 * Aussen-Layer nicht.
 */
final class PresenceBridge {

    private final MCSMLobby plugin;
    private final String host;
    private final int port;
    private final String token;

    private volatile Socket socket;
    private volatile OutputStream out;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread reader;

    // Fremd-Avatare (uuid -> Avatar). Zugriff nur vom Main-Thread (Rendering muss dort laufen).
    private final Map<String, Avatar> avatars = new ConcurrentHashMap<>();
    private final AtomicInteger entityIds = new AtomicInteger(0x40000000); // hoher, kollisionsfreier Bereich

    // Zuletzt publizierte Position je lokalem Spieler (Drossel: nur bei Bewegung senden).
    private final Map<UUID, long[]> lastSent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPub = new ConcurrentHashMap<>();   // Keepalive-Zeit je Spieler
    private volatile long lastTraffic = 0L;                              // letzte Wire-Aktivitaet (Ping)
    private final AtomicBoolean loggedSendError = new AtomicBoolean(false);
    private long seq = 0L;
    private static final long KEEPALIVE_MS = 10_000L;   // Spieler mind. alle 10s neu melden (TTL-Schutz)
    private static final long PING_MS = 20_000L;        // sonst Ping, damit die Verbindung nicht abbricht

    PresenceBridge(MCSMLobby plugin, String host, int port, String token) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.token = token;
    }

    static final class Avatar {
        int entityId;
        UUID uuid;
        String name;
        double x, y, z;
        float yaw, pitch, headYaw;
    }

    void start() {
        if (running.getAndSet(true)) {
            return;
        }
        connectAsync();
        // Lokale Spieler ~10 Hz publizieren. MAIN-Thread, weil getLocation()/getOnlinePlayers()
        // off-thread unsicher ist (Paper) bzw. auf Folia hart fehlschlaegt. Der Socket-Write ist
        // klein + loopback -> unkritisch auf dem Main-Thread.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::publishLocal, 20L, 2L);
    }

    void stop() {
        running.set(false);
        closeSocket();
        // Direkt aufraeumen: stop() kommt aus onDisable (Main-Thread), wo der Scheduler
        // KEINE neuen Tasks mehr annimmt (sonst blieben die Avatare als Geister stehen).
        if (Bukkit.isPrimaryThread()) {
            cleanupAvatars();
        } else {
            runOnMain(this::cleanupAvatars);
        }
    }

    private void cleanupAvatars() {
        for (Avatar a : new ArrayList<>(avatars.values())) {
            despawn(a);
        }
        avatars.clear();
    }

    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, r);
            } catch (Throwable ignored) {   // waehrend onDisable abgelehnt -> egal
            }
        }
    }

    private void dropAllAvatars() {
        runOnMain(this::cleanupAvatars);
    }

    // --- Verbindung -----------------------------------------------------------
    private void connectAsync() {
        reader = new Thread(this::runConnection, "mcsm-presence");
        reader.setDaemon(true);
        reader.start();
    }

    private void runConnection() {
        while (running.get()) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 4000);
                s.setTcpNoDelay(true);
                this.socket = s;
                this.out = s.getOutputStream();
                sendLine("{\"t\":\"hello\",\"token\":\"" + esc(token) + "\",\"origin\":\"vanilla\"}");
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    handleServerLine(line);
                }
            } catch (Exception ex) {
                plugin.getLogger().info("Presence-Bridge nicht verbunden: " + ex.getMessage());
            }
            closeSocket();
            // Verbindung weg -> ALLE gespiegelten Avatare entfernen. Der Manager schickt beim
            // naechsten hello einen frischen Snapshot; sonst bleiben tote Avatare als Geister
            // stehen (ein Hub-Spieler, der waehrend der Trennung ging, kaeme nie als 'rm').
            dropAllAvatars();
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(5000);   // Reconnect-Backoff
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        out = null;
    }

    private synchronized void sendLine(String json) {
        OutputStream o = this.out;
        if (o == null) {
            return;
        }
        try {
            o.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
        } catch (Exception ex) {
            closeSocket();
        }
    }

    // --- Eingehende Manager-Nachrichten (Hub-Praesenzen) ----------------------
    private void handleServerLine(String line) {
        Map<String, Object> m = Json.parse(line);
        if (m == null) {
            return;
        }
        String t = String.valueOf(m.get("t"));
        if ("up".equals(t)) {
            final String uuid = str(m.get("uuid"));
            final String name = str(m.get("name"));
            final double x = num(m.get("x"));
            final double y = num(m.get("y"));
            final double z = num(m.get("z"));
            final float yaw = (float) num(m.get("yaw"));
            final float pitch = (float) num(m.get("pitch"));
            final float hy = (float) num(m.getOrDefault("hy", m.get("yaw")));
            final String tex = str(m.get("tex"));
            final String sig = str(m.get("sig"));
            Bukkit.getScheduler().runTask(plugin, () -> upsertAvatar(uuid, name, x, y, z, yaw, pitch, hy, tex, sig));
        } else if ("rm".equals(t)) {
            final String uuid = str(m.get("uuid"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                Avatar a = avatars.remove(uuid);
                if (a != null) {
                    despawn(a);
                }
            });
        } else if ("chat".equals(t)) {
            final String name = str(m.get("name"));
            final String text = str(m.get("text"));
            Bukkit.getScheduler().runTask(plugin,
                () -> Bukkit.broadcastMessage("§7[§bLobby§7] §f<" + name + "> " + text));
        }
    }

    // --- Fremd-Avatar rendern (Main-Thread) -----------------------------------
    private void upsertAvatar(String uuid, String name, double x, double y, double z,
                              float yaw, float pitch, float headYaw, String tex, String sig) {
        Avatar a = avatars.get(uuid);
        if (a == null) {
            a = new Avatar();
            a.entityId = entityIds.incrementAndGet();
            a.uuid = uuidFrom(uuid);
            a.name = name;
            a.x = x; a.y = y; a.z = z; a.yaw = yaw; a.pitch = pitch; a.headYaw = headYaw;
            avatars.put(uuid, a);
            spawn(a, tex, sig);
        } else {
            move(a, x, y, z, yaw, pitch, headYaw);
        }
    }

    private void spawn(Avatar a, String tex, String sig) {
        UserProfile profile = new UserProfile(a.uuid, trimName(a.name));
        if (tex != null && !tex.isEmpty()) {
            profile.getTextureProperties().add(new TextureProperty("textures", tex, sig == null ? "" : sig));
        }
        // 1) Player-Info-Eintrag (mit Textur) VOR dem Spawn. listed=true -> Tab-Eintrag
        //    (aeltere Clients brauchen ihn zum Rendern; Via uebersetzt abwaerts).
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile, true, 0, GameMode.SURVIVAL, null, null);
        WrapperPlayServerPlayerInfoUpdate infoPkt = new WrapperPlayServerPlayerInfoUpdate(
            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                       WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
            info);
        // 2) Entity spawnen (Typ PLAYER) - Location-Konstruktor (klare UUID, kein Optional).
        WrapperPlayServerSpawnEntity spawnPkt = new WrapperPlayServerSpawnEntity(
            a.entityId, a.uuid, EntityTypes.PLAYER,
            new Location(new Vector3d(a.x, a.y, a.z), a.yaw, a.pitch), a.headYaw, 0, null);
        // 3) Metadaten: displayed-skin-parts (Index 17) = 0x7F -> alle Skin-Layer sichtbar.
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(17, EntityDataTypes.BYTE, (byte) 0x7F));
        WrapperPlayServerEntityMetadata metaPkt = new WrapperPlayServerEntityMetadata(a.entityId, data);

        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, infoPkt);
            send(p, spawnPkt);
            send(p, metaPkt);
            send(p, new WrapperPlayServerEntityHeadLook(a.entityId, a.headYaw));
        }
    }

    private void move(Avatar a, double x, double y, double z, float yaw, float pitch, float headYaw) {
        a.x = x; a.y = y; a.z = z; a.yaw = yaw; a.pitch = pitch; a.headYaw = headYaw;
        WrapperPlayServerEntityTeleport tp = new WrapperPlayServerEntityTeleport(
            a.entityId, new Vector3d(x, y, z), yaw, pitch, false);
        WrapperPlayServerEntityHeadLook head = new WrapperPlayServerEntityHeadLook(a.entityId, headYaw);
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, tp);
            send(p, head);
        }
    }

    private void despawn(Avatar a) {
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(a.entityId);
        WrapperPlayServerPlayerInfoRemove remove = new WrapperPlayServerPlayerInfoRemove(
            Collections.singletonList(a.uuid));
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, destroy);
            send(p, remove);
        }
    }

    // Neu verbundenem Spieler alle vorhandenen Avatare zeigen (aus MCSMLobby.onJoin gerufen).
    void showAllTo(Player p) {
        for (Avatar a : avatars.values()) {
            UserProfile profile = new UserProfile(a.uuid, trimName(a.name));
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, null, null);
            send(p, new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                           WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED), info));
            send(p, new WrapperPlayServerSpawnEntity(a.entityId, a.uuid, EntityTypes.PLAYER,
                new Location(new Vector3d(a.x, a.y, a.z), a.yaw, a.pitch), a.headYaw, 0, null));
            List<EntityData<?>> data = new ArrayList<>();
            data.add(new EntityData<>(17, EntityDataTypes.BYTE, (byte) 0x7F));
            send(p, new WrapperPlayServerEntityMetadata(a.entityId, data));
            send(p, new WrapperPlayServerEntityHeadLook(a.entityId, a.headYaw));
        }
    }

    private void send(Player p, Object wrapper) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p,
                (com.github.retrooper.packetevents.wrapper.PacketWrapper<?>) wrapper);
        } catch (Throwable t) {
            // Ein Rendering-Fehler darf die Lobby nie stoeren - aber den ERSTEN einmal loggen,
            // sonst waere ein systematischer packetevents-Fehler voellig unsichtbar (keine Avatare,
            // kein Hinweis).
            if (loggedSendError.compareAndSet(false, true)) {
                plugin.getLogger().warning("Presence-Bridge: Avatar-Paket fehlgeschlagen (weitere "
                    + "unterdrueckt): " + t + " [" + wrapper.getClass().getSimpleName() + "]");
            }
        }
    }

    // --- Lokale Paper-Spieler an den Bus melden (async Task) -------------------
    private void publishLocal() {
        if (out == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.Location l = p.getLocation();
            long xi = Math.round(l.getX() * 32), yi = Math.round(l.getY() * 32), zi = Math.round(l.getZ() * 32);
            long yawi = Math.round(l.getYaw()), pitchi = Math.round(l.getPitch());
            long[] last = lastSent.get(p.getUniqueId());
            long[] cur = {xi, yi, zi, yawi, pitchi};
            boolean moved = last == null || last[0] != xi || last[1] != yi || last[2] != zi
                || last[3] != yawi || last[4] != pitchi;
            Long lp = lastPub.get(p.getUniqueId());
            boolean keepalive = lp == null || (now - lp) > KEEPALIVE_MS;
            if (!moved && !keepalive) {
                continue;   // steht still + Keepalive noch nicht faellig -> nichts senden
            }
            lastSent.put(p.getUniqueId(), cur);
            lastPub.put(p.getUniqueId(), now);
            lastTraffic = now;
            seq++;
            // Skin-Textur bleibt vorerst leer (die Hub-Seite nutzt ohnehin Default-Skins);
            // signierte Texturen kommen in einem spaeteren Schritt (Phase 3).
            String tex = "", sig = "";
            sendLine("{\"t\":\"up\",\"uuid\":\"" + p.getUniqueId() + "\",\"name\":\"" + esc(p.getName())
                + "\",\"x\":" + (l.getX()) + ",\"y\":" + (l.getY()) + ",\"z\":" + (l.getZ())
                + ",\"yaw\":" + l.getYaw() + ",\"pitch\":" + l.getPitch() + ",\"hy\":" + l.getYaw()
                + ",\"tex\":\"" + esc(tex) + "\",\"sig\":\"" + esc(sig) + "\",\"seq\":" + seq + "}");
        }
        // Kein Traffic seit PING_MS (z.B. leere Lobby) -> Ping, sonst schlaegt der 65s-Read-
        // Timeout des Managers zu und die Verbindung bricht ab.
        if (now - lastTraffic > PING_MS) {
            lastTraffic = now;
            sendLine("{\"t\":\"ping\"}");
        }
    }

    void onLocalQuit(Player p) {
        lastSent.remove(p.getUniqueId());
        sendLine("{\"t\":\"rm\",\"uuid\":\"" + p.getUniqueId() + "\"}");
    }

    void onLocalChat(String name, String text) {
        sendLine("{\"t\":\"chat\",\"name\":\"" + esc(name) + "\",\"text\":\"" + esc(text) + "\"}");
    }

    // --- Helfer ---------------------------------------------------------------
    private static String trimName(String n) {
        if (n == null) return "?";
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    private static UUID uuidFrom(String s) {
        try {
            String hex = s.replace("-", "");
            if (hex.length() == 32) {
                return new UUID(
                    Long.parseUnsignedLong(hex.substring(0, 16), 16),
                    Long.parseUnsignedLong(hex.substring(16, 32), 16));
            }
        } catch (Exception ignored) {
        }
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return o == null ? 0.0 : Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0;
        }
    }
}
