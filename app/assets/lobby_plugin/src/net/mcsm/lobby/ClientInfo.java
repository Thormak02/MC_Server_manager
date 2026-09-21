package net.mcsm.lobby;

import org.bukkit.entity.Player;

/**
 * Was diese Lobby ueber den Client eines Spielers sagen kann - bewusst nur EINE Sache:
 * den Loader-Brand ("vanilla", "fabric", "fml,forge", "neoforge" ...).
 *
 * <p>Der Brand ist ein freier String, den der Client selbst schickt. Er ist damit
 * faelschbar und darf nie zu einer endgueltigen Absage fuehren - nur zu einer
 * Rueckfrage. Ein leerer Brand ist ein voellig normaler Zustand (Spigot ohne
 * Paper-API, Client meldet nichts) und schaltet den Abgleich einfach ab.
 *
 * <p>BEWUSST NICHT erhoben:
 * <ul>
 *   <li><b>Protokollversion:</b> hinter Velocity/ViaProxy wertlos - Via ueberschreibt
 *       das Handshake-Feld, und {@code getProtocolVersion()} liefert dann die Version
 *       der LOBBY statt die des Spielers.</li>
 *   <li><b>Kanalliste:</b> kann mehrere Kilobyte gross werden. Der Endpoint ist
 *       zeilenbasiert und begrenzt - eine gerissene Zeilengrenze wuerde die Antwort
 *       unlesbar machen und die Pruefung still auf ALLOW stellen.</li>
 * </ul>
 */
final class ClientInfo {

    /** Kappungsgrenze wie auf der Python-Seite (profile_from_payload). */
    private static final int MAX_BRAND_CHARS = 64;

    private ClientInfo() {
    }

    /**
     * Roher Client-Brand oder "" (nie null).
     *
     * <p><b>NUR aus dem Main-Thread aufrufen</b> - das ist Bukkit-API.
     */
    static String brandOf(Player p) {
        if (p == null) {
            return "";
        }
        String brand;
        try {
            brand = p.getClientBrandName();
        } catch (Throwable t) {
            // Spigot/CraftBukkit kennt die Paper-Methode nicht (NoSuchMethodError).
            // Throwable statt Exception, weil ein Linkage-Fehler kein Exception ist.
            return "";
        }
        if (brand == null) {
            return "";
        }
        brand = brand.trim();
        return brand.length() > MAX_BRAND_CHARS ? brand.substring(0, MAX_BRAND_CHARS) : brand;
    }
}
