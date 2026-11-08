package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Quader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Ladeneinheiten aus der {@code config.yml} - Teil A von Schritt 8.
 *
 * <p>Dieselbe Bauart wie {@link Marktliste}, {@link Messliste} und {@link Bonusliste}: ein
 * Ergebnis mit Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen
 * stehen gelassen.
 *
 * <p><b>Die Prueffolge ist nicht beliebig.</b> Erst wird jede Einheit fuer sich geprueft
 * (Name, Ecken, Truhe, Groesse), dann werden die ueberlebenden GEGENEINANDER geprueft:
 * Zwei Einheiten, die sich Bloecke teilen, sind beide unbrauchbar, und welche gilt,
 * entschiede sonst die Reihenfolge der Datei. Genau dieselbe Antwort wie bei den Toren in
 * {@link Weltenliste} - und aus demselben Grund: Beide Eintraege sehen fuer sich richtig
 * aus, der Fehler ist unsichtbar.
 *
 * <p><b>Die ausgelieferte Datei traegt KEINE Einheit.</b> Die Markthalle ist eine der zehn
 * Platzhalterbauten aus Plan 2.1 und existiert noch nicht; eine erfundene Koordinate waere
 * ein Laden mitten im Wasser. Fehlt der Abschnitt, ist das Verzeichnis leer - und der
 * Bericht sagt es.
 */
final class Ladenliste {

    /** Der Abschnitt mit den Einheiten. */
    static final String PFAD_EINHEITEN = "laeden.einheiten";

    /** In welcher Welt die Halle steht. */
    static final String PFAD_WELT = "laeden.welt";

    /**
     * Die Vorgabe: Altfurt.
     *
     * <p>Plan 2.1 setzt die Markthalle in die Hauptstadt, und Plan 1675 fuehrt den
     * Rundgang dort vorbei. Der Name steht hier ohne Namensraum, wie bei
     * {@code reisen.ankunftswelt} - das Plugin setzt {@code freimarken:} davor.
     */
    static final String VORGABE_WELT = "altfurt";

    record Ergebnis(String welt, Ladenverzeichnis verzeichnis, List<String> klagen,
                    List<String> bericht) {
    }

    private Ladenliste() {
    }

    /**
     * Der ganze Abschnitt {@code laeden:}.
     *
     * @param wurzel     die ganze Konfiguration, darf {@code null} sein
     * @param kantenJeWelt woran gemessen wird, ob eine Einheit hinter der Weltgrenze
     *                     liegt - der Name der Welt auf ihre Kante, oder 0 fuer eine Welt
     *                     ohne Grenze. Hereingegeben und nicht selbst gelesen: Die Kanten
     *                     stehen im Abschnitt {@code welten:}, den {@link Weltenliste}
     *                     liest, und zwei Leser derselben Zeile laufen auseinander
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
                                     Map<String, Integer> kantenJeWelt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(VORGABE_WELT, Ladenverzeichnis.ohneEinheiten(), klagen,
                    bericht);
        }
        String welt = welt(wurzel, klagen, bericht);
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD_EINHEITEN);
        if (abschnitt == null) {
            // BERICHT und keine Klage, aus demselben Grund wie bei der Lohnliste und beim
            // Sortiment: Die Markthalle ist von Hand zu bauen, und bis dahin ist eine
            // leere Liste der richtige Zustand - keine Fehleingabe.
            bericht.add(PFAD_EINHEITEN + " steht nicht in deiner config.yml - es gibt"
                    + " keine Ladeneinheiten. Die Markthalle ist von Hand zu bauen (Plan"
                    + " 2.1); danach gehoeren ihre Staende hierher.");
            return new Ergebnis(welt, Ladenverzeichnis.ohneEinheiten(), klagen, bericht);
        }
        List<Ladeneinheit> sauber = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Ladeneinheit einheit = eine(abschnitt, name, klagen);
            if (einheit != null) {
                sauber.add(einheit);
            }
        }
        verwerfeUeberschneidungen(sauber, klagen);
        verwerfeAusserhalb(sauber, welt, kantenJeWelt, klagen);
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(sauber);
        nenneDoppelte(abschnitt, verzeichnis, klagen);
        boolean ohneEinheit = verzeichnis.anzahl() == 0;
        if (ohneEinheit) {
            // ZWEI Saetze, und welcher zutrifft, haengt an den KLAGEN. Im gelesenen
            // Startlauf vom 08.11.2026 fiel auf, dass hier immer "die Gruende stehen in
            // den Zeilen davor" stand - auch bei dem ausgelieferten einheiten: {}, wo es
            // gar keine Zeilen davor gibt. Der Satz verwies auf nichts, und ein
            // Betreiber hat darueber gesucht.
            bericht.add(PFAD_EINHEITEN + " ist leer - es gibt keine Ladeneinheiten."
                    + (klagen.isEmpty()
                            ? " Die Markthalle ist von Hand zu bauen (Plan 2.1); danach"
                                    + " gehoeren ihre Staende hierher."
                            : " Die Gruende stehen in den Zeilen davor."));
        } else {
            bericht.add("Laeden: " + verzeichnis.anzahl() + " Einheiten in " + welt + " - "
                    + String.join(", ", verzeichnis.namen()) + " (zusammen "
                    + verzeichnis.bloecke() + " Bloecke). NUR BEIM START gelesen.");
        }
        return new Ergebnis(welt, verzeichnis, klagen, bericht);
    }

    /** In welcher Welt die Halle steht. */
    private static String welt(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        if (!wurzel.contains(PFAD_WELT, true)) {
            bericht.add(Wirtschaftsliste.fehltText(PFAD_WELT, VORGABE_WELT));
            return VORGABE_WELT;
        }
        Object roh = wurzel.get(PFAD_WELT);
        if (!(roh instanceof String name) || name.isBlank()) {
            klagen.add(PFAD_WELT + " ist kein Weltname ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(VORGABE_WELT));
            return VORGABE_WELT;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Eine Einheit - oder {@code null}, wenn ihre Zeilen unbrauchbar sind.
     *
     * <p>Jede kaputte Einheit wird beklagt und uebersprungen; die uebrigen gelten weiter.
     * Ein Server, der wegen eines Tippfehlers in einem von zwanzig Staenden nicht startet,
     * ist schlimmer als einer mit neunzehn.
     */
    private static Ladeneinheit eine(ConfigurationSection abschnitt, String name,
                                     List<String> klagen) {
        String pfad = PFAD_EINHEITEN + "." + name;
        if (!Ladeneinheit.brauchbarerName(name)) {
            klagen.add(pfad + " ist kein brauchbarer Name - uebersprungen. Erlaubt sind"
                    + " kleine Buchstaben, Ziffern und Striche; der Name steht auch im"
                    + " Befehl /laden mieten <name>.");
            return null;
        }
        ConfigurationSection zeilen = abschnitt.getConfigurationSection(name);
        if (zeilen == null) {
            klagen.add(pfad + " ist kein Block mit von, bis und truhe ("
                    + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + ") - uebersprungen.");
            return null;
        }
        int[] von = drei(zeilen, pfad, "von", klagen);
        int[] bis = drei(zeilen, pfad, "bis", klagen);
        int[] truhe = drei(zeilen, pfad, "truhe", klagen);
        if (von == null || bis == null || truhe == null) {
            return null;
        }
        try {
            return new Ladeneinheit(name,
                    new Quader(von[0], von[1], von[2], bis[0], bis[1], bis[2]),
                    truhe[0], truhe[1], truhe[2]);
        } catch (IllegalArgumentException fehler) {
            // Der Satz des Kerns ist schon in Worten, die ein Betreiber lesen kann -
            // Truhe ausserhalb, zu grosser Raum, unbrauchbarer Name. Ihn hier noch einmal
            // zu formulieren waere eine zweite Fassung derselben Begruendung.
            klagen.add(pfad + ": " + fehler.getMessage() + " Uebersprungen.");
            return null;
        }
    }

    /**
     * Drei ganze Zahlen aus einer Zeile wie {@code von: [10, 71, 10]}.
     *
     * <p>Am TYP geprueft und nicht am Nachkommarest, wie {@link Wirtschaftsliste#ganzeZahl}:
     * YAML macht aus {@code 1.000} eine 1.0, und die ist ganzzahlig - gemeint waren 1000.
     */
    private static int[] drei(ConfigurationSection zeilen, String pfad, String schluessel,
                              List<String> klagen) {
        if (!zeilen.contains(schluessel, true)) {
            klagen.add(pfad + "." + schluessel + " fehlt - die Einheit wird"
                    + " uebersprungen. Erwartet werden drei ganze Zahlen, etwa"
                    + " [10, 71, 10].");
            return null;
        }
        List<?> gelesen = zeilen.getList(schluessel);
        if (gelesen == null || gelesen.size() != 3) {
            klagen.add(pfad + "." + schluessel + " ist keine Liste aus DREI Zahlen ("
                    + Wirtschaftsliste.wieGetippt(zeilen.get(schluessel))
                    + ") - die Einheit wird uebersprungen.");
            return null;
        }
        int[] zahlen = new int[3];
        for (int i = 0; i < 3; i++) {
            Object roh = gelesen.get(i);
            if (!(roh instanceof Number zahl) || roh instanceof Double
                    || roh instanceof Float
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())
                    || Math.abs(zahl.doubleValue()) > Integer.MAX_VALUE) {
                klagen.add(pfad + "." + schluessel + " hat an Stelle " + (i + 1)
                        + " keine ganze Zahl (" + Wirtschaftsliste.wieGetippt(roh)
                        + ") - die Einheit wird uebersprungen. Ein Punkt trennt in YAML"
                        + " keine Tausender, sondern die Nachkommastellen.");
                return null;
            }
            zahlen[i] = zahl.intValue();
        }
        return zahlen;
    }

    /**
     * Zwei Einheiten, die sich Bloecke teilen, werden BEIDE verworfen.
     *
     * <p>Dieselbe Antwort wie bei den Toren: Welche gilt, entschiede sonst die Reihenfolge
     * der Datei - und beide Eintraege sehen fuer sich richtig aus. Ein Mieter koennte dann
     * in der Truhe seines Nachbarn stehen und waere dort geschuetzt.
     */
    private static void verwerfeUeberschneidungen(List<Ladeneinheit> einheiten,
                                                  List<String> klagen) {
        Set<String> verworfen = new LinkedHashSet<>();
        for (int i = 0; i < einheiten.size(); i++) {
            for (int j = i + 1; j < einheiten.size(); j++) {
                Ladeneinheit eine = einheiten.get(i);
                Ladeneinheit andere = einheiten.get(j);
                if (eine.bereich().schneidet(andere.bereich())) {
                    klagen.add("Die Ladeneinheiten " + eine.name() + " und "
                            + andere.name() + " haben Bloecke gemeinsam ("
                            + eine.bereich().alsText() + " und "
                            + andere.bereich().alsText() + "). Dann entschiede die"
                            + " Reihenfolge der Eintraege, wem eine Truhe gehoert - BEIDE"
                            + " werden verworfen.");
                    verworfen.add(eine.name());
                    verworfen.add(andere.name());
                }
            }
        }
        einheiten.removeIf(einheit -> verworfen.contains(einheit.name()));
    }

    /**
     * Eine Einheit hinter der Weltgrenze wird verworfen - dort kommt niemand hin.
     *
     * <p>Gemessen wird mit {@link Chunkfeld#ausGrenze}, genau wie bei den Toren. Ohne
     * eingetragene Kante (0) wird nicht geprueft: Eine Welt ohne Grenze hat keine, an der
     * etwas liegen koennte.
     */
    private static void verwerfeAusserhalb(List<Ladeneinheit> einheiten, String welt,
                                           Map<String, Integer> kantenJeWelt,
                                           List<String> klagen) {
        Integer kante = kantenJeWelt == null ? null : kantenJeWelt.get(welt);
        if (kante == null) {
            klagen.add(PFAD_WELT + " ist \"" + welt + "\", aber diese Welt steht nicht in"
                    + " welten: - die Einheiten haetten keinen Ort. Keine Einheit gilt.");
            einheiten.clear();
            return;
        }
        if (kante <= 0) {
            return;
        }
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, kante);
        einheiten.removeIf(einheit -> {
            if (einheit.bereich().liegtIn(feld)) {
                return false;
            }
            klagen.add("Die Ladeneinheit " + einheit.name() + " liegt bei "
                    + einheit.bereich().alsText() + " und damit ausserhalb der Grenze von "
                    + welt + " (Kante " + kante + " um 0/0). Dort kaeme"
                    + " niemand hin - verworfen.");
            return true;
        });
    }

    /**
     * Nennt jeden Eintrag, der weggefallen ist, weil sein Name schon da war.
     *
     * <p>Das Verzeichnis nimmt den ERSTEN und schweigt. Dass der zweite wegfiel, muss der
     * Betreiber lesen: Er hat zwei Staende eingetragen und sieht nur einen, und ohne diese
     * Zeile sucht er den Fehler in der Halle.
     *
     * <p>YAML selbst wirft einen doppelten Schluessel schon beim Laden weg, der Fall ist
     * also nur ueber zwei Namen erreichbar, die sich erst nach dem Kleinschreiben gleichen
     * ({@code Halle-1} und {@code halle-1}).
     */
    private static void nenneDoppelte(ConfigurationSection abschnitt,
                                      Ladenverzeichnis verzeichnis, List<String> klagen) {
        Set<String> gesehen = new LinkedHashSet<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            if (verzeichnis.mitNamen(klein) == null) {
                continue;
            }
            if (!gesehen.add(klein)) {
                klagen.add(PFAD_EINHEITEN + "." + name + " traegt denselben Namen wie ein"
                        + " Eintrag davor (" + klein + ") - uebersprungen. Der erste"
                        + " gewinnt.");
            }
        }
    }
}
