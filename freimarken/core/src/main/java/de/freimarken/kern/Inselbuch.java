package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Regel ueber die Meerinseln von Hafenwerder - wer eine nehmen darf und wer darauf
 * bauen darf.
 *
 * <p><b>Das DRITTE Buch</b> neben {@link Besitzbuch} und {@link Freistadtbuch}, und aus
 * demselben Grund, aus dem jene getrennt sind: Jedes haelt seine Geometrie als FELD. Das
 * Besitzbuch sieht aus, als koenne es mehrere Welten - es kann es nicht, denn {@code welt}
 * ist nur ein Parameter, waehrend Grenze und Plan fest verdrahtet sind. Wer das Nehmen
 * einer Insel durch das Besitzbuch fuehrte, bekaeme Marktstadt-Geometrie auf das Meer.
 *
 * <p><b>Viel kleiner als das Freistadtbuch</b>, und das ist keine Sparsamkeit, sondern die
 * Natur der Sache: Eine Insel hat keinen Namen, keine Kasse, keine Buerger, keine Regeln,
 * keine Auflage und keinen Verfall. Sie ist ein Stueck Besitz mit einem Festpreis - naeher
 * an einer Parzelle als an einer Stadt. Darum ist auch das Nehmen wie dort: <b>Wer eine
 * Insel will, steht darauf.</b> Eine Zuteilung durch das Buch (wie bei den Freistaedten)
 * braucht es nur, wo man nicht hingehen kann; nach Hafenwerder faehrt man mit dem Boot,
 * und genau das soll der Reiz sein (Plan 4.1: Bootsrennen, Handelsrouten).
 *
 * <p>Wie das Freistadtbuch wirft dieses nie nach aussen: Eine stumme Ablage ergibt
 * {@link Bescheid#GESTOERT} und keine Ausnahme im Befehl.
 */
public final class Inselbuch {

    /** Ausgang eines Versuchs. */
    public enum Bescheid {
        ERTEILT,
        /** Offenes Meer - hier ist gar kein Inselfeld. */
        KEIN_INSELFELD,
        /** Feld 0/0, die Ankunftsinsel. Sie gehoert niemandem und bleibt es. */
        ANKUNFTSINSEL,
        /** Die Insel gehoert schon jemandem. */
        SCHON_VERGEBEN,
        /** Jenseits der Weltgrenze - nicht zu verwechseln mit GRENZE_ERREICHT. */
        AUSSERHALB,
        /** Die Rangobergrenze ist erreicht. */
        GRENZE_ERREICHT,
        /** Das Guthaben reicht nicht. */
        ZU_WENIG_MARK,
        /** Die Ablage antwortet nicht. */
        GESTOERT
    }

    /**
     * Antwort auf einen Versuch.
     *
     * @param bescheid was herausgekommen ist
     * @param kosten   was es gekostet hat bzw. gekostet haette, in Mark
     * @param insel    die betroffene Insel, falls es eine gibt
     */
    public record Entscheid(Bescheid bescheid, long kosten, Optional<Parzelle> insel) {

        public boolean erteilt() {
            return bescheid == Bescheid.ERTEILT;
        }

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, 0L, Optional.empty());
        }

        static Entscheid nein(Bescheid grund, long kosten) {
            return new Entscheid(grund, kosten, Optional.empty());
        }
    }

    private final Inselgrenze grenze;
    private final Inselplan plan;
    private final BesitzLager lager;
    private final Kasse kasse;

    /**
     * Woher der Preis kommt.
     *
     * <p>Das Feld darf {@code final} bleiben, weil nicht der WERT eingefroren wird,
     * sondern der Zugang zu ihm: Der Preis wird genau in dem Augenblick geholt, in dem
     * abgebucht wird. Damit ist der Preis einer Buchung immer der Preis, der galt.
     */
    private final Wirtschaftsstand wirtschaft;

    public Inselbuch(Inselgrenze grenze, BesitzLager lager, Kasse kasse,
                     Wirtschaftsstand wirtschaft) {
        if (grenze == null) {
            throw new IllegalArgumentException("Ohne Inselgrenze gibt es keine Welt, in"
                    + " der eine Insel liegen koennte");
        }
        this.grenze = grenze;
        // Der Plan kommt AUS DER GRENZE und wird nicht daneben hereingegeben: Zwei
        // Geometrien fuer eine Welt sind die Lage, in der die Grenze ein Feld erlaubt,
        // das der Plan nicht kennt.
        this.plan = grenze.plan();
        this.lager = lager;
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
    }

    /** Wie weit Hafenwerder reicht. */
    public Inselgrenze grenze() {
        return grenze;
    }

    /** Die Geometrie - dieselbe, die der Weltgenerator benutzt. */
    public Inselplan plan() {
        return plan;
    }

    /** Was eine Insel JETZT kostet. Beim Gebrauch zu fragen, nicht zu merken. */
    public long preis() {
        return wirtschaft.werte().inseln().preis();
    }

    /** Wie viele Inseln ein Spieler ohne besonderes Recht halten darf. */
    public int hoechstzahl() {
        return wirtschaft.werte().inseln().hoechstzahl();
    }

    /**
     * Eine Meerinsel nehmen - die, auf der der Spieler STEHT.
     *
     * <p>Bei Erfolg ist der Preis bereits abgebucht. Bei jedem anderen Bescheid ist nichts
     * passiert - auch nicht an der Kasse.
     *
     * <p>Die Reihenfolge der Riegel ist die KOSTENREIHENFOLGE, wie im Besitzbuch: erst
     * rechnen, dann in der Ablage nachsehen, zuletzt Geld anfassen. Ein Nachsehen kostet
     * eine Abfrage, eine Abbuchung eine Schreiboperation - und eine Abbuchung, die
     * zurueckgenommen werden muss, zwei plus eine Zeile im Kontobuch, die der Spieler
     * sieht.
     *
     * @param welt        Weltschluessel von Hafenwerder
     * @param spieler     wer die Insel haben will
     * @param weltX       Weltkoordinate, an der er steht
     * @param weltZ       Weltkoordinate, an der er steht
     * @param hoechstzahl wie viele Inseln sein Rang erlaubt
     */
    public Entscheid nehmen(String welt, UUID spieler, int weltX, int weltZ,
                            int hoechstzahl) {
        if (plan.feldBei(weltX, weltZ) != Inselplan.Feld.INSELFELD) {
            return Entscheid.nein(Bescheid.KEIN_INSELFELD);
        }
        int feldX = plan.feldNummer(weltX);
        int feldZ = plan.feldNummer(weltZ);
        if (plan.istAnkunftsinsel(feldX, feldZ)) {
            // Vor dem Nachsehen in der Ablage, und zwar weil die Antwort feststeht: Die
            // Ankunftsinsel ist nie vergeben, also saehe der Spieler sonst
            // "noch frei" - eine Einladung, die nie angenommen werden kann.
            return Entscheid.nein(Bescheid.ANKUNFTSINSEL);
        }
        if (!grenze.enthaeltFeld(feldX, feldZ)) {
            return Entscheid.nein(Bescheid.AUSSERHALB);
        }
        try {
            Optional<Parzelle> vorhanden = lager.finde(welt, feldX, feldZ);
            if (vorhanden.isPresent()) {
                return new Entscheid(Bescheid.SCHON_VERGEBEN, 0L, vorhanden);
            }
            int bereits = lager.anzahlVon(welt, spieler);
            if (bereits >= hoechstzahl) {
                return Entscheid.nein(Bescheid.GRENZE_ERREICHT);
            }
            long preis = preis();
            if (!kasse.abbuchen(spieler, preis, Buchungsgrund.WERDER_GEKAUFT)) {
                return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis);
            }
            try {
                return new Entscheid(Bescheid.ERTEILT, preis, Optional.of(
                        lager.werderAnlegen(welt, feldX, feldZ, spieler, preis)));
            } catch (RuntimeException fehler) {
                // Das Geld ist weg, die Insel nicht gekommen - also zurueck damit. Der
                // Fall ist nicht theoretisch: Nehmen zwei Spieler im selben Augenblick
                // dieselbe Insel, laesst der eindeutige Schluessel in der Datenbank den
                // zweiten auflaufen. Ohne diese Gegenbuchung zahlte er 75.000 Mark fuer
                // nichts.
                kasse.gutschreiben(spieler, preis, Buchungsgrund.WERDER_ERSTATTET);
                return Entscheid.nein(Bescheid.GESTOERT, preis);
            }
        } catch (RuntimeException fehler) {
            // VOR der Abbuchung: Hier ist nichts passiert, auch nicht an der Kasse.
            return Entscheid.nein(Bescheid.GESTOERT);
        }
    }

    /**
     * Die Insel an dieser Stelle, falls eine vergeben ist.
     *
     * <p>Im offenen Meer leer, und das ist mehr als eine Abkuerzung: Ohne diesen Riegel
     * naennte {@link Inselplan#feldNummer} auch mitten im Meer ein Feld - das davor -,
     * und die Antwort waere die Insel des Nachbarn.
     */
    public Optional<Parzelle> inselAn(String welt, int weltX, int weltZ) {
        if (plan.feldBei(weltX, weltZ) != Inselplan.Feld.INSELFELD) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.feldNummer(weltX), plan.feldNummer(weltZ));
    }

    /** Alle Inseln dieses Spielers, nach Lage sortiert. */
    public List<Parzelle> inselnVon(String welt, UUID spieler) {
        return lager.vonSpieler(welt, spieler);
    }

    /** Wie viele Inseln dieser Spieler schon hat. */
    public int anzahlVon(String welt, UUID spieler) {
        return lager.anzahlVon(welt, spieler);
    }

    /**
     * Ob hier gebaut werden darf.
     *
     * <p>Drei Faelle, und nur einer davon ist ein Ja: Im offenen Meer darf niemand bauen -
     * es ist das oeffentliche Wasser des Archipels. Auf einem Inselfeld, das niemandem
     * gehoert, auch nicht; dazu gehoert die ANKUNFTSINSEL, und zwar ohne eigenen Zweig,
     * denn sie ist nie vergeben. Und auf einer vergebenen Insel entscheidet die Zeile
     * selbst - Eigentuemer und Mitbauer.
     *
     * <p><b>Ein {@code boolean} und kein Urteil mit Gruenden</b>, anders als beim
     * Freistadtbuch: Dort gibt es Stadtbuerger mit abgestuften Rechten und darum vier
     * Antworten. Hier gibt es Eigentuemer, Mitbauer und sonst niemanden.
     *
     * @param spieler wer handelt, oder {@code null} fuer die Frage "liegt hier ueberhaupt
     *                Besitz" - dann ist die Antwort immer nein
     */
    public boolean darfBauen(String welt, UUID spieler, int weltX, int weltZ) {
        return inselAn(welt, weltX, weltZ)
                .map(insel -> insel.darfBauen(spieler))
                .orElse(false);
    }
}
