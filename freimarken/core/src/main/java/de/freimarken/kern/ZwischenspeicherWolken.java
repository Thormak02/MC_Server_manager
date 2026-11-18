package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine VOLLSTAENDIGE Kopie der Inselstaende im Arbeitsspeicher - kein Cache mit Verfall.
 *
 * <p><b>Warum es ihn gibt:</b> Der Kobbelsteingenerator fragt bei JEDEM gebildeten Block
 * nach der Insel-Stufe, und zwar im Hauptfaden. Ueber {@link MariaWolken} waeren das zwei
 * Abfragen je Block - bei einem Spieler mit einem Automaten einige je Sekunde, bei zehn
 * Spielern ein Server, der stockt. Dasselbe Problem hat {@link ZwischenspeicherBerufe}
 * schon einmal geloest, nur haengt es hier an einer Zahl, die sich fast nie aendert.
 *
 * <p><b>Darum die Bauart von {@link ZwischenspeicherGrundstuecke} und nicht die mit
 * Verfall:</b> Diese Karte haelt ALLE Inselstaende, wird beim Start einmal gefuellt und
 * bei jedem Schreibzug mitgefuehrt. Eine Abfrage trifft nie die Datenbank, und es gibt
 * keinen Zeitpunkt, an dem sie eine veraltete Stufe nennt. Mit Verfall waere der Fehler
 * genau der, den ein Spieler meldet: Er erfuellt eine Herausforderung, und eine halbe
 * Minute lang gibt sein Generator noch die alte Ausbeute.
 *
 * <p><b>Die Rechnung dazu</b>, wie bei den Grundstuecken und nicht geschaetzt:
 * Wolkenwerder hat {@link Wolkengrenze#inselnZahl} vergebbare Felder, und jedes traegt
 * hoechstens EINE Insel. Mehr als 1.443 Eintraege kann diese Karte also nie haben - die
 * Obergrenze folgt aus der Weltgeometrie und nicht aus einer Annahme ueber Spieler.
 *
 * <p><b>Die Reihenfolge der zwei Schreibzuege ist Pflicht:</b> zuerst die Ablage, dann die
 * Karte. Scheitert die Ablage, bleibt die Karte unveraendert, und der Stand gilt als nicht
 * geschrieben - das ist die sichere Richtung. Umgekehrt stuende eine Stufe in der Karte,
 * die nach dem naechsten Neustart niemand mehr findet, und die Erzausbeute faellt dann
 * ohne Anlass zurueck.
 *
 * <p><b>Dieselbe Zusage wie die Ablage dahinter.</b> {@code WolkenVertrag} prueft auch
 * diese Fassung: Ein Zwischenspeicher, der sich anders verhaelt, ist kein
 * Zwischenspeicher, sondern ein zweiter Stand.
 */
public final class ZwischenspeicherWolken implements Wolkenablage {

    private final Wolkenablage wahrheit;

    /** Je Insel ihr Stand. Keine Zeile heisst: Diese Insel hat noch nichts getan. */
    private final Map<Long, Inselstand> staende = new HashMap<>();

    public ZwischenspeicherWolken(Wolkenablage wahrheit) {
        if (wahrheit == null) {
            throw new IllegalArgumentException("Ohne Ablage gibt es keinen"
                    + " Zwischenspeicher");
        }
        this.wahrheit = wahrheit;
    }

    /**
     * Liest ALLE Staende aus der Ablage - einmal beim Start.
     *
     * <p>Wie bei den Grundstuecken wird die Karte nie halb gefuellt: Wirft die Ablage,
     * bleibt sie leer, und eine leere Karte heisst "keine Insel hat etwas getan" und nicht
     * "ich weiss es nicht". Darum muss der Aufrufer den Fehlschlag beim Start behandeln -
     * ein Server, der hier weiterlaeuft, gibt jeder Insel die Ausbeute der Stufe 0.
     */
    public synchronized void fuellen() {
        staende.clear();
        for (Inselstand stand : wahrheit.alle()) {
            staende.put(stand.inselId(), stand);
        }
    }

    /** Wie viele Staende die Karte haelt - fuer den Startbericht und die Proben. */
    public synchronized int groesse() {
        return staende.size();
    }

    @Override
    public synchronized Inselstand stand(long inselId) {
        pruefe(inselId);
        Inselstand gefunden = staende.get(inselId);
        return gefunden != null ? gefunden : Inselstand.leer(inselId);
    }

    @Override
    public synchronized List<Inselstand> alle() {
        return List.copyOf(new ArrayList<>(staende.values()));
    }

    @Override
    public synchronized void biomSetzen(long inselId, String biom) {
        wahrheit.biomSetzen(inselId, biom);
        staende.put(inselId, nachgezogen(inselId).mitBiom(biom));
    }

    @Override
    public synchronized void punkteSetzen(long inselId, long punkte, Instant wann) {
        wahrheit.punkteSetzen(inselId, punkte, wann);
        staende.put(inselId, nachgezogen(inselId).mitPunkten(punkte, wann));
    }

    @Override
    public synchronized boolean erfuellen(long inselId, String schluessel) {
        // Die ANTWORT der Ablage ist die Zusage, an der die Belohnung haengt - diese
        // Fassung darf sie nicht selbst geben. Ein eigenes hatErfuellt vorweg waere genau
        // die Luecke, die der Vertrag verbietet: Zwei Abgaben im selben Augenblick
        // fragten beide die Karte, fanden nichts und zahlten zweimal.
        boolean neu = wahrheit.erfuellen(inselId, schluessel);
        if (neu) {
            staende.put(inselId, nachgezogen(inselId).mitErfuellt(schluessel));
        }
        return neu;
    }

    @Override
    public synchronized void entfernen(long inselId) {
        wahrheit.entfernen(inselId);
        staende.remove(inselId);
    }

    /**
     * Der Stand, auf den ein Schreibzug AUFSETZT - aus der Karte oder aus der Ablage.
     *
     * <p>Der Unterschied zu {@link #stand} ist der ganze Grund, warum diese Methode
     * dasteht: Jener Weg laeuft bei jedem gebildeten Block und darf die Ablage NIE fragen -
     * eine Insel ohne Zeile ist der Normalfall, und eine Abfrage je Block waere genau die
     * Last, gegen die dieser Zwischenspeicher gebaut ist. Ein Schreibzug dagegen ist
     * selten, und er setzt auf dem vollen Stand auf: Ohne dieses Nachziehen loeschte ein
     * {@code biomSetzen} auf einem Zwischenspeicher, der noch nicht gefuellt wurde, in der
     * KARTE die Punkte und die erfuellten Herausforderungen - in der Ablage stuenden sie
     * weiter, und die zwei Staende liefen auseinander, bis irgendwer neu startet.
     */
    private Inselstand nachgezogen(long inselId) {
        Inselstand gefunden = staende.get(inselId);
        return gefunden != null ? gefunden : wahrheit.stand(inselId);
    }

    private static void pruefe(long inselId) {
        if (inselId <= 0L) {
            throw new IllegalArgumentException("Eine Insel ohne Nummer gibt es nicht,"
                    + " nicht " + inselId);
        }
    }
}
