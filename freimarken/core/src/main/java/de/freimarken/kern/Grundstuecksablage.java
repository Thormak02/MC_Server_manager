package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wo die Grundstuecke liegen - die Ablage hinter {@link Grundstuecksbuch}.
 *
 * <p>Dieselbe Bauart wie {@link BesitzLager}: eine Schnittstelle, drei Umsetzungen
 * ({@code SpeicherGrundstuecke}, {@code ZwischenspeicherGrundstuecke},
 * {@code MariaGrundstuecke}) und EIN Vertrag, der alle drei prueft. Eine Zusage, die nur
 * fuer eine davon gilt, ist keine.
 *
 * <p><b>Jede Methode wirft bei einer Stoerung</b> und gibt nie still etwas Falsches
 * zurueck. Der Aufrufer entscheidet, was das heisst: Die Wache antwortet dann NEIN, ein
 * Befehl sagt es dem Spieler. Eine leere Liste wuerde dagegen "es gibt keine
 * Grundstuecke" bedeuten - und in einer Stadt voller Grundstuecke darf das nie die
 * Antwort auf eine Netzstoerung sein.
 */
public interface Grundstuecksablage {

    /**
     * Ein Grundstueck anlegen.
     *
     * <p>Die Ueberschneidung mit vorhandenen prueft der AUFRUFER
     * ({@link Grundstuecksbuch}) und nicht diese Methode - aber die Umsetzung darf sich
     * darauf nicht verlassen: Zwischen Pruefen und Schreiben liegt bei einer echten
     * Datenbank ein Augenblick, in dem ein zweiter Spieler dasselbe tun kann. Wie jede
     * Umsetzung das schliesst, sagt ihr eigenes Javadoc.
     *
     * @param preis was es gekostet hat - beim Zuschneiden 0
     * @return das angelegte Grundstueck, mit seiner neuen Nummer
     */
    Grundstueck anlegen(String welt, long stadtId, Zellquader flaeche, UUID eigentuemer,
                        long preis);

    /**
     * Das Grundstueck an dieser ZELLE - die Frage des heissen Pfades.
     *
     * <p>In Zellen und nicht in Bloecken, damit sie eine Ganzzahlfrage bleibt. Wer sie
     * oft stellt, legt den Zwischenspeicher davor; wer sie selten stellt, fragt
     * unmittelbar.
     */
    Optional<Grundstueck> anZelle(String welt, int zelleX, int zelleZ);

    /** Alle Grundstuecke einer Stadt, nach Nummer - fuer die Liste des Besitzers. */
    List<Grundstueck> inStadt(long stadtId);

    /**
     * ALLE Grundstuecke einer Welt - fuer das Fuellen des Zwischenspeichers.
     *
     * <p>Einmal beim Start und nie im Betrieb. Bei 399 Staedten zu hoechstens 256 Zellen
     * ist das die einzige Abfrage, die ueberhaupt gross werden kann - und sie laeuft
     * genau einmal.
     */
    List<Grundstueck> alle(String welt);

    /** Die Grundstuecke eines Spielers in dieser Welt - fuer seine eigene Liste. */
    List<Grundstueck> vonSpieler(String welt, UUID spieler);

    /** Die Mitbauer eines Grundstuecks neu setzen - ersetzend, nicht ergaenzend. */
    void mitbauerSetzen(long id, Set<UUID> mitbauer);

    /** Ein Grundstueck aufloesen. Die Flaeche faellt an die Stadt zurueck. */
    void entfernen(long id);
}
