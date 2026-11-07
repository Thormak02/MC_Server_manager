package de.freimarken.paper;

import de.freimarken.kern.Lebenszeichen;
import de.freimarken.kern.Messregel;
import de.freimarken.kern.Messwerte;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Messstelle aus der {@code config.yml} - die Arten, die Zahl und die Strecke.
 *
 * <p>Dieselbe Bauart wie {@link Marktliste} und {@link Verdienstliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen gelassen.
 *
 * <p><b>Die Reihenfolge der zwei Lesungen ist nicht beliebig.</b> Erst die Artenliste, dann
 * die verlangte Zahl: Die Zahl wird gegen die LAENGE der Liste geprueft, und wer das
 * umdreht, prueft gegen die Vorgabe des Programms statt gegen die Datei des Betreibers.
 * Die Regel selbst steht im Kern ({@link Messregel#brauchbareArtenzahl}) und nicht hier -
 * es gibt zwei Wege zu diesem Paar, diesen Leser und den Konstruktor von
 * {@link Messwerte}.
 */
final class Messliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Messwerte.PFAD_VERLANGTE_ARTEN, Messwerte.PFAD_STRECKE, Messwerte.PFAD_ARTEN);

    record Ergebnis(Messwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Messliste() {
    }

    /**
     * Der ganze Abschnitt {@code messstelle:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Messwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        Set<Lebenszeichen> arten = arten(wurzel, klagen, bericht);
        int verlangt = verlangteArten(wurzel, arten.size(), klagen, bericht);
        int strecke = strecke(wurzel, klagen, bericht);
        return new Ergebnis(new Messwerte(verlangt, strecke, arten), klagen, bericht);
    }

    /**
     * Welche Arten zaehlen.
     *
     * <p>Eine LEERE Liste ist hier - anders als beim Saatgut und bei den Spawngruenden -
     * nicht erlaubt und faellt auf die Vorgabe zurueck. Dort heisst leer etwas Sinnvolles
     * ("kein Wesen zahlt"), hier hiesse es "kein Taktschlag ist jemals taetig", und das
     * waere der stumme Ausschalter, den {@link Messwerte} ausdruecklich nicht haben soll.
     * Wer milde messen will, setzt {@link Messwerte#PFAD_VERLANGTE_ARTEN} auf 1.
     */
    private static Set<Lebenszeichen> arten(ConfigurationSection wurzel,
                                            List<String> klagen, List<String> bericht) {
        String pfad = Messwerte.PFAD_ARTEN;
        if (!wurzel.contains(pfad, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gelten die "
                    + Messwerte.VORGABE_ARTEN.size() + " Arten aus dem Programm.");
            return Messwerte.VORGABE_ARTEN;
        }
        List<?> gelesen = wurzel.getList(pfad);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(pfad)) + ") - verworfen."
                    + " Erwartet wird eine Liste in eckigen Klammern oder mit Strichen. "
                    + Wirtschaftsliste.esBleibt(Messwerte.VORGABE_ARTEN.size())
                    + " Arten aus dem Programm.");
            return Messwerte.VORGABE_ARTEN;
        }
        Set<Lebenszeichen> sauber = new LinkedHashSet<>();
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Name - uebersprungen.");
                continue;
            }
            Lebenszeichen art = Lebenszeichen.ausSchluessel(name);
            if (art == null) {
                klagen.add(pfad + ": \"" + name.trim() + "\" ist keine Art von"
                        + " Lebenszeichen - uebersprungen. Es gibt " + nenneAlle() + ".");
                continue;
            }
            sauber.add(art);
        }
        if (sauber.isEmpty()) {
            klagen.add(pfad + " ist leer - verworfen. Eine leere Liste hiesse, dass kein"
                    + " Taktschlag jemals taetig ist, und das sieht in jeder Anzeige aus"
                    + " wie ein leerer Server. Wer milde messen will, setzt "
                    + Messwerte.PFAD_VERLANGTE_ARTEN + " auf 1. "
                    + Wirtschaftsliste.esBleibt(Messwerte.VORGABE_ARTEN.size())
                    + " Arten aus dem Programm.");
            return Messwerte.VORGABE_ARTEN;
        }
        bericht.add(pfad + ": " + sauber.size() + " von " + Lebenszeichen.values().length
                + " Arten - " + nenne(sauber) + ".");
        return sauber;
    }

    /**
     * Wie viele verschiedene Arten ein taetiger Taktschlag braucht.
     *
     * <p>Geprueft gegen die Laenge der gelesenen Liste und nicht gegen die sieben des
     * Programms: Zwei Werte, die jeder fuer sich erlaubt sind und zusammen nicht, sind
     * genau der Fall, den eine Pruefung je Wert durchlaesst - dieselbe Falle wie bei Takt
     * und Untaetigkeitsgrenze in Teil C.
     *
     * <p>Zurueck kommt im Fehlerfall die GROESSTE passende Zahl und nicht die Vorgabe des
     * Programms: Wer drei Arten verlangt und zwei zaehlen laesst, wollte offensichtlich
     * streng messen, und 2 ist das Strengste, was seine Liste hergibt. Die Vorgabe 2 waere
     * zufaellig dieselbe Zahl - bei einer Liste mit einer Art waere sie wieder unmoeglich.
     */
    private static int verlangteArten(ConfigurationSection wurzel, int vorhanden,
                                      List<String> klagen, List<String> bericht) {
        String pfad = Messwerte.PFAD_VERLANGTE_ARTEN;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad,
                Messwerte.VORGABE_VERLANGTE_ARTEN, klagen, bericht);
        if (gelesen == null) {
            return passend(Messwerte.VORGABE_VERLANGTE_ARTEN, vorhanden);
        }
        long wert = gelesen;
        // Der LONG wird geprueft und nicht der verkuerzte int, wie bei
        // Wirtschaftsliste.anzahl: Aus (int) -2147483649 wird 2147483647, und eine
        // negative Zahl gaelte dann als die strengste Messung ueberhaupt.
        if (wert < 1L || wert > vorhanden) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 1 bis "
                    + vorhanden + ", also hoechstens so viel, wie " + Messwerte.PFAD_ARTEN
                    + " Arten zaehlen laesst; mehr hiesse, dass kein Taktschlag jemals"
                    + " taetig ist. Es gilt " + passend(
                            Messwerte.VORGABE_VERLANGTE_ARTEN, vorhanden) + ".");
            return passend(Messwerte.VORGABE_VERLANGTE_ARTEN, vorhanden);
        }
        return (int) wert;
    }

    /** Die Vorgabe, auf die Laenge der Liste geklemmt - damit der Rueckfall immer passt. */
    private static int passend(int vorgabe, int vorhanden) {
        return Math.max(1, Math.min(vorgabe, vorhanden));
    }

    /** Wie weit ein Spieler kommen muss, damit das Bewegen zaehlt. */
    private static int strecke(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        String pfad = Messwerte.PFAD_STRECKE;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, Messwerte.VORGABE_STRECKE,
                klagen, bericht);
        if (gelesen == null) {
            return Messwerte.VORGABE_STRECKE;
        }
        long wert = gelesen;
        if (wert < 1L || wert > Messwerte.HOECHSTE_STRECKE) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 1 bis "
                    + Messwerte.HOECHSTE_STRECKE + " Bloecke. Eine 0 hiesse, dass auch"
                    + " Stillstehen als Bewegung zaehlt, und darueber waere die Strecke"
                    + " nur noch im Flug zu schaffen. "
                    + Wirtschaftsliste.esBleibt(Messwerte.VORGABE_STRECKE));
            return Messwerte.VORGABE_STRECKE;
        }
        return (int) wert;
    }

    /** Alle Arten, die es gibt - fuer die Klage ueber einen unbekannten Namen. */
    private static String nenneAlle() {
        return nenne(Lebenszeichen.alle());
    }

    /**
     * Die Startzeile zur Messstelle - EIN Satz, der jeden Wert nennt, den er verspricht.
     *
     * <p>Hier und nicht inline im Plugin, damit eine Probe sie lesen kann. Der Grund steht
     * im Protokoll des 07.11.2026: Die Lesezeile des Bundesmarktes brach mitten im Satz ab
     * ("Tagesdeckel je Spieler." ohne Zahl), weil die Stelle, die sie baute, den Deckel
     * nicht liest - und keine Probe sah den Wortlaut einer Berichtzeile an. Ein Greppen
     * nach Fehlerworten findet so etwas nie.
     *
     * <p>Sie nennt die Artenliste ueber {@link #nenne} und nicht mit eigener Schreibweise:
     * Zwei Stellen, die eine Aufzaehlung verschieden schreiben, sehen nach zwei
     * verschiedenen Listen aus.
     */
    static String startzeile(Messwerte werte) {
        return "Messstelle: " + werte.verlangteArten() + " von " + werte.arten().size()
                + " Arten je Taktschlag machen ihn taetig (" + nenne(werte.arten())
                + "), Bewegen ab " + werte.mindestStrecke() + " Bloecken. Gemessen wird"
                + " neben der Anwesenheit und greift in nichts ein.";
    }

    /**
     * Die Schluessel dieser Arten, durch Komma getrennt - fuer Klage, Bericht und Protokoll.
     *
     * <p>Paketprivat und nicht privat: Die Startzeile des Plugins nennt dieselbe Liste, und
     * zwei Stellen, die eine Aufzaehlung verschieden schreiben, sehen nach zwei
     * verschiedenen Listen aus.
     */
    static String nenne(Set<Lebenszeichen> arten) {
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : arten) {
            namen.add(art.schluessel());
        }
        return String.join(", ", namen);
    }

    /**
     * Nennt jede Zeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, wie bei {@link Marktliste}: Drei Berichte, die
     * dieselbe Zahl verschieden nennen, sind schlimmer als drei Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen der"
                    + " Messstelle fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }

}
