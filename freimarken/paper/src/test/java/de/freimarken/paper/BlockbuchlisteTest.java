package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blocktat;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Blockbuchs - Weisslisten statt Wahrheitswerte, und je Welt.
 *
 * <p>Moeglich ohne laufenden Server, weil {@code YamlConfiguration} reine Dateiarbeit ist
 * - dieselbe Bauart wie {@link VerdienstlisteTest}. Der Weltschluessel kommt aus einer
 * Naht, nicht aus {@code NamespacedKey}: Genau darum ist dieser Zweig pruefbar.
 */
@DisplayName("Blockbuchliste")
class BlockbuchlisteTest {

    /** Die Welten, die es laut config.yml gibt. */
    private static final Set<String> WELTEN =
            Set.of("marktstadt", "altfurt", "hochwald");

    /** Dieselbe Umwandlung, die das Plugin mit NamespacedKey macht. */
    private static final Blockbuchliste.Schluesselmacher MACHER =
            name -> "freimarken:" + name;

    private static Blockbuchliste.Ergebnis lies(String yaml) {
        return Blockbuchliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)),
                WELTEN, MACHER);
    }

    @Test
    @DisplayName("Eine Liste von Namen wird gelesen - Taten und abgewiesen getrennt")
    void listeWirdGelesen() {
        var gelesen = lies("""
                blockbuch:
                  aufbewahrung-tage: 14
                  je-aufraeumen: 250
                  taten: [setzen, abbauen, abgewiesen]
                """);

        assertEquals(List.of(), gelesen.klagen(), "diese Datei ist in Ordnung");
        assertEquals(14, gelesen.werte().fristTage());
        assertEquals(250, gelesen.werte().jeAufraeumen());
        assertEquals("[setzen, abbauen, abgewiesen]",
                gelesen.werte().vorgabe().wieEingetragen());
    }

    @Test
    @DisplayName("Je Welt eine eigene Liste - und sie wird zum SCHLUESSEL")
    void jeWeltEineListe() {
        // DER FEHLER VOM 09.11.2026 IN DIESEM LESER: Die config.yml schreibt "altfurt",
        // die Welt traegt "freimarken:altfurt". Bliebe die Karte nach dem NAMEN
        // geschluesselt, fragte die Wache fuer immer ins Leere - und zwar stumm.
        var gelesen = lies("""
                blockbuch:
                  taten: [setzen, abbauen]
                  welten:
                    marktstadt: [setzen, abbauen, benutzen, abgewiesen]
                    hochwald: []
                """);

        assertEquals(List.of(), gelesen.klagen());
        Blockbuchwerte werte = gelesen.werte();
        assertTrue(werte.jeWelt().containsKey("freimarken:marktstadt"),
                "die Karte ist nach dem SCHLUESSEL geschluesselt, nicht nach dem Namen:"
                        + " " + werte.jeWelt().keySet());
        assertTrue(werte.schreibt("freimarken:marktstadt", Blocktat.BENUTZEN, true,
                "chest"));
        assertFalse(werte.schreibt("freimarken:hochwald", Blocktat.SETZEN, true,
                        "stone"),
                "die leere Liste nimmt hochwald aus");
        assertTrue(werte.schreibt("freimarken:altfurt", Blocktat.SETZEN, true, "stone"),
                "altfurt hat keine eigene Zeile und erbt die Vorgabe");
    }

    @Test
    @DisplayName("Ein unbekannter Tatname ist eine Klage MIT dem Namen")
    void unbekannterTatnameWirdBeklagt() {
        var gelesen = lies("""
                blockbuch:
                  taten: [setzen, zerstoeren, abgewiesen]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains("zerstoeren"),
                "die Klage muss den getippten Namen nennen, sonst sucht der Betreiber"
                        + " ihn: " + gelesen.klagen().get(0));
        assertTrue(gelesen.klagen().get(0).contains("abbauen"),
                "und sie muss die erlaubten nennen: " + gelesen.klagen().get(0));
        assertTrue(gelesen.werte().schreibt("freimarken:altfurt", Blocktat.SETZEN, true,
                        "stone"),
                "die gute Zeile bleibt stehen - eine kaputte wirft nicht alles weg");
    }

    @Test
    @DisplayName("Ein true statt einer Liste ist eine Klage und nennt den Grund")
    void wahrheitswertWirdBeklagt() {
        // DIE ENTSCHEIDUNG DIESES LESERS. Wer true eintraegt, meint "schreib alles" -
        // und bekaeme von getBoolean genau das, was er nicht meint. Hier ist es eine
        // Klage, die den Grund nennt.
        var gelesen = lies("""
                blockbuch:
                  taten: true
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains("keine Liste"),
                gelesen.klagen().get(0));
        assertEquals(Blockbuchwerte.VORGABE_AUSWAHL.wieEingetragen(),
                gelesen.werte().vorgabe().wieEingetragen(),
                "verworfen heisst: es gilt die Vorgabe des Programms");
    }

    @Test
    @DisplayName("Nur abgewiesen und keine Tat ist eine Klage - die Zeile wirkt nie")
    void nurAbgewiesenWirdBeklagt() {
        var gelesen = lies("""
                blockbuch:
                  taten: [abgewiesen]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains(Blocktat.NAME_ABGEWIESEN),
                gelesen.klagen().get(0));
        assertFalse(gelesen.werte().schreibtIrgendwo(),
                "und sie schreibt wirklich nichts - darum die Klage");
    }

    @Test
    @DisplayName("Eine leere Liste ist ERLAUBT und keine Klage")
    void leereListeIstErlaubt() {
        // Der Weg, das Blockbuch ganz abzuschalten. Kein Rueckfall auf die Vorgabe - wer
        // eine leere Liste eintraegt, meint nicht die Vorgabe.
        var gelesen = lies("""
                blockbuch:
                  taten: []
                """);

        assertEquals(List.of(), gelesen.klagen());
        assertFalse(gelesen.werte().schreibtIrgendwo());
        assertTrue(gelesen.bericht().stream().anyMatch(zeile ->
                        zeile.contains("schreibt NICHTS")),
                "aber es wird laut gesagt - stumm abgeschaltet ist der Zustand, in dem"
                        + " jemand Monate spaeter nach Zeilen sucht: " + gelesen.bericht());
    }

    @Test
    @DisplayName("Eine Welt, die es nicht gibt, ist eine Klage")
    void unbekannteWeltWirdBeklagt() {
        var gelesen = lies("""
                blockbuch:
                  taten: [setzen]
                  welten:
                    nether: [abbauen]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains("nether"), gelesen.klagen().get(0));
        assertTrue(gelesen.werte().jeWelt().isEmpty(),
                "eine Auswahl fuer eine Welt, die es nicht gibt, wirkt nie - sie kommt"
                        + " also nicht in die Karte");
    }

    @Test
    @DisplayName("Eine unbrauchbare Frist ist eine Klage, keine stille Vorgabe")
    void unbrauchbareFristWirdBeklagt() {
        var gelesen = lies("""
                blockbuch:
                  aufbewahrung-tage: 0
                  taten: [setzen]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertEquals(Blockbuchwerte.VORGABE_FRIST_TAGE, gelesen.werte().fristTage());
    }

    @Test
    @DisplayName("Ein zu grosser Happen ist eine Klage - er waere wieder ein Stillstand")
    void zuGrosserHappenWirdBeklagt() {
        var gelesen = lies("""
                blockbuch:
                  je-aufraeumen: 5000000
                  taten: [setzen]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertEquals(Blockbuchwerte.VORGABE_HAPPEN, gelesen.werte().jeAufraeumen());
    }

    @Test
    @DisplayName("Eine fehlende Datei gibt die Vorgabe und keine Klage")
    void fehlenderAbschnittGibtVorgabe() {
        var gelesen = lies("wirtschaft:\n  startgeld: 100\n");

        assertEquals(List.of(), gelesen.klagen(),
                "ein fehlender Abschnitt darf kein Neuladen verhindern");
        assertEquals(Blockbuchwerte.VORGABE_FRIST_TAGE, gelesen.werte().fristTage());
        assertTrue(gelesen.werte().schreibtIrgendwo());
        assertFalse(gelesen.bericht().isEmpty(),
                "aber im Bericht steht, dass die Zeilen fehlen: " + gelesen.bericht());
    }

    @Test
    @DisplayName("Ein Schluesselmacher, der den NAMEN zurueckgibt, wird beklagt")
    void falschVerdrahteteNahtWirdBeklagt() {
        // DER FALL, DEN KEINE PROBE FING (gemessen, Einspritzung B11 am 09.11.2026): Im
        // Plugin wurde "this::weltschluessel" gegen "name -> name" getauscht, und alle
        // 2.516 Proben blieben gruen. Die Karte waere dann nach NAMEN geschluesselt, der
        // Vergleich mit World.key().asString() immer ungleich - und das Blockbuch fuer
        // jede eingetragene Welt stumm leer.
        //
        // Diese Probe sichert die Klage, die es im Spiel laut macht; 31ah faengt es
        // vorher. Beides, weil der Fehler sonst unsichtbar ist.
        var gelesen = Blockbuchliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader("""
                        blockbuch:
                          taten: [setzen]
                          welten:
                            altfurt: [abbauen]
                        """)),
                WELTEN, name -> name);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        String klage = gelesen.klagen().get(0);
        assertTrue(klage.contains("altfurt"), klage);
        assertTrue(klage.contains("KEIN FEHLER IN DEINER DATEI"),
                "die Klage trifft den Entwickler, nicht den Betreiber - der kann an"
                        + " seiner Datei nichts aendern: " + klage);
        assertTrue(gelesen.werte().jeWelt().isEmpty(),
                "die Welt wird uebersprungen - sonst schriebe das Blockbuch dort stumm"
                        + " nichts");
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe")
    void ohneKonfigurationGiltDieVorgabe() {
        var gelesen = Blockbuchliste.ausKonfiguration(null, WELTEN, MACHER);

        assertEquals(List.of(), gelesen.klagen());
        assertEquals(Blockbuchwerte.VORGABE_FRIST_TAGE, gelesen.werte().fristTage());
    }

    @Test
    @DisplayName("Die Liste der benutzbaren Bloecke wird gelesen und geprueft")
    void blocklisteWirdGeprueft() {
        var gelesen = lies("""
                blockbuch:
                  taten: [benutzen]
                  benutzbare-bloecke: [chest, barrel, gibtsnicht]
                """);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().get(0).contains("gibtsnicht"),
                "die Klage muss den getippten Namen nennen: " + gelesen.klagen().get(0));
        assertTrue(gelesen.werte().istBenutzbar("chest"));
        assertTrue(gelesen.werte().istBenutzbar("barrel"),
                "die guten Namen bleiben stehen");
        assertTrue(gelesen.werte().schreibt("freimarken:altfurt",
                        Blocktat.BENUTZEN, true, "chest"));
        assertFalse(gelesen.werte().schreibt("freimarken:altfurt",
                        Blocktat.BENUTZEN, true, "dirt"),
                "Erde steht nicht in der Liste - sonst waere jeder Rechtsklick eine"
                        + " Zeile");
    }

    @Test
    @DisplayName("Ohne Zeile gelten die Behaelter aus dem Programm")
    void ohneBlocklisteGeltenDieBehaelter() {
        var gelesen = lies("""
                blockbuch:
                  taten: [benutzen]
                """);

        assertEquals(List.of(), gelesen.klagen());
        assertEquals(Blockbuchwerte.VORGABE_BENUTZBAR.size(),
                gelesen.werte().benutzbareBloecke().size());
        assertTrue(gelesen.werte().istBenutzbar("chest"));
    }

    @Test
    @DisplayName("Eine leere Blockliste bei eingetragenem benutzen wird GESAGT")
    void leereBlocklisteWirdGesagt() {
        // Eine Einstellung, die nur halb wirkt, muss das sagen: benutzen steht da, die
        // Liste ist leer, also schreibt die Tat nichts - und an der Datei sieht man das
        // nicht. Keine Klage (es entsteht kein Schaden), aber auch kein Schweigen.
        var gelesen = lies("""
                blockbuch:
                  taten: [setzen, benutzen]
                  benutzbare-bloecke: []
                """);

        assertEquals(List.of(), gelesen.klagen());
        String alles = String.join(" | ", gelesen.bericht());
        assertTrue(alles.contains("wirkt damit NICHT"),
                "der Bericht muss den halb wirkenden Fall nennen: " + alles);
        assertTrue(gelesen.werte().schreibt("freimarken:altfurt",
                        Blocktat.SETZEN, true, "stone"),
                "setzen wirkt weiter - nur benutzen nicht");
    }

    @Test
    @DisplayName("Der Bericht nennt, was WIRKLICH gilt - und dass es einen Neustart braucht")
    void berichtNenntWasGilt() {
        // Damit der Betreiber im Protokoll liest, was gilt, und nicht nur, was er glaubt
        // eingetragen zu haben. Und dass ein Neuladen das Blockbuch nicht anfasst: Eine
        // Einstellung, die nur teils wirkt, muss das sagen.
        var gelesen = lies("""
                blockbuch:
                  taten: [setzen, abgewiesen]
                  welten:
                    marktstadt: [benutzen]
                """);

        String alles = String.join(" | ", gelesen.bericht());
        assertTrue(alles.contains("[setzen, abgewiesen]"), alles);
        assertTrue(alles.contains("freimarken:marktstadt [benutzen]"), alles);
        assertTrue(alles.contains("NEUSTART"), alles);
    }
}
