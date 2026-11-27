package de.freimarken.paper;

import de.freimarken.kern.Einheit;
import de.freimarken.kern.Weltregel;
import de.freimarken.kern.Weltregelwerte;
import de.freimarken.kern.Zustaendigkeit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

/**
 * Was eine Welt an Zerstoerung OHNE Spieler zulaesst.
 *
 * <p><b>Die Luecke, die der Klassenkopf von {@link Schutzwache} seit Monaten
 * ankuendigt.</b> Dort steht: "Noch NICHT abgedeckt, bewusst benannt statt
 * stillschweigend offen gelassen: TNT und andere Explosionen, Feuerausbreitung, Kolben,
 * die ueber eine Grenze schieben, Wasser- und Lavafluss von aussen, Enderman und Ziegen.
 * Das sind Weltregeln, keine Spielerhandlungen, und sie gehoeren zu einem eigenen
 * Schritt." Das ist dieser Schritt.
 *
 * <p><b>Warum eine eigene Wache und nicht ein sechzehnter Zugang zu
 * {@code darfHandeln}:</b> Jene Regel fragt "darf DIESER SPIELER hier handeln" und
 * braucht dafuer einen {@code Player}. Eine TNT-Ladung hat keinen - der Zuender ist
 * laengst weg oder war ein Creeper -, ein Lavastrom hat keinen, und ein Enderman schon
 * gar nicht. Die Frage hier ist eine andere: "gehoert dieser Block jemandem".
 *
 * <p><b>Sie kommt aber aus DERSELBEN Quelle</b>, und das ist der Teil, auf den es
 * ankommt: {@link Zustaendigkeit}, und dahinter dasselbe Buch, das auch
 * {@code darfBauen} beantwortet. Eine zweite Vorstellung davon, wem ein Block gehoert,
 * waere genau die Luecke, vor der der erste Absatz jener Doku warnt - sie liefen eines
 * Tages auseinander, und dann waere eine der beiden falsch.
 *
 * <p><b>JEDE BAUWELT, nicht nur die Marktstadt</b> [27.11.2026, Betreiber: "soll
 * ueberall wo gebaut wird wirken"]: Die Wache bekommt eine Karte Weltschluessel ->
 * {@link Zustaendigkeit}. Marktstadt (Parzellen), Freistaedte (Stadtfelder und
 * Grundstuecke), Hafenwerder (Inseln) und Wolkenwerder (Wolken) haben Besitzeinheiten;
 * Altfurt ist GANZ GEBAUT und hat keine - dort gibt es keine Grenze, und darum
 * verbietet die Grenzregel dort schlicht. Eine Welt, die in der Karte fehlt, ist keine
 * Bauwelt: dort greifen nur die weltweiten Regeln.
 *
 * <p><b>NEUN ZUHOERER, ZWEI WIRKUNGEN</b> - die Teilung steht in
 * {@link Weltregel#giltWeltweit} und wird hier nur ANGEWENDET:
 *
 * <ul>
 *   <li><b>Weltweit</b> (Explosion, Feuer, Wesen): Greift die Regel in dieser Welt, wird
 *       abgebrochen - ohne eine einzige Abfrage an die Zustaendigkeit. Das ist nicht
 *       nur einfacher, es ist auch richtig: Wer keine Krater will, will sie auch
 *       nicht auf einer freien Parzelle.</li>
 *   <li><b>Ueber Grenzen</b> (Kolben, Fluessigkeit): Abgebrochen wird nur der
 *       UEBERGRIFF - wenn das Ziel jemandem gehoert und die Quelle nicht demselben.</li>
 * </ul>
 *
 * <p><b>Die Werte kommen als HALTER und nicht als Abbild</b>, dieselbe Bauart wie beim
 * {@code Wirtschaftsstand}: Damit wirkt {@code /freimarken neuladen} auf diese Regeln.
 * Ein Abbild im Feld waere ein Schutz, der sich erst beim naechsten Serverstart aendert -
 * und genau dann braucht man ihn, wenn gerade etwas passiert.
 *
 * <p><b>Alle Zuhoerer laufen auf HOCH und mit {@code ignoreCancelled}</b>, nicht auf
 * MONITOR: Diese Wache ENTSCHEIDET, sie beobachtet nicht. Auf MONITOR waere die
 * Entscheidung schon gefallen - das ist die Ebene, auf der das Blockbuch mitschreibt
 * ({@link Blockwache}), und beide duerfen sich nicht vertauschen.
 */
public final class Weltwache implements Listener {

    /** Wie lange zwischen zwei Klagen ueber die Ablage liegen muss. */
    private static final long KLAGEPAUSE_MS = 60_000L;

    private final Supplier<Weltregelwerte> werte;
    private final Map<String, Zustaendigkeit> bauwelten;
    private final Logger protokoll;

    private long letzteKlage;

    /**
     * @param werte     der Halter, nicht das Abbild - siehe den Kopf
     * @param bauwelten je BAUWELT eine Zustaendigkeit, nach Weltschluessel. Jede
     *                  kommt aus demselben Buch, das auch {@code darfBauen}
     *                  beantwortet. Eine Welt, die hier FEHLT, ist keine Bauwelt:
     *                  dort greifen die weltweiten Regeln, die Grenzregeln nicht.
     *                  Die Karte darf leer sein - ein Server ohne Freistaedte und
     *                  Werder hat nur die Marktstadt.
     */
    public Weltwache(Supplier<Weltregelwerte> werte,
                     Map<String, Zustaendigkeit> bauwelten, Logger protokoll) {
        if (werte == null || bauwelten == null || protokoll == null) {
            throw new IllegalArgumentException("Werte, Bauwelten und Protokoll"
                    + " sind Pflicht");
        }
        for (Map.Entry<String, Zustaendigkeit> eintrag : bauwelten.entrySet()) {
            // DERSELBE WALL wie in Weltregelwerte: Ein WeltNAME statt eines
            // Schluessels traefe im Ereignis NIE, und die Welt waere stumm
            // ungeschuetzt - der teuerste Fall, weil nichts bricht.
            if (eintrag.getKey() == null || eintrag.getKey().indexOf(':') < 0) {
                throw new IllegalArgumentException("\"" + eintrag.getKey()
                        + "\" ist ein WeltNAME und kein Weltschluessel -"
                        + " erwartet wird die Form \"namensraum:name\", wie"
                        + " World.key().asString() sie liefert");
            }
            if (eintrag.getValue() == null) {
                throw new IllegalArgumentException("Die Bauwelt \""
                        + eintrag.getKey() + "\" hat keine Zustaendigkeit. Fuer"
                        + " eine Welt ohne Besitzeinheiten gibt es"
                        + " Zustaendigkeit.ganzGebauteWelt(); ein null waere eine"
                        + " Bauwelt, in der die Grenzregeln stumm nichts tun.");
            }
        }
        this.werte = werte;
        this.bauwelten = Map.copyOf(bauwelten);
        this.protokoll = protokoll;
    }

    // === Die drei weltweiten Regeln =========================================

    /**
     * TNT, Creeper, Feuerkugeln, Endkristalle - alles mit einem Wesen dahinter.
     *
     * <p><b>Die Blockliste wird GELEERT und das Ereignis nicht abgebrochen</b>, und das
     * ist eine Entscheidung mit Grund: Ein abgebrochenes {@code EntityExplodeEvent}
     * laesst in Paper je nach Quelle das TNT stehen oder erneut zuenden, und ein Creeper,
     * der nicht explodiert, bleibt am Spieler haengen und versucht es wieder. Mit einer
     * leeren Liste bleibt der Knall - das Wesen ist weg, der Schaden aber auch.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimSprengen(EntityExplodeEvent ereignis) {
        if (weltweitGreift(ereignis.getLocation(), Weltregel.EXPLOSION)) {
            ereignis.blockList().clear();
        }
    }

    /**
     * Betten und Respawn-Anker im Nether, und jede Explosion ohne Wesen.
     *
     * <p>Ein eigener Zuhoerer, weil Bukkit zwei Ereignisse hat - und der zweite ist
     * genau der, den man vergisst: Ein Bett im Nether gehoert keinem Wesen, und ohne
     * diesen Haken waere es der Weg an der Regel vorbei.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimBlocksprengen(BlockExplodeEvent ereignis) {
        if (weltweitGreift(ereignis.getBlock().getLocation(),
                Weltregel.EXPLOSION)) {
            ereignis.blockList().clear();
        }
    }

    /** Ein Block brennt ab. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimAbbrennen(BlockBurnEvent ereignis) {
        if (weltweitGreift(ereignis.getBlock().getLocation(),
                Weltregel.FEUER)) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Feuer breitet sich aus - und nicht nur Feuer.
     *
     * <p>{@code BlockSpreadEvent} traegt auch Pilze, Gras und Kuerbisranken. Gefiltert
     * wird NICHT: Wer {@code feuer} fuer eine Welt eintraegt, meint die Ausbreitung von
     * Zerstoerung, und eine Ranke, die sich durch ein gebautes Beet frisst, ist
     * dasselbe Problem in langsamer Form. Die Regel heisst darum in der Datei nach der
     * SACHE und nicht nach dem Ereignis - und dieser Satz steht hier, damit niemand
     * glaubt, es sei ein Versehen.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimAusbreiten(BlockSpreadEvent ereignis) {
        if (weltweitGreift(ereignis.getBlock().getLocation(),
                Weltregel.FEUER)) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Enderman, Ziege, Schaf, Kaninchen - jedes Wesen, das einen Block aendert.
     *
     * <p><b>Ausgenommen ist der fallende Block</b>, und das ist keine Feinheit:
     * {@code EntityChangeBlockEvent} traegt auch das LANDEN eines
     * {@code FallingBlock}, also Sand, Kies, Betonpulver, Amboss und Drachenei. Im
     * eigenen Quelltext steht es ausgeschrieben - {@link Shopwache} nennt den Fall
     * "Enderman, Ziege, Zombie, Sandfall".
     *
     * <p>Das ist kein Wesen, das etwas zerstoert, sondern ein Block in der Luft, den
     * ein SPIELER gesetzt hat. Ein Abbruch laesst ihn nie unten ankommen - und zwar
     * auf der eigenen Parzelle, weil diese Regel weltweit gilt. Damit waere in der
     * Marktstadt und in Altfurt kein Sandbau mehr moeglich, und das ist genau das
     * "harmloses Spiel unnoetig verboten", das der Klassenkopf ausschliesst.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimWesenblock(EntityChangeBlockEvent ereignis) {
        if (!wesenZaehlt(ereignis.getEntityType())) {
            return;
        }
        if (weltweitGreift(ereignis.getBlock().getLocation(), Weltregel.WESEN)) {
            ereignis.setCancelled(true);
        }
    }

    // === Die zwei Grenzregeln ===============================================

    /**
     * Ein Kolben schiebt - und darf dabei keine Besitzgrenze ueberschreiten.
     *
     * <p>Geprueft wird JEDER bewegte Block gegen den KOLBEN: Der Kolben steht, wo sein
     * Besitzer ihn gebaut hat, und alles, was er bewegt, muss demselben gehoeren. Ohne
     * diesen Vergleich waere die Regel entweder nutzlos (Kolben ganz erlaubt) oder zu
     * hart (Kolben ganz verboten) - und Redstone ist Spielinhalt.
     *
     * <p>Auch der ZIELORT zaehlt, nicht nur der Block selbst: Ein Block, der auf der
     * eigenen Parzelle steht und auf die fremde geschoben wird, ueberschreitet die
     * Grenze erst durch die Bewegung.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimKolbenSchieben(BlockPistonExtendEvent ereignis) {
        if (kolbenUeberschreitet(ereignis.getBlock(), ereignis.getBlocks(),
                ereignis.getDirection())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Ein Kolben zieht - dieselbe Regel, aber die ANDERE Richtung.
     *
     * <p><b>{@code getOppositeFace()} und nicht {@code getDirection()}</b>, und das
     * ist an der benutzten paper-api gemessen: {@code getRetractLocation()} ist
     * {@code getBlock().getRelative(getDirection(), 2)}. Die Richtung zeigt also
     * AUCH beim Einfahren vom Kolben weg - der gezogene Block liegt zwei Schritte
     * dort und wandert einen Schritt ZURUECK zum Kolben.
     *
     * <p>Mit {@code getDirection()} prueefte dieser Zweig einen Ort, den das
     * Ereignis nie beruehrt (einen Schritt weiter weg), und liesse den echten
     * Zielort ungefragt. Der Schaden waere ein unnoetiges Verbot: Ein Klebekolben
     * mitten auf der eigenen Parzelle haette stillgestanden, weil drei Bloecke
     * weiter fremder Grund beginnt.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimKolbenZiehen(BlockPistonRetractEvent ereignis) {
        if (kolbenUeberschreitet(ereignis.getBlock(), ereignis.getBlocks(),
                ereignis.getDirection().getOppositeFace())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Wasser oder Lava fliesst - und darf nicht in fremden Besitz.
     *
     * <p>NUR HINEIN: Vom eigenen Grund auf den Weg darf es laufen, sonst funktioniert
     * keine Farm am Rand einer Parzelle. Abgebrochen wird der Strom, dessen ZIEL jemandem
     * gehoert und dessen Quelle nicht demselben.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimFliessen(BlockFromToEvent ereignis) {
        if (!anGrenzenGreift(ereignis.getBlock().getLocation(),
                Weltregel.FLUESSIGKEIT)) {
            return;
        }
        if (uebergriff(ereignis.getBlock().getLocation(),
                ereignis.getToBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Bilderrahmen, Gemaelde und Leinen - Wesen, die in keiner blockList stehen.
     *
     * <p><b>Der neunte Zuhoerer, und er schliesst ein Loch im achten:</b> Die zwei
     * Sprengzweige leeren {@code blockList()}, und das schuetzt BLOECKE. Ein
     * Bilderrahmen ist aber ein Wesen; er stand in keiner Liste und war nach dem
     * Knall weg, obwohl {@code explosion} fuer die Welt eingetragen war. Fuer einen
     * Laden an der Marktstadtwand heisst das: die Mauer blieb, die Ware darin war
     * fort.
     *
     * <p>Der einzige andere Haken dieser Art im Plugin steht in
     * {@link Schutzwache} und greift nur, wenn der Entferner ein Spieler IST - ein
     * Creeper ist keiner.
     *
     * <p>Gefragt wird nur die Ursache EXPLOSION, und das ist eine Grenze mit Grund:
     * {@code PHYSICS} traegt den Rahmen, dessen Wand ein Spieler abgebaut hat, und
     * der soll fallen duerfen. Was Feuer mit einem Rahmen macht, meldet Bukkit
     * nicht als eigene Ursache; das bleibt offen und steht so in TODO.md.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH,
            ignoreCancelled = true)
    public void beimHaengendenAbreissen(HangingBreakEvent ereignis) {
        if (!istSprengursache(ereignis.getCause())) {
            return;
        }
        if (weltweitGreift(ereignis.getEntity().getLocation(),
                Weltregel.EXPLOSION)) {
            ereignis.setCancelled(true);
        }
    }

    // === Die zwei Fragen, die alle neun Zweige stellen =======================

    /**
     * Greift diese Regel an diesem Ort?
     *
     * <p>Die EINE Frage der weltweiten Regeln. Ein {@code null}-Ort kann vorkommen -
     * Bukkit gibt ihn fuer eine entladene Welt - und heisst "nicht unsere Sache".
     */
    // DIE DREI ENTSCHEIDUNGEN SIND PAKETSICHTBAR UND NICHT PRIVAT, und das ist
    // kein Nachlassen: Die neun Zweige darueber sind Bukkit-Ereignisse, und die
    // lassen sich ohne laufenden Server nicht bauen - eine Rechnung dahinter waere
    // in keiner Probe erreichbar. Dieselbe Begruendung wie bei jeder Naht dieses
    // Projekts, nur umgekehrt angewandt: Hier wird nicht die Rechnung
    // herausgezogen, sondern ihre Tuer geoeffnet.
    boolean greift(Location ort, Weltregel regel) {
        if (ort == null) {
            return false;
        }
        World welt = ort.getWorld();
        return welt != null && werte.get().greift(welt.key().asString(), regel);
    }

    /**
     * Ueberschreitet dieser Kolben mit einem seiner Bloecke eine Besitzgrenze?
     *
     * <p>Herausgezogen, weil ZWEI Zuhoerer dieselbe Frage stellen - Schieben und Ziehen -
     * und ein zweiter Rumpf irgendwann anders waere. Genau die Begruendung, die der
     * Klassenkopf von {@link Schutzwache} fuer ihre fuenfzehn Zugaenge gibt.
     *
     * @param richtung wohin der Kolben wirkt; der Zielort eines Blocks liegt einen
     *                 Schritt weiter
     */
    boolean kolbenUeberschreitet(Block kolben, List<Block> bewegte,
                                 org.bukkit.block.BlockFace richtung) {
        if (!anGrenzenGreift(kolben.getLocation(), Weltregel.KOLBEN)) {
            return false;
        }
        Location vom = kolben.getLocation();
        for (Block block : bewegte) {
            if (uebergriff(vom, block.getLocation())
                    || uebergriff(vom, block.getRelative(richtung).getLocation())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gehoert das Ziel jemandem, der nicht auch die Quelle besitzt?
     *
     * <p><b>Die Frage ist bewusst EINSEITIG</b>: Vom Besitz nach draussen ist erlaubt,
     * von draussen in den Besitz nicht. Die andere Richtung mitzuverbieten waere
     * naheliegend und falsch - eine Farm am Parzellenrand laesst Wasser auf den Weg
     * laufen, und das ist niemandes Schaden.
     *
     * <p>Verglichen werden die NUMMERN der zustaendigen Parzellen und nicht die
     * Eigentuemer: Zwei Parzellen desselben Spielers sind zwei Grundstuecke, und zwischen
     * ihnen liegt ein Weg, der allen gehoert. Wer dort Lava laufen laesst, schadet der
     * Stadt, auch wenn beide Enden ihm gehoeren.
     *
     * <p>Faellt die Ablage aus, wird ABGEBROCHEN - die strenge Richtung. Dieselbe
     * Entscheidung wie in {@code Schutzwache.darfHandeln}: Ein Netzfehler darf keinen
     * Krater erlauben. Geklagt wird gedrosselt, denn der Fall traete bei jedem
     * Fliessereignis ein.
     */
    boolean uebergriff(Location von, Location nach) {
        World welt = nach.getWorld();
        if (welt == null) {
            return false;
        }
        String schluessel = welt.key().asString();
        Zustaendigkeit wer = bauwelten.get(schluessel);
        if (wer == null) {
            // KEINE BAUWELT. In einer Farmwelt gehoert kein Block jemandem, also
            // gibt es keine Grenze zu ueberschreiten. Die weltweiten Regeln
            // greifen trotzdem - wer dort keine Krater will, traegt explosion
            // ein.
            return false;
        }
        if (wer.ganzGebaut()) {
            // ALTFURT: kein Raster, keine Einheiten, Spielmodus Abenteuer. Dort
            // gibt es keine Grenze, die ueberschritten werden koennte - und
            // genau deshalb waere "niemandem gehoert etwas, also erlaubt" die
            // falsche Antwort. In einer ganz gebauten Welt ist JEDE Bewegung von
            // Bloecken ein Eingriff.
            //
            // Wer dort doch Redstone will, nimmt kolben aus der Liste dieser
            // Welt - die Entscheidung bleibt in der Datei.
            return true;
        }
        try {
            Optional<Einheit> ziel = wer.fuer(schluessel, nach.getBlockX(),
                    nach.getBlockZ());
            if (ziel.isEmpty()) {
                return false;
            }
            Optional<Einheit> quelle = wer.fuer(schluessel, von.getBlockX(),
                    von.getBlockZ());
            // Verglichen wird die ganze EINHEIT - Art UND Nummer. Ein Vergleich
            // der Nummern allein hielte Grundstueck 5 fuer Stadtfeld 5; die
            // Begruendung steht an Einheit.
            return quelle.isEmpty() || !quelle.get().equals(ziel.get());
        } catch (RuntimeException fehler) {
            klageUeberAblage(fehler);
            return true;
        }
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt aus demselben Grund wie in {@link Schutzwache}: Ohne Drossel
     * schreibt ein ausgefallener Datenbankrechner in Sekunden ein Protokoll voll und
     * verdeckt damit jede andere Meldung. Ein fliessender Lavastrom loest dieses Ereignis
     * mehrmals je Sekunde aus.
     */
    private void klageUeberAblage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        // DER FEHLERTYP GEHOERT IN DIE ZEILE, und das ist gemessen: Ein
        // NoSuchElementException aus einem Programmfehler stand hier als "Besitz
        // nicht nachsehbar: No value present" - also als Netzproblem. Der
        // Betreiber haette die Datenbank neu gestartet, den Fehler nie gemeldet,
        // und der Schutz haette solange ALLES abgebrochen.
        //
        // Die Ablage wirft IllegalStateException mit "Datenbankfehler beim ..."
        // (MariaLager.weiterreichen). JEDER ANDERE TYPNAME IST EIN FEHLER IM
        // PLUGIN, und am Namen ist das zu sehen.
        protokoll.log(Level.WARNING, "[Weltregeln] Besitz nicht nachsehbar, es wird"
                + " abgebrochen: " + fehler.getClass().getSimpleName()
                + ": " + fehler.getMessage());
    }

    /**
     * Ein weltweiter Riegel - und er BESTEHT darauf, dass die Regel weltweit gilt.
     *
     * <p><b>Hier bekommt {@link Weltregel#giltWeltweit} ihren Verbraucher.</b> Ohne
     * diese zwei Riegel stand die Zweiteilung allein darin, WELCHER Zuhoerer
     * {@code uebergriff} ruft - und damit galt genau das, was die Aufzaehlung zu
     * verhindern verspricht: Wer eine sechste Regel dort korrekt einordnet, aendert
     * nichts an der Wirkung, und wer {@code KOLBEN} dort auf weltweit umstellt,
     * aendert nur eine gruene Probe.
     *
     * <p>GEWORFEN und nicht stumm umgeleitet: Eine falsch eingeordnete Regel ist ein
     * Fehler im Plugin und keine Eingabe des Betreibers. Ein stummer Umweg waere
     * genau der Zustand, den dieses Projekt an dieser Stelle sechsmal gehabt hat -
     * gebaut, gruen geprueft, wirkungslos. Der Wurf traegt den Namen der Regel, und
     * eine Probe haelt ihn fest, damit er nie auf einem Server auftritt.
     */
    boolean weltweitGreift(Location ort, Weltregel regel) {
        if (!regel.giltWeltweit()) {
            throw new IllegalArgumentException(regel.schluessel() + " gilt laut"
                    + " Weltregel.giltWeltweit NICHT weltweit, wird hier aber von"
                    + " einem weltweiten Zweig gefragt. Einer von beiden ist"
                    + " falsch - entweder die Einordnung in der Aufzaehlung oder"
                    + " die Verdrahtung in der Weltwache.");
        }
        return greift(ort, regel);
    }

    /** Derselbe Riegel fuer die andere Sorte - siehe {@link #weltweitGreift}. */
    boolean anGrenzenGreift(Location ort, Weltregel regel) {
        if (regel.giltWeltweit()) {
            throw new IllegalArgumentException(regel.schluessel() + " gilt laut"
                    + " Weltregel.giltWeltweit weltweit, wird hier aber von einem"
                    + " Grenzzweig gefragt. Ein Grenzzweig fragt zusaetzlich den"
                    + " Besitz; eine weltweite Regel wuerde damit nur noch auf"
                    + " fremdem Grund greifen und auf freiem Grund nicht.");
        }
        return greift(ort, regel);
    }

    /**
     * Ist das ein Wesen, dessen Blockaenderung die Regel {@code wesen} meint?
     *
     * <p><b>Der fallende Block ist keines</b>, und das ist der ganze Zweck dieser
     * Frage: {@code EntityChangeBlockEvent} traegt auch das LANDEN eines
     * {@code FallingBlock} - Sand, Kies, Betonpulver, Amboss, Drachenei. Das ist
     * kein Enderman, der etwas wegtraegt, sondern ein Block in der Luft, den ein
     * SPIELER gesetzt hat.
     *
     * <p>Herausgezogen und nicht im Zweig gelassen: Der Zweig ist ein
     * Bukkit-Ereignis und ohne laufenden Server nicht baubar - eine Rechnung darin
     * waere in keiner Probe erreichbar. Dieselbe Begruendung wie bei den drei
     * Entscheidungen darunter.
     */
    static boolean wesenZaehlt(EntityType art) {
        return art != EntityType.FALLING_BLOCK;
    }

    /**
     * Hat eine EXPLOSION den Bilderrahmen abgerissen?
     *
     * <p>Nur diese Ursache, und die Grenze hat einen Grund: {@code PHYSICS} traegt
     * den Rahmen, dessen Wand ein Spieler abgebaut hat, und der soll fallen
     * duerfen. {@code ENTITY} ohne Explosion ist der Skelettpfeil oder der
     * Spieler - dafuer gibt es den Bauschutz. Wuerde dieser Zweig alles abbrechen,
     * waere ein Bilderrahmen in der Marktstadt nicht mehr abnehmbar.
     */
    static boolean istSprengursache(HangingBreakEvent.RemoveCause ursache) {
        return ursache == HangingBreakEvent.RemoveCause.EXPLOSION;
    }
}
