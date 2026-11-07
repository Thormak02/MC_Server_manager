package de.freimarken.paper;

import de.freimarken.kern.Ankaufliste;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Verkaufsregel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Bundesmarkt aus der {@code config.yml} - den Tagesdeckel und das Sortiment.
 *
 * <p>Nach demselben Muster wie {@link Verdienstliste}: reine Dateiarbeit, gesammelte
 * Klagen statt Ausnahmen, und der Rueckfall steht im Programm - mit derselben Ausnahme,
 * und sie ist hier dieselbe Entscheidung: <b>Das SORTIMENT hat keinen Rueckfall.</b> Eine
 * Liste im Programm waere eine zweite Abschrift der ausgelieferten Datei, und zwei
 * Abschriften derselben Tabelle laufen auseinander. Fehlt sie, kauft der Bund nichts -
 * die sichere Richtung, denn ein geratener Ankaufpreis macht Geld, das keine
 * Ruecksicherung zurueckholt.
 *
 * <p><b>Jedes Material wird gegen {@link Material} geprueft</b>, mit
 * {@link Material#getMaterial(String)} und NICHT mit {@code matchMaterial}: Jenes fragt
 * die Registry des Servers, und dieser Leser soll ohne Server pruefbar bleiben. Der Preis
 * ist, dass ein Name mit Bereichsvorsatz ({@code minecraft:stone}) nicht erkannt wird;
 * die ausgelieferte Datei schreibt darum nur den nackten Namen, und die Klage sagt es.
 */
final class Marktliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Marktwerte.PFAD_TAGESDECKEL,
            Marktwerte.PFAD_ANKAUF);

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

        long deckel = deckel(wurzel, klagen, bericht);
        Ankaufliste ankauf = ankauf(wurzel.getConfigurationSection(
                Marktwerte.PFAD_ANKAUF), klagen, bericht);
        return new Ergebnis(new Marktwerte(deckel, ankauf), klagen, bericht);
    }

    /**
     * Der Tagesdeckel - ein Geldwert, also mit Sprungbestaetigung.
     *
     * <p>Geteilt mit {@link Wirtschaftsliste#betrag}, wie der Verdienstdeckel: Wer ihn um
     * mehr als Faktor 2 verstellt, muss das bestaetigen. Ein Deckel, der aus einem
     * Vertipper fuenfzigtausend wird, ist kein Deckel mehr - und das Geld dahinter liegt
     * binnen Minuten auf vielen Konten.
     */
    private static long deckel(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        long gelesen = Wirtschaftsliste.betrag(wurzel, Marktwerte.PFAD_TAGESDECKEL,
                "markt-tagesdeckel", Marktwerte.VORGABE_TAGESDECKEL,
                Marktwerte.HOECHSTER_TAGESDECKEL, "Mark", klagen, bericht);
        if (!Marktwerte.brauchbarerDeckel(gelesen)) {
            klagen.add(Marktwerte.PFAD_TAGESDECKEL + " ist " + gelesen
                    + " - verworfen. Eine 0 hiesse, niemand kann verkaufen; wer den"
                    + " Ankauf abschalten will, laesst die Liste leer. "
                    + Wirtschaftsliste.esBleibt(Marktwerte.VORGABE_TAGESDECKEL));
            return Marktwerte.VORGABE_TAGESDECKEL;
        }
        return gelesen;
    }

    /**
     * Das Sortiment: Material und Preis in Pfennigen.
     *
     * <p>Eine kaputte Zeile laesst die uebrigen stehen - ein Server, der wegen eines
     * Tippfehlers in einem von vierzig Preisen nicht startet, ist schlimmer als einer mit
     * neununddreissig.
     */
    private static Ankaufliste ankauf(ConfigurationSection abschnitt,
                                      List<String> klagen, List<String> bericht) {
        if (abschnitt == null) {
            // BERICHT und keine Klage, aus demselben Grund wie bei der Lohnliste: Eine
            // Klage verhindert das ganze Neuladen, und eine vorhandene config.yml wird
            // beim Update NIE ueberschrieben. Gefahrlos ist es, weil ohne Liste NICHTS
            // gekauft wird - es entsteht kein Geld.
            bericht.add("Es gibt keinen Abschnitt " + Marktwerte.PFAD_ANKAUF
                    + " in der config.yml - der Bundesmarkt kauft dann NICHTS. Eine Liste"
                    + " im Programm gibt es dafuer mit Absicht nicht: Sie waere eine"
                    + " zweite Abschrift der ausgelieferten Datei. Zum Kopieren steht der"
                    + " ganze Abschnitt in der config.yml, die das Plugin mitbringt.");
            return Ankaufliste.ausAnkaeufen(List.of());
        }
        List<Ankaufliste.Ankauf> ankaeufe = new ArrayList<>();
        for (String material : abschnitt.getKeys(false)) {
            String pfad = Marktwerte.PFAD_ANKAUF + "." + material;
            if (Material.getMaterial(material.trim().toUpperCase(Locale.ROOT)) == null) {
                klagen.add(pfad + ": \"" + material + "\" ist kein Material, das dieser"
                        + " Server kennt - uebersprungen. Erwartet wird der nackte Name"
                        + " wie stone oder coal_ore, ohne minecraft: davor.");
                continue;
            }
            Object roh = abschnitt.get(material);
            if (!(roh instanceof Number zahl) || roh instanceof Double
                    || roh instanceof Float) {
                klagen.add(pfad + " ist keine ganze Zahl ("
                        + Wirtschaftsliste.wieGetippt(roh) + ") - uebersprungen. Ein"
                        + " Ankaufpreis steht in PFENNIGEN, also in Hundertsteln einer"
                        + " Mark: 40 heisst 0,40 Mark. Ein Punkt trennt in YAML keine"
                        + " Tausender, sondern die Nachkommastellen.");
                continue;
            }
            long pfennige = zahl.longValue();
            if (pfennige < 1L) {
                klagen.add(pfad + " ist " + pfennige + " - uebersprungen. Unter 1 ist"
                        + " kein Ankauf, sondern eine Annahme; was der Bund nicht kaufen"
                        + " soll, steht gar nicht in der Liste.");
                continue;
            }
            if (pfennige > Verkaufsregel.HOECHSTER_PREIS) {
                klagen.add(pfad + " ist " + pfennige + " - uebersprungen. Ueber "
                        + Verkaufsregel.HOECHSTER_PREIS + " Pfennigen laeuft die Rechnung"
                        + " bei einem vollen Inventar ueber, und ein uebergelaufener"
                        + " Betrag ist NEGATIV - also eine Abbuchung auf dem Weg einer"
                        + " Einnahme.");
                continue;
            }
            ankaeufe.add(new Ankaufliste.Ankauf(material, pfennige));
        }
        Ankaufliste liste = Ankaufliste.ausAnkaeufen(ankaeufe);
        if (liste.leer()) {
            // Auch hier Bericht und nicht Klage: Jede einzelne unbrauchbare Zeile hat
            // schon ihre eigene Klage. Dies ist nur die Summe.
            bericht.add(Marktwerte.PFAD_ANKAUF + " ist leer - der Bundesmarkt kauft"
                    + " NICHTS. Die Gruende stehen in den Zeilen davor.");
        } else {
            // NUR das Sortiment. Der Tagesdeckel steht hier nicht: diese Stelle liest
            // ihn nicht und kennt seine Zahl nicht, und eine halbe Zusage
            // ("Tagesdeckel je Spieler.") ist schlimmer als keine. Seine Zahl nennt die
            // Startmeldung des Plugins, seine Aenderung die Bestaetigungszeile.
            bericht.add("Bundesmarkt: " + liste.anzahl() + " Materialien im Sortiment.");
        }
        return liste;
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
