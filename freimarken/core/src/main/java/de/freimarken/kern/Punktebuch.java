package de.freimarken.kern;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Die Regel ueber die Punktzahl einer Insel und die Rangliste (Teil E).
 *
 * <p><b>Die Punktzahl entscheidet nichts.</b> Sie steht in der Rangliste, und das ist
 * alles - die Erzausbeute haengt an der STUFE und damit an den Herausforderungen. Eine
 * Rangliste, die die Ausbeute aendert, waere ein Grund, Bloecke zu stapeln, statt zu
 * spielen.
 *
 * <p><b>Das Zaehlen selbst steht nicht hier</b>, sondern in {@link Zaehlauftrag} und in
 * der Hand, die ihn taktet: Welche Bloecke in einem Chunk liegen, kann nur der Server
 * sagen. Dieses Buch entscheidet, OB gezaehlt wird (Abklingzeit, laufender Auftrag, gibt
 * es die Insel), und es schreibt das Ergebnis.
 *
 * <p><b>Die Menge der laufenden Auftraege gehoert dem Buch</b> und nicht der Hand: Sonst
 * haette jeder Taktgeber seine eigene, und zwei Zaehlungen auf derselben Insel liefen
 * nebeneinander - mit zwei Teilsummen, von denen die zweite die erste ueberschreibt.
 */
public final class Punktebuch {

    /** Was aus dem Wunsch wird, eine Insel zu zaehlen. */
    public enum Bescheid {

        /** Die Zaehlung laeuft - der Auftrag steht im Entscheid. */
        ANGEFANGEN,

        /** Es gibt keine Punktetabelle. Dann waere jede Insel 0 wert. */
        AUS,

        /** Hier ist keine Insel - in der Leere oder auf einem freien Feld. */
        KEINE_INSEL,

        /** Die letzte Zaehlung ist zu kurz her - die Wartezeit steht im Entscheid. */
        ZU_FRUEH,

        /** Auf dieser Insel zaehlt schon jemand. */
        LAEUFT_SCHON,

        /** Die Ablage hat geschwiegen. */
        GESTOERT
    }

    /**
     * @param wartesekunden bei {@link Bescheid#ZU_FRUEH}, wie lange noch
     * @param auftrag       bei {@link Bescheid#ANGEFANGEN}, was zu zaehlen ist
     */
    public record Entscheid(Bescheid bescheid, Optional<Parzelle> insel,
                            long wartesekunden, Optional<Zaehlauftrag> auftrag) {

        static Entscheid nein(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), 0L, Optional.empty());
        }

        public boolean angefangen() {
            return bescheid == Bescheid.ANGEFANGEN;
        }
    }

    /**
     * Ein Platz der Rangliste.
     *
     * @param rang   ab 1, in der Reihenfolge der Liste
     * @param insel  die Insel mit ihrem Eigentuemer und ihrem Feld
     * @param punkte ihre Punktzahl
     * @param stufe  ihre Insel-Stufe - sie steht daneben, denn sie ist die andere
     *               Haelfte des Fortschritts
     * @param gezaehltAm wann gezaehlt wurde, oder {@code null} fuer "noch nie"
     */
    public record Platz(int rang, Parzelle insel, long punkte, int stufe,
                        Instant gezaehltAm) {
    }

    private final Wolkenbuch buch;
    private final Wolkenablage wolken;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;

    /** Die Inseln, auf denen gerade gezaehlt wird - siehe Klassenkopf. */
    private final Set<Long> laufende = new LinkedHashSet<>();

    public Punktebuch(Wolkenbuch buch, Wolkenablage wolken, Wirtschaftsstand wirtschaft,
                      Clock uhr) {
        if (buch == null || wolken == null || wirtschaft == null || uhr == null) {
            throw new IllegalArgumentException("Ohne Wolkenbuch, Inselstaende,"
                    + " Wirtschaftsstand und Uhr gibt es keine Punkte");
        }
        this.buch = buch;
        this.wolken = wolken;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
    }

    /** Was JETZT in der Punktetabelle steht. Beim Gebrauch zu fragen. */
    public Punktwerte werte() {
        return wirtschaft.werte().wolken().punkte();
    }

    /**
     * Beginnt eine Zaehlung auf der Insel an diesem Ort.
     *
     * <p>Bei {@link Bescheid#ANGEFANGEN} ist die Insel als laufend vermerkt, und der
     * Aufrufer MUSS den Auftrag zu Ende bringen oder {@link #abbrechen} rufen - sonst
     * bleibt sie es bis zum naechsten Neustart.
     */
    public synchronized Entscheid beginne(String welt, int weltX, int weltZ) {
        if (!werte().greift()) {
            return Entscheid.nein(Bescheid.AUS);
        }
        try {
            Optional<Parzelle> gefunden = buch.inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Entscheid.nein(Bescheid.KEINE_INSEL);
            }
            Parzelle insel = gefunden.get();
            if (laufende.contains(insel.id())) {
                return new Entscheid(Bescheid.LAEUFT_SCHON, gefunden, 0L,
                        Optional.empty());
            }
            long warte = wartesekunden(insel.id());
            if (warte > 0L) {
                return new Entscheid(Bescheid.ZU_FRUEH, gefunden, warte,
                        Optional.empty());
            }
            laufende.add(insel.id());
            return new Entscheid(Bescheid.ANGEFANGEN, gefunden, 0L,
                    Optional.of(new Zaehlauftrag(insel.id(), buch.plan(), insel.x(),
                            insel.z())));
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
    }

    /**
     * Wie lange diese Insel noch warten muss - 0 heisst: jetzt.
     *
     * <p>Die Abklingzeit haengt an der INSEL und nicht am Spieler, und das ist die Sache
     * selbst: Gezaehlt wird ein Feld, und zehn Mitbauer koennten es sonst zehnmal
     * hintereinander zaehlen lassen. Jede Zaehlung liest sechs Millionen Bloecke.
     */
    public long wartesekunden(long inselId) {
        Instant zuletzt = wolken.stand(inselId).gezaehltAm();
        if (zuletzt == null) {
            return 0L;
        }
        long offen = werte().abklingSekunden()
                - Duration.between(zuletzt, uhr.instant()).toSeconds();
        return Math.max(0L, offen);
    }

    /**
     * Schreibt das Ergebnis einer fertigen Zaehlung.
     *
     * <p>Die Punkte werden HIER gerechnet und nicht vom Aufrufer mitgebracht: Die Tabelle
     * ist neuladbar, und der Augenblick des Schreibens ist der, in dem sie gilt.
     *
     * @return die geschriebene Punktzahl
     */
    public synchronized long fertig(Zaehlauftrag auftrag) {
        if (auftrag == null) {
            throw new IllegalArgumentException("Ohne Auftrag gibt es nichts zu"
                    + " schreiben");
        }
        long punkte = auftrag.punkte(werte());
        try {
            wolken.punkteSetzen(auftrag.inselId(), punkte, uhr.instant());
        } finally {
            // Im finally: Wirft die Ablage, bleibt die Insel sonst fuer immer als
            // laufend vermerkt - und niemand koennte sie je wieder zaehlen lassen.
            laufende.remove(auftrag.inselId());
        }
        return punkte;
    }

    /** Gibt eine Insel frei, ohne zu schreiben - fuer eine abgebrochene Zaehlung. */
    public synchronized void abbrechen(Zaehlauftrag auftrag) {
        if (auftrag != null) {
            laufende.remove(auftrag.inselId());
        }
    }

    /** Ob auf dieser Insel gerade gezaehlt wird - fuer den Startbericht und Proben. */
    public synchronized boolean laeuft(long inselId) {
        return laufende.contains(inselId);
    }

    /**
     * Die Rangliste - die Inseln mit den meisten Punkten.
     *
     * <p><b>Zusammengesetzt in Java und nicht in SQL</b>, aus demselben Grund, den
     * {@code MariaWolken.alle} nennt: Die Inseln stehen in {@code parzellen}, die Punkte
     * in {@code wolkeninsel}, und eine Welt hat hoechstens 1.443 Felder. Das sind zwei
     * Abfragen und eine Handvoll Kilobytes - ein Verbund in SQL waere eine zweite Stelle,
     * an der die Punktzahl einer Insel zustande kommt.
     *
     * <p>Sortiert nach Punkten, bei Gleichstand nach der INSELNUMMER - also nach dem
     * Alter. Ohne den zweiten Schluessel waere die Reihenfolge bei zwei Inseln mit 0
     * Punkten dem Zufall der Ablage ueberlassen, und die Liste saehe bei jedem Aufruf
     * anders aus.
     *
     * @param hoechstens wie viele Plaetze - mindestens einer
     */
    public List<Platz> rangliste(String welt, int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Rangliste mit " + hoechstens
                    + " Plaetzen ist keine");
        }
        List<Parzelle> inseln = buch.inselnIn(welt);
        List<Platz> plaetze = new ArrayList<>();
        for (Parzelle insel : inseln) {
            Inselstand stand = wolken.stand(insel.id());
            plaetze.add(new Platz(0, insel, stand.punkte(), stand.stufe(),
                    stand.gezaehltAm()));
        }
        plaetze.sort(Comparator.comparingLong(Platz::punkte).reversed()
                .thenComparingLong(platz -> platz.insel().id()));
        List<Platz> mitRang = new ArrayList<>();
        for (int i = 0; i < plaetze.size() && i < hoechstens; i++) {
            Platz ohne = plaetze.get(i);
            mitRang.add(new Platz(i + 1, ohne.insel(), ohne.punkte(), ohne.stufe(),
                    ohne.gezaehltAm()));
        }
        return List.copyOf(mitRang);
    }
}
