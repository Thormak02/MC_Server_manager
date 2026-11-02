package de.freimarken.kern;

/**
 * Was eine Parzelle kostet - und was das Zusammenlegen kostet.
 *
 * <p>Die erste Parzelle ist gratis: Wer nur bauen will, zahlt nie etwas. Jede weitere
 * verdoppelt den Preis. Das ist keine Willkuer, sondern die Bremse gegen Flaechenhorten -
 * die zehnte Parzelle kostet dann mehr, als ein Spieler in absehbarer Zeit verdient, und
 * die Marktstadt laeuft nicht voll, waehrend drei Leute alles halten.
 *
 * <p>Die Gebuehr fuers Zusammenlegen steigt mit der Gruppengroesse. Sie ist eine der
 * wenigen Senken, die ausschliesslich diejenigen zahlen, die ohnehin viel besitzen.
 *
 * <p>Ein WERT und keine Sammlung von Konstanten: Beide Betraege kommen seit dem
 * 02.11.2026 aus der Einstellungsdatei (Plan 12a.6 - "alles, was sich sinnvoll anpassen
 * laesst"). Wer rechnet, braucht dafuer ein Objekt, dem man die Zahlen mitgeben kann.
 * Unveraenderlich, damit ein Neuladen mitten in einer Rechnung keine Mischung aus zwei
 * Staenden ergeben kann; gehalten wird es in {@link Wirtschaftsstand}.
 *
 * <p>Alle Betraege in Mark. Reine Rechnung, kein Spielbezug - deshalb hier und nicht im
 * Paper-Teil.
 *
 * @param grundpreis            Preis der zweiten Parzelle; jede weitere verdoppelt
 * @param zusammenlegungJeFeld  Gebuehr je Feld der neuen Gruppe beim Zusammenlegen
 */
public record Preisstaffel(long grundpreis, long zusammenlegungJeFeld) {

    /**
     * Ab so vielen bereits besessenen Parzellen wird nicht weiter verdoppelt.
     *
     * <p>Ohne Deckel laeuft die Verdopplung in den Ueberlauf: 25.000 mal 2 hoch 63 passt in
     * kein {@code long}. Der Deckel ist also keine Spielbalance, sondern Arithmetik - die
     * Balance macht die Obergrenze je Rang, und die liegt weit darunter. Er bleibt deshalb
     * auch im Programm und kommt NICHT in die Einstellungsdatei; stattdessen begrenzt er
     * den erlaubten Grundpreis.
     */
    public static final int MAX_VERDOPPLUNGEN = 40;

    /** Der ausgelieferte Grundpreis - der Wert, gegen den die Datei verglichen wird. */
    public static final long VORGABE_GRUNDPREIS = 25_000L;

    /** Die ausgelieferte Zusammenlegungsgebuehr je Feld. */
    public static final long VORGABE_ZUSAMMENLEGUNG_JE_FELD = 10_000L;

    /**
     * Der hoechste Grundpreis, mit dem die Staffel noch rechnen kann.
     *
     * <p>Gerechnet und nicht getippt: Der Preis der letzten Stufe ist
     * {@code grundpreis << MAX_VERDOPPLUNGEN}, und das muss in einen {@code long} passen.
     * Daruber waere der Preis NEGATIV - und ein negativer Preis ist eine Gutschrift beim
     * Kauf. Das ist der Grund, warum diese Grenze keine Geschmacksfrage ist.
     *
     * <p>Er deckelt die EINZELNE Stufe und nicht die Summe. Die Summe ueber viele Stufen
     * ist ein Vielfaches davon und sprengt den {@code long} weit innerhalb dieser Grenze -
     * bei 349.526 Mark Grundpreis schon ab 64 Parzellen. Enger rechnen hilft nicht: Die
     * Summe waechst mit jeder weiteren Parzelle, eine feste Grenze kann es also fuer
     * jede Anzahl nicht geben. Deshalb faengt {@link #gesamtpreisFuer(int)} den Ueberlauf
     * selbst.
     */
    public static final long HOECHSTER_GRUNDPREIS = Long.MAX_VALUE >> MAX_VERDOPPLUNGEN;

    /**
     * Die hoechste Gebuehr je Feld, mit der die Rechnung nicht ueberlaeuft.
     *
     * <p>Die Gebuehr wird mit der Felderzahl multipliziert, und die kappt
     * {@code ParzellenBefehle.mitZuschlag} bei {@link Integer#MAX_VALUE}. Mehr als dieser
     * Betrag je Feld koennte das Produkt also ueber den {@code long} treiben.
     */
    public static final long HOECHSTE_GEBUEHR = Long.MAX_VALUE / Integer.MAX_VALUE;

    /** Die ausgelieferte Staffel - der Rueckfall, wenn die Datei nichts Brauchbares sagt. */
    public static final Preisstaffel VORGABE =
            new Preisstaffel(VORGABE_GRUNDPREIS, VORGABE_ZUSAMMENLEGUNG_JE_FELD);

    public Preisstaffel {
        if (!brauchbarerGrundpreis(grundpreis)) {
            throw new IllegalArgumentException("Grundpreis " + grundpreis
                    + " liegt nicht zwischen 0 und " + HOECHSTER_GRUNDPREIS);
        }
        if (!brauchbareGebuehr(zusammenlegungJeFeld)) {
            throw new IllegalArgumentException("Zusammenlegungsgebuehr "
                    + zusammenlegungJeFeld + " liegt nicht zwischen 0 und "
                    + HOECHSTE_GEBUEHR);
        }
    }

    /**
     * Ob aus dieser Zahl ein Grundpreis werden kann.
     *
     * <p>Oeffentlich, damit die Einstellungsdatei an derselben Regel geprueft wird, nach
     * der hier gerechnet wird - wie bei {@link Grenzregel#brauchbar}. Sonst prueft das
     * Lesen gegen eine Grenze und das Rechnen gegen eine andere, und der Konstruktor
     * wirft mitten im Serverstart.
     *
     * <p>0 ist erlaubt und heisst: jede Parzelle gratis. Das ist eine denkbare
     * Serverform und kein Tippfehler - wohl aber ein Sprung ueber den Faktor 2, der
     * deshalb bestaetigt werden muss ({@link Sprungregel}).
     */
    public static boolean brauchbarerGrundpreis(long preis) {
        return preis >= 0 && preis <= HOECHSTER_GRUNDPREIS;
    }

    /** Ob aus dieser Zahl eine Zusammenlegungsgebuehr werden kann; 0 heisst gratis. */
    public static boolean brauchbareGebuehr(long jeFeld) {
        return jeFeld >= 0 && jeFeld <= HOECHSTE_GEBUEHR;
    }

    /**
     * Was die naechste Parzelle kostet.
     *
     * @param bereitsBesessen wie viele Parzellen der Spieler schon hat, nie negativ
     * @return Preis in Mark; 0 fuer die erste
     * @throws IllegalArgumentException bei negativer Eingabe
     */
    public long preisFuerNaechste(int bereitsBesessen) {
        if (bereitsBesessen < 0) {
            throw new IllegalArgumentException(
                    "Anzahl besessener Parzellen kann nicht negativ sein: " + bereitsBesessen);
        }
        if (bereitsBesessen == 0) {
            return 0L;
        }
        int verdopplungen = Math.min(bereitsBesessen - 1, MAX_VERDOPPLUNGEN);
        return grundpreis << verdopplungen;
    }

    /**
     * Was es kostet, eine Gruppe aus {@code felder} Parzellen zusammenzulegen.
     *
     * @param felder Zahl der Felder NACH dem Zusammenlegen, mindestens 2
     * @return Gebuehr in Mark
     * @throws IllegalArgumentException wenn weniger als zwei Felder zusammengelegt werden
     */
    public long zusammenlegungsGebuehr(int felder) {
        if (felder < 2) {
            throw new IllegalArgumentException(
                    "Zusammenlegen braucht mindestens zwei Felder, nicht " + felder);
        }
        return zusammenlegungJeFeld * felder;
    }

    /**
     * Was alle Parzellen zusammen gekostet haben, wenn jemand {@code anzahl} davon haelt.
     *
     * <p>Fuer Anzeige und Wirtschaftsbericht: Was steckt an Mark in dieser Flaeche?
     *
     * <p>Die Summe laeuft bei {@link Long#MAX_VALUE} an und nicht darueber hinaus. Der
     * gerechnete {@link #HOECHSTER_GRUNDPREIS} deckt nur die einzelne Stufe; die Summe
     * waechst mit jeder weiteren Parzelle und kann auch bei einem erlaubten Grundpreis
     * ueber den {@code long} gehen. Ein ueberlaufender Betrag waere NEGATIV, und ein
     * negativer Betrag ist eine Gutschrift - das darf hier nicht entstehen, auch nicht in
     * einer Anzeige. Eine stehengebliebene Hoechstzahl ist als Unsinn sichtbar; eine
     * negative Zahl sieht aus wie ein Guthaben.
     *
     * @param anzahl Zahl der gehaltenen Parzellen, nie negativ
     * @return Summe in Mark, hoechstens {@link Long#MAX_VALUE}
     */
    public long gesamtpreisFuer(int anzahl) {
        if (anzahl < 0) {
            throw new IllegalArgumentException("Anzahl kann nicht negativ sein: " + anzahl);
        }
        long summe = 0L;
        for (int i = 0; i < anzahl; i++) {
            long stufe = preisFuerNaechste(i);
            if (summe > Long.MAX_VALUE - stufe) {
                return Long.MAX_VALUE;
            }
            summe += stufe;
        }
        return summe;
    }

    /**
     * Die Staffel im Klartext: was die naechsten Parzellen kosten.
     *
     * <p>Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und trotzdem
     * falsch ist - 25 statt 25.000 oder 2.500 statt 25.000. Keine Zahlengrenze faengt ihn,
     * beide Zahlen sind plausibel. Wer im Protokoll "2. Parzelle 25" liest, sieht ihn.
     *
     * @param bis wie viele Stufen genannt werden, mindestens eine
     */
    public String alsText(int bis) {
        StringBuilder text = new StringBuilder("1. Parzelle gratis");
        for (int i = 1; i <= Math.max(bis, 1); i++) {
            text.append(" - ").append(i + 1).append(". ").append(preisFuerNaechste(i));
        }
        return text.toString();
    }
}
