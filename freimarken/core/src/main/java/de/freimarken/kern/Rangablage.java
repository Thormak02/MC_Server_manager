package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Wo der Rang eines Spielers liegt - der Vertrag, den beide Umsetzungen halten.
 *
 * <p>Fuenf Zusagen, und jede steht hier, weil ihr Gegenteil einen Fehler ergeben hat oder
 * ergeben wuerde:
 *
 * <ol>
 *   <li><b>Die Ablage weiss nicht, welcher Rang HOEHER ist.</b> Die Reihenfolge steht in
 *       der Einstellungsdatei und beantwortet {@link Rangwerte}; dass ein Aufstieg nur
 *       nach oben geht, prueft {@link Rangregel#aufstieg}. Haette die Ablage eine eigene
 *       Vorstellung davon - etwa eine Nummernspalte -, gaebe es die Leiter zweimal, und
 *       bei einer Aenderung in der Datei waere die in der Datenbank die falsche.
 *       Dieselbe Begruendung, aus der {@link Berufsablage} die Rolle eines Berufes nicht
 *       kennt.</li>
 *   <li><b>KEINE Zeile heisst: die unterste Stufe</b>, und nicht "kein Rang". Geschrieben
 *       wird erst beim ersten Aufstieg. Zwei Gruende: Eine Zeile je Besucher waere eine
 *       Tabelle, die nur Platz braucht - und wer eine Stufe aus der Datei nimmt, strandet
 *       sonst jeden, der auf ihr stand. Der Rueckfall steht an EINER Stelle, in
 *       {@link Rangstand#stufeIn}.</li>
 *   <li><b>Geschrieben wird gegen einen ERWARTETEN Stand</b>, nie lesen-rechnen-schreiben.
 *       Wer liest, in Java rechnet und zurueckschreibt, verliert bei zwei gleichzeitigen
 *       Zuegen einen: Der Tageslauf und ein Befehl im selben Augenblick ergaeben sonst
 *       einen Rang, den niemand vergeben hat. Dieselbe Zusage wie bei
 *       {@link Kasse#abbuchen} und mit derselben Antwort - {@code false} heisst "dieser
 *       Stand gilt nicht mehr", und dann ist NICHTS passiert.</li>
 *   <li><b>EINE Zeile je Spieler, kein Verlauf.</b> Der Rang ist ein Zustand und keine
 *       Geschichte; auf die Frage "welchen Rang hat er" muss es genau eine Antwort geben.
 *       Mit einer Zeile je erreichter Stufe waere die Antwort "die juengste" - und die
 *       waere nach einer umsortierten Leiter die falsche. Wer den Verlauf will, bekommt
 *       eine zweite Tabelle wie das Blockbuch, nicht eine zweite Bedeutung fuer diese.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt im
 *       {@link Antrag} von aussen, wie bei {@link Berufsablage} und
 *       {@link Spielzeitablage}. Eine Ablage mit eigener Uhr liesse sich nicht pruefen,
 *       ohne zu warten.</li>
 * </ol>
 */
public interface Rangablage {

    /**
     * Ein Aufstieg auf EINE Stufe - geprueft beim Bauen, nicht beim Schreiben.
     *
     * @param spieler wer
     * @param rang    der Schluessel der Stufe, kleingeschrieben wie in der Datei
     * @param jetzt   der Zeitpunkt, den die Ablage eintragen soll
     */
    record Antrag(UUID spieler, String rang, Instant jetzt) {

        public Antrag {
            if (spieler == null || jetzt == null) {
                throw new IllegalArgumentException("Spieler und Zeitpunkt sind Pflicht;"
                        + " die Ablage hat keine eigene Uhr");
            }
            if (rang == null || rang.isBlank()) {
                // Ein Antrag auf "keinen Rang" waere ein ABSTIEG, und den gibt es nicht.
                // Wer die Zeile loeschen will, hat eine Verwaltungsaufgabe und keinen
                // Aufstieg - die geht nicht durch diesen Weg.
                throw new IllegalArgumentException("Ein Antrag ohne Rang. Die Zeile"
                        + " wegzunehmen waere ein Abstieg, und den gibt es nicht.");
            }
            // Kleingeschrieben hier, damit die Ablage nicht ein zweites Mal
            // normalisiert. Zwei Stellen, die denselben Namen zurechtbiegen, biegen ihn
            // eines Tages verschieden.
            rang = rang.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Auf welcher Stufe dieser Spieler steht.
     *
     * <p>Fuer einen Spieler, von dem die Ablage nichts weiss, {@link Rangstand#leer} -
     * kein {@code null} und kein Wurf. Wer nie aufgestiegen ist, ist der Normalfall.
     */
    Rangstand lies(UUID spieler);

    /**
     * Traegt den Aufstieg ein - alles oder nichts.
     *
     * <p><b>Die Ablage prueft NICHT, ob der Rang hoeher ist als der bisherige</b> - sie
     * kennt die Leiter nicht (erste Zusage). Dass nur nach oben geschrieben wird, ist
     * Sache von {@link Rangregel#aufstieg}; hier waere es eine zweite, halbe Fassung
     * derselben Regel.
     *
     * @param erwartet der Stand, auf dem die Regel geurteilt hat
     * @return {@code false}, wenn dieser Stand nicht mehr gilt; dann ist NICHTS passiert
     */
    boolean setze(Antrag antrag, Rangstand erwartet);

    /**
     * Die eine Pruefung, die beide Umsetzungen von {@link #setze} machen.
     *
     * <p>Hier und nicht zweimal: Zwei Fassungen derselben Pruefung weichen eines Tages
     * ab, und dann verhaelt sich der fluechtige Betrieb anders als der echte - gefunden
     * wird das nicht im Testlauf, sondern von einem Spieler.
     */
    static void pruefePflichten(Antrag antrag, Rangstand erwartet) {
        if (antrag == null) {
            throw new IllegalArgumentException("Ohne Antrag gibt es nichts zu schreiben");
        }
        if (erwartet == null) {
            throw new IllegalArgumentException("Ohne erwarteten Stand gaebe es nur"
                    + " lesen-rechnen-schreiben, und das verliert bei zwei gleichzeitigen"
                    + " Zuegen einen");
        }
        if (!erwartet.spieler().equals(antrag.spieler())) {
            throw new IllegalArgumentException("Der erwartete Stand gehoert "
                    + erwartet.spieler() + ", der Antrag " + antrag.spieler());
        }
    }
}
