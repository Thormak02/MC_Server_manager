package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Berufe im Arbeitsspeicher - fuer den Betrieb ohne Datenbank und fuer die Proben.
 *
 * <p><b>Vergisst beim Neustart, und das ist der Zweck.</b> Ohne eingetragene Datenbank
 * laeuft der Server fluechtig und sagt das laut; diese Fassung ist dann die Ablage. Und
 * sie ist die zweite Umsetzung desselben Vertrags - damit {@code BerufeVertrag} beide
 * prueft und keine Zusage nur fuer eine gilt.
 *
 * <p><b>Keine Uhr.</b> Der Zeitpunkt kommt im {@link Berufsablage.Antrag} herein, so wie
 * es die fuenfte Zusage des Vertrags verlangt. Eine eigene Uhr hier liesse sich nicht
 * pruefen, ohne zu warten.
 *
 * <p><b>{@code synchronized} auf jeder Methode</b>, und nicht aus Vorsicht: Der
 * Vergleich mit dem erwarteten Stand und das Schreiben muessen EINE Tat sein. Liefen
 * zwei {@code /beruf waehlen} dazwischen, bestaetigten beide denselben Stand und beide
 * schrieben - drei Grundberufe. Das Zusammenspiel ist dasselbe wie bei
 * {@link SpeicherKasse}.
 */
public final class SpeicherBerufe implements Berufsablage {

    /** Je Spieler die aktiven Berufe mit dem Zeitpunkt der Wahl, in deren Reihenfolge. */
    private final Map<UUID, LinkedHashMap<String, Instant>> aktiv = new HashMap<>();

    /** Je Spieler der juengste Zeitpunkt eines ABLEGENS - die Sperrfrist selbst. */
    private final Map<UUID, Instant> letztesAblegen = new HashMap<>();

    @Override
    public synchronized Berufsstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        LinkedHashMap<String, Instant> seine = aktiv.get(spieler);
        List<String> berufe = seine == null ? List.of()
                : List.copyOf(new ArrayList<>(seine.keySet()));
        return new Berufsstand(spieler, berufe, letztesAblegen.get(spieler));
    }

    @Override
    public synchronized boolean waehle(Antrag antrag, Berufsstand erwartet) {
        if (!giltNoch(antrag, erwartet)) {
            return false;
        }
        // Haelt er ihn schon, ist nichts zu tun - und das ist KEIN Fehlschlag: Der
        // erwartete Stand gilt, die Ablage steht danach so, wie der Aufrufer es wollte.
        // Die Regel hat diesen Fall vorher als SCHON_GEHALTEN abgewiesen; hier wuerde ein
        // false einen Erfolg als Wettlauf melden.
        aktiv.computeIfAbsent(antrag.spieler(), wer -> new LinkedHashMap<>())
                .putIfAbsent(antrag.beruf(), antrag.jetzt());
        return true;
    }

    @Override
    public synchronized boolean verlasse(Antrag antrag, Berufsstand erwartet) {
        if (!giltNoch(antrag, erwartet)) {
            return false;
        }
        // Erst nach dem Vergleich: Ein veralteter Stand haelt den Beruf vielleicht nicht
        // mehr, und dann ist false die richtige Antwort und nicht der Wurf.
        Berufsablage.pruefeAbzulegen(antrag, erwartet);
        LinkedHashMap<String, Instant> seine = aktiv.get(antrag.spieler());
        seine.remove(antrag.beruf());
        if (seine.isEmpty()) {
            aktiv.remove(antrag.spieler());
        }
        letztesAblegen.put(antrag.spieler(), antrag.jetzt());
        return true;
    }

    /**
     * Ob der Stand, auf dem geurteilt wurde, noch gilt.
     *
     * <p>Erst vollstaendig vergleichen, dann uebernehmen - nichts wird angefasst, solange
     * nicht feststeht, dass der Zug gilt. Verglichen wird der GANZE Stand und nicht nur
     * die Berufe: Ein Ablegen zwischen Urteil und Schreiben aendert nur
     * {@link Berufsstand#letztesAblegen}, und genau dann darf die Sperrfrist nicht
     * uebersprungen werden.
     */
    private boolean giltNoch(Antrag antrag, Berufsstand erwartet) {
        if (antrag == null) {
            throw new IllegalArgumentException("Ohne Antrag gibt es nichts zu schreiben");
        }
        if (erwartet == null) {
            throw new IllegalArgumentException("Ohne erwarteten Stand gaebe es nur"
                    + " lesen-rechnen-schreiben, und das verliert bei zwei gleichzeitigen"
                    + " Zuegen einen");
        }
        if (!erwartet.spieler().equals(antrag.spieler())) {
            throw new IllegalArgumentException("Der erwartete Stand gehoert "
                    + erwartet.spieler() + ", der Antrag " + antrag.spieler());
        }
        return lies(antrag.spieler()).equals(erwartet);
    }
}
