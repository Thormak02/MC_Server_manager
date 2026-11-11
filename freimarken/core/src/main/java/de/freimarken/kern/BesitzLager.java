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
     * Eine Freistadt gruenden - ein ganzes Stadtfeld auf einmal.
     *
     * <p>Ein EIGENER Weg und kein {@code art}-Parameter an {@link #anlegen}: Beide
     * Umsetzungen fuehren ihre Art als Literal und begruenden das im Quelltext. Ein
     * durchgereichter Parameter kippte diese Entscheidung still - danach koennte jeder
     * Aufrufer jede Art erzeugen, auch eine GRUPPE ohne Kinder.
     *
     * <p><b>x und z sind FELDnummern</b>, keine Blockkoordinaten - genau wie bei
     * {@link #anlegen} Parzellennummern stehen. Die Umrechnung macht der
     * {@link Stadtfeldplan}. Dadurch bleibt {@link #finde} auch fuer eine Freistadt eine
     * Punktabfrage, und die Schutzwache braucht keinen zweiten Weg.
     *
     * @param name der genormte Stadtname, siehe {@link Stadtname#genormt}
     * @throws IllegalStateException wenn das Feld schon vergeben oder der Name schon
     *                               benutzt ist - BEIDES ist ein Riegel der Ablage und
     *                               keine Pruefung des Aufrufers. Eine Pruefung davor
     *                               haette zwischen Fragen und Schreiben eine Luecke,
     *                               und zwei Spieler, die gleichzeitig tippen, bekaemen
     *                               denselben Namen.
     */
    Parzelle freistadtAnlegen(String welt, int feldX, int feldZ, UUID eigentuemer,
                              long preis, String name);

    /**
     * Alle Freistaedte dieser Welt, nach Namen sortiert.
     *
     * <p>Die EINE Frage, aus der alles Weitere faellt: welches Feld noch frei ist, wo ein
     * Name hinfuehrt, was die Liste zeigt. Ein eigener Weg je Frage waere dreimal
     * dieselbe Abfrage mit drei Gelegenheiten, auseinanderzulaufen.
     *
     * <p>Die Sortierung ist zugesagt, damit zwei Aufrufe dieselbe Reihenfolge geben -
     * eine Liste, die mal so und mal anders kommt, ist im Spiel nicht zu lesen.
     *
     * <p><b>Ohne Filter auf den Zustand</b>, aus demselben Grund wie {@link #finde}: Eine
     * verfallene Stadt belegt ihr Feld weiter. Wer bei der Zuteilung nur die aktiven
     * zaehlt, vergibt ein belegtes Feld, und der Spieler zahlt in den eindeutigen
     * Schluessel hinein.
     */
    List<Parzelle> staedteIn(String welt);

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

    /**
     * Ein einzelnes Feld aus seiner Gruppe loesen - es wird wieder eigenstaendig, die
     * Gruppe bleibt mit ihren uebrigen Feldern bestehen.
     *
     * <p>Getrennt von {@link #auftrennen}, weil es etwas anderes ist: Wer vier Felder
     * verbunden hat und eines abgeben will, verlor sonst alle Wege dazwischen und musste
     * dreimal neu zusammenlegen, Gebuehr inbegriffen.
     *
     * <p>Der Preis der Gruppe bleibt, wie er ist. Beim Auftrennen gibt es kein Geld
     * zurueck, und beim Herausloesen eines Feldes genauso wenig - eine Rueckrechnung waere
     * eine zweite Preisregel neben der Staffel.
     *
     * @param feldId die Nummer des Feldes, nicht die der Gruppe
     * @throws IllegalStateException wenn es das Feld nicht gibt oder es zu keiner Gruppe
     *                               gehoert - dann ist nichts passiert
     */
    void ausGruppeLoesen(long feldId);
}
