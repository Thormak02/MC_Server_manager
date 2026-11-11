package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherTruhe;
import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Truhenwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Freie Truhe am ECHTEN Ereignis - die Bukkit-Haelfte der Wache.
 *
 * <p><b>Warum es diese Datei neben {@code TruhenwacheTest} gibt.</b> Jene ersetzt die
 * Naht {@code Truhenhand} durch eine eigene Attrappe aus zwei Karten und prueft damit die
 * RECHNUNG: Differenz, Deckel, Rueckgabe, Reihenfolge. Was sie nicht anfasst, ist der Weg
 * vom Ereignis bis zu dieser Rechnung - und genau dort lagen am 10.11.2026 drei
 * ungefangene Faelle: Die Zaehlung in STAPELN statt Stueck, der Dreher in
 * {@code fremderSiehtHinein} und die zweite Richtung des Trichterschutzes blieben alle
 * gruen, weil kein Lauf {@code BukkitHand} oder {@code beimOeffnen} je betrat.
 *
 * <p><b>Dieselbe Bauart wie {@link ShopwacheTest}</b>, und aus demselben Grund: Ein
 * Inventar, ein Blockzustand und eine Sicht sind Schnittstellen, also baut ein
 * {@link Proxy} sie. Ein Gegenstandsstapel ist eine Klasse - den baut
 * {@code Wesenprobe.Probestapel}, der seine Menge selbst haelt, weil
 * {@code ItemStack.setAmount} ohne laufenden Server wirft.
 *
 * <p><b>Die Kiste haelt ihre Plaetze als Feld</b>, das die Probe zwischen Oeffnen und
 * Schliessen aendert. Das ist der Vorgang, den es nachzubilden gilt: Der Spieler nimmt
 * etwas heraus, und niemand sagt es dem Programm - es sieht nur, was nachher fehlt.
 */
@DisplayName("Die Freie Truhe am echten Ereignis")
class TruhenwacheEreignisTest {

    /**
     * Die Werte, gegen die diese Probe rechnet - 16 Stueck am Tag, zwei Verbote.
     *
     * <p>Eigene Zahlen und NICHT {@link Truhenwerte#VORGABE}: Ab Werk gilt seit dem
     * 10.11.2026 "kein Deckel, nichts verboten", und eine Probe, die am Deckel oder am
     * Verbot rechnet, braucht beides. Haengt sie an der Vorgabe, prueft sie die Vorgabe
     * statt der Regel - und die naechste Entscheidung des Betreibers bricht ein halbes
     * Dutzend Proben, die mit ihr nichts zu tun haben.
     */
    private static final Truhenwerte WERTE =
            new Truhenwerte(16, Set.of("written_book", "shulker_box"));

    private static final String WELT = "freimarken:altfurt";

    /** Die Stelle, die in {@code freie-truhe.truhen} steht - die Markthalle. */
    private static final int X = 72;
    private static final int Y = -60;
    private static final int Z = 20;

    /** Eine gewoehnliche Kiste daneben, die KEINE Freie Truhe ist. */
    private static final int FREMD_X = 75;

    private static final UUID ANNA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BERT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 10);
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private World welt;
    private Truhenablage ablage;
    private Truhenwache wache;
    private Spielerprobe anna;
    private Spielerprobe bert;

    @BeforeAll
    static void serverSetzen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void bauen() {
        Serverprobe.keineWeltenMehr();
        Serverprobe.niemandMehrDa();
        welt = Serverprobe.welt(WELT);
        ablage = new SpeicherTruhe();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
        wache = new Truhenwache(buch(), Sprachprobe.spracheAusDemJar());
    }

    @Test
    @DisplayName("Gezaehlt werden STUECK und nicht Stapel - ein Platz, zwanzig Stein")
    void stueckUndNichtStapel() {
        // DIE PROBE, DIE AM 10.11.2026 GEFEHLT HAT. Alles liegt auf EINEM Platz, und die
        // Zahl der Plaetze aendert sich beim Nehmen nicht: Ein Programm, das Stapel
        // zaehlt, sieht vorher einen und nachher einen - also nichts. Der Tagesdeckel
        // waere damit ein Deckel auf STAPEL, und das ist je nach Ware ein anderer Deckel
        // (Enderperlen zu 16, Werkzeug zu 1). Sechzehn Stapel Stein sind 1024 Stueck.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Inventory truhe = kiste(X, plaetze, zuschauer(anna));

        wache.beimOeffnen(new InventoryOpenEvent(sichtAuf(anna.spieler(), truhe)));
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 13);
        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(anna.spieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertEquals(7, ablage.behalten(ANNA, HEUTE),
                "sieben Stueck fehlen - die Zahl der Plaetze ist dieselbe geblieben");
        assertTrue(anna.chatBild().toString().contains("7 Gegenstaende behalten"),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("Der OEFFNENDE selbst ist kein Fremder - sonst ginge sie nie auf")
    void derOeffnendeIstKeinFremder() {
        // Beim Oeffnen steht der Spieler SCHON in der Zuschauerliste - Bukkit setzt ihn
        // hinein, bevor das Ereignis laeuft. Gefragt wird darum nach einem ZWEITEN. Wer
        // nach einem ERSTEN fragt, findet immer sich selbst: Die Freie Truhe ginge fuer
        // niemanden mehr auf, und jeder laese nur "Jemand sieht gerade hinein".
        Inventory truhe = kiste(X, new ItemStack[27], zuschauer(anna));

        InventoryOpenEvent ereignis =
                new InventoryOpenEvent(sichtAuf(anna.spieler(), truhe));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled(), "er ist der einzige Zuschauer");
        // Satz und Zahl getrennt gefragt: Im Bild steht die Farbmarke MITTEN in der Zahl
        // ("noch &616&7 Gegenstaende"), und das Bild als Ganzes haelt BildGleichTest.
        // Hier geht es um etwas anderes - dass dieser Satz mit DIESER Zahl ankommt.
        assertTrue(anna.chatBild().toString().contains("Freie Truhe - heute kannst du noch"),
                anna.chatBild().toString());
        assertTrue(anna.chatBild().toString().contains("16"),
                "mit der Zahl des Deckels: " + anna.chatBild());
    }

    @Test
    @DisplayName("Ein ZWEITER Zuschauer haelt sie zu - und es wird nichts gemerkt")
    void zweiterZuschauerHaeltSieZu() {
        // Die andere Richtung desselben Drehers, und die stille: Sehen zwei gleichzeitig
        // hinein, haben beide denselben Schnappschuss - und beim Schliessen bekommt JEDER
        // die Entnahme des anderen angerechnet.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Inventory truhe = kiste(X, plaetze, zuschauer(bert, anna));

        InventoryOpenEvent ereignis =
                new InventoryOpenEvent(sichtAuf(anna.spieler(), truhe));
        wache.beimOeffnen(ereignis);
        // Und weil nichts gemerkt wurde, rechnet ein Schliessen danach auch nichts.
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 5);
        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(anna.spieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertTrue(ereignis.isCancelled(), "Bert sieht schon hinein");
        assertTrue(anna.chatBild().toString().contains("Jemand sieht gerade"),
                anna.chatBild().toString());
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "ohne Schnappschuss wird nicht gerechnet - sonst truege sie Berts Griff");
    }

    @Test
    @DisplayName("Kein Trichter zieht heraus - und keiner schiebt hinein")
    void trichterInBeidenRichtungen() {
        // BEIDE RICHTUNGEN, und die zweite ist nicht Vorsicht: Ein Trichter, der
        // hineinlegt, fuellte die Gemeinschaftstruhe mit dem, was eine Maschine auswirft,
        // und der taegliche Lauf loeschte es jede Nacht - ein Muellschlucker mit
        // Zwischenlager. Die Richtungen sind hier WIRKLICH zu unterscheiden, weil auf der
        // anderen Seite eine gewoehnliche Kiste steht und keine zweite Truhe.
        InventoryMoveItemEvent heraus = new InventoryMoveItemEvent(
                kiste(X, new ItemStack[27], zuschauer()),
                new Wesenprobe.Probestapel(Material.STONE),
                kiste(FREMD_X, new ItemStack[27], zuschauer()), true);
        InventoryMoveItemEvent hinein = new InventoryMoveItemEvent(
                kiste(FREMD_X, new ItemStack[27], zuschauer()),
                new Wesenprobe.Probestapel(Material.DIRT),
                kiste(X, new ItemStack[27], zuschauer()), true);

        wache.beimTrichter(heraus);
        wache.beimTrichter(hinein);

        assertTrue(heraus.isCancelled(), "aus der Freien Truhe zieht kein Trichter");
        assertTrue(hinein.isCancelled(), "und in sie schiebt auch keiner");
    }

    @Test
    @DisplayName("Zwischen zwei gewoehnlichen Kisten laeuft der Trichter weiter")
    void gewoehnlicheKistenBleibenFrei() {
        // Ohne diese Probe waere nicht gehalten, dass der Trichterschutz NUR die Freie
        // Truhe betrifft - und ein Schutz fuer jede Kiste der Welt waere das Ende jeder
        // Sortieranlage in Altfurt.
        InventoryMoveItemEvent ereignis = new InventoryMoveItemEvent(
                kiste(FREMD_X, new ItemStack[27], zuschauer()),
                new Wesenprobe.Probestapel(Material.DIRT),
                kiste(FREMD_X + 1, new ItemStack[27], zuschauer()), true);

        wache.beimTrichter(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    @Test
    @DisplayName("Eine gewoehnliche Kiste wird gar nicht angefasst")
    void gewoehnlicheKisteWirdNichtGezaehlt() {
        // Die Stelle entscheidet, nicht die Art des Blocks: Jede andere Kiste in der
        // Halle bleibt eine Kiste, und wer aus ihr etwas nimmt, bekommt keinen
        // Tagesstand.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Inventory fremde = kiste(FREMD_X, plaetze, zuschauer(anna));

        wache.beimOeffnen(new InventoryOpenEvent(sichtAuf(anna.spieler(), fremde)));
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 5);
        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(anna.spieler(), fremde),
                InventoryCloseEvent.Reason.PLAYER));

        assertEquals(0, ablage.behalten(ANNA, HEUTE));
        assertEquals(List.of(), anna.chatBild(), "und gesagt wird dazu nichts");
    }

    @Test
    @DisplayName("OHNE Zaehlung geht sie auf, obwohl schon jemand hineinsieht")
    void ohneZaehlungGemeinsamAmEreignis() {
        // Am ECHTEN Ereignis, weil genau hier die Zuschauerliste gelesen wird: Bukkit
        // setzt den Oeffnenden selbst schon hinein, Bert ist der zweite. Die Probe
        // daneben zeigt denselben Griff mit Deckel - dort bleibt sie zu.
        Truhenwache offen = new Truhenwache(
                buch(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of())),
                Sprachprobe.spracheAusDemJar());
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Inventory truhe = kiste(X, plaetze, zuschauer(anna, bert));

        InventoryOpenEvent ereignis =
                new InventoryOpenEvent(sichtAuf(bert.spieler(), truhe));
        offen.beimOeffnen(ereignis);
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 13);
        offen.beimSchliessen(new InventoryCloseEvent(sichtAuf(bert.spieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertFalse(ereignis.isCancelled(),
                "zu zweit hineinsehen muss gehen: " + bert.chatBild());
        assertEquals(0, ablage.behalten(BERT, HEUTE),
                "und die sieben fehlenden Stuecke rechnet niemand jemandem an");
        assertTrue(bert.chatBild().toString().contains("nimm, was du brauchst"),
                bert.chatBild().toString());
    }

    @Test
    @DisplayName("Ohne Spieler wird nichts gerechnet - ein Wesen hat keinen Tagesstand")
    void ohneSpielerKeineRechnung() {
        // getPlayer gibt eine HumanEntity: Ein Inventar kann auch fuer ein anderes Wesen
        // offen sein. Ohne Spieler gibt es keine UUID, also keinen Tagesstand - dieselbe
        // Pruefung und derselbe Grund wie in Messwache und Shopwache.
        Inventory truhe = kiste(X, new ItemStack[27], zuschauer());

        InventoryOpenEvent ereignis =
                new InventoryOpenEvent(sichtAuf(keinSpieler(), truhe));
        wache.beimOeffnen(ereignis);
        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(keinSpieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertFalse(ereignis.isCancelled(), "eine Absage an niemanden waere keine");
    }

    @Test
    @DisplayName("Ein verbotener Gegenstand kommt in den RUCKSACK - und nicht ins Nichts")
    void verbotenesKommtZurueck() {
        // DER VERLUST AUS DEM SPIELTEST vom 10.11.2026: Der Rueckweg leitet seinen Stapel
        // aus einem VORHANDENEN ab und suchte in Truhe und Rucksack. Beim verbotenen
        // Gegenstand ist der vorhandene aber genau der, der eben herausgenommen wurde -
        // das einzige geschriebene Buch lag in der Truhe, der Spieler hatte keines. Dann
        // fand die Hand keine Vorlage, legte nichts und sagte trotzdem "du bekommst es
        // zurueck". Das Buch war geloescht, und ein geschriebenes Buch ist nicht
        // wiederherstellbar.
        ItemStack[] plaetze = new ItemStack[27];
        Inventory truhe = kiste(X, plaetze, zuschauer(anna));
        wache.beimOeffnen(new InventoryOpenEvent(sichtAuf(anna.spieler(), truhe)));
        // Er legt sein Buch hinein - sein einziges, und in der Truhe liegt sonst nichts.
        plaetze[0] = new Wesenprobe.Probestapel(Material.WRITTEN_BOOK, 1);

        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(anna.spieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertEquals(1, anna.imRucksack(Material.WRITTEN_BOOK),
                "das Buch MUSS bei ihm ankommen: " + anna.chatBild());
        assertEquals(0, inDerKiste(plaetze, Material.WRITTEN_BOOK),
                "und es liegt nicht mehr in der Truhe");
        assertTrue(anna.chatBild().toString().contains("WRITTEN_BOOK"),
                anna.chatBild().toString());
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "Hineinlegen ist keine Entnahme - auch verbotenes nicht");
    }

    @Test
    @DisplayName("Der Ueberschuss ueber dem Deckel kommt in die Truhe zurueck - auch wenn"
            + " sie restlos leer ist")
    void ueberschussKommtZurueck() {
        // DERSELBE FEHLER, DIE ANDERE SEITE, und hier traf er den gewoehnlichen Fall:
        // Wer die Truhe leerraeumt und vom Genommenen nur noch den Ueberschuss bei sich
        // hat, dem nahm die Hand diesen Ueberschuss aus dem Rucksack - und fand dann
        // weder in der Truhe noch bei ihm ein Exemplar, aus dem sie einen Stapel bilden
        // konnte. Die Ware war weg, angerechnet bekam er trotzdem alles.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Inventory truhe = kiste(X, plaetze, zuschauer(anna));
        wache.beimOeffnen(new InventoryOpenEvent(sichtAuf(anna.spieler(), truhe)));
        // Er nimmt alle zwanzig und verbaut sechzehn davon, bevor er schliesst: In der
        // Truhe liegt nichts mehr, bei ihm liegen genau die vier ueber dem Deckel.
        plaetze[0] = null;
        anna.mitImRucksack(new Wesenprobe.Probestapel(Material.STONE, 4));

        wache.beimSchliessen(new InventoryCloseEvent(sichtAuf(anna.spieler(), truhe),
                InventoryCloseEvent.Reason.PLAYER));

        assertEquals(4, inDerKiste(plaetze, Material.STONE),
                "die vier ueber dem Deckel MUESSEN in der Truhe liegen: "
                        + anna.chatBild());
        assertEquals(0, anna.imRucksack(Material.STONE),
                "und nicht mehr bei ihm - zweimal gaebe es sie nicht");
        assertEquals(16, ablage.behalten(ANNA, HEUTE),
                "angerechnet wird, was nach dem Zuruecklegen bei ihm bleibt");
    }

    // --- Werkzeug -------------------------------------------------------------

    private Truhenbuch buch() {
        return buch(WERTE);
    }

    private Truhenbuch buch(Truhenwerte werte) {
        return new Truhenbuch(
                Truhenverzeichnis.ausStellen(List.of(new Blockort(WELT, X, Y, Z))),
                ablage,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitTruhe(werte)),
                UHR, Logger.getAnonymousLogger());
    }

    /** Die Zuschauerliste - veraenderlich, denn Bukkit gibt die lebende Liste. */
    private static List<HumanEntity> zuschauer(Spielerprobe... wer) {
        List<HumanEntity> liste = new ArrayList<>();
        for (Spielerprobe einer : wer) {
            liste.add(einer.spieler());
        }
        return liste;
    }

    /** Die Kiste dieser Welt an dieser Stelle - die Attrappe steht in Kistenprobe. */
    private Inventory kiste(int x, ItemStack[] plaetze, List<HumanEntity> zuschauer) {
        return Kistenprobe.kiste(welt, x, Y, Z, plaetze, zuschauer);
    }

    /** Wie viele STUECK dieses Materials in der Kiste liegen. */
    private static int inDerKiste(ItemStack[] plaetze, Material art) {
        int stueck = 0;
        for (ItemStack stapel : plaetze) {
            if (stapel != null && stapel.getType() == art) {
                stueck += stapel.getAmount();
            }
        }
        return stueck;
    }

    /** Die Sicht auf ein Inventar - die Attrappe steht in Kistenprobe. */
    private static InventoryView sichtAuf(HumanEntity wer, Inventory oben) {
        return Kistenprobe.sichtAuf(wer, oben);
    }

    /** Ein Wesen, das kein Spieler ist - die Attrappe steht in Kistenprobe. */
    private static HumanEntity keinSpieler() {
        return Kistenprobe.keinSpieler();
    }
}
