package de.freimarken.paper;

import de.freimarken.kern.Angelwerte;
import de.freimarken.kern.Fangregel;
import de.freimarken.kern.Wirtschaftsstand;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Das eigene Angelgut von Hafenwerder - die Hand, die den Fang TAUSCHT (Plan 4.1).
 *
 * <p><b>Die erste Stelle dieses Projekts, die in eine Beute eingreift.</b> Bis zum
 * 18.11.2026 wurde {@code PlayerFishEvent} nur gelesen, um den Fang zu bezahlen; was im
 * Wasser hing, war vollstaendig Vanilla.
 *
 * <p><b>Die Reihenfolge ist tragend: dieser Tausch steht VOR der Zahlung.</b>
 * {@link Verdienstwache#beimFischen} haengt auf {@link EventPriority#MONITOR} und liest
 * dort das Material des gefangenen Stapels. Diese Wache haengt auf
 * {@link EventPriority#HIGH} - also davor - und darum bezahlt die Lohntafel den
 * getauschten Fang und nicht den, den Vanilla gewuerfelt hat. Haengte sie danach, waere
 * die Tabelle fuer den Verdienst unsichtbar, und die Zeilen in {@code verdienst.loehne}
 * zu den neuen Faengen waeren Zeilen, die nie greifen.
 *
 * <p><b>Dieselben drei Tore wie die Zahlung</b>, und aus denselben Gruenden: der Zustand
 * {@code CAUGHT_FISH} (die anderen Zustaende haben keinen Fang), das OFFENE WASSER (das
 * laesst das Ein-Block-Loch heraus, an dem ein Klickautomat Tag und Nacht angelt) und
 * {@code instanceof Item} (der gehakte Tintenfisch ist kein Fang). Vier gleiche Tore an
 * zwei Stellen waeren eine Gelegenheit, dass eine Haelfte eines Tages anders urteilt;
 * dass sie hier zweimal stehen, ist der Preis dafuer, dass beide Wachen fuer sich
 * verstaendlich bleiben - und eine Tafel haelt die Reihenfolge fest.
 *
 * <p>Der Zufall liegt HIER und nicht im Kern: {@link Fangregel} bekommt beide Wuerfe als
 * Zahl herein, damit sie ohne laufenden Server und ohne Zufall pruefbar ist.
 */
final class Angelwache implements Listener {

    /** So selten wird ein kaputter Fangname ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 60_000L;

    private final String weltschluessel;
    private final Wirtschaftsstand wirtschaft;
    private final Random wuerfel;

    /**
     * Wie aus einem Gegenstand EIN Stueck wird.
     *
     * <p><b>Eine NAHT und kein {@code new ItemStack} im Zweig</b>, und das ist gemessen:
     * Jener Aufruf schlaegt in der Gegenstandsregistrierung nach und WIRFT ohne laufenden
     * Server. Mit ihm mitten im Zweig liesse sich der Tausch - die ganze Sache dieser
     * Klasse - von keiner Probe durchlaufen. Dieselbe Antwort wie beim Zeitgeber des
     * Verdienstbuchs: Das Stueck Bukkit kommt herein, der Rest ist pruefbar.
     */
    private final Function<Material, ItemStack> stapelbau;

    private final Logger protokoll;

    /** Wann zuletzt ueber einen unbrauchbaren Fang geklagt wurde - 0 heisst: noch nie. */
    private long letzteKlage;

    /** Die Wache, wie der Server sie baut: ein Stapel ist ein Stueck des Gegenstands. */
    static Angelwache ueberBukkit(String weltschluessel, Wirtschaftsstand wirtschaft,
                                  Random wuerfel, Logger protokoll) {
        return new Angelwache(weltschluessel, wirtschaft, wuerfel,
                art -> new ItemStack(art, 1), protokoll);
    }

    /**
     * @param weltschluessel die Inselwelt, aus {@code inseln.welt} und schon umgewandelt.
     *                       LEER heisst: Diese Wache tut nichts - dieselbe Form wie bei
     *                       den Befehlen, damit ein fehlender Eintrag in der
     *                       Einstellungsdatei keinen Start kostet
     * @param wuerfel        der Zufall; herein gegeben, damit eine Probe ihn stellen kann
     */
    Angelwache(String weltschluessel, Wirtschaftsstand wirtschaft, Random wuerfel,
               Function<Material, ItemStack> stapelbau, Logger protokoll) {
        if (wirtschaft == null) {
            throw new IllegalArgumentException("Ohne Wirtschaftsstand gibt es keine"
                    + " Angeltabelle - und eine kopierte waere beim Neuladen taub");
        }
        if (wuerfel == null) {
            throw new IllegalArgumentException("Ohne Wuerfel gibt es keinen Fang");
        }
        if (stapelbau == null) {
            throw new IllegalArgumentException("Ohne Stapelbau gibt es nichts zu"
                    + " tauschen");
        }
        this.weltschluessel = weltschluessel == null ? "" : weltschluessel;
        this.wirtschaft = wirtschaft;
        this.wuerfel = wuerfel;
        this.stapelbau = stapelbau;
        this.protokoll = protokoll;
    }

    /**
     * Tauscht den Fang, wenn die Tabelle greift.
     *
     * <p>{@code ignoreCancelled}: Ein abgebrochener Angelzug hat keinen Fang, den man
     * tauschen koennte - und ein Tausch daran waere ein Gegenstand, der nie beim Spieler
     * ankommt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimFischen(PlayerFishEvent ereignis) {
        if (weltschluessel.isEmpty()) {
            return;
        }
        if (ereignis.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        // Die WELT vor allem anderen: Diese Wache haengt am Server und nicht an einer
        // Welt, und ausserhalb von Hafenwerder soll sie nach einem
        // Zeichenkettenvergleich fertig sein.
        if (ereignis.getPlayer().getWorld() == null
                || !weltschluessel.equals(
                        ereignis.getPlayer().getWorld().key().asString())) {
            return;
        }
        if (!ereignis.getHook().isInOpenWater()) {
            return;
        }
        if (!(ereignis.getCaught() instanceof Item gegenstand)) {
            return;
        }
        Angelwerte werte = wirtschaft.werte().angeln();
        if (!werte.greift()) {
            return;
        }
        Optional<String> gewaehlt = Fangregel.fang(werte, wuerfel.nextInt(100),
                wuerfel.nextLong(werte.gewichtsumme()));
        if (gewaehlt.isEmpty()) {
            return;
        }
        Material art = Material.getMaterial(gewaehlt.get().toUpperCase(java.util.Locale.ROOT));
        if (art == null) {
            // Der Leser hat jeden Namen geprueft, also kann das nur eine Tabelle sein,
            // die auf einem anderen Weg hereinkam. Dann bleibt es bei Vanilla - und der
            // Betreiber liest es, aber nicht bei jedem Fang.
            klage(gewaehlt.get());
            return;
        }
        // EIN Stueck, wie Vanilla es gibt: Ein ganzer Stapel aus dem Wasser waere eine
        // Geldquelle, die niemand eingestellt hat - die Lohntafel zahlt je Fang, und
        // der Spieler bekaeme das Vielfache. Die Zahl steht in der Naht, siehe oben.
        gegenstand.setItemStack(stapelbau.apply(art));
    }

    /**
     * Meldet einen unbrauchbaren Fangnamen - hoechstens einmal je Minute.
     *
     * <p>Gedrosselt wie bei den Wachen: Ohne Drossel schreibt ein Angelautomat in
     * Sekunden ein Protokoll voll und verdeckt jede andere Meldung.
     */
    private void klage(String name) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Der Fang \"" + name + "\" aus angeln.faenge ist"
                + " kein Gegenstand, den dieser Server kennt - in Hafenwerder bleibt es"
                + " bei dem, was Vanilla gibt. Der Leser der Einstellungsdatei haette"
                + " das beklagt; kommt diese Zeile trotzdem, ist die Tabelle auf einem"
                + " anderen Weg hereingekommen.");
    }
}
