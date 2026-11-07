package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Arten von Lebenszeichen und ihre Namen in der Datei des Betreibers. */
@DisplayName("Die Lebenszeichen")
class LebenszeichenTest {

    @Test
    @DisplayName("Jeder Schluessel fuehrt zu seiner Art zurueck")
    void hinUndZurueck() {
        for (Lebenszeichen art : Lebenszeichen.values()) {
            assertEquals(art, Lebenszeichen.ausSchluessel(art.schluessel()),
                    "der Schluessel " + art.schluessel() + " muss zurueckfuehren");
        }
    }

    @Test
    @DisplayName("Die sieben Namen in der Datei sind festgenagelt")
    void dieSiebenNamen() {
        // Diese Namen sind die Schnittstelle zum Betreiber und nicht unsere: Sie stehen
        // in SEINER config.yml. Dass sie im Programm GESPEICHERT sind und nicht aus
        // name().toLowerCase() entstehen, laesst sich hier nicht pruefen - heute sind
        // beide gleich. Was die Probe haelt, ist die Folge: Wer eine Konstante umbenennt,
        // wird hier rot und muss entscheiden, ob die Datei mitwandert.
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : Lebenszeichen.values()) {
            namen.add(art.schluessel());
        }

        assertEquals(List.of("bewegen", "abbauen", "setzen", "oeffnen", "benutzen",
                "kaempfen", "sprechen"), namen);
    }

    @Test
    @DisplayName("Kein Schluessel traegt einen Umlaut oder ein grosses Zeichen")
    void nurKleinUndOhneUmlaut() {
        // Dieselbe Regel wie in der ganzen config.yml: Ein Umlaut in einer Datei, die
        // unter wechselnden Kodierungen gelesen wird, ist eine Zeile, die stumm
        // verschwindet. "oeffnen" statt des naheliegenden Wortes ist darum Absicht.
        for (Lebenszeichen art : Lebenszeichen.values()) {
            String name = art.schluessel();
            assertTrue(name.matches("[a-z-]+"),
                    "nur kleine Buchstaben und Striche: " + name);
        }
    }

    @Test
    @DisplayName("Ein unbekannter Name gibt null und wirft nicht")
    void unbekannterName() {
        // Der Leser der config.yml muss eine kaputte Zeile BEKLAGEN und die uebrigen
        // stehen lassen koennen. Ein Wurf hier hielte den ganzen Start an.
        assertNull(Lebenszeichen.ausSchluessel("graben"));
        assertNull(Lebenszeichen.ausSchluessel(""));
        assertNull(Lebenszeichen.ausSchluessel(null));
    }

    @Test
    @DisplayName("Gross und klein und Leerzeichen rundherum werden verziehen")
    void nachsichtigGelesen() {
        // Ein Betreiber tippt "Abbauen" oder laesst ein Leerzeichen stehen; beides ist
        // kein Fehler, den er in einem Protokoll suchen soll.
        assertEquals(Lebenszeichen.ABBAUEN, Lebenszeichen.ausSchluessel("ABBAUEN"));
        assertEquals(Lebenszeichen.ABBAUEN, Lebenszeichen.ausSchluessel("  abbauen  "));
        assertEquals(Lebenszeichen.OEFFNEN, Lebenszeichen.ausSchluessel("Oeffnen"));
    }

    @Test
    @DisplayName("alle() gibt jede Art in der Reihenfolge der Aufzaehlung")
    void alleInReihenfolge() {
        Set<Lebenszeichen> alle = Lebenszeichen.alle();

        assertEquals(Lebenszeichen.values().length, alle.size());
        assertEquals(new LinkedHashSet<>(List.of(Lebenszeichen.values())), alle);
        // Die Reihenfolge und nicht nur der Inhalt: Daran haengt die Klage, die dem
        // Betreiber alle Arten aufzaehlt.
        assertEquals(Lebenszeichen.BEWEGEN, alle.iterator().next());
    }

    @Test
    @DisplayName("alle() gibt eine eigene Menge - ein Aufrufer kann sie nicht leeren")
    void alleIstEineKopie() {
        Set<Lebenszeichen> erste = Lebenszeichen.alle();
        erste.clear();

        assertEquals(Lebenszeichen.values().length, Lebenszeichen.alle().size(),
                "eine geteilte Menge liesse einen Aufrufer die Vorgabe des Programms"
                        + " ausraeumen");
        assertNotNull(Lebenszeichen.BEWEGEN.schluessel());
    }
}
