package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitregel;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Anzeige der Spielzeit - reine Rechnerei, also ohne Server pruefbar. */
class SpielzeittextTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 3);

    /** Nichts wartet auf seinen Schreibzug - der Normalfall ohne Fehlschlag. */
    private static final List<Spielzeitablage.Zuschlag> NICHTS_WARTET = List.of();

    /** Die Ablage antwortet, der Takt schreibt in seinem Abstand. */
    private static final boolean NICHT_GESPERRT = false;

    /** Nach einem Fehlschlag: gerechnet wird weiter, geschrieben nicht. */
    private static final boolean GESPERRT = true;

    private final Sprache sprache = Sprachprobe.spracheAusDemJar();

    /**
     * Der TEXT einer Zeile - ohne Farbe, aber MIT dem Einzug, der seit dem 04.11.2026
     * im Satz steht.
     *
     * <p>Ueber {@link Sprachprobe#nurText} und nicht ueber
     * {@code ((TextComponent) zeile).content()}: Ein aus {@code <gray>...</gray>} geparstes
     * Component haelt seinen Text in einem KIND, und {@code content()} der Wurzel ist dann
     * leer. Diese Proben wuerden rot, ohne dass sich am Bild etwas geaendert hat - und
     * gerade diese falsche Roetung verleitet dazu, die Zusicherung abzuschwaechen.
     */
    private static String text(List<Component> zeilen, int stelle) {
        return Sprachprobe.nurText(zeilen.get(stelle));
    }

    /** Ein gezaehlter Takt ueber die genannte Spanne, Ende am genannten Zeitpunkt. */
    private static Spielzeitregel.Urteil gezaehlt(Instant von, Duration dauer) {
        Instant bis = von.plus(dauer);
        return new Spielzeitregel.Urteil(von, bis, bis, Spielzeitregel.Ausgang.GEZAEHLT);
    }

    private static Spielzeitregel.Urteil verworfen(Instant jetzt) {
        return new Spielzeitregel.Urteil(jetzt, jetzt, jetzt,
                Spielzeitregel.Ausgang.UNTAETIG);
    }

    @Test
    @DisplayName("Stunden und Minuten, Minuten zweistellig hinter einer Stunde")
    void dauerInWorte() {
        assertEquals("0 min", Spielzeittext.dauer(sprache, Duration.ZERO));
        assertEquals("unter 1 min", Spielzeittext.dauer(sprache, Duration.ofSeconds(42)));
        assertEquals("7 min", Spielzeittext.dauer(sprache, Duration.ofMinutes(7)));
        assertEquals("1 h 30 min", Spielzeittext.dauer(sprache, Duration.ofMinutes(90)));
        // Zweistellig, weil "3 h 5 min" von "3 h 50 min" auf einen Blick nicht zu
        // unterscheiden ist.
        assertEquals("12 h 05 min",
                Spielzeittext.dauer(sprache, Duration.ofHours(12).plusMinutes(5)));
        assertEquals("40 h 00 min", Spielzeittext.dauer(sprache, Duration.ofHours(40)));
    }

    @Test
    @DisplayName("Unter einer Minute zaehlt die Anzeige Sekunden")
    void genaueDauerZeigtSekunden() {
        // "unter 1 min" waere genau dort unbrauchbar, wo ein Spieler zusieht, ob die Zahl
        // waechst - bei der laufenden Sitzung und der Untaetigkeit.
        assertEquals("0 s", Spielzeittext.genaueDauer(sprache, Duration.ZERO));
        assertEquals("42 s", Spielzeittext.genaueDauer(sprache, Duration.ofSeconds(42)));
        assertEquals("1 min", Spielzeittext.genaueDauer(sprache, Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("Die laufende Sitzung wird dazugerechnet")
    void laufendeSitzungKommtDazu() {
        // lies() kennt nur, was geschrieben ist. Ohne diese Rechnung laese ein Spieler,
        // der gerade beigetreten ist, eine 0 neben "wird gezaehlt" - und meldete das.
        var stand = new Spielzeitstand(ANNA, HEUTE, 3600L, 120L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ofMinutes(5));

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE);

        assertTrue(text(zeilen, 0).contains("7 min"),
                "2 Minuten geschrieben plus 5 offene: " + text(zeilen, 0));
        assertTrue(text(zeilen, 1).contains("1 h 05 min"),
                "eine Stunde geschrieben plus 5 offene: " + text(zeilen, 1));
    }

    @Test
    @DisplayName("Was hinter der Sperre auf seinen Schreibzug wartet, zeigt die Anzeige mit")
    void wartendeZuschlaegeKommenDazu() {
        // Der Fehlerfall ohne diese Rechnung: Die Datenbank hat EINEN Schreibzug nicht
        // angenommen, danach rechnet der Takt weiter und schreibt fuenf Schlaege lang
        // nicht. Die Sekunden dieser Schlaege sind GEMESSEN und gehen nicht verloren - sie
        // stehen nur noch nicht in der Ablage. Fehlten sie in der Anzeige, laese der
        // Spieler mitten im Ausfall eine um bis zu fuenf Taktlaengen zu kleine Zahl und
        // meldete einen Verlust, den es nicht gibt.
        var stand = new Spielzeitstand(ANNA, HEUTE, 600L, 600L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ofSeconds(30));
        // Zwei Schlaege von je einer Minute warten, einer davon vom VORTAG - er zaehlt
        // darum zu "gesamt" und nicht zu "heute".
        List<Spielzeitablage.Zuschlag> wartend = List.of(
                new Spielzeitablage.Zuschlag(ANNA, HEUTE, 60L),
                new Spielzeitablage.Zuschlag(ANNA, HEUTE.minusDays(1), 60L));

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, wartend,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE);

        assertTrue(text(zeilen, 0).contains("11 min"),
                "600 geschriebene, 60 wartende und 30 offene Sekunden von heute: "
                        + text(zeilen, 0));
        assertTrue(text(zeilen, 1).contains("12 min"),
                "gesamt zaehlt auch die wartende Minute des Vortags: " + text(zeilen, 1));
        assertTrue(text(zeilen, 2).contains("2 min"),
                "ungeschrieben sind beide wartenden Minuten und die offenen 30 s, nicht"
                        + " nur der letzte Takt: " + text(zeilen, 2));
    }

    @Test
    @DisplayName("Steht die Sperre, nennt die Zeile keinen Abstand, den es gerade nicht gibt")
    void waehrendDerSperreStehtKeinTaktAbstandDa() {
        // Der Fehlerfall: Die Datenbank hat EINEN Schreibzug nicht angenommen, danach
        // rechnet der Takt weiter und schreibt mehrere Taktlaengen lang nicht. Die
        // angezeigte MENGE ist dabei richtig - der wartende Stapel steht darin -, aber
        // "das geschieht alle 60 s" ist um das Mehrfache falsch, und zwar vorgelesen
        // genau dem Spieler, der nachsieht, ob seine Zeit gesichert wird.
        var stand = new Spielzeitstand(ANNA, HEUTE, 600L, 600L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ZERO);
        List<Spielzeitablage.Zuschlag> wartend = List.of(
                new Spielzeitablage.Zuschlag(ANNA, HEUTE, 60L));
        String abstand = Spielzeittext.taktAbstand(sprache,
                Zeitwerte.VORGABE.taktSekunden());

        String gesperrt = text(Spielzeittext.werte(sprache, stand, offen, wartend, GESPERRT,
                Duration.ZERO, Zeitwerte.VORGABE), 2);

        assertTrue(gesperrt.endsWith(Spielzeittext.schreibzugWartet(sprache)),
                "die Zeile muss den Zustand nennen: " + gesperrt);
        assertFalse(gesperrt.contains(abstand),
                "und sie darf nicht den Abstand nennen, in dem gerade NICHT geschrieben"
                        + " wird: " + gesperrt);
        assertTrue(gesperrt.contains("1 min"),
                "an der MENGE aendert die Sperre nichts - gemessen ist gemessen: "
                        + gesperrt);

        // Die Gegenprobe, damit die erste nicht auch gruen waere, wenn der Abstand
        // nirgends mehr steht: Ohne Sperre liest der Spieler genau daran ab, wann seine
        // Zeit gesichert ist.
        String offenGeschrieben = text(Spielzeittext.werte(sprache, stand, offen, wartend,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE), 2);

        assertTrue(offenGeschrieben.contains(abstand), offenGeschrieben);
    }

    @Test
    @DisplayName("Eine Sitzung ueber Mitternacht zaehlt nur teilweise zu heute")
    void mitternachtTeiltAuchDieAnzeige() {
        // 23:00 UTC ist in Europe/Berlin Mitternacht. Die halbe Minute davor gehoert dem
        // Vortag und darf nicht in der Zeile "heute" stehen.
        var stand = new Spielzeitstand(ANNA, LocalDate.of(2026, 11, 4), 600L, 0L);
        var offen = gezaehlt(Instant.parse("2026-11-03T22:59:30Z"), Duration.ofSeconds(60));

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE);

        assertTrue(text(zeilen, 0).contains("unter 1 min"),
                "nur 30 Sekunden gehoeren dem neuen Tag: " + text(zeilen, 0));
        assertTrue(text(zeilen, 1).contains("11 min"),
                "gesamt sind es 600 geschriebene plus 60 offene Sekunden: "
                        + text(zeilen, 1));
    }

    @Test
    @DisplayName("Die Zeile sagt, wenn gerade nicht gezaehlt wird - und bis wohin gezaehlt wird")
    void untaetigStehtDa() {
        var stand = new Spielzeitstand(ANNA, HEUTE, 600L, 600L);
        var offen = verworfen(Instant.parse("2026-11-03T12:00:00Z"));

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ofMinutes(12), Zeitwerte.VORGABE);

        assertTrue(text(zeilen, 2).contains("NICHT gezaehlt"), text(zeilen, 2));
        assertTrue(text(zeilen, 2).contains("12 min"),
                "wie lange schon: " + text(zeilen, 2));
        assertTrue(text(zeilen, 2).contains("10 min"),
                "und bis wohin gezaehlt wird: " + text(zeilen, 2));
    }

    @Test
    @DisplayName("Ist die Untaetigkeitsregel aus, sagt die Anzeige das")
    void ausgeschalteteRegelStehtDa() {
        var stand = new Spielzeitstand(ANNA, HEUTE, 600L, 600L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ofSeconds(30));
        Zeitwerte aus = new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60),
                Duration.ZERO);

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ofHours(3), aus);

        assertTrue(text(zeilen, 2).contains("zaehlt auf diesem Server mit"),
                "sonst wundert sich jemand, warum drei Stunden Abwesenheit zaehlen: "
                        + text(zeilen, 2));
        assertTrue(text(zeilen, 2).contains(Zeitwerte.PFAD_UNTAETIG),
                "und wo das steht: " + text(zeilen, 2));
    }

    @Test
    @DisplayName("Die Zeile heute nennt den Tag und die Tagesgrenze")
    void heuteNenntTagUndZone() {
        // Bei einer Tagesgrenze, die nicht UTC ist, ist genau das die Frage, um die es
        // geht: welcher Tag ist mit "heute" gemeint.
        var stand = new Spielzeitstand(ANNA, HEUTE, 0L, 0L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ZERO);

        String zeile = text(Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE), 0);

        assertTrue(zeile.contains("03.11.2026"), zeile);
        assertTrue(zeile.contains("Europe/Berlin"), zeile);
    }

    @Test
    @DisplayName("Dass vor dem Einbau nichts gezaehlt wurde, steht in der Anzeige")
    void hinweisAufDenEinbau() {
        // Geprueft wird die ZEILE und nicht die Konstante gegen Teile von sich selbst:
        // Solange der Befehl den Satz einzeln sendete, blieb diese Probe gruen, wenn jemand
        // die eine Sendezeile verlor - und genau dann sieht ein Spieler mit 200 Stunden auf
        // dem Server eine kleine Zahl ohne Erklaerung und meldet einen Fehler.
        var stand = new Spielzeitstand(ANNA, HEUTE, 600L, 600L);
        var offen = gezaehlt(Instant.parse("2026-11-03T12:00:00Z"), Duration.ofSeconds(30));

        List<Component> zeilen = Spielzeittext.werte(sprache, stand, offen, NICHTS_WARTET,
                NICHT_GESPERRT, Duration.ZERO, Zeitwerte.VORGABE);

        assertEquals(4, zeilen.size(), "heute, gesamt, Zustand und der Hinweis: "
                + Sprachprobe.bild(zeilen));
        // Gegen den SATZ und nicht gegen einen abgeschriebenen Wortlaut: Der Anker fuer das
        // Bild ist die festgenagelte Tafel in SpielzeitBefehleTest, hier wird zugesichert,
        // dass genau DIESER Satz die letzte Zeile ist.
        assertEquals(Sprachprobe.bild(Spielzeittext.hinweisZeile(sprache)),
                Sprachprobe.bild(zeilen.get(3)),
                "der Hinweis ist die letzte Zeile der Anzeige: "
                        + Sprachprobe.bild(zeilen));
    }
}
