package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Truhenwerte;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Freie Truhe aus der {@code config.yml} - Zahlen UND Stellen, Plan 7.2.
 *
 * <p>Dieselbe Bauart wie {@link Mietliste} und {@link Bonusliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen gelassen.
 * Ein Tippfehler darf keinen Serverstart mitreissen; er darf aber auch nicht still gelten.
 *
 * <p><b>Zwei Lesungen und EIN Abschnitt</b>, genau wie {@code laeden:} von
 * {@link Mietliste} und {@link Ladenliste} geteilt wird:
 * <ul>
 *   <li>{@link #ausKonfiguration} liest die ZAHLEN ({@code entnahme-je-tag},
 *       {@code verbotene-gegenstaende}). Sie sind neuladbar und wandern in den
 *       {@code Wirtschaftsstand}; gerufen wird aus {@link Wirtschaftsliste}.
 *   <li>{@link #stellen} liest die STELLEN. Sie sind von Hand eingemessenes Gelaende und
 *       werden nur beim Start gelesen; gerufen wird aus dem Plugin, denn nur dort sind
 *       der Weltschluessel und die Ladeneinheiten bekannt.
 * </ul>
 * Getrennt, weil beide verschiedene Dinge koennen muessen - und weil ein beim Neuladen
 * mitgetauschtes Gelaende ein offenes Truhenfenster gegen den Inhalt einer anderen Kiste
 * abrechnete. Ihre Berichtszeilen fangen darum verschieden an ("Freie Truhe:" und
 * "Truhenstellen:"); zwei Zeilen, die gleich anfangen, liest niemand als zwei.
 *
 * <p><b>Die Stellen liegen in der LADENWELT</b> und haben keinen eigenen Weltschluessel:
 * Die Freie Truhe steht in der Markthalle, und die steht in {@code laeden.welt}. Ein
 * zweiter Schluessel waere eine zweite Wahrheit ueber dieselbe Halle - und ein
 * Einstellschluessel fuer einen Wert, den es noch nicht gibt, ist derselbe Fehler wie ein
 * Index fuer eine Abfrage, die niemand stellt. Wer je eine Truhe in einer anderen Welt
 * will, baut dann den Schluessel dazu.
 */
final class Truhenliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Truhenwerte.PFAD_ENTNAHME, Truhenwerte.PFAD_VERBOTEN);

    record Ergebnis(Truhenwerte werte, List<String> klagen, List<String> bericht) {
    }

    record Stellen(Truhenverzeichnis verzeichnis, List<String> klagen,
                   List<String> bericht) {
    }

    private Truhenliste() {
    }

    /**
     * Die zwei neuladbaren Angaben aus dem Abschnitt {@code freie-truhe:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Truhenwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        int entnahme = entnahme(wurzel, klagen, bericht);
        Set<String> verboten = verboten(wurzel, klagen, bericht);
        Truhenwerte werte = new Truhenwerte(entnahme, verboten);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Preisstaffel und der Ladenmiete: die einzige
     * Schranke gegen den Wert, der im erlaubten Bereich liegt und trotzdem falsch ist. Wer
     * hier "3456 Stueck je Spieler und Tag" liest, sieht eine volle Doppelkiste am Tag -
     * keine Zahlengrenze sieht sie, denn genau dort liegt die Grenze.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann: Zwei Stellen, die
     * dieselben Zahlen verschieden schreiben, sehen nach zwei verschiedenen Zahlen aus.
     */
    static String rechenprobe(Truhenwerte werte) {
        StringBuilder satz = new StringBuilder("Freie Truhe: ");
        if (werte.gedeckelt()) {
            satz.append(werte.entnahmeJeTag())
                    .append(" Stueck je Spieler und Tag - gezaehlt wird, was beim"
                            + " Schliessen FEHLT, und der Rest wird zurueckgelegt. ");
        } else {
            satz.append("OHNE Deckel (")
                    .append(Truhenwerte.PFAD_ENTNAHME).append(": ")
                    .append(Truhenwerte.KEIN_DECKEL)
                    .append(") - jeder darf so viel herausnehmen, wie er will. ");
        }
        if (werte.verboten().isEmpty()) {
            satz.append("NICHTS ist verboten - alles darf hinein und heraus.");
        } else {
            satz.append(werte.verboten().size())
                    .append(" Gegenstaende duerfen nicht heraus.");
        }
        satz.append(" Hineinlegen ist NIE gedeckelt, und der Inhalt wird jede Nacht"
                + " geleert.");
        // DIE BETRIEBSART GEHOERT IN DENSELBEN SATZ wie die Zahlen, die sie bestimmen:
        // Dieselbe Zeile laeuft beim Start UND bei jedem Neuladen, und ein Neuladen kann
        // die Art umschalten. Zwei getrennte Zeilen waeren zwei Wahrheiten, von denen
        // eine irgendwann hinterherhinkt.
        if (werte.zaehlt()) {
            satz.append(" Gezaehlt wird, was beim Schliessen FEHLT - darum sieht immer"
                    + " nur EINER hinein, und ein Trichter bekommt nichts.");
        } else {
            satz.append(" Gezaehlt wird GAR NICHTS: Alle duerfen gemeinsam hineinsehen,"
                    + " und kein Tagesstand wird gelesen oder geschrieben. Ein Trichter"
                    + " bekommt trotzdem nichts.");
        }
        return satz.toString();
    }

    /**
     * Die Stellen aus {@code freie-truhe.truhen} - nur beim Start gelesen.
     *
     * @param wurzel         die ganze Konfiguration, darf {@code null} sein
     * @param weltschluessel die Welt der Markthalle, fertig als {@code freimarken:altfurt}
     *                       - gebaut an der EINEN Stelle im Plugin, nie hier
     * @param laeden         die Ladeneinheiten derselben Welt; eine Freie Truhe darin
     *                       waere die Truhe eines Mieters
     */
    static Stellen stellen(ConfigurationSection wurzel, String weltschluessel,
                           Ladenverzeichnis laeden) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        String pfad = Truhenverzeichnis.PFAD_TRUHEN;
        if (wurzel == null || !wurzel.contains(pfad, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gibt keine Freie"
                    + " Truhe. Eine Stelle wird von Hand eingemessen; das Programm kann"
                    + " sie nicht erraten.");
            return new Stellen(Truhenverzeichnis.ohneTruhen(), klagen, bericht);
        }
        List<?> gelesen = wurzel.getList(pfad);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(pfad)) + ") - verworfen."
                    + " Erwartet wird je Truhe eine Zeile mit drei ganzen Zahlen, etwa"
                    + " \"- [62, 72, 8]\". Es gibt dann keine Freie Truhe.");
            return new Stellen(Truhenverzeichnis.ohneTruhen(), klagen, bericht);
        }
        List<Blockort> stellen = new ArrayList<>();
        for (int i = 0; i < gelesen.size(); i++) {
            Blockort ort = eine(gelesen.get(i), pfad + " Eintrag " + (i + 1),
                    weltschluessel, laeden, klagen);
            if (ort == null) {
                continue;
            }
            if (stellen.contains(ort)) {
                // Keine Klage mit Verwerfen: Das Verzeichnis legt doppelte Stellen
                // zusammen, und zwei gleiche Zeilen sind ein Tippfehler, der nichts
                // kaputt macht. Gesagt wird es trotzdem - sonst suchte der Betreiber
                // die zweite Truhe, die der Bericht ihm genannt haette.
                klagen.add(pfad + " Eintrag " + (i + 1) + " nennt dieselbe Stelle wie"
                        + " ein Eintrag davor (" + ort.x() + "/" + ort.y() + "/"
                        + ort.z() + ") - sie zaehlt einmal.");
                continue;
            }
            stellen.add(ort);
        }
        Truhenverzeichnis verzeichnis = Truhenverzeichnis.ausStellen(stellen);
        if (!verzeichnis.gibtTruhen()) {
            bericht.add(pfad + " ist leer - es gibt keine Freie Truhe. Die Wache wird"
                    + " dann gar nicht angemeldet.");
        } else {
            StringBuilder satz = new StringBuilder("Truhenstellen: ")
                    .append(verzeichnis.anzahl()).append(" in ").append(weltschluessel)
                    .append(" -");
            for (Blockort ort : verzeichnis.alle()) {
                satz.append(" ").append(ort.x()).append("/").append(ort.y()).append("/")
                        .append(ort.z());
            }
            // Die Zahlen im Klartext, und zwar ALLE: Diese Stellen gelten nur fuer die
            // VORHANDENE Welt. Wer Altfurt neu anlegt, hat anderes Gelaende - dann steht
            // die Truhe im Boden, und die einzige Spur davon ist diese Zeile.
            satz.append(". Diese Stellen gelten nur fuer die vorhandene Welt.");
            bericht.add(satz.toString());
        }
        return new Stellen(verzeichnis, klagen, bericht);
    }

    /**
     * Eine Stelle aus einem Eintrag wie {@code [62, 72, 8]}.
     *
     * <p>Am TYP geprueft und nicht am Nachkommarest, wie {@link Ladenliste}: YAML macht
     * aus {@code 1.000} eine 1.0, und die ist ganzzahlig - gemeint waren 1000.
     */
    private static Blockort eine(Object roh, String wo, String weltschluessel,
                                 Ladenverzeichnis laeden, List<String> klagen) {
        if (!(roh instanceof List<?> zahlen) || zahlen.size() != 3) {
            klagen.add(wo + " ist keine Liste aus DREI Zahlen ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - uebersprungen.");
            return null;
        }
        int[] ort = new int[3];
        for (int i = 0; i < 3; i++) {
            Object einzeln = zahlen.get(i);
            if (!(einzeln instanceof Number zahl) || einzeln instanceof Double
                    || einzeln instanceof Float
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())
                    || Math.abs(zahl.doubleValue()) > Integer.MAX_VALUE) {
                klagen.add(wo + " hat an Stelle " + (i + 1) + " keine ganze Zahl ("
                        + Wirtschaftsliste.wieGetippt(einzeln) + ") - uebersprungen. Ein"
                        + " Punkt trennt in YAML keine Tausender, sondern die"
                        + " Nachkommastellen.");
                return null;
            }
            ort[i] = zahl.intValue();
        }
        // EINE FREIE TRUHE IN EINER LADENEINHEIT waere die Truhe eines Mieters: Der
        // Ladenschutz liesse dort niemanden heran ausser ihm, und er duerfte umgekehrt
        // alles herausnehmen, was andere hineinlegen - ohne Deckel, denn seine Kiste
        // fragt keine Truhenregel. Genau das ist die Verwechslung, die beim Einmessen von
        // Hand passiert: Beide Angaben stehen in derselben Datei und nennen Koordinaten
        // derselben Halle.
        var einheit = laeden == null ? null : laeden.anDerStelle(ort[0], ort[1], ort[2]);
        if (einheit != null) {
            klagen.add(wo + " liegt in der Ladeneinheit \"" + einheit.name()
                    + "\" - uebersprungen. Eine Freie Truhe gehoert in die Halle, nicht"
                    + " in einen vermieteten Laden: Dort kaeme niemand ausser dem Mieter"
                    + " heran, und der nimmt ohne Deckel.");
            return null;
        }
        return new Blockort(weltschluessel, ort[0], ort[1], ort[2]);
    }

    private static int entnahme(ConfigurationSection wurzel, List<String> klagen,
                                List<String> bericht) {
        String pfad = Truhenwerte.PFAD_ENTNAHME;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad,
                Truhenwerte.VORGABE_ENTNAHME, klagen, bericht);
        if (gelesen == null) {
            return Truhenwerte.VORGABE_ENTNAHME;
        }
        long wert = gelesen;
        // Der LONG wird geprueft und nicht der verkuerzte int - dieselbe Falle wie bei
        // der Mietdauer: Aus (int) 4294967312 wird 16, und das waere zufaellig die
        // Vorgabe.
        if (!Truhenwerte.brauchbareEntnahme((int) wert) || wert != (int) wert) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist "
                    + Truhenwerte.KEIN_DECKEL + " (kein Deckel) bis "
                    + Truhenwerte.HOECHSTE_ENTNAHME + " Stueck. "
                    + Wirtschaftsliste.esBleibt(Truhenwerte.VORGABE_ENTNAHME));
            return Truhenwerte.VORGABE_ENTNAHME;
        }
        if (wert == Truhenwerte.KEIN_DECKEL) {
            // Berichtet und NICHT beklagt: Die 0 ist eine Entscheidung des Betreibers.
            // Gesagt werden muss sie trotzdem, denn sie ist die eine Zahl, die das
            // Gegenteil der uebrigen bewirkt - dieselbe Form wie beim Tagesdeckel des
            // Bundesmarkts.
            bericht.add(pfad + " ist 0: KEIN Deckel - jeder darf so viel aus der Freien"
                    + " Truhe nehmen, wie er will. Der Inhalt wird trotzdem jede Nacht"
                    + " geleert.");
        }
        return (int) wert;
    }

    /**
     * Die Liste der Gegenstaende, die nicht heraus duerfen.
     *
     * <p>Jeder Name wird gegen {@link Material#getMaterial(String)} geprueft, nicht gegen
     * {@code matchMaterial} oder die Registry: Jene fragen den laufenden Server, und
     * dieser Leser soll ohne Server pruefbar bleiben. Dieselbe Begruendung wie bei
     * {@code blockbuch.benutzbar} und {@code verdienst.saatgut}.
     *
     * <p>Eine LEERE Liste ist erlaubt und heisst "alles darf heraus" - kein Rueckfall auf
     * die Vorgabe, denn wer eine leere Liste eintraegt, meint nicht die Vorgabe.
     */
    private static Set<String> verboten(ConfigurationSection wurzel, List<String> klagen,
                                        List<String> bericht) {
        String pfad = Truhenwerte.PFAD_VERBOTEN;
        if (!wurzel.contains(pfad, true)) {
            bericht.add(pfad + " fehlt - ab Werk ist NICHTS verboten: Alles darf hinein"
                    + " und heraus.");
            return Truhenwerte.VORGABE_VERBOTEN;
        }
        List<?> gelesen = wurzel.getList(pfad);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(pfad)) + ") - verworfen."
                    + " Erwartet wird eine Liste in eckigen Klammern oder mit Strichen."
                    + " " + Wirtschaftsliste.esBleibt(
                            Truhenwerte.VORGABE_VERBOTEN.isEmpty()
                                    ? "keine Verbote"
                                    : Truhenwerte.VORGABE_VERBOTEN.size() + " Namen"));
            return Truhenwerte.VORGABE_VERBOTEN;
        }
        Set<String> sauber = new LinkedHashSet<>();
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Name - uebersprungen.");
                continue;
            }
            if (Material.getMaterial(name.trim().toUpperCase(Locale.ROOT)) == null) {
                klagen.add(pfad + ": \"" + name + "\" ist kein Material, das dieser"
                        + " Server kennt - uebersprungen. Erwartet wird der nackte Name"
                        + " wie written_book oder shulker_box, ohne minecraft: davor.");
                continue;
            }
            sauber.add(name.trim());
        }
        // Kleingeschrieben wird NICHT hier, sondern an der EINEN Stelle, die es
        // zusichert: in Truhenwerte. Gezaehlt wird darum, was dort ankommt - sonst meldet
        // der Bericht bei "STONE" und "stone" zwei Gegenstaende, und die Truhe kennt
        // einen.
        Set<String> verbote = Truhenwerte.wieAbgelegt(sauber);
        bericht.add(pfad + ": " + verbote.size() + " Gegenstaende.");
        return verbote;
    }

    /**
     * Die Sammelzeile ueber fehlende Zeilen - wie bei {@link Mietliste}.
     *
     * <p>EINE Zeile und nicht zwei: Die beiden Lesungen nennen ihre fehlende Zeile
     * ohnehin je einzeln (das besorgt {@code ganzeZahl} und die Verbotsliste selbst).
     * Diese Zeile sagt, wie VIELE es sind - wer "2 von 2 Zeilen fehlen" liest, weiss,
     * dass er den ganzen Abschnitt nicht hat, und sucht nicht nach einer einzelnen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen der"
                    + " Freien Truhe fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }
}
