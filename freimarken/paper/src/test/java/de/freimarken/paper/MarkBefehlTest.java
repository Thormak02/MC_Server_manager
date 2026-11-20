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
import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Muenzhaus;
import java.util.Map;
import de.freimarken.kern.Vorrat;

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
    private record Stand(Befehlsprobe baum, SpeicherKasse kasse, Goldkasse gold,
                         Rucksack rucksack) { }

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
        return stand(Map.of());
    }

    /**
     * Derselbe Stand mit MESSWERTEN fuer die Bedingungen des Muenzhauses.
     *
     * <p>Hereingegeben und nicht gesetzt: Die Messung ist im Spiel eine
     * Datenbankabfrage, und hier ist sie eine Karte. Ohne Werte misst jede Bedingung 0 -
     * das ist der Spieler am ersten Tag.
     */
    private static Stand stand(Map<Bedingungsart, Long> gemessen) {
        SpeicherKasse kasse = new SpeicherKasse(Wirtschaftswerte.VORGABE_STARTGUTHABEN, UHR);
        Besitzbuch buch = new Besitzbuch(Stadtgrenze.marktstadt(), new SpeicherLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Rucksack rucksack = new Rucksack();
        ParzellenBefehle befehle = new ParzellenBefehle(buch, Serverprobe.welt(WELT),
                stand,
                Logger.getLogger("MarkBefehlTest"), Sprachprobe.spracheAusDemJar(),
                // Keine Bonuszeile: Diese Proben halten /mark ohne Teil H.
                // Dass die Zeile dazukommt, prueft BonusBefehlTest.
                wer -> null,
                // DIESELBE UHR wie die Kasse: Der Wechseldeckel zaehlt die Buchungen
                // seit dem Tagesbeginn, und mit zwei Uhren lagen die Buchungen und die
                // Grenze an verschiedenen Tagen.
                new Wechselbuch(kasse, gold, stand, UHR),
                // Das Muenzhaus auf DERSELBEN Goldkasse wie die Wechselstube - zwei
                // waeren zwei Konten in einer Waehrung, und keine Probe saehe es.
                new Muenzhaus(gold, stand, (wer, gebraucht) -> gemessen),
                // Der Rucksack als Karte - Vorratshand braucht einen laufenden Server
                // (Material.getMaxStackSize greift in Minecrafts Verzeichnis).
                spieler -> rucksack);
        return new Stand(new Befehlsprobe(List.of(befehle.mark())), kasse, gold,
                rucksack);
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
    @DisplayName("39k - tauschen ohne Zahl tauscht nichts und antwortet DEUTSCH")
    void tauschenOhneZahl() throws CommandSyntaxException {
        // GEAENDERT AM 20.11.2026, und zwar als FUND dieses Bauschrittes: Bis dahin
        // verlangte diese Probe einen Wurf - und der war Brigadiers englisches "Unknown
        // or incomplete command". Ein passendes Literal verdeckt in Brigadier jedes
        // Geschwister vollstaendig; ohne eigenes .executes fuehrte "kaufen" also
        // nirgendwohin. Jetzt zeigt es die TAFEL, auf der steht, was als naechstes Wort
        // gefragt ist - wie "/wolke abgeben" ohne Namen.
        //
        // Dass dabei kein Geld fliesst, bleibt die tragende Haelfte dieser Probe.
        Stand stand = stand();
        Spielerprobe spieler = spieler(71, stand.kasse());

        stand.baum().tippe("mark tauschen kaufen", spieler);

        assertTrue(spieler.chatBild().contains("&6Wechselstube"),
                "statt einer englischen Fehlerzeile die Tafel: " + spieler.chatBild());
        assertEquals(50_000L, stand.kasse().guthaben(spieler.kennung()));
        assertEquals(0L, stand.gold().guthaben(spieler.kennung()));
    }

    @Test
    @DisplayName("39m - /mark muenzhaus zeigt Stand, Deckel UND den Katalog")
    void muenzhausZeigtDieTafel() throws CommandSyntaxException {
        // DER ORT, den Plan 2.1 nennt: Kontostand, Wechseldeckel sichtbar, Katalog. Die
        // ganze Liste und nicht ein contains - eine verschobene Zeile faellt sonst nicht
        // auf.
        Stand stand = stand();
        Spielerprobe spieler = spieler(73, stand.kasse());

        assertEquals(1, stand.baum().tippe("mark muenzhaus", spieler));

        assertEquals(List.of("&6Muenzhaus",
                        "&7Du hast &60 Goldmark&7.",
                        "&7Eine Goldmark kostet &61.000 Mark&7 und bringt &6800 Mark&7"
                                + " zurueck.",
                        "&7Heute kannst du noch &612 Goldmark&7 zurueckwechseln"
                                + " (10.000 Mark).",
                        "&8- Kuerbishut fuer 2 Goldmark: 1x carved_pumpkin",
                        "&8- Blumenkiste fuer 2 Goldmark: 16x rose_bush, 16x peony,"
                                + " 16x lilac (noch nicht frei)",
                        "&8- Musikscheibe fuer 5 Goldmark: 1x music_disc_cat",
                        "&8- Rathausglocke fuer 5 Goldmark: 1x bell (noch nicht frei)",
                        "&8/mark muenzhaus kaufen [posten] - Verlauf:"
                                + " /mark muenzhaus verlauf"),
                spieler.chatBild());
    }

    @Test
    @DisplayName("39n - /mark muenzhaus kaufen ohne Posten zeigt die Tafel")
    void muenzhausKaufenOhnePosten() throws CommandSyntaxException {
        // Das Literal traegt sein eigenes .executes - ohne es endete der Ruf in
        // Brigadiers englischem "Unknown or incomplete command", und der Spieler laese
        // nicht, welche Posten es gibt.
        Stand stand = stand();
        Spielerprobe spieler = spieler(74, stand.kasse());

        stand.baum().tippe("mark muenzhaus kaufen", spieler);

        assertTrue(spieler.chatBild().contains("&6Muenzhaus"),
                spieler.chatBild().toString());
    }

    @Test
    @DisplayName("39o - ein Tippfehler im Posten bekommt einen DEUTSCHEN Satz")
    void muenzhausTippfehler() throws CommandSyntaxException {
        // Das Argument ist ein freies Wort, damit der Tippfehler den Zweig erreicht. Ein
        // Argumenttyp, der das Muster prueft, machte den Bescheid UNBEKANNT unerreichbar
        // - und eine Probe, die kaufe() unmittelbar ruft, waere trotzdem gruen.
        Stand stand = stand();
        Spielerprobe spieler = spieler(75, stand.kasse());

        stand.baum().tippe("mark muenzhaus kaufen kuerbishu", spieler);

        assertEquals(List.of("&cDen Posten \"kuerbishu\" gibt es im Katalog nicht."),
                spieler.chatBild());
    }

    @Test
    @DisplayName("39p - kaufen fragt ZURUECK, bestaetigen bucht")
    void muenzhausZweiStufen() throws CommandSyntaxException {
        // Dieselbe Sicherung wie /beruf verlassen und /freistadt gruenden: Der Posten
        // wird getippt, er kostet Goldmark, und verkauft wird hier nichts.
        Stand stand = stand();
        Spielerprobe spieler = spieler(76, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 5", spieler);
        spieler.vergessen();

        stand.baum().tippe("mark muenzhaus kaufen kuerbishut", spieler);

        assertEquals(5L, stand.gold().guthaben(spieler.kennung()),
                "die Rueckfrage bucht nichts");
        assertEquals(0, stand.rucksack().hat("carved_pumpkin"),
                "und sie liefert nichts");
        assertTrue(spieler.chatBild().stream().anyMatch(zeile -> zeile.contains(
                        "/mark muenzhaus kaufen kuerbishut bestaetigen")),
                spieler.chatBild().toString());

        spieler.vergessen();
        stand.baum().tippe("mark muenzhaus kaufen kuerbishut bestaetigen", spieler);

        assertEquals(3L, stand.gold().guthaben(spieler.kennung()),
                "jetzt sind zwei Goldmark weg");
        assertEquals(1, stand.rucksack().hat("carved_pumpkin"), "und der Hut ist da");
        assertEquals(List.of("&aKuerbishut gekauft fuer 2 Goldmark: 1x carved_pumpkin."
                        + " Du hast jetzt 3 Goldmark."), spieler.chatBild());
    }

    @Test
    @DisplayName("39t - ein voller Rucksack sagt ab, BEVOR etwas gebucht ist")
    void muenzhausOhnePlatz() throws CommandSyntaxException {
        // DIE REIHENFOLGE DIESES BAUSCHRITTES, durch den echten Befehlsbaum: erst der
        // Platz, dann das Geld. Andersherum stuenden im Goldverlauf zwei Zeilen fuer
        // einen Kauf, der nie stattgefunden hat.
        Stand stand = stand();
        Spielerprobe spieler = spieler(80, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 5", spieler);
        stand.rucksack().platzFuer(0);
        spieler.vergessen();

        stand.baum().tippe("mark muenzhaus kaufen kuerbishut bestaetigen", spieler);

        assertEquals(5L, stand.gold().guthaben(spieler.kennung()), "nichts abgebucht");
        assertEquals(List.of("&cIn deinem Rucksack fehlt Platz fuer 1 Stueck - es ist"
                + " nichts passiert."), spieler.chatBild());
    }

    @Test
    @DisplayName("39q - /mark muenzhaus verlauf zeigt das GOLDbuch")
    void muenzhausVerlauf() throws CommandSyntaxException {
        // Der Verlauf, den der Auftrag verlangt - und er zeigt Goldmark an beiden Zahlen.
        // /mark verlauf zeigt weiter das Markbuch; dass beide dasselbe Buch zeigen, waere
        // der Fehler, den niemand bemerkt.
        Stand stand = stand();
        Spielerprobe spieler = spieler(77, stand.kasse());
        stand.baum().tippe("mark tauschen kaufen 3", spieler);
        spieler.vergessen();

        stand.baum().tippe("mark muenzhaus verlauf", spieler);

        // DREI Zeilen, und die dritte ist die EROEFFNUNG des Goldkontos: Die schreibt
        // die Kasse selbst, sobald die erste Goldmark ankommt - mit 0, denn die Goldmark
        // hat kein Startguthaben (Plan 8.1 nennt genau zwei Quellen). Sie gehoert in
        // JEDES Buch, und darum steht sie hier in Goldmark da.
        assertEquals(List.of("&6Goldmark: 3 Goldmark",
                        "&7  06.11. 14:37  +3 Goldmark  Aus Mark gewechselt"
                                + "  (Stand 3 Goldmark)",
                        "&7  06.11. 14:37  0 Goldmark  Startguthaben"
                                + "  (Stand 0 Goldmark)"),
                spieler.chatBild());
    }

    @Test
    @DisplayName("39r - ein leeres Goldbuch sagt es, statt nichts zu zeigen")
    void muenzhausVerlaufLeer() throws CommandSyntaxException {
        Stand stand = stand();
        Spielerprobe spieler = spieler(78, stand.kasse());

        stand.baum().tippe("mark muenzhaus verlauf", spieler);

        assertEquals(List.of("&6Goldmark: 0 Goldmark",
                        "&7Noch keine Goldmark bewegt. Mit /mark tauschen kaufen 5 geht"
                                + " es los."),
                spieler.chatBild());
    }

    @Test
    @DisplayName("39s - der Vorschlag nennt JEDEN Posten des Katalogs")
    void muenzhausVorschlag() {
        // Alle und nicht nur die erreichbaren: Brigadier fragt die Vorschlaege bei JEDEM
        // Tastendruck, und eine Goldmark- oder Bedingungsmessung je Buchstabe waere ein
        // Datenbankzugriff je Buchstabe.
        Stand stand = stand();
        Spielerprobe spieler = spieler(79, stand.kasse());

        assertEquals(List.of("blumenkiste", "kuerbishut", "musikscheibe",
                        "rathausglocke"),
                stand.baum().vorschlaege("mark muenzhaus kaufen ", spieler));
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
    /**
     * Ein Rucksack als Karte, mit Platzbudget und Mitschrift.
     *
     * <p>Die Naht, die im Spiel {@code Vorratshand} ausfuellt - und sie ist hier Pflicht,
     * nicht Bequemlichkeit: {@code Material.getMaxStackSize} greift in Minecrafts
     * Verzeichnis und wirft ohne laufenden Server ("No RegistryAccess implementation
     * found", gemessen am 20.11.2026). Mit dieser Attrappe laeuft der ganze Kaufzweig
     * durch den ECHTEN Befehlsbaum, auch der volle Rucksack.
     */
    static final class Rucksack implements Vorrat {

        private final java.util.Map<String, Integer> inhalt = new java.util.LinkedHashMap<>();
        private int platz = 1_000;

        void platzFuer(int stueck) {
            platz = stueck;
        }

        int hat(String gegenstand) {
            return inhalt.getOrDefault(gegenstand, 0);
        }

        @Override
        public int zaehle(String gegenstand) {
            return hat(gegenstand);
        }

        @Override
        public int nimm(String gegenstand, int stueck) {
            int weg = Math.min(hat(gegenstand), stueck);
            inhalt.put(gegenstand, hat(gegenstand) - weg);
            return weg;
        }

        @Override
        public int lege(String gegenstand, int stueck) {
            inhalt.merge(gegenstand, stueck, Integer::sum);
            return stueck;
        }

        @Override
        public int fehlenderPlatz(java.util.Map<String, Integer> gegenstaende) {
            int summe = 0;
            for (int stueck : gegenstaende.values()) {
                summe += stueck;
            }
            return Math.max(0, summe - platz);
        }
    }

}
