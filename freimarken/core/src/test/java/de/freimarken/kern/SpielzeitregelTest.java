package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Spielzeitregel ohne Server, ohne Uhr und ohne Wartezeit - zwei Zeitpunkte und drei
 * Dauern hinein, ein Urteil heraus.
 *
 * <p>Die Proben, auf die es ankommt, sind die vier Grenzfaelle, die ein Betreiber nie
 * absichtlich herstellt: eine zurueckgestellte Uhr, ein Sprung nach vorn, die
 * Untaetigkeitsschranke genau auf der Grenze und die Schranke 0.
 */
class SpielzeitregelTest {

    private static final Instant MARKE = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration TAKT = Duration.ofSeconds(60);
    private static final Duration GRENZE = Duration.ofMinutes(10);
    private static final Duration AKTIV = Duration.ofSeconds(5);

    @Test
    @DisplayName("Ein gewoehnlicher Takt zaehlt die verstrichene Zeit in ganzen Sekunden")
    void gewoehnlicherTakt() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(61), AKTIV, GRENZE, TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT, urteil.ausgang());
        assertEquals(61L, urteil.sekunden());
        assertEquals(MARKE, urteil.von());
        assertEquals(MARKE.plusSeconds(61), urteil.bis());
        assertEquals(urteil.bis(), urteil.neueMarke());
    }

    @Test
    @DisplayName("Der Rest unter einer Sekunde bleibt an der Marke stehen")
    void restBleibtStehen() {
        // Ohne diese Zeile faellt je Takt durchschnittlich eine halbe Sekunde weg - immer
        // nach unten. Ueber 40 Stunden Spielzeit sind das rund 20 Minuten, also ein
        // Vielfaches dessen, was ein Absturz kostet.
        var ersterTakt = Spielzeitregel.pruefe(MARKE, MARKE.plusMillis(60_500), AKTIV,
                GRENZE, TAKT);
        var zweiterTakt = Spielzeitregel.pruefe(ersterTakt.neueMarke(),
                MARKE.plusMillis(121_000), AKTIV, GRENZE, TAKT);

        assertEquals(60L, ersterTakt.sekunden());
        assertEquals(61L, zweiterTakt.sekunden(),
                "die halbe Sekunde des ersten Taktes zaehlt beim zweiten mit");
        assertEquals(121L, ersterTakt.sekunden() + zweiterTakt.sekunden());
    }

    @Test
    @DisplayName("Weniger als eine Sekunde zaehlt nicht und geht auch nicht verloren")
    void unterEinerSekunde() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusMillis(400), AKTIV, GRENZE, TAKT);

        assertEquals(0L, urteil.sekunden());
        assertEquals(MARKE, urteil.neueMarke(), "die Marke bleibt, der Rest zaehlt spaeter");
    }

    @Test
    @DisplayName("Eine Untaetigkeit knapp unter der Grenze zaehlt noch")
    void knappUnterDerGrenze() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60),
                Duration.ofSeconds(599), GRENZE, TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT, urteil.ausgang());
        assertEquals(60L, urteil.sekunden());
    }

    @Test
    @DisplayName("Genau auf der Grenze zaehlt der Takt nicht mehr")
    void genauAufDerGrenze() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60), GRENZE, GRENZE, TAKT);

        assertEquals(Spielzeitregel.Ausgang.UNTAETIG, urteil.ausgang());
        assertEquals(0L, urteil.sekunden());
    }

    @Test
    @DisplayName("Ein verworfener Takt wird nicht angespart - die Marke rueckt trotzdem vor")
    void verworfenerTaktSpartNichtAn() {
        // Sonst sammelte ein abwesender Spieler seine Stunden und kassierte sie mit dem
        // ersten Schritt, den er tut.
        Instant jetzt = MARKE.plusSeconds(60);
        var untaetig = Spielzeitregel.pruefe(MARKE, jetzt, GRENZE, GRENZE, TAKT);

        assertEquals(jetzt, untaetig.neueMarke());

        var danach = Spielzeitregel.pruefe(untaetig.neueMarke(), jetzt.plusSeconds(60),
                AKTIV, GRENZE, TAKT);
        assertEquals(60L, danach.sekunden(),
                "nach der Rueckkehr zaehlt nur die Zeit seit der Rueckkehr");
    }

    @Test
    @DisplayName("Die Grenze 0 laesst alles zaehlen - der ehrliche Ausschalter")
    void grenzeNullZaehltAlles() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60),
                Duration.ofHours(12), Duration.ZERO, TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT, urteil.ausgang());
        assertEquals(60L, urteil.sekunden(),
                "eine 0 heisst 'Abwesenheit zaehlt mit' und nicht 'Schwelle null'");
    }

    @Test
    @DisplayName("Eine zurueckgestellte Uhr ergibt 0 und setzt die Marke neu an")
    void uhrZurueckgestellt() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.minusSeconds(3_600), AKTIV, GRENZE,
                TAKT);

        assertEquals(Spielzeitregel.Ausgang.UHR_ZURUECKGESTELLT, urteil.ausgang());
        assertEquals(0L, urteil.sekunden(), "negative Spielzeit darf nicht entstehen");
        assertEquals(MARKE.minusSeconds(3_600), urteil.neueMarke(),
                "ab jetzt wird von der neuen Uhrzeit aus gezaehlt, nicht von der alten Marke");
        assertTrue(urteil.meldepflichtig(), "eine verstellte Uhr muss nachlesbar sein");
    }

    @Test
    @DisplayName("Auch bei untaetigem Spieler wird die zurueckgestellte Uhr gemeldet")
    void uhrVorUntaetigkeit() {
        // Sonst verdeckt der Alltagsfall den Fehlerfall - und die Spielzeit hat keinen
        // Zeugen, an dem er spaeter noch auffiele.
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.minusSeconds(60), GRENZE, GRENZE,
                TAKT);

        assertEquals(Spielzeitregel.Ausgang.UHR_ZURUECKGESTELLT, urteil.ausgang());
    }

    @Test
    @DisplayName("Ein Sprung nach vorn wird auf zehn Taktlaengen geklemmt und gemeldet")
    void sprungNachVornWirdGeklemmt() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(7_200), AKTIV, GRENZE,
                TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEKLEMMT, urteil.ausgang());
        assertEquals(600L, urteil.sekunden(), "zehn Taktlaengen und nicht zwei Stunden");
        assertEquals(MARKE, urteil.von());
        assertEquals(MARKE.plusSeconds(600), urteil.bis());
        assertEquals(MARKE.plusSeconds(7_200), urteil.neueMarke(),
                "der Ueberschuss wird weggeworfen, nicht angespart");
        assertTrue(urteil.meldepflichtig());
    }

    @Test
    @DisplayName("Genau zehn Taktlaengen zaehlen noch voll - ein lahmer Server verliert nichts")
    void genauZehnTaktlaengen() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(600), AKTIV, GRENZE, TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT, urteil.ausgang());
        assertEquals(600L, urteil.sekunden());
    }

    @Test
    @DisplayName("Die Klemme richtet sich nach dem eingestellten Takt, nicht nach einer Konstante")
    void klemmeHaengtAmTakt() {
        // Die Probe dafuer, dass zeit.takt-sekunden wirklich wirkt. Stuende der Takt als
        // Konstante im Rechenweg, waere die Zeile in der Einstellungsdatei wirkungslos.
        Instant jetzt = MARKE.plusSeconds(7_200);

        assertEquals(100L, Spielzeitregel.pruefe(MARKE, jetzt, AKTIV, GRENZE,
                Duration.ofSeconds(10)).sekunden());
        assertEquals(6_000L, Spielzeitregel.pruefe(MARKE, jetzt, AKTIV, GRENZE,
                Duration.ofSeconds(600)).sekunden());
    }

    @Test
    @DisplayName("Die Schranke richtet sich nach der eingestellten Grenze")
    void schrankeHaengtAnDerGrenze() {
        Duration elfMinuten = Duration.ofMinutes(11);

        assertEquals(Spielzeitregel.Ausgang.UNTAETIG,
                Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60), elfMinuten,
                        Duration.ofMinutes(10), TAKT).ausgang());
        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT,
                Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60), elfMinuten,
                        Duration.ofMinutes(30), TAKT).ausgang());
    }

    @Test
    @DisplayName("Eine Grenze unter der Taktlaenge wirft aktive Zeit weg - darum ist sie verboten")
    void grenzeUnterDerTaktlaengeWirftAktiveZeitWeg() {
        // Der eine Fall, den die uebrigen Proben nicht treffen: Sie rechnen alle mit 60 s
        // Takt und 10 min Grenze, also mit einer Grenze, die den Takt deckt. Hier ist es
        // umgekehrt - 600 s Takt, 60 s Grenze. Der Spieler baut 540 s aktiv und legt dann
        // das Tastenbrett hin; beim Taktschlag liest getIdleDuration() 60 s ab.
        Duration langerTakt = Duration.ofSeconds(600);
        Duration kurzeGrenze = Duration.ofMinutes(1);

        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(600), kurzeGrenze,
                kurzeGrenze, langerTakt);

        assertEquals(Spielzeitregel.Ausgang.UNTAETIG, urteil.ausgang());
        assertEquals(0L, urteil.sekunden(),
                "die Grenze ist eine Stufe: der GANZE Takt faellt, samt der 540 aktiv"
                        + " gespielten Sekunden darin");
        assertEquals(MARKE.plusSeconds(600), urteil.neueMarke(),
                "und angespart wird davon auch nichts");
        assertFalse(urteil.meldepflichtig(),
                "stumm obendrein - ein verworfener Takt schreibt keine Zeile, und die"
                        + " Spielzeit hat keinen Zeugen, an dem es spaeter auffiele");

        // Deshalb ist nicht diese Regel geaendert, sondern das Paar verboten: Die Regel
        // bekommt beide Dauern einzeln und kann nur die Stufe halten; nur den untaetigen
        // TEIL abzuziehen gaebe eine Genauigkeit vor, die getIdleDuration() nicht hat (sie
        // nennt den Stand, nicht den Verlauf). Die Schranke sitzt an EINER Stelle:
        assertFalse(Zeitwerte.brauchbareGrenzeZumTakt(600L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new Zeitwerte(
                Zeitwerte.VORGABE_ZONE, langerTakt, kurzeGrenze));
    }

    @Test
    @DisplayName("Eine negative Untaetigkeit ist ein Messfehler und kostet den Spieler nichts")
    void negativeUntaetigkeit() {
        var urteil = Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60),
                Duration.ofSeconds(-5), GRENZE, TAKT);

        assertEquals(Spielzeitregel.Ausgang.GEZAEHLT, urteil.ausgang());
        assertEquals(60L, urteil.sekunden());
    }

    @Test
    @DisplayName("Nur ein Fehlerfall ist meldepflichtig, der Alltag nicht")
    void nurFehlerfaelleWerdenGemeldet() {
        assertFalse(Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60), AKTIV, GRENZE, TAKT)
                .meldepflichtig(), "ein gewoehnlicher Takt schreibt nichts ins Protokoll");
        assertFalse(Spielzeitregel.pruefe(MARKE, MARKE.plusSeconds(60), GRENZE, GRENZE, TAKT)
                .meldepflichtig(), "Untaetigkeit ist Alltag und wuerde das Protokoll fuellen");
    }

    @Test
    @DisplayName("Das Urteil nennt die Spanne, die die Tagesgrenze teilen soll")
    void spanneTrifftDieTagesgrenze() {
        // Die Summe der Tagesanteile muss genau die gezaehlten Sekunden sein - das ist die
        // Nahtstelle zwischen den beiden Regeln.
        Instant kurzVorMitternacht = Instant.parse("2026-11-02T22:59:30Z");
        var urteil = Spielzeitregel.pruefe(kurzVorMitternacht,
                kurzVorMitternacht.plusSeconds(60), AKTIV, GRENZE, TAKT);

        long summe = Tagesgrenze.teile(urteil.von(), urteil.bis(),
                        java.time.ZoneId.of("Europe/Berlin")).stream()
                .mapToLong(Tagesgrenze.Tagesanteil::sekunden).sum();

        assertEquals(urteil.sekunden(), summe);
    }

    @Test
    @DisplayName("Ohne Marke, Takt oder Grenze geht nichts")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(null, MARKE, AKTIV, GRENZE, TAKT));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, null, AKTIV, GRENZE, TAKT));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, MARKE, null, GRENZE, TAKT));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, MARKE, AKTIV, null, TAKT));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, MARKE, AKTIV, GRENZE, null));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, MARKE, AKTIV, GRENZE, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> Spielzeitregel.pruefe(MARKE, MARKE, AKTIV,
                        Duration.ofMinutes(-1), TAKT));
    }
}
