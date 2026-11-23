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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Taten des Angelwettbewerbs - anmelden, zaehlen, abrechnen.
 *
 * <p><b>Die beiden RIEGEL sind das, was hier geprueft wird</b>, und nicht nur das
 * Ergebnis: Eine doppelte Anmeldung kostet zweimal Startgeld, eine doppelte Abrechnung
 * zahlt denselben Topf zweimal aus. Darum stehen hier Saetze, die werfende Ablagen
 * einbauen und nachsehen, ob wirklich nichts passiert ist.
 */
@DisplayName("Wettbewerbsbuch")
class WettbewerbsbuchTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final LocalDate SONNTAG = LocalDate.of(2026, 11, 22);

    /** Mitten im Fenster: 18:30 an einem Tag, an dem der Wettbewerb laeuft. */
    private static final Instant IM_FENSTER =
            SONNTAG.atTime(18, 30).atZone(BERLIN).toInstant();

    /** Nach dem Fenster: 19:30. */
    private static final Instant NACH_DEM_FENSTER =
            SONNTAG.atTime(19, 30).atZone(BERLIN).toInstant();

    /** Vor dem Fenster: 12:00. */
    private static final Instant VOR_DEM_FENSTER =
            SONNTAG.atTime(12, 0).atZone(BERLIN).toInstant();

    private static Wettbewerbswerte werte(long startgeld, long zuschuss) {
        return new Wettbewerbswerte(true, LocalTime.of(18, 0), Duration.ofMinutes(60),
                EnumSet.allOf(DayOfWeek.class), startgeld, zuschuss,
                List.of(50, 30, 20), 1, Map.of("heart_of_the_sea", 100));
    }

    /** Alles, was eine Tat braucht - damit jede Probe ihre eigene Welt hat. */
    private static final class Welt {
        final UUID anna = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        final UUID bert = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        final SpeicherKasse kasse = new SpeicherKasse(0L);
        final SpeicherWettbewerb ablage = new SpeicherWettbewerb();
        final Wirtschaftsstand stand;
        final Wettbewerbsbuch buch;

        Welt(Wettbewerbswerte werte, long guthaben) {
            stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                    .mitWettbewerb(werte)
                    .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                            Zeitwerte.VORGABE.untaetigNach())));
            for (UUID wer : List.of(anna, bert)) {
                kasse.eroeffne(wer);
                if (guthaben > 0L) {
                    kasse.gutschreiben(wer, guthaben, Buchungsgrund.BESTAND);
                }
            }
            buch = new Wettbewerbsbuch(kasse, ablage, stand);
        }
    }

    @Test
    @DisplayName("Anmelden zahlt das Startgeld und legt die Zeile an")
    void anmelden() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);

        Wettbewerbsbuch.Entscheid entscheid = welt.buch.meldeAn(welt.anna, IM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.ANGEMELDET, entscheid.bescheid());
        assertEquals(250L, entscheid.startgeld());
        assertEquals(750L, welt.kasse.guthaben(welt.anna));
        assertEquals(250L,
                welt.ablage.zeile(welt.anna, SONNTAG).orElseThrow().eingezahlt());
    }

    @Test
    @DisplayName("Die zweite Anmeldung kostet NICHTS - der Riegel haelt")
    void zweiteAnmeldungKostetNichts() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        long nachher = welt.kasse.guthaben(welt.anna);

        Wettbewerbsbuch.Entscheid zweite = welt.buch.meldeAn(welt.anna, IM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.SCHON_DABEI, zweite.bescheid());
        assertEquals(nachher, welt.kasse.guthaben(welt.anna),
                "kein zweites Startgeld - sonst kostet ein Doppelklick doppelt");
    }

    @Test
    @DisplayName("Ohne offenes Fenster geht keine Anmeldung")
    void anmeldenOhneFenster() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);

        assertEquals(Wettbewerbsbuch.Bescheid.KEIN_LAUF,
                welt.buch.meldeAn(welt.anna, VOR_DEM_FENSTER).bescheid());
        assertEquals(1_000L, welt.kasse.guthaben(welt.anna));
        assertEquals(Optional.empty(), welt.ablage.zeile(welt.anna, SONNTAG));
    }

    @Test
    @DisplayName("Bei zu wenig Geld passiert nichts")
    void zuWenigGeld() {
        Welt welt = new Welt(werte(250L, 0L), 100L);

        Wettbewerbsbuch.Entscheid entscheid = welt.buch.meldeAn(welt.anna, IM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(250L, entscheid.startgeld(), "die Absage nennt den Preis");
        assertEquals(100L, welt.kasse.guthaben(welt.anna));
        assertEquals(Optional.empty(), welt.ablage.zeile(welt.anna, SONNTAG));
    }

    @Test
    @DisplayName("Eine gratis Teilnahme geht auch ohne Geld")
    void gratisTeilnahme() {
        Welt welt = new Welt(werte(0L, 1_000L), 0L);

        assertEquals(Wettbewerbsbuch.Bescheid.ANGEMELDET,
                welt.buch.meldeAn(welt.anna, IM_FENSTER).bescheid());
        assertEquals(0L, welt.kasse.guthaben(welt.anna));
    }

    @Test
    @DisplayName("Nur Angemeldete sammeln Punkte")
    void nurAngemeldeteSammeln() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);

        assertEquals(Optional.empty(),
                welt.buch.zaehleFang(welt.anna, IM_FENSTER, "cod"),
                "ohne Anmeldung zaehlt nichts - sonst wuerde ungefragt abgebucht");

        welt.buch.meldeAn(welt.anna, IM_FENSTER);

        assertEquals(Optional.of(1),
                welt.buch.zaehleFang(welt.anna, IM_FENSTER, "cod"));
    }

    @Test
    @DisplayName("Die Punktetabelle entscheidet, was ein Fang wert ist")
    void punkteNachTabelle() {
        Welt welt = new Welt(werte(0L, 0L), 0L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);

        welt.buch.zaehleFang(welt.anna, IM_FENSTER, "cod");
        Optional<Integer> nachher =
                welt.buch.zaehleFang(welt.anna, IM_FENSTER, "heart_of_the_sea");

        assertEquals(Optional.of(101), nachher,
                "ein gewoehnlicher Fang 1, das Meeresherz 100");
    }

    @Test
    @DisplayName("Ausserhalb des Fensters zaehlt kein Fang")
    void keinFangAusserhalb() {
        Welt welt = new Welt(werte(0L, 0L), 0L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);

        assertEquals(Optional.empty(),
                welt.buch.zaehleFang(welt.anna, NACH_DEM_FENSTER, "cod"));
        assertEquals(0, welt.ablage.zeile(welt.anna, SONNTAG).orElseThrow().punkte());
    }

    @Test
    @DisplayName("Die Abrechnung verteilt den Topf nach der Rangliste")
    void abrechnen() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        welt.buch.meldeAn(welt.bert, IM_FENSTER);
        welt.buch.zaehleFang(welt.bert, IM_FENSTER, "heart_of_the_sea");
        long annaVorher = welt.kasse.guthaben(welt.anna);
        long bertVorher = welt.kasse.guthaben(welt.bert);

        Wettbewerbsbuch.Entscheid entscheid = welt.buch.rechneAb(
                welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow(),
                NACH_DEM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.ABGERECHNET, entscheid.bescheid());
        assertEquals(500L, entscheid.topf(), "zwei mal 250, kein Zuschuss");
        assertEquals(2, entscheid.teilnehmer());
        // bert ist erster (100 Punkte) und bekommt 50 Prozent, anna zweiter mit 30.
        assertEquals(bertVorher + 250L, welt.kasse.guthaben(welt.bert));
        assertEquals(annaVorher + 150L, welt.kasse.guthaben(welt.anna));
        assertEquals(2, entscheid.gewinner().size());
        assertEquals(1, entscheid.gewinner().get(0).platz());
        assertEquals(welt.bert, entscheid.gewinner().get(0).spieler());
    }

    @Test
    @DisplayName("DER RIEGEL: die zweite Abrechnung zahlt NICHTS")
    void zweiteAbrechnungZahltNichts() {
        // Der wichtigste Satz dieser Klasse. Der Takt der Spielzeitwache schlaegt jede
        // Minute; ohne diesen Riegel zahlte jeder Schlag denselben Topf erneut aus - eine
        // Geldquelle, die mit jedem Takt waechst.
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        Wettbewerbswerte.Lauf lauf =
                welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow();
        welt.buch.rechneAb(lauf, NACH_DEM_FENSTER);
        long nachDerErsten = welt.kasse.guthaben(welt.anna);

        Wettbewerbsbuch.Entscheid zweite = welt.buch.rechneAb(lauf, NACH_DEM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.SCHON_ABGERECHNET, zweite.bescheid());
        assertEquals(nachDerErsten, welt.kasse.guthaben(welt.anna),
                "kein zweiter Preis");
        // Und auch ein dritter Takt zahlt nicht.
        welt.buch.rechneAb(lauf, NACH_DEM_FENSTER.plusSeconds(60));
        assertEquals(nachDerErsten, welt.kasse.guthaben(welt.anna));
    }

    @Test
    @DisplayName("Ein Lauf ohne Teilnehmer wird nicht gemerkt und nicht gezahlt")
    void niemandDabei() {
        Welt welt = new Welt(werte(250L, 1_000L), 1_000L);
        Wettbewerbswerte.Lauf lauf =
                welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow();

        Wettbewerbsbuch.Entscheid entscheid = welt.buch.rechneAb(lauf, NACH_DEM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.NIEMAND_DABEI, entscheid.bescheid());
        assertFalse(welt.ablage.istAbgeschlossen(SONNTAG),
                "KEIN Merker: Ein Lauf ohne Teilnehmer hat nichts auszuzahlen, und eine"
                        + " Zeile je Tag fuer nichts waere Verschwendung");
        assertEquals(1_000L, welt.kasse.guthaben(welt.anna),
                "und der Zuschuss wird nicht gezahlt - es gibt niemanden, der ihn"
                        + " bekaeme");
    }

    @Test
    @DisplayName("Der ZUSCHUSS kommt zum Topf dazu")
    void zuschuss() {
        Welt welt = new Welt(werte(100L, 900L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);

        Wettbewerbsbuch.Entscheid entscheid = welt.buch.rechneAb(
                welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow(),
                NACH_DEM_FENSTER);

        assertEquals(1_000L, entscheid.topf(), "100 eingezahlt plus 900 Zuschuss");
        // Anna ist allein und bekommt 50 Prozent von 1000.
        assertEquals(500L, entscheid.gewinner().get(0).preis());
    }

    @Test
    @DisplayName("Eine werfende Ablage beim ABSCHLUSS zahlt nichts")
    void werfenderAbschluss() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        long vorher = welt.kasse.guthaben(welt.anna);
        Wettbewerbsablage kaputt = new Wettbewerbsablage() {
            @Override
            public boolean meldeAn(UUID spieler, LocalDate lauf, long startgeld) {
                return false;
            }

            @Override
            public Optional<Integer> zaehlePunkte(UUID spieler, LocalDate lauf,
                                                  int punkte) {
                return Optional.empty();
            }

            @Override
            public List<Punktzeile> zeilen(LocalDate lauf) {
                return List.of(new Punktzeile(welt.anna, lauf, 5, 250L));
            }

            @Override
            public Optional<Punktzeile> zeile(UUID spieler, LocalDate lauf) {
                return Optional.empty();
            }

            @Override
            public boolean merkeAbgeschlossen(LocalDate lauf, Instant jetzt) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public boolean istAbgeschlossen(LocalDate lauf) {
                return false;
            }
        };
        Wettbewerbsbuch buch = new Wettbewerbsbuch(welt.kasse, kaputt, welt.stand);

        Wettbewerbsbuch.Entscheid entscheid = buch.rechneAb(
                buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow(), NACH_DEM_FENSTER);

        assertEquals(Wettbewerbsbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(vorher, welt.kasse.guthaben(welt.anna),
                "ungemerkt wird nicht gezahlt - sonst zahlte jeder Takt erneut");
    }

    @Test
    @DisplayName("Ein werfender Fang nimmt dem Spieler nicht seinen Fang")
    void werfenderFang() {
        // Diese Methode laeuft in einem Fangereignis. Ein Wurf von dort liesse das
        // Angeln selbst abbrechen - ein verlorener Punkt ist dagegen ein Punkt.
        Welt welt = new Welt(werte(0L, 0L), 0L);
        Wettbewerbsablage kaputt = new Wettbewerbsablage() {
            @Override
            public boolean meldeAn(UUID spieler, LocalDate lauf, long startgeld) {
                return true;
            }

            @Override
            public Optional<Integer> zaehlePunkte(UUID spieler, LocalDate lauf,
                                                  int punkte) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public List<Punktzeile> zeilen(LocalDate lauf) {
                return List.of();
            }

            @Override
            public Optional<Punktzeile> zeile(UUID spieler, LocalDate lauf) {
                return Optional.empty();
            }

            @Override
            public boolean merkeAbgeschlossen(LocalDate lauf, Instant jetzt) {
                return true;
            }

            @Override
            public boolean istAbgeschlossen(LocalDate lauf) {
                return false;
            }
        };
        Wettbewerbsbuch buch = new Wettbewerbsbuch(welt.kasse, kaputt, welt.stand);

        assertEquals(Optional.empty(), buch.zaehleFang(welt.anna, IM_FENSTER, "cod"),
                "leer und KEIN Wurf");
    }

    @Test
    @DisplayName("Ein ausgeschalteter Wettbewerb tut nichts")
    void ausgeschaltet() {
        Welt welt = new Welt(werte(250L, 0L).mitAn(false), 1_000L);

        assertEquals(Wettbewerbsbuch.Bescheid.AUS,
                welt.buch.meldeAn(welt.anna, IM_FENSTER).bescheid());
        assertEquals(Optional.empty(),
                welt.buch.zaehleFang(welt.anna, IM_FENSTER, "cod"));
        assertEquals(Optional.empty(), welt.buch.faelligerLauf(NACH_DEM_FENSTER));
        assertEquals(1_000L, welt.kasse.guthaben(welt.anna));
    }

    @Test
    @DisplayName("Der faellige Lauf ist der, dessen Fenster ZU ist")
    void faelligerLauf() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);

        // GEMESSEN am 22.11.2026, nachdem diese Probe zweimal das Gegenteil behauptet
        // hatte: Faellig ist IMMER der jüngste Lauf, dessen Fenster zu ist - und das ist
        // vor und waehrend des heutigen Fensters der von GESTERN.
        //
        // Das ist richtig und sogar noetig: War der Server ueber Nacht aus, wurde der
        // Lauf von gestern nie abgerechnet, und die Startgelder lagen im Topf. Beim
        // ersten Takt nach dem Start holt er es nach, ganz gleich, wie spaet es ist.
        //
        // Dass derselbe Lauf danach bei jedem Takt genannt wird, bis das heutige Fenster
        // vorbei ist, ist der Preis dafuer - und er ist gering: Die Antwort ist eine
        // indizierte Abfrage auf wettbewerb_lauf, und sie faellt einmal je Minute. Dass
        // dabei nichts zweimal ausgezahlt wird, haelt der Riegel in der Ablage.
        assertEquals(SONNTAG.minusDays(1),
                welt.buch.faelligerLauf(VOR_DEM_FENSTER).orElseThrow().schluessel(),
                "vor dem heutigen Fenster: der Lauf von gestern wartet noch");
        assertEquals(SONNTAG.minusDays(1),
                welt.buch.faelligerLauf(IM_FENSTER).orElseThrow().schluessel(),
                "MITTEN im heutigen Fenster auch - das heutige ist noch nicht vorbei");
        assertEquals(SONNTAG,
                welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow().schluessel(),
                "danach ist das HEUTIGE das jüngste vorbeigegangene");
    }

    @Test
    @DisplayName("Die Auskunft sagt Topf, Stand und Guthaben - ohne etwas zu tun")
    void auskunft() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);

        Wettbewerbsbuch.Auskunft ohne = welt.buch.frage(welt.anna, IM_FENSTER);
        assertTrue(ohne.laeuft());
        assertTrue(ohne.lauf().isPresent(), "das Fenster ist offen");
        assertFalse(ohne.dabei());
        assertEquals(1_000L, ohne.guthaben());
        assertEquals(250L, ohne.startgeld());
        assertEquals(Optional.empty(), welt.ablage.zeile(welt.anna, SONNTAG),
                "die Auskunft darf nichts anlegen");

        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        welt.buch.zaehleFang(welt.anna, IM_FENSTER, "heart_of_the_sea");

        Wettbewerbsbuch.Auskunft mit = welt.buch.frage(welt.anna, IM_FENSTER);
        assertTrue(mit.dabei());
        assertEquals(100, mit.eigene().orElseThrow().punkte());
        assertEquals(250L, mit.topf());
        assertEquals(750L, mit.guthaben());
        assertEquals(1, mit.rangliste().size());
    }

    @Test
    @DisplayName("Die Werte kommen beim GEBRAUCH aus dem Stand - Neuladen wirkt")
    void neuladenWirkt() {
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        assertTrue(welt.buch.werte().greift());

        welt.stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitWettbewerb(werte(250L, 0L).mitAn(false))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));

        assertFalse(welt.buch.werte().greift(), "das Neuladen hat den Wettbewerb nicht"
                + " erreicht");
        assertEquals(Wettbewerbsbuch.Bescheid.AUS,
                welt.buch.meldeAn(welt.anna, IM_FENSTER).bescheid());
    }

    @Test
    @DisplayName("Die Zone kommt aus dem Stand und nicht aus der JVM")
    void zoneAusDemStand() {
        Welt welt = new Welt(werte(250L, 0L), 0L);

        assertEquals(BERLIN, welt.buch.zone());
    }

    @Test
    @DisplayName("Ohne seine drei Teile oder ohne Zeitpunkt geht nichts")
    void pflichtangaben() {
        Welt welt = new Welt(werte(250L, 0L), 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbsbuch(null, welt.ablage, welt.stand));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbsbuch(welt.kasse, null, welt.stand));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbsbuch(welt.kasse, welt.ablage, null));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.meldeAn(null, IM_FENSTER));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.meldeAn(welt.anna, null));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.frage(null, IM_FENSTER));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.faelligerLauf(null));
        assertThrows(IllegalArgumentException.class,
                () -> welt.buch.rechneAb(null, IM_FENSTER));
    }

    @Test
    @DisplayName("Ein ABGERECHNET ohne Teilnehmer ist nicht zu bauen")
    void abgerechnetBrauchtTeilnehmer() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbsbuch.Entscheid(
                        Wettbewerbsbuch.Bescheid.ABGERECHNET, 0L, 0, Optional.empty(),
                        List.of(), 0L));
    }

    @Test
    @DisplayName("Startgeld und Preis stehen als ZWEI Gruende im Buch")
    void zweiGruendeImBuch() {
        // Die Differenz ihrer Summen IST der Zuschuss des Betreibers - das ist die
        // Zahl, die er im Wirtschaftsbericht sehen will.
        Welt welt = new Welt(werte(250L, 0L), 1_000L);
        welt.buch.meldeAn(welt.anna, IM_FENSTER);
        welt.buch.rechneAb(welt.buch.faelligerLauf(NACH_DEM_FENSTER).orElseThrow(),
                NACH_DEM_FENSTER);

        List<Buchungsgrund> gruende = new java.util.ArrayList<>();
        for (Buchung buchung : welt.kasse.verlauf(welt.anna, 20)) {
            if (buchung.grund() == Buchungsgrund.WETTBEWERB_START
                    || buchung.grund() == Buchungsgrund.WETTBEWERB_PREIS) {
                gruende.add(buchung.grund());
            }
        }
        assertTrue(gruende.contains(Buchungsgrund.WETTBEWERB_START), gruende.toString());
        assertTrue(gruende.contains(Buchungsgrund.WETTBEWERB_PREIS), gruende.toString());
    }
}
