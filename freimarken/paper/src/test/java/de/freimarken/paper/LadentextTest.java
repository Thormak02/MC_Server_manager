package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Quader;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die NAHT von {@code /laden} - die Zeilen, die der Befehl nur noch hinausschickt.
 *
 * <p>Diese Datei fehlte, und das ist ein Befund aus einer Einspritzung: Die Bildtafel in
 * {@code BildGleichTest} ruft die SAETZE unmittelbar und geht nicht durch
 * {@link Ladentext}. Eine Verdrehung der Reihenfolge und ein weggelassener Ort blieben
 * darum beide gruen - das BILD stimmte, nur hatte es niemand aus der Naht geholt.
 *
 * <p>Geprueft wird hier also die WAHL und die REIHENFOLGE, nicht der Wortlaut. Den haelt
 * die Tafel.
 */
@DisplayName("Die Zeilen der Markthalle")
class LadentextTest {

    private Sprache sprache;

    @BeforeEach
    void vorher() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    private static Ladeneinheit einheit(String name, int x) {
        return new Ladeneinheit(name, new Quader(x, 71, 0, x + 4, 75, 4), x + 2, 71, 2);
    }

    /** Eine Standzeile, die in keiner anderen Zeile vorkommen kann. */
    private static final Component STAND = Component.text("MEINLADEN");

    private List<String> bild(Ladenverzeichnis verzeichnis) {
        List<String> zeilen = new java.util.ArrayList<>();
        for (Component zeile : Ladentext.listeZeilen(sprache, verzeichnis, STAND)) {
            zeilen.add(Sprachprobe.bild(zeile));
        }
        return zeilen;
    }

    @Test
    @DisplayName("Ohne Einheiten kommt die Ueberschrift UND die Leer-Zeile")
    void ohneEinheiten() {
        // Zwei Zeilen und nicht eine: Wer nur die Ueberschrift sieht, haelt es fuer einen
        // Fehler. Ab Werk ist das der Normalfall - die Markthalle ist von Hand zu bauen.
        List<String> zeilen = bild(Ladenverzeichnis.ohneEinheiten());

        assertEquals(3, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("noch keine Ladeneinheiten"),
                zeilen.get(1));
        // Die Standzeile steht AUCH unter der Leer-Zeile. Erreichbar, wenn ein Betreiber
        // eine Einheit aus der Datei nimmt, die vermietet ist - und genau dann will man
        // wissen, dass da noch ein Vertrag haengt.
        assertTrue(zeilen.get(2).contains("MEINLADEN"), zeilen.toString());
    }

    @Test
    @DisplayName("Je Einheit EINE Zeile, dazu die Ueberschrift")
    void eineZeileJeEinheit() {
        List<String> zeilen = bild(Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 0), einheit("halle-2", 10),
                einheit("halle-3", 20))));

        assertEquals(5, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(4).contains("MEINLADEN"),
                "die eigene Mietzeile steht ZULETZT: " + zeilen);
        assertFalse(zeilen.get(1).contains("noch keine"),
                "mit Einheiten darf die Leer-Zeile nicht kommen: " + zeilen);
    }

    @Test
    @DisplayName("Die REIHENFOLGE ist die der config.yml")
    void reihenfolgeDerDatei() {
        // Dort ordnet der Betreiber. Eine gedrehte oder sortierte Anzeige nimmt ihm die
        // Ordnung weg, die er sich ueberlegt hat - und sie faellt niemandem auf, weil
        // jede Zeile fuer sich richtig aussieht.
        List<String> zeilen = bild(Ladenverzeichnis.ausEinheiten(List.of(
                einheit("zwiebel", 0), einheit("apfel", 10), einheit("mohn", 20))));

        assertTrue(zeilen.get(1).contains("zwiebel"), zeilen.toString());
        assertTrue(zeilen.get(2).contains("apfel"), zeilen.toString());
        assertTrue(zeilen.get(3).contains("mohn"), zeilen.toString());
    }

    @Test
    @DisplayName("Jede Zeile nennt den ORT ihrer Truhe")
    void jedeZeileNenntDenOrt() {
        // Eine Halle mit zwanzig gleich aussehenden Staenden ist ohne Koordinate nicht zu
        // durchsuchen. Ohne diese Probe blieb ein weggelassener Ort gruen: Die Bildtafel
        // ruft den Satz unmittelbar und sieht nicht, was die Naht einsetzt.
        List<String> zeilen = bild(Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 0), einheit("halle-2", 10))));

        assertTrue(zeilen.get(1).contains("2/71/2"),
                "die Truhe von halle-1 steht bei 2/71/2: " + zeilen.get(1));
        assertTrue(zeilen.get(2).contains("12/71/2"),
                "die von halle-2 bei 12/71/2: " + zeilen.get(2));
    }

    @Test
    @DisplayName("Der Name geht UNVERAENDERT durch - er steht auch im Befehl")
    void nameUnveraendert() {
        // Er kommt aus der config.yml und nicht aus der Sprachdatei. Wer ihn eines Tages
        // uebersetzen laesst, baut einen Befehl, der seinen eigenen Namen nicht findet.
        List<String> zeilen = bild(Ladenverzeichnis.ausEinheiten(
                List.of(einheit("halle-1", 0))));

        assertTrue(zeilen.get(1).contains("halle-1"), zeilen.get(1));
    }

    @Test
    @DisplayName("Jede Zeile ist GENAU EINE Zeile")
    void jedeZeileIstEine() {
        // Eine Umbruchmarke in einem dieser Saetze verschoebe das Bild stumm -
        // sprache.eineZeile wirft darum mit dem Pfad. Diese Probe haelt fest, dass die
        // Naht sie benutzt und nicht bau().
        for (String zeile : bild(Ladenverzeichnis.ausEinheiten(
                List.of(einheit("halle-1", 0))))) {
            assertEquals(1, zeile.split(System.lineSeparator()).length, zeile);
        }
    }

    @Test
    @DisplayName("Ohne Standzeile wirft die Naht, statt eine Zeile zu verschweigen")
    void ohneStandzeileWirftSie() {
        // Ein null hier waere die eine Zeile, die der Spieler nicht sieht - und weil sie
        // die letzte ist, faellt ihr Fehlen nicht auf. Wer keinen Laden hat, bekommt von
        // Miettext.standZeile den Hinweis; null ist also nie richtig.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Ladentext.listeZeilen(sprache, Ladenverzeichnis.ohneEinheiten(),
                        null));
    }

    @Test
    @DisplayName("Die Liste ist unveraenderlich - der Sendeort kann nichts anhaengen")
    void listeIstFest() {
        // Sonst haengte eines Tages ein Zweig eine Zeile an, die keine Tafel haelt.
        List<Component> zeilen = Ladentext.listeZeilen(sprache,
                Ladenverzeichnis.ohneEinheiten(), STAND);

        assertEquals(3, zeilen.size());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> zeilen.add(Component.empty()));
    }
}
