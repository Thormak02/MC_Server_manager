package de.freimarken.paper;

import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Lohntafel;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

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
 * <p><b>Jeder Name wird geprueft</b>, und ein unbekannter ist eine Klage mit dem Namen.
 * Ohne diese Pruefung stuende ein Tippfehler stumm in der Liste, und der Betreiber suchte
 * den Lohn, den er eingetragen hat.
 *
 * <p><b>Wogegen geprueft wird, sagt die Taetigkeit</b> ({@code Taetigkeit.Namensart}):
 * Unter {@code abbauen} steht ein Material, unter {@code toeten} eine Wesensart. Die
 * Zuordnung steht an der Aufzaehlung und nicht hier - so ist eine neue Taetigkeit eine
 * Konstante mit zwei Angaben und keine vergessene Verzweigung in diesem Leser.
 *
 * <p>Geprueft wird mit {@link Material#getMaterial(String)} und
 * {@link EntityType#fromName(String)}, NICHT mit {@code matchMaterial} oder der Registry:
 * Jene fragen den laufenden Server, und dieser Leser soll ohne Server pruefbar bleiben -
 * er ist reine Dateiarbeit. Gemessen gegen das echte Jar gilt dasselbe fuer
 * {@code Material.isBlock()} und {@code isItem()}; es gibt hier also keine Pruefung
 * "ist das auch ein Block". Der Preis ist, dass ein Name mit Bereichsvorsatz
 * ({@code minecraft:stone}) nicht erkannt wird; die ausgelieferte Datei schreibt darum nur
 * den nackten Namen, und die Klage sagt es.
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
            Verdienstwerte.PFAD_STUNDENMENGE,
            Verdienstwerte.PFAD_SPAWNGRUENDE,
            Verdienstwerte.PFAD_SAATGUT,
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
        Map<Taetigkeit, Integer> mengen = stundenmengen(wurzel, klagen, bericht);
        Set<String> gruende = namensliste(wurzel, Verdienstwerte.PFAD_SPAWNGRUENDE,
                Verdienstwerte.VORGABE_SPAWNGRUENDE, Verdienstliste::warumKeinGrund,
                klagen, bericht);
        Set<String> saat = namensliste(wurzel, Verdienstwerte.PFAD_SAATGUT,
                Verdienstwerte.VORGABE_SAATGUT, Verdienstliste::warumKeinMaterial,
                klagen, bericht);
        Lohntafel tafel = tafel(wurzel.getConfigurationSection(
                Verdienstwerte.PFAD_LOEHNE), gilt, saat, klagen, bericht);

        return new Ergebnis(new Verdienstwerte(deckel, vollBis, stufe, faktor, boden,
                mengen, gruende, saat, tafel), klagen, bericht);
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
                                   Berufsverzeichnis gilt, Set<String> saat,
                                   List<String> klagen, List<String> bericht) {
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
            loehne.addAll(jeBeruf(jeTaetigkeit, sauber, berufpfad, saat, klagen));
        }
        Lohntafel tafel = Lohntafel.ausLoehnen(loehne);
        nenneDoppelte(tafel, saat, klagen);
        if (tafel.leer()) {
            // Auch hier Bericht und nicht Klage: Jede einzelne unbrauchbare Zeile hat
            // schon ihre eigene Klage: Dies ist nur die Summe, und sie darf kein
            // Neuladen verhindern.
            bericht.add(Verdienstwerte.PFAD_LOEHNE + " ist leer - damit verdient NIEMAND"
                    + " etwas. Die Gruende stehen in den Zeilen davor.");
        } else {
            // Jede Taetigkeit aus der Aufzaehlung und nicht zwei abgeschriebene: Sonst
            // schweigt der Bericht ueber genau die Taetigkeit, die jemand neu dazubaut.
            List<String> teile = new ArrayList<>();
            for (Taetigkeit taetigkeit : Taetigkeit.values()) {
                teile.add(tafel.materialien(taetigkeit).size() + " "
                        + taetigkeit.schluessel());
            }
            bericht.add("Lohnliste: " + tafel.anzahl() + " Saetze - "
                    + String.join(", ", teile) + ".");
        }
        return tafel;
    }

    /**
     * Klagt ueber ein Material, das unter {@code abbauen} UND {@code ernten} steht.
     *
     * <p>Der Ereigniszweig zahlt je Block GENAU EINE Taetigkeit, und er entscheidet sich
     * fuer die Ernte, sobald ein Erntesatz vorliegt. Eine Zeile unter {@code abbauen}
     * waere damit entweder tot oder sie zahlte fuer eine UNREIFE Pflanze - beides ist
     * nicht, was der Betreiber meint, und beides sieht man an der Datei nicht.
     *
     * <p><b>Fuer {@code toeten} und {@code zuechten} gilt das NICHT</b>, und das ist kein
     * Vergessen: Dasselbe Tier zahlt dort schon durch die Weissliste der
     * Entstehungsgruende nicht zweimal - ein gezuechtetes Kalb traegt den Grund
     * {@code breeding} und bringt beim Tod nichts. Ein Schaf zuechten und ein wildes
     * Schaf erschlagen sind zwei verschiedene Taten, und beide duerfen zahlen.
     *
     * <p>Die zweite tote Zeile, die hier auffaellt: SAATGUT unter {@code setzen}.
     */
    private static void nenneDoppelte(Lohntafel tafel, Set<String> saat,
                                      List<String> klagen) {
        List<String> ernten = tafel.materialien(Taetigkeit.ERNTEN);
        for (String material : tafel.materialien(Taetigkeit.ABBAUEN)) {
            if (ernten.contains(material)) {
                klagen.add(Verdienstwerte.PFAD_LOEHNE + ": \"" + material + "\" steht"
                        + " unter abbauen UND unter ernten. Je Block zahlt GENAU EINE"
                        + " Taetigkeit, und sobald ein Erntesatz vorliegt, ist es die"
                        + " Ernte - die Zeile unter abbauen wirkt dann nur noch fuer eine"
                        + " UNREIFE Pflanze. Entscheide dich fuer eine der beiden.");
            }
        }
        // Und die zweite tote Zeile: SAATGUT unter setzen. Die Setzwache kehrt bei
        // Saatgut um, bevor sie den Zahlmeister ruft - mit Absicht, denn sonst waere
        // saeen-umschlagen-saeen die kleine Maschine. Ein Satz dafuer wirkt also nie,
        // und an der Datei sieht man das nicht.
        for (String material : tafel.materialien(Taetigkeit.SETZEN)) {
            if (saat.contains(material)) {
                klagen.add(Verdienstwerte.PFAD_LOEHNE + ": \"" + material + "\" steht"
                        + " unter setzen, ist aber Saatgut ("
                        + Verdienstwerte.PFAD_SAATGUT + "). Saatgut bekommt keine Marke"
                        + " und zahlt beim Setzen nichts - sonst waere Saeen selbst eine"
                        + " kleine Maschine. Bezahlt wird die ERNTE; diese Zeile wirkt"
                        + " nie.");
            }
        }
    }

    private static List<Lohntafel.Lohn> jeBeruf(ConfigurationSection jeTaetigkeit,
                                                String beruf, String berufpfad,
                                                Set<String> saat,
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
                Lohntafel.Lohn lohn = einLohn(jeMaterial, beruf, taetigkeit,
                        material, saat, pfad + "." + material, klagen);
                if (lohn != null) {
                    gefunden.add(lohn);
                }
            }
        }
        return gefunden;
    }

    private static Lohntafel.Lohn einLohn(ConfigurationSection jeMaterial, String beruf,
                                          Taetigkeit taetigkeit, String material,
                                          Set<String> saat, String pfad,
                                          List<String> klagen) {
        // SAATGUT unter abbauen wird VERWORFEN und nicht nur beklagt, und das ist der
        // eine Satz dieses Lesers, der eine Zeile wegen GEFAHR wegwirft: Saatgut bekommt
        // keine Marke, und unter abbauen greift keine Reifepruefung - saeen und sofort
        // umschlagen zahlte damit jedes Mal, unbegrenzt bis zur Stundenmenge.
        //
        // Der Fall ist nicht gedacht: Genau so sieht jede config.yml aus, die beim
        // Update nicht nachgezogen wurde (bauer/abbauen/wheat aus Teil E1), und eine
        // vorhandene Datei wird NIE ueberschrieben. Eine Klage allein liesse das Geld
        // fliessen, bis jemand das Protokoll liest; verworfen wird nichts erfunden.
        // Die sichere Richtung, wie ueberall sonst in diesem Leser.
        if (taetigkeit == Taetigkeit.ABBAUEN
                && saat.contains(material.trim().toLowerCase(Locale.ROOT))) {
            klagen.add(pfad + ": \"" + material + "\" ist Saatgut ("
                    + Verdienstwerte.PFAD_SAATGUT + ") und steht unter abbauen -"
                    + " VERWORFEN. Saatgut bekommt keine Marke, damit ein gesaetes Feld"
                    + " zahlen kann; unter abbauen fehlt dann aber die Reifepruefung, und"
                    + " saeen-umschlagen-saeen waere eine Maschine. Trage den Satz unter"
                    + " ernten ein - dort zahlt nur die reife Pflanze.");
            return null;
        }
        String warum = taetigkeit.namensart() == Taetigkeit.Namensart.WESEN
                ? warumKeinWesen(material)
                : warumKeinMaterial(material);
        if (warum != null) {
            klagen.add(pfad + ": \"" + material + "\" " + warum + " - uebersprungen."
                    + " Ohne diese Pruefung stuende der Tippfehler stumm in der Liste,"
                    + " und du suchtest den Lohn, den du eingetragen hast.");
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

    /**
     * Warum dieser Name kein Material ist - oder {@code null}, wenn er eines ist.
     *
     * <p>Ein SATZTEIL und kein Wahrheitswert: Die Klage soll sagen, WAS falsch ist, und
     * eine Pruefung, die nur "nein" sagt, laesst den Betreiber raten.
     */
    private static String warumKeinMaterial(String name) {
        if (Material.getMaterial(name.trim().toUpperCase(Locale.ROOT)) == null) {
            return "ist kein Material, das dieser Server kennt. Erwartet wird der nackte"
                    + " Name wie stone oder coal_ore, ohne minecraft: davor";
        }
        return null;
    }

    /**
     * Warum dieser Name keine lebende Wesensart ist - oder {@code null}.
     *
     * <p>Die zweite Pruefung ({@code isAlive}) faengt den Eintrag, der ein Material waere,
     * aber kein Wesen: Ein Lohn fuer {@code arrow} oder {@code acacia_boat} ist eine
     * Zeile, die nie wirkt, denn ein Boot stirbt nicht und wird nicht gezuechtet.
     */
    private static String warumKeinWesen(String name) {
        EntityType art = EntityType.fromName(name.trim());
        if (art == null) {
            return "ist keine Wesensart, die dieser Server kennt. Erwartet wird der"
                    + " nackte Name wie cow oder zombie, ohne minecraft: davor";
        }
        if (!art.isAlive()) {
            return "ist keine LEBENDE Wesensart - ein Pfeil und ein Boot sterben nicht"
                    + " und werden nicht gezuechtet";
        }
        return null;
    }

    /** Warum dieser Name kein Entstehungsgrund ist - oder {@code null}. */
    private static String warumKeinGrund(String name) {
        for (CreatureSpawnEvent.SpawnReason grund
                : CreatureSpawnEvent.SpawnReason.values()) {
            if (grund.name().equalsIgnoreCase(name.trim())) {
                return null;
            }
        }
        return "ist kein Entstehungsgrund, den dieser Server kennt. Es gibt natural,"
                + " spawner, breeding und etliche weitere - die vollstaendige Liste"
                + " nennt die Spielfassung";
    }

    /** Was ein Name taugt, oder warum nicht. */
    private interface Namenspruefer {
        String warum(String name);
    }

    /**
     * Eine Liste von Namen - jede Zeile geprueft, eine kaputte uebersprungen.
     *
     * <p>Geteilt von Entstehungsgruenden und Saatgut, denn es ist dieselbe Arbeit: lesen,
     * jeden Namen pruefen, kleinschreiben. Fehlt die Liste ganz, gilt die Vorgabe des
     * Programms - anders als bei der Lohnliste sind das wenige Namen, und eine fehlende
     * Weissliste waere keine Weissliste.
     */
    private static Set<String> namensliste(ConfigurationSection wurzel, String pfad,
                                           Set<String> vorgabe, Namenspruefer pruefer,
                                           List<String> klagen, List<String> bericht) {
        if (!wurzel.contains(pfad, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gelten die "
                    + vorgabe.size() + " Namen aus dem Programm.");
            return vorgabe;
        }
        List<?> gelesen = wurzel.getList(pfad);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(pfad)) + ") - verworfen."
                    + " Erwartet wird eine Liste in eckigen Klammern oder mit"
                    + " Strichen. " + Wirtschaftsliste.esBleibt(vorgabe.size())
                    + " Namen aus dem Programm.");
            return vorgabe;
        }
        Set<String> sauber = new LinkedHashSet<>();
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Name - uebersprungen.");
                continue;
            }
            String warum = pruefer.warum(name);
            if (warum != null) {
                klagen.add(pfad + ": \"" + name + "\" " + warum + " - uebersprungen.");
                continue;
            }
            sauber.add(name.trim().toLowerCase(Locale.ROOT));
        }
        // Eine LEERE Liste ist erlaubt und heisst etwas: beim Saatgut "jeder gesetzte
        // Block bekommt eine Marke", bei den Gruenden "kein getoetetes Wesen zahlt".
        // Beides ist die sichere Richtung, also kein Rueckfall auf die Vorgabe.
        bericht.add(pfad + ": " + sauber.size() + " Namen.");
        return sauber;
    }

    /**
     * Je Taetigkeit die harte Stundenmenge.
     *
     * <p>Eine fehlende Zeile wird mit der Vorgabe GEFUELLT und im Bericht genannt, nicht
     * beklagt: Der Satz verlangt eine Zahl zu jeder Taetigkeit, und eine Taetigkeit ohne
     * Obergrenze waere die eine ohne Bremse. Ein unbrauchbarer Wert ist dagegen eine
     * Klage - wer eine 0 eintraegt, meint nicht die Vorgabe.
     */
    private static Map<Taetigkeit, Integer> stundenmengen(ConfigurationSection wurzel,
                                                          List<String> klagen,
                                                          List<String> bericht) {
        Map<Taetigkeit, Integer> mengen = new LinkedHashMap<>();
        List<String> gefuellt = new ArrayList<>();
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            String pfad = Verdienstwerte.PFAD_STUNDENMENGE + "."
                    + taetigkeit.schluessel();
            int vorgabe = Verdienstwerte.VORGABE_STUNDENMENGEN.get(taetigkeit);
            if (!wurzel.contains(pfad, true)) {
                gefuellt.add(taetigkeit.schluessel() + " " + vorgabe);
                mengen.put(taetigkeit, vorgabe);
                continue;
            }
            Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen,
                    bericht);
            if (gelesen == null || !Verdienstwerte.brauchbareAnzahl(gelesen)) {
                if (gelesen != null) {
                    klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1"
                            + " bis " + Verdienstwerte.HOECHSTE_STUFE + "; eine 0 hiesse,"
                            + " dass diese Taetigkeit nie etwas einbringt - wer das will,"
                            + " laesst ihre Lohnzeilen weg. "
                            + Wirtschaftsliste.esBleibt(vorgabe));
                }
                mengen.put(taetigkeit, vorgabe);
                continue;
            }
            mengen.put(taetigkeit, (int) (long) gelesen);
        }
        if (!gefuellt.isEmpty()) {
            bericht.add(gefuellt.size() + " Stundenmengen stehen nicht in deiner"
                    + " config.yml - es gelten die Zahlen aus dem Programm: "
                    + String.join(", ", gefuellt) + ".");
        }
        return mengen;
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
