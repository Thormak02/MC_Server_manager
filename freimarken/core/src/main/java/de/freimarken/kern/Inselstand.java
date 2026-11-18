package de.freimarken.kern;

import java.time.Instant;
import java.util.Set;

/**
 * Was eine Wolkeninsel ausser ihrem Eigentuemer noch hat: Stufe, Biom, Punkte.
 *
 * <p><b>Eine eigene Zeile und keine drei Spalten an {@link Parzelle}</b>, und das ist
 * dieselbe Entscheidung wie bei den Grundstuecken: Jene Zeile liegt auf dem heissen Pfad -
 * sie wird bei JEDEM gesetzten und JEDEM abgebauten Block gelesen, in jeder Welt. Drei
 * Felder, die nur eine von sechs Arten fuellt, waeren dort drei Spalten, die bei jedem
 * Klick mitkommen; dazu haette ein viertes Feld an {@code Parzelle} 43 Aufrufstellen
 * angefasst, von denen drei die Zeile von Hand nachbauen - und dort gingen sie LAUTLOS
 * verloren (der Fund vom 11.11.2026).
 *
 * <p><b>Die STUFE ist gezaehlt und nicht gespeichert</b> ({@link #stufe}): Sie IST die
 * Zahl der erfuellten Herausforderungen. Eine eigene Spalte daneben waere eine zweite
 * Wahrheit, und die erste Zeile, die beim Erfuellen nicht mitgeschrieben wird, liesse die
 * Erzausbeute hinter den Herausforderungen zurueckbleiben - sichtbar nur daran, dass ein
 * Spieler sich beschwert.
 *
 * @param inselId    die Nummer der {@link Parzelle}, zu der dieser Stand gehoert
 * @param biom       das gewaehlte Biom, oder {@link #KEIN_BIOM}
 * @param punkte     die Punktzahl des letzten Blockzaehlens
 * @param gezaehltAm wann zuletzt gezaehlt wurde, oder {@link #NIE_GEZAEHLT}
 * @param erfuellt   die Schluessel der erfuellten Herausforderungen
 */
public record Inselstand(long inselId, String biom, long punkte, Instant gezaehltAm,
                         Set<String> erfuellt) {

    /**
     * Noch kein Biom gewaehlt.
     *
     * <p>{@code null} und nicht ein leerer Text, wie {@link Parzelle#KEIN_NAME}: Die
     * Spalte ist NULL-faehig, und NULL heisst hier "nie gewaehlt" - nicht "das Biom mit
     * dem leeren Namen". Was dann GILT, sagt {@code Wolkenbiome.VORGABE}, und es steht
     * ausdruecklich nicht hier: Diese Zeile beschreibt, was der Spieler getan hat, und
     * nicht, was gilt.
     */
    public static final String KEIN_BIOM = null;

    /** Noch nie gezaehlt - dann sind die Punkte 0, und zwar aus Unkenntnis. */
    public static final Instant NIE_GEZAEHLT = null;

    public Inselstand {
        if (inselId <= 0L) {
            throw new IllegalArgumentException("Eine Insel ohne Nummer gibt es nicht,"
                    + " nicht " + inselId);
        }
        if (punkte < 0L) {
            throw new IllegalArgumentException("Negative Punkte gibt es nicht, nicht "
                    + punkte);
        }
        if (erfuellt == null) {
            throw new IllegalArgumentException("Ohne Menge gibt es keinen Stand - eine"
                    + " LEERE Menge ist der Anfangszustand jeder Insel.");
        }
        erfuellt = Set.copyOf(erfuellt);
    }

    /**
     * Der Stand einer Insel, von der nichts bekannt ist.
     *
     * <p>Jede frisch genommene Insel steht so da, und darum legt das Nehmen KEINE Zeile
     * an: Eine Zeile aus lauter Vorgabewerten ist eine, die nichts sagt - und sie
     * entsteht beim ersten Biom, bei der ersten Herausforderung oder beim ersten Zaehlen
     * von selbst.
     */
    public static Inselstand leer(long inselId) {
        return new Inselstand(inselId, KEIN_BIOM, 0L, NIE_GEZAEHLT, Set.of());
    }

    /**
     * Die Insel-Stufe: die Zahl der erfuellten Herausforderungen.
     *
     * <p>Daran haengt die Erzausbeute des Kobbelsteingenerators
     * ({@link Erzwerte#prozentFuer}), und das ist die Entscheidung des Nutzers vom
     * 18.11.2026: Die Herausforderungen geben die STUFE, der Blockscan die PUNKTZAHL fuer
     * die Rangliste. Zwei Fortschrittsbegriffe, und jeder hat seine Aufgabe.
     */
    public int stufe() {
        return erfuellt.size();
    }

    /** Ob ein Biom gewaehlt ist. */
    public boolean hatBiom() {
        return biom != null && !biom.isBlank();
    }

    /** Ob ueberhaupt schon gezaehlt wurde. */
    public boolean schonGezaehlt() {
        return gezaehltAm != NIE_GEZAEHLT;
    }

    /** Ob diese Herausforderung erfuellt ist. */
    public boolean hatErfuellt(String schluessel) {
        return erfuellt.contains(schluessel);
    }

    /** Derselbe Stand mit einem anderen Biom. */
    public Inselstand mitBiom(String anderes) {
        return new Inselstand(inselId, anderes, punkte, gezaehltAm, erfuellt);
    }

    /** Derselbe Stand mit einer anderen Punktzahl. */
    public Inselstand mitPunkten(long andere, Instant wann) {
        return new Inselstand(inselId, biom, andere, wann, erfuellt);
    }

    /** Derselbe Stand mit einer weiteren erfuellten Herausforderung. */
    public Inselstand mitErfuellt(String schluessel) {
        var mehr = new java.util.LinkedHashSet<>(erfuellt);
        mehr.add(schluessel);
        return new Inselstand(inselId, biom, punkte, gezaehltAm, mehr);
    }
}
