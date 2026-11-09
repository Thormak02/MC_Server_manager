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
    private static final String KEIN_SPAWNPUNKT = "&7Hier wird kein Spawnpunkt"
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
        return new Ankunftswache(ankunftswelt, welten, new SpeicherKasse(0L),
                Logger.getLogger("AnkunftswacheTest"), Sprachprobe.spracheAusDemJar());
    }

    /** Eine Weltangabe, von der nur der Spielmodus zaehlt - der Rest ist Beiwerk. */
    private static Weltangabe angabe(String name, GameMode modus) {
        return new Weltangabe(name, World.Environment.NORMAL, Weltangabe.Art.GEWOEHNLICH,
                null, 0, null, modus, null);
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
        assertEquals(List.of("&7Hier ist Abenteuer: ansehen und benutzen, aber"
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
}
