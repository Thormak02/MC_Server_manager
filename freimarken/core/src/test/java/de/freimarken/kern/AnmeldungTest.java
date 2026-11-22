package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die eine Frage, die drei Wege stellen - geprueft, bevor einer von ihnen sie stellt.
 *
 * <p>Was hier gruen ist, gilt fuer die Wache beim Beitritt, fuer {@code /regeln} und fuer
 * den Bundesmarkt gleichermassen. Genau das ist der Zweck dieser Klasse: Drei Fassungen
 * derselben Frage wichen eines Tages ab, und dann boete ein Weg etwas an, was ein anderer
 * verweigert.
 */
@DisplayName("Die Anmeldung")
class AnmeldungTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-21T12:00:00Z");
    private static final UUID ANNA = new UUID(0L, 1L);

    private static Anmeldung mit(Anmeldewerte werte, Anmeldeablage ablage) {
        return new Anmeldung(ablage,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitAnmeldung(werte)),
                Clock.fixed(MITTAGS, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("1 - wer nichts angenommen hat, muss annehmen")
    void werNichtsAngenommenHatMussAnnehmen() {
        Anmeldung anmeldung = mit(Anmeldewerte.VORGABE, new SpeicherAnmeldungen());

        assertTrue(anmeldung.an());
        assertTrue(anmeldung.verlangt(ANNA));
    }

    @Test
    @DisplayName("2 - nach der Annahme verlangt sie nichts mehr - und der Zeitpunkt steht")
    void nachDerAnnahmeIstRuhe() {
        var ablage = new SpeicherAnmeldungen();
        Anmeldung anmeldung = mit(Anmeldewerte.VORGABE, ablage);

        assertTrue(anmeldung.nimmAn(ANNA), "die erste Annahme ist eine Aenderung");

        assertFalse(anmeldung.verlangt(ANNA));
        // DIE UHR KOMMT VON AUSSEN: Haette die Anmeldung eine eigene, stuende hier die
        // Zeit des Testlaufs, und die Probe muesste warten, um etwas zu beweisen.
        assertEquals(MITTAGS, ablage.lies(ANNA).angenommenAm());
        assertEquals(Anmeldewerte.VORGABE_FASSUNG, ablage.lies(ANNA).fassung());
    }

    @Test
    @DisplayName("3 - eine NEUE Fassung verlangt eine neue Annahme")
    void neueFassungVerlangtNeueAnnahme() {
        // DER TEURE FALL, und der Grund fuer die ganze Fassungsspalte: Der Betreiber
        // aendert seine Regeln. Ohne diesen Vergleich gilt die alte Zustimmung weiter,
        // und der Nachweis in der Datenbank zeigt auf einen Text, den es nicht mehr gibt.
        var ablage = new SpeicherAnmeldungen();
        mit(Anmeldewerte.VORGABE, ablage).nimmAn(ANNA);

        Anmeldung danach = mit(new Anmeldewerte(true, "2026-12-01"), ablage);

        assertTrue(danach.verlangt(ANNA),
                "nach einer Regelaenderung muss er erneut zustimmen");
        assertTrue(danach.nimmAn(ANNA));
        assertFalse(danach.verlangt(ANNA));
        assertEquals("2026-12-01", ablage.lies(ANNA).fassung());
    }

    @Test
    @DisplayName("4 - ist die Anmeldung AUS, verlangt sie von niemandem etwas")
    void ausVerlangtNichts() {
        var ablage = new SpeicherAnmeldungen();
        Anmeldung aus = mit(new Anmeldewerte(false, Anmeldewerte.VORGABE_FASSUNG),
                ablage);

        assertFalse(aus.an());
        assertFalse(aus.verlangt(ANNA), "ohne Anmeldung darf jeder handeln");
        // UND NICHTS WIRD ABGELEGT: Eine Zustimmung, die niemand verlangt hat, waere ein
        // Nachweis ueber eine Frage, die nie gestellt wurde.
        assertFalse(ablage.lies(ANNA).jeAngenommen());
        assertThrows(IllegalStateException.class, () -> aus.nimmAn(ANNA));
    }

    @Test
    @DisplayName("5 - wird die Anmeldung eingeschaltet, zaehlt eine alte Annahme weiter")
    void altAngenommenZaehltWeiter() {
        // Die Lage eines Betreibers, der die Anmeldung aus- und wieder einschaltet: Die
        // abgelegten Annahmen bleiben stehen, also wird nur gefragt, wer wirklich nicht
        // zugestimmt hat. Loeschte das Ausschalten die Ablage, waere jeder Schalter ein
        // Verlust des Nachweises.
        var ablage = new SpeicherAnmeldungen();
        mit(Anmeldewerte.VORGABE, ablage).nimmAn(ANNA);

        Anmeldung aus = mit(new Anmeldewerte(false, Anmeldewerte.VORGABE_FASSUNG), ablage);
        assertFalse(aus.verlangt(ANNA));

        Anmeldung wiederAn = mit(Anmeldewerte.VORGABE, ablage);
        assertFalse(wiederAn.verlangt(ANNA), "seine Annahme gilt weiter");
    }

    @Test
    @DisplayName("6 - die FASSUNG kommt aus dem Halter und nicht aus einem Schnappschuss")
    void fassungKommtAusDemHalter() {
        // Dieselbe Anmeldung-Instanz, ein Neuladen dazwischen. Haette sie die Fassung
        // beim Bauen gemerkt, verlangte sie danach die alte - und der Betreiber, der
        // gerade seine Regeln geaendert hat, bekaeme niemanden neu gefragt.
        var ablage = new SpeicherAnmeldungen();
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var anmeldung = new Anmeldung(ablage, stand,
                Clock.fixed(MITTAGS, ZoneOffset.UTC));
        anmeldung.nimmAn(ANNA);
        assertFalse(anmeldung.verlangt(ANNA));

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitAnmeldung(
                new Anmeldewerte(true, "2027-01-01")));

        assertEquals("2027-01-01", anmeldung.fassung());
        assertTrue(anmeldung.verlangt(ANNA),
                "die geltende Fassung muss aus dem Halter kommen");
    }

    @Test
    @DisplayName("7 - ohne Ablage, Halter, Uhr oder Spieler wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Clock uhr = Clock.fixed(MITTAGS, ZoneOffset.UTC);

        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldung(null, stand, uhr));
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldung(new SpeicherAnmeldungen(), null, uhr));
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldung(new SpeicherAnmeldungen(), stand, null));

        Anmeldung anmeldung = mit(Anmeldewerte.VORGABE, new SpeicherAnmeldungen());
        assertThrows(IllegalArgumentException.class, () -> anmeldung.verlangt(null));
        assertThrows(IllegalArgumentException.class, () -> anmeldung.nimmAn(null));
    }
}
