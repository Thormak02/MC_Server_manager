package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Eine Kasse, die sich beim Buchen beobachten laesst.
 *
 * <p>Die dritte dieser Art neben {@link Lagerattrappe} und
 * {@link Grundstuecksattrappe}, und es gibt sie aus demselben Grund:
 * {@link SpeicherKasse} ist {@code final}, also braucht jede Probe, die EINE Buchung
 * anders beantworten will, alle sechs Methoden.
 *
 * <p><b>Was nur mit ihr zu pruefen ist:</b> der schlimmste Fall eines Kaufs zwischen
 * zwei Spielern. Das Geld des Kaeufers ist dann abgebucht, das Grundstueck uebertragen -
 * und die Gutschrift an den Verkaeufer scheitert. Ohne eine Kasse, die genau dort wirft,
 * waere diese Stelle nur im Javadoc beschrieben und von nichts gehalten.
 */
public class Kassenattrappe implements Kasse {

    /** Die echte Kasse darunter - fuer Unterklassen, die sie brauchen. */
    protected final SpeicherKasse echte;

    public Kassenattrappe(long startguthaben) {
        this.echte = new SpeicherKasse(startguthaben);
    }

    @Override
    public long guthaben(UUID spieler) {
        return echte.guthaben(spieler);
    }

    @Override
    public boolean eroeffne(UUID spieler) {
        return echte.eroeffne(spieler);
    }

    @Override
    public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
        return echte.abbuchen(spieler, betrag, grund);
    }

    @Override
    public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
        echte.gutschreiben(spieler, betrag, grund);
    }

    @Override
    public List<Buchung> verlauf(UUID spieler, int hoechstens) {
        return echte.verlauf(spieler, hoechstens);
    }

    @Override
    public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
        return echte.summeSeit(spieler, ab, grund);
    }

    /**
     * Eine Kasse, die beim GUTSCHREIBEN wirft.
     *
     * <p>Genau diese Buchung und keine andere: Sie ist Schritt 3 eines Kaufs, und damit
     * die Stelle, an der das Geld des Kaeufers schon weg und das Grundstueck schon
     * uebertragen ist. Dass der Kauf dann GILT und der Verlust gemeldet wird, ist die
     * Zusage, die nur hiermit zu pruefen ist.
     */
    public static final class StummBeimGutschreiben extends Kassenattrappe {
        public StummBeimGutschreiben(long startguthaben) {
            super(startguthaben);
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            throw new IllegalStateException("Die Kasse antwortet nicht");
        }
    }
}
