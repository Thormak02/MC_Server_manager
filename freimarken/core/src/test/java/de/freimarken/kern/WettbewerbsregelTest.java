package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rangliste, Topf und Preise - die Rechnung des Angelwettbewerbs.
 *
 * <p>Die Kennungen sind hier FEST und nicht gewuerfelt: Bei Punktgleichheit entscheidet
 * die Kennung, und mit {@code randomUUID} waere diese Probe manchmal gruen.
 */
@DisplayName("Die Rechnung des Angelwettbewerbs")
class WettbewerbsregelTest {

    private static final LocalDate LAUF = LocalDate.of(2026, 11, 22);

    /** Feste Kennungen in bekannter Ordnung: A vor B vor C. */
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static Punktzeile zeile(UUID wer, int punkte, long eingezahlt) {
        return new Punktzeile(wer, LAUF, punkte, eingezahlt);
    }

    @Test
    @DisplayName("Die Rangliste sortiert nach Punkten, absteigend")
    void sortiertNachPunkten() {
        List<Punktzeile> sortiert = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 5, 250L), zeile(B, 120, 250L), zeile(C, 42, 250L)));

        assertEquals(List.of(B, C, A),
                sortiert.stream().map(Punktzeile::spieler).toList());
    }

    @Test
    @DisplayName("Bei Punktgleichheit entscheidet die KENNUNG - stabil, nicht zufaellig")
    void gleichstandIstStabil() {
        // Willkuerlich, aber stabil: Eine unbestimmte Reihenfolge gaebe denselben
        // Punktestand je Serverlauf einen anderen Sieger, und eine Probe darauf waere
        // mal gruen und mal rot.
        List<Punktzeile> einmal = Wettbewerbsregel.rangliste(List.of(
                zeile(C, 10, 250L), zeile(A, 10, 250L), zeile(B, 10, 250L)));
        List<Punktzeile> andersHerum = Wettbewerbsregel.rangliste(List.of(
                zeile(B, 10, 250L), zeile(A, 10, 250L), zeile(C, 10, 250L)));

        assertEquals(List.of(A, B, C),
                einmal.stream().map(Punktzeile::spieler).toList());
        assertEquals(einmal, andersHerum,
                "dieselben Zeilen in anderer Reihenfolge ergeben dieselbe Rangliste");
    }

    @Test
    @DisplayName("Zeilen mit 0 Punkten stehen MIT in der Liste - sie haben gezahlt")
    void nullPunkteStehenDrin() {
        List<Punktzeile> sortiert = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 0, 250L), zeile(B, 7, 250L)));

        assertEquals(2, sortiert.size());
        assertEquals(B, sortiert.get(0).spieler());
        assertEquals(A, sortiert.get(1).spieler(),
                "wer eingezahlt hat, gehoert in die Liste - auch ohne einen Fang");
    }

    @Test
    @DisplayName("Der Topf sind die EINGEZAHLTEN Betraege plus dem Zuschuss")
    void topfAusEinzahlungen() {
        // Und nicht "Teilnehmer mal Startgeld": Aendert der Betreiber das Startgeld
        // mitten im Lauf, zahlte die zweite Rechnung mehr aus, als hereinkam. Die drei
        // Betraege hier sind darum VERSCHIEDEN.
        long topf = Wettbewerbsregel.topf(List.of(
                zeile(A, 0, 100L), zeile(B, 0, 250L), zeile(C, 0, 300L)), 1_000L);

        assertEquals(1_650L, topf);
    }

    @Test
    @DisplayName("Ohne Zuschuss ist der Topf genau die Summe der Einzahlungen")
    void topfOhneZuschuss() {
        assertEquals(500L, Wettbewerbsregel.topf(List.of(
                zeile(A, 0, 250L), zeile(B, 0, 250L)), 0L));
    }

    @Test
    @DisplayName("Der Topf laeuft nicht ueber - er bleibt bei der Hoechstzahl stehen")
    void topfLaeuftNichtUeber() {
        // Ein uebergelaufener Topf waere NEGATIV, und ein negativer Topf verteilte
        // negative Preise - also Abbuchungen bei den Siegern.
        long topf = Wettbewerbsregel.topf(List.of(
                zeile(A, 0, Long.MAX_VALUE), zeile(B, 0, Long.MAX_VALUE)), 0L);

        assertEquals(Long.MAX_VALUE, topf);
        assertTrue(topf > 0L, "nie negativ");
    }

    @Test
    @DisplayName("Die Preise gehen nach der Verteilung an die Plaetze")
    void preiseNachVerteilung() {
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 250L), zeile(B, 120, 250L), zeile(C, 42, 250L)));

        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, 1_000L, List.of(50, 30, 20));

        // AUSGESCHRIEBEN und nicht gerechnet: B ist erster (120 Punkte), C zweiter,
        // A dritter.
        assertEquals(500L, preise.get(B));
        assertEquals(300L, preise.get(C));
        assertEquals(200L, preise.get(A));
    }

    @Test
    @DisplayName("Mehr Anteile als Teilnehmer: die ueberzaehligen fallen weg")
    void mehrAnteileAlsTeilnehmer() {
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 250L), zeile(B, 20, 250L)));

        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, 1_000L, List.of(50, 30, 20));

        assertEquals(2, preise.size(), "nur zwei Plaetze sind besetzt");
        assertEquals(500L, preise.get(B));
        assertEquals(300L, preise.get(A));
        // Die 20 Prozent bleiben im Topf - eine Senke, und die ist in dieser Wirtschaft
        // nie das Problem.
    }

    @Test
    @DisplayName("Mehr Teilnehmer als Anteile: die hinteren bekommen nichts")
    void mehrTeilnehmerAlsAnteile() {
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 250L), zeile(B, 20, 250L), zeile(C, 30, 250L)));

        Map<UUID, Long> preise = Wettbewerbsregel.preise(rangliste, 1_000L, List.of(100));

        assertEquals(1, preise.size());
        assertEquals(1_000L, preise.get(C), "der erste nimmt alles");
    }

    @Test
    @DisplayName("Ein Rest wird NICHT verteilt - er bleibt eine Senke")
    void restBleibtLiegen() {
        // Bei 999 und 50/30/20 bleiben zwei Mark uebrig. Jede Verteilung eines Rests
        // braeuchte eine Regel, welcher Platz ihn bekommt - und jede solche Regel ist
        // eine Entscheidung, die niemand getroffen hat.
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 333L), zeile(B, 20, 333L), zeile(C, 30, 333L)));

        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, 999L, List.of(50, 30, 20));

        assertEquals(499L, preise.get(C));
        assertEquals(299L, preise.get(B));
        assertEquals(199L, preise.get(A));
        assertEquals(997L, preise.values().stream().mapToLong(Long::longValue).sum(),
                "zwei Mark bleiben liegen - und das steht so im Methodenkopf");
    }

    @Test
    @DisplayName("Ein Preis von 0 steht nicht in der Karte")
    void keinePreiseVonNull() {
        // Bei einem Topf von 1 und 20 Prozent waere der dritte Preis 0. Eine Buchung
        // ueber 0 Mark mit dem Grund "Preis" ist eine Zeile, die der Spieler im Verlauf
        // sucht und nicht versteht.
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 1L), zeile(B, 20, 0L), zeile(C, 30, 0L)));

        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, 1L, List.of(50, 30, 20));

        assertEquals(Map.of(), preise, "bei einem Topf von 1 bekommt niemand etwas");
    }

    @Test
    @DisplayName("Die Preise laufen bei einem riesigen Topf nicht ueber")
    void preiseLaufenNichtUeber() {
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(List.of(
                zeile(A, 10, 0L)));

        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, Long.MAX_VALUE, List.of(100));

        assertTrue(preise.get(A) > 0L, "der Preis war " + preise.get(A)
                + " - negativ heisst uebergelaufen, und das ist eine Abbuchung");
    }

    @Test
    @DisplayName("Eine leere Rangliste ergibt keine Preise")
    void leereRangliste() {
        assertEquals(Map.of(),
                Wettbewerbsregel.preise(List.of(), 1_000L, List.of(50, 30, 20)));
        assertEquals(0L, Wettbewerbsregel.topf(List.of(), 0L));
    }

    @Test
    @DisplayName("Ohne Zeilen oder Anteile ist das ein Wurf")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.rangliste(null));
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.topf(null, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.topf(List.of(), -1L));
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.preise(null, 0L, List.of(50)));
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.preise(List.of(), 0L, null));
        assertThrows(IllegalArgumentException.class,
                () -> Wettbewerbsregel.preise(List.of(), -1L, List.of(50)));
    }


    @Test
    @DisplayName("Eine Punktzeile mit negativen Werten ist nicht zu bauen")
    void punktzeilePflichten() {
        assertThrows(IllegalArgumentException.class, () -> zeile(A, -1, 0L));
        assertThrows(IllegalArgumentException.class, () -> zeile(A, 0, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktzeile(null, LAUF, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktzeile(A, null, 0, 0L));
    }

    @Test
    @DisplayName("Punkte laufen bei der Hoechstzahl an, nicht darueber")
    void punkteLaufenAn() {
        // Eine uebergelaufene Zahl waere NEGATIV, und der Konstruktor wuerfe dann mitten
        // im Spiel - bei einem Fang, der nichts kaputtmachen soll.
        Punktzeile voll = zeile(A, Integer.MAX_VALUE, 0L);

        assertEquals(Integer.MAX_VALUE, voll.mitMehr(100).punkte());
        assertEquals(Integer.MAX_VALUE, zeile(A, Integer.MAX_VALUE - 1, 0L)
                .mitMehr(50).punkte());
        assertThrows(IllegalArgumentException.class, () -> voll.mitMehr(-1));
    }

    @Test
    @DisplayName("Eine Anmeldung ist eine Zeile mit 0 Punkten")
    void angemeldet() {
        Punktzeile frisch = Punktzeile.angemeldet(A, LAUF, 250L);

        assertEquals(0, frisch.punkte());
        assertEquals(250L, frisch.eingezahlt());
        assertEquals(LAUF, frisch.lauf());
    }
}
