package de.freimarken.kern;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Die Grundstuecke im Arbeitsspeicher - fuer den Betrieb ohne Datenbank und fuer Proben.
 *
 * <p><b>Vergisst beim Neustart, und das ist der Zweck.</b> Ohne eingetragene Datenbank
 * laeuft der Server fluechtig und sagt das laut; dann ist diese Fassung die Ablage. Und
 * sie ist die zweite Umsetzung desselben Vertrags - damit
 * {@code GrundstuecksablageVertrag} beide prueft und keine Zusage nur fuer eine gilt.
 *
 * <p>{@code synchronized} auf jeder Methode, wie bei {@link SpeicherLager}: Die
 * Ueberschneidungspruefung und das Einhaengen muessen EINE Tat sein. Liefen zwei
 * Zuschnitte dazwischen, faenden beide ihre Flaeche frei und beide schrieben - zwei
 * Grundstuecke auf denselben Zellen, und die Punktabfrage koennte nicht mehr sagen, wem
 * der Block gehoert.
 *
 * <p><b>Die Ueberschneidung wird HIER noch einmal geprueft</b>, obwohl das
 * {@link Grundstuecksbuch} sie schon prueft. Das ist kein doppelter Riegel im Sinne der
 * Hausregel, sondern derselbe Riegel an der einzigen Stelle, an der er wirklich
 * dichtmacht: Im Buch liegt zwischen Frage und Schreiben eine Luecke, hier liegt beides
 * in einem {@code synchronized}. In {@code MariaGrundstuecke} uebernimmt diese Rolle ein
 * {@code SELECT ... FOR UPDATE} in derselben Transaktion.
 */
public final class SpeicherGrundstuecke implements Grundstuecksablage {

    /** Alle Grundstuecke nach Nummer, in der Reihenfolge des Anlegens. */
    private final Map<Long, Grundstueck> nachNummer = new LinkedHashMap<>();

    private final Clock uhr;
    private long naechsteNummer = 1L;

    /** Mit der Systemuhr - fuer den fluechtigen Betrieb. */
    public SpeicherGrundstuecke() {
        this(Clock.systemUTC());
    }

    /**
     * Mit einer eigenen Uhr - fuer Proben, die nicht warten wollen.
     *
     * <p>Die Uhr kommt herein und steht nicht hier: Die Verfallsregel aus Plan 16 rechnet
     * mit {@code erworbenAm}, und eine Probe dazu muesste sonst zwoelf Monate warten.
     */
    public SpeicherGrundstuecke(Clock uhr) {
        this.uhr = uhr;
    }

    @Override
    public synchronized Grundstueck anlegen(String welt, long stadtId,
                                            Zellquader flaeche, UUID eigentuemer,
                                            long preis) {
        // Im SELBEN synchronized wie das Einhaengen - siehe Javadoc der Klasse.
        for (Grundstueck vorhanden : nachNummer.values()) {
            if (vorhanden.welt().equals(welt) && vorhanden.flaeche().schneidet(flaeche)) {
                throw new IllegalStateException("Die Flaeche " + flaeche.alsText()
                        + " ueberschneidet sich mit Grundstueck " + vorhanden.id()
                        + " (" + vorhanden.flaeche().alsText() + ")");
            }
        }
        long nummer = naechsteNummer++;
        Grundstueck neu = new Grundstueck(nummer, welt, stadtId, eigentuemer, Set.of(),
                flaeche, preis, Instant.now(uhr), Grundstueck.KEIN_ANGEBOT);
        nachNummer.put(nummer, neu);
        return neu;
    }

    @Override
    public synchronized Optional<Grundstueck> anZelle(String welt, int zelleX,
                                                      int zelleZ) {
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.welt().equals(welt) && eines.enthaelt(zelleX, zelleZ)) {
                return Optional.of(eines);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<Grundstueck> inStadt(long stadtId) {
        List<Grundstueck> gefunden = new ArrayList<>();
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.stadtId() == stadtId) {
                gefunden.add(eines);
            }
        }
        return List.copyOf(gefunden);
    }

    @Override
    public synchronized List<Grundstueck> alle(String welt) {
        List<Grundstueck> gefunden = new ArrayList<>();
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.welt().equals(welt)) {
                gefunden.add(eines);
            }
        }
        return List.copyOf(gefunden);
    }

    @Override
    public synchronized List<Grundstueck> vonSpieler(String welt, UUID spieler) {
        List<Grundstueck> gefunden = new ArrayList<>();
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.welt().equals(welt) && eines.gehoert(spieler)) {
                gefunden.add(eines);
            }
        }
        return List.copyOf(gefunden);
    }

    @Override
    public synchronized boolean besitztIn(String welt, long stadtId, UUID spieler) {
        if (spieler == null) {
            return false;
        }
        // Ein Durchlauf und kein zweiter Index: Diese Umsetzung ist die fuer Proben und
        // fuer einen Server ohne Datenbank, und dort sind es ein paar Dutzend Zeilen. Ein
        // Index daneben waere eine zweite Wahrheit, die eine Aenderung verpassen kann -
        // die gepufferte Fassung fuehrt ihn, und ihr Javadoc sagt, was sie dafuer tut.
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.stadtId() == stadtId && eines.welt().equals(welt)
                    && spieler.equals(eines.eigentuemer())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        Grundstueck alt = holeOderWirf(id);
        // DURCHTRAGEN, nicht neu setzen - die Lehre aus dem Fund vom 11.11.2026: Dort
        // baute SpeicherLager die Zeile ueber einen verkuerzten Konstruktor neu und
        // verlor dabei den Namen der Freistadt. Hier geht jedes Feld ausdruecklich mit.
        nachNummer.put(id, new Grundstueck(alt.id(), alt.welt(), alt.stadtId(),
                alt.eigentuemer(), mitbauer, alt.flaeche(), alt.preis(),
                alt.erworbenAm(), alt.angebotspreis()));
    }

    @Override
    public synchronized void anbieten(long id, Long angebotspreis) {
        Grundstueck alt = holeOderWirf(id);
        nachNummer.put(id, new Grundstueck(alt.id(), alt.welt(), alt.stadtId(),
                alt.eigentuemer(), alt.mitbauer(), alt.flaeche(), alt.preis(),
                alt.erworbenAm(), angebotspreis));
    }

    @Override
    public synchronized Grundstueck eigentuemerSetzen(long id, UUID neuerEigentuemer,
                                                      long preis) {
        Grundstueck alt = holeOderWirf(id);
        // DER RIEGEL GEGEN DEN DOPPELKAUF, und er steht im SELBEN synchronized wie das
        // Schreiben: Zwei Kaeufer, die gleichzeitig zugreifen, laufen hier auf - der
        // zweite findet kein Angebot mehr. Im Buch liegt zwischen Frage und Schreiben
        // eine Luecke, hier liegt beides in einer Tat.
        if (!alt.stehtZumVerkauf()) {
            throw new IllegalStateException("Grundstueck " + id + " steht nicht zum"
                    + " Verkauf - entweder war es nie angeboten, oder ein anderer war"
                    + " schneller");
        }
        // Die MITBAUER werden geleert, und das ist keine Nachlaessigkeit: Wer verkauft,
        // verkauft nicht die Leute mit, die darauf bauen durften. Siehe Javadoc der
        // Schnittstelle.
        // erworbenAm NEU, nicht durchgetragen: Die Verfallsregel aus Plan 16 rechnet
        // damit, und der neue Besitzer hat es jetzt erworben. Siehe die Schnittstelle.
        Grundstueck neu = new Grundstueck(alt.id(), alt.welt(), alt.stadtId(),
                neuerEigentuemer, Set.of(), alt.flaeche(), preis,
                uhr.instant(), Grundstueck.KEIN_ANGEBOT);
        nachNummer.put(id, neu);
        return neu;
    }

    @Override
    public synchronized List<Grundstueck> angebote(String welt) {
        List<Grundstueck> gefunden = new ArrayList<>();
        for (Grundstueck eines : nachNummer.values()) {
            if (eines.welt().equals(welt) && eines.stehtZumVerkauf()) {
                gefunden.add(eines);
            }
        }
        return List.copyOf(gefunden);
    }

    /** Das Grundstueck zu dieser Nummer - oder ein Wurf. */
    private Grundstueck holeOderWirf(long id) {
        Grundstueck alt = nachNummer.get(id);
        if (alt == null) {
            throw new IllegalStateException("Kein Grundstueck mit der Nummer " + id);
        }
        return alt;
    }

    @Override
    public synchronized void entfernen(long id) {
        if (nachNummer.remove(id) == null) {
            throw new IllegalStateException("Kein Grundstueck mit der Nummer " + id);
        }
    }
}
