package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Zeitfenster des Angelwettbewerbs - die erste Uhrzeit dieses Projekts.
 *
 * <p><b>Hier liegen die Fehler, die man nicht sieht.</b> Bis zum 22.11.2026 kannte das
 * Plugin nur Tage und Dauern; ein Fenster "von 18:00 bis 19:00" bringt drei Fallen mit,
 * und jede davon hat hier ihren Satz: das Fenster ueber Mitternacht, die Zone, und die
 * Nacht der Zeitumstellung.
 */
@DisplayName("Das Zeitfenster des Angelwettbewerbs")
class WettbewerbswerteTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Der 22.11.2026 ist ein SONNTAG - geprueft, nicht angenommen. */
    private static final LocalDate SONNTAG = LocalDate.of(2026, 11, 22);

    private static Wettbewerbswerte werte(LocalTime beginn, long minuten,
                                          Set<DayOfWeek> tage) {
        return new Wettbewerbswerte(true, beginn, Duration.ofMinutes(minuten), tage,
                250L, 0L, List.of(50, 30, 20), 1, Map.of("cod", 5));
    }

    private static Instant am(LocalDate tag, int stunde, int minute) {
        return tag.atTime(stunde, minute).atZone(BERLIN).toInstant();
    }

    @Test
    @DisplayName("Der 22.11.2026 ist wirklich ein Sonntag")
    void derTagIstEinSonntag() {
        // Jede Probe unten haengt daran. Eine falsche Annahme hier machte sie alle
        // gruen oder alle rot, und zwar aus einem Grund, den niemand sucht.
        assertEquals(DayOfWeek.SUNDAY, SONNTAG.getDayOfWeek());
    }

    @Test
    @DisplayName("Ein Fenster umfasst seinen Beginn, aber NICHT sein Ende")
    void raenderDesFensters() {
        Wettbewerbswerte w = werte(LocalTime.of(18, 0), 60,
                EnumSet.allOf(DayOfWeek.class));

        assertTrue(w.laufAm(am(SONNTAG, 18, 0), BERLIN).isPresent(),
                "Punkt 18:00 ist drin");
        assertTrue(w.laufAm(am(SONNTAG, 18, 59), BERLIN).isPresent());
        assertFalse(w.laufAm(am(SONNTAG, 19, 0), BERLIN).isPresent(),
                "Punkt 19:00 ist DRAUSSEN - sonst gehoerte ein Fang bei einer Dauer von"
                        + " genau 24 Stunden zu zwei Laeufen");
        assertFalse(w.laufAm(am(SONNTAG, 17, 59), BERLIN).isPresent());
    }

    @Test
    @DisplayName("Ein Fenster UEBER MITTERNACHT gehoert zum Tag seines Beginns")
    void fensterUeberMitternacht() {
        // DIE tragende Probe dieser Klasse. 23:30 plus 60 Minuten reicht bis 00:30 des
        // naechsten Tages. Wer um 00:10 nur den heutigen Tag prueft, findet das Fenster
        // geschlossen - und der Wettbewerb endete um Mitternacht, ohne dass es irgendwo
        // steht.
        Wettbewerbswerte w = werte(LocalTime.of(23, 30), 60,
                EnumSet.allOf(DayOfWeek.class));
        LocalDate montag = SONNTAG.plusDays(1);

        Optional<Wettbewerbswerte.Lauf> spaet = w.laufAm(am(SONNTAG, 23, 45), BERLIN);
        Optional<Wettbewerbswerte.Lauf> nachts = w.laufAm(am(montag, 0, 10), BERLIN);

        assertTrue(spaet.isPresent(), "um 23:45 laeuft er");
        assertTrue(nachts.isPresent(), "um 00:10 laeuft er NOCH - das Fenster ist offen");
        assertEquals(SONNTAG, spaet.orElseThrow().schluessel());
        assertEquals(SONNTAG, nachts.orElseThrow().schluessel(),
                "und zwar DERSELBE Lauf: Der Tag ist der des Beginns, sonst haelt die"
                        + " Zuordnung ueber Mitternacht nicht zusammen");
        assertFalse(w.laufAm(am(montag, 0, 35), BERLIN).isPresent(),
                "um 00:35 ist es vorbei");
    }

    @Test
    @DisplayName("Ein Fenster ueber Mitternacht laeuft nur, wenn SEIN Tag dabei ist")
    void mitternachtUndWochentage() {
        // Der Fall, in dem beide Prueftage zaehlen: Der Wettbewerb ist nur am Sonntag,
        // das Fenster reicht in den Montag. Am Montag um 00:10 laeuft er - weil der
        // SONNTAG sein Tag ist. Am Montag um 23:45 nicht.
        Wettbewerbswerte w = werte(LocalTime.of(23, 30), 60,
                Set.of(DayOfWeek.SUNDAY));
        LocalDate montag = SONNTAG.plusDays(1);

        assertTrue(w.laufAm(am(montag, 0, 10), BERLIN).isPresent(),
                "der Lauf des Sonntags reicht in den Montag");
        assertEquals(SONNTAG, w.laufAm(am(montag, 0, 10), BERLIN)
                .orElseThrow().schluessel());
        assertFalse(w.laufAm(am(montag, 23, 45), BERLIN).isPresent(),
                "am Montag selbst gibt es keinen");
    }

    @Test
    @DisplayName("Die ZONE entscheidet, nicht UTC")
    void dieZoneEntscheidet() {
        // 18:30 Berliner Zeit ist 17:30 UTC. Ein Fenster von 18:00 bis 19:00 umfasst
        // diesen Augenblick in Berlin - in UTC gerechnet laege er davor.
        Wettbewerbswerte w = werte(LocalTime.of(18, 0), 60,
                EnumSet.allOf(DayOfWeek.class));
        Instant halbSieben = am(SONNTAG, 18, 30);

        assertTrue(w.laufAm(halbSieben, BERLIN).isPresent(), "in Berlin laeuft er");
        assertFalse(w.laufAm(halbSieben, java.time.ZoneOffset.UTC).isPresent(),
                "in UTC ist es erst 17:30 - und genau darum darf niemand die Zone"
                        + " selbst waehlen");
    }

    @Test
    @DisplayName("An einem Tag ohne Wettbewerb geht kein Fenster auf")
    void falscherWochentag() {
        Wettbewerbswerte w = werte(LocalTime.of(18, 0), 60, Set.of(DayOfWeek.FRIDAY));

        assertFalse(w.laufAm(am(SONNTAG, 18, 30), BERLIN).isPresent());
        assertTrue(w.laufAm(am(SONNTAG.minusDays(2), 18, 30), BERLIN).isPresent(),
                "zwei Tage vor dem Sonntag ist Freitag");
    }

    @Test
    @DisplayName("Eine LEERE Tagesmenge heisst: nie")
    void keineTage() {
        Wettbewerbswerte w = werte(LocalTime.of(18, 0), 60, Set.of());

        assertFalse(w.greift(), "ohne Tage greift er nicht");
        assertEquals(Optional.empty(), w.laufAm(am(SONNTAG, 18, 30), BERLIN));
    }

    @Test
    @DisplayName("In der Nacht der Zeitumstellung faellt der Lauf NICHT aus")
    void zeitumstellung() {
        // Am 29.03.2026 springt Deutschland von 02:00 auf 03:00 - die Stunde von 02:00
        // bis 02:59 gibt es nicht. Ein Fenster um 02:30 ist also eine Uhrzeit, die an
        // diesem Tag nie existiert. ZonedDateTime verschiebt sie nach vorn, und das ist
        // hier richtig: Ein Wettbewerb, der in einer Nacht im Jahr eine Stunde spaeter
        // beginnt, ist besser als einer, der ausfaellt.
        LocalDate umstellung = LocalDate.of(2026, 3, 29);
        Wettbewerbswerte w = werte(LocalTime.of(2, 30), 60,
                EnumSet.allOf(DayOfWeek.class));

        Optional<Wettbewerbswerte.Lauf> lauf = w.laufFuer(umstellung, BERLIN);

        assertTrue(lauf.isPresent(), "der Lauf findet statt");
        assertEquals(Duration.ofMinutes(60),
                Duration.between(lauf.get().beginn(), lauf.get().ende()),
                "und er ist genau so lang wie eingestellt - die Dauer wird auf den"
                        + " Zeitstrahl gerechnet und nicht auf die Wanduhr");
        // Und er ist WIRKLICH verschoben: 02:30 gibt es nicht, also beginnt er um 03:30
        // Ortszeit.
        assertEquals(LocalTime.of(3, 30),
                lauf.get().beginn().atZone(BERLIN).toLocalTime());
    }

    @Test
    @DisplayName("In der doppelten Stunde im Herbst gibt es den Lauf genau EINMAL")
    void herbstumstellung() {
        // Am 25.10.2026 springt Deutschland von 03:00 auf 02:00 - die Stunde von 02:00
        // bis 02:59 gibt es ZWEIMAL. Ein Fenster um 02:30 koennte also zweimal
        // stattfinden. Es findet einmal statt: ZonedDateTime nimmt die FRUEHERE der
        // beiden, und der Lauf hat genau eine Kennung (seinen Tag).
        LocalDate umstellung = LocalDate.of(2026, 10, 25);
        Wettbewerbswerte w = werte(LocalTime.of(2, 30), 60,
                EnumSet.allOf(DayOfWeek.class));

        Optional<Wettbewerbswerte.Lauf> lauf = w.laufFuer(umstellung, BERLIN);

        assertTrue(lauf.isPresent());
        assertEquals(umstellung, lauf.get().schluessel(),
                "EIN Lauf mit EINER Kennung - zwei Laeufe am selben Tag waeren zwei"
                        + " Toepfe, und die Ablage kennt nur einen je Tag");
    }

    @Test
    @DisplayName("Ein Fenster von genau 24 Stunden laeuft durchgehend")
    void vierundzwanzigStunden() {
        Wettbewerbswerte w = werte(LocalTime.of(0, 0), 24 * 60,
                EnumSet.allOf(DayOfWeek.class));

        assertTrue(w.laufAm(am(SONNTAG, 0, 0), BERLIN).isPresent());
        assertTrue(w.laufAm(am(SONNTAG, 23, 59), BERLIN).isPresent());
        // Und der naechste Tag ist ein NEUER Lauf, nicht derselbe.
        assertEquals(SONNTAG.plusDays(1),
                w.laufAm(am(SONNTAG.plusDays(1), 0, 0), BERLIN)
                        .orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Eine Dauer ausserhalb der Grenzen wird abgewiesen")
    void dauerGrenzen() {
        assertThrows(IllegalArgumentException.class,
                () -> werte(LocalTime.of(18, 0), 0, EnumSet.allOf(DayOfWeek.class)));
        assertThrows(IllegalArgumentException.class,
                () -> werte(LocalTime.of(18, 0), 24 * 60 + 1,
                        EnumSet.allOf(DayOfWeek.class)));
        // Und die Raender gehen noch.
        assertEquals(Duration.ofMinutes(1),
                werte(LocalTime.of(18, 0), 1, Set.of(DayOfWeek.MONDAY)).dauer());
        assertEquals(Duration.ofHours(24),
                werte(LocalTime.of(18, 0), 24 * 60, Set.of(DayOfWeek.MONDAY)).dauer());
    }

    @Test
    @DisplayName("Die Punkte: was nicht in der Tabelle steht, bekommt den Grundwert")
    void punkteFuer() {
        Wettbewerbswerte w = new Wettbewerbswerte(true, LocalTime.of(18, 0),
                Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                List.of(50, 30, 20), 2, Map.of("heart_of_the_sea", 100));

        assertEquals(100, w.punkteFuer("heart_of_the_sea"));
        assertEquals(2, w.punkteFuer("cod"), "nicht in der Tabelle - der Grundwert");
        assertEquals(0, w.punkteFuer(null));
    }

    @Test
    @DisplayName("Punkte je Fang 0 und leere Tabelle heisst: er greift nicht")
    void keinePunkteMoeglich() {
        Wettbewerbswerte w = new Wettbewerbswerte(true, LocalTime.of(18, 0),
                Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                List.of(50), 0, Map.of());

        assertFalse(w.greift(), "kein Fang kann zaehlen - das Fenster waere leer");
    }

    @Test
    @DisplayName("Punkte je Fang 0 MIT Tabelle greift - nur die Tabelle zaehlt")
    void nurTabelleZaehlt() {
        Wettbewerbswerte w = new Wettbewerbswerte(true, LocalTime.of(18, 0),
                Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                List.of(50), 0, Map.of("heart_of_the_sea", 100));

        assertTrue(w.greift());
        assertEquals(100, w.punkteFuer("heart_of_the_sea"));
        assertEquals(0, w.punkteFuer("cod"), "ein gewoehnlicher Fang zaehlt nicht");
    }

    @Test
    @DisplayName("Eine Verteilung ueber 100 Prozent wird abgewiesen")
    void verteilungUeberHundert() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswerte(true, LocalTime.of(18, 0),
                        Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                        List.of(60, 50), 1, Map.of()));
        assertTrue(wurf.getMessage().contains("mehr als den Topf"), wurf.getMessage());
    }

    @Test
    @DisplayName("Eine Verteilung UNTER 100 Prozent ist erlaubt - der Rest ist Senke")
    void verteilungUnterHundert() {
        Wettbewerbswerte w = new Wettbewerbswerte(true, LocalTime.of(18, 0),
                Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                List.of(40, 20), 1, Map.of("cod", 1));

        assertEquals(List.of(40, 20), w.verteilungProzent());
    }

    @Test
    @DisplayName("Ein Anteil von 0 wird abgewiesen - das ist ein Platz ohne Preis")
    void anteilNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswerte(true, LocalTime.of(18, 0),
                        Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                        List.of(50, 0), 1, Map.of()));
    }

    @Test
    @DisplayName("Eine leere Verteilung wird abgewiesen")
    void leereVerteilung() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswerte(true, LocalTime.of(18, 0),
                        Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                        List.of(), 1, Map.of()));
    }

    @Test
    @DisplayName("Die Tagesmenge im Satz ist eine KOPIE")
    void tageSindKopiert() {
        Set<DayOfWeek> veraenderlich = EnumSet.of(DayOfWeek.FRIDAY);
        Wettbewerbswerte w = werte(LocalTime.of(18, 0), 60, veraenderlich);

        veraenderlich.add(DayOfWeek.SATURDAY);

        assertEquals(Set.of(DayOfWeek.FRIDAY), w.tage(),
                "der Satz wird bei jedem Takt gelesen - eine veraenderliche Menge darin"
                        + " waere eine Hintertuer in den Stand");
    }

    @Test
    @DisplayName("Ein Lauf, der nicht nach seinem Beginn endet, ist nicht zu bauen")
    void laufBrauchtDauer() {
        Instant jetzt = am(SONNTAG, 18, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswerte.Lauf(SONNTAG, jetzt, jetzt));
    }

    @Test
    @DisplayName("Die ausgelieferten Werte laufen taeglich um 18:00 fuer eine Stunde")
    void vorgabe() {
        Wettbewerbswerte w = Wettbewerbswerte.VORGABE;

        assertTrue(w.greift());
        assertEquals(LocalTime.of(18, 0), w.beginn());
        assertEquals(Duration.ofMinutes(60), w.dauer());
        assertEquals(7, w.tage().size(), "taeglich, wie der Betreiber es bestimmt hat");
        assertEquals(0L, w.zuschuss(),
                "ab Werk eine reine Umverteilung und keine Geldquelle");
        assertTrue(w.laufAm(am(SONNTAG, 18, 30), BERLIN).isPresent());
    }
}
