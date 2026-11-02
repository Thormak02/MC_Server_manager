package de.freimarken.paper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Offene Teleportanfragen - wer wen gefragt hat, und wie lange das gilt.
 *
 * <p>Eine eigene Klasse und keine Karte im Befehl, aus demselben Grund wie bei
 * {@link Abklingzeit}: So laesst sich die Regel ohne Server pruefen, und die Zeit kommt
 * von aussen statt aus einem {@code System.nanoTime()} mitten in der Rechnung - sonst
 * waere jede Probe ein Gluecksspiel mit der Uhr.
 *
 * <p>Mehrere Anfragen an denselben Spieler sind erlaubt und werden <b>alle</b> behalten.
 * Die neueste zu nehmen und die uebrigen wegzuwerfen waere einfacher, aber dann waere
 * eine Anfrage lautlos verschwunden - und der Angefragte haette nie erfahren, dass jemand
 * gefragt hat. {@link #neueste} nennt deshalb, wen sie betrifft, und {@link #offeneFuer}
 * sagt, wie viele noch warten.
 *
 * <p>Nicht nebenlaeufig: Befehle laufen im Hauptfaden des Servers.
 */
final class Anfragen {

    /**
     * In welche Richtung die Reise geht, wenn angenommen wird.
     *
     * <p>Das ist der ganze Unterschied zwischen {@code /tpa} und {@code /tpahere} - und
     * er steht in der Anfrage und nicht im Befehl, damit {@code /tpaccept} ihn nicht
     * erraten muss.
     *
     * <p>In beiden Faellen stimmt der zu, der <b>bewegt</b> wird: bei {@code ZU_DIR} der
     * Fragende selbst (er hat ja gefragt), bei {@code HIERHER} der Eingeladene (er nimmt
     * an). Deshalb ist {@code /tpahere} kein Missbrauchswerkzeug - niemand wird gegen
     * seinen Willen gezogen.
     */
    enum Richtung {
        /** Der Fragende kommt zum Angefragten: {@code /tpa}. */
        ZU_DIR,
        /** Der Angefragte kommt zum Fragenden: {@code /tpahere}. */
        HIERHER
    }

    /**
     * Eine Anfrage.
     *
     * @param von        wer fragt
     * @param an         wer entscheidet
     * @param richtung   wer sich bewegt, wenn angenommen wird
     * @param gestelltNs wann sie gestellt wurde, gemessen mit {@link System#nanoTime()}
     */
    record Anfrage(UUID von, UUID an, Richtung richtung, long gestelltNs) {

        /** Wer reist, wenn diese Anfrage angenommen wird. */
        UUID wandert() {
            return richtung == Richtung.HIERHER ? an : von;
        }

        /** Wer stehen bleibt - das Ziel der Reise. */
        UUID bleibt() {
            return richtung == Richtung.HIERHER ? von : an;
        }
    }

    private final long gueltigNs;
    private final List<Anfrage> offene = new ArrayList<>();

    Anfragen(Duration gueltig) {
        this.gueltigNs = gueltig.toNanos();
    }

    /**
     * Stellt eine Anfrage.
     *
     * @return {@code false}, wenn dieselbe Anfrage schon offen steht - dann ist nichts
     *         hinzugekommen, und der Fragende soll das lesen statt zu glauben, er habe
     *         noch einmal gefragt
     */
    boolean stellen(UUID von, UUID an, Richtung richtung, long jetztNs) {
        raeumeAuf(jetztNs);
        if (von.equals(an)) {
            // Sich selbst zu besuchen ist kein Fehler des Programms, sondern einer des
            // Spielers - und er wird oben beantwortet. Hier nur der Riegel, damit die
            // Karte nicht etwas Unmoegliches enthaelt.
            return false;
        }
        // Die Richtung gehoert zur Gleichheit: „Komm zu mir" und „Ich komme zu dir" sind
        // zwei verschiedene Fragen an dieselbe Person, und beide darf man stellen.
        boolean schonDa = offene.stream()
                .anyMatch(a -> a.von().equals(von) && a.an().equals(an)
                        && a.richtung() == richtung);
        if (schonDa) {
            return false;
        }
        offene.add(new Anfrage(von, an, richtung, jetztNs));
        return true;
    }

    /**
     * Die neueste offene Anfrage an diesen Spieler - ohne sie wegzunehmen.
     *
     * <p>Zum Anzeigen. Wer sie beantwortet, nimmt sie mit {@link #annehmen} weg.
     */
    Optional<Anfrage> neueste(UUID an, long jetztNs) {
        raeumeAuf(jetztNs);
        return offene.stream()
                .filter(a -> a.an().equals(an))
                .max(Comparator.comparingLong(Anfrage::gestelltNs));
    }

    /** Wie viele Anfragen an diesen Spieler offen stehen. */
    long offeneFuer(UUID an, long jetztNs) {
        raeumeAuf(jetztNs);
        return offene.stream().filter(a -> a.an().equals(an)).count();
    }

    /**
     * Nimmt die neueste Anfrage an diesen Spieler weg und gibt sie zurueck.
     *
     * <p>Wegnehmen und Zurueckgeben in einem Zug, wie bei {@link Abklingzeit#restNs}:
     * Zwei Methoden daraus zu machen hiesse, dass ein Aufrufer das Wegnehmen vergisst -
     * und dann liesse sich dieselbe Anfrage zweimal annehmen.
     */
    Optional<Anfrage> annehmen(UUID an, long jetztNs) {
        Optional<Anfrage> gefunden = neueste(an, jetztNs);
        gefunden.ifPresent(offene::remove);
        return gefunden;
    }

    /** Wirft alle Anfragen an diesen Spieler weg - fuer das Ablehnen. */
    long ablehnenAlle(UUID an, long jetztNs) {
        raeumeAuf(jetztNs);
        long vorher = offene.size();
        offene.removeIf(a -> a.an().equals(an));
        return vorher - offene.size();
    }

    /**
     * Vergisst alles von und an diesen Spieler.
     *
     * <p>Beim Abmelden. Ohne das koennte ein Spieler eine Anfrage annehmen, deren
     * Fragender nicht mehr da ist - und der Befehl muesste einen Fehler melden, wo gar
     * keine Anfrage haette stehen duerfen.
     */
    void vergessen(UUID spieler) {
        offene.removeIf(a -> a.von().equals(spieler) || a.an().equals(spieler));
    }

    /** Wie viele Anfragen insgesamt offen sind - damit das Aufraeumen pruefbar ist. */
    int offene() {
        return offene.size();
    }

    /**
     * Abgelaufenes faellt bei jeder Frage weg.
     *
     * <p>Ohne das waechst die Liste mit jeder Anfrage, die je gestellt wurde, und ein
     * Serverlauf dauert Wochen.
     */
    private void raeumeAuf(long jetztNs) {
        offene.removeIf(a -> jetztNs - a.gestelltNs() >= gueltigNs);
    }
}
