package de.freimarken.kern;

import java.util.Set;

/**
 * Was eine Freistadt kostet und wie viele ein Spieler halten darf.
 *
 * <p>Ein eigener Satz und keine zwei losen Zahlen in {@link Wirtschaftswerte}, aus
 * demselben Grund wie bei {@link Mietwerte}: Die Zahlen gehoeren zusammen und werden
 * zusammen gelesen, zusammen geprueft und zusammen gemeldet. Zwei lose Felder haetten
 * ihre Pruefung an zwei Stellen, und die zweite bekaeme die naechste Verschaerfung nicht
 * mit.
 *
 * @param gruendungspreis was das Gruenden kostet, in Mark
 * @param hoechstzahl     wie viele Staedte ein Spieler ohne besonderes Recht haelt
 * @param hoechstzellen   wie viele Zellen ein einzelnes Grundstueck umfasst
 * @param verkaufsgebuehrProzent was der Staat von einem Grundstuecksverkauf nimmt
 * @param buergerrechte   was die Stadtbuerger einer NEU GEGRUENDETEN Stadt im
 *                        Gemeinschaftsgebiet duerfen - nur die Vorgabe, danach stellt
 *                        jeder Stadtbesitzer seine Stadt selbst ein
 */
public record Freistadtwerte(long gruendungspreis, int hoechstzahl,
                             int hoechstzellen, int verkaufsgebuehrProzent,
                             Set<Tat> buergerrechte) {

    public static final String PFAD_GRUENDUNGSPREIS = "freistaedte.gruendungspreis";

    public static final String PFAD_HOECHSTZAHL = "freistaedte.hoechstzahl";

    public static final String PFAD_HOECHSTZELLEN = "freistaedte.hoechstzellen";

    /**
     * Der Pfad der Verkaufsgebuehr.
     *
     * <p><b>Unter {@code freistaedte:} und nicht unter {@code handel:}</b>, obwohl es
     * eine Marktgebuehr auf Spielerhandel ist: Jener Abschnitt gehoert den Kistenshops,
     * und er wird von {@link Shopwerte} gelesen - einer Klasse, die mit Grundstuecken
     * nichts zu tun hat. Die Zahl liegt dort, wo die anderen Freistadtzahlen liegen.
     *
     * <p>Was BEIDE Bereiche teilen, ist die RECHNUNG und nicht der Ort: Abgerundet, auf
     * die ganze Summe, dem abgezogen, der das Geld bekommt - {@link Shopregel#gebuehrAuf}
     * ist die eine Stelle, die das tut.
     */
    public static final String PFAD_VERKAUFSGEBUEHR =
            "freistaedte.verkaufsgebuehr-prozent";

    /**
     * Der Pfad der Vorgabe-Buergerrechte.
     *
     * <p>Eine LISTE in der Datei und keine drei Ja-Nein-Zeilen: Ein viertes Recht ist
     * dann ein Wort mehr und keine neue Zeile in der Vorlage - und ein Betreiber, der
     * die Datei liest, sieht auf einen Blick, was gilt. Dieselbe Bauart wie
     * {@code welten:} und {@code berufe:}, die ebenfalls Aufzaehlungen sind.
     */
    public static final String PFAD_BUERGERRECHTE = "freistaedte.buergerrechte";

    /**
     * KEINE Rechte ab Werk - die sichere Richtung.
     *
     * <p>Eine frische Stadt gibt nichts her, bis ihr Besitzer sich entscheidet. Die
     * andere Vorgabe waere ein Recht, das ein Stadtbesitzer nie gegeben hat und das er
     * erst bemerkt, wenn jemand in seinem Rathaus steht.
     */
    public static final Set<Tat> VORGABE_BUERGERRECHTE = Set.of();

    /**
     * 400.000 Mark - die Zahl aus Plan 6.2.
     *
     * <p>Sie steht dort neben der Preisstaffel der Parzellen, und beide Zahlen gehoeren
     * zusammen: Die sechste Parzelle kostet genau so viel. Das ist die Aussage des Plans
     * - eine Freistadt ist das, was ein Spieler sonst als sechstes Rasterfeld bekaeme,
     * und darum ein Ziel fuer achtzig bis zweihundert Spielstunden statt fuer einen
     * Nachmittag.
     *
     * <p>Die Zahl steht ab heute an ZWEI Stellen, und das ist kein Versehen:
     * {@code PreisstaffelTest} begruendet mit ihr den Grundpreis der Staffel. Beides sind
     * Vorgaben, die ein Betreiber einzeln verstellen darf - gekoppelt waere die
     * Freistadt ploetzlich teurer, weil jemand die Parzellen billiger machen wollte.
     */
    public static final long VORGABE_GRUENDUNGSPREIS = 400_000L;

    /**
     * Eine Stadt je Spieler.
     *
     * <p>Nicht weil mehr unmoeglich waere, sondern weil es nur 399 Felder gibt
     * ({@link Feldgrenze#staedteZahl}) und eine Stadt 256x256 Bloecke ist. Wer zwei
     * haelt, nimmt sie einem anderen weg - und zwei halbleere Staedte sind schlechter
     * als eine volle.
     *
     * <p><b>Als Zahl und nicht als Eins im Programm</b>, genau wie
     * {@code parzellen.hoechstzahl}: Schritt 12 haengt die Raenge daran, und dann gibt
     * ein Recht {@code freimarken.freistaedte.<n>} einem hoeheren Rang mehr. Eine fest
     * eingebaute Eins muesste dafuer erst wieder herausgeholt werden.
     */
    public static final int VORGABE_HOECHSTZAHL = 1;

    /**
     * 256 Zellen - ein ganzes Stadtfeld als EIN Grundstueck.
     *
     * <p>Also ab Werk KEINE Begrenzung, und das ist eine Entscheidung fuer die
     * Preisfreiheit aus Plan 16: Wer eine Stadt kauft, soll sie zuschneiden duerfen, wie
     * er will - auch als ein einziges grosses Anwesen. Die Zahl steht trotzdem da und
     * nicht als fehlende Pruefung: Ein Betreiber, der kleinteilige Staedte will, traegt
     * sie ein, ohne dass jemand das Programm anfasst.
     *
     * <p>Eine 0 heisst ausdruecklich "ohne Grenze" - dieselbe Lesart wie beim
     * Tagesdeckel. Das ist mehr als eine Abkuerzung: Wer die Grenze abschalten will,
     * soll nicht die groesste Zahl suchen muessen, die gerade noch passt.
     */
    public static final int VORGABE_HOECHSTZELLEN = 256;

    /**
     * Mehr Zellen als ein Feld hat, gibt es nicht.
     *
     * <p>Gegen den Vertipper wie {@link #HOECHSTE_ZAHL}: Ein Grundstueck kann nicht
     * groesser sein als die Stadt, in der es liegt.
     */
    public static final int HOECHSTE_ZELLEN = 256;

    /**
     * KEINE Gebuehr ab Werk.
     *
     * <p>Dieselbe Einstellung wie bei allen drei Shopgebuehren, und derselbe Grund (dort
     * im Klartext in der {@code config.yml}): Erst soll der Handel laufen, die Senke
     * kommt spaeter. Eine Gebuehr auf den ersten Grundstueckshandel des Servers waere
     * eine Steuer auf etwas, das es noch nicht gibt.
     */
    public static final int VORGABE_VERKAUFSGEBUEHR = 0;

    /** Dieselbe Obergrenze wie bei allen Geldzahlen - dieselbe Rechnung. */
    public static final long HOECHSTER_BETRAG = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    /**
     * Die groesste sinnvolle Staedtezahl.
     *
     * <p>Gegen den Vertipper, nicht gegen den Betreiber: Wer 399 eintraegt, darf das -
     * mehr Felder gibt es nicht, und bei 400 waere die Zahl ohnehin wirkungslos.
     */
    public static final int HOECHSTE_ZAHL = 399;

    public static final Freistadtwerte VORGABE = new Freistadtwerte(
            VORGABE_GRUENDUNGSPREIS, VORGABE_HOECHSTZAHL, VORGABE_HOECHSTZELLEN,
            VORGABE_VERKAUFSGEBUEHR, VORGABE_BUERGERRECHTE);

    public Freistadtwerte {
        if (gruendungspreis < 0L || gruendungspreis > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Der Gruendungspreis ist "
                    + gruendungspreis + " und liegt nicht zwischen 0 und "
                    + HOECHSTER_BETRAG + ". Eine 0 ist erlaubt und heisst: Wer das Recht"
                    + " hat, gruendet umsonst.");
        }
        if (hoechstzahl < 0 || hoechstzahl > HOECHSTE_ZAHL) {
            throw new IllegalArgumentException("Die Hoechstzahl ist " + hoechstzahl
                    + " und liegt nicht zwischen 0 und " + HOECHSTE_ZAHL
                    + ". Eine 0 ist erlaubt und heisst: Gegruendet wird gar nicht -"
                    + " brauchbar, solange eine Welt noch vorbereitet wird.");
        }
        if (hoechstzellen < 0 || hoechstzellen > HOECHSTE_ZELLEN) {
            throw new IllegalArgumentException("Die Hoechstzahl der Zellen ist "
                    + hoechstzellen + " und liegt nicht zwischen 0 und "
                    + HOECHSTE_ZELLEN + ". Eine 0 ist erlaubt und heisst: ohne Grenze -"
                    + " ein Grundstueck darf dann ein ganzes Feld sein.");
        }
        // DIESELBE Grenze wie bei den Shopgebuehren, und ueber dieselbe Frage: Ueber der
        // Haelfte waere die Gebuehr hoeher als der Erloes, und dann verkauft niemand
        // mehr - eine Steuer, die ihre eigene Grundlage abschafft.
        if (!Shopregel.brauchbareGebuehr(verkaufsgebuehrProzent)) {
            throw new IllegalArgumentException("Die Verkaufsgebuehr ist "
                    + verkaufsgebuehrProzent + " Prozent und liegt nicht zwischen 0 und "
                    + Shopregel.HOECHSTE_GEBUEHR_PROZENT + ".");
        }
        // KEINE Pruefung auf die Zahl der Rechte: Jede Teilmenge der drei ist gueltig,
        // die leere eingeschlossen - sie heisst "Buerger haben nur ihr eigenes
        // Grundstueck". Ein unbekanntes WORT faellt schon beim Lesen der Datei auf, in
        // Tat.ausSchluessel.
        //
        // Unveraenderlich kopieren, wie die Mitbauer in Parzelle: Sonst aendert ein
        // Aufrufer die Vorgabe des Betreibers nachtraeglich, und die naechste gegruendete
        // Stadt bekommt andere Rechte als die vorige.
        buergerrechte = buergerrechte == null ? Set.of() : Set.copyOf(buergerrechte);
    }

    /** Dieselben Werte mit anderem Gruendungspreis. */
    public Freistadtwerte mitGruendungspreis(long anderer) {
        return new Freistadtwerte(anderer, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte);
    }

    /** Dieselben Werte mit anderer Hoechstzahl. */
    public Freistadtwerte mitHoechstzahl(int andere) {
        return new Freistadtwerte(gruendungspreis, andere, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte);
    }

    /** Dieselben Werte mit anderer Zellengrenze. */
    public Freistadtwerte mitHoechstzellen(int andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, andere,
                verkaufsgebuehrProzent, buergerrechte);
    }

    /** Dieselben Werte mit anderer Verkaufsgebuehr. */
    public Freistadtwerte mitVerkaufsgebuehr(int andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen, andere,
                buergerrechte);
    }

    /** Dieselben Werte mit anderen Vorgabe-Buergerrechten. */
    public Freistadtwerte mitBuergerrechten(Set<Tat> andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, andere);
    }
}
