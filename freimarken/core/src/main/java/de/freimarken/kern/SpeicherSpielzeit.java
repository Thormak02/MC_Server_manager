package de.freimarken.kern;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spielzeit im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im Betrieb
 * einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken. Derselbe
 * Rueckfall, den {@link SpeicherKasse} und {@link SpeicherLager} schon haben, damit
 * Ausprobieren ohne Datenbank nicht ins Leere laeuft.
 *
 * <p>Braucht KEINE Uhr - anders als {@link SpeicherKasse}. Der Tag kommt mit dem Zuschlag
 * herein und bei {@link #lies} von aussen; eine eigene Uhr hier waere eine zweite Quelle fuer
 * die Tagesgrenze und damit genau der Fehler, gegen den {@link Tagesgrenze} gebaut ist.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherKasse}: Die Zuhoerer und
 * Zeitgeber des Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber
 * ausdruecklich nebenlaeufig. Eine nackte {@link HashMap} aus zwei Faeden ist nicht nur ein
 * verlorener Schreibzug, sondern im schlechten Fall eine zerstoerte Karte.
 */
public final class SpeicherSpielzeit implements Spielzeitablage {

    private final Map<UUID, Map<LocalDate, Long>> sekunden = new HashMap<>();

    @Override
    public synchronized void schreibeFort(List<Zuschlag> zuschlaege) {
        if (zuschlaege == null) {
            throw new IllegalArgumentException("Nichts zu schreiben heisst eine leere Liste,"
                    + " nicht null");
        }
        // Erst vollstaendig rechnen, dann uebernehmen: Die Schnittstelle sagt "alles oder
        // nichts". Bei einem Ueberlauf mitten in der Liste blieben sonst die ersten Zuschlaege
        // stehen, und die beiden Umsetzungen verhielten sich verschieden - die eine mit
        // Transaktion, die andere halb geschrieben.
        Map<UUID, Map<LocalDate, Long>> nachher = new HashMap<>();
        for (Zuschlag zuschlag : zuschlaege) {
            if (zuschlag == null) {
                throw new IllegalArgumentException("Ein Zuschlag in der Liste ist null");
            }
            Map<LocalDate, Long> tage = nachher.computeIfAbsent(zuschlag.spieler(),
                    wer -> new HashMap<>(sekunden.getOrDefault(wer, Map.of())));
            // addExact und nicht +: Ein Ueberlauf wuerde aus viel Spielzeit lautlos eine
            // negative machen. MariaDB bricht an derselben Stelle ab ("BIGINT value is out
            // of range") - beide Fassungen sollen sich gleich verhalten, nicht nur solange
            // die Zahlen klein sind.
            //
            // Und sie sollen dabei DASSELBE werfen: IllegalStateException, so wie jeder
            // fehlgeschlagene Schreibzug der Datenbankfassung. Die ArithmeticException von
            // addExact wandert deshalb hier in die Hausform. Die Richtung ist nicht
            // beliebig: MariaDB meldet den Ueberlauf als SQLException (Fehler 1690), und
            // daraus eine ArithmeticException zu machen hiesse, in MariaSpielzeit
            // Fehlernummern des Herstellers abzufragen - eine Dialektbindung in einer
            // Klasse, die bis auf das abgekuendigte VALUES() ausdruecklich dialektfrei
            // gehalten ist. Umgekehrt kostet es diese drei Zeilen. Entschieden wird das im
            // SpielzeitVertrag (ueberlaufBrichtAb), der beide Fassungen daran prueft.
            try {
                tage.merge(zuschlag.tag(), zuschlag.sekunden(), Math::addExact);
            } catch (ArithmeticException ueberlauf) {
                throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                        + zuschlaege.size() + " Zuschlaege, nicht geschrieben)", ueberlauf);
            }
        }
        sekunden.putAll(nachher);
    }

    @Override
    public synchronized Spielzeitstand lies(UUID spieler, LocalDate heute) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        if (heute == null) {
            throw new IllegalArgumentException("Ohne Tag ist 'heute' nicht zu beantworten");
        }
        Map<LocalDate, Long> tage = sekunden.get(spieler);
        if (tage == null) {
            return Spielzeitstand.leer(spieler, heute);
        }
        long gesamt = 0L;
        try {
            for (long wert : tage.values()) {
                gesamt = Math.addExact(gesamt, wert);
            }
        } catch (ArithmeticException ueberlauf) {
            // Dieselbe Hausform wie beim Schreiben, und aus demselben Grund: MariaDB
            // summiert BIGINT als DECIMAL, bildet die Summe also noch und scheitert erst
            // beim Herausgeben als long - das ist eine SQLException, und MariaSpielzeit
            // macht daraus "Spielzeit nicht lesbar". Bliebe hier die ArithmeticException
            // stehen, faenge ein Aufrufer je nach Ablage eine andere Ausnahme, und die
            // Anzeige brach auf der einen Fassung anders ab als auf der anderen.
            //
            // Erreichbar ist der Fall ueber die Schnittstelle, anders als man denkt:
            // Der Schreibweg haelt jede EINZELNE Tageszeile unter Long.MAX_VALUE, die
            // Summe ueber mehrere Tage ist davon nicht gedeckt. Entschieden wird es im
            // SpielzeitVertrag (ueberlaufBeimLesenBrichtAb), der beide Fassungen prueft.
            throw new IllegalStateException("Spielzeit nicht lesbar: " + spieler,
                    ueberlauf);
        }
        return new Spielzeitstand(spieler, heute, gesamt, tage.getOrDefault(heute, 0L));
    }
}
