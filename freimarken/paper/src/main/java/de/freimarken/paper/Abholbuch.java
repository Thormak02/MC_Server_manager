package de.freimarken.paper;

import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Abholstueck;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;

/**
 * Das Abholfach - die eine Stelle, die Stapel herausgibt.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Mietbuch} und {@link Mietlauf}. Das
 * Spiel kommt nur ueber {@link Zusteller} herein.
 *
 * <p><b>Ein BEFEHL und kein Fenster</b>, und das ist die tragende Entscheidung dieses
 * Teils. Ein Truhenfenster waere das Naheliegende: Der Spieler sieht seine Stapel und
 * nimmt heraus, was er will. Es hat aber einen Zustand - "Fenster offen" -, und jeder
 * Zustand zwischen Spieler und Datenbank ist eine Stelle, an der ein Verbindungsabbruch
 * Gegenstaende verdoppelt: Wer das Fenster schliesst, waehrend der Schreibzug laeuft, hat
 * die Ware im Inventar UND in der Datenbank. In einer Wirtschaft mit Bundesmarkt ist ein
 * verdoppelter Diamantblock sofort Geld. Der Befehl hat diesen Zustand nicht.
 *
 * <p><b>Erst loeschen, dann geben - und zwar Stapel fuer Stapel.</b> Das ist die
 * Reihenfolge, die keine Gegenstaende erzeugen kann, dieselbe Richtung wie beim Kuendigen
 * in {@link Mietbuch}. Einzeln und nicht in einem Zug, weil nur das einzelne Loeschen
 * sagt, WELCHER Stapel wirklich weg ist: {@link Abholablage#nimm} nennt eine Zahl, und aus
 * "drei von fuenf" ist nicht zu lesen, welche drei. Herausgegeben wird nur, was diese
 * Antwort als geloescht bestaetigt hat.
 *
 * <p><b>Was sich nicht zurueckverwandeln laesst, bleibt LIEGEN.</b> Darum fragt der
 * Zusteller zweimal: {@link Zusteller#lesbar} vor dem Loeschen und
 * {@link Zusteller#gib} danach. Ein Stapel, den ein Minecraft-Update unlesbar gemacht
 * hat, wird uebergangen und protokolliert - mit seiner Beschriftung, damit ein Betreiber
 * ihn von Hand ersetzen kann. Wuerde erst geloescht und dann verwandelt, waere so ein
 * Stapel weg, und das Versprechen des Fachs ("wird NIE geloescht") gebrochen.
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Jeder Griff antwortet mit einem Bescheid,
 * auch wenn die Datenbank schweigt.
 */
final class Abholbuch {

    /** Was aus einem Griff ins Fach geworden ist. */
    enum Bescheid {
        /** Mindestens ein Stapel ist herausgegangen. */
        GEHOLT,
        /** Das Fach ist leer - der Normalfall. */
        FACH_LEER,
        /** Kein Platz im Inventar; es liegt noch alles im Fach. */
        KEIN_PLATZ,
        /** Nichts ging heraus, und das lag nicht am Platz. Steht im Protokoll. */
        GESTOERT
    }

    /**
     * Was ein Griff bewirkt hat.
     *
     * @param geholt   wie viele Stapel im Inventar gelandet sind
     * @param bleibt   wie viele noch im Fach liegen - FRISCH gezaehlt, nicht gerechnet
     * @param unlesbar wie viele uebergangen wurden, weil sie sich nicht zurueckverwandeln
     *                 liessen. Die liegen noch da und stehen im Protokoll
     */
    record Griff(Bescheid bescheid, int geholt, int bleibt, int unlesbar) {
    }

    /**
     * Die Bruecke zum Inventar - die eine Naht zum Spiel.
     *
     * <p>Zwei Methoden, weil zwischen ihnen das Loeschen liegt. Eine Methode, die
     * verwandelt UND ablegt, machte die Reihenfolge aus dem Klassenkopf unmoeglich: Dann
     * waere entweder ein unlesbarer Stapel schon geloescht, oder ein gegebener noch nicht.
     */
    interface Zusteller {

        /**
         * Ob sich dieser Text in einen Gegenstand zurueckverwandeln laesst - OHNE etwas zu
         * geben und ohne zu werfen.
         */
        boolean lesbar(String inhalt);

        /**
         * Legt den Gegenstand ins Inventar.
         *
         * @return {@code false}, wenn kein Platz war. Dann wird der Stapel
         *         zurueckgelegt - er ist zu diesem Zeitpunkt schon aus dem Fach gewesen
         */
        boolean gib(String inhalt);
    }

    private final Abholablage fach;
    private final Clock uhr;
    private final Logger protokoll;

    Abholbuch(Abholablage fach, Clock uhr, Logger protokoll) {
        this.fach = fach;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Wie viele Stapel fuer diesen Spieler im Fach liegen.
     *
     * <p>Gibt bei einer Stoerung 0 zurueck und klagt. Gerufen wird das beim Beitritt
     * jedes Spielers, und eine Ausnahme dort waere ein abgebrochener Beitritt wegen eines
     * Hinweises.
     */
    int stand(UUID wer) {
        if (wer == null) {
            return 0;
        }
        try {
            return fach.anzahlFuer(wer);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Abholfach von " + wer + " nicht zaehlbar",
                    fehler);
            return 0;
        }
    }

    /**
     * Holt, was ins Inventar passt.
     *
     * @param zusteller das Inventar DIESES Spielers. Ein Parameter und kein Feld: Der
     *                  Zusteller haengt an dem Spieler, der gerade tippt, und das
     *                  Abholbuch entsteht beim Start des Servers
     * @param plaetze wie viele Stapel das Inventar noch aufnehmen kann. Konservativ
     *                gezaehlt - ein Stapel geht nur in einen GANZ freien Platz, obwohl er
     *                sich manchmal auf einen halb gefuellten legen liesse. Die
     *                Unterschaetzung ist die sichere Richtung: Zu viel geholt heisst
     *                verloren, zu wenig geholt heisst noch einmal tippen
     */
    Griff hole(UUID wer, Zusteller zusteller, int plaetze) {
        if (wer == null) {
            return new Griff(Bescheid.FACH_LEER, 0, 0, 0);
        }
        List<Abholstueck> alle;
        try {
            alle = fach.fuerSpieler(wer);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Abholfach von " + wer + " nicht lesbar",
                    fehler);
            return new Griff(Bescheid.GESTOERT, 0, 0, 0);
        }
        if (alle.isEmpty()) {
            return new Griff(Bescheid.FACH_LEER, 0, 0, 0);
        }
        // Die Platzfrage NACH der Leerfrage: "Dein Fach ist leer" ist die bessere
        // Auskunft als "kein Platz", wenn beides zutrifft.
        if (plaetze < 1) {
            return new Griff(Bescheid.KEIN_PLATZ, 0, alle.size(), 0);
        }
        int geholt = 0;
        int unlesbar = 0;
        for (Abholstueck stueck : alle) {
            if (geholt >= plaetze) {
                break;
            }
            if (!zusteller.lesbar(stueck.inhalt())) {
                unlesbar++;
                // Mit der BESCHRIFTUNG, und das ist der ganze Grund, warum sie neben dem
                // Inhalt in der Zeile steht: Ein Betreiber, der "64x Eichenholz, nicht
                // lesbar" liest, kann es von Hand ersetzen. "Ein Stapel, nicht lesbar"
                // kann er nicht.
                protokoll.log(Level.WARNING, "Abholstueck " + stueck.id() + " von " + wer
                        + " (\"" + stueck.beschriftung() + "\", aus " + stueck.herkunft()
                        + ") liess sich nicht in einen Gegenstand zurueckverwandeln. Es"
                        + " bleibt liegen und ist von Hand zu ersetzen.");
                continue;
            }
            try {
                if (fach.nimm(wer, List.of(stueck.id())) == 0) {
                    // Schon weg - ein zweiter Befehl war schneller. Kein Fehler: Das
                    // vierte Versprechen der Ablage sagt ausdruecklich, dass eines von
                    // zwei Fenstern nichts bekommt.
                    continue;
                }
            } catch (RuntimeException fehler) {
                protokoll.log(Level.WARNING, "Abholstueck " + stueck.id() + " von " + wer
                        + " liess sich nicht nehmen - es bleibt liegen.", fehler);
                break;
            }
            if (!zusteller.gib(stueck.inhalt())) {
                // Jetzt ist es aus dem Fach und nicht im Inventar - der eine Augenblick,
                // in dem ein Gegenstand nirgends ist. Zurueck damit, und zwar sofort.
                zurueck(stueck);
                break;
            }
            geholt++;
        }
        return abschluss(wer, geholt, unlesbar);
    }

    /**
     * Legt ein Stueck zurueck, das nicht zugestellt werden konnte.
     *
     * <p>Mit einer NEUEN Nummer und am Ende des Fachs - die alte Zeile ist geloescht, und
     * eine Nummer wird nie wiederverwendet. Das ist hingenommen: Die Reihenfolge im Fach
     * ist eine Bequemlichkeit, der Gegenstand ist es nicht.
     *
     * <p>Scheitert auch das, ist der Stapel verloren. Dann steht er als {@code SEVERE} im
     * Protokoll, mit Beschriftung und Herkunft - das ist der einzige Weg, auf dem dieses
     * Teil einen Gegenstand verlieren kann, und er ist genannt statt verschwiegen.
     */
    private void zurueck(Abholstueck stueck) {
        try {
            fach.lege(stueck.empfaenger(), stueck.herkunft(), uhr.instant(),
                    List.of(new Abholgut(stueck.beschriftung(), stueck.inhalt())));
            protokoll.info("Abholstueck \"" + stueck.beschriftung() + "\" von "
                    + stueck.empfaenger() + " passte nicht ins Inventar und liegt wieder"
                    + " im Fach.");
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Abholstueck \"" + stueck.beschriftung()
                    + "\" (aus " + stueck.herkunft() + ") von " + stueck.empfaenger()
                    + " ist aus dem Fach genommen, passte nicht ins Inventar und liess"
                    + " sich nicht zuruecklegen. Der Stapel ist VERLOREN und von Hand zu"
                    + " ersetzen.", fehler);
        }
    }

    /** Was uebrig ist - frisch gezaehlt und nicht aus der Anfangszahl gerechnet. */
    private Griff abschluss(UUID wer, int geholt, int unlesbar) {
        // FRISCH gezaehlt, weil zwischen Anfang und Ende dieses Griffs ein zweiter Befehl
        // gelaufen sein kann und weil ein zurueckgelegter Stapel die Rechnung
        // "Anfang minus geholt" falsch macht.
        int bleibt = stand(wer);
        if (geholt > 0) {
            return new Griff(Bescheid.GEHOLT, geholt, bleibt, unlesbar);
        }
        if (unlesbar > 0) {
            return new Griff(Bescheid.GESTOERT, 0, bleibt, unlesbar);
        }
        // Nichts geholt, nichts unlesbar, und am Anfang lag etwas da: Ein zweiter Befehl
        // war schneller, oder das Nehmen ist gescheitert. Beides ist aus Sicht des
        // Spielers dasselbe - sein Fach ist (jetzt) leer oder er versucht es erneut.
        return new Griff(bleibt > 0 ? Bescheid.GESTOERT : Bescheid.FACH_LEER, 0, bleibt,
                0);
    }
}
