package de.freimarken.paper;

import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Erzregel;
import de.freimarken.kern.Erzwerte;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wolkenablage;
import de.freimarken.kern.Wolkenbuch;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * Der Kobbelsteingenerator von Wolkenwerder - die Hand, die das ERZ hineinlegt.
 *
 * <p><b>Dies ist der Grund, warum es diese Welt gibt.</b> In einer Welt aus Leere gibt es
 * kein Erz: keine Hoehle, kein Gestein, keine Ader. Was ein Spieler an Metall bekommt,
 * bekommt er aus Lava und Wasser - und damit ist diese Klasse nicht eine Bequemlichkeit,
 * sondern die Werkbank des ganzen Spiels.
 *
 * <p><b>Dieselbe Bauart wie {@link Angelwache}</b>, und das ist Absicht: Jene tauscht den
 * Fang, diese den gebildeten Block. Beide haengen auf {@link EventPriority#HIGH}, beide
 * halten den Zufall HIER und geben dem Kern nur Zahlen, und beide klagen gedrosselt ueber
 * einen Namen, den der Server nicht kennt.
 *
 * <p><b>Die Blockart wird VOR allem anderen geprueft</b>, und das ist kein Geschmack:
 * Dasselbe Bukkit-Ereignis bildet auch Schnee, Eis und Beton. In einem Biom wie
 * {@code snowy_plains} faellt es waehrend eines Schneesturms fuer jeden freien Block -
 * eine Abfrage nach der Insel dahinter waere dort die teuerste Zeile des Servers.
 *
 * <p><b>Getauscht wird nur AUF EINER INSEL.</b> Ein Generator in der Leere zwischen den
 * Feldern gibt reinen Kobbelstein: Dort gehoert niemandem etwas, und es gibt keine Stufe,
 * nach der gerechnet werden koennte. Das ist auch der Riegel gegen den Automaten, der im
 * Niemandsland zwischen zwei Feldern stehen bleibt.
 *
 * <p><b>Die Marke ist eine Geldentscheidung</b> ({@code wolken.erze.marken}): Ohne sie
 * zahlt {@code verdienst.loehne} fuer jedes Erz aus dem Generator, und ein Automat laeuft
 * Tag und Nacht. Der Spieler behaelt das Erz - er bekommt nur kein Mark fuer das Abbauen.
 */
final class Erzwache implements Listener {

    /** So selten wird ein kaputter Erzname ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 60_000L;

    private final String weltschluessel;
    private final Wirtschaftsstand wirtschaft;
    private final Wolkenbuch buch;
    private final Wolkenablage wolken;
    private final Blockmarken marken;
    private final Random wuerfel;
    private final Logger protokoll;

    /** Wann zuletzt ueber einen unbrauchbaren Erznamen geklagt wurde - 0: noch nie. */
    private long letzteKlage;

    /**
     * @param weltschluessel Wolkenwerder, aus {@code wolken.welt} und schon umgewandelt.
     *                       LEER heisst: Diese Wache tut nichts - dieselbe Form wie bei
     *                       den Befehlen, damit ein fehlender Eintrag in der
     *                       Einstellungsdatei keinen Start kostet
     * @param wolken         die Inselstaende, und zwar der ZWISCHENSPEICHER: Diese Wache
     *                       fragt bei jedem gebildeten Block nach der Stufe, und zwei
     *                       Datenbankabfragen je Block waeren ein Server, der stockt
     * @param wuerfel        der Zufall; herein gegeben, damit eine Probe ihn stellen kann
     */
    Erzwache(String weltschluessel, Wirtschaftsstand wirtschaft, Wolkenbuch buch,
             Wolkenablage wolken, Blockmarken marken, Random wuerfel, Logger protokoll) {
        if (wirtschaft == null) {
            throw new IllegalArgumentException("Ohne Wirtschaftsstand gibt es keine"
                    + " Erztabelle - und eine kopierte waere beim Neuladen taub");
        }
        if (buch == null || wolken == null) {
            throw new IllegalArgumentException("Ohne Buch und Inselstaende gibt es keine"
                    + " Stufe, nach der sich die Ausbeute richtet");
        }
        if (marken == null) {
            throw new IllegalArgumentException("Ohne Blockmarken gibt es nichts zu"
                    + " markieren - und wolken.erze.marken waere eine Einstellung ohne"
                    + " Wirkung");
        }
        if (wuerfel == null) {
            throw new IllegalArgumentException("Ohne Wuerfel gibt es kein Erz");
        }
        this.weltschluessel = weltschluessel == null ? "" : weltschluessel;
        this.wirtschaft = wirtschaft;
        this.buch = buch;
        this.wolken = wolken;
        this.marken = marken;
        this.wuerfel = wuerfel;
        this.protokoll = protokoll;
    }

    /**
     * Tauscht den gebildeten Block, wenn die Tabelle greift.
     *
     * <p>{@code ignoreCancelled}: Hat ein anderes Plugin die Bildung abgebrochen, entsteht
     * kein Block - und ein Erz daran waere eines, das nie erscheint.
     *
     * <p>{@link EventPriority#HIGH} und nicht MONITOR, aus demselben Grund wie bei der
     * Angelwache: Hier wird GEAENDERT, und wer danach zusieht, soll das Ergebnis sehen.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimBilden(BlockFormEvent ereignis) {
        if (weltschluessel.isEmpty()) {
            return;
        }
        BlockState neu = ereignis.getNewState();
        // Die ART zuerst, siehe Klassenkopf: Schnee und Eis kommen durch dasselbe Tor.
        if (!Erzwerte.istAusgang(neu.getType().name())) {
            return;
        }
        Block block = ereignis.getBlock();
        if (block.getWorld() == null
                || !weltschluessel.equals(block.getWorld().key().asString())) {
            return;
        }
        Erzwerte werte = wirtschaft.werte().wolken().erze();
        if (!werte.greift()) {
            return;
        }
        Optional<Parzelle> insel = buch.inselAn(weltschluessel, block.getX(),
                block.getZ());
        if (insel.isEmpty()) {
            return;
        }
        int stufe = wolken.stand(insel.get().id()).stufe();
        if (werte.prozentFuer(stufe) <= 0) {
            return;
        }
        long summe = werte.gewichtsumme(stufe);
        if (summe <= 0L) {
            // Keine Zeile erreicht diese Stufe. Gefragt wird VOR dem Wurf, weil
            // nextLong(0) wirft - und das waere ein Wurf im Hauptfaden bei jedem Block.
            return;
        }
        Optional<String> gewaehlt = Erzregel.erz(werte, stufe, wuerfel.nextInt(100),
                wuerfel.nextLong(summe));
        if (gewaehlt.isEmpty()) {
            return;
        }
        Material art = Material.getMaterial(gewaehlt.get().toUpperCase(Locale.ROOT));
        if (art == null) {
            // Der Leser hat jeden Namen geprueft, also kann das nur eine Tabelle sein,
            // die auf einem anderen Weg hereinkam. Dann bleibt es bei Kobbelstein - und
            // der Betreiber liest es, aber nicht bei jedem Block.
            klage(gewaehlt.get());
            return;
        }
        neu.setType(art);
        if (werte.marken()) {
            marken.markiere(new Blockort(weltschluessel, block.getX(), block.getY(),
                    block.getZ()));
        }
    }

    /**
     * Meldet einen unbrauchbaren Erznamen - hoechstens einmal je Minute.
     *
     * <p>Gedrosselt wie bei der Angelwache: Ohne Drossel schreibt ein Generator in
     * Sekunden ein Protokoll voll und verdeckt jede andere Meldung.
     */
    private void klage(String name) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Das Erz \"" + name + "\" aus wolken.erze.tabelle"
                + " ist kein Gegenstand, den dieser Server kennt - in Wolkenwerder bleibt"
                + " es bei Kobbelstein. Der Leser der Einstellungsdatei haette das"
                + " beklagt; kommt diese Zeile trotzdem, ist die Tabelle auf einem"
                + " anderen Weg hereingekommen.");
    }
}
