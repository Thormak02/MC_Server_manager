package de.freimarken.paper;

import de.freimarken.kern.Losregel;
import de.freimarken.kern.Lotteriewerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code lotterie:} aus der {@code config.yml} (Plan 14).
 *
 * <p>Dieselbe Bauart wie {@link Angelliste}: reine Dateiarbeit, gesammelte Klagen statt
 * Wuerfe, und eine kaputte ZEILE faellt weg, waehrend der Rest der Tabelle gilt. Ein
 * Tippfehler in einem Gewinnnamen darf nicht die ganze Lotterie kosten.
 *
 * <p><b>Der Gegenstandsname wird mit {@link Verdienstliste#warumKeinMaterial} geprueft</b>,
 * also mit DERSELBEN Pruefung wie Lohntafel und Angeltabelle. Zwei Meinungen darueber, was
 * ein Gegenstandsname ist, waeren der Fehler, bei dem der Betreiber eine Zeile in der
 * einen Tabelle eintragen kann und in der anderen nicht.
 *
 * <p><b>Nicht geprueft wird, ob das Material ein GEGENSTAND ist</b> und kein reiner Block:
 * {@code Material.isItem()} wirft ohne laufenden Server, und dieser Leser laeuft in jeder
 * Probe ohne einen. Die Folge ist benannt und nicht verschwiegen: Ein Gewinn wie
 * {@code potatoes} - das ist das Kartoffel-BLOCK-Material - liesse sich eintragen und
 * endete bei jedem Treffer in einer Erstattung ({@code Lotteriebuch.Bescheid.ERSTATTET}).
 * Die Zeile kostet dann kein Geld, aber sie funktioniert nie. Geprueft wird das an der
 * Naht, die den Gegenstand wirklich baut.
 */
final class Lotterieliste {

    /** Wie viele Gewinne der Bericht hoechstens einzeln nennt. */
    private static final int ZEILEN_IM_BERICHT = 14;

    /** Was im Abschnitt stand - und woran es gehapert hat. */
    record Ergebnis(Lotteriewerte werte, List<String> klagen, List<String> bericht) {
    }

    private Lotterieliste() {
    }

    /**
     * Die Lotterie aus dem Abschnitt {@code lotterie:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null || !wurzel.contains(Lotteriewerte.PFAD_AN, true)
                && !wurzel.contains(Lotteriewerte.PFAD_GEWINNE, true)) {
            // Der ganze Abschnitt fehlt. KEINE Klage, aber eine Berichtzeile: Auf einem
            // Server, der schon lief, schreibt saveDefaultConfig die Datei nie um - die
            // Zeilen fehlen dort einfach, und es gilt die Lotterie des Programms.
            bericht.add("lotterie: fehlt in der config.yml - es gilt die ausgelieferte"
                    + " Lotterie des Programms.");
            return new Ergebnis(Lotteriewerte.VORGABE, klagen, bericht);
        }
        boolean an = jaNein(wurzel, klagen, bericht);
        int freie = zahl(wurzel, Lotteriewerte.PFAD_FREIE_LOSE,
                Lotteriewerte.VORGABE_FREIE_LOSE, 0, Integer.MAX_VALUE,
                "gratis Lose je Tag", klagen, bericht);
        int hoechste = zahl(wurzel, Lotteriewerte.PFAD_HOECHSTE_LOSE,
                Lotteriewerte.VORGABE_HOECHSTE_LOSE, 0, Integer.MAX_VALUE,
                "Lose je Tag (0 heisst unbegrenzt)", klagen, bericht);
        long grundpreis = Wirtschaftsliste.betrag(wurzel, Lotteriewerte.PFAD_GRUNDPREIS,
                "lotterie-grundpreis", Lotteriewerte.VORGABE_GRUNDPREIS,
                Lotteriewerte.HOECHSTER_HOECHSTPREIS, "Mark", klagen, bericht);
        int steigerung = zahl(wurzel, Lotteriewerte.PFAD_STEIGERUNG,
                Lotteriewerte.VORGABE_STEIGERUNG, Lotteriewerte.KLEINSTE_STEIGERUNG,
                Lotteriewerte.GROESSTE_STEIGERUNG, "Prozent", klagen, bericht);
        long hoechstpreis = Wirtschaftsliste.betrag(wurzel,
                Lotteriewerte.PFAD_HOECHSTPREIS, "lotterie-hoechstpreis",
                Lotteriewerte.VORGABE_HOECHSTPREIS,
                Lotteriewerte.HOECHSTER_HOECHSTPREIS, "Mark", klagen, bericht);
        List<Lotteriewerte.Gewinn> gewinne = gewinne(wurzel, klagen, bericht);

        // DIE ZWEI ABHAENGIGKEITEN, die der Konstruktor wirft: Der Hoechstpreis muss
        // ueber dem Grundpreis liegen, und die Tagesgrenze darf nicht unter der Zahl der
        // gratis Lose liegen. Beide Zahlen kommen aus verschiedenen Zeilen der Datei, und
        // beide sind fuer sich erlaubt - ein Wurf aus dem Konstruktor waere hier ein
        // abgebrochener Serverstart wegen zweier Zeilen, die einzeln in Ordnung sind.
        if (hoechstpreis < grundpreis) {
            klagen.add(Lotteriewerte.PFAD_HOECHSTPREIS + " ist " + hoechstpreis
                    + " und liegt unter " + Lotteriewerte.PFAD_GRUNDPREIS + " ("
                    + grundpreis + ") - dann kostet schon das erste bezahlte Los mehr,"
                    + " als erlaubt ist. Es gilt der Grundpreis als Hoechstpreis.");
            hoechstpreis = grundpreis;
        }
        if (hoechste > 0 && hoechste < freie) {
            klagen.add(Lotteriewerte.PFAD_HOECHSTE_LOSE + " ist " + hoechste + ", aber "
                    + Lotteriewerte.PFAD_FREIE_LOSE + " ist " + freie + " - dann ist ein"
                    + " Teil der gratis Lose nie zu ziehen, und niemand erfaehrt, welcher."
                    + " Es gelten " + hoechste + " gratis Lose.");
            freie = hoechste;
        }

        Lotteriewerte werte = new Lotteriewerte(an, freie, hoechste, grundpreis,
                steigerung, hoechstpreis, gewinne);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Lotterie im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Angeltabelle: die einzige Schranke gegen den Wert,
     * der im erlaubten Bereich liegt und trotzdem falsch ist. Wer "1. gratis - 2. 50"
     * liest, sieht die verschobene Null; keine Zahlengrenze sieht sie.
     *
     * <p><b>Der mittlere Markgewinn steht dabei</b>, und er ist die wichtigste Zahl des
     * ganzen Berichts: Liegt er ueber dem Preis der ersten Lose, ist die Lotterie eine
     * Geldquelle und keine Senke - dann laeuft die Wirtschaft voll (Plan 9.3), und man
     * sieht es an keiner anderen Stelle.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nimmt.
     */
    static String rechenprobe(Lotteriewerte werte) {
        if (!werte.greift()) {
            return "Lotterie: AUS - in Altfurt wird nicht gezogen.";
        }
        StringBuilder satz = new StringBuilder("Lotterie: ");
        satz.append(werte.gewinne().size()).append(" Gewinne, ")
                .append(werte.freieLoseJeTag()).append(" gratis je Tag, hoechstens ")
                .append(werte.hoechsteLoseJeTag() == 0
                        ? "unbegrenzt" : String.valueOf(werte.hoechsteLoseJeTag()))
                .append(". Staffel: ").append(werte.staffelAlsText(5))
                .append(". Im Mittel ").append(Losregel.mittlererMarkgewinn(werte))
                .append(" Mark je Los (Gegenstaende und Goldmark nicht gerechnet). ");
        List<Lotteriewerte.Gewinn> gewinne = werte.gewinne();
        for (int i = 0; i < gewinne.size() && i < ZEILEN_IM_BERICHT; i++) {
            Lotteriewerte.Gewinn gewinn = gewinne.get(i);
            if (i > 0) {
                satz.append(", ");
            }
            satz.append(gewinn.schluessel()).append(' ')
                    .append(Losregel.promilleVon(werte, gewinn.schluessel()))
                    .append(" Promille");
        }
        if (gewinne.size() > ZEILEN_IM_BERICHT) {
            satz.append(", und ").append(gewinne.size() - ZEILEN_IM_BERICHT)
                    .append(" weitere");
        }
        return satz.append('.').toString();
    }

    private static boolean jaNein(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        if (!wurzel.contains(Lotteriewerte.PFAD_AN, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Lotteriewerte.PFAD_AN,
                    String.valueOf(Lotteriewerte.VORGABE.an())));
            return Lotteriewerte.VORGABE.an();
        }
        Object roh = wurzel.get(Lotteriewerte.PFAD_AN);
        if (!(roh instanceof Boolean wert)) {
            klagen.add(Lotteriewerte.PFAD_AN + " ist kein ja/nein ("
                    + Wirtschaftsliste.wieGetippt(roh) + ") - verworfen. "
                    + Wirtschaftsliste.esBleibt(
                            String.valueOf(Lotteriewerte.VORGABE.an())));
            return Lotteriewerte.VORGABE.an();
        }
        return wert;
    }

    /** Eine Anzahl mit Unter- und Obergrenze - kein Geld, also ohne Sprungschutz. */
    private static int zahl(ConfigurationSection wurzel, String pfad, int vorgabe,
                            int kleinstes, int groesstes, String einheit,
                            List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < kleinstes || wert > groesstes) {
            klagen.add(pfad + " ist " + wert + " und liegt nicht zwischen " + kleinstes
                    + " und " + groesstes + " " + einheit + " - verworfen. "
                    + Wirtschaftsliste.esBleibt(String.valueOf(vorgabe)));
            return vorgabe;
        }
        return (int) wert;
    }

    /**
     * Die Gewinntabelle - Zeile fuer Zeile geprueft, kaputte Zeilen beklagt und
     * uebersprungen.
     *
     * <p>Fehlt der Abschnitt, gilt die Tabelle des Programms. Ist er LEER, gilt eine
     * leere Tabelle - und das ist etwas anderes: Wer alle Gewinne herausnimmt, will keine
     * Lotterie und soll sie nicht bekommen, ohne den Schalter zu finden.
     */
    private static List<Lotteriewerte.Gewinn> gewinne(ConfigurationSection wurzel,
                                                      List<String> klagen,
                                                      List<String> bericht) {
        if (!wurzel.contains(Lotteriewerte.PFAD_GEWINNE, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Lotteriewerte.PFAD_GEWINNE,
                    Lotteriewerte.VORGABE_GEWINNE.size() + " Gewinne des Programms"));
            return Lotteriewerte.VORGABE_GEWINNE;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Lotteriewerte.PFAD_GEWINNE);
        if (abschnitt == null) {
            klagen.add(Lotteriewerte.PFAD_GEWINNE + " ist keine Liste von Gewinnen mit"
                    + " Art, Gewicht und Menge - verworfen. Es gilt die Tabelle des"
                    + " Programms.");
            return Lotteriewerte.VORGABE_GEWINNE;
        }
        List<Lotteriewerte.Gewinn> tabelle = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Lotteriewerte.Gewinn gewinn = gewinnzeile(abschnitt, name, klagen);
            if (gewinn != null) {
                tabelle.add(gewinn);
            }
        }
        if (tabelle.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            // JEDE Zeile war kaputt. Die Tabelle des Programms waere hier falsch: Der
            // Betreiber hat eine eigene gewollt, und mit der Vorgabe verteilte die
            // Lotterie Gewinne, die er gerade herausgenommen hat.
            bericht.add("lotterie.gewinne: keine einzige Zeile war brauchbar - in Altfurt"
                    + " wird bis zur naechsten Berichtigung nicht gezogen.");
        }
        return tabelle;
    }

    /**
     * Eine Gewinnzeile - oder {@code null}, wenn sie wegfaellt.
     *
     * <p>Der ganze Abschnitt einer Zeile wird gelesen, bevor geklagt wird: Wer nach dem
     * ersten Fehler aufhoert, nennt dem Betreiber einen von drei Fehlern derselben Zeile
     * und schickt ihn dreimal in die Datei.
     *
     * <p><b>Heisst {@code gewinnzeile} und nicht {@code eineZeile}</b> [22.11.2026]: Der
     * zweite Name gehoert im Haus {@code Sprache.eineZeile}, und {@code VorlageTest} 10i
     * sucht genau diese Zeichenfolge im Quelltext, um jede Holstelle eines Satzes zu
     * finden. Die Probe hat die Namenskollision gefunden, und sie hatte recht: Ein
     * zweiter {@code eineZeile} in einem Leser, der gar keine Saetze holt, ist auch fuer
     * einen Menschen verwirrend.
     */
    private static Lotteriewerte.Gewinn gewinnzeile(ConfigurationSection abschnitt,
                                                  String name, List<String> klagen) {
        String pfad = Lotteriewerte.PFAD_GEWINNE + "." + name;
        ConfigurationSection zeile = abschnitt.getConfigurationSection(name);
        if (zeile == null) {
            klagen.add(pfad + " ist kein Abschnitt mit " + Lotteriewerte.SCHLUESSEL_ART
                    + ", " + Lotteriewerte.SCHLUESSEL_GEWICHT + " und "
                    + Lotteriewerte.SCHLUESSEL_MENGE
                    + " - diese Zeile faellt weg, die uebrigen gelten.");
            return null;
        }
        List<String> eigene = new ArrayList<>();

        Lotteriewerte.Art art = null;
        Object rohArt = zeile.get(Lotteriewerte.SCHLUESSEL_ART);
        if (!(rohArt instanceof String wort)) {
            eigene.add(Lotteriewerte.SCHLUESSEL_ART + " ist "
                    + Wirtschaftsliste.wieGetippt(rohArt) + " - erwartet wird mark,"
                    + " goldmark oder gegenstand");
        } else {
            try {
                art = Lotteriewerte.Art.valueOf(wort.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unbekannt) {
                eigene.add(Lotteriewerte.SCHLUESSEL_ART + " ist \"" + wort + "\" -"
                        + " erwartet wird mark, goldmark oder gegenstand");
            }
        }

        long gewicht = 0L;
        Object rohGewicht = zeile.get(Lotteriewerte.SCHLUESSEL_GEWICHT);
        if (!(rohGewicht instanceof Number zahl) || zahl.longValue() < 1L
                || zahl.longValue() > Lotteriewerte.HOECHSTES_GEWICHT) {
            eigene.add(Lotteriewerte.SCHLUESSEL_GEWICHT + " ist "
                    + Wirtschaftsliste.wieGetippt(rohGewicht) + " - erwartet wird 1 bis "
                    + Lotteriewerte.HOECHSTES_GEWICHT);
        } else {
            gewicht = zahl.longValue();
        }

        long menge = 0L;
        Object rohMenge = zeile.get(Lotteriewerte.SCHLUESSEL_MENGE);
        if (!(rohMenge instanceof Number zahl) || zahl.longValue() < 1L) {
            eigene.add(Lotteriewerte.SCHLUESSEL_MENGE + " ist "
                    + Wirtschaftsliste.wieGetippt(rohMenge) + " - erwartet wird"
                    + " mindestens 1; ein Gewinn, der nichts bringt, gehoert als mark mit"
                    + " kleinem Betrag in die Tabelle");
        } else {
            menge = zahl.longValue();
        }

        String gegenstand = text(zeile, Lotteriewerte.SCHLUESSEL_GEGENSTAND);
        String anzeigename = text(zeile, Lotteriewerte.SCHLUESSEL_NAME);

        if (art == Lotteriewerte.Art.GEGENSTAND) {
            if (gegenstand.isEmpty()) {
                eigene.add(Lotteriewerte.SCHLUESSEL_GEGENSTAND + " fehlt - bei einem"
                        + " Gewinn der Art gegenstand ist der Name der Gegenstand");
            } else {
                String klein = gegenstand.toLowerCase(Locale.ROOT);
                String warum = Verdienstliste.warumKeinMaterial(klein);
                if (warum != null) {
                    eigene.add(Lotteriewerte.SCHLUESSEL_GEGENSTAND + " " + warum);
                } else {
                    gegenstand = klein;
                }
            }
            if (menge > Lotteriewerte.Gewinn.HOECHSTE_STUECKZAHL) {
                eigene.add(Lotteriewerte.SCHLUESSEL_MENGE + " ist " + menge + " Stueck"
                        + " und damit mehr als ein voller Rucksack ("
                        + Lotteriewerte.Gewinn.HOECHSTE_STUECKZAHL + ") - der Gewinn"
                        + " waere nie zu liefern");
            }
        } else if (art != null) {
            if (!gegenstand.isEmpty()) {
                eigene.add(Lotteriewerte.SCHLUESSEL_GEGENSTAND + " steht bei einem Gewinn"
                        + " der Art " + art.name().toLowerCase(Locale.ROOT) + " - eine der"
                        + " beiden Angaben wuerde stumm ignoriert");
            }
            if (!anzeigename.isEmpty()) {
                eigene.add(Lotteriewerte.SCHLUESSEL_NAME + " steht bei einem Gewinn der"
                        + " Art " + art.name().toLowerCase(Locale.ROOT) + " - ein Betrag"
                        + " hat keinen Namen, auf dem er stehen koennte");
            }
            if (menge > Lotteriewerte.Gewinn.HOECHSTER_GELDGEWINN) {
                eigene.add(Lotteriewerte.SCHLUESSEL_MENGE + " ist " + menge + " und liegt"
                        + " ueber " + Lotteriewerte.Gewinn.HOECHSTER_GELDGEWINN);
            }
        }

        if (!eigene.isEmpty()) {
            klagen.add(pfad + ": " + String.join("; ", eigene)
                    + " - diese Zeile faellt weg, die uebrigen gelten.");
            return null;
        }
        return new Lotteriewerte.Gewinn(name.trim(), art, (int) gewicht, menge,
                gegenstand, anzeigename);
    }

    /**
     * Ein Textfeld einer Zeile - leer, wenn es fehlt.
     *
     * <p>Eine Zahl wird NICHT als Text gelesen: Wer {@code name: 2026} schreibt, hat
     * sich vertippt, und ein Gewinn, der im Spiel "2026" heisst, ist die Antwort, die
     * niemand erwartet. Fehlt das Feld, ist das kein Fehler - beide sind freiwillig.
     */
    private static String text(ConfigurationSection zeile, String schluessel) {
        Object roh = zeile.get(schluessel);
        return roh instanceof String wort ? wort.trim() : "";
    }
}
