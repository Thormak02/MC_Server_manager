package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eine VOLLSTAENDIGE Kopie der Kistenshops im Arbeitsspeicher - kein Cache mit Verfall.
 *
 * <p><b>Das ist der Unterschied zu {@link ZwischenspeicherLager}, und er ist die Antwort
 * auf einen belegten Fehler</b>: QuickShop #2165 - "Hoppers placed BEFORE shop creation
 * could steal items from the shop chest. The shop cache was not invalidated when a new
 * shop was added, causing cached null queries to persist for up to THREE MINUTES." Ein
 * Zwischenspeicher mit Verfall hat genau dieses Loch: Zwischen dem Anlegen eines Shops
 * und dem Ablauf des Eintrags "hier ist kein Shop" zieht ein Trichter weiter ab.
 *
 * <p>Darum gibt es hier <b>keinen Verfall</b>. Diese Karte haelt ALLE Shops, wird beim
 * Start einmal gefuellt und bei jeder Aenderung mitgefuehrt. Eine Abfrage trifft nie die
 * Datenbank, und es gibt keinen Zeitpunkt, an dem sie eine veraltete Antwort gibt.
 *
 * <p><b>Warum das tragbar ist:</b> Shops sind wenige. Eine Markthalle hat achtzig Staende,
 * dazu die Shops auf Parzellen - realistisch bleibt es unter zehntausend Zeilen mit je
 * einigen hundert Zeichen. Parzellen waren anders: Die Marktstadt hat 1.764 Felder, und
 * jedes Blockereignis fragt nach einer anderen Stelle, also kann dort nur ein Cache mit
 * Verfall helfen.
 *
 * <p><b>Die Reihenfolge der zwei Schreibzuege ist Pflicht:</b> zuerst die Datenbank, dann
 * die Karte. Scheitert die Datenbank, bleibt die Karte unveraendert und der Shop gilt als
 * nicht angelegt - das ist die sichere Richtung. Umgekehrt stuende ein Shop in der Karte,
 * den nach dem naechsten Neustart niemand mehr findet, und sein Schild handelte ins
 * Leere.
 *
 * <p><b>Die Karte wird nie halb gefuellt.</b> Scheitert {@link #fuellen}, bleibt sie
 * leer - und eine leere Karte heisst "es gibt keine Shops", nicht "ich weiss es nicht".
 * Darum muss der Aufrufer beim Start den Fehlschlag behandeln: Ein Server, der mit leerer
 * Karte weiterlaeuft, laesst jeden Trichter in jede Shopkiste greifen.
 */
public final class ZwischenspeicherShops implements Shopablage {

    private final Shopablage wahrheit;

    /** Alle Shops, nach Schildstelle. Ein {@link TreeMap} haelt die Reihenfolge. */
    private final Map<String, Kistenshop> nachSchild = new TreeMap<>();

    /**
     * Welche Shops an welcher KISTE haengen - der heisse Pfad.
     *
     * <p>Ein zweiter Index und damit ein zweiter Ort fuer dieselbe Menge. Das ist hier
     * ausdruecklich in Kauf genommen, und der Grund ist die Haeufigkeit: Die Frage "ist
     * das eine Shopkiste" wird bei jedem Trichtertakt gestellt - viele Male je Sekunde.
     * Ein Durchlauf ueber alle Shops waere dort zu teuer.
     *
     * <p>Damit die zwei Karten nicht auseinanderlaufen, gibt es nur EINE Stelle, die
     * beide anfasst ({@link #merke} und {@link #vergiss}), und jede Aenderung geht
     * hindurch.
     */
    private final Map<String, List<Kistenshop>> nachKiste = new ConcurrentHashMap<>();

    /** Ob {@link #fuellen} schon gelaufen ist - eine leere Karte ist sonst eine Luege. */
    private boolean gefuellt;

    public ZwischenspeicherShops(Shopablage wahrheit) {
        if (wahrheit == null) {
            throw new IllegalArgumentException("Ohne Ablage dahinter ist diese Karte eine"
                    + " Behauptung");
        }
        this.wahrheit = wahrheit;
    }

    /**
     * Liest ALLE Shops aus der Ablage - einmal beim Start.
     *
     * <p>Wirft weiter, was die Ablage wirft. Der Aufrufer MUSS das behandeln: Ein Server,
     * der mit leerer Karte weiterlaeuft, haelt jede Shopkiste fuer eine gewoehnliche
     * Kiste - und dann greift jeder Trichter hinein.
     *
     * @return wie viele Shops gelesen wurden
     */
    public synchronized int fuellen() {
        List<Kistenshop> alle = wahrheit.alle();
        nachSchild.clear();
        nachKiste.clear();
        for (Kistenshop shop : alle) {
            merke(shop);
        }
        gefuellt = true;
        return nachSchild.size();
    }

    /** Ob die Karte gefuellt ist - vor dem ersten {@link #fuellen} ist sie keine Antwort. */
    public synchronized boolean istGefuellt() {
        return gefuellt;
    }

    @Override
    public synchronized Kistenshop anDemSchild(String welt, int x, int y, int z) {
        pruefeGefuellt();
        return nachSchild.get(Kistenshop.schluesselVon(welt, x, y, z));
    }

    @Override
    public synchronized List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
        pruefeGefuellt();
        List<Kistenshop> dort = nachKiste.get(Kistenshop.schluesselVon(welt, x, y, z));
        // Eine eigene Liste: Gaebe diese Karte ihre innere heraus, koennte ein Aufrufer
        // einen Shop hinzufuegen, der in keiner Zeile steht.
        if (dort == null) {
            return List.of();
        }
        List<Kistenshop> sortiert = new ArrayList<>(dort);
        sortiert.sort(Kistenshop.NACH_STELLE);
        return List.copyOf(sortiert);
    }

    @Override
    public synchronized List<Kistenshop> vonSpieler(UUID besitzer) {
        pruefeGefuellt();
        if (besitzer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        List<Kistenshop> seine = new ArrayList<>();
        for (Kistenshop shop : nachSchild.values()) {
            if (shop.gehoert(besitzer)) {
                seine.add(shop);
            }
        }
        seine.sort(Kistenshop.NACH_STELLE);
        return seine;
    }

    @Override
    public synchronized List<Kistenshop> alle() {
        pruefeGefuellt();
        // Sortiert und nicht einfach die values(): Die Karte ordnet nach dem
        // TEXT-Schluessel, und dort steht 100 vor 9. Die Ordnung einer Shopliste steht
        // an EINER Stelle, in Kistenshop.NACH_STELLE.
        List<Kistenshop> alle = new ArrayList<>(nachSchild.values());
        alle.sort(Kistenshop.NACH_STELLE);
        return alle;
    }

    @Override
    public synchronized void schreibe(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Shop");
        }
        pruefeGefuellt();
        // ZUERST die Datenbank. Scheitert sie, wirft sie - und die Karte bleibt
        // unveraendert, der Shop gilt als nicht angelegt. Siehe den Klassenkopf.
        wahrheit.schreibe(shop);
        // Ein Shop kann an eine ANDERE Kiste gezogen sein (der Betreiber hat die Zeile
        // von Hand geaendert). Dann muss der alte Eintrag aus nachKiste weg, sonst
        // haengt der Shop an zwei Kisten - und an der alten greift kein Trichter mehr,
        // obwohl dort kein Shop ist.
        Kistenshop vorher = nachSchild.get(shop.schluessel());
        if (vorher != null) {
            vergiss(vorher);
        }
        merke(shop);
    }

    @Override
    public synchronized boolean loesche(String welt, int x, int y, int z) {
        pruefeGefuellt();
        String schluessel = Kistenshop.schluesselVon(welt, x, y, z);
        // Auch hier zuerst die Datenbank: Bleibt die Zeile dort stehen, waehrend die
        // Karte sie vergisst, steht der Shop nach dem naechsten Neustart wieder da -
        // mit einem Schild, das der Spieler laengst abgebaut hat.
        boolean war = wahrheit.loesche(welt, x, y, z);
        Kistenshop vorhanden = nachSchild.remove(schluessel);
        if (vorhanden != null) {
            vergiss(vorhanden);
        }
        return war;
    }

    /** Wie viele Shops die Karte haelt - fuer die Startzeile und die Proben. */
    public synchronized int anzahl() {
        return nachSchild.size();
    }

    /**
     * Nimmt einen Shop in BEIDE Karten auf - die eine Stelle, die sie anfasst.
     *
     * <p>Zwei Karten ueber dieselbe Menge sind eine Fehlerquelle, und die einzige
     * Gegenwehr ist, dass nur hier und in {@link #vergiss} geschrieben wird.
     */
    private void merke(Kistenshop shop) {
        nachSchild.put(shop.schluessel(), shop);
        nachKiste.computeIfAbsent(kistenschluessel(shop), wo -> new ArrayList<>())
                .add(shop);
    }

    /** Nimmt einen Shop aus BEIDEN Karten - das Gegenstueck zu {@link #merke}. */
    private void vergiss(Kistenshop shop) {
        nachSchild.remove(shop.schluessel());
        String wo = kistenschluessel(shop);
        List<Kistenshop> dort = nachKiste.get(wo);
        if (dort != null) {
            // Nach der SCHILDSTELLE entfernt und nicht ueber equals: Zwei Shops an einer
            // Kiste unterscheiden sich in allem ausser der Kiste, und ein geaenderter
            // Shop ist demselben Schild zugeordnet, aber kein gleiches Stueck mehr.
            dort.removeIf(einer -> einer.schluessel().equals(shop.schluessel()));
            if (dort.isEmpty()) {
                nachKiste.remove(wo);
            }
        }
    }

    private static String kistenschluessel(Kistenshop shop) {
        return Kistenshop.schluesselVon(shop.schildWelt(), shop.kisteX(), shop.kisteY(),
                shop.kisteZ());
    }

    /**
     * Eine ungefuellte Karte beantwortet nichts.
     *
     * <p><b>Ein Wurf und keine leere Antwort</b>, und das ist die tragende Entscheidung
     * dieser Klasse: "Es gibt hier keinen Shop" und "ich weiss es noch nicht" sind zwei
     * verschiedene Dinge, und nur das erste darf ein Trichter als Erlaubnis lesen. Ein
     * stilles Nein waere eine Markthalle, die bis zum ersten Schreibzug offen steht.
     */
    private void pruefeGefuellt() {
        if (!gefuellt) {
            throw new IllegalStateException("Die Shopkarte ist nicht gefuellt - fuellen()"
                    + " gehoert in den Start, BEVOR die erste Frage kommt. Eine leere"
                    + " Karte hiesse \"es gibt keine Shops\", und dann greift jeder"
                    + " Trichter in jede Shopkiste.");
        }
    }
}
