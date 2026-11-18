package de.freimarken.paper;

import de.freimarken.kern.Angelwerte;
import de.freimarken.kern.Fangregel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code angeln:} aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Verdienstliste}: reine Dateiarbeit, gesammelte Klagen
 * statt Wuerfe, und eine kaputte ZEILE faellt weg, waehrend der Rest der Tabelle gilt.
 * Ein Tippfehler in einem Fangnamen darf nicht die ganze Tabelle kosten - sonst angelt
 * eine Welt wieder Vanilla, und im Protokoll steht eine Zeile, die niemand liest.
 *
 * <p>Geprueft wird der Name mit {@link Verdienstliste#warumKeinMaterial}, also mit
 * DERSELBEN Pruefung wie die Lohntafel: Zwei Meinungen darueber, was ein
 * Gegenstandsname ist, waeren der Fehler, bei dem der Betreiber eine Zeile in der einen
 * Tabelle eintragen kann und in der anderen nicht.
 */
final class Angelliste {

    /** Wie viele Zeilen der Bericht hoechstens einzeln nennt. */
    private static final int ZEILEN_IM_BERICHT = 12;

    /** Was im Abschnitt stand - und woran es gehapert hat. */
    record Ergebnis(Angelwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Angelliste() {
    }

    /**
     * Die Angeltabelle aus dem Abschnitt {@code angeln:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null || !wurzel.contains(Angelwerte.PFAD_AN, true)
                && !wurzel.contains(Angelwerte.PFAD_FAENGE, true)) {
            // Der ganze Abschnitt fehlt. KEINE Klage, aber eine Berichtzeile: Auf einem
            // Server, der schon lief, schreibt saveDefaultConfig die Datei nie um - die
            // Zeilen fehlen dort einfach, und es gilt die Tabelle des Programms.
            bericht.add("angeln: fehlt in der config.yml - es gilt die ausgelieferte"
                    + " Tabelle des Programms.");
            return new Ergebnis(Angelwerte.VORGABE, klagen, bericht);
        }
        boolean an = jaNein(wurzel, klagen, bericht);
        int haeufigkeit = haeufigkeit(wurzel, klagen, bericht);
        Map<String, Integer> faenge = faenge(wurzel, klagen, bericht);

        Angelwerte werte = new Angelwerte(an, haeufigkeit, faenge);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Tabelle im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Lohntafel: die einzige Schranke gegen den Wert, der
     * im erlaubten Bereich liegt und trotzdem falsch ist. Wer "Meeresherz: 120 Promille"
     * liest, sieht die verschobene Null; keine Zahlengrenze sieht sie.
     *
     * <p>Gerechnet wird in PROMILLE VON ALLEM, also mit der Haeufigkeit darin - das ist
     * die Zahl, die ein Spieler merkt. Paketprivat, damit eine Probe denselben Text
     * nimmt.
     */
    static String rechenprobe(Angelwerte werte) {
        if (!werte.greift()) {
            return "Angelgut: AUS - in Hafenwerder faengt man, was Vanilla gibt.";
        }
        StringBuilder satz = new StringBuilder("Angelgut: ");
        satz.append(werte.gewichte().size()).append(" Faenge, jeder ")
                .append(werte.haeufigkeitProzent()).append(". Fang aus der Tabelle (");
        List<String> namen = new ArrayList<>(werte.gewichte().keySet());
        namen.sort(null);
        for (int i = 0; i < namen.size() && i < ZEILEN_IM_BERICHT; i++) {
            if (i > 0) {
                satz.append(", ");
            }
            satz.append(namen.get(i)).append(' ')
                    .append(Fangregel.promilleVonAllem(werte, namen.get(i)))
                    .append(" Promille");
        }
        if (namen.size() > ZEILEN_IM_BERICHT) {
            satz.append(", und ").append(namen.size() - ZEILEN_IM_BERICHT)
                    .append(" weitere");
        }
        return satz.append(").").toString();
    }

    private static boolean jaNein(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        if (!wurzel.contains(Angelwerte.PFAD_AN, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Angelwerte.PFAD_AN,
                    String.valueOf(Angelwerte.VORGABE.an())));
            return Angelwerte.VORGABE.an();
        }
        Object roh = wurzel.get(Angelwerte.PFAD_AN);
        if (!(roh instanceof Boolean wert)) {
            klagen.add(Angelwerte.PFAD_AN + " ist kein ja/nein ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(Angelwerte.VORGABE.an())));
            return Angelwerte.VORGABE.an();
        }
        return wert;
    }

    private static int haeufigkeit(ConfigurationSection wurzel, List<String> klagen,
                                   List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, Angelwerte.PFAD_HAEUFIGKEIT,
                Angelwerte.VORGABE_HAEUFIGKEIT, klagen, bericht);
        if (gelesen == null) {
            return Angelwerte.VORGABE_HAEUFIGKEIT;
        }
        long wert = gelesen;
        if (wert < 0L || wert > 100L) {
            klagen.add(Angelwerte.PFAD_HAEUFIGKEIT + " ist " + wert + " und liegt nicht"
                    + " zwischen 0 und 100 Prozent - es gilt "
                    + Angelwerte.VORGABE_HAEUFIGKEIT + ".");
            return Angelwerte.VORGABE_HAEUFIGKEIT;
        }
        return (int) wert;
    }

    /**
     * Die Tabelle - Zeile fuer Zeile geprueft, kaputte Zeilen beklagt und uebersprungen.
     *
     * <p>Fehlt der Abschnitt, gilt die Tabelle des Programms. Ist er LEER, gilt eine
     * leere Tabelle - und das ist etwas anderes: Wer alle Zeilen herausnimmt, will
     * Vanilla und soll es bekommen, ohne den Schalter zu finden.
     */
    private static Map<String, Integer> faenge(ConfigurationSection wurzel,
                                               List<String> klagen,
                                               List<String> bericht) {
        if (!wurzel.contains(Angelwerte.PFAD_FAENGE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Angelwerte.PFAD_FAENGE,
                    Angelwerte.VORGABE_FAENGE.size() + " Faenge des Programms"));
            return Angelwerte.VORGABE_FAENGE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Angelwerte.PFAD_FAENGE);
        if (abschnitt == null) {
            klagen.add(Angelwerte.PFAD_FAENGE + " ist keine Liste von Gegenstaenden mit"
                    + " Gewicht - verworfen. Es gilt die Tabelle des Programms.");
            return Angelwerte.VORGABE_FAENGE;
        }
        Map<String, Integer> tabelle = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                klagen.add(Angelwerte.PFAD_FAENGE + "." + name + " " + warum
                        + " - diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > Angelwerte.HOECHSTES_GEWICHT) {
                klagen.add(Angelwerte.PFAD_FAENGE + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird ein"
                        + " Gewicht von 1 bis " + Angelwerte.HOECHSTES_GEWICHT
                        + ". Diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            tabelle.put(klein, (int) zahl.longValue());
        }
        if (tabelle.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            // JEDE Zeile war kaputt. Die Tabelle des Programms waere hier falsch: Der
            // Betreiber hat eine eigene gewollt, und mit der Vorgabe faenge er Dinge,
            // die er gerade herausgenommen hat.
            bericht.add("angeln.faenge: keine einzige Zeile war brauchbar - in"
                    + " Hafenwerder faengt man bis zur naechsten Berichtigung, was"
                    + " Vanilla gibt.");
        }
        return tabelle;
    }
}
