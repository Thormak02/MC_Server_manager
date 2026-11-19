package de.freimarken.paper;

import java.util.Set;
import org.bukkit.entity.Player;

/**
 * Die EINE Naht zwischen dem Rang und dem Rechtesystem von Bukkit.
 *
 * <p>Dieselbe Bauart wie {@code Weltgriff} und {@code Chunkschau} und aus demselben
 * Grund: Dahinter steht {@code PermissionAttachment}, und das ist eine Klasse ohne
 * Schnittstelle, die ein laufendes Plugin und einen echten Spieler verlangt. Ohne diese
 * Naht liesse sich die Vergabe nur auf einem gestarteten Server pruefen - also gar nicht,
 * denn dort faellt eine vergessene Zeile erst auf, wenn ein Spieler sich beschwert.
 *
 * <p><b>Was hier NICHT steht:</b> welche Rechte ein Rang gibt. Das rechnet
 * {@code Rangwerte.buendel} im Kern aus, und die Namen baut {@link Rangzahl#knoten}. Ein
 * Griff, der selbst entschiede, waere eine zweite Leiter.
 */
interface Rechtegriff {

    /**
     * Gibt diesem Spieler GENAU diese Rechte - und nimmt alles zurueck, was er von diesem
     * Griff vorher hatte.
     *
     * <p><b>Ersetzen und nicht hinzufuegen</b>, und das ist die tragende Zusage: Ein
     * Aufstieg vergibt das Buendel neu, und das alte enthaelt die niedrigeren Zahlen
     * ({@code freimarken.parzellen.1} neben {@code freimarken.parzellen.2}). Blieben sie
     * stehen, waere das heute folgenlos - {@code Rangzahl} nimmt die hoechste -, aber
     * eine Leiter, die irgendwann eine Zahl SENKT, wirkte dann nie. Zurueckgenommen wird
     * nur, was dieser Griff gegeben hat; Rechte aus der Rechteverwaltung bleiben
     * unberuehrt.
     *
     * <p>Eine leere Menge ist erlaubt und heisst: nimm alles weg. So sieht ein Server
     * aus, auf dem {@code raenge.an} ausgeschaltet ist.
     */
    void setze(Player spieler, Set<String> rechte);

    /**
     * Nimmt zurueck, was dieser Griff dem Spieler gegeben hat.
     *
     * <p>Gerufen beim Verlassen. Nicht, weil Bukkit das Anhaengsel sonst behielte - es
     * geht mit dem Spieler -, sondern weil der Griff sich merken muss, wem er etwas
     * gegeben hat, und diese Merkliste sonst mit jedem Beitritt waechst, solange der
     * Server laeuft.
     */
    void loese(Player spieler);
}
