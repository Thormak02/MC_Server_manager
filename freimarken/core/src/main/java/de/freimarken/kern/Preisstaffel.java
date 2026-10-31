package de.freimarken.kern;

/**
 * Was eine Parzelle kostet - und was das Zusammenlegen kostet.
 *
 * <p>Die erste Parzelle ist gratis: Wer nur bauen will, zahlt nie etwas. Jede weitere
 * verdoppelt den Preis. Das ist keine Willkuer, sondern die Bremse gegen Flaechenhorten -
 * die zehnte Parzelle kostet dann mehr, als ein Spieler in absehbarer Zeit verdient, und
 * die Marktstadt laeuft nicht voll, waehrend drei Leute alles halten.
 *
 * <p>Die Gebuehr fuers Zusammenlegen steigt mit der Gruppengroesse. Sie ist eine der
 * wenigen Senken, die ausschliesslich diejenigen zahlen, die ohnehin viel besitzen.
 *
 * <p>Alle Betraege in Mark. Reine Rechnung, kein Spielbezug - deshalb hier und nicht im
 * Paper-Teil.
 */
public final class Preisstaffel {

    /** Preis der zweiten Parzelle; jede weitere verdoppelt. */
    public static final long GRUNDPREIS = 25_000L;

    /** Gebuehr je Feld der neuen Gruppe beim Zusammenlegen. */
    public static final long ZUSAMMENLEGUNG_JE_FELD = 10_000L;

    /**
     * Ab so vielen bereits besessenen Parzellen wird nicht weiter verdoppelt.
     *
     * <p>Ohne Deckel laeuft die Verdopplung in den Ueberlauf: 25.000 mal 2 hoch 63 passt in
     * kein {@code long}. Der Deckel ist also keine Spielbalance, sondern Arithmetik - die
     * Balance macht die Obergrenze je Rang, und die liegt weit darunter.
     */
    public static final int MAX_VERDOPPLUNGEN = 40;

    private Preisstaffel() {
    }

    /**
     * Was die naechste Parzelle kostet.
     *
     * @param bereitsBesessen wie viele Parzellen der Spieler schon hat, nie negativ
     * @return Preis in Mark; 0 fuer die erste
     * @throws IllegalArgumentException bei negativer Eingabe
     */
    public static long preisFuerNaechste(int bereitsBesessen) {
        if (bereitsBesessen < 0) {
            throw new IllegalArgumentException(
                    "Anzahl besessener Parzellen kann nicht negativ sein: " + bereitsBesessen);
        }
        if (bereitsBesessen == 0) {
            return 0L;
        }
        int verdopplungen = Math.min(bereitsBesessen - 1, MAX_VERDOPPLUNGEN);
        return GRUNDPREIS << verdopplungen;
    }

    /**
     * Was es kostet, eine Gruppe aus {@code felder} Parzellen zusammenzulegen.
     *
     * @param felder Zahl der Felder NACH dem Zusammenlegen, mindestens 2
     * @return Gebuehr in Mark
     * @throws IllegalArgumentException wenn weniger als zwei Felder zusammengelegt werden
     */
    public static long zusammenlegungsGebuehr(int felder) {
        if (felder < 2) {
            throw new IllegalArgumentException(
                    "Zusammenlegen braucht mindestens zwei Felder, nicht " + felder);
        }
        return ZUSAMMENLEGUNG_JE_FELD * felder;
    }

    /**
     * Was alle Parzellen zusammen gekostet haben, wenn jemand {@code anzahl} davon haelt.
     *
     * <p>Fuer Anzeige und Wirtschaftsbericht: Was steckt an Mark in dieser Flaeche?
     *
     * @param anzahl Zahl der gehaltenen Parzellen, nie negativ
     * @return Summe in Mark
     */
    public static long gesamtpreisFuer(int anzahl) {
        if (anzahl < 0) {
            throw new IllegalArgumentException("Anzahl kann nicht negativ sein: " + anzahl);
        }
        long summe = 0L;
        for (int i = 0; i < anzahl; i++) {
            summe += preisFuerNaechste(i);
        }
        return summe;
    }
}
