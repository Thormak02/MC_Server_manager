package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Die Kistenshops im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Derselbe Rueckfall, den {@link SpeicherKasse},
 * {@link SpeicherLager}, {@link SpeicherMiete} und {@link SpeicherAbholfach} schon haben:
 * Wer sie im Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu
 * entdecken.
 *
 * <p>Eine {@link TreeMap}: {@link #alle} und {@link #vonSpieler} muessen zweimal
 * hintereinander dieselbe Reihenfolge liefern, und das ist das vierte Versprechen aus
 * {@link Shopablage}.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherMiete}: Die Zuhoerer des
 * Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber ausdruecklich
 * nebenlaeufig.
 *
 * <p><b>{@link #anDerKiste} laeuft hier ueber ALLE Shops.</b> Das ist fuer eine Karte im
 * Arbeitsspeicher richtig und fuer die Datenbank falsch: Dort ist es der heisse Pfad
 * (Trichter, Explosion, Kolben), und dort gehoert ein Zwischenspeicher davor. Ein
 * zweiter Index hier waere ein zweiter Ort fuer dieselbe Menge - und der liefe
 * auseinander, sobald eine Zeile geloescht wird.
 */
public final class SpeicherShops implements Shopablage {

    private final Map<String, Kistenshop> shops = new TreeMap<>();

    @Override
    public synchronized Kistenshop anDemSchild(String welt, int x, int y, int z) {
        return shops.get(Kistenshop.schluesselVon(welt, x, y, z));
    }

    @Override
    public synchronized List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keine Stelle");
        }
        String gesucht = welt.trim();
        List<Kistenshop> dort = new ArrayList<>();
        for (Kistenshop shop : shops.values()) {
            if (shop.schildWelt().equals(gesucht) && shop.kisteX() == x
                    && shop.kisteY() == y && shop.kisteZ() == z) {
                dort.add(shop);
            }
        }
        dort.sort(Kistenshop.NACH_STELLE);
        return dort;
    }

    @Override
    public synchronized List<Kistenshop> vonSpieler(UUID besitzer) {
        if (besitzer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        List<Kistenshop> seine = new ArrayList<>();
        for (Kistenshop shop : shops.values()) {
            if (shop.gehoert(besitzer)) {
                seine.add(shop);
            }
        }
        seine.sort(Kistenshop.NACH_STELLE);
        return seine;
    }

    @Override
    public synchronized List<Kistenshop> alle() {
        // Sortiert und nicht einfach die values(): Die Karte ordnet nach dem
        // TEXT-Schluessel, und dort steht 100 vor 9. Die Ordnung einer Shopliste steht
        // an EINER Stelle, in Kistenshop.NACH_STELLE.
        List<Kistenshop> alle = new ArrayList<>(shops.values());
        alle.sort(Kistenshop.NACH_STELLE);
        return alle;
    }

    @Override
    public synchronized void schreibe(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Shop");
        }
        shops.put(shop.schluessel(), shop);
    }

    @Override
    public synchronized boolean loesche(String welt, int x, int y, int z) {
        return shops.remove(Kistenshop.schluesselVon(welt, x, y, z)) != null;
    }
}
