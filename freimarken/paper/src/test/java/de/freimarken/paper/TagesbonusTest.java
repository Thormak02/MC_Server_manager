package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.Bonusstand;
import de.freimarken.kern.Bonuswerte;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.SpeicherBonus;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Tagesbonus OHNE Server - moeglich, weil er kein Bukkit kennt.
 *
 * <p>Geprueft wird vor allem die tragende Zusage dieses Teils: <b>Kein Weg zahlt zweimal
 * am selben Tag</b>, und der Waechter darueber ist das BUCHUNGSBUCH und nicht die
 * Serienzeile. Darum stehen hier Proben, in denen die Serienzeile fehlt, veraltet ist oder
 * nicht geschrieben werden kann - und in jeder davon faellt kein zweites Geld.
 *
 * <p>Alle Uhren sind DIESELBE: Die Kasse bucht mit ihrer Uhr, der Bonus fragt mit seiner
 * nach dem Tagesbeginn. Zwei Uhren waeren an einer Tagesgrenze zwei verschiedene Tage -
 * dieselbe Falle, die {@code MarktBefehlTest} schon einmal flackernd rot gemacht hat.
 */
@DisplayName("Der Tagesbonus")
class TagesbonusTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final UUID BERT = UUID.fromString("0000be27-0000-0000-0000-000000000002");

    /** Ein Nachmittag in Berlin, weit weg von jeder Tagesgrenze. */
    private static final Instant START = Instant.parse("2026-11-07T12:00:00Z");

    private static final LocalDate TAG = LocalDate.of(2026, 11, 7);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpielzeitbuchTest.StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private SpeicherBonus ablage;
    private SpeicherKasse kasse;
    private SpeicherSpielzeit spielzeit;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new SpielzeitbuchTest.StellUhr(START);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        ablage = new SpeicherBonus();
        // Startguthaben 0, damit die Kontoeroeffnung die Betraege dieser Proben nicht
        // verdeckt: Geprueft wird, was der BONUS bringt.
        kasse = new SpeicherKasse(0L, uhr);
        spielzeit = new SpeicherSpielzeit();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("TagesbonusProbe-" + UUID.randomUUID());
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

    private Tagesbonus bonus() {
        return new Tagesbonus(wirtschaft, ablage, kasse, spielzeit, uhr, protokoll);
    }

    private Tagesbonus bonus(Bonusablage andere) {
        return new Tagesbonus(wirtschaft, andere, kasse, spielzeit, uhr, protokoll);
    }

    private Tagesbonus bonus(Kasse andere) {
        return new Tagesbonus(wirtschaft, ablage, andere, spielzeit, uhr, protokoll);
    }

    private void stelleWerte(Bonuswerte werte) {
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitBonus(werte));
    }

    private boolean protokollNennt(String stueck) {
        return protokollzeilen.stream()
                .anyMatch(satz -> satz.getMessage().contains(stueck));
    }

    @Test
    @DisplayName("Der erste Bonus bringt den Grundbetrag und legt die Serie an")
    void ersterBonus() {
        Tagesbonus.Zahlung zahlung = bonus().pruefe(ANNA);

        assertNotNull(zahlung);
        assertEquals(500L, zahlung.betrag());
        assertEquals(1, zahlung.serie());
        assertEquals(1, zahlung.laengsteSerie());
        assertTrue(zahlung.neuBegonnen());
        assertEquals(500L, kasse.guthaben(ANNA));
        assertEquals(TAG, ablage.lies(ANNA).letzterTag());
    }

    @Test
    @DisplayName("Ein zweiter Aufruf am selben Tag zahlt NICHT")
    void nichtZweimalAmTag() {
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);

        assertNull(bonus.pruefe(ANNA));
        assertNull(bonus.pruefe(ANNA));
        assertEquals(500L, kasse.guthaben(ANNA),
                "einmal gezahlt heisst einmal - der Bonus wird beim Beitritt UND in jedem"
                        + " Taktschlag gefragt");
    }

    @Test
    @DisplayName("Das BUCHUNGSBUCH wacht und nicht die Serienzeile")
    void buchungsbuchWacht() {
        // Die tragende Zusage. Hier ist die Serienzeile GELOESCHT, obwohl die Buchung von
        // heute im Buch steht - genau die Lage nach einem Absturz zwischen Zahlung und
        // Schreibzug. Es darf kein zweites Geld fallen.
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);
        ablage.schreibe(Bonusstand.leer(ANNA));

        assertNull(bonus.pruefe(ANNA));
        assertEquals(500L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("Eine veraltete Serienzeile zahlt kein zweites Mal")
    void veralteteZeileZahltNicht() {
        // Dieselbe Lage von der anderen Seite: Die Zeile steht auf GESTERN, die Buchung
        // von heute ist da. Ohne den Waechter stuende hier Serientag 2 und 600 Mark.
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);
        ablage.schreibe(new Bonusstand(ANNA, TAG.minusDays(1), 1, 1));

        assertNull(bonus.pruefe(ANNA));
        assertEquals(500L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("Am naechsten Tag setzt die Serie fort - und der Betrag waechst")
    void naechsterTagSetztFort() {
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);

        uhr.weiter(Duration.ofDays(1));
        Tagesbonus.Zahlung zahlung = bonus.pruefe(ANNA);

        assertNotNull(zahlung);
        assertEquals(600L, zahlung.betrag());
        assertEquals(2, zahlung.serie());
        assertFalse(zahlung.neuBegonnen());
        assertTrue(zahlung.istBestmarke());
        assertEquals(1_100L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("Nach einer Luecke beginnt die Serie neu - die Bestmarke bleibt")
    void nachDerLueckeNeu() {
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);
        uhr.weiter(Duration.ofDays(1));
        bonus.pruefe(ANNA);
        uhr.weiter(Duration.ofDays(1));
        bonus.pruefe(ANNA);

        // Zwei Tage auslassen.
        uhr.weiter(Duration.ofDays(3));
        Tagesbonus.Zahlung zahlung = bonus.pruefe(ANNA);

        assertNotNull(zahlung);
        assertEquals(500L, zahlung.betrag(), "wieder der Grundbetrag");
        assertEquals(1, zahlung.serie());
        assertTrue(zahlung.neuBegonnen());
        assertEquals(3, zahlung.laengsteSerie(), "drei Tage sind geschehen");
        assertFalse(zahlung.istBestmarke());
    }

    @Test
    @DisplayName("Eine Serie bis zum Hoechstbetrag waechst nicht darueber")
    void serieWaechstBisZumDeckel() {
        Tagesbonus bonus = bonus();
        long vorher = 0L;
        for (int tag = 0; tag < 20; tag++) {
            Tagesbonus.Zahlung zahlung = bonus.pruefe(ANNA);
            assertNotNull(zahlung, "Tag " + tag);
            assertTrue(zahlung.betrag() >= vorher, "der Betrag darf nie sinken");
            assertTrue(zahlung.betrag() <= Bonuswerte.VORGABE.hoechstbetrag(),
                    "Tag " + tag + " gab " + zahlung.betrag());
            vorher = zahlung.betrag();
            uhr.weiter(Duration.ofDays(1));
        }

        assertEquals(20, ablage.lies(ANNA).serie());
        assertEquals(Bonuswerte.VORGABE.hoechstbetrag(), vorher);
    }

    @Test
    @DisplayName("Jeder Spieler hat seine eigene Serie und sein eigenes Geld")
    void spielerSindGetrennt() {
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);
        uhr.weiter(Duration.ofDays(1));
        bonus.pruefe(ANNA);
        Tagesbonus.Zahlung berts = bonus.pruefe(BERT);

        assertNotNull(berts);
        assertEquals(1, berts.serie(), "Berts erster Tag ist nicht Annas zweiter");
        assertEquals(2, ablage.lies(ANNA).serie());
        assertEquals(1_100L, kasse.guthaben(ANNA));
        assertEquals(500L, kasse.guthaben(BERT));
    }

    @Test
    @DisplayName("Mit Mindestzeit faellt beim Einloggen NICHTS")
    void mindestzeitWartet() {
        stelleWerte(Bonuswerte.VORGABE.mitMindestMinuten(10L));

        assertNull(bonus().pruefe(ANNA));
        assertEquals(0L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("Mit Mindestzeit faellt der Bonus, sobald die TAETIGEN Minuten reichen")
    void mindestzeitErreicht() {
        // Die taetige Zeit aus Teil G und NICHT die Anwesenheit: Anwesenheit ist im
        // Stehen zu haben, und genau dagegen ist diese Zahl gedacht.
        stelleWerte(Bonuswerte.VORGABE.mitMindestMinuten(10L));
        Tagesbonus bonus = bonus();
        // Zwanzig Minuten ANWESEND, davon nichts taetig - das genuegt nicht.
        spielzeit.schreibeFort(List.of(
                Spielzeitablage.Zuschlag.nurAnwesend(ANNA, TAG, 1_200L)));

        assertNull(bonus.pruefe(ANNA),
                "zwanzig Minuten herumstehen sind keine zehn Minuten Taetigkeit");

        // Jetzt zehn Minuten taetig.
        spielzeit.schreibeFort(List.of(
                Spielzeitablage.Zuschlag.ganzTaetig(ANNA, TAG, 600L)));

        Tagesbonus.Zahlung zahlung = bonus.pruefe(ANNA);
        assertNotNull(zahlung);
        assertEquals(500L, zahlung.betrag());
    }

    @Test
    @DisplayName("Ohne Mindestzeit wird die Spielzeitablage GAR NICHT gefragt")
    void ohneMindestzeitKeineAbfrage() {
        // Der Normalfall, und er faellt beim Beitritt an. Eine Abfrage dort kostet eine
        // Netzrunde im Hauptfaden fuer eine Antwort, die niemand braucht - darum fragt
        // der Bonus die Ablage nur, wenn eine Zeit eingestellt ist. Geprueft mit einer
        // Ablage, die JEDE Frage mit einem Wurf beantwortet.
        Spielzeitablage werfende = new Spielzeitablage() {
            @Override
            public void schreibeFort(List<Zuschlag> zuschlaege) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public de.freimarken.kern.Spielzeitstand lies(UUID spieler, LocalDate heute) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }
        };

        Tagesbonus.Zahlung zahlung = new Tagesbonus(wirtschaft, ablage, kasse, werfende,
                uhr, protokoll).pruefe(ANNA);

        assertNotNull(zahlung, "ohne Mindestzeit darf die Spielzeit keine Rolle spielen");
    }

    @Test
    @DisplayName("Antwortet die Ablage nicht, wird NICHTS gezahlt - und es steht im Protokoll")
    void ablageAntwortetNicht() {
        // Keine Zahlung auf Verdacht: Ohne die Frage "heute schon bezahlt" ist jede
        // Gutschrift moeglicherweise eine zweite.
        Bonusablage tot = new Bonusablage() {
            @Override
            public Bonusstand lies(UUID spieler) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public void schreibe(Bonusstand stand) {
                throw new IllegalStateException("Datenbank weg");
            }
        };

        assertNull(bonus(tot).pruefe(ANNA));
        assertEquals(0L, kasse.guthaben(ANNA));
        assertTrue(protokollNennt("nicht geprueft"), protokollzeilen.toString());
    }

    @Test
    @DisplayName("Nimmt die Kasse nichts an, bleibt die Serie unveraendert")
    void kasseNimmtNichtsAn() {
        // Eine Kasse, die beim Gutschreiben wirft - und beim Fragen antwortet, damit
        // die Pruefung "heute schon bezahlt" ueberhaupt stattfindet.
        Kasse werfende = new Kasse() {
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
                throw new IllegalStateException("Kasse weg");
            }

            @Override
            public List<de.freimarken.kern.Buchung> verlauf(UUID spieler,
                                                            int hoechstens) {
                return List.of();
            }

            @Override
            public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
                return 0L;
            }
        };
        assertNull(bonus(werfende).pruefe(ANNA));
        assertFalse(ablage.lies(ANNA).schonDagewesen(),
                "ohne Zahlung darf die Serie nicht wachsen - sonst waere morgen Tag 2,"
                        + " obwohl Tag 1 nie gezahlt wurde");
        assertTrue(protokollNennt("nicht gebucht"), protokollzeilen.toString());
    }

    @Test
    @DisplayName("Scheitert der Schreibzug NACH der Zahlung, ist das Geld da und es steht laut im Protokoll")
    void schreibzugScheitertNachDerZahlung() {
        // Die gewollte Reihenfolge: zahlen, dann schreiben. Hier scheitert das Schreiben.
        // Gezahlt ist gezahlt - es wird NICHT zurueckgebucht, denn eine Rueckbuchung
        // waere eine zweite Geldbewegung fuer einen Fehler, der kein Geld betrifft.
        Bonusablage nurLesen = new Bonusablage() {
            @Override
            public Bonusstand lies(UUID spieler) {
                return Bonusstand.leer(spieler);
            }

            @Override
            public void schreibe(Bonusstand stand) {
                throw new IllegalStateException("Datenbank weg");
            }
        };

        Tagesbonus.Zahlung zahlung = bonus(nurLesen).pruefe(ANNA);

        assertNotNull(zahlung, "die Zahlung ist durch und wird gemeldet");
        assertEquals(500L, kasse.guthaben(ANNA));
        assertTrue(protokollNennt("ist GEZAHLT"), protokollzeilen.toString());
        assertTrue(protokollzeilen.stream().anyMatch(satz ->
                        satz.getLevel().intValue()
                                >= java.util.logging.Level.SEVERE.intValue()),
                "ein verlorener Serientag gehoert in die lauteste Stufe: "
                        + protokollzeilen);
    }

    @Test
    @DisplayName("Steht die Serienzeile VOR dem Buchungsbuch, wird nichts gezahlt und geklagt")
    void zeileVorDemBuch() {
        // Erreichbar, wenn eine Buchung von heute geloescht wurde oder die Tagesgrenze
        // zurueckgestellt ist. Beides muss nachlesbar sein, denn beides sieht fuer den
        // Spieler aus wie ein fehlender Bonus.
        ablage.schreibe(new Bonusstand(ANNA, TAG.plusDays(1), 3, 3));

        assertNull(bonus().pruefe(ANNA));
        assertEquals(0L, kasse.guthaben(ANNA));
        assertTrue(protokollNennt("Tagesgrenze"), protokollzeilen.toString());
    }

    @Test
    @DisplayName("Gebucht wird unter dem Grund TAGESBONUS - daran haengt der Waechter")
    void grundIstTagesbonus() {
        bonus().pruefe(ANNA);

        assertEquals(500L, kasse.summeSeit(ANNA, START.minusSeconds(60),
                Buchungsgrund.TAGESBONUS));
        // Und NICHT unter einem anderen Grund: Der Verdienstdeckel zaehlt
        // BERUF_VERDIENST, der Markt BUNDESMARKT_VERKAUF. Laege der Bonus dort, zaehlte
        // er gegen eine Arbeitsgrenze, die er nicht betrifft.
        assertEquals(0L, kasse.summeSeit(ANNA, START.minusSeconds(60),
                Buchungsgrund.BERUF_VERDIENST));
        assertEquals(0L, kasse.summeSeit(ANNA, START.minusSeconds(60),
                Buchungsgrund.BUNDESMARKT_VERKAUF));
    }

    @Test
    @DisplayName("Ohne Spieler geschieht nichts")
    void ohneSpieler() {
        assertNull(bonus().pruefe(null));
        assertNull(bonus().standZeile(null, Sprachprobe.spracheAusDemJar()));
    }

    @Test
    @DisplayName("Die Standzeile zeigt die Serie - und veraendert nichts")
    void standzeileVeraendertNichts() {
        Tagesbonus bonus = bonus();
        bonus.pruefe(ANNA);
        Sprache sprache = Sprachprobe.spracheAusDemJar();

        String erste = Sprachprobe.bild(bonus.standZeile(ANNA, sprache));

        assertTrue(erste.contains("1"), erste);
        // Zweimal gefragt: Ein Blick in den eigenen Stand darf keine Tat sein.
        assertEquals(erste, Sprachprobe.bild(bonus.standZeile(ANNA, sprache)));
        assertEquals(500L, kasse.guthaben(ANNA), "und er hat nichts dazubekommen");
    }

    @Test
    @DisplayName("Nach einer gerissenen Serie nennt die Standzeile Tag 1")
    void standzeileNachDemRiss() {
        // Die Serie steht auf 12, der letzte Tag liegt neun Tage zurueck. Heute brachte
        // den GRUNDBETRAG und nicht den dreizehnten Tag - und die Zeile muss das sagen.
        // Darum kommt die Serie aus Serienregel und nicht aus dem Stand.
        ablage.schreibe(new Bonusstand(ANNA, TAG.minusDays(9), 12, 12));

        String zeile = Sprachprobe.bild(
                bonus().standZeile(ANNA, Sprachprobe.spracheAusDemJar()));

        assertTrue(zeile.contains("500"),
                "heute bringt den Grundbetrag: " + zeile);
        assertFalse(zeile.contains("1.700"),
                "und nicht den Betrag von Tag 13: " + zeile);
    }

    @Test
    @DisplayName("Antwortet die Datenbank nicht, schweigt die Standzeile statt abzubrechen")
    void standzeileSchweigtBeiFehler() {
        Bonusablage tot = new Bonusablage() {
            @Override
            public Bonusstand lies(UUID spieler) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public void schreibe(Bonusstand stand) {
            }
        };

        assertNull(bonus(tot).standZeile(ANNA, Sprachprobe.spracheAusDemJar()),
                "gefragt war nach dem Guthaben - eine Absage waere hier zu viel");
        assertTrue(protokollNennt("nicht anzeigbar"), protokollzeilen.toString());
    }
}
