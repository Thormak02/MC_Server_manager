package de.freimarken.paper;

import de.freimarken.kern.Freistadtwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            Freistadtwerte.PFAD_HOECHSTZAHL);

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

        Freistadtwerte werte = new Freistadtwerte(preis, hoechstzahl);
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

    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen der"
                    + " Freistaedte fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }
}
