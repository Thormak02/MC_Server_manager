package de.freimarken.paper;

import java.util.Set;

/**
 * Was ein Spieler bei der Anmeldung im Buergeramt liest (Plan 10.1).
 *
 * <p><b>Die REGELN stehen hier nicht.</b> Sie stehen unter {@link RegelnSatz#TEXT}, und
 * das Formular der Anmeldung zeigt genau diesen Text - es ist dasselbe Fenster wie
 * {@code /regeln}, nur mit zwei Knoepfen statt einem. Ein zweiter Regeltext hier waere
 * derselbe Text an zwei Orten: Der Betreiber aenderte einen und wundert sich, warum der
 * andere weiter das Alte sagt.
 *
 * <p><b>Was hier steht, ist der Unterschied zwischen LESEN und ANNEHMEN</b> - die Frage
 * unter dem Text, die zwei Knoepfe und die Zeilen danach. Plan 10.1 verlangt die Annahme
 * "als Formular und nicht als Textwand, die man wegklickt"; der Unterschied ist genau
 * diese Frage.
 *
 * <p><b>Die Absage am Bundesmarkt steht ebenfalls nicht hier</b>, sondern in
 * {@link ShopSatz} als {@code SHOP_NICHT_ANGEMELDET}. Dort stehen alle Handelsbescheide,
 * sie sind {@code Art.HANDBALKEN} statt Chatzeilen, und die Familienprobe in
 * {@code Sprachschluessel} zaehlt sie - ein Handelssatz an einem anderen Ort waere der
 * erste, den sie nicht sieht.
 */
enum AnmeldeSatz implements Satz {

    /**
     * Der Titel des Anmeldefensters - im Chat die erste Zeile.
     *
     * <p>Darum GENAU EINE Zeile: Ein Fenstertitel kann keine zweite tragen. Der Satz steht
     * in {@link Einzeiler#CHATZEILEN}, und damit ist eine Umbruchmarke darin schon beim
     * LADEN eine Klage.
     */
    KOPF("anmeldung.kopf", Art.SATZ),

    /**
     * Die Frage unter dem Regeltext - der Unterschied zwischen Lesen und Annehmen.
     *
     * <p>MEHRZEILIG erlaubt: Wer seine Zustimmungsfrage ausfuehrlicher stellen will,
     * schreibt zwei Zeilen. Im Fenster wird jede ein eigener Block, im Chat eine eigene
     * Zeile - dieselbe Behandlung wie {@link RegelnSatz#TEXT}.
     */
    FRAGE("anmeldung.frage", Art.SATZ),

    /** Der Knopf, der annimmt. GENAU EINE Zeile - ein Knopf traegt keine zweite. */
    ANNEHMEN("anmeldung.annehmen", Art.SATZ),

    /**
     * Der Knopf, der das Fenster schliesst, ohne anzunehmen.
     *
     * <p>Er tut NICHTS weiter, und das ist Absicht: Eine Ablehnung mit Folgen - ein
     * Hinauswurf etwa - steht in keinem Plan, und eine Ablehnung ohne Folgen braucht
     * keinen Eintrag in der Ablage. Beim naechsten Beitritt kommt das Fenster wieder.
     */
    SPAETER("anmeldung.spaeter", Art.SATZ),

    /**
     * Die Bestaetigung nach der Annahme - die Aufnahme in den Bund.
     *
     * <p>Plan 10.1: "Die Anmeldung im Buergeramt ist gleichzeitig die Aufnahme in den
     * Bund - Fiktion und Technik fallen zusammen." Darum liest er hier keinen
     * Verwaltungsvermerk, sondern eine Begruessung.
     */
    ANGENOMMEN("anmeldung.angenommen", Art.SATZ),

    /**
     * Er hat schon angenommen - auf ein zweites {@code /regeln annehmen}.
     *
     * <p>Ein eigener Satz und nicht Schweigen: Wer den Befehl tippt, erwartet eine
     * Antwort. Und nicht derselbe wie {@link #ANGENOMMEN}: Eine zweite Begruessung sieht
     * aus, als haette die erste nicht gewirkt.
     */
    SCHON_ANGENOMMEN("anmeldung.schon-angenommen", Art.SATZ),

    /**
     * Der Weg OHNE Fenster - fuer die Konsole und fuer einen alten Client.
     *
     * <p><b>Diese Zeile ist die einzige Bruecke fuer einen Client, der keine Fenster
     * kennt.</b> {@code showDialog} ist ein Senden und kein Fragen (gemessen, siehe
     * {@link Formularhand}): Ein Paket, das eine Bruecke wie ViaProxy verwirft, merkt der
     * Server nicht. Wer das Fenster nicht sieht, bekommt die Regeln im Chat - und diese
     * Zeile sagt ihm, wie er trotzdem zustimmt.
     *
     * <p>Sie nennt den Befehl AUSGESCHRIEBEN und keinen Angabeplatz: {@code /regeln
     * annehmen} ist zu tippen, wie es dasteht.
     */
    HINWEIS("anmeldung.hinweis", Art.SATZ),

    /**
     * Die Konsole kann nicht zustimmen.
     *
     * <p>Eine GELESENE Absage und kein verborgener Knoten: Haette der Zweig ein
     * {@code requires} auf "ist ein Spieler", liesse Brigadier ihn fuer die Konsole
     * verschwinden - und ein Betreiber, der ihn nicht findet, suchte den Fehler bei sich.
     * Eine Zustimmung ist die Erklaerung eines Menschen; der Betreiber ist nicht jeder
     * Spieler, und er kann sie fuer niemanden abgeben.
     */
    NUR_SPIELER("anmeldung.nur-spieler", Art.SATZ),

    /**
     * Es gibt nichts anzunehmen - die Anmeldung ist ausgeschaltet.
     *
     * <p>Fuer den Spieler, der {@code /regeln annehmen} tippt, waehrend
     * {@code anmeldung.an} auf {@code false} steht. Ohne diesen Satz wuerfe die
     * Kernregel, und er lase eine rote englische Zeile fuer eine Lage, die der Betreiber
     * eingestellt hat.
     */
    NICHT_VERLANGT("anmeldung.nicht-verlangt", Art.SATZ);

    private final String pfad;
    private final Art art;

    AnmeldeSatz(String pfad, Art art) {
        this.pfad = pfad;
        this.art = art;
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    /**
     * Keiner dieser Saetze hat einen Angabeplatz.
     *
     * <p>Mit Absicht: Ein {@code {name}} in einer Begruessung waere nett, ein
     * {@code {fassung}} in der Zustimmungsfrage waere es nicht - die Bezeichnung einer
     * Fassung ist ein Verwaltungswert und kein Satzteil, den ein Spieler lesen will.
     * Wer sie braucht, findet sie im Protokoll des Betreibers.
     */
    @Override
    public Set<String> platzhalter() {
        return Set.of();
    }

    /**
     * Keiner dieser Saetze darf leer bleiben.
     *
     * <p>Jeder von ihnen traegt eine Tat oder eine Auskunft, die der Spieler braucht: Ein
     * leerer Knopf waere ein Fenster, in dem man nicht annehmen kann, und ein leerer
     * Hinweis liesse den Spieler mit altem Client ohne Weg dastehen. Leer erlaubt ist im
     * ganzen Projekt nur, was gar keine Zeile SENDET - die zwei Trenner der Weltenliste.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
