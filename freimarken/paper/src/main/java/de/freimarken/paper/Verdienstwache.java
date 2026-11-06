package de.freimarken.paper;

import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Wirtschaftsstand;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Was Arbeit einbringt - fuenf Haken und der Zahlmeister fuers Setzen.
 *
 * <p><b>Im Ereignis wird nur gerechnet.</b> Keine Netzrunde, kein Datenbankschreibzug,
 * kein zweiter Faden - das ist die Regel, die sich dieses Projekt selbst gegeben hat, und
 * sie steht in {@link Verdienstbuch} ausgerechnet. Gebucht wird im Takt.
 *
 * <p><b>{@link EventPriority#MONITOR} mit {@code ignoreCancelled}</b> an JEDEM
 * zahlenden Zweig, aus demselben Grund wie bei {@link Setzwache}: Die {@link Schutzwache}
 * laeuft auf NORMAL und bricht ab, und nach ihr kann ein anderer Hoerer noch abbrechen.
 * Auf NORMAL bezahlte diese Wache Arbeit, die gar nicht stattfand.
 *
 * <p><b>Jedes Ereignis zahlt GENAU EINE Taetigkeit.</b> Beim abgebauten Block entscheidet
 * sich das an der Lohnliste: Liegt fuer dieses Material ein Erntesatz vor, ist es eine
 * ERNTE, sonst ein ABBAU. Zwei Aufrufe fuer denselben Block zahlten ihn zweimal, und das
 * faellt niemandem auf - der Zaehler steigt dann nur doppelt so schnell.
 *
 * <p><b>Die Marke gilt fuer ABBAUEN UND ERNTEN.</b> Der naheliegende Entwurf - "Ernten
 * fragt die Marke nicht, weil Saat gesetzt wird" - macht einen einzigen Kuerbis in einem
 * Ein-Block-Loch zur Maschine. Dass ein von Hand gesaetes Feld trotzdem zahlt, besorgt die
 * andere Seite: Die {@link Setzwache} markiert SAATGUT nicht
 * ({@link Verdienstwerte#PFAD_SAATGUT}). Eine Regel, eine Ausnahme, und die Ausnahme steht
 * in der Einstellungsdatei.
 *
 * <p><b>Beim Toeten ist der Entstehungsgrund die Marke.</b> Nur ein Wesen aus einem Grund
 * der Weissliste zahlt ({@link Verdienstwerte#PFAD_SPAWNGRUENDE}). Das loest zwei Maschen
 * auf einmal: Ein gezuechtetes Kalb zahlt nicht zweimal - einmal die Zucht, einmal der Tod
 * -, und eine Spawnerfarm zahlt gar nicht.
 *
 * <p><b>Die Berufe kommen aus dem Zwischenspeicher</b>
 * ({@code ZwischenspeicherBerufe}), nicht aus der Datenbank: Eine Abfrage je Block waere
 * genau der Fehler, gegen den der Zwischenspeicher vor dem Lager schon gebaut ist. Dass
 * ein {@code /beruf waehlen} sofort wirkt, besorgt dessen Leerung bei jedem Schreibzug.
 *
 * <p><b>Was NICHT hier haengt</b>, benannt statt stillschweigend offen: Der Reeder soll
 * laut Plan 9.1 an Seehandel und Bootsrennen verdienen, der Mueller an Vorratshaltung, der
 * Gerber an Ruestung und der Architekt am Auftragsbau. Das sind keine Ereignisse, sondern
 * eigene Systeme - sie kommen mit ihrem eigenen Bauschritt. Bis dahin verdienen diese vier
 * an den Taetigkeiten, die es gibt.
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

    /**
     * Woher die Weissliste und die Erntesaetze kommen.
     *
     * <p>Derselbe Stand wie beim Sammler und keine eigene Abschrift: Ein Neuladen tauscht
     * ihn als EIN Stueck, und beide sehen danach dieselben Zahlen. Zwei Abschriften liefen
     * auseinander, und zwar in der Mitte einer Rechnung.
     */
    private final Wirtschaftsstand wirtschaft;

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
                   Berufsablage berufe, Wirtschaftsstand wirtschaft, Set<String> welten,
                   Sprache sprache, Logger protokoll) {
        if (taktquelle == null || buch == null || marken == null || berufe == null
                || wirtschaft == null || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("Taktquelle, Sammler, Marken,"
                    + " Berufsablage, Wirtschaftsstand, Sprache und Protokoll sind"
                    + " Pflicht");
        }
        if (welten == null || welten.isEmpty()) {
            throw new IllegalArgumentException("Ohne Welten verdient niemand etwas - eine"
                    + " leere Menge ist ein Zweig, der nie etwas tut");
        }
        this.taktquelle = taktquelle;
        this.buch = buch;
        this.marken = marken;
        this.berufe = berufe;
        this.wirtschaft = wirtschaft;
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
     * Ein abgebauter Block - Ernte ODER Abbau, und nie beides.
     *
     * <p>Welche der beiden es ist, entscheidet die LOHNLISTE und keine zweite Liste im
     * Programm: Steht fuer dieses Material ein Erntesatz darin, ist es eine Ernte. So
     * haengt die Entscheidung an der Datei, die der Betreiber pflegt, und nicht an einer
     * Abschrift, die mit ihr auseinanderlaeuft.
     *
     * <p>Eine UNREIFE Pflanze bringt nichts, und sie wird auch nicht zum Abbau: Wer
     * gesaeten Weizen umschlaegt, hat nichts geerntet. Das ist die sichere Richtung und
     * zugleich die einzige, die ein Feld nicht zur Maschine macht.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        Block block = ereignis.getBlock();
        String name = namenVon(block.getType());
        boolean ernte = werte().loehne().materialien(Taetigkeit.ERNTEN).contains(name);
        if (ernte && !istReif(block)) {
            return;
        }
        verdiene(ereignis.getPlayer(), ortVon(block),
                ernte ? Taetigkeit.ERNTEN : Taetigkeit.ABBAUEN, name, 1);
    }

    /**
     * Ein gefangener Fisch.
     *
     * <p>Drei Tore, und jedes schliesst etwas anderes: Der ZUSTAND
     * ({@link PlayerFishEvent.State#CAUGHT_FISH}) laesst die sieben anderen heraus, in
     * denen {@code getCaught()} null ist. Das OFFENE WASSER laesst das Ein-Block-Loch
     * heraus, an dem ein Klickautomat Tag und Nacht angelt. Und {@code instanceof Item}
     * laesst den gehakten Tintenfisch heraus - der ist kein Fang, und ein Cast darauf
     * waere eine ClassCastException aus einem MONITOR-Zweig, je Angelwurf erneut.
     *
     * <p>Dass ein Automat im offenen Wasser trotzdem angeln kann, faengt die harte
     * Stundenmenge: Mehr als ein Mensch in einer Stunde schafft, zahlt nicht.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimFischen(PlayerFishEvent ereignis) {
        if (ereignis.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!ereignis.getHook().isInOpenWater()) {
            return;
        }
        if (!(ereignis.getCaught() instanceof Item gegenstand)) {
            return;
        }
        verdiene(ereignis.getPlayer(), null, Taetigkeit.FISCHEN,
                namenVon(gegenstand.getItemStack().getType()), 1);
    }

    /**
     * Ein getoetetes Wesen - nur ein natuerlich entstandenes zahlt.
     *
     * <p>Der Toeter kommt aus {@code getEntity().getKiller()} und NICHT aus der
     * Schadensquelle, und das ist mehr als Bequemlichkeit: Es ist dieselbe Zuschreibung,
     * nach der das Spiel selbst Beute und Erfahrung verteilt - ein Pfeil und ein Trank mit
     * Spielerbesitzer sind drin, Fall, Lava und der Riss eines zahmen Wolfs nicht.
     * {@code getDamageSource().getDamageType()} waere ausserdem ohne laufenden Server nicht
     * zu lesen (die Konstanten schlagen in der Registry nach und werfen), und dann liesse
     * sich genau dieser Zweig nicht pruefen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimToeten(EntityDeathEvent ereignis) {
        LivingEntity gefallen = ereignis.getEntity();
        Player toeter = gefallen.getKiller();
        if (toeter == null) {
            return;
        }
        if (!werte().zahltAusGrund(grundVon(gefallen))) {
            // Gezuechtet, gespawnt, aus einem Ei gerufen: Das Wesen ist schon bezahlt oder
            // es ist aus einer Maschine. Dieselbe Rolle wie die Blockmarke.
            return;
        }
        verdiene(toeter, null, Taetigkeit.TOETEN, namenVon(gefallen.getType()), 1);
    }

    /**
     * Ein gezuechtetes Wesen.
     *
     * <p>{@code instanceof Player} erledigt zwei Faelle in einem Tor: Dorfbewohner
     * vermehren sich ohne Zuechter ({@code getBreeder()} ist dann null), und der
     * Rueckgabetyp ist nur ein {@code LivingEntity} - ein Cast auf {@code Player} waere
     * eine ClassCastException mit Ansage.
     *
     * <p>Der Name ist das JUNGE ({@code getEntity()}) und nicht ein Elternteil: Pferd und
     * Esel ergeben ein Maultier, und das traegt seinen eigenen Satz. Dass das Junge selbst
     * nie wieder zuchtfaehig ist, ist dabei kein Zufall, sondern der Grund, warum diese
     * Zeile eine eigene Zahl verdient.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimZuechten(EntityBreedEvent ereignis) {
        if (!(ereignis.getBreeder() instanceof Player wer)) {
            return;
        }
        verdiene(wer, null, Taetigkeit.ZUECHTEN,
                namenVon(ereignis.getEntity().getType()), 1);
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
        verdiene(wer, null, Taetigkeit.SETZEN, namenVon(art), neueMarken);
    }

    /**
     * Der gemeinsame Hals - Welt, Marke, Berufe, Lohn, Handbalken.
     *
     * <p>Alle sechs Taetigkeiten gehen durch diese eine Stelle, und darum heisst der
     * Parameter NAME und nicht Material: Unter {@code abbauen} ist es ein Block, unter
     * {@code toeten} eine Wesensart. Was es ist, hat der Zweig schon uebersetzt; hier
     * zaehlt nur, dass es derselbe Weg ist.
     *
     * <p>Die Reihenfolge ist nach Kosten geordnet, und das ist Absicht: zuerst die Welt
     * (ein Nachschlag in einer kleinen Menge), dann die Marke (ein Bitfeld im geladenen
     * Chunk), dann die Berufe (ein Nachschlag im Zwischenspeicher), und erst zuletzt die
     * Rechnung. Ein Block in einer fremden Welt kostet damit genau einen Vergleich.
     *
     * @param ort        der Ort fuer die Markenfrage, oder {@code null}, wenn es keinen
     *                   gibt - beim Setzen, Fischen, Toeten und Zuechten
     * @param wiederholt wie oft diese Taetigkeit gerechnet wird - bei einer Tuer zwei
     */
    private void verdiene(Player wer, Blockort ort, Taetigkeit taetigkeit, String name,
                          int wiederholt) {
        String welt = wer.getWorld().key().asString();
        if (!welten.contains(welt)) {
            return;
        }
        if (ort != null && marken.istMarkiert(ort)) {
            // Gesetzter Boden zahlt nichts - weder beim Abbauen noch beim Ernten. "Ein
            // Block zahlt nur einmal"; dass ein gesaetes Feld trotzdem zahlt, besorgt die
            // Saatgutliste in der Setzwache.
            return;
        }
        UUID kennung = wer.getUniqueId();
        Set<String> gehalten = gehaltene(kennung);
        if (gehalten.isEmpty()) {
            return;
        }
        for (int mal = 0; mal < wiederholt; mal++) {
            buch.verdiene(kennung, gehalten, taetigkeit, name);
        }
        zeige(wer, kennung);
    }

    /**
     * Ob diese Pflanze reif ist.
     *
     * <p>Drei Faelle, und die Reihenfolge entscheidet:
     *
     * <ol>
     *   <li><b>Beeren vor Alter.</b> {@code CaveVines} ist BEIDES - {@code Ageable} und
     *       {@code CaveVinesPlant} -, und sein Alter ist die Laenge der Ranke, nicht die
     *       Reife. Wer hier das Alter zuerst fragt, zahlt fuer eine Ranke ohne
     *       Gluehbeeren.</li>
     *   <li><b>Alter, wo es eines gibt.</b> Weizen, Karotten, Kakao und die uebrigen
     *       Feldfruechte sind reif am Hoechstalter.</li>
     *   <li><b>Sonst reif.</b> Kuerbis, Melone, Zuckerrohr, Bambus, Kaktus und Seetang
     *       haben keinen Reifezustand - dort ist die MARKE die Bremse, und die ist schon
     *       gefragt worden.</li>
     * </ol>
     *
     * <p><b>{@code Ageable} heisst nicht Pflanze</b>: Feuer traegt es auch. Das ist
     * ungefaehrlich, weil nur Materialien hier ankommen, fuer die ein Erntesatz in der
     * Liste steht - und wer {@code fire} dort eintraegt, bekommt vom Leser eine Klage,
     * sobald er es als Wesensart verwechselt, und sonst einen Satz, der nie greift.
     */
    private static boolean istReif(Block block) {
        BlockData daten = block.getBlockData();
        if (daten instanceof CaveVinesPlant ranke) {
            return ranke.hasBerries();
        }
        if (daten instanceof Ageable alter) {
            return alter.getAge() >= alter.getMaximumAge();
        }
        return true;
    }

    /**
     * Woraus dieses Wesen entstanden ist, kleingeschrieben - oder {@code null}.
     *
     * <p>Ein {@code null} heisst "kein Lohn", und das ist die sichere Richtung: Wer den
     * Grund nicht kennt, kann nicht wissen, ob das Wesen schon bezahlt wurde.
     */
    private static String grundVon(LivingEntity wesen) {
        CreatureSpawnEvent.SpawnReason grund = wesen.getEntitySpawnReason();
        return grund == null ? null : grund.name().toLowerCase(Locale.ROOT);
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
     * Der Handbalken - hoechstens einmal je Sekunde, und drei Saetze zur Wahl.
     *
     * <p>Die Drossel steht im Sammler: Sie ist Zustand je Spieler. Hier bleibt die Wahl
     * zwischen den Saetzen, und die Reihenfolge ist die der Reichweite: Der Tagesdeckel
     * gilt fuer alles, die Stundenmenge nur fuer eine Taetigkeit, und wer weder an der
     * einen noch an der anderen Grenze steht, liest seinen Betrag.
     *
     * <p>Dass die Grenzen UEBERHAUPT etwas sagen, ist der Punkt: Ein Spieler, der
     * weiterhaut und nichts mehr bekommt, sucht den Fehler sonst bei sich.
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
        Taetigkeit grenze = buch.anDerGrenze(kennung);
        if (grenze != null) {
            sprache.sendeHandbalken(wer, BerufSatz.VERDIENST_STUNDENMENGE,
                    Einsatz.von("taetigkeit", sprache.wort(
                            BerufSatz.taetigkeitswort(grenze), Einsatz.keine())));
            return;
        }
        if (summe > 0L) {
            sprache.sendeHandbalken(wer, BerufSatz.VERDIENST_HANDBALKEN,
                    Einsatz.von("betrag", sprache.pfennige(summe)));
        }
    }

    /** Die Werte, die JETZT gelten - aus demselben Stand wie beim Sammler. */
    private Verdienstwerte werte() {
        return wirtschaft.werte().verdienst();
    }

    /** Der Name, wie er in der Lohnliste steht - kleingeschrieben. */
    private static String namenVon(Enum<?> art) {
        return art.name().toLowerCase(Locale.ROOT);
    }

    private static Blockort ortVon(Block block) {
        return new Blockort(block.getWorld().key().asString(),
                block.getX(), block.getY(), block.getZ());
    }

    /** Die Fenster, die ein Spieler gerade offen hat - nur fuer die Proben. */
    List<String> gezaehlteArten(UUID wer) {
        return buch.gezaehlteArten(wer);
    }
}
