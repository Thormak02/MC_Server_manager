package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Auskunft ueber den eigenen Rang - zusammengestellt ohne Welt und ohne Datenbank.
 *
 * <p>Zwei Lagen sind im Spiel teuer und stehen darum als eigene Probe da: Auf der
 * hoechsten Stufe darf GAR NICHT gemessen werden (jede Messung ausser dem Beruf ist eine
 * Abfrage im Hauptfaden), und ein abgelegter Rang, den die Leiter nicht mehr kennt, darf
 * nicht wie ein normaler Anfang aussehen.
 */
@DisplayName("Die Rangschau")
class RangschauTest {

    private static final long STUNDE = Bedingungsart.SEKUNDEN_JE_STUNDE;
    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());

    /** Die Leiter der Proben: reisender ohne Bedingung, siedler, buerger. */
    private static final Rangwerte LEITER = new Rangwerte(true, List.of(
            new Rangwerte.Stufe("reisender", Map.of(), Map.of(), List.of()),
            new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                    Map.of(), List.of()),
            new Rangwerte.Stufe("buerger", Map.of(Bedingungsart.TAETIGE_STUNDEN, 10L,
                    Bedingungsart.PARZELLEN, 2L), Map.of(), List.of())));

    /** Was gefragt wurde - die Mitschrift der Messnaht. */
    private final List<List<Bedingungsart>> gefragt = new ArrayList<>();

    private Rangschau schau(Rangwerte werte, Rangablage ablage,
                            Object... messwerte) {
        Map<Bedingungsart, Long> karte = new EnumMap<>(Bedingungsart.class);
        for (int stelle = 0; stelle < messwerte.length; stelle += 2) {
            karte.put((Bedingungsart) messwerte[stelle],
                    ((Number) messwerte[stelle + 1]).longValue());
        }
        return new Rangschau(ablage, new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitRaengen(werte)),
                (wer, arten) -> {
                    // VOR der Antwort mitgeschrieben: Eine Mitschrift danach sieht einen
                    // werfenden Aufruf nie.
                    gefragt.add(List.copyOf(arten));
                    return karte;
                });
    }

    private static Rangablage mitRang(String rang) {
        var ablage = new SpeicherRaenge();
        if (rang != null) {
            ablage.setze(new Rangablage.Antrag(ANNA, rang, java.time.Instant.EPOCH),
                    Rangstand.leer(ANNA));
        }
        return ablage;
    }

    @Test
    @DisplayName("Wer nie aufgestiegen ist, steht auf der untersten Stufe")
    void nieAufgestiegen() {
        var tafel = schau(LEITER, mitRang(null)).tafel(ANNA).orElseThrow();

        assertEquals("reisender", tafel.jetzt().schluessel());
        assertTrue(tafel.unbekannterEintrag().isEmpty(), "keine Zeile in der Ablage"
                + " ist der Normalfall und kein Fehler");
        assertEquals("siedler", tafel.naechste().orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Die naechste Stufe bringt JEDE ihrer Bedingungen mit Stand")
    void naechsteStufeMitStand() {
        var tafel = schau(LEITER, mitRang("siedler"),
                Bedingungsart.TAETIGE_STUNDEN, 12 * STUNDE,
                Bedingungsart.PARZELLEN, 1L).tafel(ANNA).orElseThrow();

        assertEquals("buerger", tafel.naechste().orElseThrow().schluessel());
        assertEquals(2, tafel.stand().size(), tafel.stand().toString());
        assertTrue(tafel.stand().get(0).erfuellt(), "die Stunden hat er");
        assertFalse(tafel.stand().get(1).erfuellt(), "die Parzellen nicht");
    }

    @Test
    @DisplayName("Gemessen wird nur, was die NAECHSTE Stufe verlangt")
    void gemessenWirdNurDasNoetige() {
        // Die Leiter verlangt insgesamt Stunden UND Parzellen; die naechste Stufe des
        // Reisenden nur Stunden. Jede Messung ausser dem Beruf ist eine Abfrage im
        // Hauptfaden - was niemand braucht, soll niemand fragen.
        schau(LEITER, mitRang(null)).tafel(ANNA);

        assertEquals(List.of(List.of(Bedingungsart.TAETIGE_STUNDEN)), gefragt);
    }

    @Test
    @DisplayName("Auf der hoechsten Stufe wird GAR NICHT gemessen")
    void hoechsteStufeMisstNicht() {
        var tafel = schau(LEITER, mitRang("buerger")).tafel(ANNA).orElseThrow();

        assertTrue(tafel.naechste().isEmpty());
        assertEquals(List.of(), tafel.stand(), "ohne naechste Stufe keine Bedingungen");
        assertEquals(List.of(), gefragt, "und keine einzige Abfrage");
    }

    @Test
    @DisplayName("Ein Rang, den die Leiter nicht mehr kennt, sagt es")
    void unbekannterRangSagtEs() {
        // stufeIn faellt auf die unterste zurueck - richtig, aber allein waere es eine
        // stille Rueckstufung. Der Spieler sieht sonst "du bist reisender" und meldet
        // nichts; dann steht die Zeile fuer immer so da.
        var tafel = schau(LEITER, mitRang("stifter")).tafel(ANNA).orElseThrow();

        assertEquals("stifter", tafel.unbekannterEintrag().orElseThrow(),
                "der eingetragene Schluessel gehoert in die Zeile - sonst kann der"
                        + " Spieler nicht melden, WAS dasteht");
        assertEquals("reisender", tafel.jetzt().schluessel(),
                "angezeigt wird die unterste - das ist, was auch der Aufstiegslauf"
                        + " annimmt");
    }

    @Test
    @DisplayName("Die Leiter nennt erreicht, hier und offen - aus der NUMMER")
    void leiterNenntDreiZustaende() {
        var tafel = schau(LEITER, mitRang("siedler")).tafel(ANNA).orElseThrow();

        assertEquals(3, tafel.leiter().size());
        assertEquals(List.of("reisender", "siedler", "buerger"),
                tafel.leiter().stream().map(sprosse -> sprosse.stufe().schluessel())
                        .toList(),
                "von unten nach oben");
        assertTrue(tafel.leiter().get(0).erreicht());
        assertFalse(tafel.leiter().get(0).jetzt());
        assertTrue(tafel.leiter().get(1).erreicht());
        assertTrue(tafel.leiter().get(1).jetzt(), "hier steht er");
        assertFalse(tafel.leiter().get(2).erreicht());
    }

    @Test
    @DisplayName("Erreicht kommt aus der NUMMER und nicht aus den Bedingungen")
    void erreichtKommtAusDerNummer() {
        // Wer befoerdert wurde, behaelt seinen Rang, auch wenn der Betreiber die
        // Bedingungen danach heraufsetzt. Die Leiter zeigt, wo er steht - nicht, was er
        // heute wieder schaffen wuerde. Darum stehen hier KEINE Messwerte.
        var tafel = schau(LEITER, mitRang("buerger")).tafel(ANNA).orElseThrow();

        assertTrue(tafel.leiter().get(2).erreicht(),
                "ohne eine einzige gemessene Stunde");
    }

    @Test
    @DisplayName("Ohne Rangleiter gibt es keine Tafel")
    void ohneLeiterKeineTafel() {
        assertTrue(schau(new Rangwerte(false, LEITER.leiter()), mitRang(null))
                .tafel(ANNA).isEmpty(), "abgeschaltet");
        assertTrue(schau(new Rangwerte(true, List.of()), mitRang(null))
                .tafel(ANNA).isEmpty(), "leere Leiter");
        assertEquals(List.of(), gefragt, "und nichts gemessen");
    }

    @Test
    @DisplayName("Eine stumme Ablage wirft weiter - der Befehl sagt es")
    void stummeAblageWirftWeiter() {
        // Ein Rueckfall auf die unterste Stufe waere die schlechteste Antwort: Sie sieht
        // aus wie eine Auskunft und ist eine Vermutung.
        Rangablage stumm = new Rangablage() {
            @Override
            public Rangstand lies(UUID spieler) {
                throw new IllegalStateException("die Ablage schweigt");
            }

            @Override
            public boolean setze(Antrag antrag, Rangstand erwartet) {
                throw new IllegalStateException("die Ablage schweigt");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> schau(LEITER, stumm).tafel(ANNA));
    }

    @Test
    @DisplayName("Ablage, Wirtschaftsstand und Messung sind Pflicht")
    void pflichtenGelten() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Bedingungsmessung messung = (wer, arten) -> Map.of();

        assertThrows(IllegalArgumentException.class,
                () -> new Rangschau(null, stand, messung));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangschau(new SpeicherRaenge(), null, messung));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangschau(new SpeicherRaenge(), stand, null));
    }
}
