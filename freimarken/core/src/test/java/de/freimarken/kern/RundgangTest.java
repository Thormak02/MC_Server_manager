package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Rundgang - das Verzeichnis, der Stand und was aus einem Schritt wird.
 *
 * <p>Was hier gruen ist, gilt fuer die Wache UND fuer den Aufstiegslauf: Beide fragen
 * diesen Halter. Die teuersten Zusagen sind zwei, und beide haben mit dem LEEREN
 * Verzeichnis zu tun - dem ausgelieferten Zustand. Ein Rundgang mit null Stationen darf
 * nicht "abgeschlossen" sein, sonst waere eine Rangbedingung darauf ab Werk fuer jeden
 * erfuellt; und er darf nicht werfen, sonst stuerzte jeder Schritt auf einem Server, der
 * seine Gebaeude noch nicht gebaut hat.
 */
@DisplayName("Der Rundgang")
class RundgangTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-21T12:00:00Z");
    private static final UUID ANNA = new UUID(0L, 1L);
    private static final Clock UHR = Clock.fixed(MITTAGS, ZoneOffset.UTC);

    /** Eine Station mit einem Quader, der genau einen Block umfasst. */
    private static Rundgangsstation station(String name, int x) {
        return new Rundgangsstation(name, new Quader(x, -60, 0, x, -60, 0));
    }

    private static Rundgang mit(Rundgangsablage ablage, Rundgangsstation... stationen) {
        return new Rundgang(ablage, Rundgangsverzeichnis.ausStationen(
                List.of(stationen)), UHR);
    }

    @Nested
    @DisplayName("die Ablage haelt den Vertrag")
    class AblageHaeltDenVertrag extends RundgangVertrag {

        @Override
        protected Rundgangsablage ablage() {
            return new SpeicherRundgang();
        }
    }

    @Test
    @DisplayName("1 - ohne Stationen gibt es keinen Rundgang, und niemand hat ihn")
    void ohneStationenGibtEsKeinen() {
        // DER AUSGELIEFERTE ZUSTAND. Waere er mit null Stationen "abgeschlossen", waere
        // eine Rangbedingung darauf ab Werk fuer JEDEN erfuellt - der Betreiber haette
        // eine Stufe verschenkt, ohne eine Zeile zu schreiben.
        Rundgang rundgang = mit(new SpeicherRundgang());

        assertFalse(rundgang.gibtEs());
        assertEquals(0, rundgang.stationen());
        assertEquals(0, rundgang.gesehen(ANNA));
        assertFalse(rundgang.abgeschlossen(ANNA),
                "ein Rundgang, den es nicht gibt, ist nicht abgeschlossen");
    }

    @Test
    @DisplayName("2 - die erste Station ist NEU, die zweite schliesst ab")
    void zweiStationenEinAbschluss() {
        Rundgang rundgang = mit(new SpeicherRundgang(),
                station("buergeramt", 10), station("zunfthaus", 20));

        assertEquals(Rundgang.Bescheid.NEU, rundgang.betreten(ANNA, "buergeramt"));
        assertEquals(1, rundgang.gesehen(ANNA));
        assertFalse(rundgang.abgeschlossen(ANNA));

        assertEquals(Rundgang.Bescheid.ABGESCHLOSSEN,
                rundgang.betreten(ANNA, "zunfthaus"));
        assertTrue(rundgang.abgeschlossen(ANNA));
    }

    @Test
    @DisplayName("3 - ein zweiter Schritt in dieselbe Station meldet SCHON_GESEHEN")
    void zweiterSchrittMeldetSchonGesehen() {
        // Der haeufigste Fall im Betrieb: Ein Spieler laeuft durch dieselben Gebaeude
        // immer wieder. Haette er jedes Mal NEU gemeldet, lase er den Stationstext bei
        // jedem Schritt durch die Tuer.
        Rundgang rundgang = mit(new SpeicherRundgang(), station("buergeramt", 10));
        rundgang.betreten(ANNA, "buergeramt");

        assertEquals(Rundgang.Bescheid.SCHON_GESEHEN,
                rundgang.betreten(ANNA, "buergeramt"));
    }

    @Test
    @DisplayName("4 - ABGESCHLOSSEN kommt genau EINMAL")
    void abschlussKommtEinmal() {
        // Der Abschluss ist der Augenblick, in dem die Rangbedingung umschlaegt - und er
        // darf genau einmal gemeldet werden. Haette der Aufrufer selbst gezaehlt, meldete
        // er ihn bei jedem spaeteren Schritt erneut.
        Rundgang rundgang = mit(new SpeicherRundgang(), station("buergeramt", 10));

        assertEquals(Rundgang.Bescheid.ABGESCHLOSSEN,
                rundgang.betreten(ANNA, "buergeramt"));
        assertEquals(Rundgang.Bescheid.SCHON_GESEHEN,
                rundgang.betreten(ANNA, "buergeramt"));
    }

    @Test
    @DisplayName("5 - eine NEUE Station in der Datei macht den Rundgang wieder offen")
    void neueStationMachtIhnOffen() {
        // Der Betreiber darf seinen Weg aendern. Mit einer ZAHL statt einer Zeile je
        // Station waere der Rundgang nach dem Hinzufuegen weiter abgeschlossen - und ein
        // Spieler haette eine Station nie gesehen, die zum Weg gehoert.
        var ablage = new SpeicherRundgang();
        Rundgang vorher = mit(ablage, station("buergeramt", 10));
        vorher.betreten(ANNA, "buergeramt");
        assertTrue(vorher.abgeschlossen(ANNA));

        Rundgang nachher = mit(ablage, station("buergeramt", 10),
                station("zunfthaus", 20));

        assertFalse(nachher.abgeschlossen(ANNA),
                "ein Weg mit einer neuen Station ist ein neuer Weg");
        assertEquals(1, nachher.gesehen(ANNA));
    }

    @Test
    @DisplayName("6 - eine ENTFERNTE Station zaehlt nicht mehr mit, und das ist kein"
            + " Fehler")
    void entfernteStationZaehltNichtMehr() {
        var ablage = new SpeicherRundgang();
        Rundgang vorher = mit(ablage, station("buergeramt", 10), station("altes", 30));
        vorher.betreten(ANNA, "altes");

        Rundgang nachher = mit(ablage, station("buergeramt", 10));

        // Die Zeile auf "altes" bleibt in der Ablage stehen - sie zaehlt nur nicht mehr.
        assertEquals(0, nachher.gesehen(ANNA));
        assertFalse(nachher.abgeschlossen(ANNA));
    }

    @Test
    @DisplayName("7 - das Verzeichnis findet die Station am ORT")
    void verzeichnisFindetDenOrt() {
        // DIE EINE FRAGE, DIE DIE WACHE STELLT. Sie gibt null fuer einen Ort ohne
        // Station - ein Spieler laeuft die meiste Zeit nirgendwo hin.
        var verzeichnis = Rundgangsverzeichnis.ausStationen(List.of(
                station("buergeramt", 10), station("zunfthaus", 20)));

        assertEquals("zunfthaus", verzeichnis.anDemOrt(20, -60, 0).schluessel());
        assertEquals("buergeramt", verzeichnis.anDemOrt(10, -60, 0).schluessel());
        assertEquals(null, verzeichnis.anDemOrt(15, -60, 0));
        // Die HOEHE gehoert dazu: Altfurt ist flach, und ein Quader nur in der Ebene
        // zaehlte einen Spieler auch, wenn er zwanzig Bloecke darueber fliegt.
        assertEquals(null, verzeichnis.anDemOrt(10, -40, 0),
                "wer darueber fliegt, ist nicht in der Station");
    }

    @Test
    @DisplayName("8 - die Reihenfolge der Datei bleibt - sie ist der Weg")
    void reihenfolgeBleibt() {
        var verzeichnis = Rundgangsverzeichnis.ausStationen(List.of(
                station("stadttor", 40), station("buergeramt", 10),
                station("zunfthaus", 20)));

        assertEquals(List.of("stadttor", "buergeramt", "zunfthaus"),
                verzeichnis.namen());
    }

    @Test
    @DisplayName("9 - der ERSTE Eintrag zu einem Namen gewinnt")
    void ersterEintragGewinnt() {
        var verzeichnis = Rundgangsverzeichnis.ausStationen(List.of(
                station("buergeramt", 10), station("buergeramt", 99)));

        assertEquals(1, verzeichnis.anzahl());
        assertEquals(10, verzeichnis.mitNamen("buergeramt").bereich().vonX());
    }

    @Test
    @DisplayName("10 - eine Station, die das Verzeichnis nicht kennt, wird abgewiesen")
    void unbekannteStationWirdAbgewiesen() {
        // Wer hierher kommt, hat die Station nicht ueber anDemOrt gefunden - und traegt
        // dann etwas ein, das es nicht gibt. Das ist ein Fehler im Programm und keine
        // Fehleingabe, darum ein Wurf.
        Rundgang rundgang = mit(new SpeicherRundgang(), station("buergeramt", 10));

        assertThrows(IllegalArgumentException.class,
                () -> rundgang.betreten(ANNA, "gibtsnicht"));
    }

    @Test
    @DisplayName("11 - zu viele Stationen und eine zu grosse werden abgewiesen")
    void grenzenWerdenGehalten() {
        List<Rundgangsstation> zuViele = new java.util.ArrayList<>();
        for (int nummer = 0; nummer <= Rundgangsverzeichnis.HOECHSTENS; nummer++) {
            zuViele.add(station("s" + nummer, nummer));
        }
        assertThrows(IllegalArgumentException.class,
                () -> Rundgangsverzeichnis.ausStationen(zuViele));

        // UND DIE GEGENRICHTUNG: genau die Hoechstzahl geht durch.
        assertEquals(Rundgangsverzeichnis.HOECHSTENS,
                Rundgangsverzeichnis.ausStationen(
                        zuViele.subList(0, Rundgangsverzeichnis.HOECHSTENS)).anzahl());

        // Eine Station von der Groesse Altfurts waere mit dem ersten Schritt gesehen.
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgangsstation("zugross",
                        new Quader(0, -61, 0, 200, 100, 200)));
    }

    @Test
    @DisplayName("12 - ohne Ablage, Verzeichnis oder Uhr wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        var verzeichnis = Rundgangsverzeichnis.ohneStationen();

        assertThrows(IllegalArgumentException.class,
                () -> new Rundgang(null, verzeichnis, UHR));
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgang(new SpeicherRundgang(), null, UHR));
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgang(new SpeicherRundgang(), verzeichnis, null));
        assertThrows(IllegalArgumentException.class,
                () -> Rundgangsverzeichnis.ausStationen(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgangsstation("ohne-bereich", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgangsstation("Mit Leerzeichen",
                        new Quader(0, -60, 0, 1, -60, 1)));
    }
}
