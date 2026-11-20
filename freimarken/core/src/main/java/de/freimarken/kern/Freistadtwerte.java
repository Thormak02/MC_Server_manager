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
 * @param auflageTeleporter ob eine Stadt die Auflage erfuellen muss, damit Fremde mit
 *                          {@code /warp freistadt} hineinkommen
 * @param auflageLadenrecht ob eine Stadt die Auflage erfuellen muss, damit in ihr neue
 *                          Kistenshops entstehen duerfen
 */
public record Freistadtwerte(long gruendungspreis, int hoechstzahl,
                             int hoechstzellen, int verkaufsgebuehrProzent,
                             Set<Tat> buergerrechte, boolean auflageTeleporter,
                             boolean auflageLadenrecht, Verfallswerte verfall,
                             int erstattungProzent, long ladengebuehrAnlage,
                             long ladengebuehrTag) {

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
     * Ob die Auflage den Teleporter gattert.
     *
     * <p>ZWEI Schalter und nicht einer, obwohl es EINE Auflage ist: Ein Betreiber kann
     * den Teleporter freigeben und das Ladenrecht gattern - oder umgekehrt. Mit einem
     * Schalter waere jede Entscheidung eine doppelte, und wer nur eine der beiden
     * Wirkungen will, bekaeme die andere dazu.
     *
     * <p>Die Auflage selbst ist trotzdem EINE Regel, an EINER Stelle
     * ({@code Freistadtbuch.auflage}) - diese Schalter sagen nur, WO sie wirkt.
     */
    public static final String PFAD_AUFLAGE_TELEPORTER =
            "freistaedte.auflage.teleporter";

    /** Was der Besitzer beim Ende seiner Stadt zurueckbekommt, in Prozent. */
    public static final String PFAD_ERSTATTUNG = "freistaedte.erstattung-prozent";

    /** Was ein neuer Kistenshop EINMALIG an die Stadtkasse zahlt. */
    public static final String PFAD_LADENGEBUEHR_ANLAGE =
            "freistaedte.ladengebuehr.anlage";

    /** Was ein Kistenshop TAEGLICH an die Stadtkasse zahlt. */
    public static final String PFAD_LADENGEBUEHR_TAG = "freistaedte.ladengebuehr.tag";

    /**
     * Die Haelfte zurueck.
     *
     * <p><b>Nicht alles und nicht nichts</b>, und beides mit Grund: Die ganze Summe
     * machte das Gruenden kostenlos - wer ein Feld ein Jahr blockiert und dann aufgibt,
     * haette nichts bezahlt, und 400.000 Mark waeren keine Entscheidung mehr, sondern
     * ein Pfand. Nichts dagegen machte das Aufgeben unmoeglich: Wer eine Stadt nicht
     * mehr will, liesse sie lieber verfallen, und genau die Geisterstadt soll
     * verschwinden.
     *
     * <p>Gerechnet wird vom EINGESETZTEN Preis der Zeile und nicht vom heutigen aus der
     * Datei: Wer fuer 400.000 gegruendet hat, soll nicht darunter leiden, dass der
     * Betreiber den Preis seitdem gesenkt hat.
     */
    public static final int VORGABE_ERSTATTUNG = 50;

    /**
     * 250 Mark fuer einen neuen Laden in einer Stadt.
     *
     * <p><b>Nicht 0</b>, obwohl eine 0 die bequemere Vorgabe waere: Die Stadtkasse ist
     * seit Teil E1 gebaut, und ihre zweite Einnahme - die Marktgebuehr - liegt ab Werk
     * bei 0 Prozent. Mit zwei Nullen waere sie eine Kasse, in die nie etwas fliesst,
     * und ein Betreiber, der sie im Spiel ansieht, hielte sie fuer kaputt. 250 Mark
     * sind wenig gegen die Einnahmen eines Shops und machen den Weg sichtbar.
     *
     * <p>Wer sie nicht will, schreibt 0 - dann faellt sie nicht, und der Startbericht
     * nennt es.
     */
    public static final long VORGABE_LADENGEBUEHR_ANLAGE = 250L;

    /**
     * Taeglich NICHTS, und das ist die Vorgabe mit Bedacht.
     *
     * <p>Eine Tagesgebuehr ist die STAERKERE Senke, und sie hat eine Folge, die eine
     * einmalige nicht hat: Wer sie nicht zahlen kann, verliert seinen Laden. Ob ein
     * Server das will, entscheidet sein Betreiber und nicht diese Vorgabe - und eine
     * Zahl ueber 0 hier naehme Spielern Laeden, von denen der Betreiber nie gelesen
     * hat.
     */
    public static final long VORGABE_LADENGEBUEHR_TAG = 0L;

    /** Ob die Auflage das Anlegen neuer Kistenshops gattert. */
    public static final String PFAD_AUFLAGE_LADENRECHT =
            "freistaedte.auflage.ladenrecht";

    /**
     * DIE AUFLAGE GILT ab Werk - beide Male.
     *
     * <p>So steht es in Plan 16, und der Zweck ist dort genannt: Geisterstaedte, die
     * Flaeche blockieren. Eine Vorgabe von {@code false} haette die ganze Regel gebaut
     * und ausgeschaltet geliefert - gruen geprueft und im Spiel wirkungslos.
     *
     * <p><b>Die Folge ist rueckwirkend und soll das sein:</b> Die Auflage ist eine
     * LAUFENDE Bedingung und keine Vorgabe beim Gruenden. Eine Stadt, die ihr letztes
     * vergebenes Grundstueck zurueckkauft oder ihre Regeln loescht, verliert den
     * Teleporter wieder - und eine Stadt, die es heute schon gibt, hat ihn erst, wenn
     * sie Regeln setzt. Eine Auflage, die nur beim Gruenden geprueft wird, waere nach
     * dem ersten Verkauf stumm.
     *
     * <p><b>Seit Teil E3b ist diese Folge doppelt so gross</b>, und wer hier ueber die
     * Vorgabe entscheidet, muss es wissen: Dieselbe Bedingung gattert auch das
     * LADENRECHT. Jede heute bestehende Stadt ohne Regeltext verliert damit nicht nur
     * ihren Teleporter, sondern auch die Moeglichkeit, dass in ihr ein NEUER
     * Kistenshop entsteht. Die bestehenden handeln weiter - eine Einstellung darf
     * keine Laeden loeschen, deren Ware Spielern gehoert.
     */
    public static final boolean VORGABE_AUFLAGE = true;

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
     * {@code parzellen.hoechstzahl} - und seit Schritt 12 Teil H haengt der Rang daran:
     * {@code freimarken.freistaedte.<n>} hebt diese Zahl, {@code Rangzahl} nimmt die
     * hoechste aus Recht und Datei. Diese Zahl ist damit die UNTERGRENZE fuer jeden.
     *
     * <p><b>Eine 0 schliesst das Gruenden nur zusammen mit der Leiter</b>: Vergibt eine
     * Rangstufe {@code freistaedte: 1}, gilt die Eins aus dem Recht, und die 0 hier wirkt
     * nicht. Der Startlauf sagt genau das in einer eigenen Zeile
     * ({@code Rangliste.wirkungslose}) - sonst waere es die Art Falle, bei der zwei
     * Zeilen in zwei Abschnitten jede fuer sich richtig sind.
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
            VORGABE_VERKAUFSGEBUEHR, VORGABE_BUERGERRECHTE, VORGABE_AUFLAGE,
            VORGABE_AUFLAGE, Verfallswerte.VORGABE, VORGABE_ERSTATTUNG,
            VORGABE_LADENGEBUEHR_ANLAGE, VORGABE_LADENGEBUEHR_TAG);

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
        // PFLICHT und nicht NULL-faehig: Ein fehlendes Satzstueck waere eine
        // Verfallsrechnung, die beim ersten Takt mit einer NullPointerException stirbt -
        // und zwar im Minutentakt, also fuer alle Spieler zugleich. Wer den Verfall
        // nicht will, nimmt Verfallswerte.VORGABE.mitAn(false).
        if (verfall == null) {
            throw new IllegalArgumentException("Ohne Verfallswerte gibt es keine Frist -"
                    + " wer den Verfall abschalten will, nimmt mitAn(false)");
        }
        // DIE ERSTATTUNG bis 100 Prozent, und beide Enden sind erlaubt: 0 heisst "nichts
        // zurueck", 100 "alles zurueck". Darueber waere es ein Geschaeft - wer gruendet
        // und aufgibt, machte Gewinn, und 400.000 Mark waeren eine Geldquelle.
        if (erstattungProzent < 0 || erstattungProzent > 100) {
            throw new IllegalArgumentException("Die Erstattung ist " + erstattungProzent
                    + " Prozent und liegt nicht zwischen 0 und 100. Ueber 100 waere das"
                    + " Gruenden eine Geldquelle: Wer gruendet und aufgibt, machte"
                    + " Gewinn.");
        }
        for (long gebuehr : new long[] {ladengebuehrAnlage, ladengebuehrTag}) {
            if (gebuehr < 0L || gebuehr > HOECHSTER_BETRAG) {
                throw new IllegalArgumentException("Eine Ladengebuehr ist " + gebuehr
                        + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                        + ". Eine 0 ist erlaubt und heisst: Sie faellt nicht.");
            }
        }
    }

    /** Dieselben Werte mit anderem Gruendungspreis. */
    public Freistadtwerte mitGruendungspreis(long anderer) {
        return new Freistadtwerte(anderer, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderer Hoechstzahl. */
    public Freistadtwerte mitHoechstzahl(int andere) {
        return new Freistadtwerte(gruendungspreis, andere, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderer Zellengrenze. */
    public Freistadtwerte mitHoechstzellen(int andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, andere,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderer Verkaufsgebuehr. */
    public Freistadtwerte mitVerkaufsgebuehr(int andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen, andere,
                buergerrechte, auflageTeleporter, auflageLadenrecht, verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderen Vorgabe-Buergerrechten. */
    public Freistadtwerte mitBuergerrechten(Set<Tat> andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, andere, auflageTeleporter, auflageLadenrecht,
                verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderem Teleporter-Gatter. */
    public Freistadtwerte mitAuflageTeleporter(boolean andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, andere, auflageLadenrecht,
                verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderem Erstattungsanteil. */
    public Freistadtwerte mitErstattung(int andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, verfall, andere, ladengebuehrAnlage,
                ladengebuehrTag);
    }

    /** Dieselben Werte mit anderen Ladengebuehren - beide zusammen, weil sie es sind. */
    public Freistadtwerte mitLadengebuehren(long anlage, long tag) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, verfall, erstattungProzent, anlage, tag);
    }

    /** Dieselben Werte mit anderen Verfallsfristen. */
    public Freistadtwerte mitVerfall(Verfallswerte andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter,
                auflageLadenrecht, andere, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }

    /** Dieselben Werte mit anderem Ladenrecht-Gatter. */
    public Freistadtwerte mitAuflageLadenrecht(boolean andere) {
        return new Freistadtwerte(gruendungspreis, hoechstzahl, hoechstzellen,
                verkaufsgebuehrProzent, buergerrechte, auflageTeleporter, andere,
                verfall, erstattungProzent,
                ladengebuehrAnlage, ladengebuehrTag);
    }
}
