package de.freimarken.paper;

import java.util.List;
import java.util.Set;

/**
 * Was ein Satz der Sprachdatei ueber sich weiss - ohne ein einziges Wort.
 *
 * <p>Die Entsprechung zu den {@code PFAD_}-Konstanten in
 * {@link de.freimarken.kern.Wirtschaftswerte}: Der Pfad steht hier und nicht an jeder
 * Stelle, die klagt. Der WORTLAUT steht NUR in der ausgelieferten Ressource, die
 * Pruefvorlage NUR hier - deshalb ist die Platzhalterprobe nicht selbstbezueglich. Wer
 * beim Abschreiben einem Satz seinen {@code {betrag}} wegnimmt, bekommt eine rote Probe
 * und keinen stummen Fehler.
 *
 * <p>Eine Schnittstelle und sieben Aufzaehlungen dahinter, nicht eine Liste: Was
 * {@code values()} aufzaehlt, kann man nicht vergessen, und sieben Dateien teilen sich
 * zwei gleichzeitig arbeitende Haende nie.
 */
interface Satz {

    /** Wofuer ein Satz gebraucht wird - und damit, was beim Laden geprueft wird. */
    enum Art {
        /** Ein ganzer Satz an einen Menschen. Traegt Farbtags. Darf eine Umbruchmarke tragen. */
        SATZ,
        /** Ein Satz mit einer Zahl: zwei Zeilen in der Datei, {@code .eins} und {@code .viele}. */
        GEZAEHLT,
        /** Ein Wortstueck, das als WERT in einen anderen Satz geht. Ohne Farbtag. */
        WORT,
        /** Ein Muster fuer {@code DateTimeFormatter}. Ohne Farbtag, beim Laden uebersetzt. */
        MUSTER,
        /**
         * Ein Satz fuer den Handbalken ueber dem Gegenstandsgurt. Traegt Farbtags, aber
         * GENAU EINE Zeile: {@code sendActionBar} nimmt ein Component und keine Liste
         * (Schutzwache:210). Eine Umbruchmarke darin ist eine KLAGE mit Zeilennummer -
         * sonst verschwindet die zweite Zeile stumm.
         */
        HANDBALKEN
    }

    /** Der Pfad in der Datei, ohne {@code .eins}/{@code .viele}. */
    String pfad();

    Art art();

    /** Die Namen, die der Satz einsetzen DARF - der Vertrag, wortlos. */
    Set<String> platzhalter();

    /** Ob ein leerer Wert erlaubt ist. Heute: nirgends {@code true}. */
    boolean darfLeerSein();

    /** Die Pfade in der Datei: einer, oder zwei mit {@code .eins} und {@code .viele}. */
    default List<String> pfade() {
        return art() == Art.GEZAEHLT
                ? List.of(pfad() + ".eins", pfad() + ".viele")
                : List.of(pfad());
    }
}
