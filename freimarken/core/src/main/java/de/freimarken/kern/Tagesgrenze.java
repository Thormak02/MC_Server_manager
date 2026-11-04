package de.freimarken.kern;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die eine Stelle, an der aus einem Zeitpunkt ein TAG wird.
 *
 * <p>Jeder Zeitstempel im Plugin ist UTC, und die Datenbanksitzung steht fest auf UTC. Ein
 * Tag ist aber keine UTC-Groesse: Zwischen 00:00 und 01:00 oder 02:00 Ortszeit nennt UTC
 * noch den Vortag. Deshalb rechnet NUR Java den Tag, aus einem {@link Instant} und einer
 * {@link ZoneId}, und gibt ihn als Wert weiter. Kein {@code CURDATE()}, kein
 * {@code UTC_DATE()}, kein {@code NOW()} - MariaDB darf die Tagesgrenze nicht entscheiden,
 * sonst waere der eingestellte Wert stumm wirkungslos.
 *
 * <p>Statisch und mit der Zone als Parameter, nach dem Muster von
 * {@code Verlaufszeile.text(Buchung, ZoneId)} und {@link Sprungregel}: Die Zone steht in
 * {@link Zeitwerte} und wird beim Gebrauch von dort geholt. Eine zweite Zone im Programm
 * waere der belegte Weg, auf dem {@code /mark verlauf} eine andere Ortszeit zeigt als die
 * Tagesgrenze nimmt.
 *
 * <p>Gerechnet wird mit echten Zeitpunkten und nie mit 86.400 Sekunden je Tag. In der Nacht
 * der Zeitumstellung hat ein Tag 23 oder 25 Stunden; eine Rechnung mit festen Tageslaengen
 * verschiebt die Grenze dann um eine Stunde, und ein Tagesbonus griffe zweimal oder gar
 * nicht.
 */
public final class Tagesgrenze {

    /**
     * Was von einer Zeitspanne auf EINEN Kalendertag faellt.
     *
     * @param tag      der Tag in der eingestellten Zone
     * @param sekunden volle Sekunden, nie negativ und nie 0
     */
    public record Tagesanteil(LocalDate tag, long sekunden) {
    }

    /** Wie die Klartextzeile Datum und Uhrzeit schreibt - deutsch, wie im ganzen Plugin. */
    private static final DateTimeFormatter MUSTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);

    /** Die Laenge eines Tages ohne Umstellung - nur, um die Ausnahme zu erkennen. */
    private static final long STUNDEN_JE_TAG = 24L;

    private Tagesgrenze() {
    }

    /**
     * Der Kalendertag, auf den dieser Zeitpunkt in dieser Zone faellt.
     *
     * @throws IllegalArgumentException wenn Zeitpunkt oder Zone fehlen
     */
    public static LocalDate tagVon(Instant zeitpunkt, ZoneId zone) {
        pruefe(zeitpunkt, zone);
        return zeitpunkt.atZone(zone).toLocalDate();
    }

    /**
     * Der Augenblick, in dem dieser Tag in dieser Zone beginnt.
     *
     * <p>Ueber {@code atStartOfDay(zone)} und nicht ueber {@code atTime(0, 0)}: In einer Zone,
     * deren Umstellung auf Mitternacht faellt, gibt es 00:00 an einem Tag im Jahr nicht, und
     * dann ist der Tagesbeginn der erste Augenblick, den es gibt. Von Hand gerechnet waere das
     * eine Ausnahme, an die niemand denkt.
     */
    public static Instant tagesbeginn(LocalDate tag, ZoneId zone) {
        if (tag == null || zone == null) {
            throw new IllegalArgumentException("Tag und Zone sind Pflicht");
        }
        return tag.atStartOfDay(zone).toInstant();
    }

    /**
     * Teilt eine Zeitspanne in ihre Tagesanteile - die Regel, die ueber Mitternacht traegt.
     *
     * <p>Ein Zuschlag, der ueber eine Tagesgrenze liegt, wird GETEILT und nicht ganz dem
     * neuen Tag gegeben. Sonst zaehlte die letzte Minute vor Mitternacht zum naechsten Tag,
     * und jede Tagesrechnung - Tagesbonus, Verdienstdeckel, Ladenmiete - waere am
     * Tagesanfang um bis zu eine Taktlaenge falsch.
     *
     * <p>Zwei Zusagen, auf die sich der Aufrufer verlassen darf:
     * <ul>
     *   <li>Die SUMME der Anteile ist genau die verstrichene Zeit in ganzen Sekunden. Gerechnet
     *       wird deshalb ueber die aufgelaufene Zeit bis zu jeder Grenze und nicht Tag fuer Tag:
     *       Wer jeden Tagesanteil einzeln abrundet, verliert je Grenze bis zu eine Sekunde, und
     *       die fehlt dann in der Summe.</li>
     *   <li>Kein Anteil ist negativ oder 0. Liegt {@code bis} nicht nach {@code von}, ist die
     *       Liste leer - eine zurueckgestellte Uhr darf keine negative Spielzeit erzeugen.</li>
     * </ul>
     *
     * <p>Traegt ueber beliebig viele Tage am Stueck. Ein Server, der drei Tage ohne Takt lief,
     * ist ein Fehlerfall - aber die Regel darf daran nicht zerbrechen. Die Begrenzung auf eine
     * plausible Spanne ist Sache von {@link Spielzeitregel}, nicht dieser Rechnung.
     *
     * @param von  Anfang der Spanne
     * @param bis  Ende der Spanne, ausschliesslich
     * @param zone die Tagesgrenze
     * @return die Anteile in zeitlicher Reihenfolge, der erste Tag zuerst
     */
    public static List<Tagesanteil> teile(Instant von, Instant bis, ZoneId zone) {
        pruefe(von, zone);
        if (bis == null) {
            throw new IllegalArgumentException("Ohne Ende gibt es keine Spanne");
        }
        if (!bis.isAfter(von)) {
            return List.of();
        }
        // getSeconds() rundet eine positive Dauer ab - das ist gewollt: gezaehlt werden ganze
        // Sekunden, und der Rest bleibt an der Marke des Aufrufers stehen.
        long gesamt = Duration.between(von, bis).getSeconds();
        List<Tagesanteil> anteile = new ArrayList<>();
        LocalDate tag = tagVon(von, zone);
        long bisher = 0L;
        while (true) {
            Instant grenze = tagesbeginn(tag.plusDays(1), zone);
            if (!grenze.isBefore(bis)) {
                nenne(anteile, tag, gesamt - bisher);
                return List.copyOf(anteile);
            }
            long aufgelaufen = Duration.between(von, grenze).getSeconds();
            nenne(anteile, tag, aufgelaufen - bisher);
            bisher = aufgelaufen;
            tag = tag.plusDays(1);
        }
    }

    /**
     * Die Klartextzeile zur Tagesgrenze - die einzige Schranke gegen eine Zone, die gueltig
     * und trotzdem falsch ist.
     *
     * <p>Keine Pruefung faengt {@code UTC} statt {@code Europe/Berlin}: Beides sind gueltige
     * Zonennamen, und der Unterschied ist eine stumm verschobene Tagesgrenze. Wer im Protokoll
     * bei Nachmittagslicht "00:00 Ortszeit = 00:00 UTC" liest, sieht den Fehler. Dasselbe
     * Mittel wie {@code Preisstaffel.alsText}: Erkennung statt Verhinderung.
     *
     * <p>Am Tag einer Umstellung nennt dieselbe Zeile den 23- oder 25-Stunden-Tag. Wer dort
     * eine Rechnung mit 86.400 Sekunden gebaut hat, sieht an diesem Tag, dass sie nicht passt.
     */
    public static String tageswechselText(Instant jetzt, ZoneId zone) {
        pruefe(jetzt, zone);
        LocalDate heute = tagVon(jetzt, zone);
        Instant wechsel = tagesbeginn(heute.plusDays(1), zone);
        String text = "Tagesgrenze " + zone.getId() + " - naechster Tageswechsel "
                + MUSTER.format(wechsel.atZone(zone)) + " Ortszeit = "
                + MUSTER.format(wechsel.atZone(ZoneOffset.UTC)) + " UTC";
        long stunden = Duration.between(tagesbeginn(heute, zone), wechsel).toHours();
        if (stunden != STUNDEN_JE_TAG) {
            text = text + " - dieser Tag hat " + stunden + " Stunden (Zeitumstellung)";
        }
        return text;
    }

    /** Ein Anteil von 0 Sekunden waere eine Zeile ohne Inhalt - er wird weggelassen. */
    private static void nenne(List<Tagesanteil> hinein, LocalDate tag, long sekunden) {
        if (sekunden > 0L) {
            hinein.add(new Tagesanteil(tag, sekunden));
        }
    }

    private static void pruefe(Instant zeitpunkt, ZoneId zone) {
        if (zeitpunkt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keinen Tag");
        }
        if (zone == null) {
            throw new IllegalArgumentException("Ohne Zone gibt es keine Tagesgrenze");
        }
    }
}
