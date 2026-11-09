package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Das Blockbuch - wer wann wo was getan hat.
 *
 * <p>Fuenf Versprechen, auf die sich Abfrage und Aufraeumen verlassen und die der
 * {@code BlockbuchVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Jede Lesemethode hat eine Grenze, und sie ist Pflicht.</b> Das ist die
 *       groesste Tabelle dieses Projekts - eine Welt mit {@code benutzen} sammelt
 *       Zehntausende Zeilen am Tag. Eine Abfrage ohne Grenze holte sie alle in den
 *       Arbeitsspeicher des Spielservers, und zwar im Hauptfaden, weil ein Befehl dort
 *       ausgefuehrt wird.</li>
 *   <li><b>Gelesen wird die NEUESTE Zeile zuerst</b>, und das haengt an der Grenze: Mit
 *       "aelteste zuerst" liefe die Grenze genau falsch - sie schnitte die zwanzig
 *       aeltesten Zeilen heraus und verschwiege, was gerade passiert ist. Die
 *       Moderationsfrage ist immer "was ist hier ZULETZT geschehen".</li>
 *   <li><b>{@link #schreibe} ist NICHT alles oder nichts</b>, und das ist eine bewusste
 *       Abweichung von {@link Abholablage#lege}. Dort geht es um Gegenstaende, und eine
 *       halbe Ladung waere beim zweiten Versuch eine Verdopplung. Hier geht es um ein
 *       Protokoll, das niemand wiederholt: Eine Transaktion, die bei der
 *       dreihundertsten von fuenfhundert Zeilen scheitert, wuerfe 299 gute Zeilen weg.
 *       Ein Buch soll so viel behalten wie moeglich - was durchkommt, bleibt.</li>
 *   <li><b>{@link #raeumeAuf} loescht hoechstens so viele Zeilen, wie es soll.</b> Der
 *       Happen ist die ganze Vorsorge gegen den Stillstand: Das Aufraeumen laeuft im
 *       Hauptfaden auf der einen Verbindung, und am ersten faelligen Tageswechsel gehen
 *       Wochen auf einmal weg. Was ein Schlag nicht schafft, nimmt der naechste.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt herein -
 *       beim Schreiben in der Zeile, beim Aufraeumen als Grenze. Kein {@code NOW()} und
 *       kein {@code INTERVAL} in der Datenbank: Sonst fragte die Frist die Uhr und die
 *       Zone des Datenbankrechners, und die eingestellte Zone waere stumm
 *       wirkungslos.</li>
 * </ul>
 *
 * <p><b>Warum keine Zusage ueber Vollstaendigkeit.</b> Dieses Buch verspricht NICHT, dass
 * jede Tat darin steht. Es ist ein Protokoll und keine Buchfuehrung: Faellt die Datenbank
 * aus, verliert der Sammler seinen Stapel und sagt es ins Serverprotokoll - aber der
 * Spielserver laeuft weiter. Die andere Richtung - Bloecke verweigern, solange nicht
 * geschrieben werden kann - waere ein Server, den ein Netzfehler anhaelt, und das fuer ein
 * Buch, das niemand in diesem Augenblick liest.
 */
public interface Blockbuchablage {

    /**
     * Schreibt die gesammelten Zeilen - so viele wie moeglich.
     *
     * <p>Eine leere Liste ist erlaubt und tut nichts. Das ist der haeufigste Fall: Der
     * Takt schlaegt jede Sekunde, und auf einem ruhigen Server ist der Stapel meist leer.
     *
     * <p>Die Nummern der Zeilen werden NICHT gelesen - die Ablage vergibt sie. Eine Zeile
     * mit {@link Blockzeile#OHNE_NUMMER} ist der Normalfall.
     *
     * @param zeilen was aufzuschreiben ist, in der Reihenfolge, in der es geschah
     * @return wie viele Zeilen wirklich geschrieben wurden
     * @throws IllegalArgumentException wenn die Liste {@code null} ist oder einen Eintrag
     *         enthaelt, der {@code null} ist
     * @throws IllegalStateException wenn nicht geschrieben werden konnte - derselbe Typ
     *         fuer jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung
     *         hinter der Schnittstelle steht
     */
    int schreibe(List<Blockzeile> zeilen);

    /**
     * Was an diesem Block geschehen ist - die neueste Zeile zuerst.
     *
     * <p>Die Frage des Befehls {@code /freimarken blockbuch}: Ein Moderator steht vor
     * einem Loch und will wissen, wer es gegraben hat.
     *
     * @param ort        genau dieser Block, nicht die Saeule und nicht der Chunk
     * @param hoechstens wie viele Zeilen zurueckkommen duerfen - mindestens 1
     * @return die Zeilen, neueste zuerst, oder eine leere Liste. Leer ist der Normalfall
     *         und kein Fehler
     * @throws IllegalArgumentException wenn der Ort {@code null} ist oder die Grenze
     *         kleiner als 1
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Blockzeile> amOrt(Blockort ort, int hoechstens);

    /**
     * Was dieser Spieler getan hat - die neueste Zeile zuerst.
     *
     * <p>Die andere Moderationsfrage, und sie geht in die andere Richtung: nicht "wer war
     * hier", sondern "wo war der". Ein Dutzend abgewiesener Versuche an verschiedenen
     * Parzellen in zwei Minuten ist der Fund, den nur diese Richtung zeigt.
     *
     * @param spieler    wessen Taten
     * @param hoechstens wie viele Zeilen zurueckkommen duerfen - mindestens 1
     * @throws IllegalArgumentException wenn der Spieler {@code null} ist oder die Grenze
     *         kleiner als 1
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Blockzeile> vonSpieler(UUID spieler, int hoechstens);

    /**
     * Loescht alte Zeilen - hoechstens so viele, wie der Happen erlaubt.
     *
     * <p>Die Grenze kommt als ZEITPUNKT herein und wird nicht hier gerechnet: Welcher Tag
     * vor dreissig Tagen lag, entscheidet {@link Tagesgrenze} mit der eingestellten Zone.
     *
     * @param aelterAls  was vor diesem Zeitpunkt liegt, darf weg
     * @param hoechstens wie viele Zeilen dieser Schlag loescht - mindestens 1
     * @return wie viele Zeilen weg sind. Erreicht die Zahl {@code hoechstens}, ist
     *         vermutlich noch mehr da - der naechste Schlag nimmt es
     * @throws IllegalArgumentException wenn der Zeitpunkt {@code null} ist oder die
     *         Grenze kleiner als 1
     * @throws IllegalStateException wenn nicht geloescht werden konnte
     */
    int raeumeAuf(Instant aelterAls, int hoechstens);
}
