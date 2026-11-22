package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Welche Stationen ein Spieler gesehen hat - der Vertrag beider Umsetzungen (Plan 10.2).
 *
 * <p>Vier Zusagen, und jede steht hier, weil ihr Gegenteil einen Fehler ergaebe:
 *
 * <ol>
 *   <li><b>EINE Zeile je Spieler und Station</b>, nicht eine je Spieler mit einer Zahl.
 *       Eine Zahl saehe nicht, WELCHE Station gesehen wurde - und damit auch nicht, dass
 *       eine neu hinzugekommene fehlt. Der Betreiber darf seinen Weg aendern; mit einer
 *       Zahl waere der Rundgang nach dem Hinzufuegen einer Station weiter abgeschlossen.
 *       Dasselbe Muster wie bei den erfuellten Herausforderungen von Wolkenwerder.</li>
 *   <li><b>Die Ablage weiss NICHT, welche Stationen es gibt</b>, und schon gar nicht, wie
 *       viele einen Rundgang ausmachen. Das steht in der {@code config.yml} und
 *       beantwortet {@link Rundgangsverzeichnis}. Haette die Ablage eine eigene
 *       Vorstellung davon, gaebe es den Weg zweimal, und nach einer Aenderung in der
 *       Datei waere der in der Datenbank der falsche. Dieselbe Begruendung, aus der
 *       {@link Rangablage} die Leiter nicht kennt.</li>
 *   <li><b>Gesehen ist gesehen - es gibt kein Zuruecknehmen.</b> Eine Station wird
 *       einmal betreten und bleibt eingetragen; wer den Rundgang wiederholen will, hat
 *       nichts davon, und wer ihn rueckgaengig machen will, hat eine Verwaltungsaufgabe
 *       und keinen Rundgang. Darum gibt es nur {@link #merke} und kein Loeschen.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt von
 *       aussen, wie bei {@link Rangablage} und {@link Anmeldeablage}. Eine Ablage mit
 *       eigener Uhr liesse sich nicht pruefen, ohne zu warten.</li>
 * </ol>
 */
public interface Rundgangsablage {

    /**
     * Welche Stationen dieser Spieler gesehen hat - die Namen, kleingeschrieben.
     *
     * <p>Fuer einen Spieler, von dem die Ablage nichts weiss, eine LEERE Menge - kein
     * {@code null} und kein Wurf. Wer noch nirgends war, ist der Normalfall.
     *
     * <p>Darin koennen Namen stehen, die es in der {@code config.yml} nicht mehr gibt:
     * Der Betreiber darf seinen Weg aendern. Was davon zaehlt, entscheidet
     * {@link Rundgangsverzeichnis#davonVorhanden} und nicht diese Methode.
     */
    Set<String> gesehen(UUID spieler);

    /**
     * Traegt eine gesehene Station ein.
     *
     * @param station der Name der Station, kleingeschrieben wie in der Datei
     * @param jetzt   der Zeitpunkt, den die Ablage eintragen soll
     * @return {@code true}, wenn sie NEU war - nur dann sagt der Spieler etwas darueber.
     *         {@code false} heisst: Er war schon da, und dann ist nichts passiert
     */
    boolean merke(UUID spieler, String station, Instant jetzt);

    /**
     * Die eine Pruefung, die beide Umsetzungen von {@link #merke} machen.
     *
     * <p>Hier und nicht zweimal: Zwei Fassungen derselben Pruefung weichen eines Tages
     * ab, und dann verhaelt sich der fluechtige Betrieb anders als der echte - gefunden
     * wird das nicht im Testlauf, sondern von einem Spieler.
     *
     * @return der Name, kleingeschrieben - die Umsetzung legt genau diesen ab
     */
    static String pruefeUndNormalisiere(UUID spieler, String station, Instant jetzt) {
        if (spieler == null || jetzt == null) {
            throw new IllegalArgumentException("Spieler und Zeitpunkt sind Pflicht;"
                    + " die Ablage hat keine eigene Uhr");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Eine gesehene Station ohne Namen - der"
                    + " Name ist ihr Schluessel");
        }
        String rein = station.trim().toLowerCase(Locale.ROOT);
        if (rein.length() > Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE) {
            // Hier und nicht erst in der Datenbank: Ohne diese Zeile nahm die
            // Speicherfassung an, was MariaDB abweist - der fluechtige Betrieb
            // verhielte sich anders als der echte.
            throw new IllegalArgumentException("Der Stationsname \"" + rein + "\" ist"
                    + " laenger als " + Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE
                    + " Zeichen - so lang ist die Spalte in der Ablage.");
        }
        return rein;
    }
}
