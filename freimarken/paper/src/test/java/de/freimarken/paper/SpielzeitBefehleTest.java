package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Befehl {@code /freimarken spielzeit} - der Teil, der ohne Server auskommt.
 *
 * <p>Welche Rechte ein Spieler hat und wer gerade anwesend ist, weiss nur der Server; WAS
 * unter der Ueberschrift steht, nicht. Genau deshalb steht das in
 * {@link SpielzeitBefehle#zeilen} und nicht mitten im Senden - dasselbe Muster wie bei
 * {@link ParzellenBefehleTest}.
 *
 * <p>Die beiden anderen Absagen des Befehls (Konsole, abgemeldeter Spieler) sind in ihrer
 * VERZWEIGUNG hier nicht pruefbar: Die eine haengt an
 * {@code CommandSourceStack.getExecutor()}, die andere an {@code Bukkit.getPlayerExact} -
 * beides gibt es ohne laufenden Server nicht. Ihr BILD ist es: Sie sind reine Methoden, und
 * die Tafel unten haelt es zeichengenau.
 */
class SpielzeitBefehleTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    /** Ein Nachmittag: 12:00 UTC ist in Europe/Berlin derselbe Tag. */
    private static final Instant START = Instant.parse("2026-11-03T12:00:00Z");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 3);

    /** Eine Ablage, die auf keine Frage antwortet - die weggezogene Datenbank. */
    private static final class UnerreichbareAblage implements Spielzeitablage {

        @Override
        public void schreibeFort(List<Zuschlag> zuschlaege) {
            throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                    + zuschlaege.size() + " Zuschlaege, nicht geschrieben)");
        }

        @Override
        public Spielzeitstand lies(UUID spieler, LocalDate heute) {
            throw new IllegalStateException("Spielzeit nicht lesbar");
        }
    }

    /** Eine Ablage, die lesen kann, aber nichts annimmt - so entsteht die Sperre. */
    private static final class NimmtNichtsAn implements Spielzeitablage {

        @Override
        public void schreibeFort(List<Zuschlag> zuschlaege) {
            throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                    + zuschlaege.size() + " Zuschlaege, nicht geschrieben)");
        }

        @Override
        public Spielzeitstand lies(UUID spieler, LocalDate heute) {
            return Spielzeitstand.leer(spieler, heute);
        }
    }

    /**
     * Eine {@link Audience}, die mitschreibt statt zu senden.
     *
     * <p>Damit ist der Sendeweg ohne Server pruefbar: Eine Audience ist reines Adventure und
     * braucht kein Bukkit - Paper bringt die Schnittstelle mit.
     */
    private static final class Mitschrift implements Audience {

        private final List<Component> gesendet = new ArrayList<>();

        @Override
        public void sendMessage(Component nachricht) {
            gesendet.add(nachricht);
        }
    }

    /**
     * Der TEXT einer gesendeten Zeile - ohne Farbe, aber MIT dem Einzug, der seit dem
     * 04.11.2026 im Satz steht.
     *
     * <p>Ueber {@link Sprachprobe#nurText} und nicht ueber
     * {@code ((TextComponent) nachricht).content()}: Ein aus {@code <gray>...</gray>}
     * geparstes Component haelt seinen Text in einem KIND, und {@code content()} der Wurzel
     * ist dann leer. Die Proben wuerden rot, ohne dass sich am Bild etwas geaendert hat.
     */
    private static String text(List<Component> zeilen, int stelle) {
        return Sprachprobe.nurText(zeilen.get(stelle));
    }

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private final Sprache sprache = Sprachprobe.spracheAusDemJar();
    private SpielzeitbuchTest.StellUhr uhr;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new SpielzeitbuchTest.StellUhr(START);
        protokollzeilen.clear();
        // Eigener Name je Probe: Logger leben im LogManager weiter, und ein geteilter Name
        // liesse die Zeilen zweier Proben durcheinanderlaufen.
        protokoll = Logger.getLogger("SpielzeitBefehleProbe-" + UUID.randomUUID());
        protokoll.setUseParentHandlers(false);
        protokoll.addHandler(new Handler() {
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
    }

    private static Wirtschaftsstand stand() {
        // Genau die Vorgabe - diese Probe variiert nichts am Wirtschaftssatz.
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
    }

    private Spielzeitbuch buch(Spielzeitablage ablage) {
        return new Spielzeitbuch(stand(), ablage, uhr, protokoll);
    }

    private SpielzeitBefehle befehle(Spielzeitablage ablage, Spielzeitbuch buch) {
        return new SpielzeitBefehle(buch, ablage, stand(), uhr, protokoll, sprache);
    }

    private List<Component> zeilen(SpielzeitBefehle befehle) {
        return befehle.zeilen(ANNA, Duration.ZERO, HEUTE, Zeitwerte.VORGABE);
    }

    @Test
    @DisplayName("Antwortet die Ablage nicht, steht die Absage in den Zeilen")
    void absageWennDieAblageNichtAntwortet() {
        // Der Fehlerfall: Solange dieser Satz im Befehl einzeln gesendet wurde, konnte er
        // beim naechsten Umbau herausfallen, ohne dass eine Probe rot wird - der Spieler
        // liest dann nach der goldenen Ueberschrift GAR NICHTS und haelt den Befehl fuer
        // kaputt. Geprueft wird darum die ausgegebene Zeile und nicht die Konstante gegen
        // Teile von sich selbst.
        Spielzeitablage tot = new UnerreichbareAblage();
        SpielzeitBefehle befehle = befehle(tot, buch(tot));

        List<Component> zeilen = zeilen(befehle);

        // Das BILD der ganzen Liste gegen das Bild der reinen Methode: eine Zeile, und zwar
        // die Absage - keine leere Liste. Der Wortlaut selbst haengt an der Tafel unten.
        assertEquals(Sprachprobe.bild(SpielzeitBefehle.nichtLesbarZeilen(sprache)),
                Sprachprobe.bild(zeilen),
                "eine Zeile, und zwar die Absage - keine leere Liste: "
                        + Sprachprobe.bild(zeilen));
        assertTrue(protokollzeilen.stream()
                        .anyMatch(satz -> satz.getMessage().contains(ANNA.toString())),
                "der Operator braucht den Grund und den Spieler: " + protokollzeilen);
    }

    @Test
    @DisplayName("Sonst sind es die vier Zeilen der Anzeige")
    void dieVierZeilenKommenAusDemText() {
        SpeicherSpielzeit ablage = new SpeicherSpielzeit();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(ANNA, HEUTE, 600L)));
        SpielzeitBefehle befehle = befehle(ablage, buch(ablage));

        List<Component> zeilen = zeilen(befehle);

        assertEquals(4, zeilen.size(), "heute, gesamt, Zustand und der Hinweis: "
                + Sprachprobe.bild(zeilen));
        assertTrue(text(zeilen, 0).contains("10 min"),
                "die geschriebenen zehn Minuten: " + text(zeilen, 0));
        assertEquals(Sprachprobe.bild(Spielzeittext.hinweisZeile(sprache)),
                Sprachprobe.bild(zeilen.get(3)),
                "auch der Hinweis kommt aus der Anzeige und nicht aus dem Befehl: "
                        + Sprachprobe.bild(zeilen));
    }

    @Test
    @DisplayName("Was hinter der Sperre auf seinen Schreibzug wartet, zeigt der Befehl mit")
    void wasHinterDerSperreWartetStehtInDerAnzeige() {
        // Ohne den Stapel laese der Spieler mitten im Ausfall eine zu kleine Zahl: Die
        // Ablage kennt nur das Geschriebene, die offene Sitzung nur den letzten Takt, und
        // dazwischen liegen die Takte, die hinter der Sperre gerechnet und noch nicht
        // geschrieben wurden.
        SpielzeitBefehle befehle = hinterDerSperre();

        List<Component> zeilen = zeilen(befehle);

        assertTrue(text(zeilen, 0).contains("2 min"),
                "die wartende und die offene Minute sind gemessen und gehoeren beide in"
                        + " die Anzeige: " + text(zeilen, 0));
        assertTrue(text(zeilen, 2).contains("2 min"),
                "und zusammen sind sie genau das, was noch nicht geschrieben ist: "
                        + text(zeilen, 2));
        // Und der Befehl muss die SPERRE durchreichen, nicht nur den Stapel: Sonst waere
        // die Menge richtig und der genannte Abstand falsch - "das geschieht alle 60 s",
        // waehrend der naechste Schreibzug noch Taktlaengen entfernt ist.
        assertTrue(text(zeilen, 2).endsWith(Spielzeittext.schreibzugWartet(sprache)),
                "die Zeile muss den Zustand des Buchs nennen: " + text(zeilen, 2));
    }

    /**
     * Der Befehl, nachdem die Datenbank zwei Schreibzuege nicht angenommen hat.
     *
     * <p>Eigene Methode, weil zwei Proben denselben Aufbau brauchen: die Zusicherung auf
     * den Stapel und die Bildtafel fuer die Zustandszeile hinter der Sperre.
     */
    private SpielzeitBefehle hinterDerSperre() {
        Spielzeitablage taub = new NimmtNichtsAn();
        Spielzeitbuch buch = buch(taub);
        SpielzeitBefehle befehle = befehle(taub, buch);
        buch.beginne(ANNA);

        // Der erste Takt scheitert - diese Minute ist verloren und setzt die Sperre.
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(Map.of(ANNA, Duration.ZERO));
        // Der zweite Takt rechnet, schreibt aber nicht: Seine Minute wartet.
        uhr.weiter(Duration.ofSeconds(60));
        buch.schreibeFort(Map.of(ANNA, Duration.ZERO));
        assertTrue(buch.gesperrt(), "sonst prueft die Probe den falschen Zustand");
        // Und seit diesem letzten Takt ist eine weitere Minute verstrichen, die noch in
        // keinem Zuschlag steht. Ohne dieses Weiterstellen stuende die Uhr genau auf der
        // Marke, die offene Spanne waere 0 - und dass der Befehl sie ueberhaupt holt
        // (buch.offeneSitzung), waere von keiner Zusicherung gedeckt.
        uhr.weiter(Duration.ofSeconds(60));
        return befehle;
    }

    @Test
    @DisplayName("Wer untaetig ist, liest das - und seine untaetigen Minuten zaehlen nicht")
    void untaetigkeitStehtInDerAnzeige() {
        // Der Fehlerfall: Reicht der Befehl die gemessene Untaetigkeit nicht an das Buch
        // durch (buch.offeneSitzung(wer, Duration.ZERO) - eine Zeile, die beim Aufraeumen
        // der Parameterliste leicht so endet), dann urteilt das Buch GEZAEHLT, und die
        // Anzeige sagt genau dem Spieler, der nachsieht, ob seine Zeit zaehlt, das
        // Gegenteil dessen, was der Takt tut: "wird gezaehlt" samt einer Zahl, die um die
        // untaetige Spanne zu hoch ist. Bis hierher war die Untaetigkeit in jeder Probe
        // dieses Befehls Null und der Zweig damit ungeprueft.
        Duration untaetig = Duration.ofMinutes(12);
        SpielzeitBefehle befehle = untaetigSeit(untaetig);

        List<Component> zeilen = befehle.zeilen(ANNA, untaetig, HEUTE, Zeitwerte.VORGABE);

        assertTrue(text(zeilen, 2).contains("NICHT gezaehlt"),
                "die Zustandszeile muss den untaetigen Zustand nennen: " + text(zeilen, 2));
        assertTrue(text(zeilen, 2).contains("12 min"),
                "und sagen, seit wann er steht: " + text(zeilen, 2));
        assertTrue(text(zeilen, 0).contains("10 min"),
                "die untaetige Spanne darf NICHT in die Zahl des Tages wandern - gezaehlt"
                        + " ist nur, was geschrieben ist: " + text(zeilen, 0));
    }

    /** Ein Spieler mit zehn geschriebenen Minuten, der seit der genannten Spanne steht. */
    private SpielzeitBefehle untaetigSeit(Duration untaetig) {
        SpeicherSpielzeit ablage = new SpeicherSpielzeit();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(ANNA, HEUTE, 600L)));
        Spielzeitbuch buch = buch(ablage);
        SpielzeitBefehle befehle = befehle(ablage, buch);
        buch.beginne(ANNA);
        // Zwoelf Minuten ohne einen Schritt - mehr als die ausgelieferten zehn.
        uhr.weiter(untaetig);
        return befehle;
    }

    @Test
    @DisplayName("Die Ueberschrift und JEDE Zeile gehen wirklich an den Spieler")
    void derSendewegGibtAllesAus() {
        // Der Fehlerfall: Faellt die Schleife in sende() heraus - der naheliegende Patzer
        // bei dem Umbau, den die Klasse selbst ankuendigt (eigener Befehl mit dem
        // Zweitnamen /playtime) -, liest der Spieler die goldene Ueberschrift und darunter
        // NICHTS: kein heute, kein gesamt, nicht einmal die Absage.
        //
        // Einzug und Farbe prueft diese Probe seit dem 04.11.2026 NICHT mehr: Beides steht
        // jetzt IM Satz und nicht am Sendeort, und zeichengenau haelt es die Tafel unten.
        // Was hier bleibt, ist die ZAHL und die REIHENFOLGE - genau der Fehlerfall oben.
        Mitschrift empfaenger = new Mitschrift();
        List<Component> zeilen = List.of(Component.text("erste"), Component.text("zweite"),
                Component.text("dritte"));
        Component kopf = Spielzeittext.kopfSelbst(sprache);

        SpielzeitBefehle.sende(empfaenger, kopf, zeilen);

        assertEquals(zeilen.size() + 1, empfaenger.gesendet.size(),
                "die Ueberschrift und jede Zeile: " + empfaenger.gesendet);
        assertEquals(Sprachprobe.bild(kopf), Sprachprobe.bild(empfaenger.gesendet.getFirst()),
                "zuerst die Ueberschrift");
        for (int stelle = 0; stelle < zeilen.size(); stelle++) {
            assertEquals(Sprachprobe.bild(zeilen.get(stelle)),
                    Sprachprobe.bild(empfaenger.gesendet.get(stelle + 1)),
                    "jede Zeile genau einmal und in der Reihenfolge der Liste");
        }
    }

    // --- Die Bildtafel: das heutige Bild, zeichengenau festgenagelt -------------
    //
    // Sie entstand VOR dem Umzug in die Sprachdatei und bleibt danach Zeichen fuer
    // Zeichen stehen. Dass sie gruen bleibt, IST der Beweis "zeichengleich": Jede
    // Erwartung kam aus einem ROTEN Lauf gegen den committeten Stand 891bb17, also aus
    // der Maschine und nicht aus einer zweiten Lesung derselben Quellzeile. Haette eine
    // Hand das Bild aus dem Quelltext abgeschrieben und daraus auch die Vorlagenzeile,
    // verglichen zwei Kopien desselben Irrtums - ein verlorenes Leerzeichen waere in
    // beiden.
    //
    // Verglichen wird das &-Bild (LegacyComponentSerializer, Paragraf zu &), nicht
    // Component.equals: Die Baumform unterscheidet sich vor und nach dem Umzug (ein
    // Elternteil mit Kindern gegen eine Kette von append), und nur das Bild zaehlt.
    //
    // Sie faengt: ein verlorenes fuehrendes Leerzeichen, eine verschobene Spalte, eine
    // falsche Farbe, die drei Leerzeichen vor der Klammer der heute-Zeile, einen Einzug,
    // den die Konsolenabsage nie hatte, und eine Absage, die ihr Rot verliert.
    //
    // WER SIE AUFWEICHT, NIMMT NICHT NUR IHR DIE KRAFT. Fuenf weitere Proben leihen
    // ihren Wortlaut von dieser Tafel: Sie vergleichen die ausgegebene Zeile mit dem
    // Satz AUS DER SPRACHDATEI und nicht mehr mit einer Konstanten im Programm - die
    // Konstanten SpielzeitBefehle.NICHT_LESBAR, Spielzeittext.HINWEIS_KEIN_ALTBESTAND
    // und Spielzeittext.SCHREIBZUG_WARTET sind mit dem Umzug entfallen. Was die fuenf
    // noch zusichern, ist WELCHE Zeile wo steht; WIE sie aussieht, haengt allein hier.
    // Gemessen am 05.11.2026 sind es genau diese fuenf - drei in dieser Datei:
    //   absageWennDieAblageNichtAntwortet         (SpielzeitBefehle.nichtLesbarZeilen)
    //   dieVierZeilenKommenAusDemText             (Spielzeittext.hinweisZeile)
    //   wasHinterDerSperreWartetStehtInDerAnzeige (Spielzeittext.schreibzugWartet)
    // und zwei in SpielzeittextTest:
    //   waehrendDerSperreStehtKeinTaktAbstandDa   (schreibzugWartet und taktAbstand)
    //   hinweisAufDenEinbau                       (Spielzeittext.hinweisZeile)

    /** Zehn geschriebene Minuten, nichts offen - der Normalfall der Anzeige. */
    private SpielzeitBefehle zehnMinutenGeschrieben() {
        SpeicherSpielzeit ablage = new SpeicherSpielzeit();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(ANNA, HEUTE, 600L)));
        return befehle(ablage, buch(ablage));
    }

    /** Ueberschrift und Zeilen, wie der Spieler sie liest - durch den echten Sendeweg. */
    private String gesendetesBild(Component kopf, List<Component> zeilen) {
        Mitschrift empfaenger = new Mitschrift();
        SpielzeitBefehle.sende(empfaenger, kopf, zeilen);
        return Sprachprobe.bild(empfaenger.gesendet);
    }

    @Test
    @DisplayName("33a - das Bild der eigenen Anzeige ist zeichengleich festgenagelt")
    void bildDerEigenenAnzeige() {
        SpielzeitBefehle befehle = zehnMinutenGeschrieben();

        assertEquals("""
                &6Deine Spielzeit
                &7  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)
                &7  gesamt  10 min
                &7  wird gezaehlt - die letzten 0 s sind noch nicht geschrieben, das geschieht alle 60 s.
                &7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.""",
                gesendetesBild(Spielzeittext.kopfSelbst(sprache), zeilen(befehle)));
    }

    @Test
    @DisplayName("33b - das Bild der fremden Anzeige ist zeichengleich festgenagelt")
    void bildDerFremdenAnzeige() {
        SpielzeitBefehle befehle = zehnMinutenGeschrieben();

        assertEquals("""
                &6Spielzeit von Anna
                &7  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)
                &7  gesamt  10 min
                &7  wird gezaehlt - die letzten 0 s sind noch nicht geschrieben, das geschieht alle 60 s.
                &7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.""",
                gesendetesBild(Spielzeittext.kopfFuer(sprache, "Anna"), zeilen(befehle)));
    }

    @Test
    @DisplayName("33c - das Bild der Absage, wenn die Ablage nicht antwortet")
    void bildDerAbsageAnDieAblage() {
        Spielzeitablage tot = new UnerreichbareAblage();
        SpielzeitBefehle befehle = befehle(tot, buch(tot));

        // GRAU und MIT Einzug, obwohl es eine Absage ist: Sie steht an der Stelle der vier
        // Zeilen. Nicht zu verwechseln mit 33h, der einzigen roten Zeile dieses Befehls.
        assertEquals("""
                &6Deine Spielzeit
                &7  Die Spielzeit ist gerade nicht lesbar - gezaehlt wird trotzdem. Sag einem Operator Bescheid.""",
                gesendetesBild(Spielzeittext.kopfSelbst(sprache), zeilen(befehle)));
    }

    @Test
    @DisplayName("33d - das Bild, wenn gerade nicht gezaehlt wird")
    void bildBeiUntaetigkeit() {
        Duration untaetig = Duration.ofMinutes(12);
        SpielzeitBefehle befehle = untaetigSeit(untaetig);

        assertEquals("""
                &6Deine Spielzeit
                &7  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)
                &7  gesamt  10 min
                &7  wird gerade NICHT gezaehlt - untaetig seit 12 min, gezaehlt wird bis 10 min. Ein Schritt oder ein Tastendruck zaehlt wieder.
                &7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.""", gesendetesBild(Spielzeittext.kopfSelbst(sprache),
                befehle.zeilen(ANNA, untaetig, HEUTE, Zeitwerte.VORGABE)));
    }

    @Test
    @DisplayName("33e - das Bild, solange der Schreibzug hinter der Sperre wartet")
    void bildHinterDerSperre() {
        SpielzeitBefehle befehle = hinterDerSperre();

        assertEquals("""
                &6Deine Spielzeit
                &7  heute   2 min   (03.11.2026, Tagesgrenze Europe/Berlin)
                &7  gesamt  2 min
                &7  wird gezaehlt - die letzten 2 min sind noch nicht geschrieben, der naechste Schreibzug wartet auf die Datenbank - gezaehlt wird weiter, nachgeschrieben wird alles.
                &7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.""",
                gesendetesBild(Spielzeittext.kopfSelbst(sprache), zeilen(befehle)));
    }

    @Test
    @DisplayName("33f - das Bild, wenn die Untaetigkeitsregel aus ist")
    void bildOhneUntaetigkeitsregel() {
        // Die Regel AUS heisst: untaetigNach ist null, also zaehlt Abwesenheit mit - und
        // die Zustandszeile haengt den Zusatz samt dem Pfad aus der config.yml an.
        Zeitwerte aus = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60),
                Duration.ZERO);
        SpielzeitBefehle befehle = zehnMinutenGeschrieben();

        assertEquals("""
                &6Deine Spielzeit
                &7  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)
                &7  gesamt  10 min
                &7  wird gezaehlt - die letzten 0 s sind noch nicht geschrieben, das geschieht alle 60 s. Abwesenheit zaehlt auf diesem Server mit (zeit.untaetig-minuten: 0).
                &7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.""", gesendetesBild(Spielzeittext.kopfSelbst(sprache),
                befehle.zeilen(ANNA, Duration.ZERO, HEUTE, aus)));
    }

    @Test
    @DisplayName("33g - das Bild der Absage an die Konsole - OHNE Einzug")
    void bildDerKonsolenabsage() {
        // Sie geht nicht durch sende() und stand nie unter einer Ueberschrift. Wer den
        // Einzug mechanisch in alle Saetze schreibt, gibt ihr einen, den sie nie hatte.
        assertEquals("&7Die Konsole spielt nicht. /freimarken spielzeit <spieler> nennt die Zeit eines anwesenden Spielers.",
                Sprachprobe.bild(SpielzeitBefehle.keineKonsoleZeilen(sprache)));
    }

    @Test
    @DisplayName("33h - das Bild der Absage fuer einen Abwesenden - ROT und ohne Einzug")
    void bildFuerEinenAbwesenden() {
        // Die EINZIGE rote Zeile dieses Befehls. Das Javadoc von sende() sagte, die Absage
        // sei grau und nicht mehr rot - gemeint war 33c, die durch sende() laeuft, nicht
        // diese. Wer die zwei verwechselt, nimmt der Absage, die der Spieler als Fehler
        // lesen soll, ihre Farbe.
        assertEquals("&cAnna ist nicht da. Gezeigt werden kann nur die Spielzeit eines anwesenden Spielers.",
                Sprachprobe.bild(SpielzeitBefehle.nichtDaZeilen(sprache, "Anna")));
    }

    @Test
    @DisplayName("33i - heute und gesamt beginnen in derselben Spalte")
    void spaltenStehenUntereinander() {
        // Die Spaltenbreite ist seit dem 04.11.2026 Inhalt der Sprachdatei: "heute" mit
        // drei und "gesamt" mit zwei Leerzeichen. Diese Probe sagt, WARUM diese Zahlen so
        // sind - die Tafel oben sagt nur, DASS sie so sind. Nimmt der Betreiber ein
        // Leerzeichen heraus, rutscht ihm die Spalte, und das hier faengt es mit einer
        // Meldung, die den Grund nennt.
        SpielzeitBefehle befehle = zehnMinutenGeschrieben();
        List<Component> zeilen = zeilen(befehle);
        String heute = text(zeilen, 0);
        String gesamt = text(zeilen, 1);
        String wert = "10 min";

        assertEquals(heute.indexOf(wert), gesamt.indexOf(wert),
                "die Dauer muss in beiden Zeilen in derselben Spalte beginnen: ["
                        + heute + "] [" + gesamt + "]");
        assertEquals(10, heute.indexOf(wert),
                "und zwar in Spalte 11, also mit dem Einzug von zwei Zeichen und einer"
                        + " Spaltenbreite von acht: [" + heute + "]");
    }
}
