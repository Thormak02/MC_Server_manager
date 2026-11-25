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
        if (stufe == null) {
            return Set.of();
        }
        Set<String> rechte =
                new LinkedHashSet<>(Rangzahl.knoten(raenge.buendel(stufe.schluessel())));
        rechte.addAll(stufenrechte(raenge, stufe));
        return Collections.unmodifiableSet(rechte);
    }

    /**
     * Die erreichte Stufe und JEDE DARUNTER als Recht - {@code freimarken.stufe.*}.
     *
     * <p><b>Wofuer:</b> Eine Welt mit {@code ab-rang: siedler} fragt
     * {@code freimarken.stufe.siedler} ({@link Zutrittsregel}). Ein Buerger steht
     * ueber dem Siedler und muss dort hinein - darum nicht nur die erreichte Stufe,
     * sondern die ganze Strecke von unten.
     *
     * <p><b>Warum hier und nicht in {@code Rangwerte.buendel}</b> [25.11.2026]: Das
     * Buendel ist das Spiegelbild der Datei - was dort steht, hat der Betreiber
     * eingetragen. Diese Rechte hat niemand eingetragen; sie entstehen aus der
     * Leiter. Stuenden sie im Buendel, wuerde {@link Rangliste#unbekannteRechte} sie
     * mit "ist kein Recht dieses Plugins" beklagen. Dazu kommt ein gemessener
     * Grund: {@code Quellen.alle()} liest nur das Modul {@code paper}. Eine Vorsilbe
     * als Literal im Kern ist fuer {@code GrenzeTest} 31al unsichtbar - die Zusage
     * "jedes gefragte Recht ist angemeldet oder eine bekannte Vorsilbe" waere dann
     * nur scheinbar gehalten.
     *
     * <p>Steht HINTER dem Riegel in {@link #rangrechte}: Ohne Rangleiter gibt es
     * keine Stufe, und eine Welt mit Schranke bliebe sonst fuer jeden gesperrt,
     * obwohl die Raenge aus sind.
     */
    private static Set<String> stufenrechte(Rangwerte raenge,
                                            Rangwerte.Stufe erreicht) {
        int bis = raenge.nummer(erreicht.schluessel());
        if (bis < 0) {
            return Set.of();
        }
        Set<String> namen = new LinkedHashSet<>();
        java.util.List<String> leiter = raenge.schluessel();
        for (int stelle = 0; stelle <= bis && stelle < leiter.size(); stelle++) {
            namen.add(Zutrittsregel.stufenrecht(leiter.get(stelle)));
        }
        return namen;
    }
}
