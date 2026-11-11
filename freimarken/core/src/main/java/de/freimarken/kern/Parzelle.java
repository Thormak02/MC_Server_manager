package de.freimarken.kern;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Stueck vergebener Besitz - eine Parzelle in der Marktstadt oder eine Freistadt.
 *
 * <p>Der Name sagt heute weniger, als die Zeile traegt, und das ist Absicht: Alle Arten
 * liegen in derselben Tabelle, siehe {@link Art}. Umbenennen hiesse, 14 Aufrufstellen in
 * sieben Dateien anzufassen, ohne dass dabei eine einzige Regel besser wuerde.
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
 * @param name        der Name einer Freistadt, sonst {@link #KEIN_NAME}
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
        Zustand zustand,
        String name) {

    /**
     * Keine Elternnummer.
     *
     * <p>Null ist dafuer frei: AUTO_INCREMENT beginnt bei 1, und das Speicherlager zaehlt
     * ebenfalls ab 1. Damit kostet die Umwandlung aus der Datenbank nichts, denn
     * {@code ResultSet.getLong} liefert fuer SQL NULL genau diese Null.
     */
    public static final long KEIN_ELTERNTEIL = 0L;

    /**
     * Kein Name - alles ausser einer Freistadt.
     *
     * <p>{@code null} und keine leere Zeichenkette: In der Spalte steht NULL, und nur
     * NULL zaehlt InnoDB im eindeutigen Schluessel {@code (welt, name)} als verschieden.
     * Mit einer leeren Zeichenkette gaebe es genau eine namenlose Parzelle je Welt, und
     * die zweite liefe in eine Schluesselverletzung, die niemand erwartet.
     */
    public static final String KEIN_NAME = null;

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
        // Der Name gehoert zur Art, und beide Richtungen sind ein Fehler. Eine
        // Freistadt ohne Namen ist ueber keinen Warp erreichbar - 400.000 Mark fuer ein
        // Feld, das niemand findet. Und eine Parzelle MIT Namen belegte einen Namen im
        // eindeutigen Schluessel, ohne dass je eine Stadt dazu entstuende.
        //
        // Hier und nicht nur in der Datenbank: Ein CHECK auf zwei Spalten faellt erst beim
        // Schreiben auf, und zwar als Treiberausnahme ohne Satz. Hier faellt er in der
        // Probe auf, die das Objekt baut.
        if (art == Art.FREISTADT && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Eine Freistadt braucht einen Namen: " + id);
        }
        if (art != Art.FREISTADT && name != null) {
            throw new IllegalArgumentException("Nur eine Freistadt traegt einen Namen,"
                    + " nicht " + art.schluessel() + ": " + name);
        }
        // Unveraenderlich kopieren: Sonst kann der Aufrufer die Mitbauer nachtraeglich
        // aendern, ohne dass das Lager etwas davon erfaehrt - und die Datenbank weiss
        // dann etwas anderes als der Speicher.
        mitbauer = mitbauer == null ? Set.of() : Set.copyOf(mitbauer);
    }

    /**
     * Besitz ohne Namen - jede Art ausser einer Freistadt.
     *
     * <p>Damit die vierzehn Aufrufstellen in sieben Dateien nicht alle ein {@code null}
     * als zwoelftes Argument mitschleppen muessen. Das ist KEIN stiller Vorgabewert: Der
     * kanonische Konstruktor darueber wirft, wenn eine {@link Art#FREISTADT} ohne Namen
     * ankommt - dieser Weg kann eine Freistadt also gar nicht erzeugen.
     */
    public Parzelle(long id, String welt, int x, int z, UUID eigentuemer,
                    Set<UUID> mitbauer, long preis, Instant erworbenAm, Art art,
                    long elternId, Zustand zustand) {
        this(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm, art, elternId,
                zustand, KEIN_NAME);
    }

    /** Ob dieser Spieler Eigentuemer ist. */
    public boolean gehoert(UUID spieler) {
        return eigentuemer.equals(spieler);
    }

    /** Ob das eine Freistadt ist - dann und nur dann traegt die Zeile einen Namen. */
    public boolean istFreistadt() {
        return art == Art.FREISTADT;
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
