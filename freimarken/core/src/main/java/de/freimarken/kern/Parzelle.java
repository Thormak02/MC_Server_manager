package de.freimarken.kern;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Eine vergebene Parzelle in der Marktstadt.
 *
 * <p>Die Flaeche steht nicht als Koordinatenpaar drin, sondern als Parzellennummer - die
 * Umrechnung in Weltkoordinaten macht der {@link Rasterplan}. Damit gibt es genau eine
 * Stelle, die weiss, wie gross eine Parzelle ist, und ein spaeteres Umstellen des Rasters
 * laesst bestehende Datensaetze unberuehrt.
 *
 * <p>Die Welt steht als SCHLUESSEL (``freimarken:marktstadt``), nicht als Name. Namen sind
 * in Paper seit 26.1 nicht mehr der Bezugspunkt, und ein Fremdschluessel auf einen Namen
 * muss spaeter wandern.
 *
 * @param id          laufende Nummer, vom Lager vergeben
 * @param welt        Weltschluessel, z.B. {@code freimarken:marktstadt}
 * @param x           Parzellennummer auf der X-Achse
 * @param z           Parzellennummer auf der Z-Achse
 * @param eigentuemer wem sie gehoert
 * @param mitbauer    wer ausserdem bauen darf, nie {@code null}
 * @param preis       was sie gekostet hat, in Mark
 * @param erworbenAm  wann sie vergeben wurde
 */
public record Parzelle(
        long id,
        String welt,
        int x,
        int z,
        UUID eigentuemer,
        Set<UUID> mitbauer,
        long preis,
        Instant erworbenAm) {

    public Parzelle {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Weltschluessel fehlt");
        }
        if (eigentuemer == null) {
            throw new IllegalArgumentException("Eigentuemer fehlt");
        }
        // Unveraenderlich kopieren: Sonst kann der Aufrufer die Mitbauer nachtraeglich
        // aendern, ohne dass das Lager etwas davon erfaehrt - und die Datenbank weiss
        // dann etwas anderes als der Speicher.
        mitbauer = mitbauer == null ? Set.of() : Set.copyOf(mitbauer);
    }

    /** Ob dieser Spieler Eigentuemer ist. */
    public boolean gehoert(UUID spieler) {
        return eigentuemer.equals(spieler);
    }

    /** Ob dieser Spieler hier bauen darf - als Eigentuemer oder als Mitbauer. */
    public boolean darfBauen(UUID spieler) {
        return gehoert(spieler) || mitbauer.contains(spieler);
    }
}
