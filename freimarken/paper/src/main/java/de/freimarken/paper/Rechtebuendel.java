package de.freimarken.paper;

import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.Teamstand;
import de.freimarken.kern.Teamwerte;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Was ein Spieler aus RANG und TEAMROLLE zusammen darf - die EINE Stelle, an der das
 * entsteht (Schritt 16a Teil A).
 *
 * <p><b>Warum es diese Klasse gibt</b>, und das ist die ganze Begruendung: Hinter
 * {@link Rechtegriff#setze} steht ein {@code PermissionAttachment}, und das wird
 * ERSETZT und nicht ergaenzt - aus einem guten Grund, der in {@link AnhangRechte} steht.
 * Damit gibt es genau EINEN Zug, der die Rechte eines Spielers setzt, und wer einen
 * zweiten Verbraucher hinzufuegt, ohne beide Quellen zu vereinen, loescht mit der einen
 * die andere. Ein Moderator haette seine Rolle und bekaeme beim Beitritt die Rechte
 * seines Ranges - oder umgekehrt, je nachdem welcher Zug zuletzt lief.
 *
 * <p>Das ist genau die Art Fehler, gegen die dieses Projekt "alle Wege zur selben Tat"
 * gesetzt hat: Erst alle Pfade suchen, dann die Regel an EINER Stelle bauen. Darum
 * rechnet {@link Rangwache} die Menge nicht selbst, sondern fragt hier.
 *
 * <p><b>Die beiden Quellen sind unabhaengig voneinander AUS-schaltbar</b>, und keiner der
 * beiden Schalter darf den anderen treffen:
 * <ul>
 *   <li>{@code raenge.an: false} oder eine leere Leiter heisst: Fuer jeden gelten die
 *       Zahlen der {@code config.yml}. Die TEAMROLLEN bleiben davon unberuehrt - ein
 *       Server ohne Rangleiter hat trotzdem Moderatoren.</li>
 *   <li>Eine leere Rollenliste heisst: kein Team. Die RANGLEITER bleibt davon
 *       unberuehrt.</li>
 * </ul>
 * Bis zum 24.11.2026 stand in {@code Rangwache.vergibNeu} ein frueher Rueckweg fuer den
 * ersten Fall, der {@code Set.of()} setzte. Mit Teamrollen daneben waere das der Fehler
 * gewesen, den man nie findet: Ein Betreiber schaltet die Raenge ab, und am naechsten Tag
 * kann sein Moderator nichts mehr - zwei Einstellungen, von denen jede fuer sich richtig
 * aussieht.
 */
final class Rechtebuendel {

    private Rechtebuendel() {
    }

    /**
     * Die Rechte dieses Spielers - MIT Vorsilbe, aus beiden Quellen vereint.
     *
     * <p>Eine leere Menge ist ein gueltiges Ergebnis und heisst: Dieser Griff haelt
     * nichts. So sieht ein Spieler ohne Rolle auf einem Server ohne Rangleiter aus.
     *
     * @param raenge     die Leiter aus der Datei; {@code null} heisst: keine
     * @param rangstand  der erreichte Rang; {@code null} heisst: nie aufgestiegen
     * @param team       die Rollen aus der Datei; {@code null} heisst: kein Team
     * @param teamstand  die Rollen dieses Spielers; {@code null} heisst: keine
     */
    static Set<String> fuer(Rangwerte raenge, Rangstand rangstand,
                            Teamwerte team, Teamstand teamstand) {
        // LinkedHashSet und nicht HashSet: Die Reihenfolge steht im Startbericht und in
        // /team zeigen, und eine Liste, die bei jedem Start anders sortiert ist, laesst
        // sich zwischen zwei Starts nicht vergleichen. Erst der Rang, dann das Team -
        // die Leiter ist das, was jeder hat, die Rolle das Hinzugekommene.
        Set<String> rechte = new LinkedHashSet<>(rangrechte(raenge, rangstand));
        if (team != null && teamstand != null) {
            rechte.addAll(team.rechteFuer(teamstand.rollen()));
        }
        return Collections.unmodifiableSet(rechte);
    }

    /**
     * Was der Rang gibt - oder nichts, wenn die Leiter nicht greift.
     *
     * <p>Der Rueckweg bei {@code !greift()} steht HIER und nicht beim Aufrufer: Dort
     * nahm er bis zum 24.11.2026 auch die Teamrechte mit. Jetzt trifft er nur noch die
     * Quelle, zu der er gehoert - siehe den Klassenkopf.
     */
    private static Set<String> rangrechte(Rangwerte raenge, Rangstand rangstand) {
        if (raenge == null || !raenge.greift() || rangstand == null) {
            return Set.of();
        }
        Rangwerte.Stufe stufe = rangstand.stufeIn(raenge);
        // Kann nicht null sein, solange greift() gilt - die Leiter ist dann nicht leer.
        // Trotzdem gefragt: Eine spaetere Aenderung an greift() soll hier keinen Absturz
        // beim Beitritt ergeben.
        return stufe == null ? Set.of()
                : Rangzahl.knoten(raenge.buendel(stufe.schluessel()));
    }
}
