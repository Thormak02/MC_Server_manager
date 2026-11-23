package de.freimarken.kern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche Platzhalterbauten Altfurt hat - die Liste aus der {@code config.yml} (Plan 2.1).
 *
 * <p>Dieselbe Bauart wie {@link Rundgangsverzeichnis}: Die REIHENFOLGE der Datei bleibt.
 * Hier ist sie kein Weg, sondern die Reihenfolge, in der gebaut und berichtet wird - und
 * ein Bericht, dessen Zeilen bei jedem Start anders stehen, ist einer, den niemand
 * vergleicht. Bei zwei Eintraegen zum selben Namen gewinnt der ERSTE; der zweite wird
 * beim Lesen beklagt, nicht hier - diese Klasse kennt keinen Betreiber.
 *
 * <p><b>Dieses Verzeichnis wird NUR BEIM START gelesen.</b> Es ist kein Wert, sondern ein
 * Ort: Verschiebt ein Neuladen den Quader eines Baus, stuende das Haus an der alten
 * Stelle und das Programm rechnete mit der neuen - und der naechste Start setzte ein
 * zweites daneben, ohne das erste abzuraeumen. Dieselbe Entscheidung wie bei den
 * Ladeneinheiten, den Stationen und den Toren der Welten.
 *
 * <p><b>Ein LEERES Verzeichnis ist erlaubt und kein Mangel.</b> Wer Altfurt von Hand
 * gebaut hat oder gar kein Altfurt will, traegt nichts ein; dann setzt das Plugin nichts
 * und sagt das im Startbericht. Dieselbe Linie wie beim Rundgang: Ein Server ohne
 * Platzhalter ist nicht kaputt.
 */
public final class Bautenverzeichnis {

    /**
     * So viele Bauten darf Altfurt hoechstens haben.
     *
     * <p>Zwanzig, also das Doppelte der zehn aus Plan 2.1. Die Grenze ist keine
     * Rechengrenze, sondern eine ueber den Startlauf: Jeder Bau wird beim Start gesetzt,
     * wenn er fehlt, und fuenfzig Haeuser waeren ein Start, der Minuten braucht, ohne
     * dass jemand sieht, warum.
     */
    public static final int HOECHSTENS = 20;

    private final Map<String, Platzhalterbau> bauten;

    private Bautenverzeichnis(Map<String, Platzhalterbau> bauten) {
        this.bauten = bauten;
    }

    /**
     * Baut das Verzeichnis - der ERSTE Eintrag zu einem Namen gewinnt.
     *
     * <p>Ueberschneidende Quader werden hier NICHT abgewiesen, sondern beim Lesen: Dort
     * gibt es den Betreiber, dem man sagen kann, welche zwei Zeilen sich beissen. Hier
     * waere es eine Ausnahme mitten im Start.
     */
    public static Bautenverzeichnis ausBauten(List<Platzhalterbau> bauten) {
        if (bauten == null) {
            throw new IllegalArgumentException("Nichts ist kein Verzeichnis, sondern eine"
                    + " leere Liste");
        }
        if (bauten.size() > HOECHSTENS) {
            throw new IllegalArgumentException("Altfurt hat hoechstens " + HOECHSTENS
                    + " Platzhalterbauten, nicht " + bauten.size() + ".");
        }
        Map<String, Platzhalterbau> karte = new LinkedHashMap<>();
        for (Platzhalterbau bau : bauten) {
            if (bau == null) {
                throw new IllegalArgumentException("Ein Bau in der Liste ist null");
            }
            karte.putIfAbsent(bau.schluessel(), bau);
        }
        return new Bautenverzeichnis(Collections.unmodifiableMap(karte));
    }

    /** Das leere Verzeichnis - erlaubt, siehe Klassenkopf. */
    public static Bautenverzeichnis ohneBauten() {
        return ausBauten(List.of());
    }

    public int anzahl() {
        return bauten.size();
    }

    /** Ob gar nichts gesetzt wird. */
    public boolean leer() {
        return bauten.isEmpty();
    }

    /** Die Namen in der Reihenfolge der Datei. */
    public List<String> namen() {
        return List.copyOf(bauten.keySet());
    }

    /** Die Bauten in der Reihenfolge der Datei. */
    public List<Platzhalterbau> alle() {
        return List.copyOf(bauten.values());
    }

    /**
     * Der Bau mit diesem Namen - oder {@code null}.
     *
     * <p>Gibt {@code null} und wirft nicht: Der Name kann aus einer Station kommen, und
     * dort steht womoeglich ein Bau, den der Betreiber inzwischen aus der Datei genommen
     * hat. Das ist kein Fehler.
     */
    public Platzhalterbau mitNamen(String name) {
        if (name == null) {
            return null;
        }
        return bauten.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Die Namen, die mit diesem Anfang beginnen - fuer die Vervollstaendigung von
     * {@code /freimarken altfurt sichern <bau>}.
     *
     * <p>Dieselbe Stelle wie bei den Warps und den Berufen ({@link Namensvorschlag}).
     */
    public List<String> vorschlaege(String angefangen) {
        return Namensvorschlag.zu(namen(), angefangen);
    }

    /**
     * Wie viele Bloecke alle Bauten zusammen umfassen - Luft eingerechnet.
     *
     * <p>Die Zahl geht in den Startbericht. Sie ist die eine Angabe, an der ein Betreiber
     * sieht, dass sein naechster Start etwas zu tun hat.
     */
    public long bloecke() {
        long summe = 0L;
        for (Platzhalterbau bau : bauten.values()) {
            summe += bau.bloecke();
        }
        return summe;
    }
}
