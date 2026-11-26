package de.freimarken.paper;

import de.freimarken.kern.Weltregel;
import de.freimarken.kern.Weltregelwerte;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code weltregeln:} - die Vorgabe und die Auswahl je Welt.
 *
 * <p>Dieselbe Bauart wie {@link Blockbuchliste}: reine Dateiarbeit, gesammelte Klagen
 * statt Ausnahmen, Weisslisten von Namen statt Wahrheitswerten, und jeder Weltname wird
 * gegen die laufenden Welten geprueft. Die Begruendungen stehen dort und an
 * {@link Weltregel}.
 *
 * <p><b>Die Welten kommen als NAME herein und als SCHLUESSEL heraus</b> - derselbe
 * Schluesselmacher, derselbe Wall gegen die falsch verdrahtete Naht. Bei einem SCHUTZ
 * waere der stumme Fehler besonders teuer: Eine Karte nach Namen trifft im Ereignis nie,
 * und der Betreiber haette eine Welt eingetragen, die ungeschuetzt bleibt.
 */
final class Weltregelliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Weltregelwerte.PFAD_REGELN,
            Weltregelwerte.PFAD_WELTEN);

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Weltregelwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Weltregelliste() {
    }

    /**
     * Der ganze Abschnitt {@code weltregeln:}.
     *
     * @param wurzel         die ganze Konfiguration, darf {@code null} sein
     * @param bekannteWelten die Weltnamen, die es laut {@code welten:} gibt
     * @param macher         wandelt einen Weltnamen in den Schluessel um
     * @param parzellenwelt  der NAME der einen Welt mit Parzellenraster; nur
     *                       dort kann eine Grenzregel ueberhaupt wirken. Leer
     *                       heisst "nicht bekannt" - dann wird nicht geprueeft,
     *                       denn ein Leser soll keine Parzellenwelt erfinden.
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
                                     Set<String> bekannteWelten,
                                     Blockbuchliste.Schluesselmacher macher,
                                     String parzellenwelt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Weltregelwerte.VORGABE, klagen, bericht);
        }
        if (macher == null) {
            throw new IllegalArgumentException("Ohne Schluesselmacher waere jede Auswahl"
                    + " nach dem WeltNAMEN geschluesselt, und die Wache fragt nach dem"
                    + " SCHLUESSEL - der Vergleich waere immer ungleich, und der Schutz"
                    + " bliebe stumm aus");
        }
        nenneFehlende(wurzel, bericht);

        Set<Weltregel> vorgabe = auswahl(wurzel, Weltregelwerte.PFAD_REGELN,
                Weltregelwerte.PFAD_REGELN, Weltregelwerte.VORGABE_REGELN, klagen,
                bericht);
        Map<String, Set<Weltregel>> jeWelt = jeWelt(
                wurzel.getConfigurationSection(Weltregelwerte.PFAD_WELTEN),
                bekannteWelten == null ? Set.of() : bekannteWelten, macher, klagen,
                bericht, parzellenwelt);

        Weltregelwerte werte = new Weltregelwerte(vorgabe, jeWelt);
        nenneWasGilt(werte, bericht);
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Eine Auswahl von Regeln - jeder Name geprueft, ein unbekannter uebersprungen.
     *
     * <p>Fehlt die Zeile ganz, gilt die Vorgabe des Programms (und die ist LEER). Eine
     * LEERE Liste ist dagegen erlaubt und heisst etwas: in dieser Welt gilt keine Regel.
     * Kein Rueckfall - wer eine leere Liste eintraegt, meint nicht die Vorgabe.
     */
    private static Set<Weltregel> auswahl(ConfigurationSection wurzel, String schluessel,
                                          String pfad, Set<Weltregel> vorgabe,
                                          List<String> klagen, List<String> bericht) {
        if (!wurzel.contains(schluessel, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gilt "
                    + Weltregelwerte.wieEingetragen(vorgabe) + " aus dem Programm.");
            return vorgabe;
        }
        List<?> gelesen = wurzel.getList(schluessel);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(schluessel))
                    + ") - verworfen. Erwartet wird eine Liste in eckigen Klammern oder"
                    + " mit Strichen, etwa [explosion, feuer]. Ein true oder false gibt"
                    + " es hier mit Absicht nicht: Bukkit liest jedes andere Wort stumm"
                    + " als false, und dann waere der Schutz aus, ohne dass es jemand"
                    + " merkt. "
                    + Wirtschaftsliste.esBleibt(
                            Weltregelwerte.wieEingetragen(vorgabe)));
            return vorgabe;
        }
        Set<Weltregel> regeln = EnumSet.noneOf(Weltregel.class);
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Name - uebersprungen. Erlaubt sind "
                        + String.join(", ", Weltregel.erlaubteNamen()) + ".");
                continue;
            }
            Weltregel regel = Weltregel.ausSchluessel(name);
            if (regel == null) {
                klagen.add(pfad + ": \"" + name + "\" ist keine Weltregel, die dieses"
                        + " Plugin kennt - uebersprungen. Erlaubt sind "
                        + String.join(", ", Weltregel.erlaubteNamen()) + "; weitere"
                        + " kommen mit ihrem eigenen Bauschritt, weil jede ein eigenes"
                        + " Ereignis braucht. Ohne diese Pruefung stuende der Tippfehler"
                        + " stumm in der Liste, und du haeltest eine Welt fuer"
                        + " geschuetzt, die es nicht ist.");
                continue;
            }
            regeln.add(regel);
        }
        return regeln;
    }

    /**
     * Die Auswahl je Welt - nach SCHLUESSEL geschluesselt.
     *
     * <p>Eine kaputte Zeile laesst die uebrigen stehen: Ein Server, der wegen eines
     * Tippfehlers in einer von vier Welten nicht startet, ist schlimmer als einer mit
     * drei geschuetzten Welten.
     */
    private static Map<String, Set<Weltregel>> jeWelt(
            ConfigurationSection abschnitt, Set<String> bekannteWelten,
            Blockbuchliste.Schluesselmacher macher, List<String> klagen,
            List<String> bericht, String parzellenwelt) {
        Map<String, Set<Weltregel>> jeWelt = new LinkedHashMap<>();
        if (abschnitt == null) {
            // BERICHT und keine Klage, derselbe Grund wie bei der Blockbuchliste: Eine
            // Klage verhindert das ganze Neuladen, und auf einem Server, dessen
            // config.yml den Abschnitt noch nicht hat - eine vorhandene Datei wird beim
            // Update NIE ueberschrieben -, waere das eine Strafe fuer nichts.
            //
            // HIER IST ES ABER KEIN HARMLOSER FALL, und das sagt die Zeile auch: Ohne
            // den Abschnitt gilt ueberall die leere Vorgabe, also KEIN Schutz. Beim
            // Blockbuch war die Vorgabe die sichere Richtung; hier ist sie die offene.
            bericht.add("Es gibt keinen Abschnitt " + Weltregelwerte.PFAD_WELTEN
                    + " in der config.yml - damit gilt in JEDER Welt die Vorgabe aus "
                    + Weltregelwerte.PFAD_REGELN + ", und die ist ab Werk LEER. Es"
                    + " greift also keine Weltregel: TNT, Feuer und Lava richten"
                    + " ueberall Schaden an. Wer das nicht will, traegt die Welt dort"
                    + " mit ihrer Liste ein.");
            return jeWelt;
        }
        for (String weltname : abschnitt.getKeys(false)) {
            String pfad = Weltregelwerte.PFAD_WELTEN + "." + weltname;
            String sauber = weltname.trim().toLowerCase(Locale.ROOT);
            if (!bekannteWelten.contains(sauber)) {
                klagen.add(pfad + ": Die Welt \"" + weltname + "\" steht nicht in"
                        + " welten: - uebersprungen. Eine Auswahl fuer eine Welt, die es"
                        + " nicht gibt, ist eine Zeile, die nie wirkt, und an der Datei"
                        + " sieht man das nicht.");
                continue;
            }
            String schluessel = macher.fuer(sauber);
            // DER WALL GEGEN DIE FALSCH VERDRAHTETE NAHT - dieselbe Begruendung wie in
            // Blockbuchliste, und hier waere der Schaden groesser: Dort bliebe ein Buch
            // leer, hier bliebe eine Welt UNGESCHUETZT, obwohl sie eingetragen ist.
            if (schluessel == null || schluessel.indexOf(':') < 0) {
                klagen.add(pfad + ": Der Weltschluessel zu \"" + weltname + "\" ist \""
                        + schluessel + "\" - das ist ein NAME und kein Schluessel."
                        + " Erwartet wird die Form \"namensraum:name\". DAS IST KEIN"
                        + " FEHLER IN DEINER DATEI, sondern einer im Plugin: Der"
                        + " Schluesselmacher ist falsch verdrahtet. Diese Welt wird"
                        + " uebersprungen - sonst bliebe sie stumm ungeschuetzt.");
                continue;
            }
            jeWelt.put(schluessel, ohneWirkungslose(
                    auswahl(abschnitt, weltname, pfad,
                            Weltregelwerte.VORGABE_REGELN, klagen, bericht),
                    sauber, parzellenwelt, pfad, klagen));
        }
        return jeWelt;
    }

    /**
     * Nennt im Bericht, was WIRKLICH gilt - je Welt eine Zeile.
     *
     * <p>Damit der Betreiber im Protokoll sieht, was gilt, und nicht nur, was er glaubt
     * eingetragen zu haben. Bei einem Schutz ist das die wichtigere Richtung: Ein stumm
     * nicht greifender Schutz ist genau der Zustand, in dem jemand einen Krater findet
     * und das Plugin fuer kaputt haelt.
     */
    private static void nenneWasGilt(Weltregelwerte werte, List<String> bericht) {
        if (!werte.greiftIrgendwo()) {
            bericht.add("Es greift KEINE Weltregel - weder die Vorgabe noch eine"
                    + " einzelne Welt nennt eine. TNT, Feuer, Lava, Kolben und Enderman"
                    + " richten ueberall Schaden an, auch auf fremden Parzellen. Wer das"
                    + " will, ist richtig; wer es nicht will, traegt in "
                    + Weltregelwerte.PFAD_REGELN + " oder bei einer Welt etwas ein.");
            return;
        }
        List<String> teile = new ArrayList<>();
        teile.add("Vorgabe " + Weltregelwerte.wieEingetragen(werte.vorgabe()));
        for (Map.Entry<String, Set<Weltregel>> eintrag : werte.jeWelt().entrySet()) {
            teile.add(eintrag.getKey() + " "
                    + Weltregelwerte.wieEingetragen(eintrag.getValue()));
        }
        bericht.add("Weltregeln: " + String.join(", ", teile)
                + ". Explosion, Feuer und Wesen gelten WELTWEIT; Kolben und"
                + " Fluessigkeit nur ueber Besitzgrenzen. /freimarken neuladen"
                + " uebernimmt diese Zeilen.");
    }

    /**
     * Nennt jede Weltregelzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste}:
     * Zwei Berichte, die dieselbe Zahl verschieden nennen, sind schlimmer als zwei
     * Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel,
                                      List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size()
                    + " Weltregelzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie"
                    + " erst, wenn sie in der Datei stehen.");
        }
    }

    /**
     * Nimmt die Regeln heraus, die in dieser Welt nicht wirken KOENNEN.
     *
     * <p><b>Betroffen sind die Grenzregeln in einer Welt ohne Parzellenraster.</b>
     * {@code kolben} und {@code fluessigkeit} fragen eine Besitzgrenze, und die gibt
     * es nur in der einen Parzellenwelt - die {@code Weltwache} bekommt genau einen
     * Weltschluessel dafuer. In jeder anderen Welt ist so ein Eintrag eine Zeile,
     * die dasteht und nichts tut.
     *
     * <p>Das ist dieselbe Gestalt, die bei einer UNBEKANNTEN Welt schon beklagt wird
     * - sie ging nur bei einer bekannten durch. In der ausgelieferten Datei stand
     * sie bei Altfurt, mit dem Kommentar "dort darf gar nichts passieren".
     *
     * <p>HERAUSGENOMMEN und nicht bloss beklagt: Bliebe die Regel in den Werten,
     * behauptete {@code greift} einen Schutz, den es nicht gibt - und jede Probe
     * darueber prueefte die Behauptung statt der Wirkung.
     *
     * <p>Welche Regel zu welcher Sorte gehoert, entscheidet
     * {@link Weltregel#giltWeltweit} und nicht eine Liste hier: Eine sechste Regel
     * soll an EINER Stelle eingeordnet werden.
     */
    private static Set<Weltregel> ohneWirkungslose(Set<Weltregel> gewaehlt,
                                                   String weltname,
                                                   String parzellenwelt,
                                                   String pfad,
                                                   List<String> klagen) {
        if (parzellenwelt == null || parzellenwelt.isBlank()
                || parzellenwelt.equals(weltname)) {
            return gewaehlt;
        }
        Set<Weltregel> wirkungslos = EnumSet.noneOf(Weltregel.class);
        for (Weltregel regel : gewaehlt) {
            if (!regel.giltWeltweit()) {
                wirkungslos.add(regel);
            }
        }
        if (wirkungslos.isEmpty()) {
            return gewaehlt;
        }
        Set<Weltregel> bleibt = EnumSet.noneOf(Weltregel.class);
        bleibt.addAll(gewaehlt);
        bleibt.removeAll(wirkungslos);
        klagen.add(pfad + ": " + Weltregelwerte.wieEingetragen(wirkungslos)
                + " wirkt in dieser Welt nicht und wurde herausgenommen. Diese"
                + " Regeln fragen eine BESITZGRENZE, und ein Parzellenraster gibt"
                + " es nur in \"" + parzellenwelt + "\". Es bleibt "
                + Weltregelwerte.wieEingetragen(bleibt) + ". Gegen Spielerhand"
                + " schuetzt in dieser Welt der Bauschutz, nicht diese Liste.");
        return bleibt;
    }
}
