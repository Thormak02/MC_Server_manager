package de.freimarken.kern;

import java.util.Map;

/**
 * Was ein Spieler traegt - die EINE Naht zwischen einer Regel und dem Server.
 *
 * <p><b>Warum das eine eigene Datei ist</b> [20.11.2026, Schritt 12 Teil G]: Bis hierher
 * war diese Schnittstelle in {@link Aufgabenbuch} geschachtelt, weil es dort den einzigen
 * Verbraucher gab. Mit dem Muenzhaus kam der zweite, und {@code Aufgabenbuch.Vorrat} in
 * der Signatur von {@link Muenzhaus} haette behauptet, der Kauf einer Kosmetik haenge an
 * den Herausforderungen von Wolkenwerder. Dieselbe Auskopplung aus demselben Grund wie bei
 * {@code Rangzahl}, als die dritte Obergrenze derselben Bauart dazukam.
 *
 * <p><b>Zahlen und kein {@code boolean}</b>, und das ist der Kern dieser Naht: Bukkits
 * {@code removeItem} und {@code addItem} sind NICHT unteilbar (dieselbe Lehre steht in
 * {@code Shopware}). Sie nehmen und legen, was sie finden, und melden den Rest - wer sie
 * als ganz-oder-gar-nicht liest, verliert Gegenstaende.
 *
 * <p><b>Dahinter rechnen die Regeln mit vier Zahlen</b> - zaehlen, nehmen, hineinlegen,
 * Platz -, und genau deshalb ist jeder ihrer Zweige ohne laufenden Server durchlaufbar,
 * auch der teuerste: das Zurueckgeben nach einem halben Nehmen.
 *
 * <p><b>Nur die ART wird verglichen, nicht der Gegenstand</b> - das entscheidet die
 * Bukkit-Haelfte ({@code Vorratshand}), und es hat Folgen fuer jeden, der hier etwas
 * zurueckgeben will: Eine verzauberte Spitzhacke und eine blanke sind fuer diese Naht
 * dasselbe. Wer NIMMT, um etwas Geliefertes zurueckzuholen, kann darum das bessere
 * Exemplar des Spielers treffen. Das Aufgabenbuch darf das (es nimmt Rohstoffe und gibt
 * genau sie zurueck), das Muenzhaus nicht - es nimmt nie zurueck und fragt stattdessen
 * vorher nach dem {@link #fehlenderPlatz}.
 */
public interface Vorrat {

    /** Wie viele Stueck dieses Gegenstandes er hat. */
    int zaehle(String gegenstand);

    /** Nimmt Stueck weg und meldet, wie viele WIRKLICH wegkamen. */
    int nimm(String gegenstand, int stueck);

    /** Legt Stueck hinein und meldet, wie viele WIRKLICH ankamen. */
    int lege(String gegenstand, int stueck);

    /**
     * Wie viele Stueck dieser Lieferung NICHT hineinpassen - 0 heisst: alles passt.
     *
     * <p><b>EINE Frage ueber den ganzen Posten und nicht eine je Gegenstand</b>, und das
     * ist der ganze Grund fuer diesen Zuschnitt: Die freien Plaetze teilen sich alle
     * Gegenstaende. Je Zeile gefragt, antworteten drei Zeilen bei einem freien Platz
     * dreimal "vierundsechzig passen" - und zusammen passt nichts. Die Antwort auf die
     * Lieferung ist nur als Antwort auf die ganze Lieferung richtig.
     *
     * <p>Gebraucht wird sie vom {@link Muenzhaus}, das NIE etwas zurueckholt und darum
     * vorher fragen muss. Das {@link Aufgabenbuch} fragt sie nicht: Es nimmt nur und legt
     * ausschliesslich zurueck, was es im selben Augenblick genommen hat - dafuer ist der
     * Platz schon dadurch frei, dass es ihn gerade geraeumt hat.
     *
     * @param gegenstaende je Gegenstand die Anzahl, die hineinsoll
     */
    int fehlenderPlatz(Map<String, Integer> gegenstaende);
}
