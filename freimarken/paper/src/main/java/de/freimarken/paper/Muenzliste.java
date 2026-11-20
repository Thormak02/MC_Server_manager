package de.freimarken.paper;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Muenzwerte;
import de.freimarken.kern.Wechselwerte;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Katalog des Muenzhauses aus der {@code config.yml} - Posten fuer Posten.
 *
 * <p>Nach demselben Muster wie {@link Wechselliste} und {@link Rangliste}: reine
 * Dateiarbeit, gesammelte KLAGEN statt Ausnahmen, und der Rueckfall steht im Programm. Ein
 * Tippfehler in einem Preis darf keinen Serverstart mitreissen; er darf aber auch nicht
 * still gelten.
 *
 * <p><b>Ein kaputter Posten faellt GANZ weg</b>, nicht nur seine Zeile - wie eine
 * Rangstufe und eine Herausforderung, und aus demselben Grund: Ein Posten, dem ein
 * Gegenstand fehlt, ist ein ANDERER Posten, und zwar ein guenstigerer. Die uebrigen
 * gelten weiter.
 *
 * <p><b>KEIN Sprungschutz, und das ist eine Entscheidung</b>: {@code Wirtschaftsliste.betrag}
 * braucht je Wert einen festen Schluessel unter {@code wirtschaft.sprung-bestaetigt}, und
 * den hat ein frei getippter Posten nicht - Tafel 31am prueft beide Richtungen auf
 * Gleichheit, ein Aufruf ohne Zeile dort waere rot. Geprueft wird darum nur der Bereich,
 * wie bei {@link Rangliste} und {@link Punkteliste}. Die Schranke gegen den Preis, der im
 * Bereich liegt und trotzdem falsch ist, ist die RECHENPROBE im Bericht: Sie nennt jeden
 * Preis auch in Mark, und "ein Hut kostet 5.000.000 Mark" sieht jeder.
 *
 * <p><b>Jeder Gegenstandsname geht durch {@link Verdienstliste#warumKeinMaterial}</b>, die
 * eine Stelle, die Minecraft-Namen prueft. Ohne sie stuende ein vertippter Posten mit
 * vollem Preis in der Tafel und meldete beim Kauf "kein Platz" bei leerem Rucksack - denn
 * was dieser Server nicht kennt, kann nicht ankommen. Tafel 31bb sieht das nicht: Sie
 * laeuft ueber die Vorgaben im Programm und nie ueber die Datei des Betreibers.
 */
final class Muenzliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Muenzwerte.PFAD_AN,
            Muenzwerte.PFAD_KATALOG);

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Muenzwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Muenzliste() {
    }

    /**
     * Der ganze Abschnitt {@code muenzhaus:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     * @param kurs   die schon gelesenen Kurse der Wechselstube - gebraucht nur fuer die
     *               Rechenprobe, die jeden Preis auch in Mark nennt. Hereingegeben und
     *               nicht hier geholt, damit dieser Leser EINEN Abschnitt liest; dass er
     *               nach {@link Wechselliste} laufen muss, steht bei seinem Aufruf
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel, Wechselwerte kurs) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Muenzwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        Boolean gelesen = Wirtschaftsliste.jaNein(wurzel, Muenzwerte.PFAD_AN,
                Muenzwerte.VORGABE.an(), klagen, bericht);
        boolean an = gelesen == null ? Muenzwerte.VORGABE.an() : gelesen;
        List<Muenzwerte.Posten> katalog = katalog(wurzel, klagen, bericht);
        try {
            Muenzwerte werte = new Muenzwerte(an, katalog);
            bericht.add(rechenprobe(werte, kurs));
            return new Ergebnis(werte, klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Der Leser wirft NIE, der record sehr wohl - darum dieses try/catch. Hierher
            // kommt nur, was die Zeile fuer Zeile gelesenen Posten ZUSAMMEN verletzen
            // koennen: zu viele, oder zweimal derselbe Schluessel.
            klagen.add(Muenzwerte.PFAD_KATALOG + ": " + unbrauchbar.getMessage()
                    + " Der ganze Katalog ist verworfen; es gilt der ausgelieferte.");
            bericht.add(rechenprobe(Muenzwerte.VORGABE, kurs));
            return new Ergebnis(Muenzwerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Zeile, die den Katalog im Zusammenhang nennt.
     *
     * <p><b>Sie ist die einzige Schranke gegen den Preis, der im erlaubten Bereich liegt
     * und trotzdem falsch ist</b> - und darum nennt sie jeden Preis in BEIDEN Waehrungen:
     * Goldmark ist die grobe Einheit (Plan 8.1, "Preise werden in Zehnern genannt"), und
     * erst in Mark sieht man, ob ein Hut eine Spielstunde oder einen Monat kostet.
     *
     * <p>Gerechnet wird mit {@code kurs.kaufpreis}, also mit dem HINWEG - das ist, was den
     * Spieler die Goldmark gekostet hat. Der Rueckwegkurs liegt zwanzig Prozent darunter
     * und waere die falsche Zahl. Ein Preis von 0 wird gar nicht gerechnet:
     * {@code kaufpreis(0)} wirft (ein Tausch ueber null Goldmark ist keiner), und ein
     * Wurf hier riss den Startlauf mit.
     *
     * <p>Eigene Methode, damit die Probe sie ohne Datei bekommt - dasselbe Muster wie
     * {@code Wechselliste.rechenprobe}.
     */
    static String rechenprobe(Muenzwerte werte, Wechselwerte kurs) {
        if (!werte.greift()) {
            return "Muenzhaus: nichts zu kaufen - "
                    + (werte.an() ? "der Katalog ist leer" : Muenzwerte.PFAD_AN
                            + " ist aus")
                    + ". Kontostand und Verlauf der Goldmark bleiben zu sehen.";
        }
        var guenstigster = werte.guenstigster().orElseThrow();
        var teuerster = werte.teuerster().orElseThrow();
        return "Muenzhaus: " + werte.katalog().size() + " Posten von "
                + preis(guenstigster, kurs) + " bis " + preis(teuerster, kurs)
                + "; der teuerste ist " + teuerster.schluessel() + ". Gewechselt wird zu "
                + kurs.markJeGoldmark() + " Mark je Goldmark.";
    }

    /** Ein Preis in beiden Waehrungen - "2 Goldmark (2000 Mark)". */
    private static String preis(Muenzwerte.Posten posten, Wechselwerte kurs) {
        if (posten.preis() == 0L) {
            return "gratis";
        }
        return posten.preis() + " Goldmark (" + kurs.kaufpreis(posten.preis())
                + " Mark)";
    }

    /**
     * Der Abschnitt {@code muenzhaus.katalog} - vier Faelle, vier Bedeutungen.
     *
     * <p>Wie bei den Herausforderungen: Abschnitt fehlt heisst Vorgabe plus Bericht;
     * Abschnitt ist keine Tabelle heisst Klage plus Vorgabe; ein LEERER Abschnitt
     * ({@code katalog: {}}) GILT - wer alles herausnimmt, will nichts verkaufen; und
     * faellt jeder Eintrag weg, bleibt eine leere Liste mit eigener Berichtzeile.
     *
     * <p>Die Reihenfolge kommt aus {@code getKeys(false)}, also aus der Datei: So stehen
     * die Posten auch auf der Tafel, und das ist eine Aussage des Betreibers.
     */
    private static List<Muenzwerte.Posten> katalog(ConfigurationSection wurzel,
                                                   List<String> klagen,
                                                   List<String> bericht) {
        if (!wurzel.contains(Muenzwerte.PFAD_KATALOG, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Muenzwerte.PFAD_KATALOG,
                    Muenzwerte.VORGABE_KATALOG.size() + " Posten"));
            return Muenzwerte.VORGABE_KATALOG;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Muenzwerte.PFAD_KATALOG);
        if (abschnitt == null) {
            klagen.add(Muenzwerte.PFAD_KATALOG + " ist keine Liste von Posten ("
                    + Wirtschaftsliste.wieGetippt(wurzel.get(Muenzwerte.PFAD_KATALOG))
                    + ") - verworfen. Erwartet wird je Posten ein Abschnitt mit "
                    + Muenzwerte.SCHLUESSEL_GEGENSTAENDE + ": und "
                    + Muenzwerte.SCHLUESSEL_PREIS + ":. "
                    + Wirtschaftsliste.esBleibt(Muenzwerte.VORGABE_KATALOG.size()
                            + " Posten"));
            return Muenzwerte.VORGABE_KATALOG;
        }
        List<Muenzwerte.Posten> posten = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Muenzwerte.Posten gelesen = posten(abschnitt, name, klagen);
            if (gelesen != null) {
                posten.add(gelesen);
            }
        }
        if (posten.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            bericht.add(Muenzwerte.PFAD_KATALOG + ": kein einziger Posten war brauchbar -"
                    + " das Muenzhaus verkauft nichts. Kontostand und Verlauf der"
                    + " Goldmark bleiben zu sehen.");
        }
        return posten;
    }

    /**
     * EIN Posten - oder {@code null}, wenn er wegfaellt.
     *
     * <p>Jede Klage nennt den vollen Pfad UND die Folge. "Dieser Posten faellt weg, die
     * uebrigen gelten" ist dabei keine Hoeflichkeit, sondern die Auskunft, die der
     * Betreiber braucht: Er muss wissen, ob sein Katalog noch steht.
     */
    private static Muenzwerte.Posten posten(ConfigurationSection abschnitt, String name,
                                            List<String> klagen) {
        String pfad = Muenzwerte.PFAD_KATALOG + "." + name;
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
        if (eintrag == null) {
            klagen.add(pfad + " ist kein Posten ("
                    + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + ") - erwartet wird ein Abschnitt mit "
                    + Muenzwerte.SCHLUESSEL_GEGENSTAENDE + ": und "
                    + Muenzwerte.SCHLUESSEL_PREIS + ":. Dieser Posten faellt weg, die"
                    + " uebrigen gelten.");
            return null;
        }
        if (!Muenzwerte.brauchbarerSchluessel(name.trim().toLowerCase(Locale.ROOT))) {
            klagen.add(pfad + ": der Name taugt nicht. Erlaubt sind Kleinbuchstaben,"
                    + " Ziffern, Unterstrich und Bindestrich, hoechstens "
                    + Muenzwerte.HOECHSTE_SCHLUESSELLAENGE + " Zeichen, und beginnen muss"
                    + " er mit einem Buchstaben. Er steht in der sprache.yml und im"
                    + " getippten Befehl. Dieser Posten faellt weg, die uebrigen gelten.");
            return null;
        }
        Map<String, Integer> gegenstaende = gegenstaende(eintrag, pfad, klagen);
        if (gegenstaende == null) {
            return null;
        }
        Long preis = preis(eintrag, pfad, klagen);
        if (preis == null) {
            return null;
        }
        Map<Bedingungsart, Long> bedingungen = bedingungen(eintrag, pfad, klagen);
        if (bedingungen == null) {
            return null;
        }
        try {
            return new Muenzwerte.Posten(name, gegenstaende, preis, bedingungen);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add(pfad + ": " + unbrauchbar.getMessage()
                    + " Dieser Posten faellt weg, die uebrigen gelten.");
            return null;
        }
    }

    /**
     * Die Gegenstaende eines Postens - {@code null}, wenn der Posten wegfaellt.
     *
     * <p>Ein unbekannter Gegenstand nimmt den GANZEN Posten mit und nicht nur seine
     * Zeile: Ein Posten, dem ein Stueck fehlt, ist ein anderer Posten - und was dieser
     * Server nicht kennt, kann nicht ankommen. Der Spieler laese "kein Platz" bei leerem
     * Rucksack und suchte den Fehler bei sich.
     */
    private static Map<String, Integer> gegenstaende(ConfigurationSection eintrag,
                                                     String pfad, List<String> klagen) {
        String unten = pfad + "." + Muenzwerte.SCHLUESSEL_GEGENSTAENDE;
        ConfigurationSection liste =
                eintrag.getConfigurationSection(Muenzwerte.SCHLUESSEL_GEGENSTAENDE);
        if (liste == null) {
            klagen.add(unten + " fehlt oder ist keine Liste ("
                    + Wirtschaftsliste.wieGetippt(
                            eintrag.get(Muenzwerte.SCHLUESSEL_GEGENSTAENDE))
                    + ") - erwartet wird je Zeile ein Gegenstand und eine Anzahl. Ein"
                    + " Posten ohne Gegenstaende waere Goldmark fuer nichts. Dieser"
                    + " Posten faellt weg, die uebrigen gelten.");
            return null;
        }
        // LinkedHashMap: Die Reihenfolge der Datei steht in EINER Zeile der Tafel, und
        // der Betreiber soll sie dort wiederfinden.
        Map<String, Integer> gegenstaende = new LinkedHashMap<>();
        for (String name : liste.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            String warum = Verdienstliste.warumKeinMaterial(klein);
            if (warum != null) {
                // " - " als Trenner, wie bei den Herausforderungen: warumKeinMaterial
                // endet ohne Satzzeichen, und ohne den Strich lief im Startprotokoll
                // "ohne minecraft: davor Dieser Posten" in einem Zug durch.
                klagen.add(unten + "." + name + " " + warum + " - dieser Posten faellt"
                        + " weg, die uebrigen gelten.");
                return null;
            }
            Object roh = liste.get(name);
            if (!(roh instanceof Number zahl)
                    || !Muenzwerte.brauchbareStueckzahl(zahl.intValue())
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())
                    || zahl.longValue() != zahl.intValue()) {
                klagen.add(unten + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine ganze"
                        + " Zahl von 1 bis " + Muenzwerte.HOECHSTE_STUECKZAHL
                        + " Stueck; mehr passt in keinen Rucksack. Dieser Posten faellt"
                        + " weg, die uebrigen gelten.");
                return null;
            }
            gegenstaende.put(klein, zahl.intValue());
        }
        if (gegenstaende.isEmpty()) {
            klagen.add(unten + " ist leer - ein Posten ohne Gegenstaende waere Goldmark"
                    + " fuer nichts. Dieser Posten faellt weg, die uebrigen gelten.");
            return null;
        }
        return gegenstaende;
    }

    /** Der Preis eines Postens in Goldmark - {@code null}, wenn der Posten wegfaellt. */
    private static Long preis(ConfigurationSection eintrag, String pfad,
                              List<String> klagen) {
        String unten = pfad + "." + Muenzwerte.SCHLUESSEL_PREIS;
        Object roh = eintrag.get(Muenzwerte.SCHLUESSEL_PREIS);
        if (!(roh instanceof Number zahl) || !Muenzwerte.brauchbarerPreis(zahl.longValue())
                || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
            klagen.add(unten + " ist " + Wirtschaftsliste.wieGetippt(roh)
                    + " - erwartet wird eine ganze Zahl von 0 bis "
                    + Muenzwerte.HOECHSTER_PREIS + " GOLDMARK, und eine 0 heisst gratis."
                    + " Achtung, nicht in Mark: Die Goldmark ist die grobe Einheit, der"
                    + " Plan nennt fuer Kosmetik 2 bis 5. Dieser Posten faellt weg, die"
                    + " uebrigen gelten.");
            return null;
        }
        return zahl.longValue();
    }

    /**
     * Die Bedingungen eines Postens - leer heisst: fuer jeden.
     *
     * <p>Abgeschrieben von {@code Rangliste.bedingungen}, der einzigen anderen Stelle, die
     * eine Bedingungstabelle unter einem Listeneintrag liest - und mit derselben Strenge:
     * Ein unbekannter Name nimmt den GANZEN Posten mit. Eine Bedingung, die stillschweigend
     * wegfaellt, machte den Posten LEICHTER als eingestellt.
     *
     * <p>Die untere Grenze ist 1 und nicht 0: Eine 0 ist keine Bedingung, sondern eine
     * Zeile, die nie wirkt. Wer keine will, laesst die Zeile weg.
     */
    private static Map<Bedingungsart, Long> bedingungen(ConfigurationSection eintrag,
                                                        String pfad,
                                                        List<String> klagen) {
        String unten = pfad + "." + Muenzwerte.SCHLUESSEL_BEDINGUNGEN;
        if (!eintrag.contains(Muenzwerte.SCHLUESSEL_BEDINGUNGEN, true)) {
            return new EnumMap<>(Bedingungsart.class);
        }
        ConfigurationSection liste =
                eintrag.getConfigurationSection(Muenzwerte.SCHLUESSEL_BEDINGUNGEN);
        if (liste == null) {
            klagen.add(unten + " ist keine Liste von Bedingungen ("
                    + Wirtschaftsliste.wieGetippt(
                            eintrag.get(Muenzwerte.SCHLUESSEL_BEDINGUNGEN))
                    + ") - erwartet wird je Zeile eine Art und eine Zahl. Es gibt: "
                    + String.join(", ", Bedingungsart.alleSchluessel())
                    + ". Dieser Posten faellt weg, die uebrigen gelten.");
            return null;
        }
        Map<Bedingungsart, Long> bedingungen = new EnumMap<>(Bedingungsart.class);
        for (String name : liste.getKeys(false)) {
            var art = Bedingungsart.ausSchluessel(name);
            if (art.isEmpty()) {
                klagen.add(unten + "." + name + " ist keine Bedingung, die dieses Plugin"
                        + " messen kann. Es gibt: "
                        + String.join(", ", Bedingungsart.alleSchluessel())
                        + ". Dieser Posten faellt weg, die uebrigen gelten.");
                return null;
            }
            Object roh = liste.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > art.get().hoechsterWert()
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
                klagen.add(unten + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine ganze"
                        + " Zahl von 1 bis " + art.get().hoechsterWert() + " ("
                        + art.get().beschreibung() + "). Eine 0 ist keine Bedingung,"
                        + " sondern eine Zeile, die nie wirkt - wer keine will, laesst"
                        + " sie weg. Dieser Posten faellt weg, die uebrigen gelten.");
                return null;
            }
            bedingungen.put(art.get(), zahl.longValue());
        }
        return bedingungen;
    }

    /**
     * Nennt jede Muenzzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste},
     * {@link Marktliste} oder {@link Wechselliste}: Fuenf Berichte, die dieselbe Zahl
     * verschieden nennen, sind schlimmer als fuenf Zeilen.
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
                    + " Muenzzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie erst,"
                    + " wenn sie in der Datei stehen; was das Plugin ohne die Zeile"
                    + " mitbringt, nennt die Rechenprobe darunter.");
        }
    }
}
