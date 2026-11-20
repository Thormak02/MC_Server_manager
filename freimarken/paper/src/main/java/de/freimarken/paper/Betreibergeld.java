package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import java.util.UUID;

/**
 * Geld von Hand: {@code /freimarken geld <spieler> <betrag>} - die REGEL dahinter.
 *
 * <p><b>Warum es diesen Befehl gibt:</b> Bis zum 20.11.2026 gab es keinen Weg, einem
 * Spieler Mark zu geben oder abzuziehen. Fuer eine Probe im Spiel und fuer den Ernstfall
 * - ein Spieler verliert Geld durch einen Fehler des Programms - blieb nur die Datenbank
 * von Hand. Eine Zeile, die dort entsteht, steht in KEINEM Buch: Sie bricht die tragende
 * Zusage der Kasse ("fuer ein eroeffnetes Konto ist die Summe aller Buchungen sein
 * Stand"), und der naechste Bericht rechnet falsch.
 *
 * <p><b>Ohne Text, ohne Bukkit, ohne Protokoll</b>, und das ist Absicht: So laeuft jede
 * Lage in einer Probe ohne Server. Was der Betreiber liest, baut
 * {@link FreimarkenPlugin} aus dem {@link Ausgang} - mit einem Schalter ohne
 * {@code default}, damit ein neuer Ausgang nicht stumm in einen fremden Satz faellt.
 *
 * <p><b>Die Richtung kommt aus dem VORZEICHEN, der Grund aus der Richtung</b>: Ein Minus
 * zieht ab. Die Kasse selbst nimmt nur Betraege ohne Vorzeichen - "das Vorzeichen im Buch
 * kommt aus dem Grund" -, also macht dieser Weg beides auseinander und waehlt den
 * passenden der zwei Gruende.
 */
final class Betreibergeld {

    /**
     * Die groesste Zahl, die der Befehl annimmt - eine TECHNISCHE Schranke.
     *
     * <p>Keine Spielzahl und darum nicht in der {@code config.yml}: Sie legt nichts fest,
     * was ein Betreiber einstellen WILL, sondern faengt den Tippfehler mit drei Nullen zu
     * viel. Eine Milliarde Mark sind viertausend Freistaedte - was darueber liegt, ist
     * keine Korrektur mehr, sondern ein Versehen. Und ein Betrag in dieser Groesse kann
     * keine Summe im Buch zum Ueberlaufen bringen.
     */
    static final long HOECHSTENS = 1_000_000_000L;

    /**
     * Was aus dem Versuch geworden ist - je Lage eine, und keine Lage ohne eine.
     *
     * <p>Eine Aufzaehlung und kein Wahrheitswert: Zwischen "nicht gedeckt" und "Spieler
     * unbekannt" liegt fuer den Betreiber der Unterschied zwischen "noch einmal mit einer
     * kleineren Zahl" und "noch einmal mit dem richtigen Namen".
     */
    enum Ausgang {

        /** Gutgeschrieben - der Betrag war positiv. */
        GUTGESCHRIEBEN,

        /** Abgezogen - der Betrag war negativ und das Konto gedeckt. */
        ABGEZOGEN,

        /**
         * Diesen Namen kennt der Server nicht.
         *
         * <p>Der wichtigste der fuenf: {@code getOfflinePlayer(String)} erfindet fuer
         * einen unbekannten Namen eine frische Kennung, und {@code gutschreiben} legt
         * dafuer ein Konto samt Startguthaben an. Ein Tippfehler schickte das Geld sonst
         * auf ein Geisterkonto, und nichts waere aufgefallen.
         */
        UNBEKANNTER_SPIELER,

        /** Der Betrag war 0 - eine Buchung ueber nichts ist keine Korrektur. */
        NICHTS_ZU_BUCHEN,

        /** Abziehen ging nicht: So viel steht nicht auf dem Konto. */
        NICHT_GEDECKT
    }

    /**
     * Der Ausgang samt den Zahlen, die die Antwort nennt.
     *
     * @param betrag der Betrag OHNE Vorzeichen - die Richtung steht im Ausgang
     * @param stand  der Kontostand danach; 0 bei einem Ausgang ohne Buchung
     */
    record Ergebnis(Ausgang ausgang, String spieler, long betrag, long stand) {
    }

    /**
     * Woher die Kennung zu einem getippten Namen kommt.
     *
     * <p>Eine Naht und kein statischer Aufruf: {@code Bukkit.getOfflinePlayer} wirft ohne
     * Server, und dann liefe keine Probe. Im Spiel steht dahinter
     * {@link Spielername#kennungAuchAbwesend} - die Fassung, die auch einen lange
     * Abwesenden findet, denn genau der ist hier gemeint.
     */
    interface Namenssuche {

        /** Die Kennung, oder {@code null}, wenn der Server den Namen nicht kennt. */
        UUID kennungVon(String name);
    }

    private Betreibergeld() {
    }

    /**
     * Bucht - oder sagt, warum nicht.
     *
     * <p>Die Reihenfolge der Fragen ist die Zusage: ERST der Name, dann der Betrag. Wer
     * sich im Namen vertippt UND eine 0 schreibt, soll den Namen lesen - jener ist der
     * Fehler, der sonst auf ein Geisterkonto fuehrt.
     *
     * <p>Wirft weiter, wenn die Kasse schweigt: Der Aufrufer fasst es als Stoerung und
     * schreibt die Ursache ins Protokoll. Ein stiller Rueckfall waere hier besonders
     * teuer - der Betreiber haelt die Korrektur fuer erledigt und die naechste fuer eine
     * zweite.
     */
    static Ergebnis buche(Kasse kasse, Namenssuche suche, String name, long betrag) {
        if (kasse == null || suche == null) {
            throw new IllegalArgumentException("Ohne Kasse und Namenssuche gibt es keine"
                    + " Buchung");
        }
        UUID wer = suche.kennungVon(name);
        if (wer == null) {
            return new Ergebnis(Ausgang.UNBEKANNTER_SPIELER, name, 0L, 0L);
        }
        if (betrag == 0L) {
            return new Ergebnis(Ausgang.NICHTS_ZU_BUCHEN, name, 0L, kasse.guthaben(wer));
        }
        if (betrag > 0L) {
            kasse.gutschreiben(wer, betrag, Buchungsgrund.BETREIBER_GUTSCHRIFT);
            return new Ergebnis(Ausgang.GUTGESCHRIEBEN, name, betrag,
                    kasse.guthaben(wer));
        }
        long abzug = -betrag;
        if (!kasse.abbuchen(wer, abzug, Buchungsgrund.BETREIBER_ABZUG)) {
            // Die Rueckgabe NICHT wegwerfen: false heisst "nicht gedeckt, nichts
            // passiert, keine Zeile". Wer sie ignoriert, meldet dem Betreiber Erfolg
            // fuer eine Buchung, die es nicht gibt.
            return new Ergebnis(Ausgang.NICHT_GEDECKT, name, abzug, kasse.guthaben(wer));
        }
        return new Ergebnis(Ausgang.ABGEZOGEN, name, abzug, kasse.guthaben(wer));
    }
}
