package net.mcsm.lobby;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Winziger JSON-Parser fuer die flachen Bus-Nachrichten des Presence-Bridge-Endpoints
 * ({@code {"t":"up","uuid":"..","x":1.5,...}}). Kein externes Dependency. Unterstuetzt
 * Objekte, Strings, Zahlen, true/false/null - genug fuer die Bridge.
 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Parst EIN flaches JSON-Objekt in eine Map (Werte: String, Double, Boolean, null). */
    static Map<String, Object> parse(String s) {
        try {
            Json j = new Json(s);
            j.ws();
            Object o = j.value();
            return (o instanceof Map) ? castMap(o) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '"') return string();
        if (c == 't' || c == 'f') return bool();
        if (c == 'n') {
            i += 4;   // null
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            String key = string();
            ws();
            i++; // :
            Object val = value();
            m.put(key, val);
            ws();
            char c = s.charAt(i++);
            if (c == '}') break;
            // c == ',' -> weiter
        }
        return m;
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++; // opening "
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Object bool() {
        if (s.charAt(i) == 't') {
            i += 4; // true
            return Boolean.TRUE;
        }
        i += 5; // false
        return Boolean.FALSE;
    }

    private Double number() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                i++;
            } else {
                break;
            }
        }
        return Double.parseDouble(s.substring(start, i));
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                break;
            }
        }
    }
}
