package net.mcsm.lobby;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
 *   -&gt; {"token":"..","op":"join_check","server_id":7,"player":"David"}
 *   &lt;- {"ok":true}   |   {"ok":false,"reason":".."}
 * </pre>
 *
 * <p><b>FAIL-OPEN:</b> Timeout, kein Manager, falscher Token, kaputte Antwort -&gt; der
 * Wechsel wird ERLAUBT. Eine kaputte Pruefung darf niemanden aussperren; abgelehnt wird
 * nur bei einem ausdruecklichen {@code ok:false}.
 */
final class JoinCheck {

    /** Urteil des Managers. {@code allowed == false} nur bei einer klaren Absage. */
    static final class Result {
        final boolean allowed;
        final String reason;

        Result(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason == null ? "" : reason;
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
        if (serverId <= 0 || token.isEmpty() || port <= 0) {
            return ALLOW;
        }
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            String request = "{\"token\":" + quote(token)
                + ",\"op\":\"join_check\",\"server_id\":" + serverId
                + ",\"player\":" + quote(player) + "}\n";
            OutputStream out = sock.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) {
                return ALLOW;
            }
            Map<String, Object> answer = Json.parse(line);
            // Kein "ok"-Feld = kein Urteil (Auth-/Protokollfehler) -> durchlassen.
            if (answer == null || !(answer.get("ok") instanceof Boolean)) {
                return ALLOW;
            }
            if (Boolean.TRUE.equals(answer.get("ok"))) {
                return ALLOW;
            }
            Object reason = answer.get("reason");
            return new Result(false, reason == null ? "" : String.valueOf(reason));
        } catch (Exception ex) {
            return ALLOW;   // Manager nicht erreichbar -> nicht aussperren
        }
    }

    /** JSON-String inklusive Anfuehrungszeichen (Token/Spielername sicher einbetten). */
    private static String quote(String value) {
        StringBuilder b = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
