package net.mcsm.lobby;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fragt den Manager VOR einem Transfer, ob der Spieler auf dem Ziel landen darf.
 *
 * <p>Warum: Ein nativer {@code transfer()} TRENNT die Verbindung zur Lobby. Ist das Ziel
 * gebannt/voll/noch nicht bereit, fliegt der Spieler komplett raus - die Lobby kann ihn
 * danach nicht mehr erreichen und ihm auch nichts mehr sagen. Also wird vorher gefragt,
 * solange der Spieler noch hier ist.
 *
 * <p>Die Antwort kommt aus derselben Pruefung, die auch der Python-Hub benutzt. Dadurch
 * bekommt ein Spieler auf BEIDEN Lobbys denselben Text - egal mit welchem Client.
 *
 * <p>Protokoll (eine Zeile JSON hin, eine zurueck, dann zu):
 * <pre>
 *   -&gt; {"token":"..","op":"join_check","server_id":7,"player":"David",
 *       "client":{"brand":"vanilla","source":"bukkit"},"override":false}
 *   &lt;- {"ok":true}  |  {"ok":true,"note":".."}
 *      {"ok":false,"reason":"..","confirm":true}   &lt;- Rueckfrage, mit override ueberstimmbar
 *      {"ok":false,"reason":".."}                  &lt;- hart
 *
 *   -&gt; {"token":"..","op":"fit_check","server_ids":[2,5,7],"player":"David",
 *       "client":{"brand":"vanilla","source":"bukkit"}}
 *   &lt;- {"ok":true,"fits":{"2":{"level":"confirm","short":"Braucht NeoForge 1.21.1"}}}
 * </pre>
 *
 * <p><b>FAIL-OPEN:</b> Timeout, kein Manager, falscher Token, kaputte Antwort -&gt; der
 * Wechsel wird ERLAUBT. Eine kaputte Pruefung darf niemanden aussperren; abgelehnt wird
 * nur bei einem ausdruecklichen {@code ok:false}.
 */
final class JoinCheck {

    /**
     * Urteil des Managers. {@code allowed == false} nur bei einer klaren Absage.
     *
     * <p>{@code confirm} trennt die beiden Absage-Arten: {@code false} ist hart
     * (Ban, Whitelist, voll) und unueberstimmbar, {@code true} ist eine Rueckfrage
     * aus dem Client-Abgleich, die ein zweiter Klick mit {@code override} aufhebt.
     * {@code note} ist ein Hinweis, der den Wechsel NIE aufhaelt.
     */
    static final class Result {
        final boolean allowed;
        final String reason;
        final boolean confirm;
        final String note;

        Result(boolean allowed, String reason) {
            this(allowed, reason, false, "");
        }

        Result(boolean allowed, String reason, boolean confirm, String note) {
            this.allowed = allowed;
            this.reason = reason == null ? "" : reason;
            this.confirm = confirm;
            this.note = note == null ? "" : note;
        }
    }

    static final Result ALLOW = new Result(true, "");

    private final String host;
    private final int port;
    private final String token;
    private final int timeoutMs;

    JoinCheck(String host, int port, String token, int timeoutMs) {
        this.host = (host == null || host.isEmpty()) ? "127.0.0.1" : host;
        this.port = port;
        this.token = token == null ? "" : token;
        this.timeoutMs = Math.max(250, timeoutMs);
    }

    /** Blockiert bis zur Antwort - NUR aus einem Async-Task aufrufen, nie im Main-Thread. */
    Result check(int serverId, String player) {
        return check(serverId, player, "", false);
    }

    /**
     * Wie oben, zusaetzlich mit dem Client-Brand und dem Ueberstimmen einer Rueckfrage.
     *
     * <p>Blockiert bis zur Antwort - NUR aus einem Async-Task aufrufen.
     *
     * @param brand    roher Client-Brand oder "" - leer laesst den client-Block ganz weg
     * @param override {@code true} = der Spieler hat die Rueckfrage bejaht
     */
    Result check(int serverId, String player, String brand, boolean override) {
        if (serverId <= 0 || token.isEmpty() || port <= 0) {
            return ALLOW;
        }
        String request = "{\"token\":" + quote(token)
            + ",\"op\":\"join_check\",\"server_id\":" + serverId
            + ",\"player\":" + quote(player)
            + clientPart(brand)
            + ",\"override\":" + (override ? "true" : "false") + "}\n";
        String line = exchange(request);
        if (line == null) {
            return ALLOW;
        }
        Map<String, Object> answer = Json.parse(line);
        // Kein "ok"-Feld = kein Urteil (Auth-/Protokollfehler) -> durchlassen.
        if (answer == null || !(answer.get("ok") instanceof Boolean)) {
            return ALLOW;
        }
        if (Boolean.TRUE.equals(answer.get("ok"))) {
            Object note = answer.get("note");
            // note nur uebernehmen, wenn es wirklich ein String ist - eine Zahl oder ein
            // Objekt wuerde sonst als "{...}" im Chat landen.
            if (note instanceof String && !((String) note).isEmpty()) {
                return new Result(true, "", false, (String) note);
            }
            return ALLOW;
        }
        Object reason = answer.get("reason");
        Object confirm = answer.get("confirm");
        return new Result(false,
            reason instanceof String ? (String) reason : "",
            // Nur ein echtes Boolean zaehlt: alles andere (fehlend, Zahl, String) heisst
            // "harte Absage" - im Zweifel lieber nicht ueberstimmbar machen.
            Boolean.TRUE.equals(confirm),
            "");
    }

    /**
     * Fuer die Menue-Marker: welche der Server passen nicht zum Client?
     *
     * <p>Blockiert bis zur Antwort - NUR aus einem Async-Task aufrufen.
     *
     * @return Server-ID (als String) -&gt; Kurztext fuer die Lore. Ein fehlender Eintrag
     *     heisst "passt". Bei JEDEM Fehler eine leere Map - dann sieht das Menue exakt
     *     aus wie ohne diese Pruefung.
     */
    /**
     * Die Marker sind Kosmetik - das Menue darf auf sie nicht lange warten. Deshalb ein
     * eigenes, kurzes Zeitlimit: normal antwortet der Manager in wenigen Millisekunden,
     * und haengt er, geht das Menue lieber ungemarkt auf als gar nicht.
     */
    private static final int FIT_TIMEOUT_MS = 1_500;

    Map<String, String> fitCheck(int[] serverIds, String player, String brand) {
        // Ohne Brand kann der Manager ueber den Client nichts sagen (der Abgleich ist
        // dann abgeschaltet) - die Runde ueber das Netz waere garantiert ergebnislos.
        if (serverIds == null || serverIds.length == 0 || token.isEmpty() || port <= 0
            || brand == null || brand.isEmpty()) {
            return Collections.emptyMap();
        }
        StringBuilder ids = new StringBuilder();
        for (int serverId : serverIds) {
            if (serverId <= 0) {
                continue;
            }
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(serverId);
        }
        if (ids.length() == 0) {
            return Collections.emptyMap();
        }
        String request = "{\"token\":" + quote(token)
            + ",\"op\":\"fit_check\",\"server_ids\":[" + ids + "]"
            + ",\"player\":" + quote(player)
            + clientPart(brand) + "}\n";
        String line = exchange(request, Math.min(timeoutMs, FIT_TIMEOUT_MS));
        if (line == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> answer = Json.parse(line);
        if (answer == null || !(answer.get("fits") instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) answer.get("fits")).entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                continue;
            }
            Object shortText = ((Map<?, ?>) value).get("short");
            if (shortText instanceof String && !((String) shortText).isEmpty()) {
                out.put(String.valueOf(entry.getKey()), (String) shortText);
            }
        }
        return out;
    }

    /** Eine Zeile hin, eine Zeile zurueck. {@code null} = keine verwertbare Antwort. */
    private String exchange(String request) {
        return exchange(request, timeoutMs);
    }

    private String exchange(String request, int waitMs) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), waitMs);
            sock.setSoTimeout(waitMs);
            OutputStream out = sock.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            return in.readLine();
        } catch (Exception ex) {
            return null;   // Manager nicht erreichbar -> nicht aussperren
        }
    }

    /** {@code ,"client":{...}} oder "" bei leerem Brand. */
    private static String clientPart(String brand) {
        if (brand == null || brand.isEmpty()) {
            return "";
        }
        return ",\"client\":{\"brand\":" + quote(brand) + ",\"source\":\"bukkit\"}";
    }

    /**
     * JSON-String inklusive Anfuehrungszeichen.
     *
     * <p>PFLICHT fuer JEDES Feld, auch fuer den Brand: der kommt als freies
     * {@code readUtf(256)} vom Client. Ein rohes Anfuehrungszeichen oder ein
     * Zeilenumbruch darin wuerde das zeilenbasierte Framing auf der Python-Seite
     * zerlegen - die Antwort haette kein "ok"-Feld und die Pruefung waere damit
     * dauerhaft und unbemerkt auf ALLOW.
     */
    private static String quote(String value) {
        String v = value == null ? "" : value;
        StringBuilder b = new StringBuilder(v.length() + 2).append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }

}
