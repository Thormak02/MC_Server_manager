package de.freimarken.paper;

import de.freimarken.kern.Teamwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code team:} - die Teamrollen (Plan 12c.2, Schritt 16a Teil A).
 *
 * <p>Ein eigener Leser wie {@link Rangliste} und aus demselben Grund: Hier wird eine
 * LISTE von Eintraegen gelesen, von denen jeder eine eigene Rechteliste traegt.
 *
 * <p><b>Die Reihenfolge der Datei bleibt, bedeutet aber nichts.</b> Das ist der
 * Unterschied zur Rangleiter: Dort IST die Reihenfolge die Regel, hier ist sie nur die
 * Reihenfolge, in der die Rollen im Startbericht und in {@code /team rollen} stehen. Ein
 * Betreiber, der seine Rollen umsortiert, aendert damit nichts an den Rechten.
 *
 * <p><b>Eine kaputte Rolle faellt GANZ weg</b>, nicht nur ihre kaputte Zeile - dieselbe
 * Linie wie bei einer Rangstufe, und mit derselben Begruendung: Eine Rolle, der eine
 * Rechtezeile fehlt, ist nicht dieselbe Rolle mit einem Fehler, sondern eine SCHWAECHERE.
 * Wer sie hat, duerfte weniger als eingestellt - und das sieht im Spiel aus wie ein
 * Fehler des Plugins, nicht wie eine Zeile in einer Datei. Eine weggefallene Rolle nimmt
 * dagegen niemandem etwas, was er schon hat: Die Vergabe bleibt in der Ablage stehen und
 * wirkt wieder, sobald der Betreiber die Zeile berichtigt.
 *
 * <p><b>Ein FEHLENDER Abschnitt und ein LEERER sind zwei verschiedene Dinge</b>, und das
 * ist hier wichtiger als bei den Raengen. Fehlt {@code team:} ganz, gilt die Vorgabe des
 * Programms - das ist der Server, auf dem noch niemand an die Datei gedacht hat, und er
 * soll seine fuenf Rollen haben. Steht {@code team:} dort und {@code rollen:} ist leer,
 * hat der Betreiber sich entschieden: kein Team. Wer beides gleich behandelte, gaebe dem
 * zweiten die Vorgabe zurueck - also fuenf Rollen, die er ausdruecklich abgeschafft hat.
 *
 * <p><b>Unterschieden wird am ELTERNKNOTEN {@code team:} und nicht an
 * {@code team.rollen}</b>, und das ist eine Messung und keine Vorliebe: Bukkit gibt fuer
 * einen Schluessel ohne Wert dasselbe zurueck wie fuer einen fehlenden. Ein
 * {@code rollen:} ohne etwas darunter kommt beim Leser also nie an - siehe
 * {@link #ABSCHNITT}.
 */
final class Teamliste {

    /** Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL = List.of(Teamwerte.PFAD_ROLLEN);

    /**
     * Der Abschnitt ueber {@code rollen:} - abgeleitet und nicht abgeschrieben.
     *
     * <p><b>Er wird gebraucht, weil Bukkit einen LEEREN Schluessel nicht von einem
     * fehlenden unterscheidet</b> [gemessen am 25.11.2026]: Fuer {@code rollen:} ohne
     * etwas darunter gibt {@code contains("team.rollen", true)} schon {@code false}, und
     * der Leser kaeme in denselben Zweig wie bei einer Datei ganz ohne {@code team:}.
     * Damit bekaeme ein Betreiber, der seine Rollen ABGESCHAFFT hat, die fuenf des
     * Programms zurueck - das Gegenteil dessen, was er getippt hat.
     *
     * <p>Unterschieden wird darum am ELTERNKNOTEN: Wer {@code team:} in seiner Datei hat,
     * hat sich mit den Rollen befasst; wer ihn gar nicht hat, hat nicht daran gedacht.
     * Abgeleitet aus {@link Teamwerte#PFAD_ROLLEN}, damit es EINE Quelle bleibt - zwei
     * Abschriften von "team" liefen eines Tages auseinander.
     */
    private static final String ABSCHNITT =
            Teamwerte.PFAD_ROLLEN.substring(0, Teamwerte.PFAD_ROLLEN.indexOf('.'));

    record Ergebnis(Teamwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Teamliste() {
    }

    /**
     * Der Abschnitt {@code team:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null || !wurzel.contains(ABSCHNITT, true)) {
            // FEHLT der Abschnitt team: ganz, gilt die Vorgabe - siehe den Klassenkopf.
            // Gefragt wird der ELTERNKNOTEN und nicht team.rollen: Bukkit unterscheidet
            // einen leeren Schluessel nicht von einem fehlenden, siehe ABSCHNITT.
            return fertig(Teamwerte.VORGABE, klagen, bericht);
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Teamwerte.PFAD_ROLLEN);
        if (abschnitt == null) {
            Object roh = wurzel.get(Teamwerte.PFAD_ROLLEN);
            if (roh == null) {
                // team: ist da, rollen: fehlt oder steht leer da. Das ist der Fall "kein
                // Team": Wer den Abschnitt in seiner Datei hat, hat sich mit den Rollen
                // befasst. Eine Klage waere hier falsch, und die Vorgabe waere es noch
                // mehr - sie gaebe ihm fuenf Rollen zurueck, die er abgeschafft hat.
                return fertig(new Teamwerte(List.of()), klagen, bericht);
            }
            klagen.add(Teamwerte.PFAD_ROLLEN + " ist " + Wirtschaftsliste.wieGetippt(roh)
                    + " - erwartet wird je Rolle ein Name mit der Zeile "
                    + Teamwerte.SCHLUESSEL_RECHTE + " darunter. "
                    + Wirtschaftsliste.esBleibt(Teamwerte.VORGABE_ROLLEN.size()
                            + " Rollen des Programms"));
            return fertig(Teamwerte.VORGABE, klagen, bericht);
        }
        List<Teamwerte.Rolle> rollen = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Teamwerte.Rolle rolle = rolle(abschnitt, name, klagen);
            if (rolle != null) {
                rollen.add(rolle);
            }
        }
        if (rollen.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            bericht.add(Teamwerte.PFAD_ROLLEN + ": keine einzige Rolle war brauchbar -"
                    + " bis zur naechsten Berichtigung hat dieser Server kein Team. Was"
                    + " schon vergeben ist, bleibt in der Datenbank stehen und wirkt"
                    + " wieder, sobald die Zeilen stimmen.");
        }
        try {
            return fertig(new Teamwerte(rollen), klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt aus einer Zusage, die der Leser nicht selbst prueft - heute nur die
            // Hoechstzahl, denn Doppelte kann ein YAML-Abschnitt nicht haben.
            klagen.add(Teamwerte.PFAD_ROLLEN + ": " + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt(Teamwerte.VORGABE_ROLLEN.size()
                            + " Rollen des Programms"));
            return fertig(Teamwerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Das Ergebnis - und JEDER Rueckweg geht hier durch.
     *
     * <p><b>Weil die Ungereimtheiten sonst nur die halbe Zeit gemeldet werden</b>
     * [gemessen am 25.11.2026 am Startbericht des Probeservers]: Der Bericht entstand
     * vorher erst im letzten Zweig. Ein Server ohne {@code team:}-Abschnitt bekam damit
     * die Vorgabe des Programms OHNE ihre Meldung - und das ist genau der Server, auf dem
     * noch niemand an die Datei gedacht hat, also der, dem die Auskunft am meisten hilft.
     *
     * <p>Das ist dieselbe Klasse Fehler, die in diesem Projekt schon einmal zugeschlagen
     * hat: Ein frueher Rueckweg bei fehlendem Abschnitt ueberspringt die spaeteren
     * Pruefungen, und der WERKSWERT laeuft dann durch andere Riegel als ein eingetragener.
     * Rot wird dabei nichts - die Zeile fehlt einfach.
     */
    private static Ergebnis fertig(Teamwerte werte, List<String> klagen,
                                   List<String> bericht) {
        // Keine Klage, sondern ein Bericht: Alles davon ist erlaubt und fast immer ein
        // Vertipper - im Spiel faellt keines davon auf.
        bericht.addAll(werte.ungereimtheiten());
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Rechte, die eine Rolle vergibt, die es im Plugin aber gar nicht gibt.
     *
     * <p>Getrennt von {@link #ausKonfiguration}, wie bei
     * {@link Rangliste#unbekannteRechte}: Nur ein LAUFENDER Server weiss, welche Rechte
     * angemeldet sind, und der Leser selbst soll ohne Server pruefbar bleiben. Ein
     * vertipptes {@code blockbuchh} faellt sonst nirgends auf - das Anhaengsel setzt es
     * klaglos, und es fragt es nie jemand.
     *
     * @param angemeldet die Namen der angemeldeten Rechte, MIT Vorsilbe
     */
    static List<String> unbekannteRechte(Teamwerte werte, Set<String> angemeldet) {
        if (werte == null || angemeldet == null) {
            return List.of();
        }
        Set<String> klein = new TreeSet<>();
        for (String name : angemeldet) {
            if (name != null) {
                klein.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> unbekannt = new ArrayList<>();
        for (Teamwerte.Rolle rolle : werte.rollen()) {
            for (String recht : rolle.rechte()) {
                if (!klein.contains(Teamwerte.VORSILBE + recht)) {
                    unbekannt.add(Teamwerte.PFAD_ROLLEN + "." + rolle.schluessel() + "."
                            + Teamwerte.SCHLUESSEL_RECHTE + ": " + Teamwerte.VORSILBE
                            + recht + " ist kein Recht dieses Plugins - die Rolle vergibt"
                            + " es, und niemand fragt danach.");
                }
            }
        }
        return List.copyOf(unbekannt);
    }

    /**
     * Die Rollen im Klartext - die Rechenprobe fuer den Startbericht.
     *
     * <p>Je Rolle eine Zeile mit der ZAHL ihrer Rechte und nicht mit ihren Namen: Eine
     * Rolle hat bis zu einem Dutzend, und eine Zeile, die alle nennt, liest niemand mehr.
     * Die Zahl genuegt fuer die Frage, die ein Betreiber beim Start wirklich hat - ist
     * meine Datei angekommen oder gilt die Vorgabe?
     */
    static String rechenprobe(Teamwerte werte) {
        if (werte == null || !werte.greift()) {
            return "Teamrollen: keine - auf diesem Server gibt es kein Team. Was in der"
                    + " Datenbank steht, wirkt nicht, bleibt aber stehen.";
        }
        List<String> teile = new ArrayList<>();
        for (Teamwerte.Rolle rolle : werte.rollen()) {
            teile.add(rolle.schluessel() + " (" + rolle.rechte().size() + ")");
        }
        return "Teamrollen: " + String.join(", ", teile)
                + " - in Klammern, wie viele Rechte jede vergibt.";
    }

    /** Eine Rolle - oder {@code null}, wenn sie ganz wegfaellt. */
    private static Teamwerte.Rolle rolle(ConfigurationSection abschnitt, String name,
                                         List<String> klagen) {
        String pfad = Teamwerte.PFAD_ROLLEN + "." + name;
        String klein = name.trim().toLowerCase(Locale.ROOT);
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
        if (eintrag == null) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + " - erwartet wird die Zeile " + Teamwerte.SCHLUESSEL_RECHTE
                    + " darunter. Diese Rolle faellt weg.");
            return null;
        }
        List<String> rechte = rechte(eintrag, pfad, klagen);
        if (rechte == null) {
            return null;
        }
        try {
            return new Teamwerte.Rolle(klein, rechte);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add(pfad + ": " + unbrauchbar.getMessage() + " Diese Rolle faellt"
                    + " weg.");
            return null;
        }
    }

    /**
     * Die Rechte - oder {@code null}, wenn die Rolle wegfaellt.
     *
     * <p>Der Abschnitt darf FEHLEN und heisst dann: eine Rolle, die nichts gibt. Das ist
     * erlaubt, weil ein Betreiber eine Rolle vorbereiten darf, deren Werkzeuge noch nicht
     * gebaut sind - gemeldet wird sie von {@code Teamwerte.ungereimtheiten()}.
     */
    private static List<String> rechte(ConfigurationSection eintrag, String pfad,
                                       List<String> klagen) {
        if (!eintrag.contains(Teamwerte.SCHLUESSEL_RECHTE, true)) {
            return new ArrayList<>();
        }
        Object roh = eintrag.get(Teamwerte.SCHLUESSEL_RECHTE);
        if (!(roh instanceof List<?> zeilen)) {
            klagen.add(pfad + "." + Teamwerte.SCHLUESSEL_RECHTE + " ist "
                    + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine Liste von"
                    + " Rechten, je Zeile eines mit einem Strich davor. Diese Rolle"
                    + " faellt weg.");
            return null;
        }
        List<String> rechte = new ArrayList<>();
        for (Object zeile : zeilen) {
            if (!(zeile instanceof String name) || name.isBlank()) {
                klagen.add(pfad + "." + Teamwerte.SCHLUESSEL_RECHTE + " enthaelt "
                        + Wirtschaftsliste.wieGetippt(zeile) + " - erwartet wird der Name"
                        + " eines Rechtes ohne die Vorsilbe " + Teamwerte.VORSILBE
                        + ". Diese Rolle faellt weg.");
                return null;
            }
            rechte.add(name);
        }
        return rechte;
    }
}
