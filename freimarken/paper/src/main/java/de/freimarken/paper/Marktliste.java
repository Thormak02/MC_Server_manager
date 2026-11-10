package de.freimarken.paper;

import de.freimarken.kern.Marktwerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Bundesmarkt aus der {@code config.yml} - den Tagesdeckel.
 *
 * <p>Nach demselben Muster wie {@link Verdienstliste}: reine Dateiarbeit, gesammelte
 * Klagen statt Ausnahmen, und der Rueckfall steht im Programm.
 *
 * <p><b>Das SORTIMENT stand hier bis zum 10.11.2026</b> - eine Tafel
 * {@code bundesmarkt.ankauf} mit je Material einem Ankaufpreis in Pfennigen, samt Pruefung
 * gegen {@link org.bukkit.Material} und einer Klage je unbrauchbarer Zeile. Sie ist
 * gefallen, und die Begruendung ist ein Satz des Betreibers: <b>"es kann ja nur ge- und
 * verkauft werden, wofuer es ein Schild gibt."</b> Ein zweites Tor hinter dem Schild
 * verbot ihm genau das, was er wollte - alles freizugeben.
 *
 * <p>Damit ist dieser Leser fast leer, und das ist richtig so: Was der Bund ankauft, steht
 * nicht mehr in einer Datei, sondern an seinen Schildern. Was der BERICHT davon noch
 * nennen kann, nennt die Startzeile der Kistenshops - sie zaehlt die Staende, und sie
 * kann es erst, wenn die Shops gelesen sind.
 */
final class Marktliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Marktwerte.PFAD_TAGESDECKEL);

    /**
     * Der Abschnitt, den es nicht mehr gibt - und ueber den geklagt wird.
     *
     * <p>Eine vorhandene {@code config.yml} wird beim Update NIE ueberschrieben: Wer von
     * einer aelteren Fassung kommt, hat {@code bundesmarkt.ankauf} mit sechsundfuenfzig
     * Zeilen in seiner Datei. Ohne diese Klage sagt ihm niemand, dass sie nicht mehr
     * gelesen wird - er stellt dort Preise ein, wartet auf ihre Wirkung und sucht den
     * Fehler ueberall sonst.
     *
     * <p>Dasselbe Mittel wie {@code Shopliste.nenneAlte} und derselbe Nutzergrundsatz:
     * Eine Zahl, die in der Datei steht und nicht wirkt, ist die schlimmste Art
     * Einstellung.
     */
    private static final String ALTE_TAFEL = "bundesmarkt.ankauf";

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Marktwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Marktliste() {
    }

    /**
     * Der ganze Abschnitt {@code bundesmarkt:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Marktwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        nenneAlteTafel(wurzel, klagen);
        return new Ergebnis(new Marktwerte(deckel(wurzel, klagen, bericht)), klagen,
                bericht);
    }

    /**
     * Der Tagesdeckel - ein Geldwert, also mit Sprungbestaetigung.
     *
     * <p>Geteilt mit {@link Wirtschaftsliste#betrag}, wie der Verdienstdeckel: Wer ihn um
     * mehr als Faktor 2 verstellt, muss das bestaetigen. Ein Deckel, der aus einem
     * Vertipper fuenfzigtausend wird, ist kein Deckel mehr - und das Geld dahinter liegt
     * binnen Minuten auf vielen Konten.
     *
     * <p><b>Eine 0 ist seit dem 10.11.2026 erlaubt und heisst: kein Deckel.</b> Vorher
     * wurde sie verworfen, mit der Begruendung "eine 0 hiesse, niemand kann verkaufen -
     * wer den Ankauf abschalten will, laesst die Liste leer". Die Liste gibt es nicht
     * mehr; abgeschaltet wird der Ankauf dadurch, dass kein Schild dafuer steht. Damit
     * ist die 0 frei fuer die Einstellung, die der Betreiber braucht.
     *
     * <p><b>Der Sprungschutz greift bei der 0 nicht</b>, und das ist eine Folge und kein
     * Versehen: {@code Wirtschaftsliste.betrag} vergleicht gegen die ausgelieferten
     * 5.000, und von 5.000 auf 0 ist mehr als Faktor 2 - also verlangt er eine
     * Bestaetigung. Wer den Deckel abschaltet, traegt sie ein; das ist dieselbe Huerde
     * wie bei jeder anderen grossen Aenderung eines Geldwertes, und sie ist hier
     * angebracht: Ohne Deckel ist der Ankauf des Bundes eine Quelle ohne Grenze.
     */
    private static long deckel(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        long gelesen = Wirtschaftsliste.betrag(wurzel, Marktwerte.PFAD_TAGESDECKEL,
                "markt-tagesdeckel", Marktwerte.VORGABE_TAGESDECKEL,
                Marktwerte.HOECHSTER_TAGESDECKEL, "Mark", klagen, bericht);
        if (!Marktwerte.brauchbarerDeckel(gelesen)) {
            klagen.add(Marktwerte.PFAD_TAGESDECKEL + " ist " + gelesen
                    + " - verworfen. Erlaubt ist " + Marktwerte.KEIN_DECKEL + " bis "
                    + Marktwerte.HOECHSTER_TAGESDECKEL + ", und eine "
                    + Marktwerte.KEIN_DECKEL + " heisst: kein Deckel. "
                    + Wirtschaftsliste.esBleibt(Marktwerte.VORGABE_TAGESDECKEL));
            return Marktwerte.VORGABE_TAGESDECKEL;
        }
        if (gelesen == Marktwerte.KEIN_DECKEL) {
            // Ein BERICHT und keine Klage: Es ist eine Einstellung. Aber eine, die man
            // lesen soll - der Ankauf des Bundes ist damit eine Geldquelle ohne Grenze,
            // und das ist eine Entscheidung und kein Zustand, in den man rutscht.
            bericht.add(Marktwerte.PFAD_TAGESDECKEL + " ist "
                    + Marktwerte.KEIN_DECKEL + ": KEIN Tagesdeckel. Wer an einem"
                    + " Bundschild verkauft, kann beliebig viel Geld am Tag bekommen -"
                    + " begrenzt nur dadurch, was er zusammentraegt und welche Schilder"
                    + " stehen.");
        }
        return gelesen;
    }

    /**
     * Klagt ueber die Tafel, die es nicht mehr gibt - siehe {@link #ALTE_TAFEL}.
     *
     * <p>Eine KLAGE und kein Bericht: Der Betreiber hat dort Preise eingestellt, und sie
     * wirken nicht mehr. Das ist derselbe Rang wie eine verworfene Zahl, denn die Folge
     * ist dieselbe - der Server tut etwas anderes, als in der Datei steht.
     */
    private static void nenneAlteTafel(ConfigurationSection wurzel,
            List<String> klagen) {
        if (!wurzel.contains(ALTE_TAFEL, true)) {
            return;
        }
        klagen.add(ALTE_TAFEL + " wird nicht mehr gelesen. Seit dem 10.11.2026 steht der"
                + " Preis eines Bundstandes auf seinem SCHILD, wie bei jedem anderen"
                + " Laden - was der Bund handelt, entscheiden seine Schilder und keine"
                + " Liste. Loesche diesen Abschnitt; /markt zeigt dir, welche Staende"
                + " stehen.");
    }

    /**
     * Nennt jede Marktzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste} oder
     * {@link Verdienstliste}: Drei Berichte, die dieselbe Zahl verschieden nennen, sind
     * schlimmer als drei Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size()
                    + " Marktzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie erst,"
                    + " wenn sie in der Datei stehen; was das Plugin ohne die Zeile"
                    + " mitbringt, nennt die eigene Zeile zu jedem Schluessel.");
        }
    }
}
