package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitregel;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Abrechnung der Spielzeit - ohne Server.
 *
 * <p>Moeglich, weil {@link Spielzeitbuch} kein Bukkit kennt: Es bekommt eine Uhr und eine
 * Karte "wer ist da und wie lange schon untaetig" und sonst nichts. Damit ist genau das
 * pruefbar, was am Server nicht zu pruefen ist, ohne Stunden zu warten - der Rest unter
 * einer Sekunde, die Mitternacht und die verstellte Uhr.
 */
class SpielzeitbuchTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final UUID BERT = UUID.fromString("0000be27-0000-0000-0000-000000000002");

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    private static final Instant START = Instant.parse("2026-11-03T12:00:00Z");

    /** Derselbe Augenblick als Tag in Europe/Berlin. */
    private static final LocalDate TAG = LocalDate.of(2026, 11, 3);

    /**
     * Die Pruefuhr - dieselbe Bauart wie im Kern: fest UTC, von Hand weiterzustellen.
     *
     * <p>Die Zone kommt getrennt aus {@link Zeitwerte}, deshalb braucht
     * {@code withZone} hier keine Umsetzung.
     *
     * <p>Nicht {@code private}, damit {@link SpielzeitBefehleTest} dieselbe Uhr nimmt: Zwei
     * Pruefuhren im selben Paket waeren zwei Stellen, an denen dieselbe Entscheidung
     * (fest UTC, kein {@code withZone}) gepflegt werden muss.
     */
    static final class StellUhr extends Clock {

        private Instant jetzt;

        StellUhr(Instant anfang) {
            this.jetzt = anfang;
        }

        void weiter(Duration dauer) {
            jetzt = jetzt.plus(dauer);
        }

        void setze(Instant wohin) {
            jetzt = wohin;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("Die Zone kommt aus den Zeitwerten");
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private StellUhr uhr;
    private SpeicherSpielzeit ablage;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new StellUhr(START);
        ablage = new SpeicherSpielzeit();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    private Logger eigenesProtokoll() {
        // Eigener Name je Probe: Logger leben im LogManager weiter, und ein geteilter
        // Name liesse die Zeilen zweier Proben durcheinanderlaufen.
        Logger eigener = Logger.getLogger("SpielzeitbuchProbe-" + UUID.randomUUID());
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

    private Spielzeitbuch buch(Zeitwerte zeit) {
        return new Spielzeitbuch(stand(zeit), ablage, uhr, protokoll);
    }

    private Spielzeitbuch buch(Zeitwerte zeit, Spielzeitablage andere) {
        return new Spielzeitbuch(stand(zeit), andere, uhr, protokoll);
    }

    private static Wirtschaftsstand stand(Zeitwerte zeit) {
        return new Wirtschaftsstand(new Wirtschaftswerte(Preisstaffel.VORGABE,
                Wirtschaftswerte.VORGABE_STARTGUTHABEN, Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, zeit,
                Berufswerte.VORGABE));
    }

    /** Wie viel fuer diesen Spieler insgesamt geschrieben ist. */
    private long gesamt(UUID wer) {
        return ablage.lies(wer, TAG).sekundenGesamt();
    }

    private static Map<UUID, Duration> aktiv(UUID... wer) {
        Map<UUID, Duration> karte = new LinkedHashMap<>();
        for (UUID einer : wer) {
            karte.put(einer, Duration.ZERO);
        }
        return karte;
    }

    private boolean protokollNennt(String stueck) {
        return protokollzeilen.stream()
                .anyMatch(satz -> satz.getMessage().contains(stueck));
    }

    private long protokollZaehlt(String stueck) {
        return protokollzeilen.stream()
                .filter(satz -> satz.getMessage().contains(stueck))
                .count();
    }

    /** Was fuer diesen Spieler gemessen ist, aber noch nicht geschrieben wurde. */
    private static long wartendeSekunden(Spielzeitbuch buch, UUID wer) {
        return buch.wartende(wer).stream()
                .mapToLong(Spielzeitablage.Zuschlag::sekunden)
                .sum();
    }

    /** Eine Ablage, die bei jedem Schreibzug wirft - wie eine weggezogene Datenbank. */
    private static Spielzeitablage kaputteAblage() {
        return new Spielzeitablage() {
            @Override
            public void schreibeFort(List<Zuschlag> zuschlaege) {
                throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                        + zuschlaege.size() + " Zuschlaege, nicht geschrieben)");
            }

            @Override
            public Spielzeitstand lies(UUID spieler, LocalDate heute) {
                return Spielzeitstand.leer(spieler, heute);
            }
        };
    }

    /**
     * Eine Ablage, die ihre Schreibzuege zaehlt und auf Wunsch wirft.
     *
     * <p>Das Zaehlen ist hier die ganze Probe: Mit toter Datenbank ist nicht der Fehler
     * teuer, sondern der VERSUCH - er haelt den Hauptfaden rund fuenf Sekunden an. Ob die
     * Sperre wirkt, laesst sich darum nur an der Zahl der Zugriffe sehen.
     */
    private static final class ZaehlendeAblage implements Spielzeitablage {

        private final SpeicherSpielzeit echt = new SpeicherSpielzeit();

        private int aufrufe;

        private boolean wirft;

        @Override
        public void schreibeFort(List<Zuschlag> zuschlaege) {
            aufrufe++;
            if (wirft) {
                throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                        + zuschlaege.size() + " Zuschlaege, nicht geschrieben)");
            }
            echt.schreibeFort(zuschlaege);
        }

        @Override
        public Spielzeitstand lies(UUID spieler, LocalDate heute) {
            return echt.lies(spieler, heute);
        }
    }

    @Test
    @DisplayName("Ein Takt schreibt die verstrichene Zeit, nicht die Taktlaenge")
    void derTaktZaehltDieUhr() {
        // Gerechnet wird die Differenz zweier Uhrablesungen. Ein verspaeteter Takt zaehlt
        // damit richtig - ein Takt, der die Takte zaehlte, verlore die Verspaetung.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(95));

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(95L, gesamt(ANNA), "95 Sekunden sind vergangen, nicht 60");
    }

    @Test
    @DisplayName("Der Rest unter einer Sekunde bleibt an der Marke stehen")
    void derRestBleibtStehen() {
        // Der groesste stille Verlust, wenn die Marke auf jetzt springt: eine halbe
        // Sekunde je Takt. Ueber die 40 Stunden bis Buerger sind das rund 20 Minuten.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofMillis(1500));
        buch.schreibeFort(aktiv(ANNA));
        uhr.weiter(Duration.ofMillis(1500));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(3L, gesamt(ANNA),
                "nach 3 Sekunden sind 3 Sekunden gezaehlt - nicht 2");
    }

    @Test
    @DisplayName("Ein untaetiger Takt wird nicht angespart und spaeter ausgezahlt")
    void untaetigkeitWirdNichtAngespart() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofMinutes(30));

        buch.schreibeFort(Map.of(ANNA, Duration.ofMinutes(30)));
        assertEquals(0L, gesamt(ANNA), "eine halbe Stunde untaetig zaehlt nicht");

        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(60L, gesamt(ANNA),
                "die Marke war vorgerueckt - die halbe Stunde kommt nicht nach");
    }

    @Test
    @DisplayName("Untaetig-minuten 0 heisst: Abwesenheit zaehlt mit")
    void nullSchaltetDieRegelAus() {
        Zeitwerte aus = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60),
                Duration.ZERO);
        Spielzeitbuch buch = buch(aus);
        buch.beginne(ANNA);
        // Fuenf Minuten und nicht dreissig: Ueber zehn Taktlaengen greift die Klemme gegen
        // eine verstellte Uhr, und dann prueft die Probe die falsche Regel.
        uhr.weiter(Duration.ofMinutes(5));

        buch.schreibeFort(Map.of(ANNA, Duration.ofMinutes(5)));

        assertEquals(300L, gesamt(ANNA), "mit 0 zaehlt auch der Nachtlang mit");
    }

    @Test
    @DisplayName("Die Untaetigkeitsgrenze haengt am eingestellten Wert")
    void schrankeHaengtAnDerGrenze() {
        Zeitwerte streng = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60),
                Duration.ofMinutes(2));
        Spielzeitbuch buch = buch(streng);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(60));

        buch.schreibeFort(Map.of(ANNA, Duration.ofMinutes(3)));

        assertEquals(0L, gesamt(ANNA),
                "drei Minuten untaetig liegen ueber den eingestellten zwei");
    }

    @Test
    @DisplayName("Abmelden rechnet ab und entfernt die Marke")
    void abmeldenRechnetAb() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(45));

        buch.schliesse(ANNA, Duration.ZERO);

        assertEquals(45L, gesamt(ANNA));
        assertEquals(0, buch.offeneMarken(), "die Marke eines Abgemeldeten bleibt nicht");

        // Die Zeit zwischen zwei Sitzungen darf nicht mitzaehlen.
        uhr.weiter(Duration.ofHours(8));
        buch.schliesse(ANNA, Duration.ZERO);

        assertEquals(45L, gesamt(ANNA), "ohne Marke gibt es nichts abzurechnen");
    }

    @Test
    @DisplayName("Abschalten rechnet jede offene Sitzung ab, auch ohne gemessene Untaetigkeit")
    void abschaltenSchreibtAllesWeg() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        uhr.weiter(Duration.ofSeconds(20));

        // BERT fehlt in der Karte - beim Herunterfahren gibt es nicht zu jeder Marke noch
        // ein Spielerobjekt. Er gilt dann als aktiv und verliert seine Zeit nicht.
        buch.schliesseAlles(aktiv(ANNA));

        assertEquals(20L, gesamt(ANNA));
        assertEquals(20L, gesamt(BERT), "eine fehlende Messung kostet keine Spielzeit");
        assertEquals(0, buch.offeneMarken());
    }

    @Test
    @DisplayName("Ein Takt ueber Mitternacht faellt auf zwei Tage")
    void mitternachtTeiltDenTakt() {
        // 23:00 UTC ist in Europe/Berlin genau Mitternacht. Der Takt beginnt 30 s davor
        // und endet 30 s danach.
        uhr.setze(Instant.parse("2026-11-03T22:59:30Z"));
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(60));

        buch.schreibeFort(aktiv(ANNA));

        Spielzeitstand neuerTag = ablage.lies(ANNA, LocalDate.of(2026, 11, 4));
        Spielzeitstand alterTag = ablage.lies(ANNA, LocalDate.of(2026, 11, 3));
        assertEquals(60L, neuerTag.sekundenGesamt(), "verloren geht nichts");
        assertEquals(30L, neuerTag.sekundenHeute(),
                "nur die halbe Minute nach Mitternacht gehoert dem neuen Tag");
        assertEquals(30L, alterTag.sekundenHeute(),
                "die andere Haelfte bleibt beim alten Tag");
    }

    @Test
    @DisplayName("Ein Sprung nach vorn wird geklemmt und gemeldet")
    void sprungNachVornWirdGeklemmt() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofHours(2));

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(600L, gesamt(ANNA), "zehn Taktlaengen, der Rest wird weggeworfen");
        assertTrue(protokollNennt("GEKLEMMT"),
                "eine verstellte Uhr muss nachlesbar sein: " + protokollzeilen);
    }

    @Test
    @DisplayName("Die Klemme haengt am eingestellten Takt")
    void klemmeHaengtAmTakt() {
        Zeitwerte langsam = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(120),
                Duration.ofMinutes(10));
        Spielzeitbuch buch = buch(langsam);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofHours(2));

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(1200L, gesamt(ANNA),
                "zehn Taktlaengen von 120 s - sonst waere die Einstellung wirkungslos");
    }

    @Test
    @DisplayName("Eine zurueckgestellte Uhr zaehlt nichts und meldet es")
    void zurueckgestellteUhr() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.setze(START.minusSeconds(300));

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(0L, gesamt(ANNA), "negative Spielzeit gibt es nicht");
        assertTrue(protokollNennt("UHR_ZURUECKGESTELLT"), protokollzeilen.toString());

        // Die Marke steht jetzt auf der zurueckgestellten Zeit, also zaehlt es ab dort
        // normal weiter - und nicht erst wieder in fuenf Minuten.
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(60L, gesamt(ANNA));
    }

    @Test
    @DisplayName("Ein Fehler beim Schreiben verliert die Zeit und nennt sie im Protokoll")
    void fehlerBeimSchreibenWirdGemeldetUndNichtWiederholt() {
        // Wiederholen waere schlimmer als verlieren: Fortschreiben ist additiv, ein
        // zweiter Versuch mit derselben Liste zaehlt doppelt - und zu viel Spielzeit gibt
        // einen Rang zu frueh.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, kaputteAblage());
        buch.beginne(ANNA);
        buch.beginne(BERT);
        uhr.weiter(Duration.ofSeconds(30));

        buch.schreibeFort(aktiv(ANNA, BERT));

        assertTrue(protokollNennt("60 Sekunden von 2 Spieler(n)"),
                "die Zeile muss die verlorenen Sekunden und die Spielerzahl nennen: "
                        + protokollzeilen);
        assertEquals(2, buch.offeneMarken(),
                "die Sitzungen laufen weiter - verloren ist nur dieser Takt");
        // Und die Zeile darf die Sperre nicht als unbedingte Zusage verkaufen: Waehrend
        // des Fensters liegen die Sekunden MEHRERER Taktlaengen je Spieler nur im
        // Arbeitsspeicher. Eine Zeile, die "gehen nicht verloren" sagt und den Absturz
        // nicht nennt, laesst den Betreiber mitten im Ausfall aufhoeren zu suchen.
        assertTrue(protokollNennt("Stuerzt der Server in diesem Fenster ab"),
                "die Zeile muss den Preis der Sperre nennen: " + protokollzeilen);
    }

    @Test
    @DisplayName("Die offene Sitzung wird angezeigt, aber nicht geschrieben")
    void offeneSitzungWirdNichtGeschrieben() {
        // lies() kennt nur, was geschrieben ist. Ohne diese Zahl laese ein Spieler, der
        // gerade beigetreten ist, eine 0 neben "wird gezaehlt".
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(30));

        assertEquals(30L, buch.offeneSitzung(ANNA, Duration.ZERO).sekunden());
        assertEquals(0L, gesamt(ANNA), "nachsehen schreibt nichts");

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(30L, gesamt(ANNA), "nachsehen hat die Marke nicht vorgerueckt");
    }

    @Test
    @DisplayName("Wer zwischen zwei Takten dazukommt, faellt nicht durch das Raster")
    void spielerOhneMarkeZaehltAbJetzt() {
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);

        buch.schreibeFort(aktiv(ANNA));
        assertEquals(0L, gesamt(ANNA), "ohne Marke ist nichts verstrichen");
        assertEquals(1, buch.offeneMarken(), "aber jetzt hat er eine");

        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(60L, gesamt(ANNA));
    }

    @Test
    @DisplayName("Ein Beitritt setzt die Marke auf jetzt - auch wenn schon eine da ist")
    void beginneSetztDieMarkeNeu() {
        // Die sichere Richtung: Ueberlebte eine Marke je ihre Sitzung, zaehlte der Abstand
        // zwischen zwei Sitzungen als Spielzeit - bis zu zehn Taktlaengen, als Ausgang
        // GEZAEHLT und damit ohne Protokollzeile. Beim Start kostet das nichts, weil die
        // Karte dort leer ist und erst aus den anwesenden Spielern gefuellt wird.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(40));
        buch.beginne(ANNA);

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(0L, gesamt(ANNA), "ab der neuen Marke ist nichts verstrichen");
        assertEquals(1, buch.offeneMarken());
    }

    @Test
    @DisplayName("Nach einem Fehlschlag wird nicht geschrieben - verloren ist nur der"
            + " versuchte Takt")
    void nachEinemFehlschlagWirdAusgesetzt() {
        // Mit totem Datenbankrechner kostet jeder Versuch rund 5 s Stillstand im
        // Hauptfaden - erst die Pruefung der alten Verbindung, dann der Aufbau einer neuen.
        // Ohne Sperre waere das bei jedem Takt erneut so, ohne dass ein Spieler etwas tut.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(60));

        buch.schreibeFort(aktiv(ANNA));

        assertEquals(1, zaehlt.aufrufe, "der erste Takt versucht es");
        assertTrue(buch.gesperrt(), "danach ist die Ablage als tot bekannt");

        // Die Datenbank ist wieder da, aber der naechste Schlag ruehrt sie nicht an.
        zaehlt.wirft = false;
        int ausgesetzte = 0;
        while (buch.gesperrt()) {
            uhr.weiter(Duration.ofSeconds(60));
            buch.schreibeFort(aktiv(ANNA));
            ausgesetzte++;
            assertEquals(1, zaehlt.aufrufe,
                    "ein ausgesetzter Schlag fasst die Ablage nicht an");
            assertTrue(ausgesetzte < Spielzeitregel.KLEMME_IN_TAKTEN,
                    "die Sperre MUSS enden, und zwar lange bevor eine Messung so alt ist,"
                            + " dass die Klemme sie fuer eine verstellte Uhr haelt");
        }

        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(2, zaehlt.aufrufe, "danach wird wieder geschrieben");
        // Der gescheiterte erste Takt ist verloren (es wird NIE wiederholt). Die
        // ausgesetzten Schlaege sind es NICHT: Sie haben GERECHNET, nur nicht geschrieben,
        // und dieser eine Schreibzug nimmt ihren Stapel mit - jeden Zuschlag genau einmal.
        assertEquals(60L * (ausgesetzte + 1), zaehlt.lies(ANNA, TAG).sekundenGesamt(),
                "die ausgesetzte Zeit kommt einmal nach, nicht zweimal und nicht nie");
    }

    @Test
    @DisplayName("Scheitert der Zug nach der Sperre, nennt die Verlustzeile den ganzen"
            + " Stapel")
    void fehlschlagHinterDerSperreNenntDenGanzenStapel() {
        // Das ist der groesste Verlust, den dieses Teilstueck ueberhaupt melden kann: Der
        // erste Zug scheitert, die naechsten Schlaege rechnen und legen ab, und dann
        // scheitert auch der Zug, der den gewachsenen Stapel mitnehmen soll - auf einen
        // Schlag (Aussetzer + 1) Taktlaengen je Spieler. Die Verlustzeile ist der einzige
        // Beleg darueber, und ihre Zahl ist die, nach der der Betreiber sucht. Bisher
        // scheiterte in JEDER Probe, die melde() erreicht, der ERSTE Zug; ob die Zeile den
        // ganzen Stapel oder nur die Zuschlaege des laufenden Aufrufs nennt, war damit von
        // keiner Zusicherung gedeckt.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(60));

        buch.schreibeFort(aktiv(ANNA));
        assertTrue(buch.gesperrt(), "sonst prueft die Probe den falschen Zustand");

        // Die Datenbank bleibt tot. Die ausgesetzten Schlaege rechnen und legen ab.
        int ausgesetzte = 0;
        while (buch.gesperrt()) {
            uhr.weiter(Duration.ofSeconds(60));
            buch.schreibeFort(aktiv(ANNA));
            ausgesetzte++;
            assertTrue(ausgesetzte < Spielzeitregel.KLEMME_IN_TAKTEN,
                    "die Sperre MUSS enden, sonst dreht diese Probe endlos");
        }
        assertEquals(60L * ausgesetzte, wartendeSekunden(buch, ANNA),
                "jeder ausgesetzte Schlag liegt auf dem Stapel");

        // Und jetzt der Zug, der ihn mitnehmen soll - er scheitert wieder.
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA));

        assertEquals(2, zaehlt.aufrufe, "genau zwei Versuche: der erste und dieser");
        // Die Zahl aus der Klemme und nicht getippt: Aendert sich die Zahl der ausgesetzten
        // Schlaege, zieht diese Zusicherung mit.
        assertTrue(protokollNennt(60L * (ausgesetzte + 1) + " Sekunden von 1 Spieler(n)"),
                "verloren ist der GANZE Stapel, nicht nur der Zuschlag dieses Schlags: "
                        + protokollzeilen);
        assertEquals(2, protokollZaehlt("sind verloren"),
                "zwei wirklich versuchte Zuege, zwei Zeilen - die ausgesetzten schweigen: "
                        + protokollzeilen);
        assertEquals(0L, wartendeSekunden(buch, ANNA),
                "der Stapel ist VOR dem Zug geleert - wiederholt wird nie, und ein Doppel"
                        + " faellt bei einer additiven Ablage niemandem auf");
        assertTrue(buch.gesperrt(), "und die Sperre steht neu, nicht nur noch halb");
    }

    @Test
    @DisplayName("Ein ausgesetzter Schlag rechnet - sonst wirft ein untaetiger Takt die"
            + " ganze Sperre weg")
    void waehrendDerSperreWirdWeitergerechnet() {
        // Der Fehlerfall, wenn der ausgesetzte Schlag NICHT rechnet: Die Marke bleibt
        // stehen, die Spanne waechst auf sechs Taktlaengen - und die Untaetigkeitsgrenze
        // ist eine STUFE. Ein einziger untaetiger Schlag am Ende des Fensters warf dann
        // die GANZE Spanne weg, also auch die fuenf Taktlaengen aktiv gespielter Zeit
        // davor, und zwar ohne eine Protokollzeile. 600 s Takt mit 10 min Grenze ist das
        // Paar, das Zeitwerte ausdruecklich erlaubt und die Klage selbst empfiehlt.
        Zeitwerte langsam = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(600),
                Duration.ofMinutes(10));
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(langsam, zaehlt);
        buch.beginne(ANNA);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(600));

        buch.schreibeFort(aktiv(ANNA));
        assertTrue(buch.gesperrt(), "dieser eine Takt ist verloren, das ist gewollt");

        // Die Datenbank ist nach zehn Sekunden zurueck, die Sperre steht noch. Der Spieler
        // baut die ganze Zeit aktiv weiter.
        zaehlt.wirft = false;
        long aktiveSekunden = 0L;
        while (buch.gesperrt()) {
            uhr.weiter(Duration.ofSeconds(600));
            buch.schreibeFort(aktiv(ANNA));
            aktiveSekunden += 600L;
        }

        // Jetzt legt er das Tastenbrett hin: Der naechste Schlag sieht zehn Minuten
        // Untaetigkeit und verwirft SEINEN Takt - aber nur seinen.
        uhr.weiter(Duration.ofSeconds(600));
        buch.schreibeFort(Map.of(ANNA, Duration.ofMinutes(10)));

        assertEquals(aktiveSekunden, zaehlt.lies(ANNA, TAG).sekundenGesamt(),
                "die aktiv gespielten Taktlaengen hinter der Sperre zaehlen; nur der eine"
                        + " untaetige Takt faellt");
        assertTrue(aktiveSekunden > 600L,
                "die Probe prueft nichts, wenn die Sperre nur einen Schlag lang ist");
    }

    @Test
    @DisplayName("Beim Abmelden laeuft niemand in eine als tot bekannte Ablage")
    void abmeldenWaehrendDerSperre() {
        // Ein Proxy-Neustart meldet vierzig Spieler in EINEM Tick ab. Ohne Sperre wartete
        // jeder dieser vierzig Schreibzuege die Aufbaufrist der Verbindung ab - zusammen
        // mehr, als der Serverwaechter zulaesst: Der Server wird mitten im Abrechnen
        // abgeschossen, und dann ist die Zeit ALLER vierzig weg.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(30));

        buch.schliesse(ANNA, Duration.ZERO);
        buch.schliesse(BERT, Duration.ZERO);

        assertEquals(1, zaehlt.aufrufe, "nach dem ersten Fehlschlag kein zweiter Versuch");
        assertEquals(0, buch.offeneMarken(),
                "die Marke eines Abgemeldeten bleibt auch dann nicht stehen");
        // Verloren ist nur, was WIRKLICH versucht wurde. Der zweite Abgang hat nichts
        // versucht und darum nichts verloren - seine Sekunden liegen auf dem Stapel. Eine
        // zweite Verlustzeile waere eine Fehlmeldung, und der Betreiber koennte aus dem
        // Protokoll nicht mehr ablesen, wie viel wirklich fehlt.
        assertEquals(1, protokollZaehlt("sind verloren"),
                "nur der versuchte Schreibzug ist ein Verlust: " + protokollzeilen);
        assertEquals(30L, wartendeSekunden(buch, BERT),
                "die Sekunden des zweiten Abgangs warten auf ihren Schreibzug");
    }

    @Test
    @DisplayName("Ein Abgang hinter der Sperre wird nachgeschrieben, sobald es wieder geht")
    void abgangHinterDerSperreGehtNichtVerloren() {
        // Der Fehlerfall: Die Datenbank ist nach drei Sekunden zurueck, die Sperre steht
        // fuenf Taktlaengen. Wer in diesem Fenster abging, verlor vorher seine ganze
        // Sitzung - mit einer Verlustzeile, die sagte, es sei "gar nicht erst versucht"
        // worden, obwohl jeder Schreibzug durchgekommen waere.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA, BERT));
        assertTrue(buch.gesperrt());

        // Die Datenbank antwortet wieder; ANNA meldet sich mitten im Sperrfenster ab.
        zaehlt.wirft = false;
        uhr.weiter(Duration.ofSeconds(30));
        buch.schliesse(ANNA, Duration.ZERO);

        assertEquals(1, zaehlt.aufrufe,
                "ihr Abgang versucht keinen eigenen Schreibzug - vierzig Abgaenge in einem"
                        + " Tick waeren vierzig Aufbaufristen");

        while (buch.gesperrt()) {
            uhr.weiter(Duration.ofSeconds(60));
            buch.schreibeFort(aktiv(BERT));
        }
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(BERT));

        assertEquals(30L, zaehlt.lies(ANNA, TAG).sekundenGesamt(),
                "die Sitzung der Abgemeldeten kommt nach, obwohl sie nicht mehr da ist");
    }

    @Test
    @DisplayName("Das Abschalten zaehlt nur offene Sitzungen, nicht den Stapel")
    void abschaltenZaehltKeineFremdenStapeleintraege() {
        // Die Zeile "n von m offenen Sitzung(en) weggeschrieben" darf nicht "2 von 1"
        // sagen. Auf dem Stapel koennen Zuschlaege von Spielern liegen, die sich waehrend
        // der Sperre abgemeldet haben; sie gehen mit, sind aber keine offene Sitzung.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA, BERT));

        zaehlt.wirft = false;
        uhr.weiter(Duration.ofSeconds(30));
        buch.schliesse(ANNA, Duration.ZERO);
        uhr.weiter(Duration.ofSeconds(30));

        int geschrieben = buch.schliesseAlles(aktiv(BERT));

        assertEquals(1, geschrieben, "offen war nur noch eine Sitzung");
        assertEquals(30L, zaehlt.lies(ANNA, TAG).sekundenGesamt(),
                "der Stapel der Abgemeldeten geht beim Abschalten trotzdem mit");
        assertEquals(60L, zaehlt.lies(BERT, TAG).sekundenGesamt(),
                "und seine beiden halben Minuten auch");
    }

    @Test
    @DisplayName("Das Abschalten rechnet trotz Sperre ab")
    void abschaltenSetztDieSperreBeiseite() {
        // Das ist der letzte Schreibzug. Ist die Datenbank inzwischen zurueck, waere ein
        // ausgesetzter Versuch genau der Verlust, den die Sperre verhindern soll.
        ZaehlendeAblage zaehlt = new ZaehlendeAblage();
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, zaehlt);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        zaehlt.wirft = true;
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(aktiv(ANNA, BERT));
        assertTrue(buch.gesperrt());

        zaehlt.wirft = false;
        uhr.weiter(Duration.ofSeconds(45));
        int geschrieben = buch.schliesseAlles(aktiv(ANNA, BERT));

        assertEquals(2, geschrieben, "beide Sitzungen sind wirklich weggeschrieben");
        assertEquals(45L, zaehlt.lies(ANNA, TAG).sekundenGesamt());
        assertEquals(0, buch.offeneMarken());
    }

    @Test
    @DisplayName("Das Abschalten meldet nur die Sitzungen, fuer die wirklich etwas geschrieben wurde")
    void abschaltenZaehltGeschriebenesUndKeineMarken() {
        // An dieser Zahl haengt die Zeile "Spielzeit abgerechnet: n von m offenen
        // Sitzung(en) weggeschrieben" - sie steht auf der Abnahmeliste als Beweis fuer
        // "0 Verlust" und darf nicht die Marken zaehlen.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE);
        buch.beginne(ANNA);
        buch.beginne(BERT);
        uhr.weiter(Duration.ofSeconds(60));

        // BERT war den ganzen letzten Takt untaetig: Seine Liste ist leer, fuer ihn ist
        // nichts geschrieben worden.
        int geschrieben = buch.schliesseAlles(Map.of(ANNA, Duration.ZERO,
                BERT, Duration.ofMinutes(30)));

        assertEquals(1, geschrieben, "nur ANNA hatte Sekunden zu schreiben");
        assertEquals(60L, gesamt(ANNA));
        assertEquals(0L, gesamt(BERT));
    }

    @Test
    @DisplayName("Mit einer Ablage, die wirft, ist beim Abschalten nichts weggeschrieben")
    void abschaltenMitKaputterAblageMeldetNull() {
        // Die Erfolgszeile des Abschaltens haengt an dieser Zahl. Vorher zaehlte sie die
        // Marken, und darum stand "3 offene Sitzung(en) weggeschrieben" unmittelbar unter
        // der Zeile, die den Verlust derselben drei meldete.
        Spielzeitbuch buch = buch(Zeitwerte.VORGABE, kaputteAblage());
        buch.beginne(ANNA);
        uhr.weiter(Duration.ofSeconds(60));

        int geschrieben = buch.schliesseAlles(aktiv(ANNA));

        assertEquals(0, geschrieben, "geschrieben wurde nichts, also ist es nichts");
        assertTrue(protokollNennt("60 Sekunden von 1 Spieler(n)"),
                protokollzeilen.toString());
        assertEquals(0, buch.offeneMarken(), "die Karte ist trotzdem leer");
    }
}
