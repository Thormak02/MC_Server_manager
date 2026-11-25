package de.freimarken.paper;

import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafbuch;
import de.freimarken.kern.Strafe;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Was eine Strafe im Spiel BEWIRKT (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p><b>Ohne diese Klasse ist der ganze Teil unsichtbar wirkungslos.</b> Der Kern prueft
 * die Rechte, die Ablage haelt die Strafe, die Bannliste zeigt sie - und bis hierher hat
 * kein gesperrter Spieler davon etwas gemerkt. Genau diese Art Luecke hat dieses Projekt
 * schon viermal getroffen: eine gebaute, gruen gepruefte Regel, die niemand verdrahtet
 * hat.
 *
 * <p><b>Drei Zweige, und jeder haelt eine andere Zusage:</b>
 * <ul>
 *   <li>{@link #beimAnmelden} - der BANN. {@link AsyncPlayerPreLoginEvent} und nicht
 *       {@code PlayerJoinEvent}: Jenes laeuft, wenn der Spieler schon drin ist, und ein
 *       Hinauswerfen danach laedt erst seine Welt. Hier wird er an der Tuer abgewiesen,
 *       bevor etwas geladen wird.</li>
 *   <li>{@link #beimSprechen} - die STUMMSCHALTUNG im Chat.</li>
 *   <li>{@link #beimBefehl} - die Stummschaltung bei den mitgesperrten BEFEHLEN. Ohne
 *       diesen Zweig ist jede Stummschaltung ueber {@code /msg} zu umgehen, und der
 *       Moderator haelt den Spieler fuer still.</li>
 * </ul>
 *
 * <p><b>Fruehe Prioritaet und {@code ignoreCancelled = false}</b>, anders als bei
 * {@link Meldewache} und {@link Messwache}: Diese Wache MESST nicht, sie BRICHT AB. Eine
 * Wache auf {@code MONITOR} darf nichts mehr verhindern - dort ist die Zeile schon
 * unterwegs.
 *
 * <p><b>Was geschieht, wenn die Datenbank nicht antwortet, ist eine Entscheidung, und sie
 * steht hier:</b> Der Spieler kommt herein, und er darf sprechen. Die Gegenrichtung waere
 * ein Server, der bei einer Datenbankstoerung fuer ALLE zu ist - aus einem Riegel gegen
 * einen Griefer wuerde ein Riegel gegen jeden. Jeder solche Fall geht als Warnung ins
 * Protokoll, denn er heisst: In diesem Augenblick wirkt keine Sperre.
 *
 * <p><b>Ein Lesezugriff je Beitritt und je Chatzeile</b>, jeweils auf einen Index
 * ({@code betroffener, art, aufgehoben_am, laeuft_ab_am}). Dieselbe Linie wie bei
 * {@link Rangwache} und der Vervollstaendigung von {@code /meldung}. Ein
 * Zwischenspeicher je Spieler waere schneller und ist eine benannte Luecke: Er waere ein
 * zweiter Stand neben der Ablage, und ein veralteter Zwischenspeicher ist eine
 * Stummschaltung, die nach dem Aufheben weiterwirkt.
 */
final class Strafwache implements Listener {

    /**
     * Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann.
     *
     * <p><b>Ein behobener Fehler [25.11.2026]:</b> Vorher rief diese Wache dreimal
     * {@link Instant#now()}. Die Proben legen ihre Strafen auf einen festen Zeitpunkt
     * mit kurzer Dauer - sie waren damit nur in einem Fenster von zehn Minuten am Tag
     * gruen, beim ersten Lauf zufaellig und beim zweiten nicht mehr. Eine Probe, die
     * von der Tageszeit abhaengt, ist keine.
     *
     * <p>Dieselbe Naht wie in {@link StrafBefehle} und {@code MeldungBefehle} - und
     * damit kommen alle Zeiten dieses Teils aus EINER Uhr, so wie es die Ablagen
     * verlangen.
     */
    interface Uhr {
        Instant jetzt();
    }

    private final Strafbuch buch;
    private final Sprache sprache;
    private final Supplier<ZoneId> zone;
    private final Meldetext.Namensgeber namen;
    private final Uhr uhr;
    private final Logger protokoll;

    Strafwache(Strafbuch buch, Sprache sprache, Supplier<ZoneId> zone,
               Meldetext.Namensgeber namen, Uhr uhr, Logger protokoll) {
        if (buch == null || sprache == null || zone == null || namen == null
                || uhr == null || protokoll == null) {
            throw new IllegalArgumentException("Die Strafwache braucht das Strafbuch,"
                    + " Sprache, die Zone, den Namensgeber, eine Uhr und das"
                    + " Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.zone = zone;
        this.namen = namen;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Die Tuer - ein gebannter Spieler kommt nicht herein.
     *
     * <p>{@link EventPriority#NORMAL} und nicht {@code MONITOR}: Hier wird abgewiesen,
     * und eine Wache auf MONITOR kann das nicht mehr. Nicht {@code LOWEST}, damit eine
     * Weissliste oder ein Wartungsmodus eines Nachbarplugins vor uns entscheiden darf -
     * wer ohnehin nicht herein darf, braucht keine Bannpruefung.
     *
     * <p>Dieser Zweig laeuft im NETZFADEN, nicht im Hauptfaden. Das ist der Zweck des
     * Ereignisses: Hier darf und soll man die Datenbank fragen, ohne den Server
     * anzuhalten.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void beimAnmelden(AsyncPlayerPreLoginEvent ereignis) {
        if (ereignis.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            // Ein anderes Plugin hat schon abgewiesen. Eine zweite Begruendung
            // ueberschriebe die erste, und der Spieler laese die falsche.
            return;
        }
        abweisungFuer(ereignis.getUniqueId(), ereignis.getName()).ifPresent(
                // disallow mit KICK_BANNED und nicht kickPlayer: Der Spieler ist noch
                // nicht im Spiel, und die Zeile erscheint in seinem Serverbrowser -
                // dort, wo er sie liest, bevor er es noch einmal versucht.
                zeile -> ereignis.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_BANNED, zeile));
    }

    /**
     * Die Zeile, mit der dieser Spieler abgewiesen wird - oder leer, wenn er herein
     * darf.
     *
     * <p><b>Die ganze Entscheidung steht HIER und nicht im Ereigniszweig</b>, und das
     * ist eine Messung: {@code AsyncPlayerPreLoginEvent} laesst sich ohne laufenden
     * Server nicht bauen (es braucht {@code Server.createProfile}). Eine Entscheidung,
     * die nur dort stuende, waere ausschliesslich auf einem gestarteten Server pruefbar
     * - also gar nicht. Hier laeuft sie durch eine Probe, samt der Richtung bei einer
     * Datenbankstoerung.
     *
     * <p><b>EIN Component und keine Liste</b>: {@code disallow} nimmt nur eines. Die
     * Zeilen werden mit echten Umbruechen zusammengefuegt ({@link Straftext#alsEine}),
     * und auf dem Serverbrowser ist genau das richtig - Grund, Frist und Ausweg
     * untereinander.
     */
    Optional<Component> abweisungFuer(UUID wer, String name) {
        Optional<Strafe> bann = geltende(wer, Strafart.BANN, name);
        if (bann.isEmpty()) {
            return Optional.empty();
        }
        Strafe eine = bann.get();
        protokoll.info(name + " wurde an der Tuer abgewiesen - Strafe #"
                + eine.nummer() + ", Grund: " + eine.grund());
        return Optional.of(Straftext.alsEine(Straftext.getroffen(sprache, eine,
                uhr.jetzt(), zone.get(),
                Straftext.werwort(sprache, eine.verhaengtVon(), namen))));
    }

    /**
     * Der Chat - ein stummgeschalteter Spieler schreibt nicht.
     *
     * <p>{@link EventPriority#LOWEST}: Die Zeile soll gar nicht erst durch die
     * Verarbeitung anderer Plugins laufen. Ein Chatfilter, der sie protokolliert, haette
     * sonst eine Zeile im Buch, die niemand gelesen hat.
     *
     * <p>Dieser Zweig laeuft ausserhalb des Hauptfadens - Paper liefert den Chat
     * nebenlaeufig aus. Gelesen wird nur, nichts geschrieben; siehe den Klassenkopf zur
     * Haeufigkeit.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beimSprechen(AsyncChatEvent ereignis) {
        if (ereignis.isCancelled()) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        List<Component> absage = absageFuerChat(spieler.getUniqueId(),
                spieler.getName());
        if (absage.isEmpty()) {
            return;
        }
        ereignis.setCancelled(true);
        // Die Zeilen gehen an IHN und nicht an alle: Eine abgewiesene Chatzeile, die
        // alle lesen, waere die Buehne, die eine Stummschaltung nehmen soll.
        for (Component zeile : absage) {
            spieler.sendMessage(zeile);
        }
    }

    /**
     * Die Zeilen, die ein stummgeschalteter Spieler statt seiner Chatzeile liest -
     * oder eine leere Liste, wenn er sprechen darf.
     *
     * <p>Heraus aus dem Ereigniszweig, aus demselben Grund wie
     * {@link #abweisungFuer}: Was hinter einer Bukkit-Naht liegt, ist nur auf einem
     * laufenden Server pruefbar. Dass eine LEERE Liste "darf sprechen" heisst, steht
     * hier und an der einen Aufrufstelle - zwei Stellen, und keine davon ist ein
     * {@code boolean} neben einer Liste, der eines Tages das Gegenteil sagt.
     */
    List<Component> absageFuerChat(UUID wer, String name) {
        Optional<Strafe> stumm = geltende(wer, Strafart.STUMM, name);
        if (stumm.isEmpty()) {
            return List.of();
        }
        Strafe eine = stumm.get();
        return Straftext.getroffen(sprache, eine, uhr.jetzt(), zone.get(),
                Straftext.werwort(sprache, eine.verhaengtVon(), namen));
    }

    /**
     * Ein Befehl, den die Stummschaltung mitsperrt.
     *
     * <p><b>Ohne diesen Zweig ist jede Stummschaltung umgehbar</b>, und zwar mit einem
     * Befehl, den jeder Server hat: {@code /msg}. Welche Befehle es sind, steht in der
     * {@code config.yml} als Liste von Namen - jedes Nachbarplugin bringt eigene mit.
     *
     * <p>Gefragt wird ZUERST die Liste und erst danach die Datenbank: Die
     * ueberwaeltigende Mehrheit aller Befehle steht nicht darin, und fuer die soll kein
     * Lesezugriff anfallen. Dieser Zweig laeuft bei JEDEM getippten Befehl.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beimBefehl(PlayerCommandPreprocessEvent ereignis) {
        if (ereignis.isCancelled()) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        Optional<Component> absage = absageFuerBefehl(spieler.getUniqueId(),
                spieler.getName(), ereignis.getMessage());
        if (absage.isEmpty()) {
            return;
        }
        ereignis.setCancelled(true);
        spieler.sendMessage(absage.get());
    }

    /**
     * Die Zeile, mit der ein gesperrter Befehl abgewiesen wird - oder leer, wenn er
     * durchgeht.
     *
     * <p>Heraus aus dem Ereigniszweig, aus demselben Grund wie {@link #abweisungFuer}:
     * {@code PlayerCommandPreprocessEvent} laesst sich ohne laufenden Server nicht
     * bauen (es braucht {@code Player.getServer}) - gemessen am 25.11.2026.
     *
     * <p>Gefragt wird ZUERST die Liste und erst danach die Datenbank: Die
     * ueberwaeltigende Mehrheit aller Befehle steht nicht darin, und fuer die soll kein
     * Lesezugriff anfallen. Dieser Weg laeuft bei JEDEM getippten Befehl.
     */
    Optional<Component> absageFuerBefehl(UUID wer, String name, String getippt) {
        if (!buch.werte().sperrtBefehl(getippt)) {
            return Optional.empty();
        }
        Optional<Strafe> stumm = geltende(wer, Strafart.STUMM, name);
        if (stumm.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Straftext.befehlGesperrt(sprache, stumm.get(),
                erstesWort(getippt), uhr.jetzt()));
    }

    /**
     * Die geltende Strafe - oder leer, auch bei einer Stoerung.
     *
     * <p>Die EINE Stelle, an der die Entscheidung aus dem Klassenkopf steht: Wer nicht
     * weiss, ob eine Strafe gilt, laesst den Spieler durch. Mit einer Warnung, denn das
     * heisst: In diesem Augenblick wirkt keine Sperre.
     */
    private Optional<Strafe> geltende(UUID wer, Strafart art, String name) {
        try {
            return buch.geltende(wer, art, uhr.jetzt());
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Strafen (" + art + ") von " + name
                    + " sind nicht lesbar: " + gestoert.getMessage()
                    + ". Er wird DURCHGELASSEN - ein Server, der bei einer"
                    + " Datenbankstoerung fuer alle zu ist, waere schlimmer. Solange"
                    + " diese Meldung kommt, wirkt keine Sperre dieser Art.");
            return Optional.empty();
        }
    }

    /**
     * Das erste Wort eines getippten Befehls, ohne Schraegstrich.
     *
     * <p>Fuer die Zeile, die der Spieler liest: Sie soll den Befehl nennen, den er
     * versucht hat, und nicht die ganze Zeile - in der steht, was er schreiben wollte,
     * und das gehoert nicht in seine eigene Absage zurueck.
     */
    private static String erstesWort(String getippt) {
        String rein = getippt.trim();
        while (rein.startsWith("/")) {
            rein = rein.substring(1);
        }
        int leer = rein.indexOf(' ');
        return leer < 0 ? rein : rein.substring(0, leer);
    }
}
