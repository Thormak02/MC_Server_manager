package de.freimarken.paper;

import de.freimarken.kern.Wettbewerbswerte;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code wettbewerb:} aus der {@code config.yml} (Plan 14).
 *
 * <p>Dieselbe Bauart wie {@link Angelliste} und {@link Lotterieliste}: reine Dateiarbeit,
 * gesammelte Klagen statt Wuerfe, und eine kaputte ZEILE faellt weg, waehrend der Rest
 * gilt.
 *
 * <p><b>Hier wird die erste UHRZEIT dieses Projekts gelesen</b>, und das ist die Stelle,
 * an der am meisten schiefgehen kann:
 * <ul>
 *   <li><b>YAML liest {@code 18:00} NICHT als Text.</b> Ein unquotierter Doppelpunkt
 *       macht daraus eine Abbildung, und der Leser bekaeme eine {@code Map} statt einer
 *       Zeichenkette. Die ausgelieferte Datei schreibt die Zeit deshalb in
 *       Anfuehrungszeichen, und dieser Leser sagt es in seiner Klage - sonst sucht der
 *       Betreiber den Fehler in seiner Uhrzeit und nicht in den Zeichen darum.</li>
 *   <li><b>Die WOCHENTAGE heissen deutsch in der Datei</b> und englisch in Java. Die
 *       Umrechnung steht an EINER Stelle ({@link #WOCHENTAGE}); zwei Karten liefen
 *       auseinander, und dann waere ein Tag in der einen Richtung lesbar und in der
 *       anderen nicht.</li>
 * </ul>
 */
final class Wettbewerbsliste {

    /**
     * Die deutschen Namen der Wochentage - die EINE Umrechnung.
     *
     * <p>Deutsch in der Datei, weil der Betreiber deutsch tippt; englisch in Java, weil
     * {@link DayOfWeek} so heisst. Die Karte geht nur in eine Richtung und wird fuer die
     * andere durchlaufen: Zwei Karten waeren zwei Gelegenheiten, dass ein Tag in der
     * einen Richtung bekannt ist und in der anderen nicht.
     */
    private static final Map<String, DayOfWeek> WOCHENTAGE = Map.ofEntries(
            Map.entry("montag", DayOfWeek.MONDAY),
            Map.entry("dienstag", DayOfWeek.TUESDAY),
            Map.entry("mittwoch", DayOfWeek.WEDNESDAY),
            Map.entry("donnerstag", DayOfWeek.THURSDAY),
            Map.entry("freitag", DayOfWeek.FRIDAY),
            Map.entry("samstag", DayOfWeek.SATURDAY),
            Map.entry("sonntag", DayOfWeek.SUNDAY));

    /** Wie viele Punktzeilen der Bericht hoechstens einzeln nennt. */
    private static final int ZEILEN_IM_BERICHT = 10;

    /** Was im Abschnitt stand - und woran es gehapert hat. */
    record Ergebnis(Wettbewerbswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Wettbewerbsliste() {
    }

    /**
     * Der Angelwettbewerb aus dem Abschnitt {@code wettbewerb:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null || !wurzel.contains(Wettbewerbswerte.PFAD_AN, true)
                && !wurzel.contains(Wettbewerbswerte.PFAD_BEGINN, true)) {
            bericht.add("wettbewerb: fehlt in der config.yml - es gilt der ausgelieferte"
                    + " Angelwettbewerb des Programms.");
            return new Ergebnis(Wettbewerbswerte.VORGABE, klagen, bericht);
        }
        boolean an = jaNein(wurzel, klagen, bericht);
        LocalTime beginn = beginn(wurzel, klagen, bericht);
        Duration dauer = dauer(wurzel, klagen, bericht);
        Set<DayOfWeek> tage = tage(wurzel, klagen, bericht);
        long startgeld = Wirtschaftsliste.betrag(wurzel, Wettbewerbswerte.PFAD_STARTGELD,
                "wettbewerb-startgeld", Wettbewerbswerte.VORGABE_STARTGELD,
                Wettbewerbswerte.HOECHSTER_BETRAG, "Mark", klagen, bericht);
        long zuschuss = Wirtschaftsliste.betrag(wurzel, Wettbewerbswerte.PFAD_ZUSCHUSS,
                "wettbewerb-zuschuss", Wettbewerbswerte.VORGABE_ZUSCHUSS,
                Wettbewerbswerte.HOECHSTER_BETRAG, "Mark", klagen, bericht);
        List<Integer> verteilung = verteilung(wurzel, klagen, bericht);
        int jeFang = punkteJeFang(wurzel, klagen, bericht);
        Map<String, Integer> punkte = punkte(wurzel, klagen, bericht);

        if (jeFang == 0 && punkte.isEmpty()) {
            // BEIDE Wege aus: Dann zaehlt kein Fang, und der Wettbewerb lief leer. Das
            // ist kein Tippfehler, den man erraten kann - aber eine Lage, die der
            // Betreiber kennen soll, denn sein Fenster geht trotzdem auf.
            bericht.add("wettbewerb: punkte-je-fang ist 0 UND die Punktetabelle ist leer"
                    + " - damit zaehlt kein Fang, und jeder Lauf endet ohne Punkte.");
        }

        Wettbewerbswerte werte = new Wettbewerbswerte(an, beginn, dauer, tage, startgeld,
                zuschuss, verteilung, jeFang, punkte);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Der Wettbewerb im Klartext - die Rechenprobe.
     *
     * <p>Sie nennt das FENSTER mit seinem Ende, und das ist hier das Wichtigste: Aus
     * "18:00" und "60 Minuten" das Ende zu rechnen, ist eine Zeile, die niemand im Kopf
     * macht - und ein Fenster, das aus Versehen bis 4:00 des naechsten Tages reicht
     * (dauer-minuten: 600), sieht in der Datei aus wie eines von einer Stunde.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nimmt.
     */
    static String rechenprobe(Wettbewerbswerte werte) {
        if (!werte.greift()) {
            return "Angelwettbewerb: AUS - in Altfurt wird nicht gewertet.";
        }
        StringBuilder satz = new StringBuilder("Angelwettbewerb: ");
        satz.append(werte.beginn()).append(" bis ")
                .append(werte.beginn().plus(werte.dauer()))
                .append(werte.dauer().toMinutes() + werte.beginn().toSecondOfDay() / 60
                        > 24 * 60 ? " des naechsten Tages" : "")
                .append(" an ").append(tageAlsText(werte))
                .append(". Startgeld ").append(werte.startgeld())
                .append(", Zuschuss ").append(werte.zuschuss())
                .append(", Verteilung ").append(werte.verteilungProzent())
                .append(" Prozent. Punkte: je Fang ").append(werte.punkteJeFang());
        List<String> namen = new ArrayList<>(werte.punkte().keySet());
        namen.sort(null);
        for (int i = 0; i < namen.size() && i < ZEILEN_IM_BERICHT; i++) {
            satz.append(", ").append(namen.get(i)).append(' ')
                    .append(werte.punkte().get(namen.get(i)));
        }
        if (namen.size() > ZEILEN_IM_BERICHT) {
            satz.append(", und ").append(namen.size() - ZEILEN_IM_BERICHT)
                    .append(" weitere");
        }
        return satz.append('.').toString();
    }

    /** Die Wochentage in der Reihenfolge der Woche - nicht des Alphabets. */
    private static String tageAlsText(Wettbewerbswerte werte) {
        if (werte.tage().size() == 7) {
            return "jedem Tag";
        }
        List<String> namen = new ArrayList<>();
        for (DayOfWeek tag : DayOfWeek.values()) {
            if (werte.tage().contains(tag)) {
                namen.add(tag.name().toLowerCase(Locale.ROOT));
            }
        }
        return String.join("/", namen);
    }

    private static boolean jaNein(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        if (!wurzel.contains(Wettbewerbswerte.PFAD_AN, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wettbewerbswerte.PFAD_AN,
                    String.valueOf(Wettbewerbswerte.VORGABE.an())));
            return Wettbewerbswerte.VORGABE.an();
        }
        Object roh = wurzel.get(Wettbewerbswerte.PFAD_AN);
        if (!(roh instanceof Boolean wert)) {
            klagen.add(Wettbewerbswerte.PFAD_AN + " ist kein ja/nein ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(
                            String.valueOf(Wettbewerbswerte.VORGABE.an())));
            return Wettbewerbswerte.VORGABE.an();
        }
        return wert;
    }

    /**
     * Die Beginnzeit - und die Klage, die den YAML-Doppelpunkt benennt.
     *
     * <p>Ohne Anfuehrungszeichen liest YAML {@code 18:00} nicht als Text, sondern
     * versucht eine Abbildung daraus zu machen. Der Betreiber sieht dann eine Klage ueber
     * seine Uhrzeit und sucht den Fehler in der Zahl - er steht aber in den Zeichen darum.
     */
    private static LocalTime beginn(ConfigurationSection wurzel, List<String> klagen,
                                    List<String> bericht) {
        if (!wurzel.contains(Wettbewerbswerte.PFAD_BEGINN, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wettbewerbswerte.PFAD_BEGINN,
                    Wettbewerbswerte.VORGABE_BEGINN.toString()));
            return Wettbewerbswerte.VORGABE_BEGINN;
        }
        Object roh = wurzel.get(Wettbewerbswerte.PFAD_BEGINN);
        if (!(roh instanceof String wort)) {
            klagen.add(Wettbewerbswerte.PFAD_BEGINN + " ist keine Uhrzeit in"
                    + " Anfuehrungszeichen (" + Wirtschaftsliste.wieGetippt(roh)
                    + ") - verworfen. Erwartet wird \"18:00\" MIT den"
                    + " Anfuehrungszeichen: Ohne sie liest YAML den Doppelpunkt als"
                    + " Trenner und macht keine Uhrzeit daraus. "
                    + Wirtschaftsliste.esBleibt(
                            Wettbewerbswerte.VORGABE_BEGINN.toString()));
            return Wettbewerbswerte.VORGABE_BEGINN;
        }
        try {
            return LocalTime.parse(wort.trim());
        } catch (DateTimeParseException kaputt) {
            klagen.add(Wettbewerbswerte.PFAD_BEGINN + " ist \"" + wort + "\" und damit"
                    + " keine Uhrzeit - verworfen. Erwartet wird Stunde:Minute wie"
                    + " \"18:00\" oder \"07:30\", immer zweistellig. "
                    + Wirtschaftsliste.esBleibt(
                            Wettbewerbswerte.VORGABE_BEGINN.toString()));
            return Wettbewerbswerte.VORGABE_BEGINN;
        }
    }

    private static Duration dauer(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, Wettbewerbswerte.PFAD_DAUER,
                Wettbewerbswerte.VORGABE_DAUER.toMinutes(), klagen, bericht);
        if (gelesen == null) {
            return Wettbewerbswerte.VORGABE_DAUER;
        }
        long minuten = gelesen;
        long kleinste = Wettbewerbswerte.KUERZESTE_DAUER.toMinutes();
        long groesste = Wettbewerbswerte.LAENGSTE_DAUER.toMinutes();
        if (minuten < kleinste || minuten > groesste) {
            klagen.add(Wettbewerbswerte.PFAD_DAUER + " ist " + minuten + " und liegt"
                    + " nicht zwischen " + kleinste + " und " + groesste + " Minuten -"
                    + " verworfen. Kuerzer als der Takt der Spielzeitwache ginge das"
                    + " Fenster auf und zu, ohne dass ein Spieler es sieht; laenger als"
                    + " ein Tag ueberlappten sich zwei Laeufe desselben Wochentags. "
                    + Wirtschaftsliste.esBleibt(
                            String.valueOf(Wettbewerbswerte.VORGABE_DAUER.toMinutes())));
            return Wettbewerbswerte.VORGABE_DAUER;
        }
        return Duration.ofMinutes(minuten);
    }

    /**
     * Die Wochentage - jeder unbekannte Name faellt weg, die uebrigen gelten.
     *
     * <p>Eine LEERE Liste ist erlaubt und heisst: an keinem Tag. Das ist etwas anderes
     * als ein fehlender Schluessel (dann gelten alle sieben) - wer die Liste leert, will
     * den Wettbewerb anhalten, ohne den Schalter zu finden.
     */
    private static Set<DayOfWeek> tage(ConfigurationSection wurzel, List<String> klagen,
                                       List<String> bericht) {
        if (!wurzel.contains(Wettbewerbswerte.PFAD_TAGE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wettbewerbswerte.PFAD_TAGE,
                    "alle sieben Tage"));
            return Wettbewerbswerte.VORGABE_TAGE;
        }
        List<?> roh = wurzel.getList(Wettbewerbswerte.PFAD_TAGE);
        if (roh == null) {
            klagen.add(Wettbewerbswerte.PFAD_TAGE + " ist keine Liste von Wochentagen -"
                    + " verworfen. Erwartet wird [montag, dienstag, ...]. Es gelten alle"
                    + " sieben Tage.");
            return Wettbewerbswerte.VORGABE_TAGE;
        }
        Set<DayOfWeek> gefunden = new LinkedHashSet<>();
        for (Object eintrag : roh) {
            String name = String.valueOf(eintrag).trim().toLowerCase(Locale.ROOT);
            DayOfWeek tag = WOCHENTAGE.get(name);
            if (tag == null) {
                klagen.add(Wettbewerbswerte.PFAD_TAGE + ": \"" + eintrag + "\" ist kein"
                        + " Wochentag - erwartet wird montag, dienstag, mittwoch,"
                        + " donnerstag, freitag, samstag oder sonntag. Dieser Eintrag"
                        + " faellt weg, die uebrigen gelten.");
                continue;
            }
            gefunden.add(tag);
        }
        if (gefunden.isEmpty() && !roh.isEmpty()) {
            bericht.add("wettbewerb.tage: kein einziger Eintrag war ein Wochentag - bis"
                    + " zur naechsten Berichtigung findet kein Wettbewerb statt.");
        }
        return Set.copyOf(gefunden);
    }

    /** Die Verteilung auf die Plaetze - eine kaputte Zahl kostet die ganze Liste. */
    private static List<Integer> verteilung(ConfigurationSection wurzel,
                                            List<String> klagen, List<String> bericht) {
        if (!wurzel.contains(Wettbewerbswerte.PFAD_VERTEILUNG, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wettbewerbswerte.PFAD_VERTEILUNG,
                    Wettbewerbswerte.VORGABE_VERTEILUNG.toString() + " Prozent"));
            return Wettbewerbswerte.VORGABE_VERTEILUNG;
        }
        List<?> roh = wurzel.getList(Wettbewerbswerte.PFAD_VERTEILUNG);
        if (roh == null || roh.isEmpty()) {
            klagen.add(Wettbewerbswerte.PFAD_VERTEILUNG + " ist keine Liste von"
                    + " Prozentsaetzen - verworfen. Erwartet wird [50, 30, 20]. "
                    + Wirtschaftsliste.esBleibt(
                            Wettbewerbswerte.VORGABE_VERTEILUNG.toString()));
            return Wettbewerbswerte.VORGABE_VERTEILUNG;
        }
        List<Integer> anteile = new ArrayList<>();
        long summe = 0L;
        for (Object eintrag : roh) {
            if (!(eintrag instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > 100L) {
                // DIE GANZE LISTE, und das ist der Unterschied zur Punktetabelle: Die
                // Anteile haengen voneinander ab. Faellt der zweite weg, ruecken der
                // dritte und jeder weitere einen Platz vor - dann bekaeme der Dritte den
                // Preis des Zweiten, und niemand hat es gelesen.
                klagen.add(Wettbewerbswerte.PFAD_VERTEILUNG + ": \"" + eintrag + "\" ist"
                        + " kein Prozentsatz von 1 bis 100 - die GANZE Verteilung ist"
                        + " verworfen, denn ihre Plaetze haengen voneinander ab. "
                        + Wirtschaftsliste.esBleibt(
                                Wettbewerbswerte.VORGABE_VERTEILUNG.toString()));
                return Wettbewerbswerte.VORGABE_VERTEILUNG;
            }
            summe += zahl.longValue();
            anteile.add((int) zahl.longValue());
        }
        if (summe > 100L) {
            klagen.add(Wettbewerbswerte.PFAD_VERTEILUNG + " ergibt " + summe
                    + " Prozent und damit mehr als den Topf - verworfen. Weniger als 100"
                    + " ist erlaubt: Was uebrigbleibt, bleibt bei denen, die eingezahlt"
                    + " haben. " + Wirtschaftsliste.esBleibt(
                            Wettbewerbswerte.VORGABE_VERTEILUNG.toString()));
            return Wettbewerbswerte.VORGABE_VERTEILUNG;
        }
        return List.copyOf(anteile);
    }

    private static int punkteJeFang(ConfigurationSection wurzel, List<String> klagen,
                                    List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel,
                Wettbewerbswerte.PFAD_PUNKTE_JE_FANG,
                Wettbewerbswerte.VORGABE_PUNKTE_JE_FANG, klagen, bericht);
        if (gelesen == null) {
            return Wettbewerbswerte.VORGABE_PUNKTE_JE_FANG;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Wettbewerbswerte.HOECHSTE_PUNKTE) {
            klagen.add(Wettbewerbswerte.PFAD_PUNKTE_JE_FANG + " ist " + wert + " und"
                    + " liegt nicht zwischen 0 und " + Wettbewerbswerte.HOECHSTE_PUNKTE
                    + " - verworfen. Eine 0 ist erlaubt und heisst: nur die Faenge aus"
                    + " der Punktetabelle zaehlen. "
                    + Wirtschaftsliste.esBleibt(
                            String.valueOf(Wettbewerbswerte.VORGABE_PUNKTE_JE_FANG)));
            return Wettbewerbswerte.VORGABE_PUNKTE_JE_FANG;
        }
        return (int) wert;
    }

    /**
     * Die Punktetabelle - Zeile fuer Zeile geprueft, kaputte Zeilen beklagt.
     *
     * <p>Hier kostet eine kaputte Zeile NUR SICH SELBST, anders als bei der Verteilung:
     * Die Zeilen haengen nicht voneinander ab, und ein Fang ohne Zeile bekommt
     * {@code punkte-je-fang}.
     */
    private static Map<String, Integer> punkte(ConfigurationSection wurzel,
                                               List<String> klagen,
                                               List<String> bericht) {
        if (!wurzel.contains(Wettbewerbswerte.PFAD_PUNKTE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wettbewerbswerte.PFAD_PUNKTE,
                    Wettbewerbswerte.VORGABE_PUNKTE.size() + " Zeilen des Programms"));
            return Wettbewerbswerte.VORGABE_PUNKTE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Wettbewerbswerte.PFAD_PUNKTE);
        if (abschnitt == null) {
            klagen.add(Wettbewerbswerte.PFAD_PUNKTE + " ist keine Liste von"
                    + " Gegenstaenden mit Punkten - verworfen. Es gilt die Tabelle des"
                    + " Programms.");
            return Wettbewerbswerte.VORGABE_PUNKTE;
        }
        Map<String, Integer> tabelle = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                klagen.add(Wettbewerbswerte.PFAD_PUNKTE + "." + name + " " + warum
                        + " - diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > Wettbewerbswerte.HOECHSTE_PUNKTE) {
                klagen.add(Wettbewerbswerte.PFAD_PUNKTE + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet werden 1 bis "
                        + Wettbewerbswerte.HOECHSTE_PUNKTE + " Punkte. Eine 0 ist eine"
                        + " Zeile, die nichts tut; was nichts zaehlen soll, gehoert"
                        + " heraus und bekommt dann punkte-je-fang. Diese Zeile faellt"
                        + " weg, die uebrigen gelten.");
                continue;
            }
            tabelle.put(klein, (int) zahl.longValue());
        }
        return tabelle;
    }
}
