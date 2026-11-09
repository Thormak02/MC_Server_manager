package de.freimarken.paper;

import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import java.time.Clock;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Schreibt auf, wer wo was getan hat - drei Haken fuer die Moderation.
 *
 * <p><b>{@link EventPriority#MONITOR} OHNE {@code ignoreCancelled}</b>, und das ist genau
 * umgekehrt zu {@link Verdienstwache} und {@link Setzwache}. Jene wollen nur die
 * gelungenen Taten sehen - sie zahlen Geld, und fuer eine abgebrochene Tat darf nichts
 * fliessen. Dieses Buch will BEIDES: Ein Dutzend abgewiesener Versuche an einer fremden
 * Parzelle in zwei Minuten ist der Fund, nach dem die Moderation sucht, und es ist ein
 * Fund, bei dem nichts passiert ist. MONITOR ist dabei die Stelle, an der der Ausgang
 * feststeht - nach dem Schutz und nach jedem anderen Hoerer, der noch abbrechen koennte.
 *
 * <p><b>Die Welt wird ZUERST gefragt</b>, vor allem anderen. Nach Kosten geordnet, wie
 * bei {@link Verdienstwache}: Ein Block in einer Welt ohne Blockbuch kostet damit genau
 * einen Nachschlag in einer kleinen Karte - kein Objekt, keine Zeichenkette, kein
 * Stapeleintrag.
 *
 * <p><b>Die Einstellungen sind FEST und nicht neuladbar.</b> Anders als bei den Preisen,
 * und mit Grund: Diese Wache haengt an keinem {@code Wirtschaftsstand}. Was aufgeschrieben
 * wird, aendert sich im Betrieb nicht sinnvoll - ein Neuladen mitten im Lauf hinterliesse
 * ein Buch, dessen Luecken niemand erklaeren kann. Der Leser sagt im Startbericht
 * ausdruecklich, dass eine Aenderung einen Neustart braucht.
 *
 * <p><b>Beim Benutzen sind drei Tore noetig</b>, und jedes schliesst etwas anderes:
 * <ul>
 *   <li><b>Nur der Rechtsklick auf einen Block.</b> Der Linksklick ist der Beginn eines
 *       Abbaus, und der hat seinen eigenen Haken - sonst stuende jeder angeschlagene Block
 *       zweimal im Buch.</li>
 *   <li><b>Nur die Haupthand.</b> {@link PlayerInteractEvent} feuert fuer BEIDE Haende,
 *       und ohne dieses Tor stuende jede Tuer zweimal im Buch, auf die Millisekunde
 *       gleich. Das ist die unangenehmste Sorte Fehler: Die Zeilen sehen echt aus, und
 *       wer sie zaehlt, kommt auf das Doppelte.</li>
 *   <li><b>Nur ein Block aus {@link Blockbuchwerte#PFAD_BENUTZBAR}.</b> Dieses Tor steht
 *       nicht hier, sondern in der Regel - aber es ist das wichtigste: Ohne es schriebe
 *       jeder Rechtsklick auf Erde eine Zeile, und wer mit einem Block in der Hand baut,
 *       erzeugt beides - einen Rechtsklick UND eine Setzung, also zwei Zeilen fuer eine
 *       Tat. Eine Liste in der Datei und nicht {@code Material.isInteractable()}: Jene
 *       Frage ist in Bukkit veraltet, und sie haelt auch jedes Gartentor fuer
 *       interessant.</li>
 * </ul>
 */
final class Blockwache implements Listener {

    /**
     * Wie oft der Zeitgeber schlaegt - jede Sekunde.
     *
     * <p>Dieselbe Zahl wie bei {@link Verdienstwache#TAKT_TICKS} und aus demselben Grund:
     * Nicht jeden Tick, denn der Schlag ist ein Schreibzug ueber das Netz. Nicht je
     * Minute, denn dann stuende bis zu eine Minute Spielgeschehen nur im
     * Arbeitsspeicher, und ein Serverabsturz naehme sie mit.
     */
    static final long TAKT_TICKS = 20L;

    /**
     * Woher der Zeitgeber kommt.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk - dieselbe Bauart wie
     * {@link Verdienstwache.Taktquelle}. Eine EIGENE Schnittstelle und nicht jene
     * geteilt: Das Blockbuch soll nicht an der Verdienstrechnung haengen. Zwei Bereiche,
     * die sich eine Schnittstelle teilen, teilen sich bald mehr, und dann kann man den
     * einen nicht ohne den anderen pruefen.
     */
    interface Taktquelle {

        /**
         * Stellt den Zeitgeber.
         *
         * @param schlag       was jeden Takt zu tun ist
         * @param abstandTicks wie viele Ticks zwischen zwei Schlaegen liegen
         * @return was diesen Zeitgeber wieder abmeldet - nie {@code null}
         */
        Runnable stelle(Runnable schlag, long abstandTicks);
    }

    private final Taktquelle taktquelle;
    private final Blockbuch buch;
    private final Blockbuchwerte werte;
    private final Clock uhr;
    private final Logger protokoll;

    /** Was den laufenden Zeitgeber abmeldet - und zugleich, DASS einer laeuft. */
    private Runnable abmelden;

    Blockwache(Taktquelle taktquelle, Blockbuch buch, Blockbuchwerte werte, Clock uhr,
               Logger protokoll) {
        if (taktquelle == null || buch == null || werte == null || uhr == null
                || protokoll == null) {
            throw new IllegalArgumentException("Taktquelle, Buch, Werte, Uhr und"
                    + " Protokoll sind Pflicht");
        }
        this.taktquelle = taktquelle;
        this.buch = buch;
        this.werte = werte;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Stellt den Zeitgeber, der schreibt.
     *
     * <p>Im Hauptfaden und nicht nebenlaeufig - das besorgt die {@link Taktquelle}, die
     * das Plugin mitgibt: Die Datenschicht haelt EINE Verbindung, und MariaKasse schaltet
     * darauf {@code autoCommit} um. Ein zweiter Faden committete dort die halbe Buchung
     * des Hauptfadens mit.
     */
    void starte() {
        if (abmelden != null) {
            throw new IllegalStateException("Dieser Zeitgeber steht schon. Ein zweiter"
                    + " schriebe denselben Stapel zweimal - und weil das Blockbuch keinen"
                    + " eindeutigen Schluessel hat, stuende dann jede Tat doppelt darin,"
                    + " ohne dass etwas davor warnt.");
        }
        Runnable gestellt = taktquelle.stelle(this::schlag, TAKT_TICKS);
        if (gestellt == null) {
            throw new IllegalStateException("Die Taktquelle hat keinen Zeitgeber"
                    + " geliefert. Ohne ihn wird nie geschrieben, und beende() koennte"
                    + " nichts abmelden.");
        }
        abmelden = gestellt;
    }

    /**
     * Meldet den Zeitgeber ab und schreibt ALLES - in dieser Reihenfolge.
     *
     * <p>Erst abmelden, dann schreiben: Sonst kaeme mitten im letzten Schreibzug noch ein
     * Schlag dazu. Und VOR {@code datenbank.close()}, sonst waere die letzte Sekunde
     * Spielgeschehen verloren - dieselbe Reihenfolge wie bei
     * {@link Verdienstwache#beende}.
     */
    void beende() {
        if (abmelden != null) {
            abmelden.run();
            abmelden = null;
        }
        int offen = buch.imStapel();
        int geschrieben = buch.gibAb();
        if (offen > 0) {
            protokoll.info("Beim Abschalten noch " + geschrieben + " von " + offen
                    + " Blockzeilen geschrieben.");
        }
    }

    private void schlag() {
        buch.gibAb();
    }

    /** Ein gesetzter Block - der GESETZTE zaehlt, nicht der, auf den geklickt wurde. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimSetzen(BlockPlaceEvent ereignis) {
        // getBlockPlaced und nicht getBlockAgainst: Gefragt wird, was jetzt dasteht.
        // Bei einem Eimer Wasser ist das Wasser der Block, und das ist die Angabe, die
        // jemand sucht, der eine geflutete Parzelle vor sich hat.
        halteFest(ereignis.getPlayer(), ereignis.getBlockPlaced(), Blocktat.SETZEN,
                !ereignis.isCancelled());
    }

    /**
     * Ein abgebauter Block - sein Name, wie er VOR dem Abbau dastand.
     *
     * <p>Im Ereignis gelesen und nicht danach: Hinterher steht dort Luft, und die
     * beantwortet keine Frage. Das ist der Grund, warum diese Zeile hier entsteht und
     * nicht im Takt.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        halteFest(ereignis.getPlayer(), ereignis.getBlock(), Blocktat.ABBAUEN,
                !ereignis.isCancelled());
    }

    /**
     * Ein benutzter Block - Tuer, Knopf, Hebel, Truhe, Ofen.
     *
     * <p>Der Ausgang kommt aus {@link PlayerInteractEvent#useInteractedBlock()} und nicht
     * aus {@code isCancelled()}. Dieses Ereignis traegt zwei Ergebnisse - eines fuer den
     * Block, eines fuer den Gegenstand in der Hand -, und gefragt wird das BLOCKergebnis:
     * Wer mit einem unerlaubten Werkzeug eine erlaubte Truhe oeffnet, hat die Truhe
     * geoeffnet.
     *
     * <p><b>Die beiden sind in Bukkit allerdings DASSELBE, und das ist gemessen</b>
     * (Einspritzung B13 am 09.11.2026): {@code isCancelled()} liest genau dieses
     * Blockergebnis, ein Tausch bleibt also wirkungslos, und alle 2.516 Proben blieben
     * gruen. Hier stand einmal die Begruendung, {@code isCancelled()} sei schon bei
     * verweigertem GEGENSTAND wahr - das ist falsch. Was bleibt, ist ein handfesterer
     * Grund: {@code isCancelled()} ist bei diesem Ereignis VERALTET, und dieses Projekt
     * uebersetzt mit {@code -Xlint:all} ohne Warnung und ohne ein einziges
     * {@code SuppressWarnings}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBenutzen(PlayerInteractEvent ereignis) {
        if (ereignis.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (ereignis.getHand() != EquipmentSlot.HAND) {
            // Das Ereignis feuert fuer BEIDE Haende. Ohne dieses Tor stuende jede Tuer
            // zweimal im Buch, auf die Millisekunde gleich - Zeilen, die echt aussehen,
            // und eine Zaehlung, die das Doppelte ergibt.
            return;
        }
        Block block = ereignis.getClickedBlock();
        if (block == null) {
            return;
        }
        // Dass nur BENUTZBARE Bloecke gezaehlt werden, entscheidet die Regel in
        // Blockbuchwerte - hier steht kein zweites Tor. Ohne jene Liste schriebe jeder
        // Rechtsklick auf Erde eine Zeile, und wer mit einem Block in der Hand baut,
        // erzeugte beides: einen Rechtsklick UND eine Setzung, also zwei Zeilen fuer
        // eine Tat.
        halteFest(ereignis.getPlayer(), block, Blocktat.BENUTZEN,
                ereignis.useInteractedBlock() != Event.Result.DENY);
    }

    /**
     * Der gemeinsame Hals - Welt, Regel, Zeile, Stapel.
     *
     * <p>Alle drei Taten gehen durch diese eine Stelle, und sie stellen GENAU EINE Frage
     * ({@link Blockbuchwerte#schreibt}). Drei getrennte Fragen - Welt, Ausgang, Blockliste
     * - waeren drei Verzweigungen an jedem Ereigniszweig, und beim naechsten Zweig
     * vergessen.
     *
     * <p>Der Blockname wird VOR der Frage gebaut, obwohl er bei einer Welt ohne Blockbuch
     * umsonst entsteht. Das ist Absicht und keine Nachlaessigkeit: Er wird fuer die Zeile
     * ohnehin gebraucht, und ein {@code toLowerCase} auf einen kurzen Namen ist bei
     * vierhundert Bloecken je Sekunde nichts gegen den Preis einer zweiten
     * Entscheidungsstelle.
     */
    private void halteFest(Player wer, Block block, Blocktat tat, boolean gelungen) {
        String welt = block.getWorld().key().asString();
        String name = namenVon(block);
        if (!werte.schreibt(welt, tat, gelungen, name)) {
            return;
        }
        buch.merke(Blockzeile.neu(uhr.instant(), wer.getUniqueId(),
                new Blockort(welt, block.getX(), block.getY(), block.getZ()),
                tat, gelungen, name));
    }

    /** Der Name des Blocks, kleingeschrieben - wie in der Lohnliste. */
    private static String namenVon(Block block) {
        return block.getType().name().toLowerCase(Locale.ROOT);
    }

    /** Nur fuer die Proben: wie viele Zeilen noch auf dem Stapel liegen. */
    int imStapel() {
        return buch.imStapel();
    }
}
