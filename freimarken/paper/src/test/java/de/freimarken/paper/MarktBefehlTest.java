package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Warenprobe;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /markt} GETIPPT - die Staende des Bundes.
 *
 * <p>Dieselbe Bauart wie {@link BerufBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest.
 *
 * <p><b>Dieser Befehl ist am 10.11.2026 zweimal umgebaut worden.</b> Vormittags fiel
 * {@code verkaufen} weg - verkauft wird am Schild. Nachmittags fiel die TAFEL
 * {@code bundesmarkt.ankauf}, mit einem Satz des Betreibers: "es kann ja nur ge- und
 * verkauft werden, wofuer es ein Schild gibt." Was {@code /markt} seither zeigt, sind
 * nicht Preise aus einer Datei, sondern die STAENDE aus der Datenbank - samt Ort.
 *
 * <p>Damit ist der Bundesmarkt erst wirklich ein ORT: Vorher stand hier eine
 * Weltpruefung, die das nachbilden sollte. Ein Schild steht, wo es steht, und diese
 * Liste sagt, wo.
 */
@DisplayName("/markt getippt")
class MarktBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String HOCHWALD = "freimarken:hochwald";

    /**
     * Die Uhr, die KASSE und BEFEHL teilen.
     *
     * <p>Dieselbe, und das ist keine Sparsamkeit: Der heutige Rest fragt die Kasse nach
     * den Buchungen SEIT dem Tagesbeginn. Haette die Kasse die Systemuhr und der Befehl
     * eine gestellte, laege eine vorgebuchte Zeile je nach Tageszeit vor oder nach jener
     * Grenze - und die Probe waere nachts rot und mittags gruen. Gefunden hat das genau
     * diese Probe, und zwar nachts.
     */
    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-07T12:00:00Z"), java.time.ZoneOffset.UTC);

    private SpeicherKasse kasse;
    private Shopablage shops;
    private Spielerprobe anna;
    private World altfurt;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(ALTFURT);
        kasse = new SpeicherKasse(0L, UHR);
        shops = new SpeicherShops();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                MarktBefehle.RECHT).inWelt(altfurt);
    }

    /**
     * Ein Stand des Bundes an dieser Stelle.
     *
     * <p>Kein Besitzer - das IST ein Stand des Bundes. Die Preise stehen in der Zeile,
     * wie bei jedem Laden; eine 0 heisst "in dieser Richtung nicht".
     */
    private Kistenshop stand(int x, int menge, long kauf, long ankauf, String ware) {
        return new Kistenshop(ALTFURT, x, -60, 19, x, -60, 20, null, menge, kauf,
                ankauf, new Warenprobe(ware, "inhalt-von-" + ware), Set.of());
    }

    /** Ein Laden eines Spielers - er gehoert NICHT in diese Liste. */
    private Kistenshop annasLaden(int x) {
        return new Kistenshop(ALTFURT, x, -60, 19, x, -60, 20,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), 16, 100L, 80L,
                new Warenprobe("STONE", "inhalt-von-STONE"), Set.of());
    }

    private Befehlsprobe probe(long deckel, int jeSeite) {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitMarkt(Marktwerte.VORGABE.mitDeckel(deckel))
                .mitHandel(Shopwerte.VORGABE.mitZeilen(jeSeite)));
        var befehle = new MarktBefehle(shops, stand, kasse, UHR,
                Sprachprobe.spracheAusDemJar(), Logger.getAnonymousLogger());
        return new Befehlsprobe(List.of(befehle.markt()));
    }

    private Befehlsprobe probe() {
        return probe(5_000L, 10);
    }

    /** Was Anna gelesen hat, als Bild. */
    private List<String> gelesen() {
        return anna.chatBild();
    }

    @Test
    @DisplayName("/markt nennt jeden Stand mit Ort, Ware und Preis")
    void marktNenntDieStaende() throws CommandSyntaxException {
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        shops.schreibe(stand(104, 1, 20L, 11L, "DIAMOND"));

        probe().tippe("markt", anna);

        assertEquals(4, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("(2)")
                && gelesen().get(0).contains("Seite 1 von 1"), gelesen().get(0));
        // Die Ordnung ist die der Ablage: Welt, dann x, numerisch. Der Stand bei 100
        // steht vor dem bei 104.
        assertTrue(gelesen().get(1).contains("100/-60/19")
                && gelesen().get(1).contains("5x STONE"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("DIAMOND"), gelesen().get(2));
        assertTrue(gelesen().get(3).contains("5.000 Mark"), gelesen().get(3));
    }

    @Test
    @DisplayName("Ein Stand, der nur ankauft, nennt NUR den Ankauf")
    void ankaufstandNenntNurAnkauf() throws CommandSyntaxException {
        // Die zwei Blickwinkel, und sie sind in diesem Projekt schon einmal vertauscht
        // worden: verkauftWare() heisst "der Shop verkauft", also KAUFT der Kunde.
        // Vertauscht stuende an einem reinen Ankaufstand "Kauf 0 Mark" - und eine 0 als
        // Preis ist genau das, was Shopschild als PREIS_BEIDE_NULL abweist.
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));

        probe().tippe("markt", anna);

        assertTrue(gelesen().get(1).contains("Ankauf 1 Mark"), gelesen().get(1));
        assertTrue(!gelesen().get(1).contains("Kauf 0"), gelesen().get(1));
    }

    @Test
    @DisplayName("Ein LADEN EINES SPIELERS steht nicht in der Liste des Bundes")
    void spielerladenGehoertNichtDazu() throws CommandSyntaxException {
        // Der Bundesmarkt ist, was dem Bund gehoert - und das ist die Frage
        // Kistenshop.demBund(). Ohne diesen Filter stuende jeder Laden der Welt in
        // /markt, und die Auskunft waere eine andere als ihr Name.
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        shops.schreibe(annasLaden(110));

        probe().tippe("markt", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("(1)"), gelesen().get(0));
        assertTrue(!String.join(" ", gelesen()).contains("110/"), gelesen().toString());
    }

    @Test
    @DisplayName("Ohne Stand sagt es die Liste - und nennt den Grund")
    void ohneStand() throws CommandSyntaxException {
        // Der Normalzustand eines frischen Servers, und seit dem 10.11.2026 auch der
        // Weg, den Ankauf abzuschalten: Es steht kein Schild.
        probe().tippe("markt", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("keinen Stand"), gelesen().get(0));
    }

    @Test
    @DisplayName("Die Liste blaettert - mit derselben Seitengroesse wie /shop liste")
    void listeBlaettert() throws CommandSyntaxException {
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        shops.schreibe(stand(104, 5, 0L, 2L, "DIRT"));
        shops.schreibe(stand(108, 5, 0L, 3L, "SAND"));

        probe(5_000L, 2).tippe("markt liste", anna);

        assertEquals(5, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Seite 1 von 2"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("STONE"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("DIRT"), gelesen().get(2));
        assertTrue(gelesen().get(3).contains("/markt liste 2"), gelesen().get(3));
    }

    @Test
    @DisplayName("Die LETZTE Seite zeigt den Rest und KEINEN Weiter-Hinweis")
    void letzteSeiteOhneHinweis() throws CommandSyntaxException {
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        shops.schreibe(stand(104, 5, 0L, 2L, "DIRT"));
        shops.schreibe(stand(108, 5, 0L, 3L, "SAND"));

        probe(5_000L, 2).tippe("markt liste 2", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Seite 2 von 2"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("SAND"), gelesen().get(1));
        assertTrue(!String.join(" ", gelesen()).contains("zeigt die naechste"),
                gelesen().toString());
    }

    @Test
    @DisplayName("Eine Seite, die es nicht gibt, nennt die Zahl der vorhandenen")
    void keineSolcheSeite() throws CommandSyntaxException {
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));

        probe(5_000L, 2).tippe("markt liste 9", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Seite 9 gibt es nicht"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("1 Seiten"), gelesen().get(0));
    }

    @Test
    @DisplayName("Die Liste darf man ueberall ansehen - sie nennt Orte und Preise")
    void listeUeberall() throws CommandSyntaxException {
        // Wer wissen will, ob sich der Weg nach Altfurt lohnt, soll nicht erst hingehen
        // muessen. Das ist der Unterschied zwischen einer Auskunft und einer Tat.
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe().tippe("markt liste", anna);

        assertTrue(gelesen().get(0).contains("Staende des Bundes"), gelesen().get(0));
    }

    @Test
    @DisplayName("VERKAUFEN gibt es nicht mehr - der Zweig ist weg, nicht nur stumm")
    void verkaufenGibtEsNichtMehr() {
        // DIE PROBE ZUR ENTSCHEIDUNG. Verkauft wird am Schild (Plan 7.1), und das muss am
        // Befehlsbaum zu sehen sein: Ein Zweig, der noch da waere und nur nichts taete,
        // liesse jeden, der die alte Hilfe gelesen hat, ins Leere tippen - und die
        // naechste Fassung baute ihn aus Gewohnheit wieder voll.
        Befehlsprobe baum = probe();

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("markt verkaufen", anna));
    }

    @Test
    @DisplayName("Der heutige Rest zaehlt NUR Verkaeufe - Arbeit schmaelert ihn nicht")
    void restZaehltNurVerkaeufe() throws CommandSyntaxException {
        // Mit einem gemeinsamen Grund waere jede der zwei Quellen durch die andere zu
        // verschliessen: Wer vormittags arbeitet, koennte nachmittags nicht verkaufen.
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        kasse.gutschreiben(anna.kennung(), 10_000L, Buchungsgrund.BERUF_VERDIENST);

        probe(100L, 10).tippe("markt", anna);

        assertTrue(gelesen().get(2).contains("100 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Was heute schon verkauft ist, fehlt im Rest")
    void restSinktMitDemVerkauften() throws CommandSyntaxException {
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));
        kasse.gutschreiben(anna.kennung(), 40L, Buchungsgrund.BUNDESMARKT_VERKAUF);

        probe(100L, 10).tippe("markt", anna);

        assertTrue(gelesen().get(2).contains("60 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("OHNE Deckel steht ein eigener Satz und keine Zahl")
    void ohneDeckelEigenerSatz() throws CommandSyntaxException {
        // ENTSCHIEDEN AM 10.11.2026: bundesmarkt.tagesdeckel: 0 heisst kein Deckel.
        // "Heute kannst du noch 0 Mark bekommen" waere das Gegenteil der Wahrheit, und
        // eine riesige Zahl liest niemand - darum ein eigener Satz.
        shops.schreibe(stand(100, 5, 0L, 1L, "STONE"));

        probe(Marktwerte.KEIN_DECKEL, 10).tippe("markt", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(2).contains("keinen Tagesdeckel"), gelesen().get(2));
        assertTrue(!gelesen().get(2).contains("0 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Eine gestoerte Ablage sagt es - und NICHT, es stehe kein Schild")
    void gestoerteAblage() throws CommandSyntaxException {
        // Eine Stoerung, die wie "es steht kein Schild" aussieht, laesst einen Spieler
        // auf einen Markt warten, der dasteht. Dieselbe Entscheidung wie bei
        // /shop liste.
        shops = stolpernde();

        probe().tippe("markt", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("nicht lesen"), gelesen().get(0));
    }

    @Test
    @DisplayName("Auf der Konsole gibt es keine Liste - sie hat kein Heute")
    void konsoleHatKeinHeute() throws CommandSyntaxException {
        // Die letzte Zeile nennt den heutigen Rest EINES SPIELERS, und den hat die
        // Konsole nicht. Eine eigene uebersetzte Zeile braucht das nicht: Wer auf der
        // Konsole tippt, ist der Betreiber.
        Befehlsprobe baum = probe();

        baum.tippeAufDerKonsole("markt");

        assertEquals(List.of("Der Bundesmarkt braucht einen Spieler."),
                baum.konsoleBild());
    }

    @Test
    @DisplayName("Ablage, Stand, Kasse, Uhr, Sprache und Protokoll sind Pflicht")
    void pflichtenGelten() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uhr = java.time.Clock.systemUTC();
        var sprache = Sprachprobe.spracheAusDemJar();
        var protokoll = Logger.getAnonymousLogger();

        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(null, stand, kasse, uhr, sprache, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(shops, null, kasse, uhr, sprache, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(shops, stand, null, uhr, sprache, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(shops, stand, kasse, null, sprache, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(shops, stand, kasse, uhr, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(shops, stand, kasse, uhr, sprache, null));
    }

    /**
     * Eine Ablage, die beim Lesen WIRFT - fuer die Stoerungsprobe.
     *
     * <p>Als Schnittstelle und nicht als Erbe von {@code SpeicherShops}: Jener ist
     * absichtlich {@code final}, denn eine Ablage, von der sich erben laesst, kann die
     * Haelfte ihrer Zusagen verlieren, ohne dass es auffaellt. Die uebrigen Methoden
     * werfen hier ebenfalls - dieser Befehl darf sie gar nicht rufen.
     */
    private static Shopablage stolpernde() {
        return new Shopablage() {
            @Override
            public List<Kistenshop> alle() {
                throw new IllegalStateException("die Datenbank schweigt");
            }

            @Override
            public Kistenshop anDemSchild(String welt, int x, int y, int z) {
                throw new AssertionError("/markt fragt kein einzelnes Schild");
            }

            @Override
            public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
                throw new AssertionError("/markt fragt keine Kiste");
            }

            @Override
            public List<Kistenshop> vonSpieler(UUID besitzer) {
                throw new AssertionError("/markt fragt keinen Besitzer");
            }

            @Override
            public void schreibe(Kistenshop shop) {
                throw new AssertionError("/markt schreibt nichts");
            }

            @Override
            public boolean loesche(String welt, int x, int y, int z) {
                throw new AssertionError("/markt loescht nichts");
            }
        };
    }
}
