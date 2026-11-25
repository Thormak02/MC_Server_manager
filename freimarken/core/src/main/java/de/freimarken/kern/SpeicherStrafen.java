package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Strafen im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Derselbe Rueckfall wie {@link SpeicherMeldungen} und
 * {@link SpeicherTeam}, und hier ist das Vergessen die SCHLIMMSTE Richtung von allen:
 * <b>Ein Neustart entsperrt jeden gebannten Spieler.</b> Ein Server, der einen
 * Wiederholungstaeter dauerhaft bannt und ihn am naechsten Morgen wieder hereinlaesst,
 * hat keine Moderation - er hat eine Geste. Und niemand merkt es, denn der Bann war ja
 * erfolgreich.
 *
 * <p>Darum ist diese Fassung ausdruecklich die fuer Proben. Fuer einen Server mit Team ist
 * die MariaDB-Fassung die einzige richtige, und {@code Strafliste} sagt das beim Start.
 *
 * <p>{@code synchronized} auf jeder Methode, wie bei den Geschwistern: Die Zuhoerer des
 * Plugins laufen im Hauptfaden, die Beitrittswache aber im NETZFADEN
 * ({@code AsyncPlayerPreLoginEvent}) - sie fragt {@link #geltende}, waehrend ein
 * Moderator im Spiel eine Strafe verhaengt.
 */
public final class SpeicherStrafen implements Strafablage {

    /**
     * Die Strafen nach Nummer, in der Reihenfolge ihres Entstehens.
     *
     * <p>{@link LinkedHashMap}, damit die Reihenfolge bei gleichen Zeitstempeln
     * festliegt - zwei Strafen in derselben Millisekunde gibt es, sobald eine Probe die
     * Zeit von aussen hereingibt.
     */
    private final Map<Long, Strafe> strafen = new LinkedHashMap<>();

    /** Die naechste Nummer - zaehlt nur hoch, wie bei {@link SpeicherMeldungen}. */
    private long naechste = 1L;

    @Override
    public synchronized Strafe lege(UUID betroffener, Strafart art, String grund,
                                    UUID vonWem, Instant verhaengtAm, Strafdauer dauer) {
        // Die Pruefungen stehen in Strafe.neu - Art gegen Dauer in beide Richtungen,
        // Grund gegen die Grenzen. Hier wird nichts zweimal geprueft.
        Strafe neue = Strafe.neu(naechste, betroffener, art, grund, vonWem, verhaengtAm,
                dauer);
        strafen.put(neue.nummer(), neue);
        naechste++;
        return neue;
    }

    @Override
    public synchronized Optional<Strafe> lies(long nummer) {
        return Optional.ofNullable(strafen.get(nummer));
    }

    @Override
    public synchronized boolean hebeAuf(Strafe geaendert) {
        if (geaendert == null) {
            throw new IllegalArgumentException("Nichts zu schreiben");
        }
        if (!geaendert.istAufgehoben()) {
            // Ein Wurf und kein false: Eine Fassung, die nicht aufgehoben ist, hier
            // hereinzugeben, ist ein Rechenfehler im Aufrufer - und ohne diesen Riegel
            // waere hebeAuf ein allgemeines Schreiben, mit dem sich der Grund einer
            // stehenden Strafe nachtraeglich aendern liesse.
            throw new IllegalArgumentException("Die Fassung der Strafe "
                    + geaendert.nummer() + " ist nicht aufgehoben - hebeAuf schreibt"
                    + " nur Aufhebungen");
        }
        Strafe jetzige = strafen.get(geaendert.nummer());
        if (jetzige == null) {
            throw new IllegalArgumentException("Die Strafe " + geaendert.nummer()
                    + " gibt es nicht");
        }
        if (jetzige.istAufgehoben()) {
            // DAS DRITTE VERSPRECHEN: Ein anderer war schneller. Sonst stehen zwei
            // Aufhebungsgruende uebereinander, und der erste ist verloren.
            return false;
        }
        strafen.put(geaendert.nummer(), geaendert);
        return true;
    }

    @Override
    public synchronized Optional<Strafe> geltende(UUID betroffener, Strafart art,
                                                  Instant jetzt) {
        pruefe(betroffener, art);
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, was"
                    + " noch gilt");
        }
        Strafe laengste = null;
        for (Strafe eine : strafen.values()) {
            if (eine.art() != art || !eine.betroffener().equals(betroffener)) {
                continue;
            }
            if (!eine.giltNoch(jetzt)) {
                continue;
            }
            // DIE LAENGSTE und nicht die erste: Gibt es wider Erwarten zwei geltende,
            // ist die laengere die Wahrheit. Eine unbegrenzte schlaegt jede befristete -
            // sie hat kein Ende, mit dem sie sich vergleichen liesse.
            if (laengste == null || besser(eine, laengste)) {
                laengste = eine;
            }
        }
        return Optional.ofNullable(laengste);
    }

    /** Ob die erste Strafe laenger gilt als die zweite. */
    private static boolean besser(Strafe eine, Strafe andere) {
        if (eine.unbegrenzt()) {
            return true;
        }
        if (andere.unbegrenzt()) {
            return false;
        }
        return eine.laeuftAbAm().isAfter(andere.laeuftAbAm());
    }

    @Override
    public synchronized List<Strafe> von(UUID betroffener, int hoechstens) {
        if (betroffener == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Strafen");
        }
        pruefeZahl(hoechstens);
        List<Strafe> gefunden = new ArrayList<>();
        for (Strafe eine : strafen.values()) {
            if (eine.betroffener().equals(betroffener)) {
                gefunden.add(eine);
            }
        }
        return sortiertUndBegrenzt(gefunden, hoechstens);
    }

    @Override
    public synchronized List<Strafe> geltendeAlle(Strafart art, int hoechstens,
                                                  Instant jetzt) {
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Liste");
        }
        pruefeZahl(hoechstens);
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, was"
                    + " noch gilt");
        }
        List<Strafe> gefunden = new ArrayList<>();
        for (Strafe eine : strafen.values()) {
            if (eine.art() == art && eine.giltNoch(jetzt)) {
                gefunden.add(eine);
            }
        }
        return sortiertUndBegrenzt(gefunden, hoechstens);
    }

    @Override
    public synchronized int zaehle(UUID betroffener, Strafart art) {
        pruefe(betroffener, art);
        int gezaehlt = 0;
        for (Strafe eine : strafen.values()) {
            if (eine.art() == art && eine.betroffener().equals(betroffener)) {
                gezaehlt++;
            }
        }
        return gezaehlt;
    }

    /**
     * Die neuesten zuerst, hoechstens so viele.
     *
     * <p>Nach ZEITPUNKT absteigend und bei Gleichstand nach NUMMER absteigend - dieselbe
     * Reihenfolge, die die MariaDB-Fassung in ihrem {@code ORDER BY} zusagt. Ohne den
     * zweiten Schluessel waere die Reihenfolge zweier Strafen aus derselben Millisekunde
     * zwischen den Fassungen verschieden, und der Vertrag koennte sie nicht pruefen.
     */
    private static List<Strafe> sortiertUndBegrenzt(List<Strafe> gefunden,
                                                    int hoechstens) {
        gefunden.sort(Comparator.comparing(Strafe::verhaengtAm)
                .thenComparingLong(Strafe::nummer).reversed());
        return List.copyOf(gefunden.subList(0, Math.min(hoechstens, gefunden.size())));
    }

    private static void pruefe(UUID betroffener, Strafart art) {
        if (betroffener == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Strafen");
        }
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Frage");
        }
    }

    private static void pruefeZahl(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Liste von " + hoechstens
                    + " Zeilen ist keine Liste");
        }
    }
}
