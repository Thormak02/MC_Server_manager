package de.freimarken.kern;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Die Zahlen der Messstelle - woran sie Taetigkeit erkennt.
 *
 * <p>Dieselbe Bauart wie {@link Marktwerte} und {@link Verdienstwerte}: ein Satz, eine
 * Vorgabe im Programm, und zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu
 * werfen. Was die Werte BEDEUTEN, steht in {@link Messregel}; hier stehen nur ihre Grenzen.
 *
 * <p><b>Es gibt keinen Ausschalter.</b> Kein Schluessel setzt die Messstelle still, und das
 * ist dieselbe Entscheidung wie bei der Zahl der ausgesetzten Taktschlaege in Plan 8.8: Ein
 * Schalter dafuer waere einer, mit dem ein Betreiber sich stumm seine eigene Messung
 * wegwirft - und die zweite Zahl ist spaeter die Grundlage einer Rangschwelle, also etwas,
 * das Spieler betrifft. Wem die Messung zu streng ist, setzt
 * {@link #PFAD_VERLANGTE_ARTEN} auf 1: Dann genuegt EIN Lebenszeichen, und gemessen wird
 * noch "hat ueberhaupt etwas getan". Das ist die milde Fassung und nicht das Aus.
 *
 * @param verlangteArten wie viele VERSCHIEDENE Arten von Lebenszeichen ein Taktschlag
 *                       braucht, um als taetig zu zaehlen. Muss zu {@code arten} passen -
 *                       siehe {@link Messregel#brauchbareArtenzahl}
 * @param mindestStrecke wie viele Bloecke ein Spieler im Takt zuruecklegen muss, damit
 *                       {@link Lebenszeichen#BEWEGEN} zaehlt
 * @param arten          welche Arten ueberhaupt zaehlen. Die REIHENFOLGE bleibt die der
 *                       Datei, denn sie ist die Reihenfolge im Bericht
 */
public record Messwerte(int verlangteArten, int mindestStrecke, Set<Lebenszeichen> arten) {

    /** Wie viele verschiedene Lebenszeichen ein taetiger Takt braucht. */
    public static final String PFAD_VERLANGTE_ARTEN = "messstelle.verlangte-arten";

    /** Wie weit ein Spieler kommen muss, damit das Bewegen zaehlt - in Bloecken. */
    public static final String PFAD_STRECKE = "messstelle.mindest-strecke";

    /** Welche Arten von Lebenszeichen zaehlen - eine Liste von Schluesseln. */
    public static final String PFAD_ARTEN = "messstelle.arten";

    /**
     * Zwei verschiedene Arten - die tragende Zahl.
     *
     * <p>Gerechnet gegen den Fall, den es treffen soll: Ein Makro am
     * Pflastersteingenerator gibt genau ein Zeichen ({@link Lebenszeichen#ABBAUEN}), eine
     * Angelfarm ebenso ({@link Lebenszeichen#BENUTZEN}), ein beschwertes Tastenbrett im
     * Startbildschirm keines. Ein Mensch, der denselben Generator abbaut, geht dabei hin
     * und her, raeumt in die Truhe oder setzt einen Block - er kommt auf zwei.
     *
     * <p>Nicht drei: Beim kuerzesten erlaubten Takt von 10 Sekunden sind drei verschiedene
     * Taetigkeiten auch fuer einen Menschen nicht selbstverstaendlich, und die Messung soll
     * nicht von der Taktlaenge abhaengen.
     */
    public static final int VORGABE_VERLANGTE_ARTEN = 2;

    /**
     * Acht Bloecke - weiter als ein Spieler an einer Wand jemals kommt.
     *
     * <p>Gerechnet gegen beide Richtungen: Eine gewackelte Maus und ein beschwerter Taster
     * tragen den Spieler null bis zwei Bloecke weit (Rueckstoss, Rutschen auf Eis), acht
     * sind davon klar getrennt. Nach oben ist es wenig: Wer in einem Takt von 10 Sekunden
     * normal laeuft, kommt gut vierzig Bloecke weit.
     */
    public static final int VORGABE_STRECKE = 8;

    /** Hoechstens so viele Bloecke - darueber waere die Strecke nur noch mit Flug zu
     * schaffen, und das Bewegen zaehlte stumm nie mehr. */
    public static final int HOECHSTE_STRECKE = 1_000;

    /**
     * Sechs der sieben Arten - {@link Lebenszeichen#BENUTZEN} ist ab Werk AUS.
     *
     * <p><b>Warum eine Vorgabe und nicht die leere Liste.</b> Bei der Lohnliste und beim
     * Sortiment ist die leere Liste die sichere Richtung, denn ein geratener Preis macht
     * Geld, das keine Ruecksicherung zurueckholt. Eine fehlende Messung macht nichts
     * kaputt, sie misst nur nicht - und eine Messstelle, die ohne ihre Zeile in der Datei
     * gar nichts zaehlt, waere genau der stumme Ausschalter, den es nicht geben soll.
     *
     * <p><b>Warum das Benutzen fehlt.</b> Es ist keine Art fuer sich, sondern die halbe
     * Art zum Setzen: Ein Block wird mit dem RECHTEN Mausknopf gesetzt, und derselbe
     * Knopfdruck ist das Benutzen. Ein Makro, das Bloecke setzt, gaebe damit ZWEI Zeichen
     * und zaehlte als taetig, obwohl es genau eine Taste wiederholt - und die Vielfalt,
     * auf der die ganze Messung steht ({@link Messregel}), waere an dieser einen Stelle
     * keine. Die uebrigen sechs haben dieses Paar nicht: Graben, Setzen, Kaempfen und
     * Oeffnen sind jedes fuer sich eine Taste, und die Strecke ist keine Taste.
     *
     * <p>Die Art bleibt im Programm und laesst sich in der Datei dazunehmen - wer eine
     * mildere Messung will, hat damit einen Hebel neben
     * {@link #PFAD_VERLANGTE_ARTEN}.
     *
     * <p>{@link LinkedHashSet} und nicht {@code Set.of}: Die Reihenfolge dieser Liste ist
     * die Reihenfolge im Startprotokoll und im Bericht, und {@code Set.of} hat keine
     * zugesagte. Sie waere von Lauf zu Lauf eine andere, und das sieht aus wie eine
     * geaenderte Einstellung.
     */
    public static final Set<Lebenszeichen> VORGABE_ARTEN = new LinkedHashSet<>(
            java.util.List.of(Lebenszeichen.BEWEGEN, Lebenszeichen.ABBAUEN,
                    Lebenszeichen.SETZEN, Lebenszeichen.OEFFNEN, Lebenszeichen.KAEMPFEN,
                    Lebenszeichen.SPRECHEN));

    public static final Messwerte VORGABE = new Messwerte(VORGABE_VERLANGTE_ARTEN,
            VORGABE_STRECKE, VORGABE_ARTEN);

    public Messwerte {
        if (arten == null) {
            throw new IllegalArgumentException("Ohne eine Liste von Arten gibt es keine"
                    + " Messstelle; eine LEERE Liste ist auch nichts, denn dann waere kein"
                    + " Takt jemals taetig");
        }
        // Eine eigene Kopie in der Reihenfolge der Datei. LinkedHashSet und nicht Set.copyOf:
        // copyOf wirft die Reihenfolge weg, und sie ist die Reihenfolge im Bericht.
        arten = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(arten));
        if (arten.contains(null)) {
            throw new IllegalArgumentException("Eine Art in der Liste ist null");
        }
        if (!Messregel.brauchbareArtenzahl(verlangteArten, arten.size())) {
            throw new IllegalArgumentException("Es sind " + verlangteArten
                    + " verschiedene Arten verlangt, aber nur " + arten.size()
                    + " zaehlen ueberhaupt. Damit waere kein Taktschlag jemals taetig, und"
                    + " zwar stumm - eine Null sieht aus wie ein leerer Server.");
        }
        if (!brauchbareStrecke(mindestStrecke)) {
            throw new IllegalArgumentException("Die Mindeststrecke ist " + mindestStrecke
                    + " und liegt nicht zwischen 1 und " + HOECHSTE_STRECKE
                    + ". Eine 0 hiesse, dass auch Stillstehen als Bewegung zaehlt.");
        }
    }

    /** Ob diese Art zaehlt - die Frage, die {@link Messregel} stellt. */
    public boolean zaehlt(Lebenszeichen art) {
        return art != null && arten.contains(art);
    }

    /** Dieselben Werte mit anderer Artenzahl - fuer die Proben und den Leser. */
    public Messwerte mitVerlangtenArten(int andere) {
        return new Messwerte(andere, mindestStrecke, arten);
    }

    /** Dieselben Werte mit anderer Mindeststrecke. */
    public Messwerte mitStrecke(int andere) {
        return new Messwerte(verlangteArten, andere, arten);
    }

    /**
     * Dieselben Werte mit einer anderen Artenliste.
     *
     * <p>Nimmt die Artenzahl MIT, weil sie sonst nicht passen koennte: Wer von sieben Arten
     * auf eine heruntergeht, hat mit {@code verlangteArten = 2} einen Satz, den der
     * Konstruktor verweigert. Der Aufrufer soll das nicht in zwei Schritten tun muessen,
     * von denen der erste wirft.
     */
    public Messwerte mitArten(Set<Lebenszeichen> andere, int verlangt) {
        return new Messwerte(verlangt, mindestStrecke, andere);
    }

    public static boolean brauchbareStrecke(int bloecke) {
        return bloecke >= 1 && bloecke <= HOECHSTE_STRECKE;
    }
}
