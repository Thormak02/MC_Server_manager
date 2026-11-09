package de.freimarken.paper;

import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blocktat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest das Blockbuch aus der {@code config.yml} - Frist, Happen und die Auswahl je Welt.
 *
 * <p>Nach demselben Muster wie {@link Verdienstliste} und {@link Wirtschaftsliste}: reine
 * Dateiarbeit, gesammelte Klagen statt Ausnahmen, und der Rueckfall steht im Programm.
 *
 * <p><b>Ein eigener Leser und kein Anbau an {@link Wirtschaftsliste}</b>, und das ist eine
 * Entscheidung mit Grund: Dort faellt bei EINER Klage der ganze Wirtschaftsblock nicht
 * mehr neu - ein Tippfehler im Blockbuch koennte sonst verhindern, dass der Betreiber
 * einen Preis aendert. Zwei Dinge, die nichts miteinander zu tun haben, sollen sich nicht
 * gegenseitig blockieren.
 *
 * <p><b>Keine Wahrheitswerte, sondern Weisslisten von Namen.</b> Die Begruendung steht in
 * {@link Blockbuchwerte}: Bukkit liest jedes Wort ausser {@code true} stumm als
 * {@code false}, und aus einem {@code benutzen: ja} wuerde ein abgeschaltetes Blockbuch
 * ohne eine Zeile Klage. Dieselbe Bauart wie {@code verdienst.spawngruende}.
 *
 * <p><b>Jeder Weltname wird gegen die laufenden Welten geprueft</b> - dieselbe Trennung
 * wie bei den Berufen in {@link Verdienstliste}: Eine Auswahl fuer eine Welt, die es nicht
 * gibt, ist eine Zeile, die nie wirkt, und an der Datei sieht man das nicht.
 *
 * <p><b>Die Welten kommen als NAME herein und als SCHLUESSEL heraus.</b> Die
 * {@code config.yml} schreibt {@code altfurt}, die Welt traegt {@code freimarken:altfurt},
 * und der Vergleich der beiden ist immer ungleich - genau der Fehler, der am 09.11.2026 im
 * Spiel auffiel und den ganzen Ladenbereich tot legte. Umgewandelt wird darum hier, beim
 * Lesen, und zwar mit dem {@link Schluesselmacher}, den das Plugin mitgibt: So steht die
 * Umwandlung weiter an genau EINER Stelle, und die Wache vergleicht nur noch Schluessel
 * mit Schluesseln.
 */
final class Blockbuchliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Blockbuchwerte.PFAD_FRIST,
            Blockbuchwerte.PFAD_TATEN,
            Blockbuchwerte.PFAD_HAPPEN,
            Blockbuchwerte.PFAD_BENUTZBAR,
            Blockbuchwerte.PFAD_REICHWEITE,
            Blockbuchwerte.PFAD_ZEILEN,
            Blockbuchwerte.PFAD_WELTEN);

    /**
     * Woher der Weltschluessel kommt.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk, aus demselben Grund wie
     * {@link Verdienstwache.Taktquelle}: Ohne sie braeuchte jede Probe dieses Lesers ein
     * {@code JavaPlugin}, und das laesst sich ohne laufenden Server nicht bauen -
     * {@code NamespacedKey} verlangt es. Der Zweig, in dem die Auswahl je Welt entsteht,
     * waere dann der einzige ungepruefte dieses Schrittes, und er ist genau der, in dem
     * der Fehler vom 09.11.2026 steckte.
     */
    interface Schluesselmacher {

        /**
         * Der Schluessel zu einem Weltnamen aus der Datei.
         *
         * @param weltname wie er in der {@code config.yml} steht, etwa {@code altfurt}
         * @return der Schluessel, etwa {@code freimarken:altfurt}
         */
        String fuer(String weltname);
    }

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Blockbuchwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Blockbuchliste() {
    }

    /**
     * Der ganze Abschnitt {@code blockbuch:}.
     *
     * @param wurzel         die ganze Konfiguration, darf {@code null} sein
     * @param bekannteWelten die Weltnamen, die es laut {@code welten:} gibt - gegen sie
     *                       wird jede Auswahl geprueft. NAMEN, nicht Schluessel: So steht
     *                       in der Klage dasselbe Wort, das der Betreiber getippt hat
     * @param macher         wandelt einen Weltnamen in den Schluessel um
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
                                     Set<String> bekannteWelten,
                                     Schluesselmacher macher) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Blockbuchwerte.VORGABE, klagen, bericht);
        }
        if (macher == null) {
            throw new IllegalArgumentException("Ohne Schluesselmacher waere jede Auswahl"
                    + " nach dem WeltNAMEN geschluesselt, und die Wache fragt nach dem"
                    + " SCHLUESSEL - der Vergleich waere immer ungleich");
        }
        nenneFehlende(wurzel, bericht);

        int frist = zahl(wurzel, Blockbuchwerte.PFAD_FRIST,
                Blockbuchwerte.VORGABE_FRIST_TAGE, Blockbuchwerte.HOECHSTE_FRIST_TAGE,
                "Tage", Blockbuchwerte::brauchbareFrist,
                "Eine 0 hiesse, dass jede Zeile beim naechsten Aufraeumen weggeht - wer"
                        + " nichts aufschreiben will, laesst die Tatenliste leer.",
                klagen, bericht);
        int happen = zahl(wurzel, Blockbuchwerte.PFAD_HAPPEN,
                Blockbuchwerte.VORGABE_HAPPEN, Blockbuchwerte.HOECHSTER_HAPPEN,
                "Zeilen", Blockbuchwerte::brauchbarerHappen,
                "Eine 0 hiesse, dass nie etwas weggeraeumt wird, und die Tabelle waechst"
                        + " dann ohne Ende.",
                klagen, bericht);
        int reichweite = zahl(wurzel, Blockbuchwerte.PFAD_REICHWEITE,
                Blockbuchwerte.VORGABE_REICHWEITE, Blockbuchwerte.HOECHSTE_REICHWEITE,
                "Bloecke", Blockbuchwerte::brauchbareReichweite,
                "Eine 0 hiesse, dass der Befehl nie einen Block trifft; ueber hundert"
                        + " trifft der Strahl irgendetwas, und niemand wuesste mehr,"
                        + " welcher Block gefragt wurde.",
                klagen, bericht);
        int zeilen = zahl(wurzel, Blockbuchwerte.PFAD_ZEILEN,
                Blockbuchwerte.VORGABE_ZEILEN, Blockbuchwerte.HOECHSTE_ZEILEN,
                "Zeilen", Blockbuchwerte::brauchbareZeilen,
                "Eine 0 waere die stille Form von \"der Befehl findet nie etwas\".",
                klagen, bericht);
        Blockbuchwerte.Auswahl vorgabe = auswahl(wurzel, Blockbuchwerte.PFAD_TATEN,
                Blockbuchwerte.PFAD_TATEN, Blockbuchwerte.VORGABE_AUSWAHL, klagen,
                bericht);
        Set<String> benutzbar = bloecke(wurzel, klagen, bericht);
        Map<String, Blockbuchwerte.Auswahl> jeWelt = jeWelt(
                wurzel.getConfigurationSection(Blockbuchwerte.PFAD_WELTEN),
                bekannteWelten == null ? Set.of() : bekannteWelten, macher, klagen,
                bericht);

        Blockbuchwerte werte = new Blockbuchwerte(frist, happen, vorgabe, jeWelt,
                benutzbar, reichweite, zeilen);
        nenneWasGilt(werte, bericht);
        return new Ergebnis(werte, klagen, bericht);
    }

    /** Ob eine gelesene Zahl im erlaubten Bereich liegt. */
    private interface Bereichspruefer {
        boolean brauchbar(long gelesen);
    }

    /**
     * Eine Anzahl mit Obergrenze - Frist, Happen, Reichweite oder Zeilenzahl.
     *
     * <p>Geteilt, weil es dieselbe Arbeit ist: lesen, Bereich pruefen, bei einem
     * unbrauchbaren Wert klagen und die Vorgabe nehmen. Ein unbrauchbarer Wert ist eine
     * KLAGE und nicht nur ein Bericht - wer eine 0 eintraegt, meint nicht die Vorgabe.
     *
     * <p><b>Pruefer und Hinweis kommen als Parameter herein</b>, nicht als Verzweigung
     * ueber den Pfad. Hier stand einmal ein {@code pfad.equals(PFAD_FRIST) ? ... : ...}
     * fuer zwei Zahlen; bei der dritten waere daraus eine Kette geworden, in der die
     * NEUE Zahl stumm den Pruefer der letzten bekommt - also einen Bereich, den niemand
     * eingetragen hat. Mit dem Parameter ist eine vergessene Zuordnung ein
     * Uebersetzungsfehler.
     *
     * @param hinweis was eine 0 oder ein zu grosser Wert bedeutet hiesse - der Satz, der
     *                dem Betreiber sagt, warum sein Wert verworfen wurde
     */
    private static int zahl(ConfigurationSection wurzel, String pfad, int vorgabe,
                            int hoechstens, String einheit, Bereichspruefer pruefer,
                            String hinweis, List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        if (!pruefer.brauchbar(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1 bis "
                    + hoechstens + " " + einheit + ". " + hinweis + " "
                    + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return (int) (long) gelesen;
    }

    /**
     * Eine Auswahl von Taten - jeder Name geprueft, ein unbekannter uebersprungen.
     *
     * <p>Fehlt die Zeile ganz, gilt die Vorgabe des Programms. Eine LEERE Liste ist
     * dagegen erlaubt und heisst etwas: in dieser Welt wird nichts aufgeschrieben. Kein
     * Rueckfall - wer eine leere Liste eintraegt, meint nicht die Vorgabe. Dieselbe
     * Entscheidung wie bei der Saatgutliste.
     */
    private static Blockbuchwerte.Auswahl auswahl(ConfigurationSection wurzel,
                                                  String schluessel, String pfad,
                                                  Blockbuchwerte.Auswahl vorgabe,
                                                  List<String> klagen,
                                                  List<String> bericht) {
        if (!wurzel.contains(schluessel, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gilt "
                    + vorgabe.wieEingetragen() + " aus dem Programm.");
            return vorgabe;
        }
        List<?> gelesen = wurzel.getList(schluessel);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(schluessel))
                    + ") - verworfen."
                    + " Erwartet wird eine Liste in eckigen Klammern oder mit Strichen,"
                    + " etwa [setzen, abbauen, abgewiesen]. Ein true oder false gibt es"
                    + " hier mit Absicht nicht: Bukkit liest jedes andere Wort stumm als"
                    + " false, und dann waere das Blockbuch aus, ohne dass es jemand"
                    + " merkt. " + Wirtschaftsliste.esBleibt(vorgabe.wieEingetragen()));
            return vorgabe;
        }
        Set<Blocktat> taten = EnumSet.noneOf(Blocktat.class);
        boolean auchAbgewiesen = false;
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Name - uebersprungen. Erlaubt sind "
                        + String.join(", ", Blocktat.erlaubteNamen()) + ".");
                continue;
            }
            String sauber = name.trim().toLowerCase(Locale.ROOT);
            if (sauber.equals(Blocktat.NAME_ABGEWIESEN)) {
                auchAbgewiesen = true;
                continue;
            }
            Blocktat tat = Blocktat.ausSchluessel(sauber);
            if (tat == null) {
                klagen.add(pfad + ": \"" + name + "\" ist keine Tat, die das Blockbuch"
                        + " kennt - uebersprungen. Erlaubt sind "
                        + String.join(", ", Blocktat.erlaubteNamen()) + "; weitere"
                        + " kommen mit ihrem eigenen Bauschritt, weil jede ein eigenes"
                        + " Ereignis braucht. Ohne diese Pruefung stuende der Tippfehler"
                        + " stumm in der Liste, und du suchtest die Zeilen, die du"
                        + " aufzuschreiben glaubtest.");
                continue;
            }
            taten.add(tat);
        }
        if (taten.isEmpty() && auchAbgewiesen) {
            // Die eine Stelle, an der eine Liste sich selbst widerspricht: "abgewiesen"
            // wirkt QUER zu den Taten und schaltet keine zu. Ohne eine Tat daneben ist
            // die Zeile stumm wirkungslos, und sie sieht aus wie eine Einstellung.
            klagen.add(pfad + " nennt nur \"" + Blocktat.NAME_ABGEWIESEN + "\" und keine"
                    + " einzige Tat - damit wird NICHTS aufgeschrieben. Jenes Wort"
                    + " schaltet die misslungenen Versuche zu den Taten zu, die daneben"
                    + " stehen; es ist selbst keine. Gemeint ist vermutlich [setzen,"
                    + " abbauen, " + Blocktat.NAME_ABGEWIESEN + "].");
        }
        return new Blockbuchwerte.Auswahl(taten, auchAbgewiesen);
    }

    /**
     * Welche Bloecke die Tat {@code benutzen} aufschreibt.
     *
     * <p>Jeder Name wird gegen {@link Material#getMaterial(String)} geprueft, und ein
     * unbekannter ist eine Klage MIT dem Namen - dieselbe Bauart und dieselbe Begruendung
     * wie bei {@code verdienst.saatgut}: Ohne die Pruefung stuende ein Tippfehler stumm
     * in der Liste, und der Betreiber suchte die Zeilen, die er aufzuschreiben glaubte.
     *
     * <p>Geprueft wird mit {@code getMaterial} und NICHT mit {@code matchMaterial} oder
     * der Registry: Jene fragen den laufenden Server, und dieser Leser soll ohne Server
     * pruefbar bleiben. Es gibt darum auch keine Pruefung "ist das auch ein Block" -
     * {@code Material.isBlock()} schlaegt in der Registry nach und wirft, gemessen gegen
     * das echte Jar. Ein Eintrag wie {@code diamond_sword} ist damit erlaubt und wirkt
     * nie; das kostet nichts, denn ein Schwert kann man nicht anklicken.
     *
     * <p>Eine LEERE Liste ist erlaubt und heisst "benutzen schreibt nichts". Kein
     * Rueckfall auf die Vorgabe - wer eine leere Liste eintraegt, meint nicht die
     * Vorgabe.
     */
    private static Set<String> bloecke(ConfigurationSection wurzel, List<String> klagen,
                                       List<String> bericht) {
        String pfad = Blockbuchwerte.PFAD_BENUTZBAR;
        if (!wurzel.contains(pfad, true)) {
            bericht.add(pfad + " steht nicht in deiner config.yml - es gelten die "
                    + Blockbuchwerte.VORGABE_BENUTZBAR.size()
                    + " Behaelter aus dem Programm.");
            return Blockbuchwerte.VORGABE_BENUTZBAR;
        }
        List<?> gelesen = wurzel.getList(pfad);
        if (gelesen == null) {
            klagen.add(pfad + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(pfad)) + ") - verworfen."
                    + " Erwartet wird eine Liste von Blocknamen, etwa [chest, barrel]. "
                    + Wirtschaftsliste.esBleibt(
                            Blockbuchwerte.VORGABE_BENUTZBAR.size())
                    + " Behaelter aus dem Programm.");
            return Blockbuchwerte.VORGABE_BENUTZBAR;
        }
        Set<String> sauber = new LinkedHashSet<>();
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(pfad + ": " + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Blockname - uebersprungen.");
                continue;
            }
            if (Material.getMaterial(name.trim().toUpperCase(Locale.ROOT)) == null) {
                klagen.add(pfad + ": \"" + name + "\" ist kein Material, das dieser"
                        + " Server kennt - uebersprungen. Erwartet wird der nackte Name"
                        + " wie chest oder barrel, ohne minecraft: davor.");
                continue;
            }
            sauber.add(name.trim().toLowerCase(Locale.ROOT));
        }
        bericht.add(pfad + ": " + sauber.size() + " Bloecke.");
        return sauber;
    }

    /**
     * Die Auswahl je Welt - nach SCHLUESSEL geschluesselt.
     *
     * <p>Eine kaputte Zeile laesst die uebrigen stehen: Ein Server, der wegen eines
     * Tippfehlers in einer von vier Welten nicht startet, ist schlimmer als einer mit
     * drei.
     */
    private static Map<String, Blockbuchwerte.Auswahl> jeWelt(
            ConfigurationSection abschnitt, Set<String> bekannteWelten,
            Schluesselmacher macher, List<String> klagen, List<String> bericht) {
        Map<String, Blockbuchwerte.Auswahl> jeWelt = new LinkedHashMap<>();
        if (abschnitt == null) {
            // BERICHT und keine Klage, derselbe Grund wie bei der Lohnliste: Eine Klage
            // verhindert das ganze Neuladen, und auf einem Server, dessen config.yml den
            // Abschnitt noch nicht hat - eine vorhandene Datei wird beim Update NIE
            // ueberschrieben -, waere das eine Strafe fuer nichts. Gefahrlos ist es,
            // weil dann ueberall die Vorgabe gilt, und die schreibt etwas.
            bericht.add("Es gibt keinen Abschnitt " + Blockbuchwerte.PFAD_WELTEN
                    + " in der config.yml - in JEDER Welt gilt dann die Vorgabe aus "
                    + Blockbuchwerte.PFAD_TATEN + ". Wer eine Welt ausnehmen oder ihr"
                    + " mehr geben will, traegt sie dort mit ihrer eigenen Liste ein.");
            return jeWelt;
        }
        for (String weltname : abschnitt.getKeys(false)) {
            String pfad = Blockbuchwerte.PFAD_WELTEN + "." + weltname;
            String sauber = weltname.trim().toLowerCase(Locale.ROOT);
            if (!bekannteWelten.contains(sauber)) {
                klagen.add(pfad + ": Die Welt \"" + weltname + "\" steht nicht in"
                        + " welten: - uebersprungen. Eine Auswahl fuer eine Welt, die es"
                        + " nicht gibt, ist eine Zeile, die nie wirkt, und an der Datei"
                        + " sieht man das nicht.");
                continue;
            }
            // Gelesen wird unter dem NAMEN, geschluesselt wird mit dem SCHLUESSEL, und
            // die Klage nennt den GANZEN Pfad: Der Betreiber soll das Wort lesen, das er
            // getippt hat, und die Wache soll vergleichen, was World.key() liefert.
            String schluessel = macher.fuer(sauber);
            // DER WALL GEGEN DIE FALSCH VERDRAHTETE NAHT. Gibt der Schluesselmacher den
            // rohen Namen zurueck, trifft die Karte im Ereignis NIE - der Vergleich mit
            // World.key().asString() ist immer ungleich, und das Blockbuch bliebe fuer
            // diese Welt stumm leer. Genau dieser Fehler hat am 09.11.2026 den ganzen
            // Ladenbereich tot gelegt, und eine Einspritzung (B11) hat gezeigt, dass ihn
            // keine Probe faengt: Die Naht sitzt im Plugin, und das laesst sich ohne
            // Server nicht starten.
            //
            // Hier eine KLAGE und nicht der Wurf, den Blockbuchwerte wirft: Der Server
            // soll laufen. Die Klage trifft dafuer den Entwickler und nicht den
            // Betreiber - der kann an seiner Datei nichts aendern -, und sie sagt das.
            if (schluessel == null || schluessel.indexOf(':') < 0) {
                klagen.add(pfad + ": Der Weltschluessel zu \"" + weltname + "\" ist \""
                        + schluessel + "\" - das ist ein NAME und kein Schluessel."
                        + " Erwartet wird die Form \"namensraum:name\". DAS IST KEIN"
                        + " FEHLER IN DEINER DATEI, sondern einer im Plugin: Der"
                        + " Schluesselmacher ist falsch verdrahtet. Diese Welt wird"
                        + " uebersprungen - sonst schriebe das Blockbuch dort stumm"
                        + " nichts.");
                continue;
            }
            jeWelt.put(schluessel, auswahl(abschnitt, weltname, pfad,
                    Blockbuchwerte.VORGABE_AUSWAHL, klagen, bericht));
        }
        return jeWelt;
    }

    /**
     * Nennt im Bericht, was WIRKLICH gilt - je Welt eine Zeile.
     *
     * <p>Damit der Betreiber im Protokoll sieht, was gilt, und nicht nur, was er glaubt
     * eingetragen zu haben. Ein stumm abgeschaltetes Blockbuch ist genau der Zustand, in
     * dem jemand Monate spaeter nach Zeilen sucht, die es nie gab - darum sagt es diese
     * Zeile laut.
     */
    private static void nenneWasGilt(Blockbuchwerte werte, List<String> bericht) {
        if (!werte.schreibtIrgendwo()) {
            bericht.add("Das Blockbuch schreibt NICHTS - weder die Vorgabe noch eine"
                    + " einzelne Welt nennt eine Tat. Der Befehl /freimarken blockbuch"
                    + " findet dann nie etwas. Wer das will, ist richtig; wer es nicht"
                    + " will, traegt in " + Blockbuchwerte.PFAD_TATEN + " etwas ein.");
            return;
        }
        // Der Fall, der sonst still durchfiele: benutzen ist eingetragen, die Liste der
        // Bloecke ist aber leer - dann wirkt die Einstellung nur halb, und an der Datei
        // sieht man das nicht. Eine Klage waere es nicht wert (es entsteht kein Schaden),
        // ein Schweigen aber auch nicht.
        if (werte.benutzbareBloecke().isEmpty() && nenntBenutzen(werte)) {
            bericht.add(Blockbuchwerte.PFAD_BENUTZBAR + " ist leer, obwohl \""
                    + Blocktat.BENUTZEN.schluessel() + "\" eingetragen ist - die Tat"
                    + " wirkt damit NICHT. Welche Bloecke zaehlen, steht in jener Liste;"
                    + " ohne sie waere jeder Rechtsklick auf Erde eine Zeile.");
        }
        List<String> teile = new ArrayList<>();
        teile.add("Vorgabe " + werte.vorgabe().wieEingetragen());
        for (Map.Entry<String, Blockbuchwerte.Auswahl> eintrag
                : werte.jeWelt().entrySet()) {
            teile.add(eintrag.getKey() + " " + eintrag.getValue().wieEingetragen());
        }
        bericht.add("Blockbuch: " + String.join(", ", teile) + "; "
                + werte.fristTage() + " Tage Aufbewahrung, hoechstens "
                + werte.jeAufraeumen() + " Zeilen je Aufraeumschlag. Aenderungen daran"
                + " brauchen einen NEUSTART - ein Neuladen fasst das Blockbuch nicht an.");
    }

    /** Ob irgendeine Auswahl die Tat {@code benutzen} nennt - Vorgabe oder eine Welt. */
    private static boolean nenntBenutzen(Blockbuchwerte werte) {
        if (werte.vorgabe().taten().contains(Blocktat.BENUTZEN)) {
            return true;
        }
        for (Blockbuchwerte.Auswahl auswahl : werte.jeWelt().values()) {
            if (auswahl.taten().contains(Blocktat.BENUTZEN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nennt jede Blockbuchzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
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
                    + " Blockbuchzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie"
                    + " erst, wenn sie in der Datei stehen; was das Plugin ohne die"
                    + " Zeile mitbringt, nennt die eigene Zeile zu jedem Schluessel.");
        }
    }
}
