package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rundgangsstation;
import de.freimarken.kern.Rundgangsverzeichnis;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Stationen des Rundgangs aus der {@code config.yml} - Station fuer Station.
 *
 * <p>Nach demselben Muster wie {@link Ladenliste}, und zwar Zeile fuer Zeile: reine
 * Dateiarbeit, gesammelte KLAGEN statt Ausnahmen, ein kaputter Eintrag faellt GANZ weg,
 * und am Ende werden Ueberschneidungen und Bereiche hinter der Weltgrenze verworfen. Ein
 * Tippfehler in einer Koordinate darf keinen Serverstart mitreissen; er darf aber auch
 * nicht still gelten.
 *
 * <p><b>Ab Werk steht hier NICHTS</b>, und das ist kein Mangel: Die Gebaeude in Altfurt
 * sind Handarbeit (Baureihenfolge Schritt 3, "offen ist nur Handarbeit"), und ein Bereich
 * auf einem Platz, auf dem nichts steht, waere eine Station, die niemand findet. Eine
 * leere Liste heisst ueberall dasselbe: kein Rundgang. Die Wache wird dann nicht
 * angemeldet, und eine Rangbedingung darauf ist nicht erfuellbar.
 *
 * <p><b>Ein kaputter Eintrag faellt GANZ weg</b> - wie eine Ladeneinheit, eine Rangstufe
 * und ein Katalogposten, und aus demselben Grund: Eine Station ohne Bereich ist keine
 * Station, und eine mit halben Koordinaten laege irgendwo. Die uebrigen gelten weiter;
 * der Rundgang ist dann kuerzer, und die Klage sagt, welche Zeile fehlt.
 *
 * <p><b>Ueberschneidende Stationen werden BEIDE verworfen</b>, wie bei den Ladeneinheiten
 * und den Toren: Welche zaehlt, entschiede sonst die Reihenfolge der Datei - und beide
 * Eintraege sehen fuer sich richtig aus. Ein Spieler bekaeme je nach Sortierung eine
 * andere Station gezaehlt, und {@link Rundgangsverzeichnis#anDemOrt} darf genau das nicht.
 */
final class Rundgangsliste {

    /** Die Welt, in der der Rundgang liegt. */
    static final String PFAD_WELT = "rundgang.welt";

    /** Der Block mit den Stationen. */
    static final String PFAD_STATIONEN = "rundgang.stationen";

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(String welt, Rundgangsverzeichnis verzeichnis, List<String> klagen,
                    List<String> bericht) {
    }

    private Rundgangsliste() {
    }

    /**
     * Der ganze Abschnitt {@code rundgang:}.
     *
     * @param wurzel       die ganze Konfiguration, darf {@code null} sein
     * @param kantenJeWelt die Kanten aus {@code welten:} - gebraucht, um einen Bereich
     *                     hinter der Weltgrenze zu verwerfen. Hereingegeben und nicht
     *                     hier geholt, damit dieser Leser EINEN Abschnitt liest; ein
     *                     zweites Lesen von {@code welten:} waere eine zweite Wahrheit
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
            Map<String, Integer> kantenJeWelt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis("", Rundgangsverzeichnis.ohneStationen(), klagen,
                    bericht);
        }
        String welt = welt(wurzel);
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD_STATIONEN);
        if (abschnitt == null || abschnitt.getKeys(false).isEmpty()) {
            // DER AUSGELIEFERTE ZUSTAND, und er bekommt eine Berichtzeile und keine
            // Klage: Ein Server, dessen Altfurt noch nicht steht, ist nicht kaputt.
            bericht.add("Kein Rundgang: " + PFAD_STATIONEN + " ist leer. Sobald die"
                    + " Gebaeude in Altfurt stehen, traegst du je Station zwei Ecken ein"
                    + " - dann fuehrt der Rundgang durch sie, und eine Rangstufe kann ihn"
                    + " verlangen.");
            return new Ergebnis(welt, Rundgangsverzeichnis.ohneStationen(), klagen,
                    bericht);
        }

        List<Rundgangsstation> stationen = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Rundgangsstation station = station(abschnitt, name, klagen);
            if (station != null) {
                stationen.add(station);
            }
        }
        verwerfeUeberschneidungen(stationen, klagen);
        verwerfeAusserhalb(stationen, welt, kantenJeWelt, klagen);
        try {
            Rundgangsverzeichnis verzeichnis =
                    Rundgangsverzeichnis.ausStationen(stationen);
            bericht.add(rechenprobe(welt, verzeichnis));
            return new Ergebnis(welt, verzeichnis, klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Der Leser wirft NIE, das Verzeichnis sehr wohl - darum dieses try/catch.
            // Hierher kommt, was die Zeile fuer Zeile gelesenen Stationen ZUSAMMEN
            // verletzen koennen: zu viele.
            klagen.add(PFAD_STATIONEN + ": " + unbrauchbar.getMessage()
                    + " Es gilt kein Rundgang.");
            return new Ergebnis(welt, Rundgangsverzeichnis.ohneStationen(), klagen,
                    bericht);
        }
    }

    /** Die Welt des Rundgangs - ohne Eintrag die Ladenwelt, denn dort liegt Altfurt. */
    private static String welt(ConfigurationSection wurzel) {
        String gelesen = wurzel.getString(PFAD_WELT);
        if (gelesen == null || gelesen.isBlank()) {
            // KEINE Klage: Ohne Stationen braucht es keine Welt, und mit Stationen
            // klagt verwerfeAusserhalb ueber die fehlende Kante. Zwei Klagen fuer
            // dieselbe fehlende Zeile waeren eine zu viel.
            return "";
        }
        return gelesen.trim().toLowerCase(Locale.ROOT);
    }

    /** EINE Station aus ihrem Block - oder {@code null}, wenn die Zeilen nicht taugen. */
    private static Rundgangsstation station(ConfigurationSection abschnitt, String name,
            List<String> klagen) {
        String pfad = PFAD_STATIONEN + "." + name;
        ConfigurationSection zeilen = abschnitt.getConfigurationSection(name);
        if (zeilen == null) {
            klagen.add(pfad + " ist kein Block mit von und bis ("
                    + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + ") - uebersprungen.");
            return null;
        }
        int[] von = drei(zeilen, pfad, "von", klagen);
        int[] bis = drei(zeilen, pfad, "bis", klagen);
        if (von == null || bis == null) {
            return null;
        }
        try {
            return new Rundgangsstation(name,
                    new Quader(von[0], von[1], von[2], bis[0], bis[1], bis[2]));
        } catch (IllegalArgumentException fehler) {
            // Der Satz des Kerns ist schon in Worten, die ein Betreiber lesen kann -
            // unbrauchbarer Name, zu grosser Bereich. Ihn hier noch einmal zu formulieren
            // waere eine zweite Fassung derselben Begruendung.
            klagen.add(pfad + ": " + fehler.getMessage() + " Uebersprungen.");
            return null;
        }
    }

    /**
     * Drei ganze Zahlen aus einer Zeile wie {@code von: [10, -60, 10]}.
     *
     * <p>Am TYP geprueft und nicht am Nachkommarest, wie {@link Ladenliste}: YAML macht
     * aus {@code 1.000} eine 1.0, und die ist ganzzahlig - gemeint waren 1000.
     *
     * <p>Dieselbe Pruefung steht in {@code Ladenliste.drei} und
     * {@code Weltenliste.ecke}. Drei Fassungen sind zwei zu viel, und das ist bekannt -
     * zusammengelegt werden sie, wenn der vierte Leser kommt. Bis dahin ist die
     * Abschrift die kleinere Schuld als ein Umbau an drei gepruefte Lesern waehrend
     * eines Bauschritts.
     */
    private static int[] drei(ConfigurationSection zeilen, String pfad, String schluessel,
            List<String> klagen) {
        if (!zeilen.contains(schluessel, true)) {
            klagen.add(pfad + "." + schluessel + " fehlt - die Station wird"
                    + " uebersprungen. Erwartet werden drei ganze Zahlen, etwa"
                    + " [10, -60, 10]. Altfurt ist flach: Der Boden liegt auf y=-61, der"
                    + " erste freie Block auf y=-60.");
            return null;
        }
        List<?> gelesen = zeilen.getList(schluessel);
        if (gelesen == null || gelesen.size() != 3) {
            klagen.add(pfad + "." + schluessel + " ist keine Liste aus DREI Zahlen ("
                    + Wirtschaftsliste.wieGetippt(zeilen.get(schluessel))
                    + ") - die Station wird uebersprungen.");
            return null;
        }
        int[] zahlen = new int[3];
        for (int stelle = 0; stelle < 3; stelle++) {
            Object roh = gelesen.get(stelle);
            if (!(roh instanceof Number zahl) || roh instanceof Double
                    || roh instanceof Float
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())
                    || Math.abs(zahl.doubleValue()) > Integer.MAX_VALUE) {
                klagen.add(pfad + "." + schluessel + " hat an Stelle " + (stelle + 1)
                        + " keine ganze Zahl (" + Wirtschaftsliste.wieGetippt(roh)
                        + ") - die Station wird uebersprungen. Ein Punkt trennt in YAML"
                        + " keine Tausender, sondern die Nachkommastellen.");
                return null;
            }
            zahlen[stelle] = zahl.intValue();
        }
        return zahlen;
    }

    /**
     * Zwei Stationen, die sich Bloecke teilen, werden BEIDE verworfen.
     *
     * <p>Dieselbe Antwort wie bei den Ladeneinheiten und den Toren: Welche zaehlt,
     * entschiede sonst die Reihenfolge der Datei - und beide Eintraege sehen fuer sich
     * richtig aus. Ein Spieler bekaeme je nach Sortierung eine andere Station gezaehlt.
     */
    private static void verwerfeUeberschneidungen(List<Rundgangsstation> stationen,
            List<String> klagen) {
        Set<String> verworfen = new LinkedHashSet<>();
        for (int eine = 0; eine < stationen.size(); eine++) {
            for (int andere = eine + 1; andere < stationen.size(); andere++) {
                Rundgangsstation diese = stationen.get(eine);
                Rundgangsstation jene = stationen.get(andere);
                if (diese.bereich().schneidet(jene.bereich())) {
                    klagen.add("Die Stationen " + diese.schluessel() + " und "
                            + jene.schluessel() + " haben Bloecke gemeinsam ("
                            + diese.bereich().alsText() + " und "
                            + jene.bereich().alsText() + "). Dann entschiede die"
                            + " Reihenfolge der Eintraege, welche ein Spieler gesehen"
                            + " hat - BEIDE werden verworfen.");
                    verworfen.add(diese.schluessel());
                    verworfen.add(jene.schluessel());
                }
            }
        }
        stationen.removeIf(station -> verworfen.contains(station.schluessel()));
    }

    /**
     * Eine Station hinter der Weltgrenze wird verworfen - dort kommt niemand hin.
     *
     * <p>Gemessen wird mit {@link Chunkfeld#ausGrenze}, genau wie bei den Toren und den
     * Ladeneinheiten. Ohne eingetragene Kante (0) wird nicht geprueft: Eine Welt ohne
     * Grenze hat keine, an der etwas liegen koennte.
     */
    private static void verwerfeAusserhalb(List<Rundgangsstation> stationen, String welt,
            Map<String, Integer> kantenJeWelt, List<String> klagen) {
        if (stationen.isEmpty()) {
            return;
        }
        Integer kante = kantenJeWelt == null ? null : kantenJeWelt.get(welt);
        if (kante == null) {
            klagen.add(PFAD_WELT + " ist \"" + welt + "\", aber diese Welt steht nicht in"
                    + " welten: - die Stationen haetten keinen Ort. Kein Rundgang gilt.");
            stationen.clear();
            return;
        }
        if (kante <= 0) {
            return;
        }
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, kante);
        stationen.removeIf(station -> {
            if (station.bereich().liegtIn(feld)) {
                return false;
            }
            klagen.add("Die Station " + station.schluessel() + " liegt bei "
                    + station.bereich().alsText() + " und damit ausserhalb der"
                    + " Weltgrenze von " + welt + " (Kante " + kante + ") - dort kommt"
                    + " niemand hin. Uebersprungen.");
            return true;
        });
    }

    /**
     * Die Zeile, die den Rundgang im Zusammenhang nennt.
     *
     * <p>Sie nennt die Stationen IN DER REIHENFOLGE DER DATEI, denn die ist der Weg - und
     * sie ist die einzige Schranke gegen eine Reihenfolge, die im erlaubten Bereich liegt
     * und trotzdem falsch ist. Wer "stadttor, buergeramt, zunfthaus" liest, sieht, dass
     * sein Weg am Ausgang beginnt.
     */
    private static String rechenprobe(String welt, Rundgangsverzeichnis verzeichnis) {
        return "Rundgang in " + welt + ": " + verzeichnis.anzahl() + " Stationen in"
                + " dieser Reihenfolge - " + String.join(", ", verzeichnis.namen())
                + ". Sie ist der WEG; wer sie aendert, aendert den Weg. Jede Station"
                + " braucht zwei Zeilen in der sprache.yml (rundgang.station.<name>.name"
                + " und .text).";
    }
}
