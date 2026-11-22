package de.freimarken.paper;

import de.freimarken.kern.Anmeldung;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Die Bruecke zwischen dem Beitritt und der Anmeldung im Buergeramt (Plan 10.1).
 *
 * <p>Sie tut genau eines: Wer die heute geltenden Regeln noch nicht angenommen hat,
 * bekommt das Formular. Ob er das muss, entscheidet {@link Anmeldung#verlangt} - die eine
 * Stelle, die auch {@code /regeln} und der Bundesmarkt fragen.
 *
 * <p><b>{@link EventPriority#MONITOR}</b>, wie bei {@link Bonuswache} und aus demselben
 * Grund: Das Formular ist eine FOLGE des Beitritts und keine Bedingung dafuer. Die
 * Reihenfolge zu den anderen Beitrittswachen ist gleichgueltig - es wird nichts gelesen,
 * was eine von ihnen erst anlegt. Insbesondere braucht die Anmeldung KEIN Konto: Sie
 * haengt an keinem Fremdschluessel, und angenommen hat ein Mensch, nicht ein Guthaben.
 *
 * <p><b>Warum NICHT in der Ankunftswache</b>, die schon am Beitritt haengt: Jene eroeffnet
 * das Konto und setzt den Spielmodus - zwei Dinge, ohne die der Spieler nicht spielen
 * kann. Das Formular ist eine dritte Sache mit eigener Einstellung und eigener Ablage, und
 * jede Wache dieses Plugins traegt genau eine Aufgabe (Rangwache, Bonuswache,
 * Mietwache). Zusammengelegt haette die Ankunftswache drei Mitspieler mehr, nur damit
 * eine Zeile dort stehen kann.
 *
 * <p><b>Angemeldet wird sie nur, wenn die Anmeldung AN ist</b> - dieselbe Entscheidung wie
 * bei der Torwache, die ohne Tore nicht angemeldet wird. Aus heisst dann wirklich aus:
 * kein Zuhoerer, kein Lesen der Ablage bei jedem Beitritt.
 *
 * <p><b>Was sie NICHT tut:</b> den Spieler festhalten. Es gibt keinen Zwang, der ihn am
 * Spielen hindert, bis er zustimmt - gesperrt ist allein der Bundesmarkt (Plan 10.1,
 * "Erst danach oeffnet sich der Bundesmarkt"). Ein festgehaltener Spieler, dessen Fenster
 * unterwegs verworfen wurde, saesse fuer immer fest.
 */
final class Anmeldewache implements Listener {

    private final Anmeldung anmeldung;
    private final Anmeldeformular formular;

    Anmeldewache(Anmeldung anmeldung, Anmeldeformular formular) {
        if (anmeldung == null || formular == null) {
            throw new IllegalArgumentException("Die Anmeldewache braucht die Anmeldung"
                    + " und ihr Formular");
        }
        this.anmeldung = anmeldung;
        this.formular = formular;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        zeigeWennNoetig(ereignis.getPlayer());
    }

    /**
     * Dasselbe von Hand - fuer den Start, wenn schon jemand da ist.
     *
     * <p>Ein Neuladen mit einer geaenderten Fassung erreicht die Anwesenden sonst erst
     * beim naechsten Beitritt. Gerufen wird das heute nur von Proben; die Stelle steht
     * hier, damit ein spaeterer Neuladeweg sie nicht ein zweites Mal baut.
     */
    void zeigeWennNoetig(Player wer) {
        if (anmeldung.verlangt(wer.getUniqueId())) {
            formular.zeige(wer);
        }
    }
}
