package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Was ein Spieler an einem Block getan hat - die drei Taten, die das Blockbuch kennt.
 *
 * <p><b>Eine Aufzaehlung und keine Liste in der Einstellungsdatei</b>, aus demselben
 * Grund wie bei {@link Taetigkeit}: Eine neue Tat braucht einen eigenen Ereigniszweig im
 * Programm. Was man nicht eintragen kann, soll auch nicht aussehen wie ein Eintrag. Was
 * man dagegen EINTRAEGT, ist die Auswahl je Welt - und die nennt genau diese Schluessel.
 *
 * <p><b>"Abgewiesen" ist keine Tat</b>, sondern ihr Ausgang, und darum steht es nicht in
 * dieser Aufzaehlung. Der Unterschied ist nicht akademisch: Eine vierte Konstante
 * {@code ABGEWIESEN} verloere die Angabe, WAS abgewiesen wurde - und genau die braucht
 * die Moderation, denn "hier wurde etwas abgewiesen" beantwortet keine Frage. Eine Zeile
 * traegt darum beides: die Tat und ob sie gelungen ist. In der Auswahl steht
 * {@code abgewiesen} daneben als eigener Name ({@link #NAME_ABGEWIESEN}) und schaltet die
 * misslungenen Versuche zu - quer zu allen drei Taten.
 *
 * <p>Der Schluessel ist kleingeschrieben: Er steht in der {@code config.yml} als Eintrag
 * in der Liste {@code taten}, und er steht in der Spalte {@code tat} der Tabelle. Beides
 * ist dieselbe Zeichenfolge, und zwar mit Absicht - so ist eine Zeile in der Datenbank
 * ohne Uebersetzungstabelle lesbar.
 */
public enum Blocktat {

    /**
     * Einen Block gesetzt.
     *
     * <p>Der Name ist das Material des GESETZTEN Blocks, nicht das des Gegenstands in der
     * Hand. Bei einem Eimer Wasser ist das Wasser der Block, und das ist die Angabe, die
     * jemand sucht, der eine geflutete Parzelle vor sich hat.
     */
    SETZEN("setzen"),

    /**
     * Einen Block abgebaut.
     *
     * <p>Der Name ist das Material des Blocks, wie er VOR dem Abbau dastand - danach
     * steht dort Luft, und die beantwortet keine Frage. Das ist zugleich der Grund, warum
     * diese Tat im Ereignis aufgeschrieben werden muss und nicht danach.
     */
    ABBAUEN("abbauen"),

    /**
     * Einen Block benutzt - Tuer, Knopf, Hebel, Truhe, Ofen.
     *
     * <p><b>Die mit Abstand haeufigste Tat</b>, und darum steht sie in der Vorgabe NICHT
     * drin: Ein Spieler, der eine Minute durch sein Haus laeuft, oeffnet ein Dutzend
     * Tueren, und niemand sucht je danach. Wer einer Welt {@code benutzen} gibt, bekommt
     * eine ungleich groessere Tabelle - in der Marktstadt ist das richtig, denn dort ist
     * die Frage "wer war in meiner Truhe" der ganze Zweck.
     */
    BENUTZEN("benutzen");

    /**
     * Der Name, der in der Auswahl die MISSLUNGENEN Versuche zuschaltet.
     *
     * <p>Steht in derselben Liste wie die drei Taten, ist aber keine - siehe den Kopf
     * dieser Aufzaehlung. Als Konstante und nicht als getippter Text an drei Stellen:
     * Leser, Klage und ausgelieferte Datei muessen dasselbe Wort nennen.
     */
    public static final String NAME_ABGEWIESEN = "abgewiesen";

    private final String schluessel;

    Blocktat(String schluessel) {
        this.schluessel = schluessel;
    }

    /** Der Name in der Einstellungsdatei und in der Spalte {@code tat}. */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Die Tat zu einem Schluessel, oder {@code null} fuer einen unbekannten.
     *
     * <p>Kein Wurf, dieselbe Entscheidung wie bei {@link Taetigkeit#ausSchluessel}: Der
     * Leser der Einstellungsdatei fragt damit, ob ein Eintrag eine Tat ist, und ein
     * unbekannter ist dort eine KLAGE mit dem Namen und kein Absturz beim Start.
     */
    public static Blocktat ausSchluessel(String text) {
        if (text == null) {
            return null;
        }
        String klein = text.trim().toLowerCase(Locale.ROOT);
        for (Blocktat tat : values()) {
            if (tat.schluessel.equals(klein)) {
                return tat;
            }
        }
        return null;
    }

    /**
     * Alle Namen, die in einer Auswahl stehen duerfen - die drei Taten und
     * {@code abgewiesen}.
     *
     * <p>Aus der Aufzaehlung gebaut und nicht abgeschrieben, wie
     * {@code Verdienstliste.taetigkeiten()}: Sonst schweigt die Klage ueber genau die
     * Tat, die jemand neu dazubaut, und der Betreiber sucht einen Namen, den das Programm
     * kennt und nicht nennt.
     */
    public static List<String> erlaubteNamen() {
        List<String> namen = new ArrayList<>();
        for (Blocktat tat : values()) {
            namen.add(tat.schluessel);
        }
        namen.add(NAME_ABGEWIESEN);
        return namen;
    }
}
