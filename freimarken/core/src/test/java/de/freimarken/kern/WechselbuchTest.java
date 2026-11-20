package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Wechselstube: zwei Buecher, zwei Richtungen, ein Deckel.
 *
 * <p>Geprueft wird mit ECHTEN Kassen im Arbeitsspeicher und nicht mit Attrappen, wo es
 * geht: Die Frage dieses Teils ist, was in den zwei Buechern STEHT - und das sieht eine
 * Attrappe nicht, sie sieht nur den Ruf. Attrappen stehen nur dort, wo eine Kasse
 * SCHWEIGEN soll.
 */
@DisplayName("Die Wechselstube")
class WechselbuchTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    /** 14:37 Ortszeit am 19.11.2026 - mitten am Tag, damit die Tagesgrenze nicht stoert. */
    private static final Instant MITTAGS = Instant.parse("2026-11-19T13:37:00Z");

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Eine Wechselstube mit echten Kassen - und was in ihnen steht. */
    private record Stube(Wechselbuch buch, Kasse mark, Goldkasse gold) {
    }

    private static Stube stube(long markAnfang, Wechselwerte werte) {
        return stube(markAnfang, werte, Clock.fixed(MITTAGS, ZoneOffset.UTC));
    }

    private static Stube stube(long markAnfang, Wechselwerte werte, Clock uhr) {
        Kasse mark = new SpeicherKasse(markAnfang, uhr);
        mark.eroeffne(ANNA);
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L, uhr));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWechsel(werte)
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        return new Stube(new Wechselbuch(mark, gold, stand, uhr), mark, gold);
    }

    @Test
    @DisplayName("Kaufen: die Mark sind weg, die Goldmark sind da")
    void kaufen() {
        Stube stube = stube(10_000L, Wechselwerte.VORGABE);

        var entscheid = stube.buch().kaufe(ANNA, 3L);

        assertEquals(Wechselbuch.Bescheid.GEKAUFT, entscheid.bescheid());
        assertEquals(3L, entscheid.anzahl());
        assertEquals(3_000L, entscheid.mark());
        assertEquals(3L, entscheid.stand());
        assertEquals(7_000L, stube.mark().guthaben(ANNA));
        assertEquals(3L, stube.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Kaufen schreibt in BEIDE Buecher, je mit dem Grund dieses Buches")
    void kaufenSchreibtBeideBuecher() {
        // DIE PROBE, DIE EINE ZEILE IM FALSCHEN BUCH FAENGT: Beide Gruende heissen fast
        // gleich, und ein Dreher waere eine Zeile, die richtig aussieht und von der
        // falschen Waehrung erzaehlt.
        Stube stube = stube(10_000L, Wechselwerte.VORGABE);

        stube.buch().kaufe(ANNA, 2L);

        assertEquals(List.of(Buchungsgrund.WECHSEL_MARK_WEG, Buchungsgrund.EROEFFNUNG),
                gruende(stube.mark()));
        // Die EROEFFNUNG steht mit im Goldbuch, und sie kommt nicht von hier: Jede Kasse
        // legt ein Konto mit dieser Zeile an, in welchem Buch sie auch fuehrt.
        assertEquals(List.of(Buchungsgrund.WECHSEL_GOLDMARK_HER, Buchungsgrund.EROEFFNUNG),
                gruende(stube.gold()));
        // UND JEDE ZEILE TRAEGT EINEN GRUND, DER IN IHR BUCH GEHOERT. Mechanisch gefragt
        // und nicht Grund fuer Grund aufgeschrieben: Ein siebter Wechselgrund waere sonst
        // nicht gedeckt.
        //
        // Gefragt mit gehoertIn und nicht mit == : Die EROEFFNUNG, die jede Kasse von
        // sich aus schreibt, steht in BEIDEN Buechern (Buch.JEDES) - genau daran war
        // diese Probe beim ersten Lauf rot.
        gruende(stube.mark()).forEach(grund ->
                assertTrue(grund.gehoertIn(Buchungsgrund.Buch.MARK),
                        grund + " steht im Markbuch, gehoert aber in " + grund.buch()));
        gruende(stube.gold()).forEach(grund ->
                assertTrue(grund.gehoertIn(Buchungsgrund.Buch.GOLDMARK),
                        grund + " steht im Goldbuch, gehoert aber in " + grund.buch()));
    }

    @Test
    @DisplayName("Kaufen ohne Deckung: nichts passiert, und der PREIS steht in der Antwort")
    void kaufenOhneDeckung() {
        Stube stube = stube(2_999L, Wechselwerte.VORGABE);

        var entscheid = stube.buch().kaufe(ANNA, 3L);

        assertEquals(Wechselbuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(3_000L, entscheid.mark(), "die Antwort muss den Preis nennen");
        assertEquals(2_999L, stube.mark().guthaben(ANNA));
        assertEquals(0L, stube.gold().guthaben(ANNA));
        // KEINE ZEILE IM BUCH: Ein abgelehntes Abbuchen ist keine Bewegung.
        assertEquals(List.of(Buchungsgrund.EROEFFNUNG), gruende(stube.mark()));
        assertEquals(List.of(), gruende(stube.gold()));
    }

    @Test
    @DisplayName("Der Hinweg ist NICHT gedeckelt - auch nicht beim zwanzigsten Mal")
    void hinwegOhneDeckel() {
        // Plan 8.1: "Mark -> Goldmark frei". Der Hinweg VERNICHTET Mark, also braucht er
        // keine Grenze - und wer den Wechseldeckel aus Versehen auf beide Richtungen
        // haengt, nimmt dem Spieler genau den Weg, der die Goldmark erspielbar macht.
        Stube stube = stube(20_000L, Wechselwerte.VORGABE);

        for (int mal = 0; mal < 20; mal++) {
            assertEquals(Wechselbuch.Bescheid.GEKAUFT, stube.buch().kaufe(ANNA, 1L)
                    .bescheid(), "Kauf Nummer " + (mal + 1));
        }

        assertEquals(20L, stube.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Verkaufen: die Goldmark sind weg, die Mark sind da")
    void verkaufen() {
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 5L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 5L);

        assertEquals(Wechselbuch.Bescheid.VERKAUFT, entscheid.bescheid());
        assertEquals(4_000L, entscheid.mark());
        assertEquals(4_000L, stube.mark().guthaben(ANNA));
        assertEquals(0L, stube.gold().guthaben(ANNA));
        assertEquals(List.of(Buchungsgrund.WECHSEL_MARK_HER, Buchungsgrund.EROEFFNUNG),
                gruende(stube.mark()));
    }

    @Test
    @DisplayName("Der Rundlauf KOSTET - 1.000 Mark hin, 800 Mark zurueck")
    void rundlaufKostet() {
        // Die Gegenprobe zum Riegel in Wechselwerte, und zwar als Ende-zu-Ende-Rechnung:
        // Wer mit 1.000 Mark anfaengt und einmal hin und zurueck wechselt, hat 800.
        Stube stube = stube(1_000L, Wechselwerte.VORGABE);

        stube.buch().kaufe(ANNA, 1L);
        stube.buch().verkaufe(ANNA, 1L);

        assertEquals(800L, stube.mark().guthaben(ANNA));
        assertEquals(0L, stube.gold().guthaben(ANNA));
    }

    @Test
    @DisplayName("Ueber dem Deckel wird ABGESAGT und nicht gekuerzt")
    void ueberDemDeckelAbgesagt() {
        // DER UNTERSCHIED ZUM VERDIENSTDECKEL, und er ist eine Entscheidung: Dort wird
        // gekuerzt, weil ein abgebauter Block sich nicht zuruecknehmen laesst. Hier hat
        // der Spieler nur eine Zahl genannt - und wer zwanzig tauschen will und zwoelf
        // darf, soll das LESEN und nicht hinterher acht bei sich finden.
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 20L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 20L);

        assertEquals(Wechselbuch.Bescheid.DECKEL_ERREICHT, entscheid.bescheid());
        assertEquals(12L, entscheid.nochMoeglich(),
                "die Absage muss sagen, wie viele heute noch gehen");
        assertEquals(20L, stube.gold().guthaben(ANNA), "nichts wurde abgebucht");
        assertEquals(0L, stube.mark().guthaben(ANNA));
    }

    @Test
    @DisplayName("Genau auf dem Deckel geht es noch")
    void genauAufDemDeckel() {
        // Die Gegenrichtung, auf der Grenze: Ein Vergleich auf >= statt > nahm dem
        // Spieler die zwoelfte Goldmark, und die Absage nannte trotzdem zwoelf.
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 12L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 12L);

        assertEquals(Wechselbuch.Bescheid.VERKAUFT, entscheid.bescheid());
        assertEquals(9_600L, stube.mark().guthaben(ANNA));
        assertEquals(0L, entscheid.nochMoeglich(), "danach geht heute nichts mehr");
    }

    @Test
    @DisplayName("Ein Erloes GENAU in Hoehe des Deckels geht noch durch")
    void erloesGenauAufDemDeckel() {
        // DIE ECHTE GRENZE DES VERGLEICHS, und sie ist gemessen: Eine Einspritzung am
        // 20.11.2026 hat aus dem {@code >} ein {@code >=} gemacht - und ALLE Proben
        // blieben gruen. Der Grund ist Arithmetik: Mit 800 Mark je Goldmark trifft kein
        // Tausch den Deckel von 10.000 genau (zwoelf sind 9.600, dreizehn 10.400), also
        // entscheidet das Zeichen dort nichts. Mit einem Kurs von 1.000 sind zehn
        // Goldmark genau 10.000 Mark - und erst hier ist der Unterschied zwischen "noch
        // erlaubt" und "schon zu viel" sichtbar.
        //
        // Die Probe darueber bleibt daneben stehen: Sie haelt die Rechnung mit dem
        // ausgelieferten Kurs, und diese haelt das Zeichen.
        Stube stube = stube(0L, Wechselwerte.VORGABE.mitKursen(1_000L, 1_000L));
        stube.gold().gutschreiben(ANNA, 10L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 10L);

        assertEquals(Wechselbuch.Bescheid.VERKAUFT, entscheid.bescheid(),
                "genau der Deckel ist noch erlaubt - abgesagt wird erst darueber");
        assertEquals(10_000L, stube.mark().guthaben(ANNA));

        // UND EINE MARK DARUEBER NICHT MEHR: Ohne diese Haelfte waere die Probe mit
        // einem Vergleich gruen, der ALLES durchlaesst.
        Stube knapp = stube(0L, Wechselwerte.VORGABE.mitKursen(1_000L, 1_000L)
                .mitDeckel(9_999L));
        knapp.gold().gutschreiben(ANNA, 10L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        assertEquals(Wechselbuch.Bescheid.DECKEL_ERREICHT,
                knapp.buch().verkaufe(ANNA, 10L).bescheid());
    }

    @Test
    @DisplayName("Der Deckel zaehlt, was HEUTE schon zurueckkam - aus dem Markbuch")
    void deckelZaehltAusDemBuch() {
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 20L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        assertEquals(Wechselbuch.Bescheid.VERKAUFT,
                stube.buch().verkaufe(ANNA, 10L).bescheid());
        // 8.000 Mark sind durch, 2.000 frei - also noch zwei Goldmark und nicht drei.
        var zweiter = stube.buch().verkaufe(ANNA, 3L);

        assertEquals(Wechselbuch.Bescheid.DECKEL_ERREICHT, zweiter.bescheid());
        assertEquals(2L, zweiter.nochMoeglich());
        assertEquals(Wechselbuch.Bescheid.VERKAUFT,
                stube.buch().verkaufe(ANNA, 2L).bescheid());
        assertEquals(9_600L, stube.mark().guthaben(ANNA));
    }

    @Test
    @DisplayName("Am naechsten Tag ist der Deckel wieder frei")
    void naechsterTagIstFrei() {
        // Die Tagesgrenze ist die EINE Stelle, an der aus einem Zeitpunkt ein Tag wird -
        // gerechnet in Ortszeit und nicht in UTC. Hier wird die Uhr um einen Tag
        // weitergestellt, und die Zeilen von gestern zaehlen nicht mehr mit.
        Kasse mark = new SpeicherKasse(0L, Clock.fixed(MITTAGS, ZoneOffset.UTC));
        mark.eroeffne(ANNA);
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));
        gold.gutschreiben(ANNA, 30L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        var gestern = new Wechselbuch(mark, gold, stand,
                Clock.fixed(MITTAGS, ZoneOffset.UTC));
        var heute = new Wechselbuch(mark, gold, stand,
                Clock.fixed(MITTAGS.plusSeconds(86_400L), ZoneOffset.UTC));

        assertEquals(Wechselbuch.Bescheid.VERKAUFT, gestern.verkaufe(ANNA, 12L).bescheid());
        assertEquals(Wechselbuch.Bescheid.DECKEL_ERREICHT,
                gestern.verkaufe(ANNA, 1L).bescheid());

        assertEquals(Wechselbuch.Bescheid.VERKAUFT, heute.verkaufe(ANNA, 12L).bescheid(),
                "am naechsten Tag gilt der Deckel wieder ganz");
    }

    @Test
    @DisplayName("Eine ERSTATTUNG fuellt den Wechseldeckel nicht")
    void erstattungFuelltDenDeckelNicht() {
        // DIE PROBE ZU DEM EIGENEN GRUND: Eine misslungene Hinrichtung gibt dem Spieler
        // seine Mark zurueck - und das ist kein Wechsel. Stuende dort WECHSEL_MARK_HER,
        // haette ein Spieler, dem der Kauf misslang, seinen Tagesdeckel verbraucht.
        Kasse mark = new SpeicherKasse(10_000L);
        mark.eroeffne(ANNA);
        Goldkasse stumm = new Goldkasse(new StummeKasse());
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        var buch = new Wechselbuch(mark, stumm, stand, Clock.systemUTC());

        var kauf = buch.kaufe(ANNA, 9L);

        assertEquals(Wechselbuch.Bescheid.GESTOERT, kauf.bescheid());
        assertEquals(10_000L, mark.guthaben(ANNA), "der Preis ist zurueck");
        assertEquals(List.of(Buchungsgrund.WECHSEL_MARK_ZURUECK,
                        Buchungsgrund.WECHSEL_MARK_WEG, Buchungsgrund.EROEFFNUNG),
                gruende(mark));
        // UND DER DECKEL IST UNANGETASTET: zwoelf Goldmark, nicht drei.
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));
        gold.gutschreiben(ANNA, 20L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        var zweite = new Wechselbuch(mark, gold, stand, Clock.systemUTC());

        assertEquals(12L, zweite.auskunft(ANNA).orElseThrow().nochMoeglich());
    }

    @Test
    @DisplayName("Schweigt die Goldkasse, kommt der Preis zurueck")
    void goldkasseSchweigtBeimKaufen() {
        Kasse mark = new SpeicherKasse(5_000L);
        mark.eroeffne(ANNA);
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var buch = new Wechselbuch(mark, new Goldkasse(new StummeKasse()), stand,
                Clock.systemUTC());

        var entscheid = buch.kaufe(ANNA, 2L);

        assertEquals(Wechselbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(5_000L, mark.guthaben(ANNA));
    }

    @Test
    @DisplayName("Schweigt die Markkasse beim Rueckweg, kommt die Goldmark zurueck")
    void markkasseSchweigtBeimVerkaufen() {
        // EINE KASSE, DIE NUR DIE GUTSCHRIFT VERWEIGERT, und das ist nicht Feinheit,
        // sondern gemessen: Mit einer rundum stummen Markkasse scheitert schon der Blick
        // auf die Tagessumme, und dann kommt die Goldmark gar nicht erst in Gefahr. Die
        // Probe war so gruen, ohne die Rueckgabe je zu beruehren.
        Kasse ohneGutschrift = new OhneGutschrift(new SpeicherKasse(0L));
        ohneGutschrift.eroeffne(ANNA);
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));
        gold.gutschreiben(ANNA, 5L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var buch = new Wechselbuch(ohneGutschrift, gold, stand, Clock.systemUTC());

        var entscheid = buch.verkaufe(ANNA, 5L);

        assertEquals(Wechselbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(5L, gold.guthaben(ANNA), "die Goldmark ist zurueck");
        assertEquals(List.of(Buchungsgrund.WECHSEL_GOLDMARK_ZURUECK,
                        Buchungsgrund.WECHSEL_GOLDMARK_WEG,
                        Buchungsgrund.WECHSEL_GOLDMARK_HER, Buchungsgrund.EROEFFNUNG),
                gruende(gold));
    }

    @Test
    @DisplayName("Schweigt die Markkasse schon bei der Tagessumme, bleibt die Goldmark liegen")
    void markkasseSchweigtVorDemDeckel() {
        // Die Lage, die die Probe darueber beim ersten Lauf versehentlich gebaut hat -
        // und sie ist richtig so: Wer die Tagessumme nicht lesen kann, kennt den Deckel
        // nicht und darf darum nichts abbuchen.
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));
        gold.gutschreiben(ANNA, 5L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var buch = new Wechselbuch(new StummeKasse(), gold, stand, Clock.systemUTC());

        assertEquals(Wechselbuch.Bescheid.GESTOERT, buch.verkaufe(ANNA, 1L).bescheid());
        assertEquals(5L, gold.guthaben(ANNA), "nichts wurde angefasst");
        assertEquals(List.of(Buchungsgrund.WECHSEL_GOLDMARK_HER,
                        Buchungsgrund.EROEFFNUNG),
                gruende(gold));
    }

    @Test
    @DisplayName("Ein geschlossener Rueckweg sagt ab, ohne etwas anzufassen")
    void rueckwegZu() {
        Stube stube = stube(0L, Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU));
        stube.gold().gutschreiben(ANNA, 5L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 1L);

        assertEquals(Wechselbuch.Bescheid.RUECKWEG_ZU, entscheid.bescheid());
        assertEquals(5L, stube.gold().guthaben(ANNA));
        assertEquals(0L, stube.mark().guthaben(ANNA));
        // UND DER HINWEG GEHT TROTZDEM: Eine 0 schliesst den Rueckweg, nicht die Stube.
        Stube offen = stube(1_000L,
                Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU));
        assertEquals(Wechselbuch.Bescheid.GEKAUFT, offen.buch().kaufe(ANNA, 1L).bescheid());
    }

    @Test
    @DisplayName("Ohne genug Goldmark wird abgesagt - und der Stand steht in der Antwort")
    void zuWenigGoldmark() {
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 2L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var entscheid = stube.buch().verkaufe(ANNA, 3L);

        assertEquals(Wechselbuch.Bescheid.ZU_WENIG_GOLDMARK, entscheid.bescheid());
        assertEquals(2L, entscheid.stand());
        assertEquals(2L, stube.gold().guthaben(ANNA));
        assertEquals(0L, stube.mark().guthaben(ANNA));
    }

    @Test
    @DisplayName("Die Auskunft nennt Stand, Kurse und was heute noch geht")
    void auskunft() {
        Stube stube = stube(0L, Wechselwerte.VORGABE);
        stube.gold().gutschreiben(ANNA, 7L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        stube.buch().verkaufe(ANNA, 5L);

        var auskunft = stube.buch().auskunft(ANNA).orElseThrow();

        assertEquals(2L, auskunft.goldmark());
        assertEquals(4_000L, auskunft.heuteSchon());
        assertEquals(7L, auskunft.nochMoeglich(), "6.000 Mark frei sind sieben Goldmark");
        assertEquals(Wechselwerte.VORGABE, auskunft.werte());
    }

    @Test
    @DisplayName("Schweigt eine Kasse, gibt die Auskunft NICHTS statt einer halben Tafel")
    void auskunftBeiStoerung() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var buch = new Wechselbuch(new StummeKasse(), new Goldkasse(new StummeKasse()),
                stand, Clock.systemUTC());

        assertTrue(buch.auskunft(ANNA).isEmpty());
    }

    @Test
    @DisplayName("Die Werte kommen beim GEBRAUCH aus dem Stand - ein Neuladen erreicht sie")
    void werteKommenBeimGebrauch() {
        Kasse mark = new SpeicherKasse(10_000L);
        mark.eroeffne(ANNA);
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var buch = new Wechselbuch(mark, new Goldkasse(new SpeicherKasse(0L)), stand,
                Clock.systemUTC());

        stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitWechsel(Wechselwerte.VORGABE.mitKursen(2_000L, 500L)));

        assertEquals(2_000L, buch.werte().markJeGoldmark());
        assertEquals(Wechselbuch.Bescheid.GEKAUFT, buch.kaufe(ANNA, 1L).bescheid());
        assertEquals(8_000L, mark.guthaben(ANNA), "der NEUE Kurs hat gegolten");
    }

    @Test
    @DisplayName("Ohne Kasse, Stand oder Uhr gibt es keine Wechselstube")
    void ohneTeile() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Goldkasse gold = new Goldkasse(new SpeicherKasse(0L));

        assertThrows(IllegalArgumentException.class,
                () -> new Wechselbuch(null, gold, stand, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new Wechselbuch(
                new SpeicherKasse(0L), null, stand, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new Wechselbuch(
                new SpeicherKasse(0L), gold, null, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new Wechselbuch(
                new SpeicherKasse(0L), gold, stand, null));
    }

    @Test
    @DisplayName("Eine unmoegliche Anzahl ist ein Wurf und keine Absage")
    void unmoeglicheAnzahl() {
        Stube stube = stube(10_000L, Wechselwerte.VORGABE);

        assertThrows(IllegalArgumentException.class, () -> stube.buch().kaufe(ANNA, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> stube.buch().verkaufe(ANNA, -1L));
        assertThrows(IllegalArgumentException.class, () -> stube.buch().kaufe(null, 1L));
    }

    /** Die Gruende im Buch, der neueste zuerst. */
    private static List<Buchungsgrund> gruende(Kasse kasse) {
        return kasse.verlauf(ANNA, 20).stream().map(Buchung::grund).toList();
    }

    /**
     * Eine Kasse, die bei jeder Buchung wirft - und bei {@code guthaben} 0 gibt.
     *
     * <p>Die 0 ist Pflicht, damit der Riegel der Goldkasse sie annimmt: Er fragt genau
     * das. Geworfen wird erst beim Buchen, und das ist der Augenblick, um den es geht -
     * die Verbindung bricht nicht, wenn man sie aufbaut, sondern wenn man sie braucht.
     *
     * <p>Sie UMSETZT {@link Kasse} und erbt nicht von {@link SpeicherKasse}: Jene ist
     * {@code final}, und das ist richtig so.
     */
    private static final class StummeKasse implements Kasse {

        @Override
        public long guthaben(UUID spieler) {
            return 0L;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            throw new IllegalStateException("die Verbindung ist weg");
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Verbindung ist weg");
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Verbindung ist weg");
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            throw new IllegalStateException("die Verbindung ist weg");
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            throw new IllegalStateException("die Verbindung ist weg");
        }
    }

    /**
     * Eine Kasse, die alles kann ausser gutschreiben.
     *
     * <p>Sie UMSETZT {@link Kasse} und reicht an eine echte weiter: Die Lage, um die es
     * geht, ist "abgebucht, und die Gegenbuchung scheitert" - und die ist nur mit einer
     * Kasse herstellbar, die bis dahin funktioniert.
     */
    private static final class OhneGutschrift implements Kasse {

        private final Kasse darunter;

        private OhneGutschrift(Kasse darunter) {
            this.darunter = darunter;
        }

        @Override
        public long guthaben(UUID spieler) {
            return darunter.guthaben(spieler);
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return darunter.eroeffne(spieler);
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            return darunter.abbuchen(spieler, betrag, grund);
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("die Verbindung ist weg");
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            return darunter.verlauf(spieler, hoechstens);
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            return darunter.summeSeit(spieler, ab, grund);
        }
    }

    @Test
    @DisplayName("Ein Markgrund erreicht das Goldbuch nie - auch nicht von hier aus")
    void markgrundErreichtDasGoldbuchNicht() {
        // Der Riegel steht in der Goldkasse; hier wird nur gezeigt, dass die Stube ihn
        // nicht umgeht. Die Gegenseite - ein Goldmarkgrund im Markbuch - faengt der
        // Riegel in Wechselbuch selbst, und beide zusammen deckeln die eine Stelle des
        // Projekts, die zwei Buecher anfasst.
        Stube stube = stube(0L, Wechselwerte.VORGABE);

        assertThrows(IllegalArgumentException.class,
                () -> stube.gold().gutschreiben(ANNA, 1L, Buchungsgrund.TAGESBONUS));
        assertFalse(stube.buch().auskunft(ANNA).isEmpty(),
                "die Stube selbst arbeitet weiter");
    }
}
