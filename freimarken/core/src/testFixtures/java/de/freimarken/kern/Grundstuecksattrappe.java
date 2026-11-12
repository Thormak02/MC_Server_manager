package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Eine Grundstuecksablage, die sich beim Fragen beobachten laesst.
 *
 * <p>Das Geschwister von {@link Lagerattrappe}, und es gibt sie aus demselben Grund:
 * {@link SpeicherGrundstuecke} ist {@code final}, also braucht jede Probe, die EINE
 * Methode anders beantworten will, alle elf. Die stehen hier EINMAL.
 *
 * <p>Sie liegt in {@code testFixtures}, weil zwei Proben sie brauchen - und das war
 * schon so, bevor diese Klasse entstand: {@code FreistadtbuchTest} und
 * {@code ZwischenspeicherGrundstueckeTest} hatten je eine eigene Abschrift, und als
 * Teil C3 drei Methoden zur Schnittstelle hinzufuegte, mussten BEIDE nachgezogen
 * werden. Genau das ist der Fall, gegen den eine gemeinsame Attrappe gebaut ist.
 *
 * <p><b>Alles wird durchgereicht</b>, auch {@link #anZelle}. Wer etwas anderes will,
 * ueberschreibt genau das; wer nur zaehlen will, liest {@link #gezaehlt()}.
 */
public class Grundstuecksattrappe implements Grundstuecksablage {

    /** Die echte Ablage darunter - fuer Unterklassen, die sie brauchen. */
    protected final SpeicherGrundstuecke echte = new SpeicherGrundstuecke();

    private int gefragt;

    /** Wie oft {@link #anZelle} gefragt wurde - die Zahl ist die ganze Zusage. */
    public int gezaehlt() {
        return gefragt;
    }

    /** Den Zaehler auf null stellen - nach dem Aufbau einer Probe. */
    public void zaehlerZuruecksetzen() {
        gefragt = 0;
    }

    @Override
    public Optional<Grundstueck> anZelle(String welt, int zelleX, int zelleZ) {
        gefragt++;
        return echte.anZelle(welt, zelleX, zelleZ);
    }

    @Override
    public Grundstueck anlegen(String welt, long stadtId, Zellquader flaeche,
                               UUID eigentuemer, long preis) {
        return echte.anlegen(welt, stadtId, flaeche, eigentuemer, preis);
    }

    @Override
    public List<Grundstueck> inStadt(long stadtId) {
        return echte.inStadt(stadtId);
    }

    @Override
    public List<Grundstueck> alle(String welt) {
        return echte.alle(welt);
    }

    @Override
    public List<Grundstueck> vonSpieler(String welt, UUID spieler) {
        return echte.vonSpieler(welt, spieler);
    }

    @Override
    public boolean besitztIn(String welt, long stadtId, UUID spieler) {
        return echte.besitztIn(welt, stadtId, spieler);
    }

    @Override
    public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        echte.mitbauerSetzen(id, mitbauer);
    }

    @Override
    public void entfernen(long id) {
        echte.entfernen(id);
    }

    @Override
    public void anbieten(long id, Long angebotspreis) {
        echte.anbieten(id, angebotspreis);
    }

    @Override
    public Grundstueck eigentuemerSetzen(long id, UUID neuerEigentuemer, long preis) {
        return echte.eigentuemerSetzen(id, neuerEigentuemer, preis);
    }

    @Override
    public List<Grundstueck> angebote(String welt) {
        return echte.angebote(welt);
    }

    /**
     * Eine Ablage, die beim BESITZERWECHSEL wirft - fuer den schlimmsten Fall des Kaufs.
     *
     * <p>Genau dieser Schritt, und kein anderer: Er ist der Punkt, an dem das Geld des
     * Kaeufers schon weg ist. Dass es dann zurueckkommt, ist die Zusage, die nur mit
     * dieser Attrappe zu pruefen ist.
     */
    public static final class StummBeimWechsel extends Grundstuecksattrappe {
        @Override
        public Grundstueck eigentuemerSetzen(long id, UUID neuerEigentuemer,
                                             long preis) {
            throw new IllegalStateException("Die Ablage antwortet nicht");
        }
    }
}
