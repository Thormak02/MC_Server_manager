package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Besitzlager, das sich beim Fragen beobachten laesst.
 *
 * <p><b>Warum es diese Klasse gibt:</b> {@link SpeicherLager} ist {@code final}, und das
 * ist richtig so - es ist eine Umsetzung des Vertrags und keine Grundlage zum
 * Weiterbauen. Wer trotzdem EINE Methode anders beantworten will, braucht eine eigene
 * Umsetzung mit allen zwoelf Methoden. Die steht hier EINMAL, statt in jeder Probe neu.
 *
 * <p>Sie liegt in {@code testFixtures} und nicht in einer Probe, weil sie aus zwei
 * Modulen gebraucht wird: {@code FreistadtbuchTest} im Kern zaehlt mit ihr die Gaenge
 * zur Ablage auf dem heissen Pfad, {@code StadtwacheTest} im Paper-Modul laesst sie
 * werfen. Zwei Abschriften derselben zwoelf Methoden liefen beim naechsten Zuwachs des
 * Vertrags auseinander - und dann prueft eine der beiden Proben etwas anderes als die
 * andere.
 *
 * <p><b>Alles wird durchgereicht</b>, auch {@link #finde}: Wer etwas anderes will,
 * ueberschreibt genau das und ruft {@link #gezaehlt()} oder wirft. Eine Attrappe, die
 * von sich aus luegt, waere eine zweite Wahrheit ueber den Vertrag.
 */
public class Lagerattrappe implements BesitzLager {

    private final SpeicherLager echtes = new SpeicherLager();

    /** Wie oft {@link #finde} gefragt wurde - die Zahl ist die ganze Zusage. */
    private int gefragt;

    /** Wie oft {@link #finde} seit dem Bauen gefragt wurde. */
    public int gezaehlt() {
        return gefragt;
    }

    @Override
    public Optional<Parzelle> finde(String welt, int x, int z) {
        gefragt++;
        return echtes.finde(welt, x, z);
    }

    @Override
    public List<Parzelle> vonSpieler(String welt, UUID spieler) {
        return echtes.vonSpieler(welt, spieler);
    }

    @Override
    public int anzahlVon(String welt, UUID spieler) {
        return echtes.anzahlVon(welt, spieler);
    }

    @Override
    public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
        return echtes.anlegen(welt, x, z, eigentuemer, preis);
    }

    @Override
    public Parzelle freistadtAnlegen(String welt, int feldX, int feldZ, UUID eigentuemer,
                                     long preis, String name) {
        return echtes.freistadtAnlegen(welt, feldX, feldZ, eigentuemer, preis, name);
    }

    @Override
    public List<Parzelle> staedteIn(String welt) {
        return echtes.staedteIn(welt);
    }

    @Override
    public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        echtes.mitbauerSetzen(id, mitbauer);
    }

    @Override
    public void entfernen(long id) {
        echtes.entfernen(id);
    }

    @Override
    public List<Parzelle> kinderVon(long gruppeId) {
        return echtes.kinderVon(gruppeId);
    }

    @Override
    public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                             Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
        return echtes.gruppeSetzen(gruppeId, welt, eigentuemer, mitbauer, gebuehr,
                felder);
    }

    @Override
    public void auftrennen(long gruppeId) {
        echtes.auftrennen(gruppeId);
    }

    @Override
    public void ausGruppeLoesen(long feldId) {
        echtes.ausGruppeLoesen(feldId);
    }

    /**
     * Ein Lager, das auf die ORTSFRAGE mit einem Wurf antwortet - tote Datenbank.
     *
     * <p>Nur {@link #finde} wirft, und das ist Absicht: Genau diese Frage laeuft auf dem
     * heissen Pfad, und genau sie muss jede Wache gefangen haben. Wuerde alles werfen,
     * liesse sich nicht mehr unterscheiden, ob eine Probe die Wache prueft oder ihren
     * eigenen Aufbau.
     */
    public static final class Stumm extends Lagerattrappe {
        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Die Ablage antwortet nicht");
        }
    }
}
