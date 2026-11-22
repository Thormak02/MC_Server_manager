package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import de.freimarken.kern.Rundgang;
import de.freimarken.kern.Rundgangsstation;
import de.freimarken.kern.Rundgangsverzeichnis;
import de.freimarken.kern.SpeicherRundgang;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was ein Spieler beim Betreten einer Station liest.
 *
 * <p><b>Geprueft wird {@code zeilen} und nicht das Bewegungsereignis.</b>
 * {@code PlayerMoveEvent} braucht zwei {@code Location} mit echten Welten, und was es
 * ausloest, ist Bukkit-Arbeit; die Entscheidung dahinter - ob eine Station neu ist, ob
 * sie die letzte war - liegt in {@link Rundgang} und laeuft ohne Server
 * ({@code RundgangTest}). Was hier geprueft wird, ist das DAZWISCHEN: welche Zeilen aus
 * einem Bescheid werden, mit welchen Zahlen und in welcher Reihenfolge.
 *
 * <p>Dass die Wache am Schritt haengt und nur bei vorhandenen Stationen angemeldet wird,
 * haelt eine Quelltexttafel in {@code GrenzeTest} (31dk) - dieselbe Teilung wie bei der
 * Torwache.
 */
@DisplayName("Die Rundgangswache")
class RundgangswacheTest {

    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    private static Rundgangsstation station(String name, int x) {
        return new Rundgangsstation(name, new Quader(x, -60, 0, x + 2, -58, 2));
    }

    private static Rundgang rundgang(Rundgangsstation... stationen) {
        return new Rundgang(new SpeicherRundgang(),
                Rundgangsverzeichnis.ausStationen(List.of(stationen)), UHR);
    }

    /**
     * Die Sprache mit zwei Stationszeilen - ueber den ECHTEN Weg.
     *
     * <p>Die Zeilen stehen NICHT in der ausgelieferten Vorlage: Welche Stationen es gibt,
     * bestimmt der Betreiber. Darum geht diese Probe durch {@code Sprachliste.ausQuelle}
     * und tippt eine Betreiberdatei - und prueft damit gleich mit, dass ein Betreiber
     * diese Zeilen ueberhaupt SETZEN kann. Mit {@code Sprachprobe.mitZeilen} ginge es
     * nicht: Jene weist einen Pfad ab, den die Vorlage nicht kennt, und das ist richtig -
     * eine Probe, die einen erfundenen Pfad ueberschreibt, prueft nichts.
     */
    private static Sprache spracheMitStationen() {
        // EIN TEXTBLOCK und keine Zeichenkette mit Escapes: Die Anfuehrungszeichen der
        // YAML-Werte stehen hier woertlich, und die Datei liest sich wie die, die ein
        // Betreiber tippt.
        //
        // DIE NAMEN TRAGEN KEINEN FARBTAG, und das hat der Lader dieser Probe gesagt:
        // Ein Art.WORT wird in einen anderen Satz EINGESETZT und ist dort ein Blatt - ein
        // Farbtag darin stuende dem Spieler woertlich im Chat. Die Farbe kommt aus der
        // Zeile, die ihn einsetzt (rundgang.station-entdeckt traegt <kopf>).
        var gelesen = Sprachliste.ausQuelle("""
                rundgang:
                  station:
                    zunfthaus:
                      name: "Das Zunfthaus"
                      text: "<fliesstext>Hier waehlst du einen Beruf mit /beruf.</fliesstext>"
                    muenzhaus:
                      name: "Das Muenzhaus"
                      text: "<fliesstext>Hier gibt es Ware fuer Goldmark.</fliesstext>"
                """, Sprachprobe.ausDemJar());
        assertEquals(List.of(), gelesen.klagen(),
                "ein Betreiber MUSS diese Zeilen setzen koennen: " + gelesen.klagen());
        return new Sprache(new Sprachstand(gelesen.buch()));
    }

    @Test
    @DisplayName("1 - die erste Station: Kopfzeile mit Fortschritt und ihr Text")
    void ersteStationZweiZeilen() {
        var rundgang = rundgang(station("zunfthaus", 10), station("muenzhaus", 20));
        var wache = new Rundgangswache(rundgang, null, spracheMitStationen());
        var station = rundgang.verzeichnis().mitNamen("zunfthaus");

        List<String> gelesen = wache.zeilen(station, 1, false).stream()
                .map(Sprachprobe::bild).toList();

        // DIE REIHENFOLGE: erst wo er ist, dann was man dort tut. Umgekehrt stuende die
        // Erklaerung vor ihrer Ueberschrift.
        assertEquals(List.of(
                        "&#c6a15bDas Zunfthaus&#6e7a82 - Station 1 von 2",
                        "&#a5b4bfHier waehlst du einen Beruf mit /beruf."),
                gelesen);
    }

    @Test
    @DisplayName("2 - die LETZTE Station bekommt den Abschluss dazu")
    void letzteStationMitAbschluss() {
        var rundgang = rundgang(station("zunfthaus", 10), station("muenzhaus", 20));
        var wache = new Rundgangswache(rundgang, null, spracheMitStationen());
        var station = rundgang.verzeichnis().mitNamen("muenzhaus");

        List<String> gelesen = wache.zeilen(station, 2, true).stream()
                .map(Sprachprobe::bild).toList();

        assertEquals(4, gelesen.size(), gelesen.toString());
        assertEquals("&#c6a15bDas Muenzhaus&#6e7a82 - Station 2 von 2", gelesen.get(0));
        assertEquals("&#73bf96Rundgang geschafft - du kennst Altfurt!", gelesen.get(2));
        // DIE VIERTE ZEILE ist die zweite des mehrzeiligen Abschlusssatzes - das
        // "naechste Ziel" aus Plan 10. Ohne sie waere der Abschluss ein Lob ohne Weg.
        assertTrue(gelesen.get(3).contains("/parzelle nehmen"), gelesen.get(3));
    }

    @Test
    @DisplayName("3 - fehlt die Sprachzeile, steht der PFAD da - und die Station bleibt")
    void ohneSprachzeileStehtDerPfad() {
        // Eine weggefallene Station waere ein Rundgang, den niemand abschliessen kann,
        // und der Spieler suchte den Fehler bei sich. Der Start nennt sie
        // (nenneStationenOhneWort); hier steht, was der Spieler bis dahin sieht.
        var rundgang = rundgang(station("kirche", 30));
        var wache = new Rundgangswache(rundgang, null,
                Sprachprobe.spracheAusDemJar());

        List<String> gelesen = wache.zeilen(
                rundgang.verzeichnis().mitNamen("kirche"), 1, true).stream()
                .map(Sprachprobe::bild).toList();

        assertFalse(gelesen.isEmpty());
        assertTrue(gelesen.get(0).contains("rundgang.station.kirche.name"),
                "die Kopfzeile muss den Pfad nennen: " + gelesen.get(0));
    }

    @Test
    @DisplayName("4 - ohne Stationen meldet die Wache sich nicht an")
    void ohneStationenKeineAnmeldung() {
        // Der ausgelieferte Zustand. Ab Werk hoert hier niemand zu, und kein Schritt
        // kostet etwas - dieselbe Entscheidung wie bei der Torwache (if hatTore()).
        var leer = new Rundgang(new SpeicherRundgang(),
                Rundgangsverzeichnis.ohneStationen(), UHR);

        assertFalse(new Rundgangswache(leer, null, Sprachprobe.spracheAusDemJar())
                .hatStationen());
        // UND die Gegenrichtung braucht eine WELT: Ohne sie weiss die Wache nicht, wo
        // die Bereiche liegen, und darf nicht zuhoeren.
        assertFalse(new Rundgangswache(rundgang(station("zunfthaus", 10)), null,
                Sprachprobe.spracheAusDemJar()).hatStationen(),
                "ohne Welt darf sie nicht zuhoeren - sie koennte die Bereiche nicht"
                        + " zuordnen");
    }

    @Test
    @DisplayName("5 - ohne Rundgang oder Sprache wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        var rundgang = rundgang(station("zunfthaus", 10));

        assertThrows(IllegalArgumentException.class,
                () -> new Rundgangswache(null, null, Sprachprobe.spracheAusDemJar()));
        assertThrows(IllegalArgumentException.class,
                () -> new Rundgangswache(rundgang, null, null));
    }
}
