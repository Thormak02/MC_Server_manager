package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Das Gruen auf der Platzkante")
class PlatzgruenTest {

    @Test
    @DisplayName("Ab Werk waechst eine Eiche und steht ein Brunnen")
    void abWerk() {
        Platzgruen gruen = Platzgruen.abWerk();

        assertTrue(gruen.stehtEtwas(), "Stamm und Laub sind da");
        assertTrue(gruen.brunnenMoeglich(), "Rand und Wasser sind da");
        assertEquals(Material.OAK_LOG, gruen.stamm().get(0));
        // ZWEI LAUBARTEN, und die haeufigere steht zweimal: Die Gewichtung kommt aus der
        // Wiederholung, wie beim Pflaster.
        assertTrue(gruen.laub().size() >= 3, "eine Mischung: " + gruen.laub());
        assertTrue(gruen.laub().stream().distinct().count() >= 2,
                "aus mehr als einem Block: " + gruen.laub());
        assertEquals(Material.WATER, gruen.wasser());
        assertTrue(gruen.baumhoehe() >= Platzgruen.HOEHE_WENIGSTENS
                        && gruen.baumhoehe() <= Platzgruen.HOEHE_HOECHSTENS,
                "die Hoehe liegt in den Schranken: " + gruen.baumhoehe());
    }

    @Test
    @DisplayName("Ohne Abschnitt bleibt die Kante Pflaster - ohne Klage")
    void ohneAbschnitt() {
        List<String> klagen = new ArrayList<>();

        Platzgruen leer = Platzgruen.ausKonfiguration(new YamlConfiguration(), klagen);

        assertFalse(leer.stehtEtwas(), "es waechst nichts");
        assertFalse(leer.brunnenMoeglich(), "und es steht kein Brunnen");
        // KEINE KLAGE: Wer einen steinernen Platz will, laesst den Abschnitt weg. Eine
        // Klage waere eine Ruege fuer eine erlaubte Entscheidung.
        assertEquals(List.of(), klagen, "keine Klage ueber einen fehlenden Abschnitt");
        // UND OHNE WURZEL AUCH NICHT.
        assertFalse(Platzgruen.ausKonfiguration(null, klagen).stehtEtwas());
        assertEquals(List.of(), klagen);
    }

    @Test
    @DisplayName("Ein unbekannter Block wird geklagt und fehlt in der Mischung")
    void unbekannterBlock() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Platzgruen.PFAD + ".stamm", List.of("oak_log", "gibtsnicht"));
        datei.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves", "air"));

        Platzgruen gruen = Platzgruen.ausKonfiguration(datei, klagen);

        assertEquals(2, klagen.size(), klagen.toString());
        String alle = String.join(" | ", klagen);
        assertTrue(alle.contains("gibtsnicht"), alle);
        assertTrue(alle.contains("air"), alle);
        // UND WAS GUT WAR, BLEIBT: Eine kaputte Zeile wirft nicht die ganze Mischung weg.
        assertEquals(List.of(Material.OAK_LOG), gruen.stamm());
        assertEquals(List.of(Material.OAK_LEAVES), gruen.laub());
        assertTrue(gruen.stehtEtwas(), "es waechst trotzdem");
    }

    @Test
    @DisplayName("Ein Stamm ohne Laub ist ein Pfahl - dann waechst nichts")
    void stammOhneLaub() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Platzgruen.PFAD + ".stamm", List.of("oak_log"));

        Platzgruen gruen = Platzgruen.ausKonfiguration(datei, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("laub"), klagen.get(0));
        assertFalse(gruen.stehtEtwas(), "kein Pfahl ohne Krone");
        assertEquals(List.of(), gruen.stamm(), "und der Stamm wird auch weggelassen");

        // UND UMGEKEHRT: Laub ohne Stamm haengt in der Luft.
        klagen.clear();
        YamlConfiguration andersrum = new YamlConfiguration();
        andersrum.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves"));
        Platzgruen schwebend = Platzgruen.ausKonfiguration(andersrum, klagen);
        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("stamm"), klagen.get(0));
        assertFalse(schwebend.stehtEtwas());
        assertEquals(List.of(), schwebend.laub());
    }

    @Test
    @DisplayName("Ein Brunnen ohne Wasser wird geklagt - an seiner Stelle waechst ein Baum")
    void brunnenOhneWasser() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Platzgruen.PFAD + ".stamm", List.of("oak_log"));
        datei.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves"));
        datei.set(Platzgruen.PFAD + ".brunnenrand", List.of("cobblestone"));

        Platzgruen gruen = Platzgruen.ausKonfiguration(datei, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("brunnenwasser"), klagen.get(0));
        assertFalse(gruen.brunnenMoeglich(), "ohne Wasser kein Brunnen");
        // UND DER RAND WIRD AUCH WEGGELASSEN: Acht Bloecke Fassung um nichts sind ein
        // Ring im Pflaster, kein Brunnen.
        assertEquals(List.of(), gruen.brunnenrand());
        // ABER BAEUME WACHSEN WEITER - der Standort bleibt besetzt.
        assertTrue(gruen.stehtEtwas());
    }

    @Test
    @DisplayName("Eine Baumhoehe jenseits der Schranken wird geklagt")
    void baumhoeheJenseits() {
        for (int gelesen : new int[] {Platzgruen.HOEHE_WENIGSTENS - 1,
                Platzgruen.HOEHE_HOECHSTENS + 1, 0, 200}) {
            List<String> klagen = new ArrayList<>();
            YamlConfiguration datei = new YamlConfiguration();
            datei.set(Platzgruen.PFAD + ".stamm", List.of("oak_log"));
            datei.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves"));
            datei.set(Platzgruen.PFAD + ".baumhoehe", gelesen);

            Platzgruen gruen = Platzgruen.ausKonfiguration(datei, klagen);

            assertEquals(1, klagen.size(), gelesen + ": " + klagen);
            assertTrue(klagen.get(0).contains(String.valueOf(gelesen)), klagen.get(0));
            assertEquals(Platzgruen.abWerk().baumhoehe(), gruen.baumhoehe(),
                    "es bleibt bei der ausgelieferten Hoehe");
        }

        // KEIN GANZZAHLWERT wird auch geklagt - "fuenf" ist keine Hoehe.
        List<String> klagen = new ArrayList<>();
        YamlConfiguration wort = new YamlConfiguration();
        wort.set(Platzgruen.PFAD + ".stamm", List.of("oak_log"));
        wort.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves"));
        wort.set(Platzgruen.PFAD + ".baumhoehe", "fuenf");
        Platzgruen gruen = Platzgruen.ausKonfiguration(wort, klagen);
        assertEquals(1, klagen.size(), klagen.toString());
        assertEquals(Platzgruen.abWerk().baumhoehe(), gruen.baumhoehe());

        // UND EIN WERT IN DEN SCHRANKEN WIRD GENOMMEN, ohne Klage.
        klagen.clear();
        YamlConfiguration gut = new YamlConfiguration();
        gut.set(Platzgruen.PFAD + ".stamm", List.of("oak_log"));
        gut.set(Platzgruen.PFAD + ".laub", List.of("oak_leaves"));
        gut.set(Platzgruen.PFAD + ".baumhoehe", Platzgruen.HOEHE_HOECHSTENS);
        assertEquals(Platzgruen.HOEHE_HOECHSTENS,
                Platzgruen.ausKonfiguration(gut, klagen).baumhoehe());
        assertEquals(List.of(), klagen, klagen.toString());
    }

    @Test
    @DisplayName("Eine unmoegliche Hoehe kommt nicht am Record vorbei")
    void derRecordWehrtSich() {
        // DIE SCHRANKE STEHT AUCH IM RECORD: Der Leser faengt sie ab, aber er ist nicht
        // der einzige Weg herein - eine Probe oder ein spaeterer Aufrufer baut das Ding
        // von Hand.
        assertThrows(IllegalArgumentException.class, () -> new Platzgruen(
                List.of(Material.OAK_LOG), List.of(Material.OAK_LEAVES), List.of(),
                null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Platzgruen(
                List.of(Material.OAK_LOG), List.of(Material.OAK_LEAVES), List.of(),
                null, Platzgruen.HOEHE_HOECHSTENS + 1));
    }

    @Test
    @DisplayName("Ein einzelnes Wort gilt als Mischung aus einem Block")
    void einzelnesWort() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Platzgruen.PFAD + ".stamm", "birch_log");
        datei.set(Platzgruen.PFAD + ".laub", "birch_leaves");

        Platzgruen gruen = Platzgruen.ausKonfiguration(datei, klagen);

        assertEquals(List.of(), klagen, klagen.toString());
        assertEquals(List.of(Material.BIRCH_LOG), gruen.stamm());
        assertEquals(List.of(Material.BIRCH_LEAVES), gruen.laub());
    }
}
