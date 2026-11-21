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
 * @param farben      die geltende Farbwelt. Sie reist MIT dem Buch, weil sie in derselben
 *                    Datei steht und derselbe Tausch sie austauscht: Ein Buch mit neuen
 *                    Worten und alten Farben hat es damit nie gegeben
 */
record Sprachbuch(Map<String, String> saetze, Set<String> ausDerDatei,
                  Map<String, DateTimeFormatter> muster, Farbtafel farben) {

    Sprachbuch {
        saetze = Map.copyOf(saetze);
        ausDerDatei = Set.copyOf(ausDerDatei);
        muster = Map.copyOf(muster);
        if (farben == null) {
            throw new IllegalArgumentException("Ein Buch ohne Farbwelt gibt es nicht -"
                    + " der Leser traegt Fehlendes aus der Vorgabe nach");
        }
    }

    /** Der Wortlaut eines einfachen Satzes. Wirft bei einem gezaehlten - dort fehlt die Zahl. */
    String wortlaut(Satz schluessel) {
        if (schluessel.art() == Satz.Art.GEZAEHLT) {
            throw new IllegalArgumentException(schluessel.pfad() + " ist gezaehlt - ohne"
                    + " Anzahl ist nicht entschieden, ob .eins oder .viele gilt.");
        }
        if (Sprachschluessel.gehoertZuFamilie(schluessel)
                && !saetze.containsKey(schluessel.pfad())) {
            // DER PFAD STATT EINES WURFS - aber NUR fuer eine offene Familie
            // [20.11.2026].
            //
            // Was es gibt, steht in der config.yml; wie es heisst, in der sprache.yml.
            // Der Betreiber darf dort morgen einen Posten eintragen und die Namenszeile
            // vergessen, und genau diese Antwort haben drei Javadocs, eine
            // Startmeldung und die ausgelieferte Vorlage ihm zugesagt: "Kaufen laesst
            // sich der Posten weiter; in der Tafel steht bis dahin sein Pfad."
            //
            // ZUGESAGT, ABER NICHT GEBAUT - gemessen in der Pruefrunde zu Teil G: Ein
            // vergessener Name liess JEDEN Muenzhausbefehl in "steht nicht in diesem
            // Buch" laufen, also auch die vier richtigen Posten; und ein Kauf buchte ab,
            // lieferte und antwortete dann mit einer roten englischen Serverzeile. Der
            // Spieler haelt ihn fuer gescheitert und kauft ein zweites Mal.
            //
            // Fuer jeden anderen Pfad bleibt es ein WURF: Dort ist ein Fehlen ein
            // Programmfehler, und ein Buch ist vollstaendig oder es entsteht nicht.
            return schluessel.pfad();
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
