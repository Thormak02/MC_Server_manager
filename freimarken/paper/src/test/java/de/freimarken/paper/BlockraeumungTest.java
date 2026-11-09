package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.SpeicherBlockbuch;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Aufraeumen des Blockbuchs - Frist, Happen und der Merker.
 *
 * <p>Ohne Bukkit und ohne Datenbank: Die Raeumung rechnet nur eine Grenze aus und gibt
 * sie weiter. Geprueft wird mit {@link SpeicherBlockbuch}, also derselben Ablage, die
 * auch den geteilten Vertrag haelt.
 *
 * <p><b>Die wichtigste Probe ist {@link #merkerNurBeiVollstaendigerRaeumung}.</b> Wuerde
 * der Tagesmerker schon nach einem vollen Happen gesetzt, kaeme der naechste Versuch erst
 * morgen - und bei einem Rueckstand von Wochen holte die Frist nie auf: Die Tabelle
 * wuechse schneller, als sie geraeumt wird. Nichts bricht dabei, nichts wird rot; die
 * Tabelle waechst nur.
 */
@DisplayName("Blockraeumung")
class BlockraeumungTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Ein Nachmittag, weit weg von jeder Tagesgrenze. */
    private static final Instant JETZT = Instant.parse("2026-11-09T14:30:00Z");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 9);

    private final SpeicherBlockbuch buch = new SpeicherBlockbuch();

    private static Blockbuchwerte werte(int fristTage, int happen) {
        return new Blockbuchwerte(fristTage, happen,
                new Blockbuchwerte.Auswahl(Set.of(Blocktat.SETZEN), true), Map.of(),
                Set.of(), Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);
    }

    /** Der Stand mit der Vorgabezone (Europe/Berlin) - daher kommt der Tageswechsel. */
    private static Wirtschaftsstand stand() {
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
    }

    private Blockraeumung raeumung(int fristTage, int happen) {
        return new Blockraeumung(buch, werte(fristTage, happen), stand(),
                Clock.fixed(JETZT, ZoneOffset.UTC), Logger.getLogger("Probe"));
    }

    /** Legt eine Zeile mit diesem Zeitpunkt ins Buch. */
    private void zeileAm(Instant wann, String name) {
        buch.schreibe(List.of(Blockzeile.neu(wann, UUID.randomUUID(),
                new Blockort(ALTFURT, 7, -60, 13), Blocktat.SETZEN, true, name)));
    }

    /** Welche Zeilen noch im Buch stehen - so liest sich eine Erwartung. */
    private List<String> nochDa() {
        List<String> namen = new ArrayList<>();
        for (Blockzeile zeile : buch.amOrt(new Blockort(ALTFURT, 7, -60, 13), 100)) {
            namen.add(zeile.block());
        }
        return namen;
    }

    @Test
    @DisplayName("Die Grenze liegt am TAGESBEGINN, nicht auf die Stunde genau")
    void grenzeLiegtAmTagesbeginn() {
        // Eine bewusste Rundung zur sicheren Seite: Bei dreissig Tagen Frist bleiben 31
        // Kalendertage stehen - der heutige und dreissig davor. Eine Beschwerde, die am
        // dreissigsten Tag kommt, findet ihre Zeile noch. Auf die Stunde genau haenge es
        // an der UHRZEIT der Anfrage, ob die Moderation sie sieht.
        Instant grenze = raeumung(30, 500).grenzeFuer(HEUTE);

        assertEquals(LocalDate.of(2026, 10, 10).atStartOfDay(BERLIN).toInstant(), grenze,
                "dreissig Tage vor dem 09.11. ist der 10.10., und zwar sein BEGINN");
    }

    @Test
    @DisplayName("Die Grenze folgt der eingestellten ZONE, nicht UTC")
    void grenzeFolgtDerZone() {
        // Kein NOW() - INTERVAL in SQL: Das fragte die Uhr und die Zone des
        // Datenbankrechners, und die eingestellte Zone waere stumm wirkungslos -
        // sichtbar erst an Zeilen, die einen Tag zu frueh oder zu spaet verschwinden.
        Instant grenze = raeumung(30, 500).grenzeFuer(HEUTE);

        assertFalse(grenze.equals(LocalDate.of(2026, 10, 10)
                        .atStartOfDay(ZoneOffset.UTC).toInstant()),
                "Berlin liegt im Oktober zwei Stunden vor UTC - waere hier UTC gerechnet,"
                        + " traefe die Grenze zwei Stunden zu spaet");
    }

    @Test
    @DisplayName("Was aelter als die Frist ist, geht weg - der Rest bleibt")
    void altesGehtWegNeuesBleibt() {
        zeileAm(JETZT.minusSeconds(60L * 60 * 24 * 40), "vierzig_tage_alt");
        zeileAm(JETZT.minusSeconds(60L * 60 * 24 * 31), "einunddreissig_tage_alt");
        zeileAm(JETZT.minusSeconds(60L * 60 * 24 * 5), "fuenf_tage_alt");

        Blockraeumung.Ergebnis ergebnis = raeumung(30, 500).imTakt();

        assertEquals(2, ergebnis.geloescht());
        assertFalse(ergebnis.mehrDa());
        assertFalse(ergebnis.gestoert());
        assertEquals(List.of("fuenf_tage_alt"), nochDa());
    }

    @Test
    @DisplayName("Der Happen wird eingehalten - und sagt, dass mehr da ist")
    void happenWirdEingehalten() {
        for (int nummer = 0; nummer < 7; nummer++) {
            zeileAm(JETZT.minusSeconds(60L * 60 * 24 * 40 + nummer), "alt_" + nummer);
        }

        Blockraeumung.Ergebnis ergebnis = raeumung(30, 3).imTakt();

        assertEquals(3, ergebnis.geloescht(), "hoechstens drei je Schlag");
        assertTrue(ergebnis.mehrDa(), "und es ist noch mehr da");
        assertEquals(4, nochDa().size());
    }

    @Test
    @DisplayName("Der Merker wird NUR bei vollstaendiger Raeumung gesetzt")
    void merkerNurBeiVollstaendigerRaeumung() {
        // DIE WICHTIGSTE PROBE DIESER KLASSE, und der Unterschied zum Mietlauf. Jener ist
        // nach einem Durchgang fertig und darf "heute erledigt" vermerken. Diese Raeumung
        // ist es nicht: Reicht der Happen nicht, liegt der Rest noch da. Wuerde der
        // Merker trotzdem gesetzt, kaeme der naechste Versuch erst MORGEN - und bei einem
        // Rueckstand von Wochen holte die Frist nie auf.
        for (int nummer = 0; nummer < 7; nummer++) {
            zeileAm(JETZT.minusSeconds(60L * 60 * 24 * 40 + nummer), "alt_" + nummer);
        }
        Blockraeumung raeumung = raeumung(30, 3);

        assertEquals(3, raeumung.imTakt().geloescht());
        assertEquals(3, raeumung.imTakt().geloescht(),
                "derselbe Tag, zweiter Schlag - der Merker darf noch nicht stehen");
        Blockraeumung.Ergebnis letzter = raeumung.imTakt();
        assertEquals(1, letzter.geloescht(), "der dritte Schlag findet nur noch den Rest");
        assertFalse(letzter.mehrDa(), "und damit ist alles weg");
        assertEquals(List.of(), nochDa());

        assertEquals(0, raeumung.imTakt().geloescht(),
                "JETZT steht der Merker - der naechste Schlag am selben Tag tut nichts");
    }

    @Test
    @DisplayName("Ist heute schon geraeumt, geht keine Abfrage mehr hinaus")
    void tagesschrankeSpartDieAbfrage() {
        // Der Takt schlaegt alle zehn bis sechshundert Sekunden, und an fast allen
        // Schlaegen ist nichts zu tun. Ohne die Schranke ginge bei jedem ein DELETE ueber
        // das Netz, das nichts findet - auf der groessten Tabelle des Projekts.
        var zaehler = new int[] {0};
        Blockbuchablage zaehlende = new Blockbuchablage() {
            @Override
            public int schreibe(List<Blockzeile> zeilen) {
                return buch.schreibe(zeilen);
            }

            @Override
            public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
                return buch.amOrt(ort, hoechstens);
            }

            @Override
            public List<Blockzeile> vonSpieler(UUID spieler, int hoechstens) {
                return buch.vonSpieler(spieler, hoechstens);
            }

            @Override
            public int raeumeAuf(Instant aelterAls, int hoechstens) {
                zaehler[0]++;
                return buch.raeumeAuf(aelterAls, hoechstens);
            }
        };
        Blockraeumung raeumung = new Blockraeumung(zaehlende, werte(30, 500),
                stand(), Clock.fixed(JETZT, ZoneOffset.UTC),
                Logger.getLogger("Probe"));

        raeumung.imTakt();
        assertEquals(1, zaehler[0], "der erste Schlag raeumt");
        raeumung.imTakt();
        raeumung.imTakt();
        assertEquals(1, zaehler[0],
                "die folgenden Schlaege am selben Tag fragen gar nicht mehr");
    }

    @Test
    @DisplayName("Eine gestoerte Ablage setzt den Merker NICHT und wirft nicht nach aussen")
    void stoerungWirdWiederholtUndNichtGeworfen() {
        // Gerufen wird aus dem Spielzeittakt, und eine Ausnahme dort waere nicht eine
        // verlorene Raeumung, sondern die Spielzeit aller Spieler bis zum naechsten
        // Neustart. Der Merker bleibt offen, damit der naechste Takt es erneut versucht.
        var versuche = new int[] {0};
        Blockbuchablage gestoert = new Blockbuchablage() {
            @Override
            public int schreibe(List<Blockzeile> zeilen) {
                return 0;
            }

            @Override
            public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
                return List.of();
            }

            @Override
            public List<Blockzeile> vonSpieler(UUID spieler, int hoechstens) {
                return List.of();
            }

            @Override
            public int raeumeAuf(Instant aelterAls, int hoechstens) {
                versuche[0]++;
                throw new IllegalStateException("Datenbank weg");
            }
        };
        Blockraeumung raeumung = new Blockraeumung(gestoert, werte(30, 500),
                stand(), Clock.fixed(JETZT, ZoneOffset.UTC),
                Logger.getLogger("Probe"));

        Blockraeumung.Ergebnis erster = raeumung.imTakt();

        assertTrue(erster.gestoert());
        assertEquals(0, erster.geloescht());
        raeumung.imTakt();
        assertEquals(2, versuche[0],
                "der naechste Takt versucht es ERNEUT - der Merker darf nicht stehen");
    }

    @Test
    @DisplayName("Ein leeres Buch zu raeumen ist kein Fehler")
    void leeresBuchIstKeinFehler() {
        Blockraeumung.Ergebnis ergebnis = raeumung(30, 500).imTakt();

        assertEquals(0, ergebnis.geloescht());
        assertFalse(ergebnis.mehrDa());
        assertFalse(ergebnis.gestoert());
    }

    @Test
    @DisplayName("Ohne Pflichtangabe wird die Raeumung nicht gebaut")
    void ohnePflichtangabeWirdAbgewiesen() {
        Blockbuchwerte echteWerte = werte(30, 500);
        Wirtschaftsstand stand = stand();
        Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);
        Logger log = Logger.getLogger("Probe");

        assertThrows(IllegalArgumentException.class,
                () -> new Blockraeumung(null, echteWerte, stand, uhr, log));
        assertThrows(IllegalArgumentException.class,
                () -> new Blockraeumung(buch, null, stand, uhr, log));
        assertThrows(IllegalArgumentException.class,
                () -> new Blockraeumung(buch, echteWerte, null, uhr, log));
        assertThrows(IllegalArgumentException.class,
                () -> new Blockraeumung(buch, echteWerte, stand, null, log));
    }
}
