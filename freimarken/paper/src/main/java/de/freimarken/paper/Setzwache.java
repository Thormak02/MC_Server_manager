package de.freimarken.paper;

import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Merkt sich jede Position, an der ein Spieler einen Block gesetzt hat.
 *
 * <p>Das ist der ganze Schritt: <b>ein Block zahlt nur einmal.</b> Noch zahlt nichts - der
 * Verdienst kommt mit Teil E -, und genau darum steht diese Wache VORHER. Der Plan sagt es
 * woertlich: Die Markierung muss stehen, bevor die erste Mark aus Arbeit fliesst; alles,
 * was vorher gesetzt wurde, gilt danach fuer immer als gewachsener Boden. Ein leerer
 * Anfangszustand ist also der richtige, und es ist nichts nachzutragen.
 *
 * <p><b>{@link EventPriority#MONITOR}, und das ist keine Zierde.</b> Die
 * {@link Schutzwache} laeuft auf NORMAL, und nach ihr kann ein anderer Hoerer das Setzen
 * noch abbrechen - eine Weltschutzregel, ein Baurecht eines anderen Plugins. Auf NORMAL
 * markierte diese Wache dann Positionen, an denen nie ein Block entstand: Der Spieler
 * saehe nichts, und die Position waere fuer immer entwertet. MONITOR laeuft als letztes,
 * und {@code ignoreCancelled = true} laesst jedes abgebrochene Ereignis aus. Dass hier
 * nichts am Ereignis geaendert wird, ist die Gegenleistung dafuer - MONITOR darf das nicht,
 * und diese Wache tut es auch nicht.
 *
 * <p><b>Nur das SETZEN, und nur Bloecke.</b> Drei Wege wurden geprueft und bewusst
 * ausgelassen:
 * <ul>
 *   <li><b>Das Abbauen.</b> Es markiert nicht: Wer gewachsenen Boden abbaut, hat dort
 *       nichts gesetzt. Und es LOESCHT auch nicht - die Marke bleibt, sonst waere
 *       Setzen-Abbauen-Setzen die Gelddruckmaschine des Baumeisters.</li>
 *   <li><b>Der Eimer.</b> Ein ausgegossener Eimer setzt einen Block Wasser oder Lava. Er
 *       bleibt aus, und zwar nicht aus Vergesslichkeit: Fluessigkeit ist keine bezahlbare
 *       Setzung, die Marke verhinderte also kein Geld - sie entwertete nur eine Position,
 *       an der spaeter ein echter Block stehen kann. Wer sein Feld unter Wasser setzt und
 *       es spaeter zuschuettet, soll fuer das Zuschuetten bezahlt werden.</li>
 *   <li><b>Der Klickweg.</b> Hacke, Schaufel und Knochenmehl AENDERN einen vorhandenen
 *       Block, sie setzen keinen. Ackerboden ist derselbe gewachsene Boden wie vorher.</li>
 * </ul>
 *
 * <p><b>Was offen bleibt, benannt statt stillschweigend</b>: Ein Kolben, der einen
 * markierten Block eine Position weiterschiebt, laesst die Marke zurueck - der Block steht
 * danach unmarkiert da. Und ein Pflastersteingenerator erzeugt Stein, den kein Spieler
 * gesetzt hat. Beides sind Weltregeln, keine Spielerhandlungen, und sie gehoeren zu
 * demselben eigenen Schritt, den schon {@link Schutzwache} dafuer benennt. Der
 * abnehmende Ertrag aus Teil E daempft sie; aufgehoben sind sie damit nicht.
 */
final class Setzwache implements Listener {

    private final Blockmarken marken;

    /**
     * In welchen Welten markiert wird - die Weltschluessel, nicht die Namen.
     *
     * <p>Mehrere Welten und nicht eine, anders als bei der {@link Schutzwache}: Die
     * verdienenden Berufe arbeiten gar nicht in der Marktstadt - Bergmann, Holzfaeller,
     * Bauer und Jaeger sind in Hochwald und Kornmark unterwegs. Eine Wache, die nur die
     * Marktstadt sieht, markierte genau dort nicht, wo gearbeitet wird.
     *
     * <p>Die Menge kommt aus den Welten, die Freimarken selbst baut, und wird nicht
     * zusaetzlich eingestellt: Sie steht damit an EINER Stelle, naemlich im Abschnitt
     * {@code welten:}. Eine zweite Liste liefe auseinander, sobald jemand eine Welt
     * hinzufuegt.
     */
    private final Set<String> welten;

    Setzwache(Blockmarken marken, Set<String> welten) {
        if (marken == null) {
            throw new IllegalArgumentException("Ohne Marken gibt es nichts zu merken");
        }
        if (welten == null || welten.isEmpty()) {
            throw new IllegalArgumentException("Ohne Welten markiert diese Wache nichts -"
                    + " eine leere Menge ist ein Zweig, der nie etwas tut, und das faellt"
                    + " erst mit Teil E auf: Dann zahlt jeder Block jedes Mal.");
        }
        this.marken = marken;
        this.welten = Set.copyOf(welten);
    }

    /**
     * Ein gesetzter Block - oder bei einem Bett, einer Tuer und einer grossen Blume zwei.
     *
     * <p>Duenn gehalten wie jeder Ereigniszweig dieses Projekts: Das Ereignis wird in
     * einfache Werte uebersetzt, entschieden wird in {@link #merke}. So ist die
     * Entscheidung ohne Server pruefbar, und was hier bleibt, ist die Uebersetzung.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSetzen(BlockPlaceEvent ereignis) {
        merke(orteAus(ereignis));
    }

    /**
     * Der gemeinsame Hals - hier wird die Welt geprueft und markiert.
     *
     * <p>Die Weltpruefung steht HIER und nicht im Zweig: Ein Ort traegt seine Welt, und so
     * ist die Pruefung mit einfachen Werten pruefbar. Ein {@code markiereAlle} fuer die
     * ganze Liste und nicht je Ort eine Marke: Ein Bett liegt in einem Abschnitt, und die
     * Chunkfassung schreibt dessen 512-Byte-Feld dann einmal statt zweimal.
     *
     * @return wie viele Marken NEU sind - heute nur fuer die Proben, mit Teil E die Zahl
     *         der Positionen, die zahlen
     */
    int merke(List<Blockort> orte) {
        List<Blockort> eigene = new ArrayList<>();
        for (Blockort ort : orte) {
            if (welten.contains(ort.welt())) {
                eigene.add(ort);
            }
        }
        return eigene.isEmpty() ? 0 : marken.markiereAlle(eigene);
    }

    /**
     * Die Positionen, die dieses Setzen belegt.
     *
     * <p>{@link BlockMultiPlaceEvent} erbt von {@link BlockPlaceEvent} und kommt durch
     * denselben Zweig - ein Bett, eine Tuer, eine grosse Blume, eine Truhe mit zwei
     * Haelften. Ohne {@code getReplacedBlockStates} waere nur die eine Haelfte markiert,
     * und die andere brachte beim Abbauen Geld: ein halber Block aus jeder Tuer.
     *
     * <p>Eine Menge mit Reihenfolge, weil der gesetzte Block in der Liste der ersetzten
     * Zustaende meist schon enthalten ist - zweimal derselbe Ort waere keine zweite Marke,
     * aber eine zweite Zeile in der Antwort.
     */
    private static List<Blockort> orteAus(BlockPlaceEvent ereignis) {
        Set<Blockort> orte = new LinkedHashSet<>();
        orte.add(ortVon(ereignis.getBlock()));
        if (ereignis instanceof BlockMultiPlaceEvent mehrere) {
            for (BlockState zustand : mehrere.getReplacedBlockStates()) {
                orte.add(new Blockort(zustand.getWorld().key().asString(),
                        zustand.getX(), zustand.getY(), zustand.getZ()));
            }
        }
        return List.copyOf(orte);
    }

    private static Blockort ortVon(Block block) {
        return new Blockort(block.getWorld().key().asString(),
                block.getX(), block.getY(), block.getZ());
    }
}
