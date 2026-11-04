package de.freimarken.paper;

import java.text.NumberFormat;

/**
 * Geldbetraege in Worte - mit deutschen Tausenderpunkten.
 *
 * <p>Eigene Klasse, damit es nur eine Fassung davon gibt: Sonst steht in einem Befehl
 * "400.000 Mark" und in einem anderen "400000", und beides ist derselbe Betrag.
 *
 * <p><b>Wird abgeloest.</b> Der Wortlaut "Mark" steht ab dem 04.11.2026 in
 * {@code sprache-vorlage.yml} unter {@code waehrung.betrag}, und
 * {@link Sprache#betrag(long)} ist der Weg dorthin. Diese Klasse bleibt genau so lange
 * stehen, wie noch ein Aufrufer sie benutzt - geloescht wird sie, wenn der letzte
 * umgestellt ist. Dass zwei Wege eine Zeit lang dasselbe Wort tragen, ist der Preis dafuer,
 * dass nach jedem Bauschritt alles laeuft; dass sie nicht auseinanderlaufen koennen, liegt
 * daran, dass die Zahlform nur noch an einer Stelle steht.
 */
final class Betraege {

    private Betraege() {
    }

    /**
     * Zum Beispiel {@code "400.000 Mark"} - mit einem neuen {@link NumberFormat} je Aufruf.
     *
     * <p>Hier stand ein geteiltes {@code static final NumberFormat} und darueber der
     * eigene Auftrag: "Nicht nebenlaeufig benutzbar - NumberFormat ist das nie. Alles hier
     * laeuft im Serverhauptfaden, und wenn das einmal nicht mehr stimmt, gehoert hier ein
     * neues Exemplar je Aufruf her." Es stimmt nicht mehr: Die Vervollstaendigung von
     * Befehlen und das Senden des Befehlsbaums laufen ausdruecklich nebenlaeufig
     * ({@code Wirtschaftsstand}, Javadoc 17-25), und {@code ParzellenBefehle} hat an zwei
     * Stellen schon {@code suggests()}. Sobald eine Vervollstaendigung einen Preis
     * anzeigt, formatieren zwei Faeden ueber dasselbe Objekt - und ein geteiltes
     * {@code DecimalFormat} liefert dann nicht einen falschen Betrag, sondern Schrott wie
     * {@code "1.0001.000"}.
     *
     * <p>Das neue Exemplar entsteht in {@link Sprache#deutscheZahl}, damit die Zahlform
     * nur an EINER Stelle steht. Zwei Fassungen desselben Tausenderpunkts waeren genau der
     * Fehler, gegen den es diese Klasse gibt.
     */
    static String mark(long betrag) {
        return Sprache.deutscheZahl(betrag) + " Mark";
    }

    /** Nur die Zahl, ohne Waehrung. */
    static String zahl(long wert) {
        return Sprache.deutscheZahl(wert);
    }
}
