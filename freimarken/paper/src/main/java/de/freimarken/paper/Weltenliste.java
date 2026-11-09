package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Grenzregel;
import de.freimarken.kern.Quader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
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
     * @param abschnitt    der Abschnitt {@code welten:}, darf {@code null} sein
     * @param rasterwelt   der Name der einen Welt, die das Parzellenraster tragen darf
     * @param ankunftswelt die Welt, in der die Tore stehen - dort landet auch, wer neu ist
     */
    static Ergebnis ausKonfiguration(ConfigurationSection abschnitt, String rasterwelt,
                                     String ankunftswelt) {
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
                        + " leer, flach - uebersprungen.");
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
            welten.add(new Weltangabe(name, umgebung, art,
                    startwert(eintrag, name, klagen), kante(eintrag, name, klagen),
                    eintrag.getString("sprung-bestaetigt", "").trim(),
                    spielmodus(eintrag, name, klagen),
                    tor(eintrag, name, klagen)));
        }
        pruefeTore(welten, ankunftswelt, klagen);
        pruefeStartwerte(welten, klagen);
        return new Ergebnis(welten, klagen);
    }

    /**
     * Der Spielmodus, in dem ein Spieler diese Welt betritt.
     *
     * <p>Vorgabe ist Ueberleben - die Tabelle in Plan Abschnitt 4 fuehrt alle Welten so,
     * ausser Altfurt. Minecraft kennt keinen Spielmodus je Welt; das Plugin setzt ihn beim
     * Betreten.
     */
    private static GameMode spielmodus(ConfigurationSection eintrag, String name,
                                       List<String> klagen) {
        String wort = eintrag.getString("spielmodus");
        if (wort == null) {
            return GameMode.SURVIVAL;
        }
        GameMode modus = switch (wort.trim().toLowerCase(Locale.ROOT)) {
            case "ueberleben", "überleben", "survival" -> GameMode.SURVIVAL;
            case "abenteuer", "adventure" -> GameMode.ADVENTURE;
            case "kreativ", "creative" -> GameMode.CREATIVE;
            case "zuschauer", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (modus == null) {
            klagen.add("welten." + name + ".spielmodus ist unbekannt (" + wort + "). Erlaubt"
                    + " sind ueberleben, abenteuer, kreativ, zuschauer - es gilt"
                    + " ueberleben.");
            return GameMode.SURVIVAL;
        }
        return modus;
    }

    /**
     * Das Tor, das zu dieser Welt fuehrt - oder {@code null}, wenn keines eingetragen ist.
     *
     * <p>Zwei Ecken, je drei ganze Zahlen:
     *
     * <pre>
     *   tor:
     *     von: [80, 72, 2]
     *     bis: [82, 74, 4]
     * </pre>
     *
     * <p>Ein halbes Tor gibt es nicht: Fehlt eine der beiden Ecken, wird geklagt und kein
     * Tor gebaut. Mit einer geratenen zweiten Ecke staende irgendwo in Altfurt ein Tor,
     * das niemand dort haben wollte - und es wuerde ausloesen.
     */
    private static Quader tor(ConfigurationSection eintrag, String name,
                              List<String> klagen) {
        if (!eintrag.contains("tor")) {
            return null;
        }
        ConfigurationSection abschnitt = eintrag.getConfigurationSection("tor");
        if (abschnitt == null) {
            klagen.add("welten." + name + ".tor ist kein Abschnitt - erwartet werden die"
                    + " Zeilen von und bis, je drei ganze Zahlen. Kein Tor.");
            return null;
        }
        int[] von = ecke(abschnitt, "welten." + name + ".tor.von", klagen);
        int[] bis = ecke(abschnitt, "welten." + name + ".tor.bis", klagen);
        if (von == null || bis == null) {
            return null;
        }
        return new Quader(von[0], von[1], von[2], bis[0], bis[1], bis[2]);
    }

    /** Eine Ecke als drei ganze Zahlen - oder {@code null} mit Klage. */
    private static int[] ecke(ConfigurationSection abschnitt, String pfad,
                              List<String> klagen) {
        String schluessel = pfad.substring(pfad.lastIndexOf('.') + 1);
        if (!abschnitt.contains(schluessel)) {
            klagen.add(pfad + " fehlt - ein halbes Tor gibt es nicht. Kein Tor.");
            return null;
        }
        List<?> werte = abschnitt.getList(schluessel);
        if (werte == null || werte.size() != 3) {
            klagen.add(pfad + " braucht genau drei Zahlen [x, y, z], nicht "
                    + abschnitt.get(schluessel) + ". Kein Tor.");
            return null;
        }
        int[] ecke = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(werte.get(i) instanceof Number zahl)
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())
                    || zahl instanceof Double || zahl instanceof Float) {
                klagen.add(pfad + " enthaelt keine ganze Zahl (" + werte.get(i)
                        + "). Kein Tor.");
                return null;
            }
            long wert = zahl.longValue();
            if (Math.abs(wert) > 30_000_000L) {
                // Paper stuerzt bei einem Teleport jenseits der Weltkoordinaten ab
                // (PaperMC/Paper#13186). Ein vertippter Quader reicht dafuer.
                klagen.add(pfad + " liegt jenseits der Weltkoordinaten (" + wert
                        + "). Kein Tor.");
                return null;
            }
            ecke[i] = (int) wert;
        }
        return ecke;
    }

    /**
     * Prueft die Tore gegeneinander und gegen die Ankunftswelt.
     *
     * <p>Erst hier und nicht im Schleifendurchlauf: Ein Tor ist nur im Verhaeltnis zu den
     * anderen und zur Ankunftswelt falsch, und die kann in der Datei weiter unten stehen.
     *
     * <p>Die Grenze wird gegen die EINGETRAGENE Kante um 0/0 geprueft. Was wirklich gilt,
     * weiss erst die geladene Welt - diese Pruefung faengt den Vertipper, die zweite
     * steht in {@code Reise} und faengt den Rest.
     */
    private static void pruefeTore(List<Weltangabe> welten, String ankunftswelt,
                                   List<String> klagen) {
        List<Weltangabe> mitTor = welten.stream().filter(Weltangabe::hatTor).toList();
        if (mitTor.isEmpty()) {
            return;
        }
        Weltangabe ankunft = welten.stream()
                .filter(w -> w.name().equals(ankunftswelt))
                .findFirst()
                .orElse(null);
        if (ankunft == null) {
            klagen.add("Es sind Tore eingetragen, aber die Ankunftswelt " + ankunftswelt
                    + " steht nicht in der Liste - die Tore haetten keinen Ort. Kein Tor"
                    + " wird gebaut.");
            welten.replaceAll(w -> w.hatTor() ? ohneTor(w) : w);
            return;
        }
        Chunkfeld feld = ankunft.hatGrenze()
                ? Chunkfeld.ausGrenze(0, 0, ankunft.kante())
                : null;
        Set<String> verworfen = new java.util.LinkedHashSet<>();
        for (Weltangabe welt : mitTor) {
            if (welt.name().equals(ankunftswelt)) {
                klagen.add("welten." + ankunftswelt + ".tor fuehrt in die Welt, in der es"
                        + " steht - ein Tor nach nebenan. Kein Tor.");
                verworfen.add(welt.name());
                continue;
            }
            if (feld != null && !welt.tor().liegtIn(feld)) {
                klagen.add("welten." + welt.name() + ".tor liegt bei "
                        + welt.tor().alsText() + " und damit ausserhalb der eingetragenen"
                        + " Grenze von " + ankunftswelt + " (Kante " + ankunft.kante()
                        + " um 0/0). Dort kaeme niemand hin. Kein Tor.");
                verworfen.add(welt.name());
            }
        }
        for (int i = 0; i < mitTor.size(); i++) {
            for (int j = i + 1; j < mitTor.size(); j++) {
                Weltangabe eine = mitTor.get(i);
                Weltangabe andere = mitTor.get(j);
                if (eine.tor().schneidet(andere.tor())) {
                    klagen.add("Die Tore welten." + eine.name() + ".tor und welten."
                            + andere.name() + ".tor haben Bloecke gemeinsam ("
                            + eine.tor().alsText() + " und " + andere.tor().alsText()
                            + "). Dann entscheidet die Reihenfolge der Eintraege, wohin ein"
                            + " Spieler reist. Beide Tore werden verworfen.");
                    verworfen.add(eine.name());
                    verworfen.add(andere.name());
                }
            }
        }
        if (!verworfen.isEmpty()) {
            welten.replaceAll(w -> verworfen.contains(w.name()) ? ohneTor(w) : w);
        }
    }

    private static Weltangabe ohneTor(Weltangabe welt) {
        return new Weltangabe(welt.name(), welt.umgebung(), welt.art(),
                welt.startwert(), welt.kante(), welt.sprungBestaetigt(),
                welt.spielmodus(), null);
    }

    /**
     * Der Startwert - oder {@code null}, wenn aus dem Eintrag keiner werden kann.
     *
     * <p>Roh gelesen und nicht mit {@code getLong}, aus demselben Grund wie bei
     * {@link #kante}: Jenes gibt bei allem, was keine Zahl ist, stillschweigend 0
     * zurueck - und 0 ist hier ein GUELTIGER Startwert. Ein Tippfehler waere damit
     * nicht "kein Startwert", sondern ein ganz bestimmtes Gelaende, und niemand
     * saehe es.
     *
     * <p><b>Nur ganze Zahlen, kein Text.</b> Minecraft nimmt im Spielmenue auch
     * Woerter als Startwert und rechnet sie selbst in eine Zahl um; Bukkits
     * {@code WorldCreator.seed} nimmt nur einen {@code long}. Diesen Umweg hier
     * nachzubauen waere eine Abschrift des Spiels - weicht sie eines Tages ab,
     * entsteht aus demselben Wort ein anderes Gelaende, und alle eingemessenen
     * Quader liegen daneben. Darum wird Text beklagt und nicht umgerechnet.
     *
     * <p>Die Welt bleibt in jedem Fall; nur die Wiederholbarkeit faellt weg - wie
     * bei der Kante nur die Grenze.
     */
    private static Long startwert(ConfigurationSection eintrag, String name,
                                  List<String> klagen) {
        Object roh = eintrag.get("startwert");
        if (roh == null) {
            return null;
        }
        String pfad = "welten." + name + ".startwert";
        if (!(roh instanceof Number zahl)) {
            klagen.add(pfad + " ist keine Zahl (" + roh + ") - verworfen, das"
                    + " Gelaende dieser Welt wird gewuerfelt und ist nicht mehr"
                    + " herzustellen. Minecraft nimmt im Menue auch Woerter; hier"
                    + " gehoert eine ganze Zahl hin.");
            return null;
        }
        double wert = zahl.doubleValue();
        // Am TYP und nicht am Nachkommarest - dieselbe Falle wie bei der Kante: YAML
        // macht aus 1.234 eine 1.234, und wer Tausender mit Punkt schreibt, bekommt
        // ein ganz anderes Gelaende als gemeint.
        if (zahl instanceof Double || zahl instanceof Float || wert != Math.floor(wert)) {
            klagen.add(pfad + " ist keine ganze Zahl (" + roh + ") - verworfen. Ein"
                    + " Punkt trennt in YAML keine Tausender, sondern die"
                    + " Nachkommastellen.");
            return null;
        }
        return zahl.longValue();
    }

    /**
     * Klagt ueber zwei Welten, die dasselbe Gelaende bekommen wuerden.
     *
     * <p>Gleicher Startwert UND gleiche Umgebung heisst in Minecraft: dasselbe
     * Gelaende, Block fuer Block. Hochwald und Kornmark waeren dann zwei Namen fuer
     * eine Landschaft - ein Spieler, der von einer in die andere reist, stuende in
     * derselben Gegend, und keine Zeile im Protokoll sagte warum.
     *
     * <p>Nur eine KLAGE und kein Verwerfen: Die Welten entstehen trotzdem, denn
     * zweimal dasselbe Gelaende ist kein Fehler, der etwas kaputt macht. Es ist nur
     * mit Sicherheit nicht gewollt.
     *
     * <p>Die Rasterwelt ist ausgenommen: Ihr Gelaende kommt aus dem
     * {@code MarktstadtGenerator} und nicht aus einem Startwert.
     */
    private static void pruefeStartwerte(List<Weltangabe> welten,
                                         List<String> klagen) {
        for (int i = 0; i < welten.size(); i++) {
            Weltangabe eine = welten.get(i);
            if (!eine.wiederholbar()) {
                continue;
            }
            for (int j = i + 1; j < welten.size(); j++) {
                Weltangabe andere = welten.get(j);
                if (andere.wiederholbar()
                        && eine.startwert().equals(andere.startwert())
                        && eine.umgebung() == andere.umgebung()) {
                    klagen.add("welten." + eine.name() + " und welten."
                            + andere.name() + " haben denselben Startwert ("
                            + eine.startwert() + ") und dieselbe Umgebung - sie"
                            + " bekommen damit DASSELBE Gelaende, Block fuer Block."
                            + " Beide entstehen trotzdem; gewollt ist das mit"
                            + " Sicherheit nicht.");
                }
            }
        }
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
            case "flach" -> Weltangabe.Art.FLACH;
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
