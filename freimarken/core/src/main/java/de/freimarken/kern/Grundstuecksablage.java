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

    /**
     * Ob diesem Spieler in dieser Stadt ueberhaupt Grund gehoert.
     *
     * <p><b>Die Punktfrage der Stadtbuergerschaft</b> [12.11.2026], und sie steht hier
     * neben {@link #anZelle} aus demselben Grund: Sie faellt auf dem HEISSEN PFAD. Die
     * Bauregel fragt bei jedem gesetzten Block, ob der Spieler Stadtbuerger ist, und
     * {@link #inStadt} waere dafuer eine Abfrage auf alle Zeilen einer Stadt - genau die
     * Richtung, gegen die der ganze Zwischenspeicher gebaut ist.
     *
     * <p><b>Ein Ja-oder-Nein und keine Liste</b>: Gefragt ist nicht, WAS ihm gehoert,
     * sondern OB. Eine Liste waere auf diesem Pfad eine Antwort, von der die Wache
     * neunundneunzig Hundertstel wegwirft - und bei einem Grossgrundbesitzer mit 256
     * Grundstuecken waere das je Block eine Liste mit 256 Zeilen.
     *
     * <p><b>Die Buergerregel steht NICHT hier</b>, sondern in
     * {@link Stadtbuergerschaft#istBuerger}: Ob der Stadtbesitzer als Buerger zaehlt, ist
     * eine Regel und keine Ablagefrage. Diese Methode sagt nur, was in der Tabelle steht.
     *
     * @param welt    der Weltschluessel - er entscheidet, ob ein Zwischenspeicher
     *                zustaendig ist
     * @param stadtId die Stadt, also die Nummer ihrer Parzellenzeile
     * @param spieler wer gefragt ist; {@code null} ist niemals Besitzer
     */
    boolean besitztIn(String welt, long stadtId, UUID spieler);

    /** Die Mitbauer eines Grundstuecks neu setzen - ersetzend, nicht ergaenzend. */
    void mitbauerSetzen(long id, Set<UUID> mitbauer);

    /** Ein Grundstueck aufloesen. Die Flaeche faellt an die Stadt zurueck. */
    void entfernen(long id);

    /**
     * Den Angebotspreis setzen oder zuruecknehmen.
     *
     * @param angebotspreis was es kosten soll, oder {@link Grundstueck#KEIN_ANGEBOT}.
     *                      Eine 0 heisst "zu verschenken" und ist etwas anderes als
     *                      {@code null}
     */
    void anbieten(long id, Long angebotspreis);

    /**
     * Den Eigentuemer wechseln - der VERKAUF.
     *
     * <p><b>EINE Methode fuer drei Aenderungen</b>, und das ist der Kern der Sache: Der
     * neue Eigentuemer, der bezahlte Preis und das Zuruecknehmen des Angebots gehoeren
     * in einen Zug. Drei einzelne Aufrufe haetten zwei Augenblicke dazwischen, in denen
     * das Grundstueck einem anderen gehoert und noch zum alten Preis ausliegt - ein
     * zweiter Kaeufer koennte es ein zweites Mal kaufen.
     *
     * <p><b>Die Mitbauer werden GELEERT.</b> Wer ein Grundstueck verkauft, verkauft es
     * nicht mitsamt den Leuten, die darauf bauen durften: Der Kaeufer bekaeme eine
     * Flaeche, auf der Fremde Rechte haben, die er nicht kennt. Dass das hier geschieht
     * und nicht beim Aufrufer, ist Absicht - eine Umsetzung, die es vergisst, waere eine
     * Sicherheitsluecke, und keine Probe am Buch wuerde sie sehen.
     *
     * <p><b>{@code erworbenAm} wird NEU gesetzt</b>, und das ist keine Kosmetik: Die
     * Verfallsregel aus Plan 16 rechnet mit diesem Zeitpunkt. Bliebe er stehen, kaufte
     * jemand ein Grundstueck und haette es nach dem Zeitpunkt des VORBESITZERS schon
     * verwirkt - bei einem alten Grundstueck sofort. Der Name sagt dasselbe: erworben
     * hat es der, dem es jetzt gehoert.
     *
     * @param preis was der Kaeufer bezahlt hat - geht in die Spalte {@code preis}
     * @return das Grundstueck, wie es NACHHER ist. Zurueckgegeben und nicht vom Aufrufer
     *         zusammengebaut: Ein Aufrufer, der die Zeile selbst nachbildet, raet den
     *         neuen Zeitpunkt - und raet ihn eines Tages falsch. Dieselbe Bauart wie
     *         {@link #anlegen}
     * @throws IllegalStateException wenn es das Grundstueck nicht gibt oder es nicht
     *                               (mehr) zum Verkauf steht. Die zweite Bedingung ist
     *                               der RIEGEL gegen den Doppelkauf: Zwei Kaeufer, die
     *                               gleichzeitig zugreifen, laufen hier auf
     */
    Grundstueck eigentuemerSetzen(long id, UUID neuerEigentuemer, long preis);

    /** Alle Grundstuecke einer Welt, die zum Verkauf stehen - fuer die Angebotsliste. */
    List<Grundstueck> angebote(String welt);
}
