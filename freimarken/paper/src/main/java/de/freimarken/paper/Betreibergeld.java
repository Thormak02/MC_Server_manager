package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Goldkasse;
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
     * Die groesste Zahl in GOLDMARK - viel kleiner als {@link #HOECHSTENS}.
     *
     * <p>Dieselbe Begruendung, andere Zahl: 1 Goldmark ist 800 Mark (Plan 8.1), also
     * waere eine Milliarde Goldmark achthundert Milliarden Mark - keine Korrektur mehr,
     * sondern ein Versehen mit drei Nullen zu viel. Die Markschranke hier zu uebernehmen
     * waere der eigentliche Schaden gewesen, denn fuer diese Groesse ist nicht mehr
     * gerechnet, ob eine Summe im Buch ueberlaufsicher bleibt.
     */
    static final long HOECHSTENS_GOLD = 1_000_000L;

    /**
     * Welches Konto berichtigt wird.
     *
     * <p>Das BEFEHLSWORT und die SCHRANKE stehen hier beim Namen und nicht beim
     * Befehlsbaum: Es ist ein Paar, und wer das Wort an einer Stelle fuehrt und die Zahl
     * an einer anderen, laesst sie auseinanderlaufen. Nimmt Brigadier eine Zahl nicht an,
     * antwortet es englisch an eine Konsole, die ihre Antwort ohnehin nicht zurueckgibt.
     *
     * <p>Das ANZEIGEWORT ("Mark", "Goldmark") steht bewusst NICHT hier: Diese Klasse ist
     * ohne Text gebaut, damit jede Lage in einer Probe ohne Server laeuft. Was der
     * Betreiber liest, baut {@link FreimarkenPlugin}.
     */
    enum Waehrung {

        MARK("geld", HOECHSTENS, "/mark verlauf"),
        GOLDMARK("goldgeld", HOECHSTENS_GOLD, "/mark muenzhaus verlauf");

        private final String befehlswort;
        private final long hoechstens;
        private final String verlaufsbefehl;

        Waehrung(String befehlswort, long hoechstens, String verlaufsbefehl) {
            this.befehlswort = befehlswort;
            this.hoechstens = hoechstens;
            this.verlaufsbefehl = verlaufsbefehl;
        }

        /** Das Wort hinter {@code /freimarken}, das der Betreiber tippt. */
        String befehlswort() {
            return befehlswort;
        }

        /** Die groesste Zahl, die der Befehl in dieser Waehrung annimmt. */
        long hoechstens() {
            return hoechstens;
        }

        /**
         * Der Befehl, in dem die Buchung DANACH steht - der dritte Teil des Paars.
         *
         * <p>{@code /mark verlauf} zeigt ausschliesslich das MARKbuch
         * ({@code ParzellenBefehle.verlaufZeilen} gibt {@code Buch.MARK} herein), das
         * Goldbuch liest nur {@code /mark muenzhaus verlauf}. Die Hilfezeile nannte am
         * 23.11.2026 fuer beide Waehrungen {@code /mark verlauf}: Der Betreiber bucht 40
         * Goldmark zurueck, sieht im Markverlauf nichts davon und bucht noch einmal - die
         * Lage, die diese Klasse weiter unten selbst als die teuerste des Zweigs nennt,
         * nur zum achthundertfachen Wert.
         */
        String verlaufsbefehl() {
            return verlaufsbefehl;
        }
    }

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
        return buche(kasse, suche, name, betrag, Buchungsgrund.BETREIBER_GUTSCHRIFT,
                Buchungsgrund.BETREIBER_ABZUG);
    }

    /**
     * Dasselbe fuer die GOLDMARK - und der PARAMETERTYP ist der Riegel [23.11.2026].
     *
     * <p><b>{@link Goldkasse} und nicht {@link Kasse}:</b> Die Markkasse hat
     * ausdruecklich keinen Waehrungsriegel - die Goldkasse ist die einzige Tuer zum
     * Goldbuch, und dort sitzt er. Ein Goldgrund koennte also nur dadurch im Markbuch
     * landen, dass jemand hier die falsche Kasse hereingibt, und genau das weist der
     * Uebersetzer mit dem engeren Typ ab. Das ist dieselbe Begruendung, aus der es die
     * Klasse {@code Goldkasse} ueberhaupt gibt, und es ist die staerkste Form eines
     * Riegels: Eine Laufzeitprobe dafuer laesst sich gar nicht schreiben. Ein zweiter
     * Riegel zur Laufzeit waere der doppelte, der sich selbst unpruefbar macht.
     *
     * <p>Dies ist der erste Weg, auf dem Goldmark von aussen in das Buch kommt. Bis zum
     * 23.11.2026 war eine verlorene Goldmark nur von Hand in der Datenbank zu ersetzen -
     * genau der Weg, gegen den diese Klasse gebaut wurde.
     */
    static Ergebnis bucheGold(Goldkasse kasse, Namenssuche suche, String name,
                              long betrag) {
        return buche(kasse, suche, name, betrag, Buchungsgrund.BETREIBER_GOLDGUTSCHRIFT,
                Buchungsgrund.BETREIBER_GOLDABZUG);
    }

    /**
     * Die Regel, einmal - die zwei Tueren darueber geben nur die Gruende herein.
     *
     * <p>Die Gruende sind Parameter und kein Schalter ueber die Waehrung: So kann es
     * keinen Zweig geben, der einen Grund vergisst, und die Reihenfolge der Fragen (Name,
     * Betrag, Deckung) steht fuer beide Waehrungen an derselben Stelle.
     */
    private static Ergebnis buche(Kasse kasse, Namenssuche suche, String name,
                                  long betrag, Buchungsgrund gutschrift,
                                  Buchungsgrund abzugsgrund) {
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
            kasse.gutschreiben(wer, betrag, gutschrift);
            return new Ergebnis(Ausgang.GUTGESCHRIEBEN, name, betrag,
                    kasse.guthaben(wer));
        }
        long abzug = -betrag;
        if (!kasse.abbuchen(wer, abzug, abzugsgrund)) {
            // Die Rueckgabe NICHT wegwerfen: false heisst "nicht gedeckt, nichts
            // passiert, keine Zeile". Wer sie ignoriert, meldet dem Betreiber Erfolg
            // fuer eine Buchung, die es nicht gibt.
            return new Ergebnis(Ausgang.NICHT_GEDECKT, name, abzug, kasse.guthaben(wer));
        }
        return new Ergebnis(Ausgang.ABGEZOGEN, name, abzug, kasse.guthaben(wer));
    }
}
