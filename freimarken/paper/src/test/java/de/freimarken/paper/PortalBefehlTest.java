package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Quader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code /portal} DURCHLAUFEN - jeder Zweig, mit Datei.
 *
 * <p><b>Warum diese Probe die Datei WIRKLICH schreibt</b>: Der ganze Zweck des Befehls
 * ist, dass ein Tor den Neustart ueberlebt. Eine Probe, die nur den Satz im Chat prueft,
 * waere gruen, waehrend {@code tore.yml} unberuehrt bleibt - und im Spiel faellt das erst
 * beim naechsten Start auf, als verschwundenes Tor.
 *
 * <p><b>Und sie prueft die REIHENFOLGE Pruefung-vor-Schreiben</b> (Probe 6): Ein
 * abgelehntes Tor darf die Datei nicht anfassen. Stuende die Pruefung hinter dem
 * Schreiben, waere die Datei schon kaputt, wenn die Absage kommt.
 */
class PortalBefehlTest {

    private static final String ANKUNFT = "altfurt";
    private static final String FREMDE_WELT = "kornmark";

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @TempDir
    Path ordner;

    private Torbuch torbuch;
    private Befehlsprobe baum;

    /**
     * Die Lage: Ankunftswelt Altfurt, vier Welten, Kante 800.
     *
     * <p>Als Attrappe von drei Fragen und nicht als halbes Plugin - genau dafuer gibt es
     * {@link PortalBefehle.Lage}. Die Kante 800 ist klein genug, dass ein Quader bei
     * x 9000 ausserhalb liegt, und das ist der Fall, den Probe 6 braucht.
     */
    private static final PortalBefehle.Lage LAGE = new PortalBefehle.Lage() {
        @Override
        public String ankunftswelt() {
            return ANKUNFT;
        }

        @Override
        public Set<String> welten() {
            return Set.of(ANKUNFT, "kornmark", "hochwald", "vogtei");
        }

        @Override
        public Integer kanteDerAnkunftswelt() {
            return 800;
        }
    };

    @BeforeEach
    void bauen() throws IOException {
        Path datei = ordner.resolve(Torbuch.DATEI);
        Files.writeString(datei, Torbuch.schrift(java.util.Map.of("hochwald",
                        new Torbuch.Tor("Nordtorhaus", new Quader(-4, -60, -108,
                                -4, -58, -106)))),
                StandardCharsets.UTF_8);
        torbuch = new Torbuch(datei.toFile());
        assertEquals(List.of(), torbuch.lade());
        baum = new Befehlsprobe(List.of(new PortalBefehle(torbuch, LAGE,
                Sprachprobe.spracheAusDemJar(),
                Logger.getLogger("PortalBefehlTest")).portal()));
    }

    /** Ein Verwalter, der in Altfurt steht - an der Stelle, die er merken will. */
    private Spielerprobe inAltfurt(int zahl, int x, int y, int z) {
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl),
                PortalBefehle.RECHT);
        World welt = Serverprobe.welt("freimarken:" + ANKUNFT);
        // BEIDES: anOrt setzt den Ort, inWelt die Welt. Der Zweig fragt den Ort
        // fuer die Zahlen UND die Welt fuer den Riegel - mit nur einem von beiden
        // wirft die Probe statt zu pruefen.
        spieler.inWelt(welt);
        spieler.anOrt(new Location(welt, x + 0.5, y, z + 0.5));
        return spieler;
    }

    // -------------------------------------------------- Die Rechtefrage

    @Test
    @DisplayName("1 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtUnsichtbar() {
        Spielerprobe ohne = new Spielerprobe("Bert", Spielerprobe.kennungAus(60));

        // Brigadier unterscheidet "gibt es nicht" und "darfst du nicht" nicht - das ist
        // richtig so: Ein Werkzeug, von dessen Existenz niemand erfaehrt, wird nicht
        // erfragt.
        assertThrows(CommandSyntaxException.class, () -> baum.tippe("portal", ohne));
    }

    @Test
    @DisplayName("1b - von der Konsole geht es nicht, und sie liest warum")
    void dieKonsoleSetztKeinTor() throws CommandSyntaxException {
        assertEquals(1, baum.tippeAufDerKonsole("portal ecke"));

        assertEquals(1, baum.konsoleBild().size(), baum.konsoleBild().toString());
        assertTrue(baum.konsoleBild().get(0).contains("nur im Spiel"),
                baum.konsoleBild().get(0));
    }

    // -------------------------------------------------- Die Ecken

    @Test
    @DisplayName("2 - zwei Ecken merken, und jede Antwort nennt die Zahlen")
    void zweiEckenMerken() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(61, 106, -60, -4);

        assertEquals(1, baum.tippe("portal ecke", spieler));
        assertTrue(spieler.chatBild().get(0).contains("106/-60/-4"),
                spieler.chatBild().get(0));

        spieler.anOrt(new Location(Serverprobe.welt("freimarken:" + ANKUNFT),
                108.5, -58, -3.5));
        assertEquals(1, baum.tippe("portal ecke", spieler));

        assertEquals(2, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(1).contains("106/-60/-4 bis 108/-58/-4"),
                spieler.chatBild().get(1));
        // NEUN Bloecke: drei hoch, drei breit, einen dick. Die Zahl ist der Zeuge
        // dafuer, dass beide Ecken eingeschlossen sind - mit einer offenen Grenze
        // waeren es vier.
        assertTrue(spieler.chatBild().get(1).contains("9 Bloecke"),
                spieler.chatBild().get(1));
    }

    @Test
    @DisplayName("2b - in einer anderen Welt wird keine Ecke gemerkt")
    void inDerFalschenWeltKeineEcke() throws CommandSyntaxException {
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(62),
                PortalBefehle.RECHT);
        World fremde = Serverprobe.welt("freimarken:" + FREMDE_WELT);
        spieler.inWelt(fremde);
        spieler.anOrt(new Location(fremde, 0.5, 64, 0.5));

        assertEquals(1, baum.tippe("portal ecke", spieler));

        assertTrue(spieler.chatBild().get(0).contains("du stehst in " + FREMDE_WELT),
                spieler.chatBild().get(0));
        assertTrue(spieler.chatBild().get(0).contains(ANKUNFT),
                "und wohin er muss: " + spieler.chatBild().get(0));
    }

    // -------------------------------------------------- Setzen

    @Test
    @DisplayName("3 - setzen ohne Ecken sagt, wie es geht")
    void setzenOhneEcken() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(63, 0, -60, 0);

        assertEquals(1, baum.tippe("portal setzen vogtei", spieler));

        assertTrue(spieler.chatBild().get(0).contains("Erst zwei Ecken merken"),
                spieler.chatBild().get(0));
        assertEquals(1, torbuch.anzahl(), "und die Datei bleibt, wie sie war");
    }

    @Test
    @DisplayName("4 - WIRKT: setzen schreibt die Datei, und das Tor gilt sofort")
    void setzenSchreibtUndWirkt() throws CommandSyntaxException, IOException {
        Spielerprobe spieler = inAltfurt(64, 0, -60, 20);
        baum.tippe("portal ecke", spieler);
        spieler.anOrt(new Location(Serverprobe.welt("freimarken:" + ANKUNFT),
                2.5, -58, 20.5));
        baum.tippe("portal ecke", spieler);

        assertEquals(1, baum.tippe("portal setzen vogtei", spieler));

        assertTrue(spieler.chatBild().get(2).contains("Tor nach vogtei gesetzt"),
                spieler.chatBild().get(2));
        // DREI Zeugen, und der dritte ist der eigentliche: Der Satz im Chat, das Buch
        // im Speicher - und die DATEI. Ohne den dritten waere das Tor nach dem
        // naechsten Start weg, und die Probe bliebe gruen.
        assertEquals(new Quader(0, -60, 20, 2, -58, 20), torbuch.tor("vogtei"));
        String geschrieben = Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8);
        assertTrue(geschrieben.contains("  vogtei:"), geschrieben);
        assertTrue(geschrieben.startsWith("# ====="),
                "und der Kopf der Datei steht noch da");
    }

    @Test
    @DisplayName("5 - eine Welt, die es nicht gibt, bekommt kein Tor")
    void unbekannteWelt() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(65, 0, -60, 30);
        baum.tippe("portal ecke", spieler);
        baum.tippe("portal ecke", spieler);

        assertEquals(1, baum.tippe("portal setzen gibtesnicht", spieler));

        assertTrue(spieler.chatBild().get(2).contains("Die Welt gibtesnicht gibt es nicht"),
                spieler.chatBild().get(2));
        assertEquals(1, torbuch.anzahl());
    }

    /**
     * Der Zeuge fuer die Reihenfolge: PRUEFEN, dann schreiben.
     *
     * <p>Der Quader liegt bei x 9000 und damit ausserhalb der Kante 800. Die Pruefung
     * findet das, und danach muss die Datei UNBERUEHRT sein. Stuende das Schreiben
     * vorher, waere sie schon kaputt, wenn die Absage im Chat ankommt - und niemand
     * wuesste, dass er sie von Hand richten muss.
     */
    @Test
    @DisplayName("6 - WIRKT: ein abgelehntes Tor laesst die Datei unberuehrt")
    void abgelehntLaesstDieDateiUnberuehrt() throws CommandSyntaxException, IOException {
        String vorher = Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8);
        Spielerprobe spieler = inAltfurt(66, 9000, -60, 0);
        baum.tippe("portal ecke", spieler);
        baum.tippe("portal ecke", spieler);

        assertEquals(1, baum.tippe("portal setzen vogtei", spieler));

        assertTrue(spieler.chatBild().get(2).contains("wird nicht gesetzt"),
                spieler.chatBild().get(2));
        assertTrue(spieler.chatBild().get(2).contains("ausserhalb der eingetragenen Grenze"),
                "die Klage des PROGRAMMS mit ihren Zahlen: "
                        + spieler.chatBild().get(2));
        assertEquals(1, torbuch.anzahl(), "im Speicher hat sich nichts geaendert");
        assertEquals(vorher, Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8), "und auf der Platte auch nicht");
    }

    @Test
    @DisplayName("6b - ein Tor in die Ankunftswelt selbst wird abgelehnt")
    void torNachNebenanAbgelehnt() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(67, 0, -60, 40);
        baum.tippe("portal ecke", spieler);
        baum.tippe("portal ecke", spieler);

        assertEquals(1, baum.tippe("portal setzen " + ANKUNFT, spieler));

        assertTrue(spieler.chatBild().get(2).contains("Tor nach nebenan"),
                spieler.chatBild().get(2));
    }

    // -------------------------------------------------- Liste, Entfernen, Neuladen

    @Test
    @DisplayName("7 - die Liste nennt jedes Tor mit Quader und Notiz")
    void dieListeNenntAlles() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(68, 0, -60, 0);

        assertEquals(1, baum.tippe("portal", spieler));

        assertEquals(2, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("steht 1 Tor"),
                "die Einzahlform bei genau einem: " + spieler.chatBild().get(0));
        assertTrue(spieler.chatBild().get(1).contains("hochwald"),
                spieler.chatBild().get(1));
        assertTrue(spieler.chatBild().get(1).contains("-4/-60/-108 bis -4/-58/-106"),
                spieler.chatBild().get(1));
        assertTrue(spieler.chatBild().get(1).contains("Nordtorhaus"),
                "die Notiz aus der Datei: " + spieler.chatBild().get(1));
    }

    @Test
    @DisplayName("8 - WIRKT: entfernen nimmt das Tor aus der Datei")
    void entfernenSchreibt() throws CommandSyntaxException, IOException {
        Spielerprobe spieler = inAltfurt(69, 0, -60, 0);

        assertEquals(1, baum.tippe("portal entfernen hochwald", spieler));

        assertTrue(spieler.chatBild().get(0).contains("Tor nach hochwald entfernt"),
                spieler.chatBild().get(0));
        assertEquals(0, torbuch.anzahl());
        assertTrue(!Files.readString(ordner.resolve(Torbuch.DATEI),
                        StandardCharsets.UTF_8).contains("hochwald"),
                "und die Datei weiss es auch");
    }

    @Test
    @DisplayName("8b - entfernen, wo kein Tor ist, sagt es")
    void entfernenOhneTor() throws CommandSyntaxException {
        Spielerprobe spieler = inAltfurt(70, 0, -60, 0);

        assertEquals(1, baum.tippe("portal entfernen vogtei", spieler));

        assertTrue(spieler.chatBild().get(0).contains("Nach vogtei fuehrt kein Tor"),
                spieler.chatBild().get(0));
    }

    @Test
    @DisplayName("9 - WIRKT: neuladen liest eine von Hand geaenderte Datei")
    void neuladenLiestDiePlatte() throws CommandSyntaxException, IOException {
        // Der Fall, fuer den es diesen Zweig gibt: Der Betreiber aendert die Datei von
        // Hand und will den Server nicht stoppen.
        Files.writeString(ordner.resolve(Torbuch.DATEI), Torbuch.schrift(java.util.Map.of(
                        "hochwald", new Torbuch.Tor("", new Quader(-4, -60, -108,
                                -4, -58, -106)),
                        "vogtei", new Torbuch.Tor("", new Quader(0, -60, 50,
                                2, -58, 50)))),
                StandardCharsets.UTF_8);
        Spielerprobe spieler = inAltfurt(71, 0, -60, 0);

        assertEquals(1, baum.tippe("portal neuladen", spieler));

        assertTrue(spieler.chatBild().get(0).contains("2 Tore"),
                spieler.chatBild().get(0));
        assertEquals(2, torbuch.anzahl());
        assertEquals(new Quader(0, -60, 50, 2, -58, 50), torbuch.tor("vogtei"));
    }

    // -------------------------------------------------- Die Vorschlaege

    @Test
    @DisplayName("10 - setzen schlaegt jede Welt vor, aber NICHT die Ankunftswelt")
    void vorschlaegeBeimSetzen() {
        Spielerprobe spieler = inAltfurt(72, 0, -60, 0);

        List<String> vorschlaege = baum.vorschlaege("portal setzen ", spieler);

        assertTrue(vorschlaege.contains("vogtei"), vorschlaege.toString());
        assertTrue(vorschlaege.contains("kornmark"), vorschlaege.toString());
        // Die Ankunftswelt NICHT: Ein Tor dorthin waere ein Tor nach nebenan, und was
        // abgelehnt wird, braucht nicht angeboten zu werden.
        assertTrue(!vorschlaege.contains(ANKUNFT), vorschlaege.toString());
    }

    @Test
    @DisplayName("10b - entfernen schlaegt nur vor, wohin ein Tor fuehrt")
    void vorschlaegeBeimEntfernen() {
        Spielerprobe spieler = inAltfurt(73, 0, -60, 0);

        List<String> vorschlaege = baum.vorschlaege("portal entfernen ", spieler);

        assertEquals(List.of("hochwald"), vorschlaege,
                "nur die Welt mit Tor - alles andere waere ein Angebot ins Leere");
    }
}
