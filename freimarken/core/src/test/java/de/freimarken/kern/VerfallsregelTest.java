package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung zum Verfall - fuenf Staende, eine Frist [Plan 16, Teil E5].
 *
 * <p><b>Die Zone ist NICHT UTC</b>, und daran haengt die Haelfte dieser Proben: Eine
 * Stadt, die nie besucht wurde, rechnet ab ihrer GRUENDUNG, und die steht als
 * {@code Instant} in der Zeile. Mit Uhr und Zone beide auf UTC waere jede Zeile hier
 * gruen, auch wenn die Tagesgrenze ganz fehlte.
 */
@DisplayName("Der Verfall einer Freistadt")
class VerfallsregelTest {

    private static final String WELT = "freimarken:freistaedte";

    /** Auckland, damit der Tag NICHT der UTC-Tag ist. */
    private static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

    private final UUID anna = UUID.randomUUID();

    @Test
    @DisplayName("Frisch besucht: nichts zu tun")
    void frischBesuchtIstAktiv() {
        Parzelle stadt = stadtBesuchtAm(LocalDate.of(2026, 11, 17));

        assertEquals(Verfallsregel.Stand.AKTIV, stand(stadt, false,
                LocalDate.of(2026, 11, 17)));
        assertEquals(365L, Verfallsregel.resttage(stadt, Verfallswerte.VORGABE,
                LocalDate.of(2026, 11, 17), ZONE));
    }

    @Test
    @DisplayName("Die drei Grenzen liegen GENAU dort, wo sie stehen")
    void dieDreiGrenzen() {
        // JEDE GRENZE ZWEIMAL: einen Tag davor und am Tag selbst. Eine Probe, die nur
        // mitten im Bereich fragt, ist gruen, auch wenn der Vergleich um eins daneben
        // liegt - und um eins daneben heisst hier: Ein Stadtbesitzer liest seine letzte
        // Warnung einen Tag NACH dem Verfall.
        LocalDate besuch = LocalDate.of(2026, 1, 1);
        Parzelle stadt = stadtBesuchtAm(besuch);
        Verfallswerte werte = Verfallswerte.VORGABE;

        // Erste Warnung: 60 Tage vorher, also 305 Tage ohne Besuch.
        assertEquals(Verfallsregel.Stand.AKTIV,
                stand(stadt, false, besuch.plusDays(304)), "einen Tag davor");
        assertEquals(Verfallsregel.Stand.WARNUNG_FRUEH,
                stand(stadt, false, besuch.plusDays(305)), "und genau am Tag");

        // Zweite Warnung: 14 Tage vorher, also 351 Tage ohne Besuch.
        assertEquals(Verfallsregel.Stand.WARNUNG_FRUEH,
                stand(stadt, false, besuch.plusDays(350)), "einen Tag davor");
        assertEquals(Verfallsregel.Stand.WARNUNG_SPAET,
                stand(stadt, false, besuch.plusDays(351)), "und genau am Tag");

        // Faellig: 365 Tage ohne Besuch.
        assertEquals(Verfallsregel.Stand.WARNUNG_SPAET,
                stand(stadt, false, besuch.plusDays(364)), "einen Tag davor");
        assertEquals(Verfallsregel.Stand.FAELLIG,
                stand(stadt, false, besuch.plusDays(365)), "und genau am Tag");

        assertEquals(1L, Verfallsregel.resttage(stadt, werte, besuch.plusDays(364),
                ZONE), "am letzten Tag steht eine 1 im Satz");
        assertEquals(0L, Verfallsregel.resttage(stadt, werte, besuch.plusDays(400),
                ZONE), "und nie eine negative Zahl - die liest sich wie ein Fehler");
    }

    @Test
    @DisplayName("Ein STADTBUERGER haelt die Stadt - auch unbesucht und auch gewarnt")
    void buergerHaeltDieStadt() {
        // Die zweite Haelfte der Regel aus Plan 16 ("unbesucht UND ohne Buerger"), und
        // sie steht VOR der Fristrechnung: Wo jemand wohnt, ist keine Geisterstadt -
        // und ihr Besitzer soll dafuer auch nicht gewarnt werden.
        Parzelle stadt = stadtBesuchtAm(LocalDate.of(2020, 1, 1));

        assertEquals(Verfallsregel.Stand.FAELLIG,
                stand(stadt, false, LocalDate.of(2026, 11, 17)),
                "ohne Buerger ist sie laengst faellig");
        assertEquals(Verfallsregel.Stand.AKTIV,
                stand(stadt, true, LocalDate.of(2026, 11, 17)),
                "mit Buerger nicht - und zwar ohne Warnung");
    }

    @Test
    @DisplayName("Noch NIE besucht rechnet ab der GRUENDUNG - nicht ab 1970")
    void nieBesuchtRechnetAbDerGruendung() {
        // DER GRUND, WARUM NIE_BESUCHT ein null ist und nicht der 1.1.1970: Mit jenem
        // waere jede frisch gegruendete Stadt am Tag ihrer Gruendung faellig, und
        // 400.000 Mark waeren weg, bevor der Besitzer das erste Grundstueck
        // zugeschnitten hat.
        //
        // Die Gruendung ist ein ZEITPUNKT, und erst die Zone macht daraus einen Tag.
        // 2026-11-17 um 22:30 UTC ist in Auckland schon der 18. - also ist am 18. der
        // Tag null und nicht der Tag eins.
        Instant gegruendet = Instant.parse("2026-11-17T22:30:00Z");
        Parzelle frisch = new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                gegruendet, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                "annastadt", Parzelle.KEINE_RECHTE, UUID.randomUUID(),
                Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT);

        assertEquals(Verfallsregel.Stand.AKTIV,
                stand(frisch, false, LocalDate.of(2026, 11, 18)));
        assertEquals(365L, Verfallsregel.resttage(frisch, Verfallswerte.VORGABE,
                LocalDate.of(2026, 11, 18), ZONE),
                "am Gruendungstag ist die ganze Frist da - gerechnet in der ZONE und"
                        + " nicht in UTC, wo es noch der 17. waere");
        assertEquals(Verfallsregel.Stand.FAELLIG,
                stand(frisch, false, LocalDate.of(2027, 11, 18)),
                "und ein Jahr spaeter ist sie faellig, ohne dass sie je besucht wurde");
    }

    @Test
    @DisplayName("Abgeschaltet gewinnt ueber alles - auch ueber eine verfallene Stadt")
    void abgeschaltetGewinnt() {
        // Wer den Verfall abschaltet, soll keine Warnungen mehr lesen. Auch nicht fuer
        // eine Stadt, die vorher schon verfallen war - sonst stuende sie fuer immer in
        // einer Warnung, die niemand mehr einloesen kann.
        Parzelle laengstFaellig = stadtBesuchtAm(LocalDate.of(2020, 1, 1));
        Parzelle verfallen = laengstFaellig.mitZustand(Zustand.VERFALLEN);
        Verfallswerte aus = Verfallswerte.VORGABE.mitAn(false);

        assertEquals(Verfallsregel.Stand.AKTIV, Verfallsregel.stand(laengstFaellig,
                false, aus, LocalDate.of(2026, 11, 17), ZONE));
        assertEquals(Verfallsregel.Stand.AKTIV, Verfallsregel.stand(verfallen, false,
                aus, LocalDate.of(2026, 11, 17), ZONE));
    }

    @Test
    @DisplayName("VERFALLEN ist ein eigener Stand - und nicht wieder FAELLIG")
    void verfallenIstNichtFaellig() {
        // Ohne diese Trennung setzte der taegliche Lauf jeden Tag denselben Zustand
        // neu, und jeder Schreibvorgang leert den Zwischenspeicher aller Parzellen.
        Parzelle verfallen = stadtBesuchtAm(LocalDate.of(2020, 1, 1))
                .mitZustand(Zustand.VERFALLEN);

        assertEquals(Verfallsregel.Stand.VERFALLEN,
                stand(verfallen, false, LocalDate.of(2026, 11, 17)));
        assertTrue(Verfallsregel.Stand.VERFALLEN != Verfallsregel.Stand.FAELLIG);
    }

    @Test
    @DisplayName("Ein Besuch aus der ZUKUNFT laesst die Stadt als besucht gelten")
    void besuchAusDerZukunft() {
        // Eine verstellte Uhr oder Zone kann so einen Wert hinterlassen. Harmlos: Die
        // Stadt gilt als besucht, und Freistadtbuch.stempleBesuch heilt den Wert beim
        // naechsten echten Besuch. Die Alternative waere eine Stadt, die wegen einer
        // falschen Zahl verfaellt.
        Parzelle stadt = stadtBesuchtAm(LocalDate.of(2027, 1, 1));

        assertEquals(Verfallsregel.Stand.AKTIV,
                stand(stadt, false, LocalDate.of(2026, 11, 17)));
    }

    @Test
    @DisplayName("Verfallen koennen nur Freistaedte - und nie ohne Tag und Zone")
    void unbrauchbareFragenWerfen() {
        Parzelle parzelle = new Parzelle(1L, "freimarken:altfurt", 0, 0, anna, Set.of(),
                1_000L, Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL,
                Zustand.AKTIV);
        Parzelle stadt = stadtBesuchtAm(LocalDate.of(2026, 11, 17));

        assertThrows(IllegalArgumentException.class, () -> Verfallsregel.stand(parzelle,
                false, Verfallswerte.VORGABE, LocalDate.of(2026, 11, 17), ZONE));
        assertThrows(IllegalArgumentException.class, () -> Verfallsregel.stand(null,
                false, Verfallswerte.VORGABE, LocalDate.of(2026, 11, 17), ZONE));
        assertThrows(IllegalArgumentException.class, () -> Verfallsregel.stand(stadt,
                false, Verfallswerte.VORGABE, null, ZONE));
        assertThrows(IllegalArgumentException.class, () -> Verfallsregel.stand(stadt,
                false, Verfallswerte.VORGABE, LocalDate.of(2026, 11, 17), null));
        assertThrows(IllegalArgumentException.class, () -> Verfallsregel.stand(stadt,
                false, null, LocalDate.of(2026, 11, 17), ZONE));
    }

    private Verfallsregel.Stand stand(Parzelle stadt, boolean hatBuerger,
            LocalDate heute) {
        return Verfallsregel.stand(stadt, hatBuerger, Verfallswerte.VORGABE, heute,
                ZONE);
    }

    private Parzelle stadtBesuchtAm(LocalDate besuch) {
        return new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                Instant.parse("2019-01-01T12:00:00Z"), Art.FREISTADT,
                Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, "annastadt",
                Parzelle.KEINE_RECHTE, UUID.randomUUID(), Parzelle.KEINE_REGELN,
                besuch);
    }
}
