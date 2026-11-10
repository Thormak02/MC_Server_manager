package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Leeren der Freien Truhe - die Hand des naechtlichen Laufes im Spiel.
 *
 * <p><b>Warum es diese Datei gibt.</b> {@code TruhenlaufTest} tauscht die Naht
 * {@code Truhenlauf.Leerung} gegen eine Attrappe und prueft damit den TAKT: Merker,
 * Reihenfolge, SPAETER. Die Klasse, die das Wort "geleert" wirklich einloest, wurde dabei
 * von keinem Lauf betreten - und am 10.11.2026 blieben darum zwei Einspritzungen
 * ungefangen: {@code inventar.clear(0)} leerte nur den ersten Platz und MELDETE die ganze
 * Stueckzahl, und die Zuschauerfrage liess sich zu "mehr als einer" verdrehen, womit der
 * Lauf jedem unter den Haenden geleert haette.
 *
 * <p><b>Alle Stellvertreter stehen hier und nicht in {@link Serverprobe}</b>: Diese
 * Klasse bekommt ihren {@code Server} im Konstruktor und fragt keinen Bukkit-Singleton -
 * also ist der ganze Weg vom Server bis zum Platz in der Kiste ohne fremde Hilfe zu
 * bauen. Was {@code Serverprobe} nicht kann, ist gerade das Entscheidende: ein Block mit
 * einem {@code Container}-Zustand.
 */
@DisplayName("Das Leeren der Freien Truhe")
class TruhenleerungTest {

    private static final String WELT = "freimarken:altfurt";
    private static final Blockort HALLE = new Blockort(WELT, 72, -60, 20);

    private final List<String> protokollzeilen = new ArrayList<>();

    @BeforeAll
    static void serverSetzen() {
        // Fuer Wesenprobe.Probestapel: Ein ItemStack ohne jeden Server ist nicht zu bauen.
        Serverprobe.einmalSetzen();
    }

    @Test
    @DisplayName("Geleert wird JEDER Platz - und gezaehlt wird, was weg ist")
    void jederPlatzWirdGeleert() {
        // DIE PROBE, DIE AM 10.11.2026 GEFEHLT HAT. Die Stueckzahl allein genuegt nicht:
        // Ein Lauf, der nur den ersten Platz leert, zaehlt trotzdem alles - er meldet
        // "412 Stueck weg", setzt den Tagesmerker und loescht die Zeilen von gestern,
        // waehrend der Inhalt von gestern noch daliegt. Dann steht die Truhe voll und der
        // Deckel ist offen, und das ist genau der Zustand, gegen den der ganze Lauf
        // gebaut ist.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        plaetze[5] = new Wesenprobe.Probestapel(Material.DIRT, 5);
        Truhenleerung leerung = leerungMit(kiste(plaetze, zuschauer()));

        int weg = leerung.leere(HALLE);

        assertEquals(25, weg, "zwanzig Stein und fuenf Erde");
        assertNull(plaetze[0], "der erste Platz ist leer");
        assertNull(plaetze[5], "und der sechste auch - geleert wird die ganze Kiste");
        assertEquals(List.of(), protokollzeilen, "eine Truhe, die da ist, wird nicht beklagt");
    }

    @Test
    @DisplayName("Wer hineinsieht, haelt den Lauf auf - SPAETER und kein Stueck weg")
    void zuschauerHaeltDenLaufAuf() {
        // Die tragende Zusage dieser Klasse: Eine Truhe mit Zuschauer wird NICHT geleert.
        // Es ist EIN Zuschauer und nicht zwei, denn mehr laesst die Truhenwache gar nicht
        // zu - eine Frage nach "mehr als einem" waere ein Zweig, den nichts je betritt,
        // und der Lauf raeumte dem Spieler die Kiste unter den Haenden weg.
        ItemStack[] plaetze = new ItemStack[27];
        plaetze[0] = new Wesenprobe.Probestapel(Material.STONE, 20);
        Spielerprobe anna = new Spielerprobe("Anna", UUID.randomUUID());
        Truhenleerung leerung = leerungMit(kiste(plaetze, zuschauer(anna)));

        int weg = leerung.leere(HALLE);

        assertEquals(Truhenlauf.SPAETER, weg, "SPAETER und nicht 0 - der naechste Takt"
                + " versucht es wieder, und das ist in einer Minute und nicht morgen");
        assertEquals(20, plaetze[0].getAmount(), "und nichts ist weg");
    }

    @Test
    @DisplayName("Keine Kiste an der Stelle: 0, und beklagt wird EINMAL")
    void keineKisteAnDerStelle() {
        // 0 und nicht SPAETER: Eine Stelle, an der kein Behaelter steht, wird nicht von
        // selbst richtig - sie ist ein Fehler in der config.yml, und der Lauf soll
        // trotzdem seinen Tag abschliessen. Beklagt wird einmal und nicht jede Nacht:
        // Eine Zeile am Tag ueber dieselbe fehlende Kiste ist ein Protokoll, das niemand
        // mehr liest.
        Truhenleerung leerung = leerungMit(null);

        assertEquals(0, leerung.leere(HALLE));
        assertEquals(0, leerung.leere(HALLE));

        assertEquals(1, protokollzeilen.size(), protokollzeilen.toString());
        assertTrue(protokollzeilen.getFirst().contains("STONE"),
                "die Klage sagt, was dort steht: " + protokollzeilen);
        assertTrue(protokollzeilen.getFirst().contains("freie-truhe.truhen"),
                "und wo die Stelle eingetragen ist: " + protokollzeilen);
    }

    @Test
    @DisplayName("Die Welt ist nicht geladen: SPAETER, nicht 0")
    void weltNichtGeladen() {
        // Eine Welt kann im Betrieb nachgeladen werden - dann soll der naechste Takt die
        // Truhe finden. Mit einer 0 waere der Tag abgeschlossen und die Truhe voll.
        Truhenleerung leerung = new Truhenleerung(server(), protokoll());

        assertEquals(Truhenlauf.SPAETER, leerung.leere(HALLE));
        assertTrue(protokollzeilen.getFirst().contains("nicht geladen"),
                protokollzeilen.toString());
    }

    @Test
    @DisplayName("Ohne Server oder Protokoll gibt es keine Leerung")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenleerung(null, Logger.getAnonymousLogger()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenleerung(server(), null));
    }

    // --- Werkzeug -------------------------------------------------------------

    /** Eine Leerung, deren Welt genau an der Stelle der Halle diese Kiste hat. */
    private Truhenleerung leerungMit(Inventory kiste) {
        return new Truhenleerung(server(welt(kiste)), protokoll());
    }

    private static List<HumanEntity> zuschauer(Spielerprobe... wer) {
        List<HumanEntity> liste = new ArrayList<>();
        for (Spielerprobe einer : wer) {
            liste.add(einer.spieler());
        }
        return liste;
    }

    private static Server server(World... welten) {
        return (Server) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getWorlds" -> List.of(welten);
                    case "toString" -> "Serverprobe der Leerung";
                    case "hashCode" -> 41;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieser Server beantwortet " + methode.getName()
                                    + " nicht - gefragt werden duerfen nur die Welten");
                });
    }

    /**
     * Die Welt der Halle. {@code null} als Kiste heisst: dort steht Stein.
     *
     * <p>{@code getBlockAt} antwortet fuer JEDE Stelle - was dort steht, haengt allein
     * daran, ob eine Kiste mitgegeben wurde. Mehr braucht der gepruefte Zweig nicht, und
     * eine Stellenverwaltung hier waere ein zweites Modell der Welt.
     */
    private static World welt(Inventory kiste) {
        NamespacedKey name = NamespacedKey.fromString(WELT);
        return (World) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {World.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "key", "getKey" -> name;
                    case "getBlockAt" -> block(kiste);
                    case "toString" -> "Weltprobe " + WELT;
                    case "hashCode" -> WELT.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Diese Welt beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf ihr Schluessel und"
                                    + " ein Block");
                });
    }

    /** Ein Block mit oder ohne Behaelter - {@code getState(false)} ist die Frage. */
    private static Block block(Inventory kiste) {
        return (Block) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {Block.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getState" -> kiste == null ? stein() : behaelter(kiste);
                    case "getType" -> Material.STONE;
                    case "toString" -> "Blockprobe der Leerung";
                    case "hashCode" -> 43;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieser Block beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf sein Zustand und"
                                    + " seine Art");
                });
    }

    /** Ein Zustand, der KEIN Behaelter ist - die Stelle ohne Kiste. */
    private static BlockState stein() {
        return (BlockState) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {BlockState.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "toString" -> "Zustand ohne Behaelter";
                    case "hashCode" -> 47;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieser Zustand ist absichtlich KEIN Behaelter. Gefragt"
                                    + " wurde " + methode.getName());
                });
    }

    /** Ein Behaelterzustand - BlockState UND Container, wie eine echte Kiste. */
    private static BlockState behaelter(Inventory kiste) {
        return (BlockState) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {BlockState.class, Container.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getInventory" -> kiste;
                    case "toString" -> "Behaelterprobe";
                    case "hashCode" -> 53;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieser Behaelter beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf sein Inventar");
                });
    }

    /**
     * Das Inventar der Kiste.
     *
     * <p><b>{@code clear} achtet auf sein Argument</b>, und das ist der Punkt:
     * {@code clear()} leert die ganze Kiste, {@code clear(0)} genau einen Platz. Ein
     * Stellvertreter, der beides gleich behandelt, machte den Unterschied unsichtbar -
     * und damit genau die Einspritzung, gegen die diese Datei steht.
     */
    private static Inventory kiste(ItemStack[] plaetze, List<HumanEntity> zuschauer) {
        return (Inventory) Proxy.newProxyInstance(
                TruhenleerungTest.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getContents" -> plaetze;
                    case "getViewers" -> zuschauer;
                    case "getSize" -> plaetze.length;
                    case "clear" -> {
                        if (werte == null || werte.length == 0) {
                            for (int platz = 0; platz < plaetze.length; platz++) {
                                plaetze[platz] = null;
                            }
                        } else {
                            plaetze[(Integer) werte[0]] = null;
                        }
                        yield null;
                    }
                    case "toString" -> "Kistenprobe der Leerung";
                    case "hashCode" -> 59;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Kistenprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf nach Plaetzen,"
                                    + " Zuschauern und dem Leeren");
                });
    }

    /** Ein Protokoll, das jede Zeile mitschreibt - die Klage ist hier die halbe Zusage. */
    private Logger protokoll() {
        Logger eigener = Logger.getLogger("Truhenleerungsprobe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz.getLevel() + ": " + satz.getMessage());
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
}
