package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Verdienstregel;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Was jeder Spieler verdient hat und noch nicht gebucht ist - der Sammler.
 *
 * <p>Die ganze Rechnung liegt hier und kennt kein Bukkit. Derselbe Schnitt wie bei
 * {@link Spielzeitbuch}, und aus demselben Grund: Was ohne Server pruefbar ist, soll es
 * auch sein. Das Spiel liefert nur herein, WER was getan hat.
 *
 * <p>Nicht im Kern, obwohl nichts von Bukkit darin steht: Der Kern haelt keinen Zustand
 * einer laufenden Sitzung. Pfennigrest, Minderungszaehler und Tagessumme sind genau das.
 *
 * <p><b>Im Blockereignis wird NICHT geschrieben.</b> Das ist die tragende Entscheidung
 * dieses Bauschrittes, und sie ist gerechnet: Eine Buchung kostet drei SQL-Anweisungen in
 * einer Transaktion ueber die EINE Verbindung, die dieses Projekt im Hauptfaden haelt. Je
 * abgebautem Block waeren das bei fuenfzig Spielern eine Viertelmillion Zeilen in der
 * Stunde und ein Drittel bis das Anderthalbfache des Hauptfadens nur fuer das Buchen -
 * also derselbe Weg, den die Blockmarke schon verworfen hat. Im Ereignis wird darum nur
 * gerechnet; gebucht wird im Takt.
 *
 * <p><b>Gerechnet wird in PFENNIGEN, gebucht in ganzen Mark.</b> Der Rest bleibt stehen
 * und wartet auf den naechsten Takt. Ohne ihn muesste gewoehnlicher Stein 0 zahlen, und
 * die Spanne von 2.000 bis 5.000 Mark je Spielstunde haenge an der Erzdichte statt an der
 * Arbeit.
 *
 * <p><b>Der abnehmende Ertrag zaehlt je Spieler und BLOCKART, in einem Fenster von einer
 * Stunde.</b> Je Blockart und nicht je Ort: Eine Mobfarm liefert immer dieselbe Art an
 * wechselnden Stellen, und genau sie ist der benannte Grund fuer die Regel. Das Fenster
 * springt und gleitet nicht - nach einer Stunde beginnt es neu. Der Preis ist eine Kante:
 * Wer eine Stunde durchgraebt, zahlt danach wieder voll. Der Gewinn ist, dass je Spieler
 * und Art eine Zahl und ein Zeitpunkt genuegen; ein gleitendes Fenster brauchte die
 * Zeitpunkte jedes einzelnen Blocks.
 *
 * <p><b>Der Tagesdeckel kommt aus dem BUCHUNGSBUCH</b> und nicht aus einer eigenen
 * Tabelle. Die Summe steht dort schon; eine zweite Tabelle daneben wiche nach dem ersten
 * Absturz von der ersten ab, und dann deckelte der Server einen Spieler, dessen Konto
 * etwas anderes sagt. Gefragt wird einmal je Spieler und Tag - im TAKT und nie im
 * Blockereignis.
 *
 * <p><b>Was am Deckel anfaellt, verfaellt.</b> Es wandert nicht in den naechsten Tag:
 * Sonst waere der Deckel nur eine Verzoegerung, und wer einen Tag durchfarmt, bekaeme am
 * naechsten Morgen alles. Der Spieler erfaehrt es - der Handbalken sagt es ihm, sonst
 * arbeitete er weiter fuer nichts.
 *
 * <p><b>Nach einem Fehlschlag wird NICHT wiederholt.</b> Anders als bei der Spielzeit ist
 * das hier zwingend: Ein zweiter Versuch mit derselben Summe buchte doppelt, und die
 * Zusage "Summe aller Buchungen gleich Kontostand" waere gebrochen - mit Geld, das niemand
 * erarbeitet hat. Der verlorene Betrag steht im Protokoll, und der Rest wird verworfen.
 */
final class Verdienstbuch {

    /** Wie lange ein Minderungsfenster gilt - eine Stunde, wie im Plan. */
    static final Duration FENSTER = Duration.ofHours(1L);

    /** So selten wird ein Ausfall der Kasse ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    /** Hoechstens so oft wird ein Handbalken gesendet - je Spieler. */
    static final Duration ANZEIGEPAUSE = Duration.ofSeconds(1L);

    private final Kasse kasse;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;
    private final Logger protokoll;

    /** Je Spieler sein Stand - angelegt beim ersten Verdienst, weg beim Abgang. */
    private final Map<UUID, Stand> staende = new HashMap<>();

    private long letzteKlage;

    Verdienstbuch(Kasse kasse, Wirtschaftsstand wirtschaft, Clock uhr,
                  Logger protokoll) {
        if (kasse == null || wirtschaft == null || uhr == null || protokoll == null) {
            throw new IllegalArgumentException("Kasse, Wirtschaftsstand, Uhr und"
                    + " Protokoll sind Pflicht - dieser Sammler holt sich nichts selbst");
        }
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Rechnet eine Taetigkeit ab - ohne zu buchen.
     *
     * <p>Das ist der Weg aus dem Blockereignis, und er kostet keine Netzrunde: zwei
     * Nachschlaege in einer Tabelle, eine Multiplikation, eine Addition.
     *
     * @return wie viele PFENNIGE das eingebracht hat; 0 heisst, dass diese Taetigkeit an
     *         diesem Material diesem Spieler nichts bringt
     */
    long verdiene(UUID wer, Set<String> berufe, Taetigkeit taetigkeit, String material) {
        if (wer == null || berufe == null || taetigkeit == null || material == null) {
            throw new IllegalArgumentException("Ohne Spieler, Berufe, Taetigkeit und"
                    + " Material gibt es nichts zu verdienen");
        }
        Verdienstwerte werte = wirtschaft.werte().verdienst();
        // ZUERST fragen, ob es ueberhaupt einen Satz gibt - und erst dann einen Zaehler
        // anlegen. Sonst bekaeme jede Blockart, die ein Spieler anfasst, einen Eintrag im
        // Arbeitsspeicher: Erde, Gras, Laub, und keiner davon zahlt je etwas.
        if (werte.loehne().hoechsterLohn(berufe, taetigkeit, material) <= 0L) {
            return 0L;
        }
        Instant jetzt = uhr.instant();
        Stand stand = staende.computeIfAbsent(wer, spieler -> new Stand());

        // Die HARTE Obergrenze zuerst: Sie zaehlt ueber alle Namen einer Taetigkeit
        // zusammen und ist die einzige Bremse, die eine Farm wirklich anhaelt. Die Stufen
        // darunter daempfen nur - ihre Pauschale vervielfacht sich sonst mit jeder
        // weiteren Art, die eine Farm ausspuckt.
        Zaehler jeTaetigkeit = imFenster(stand.taetigkeiten, taetigkeit.schluessel(),
                jetzt);
        if (jeTaetigkeit.anzahl >= werte.stundenmenge(taetigkeit)) {
            // Nicht weiterzaehlen - aber ohne Versprechen: Die naechste Stunde beginnt
            // ohnehin bei 0, dafuer sorgt imFenster. Dass der Zaehler hier stehenbleibt,
            // ist darum von aussen NICHT zu beobachten, und keine Probe behauptet es.
            // Ein frueherer Kommentar an dieser Stelle versprach mehr; eine Einspritzung
            // hat gezeigt, dass das Versprechen von keiner Probe gedeckt war.
            stand.anDerGrenze = taetigkeit;
            return 0L;
        }

        // Die Stufen je TAETIGKEIT UND Name. Nicht je Name allein: Sonst teilten
        // "fischen cod" und "abbauen cod" ein Fenster, und bei "ernten wheat" gegen
        // "abbauen wheat" fiele derselbe Namenszufall zugunsten des Spielers aus.
        Zaehler jeName = imFenster(stand.arten,
                taetigkeit.schluessel() + "/" + material, jetzt);
        long pfennige = Verdienstregel.pfennige(werte, berufe, taetigkeit, material,
                jeName.anzahl);
        jeName.anzahl++;
        jeTaetigkeit.anzahl++;
        stand.anDerGrenze = null;
        stand.rest += pfennige;
        stand.seitAnzeige += pfennige;
        return pfennige;
    }

    /**
     * Der Zaehler zu diesem Schluessel, mit frischem Fenster, wenn die Stunde um ist.
     *
     * <p>Die eine Stelle fuer die gleitende Stunde - beide Zaehler gehen durch sie. Zwei
     * Abschriften derselben Fensterrechnung liefen auseinander, und zwar lautlos: Die
     * Minderung griffe dann zu einem anderen Zeitpunkt als die Obergrenze.
     */
    private static Zaehler imFenster(Map<String, Zaehler> wo, String schluessel,
                                     Instant jetzt) {
        Zaehler zaehler = wo.computeIfAbsent(schluessel, egal -> new Zaehler(jetzt));
        if (Duration.between(zaehler.fensterBeginn, jetzt).compareTo(FENSTER) >= 0) {
            // Ein neues Fenster. Auch bei einer zurueckgestellten Uhr: Eine negative
            // Dauer ist kleiner als eine Stunde, also bleibt das Fenster stehen - das ist
            // die sichere Richtung, denn ein Neubeginn waere wieder der volle Satz.
            zaehler.anzahl = 0;
            zaehler.fensterBeginn = jetzt;
        }
        return zaehler;
    }

    /**
     * Was seit dem letzten Handbalken verdient wurde - oder {@code -1}, wenn die Pause
     * noch laeuft.
     *
     * <p>Die Drossel steht HIER und nicht in der Wache: Sie ist Zustand je Spieler, und
     * der liegt in diesem Sammler. {@code -1} und nicht 0, weil 0 eine Antwort ist -
     * "nichts verdient" soll nichts senden, und "noch nicht wieder" auch, aber eine Probe
     * muss die zwei unterscheiden koennen.
     */
    long fuerDenHandbalken(UUID wer) {
        Stand stand = staende.get(wer);
        if (stand == null || stand.seitAnzeige <= 0L) {
            return 0L;
        }
        Instant jetzt = uhr.instant();
        if (Duration.between(stand.letzteAnzeige, jetzt).compareTo(ANZEIGEPAUSE) < 0) {
            return -1L;
        }
        long summe = stand.seitAnzeige;
        stand.seitAnzeige = 0L;
        stand.letzteAnzeige = jetzt;
        return summe;
    }

    /** Ob dieser Spieler seinen Tagesdeckel erreicht hat - fuer den Handbalken. */
    boolean amDeckel(UUID wer) {
        Stand stand = staende.get(wer);
        return stand != null && stand.amDeckel;
    }

    /**
     * Welche Taetigkeit zuletzt an ihrer Stundenmenge stand - oder {@code null}.
     *
     * <p>Fuer den Handbalken: Ein Spieler, der weiterhaut und nichts mehr bekommt, soll
     * den Grund lesen und nicht den Fehler bei sich suchen.
     */
    Taetigkeit anDerGrenze(UUID wer) {
        Stand stand = staende.get(wer);
        return stand == null ? null : stand.anDerGrenze;
    }

    /**
     * Bucht, was gesammelt ist - der Weg aus dem Zeitgeber.
     *
     * <p>HOECHSTENS so viele Spieler je Aufruf, und das ist gemessen begruendet: Fuenfzig
     * Buchungen in EINEM Tick sind fuenfzig Transaktionen mit je drei Anweisungen - das
     * ist genau die Massenabmeldung, gegen die {@link Spielzeitbuch} seine Sperre haelt.
     * Verteilt auf mehrere Takte faellt es nicht auf, und verloren geht nichts: Was heute
     * nicht gebucht wird, steht im naechsten Takt noch da.
     *
     * @param hoechstens wie viele Spieler dieser Aufruf hoechstens bucht
     * @return wie viele Mark insgesamt gebucht wurden
     */
    long rechneAb(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Es muss mindestens ein Spieler sein: "
                    + hoechstens);
        }
        long gebucht = 0L;
        int getan = 0;
        for (Map.Entry<UUID, Stand> eintrag : staende.entrySet()) {
            if (getan >= hoechstens) {
                break;
            }
            if (eintrag.getValue().rest < 100L) {
                continue;
            }
            gebucht += bucheEinen(eintrag.getKey(), eintrag.getValue());
            getan++;
        }
        return gebucht;
    }

    /**
     * Ein Abgang: sofort abrechnen und vergessen.
     *
     * <p>Sofort und nicht im naechsten Takt: Der Spieler ist weg, aber seine Arbeit ist
     * geschehen. Und vergessen, weil der Minderungszaehler die Sitzung nicht ueberlebt -
     * eine Stunde voller Saetze nach dem Wiederkommen ist der Preis dafuer, dass dieser
     * Zustand nirgends geschrieben wird.
     */
    void abgang(UUID wer) {
        Stand stand = staende.remove(wer);
        if (stand != null && stand.rest >= 100L) {
            bucheEinen(wer, stand);
        }
    }

    /** Alles abrechnen - beim Abschalten des Plugins. */
    long rechneAllesAb() {
        long gebucht = 0L;
        for (Map.Entry<UUID, Stand> eintrag : staende.entrySet()) {
            if (eintrag.getValue().rest >= 100L) {
                gebucht += bucheEinen(eintrag.getKey(), eintrag.getValue());
            }
        }
        staende.clear();
        return gebucht;
    }

    /** Wie viele Spieler gerade etwas Ungebuchtes haben - fuer die Proben. */
    int wartende() {
        int zahl = 0;
        for (Stand stand : staende.values()) {
            if (stand.rest >= 100L) {
                zahl++;
            }
        }
        return zahl;
    }

    /** Der ungebuchte Pfennigrest eines Spielers - fuer die Proben. */
    long rest(UUID wer) {
        Stand stand = staende.get(wer);
        return stand == null ? 0L : stand.rest;
    }

    /**
     * Die eine Buchung - mit Deckel, Rest und Fehlerbehandlung.
     *
     * <p>Die Reihenfolge ist die Sache selbst: Tagessumme sicherstellen, kuerzen, buchen,
     * und ERST DANACH den Rest senken. Ein Absturz zwischen Buchen und Senken buchte
     * beim naechsten Takt noch einmal - darum steht das Senken unmittelbar hinter dem
     * Buchen und ohne etwas dazwischen.
     */
    private long bucheEinen(UUID wer, Stand stand) {
        Verdienstwerte werte = wirtschaft.werte().verdienst();
        LocalDate heute = Tagesgrenze.tagVon(uhr.instant(),
                wirtschaft.werte().zeit().zone());
        long mark = Verdienstregel.mark(stand.rest);
        try {
            if (stand.tag == null || !stand.tag.equals(heute) || stand.heuteGebucht < 0L) {
                // Einmal je Spieler und Tag, und zwar HIER im Takt: Im Blockereignis
                // waere das eine Abfrage je Block.
                stand.heuteGebucht = kasse.summeSeit(wer,
                        Tagesgrenze.tagesbeginn(heute,
                                wirtschaft.werte().zeit().zone()),
                        Buchungsgrund.BERUF_VERDIENST);
                stand.tag = heute;
            }
            long erlaubt = Verdienstregel.unterDemDeckel(werte, stand.heuteGebucht, mark);
            stand.amDeckel = erlaubt < mark;
            if (erlaubt <= 0L) {
                // Am Deckel VERFAELLT der Rest. Er wandert nicht in den naechsten Tag -
                // sonst waere der Deckel nur eine Verzoegerung.
                stand.rest = 0L;
                return 0L;
            }
            kasse.gutschreiben(wer, erlaubt, Buchungsgrund.BERUF_VERDIENST);
            stand.heuteGebucht += erlaubt;
            // Der Rest der nicht gebuchten Mark verfaellt mit: Gebucht wurde, was der
            // Deckel zuliess, und der Rest darunter wartet auf den naechsten Takt.
            stand.rest = erlaubt == mark
                    ? Verdienstregel.rest(stand.rest)
                    : 0L;
            return erlaubt;
        } catch (RuntimeException fehler) {
            // NICHT wiederholen: Ein zweiter Versuch mit derselben Summe buchte doppelt.
            // Der verlorene Betrag steht im Protokoll, und der Rest wird verworfen - das
            // ist die einzige Richtung, in der die Summenzusage haelt.
            klageUeberKasse(wer, mark, fehler);
            stand.rest = 0L;
            stand.heuteGebucht = -1L;
            return 0L;
        }
    }

    /** Meldet einen Ausfall der Kasse - hoechstens einmal je Pause. */
    private void klageUeberKasse(UUID wer, long mark, RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.SEVERE, "Verdienst nicht buchbar fuer " + wer + " - " + mark
                + " Mark sind verloren. Es wird NICHT wiederholt: Ein zweiter Versuch"
                + " buchte doppelt.", fehler);
    }

    /** Alles, was ein Spieler an laufendem Zustand hat. */
    private static final class Stand {
        private long rest;
        private long seitAnzeige;
        private Instant letzteAnzeige = Instant.EPOCH;
        private LocalDate tag;
        private long heuteGebucht = -1L;
        private boolean amDeckel;

        /**
         * Welche Taetigkeit zuletzt an ihrer Stundenmenge stand - oder {@code null}.
         *
         * <p>Gemerkt und nicht verworfen: Ein Spieler, der weiterarbeitet, ohne etwas zu
         * bekommen, muss es erfahren. Dieselbe Begruendung wie beim Tagesdeckel, nur
         * eine Ebene darunter.
         */
        private Taetigkeit anDerGrenze;

        /** Je "taetigkeit/name" die Stufenminderung. */
        private final Map<String, Zaehler> arten = new HashMap<>();

        /** Je Taetigkeit die harte Stundenmenge. */
        private final Map<String, Zaehler> taetigkeiten = new HashMap<>();
    }

    /** Wie viele Bloecke einer Art in diesem Fenster schon gezaehlt wurden. */
    private static final class Zaehler {
        private int anzahl;
        private Instant fensterBeginn;

        private Zaehler(Instant beginn) {
            this.fensterBeginn = beginn;
        }
    }

    /**
     * Nur fuer die Proben: welche Fenster ein Spieler gerade offen hat.
     *
     * <p>Die Schluessel sind "taetigkeit/name" - siehe {@link #verdiene}.
     */
    List<String> gezaehlteArten(UUID wer) {
        Stand stand = staende.get(wer);
        return stand == null ? List.of() : List.copyOf(new ArrayList<>(
                stand.arten.keySet()));
    }
}
