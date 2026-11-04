package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * <p>Die beiden anderen Absagen des Befehls (Konsole, abgemeldeter Spieler) sind hier nicht
 * pruefbar: Die eine haengt an {@code CommandSourceStack.getExecutor()}, die andere an
 * {@code Bukkit.getPlayerExact} - beides gibt es ohne laufenden Server nicht. Dass ihre
 * Saetze gesammelt am Kopf der Klasse stehen, ist darum alles, was dafuer getan werden kann.
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

    /** Der Text einer gesendeten Zeile - gebaut wird jede mit {@code Component.text}. */
    private static String inhalt(Component nachricht) {
        return ((TextComponent) nachricht).content();
    }

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
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
        return new Wirtschaftsstand(new Wirtschaftswerte(Preisstaffel.VORGABE,
                Wirtschaftswerte.VORGABE_STARTGUTHABEN, Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE));
    }

    private Spielzeitbuch buch(Spielzeitablage ablage) {
        return new Spielzeitbuch(stand(), ablage, uhr, protokoll);
    }

    private SpielzeitBefehle befehle(Spielzeitablage ablage, Spielzeitbuch buch) {
        // Die Sprache ist in Schritt 1 verdrahtet und in dieser Klasse noch
        // ungenutzt - sie kommt mit dem Bauschritt, der ihre Saetze umzieht.
        return new SpielzeitBefehle(buch, ablage, stand(), uhr, protokoll,
                Sprachprobe.spracheAusDemJar());
    }

    private List<String> zeilen(SpielzeitBefehle befehle) {
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

        List<String> zeilen = zeilen(befehle);

        assertEquals(List.of(SpielzeitBefehle.NICHT_LESBAR), zeilen,
                "eine Zeile, und zwar die Absage - keine leere Liste: " + zeilen);
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

        List<String> zeilen = zeilen(befehle);

        assertEquals(4, zeilen.size(), "heute, gesamt, Zustand und der Hinweis: " + zeilen);
        assertTrue(zeilen.get(0).contains("10 min"),
                "die geschriebenen zehn Minuten: " + zeilen.get(0));
        assertEquals(Spielzeittext.HINWEIS_KEIN_ALTBESTAND, zeilen.get(3),
                "auch der Hinweis kommt aus der Anzeige und nicht aus dem Befehl: "
                        + zeilen);
    }

    @Test
    @DisplayName("Was hinter der Sperre auf seinen Schreibzug wartet, zeigt der Befehl mit")
    void wasHinterDerSperreWartetStehtInDerAnzeige() {
        // Ohne den Stapel laese der Spieler mitten im Ausfall eine zu kleine Zahl: Die
        // Ablage kennt nur das Geschriebene, die offene Sitzung nur den letzten Takt, und
        // dazwischen liegen die Takte, die hinter der Sperre gerechnet und noch nicht
        // geschrieben wurden.
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

        List<String> zeilen = zeilen(befehle);

        assertTrue(zeilen.get(0).contains("2 min"),
                "die wartende und die offene Minute sind gemessen und gehoeren beide in"
                        + " die Anzeige: " + zeilen.get(0));
        assertTrue(zeilen.get(2).contains("2 min"),
                "und zusammen sind sie genau das, was noch nicht geschrieben ist: "
                        + zeilen.get(2));
        // Und der Befehl muss die SPERRE durchreichen, nicht nur den Stapel: Sonst waere
        // die Menge richtig und der genannte Abstand falsch - "das geschieht alle 60 s",
        // waehrend der naechste Schreibzug noch Taktlaengen entfernt ist.
        assertTrue(zeilen.get(2).endsWith(Spielzeittext.SCHREIBZUG_WARTET),
                "die Zeile muss den Zustand des Buchs nennen: " + zeilen.get(2));
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
        SpeicherSpielzeit ablage = new SpeicherSpielzeit();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(ANNA, HEUTE, 600L)));
        Spielzeitbuch buch = buch(ablage);
        SpielzeitBefehle befehle = befehle(ablage, buch);
        buch.beginne(ANNA);
        // Zwoelf Minuten ohne einen Schritt - mehr als die ausgelieferten zehn.
        Duration untaetig = Duration.ofMinutes(12);
        uhr.weiter(untaetig);

        List<String> zeilen = befehle.zeilen(ANNA, untaetig, HEUTE, Zeitwerte.VORGABE);

        assertTrue(zeilen.get(2).contains("NICHT gezaehlt"),
                "die Zustandszeile muss den untaetigen Zustand nennen: " + zeilen.get(2));
        assertTrue(zeilen.get(2).contains("12 min"),
                "und sagen, seit wann er steht: " + zeilen.get(2));
        assertTrue(zeilen.get(0).contains("10 min"),
                "die untaetige Spanne darf NICHT in die Zahl des Tages wandern - gezaehlt"
                        + " ist nur, was geschrieben ist: " + zeilen.get(0));
    }

    @Test
    @DisplayName("Die Ueberschrift und JEDE Zeile gehen wirklich an den Spieler")
    void derSendewegGibtAllesAus() {
        // Der Fehlerfall: Faellt die Schleife in sende() heraus - der naheliegende Patzer
        // bei dem Umbau, den die Klasse selbst ankuendigt (eigener Befehl mit dem
        // Zweitnamen /playtime) -, liest der Spieler die goldene Ueberschrift und darunter
        // NICHTS: kein heute, kein gesamt, nicht einmal die Absage. Geprueft war bisher nur,
        // WAS in den Zeilen steht, nicht DASS sie ankommen.
        Mitschrift empfaenger = new Mitschrift();
        List<String> zeilen = List.of("heute   10 min", "gesamt  10 min",
                SpielzeitBefehle.NICHT_LESBAR);

        SpielzeitBefehle.sende(empfaenger, Spielzeittext.kopfSelbst(), zeilen);

        assertEquals(zeilen.size() + 1, empfaenger.gesendet.size(),
                "die Ueberschrift und jede Zeile: " + empfaenger.gesendet);
        assertEquals(Spielzeittext.kopfSelbst(), inhalt(empfaenger.gesendet.getFirst()),
                "zuerst die Ueberschrift");
        assertEquals(NamedTextColor.GOLD, empfaenger.gesendet.getFirst().color(),
                "und zwar golden - daran erkennt der Spieler, wo seine Antwort anfaengt");
        for (int stelle = 0; stelle < zeilen.size(); stelle++) {
            Component gesendet = empfaenger.gesendet.get(stelle + 1);
            assertEquals("  " + zeilen.get(stelle), inhalt(gesendet),
                    "jede Zeile genau einmal, eingezogen und in der Reihenfolge der Liste");
            assertEquals(NamedTextColor.GRAY, gesendet.color(),
                    "eine Farbe fuer alles unter der Ueberschrift, auch fuer die Absage");
        }
    }
}
