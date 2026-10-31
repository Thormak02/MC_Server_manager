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
 * @param art         was fuer ein Stueck Besitz das ist
 * @param elternId    Nummer des uebergeordneten Besitzes, sonst {@link #KEIN_ELTERNTEIL}
 * @param zustand     ob der Besitz laeuft, gekuendigt oder verfallen ist
 */
public record Parzelle(
        long id,
        String welt,
        int x,
        int z,
        UUID eigentuemer,
        Set<UUID> mitbauer,
        long preis,
        Instant erworbenAm,
        Art art,
        long elternId,
        Zustand zustand) {

    /**
     * Keine Elternnummer.
     *
     * <p>Null ist dafuer frei: AUTO_INCREMENT beginnt bei 1, und das Speicherlager zaehlt
     * ebenfalls ab 1. Damit kostet die Umwandlung aus der Datenbank nichts, denn
     * {@code ResultSet.getLong} liefert fuer SQL NULL genau diese Null.
     */
    public static final long KEIN_ELTERNTEIL = 0L;

    public Parzelle {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Weltschluessel fehlt");
        }
        if (eigentuemer == null) {
            throw new IllegalArgumentException("Eigentuemer fehlt");
        }
        if (art == null) {
            throw new IllegalArgumentException("Art fehlt");
        }
        if (zustand == null) {
            throw new IllegalArgumentException("Zustand fehlt");
        }
        if (elternId < 0L) {
            throw new IllegalArgumentException("Elternnummer ist negativ: " + elternId);
        }
        // Der erste Teil der Bedingung ist noetig, nicht nur sauber: Ohne ihn verwuerfe
        // dieser Waechter jedes Stueck Besitz mit der Nummer 0 und ohne Elternteil - und
        // genau so sieht ein Objekt aus, das noch keine Nummer vom Lager bekommen hat.
        if (elternId != KEIN_ELTERNTEIL && elternId == id) {
            throw new IllegalArgumentException(
                    "Besitz kann nicht sein eigener Elternteil sein: " + id);
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

    /**
     * Teil eines uebergeordneten Besitzes - dann wird hier nichts einzeln geaendert.
     *
     * <p>Wer eine Gruppe aufloesen oder ihre Mitbauer aendern will, tut das an der Gruppe.
     * Das Kind traegt seinen Eigentuemer und seine Mitbauer zwar selbst - nur so bleibt
     * {@link #darfBauen} eine Abfrage ohne Umweg -, aber geschrieben wird beides
     * ausschliesslich ueber den Elternteil.
     */
    public boolean hatElternteil() {
        return elternId != KEIN_ELTERNTEIL;
    }

    /**
     * Ob dieser Spieler hier bauen darf - als Eigentuemer oder als Mitbauer.
     *
     * <p>Der Zustand steht vorn, weil er fuer alle gilt: Auf verfallenem Besitz baut auch
     * der Eigentuemer nicht mehr.
     */
    public boolean darfBauen(UUID spieler) {
        return zustand.erlaubtBauen() && (gehoert(spieler) || mitbauer.contains(spieler));
    }
}
