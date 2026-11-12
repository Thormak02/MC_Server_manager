package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;

/**
 * Der Vertrag aus {@link GrundstuecksablageVertrag}, geprueft an der Fassung im
 * Arbeitsspeicher.
 */
@DisplayName("Grundstuecke im Arbeitsspeicher")
class SpeicherGrundstueckeTest extends GrundstuecksablageVertrag {

    /** Die Nummern steigen, damit zwei Staedte nie dieselbe bekommen. */
    private long naechste = 100L;

    @Override
    protected Grundstuecksablage ablage() {
        return new SpeicherGrundstuecke();
    }

    /**
     * Eine blosse Zahl - diese Fassung kennt die Parzellen nicht.
     *
     * <p>Genau darin liegt der Unterschied zu {@code MariaGrundstueckeTest}, und der
     * Vertrag macht ihn sichtbar statt ihn zu verstecken: Dort legt dieselbe Methode
     * eine echte Freistadt an, weil {@code fk_grundstueck_stadt} nichts anderes
     * zulaesst.
     */
    @Override
    protected long stadtAnlegen(String welt, int feldX, int feldZ) {
        return naechste++;
    }
}
