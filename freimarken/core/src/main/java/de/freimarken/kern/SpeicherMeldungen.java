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
 * Die Meldungen im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 * Derselbe Rueckfall, den {@link SpeicherKasse}, {@link SpeicherTeam} und
 * {@link SpeicherLose} schon haben.
 *
 * <p><b>Beim Meldewesen ist das Vergessen schlimmer als bei den Teamrollen</b>, und es
 * steht darum hier: Eine Rolle kann ein Verwalter neu vergeben; eine verlorene Meldung
 * ist der Vorfall, den der Melder gesehen hat und der Moderator nie. Der Melder hat
 * gemeldet, nichts geschah, und er meldet nicht wieder - genau der Zustand, gegen den
 * Plan 12c.3 die Rueckmeldung gesetzt hat. Fuer einen Server mit Team ist die
 * MariaDB-Fassung die einzige richtige.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherTeam}: Die Zuhoerer des
 * Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber ausdruecklich
 * nebenlaeufig - und die Vervollstaendigung von {@code /meldung} liest die Nummern.
 */
public final class SpeicherMeldungen implements Meldeablage {

    /**
     * Die Meldungen nach Nummer, in der Reihenfolge ihres Entstehens.
     *
     * <p>{@link LinkedHashMap} und nicht {@code HashMap}: Die Reihenfolge des Entstehens
     * IST die Reihenfolge nach Alter, und {@link #offene} sagt die aeltesten zuerst zu.
     * Mit einer unsortierten Karte muesste jede Abfrage sortieren, und bei gleichen
     * Zeitstempeln - zwei Meldungen in derselben Millisekunde - waere die Reihenfolge
     * zufaellig.
     */
    private final Map<Long, Meldung> meldungen = new LinkedHashMap<>();

    /**
     * Die naechste Nummer.
     *
     * <p>Sie zaehlt nur hoch und wird nie wiederverwendet, auch wenn eine Meldung
     * verschwaende - anders als eine AUTO_INCREMENT-Spalte, die nach einem Neustart
     * wieder bei {@code MAX(nummer) + 1} anfaengt. Dieser Unterschied ist hier folgenlos,
     * weil es kein Loeschen gibt; er steht da, damit niemand ihn fuer eine Zusage haelt.
     */
    private long naechste = 1L;

    @Override
    public synchronized Meldung lege(UUID melder, UUID betroffener, String grund,
                                     String welt, int x, int y, int z, Instant jetzt) {
        // Die Pruefungen stehen in Meldung selbst - der Konstruktor wirft bei
        // Selbstmeldung, leerem Grund und fehlender Welt. Hier wird nichts zweimal
        // geprueft; ein zweiter Riegel waere einer, der sich nicht pruefen laesst.
        Meldung neue = Meldung.neu(naechste, melder, betroffener, grund, welt, x, y, z,
                jetzt);
        meldungen.put(neue.nummer(), neue);
        naechste++;
        return neue;
    }

    @Override
    public synchronized Optional<Meldung> lies(long nummer) {
        return Optional.ofNullable(meldungen.get(nummer));
    }

    @Override
    public synchronized boolean schreibe(Meldung geaendert, Meldestand erwartet) {
        if (geaendert == null) {
            throw new IllegalArgumentException("Nichts zu schreiben");
        }
        if (erwartet == null) {
            throw new IllegalArgumentException("Ohne erwarteten Stand gibt es kein"
                    + " Schreiben - sonst gewinnen zwei Moderatoren dieselbe Meldung");
        }
        Meldung jetzige = meldungen.get(geaendert.nummer());
        if (jetzige == null) {
            // Ein Wurf und kein false: false hiesse "versuch es noch einmal", und das
            // koennte nie gelingen. Das hier ist ein Rechenfehler im Aufrufer.
            throw new IllegalArgumentException("Die Meldung " + geaendert.nummer()
                    + " gibt es nicht");
        }
        if (jetzige.stand() != erwartet) {
            // DAS DRITTE VERSPRECHEN: Ein anderer war schneller. Der Aufrufer sagt das,
            // und beide Moderatoren wissen danach, wer sie hat.
            return false;
        }
        meldungen.put(geaendert.nummer(), geaendert);
        return true;
    }

    @Override
    public synchronized List<Meldung> offene(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Liste von " + hoechstens
                    + " Zeilen ist keine Liste");
        }
        List<Meldung> gefunden = new ArrayList<>();
        for (Meldung eine : meldungen.values()) {
            if (eine.stand().inBearbeitung()) {
                gefunden.add(eine);
            }
        }
        // Nach ZEITPUNKT und bei Gleichstand nach NUMMER: Zwei Meldungen in derselben
        // Millisekunde gibt es, und ohne den zweiten Schluessel waere ihre Reihenfolge
        // zwischen zwei Fassungen verschieden - die MariaDB-Fassung sortiert nach ihrem
        // Index, diese nach Einfuegereihenfolge.
        gefunden.sort(Comparator.comparing(Meldung::gemeldetAm)
                .thenComparingLong(Meldung::nummer));
        return List.copyOf(gefunden.subList(0, Math.min(hoechstens, gefunden.size())));
    }

    @Override
    public synchronized int offeneVon(UUID melder) {
        pruefe(melder);
        int gezaehlt = 0;
        for (Meldung eine : meldungen.values()) {
            if (eine.stand().inBearbeitung() && eine.gemeldetVon(melder)) {
                gezaehlt++;
            }
        }
        return gezaehlt;
    }

    @Override
    public synchronized Optional<Instant> letzteVon(UUID melder) {
        pruefe(melder);
        Instant juengste = null;
        for (Meldung eine : meldungen.values()) {
            if (!eine.gemeldetVon(melder)) {
                continue;
            }
            // Die JUENGSTE und nicht die letzte der Karte: Die Abklingzeit fragt, wann
            // zuletzt gemeldet wurde, und die Einfuegereihenfolge ist nicht die
            // Zeitreihenfolge, sobald eine Probe Zeitpunkte von aussen hereingibt.
            if (juengste == null || eine.gemeldetAm().isAfter(juengste)) {
                juengste = eine.gemeldetAm();
            }
        }
        return Optional.ofNullable(juengste);
    }

    @Override
    public synchronized List<Meldung> ungeleseneVon(UUID melder) {
        pruefe(melder);
        List<Meldung> gefunden = new ArrayList<>();
        for (Meldung eine : meldungen.values()) {
            if (eine.gemeldetVon(melder) && eine.warteteAufLesen()) {
                gefunden.add(eine);
            }
        }
        gefunden.sort(Comparator.comparing(Meldung::gemeldetAm)
                .thenComparingLong(Meldung::nummer));
        return List.copyOf(gefunden);
    }

    private static void pruefe(UUID melder) {
        if (melder == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Meldungen");
        }
    }
}
