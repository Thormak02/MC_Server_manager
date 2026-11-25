package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Was am Einsehen einstellbar ist (Plan 12c.3, Schritt 16a Teil D).
 *
 * <p><b>EINE Zahl, und sie ist eine Bremse gegen das Vergessen:</b> Wie lange die
 * Einsicht anbleibt, nachdem ein Moderator sie eingeschaltet hat. Ohne Frist haette er
 * nach einem einzigen {@code /einsehen} dauerhaft Zugriff auf jede fremde Kiste des
 * Servers - und zwar ohne es zu merken, denn im Spiel sieht man einen Modus nicht.
 *
 * <p><b>Die Frist ist kein Misstrauen, sondern die Gestalt des Werkzeugs.</b> Einsehen
 * geschieht WAEHREND einer Untersuchung: Ein Moderator liest eine Meldung, springt hin,
 * sieht in die Kiste, entscheidet. Das dauert Minuten. Ein Zustand, der laenger gilt als
 * die Tat, fuer die er gedacht war, ist eine offene Tuer, die niemand mehr sieht.
 *
 * <p><b>Was NICHT einstellbar ist: ob die Einsicht etwas aendern darf.</b> Plan 12c.3
 * sagt <i>"hinsehen duerfen, anfassen nicht"</i>, und das ist der ganze Zweck. Ein
 * Schalter dafuer waere die Einstellung, mit der ein Betreiber aus dem Einsichtsrecht ein
 * Nehmrecht macht, ohne es zu bemerken.
 *
 * @param dauerSekunden wie lange die Einsicht nach dem Einschalten gilt
 */
public record Einsichtswerte(long dauerSekunden) {

    /** Wie lange die Einsicht anbleibt. */
    public static final String PFAD_DAUER = "moderation.einsehen.dauer-sekunden";

    /**
     * Die kuerzeste brauchbare Frist - zehn Sekunden.
     *
     * <p>Darunter ist der Modus abgelaufen, bevor der Moderator die Kiste erreicht:
     * Hinspringen, Umsehen und Anklicken dauert laenger. Eine 0 waere ein Werkzeug, das
     * nie wirkt, und genau so eines sucht niemand im Protokoll.
     */
    public static final long KUERZESTE_DAUER = 10L;

    /**
     * Die laengste erlaubte Frist - eine Stunde.
     *
     * <p>Eine Grenze, weil ein vertippter Wert sonst aus der Bremse das Gegenteil macht:
     * Wer {@code 3600000} statt {@code 300} tippt, hat eine Einsicht, die 41 Tage gilt -
     * und das ist der Zustand, gegen den die Frist gebaut ist.
     */
    public static final long LAENGSTE_DAUER = 3600L;

    /**
     * Die Vorgabe des Programms - fuenf Minuten.
     *
     * <p>Gewaehlt nach der Tat, nicht nach dem Gefuehl: Eine Meldung lesen, hinspringen,
     * in zwei oder drei Kisten sehen und entscheiden - das ist der Vorgang, und er
     * dauert Minuten. Wer laenger braucht, tippt {@code /einsehen} noch einmal; das ist
     * ein Tastendruck und eine Zeile im Protokoll.
     */
    public static final Einsichtswerte VORGABE = new Einsichtswerte(300L);

    public Einsichtswerte {
        if (!brauchbareDauer(dauerSekunden)) {
            throw new IllegalArgumentException("Die Einsicht gilt zwischen "
                    + KUERZESTE_DAUER + " und " + LAENGSTE_DAUER + " Sekunden, nicht "
                    + dauerSekunden + ". Eine 0 waere ein Werkzeug, das nie wirkt, und"
                    + " eine zu grosse Zahl ist genau die offene Tuer, gegen die die"
                    + " Frist gebaut ist.");
        }
    }

    /**
     * Was an diesen Werten auffaellt, ohne dass es ein Fehler waere.
     *
     * <p>Ein Bericht und keine Klage - dieselbe Linie wie bei {@link Strafwerte} und
     * {@link Meldewerte}.
     */
    public List<String> ungereimtheiten() {
        List<String> saetze = new ArrayList<>();
        if (dauerSekunden >= 1800L) {
            saetze.add(PFAD_DAUER + " ist " + dauerSekunden + " Sekunden ("
                    + (dauerSekunden / 60L) + " Minuten). Ein Moderator, der die"
                    + " Einsicht einschaltet und es vergisst, sieht so lange in jede"
                    + " fremde Kiste - und im Spiel sieht man einen Modus nicht.");
        }
        if (dauerSekunden <= 30L) {
            saetze.add(PFAD_DAUER + " ist " + dauerSekunden + " Sekunden. Das reicht"
                    + " selten fuer Hinspringen und Umsehen; der Moderator tippt"
                    + " /einsehen dann mehrmals je Untersuchung.");
        }
        return List.copyOf(saetze);
    }

    /** Dieselben Werte mit anderer Dauer - fuer das Neuladen und fuer Proben. */
    public Einsichtswerte mitDauer(long sekunden) {
        return new Einsichtswerte(sekunden);
    }

    /** Ob aus dieser Zahl eine Frist werden kann - fuer den Leser der Datei. */
    public static boolean brauchbareDauer(long sekunden) {
        return sekunden >= KUERZESTE_DAUER && sekunden <= LAENGSTE_DAUER;
    }
}
