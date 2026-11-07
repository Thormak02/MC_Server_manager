package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Ankaufliste;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /markt} GETIPPT - vom Wort bis zur Buchung.
 *
 * <p>Dieselbe Bauart wie {@link BerufBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest und was im Buch steht.
 *
 * <p><b>Die tragende Probe ist {@link #nurDieStueckeDieBezahltWerden()}:</b> Der Bund
 * nimmt nur so viele Stuecke, wie eine ganze Mark ergeben. Naehme er alles und rundete ab,
 * waere die Ware weg und der Pfennigrest verschwunden - und das faellt dem Spieler erst
 * auf, wenn er nachrechnet.
 */
@DisplayName("/markt getippt")
class MarktBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";

    /**
     * Die Uhr, die KASSE und BEFEHL teilen.
     *
     * <p>Dieselbe, und das ist keine Sparsamkeit: Der Tagesdeckel fragt die Kasse nach
     * den Buchungen SEIT dem Tagesbeginn. Haette die Kasse die Systemuhr und der Befehl
     * eine gestellte, laege eine vorgebuchte Zeile je nach Tageszeit vor oder nach jener
     * Grenze - und die Probe waere nachts rot und mittags gruen. Gefunden hat das genau
     * diese Probe, und zwar nachts.
     */
    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-07T12:00:00Z"), java.time.ZoneOffset.UTC);
    private static final String HOCHWALD = "freimarken:hochwald";

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherKasse kasse;
    private Spielerprobe anna;
    private World altfurt;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(ALTFURT);
        kasse = new SpeicherKasse(0L, UHR);
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                MarktBefehle.RECHT).inWelt(altfurt);
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("MarktprobeLog-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }

    /** Das Sortiment dieser Proben: Stein zu 20 Pfennigen, Diamant zu 11 Mark. */
    private static Marktwerte werte(long deckel) {
        return Marktwerte.VORGABE.mitDeckel(deckel)
                .mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                        new Ankaufliste.Ankauf("stone", 20L),
                        new Ankaufliste.Ankauf("diamond", 1_100L))));
    }

    private Befehlsprobe probe(long deckel) {
        return probe(kasse, werte(deckel));
    }

    private Befehlsprobe probe(Kasse welche, Marktwerte werte) {
        Wirtschaftsstand stand = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitMarkt(werte));
        var befehle = new MarktBefehle(stand, welche, UHR,
                Sprachprobe.spracheAusDemJar(), ALTFURT, protokoll);
        return new Befehlsprobe(List.of(befehle.markt()));
    }

    /** Was Anna gelesen hat, als Bild. */
    private List<String> gelesen() {
        return anna.chatBild();
    }

    /** Was aus Verkaeufen auf ihrem Konto steht. */
    private long verkauft() {
        long summe = 0L;
        for (Buchung zeile : kasse.verlauf(anna.kennung(), 100)) {
            if (zeile.grund() == Buchungsgrund.BUNDESMARKT_VERKAUF) {
                summe += zeile.betrag();
            }
        }
        return summe;
    }

    @Test
    @DisplayName("/markt nennt das Sortiment und was heute noch frei ist")
    void marktNenntDasSortiment() throws CommandSyntaxException {
        probe(5_000L).tippe("markt", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Der Bundesmarkt kauft"), gelesen().get(0));
        // Die Reihenfolge der Datei, nicht alphabetisch - der Betreiber ordnet.
        assertTrue(gelesen().get(1).contains("stone")
                && gelesen().get(1).contains("diamond"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("5.000 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Die LISTE darf man ueberall ansehen - nur verkaufen ist ein Ort")
    void listeUeberall() throws CommandSyntaxException {
        // Wer wissen will, ob sich der Weg nach Altfurt lohnt, soll nicht erst hingehen
        // muessen. Das ist der Unterschied zwischen einer Auskunft und einer Tat.
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe(5_000L).tippe("markt liste", anna);

        assertTrue(gelesen().get(0).contains("Der Bundesmarkt kauft"), gelesen().get(0));
    }

    @Test
    @DisplayName("Fuenf Stein bringen eine Mark - und sie sind aus der Hand")
    void fuenfSteinEineMark() throws CommandSyntaxException {
        anna.mitInDerHand(Material.STONE, 5);

        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(1L, verkauft());
        assertEquals(0, anna.mengeInDerHand(), "alle fuenf sind weg");
        assertTrue(gelesen().get(0).contains("5 stone verkauft"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("1 Mark"), gelesen().get(0));
    }

    @Test
    @DisplayName("Nur die STUECKE, die bezahlt werden - der Rest bleibt in der Hand")
    void nurDieStueckeDieBezahltWerden() throws CommandSyntaxException {
        // Sieben Stein sind 1,40 Mark. Der Bund nimmt fuenf und zahlt eine Mark, zwei
        // bleiben. Haette er alle sieben genommen, waeren 40 Pfennige verschwunden.
        anna.mitInDerHand(Material.STONE, 7);

        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(1L, verkauft());
        assertEquals(2, anna.mengeInDerHand(), "zwei bleiben liegen");
        assertTrue(gelesen().get(0).contains("5 stone verkauft"), gelesen().get(0));
    }

    @Test
    @DisplayName("In der falschen Welt wird nichts gekauft")
    void falscheWelt() throws CommandSyntaxException {
        anna.inWelt(Serverprobe.welt(HOCHWALD)).mitInDerHand(Material.STONE, 64);

        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(0L, verkauft());
        assertEquals(64, anna.mengeInDerHand(), "die Ware bleibt");
        assertTrue(gelesen().get(0).contains("Altfurt"), gelesen().get(0));
    }

    @Test
    @DisplayName("Mit leerer Hand gibt es nichts zu verkaufen")
    void leereHand() throws CommandSyntaxException {
        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(0L, verkauft());
        assertTrue(gelesen().get(0).contains("nichts in der Hand"), gelesen().get(0));
    }

    @Test
    @DisplayName("Was der Bund nicht kauft, sagt er - mit dem Namen und dem Hinweis")
    void kauftNicht() throws CommandSyntaxException {
        anna.mitInDerHand(Material.DIRT, 64);

        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(0L, verkauft());
        assertEquals(64, anna.mengeInDerHand());
        // ZWEI Zeilen: was nicht geht, und daneben, wo das Sortiment steht. Der
        // Hinweis gehoert dazu - ohne ihn weiss der Spieler nur, dass es nicht geht.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("dirt"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("/markt"), gelesen().get(1));
    }

    @Test
    @DisplayName("Zu wenig fuer eine Mark hat seinen EIGENEN Satz")
    void zuWenigFuerEineMark() throws CommandSyntaxException {
        // Diese Zeile MUSS es geben: Sonst tippt ein Spieler mit vier Stein in der Hand
        // /markt verkaufen, es passiert nichts, und er haelt es fuer einen Fehler.
        anna.mitInDerHand(Material.STONE, 4);

        probe(5_000L).tippe("markt verkaufen", anna);

        assertEquals(0L, verkauft());
        assertEquals(4, anna.mengeInDerHand());
        assertTrue(gelesen().get(0).contains("keine ganze Mark"), gelesen().get(0));
        assertFalse(gelesen().get(0).contains("genug verkauft"),
                "das ist der ANDERE Grund - er verlangt eine andere Tat");
    }

    @Test
    @DisplayName("Am Tagesdeckel hat es seinen EIGENEN Satz")
    void amDeckel() throws CommandSyntaxException {
        // Zwei Gruende fuer dasselbe Nichts, und sie verlangen Verschiedenes: Am Deckel
        // hilft Warten, bei zu wenig Stueck hilft Sammeln.
        kasse.gutschreiben(anna.kennung(), 100L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        anna.mitInDerHand(Material.STONE, 64);

        probe(100L).tippe("markt verkaufen", anna);

        assertEquals(100L, verkauft(), "nichts dazu");
        assertEquals(64, anna.mengeInDerHand());
        assertTrue(gelesen().get(0).contains("genug verkauft"), gelesen().get(0));
    }

    @Test
    @DisplayName("Kurz vor dem Deckel wird nur das Freie gekauft")
    void kurzVorDemDeckel() throws CommandSyntaxException {
        kasse.gutschreiben(anna.kennung(), 98L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        anna.mitInDerHand(Material.STONE, 64);

        probe(100L).tippe("markt verkaufen", anna);

        assertEquals(100L, verkauft(), "genau bis an den Deckel");
        assertEquals(54, anna.mengeInDerHand(), "zehn Stein sind zwei Mark");
    }

    @Test
    @DisplayName("Der Deckel zaehlt NUR Verkaeufe - Arbeit sperrt den Markt nicht")
    void nurVerkaeufeZaehlen() throws CommandSyntaxException {
        // Mit einem gemeinsamen Grund waere jede der zwei Quellen durch die andere zu
        // verschliessen: Wer vormittags arbeitet, koennte nachmittags nicht verkaufen.
        kasse.gutschreiben(anna.kennung(), 10_000L, Buchungsgrund.BERUF_VERDIENST);
        anna.mitInDerHand(Material.STONE, 5);

        probe(100L).tippe("markt verkaufen", anna);

        assertEquals(1L, verkauft());
        assertEquals(0, anna.mengeInDerHand());
    }

    @Test
    @DisplayName("Eine kaputte Kasse laesst die Ware in der Hand - und meldet es laut")
    void kaputteKasse() throws CommandSyntaxException {
        // Erst buchen, dann aus der Hand nehmen: So bleibt im schlimmsten Fall eine
        // Gutschrift ohne Ware, und die ist lesbar. Andersherum waere der Spieler ohne
        // Ware UND ohne Geld.
        anna.mitInDerHand(Material.STONE, 5);

        probe(new KaputteKasse(), werte(5_000L)).tippe("markt verkaufen", anna);

        assertEquals(5, anna.mengeInDerHand(), "die Ware bleibt");
        assertTrue(gelesen().get(0).contains("nicht buchbar"), gelesen().get(0));
        assertTrue(protokollzeilen.stream().anyMatch(satz ->
                        satz.getMessage() != null
                                && satz.getMessage().contains("bleiben in der Hand")),
                protokollzeilen.stream().map(LogRecord::getMessage).toList().toString());
    }

    @Test
    @DisplayName("Auf der Konsole gibt es keinen Markt")
    void konsoleHatKeineHand() throws CommandSyntaxException {
        // Die Konsole hat keine Hand und steht in keiner Welt.
        probe(5_000L).tippeAufDerKonsole("markt verkaufen");

        assertEquals(0L, verkauft());
    }

    @Test
    @DisplayName("Wirtschaftsstand, Kasse, Uhr, Sprache, Welt und Protokoll sind Pflicht")
    void pflichtenGelten() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uhr = java.time.Clock.systemUTC();
        var sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(null, kasse, uhr, sprache, ALTFURT, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, null, uhr, sprache, ALTFURT, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, null, sprache, ALTFURT, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, uhr, null, ALTFURT, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, uhr, sprache, ALTFURT, null));
        // Und ohne Weltschluessel waere der Markt kein Ort, sondern ein Menue - von
        // jeder Welt aus offen.
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, uhr, sprache, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, uhr, sprache, "  ", protokoll));
    }

    /** Eine Kasse, die beim Gutschreiben wirft. */
    private static final class KaputteKasse implements Kasse {

        @Override
        public long guthaben(UUID spieler) {
            return 0L;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return false;
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            return false;
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Datenbank ist weg");
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            return List.of();
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            return 0L;
        }
    }
}
