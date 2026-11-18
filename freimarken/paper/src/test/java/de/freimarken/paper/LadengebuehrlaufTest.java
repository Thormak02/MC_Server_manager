package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Stadtkasse;
import de.freimarken.kern.Warenprobe;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die TAEGLICHE Ladengebuehr der Kistenshops - ohne Server [Teil E6].
 *
 * <p>Die Kernzusage ist eine unangenehme: Wer sie nicht zahlen kann, VERLIERT seinen
 * Shop - das Schild ist weg, die Ware bleibt in der Kiste. Darum liegt die Gebuehr ab
 * Werk bei 0, und jede Probe hier stellt sie eigens ein.
 */
@DisplayName("Die taegliche Ladengebuehr")
class LadengebuehrlaufTest {

    private static final String STADTWELT = "freimarken:freistaedte";
    private static final String ALTFURT = "freimarken:altfurt";
    private static final Instant JETZT = Instant.parse("2026-11-17T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);
    private static final Warenprobe STEIN = new Warenprobe("STONE", "ZWVpbi1zdGVpbg==");

    private StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private SpeicherLager lager;
    private SpeicherKasse geld;
    private Freistadtbuch buch;
    private Shopablage shops;
    private Shopbuch shopbuch;
    private Ladengebuehrlauf lauf;

    /**
     * Was der Lauf ins Protokoll geschrieben hat - MITGESCHRIEBEN, nicht gezaehlt.
     *
     * <p>Der Waechter dieser Datei, und er musste erst gefunden werden: Jeder Absturz
     * des Laufs hinterlaesst genau eine Klage. Eine Probe, die bloss sagt "nichts ist
     * passiert", ist blind dagegen - und ein zweiter, zahlender Shop beweist nur, dass
     * der Lauf BIS ZU IHM gekommen ist, nicht dass er durch ist.
     */
    private final java.util.List<java.util.logging.LogRecord> protokollzeilen =
            new java.util.ArrayList<>();

    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.niemandMehrDa();
        uhr = new StellUhr(JETZT);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitZeit(new de.freimarken.kern.Zeitwerte(ZONE,
                        de.freimarken.kern.Zeitwerte.VORGABE.takt(),
                        de.freimarken.kern.Zeitwerte.VORGABE.untaetigNach()))
                .mitHandel(Shopwerte.VORGABE));
        lager = new SpeicherLager();
        geld = new SpeicherKasse(1_000_000L);
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, geld,
                new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("altfurt", "freistaedte")),
                wirtschaft);
        buch.gibGrundstuecke(new SpeicherGrundstuecke());
        shops = new SpeicherShops();
        shopbuch = new Shopbuch(shops, null, null, wirtschaft, buch,
                Logger.getAnonymousLogger());
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        lauf = new Ladengebuehrlauf(shopbuch, shops, buch, STADTWELT, uhr, wirtschaft,
                org.bukkit.Bukkit.getServer(), Sprachprobe.spracheAusDemJar(),
                protokoll);
    }

    @Test
    @DisplayName("AB WERK faellt nichts - die Gebuehr ist 0")
    void abWerkFaelltNichts() {
        // Und das ist die Vorgabe mit Bedacht: Eine Tagesgebuehr nimmt Spielern Laeden,
        // und ob ein Server das will, entscheidet sein Betreiber.
        Parzelle stadt = gegruendet();
        shops.schreibe(shopIn(stadt, BERT));
        long vorher = geld.guthaben(BERT);

        assertEquals(0, lauf.imTakt());

        assertEquals(vorher, geld.guthaben(BERT));
        assertNotNull(shops.anDemSchild(STADTWELT, stelleX(stadt) - 1, 64,
                stelleZ(stadt)), "und der Shop steht");
    }

    @Test
    @DisplayName("Eingeschaltet zahlt jeder Laden an SEINE Stadt")
    void eingeschaltetZahltJeder() {
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(shopIn(stadt, BERT));
        long vorher = geld.guthaben(BERT);

        assertEquals(0, lauf.imTakt(), "abgebaut wird nichts - er kann zahlen");

        assertEquals(vorher - 10L, geld.guthaben(BERT));
        assertEquals(10L, buch.kassenstand(
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow()));
    }

    @Test
    @DisplayName("Zweimal am selben Tag zahlt NICHT zweimal")
    void nurEinmalJeTag() {
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(shopIn(stadt, BERT));
        lauf.imTakt();
        long nachEinmal = geld.guthaben(BERT);

        lauf.imTakt();
        lauf.imTakt();

        assertEquals(nachEinmal, geld.guthaben(BERT));
    }

    @Test
    @DisplayName("Morgen zahlt er wieder")
    void morgenWieder() {
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(shopIn(stadt, BERT));
        lauf.imTakt();
        long nachEinmal = geld.guthaben(BERT);

        uhr.weiter(java.time.Duration.ofDays(1));
        lauf.imTakt();

        assertEquals(nachEinmal - 10L, geld.guthaben(BERT));
    }

    @Test
    @DisplayName("Wer nicht zahlen kann, VERLIERT sein Schild - die Ware bleibt")
    void werNichtZahltVerliertDasSchild() {
        // DIE UNANGENEHME ZUSAGE, und genau sie ist der Grund fuer die Vorgabe 0. Der
        // Haendler verliert sein SCHILD und nicht seine Waren: Beim Abbau bleibt die
        // Ware immer in der Kiste, und er kann ein neues Schild setzen.
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(shopIn(stadt, BERT));
        geld.abbuchen(BERT, geld.guthaben(BERT), Buchungsgrund.FREISTADT_GEGRUENDET);

        assertEquals(1, lauf.imTakt());

        assertNull(shops.anDemSchild(STADTWELT, stelleX(stadt) - 1, 64,
                stelleZ(stadt)), "das Schild ist weg");
        assertEquals(0L, buch.kassenstand(
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow()),
                "und die Stadt hat nichts bekommen - abgebucht wurde nichts");
    }

    @Test
    @DisplayName("Ein BUNDSHOP zahlt nicht und wird nicht abgebaut")
    void bundshopZahltNicht() {
        // Er gehoert niemandem - es gibt niemanden, der zahlen koennte. Ihn abzubauen,
        // weil ein Nichtvorhandener nicht zahlt, waere der schlimmste Fall: Die
        // Ankaufstaende des Bundes verschwaenden ueber Nacht.
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(new Kistenshop(STADTWELT, stelleX(stadt) - 1, 64, stelleZ(stadt),
                stelleX(stadt), 64, stelleZ(stadt), null, 16, 100L, 0L, STEIN,
                Set.of()));
        // FUND DER EINSPRITZUNGSRUNDE (F37): EIN ZWEITER, GEWOEHNLICHER SHOP, und er
        // ist der eigentliche Waechter. Ohne ihn sagte diese Probe nur "der Bundshop
        // steht noch" - und das gilt auch, wenn der Lauf an ihm ABBRICHT: Ein
        // Bundshop hat keinen Besitzer, das Abbuchen wirft, und der Fang beendet den
        // ganzen Durchgang. Im Spiel hiesse das: EIN Bundshop in einer Stadt haelt die
        // Tagesgebuehr fuer ALLE Laeden auf, jeden Tag.
        Parzelle zweite = buch.gruende(STADTWELT, ANNA, "bertstadt", 99).stadt()
                .orElseThrow();
        shops.schreibe(shopIn(zweite, BERT));
        long bertVorher = geld.guthaben(BERT);

        assertEquals(0, lauf.imTakt());

        assertNotNull(shops.anDemSchild(STADTWELT, stelleX(stadt) - 1, 64,
                stelleZ(stadt)));
        assertEquals(bertVorher - 10L, geld.guthaben(BERT),
                "und der gewoehnliche Shop hat gezahlt - der Lauf ist bis zu ihm"
                        + " gekommen");
    }

    @Test
    @DisplayName("Ein Shop AUSSERHALB einer Stadt zahlt nichts")
    void ausserhalbZahltNichts() {
        // Die Gebuehr ist eine Gebuehr AN EINE STADT. Wo keine steht, gibt es keine
        // Kasse - und in Altfurt steht nie eine.
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        shops.schreibe(new Kistenshop(ALTFURT, 10, 64, 10, 11, 64, 10, BERT, 16, 100L,
                0L, STEIN, Set.of()));
        long vorher = geld.guthaben(BERT);
        // FUND DER EINSPRITZUNGSRUNDE (F40): EIN ZWEITER SHOP, DER ZAHLEN MUSS - der
        // eigentliche Waechter. Ohne ihn sagte diese Probe nur "nichts ist passiert",
        // und genau so sieht ein Lauf aus, der an dem Shop in Altfurt ABBRICHT. Im
        // Spiel hiesse das: Ein einziger Shop in der Wildnis haelt die Tagesgebuehr
        // fuer alle Staedte auf.
        shops.schreibe(shopIn(stadt, ANNA));
        long annaVorher = geld.guthaben(ANNA);

        assertEquals(0, lauf.imTakt());

        assertEquals(vorher, geld.guthaben(BERT));
        assertNotNull(shops.anDemSchild(ALTFURT, 10, 64, 10));
        assertEquals(annaVorher - 10L, geld.guthaben(ANNA),
                "und der Shop IN der Stadt hat gezahlt");
        // DER WAECHTER, der nicht an der Reihenfolge haengt [F40, zweiter Versuch]:
        // Jeder Absturz dieses Laufs hinterlaesst genau eine Klage. Die Zeile darueber
        // beweist nur, dass er BIS ZU diesem Shop gekommen ist - alle() ist nach der
        // Stelle sortiert, und welcher zuerst kommt, ist Zufall der Koordinaten.
        assertEquals(java.util.List.of(), klagen(),
                "und er ist OHNE Klage durchgelaufen");
    }

    @Test
    @DisplayName("Ein Shop auf einem FREIEN Feld der Stadtwelt zahlt auch nichts")
    void freiesFeldZahltNichts() {
        Parzelle stadt = gegruendet();
        schalteEin(10L);
        int frei = buch.plan().feldMitte(40);
        shops.schreibe(new Kistenshop(STADTWELT, frei - 1, 64, frei, frei, 64, frei,
                BERT, 16, 100L, 0L, STEIN, Set.of()));
        long vorher = geld.guthaben(BERT);
        // Und wieder der zweite Shop als Waechter - siehe F40 bei der Probe darueber.
        shops.schreibe(shopIn(stadt, ANNA));
        long annaVorher = geld.guthaben(ANNA);

        assertEquals(0, lauf.imTakt());

        assertEquals(vorher, geld.guthaben(BERT));
        assertEquals(annaVorher - 10L, geld.guthaben(ANNA),
                "der Shop IN der Stadt hat gezahlt");
        // Und wieder die Klage als Waechter - siehe die Probe darueber.
        assertEquals(java.util.List.of(), klagen(),
                "ohne Klage durchgelaufen: Ein Shop auf einem freien Feld ist kein"
                        + " Fehler, sondern ein Shop ohne Stadt");
    }

    @Test
    @DisplayName("Ohne Buch, Ablage oder Weltschluessel entsteht der Lauf nicht")
    void unbrauchbarerLaufWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Ladengebuehrlauf(null,
                shops, buch, STADTWELT, uhr, wirtschaft, org.bukkit.Bukkit.getServer(),
                Sprachprobe.spracheAusDemJar(), Logger.getAnonymousLogger()));
        assertThrows(IllegalArgumentException.class, () -> new Ladengebuehrlauf(shopbuch,
                shops, buch, " ", uhr, wirtschaft, org.bukkit.Bukkit.getServer(),
                Sprachprobe.spracheAusDemJar(), Logger.getAnonymousLogger()));
    }

    @Test
    @DisplayName("Einschalten MITTEN AM TAG wirkt sofort - nicht erst morgen")
    void einschaltenWirktSofort() {
        // Der Grund, warum die 0-Pruefung VOR dem Tagesmerker steht: Sonst haette der
        // Lauf den heutigen Tag als erledigt gemerkt, waehrend die Gebuehr noch 0 war -
        // und ein Betreiber, der sie einschaltet, saehe einen Tag lang nichts.
        Parzelle stadt = gegruendet();
        shops.schreibe(shopIn(stadt, BERT));
        assertEquals(0, lauf.imTakt(), "erst aus");

        schalteEin(10L);
        long vorher = geld.guthaben(BERT);
        lauf.imTakt();

        assertEquals(vorher - 10L, geld.guthaben(BERT));
    }

    // --- Werkzeug -------------------------------------------------------------

    private java.util.List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue()
                        >= java.util.logging.Level.WARNING.intValue())
                .map(java.util.logging.LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("LadengebuehrProbe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord satz) {
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

    private void schalteEin(long betrag) {
        wirtschaft.uebernimm(wirtschaft.werte().mitFreistaedten(
                wirtschaft.werte().freistaedte().mitLadengebuehren(
                        Freistadtwerte.VORGABE_LADENGEBUEHR_ANLAGE, betrag)));
    }

    private Parzelle gegruendet() {
        return buch.gruende(STADTWELT, ANNA, "annastadt", 99).stadt().orElseThrow();
    }

    private int stelleX(Parzelle stadt) {
        return buch.plan().feldMitte(stadt.x());
    }

    private int stelleZ(Parzelle stadt) {
        return buch.plan().feldMitte(stadt.z());
    }

    private Kistenshop shopIn(Parzelle stadt, UUID besitzer) {
        return new Kistenshop(STADTWELT, stelleX(stadt) - 1, 64, stelleZ(stadt),
                stelleX(stadt), 64, stelleZ(stadt), besitzer, 16, 100L, 0L, STEIN,
                Set.of());
    }

    /** Eine Uhr, die sich stellen laesst - dieselbe Bauart wie in {@code MietlaufTest}. */
    private static final class StellUhr extends Clock {

        private Instant jetzt;

        StellUhr(Instant anfang) {
            this.jetzt = anfang;
        }

        void weiter(java.time.Duration wie) {
            jetzt = jetzt.plus(wie);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException(
                    "Die Zone kommt aus den Zeitwerten");
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }
}
