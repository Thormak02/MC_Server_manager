package de.freimarken.kern;

import java.time.Clock;
import java.util.UUID;

/**
 * Die EINE Stelle, die sagt, ob ein Spieler die Regeln noch annehmen muss (Plan 10.1).
 *
 * <p><b>Drei Wege fragen dieselbe Frage</b>, und darum steht sie hier und nicht dreimal:
 * die Wache beim Beitritt (zeigt das Formular), der Befehl {@code /regeln} (zeigt einen
 * oder zwei Knoepfe) und der Bundesmarkt (sagt ab). Drei Fassungen derselben Frage wichen
 * eines Tages ab - und dann boete ein Weg etwas an, was ein anderer verweigert. Genau
 * diese Fehlerklasse hat das Hauptmenue am 21.11.2026 gekostet.
 *
 * <p><b>Die Frage wird im AUGENBLICK gestellt und nicht beim Bauen beantwortet.</b> Die
 * geltende Fassung kommt aus {@link Wirtschaftsstand} - dem Halter, nicht einem
 * Schnappschuss -, denn ein Neuladen kann sie aendern, und zwar genau dann, wenn der
 * Betreiber seine Regeln geaendert hat. Ein gemerkter Wert waere die Fassung von vorher,
 * und niemand muesste neu zustimmen.
 *
 * <p><b>Die Uhr kommt von aussen</b>, wie bei jeder Stelle dieses Moduls: Eine eigene
 * {@code Instant.now()} liesse sich nicht pruefen, ohne zu warten.
 *
 * <p><b>Was hier NICHT steht:</b> was bei einer Ablehnung geschieht. Es gibt keine
 * Ablehnung, die etwas ablegt - der zweite Knopf des Formulars schliesst es, und beim
 * naechsten Beitritt kommt es wieder. Eine Ablehnung mit Folgen (ein Hinauswurf etwa)
 * steht in keinem Plan, und eine ohne Folgen braucht keinen Eintrag.
 */
public final class Anmeldung {

    private final Anmeldeablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;

    public Anmeldung(Anmeldeablage ablage, Wirtschaftsstand wirtschaft, Clock uhr) {
        if (ablage == null || wirtschaft == null || uhr == null) {
            throw new IllegalArgumentException("Die Anmeldung braucht Ablage,"
                    + " Wirtschaftsstand und Uhr - die Fassung kommt aus dem HALTER,"
                    + " damit ein Neuladen sie aendern kann");
        }
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
    }

    /** Ob die Anmeldung ueberhaupt verlangt wird ({@code anmeldung.an}). */
    public boolean an() {
        return wirtschaft.werte().anmeldung().an();
    }

    /** Die Fassung, die heute gilt - fuer die Protokollzeile des Betreibers. */
    public String fassung() {
        return wirtschaft.werte().anmeldung().fassung();
    }

    /**
     * Ob dieser Spieler noch annehmen muss.
     *
     * <p><b>Die Frage, an der alles haengt.</b> {@code false} heisst: Er darf handeln, und
     * kein Formular kommt. Das ist auch die Antwort, wenn die Anmeldung AUS ist - dann
     * verlangt niemand etwas, und die abgelegten Annahmen bleiben unangetastet stehen.
     *
     * <p>Verglichen wird gegen die GELTENDE Fassung und nicht gegen "hat irgendwann
     * etwas angenommen": Wer einer aelteren Fassung zugestimmt hat, hat den heutigen
     * Regeln nicht zugestimmt.
     */
    public boolean verlangt(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu fragen");
        }
        if (!an()) {
            return false;
        }
        return !ablage.lies(spieler).hatAngenommen(fassung());
    }

    /**
     * Traegt die Annahme der heute geltenden Fassung ein.
     *
     * <p>Angenommen wird immer die GELTENDE Fassung - der Spieler bestimmt nicht, wozu er
     * zustimmt. Ein Parameter dafuer waere eine Tuer, durch die jemand eine alte Fassung
     * annehmen koennte, und damit eine Zustimmung zu Regeln, die niemand mehr liest.
     *
     * <p>Wirft, wenn die Anmeldung AUS ist: Dann gibt es nichts anzunehmen, und eine
     * Zeile in der Ablage waere eine Zustimmung, die niemand verlangt hat. Der Aufrufer
     * fragt vorher {@link #an()} - der Befehl tut das, und die Wache zeigt ohne
     * {@link #verlangt} gar kein Formular.
     *
     * @return {@code true}, wenn sich dadurch etwas geaendert hat; {@code false}, wenn er
     *         genau diese Fassung schon angenommen hatte
     */
    public boolean nimmAn(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts anzunehmen");
        }
        if (!an()) {
            throw new IllegalStateException("Die Anmeldung ist aus ("
                    + Anmeldewerte.PFAD_AN + ": false) - dann gibt es nichts"
                    + " anzunehmen. Wer hierher kommt, hat an() nicht gefragt.");
        }
        return ablage.nimmAn(new Anmeldeablage.Antrag(spieler, fassung(), uhr.instant()));
    }

    /**
     * Was dieser Spieler angenommen hat - fuer die Auskunft, nicht fuer den Riegel.
     *
     * <p>Der Riegel fragt {@link #verlangt}. Diese Methode gibt den ganzen Stand, damit
     * ein Betreiberbefehl eines Tages Fassung und Zeitpunkt nennen kann.
     */
    public Anmeldestand stand(UUID spieler) {
        return ablage.lies(spieler);
    }
}
