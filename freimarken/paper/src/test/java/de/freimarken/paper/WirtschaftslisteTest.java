package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            zeit:
              zone: "Europe/Berlin"
              takt-sekunden: 60
              untaetig-minuten: 10
            """;

    private static Wirtschaftsliste.Ergebnis lies(String yaml) {
        return Wirtschaftsliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage diesen Schluessel nennt. */
    private static boolean klagtUeber(Wirtschaftsliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    /**
     * Keine Zeile dieses Lesers darf behaupten, dass ein nur beim Start gelesener Wert
     * GILT - die eine Pruefung dafuer, fuer alle drei Schluessel und beide Listen.
     *
     * <p>Geprueft wird die WORTFAMILIE und nicht ein abgeschriebener Satz. Die Vorgaengerin
     * dieser Pruefung suchte nach " gilt" und liess damit genau den Satz durch, der dastand:
     * "Sie gelten aus den Vorgaben des Plugins" - sie schrieb den Wortlaut halb ab und
     * verfehlte ihn. "galt" ist dagegen ausdruecklich erlaubt: "Es bleibt bei dem Wert, der
     * bisher galt" stimmt auf beiden Wegen und ist genau die Form, die dieser Leser
     * benutzen darf.
     *
     * <p>Die Liste der Schluessel kommt aus {@link Wirtschaftsstand#NUR_BEIM_START} und
     * nicht aus dieser Probe: Wer dort einen vierten eintraegt, ist hier sofort mitgeprueft.
     */
    private static void keineGeltungsbehauptung(Wirtschaftsliste.Ergebnis ergebnis) {
        List<String> alle = new ArrayList<>(ergebnis.bericht());
        alle.addAll(ergebnis.klagen());
        for (String zeile : alle) {
            String klein = zeile.toLowerCase(Locale.ROOT);
            for (String pfad : Wirtschaftsstand.NUR_BEIM_START) {
                if (!zeile.contains(pfad)) {
                    continue;
                }
                assertFalse(klein.contains("gilt") || klein.contains("gelt"),
                        "diese Zeile nennt " + pfad + " und behauptet eine Geltung, die"
                                + " dieser Leser nicht kennen kann - er kennt nur die"
                                + " Datei: " + zeile);
            }
        }
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
                .anyMatch(zeile -> zeile.contains("9 von 9 Wirtschaftszeilen")),
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
            // Und jeder Schluessel muss WIRKLICH darin stehen. Ohne diese Zeile beweist
            // der Vergleich darueber zu wenig: Fuer eine Zeile, die in der Vorlage fehlt,
            // holt der Leser dieselbe Vorgabe aus dem Programm, der Vergleich bleibt
            // gruen - und der Betreiber sieht den Wert nie und kann ihn nicht aendern
            // (Plan 12a.6). Genau so sind auf dem Probeserver 16 von 42 Schluesseln
            // unsichtbar gewirkt.
            assertTrue(ergebnis.bericht().stream()
                    .noneMatch(zeile -> zeile.contains("nicht in deiner config.yml")),
                    "die ausgelieferte Datei muss jede gelesene Zeile enthalten: "
                            + ergebnis.bericht());
            // Und die Vorlage selbst darf keinen Satz ausloesen, der eine Geltung
            // behauptet, die dieser Leser nicht kennt.
            keineGeltungsbehauptung(ergebnis);
        } catch (java.io.IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("Der Kommentar zum Stillstand nennt keine Dauer aus der Datenschicht")
    void keineAbgeleiteteDauerImKommentar() {
        // Der Fehlerfall ohne diese Probe: Im Absatz zu takt-sekunden stand, ein
        // Schreibversuch in eine tote Datenbank halte den Hauptfaden "rund 5 Sekunden" an.
        // Diese 5 folgt aus zwei Groessen in de.freimarken.daten.Datenbank - der Aufbaufrist
        // und der Frist von isValid -, beide dort privat und beide aenderbar. Wer eine davon
        // anfasst, macht die Zahl in der Datei des Betreibers still falsch: Kein Lauf wird
        // rot, und die Datei behauptet etwas anderes als das Programm tut. Der Absatz nennt
        // darum nur noch die Wirkung und verweist auf die Fristen.
        //
        // Geprueft wird die ZEILE und nicht die Konstante. Eine Probe, die 3000 + 2000 gegen
        // eine 5 im Kommentar haelt, schriebe dieselbe Ableitung ein zweites Mal hin - und
        // sie waere obendrein falsch: Welche Frist wirklich ablaeuft, haengt daran, ob die
        // Verbindung beim AUFBAU oder bei der ANTWORT haengt. Messbar ist das am Server und
        // nicht hier.
        List<String> zumStillstand = ausgelieferteZeilen().stream()
                .filter(zeile -> zeile.contains("haelt den Hauptfaden"))
                .toList();

        // Genau eine, damit die Probe nicht gruen wird, weil der Satz verschwunden ist: Wer
        // ihn umformuliert, soll hier vorbeikommen und die Regel lesen.
        assertEquals(1, zumStillstand.size(),
                "genau eine Zeile beschreibt den Stillstand: " + zumStillstand);
        assertFalse(zumStillstand.getFirst().matches(".*\\d.*"),
                "diese Zeile nennt eine Dauer, die aus der Datenschicht folgt und die"
                        + " keine Probe nachhaelt: " + zumStillstand.getFirst());
    }

    @Test
    @DisplayName("Was ein Absturz kostet, steht in der Datei mit seiner Bedingung")
    void derAbsturzverlustNenntSeineBedingung() {
        // Der Fehlerfall ohne diese Probe: Der Absatz zu takt-sekunden sagt dem Betreiber,
        // bei 60 koste ein Absturz bis zu 1 Minute je Spieler und bei 600 bis zu 10 - das
        // ist die eine Zahl, mit der er Schreiblast gegen Verlust tauscht. Nach einem
        // Fehlschlag der Datenbank setzt der Takt das Schreiben aber mehrere Schlaege lang
        // aus (Spielzeitbuch.AUSSETZER_NACH_FEHLSCHLAG), und dann liegt ein Mehrfaches
        // dieser Zusage je Spieler nur im Arbeitsspeicher. Wer 600 setzt, weil er mit zehn
        // Minuten rechnet, verliert in diesem Fenster ein Vielfaches davon.
        //
        // Geprueft wird der ABSATZ und nicht die Zeile: Der Satz laeuft ueber mehrere
        // Zeilen, und eine Bedingung, die drei Absaetze weiter unten steht, liest niemand,
        // der die Zahl gerade abwaegt. Die Zahl der ausgesetzten Schlaege steht bewusst
        // NICHT in der Datei - sie folgt aus der Klemme und waere hier beim naechsten
        // Aendern still falsch; genau darum muss die Bedingung dastehen.
        List<String> absatz = absatzMit("verliert ein Absturz");

        assertTrue(absatz.stream().anyMatch(zeile -> zeile.contains("Fehlschlag")),
                "derselbe Absatz muss den Fehlschlag als Bedingung nennen: " + absatz);
    }

    /**
     * Der Absatz der ausgelieferten {@code config.yml}, in dem dieses Stueck steht.
     *
     * <p>Ein Absatz ist ein Block zusammenhaengender Erklaerungszeilen; eine Kommentarzeile
     * ohne Text ({@code #}) trennt zwei Absaetze, und ein Schluessel beendet den letzten.
     * Genau EIN Treffer wird verlangt: Sonst waere die Probe auch gruen, wenn der Satz
     * geloescht wurde - wer ihn umformuliert, soll hier vorbeikommen und die Regel lesen.
     */
    private static List<String> absatzMit(String stueck) {
        List<String> zeilen = ausgelieferteZeilen();
        int treffer = -1;
        for (int stelle = 0; stelle < zeilen.size(); stelle++) {
            if (zeilen.get(stelle).contains(stueck)) {
                assertEquals(-1, treffer,
                        "genau eine Zeile sagt \"" + stueck + "\", sonst ist nicht gesagt,"
                                + " welcher Absatz gemeint ist");
                treffer = stelle;
            }
        }
        assertTrue(treffer >= 0, "keine Zeile der Datei sagt \"" + stueck + "\"");
        int von = treffer;
        while (von > 0 && istErklaerung(zeilen.get(von - 1))) {
            von--;
        }
        int bis = treffer;
        while (bis + 1 < zeilen.size() && istErklaerung(zeilen.get(bis + 1))) {
            bis++;
        }
        return zeilen.subList(von, bis + 1);
    }

    /** Eine Kommentarzeile MIT Text - ein nacktes {@code #} trennt zwei Absaetze. */
    private static boolean istErklaerung(String zeile) {
        String ohneEinzug = zeile.trim();
        return ohneEinzug.startsWith("#") && !ohneEinzug.equals("#");
    }

    /** Die ausgelieferte {@code config.yml} als Zeilen - samt Kommentaren. */
    private static List<String> ausgelieferteZeilen() {
        try (InputStream strom = Wirtschaftsliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            return new String(strom.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException fehler) {
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
        // Geprueft an EINER Stelle, fuer alle drei Schluessel und die ganze Wortfamilie.
        keineGeltungsbehauptung(ergebnis);
    }

    @Test
    @DisplayName("Ein Tippfehler in der Zone gilt nicht und faellt nicht auf die Systemzone")
    void zoneMussEineZoneSein() {
        var ergebnis = lies(VOLLSTAENDIG.replace("zone: \"Europe/Berlin\"",
                "zone: \"Europe/Berln\""));

        assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_ZONE), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().getFirst().contains("Europe/Berln"),
                "der Klagetext muss nennen, was wirklich dasteht: "
                        + ergebnis.klagen().getFirst());
        assertEquals(Zeitwerte.VORGABE_ZONE, ergebnis.werte().zeit().zone());
    }

    @Test
    @DisplayName("Ein fester Versatz wie UTC+2 wird abgewiesen - er kennt keine Sommerzeit")
    void festerVersatzGiltNicht() {
        // Die gefaehrlichere Eingabe von beiden: ZoneId.of("UTC+2") nimmt sie an. Ein
        // Versatz kennt keine Sommerzeit, die Tagesgrenze laege ab Ende Maerz um 01:00.
        var ergebnis = lies(VOLLSTAENDIG.replace("zone: \"Europe/Berlin\"",
                "zone: \"UTC+2\""));

        assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_ZONE), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().getFirst().contains("Sommerzeit"),
                "die Klage muss den Grund nennen: " + ergebnis.klagen().getFirst());
        assertEquals(Zeitwerte.VORGABE_ZONE, ergebnis.werte().zeit().zone());
    }

    @Test
    @DisplayName("UTC ist ein gueltiger Zonenname und erlaubt")
    void utcIstErlaubt() {
        // Gueltig und trotzdem meist falsch - dagegen hilft keine Pruefung, sondern nur
        // die Tageswechselzeile im Startprotokoll. Klagen darf hier also nichts.
        var ergebnis = lies(VOLLSTAENDIG.replace("zone: \"Europe/Berlin\"",
                "zone: \"UTC\""));

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(ZoneId.of("UTC"), ergebnis.werte().zeit().zone());
    }

    @Test
    @DisplayName("Eine Liste ist keine Zone")
    void zoneIstKeineListe() {
        var ergebnis = lies("""
                zeit:
                  zone:
                    - "Europe/Berlin"
                    - "UTC"
                """);

        assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_ZONE), ergebnis.klagen().toString());
        assertEquals(Zeitwerte.VORGABE_ZONE, ergebnis.werte().zeit().zone());
    }

    @Test
    @DisplayName("Der Takt liegt zwischen 10 und 600 Sekunden")
    void taktHatGrenzen() {
        for (String getippt : new String[] {"9", "0", "-60", "601", "4294967296"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("takt-sekunden: 60",
                    "takt-sekunden: " + getippt));

            assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_TAKT),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(Zeitwerte.VORGABE_TAKT_SEKUNDEN,
                    ergebnis.werte().zeit().taktSekunden(), getippt);
        }
        for (String getippt : new String[] {"10", "600", "120"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("takt-sekunden: 60",
                    "takt-sekunden: " + getippt));

            assertTrue(ergebnis.klagen().isEmpty(),
                    getippt + " ist erlaubt: " + ergebnis.klagen());
            assertEquals(Long.parseLong(getippt), ergebnis.werte().zeit().taktSekunden());
        }
    }

    @Test
    @DisplayName("Untaetig-minuten 0 ist der Ausschalter und wird genannt")
    void untaetigNullIstDerAusschalter() {
        var ergebnis = lies(VOLLSTAENDIG.replace("untaetig-minuten: 10",
                "untaetig-minuten: 0"));

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().zeit().untaetigkeitZaehltMit(),
                "0 heisst: Abwesenheit zaehlt mit");
        assertTrue(ergebnis.bericht().stream()
                .anyMatch(zeile -> zeile.startsWith(Zeitwerte.PFAD_UNTAETIG)),
                "ein ausgeschalteter Waechter gehoert ins Protokoll: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Mehr als ein Tag Untaetigkeit gilt nicht - dafuer gibt es die 0")
    void untaetigUeberEinemTagGiltNicht() {
        var ergebnis = lies(VOLLSTAENDIG.replace("untaetig-minuten: 10",
                "untaetig-minuten: 1441"));

        assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_UNTAETIG),
                ergebnis.klagen().toString());
        assertEquals(Zeitwerte.VORGABE_UNTAETIG_MINUTEN,
                ergebnis.werte().zeit().untaetigMinuten());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt zeit faellt auf die Vorgaben zurueck und wird genannt")
    void fehlenderZeitabschnitt() {
        var ergebnis = lies(VOLLSTAENDIG.substring(0,
                VOLLSTAENDIG.indexOf("zeit:")));

        assertTrue(ergebnis.klagen().isEmpty(),
                "Vorgaben sind richtig, also kein Grund zur Klage: " + ergebnis.klagen());
        assertEquals(Zeitwerte.VORGABE, ergebnis.werte().zeit());
        assertTrue(ergebnis.bericht().stream()
                .anyMatch(zeile -> zeile.contains("3 von 9 Wirtschaftszeilen")
                        && zeile.contains(Zeitwerte.PFAD_ZONE)),
                "ohne diese Zeile sieht der Betreiber die Tagesgrenze nie: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Keine Zeile behauptet, dass ein nur beim Start gelesener Wert GILT")
    void nurBeimStartBehauptetKeineGeltung() {
        // Dieselbe Falle bei allen drei: Sie werden gelesen und gemeldet, aber nicht
        // uebernommen. Beim Neuladen stand sonst "Sie gelten aus den Vorgaben des Plugins"
        // (also Europe/Berlin) zwei Zeilen ueber "zeit.zone: es gilt weiter
        // America/New_York" - zwei verschiedene Tagesgrenzen in EINER Antwort.
        var vorhanden = lies(VOLLSTAENDIG.replace("zone: \"Europe/Berlin\"", "zone: \"UTC\"")
                .replace("takt-sekunden: 60", "takt-sekunden: 120")
                .replace("startguthaben: 50000", "startguthaben: 70000"));
        var unbrauchbar = lies(VOLLSTAENDIG.replace("zone: \"Europe/Berlin\"",
                        "zone: \"Europe/Berln\"")
                .replace("takt-sekunden: 60", "takt-sekunden: 601"));
        var fehlend = lies("datenbank:\n  url: \"\"\n");
        var zeitFehlt = lies(VOLLSTAENDIG.substring(0, VOLLSTAENDIG.indexOf("zeit:")));

        for (var ergebnis : List.of(vorhanden, unbrauchbar, fehlend, zeitFehlt)) {
            keineGeltungsbehauptung(ergebnis);
        }

        // Und der Gegenbeweis, dass hier nicht Schweigen geprueft wird: Die fehlende Zeile
        // MUSS genannt werden, sonst sieht der Betreiber die Tagesgrenze nie.
        for (String pfad : Wirtschaftsstand.NUR_BEIM_START) {
            assertTrue(fehlend.bericht().stream().anyMatch(zeile -> zeile.contains(pfad)),
                    pfad + " fehlt in der Datei und muss genannt werden: "
                            + fehlend.bericht());
        }
    }

    @Test
    @DisplayName("Eine fehlende neuladbare Zeile nennt weiter, was GILT")
    void fehlendeNeuladbareZeileNenntWasGilt() {
        // Die andere Haelfte derselben Entscheidung: Fuer die sechs neuladbaren Schluessel
        // wird der Rueckfall wirklich uebernommen, also stimmt "es gilt 4" auf beiden
        // Wegen. Eine Zeit lang stand fuer alle neun nur "ausgeliefert ist 4" - damit
        // erfuhr der Betreiber in der ganzen Antwort nicht mehr, welche Hoechstzahl gilt,
        // denn uebernimm() nennt nur GEAENDERTE Zeilen und der Block darunter fuehrt sie
        // nicht.
        var ergebnis = lies(VOLLSTAENDIG.replace("  hoechstzahl: 4\n", ""));

        assertEquals(Wirtschaftswerte.VORGABE_HOECHSTZAHL, ergebnis.werte().hoechstzahl());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                zeile.contains(Wirtschaftswerte.PFAD_HOECHSTZAHL)
                        && zeile.contains("es gilt 4")),
                "die Zeile muss nennen, welche Hoechstzahl gilt: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Eine Untaetigkeitsgrenze kuerzer als der Takt wird verworfen, nicht geworfen")
    void grenzeMussDenTaktDecken() {
        // Der Fehlerfall, der 90 Prozent der gemessenen Zeit verschluckte: takt 600 s und
        // grenze 1 min liegen beide in ihrem eigenen Bereich. Die Grenze ist aber eine
        // STUFE und kein Schnitt - reisst sie, faellt der GANZE Takt weg, auch die 540
        // aktiv gespielten Sekunden darin, und zwar ohne Protokollzeile.
        var ergebnis = lies(VOLLSTAENDIG.replace("takt-sekunden: 60", "takt-sekunden: 600")
                .replace("untaetig-minuten: 10", "untaetig-minuten: 1"));

        assertTrue(klagtUeber(ergebnis, Zeitwerte.PFAD_UNTAETIG),
                ergebnis.klagen().toString());
        String klage = ergebnis.klagen().getFirst();
        assertTrue(klage.contains("600"), "die Klage muss den Takt nennen: " + klage);
        assertTrue(klage.contains("mindestens 10"),
                "und die kleinste Zahl, die passt: " + klage);
        assertEquals(Zeitwerte.VORGABE_UNTAETIG_MINUTEN,
                ergebnis.werte().zeit().untaetigMinuten(),
                "der Rueckfall deckt auch den laengsten erlaubten Takt");
        assertEquals(600L, ergebnis.werte().zeit().taktSekunden(),
                "der Takt selbst ist brauchbar und bleibt stehen");

        // Die 0 passt zu JEDEM Takt - sonst gaebe es fuer einen langen Takt keinen Weg
        // mehr, die Regel abzuschalten.
        var aus = lies(VOLLSTAENDIG.replace("takt-sekunden: 60", "takt-sekunden: 600")
                .replace("untaetig-minuten: 10", "untaetig-minuten: 0"));
        assertTrue(aus.klagen().isEmpty(), aus.klagen().toString());

        // Aufgerundet auf ganze Minuten, weil die Einstellung nur die kennt: Zu 90 s
        // genuegen 2 Minuten, 1 nicht.
        var knapp = lies(VOLLSTAENDIG.replace("takt-sekunden: 60", "takt-sekunden: 90")
                .replace("untaetig-minuten: 10", "untaetig-minuten: 2"));
        assertTrue(knapp.klagen().isEmpty(), knapp.klagen().toString());
        var zuKnapp = lies(VOLLSTAENDIG.replace("takt-sekunden: 60", "takt-sekunden: 90")
                .replace("untaetig-minuten: 10", "untaetig-minuten: 1"));
        assertTrue(klagtUeber(zuKnapp, Zeitwerte.PFAD_UNTAETIG),
                "1 Minute deckt 90 Sekunden nicht: " + zuKnapp.klagen());
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
