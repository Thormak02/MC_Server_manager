package de.freimarken.kern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Die abgesteckten Ecken je Spieler - fluechtig, im Arbeitsspeicher.
 *
 * <p><b>Fluechtig und mit Absicht nicht in der Datenbank:</b> Eine Absteckung ist eine
 * halbe Handlung, keine Tat. Wer sie ueber einen Neustart hinweg behielte, fuende nach
 * einer Woche zwei Marken vor, an die er sich nicht erinnert - und legte damit ein
 * Grundstueck an, das er nicht gemeint hat. Vergessen ist hier die sichere Richtung.
 *
 * <p><b>Hoechstens ZWEI Ecken, und die dritte beginnt neu.</b> Nicht abwechselnd wie in
 * manchen Baukloetzchen-Werkzeugen: Dort muss man wissen, welche Ecke als naechste
 * ersetzt wird, und wer sich irrt, verschiebt die falsche. So ist die Regel in einem Satz
 * erklaert, und jeder Stand laesst sich an zwei Zahlen ablesen.
 *
 * <p>{@code synchronized} auf jeder Methode: Die Ecken kommen aus einem Befehl, also vom
 * Hauptfaden - aber {@link #vergiss} wird auch beim Abmelden gerufen, und wo Paper das
 * tut, ist nicht verbuergt.
 */
public final class Absteckbuch {

    /** So viele Ecken macht ein Rechteck. */
    public static final int ECKEN = 2;

    private final Map<UUID, List<Blockecke>> ecken = new HashMap<>();

    /**
     * Eine Ecke abstecken.
     *
     * @return der Stand NACH dem Abstecken - wie viele Ecken stehen und welche
     */
    public synchronized Stand stecke(UUID spieler, int blockX, int blockZ) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Absteckung");
        }
        List<Blockecke> seine = ecken.computeIfAbsent(spieler, wer -> new ArrayList<>());
        if (seine.size() >= ECKEN) {
            // Die dritte Ecke beginnt eine NEUE Absteckung - siehe Javadoc der Klasse.
            seine.clear();
        }
        seine.add(new Blockecke(blockX, blockZ));
        return new Stand(List.copyOf(seine), seine.size() == 1);
    }

    /**
     * Die beiden Ecken dieses Spielers - nur, wenn es wirklich zwei sind.
     *
     * <p>Eine halbe Absteckung kommt hier nicht heraus: Der Aufrufer soll nicht
     * entscheiden muessen, was eine einzelne Ecke bedeutet. Wie viele stehen, sagt
     * {@link #zahl}.
     */
    public synchronized Optional<Zweiecke> fertige(UUID spieler) {
        List<Blockecke> seine = ecken.get(spieler);
        if (seine == null || seine.size() < ECKEN) {
            return Optional.empty();
        }
        return Optional.of(new Zweiecke(seine.get(0), seine.get(1)));
    }

    /** Wie viele Ecken dieser Spieler gesetzt hat - 0, 1 oder 2. */
    public synchronized int zahl(UUID spieler) {
        List<Blockecke> seine = ecken.get(spieler);
        return seine == null ? 0 : seine.size();
    }

    /**
     * Die Absteckung dieses Spielers vergessen.
     *
     * <p>Gerufen nach jedem Anlegen und beim Abmelden. Nach dem Anlegen, weil die Ecken
     * sonst stehen blieben und das naechste {@code anlegen} dasselbe Grundstueck noch
     * einmal versuchte - mit einer Absage, die niemand versteht.
     */
    public synchronized void vergiss(UUID spieler) {
        ecken.remove(spieler);
    }

    /** Wie viele Spieler gerade abstecken - fuer Proben und fuer den Startbericht. */
    public synchronized int absteckende() {
        return ecken.size();
    }

    /**
     * Zwei fertige Ecken.
     *
     * <p>Ein eigener Typ und keine Liste: Eine Liste koennte drei tragen, und dann
     * muesste jeder Aufrufer entscheiden, welche zwei gemeint sind.
     */
    public record Zweiecke(Blockecke eine, Blockecke andere) {
    }

    /**
     * Der Stand nach einem Abstecken - fuer die Meldung an den Spieler.
     *
     * @param gesetzte  die Ecken, die jetzt stehen
     * @param neuBegonnen ob diese Ecke eine NEUE Absteckung angefangen hat. Der Spieler
     *                    soll lesen, dass seine vorige weg ist - sonst sucht er sie beim
     *                    naechsten Anlegen
     */
    public record Stand(List<Blockecke> gesetzte, boolean neuBegonnen) {

        /** Ob die Absteckung fertig ist. */
        public boolean fertig() {
            return gesetzte.size() == ECKEN;
        }

        /** Die zuletzt gesetzte Ecke. */
        public Blockecke letzte() {
            return gesetzte.get(gesetzte.size() - 1);
        }
    }
}
