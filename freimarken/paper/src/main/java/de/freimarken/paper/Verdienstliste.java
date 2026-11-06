package de.freimarken.paper;

import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Lohntafel;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Verdienst aus der {@code config.yml} - Deckel, Minderung und die Lohnliste.
 *
 * <p>Nach demselben Muster wie {@link Berufsliste} und {@link Wirtschaftsliste}: reine
 * Dateiarbeit, gesammelte Klagen statt Ausnahmen, und der Rueckfall steht im Programm.
 *
 * <p><b>Mit einer Ausnahme, und die ist die wichtigste Entscheidung dieses Lesers: Die
 * LOHNLISTE hat keinen Rueckfall.</b> Fehlt sie, verdient niemand etwas, und es gibt eine
 * laute Zeile dazu. Eine geratene Liste im Programm waere eine zweite Abschrift der
 * ausgelieferten Datei - und zwei Abschriften derselben Tabelle laufen auseinander, genau
 * wie beim Abschnitt {@code welten:}. Ausserdem ist das die sichere Richtung: Ein
 * geratener Lohn macht Geld, das keine Ruecksicherung zurueckholt.
 *
 * <p><b>Jedes Material wird gegen {@link Material} geprueft</b>, und ein unbekanntes ist
 * eine Klage mit dem Namen. Ohne diese Pruefung stuende ein Tippfehler stumm in der Liste,
 * und der Betreiber suchte den Lohn, den er eingetragen hat.
 *
 * <p>Geprueft wird mit {@link Material#getMaterial(String)} und NICHT mit
 * {@code matchMaterial}: Jenes fragt die Registry des Servers, und dieser Leser soll ohne
 * Server pruefbar bleiben - er ist reine Dateiarbeit. Der Preis ist, dass ein Name mit
 * Bereichsvorsatz ({@code minecraft:stone}) nicht erkannt wird; die ausgelieferte Datei
 * schreibt darum nur den nackten Namen, und die Klage sagt es.
 *
 * <p><b>Jeder Beruf wird gegen das laufende Verzeichnis geprueft</b> - dieselbe Trennung
 * wie bei den Platzzahlen in {@link Berufsliste}: Ein Lohn fuer einen Beruf, den es nicht
 * gibt, ist eine Zeile, die nie wirkt.
 */
final class Verdienstliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Verdienstwerte.PFAD_TAGESDECKEL,
            Verdienstwerte.PFAD_VOLL_BIS,
            Verdienstwerte.PFAD_STUFE,
            Verdienstwerte.PFAD_FAKTOR,
            Verdienstwerte.PFAD_BODEN,
            Verdienstwerte.PFAD_LOEHNE);

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Verdienstwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Verdienstliste() {
    }

    /**
     * Der ganze Abschnitt {@code verdienst:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     * @param gilt   das Berufsverzeichnis, das JETZT gilt - gegen es werden die Berufe der
     *               Lohnliste geprueft. Dieselbe Trennung wie bei den Platzzahlen: Die
     *               Liste der Berufe ist nicht neuladbar, die Loehne sind es
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
                                     Berufsverzeichnis gilt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Verdienstwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long deckel = deckel(wurzel, klagen, bericht);
        int vollBis = zahl(wurzel, Verdienstwerte.PFAD_VOLL_BIS,
                Verdienstwerte.VORGABE_VOLL_BIS, klagen, bericht);
        int stufe = zahl(wurzel, Verdienstwerte.PFAD_STUFE,
                Verdienstwerte.VORGABE_STUFE, klagen, bericht);
        int faktor = anteil(wurzel, Verdienstwerte.PFAD_FAKTOR,
                Verdienstwerte.VORGABE_FAKTOR_PROZENT, false, klagen, bericht);
        int boden = anteil(wurzel, Verdienstwerte.PFAD_BODEN,
                Verdienstwerte.VORGABE_BODEN_PROZENT, true, klagen, bericht);
        Lohntafel tafel = tafel(wurzel.getConfigurationSection(
                Verdienstwerte.PFAD_LOEHNE), gilt, klagen, bericht);

        return new Ergebnis(new Verdienstwerte(deckel, vollBis, stufe, faktor, boden,
                tafel), klagen, bericht);
    }

    /**
     * Der Tagesdeckel - ein Geldwert, also mit Sprungbestaetigung.
     *
     * <p>Geteilt mit {@link Wirtschaftsliste#betrag}: Wer den Deckel um mehr als Faktor 2
     * verstellt, muss das bestaetigen. Ein Deckel, der aus einem Vertipper
     * zweihunderttausend wird, ist kein Deckel mehr - und das Geld dahinter liegt binnen
     * Minuten auf vielen Konten.
     */
    private static long deckel(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        long gelesen = Wirtschaftsliste.betrag(wurzel, Verdienstwerte.PFAD_TAGESDECKEL,
                "tagesdeckel", Verdienstwerte.VORGABE_TAGESDECKEL,
                Verdienstwerte.HOECHSTER_TAGESDECKEL, "Mark", klagen, bericht);
        if (!Verdienstwerte.brauchbarerDeckel(gelesen)) {
            klagen.add(Verdienstwerte.PFAD_TAGESDECKEL + " ist " + gelesen
                    + " - verworfen. Eine 0 hiesse, niemand verdient etwas; wer die"
                    + " Berufe abschalten will, laesst die Lohnliste leer. "
                    + Wirtschaftsliste.esBleibt(Verdienstwerte.VORGABE_TAGESDECKEL));
            return Verdienstwerte.VORGABE_TAGESDECKEL;
        }
        return gelesen;
    }

    /** Eine Stufenzahl - mindestens 1, denn eine 0 teilte die Rechnung durch null. */
    private static int zahl(ConfigurationSection wurzel, String pfad, int vorgabe,
                            List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        if (!Verdienstwerte.brauchbareAnzahl(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1 bis "
                    + Verdienstwerte.HOECHSTE_STUFE + "; eine 0 teilte die Rechnung"
                    + " durch null. " + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return (int) (long) gelesen;
    }

    /**
     * Ein Prozentwert - fuer den Faktor 1 bis 99, fuer den Boden 1 bis 100.
     *
     * @param bisHundert ob 100 erlaubt ist. Beim Boden heisst 100 "die Minderung ist aus",
     *                   beim Faktor hiesse es "sie mindert nichts" - und das ist dasselbe,
     *                   nur an der falschen Zahl eingestellt
     */
    private static int anteil(ConfigurationSection wurzel, String pfad, int vorgabe,
                              boolean bisHundert, List<String> klagen,
                              List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        boolean brauchbar = bisHundert
                ? Verdienstwerte.brauchbarerBoden(gelesen)
                : Verdienstwerte.brauchbarerFaktor(gelesen);
        if (!brauchbar) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1 bis "
                    + (bisHundert ? "100" : "99") + " Prozent."
                    + (bisHundert
                            ? " Eine 0 waere eine unsichtbare Wand: Ab einer Zahl, die"
                                    + " niemand kennt, passiert gar nichts mehr."
                            : " Bei 100 mindert nichts - wer das will, setzt voll-bis"
                                    + " hoch.")
                    + " " + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return (int) (long) gelesen;
    }

    /**
     * Die Lohnliste: Beruf, Taetigkeit, Material, Pfennige.
     *
     * <p>Drei Ebenen tief, und jede wird gepruefet. Eine kaputte Zeile laesst die uebrigen
     * stehen - ein Server, der wegen eines Tippfehlers in einem von vierzig Loehnen nicht
     * startet, ist schlimmer als einer mit neununddreissig.
     */
    private static Lohntafel tafel(ConfigurationSection abschnitt,
                                   Berufsverzeichnis gilt, List<String> klagen,
                                   List<String> bericht) {
        if (abschnitt == null) {
            // BERICHT und keine Klage, und der Unterschied ist wichtig: Eine Klage
            // verhindert das ganze Neuladen. Auf einem Server, dessen config.yml den
            // Abschnitt noch nicht hat - und eine vorhandene Datei wird beim Update NIE
            // ueberschrieben -, koennte der Betreiber dann keinen Preis mehr neu laden,
            // nur weil er noch keine Loehne eingetragen hat.
            //
            // Gefahrlos ist das, weil ohne Liste NIEMAND etwas verdient: Es entsteht kein
            // Geld, das eine Ruecksicherung zurueckholen muesste. Laut gesagt wird es
            // trotzdem - der Betreiber soll wissen, dass seine Berufe nichts einbringen.
            bericht.add("Es gibt keinen Abschnitt " + Verdienstwerte.PFAD_LOEHNE
                    + " in der config.yml - damit verdient NIEMAND etwas. Eine Liste im"
                    + " Programm gibt es dafuer mit Absicht nicht: Sie waere eine zweite"
                    + " Abschrift der ausgelieferten Datei. Zum Kopieren steht der"
                    + " ganze Abschnitt in der config.yml, die das Plugin mitbringt.");
            return Lohntafel.ausLoehnen(List.of());
        }
        List<Lohntafel.Lohn> loehne = new ArrayList<>();
        for (String beruf : abschnitt.getKeys(false)) {
            String berufpfad = Verdienstwerte.PFAD_LOEHNE + "." + beruf;
            String sauber = beruf.trim().toLowerCase(Locale.ROOT);
            if (gilt.aufgeloest(sauber).isEmpty()) {
                klagen.add(berufpfad + ": Den Beruf \"" + beruf + "\" gibt es nicht in "
                        + Berufswerte.PFAD_LISTE
                        + " - der ganze Block ist uebersprungen. Ein Lohn fuer einen"
                        + " Beruf, den es nicht gibt, ist eine Zeile, die nie wirkt.");
                continue;
            }
            ConfigurationSection jeTaetigkeit = abschnitt.getConfigurationSection(beruf);
            if (jeTaetigkeit == null) {
                klagen.add(berufpfad + " ist kein Abschnitt ("
                        + Wirtschaftsliste.wieGetippt(abschnitt.get(beruf))
                        + ") - uebersprungen. Unter einem Beruf stehen die Taetigkeiten,"
                        + " darunter die Materialien.");
                continue;
            }
            loehne.addAll(jeBeruf(jeTaetigkeit, sauber, berufpfad, klagen));
        }
        Lohntafel tafel = Lohntafel.ausLoehnen(loehne);
        if (tafel.leer()) {
            // Auch hier Bericht und nicht Klage: Jede einzelne unbrauchbare Zeile hat
            // schon ihre eigene Klage: Dies ist nur die Summe, und sie darf kein
            // Neuladen verhindern.
            bericht.add(Verdienstwerte.PFAD_LOEHNE + " ist leer - damit verdient NIEMAND"
                    + " etwas. Die Gruende stehen in den Zeilen davor.");
        } else {
            bericht.add("Lohnliste: " + tafel.anzahl() + " Saetze - "
                    + tafel.materialien(Taetigkeit.ABBAUEN).size()
                    + " Materialien beim Abbauen, "
                    + tafel.materialien(Taetigkeit.SETZEN).size() + " beim Setzen.");
        }
        return tafel;
    }

    private static List<Lohntafel.Lohn> jeBeruf(ConfigurationSection jeTaetigkeit,
                                                String beruf, String berufpfad,
                                                List<String> klagen) {
        List<Lohntafel.Lohn> gefunden = new ArrayList<>();
        for (String name : jeTaetigkeit.getKeys(false)) {
            String pfad = berufpfad + "." + name;
            Taetigkeit taetigkeit = Taetigkeit.ausSchluessel(name);
            if (taetigkeit == null) {
                klagen.add(pfad + ": \"" + name + "\" ist keine Taetigkeit"
                        + " - uebersprungen. Es gibt " + taetigkeiten() + "; weitere"
                        + " kommen mit ihrem eigenen Bauschritt, weil jede ein eigenes"
                        + " Ereignis braucht.");
                continue;
            }
            ConfigurationSection jeMaterial =
                    jeTaetigkeit.getConfigurationSection(name);
            if (jeMaterial == null) {
                klagen.add(pfad + " ist kein Abschnitt ("
                        + Wirtschaftsliste.wieGetippt(jeTaetigkeit.get(name))
                        + ") - uebersprungen. Unter einer Taetigkeit stehen die"
                        + " Materialien mit ihrem Lohn in Pfennigen.");
                continue;
            }
            for (String material : jeMaterial.getKeys(false)) {
                Lohntafel.Lohn lohn = einLohn(jeMaterial, beruf, taetigkeit, material,
                        pfad + "." + material, klagen);
                if (lohn != null) {
                    gefunden.add(lohn);
                }
            }
        }
        return gefunden;
    }

    private static Lohntafel.Lohn einLohn(ConfigurationSection jeMaterial, String beruf,
                                          Taetigkeit taetigkeit, String material,
                                          String pfad, List<String> klagen) {
        if (Material.getMaterial(material.trim().toUpperCase(Locale.ROOT)) == null) {
            klagen.add(pfad + ": \"" + material + "\" ist kein Material, das dieser"
                    + " Server kennt - uebersprungen. Ohne diese Pruefung stuende der"
                    + " Tippfehler stumm in der Liste, und du suchtest den Lohn, den du"
                    + " eingetragen hast. Erwartet wird der nackte Name wie stone oder"
                    + " coal_ore, ohne minecraft: davor.");
            return null;
        }
        Object roh = jeMaterial.get(material);
        if (!(roh instanceof Number zahl) || roh instanceof Double
                || roh instanceof Float) {
            klagen.add(pfad + " ist keine ganze Zahl ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - uebersprungen. Ein Lohn"
                    + " steht in PFENNIGEN, also in Hundertsteln einer Mark: 40 heisst"
                    + " 0,40 Mark. Ein Punkt trennt in YAML keine Tausender, sondern die"
                    + " Nachkommastellen.");
            return null;
        }
        long pfennige = zahl.longValue();
        if (pfennige < 0L) {
            klagen.add(pfad + " ist " + pfennige + " - uebersprungen. Ein negativer Lohn"
                    + " waere eine Gebuehr fuers Arbeiten, und die gehoert zu den"
                    + " Senken.");
            return null;
        }
        return new Lohntafel.Lohn(beruf, taetigkeit, material, pfennige);
    }

    /** Die Taetigkeiten, die es gibt - fuer die Klage, aus der Aufzaehlung gebaut. */
    private static String taetigkeiten() {
        List<String> namen = new ArrayList<>();
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            namen.add(taetigkeit.schluessel());
        }
        return String.join(" und ", namen);
    }

    /**
     * Nennt jede Verdienstzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste}: Zwei
     * Berichte, die dieselbe Zahl verschieden nennen, sind schlimmer als zwei Zeilen.
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
                    + " Verdienstzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie erst,"
                    + " wenn sie in der Datei stehen; was das Plugin ohne die Zeile"
                    + " mitbringt, nennt die eigene Zeile zu jedem Schluessel.");
        }
    }
}
