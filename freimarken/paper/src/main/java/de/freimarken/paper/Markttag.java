package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.util.UUID;

/**
 * Was ein Spieler HEUTE schon aus Verkaeufen an den Bund bekommen hat - EINE Stelle.
 *
 * <p><b>Warum eine eigene Klasse fuer eine Zeile:</b> Diese Zahl wird seit dem 10.11.2026
 * an ZWEI Stellen gebraucht, und die beiden muessen dieselbe nennen. {@code /markt liste}
 * sagt dem Spieler, was heute noch zu holen ist; der Ankaufstand am Schild entscheidet
 * damit, wie viel er noch nimmt. Zwei Abschriften derselben Abfrage liefen auseinander -
 * und dann verspricht die Liste etwas, das der Stand nicht mehr hergibt.
 *
 * <p>Gezaehlt wird aus dem BUCHUNGSBUCH und nicht aus einem Zaehler: Dieselbe
 * Entscheidung wie beim Verdienstdeckel, und mit demselben Grund - ein Zaehler im
 * Arbeitsspeicher ueberlebte keinen Neustart, und dann waere der Deckel nach jedem
 * Neustart offen.
 *
 * <p>Die ZONE kommt aus dem {@link Wirtschaftsstand} und nicht aus der Systemzeit: Wann
 * ein Tag beginnt, ist eine Einstellung ({@code zeit.zone}), und ein Deckel, der nach
 * einer anderen Zone rechnet als der Tagesbonus, geht an einem Tag im Jahr um eine Stunde
 * daneben.
 */
final class Markttag {

    private Markttag() {
    }

    /**
     * Die Summe der heutigen Gutschriften aus {@link Buchungsgrund#BUNDESMARKT_VERKAUF}.
     *
     * <p>Gefragt wird je Tat und nicht je Takt: Ein Verkauf ist eine Tat des Spielers,
     * und die kostet eine Abfrage. Wirft, wenn die Kasse schweigt - beide Aufrufer fangen
     * das, und zwar verschieden: Der Befehl sagt es, der Handel sagt ab. Ein stiller
     * Rueckfall auf 0 waere der offene Deckel.
     */
    static long heuteSchon(Kasse kasse, Wirtschaftsstand wirtschaft, Clock uhr,
            UUID wer) {
        if (kasse == null || wirtschaft == null || uhr == null) {
            throw new IllegalArgumentException("Ohne Kasse, Wirtschaftsstand und Uhr"
                    + " gibt es kein Heute");
        }
        Zeitwerte zeit = wirtschaft.werte().zeit();
        return kasse.summeSeit(wer,
                Tagesgrenze.tagesbeginn(Tagesgrenze.tagVon(uhr.instant(), zeit.zone()),
                        zeit.zone()),
                Buchungsgrund.BUNDESMARKT_VERKAUF);
    }
}
