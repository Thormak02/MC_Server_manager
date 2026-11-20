package de.freimarken.paper;

import de.freimarken.kern.Inselwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code inseln:} aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Freistadtliste}: ein Ergebnis mit Werten, Klagen und
 * Bericht, jede kaputte Zeile beklagt und die uebrigen stehen gelassen. Ein Tippfehler in
 * einer Zahl darf keinen Serverstart mitreissen.
 *
 * <p><b>Nicht zu verwechseln mit {@code welten.hafenwerder}</b>: Jener Eintrag beschreibt
 * die WELT - ihre Art, ihre Kante, ihr Tor - und wird von {@link Weltenliste} gelesen.
 * Dieser Abschnitt steht auf oberster Ebene und beschreibt die Meerinseln darin. Anders
 * als bei den Freistaedten heissen die beiden nicht gleich, und das ist hier der Vorteil:
 * Bei {@code freistaedte:} neben {@code welten.freistaedte} ist schon mehr als einmal der
 * falsche Abschnitt gemeint gewesen.
 */
final class Inselliste {

    /** In welcher Welt die Inselfelder liegen - ohne Namensraum, wie in der Datei. */
    static final String PFAD_WELT = "inseln.welt";

    /** Der ausgelieferte Weltname. */
    static final String VORGABE_WELT = "hafenwerder";

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            PFAD_WELT, Inselwerte.PFAD_PREIS, Inselwerte.PFAD_HOECHSTZAHL);

    /**
     * @param welt der Weltname OHNE Namensraum, wie er in der Datei steht. Der Schluessel
     *             entsteht erst im Plugin ueber {@code weltschluessel(name)} - hier einen
     *             zu bauen waere Handarbeit, und genau die wird an der naechsten Stelle
     *             vergessen (gefunden am 09.11.2026 im ganzen Ladenbereich)
     */
    record Ergebnis(String welt, Inselwerte werte, List<String> klagen,
                    List<String> bericht) {
    }

    private Inselliste() {
    }

    /**
     * Der Abschnitt {@code inseln:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(VORGABE_WELT, Inselwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        String welt = welt(wurzel, klagen, bericht);
        long preis = Wirtschaftsliste.betrag(wurzel, Inselwerte.PFAD_PREIS,
                "insel-kauf", Inselwerte.VORGABE_PREIS, Inselwerte.HOECHSTER_BETRAG,
                "Mark einmalig", klagen, bericht);
        int hoechstzahl = hoechstzahl(wurzel, klagen, bericht);

        Inselwerte werte = new Inselwerte(preis, hoechstzahl);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(welt, werte, klagen, bericht);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Freistadt: die einzige Schranke gegen den Wert, der
     * im erlaubten Bereich liegt und trotzdem falsch ist. Wer hier "7.500 Mark" statt
     * "75.000 Mark" liest, sieht die fehlende Null - keine Zahlengrenze sieht sie.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann.
     */
    static String rechenprobe(Inselwerte werte) {
        StringBuilder satz = new StringBuilder("Meerinseln: ");
        if (werte.hoechstzahl() == 0) {
            satz.append("AUS - hoechstzahl ist 0, es wird keine vergeben.");
            return satz.toString();
        }
        satz.append(werte.preis()).append(" Mark je Insel, ")
                .append(inseln(werte.hoechstzahl())).append(" je Spieler.");
        if (werte.preis() == 0L) {
            satz.append(" GRATIS - wer das Recht hat, nimmt sie umsonst.");
        }
        return satz.toString();
    }

    private static String inseln(int zahl) {
        return zahl == 1 ? "eine Insel" : zahl + " Inseln";
    }

    /**
     * Welche mitgelieferten Zeilen in der Datei FEHLEN.
     *
     * <p>Dieselbe Bauart wie bei {@link Freistadtliste}: Eine fehlende Zeile ist keine
     * Klage - das Programm hat seinen Rueckfall -, aber sie gehoert in den Bericht. Auf
     * einem Server, der schon lief, schreibt {@code saveDefaultConfig()} die Datei nie
     * um; neue Zeilen fehlen dort einfach.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(Wirtschaftsliste.fehlendeZeilen(fehlen,
                    SCHLUESSEL.size(), "der Meerinseln"));
        }
    }

    /**
     * Die Obergrenze - wie viele Inseln ein Spieler ohne besonderes Recht haelt.
     *
     * <p>Eine 0 ist erlaubt und heisst: Es wird keine vergeben. Das ist der Weg, die
     * Inseln abzuschalten, ohne das Recht anzufassen.
     */
    private static int hoechstzahl(ConfigurationSection wurzel, List<String> klagen,
                                   List<String> bericht) {
        if (!wurzel.contains(Inselwerte.PFAD_HOECHSTZAHL, true)) {
            return Inselwerte.VORGABE_HOECHSTZAHL;
        }
        // Als long gelesen und geprueft, obwohl das Feld ein int ist - derselbe Grund
        // wie bei Wirtschaftswerte.brauchbareAnzahl: Wer vorher auf int verkuerzt, prueft
        // nicht mehr den getippten Wert, sondern dessen untere 32 Bit.
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, Inselwerte.PFAD_HOECHSTZAHL,
                Inselwerte.VORGABE_HOECHSTZAHL, klagen, bericht);
        if (gelesen == null) {
            return Inselwerte.VORGABE_HOECHSTZAHL;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Inselwerte.HOECHSTE_ZAHL) {
            klagen.add(Inselwerte.PFAD_HOECHSTZAHL + " ist " + wert
                    + " und liegt nicht zwischen 0 und " + Inselwerte.HOECHSTE_ZAHL
                    + " - es gilt " + Inselwerte.VORGABE_HOECHSTZAHL + ".");
            return Inselwerte.VORGABE_HOECHSTZAHL;
        }
        if (wert != Inselwerte.VORGABE_HOECHSTZAHL) {
            bericht.add(Inselwerte.PFAD_HOECHSTZAHL + " = " + wert + " gilt.");
        }
        return (int) wert;
    }

    /**
     * Der Weltname, in dem die Inselfelder liegen.
     *
     * <p><b>Wort fuer Wort wie {@code Freistadtliste.welt}</b>, und das ist der Punkt:
     * Zwei Leser derselben Art von Wert, die verschieden streng sind, sind der Fehler, den
     * niemand sucht - der eine nimmt "Hafen Werder" an und der andere nicht. Geprueft wird
     * also hier nur, dass ueberhaupt ein Wort dasteht; was ein Weltname sein darf,
     * entscheidet {@link Weltenliste} fuer alle Welten an einer Stelle.
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
}
