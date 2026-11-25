package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import de.freimarken.kern.SpeicherKasse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Ankunftswache DURCHLAUFEN - beide Ereignisse, alle Anlaesse.
 *
 * <p>Der zweite Zug nach {@code SchutzwacheTest}: Dieselben Stellvertreter, und diesmal
 * zwei Ereignisse statt einer Methode. Was {@code GrenzeTest} 31s nur am Quelltext lesen
 * kann - welcher Anlass welchen Satz waehlt -, ist hier ausgefuehrt.
 *
 * <p><b>Die Wache entscheidet zweimal, und die zwei Entscheidungen sind verschieden.</b>
 * Beim Spawnpunkt entscheidet ein {@code switch} ueber sechs Anlaesse, ob gesperrt wird -
 * und DANACH noch einmal, ob dem Spieler etwas gesagt wird. Zwei Anlaesse sperren still
 * ({@code UNKNOWN}, und {@code COMMAND} ohne Recht), zwei sperren und sagen es
 * ({@code BED}, {@code RESPAWN_ANCHOR}), zwei sperren nicht. Keine Zahltafel kann das
 * auseinanderhalten; sie zaehlt zwei Sendestellen und ist zufrieden.
 *
 * <p>Beim Weltwechsel haengt alles an der Weltangabe: Der Spielmodus wird gesetzt, aber
 * der Satz kommt nur, wenn es Abenteuer wird. Dass der Modus WIRKLICH gesetzt wird, steht
 * hier erstmals fest - und dass er NICHT gesetzt wird, wenn er schon stimmt.
 */
class AnkunftswacheTest {

    private static final String ANKUNFT = "minecraft:altfurt";
    private static final String ABENTEUERWELT = "minecraft:wildnis";
    private static final String BAUWELT = "minecraft:marktstadt";

    /**
     * Der Satz zum gesperrten Spawnpunkt - aus dem ROTEN Lauf, nicht abgeschrieben.
     *
     * <p>Als Konstante und nicht zweimal getippt, weil genau das die Zusicherung
     * ist: Bett und Seelenanker sind zwei Anlaesse und bekommen DENSELBEN Satz.
     * Zweimal derselbe Text im Quelltext liesse offen, ob das Absicht war.
     */
    private static final String KEIN_SPAWNPUNKT = "&#a5b4bfHier wird kein Spawnpunkt"
            + " gesetzt - gestorben wird immer nach Altfurt. Schlafen geht"
            + " weiter.";

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    private static Ankunftswache wache() {
        World ankunftswelt = Serverprobe.welt(ANKUNFT);
        Map<String, Weltangabe> welten = Map.of(
                ABENTEUERWELT, angabe("wildnis", GameMode.ADVENTURE),
                BAUWELT, angabe("marktstadt", GameMode.SURVIVAL));
        // Nachher laeuft SOFORT: In einer Probe gibt es keinen Taktgeber, und die
        // Verzoegerung im Spiel hat einen anderen Grund als die Wirkung - sie wartet
        // auf die Rangwache. Hier gibt es keine.
        return new Ankunftswache(ankunftswelt, welten, new SpeicherKasse(0L),
                Logger.getLogger("AnkunftswacheTest"), Sprachprobe.spracheAusDemJar(),
                Runnable::run);
    }

    /** Eine Weltangabe, von der nur der Spielmodus zaehlt - der Rest ist Beiwerk. */
    private static Weltangabe angabe(String name, GameMode modus) {
        return new Weltangabe(name, World.Environment.NORMAL, Weltangabe.Art.GEWOEHNLICH,
                null, 0, null, modus, false, "");
    }

    /** Ein Ereignis, wie Paper es der Wache gibt - nur Spieler und Anlass zaehlen. */
    private static PlayerSetSpawnEvent spawnEreignis(Spielerprobe spieler,
                                                     PlayerSetSpawnEvent.Cause anlass) {
        return new PlayerSetSpawnEvent(spieler.spieler(), anlass, null, false, true, null);
    }

    @Test
    @DisplayName("37 - Bett und Seelenanker: gesperrt, und der Spieler erfaehrt es")
    void bettUndAnkerSperrenUndSagenEs() {
        Ankunftswache wache = wache();
        List<String> gefunden = new ArrayList<>();

        for (PlayerSetSpawnEvent.Cause anlass : List.of(PlayerSetSpawnEvent.Cause.BED,
                PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR)) {
            Spielerprobe spieler = new Spielerprobe("Cara",
                    Spielerprobe.kennungAus(anlass.ordinal()));
            PlayerSetSpawnEvent ereignis = spawnEreignis(spieler, anlass);

            wache.beimSpawnpunktSetzen(ereignis);

            assertTrue(ereignis.isCancelled(), anlass + " muss gesperrt werden");
            assertFalse(ereignis.willNotifyPlayer(), "Vanilla soll NICHT auch noch"
                    + " melden - sonst liest der Spieler zwei Hinweise, und einer sagt"
                    + " das Gegenteil");
            assertEquals(List.of(), spieler.handbalkenBild(),
                    "das ist eine Chatzeile und kein Handbalken");
            gefunden.addAll(spieler.chatBild());
        }

        // Zwei Anlaesse, zweimal derselbe Satz - und gegengelesen gegen die Tafel
        // von BildGleichTest, die ihn ueber den Schluessel holt statt ueber das
        // Ereignis. Zwei Wege, ein Bild.
        assertEquals(List.of(KEIN_SPAWNPUNKT, KEIN_SPAWNPUNKT), gefunden,
                "Bett und Seelenanker bekommen denselben Satz: Wer sich schlafen"
                        + " legt, erfaehrt, dass gestorben wird nach Altfurt - die"
                        + " Meldung von Vanilla ist dafuer abgeschaltet.");
    }

    @Test
    @DisplayName("37b - bei COMMAND entscheidet das Recht, und zwar still")
    void beiCommandEntscheidetDasRecht() {
        Ankunftswache wache = wache();

        Spielerprobe ohneRecht = new Spielerprobe("Cara", Spielerprobe.kennungAus(21));
        PlayerSetSpawnEvent abgewiesen = spawnEreignis(ohneRecht,
                PlayerSetSpawnEvent.Cause.COMMAND);
        wache.beimSpawnpunktSetzen(abgewiesen);

        assertTrue(abgewiesen.isCancelled(), "ohne das Recht wird gesperrt");
        // STILL, und das ist der Unterschied zum Bett: Wer /spawnpoint tippt, hat es
        // gewollt und bekommt die Absage von Minecraft selbst. Ein zweiter Satz von uns
        // waere Rauschen - und genau diese Unterscheidung sieht keine Zahltafel.
        assertEquals(List.of(), ohneRecht.chatBild(),
                "ein getippter Befehl braucht keinen eigenen Satz von uns");

        Spielerprobe mitRecht = new Spielerprobe("Dora", Spielerprobe.kennungAus(22),
                Ankunftswache.RECHT_SPAWNPUNKT_SETZEN);
        PlayerSetSpawnEvent erlaubt = spawnEreignis(mitRecht,
                PlayerSetSpawnEvent.Cause.COMMAND);
        wache.beimSpawnpunktSetzen(erlaubt);

        assertFalse(erlaubt.isCancelled(), "mit dem Recht darf der Punkt gesetzt werden");
        assertEquals(List.of(), mitRecht.chatBild());
    }

    @Test
    @DisplayName("37c - eigene Wege bleiben frei: PLAYER_RESPAWN und PLUGIN sperren nicht")
    void eigeneWegeBleibenFrei() {
        Ankunftswache wache = wache();

        for (PlayerSetSpawnEvent.Cause anlass : List.of(
                PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN,
                PlayerSetSpawnEvent.Cause.PLUGIN)) {
            Spielerprobe spieler = new Spielerprobe("Cara",
                    Spielerprobe.kennungAus(30 + anlass.ordinal()));
            PlayerSetSpawnEvent ereignis = spawnEreignis(spieler, anlass);

            wache.beimSpawnpunktSetzen(ereignis);

            assertFalse(ereignis.isCancelled(), anlass + " ist unser eigener Weg -"
                    + " wird der gesperrt, raeumt das Erscheinen nicht mehr auf und"
                    + " /freimarken setzt keinen Punkt mehr");
            assertEquals(List.of(), spieler.chatBild());
        }
    }

    @Test
    @DisplayName("37d - der Weltwechsel setzt den Spielmodus und sagt es nur beim Abenteuer")
    void derWeltwechselSetztDenModusUndSagtEsNurBeimAbenteuer() {
        Ankunftswache wache = wache();

        // In die Abenteuerwelt: Modus wird gesetzt UND der Satz kommt.
        Spielerprobe reisender = new Spielerprobe("Cara", Spielerprobe.kennungAus(41))
                .inWelt(ABENTEUERWELT).imModus(GameMode.SURVIVAL);
        wache.beimWeltwechsel(new PlayerChangedWorldEvent(reisender.spieler(),
                Serverprobe.welt(BAUWELT)));

        assertEquals(List.of(GameMode.ADVENTURE), reisender.moduswechsel(),
                "in der Wildnis wird auf Abenteuer gestellt");
        assertEquals(List.of("&#a5b4bfHier ist Abenteuer: ansehen und benutzen, aber"
                + " nicht abbauen. Gebaut wird in der Marktstadt."),
                reisender.chatBild(), "wer ins Abenteuer kommt, erfaehrt warum");

        // In die Bauwelt: Modus wird gesetzt, aber NICHTS gesagt.
        Spielerprobe heimkehrer = new Spielerprobe("Dora", Spielerprobe.kennungAus(42))
                .inWelt(BAUWELT).imModus(GameMode.ADVENTURE);
        wache.beimWeltwechsel(new PlayerChangedWorldEvent(heimkehrer.spieler(),
                Serverprobe.welt(ABENTEUERWELT)));

        assertEquals(List.of(GameMode.SURVIVAL), heimkehrer.moduswechsel(),
                "in der Marktstadt wird auf Ueberleben gestellt");
        assertEquals(List.of(), heimkehrer.chatBild(),
                "nur das Abenteuer braucht eine Erklaerung - der Normalfall nicht");

        // Stimmt der Modus schon, wird GAR NICHT gesetzt. Ohne diese Haelfte waere ein
        // setGameMode in jedem Weltwechsel gruen, und der Spieler bekaeme beim Betreten
        // der Wildnis jedes Mal denselben Satz zu lesen.
        Spielerprobe schonRichtig = new Spielerprobe("Emil", Spielerprobe.kennungAus(43))
                .inWelt(ABENTEUERWELT).imModus(GameMode.ADVENTURE);
        wache.beimWeltwechsel(new PlayerChangedWorldEvent(schonRichtig.spieler(),
                Serverprobe.welt(ABENTEUERWELT)));

        assertEquals(List.of(), schonRichtig.moduswechsel(),
                "was schon stimmt, wird nicht gesetzt");
        assertEquals(List.of(), schonRichtig.chatBild(),
                "und darum kommt auch kein Satz");
    }

    @Test
    @DisplayName("37e - wer den Spielmodus frei hat, wird nicht umgestellt")
    void werDenModusFreiHatBleibtUnangetastet() {
        Ankunftswache wache = wache();

        Spielerprobe baumeister = new Spielerprobe("Fritz", Spielerprobe.kennungAus(44),
                Ankunftswache.RECHT_SPIELMODUS_FREI)
                .inWelt(ABENTEUERWELT).imModus(GameMode.CREATIVE);
        wache.beimWeltwechsel(new PlayerChangedWorldEvent(baumeister.spieler(),
                Serverprobe.welt(BAUWELT)));

        assertEquals(List.of(), baumeister.moduswechsel(),
                "das Recht ist der Grund, warum ein Betreiber im Bauen bleiben kann -"
                        + " faellt die Pruefung, steht er beim naechsten Weltwechsel im"
                        + " Abenteuer und kann nichts mehr abbauen");
        assertEquals(List.of(), baumeister.chatBild());
    }
    // -------------------------------------------------- Die Rettung beim Beitritt

    /** Die Mitgliederwelt - premium, und darum nur mit dem Weltrecht. */
    private static final String MITGLIEDERWELT = "minecraft:vogtei";

    /**
     * Eine Wache, die auch die Vogtei kennt - und zwar als PREMIUM-Welt.
     *
     * <p>Dieselbe Wache wie oben, nur mit einem vierten Eintrag. Eine eigene Methode
     * und nicht die bestehende erweitert: Jene prueft den Spielmodus, und ihre Welten
     * sollen offen bleiben - sonst pruefte die Haelfte ihrer Proben kuenftig den
     * Zutritt.
     */
    private static Ankunftswache wacheMitVogtei() {
        return new Ankunftswache(Serverprobe.welt(ANKUNFT), Map.of(
                        ABENTEUERWELT, angabe("wildnis", GameMode.ADVENTURE),
                        BAUWELT, angabe("marktstadt", GameMode.SURVIVAL),
                        MITGLIEDERWELT, premiumAngabe("vogtei")),
                new SpeicherKasse(0L), Logger.getLogger("AnkunftswacheTest"),
                Sprachprobe.spracheAusDemJar(), Runnable::run);
    }

    /** Eine Weltangabe, von der nur das premium zaehlt. */
    private static Weltangabe premiumAngabe(String name) {
        return new Weltangabe(name, World.Environment.NORMAL,
                Weltangabe.Art.GEWOEHNLICH, null, 0, null, GameMode.SURVIVAL, true, "");
    }

    /**
     * WIRKT: Wer in der Vogtei abgemeldet hat und sein Recht verliert, kommt heim.
     *
     * <p><b>Der Fall, den {@code Zutrittsregel} in {@code Reise} nicht abdeckt</b>: Jene
     * sieht jeden Weg DORTHIN. Wer aber schon drin steht, reist nicht - und ohne diese
     * Rettung bliebe er dort, solange er sich nicht wegbewegt. Dasselbe gilt, wenn ein
     * Betreiber einer Welt nachtraeglich {@code premium: true} gibt: Alle, die gerade
     * drin stehen, duerften bleiben.
     */
    @Test
    @DisplayName("37f - WIRKT: ohne das Weltrecht wird er beim Beitritt heimgeholt")
    void ohneWeltrechtHeimgeholt() {
        Spielerprobe gast = new Spielerprobe("Gast", Spielerprobe.kennungAus(45))
                .inWelt(MITGLIEDERWELT)
                .anOrt(new org.bukkit.Location(Serverprobe.welt(MITGLIEDERWELT),
                        0.5, 64, 0.5));

        wacheMitVogtei().beimBeitritt(new org.bukkit.event.player.PlayerJoinEvent(
                gast.spieler(), net.kyori.adventure.text.Component.empty()));

        // DER ORT und nicht nur der Satz: Ein Satz ohne Teleport waere eine Warnung,
        // die nichts tut - und der Spieler stuende weiter in der Welt, die ihm nicht
        // mehr offensteht.
        // Nach dem SCHLUESSEL und nicht nach dem Exemplar: Serverprobe.welt gibt
        // bei jedem Aufruf einen neuen Stellvertreter - ein Vergleich der Objekte
        // waere auch bei derselben Welt rot.
        assertEquals(ANKUNFT,
                gast.spieler().getLocation().getWorld().key().asString(),
                "er muss WIRKLICH in der Ankunftswelt stehen");
        assertEquals(1, gast.chatBild().size(), gast.chatBild().toString());
        assertTrue(gast.chatBild().get(0).contains("steht dir nicht mehr offen"),
                gast.chatBild().get(0));
        assertTrue(gast.chatBild().get(0).contains("vogtei"),
                "der Satz nennt die Welt, aus der er kommt: " + gast.chatBild().get(0));
    }

    /**
     * Das Gegenbeispiel - ohne das waere 37f auch gruen, wenn jeder heimgeholt wuerde.
     */
    @Test
    @DisplayName("37g - WIRKT: MIT dem Weltrecht bleibt er, wo er ist")
    void mitWeltrechtBleibtErDa() {
        Spielerprobe mitglied = new Spielerprobe("Mila", Spielerprobe.kennungAus(46),
                "freimarken.welt.vogtei")
                .inWelt(MITGLIEDERWELT)
                .anOrt(new org.bukkit.Location(Serverprobe.welt(MITGLIEDERWELT),
                        0.5, 64, 0.5));

        wacheMitVogtei().beimBeitritt(new org.bukkit.event.player.PlayerJoinEvent(
                mitglied.spieler(), net.kyori.adventure.text.Component.empty()));

        assertEquals(MITGLIEDERWELT,
                mitglied.spieler().getLocation().getWorld().key().asString(),
                "wer darf, wird nicht weggesetzt");
        assertEquals(List.of(), mitglied.chatBild(),
                "und liest auch nichts davon");
    }

    /**
     * Und wer in einer OFFENEN Welt beitritt, merkt von der Rettung nichts.
     *
     * <p>Der dritte Zeuge: Ohne ihn koennte die Rettung jeden heimholen, der nicht
     * gerade in der Ankunftswelt steht - und 37f und 37g waeren trotzdem gruen.
     */
    @Test
    @DisplayName("37h - in einer offenen Welt greift die Rettung nicht")
    void inEinerOffenenWeltKeineRettung() {
        Spielerprobe jeder = new Spielerprobe("Jan", Spielerprobe.kennungAus(47))
                .inWelt(BAUWELT)
                .anOrt(new org.bukkit.Location(Serverprobe.welt(BAUWELT),
                        0.5, 64, 0.5));

        wacheMitVogtei().beimBeitritt(new org.bukkit.event.player.PlayerJoinEvent(
                jeder.spieler(), net.kyori.adventure.text.Component.empty()));

        assertEquals(BAUWELT,
                jeder.spieler().getLocation().getWorld().key().asString());
        assertEquals(List.of(), jeder.chatBild());
    }
}
