package de.freimarken.kern;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Zeile im Buchungsbuch: was geflossen ist, warum, und was danach dastand.
 *
 * <p>Der Betrag ist <b>vorzeichenbehaftet</b>: Eine Ausgabe ist negativ, eine Einnahme
 * positiv. Das ist nicht Geschmack, sondern die Voraussetzung fuer die einzige Zusage, mit
 * der sich Buchung und Protokoll gegenseitig halten - <b>die Summe aller Buchungen eines
 * eroeffneten Kontos ist sein Stand</b>. Waere der Betrag immer positiv und die Richtung
 * nur im Grund, muesste jede Auswertung die Richtung selbst nachschlagen, und eine von
 * ihnen wuerde es falsch machen.
 *
 * <p>{@code standNachher} ist Absicht und keine Redundanz. Ohne ihn liesse sich eine
 * einzelne Zeile nicht lesen, ohne alle vorherigen zu addieren - und eine Luecke im Buch
 * waere unsichtbar, weil es nichts gaebe, womit sie sich widerspricht.
 *
 * @param id           die laufende Nummer aus der Datenbank; 0 bei einer noch nicht
 *                     geschriebenen Buchung
 * @param zeitpunkt    wann gebucht wurde, in UTC - wie jeder Zeitpunkt in dieser Ablage
 * @param spieler      wessen Konto
 * @param grund        warum
 * @param betrag       vorzeichenbehaftet: Ausgabe negativ, Einnahme positiv
 * @param standNachher der Kontostand unmittelbar nach dieser Buchung
 */
public record Buchung(long id, Instant zeitpunkt, UUID spieler, Buchungsgrund grund,
                      long betrag, long standNachher) {

    public Buchung {
        if (zeitpunkt == null || spieler == null || grund == null) {
            throw new IllegalArgumentException("Eine Buchung ohne Zeitpunkt, Spieler oder"
                    + " Grund gibt es nicht");
        }
        // Die Richtungsregel steht HIER und ausdruecklich nicht zusaetzlich als Pruefung
        // in der Spaltendefinition: Welcher Grund in welche Richtung zeigt, entscheidet
        // Buchungsgrund, und eine zweite Fassung davon in SQL waeren zwei Stellen, die
        // dasselbe entscheiden.
        if (grund.istAusgabe() ? betrag > 0 : betrag < 0) {
            throw new IllegalArgumentException("Der Betrag " + betrag + " passt nicht zu "
                    + grund + " (" + grund.richtung() + ")");
        }
        if (standNachher < 0) {
            throw new IllegalArgumentException("Ein Konto kann nicht unter null stehen: "
                    + standNachher);
        }
    }

    /** Der Betrag ohne Vorzeichen - fuer eine Anzeige, die das Vorzeichen selbst setzt. */
    public long betragsgroesse() {
        return Math.abs(betrag);
    }
}
