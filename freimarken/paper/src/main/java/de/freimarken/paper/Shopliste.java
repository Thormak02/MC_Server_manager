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
 * <p><b>Vier Zeilen und zwei Arten von Zeile.</b> Die drei Gebuehren sind Zahlen mit
 * einer Schranke, das Bundwort ist Text. Dass beides in EINEM Leser steht, ist kein
 * Zufall: Es ist derselbe Abschnitt der Datei, und zwei Leser darauf haetten zwei
 * Berichtszeilen, die gleich anfangen - und zwei Zeilen, die gleich anfangen, liest
 * niemand als zwei.
 *
 * <p><b>Alle Gebuehren sind vorgegeben 0</b>, und das ist die Entscheidung des Nutzers
 * vom 08.11.2026: "erstmal keine gebuehr bei spielershops, aber anpassbar, markt gebuehr
 * auch". Deshalb steht hier auch kein Wort, das eine Gebuehr empfiehlt - die
 * Rechenprobe nennt die Zahlen und sonst nichts.
 *
 * <p><b>Je LAGE ein Schluessel</b> [entschieden 08.11.2026]: Auch WANN eine Gebuehr
 * faellt, ist eine Einstellung und nicht eine Entscheidung des Programms. Die zwei
 * Schluessel von vorher - einer fuer Spielershops, einer fuer den Bund - konnten nicht
 * sagen, ob die Gebuehr beim Kauf oder beim Verkauf greift; die {@code config.yml} sagte
 * "beim Verkauf", der Javadoc von {@link Shopwerte} sagte "bei jedem Handel". Jetzt sagt
 * es der Betreiber, und {@link #nenneAlte} weist ihn auf die alten Zeilen hin, damit
 * keine Zahl in der Datei steht, die niemand mehr liest.
 */
final class Shopliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Shopwerte.PFAD_SPIELER_KAUF, Shopwerte.PFAD_SPIELER_VERKAUF,
            Shopwerte.PFAD_BUND_VERKAUF, Shopwerte.PFAD_WORT,
            Shopwerte.PFAD_ZEILEN);

    /**
     * Die Zeilen von vor dem 08.11.2026 - sie werden nicht mehr gelesen.
     *
     * <p>Eine Zahl, die in der Datei steht und nicht wirkt, ist die schlimmste Art
     * Einstellung: Der Betreiber stellt sie ein, wartet auf ihre Wirkung und sucht den
     * Fehler ueberall sonst. Darum klagt der Leser darueber, statt sie zu uebergehen -
     * derselbe Nutzergrundsatz wie bei einem halb umgezogenen Schluessel.
     */
    private static final List<String> ALTE = List.of(
            "handel.marktgebuehr-spieler-prozent", "handel.marktgebuehr-bund-prozent");

    record Ergebnis(Shopwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Shopliste() {
    }

    /** Die vier Zeilen aus dem Abschnitt {@code handel:}. */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Shopwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        nenneAlte(wurzel, klagen);

        int spielerKauf = gebuehr(wurzel, Shopwerte.PFAD_SPIELER_KAUF,
                Shopwerte.VORGABE_SPIELER_KAUF, klagen, bericht);
        int spielerVerkauf = gebuehr(wurzel, Shopwerte.PFAD_SPIELER_VERKAUF,
                Shopwerte.VORGABE_SPIELER_VERKAUF, klagen, bericht);
        int bundVerkauf = gebuehr(wurzel, Shopwerte.PFAD_BUND_VERKAUF,
                Shopwerte.VORGABE_BUND_VERKAUF, klagen, bericht);
        String wort = bundwort(wurzel, klagen, bericht);
        int zeilen = zeilenJeSeite(wurzel, klagen, bericht);

        Shopwerte werte = new Shopwerte(spielerKauf, spielerVerkauf, bundVerkauf, wort,
                zeilen);
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
     *
     * <p><b>Und sie nennt, WER zahlt.</b> Das ist der Unterschied der drei Lagen: Beim
     * Kauf am Spielershop zahlt der BESITZER die Gebuehr, beim Verkauf der KUNDE. Eine
     * Zeile, die nur Prozente nennt, liest sich wie eine Zahl und nicht wie eine
     * Entscheidung.
     */
    private static String rechenprobe(Shopwerte werte) {
        long beispiel = 1_000L;
        return "Handel: Von " + Sprache.deutscheZahl(beispiel) + " Mark Umsatz bleiben"
                + " dem, der das Geld bekommt, beim Kauf an einem Spielershop "
                + bleibt(beispiel, werte.spielershopKauf()) + " (das zahlt der"
                + " Besitzer), beim Verkauf an einen Spielershop "
                + bleibt(beispiel, werte.spielershopVerkauf()) + " (das zahlt der Kunde)"
                + " und beim Verkauf an einen Bundshop "
                + bleibt(beispiel, werte.bundshopVerkauf()) + ". Beim Kauf an einem"
                + " Bundshop faellt keine Gebuehr - dort bekommt niemand Geld, dem sie"
                + " abzuziehen waere. Bundshops heissen auf dem Schild "
                + (werte.bundWort().isBlank()
                        ? "gar nicht - ueber ein Schild sind sie nicht anzulegen."
                        : "\"" + werte.bundWort() + "\".");
    }

    /** Was von einem Umsatz bleibt, samt Satz in Klammern. */
    private static String bleibt(long umsatz, int prozent) {
        Shopregel.Handel handel = Shopregel.rechne(1, umsatz, 1, prozent);
        return Sprache.deutscheZahl(handel.erloes()) + " (" + prozent + " Prozent"
                + " Gebuehr)";
    }

    /**
     * Eine Gebuehr in Prozent - alle drei Zeilen gehen durch dieselbe Methode.
     *
     * <p>Eine Methode fuer alle und nicht drei, anders als bei {@code Mietliste.dauer}
     * und {@code ausfaelle}: Dort hat jede Zahl eine eigene Schranke und einen eigenen
     * Grund. Hier ist es dieselbe Schranke, dieselbe Bedeutung und dieselbe Klage - nur
     * ein anderer Pfad. Drei Methoden daraus zu machen hiesse, die Schranke dreimal
     * hinzuschreiben, und dann aendert jemand eine davon.
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
    /**
     * Wie viele Shops eine Seite von {@code /shop liste} zeigt.
     *
     * <p>KLAGE statt Wurf, wie bei jeder Zahl dieser Liste: Der Satz
     * {@link Shopwerte} wirft bei einer unbrauchbaren Zahl, und das ist dort richtig -
     * ein Shopwerte mit 0 Zeilen je Seite darf es nicht geben. Hier aber laeuft der
     * Start, und ein Server, der wegen einer Zahl in einer Datei nicht hochkommt, ist
     * die schlechtere Antwort als einer, der die Vorgabe nimmt und es sagt.
     */
    private static int zeilenJeSeite(ConfigurationSection wurzel, List<String> klagen,
            List<String> bericht) {
        String pfad = Shopwerte.PFAD_ZEILEN;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad,
                Shopwerte.VORGABE_ZEILEN, klagen, bericht);
        if (gelesen == null) {
            return Shopwerte.VORGABE_ZEILEN;
        }
        if (!Shopwerte.brauchbareZeilen(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1 bis "
                    + Shopwerte.HOECHSTE_ZEILEN + " Zeilen je Seite. Eine 0 hiesse:"
                    + " Die Liste zeigt nie etwas, und der Befehl waere stumm"
                    + " abgeschaltet. Es bleibt bei "
                    + Shopwerte.VORGABE_ZEILEN + ".");
            return Shopwerte.VORGABE_ZEILEN;
        }
        return (int) (long) gelesen;
    }

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
                        ? "Marktgebuehr " + werte.spielershopKauf() + "/"
                                + werte.spielershopVerkauf()
                                + " Prozent auf Kauf/Verkauf am Spielershop und "
                                + werte.bundshopVerkauf()
                                + " Prozent beim Verkauf an einen Bundshop"
                        : "ohne Marktgebuehr (" + Shopwerte.PFAD_SPIELER_KAUF + ", "
                                + Shopwerte.PFAD_SPIELER_VERKAUF + " und "
                                + Shopwerte.PFAD_BUND_VERKAUF + " stehen auf 0)")
                + ", eine Seite von /shop liste zeigt " + werte.zeilenJeSeite()
                + " Shops, Bundshops heissen "
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

    /**
     * Klagt ueber jede Zeile, die es nicht mehr gibt - siehe {@link #ALTE}.
     *
     * <p>Eine KLAGE und kein Bericht: Der Betreiber hat dort eine Zahl eingestellt, und
     * sie wirkt nicht mehr. Das ist derselbe Rang wie eine verworfene Zahl, denn die
     * Folge ist dieselbe - der Server tut etwas anderes, als in der Datei steht.
     */
    private static void nenneAlte(ConfigurationSection wurzel, List<String> klagen) {
        for (String alt : ALTE) {
            if (wurzel.contains(alt, true)) {
                klagen.add(alt + " wird nicht mehr gelesen. Seit dem 08.11.2026 hat"
                        + " jede LAGE ihren eigenen Satz, denn auch WANN eine Gebuehr"
                        + " faellt, soll einstellbar sein: "
                        + Shopwerte.PFAD_SPIELER_KAUF + ", "
                        + Shopwerte.PFAD_SPIELER_VERKAUF + " und "
                        + Shopwerte.PFAD_BUND_VERKAUF + ". Trage deine Zahl dort ein"
                        + " und loesche diese Zeile.");
            }
        }
    }
}
