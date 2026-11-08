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

    /**
     * Hoechstens 366 Ausfaelle bis zur Raeumung - dieselbe Zahl wie bei der Dauer.
     *
     * <p>Eine Schranke ueber einem Jahr waere keine: Der Vertrag laeuft vorher ab, und
     * dann raeumt ihn der Ablauf und nicht der Verzug. Die Zahl steht trotzdem so hoch,
     * weil "nachsichtig" eine zulaessige Einstellung ist - ein Betreiber darf 30 setzen,
     * wenn er einen Monat Geduld haben will.
     */
    public static final int HOECHSTE_SCHRANKE = 366;

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
     * {@code config.yml}: Ohne sie koennte ein reicher Spieler beliebig oft verlaengern
     * und eine Ladeneinheit fuer Jahre binden, obwohl er sie nicht benutzt. Mit ihr liegt
     * das Ende immer hoechstens zwei Mietdauern MINUS einen Tag voraus - bei 7 Tagen also
     * hoechstens 13, nicht 14. Das Minus kommt daher, dass {@code laeuftBis}
     * eingeschlossen ist; gezaehlt ist es in {@code MietregelTest.nieWeiterAlsZweiDauern}.
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

    /**
     * Ob dieser Vertrag geraeumt werden muss - die Schranke gegen den Zahlungsausfall.
     *
     * <p><b>{@code >=} und nicht {@code ==}</b>, und das ist nicht Vorsicht, sondern ein
     * gemessener Fall: Der Betreiber darf {@code laeden.ausfaelle-bis-raeumung} im
     * Betrieb senken. Dann stehen Vertraege da, deren Ausfallzahl die neue Schranke
     * schon ueberschritten hat. Mit {@code ==} ginge der Lauf an ihnen vorbei, und sie
     * stuenden fuer immer in Verzug, ohne je geraeumt zu werden - die Halle waere voll
     * mit Laeden, die niemand bezahlt und niemand bekommt.
     *
     * @param ausfaelle die gezaehlten Tage ohne Zahlung, nach dem heutigen Ausfall
     * @param schranke  wie viele Ausfaelle in Folge die Raeumung ausloesen
     */
    public static boolean raeumungsreif(int ausfaelle, int schranke) {
        if (!brauchbareSchranke(schranke)) {
            throw new IllegalArgumentException("Eine Schranke von " + schranke
                    + " Ausfaellen ist keine - erlaubt ist 1 bis " + HOECHSTE_SCHRANKE
                    + ". Bei 0 waere JEDER Vertrag raeumungsreif, auch der heute"
                    + " abgeschlossene mit 0 Ausfaellen: Der erste Lauf raeumte die ganze"
                    + " Halle.");
        }
        return ausfaelle >= schranke;
    }

    /**
     * Ob diese Schranke eine ist - 1 bis {@link #HOECHSTE_SCHRANKE}.
     *
     * <p><b>Die 1 ist ERLAUBT</b>, und das ist eine Entscheidung: "Wer nicht zahlt, ist
     * sofort weg" ist eine zulaessige Serverregel, auch wenn sie hart ist. Der Spieler
     * bekommt dabei keine Warnung, sondern gleich die Raeumung - das ist die Folge, nicht
     * ein Fehler, und {@code Mietliste} schreibt sie dem Betreiber in den Bericht.
     *
     * <p><b>Die 0 ist es nicht</b>, und zwar aus einem anderen Grund als Haerte: Bei 0
     * ist {@code raeumungsreif(0, 0)} wahr, also waere JEDER Vertrag raeumungsreif - auch
     * der heute abgeschlossene, an dem kein einziger Ausfall steht. Der erste Lauf
     * raeumte die ganze Halle.
     */
    public static boolean brauchbareSchranke(int schranke) {
        return schranke >= 1 && schranke <= HOECHSTE_SCHRANKE;
    }
}
