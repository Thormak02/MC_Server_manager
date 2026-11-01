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

    /**
     * Die Mitbauer setzen.
     *
     * <p>Ist die Nummer eine GRUPPE, wird dieselbe Liste in einem Zug auf die Gruppe und
     * auf jedes ihrer Kinder geschrieben. Das ist das Durchschreiben aus Plan 6.1: Jede
     * Zeile traegt ihre Mitbauer vollstaendig, damit {@code darfBauen} eine Punktabfrage
     * ohne Umweg bleibt - und genau deshalb muss der Schreibweg alle Zeilen auf einmal
     * treffen. Alles oder nichts.
     *
     * <p>Gibt nichts zurueck: Eine Gruppenzeile hat keine Rasterzelle, ein
     * {@link Parzelle}-Objekt ohne x und z gibt es nicht, und kein Aufrufer hat den
     * frueheren Rueckgabewert je benutzt.
     */
    void mitbauerSetzen(long id, Set<UUID> mitbauer);

    /** Eine Parzelle freigeben. */
    void entfernen(long id);

    /**
     * Die Felder dieser Gruppe, nach Lage sortiert. Leer, wenn es die Gruppe nicht gibt.
     *
     * <p>Die eine Frage "welche Rasterfelder stecken in dieser Gruppe". Bewusst nicht
     * ueber {@link #vonSpieler} mit anschliessendem Filtern: Das haengt daran, dass Kind
     * und Gruppe denselben Eigentuemer fuehren, und liest alle Parzellen des Spielers,
     * um ein paar davon zu behalten.
     */
    List<Parzelle> kinderVon(long gruppeId);

    /**
     * Eine Gruppe anlegen oder erweitern - alles oder nichts.
     *
     * <p>Gibt die NUMMER zurueck und keine {@link Parzelle}: Die Gruppenzeile liegt auf
     * keiner Rasterzelle. Wie eine Umsetzung das Alles-oder-nichts erreicht, bleibt ihre
     * Sache - eine Transaktion gehoert nicht in diese Schnittstelle, denn das Lager im
     * Arbeitsspeicher hat keine.
     *
     * @param gruppeId {@link Parzelle#KEIN_ELTERNTEIL} legt eine neue Gruppe an, jede
     *                 andere Nummer erweitert die bestehende
     * @param mitbauer die Liste, die danach fuer die Gruppe UND JEDES Kind gilt, auch die
     *                 schon vorhandenen - beim Zusammenlegen ist das die Vereinigung
     * @param gebuehr  was dieser Schritt gekostet hat; wird auf den Preis der Gruppe
     *                 addiert, damit eine Gruppe ihren wahren Wert kennt
     * @param felder   die Nummern der neu anzuhaengenden Felder, nie leer
     * @return die Nummer der Gruppe
     * @throws IllegalStateException wenn die Gruppe fehlt, ein Feld nicht dem Eigentuemer
     *                               gehoert, nicht auf dem Raster liegt oder schon zu
     *                               einer Gruppe gehoert - dann ist nichts passiert
     */
    long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer, Set<UUID> mitbauer,
                      long gebuehr, List<Long> felder);

    /**
     * Eine Gruppe aufloesen: Die Felder werden wieder eigenstaendig, die Gruppenzeile
     * geht weg. Alles oder nichts.
     *
     * <p>Plan 6.3 verlangt das ausdruecklich - "sonst ist jeder Fehlgriff endgueltig".
     *
     * @throws IllegalStateException wenn die Nummer keine Gruppe ist
     */
    void auftrennen(long gruppeId);
}
