package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wo die Strafen liegen (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p>Sieben Versprechen, auf die sich {@link Strafbuch} verlaesst und die der
 * {@code StrafVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Die NUMMER vergibt die Ablage</b>, nicht der Aufrufer - dieselbe Festlegung
 *       wie bei {@link Meldeablage}. Sie ist fortlaufend und wird nie zweimal vergeben,
 *       solange die Ablage lebt.</li>
 *   <li><b>{@link #lege} gibt die FERTIGE Strafe zurueck</b>, mit ihrer Nummer. Der
 *       Aufrufer muss nicht danach suchen; ein Suchen waere bei zwei gleichzeitigen
 *       Strafen gegen denselben Spieler der Griff zur falschen.</li>
 *   <li><b>{@link #hebeAuf} schreibt nur, wenn die Strafe noch steht.</b> Zwei
 *       Moderatoren, die dieselbe Sperre im selben Augenblick aufheben, duerfen nicht
 *       beide gewinnen: Der zweite bekommt {@code false} und liest, dass sie schon
 *       aufgehoben ist. Ohne diesen Riegel stehen am Ende zwei Aufhebungsgruende
 *       uebereinander, und der erste ist verloren.</li>
 *   <li><b>Eine unbekannte Nummer gibt {@link Optional#empty()}</b> und keinen Fehler:
 *       Ein Moderator vertippt sich, und das ist eine Antwort, kein Absturz.</li>
 *   <li><b>{@link #geltende} gibt die, die am LAENGSTEN gilt.</b> Es soll nie zwei
 *       geltende derselben Art gegen denselben Spieler geben - dafuer sorgt das Buch.
 *       Gibt es sie doch (eine Zeile von Hand, ein alter Datenstand), ist die laengere
 *       die Wahrheit: Von zwei Fehlern nimmt man den, der niemanden zu frueh
 *       hereinlaesst.</li>
 *   <li><b>{@link #von} gibt die NEUESTEN zuerst</b> - anders als die Meldungen, und aus
 *       dem umgekehrten Grund: Bei einer Meldung wartet die aelteste am laengsten; bei
 *       einer Vorgeschichte ist die letzte Tat die, nach der gefragt wird. Bei gleichem
 *       Zeitpunkt die hoehere Nummer zuerst, damit die Reihenfolge nicht vom Zufall
 *       abhaengt.</li>
 *   <li><b>Die Ablage urteilt NIE.</b> Sie prueft keine Rechte, keine Hoechstdauer und
 *       nicht, ob schon eine Strafe gilt - das tut {@link Strafbuch}. Zwei Riegel fuer
 *       denselben Fall machen sich gegenseitig unpruefbar.</li>
 * </ul>
 *
 * <p><b>Es gibt kein Loeschen.</b> Eine Strafe wird aufgehoben, nicht entfernt - sie ist
 * ein Protokoll (Plan 12c.3: <i>"alles protokolliert, mit Grund und Bearbeiter"</i>), und
 * ein Protokoll, aus dem sich Zeilen nehmen lassen, ist keines. Gerade die
 * zurueckgenommene Strafe ist die, nach der ein zweiter Moderator fragt.
 */
public interface Strafablage {

    /**
     * Legt eine Strafe an und gibt sie MIT ihrer Nummer zurueck.
     *
     * @param art         was geschieht
     * @param grund       warum - Pflicht
     * @param vonWem      wer; {@code null} heisst die Konsole
     * @param verhaengtAm wann, UTC - der Zeitpunkt kommt von aussen, damit alle Zeiten
     *                    dieses Projekts aus EINER Uhr stammen
     * @param dauer       wie lange; {@code null} genau dann, wenn die Art nicht
     *                    fortwirkt - das prueft {@link Strafe#neu}
     * @return die angelegte Strafe
     * @throws IllegalArgumentException wenn der Grund nicht brauchbar ist oder Art und
     *         Dauer nicht zusammenpassen - beides prueft {@link Strafe} selbst
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         meldet das und behauptet NICHT, die Strafe sei verhaengt: Eine Zusage ohne
     *         Zeile ist ein Spieler, der weiterschreibt, waehrend der Moderator ihn fuer
     *         stumm haelt
     */
    Strafe lege(UUID betroffener, Strafart art, String grund, UUID vonWem,
                Instant verhaengtAm, Strafdauer dauer);

    /**
     * Die Strafe mit dieser Nummer.
     *
     * @return leer, wenn es sie nicht gibt - ein Vertipper ist kein Fehler
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Optional<Strafe> lies(long nummer);

    /**
     * Hebt eine Strafe auf - aber nur, wenn sie noch steht.
     *
     * @param geaendert die aufgehobene Fassung; ihre Nummer muss es schon geben
     * @return {@code true}, wenn geschrieben wurde; {@code false}, wenn sie inzwischen
     *         schon aufgehoben ist - dann war ein anderer schneller, und der Aufrufer
     *         sagt das
     * @throws IllegalArgumentException wenn es die Nummer nicht gibt, oder wenn die
     *         Fassung nicht aufgehoben ist. Ein Wurf und kein {@code false}:
     *         {@code false} hiesse "versuch es noch einmal", und das koennte nie
     *         gelingen
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    boolean hebeAuf(Strafe geaendert);

    /**
     * Die Strafe dieser Art, die gegen diesen Spieler in diesem Augenblick wirkt.
     *
     * <p>Die Frage der Wache: Darf er herein, darf er schreiben? Sie wird bei JEDEM
     * Beitritt gestellt, also gehoert sie auf einen Index und nicht in eine Schleife
     * ueber die Vorgeschichte.
     *
     * @return leer, wenn keine wirkt. Bei einer Art, die nicht fortwirkt
     *         ({@link Strafart#giltFort()}), ist die Antwort immer leer - ein Rauswurf
     *         wirkt nie noch
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Optional<Strafe> geltende(UUID betroffener, Strafart art, Instant jetzt);

    /**
     * Die Vorgeschichte dieses Spielers - die neuesten zuerst.
     *
     * <p>Alle Arten und alle Staende, auch die aufgehobenen und die abgelaufenen. Das ist
     * der Zweck: Ein Moderator, der eine Meldung bearbeitet, will wissen, ob derselbe
     * Spieler schon dreimal rausgeworfen wurde.
     *
     * @param hoechstens wie viele hoechstens; mindestens 1
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Strafe> von(UUID betroffener, int hoechstens);

    /**
     * Alle Strafen dieser Art, die in diesem Augenblick wirken - die neuesten zuerst.
     *
     * <p>Fuer die oeffentliche Bannliste aus Plan 12c.4: <i>"Bannliste mit Grund und
     * Dauer - oeffentlich, wie im Vorbild."</i>
     *
     * @param hoechstens wie viele hoechstens; mindestens 1
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Strafe> geltendeAlle(Strafart art, int hoechstens, Instant jetzt);

    /**
     * Wie viele Strafen dieser Art gegen diesen Spieler schon verhaengt wurden.
     *
     * <p>Fuer die Zeile, die ein Moderator liest, bevor er entscheidet: <i>"dritter
     * Rauswurf"</i>. Eine eigene Abfrage und nicht {@code von(...).size()}: Jene Liste
     * ist begrenzt, und eine Zaehlung auf einer begrenzten Liste zaehlt falsch, sobald
     * die Vorgeschichte laenger ist als die Liste - also genau beim Wiederholungstaeter.
     *
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    int zaehle(UUID betroffener, Strafart art);
}
