package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Ob ein Spieler die Regeln angenommen hat - und WELCHE Fassung.
 *
 * <p><b>Ein Stand ohne Annahme ist der Normalfall und kein Fehler.</b> Wer noch nie
 * angenommen hat, hat keine Zeile in der Ablage, und dann steht hier {@code null}.
 * Dieselbe Entscheidung wie bei {@link Rangstand}: Eine Zeile je Besucher waere eine
 * Tabelle, die nur Platz braucht.
 *
 * <p><b>Die FASSUNG ist der Grund, warum hier mehr steht als ein Zeitpunkt.</b> Plan 10.1
 * verlangt die Annahme von "Regeln UND Nutzungsbedingungen". Aendert der Betreiber die
 * Regeln, ist eine Zustimmung von vorher keine Zustimmung zu den neuen - sie waere eine
 * stille Halbheit, die aussieht wie ein Nachweis. Darum traegt jede Annahme die Fassung,
 * die damals galt; welche HEUTE gilt, sagt die Einstellungsdatei und nicht dieser Satz.
 * Die Trennung ist dieselbe wie beim Rang, der die Leiter nicht kennt.
 *
 * <p><b>Fassung und Zeitpunkt sind untrennbar</b>, beide oder keiner. Eine Fassung ohne
 * Zeitpunkt waere eine Zustimmung ohne Datum - also kein Nachweis -, ein Zeitpunkt ohne
 * Fassung eine Zustimmung zu nichts.
 *
 * @param spieler      wessen Stand das ist
 * @param fassung      die Fassung, die er angenommen hat, kleingeschrieben wie in der
 *                     Datei - oder {@code null} fuer "hat nie angenommen"
 * @param angenommenAm wann er sie angenommen hat, oder {@code null} zusammen mit der
 *                     Fassung
 */
public record Anmeldestand(UUID spieler, String fassung, Instant angenommenAm) {

    /**
     * So lang darf eine Fassungsbezeichnung sein - so lang ist die Spalte in der Ablage.
     *
     * <p>Zweiunddreissig Zeichen reichen fuer ein Datum, eine Nummer oder beides
     * ({@code 2026-11-21}, {@code 3}, {@code 2026-11-21-agb2}). Was der Betreiber
     * hineinschreibt, ist seine Sache - das Programm vergleicht nur.
     */
    public static final int HOECHSTE_FASSUNGSLAENGE = 32;

    /**
     * Welche Zeichen eine Fassung tragen darf.
     *
     * <p>Dasselbe Muster wie bei einem Rangschluessel, nur dass eine Fassung auch mit
     * einer Ziffer beginnen darf - ein Datum tut das. Punkte sind erlaubt, damit
     * {@code 1.2} geht.
     */
    public static final String MUSTER = "[a-z0-9][a-z0-9._-]*";

    public Anmeldestand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keinen Anmeldestand");
        }
        if ((fassung == null) != (angenommenAm == null)) {
            // Beides oder keines. Ein halber Stand kaeme aus einer Zeile, die es in der
            // Tabelle nicht geben kann - und er liefe erst beim Schreiben auf, als
            // Datenbankfehler mitten im Spiel.
            throw new IllegalArgumentException("Fassung und Zeitpunkt gehoeren zusammen:"
                    + " fassung=" + fassung + ", angenommen_am=" + angenommenAm + ". Wer"
                    + " nie angenommen hat, hat KEINEN von beiden.");
        }
        if (fassung != null) {
            fassung = fassung.trim().toLowerCase(Locale.ROOT);
            if (!fassung.matches(MUSTER)) {
                throw new IllegalArgumentException("Die Fassung \"" + fassung + "\" von "
                        + spieler + " taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                        + " Punkt, Unterstrich und Bindestrich.");
            }
            if (fassung.length() > HOECHSTE_FASSUNGSLAENGE) {
                // Dieselbe Naht wie beim Rang: Die Spalte ist VARCHAR(32). Hier noch
                // einmal, weil ein Stand auch aus der ABLAGE kommt und nicht nur aus
                // einem Antrag - und eine von Hand gefuellte Datenbank ist kein Antrag.
                throw new IllegalArgumentException("Die Fassung \"" + fassung + "\" von "
                        + spieler + " ist laenger als " + HOECHSTE_FASSUNGSLAENGE
                        + " Zeichen - so lang ist die Spalte in der Ablage.");
            }
        }
    }

    /**
     * Der Stand eines Spielers, von dem die Ablage nichts weiss.
     *
     * <p>Das ist jeder, der zum ersten Mal ankommt - und jeder, der vor der Anmeldung
     * schon einmal da war.
     */
    public static Anmeldestand leer(UUID spieler) {
        return new Anmeldestand(spieler, null, null);
    }

    /**
     * Ob er die Fassung angenommen hat, die HEUTE gilt.
     *
     * <p><b>Die eine Frage, an der alles haengt</b> - der Bundesmarkt fragt sie bei jedem
     * Klick, und das Formular beim Beitritt. Sie vergleicht die abgelegte Fassung mit der
     * geltenden: Wer eine aeltere angenommen hat, hat die heutigen Regeln NICHT
     * angenommen und wird wieder gefragt.
     *
     * <p>Darum steht hier ein Vergleich und kein {@code angenommenAm != null}: Jenes
     * waere die Antwort "hat irgendwann irgendetwas angenommen", und genau die macht aus
     * einer Regelaenderung eine stille Halbheit.
     *
     * @param geltend die Fassung aus der Einstellungsdatei, kleingeschrieben
     */
    public boolean hatAngenommen(String geltend) {
        if (geltend == null || geltend.isBlank()) {
            throw new IllegalArgumentException("Ohne geltende Fassung ist die Frage nicht"
                    + " zu beantworten - sie steht in der Einstellungsdatei");
        }
        return fassung != null
                && fassung.equals(geltend.trim().toLowerCase(Locale.ROOT));
    }

    /** Ob er je etwas angenommen hat - fuer die Auskunft, nicht fuer den Riegel. */
    public boolean jeAngenommen() {
        return fassung != null;
    }
}
