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
 * <p><b>Diese Datei beginnt bei Teil D</b> [12.11.2026] und prueft seit Teil E3 DREI
 * Zeilen der Konfiguration: {@code freistaedte.buergerrechte} und die zwei
 * Auflagenschalter. Die zwei kamen dazu, weil sie als benachbarte {@code boolean} in
 * den Satz {@code Freistadtwerte} gehen und ab Werk GLEICH sind - ein Tausch waere ohne
 * eine Probe mit zwei verschiedenen Werten unsichtbar. Die vier Zahlen daneben -
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
@DisplayName("Der Abschnitt freistaedte: aus der config.yml")
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

    // --- Die zwei Auflagenschalter [Teil E3] ----------------------------------

    @Test
    @DisplayName("Die zwei Auflagenschalter werden nicht vertauscht")
    void dieZweiSchalterKommenNichtVertauschtAn() {
        // DER UNTERSCHIED IST DAS GEPRUEFTE: Die beiden Schalter stehen als
        // benachbarte booleans im Satz Freistadtwerte, und ab Werk sind beide true.
        // Eine Probe mit zwei gleichen Werten waere gruen, egal in welcher Reihenfolge
        // dieser Leser sie hereingibt - also setzt diese Probe sie VERSCHIEDEN.
        var ergebnis = lies("""
                freistaedte:
                  auflage:
                    teleporter: false
                    ladenrecht: true
                """);

        assertFalse(ergebnis.werte().auflageTeleporter(),
                "teleporter stand auf false");
        assertTrue(ergebnis.werte().auflageLadenrecht(),
                "ladenrecht stand auf true - vertauscht waere beides umgekehrt");
        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Und in der anderen Richtung - sonst prueft die Probe nur eine Lesart")
    void undInDerAnderenRichtung() {
        var ergebnis = lies("""
                freistaedte:
                  auflage:
                    teleporter: true
                    ladenrecht: false
                """);

        assertTrue(ergebnis.werte().auflageTeleporter());
        assertFalse(ergebnis.werte().auflageLadenrecht());
    }

    @Test
    @DisplayName("Eine abgeschaltete Auflage steht im Startbericht - mit ihrem Pfad")
    void abgeschalteteAuflageStehtImBericht() {
        // Eine abgeschaltete Regel sieht im Spiel aus wie eine kaputte. Wer den
        // Startbericht liest, soll sie finden - und zwar mit dem PFAD, damit er weiss,
        // welche Zeile er anfassen muss.
        var ergebnis = lies("""
                freistaedte:
                  auflage:
                    ladenrecht: false
                """);

        assertTrue(berichtetUeber(ergebnis, Freistadtwerte.PFAD_AUFLAGE_LADENRECHT),
                ergebnis.bericht().toString());
        assertTrue(berichtetUeber(ergebnis, "wirkt hier NICHT"),
                ergebnis.bericht().toString());
        // UND GENAU EINE solche Zeile: Der unveraenderte Schalter bekommt keine.
        // Sonst stuenden im Startbericht dreissig Zeilen, die nichts sagen.
        //
        // Gezaehlt wird die Zeile mit "wirkt hier NICHT" und nicht das Wort
        // teleporter: Jenes steht ohnehin in der Liste der FEHLENDEN Zeilen, denn
        // diese Probe gibt nur den einen Schluessel herein. Ein assertFalse darauf
        // waere rot aus einem Grund, der mit der Auflage nichts zu tun hat.
        assertEquals(1, ergebnis.bericht().stream()
                        .filter(zeile -> zeile.contains("wirkt hier NICHT")).count(),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Wert, der kein Ja oder Nein ist, wird BEKLAGT - die Auflage bleibt")
    void vielleichtWirdBeklagt() {
        // Bukkits getBoolean gibt fuer "vielleicht" ein false zurueck, und das waere
        // eine ausgeschaltete Auflage, die der Betreiber eingeschaltet glaubt.
        var ergebnis = lies("""
                freistaedte:
                  auflage:
                    ladenrecht: vielleicht
                """);

        assertTrue(klagtUeber(ergebnis, Freistadtwerte.PFAD_AUFLAGE_LADENRECHT),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().auflageLadenrecht(),
                "und die Vorgabe gilt weiter - NICHT false");
    }


    // --- Das Stadtrecht im Startbericht [Fund 27.11.2026] --------------------

    @Test
    @DisplayName("Die Rechenprobe nennt den Preis des Stadtrechts - mit der Zahl")
    void rechenprobeNenntDasStadtrecht() {
        // DER FUND DES STARTLAUFS: Im ganzen Startprotokoll stand das Wort
        // "Stadtrecht" nicht einmal. Eine Einstellung, die entscheidet, ob in
        // einer Stadt gehandelt werden darf, war unsichtbar - und kein Probenlauf
        // sieht so etwas, weil keine Probe den Bericht als GANZES liest.
        String satz = Freistadtliste.rechenprobe(Freistadtwerte.VORGABE);

        assertTrue(satz.contains("Stadtrecht"), satz);
        // DIE ZAHL selbst, und nicht nur das Wort: Darum geht es bei einer
        // Rechenprobe - sie ist die einzige Schranke gegen den Wert, der im
        // erlaubten Bereich liegt und trotzdem falsch ist.
        assertTrue(satz.contains(String.valueOf(Freistadtwerte.VORGABE_STADTRECHT_PREIS)),
                "die Zahl muss dastehen, sonst sieht niemand den fehlenden"
                        + " Nullenblock: " + satz);
        assertTrue(satz.contains("Stadtkasse"),
                "und WOHER das Geld kommt - nicht aus der Tasche des Besitzers: "
                        + satz);
    }

    @Test
    @DisplayName("Bei Preis 0 sagt die Rechenprobe AUS - nicht einfach nichts")
    void rechenprobeSagtAusBeiNull() {
        // DIE GEGENRICHTUNG, und ohne sie waere die Probe darueber auch gruen,
        // wenn die Zeile bei 0 eine 0 nennte: Der Betreiber liese dann
        // "Stadtrecht einmalig 0 Mark" und hielte es fuer gratis statt fuer
        // abgeschaltet. Die zwei sind nicht dasselbe - bei 0 gibt es gar keine
        // Buchung, siehe VORGABE_STADTRECHT_PREIS.
        String satz = Freistadtliste.rechenprobe(
                Freistadtwerte.VORGABE.mitStadtrechtPreis(0L));

        assertTrue(satz.contains("Stadtrecht: AUS"), satz);
        assertFalse(satz.contains("Stadtrecht einmalig"),
                "bei 0 darf dort keine Zahl stehen: " + satz);
    }
}
