package de.freimarken.kern;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Die Arten von Lebenszeichen, aus denen die Messstelle Taetigkeit erkennt.
 *
 * <p>Der Plan nennt in 8.8 drei Signale, mit denen aus gemessener ANWESENHEIT eine
 * gemessene TAETIGKEIT wird: "Abbauleistung, Wiederholungsmuster, geoeffnete Oberflaechen".
 * Zwei davon sind hier eine Art ({@link #ABBAUEN}, {@link #OEFFNEN}); das
 * Wiederholungsmuster ist keine, sondern der Grund, warum es ueberhaupt mehrere gibt:
 * Ein Makro gibt immer DASSELBE Lebenszeichen, ein Mensch mehrere verschiedene. Gezaehlt
 * wird deshalb nicht die Menge der Zeichen, sondern ihre VIELFALT - siehe
 * {@link Messregel#taetig}.
 *
 * <p><b>Eine Liste und kein {@code switch}.</b> Welche Arten ueberhaupt zaehlen, steht in
 * der {@code config.yml} ({@link Messwerte#PFAD_ARTEN}); wer eine Art auf seinem Server
 * missbraucht sieht, nimmt sie dort heraus, ohne das Programm anzufassen. Darum traegt
 * jede Art nur ihren Schluessel und keine Zahl: Ein Gewicht je Art waere eine zweite
 * Stellschraube fuer dieselbe Frage.
 *
 * <p><b>Keine Art fuer "hat sich bewegt" im Sinne von Ereignissen.</b> {@link #BEWEGEN}
 * meint eine zurueckgelegte STRECKE und nicht die Zahl der Bewegungsereignisse: Eine
 * gewackelte Maus und ein beschwertes Tastenbrett erzeugen Ereignisse im Dutzend, ohne den
 * Spieler einen Block weit zu tragen. Wie weit es sein muss, steht in
 * {@link Messwerte#PFAD_STRECKE}.
 */
public enum Lebenszeichen {

    /**
     * Der Spieler ist eine Strecke weit gekommen.
     *
     * <p>Gemessen wird der Abstand zwischen Anfang und Ende des Taktes und nicht der
     * gelaufene Weg: Wer im Kreis laeuft, legt Weg zurueck und kommt nicht weiter, und
     * genau das tut ein Laufmakro an einer Wand.
     */
    BEWEGEN("bewegen"),

    /** Der Spieler hat einen Block abgebaut - die "Abbauleistung" aus Plan 8.8. */
    ABBAUEN("abbauen"),

    /** Der Spieler hat einen Block gesetzt. */
    SETZEN("setzen"),

    /** Der Spieler hat eine Oberflaeche geoeffnet - Truhe, Werkbank, Ofen, Trichter. */
    OEFFNEN("oeffnen"),

    /** Der Spieler hat einen Block oder Gegenstand benutzt. */
    BENUTZEN("benutzen"),

    /** Der Spieler hat ein Wesen angegriffen. */
    KAEMPFEN("kaempfen"),

    /** Der Spieler hat geschrieben oder einen Befehl gegeben. */
    SPRECHEN("sprechen");

    private final String schluessel;

    Lebenszeichen(String schluessel) {
        this.schluessel = schluessel;
    }

    /**
     * Der Name in der {@code config.yml} und in der Sprachdatei - klein und ohne Umlaut.
     *
     * <p>Nicht {@code name().toLowerCase()}: Dann haengt der Name in der Datei des
     * Betreibers am Namen der Konstante, und ein Umbenennen im Programm machte stumm jede
     * Zeile seiner Datei unbrauchbar.
     */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Welche Art diesen Schluessel traegt - oder {@code null} fuer einen unbekannten.
     *
     * <p>Gibt {@code null} und wirft nicht: Der Leser der {@code config.yml} muss eine
     * unbekannte Zeile BEKLAGEN und die uebrigen stehen lassen koennen. Dieselbe
     * Entscheidung wie bei der Lohnliste.
     */
    public static Lebenszeichen ausSchluessel(String schluessel) {
        if (schluessel == null) {
            return null;
        }
        String gesucht = schluessel.trim().toLowerCase(java.util.Locale.ROOT);
        for (Lebenszeichen art : values()) {
            if (art.schluessel.equals(gesucht)) {
                return art;
            }
        }
        return null;
    }

    /** Alle Arten, in der Reihenfolge der Aufzaehlung - die Vorgabe des Programms. */
    public static Set<Lebenszeichen> alle() {
        return new LinkedHashSet<>(java.util.Arrays.asList(values()));
    }
}
