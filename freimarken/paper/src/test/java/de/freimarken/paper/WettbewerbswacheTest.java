package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherWettbewerb;
import de.freimarken.kern.Wettbewerbsbuch;
import de.freimarken.kern.Wettbewerbswerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code imTakt} - der Weg, ueber den das Geld des Angelwettbewerbs fliesst.
 *
 * <p><b>Diese Datei prueft, was im Betrieb einmal am Tag geschieht</b> und was ohne sie
 * ungeprueft waere: die Ansage beim Fensterbeginn (genau einmal, nicht bei jedem Takt)
 * und die Abrechnung beim Fensterende (genau einmal, denn der Takt schlaegt jede Minute).
 *
 * <p>Die UHR ist dabei eine Attrappe, die die Probe stellt. Eine echte machte diese
 * Proben zwischen 18:00 und 19:00 gruen und sonst rot.
 *
 * <p>Das FANGEREIGNIS selbst laeuft hier nicht: Dafuer braeuchte es den Ereignisbus des
 * Servers, und dass die Wache auf {@code MONITOR} haengt und angemeldet ist, haelt
 * {@code GrenzeTest} 31ax am Quelltext.
 */
@DisplayName("Die Wettbewerbswache im Takt")
class WettbewerbswacheTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final LocalDate SONNTAG = LocalDate.of(2026, 11, 22);

    private static final UUID ANNA = Spielerprobe.kennungAus(514);

    private SpeicherKasse kasse;
    private SpeicherWettbewerb ablage;
    private Wirtschaftsstand stand;
    private Spielerprobe anna;
    private List<String> protokolliert;
    private Logger protokoll;

    /** Eine Uhr, die die Probe stellt. */
    private static final class Stelluhr extends Clock {
        private Instant jetzt;

        Stelluhr(Instant jetzt) {
            this.jetzt = jetzt;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }

    private static Instant am(int stunde, int minute) {
        return SONNTAG.atTime(stunde, minute).atZone(BERLIN).toInstant();
    }

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        anna = new Spielerprobe("Anna", ANNA);
        kasse = new SpeicherKasse(0L);
        ablage = new SpeicherWettbewerb();
        kasse.eroeffne(ANNA);
        kasse.gutschreiben(ANNA, 1_000L, Buchungsgrund.BESTAND);
        stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitWettbewerb(new Wettbewerbswerte(true, LocalTime.of(18, 0),
                        Duration.ofMinutes(60), EnumSet.allOf(DayOfWeek.class), 250L, 0L,
                        List.of(100), 1, Map.of()))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        protokolliert = new ArrayList<>();
        protokoll = Logger.getLogger("WettbewerbswacheTest-" + UUID.randomUUID());
        protokoll.setUseParentHandlers(false);
        protokoll.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokolliert.add(satz.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private Wettbewerbswache wache(Stelluhr uhr) {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        return new Wettbewerbswache(buch, uhr, Sprachprobe.spracheAusDemJar(),
                wer -> "Anna", () -> List.of(anna.spieler()),
                wer -> wer.equals(ANNA) ? anna.spieler() : null, protokoll);
    }

    @Test
    @DisplayName("1 - die Ansage kommt GENAU EINMAL je Fenster, nicht bei jedem Takt")
    void ansageNurEinmal() {
        // Der Takt schlaegt jede Minute. Ohne den Merker stuende die Ansage sechzigmal
        // im Chat - und das ist die Art Fehler, die im Spiel sofort auffaellt und in
        // keiner Probe.
        Stelluhr uhr = new Stelluhr(am(18, 0));
        Wettbewerbswache wache = wache(uhr);

        wache.imTakt();
        int nachDemErsten = anna.chatBild().size();
        uhr.jetzt = am(18, 1);
        wache.imTakt();
        uhr.jetzt = am(18, 30);
        wache.imTakt();

        assertEquals(nachDemErsten, anna.chatBild().size(),
                "nur die EINE Ansage - der Takt schlaegt sechzigmal je Fenster");
        assertTrue(anna.chatBild().stream()
                        .anyMatch(z -> z.contains("Der Angelwettbewerb hat begonnen")),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("2 - vor dem Fenster sagt sie nichts an")
    void keineAnsageVorDemFenster() {
        Stelluhr uhr = new Stelluhr(am(17, 59));

        wache(uhr).imTakt();

        assertFalse(anna.chatBild().stream()
                        .anyMatch(z -> z.contains("hat begonnen")),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("3 - die Abrechnung zahlt GENAU EINMAL, auch bei zehn Takten")
    void abrechnungNurEinmal() {
        // DIE tragende Probe dieser Klasse: Ohne den Riegel zahlte jeder Takt denselben
        // Topf erneut aus - eine Geldquelle, die mit jedem Takt waechst.
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        buch.meldeAn(ANNA, am(18, 30));
        long nachDerAnmeldung = kasse.guthaben(ANNA);
        Stelluhr uhr = new Stelluhr(am(19, 0));
        Wettbewerbswache wache = wache(uhr);

        for (int takt = 0; takt < 10; takt++) {
            uhr.jetzt = am(19, takt);
            wache.imTakt();
        }

        assertEquals(nachDerAnmeldung + 250L, kasse.guthaben(ANNA),
                "GENAU einmal der Topf von 250 - nicht zehnmal");
        assertEquals(1, protokolliert.size(),
                "und genau EINE Zeile im Protokoll: " + protokolliert);
    }

    @Test
    @DisplayName("4 - die Protokollzeile nennt Tag, Topf, Teilnehmer und jeden Sieger")
    void protokollzeile() {
        // Die Tabelle wettbewerb_lauf haelt Topf und Teilnehmerzahl, aber nicht, WER was
        // bekam. Ein Betreiber, der gefragt wird "warum habe ich 250 Mark bekommen",
        // braucht diese Zeile.
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        buch.meldeAn(ANNA, am(18, 30));
        buch.zaehleFang(ANNA, am(18, 31), "cod");
        Stelluhr uhr = new Stelluhr(am(19, 0));

        wache(uhr).imTakt();

        assertEquals(1, protokolliert.size(), protokolliert.toString());
        String zeile = protokolliert.get(0);
        assertTrue(zeile.contains("2026-11-22"), zeile);
        assertTrue(zeile.contains("Topf 250 Mark"), zeile);
        assertTrue(zeile.contains("1 Teilnehmer"), zeile);
        assertTrue(zeile.contains(ANNA.toString()), zeile);
        assertTrue(zeile.contains("1 Punkte, 250 Mark"), zeile);
    }

    @Test
    @DisplayName("5 - der Sieger liest seinen Gewinn, und alle lesen die Siegerliste")
    void ansageDesErgebnisses() {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        buch.meldeAn(ANNA, am(18, 30));
        Stelluhr uhr = new Stelluhr(am(19, 0));

        wache(uhr).imTakt();

        List<String> gelesen = anna.chatBild();
        assertTrue(gelesen.stream().anyMatch(z -> z.contains("Platz 1 im"
                + " Angelwettbewerb")), gelesen.toString());
        assertTrue(gelesen.stream().anyMatch(z -> z.contains("ist zu Ende")),
                gelesen.toString());
        assertTrue(gelesen.stream().anyMatch(z -> z.contains("1. Anna")),
                gelesen.toString());
    }

    @Test
    @DisplayName("6 - ein Lauf ohne Teilnehmer wird nicht angesagt und nicht gezahlt")
    void niemandDabei() {
        Stelluhr uhr = new Stelluhr(am(19, 0));

        wache(uhr).imTakt();

        assertEquals(List.of(), protokolliert,
                "kein abgerechneter Lauf, keine Zeile");
        assertFalse(anna.chatBild().stream().anyMatch(z -> z.contains("ist zu Ende")),
                anna.chatBild().toString());
        assertEquals(1_000L, kasse.guthaben(ANNA));
    }

    @Test
    @DisplayName("7 - sie wirft NIE - ein Wurf beendete den Zeitgeber")
    void wirftNie() {
        // Eine Ausnahme von hier beendete den Spielzeittakt, und der Verlust waere die
        // Spielzeit aller Spieler bis zum naechsten Neustart. Geprueft mit einem Buch,
        // dessen Werte werfen.
        // Ein echtes Buch mit einem Lauf, der faellig ist - die WERFENDEN Teile sind
        // die drei Naehte darunter. Wettbewerbsbuch ist final, also ist eine
        // Unterklasse nicht der Weg; sie waere auch der falsche, denn der Wurf soll von
        // dort kommen, wo im Betrieb Bukkit steht.
        Wettbewerbsbuch mitLauf = new Wettbewerbsbuch(kasse, ablage, stand);
        mitLauf.meldeAn(ANNA, am(18, 30));
        Stelluhr uhr = new Stelluhr(am(19, 0));
        Wettbewerbswache wache = new Wettbewerbswache(mitLauf, uhr,
                Sprachprobe.spracheAusDemJar(), wer -> {
                    throw new IllegalStateException("Namensnaht kaputt");
                }, () -> {
                    throw new IllegalStateException("Spielerliste kaputt");
                }, wer -> null, protokoll);

        // KEIN assertThrows: Der Satz ist, dass NICHTS geworfen wird.
        wache.imTakt();
    }

    @Test
    @DisplayName("8 - ohne ihre sieben Teile ist sie nicht zu bauen")
    void pflichtangaben() {
        Wettbewerbsbuch buch = new Wettbewerbsbuch(kasse, ablage, stand);
        Clock uhr = new Stelluhr(am(18, 0));
        Sprache sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(null, uhr, sprache, wer -> "x",
                        List::of, wer -> null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, null, sprache, wer -> "x",
                        List::of, wer -> null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, uhr, null, wer -> "x",
                        List::of, wer -> null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, uhr, sprache, null,
                        List::of, wer -> null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, uhr, sprache, wer -> "x",
                        null, wer -> null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, uhr, sprache, wer -> "x",
                        List::of, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wettbewerbswache(buch, uhr, sprache, wer -> "x",
                        List::of, wer -> null, null));
    }
}
