package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die Mengenfassung haelt den Vertrag - und zaehlt, was die Chunkfassung nicht zaehlen kann.
 *
 * <p>Gleiche Bauart wie {@link SpeicherBerufeTest}. Die eine eigene Probe hier prueft, dass
 * eine zweite Marke an derselben Position auch wirklich NICHTS anlegt: Im Vertrag steht
 * nur, dass {@code markiere} dann {@code false} sagt - dass dabei kein zweiter Eintrag
 * entsteht, sieht man allein an der Zahl.
 */
@DisplayName("Blockmarken im Arbeitsspeicher")
class SpeicherBlockmarkenTest {

    @Nested
    @DisplayName("Haelt den Blockmarkenvertrag ein")
    class Vertrag extends BlockmarkenVertrag {
        @Override
        protected Blockmarken marken() {
            return new SpeicherBlockmarken();
        }
    }

    @Test
    @DisplayName("Eine zweite Marke an derselben Stelle legt nichts an")
    void zweiteMarkeLegtNichtsAn() {
        SpeicherBlockmarken marken = new SpeicherBlockmarken();
        Blockort ort = new Blockort("freimarken:marktstadt", 7, 71, 13);

        marken.markiere(ort);
        marken.markiere(ort);
        marken.markiere(new Blockort("freimarken:marktstadt", 7, 71, 13));

        // Dreimal dieselbe Position, und zwar zweimal ueber dasselbe Objekt und einmal
        // ueber ein gleiches: Ein Blockort muss ein WERT sein. Waere er es nicht - etwa
        // als Klasse ohne equals -, stuenden hier drei Marken, und die Chunkfassung
        // antwortete anders als diese.
        assertEquals(1, marken.anzahl());
    }
}
