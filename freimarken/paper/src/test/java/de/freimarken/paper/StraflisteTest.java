package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafwerte;
import java.io.StringReader;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Abschnitts {@code moderation.strafen:}.
 *
 * <p>Moeglich ohne laufenden Server, weil {@code YamlConfiguration} reine Dateiarbeit
 * ist - dieselbe Bauart wie {@link BlockbuchlisteTest}.
 *
 * <p><b>Die tragenden Proben sind {@link #hoechstdauerJeArt()} und
 * {@link #befehlslisteFehltUndWirdGemeldet()}.</b> Die erste haelt die Grenze aus Plan
 * 12c.2 zwischen Moderator und Verwalter; die zweite den Fall, in dem ein Betreiber eine
 * Zeile loescht und das Gegenteil von dem bekommt, was er wollte.
 */
@DisplayName("Der Leser der Strafen")
class StraflisteTest {

    private static Strafliste.Ergebnis lies(String yaml) {
        return Strafliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    @DisplayName("1 - die ausgelieferte Form wird klaglos gelesen")
    void ausgelieferteFormIstBrauchbar() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    hoechste-stunden:
                      stumm: 168
                      bann: 720
                    bannliste-oeffentlich: true
                    ansagen: true
                    zeilen-je-seite: 10
                    stumm-sperrt-befehle: [msg, tell, w, teammsg, tm, me, say]
                """);

        assertEquals(List.of(), gelesen.klagen(), "diese Datei ist in Ordnung");
        assertEquals(List.of(), gelesen.bericht(), gelesen.bericht().toString());
        assertEquals(Strafwerte.VORGABE, gelesen.werte(),
                "die ausgelieferte Form IST die Vorgabe - sonst gilt bei einem"
                        + " Erststart etwas anderes als beim Rueckfall");
    }

    @Test
    @DisplayName("2 - ein FEHLENDER Abschnitt gibt die Vorgabe des Programms")
    void fehlenderAbschnitt() {
        var gelesen = lies("wirtschaft:\n  grundpreis: 25000\n");

        assertEquals(List.of(), gelesen.klagen());
        assertEquals(Strafwerte.VORGABE.hoechsteStundenFuer(Strafart.BANN),
                gelesen.werte().hoechsteStundenFuer(Strafart.BANN));
        // JEDER Rueckweg geht durch fertig() - also auch dieser, und dann nennt der
        // Bericht die fehlenden Schluessel. Genau das hat am 25.11.2026 bei den
        // Teamrollen gefehlt: Der Server, dem die Auskunft am meisten hilft - der
        // ohne Eintrag - bekam sie nicht.
        assertFalse(gelesen.bericht().isEmpty(),
                "ein Server ohne Abschnitt soll lesen, was gilt");
    }

    @Test
    @DisplayName("3 - DIE GRENZE: die Hoechstdauer wird je ART gelesen")
    void hoechstdauerJeArt() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    hoechste-stunden:
                      stumm: 24
                      bann: 48
                """);

        // Die Zahl, die den Moderator vom Verwalter trennt (Plan 12c.2). Je Art eine
        // eigene, weil sieben Tage stumm eine harte Strafe sind und sieben Tage
        // gebannt eine gewoehnliche.
        assertEquals(24L, gelesen.werte().hoechsteStundenFuer(Strafart.STUMM));
        assertEquals(48L, gelesen.werte().hoechsteStundenFuer(Strafart.BANN));
        assertEquals(List.of(), gelesen.klagen());
    }

    @Test
    @DisplayName("4 - eine fehlende Hoechstdauer faellt EINZELN auf die Vorgabe")
    void fehlendeDauerEinzeln() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    hoechste-stunden:
                      bann: 48
                """);

        // Einzeln und nicht den ganzen Abschnitt verwerfen: Ein Server, auf dem nach
        // einem Tippfehler niemand mehr bannen kann, ist schlimmer als einer mit
        // einer Vorgabezahl.
        assertEquals(Strafwerte.VORGABE.hoechsteStundenFuer(Strafart.STUMM),
                gelesen.werte().hoechsteStundenFuer(Strafart.STUMM));
        assertEquals(48L, gelesen.werte().hoechsteStundenFuer(Strafart.BANN));
    }

    @Test
    @DisplayName("5 - eine unbrauchbare Zahl wird GEKLAGT und faellt zurueck")
    void unbrauchbareZahlWirdGeklagt() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    hoechste-stunden:
                      stumm: -5
                      bann: hundert
                    zeilen-je-seite: 0
                """);

        assertEquals(3, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains("hoechste-stunden.stumm"),
                gelesen.klagen().get(0));
        assertEquals(Strafwerte.VORGABE.hoechsteStundenFuer(Strafart.STUMM),
                gelesen.werte().hoechsteStundenFuer(Strafart.STUMM));
        assertEquals(Strafwerte.VORGABE.zeilenJeSeite(),
                gelesen.werte().zeilenJeSeite());
    }

    @Test
    @DisplayName("6 - eine Hoechstdauer von 0 ist erlaubt und wird GEMELDET")
    void nullStundenWirdGemeldet() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    hoechste-stunden:
                      stumm: 168
                      bann: 0
                """);

        assertEquals(0L, gelesen.werte().hoechsteStundenFuer(Strafart.BANN));
        assertEquals(List.of(), gelesen.klagen(), "eine 0 ist erlaubt");
        assertTrue(gelesen.bericht().stream().anyMatch(satz -> satz.contains("ist 0")),
                "aber sie heisst: ein Moderator kann gar nicht bannen - und das soll"
                        + " er lesen: " + gelesen.bericht());
    }

    @Test
    @DisplayName("7 - ja statt true wird GEKLAGT und nicht stumm als false gelesen")
    void jaStattTrueWirdGeklagt() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    bannliste-oeffentlich: ja
                    ansagen: nein
                """);

        // Bukkit liest jedes Wort ausser true stumm als false. Aus einem
        // "ansagen: ja" wuerde sonst eine abgeschaltete Ansage - und niemand vermisst
        // eine Zeile, die er nie gesehen hat.
        assertEquals(2, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.werte().bannlisteOeffentlich(),
                "es bleibt bei der Vorgabe");
        assertTrue(gelesen.werte().ansagen());
    }

    @Test
    @DisplayName("8 - false wird gelesen und seine Folge gemeldet")
    void falseWirdGelesenUndGemeldet() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    bannliste-oeffentlich: false
                    ansagen: false
                """);

        assertEquals(List.of(), gelesen.klagen());
        assertFalse(gelesen.werte().bannlisteOeffentlich());
        assertFalse(gelesen.werte().ansagen());
        assertTrue(gelesen.bericht().stream()
                        .anyMatch(satz -> satz.contains("12c.4")),
                "Plan 12c.4 macht die Liste ausdruecklich oeffentlich: "
                        + gelesen.bericht());
    }

    @Test
    @DisplayName("9 - die Befehlsliste wird gelesen und vereinheitlicht")
    void befehlslisteWirdGelesen() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    stumm-sperrt-befehle:
                      - /MSG
                      - tell
                """);

        assertEquals(List.of("msg", "tell"), gelesen.werte().stummSperrtBefehle());
        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.werte().sperrtBefehl("/minecraft:msg Hans hallo"),
                "der Namensraum ist der Weg um die Sperre herum");
    }

    @Test
    @DisplayName("10 - eine AUSDRUECKLICH leere Liste gilt und wird gemeldet")
    void leereListeGiltUndWirdGemeldet() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    stumm-sperrt-befehle: []
                """);

        // Ein klarer Wunsch - sie gilt. Was sie bedeutet, sagt der Bericht: Eine
        // Stummschaltung ist dann ueber /msg zu umgehen.
        assertTrue(gelesen.werte().stummSperrtBefehle().isEmpty());
        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.bericht().stream().anyMatch(satz -> satz.contains("/msg")),
                gelesen.bericht().toString());
    }

    @Test
    @DisplayName("11 - eine FEHLENDE Befehlsliste wird gemeldet, nicht stumm ersetzt")
    void befehlslisteFehltUndWirdGemeldet() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    zeilen-je-seite: 10
                """);

        // DER UNTERSCHIED ZU DEN ZAHLEN: Wer die Zeile aus der Datei nimmt, meint
        // meistens "nichts sperren" und bekommt das Gegenteil - sieben gesperrte
        // Befehle. Das muss er lesen, und die Zeile nennt den Weg: [].
        assertEquals(Strafwerte.VORGABE_STUMM_SPERRT,
                gelesen.werte().stummSperrtBefehle());
        assertTrue(gelesen.bericht().stream()
                        .anyMatch(satz -> satz.contains("[]")),
                "der Bericht nennt den Weg zur leeren Liste: " + gelesen.bericht());
    }

    @Test
    @DisplayName("12 - ein leerer Eintrag in der Liste wird uebersprungen und geklagt")
    void leererEintragWirdGeklagt() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    stumm-sperrt-befehle:
                      - msg
                      - ""
                      - tell
                """);

        // Uebersprungen und nicht geworfen: Eine Stummschaltung, die zwei von drei
        // Befehlen sperrt, ist besser als keine.
        assertEquals(List.of("msg", "tell"), gelesen.werte().stummSperrtBefehle());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
    }

    @Test
    @DisplayName("13 - eine Liste, die keine ist, wird geklagt")
    void keineListeWirdGeklagt() {
        var gelesen = lies("""
                moderation:
                  strafen:
                    stumm-sperrt-befehle: msg
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertEquals(Strafwerte.VORGABE_STUMM_SPERRT,
                gelesen.werte().stummSperrtBefehle());
    }

    @Test
    @DisplayName("14 - die SCHLUESSEL-Liste nennt jede Hoechstdauer, gerechnet")
    void schluesselNennenJedeDauer() {
        // GERECHNET und nicht aufgezaehlt: Eine vierte fortwirkende Art wird damit
        // von selbst vermisst. Eine Liste von Hand waere die Stelle, an der sie
        // fehlt - und eine fehlende Hoechstdauer ist ein Riegel, der nicht greift.
        for (Strafart art : Strafart.values()) {
            String pfad = Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel();
            assertEquals(art.giltFort(), Strafliste.SCHLUESSEL.contains(pfad),
                    art + ": " + Strafliste.SCHLUESSEL);
        }
        assertTrue(Strafliste.SCHLUESSEL.contains(
                Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE));
    }

    @Test
    @DisplayName("15 - die Rechenprobe nennt jede Hoechstdauer und die Befehlszahl")
    void rechenprobeNenntDieZahlen() {
        String satz = Strafliste.rechenprobe(Strafwerte.VORGABE);

        // Die zwei Angaben, an denen die Wirkung haengt: die Grenze zwischen
        // Moderator und Verwalter und die Luecke, durch die eine Stummschaltung
        // umgangen wird. Eine Zeile ohne sie waere eine Zusage ohne Zahl.
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            assertTrue(satz.contains(art.kuerzel()), art + " fehlt: " + satz);
            assertTrue(satz.contains(String.valueOf(
                            Strafwerte.VORGABE.hoechsteStundenFuer(art))),
                    art + ": die Zahl fehlt: " + satz);
        }
        assertTrue(satz.contains(String.valueOf(
                Strafwerte.VORGABE_STUMM_SPERRT.size())), satz);
        assertTrue(satz.contains("oeffentlich"), satz);
    }

    @Test
    @DisplayName("16 - die Rechenprobe vertraegt null")
    void rechenprobeVertraegtNull() {
        // Sie laeuft beim Start und beim Neuladen. Ein Wurf dort waere ein Server,
        // der an seinem eigenen Bericht scheitert.
        assertFalse(Strafliste.rechenprobe(null).isBlank());
    }
}
