package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Goldkasse;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Wechselbuch;
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
    private record Stand(Befehlsprobe baum, SpeicherKasse kasse, Goldkasse gold) { }

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
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        ParzellenBefehle befehle = new ParzellenBefehle(buch, Serverprobe.welt(WELT),
                stand,
                Logger.getLogger("MarkBefehlTest"), Sprachprobe.spracheAusDemJar(),
                // Keine Bonuszeile: Diese Proben halten /mark ohne Teil H.
                // Dass die Zeile dazukommt, prueft BonusBefehlTest.
                wer -> null,
                // DIESELBE UHR wie die Kasse: Der Wechseldeckel zaehlt die Buchungen
                // seit dem Tagesbeginn, und mit zwei Uhren lagen die Buchungen und die
                // Grenze an verschiedenen Tagen.
                new Wechselbuch(kasse, gold, stand, UHR));
        return new Stand(new Befehlsprobe(List.of(befehle.mark())), kasse, gold);
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
    @DisplayName("39f - /mark tauschen zeigt die Tafel der Wechselstube")
    void tauschenZeigtDieTafel() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(66, stand.kasse());

        assertEquals(1, stand.baum().tippe("mark tauschen", spieler));

        // FUENF ZEILEN, und die vierte ist die, die Plan 2.1 "Wechseldeckel sichtbar"
        // nennt. Sie nennt die ZAHL DES DECKELS und nicht den Goldmarkstand des
        // Spielers: Was heute noch geht, haengt am Deckel und nicht daran, wie viel
        // einer hat.
        assertEquals(List.of("&6Wechselstube",
                        "&7Du hast &60 Goldmark&7.",
                        "&7Eine Goldmark kostet &61.000 Mark&7 und bringt &6800 Mark&7"
                                + " zurueck.",
                        "&7Heute kannst du noch &612 Goldmark&7 zurueckwechseln"
                                + " (10.000 Mark).",
                        "&8/mark tauschen kaufen 5 oder /mark tauschen verkaufen 5"),
                spieler.chatBild());
    }

    @Test
    @DisplayName("39g - /mark tauschen kaufen bewegt BEIDE Kassen")
    void tauschenKaufen() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(67, stand.kasse());

        assertEquals(1, stand.baum().tippe("mark tauschen kaufen 3", spieler));

        assertEquals(List.of("&a3 Goldmark fuer 3.000 Mark gekauft. Du hast jetzt"
                + " 3 Goldmark."), spieler.chatBild());
        // UND DAS GELD IST WIRKLICH GEFLOSSEN. Ohne diese zwei Zeilen prueefte die Probe
        // nur den Satz - und ein Zweig, der die Antwort baut und nichts bucht, waere
        // gruen.
        assertEquals(47_000L, stand.kasse().guthaben(spieler.kennung()));
        assertEquals(3L, stand.gold().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39h - /mark tauschen verkaufen holt Mark zurueck")
    void tauschenVerkaufen() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(68, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 5", spieler);
        spieler.vergessen();

        assertEquals(1, stand.baum().tippe("mark tauschen verkaufen 5", spieler));

        // 5.000 Mark hinein, 4.000 heraus: Der Rundlauf KOSTET, und das ist der Zweck
        // des schlechteren Rueckwegkurses.
        assertEquals(49_000L, stand.kasse().guthaben(spieler.kennung()));
        assertEquals(0L, stand.gold().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39i - ueber dem Wechseldeckel sagt der Befehl ab, ohne zu kuerzen")
    void tauschenUeberDemDeckel() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(69, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 20", spieler);
        spieler.vergessen();

        assertEquals(1, stand.baum().tippe("mark tauschen verkaufen 20", spieler));

        assertEquals(List.of("&cHeute gehen nur noch 12 Goldmark (9.600 Mark) -"
                + " der Wechseldeckel."), spieler.chatBild());
        // NICHTS GEKUERZT: Die zwanzig Goldmark liegen noch beim Spieler. Wer hier
        // kuerzt, nimmt ihm zwoelf, ohne dass er es gesagt hat.
        assertEquals(20L, stand.gold().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39j - eine 0 kommt nicht durch den Befehlsbaum")
    void nullKommtNichtDurch() {
        // Die Grenzen stehen im BEFEHLSBAUM: Der Client markiert eine 0 rot, bevor sie
        // den Server erreicht. Genau darum darf das Wechselbuch bei einer unmoeglichen
        // Anzahl werfen - sie kann von hier nicht kommen.
        Stand stand = stand();
        Spielerprobe spieler = spieler(70, stand.kasse());

        assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippe("mark tauschen kaufen 0", spieler));
        assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippe("mark tauschen verkaufen -1", spieler));
        assertEquals(List.of(), spieler.chatBild());
        assertEquals(50_000L, stand.kasse().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39k - tauschen ohne Zahl tauscht nichts")
    void tauschenOhneZahl() {
        // kaufen ohne Anzahl waere eine Vermutung darueber, wie viele einer meint - und
        // hier bewegt jeder Ruf Geld. Der Zweig hat darum KEIN eigenes executes.
        Stand stand = stand();
        Spielerprobe spieler = spieler(71, stand.kasse());

        assertThrows(CommandSyntaxException.class,
                () -> stand.baum().tippe("mark tauschen kaufen", spieler));
        assertEquals(List.of(), spieler.chatBild());
        assertEquals(50_000L, stand.kasse().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39l - die Tafel zeigt nach einem Rueckwechsel weniger")
    void tafelZeigtDenVerbrauchtenDeckel() throws CommandSyntaxException {
        // DIE PROBE AUF DEN ZEUGEN DES DECKELS: Die Tafel rechnet ihre Zahl aus dem
        // Buchungsbuch, nicht aus einem Zaehler. Steht die Buchung mit dem falschen
        // Grund im Buch, bleibt diese Zahl auf zwoelf.
        Stand stand = stand();
        Spielerprobe spieler = spieler(72, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 10", spieler);
        stand.baum().tippe("mark tauschen verkaufen 10", spieler);
        spieler.vergessen();

        stand.baum().tippe("mark tauschen", spieler);

        // Die GANZE Zeile und nicht ihre Teile: Ein Index in die Chatliste waere die
        // naechste Probe, die nach einer zusaetzlichen Zeile das Falsche prueft.
        assertTrue(spieler.chatBild().contains(
                        "&7Heute kannst du noch &62 Goldmark&7 zurueckwechseln"
                                + " (2.000 Mark)."),
                "8.000 Mark sind durch, 2.000 frei - das sind zwei Goldmark: "
                        + spieler.chatBild());
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
