package de.freimarken.kern;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Zwischenspeicher vor einem anderen Lager.
 *
 * <p>Gebraucht wird er, weil {@link Besitzbuch#darfBauen} bei JEDEM gesetzten und
 * abgebauten Block laeuft. Eine Datenbankabfrage je Block waere selbst bei einer lokalen
 * Datenbank spuerbar - und bei einem Spieler mit Flaechenwerkzeug unertraeglich.
 *
 * <p>Die Regel aus dem Plan bleibt dabei unangetastet: <em>Die Datenbank ist die Wahrheit,
 * der Speicher nur ein Zwischenspeicher mit Verfall.</em> Deshalb zwei Vorkehrungen:
 *
 * <ul>
 *   <li><b>Jede Schreiboperation leert den ganzen Zwischenspeicher.</b> Grob, aber
 *       verlaesslich - feingliedriges Ungueltigmachen ist die Stelle, an der sich
 *       Fehler verstecken, und Schreibvorgaenge sind selten.
 *   <li><b>Jeder Eintrag verfaellt nach kurzer Zeit.</b> Kommt spaeter ein zweiter Server
 *       dazu, sieht dieser hier eine fremde Aenderung spaetestens nach der Verfallszeit.
 *       Ohne Verfall waere der Zwischenspeicher dann dauerhaft falsch.
 * </ul>
 */
public final class ZwischenspeicherLager implements BesitzLager {

    /** Wie lange ein gelesener Eintrag gilt. Kurz genug fuer einen zweiten Server. */
    public static final Duration VERFALL = Duration.ofSeconds(30);

    /** Ab so vielen Eintraegen wird beim Einfuegen nach Abgelaufenem gesucht. */
    private static final int HOECHSTZAHL = 20_000;

    private record Eintrag(Optional<Parzelle> parzelle, Instant bis) {
    }

    private final BesitzLager wahrheit;
    private final Clock uhr;
    private final Duration verfall;
    private final Map<String, Eintrag> speicher = new ConcurrentHashMap<>();

    public ZwischenspeicherLager(BesitzLager wahrheit) {
        this(wahrheit, Clock.systemUTC(), VERFALL);
    }

    /** Mit eigener Uhr und Verfallszeit - damit Tests den Verfall ohne Warten pruefen. */
    public ZwischenspeicherLager(BesitzLager wahrheit, Clock uhr, Duration verfall) {
        this.wahrheit = wahrheit;
        this.uhr = uhr;
        this.verfall = verfall;
    }

    private static String ort(String welt, int x, int z) {
        return welt + "/" + x + "/" + z;
    }

    @Override
    public Optional<Parzelle> finde(String welt, int x, int z) {
        String schluessel = ort(welt, x, z);
        Eintrag vorhanden = speicher.get(schluessel);
        if (vorhanden != null && uhr.instant().isBefore(vorhanden.bis())) {
            return vorhanden.parzelle();
        }
        if (speicher.size() >= HOECHSTZAHL) {
            raeumeVerfallenes();
        }
        Optional<Parzelle> frisch = wahrheit.finde(welt, x, z);
        speicher.put(schluessel, new Eintrag(frisch, uhr.instant().plus(verfall)));
        return frisch;
    }

    // Die uebrigen Lesewege gehen absichtlich ungepuffert durch: Sie laufen bei Befehlen,
    // nicht bei jedem Block, und eine falsche Parzellenzahl waere teurer als die Abfrage.

    @Override
    public List<Parzelle> vonSpieler(String welt, UUID spieler) {
        return wahrheit.vonSpieler(welt, spieler);
    }

    @Override
    public int anzahlVon(String welt, UUID spieler) {
        return wahrheit.anzahlVon(welt, spieler);
    }

    @Override
    public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
        Parzelle neu = wahrheit.anlegen(welt, x, z, eigentuemer, preis);
        leeren();
        return neu;
    }

    @Override
    public Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer) {
        Parzelle neu = wahrheit.mitbauerSetzen(id, mitbauer);
        leeren();
        return neu;
    }

    @Override
    public void entfernen(long id) {
        wahrheit.entfernen(id);
        leeren();
    }

    /**
     * Wirft abgelaufene Eintraege weg.
     *
     * <p>Sonst wird nur in {@link #leeren()} entfernt, also erst bei einem Schreibvorgang.
     * Ein Weglaufen ist das nicht: Der Schluessel ist die Parzellennummer und nicht der
     * Block, also wird der Speicher hoechstens so gross wie die Zahl der je angesehenen
     * Parzellen - in der Marktstadt gut tausend. Die Grenze ist Vorsorge fuer den Tag, an
     * dem mehrere Welten dazukommen, und wird im normalen Betrieb nie erreicht.
     *
     * <p>Aufgeraeumt wird nur beim Einfuegen und nur oberhalb der Grenze, nicht bei jedem
     * Nachsehen: Ein Durchlauf ueber alles bei jeder Abfrage waere teurer als der
     * Zwischenspeicher einbringt.
     */
    private void raeumeVerfallenes() {
        Instant jetzt = uhr.instant();
        speicher.values().removeIf(eintrag -> !jetzt.isBefore(eintrag.bis()));
    }

    /** Alles vergessen. Nach jeder Aenderung und bei Bedarf von aussen. */
    public void leeren() {
        speicher.clear();
    }

    /** Wie viele Eintraege gerade gehalten werden - fuer Tests und Anzeige. */
    public int eintraege() {
        return speicher.size();
    }
}
