package de.freimarken.paper;

import de.freimarken.kern.Einsichtswerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code moderation.einsehen:} (Plan 12c.3, Schritt 16a Teil D).
 *
 * <p>Der kleinste Leser des Projekts - EINE Zahl. Er steht trotzdem in einer eigenen
 * Datei und nicht in {@link Wirtschaftsliste}: Dort liegen die Werte der WIRTSCHAFT.
 * Dieselbe Trennung wie bei {@link Meldeliste} und {@link Strafliste}.
 *
 * <p><b>Ein unbrauchbarer Wert faellt auf die Vorgabe zurueck</b> und verwirft nicht den
 * Abschnitt: Ein Server, auf dem nach einem Tippfehler niemand mehr einsehen kann, ist
 * schlimmer als einer mit fuenf Minuten. Die Klage nennt die Zahl.
 */
final class Einsichtsliste {

    /** Die Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL = List.of(Einsichtswerte.PFAD_DAUER);

    record Ergebnis(Einsichtswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Einsichtsliste() {
    }

    /**
     * Der Abschnitt {@code moderation.einsehen:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return fertig(Einsichtswerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        long dauer = Einsichtswerte.VORGABE.dauerSekunden();
        if (wurzel.contains(Einsichtswerte.PFAD_DAUER, true)) {
            Object roh = wurzel.get(Einsichtswerte.PFAD_DAUER);
            // Als long geprueft, obwohl die Zahl klein ist - aus dem Grund, der an
            // Wirtschaftswerte.brauchbareAnzahl steht: Wer vorher auf int verkuerzt,
            // prueft nicht mehr den getippten Wert, sondern dessen untere 32 Bit.
            if (!(roh instanceof Number zahl) || zahl.doubleValue() != Math.floor(
                    zahl.doubleValue())
                    || !Einsichtswerte.brauchbareDauer(zahl.longValue())) {
                klagen.add(Einsichtswerte.PFAD_DAUER + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine"
                        + " ganze Zahl von " + Einsichtswerte.KUERZESTE_DAUER + " bis "
                        + Einsichtswerte.LAENGSTE_DAUER + " Sekunden. "
                        + Wirtschaftsliste.esBleibt(dauer));
            } else {
                dauer = zahl.longValue();
            }
        }
        try {
            return fertig(new Einsichtswerte(dauer), klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt aus einer Zusage, die dieser Leser nicht selbst prueft. Heute kann
            // das nicht eintreten - die Zahl ist einzeln geprueft -, und genau darum
            // steht der Zweig hier: Wer eine zweite Zahl hinzufuegt und die Pruefung
            // vergisst, bekommt eine Klage und keine Ausnahme beim Start.
            klagen.add("moderation.einsehen: " + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt("die Vorgabe des Programms"));
            return fertig(Einsichtswerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Rechenprobe fuer den Startbericht.
     *
     * <p>Sie nennt die Frist in Minuten DAZU: Die Datei fuehrt Sekunden, weil eine
     * Sekunde die kleinste sinnvolle Einheit ist - aber "300" liest sich nicht als fuenf
     * Minuten, und genau das ist die Zahl, die ein Betreiber beurteilen soll.
     */
    static String rechenprobe(Einsichtswerte werte) {
        if (werte == null) {
            return "Einsehen: es gilt die Vorgabe des Programms.";
        }
        long sekunden = werte.dauerSekunden();
        return "Einsehen: " + sekunden + " Sekunden"
                + (sekunden >= 60L ? " (" + (sekunden / 60L) + " Min.)" : "")
                + " je /einsehen an; wer " + EinsichtBefehle.RECHT + " hat, sieht in"
                + " fremde Behaelter, nimmt aber nichts.";
    }

    /** Das Ergebnis - und JEDER Rueckweg geht hier durch. */
    private static Ergebnis fertig(Einsichtswerte werte, List<String> klagen,
                                   List<String> bericht) {
        // Dieselbe Bauart wie in Teamliste, Meldeliste und Strafliste, und aus dem
        // am 25.11.2026 gemessenen Grund: Ein frueher Rueckweg bei fehlendem Abschnitt
        // uebersprang dort die Ungereimtheiten, und der Server, dem die Auskunft am
        // meisten hilft - der ohne Eintrag - bekam sie nicht.
        bericht.addAll(werte.ungereimtheiten());
        return new Ergebnis(werte, klagen, bericht);
    }

    /** Welche Schluessel dieses Abschnitts in der Datei fehlen - fuer den Bericht. */
    private static void nenneFehlende(ConfigurationSection wurzel,
                                      List<String> bericht) {
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
