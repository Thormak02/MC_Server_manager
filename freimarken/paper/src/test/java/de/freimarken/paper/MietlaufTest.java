package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Abholstueck;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Mietwerte;
import de.freimarken.kern.SpeicherAbholfach;
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
 * Der taegliche Lauf der Ladenmiete - abbuchen, warnen, raeumen.
 *
 * <p>Ohne Server, wie {@code MietbuchTest}: Das Spiel kommt nur ueber die Attrappe
 * {@link Probentruhe} herein, und die zaehlt mit, WANN sie gerufen wurde. Genau darauf
 * laufen die Reihenfolgeproben - die Reihenfolge der Raeumung ist die einzige Zusage
 * dieses Teils, die keine Gegenstaende verdoppeln kann.
 *
 * <p>Die Mietdauer und die Ausfallschranke setzt diese Probe SELBST (30 Tage, 3
 * Ausfaelle) und nimmt sie nicht aus der ausgelieferten Vorgabe - dieselbe Lehre wie in
 * {@code MietbuchTest}: Haengen die Zahlen an der Datei, wird bei jeder Preisaenderung
 * ein halbes Dutzend Proben rot, ohne dass sich am Geprueften etwas geaendert hat.
 */
@DisplayName("Der taegliche Lauf der Ladenmiete")
class MietlaufTest {

    private static final UUID ANNA =
            UUID.fromString("0000a44a-0000-0000-0000-000000000001");

    private static final UUID BERT =
            UUID.fromString("0000be27-0000-0000-0000-000000000002");

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    private static final Instant START = Instant.parse("2026-11-08T12:00:00Z");

    private static final LocalDate TAG = LocalDate.of(2026, 11, 8);

    /** Drei Ausfaelle bis zur Raeumung, 30 Tage Dauer - von dieser Probe gesetzt. */
    private static final int SCHRANKE = 3;

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpielzeitbuchTest.StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private SpeicherMiete ablage;
    private SpeicherAbholfach fach;
    private SpeicherKasse kasse;
    private Probentruhe truhe;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new SpielzeitbuchTest.StellUhr(START);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitLaeden(Mietwerte.VORGABE.mitDauer(30).mitAusfaellen(SCHRANKE)));
        ablage = new SpeicherMiete();
        fach = new SpeicherAbholfach();
        kasse = new SpeicherKasse(1_000_000L, uhr);
        kasse.eroeffne(ANNA);
        kasse.eroeffne(BERT);
        truhe = new Probentruhe();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    // ==================================================================== Bezahlen

    @Test
    @DisplayName("Die Miete wird abgebucht - und zwar genau die VEREINBARTE")
    void mieteWirdAbgebucht() {
        // Die Miete aus der VERTRAGSZEILE und nicht die aus der config.yml: Das ist der
        // Bestandsschutz aus Plan 1943, und der taegliche Lauf ist die Stelle, an der er
        // jeden Tag greift. 777 steht in der Zeile, 500 in der Datei.
        ablage.schreibe(vertrag(ANNA, "halle-1", 777L, 5_000L));

        List<Mietlauf.Posten> posten = lauf().laufe(TAG);

        assertEquals(1, posten.size());
        assertEquals(Mietlauf.Ausgang.BEZAHLT, posten.get(0).ausgang());
        assertEquals(777L, posten.get(0).betrag());
        assertEquals(-777L, summe(ANNA, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Ein ZWEITER Lauf am selben Tag bucht NICHT noch einmal ab")
    void zweiterLaufZahltNichtDoppelt() {
        // Der Waechter ist das BUCHUNGSBUCH und nicht der Merker im Arbeitsspeicher. Die
        // Probe geht deshalb zweimal durch laufe() und nicht durch imTakt() - sie
        // umgeht den Merker absichtlich, denn genau das tut ein Neustart.
        //
        // Gezaehlt werden die ZEILEN und nicht die Summe: Eine Summe stimmt auch, wenn
        // zweimal die Haelfte gebucht wurde.
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        Mietlauf lauf = lauf();
        lauf.laufe(TAG);

        List<Mietlauf.Posten> zweiter = lauf.laufe(TAG);

        assertEquals(Mietlauf.Ausgang.SCHON_BEZAHLT, zweiter.get(0).ausgang());
        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_MIETE));
        assertEquals(-500L, summe(ANNA, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Ein Server, der drei Tage aus war, fordert NICHT drei Tage nach")
    void keineNachforderung() {
        // In diesen drei Tagen konnte niemand seinen Laden benutzen. Eine Nachforderung
        // traefe den Spieler, der nichts dafuer kann - samt der Ausfaelle, die ihn dann
        // raeumen. Gezaehlt wird EINE Zeile, nicht eine Summe.
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        uhr.weiter(Duration.ofDays(3));

        lauf().laufe(TAG.plusDays(3));

        assertEquals(1L, zeilen(ANNA, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Der Takt laeuft nur bei TAGESWECHSEL - nicht bei jedem Schlag")
    void taktLaeuftNurEinmalAmTag() {
        // Der Takt schlaegt jede Minute, und ein Lauf fragt je Vertrag zweimal die
        // Datenbank. Ohne diese Schranke waeren das bei 80 Laeden 160 Abfragen in der
        // Minute - fuer nichts. Gemessen an der LEEREN Liste: Sie ist die Zusage, dass
        // gar nicht erst gelesen wurde.
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        Mietlauf lauf = lauf();

        assertEquals(1, lauf.imTakt().size(), "der erste Schlag laeuft");
        assertTrue(lauf.imTakt().isEmpty(), "der zweite am selben Tag nicht");
        assertTrue(lauf.imTakt().isEmpty());

        uhr.weiter(Duration.ofDays(1));
        assertEquals(1, lauf.imTakt().size(), "am naechsten Tag wieder");
    }

    @Test
    @DisplayName("Wer zahlt, ist seine Ausfaelle los")
    void zahlenLoeschtDieAusfaelle() {
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, 2));

        Mietlauf.Posten posten = lauf().laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.BEZAHLT, posten.ausgang());
        assertEquals(0, posten.ausfaelle());
        assertEquals(0, ablage.fuerEinheit("halle-1").ausfaelle(),
                "und die Zeile ist auch WIRKLICH geschrieben");
        assertFalse(ablage.fuerEinheit("halle-1").inVerzug());
    }

    @Test
    @DisplayName("Ein Tag, an dem alle zahlen, schreibt KEINE Zeile neu")
    void ohneVerzugKeinSchreibzug() {
        // Der haeufigste Tag. ohneAusfaelle() gibt dasselbe Stueck zurueck, wenn keine
        // Ausfaelle da sind, und darauf baut der Lauf: Sonst schriebe er bei 80 Laeden
        // jeden Tag 80 Zeilen, die sich nicht geaendert haben.
        Mietvertrag vorher = vertrag(ANNA, "halle-1", 500L, 5_000L);
        ablage.schreibe(vorher);
        Mietablage zaehlend = new ZaehlendeAblage(ablage);

        new Mietlauf(wirtschaft, zaehlend, fach, kasse, truhe, uhr, protokoll)
                .laufe(TAG);

        assertEquals(0, ((ZaehlendeAblage) zaehlend).schreibzuege,
                "kein Schreibzug, wenn sich nichts geaendert hat");
    }

    // ==================================================================== Warnen

    @Test
    @DisplayName("Fehlt das Geld, wird gewarnt - der Laden bleibt")
    void ohneGeldWirdGewarnt() {
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));

        Mietlauf.Posten posten = new Mietlauf(wirtschaft, ablage, fach, arm, truhe, uhr,
                protokoll).laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.GEWARNT, posten.ausgang());
        assertEquals(500L, posten.betrag(), "was gefehlt haette");
        assertEquals(1, posten.ausfaelle());
        assertEquals(2, posten.bisRaeumung(), "noch zwei, dann wird geraeumt");
        assertNotNull(ablage.fuerEinheit("halle-1"), "der Laden bleibt");
        assertEquals(1, ablage.fuerEinheit("halle-1").ausfaelle());
        // KEINE Buchung - der Versuch ist gescheitert, nicht halb gelungen.
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Die Ausfaelle zaehlen ueber Tage HOCH")
    void ausfaelleZaehlenHoch() {
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        Mietlauf lauf = new Mietlauf(wirtschaft, ablage, fach, arm, truhe, uhr,
                protokoll);

        assertEquals(1, lauf.laufe(TAG).get(0).ausfaelle());
        assertEquals(2, lauf.laufe(TAG.plusDays(1)).get(0).ausfaelle());
        // Der DRITTE raeumt - bei einer Schranke von 3.
        assertEquals(Mietlauf.Ausgang.GERAEUMT,
                lauf.laufe(TAG.plusDays(2)).get(0).ausgang());
    }

    // ==================================================================== Raeumen

    @Test
    @DisplayName("Beim Raeumen verfaellt die Kaution - und die Ware ist im Fach")
    void raeumenLaesstDieKautionVerfallen() {
        // Der ganze Zweck der Kaution (Plan 7.1: zurueck nur "bei ordentlicher
        // Rueckgabe"): Sie macht das Weglaufen teurer als das Kuendigen. Geprueft wird
        // auf die ZEILENZAHL im Buch und nicht auf die Summe - eine Gutschrift ueber 0
        // waere auch mit einer Summe von 0 vereinbar.
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, SCHRANKE - 1));
        truhe.lege("halle-1", "64x STONE", "12x OAK_LOG");

        Mietlauf.Posten posten = new Mietlauf(wirtschaft, ablage, fach, arm, truhe, uhr,
                protokoll).laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.GERAEUMT, posten.ausgang());
        assertEquals(0L, posten.kautionZurueck());
        assertEquals(0L, zaehleBuchungen(arm, ANNA,
                Buchungsgrund.LADEN_KAUTION_ZURUECK), "keine Gutschrift");
        assertNull(ablage.fuerEinheit("halle-1"), "der Laden ist frei");
        assertEquals(2, posten.insFach());
        assertEquals(List.of("64x STONE", "12x OAK_LOG"), imFach(ANNA));
    }

    @Test
    @DisplayName("Die Truhe wird geleert - sonst findet der naechste Mieter alles doppelt")
    void truheWirdGeleert() {
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, SCHRANKE - 1));
        truhe.lege("halle-1", "64x STONE");

        new Mietlauf(wirtschaft, ablage, fach, arm, truhe, uhr, protokoll).laufe(TAG);

        assertTrue(truhe.inhalt("halle-1").isEmpty(), "die Truhe ist leer");
        assertEquals(1, imFach(ANNA).size(), "und die Ware liegt im Fach");
    }

    @Test
    @DisplayName("Die Reihenfolge: ins Fach, DANN loeschen, DANN leeren")
    void reihenfolgeDerRaeumung() {
        // Die einzige Zusage dieses Teils, die keine Gegenstaende verdoppeln kann - und
        // keine Zaehlung kann sie halten, sondern nur die beobachtete REIHENFOLGE.
        //
        // Die Attrappe schaut beim Einlegen nach, ob der Vertrag noch da ist: Waere er
        // beim Legen schon geloescht und das Legen scheiterte, waere der Laden frei und
        // die Ware weg. Und sie schaut beim Leeren nach, ob er schon weg ist - stand er
        // da noch, koennte ein Abbruch danach einen Vertrag mit leerer Truhe
        // hinterlassen.
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, SCHRANKE - 1));
        truhe.lege("halle-1", "64x STONE");
        List<String> beobachtet = new ArrayList<>();
        Abholablage beobachtend = new Abholablage() {
            @Override
            public List<Abholstueck> fuerSpieler(UUID empfaenger) {
                return fach.fuerSpieler(empfaenger);
            }

            @Override
            public int anzahlFuer(UUID empfaenger) {
                return fach.anzahlFuer(empfaenger);
            }

            @Override
            public void lege(UUID empfaenger, String herkunft, Instant wann,
                    List<Abholgut> stapel) {
                beobachtet.add("lege, Vertrag da: "
                        + (ablage.fuerEinheit("halle-1") != null));
                fach.lege(empfaenger, herkunft, wann, stapel);
            }

            @Override
            public int nimm(UUID empfaenger, List<Long> ids) {
                return fach.nimm(empfaenger, ids);
            }
        };
        truhe.merkeVertragslage(() -> ablage.fuerEinheit("halle-1") != null, beobachtet);

        new Mietlauf(wirtschaft, ablage, beobachtend, arm, truhe, uhr, protokoll)
                .laufe(TAG);

        assertEquals(List.of("lese, Vertrag da: true", "lege, Vertrag da: true",
                "leere, Vertrag da: false"), beobachtet);
    }

    @Test
    @DisplayName("Laesst sich das Fach nicht schreiben, bleibt ALLES, wie es war")
    void faellachSchlagFehlLaesstAllesStehen() {
        // Der Schritt, der dauerhaft wirkt, ist das Einlegen. Scheitert er, darf der
        // Vertrag nicht geloescht und die Truhe nicht geleert werden - sonst waere die
        // Ware weg und der Laden frei. Der naechste Lauf versucht es wieder, und genau
        // dafuer prueft raeumungsreif mit >= und nicht mit ==.
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, SCHRANKE - 1));
        truhe.lege("halle-1", "64x STONE");

        Mietlauf.Posten posten = new Mietlauf(wirtschaft, ablage, new NichtEinlegbar(),
                arm, truhe, uhr, protokoll).laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.GESTOERT, posten.ausgang());
        assertNotNull(ablage.fuerEinheit("halle-1"), "der Vertrag bleibt");
        assertEquals(List.of("64x STONE"), truhe.inhalt("halle-1"),
                "und die Truhe bleibt voll");
        assertTrue(hatZeile(Level.SEVERE, "Nichts ist verloren"), protokollzeilen.size()
                + " Zeilen: " + zeilenText());
    }

    @Test
    @DisplayName("Eine leere Truhe wird geraeumt wie jede andere")
    void leereTruheRaeumtDurch() {
        // Der haeufigste Fall: Ein verlassener Laden hat eine leere Truhe. Wuerde das
        // Einlegen einer leeren Liste werfen, bliebe genau dieser Laden fuer immer
        // stehen.
        SpeicherKasse arm = new SpeicherKasse(100L, uhr);
        arm.eroeffne(ANNA);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, SCHRANKE - 1));

        Mietlauf.Posten posten = new Mietlauf(wirtschaft, ablage, fach, arm, truhe, uhr,
                protokoll).laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.GERAEUMT, posten.ausgang());
        assertEquals(0, posten.insFach());
        assertNull(ablage.fuerEinheit("halle-1"));
    }

    // ==================================================================== Ablaufen

    @Test
    @DisplayName("Beim ABLAUF kommt die Kaution ZURUECK")
    void ablaufGibtDieKautionZurueck() {
        // Entschieden am 08.11.2026: Der Mieter hat jeden Tag gezahlt, und der Vertrag
        // endete so, wie er abgeschlossen war. Die Kaution ist das Pfand gegen das
        // Weglaufen, nicht gegen das Nicht-Verlaengern. Das ist der EINZIGE Unterschied
        // zur Raeumung - und darum die wichtigste Probe dieses Paares.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG.minusDays(30),
                TAG.minusDays(1), 500L, 5_000L));
        truhe.lege("halle-1", "64x STONE");

        Mietlauf.Posten posten = lauf().laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.ABGELAUFEN, posten.ausgang());
        assertEquals(5_000L, posten.kautionZurueck());
        assertEquals(5_000L, summe(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK));
        assertNull(ablage.fuerEinheit("halle-1"));
        assertEquals(1, posten.insFach());
    }

    @Test
    @DisplayName("Am Tag des Ablaufs wird KEINE Miete mehr abgebucht")
    void ablaufBuchtNichtMehrAb() {
        // Der Ablauf steht VOR der Abbuchung, und das ist der Unterschied zwischen "sein
        // Vertrag war zu Ende" und "er konnte nicht zahlen". Wer bis zum letzten Tag
        // gezahlt hat, soll nicht am Tag danach noch einen Ausfall bekommen.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG.minusDays(30),
                TAG.minusDays(1), 500L, 5_000L));

        lauf().laufe(TAG);

        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Am LETZTEN Tag des Vertrags wird noch gezahlt")
    void amLetztenTagWirdGezahlt() {
        // laeuftBis ist EINGESCHLOSSEN. Eine Fassung, die hier schon ablaufen laesst,
        // nimmt dem Betreiber einen bezahlten Tag - und dem Spieler einen Ladentag.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG.minusDays(29), TAG, 500L,
                5_000L));

        Mietlauf.Posten posten = lauf().laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.BEZAHLT, posten.ausgang());
        assertNotNull(ablage.fuerEinheit("halle-1"));
    }

    @Test
    @DisplayName("Ein abgelaufener Vertrag OHNE Kaution laeuft trotzdem ab")
    void ablaufOhneKaution() {
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG.minusDays(30),
                TAG.minusDays(1), 500L, 0L));

        Mietlauf.Posten posten = lauf().laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.ABGELAUFEN, posten.ausgang());
        assertEquals(0L, posten.kautionZurueck());
        assertEquals(0L, zeilen(ANNA, Buchungsgrund.LADEN_KAUTION_ZURUECK),
                "keine Gutschrift ueber 0");
    }

    // ==================================================================== Mehrere

    @Test
    @DisplayName("Eine GESENKTE Schranke macht die Restzahl nicht negativ")
    void gesenkteSchrankeGibtKeineNegativeRestzahl() {
        // Befund der Einspritzungskampagne vom 08.11.2026: Das Math.max(0, ...) in
        // uebrig() liess sich wegnehmen, ohne dass eine Probe rot wurde. Der Fall
        // entsteht, wenn der Betreiber laeden.ausfaelle-bis-raeumung im Betrieb SENKT -
        // dann stehen Vertraege da, deren Ausfallzahl die neue Schranke uebersteigt.
        //
        // Eine negative Zahl geht in die Warnung ein ("Ausfall 5 von 3"), und sie geht in
        // die Zeile beim Beitritt. Der Spieler liest dann eine Rechnung, die nicht
        // aufgeht.
        // Der Zustand wird DIREKT hergestellt und nicht erlaufen: Ein Lauf, der zahlt,
        // setzt die Ausfaelle auf 0, und damit waere der Fall weg. Real entsteht er, wenn
        // die Miete heute schon gebucht ist, der Vertrag aber noch seine alten Ausfaelle
        // traegt - etwa weil das Zurueckschreiben nach der Buchung gescheitert ist, oder
        // weil der Betreiber die Schranke gesenkt hat, nachdem der heutige Lauf durch
        // war.
        kasse.abbuchen(ANNA, 500L, Buchungsgrund.LADEN_MIETE);
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, 5));

        Mietlauf.Posten posten = lauf().laufe(TAG).get(0);

        assertEquals(Mietlauf.Ausgang.SCHON_BEZAHLT, posten.ausgang());
        assertEquals(0, posten.bisRaeumung(),
                "5 Ausfaelle bei Schranke 3: uebrig ist 0 und nicht -2");
    }

    @Test
    @DisplayName("Mehrere Vertraege laufen in der Reihenfolge der Einheitennamen")
    void reihenfolgeIstStabil() {
        // Hier wird Geld abgebucht, und nach einem Abbruch mitten im Lauf soll
        // nachrechenbar sein, wer schon gezahlt hat.
        ablage.schreibe(vertrag(ANNA, "halle-2", 500L, 5_000L));
        ablage.schreibe(vertrag(BERT, "halle-1", 600L, 5_000L));

        List<Mietlauf.Posten> posten = lauf().laufe(TAG);

        assertEquals(List.of("halle-1", "halle-2"),
                List.of(posten.get(0).vertrag().einheit(),
                        posten.get(1).vertrag().einheit()));
    }

    @Test
    @DisplayName("Ein stolpernder Vertrag haelt die anderen NICHT auf")
    void einStolpernHaeltDenLaufNichtAuf() {
        // Der Lauf bucht Geld fuer ALLE ab. Reisst er beim ersten Fehler ab, zahlen die
        // Laeden mit den spaeteren Namen nie - und zwar jeden Tag dieselben.
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        ablage.schreibe(vertrag(BERT, "halle-2", 600L, 5_000L));

        List<Mietlauf.Posten> posten = new Mietlauf(wirtschaft, ablage, fach,
                new StolperndeKasse(kasse, ANNA), truhe, uhr, protokoll).laufe(TAG);

        assertEquals(Mietlauf.Ausgang.GESTOERT, posten.get(0).ausgang());
        assertEquals(Mietlauf.Ausgang.BEZAHLT, posten.get(1).ausgang(),
                "der zweite Laden zahlt trotzdem");
        assertEquals(-600L, summe(BERT, Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Ein gestoerter Lauf wird beim naechsten Takt WIEDERHOLT")
    void gestoerterLaufWirdWiederholt() {
        // Der Merker wird nur nach einem stoerungsfreien Lauf gesetzt. Eine verlorene
        // Tagesmiete waere sonst endgueltig - und doppelt abbuchen kann der
        // Wiederholversuch nicht, dafuer steht das Buchungsbuch.
        ablage.schreibe(vertrag(ANNA, "halle-1", 500L, 5_000L));
        StolperndeKasse stolpernd = new StolperndeKasse(kasse, ANNA);
        Mietlauf lauf = new Mietlauf(wirtschaft, ablage, fach, stolpernd, truhe, uhr,
                protokoll);

        assertEquals(Mietlauf.Ausgang.GESTOERT, lauf.imTakt().get(0).ausgang());
        stolpernd.heile();
        assertEquals(Mietlauf.Ausgang.BEZAHLT, lauf.imTakt().get(0).ausgang(),
                "derselbe Tag wird noch einmal versucht");
        assertTrue(lauf.imTakt().isEmpty(), "und DANN ist der Tag erledigt");
    }

    @Test
    @DisplayName("Eine Ablage, die nicht lesen kann, meldet EINEN gestoerten Posten")
    void ablageStummMeldetGestoert() {
        Mietlauf lauf = new Mietlauf(wirtschaft, new NichtLesbar(), fach, kasse, truhe,
                uhr, protokoll);

        List<Mietlauf.Posten> posten = lauf.laufe(TAG);

        assertEquals(1, posten.size());
        assertEquals(Mietlauf.Ausgang.GESTOERT, posten.get(0).ausgang());
        assertNull(posten.get(0).vertrag(), "kein Vertrag, also auch keine Nachricht");
        assertTrue(hatZeile(Level.SEVERE, "keine Miete abgebucht"), zeilenText());
    }

    @Test
    @DisplayName("Ohne Vertraege tut der Lauf nichts und wirft nicht")
    void leererLaufIstKeinFehler() {
        assertTrue(lauf().laufe(TAG).isEmpty());
        assertTrue(protokollzeilen.isEmpty(), zeilenText());
    }

    // ==================================================================== Werkzeug

    private Mietlauf lauf() {
        return new Mietlauf(wirtschaft, ablage, fach, kasse, truhe, uhr, protokoll);
    }

    private static Mietvertrag vertrag(UUID wer, String einheit, long miete,
            long kaution) {
        return new Mietvertrag(einheit, wer, TAG, TAG.plusDays(29), miete, kaution);
    }

    private long summe(UUID wer, Buchungsgrund grund) {
        return kasse.summeSeit(wer, Instant.EPOCH, grund);
    }

    private long zeilen(UUID wer, Buchungsgrund grund) {
        return zaehleBuchungen(kasse, wer, grund);
    }

    private static long zaehleBuchungen(SpeicherKasse welche, UUID wer,
            Buchungsgrund grund) {
        // Die ZEILEN und nicht die Summe: Eine Summe stimmt auch, wenn zweimal die
        // Haelfte gebucht wurde.
        return welche.verlauf(wer, 100).stream()
                .filter(buchung -> buchung.grund() == grund)
                .count();
    }

    private List<String> imFach(UUID wer) {
        List<String> namen = new ArrayList<>();
        for (Abholstueck stueck : fach.fuerSpieler(wer)) {
            namen.add(stueck.beschriftung());
        }
        return namen;
    }

    private boolean hatZeile(Level stufe, String teil) {
        return protokollzeilen.stream().anyMatch(zeile -> zeile.getLevel() == stufe
                && zeile.getMessage() != null && zeile.getMessage().contains(teil));
    }

    private String zeilenText() {
        StringBuilder alles = new StringBuilder();
        for (LogRecord zeile : protokollzeilen) {
            alles.append(zeile.getLevel()).append(": ").append(zeile.getMessage())
                    .append(" | ");
        }
        return alles.toString();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("MietlaufProbe-" + UUID.randomUUID());
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

    /** Eine Ladentruhe im Arbeitsspeicher, die mitschreibt, wann sie gerufen wurde. */
    private static final class Probentruhe implements Mietlauf.Truhe {

        private final java.util.Map<String, List<String>> inhalte =
                new java.util.LinkedHashMap<>();
        private java.util.function.BooleanSupplier vertragDa;
        private List<String> beobachtet;

        void lege(String einheit, String... beschriftungen) {
            inhalte.put(einheit, new ArrayList<>(List.of(beschriftungen)));
        }

        List<String> inhalt(String einheit) {
            return inhalte.getOrDefault(einheit, List.of());
        }

        /** Von jetzt an wird jeder Griff samt Vertragslage aufgeschrieben. */
        void merkeVertragslage(java.util.function.BooleanSupplier lage,
                List<String> hinein) {
            this.vertragDa = lage;
            this.beobachtet = hinein;
        }

        @Override
        public List<Abholgut> lese(String einheit) {
            notiere("lese");
            List<Abholgut> gut = new ArrayList<>();
            for (String name : inhalt(einheit)) {
                gut.add(new Abholgut(name, "inhalt-von-" + name));
            }
            return gut;
        }

        @Override
        public void leere(String einheit) {
            notiere("leere");
            inhalte.remove(einheit);
        }

        private void notiere(String was) {
            if (beobachtet != null) {
                beobachtet.add(was + ", Vertrag da: " + vertragDa.getAsBoolean());
            }
        }
    }

    /** Ein Fach, in das nichts hineingeht - fuer den Abbruch der Raeumung. */
    private static final class NichtEinlegbar implements Abholablage {
        @Override
        public List<Abholstueck> fuerSpieler(UUID empfaenger) {
            return List.of();
        }

        @Override
        public int anzahlFuer(UUID empfaenger) {
            return 0;
        }

        @Override
        public void lege(UUID empfaenger, String herkunft, Instant wann,
                List<Abholgut> stapel) {
            throw new IllegalStateException("Abholfach nicht schreibbar (Probe)");
        }

        @Override
        public int nimm(UUID empfaenger, List<Long> ids) {
            return 0;
        }
    }

    /** Eine Mietablage, die nicht lesen kann. */
    private static final class NichtLesbar implements Mietablage {
        @Override
        public Mietvertrag fuerEinheit(String einheit) {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public Mietvertrag fuerMieter(UUID mieter) {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public List<Mietvertrag> alle() {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public void schreibe(Mietvertrag vertrag) {
            throw new IllegalStateException("nicht schreibbar (Probe)");
        }

        @Override
        public boolean loesche(String einheit) {
            throw new IllegalStateException("nicht loeschbar (Probe)");
        }
    }

    /** Zaehlt die Schreibzuege - fuer die Probe ueber den gesparten Schreibzug. */
    private static final class ZaehlendeAblage implements Mietablage {

        private final Mietablage echte;
        private int schreibzuege;

        ZaehlendeAblage(Mietablage echte) {
            this.echte = echte;
        }

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
            schreibzuege++;
            echte.schreibe(vertrag);
        }

        @Override
        public boolean loesche(String einheit) {
            return echte.loesche(einheit);
        }
    }

    /**
     * Eine Kasse, die beim ABBUCHEN fuer EINEN Spieler stolpert - und sich heilen laesst.
     *
     * <p>Setzt {@link de.freimarken.kern.Kasse} um und erbt nicht von
     * {@code SpeicherKasse}: Jene ist {@code final}, und das ist richtig so.
     *
     * <p>Nur das Abbuchen stolpert, nicht das Lesen: Sonst waere schon die Frage "heute
     * bezahlt" rot, und die Probe saehe nie, was danach kommt.
     */
    private static final class StolperndeKasse implements de.freimarken.kern.Kasse {

        private final de.freimarken.kern.Kasse echte;
        private final UUID wer;
        private boolean kaputt = true;

        StolperndeKasse(de.freimarken.kern.Kasse echte, UUID wer) {
            this.echte = echte;
            this.wer = wer;
        }

        void heile() {
            kaputt = false;
        }

        @Override
        public boolean abbuchen(UUID welcher, long betrag, Buchungsgrund grund) {
            if (kaputt && wer.equals(welcher)) {
                throw new IllegalStateException("Kasse nicht erreichbar (Probe)");
            }
            return echte.abbuchen(welcher, betrag, grund);
        }

        @Override
        public long guthaben(UUID welcher) {
            return echte.guthaben(welcher);
        }

        @Override
        public boolean eroeffne(UUID welcher) {
            return echte.eroeffne(welcher);
        }

        @Override
        public long summeSeit(UUID welcher, Instant seit, Buchungsgrund grund) {
            return echte.summeSeit(welcher, seit, grund);
        }

        @Override
        public void gutschreiben(UUID welcher, long betrag, Buchungsgrund grund) {
            echte.gutschreiben(welcher, betrag, grund);
        }

        @Override
        public List<Buchung> verlauf(UUID welcher, int hoechstens) {
            return echte.verlauf(welcher, hoechstens);
        }
    }
}
