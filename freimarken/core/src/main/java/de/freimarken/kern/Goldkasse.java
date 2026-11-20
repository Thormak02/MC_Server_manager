package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Die Kasse der GOLDMARK - dieselbe Mechanik, eine andere Waehrung (Plan 8.1).
 *
 * <p><b>Ein eigener Typ und nicht einfach ein zweites {@link Kasse}-Feld</b>, und das ist
 * der Hauptgrund fuer diese Klasse: Zwei Felder vom Typ {@code Kasse} sehen an jeder
 * Aufrufstelle gleich aus. Eines Tages buchte jemand Mark in die Goldmark oder umgekehrt,
 * und beide Buchungen saehen fuer sich richtig aus - der Fehler faende sich nur noch
 * dadurch, dass jemandem eine Summe nicht gefaellt. Mit einem eigenen Typ weist der
 * Uebersetzer die Verwechslung ab, bevor sie Geld bewegt.
 *
 * <p><b>Das Startguthaben ist die Falle dieses Bauschritts</b>, genau wie bei
 * {@link Stadtkasse} - darum steht hier derselbe Riegel. {@code Kasse.guthaben} gibt fuer
 * ein unbekanntes Konto das Startguthaben zurueck, und die Datenbankfassung legt JEDES
 * Konto mit diesem Betrag an, gerufen nicht nur von {@code eroeffne}, sondern von jeder
 * Buchung. Mit der Spielerkasse (ab Werk 50.000) haette die erste Gutschrift jedem
 * Spieler 50.000 Goldmark geschenkt - und das waere, zum Kurs des Plans (1 Goldmark = 800
 * Mark), vierzig Millionen Mark aus dem Nichts.
 *
 * <p><b>Die Goldmark hat ueberhaupt KEIN Startguthaben, und das ist keine Einstellung.</b>
 * Plan 8.1 nennt genau zwei Quellen - Echtgeld und Tausch aus Mark -, und eine dritte
 * waere geschenktes Geld in einer Waehrung, deren Rueckweg in Mark gedeckelt ist. Wer sie
 * doch verschenken will, verschenkt Mark; dafuer gibt es {@code kasse.startguthaben}.
 *
 * <p><b>Eigene Tabellen, nicht eine Waehrungsspalte</b> [entschieden 18.11.2026]: Zwei
 * Waehrungen in einer Tabelle addieren sich zu einem Stand, und die tragende Zusage des
 * Buchungsbuchs - "fuer ein eroeffnetes Konto ist die Summe aller Buchungen sein Stand" -
 * wuerde diesen Stand dann bestaetigen, obwohl er zwei Dinge zusammenwirft. Welche
 * Tabellen es sind, entscheidet die Datenschicht ({@code Kassentabellen}); dieser Kern
 * weiss davon nichts.
 *
 * <p><b>Der zweite Riegel kam mit Teil F: Der GRUND muss ein Goldmarkgrund sein.</b> Seit
 * es zwei Buecher gibt, ist der schlimmste denkbare Fehler eine Zeile im falschen - sie
 * steht da, sie ist richtig gerichtet, die Summe des Kontos stimmt, und sie erzaehlt von
 * einer Waehrung, die dieses Konto nicht fuehrt. Niemand sieht es, bis jemand nachrechnet.
 * Diese Klasse ist die EINZIGE Tuer zum Goldbuch, in beiden Betriebsarten - also steht der
 * Riegel hier und nicht in den zwei Kassenumsetzungen.
 *
 * <p>Fuer das MARKBUCH gibt es ihn ausdruecklich nicht, und das ist keine
 * Nachlaessigkeit: Dort buchen dreissig Stellen, und alle hatten bis heute nur
 * Markgruende. Die einzige Stelle, die BEIDE Buecher anfasst, ist {@link Wechselbuch} -
 * und die prueft ihre Markbuchungen selbst, mit demselben Satz. Zwei Riegel fuer dieselbe
 * Lage machten sich gegenseitig unpruefbar.
 *
 * <p><b>Was hier NICHT steht:</b> Kurs, Wechsel und Wechseldeckel. Die stehen seit Teil F
 * in {@link Wechselwerte} und {@link Wechselbuch}. Dieselbe Abgrenzung, die {@link Kasse}
 * im Klassenkopf fuer sich zieht - sie waeren hier eine halbe Umsetzung.
 */
public final class Goldkasse implements Kasse {

    private final Kasse kasse;

    /**
     * @param kasse eine Kasse mit Startguthaben 0 auf den Goldmarktabellen - siehe die
     *              Klassenbeschreibung
     * @throws IllegalArgumentException wenn die Kasse ein Startguthaben vergibt; das waere
     *                                  geschenkte Goldmark fuer jeden Spieler
     */
    public Goldkasse(Kasse kasse) {
        if (kasse == null) {
            throw new IllegalArgumentException("Die Goldkasse braucht eine Kasse");
        }
        // DER RIEGEL GEGEN GESCHENKTES GELD, wortgleich mit dem der Stadtkasse und aus
        // demselben Grund eine FRAGE und keine Zusage im Kommentar: Eine Kennung, die noch
        // nie ein Konto hatte, muss 0 ergeben. Ein Zufallswert aus 122 Bit ist dafuer
        // sicher genug - und er legt nichts an, denn guthaben() liest nur.
        //
        // Beim START und nicht bei der ersten Buchung: Ein Betreiber, der das Plugin
        // falsch verdrahtet bekommt, soll es in der Startmeldung lesen und nicht Wochen
        // spaeter an einem Spieler mit 50.000 Goldmark, die niemand gekauft hat.
        long aufUnbekanntem = kasse.guthaben(UUID.randomUUID());
        if (aufUnbekanntem != 0L) {
            throw new IllegalArgumentException("Die Goldkasse braucht eine Kasse ohne"
                    + " Startguthaben, diese gibt " + aufUnbekanntem + " Goldmark. Sonst"
                    + " bekaeme jeder Spieler diesen Betrag geschenkt, sobald die erste"
                    + " Goldmark bei ihm ankommt - und zwar lautlos.");
        }
        this.kasse = kasse;
    }

    @Override
    public long guthaben(UUID spieler) {
        return kasse.guthaben(spieler);
    }

    @Override
    public boolean eroeffne(UUID spieler) {
        return kasse.eroeffne(spieler);
    }

    @Override
    public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
        pruefeWaehrung(grund);
        return kasse.abbuchen(spieler, betrag, grund);
    }

    @Override
    public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
        pruefeWaehrung(grund);
        kasse.gutschreiben(spieler, betrag, grund);
    }

    @Override
    public List<Buchung> verlauf(UUID spieler, int hoechstens) {
        return kasse.verlauf(spieler, hoechstens);
    }

    @Override
    public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
        // Auch beim LESEN: Ein Markgrund im Goldbuch ergibt 0, und eine 0 ist hier die
        // gefaehrlichste Antwort, die es gibt - nach ihr sieht ein verbrauchter
        // Tagesdeckel aus wie ein unberuehrter.
        pruefeWaehrung(grund);
        return kasse.summeSeit(spieler, ab, grund);
    }

    /**
     * Der Riegel gegen eine Zeile im falschen Buch - siehe die Klassenbeschreibung.
     *
     * <p>Ein Wurf und keine stille Ablehnung: Ein Grund aus der falschen Waehrung ist kein
     * Zustand, auf den ein Spieler eine Antwort bekommen soll, sondern ein Fehler im
     * Programm. Er faellt beim ersten Aufruf auf, mit dem Namen des Grundes darin.
     */
    private static void pruefeWaehrung(Buchungsgrund grund) {
        if (grund == null) {
            throw new IllegalArgumentException("Ohne Grund gibt es keine Buchung");
        }
        if (!grund.gehoertIn(Buchungsgrund.Buch.GOLDMARK)) {
            throw new IllegalArgumentException("Der Grund " + grund + " gehoert in das "
                    + grund.buch() + "-Buch und nicht in das Goldbuch. Eine Zeile im"
                    + " falschen Buch sieht richtig aus und erzaehlt von einer Waehrung,"
                    + " die dieses Konto nicht fuehrt.");
        }
    }
}
