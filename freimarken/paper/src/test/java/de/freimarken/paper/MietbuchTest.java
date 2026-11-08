package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Mietwerte;
import de.freimarken.kern.Quader;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherMiete;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Ladenmiete OHNE Server - moeglich, weil {@link Mietbuch} kein Bukkit kennt.
 *
 * <p>Geprueft wird vor allem die tragende Zusage dieses Teils: <b>Keine Reihenfolge von
 * Fehlschlaegen erzeugt Geld.</b> Darum stehen hier Proben, in denen die Ablage mitten in
 * einer Tat versagt - beim Mieten nach dem Abbuchen, beim Kuendigen nach dem Loeschen -
 * und in jeder davon wird nachgerechnet, wo das Geld geblieben ist.
 *
 * <p>Die zweite Zusage: <b>Das Buchungsbuch ist der Beleg, dass die Lizenz bezahlt
 * ist.</b> Es gibt keine Spalte dafuer, also muss eine Probe dasselbe sehen, was das
 * Programm sieht - und sie zaehlt die Buchungen.
 *
 * <p>Alle Uhren sind DIESELBE: Die Kasse bucht mit ihrer Uhr, das Mietbuch rechnet mit
 * ihr den Tag. Zwei Uhren waeren an einer Tagesgrenze zwei verschiedene Tage.
 */
@DisplayName("Die Ladenmiete")
class MietbuchTest {

    private static final UUID ANNA =
            UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final UUID BERT =
            UUID.fromString("0000be27-0000-0000-0000-000000000002");

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    private static final Instant START = Instant.parse("2026-11-07T12:00:00Z");

    private static final LocalDate TAG = LocalDate.of(2026, 11, 7);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpielzeitbuchTest.StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private Ladenverzeichnis verzeichnis;
    private SpeicherMiete ablage;
    private SpeicherKasse kasse;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new SpielzeitbuchTest.StellUhr(START);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        verzeichnis = Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 60), einheit("halle-2", 70)));
        ablage = new SpeicherMiete();
        // Reichlich Startguthaben: Geprueft wird, was die MIETE kostet, nicht wer sie
        // sich leisten kann. Die Armut bekommt ihre eigenen Proben.
        kasse = new SpeicherKasse(1_000_000L, uhr);
        kasse.eroeffne(ANNA);
        kasse.eroeffne(BERT);
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    private static Ladeneinheit einheit(String name, int x) {
        return new Ladeneinheit(name, new Quader(x, 71, 4, x + 4, 75, 8), x + 2, 71, 6);
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("MietbuchProbe-" + UUID.randomUUID());
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

    private Mietbuch buch() {
        return new Mietbuch(wirtschaft, verzeichnis, ablage, kasse, uhr, protokoll);
    }

    private Mietbuch buch(Mietablage andere) {
        return new Mietbuch(wirtschaft, verzeichnis, andere, kasse, uhr, protokoll);
    }

    private void stelleWerte(Mietwerte werte) {
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitLaeden(werte));
    }

    private long summe(UUID wer, Buchungsgrund grund) {
        return kasse.summeSeit(wer, Instant.EPOCH, grund);
    }

    private long zeilen(UUID wer, Buchungsgrund grund) {
        return kasse.verlauf(wer, 100).stream()
                .filter(zeile -> zeile.grund() == grund)
                .count();
    }

    private boolean protokollNennt(Level stufe, String stueck) {
        return protokollzeilen.stream().anyMatch(satz -> satz.getLevel() == stufe
                && satz.getMessage().contains(stueck));
    }

    // ------------------------------------------------------------------- mieten

    @Test
    @DisplayName("Der erste Laden kostet Kaution UND Lizenz")
    void ersterLadenKostetBeides() {
        long vorher = kasse.guthaben(ANNA);

        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "halle-1");

        assertEquals(Mietbuch.Bescheid.GEMIETET, abschluss.bescheid());
        assertTrue(abschluss.lizenz(), "der erste Laden zahlt die Lizenz");
        assertEquals(55_000L, abschluss.kosten());
        assertEquals(vorher - 55_000L, kasse.guthaben(ANNA));
        // ZWEI Buchungen und nicht eine Summe: Der Wirtschaftsbericht aus Plan 12b muss
        // Lizenz und Kaution trennen koennen - sie sind zwei verschiedene Senken.
        //
        // NEGATIV, und das wird hier mit Absicht festgehalten: Eine Ausgabe steht im
        // Buchungsbuch als -betrag. Darauf beruht, dass der Lizenzzeuge in Mietbuch auf
        // GLEICH NULL fragt und nicht auf ein Vorzeichen - mit einem "<= 0" wurde in der
        // ersten Probenrunde jede zweite Lizenz erneut verlangt.
        assertEquals(-50_000L, summe(ANNA, Buchungsgrund.LADEN_LIZENZ));
        assertEquals(-5_000L, summe(ANNA, Buchungsgrund.LADEN_KAUTION));
    }

    @Test
    @DisplayName("Der Vertrag traegt Miete und Kaution, wie sie JETZT gelten")
    void vertragFriertDieZahlenEin() {
        Mietvertrag vertrag = buch().mieten(ANNA, "halle-1").vertrag();

        assertNotNull(vertrag);
        assertEquals("halle-1", vertrag.einheit());
        assertEquals(ANNA, vertrag.mieter());
        assertEquals(TAG, vertrag.beginn());
        // 30 Tage Dauer, laeuftBis EINGESCHLOSSEN - also 29 Tage nach heute.
        assertEquals(TAG.plusDays(29), vertrag.laeuftBis());
        assertEquals(500L, vertrag.miete());
        assertEquals(5_000L, vertrag.kaution());
    }

    @Test
    @DisplayName("Der ZWEITE Laden zahlt die Lizenz NICHT noch einmal")
    void lizenzNurEinmal() {
        // Die tragende Zusage: Das Buchungsbuch ist der Beleg. Anna mietet, kuendigt und
        // mietet wieder - 50.000 Mark duerfen nur EINMAL geflossen sein.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        buch.kuendigen(ANNA);

        Mietbuch.Abschluss zweiter = buch.mieten(ANNA, "halle-2");

        assertEquals(Mietbuch.Bescheid.GEMIETET, zweiter.bescheid());
        assertFalse(zweiter.lizenz(), "die Lizenz ist schon bezahlt");
        assertEquals(5_000L, zweiter.kosten(), "nur die Kaution");
        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_LIZENZ),
                "GENAU EINE Lizenzbuchung - zwei waeren 50.000 Mark, die niemand"
                        + " zurueckbekommt, weil niemand es merkt");
    }

    @Test
    @DisplayName("Ohne Lizenzgebuehr wird gar keine Lizenz gebucht")
    void ohneLizenzKeineBuchung() {
        // Eine 0 heisst "wer das Recht hat, darf mieten". Eine Buchung ueber 0 Mark waere
        // eine Zeile im Verlauf, die nichts bedeutet - und sie waere der Beleg dafuer,
        // dass die Lizenz bezahlt ist, obwohl nie Geld geflossen ist.
        stelleWerte(Mietwerte.VORGABE.mitLizenz(0L));

        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "halle-1");

        assertEquals(Mietbuch.Bescheid.GEMIETET, abschluss.bescheid());
        assertFalse(abschluss.lizenz());
        assertEquals(5_000L, abschluss.kosten());
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_LIZENZ));
    }

    @Test
    @DisplayName("Ohne Kaution wird gar keine Kaution gebucht")
    void ohneKautionKeineBuchung() {
        stelleWerte(Mietwerte.VORGABE.mitKaution(0L));

        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "halle-1");

        assertEquals(50_000L, abschluss.kosten(), "nur die Lizenz");
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION));
        assertEquals(0L, abschluss.vertrag().kaution(),
                "und der Vertrag haelt fest, dass keine hinterlegt wurde");
    }

    @Test
    @DisplayName("Einen Stand, den es nicht gibt, kann niemand mieten")
    void keineEinheit() {
        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "halle-9");

        assertEquals(Mietbuch.Bescheid.KEINE_EINHEIT, abschluss.bescheid());
        assertEquals(0L, abschluss.kosten());
        assertEquals(1_000_000L, kasse.guthaben(ANNA), "kein Geld bewegt");
        assertTrue(ablage.alle().isEmpty());
    }

    @Test
    @DisplayName("Gross geschrieben ist derselbe Stand")
    void grossGeschriebenIstDerselbe() {
        // Der Name kommt aus einem Chatfenster, und dort tippt ein Mensch. Ohne das waere
        // "Halle-1" ein Stand, den es nicht gibt.
        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "  HALLE-1 ");

        assertEquals(Mietbuch.Bescheid.GEMIETET, abschluss.bescheid());
        assertEquals("halle-1", abschluss.vertrag().einheit());
    }

    @Test
    @DisplayName("Ein vermieteter Stand wird nicht ein zweites Mal vergeben")
    void schonVermietet() {
        buch().mieten(ANNA, "halle-1");

        Mietbuch.Abschluss abschluss = buch().mieten(BERT, "halle-1");

        assertEquals(Mietbuch.Bescheid.SCHON_VERMIETET, abschluss.bescheid());
        assertEquals(ANNA, ablage.fuerEinheit("halle-1").mieter(),
                "Annas Vertrag ist unberuehrt");
        assertEquals(1_000_000L, kasse.guthaben(BERT), "und Bert hat nichts gezahlt");
    }

    @Test
    @DisplayName("Ein Spieler bekommt keinen ZWEITEN Laden - und die Absage nennt seinen")
    void schonEinLaden() {
        // Plan 7.1: ein Laden je Spieler. Der vorhandene Vertrag muss mitkommen, sonst
        // sucht der Spieler, welchen Laden er eigentlich hat.
        buch().mieten(ANNA, "halle-1");

        Mietbuch.Abschluss abschluss = buch().mieten(ANNA, "halle-2");

        assertEquals(Mietbuch.Bescheid.SCHON_EIN_LADEN, abschluss.bescheid());
        assertEquals("halle-1", abschluss.vertrag().einheit());
        assertNull(ablage.fuerEinheit("halle-2"));
        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION),
                "und die Kaution wurde nicht ein zweites Mal genommen");
    }

    @Test
    @DisplayName("Wer zu wenig hat, zahlt NICHTS - auch nicht die Lizenz")
    void zuWenigGeld() {
        // Die Reihenfolge ist die Sache: Geprueft wird gegen die SUMME, bevor irgendetwas
        // gebucht wird. Eine Fassung, die erst die Lizenz abbucht und dann an der Kaution
        // scheitert, haette ihm 50.000 Mark fuer nichts genommen.
        SpeicherKasse arm = new SpeicherKasse(54_999L, uhr);
        arm.eroeffne(ANNA);
        Mietbuch buch = new Mietbuch(wirtschaft, verzeichnis, ablage, arm, uhr, protokoll);

        Mietbuch.Abschluss abschluss = buch.mieten(ANNA, "halle-1");

        assertEquals(Mietbuch.Bescheid.ZU_WENIG_GELD, abschluss.bescheid());
        assertEquals(55_000L, abschluss.kosten(), "was es gekostet HAETTE");
        assertEquals(54_999L, abschluss.guthaben());
        assertEquals(54_999L, arm.guthaben(ANNA), "kein Pfennig bewegt");
        assertEquals(0L, arm.summeSeit(ANNA, Instant.EPOCH, Buchungsgrund.LADEN_LIZENZ));
        assertTrue(ablage.alle().isEmpty());
    }

    @Test
    @DisplayName("Genau das Guthaben reicht - der Rand")
    void genauGenug() {
        SpeicherKasse knapp = new SpeicherKasse(55_000L, uhr);
        knapp.eroeffne(ANNA);
        Mietbuch buch = new Mietbuch(wirtschaft, verzeichnis, ablage, knapp, uhr,
                protokoll);

        assertEquals(Mietbuch.Bescheid.GEMIETET, buch.mieten(ANNA, "halle-1").bescheid());
        assertEquals(0L, knapp.guthaben(ANNA));
    }

    @Test
    @DisplayName("Scheitert der Vertrag NACH dem Abbuchen, kommt das Geld zurueck")
    void erstattungNachFehlschlag() {
        // Die tragende Zusage, von der falschen Seite: Erst das Geld, dann der Vertrag -
        // und wenn der Vertrag nicht zustande kommt, muss das Geld zurueck. Sonst hat ein
        // Spieler 55.000 Mark fuer nichts gezahlt.
        Mietbuch.Abschluss abschluss = buch(new NichtSchreibbar(new SpeicherMiete()))
                .mieten(ANNA, "halle-1");

        assertEquals(Mietbuch.Bescheid.GESTOERT, abschluss.bescheid());
        // NUR die Kaution. Die Lizenz mit zu erstatten war der zweite Befund der ersten
        // Probenrunde: Sie stand danach als bezahlt im Buch, ohne dass Geld geblieben
        // waere - der naechste Laden desselben Spielers waere gratis gewesen. Und es ist
        // auch sachlich richtig: Die Kaution haengt an dem Vertrag, der nicht zustande
        // kam; die Lizenz haengt an keinem Laden.
        assertEquals(5_000L, summe(ANNA, Buchungsgrund.LADEN_ERSTATTET));
        assertEquals(1_000_000L - 50_000L, kasse.guthaben(ANNA),
                "die Kaution ist zurueck, die Lizenz bezahlt");
        assertTrue(protokollNennt(Level.WARNING, "halle-1"),
                "und es steht im Protokoll: " + protokollzeilen);
    }

    @Test
    @DisplayName("Die Erstattung geht als EINE Zeile, nicht als zwei")
    void erstattungIstEineZeile() {
        // Zwei Zeilen im Verlauf fuer einen gescheiterten Abschluss waeren fuer den
        // Spieler nicht lesbar - er sieht nur, dass irgendetwas zweimal passiert ist.
        buch(new NichtSchreibbar(new SpeicherMiete())).mieten(ANNA, "halle-1");

        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_ERSTATTET));
    }

    @Test
    @DisplayName("Nach einem gescheiterten Abschluss kostet der naechste nur die Kaution")
    void nachFehlschlagNurNochKaution() {
        // Die Gegenprobe dazu, dass die Lizenz NICHT erstattet wird: Sie ist bezahlt und
        // bleibt es. Eine Fassung, die sie mit zurueckgibt, waere bei der Probe darueber
        // gruen und schenkte hier 50.000 Mark - der zweite Versuch verlangte sie dann
        // nicht mehr, obwohl das Geld zurueckgeflossen ist.
        buch(new NichtSchreibbar(new SpeicherMiete())).mieten(ANNA, "halle-1");

        Mietbuch.Abschluss zweiter = buch().mieten(ANNA, "halle-1");

        assertEquals(Mietbuch.Bescheid.GEMIETET, zweiter.bescheid());
        assertFalse(zweiter.lizenz(), "die Lizenz ist bezahlt und bleibt es");
        assertEquals(5_000L, zweiter.kosten());
        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_LIZENZ),
                "GENAU EINE Lizenzbuchung, auch ueber einen Fehlschlag hinweg");
    }

    // -------------------------------------------------------------- verlaengern

    @Test
    @DisplayName("Verlaengern gegen Ende haengt eine Mietdauer an - und kostet nichts")
    void verlaengernKostetNichts() {
        // Gezahlt wird TAEGLICH (Plan 7.1, Teil C). Eine Gebuehr auf das Verlaengern waere
        // eine zweite Miete fuer dieselben Tage.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        long nachDemMieten = kasse.guthaben(ANNA);
        // Einen Tag vor dem Ende - dann ist die Restlaufzeit 0 und kleiner als 30.
        uhr.weiter(Duration.ofDays(29));

        Mietbuch.Verlaengerung ergebnis = buch.verlaengern(ANNA);

        assertEquals(Mietbuch.Bescheid.VERLAENGERT, ergebnis.bescheid());
        assertEquals(TAG.plusDays(59), ergebnis.vertrag().laeuftBis());
        assertEquals(30L, ergebnis.restTage());
        assertEquals(nachDemMieten, kasse.guthaben(ANNA), "kostet nichts");
    }

    @Test
    @DisplayName("Verlaengern laesst Miete UND Kaution stehen - der Bestandsschutz")
    void verlaengernHaeltDieZahlen() {
        // Plan 1943: anpassbar mit Bestandsschutz, gilt nur fuer NEUE Vertraege. Hier
        // wird die Datei MITTENDRIN geaendert - genau der Fall, den ein Neuladen macht.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        uhr.weiter(Duration.ofDays(29));
                // Erst die Kaution, dann die Miete: mitMiete(9000) ALLEIN ergaebe Kaution
        // 5000 unter Miete 9000, und das verwirft die Paarregel schon im Wither.
        stelleWerte(Mietwerte.VORGABE.mitKaution(90_000L).mitMiete(9_000L));

        Mietbuch.Verlaengerung ergebnis = buch.verlaengern(ANNA);

        assertEquals(500L, ergebnis.vertrag().miete(),
                "die VEREINBARTE Miete, nicht die neue aus der config.yml");
        assertEquals(5_000L, ergebnis.vertrag().kaution());
        assertEquals(5_000L, ablage.fuerEinheit("halle-1").kaution(),
                "und so steht es auch in der Ablage");
    }

    @Test
    @DisplayName("Verlaengern geht sofort - die Zusage ist der DECKEL, keine Wartezeit")
    void verlaengernGehtSofort() {
        // Ein frischer Vertrag hat 29 Resttage, und 29 ist kleiner als die Mietdauer 30:
        // also erlaubt. Das ist gewollt und war der dritte Befund der ersten Probenrunde
        // - die Zusage der Regel ist nicht "warte erst", sondern "das Ende liegt nie
        // weiter als zwei Mietdauern voraus". Die naechste Probe haelt den Deckel.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");

        Mietbuch.Verlaengerung ergebnis = buch.verlaengern(ANNA);

        assertEquals(Mietbuch.Bescheid.VERLAENGERT, ergebnis.bescheid());
        assertEquals(TAG.plusDays(59), ergebnis.vertrag().laeuftBis());
    }

    @Test
    @DisplayName("Der DRITTE Versuch wird abgewiesen - zwei Mietdauern sind der Deckel")
    void nochZuFrueh() {
        // Der Deckel, vom Spieler aus gesehen: Nach einer Verlaengerung liegen 59 Tage
        // vor ihm, und 59 ist nicht kleiner als 30. Ohne diese Schranke koennte ein
        // reicher Spieler dreissigmal verlaengern und einen Stand fuer Jahre binden.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        buch.verlaengern(ANNA);

        Mietbuch.Verlaengerung ergebnis = buch.verlaengern(ANNA);

        assertEquals(Mietbuch.Bescheid.NOCH_ZU_FRUEH, ergebnis.bescheid());
        assertEquals(59L, ergebnis.restTage());
        assertEquals("halle-1", ergebnis.vertrag().einheit());
        assertEquals(TAG.plusDays(59), ablage.fuerEinheit("halle-1").laeuftBis(),
                "der Vertrag ist unveraendert");
    }

    @Test
    @DisplayName("Ohne Laden gibt es nichts zu verlaengern")
    void verlaengernOhneLaden() {
        Mietbuch.Verlaengerung ergebnis = buch().verlaengern(ANNA);

        assertEquals(Mietbuch.Bescheid.KEIN_LADEN, ergebnis.bescheid());
        assertNull(ergebnis.vertrag());
    }

    @Test
    @DisplayName("Einen ABGELAUFENEN Vertrag rettet das Verlaengern - ohne Freitage")
    void abgelaufenWirdGerettet() {
        // Zwischen dem Ablauf und dem taeglichen Lauf aus Teil C steht die Zeile noch da.
        // Wer in diesem Fenster verlaengert, rettet seinen Laden - bekommt die
        // verstrichenen Tage aber NICHT geschenkt.
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        uhr.weiter(Duration.ofDays(100));

        Mietbuch.Verlaengerung ergebnis = buch.verlaengern(ANNA);

        assertEquals(Mietbuch.Bescheid.VERLAENGERT, ergebnis.bescheid());
        assertEquals(TAG.plusDays(130), ergebnis.vertrag().laeuftBis(),
                "gerechnet ab HEUTE und nicht ab dem alten Ende");
        assertEquals(30L, ergebnis.restTage());
    }

    // ---------------------------------------------------------------- kuendigen

    @Test
    @DisplayName("Kuendigen gibt die HINTERLEGTE Kaution zurueck")
    void kuendigenGibtDieKautionZurueck() {
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");
        // Die Datei wird geaendert - zurueck muss trotzdem das Hinterlegte.
        stelleWerte(Mietwerte.VORGABE.mitKaution(90_000L));

        Mietbuch.Kuendigung ergebnis = buch.kuendigen(ANNA);

        assertEquals(Mietbuch.Bescheid.GEKUENDIGT, ergebnis.bescheid());
        assertEquals("halle-1", ergebnis.einheit());
        assertEquals(5_000L, ergebnis.kautionZurueck(),
                "die HINTERLEGTE und nicht die aus der config.yml");
        assertEquals(5_000L, summe(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK));
        assertNull(ablage.fuerEinheit("halle-1"), "und der Stand ist frei");
    }

    @Test
    @DisplayName("Die Lizenz kommt NICHT zurueck - sie ist einmalig")
    void lizenzKommtNichtZurueck() {
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");

        buch.kuendigen(ANNA);

        assertEquals(-50_000L, summe(ANNA, Buchungsgrund.LADEN_LIZENZ));
        assertEquals(0L, summe(ANNA, Buchungsgrund.LADEN_ERSTATTET));
        assertEquals(1_000_000L - 50_000L, kasse.guthaben(ANNA),
                "Kaution zurueck, Lizenz behalten");
    }

    @Test
    @DisplayName("Ohne hinterlegte Kaution wird gar nichts gutgeschrieben")
    void ohneKautionNichtsZurueck() {
        stelleWerte(Mietwerte.VORGABE.mitKaution(0L));
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");

        Mietbuch.Kuendigung ergebnis = buch.kuendigen(ANNA);

        assertEquals(Mietbuch.Bescheid.GEKUENDIGT, ergebnis.bescheid());
        assertEquals(0L, ergebnis.kautionZurueck());
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK),
                "eine Buchung ueber 0 Mark waere eine Zeile ohne Inhalt");
    }

    @Test
    @DisplayName("Ohne Laden gibt es nichts zu kuendigen")
    void kuendigenOhneLaden() {
        Mietbuch.Kuendigung ergebnis = buch().kuendigen(ANNA);

        assertEquals(Mietbuch.Bescheid.KEIN_LADEN, ergebnis.bescheid());
        assertNull(ergebnis.einheit());
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK));
    }

    @Test
    @DisplayName("Scheitert das Loeschen, wird KEINE Kaution gutgeschrieben")
    void loeschenScheitertOhneGeld() {
        // Die Reihenfolge beim Kuendigen: erst loeschen, dann gutschreiben. Eine Fassung,
        // die erst gutschreibt, haette nach diesem Fehlschlag das Geld UND den Laden -
        // und der Spieler koennte es wiederholen.
        SpeicherMiete echt = new SpeicherMiete();
        echt.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L));
        Mietbuch.Kuendigung ergebnis = buch(new NichtLoeschbar(echt)).kuendigen(ANNA);

        assertEquals(Mietbuch.Bescheid.GESTOERT, ergebnis.bescheid());
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK),
                "kein Geld, solange der Laden noch steht");
        assertNotNull(echt.fuerEinheit("halle-1"), "und der Vertrag steht noch");
    }

    @Test
    @DisplayName("Scheitert die Gutschrift NACH dem Loeschen, schreit das Protokoll")
    void gutschriftScheitertLaut() {
        // Der Fall, der von Hand zu heilen ist: Der Laden ist weg und das Geld nicht da.
        // Gemeldet wird trotzdem GEKUENDIGT - gekuendigt IST, und eine Absage liesse den
        // Spieler glauben, er habe noch einen Laden.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L));
        Mietbuch buch = new Mietbuch(wirtschaft, verzeichnis, ablage,
                new StolperndeKasse(kasse), uhr, protokoll);

        Mietbuch.Kuendigung ergebnis = buch.kuendigen(ANNA);

        assertEquals(Mietbuch.Bescheid.GEKUENDIGT, ergebnis.bescheid());
        assertEquals(0L, ergebnis.kautionZurueck(),
                "die Zeile darf nicht melden, was nicht geflossen ist");
        assertNull(ablage.fuerEinheit("halle-1"));
        // SEVERE und nicht WARNING: Niemand merkt es, wenn es nicht auffaellt.
        assertTrue(protokollNennt(Level.SEVERE, "Von Hand gutzuschreiben"),
                protokollzeilen.toString());
    }

    // ------------------------------------------------------------------ Auskunft

    @Test
    @DisplayName("vertragVon und restTage antworten fuer die Liste")
    void auskunft() {
        Mietbuch buch = buch();
        buch.mieten(ANNA, "halle-1");

        Mietvertrag vertrag = buch.vertragVon(ANNA);

        assertNotNull(vertrag);
        assertEquals(29L, buch.restTage(vertrag));
        assertNull(buch.vertragVon(BERT));
        assertNull(buch.vertragVon(null));
    }

    @Test
    @DisplayName("Antwortet die Ablage nicht, ist vertragVon null und es steht im Protokoll")
    void auskunftBeiStoerung() {
        // Die Liste soll nicht abbrechen, weil die Datenbank schweigt. Was wirklich war,
        // muss aber nachlesbar sein - sonst sucht der Betreiber nach einem Laden, der
        // laut Anzeige nicht da ist.
        assertNull(buch(new StolpernderMiete()).vertragVon(ANNA));

        assertTrue(protokollNennt(Level.WARNING, "nicht lesbar"),
                protokollzeilen.toString());
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Namen antwortet jede Tat mit einer Absage")
    void pflichtangaben() {
        // Geworfen wird nie: Gerufen wird aus einem Befehl, und eine Ausnahme dort waere
        // fuer den Spieler eine rote Zeile ohne Sinn.
        Mietbuch buch = buch();

        assertEquals(Mietbuch.Bescheid.KEINE_EINHEIT,
                buch.mieten(null, "halle-1").bescheid());
        assertEquals(Mietbuch.Bescheid.KEINE_EINHEIT, buch.mieten(ANNA, null).bescheid());
        assertEquals(Mietbuch.Bescheid.KEINE_EINHEIT, buch.mieten(ANNA, "  ").bescheid());
        assertEquals(Mietbuch.Bescheid.KEIN_LADEN, buch.verlaengern(null).bescheid());
        assertEquals(Mietbuch.Bescheid.KEIN_LADEN, buch.kuendigen(null).bescheid());
    }

    // ------------------------------------------------------------------ Helfer

    /** Eine Ablage, die beim Schreiben und Lesen versagt - wie eine stumme Datenbank. */
    private static final class StolpernderMiete implements Mietablage {
        @Override
        public Mietvertrag fuerEinheit(String einheit) {
            return null;
        }

        @Override
        public Mietvertrag fuerMieter(UUID mieter) {
            throw new IllegalStateException("Mietvertrag nicht lesbar (Probe)");
        }

        @Override
        public List<Mietvertrag> alle() {
            throw new IllegalStateException("Mietvertraege nicht lesbar (Probe)");
        }

        @Override
        public void schreibe(Mietvertrag vertrag) {
            throw new IllegalStateException("Mietvertrag nicht schreibbar (Probe)");
        }

        @Override
        public boolean loesche(String einheit) {
            throw new IllegalStateException("Mietvertrag nicht loeschbar (Probe)");
        }
    }

    /**
     * Eine Ablage, die LESEN kann und nur das Schreiben verweigert.
     *
     * <p>Dieser Zuschnitt ist der Fall, und das ist ein Befund: Mit einer Ablage, die
     * schon beim ersten Lesen wirft, wird nie Geld gebucht - dann hat die Erstattung
     * nichts zu tun, und die Probe ist gruen, ohne die Reihenfolge je erreicht zu haben.
     */
    private record NichtSchreibbar(Mietablage echte) implements Mietablage {
        @Override
        public Mietvertrag fuerEinheit(String einheit) {
            return echte.fuerEinheit(einheit);
        }

        @Override
        public Mietvertrag fuerMieter(UUID mieter) {
            return echte.fuerMieter(mieter);
        }

        @Override
        public List<Mietvertrag> alle() {
            return echte.alle();
        }

        @Override
        public void schreibe(Mietvertrag vertrag) {
            throw new IllegalStateException("Mietvertrag nicht schreibbar (Probe)");
        }

        @Override
        public boolean loesche(String einheit) {
            return echte.loesche(einheit);
        }
    }

    /**
     * Eine Ablage, die LESEN kann und nur das Loeschen verweigert.
     *
     * <p>Genau dieser Zuschnitt ist der Fall: Der Vertrag wird gefunden, und erst der
     * Loeschzug scheitert. Mit einer Ablage, die schon beim Lesen wirft, waere die Probe
     * gruen, ohne die Reihenfolge je erreicht zu haben.
     */
    private record NichtLoeschbar(Mietablage echte) implements Mietablage {
        @Override
        public Mietvertrag fuerEinheit(String einheit) {
            return echte.fuerEinheit(einheit);
        }

        @Override
        public Mietvertrag fuerMieter(UUID mieter) {
            return echte.fuerMieter(mieter);
        }

        @Override
        public List<Mietvertrag> alle() {
            return echte.alle();
        }

        @Override
        public void schreibe(Mietvertrag vertrag) {
            echte.schreibe(vertrag);
        }

        @Override
        public boolean loesche(String einheit) {
            throw new IllegalStateException("Mietvertrag nicht loeschbar (Probe)");
        }
    }

    /**
     * Eine Kasse, die alles kann, nur nicht gutschreiben.
     *
     * <p>Fuer den einen Fall, der von Hand zu heilen ist: Der Laden ist schon geloescht,
     * und die Kaution kommt nicht zurueck.
     */
    private record StolperndeKasse(de.freimarken.kern.Kasse echte)
            implements de.freimarken.kern.Kasse {
        @Override
        public long guthaben(UUID spieler) {
            return echte.guthaben(spieler);
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return echte.eroeffne(spieler);
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            return echte.abbuchen(spieler, betrag, grund);
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("Gutschrift nicht moeglich (Probe)");
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
}
