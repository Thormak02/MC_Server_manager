package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Die Teamrollen im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 * Derselbe Rueckfall, den {@link SpeicherKasse}, {@link SpeicherRaenge} und
 * {@link SpeicherLose} schon haben.
 *
 * <p><b>Beim Team ist das Vergessen besonders unangenehm</b>, und es steht darum hier:
 * Nach einem Neustart hat niemand mehr eine Rolle. Das faellt nicht auf wie ein fehlender
 * Kontostand - der Moderator meldet sich an, bekommt keine Rechte und haelt es fuer einen
 * Fehler des Plugins. Fuer einen Server mit Team ist die MariaDB-Fassung die einzige
 * richtige.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherLose}: Die Zuhoerer des
 * Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber ausdruecklich
 * nebenlaeufig - und die Vervollstaendigung von {@code /team nimm} liest genau diese
 * Karte.
 */
public final class SpeicherTeam implements Teamablage {

    /**
     * Je Spieler die Rollen mit ihrer Vergabe.
     *
     * <p>Zwei Karten uebereinander und nicht eine mit zusammengesetztem Schluessel wie
     * bei {@link SpeicherLose}: Dort wird immer nach EINEM Schluessel gefragt, hier auch
     * nach allen Rollen eines Spielers. Mit einem Textschluessel waere {@link #lies} eine
     * Suche durch die ganze Karte.
     */
    private final Map<UUID, Map<String, Teamstand.Zeile>> rollen = new LinkedHashMap<>();

    @Override
    public synchronized Teamstand lies(UUID spieler) {
        pruefe(spieler);
        Map<String, Teamstand.Zeile> seine = rollen.get(spieler);
        if (seine == null || seine.isEmpty()) {
            return Teamstand.leer(spieler);
        }
        return new Teamstand(spieler, new ArrayList<>(seine.values()));
    }

    @Override
    public synchronized boolean gib(UUID spieler, String rolle, Instant wann,
                                    UUID vonWem) {
        pruefe(spieler);
        String klein = geprueft(rolle);
        if (wann == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keine Vergabe -"
                    + " sie kommt von aussen und nicht aus der Ablage");
        }
        Map<String, Teamstand.Zeile> seine =
                rollen.computeIfAbsent(spieler, wer -> new LinkedHashMap<>());
        if (seine.containsKey(klein)) {
            // DIE ALTE ZEILE BLEIBT - siehe das zweite Versprechen in Teamablage. Ein
            // zweites Geben soll die Spur des ersten nicht ueberschreiben; sie ist die
            // Antwort auf "seit wann".
            return false;
        }
        seine.put(klein, new Teamstand.Zeile(klein, wann, vonWem));
        return true;
    }

    @Override
    public synchronized boolean nimm(UUID spieler, String rolle) {
        pruefe(spieler);
        String klein = geprueft(rolle);
        Map<String, Teamstand.Zeile> seine = rollen.get(spieler);
        if (seine == null) {
            return false;
        }
        boolean weg = seine.remove(klein) != null;
        if (seine.isEmpty()) {
            // Die leere Karte wegraeumen: Sonst zaehlt alle() einen Spieler mit, der
            // keine Rolle mehr hat - und dann steht er in der Teamliste, obwohl er nicht
            // im Team ist. Die MariaDB-Fassung hat dieses Problem nicht, weil dort die
            // letzte Zeile verschwindet; hier bliebe die Huelle stehen.
            rollen.remove(spieler);
        }
        return weg;
    }

    @Override
    public synchronized List<Teamstand> alle() {
        List<Teamstand> staende = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Teamstand.Zeile>> eintrag : rollen.entrySet()) {
            if (eintrag.getValue().isEmpty()) {
                continue;
            }
            staende.add(new Teamstand(eintrag.getKey(),
                    new ArrayList<>(eintrag.getValue().values())));
        }
        // Nach UUID sortiert, wie es die Schnittstelle zusagt: Eine LinkedHashMap gaebe
        // die Reihenfolge der ersten Vergabe zurueck, und die MariaDB-Fassung sortiert
        // nach ihrem Index. Eine Probe, die beide vergleicht, waere sonst mal gruen.
        staende.sort(Comparator.comparing(stand -> stand.spieler().toString()));
        return List.copyOf(staende);
    }

    private static void pruefe(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Teamrollen");
        }
    }

    /**
     * Der Rollenname, kleingeschrieben - und eine Klage, wenn er fehlt.
     *
     * <p>Normalisiert und nicht abgelehnt: Diese Fassung wird aus Proben gerufen, und ein
     * {@code "Moderator"} aus einer Probe soll dieselbe Rolle treffen wie ein
     * {@code "moderator"} aus dem Befehl. Was in der DATEI stehen darf, prueft
     * {@link Teamwerte} - und zwar streng, weil dort ein Satzpfad daran haengt.
     */
    private static String geprueft(String rolle) {
        if (rolle == null || rolle.isBlank()) {
            throw new IllegalArgumentException("Ohne Rolle gibt es nichts zu vergeben");
        }
        return rolle.trim().toLowerCase(Locale.ROOT);
    }
}
