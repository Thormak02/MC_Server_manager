package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wer welche Teamrolle hat - eine Zeile je Spieler UND Rolle (Schritt 16a Teil A).
 *
 * <p>Fuenf Versprechen, auf die sich {@link Teambuch} verlaesst und die der
 * {@code TeamVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Eine Zeile je Spieler und Rolle</b>, nicht eine je Spieler. Darum gibt es kein
 *       {@code setze} und kein "ersetze alle Rollen": Zwei Verwalter, die im selben
 *       Augenblick zwei verschiedene Rollen vergeben, sollen beide vergeben haben.
 *       Setzend verschwaende der zweite Zug den ersten, und gemerkt wuerde es erst, wenn
 *       der Betroffene etwas nicht darf.</li>
 *   <li><b>{@link #gib} sagt, ob es eine AENDERUNG war.</b> {@code true} heisst: Die
 *       Rolle war neu. {@code false} heisst: Er hatte sie schon, und es ist nichts
 *       geschehen - auch die Vergabezeit bleibt dann die ALTE. Ein zweites Geben soll
 *       nicht die Spur des ersten ueberschreiben; genau diese Spur ist die Antwort auf
 *       "seit wann".</li>
 *   <li><b>{@link #nimm} sagt dasselbe umgekehrt</b> und wirft NICHT, wenn der Spieler
 *       die Rolle nicht hatte. Ein Verwalter, der eine Rolle zweimal nimmt, hat sich
 *       vertippt und keinen Fehler gemacht.</li>
 *   <li><b>Ein unbekannter Spieler hat {@link Teamstand#leer}</b> und keinen Fehler. Das
 *       ist der Normalfall: Die ueberwiegende Mehrheit der Spieler ist nicht im Team, und
 *       diese Frage wird bei JEDEM Beitritt gestellt.</li>
 *   <li><b>Die Ablage prueft nicht, ob es die Rolle gibt.</b> Was in der
 *       {@code config.yml} steht, weiss {@link Teamwerte}, und das ist eine Schicht
 *       darueber. Eine Ablage, die es mitpruefte, verlore beim Streichen einer Zeile aus
 *       der Datei alle Vergaben dieser Rolle - und ein Betreiber, der sie nach einem
 *       Vertipper wieder eintraegt, haette sie trotzdem verloren.</li>
 * </ul>
 *
 * <p><b>Es gibt kein Rollenrecht in dieser Schnittstelle.</b> Wer wem etwas geben darf,
 * entscheidet {@link Teambuch} mit den Rechten des Vergebenden; die Ablage schreibt, was
 * man ihr sagt. Eine Ablage, die selbst entschiede, waere ein zweiter Riegel neben dem
 * ersten, und zwei Riegel fuer denselben Fall machen sich gegenseitig unpruefbar.
 */
public interface Teamablage {

    /**
     * Welche Rollen dieser Spieler hat.
     *
     * @return der Stand; fuer einen Spieler ohne Rolle {@link Teamstand#leer}
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    Teamstand lies(UUID spieler);

    /**
     * Gibt diesem Spieler diese Rolle.
     *
     * @param wann   die Vergabezeit, UTC - sie kommt von aussen und nie aus der
     *               Datenbank, damit die Zeiten dieses Projekts aus EINER Uhr stammen
     * @param vonWem wer vergibt; {@code null} heisst: die Konsole
     * @return {@code true}, wenn die Rolle neu war; {@code false}, wenn er sie schon
     *         hatte - dann bleibt die alte Vergabezeit stehen
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         meldet das und behauptet NICHT, die Rolle sei vergeben: Eine Zusage ohne
     *         Zeile ist ein Moderator, der beim naechsten Beitritt wieder keiner ist
     */
    boolean gib(UUID spieler, String rolle, Instant wann, UUID vonWem);

    /**
     * Nimmt diesem Spieler diese Rolle.
     *
     * @return {@code true}, wenn sie genommen wurde; {@code false}, wenn er sie nicht
     *         hatte
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Hier ist der
     *         Wurf wichtiger als beim Geben: Eine nicht genommene Rolle ist ein
     *         Moderator, dem man die Rechte entzogen zu haben GLAUBT
     */
    boolean nimm(UUID spieler, String rolle);

    /**
     * Alle, die irgendeine Rolle haben - fuer die Teamliste.
     *
     * <p>Je Spieler EIN Stand mit allen seinen Rollen und nicht je Zeile ein Eintrag: Die
     * Liste wird gelesen, um zu sehen, wer im Team ist, und ein Verwalter mit drei Rollen
     * ist eine Person und nicht drei.
     *
     * <p>Nach Spieler-UUID sortiert und damit stabil, aber nicht nach Namen: Welchen
     * Namen eine UUID heute traegt, weiss nur der Server, und die Ablage soll ihn nicht
     * fragen muessen. Sortiert wird fuer die Anzeige, dort wo die Namen bekannt sind.
     *
     * @return alle Staende, niemals {@code null}; eine leere Liste heisst: kein Team
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Teamstand> alle();
}
