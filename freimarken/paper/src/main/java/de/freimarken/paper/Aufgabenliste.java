package de.freimarken.paper;

import de.freimarken.kern.Aufgabenwerte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code wolken.aufgaben:} - die Herausforderungen.
 *
 * <p>Ein eigener Leser und kein weiterer Zweig in {@link Wolkenliste}, aus demselben
 * Grund, aus dem {@link Angelliste} und {@link Verdienstliste} eigene sind: Hier wird eine
 * LISTE von Eintraegen gelesen, von denen jeder selbst eine Tabelle traegt. Das sind zwei
 * Schachtelungen tiefer als jede andere Zeile der Wolkeninseln, und sie haben ihre eigenen
 * Klagen.
 *
 * <p><b>Die Reihenfolge der Datei wird gehalten.</b> Bukkits {@code getKeys(false)} liefert
 * die Schluessel in der Reihenfolge, in der sie in der Datei stehen - und die ist eine
 * Aussage des Betreibers: von leicht nach schwer. Eine sortierte Liste waere die Tafel im
 * Spiel in alphabetischer Folge, und damit faenge jeder Spieler mit dem Baecker an.
 *
 * <p><b>Jede kaputte Zeile kostet NUR sich selbst.</b> Dieselbe Linie wie ueberall: Eine
 * Aufgabe mit einem Gegenstand, den es nicht gibt, faellt weg - die uebrigen gelten. Ein
 * Wurf mitten im Lesen riss schon einmal einen Start mit.
 */
final class Aufgabenliste {

    /** Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL =
            List.of(Aufgabenwerte.PFAD_AN, Aufgabenwerte.PFAD_LISTE);

    record Ergebnis(Aufgabenwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Aufgabenliste() {
    }

    /**
     * Der Abschnitt {@code wolken.aufgaben:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Aufgabenwerte.VORGABE, klagen, bericht);
        }
        boolean an = schalter(wurzel, klagen, bericht);
        List<Aufgabenwerte.Aufgabe> liste = liste(wurzel, klagen, bericht);
        try {
            return new Ergebnis(new Aufgabenwerte(an, liste), klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt nur aus einer Zusage, die der Leser nicht selbst prueft - etwa aus
            // der Hoechstzahl. Die Liste des Programms ist dann der Rueckfall, denn eine
            // halbe Liste waere eine Welt mit einem Fortschritt, den niemand eingestellt
            // hat.
            klagen.add("Die Aufgabenliste passt nicht zusammen: "
                    + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt(Aufgabenwerte.VORGABE_LISTE.size()
                            + " Herausforderungen des Programms"));
            return new Ergebnis(Aufgabenwerte.VORGABE.mitAn(an), klagen, bericht);
        }
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Sie nennt die Zahl der Aufgaben und die hoechste Stufe, die damit erreichbar ist.
     * Das ist die Schranke gegen den haeufigsten Fehler dieser Liste: Wer vier Aufgaben
     * eintraegt und in {@code wolken.erze.tabelle} den Diamanten ab Stufe fuenf stehen
     * laesst, hat ein Erz, das in dieser Welt niemand je bekommt.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann.
     */
    static String rechenprobe(Aufgabenwerte werte) {
        if (!werte.greift()) {
            return "Herausforderungen: keine - "
                    + (werte.an() ? "die Liste ist leer" : "wolken.aufgaben.an ist aus")
                    + ". Jede Insel bleibt auf Stufe 0, und der Generator gibt nur, was"
                    + " dort steht.";
        }
        long summe = 0L;
        for (Aufgabenwerte.Aufgabe aufgabe : werte.liste()) {
            summe += aufgabe.belohnung();
        }
        return "Herausforderungen: " + werte.liste().size() + " Stueck, hoechste Stufe "
                + werte.liste().size() + ", zusammen " + summe
                + " Mark zu verdienen - je Insel EINMAL.";
    }

    private static boolean schalter(ConfigurationSection wurzel, List<String> klagen,
                                    List<String> bericht) {
        Boolean gelesen = Wirtschaftsliste.jaNein(wurzel, Aufgabenwerte.PFAD_AN,
                Aufgabenwerte.VORGABE.an(), klagen, bericht);
        if (gelesen == null) {
            return Aufgabenwerte.VORGABE.an();
        }
        if (gelesen != Aufgabenwerte.VORGABE.an()) {
            // GEMELDET, und zwar laut: Ohne Herausforderungen hat diese Welt keinen
            // Fortschritt - jede Insel bleibt auf Stufe 0, und die Mindeststufen der
            // Erztabelle sind Zeilen, die nie greifen.
            bericht.add(Aufgabenwerte.PFAD_AN + " = " + gelesen + " gilt.");
        }
        return gelesen;
    }

    /**
     * Die Liste - Eintrag fuer Eintrag.
     *
     * <p>Fehlt der Abschnitt, gilt die Liste des Programms. Ist er LEER, gilt eine leere
     * Liste - und das ist etwas anderes: Wer alle Aufgaben herausnimmt, will keine, und
     * soll sie nicht aus der Vorgabe zurueckbekommen.
     */
    private static List<Aufgabenwerte.Aufgabe> liste(ConfigurationSection wurzel,
                                                     List<String> klagen,
                                                     List<String> bericht) {
        if (!wurzel.contains(Aufgabenwerte.PFAD_LISTE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Aufgabenwerte.PFAD_LISTE,
                    Aufgabenwerte.VORGABE_LISTE.size()
                            + " Herausforderungen des Programms"));
            return Aufgabenwerte.VORGABE_LISTE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Aufgabenwerte.PFAD_LISTE);
        if (abschnitt == null) {
            klagen.add(Aufgabenwerte.PFAD_LISTE + " ist keine Liste von"
                    + " Herausforderungen - verworfen. Es gilt die Liste des Programms.");
            return Aufgabenwerte.VORGABE_LISTE;
        }
        List<Aufgabenwerte.Aufgabe> liste = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Aufgabenwerte.Aufgabe aufgabe = aufgabe(abschnitt, name, klagen);
            if (aufgabe != null) {
                liste.add(aufgabe);
            }
        }
        if (liste.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            // JEDE Zeile war kaputt. Die Liste des Programms waere hier falsch: Der
            // Betreiber hat eine eigene gewollt, und mit der Vorgabe stuenden Aufgaben
            // da, die er gerade herausgenommen hat.
            bericht.add(Aufgabenwerte.PFAD_LISTE + ": keine einzige Herausforderung war"
                    + " brauchbar - jede Insel bleibt bis zur naechsten Berichtigung auf"
                    + " Stufe 0.");
        }
        return liste;
    }

    /** Eine Aufgabe - oder {@code null}, wenn sie wegfaellt. */
    private static Aufgabenwerte.Aufgabe aufgabe(ConfigurationSection abschnitt,
                                                 String name, List<String> klagen) {
        String pfad = Aufgabenwerte.PFAD_LISTE + "." + name;
        String klein = name.trim().toLowerCase(Locale.ROOT);
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
        if (eintrag == null) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + " - erwartet werden die Zeilen "
                    + Aufgabenwerte.SCHLUESSEL_VERLANGT + " und "
                    + Aufgabenwerte.SCHLUESSEL_BELOHNUNG + " darunter. Diese"
                    + " Herausforderung faellt weg, die uebrigen gelten.");
            return null;
        }
        Map<String, Integer> verlangt = verlangt(eintrag, pfad, klagen);
        if (verlangt == null) {
            return null;
        }
        Object rohLohn = eintrag.get(Aufgabenwerte.SCHLUESSEL_BELOHNUNG);
        long belohnung = 0L;
        if (rohLohn != null) {
            // Die Belohnung darf FEHLEN und heisst dann 0: eine Aufgabe nur fuer die
            // Stufe. Ein Wert, der keine Zahl ist, darf es nicht - sonst waere eine
            // vertippte Belohnung stillschweigend eine unbezahlte Aufgabe.
            if (!(rohLohn instanceof Number zahl)
                    || zahl.longValue() < 0L
                    || zahl.longValue() > Aufgabenwerte.HOECHSTE_BELOHNUNG
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
                klagen.add(pfad + "." + Aufgabenwerte.SCHLUESSEL_BELOHNUNG + " ist "
                        + Wirtschaftsliste.wieGetippt(rohLohn) + " - erwartet wird eine"
                        + " ganze Zahl von 0 bis " + Aufgabenwerte.HOECHSTE_BELOHNUNG
                        + " Mark. Diese Herausforderung faellt weg, die uebrigen"
                        + " gelten.");
                return null;
            }
            belohnung = zahl.longValue();
        }
        try {
            return new Aufgabenwerte.Aufgabe(klein, verlangt, belohnung);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add(pfad + ": " + unbrauchbar.getMessage()
                    + " Diese Herausforderung faellt weg, die uebrigen gelten.");
            return null;
        }
    }

    /** Die verlangten Gegenstaende - oder {@code null}, wenn die Aufgabe wegfaellt. */
    private static Map<String, Integer> verlangt(ConfigurationSection eintrag,
                                                 String pfad, List<String> klagen) {
        ConfigurationSection abschnitt =
                eintrag.getConfigurationSection(Aufgabenwerte.SCHLUESSEL_VERLANGT);
        if (abschnitt == null) {
            klagen.add(pfad + "." + Aufgabenwerte.SCHLUESSEL_VERLANGT + " ist "
                    + Wirtschaftsliste.wieGetippt(
                            eintrag.get(Aufgabenwerte.SCHLUESSEL_VERLANGT))
                    + " - erwartet wird je Zeile ein Gegenstand mit einer Anzahl. Diese"
                    + " Herausforderung faellt weg, die uebrigen gelten.");
            return null;
        }
        Map<String, Integer> gegenstaende = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                // GANZ WEG und nicht nur diese Zeile: Eine Aufgabe, der ein verlangter
                // Gegenstand fehlt, ist eine andere Aufgabe - und zwar eine leichtere.
                // Wer sich in einem Namen vertippt, soll die Aufgabe vermissen und nicht
                // einen Teil davon erfuellt bekommen.
                klagen.add(pfad + "." + Aufgabenwerte.SCHLUESSEL_VERLANGT + "." + name
                        + " " + warum + " - die ganze Herausforderung faellt weg, die"
                        + " uebrigen gelten.");
                return null;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > Aufgabenwerte.HOECHSTE_ANZAHL) {
                klagen.add(pfad + "." + Aufgabenwerte.SCHLUESSEL_VERLANGT + "." + name
                        + " ist " + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird"
                        + " eine Anzahl von 1 bis " + Aufgabenwerte.HOECHSTE_ANZAHL
                        + ". Die ganze Herausforderung faellt weg, die uebrigen gelten.");
                return null;
            }
            gegenstaende.put(klein, (int) zahl.longValue());
        }
        if (gegenstaende.isEmpty()) {
            klagen.add(pfad + "." + Aufgabenwerte.SCHLUESSEL_VERLANGT + " ist leer -"
                    + " eine Herausforderung, die nichts verlangt, waere Geld fuer das"
                    + " Tippen eines Befehls. Sie faellt weg, die uebrigen gelten.");
            return null;
        }
        return gegenstaende;
    }
}
