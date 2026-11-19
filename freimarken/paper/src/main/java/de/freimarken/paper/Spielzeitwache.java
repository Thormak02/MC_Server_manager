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
     * Der Mitfahrer fuer den Rangaufstieg [Schritt 12 Teil D].
     *
     * <p>JE ANWESENDEM SPIELER, wie der Bonus und aus demselben Grund: Ein Rang gehoert
     * einem Spieler. Und NACH dem Abrechnen, ebenfalls wie der Bonus - die taetigen
     * Sekunden dieses Taktes entstehen genau dort, und auf die wartet der Aufstieg.
     *
     * <p>Darf NICHT {@code null} sein, anders als die fuenf Laeufe darunter: Es gibt
     * keinen Betrieb ohne Raenge. Ist die Leiter leer oder {@code raenge.an} aus, sagt
     * das die Wache selbst - sie tut dann nichts, und das ist eine Entscheidung der
     * {@code config.yml} und keine der Verdrahtung.
     */
    private final Rangwache rangwache;

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

    /**
     * Der fuenfte Mitfahrer: der Besuchsstempel der Freistaedte [Teil E4].
     *
     * <p>JE ANWESENDEM SPIELER, wie der Bonus und anders als die drei dazwischen: Er
     * fragt, WO einer steht. Einmal je Schlag waere die Frage nicht zu stellen.
     *
     * <p><b>Darf {@code null} sein</b>, wie die Raeumungen: Fehlt die Freistaedtewelt in
     * der {@code config.yml}, entsteht gar kein Freistadtbuch - dann gibt es keine
     * Stadt, die man besuchen koennte, und der Start sagt das mit eigenen Zeilen.
     *
     * <p>VOR dem Bonus und nicht danach, obwohl beide je Spieler laufen: Der Stempel
     * schreibt hoechstens einmal je Stadt und Tag, der Bonus bucht Geld. Was selten
     * schreibt, soll nicht hinter dem warten, was haeufig bucht - und wenn der Bonus
     * wirft, ist der Stempel dieses Spielers schon gesetzt.
     */
    private final Besuchslauf besuchslauf;

    /**
     * Der sechste Mitfahrer: der taegliche Verfall der Geisterstaedte [Teil E5].
     *
     * <p>EINMAL je Taktschlag und nicht je Spieler, wie die Ladenmiete und aus demselben
     * Grund: Der Lauf geht ueber alle STAEDTE einer Welt, nicht ueber die Anwesenden -
     * und genau das ist sein Zweck. Eine Geisterstadt wird gerade dadurch zu einer, dass
     * niemand kommt.
     *
     * <p><b>Darf {@code null} sein</b>, wie der Besuchsstempel und die zwei Raeumungen:
     * Ohne Freistaedtewelt gibt es keine Stadt, die verfallen koennte.
     *
     * <p>NACH dem Besuchsstempel, und das ist wesentlich: Jener laeuft je Spieler und
     * setzt heute besuchte Staedte auf heute. Liefe der Verfall davor, verfiele eine
     * Stadt, in der in derselben Minute jemand steht - und wachte eine Zeile spaeter
     * wieder auf.
     */
    private final Verfallslauf verfallslauf;

    /**
     * Der siebte Mitfahrer: die taegliche Ladengebuehr [Teil E6].
     *
     * <p>EINMAL je Taktschlag, wie die Ladenmiete und der Verfall: Der Lauf geht ueber
     * alle SHOPS, nicht ueber die Anwesenden - und genau das ist sein Zweck. Ein Laden
     * zahlt, ob sein Besitzer da ist oder nicht; waere er an Anwesenheit gebunden, waere
     * Wegbleiben der Weg, ihn kostenlos zu halten.
     *
     * <p><b>Darf {@code null} sein</b>, und hier gleich aus zwei Gruenden: ohne
     * Freistaedtewelt gibt es keine Stadt, und ohne lesbare Shopablage keinen Laden.
     *
     * <p>NACH dem Verfall, und das ist wesentlich: Jener kann eine Stadt auf verfallen
     * setzen. Liefe die Gebuehr davor, zahlten Laeden einen Tag lang an eine Stadt, die
     * im selben Takt aufhoert - und das ist die unangenehmere Reihenfolge, weil Geld
     * fliesst.
     */
    private final Ladengebuehrlauf ladengebuehrlauf;

    private final Wirtschaftsstand wirtschaft;
    private final Logger protokoll;

    /** Der laufende Zeitgeber - {@code null}, solange keiner steht. */
    private BukkitTask zeitgeber;

    Spielzeitwache(JavaPlugin plugin, Spielzeitbuch buch, Bonuswache bonuswache,
                   Rangwache rangwache, Mietwache mietwache,
                   Blockraeumung blockraeumung,
                   Truhenlauf truhenlauf, Besuchslauf besuchslauf,
                   Verfallslauf verfallslauf, Ladengebuehrlauf ladengebuehrlauf,
                   Wirtschaftsstand wirtschaft, Logger protokoll) {
        if (rangwache == null) {
            // DIE EINE PRUEFUNG IN DIESEM KONSTRUKTOR, und sie steht hier, weil das
            // Javadoc des Feldes sie zusagt: Die fuenf Laeufe darunter DUERFEN null sein,
            // der Rang nicht. Ohne diese Zeile waere eine vergessene Angabe kein Fehler
            // beim Bauen, sondern ein NullPointer in JEDEM Taktschlag - und damit der
            // Verlust der Spielzeit aller Spieler, denn der Fang um den Schlag meldet nur
            // und der Zeitgeber laeuft weiter.
            //
            // Eine Zusage im Javadoc ohne Pruefung im Quelltext ist keine Zusage.
            throw new IllegalArgumentException("Ohne Rangwache gibt es keinen Takt. Sie"
                    + " darf - anders als die fuenf Laeufe - nicht fehlen: Ist die"
                    + " Leiter leer oder raenge.an aus, sagt die Wache das selbst und"
                    + " tut nichts.");
        }
        this.plugin = plugin;
        this.buch = buch;
        this.bonuswache = bonuswache;
        this.rangwache = rangwache;
        this.mietwache = mietwache;
        this.blockraeumung = blockraeumung;
        this.truhenlauf = truhenlauf;
        this.besuchslauf = besuchslauf;
        this.verfallslauf = verfallslauf;
        this.ladengebuehrlauf = ladengebuehrlauf;
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
                // DER BESUCHSSTEMPEL ZUERST [Teil E4]: Er schreibt hoechstens einmal
                // je Stadt und Tag, der Bonus bucht Geld. Wirft der Bonus trotz seines
                // eigenen Fangs, ist der Stempel dieses Spielers schon gesetzt.
                //
                // Der null-Fall ist der Betrieb OHNE Freistaedtewelt, nicht ein
                // Versehen - dann gibt es keine Stadt zu besuchen.
                if (besuchslauf != null) {
                    besuchslauf.imTakt(spieler);
                }
                bonuswache.imTakt(spieler);
                // DER RANG ZULETZT in diesem Durchgang [Teil D]: Er liest die taetige
                // Zeit, die das Abrechnen oben gerade geschrieben hat, und er liest die
                // Parzellenzahl und den Beruf. Nichts davon aendert er - er haengt
                // Rechte an und sendet. Steht er VOR dem Bonus, kann eine Ausnahme von
                // dort den Bonus dieses Taktes kosten; steht er danach, kostet eine
                // Ausnahme von hier nur den Aufstieg, und den holt der naechste Takt
                // nach.
                rangwache.imTakt(spieler);
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
            // UND DER VERFALL, ganz am Ende [Teil E5]. Zuletzt aus demselben Grund wie
            // die Raeumungen davor: Sein Ausfall kostet nichts - der naechste Takt holt
            // ihn nach, und eine Geisterstadt, die einen Tag laenger steht, schadet
            // niemandem. Alles davor schreibt Zeit oder bucht Geld.
            //
            // NACH dem Besuchsstempel, der weiter oben je Spieler laeuft: Liefe er
            // davor, verfiele eine Stadt, in der in derselben Minute jemand steht.
            if (verfallslauf != null) {
                verfallslauf.imTakt();
            }
            // UND DIE TAGESGEBUEHR, nach dem Verfall [Teil E6]: Jener kann eine Stadt
            // auf verfallen setzen, und dann sollen ihre Laeden an diesem Tag nicht
            // mehr an sie zahlen. Ab Werk tut dieser Zweig nichts - die Tagesgebuehr
            // ist 0, und das steht in der config.yml mit Begruendung.
            if (ladengebuehrlauf != null) {
                ladengebuehrlauf.imTakt();
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
