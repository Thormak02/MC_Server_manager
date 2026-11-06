package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Warpverzeichnis;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /warp} GETIPPT - und wer es ueberhaupt tippen darf.
 *
 * <p><b>Zwei Fragen, die keine Tafel beantworten kann.</b> Die erste ist das
 * {@code requires}: An der Wurzel haengen ZWEI Bedingungen, ein Spieler zu sein UND
 * {@code freimarken.reisen} zu haben. Faellt eine davon weg, bleibt jede Zahl im
 * Quelltext gleich - und im Spiel reist entweder jeder oder niemand. Die zweite ist die
 * Weiche zwischen dem nackten {@code /warp} und {@code /warp <ziel>}: Es sind zwei
 * {@code executes} an zwei Knoten, und welcher antwortet, entscheidet erst Brigadier.
 *
 * <p><b>Die Reise selbst bleibt aussen vor, und das ist eine Grenze mit Grund.</b>
 * {@code Reise.fuehre} fragt als ERSTES die Aufwaermzeit, und die braucht eine
 * {@code JavaPlugin}-Instanz - die laesst sich ohne {@code PluginClassLoader} nicht
 * bauen. Erreichbar sind darum die Absagen, die VOR der Aufwaermzeit liegen: das
 * unbekannte Ziel, die fehlende Welt und der Fall "du bist schon da". Das sind genau die
 * Zweige, die ein Spieler am haeufigsten sieht.
 */
class WarpBefehlTest {

    private static final String NETHER = "minecraft:world_nether";
    private static final String ENDE = "minecraft:world_the_end";

    private World nether;
    private Befehlsprobe baum;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        nether = Serverprobe.welt(NETHER);
        Warpverzeichnis verzeichnis = Warpverzeichnis.ausWelten(
                List.of("nether", "ende"));
        // "ende" steht im Verzeichnis, aber nicht in dieser Karte: der Fall, in dem der
        // Name in der Datei steht und die Welt nicht entstanden ist.
        Map<String, World> welten = Map.of("nether", nether);
        Reise reise = new Reise(Logger.getLogger("WarpBefehlTest"), new Rueckweg(),
                null, Sprachprobe.spracheAusDemJar());
        WarpBefehle befehle = new WarpBefehle(verzeichnis, welten, reise,
                Sprachprobe.spracheAusDemJar());
        baum = new Befehlsprobe(List.of(befehle.warp()));
    }

    private Spielerprobe reisender(int zahl) {
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl),
                WarpBefehle.RECHT_REISEN);
    }

    @Test
    @DisplayName("41 - /warp ohne Ziel zeigt die Ziele")
    void warpOhneZielZeigtDieZiele() throws CommandSyntaxException {
        Spielerprobe spieler = reisender(81);

        assertEquals(1, baum.tippe("warp", spieler));

        assertFalse(spieler.chatBild().isEmpty(), "das nackte /warp antwortet");
    }

    @Test
    @DisplayName("41b - drei Lagen von /warp <ziel> geben drei verschiedene Absagen")
    void dreiLagenDreiAbsagen() throws CommandSyntaxException {
        // Unbekanntes Ziel, bekanntes Ziel ohne Welt, und "du bist schon da". Die drei
        // muessen sich UNTERSCHEIDEN: Ein vertauschter Zweig liesse zwei davon gleich,
        // und die Zahl der Sendestellen blieb dabei eine Drei.
        List<String> unbekannt = antwortAuf("warp gibtsnicht", 82, null);
        List<String> ohneWelt = antwortAuf("warp ende", 83, null);
        List<String> schonDa = antwortAuf("warp nether", 84, nether);

        for (List<String> antwort : List.of(unbekannt, ohneWelt, schonDa)) {
            assertFalse(antwort.isEmpty(), "jede Lage antwortet");
        }
        assertNotEquals(unbekannt, ohneWelt, "ein unbekanntes Ziel und eine fehlende"
                + " Welt sind zwei verschiedene Lagen - der Spieler soll wissen, ob er"
                + " sich vertippt hat oder ob der Server etwas nicht hat");
        assertNotEquals(ohneWelt, schonDa);
        assertNotEquals(unbekannt, schonDa);
    }

    private List<String> antwortAuf(String befehl, int zahl, World wo)
            throws CommandSyntaxException {
        Spielerprobe spieler = reisender(zahl);
        if (wo != null) {
            spieler.inWelt(wo);
        }
        baum.tippe(befehl, spieler);
        return spieler.chatBild();
    }

    @Test
    @DisplayName("41c - ohne freimarken.reisen gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        Spielerprobe ohneRecht = new Spielerprobe("Dora", Spielerprobe.kennungAus(85));

        // Die zweite Haelfte des requires. Faellt sie, reist jeder Gast - und der Zugang
        // zu Nether und Ende haengt im Plan an der Rangleiter.
        assertThrows(CommandSyntaxException.class, () -> baum.tippe("warp", ohneRecht));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("warp nether", ohneRecht));
        assertEquals(List.of(), ohneRecht.chatBild());
    }

    @Test
    @DisplayName("41d - die Konsole hat keinen Ort, in den sie reisen koennte")
    void dieKonsoleReistNicht() {
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippeAufDerKonsole("warp nether"));
    }
}
