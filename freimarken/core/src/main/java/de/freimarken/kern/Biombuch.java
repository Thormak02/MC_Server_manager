package de.freimarken.kern;

import java.util.Optional;
import java.util.UUID;

/**
 * Die Regel ueber den Biomwechsel einer Wolkeninsel (Teil F).
 *
 * <p><b>Das Biom ist nicht nur eine Farbe.</b> Es entscheidet, WAS erscheint und WAS
 * waechst: {@code swamp} gibt Schleim und damit Kolben, {@code snowy_plains} gibt Eis und
 * damit Wasser, {@code mushroom_fields} laesst kein Monster erscheinen, {@code desert}
 * laesst es nie regnen. In einer Welt aus Leere ist das die zweite grosse Entscheidung
 * eines Spielers - nach der Insel selbst.
 *
 * <p><b>Jeder Wechsel kostet, auch der auf dasselbe Biom.</b> Das ist die ausdrueckliche
 * Vorgabe und kein Versehen: Hinter dem Wechsel steht das Umsetzen der Biomzellen eines
 * ganzen Feldes, und das ist Arbeit, die der Server jedes Mal leistet. Ein Riegel "du hast
 * dieses Biom schon" waere ausserdem eine Falle - nach einem abgebrochenen Umsetzen kaeme
 * man nie wieder an die Umsetzung heran.
 *
 * <p><b>Nur der EIGENTUEMER</b>, anders als beim Abgeben einer Herausforderung: Ein
 * Biomwechsel kostet Geld und aendert die ganze Insel, nicht nur ihre Zeile. Ein Mitbauer
 * duerfte damit das Spiel eines anderen umstellen - und zwar auf dessen Rechnung.
 */
public final class Biombuch {

    /** Was aus dem Wunsch wird, das Biom zu wechseln. */
    public enum Bescheid {

        /** Gewechselt: Das Geld ist weg, die Ablage traegt das neue Biom. */
        ERTEILT,

        /** In dieser Welt gibt es keine Biomwahl - die Liste ist leer. */
        KEINE_WAHL,

        /** Dieses Biom steht nicht in {@code wolken.biome}. */
        NICHT_ANGEBOTEN,

        /** Hier ist keine Insel. */
        KEINE_INSEL,

        /** Die Insel gehoert einem anderen. */
        NICHT_EIGENTUEMER,

        /** Das Guthaben reicht nicht. */
        ZU_WENIG_MARK,

        /** Die Ablage hat geschwiegen - der Preis ist zurueck. */
        GESTOERT
    }

    /**
     * @param kosten was es gekostet hat (bei {@link Bescheid#ERTEILT}) oder kosten
     *               WUERDE (bei {@link Bescheid#ZU_WENIG_MARK})
     * @param biom   das gewuenschte Biom, kleingeschrieben wie abgelegt
     */
    public record Entscheid(Bescheid bescheid, Optional<Parzelle> insel, long kosten,
                            String biom) {

        static Entscheid nein(Bescheid bescheid, String biom) {
            return new Entscheid(bescheid, Optional.empty(), 0L, biom);
        }

        public boolean erteilt() {
            return bescheid == Bescheid.ERTEILT;
        }
    }

    private final Wolkenbuch buch;
    private final Wolkenablage wolken;
    private final Kasse kasse;
    private final Wirtschaftsstand wirtschaft;

    public Biombuch(Wolkenbuch buch, Wolkenablage wolken, Kasse kasse,
                    Wirtschaftsstand wirtschaft) {
        if (buch == null || wolken == null || kasse == null || wirtschaft == null) {
            throw new IllegalArgumentException("Ohne Wolkenbuch, Inselstaende, Kasse und"
                    + " Wirtschaftsstand gibt es keinen Biomwechsel");
        }
        this.buch = buch;
        this.wolken = wolken;
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
    }

    /** Was ein Wechsel JETZT kostet. Beim Gebrauch zu fragen, nicht zu merken. */
    public long preis() {
        return wirtschaft.werte().wolken().biompreis();
    }

    /** Welche Biome JETZT angeboten werden. */
    public java.util.List<String> angebot() {
        return wirtschaft.werte().wolken().biome();
    }

    /**
     * Setzt das Biom der Insel an diesem Ort.
     *
     * <p>Die Reihenfolge ist die KOSTENREIHENFOLGE wie im Wolkenbuch: erst rechnen, dann
     * nachsehen, zuletzt Geld anfassen. Und das Schreiben kommt NACH dem Abbuchen, weil
     * nur so die Erstattung eindeutig ist: Schweigt die Ablage, kommt der Preis zurueck -
     * schwieg die Kasse, ist nichts passiert.
     *
     * <p>Die BLOECKE setzt diese Regel nicht; das tut die Hand im Plugin. Bei
     * {@link Bescheid#ERTEILT} ist das Biom gewaehlt und bezahlt - sichtbar wird es, wenn
     * die Biomzellen umgesetzt sind.
     */
    public Entscheid setze(String welt, UUID spieler, int weltX, int weltZ, String biom) {
        String gewuenscht = Wolkenbiome.wieAbgelegt(biom);
        if (!wirtschaft.werte().wolken().biomwahl()) {
            return Entscheid.nein(Bescheid.KEINE_WAHL, gewuenscht);
        }
        if (!wirtschaft.werte().wolken().bietetBiom(gewuenscht)) {
            return Entscheid.nein(Bescheid.NICHT_ANGEBOTEN, gewuenscht);
        }
        long kosten = preis();
        try {
            Optional<Parzelle> gefunden = buch.inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Entscheid.nein(Bescheid.KEINE_INSEL, gewuenscht);
            }
            Parzelle insel = gefunden.get();
            if (!insel.gehoert(spieler)) {
                return new Entscheid(Bescheid.NICHT_EIGENTUEMER, gefunden, kosten,
                        gewuenscht);
            }
            if (!kasse.abbuchen(spieler, kosten, Buchungsgrund.WOLKE_BIOM)) {
                return new Entscheid(Bescheid.ZU_WENIG_MARK, gefunden, kosten,
                        gewuenscht);
            }
            try {
                wolken.biomSetzen(insel.id(), gewuenscht);
            } catch (RuntimeException fehler) {
                // Der Preis kommt zurueck, und zwar mit eigenem Grund: Zwei Zeilen im
                // Kontobuch sind besser als ein Spieler, der fuer nichts bezahlt hat.
                kasse.gutschreiben(spieler, kosten,
                        Buchungsgrund.WOLKE_BIOM_ERSTATTET);
                return new Entscheid(Bescheid.GESTOERT, gefunden, 0L, gewuenscht);
            }
            return new Entscheid(Bescheid.ERTEILT, gefunden, kosten, gewuenscht);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, gewuenscht);
        }
    }

    /** Welches Biom diese Insel traegt - oder die Vorgabe, wenn keines gewaehlt ist. */
    public String biomVon(long inselId) {
        Inselstand stand = wolken.stand(inselId);
        return stand.hatBiom() ? stand.biom() : Wolkenbiome.VORGABE;
    }
}
