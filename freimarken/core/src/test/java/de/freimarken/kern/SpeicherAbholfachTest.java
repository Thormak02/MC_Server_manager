package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt das Abholfach - und drei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link AbholVertrag} und
 * nicht hier: Sonst erbt die naechste Fassung sie nicht.
 */
@DisplayName("Abholfach im Arbeitsspeicher")
class SpeicherAbholfachTest {

    @Nested
    @DisplayName("Haelt den Abholvertrag ein")
    class Vertrag extends AbholVertrag {
        @Override
        protected Abholablage ablage() {
            return new SpeicherAbholfach();
        }
    }

    @Test
    @DisplayName("Zwei Faecher teilen nichts")
    void zweiFaecherTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt.
        UUID anna = UUID.randomUUID();
        SpeicherAbholfach erstes = new SpeicherAbholfach();
        erstes.lege(anna, "Laden halle-1", AbholVertrag.WANN,
                List.of(AbholVertrag.gut("64x Stein")));

        assertTrue(new SpeicherAbholfach().fuerSpieler(anna).isEmpty());
    }

    @Test
    @DisplayName("Eine Nummer wird NIE wiederverwendet")
    void nummernWerdenNichtWiederverwendet() {
        // Der Fall, gegen den das steht: Anna holt ihr letztes Stueck, Bert legt eines
        // ein, und Annas altes Fenster holt es mit derselben Nummer. Ohne diese Zusage
        // bekaeme sie Berts Ware - und zwar ohne dass irgendwo eine Pruefung fehlt.
        SpeicherAbholfach fach = new SpeicherAbholfach();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        fach.lege(anna, "Laden halle-1", AbholVertrag.WANN,
                List.of(AbholVertrag.gut("Annas Stein")));
        long annas = fach.fuerSpieler(anna).get(0).id();
        fach.nimm(anna, List.of(annas));

        fach.lege(bert, "Laden halle-2", AbholVertrag.WANN,
                List.of(AbholVertrag.gut("Berts Holz")));

        assertEquals(annas + 1L, fach.fuerSpieler(bert).get(0).id());

        // UND DANN NOCH EINMAL, und das ist ein Befund der Einspritzungskampagne vom
        // 08.11.2026: Bis hierher war die Probe auch gruen, wenn die naechste Nummer aus
        // der GROESSE der Karte gerechnet wurde (stuecke.size() + 1). Beim ersten
        // Schritt stimmt das zufaellig; erst beim zweiten laeuft es auseinander, weil
        // eine geloeschte Zeile die Groesse senkt und die Nummer damit zurueckfaellt.
        fach.nimm(bert, List.of(annas + 1L));
        fach.lege(anna, "Laden halle-1", AbholVertrag.WANN,
                List.of(AbholVertrag.gut("Annas Holz")));

        assertEquals(annas + 2L, fach.fuerSpieler(anna).get(0).id(),
                "die dritte Nummer steigt weiter, obwohl das Fach wieder leer war");
    }

    @Test
    @DisplayName("Ein Stapel, der auffaellt, laesst die vorigen NICHT im Fach")
    void alleOderKeines() {
        // Das zweite Versprechen, und es ist der Grund fuer die zwei Schleifen in lege:
        // Der dritte Stapel wirft, weil sein Inhalt leer ist. Lagen die ersten zwei schon
        // im Fach, stuenden sie beim zweiten Versuch doppelt da - und ein verdoppelter
        // Diamantblock ist in einer Wirtschaft mit Bundesmarkt sofort Geld.
        SpeicherAbholfach fach = new SpeicherAbholfach();
        UUID anna = UUID.randomUUID();
        List<Abholgut> mitFehler = java.util.Arrays.asList(
                AbholVertrag.gut("eins"), AbholVertrag.gut("zwei"), null);

        assertThrows(RuntimeException.class,
                () -> fach.lege(anna, "Laden halle-1", AbholVertrag.WANN, mitFehler));

        assertEquals(0, fach.anzahlFuer(anna), "nichts darf liegen geblieben sein");
    }
}
