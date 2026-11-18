package de.freimarken.paper;

import de.freimarken.kern.Wolkenbiome;
import de.freimarken.kern.Wolkenwerte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code wolken:} aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Inselliste} und {@link Freistadtliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen
 * gelassen. Ein Tippfehler in einer Zahl darf keinen Serverstart mitreissen.
 *
 * <p><b>Nicht zu verwechseln mit {@code welten.wolkenwerder}</b>: Jener Eintrag beschreibt
 * die WELT - ihre Art, ihre Kante, ihr Tor - und wird von {@link Weltenliste} gelesen.
 * Dieser Abschnitt steht auf oberster Ebene und beschreibt die Inseln darin.
 */
final class Wolkenliste {

    /** In welcher Welt die Wolkenfelder liegen - ohne Namensraum, wie in der Datei. */
    static final String PFAD_WELT = "wolken.welt";

    /** Der ausgelieferte Weltname. */
    static final String VORGABE_WELT = "wolkenwerder";

    /**
     * Der eine Gegenstand, ohne den die ganze Welt nicht spielbar ist.
     *
     * <p>Aus der Lava und dem geschmolzenen Eis entsteht der Kobbelsteingenerator, und
     * aus ihm alles Weitere. Fehlt er in der Starttruhe, sagt es der Bericht - siehe
     * {@link #starttruhe}.
     */
    private static final String LAVA = "lava_bucket";

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            PFAD_WELT, Wolkenwerte.PFAD_PREIS, Wolkenwerte.PFAD_HOECHSTZAHL,
            Wolkenwerte.PFAD_BIOME, Wolkenwerte.PFAD_STARTTRUHE);

    /**
     * @param welt der Weltname OHNE Namensraum, wie er in der Datei steht. Der Schluessel
     *             entsteht erst im Plugin ueber {@code weltschluessel(name)} - hier einen
     *             zu bauen waere Handarbeit, und genau die wird an der naechsten Stelle
     *             vergessen (gefunden am 09.11.2026 im ganzen Ladenbereich)
     */
    record Ergebnis(String welt, Wolkenwerte werte, List<String> klagen,
                    List<String> bericht) {
    }

    private Wolkenliste() {
    }

    /**
     * Der Abschnitt {@code wolken:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(VORGABE_WELT, Wolkenwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        String welt = welt(wurzel, klagen, bericht);
        long preis = Wirtschaftsliste.betrag(wurzel, Wolkenwerte.PFAD_PREIS,
                "wolken-kauf", Wolkenwerte.VORGABE_PREIS, Wolkenwerte.HOECHSTER_BETRAG,
                "Mark einmalig", klagen, bericht);
        int hoechstzahl = hoechstzahl(wurzel, klagen, bericht);
        List<String> biome = biome(wurzel, klagen, bericht);
        Map<String, Integer> truhe = starttruhe(wurzel, klagen, bericht);

        Wolkenwerte werte = new Wolkenwerte(preis, hoechstzahl, biome, truhe);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(welt, werte, klagen, bericht);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Meerinsel: die einzige Schranke gegen den Wert, der
     * im erlaubten Bereich liegt und trotzdem falsch ist. Und hier traegt sie noch etwas:
     * Sie sagt GRATIS aus, solange der Preis 0 ist - wer dort versehentlich eine Zahl
     * eintraegt, hat den zweiten gratis Einstieg des Spiels zugemacht und soll es beim
     * naechsten Start lesen.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann.
     */
    static String rechenprobe(Wolkenwerte werte) {
        StringBuilder satz = new StringBuilder("Wolkeninseln: ");
        if (werte.hoechstzahl() == 0) {
            satz.append("AUS - hoechstzahl ist 0, es wird keine vergeben.");
            return satz.toString();
        }
        satz.append(werte.preis() == 0L
                ? "GRATIS"
                : werte.preis() + " Mark je Insel");
        satz.append(", ").append(inseln(werte.hoechstzahl())).append(" je Spieler, ")
                .append(biomwahl(werte)).append(", Starttruhe mit ")
                .append(werte.starttruhe().size()).append(" Gegenstaenden");
        if (!werte.starttruhe().containsKey(LAVA)) {
            satz.append(" (OHNE Lava)");
        }
        satz.append(".");
        return satz.toString();
    }

    private static String inseln(int zahl) {
        return zahl == 1 ? "eine Insel" : zahl + " Inseln";
    }

    private static String biomwahl(Wolkenwerte werte) {
        if (!werte.biomwahl()) {
            return "keine Biomwahl";
        }
        return werte.biome().size() == 1
                ? "nur das Biom " + werte.biome().get(0)
                : werte.biome().size() + " Biome zur Wahl";
    }

    /**
     * Welche mitgelieferten Zeilen in der Datei FEHLEN.
     *
     * <p>Dieselbe Bauart wie bei {@link Inselliste}: Eine fehlende Zeile ist keine Klage -
     * das Programm hat seinen Rueckfall -, aber sie gehoert in den Bericht. Auf einem
     * Server, der schon lief, schreibt {@code saveDefaultConfig()} die Datei nie um; neue
     * Zeilen fehlen dort einfach.
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
                    + " Wolkeninseln stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }

    /**
     * Die Obergrenze - wie viele Inseln ein Spieler ohne besonderes Recht haelt.
     *
     * <p>Eine 0 ist erlaubt und heisst: Es wird keine vergeben. Das ist der Weg,
     * Wolkenwerder zuzumachen, ohne das Recht anzufassen.
     */
    private static int hoechstzahl(ConfigurationSection wurzel, List<String> klagen,
                                   List<String> bericht) {
        if (!wurzel.contains(Wolkenwerte.PFAD_HOECHSTZAHL, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wolkenwerte.PFAD_HOECHSTZAHL,
                    String.valueOf(Wolkenwerte.VORGABE_HOECHSTZAHL)));
            return Wolkenwerte.VORGABE_HOECHSTZAHL;
        }
        // Als long gelesen und geprueft, obwohl das Feld ein int ist - derselbe Grund
        // wie bei Wirtschaftswerte.brauchbareAnzahl: Wer vorher auf int verkuerzt, prueft
        // nicht mehr den getippten Wert, sondern dessen untere 32 Bit.
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, Wolkenwerte.PFAD_HOECHSTZAHL,
                Wolkenwerte.VORGABE_HOECHSTZAHL, klagen, bericht);
        if (gelesen == null) {
            return Wolkenwerte.VORGABE_HOECHSTZAHL;
        }
        long wert = gelesen;
        if (wert < 0L || wert > Wolkenwerte.HOECHSTE_ZAHL) {
            klagen.add(Wolkenwerte.PFAD_HOECHSTZAHL + " ist " + wert
                    + " und liegt nicht zwischen 0 und " + Wolkenwerte.HOECHSTE_ZAHL
                    + " - es gilt " + Wolkenwerte.VORGABE_HOECHSTZAHL + ".");
            return Wolkenwerte.VORGABE_HOECHSTZAHL;
        }
        if (wert != Wolkenwerte.VORGABE_HOECHSTZAHL) {
            bericht.add(Wolkenwerte.PFAD_HOECHSTZAHL + " = " + wert + " gilt.");
        }
        return (int) wert;
    }

    /**
     * Welche Biome angeboten werden.
     *
     * <p>Dieselbe Bauart wie {@code Verdienstliste.namensliste}: jede Zeile einzeln
     * geprueft, eine kaputte uebersprungen, die uebrigen gelten. Geprueft wird gegen die
     * Tafel im Kern ({@link Wolkenbiome}) - ein Name, den Minecraft nicht kennt, waere
     * ein Biom, das der Befehl anbietet und das Plugin nicht setzen kann.
     *
     * <p>Eine LEERE Liste ist erlaubt und heisst etwas anderes als eine fehlende: Wer alle
     * Zeilen herausnimmt, will keine Biomwahl, und soll sie nicht aus der Vorgabe
     * zurueckbekommen.
     *
     * <p>Doppelte Namen werden hier und nicht im Satz abgefangen: {@link Wolkenwerte}
     * WIRFT dort, und ein Wurf mitten im Lesen riss schon einmal einen Start mit.
     */
    private static List<String> biome(ConfigurationSection wurzel, List<String> klagen,
                                      List<String> bericht) {
        if (!wurzel.contains(Wolkenwerte.PFAD_BIOME, true)) {
            bericht.add(Wolkenwerte.PFAD_BIOME + " steht nicht in deiner config.yml - es"
                    + " gelten die " + Wolkenwerte.VORGABE_BIOME.size()
                    + " Biome aus dem Programm.");
            return Wolkenwerte.VORGABE_BIOME;
        }
        List<?> gelesen = wurzel.getList(Wolkenwerte.PFAD_BIOME);
        if (gelesen == null) {
            klagen.add(Wolkenwerte.PFAD_BIOME + " ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(Wolkenwerte.PFAD_BIOME))
                    + ") - verworfen. Erwartet wird eine Liste in eckigen Klammern oder"
                    + " mit Strichen. "
                    + Wirtschaftsliste.esBleibt(Wolkenwerte.VORGABE_BIOME.size())
                    + " Biome aus dem Programm.");
            return Wolkenwerte.VORGABE_BIOME;
        }
        var sauber = new LinkedHashSet<String>();
        for (Object eintrag : gelesen) {
            if (!(eintrag instanceof String name) || name.isBlank()) {
                klagen.add(Wolkenwerte.PFAD_BIOME + ": "
                        + Wirtschaftsliste.wieGetippt(eintrag)
                        + " ist kein Biomname - uebersprungen.");
                continue;
            }
            if (!Wolkenbiome.gibtEs(name)) {
                klagen.add(Wolkenwerte.PFAD_BIOME + ": Das Biom \"" + name.trim()
                        + "\" gibt es nicht - uebersprungen. Angeboten werden kann nur,"
                        + " was Minecraft kennt und was dieser Weltgenerator abgibt: "
                        + String.join(", ", Wolkenbiome.NAMEN) + ".");
                continue;
            }
            if (!sauber.add(name.trim().toLowerCase(Locale.ROOT))) {
                bericht.add(Wolkenwerte.PFAD_BIOME + ": \"" + name.trim()
                        + "\" steht zweimal - einmal gezaehlt.");
            }
        }
        bericht.add(Wolkenwerte.PFAD_BIOME + ": " + sauber.size() + " Biome.");
        return List.copyOf(sauber);
    }

    /**
     * Was in der Starttruhe liegt - Zeile fuer Zeile geprueft.
     *
     * <p>Dieselbe Bauart wie {@code Angelliste.faenge}, und der Name wird an derselben
     * Stelle geprueft wie jeder andere Gegenstandsname im Projekt
     * ({@code Verdienstliste.warumKeinMaterial}). Zwei Meinungen darueber, was ein
     * Gegenstand ist, waeren der Fehler, bei dem eine Zeile in der einen Tabelle gilt und
     * in der anderen nicht.
     *
     * <p>Fehlt der Abschnitt, gilt die Truhe des Programms. Ist er LEER, ist die Truhe
     * leer - und das ist etwas anderes: Wer alle Zeilen herausnimmt, will einen harten
     * Anfang und soll ihn bekommen, ohne einen Schalter zu suchen.
     *
     * <p><b>Eine kaputte Zeile kostet nur sich selbst</b>, und hier haengt daran mehr als
     * sonst: Ohne lava_bucket gibt es keinen Kobbelsteingenerator und damit kein Spiel.
     * Darum nennt der Bericht am Ende, was wirklich in der Truhe liegt.
     */
    private static Map<String, Integer> starttruhe(ConfigurationSection wurzel,
                                                   List<String> klagen,
                                                   List<String> bericht) {
        if (!wurzel.contains(Wolkenwerte.PFAD_STARTTRUHE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Wolkenwerte.PFAD_STARTTRUHE,
                    Wolkenwerte.VORGABE_STARTTRUHE.size() + " Gegenstaende des"
                            + " Programms"));
            return Wolkenwerte.VORGABE_STARTTRUHE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Wolkenwerte.PFAD_STARTTRUHE);
        if (abschnitt == null) {
            klagen.add(Wolkenwerte.PFAD_STARTTRUHE + " ist keine Liste von Gegenstaenden"
                    + " mit Anzahl - verworfen. Es gilt die Truhe des Programms.");
            return Wolkenwerte.VORGABE_STARTTRUHE;
        }
        Map<String, Integer> truhe = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                klagen.add(Wolkenwerte.PFAD_STARTTRUHE + "." + name + " " + warum
                        + " - diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > Wolkenwerte.HOECHSTE_ANZAHL) {
                klagen.add(Wolkenwerte.PFAD_STARTTRUHE + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine"
                        + " Anzahl von 1 bis " + Wolkenwerte.HOECHSTE_ANZAHL
                        + ". Diese Zeile faellt weg, die uebrigen gelten.");
                continue;
            }
            truhe.put(klein, (int) zahl.longValue());
        }
        if (!truhe.containsKey(LAVA)) {
            // KEINE Klage, sondern ein Satz im Bericht: Eine leere oder lavafreie Truhe
            // ist eine erlaubte Einstellung - ein harter Anfang. Sie ist aber auch der
            // haeufigste Vertipper mit der groessten Folge, denn ohne Lava gibt es
            // keinen Kobbelstein, keinen Stein und damit kein Spiel. Wer es so will,
            // liest hier, dass es so ist; wer es nicht wollte, sieht es beim Start und
            // nicht am ersten ratlosen Spieler.
            bericht.add(Wolkenwerte.PFAD_STARTTRUHE + " enthaelt kein " + LAVA
                    + " - ohne Lava gibt es auf einer Wolkeninsel keinen"
                    + " Kobbelsteingenerator und damit keinen Stein. Das ist erlaubt,"
                    + " aber es ist ein sehr harter Anfang.");
        }
        return truhe;
    }

    /**
     * Der Weltname, in dem die Wolkenfelder liegen.
     *
     * <p><b>Wort fuer Wort wie {@code Inselliste.welt}</b>, und das ist der Punkt: Zwei
     * Leser derselben Art von Wert, die verschieden streng sind, sind der Fehler, den
     * niemand sucht. Geprueft wird also hier nur, dass ueberhaupt ein Wort dasteht; was
     * ein Weltname sein darf, entscheidet {@link Weltenliste} fuer alle Welten an einer
     * Stelle.
     */
    private static String welt(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        if (!wurzel.contains(PFAD_WELT, true)) {
            bericht.add(Wirtschaftsliste.fehltText(PFAD_WELT, VORGABE_WELT));
            return VORGABE_WELT;
        }
        Object roh = wurzel.get(PFAD_WELT);
        if (!(roh instanceof String name) || name.isBlank()) {
            klagen.add(PFAD_WELT + " ist kein Weltname ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(VORGABE_WELT));
            return VORGABE_WELT;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
