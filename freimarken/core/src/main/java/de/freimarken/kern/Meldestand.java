package de.freimarken.kern;

/**
 * Wo eine Meldung steht (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p><b>Vier Staende und nicht zwei</b>, und der Grund ist der Satz, der den ganzen
 * Abschnitt traegt: <i>"Der Melder bekommt eine Rueckmeldung - sonst meldet beim zweiten
 * Mal niemand mehr."</i> Mit nur "offen" und "erledigt" waere jede Meldung, die ein
 * Moderator ansieht und fuer unbegruendet haelt, als "erledigt" verbucht - und der Melder
 * lase dasselbe wie bei einer, der stattgegeben wurde. Das ist genau die Erfahrung, nach
 * der niemand mehr meldet.
 *
 * <p><b>{@link #IN_ARBEIT} ist kein Schmuck</b>: Zwei Moderatoren, die dieselbe Meldung
 * gleichzeitig bearbeiten, sprechen denselben Spieler zweimal an - und das ist schlimmer
 * als eine unbearbeitete Meldung. Der Stand haelt fest, WER sie genommen hat.
 *
 * <p>Die Reihenfolge der Konstanten ist die des Weges, nicht des Alphabets: Eine Meldung
 * geht von oben nach unten. {@code ordinal()} wird davon nirgends benutzt - was
 * gespeichert wird, ist der NAME.
 */
public enum Meldestand {

    /** Angelegt, niemand hat sie angesehen. */
    OFFEN,

    /**
     * Ein Moderator hat sie genommen - er steht in {@link Meldung#bearbeiter()}.
     *
     * <p>Aus diesem Stand gibt es zwei Wege zurueck: abschliessen oder abweisen. Ein
     * dritter Weg - "zurueckgeben" - ist nicht gebaut, und das ist eine benannte Luecke:
     * Ein Moderator, der aufhoert, laesst die Meldung in Arbeit stehen. Darum nennt die
     * Liste das Alter JEDER Meldung und nicht nur der offenen.
     */
    IN_ARBEIT,

    /**
     * Bearbeitet, mit einem Vermerk - der Melder hat recht gehabt.
     *
     * <p>Was der Moderator GETAN hat, steht nicht hier: Eine Strafe ist eine eigene Tat
     * mit eigenem Protokoll (Teil B). Hier steht nur, dass die Meldung erledigt ist und
     * was der Bearbeiter dem Melder sagt.
     */
    ABGESCHLOSSEN,

    /**
     * Angesehen und fuer unbegruendet gehalten - mit Vermerk.
     *
     * <p><b>Ein eigener Stand und kein {@link #ABGESCHLOSSEN} mit anderem Vermerk.</b> Der
     * Melder liest einen anderen Satz, und der Betreiber sieht in der Liste, wie viele
     * Meldungen ins Leere gingen. Waere das dasselbe, liesse sich ein Spieler, der
     * grundlos meldet, nicht von einem unterscheiden, dem man jedes Mal geglaubt hat.
     */
    ABGEWIESEN;

    /** Ob aus diesem Stand noch etwas werden kann. */
    public boolean inBearbeitung() {
        return this == OFFEN || this == IN_ARBEIT;
    }

    /** Ob der Melder eine Antwort zu lesen hat. */
    public boolean beantwortet() {
        return this == ABGESCHLOSSEN || this == ABGEWIESEN;
    }
}
