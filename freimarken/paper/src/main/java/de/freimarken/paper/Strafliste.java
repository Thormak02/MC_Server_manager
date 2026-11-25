package de.freimarken.paper;

import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafwerte;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code moderation.strafen:} (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p>Ein eigener Leser und nicht ein Teil von {@link Wirtschaftsliste}: Dort liegen die
 * Werte der WIRTSCHAFT. Dieselbe Trennung wie bei {@link Meldeliste},
 * {@link Blockbuchliste} und {@link Teamliste}.
 *
 * <p><b>Die Hoechstdauern werden je ART gelesen, gerechnet aus der Aufzaehlung</b> -
 * nicht aus einer Liste von Hand. Eine vierte fortwirkende {@link Strafart} wird damit
 * von selbst gelesen und von selbst vermisst. Eine aufgezaehlte Liste waere die Stelle,
 * an der sie fehlt, und eine fehlende Hoechstdauer ist ein Riegel, der nicht greift.
 *
 * <p><b>Jeder unbrauchbare Wert faellt EINZELN auf die Vorgabe zurueck</b>, wie bei
 * {@link Meldeliste}: Ein Server ohne Eintrag bekommt genau dasselbe, niemand wird
 * bevorteilt, und die Moderation bleibt arbeitsfaehig. Die Gegenrichtung - den ganzen
 * Abschnitt verwerfen - waere ein Server, auf dem nach einem Tippfehler niemand mehr
 * bannen kann.
 */
final class Strafliste {

    /**
     * Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende.
     *
     * <p>Die Hoechstdauern stehen GERECHNET darin, je fortwirkender Art eine. Wer sie von
     * Hand aufzaehlte, haette die vierte Art hier nicht - und dann vermisst der Bericht
     * eine Zeile nicht, die den Riegel traegt.
     */
    static final List<String> SCHLUESSEL = schluessel();

    record Ergebnis(Strafwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Strafliste() {
    }

    private static List<String> schluessel() {
        List<String> alle = new ArrayList<>();
        for (Strafart art : Strafart.values()) {
            if (art.giltFort()) {
                alle.add(Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel());
            }
        }
        alle.add(Strafwerte.PFAD_BANNLISTE_OEFFENTLICH);
        alle.add(Strafwerte.PFAD_ANSAGEN);
        alle.add(Strafwerte.PFAD_ZEILEN_JE_SEITE);
        alle.add(Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE);
        return List.copyOf(alle);
    }

    /**
     * Der Abschnitt {@code moderation.strafen:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return fertig(Strafwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        Map<Strafart, Long> stunden = new EnumMap<>(Strafart.class);
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            String pfad = Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel();
            stunden.put(art, zahl(wurzel, pfad,
                    Strafwerte.VORGABE.hoechsteStundenFuer(art),
                    Strafwerte::brauchbareStunden,
                    "0 bis " + Strafwerte.HOECHSTE_STUNDEN_GRENZE + " Stunden",
                    klagen));
        }
        boolean oeffentlich = jaNein(wurzel, Strafwerte.PFAD_BANNLISTE_OEFFENTLICH,
                Strafwerte.VORGABE.bannlisteOeffentlich(), klagen);
        boolean ansagen = jaNein(wurzel, Strafwerte.PFAD_ANSAGEN,
                Strafwerte.VORGABE.ansagen(), klagen);
        long zeilen = zahl(wurzel, Strafwerte.PFAD_ZEILEN_JE_SEITE,
                Strafwerte.VORGABE.zeilenJeSeite(), Strafwerte::brauchbareZeilen,
                "1 bis " + Strafwerte.HOECHSTE_ZEILEN, klagen);
        List<String> befehle = befehle(wurzel, klagen, bericht);
        try {
            return fertig(new Strafwerte(stunden, oeffentlich, ansagen, (int) zeilen,
                    befehle), klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt aus einer Zusage, die dieser Leser nicht selbst prueft. Heute kann
            // das nicht eintreten - jeder Wert ist einzeln geprueft -, und genau darum
            // steht der Zweig hier: Wer einen fuenften Wert hinzufuegt und die Pruefung
            // vergisst, bekommt eine Klage und keine Ausnahme beim Start.
            klagen.add("moderation.strafen: " + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt("die Vorgaben des Programms"));
            return fertig(Strafwerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Liste der mitgesperrten Befehle.
     *
     * <p><b>Ein FEHLENDER Schluessel wird gemeldet</b>, obwohl er nur auf die Vorgabe
     * zurueckfaellt - und das ist der Unterschied zu den Zahlen darueber. Wer die Zeile
     * aus der Datei nimmt, meint damit meistens "nichts sperren" und bekommt das
     * Gegenteil: sieben gesperrte Befehle. Umgekehrt ist eine AUSDRUECKLICH leere Liste
     * ({@code []}) ein klarer Wunsch - sie gilt, und {@code Strafwerte.ungereimtheiten()}
     * sagt, was sie bedeutet.
     *
     * <p>Bukkit kann einen Schluessel ohne Wert nicht von einem fehlenden unterscheiden
     * (gemessen in diesem Projekt: {@code leerer-eintrag-verschwindet}). Darum steht in
     * der Berichtzeile beides.
     */
    private static List<String> befehle(ConfigurationSection wurzel,
                                        List<String> klagen, List<String> bericht) {
        String pfad = Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE;
        if (!wurzel.contains(pfad, true)) {
            bericht.add(pfad + " fehlt oder hat keinen Wert - es gelten die "
                    + Strafwerte.VORGABE_STUMM_SPERRT.size() + " Befehle des Programms ("
                    + String.join(", ", Strafwerte.VORGABE_STUMM_SPERRT) + "). Wer"
                    + " KEINEN sperren will, schreibt eine leere Liste: [].");
            return Strafwerte.VORGABE_STUMM_SPERRT;
        }
        Object roh = wurzel.get(pfad);
        if (!(roh instanceof List<?> liste)) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(roh)
                    + " - erwartet wird eine Liste von Befehlsnamen. "
                    + Wirtschaftsliste.esBleibt("die Befehle des Programms"));
            return Strafwerte.VORGABE_STUMM_SPERRT;
        }
        List<String> namen = new ArrayList<>();
        for (Object eintrag : liste) {
            if (eintrag == null || String.valueOf(eintrag).isBlank()) {
                // Ein leerer Eintrag wuerde auf JEDEN Befehl passen oder auf keinen -
                // beides ist kein Zustand. Uebersprungen und geklagt, nicht geworfen:
                // Die anderen Namen sind brauchbar, und eine Stummschaltung, die sechs
                // von sieben Befehlen sperrt, ist besser als keine.
                klagen.add(pfad + " enthaelt einen leeren Eintrag - er wird"
                        + " uebersprungen.");
                continue;
            }
            namen.add(String.valueOf(eintrag));
        }
        return namen;
    }

    /**
     * Die Rechenprobe fuer den Startbericht.
     *
     * <p>Sie nennt die Hoechstdauern JE ART und die Zahl der mitgesperrten Befehle, denn
     * das sind die zwei Angaben, an denen die Wirkung haengt: die Grenze zwischen
     * Moderator und Verwalter (Plan 12c.2) und die Luecke, durch die eine Stummschaltung
     * umgangen wird. Eine Zeile ohne sie waere eine Zusage ohne Zahl.
     */
    static String rechenprobe(Strafwerte werte) {
        if (werte == null) {
            return "Strafen: es gelten die Vorgaben des Programms.";
        }
        StringBuilder satz = new StringBuilder("Strafen: ");
        boolean erste = true;
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            if (!erste) {
                satz.append(", ");
            }
            erste = false;
            long stunden = werte.hoechsteStundenFuer(art);
            satz.append(art.kuerzel()).append(" bis ").append(stunden)
                    .append(" Std.");
            if (stunden >= 24L) {
                satz.append(" (").append(stunden / 24L).append(" Tg.)");
            }
        }
        satz.append(" ohne ").append(StrafBefehle.RECHTE.dauerhaft()).append("; ")
                .append(werte.stummSperrtBefehle().size())
                .append(" Befehl(e) sperrt eine Stummschaltung mit; Bannliste ")
                .append(werte.bannlisteOeffentlich() ? "oeffentlich" : "nur fuer das Team")
                .append(", Ansage ").append(werte.ansagen() ? "an" : "aus").append(".");
        return satz.toString();
    }

    /** Das Ergebnis - und JEDER Rueckweg geht hier durch. */
    private static Ergebnis fertig(Strafwerte werte, List<String> klagen,
                                   List<String> bericht) {
        // Dieselbe Bauart wie in Teamliste.fertig und Meldeliste.fertig, und aus
        // demselben am 25.11.2026 gemessenen Grund: Ein frueher Rueckweg bei fehlendem
        // Abschnitt uebersprang dort die Ungereimtheiten, und der Server, dem die
        // Auskunft am meisten hilft - der ohne Eintrag - bekam sie nicht.
        bericht.addAll(werte.ungereimtheiten());
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Ein Ja/Nein aus der Datei.
     *
     * <p><b>Bukkit liest jedes Wort ausser {@code true} stumm als {@code false}</b> -
     * derselbe Grund wie bei {@link Meldeliste}. Hier waere die Folge noch stiller: Aus
     * einem {@code ansagen: ja} wuerde eine abgeschaltete Ansage, und niemand vermisst
     * eine Zeile, die er nie gesehen hat.
     */
    private static boolean jaNein(ConfigurationSection wurzel, String pfad,
                                  boolean vorgabe, List<String> klagen) {
        if (!wurzel.contains(pfad, true)) {
            return vorgabe;
        }
        Object roh = wurzel.get(pfad);
        if (roh instanceof Boolean wahrheit) {
            return wahrheit;
        }
        klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(roh)
                + " - erwartet wird true oder false. "
                + Wirtschaftsliste.esBleibt(vorgabe ? "true" : "false"));
        return vorgabe;
    }

    /**
     * Welche Schluessel dieses Abschnitts in der Datei fehlen - fuer den Bericht.
     *
     * <p>Dieselbe Bauart wie in {@link Anmeldeliste}. <b>Die Liste war gebaut und nicht
     * angeschlossen</b> [gefunden am 25.11.2026 beim Bau von Teil B]: Ein Betreiber, der
     * eine Zeile aus der Datei nimmt, bekommt die Vorgabe - und erfuhr es nicht. Genau
     * die Art Luecke, die dieses Projekt schon mehrfach getroffen hat: ein gebautes,
     * gruen gepruefbares Stueck, das niemand verdrahtet hat.
     */
    private static void nenneFehlende(ConfigurationSection wurzel,
                                      List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String schluessel : SCHLUESSEL) {
            if (!wurzel.isSet(schluessel)) {
                fehlen.add(schluessel);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add("Nicht in der Datei, es gilt die Vorgabe: "
                    + String.join(", ", fehlen));
        }
    }

    /** Eine ganze Zahl aus der Datei, gegen ihre eigene Regel geprueft. */
    private static long zahl(ConfigurationSection wurzel, String pfad, long vorgabe,
                             java.util.function.LongPredicate brauchbar,
                             String erwartet, List<String> klagen) {
        if (!wurzel.contains(pfad, true)) {
            return vorgabe;
        }
        Object roh = wurzel.get(pfad);
        // Als long geprueft, obwohl ein Feld int ist - aus dem Grund, der an
        // Wirtschaftswerte.brauchbareAnzahl steht: Wer vorher auf int verkuerzt, prueft
        // nicht mehr den getippten Wert, sondern dessen untere 32 Bit.
        if (!(roh instanceof Number zahl) || zahl.doubleValue() != Math.floor(
                zahl.doubleValue()) || !brauchbar.test(zahl.longValue())) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(roh)
                    + " - erwartet wird eine ganze Zahl von " + erwartet + ". "
                    + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return zahl.longValue();
    }

}
