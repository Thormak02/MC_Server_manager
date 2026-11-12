package de.freimarken.kern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Eine VOLLSTAENDIGE Kopie der Grundstuecke im Arbeitsspeicher - kein Cache mit Verfall.
 *
 * <p><b>Dieselbe Bauart wie {@link ZwischenspeicherShops}, und aus demselben belegten
 * Grund:</b> Dessen Javadoc nennt QuickShop #2165 - ein Zwischenspeicher mit Verfall
 * lieferte dort bis zu drei Minuten lang die Antwort "hier ist kein Shop", und ein
 * Trichter zog derweil die Kiste leer. Hier waere der Fehler derselbe: Zwischen dem
 * Zuschneiden eines Grundstuecks und dem Ablauf eines Eintrags "hier ist nichts" duerfte
 * jeder darauf bauen.
 *
 * <p><b>Darum kein Verfall.</b> Diese Karte haelt ALLE Grundstuecke, wird beim Start
 * einmal gefuellt und bei jeder Aenderung mitgefuehrt. Eine Abfrage trifft nie die
 * Datenbank, und es gibt keinen Zeitpunkt, an dem sie eine veraltete Antwort gibt.
 *
 * <p><b>Warum das tragbar ist - und hier ist es eine RECHNUNG, keine Schaetzung:</b> Die
 * Welt der Stadtfelder hat 399 vergebbare Felder ({@link Feldgrenze#staedteZahl}), jedes
 * traegt 16 x 16 = 256 Zellen. Mehr als 102.144 Zellen kann es also nie geben, und jede
 * gehoert hoechstens einem Grundstueck. Die Karte hat damit eine OBERGRENZE, die aus der
 * Weltgeometrie folgt und nicht aus einer Annahme ueber das Spielerverhalten. Bei den
 * Parzellen war das anders - dort hilft nur ein Cache mit Verfall, und sein Javadoc sagt
 * warum.
 *
 * <p><b>Eine Karte JE ZELLE und nicht je Grundstueck</b>, und das ist der ganze Zweck:
 * {@code darfBauen} laeuft bei jedem gesetzten Block und fragt nach EINER Zelle. Ein
 * Durchlauf ueber alle Quader waere dort bei einer vollen Welt ein Vergleich gegen
 * hunderttausend Rechtecke. So ist es ein Nachsehen in einer Karte. Der Preis ist, dass
 * ein Grundstueck mit 256 Zellen 256 Eintraege belegt - die Obergrenze oben zaehlt genau
 * diese Eintraege, nicht die Grundstuecke.
 *
 * <p><b>Die Reihenfolge der zwei Schreibzuege ist Pflicht:</b> zuerst die Ablage, dann
 * die Karte. Scheitert die Ablage, bleibt die Karte unveraendert und das Grundstueck
 * gilt als nicht angelegt - das ist die sichere Richtung. Umgekehrt stuende ein
 * Grundstueck in der Karte, das nach dem naechsten Neustart niemand mehr findet.
 *
 * <p><b>Die Karte wird nie halb gefuellt.</b> Scheitert {@link #fuellen}, bleibt sie
 * leer - und eine leere Karte heisst "es gibt keine Grundstuecke", nicht "ich weiss es
 * nicht". Darum muss der Aufrufer den Fehlschlag beim Start behandeln: Ein Server, der
 * mit leerer Karte weiterlaeuft, laesst jeden auf jedem verkauften Grundstueck bauen.
 */
public final class ZwischenspeicherGrundstuecke implements Grundstuecksablage {

    private final Grundstuecksablage wahrheit;

    /**
     * Je ZELLE das Grundstueck, das sie traegt - der heisse Pfad.
     *
     * <p>Der Schluessel ist {@code welt/zx/zz}, wie bei {@link ZwischenspeicherLager}:
     * eine Zeichenkette und kein eigener Record, damit die Karte ohne {@code equals} und
     * {@code hashCode} einer weiteren Klasse auskommt.
     */
    private final Map<String, Grundstueck> nachZelle = new HashMap<>();

    /** Je Nummer das Grundstueck - fuer Aenderungen, die nur die Nummer kennen. */
    private final Map<Long, Grundstueck> nachNummer = new HashMap<>();

    /**
     * Die Welt, die diese Karte deckt - {@code null}, solange nicht gefuellt wurde.
     *
     * <p><b>EINE Welt, und jede andere geht ungepuffert durch.</b> Das ist der Grund,
     * aus dem die Obergrenze oben eine Rechnung sein kann und keine Schaetzung: Sie
     * folgt aus der Geometrie GENAU DIESER Welt. Eine Karte ueber alle Welten haette
     * keine Grenze mehr - und Hafenwerder und Wolkenwerder stehen im Plan.
     *
     * <p>Durchreichen und nicht verweigern: Eine fremde Welt ist kein Fehler, sondern
     * eine Frage, fuer die dieser Speicher nicht gebaut ist. Sie kostet dann einen Gang
     * zur Ablage - und sie kommt auf dem heissen Pfad nie vor, weil dort nur die
     * Stadtfelderwelt gefragt wird.
     */
    private String gedeckteWelt;

    /**
     * Ob {@link #fuellen} gelaufen ist.
     *
     * <p>Eine leere Karte und eine ungefuellte sehen gleich aus, bedeuten aber das
     * Gegenteil. Jede Frage auf dem heissen Pfad wirft darum, solange nicht gefuellt
     * wurde - lieber ein lauter Fehler beim Start als ein stilles Ja fuer jeden Block.
     */
    private boolean gefuellt;

    public ZwischenspeicherGrundstuecke(Grundstuecksablage wahrheit) {
        this.wahrheit = wahrheit;
    }

    /**
     * Die Karte aus der Ablage fuellen - einmal beim Start.
     *
     * @param welt die Stadtfelderwelt. EINE Welt und nicht alle: Grundstuecke gibt es
     *             heute nur dort, und eine Karte ueber alle Welten haette keine
     *             Obergrenze mehr
     * @return wie viele Grundstuecke gefunden wurden
     */
    public synchronized int fuellen(String welt) {
        List<Grundstueck> alle = wahrheit.alle(welt);
        nachZelle.clear();
        nachNummer.clear();
        for (Grundstueck eines : alle) {
            merke(eines);
        }
        gedeckteWelt = welt;
        gefuellt = true;
        return alle.size();
    }

    /** Wie viele ZELLEN belegt sind - fuer den Startbericht und fuer Proben. */
    public synchronized int belegteZellen() {
        return nachZelle.size();
    }

    @Override
    public synchronized Optional<Grundstueck> anZelle(String welt, int zelleX,
                                                      int zelleZ) {
        if (!deckt(welt)) {
            return wahrheit.anZelle(welt, zelleX, zelleZ);
        }
        return Optional.ofNullable(nachZelle.get(schluessel(welt, zelleX, zelleZ)));
    }

    @Override
    public synchronized Grundstueck anlegen(String welt, long stadtId,
                                            Zellquader flaeche, UUID eigentuemer,
                                            long preis) {
        // ERST die Ablage, DANN die Karte - siehe Javadoc der Klasse.
        Grundstueck neu = wahrheit.anlegen(welt, stadtId, flaeche, eigentuemer, preis);
        if (deckt(welt)) {
            merke(neu);
        }
        return neu;
    }

    @Override
    public synchronized void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        wahrheit.mitbauerSetzen(id, mitbauer);
        Grundstueck alt = nachNummer.get(id);
        if (alt == null) {
            // Entweder liegt es in einer Welt, die diese Karte nicht deckt - dann ist
            // nichts zu tun -, oder die Karte ist schief. Beides ist von hier aus nicht
            // zu unterscheiden, und beides ist mit Nichtstun richtig behandelt: Was
            // nicht in der Karte steht, kann aus ihr nicht falsch beantwortet werden.
            return;
        }
        vergiss(alt);
        merke(new Grundstueck(alt.id(), alt.welt(), alt.stadtId(), alt.eigentuemer(),
                mitbauer, alt.flaeche(), alt.preis(), alt.erworbenAm(),
                alt.angebotspreis()));
    }

    @Override
    public synchronized void anbieten(long id, Long angebotspreis) {
        wahrheit.anbieten(id, angebotspreis);
        Grundstueck alt = nachNummer.get(id);
        if (alt == null) {
            return;
        }
        vergiss(alt);
        merke(new Grundstueck(alt.id(), alt.welt(), alt.stadtId(), alt.eigentuemer(),
                alt.mitbauer(), alt.flaeche(), alt.preis(), alt.erworbenAm(),
                angebotspreis));
    }

    @Override
    public synchronized Grundstueck eigentuemerSetzen(long id, UUID neuerEigentuemer,
                                                      long preis) {
        // ERST die Ablage - dort liegt der Riegel gegen den Doppelkauf. Scheitert sie,
        // bleibt die Karte unveraendert und der Verkauf gilt als nicht geschehen.
        Grundstueck neu = wahrheit.eigentuemerSetzen(id, neuerEigentuemer, preis);
        Grundstueck alt = nachNummer.get(id);
        if (alt != null) {
            vergiss(alt);
        }
        // DIE ZEILE DER WAHRHEIT gemerkt und keine nachgebaute: Mitbauer geleert und
        // erworbenAm neu gesetzt hat schon die Ablage darunter. Wer sie hier ein
        // zweites Mal zusammensetzt, bekommt einen anderen Zeitpunkt als jeder, der
        // nach einem Neustart aus der Datenbank liest.
        merke(neu);
        return neu;
    }

    @Override
    public List<Grundstueck> angebote(String welt) {
        return wahrheit.angebote(welt);
    }

    @Override
    public synchronized void entfernen(long id) {
        wahrheit.entfernen(id);
        Grundstueck alt = nachNummer.get(id);
        if (alt != null) {
            vergiss(alt);
        }
    }

    // --- Alles Weitere geht UNGEPUFFERT durch -----------------------------------
    //
    // Diese drei Fragen laufen nicht auf dem heissen Pfad: inStadt und vonSpieler
    // beantworten eine Liste fuer einen Chatbefehl, alle() fuellt den Speicher selbst.
    // Sie aus der Karte zu bedienen hiesse, einen zweiten Index zu fuehren - und zwei
    // Indizes ueber dieselbe Menge laufen auseinander, sobald einer eine Aenderung
    // verpasst. Dass die Karte fuer den einen Pfad gebaut ist, der sie braucht, ist der
    // ganze Entwurf.

    @Override
    public List<Grundstueck> inStadt(long stadtId) {
        return wahrheit.inStadt(stadtId);
    }

    @Override
    public List<Grundstueck> alle(String welt) {
        return wahrheit.alle(welt);
    }

    @Override
    public List<Grundstueck> vonSpieler(String welt, UUID spieler) {
        return wahrheit.vonSpieler(welt, spieler);
    }

    // ------------------------------------------------------------------- innen

    /**
     * Ob diese Karte fuer diese Welt zustaendig ist.
     *
     * <p>Wirft, solange GAR NICHT gefuellt wurde: Eine leere Karte und eine ungefuellte
     * sehen gleich aus, bedeuten aber das Gegenteil - "es gibt keine Grundstuecke"
     * gegen "ich weiss es nicht". Ein stilles Nein hiesse auf dem heissen Pfad, dass
     * jeder auf jedem verkauften Grundstueck bauen darf. Lieber ein lauter Fehler beim
     * Start.
     */
    private boolean deckt(String welt) {
        if (!gefuellt) {
            throw new IllegalStateException("Der Zwischenspeicher der Grundstuecke ist"
                    + " nicht gefuellt. Eine leere Karte hiesse \"es gibt keine\" - und"
                    + " dann duerfte jeder auf jedem verkauften Grundstueck bauen."
                    + " fuellen(welt) gehoert in den Start.");
        }
        return gedeckteWelt != null && gedeckteWelt.equals(welt);
    }

    /** Traegt ein Grundstueck in JEDE seiner Zellen ein. */
    private void merke(Grundstueck eines) {
        nachNummer.put(eines.id(), eines);
        Zellquader flaeche = eines.flaeche();
        for (int x = flaeche.vonX(); x <= flaeche.bisX(); x++) {
            for (int z = flaeche.vonZ(); z <= flaeche.bisZ(); z++) {
                nachZelle.put(schluessel(eines.welt(), x, z), eines);
            }
        }
    }

    /** Nimmt es aus jeder seiner Zellen - das Gegenstueck zu {@link #merke}. */
    private void vergiss(Grundstueck eines) {
        nachNummer.remove(eines.id());
        Zellquader flaeche = eines.flaeche();
        for (int x = flaeche.vonX(); x <= flaeche.bisX(); x++) {
            for (int z = flaeche.vonZ(); z <= flaeche.bisZ(); z++) {
                String wo = schluessel(eines.welt(), x, z);
                // NUR wenn dort wirklich DIESES steht: Haette ein anderes die Zelle
                // inzwischen uebernommen, naehme ein blindes remove es mit heraus.
                Grundstueck dort = nachZelle.get(wo);
                if (dort != null && dort.id() == eines.id()) {
                    nachZelle.remove(wo);
                }
            }
        }
    }

    private static String schluessel(String welt, int zelleX, int zelleZ) {
        return welt + "/" + zelleX + "/" + zelleZ;
    }

    /** Die Grundstuecke, die die Karte kennt - fuer Proben. */
    synchronized List<Grundstueck> gemerkte() {
        return new ArrayList<>(nachNummer.values());
    }
}
