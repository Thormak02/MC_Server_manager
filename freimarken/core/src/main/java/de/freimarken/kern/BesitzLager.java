package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wo Besitz dauerhaft liegt - als Schnittstelle, damit der Kern die Datenbank nicht kennt.
 *
 * <p>Das ist die Umsetzung von E7 aus dem Plan: Der Zugriff liegt hinter einer
 * Schnittstelle, der Umstieg von einer Ablage auf eine andere ist dann ein
 * Konfigurationswert statt einer Wanderung durch den Spielcode.
 *
 * <p>Zwei Zusagen, auf die sich der Kern verlaesst und die jede Umsetzung einhalten muss:
 *
 * <ul>
 *   <li>Was {@link #anlegen} zurueckgibt, ist dauerhaft gespeichert. Kein "wird schon
 *       irgendwann geschrieben" - sonst verliert ein Absturz Besitz, und das ist der
 *       Fehler, den Spieler nie verzeihen.
 *   <li>{@link #finde} ist die Wahrheit. Ein Zwischenspeicher darueber ist erlaubt, aber
 *       er muss bei jeder Aenderung ungueltig werden - sobald ein zweiter Server dazukommt,
 *       ist alles andere falsch.
 * </ul>
 */
public interface BesitzLager {

    /** Die Parzelle an dieser Stelle, falls vergeben. */
    Optional<Parzelle> finde(String welt, int x, int z);

    /** Alle Parzellen dieses Spielers in dieser Welt, nach Nummer sortiert. */
    List<Parzelle> vonSpieler(String welt, UUID spieler);

    /** Wie viele Parzellen dieser Spieler in dieser Welt haelt. */
    int anzahlVon(String welt, UUID spieler);

    /**
     * Eine Parzelle vergeben.
     *
     * @throws IllegalStateException wenn die Stelle schon vergeben ist - der Aufrufer
     *                               haette vorher fragen muessen, und ein stilles
     *                               Ueberschreiben waere Besitzverlust
     */
    Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis);

    /** Die Mitbauer einer Parzelle setzen. Gibt den neuen Stand zurueck. */
    Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer);

    /** Eine Parzelle freigeben. */
    void entfernen(long id);
}
