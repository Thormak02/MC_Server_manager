package de.freimarken.paper;

import de.freimarken.kern.Bautenverzeichnis;
import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code bauten:} - die Platzhalterbauten Altfurts (Plan 2.1).
 *
 * <p>Dieselbe Bauart wie {@link Rundgangsliste}: Dieser Leser WIRFT NIE und bricht den
 * Start nicht ab. Jede unbrauchbare Zeile wird beklagt und der Eintrag ganz
 * uebersprungen; die uebrigen gelten. Ein Server, dessen Altfurt noch nicht steht, ist
 * nicht kaputt.
 *
 * <p><b>Zwei Bauten, die sich Bloecke teilen, werden BEIDE verworfen</b> - dieselbe
 * Antwort wie bei den Ladeneinheiten, den Stationen und den Toren. Hier ist sie noch
 * teurer als dort: Zwei sich schneidende Quader wuerden GESETZT, und der zweite risse ein
 * Loch in die Wand des ersten. Welcher gewinnt, entschiede die Reihenfolge der Datei.
 *
 * <p><b>Ein Bau, der ein TOR beruehrt, wird verworfen</b>, und das ist die Pruefung, die
 * es bei den Stationen nicht gibt: Eine Station liest nur, ein Bau SETZT. Ein Quader
 * ueber einem Tor mauerte den Durchgang zu, und der Betreiber saehe ein Tor, das im
 * Startbericht gilt und im Spiel eine Wand ist.
 */
final class Bautenliste {

    /** Die Welt, in der die Bauten stehen. */
    static final String PFAD_WELT = "bauten.welt";

    /** Der Block mit den Bauten. */
    static final String PFAD_HAEUSER = "bauten.haeuser";

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(String welt, Bautenverzeichnis verzeichnis, List<String> klagen,
                    List<String> bericht) {
    }

    private Bautenliste() {
    }

    /**
     * Der ganze Abschnitt {@code bauten:}.
     *
     * @param wurzel       die ganze Konfiguration, darf {@code null} sein
     * @param kantenJeWelt die Kanten aus {@code welten:} - gebraucht, um einen Quader
     *                     hinter der Weltgrenze zu verwerfen. Hereingegeben und nicht
     *                     hier geholt, damit dieser Leser EINEN Abschnitt liest
     * @param tore         die Quader aller Tore - ein Bau darf keines beruehren.
     *                     Hereingegeben aus demselben Grund
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
            Map<String, Integer> kantenJeWelt, List<Quader> tore) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis("", Bautenverzeichnis.ohneBauten(), klagen, bericht);
        }
        String welt = welt(wurzel);
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD_HAEUSER);
        if (abschnitt == null || abschnitt.getKeys(false).isEmpty()) {
            bericht.add("Keine Platzhalterbauten: " + PFAD_HAEUSER + " ist leer. Wer"
                    + " Altfurt von Hand gebaut hat, laesst das so - sonst traegst du je"
                    + " Gebaeude zwei Ecken und die Tuerseite ein, und der naechste Start"
                    + " setzt es.");
            return new Ergebnis(welt, Bautenverzeichnis.ohneBauten(), klagen, bericht);
        }

        List<Platzhalterbau> bauten = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Platzhalterbau bau = einer(abschnitt, name, klagen);
            if (bau != null) {
                bauten.add(bau);
            }
        }
        verwerfeUeberschneidungen(bauten, klagen);
        verwerfeAufToren(bauten, tore, klagen);
        verwerfeAusserhalb(bauten, welt, kantenJeWelt, klagen);
        try {
            Bautenverzeichnis verzeichnis = Bautenverzeichnis.ausBauten(bauten);
            bericht.add(rechenprobe(welt, verzeichnis));
            return new Ergebnis(welt, verzeichnis, klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Der Leser wirft NIE, das Verzeichnis sehr wohl - darum dieses try/catch.
            // Hierher kommt, was die Zeile fuer Zeile gelesenen Bauten ZUSAMMEN
            // verletzen koennen: zu viele.
            klagen.add(PFAD_HAEUSER + ": " + unbrauchbar.getMessage()
                    + " Es wird nichts gesetzt.");
            return new Ergebnis(welt, Bautenverzeichnis.ohneBauten(), klagen, bericht);
        }
    }

    /** Die Welt der Bauten - ohne Eintrag keine, denn dann gibt es auch keinen Ort. */
    private static String welt(ConfigurationSection wurzel) {
        String gelesen = wurzel.getString(PFAD_WELT);
        if (gelesen == null || gelesen.isBlank()) {
            return "";
        }
        return gelesen.trim().toLowerCase(Locale.ROOT);
    }

    /** EIN Bau aus seinem Block - oder {@code null}, wenn die Zeilen nicht taugen. */
    private static Platzhalterbau einer(ConfigurationSection abschnitt, String name,
            List<String> klagen) {
        String pfad = PFAD_HAEUSER + "." + name;
        ConfigurationSection zeilen = abschnitt.getConfigurationSection(name);
        if (zeilen == null) {
            klagen.add(pfad + " ist kein Block mit von, bis und tuer ("
                    + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + ") - uebersprungen.");
            return null;
        }
        int[] von = drei(zeilen, pfad, "von", klagen);
        int[] bis = drei(zeilen, pfad, "bis", klagen);
        Richtung tuer = tuer(zeilen, pfad, klagen);
        if (von == null || bis == null || tuer == null) {
            return null;
        }
        try {
            return new Platzhalterbau(name,
                    new Quader(von[0], von[1], von[2], bis[0], bis[1], bis[2]), tuer);
        } catch (IllegalArgumentException fehler) {
            // Der Satz des Kerns ist schon in Worten, die ein Betreiber lesen kann -
            // zu kleiner Bau, unbrauchbarer Name, zu grosser Quader.
            klagen.add(pfad + ": " + fehler.getMessage() + " Uebersprungen.");
            return null;
        }
    }

    /**
     * Die Tuerseite als Wort - nord, ost, sued oder west.
     *
     * <p>Ein WORT und keine Zahl: Der Betreiber schreibt, wo die Tuer sein soll, und die
     * Zuordnung "Norden ist negatives Z" liegt im Kern einmal fest ({@link Richtung}).
     */
    private static Richtung tuer(ConfigurationSection zeilen, String pfad,
            List<String> klagen) {
        String gelesen = zeilen.getString("tuer");
        if (gelesen == null || gelesen.isBlank()) {
            klagen.add(pfad + ".tuer fehlt - der Bau wird uebersprungen. Erwartet wird"
                    + " eines der Worte " + worte() + ": die Seite, in der die Tuer"
                    + " steht.");
            return null;
        }
        String wort = gelesen.trim().toLowerCase(Locale.ROOT);
        for (Richtung richtung : Richtung.values()) {
            if (richtung.wort().equals(wort)) {
                return richtung;
            }
        }
        klagen.add(pfad + ".tuer ist \"" + gelesen + "\" - das ist keine Seite. Erwartet"
                + " wird eines der Worte " + worte() + ". Uebersprungen.");
        return null;
    }

    /** Die erlaubten Worte, aus der Aufzaehlung und nicht abgeschrieben. */
    private static String worte() {
        List<String> worte = new ArrayList<>();
        for (Richtung richtung : Richtung.values()) {
            worte.add(richtung.wort());
        }
        return String.join(", ", worte);
    }

    /**
     * Drei ganze Zahlen - die Pruefung macht {@link Zahlenzeile}, die WORTE stehen hier.
     *
     * <p>So herum und nicht umgekehrt: Jeder Leser sagt dem Betreiber etwas anderes, und
     * das Beispiel im Satz nennt die Hoehen SEINER Welt. Altfurt ist flach.
     */
    private static int[] drei(ConfigurationSection zeilen, String pfad, String schluessel,
            List<String> klagen) {
        Zahlenzeile.Drei gelesen = Zahlenzeile.lies(zeilen, schluessel);
        String stelle = pfad + "." + schluessel;
        switch (gelesen.mangel()) {
            case KEINER -> {
                return gelesen.zahlen();
            }
            case FEHLT -> klagen.add(stelle + " fehlt - der Bau wird uebersprungen."
                    + " Erwartet werden drei ganze Zahlen, etwa [50, -61, 2]. Altfurt ist"
                    + " flach: Der Boden liegt auf y=-61, der erste freie Block auf"
                    + " y=-60.");
            case NICHT_DREI -> klagen.add(stelle + " ist keine Liste aus DREI Zahlen ("
                    + Wirtschaftsliste.wieGetippt(gelesen.getippt())
                    + ") - der Bau wird uebersprungen.");
            case KEINE_GANZE_ZAHL -> klagen.add(stelle + " hat an Stelle "
                    + gelesen.stelle() + " keine ganze Zahl ("
                    + Wirtschaftsliste.wieGetippt(gelesen.getippt()) + ") - der Bau wird"
                    + " uebersprungen. Ein Punkt trennt in YAML keine Tausender, sondern"
                    + " die Nachkommastellen.");
            case JENSEITS -> klagen.add(stelle + " liegt an Stelle " + gelesen.stelle()
                    + " jenseits der Weltkoordinaten ("
                    + Wirtschaftsliste.wieGetippt(gelesen.getippt()) + ") - der Bau wird"
                    + " uebersprungen.");
            default -> throw new IllegalStateException("Unbekannter Mangel: "
                    + gelesen.mangel());
        }
        return null;
    }

    /** Zwei Bauten, die sich Bloecke teilen, werden BEIDE verworfen - siehe Klassenkopf. */
    private static void verwerfeUeberschneidungen(List<Platzhalterbau> bauten,
            List<String> klagen) {
        Set<String> verworfen = new LinkedHashSet<>();
        for (int einer = 0; einer < bauten.size(); einer++) {
            for (int anderer = einer + 1; anderer < bauten.size(); anderer++) {
                Platzhalterbau dieser = bauten.get(einer);
                Platzhalterbau jener = bauten.get(anderer);
                if (dieser.bereich().schneidet(jener.bereich())) {
                    klagen.add("Die Bauten " + dieser.schluessel() + " und "
                            + jener.schluessel() + " haben Bloecke gemeinsam ("
                            + dieser.bereich().alsText() + " und "
                            + jener.bereich().alsText() + "). Der zweite risse ein Loch"
                            + " in die Wand des ersten - BEIDE werden verworfen.");
                    verworfen.add(dieser.schluessel());
                    verworfen.add(jener.schluessel());
                }
            }
        }
        bauten.removeIf(bau -> verworfen.contains(bau.schluessel()));
    }

    /**
     * Ein Bau, der ein Tor beruehrt, wird verworfen - er mauerte den Durchgang zu.
     *
     * <p>Diese Pruefung gibt es bei den Stationen nicht, und der Unterschied ist der
     * Grund: Eine Station liest nur, ein Bau SETZT.
     */
    private static void verwerfeAufToren(List<Platzhalterbau> bauten, List<Quader> tore,
            List<String> klagen) {
        if (tore == null || tore.isEmpty()) {
            return;
        }
        bauten.removeIf(bau -> {
            for (Quader tor : tore) {
                if (bau.bereich().schneidet(tor)) {
                    klagen.add("Der Bau " + bau.schluessel() + " liegt bei "
                            + bau.bereich().alsText() + " und beruehrt damit das Tor bei "
                            + tor.alsText() + " - er mauerte den Durchgang zu."
                            + " Uebersprungen.");
                    return true;
                }
            }
            return false;
        });
    }

    /** Ein Bau hinter der Weltgrenze wird verworfen - dort kommt niemand hin. */
    private static void verwerfeAusserhalb(List<Platzhalterbau> bauten, String welt,
            Map<String, Integer> kantenJeWelt, List<String> klagen) {
        if (bauten.isEmpty()) {
            return;
        }
        Integer kante = kantenJeWelt == null ? null : kantenJeWelt.get(welt);
        if (kante == null) {
            klagen.add(PFAD_WELT + " ist \"" + welt + "\", aber diese Welt steht nicht in"
                    + " welten: - die Bauten haetten keinen Ort. Es wird nichts gesetzt.");
            bauten.clear();
            return;
        }
        if (kante <= 0) {
            return;
        }
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, kante);
        bauten.removeIf(bau -> {
            if (bau.bereich().liegtIn(feld)) {
                return false;
            }
            klagen.add("Der Bau " + bau.schluessel() + " liegt bei "
                    + bau.bereich().alsText() + " und damit ausserhalb der Weltgrenze von "
                    + welt + " (Kante " + kante + ") - dort kommt niemand hin."
                    + " Uebersprungen.");
            return true;
        });
    }

    /**
     * Die Zeile fuer den Startbericht - sie nennt die Zahl, die beim naechsten Start
     * Arbeit bedeutet.
     */
    private static String rechenprobe(String welt, Bautenverzeichnis verzeichnis) {
        return "Platzhalterbauten in " + welt + ": " + verzeichnis.anzahl() + " ("
                + String.join(", ", verzeichnis.namen()) + "), zusammen "
                + verzeichnis.bloecke() + " Bloecke. Gesetzt wird beim Start nur, was"
                + " FEHLT - NUR BEIM START gelesen.";
    }
}
