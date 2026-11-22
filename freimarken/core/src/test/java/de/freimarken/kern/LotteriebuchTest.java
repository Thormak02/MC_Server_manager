package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Tat: ein Los ziehen - zaehlen, zahlen, auszahlen.
 *
 * <p><b>Die Reihenfolge ist das, was hier geprueft wird</b>, und nicht nur das Ergebnis:
 * Beim gratis Los ist die Zaehlung der einzige Riegel, denn {@code abbuchen(0)} gelingt
 * immer. Faellt sie aus und wird trotzdem gezogen, zieht derselbe Spieler endlos gratis -
 * das ist eine Geldquelle. Darum stehen hier Saetze, die eine werfende Ablage einbauen und
 * nachsehen, ob wirklich nichts passiert ist.
 */
@DisplayName("Lotteriebuch")
class LotteriebuchTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Mittags am 22.11.2026 - weit weg von jeder Tagesgrenze. */
    private static final Instant MITTAGS =
            LocalDate.of(2026, 11, 22).atTime(12, 0).atZone(BERLIN).toInstant();

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 22);

    /** Drei Gewinne, jeder mit Gewicht 1 - der Wurf waehlt also eindeutig. */
    private static Lotteriewerte werte() {
        return new Lotteriewerte(true, 1, 3, 500L, 200, 250_000L, List.of(
                new Lotteriewerte.Gewinn("a-mark", Lotteriewerte.Art.MARK, 1, 700L, "",
                        ""),
                new Lotteriewerte.Gewinn("b-ding", Lotteriewerte.Art.GEGENSTAND, 1, 4L,
                        "diamond", ""),
                new Lotteriewerte.Gewinn("c-gold", Lotteriewerte.Art.GOLDMARK, 1, 3L, "",
                        "")));
    }

    /** Wurf 0 trifft a-mark, 1 trifft b-ding, 2 trifft c-gold (sortiert, Gewicht je 1). */
    private static final long WURF_MARK = 0L;

    private static final long WURF_DING = 1L;

    private static final long WURF_GOLD = 2L;

    /** Alles, was ein Zug braucht - damit jede Probe ihre eigene Welt hat. */
    private static final class Welt {
        final UUID anna = UUID.randomUUID();
        final SpeicherKasse mark = new SpeicherKasse(0L);
        final Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));
        final SpeicherLose lose = new SpeicherLose();
        final Wirtschaftsstand stand;
        final Rucksack rucksack = new Rucksack();
        final Lotteriebuch buch;

        Welt(Lotteriewerte werte, long guthaben) {
            stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                    .mitLotterie(werte)
                    .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                            Zeitwerte.VORGABE.untaetigNach())));
            mark.eroeffne(anna);
            gold.eroeffne(anna);
            if (guthaben > 0L) {
                mark.gutschreiben(anna, guthaben, Buchungsgrund.BESTAND);
            }
            buch = new Lotteriebuch(mark, gold, lose, stand);
        }
    }

    /** Ein Rucksack mit Zahlen - dieselbe Naht, die auch das Muenzhaus benutzt. */
    private static final class Rucksack implements Vorrat {
        final Map<String, Integer> inhalt = new HashMap<>();
        final List<String> gelegt = new ArrayList<>();
        int freiePlaetze = 36;

        @Override
        public int zaehle(String gegenstand) {
            return inhalt.getOrDefault(gegenstand, 0);
        }

        @Override
        public int nimm(String gegenstand, int stueck) {
            int da = zaehle(gegenstand);
            int weg = Math.min(da, stueck);
            inhalt.put(gegenstand, da - weg);
            return weg;
        }

        @Override
        public int lege(String gegenstand, int stueck) {
            // MITGESCHRIEBEN VOR dem Rechnen: Eine Attrappe, die erst am Ende notiert,
            // ist bei einem werfenden Aufruf blind - und genau den baut eine der Proben
            // unten ein.
            gelegt.add(gegenstand + " x" + stueck);
            int passt = Math.min(stueck, freiePlaetze * 64);
            inhalt.merge(gegenstand, passt, Integer::sum);
            return passt;
        }

        @Override
        public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
            int summe = 0;
            for (int stueck : gegenstaende.values()) {
                summe += stueck;
            }
            return Math.max(0, summe - freiePlaetze * 64);
        }
    }

    /**
     * Die Buchungsgruende der Lotterie im Verlauf - ohne Eroeffnung und Bestand.
     *
     * <p>Die Probenumgebung eroeffnet ein Konto und schreibt ein Guthaben, und beides
     * steht im Buch. Ein Satz, der "keine Buchung" verlangt, prueft damit die Umgebung
     * und nicht die Lotterie.
     */
    private static List<Buchungsgrund> lotteriezeilen(Kasse kasse, UUID spieler) {
        List<Buchungsgrund> gruende = new ArrayList<>();
        for (Buchung buchung : kasse.verlauf(spieler, 50)) {
            if (buchung.grund() == Buchungsgrund.LOS_GEZOGEN
                    || buchung.grund() == Buchungsgrund.LOS_ERSTATTET
                    || buchung.grund() == Buchungsgrund.LOTTERIE_GEWINN
                    || buchung.grund() == Buchungsgrund.LOTTERIE_GOLDGEWINN) {
                gruende.add(buchung.grund());
            }
        }
        return gruende;
    }

    @Test
    @DisplayName("Das erste Los am Tag kostet nichts und zahlt aus")
    void erstesLosIstGratis() {
        Welt welt = new Welt(werte(), 0L);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
        assertEquals(0L, entscheid.preis(), "das erste Los ist gratis");
        assertEquals("a-mark", entscheid.gewinn().orElseThrow().schluessel());
        assertEquals(1, entscheid.loseHeute());
        // MIT NULL GUTHABEN gestartet: Wer nichts hat, kann trotzdem ziehen - das ist der
        // Kern der ganzen Einrichtung.
        assertEquals(700L, welt.mark.guthaben(welt.anna));
    }

    @Test
    @DisplayName("Das zweite Los kostet den Grundpreis und wird abgebucht")
    void zweitesLosKostet() {
        Welt welt = new Welt(werte(), 10_000L);
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);
        long nachDemErsten = welt.mark.guthaben(welt.anna);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
        assertEquals(500L, entscheid.preis());
        assertEquals(2, entscheid.loseHeute());
        assertEquals(nachDemErsten - 500L + 700L, welt.mark.guthaben(welt.anna),
                "500 Mark fuer das Los ab, 700 Mark Gewinn drauf");
    }

    @Test
    @DisplayName("Das Los steht als eigene Buchung im Buch - auch das gratis")
    void losStehtImBuch() {
        // Der Grund fuer den eigenen Buchungsgrund: Ohne ihn saehe der Spieler im Verlauf
        // nur die Gutschrift und wuesste nicht, woher sie kommt. Und das GRATIS-Los muss
        // dort stehen, obwohl es 0 Mark ist - sonst fehlt die Zeile ganz.
        Welt welt = new Welt(werte(), 0L);

        welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        List<Buchungsgrund> gruende = lotteriezeilen(welt.mark, welt.anna);
        assertTrue(gruende.contains(Buchungsgrund.LOS_GEZOGEN),
                "das Los selbst fehlt im Buch: " + gruende);
        assertTrue(gruende.contains(Buchungsgrund.LOTTERIE_GEWINN),
                "der Gewinn fehlt im Buch: " + gruende);
    }

    @Test
    @DisplayName("Ein Gegenstandsgewinn landet im Rucksack")
    void gegenstandsgewinn() {
        Welt welt = new Welt(werte(), 0L);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
        assertEquals("b-ding", entscheid.gewinn().orElseThrow().schluessel());
        assertEquals(4, welt.rucksack.zaehle("diamond"));
        assertEquals(0L, welt.mark.guthaben(welt.anna), "ein Ding ist kein Geld");
    }

    @Test
    @DisplayName("Ein Goldgewinn landet auf dem GOLDkonto, nicht auf dem Markkonto")
    void goldgewinn() {
        Welt welt = new Welt(werte(), 0L);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_GOLD, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
        assertEquals(3L, welt.gold.guthaben(welt.anna));
        assertEquals(0L, welt.mark.guthaben(welt.anna),
                "drei Goldmark gehoeren nicht auf das Markkonto");
    }

    @Test
    @DisplayName("Bei zu wenig Geld passiert NICHTS - kein Los, keine Zaehlung")
    void zuWenigGeld() {
        Welt welt = new Welt(werte(), 0L);
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);
        // Nach dem gratis Los hat Anna 700 Mark - das zweite Los kostet 500, das dritte
        // 1000. Also erst noch eines ziehen, damit sie zu arm fuer das naechste ist.
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING, welt.rucksack);
        long vorher = welt.mark.guthaben(welt.anna);
        int loseVorher = welt.lose.lies(welt.anna, HEUTE).loseHeute();

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(1_000L, entscheid.preis(), "die Absage nennt den Preis");
        assertEquals(vorher, welt.mark.guthaben(welt.anna), "nichts abgebucht");
        assertEquals(loseVorher, welt.lose.lies(welt.anna, HEUTE).loseHeute(),
                "und nichts gezaehlt - das Los ist nicht verbraucht");
    }

    @Test
    @DisplayName("Die Tagesgrenze haelt - und sie haelt NACH dem letzten erlaubten Los")
    void tagesgrenze() {
        Welt welt = new Welt(werte(), 1_000_000L);

        for (int i = 1; i <= 3; i++) {
            assertEquals(Lotteriebuch.Bescheid.GEZOGEN,
                    welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack)
                            .bescheid(),
                    "Los " + i + " muss noch gehen - die Grenze ist 3");
        }

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.HEUTE_GENUG, entscheid.bescheid());
        assertEquals(3, welt.lose.lies(welt.anna, HEUTE).loseHeute(),
                "das vierte wurde nicht gezaehlt");
    }

    @Test
    @DisplayName("Am naechsten Tag ist wieder eines gratis")
    void naechsterTagWiederGratis() {
        Welt welt = new Welt(werte(), 1_000_000L);
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        Instant morgen = MITTAGS.plusSeconds(24 * 60 * 60);
        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, morgen, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
        assertEquals(0L, entscheid.preis(), "der neue Tag bringt ein neues gratis Los");
        assertEquals(1, entscheid.loseHeute(), "und die Zaehlung faengt neu an");
    }

    @Test
    @DisplayName("Der Tag haengt an der eingestellten ZONE und nicht an UTC")
    void tagKommtAusDerZone() {
        // 23:30 Uhr Berliner Zeit ist 22:30 UTC - derselbe Tag in UTC, aber in Berlin
        // schon fast der naechste. Eine halbe Stunde spaeter ist es in Berlin der
        // naechste Tag und in UTC noch derselbe. Genau dort trennt sich eine Lotterie,
        // die Tagesgrenze.tagVon benutzt, von einer, die UTC nimmt.
        Welt welt = new Welt(werte(), 1_000_000L);
        Instant spaetAbends = LocalDate.of(2026, 11, 22).atTime(23, 30)
                .atZone(BERLIN).toInstant();
        Instant nachMitternacht = LocalDate.of(2026, 11, 23).atTime(0, 30)
                .atZone(BERLIN).toInstant();

        welt.buch.ziehe(welt.anna, spaetAbends, WURF_MARK, welt.rucksack);
        Lotteriebuch.Entscheid danach =
                welt.buch.ziehe(welt.anna, nachMitternacht, WURF_MARK, welt.rucksack);

        assertEquals(0L, danach.preis(),
                "in Berlin ist das ein neuer Tag - in UTC waere es noch derselbe, und"
                        + " dann kostete dieses Los 500");
        assertEquals(LocalDate.of(2026, 11, 23), welt.buch.tagVon(nachMitternacht));
    }

    @Test
    @DisplayName("Ein voller Rucksack kostet kein Los und kein Geld")
    void vollerRucksack() {
        // PLATZ VOR GELD, und zwar VOR der Zaehlung: Ein voller Rucksack ist der
        // haeufigste Fehlschlag. Wer ihn erst nach der Zaehlung bemerkt, nimmt dem
        // Spieler sein Los fuer nichts.
        Welt welt = new Welt(werte(), 1_000_000L);
        welt.rucksack.freiePlaetze = 0;

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.KEIN_PLATZ, entscheid.bescheid());
        assertEquals(4, entscheid.fehlenderPlatz(), "die Meldung sagt, wie viel fehlt");
        assertEquals(0, welt.lose.lies(welt.anna, HEUTE).loseHeute(),
                "nicht gezaehlt - das Los bleibt dem Spieler");
        assertTrue(welt.rucksack.gelegt.isEmpty(), "und nichts versucht hineinzulegen");
    }

    @Test
    @DisplayName("Ein MARK-Gewinn braucht keinen Platz - auch bei vollem Rucksack")
    void geldBrauchtKeinenPlatz() {
        Welt welt = new Welt(werte(), 1_000_000L);
        welt.rucksack.freiePlaetze = 0;

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GEZOGEN, entscheid.bescheid());
    }

    @Test
    @DisplayName("Eine werfende Ablage beim ZAEHLEN laesst nichts zurueck")
    void werfendeAblageBeimZaehlen() {
        // DIE wichtigste Probe dieser Klasse. Ungezaehlt wird nicht gezogen: Beim gratis
        // Los ist die Zaehlung der einzige Riegel, denn abbuchen(0) gelingt immer. Ohne
        // diesen Zweig zieht ein Spieler bei kaputter Datenbank endlos gratis.
        Welt welt = new Welt(werte(), 0L);
        Lotteriebuch buch = new Lotteriebuch(welt.mark, welt.gold, new Losablage() {
            @Override
            public Losstand lies(UUID spieler, LocalDate tag) {
                return Losstand.leer(spieler, tag);
            }

            @Override
            public int zaehleEins(UUID spieler, LocalDate tag) {
                throw new IllegalStateException("Datenbank weg");
            }
        }, welt.stand);

        Lotteriebuch.Entscheid entscheid =
                buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(0L, welt.mark.guthaben(welt.anna),
                "kein Gewinn ausgezahlt - sonst waere die kaputte Datenbank eine"
                        + " Geldmaschine");
        // KEINE LOTTERIEBUCHUNG, und nicht "keine einzige Buchung": Die Eroeffnung des
        // Kontos steht auch im Buch, und ein Satz, der sie mitzaehlt, prueft die
        // Probenumgebung statt die Lotterie.
        assertEquals(List.of(), lotteriezeilen(welt.mark, welt.anna),
                "keine Zeile der Lotterie darf stehen");
    }

    @Test
    @DisplayName("Eine werfende Ablage beim LESEN laesst nichts zurueck")
    void werfendeAblageBeimLesen() {
        // Ohne die Zahl ist der Preis nicht zu bestimmen, und ein Los zum falschen Preis
        // ist schlimmer als keines.
        Welt welt = new Welt(werte(), 1_000_000L);
        Lotteriebuch buch = new Lotteriebuch(welt.mark, welt.gold, new Losablage() {
            @Override
            public Losstand lies(UUID spieler, LocalDate tag) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public int zaehleEins(UUID spieler, LocalDate tag) {
                throw new IllegalStateException("gar nicht erst hierher");
            }
        }, welt.stand);
        long vorher = welt.mark.guthaben(welt.anna);

        Lotteriebuch.Entscheid entscheid =
                buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(vorher, welt.mark.guthaben(welt.anna));
    }

    @Test
    @DisplayName("Kommt der Gewinn nicht an, ist der LOSPREIS zurueck")
    void erstattungBeiHalberLieferung() {
        // Nach einer genauen Platzrechnung kann das nicht eintreten - also ist es ein
        // Programm- oder Weltfehler. Der Rucksack hier luegt beim Platz und nimmt dann
        // nichts: genau der Fall, gegen den die Erstattung steht.
        Welt welt = new Welt(werte(), 1_000_000L);
        // Das erste Los auf GOLD und nicht auf MARK: Es dient nur dazu, dass das zweite
        // Geld kostet. Mit einem Markgewinn stuende danach eine LOTTERIE_GEWINN-Zeile im
        // Buch, und der Satz unten koennte nicht mehr sagen, ob sie vom ersten Los kommt
        // oder von einer falschen Erstattung.
        welt.buch.ziehe(welt.anna, MITTAGS, WURF_GOLD, welt.rucksack);
        long vorher = welt.mark.guthaben(welt.anna);

        // Der Rucksack unten sagt "alles passt" und nimmt dann nichts - das ist die
        // Luege, die nach einer genauen Platzrechnung nicht vorkommen darf.
        Lotteriebuch.Entscheid entscheid = welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING,
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
                        return 0;
                    }

                    @Override
                    public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
                        return 0;
                    }
                });

        assertEquals(Lotteriebuch.Bescheid.ERSTATTET, entscheid.bescheid());
        assertEquals(4, entscheid.fehlenderPlatz(), "vier Stueck kamen nicht an");
        assertEquals(vorher, welt.mark.guthaben(welt.anna),
                "der Lospreis ist zurueck - unterm Strich dasselbe Guthaben");
        List<Buchungsgrund> gruende = lotteriezeilen(welt.mark, welt.anna);
        assertTrue(gruende.contains(Buchungsgrund.LOS_ERSTATTET),
                "erstattet wird unter LOS_ERSTATTET und nicht als Gewinn, den es nie"
                        + " gab: " + gruende);
        assertFalse(gruende.contains(Buchungsgrund.LOTTERIE_GEWINN),
                "ein Gegenstandsgewinn schreibt keine Markgutschrift: " + gruende);
    }

    @Test
    @DisplayName("Ein Wurf ausserhalb der Tabelle kostet nichts")
    void wurfAusserhalb() {
        Welt welt = new Welt(werte(), 1_000_000L);
        long vorher = welt.mark.guthaben(welt.anna);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, 99L, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(vorher, welt.mark.guthaben(welt.anna),
                "ein Los ohne Gewinn waere bezahlter Betrug");
        assertEquals(0, welt.lose.lies(welt.anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Eine ausgeschaltete Lotterie zieht nicht")
    void ausgeschaltet() {
        Welt welt = new Welt(werte().mitAn(false), 1_000_000L);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack);

        assertEquals(Lotteriebuch.Bescheid.AUS, entscheid.bescheid());
        assertEquals(0, welt.lose.lies(welt.anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Ein Gegenstandsgewinn ohne Rucksack ist gestoert, nicht geliefert")
    void gegenstandOhneRucksack() {
        Welt welt = new Welt(werte(), 1_000_000L);

        Lotteriebuch.Entscheid entscheid =
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING, null);

        assertEquals(Lotteriebuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(0, welt.lose.lies(welt.anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Die Auskunft sagt den Preis, ohne etwas zu tun")
    void auskunft() {
        Welt welt = new Welt(werte(), 400L);

        Lotteriebuch.Auskunft erste = welt.buch.frage(welt.anna, MITTAGS);

        assertTrue(erste.laeuft());
        assertEquals(0, erste.loseHeute());
        assertEquals(0L, erste.preis(), "das erste ist gratis");
        assertEquals(3, erste.hoechsteLose());
        assertEquals(400L, erste.guthaben());
        assertTrue(erste.koennteZiehen());
        assertEquals(0, welt.lose.lies(welt.anna, HEUTE).loseHeute(),
                "die Auskunft darf nichts zaehlen");

        welt.buch.ziehe(welt.anna, MITTAGS, WURF_DING, welt.rucksack);

        Lotteriebuch.Auskunft zweite = welt.buch.frage(welt.anna, MITTAGS);
        assertEquals(1, zweite.loseHeute());
        assertEquals(500L, zweite.preis());
        assertFalse(zweite.koennteZiehen(), "400 Mark reichen nicht fuer 500");
    }

    @Test
    @DisplayName("Ohne Spieler oder Zeitpunkt geht nichts")
    void pflichtangaben() {
        Welt welt = new Welt(werte(), 0L);

        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.ziehe(null, MITTAGS, 0L, welt.rucksack));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.ziehe(welt.anna, null, 0L, welt.rucksack));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.frage(null, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.frage(welt.anna, null));
    }

    @Test
    @DisplayName("Ohne seine vier Teile laesst sich kein Lotteriebuch bauen")
    void konstruktorPflichten() {
        Welt welt = new Welt(werte(), 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch(null, welt.gold, welt.lose, welt.stand));
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch(welt.mark, null, welt.lose, welt.stand));
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch(welt.mark, welt.gold, null, welt.stand));
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch(welt.mark, welt.gold, welt.lose, null));
    }

    @Test
    @DisplayName("Die Werte kommen beim GEBRAUCH aus dem Stand - Neuladen wirkt")
    void neuladenWirkt() {
        // Ohne diese Zusage stuende die Lotterie nach /freimarken neuladen noch mit der
        // Gewinntabelle des Serverstarts da - dieselbe Falle wie beim Muenzhaus.
        Welt welt = new Welt(werte(), 1_000_000L);
        assertTrue(welt.buch.werte().greift());

        welt.stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitLotterie(werte().mitAn(false))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                            Zeitwerte.VORGABE.untaetigNach())));

        assertFalse(welt.buch.werte().greift(), "das Neuladen hat die Lotterie nicht"
                + " erreicht");
        assertEquals(Lotteriebuch.Bescheid.AUS,
                welt.buch.ziehe(welt.anna, MITTAGS, WURF_MARK, welt.rucksack).bescheid());
    }

    @Test
    @DisplayName("Ein GEZOGEN ohne Gewinn ist nicht zu bauen")
    void gezogenBrauchtGewinn() {
        // Der Riegel im Entscheid selbst: Eine Meldung, die nicht sagen kann, was der
        // Spieler bekommen hat, ist bezahlter Betrug.
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch.Entscheid(Lotteriebuch.Bescheid.GEZOGEN,
                        java.util.Optional.empty(), 0L, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Lotteriebuch.Entscheid(Lotteriebuch.Bescheid.GEZOGEN,
                        java.util.Optional.of(werte().gewinne().get(0)), 0L, 0, 0));
    }
}
