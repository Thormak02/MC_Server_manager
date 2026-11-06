package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /mark} GETIPPT - durch den echten Befehlsbaum.
 *
 * <p><b>Was hier geprueft wird, ist nicht der Wortlaut, sondern die WEICHE.</b> Die
 * Zeilen selbst haelt {@code AufrufstelleTest} schon fest: Es ruft {@code zeigeVerlauf}
 * unmittelbar auf und schreibt mit einer Audience mit - darum sind die Zweige in
 * {@code ParzellenBefehle} paketweit sichtbar. Was dort nicht geprueft werden kann, ist
 * der Weg dorthin: ob der getippte Befehl den Zweig ueberhaupt erreicht, ob
 * {@code /mark verlauf} beim Verlauf landet und nicht beim Stand, und ob
 * {@code requires} den Richtigen durchlaesst.
 *
 * <p>Eine Zahltafel kann das nicht: Sie zaehlt im Quelltext, dass {@code zeigeVerlauf}
 * einmal gerufen wird. Haengt der Aufruf am falschen Unterbefehl, bleibt die Zahl eine
 * Eins - und ein Spieler, der {@code /mark} tippt, liest seinen Verlauf statt seines
 * Standes.
 *
 * <p><b>Die Konsole bekommt hier eine eigene Probe</b>, und sie haelt eine bekannte
 * Unschoenheit fest statt sie zu verschweigen: Brigadier unterscheidet nicht zwischen
 * "den Befehl gibt es nicht" und "{@code requires} laesst dich nicht". Der Betreiber liest
 * auf der Konsole bei {@code /mark} dasselbe wie bei einem Tippfehler. Dass
 * {@code FreimarkenPlugin} darum eine eigene Bereitmeldung schreibt, steht dort im
 * Kommentar - jetzt steht das Verhalten auch in einer Probe.
 */
class MarkBefehlTest {

    private static final String WELT = "minecraft:marktstadt";

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    /** Befehlsbaum und Kasse zusammen - die Kasse braucht die Probe zum Fuellen. */
    private record Stand(Befehlsprobe baum, SpeicherKasse kasse) { }

    /**
     * Die Uhr der Kasse steht FEST, und das ist die Sache selbst.
     *
     * <p>Eine Verlaufszeile traegt den Zeitpunkt der Buchung. Mit der Wanduhr
     * waere ihr Bild in jeder Minute ein anderes und darum nicht nagelbar - die
     * Probe koennte nur Zeilen ZAEHLEN, und eine verschobene Spalte faellt beim
     * Zaehlen nicht auf. Gewaehlt ist ein Nachmittag: Zur Mittagszeit sind
     * Ortszeit und UTC am Tag vor der Zeitumstellung zwei verschiedene Stunden,
     * also faellt eine falsche Zone im Bild auf.
     */
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-06T13:37:00Z"), ZoneOffset.UTC);

    private static Stand stand() {
        SpeicherKasse kasse = new SpeicherKasse(Wirtschaftswerte.VORGABE_STARTGUTHABEN, UHR);
        Besitzbuch buch = new Besitzbuch(Stadtgrenze.marktstadt(), new SpeicherLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        ParzellenBefehle befehle = new ParzellenBefehle(buch, Serverprobe.welt(WELT),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                Logger.getLogger("MarkBefehlTest"), Sprachprobe.spracheAusDemJar());
        return new Stand(new Befehlsprobe(List.of(befehle.mark())), kasse);
    }

    private static Spielerprobe spieler(int zahl, SpeicherKasse kasse) {
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl));
        kasse.eroeffne(spieler.kennung());
        return spieler;
    }

    @Test
    @DisplayName("39 - /mark zeigt den Stand und den Hinweis, nicht den Verlauf")
    void markZeigtDenStand() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(61, stand.kasse());

        assertEquals(1, stand.baum().tippe("mark", spieler),
                "ein ausgefuehrter Zweig gibt SINGLE_SUCCESS");

        assertEquals(List.of("&7Du hast &650.000 Mark&7.",
                "&8/mark verlauf zeigt die letzten Buchungen."),
                spieler.chatBild(), "der Stand und der Hinweis darunter -"
                        + " nicht der Verlauf");
    }

    @Test
    @DisplayName("39b - /mark verlauf landet beim Verlauf und nicht beim Stand")
    void markVerlaufLandetBeimVerlauf() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(62, stand.kasse());

        assertEquals(1, stand.baum().tippe("mark verlauf", spieler));

        // DIE WEICHE: Haengt zeigeVerlauf am falschen Unterbefehl, kommt hier der Stand -
        // und die Zahl in AUFRUFE_IN_PARZELLENBEFEHLE bleibt dabei eine Eins.
        assertEquals(List.of("&6Deine letzten Buchungen",
                "&7  06.11. 14:37  +50.000 Mark  Startguthaben  (Stand 50.000 Mark)"),
                spieler.chatBild(), "der Kopf und die EINE Buchung, die das Eroeffnen"
                        + " des Kontos erzeugt hat - 14:37 Ortszeit aus 13:37 UTC,"
                        + " also die eingestellte Zone und nicht die des Rechners");
    }

    @Test
    @DisplayName("39c - mit Buchungen zeigt /mark verlauf Kopf und Zeilen")
    void markVerlaufMitBuchungen() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(63, stand.kasse());
        stand.kasse().abbuchen(spieler.kennung(), 25_000L,
                Buchungsgrund.PARZELLE_GEKAUFT);

        assertEquals(1, stand.baum().tippe("mark verlauf", spieler));

        // Die NEUESTE Buchung zuerst - ein Verlauf, der von hinten liest, zeigt
        // dem Spieler sein Startguthaben ueber dem Kauf von gestern.
        assertEquals(List.of("&6Deine letzten Buchungen",
                "&7  06.11. 14:37  -25.000 Mark  Parzelle genommen  (Stand 25.000 Mark)",
                "&7  06.11. 14:37  +50.000 Mark  Startguthaben  (Stand 50.000 Mark)"),
                spieler.chatBild(), "der Kauf steht UEBER dem Startguthaben: Ein"
                        + " Verlauf, der von hinten liest, zeigt dem Spieler die"
                        + " aelteste Buchung zuerst");
    }

    @Test
    @DisplayName("39d - auf der Konsole ist /mark wortgleich mit einem Tippfehler")
    void aufDerKonsoleWieEinTippfehler() {
        Stand stand = stand();

        // Die bekannte Unschoenheit, hier festgehalten statt verschwiegen: requires
        // versteckt den Befehl, und Brigadier meldet dasselbe wie bei einem Befehl, den
        // es nicht gibt. Darum schreibt FreimarkenPlugin eine eigene Bereitmeldung -
        // ohne sie liesse sich von aussen nicht feststellen, ob die Anmeldung geklappt
        // hat.
        var versteckt = assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippeAufDerKonsole("mark"));
        var gibtEsNicht = assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippeAufDerKonsole("markx"));

        assertEquals(gibtEsNicht.getMessage(), versteckt.getMessage(),
                "WORTGLEICH - genau das ist die Unschoenheit. Aendert Paper das eines"
                        + " Tages, wird diese Probe rot, und dann gehoert die"
                        + " Bereitmeldung in FreimarkenPlugin neu bedacht.");
    }

    @Test
    @DisplayName("39e - ein Unterbefehl, den es nicht gibt, fuehrt nirgendwohin")
    void unbekannterUnterbefehlFuehrtNirgendwohin() {
        Stand stand = stand();
        Spielerprobe spieler = spieler(65, stand.kasse());

        assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippe("mark verlaufx", spieler));
        assertEquals(List.of(), spieler.chatBild(),
                "ein Tippfehler darf keinen Zweig ausloesen - sonst zeigte /mark"
                        + " verlaufx den Stand, weil Brigadier auf den Elternknoten"
                        + " zurueckfaellt");
    }
}
