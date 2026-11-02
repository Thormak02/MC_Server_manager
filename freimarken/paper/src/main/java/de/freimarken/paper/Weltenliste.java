package de.freimarken.paper;

import de.freimarken.kern.Grenzregel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code welten:} und macht Angaben daraus.
 *
 * <p>Getrennt vom Anlegen der Welten, weil nur das Lesen ohne Server prueffbar ist - und
 * weil hier die Fehler passieren, die am teuersten sind. Ein Tippfehler in der Art einer
 * Welt darf nicht dazu fuehren, dass sie still mit gewoehnlichem Gelaende entsteht: Ueber
 * einer bebauten Marktstadt waere das nicht zurueckzunehmen.
 *
 * <p>Deshalb gibt diese Klasse neben den Angaben eine Liste von KLAGEN zurueck, statt
 * Fehler zu verschlucken oder zu werfen. Wer klagt, wird uebersprungen und laut benannt;
 * die uebrigen Welten entstehen trotzdem. Eine Ausnahme mitten im Serverstart haette
 * dagegen alle mitgerissen.
 */
final class Weltenliste {

    /** Was im Abschnitt stand - und woran es gehapert hat. */
    record Ergebnis(List<Weltangabe> welten, List<String> klagen) {
    }

    private Weltenliste() {
    }

    /**
     * @param abschnitt  der Abschnitt {@code welten:}, darf {@code null} sein
     * @param rasterwelt der Name der einen Welt, die das Parzellenraster tragen darf
     */
    static Ergebnis ausKonfiguration(ConfigurationSection abschnitt, String rasterwelt) {
        List<Weltangabe> welten = new ArrayList<>();
        List<String> klagen = new ArrayList<>();
        if (abschnitt == null) {
            klagen.add("Es gibt keinen Abschnitt welten: in der config.yml.");
            return new Ergebnis(welten, klagen);
        }
        nenneFehlende(abschnitt, klagen);
        for (String name : abschnitt.getKeys(false)) {
            ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
            if (eintrag == null) {
                klagen.add("welten." + name + " ist kein Abschnitt - uebersprungen.");
                continue;
            }
            if (!gueltigerName(name)) {
                klagen.add("welten." + name + ": Ein Weltname darf nur Kleinbuchstaben,"
                        + " Ziffern, Unterstrich, Punkt und Bindestrich enthalten"
                        + " - uebersprungen.");
                continue;
            }
            Weltangabe.Art art = art(eintrag.getString("art"));
            if (art == null) {
                // Keine Vorgabe: Eine fehlende Art als "gewoehnlich" zu lesen wuerde aus
                // der Marktstadt beim naechsten Start eine Welt mit Huegeln und Baeumen
                // machen, mitten in den Parzellen. Lieber laut aussteigen.
                klagen.add("welten." + name + ".art fehlt oder ist unbekannt ("
                        + eintrag.getString("art") + "). Erlaubt sind raster, gewoehnlich,"
                        + " leer - uebersprungen.");
                continue;
            }
            if (art == Weltangabe.Art.RASTER != name.equals(rasterwelt)) {
                // Die Art haengt am Namen, und zwar in beide Richtungen. Steht beim
                // Raster etwas anderes, entstuende die Marktstadt als gewoehnliche Welt
                // mit Huegeln und Baeumen mitten in den bezahlten Parzellen - und zwar
                // bevor eine Pruefung sie wieder loswerden koennte. Traegt eine zweite
                // Welt raster, bekaeme sie Mauern und Wege, aber kein Besitzbuch und
                // keine Schutzwache: eine Parzellenstadt, in der jeder ueberall baut.
                klagen.add(name.equals(rasterwelt)
                        ? "welten." + name + ".art muss raster sein (steht: "
                                + eintrag.getString("art") + ") - uebersprungen. Die"
                                + " Marktstadt IST das Parzellenraster; ohne sie startet"
                                + " Freimarken gar nicht, und das ist hier das Richtige:"
                                + " So wird sie nicht als falsche Welt angelegt."
                        : "welten." + name + ".art ist raster, aber das Raster traegt"
                                + " allein welten." + rasterwelt + " - uebersprungen."
                                + " Eine zweite Rasterwelt haette Mauern und Wege, aber"
                                + " keinen Besitz und keinen Schutz.");
                continue;
            }
            World.Environment umgebung = umgebung(eintrag.getString("umgebung", "normal"));
            if (umgebung == null) {
                klagen.add("welten." + name + ".umgebung ist unbekannt ("
                        + eintrag.getString("umgebung") + "). Erlaubt sind normal, nether,"
                        + " ende - uebersprungen.");
                continue;
            }
            welten.add(new Weltangabe(name, umgebung, art, kante(eintrag, name, klagen),
                    eintrag.getString("sprung-bestaetigt", "").trim()));
        }
        return new Ergebnis(welten, klagen);
    }

    /**
     * Die Wunschkante - oder 0, wenn aus dem Eintrag keine werden kann.
     *
     * <p>Roh gelesen und nicht mit {@code getInt}: Das gibt bei allem, was keine Zahl
     * ist, stillschweigend 0 zurueck und kuerzt eine zu grosse Zahl auf etwas
     * Unkenntliches. {@code kante: "6000"} waere dann eine Welt ohne Grenze, in der
     * Spieler beliebig weit hinauslaufen und endlos Gelaende erzeugen - ohne eine Zeile
     * im Protokoll. Und {@code kante: 6.000}, wie die Kommentare dieser Datei selbst
     * Tausender schreiben, liest YAML als 6.
     *
     * <p>Die Welt bleibt in jedem Fall; nur die Grenze faellt weg. Eine Welt wegen eines
     * Tippfehlers in einer Zahl gar nicht anzulegen, naehme den Spielern mehr, als der
     * Tippfehler wert ist.
     */
    private static int kante(ConfigurationSection eintrag, String name,
                             List<String> klagen) {
        Object roh = eintrag.get("kante");
        if (roh == null) {
            return 0;
        }
        String pfad = "welten." + name + ".kante";
        if (!(roh instanceof Number zahl)) {
            klagen.add(pfad + " ist keine Zahl (" + roh + ") - verworfen, die Welt"
                    + " entsteht ohne Grenze aus dieser Zeile. Anfuehrungszeichen machen"
                    + " aus einer Zahl Text.");
            return 0;
        }
        double wert = zahl.doubleValue();
        // Am TYP und nicht am Nachkommarest: YAML macht aus 6.000 eine 6.0, und die ist
        // ganzzahlig. Gemeint war aber 6000 - die Kommentare dieser Datei schreiben
        // Tausender selbst mit Punkt. Eine Fliesskommazahl entsteht in YAML nur dort, wo
        // ein Punkt oder ein Exponent steht; als Kante war sie nie gemeint.
        if (zahl instanceof Double || zahl instanceof Float || wert != Math.floor(wert)) {
            klagen.add(pfad + " ist keine ganze Zahl (" + roh + ") - verworfen. Ein Punkt"
                    + " trennt in YAML keine Tausender, sondern die Nachkommastellen:"
                    + " aus 6.000 wird 6.");
            return 0;
        }
        if (wert == 0.0) {
            return 0;
        }
        if (!Grenzregel.brauchbar((long) wert)) {
            klagen.add(pfad + " ist " + (long) wert + " - verworfen, die Welt entsteht"
                    + " ohne Grenze. Erlaubt sind " + Grenzregel.MINDESTKANTE + " bis "
                    + (long) Grenzregel.HOECHSTMASS + ": weniger waere kein Rand, sondern"
                    + " ein Kaefig, mehr nimmt Minecraft nicht an.");
            return 0;
        }
        return (int) wert;
    }

    /**
     * Klagt ueber jede Welt, die das Plugin mitbringt, aber in der Datei des Betreibers
     * nicht steht.
     *
     * <p>{@code saveDefaultConfig()} schreibt eine vorhandene Datei nie um. Auf einem
     * Server, der schon lief, fehlen neue Eintraege also - und {@code getKeys} mischt die
     * Vorgaben aus der Jar nicht ein, {@code getString} fuer einen einzelnen Schluessel
     * aber doch. Ohne diese Klage heisst das: Die Marktstadt entsteht, weil ihre Art
     * stumm aus der Jar kommt, die sieben anderen Welten entstehen nicht, und im
     * Protokoll steht kein Wort darueber - ein unauffaelliger Start, in dem sieben
     * Welten fehlen.
     */
    private static void nenneFehlende(ConfigurationSection abschnitt, List<String> klagen) {
        ConfigurationSection vorgabe = abschnitt.getDefaultSection();
        if (vorgabe == null) {
            return;
        }
        Set<String> vorhanden = abschnitt.getKeys(false);
        for (String name : vorgabe.getKeys(false)) {
            if (!vorhanden.contains(name)) {
                klagen.add("welten." + name + " bringt das Plugin mit, aber in deiner"
                        + " config.yml steht der Eintrag nicht - diese Welt entsteht"
                        + " NICHT. Eine vorhandene Datei wird beim Update nie"
                        + " ueberschrieben; der Block muss von Hand uebernommen werden.");
            }
        }
    }

    /**
     * Die Zeichen, die ein {@code NamespacedKey} annimmt.
     *
     * <p>Hier geprueft und nicht erst beim Anlegen: Bukkit wirft dort, und zwar mitten im
     * Start, nachdem andere Welten schon stehen.
     */
    private static boolean gueltigerName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean erlaubt = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-';
            if (!erlaubt) {
                return false;
            }
        }
        return true;
    }

    private static Weltangabe.Art art(String wort) {
        if (wort == null) {
            return null;
        }
        return switch (wort.trim().toLowerCase(Locale.ROOT)) {
            case "raster" -> Weltangabe.Art.RASTER;
            case "gewoehnlich", "gewöhnlich" -> Weltangabe.Art.GEWOEHNLICH;
            case "leer" -> Weltangabe.Art.LEER;
            default -> null;
        };
    }

    private static World.Environment umgebung(String wort) {
        if (wort == null) {
            return null;
        }
        return switch (wort.trim().toLowerCase(Locale.ROOT)) {
            case "normal", "oberwelt" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "ende", "end" -> World.Environment.THE_END;
            default -> null;
        };
    }
}
