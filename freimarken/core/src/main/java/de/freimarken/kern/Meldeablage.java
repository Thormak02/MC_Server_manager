package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wo die Meldungen liegen (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p>Sechs Versprechen, auf die sich {@link Meldebuch} verlaesst und die der
 * {@code MeldeVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Die NUMMER vergibt die Ablage</b>, nicht der Aufrufer. Sie ist fortlaufend und
 *       wird nie zweimal vergeben, solange die Ablage lebt. Wer sie von aussen vergaebe,
 *       braeuchte dafuer eine zweite Wahrheit darueber, welche schon vergeben ist - und
 *       zwei Moderatoren, die gleichzeitig eine Meldung anlegen, bekaemen dieselbe.</li>
 *   <li><b>{@link #lege} gibt die FERTIGE Meldung zurueck</b>, mit ihrer Nummer. Der
 *       Aufrufer muss nicht danach suchen; ein Suchen waere bei zwei gleichzeitigen
 *       Meldungen desselben Melders der Griff zur falschen.</li>
 *   <li><b>{@link #schreibe} schreibt nur, wenn der Stand noch der erwartete ist</b> -
 *       dieselbe Bauart wie {@link Rangablage#setze}. Zwei Moderatoren, die dieselbe
 *       Meldung im selben Augenblick annehmen, duerfen nicht beide gewinnen: Der zweite
 *       bekommt {@code false} und liest, dass sie schon jemand hat. Ohne diesen Riegel
 *       sprechen beide denselben Spieler an, und das ist schlimmer als eine
 *       unbearbeitete Meldung.</li>
 *   <li><b>Eine unbekannte Nummer gibt {@link Optional#empty()}</b> und keinen Fehler:
 *       Ein Moderator vertippt sich in der Nummer, und das ist eine Antwort, kein
 *       Absturz.</li>
 *   <li><b>{@link #offene} gibt die aeltesten ZUERST.</b> Eine Liste, die mit der
 *       neuesten beginnt, laesst die aelteste fuer immer unten stehen - und genau die
 *       wartet am laengsten. Die Sortierung ist eine Zusage und keine Bequemlichkeit.</li>
 *   <li><b>Die Ablage urteilt NIE.</b> Sie prueft keine Abklingzeit, keine Hoechstzahl
 *       und keine Rechte - das tut {@link Meldebuch} mit den Werten aus der Datei. Eine
 *       Ablage, die mitpruefte, waere ein zweiter Riegel neben dem ersten, und zwei
 *       Riegel fuer denselben Fall machen sich gegenseitig unpruefbar.</li>
 * </ul>
 *
 * <p><b>Es gibt kein Loeschen.</b> Eine Meldung wird erledigt, nicht entfernt - sie ist
 * ein Protokoll (Plan 12c.3: <i>"Alles protokolliert, mit Grund und Bearbeiter"</i>), und
 * ein Protokoll, aus dem sich Zeilen nehmen lassen, ist keines. Wer Platz braucht, raeumt
 * von Hand; gelesen wird ohnehin nur, was offen ist.
 */
public interface Meldeablage {

    /**
     * Legt eine Meldung an und gibt sie MIT ihrer Nummer zurueck.
     *
     * @param welt  der Weltname des Tatorts
     * @param jetzt der Zeitpunkt, UTC - er kommt von aussen, damit alle Zeiten dieses
     *              Projekts aus EINER Uhr stammen
     * @return die angelegte Meldung, im Stand {@link Meldestand#OFFEN}
     * @throws IllegalArgumentException wenn Melder und Betroffener derselbe sind, oder
     *         wenn der Grund nicht brauchbar ist - beides prueft {@link Meldung} selbst
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         meldet das und behauptet NICHT, die Meldung sei angelegt: Eine Zusage ohne
     *         Zeile ist ein Spieler, der auf eine Antwort wartet, die nie kommt
     */
    Meldung lege(UUID melder, UUID betroffener, String grund, String welt,
                 int x, int y, int z, Instant jetzt);

    /**
     * Die Meldung mit dieser Nummer.
     *
     * @return leer, wenn es sie nicht gibt - ein Vertipper ist kein Fehler
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Optional<Meldung> lies(long nummer);

    /**
     * Schreibt eine geaenderte Meldung - aber nur, wenn ihr Stand noch der erwartete ist.
     *
     * @param geaendert die neue Fassung; ihre Nummer muss es schon geben
     * @param erwartet  der Stand, den der Aufrufer gelesen hat
     * @return {@code true}, wenn geschrieben wurde; {@code false}, wenn die Meldung
     *         inzwischen in einem anderen Stand ist - dann hat sie ein anderer genommen,
     *         und der Aufrufer sagt das
     * @throws IllegalArgumentException wenn es die Nummer nicht gibt. Ein Wurf und kein
     *         {@code false}: {@code false} hiesse "versuch es noch einmal", und das
     *         koennte nie gelingen
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    boolean schreibe(Meldung geaendert, Meldestand erwartet);

    /**
     * Die unerledigten Meldungen - die aeltesten zuerst.
     *
     * @param hoechstens wie viele hoechstens; mindestens 1
     * @return offene und in Arbeit befindliche, nie {@code null}
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Meldung> offene(int hoechstens);

    /**
     * Wie viele unerledigte Meldungen dieser Spieler abgegeben hat.
     *
     * <p>Fuer die Hoechstzahl aus {@link Meldewerte#hoechsteOffeneJeMelder()}. Eine
     * eigene Methode und nicht {@code offene().stream().filter(...)}: Jene Liste ist
     * begrenzt, und eine Zaehlung auf einer begrenzten Liste zaehlt falsch, sobald mehr
     * Meldungen offen sind als die Liste lang ist. Genau dann greift die Grenze nicht
     * mehr - also im Ernstfall.
     *
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    int offeneVon(UUID melder);

    /**
     * Wann dieser Spieler zuletzt gemeldet hat - fuer die Abklingzeit.
     *
     * @return leer, wenn er noch nie gemeldet hat
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Optional<Instant> letzteVon(UUID melder);

    /**
     * Die beantworteten Meldungen dieses Spielers, die er noch nicht gelesen hat.
     *
     * <p><b>Das ist die Frage, ohne die die Rueckmeldung verschwindet.</b> Ein Moderator
     * schliesst eine Meldung ab, waehrend der Melder offline ist; die Chatzeile geht an
     * niemanden. Beim naechsten Beitritt wird diese Liste gefragt - und erst danach darf
     * {@link Meldung#alsGelesen()} geschrieben werden.
     *
     * <p>Die aeltesten zuerst, wie bei {@link #offene}: Wer drei Antworten offen hat,
     * liest sie in der Reihenfolge, in der sie entstanden sind.
     *
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Meldung> ungeleseneVon(UUID melder);
}
