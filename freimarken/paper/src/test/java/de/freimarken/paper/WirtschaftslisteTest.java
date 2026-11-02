package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Wirtschaftswerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Das Lesen der Wirtschaftswerte - ohne Server.
 *
 * <p>Moeglich, weil {@code YamlConfiguration} reine Dateiarbeit ist. Geprueft wird damit
 * genau das, was der Betreiber tippt, und nicht eine nachgebaute Karte, die sich anders
 * verhaelt als die echte Datei.
 */
class WirtschaftslisteTest {

    private static final String VOLLSTAENDIG = """
            wirtschaft:
              parzelle:
                grundpreis: 25000
                zusammenlegung-je-feld: 10000
            parzellen:
              hoechstzahl: 4
              gruppe-hoechstzahl: 4
            hinweise:
              rang-werbung: "Mehr erlaubt ein hoeherer Rang."
            kasse:
              startguthaben: 50000
            """;

    private static Wirtschaftsliste.Ergebnis lies(String yaml) {
        return Wirtschaftsliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage diesen Schluessel nennt. */
    private static boolean klagtUeber(Wirtschaftsliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ohne Klage durch")
    void vollstaendig() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(Wirtschaftswerte.VORGABE, ergebnis.werte());
        assertFalse(ergebnis.bericht().isEmpty(), "die Rechenprobe muss dabei sein");
        assertTrue(ergebnis.bericht().stream()
                .anyMatch(zeile -> zeile.contains("2. 25000")),
                ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().stream()
                .noneMatch(zeile -> zeile.contains(Wirtschaftswerte.PFAD_STARTGUTHABEN)),
                "der Leser sagt nichts ueber das geltende Startguthaben - das waere die"
                        + " Zeile, die sich im Spiel mit dem Block darunter doppelte: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Ein Punkt trennt keine Tausender: 25.000 gilt NICHT")
    void punktIstKeinTausendertrenner() {
        // Der teuerste Tippfehler der ganzen Datei: YAML liest 25.000 als 25, und ein
        // tausendfach zu billiges Grundstueck macht Geld, das nur eine Ruecksicherung
        // des Servers zurueckholt.
        var ergebnis = lies(VOLLSTAENDIG.replace("grundpreis: 25000",
                "grundpreis: 25.000"));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().getFirst().contains("25.000"),
                "der Klagetext muss nennen, was wirklich dasteht");
        assertEquals(25_000L, ergebnis.werte().preise().grundpreis(),
                "es gilt die Vorgabe");
    }

    @Test
    @DisplayName("Anfuehrungszeichen machen aus einer Zahl Text - und das gilt nicht")
    void textIstKeineZahl() {
        // getLong liest aus \"25000\" stillschweigend eine 0. Belegt ist genau dieses
        // Bild: Nach einem Versionswechsel standen alle Ladenpreise auf 0.
        var ergebnis = lies(VOLLSTAENDIG.replace("grundpreis: 25000",
                "grundpreis: \"25000\""));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ergebnis.klagen().toString());
        assertEquals(25_000L, ergebnis.werte().preise().grundpreis());
    }

    @Test
    @DisplayName("Ein negativer Preis waere eine Gutschrift beim Kauf")
    void negativGiltNicht() {
        var ergebnis = lies(VOLLSTAENDIG.replace("grundpreis: 25000",
                "grundpreis: -5000"));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ergebnis.klagen().toString());
        assertEquals(25_000L, ergebnis.werte().preise().grundpreis());
    }

    @Test
    @DisplayName("Ueber dem gerechneten Hoechstwert laeuft die Verdopplung ueber")
    void obergrenze() {
        var ergebnis = lies(VOLLSTAENDIG.replace("grundpreis: 25000",
                "grundpreis: " + (Preisstaffel.HOECHSTER_GRUNDPREIS + 1)));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().getFirst()
                .contains(String.valueOf(Preisstaffel.HOECHSTER_GRUNDPREIS)),
                "der Bereich muss im Klagetext stehen");
    }

    @Test
    @DisplayName("Mehr als Faktor 2 gilt nur mit Bestaetigung - und die Klage nennt den Text")
    void sprungBrauchtBestaetigung() {
        var ohne = lies(VOLLSTAENDIG.replace("grundpreis: 25000",
                "grundpreis: 100000"));

        assertTrue(klagtUeber(ohne, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ohne.klagen().toString());
        assertEquals(25_000L, ohne.werte().preise().grundpreis());
        assertTrue(ohne.klagen().getFirst().contains("25000 -> 100000"),
                "die Klage muss den Text nennen, der einzutragen ist: "
                        + ohne.klagen().getFirst());
        assertTrue(ohne.klagen().getFirst()
                .contains(Wirtschaftswerte.PFAD_BESTAETIGT + ".grundpreis"),
                "und wohin er gehoert: " + ohne.klagen().getFirst());

        var echt = lies("""
                wirtschaft:
                  parzelle:
                    grundpreis: 100000
                    zusammenlegung-je-feld: 10000
                  sprung-bestaetigt:
                    grundpreis: "25000 -> 100000"
                parzellen:
                  hoechstzahl: 4
                  gruppe-hoechstzahl: 4
                hinweise:
                  rang-werbung: ""
                kasse:
                  startguthaben: 50000
                """);
        assertTrue(echt.klagen().isEmpty(), echt.klagen().toString());
        assertEquals(100_000L, echt.werte().preise().grundpreis());
        assertTrue(echt.bericht().stream().anyMatch(zeile -> zeile.contains("bestaetigt")),
                echt.bericht().toString());
    }

    @Test
    @DisplayName("Eine Bestaetigung gilt nur fuer genau den eingetragenen Sprung")
    void bestaetigungPasstNichtMehr() {
        // Sonst waere eine einmal eingetragene Zeile ein Dauerfreibrief: Der Betreiber
        // setzt spaeter 400.000 und bekommt keine Rueckfrage mehr.
        var ergebnis = lies("""
                wirtschaft:
                  parzelle:
                    grundpreis: 400000
                    zusammenlegung-je-feld: 10000
                  sprung-bestaetigt:
                    grundpreis: "25000 -> 100000"
                """);

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUNDPREIS),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().getFirst().contains("25000 -> 400000"),
                ergebnis.klagen().getFirst());
    }

    @Test
    @DisplayName("Gratis ist erlaubt - aber nicht beilaeufig")
    void gratisBrauchtBestaetigung() {
        var ohne = lies(VOLLSTAENDIG.replace("grundpreis: 25000", "grundpreis: 0"));
        assertTrue(klagtUeber(ohne, Wirtschaftswerte.PFAD_GRUNDPREIS),
                "0 ist der grosse Sprung nach unten: " + ohne.klagen());
        assertEquals(25_000L, ohne.werte().preise().grundpreis());

        var mit = lies("""
                wirtschaft:
                  parzelle:
                    grundpreis: 0
                    zusammenlegung-je-feld: 10000
                  sprung-bestaetigt:
                    grundpreis: "25000 -> 0"
                """);
        assertTrue(mit.klagen().isEmpty(), mit.klagen().toString());
        assertEquals(0L, mit.werte().preise().grundpreis());
        assertTrue(mit.bericht().stream()
                .anyMatch(zeile -> zeile.contains("gratis")), mit.bericht().toString());
    }

    @Test
    @DisplayName("Eine Obergrenze von 0 hiesse: niemand darf eine Parzelle")
    void nullParzellen() {
        var ergebnis = lies(VOLLSTAENDIG.replace("hoechstzahl: 4", "hoechstzahl: 0"));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_HOECHSTZAHL),
                ergebnis.klagen().toString());
        assertEquals(4, ergebnis.werte().hoechstzahl());
    }

    @Test
    @DisplayName("Eine Obergrenze darf wachsen, ohne dass jemand etwas bestaetigt")
    void anzahlOhneRueckfrage() {
        // Die Faktor-2-Rueckfrage gilt nur fuer Geldwerte. Eine Obergrenze macht kein
        // Geld, und der Wert ist ohnehin nur die Untergrenze der Rechteauswertung.
        var ergebnis = lies(VOLLSTAENDIG.replace("hoechstzahl: 4", "hoechstzahl: 20"));

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(20, ergebnis.werte().hoechstzahl());
    }

    @Test
    @DisplayName("Die Rangwerbung ist Text, leer erlaubt - eine Liste nicht")
    void werbung() {
        var leer = lies(VOLLSTAENDIG.replace(
                "rang-werbung: \"Mehr erlaubt ein hoeherer Rang.\"", "rang-werbung: \"\""));
        assertTrue(leer.klagen().isEmpty(), leer.klagen().toString());
        assertEquals("", leer.werte().rangWerbung());

        var liste = lies("""
                hinweise:
                  rang-werbung:
                    - "erste Zeile"
                    - "zweite Zeile"
                """);
        assertTrue(klagtUeber(liste, Wirtschaftswerte.PFAD_RANG_WERBUNG),
                liste.klagen().toString());
        assertEquals(Wirtschaftswerte.VORGABE_RANG_WERBUNG, liste.werte().rangWerbung());
    }

    @Test
    @DisplayName("Eine fehlende Zeile ist kein Fehler, aber sie wird genannt")
    void fehlendeZeilenWerdenGenannt() {
        // Am 02.11.2026 gemessen: Auf dem Probeserver fehlten 16 von 42 Schluesseln, und
        // alle wirkten trotzdem - get holt die Vorgabe stumm aus der Jar. Was der
        // Betreiber nicht sieht, kann er nicht aendern.
        var ergebnis = lies("datenbank:\n  url: \"\"\n");

        assertTrue(ergebnis.klagen().isEmpty(),
                "Vorgaben sind richtig, also kein Grund zur Klage: " + ergebnis.klagen());
        assertEquals(Wirtschaftswerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream()
                .anyMatch(zeile -> zeile.contains("6 von 6 Wirtschaftszeilen")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ohne jede Konfiguration gelten die Vorgaben")
    void garNichts() {
        var ergebnis = Wirtschaftsliste.ausKonfiguration(null);

        assertTrue(ergebnis.klagen().isEmpty());
        assertEquals(Wirtschaftswerte.VORGABE, ergebnis.werte());
        assertFalse(ergebnis.bericht().isEmpty());
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml verstoesst nicht gegen die eigenen Schranken")
    void ausgelieferteDateiIstBrauchbar() {
        // Die wichtigste Probe hier: Sie faengt den Fall, dass die Vorlage selbst gegen
        // die eigenen Grenzen verstoesst - also einen Erststart, der mit einer
        // severe-Zeile beginnt, bevor der Betreiber ein Zeichen getippt hat.
        try (InputStream strom =
                     Wirtschaftsliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            var ergebnis = Wirtschaftsliste.ausKonfiguration(datei);

            assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
            assertEquals(Wirtschaftswerte.VORGABE, ergebnis.werte(),
                    "die Datei muss genau die Vorgaben des Programms enthalten - sonst"
                            + " gilt bei einem Erststart etwas anderes als beim Rueckfall");
        } catch (java.io.IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("Eine negative Obergrenze gilt nicht - auch wenn sie als int positiv wird")
    void negativeAnzahlKipptNicht() {
        // -2147483649 wird als int zu 2147483647. Wer vor der Pruefung verkuerzt, laesst
        // aus einer getippten negativen Zahl eine unbegrenzte Parzellenzahl werden - und
        // damit ist die Bremse gegen Flaechenhorten lautlos aus.
        for (String getippt : new String[] {"-2147483649", "-4294967292", "-2147483650",
                                            "-1", "0"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("hoechstzahl: 4",
                    "hoechstzahl: " + getippt));

            assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_HOECHSTZAHL),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                    ergebnis.werte().hoechstzahl(), getippt);
        }
    }

    @Test
    @DisplayName("Dieselbe Luecke bei der Gruppengroesse")
    void negativeGruppengroesseKipptNicht() {
        var ergebnis = lies(VOLLSTAENDIG.replace("gruppe-hoechstzahl: 4",
                "gruppe-hoechstzahl: -4294967292"));

        assertTrue(klagtUeber(ergebnis, Wirtschaftswerte.PFAD_GRUPPEN_FELDER),
                ergebnis.klagen().toString());
        assertEquals(Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                ergebnis.werte().gruppenFelder());
    }

    @Test
    @DisplayName("Ein bestaetigter Sprung behauptet nicht, der Wert GELTE")
    void bestaetigungBehauptetKeineGeltung() {
        // Fuer kasse.startguthaben wuerde das beim Neuladen der eigenen Antwort
        // widersprechen: uebernimm() laesst diesen Wert ausdruecklich stehen, und der
        // Block darunter sagt "es gilt weiter 50000". Dieser Leser kennt nur die Datei.
        var ergebnis = lies("""
                wirtschaft:
                  parzelle:
                    grundpreis: 25000
                    zusammenlegung-je-feld: 10000
                  sprung-bestaetigt:
                    startguthaben: "50000 -> 120000"
                parzellen:
                  hoechstzahl: 4
                  gruppe-hoechstzahl: 4
                hinweise:
                  rang-werbung: ""
                kasse:
                  startguthaben: 120000
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(120_000L, ergebnis.werte().startguthaben());
        assertTrue(ergebnis.bericht().stream()
                .anyMatch(zeile -> zeile.contains("Bestaetigung erkannt")),
                ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().stream().noneMatch(zeile ->
                zeile.contains(Wirtschaftswerte.PFAD_STARTGUTHABEN)
                        && zeile.contains(" gilt")),
                "keine Berichtszeile darf behaupten, das Startguthaben gelte: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Eine kaputte Datei wird als kaputt gemeldet und nicht als Vorgabe gelesen")
    void kaputteDateiWirdGesehen(@TempDir Path ordner) throws IOException {
        // Der Fehler, den kein Pfad sieht: Bukkit faengt den Parse-Fehler selbst, liefert
        // eine LEERE Konfiguration mit den Jar-Vorgaben - und dieser Leser findet dann
        // nichts zu klagen. Ohne diesen Blick waere ein Tabulator in der Datei ein gruenes
        // "Uebernommen: grundpreis 200000 -> 25000".
        Path datei = ordner.resolve(Wirtschaftsliste.DATEI);
        // Ein nicht geschlossenes Anfuehrungszeichen - einer der Handgriffe, die
        // eine YAML-Datei im Vorbeigehen zerstoeren.
        Files.writeString(datei, """
                wirtschaft:
                  parzelle:
                    grundpreis: "25000
                """);

        String fehler = Wirtschaftsliste.lesefehler(ordner.toFile());
        assertNotNull(fehler, "eine unlesbare Datei muss gemeldet werden");
        assertTrue(fehler.contains(Wirtschaftsliste.DATEI), fehler);
        assertEquals(1, fehler.lines().count(),
                "die Meldung muss eine Chatzeile sein: " + fehler);

        // Und der Gegenbeweis - nachgestellt, weil er sich nicht fahren laesst:
        // YamlConfiguration.loadConfiguration faengt den Parse-Fehler und PROTOKOLLIERT
        // ihn ueber Bukkit.getLogger(), und ohne Server gibt das hier eine
        // NullPointerException. Genau dieses Fangen ist der Fund: Danach steht eine LEERE
        // Konfiguration da, an der die Vorgaben aus der Jar haengen. So sieht sie aus:
        var wieNachDemFehler = new YamlConfiguration();
        wieNachDemFehler.setDefaults(
                YamlConfiguration.loadConfiguration(new StringReader(VOLLSTAENDIG)));
        var gelesen = Wirtschaftsliste.ausKonfiguration(wieNachDemFehler);
        assertTrue(gelesen.klagen().isEmpty(),
                "der Leser findet nichts zu klagen - genau deshalb braucht es den Blick"
                        + " auf die Datei: " + gelesen.klagen());
        assertEquals(Wirtschaftswerte.VORGABE, gelesen.werte(),
                "und er liest die ausgelieferten Werte als die des Betreibers");
    }

    @Test
    @DisplayName("Eine fehlende Datei wird als fehlend gemeldet")
    void fehlendeDateiWirdGesehen(@TempDir Path ordner) {
        String fehler = Wirtschaftsliste.lesefehler(ordner.toFile());

        assertNotNull(fehler, "ohne Datei darf nichts uebernommen werden");
        assertTrue(fehler.contains(Wirtschaftsliste.DATEI), fehler);
    }

    @Test
    @DisplayName("Eine lesbare Datei meldet keinen Fehler")
    void lesbareDateiSchweigt(@TempDir Path ordner) throws IOException {
        Files.writeString(ordner.resolve(Wirtschaftsliste.DATEI), VOLLSTAENDIG);

        assertNull(Wirtschaftsliste.lesefehler(ordner.toFile()));
    }
}
