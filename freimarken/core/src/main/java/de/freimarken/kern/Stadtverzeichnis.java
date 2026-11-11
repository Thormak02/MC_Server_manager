package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Die Namen aller Freistaedte im Arbeitsspeicher - fuer den Vorschlagsgeber.
 *
 * <p><b>Warum es diese Liste ueberhaupt gibt.</b> Ein Vorschlagsgeber laeuft bei JEDEM
 * TASTENDRUCK: keine Meldung, keine Abklingzeit, kein Blick in die Welt - und ein Blick
 * in die Ablage erst recht nicht. Bis zu 399 Staedte stehen in der Datenbank, und
 * {@code /warp freistadt ne} wuerde sie ohne diese Liste bei jedem Buchstaben erneut
 * holen. Darum liegen die NAMEN hier, und zwar alle.
 *
 * <p><b>Warum ein veralteter Eintrag hier nichts kostet.</b> Dieser Speicher traegt
 * VORSCHLAEGE und keine Taten: Der Warp selbst fragt weiter die Ablage
 * ({@code Freistadtbuch.staedte}), und das Gruenden haengt am eindeutigen Schluessel der
 * Datenbank. Fehlt hier ein Name, fehlt ein Vorschlag - die Stadt bleibt erreichbar, wer
 * sie tippt, kommt an. Das ist der Unterschied zu {@link ZwischenspeicherShops}, dessen
 * Javadoc den belegten Fehler nennt: Dort ANTWORTET der Zwischenspeicher ("hier ist kein
 * Shop"), und eine veraltete Antwort liess einen Trichter abziehen. Hier antwortet er
 * nie.
 *
 * <p><b>Keine Obergrenze fuer die Vorschlaege</b>, und das ist eine Entscheidung gegen
 * die naheliegende: Eine Kappung bei etwa fuenfzig traefe genau den Spieler, dessen Stadt
 * an einundfuenfzigster Stelle steht - er tippt den richtigen Anfang und sieht sie
 * trotzdem nicht, ohne dass ihm jemand sagt warum. Und teuer ist es nicht: Vanilla
 * schlaegt bei {@code /give @p } ueber tausend Gegenstaende vor; 399 kurze Namen sind
 * dagegen nichts.
 *
 * <p>{@code synchronized} auf jeder Methode, wie bei {@link SpeicherLager}: Gegruendet
 * wird auf dem Hauptfaden, und wo ein Vorschlagsgeber laeuft, ist nicht verbuergt - Paper
 * vervollstaendigt einen Teil der Befehle abseits davon. Die Sperre kostet an dieser
 * Stelle nichts und nimmt die Frage aus dem Weg.
 */
public final class Stadtverzeichnis {

    /**
     * Alle Namen, klein und sortiert.
     *
     * <p>Sortiert, damit {@link #vorschlaege} nicht ueber alle laufen muss: Ein
     * {@link TreeSet} findet den Anfang und bricht beim ersten Namen ab, der nicht mehr
     * passt. Und sortiert ist zugleich die Reihenfolge, in der die Namen im
     * Vorschlagsfenster erscheinen - alphabetisch, weil es keine bessere gibt. Bei den
     * Welten ist das anders: Dort gewinnt die Reihenfolge der {@code config.yml}, weil
     * der Betreiber sie gesetzt hat.
     */
    private final TreeSet<String> namen = new TreeSet<>();

    /**
     * Die ganze Liste neu setzen - beim Start, aus der Ablage.
     *
     * <p>Ersetzend und nicht ergaenzend: Nur so verschwindet ein Name, den es nicht mehr
     * gibt. Die Verfallsregel aus Plan 16 (zwoelf Monate ohne Besuch) loescht Staedte,
     * und ein Verzeichnis, das nur waechst, schluege sie noch Jahre danach vor.
     *
     * <p>Leere und {@code null} werden uebersprungen statt zu werfen: Ein Name, der es
     * bis in die Ablage geschafft hat, ist dort am eindeutigen Schluessel vorbeigekommen;
     * ein Vorschlagsverzeichnis, das deswegen den Start abbricht, waere die falsche
     * Stelle fuer diese Klage.
     */
    public synchronized void erneuere(Collection<String> staedtenamen) {
        namen.clear();
        if (staedtenamen == null) {
            return;
        }
        for (String name : staedtenamen) {
            nimmAufOhneSperre(name);
        }
    }

    /**
     * Einen Namen aufnehmen - nach einer Gruendung.
     *
     * <p>Gerufen wird das im {@link Freistadtbuch} und nur dort, in demselben Zug, in dem
     * die Stadt entsteht. Ein zweiter Aufrufer waere ein zweiter Ort, an dem es vergessen
     * werden kann.
     */
    public synchronized void nimmAuf(String name) {
        nimmAufOhneSperre(name);
    }

    private void nimmAufOhneSperre(String name) {
        if (name == null) {
            return;
        }
        String klein = name.trim().toLowerCase(Locale.ROOT);
        if (!klein.isEmpty()) {
            namen.add(klein);
        }
    }

    /**
     * Die Vorschlaege zu einem angefangenen Wort.
     *
     * <p>Kleingeschrieben verglichen, alphabetisch zurueckgegeben. Ein leerer Anfang
     * schlaegt alles vor - wie bei {@link Warpverzeichnis#vorschlaege}, und aus demselben
     * Grund: Wer {@code /warp freistadt } tippt und dann die Tabulatortaste drueckt, will
     * sehen, was es gibt.
     */
    public synchronized List<String> vorschlaege(String angefangen) {
        String klein = angefangen == null
                ? ""
                : angefangen.trim().toLowerCase(Locale.ROOT);
        List<String> treffer = new ArrayList<>();
        // Ab dem ersten Namen, der nicht mehr vor dem Anfang liegt - und Schluss beim
        // ersten, der ihn nicht mehr traegt. Hinter dem stehen nur noch groessere.
        for (String name : namen.tailSet(klein)) {
            if (!name.startsWith(klein)) {
                break;
            }
            treffer.add(name);
        }
        return treffer;
    }

    /** Wie viele Staedte das Verzeichnis kennt - fuer den Startbericht. */
    public synchronized int anzahl() {
        return namen.size();
    }
}
