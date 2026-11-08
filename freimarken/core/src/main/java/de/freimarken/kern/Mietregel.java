package de.freimarken.kern;

import java.time.LocalDate;

/**
 * Die EINE Stelle, an der ueber Laufzeiten von Mietvertraegen entschieden wird.
 *
 * <p>Dieselbe Rolle wie {@link Serienregel} beim Tagesbonus: Es gibt genau drei Fragen -
 * ist der Vertrag abgelaufen, darf er verlaengert werden, und bis wann laeuft er dann. Wer
 * eine davon ein zweites Mal beantwortet, baut eine zweite Wahrheit; und bei Mietvertraegen
 * heisst eine zweite Wahrheit, dass der taegliche Lauf einen Laden raeumt, den der Befehl
 * gerade als gueltig gemeldet hat.
 *
 * <p><b>Gerechnet wird in Tagen und nicht in Zeitpunkten</b> - wie beim Tagesbonus und aus
 * demselben Grund (Plan 8.8): Die Tagesgrenze ist eine eingestellte Zone, und ein
 * Zeitpunkt haette eine zweite daneben. Diese Klasse kennt darum keine Uhr; "heute" kommt
 * immer von aussen herein, aus {@link Tagesgrenze#tagVon}.
 *
 * <p><b>{@code laeuftBis} ist EINGESCHLOSSEN.</b> Am Tag, der dort steht, gilt der Vertrag
 * noch. Abgelaufen ist er erst am Tag danach. Das ist die Festlegung, auf der alle drei
 * Fragen beruhen, und sie steht hier und nicht in der Datenbank.
 */
public final class Mietregel {

    /**
     * Hoechstens ein Jahr - dieselbe Grenze wie bei der Serienluecke.
     *
     * <p>366 und nicht 365, damit ein Schaltjahr nicht als Tippfehler aussieht.
     */
    public static final int HOECHSTE_DAUER = 366;

    private Mietregel() {
    }

    /**
     * Wie viele Tage der Vertrag noch gilt.
     *
     * <p>0 am letzten Tag - er gilt dann noch. Negativ, wenn er abgelaufen ist, und der
     * Betrag sagt, seit wie vielen Tagen.
     */
    public static long restTage(LocalDate laeuftBis, LocalDate heute) {
        if (laeuftBis == null || heute == null) {
            throw new IllegalArgumentException("Ohne Ende und ohne heute ist keine"
                    + " Restlaufzeit zu rechnen");
        }
        return laeuftBis.toEpochDay() - heute.toEpochDay();
    }

    /** Ob der Vertrag abgelaufen ist - erst am Tag NACH {@code laeuftBis}. */
    public static boolean abgelaufen(LocalDate laeuftBis, LocalDate heute) {
        return restTage(laeuftBis, heute) < 0L;
    }

    /**
     * Ob jetzt verlaengert werden darf.
     *
     * <p><b>Nur, wenn weniger als eine ganze Mietdauer uebrig ist.</b> Das ist die
     * Entscheidung dieser Regel, und sie braucht keine eigene Zahl in der
     * {@code config.yml}: Ohne sie koennte ein reicher Spieler dreissigmal verlaengern und
     * eine Ladeneinheit fuer Jahre binden, obwohl er sie nicht benutzt. Mit ihr liegt das
     * Ende immer hoechstens zwei Mietdauern voraus - bei 30 Tagen also hoechstens 60.
     *
     * <p><b>Ein abgelaufener Vertrag darf verlaengert werden</b>, und das ist gewollt:
     * Zwischen dem Ablauf und dem taeglichen Lauf steht die Zeile noch da. Wer in diesem
     * Fenster verlaengert, rettet seinen Laden. Freie Tage bekommt er dadurch nicht -
     * dafuer sorgt {@link #verlaengert}.
     */
    public static boolean darfVerlaengern(LocalDate laeuftBis, LocalDate heute,
            int dauerTage) {
        return restTage(laeuftBis, heute) < dauerTage;
    }

    /**
     * Bis wann der Vertrag nach einer Verlaengerung laeuft.
     *
     * <p>Gerechnet vom spaeteren der beiden Tage: Wer frueh verlaengert, verliert seine
     * Resttage NICHT (gerechnet ab {@code laeuftBis}), und wer nach dem Ablauf verlaengert,
     * bekommt die verstrichenen Tage nicht geschenkt (gerechnet ab heute). Beides derselbe
     * Ausdruck, und darum gibt es keinen Fall, in dem nur eine der zwei Absichten gilt.
     */
    public static LocalDate verlaengert(LocalDate laeuftBis, LocalDate heute,
            int dauerTage) {
        if (!brauchbareDauer(dauerTage)) {
            throw new IllegalArgumentException("Um " + dauerTage
                    + " Tage ist nicht zu verlaengern - erlaubt ist 1 bis "
                    + HOECHSTE_DAUER + ".");
        }
        LocalDate ab = laeuftBis.isBefore(heute) ? heute : laeuftBis;
        return ab.plusDays(dauerTage);
    }

    /**
     * Bis wann ein frisch abgeschlossener Vertrag laeuft.
     *
     * <p>{@code dauerTage - 1} Tage nach heute, und das ist kein Rechenfehler: Weil
     * {@code laeuftBis} eingeschlossen ist, gilt ein Vertrag mit einem Tag Dauer genau
     * heute. Ohne das Minus waere jeder Abschluss einen Tag zu lang, und bei einer Dauer
     * von einem Tag waeren es zwei - also das Doppelte.
     */
    public static LocalDate laeuftBisAb(LocalDate beginn, int dauerTage) {
        if (beginn == null) {
            throw new IllegalArgumentException("Ohne Beginn ist kein Ende zu rechnen");
        }
        if (!brauchbareDauer(dauerTage)) {
            throw new IllegalArgumentException("Eine Mietdauer von " + dauerTage
                    + " Tagen ist keine - erlaubt ist 1 bis " + HOECHSTE_DAUER + ".");
        }
        return beginn.plusDays(dauerTage - 1L);
    }

    /** Was ein ganzer Mietzeitraum an Miete kostet. */
    public static long vollerZeitraum(long miete, int dauerTage) {
        return Math.multiplyExact(miete, (long) dauerTage);
    }

    public static boolean brauchbareDauer(int tage) {
        return tage >= 1 && tage <= HOECHSTE_DAUER;
    }
}
