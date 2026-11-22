package de.freimarken.paper;

import de.freimarken.kern.Anmeldestand;
import de.freimarken.kern.Anmeldewerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code anmeldung:} aus der {@code config.yml} - zwei Werte.
 *
 * <p>Nach demselben Muster wie {@link Muenzliste} und {@link Wechselliste}: reine
 * Dateiarbeit, gesammelte KLAGEN statt Ausnahmen, und der Rueckfall steht im Programm. Ein
 * Tippfehler in der Fassung darf keinen Serverstart mitreissen; er darf aber auch nicht
 * still gelten.
 *
 * <p><b>Eine kaputte Fassung ist eine Klage und keine stille Vorgabe</b>, und zwar eine
 * besonders wichtige: Gaelte dann die ausgelieferte Fassung, hielte der Server die
 * Zustimmung zu SEINEN Regeln fuer die Zustimmung zu den mitgelieferten - ein Nachweis,
 * der auf den falschen Text zeigt. Der Betreiber liest die Klage mit dem Schluessel und
 * setzt sie richtig; bis dahin gilt die ausgelieferte, und die Zeile sagt das.
 *
 * <p><b>Die Rechenprobe dieses Blocks ist der BERICHT ueber die geltende Fassung.</b> Sie
 * ist die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und trotzdem
 * falsch ist: Wer seine Regeln geaendert und die Fassung vergessen hat, liest nach dem
 * Neuladen, dass die alte noch gilt - und damit, dass niemand neu zustimmen muss.
 */
final class Anmeldeliste {

    /**
     * Die Schluessel dieses Abschnitts - fuer die Zeile, die fehlende nennt.
     *
     * <p>Dieselbe Bauart wie bei {@link Muenzliste}: Was in der Datei fehlt, gilt aus dem
     * Programm, und das steht im Bericht statt in einer Klage. Ein Betreiber, der eine
     * Zeile nicht kennt, soll sie im Startprotokoll finden.
     */
    private static final List<String> SCHLUESSEL = List.of(
            Anmeldewerte.PFAD_AN,
            Anmeldewerte.PFAD_FASSUNG);

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Anmeldewerte werte, List<String> klagen, List<String> bericht) {
    }

    private Anmeldeliste() {
    }

    /**
     * Der ganze Abschnitt {@code anmeldung:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Anmeldewerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        Boolean gelesen = Wirtschaftsliste.jaNein(wurzel, Anmeldewerte.PFAD_AN,
                Anmeldewerte.VORGABE.an(), klagen, bericht);
        boolean an = gelesen == null ? Anmeldewerte.VORGABE.an() : gelesen;
        String fassung = fassung(wurzel, klagen);
        try {
            Anmeldewerte werte = new Anmeldewerte(an, fassung);
            bericht.add(rechenprobe(werte));
            return new Ergebnis(werte, klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Der Leser wirft NIE, der record sehr wohl - darum dieses try/catch. Hierher
            // kommt, was fassung() durchgelassen hat und der record doch abweist; heute
            // ist das nichts, aber ein neuer Riegel dort darf keinen Start mitreissen.
            klagen.add(Anmeldewerte.PFAD_FASSUNG + ": " + unbrauchbar.getMessage()
                    + " Es gilt die ausgelieferte Fassung.");
            bericht.add(rechenprobe(Anmeldewerte.VORGABE));
            return new Ergebnis(Anmeldewerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Fassung aus der Datei - oder die ausgelieferte, wenn die Zeile nicht taugt.
     *
     * <p>Geprueft wird HIER und nicht erst im record, damit die Klage den SCHLUESSEL
     * nennt: Ein Betreiber, der "{@code 2026-11-21 agb2}" schreibt, soll
     * {@code anmeldung.fassung} lesen und nicht einen Satz ueber Muster.
     */
    private static String fassung(ConfigurationSection wurzel, List<String> klagen) {
        if (!wurzel.isSet(Anmeldewerte.PFAD_FASSUNG)) {
            return Anmeldewerte.VORGABE.fassung();
        }
        String roh = wurzel.getString(Anmeldewerte.PFAD_FASSUNG);
        if (roh == null || roh.isBlank()) {
            // Ein Schluessel OHNE Wert kommt beim Leser als null an - dieselbe Falle wie
            // bei jedem anderen Wert dieser Datei. Er ist eine Klage und nicht die
            // Vorgabe: Wer die Zeile hinschreibt, meint etwas damit.
            klagen.add(Anmeldewerte.PFAD_FASSUNG + " steht da, hat aber keinen Wert."
                    + " Erwartet wird eine Bezeichnung in Anfuehrungszeichen, zum"
                    + " Beispiel \"" + Anmeldewerte.VORGABE_FASSUNG + "\".");
            return Anmeldewerte.VORGABE.fassung();
        }
        String rein = roh.trim().toLowerCase(Locale.ROOT);
        if (rein.length() > Anmeldestand.HOECHSTE_FASSUNGSLAENGE) {
            klagen.add(Anmeldewerte.PFAD_FASSUNG + ": \"" + roh + "\" ist laenger als "
                    + Anmeldestand.HOECHSTE_FASSUNGSLAENGE
                    + " Zeichen - so lang ist die Spalte in der Ablage. Es gilt die"
                    + " ausgelieferte Fassung.");
            return Anmeldewerte.VORGABE.fassung();
        }
        if (!rein.matches(Anmeldestand.MUSTER)) {
            klagen.add(Anmeldewerte.PFAD_FASSUNG + ": \"" + roh + "\" taugt nicht."
                    + " Erlaubt sind Kleinbuchstaben, Ziffern, Punkt, Unterstrich und"
                    + " Bindestrich - ein Leerzeichen oder ein Umlaut waere in der Datei"
                    + " nicht wiederzuerkennen. Es gilt die ausgelieferte Fassung.");
            return Anmeldewerte.VORGABE.fassung();
        }
        return rein;
    }

    /**
     * Die Zeile, die die Anmeldung im Zusammenhang nennt.
     *
     * <p>Sie nennt die FASSUNG woertlich, und das ist ihr Zweck: Wer seine Regeln geaendert
     * und die Fassung vergessen hat, liest hier, dass die alte noch gilt - und damit, dass
     * niemand neu zustimmen muss. Eine Zeile "Anmeldung: an" sagte das nicht.
     */
    private static String rechenprobe(Anmeldewerte werte) {
        if (!werte.an()) {
            return "Anmeldung AUS (" + Anmeldewerte.PFAD_AN + ": false) - niemand wird"
                    + " nach einer Zustimmung gefragt, und der Bundesmarkt ist fuer jeden"
                    + " offen. Die abgelegten Annahmen bleiben stehen.";
        }
        return "Anmeldung an, geltende Fassung \"" + werte.fassung() + "\" - wer eine"
                + " andere angenommen hat, wird beim Beitritt erneut gefragt und kann"
                + " bis dahin am Bundesmarkt nicht handeln. Aenderst du die Regeln in"
                + " der sprache.yml, aendere auch " + Anmeldewerte.PFAD_FASSUNG + ".";
    }

    /** Welche Schluessel dieses Abschnitts in der Datei fehlen - fuer den Bericht. */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String schluessel : SCHLUESSEL) {
            if (!wurzel.isSet(schluessel)) {
                fehlen.add(schluessel);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add("Nicht in der Datei, es gilt die Vorgabe: "
                    + String.join(", ", fehlen));
        }
    }
}
