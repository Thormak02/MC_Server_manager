package de.freimarken.paper;

import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Sprungregel;
import de.freimarken.kern.Wirtschaftswerte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Liest die Stammwerte der Wirtschaft aus der {@code config.yml} - der eine Leser.
 *
 * <p>Nach demselben Muster wie {@link Weltenliste}: reine Dateiarbeit, deshalb ohne Server
 * pruefbar, und mit gesammelten KLAGEN statt Ausnahmen. Ein Tippfehler in einer Zahl darf
 * keinen Serverstart mitreissen; er darf aber auch nicht still gelten.
 *
 * <p>Der Unterschied zwischen den beiden Listen ist genau die Entscheidungsgrundlage fuer
 * das Neuladen:
 * <ul>
 *   <li>KLAGE heisst: Was du getippt hast, gilt NICHT. Beim Start gilt dann die Vorgabe,
 *       beim Neuladen wird gar nichts uebernommen.
 *   <li>BERICHT heisst: Es gilt, aber du sollst es gelesen haben - eine fehlende Zeile,
 *       ein Grundpreis von 0, und die Staffel im Klartext.
 * </ul>
 *
 * <p>Gelesen wird ROH ueber {@code get}. Bei einer Weltkante faengt das einen fehlenden
 * Rand; bei einem Preis faengt es ein tausendfach zu billiges Grundstueck:
 * {@code getLong} macht aus {@code grundpreis: 25.000} eine 25 und aus {@code "25000"}
 * eine 0 - beides in die Richtung, die sich nicht zurueckholen laesst, weil das
 * entstandene Geld binnen Minuten auf vielen Konten liegt.
 */
final class Wirtschaftsliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Wirtschaftswerte.PFAD_GRUNDPREIS,
            Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
            Wirtschaftswerte.PFAD_HOECHSTZAHL,
            Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
            Wirtschaftswerte.PFAD_RANG_WERBUNG,
            Wirtschaftswerte.PFAD_STARTGUTHABEN);

    /** Wie viele Preisstufen die Rechenprobe im Protokoll nennt. */
    private static final int STUFEN_IM_BERICHT = 4;

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Wirtschaftswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Wirtschaftsliste() {
    }

    /**
     * @param wurzel die ganze Konfiguration, darf {@code null} sein - die Schluessel
     *               liegen in vier verschiedenen Abschnitten
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            bericht.add("Keine Einstellungen zu lesen - es gelten die Vorgaben.");
            return new Ergebnis(Wirtschaftswerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long grundpreis = betrag(wurzel, Wirtschaftswerte.PFAD_GRUNDPREIS, "grundpreis",
                Preisstaffel.VORGABE_GRUNDPREIS, Preisstaffel.HOECHSTER_GRUNDPREIS,
                "Mark", klagen, bericht);
        long jeFeld = betrag(wurzel, Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
                "zusammenlegung-je-feld", Preisstaffel.VORGABE_ZUSAMMENLEGUNG_JE_FELD,
                Preisstaffel.HOECHSTE_GEBUEHR, "Mark je Feld", klagen, bericht);
        long startguthaben = betrag(wurzel, Wirtschaftswerte.PFAD_STARTGUTHABEN,
                "startguthaben", Wirtschaftswerte.VORGABE_STARTGUTHABEN,
                Wirtschaftswerte.HOECHSTES_STARTGUTHABEN, "Mark", klagen, bericht);
        int hoechstzahl = anzahl(wurzel, Wirtschaftswerte.PFAD_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_HOECHSTZAHL, klagen, bericht);
        int gruppenFelder = anzahl(wurzel, Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER, klagen, bericht);
        String werbung = werbung(wurzel, klagen, bericht);

        Preisstaffel preise = new Preisstaffel(grundpreis, jeFeld);
        if (grundpreis == 0L) {
            bericht.add(Wirtschaftswerte.PFAD_GRUNDPREIS + " ist 0 - jede weitere Parzelle"
                    + " ist damit gratis, und die Bremse gegen Flaechenhorten ist aus.");
        }
        if (jeFeld == 0L) {
            bericht.add(Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG + " ist 0 - Zusammenlegen"
                    + " kostet nichts.");
        }
        // Die Rechenprobe: Die einzige Schranke gegen den Wert, der im erlaubten Bereich
        // liegt und trotzdem falsch ist. Wer "2. Parzelle 25" liest, sieht den Vertipper,
        // den keine Zahlengrenze sieht.
        bericht.add("Preisstaffel: " + preise.alsText(STUFEN_IM_BERICHT)
                + " - Gruppe aus " + gruppenFelder + " Feldern "
                + preise.zusammenlegungsGebuehr(Math.max(gruppenFelder, 2)));
        // Das Startguthaben steht hier mit ABSICHT nicht. Es stand einmal hier, und im
        // Spiel sah man die Folge: zwei Zeilen untereinander nannten denselben
        // Schluessel mit derselben Zahl - "in der Datei: 50000" und darunter "50000 -
        // wird nur beim Start gelesen". Dieser Leser kennt nur die Datei; was GILT, sagt
        // der Aufrufer, beim Start das Protokoll und beim Neuladen der Block darunter.
        // Dass eine fehlende oder unbrauchbare Zeile genannt wird, besorgen ganzeZahl
        // und betrag ohnehin.

        return new Ergebnis(new Wirtschaftswerte(preise, startguthaben, hoechstzahl,
                gruppenFelder, werbung), klagen, bericht);
    }

    /**
     * Ein Geldbetrag: roh gelesen, am Typ geprueft, im Bereich geprueft und gegen einen
     * grossen Sprung gesichert.
     *
     * @param name    der Schluessel im Abschnitt {@code sprung-bestaetigt}
     * @param vorgabe der ausgelieferte Wert - zugleich der Rueckfall UND der Vergleichswert
     *                fuer den Sprung
     */
    private static long betrag(ConfigurationSection wurzel, String pfad, String name,
                               long vorgabe, long hoechstens, String einheit,
                               List<String> klagen, List<String> bericht) {
        Long gelesen = ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < 0 || wert > hoechstens) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt sind 0 bis "
                    + hoechstens + " " + einheit + "; darueber laeuft die Rechnung ueber,"
                    + " und ein uebergelaufener Preis ist NEGATIV - also eine Gutschrift"
                    + " beim Kauf. " + esBleibt(vorgabe));
            return vorgabe;
        }
        if (wert != vorgabe && Sprungregel.grosserSprung(vorgabe, wert)) {
            String schluessel = Wirtschaftswerte.PFAD_BESTAETIGT + "." + name;
            if (!Sprungregel.bestaetigt(wurzel.getString(schluessel), vorgabe, wert)) {
                klagen.add(pfad + " ist " + wert + ", ausgeliefert sind " + vorgabe
                        + " - das ist mehr als Faktor 2, und bei einem Geldwert braucht"
                        + " das eine zweite Bestaetigung: Was zu billig verkauft oder zu"
                        + " reichlich ausgegeben wurde, liegt binnen Minuten auf vielen"
                        + " Konten und ist nur mit einer Ruecksicherung des ganzen"
                        + " Servers zurueckzuholen. " + esBleibt(vorgabe) + " Fuer den"
                        + " Sprung: " + schluessel + ": \""
                        + Sprungregel.sprungText(vorgabe, wert) + "\"");
                return vorgabe;
            }
            // Kein "gilt": Fuer kasse.startguthaben waere das beim Neuladen falsch, denn
            // uebernimm() laesst diesen Wert ausdruecklich stehen - zwei Zeilen derselben
            // Antwort widersprachen sich. Was gilt, sagt der Aufrufer; hier steht nur,
            // dass die Bestaetigung zur Datei passt.
            bericht.add("Bestaetigung erkannt: " + schluessel + " deckt " + pfad + " "
                    + Sprungregel.sprungText(vorgabe, wert) + ".");
        }
        return wert;
    }

    /** Eine Obergrenze: dieselbe Pruefung, aber als {@code int} und mindestens 1. */
    private static int anzahl(ConfigurationSection wurzel, String pfad, int vorgabe,
                              List<String> klagen, List<String> bericht) {
        Long gelesen = ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        // Geprueft wird der long und NICHT der verkuerzte int: (int) -2147483649 ist
        // 2147483647, und eine negative Zahl gaelte so als unbegrenzte Parzellenzahl.
        if (!Wirtschaftswerte.brauchbareAnzahl(wert)) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist eine ganze Zahl"
                    + " von 1 bis " + Integer.MAX_VALUE + "; eine 0 hiesse, niemand darf"
                    + " eine Parzelle. " + esBleibt(vorgabe)
                    + " Dieser Wert ist ohnehin nur die Untergrenze - mehr erlauben die"
                    + " Rechte.");
            return vorgabe;
        }
        return (int) wert;
    }

    /**
     * Die Rangwerbung: Text, leer erlaubt.
     *
     * <p>Keine Zahl und kein Bereich - die einzige Falle ist eine Zeile, die gar kein Text
     * ist. Eine Liste an dieser Stelle kommt als etwas an, dessen {@code toString} dem
     * Spieler ins Gesicht faellt.
     */
    private static String werbung(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        String pfad = Wirtschaftswerte.PFAD_RANG_WERBUNG;
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(fehltText(pfad, "\"" + Wirtschaftswerte.VORGABE_RANG_WERBUNG + "\""));
            return Wirtschaftswerte.VORGABE_RANG_WERBUNG;
        }
        if (!(roh instanceof String text)) {
            klagen.add(pfad + " ist kein Text (" + wieGetippt(roh) + ") - verworfen."
                    + " Es bleibt bei dem Text, der bisher galt (ausgeliefert: \""
                    + Wirtschaftswerte.VORGABE_RANG_WERBUNG + "\"). Mehrere Zeilen"
                    + " gehoeren in Anfuehrungszeichen, nicht in eine Liste.");
            return Wirtschaftswerte.VORGABE_RANG_WERBUNG;
        }
        if (text.isBlank()) {
            bericht.add(pfad + " ist leer - es wird kein Hinweis angehaengt.");
        }
        return text;
    }

    /**
     * Roh lesen und am TYP pruefen - oder {@code null}, wenn daraus keine Zahl wird.
     *
     * <p>Am Typ und nicht am Nachkommarest: YAML macht aus {@code 25.000} eine 25.0, und
     * die ist ganzzahlig. Gemeint waren 25000 - die Kommentare dieser Datei schreiben
     * Tausender selbst mit Punkt. Eine Fliesskommazahl entsteht in YAML nur dort, wo ein
     * Punkt oder ein Exponent steht; als Betrag war sie nie gemeint.
     */
    private static Long ganzeZahl(ConfigurationSection wurzel, String pfad, long vorgabe,
                                  List<String> klagen, List<String> bericht) {
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(fehltText(pfad, String.valueOf(vorgabe)));
            return null;
        }
        if (!(roh instanceof Number zahl)) {
            klagen.add(pfad + " ist keine Zahl (" + wieGetippt(roh) + ") - verworfen. "
                    + esBleibt(vorgabe) + " Anfuehrungszeichen machen aus einer Zahl Text,"
                    + " und getLong liest daraus stillschweigend eine 0.");
            return null;
        }
        double wert = zahl.doubleValue();
        if (zahl instanceof Double || zahl instanceof Float || wert != Math.floor(wert)) {
            klagen.add(pfad + " ist keine ganze Zahl (" + roh + ") - verworfen. "
                    + esBleibt(vorgabe) + " Ein Punkt trennt in YAML keine Tausender,"
                    + " sondern die Nachkommastellen: aus 25.000 wird 25.");
            return null;
        }
        return zahl.longValue();
    }

    /**
     * Was statt des verworfenen Wertes gilt - in einem Satz, der auf BEIDEN Wegen stimmt.
     *
     * <p>Vorher stand hier "Es gilt 25000". Beim Start war das richtig, beim Neuladen
     * nicht: Dort bleibt der laufende Wert stehen, und der kann 40.000 sein. Dann
     * widersprachen sich zwei Zeilen derselben Antwort. Dieser Leser weiss nicht, was
     * gerade gilt - er kennt nur die Datei und die Vorgabe -, also sagt er es auch nicht.
     * Was gilt, sagt der Aufrufer: beim Start die Rechenprobe, beim Neuladen die Zeile
     * "Es gilt unveraendert weiter".
     */
    private static String esBleibt(long vorgabe) {
        return "Es bleibt bei dem Wert, der bisher galt (ausgeliefert: " + vorgabe + ").";
    }

    /**
     * Den gelesenen Wert so zeigen, wie er in der Datei steht.
     *
     * <p>Ein Text in Anfuehrungszeichen kam im Protokoll als {@code (10000)} an - also
     * genau so, wie eine Zahl aussieht. Der Unterschied, um den die ganze Klage geht,
     * war damit unsichtbar.
     */
    private static String wieGetippt(Object roh) {
        return roh instanceof String text ? "\"" + text + "\"" : String.valueOf(roh);
    }

    private static String fehltText(String pfad, String gilt) {
        return pfad + " steht nicht in deiner config.yml - es gilt " + gilt + ". Eine"
                + " vorhandene Datei wird beim Update nie ueberschrieben; die Zeile muss"
                + " von Hand hinein, sonst ist der Wert nicht einstellbar.";
    }

    /** Der Name der Einstellungsdatei - einmal, damit kein zweiter Name entsteht. */
    static final String DATEI = "config.yml";

    /**
     * Ob die Einstellungsdatei ueberhaupt gelesen werden kann - die Luecke, die kein
     * Pfad in dieser Klasse sieht.
     *
     * <p>{@code reloadConfig()} verschluckt jeden Lesefehler: Bei kaputtem YAML loggt
     * Bukkit selbst eine Zeile und liefert eine LEERE Konfiguration, bei fehlender Datei
     * schweigt es ganz. Danach haengt es die Vorgaben aus der Jar an - und weil
     * {@code get} auf die Vorgabe zurueckfaellt, liest dieser Leser brav lauter
     * ausgelieferte Werte, findet nichts zu klagen und meldet Erfolg. Aus einem
     * Tabulator in der Datei wurde so ein gruenes "Uebernommen: grundpreis
     * 200000 -&gt; 25000" - und ein zu billiger Preis macht Mark, die nur eine
     * Ruecksicherung des ganzen Servers zurueckholt.
     *
     * <p>Deshalb wird die Datei hier ein zweites Mal geladen, ohne Vorgaben und mit
     * gesehener Ausnahme. Das kostet eine Dateiarbeit je Neuladen und ist der einzige
     * Weg, "Datei unlesbar" von "Datei leer" zu trennen.
     *
     * @param ordner der Datenordner des Plugins
     * @return die Meldung im Klartext, oder {@code null}, wenn die Datei gelesen wurde
     */
    static String lesefehler(File ordner) {
        File datei = new File(ordner, DATEI);
        if (!datei.isFile()) {
            return DATEI + " liegt nicht in " + ordner.getPath() + " - ohne Datei liefert"
                    + " Bukkit stumm die ausgelieferten Werte, und die waeren hier als"
                    + " deine gemeldet.";
        }
        try {
            new YamlConfiguration().load(datei);
            return null;
        } catch (IOException | InvalidConfigurationException fehler) {
            // SnakeYAML nennt die Quelle 'reader' - wer das liest, sucht nach einer Datei
            // dieses Namens. Die Zeilennummer darin ist das Brauchbare an der Meldung.
            return DATEI + " ist nicht lesbar: "
                    + String.join(" ", String.valueOf(fehler.getMessage())
                            .lines().map(String::trim).toList())
                            .replace("'reader'", DATEI)
                    + " - solange das so ist, kennt der Server deine Werte nicht.";
        }
    }

    /**
     * Nennt jede Zeile, die das Plugin mitbringt, die aber in der Datei des Betreibers
     * nicht steht.
     *
     * <p>Am 02.11.2026 gemessen: Auf dem Probeserver fehlten 16 von 42 Schluesseln, und
     * alle wirkten trotzdem - {@code get} fuer einen einzelnen Schluessel holt die Vorgabe
     * stumm aus der Jar. Genau das ist die Falle: Was der Betreiber nicht sieht, kann er
     * nicht aendern, und Plan 12a.6 verlangt, dass alles Anpassbare anpassbar ist. Kein
     * Grund zur Klage - es gilt ja das Richtige - aber einer, es zu lesen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Wirtschaftszeilen"
                    + " stehen nicht in deiner config.yml: " + String.join(", ", fehlen)
                    + ". Sie gelten aus den Vorgaben des Plugins - sichtbar und aenderbar"
                    + " sind sie erst, wenn sie in der Datei stehen.");
        }
    }
}
