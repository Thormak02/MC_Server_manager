package de.freimarken.paper;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * Alle Saetze in EINEM Stueck - die Entsprechung zu
 * {@link de.freimarken.kern.Wirtschaftswerte}.
 *
 * <p>Ein Satz und nicht 231 einzelne Werte, aus demselben Grund wie dort: Wer eine Antwort
 * baut, sieht immer einen in sich stimmigen Stand, auch wenn mitten im Bauen neu geladen
 * wird. Unveraenderlich, also von jedem Faden teilbar - genau die Zusage, auf der
 * {@link Sprachstand} mit einem einzigen {@code volatile} auskommt.
 *
 * @param saetze      Pfad (mit {@code .eins}/{@code .viele}) auf Wortlaut - VOLLSTAENDIG,
 *                    nie eine Teilmenge. Was der Betreiber nicht geaendert hat, steht hier
 *                    mit dem ausgelieferten Wortlaut; darum kann kein Satz verstummen
 * @param ausDerDatei welche davon aus der Datei des Betreibers kommen - nur fuer den
 *                    Bericht, nie fuer eine Entscheidung
 * @param muster      die schon uebersetzten Zeitmuster. Beim LADEN uebersetzt und nicht
 *                    beim ersten {@code /mark verlauf}: Ein unbrauchbares Muster ist dann
 *                    eine Klage mit Zeilennummer und kein Wurf mitten in einem Befehl
 */
record Sprachbuch(Map<String, String> saetze, Set<String> ausDerDatei,
                  Map<String, DateTimeFormatter> muster) {

    Sprachbuch {
        saetze = Map.copyOf(saetze);
        ausDerDatei = Set.copyOf(ausDerDatei);
        muster = Map.copyOf(muster);
    }

    /** Der Wortlaut eines einfachen Satzes. Wirft bei einem gezaehlten - dort fehlt die Zahl. */
    String wortlaut(Satz schluessel) {
        if (schluessel.art() == Satz.Art.GEZAEHLT) {
            throw new IllegalArgumentException(schluessel.pfad() + " ist gezaehlt - ohne"
                    + " Anzahl ist nicht entschieden, ob .eins oder .viele gilt.");
        }
        return hole(schluessel.pfad());
    }

    /** Der Wortlaut eines gezaehlten Satzes: {@code anzahl == 1} nimmt {@code .eins}. */
    String wortlaut(Satz schluessel, long anzahl) {
        if (schluessel.art() != Satz.Art.GEZAEHLT) {
            return wortlaut(schluessel);
        }
        return hole(schluessel.pfad() + (anzahl == 1L ? ".eins" : ".viele"));
    }

    DateTimeFormatter muster(Satz schluessel) {
        DateTimeFormatter form = muster.get(schluessel.pfad());
        if (form == null) {
            throw new IllegalStateException(schluessel.pfad() + " ist kein uebersetztes"
                    + " Zeitmuster. Ein Buch wird nur vollstaendig gebaut - fehlt es hier,"
                    + " ist die Art falsch gesetzt.");
        }
        return form;
    }

    private String hole(String pfad) {
        String wortlaut = saetze.get(pfad);
        if (wortlaut == null) {
            // Kein Rueckfall auf eine leere Zeichenkette: Ein Buch ist vollstaendig oder
            // es entsteht gar nicht. Eine stumme leere Chatzeile waere der eine Fehler,
            // den niemand mehr zuordnen kann.
            throw new IllegalStateException(pfad + " steht nicht in diesem Buch. Ein Buch"
                    + " ist immer vollstaendig - fehlt ein Pfad, wurde es nicht ueber"
                    + " Sprachliste gebaut.");
        }
        return wortlaut;
    }
}
