package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Angelwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerFishEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Hand, die in Hafenwerder den Fang tauscht.
 *
 * <p>Geprueft wird am ECHTEN Ereignis, nicht an einer Naht: {@code PlayerFishEvent} laesst
 * sich ohne Server bauen (dieselbe Bauart wie {@code VerdienstwacheTest}), und nur so ist
 * zu sehen, ob die vier Tore in der richtigen Reihenfolge stehen und ob der Tausch
 * wirklich im Netz landet.
 *
 * <p><b>Der Zufall kommt als Wuerfel herein</b>, und diese Proben stellen ihn: Eine Probe
 * mit echtem Zufall ist eine, die manchmal gruen ist. Was die Regel aus zwei Wuerfen
 * macht, prueft {@code FangregelTest} im Kern; hier geht es um die Tore, die Welt und den
 * getauschten Stapel.
 */
@DisplayName("Die Angelwache")
class AngelwacheTest {

    private static final String HAFENWERDER = "freimarken:hafenwerder";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    /** Eine Tabelle mit EINER Zeile - dann ist der zweite Wurf gleichgueltig. */
    private static final Angelwerte EINE_ZEILE =
            new Angelwerte(true, 100, Map.of("heart_of_the_sea", 1));

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private Spielerprobe anna;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1))
                .inWelt(Serverprobe.welt(HAFENWERDER));
    }

    // ===================================================== der Tausch

    @Test
    @DisplayName("Im offenen Wasser von Hafenwerder wird der Fang getauscht")
    void derFangWirdGetauscht() {
        Item gefangen = Wesenprobe.gegenstand(Material.COD);

        wache(EINE_ZEILE).beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true,
                gefangen));

        assertEquals(Material.HEART_OF_THE_SEA, gefangen.getItemStack().getType());
        assertEquals(1, gefangen.getItemStack().getAmount(),
                "EIN Stueck, wie Vanilla es gibt: Ein ganzer Stapel waere eine"
                        + " Geldquelle, die niemand eingestellt hat - die Lohntafel"
                        + " zahlt je Fang");
    }

    @Test
    @DisplayName("Greift die Tabelle nicht, bleibt der Vanillafang liegen")
    void ohneTreffferBleibtVanilla() {
        // Der Wurf liegt UEBER der Haeufigkeit. Das ist der haeufigste Fall im Spiel -
        // vier von fuenf Faengen -, und er muss den Stapel unberuehrt lassen.
        Item gefangen = Wesenprobe.gegenstand(Material.COD);
        Angelwache wache = wache(EINE_ZEILE.mitHaeufigkeit(20), 20, 0L);

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, gefangen));

        assertEquals(Material.COD, gefangen.getItemStack().getType());
    }

    @Test
    @DisplayName("Eine ausgeschaltete Tabelle laesst alles, wie es ist")
    void ausgeschaltetTauschtNichts() {
        Item gefangen = Wesenprobe.gegenstand(Material.COD);

        wache(EINE_ZEILE.mitAn(false)).beimFischen(
                angeln(PlayerFishEvent.State.CAUGHT_FISH, true, gefangen));

        assertEquals(Material.COD, gefangen.getItemStack().getType());
    }

    // ===================================================== die vier Tore

    @Test
    @DisplayName("In einer anderen WELT tauscht sie nichts")
    void fremdeWeltBleibtUnberuehrt() {
        // Die billigste Frage und darum die erste: Diese Wache haengt am Server und
        // nicht an einer Welt. Ausserhalb von Hafenwerder soll sie nach einem
        // Zeichenkettenvergleich fertig sein - und sie darf dort NICHTS tun, sonst
        // angelte der ganze Server Hafenwerders Gut.
        anna.inWelt(Serverprobe.welt(MARKTSTADT));
        Item gefangen = Wesenprobe.gegenstand(Material.COD);

        wache(EINE_ZEILE).beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true,
                gefangen));

        assertEquals(Material.COD, gefangen.getItemStack().getType());
    }

    @Test
    @DisplayName("Ohne Inselwelt tut sie gar nichts")
    void ohneInselweltNichts() {
        // Dieselbe Form wie bei den Befehlen: Die Wache wird auch dann angemeldet, wenn
        // inseln.welt ins Leere zeigt - ein Zuhoerer, den es je nach Einstellungsdatei
        // gibt oder nicht, ist einer, den niemand findet.
        Item gefangen = Wesenprobe.gegenstand(Material.COD);
        Angelwache ohneWelt = new Angelwache("", stand(EINE_ZEILE),
                gestellterWuerfel(0, 0L), Wesenprobe.Probestapel::new, protokoll);

        ohneWelt.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, gefangen));

        assertEquals(Material.COD, gefangen.getItemStack().getType());
    }

    @Test
    @DisplayName("In einem Ein-Block-Loch wird nicht getauscht")
    void geschlossenesWasserBleibtUnberuehrt() {
        // Dasselbe Tor wie bei der Zahlung, und aus demselben Grund: Das offene Wasser
        // laesst das Loch heraus, an dem ein Klickautomat Tag und Nacht angelt. Ohne
        // dieses Tor liefe die Tabelle dort am schnellsten.
        Item gefangen = Wesenprobe.gegenstand(Material.COD);

        wache(EINE_ZEILE).beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, false,
                gefangen));

        assertEquals(Material.COD, gefangen.getItemStack().getType());
    }

    @Test
    @DisplayName("Nur der Zustand CAUGHT_FISH hat einen Fang")
    void nurGefangenerFischZaehlt() {
        for (PlayerFishEvent.State zustand : PlayerFishEvent.State.values()) {
            if (zustand == PlayerFishEvent.State.CAUGHT_FISH) {
                continue;
            }
            Item gefangen = Wesenprobe.gegenstand(Material.COD);

            wache(EINE_ZEILE).beimFischen(angeln(zustand, true, gefangen));

            assertEquals(Material.COD, gefangen.getItemStack().getType(),
                    "Zustand " + zustand + " hat keinen Fang, den man tauschen koennte");
        }
    }

    @Test
    @DisplayName("Ein gehakter Tintenfisch ist kein Fang - und wirft nicht")
    void ohneGegenstandKeinWurf() {
        // getCaught() ist bei den meisten Zustaenden null, und beim gehakten Wesen ist
        // es kein Item. Ein Cast darauf waere eine ClassCastException aus einem Zweig,
        // der bei jedem Angelwurf laeuft.
        Angelwache wache = wache(EINE_ZEILE);

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, null));

        assertEquals(List.of(), klagen(), "und zwar ohne eine Zeile im Protokoll");
    }

    // ===================================================== Neuladen und Klage

    @Test
    @DisplayName("Ein Neuladen erreicht die Wache - sie haelt den STAND, nicht die Werte")
    void neuladenErreichtDieWache() {
        // Der Grund, aus dem die Wache den Wirtschaftsstand bekommt und nicht die
        // Angelwerte: Eine kopierte Tabelle waere nach dem ersten Neuladen taub, und
        // niemand saehe es - der Betreiber liest "angeln.faenge: dazu: sponge" und
        // faengt weiter das Alte.
        Wirtschaftsstand stand = stand(EINE_ZEILE);
        Angelwache wache = new Angelwache(HAFENWERDER, stand,
                gestellterWuerfel(0, 0L), Wesenprobe.Probestapel::new, protokoll);
        Item ersterFang = Wesenprobe.gegenstand(Material.COD);
        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, ersterFang));
        assertEquals(Material.HEART_OF_THE_SEA, ersterFang.getItemStack().getType());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitAngeln(
                new Angelwerte(true, 100, Map.of("sponge", 1))));

        Item zweiterFang = Wesenprobe.gegenstand(Material.COD);
        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, zweiterFang));

        assertEquals(Material.SPONGE, zweiterFang.getItemStack().getType());
    }

    @Test
    @DisplayName("Ein Fang, den es nicht gibt, bleibt Vanilla - und klagt EINMAL")
    void unbekannterFangKlagtEinmal() {
        // Der Leser der Einstellungsdatei prueft jeden Namen, also kann das nur eine
        // Tabelle sein, die auf einem anderen Weg hereinkam. Dann bleibt es bei Vanilla,
        // und der Betreiber liest es - aber nicht bei jedem Fang, sonst verdeckt ein
        // Angelautomat in Sekunden jede andere Meldung.
        Angelwache wache = wache(new Angelwerte(true, 100, Map.of("gibtsnicht", 1)));
        Item erster = Wesenprobe.gegenstand(Material.COD);
        Item zweiter = Wesenprobe.gegenstand(Material.COD);

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, erster));
        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true, zweiter));

        assertEquals(Material.COD, erster.getItemStack().getType());
        assertEquals(1, klagen().size(), "gedrosselt: " + klagen());
        assertTrue(klagen().get(0).contains("gibtsnicht"), klagen().get(0));
    }

    @Test
    @DisplayName("Ohne Stand und ohne Wuerfel gibt es diese Wache nicht")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwache(HAFENWERDER, null, new Random(),
                        Wesenprobe.Probestapel::new, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwache(HAFENWERDER, stand(EINE_ZEILE), null,
                        Wesenprobe.Probestapel::new, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwache(HAFENWERDER, stand(EINE_ZEILE), new Random(), null,
                        protokoll),
                "ohne Stapelbau gibt es nichts zu tauschen");
    }

    // ===================================================== Werkzeug

    private Wirtschaftsstand stand(Angelwerte werte) {
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitAngeln(werte));
    }

    private Angelwache wache(Angelwerte werte) {
        return wache(werte, 0, 0L);
    }

    private Angelwache wache(Angelwerte werte, int prozentWurf, long gewichtsWurf) {
        return new Angelwache(HAFENWERDER, stand(werte),
                gestellterWuerfel(prozentWurf, gewichtsWurf),
                Wesenprobe.Probestapel::new, protokoll);
    }

    /**
     * Ein Wuerfel, der SAGT, was er wirft.
     *
     * <p>{@link Random} ist nicht endgueltig, und beide Methoden sind ueberschreibbar -
     * also braucht es keinen Stellvertreter. Was die Regel aus diesen zwei Zahlen macht,
     * prueft {@code FangregelTest}; hier sollen sie nur verlaesslich sein.
     */
    private static Random gestellterWuerfel(int prozentWurf, long gewichtsWurf) {
        return new Random() {
            @Override
            public int nextInt(int grenze) {
                return prozentWurf;
            }

            @Override
            public long nextLong(long grenze) {
                return gewichtsWurf;
            }
        };
    }

    private PlayerFishEvent angeln(PlayerFishEvent.State zustand, boolean offenesWasser,
                                   org.bukkit.entity.Entity gefangen) {
        return new PlayerFishEvent(anna.spieler(), gefangen,
                Wesenprobe.angel(offenesWasser), zustand);
    }

    private List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("AngelwacheProbe-" + UUID.randomUUID());
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
}
