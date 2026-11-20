package de.freimarken.paper;

import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Verfallswerte;
import de.freimarken.kern.Tat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Zahlen der Freistaedte aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Mietliste}: ein Ergebnis mit Werten, Klagen und Bericht,
 * jede kaputte Zeile beklagt und die uebrigen stehen gelassen. Ein Tippfehler in einer
 * Zahl darf keinen Serverstart mitreissen.
 *
 * <p><b>Nicht zu verwechseln mit {@code welten.freistaedte}</b>: Jener Eintrag beschreibt
 * die WELT - ihre Art, ihre Kante, ihr Tor - und wird von {@link Weltenliste} gelesen.
 * Dieser Abschnitt steht auf oberster Ebene und beschreibt, was eine Stadt kostet. Zwei
 * gleichnamige Abschnitte auf zwei Ebenen sind eine Stolperstelle; genau dieselbe gibt es
 * seit Schritt 8 bei {@code laeden:} neben {@code welten.altfurt}.
 */
final class Freistadtliste {

    /** In welcher Welt die Stadtfelder liegen - ohne Namensraum, wie in der Datei. */
    static final String PFAD_WELT = "freistaedte.welt";

    /** Der ausgelieferte Weltname. */
    static final String VORGABE_WELT = "freistaedte";

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            PFAD_WELT, Freistadtwerte.PFAD_GRUENDUNGSPREIS,
            Freistadtwerte.PFAD_HOECHSTZAHL, Freistadtwerte.PFAD_HOECHSTZELLEN,
            Freistadtwerte.PFAD_VERKAUFSGEBUEHR, Freistadtwerte.PFAD_BUERGERRECHTE,
            Freistadtwerte.PFAD_AUFLAGE_TELEPORTER,
            Freistadtwerte.PFAD_AUFLAGE_LADENRECHT, Verfallswerte.PFAD_AN,
            Verfallswerte.PFAD_UNBESUCHT_TAGE, Verfallswerte.PFAD_WARNUNG_FRUEH,
            Verfallswerte.PFAD_WARNUNG_SPAET, Freistadtwerte.PFAD_ERSTATTUNG,
            Freistadtwerte.PFAD_LADENGEBUEHR_ANLAGE,
            Freistadtwerte.PFAD_LADENGEBUEHR_TAG);

    /**
     * @param welt der Weltname OHNE Namensraum, wie er in der Datei steht. Der Schluessel
     *             entsteht erst im Plugin ueber {@code weltschluessel(name)} - hier einen
     *             zu bauen waere Handarbeit, und genau die wird an der naechsten Stelle
     *             vergessen (gefunden am 09.11.2026 im ganzen Ladenbereich)
     */
    record Ergebnis(String welt, Freistadtwerte werte, List<String> klagen,
                    List<String> bericht) {
    }

    private Freistadtliste() {
    }

    /**
     * Die zwei Zahlen aus dem Abschnitt {@code freistaedte:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(VORGABE_WELT, Freistadtwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        String welt = welt(wurzel, klagen, bericht);

        long preis = Wirtschaftsliste.betrag(wurzel,
                Freistadtwerte.PFAD_GRUENDUNGSPREIS, "freistadt-gruendung",
                Freistadtwerte.VORGABE_GRUENDUNGSPREIS, Freistadtwerte.HOECHSTER_BETRAG,
                "Mark einmalig", klagen, bericht);
        int hoechstzahl = hoechstzahl(wurzel, klagen, bericht);
        int hoechstzellen = hoechstzellen(wurzel, klagen, bericht);
        int gebuehr = verkaufsgebuehr(wurzel, klagen, bericht);
        Set<Tat> rechte = buergerrechte(wurzel, klagen, bericht);
        boolean teleporter = jaNein(wurzel,
                Freistadtwerte.PFAD_AUFLAGE_TELEPORTER,
                Freistadtwerte.VORGABE_AUFLAGE, klagen, bericht);
        boolean ladenrecht = jaNein(wurzel,
                Freistadtwerte.PFAD_AUFLAGE_LADENRECHT,
                Freistadtwerte.VORGABE_AUFLAGE, klagen, bericht);

        Freistadtwerte werte = new Freistadtwerte(preis, hoechstzahl,
                hoechstzellen, gebuehr, rechte, teleporter, ladenrecht,
                verfall(wurzel, klagen, bericht),
                erstattung(wurzel, klagen, bericht),
                betrag(wurzel, Freistadtwerte.PFAD_LADENGEBUEHR_ANLAGE,
                        Freistadtwerte.VORGABE_LADENGEBUEHR_ANLAGE, klagen, bericht),
                betrag(wurzel, Freistadtwerte.PFAD_LADENGEBUEHR_TAG,
                        Freistadtwerte.VORGABE_LADENGEBUEHR_TAG, klagen, bericht));
        bericht.add(rechenprobe(werte) + " Welt: " + welt + ".");
        return new Ergebnis(welt, werte, klagen, bericht);
    }

    /**
     * In welcher Welt die Stadtfelder liegen.
     *
     * <p>Wortgleich zu {@code Ladenliste.welt}: ein NAME, klein und ohne Rand. Ob es die
     * Welt gibt, entscheidet dieser Leser NICHT - die Welten stehen im Abschnitt
     * {@code welten:}, den {@link Weltenliste} liest, und zwei Leser derselben Zeile
     * laufen auseinander.
     */
    private static String welt(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        if (!wurzel.contains(PFAD_WELT, true)) {
            bericht.add(Wirtschaftsliste.fehltText(PFAD_WELT, VORGABE_WELT));
            return VORGABE_WELT;
        }
        Object roh = wurzel.get(PFAD_WELT);
        if (!(roh instanceof String name) || name.isBlank()) {
            klagen.add(PFAD_WELT + " ist kein Weltname ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(VORGABE_WELT));
            return VORGABE_WELT;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Ladenmiete: die einzige Schranke gegen den Wert,
     * der im erlaubten Bereich liegt und trotzdem falsch ist. Wer hier "4.000 Mark" statt
     * "400.000 Mark" liest, sieht den fehlenden Nullenblock - keine Zahlengrenze sieht
     * ihn.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann: Zwei Stellen, die
     * dieselben Zahlen verschieden schreiben, sehen nach zwei verschiedenen Zahlen aus.
     */
    static String rechenprobe(Freistadtwerte werte) {
        StringBuilder satz = new StringBuilder("Freistaedte: ");
        if (werte.hoechstzahl() == 0) {
            satz.append("AUS - hoechstzahl ist 0, es wird nicht gegruendet.");
            return satz.toString();
        }
        satz.append(werte.gruendungspreis()).append(" Mark je Gruendung, ")
                .append(staedte(werte.hoechstzahl())).append(" je Spieler.");
        if (werte.gruendungspreis() == 0L) {
            satz.append(" GRATIS - wer das Recht hat, gruendet umsonst.");
        }
        return satz.toString();
    }

    private static String staedte(int zahl) {
        return zahl == 1 ? "eine Stadt" : zahl + " Staedte";
    }

    /**
     * Wie viele Zellen ein Grundstueck umfassen darf - 0 heisst ohne Grenze.
     *
     * <p>Wortgleich gebaut wie {@link #hoechstzahl} daneben, und das ist Absicht: Zwei
     * Zahlen aus demselben Abschnitt, die auf zwei verschiedene Weisen gelesen werden,
     * verhalten sich bei einem Vertipper verschieden - und niemand kann sagen, warum.
     */
    private static int hoechstzellen(ConfigurationSection wurzel, List<String> klagen,
                                     List<String> bericht) {
        if (!wurzel.contains(Freistadtwerte.PFAD_HOECHSTZELLEN, true)) {
            return Freistadtwerte.VORGABE_HOECHSTZELLEN;
        }
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel,
                Freistadtwerte.PFAD_HOECHSTZELLEN,
                Freistadtwerte.VORGABE_HOECHSTZELLEN, klagen, bericht);
        if (gelesen == null) {
            return Freistadtwerte.VORGABE_HOECHSTZELLEN;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Freistadtwerte.HOECHSTE_ZELLEN) {
            klagen.add(Freistadtwerte.PFAD_HOECHSTZELLEN + " ist " + wert
                    + " und liegt nicht zwischen 0 und " + Freistadtwerte.HOECHSTE_ZELLEN
                    + " - es gilt " + Freistadtwerte.VORGABE_HOECHSTZELLEN + ".");
            return Freistadtwerte.VORGABE_HOECHSTZELLEN;
        }
        if (wert != Freistadtwerte.VORGABE_HOECHSTZELLEN) {
            bericht.add(Freistadtwerte.PFAD_HOECHSTZELLEN + " = " + wert + " gilt.");
        }
        return (int) wert;
    }

    // --- Teil C3 ---------------------------------------------------------------

    /**
     * Die Verkaufsgebuehr in Prozent - 0 heisst keine.
     *
     * <p>Dieselbe Bauart wie {@link #hoechstzellen} und {@link #hoechstzahl}: Drei
     * Zahlen aus demselben Abschnitt, die auf drei Weisen gelesen werden, verhalten sich
     * bei einem Vertipper verschieden - und niemand kann sagen, warum.
     *
     * <p>Die Grenze kommt aus {@code Shopregel.brauchbareGebuehr} und nicht von hier:
     * Es ist dieselbe Frage wie bei den Shopgebuehren, und eine zweite Zahl dafuer liefe
     * bei der naechsten Verschaerfung auseinander.
     */
    private static int verkaufsgebuehr(ConfigurationSection wurzel, List<String> klagen,
                                       List<String> bericht) {
        if (!wurzel.contains(Freistadtwerte.PFAD_VERKAUFSGEBUEHR, true)) {
            return Freistadtwerte.VORGABE_VERKAUFSGEBUEHR;
        }
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel,
                Freistadtwerte.PFAD_VERKAUFSGEBUEHR,
                Freistadtwerte.VORGABE_VERKAUFSGEBUEHR, klagen, bericht);
        if (gelesen == null) {
            return Freistadtwerte.VORGABE_VERKAUFSGEBUEHR;
        }
        long wert = gelesen;
        if (wert < 0L || wert > de.freimarken.kern.Shopregel.HOECHSTE_GEBUEHR_PROZENT) {
            klagen.add(Freistadtwerte.PFAD_VERKAUFSGEBUEHR + " ist " + wert
                    + " und liegt nicht zwischen 0 und "
                    + de.freimarken.kern.Shopregel.HOECHSTE_GEBUEHR_PROZENT
                    + " - es gilt " + Freistadtwerte.VORGABE_VERKAUFSGEBUEHR + ".");
            return Freistadtwerte.VORGABE_VERKAUFSGEBUEHR;
        }
        if (wert != Freistadtwerte.VORGABE_VERKAUFSGEBUEHR) {
            bericht.add(Freistadtwerte.PFAD_VERKAUFSGEBUEHR + " = " + wert + " gilt.");
        }
        return (int) wert;
    }

    private static int hoechstzahl(ConfigurationSection wurzel, List<String> klagen,
                                   List<String> bericht) {
        if (!wurzel.contains(Freistadtwerte.PFAD_HOECHSTZAHL, true)) {
            return Freistadtwerte.VORGABE_HOECHSTZAHL;
        }
        // Als long gelesen und geprueft, obwohl das Feld ein int ist - derselbe Grund
        // wie bei Wirtschaftswerte.brauchbareAnzahl: Wer vorher auf int verkuerzt, prueft
        // nicht mehr den getippten Wert, sondern dessen untere 32 Bit.
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, Freistadtwerte.PFAD_HOECHSTZAHL,
                Freistadtwerte.VORGABE_HOECHSTZAHL, klagen, bericht);
        if (gelesen == null) {
            return Freistadtwerte.VORGABE_HOECHSTZAHL;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Freistadtwerte.HOECHSTE_ZAHL) {
            klagen.add(Freistadtwerte.PFAD_HOECHSTZAHL + " ist " + wert
                    + " und liegt nicht zwischen 0 und " + Freistadtwerte.HOECHSTE_ZAHL
                    + " - es gilt " + Freistadtwerte.VORGABE_HOECHSTZAHL + ".");
            return Freistadtwerte.VORGABE_HOECHSTZAHL;
        }
        if (wert != Freistadtwerte.VORGABE_HOECHSTZAHL) {
            bericht.add(Freistadtwerte.PFAD_HOECHSTZAHL + " = " + wert + " gilt.");
        }
        return (int) wert;
    }

    // --- Teil E3 ---------------------------------------------------------------

    /**
     * Ein Auflagenschalter - EINE Methode fuer BEIDE.
     *
     * <p>Zwei gleich gebaute Leser nebeneinander waeren genau die Stelle, an der der
     * zweite die naechste Verschaerfung nicht mitbekommt - dieselbe Begruendung, die
     * {@link #hoechstzellen} fuer seine Bauart gibt, nur hier zu Ende gedacht: Es IST
     * dieselbe Frage, also ist es eine Methode mit einem Pfad als Argument.
     *
     * <p>Ein Wert, der kein Ja oder Nein ist, wird BEKLAGT und die Vorgabe gilt - nicht
     * still als {@code false} gelesen. Bukkits {@code getBoolean} gibt fuer "vielleicht"
     * ein {@code false} zurueck, und das waere eine ausgeschaltete Auflage, die der
     * Betreiber eingeschaltet glaubt.
     */
    private static boolean jaNein(ConfigurationSection wurzel, String pfad,
                                  boolean vorgabe, List<String> klagen,
                                  List<String> bericht) {
        if (!wurzel.contains(pfad, true)) {
            return vorgabe;
        }
        Object roh = wurzel.get(pfad);
        if (!(roh instanceof Boolean gelesen)) {
            klagen.add(pfad + " ist kein Ja oder Nein ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        if (gelesen != vorgabe) {
            // GEMELDET, und zwar laut: Eine abgeschaltete Auflage ist eine Regel, die
            // gebaut ist und nicht wirkt. Wer den Startbericht liest, soll das sehen -
            // sonst sucht er die Wirkung im Programm.
            bericht.add(pfad + " = " + gelesen + " gilt - die Regel wirkt hier NICHT.");
        }
        return gelesen;
    }

    /**
     * Die vier Verfallszeilen - und bei jedem Unsinn die ganze VORGABE.
     *
     * <p><b>Alles oder nichts, anders als bei den Zahlen daneben</b>: Die drei Fristen
     * haengen voneinander ab ({@code Verfallswerte} verlangt, dass die zweite Warnung
     * naeher am Verfall liegt als die erste). Ein einzeln zurechtgebogener Wert ergaebe
     * eine Mischung aus Datei und Vorgabe, die der Betreiber nirgends nachlesen kann -
     * und die Pruefung im Satz wuerfe mitten im Start.
     *
     * <p>Darum: Jede Zeile wird einzeln gelesen und beklagt, und wenn der fertige Satz
     * sich nicht bauen laesst, gilt {@code Verfallswerte.VORGABE} - mit einer Klage, die
     * den Grund woertlich nennt.
     */
    private static Verfallswerte verfall(ConfigurationSection wurzel,
                                         List<String> klagen, List<String> bericht) {
        boolean an = jaNein(wurzel, Verfallswerte.PFAD_AN, Verfallswerte.VORGABE_AN,
                klagen, bericht);
        int unbesucht = frist(wurzel, Verfallswerte.PFAD_UNBESUCHT_TAGE,
                Verfallswerte.VORGABE_UNBESUCHT_TAGE, klagen, bericht);
        int frueh = frist(wurzel, Verfallswerte.PFAD_WARNUNG_FRUEH,
                Verfallswerte.VORGABE_WARNUNG_FRUEH, klagen, bericht);
        int spaet = frist(wurzel, Verfallswerte.PFAD_WARNUNG_SPAET,
                Verfallswerte.VORGABE_WARNUNG_SPAET, klagen, bericht);
        try {
            return new Verfallswerte(an, unbesucht, frueh, spaet);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add("Die Verfallsfristen passen nicht zusammen: "
                    + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt("die Vorgabe ("
                            + Verfallswerte.VORGABE_UNBESUCHT_TAGE + " Tage, Warnungen "
                            + Verfallswerte.VORGABE_WARNUNG_FRUEH + " und "
                            + Verfallswerte.VORGABE_WARNUNG_SPAET + " Tage vorher)"));
            return Verfallswerte.VORGABE;
        }
    }

    /**
     * Eine Tagesfrist - ganze Zahl, nicht negativ, nicht laenger als zehn Jahre.
     *
     * <p>EINE Methode fuer drei Zeilen mit dem Pfad als Argument, aus demselben Grund,
     * den {@link #auflage} nebenan nennt: Drei gleich gebaute Leser waeren drei Stellen,
     * an denen der zweite die naechste Verschaerfung nicht mitbekommt.
     */
    private static int frist(ConfigurationSection wurzel, String pfad, int vorgabe,
                             List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen,
                bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Verfallswerte.LAENGSTE_FRIST_TAGE) {
            klagen.add(pfad + " ist " + wert + " und liegt nicht zwischen 0 und "
                    + Verfallswerte.LAENGSTE_FRIST_TAGE + " Tagen - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        return (int) wert;
    }

    /** Der Erstattungsanteil in Prozent - 0 bis 100. */
    private static int erstattung(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel,
                Freistadtwerte.PFAD_ERSTATTUNG, Freistadtwerte.VORGABE_ERSTATTUNG,
                klagen, bericht);
        if (gelesen == null) {
            return Freistadtwerte.VORGABE_ERSTATTUNG;
        }
        long wert = gelesen;
        if (wert < 0L || wert > 100L) {
            klagen.add(Freistadtwerte.PFAD_ERSTATTUNG + " ist " + wert + " und liegt"
                    + " nicht zwischen 0 und 100 Prozent - verworfen. Ueber 100 waere"
                    + " das Gruenden eine Geldquelle. "
                    + Wirtschaftsliste.esBleibt(
                            String.valueOf(Freistadtwerte.VORGABE_ERSTATTUNG)));
            return Freistadtwerte.VORGABE_ERSTATTUNG;
        }
        return (int) wert;
    }

    /**
     * Ein Mark-Betrag - nicht negativ, nicht ueber der Obergrenze.
     *
     * <p>EINE Methode fuer die zwei Ladengebuehren mit dem Pfad als Argument, aus
     * demselben Grund, den {@link #jaNein} und {@link #frist} nennen: Zwei gleich
     * gebaute Leser waeren zwei Stellen, an denen der zweite die naechste Verschaerfung
     * nicht mitbekommt.
     */
    private static long betrag(ConfigurationSection wurzel, String pfad, long vorgabe,
                               List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen,
                bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Freistadtwerte.HOECHSTER_BETRAG) {
            klagen.add(pfad + " ist " + wert + " und liegt nicht zwischen 0 und "
                    + Freistadtwerte.HOECHSTER_BETRAG + " Mark - verworfen. Eine 0 ist"
                    + " erlaubt und heisst: Die Gebuehr faellt nicht. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        return wert;
    }

    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(Wirtschaftsliste.fehlendeZeilen(fehlen,
                    SCHLUESSEL.size(), "der Freistaedte"));
        }
    }

    // --- Teil D3 ---------------------------------------------------------------

    /**
     * Die Vorgabe-Buergerrechte fuer NEU gegruendete Staedte.
     *
     * <p>Eine LISTE von Woertern und keine Zahl, also eine andere Bauart als die drei
     * Zahlenleser darueber - und das liegt an der Sache: Was einstellbar ist, sind drei
     * benannte Rechte, und ein viertes soll ein Wort mehr sein und keinen Schemaschritt
     * brauchen.
     *
     * <p><b>Ein unbekanntes Wort wird BEKLAGT und das Recht weggelassen</b>, der Start
     * laeuft weiter - dieselbe Lesart wie bei jeder anderen kaputten Zeile dieser Datei.
     * Was NICHT geht, ist ein stilles Uebergehen: Der Betreiber hat ein Recht gegeben,
     * und ohne Klage suchte er die Wirkung an der falschen Stelle.
     *
     * <p>Die Woerter sind die Schluessel aus {@link Tat} und nicht die Namen der
     * Konstanten: In der Datei steht {@code behaelter}, nicht {@code BEHAELTER}. Welche
     * es gibt, nennt die Klage selbst - eine zweite Liste hier waere die Stelle, die beim
     * vierten Recht vergessen wird.
     */
    private static Set<Tat> buergerrechte(ConfigurationSection wurzel,
                                          List<String> klagen, List<String> bericht) {
        if (!wurzel.contains(Freistadtwerte.PFAD_BUERGERRECHTE, true)) {
            return Freistadtwerte.VORGABE_BUERGERRECHTE;
        }
        List<String> woerter = wurzel.getStringList(Freistadtwerte.PFAD_BUERGERRECHTE);
        Set<Tat> rechte = new LinkedHashSet<>();
        for (String wort : woerter) {
            // Kleingeschrieben und ohne Rand gelesen: "  Benutzen " in der Datei ist
            // derselbe Wunsch wie "benutzen", und ein Betreiber soll nicht an einem
            // Leerzeichen scheitern.
            String sauber = wort == null ? "" : wort.trim().toLowerCase(Locale.ROOT);
            try {
                rechte.add(Tat.ausSchluessel(sauber));
            } catch (IllegalArgumentException unbekannt) {
                klagen.add(Freistadtwerte.PFAD_BUERGERRECHTE + " nennt " + wort
                        + " - das ist kein Buergerrecht. " + unbekannt.getMessage()
                        + " Dieses Wort gilt nicht.");
            }
        }
        if (!rechte.equals(Freistadtwerte.VORGABE_BUERGERRECHTE)) {
            bericht.add(Freistadtwerte.PFAD_BUERGERRECHTE + " = " + schluessel(rechte)
                    + " gilt fuer neue Staedte.");
        }
        return rechte;
    }

    /** Die Schluessel einer Rechtemenge, in fester Reihenfolge - fuer den Bericht. */
    private static String schluessel(Set<Tat> rechte) {
        if (rechte.isEmpty()) {
            return "keine";
        }
        StringBuilder gebaut = new StringBuilder();
        // Ueber values() und nicht ueber die Menge: So steht die Reihenfolge fest, und
        // zwei Starts mit derselben Einstellung schreiben dieselbe Zeile.
        for (Tat tat : Tat.values()) {
            if (rechte.contains(tat)) {
                if (gebaut.length() > 0) {
                    gebaut.append(", ");
                }
                gebaut.append(tat.schluessel());
            }
        }
        return gebaut.toString();
    }

}
