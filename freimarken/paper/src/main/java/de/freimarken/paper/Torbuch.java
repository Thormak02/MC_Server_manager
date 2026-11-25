package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Quader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Die Tore am Bundesmarkt - gelesen und GESCHRIEBEN.
 *
 * <p><b>Warum eine eigene Datei</b> [25.11.2026]: Die Quader standen bis dahin in der
 * {@code config.yml} bei {@code welten.<welt>.tor}, also am Eintrag der ZIELwelt. Das war
 * richtig, solange ein Tor nur von Hand entstand - eine neue Welt blieb damit „ein
 * Eintrag plus Generator". Mit {@code /portal setzen} schreibt das Plugin Tore selbst, und
 * dafuer muesste es die {@code config.yml} neu schreiben. Bukkits
 * {@code FileConfiguration.save} wirft jeden Kommentar weg, den es nicht selbst gesetzt
 * hat; die {@code config.yml} dieses Plugins besteht zu drei Vierteln aus Kommentar. Eine
 * Datei, die das Plugin allein verantwortet, kann es auch neu schreiben.
 *
 * <p><b>Die Schrift ist eine NAHT</b> ({@link #schrift}): Der Dateitext entsteht an EINER
 * Stelle, ohne Bukkit und ohne Datei - und darum kann eine Probe ihn zeichengleich gegen
 * die ausgelieferte Ressource halten. Das ist der Zeuge dafuer, dass ein
 * {@code /portal setzen} den Kopf der Datei nicht verliert; genau dieser Verlust ist der
 * Grund, aus dem {@code lobby_service.py} die Serverdateien zeilenweise behandelt und
 * nicht mit einem YAML-Schreiber.
 *
 * <p><b>Zwei Schritte, nicht einer</b>: {@link #lade} liest die Datei und klagt ueber
 * ihre FORM; {@link #pruefe} haelt die Tore gegen die Welten und gegen einander. Das muss
 * getrennt sein, weil die Reihenfolge im Start es verlangt: Die Datei wird angelegt und
 * gelesen, bevor eine Welt steht ({@code FreimarkenPlugin.onEnable}), und die Welten
 * kennt erst die {@code Weltenliste}. Lief es umgekehrt, pruefte die
 * {@code Bautenliste} gegen eine leere Torliste - und der naechste Start mauerte den
 * Durchgang zu.
 */
final class Torbuch {

    /** Der Dateiname im Ordner des Plugins. */
    static final String DATEI = "tore.yml";

    /** Der eine Abschnitt darin. */
    static final String ABSCHNITT = "tore";

    /**
     * Ein Tor samt seiner Notiz.
     *
     * <p>{@code wo} ist ein WERT und kein Kommentar, und das ist der ganze Grund, aus dem
     * es dieses Feld gibt: Ein Kommentar in der Datei waere nach dem ersten
     * {@code /portal setzen} weg, weil die Datei dann neu geschrieben wird. Was zu einem
     * Tor zu sagen ist - bei den ausgelieferten Toren die Nische, aus der es gerechnet
     * ist -, muss darum in einer Zeile stehen, die mitwandert.
     */
    record Tor(String wo, Quader quader) {

        Tor {
            if (quader == null) {
                throw new IllegalArgumentException("Ein Tor ohne Quader ist kein Tor.");
            }
            wo = wo == null ? "" : wo.trim();
        }
    }

    /** Was eine Pruefung gefunden hat - und welche Tore darum nicht gelten. */
    record Pruefung(List<String> klagen, Set<String> verworfen) {

        boolean sauber() {
            return klagen.isEmpty();
        }
    }

    private final File ort;

    /**
     * Die Tore in DATEIREIHENFOLGE.
     *
     * <p>Eine geordnete Karte und keine {@code HashMap}: Die Reihenfolge steht in der
     * Datei, und eine Datei, die bei jedem Schreiben anders sortiert ist, laesst sich
     * zwischen zwei Staenden nicht vergleichen. Ein neues Tor kommt hinten dazu.
     */
    private Map<String, Tor> tore = new LinkedHashMap<>();

    Torbuch(File ort) {
        this.ort = ort;
    }

    // --- Lesen ----------------------------------------------------------------

    /**
     * Liest die Datei - und klagt ueber ihre Form, nicht ueber ihren Inhalt.
     *
     * <p>Eine fehlende Datei ist kein Fehler und keine Klage: Dann gibt es keine Tore,
     * und man kommt mit {@code /warp} hin. Angelegt wird sie beim Start aus der
     * Ressource ({@code saveResource}), genau wie die Sprachdatei.
     */
    List<String> lade() {
        List<String> klagen = new ArrayList<>();
        Map<String, Tor> gelesen = new LinkedHashMap<>();
        if (ort == null || !ort.isFile()) {
            tore = gelesen;
            return klagen;
        }
        YamlConfiguration datei = YamlConfiguration.loadConfiguration(ort);
        ConfigurationSection abschnitt = datei.getConfigurationSection(ABSCHNITT);
        if (abschnitt == null) {
            if (!datei.getKeys(false).isEmpty()) {
                klagen.add(DATEI + " hat keinen Abschnitt " + ABSCHNITT + ":. Solange er"
                        + " fehlt, gibt es kein Tor - man kommt nur mit /warp hin.");
            }
            tore = gelesen;
            return klagen;
        }
        for (String welt : abschnitt.getKeys(false)) {
            ConfigurationSection eintrag = abschnitt.getConfigurationSection(welt);
            if (eintrag == null) {
                klagen.add(ABSCHNITT + "." + welt + " ist kein Abschnitt - erwartet"
                        + " werden die Zeilen von und bis, je drei ganze Zahlen. Kein"
                        + " Tor.");
                continue;
            }
            int[] von = ecke(eintrag, ABSCHNITT + "." + welt + ".von", klagen);
            int[] bis = ecke(eintrag, ABSCHNITT + "." + welt + ".bis", klagen);
            if (von == null || bis == null) {
                continue;
            }
            gelesen.put(welt, new Tor(eintrag.getString("wo", ""),
                    new Quader(von[0], von[1], von[2], bis[0], bis[1], bis[2])));
        }
        tore = gelesen;
        return klagen;
    }

    /**
     * Eine Ecke als drei ganze Zahlen - oder {@code null} mit Klage.
     *
     * <p>Wortgleich uebernommen aus {@code Weltenliste.ecke} [25.11.2026], samt der
     * Grenze bei 30 Millionen: Paper stuerzt bei einem Teleport jenseits der
     * Weltkoordinaten ab (PaperMC/Paper#13186), und ein vertippter Quader reicht dafuer.
     * Roh gelesen und nicht mit {@code getInt}: Jenes gibt bei allem, was keine Zahl
     * ist, stillschweigend 0 - und 0 ist eine gueltige Koordinate.
     */
    private static int[] ecke(ConfigurationSection eintrag, String pfad,
                              List<String> klagen) {
        String schluessel = pfad.substring(pfad.lastIndexOf('.') + 1);
        if (!eintrag.contains(schluessel)) {
            klagen.add(pfad + " fehlt - ein halbes Tor gibt es nicht. Kein Tor.");
            return null;
        }
        List<?> werte = eintrag.getList(schluessel);
        if (werte == null || werte.size() != 3) {
            klagen.add(pfad + " braucht genau drei Zahlen [x, y, z], nicht "
                    + eintrag.get(schluessel) + ". Kein Tor.");
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
                klagen.add(pfad + " liegt jenseits der Weltkoordinaten (" + wert
                        + "). Kein Tor.");
                return null;
            }
            ecke[i] = (int) wert;
        }
        return ecke;
    }

    // --- Pruefen --------------------------------------------------------------

    /**
     * Haelt die Tore gegen die Welten und gegen einander.
     *
     * <p>Vier Faelle, und jeder verwirft nur das BETROFFENE Tor - ausser dem ersten:
     * Ohne Ankunftswelt hat kein Tor einen Ort.
     *
     * <p>Diese Methode steht hier und nicht im Plugin, weil {@code /portal setzen}
     * dieselbe Pruefung VORHER laufen laesst: Was der Start verwerfen wuerde, soll gar
     * nicht in die Datei kommen. Zwei getrennte Pruefungen waeren zwei Wahrheiten, und
     * die eine davon wuerde beim naechsten Umbau vergessen.
     *
     * @param ankunftswelt    wo die Tore stehen, etwa {@code altfurt}
     * @param bekannteWelten  alle Welten aus {@code welten:} - ein Tor in eine Welt, die
     *                        es nicht gibt, fuehrt nirgendwohin
     * @param kanteAnkunft    die eingetragene Kante der Ankunftswelt, oder {@code null}
     *                        fuer „keine Grenze eingetragen"
     */
    static Pruefung pruefe(Map<String, Tor> tore, String ankunftswelt,
                           Set<String> bekannteWelten, Integer kanteAnkunft) {
        List<String> klagen = new ArrayList<>();
        Set<String> verworfen = new LinkedHashSet<>();
        if (tore == null || tore.isEmpty()) {
            return new Pruefung(klagen, verworfen);
        }
        if (ankunftswelt == null || ankunftswelt.isBlank()) {
            klagen.add("In " + DATEI + " stehen " + tore.size() + " Tore, aber"
                    + " reisen.ankunftswelt ist leer - die Tore haetten keinen Ort. Kein"
                    + " Tor wird gebaut.");
            verworfen.addAll(tore.keySet());
            return new Pruefung(klagen, verworfen);
        }
        if (bekannteWelten != null && !bekannteWelten.contains(ankunftswelt)) {
            klagen.add("In " + DATEI + " stehen " + tore.size() + " Tore, aber die"
                    + " Ankunftswelt " + ankunftswelt + " steht nicht in welten: - die"
                    + " Tore haetten keinen Ort. Kein Tor wird gebaut.");
            verworfen.addAll(tore.keySet());
            return new Pruefung(klagen, verworfen);
        }
        Chunkfeld feld = kanteAnkunft != null && kanteAnkunft > 0
                ? Chunkfeld.ausGrenze(0, 0, kanteAnkunft)
                : null;
        for (Map.Entry<String, Tor> eintrag : tore.entrySet()) {
            String welt = eintrag.getKey();
            Quader quader = eintrag.getValue().quader();
            if (bekannteWelten != null && !bekannteWelten.contains(welt)) {
                klagen.add(ABSCHNITT + "." + welt + " fuehrt in eine Welt, die es nicht"
                        + " gibt - unter welten: steht kein Eintrag " + welt + ". Das Tor"
                        + " wuerde ausloesen und nirgendwohin fuehren. Kein Tor.");
                verworfen.add(welt);
                continue;
            }
            if (welt.equals(ankunftswelt)) {
                klagen.add(ABSCHNITT + "." + welt + " fuehrt in die Welt, in der es steht"
                        + " - ein Tor nach nebenan. Kein Tor.");
                verworfen.add(welt);
                continue;
            }
            if (feld != null && !quader.liegtIn(feld)) {
                klagen.add(ABSCHNITT + "." + welt + " liegt bei " + quader.alsText()
                        + " und damit ausserhalb der eingetragenen Grenze von "
                        + ankunftswelt + " (Kante " + kanteAnkunft + " um 0/0). Dort"
                        + " kaeme niemand hin. Kein Tor.");
                verworfen.add(welt);
            }
        }
        List<String> namen = new ArrayList<>(tore.keySet());
        for (int i = 0; i < namen.size(); i++) {
            for (int j = i + 1; j < namen.size(); j++) {
                Quader eine = tore.get(namen.get(i)).quader();
                Quader andere = tore.get(namen.get(j)).quader();
                if (eine.schneidet(andere)) {
                    klagen.add("Die Tore " + ABSCHNITT + "." + namen.get(i) + " und "
                            + ABSCHNITT + "." + namen.get(j) + " haben Bloecke gemeinsam"
                            + " (" + eine.alsText() + " und " + andere.alsText() + ")."
                            + " Dann entscheidet die Reihenfolge der Eintraege, wohin ein"
                            + " Spieler reist. Beide Tore werden verworfen.");
                    verworfen.add(namen.get(i));
                    verworfen.add(namen.get(j));
                }
            }
        }
        return new Pruefung(klagen, verworfen);
    }

    /**
     * Prueft die gelesenen Tore und WIRFT die verworfenen heraus.
     *
     * <p>Herausgeworfen und nicht bloss beklagt: Ein Tor, das der Start verwirft, darf
     * nicht in der Torwache stehen - sonst klagt das Protokoll und der Durchgang wirkt
     * trotzdem.
     */
    List<String> pruefeUndVerwirf(String ankunftswelt, Set<String> bekannteWelten,
                                  Integer kanteAnkunft) {
        Pruefung pruefung = pruefe(tore, ankunftswelt, bekannteWelten, kanteAnkunft);
        if (!pruefung.verworfen().isEmpty()) {
            Map<String, Tor> bleibt = new LinkedHashMap<>();
            tore.forEach((welt, tor) -> {
                if (!pruefung.verworfen().contains(welt)) {
                    bleibt.put(welt, tor);
                }
            });
            tore = bleibt;
        }
        return pruefung.klagen();
    }

    // --- Fragen ---------------------------------------------------------------

    /** Der Quader, der in diese Welt fuehrt - oder {@code null}. */
    Quader tor(String welt) {
        Tor tor = tore.get(welt);
        return tor == null ? null : tor.quader();
    }

    /**
     * Alle Tore in Dateireihenfolge - unveraenderlich.
     *
     * <p><b>Nicht {@code Map.copyOf}</b>, und das ist gemessen [25.11.2026]:
     * Jenes gibt eine Karte mit UNBESTIMMTER Reihenfolge zurueck. Der Dateitext
     * aus {@link #schrift} stand damit in einer anderen Reihenfolge da als die
     * Datei, aus der er kam - acht Tore, acht Mal derselbe Inhalt, und doch nicht
     * dieselbe Datei. Bukkits Leser erhaelt die Reihenfolge; diese Zeile hat sie
     * wieder weggeworfen.
     */
    Map<String, Tor> alle() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(tore));
    }

    /** Die Namen in Dateireihenfolge - fuer Anzeige und Ergaenzung. */
    List<String> namen() {
        return List.copyOf(tore.keySet());
    }

    /** Alle Quader - fuer die Bautenliste, die sie gegen die Nischen haelt. */
    List<Quader> quader() {
        List<Quader> alle = new ArrayList<>();
        tore.values().forEach(tor -> alle.add(tor.quader()));
        return alle;
    }

    boolean hatTore() {
        return !tore.isEmpty();
    }

    int anzahl() {
        return tore.size();
    }

    // --- Schreiben ------------------------------------------------------------

    /**
     * Setzt ein Tor - aber nur, wenn der Start es nicht verwerfen wuerde.
     *
     * <p>Die Pruefung laeuft auf dem GEDACHTEN Stand, nicht auf dem jetzigen: So faellt
     * ein Tor auf, das sich mit einem vorhandenen schneidet, bevor es in der Datei
     * steht. Findet sie etwas, aendert sich nichts - weder im Speicher noch auf der
     * Platte.
     *
     * @return die Klagen; leer heisst: gesetzt und geschrieben
     */
    List<String> setze(String welt, Tor neu, String ankunftswelt,
                       Set<String> bekannteWelten, Integer kanteAnkunft)
            throws IOException {
        Map<String, Tor> gedacht = new LinkedHashMap<>(tore);
        gedacht.put(welt, neu);
        Pruefung pruefung = pruefe(gedacht, ankunftswelt, bekannteWelten, kanteAnkunft);
        if (pruefung.verworfen().contains(welt)) {
            return pruefung.klagen();
        }
        tore = gedacht;
        schreibe();
        return List.of();
    }

    /** Nimmt ein Tor heraus und schreibt die Datei - {@code false}, wenn keines da war. */
    boolean entferne(String welt) throws IOException {
        if (!tore.containsKey(welt)) {
            return false;
        }
        Map<String, Tor> bleibt = new LinkedHashMap<>(tore);
        bleibt.remove(welt);
        tore = bleibt;
        schreibe();
        return true;
    }

    /**
     * Schreibt die Datei - ueber eine Nebendatei und in EINEM Zug.
     *
     * <p>Erst vollstaendig daneben schreiben, dann umbenennen: Bricht der Server mitten
     * im Schreiben ab, liegt entweder der alte oder der neue Stand da, nie ein halber.
     * Eine halbe {@code tore.yml} waere beim naechsten Start eine Datei ohne Abschnitt -
     * und damit ein Bundesmarkt ohne Tore.
     */
    void schreibe() throws IOException {
        File neben = new File(ort.getParentFile(), DATEI + ".neu");
        Files.writeString(neben.toPath(), schrift(tore), StandardCharsets.UTF_8);
        try {
            Files.move(neben.toPath(), ort.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ohneAtom) {
            // Auf einer Netzwerkfreigabe gibt es den atomaren Zug womoeglich nicht.
            // Dann ist ein gewoehnliches Ersetzen besser als keine Datei.
            Files.move(neben.toPath(), ort.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Der ganze Dateitext - die NAHT.
     *
     * <p>Von Hand gebaut und nicht mit {@code YamlConfiguration.save}: Jenes wirft den
     * Kopf weg, und der Kopf ist hier die Anleitung. Zugleich ist dieser Text damit
     * ohne Datei und ohne Server pruefbar - {@code TorbuchTest} haelt ihn zeichengleich
     * gegen die ausgelieferte Ressource. Diese eine Zusage ist der Nachweis, dass ein
     * {@code /portal setzen} die Datei nicht verstuemmelt.
     */
    static String schrift(Map<String, Tor> tore) {
        StringBuilder text = new StringBuilder(KOPF);
        text.append(ABSCHNITT).append(":").append(NL);
        tore.forEach((welt, tor) -> {
            text.append("  ").append(welt).append(":").append(NL);
            text.append("    wo: \"").append(tor.wo().replace("\"", "'")).append("\"")
                    .append(NL);
            text.append("    von: [").append(tor.quader().vonX()).append(", ")
                    .append(tor.quader().vonY()).append(", ")
                    .append(tor.quader().vonZ()).append("]").append(NL);
            text.append("    bis: [").append(tor.quader().bisX()).append(", ")
                    .append(tor.quader().bisY()).append(", ")
                    .append(tor.quader().bisZ()).append("]").append(NL);
        });
        return text.toString();
    }

    /** Zeilenende als Konstante: Die Datei soll auf jeder Maschine gleich aussehen. */
    private static final String NL = "\n";

    /**
     * Der Kopf der Datei - zeichengleich mit der ausgelieferten Ressource.
     *
     * <p>Im Quelltext und nicht aus der Ressource gelesen: Geschrieben wird auch dann,
     * wenn die Ressource im Jar nicht erreichbar ist, und ein Kopf, der beim Schreiben
     * fehlen KANN, ist kein Kopf. Dass die zwei nicht auseinanderlaufen, haelt
     * {@code TorbuchTest}.
     */
    private static final String KOPF =
            "# ============================================================================" + NL
            + "# DIE TORE AM BUNDESMARKT" + NL
            + "# ============================================================================" + NL
            + "# Jedes Tor ist ein Quader in der Ankunftswelt (reisen.ankunftswelt, also" + NL
            + "# Altfurt). Wer hineintritt, reist in die Welt, deren Namen der Eintrag traegt." + NL
            + "#" + NL
            + "# DIESE DATEI SCHREIBT DAS PLUGIN. /portal setzen <welt> und" + NL
            + "# /portal entfernen <welt> aendern sie im Spiel; dabei wird sie vollstaendig" + NL
            + "# neu geschrieben. Dieser Kopf und die Zeilen wo: bleiben dabei erhalten -" + NL
            + "# jeder ANDERE Kommentar, den du hier eintraegst, ist nach dem naechsten" + NL
            + "# /portal setzen weg. Deshalb ist wo: ein Wert und kein Kommentar: Was zu" + NL
            + "# einem Tor zu sagen ist, gehoert in diese Zeile." + NL
            + "#" + NL
            + "# Von Hand aendern geht weiter. Die Datei wird beim Start gelesen, und" + NL
            + "# /portal neuladen liest sie erneut, ohne den Server zu stoppen." + NL
            + "#" + NL
            + "# WARUM DIE TORE NICHT MEHR IN DER config.yml STEHEN [25.11.2026]: Sie standen" + NL
            + "# bis dahin bei welten.<welt>.tor, also an der Zielwelt. Das war richtig," + NL
            + "# solange ein Tor nur von Hand eingetragen wurde - eine neue Welt blieb \"ein" + NL
            + "# Eintrag plus Generator\". Sobald das Plugin selbst Tore setzt, muesste es die" + NL
            + "# config.yml neu schreiben, und dabei gingen deren 4000 Zeilen Kommentar" + NL
            + "# verloren. Eine eigene Datei kann das Plugin verantworten." + NL
            + "#" + NL
            + "# WO DIE ACHT AUSGELIEFERTEN TORE STEHEN: Es sind die Nischen der vier" + NL
            + "# Torhaeuser, je zwei - sie stehen in der config.yml unter bauten.torhaeuser," + NL
            + "# und Torhausplan rechnet daraus die Quader. Altfurt ist art: flach, der Boden" + NL
            + "# also ueberall auf y -61 und der erste freie Block auf y -60. Darum ist jeder" + NL
            + "# Torquader drei Bloecke hoch von y -60 bis -58." + NL
            + "#" + NL
            + "# DAS MAUERWERK IST VON HAND ZU BAUEN (Plan 2.1 nennt die Gebaeude). Bis dahin" + NL
            + "# ist ein Tor eine unsichtbare Tuer; das Protokoll sagt beim Start, durch welche" + NL
            + "# Tore niemand gehen kann - im Berg, in der Luft oder im Wasser." + NL
            + "#" + NL
            + "# ZU JEDEM EINTRAG:" + NL
            + "#   wo:   eine Notiz, die das Plugin nicht auswertet - bei den ausgelieferten" + NL
            + "#         Toren ist es die Nische, aus der der Quader gerechnet ist. Die" + NL
            + "#         Nischen selbst stehen in der config.yml unter bauten.torhaeuser." + NL
            + "#   von:  die eine Ecke, drei ganze Zahlen [x, y, z], eingeschlossen" + NL
            + "#   bis:  die andere Ecke, ebenso eingeschlossen" + NL
            + "#" + NL
            + "# WAS DER START PRUEFT - und bei einem Fund das BETROFFENE Tor verwirft, nicht" + NL
            + "# alle:" + NL
            + "#   1. Die Welt muss es geben (welten: in der config.yml)." + NL
            + "#   2. Kein Tor fuehrt in die Welt, in der es steht - das waere ein Tor nach" + NL
            + "#      nebenan." + NL
            + "#   3. Kein Tor liegt ausserhalb der eingetragenen Grenze der Ankunftswelt." + NL
            + "#      Dort kaeme niemand hin." + NL
            + "#   4. Keine zwei Tore teilen sich Bloecke. Sonst entschiede die Reihenfolge" + NL
            + "#      der Eintraege, wohin ein Spieler reist." + NL
            + "# /portal setzen prueft dasselbe VORHER und setzt nichts, was der Start" + NL
            + "# verwerfen wuerde." + NL
            + "# ============================================================================" + NL
            + NL;
}
