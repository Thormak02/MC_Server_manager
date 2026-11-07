package de.freimarken.kern;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kasse im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 *
 * <p>Fuehrt das Buchungsbuch mit, und zwar vollstaendig. Es gibt zwei Kassen hinter einer
 * Schnittstelle; wuerde nur eine das Buch fuehren, pruefte der {@code KasseVertrag} etwas
 * anderes, als der Server tut.
 *
 * <p>Jede Buchung eroeffnet das Konto, wenn es noch nicht offen ist. Sonst enthielte der
 * Stand das Startguthaben und das Buch nicht - und die Summenprobe waere schon beim
 * ersten Kauf falsch.
 */
public final class SpeicherKasse implements Kasse {

    private final Map<UUID, Long> stand = new HashMap<>();
    private final List<Buchung> buch = new ArrayList<>();
    private final long startguthaben;
    private final Clock uhr;
    private long naechsteId = 1L;

    public SpeicherKasse(long startguthaben) {
        this(startguthaben, Clock.systemUTC());
    }

    /**
     * @param uhr woher der Zeitpunkt einer Buchung kommt - fuer Proben, die sich nicht auf
     *            die Uhr verlassen sollen
     */
    public SpeicherKasse(long startguthaben, Clock uhr) {
        if (startguthaben < 0) {
            throw new IllegalArgumentException("Startguthaben darf nicht negativ sein");
        }
        this.startguthaben = startguthaben;
        this.uhr = uhr;
    }

    @Override
    public synchronized long guthaben(UUID spieler) {
        return stand.getOrDefault(spieler, startguthaben);
    }

    @Override
    public synchronized boolean eroeffne(UUID spieler) {
        if (stand.containsKey(spieler)) {
            return false;
        }
        stand.put(spieler, startguthaben);
        schreibe(spieler, startguthaben, Buchungsgrund.EROEFFNUNG, startguthaben);
        return true;
    }

    @Override
    public synchronized boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
        Kasse.pruefeBetrag(betrag);
        eroeffne(spieler);
        long vorhanden = guthaben(spieler);
        if (vorhanden < betrag) {
            return false;
        }
        long nachher = vorhanden - betrag;
        // Erst rechnen, dann beides setzen: Faellt etwas zwischen Stand und Zeile, bleibt
        // eine von beiden stehen - und genau das soll es nicht geben.
        stand.put(spieler, nachher);
        schreibe(spieler, -betrag, grund, nachher);
        return true;
    }

    @Override
    public synchronized void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
        Kasse.pruefeBetrag(betrag);
        eroeffne(spieler);
        // addExact und nicht +: Ein Ueberlauf wuerde aus viel Geld lautlos Schulden machen.
        // MariaDB bricht an derselben Stelle mit einem Fehler ab - beide Kassen sollen sich
        // gleich verhalten, nicht nur solange die Betraege klein bleiben.
        long nachher = Math.addExact(guthaben(spieler), betrag);
        stand.put(spieler, nachher);
        schreibe(spieler, betrag, grund, nachher);
    }

    @Override
    public synchronized List<Buchung> verlauf(UUID spieler, int hoechstens) {
        Kasse.pruefeAnzahl(hoechstens);
        return buch.stream()
                .filter(zeile -> zeile.spieler().equals(spieler))
                .sorted(Comparator.comparingLong(Buchung::id).reversed())
                .limit(hoechstens)
                .toList();
    }

    @Override
    public synchronized long summeSeit(UUID spieler, Instant ab,
                                      Buchungsgrund grund) {
        if (spieler == null || ab == null || grund == null) {
            throw new IllegalArgumentException("Ohne Spieler, Zeitpunkt und Grund gibt es"
                    + " keine Tagessumme");
        }
        long summe = 0L;
        for (Buchung zeile : buch) {
            if (zeile.spieler().equals(spieler) && zeile.grund() == grund
                    && !zeile.zeitpunkt().isBefore(ab)) {
                summe += zeile.betrag();
            }
        }
        return summe;
    }

    private void schreibe(UUID spieler, long betrag, Buchungsgrund grund, long nachher) {
        buch.add(new Buchung(naechsteId++, uhr.instant(), spieler, grund, betrag, nachher));
    }
}
