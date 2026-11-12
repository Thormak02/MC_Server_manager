package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tat;
import java.io.StringReader;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der VORGABE-BUERGERRECHTE aus dem Abschnitt {@code freistaedte:}.
 *
 * <p><b>Diese Datei beginnt bei Teil D</b> [12.11.2026], und sie prueft genau EINE Zeile
 * der Konfiguration: {@code freistaedte.buergerrechte}. Die vier Zahlen daneben -
 * Gruendungspreis, Hoechstzahl, Hoechstzellen, Verkaufsgebuehr - sind von keiner Probe
 * dieses Projekts gedeckt; sie gehen durch {@code Wirtschaftsliste.ganzeZahl}, das seine
 * eigene hat. Dass dieser Leser hier eine bekommt, hat einen Grund: Er ist der erste des
 * Abschnitts, der eine LISTE liest, und die einzige Zusage, die er gibt - ein
 * unbekanntes Wort wird BEKLAGT und nicht verschluckt -, waere ohne Probe nur ein
 * Kommentar.
 *
 * <p>Ohne Server, wie jeder Listenleser dieses Projekts: {@code YamlConfiguration} aus
 * einer Zeichenkette, und der Leser bekommt sie wie beim Start.
 */
@DisplayName("Buergerrechte aus der config.yml")
class FreistadtlisteTest {

    private static Freistadtliste.Ergebnis lies(String yaml) {
        return Freistadtliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static boolean klagtUeber(Freistadtliste.Ergebnis ergebnis, String stueck) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.contains(stueck));
    }

    private static boolean berichtetUeber(Freistadtliste.Ergebnis ergebnis,
                                          String stueck) {
        return ergebnis.bericht().stream().anyMatch(zeile -> zeile.contains(stueck));
    }

    @Test
    @DisplayName("Eine leere Liste heisst: Buerger haben nur ihr eigenes Grundstueck")
    void leerIstDieVorgabe() {
        var ergebnis = lies("""
                freistaedte:
                  buergerrechte: []
                """);

        assertEquals(Parzelle.KEINE_RECHTE, ergebnis.werte().buergerrechte());
        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        // KEINE Berichtszeile, wenn die Einstellung die Vorgabe ist: Der Startbericht
        // nennt nur, was ABWEICHT - sonst stehen dort dreissig Zeilen, die nichts sagen.
        assertFalse(berichtetUeber(ergebnis, "buergerrechte"),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Fehlt die Zeile ganz, gilt dasselbe wie bei einer leeren Liste")
    void fehlendIstWieLeer() {
        // Eine fehlende Zeile und eine leere Liste bedeuten hier WIRKLICH dasselbe, und
        // das ist nicht immer so: Bei einer Zahl heisst fehlend "nimm die Vorgabe" und 0
        // oft etwas Eigenes. Hier ist die Vorgabe die leere Menge.
        var ergebnis = lies("""
                freistaedte:
                  gruendungspreis: 400000
                """);

        assertEquals(Parzelle.KEINE_RECHTE, ergebnis.werte().buergerrechte());
        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Jedes der drei Rechte kommt an - und zwar genau es")
    void alleDreiKommenAn() {
        // ALLE DREI einzeln, und nicht nur eines: Ein Leser, der jeden Namen auf dasselbe
        // Recht abbildet, waere mit einer Probe ueber ein einziges Wort gruen.
        for (Tat recht : Tat.values()) {
            var ergebnis = lies("""
                    freistaedte:
                      buergerrechte:
                        - %s
                    """.formatted(recht.schluessel()));

            assertEquals(Set.of(recht), ergebnis.werte().buergerrechte(),
                    "gelesen aus " + recht.schluessel());
            assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
            assertTrue(berichtetUeber(ergebnis, recht.schluessel()),
                    "was abweicht, steht im Startbericht: " + ergebnis.bericht());
        }
    }

    @Test
    @DisplayName("Zwei Rechte zusammen, und das dritte bleibt draussen")
    void zweiVonDrei() {
        var ergebnis = lies("""
                freistaedte:
                  buergerrechte:
                    - benutzen
                    - behaelter
                """);

        assertEquals(Set.of(Tat.BENUTZEN, Tat.BEHAELTER),
                ergebnis.werte().buergerrechte(),
                "BAUEN darf nicht mitkommen - ein Leser, der bei einem Eintrag alles"
                        + " gibt, waere mit einer Probe ueber eine Vollliste gruen");
    }

    @Test
    @DisplayName("Ein unbekanntes Wort wird BEKLAGT - und die anderen gelten weiter")
    void unbekanntesWortKlagt() {
        // DIE EINE ZUSAGE DIESES LESERS. Ein stilles Uebergehen waere der schlimmere
        // Fall: Der Betreiber hat ein Recht gegeben, und ohne Klage suchte er die
        // Wirkung an der falschen Stelle - im Befehl, in der Wache, in der Datenbank.
        var ergebnis = lies("""
                freistaedte:
                  buergerrechte:
                    - benutzen
                    - fliegen
                """);

        assertTrue(klagtUeber(ergebnis, "fliegen"),
                "das WORT steht in der Klage: " + ergebnis.klagen());
        assertEquals(Set.of(Tat.BENUTZEN), ergebnis.werte().buergerrechte(),
                "und das gute Wort gilt weiter - eine kaputte Zeile reisst nie die"
                        + " ganze Datei mit");
    }

    @Test
    @DisplayName("Gross geschrieben und mit Leerzeichen gilt auch")
    void randUndGrossschreibung() {
        // Ein Betreiber soll nicht an einem Leerzeichen scheitern. GROSS geschrieben ist
        // derselbe Wunsch - und BEHAELTER ist genau die Form, die im Quelltext steht,
        // also die naheliegende Verwechslung.
        var ergebnis = lies("""
                freistaedte:
                  buergerrechte:
                    - "  BEHAELTER "
                """);

        assertEquals(Set.of(Tat.BEHAELTER), ergebnis.werte().buergerrechte());
        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Die Vorgabe des PLUGINS ist leer - gemessen an der echten Datei")
    void dieAusgelieferteDateiGibtNichtsHer() {
        // Gegen die AUSGELIEFERTE Vorgabe und nicht gegen eine hier getippte: Wer in der
        // config.yml ein Recht eintraegt, bekommt damit eine rote Probe - und das ist
        // richtig, denn jede neu gegruendete Stadt des Servers haengt daran.
        assertEquals(Set.of(), Freistadtwerte.VORGABE_BUERGERRECHTE,
                "eine frische Stadt gibt nichts her, bis ihr Besitzer sich"
                        + " entscheidet");
        assertEquals(Set.of(), Freistadtwerte.VORGABE.buergerrechte());
    }
}
