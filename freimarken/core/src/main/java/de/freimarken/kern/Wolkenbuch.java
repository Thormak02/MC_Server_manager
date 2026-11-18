package de.freimarken.kern;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wer welche Wolkeninsel hat, und wer eine bekommt.
 *
 * <p>Das vierte Buch neben {@link Besitzbuch}, {@link Freistadtbuch} und
 * {@link Inselbuch}, und es ist das kleinste von allen: Eine Wolkeninsel hat keinen
 * Namen, keine Kasse, keine Buerger und keine Auflage. Sie hat ein Feld und einen
 * Eigentuemer.
 *
 * <p><b>Der eine Unterschied zum Inselbuch, und er bestimmt den ganzen Ablauf</b>: In
 * Hafenwerder nimmt man die Insel, auf der man STEHT - man kann hinsegeln und sich
 * ansehen, was man kauft. Hier gibt es nichts, worauf man stehen koennte: Die Welt ist
 * Leere, und die Insel entsteht erst in dem Augenblick, in dem sie jemand nimmt. Darum
 * SUCHT dieses Buch das Feld, und zwar wie das Freistadtbuch - ringweise von der Mitte
 * nach aussen ({@link Feldfenster#freiesFeld}).
 *
 * <p><b>Das Aufsetzen gehoert nicht hierher.</b> Dieses Buch schreibt die Zeile und
 * bucht ab; die Bloecke setzt danach das Plugin. Der Grund ist derselbe wie ueberall im
 * Kern: Hier liegt keine Minecraft-Klasse, und darum ist jede Regel dieser Welt ohne
 * laufenden Server pruefbar. Was daraus folgt, steht in {@link Bescheid#ERTEILT}.
 */
public final class Wolkenbuch {

    /** Was aus dem Versuch wurde, eine Insel zu nehmen. */
    public enum Bescheid {
        /**
         * Die Insel ist vergeben und bezahlt - jetzt muss sie noch AUFGESETZT werden.
         *
         * <p>Das ist die eine Zusage, die dieses Buch nicht selbst halten kann: Es gibt
         * das Feld heraus, und der Aufrufer setzt die Startinsel darauf. Bleibt das aus
         * (etwa weil die Welt nicht geladen ist), gehoert dem Spieler ein Feld aus Luft -
         * reparierbar, weil Aufsetzen und Zuruecksetzen dieselbe Hand sind, aber er muss
         * es erfahren.
         */
        ERTEILT,
        /** Er hat schon so viele, wie sein Rang erlaubt. */
        GRENZE_ERREICHT,
        /**
         * Die Welt ist voll - jedes Feld innerhalb der Grenze ist vergeben.
         *
         * <p>Bei 1.443 Feldern ist das weit weg, aber es ist der Zustand, in dem ein
         * Spieler sonst eine Zusage ohne Insel bekaeme. Der Ausweg ist eine groessere
         * {@code welten.wolkenwerder.kante} - die Grenze waechst nach aussen, und was
         * heute drin liegt, liegt auch danach drin.
         */
        WELT_VOLL,
        /** Das Geld reicht nicht. Ab Werk kostet eine Insel nichts, also nie. */
        ZU_WENIG_MARK,
        /** Die Ablage hat nicht geantwortet. Nichts ist passiert, auch nicht am Konto. */
        GESTOERT
    }

    /**
     * Der Bescheid samt allem, was eine Meldung braucht.
     *
     * @param bescheid was entschieden wurde
     * @param kosten   was es gekostet hat oder gekostet haette
     * @param insel    die Zeile, falls eine entstanden ist
     */
    public record Entscheid(Bescheid bescheid, long kosten, Optional<Parzelle> insel) {

        /** Ob die Insel vergeben ist. */
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

    private final Wolkengrenze grenze;
    private final Wolkenplan plan;
    private final BesitzLager lager;
    private final Kasse kasse;

    /**
     * Woher Preis und Obergrenze kommen.
     *
     * <p>Das Feld darf {@code final} bleiben, weil nicht der WERT eingefroren wird,
     * sondern der Zugang zu ihm: Der Preis wird genau in dem Augenblick geholt, in dem
     * abgebucht wird.
     */
    private final Wirtschaftsstand wirtschaft;

    public Wolkenbuch(Wolkengrenze grenze, BesitzLager lager, Kasse kasse,
                      Wirtschaftsstand wirtschaft) {
        if (grenze == null) {
            throw new IllegalArgumentException("Ohne Wolkengrenze gibt es keine Welt, in"
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

    /** Wie weit Wolkenwerder reicht. */
    public Wolkengrenze grenze() {
        return grenze;
    }

    /** Die Geometrie - dieselbe, die der Weltgenerator und das Aufsetzen benutzen. */
    public Wolkenplan plan() {
        return plan;
    }

    /** Was eine Insel JETZT kostet. Beim Gebrauch zu fragen, nicht zu merken. */
    public long preis() {
        return wirtschaft.werte().wolken().preis();
    }

    /** Wie viele Inseln ein Spieler ohne besonderes Recht halten darf. */
    public int hoechstzahl() {
        return wirtschaft.werte().wolken().hoechstzahl();
    }

    /**
     * Was JETZT in eine Starttruhe gehoert.
     *
     * <p>Beim Gebrauch zu fragen und nicht zu merken, wie der Preis: Ein Neuladen tauscht
     * den ganzen Wirtschaftssatz, und eine gemerkte Truhe gaebe dem naechsten Spieler den
     * Inhalt von gestern.
     */
    public Map<String, Integer> starttruhe() {
        return wirtschaft.werte().wolken().starttruhe();
    }

    /**
     * Eine Wolkeninsel nehmen - das naechste freie Feld.
     *
     * <p>Bei Erfolg ist der Preis abgebucht und die Zeile geschrieben; die Bloecke stehen
     * noch NICHT. Bei jedem anderen Bescheid ist nichts passiert, auch nicht an der Kasse.
     *
     * <p>Die Reihenfolge der Riegel ist die KOSTENREIHENFOLGE, wie im Inselbuch: erst
     * rechnen, dann in der Ablage nachsehen, zuletzt Geld anfassen. Eine Abbuchung, die
     * zurueckgenommen werden muss, kostet zwei Schreiboperationen plus eine Zeile im
     * Kontobuch, die der Spieler sieht.
     *
     * <p><b>Die Hoechstzahl kommt von aussen</b> und nicht aus {@link #hoechstzahl}: Sie
     * haengt am RECHT des Spielers, und Rechte kennt nur das Plugin. Die Zahl aus der
     * Einstellungsdatei ist die Untergrenze fuer jeden, der kein besonderes Recht hat.
     *
     * @param welt        Weltschluessel von Wolkenwerder
     * @param spieler     wer eine Insel haben will
     * @param hoechstzahl wie viele Inseln sein Rang erlaubt
     */
    public Entscheid nehmen(String welt, UUID spieler, int hoechstzahl) {
        try {
            int bereits = lager.anzahlVon(welt, spieler);
            if (bereits >= hoechstzahl) {
                // VOR der Suche: Wer schon genug hat, braucht kein freies Feld - und die
                // Suche liest die ganze Liste der vergebenen.
                return Entscheid.nein(Bescheid.GRENZE_ERREICHT);
            }
            Optional<int[]> feld = freiesFeld(welt);
            if (feld.isEmpty()) {
                return Entscheid.nein(Bescheid.WELT_VOLL);
            }
            int feldX = feld.get()[0];
            int feldZ = feld.get()[1];
            long preis = preis();
            if (!kasse.abbuchen(spieler, preis, Buchungsgrund.WOLKE_GENOMMEN)) {
                return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis);
            }
            try {
                return new Entscheid(Bescheid.ERTEILT, preis, Optional.of(
                        lager.wolkeAnlegen(welt, feldX, feldZ, spieler, preis)));
            } catch (RuntimeException fehler) {
                // Das Geld ist weg, die Insel nicht gekommen - also zurueck damit. Der
                // Fall ist hier naeher als bei den Meerinseln: Zwei Spieler, die im
                // selben Augenblick tippen, bekommen aus der Ringsuche DASSELBE Feld,
                // und sie stehen dafuer nicht einmal am selben Ort. Den zweiten laesst
                // der eindeutige Schluessel auflaufen.
                kasse.gutschreiben(spieler, preis, Buchungsgrund.WOLKE_ERSTATTET);
                return Entscheid.nein(Bescheid.GESTOERT, preis);
            }
        } catch (RuntimeException fehler) {
            // VOR der Abbuchung: Hier ist nichts passiert, auch nicht an der Kasse.
            return Entscheid.nein(Bescheid.GESTOERT);
        }
    }

    /**
     * Das naechste freie Feld - ringweise von der Mitte nach aussen.
     *
     * <p>Gerechnet wird in {@link Feldfenster#freiesFeld}, und zwar fuer die Freistaedte
     * und fuer Wolkenwerder an EINER Stelle: Zwei Abschriften waeren zwei Orte, an denen
     * die Reihenfolge der Ringe auseinanderlaeuft - und dann vergaebe die eine Welt ihre
     * Felder anders als die andere.
     */
    private Optional<int[]> freiesFeld(String welt) {
        return grenze.fenster().freiesFeld(
                lager.besitzIn(welt, Art.WOLKE).stream()
                        .map(insel -> new int[] {insel.x(), insel.z()})
                        .toList(),
                Wolkenplan.ANKUNFTSFELD);
    }

    /**
     * Was aus dem Versuch wurde, einen Mitbauer zu setzen.
     *
     * <p><b>Ein ZWEITES Bescheid-Wort neben {@link Bescheid}</b>, und das ist eine
     * Entscheidung gegen die naheliegende: Ein gemeinsames Wort fuer beide Vorgaenge
     * zwaenge jeden Verzweig darueber, auch die Faelle des anderen zu behandeln - der
     * Satz fuer "die Welt ist voll" stuende dann in der Antwort auf "trag meinen Freund
     * ein". Mit zwei Worten bleibt jeder Verzweig vollstaendig UND ohne {@code default},
     * und genau das ist der Riegel, der einen neuen Fall nicht stumm durchlaesst.
     */
    public enum Mitbescheid {
        /** Eingetragen oder ausgetragen - die Zeile steht. */
        ERTEILT,
        /** Hier liegt keine Insel - der Spieler steht in der Leere oder auf Feld 0/0. */
        KEINE_INSEL,
        /** Die Insel gehoert einem anderen. Nur der Eigentuemer setzt Mitbauer. */
        NICHT_EIGENTUEMER,
        /** Der stand schon drin. */
        SCHON_EINGETRAGEN,
        /** Der stand nicht drin. */
        NICHT_EINGETRAGEN,
        /** Die Ablage hat nicht geantwortet. */
        GESTOERT
    }

    /**
     * Der Bescheid eines Mitbauer-Vorgangs samt der Insel, um die es ging.
     *
     * @param bescheid was entschieden wurde
     * @param insel    die Zeile, falls eine gefunden wurde - auch bei einer Absage, damit
     *                 die Meldung das Feld nennen kann
     */
    public record Mitentscheid(Mitbescheid bescheid, Optional<Parzelle> insel) {

        /** Ob die Liste geaendert wurde. */
        public boolean erteilt() {
            return bescheid == Mitbescheid.ERTEILT;
        }

        static Mitentscheid nein(Mitbescheid grund) {
            return new Mitentscheid(grund, Optional.empty());
        }
    }

    /**
     * Einen Mitbauer eintragen - auf der Insel, auf der der Eigentuemer STEHT.
     *
     * <p>Die Stelle und nicht ein Feld als Argument, und das ist dieselbe Wahl wie bei den
     * Parzellen ({@code Besitzbuch.mitbauerHinzufuegen}): Wer jemanden einlaedt, steht
     * dabei auf seiner Insel - und in einer Welt, in der jeder mehrere Felder haben kann,
     * ist "hier" die eindeutigere Angabe als eine Zahl, die man abschreiben muss.
     *
     * <p><b>Der Eigentuemer zaehlt selbst nicht als Mitbauer</b>: Er darf ohnehin alles,
     * und ein Eintrag waere eine Zeile, die nichts aendert. Darum gilt er als "schon
     * eingetragen" - dieselbe Antwort wie im Besitzbuch.
     */
    public Mitentscheid mitbauerHinzufuegen(String welt, UUID eigentuemer, int weltX,
                                            int weltZ, UUID neuer) {
        try {
            Optional<Parzelle> gefunden = inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Mitentscheid.nein(Mitbescheid.KEINE_INSEL);
            }
            Parzelle insel = gefunden.get();
            if (!insel.gehoert(eigentuemer)) {
                return new Mitentscheid(Mitbescheid.NICHT_EIGENTUEMER, gefunden);
            }
            if (insel.mitbauer().contains(neuer) || insel.gehoert(neuer)) {
                return new Mitentscheid(Mitbescheid.SCHON_EINGETRAGEN, gefunden);
            }
            Set<UUID> neueListe = new LinkedHashSet<>(insel.mitbauer());
            neueListe.add(neuer);
            lager.mitbauerSetzen(insel.id(), neueListe);
            return new Mitentscheid(Mitbescheid.ERTEILT,
                    lager.finde(welt, insel.x(), insel.z()));
        } catch (RuntimeException fehler) {
            return Mitentscheid.nein(Mitbescheid.GESTOERT);
        }
    }

    /** Einen Mitbauer austragen - auf der Insel, auf der der Eigentuemer STEHT. */
    public Mitentscheid mitbauerEntfernen(String welt, UUID eigentuemer, int weltX,
                                          int weltZ, UUID bisheriger) {
        try {
            Optional<Parzelle> gefunden = inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Mitentscheid.nein(Mitbescheid.KEINE_INSEL);
            }
            Parzelle insel = gefunden.get();
            if (!insel.gehoert(eigentuemer)) {
                return new Mitentscheid(Mitbescheid.NICHT_EIGENTUEMER, gefunden);
            }
            if (!insel.mitbauer().contains(bisheriger)) {
                return new Mitentscheid(Mitbescheid.NICHT_EINGETRAGEN, gefunden);
            }
            Set<UUID> neueListe = new LinkedHashSet<>(insel.mitbauer());
            neueListe.remove(bisheriger);
            lager.mitbauerSetzen(insel.id(), neueListe);
            return new Mitentscheid(Mitbescheid.ERTEILT,
                    lager.finde(welt, insel.x(), insel.z()));
        } catch (RuntimeException fehler) {
            return Mitentscheid.nein(Mitbescheid.GESTOERT);
        }
    }

    /**
     * Die Insel an dieser Stelle, falls eine vergeben ist.
     *
     * <p>In der Leere leer, und das ist mehr als eine Abkuerzung: Ohne diesen Riegel
     * nennte {@link Wolkenplan#feldNummer} auch mitten in der Leere ein Feld - das davor -,
     * und die Antwort waere die Insel des Nachbarn.
     */
    public Optional<Parzelle> inselAn(String welt, int weltX, int weltZ) {
        if (plan.feldBei(weltX, weltZ) != Wolkenplan.Feld.WOLKENFELD) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.feldNummer(weltX), plan.feldNummer(weltZ));
    }

    /** Alle Wolkeninseln dieses Spielers, nach Lage sortiert. */
    public List<Parzelle> inselnVon(String welt, UUID spieler) {
        return lager.vonSpieler(welt, spieler);
    }

    /**
     * ALLE vergebenen Wolkeninseln dieser Welt - fuer die Rangliste.
     *
     * <p>Mit der ART und nicht nur mit der Welt: In Wolkenwerder kann es ausser Inseln
     * nichts anderes geben, aber die Abfrage ist dieselbe wie bei den Freistaedten, und
     * eine Welt ohne Art-Filter waere die erste Stelle, an der das stillschweigend gilt.
     *
     * <p>Hoechstens {@link Wolkengrenze#inselnZahl} Zeilen, und das ist der Grund, warum
     * die Rangliste sie alle lesen darf: 1.443 Zeilen sind eine Handvoll Kilobytes.
     */
    public List<Parzelle> inselnIn(String welt) {
        return lager.besitzIn(welt, Art.WOLKE);
    }

    /** Wie viele Wolkeninseln dieser Spieler schon hat. */
    public int anzahlVon(String welt, UUID spieler) {
        return lager.anzahlVon(welt, spieler);
    }

    /**
     * Ob hier gebaut werden darf.
     *
     * <p>Drei Faelle, und nur einer davon ist ein Ja: In der LEERE darf niemand bauen -
     * ein Block dort ist der erste Block einer Bruecke zum Nachbarn. Auf einem Feld, das
     * niemandem gehoert, auch nicht; dazu gehoert das ANKUNFTSFELD, und zwar ohne eigenen
     * Zweig, denn es ist nie vergeben. Und auf einer vergebenen Insel entscheidet die
     * Zeile selbst - Eigentuemer und Mitbauer.
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
