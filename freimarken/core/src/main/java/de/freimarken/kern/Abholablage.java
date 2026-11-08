package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Das Abholfach - wo Gegenstaende warten, die ihrem Besitzer nicht mehr zugestellt werden
 * konnten.
 *
 * <p>Fuenf Versprechen, auf die sich die Raeumung verlaesst und die der
 * {@code AbholVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Nichts wird von selbst geloescht.</b> Kein Verfall, keine Obergrenze, kein
 *       Aufraeumen nach einem Jahr - Plan 7.1 sagt "wird NIE geloescht", und das ist das
 *       ganze Versprechen des Fachs. Eine Obergrenze waere genau der Fall, in dem die
 *       Raeumung eines vollen Ladens Gegenstaende wegwirft, und sie traefe den
 *       Fleissigsten.</li>
 *   <li><b>{@link #lege} ist alles oder nichts.</b> Eine halb eingelegte Truhe waere
 *       schlimmer als eine nicht eingelegte: Beim zweiten Versuch stuenden die ersten
 *       Stapel doppelt im Fach, und das ist die Gelddruckmaschine fuer Gegenstaende.</li>
 *   <li><b>{@link #fuerSpieler} liefert das AELTESTE zuerst</b>, und zweimal
 *       hintereinander dieselbe Reihenfolge. Wer ein Fach in mehreren Griffen leert,
 *       bekommt sonst beim zweiten Griff andere Stapel als beim ersten angezeigt.</li>
 *   <li><b>{@link #nimm} loescht die genannten Nummern UND NUR DIE EIGENEN.</b> Eine
 *       Nummer, die nicht (mehr) da ist oder einem anderen gehoert, wird uebergangen und
 *       ist kein Fehler: Zwei Fenster, die dasselbe Stueck holen, duerfen nicht beide
 *       werfen - eines bekommt es, das andere nichts. Die Zahl im Rueckgabewert sagt, wie
 *       viele wirklich weg sind, und der Aufrufer gibt nur diese heraus.
 *       <p>Der Empfaenger ist Teil der Frage und keine Hoeflichkeit. MariaDB vergibt die
 *       Nummern mit {@code AUTO_INCREMENT}, und dessen Zaehler ueberlebt einen Neustart
 *       nicht zuverlaessig: Er beginnt dann bei {@code MAX(id) + 1}, also kann eine
 *       Nummer, deren Zeile abgeholt wurde, ein zweites Mal vergeben werden. Ohne den
 *       Empfaenger in der Abfrage holte ein alt gewordenes Fenster - oder eine geratene
 *       Zahl - die Ware eines fremden Spielers.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt mit
 *       herein, wie bei {@link Mietablage} der Tag. Kein {@code NOW()} in der Datenbank -
 *       sonst waere die eingestellte Zone stumm wirkungslos.</li>
 * </ul>
 *
 * <p><b>Warum eine eigene Ablage und nicht eine Spalte am Vertrag.</b> Das Fach
 * ueberlebt den Vertrag - es entsteht in dem Augenblick, in dem der Vertrag geloescht
 * wird, und muss noch da sein, wenn der Spieler Wochen spaeter wiederkommt und laengst
 * einen anderen Laden hat. Eine Spalte an einer Zeile, die geloescht wird, kann das
 * nicht.
 */
public interface Abholablage {

    /**
     * Was fuer diesen Spieler im Fach liegt - das aelteste Stueck zuerst.
     *
     * @return die Stuecke, oder eine leere Liste. Leer ist der Normalfall und kein Fehler
     * @throws IllegalArgumentException wenn der Spieler {@code null} ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    List<Abholstueck> fuerSpieler(UUID empfaenger);

    /**
     * Wie viele Stuecke dieser Spieler im Fach hat.
     *
     * <p>Eine eigene Frage und nicht {@code fuerSpieler().size()}: Beim Beitritt wird sie
     * fuer jeden Spieler gestellt, und dann soll nicht der ganze Inhalt - samt der langen
     * Textspalte - durch die Leitung kommen, nur um ihn zu zaehlen.
     *
     * @throws IllegalArgumentException wenn der Spieler {@code null} ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    int anzahlFuer(UUID empfaenger);

    /**
     * Legt Stapel ins Fach - alles oder nichts.
     *
     * <p>Eine leere Liste ist erlaubt und tut nichts. Das ist kein Entgegenkommen,
     * sondern der haeufigste Fall beim Raeumen: Die meisten verlassenen Laeden haben eine
     * leere Truhe, und der Aufrufer soll davor nicht erst fragen muessen.
     *
     * @param empfaenger wem die Stapel gehoeren
     * @param herkunft   woher sie kommen, in Worten
     * @param wann       der Zeitpunkt - kommt herein, siehe das fuenfte Versprechen
     * @throws IllegalArgumentException wenn Empfaenger oder Liste {@code null} sind;
     *         dazu, sobald die Liste nicht leer ist, auch bei fehlender Herkunft,
     *         fehlendem Zeitpunkt oder einem Eintrag, der {@code null} ist. Bei einer
     *         LEEREN Liste werden Herkunft und Zeitpunkt nicht geprueft: Es wird nichts
     *         geschrieben, also beschriftet auch nichts etwas falsch
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Dann liegt
     *         NICHTS im Fach - der Aufrufer darf die Truhe nicht leeren
     */
    void lege(UUID empfaenger, String herkunft, Instant wann, List<Abholgut> stapel);

    /**
     * Nimmt die genannten Stuecke aus dem Fach - und sagt, wie viele es wirklich waren.
     *
     * <p><b>Diese Zahl ist die Erlaubnis, Gegenstaende herauszugeben.</b> Der Aufrufer
     * loescht ZUERST und gibt DANN; was hier nicht geloescht wurde, darf er nicht geben.
     * Die andere Reihenfolge - geben, dann loeschen - verdoppelt bei einem Absturz
     * dazwischen jeden Stapel, und ein verdoppelter Diamantblock ist in einer Wirtschaft
     * mit Bundesmarkt sofort Geld.
     *
     * @param empfaenger wessen Stuecke; fremde werden uebergangen
     * @param ids die Nummern; eine unbekannte wird uebergangen
     * @return wie viele Zeilen wirklich geloescht wurden
     * @throws IllegalArgumentException wenn Empfaenger oder Liste {@code null} sind oder
     *         die Liste eine Nummer enthaelt, die keine ist
     * @throws IllegalStateException wenn nicht geloescht werden konnte
     */
    int nimm(UUID empfaenger, List<Long> ids);
}
