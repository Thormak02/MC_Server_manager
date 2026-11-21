package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /freimarken} GETIPPT - der letzte Befehl, der keine Durchlaufprobe hatte.
 *
 * <p>Moeglich wurde das erst durch den Auszug am 06.11.2026: Der Baum stand bis dahin in
 * {@code FreimarkenPlugin} selbst, und ein {@code JavaPlugin} laesst sich ohne
 * Paper-Klassenlader nicht bauen. Jetzt steht er in {@link BundBefehle} wie bei allen
 * sechs anderen Befehlsgruppen - und laesst sich tippen.
 *
 * <p><b>Sieben Zweige, und sie teilen sich in drei Gattungen:</b> zwei fuer jeden Spieler
 * (der Stand und der Preis), vier nur mit {@code freimarken.verwalten}, und einer, der
 * einer anderen Klasse gehoert ({@code spielzeit}). Die vier Verwaltungszweige kommen als
 * Griffe herein, und diese Probe gibt ihnen Griffe, die MITSCHREIBEN - so steht fest,
 * welches Wort bei welchem Griff landet. Vorher sagte darueber nichts etwas: Im Quelltext
 * sind es vier gleich gebaute Bloecke, und eine Zahltafel zaehlt vier Aufrufe.
 */
class FreimarkenBefehlTest {

    private static final String VERSION = "0.1.0";
    private static final String JAVA = "25";
    private static final String MINECRAFT = "1.21.4";

    /** Die Grenzen von {@code /freimarken parzelle <x> <z>} - klein und nachpruefbar. */
    private static final int VON = -3;
    private static final int BIS = 2;

    /** Welcher Griff gerufen wurde, in der Reihenfolge - der Kern dieser Probe. */
    private final List<String> gerufen = new ArrayList<>();

    private Befehlsprobe baum;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        gerufen.clear();
        baum = new Befehlsprobe(List.of(bund().freimarken()));
    }

    private BundBefehle bund() {
        return new BundBefehle(Sprachprobe.spracheAusDemJar(),
                new BundBefehle.Stand(VERSION, JAVA, MINECRAFT, false),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                // Ein Platzhalter fuer den Zweig von SpielzeitBefehle: Diese Probe fragt,
                // ob /freimarken spielzeit DORT landet - was der Zweig dann tut, haelt
                // SpielzeitBefehleTest.
                Commands.literal("spielzeit").executes(z -> {
                    gerufen.add("spielzeit");
                    return Command.SINGLE_SUCCESS;
                }),
                // Derselbe Platzhalter fuer den Zweig von BlockBefehle: Diese Probe
                // fragt, ob /freimarken blockbuch DORT landet - was der Zweig dann tut,
                // haelt BlockBefehlTest.
                Commands.literal("blockbuch").executes(z -> {
                    gerufen.add("blockbuch");
                    return Command.SINGLE_SUCCESS;
                }),
                (empfaenger, wer) -> gerufen.add("welt"),
                (empfaenger, wer) -> gerufen.add("vorgenerieren"),
                (empfaenger, wer) -> gerufen.add("neuladen:" + wer),
                (empfaenger, wer) -> gerufen.add("messstelle"),
                (empfaenger, x, z) -> gerufen.add("parzelle " + x + "/" + z),
                new BundBefehle.Geldzweig() {
                    @Override
                    public void tue(Audience empfaenger, String wer, String spieler,
                                    long betrag) {
                        gerufen.add("geld:" + wer + ":" + spieler + ":" + betrag);
                    }

                    @Override
                    public void hilfe(Audience empfaenger) {
                        gerufen.add("geld-hilfe");
                    }
                },
                VON, BIS);
    }

    private Spielerprobe spieler(int zahl, String... rechte) {
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl), rechte);
    }

    @Test
    @DisplayName("44 - /freimarken ohne Recht: vier Zeilen, die Verwaltungszeile fehlt")
    void ohneRechtVierZeilen() throws CommandSyntaxException {
        Spielerprobe ohneRecht = spieler(111);

        assertEquals(1, baum.tippe("freimarken", ohneRecht));

        assertEquals(List.of("&#c6a15bDer Freimarkenbund",
                        "&#a5b4bfStand 0.1.0 · Java 25 · 1.21.4",
                        "&#6e7a82Ablage MariaDB",
                        "&#a5b4bf/parzelle · /mark · /freimarken preis <anzahl>"
                                + " · /freimarken spielzeit"),
                ohneRecht.chatBild(),
                "wer das Recht nicht hat, liest die Verwaltungszeile nie");
        assertEquals(List.of(), gerufen, "und kein Verwaltungsgriff wird gerufen");
    }

    @Test
    @DisplayName("44b - mit Recht kommt die fuenfte Zeile dazu")
    void mitRechtFuenfZeilen() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(112, BundBefehle.RECHT_VERWALTEN);

        assertEquals(1, baum.tippe("freimarken", mitRecht));

        assertEquals(5, mitRecht.chatBild().size(), "vier Zeilen plus die Verwaltung: "
                + mitRecht.chatBild());
        assertEquals("&#6e7a82/freimarken welt · parzelle <x> <z> · neuladen",
                mitRecht.chatBild().get(4),
                "und sie steht ZULETZT - ein Spieler liest sie sonst vor den Befehlen,"
                        + " die ihn angehen");
    }

    @Test
    @DisplayName("44c - /freimarken preis nennt Preis und Summe, auch ohne Recht")
    void preisNenntPreisUndSumme() throws CommandSyntaxException {
        Spielerprobe spieler = spieler(113);

        assertEquals(1, baum.tippe("freimarken preis 1", spieler));

        assertEquals(2, spieler.chatBild().size(), "zwei Zeilen: " + spieler.chatBild());
        assertEquals(List.of(), gerufen, "der Preis ist kein Verwaltungszweig");
    }

    @Test
    @DisplayName("44d - preis nimmt nur Zahlen von 0 bis 64, und nur mit Zahl")
    void preisNimmtNurZahlenImRahmen() {
        Spielerprobe spieler = spieler(114);

        // Ohne Zahl gibt es den Zweig nicht - am Wort preis haengt kein executes.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken preis", spieler));
        // Und ausserhalb des Rahmens auch nicht. Die 64 ist die Hoechstzahl der
        // Parzellen einer Achse; eine 65 waere eine Parzelle, die es nicht gibt.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken preis 65", spieler));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken preis -1", spieler));
        assertEquals(List.of(), spieler.chatBild());
    }

    @Test
    @DisplayName("44e - jeder Verwaltungszweig landet bei seinem eigenen Griff")
    void jederVerwaltungszweigLandetBeiSeinemGriff() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(115, BundBefehle.RECHT_VERWALTEN);

        baum.tippe("freimarken welt", mitRecht);
        baum.tippe("freimarken vorgenerieren", mitRecht);
        baum.tippe("freimarken neuladen", mitRecht);
        baum.tippe("freimarken spielzeit", mitRecht);

        // Im Quelltext sind das vier gleich gebaute Bloecke, die sich in einem Wort und
        // einem Griff unterscheiden. Vertauscht sagt /freimarken welt die Vorgenerierung
        // auf - und keine Zahltafel sieht es, weil jeder Griff weiter einmal gerufen wird.
        assertEquals(List.of("welt", "vorgenerieren", "neuladen:Cara", "spielzeit"),
                gerufen);
    }

    @Test
    @DisplayName("44f - parzelle gibt x und z in DIESER Reihenfolge weiter")
    void parzelleGibtXUndZInDieserReihenfolge() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(116, BundBefehle.RECHT_VERWALTEN);

        assertEquals(1, baum.tippe("freimarken parzelle -3 2", mitRecht));

        // Zwei Zahlen nebeneinander, und beide heissen im Baum nur "x" und "z": Ein
        // Tausch uebersetzt fehlerfrei, und der Betreiber landet auf der gespiegelten
        // Parzelle. Die Werte sind mit Absicht verschieden und eins davon negativ.
        assertEquals(List.of("parzelle -3/2"), gerufen);
    }

    @Test
    @DisplayName("44g - die Grenzen von parzelle kommen aus der Stadt")
    void dieGrenzenKommenAusDerStadt() {
        Spielerprobe mitRecht = spieler(117, BundBefehle.RECHT_VERWALTEN);

        // VON und BIS sind hier klein gesetzt, damit die Grenze pruefbar ist. Stuende im
        // Baum eine geratene Zahl statt grenze.von()/bis(), liesse er diese Nummern zu -
        // und mitteX davon laege jenseits der Weltgrenze.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken parzelle " + (VON - 1) + " 0", mitRecht));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken parzelle 0 " + (BIS + 1), mitRecht));
        assertEquals(List.of(), gerufen);
    }

    @Test
    @DisplayName("44h - ohne Recht gibt es die vier Verwaltungszweige nicht")
    void ohneRechtKeineVerwaltung() {
        Spielerprobe ohneRecht = spieler(118);

        for (String zweig : List.of("welt", "vorgenerieren", "neuladen",
                "parzelle 0 0")) {
            assertThrows(CommandSyntaxException.class,
                    () -> baum.tippe("freimarken " + zweig, ohneRecht),
                    zweig + " haengt am Recht freimarken.verwalten");
        }
        assertEquals(List.of(), gerufen);
    }

    @Test
    @DisplayName("44i - auf der Konsole antwortet /freimarken, anders als /mark")
    void aufDerKonsoleAntwortetFreimarken() throws CommandSyntaxException {
        // Der Unterschied zu /mark und /parzelle: Dort steht ein requires auf
        // getExecutor() instanceof Player, hier nicht. Die Konsole ist ein Betreiber und
        // soll den Stand lesen koennen - mit Verwaltungszeile, denn sie darf alles.
        assertEquals(1, baum.tippeAufDerKonsole("freimarken"));

        assertEquals(5, baum.konsoleBild().size(),
                "die Konsole liest alle fuenf Zeilen: " + baum.konsoleBild());
        assertEquals("&#c6a15bDer Freimarkenbund", baum.konsoleBild().get(0));
    }

    @Test
    @DisplayName("44j - geld gibt Absender, Spieler und Betrag in DIESER Reihenfolge"
            + " weiter")
    void geldGibtDreiAngabenInDieserReihenfolge() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(119, BundBefehle.RECHT_GELD);

        assertEquals(1, baum.tippe("freimarken geld Anna -250", mitRecht));

        // Drei Angaben, zwei davon Namen: Ein Tausch von Absender und Spieler uebersetzt
        // fehlerfrei - und dann bucht der Befehl auf das Konto des Betreibers und
        // schreibt den Spieler ins Protokoll. Das Minus steht mit Absicht da: Es geht
        // als Vorzeichen durch den Baum und nicht als eigener Zweig.
        assertEquals(List.of("geld:Cara:Anna:-250"), gerufen);
    }

    @Test
    @DisplayName("44k - jede unvollstaendige Form zeigt die Benutzung")
    void unvollstaendigeFormZeigtBenutzung() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(120, BundBefehle.RECHT_GELD);

        // Ohne eigenes .executes an BEIDEN Stellen endet das in Brigadiers englischem
        // "Unknown or incomplete command" - und der Betreiber sucht den Fehler bei sich.
        assertEquals(1, baum.tippe("freimarken geld", mitRecht));
        assertEquals(1, baum.tippe("freimarken geld Anna", mitRecht));

        assertEquals(List.of("geld-hilfe", "geld-hilfe"), gerufen);
    }

    @Test
    @DisplayName("44l - freimarken.verwalten allein oeffnet den Geldzweig NICHT")
    void verwaltenAlleinOeffnetGeldNicht() {
        // DIE ZUSAGE DES EIGENEN RECHTS: Wer Welten nachsehen und Spieler versetzen
        // darf, darf nicht zugleich Konten fuellen. Ohne diese Probe waere der
        // Unterschied nur ein Kommentar.
        Spielerprobe nurVerwalten = spieler(121, BundBefehle.RECHT_VERWALTEN);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken geld Anna 1", nurVerwalten));
        assertEquals(List.of(), gerufen);

        // Und die Gegenrichtung: Das Geldrecht allein oeffnet die Verwaltungszweige
        // nicht. Sonst waere oben nicht das Recht geprueft, sondern irgendetwas.
        Spielerprobe nurGeld = spieler(122, BundBefehle.RECHT_GELD);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken welt", nurGeld));
        assertEquals(List.of(), gerufen);
    }
}
