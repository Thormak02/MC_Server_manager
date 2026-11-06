package de.freimarken.paper;

import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Taetigkeit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Was Arbeit einbringt - der Haken am Abbauen und der Zahlmeister fuers Setzen.
 *
 * <p><b>Im Ereignis wird nur gerechnet.</b> Keine Netzrunde, kein Datenbankschreibzug,
 * kein zweiter Faden - das ist die Regel, die sich dieses Projekt selbst gegeben hat, und
 * sie steht in {@link Verdienstbuch} ausgerechnet. Gebucht wird im Takt.
 *
 * <p><b>{@link EventPriority#MONITOR} mit {@code ignoreCancelled}</b>, aus demselben Grund
 * wie bei {@link Setzwache}: Die {@link Schutzwache} laeuft auf NORMAL und bricht ab, und
 * nach ihr kann ein anderer Hoerer noch abbrechen. Auf NORMAL bezahlte diese Wache Arbeit,
 * die gar nicht stattfand.
 *
 * <p><b>Das Abbauen fragt die MARKE, das Setzen nicht.</b> Ein markierter Block wurde
 * einmal gesetzt und ist bezahlt - er bringt beim Abbauen nichts. Fuers Setzen gilt das
 * Gegenteil: Dort IST die neue Marke der Beweis, dass diese Position noch nie bezahlt
 * wurde, und sie entsteht in der Setzwache. Darum ruft JENE hier herein
 * ({@link #zahleFuerSetzen}) und nicht umgekehrt: Ein zweiter Hoerer auf MONITOR laese die
 * Marke erst nach dem Schreibzug der Setzwache und zahlte nie.
 *
 * <p><b>Die Berufe kommen aus dem Zwischenspeicher</b>
 * ({@code ZwischenspeicherBerufe}), nicht aus der Datenbank: Eine Abfrage je Block waere
 * genau der Fehler, gegen den der Zwischenspeicher vor dem Lager schon gebaut ist. Dass
 * ein {@code /beruf waehlen} sofort wirkt, besorgt dessen Leerung bei jedem Schreibzug.
 *
 * <p><b>Was NICHT hier haengt</b>, benannt statt stillschweigend offen: Fischen, Toeten,
 * Zuechten und Ernten. Sie brauchen je ein eigenes Ereignis, das dieses Plugin heute nicht
 * hoert, und kommen mit dem naechsten Schritt - dann als weitere Konstante in
 * {@link Taetigkeit} und als weitere Zeilen in der Lohnliste, ohne dass hier etwas
 * umzieht.
 */
final class Verdienstwache implements Listener {

    /**
     * Wie oft der Zeitgeber schlaegt - jede Sekunde.
     *
     * <p>Nicht jeden Tick: Der Schlag sieht die Staende aller Spieler durch, und
     * zwanzigmal je Sekunde waere das zwanzigmal fuer nichts. Nicht je Minute: Dann
     * stuende der Betrag eines Spielers bis zu einer Minute lang nur im Arbeitsspeicher,
     * und ein Serverabsturz nahme ihn mit.
     */
    static final long TAKT_TICKS = 20L;

    /**
     * Wie viele Spieler EIN Schlag bucht.
     *
     * <p>Gemessen begruendet: Eine Buchung sind drei SQL-Anweisungen in einer
     * Transaktion, also etwa sechs Netzrunden. Fuenfzig Spieler in EINEM Tick waeren
     * dreihundert Runden hintereinander - das ist genau die Massenabmeldung, gegen die
     * {@link Spielzeitbuch} seine Sperre haelt. Zu drei je Sekunde ist eine Serverfuellung
     * in einer knappen halben Minute durch, und kein Tick traegt mehr als achtzehn Runden.
     *
     * <p>Verloren geht dabei nichts: Was dieser Schlag nicht bucht, steht beim naechsten
     * noch da.
     */
    static final int JE_SCHLAG = 3;

    /**
     * Woher der Zeitgeber kommt.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk, aus demselben Grund wie
     * {@link Chunkmarken.Weltsucher} und {@link Setzwache.Zahlmeister}: Ohne sie
     * braeuchte jede Probe dieser Wache ein {@code JavaPlugin}, und das laesst sich ohne
     * laufenden Server nicht bauen - der Zweig, in dem Geld entsteht, waere der einzige
     * ungepruefte dieses Schrittes. Wer den Zeitgeber stellt, entscheidet damit auch, in
     * welchem Faden er laeuft; die Begruendung dafuer steht beim Aufrufer.
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

    private final Verdienstbuch buch;
    private final Blockmarken marken;
    private final Berufsablage berufe;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * In welchen Welten verdient wird - dieselbe Menge wie bei {@link Setzwache}.
     *
     * <p>Dieselbe und nicht eine eigene: Was markiert wird, soll auch zahlen, und
     * umgekehrt. Zwei Listen liefen auseinander, und dann waere eine Welt markiert, in
     * der niemand verdient - oder schlimmer: eine, in der verdient und nicht markiert
     * wird, und das ist die Gelddruckmaschine.
     */
    private final Set<String> welten;

    /** Was den laufenden Zeitgeber abmeldet - und zugleich, DASS einer laeuft. */
    private Runnable abmelden;

    Verdienstwache(Taktquelle taktquelle, Verdienstbuch buch, Blockmarken marken,
                   Berufsablage berufe, Set<String> welten, Sprache sprache,
                   Logger protokoll) {
        if (taktquelle == null || buch == null || marken == null || berufe == null
                || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("Taktquelle, Sammler, Marken,"
                    + " Berufsablage, Sprache und Protokoll sind Pflicht");
        }
        if (welten == null || welten.isEmpty()) {
            throw new IllegalArgumentException("Ohne Welten verdient niemand etwas - eine"
                    + " leere Menge ist ein Zweig, der nie etwas tut");
        }
        this.taktquelle = taktquelle;
        this.buch = buch;
        this.marken = marken;
        this.berufe = berufe;
        this.welten = Set.copyOf(welten);
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Stellt den Zeitgeber, der bucht.
     *
     * <p>Im Hauptfaden und nicht nebenlaeufig - das besorgt die {@link Taktquelle}, die
     * das Plugin mitgibt: Die Datenschicht haelt EINE Verbindung, und MariaKasse schaltet
     * darauf {@code autoCommit} um. Ein zweiter Faden committete dort die halbe Buchung
     * des Hauptfadens mit - dieselbe Begruendung, die {@link Spielzeitwache}
     * ausbuchstabiert.
     */
    void starte() {
        if (abmelden != null) {
            throw new IllegalStateException("Dieser Zeitgeber steht schon. Ein zweiter"
                    + " buchte denselben Verdienst doppelt.");
        }
        Runnable gestellt = taktquelle.stelle(this::schlag, TAKT_TICKS);
        if (gestellt == null) {
            // Ein stilles null waere der schlechtere Ausgang: Die Wache hoerte weiter auf
            // Bloecke, sammelte Pfennige - und bei abmelden == null liesse sich ein
            // zweiter Zeitgeber stellen, der denselben Verdienst doppelt buchte.
            throw new IllegalStateException("Die Taktquelle hat keinen Zeitgeber"
                    + " geliefert. Ohne ihn wird nie gebucht, und beende() koennte nichts"
                    + " abmelden.");
        }
        abmelden = gestellt;
    }

    /**
     * Meldet den Zeitgeber ab und bucht ALLES - in dieser Reihenfolge.
     *
     * <p>Erst abmelden, dann buchen: Sonst kaeme mitten im letzten Abrechnen noch ein
     * Schlag dazu. Und VOR {@code datenbank.close()}, sonst waere der letzte Verdienst
     * jedes anwesenden Spielers verloren.
     */
    void beende() {
        if (abmelden != null) {
            abmelden.run();
            abmelden = null;
        }
        long gebucht = buch.rechneAllesAb();
        if (gebucht > 0L) {
            protokoll.info("Beim Abschalten noch " + gebucht + " Mark Verdienst"
                    + " gebucht.");
        }
    }

    private void schlag() {
        buch.rechneAb(JE_SCHLAG);
    }

    /**
     * Ein abgebauter Block - gewachsener Boden zahlt, gesetzter nicht.
     *
     * <p>Duenn gehalten wie jeder Ereigniszweig dieses Projekts: Das Ereignis wird in
     * einfache Werte uebersetzt, entschieden wird in {@link #verdiene}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        Block block = ereignis.getBlock();
        verdiene(ereignis.getPlayer(), ortVon(block), Taetigkeit.ABBAUEN,
                block.getType(), 1);
    }

    /**
     * Ein Abgang: sofort abrechnen und den laufenden Zustand vergessen.
     *
     * <p>Ohne Prioritaet und ohne {@code ignoreCancelled} - ein Abgang laesst sich nicht
     * abbrechen. Sofort und nicht im naechsten Takt: Der Spieler ist weg, aber seine
     * Arbeit ist geschehen.
     */
    @EventHandler
    public void beimAbgang(PlayerQuitEvent ereignis) {
        try {
            buch.abgang(ereignis.getPlayer().getUniqueId());
        } catch (RuntimeException fehler) {
            // Ein Wurf aus einem Abgangszweig reisst die ganze Abmeldung mit, und Bukkit
            // schreibt sie ins Protokoll, ohne dass ein Mensch sie sieht. Der Sammler
            // faengt seine Kassenfehler selbst; was hier noch durchkommt, ist ein
            // Rechenfehler und gehoert laut gemeldet.
            protokoll.log(Level.SEVERE, "Abrechnen beim Abgang von "
                    + ereignis.getPlayer().getName() + " fehlgeschlagen", fehler);
        }
    }

    /**
     * Der Zahlmeister fuers Setzen - gerufen von {@link Setzwache}.
     *
     * <p>{@code neueMarken} ist die Zahl der Positionen, die vorher noch keine Marke
     * trugen. Bei einer Tuer sind das zwei, und sie zahlt zweimal: Es sind zwei Bloecke.
     * Bei einer Tuer, von der eine Haelfte schon stand, zahlt nur die andere.
     */
    void zahleFuerSetzen(Player wer, Material art, int neueMarken) {
        if (neueMarken <= 0) {
            return;
        }
        // Der Ort wird hier NICHT gebraucht: Die Setzwache hat die Marke schon gesetzt
        // und sagt mit ihrer Zahl, dass diese Positionen noch nie bezahlt wurden.
        verdiene(wer, null, Taetigkeit.SETZEN, art, neueMarken);
    }

    /**
     * Der gemeinsame Hals - Welt, Marke, Berufe, Lohn, Handbalken.
     *
     * <p>Die Reihenfolge ist nach Kosten geordnet, und das ist Absicht: zuerst die Welt
     * (ein Nachschlag in einer kleinen Menge), dann die Marke (ein Bitfeld im geladenen
     * Chunk), dann die Berufe (ein Nachschlag im Zwischenspeicher), und erst zuletzt die
     * Rechnung. Ein Block in einer fremden Welt kostet damit genau einen Vergleich.
     *
     * @param ort        der Ort fuer die Markenfrage, oder {@code null} beim Setzen
     * @param wiederholt wie oft diese Taetigkeit gerechnet wird - bei einer Tuer zwei
     */
    private void verdiene(Player wer, Blockort ort, Taetigkeit taetigkeit, Material art,
                          int wiederholt) {
        String welt = wer.getWorld().key().asString();
        if (!welten.contains(welt)) {
            return;
        }
        if (ort != null && marken.istMarkiert(ort)) {
            // Gesetzter Boden zahlt beim Abbauen nichts - "ein Block zahlt nur einmal".
            return;
        }
        UUID kennung = wer.getUniqueId();
        Set<String> gehalten = gehaltene(kennung);
        if (gehalten.isEmpty()) {
            return;
        }
        String material = art.name().toLowerCase(Locale.ROOT);
        for (int mal = 0; mal < wiederholt; mal++) {
            buch.verdiene(kennung, gehalten, taetigkeit, material);
        }
        zeige(wer, kennung);
    }

    /**
     * Die Berufe dieses Spielers - aus dem Zwischenspeicher.
     *
     * <p>Ein Ausfall der Ablage darf hier nicht durchschlagen: Der Zweig laeuft auf
     * MONITOR, und ein Wurf landete als Stapelspur im Serverprotokoll, je Block erneut.
     * Eine leere Menge heisst "kein Lohn" - die sichere Richtung.
     */
    private Set<String> gehaltene(UUID wer) {
        try {
            Berufsstand stand = berufe.lies(wer);
            return new HashSet<>(stand.berufe());
        } catch (RuntimeException fehler) {
            return Set.of();
        }
    }

    /**
     * Der Handbalken - hoechstens einmal je Sekunde, und am Deckel ein anderer Satz.
     *
     * <p>Die Drossel steht im Sammler: Sie ist Zustand je Spieler. Hier bleibt nur die
     * Wahl zwischen den zwei Saetzen - und die haengt daran, ob der Deckel gerade greift.
     */
    private void zeige(Player wer, UUID kennung) {
        long summe = buch.fuerDenHandbalken(kennung);
        if (summe < 0L) {
            return;
        }
        if (buch.amDeckel(kennung)) {
            sprache.sendeHandbalken(wer, BerufSatz.VERDIENST_DECKEL, Einsatz.keine());
            return;
        }
        if (summe > 0L) {
            sprache.sendeHandbalken(wer, BerufSatz.VERDIENST_HANDBALKEN,
                    Einsatz.von("betrag", sprache.pfennige(summe)));
        }
    }

    private static Blockort ortVon(Block block) {
        return new Blockort(block.getWorld().key().asString(),
                block.getX(), block.getY(), block.getZ());
    }

    /** Die Blockarten, die ein Spieler gerade gezaehlt hat - nur fuer die Proben. */
    List<String> gezaehlteArten(UUID wer) {
        return buch.gezaehlteArten(wer);
    }
}
