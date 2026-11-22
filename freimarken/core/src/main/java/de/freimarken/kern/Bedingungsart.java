package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Woran ein Rangaufstieg oder ein Kauf haengen darf - die Liste der messbaren Dinge.
 *
 * <p><b>Der zweite Verbraucher kam am 20.11.2026</b> mit dem {@link Muenzhaus}: Dort
 * haengt ein Katalogposten an denselben Bedingungen, und das ist der Grund, warum hier
 * keine zweite Aufzaehlung daneben steht. Eine eigene Katalogbedingungsart haette
 * {@link #ausSchluessel}, {@link #alleSchluessel} und {@link #inMesseinheit} verdoppelt -
 * und damit die Umrechnung, die es genau einmal geben darf. Was ein Rang verlangen kann,
 * kann auch ein Preis verlangen; gemessen wird beides an derselben Stelle.
 *
 * <p><b>Nicht fuer reine Anwesenheit</b> [entschieden 18.11.2026, Nutzer]: "man soll nicht
 * fuer die reine gespielte zeit belohnt werden, die gespielte zeit gilt als richtwert fuer
 * kosten". Darum steht die TAETIGE Zeit an erster Stelle und die blosse Anwesenheit
 * daneben als das, was sie ist - eine schwaechere Zahl, die ein Betreiber nehmen kann,
 * aber nicht muss. Der Plan nennt in der Rangtafel (Abschnitt 9) Stunden; die sind nach
 * dieser Entscheidung ein Richtwert fuer Preise und nicht der Ausloeser eines Aufstiegs.
 *
 * <p><b>Warum eine Aufzaehlung und keine freien Namen:</b> Jede Art braucht eine Stelle,
 * die sie MISST - die taetige Zeit kommt aus der Spielzeitmessung, die Parzellenzahl aus
 * dem Besitzbuch. Ein frei getippter Name haette keinen Messer und waere eine Bedingung,
 * die nie erfuellt wird; der Spieler sucht dann einen Aufstieg, den es nicht geben kann.
 * Ein unbekannter Name in der Datei wird deshalb BEKLAGT und nicht geraten.
 *
 * <p><b>Der RUNDGANG ist seit dem 21.11.2026 dabei</b> (Schritt 13 Teil D). Er ist die
 * einzige Art, die nichts zaehlt, sondern eine Frage mit ja oder nein beantwortet - 1
 * heisst "abgeschlossen", 0 heisst: keine Bedingung. Dieselbe Form wie {@link #BERUF}.
 *
 * <p><b>Was noch fehlt und mit Absicht fehlt:</b> "eigene Parzelle bebaut" (Plan 9,
 * Buerger). Was "bebaut" misst, steht im Plan nirgends, und eine geratene Zahl waere eine
 * Huerde, die der Betreiber nicht nachrechnen kann. Sie kommt als Zeile dazu, wenn es sie
 * gibt - {@link #ausSchluessel} nimmt sie dann ohne weitere Aenderung an.
 */
public enum Bedingungsart {

    /**
     * Stunden TAETIGER Zeit - gemessen, nicht abgesessen.
     *
     * <p>Die Messstelle aus Schritt 7 zaehlt neben der Anwesenheit die taetige Zeit
     * ({@code Spielzeitstand.taetigSekundenGesamt}); sie ist seit dem 03.11.2026 in
     * Betrieb und hatte bisher nur den Tagesbonus als Verbraucher.
     */
    TAETIGE_STUNDEN("taetige-stunden", "Stunden taetiger Zeit", 100_000L),

    /**
     * Stunden blosser Anwesenheit.
     *
     * <p>Steht zur Verfuegung, ist aber ausdruecklich die schwaechere Zahl: Sie ist mit
     * einem Makro im Stehen zu erreichen, und der Plan sagt das selbst (Abschnitt 9,
     * "gemessene Anwesenheit und keine gemessene Taetigkeit").
     */
    ANWESENHEIT_STUNDEN("anwesenheit-stunden", "Stunden Anwesenheit", 100_000L),

    /** Wie viele Parzellen der Spieler haelt. */
    PARZELLEN("parzellen", "eigene Parzellen", 10_000L),

    /** Ob ein Beruf gewaehlt ist - 1 heisst ja, 0 heisst: keine Bedingung. */
    BERUF("beruf", "gewaehlter Beruf", 1L),

    /** Mark auf dem Konto. */
    GUTHABEN("guthaben", "Mark auf dem Konto", Freistadtwerte.HOECHSTER_BETRAG),

    /**
     * Ob der Rundgang abgeschlossen ist - 1 heisst ja, 0 heisst: keine Bedingung.
     *
     * <p>Dieselbe Form wie {@link #BERUF}: eine Frage und keine Zahl. Die Hoechstzahl ist
     * darum 1 - eine 2 waere "zweimal abgeschlossen" und hiesse nichts.
     *
     * <p><b>Ohne Stationen ist sie NICHT erfuellbar</b>, und das ist Absicht:
     * {@code Rundgang.abgeschlossen} gibt dann {@code false}. Darum steht sie in der
     * ausgelieferten Leiter auch nicht - wer Altfurt gebaut hat, traegt die Stationen
     * UND diese Zeile ein. Stuende sie ab Werk da, waere der Siedlerrang auf einem
     * frischen Server unerreichbar.
     */
    RUNDGANG("rundgang", "Rundgang abgeschlossen", 1L);

    private final String schluessel;

    private final String beschreibung;

    private final long hoechsterWert;

    Bedingungsart(String schluessel, String beschreibung, long hoechsterWert) {
        this.schluessel = schluessel;
        this.beschreibung = beschreibung;
        this.hoechsterWert = hoechsterWert;
    }

    /** Wie die Art in der Einstellungsdatei heisst. */
    public String schluessel() {
        return schluessel;
    }

    /** Was sie misst - fuer die Klage und den Startbericht, nicht fuer das Spiel. */
    public String beschreibung() {
        return beschreibung;
    }

    /**
     * Die hoechste Zahl, die in der Datei stehen darf.
     *
     * <p>Je Art eine eigene Schranke und keine gemeinsame: 100.000 Stunden sind
     * offensichtlich ein Vertipper, bei einem Geldbetrag waeren sie es nicht. Ohne diese
     * Schranke faengt den Vertipper niemand - eine unerfuellbare Bedingung sieht im
     * Betrieb genauso aus wie ein Rang, den eben noch niemand erreicht hat.
     */
    public long hoechsterWert() {
        return hoechsterWert;
    }

    /** Ob diese Art ein Ja/Nein ist und keine Menge. */
    public boolean jaNein() {
        return hoechsterWert == 1L;
    }

    /** Ob diese Art in der Datei in STUNDEN steht und in Sekunden gemessen wird. */
    public boolean inStunden() {
        return this == TAETIGE_STUNDEN || this == ANWESENHEIT_STUNDEN;
    }

    /**
     * Ob der Wert dieser Art ein GELDbetrag ist [20.11.2026].
     *
     * <p><b>Warum das jemand fragen muss</b>: In diesem Projekt kommt jeder Betrag, den
     * ein Spieler liest, aus {@code waehrung.betrag} - mit dem Tausenderpunkt und mit dem
     * Wort des Betreibers. Die Bedingungszeile des Muenzhauses setzte ihre Zahl nackt ein
     * und hatte "Mark" fest in der Sprachdatei: Derselbe Betrag hiess zwei Zeilen
     * hoeher "50.000 Mark" und hier "50000 Mark". Gefunden in der Pruefrunde zu Teil G.
     *
     * <p><b>Ein erschoepfender Schalter</b> und nicht {@code this == GUTHABEN}, anders
     * als bei {@link #inStunden} daneben: Die naechste Art ist entweder Geld oder nicht,
     * und diese Frage soll der Uebersetzer stellen und nicht der Betrieb. (Jene Methode
     * bleibt, wie sie ist - zwei von fuenf Arten aufzuzaehlen waere hier dieselbe
     * Luecke mit umgekehrtem Vorzeichen.)
     */
    public boolean istGeld() {
        return switch (this) {
            case TAETIGE_STUNDEN, ANWESENHEIT_STUNDEN, PARZELLEN, BERUF, RUNDGANG
                    -> false;
            case GUTHABEN -> true;
        };
    }

    /**
     * Die Zahl aus der Datei in der Einheit, in der gemessen wird.
     *
     * <p><b>Die Datei spricht Stunden, die Messung spricht Sekunden</b> - und die
     * Umrechnung steht hier, an EINER Stelle. Stuende sie am Rand, muesste jeder Messer
     * sie selbst machen: einer rechnete in Stunden und schnitte dabei die angefangene ab,
     * der naechste in Sekunden - und zwei Spieler mit derselben Zeit haetten verschiedene
     * Raenge, je nachdem, welcher Weg sie gemessen hat.
     */
    public long inMesseinheit(long ausDerDatei) {
        return inStunden() ? ausDerDatei * SEKUNDEN_JE_STUNDE : ausDerDatei;
    }

    /**
     * Die gemessene Zahl in der Einheit, in der sie in der DATEI steht - der Rueckweg.
     *
     * <p><b>Warum es ihn gibt</b> [20.11.2026, Teil G]: {@link Rangregel.Bedingungsstand} rechnet
     * in der Messeinheit, also in Sekunden. Ungerechnet in eine Chatzeile gesetzt, laese
     * ein Spieler "noch 14.400 Stunden", wo in der {@code config.yml}
     * {@code taetige-stunden: 5} steht - und der Betreiber faende seine Zahl in der Zeile
     * nicht wieder.
     *
     * <p>Er steht neben {@link #inMesseinheit} und nicht beim Leser, aus genau demselben
     * Grund: Zwei Wege zurueck wuerden eines Tages verschieden runden. Gerundet wird
     * ABWAERTS, und das ist die ehrliche Richtung - wer noch 90 Minuten braucht, soll
     * nicht "noch 2 Stunden" lesen und nach einer Stunde immer noch gesperrt sein.
     */
    public long inDerDatei(long gemessen) {
        return inStunden() ? gemessen / SEKUNDEN_JE_STUNDE : gemessen;
    }

    /** Wie viele Sekunden eine Stunde hat - damit die Zahl nicht dreimal dasteht. */
    public static final long SEKUNDEN_JE_STUNDE = 3_600L;

    /** Die Art zu diesem Schluessel - leer, wenn es sie nicht gibt. */
    public static Optional<Bedingungsart> ausSchluessel(String roh) {
        if (roh == null) {
            return Optional.empty();
        }
        String klein = roh.trim().toLowerCase(Locale.ROOT);
        for (Bedingungsart art : values()) {
            if (art.schluessel.equals(klein)) {
                return Optional.of(art);
            }
        }
        return Optional.empty();
    }

    /** Alle Schluessel in der Reihenfolge der Aufzaehlung - fuer Klage und Ergaenzung. */
    public static List<String> alleSchluessel() {
        List<String> namen = new ArrayList<>();
        for (Bedingungsart art : values()) {
            namen.add(art.schluessel);
        }
        return List.copyOf(namen);
    }
}
