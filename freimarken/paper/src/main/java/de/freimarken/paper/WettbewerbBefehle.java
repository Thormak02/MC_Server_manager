package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Punktzeile;
import de.freimarken.kern.Wettbewerbsbuch;
import de.freimarken.kern.Wettbewerbswerte;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /wettbewerb} - der Angelwettbewerb in Altfurt (Plan 14).
 *
 * <p>Zwei Zweige, jeder mit eigenem {@code executes}: {@code /wettbewerb} zeigt Stand und
 * Rangliste, {@code /wettbewerb anmelden} zahlt das Startgeld. Ohne das zweite endete
 * {@code /wettbewerb anmelden} in Brigadiers englischem "Unknown or incomplete command".
 *
 * <p><b>Die ANMELDUNG ist eine eigene Tat und kein Nebeneffekt des Angelns</b>, und das
 * ist die tragende Entscheidung: Wer im Fenster an einem Teich steht, soll nicht ungefragt
 * Startgeld zahlen. Darum sammelt nur Punkte, wer eine Zeile hat - und die entsteht hier.
 *
 * <p><b>Die Spielernamen kommen ueber eine Naht herein</b> ({@link Blocktext.Namensgeber},
 * dieselbe wie beim Blockbuch): {@code Bukkit.getOfflinePlayer} braucht einen Server, und
 * eine Rangliste hat viele Namen. Der Befehl gibt {@code Spielername::von}, die Probe gibt
 * eine Karte.
 */
final class WettbewerbBefehle {

    /** Wer mitmachen darf - jeder. Angemeldet mit {@code default: true}. */
    static final String RECHT = "freimarken.wettbewerb";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "wettbewerb";

    /** Der Zweig, der anmeldet. */
    static final String ANMELDEN = "anmelden";

    /** Wie viele Plaetze die Rangliste im Chat zeigt. */
    private static final int PLAETZE_IM_CHAT = 10;

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Wettbewerbsbuch buch;
    private final Sprache sprache;
    private final Uhr uhr;
    private final Blocktext.Namensgeber namen;

    WettbewerbBefehle(Wettbewerbsbuch buch, Sprache sprache, Uhr uhr,
                      Blocktext.Namensgeber namen) {
        if (buch == null || sprache == null || uhr == null || namen == null) {
            throw new IllegalArgumentException("/wettbewerb braucht das Buch, Sprache,"
                    + " eine Uhr und die Namensnaht");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.uhr = uhr;
        this.namen = namen;
    }

    /** Der Baum: {@code /wettbewerb} und {@code /wettbewerb anmelden}. */
    LiteralCommandNode<CommandSourceStack> wettbewerb() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeStand)
                .then(Commands.literal(ANMELDEN)
                        .executes(this::meldeAn))
                .build();
    }

    /**
     * {@code /wettbewerb} - was laeuft, wer vorn ist, wie man mitmacht.
     *
     * <p>Auch die KONSOLE darf das lesen: Ein Betreiber soll nachsehen koennen, was er
     * eingestellt hat. Fuer sie faellt nur die eigene Standzeile weg - sie hat kein
     * Konto und steht in keiner Rangliste.
     */
    private int zeigeStand(CommandContext<CommandSourceStack> zusammenhang) {
        Wettbewerbswerte werte = buch.werte();
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(WettbewerbSatz.KOPF, 1L, Einsatz.keine()));
        if (!werte.greift()) {
            zeilen.addAll(sprache.bau(WettbewerbSatz.AUS, 1L, Einsatz.keine()));
            sende(zusammenhang.getSource().getSender(), zeilen);
            return Command.SINGLE_SUCCESS;
        }
        Instant jetzt = uhr.jetzt();
        boolean istSpieler = zusammenhang.getSource().getSender() instanceof Player;
        Wettbewerbsbuch.Auskunft auskunft = istSpieler
                ? buch.frage(((Player) zusammenhang.getSource().getSender())
                        .getUniqueId(), jetzt)
                : null;

        Optional<Wettbewerbswerte.Lauf> offen = buch.offenerLauf(jetzt);
        if (offen.isPresent()) {
            long topf = auskunft == null ? 0L : auskunft.topf();
            int teilnehmer = auskunft == null ? 0 : auskunft.rangliste().size();
            zeilen.addAll(sprache.bau(WettbewerbSatz.OFFEN, 1L,
                    Einsatz.von("bis", uhrzeit(offen.get().ende()))
                            .mit("topf", sprache.betrag(topf))
                            .mit("teilnehmer", teilnehmer)));
        } else {
            zeilen.addAll(sprache.bau(WettbewerbSatz.GESCHLOSSEN, 1L,
                    Einsatz.von("beginn", naechsterBeginn(werte))));
        }
        if (auskunft != null) {
            zeilen.addAll(eigeneZeilen(auskunft, werte));
            zeilen.addAll(rangliste(auskunft));
        }
        sende(zusammenhang.getSource().getSender(), zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /** Die eigene Standzeile - oder der Hinweis, wie man mitmacht. */
    private List<Component> eigeneZeilen(Wettbewerbsbuch.Auskunft auskunft,
                                         Wettbewerbswerte werte) {
        if (!auskunft.dabei()) {
            return sprache.bau(WettbewerbSatz.MITMACHEN, 1L,
                    Einsatz.von("startgeld", sprache.betrag(werte.startgeld())));
        }
        Punktzeile eigene = auskunft.eigene().orElseThrow();
        int platz = 1 + auskunft.rangliste().indexOf(eigene);
        return sprache.bau(WettbewerbSatz.EIGENER_STAND, eigene.punkte(),
                Einsatz.von("punkte", eigene.punkte()).mit("platz", platz));
    }

    /** Die Rangliste - hoechstens zehn Plaetze. */
    private List<Component> rangliste(Wettbewerbsbuch.Auskunft auskunft) {
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(WettbewerbSatz.RANGLISTE_KOPF, 1L, Einsatz.keine()));
        if (auskunft.rangliste().isEmpty()) {
            zeilen.addAll(sprache.bau(WettbewerbSatz.RANGLISTE_LEER, 1L,
                    Einsatz.keine()));
            return zeilen;
        }
        List<Punktzeile> liste = auskunft.rangliste();
        for (int i = 0; i < liste.size() && i < PLAETZE_IM_CHAT; i++) {
            Punktzeile zeile = liste.get(i);
            zeilen.addAll(sprache.bau(WettbewerbSatz.PLATZ, 1L,
                    Einsatz.von("platz", i + 1)
                            .mit("name", namen.von(zeile.spieler()))
                            .mit("punkte", zeile.punkte())));
        }
        return zeilen;
    }

    /**
     * {@code /wettbewerb anmelden} - das Startgeld.
     *
     * <p>Ein Schalter ohne {@code default}: Wer dem {@link Wettbewerbsbuch.Bescheid}
     * einen weiteren Wert hinzufuegt, wird vom Uebersetzer gefragt, was der Spieler dann
     * liest.
     */
    private int meldeAn(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            sende(zusammenhang.getSource().getSender(),
                    sprache.bau(WettbewerbSatz.NUR_SPIELER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Instant jetzt = uhr.jetzt();
        Wettbewerbsbuch.Entscheid entscheid = buch.meldeAn(wer.getUniqueId(), jetzt);
        Wettbewerbswerte werte = buch.werte();
        sende(wer, switch (entscheid.bescheid()) {
            case ANGEMELDET -> sprache.bau(WettbewerbSatz.ANGEMELDET, 1L,
                    Einsatz.von("startgeld", sprache.betrag(entscheid.startgeld()))
                            .mit("topf", sprache.betrag(
                                    buch.frage(wer.getUniqueId(), jetzt).topf())));
            case SCHON_DABEI -> sprache.bau(WettbewerbSatz.SCHON_DABEI, 1L,
                    Einsatz.keine());
            case KEIN_LAUF -> sprache.bau(WettbewerbSatz.KEIN_LAUF, 1L,
                    Einsatz.von("beginn", naechsterBeginn(werte)));
            case ZU_WENIG_MARK -> sprache.bau(WettbewerbSatz.ZU_WENIG, 1L,
                    Einsatz.von("startgeld", sprache.betrag(entscheid.startgeld()))
                            .mit("guthaben", sprache.betrag(
                                    buch.frage(wer.getUniqueId(), jetzt).guthaben())));
            case AUS -> sprache.bau(WettbewerbSatz.AUS, 1L, Einsatz.keine());
            case GESTOERT -> sprache.bau(WettbewerbSatz.GESTOERT, 1L, Einsatz.keine());
            // Diese drei kommen von der ABRECHNUNG und nie von einer Anmeldung. Ein
            // gemeinsamer Schalter ohne default ist trotzdem richtig: Wer dem Bescheid
            // einen Wert hinzufuegt, soll gefragt werden - und ein Zweig, der GESTOERT
            // sagt, ist besser als einer, der schweigt.
            case ABGERECHNET, SCHON_ABGERECHNET, NIEMAND_DABEI ->
                    sprache.bau(WettbewerbSatz.GESTOERT, 1L, Einsatz.keine());
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Wann das naechste Fenster aufgeht - als Uhrzeit mit Wochentag, wenn noetig.
     *
     * <p>NUR die Uhrzeit, wenn der Wettbewerb taeglich laeuft: "um 18:00" ist dann die
     * ganze Auskunft. Laeuft er an einzelnen Tagen, gehoert der Tag dazu - sonst wartet
     * jemand am Montag auf einen Wettbewerb, der erst am Freitag ist.
     */
    private String naechsterBeginn(Wettbewerbswerte werte) {
        LocalTime beginn = werte.beginn();
        if (werte.tage().size() == 7) {
            return beginn.toString();
        }
        List<String> namenDerTage = new ArrayList<>();
        for (java.time.DayOfWeek tag : java.time.DayOfWeek.values()) {
            if (werte.tage().contains(tag)) {
                namenDerTage.add(sprache.wort(wochentag(tag), Einsatz.keine()));
            }
        }
        return beginn + " (" + String.join("/", namenDerTage) + ")";
    }

    /**
     * Das Wort zu einem Wochentag - ein Schalter ohne {@code default}.
     *
     * <p>Wer {@link java.time.DayOfWeek} einen achten Wert hinzufuegt, wird vom
     * Uebersetzer gefragt. Das wird nicht geschehen - aber eine Karte daneben waere eine
     * stille Vorgabe, und die fehlende Zeile faende man erst im Chat.
     *
     * <p><b>Sie steht HIER und nicht in {@link WettbewerbSatz}</b> [22.11.2026]:
     * {@code GrenzeTest} 30 prueft, dass jeder Satz von einem Zweig AUSSERHALB seiner
     * eigenen Aufzaehlung namentlich geholt wird - sonst steht er in der Sprachdatei und
     * wirkt nicht. Stand die Zuordnung dort, nannte niemand die sieben Worte, und die
     * Probe hatte recht damit: Der Verbraucher ist dieser Befehl.
     */
    private static WettbewerbSatz wochentag(java.time.DayOfWeek tag) {
        return switch (tag) {
            case MONDAY -> WettbewerbSatz.TAG_MONTAG;
            case TUESDAY -> WettbewerbSatz.TAG_DIENSTAG;
            case WEDNESDAY -> WettbewerbSatz.TAG_MITTWOCH;
            case THURSDAY -> WettbewerbSatz.TAG_DONNERSTAG;
            case FRIDAY -> WettbewerbSatz.TAG_FREITAG;
            case SATURDAY -> WettbewerbSatz.TAG_SAMSTAG;
            case SUNDAY -> WettbewerbSatz.TAG_SONNTAG;
        };
    }

    /** Ein Zeitpunkt als Ortszeit - dieselbe Zone, in der das Fenster liegt. */
    private String uhrzeit(Instant augenblick) {
        ZoneId zone = buch.zone();
        return augenblick.atZone(zone).toLocalTime().toString();
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
