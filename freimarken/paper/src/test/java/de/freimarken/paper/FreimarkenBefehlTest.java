package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                                    long betrag, Betreibergeld.Waehrung waehrung) {
                        // DIE WAEHRUNG GEHOERT IN DIE MITSCHRIFT [23.11.2026]: Ein Baum,
                        // der beide Literale an denselben Zweig haengt und dabei immer
                        // MARK hereingibt, waere ohne sie gruen - und das waere der
                        // Fehler, um den es ging, nur eine Ebene hoeher.
                        gerufen.add("geld:" + wer + ":" + spieler + ":" + betrag + ":"
                                + waehrung);
                    }

                    @Override
                    public void hilfe(Audience empfaenger,
                                      Betreibergeld.Waehrung waehrung) {
                        gerufen.add("geld-hilfe:" + waehrung);
                    }
                },
                new BundBefehle.Besitzzweig() {
                    @Override
                    public void uebertrage(Audience empfaenger, String wer,
                                           int parzelleX, int parzelleZ,
                                           String anName) {
                        gerufen.add("besitz-uebertragen:" + wer + ":" + parzelleX + "/"
                                + parzelleZ + ":" + anName);
                    }

                    @Override
                    public void gibFrei(Audience empfaenger, String wer, int parzelleX,
                                        int parzelleZ) {
                        gerufen.add("besitz-freigeben:" + wer + ":" + parzelleX + "/"
                                + parzelleZ);
                    }

                    @Override
                    public void hilfe(Audience empfaenger) {
                        gerufen.add("besitz-hilfe");
                    }
                },
                new BundBefehle.Altfurtzweig() {
                    @Override
                    public void sichere(Audience empfaenger, String wer, String bau,
                                        boolean bestaetigt) {
                        // NAME UND BESTAETIGUNG GEHOEREN IN DIE MITSCHRIFT: Ein Baum, der
                        // alle drei Formen an denselben Zweig haengt und dabei immer
                        // null und false hereingibt, waere ohne sie gruen - und dann
                        // ueberschriebe "sichern" jede Handarbeit.
                        gerufen.add("altfurt-sichern:" + wer + ":" + bau + ":"
                                + bestaetigt);
                    }

                    @Override
                    public List<String> namen(String angefangen) {
                        // GEFILTERT wie das echte Verzeichnis: Brigadier filtert nicht
                        // nach, und eine Attrappe, die alles herausgibt, verdeckte genau
                        // diesen Fehler.
                        return de.freimarken.kern.Namensvorschlag.zu(
                                List.of("buergeramt", "zunfthaus"), angefangen);
                    }

                    @Override
                    public void hilfe(Audience empfaenger) {
                        gerufen.add("altfurt-hilfe");
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
        // VOLLSTAENDIG SEIT DEM 27.11.2026: Jeder Zweig des Baums steht hier, und
        // jederZweigStehtInDerUebersicht haelt das - eine neue Verzweigung macht
        // jene Tafel rot, und diese Zeile hier zieht nach.
        //
        // NUR vorgenerieren fehlt mit Absicht; die Begruendung steht in der
        // Sprachvorlage und in NICHT_IN_DER_UEBERSICHT.
        assertEquals("&#6e7a82/freimarken welt · parzelle <x> <z> · neuladen"
                        + " · messstelle · blockbuch · blockbuch zurueck <radius>"
                        + " <minuten> · geld <spieler> <betrag> · goldgeld <spieler>"
                        + " <betrag> · besitz uebertragen <x> <z> <spieler> · besitz"
                        + " freigeben <x> <z> bestaetigen · altfurt sichern <bau>",
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
                "parzelle 0 0", "altfurt", "altfurt sichern")) {
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
        assertEquals(List.of("geld:Cara:Anna:-250:MARK"), gerufen);
    }

    @Test
    @DisplayName("44j-gold - goldgeld landet im SELBEN Zweig, aber mit der Goldmark")
    void goldgeldGibtDieWaehrungMit() throws CommandSyntaxException {
        // Bis zum 23.11.2026 gab es diesen Zweig nicht, und damit im ganzen Haus keinen
        // Weg, Goldmark auf ein Konto zu buchen. Wer 40 Goldmark verlor, bekam auf
        // "/freimarken geld Anna 40" vierzig MARK - ein Achthundertstel, und die
        // Antwortzeile war dabei richtig.
        Spielerprobe mitRecht = spieler(219, BundBefehle.RECHT_GELD);

        assertEquals(1, baum.tippe("freimarken goldgeld Anna -250", mitRecht));

        assertEquals(List.of("geld:Cara:Anna:-250:GOLDMARK"), gerufen);
    }

    @Test
    @DisplayName("44j-schranke - die Goldmark hat ihre EIGENE, viel kleinere Grenze")
    void goldgeldHatSeineEigeneSchranke() throws CommandSyntaxException {
        // Das Paar aus Befehlswort und Schranke steht an EINER Stelle
        // (Betreibergeld.Waehrung). Diese Probe ist der Beleg, dass es wirklich
        // auseinandergehalten wird: Eine Million Goldmark sind achthundert Millionen
        // Mark, eine Milliarde waeren achthundert Milliarden.
        Spielerprobe mitRecht = spieler(220, BundBefehle.RECHT_GELD);

        // In MARK geht die Zahl durch ...
        assertEquals(1, baum.tippe("freimarken geld Anna 1000001", mitRecht));
        // ... in GOLDMARK nicht. Brigadier weist sie am Argument ab, bevor der Zweig
        // laeuft - darum der Wurf und nicht ein Ergebnis.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken goldgeld Anna 1000001", mitRecht));

        assertEquals(List.of("geld:Cara:Anna:1000001:MARK"), gerufen,
                "nur die Markbuchung darf angekommen sein");
    }

    @Test
    @DisplayName("44k - jede unvollstaendige Form zeigt die Benutzung")
    void unvollstaendigeFormZeigtBenutzung() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(120, BundBefehle.RECHT_GELD);

        // Ohne eigenes .executes an BEIDEN Stellen endet das in Brigadiers englischem
        // "Unknown or incomplete command" - und der Betreiber sucht den Fehler bei sich.
        assertEquals(1, baum.tippe("freimarken geld", mitRecht));
        assertEquals(1, baum.tippe("freimarken geld Anna", mitRecht));
        // BEIDE Worte, denn beide Teilbaeume kommen aus demselben Helfer - eine Hilfe,
        // die das falsche Wort nennt, schickt den Betreiber in den anderen Befehl.
        assertEquals(1, baum.tippe("freimarken goldgeld", mitRecht));
        assertEquals(1, baum.tippe("freimarken goldgeld Anna", mitRecht));

        assertEquals(List.of("geld-hilfe:MARK", "geld-hilfe:MARK",
                        "geld-hilfe:GOLDMARK", "geld-hilfe:GOLDMARK"),
                gerufen);
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
        // Und derselbe Riegel am Goldzweig: Er kommt aus demselben Helfer, aber eine
        // zweite Tuer, die niemand prueft, ist kein Riegel.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken goldgeld Anna 1", nurVerwalten));
        assertEquals(List.of(), gerufen);

        // Und die Gegenrichtung: Das Geldrecht allein oeffnet die Verwaltungszweige
        // nicht. Sonst waere oben nicht das Recht geprueft, sondern irgendetwas.
        Spielerprobe nurGeld = spieler(122, BundBefehle.RECHT_GELD);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken welt", nurGeld));
        assertEquals(List.of(), gerufen);
    }
    @Test
    @DisplayName("44m - /freimarken besitz uebertragen reicht Stelle und Name weiter")
    void besitzUebertragen() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(123, BundBefehle.RECHT_BESITZ);

        assertEquals(1, baum.tippe("freimarken besitz uebertragen -3 2 Anna",
                mitRecht));

        // VIER Angaben, zwei davon Zahlen und zwei davon Namen: Ein Tausch von x und z
        // oder von Absender und Empfaenger uebersetzt fehlerfrei - und dann wechselt die
        // falsche Parzelle den Besitzer oder der Betreiber bekommt sie selbst. Die
        // Reihenfolge steht nur hier fest.
        assertEquals(List.of("besitz-uebertragen:Cara:-3/2:Anna"), gerufen);
    }

    @Test
    @DisplayName("44n - /freimarken besitz freigeben verlangt bestaetigen")
    void besitzFreigebenVerlangtBestaetigung() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(124, BundBefehle.RECHT_BESITZ);

        assertEquals(1, baum.tippe("freimarken besitz freigeben -3 2 bestaetigen",
                mitRecht));
        assertEquals(List.of("besitz-freigeben:Cara:-3/2"), gerufen);

        // OHNE das Wort fuehrt dieselbe Zeile zur Benutzung und nicht zur Tat. Das ist
        // die Sicherung: Freigeben loescht Besitz ohne Rueckzahlung, und ein Tippfehler
        // in der Nummer traefe die Parzelle eines Unbeteiligten.
        gerufen.clear();
        assertEquals(1, baum.tippe("freimarken besitz freigeben -3 2", mitRecht));
        assertEquals(List.of("besitz-hilfe"), gerufen);
    }

    @Test
    @DisplayName("44o - jede unvollstaendige Form des Besitzzweigs zeigt die Benutzung")
    void besitzUnvollstaendig() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(125, BundBefehle.RECHT_BESITZ);

        // Fuenf Stellen, an denen die Angaben abbrechen koennen. Ohne eigenes .executes
        // an JEDER endet es in Brigadiers englischem "Unknown or incomplete command".
        assertEquals(1, baum.tippe("freimarken besitz", mitRecht));
        assertEquals(1, baum.tippe("freimarken besitz uebertragen", mitRecht));
        assertEquals(1, baum.tippe("freimarken besitz uebertragen -3", mitRecht));
        assertEquals(1, baum.tippe("freimarken besitz uebertragen -3 2", mitRecht));
        assertEquals(1, baum.tippe("freimarken besitz freigeben", mitRecht));
        assertEquals(1, baum.tippe("freimarken besitz freigeben -3", mitRecht));

        assertEquals(List.of("besitz-hilfe", "besitz-hilfe", "besitz-hilfe",
                        "besitz-hilfe", "besitz-hilfe", "besitz-hilfe"),
                gerufen);
    }

    @Test
    @DisplayName("44p - eine Stelle jenseits der Stadt erreicht den Zweig nicht")
    void besitzAusserhalbDerGrenzen() {
        Spielerprobe mitRecht = spieler(126, BundBefehle.RECHT_BESITZ);

        // Die Grenzen kommen aus der Stadt und stehen am Argument. Ohne sie reichte
        // mitteX(10000) weit jenseits der Weltgrenze, und die Antwort waere eine
        // Ausnahme statt eines Satzes.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken besitz uebertragen 99 2 Anna", mitRecht));
        assertEquals(List.of(), gerufen);
    }

    @Test
    @DisplayName("44q - freimarken.geld oeffnet den Besitzzweig NICHT, und umgekehrt")
    void geldUndBesitzSindGetrennt() {
        // DIE ZUSAGE DES EIGENEN RECHTS: Eine falsche Buchung heilt eine Gegenbuchung,
        // ein freigegebener Besitz nicht. Ohne diese Probe waere der Unterschied nur
        // ein Kommentar.
        Spielerprobe nurGeld = spieler(127, BundBefehle.RECHT_GELD);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken besitz uebertragen -3 2 Anna", nurGeld));
        assertEquals(List.of(), gerufen);

        Spielerprobe nurBesitz = spieler(128, BundBefehle.RECHT_BESITZ);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken geld Anna 1", nurBesitz));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("freimarken welt", nurBesitz));
        assertEquals(List.of(), gerufen);
    }

    @Test
    @DisplayName("44m - /freimarken altfurt: drei Formen, drei verschiedene Rufe")
    void altfurtSichern() throws CommandSyntaxException {
        Spielerprobe mitRecht = spieler(141, BundBefehle.RECHT_VERWALTEN);

        // Das nackte Wort zeigt die Benutzung und sichert NICHTS. Ohne eigenes .executes
        // endete es in Brigadiers englischem "Unknown or incomplete command".
        assertEquals(1, baum.tippe("freimarken altfurt", mitRecht));
        // Ohne Namen: alles, was noch keine Datei hat.
        assertEquals(1, baum.tippe("freimarken altfurt sichern", mitRecht));
        // Mit Namen: nur dieser, und OHNE Bestaetigung.
        assertEquals(1, baum.tippe("freimarken altfurt sichern buergeramt", mitRecht));
        // Erst das vierte Wort erlaubt das Ueberschreiben.
        assertEquals(1, baum.tippe("freimarken altfurt sichern buergeramt bestaetigen",
                mitRecht));

        assertEquals(List.of("altfurt-hilfe",
                        "altfurt-sichern:Cara:null:false",
                        "altfurt-sichern:Cara:buergeramt:false",
                        "altfurt-sichern:Cara:buergeramt:true"),
                gerufen);
    }

    @Test
    @DisplayName("44n - die Namen der Bauten werden vorgeschlagen")
    void altfurtNamenVorgeschlagen() {
        Spielerprobe mitRecht = spieler(142, BundBefehle.RECHT_VERWALTEN);

        // GEFRAGT und nicht kopiert: /freimarken neuladen tauscht das Verzeichnis aus.
        assertEquals(List.of("buergeramt", "zunfthaus"),
                baum.vorschlaege("freimarken altfurt sichern ", mitRecht));
        assertEquals(List.of("zunfthaus"),
                baum.vorschlaege("freimarken altfurt sichern zunf", mitRecht));
        assertEquals(List.of("bestaetigen"),
                baum.vorschlaege("freimarken altfurt sichern buergeramt ", mitRecht),
                "nach dem Namen steht nur noch das Bestaetigen");
        assertEquals(List.of(), gerufen, "Vorschlaege sichern nichts");
    }


    /** Dieselbe Sprache, die der Baum bekommt - fuer die Uebersichtszeilen. */
    private static Sprache sprache() {
        return Sprachprobe.spracheAusDemJar();
    }

    // === Die Uebersicht und der Baum ====================================

    /**
     * Zweige, die mit ABSICHT in keiner Uebersichtszeile stehen.
     *
     * <p>Je Eintrag ein Grund, und der Grund steht hier und nicht im TODO: Wer einen
     * Zweig hier eintraegt, nimmt ihn aus der Tafel - das soll man begruenden
     * muessen.
     */
    private static final Map<String, String> NICHT_IN_DER_UEBERSICHT = Map.of(
            "vorgenerieren", "Laeuft einmal beim Einrichten und erzeugt Last fuer"
                    + " Minuten. Wer ihn braucht, bekommt ihn im Startbericht"
                    + " genannt; in einer Zeile, die bei jedem /freimarken kommt,"
                    + " waere er eine Einladung.",
            "preis", "Steht in der Spielerzeile mit seinem Argument\\<anzahl> und"
                    + " wird darum von der Wortsuche unten nicht als blankes Wort"
                    + " gefunden - er IST genannt.");

    @Test
    @DisplayName("Jeder Zweig von /freimarken steht in einer Uebersichtszeile")
    void jederZweigStehtInDerUebersicht() {
        // DER BAUM IST DIE QUELLE, nicht eine Liste von Hand: "geld" und "goldgeld"
        // entstehen aus der Waehrung, also findet sie keine Textsuche nach
        // literal("geld"). Eine abgeschriebene Liste haette genau die zwei nicht
        // gekannt - und das sind die zwei, mit denen ein Betreiber Geld bewegt.
        var zweige = new java.util.TreeSet<String>();
        for (var kind : bund().freimarken().getChildren()) {
            zweige.add(kind.getName());
        }
        assertTrue(zweige.size() >= 10,
                "nur " + zweige.size() + " Zweige gefunden (" + zweige + ") - dann"
                        + " geht diese Tafel den Baum nicht mehr ab, und sie ist"
                        + " gruen, ohne etwas geprueft zu haben");

        String uebersicht = Sprachprobe.nurText(sprache().eineZeile(
                        BundSatz.STAND_BEFEHLE, Einsatz.keine()))
                + " " + Sprachprobe.nurText(sprache().eineZeile(
                        BundSatz.STAND_BEFEHLE_VERWALTUNG, Einsatz.keine()));

        var fehlen = new java.util.TreeSet<String>();
        for (String zweig : zweige) {
            if (NICHT_IN_DER_UEBERSICHT.containsKey(zweig)) {
                continue;
            }
            if (!uebersicht.contains(zweig)) {
                fehlen.add(zweig);
            }
        }
        assertEquals(java.util.Set.of(), fehlen,
                "Diese Zweige von /freimarken gibt es im Baum, aber in keiner der"
                        + " zwei Uebersichtszeilen (bund.stand.befehle und"
                        + " bund.stand.befehle-verwaltung). Ein Befehl, den niemand"
                        + " nennt, ist einer, auf den niemand kommt: /hilfe kennt"
                        + " Unterzweige nicht, und die plugin.yml liest kein"
                        + " Moderator. Entweder in die Zeile eintragen oder mit"
                        + " Grund in NICHT_IN_DER_UEBERSICHT: " + fehlen
                        + " -- Uebersicht ist: " + uebersicht);
    }

    @Test
    @DisplayName("Die Uebersicht nennt keinen Zweig, den es nicht gibt")
    void uebersichtNenntKeinenErfundenenZweig() {
        // DIE GEGENRICHTUNG, und sie faengt den Fall, der nach einer Umbenennung
        // bleibt: Die Zeile nennt weiter das alte Wort, der Baum kennt es nicht mehr,
        // und der Moderator tippt etwas, das Brigadier mit einer englischen Meldung
        // abweist.
        // GANZ hinunter und nicht zwei Ebenen: "bestaetigen" liegt unter
        // besitz > freigeben > <x> > <z>, also fuenf Ebenen tief. Mit zwei Ebenen
        // meldete die Tafel es als erfundenen Befehl - und ein Fehltreffer ist eine
        // Einladung, die Tafel auszunehmen.
        var zweige = new java.util.TreeSet<String>();
        var offen = new java.util.ArrayDeque<
                com.mojang.brigadier.tree.CommandNode<CommandSourceStack>>();
        offen.add(bund().freimarken());
        while (!offen.isEmpty()) {
            var knoten = offen.poll();
            zweige.add(knoten.getName());
            offen.addAll(knoten.getChildren());
        }

        // Woerter, die in den Zeilen stehen duerfen, ohne ein Zweig zu sein.
        //
        // "zurueck" steht hier aus einem Grund, der NUR fuer diese Probe gilt: Der
        // blockbuch-Zweig ist hier ein Platzhalter ohne Unterzweige (siehe bund()),
        // also kennt dieser Baum sein "zurueck" nicht. Dass es den Zweig wirklich
        // gibt, haelt BlockBefehlTest.
        var erlaubt = new java.util.TreeSet<>(java.util.List.of(
                "freimarken", "parzelle", "mark", "team", "hilfe", "zurueck"));

        var erfunden = new java.util.TreeSet<String>();
        for (String satz : java.util.List.of(
                Sprachprobe.nurText(sprache().eineZeile(BundSatz.STAND_BEFEHLE,
                        Einsatz.keine())),
                Sprachprobe.nurText(sprache().eineZeile(
                        BundSatz.STAND_BEFEHLE_VERWALTUNG, Einsatz.keine())))) {
            // DIE ARGUMENTE HERAUS, und zwar VOR dem Zerlegen: "\<anzahl>" ist ein
            // Hilfetext fuer den Spieler und kein Zweig. Ohne diesen Schnitt meldete
            // die Tafel anzahl, radius und minuten als erfundene Befehle - und ein
            // Fehltreffer in einer Tafel ist eine Einladung, sie auszunehmen.
            for (String wort : satz.replaceAll("<[^>]*>", " ").split("[^a-z-]+")) {
                if (wort.length() < 4 || erlaubt.contains(wort)
                        || zweige.contains(wort)) {
                    continue;
                }
                erfunden.add(wort);
            }
        }
        assertEquals(java.util.Set.of(), erfunden,
                "Diese Woerter stehen in der Uebersicht, sind aber kein Zweig des"
                        + " Befehlsbaums - ein Moderator tippt sie und bekommt"
                        + " Brigadiers englische Absage. Entweder berichtigen oder,"
                        + " wenn es ein anderer Befehl des Plugins ist, oben in"
                        + " erlaubt eintragen: " + erfunden);
    }
}
