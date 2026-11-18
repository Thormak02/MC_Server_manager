package de.freimarken.paper;

import de.freimarken.kern.Punktwerte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code wolken.punkte:} - was eine Insel WERT ist.
 *
 * <p>Ein eigener Leser wie {@link Aufgabenliste}, aus demselben Grund: eine Tabelle mit
 * eigenen Klagen und zwei Zahlen daneben. Die tragende Zusage ist auch hier, dass er NIE
 * wirft - ein Tippfehler kostet seine Zeile.
 */
final class Punkteliste {

    /** Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL = List.of(Punktwerte.PFAD_TABELLE,
            Punktwerte.PFAD_CHUNKS_JE_TAKT, Punktwerte.PFAD_ABKLINGZEIT);

    record Ergebnis(Punktwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Punkteliste() {
    }

    /** Der Abschnitt {@code wolken.punkte:}; {@code null} ist erlaubt. */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Punktwerte.VORGABE, klagen, bericht);
        }
        Map<String, Long> tabelle = tabelle(wurzel, klagen, bericht);
        int happen = (int) zahl(wurzel, Punktwerte.PFAD_CHUNKS_JE_TAKT,
                Punktwerte.VORGABE_CHUNKS_JE_TAKT, 1L, Punktwerte.HOECHSTENS_JE_TAKT,
                klagen, bericht);
        long abklingzeit = zahl(wurzel, Punktwerte.PFAD_ABKLINGZEIT,
                Punktwerte.VORGABE_ABKLINGZEIT, 0L, 86_400L, klagen, bericht);
        try {
            return new Ergebnis(new Punktwerte(tabelle, happen, abklingzeit), klagen,
                    bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add("Die Punktwerte passen nicht zusammen: "
                    + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt(Punktwerte.VORGABE_TABELLE.size()
                            + " Bloecke des Programms"));
            return new Ergebnis(Punktwerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Sie nennt, wie lange eine Zaehlung braucht, und das ist die Schranke gegen den
     * einen teuren Vertipper: Wer {@code chunks-je-takt: 64} setzt, zaehlt ein ganzes Feld
     * in EINEM Takt - und der Server steht, solange das laeuft.
     */
    static String rechenprobe(Punktwerte werte) {
        if (!werte.greift()) {
            return "Punkte: keine Tabelle - jede Insel hat 0 Punkte, und die Rangliste"
                    + " zeigt sie in der Reihenfolge ihres Alters.";
        }
        // 64 Chunks je Feld (128 Bloecke zu 16), ein Takt ist ein Tick.
        long takte = (64 + werte.chunksJeTakt() - 1) / werte.chunksJeTakt();
        return "Punkte: " + werte.tabelle().size() + " Bloecke zaehlen, "
                + werte.chunksJeTakt() + " Chunk(s) je Takt - eine Zaehlung braucht "
                + takte + " Takte (etwa " + (takte / 20 + 1) + " Sekunden), danach "
                + werte.abklingSekunden() + " Sekunden Pause je Insel.";
    }

    /** Eine Zahl mit Grenzen - drei Zeilen durch EINE Methode. */
    private static long zahl(ConfigurationSection wurzel, String pfad, long vorgabe,
                             long kleinstens, long groesstens, List<String> klagen,
                             List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        if (gelesen < kleinstens || gelesen > groesstens) {
            klagen.add(pfad + " ist " + gelesen + " und liegt nicht zwischen "
                    + kleinstens + " und " + groesstens + " - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        if (gelesen != vorgabe) {
            bericht.add(pfad + " = " + gelesen + " gilt.");
        }
        return gelesen;
    }

    /**
     * Die Punktetabelle - Zeile fuer Zeile geprueft.
     *
     * <p>Fehlt der Abschnitt, gilt die Tabelle des Programms. Ist er LEER, gilt eine leere
     * Tabelle, und das ist etwas anderes: Wer alle Zeilen herausnimmt, will keine
     * Rangliste nach Punkten - und soll sie nicht aus der Vorgabe zurueckbekommen.
     */
    private static Map<String, Long> tabelle(ConfigurationSection wurzel,
                                             List<String> klagen, List<String> bericht) {
        if (!wurzel.contains(Punktwerte.PFAD_TABELLE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Punktwerte.PFAD_TABELLE,
                    Punktwerte.VORGABE_TABELLE.size() + " Bloecke des Programms"));
            return Punktwerte.VORGABE_TABELLE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Punktwerte.PFAD_TABELLE);
        if (abschnitt == null) {
            klagen.add(Punktwerte.PFAD_TABELLE + " ist keine Liste von Bloecken mit"
                    + " Punkten - verworfen. Es gilt die Tabelle des Programms.");
            return Punktwerte.VORGABE_TABELLE;
        }
        Map<String, Long> tabelle = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                klagen.add(Punktwerte.PFAD_TABELLE + "." + name + " " + warum
                        + " - diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > Punktwerte.HOECHSTE_PUNKTE) {
                klagen.add(Punktwerte.PFAD_TABELLE + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet werden Punkte"
                        + " von 1 bis " + Punktwerte.HOECHSTE_PUNKTE + ". Diese Zeile"
                        + " faellt weg, die uebrigen gelten.");
                continue;
            }
            tabelle.put(klein, zahl.longValue());
        }
        if (tabelle.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            bericht.add(Punktwerte.PFAD_TABELLE + ": keine einzige Zeile war brauchbar -"
                    + " jede Insel hat bis zur naechsten Berichtigung 0 Punkte.");
        }
        return tabelle;
    }
}
