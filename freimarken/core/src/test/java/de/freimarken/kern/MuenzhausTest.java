package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Muenzhaus - die Reihenfolge des Kaufs und was in jedem Zweig NICHT passiert.
 *
 * <p><b>Der Vorrat ist hier eine Karte mit Platzbudget</b>, und das Luegen ist sein Zweck:
 * Auf einem Server kann {@code addItem} weniger annehmen als gerechnet, und ohne eine
 * luegende Attrappe waere der Erstattungszweig von keiner Probe durchlaufbar.
 */
@DisplayName("Das Muenzhaus")
class MuenzhausTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());

    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-20T13:37:00Z"), ZoneOffset.UTC);

    /** Ein Rucksack als Karte - mit Platzbudget, Mitschrift und der Moeglichkeit zu luegen. */
    private static final class Kartenvorrat implements Vorrat {

        private final Map<String, Integer> inhalt = new LinkedHashMap<>();
        private final List<String> mitschrift = new ArrayList<>();
        private int platz = 1_000;
        private int nimmtHoechstens = Integer.MAX_VALUE;

        /** So viele Stueck passen noch hinein - 0 heisst: voll. */
        void platzFuer(int stueck) {
            platz = stueck;
        }

        /** Beim Legen kommt hoechstens so viel an, egal wie viel Platz gerechnet war. */
        void nimmtNurAn(int stueck) {
            nimmtHoechstens = stueck;
        }

        @Override
        public int zaehle(String gegenstand) {
            mitschrift.add("zaehle " + gegenstand);
            return inhalt.getOrDefault(gegenstand, 0);
        }

        @Override
        public int nimm(String gegenstand, int stueck) {
            mitschrift.add("nimm " + gegenstand + " " + stueck);
            int da = inhalt.getOrDefault(gegenstand, 0);
            int weg = Math.min(da, stueck);
            inhalt.put(gegenstand, da - weg);
            return weg;
        }

        @Override
        public int lege(String gegenstand, int stueck) {
            mitschrift.add("lege " + gegenstand + " " + stueck);
            int an = Math.min(stueck, nimmtHoechstens);
            inhalt.merge(gegenstand, an, Integer::sum);
            return an;
        }

        @Override
        public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
            mitschrift.add("platz " + gegenstaende);
            int summe = 0;
            for (int stueck : gegenstaende.values()) {
                summe += stueck;
            }
            return Math.max(0, summe - platz);
        }
    }

    /** Eine Kasse, die beim Lesen JA sagt und beim Abbuchen NEIN - der Wettlauf. */
    private static final class LuegendeKasse implements Kasse {

        private final Kasse echte = new SpeicherKasse(0L, UHR);

        @Override
        public long guthaben(UUID spieler) {
            // NUR fuer Anna, und das ist kein Zierrat: Der Konstruktor der Goldkasse
            // liest das Guthaben einer ZUFALLSkennung und verweigert jede Kasse, die
            // dort etwas hergibt - sonst waere es geschenkte Goldmark fuer jeden.
            return spieler.equals(ANNA) ? 1_000_000L : 0L;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return echte.eroeffne(spieler);
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            return false;
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            echte.gutschreiben(spieler, betrag, grund);
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            return echte.verlauf(spieler, hoechstens);
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            return echte.summeSeit(spieler, ab, grund);
        }
    }

    /**
     * Eine Kasse, die beim LESEN wirft - die Datenbank antwortet nicht.
     *
     * <p>Eine eigene Umsetzung und keine Unterklasse von {@code SpeicherKasse}: Die
     * Speicherfassungen sind {@code final}, und das mit Absicht - eine halb
     * ueberschriebene Kasse waere eine zweite Wahrheit ueber Geld.
     *
     * <p>Sie wirft erst bei {@code guthaben} eines bestimmten Spielers, denn der
     * Konstruktor der Goldkasse liest das Guthaben einer ZUFALLSkennung: Wuerfe sie
     * schon dort, gaebe es dieses Haus gar nicht.
     */
    private static final class SchweigendeKasse implements Kasse {

        @Override
        public long guthaben(UUID spieler) {
            if (spieler.equals(ANNA)) {
                throw new IllegalStateException("die Datenbank antwortet nicht");
            }
            return 0L;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return true;
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }
    }

    /** Was ein Haus braucht: Kasse, Stand, Messung - und der Rucksack daneben. */
    private record Haus(Muenzhaus muenzhaus, Goldkasse gold, Kartenvorrat vorrat,
                        List<List<Bedingungsart>> gefragt) {
    }

    private static Haus haus() {
        return haus(Muenzwerte.VORGABE, new EnumMap<>(Bedingungsart.class),
                new Goldkasse(new SpeicherKasse(0L, UHR)));
    }

    private static Haus haus(Muenzwerte werte, Map<Bedingungsart, Long> gemessen,
                             Goldkasse gold) {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitMuenzhaus(werte));
        var gefragt = new ArrayList<List<Bedingungsart>>();
        var vorrat = new Kartenvorrat();
        var muenzhaus = new Muenzhaus(gold, stand, (wer, gebraucht) -> {
            gefragt.add(gebraucht);
            return gemessen;
        });
        return new Haus(muenzhaus, gold, vorrat, gefragt);
    }

    /** Goldmark auf das Konto - ueber den Weg, den es im Spiel gibt. */
    private static void gib(Goldkasse gold, long goldmark) {
        gold.eroeffne(ANNA);
        gold.gutschreiben(ANNA, goldmark, Buchungsgrund.WECHSEL_GOLDMARK_HER);
    }

    @Test
    @DisplayName("Ein Kauf liefert die Gegenstaende und bucht GENAU EINE Zeile")
    void kaufenLiefertUndBucht() {
        Haus haus = haus();
        gib(haus.gold(), 10L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.GEKAUFT, entscheid.bescheid());
        assertEquals(2L, entscheid.preis());
        assertEquals(8L, entscheid.stand(), "der Stand DANACH");
        assertEquals(8L, haus.gold().guthaben(ANNA));
        assertEquals(1, haus.vorrat().inhalt.get("carved_pumpkin"));
        List<Buchung> buch = haus.gold().verlauf(ANNA, 10);
        long kaeufe = buch.stream()
                .filter(zeile -> zeile.grund() == Buchungsgrund.MUENZHAUS_KAUF).count();
        assertEquals(1L, kaeufe, "genau eine Kaufzeile");
        assertEquals(0L, buch.stream()
                        .filter(z -> z.grund() == Buchungsgrund.MUENZHAUS_ERSTATTET)
                        .count(),
                "und keine Erstattung");
    }

    @Test
    @DisplayName("Jede Zeile des Goldbuchs gehoert auch in das Goldbuch")
    void jedeZeileImRichtigenBuch() {
        // Mechanisch und nicht aufgezaehlt: Der schlimmste denkbare Fehler ist eine Zeile
        // im falschen Buch - sie steht da, sie ist richtig gerichtet, und sie erzaehlt
        // von einer Waehrung, die dieses Konto nicht fuehrt.
        Haus haus = haus();
        gib(haus.gold(), 10L);
        haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat());

        for (Buchung zeile : haus.gold().verlauf(ANNA, 10)) {
            assertTrue(zeile.grund().gehoertIn(Buchungsgrund.Buch.GOLDMARK),
                    zeile.grund() + " gehoert in das " + zeile.grund().buch() + "-Buch");
        }
    }

    @Test
    @DisplayName("Kein Platz: abgesagt, BEVOR sich etwas bewegt")
    void keinPlatzBewegtNichts() {
        // DIE REIHENFOLGE DIESES BAUSCHRITTES, und sie ist die des Kistenshops: erst der
        // Platz, dann das Geld. Andersherum stuenden hier zwei Zeilen im Goldbuch - eine
        // Buchung und eine Erstattung - fuer einen Kauf, der nie stattgefunden hat.
        Haus haus = haus();
        gib(haus.gold(), 10L);
        haus.vorrat().platzFuer(0);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.KEIN_PLATZ, entscheid.bescheid());
        assertEquals(1, entscheid.fehlenderPlatz());
        assertEquals(10L, haus.gold().guthaben(ANNA), "nichts abgebucht");
        assertTrue(haus.vorrat().inhalt.isEmpty(), "nichts geliefert");
        assertFalse(haus.vorrat().mitschrift.stream().anyMatch(z -> z.startsWith("lege")),
                "nicht einmal versucht: " + haus.vorrat().mitschrift);
        assertEquals(0L, haus.gold().verlauf(ANNA, 10).stream()
                .filter(z -> z.grund() == Buchungsgrund.MUENZHAUS_KAUF).count());
    }

    @Test
    @DisplayName("Das Muenzhaus nimmt NIE etwas zurueck")
    void nimmtNichtsZurueck() {
        // Die zweite tragende Entscheidung: Die Naht vergleicht nur die ART eines
        // Gegenstandes - ein Rueckweg per nimm koennte das verzauberte Schwert des
        // Spielers treffen und ihm das gelieferte blanke lassen. Darum darf in KEINEM
        // Zweig ein nimm stehen.
        Haus haus = haus();
        gib(haus.gold(), 10L);
        haus.vorrat().nimmtNurAn(0);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.ERSTATTET, entscheid.bescheid());
        assertFalse(haus.vorrat().mitschrift.stream().anyMatch(z -> z.startsWith("nimm")),
                "kein Zurueckholen: " + haus.vorrat().mitschrift);
    }

    @Test
    @DisplayName("Kommt die Lieferung nicht an, ist das Geld zurueck")
    void lieferungScheitertErstattet() {
        Haus haus = haus();
        gib(haus.gold(), 10L);
        haus.vorrat().nimmtNurAn(0);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.ERSTATTET, entscheid.bescheid());
        assertEquals(10L, haus.gold().guthaben(ANNA), "der Stand ist wieder der alte");
        List<Buchung> buch = haus.gold().verlauf(ANNA, 10);
        assertEquals(1L, buch.stream()
                .filter(z -> z.grund() == Buchungsgrund.MUENZHAUS_KAUF).count());
        assertEquals(1L, buch.stream()
                        .filter(z -> z.grund() == Buchungsgrund.MUENZHAUS_ERSTATTET)
                        .count(),
                "die Erstattung steht als EIGENE Zeile im Buch - der Spieler sieht sie");
    }

    @Test
    @DisplayName("Wirft die Kasse nach dem Abbuchen, ist das Geld trotzdem zurueck")
    void wurfNachDemAbbuchenErstattet() {
        // Der schlimmste Fall: Goldmark hat genau zwei Quellen (Plan 8.1), sie entsteht
        // nicht von selbst wieder. Ohne die Erstattung im Faenger waere sie weg.
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus haus = haus(Muenzwerte.VORGABE, new EnumMap<>(Bedingungsart.class), gold);
        gib(gold, 10L);
        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                new Vorrat() {
                    @Override
                    public int zaehle(String gegenstand) {
                        return 0;
                    }

                    @Override
                    public int nimm(String gegenstand, int stueck) {
                        return 0;
                    }

                    @Override
                    public int lege(String gegenstand, int stueck) {
                        throw new IllegalStateException("das Inventar antwortet nicht");
                    }

                    @Override
                    public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
                        return 0;
                    }
                });

        assertEquals(Muenzhaus.Bescheid.ERSTATTET, entscheid.bescheid());
        assertEquals(10L, gold.guthaben(ANNA), "erstattet");
        List<Buchung> buch = gold.verlauf(ANNA, 10);
        assertEquals(1L, buch.stream()
                        .filter(z -> z.grund() == Buchungsgrund.MUENZHAUS_ERSTATTET)
                        .count(),
                "die Erstattung steht im Buch, auch nach einem WURF");
    }

    @Test
    @DisplayName("Zu wenig Goldmark: nichts geliefert, nichts gebucht, und die Zahl steht da")
    void zuWenigGoldmark() {
        Haus haus = haus();
        gib(haus.gold(), 1L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.ZU_WENIG_GOLDMARK, entscheid.bescheid());
        assertEquals(1L, entscheid.stand());
        assertEquals(2L, entscheid.preis());
        assertEquals(1L, entscheid.fehlt(), "die Absage nennt, was FEHLT");
        assertTrue(haus.vorrat().inhalt.isEmpty());
        assertEquals(1L, haus.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Sagt die Kasse beim Abbuchen NEIN, bleibt die Lieferung aus")
    void luegendeKasseLiefertNicht() {
        // Der Riegel ist abbuchen und nicht die Lesung in pruefe: Diese Kasse meldet ein
        // Guthaben und bucht trotzdem nicht ab. Ohne den Riegel wuerde geliefert.
        var gold = new Goldkasse(new LuegendeKasse());
        Haus haus = haus(Muenzwerte.VORGABE, new EnumMap<>(Bedingungsart.class), gold);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.ZU_WENIG_GOLDMARK, entscheid.bescheid());
        assertTrue(haus.vorrat().inhalt.isEmpty(),
                "nicht geliefert, obwohl das Guthaben JA sagte");
    }

    @Test
    @DisplayName("Eine fehlende Bedingung sagt ab und nennt sie")
    void bedingungFehlt() {
        Haus haus = haus();
        gib(haus.gold(), 10L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "blumenkiste",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, entscheid.bescheid());
        assertEquals(1, entscheid.fehlend().size());
        assertEquals(Bedingungsart.PARZELLEN, entscheid.fehlend().get(0).art());
        assertEquals(1L, entscheid.fehlend().get(0).verlangt());
        assertTrue(haus.vorrat().inhalt.isEmpty());
        assertEquals(10L, haus.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Mit dem Messwert geht derselbe Kauf durch")
    void bedingungErfuellt() {
        // Die Gegenrichtung zur Probe darueber - ohne sie waere ein Riegel, der ALLES
        // absagt, genauso gruen.
        Map<Bedingungsart, Long> gemessen = new EnumMap<>(Bedingungsart.class);
        gemessen.put(Bedingungsart.PARZELLEN, 1L);
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus haus = haus(Muenzwerte.VORGABE, gemessen, gold);
        gib(gold, 10L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "blumenkiste",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.GEKAUFT, entscheid.bescheid());
        assertEquals(16, haus.vorrat().inhalt.get("rose_bush"));
    }

    @Test
    @DisplayName("Die Stunden kommen in SEKUNDEN herein - sonst waere die Huerde 3600 mal zu klein")
    void stundenInSekunden() {
        // Die Bedingung steht in der Datei in Stunden, gemessen wird in Sekunden. Wer
        // die Umrechnung vergisst, hat eine Bedingung, die nach einer Minute erfuellt
        // ist.
        Map<Bedingungsart, Long> fastGenug = new EnumMap<>(Bedingungsart.class);
        fastGenug.put(Bedingungsart.TAETIGE_STUNDEN, 4L * 3_600L + 3_599L);
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus knapp = haus(Muenzwerte.VORGABE, fastGenug, gold);
        gib(gold, 10L);

        assertEquals(Muenzhaus.Bescheid.BEDINGUNG_FEHLT,
                knapp.muenzhaus().kaufe(ANNA, "rathausglocke", knapp.vorrat())
                        .bescheid());

        Map<Bedingungsart, Long> genug = new EnumMap<>(Bedingungsart.class);
        genug.put(Bedingungsart.TAETIGE_STUNDEN, 5L * 3_600L);
        var zweiteKasse = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus reicht = haus(Muenzwerte.VORGABE, genug, zweiteKasse);
        gib(zweiteKasse, 10L);

        assertEquals(Muenzhaus.Bescheid.GEKAUFT,
                reicht.muenzhaus().kaufe(ANNA, "rathausglocke", reicht.vorrat())
                        .bescheid());
    }

    @Test
    @DisplayName("Gefragt wird nur nach den Arten, die der Katalog verlangt")
    void messungNurNachDemVerlangten() {
        Haus haus = haus();
        gib(haus.gold(), 10L);

        haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat());

        assertEquals(List.of(List.of(Bedingungsart.TAETIGE_STUNDEN,
                        Bedingungsart.PARZELLEN)), haus.gefragt(),
                "genau die zwei Arten des ausgelieferten Katalogs, einmal gefragt");
    }

    @Test
    @DisplayName("Einen Posten, den es nicht gibt, bekommt der Spieler als Satz gesagt")
    void unbekannterPosten() {
        Haus haus = haus();
        gib(haus.gold(), 10L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().kaufe(ANNA, "gibt-es-nicht",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.UNBEKANNT, entscheid.bescheid());
        assertTrue(entscheid.posten().isEmpty());
        assertEquals(10L, haus.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Ist das Muenzhaus aus, wird nichts verkauft - auch kein vorhandener Posten")
    void ausVerkauftNichts() {
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus haus = haus(Muenzwerte.VORGABE.mitAn(false),
                new EnumMap<>(Bedingungsart.class), gold);
        gib(gold, 10L);

        assertEquals(Muenzhaus.Bescheid.AUS,
                haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat()).bescheid());
        assertEquals(Muenzhaus.Bescheid.AUS,
                haus.muenzhaus().pruefe(ANNA, "kuerbishut", haus.vorrat()).bescheid());
        assertEquals(10L, gold.guthaben(ANNA));
    }

    @Test
    @DisplayName("Ein leerer Katalog verkauft nichts, und die Tafel steht trotzdem")
    void leererKatalog() {
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus haus = haus(new Muenzwerte(true, List.of()),
                new EnumMap<>(Bedingungsart.class), gold);
        gib(gold, 10L);

        assertEquals(Muenzhaus.Bescheid.AUS,
                haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat()).bescheid());
        Muenzhaus.Tafel tafel = haus.muenzhaus().tafel(ANNA).orElseThrow();
        assertEquals(10L, tafel.goldmark(), "der Stand bleibt zu sehen");
        assertTrue(tafel.zeilen().isEmpty());
    }

    @Test
    @DisplayName("pruefe sagt BEREIT und bucht nichts")
    void pruefeBuchtNichts() {
        Haus haus = haus();
        gib(haus.gold(), 10L);

        Muenzhaus.Entscheid entscheid = haus.muenzhaus().pruefe(ANNA, "kuerbishut",
                haus.vorrat());

        assertEquals(Muenzhaus.Bescheid.BEREIT, entscheid.bescheid());
        assertEquals(2L, entscheid.preis());
        assertEquals(10L, entscheid.stand(), "der Stand VOR dem Kauf");
        assertEquals(10L, haus.gold().guthaben(ANNA), "nichts gebucht");
        assertTrue(haus.vorrat().inhalt.isEmpty(), "nichts geliefert");
    }

    @Test
    @DisplayName("pruefe gibt genau die Absage, die auch kaufe geben wuerde")
    void pruefeUndKaufeSagenDasselbe() {
        // Sonst erfaehrt ein Spieler erst NACH der Bestaetigung, dass ihm etwas fehlt.
        for (String posten : List.of("blumenkiste", "gibt-es-nicht")) {
            Haus haus = haus();
            gib(haus.gold(), 10L);

            assertEquals(haus.muenzhaus().pruefe(ANNA, posten, haus.vorrat()).bescheid(),
                    haus.muenzhaus().kaufe(ANNA, posten, haus.vorrat()).bescheid(),
                    "fuer " + posten);
        }
    }

    @Test
    @DisplayName("Die Tafel kennt drei Zustaende je Zeile")
    void tafelKenntDreiZustaende() {
        Map<Bedingungsart, Long> gemessen = new EnumMap<>(Bedingungsart.class);
        gemessen.put(Bedingungsart.PARZELLEN, 1L);
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        Haus haus = haus(Muenzwerte.VORGABE, gemessen, gold);
        gib(gold, 2L);

        Muenzhaus.Tafel tafel = haus.muenzhaus().tafel(ANNA).orElseThrow();

        assertEquals(4, tafel.zeilen().size());
        assertTrue(zeile(tafel, "kuerbishut").kaufbar(), "2 Goldmark, keine Bedingung");
        assertTrue(zeile(tafel, "blumenkiste").kaufbar(), "Bedingung erfuellt");
        assertTrue(zeile(tafel, "musikscheibe").erfuellt(), "keine Bedingung");
        assertFalse(zeile(tafel, "musikscheibe").bezahlbar(), "5 Goldmark hat sie nicht");
        assertFalse(zeile(tafel, "rathausglocke").erfuellt(), "Stunden fehlen");
        assertEquals(1, zeile(tafel, "rathausglocke").fehlend().size());
    }

    private static Muenzhaus.Zeile zeile(Muenzhaus.Tafel tafel, String schluessel) {
        return tafel.zeilen().stream()
                .filter(zeile -> zeile.posten().schluessel().equals(schluessel))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Schweigt die Kasse, gibt es KEINE halbe Tafel und kein halbes Konto")
    void schweigenGibtNichts() {
        var gold = new Goldkasse(new SchweigendeKasse());
        Haus haus = haus(Muenzwerte.VORGABE, new EnumMap<>(Bedingungsart.class), gold);

        assertTrue(haus.muenzhaus().tafel(ANNA).isEmpty());
        assertTrue(haus.muenzhaus().konto(ANNA, 10).isEmpty());
        assertEquals(Muenzhaus.Bescheid.GESTOERT,
                haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat()).bescheid(),
                "GESTOERT und nicht ERSTATTET: Hier hat die Kasse schon beim LESEN"
                        + " geschwiegen, es ist nie abgebucht worden - und wer \"Dein"
                        + " Geld ist zurueck\" liest, sucht danach eine Buchung, die es"
                        + " nicht gibt. Gefunden am 20.11.2026.");
    }

    @Test
    @DisplayName("Das Konto nennt Stand und Buchungen aus EINEM Blick")
    void kontoNenntBeides() {
        Haus haus = haus();
        gib(haus.gold(), 10L);
        haus.muenzhaus().kaufe(ANNA, "kuerbishut", haus.vorrat());

        Muenzhaus.Konto konto = haus.muenzhaus().konto(ANNA, 10).orElseThrow();

        assertEquals(8L, konto.goldmark());
        assertEquals(8L, konto.buchungen().get(0).standNachher(),
                "die neueste Buchung zuerst, und ihr Stand ist der Stand");
    }

    @Test
    @DisplayName("Die GELTENDEN Werte kommen beim Gebrauch, nicht beim Bauen")
    void werteKommenBeimGebrauch() {
        // Sonst erreichte /freimarken neuladen den Katalog nicht.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        var muenzhaus = new Muenzhaus(gold, stand, (wer, gebraucht) -> Map.of());
        assertEquals(4, muenzhaus.werte().katalog().size());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitMuenzhaus(
                Muenzwerte.VORGABE.mitKatalog(List.of())));

        assertEquals(0, muenzhaus.werte().katalog().size());
    }

    @Test
    @DisplayName("Ohne Spieler, Vorrat, Kasse, Stand oder Messung gibt es kein Muenzhaus")
    void riegel() {
        var gold = new Goldkasse(new SpeicherKasse(0L, UHR));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzhaus(null, stand, (wer, gebraucht) -> Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzhaus(gold, null, (wer, gebraucht) -> Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzhaus(gold, stand, null));

        Haus haus = haus();
        assertThrows(IllegalArgumentException.class,
                () -> haus.muenzhaus().kaufe(null, "kuerbishut", haus.vorrat()));
        assertThrows(IllegalArgumentException.class,
                () -> haus.muenzhaus().kaufe(ANNA, "kuerbishut", null));
        assertThrows(IllegalArgumentException.class, () -> haus.muenzhaus().tafel(null));
        assertThrows(IllegalArgumentException.class,
                () -> haus.muenzhaus().konto(null, 10));
    }
}
