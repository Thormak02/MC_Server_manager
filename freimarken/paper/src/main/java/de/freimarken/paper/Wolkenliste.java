package de.freimarken.paper;

import de.freimarken.kern.Aufgabenwerte;
import de.freimarken.kern.Erzwerte;
import de.freimarken.kern.Punktwerte;
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
            Wolkenwerte.PFAD_BIOME, Wolkenwerte.PFAD_STARTTRUHE,
            Erzwerte.PFAD_AN, Erzwerte.PFAD_GRUND, Erzwerte.PFAD_JE_STUFE,
            Erzwerte.PFAD_HOECHST, Erzwerte.PFAD_MARKEN, Erzwerte.PFAD_TABELLE,
            Aufgabenwerte.PFAD_AN, Aufgabenwerte.PFAD_LISTE,
            Wolkenwerte.PFAD_BIOMPREIS, Punktwerte.PFAD_TABELLE,
            Punktwerte.PFAD_CHUNKS_JE_TAKT, Punktwerte.PFAD_ABKLINGZEIT);

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

        Erzwerte erze = erze(wurzel, klagen, bericht);
        // Die Herausforderungen liest ihr EIGENER Leser, und seine Klagen kommen hier
        // mit heraus: Der Startbericht hat eine Stelle, an der die Wolkeninseln stehen,
        // und zwei Quellen dafuer waeren zwei Stellen, von denen eine uebersehen wird.
        Aufgabenliste.Ergebnis aufgaben = Aufgabenliste.ausKonfiguration(wurzel);
        klagen.addAll(aufgaben.klagen());
        bericht.addAll(aufgaben.bericht());
        Punkteliste.Ergebnis punkte = Punkteliste.ausKonfiguration(wurzel);
        klagen.addAll(punkte.klagen());
        bericht.addAll(punkte.bericht());
        // Der Biompreis geht durch betrag() und damit durch den SPRUNGSCHUTZ: Eine
        // Verdoppelung ohne Bestaetigung wird beklagt. Der Schluessel heisst wolken-biom
        // und steht in der ausgelieferten config.yml unter sprung-bestaetigt (Tafel
        // 31am).
        long biompreis = Wirtschaftsliste.betrag(wurzel, Wolkenwerte.PFAD_BIOMPREIS,
                "wolken-biom", Wolkenwerte.VORGABE_BIOMPREIS,
                Wolkenwerte.HOECHSTER_BETRAG, "Mark je Wechsel", klagen, bericht);

        Wolkenwerte werte = new Wolkenwerte(preis, hoechstzahl, biome, truhe, erze,
                aufgaben.werte(), punkte.werte(), biompreis);
        bericht.add(rechenprobe(werte));
        bericht.add(erzprobe(erze));
        bericht.add(Aufgabenliste.rechenprobe(aufgaben.werte()));
        bericht.add(Punkteliste.rechenprobe(punkte.werte()));
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
                .append(biomwahl(werte));
        if (werte.biomwahl()) {
            // Der Preis gehoert NEBEN die Zahl der Biome: Eine Wahl, die 2.000 Mark je
            // Wechsel kostet, ist eine andere Wahl als eine kostenlose - und genau diese
            // Zeile liest der Betreiber beim Start.
            satz.append(werte.biompreis() == 0L
                    ? " (Wechsel gratis)"
                    : " (Wechsel " + werte.biompreis() + " Mark)");
        }
        satz.append(", Starttruhe mit ")
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
     * Der Abschnitt {@code wolken.erze:} - der Kobbelsteingenerator.
     *
     * <p><b>Alles oder nichts bei den drei Prozentzahlen</b>, dieselbe Antwort wie bei den
     * Verfallsfristen: Sie haengen voneinander ab ({@link Erzwerte} verlangt, dass der
     * Deckel nicht unter dem Grundwert liegt), und ein einzeln zurechtgebogener Wert waere
     * eine Mischung aus Datei und Vorgabe, die der Betreiber nirgends nachlesen kann. Also
     * wird jede Zeile einzeln gelesen und beklagt, und laesst sich der Satz am Ende nicht
     * bauen, gilt {@link Erzwerte#VORGABE} - mit einer Klage, die den Grund woertlich
     * nennt.
     *
     * <p>Der SCHALTER bleibt dabei stehen: Wer den Generator abschaltet und sich in einer
     * Prozentzahl vertippt, bekommt keine eingeschaltete Tabelle zurueck.
     */
    private static Erzwerte erze(ConfigurationSection wurzel, List<String> klagen,
                                 List<String> bericht) {
        boolean an = jaNein(wurzel, Erzwerte.PFAD_AN, Erzwerte.VORGABE.an(), klagen,
                bericht);
        boolean marken = jaNein(wurzel, Erzwerte.PFAD_MARKEN, Erzwerte.VORGABE.marken(),
                klagen, bericht);
        int grund = prozent(wurzel, Erzwerte.PFAD_GRUND, Erzwerte.VORGABE_GRUND, klagen,
                bericht);
        int jeStufe = prozent(wurzel, Erzwerte.PFAD_JE_STUFE, Erzwerte.VORGABE_JE_STUFE,
                klagen, bericht);
        int hoechst = prozent(wurzel, Erzwerte.PFAD_HOECHST, Erzwerte.VORGABE_HOECHST,
                klagen, bericht);
        Map<String, Erzwerte.Erzzeile> tabelle = erztabelle(wurzel, klagen, bericht);
        try {
            return new Erzwerte(an, grund, jeStufe, hoechst, marken, tabelle);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add("Die Erzwerte passen nicht zusammen: "
                    + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt(Erzwerte.VORGABE_GRUND + " Prozent plus "
                            + Erzwerte.VORGABE_JE_STUFE + " je Stufe, hoechstens "
                            + Erzwerte.VORGABE_HOECHST + " Prozent"));
            return Erzwerte.VORGABE.mitAn(an).mitMarken(marken);
        }
    }

    /**
     * Ein Ja oder Nein mit Bericht - der Schalter und die Marken.
     *
     * <p>Der Bericht steht HIER und nicht in {@link Wirtschaftsliste#jaNein}: Ein
     * abgeschalteter Generator ist die Einstellung, die den ganzen Reiz dieser Welt
     * ausmacht, und wer sie setzt, soll sie beim Start lesen. Eine stumme Zeile waere die
     * unauffaelligste Art, Wolkenwerder langweilig zu machen.
     */
    private static boolean jaNein(ConfigurationSection wurzel, String pfad,
                                  boolean vorgabe, List<String> klagen,
                                  List<String> bericht) {
        Boolean gelesen = Wirtschaftsliste.jaNein(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        if (gelesen != vorgabe) {
            bericht.add(pfad + " = " + gelesen + " gilt.");
        }
        return gelesen;
    }

    /**
     * Eine Prozentzahl von 0 bis 100 - EINE Methode fuer drei Zeilen.
     *
     * <p>Mit dem Pfad als Argument, aus demselben Grund, den {@code Freistadtliste.frist}
     * nennt: Drei gleich gebaute Leser waeren drei Stellen, an denen der zweite die
     * naechste Verschaerfung nicht mitbekommt.
     */
    private static int prozent(ConfigurationSection wurzel, String pfad, int vorgabe,
                               List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < 0L || wert > 100L) {
            klagen.add(pfad + " ist " + wert + " und liegt nicht zwischen 0 und 100"
                    + " Prozent - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        if (wert != vorgabe) {
            bericht.add(pfad + " = " + wert + " Prozent gilt.");
        }
        return (int) wert;
    }

    /**
     * Die Erztabelle - Zeile fuer Zeile geprueft, kaputte beklagt und uebersprungen.
     *
     * <p>Dieselbe Bauart wie {@code Angelliste.faenge}, mit einem Unterschied: Jede Zeile
     * traegt ZWEI Zahlen, und darum ist sie selbst ein Abschnitt. Die Namen der beiden
     * Unterschluessel stehen in {@link Erzwerte#SCHLUESSEL_GEWICHT} und
     * {@link Erzwerte#SCHLUESSEL_AB_STUFE} - in der Datei geschriebene Namen und zur
     * Laufzeit gelesene Schluessel sind eine Naht, und eine Probe, die die fertige Tabelle
     * hereingibt, sieht sie nie.
     *
     * <p><b>Die Mindeststufe darf fehlen und heisst dann 0</b> - "von Anfang an". Das
     * Gewicht darf es nicht: Eine Zeile ohne Gewicht ist keine Angabe, sondern ein halb
     * getippter Eintrag, und eine stille 1 daraus waere ein Erz, das haeufiger kommt als
     * alles, was danebensteht.
     *
     * <p>Fehlt der Abschnitt, gilt die Tabelle des Programms. Ist er LEER, gilt eine leere
     * Tabelle - und das ist etwas anderes: Wer alle Zeilen herausnimmt, will reinen
     * Kobbelstein und soll ihn bekommen, ohne den Schalter zu finden.
     */
    private static Map<String, Erzwerte.Erzzeile> erztabelle(ConfigurationSection wurzel,
                                                             List<String> klagen,
                                                             List<String> bericht) {
        if (!wurzel.contains(Erzwerte.PFAD_TABELLE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Erzwerte.PFAD_TABELLE,
                    Erzwerte.VORGABE_TABELLE.size() + " Erze des Programms"));
            return Erzwerte.VORGABE_TABELLE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Erzwerte.PFAD_TABELLE);
        if (abschnitt == null) {
            klagen.add(Erzwerte.PFAD_TABELLE + " ist keine Liste von Erzen mit Gewicht"
                    + " und Mindeststufe - verworfen. Es gilt die Tabelle des Programms.");
            return Erzwerte.VORGABE_TABELLE;
        }
        Map<String, Erzwerte.Erzzeile> tabelle = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            Erzwerte.Erzzeile zeile = erzzeile(abschnitt, name, klagen);
            if (zeile != null) {
                tabelle.put(name.trim().toLowerCase(Locale.ROOT), zeile);
            }
        }
        if (tabelle.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            // JEDE Zeile war kaputt. Die Tabelle des Programms waere hier falsch: Der
            // Betreiber hat eine eigene gewollt, und mit der Vorgabe kaemen Erze, die er
            // gerade herausgenommen hat.
            bericht.add(Erzwerte.PFAD_TABELLE + ": keine einzige Zeile war brauchbar -"
                    + " der Generator gibt bis zur naechsten Berichtigung nur"
                    + " Kobbelstein.");
        }
        return tabelle;
    }

    /** Eine Zeile der Erztabelle - oder {@code null}, wenn sie wegfaellt. */
    private static Erzwerte.Erzzeile erzzeile(ConfigurationSection abschnitt, String name,
                                              List<String> klagen) {
        String pfad = Erzwerte.PFAD_TABELLE + "." + name;
        String klein = name.trim().toLowerCase(Locale.ROOT);
        String warum = Verdienstliste.warumKeinMaterial(klein);
        if (warum != null) {
            klagen.add(pfad + " " + warum
                    + " - diese Zeile faellt weg, die uebrigen gelten.");
            return null;
        }
        if (Erzwerte.istAusgang(klein)) {
            // Kobbelstein IN der Tabelle waere ein Tausch von Kobbelstein gegen
            // Kobbelstein: Der Prozentwurf greift, und heraus kommt dasselbe. Der
            // Betreiber saehe eine Zeile mit Gewicht 50 und wunderte sich, warum sie
            // nichts tut.
            klagen.add(pfad + " ist der Block, AUS dem getauscht wird - eine Zeile"
                    + " darueber tut nichts. Sie faellt weg, die uebrigen gelten.");
            return null;
        }
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
        if (eintrag == null) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + " - erwartet werden die zwei Zeilen "
                    + Erzwerte.SCHLUESSEL_GEWICHT + " und "
                    + Erzwerte.SCHLUESSEL_AB_STUFE + " darunter. Diese Zeile faellt weg,"
                    + " die uebrigen gelten.");
            return null;
        }
        Object rohGewicht = eintrag.get(Erzwerte.SCHLUESSEL_GEWICHT);
        if (!(rohGewicht instanceof Number gewicht) || gewicht.longValue() < 1L
                || gewicht.longValue() > Erzwerte.HOECHSTES_GEWICHT) {
            klagen.add(pfad + "." + Erzwerte.SCHLUESSEL_GEWICHT + " ist "
                    + Wirtschaftsliste.wieGetippt(rohGewicht) + " - erwartet wird ein"
                    + " Gewicht von 1 bis " + Erzwerte.HOECHSTES_GEWICHT
                    + ". Diese Zeile faellt weg, die uebrigen gelten.");
            return null;
        }
        Object rohStufe = eintrag.get(Erzwerte.SCHLUESSEL_AB_STUFE);
        long abStufe = 0L;
        if (rohStufe != null) {
            if (!(rohStufe instanceof Number stufe) || stufe.longValue() < 0L
                    || stufe.longValue() > Erzwerte.HOECHSTE_STUFE) {
                klagen.add(pfad + "." + Erzwerte.SCHLUESSEL_AB_STUFE + " ist "
                        + Wirtschaftsliste.wieGetippt(rohStufe) + " - erwartet wird eine"
                        + " Stufe von 0 bis " + Erzwerte.HOECHSTE_STUFE
                        + ". Diese Zeile faellt weg, die uebrigen gelten.");
                return null;
            }
            abStufe = stufe.longValue();
        }
        return new Erzwerte.Erzzeile((int) gewicht.longValue(), (int) abStufe);
    }

    /**
     * Die Zahlen des Generators im Klartext - die zweite Rechenprobe.
     *
     * <p>Eine eigene Zeile und nicht an {@link #rechenprobe} angehaengt: Der Generator ist
     * die Sache, um die es in dieser Welt geht, und seine Zahlen sind die, bei denen ein
     * Vertipper am teuersten ist. Wer {@code je-stufe-prozent: 20} tippt, soll beim Start
     * lesen, dass der Deckel schon bei Stufe 2 erreicht ist - und nicht erst merken, dass
     * seine Herausforderungen ab der dritten nichts mehr bringen.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann.
     */
    static String erzprobe(Erzwerte werte) {
        if (!werte.greift()) {
            return "Kobbelsteingenerator: nur Kobbelstein - "
                    + (werte.an() ? "die Erztabelle ist leer" : "wolken.erze.an ist aus")
                    + ".";
        }
        StringBuilder satz = new StringBuilder("Kobbelsteingenerator: ");
        satz.append(werte.grundProzent()).append(" Prozent Erz bei Stufe 0");
        if (werte.jeStufeProzent() == 0) {
            satz.append(", keine Steigerung je Stufe");
        } else {
            satz.append(", je Stufe ").append(werte.jeStufeProzent())
                    .append(" mehr bis ").append(werte.hoechstProzent())
                    .append(" Prozent ab Stufe ").append(deckelstufe(werte));
        }
        satz.append("; ").append(werte.tabelle().size()).append(" Erze, davon ")
                .append(werte.tabelle().values().stream()
                        .filter(zeile -> zeile.abStufe() == 0).count())
                .append(" von Anfang an");
        if (!werte.marken()) {
            // Die Zeile, die Geld kostet: Ohne Marke zahlt der Abbau jedes getauschten
            // Blocks den Lohn aus verdienst.loehne - und ein Automat laeuft Tag und Nacht.
            satz.append("; OHNE Blockmarke, der Abbau zahlt Lohn");
        }
        return satz.append(".").toString();
    }

    /**
     * Die erste Stufe, bei der der Deckel greift.
     *
     * <p>Aufgerundet, und das ist der Sinn der Zeile: Bei 15 Prozent plus 2 je Stufe und
     * einem Deckel von 40 sind es 12,5 Stufen - gemeint ist die 13, denn bei 12 steht die
     * Ausbeute noch bei 39.
     */
    private static int deckelstufe(Erzwerte werte) {
        int fehlt = werte.hoechstProzent() - werte.grundProzent();
        return (fehlt + werte.jeStufeProzent() - 1) / werte.jeStufeProzent();
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
