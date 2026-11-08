package de.freimarken.paper;

import de.freimarken.kern.Shopregel;
import de.freimarken.kern.Shopwerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code handel:} aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Mietliste} und {@link Bonusliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen
 * gelassen. Dieser Leser darf nicht werfen - er laeuft beim Start, und ein Wurf hiesse
 * ein Server, der wegen einer Zahl in einer Datei nicht hochkommt.
 *
 * <p><b>Drei Zeilen und zwei Arten von Zeile.</b> Die zwei Gebuehren sind Zahlen mit
 * einer Schranke, das Bundwort ist Text. Dass beides in EINEM Leser steht, ist kein
 * Zufall: Es ist derselbe Abschnitt der Datei, und zwei Leser darauf haetten zwei
 * Berichtszeilen, die gleich anfangen - und zwei Zeilen, die gleich anfangen, liest
 * niemand als zwei.
 *
 * <p><b>Beide Gebuehren sind vorgegeben 0</b>, und das ist die Entscheidung des Nutzers
 * vom 08.11.2026: "erstmal keine gebuehr bei spielershops, aber anpassbar, markt gebuehr
 * auch". Deshalb steht hier auch kein Wort, das eine Gebuehr empfiehlt - die
 * Rechenprobe nennt die Zahlen und sonst nichts.
 */
final class Shopliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Shopwerte.PFAD_SPIELER, Shopwerte.PFAD_BUND, Shopwerte.PFAD_WORT);

    record Ergebnis(Shopwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Shopliste() {
    }

    /** Die drei Zeilen aus dem Abschnitt {@code handel:}. */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Shopwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        int spieler = gebuehr(wurzel, Shopwerte.PFAD_SPIELER, Shopwerte.VORGABE_SPIELER,
                klagen, bericht);
        int bund = gebuehr(wurzel, Shopwerte.PFAD_BUND, Shopwerte.VORGABE_BUND, klagen,
                bericht);
        String wort = bundwort(wurzel, klagen, bericht);

        Shopwerte werte = new Shopwerte(spieler, bund, wort);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Ladenmiete: Die einzige Zeile, die ein Betreiber
     * wirklich liest, ist die mit seinen eigenen Zahlen darin. Sie rechnet an einem
     * Beispiel vor, was die Gebuehr bedeutet - 0 Prozent ist eine Zahl, die niemandem
     * etwas sagt, "von 1.000 Mark bleiben 1.000" schon.
     */
    private static String rechenprobe(Shopwerte werte) {
        long beispiel = 1_000L;
        Shopregel.Handel spieler = Shopregel.rechne(1, beispiel, 1,
                werte.spielerProzent());
        Shopregel.Handel bund = Shopregel.rechne(1, beispiel, 1, werte.bundProzent());
        return "Handel: Von " + Sprache.deutscheZahl(beispiel) + " Mark Umsatz bleiben"
                + " dem Verkaeufer bei einem Spielershop "
                + Sprache.deutscheZahl(spieler.erloes()) + " ("
                + werte.spielerProzent() + " Prozent Gebuehr), bei einem Bundshop "
                + Sprache.deutscheZahl(bund.erloes()) + " (" + werte.bundProzent()
                + " Prozent). Bundshops heissen auf dem Schild "
                + (werte.bundWort().isBlank()
                        ? "gar nicht - ueber ein Schild sind sie nicht anzulegen."
                        : "\"" + werte.bundWort() + "\".");
    }

    /**
     * Eine Gebuehr in Prozent - beide Zeilen gehen durch dieselbe Methode.
     *
     * <p>Eine Methode fuer beide und nicht zwei, anders als bei {@code Mietliste.dauer}
     * und {@code ausfaelle}: Dort hat jede Zahl eine eigene Schranke und einen eigenen
     * Grund. Hier ist es dieselbe Schranke, dieselbe Bedeutung und dieselbe Klage - nur
     * ein anderer Pfad. Zwei Methoden daraus zu machen hiesse, die Schranke zweimal
     * hinzuschreiben, und dann aendert jemand eine von beiden.
     *
     * <p>Der LONG wird geprueft und nicht der verkuerzte int - dieselbe Falle wie bei der
     * Mietdauer: Aus {@code (int) 4294967296} wird 0, und das waere zufaellig die
     * Vorgabe. Der Betreiber bekaeme keine Klage und haette eine Gebuehr, die er nicht
     * gesetzt hat.
     */
    private static int gebuehr(ConfigurationSection wurzel, String pfad, int vorgabe,
            List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (!Shopregel.brauchbareGebuehr(wert > Integer.MAX_VALUE
                || wert < Integer.MIN_VALUE ? -1 : (int) wert)) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 0 bis "
                    + Shopregel.HOECHSTE_GEBUEHR_PROZENT + " Prozent. Ueber der Haelfte"
                    + " waere die Gebuehr hoeher als der Erloes des Verkaeufers, und dann"
                    + " verkauft niemand mehr - eine Steuer, die ihre eigene Grundlage"
                    + " abschafft. " + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return (int) wert;
    }

    /**
     * Das Bundwort: Text, leer erlaubt.
     *
     * <p>Nach dem Muster von {@code Wirtschaftsliste.werbung} - roh gelesen, am Typ
     * geprueft, Klage statt Wurf. Die einzige Falle ist eine Zeile, die gar kein Text
     * ist: Eine Liste kommt als etwas an, dessen {@code toString} auf dem Schild
     * verglichen wuerde.
     *
     * <p><b>Leer ist eine Einstellung und keine Stoerung.</b> Ein Server ohne Bundshops
     * braucht kein Wort dafuer, und dann sind sie ueber ein Schild nicht anzulegen - das
     * steht im Bericht, damit ein Betreiber es nicht fuer einen Fehler haelt.
     *
     * <p>Die Laengengrenze ist eine Schildzeile: Ein laengeres Wort liesse sich in die
     * erste Zeile gar nicht tippen, und dann waere die Einstellung eine, die nie wirkt -
     * genau der Fall, von dem der Nutzergrundsatz sagt, dass er es in der Datei SAGEN
     * muss.
     */
    private static String bundwort(ConfigurationSection wurzel, List<String> klagen,
            List<String> bericht) {
        String pfad = Shopwerte.PFAD_WORT;
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(Wirtschaftsliste.fehltText(pfad, "\"" + Shopwerte.VORGABE_WORT
                    + "\""));
            return Shopwerte.VORGABE_WORT;
        }
        if (!(roh instanceof String text)) {
            klagen.add(pfad + " ist kein Text (" + Wirtschaftsliste.wieGetippt(roh)
                    + ") - verworfen. Es bleibt bei \"" + Shopwerte.VORGABE_WORT
                    + "\".");
            return Shopwerte.VORGABE_WORT;
        }
        String wort = text.trim();
        if (wort.isEmpty()) {
            bericht.add(pfad + " ist leer: Bundshops sind ueber ein Schild nicht"
                    + " anzulegen. Das ist erlaubt - wer welche will, traegt hier ein"
                    + " Wort ein.");
            return "";
        }
        if (wort.length() > Shopwerte.LAENGSTES_WORT) {
            klagen.add(pfad + " ist \"" + wort + "\" und damit " + wort.length()
                    + " Zeichen lang - verworfen. Auf eine Schildzeile passen "
                    + Shopwerte.LAENGSTES_WORT + ", ein laengeres Wort liesse sich also"
                    + " nie eintippen und die Einstellung wirkte nie. Es bleibt bei \""
                    + Shopwerte.VORGABE_WORT + "\".");
            return Shopwerte.VORGABE_WORT;
        }
        return wort;
    }

    /**
     * Die Zeile fuer das Startprotokoll.
     *
     * <p>Getrennt von {@link #rechenprobe}, und dasselbe Maass wie bei
     * {@code Messliste.startzeile}: Diese Zeile liest ein Betreiber bei JEDEM Start, die
     * Rechenprobe nur, wenn er die Datei anfasst. Darum nennt sie, was den Betrieb
     * bestimmt - ob eine Gebuehr erhoben wird und wie Bundshops heissen -, und rechnet
     * nicht vor.
     *
     * <p><b>Jeder Wert, den dieser Satz verspricht, steht auch darin.</b> Das ist der
     * Befund vom Bundesmarkt: Eine halbe Zusage - "es wird eine Gebuehr erhoben" ohne die
     * Zahl - liest sich wie eine Angabe und traegt keine.
     */
    static String startzeile(Shopwerte werte) {
        return (werte.erhebtGebuehr()
                        ? "Marktgebuehr " + werte.spielerProzent()
                                + " Prozent auf Spielershops und " + werte.bundProzent()
                                + " Prozent am Bundesmarkt"
                        : "ohne Marktgebuehr (" + Shopwerte.PFAD_SPIELER + " und "
                                + Shopwerte.PFAD_BUND + " stehen auf 0)")
                + ", Bundshops heissen "
                + (werte.bundWort().isBlank()
                        ? "gar nicht und sind ueber ein Schild nicht anzulegen."
                        : "\"" + werte.bundWort() + "\" in der ersten Schildzeile.");
    }

    private static void nenneFehlende(ConfigurationSection wurzel,
            List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen des"
                    + " Handels fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }
}
