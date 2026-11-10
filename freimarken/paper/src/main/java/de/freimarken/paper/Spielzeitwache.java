package de.freimarken.paper;

import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Die Bruecke zwischen dem Spiel und dem {@link Spielzeitbuch}: Beitritt, Abmelden, Takt.
 *
 * <p>Eine eigene Wache und kein Zweig in {@link Ankunftswache}, aus zwei Gruenden. Die
 * Ankunftswache beantwortet eine einzige Frage - was gilt fuer einen Spieler, der gerade
 * irgendwo auftaucht - und haelt dafuer keinen Zustand; sie wird beim Abschalten nicht
 * gefragt. Die Spielzeit haelt eine Karte, die beim Abschalten abgerechnet werden MUSS, und
 * sie stellt einen Zeitgeber. Laege sie dort, muesste {@code onDisable} eine Klasse
 * abrechnen, deren Name nichts davon sagt. Das Hausmuster fuer genau diesen Fall ist
 * {@link Aufwaermzeit}: Zuhoerer mit eigenem Zustand, eigener Zeitplanung und einem
 * Aufraeumer, den das Abschalten ruft.
 *
 * <p><b>Alles laeuft im HAUPTFADEN.</b> Der Zeitgeber ist
 * {@code getScheduler().runTaskTimer(...)} und nicht {@code getAsyncScheduler()}: Die
 * Datenschicht haelt EINE JDBC-Verbindung, auf der MariaKasse und MariaLager
 * {@code autoCommit} umschalten. Ein zweiter Faden committete dort die halbe Geldbuchung
 * des Hauptfadens mit - das waere nicht eine verlorene Spielminute, sondern die gebrochene
 * Zusage des Buchungsbuchs.
 *
 * <p><b>Der Takt zaehlt die Uhr, nicht die Takte.</b> Gerechnet wird die Differenz zweier
 * Uhrablesungen. Ein verspaeteter Takt zaehlt deshalb richtig, und ein lahmender Server
 * verliert nichts - Ticks laufen unter Last langsamer als die Wanduhr.
 */
final class Spielzeitwache implements Listener {

    /**
     * Wie viele Serverticks eine Sekunde sind.
     *
     * <p>Keine Einstellung, sondern eine Festlegung von Minecraft: 20 Ticks je Sekunde, ein
     * Tick alle 50 ms. Darum steht sie im Programm und nicht in der {@code config.yml} -
     * eine andere Zahl hier machte nicht den Takt anders, sondern die Rechnung falsch. Aus
     * dem eingestellten Takt von 60 s werden damit 1.200 Ticks.
     */
    private static final long TICKS_JE_SEKUNDE = 20L;

    private final JavaPlugin plugin;
    private final Spielzeitbuch buch;

    /**
     * Der erste Mitfahrer: der Tagesbonus [Teil H].
     *
     * <p>Hier und nicht mit einem eigenen Zeitgeber: Plan 8.8 schliesst "ein zweiter
     * Zeitgeber fuer irgendetwas anderes" ausdruecklich aus. Der Bonus braucht auch kein
     * eigenes Fenster - er fragt nur, ob heute schon gezahlt ist.
     *
     * <p>Klassen und keine Schnittstelle, auch jetzt mit zwei Mitfahrern: Eine
     * Schnittstelle waere erst richtig, wenn ein Mitfahrer austauschbar WERDEN soll, und
     * das will keiner von beiden. Was sie unterscheidet, ist ohnehin zu viel fuer eine
     * gemeinsame Form - der eine wird je anwesendem Spieler gerufen, der andere einmal
     * je Schlag.
     */
    private final Bonuswache bonuswache;

    /**
     * Der zweite Mitfahrer: der taegliche Lauf der Ladenmiete [Teil C].
     *
     * <p>EINMAL je Taktschlag und nicht je Spieler, anders als der Bonus. Der Bonus
     * gehoert einem Spieler; die Ladenmiete gehoert allen Vertraegen, und die laufen
     * weiter, ob ihr Mieter da ist oder nicht. Genau das ist ihr Zweck - waere sie an
     * Anwesenheit gebunden, waere Wegbleiben der Weg, einen Stand kostenlos zu halten.
     */
    private final Mietwache mietwache;

    /**
     * Der dritte Mitfahrer: das Aufraeumen des Blockbuchs.
     *
     * <p>EINMAL je Taktschlag, wie die Ladenmiete, und aus demselben Grund: Geraeumt wird
     * eine Tabelle, nicht ein Spieler.
     *
     * <p><b>Darf {@code null} sein</b> - der einzige Mitfahrer, bei dem das erlaubt ist.
     * Das Blockbuch braucht eine Ablage, und ohne Datenbank und ohne eingetragene Taten
     * gibt es nichts zu raeumen. Ein Pflichtfeld zwaenge das Plugin, eine Raeumung zu
     * bauen, die nie etwas tut.
     *
     * <p>Hier und nicht an der {@link Blockwache}, obwohl die ihren eigenen Zeitgeber
     * hat: Jene wird bei einer leeren Auswahl GAR NICHT angemeldet. Wer das Buch
     * abschaltet, will seine alten Zeilen trotzdem nicht fuer immer behalten - und sie
     * waeren sonst unloeschbar, weil niemand mehr aufraeumt.
     */
    private final Blockraeumung blockraeumung;

    /**
     * Der vierte Mitfahrer: das taegliche Leeren der Freien Truhe.
     *
     * <p>EINMAL je Taktschlag, wie die Ladenmiete und das Aufraeumen, und aus demselben
     * Grund: Geleert wird eine Kiste, nicht ein Spieler.
     *
     * <p><b>Darf ebenfalls {@code null} sein</b>, aus demselben Grund wie die
     * {@link Blockraeumung}: Ohne Ablage gibt es keinen Tagesstand und nichts zu raeumen.
     *
     * <p>Hier und nicht an der {@link Truhenwache}: Jene wird ohne eingetragene Stelle
     * GAR NICHT angemeldet. Wer die letzte Stelle aus der {@code config.yml} nimmt, will
     * seine alten Tageszeilen trotzdem nicht fuer immer behalten.
     */
    private final Truhenlauf truhenlauf;

    private final Wirtschaftsstand wirtschaft;
    private final Logger protokoll;

    /** Der laufende Zeitgeber - {@code null}, solange keiner steht. */
    private BukkitTask zeitgeber;

    Spielzeitwache(JavaPlugin plugin, Spielzeitbuch buch, Bonuswache bonuswache,
                   Mietwache mietwache, Blockraeumung blockraeumung,
                   Truhenlauf truhenlauf, Wirtschaftsstand wirtschaft, Logger protokoll) {
        this.plugin = plugin;
        this.buch = buch;
        this.bonuswache = bonuswache;
        this.mietwache = mietwache;
        this.blockraeumung = blockraeumung;
        this.truhenlauf = truhenlauf;
        this.wirtschaft = wirtschaft;
        this.protokoll = protokoll;
    }

    /**
     * Der eingestellte Takt in Serverticks.
     *
     * <p>Eine eigene Methode, damit die Umrechnung geprueft werden kann, ohne einen Server
     * zu starten. Der Taktabstand ist auf 10 bis 600 Sekunden begrenzt, also sind es 200 bis
     * 12.000 Ticks - kein Ueberlauf in Sicht.
     */
    static long ticks(Duration takt) {
        if (takt == null) {
            throw new IllegalArgumentException("Ohne Takt gibt es keinen Zeitgeber");
        }
        return takt.toSeconds() * TICKS_JE_SEKUNDE;
    }

    /**
     * Stellt den Zeitgeber und nimmt die schon anwesenden Spieler auf.
     *
     * <p>Die zweite Haelfte ist nicht Zierde: Beim Abschalten und Wiedereinschalten des
     * Plugins im Betrieb - {@code /reload} oder ein Plugin-Verwalter - feuert fuer die
     * anwesenden Spieler kein {@code PlayerJoinEvent} mehr. Ohne diesen Durchgang zaehlte
     * deren Zeit erst wieder, wenn sie sich einmal abmelden.
     *
     * <p>Der erste Takt kommt eine ganze Taktlaenge spaeter und nicht sofort: Beim Start
     * ist seit den gerade gesetzten Marken nichts verstrichen, er schriebe also eine leere
     * Liste - ein Schreibzugriff in der Sekunde, in der das Plugin hochkommt, fuer nichts.
     */
    void starte() {
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            buch.beginne(spieler.getUniqueId());
        }
        Zeitwerte zeit = zeitwerte();
        long takte = ticks(zeit.takt());
        zeitgeber = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::schlag, takte, takte);
        protokoll.info("Spielzeittakt steht: alle " + zeit.taktSekunden() + " s ("
                + takte + " Ticks) im Hauptfaden, " + buch.offeneMarken()
                + " Spieler schon anwesend. Untaetigkeit zaehlt nicht mehr ab "
                + (zeit.untaetigkeitZaehltMit() ? "nie - die Regel ist aus"
                        : zeit.untaetigMinuten() + " min") + ".");
    }

    /**
     * Meldet den Zeitgeber ab und rechnet jede offene Sitzung ab - VOR dem Schliessen der
     * Datenbank.
     *
     * <p>Die Reihenfolge ist Pflicht und nicht Geschmack: Erst der Zeitgeber, damit nicht
     * mitten im Abrechnen noch ein Takt dazukommt; dann die offenen Sitzungen, solange die
     * Verbindung noch steht. Umgekehrt waere die letzte Sitzung jedes anwesenden Spielers
     * verloren, und eine Ablage, die nach {@code close()} noch schreibt, baut eine neue
     * Verbindung auf, die niemand mehr schliesst.
     *
     * <p><b>Die Erfolgszeile nennt, was WIRKLICH geschrieben wurde</b>, und nicht die Zahl
     * der Marken. Sie steht auf der Abnahmeliste des Betreibers als Beweis fuer "0 Verlust":
     * Scheitert das Schreiben, meldet das {@link Spielzeitbuch} den Verlust und kehrt normal
     * zurueck - eine Erfolgszeile unter dieser Verlustzeile waere die Zeile, die abgehakt
     * wird, waehrend die Zeit weg ist. Die Zahl kann auch ohne Verlust kleiner sein als die
     * Zahl der Sitzungen: Wer den letzten Takt untaetig war oder weniger als eine Sekunde
     * offen hatte, hat nichts zu schreiben. Darum nennt die Zeile beide Zahlen.
     */
    void beende() {
        if (zeitgeber != null) {
            zeitgeber.cancel();
            zeitgeber = null;
        }
        int offene = buch.offeneMarken();
        int geschrieben;
        // Gefangen wie im Taktschlag, aber aus einem anderen Grund: Der Aufrufer schliesst
        // danach die Datenbank. Eine durchgelassene Ausnahme liesse die Verbindung offen
        // stehen - der Preis fuer eine verlorene letzte Minute waere dann ein Leck.
        try {
            geschrieben = buch.schliesseAlles(untaetigkeiten());
        } catch (Throwable fehler) {
            protokoll.log(Level.SEVERE, "Die letzten " + offene + " offenen Sitzung(en)"
                    + " liessen sich beim Abschalten nicht abrechnen.", fehler);
            return;
        }
        if (offene > 0) {
            protokoll.info("Spielzeit abgerechnet: " + geschrieben + " von " + offene
                    + " offenen Sitzung(en) weggeschrieben.");
        }
    }

    @EventHandler
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        buch.beginne(ereignis.getPlayer().getUniqueId());
    }

    /**
     * Beim Abmelden wird abgerechnet und die Marke entfernt.
     *
     * <p>Nur dieses Ereignis und ausdruecklich KEIN {@code PlayerKickEvent}: Das ist
     * abbrechbar, und wer dort abrechnet, rechnet auch dann ab, wenn ein anderes Plugin den
     * Kick danach zurueckholt. Ein Kick endet ohnehin hier, mit
     * {@code QuitReason.KICKED}.
     */
    @EventHandler
    public void beimAbmelden(PlayerQuitEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        buch.schliesse(spieler.getUniqueId(), spieler.getIdleDuration());
    }

    /**
     * Wie lange dieser Spieler schon untaetig ist - fuer die Anzeige.
     *
     * <p>Aus {@code Player.getIdleDuration()} und keinem eigenen Zuhoerer: Der Server fuehrt
     * diese Zahl selbst. Ein eigener Bewegungszuhoerer waere ein zweiter Zustand fuer
     * dieselbe Frage, und er muesste dabei die Faelle nachbauen, die der Server schon kennt.
     */
    static Duration untaetigkeitVon(Player spieler) {
        return spieler.getIdleDuration();
    }

    /**
     * Ein Taktschlag.
     *
     * <p>Faengt {@link Throwable} und nicht bloss {@code RuntimeException}, und das ist der
     * wichtigste Satz dieser Klasse: Eine durchgelassene Ausnahme kann alle weiteren
     * Ausfuehrungen eines wiederkehrenden Zeitgebers beenden. Der Verlust waere dann nicht
     * eine Minute, sondern die ganze Spielzeit bis zum naechsten Neustart - und zwar lautlos,
     * weil der Spielzeit keine Summenprobe widerspricht. Das Schreiben selbst faengt seinen
     * Fehler schon im {@link Spielzeitbuch}; hier geht es um alles davor.
     */
    private void schlag() {
        try {
            buch.schreibeFort(untaetigkeiten());
            // NACH dem Abrechnen: Ist eine Mindestzeit eingestellt, zaehlt sie taetige
            // Minuten, und die entstehen genau in diesem Abrechnen. Davor gefragt, fehlte
            // dem Bonus immer der letzte Taktschlag.
            //
            // In DEMSELBEN try: Eine Ausnahme aus dem Bonus darf den Zeitgeber nicht
            // beenden - der Verlust waere dann nicht ein Bonus, sondern die Spielzeit
            // aller Spieler bis zum naechsten Neustart. Tagesbonus.pruefe faengt seine
            // Fehler selbst; dies ist die zweite Schranke.
            for (Player spieler : plugin.getServer().getOnlinePlayers()) {
                bonuswache.imTakt(spieler);
            }
            // EINMAL und nicht je Spieler [Teil C]: Der Lauf geht ueber alle Vertraege,
            // nicht ueber alle Anwesenden. Nach dem Bonus, weil er Geld ABBUCHT - wer
            // heute seinen Bonus bekommt, soll ihn fuer die Miete einsetzen koennen, und
            // nicht geraeumt werden, waehrend das Geld schon unterwegs ist.
            //
            // In DEMSELBEN try: Eine Ausnahme von hier darf den Zeitgeber nicht beenden -
            // der Verlust waere dann die Spielzeit aller Spieler bis zum naechsten
            // Neustart. Mietlauf faengt seine Fehler selbst; dies ist die zweite
            // Schranke.
            mietwache.imTakt();
            // ZULETZT, und das ist Absicht: Aufraeumen ist das einzige hier, dessen
            // Ausfall nichts kostet - der naechste Takt holt es nach. Alles davor
            // schreibt Zeit oder bucht Geld, und das soll nicht hinter einem DELETE
            // warten, das bei einem Rueckstand von Wochen hunderte Zeilen loescht.
            //
            // In DEMSELBEN try, aus demselben Grund wie bei den beiden davor: Eine
            // Ausnahme von hier darf den Zeitgeber nicht beenden - der Verlust waere
            // dann die Spielzeit aller Spieler bis zum naechsten Neustart. Blockraeumung
            // faengt ihre Fehler selbst; dies ist die zweite Schranke.
            //
            // Der null-Fall ist der Normalbetrieb ohne Datenbank, nicht ein Versehen:
            // Ohne Ablage gibt es kein Blockbuch und nichts zu raeumen.
            if (blockraeumung != null) {
                blockraeumung.imTakt();
            }
            // Und die Freie Truhe, ganz am Ende: Sie leert eine Kiste und loescht
            // Tageszeilen, und beides holt der naechste Takt nach. In DEMSELBEN try,
            // aus demselben Grund wie die drei davor - Truhenlauf faengt seine Fehler
            // selbst, dies ist die zweite Schranke.
            if (truhenlauf != null) {
                truhenlauf.imTakt();
            }
        } catch (Throwable fehler) {
            protokoll.log(Level.SEVERE, "Der Spielzeittakt ist in einen Fehler gelaufen."
                    + " Die Zeit dieses Taktes ist verloren; der Zeitgeber laeuft weiter.",
                    fehler);
        }
    }

    /**
     * Wer anwesend ist und wie lange er schon untaetig ist.
     *
     * <p>Gefragt wird der Server und nicht die Markenkarte: Ein Spieler, der zwischen zwei
     * Takten beigetreten ist, soll mitgezaehlt werden, und die Zeit eines Spielers, der
     * nicht mehr da ist, darf nicht weiterlaufen. Seine Marke ist beim Abmelden ohnehin
     * entfernt worden - ueber den Server zu gehen ist die zweite Schranke dagegen.
     */
    private Map<UUID, Duration> untaetigkeiten() {
        Map<UUID, Duration> karte = new LinkedHashMap<>();
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            karte.put(spieler.getUniqueId(), untaetigkeitVon(spieler));
        }
        return karte;
    }

    /**
     * Zone, Takt und Untaetigkeitsgrenze aus der einen Zelle.
     *
     * <p>Der Takt wird hier genau EINMAL gefragt, beim Stellen des Zeitgebers. Genau deshalb
     * laesst {@code Wirtschaftsstand.uebernimm} ihn stehen: Ein Neuladen, das ihn aenderte,
     * aenderte den gestellten Zeitgeber nicht - und wer ihn dabei neu stellte, haette nach
     * zwei Neuladen drei Zeitgeber.
     */
    private Zeitwerte zeitwerte() {
        return wirtschaft.werte().zeit();
    }
}
