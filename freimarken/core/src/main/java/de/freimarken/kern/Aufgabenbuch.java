package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Regel ueber die Herausforderungen - abgeben, pruefen, bezahlen (Plan 4.1).
 *
 * <p><b>Ohne Bukkit, und das ist der ganze Zweck des {@link Vorrat}:</b> Was ein Spieler
 * traegt und was ihm weggenommen wird, kann nur der Server sagen. Diese Regel bekommt es
 * als drei Zahlen herein - zaehlen, nehmen, zurueckgeben -, und damit laesst sich jeder
 * Zweig ohne laufenden Server durchlaufen, auch der teuerste: das Zurueckgeben nach einem
 * halben Nehmen.
 *
 * <p><b>Die Reihenfolge der Schritte ist die Sache selbst</b>, und sie steht in
 * {@link #abgeben}: zaehlen, nehmen, EINTRAGEN, zahlen. Das Eintragen ist der Riegel gegen
 * die doppelte Belohnung - es geht durch {@link Wolkenablage#erfuellen}, und dessen Antwort
 * kommt aus einem eindeutigen Schluessel in der Ablage. Eine Pruefung davor hat eine
 * Luecke: Zwei Abgaben im selben Augenblick zahlten zweimal.
 *
 * <p><b>Und darum wird VOR dem Eintragen genommen.</b> Umgekehrt waere das Eintragen nicht
 * zurueckzunehmen - es gibt kein "unerfuellen" -, und ein Spieler haette die Stufe ohne
 * die Gegenstaende. Scheitert das Eintragen, kommt alles Genommene zurueck.
 */
public final class Aufgabenbuch {

    /**
     * Was ein Spieler traegt - die EINE Naht zum Server.
     *
     * <p>Drei Zahlen und kein {@code boolean}: Bukkits {@code removeItem} und
     * {@code addItem} sind NICHT unteilbar (dieselbe Lehre steht in {@code Shopware}). Sie
     * nehmen und legen, was sie finden, und melden den Rest - wer sie als
     * ganz-oder-gar-nicht liest, verliert Gegenstaende.
     */
    public interface Vorrat {

        /** Wie viele Stueck dieses Gegenstandes er hat. */
        int zaehle(String gegenstand);

        /** Nimmt Stueck weg und meldet, wie viele WIRKLICH wegkamen. */
        int nimm(String gegenstand, int stueck);

        /** Legt Stueck zurueck und meldet, wie viele WIRKLICH ankamen. */
        int lege(String gegenstand, int stueck);
    }

    /** Was aus einer Abgabe wird. */
    public enum Bescheid {

        /** Erfuellt: Die Gegenstaende sind weg, die Stufe ist hoeher, das Geld da. */
        ERTEILT,

        /** In dieser Welt gibt es keine Herausforderungen - der Schalter ist aus. */
        AUS,

        /** Diesen Schluessel kennt die Liste nicht. */
        GIBT_ES_NICHT,

        /** Hier ist keine Insel - in der Leere oder auf einem freien Feld. */
        KEINE_INSEL,

        /** Die Insel gehoert einem anderen, und er hat niemanden eingetragen. */
        NICHT_BERECHTIGT,

        /** Diese Herausforderung steht auf dieser Insel schon. */
        SCHON_ERFUELLT,

        /** Es fehlt etwas - was, steht im Entscheid. */
        FEHLT,

        /**
         * Gezaehlt war genug da, genommen wurde zu wenig.
         *
         * <p>Der Fall liegt zwischen zwei Augenblicken: Was ein Spieler zwischen dem
         * Zaehlen und dem Nehmen wegwirft, ist weg. Alles Genommene ist dann
         * zurueckgegeben, und nichts ist eingetragen.
         */
        NICHT_GENOMMEN,

        /** Die Ablage oder die Kasse hat geschwiegen. Nichts ist passiert. */
        GESTOERT
    }

    /**
     * Das Ergebnis einer Abgabe.
     *
     * @param bescheid  was daraus wurde
     * @param aufgabe   welche Aufgabe gemeint war, falls es sie gibt
     * @param fehlt     je Gegenstand, wie viele Stueck FEHLEN - leer, wenn nichts fehlt
     * @param belohnung was gutgeschrieben wurde; nur bei {@link Bescheid#ERTEILT}
     * @param stufe     die Insel-Stufe DANACH; bei jedem anderen Bescheid die von vorher
     */
    public record Entscheid(Bescheid bescheid, Optional<Aufgabenwerte.Aufgabe> aufgabe,
                            Map<String, Integer> fehlt, long belohnung, int stufe) {

        public Entscheid {
            fehlt = Map.copyOf(fehlt);
        }

        /** Eine Absage ohne Aufgabe und ohne Zahlen. */
        static Entscheid nein(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), Map.of(), 0L, 0);
        }

        /** Eine Absage, die ihre Aufgabe und die bestehende Stufe mitnimmt. */
        static Entscheid nein(Bescheid bescheid, Aufgabenwerte.Aufgabe aufgabe,
                              int stufe) {
            return new Entscheid(bescheid, Optional.of(aufgabe), Map.of(), 0L, stufe);
        }

        public boolean erteilt() {
            return bescheid == Bescheid.ERTEILT;
        }
    }

    /** Eine Zeile der Tafel: die Aufgabe und ob sie auf DIESER Insel schon steht. */
    public record Zeile(Aufgabenwerte.Aufgabe aufgabe, boolean erfuellt) {
    }

    /**
     * Die Tafel fuer einen Ort - alle Aufgaben mit ihrem Stand.
     *
     * @param bescheid {@link Bescheid#ERTEILT}, {@link Bescheid#AUS},
     *                 {@link Bescheid#KEINE_INSEL} oder {@link Bescheid#GESTOERT}
     * @param zeilen   die Aufgaben in der Reihenfolge der Datei
     * @param stufe    wie viele davon erfuellt sind
     */
    public record Tafel(Bescheid bescheid, List<Zeile> zeilen, int stufe) {

        public Tafel {
            zeilen = List.copyOf(zeilen);
        }

        static Tafel nein(Bescheid bescheid) {
            return new Tafel(bescheid, List.of(), 0);
        }
    }

    private final Wolkenbuch buch;
    private final Wolkenablage wolken;
    private final Kasse kasse;

    /**
     * Woher die Liste kommt - der STAND und keine Kopie.
     *
     * <p>Wie beim Preis im Wolkenbuch: Die Liste wird in dem Augenblick geholt, in dem
     * abgegeben wird. Eine kopierte waere nach dem ersten Neuladen taub, und der Betreiber
     * saehe seine neue Aufgabe nicht.
     */
    private final Wirtschaftsstand wirtschaft;

    public Aufgabenbuch(Wolkenbuch buch, Wolkenablage wolken, Kasse kasse,
                        Wirtschaftsstand wirtschaft) {
        if (buch == null || wolken == null || kasse == null || wirtschaft == null) {
            throw new IllegalArgumentException("Ohne Wolkenbuch, Inselstaende, Kasse und"
                    + " Wirtschaftsstand gibt es keine Herausforderungen");
        }
        this.buch = buch;
        this.wolken = wolken;
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
    }

    /** Was JETZT in der Liste steht. Beim Gebrauch zu fragen, nicht zu merken. */
    public Aufgabenwerte werte() {
        return wirtschaft.werte().wolken().aufgaben();
    }

    /**
     * Die Tafel fuer die Insel an diesem Ort.
     *
     * <p>Jeder darf sie sehen, auch auf einer fremden Insel: Was dort erfuellt ist, ist
     * kein Geheimnis - und ein Spieler, der einem Freund hilft, soll den Stand lesen
     * koennen, ohne eingetragen zu sein.
     */
    public Tafel tafel(String welt, int weltX, int weltZ) {
        Aufgabenwerte werte = werte();
        if (!werte.greift()) {
            return Tafel.nein(Bescheid.AUS);
        }
        try {
            Optional<Parzelle> gefunden = buch.inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Tafel.nein(Bescheid.KEINE_INSEL);
            }
            Inselstand stand = wolken.stand(gefunden.get().id());
            List<Zeile> zeilen = new ArrayList<>();
            int erfuellt = 0;
            for (Aufgabenwerte.Aufgabe aufgabe : werte.liste()) {
                boolean steht = stand.hatErfuellt(aufgabe.schluessel());
                zeilen.add(new Zeile(aufgabe, steht));
                if (steht) {
                    erfuellt++;
                }
            }
            // GEZAEHLT ueber die Liste und nicht stand.stufe(): Jene Zahl zaehlt JEDE
            // Zeile der Ablage, auch eine Aufgabe, die der Betreiber inzwischen
            // herausgenommen hat. Fuer die Erzausbeute ist das richtig - ein Spieler
            // verliert seine Stufe nicht, weil eine Zeile aus der Datei verschwindet.
            // In der Tafel waere es falsch: Dort stuende "7 von 5".
            return new Tafel(Bescheid.ERTEILT, zeilen, erfuellt);
        } catch (RuntimeException fehler) {
            return Tafel.nein(Bescheid.GESTOERT);
        }
    }

    /**
     * Eine Herausforderung abgeben - auf der Insel, auf der der Spieler STEHT.
     *
     * <p>Bei {@link Bescheid#ERTEILT} sind die Gegenstaende weg, die Zeile steht in der
     * Ablage und die Belohnung ist gutgeschrieben. Bei JEDEM anderen Bescheid hat der
     * Spieler alles noch - auch nach einem halben Nehmen.
     *
     * <p><b>Der Ort und nicht der Spieler entscheidet, welche Insel gemeint ist</b>, wie
     * bei {@code /wolke mitbauer}: In einer Welt, in der jemand mehrere Felder haben kann,
     * ist "hier" die eindeutigere Angabe als eine Zahl, die man abschreiben muss. Und der
     * Fortschritt gehoert der INSEL, nicht dem Spieler - zwei Inseln desselben Spielers
     * haben zwei Stufen.
     *
     * <p><b>Abgeben darf, wer dort bauen darf</b> - Eigentuemer UND Mitbauer. Wer eine
     * Insel zusammen aufbaut, soll auch zusammen abgeben koennen; die Belohnung bekommt,
     * wer abgibt, die Stufe bekommt die Insel.
     */
    public Entscheid abgeben(String welt, UUID spieler, int weltX, int weltZ,
                             String schluessel, Vorrat vorrat) {
        if (vorrat == null) {
            throw new IllegalArgumentException("Ohne Vorrat gibt es nichts abzugeben");
        }
        Aufgabenwerte werte = werte();
        if (!werte.greift()) {
            return Entscheid.nein(Bescheid.AUS);
        }
        Optional<Aufgabenwerte.Aufgabe> gesucht = werte.aufgabe(schluessel);
        if (gesucht.isEmpty()) {
            return Entscheid.nein(Bescheid.GIBT_ES_NICHT);
        }
        Aufgabenwerte.Aufgabe aufgabe = gesucht.get();
        Map<String, Integer> genommen = new LinkedHashMap<>();
        try {
            Optional<Parzelle> gefunden = buch.inselAn(welt, weltX, weltZ);
            if (gefunden.isEmpty()) {
                return Entscheid.nein(Bescheid.KEINE_INSEL, aufgabe, 0);
            }
            Parzelle insel = gefunden.get();
            if (!insel.darfBauen(spieler)) {
                return Entscheid.nein(Bescheid.NICHT_BERECHTIGT, aufgabe, 0);
            }
            int stufe = wolken.stand(insel.id()).stufe();
            if (wolken.stand(insel.id()).hatErfuellt(aufgabe.schluessel())) {
                return Entscheid.nein(Bescheid.SCHON_ERFUELLT, aufgabe, stufe);
            }
            Map<String, Integer> fehlt = fehlendes(aufgabe, vorrat);
            if (!fehlt.isEmpty()) {
                return new Entscheid(Bescheid.FEHLT, gesucht, fehlt, 0L, stufe);
            }
            if (!nehmeAlles(aufgabe, vorrat, genommen)) {
                gebeZurueck(vorrat, genommen);
                return Entscheid.nein(Bescheid.NICHT_GENOMMEN, aufgabe, stufe);
            }
            if (!wolken.erfuellen(insel.id(), aufgabe.schluessel())) {
                // Der Wettlauf: Zwei Abgaben im selben Augenblick. Der eindeutige
                // Schluessel in der Ablage laesst den zweiten auflaufen, und genau das
                // ist der Riegel gegen die doppelte Belohnung.
                gebeZurueck(vorrat, genommen);
                return Entscheid.nein(Bescheid.SCHON_ERFUELLT, aufgabe, stufe);
            }
            // ZULETZT das Geld, wie ueberall in diesem Projekt: Eine Gutschrift, die
            // zurueckgenommen werden muss, steht als zweite Zeile im Kontobuch - und der
            // Spieler sieht sie.
            kasse.gutschreiben(spieler, aufgabe.belohnung(),
                    Buchungsgrund.WOLKE_AUFGABE);
            return new Entscheid(Bescheid.ERTEILT, gesucht, Map.of(),
                    aufgabe.belohnung(), stufe + 1);
        } catch (RuntimeException fehler) {
            // Auch hier zurueck: Der Wurf kann aus der Ablage oder der Kasse kommen, und
            // in beiden Faellen hat der Spieler schon abgegeben. Ein stiller Verlust
            // waere die Art Fehler, die ein Spieler als Diebstahl liest.
            gebeZurueck(vorrat, genommen);
            return Entscheid.nein(Bescheid.GESTOERT, aufgabe, 0);
        }
    }

    /** Was von dieser Aufgabe FEHLT - je Gegenstand die fehlende Anzahl. */
    private static Map<String, Integer> fehlendes(Aufgabenwerte.Aufgabe aufgabe,
                                                  Vorrat vorrat) {
        Map<String, Integer> fehlt = new LinkedHashMap<>();
        for (var zeile : aufgabe.verlangt().entrySet()) {
            int da = vorrat.zaehle(zeile.getKey());
            if (da < zeile.getValue()) {
                fehlt.put(zeile.getKey(), zeile.getValue() - Math.max(0, da));
            }
        }
        return fehlt;
    }

    /**
     * Nimmt ALLES - oder meldet nein und sagt, was schon weg ist.
     *
     * <p>Die Karte {@code genommen} wird mitgefuehrt, auch wenn es schiefgeht: Sie ist die
     * Vorlage fuer den Rueckweg. Ohne sie waere das halb Genommene verloren, und zwar
     * genau in dem Fall, in dem der Spieler nichts bekommt.
     */
    private static boolean nehmeAlles(Aufgabenwerte.Aufgabe aufgabe, Vorrat vorrat,
                                      Map<String, Integer> genommen) {
        for (var zeile : aufgabe.verlangt().entrySet()) {
            int weg = vorrat.nimm(zeile.getKey(), zeile.getValue());
            if (weg > 0) {
                genommen.merge(zeile.getKey(), weg, Integer::sum);
            }
            if (weg < zeile.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Legt zurueck, was genommen wurde. */
    private static void gebeZurueck(Vorrat vorrat, Map<String, Integer> genommen) {
        for (var zeile : genommen.entrySet()) {
            vorrat.lege(zeile.getKey(), zeile.getValue());
        }
        genommen.clear();
    }
}
