package de.freimarken.kern;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wer gerade einsieht (Plan 12c.3, Schritt 16a Teil D).
 *
 * <p><b>Ein Zustand im Arbeitsspeicher und ausdruecklich KEINE Tabelle.</b> Das ist die
 * tragende Entscheidung dieses Teils: Einsicht ist kein Besitz und kein Protokoll, sie
 * ist eine Haltung fuer die naechsten Minuten. Ueberlebte sie einen Neustart, waere sie
 * nach einem vergessenen {@code /einsehen} fuer immer an - und niemand sucht in einer
 * Tabelle nach einem Zustand, von dem er nicht weiss, dass es ihn gibt. Ein Neustart
 * raeumt sie auf, und das ist die richtige Richtung.
 *
 * <p><b>Die FRIST steht hier und nicht beim Aufrufer</b>, und zwar als Zeitpunkt des
 * Ablaufs und nicht als Zaehler: Ein Zaehler braeuchte einen Takt, der ihn
 * herunterzaehlt, und ein Takt, der ausfaellt, liesse die Einsicht offen. Ein Zeitpunkt
 * laeuft von selbst ab - auch wenn nichts mehr laeuft.
 *
 * <p><b>{@code synchronized} auf jeder Methode</b>, wie bei {@link SpeicherStrafen}: Die
 * Zuhoerer des Plugins laufen im Hauptfaden, die Chatwache aber im Netzfaden - und
 * {@code Schutzwache.darfHandeln} fragt diesen Stand bei jedem Klick auf einen
 * Behaelter.
 */
public final class Einsichtsstand {

    /** Wer einsieht, und bis wann. */
    private final Map<UUID, Instant> bis = new HashMap<>();

    /**
     * Die Werte - als Lieferant und nicht als Wert.
     *
     * <p>Die {@code config.yml} laesst sich im Betrieb neu laden, und ein hier
     * festgehaltenes Exemplar waere nach dem ersten Neuladen die alte Datei. Dieselbe
     * Bauart wie bei {@link Strafbuch} und {@link Meldebuch}.
     *
     * <p>Die neue Frist gilt fuer die naechste Einsicht, nicht fuer die laufende: Wer
     * schon einsieht, hat seinen Ablaufzeitpunkt - ihn nachtraeglich zu verschieben
     * waere eine Einsicht, die laenger gilt, weil jemand eine Datei geaendert hat.
     */
    private final Supplier<Einsichtswerte> werte;

    public Einsichtsstand(Supplier<Einsichtswerte> werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Der Einsichtsstand braucht die Werte -"
                    + " ohne die Frist waere die Einsicht fuer immer an");
        }
        this.werte = werte;
    }

    /** Was gilt. */
    public Einsichtswerte werte() {
        Einsichtswerte gelesen = werte.get();
        return gelesen == null ? Einsichtswerte.VORGABE : gelesen;
    }

    /**
     * Schaltet die Einsicht ein und gibt den Zeitpunkt zurueck, bis zu dem sie gilt.
     *
     * <p>Ein ERNEUTES Einschalten verlaengert - das ist der Weg fuer eine Untersuchung,
     * die laenger dauert: ein Tastendruck und eine Zeile im Protokoll. Die Antwort
     * nennt den neuen Zeitpunkt, damit der Moderator nicht raten muss, wie viel er
     * gewonnen hat.
     */
    public synchronized Instant schalteEin(UUID wer, Instant jetzt) {
        pruefe(wer, jetzt);
        Instant ende = jetzt.plusSeconds(werte().dauerSekunden());
        bis.put(wer, ende);
        return ende;
    }

    /**
     * Schaltet die Einsicht aus.
     *
     * @return {@code true}, wenn sie vorher an war - sonst {@code false}. Der Aufrufer
     *         sagt das: Ein "ist jetzt aus" auf etwas, das nie an war, laesst einen
     *         Moderator glauben, er habe eben noch eingesehen
     */
    public synchronized boolean schalteAus(UUID wer) {
        if (wer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Einsicht");
        }
        return bis.remove(wer) != null;
    }

    /**
     * Ob dieser Spieler in diesem Augenblick einsieht.
     *
     * <p>DIE FRAGE DER SCHUTZWACHE, und sie wird bei jedem Klick auf einen Behaelter
     * gestellt. Darum raeumt sie den abgelaufenen Eintrag gleich mit weg: Ohne das
     * waechst die Tabelle mit jedem Moderator, der je eingesehen hat - dieselbe
     * Begruendung, die an {@code Schutzwache.letzteMeldung} steht.
     *
     * <p>Der Vergleich ist {@code isAfter} und nicht {@code !isBefore}: Eine Einsicht,
     * die in diesem Augenblick ablaeuft, ist abgelaufen. Beim Grenzfall entscheidet die
     * Richtung GEGEN den Zugriff - anders als bei einer Strafe, wo sie fuer den
     * Betroffenen entscheidet. Hier ist der Zugriff das Besondere, nicht die Sperre.
     */
    public synchronized boolean schaut(UUID wer, Instant jetzt) {
        if (wer == null || jetzt == null) {
            return false;
        }
        Instant ende = bis.get(wer);
        if (ende == null) {
            return false;
        }
        if (!ende.isAfter(jetzt)) {
            bis.remove(wer);
            return false;
        }
        return true;
    }

    /**
     * Bis wann dieser Spieler einsieht - leer, wenn er es nicht tut.
     *
     * <p>Fuer die Antwort auf {@code /einsehen} ohne Zusatz: Wer wissen will, ob er noch
     * einsieht, fragt den Befehl - und erfaehrt die Restzeit. Ohne diese Auskunft muesste
     * er den Modus ausschalten, um zu erfahren, ob er an war.
     */
    public synchronized Optional<Instant> endeFuer(UUID wer, Instant jetzt) {
        return schaut(wer, jetzt) ? Optional.of(bis.get(wer)) : Optional.empty();
    }

    /**
     * Wie viele gerade einsehen - fuer den Bericht und fuer Proben.
     *
     * <p>Zaehlt nur die GUELTIGEN und raeumt die abgelaufenen weg. Eine Zahl, die
     * abgelaufene mitzaehlt, waere eine, die nie sinkt.
     */
    public synchronized int anzahl(Instant jetzt) {
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, wer"
                    + " noch einsieht");
        }
        bis.entrySet().removeIf(eintrag -> !eintrag.getValue().isAfter(jetzt));
        return bis.size();
    }

    /**
     * Raeumt diesen Spieler weg - beim Verlassen des Servers.
     *
     * <p>Dieselbe Begruendung wie bei {@code Schutzwache.beimVerlassen}: Sonst waechst
     * die Tabelle mit jedem Spieler, der je eingesehen hat, und niemand sieht es.
     *
     * <p><b>Und es ist mehr als Aufraeumen:</b> Wer den Server verlaesst und
     * wiederkommt, soll NICHT weiter einsehen. Ein Modus, der eine Abwesenheit
     * uebersteht, ist einer, den man beim Beitritt nicht mehr erwartet - und dann
     * oeffnet der naechste Klick eine fremde Kiste, die man nur ansehen wollte.
     */
    public synchronized void vergiss(UUID wer) {
        if (wer != null) {
            bis.remove(wer);
        }
    }

    private static void pruefe(UUID wer, Instant jetzt) {
        if (wer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Einsicht");
        }
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keine Frist -"
                    + " und ohne Frist waere die Einsicht fuer immer an");
        }
    }
}
