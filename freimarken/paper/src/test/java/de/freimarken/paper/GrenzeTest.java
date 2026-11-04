package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Huterin - drei Tore, die bei JEDER Aenderung rot werden.
 *
 * <p>Sie bewacht die eine Zusage, die kein Typ traegt: Ein Spielersatz entsteht nicht mehr
 * im Quelltext. Ein Verbot waere mit einer Konstante zu umgehen, darum sind es Tore und
 * keine Verbote: Die Menge der Dateien, die Farbe nennen, und die Menge der Dateien, die
 * senden, sind FESTGENAGELT und werden in jedem Bauschritt um dessen Dateien kuerzer. Weil
 * {@code assertEquals} in beide Richtungen prueft, ist jede dieser Proben rot, wenn ein
 * Schritt seine Datei vergisst (Menge zu gross) UND wenn irgendwo neue Farbe auftaucht
 * (Menge zu klein). Jede Aenderung an diesen Listen ist damit absichtlich - das ist der
 * ganze Zweck.
 *
 * <p>Die Begruendung "wer senden will, braucht eine Farbe" ist gemessen FALSCH:
 * {@code Component.text("Das geht so nicht.")} ohne Farbe laesst sich senden und erscheint
 * weiss, und dass die farblose Form in diesem Quelltext uebersetzt, zeigt
 * {@code ParzellenBefehle:286} mit {@code Component.empty()}. Darum gibt es das zweite Tor
 * auf den SENDEWEG und nicht nur das auf die Farbe.
 */
class GrenzeTest {

    /**
     * Die Dateien, die nach Bauschritt 1 noch Farbe nennen duerfen.
     *
     * <p>Das sind alle elf Sender - in Schritt 1 ist kein Spielersatz umgezogen, also kann
     * keine Farbe verschwunden sein - und dazu {@code Sprachstand.java}: Dort entsteht die
     * Antwort auf {@code /freimarken neuladen}, und das ist BETREIBERTEXT. Der bleibt nach
     * der Grenze dieses Umbaus im Programm, denn eine Diagnose darf nicht von der Datei
     * abhaengen, deren Zustand sie meldet. {@code Sprachstand.java} wird aus dieser Menge
     * also nie gestrichen.
     *
     * <p>{@code FreimarkenPlugin.java} bleibt ebenfalls darin, und das ist ein Loch, das
     * offen bleibt - nur nicht unbewacht. Seine Betreiberzeilen muessen dort bleiben, also
     * faengt diese Probe einen dort neu geschriebenen Spielersatz nicht. Dafuer gibt es
     * {@link #freimarkenPluginSendetGenauNMal}: ein Tor und kein Beweis.
     */
    private static final Set<String> FARBE_ERLAUBT = new TreeSet<>(List.of(
            "AnfragenBefehle.java", "Ankunftswache.java", "Aufwaermzeit.java",
            "FreimarkenPlugin.java", "ParzellenBefehle.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            "SpielzeitBefehle.java", "Sprachstand.java", "WarpBefehle.java"));

    /**
     * Die Dateien, die nach Bauschritt 1 noch senden duerfen.
     *
     * <p>Dieselben elf, dazu {@code Sprache.java} - das ist der EINE Ausgang nach
     * Adventure und soll senden. {@code Sprachstand.java} steht hier NICHT: Es baut die
     * Antwortzeilen und gibt sie zurueck, gesendet werden sie vom Aufrufer. Genau so ist
     * die Antwort ohne Server pruefbar.
     */
    private static final Set<String> SENDEN_ERLAUBT = new TreeSet<>(List.of(
            "AnfragenBefehle.java", "Ankunftswache.java", "Aufwaermzeit.java",
            "FreimarkenPlugin.java", "ParzellenBefehle.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            "SpielzeitBefehle.java", "Sprache.java", "WarpBefehle.java"));

    /**
     * Wie oft {@code FreimarkenPlugin} sendet - nachgezaehlt, nicht geschaetzt.
     *
     * <p>39 vor dem 04.11.2026, 40 danach: Dazu kam die eine Zeile, die den Sprachteil der
     * Antwort auf {@code /freimarken neuladen} hinausschickt. Wer eine Sendestelle
     * hinzufuegt, muss diese Zahl heben und begruendet damit seine Entscheidung.
     */
    private static final int SENDESTELLEN_IM_PLUGIN = 40;

    /** Ein echter Sendeaufruf - nicht die Erwaehnung in einem Kommentar. */
    private static final Pattern SENDEWEG = Pattern.compile(
            "[.]send(Message|ActionBar)\\(|::send(Message|ActionBar)");

    private static final Pattern FARBE = Pattern.compile("NamedTextColor|TextColor");

    @Test
    @DisplayName("32 - die Quellen wurden ueberhaupt gefunden")
    void quellenWurdenGefunden() {
        // Ohne diese Probe laufen die drei Tore leer durch und sind gruen, ohne etwas
        // gesehen zu haben.
        assertNotNull(Quellen.ordner(), "der Quellordner wurde nicht gefunden - die drei"
                + " Tore darueber waeren damit gruen, ohne etwas geprueft zu haben");
        List<Path> dateien = Quellen.alle();
        assertFalse(dateien.isEmpty());
        assertTrue(dateien.stream().anyMatch(pfad
                        -> pfad.getFileName().toString().equals("ParzellenBefehle.java")),
                "die groesste Sendeklasse des Projekts fehlt in der Liste");
    }

    @Test
    @DisplayName("30 - jeder Schluessel wird irgendwo benutzt")
    void jederSchluesselWirdGesendet() {
        // Gesucht wird die QUALIFIZIERTE Nennung, also BundSatz.BEFEHL_MARK und nicht
        // bloss BEFEHL_MARK: Ein nackter Konstantenname traefe auch gewoehnlichen Text.
        Set<String> ohneNennung = new TreeSet<>();
        for (Satz satz : Sprachschluessel.alle()) {
            String bereich = ((Enum<?>) satz).getDeclaringClass().getSimpleName();
            String eigenerBereich = bereich + ".java";
            String marke = bereich + "." + ((Enum<?>) satz).name();
            boolean gefunden = false;
            for (Path datei : Quellen.alle()) {
                String name = datei.getFileName().toString();
                // Die eigene Aufzaehlung zaehlt nicht - dort steht der Schluessel nur
                // deshalb, weil es ihn gibt. Sprachschluessel und Sprachstand zaehlen
                // ebenfalls nicht: Dort bescheinigten sich die mechanischen Familien
                // selbst.
                if (name.equals(eigenerBereich) || name.equals("Sprachschluessel.java")
                        || name.equals("Sprachstand.java")) {
                    continue;
                }
                if (Quellen.text(datei).contains(marke)) {
                    gefunden = true;
                    break;
                }
            }
            if (!gefunden) {
                ohneNennung.add(satz.pfad());
            }
        }
        // assertEquals auf die MENGE: Erlaubt ist genau die eine Familie, deren Pfad
        // mechanisch aus einem Aufzaehlungsnamen entsteht und die darum nirgends
        // namentlich getippt wird. Was sie wirklich deckt, ist SendewegTest - dort geht
        // jeder Buchungsgrund durch die echte Zeile. Wird ein weiterer Satz
        // selbstbescheinigend, ist diese Probe rot.
        assertEquals(new TreeSet<>(Sprachschluessel.familienDeckung()), ohneNennung);
    }

    @Test
    @DisplayName("31 - Farbe steht nur noch in DIESEN Dateien")
    void farbeNurNochInDiesenDateien() {
        assertEquals(FARBE_ERLAUBT, dateienMit(FARBE));
    }

    @Test
    @DisplayName("31b - gesendet wird nur noch in DIESEN Dateien")
    void sendenNurNochInDiesenDateien() {
        assertEquals(SENDEN_ERLAUBT, dateienMit(SENDEWEG));
    }

    @Test
    @DisplayName("31c - FreimarkenPlugin sendet genau so oft wie festgenagelt")
    void freimarkenPluginSendetGenauNMal() {
        Path plugin = null;
        for (Path datei : Quellen.alle()) {
            if (datei.getFileName().toString().equals("FreimarkenPlugin.java")) {
                plugin = datei;
            }
        }
        assertNotNull(plugin);
        var treffer = SENDEWEG.matcher(Quellen.text(plugin));
        int gezaehlt = 0;
        while (treffer.find()) {
            gezaehlt++;
        }
        assertEquals(SENDESTELLEN_IM_PLUGIN, gezaehlt, "FreimarkenPlugin ist der eine Ort,"
                + " an dem ein Spielersatz entstehen koennte, ohne dass eine andere Probe"
                + " rot wird - seine Betreiberzeilen muessen dort bleiben. Diese Zahl"
                + " macht jede neue Sendestelle zu einer sichtbaren Entscheidung.");
    }

    private static Set<String> dateienMit(Pattern muster) {
        Set<String> dateien = new TreeSet<>();
        for (Path datei : Quellen.alle()) {
            if (muster.matcher(Quellen.text(datei)).find()) {
                dateien.add(datei.getFileName().toString());
            }
        }
        // Eine leere Menge ist hier immer ein Fehler der Suche und nie ein Befund: Dann
        // waere das Tor darueber gruen, ohne etwas gesehen zu haben.
        assertFalse(dateien.isEmpty(), "die Suche hat keine einzige Datei gefunden");
        return dateien;
    }
}
