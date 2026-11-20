package de.freimarken.paper;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Rangwerte;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code raenge:} - die Rangleiter (Plan 9, Schritt 12).
 *
 * <p>Ein eigener Leser wie bei den Herausforderungen und aus demselben Grund: Hier wird
 * eine LISTE von Eintraegen gelesen, von denen jeder drei eigene Tabellen traegt
 * (Bedingungen, Grenzen, Rechte).
 *
 * <p><b>Die Reihenfolge der Datei IST die Leiter.</b> Bukkits {@code getKeys(false)} gibt
 * die Schluessel so zurueck, wie sie in der Datei stehen - und oben/unten ist hier keine
 * Schoenheit, sondern die Regel: Gestiegen wird Sprosse fuer Sprosse.
 *
 * <p><b>Eine kaputte Stufe faellt GANZ weg</b>, nicht nur ihre kaputte Zeile - und das ist
 * der Unterschied zu fast jedem anderen Leser dieses Plugins. Eine Stufe, der eine
 * Bedingung fehlt, ist nicht dieselbe Stufe mit einem Fehler, sondern eine LEICHTERE:
 * Spieler bekaemen Rechte, die sie nicht verdient haben. Eine weggefallene Stufe nimmt
 * dagegen niemandem etwas, was er schon hat (das Buch behaelt den erreichten Rang), sie
 * ist nur nicht mehr zu erreichen - bis der Betreiber die Zeile berichtigt.
 */
final class Rangliste {

    /** Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL =
            List.of(Rangwerte.PFAD_AN, Rangwerte.PFAD_LEITER);

    record Ergebnis(Rangwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Rangliste() {
    }

    /**
     * Der Abschnitt {@code raenge:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Rangwerte.VORGABE, klagen, bericht);
        }
        boolean an = schalter(wurzel, klagen, bericht);
        List<Rangwerte.Stufe> leiter = leiter(wurzel, klagen, bericht);
        try {
            Rangwerte werte = new Rangwerte(an, leiter);
            for (String satz : werte.ungereimtheiten()) {
                // Keine Klage, sondern ein Bericht: Es ist erlaubt und fast immer ein
                // Vertipper. Im Spiel faellt es gar nicht auf, weil Rangzahl die hoechste
                // Zahl aus Recht UND Datei nimmt - gemeldet gehoert es trotzdem.
                bericht.add(satz);
            }
            return new Ergebnis(werte, klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt aus einer Zusage, die der Leser nicht selbst prueft - etwa der
            // untersten Stufe mit Bedingung. Die Leiter des Programms ist der Rueckfall:
            // Eine halbe Leiter waere eine, auf der Spieler haengenbleiben.
            klagen.add("Die Rangleiter passt nicht zusammen: " + unbrauchbar.getMessage()
                    + " " + Wirtschaftsliste.esBleibt(Rangwerte.VORGABE_LEITER.size()
                            + " Stufen des Programms"));
            return new Ergebnis(Rangwerte.VORGABE.mitAn(an), klagen, bericht);
        }
    }

    /**
     * Rechte, die eine Stufe vergibt, die es im Plugin aber gar nicht gibt.
     *
     * <p>Getrennt von {@link #ausKonfiguration}, weil nur ein LAUFENDER Server weiss,
     * welche Rechte angemeldet sind - der Leser selbst bleibt ohne Server pruefbar. Ein
     * vertipptes {@code laden.miete} faellt sonst nirgends auf: Das Attachment setzt es
     * klaglos, und es fragt es nie jemand.
     *
     * @param angemeldet die Namen der angemeldeten Rechte, MIT Vorsilbe
     */
    static List<String> unbekannteRechte(Rangwerte werte, Set<String> angemeldet) {
        if (werte == null || angemeldet == null) {
            return List.of();
        }
        Set<String> klein = new TreeSet<>();
        for (String name : angemeldet) {
            if (name != null) {
                klein.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> unbekannt = new ArrayList<>();
        for (Rangwerte.Stufe stufe : werte.leiter()) {
            for (String recht : stufe.rechte()) {
                if (!klein.contains(Rangwerte.VORSILBE + recht)) {
                    unbekannt.add(Rangwerte.PFAD_LEITER + "." + stufe.schluessel() + "."
                            + Rangwerte.SCHLUESSEL_RECHTE + ": " + Rangwerte.VORSILBE
                            + recht + " ist kein Recht dieses Plugins - der Rang vergibt"
                            + " es, und niemand fragt danach.");
                }
            }
        }
        return List.copyOf(unbekannt);
    }

    /**
     * Die Leiter im Klartext - die Rechenprobe.
     *
     * <p>Sie nennt die Stufen von unten nach oben mit ihren Bedingungen. Was eine Stufe
     * GIBT, steht nicht darin: Das sind vier Zahlen je Stufe, und eine Zeile, die alles
     * nennt, liest niemand mehr. Wer es wissen will, liest die Datei - oder spaeter das
     * Dashboard (Plan 12a).
     *
     * <p>Getrennt von {@link #wirkungslose}, weil das zwei Dinge sind: Dies ist, was
     * eingestellt IST, jenes ist eine Warnung darueber, dass es nicht wirkt.
     */
    static String rechenprobe(Rangwerte werte) {
        if (!werte.greift()) {
            return "Raenge: keine - "
                    + (werte.an() ? "die Leiter ist leer" : Rangwerte.PFAD_AN + " ist aus")
                    + ". Es gelten die Zahlen aus der config.yml, fuer jeden dieselben.";
        }
        List<String> stufen = new ArrayList<>();
        for (Rangwerte.Stufe stufe : werte.leiter()) {
            stufen.add(stufe.schluessel() + " (" + bedingungstext(stufe) + ")");
        }
        return "Raenge: " + werte.leiter().size() + " Stufen - "
                + String.join(" -> ", stufen) + ".";
    }

    /**
     * Welche Grenzen die Leiter nicht heben kann, weil die Datei sie schon vergibt.
     *
     * <p><b>Die gefaehrlichste Schranke dieser Leiter</b>, und sie steht in einer anderen
     * Zeile der Datei: Die Zahl in der {@code config.yml} ist eine UNTERGRENZE fuer
     * JEDEN. {@code Rangzahl} nimmt die hoechste Zahl aus Recht und Datei; steht in
     * {@code parzellen.hoechstzahl} eine 4, bekommt sie jeder Reisende - und der ganze
     * Aufstieg auf dieser Grenze ist wirkungslos, ohne dass irgendwo etwas rot wuerde.
     * Genau so stand die ausgelieferte Datei bis Teil H dieses Schrittes da.
     *
     * <p><b>VIER Lagen und nicht eine</b> [20.11.2026, Teil H]: Die alte Bedingung
     * ({@code unten >= hoechste}) war in beide Richtungen zu grob.
     * <ul>
     *   <li><b>Die Null, die nicht schliesst:</b> Die ausgelieferte Datei verspricht bei
     *       den Inseln und den Freistaedten, eine 0 sei "der Weg, das abzuschalten, ohne
     *       das Recht anzufassen". Vergibt eine Stufe dort eine 1, ist das Versprechen
     *       gebrochen - und zwar stumm.</li>
     *   <li><b>Die ganze Leiter tot:</b> der alte Fall - die Datei gibt jedem, was erst
     *       die oberste Stufe geben wollte.</li>
     *   <li><b>Die unteren Stufen tot:</b> Leiter 1/2/4 und Datei 2 - der Aufstieg zum
     *       Siedler bringt auf dieser Grenze nichts. Die alte Bedingung schwieg dazu,
     *       denn 2 ist nicht groesser als 4.</li>
     *   <li><b>Richtig eingestellt und darum STILL:</b> Die Datei nennt hoechstens die
     *       Zahl der untersten Stufe. Das gilt auch fuer eine Grenze, die die Leiter
     *       ueberhaupt nicht staffelt (Inseln und Freistaedte stehen auf allen Stufen bei
     *       1) - eine Warnung dort waere nach Teil H die Haelfte aller Zeilen, und ein
     *       Startbericht, dessen Warnungen man ueberliest, warnt nicht mehr.</li>
     * </ul>
     *
     * <p>Fehlt zu einer bekannten Grenze der Eintrag in {@code untergrenzen}, sagt diese
     * Methode DAS - sie ist dann unbeobachtet. Der Riegel steht hier und nicht als Tafel
     * ueber dem Quelltext der Karte: Eine Probe kann ihn stellen, und auf dem Server, dem
     * eine Zeile fehlt, erscheint er auch.
     *
     * <p>Paketprivat und einzeln, damit eine Probe genau diesen Satz nehmen kann.
     *
     * @param untergrenzen je Grenzname die Zahl aus der {@code config.yml}
     */
    static List<String> wirkungslose(Rangwerte werte, Map<String, Integer> untergrenzen) {
        if (werte == null || !werte.greift() || untergrenzen == null) {
            return List.of();
        }
        List<String> saetze = new ArrayList<>();
        // SORTIERT: Map.copyOf sagt keine Reihenfolge zu, und ein Startbericht, der bei
        // jedem Lauf anders herum steht, ist nicht zu vergleichen.
        for (String name : new TreeSet<>(Rangwerte.BEKANNTE_ZAHLEN)) {
            Integer unten = untergrenzen.get(name);
            if (unten == null) {
                saetze.add("Zur Grenze " + name + " nennt der Startbericht keine Zahl aus"
                        + " der config.yml - sie steht in Rangwerte.BEKANNTE_ZAHLEN, und"
                        + " ob die Leiter sie ueberhaupt hebt, prueft darum niemand.");
                continue;
            }
            int hoechste = 0;
            String hoechsterRang = null;
            int niedrigste = 0;
            String niedrigsterRang = null;
            // Die TOTEN Stufen: die, deren Zahl die Datei schon jedem gibt. Gezaehlt wird
            // in der Reihenfolge der Leiter, denn so liest der Betreiber seine Datei.
            List<String> tote = new ArrayList<>();
            for (Rangwerte.Stufe stufe : werte.leiter()) {
                int wert = stufe.zahl(name).orElse(0);
                if (wert <= 0) {
                    // Eine Stufe, die diese Grenze nicht nennt, hebt sie nicht und ist
                    // darum auch nicht "tot" - dort entscheidet allein die Datei.
                    continue;
                }
                if (wert > hoechste) {
                    hoechste = wert;
                    hoechsterRang = stufe.schluessel();
                }
                if (niedrigsterRang == null || wert < niedrigste) {
                    niedrigste = wert;
                    niedrigsterRang = stufe.schluessel();
                }
                if (wert <= unten) {
                    tote.add(stufe.schluessel());
                }
            }
            if (hoechsterRang == null) {
                // Die Leiter hebt diese Grenze nirgends. Dann ist die Zahl in der Datei
                // die ganze Regel, und daran ist nichts zu beklagen.
                continue;
            }
            if (unten == 0) {
                saetze.add("Die config.yml gibt " + name + ": 0 - das schaltet diese"
                        + " Grenze NICHT ab. Die Stufe " + niedrigsterRang + " vergibt"
                        + " " + Rangwerte.VORSILBE + name + "." + niedrigste + ", und es"
                        + " gilt die hoechste Zahl aus Recht UND Datei. Zum Abschalten"
                        + " muss die Zeile auch aus raenge.leiter heraus.");
            } else if (unten <= niedrigste) {
                // RICHTIG EINGESTELLT: Die Datei nennt den Boden, die Leiter hebt
                // darueber. Hier gehoert keine Zeile in den Startbericht.
                continue;
            } else if (unten >= hoechste) {
                saetze.add("Die config.yml gibt " + name + ": " + unten
                        + " an JEDEN, die Leiter hebt es hoechstens auf "
                        + hoechste + " (" + hoechsterRang + ") - der Aufstieg"
                        + " wirkt auf diese Grenze nicht.");
            } else {
                // DREI KURZE SAETZE UND EINE LISTE, kein Satz mit der Liste als
                // Subjekt: "damit hebt reisender, siedler diese Grenze nicht" war bei
                // EINER toten Stufe richtig und bei zwei falsch. Eine Meldung, deren
                // Grammatik von einer Anzahl abhaengt, ist entweder halb falsch oder
                // sie braucht zwei Fassungen.
                saetze.add("Die config.yml gibt " + name + ": " + unten
                        + " an JEDEN. Ohne Wirkung auf diese Grenze: "
                        + String.join(", ", tote) + ". In die Datei gehoert hoechstens"
                        + " die Zahl der untersten Stufe (" + niedrigsterRang + ": "
                        + niedrigste + ").");
            }
        }
        return List.copyOf(saetze);
    }

    private static String bedingungstext(Rangwerte.Stufe stufe) {
        if (stufe.ohneBedingung()) {
            return "ohne Bedingung";
        }
        List<String> teile = new ArrayList<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            Long wert = stufe.bedingungen().get(art);
            if (wert != null) {
                teile.add(wert + " " + art.beschreibung());
            }
        }
        return String.join(", ", teile);
    }

    private static boolean schalter(ConfigurationSection wurzel, List<String> klagen,
                                    List<String> bericht) {
        Boolean gelesen = Wirtschaftsliste.jaNein(wurzel, Rangwerte.PFAD_AN,
                Rangwerte.VORGABE.an(), klagen, bericht);
        if (gelesen == null) {
            return Rangwerte.VORGABE.an();
        }
        if (gelesen != Rangwerte.VORGABE.an()) {
            bericht.add(Rangwerte.PFAD_AN + " = " + gelesen + " gilt.");
        }
        return gelesen;
    }

    /**
     * Die Leiter - Stufe fuer Stufe, in der Reihenfolge der Datei.
     *
     * <p>Fehlt der Abschnitt, gilt die Leiter des Programms. Ist er LEER, gilt eine leere
     * Leiter - wer alle Stufen herausnimmt, will keine Raenge und soll sie nicht aus der
     * Vorgabe zurueckbekommen.
     */
    private static List<Rangwerte.Stufe> leiter(ConfigurationSection wurzel,
                                                List<String> klagen,
                                                List<String> bericht) {
        if (!wurzel.contains(Rangwerte.PFAD_LEITER, true)) {
            bericht.add(Wirtschaftsliste.fehltText(Rangwerte.PFAD_LEITER,
                    Rangwerte.VORGABE_LEITER.size() + " Stufen des Programms"));
            return Rangwerte.VORGABE_LEITER;
        }
        ConfigurationSection abschnitt =
                wurzel.getConfigurationSection(Rangwerte.PFAD_LEITER);
        if (abschnitt == null) {
            klagen.add(Rangwerte.PFAD_LEITER + " ist keine Leiter von Raengen -"
                    + " verworfen. Es gilt die Leiter des Programms.");
            return Rangwerte.VORGABE_LEITER;
        }
        List<Rangwerte.Stufe> leiter = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            Rangwerte.Stufe stufe = stufe(abschnitt, name, klagen);
            if (stufe != null) {
                leiter.add(stufe);
            }
        }
        if (leiter.isEmpty() && !abschnitt.getKeys(false).isEmpty()) {
            bericht.add(Rangwerte.PFAD_LEITER + ": keine einzige Stufe war brauchbar -"
                    + " bis zur naechsten Berichtigung gelten fuer jeden die Zahlen aus"
                    + " der config.yml.");
        }
        return leiter;
    }

    /** Eine Stufe - oder {@code null}, wenn sie ganz wegfaellt. */
    private static Rangwerte.Stufe stufe(ConfigurationSection abschnitt, String name,
                                         List<String> klagen) {
        String pfad = Rangwerte.PFAD_LEITER + "." + name;
        String klein = name.trim().toLowerCase(Locale.ROOT);
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(name);
        if (eintrag == null) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(abschnitt.get(name))
                    + " - erwartet werden die Zeilen "
                    + Rangwerte.SCHLUESSEL_BEDINGUNGEN + ", "
                    + Rangwerte.SCHLUESSEL_ZAHLEN + " und "
                    + Rangwerte.SCHLUESSEL_RECHTE + " darunter. Diese Stufe faellt weg.");
            return null;
        }
        Map<Bedingungsart, Long> bedingungen = bedingungen(eintrag, pfad, klagen);
        if (bedingungen == null) {
            return null;
        }
        Map<String, Integer> zahlen = zahlen(eintrag, pfad, klagen);
        if (zahlen == null) {
            return null;
        }
        List<String> rechte = rechte(eintrag, pfad, klagen);
        if (rechte == null) {
            return null;
        }
        try {
            return new Rangwerte.Stufe(klein, bedingungen, zahlen, rechte);
        } catch (IllegalArgumentException unbrauchbar) {
            klagen.add(pfad + ": " + unbrauchbar.getMessage() + " Diese Stufe faellt"
                    + " weg.");
            return null;
        }
    }

    /**
     * Die Bedingungen - oder {@code null}, wenn die Stufe wegfaellt.
     *
     * <p>Der Abschnitt darf FEHLEN und heisst dann: keine Bedingung, steht jedem zu. So
     * ist der unterste Rang gebaut.
     */
    private static Map<Bedingungsart, Long> bedingungen(ConfigurationSection eintrag,
                                                        String pfad,
                                                        List<String> klagen) {
        if (!eintrag.contains(Rangwerte.SCHLUESSEL_BEDINGUNGEN, true)) {
            return new EnumMap<>(Bedingungsart.class);
        }
        ConfigurationSection abschnitt =
                eintrag.getConfigurationSection(Rangwerte.SCHLUESSEL_BEDINGUNGEN);
        if (abschnitt == null) {
            klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_BEDINGUNGEN + " ist "
                    + Wirtschaftsliste.wieGetippt(
                            eintrag.get(Rangwerte.SCHLUESSEL_BEDINGUNGEN))
                    + " - erwartet wird je Zeile eine Bedingung mit einer Zahl. Diese"
                    + " Stufe faellt weg.");
            return null;
        }
        Map<Bedingungsart, Long> bedingungen = new EnumMap<>(Bedingungsart.class);
        for (String name : abschnitt.getKeys(false)) {
            var art = Bedingungsart.ausSchluessel(name);
            if (art.isEmpty()) {
                // GANZ WEG und nicht nur diese Zeile: Eine Stufe, der eine Bedingung
                // fehlt, ist eine LEICHTERE Stufe - und dann bekaeme jemand Rechte, die
                // er nicht verdient hat. Eine weggefallene Stufe nimmt dagegen niemandem
                // etwas, was er schon hat.
                klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_BEDINGUNGEN + "." + name
                        + " kennt das Programm nicht. Es gibt: "
                        + String.join(", ", Bedingungsart.alleSchluessel())
                        + ". Diese Stufe faellt weg - sonst waere sie leichter, als sie"
                        + " dasteht.");
                return null;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 1L
                    || zahl.longValue() > art.get().hoechsterWert()
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
                klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_BEDINGUNGEN + "." + name
                        + " ist " + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird"
                        + " eine ganze Zahl von 1 bis " + art.get().hoechsterWert()
                        + ". Diese Stufe faellt weg.");
                return null;
            }
            bedingungen.put(art.get(), zahl.longValue());
        }
        return bedingungen;
    }

    /** Die Grenzen - oder {@code null}, wenn die Stufe wegfaellt. */
    private static Map<String, Integer> zahlen(ConfigurationSection eintrag, String pfad,
                                               List<String> klagen) {
        if (!eintrag.contains(Rangwerte.SCHLUESSEL_ZAHLEN, true)) {
            return new LinkedHashMap<>();
        }
        ConfigurationSection abschnitt =
                eintrag.getConfigurationSection(Rangwerte.SCHLUESSEL_ZAHLEN);
        if (abschnitt == null) {
            klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_ZAHLEN + " ist "
                    + Wirtschaftsliste.wieGetippt(eintrag.get(Rangwerte.SCHLUESSEL_ZAHLEN))
                    + " - erwartet wird je Zeile eine Grenze mit einer Zahl. Diese Stufe"
                    + " faellt weg.");
            return null;
        }
        Map<String, Integer> zahlen = new LinkedHashMap<>();
        for (String name : abschnitt.getKeys(false)) {
            String klein = name.trim().toLowerCase(Locale.ROOT);
            if (!Rangwerte.BEKANNTE_ZAHLEN.contains(klein)) {
                klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_ZAHLEN + "." + name
                        + " kennt das Programm nicht. Es gibt: "
                        + String.join(", ", new TreeSet<>(Rangwerte.BEKANNTE_ZAHLEN))
                        + ". Diese Stufe faellt weg - eine Grenze ohne Leser waere ein"
                        + " Recht, das vergeben wird und nichts tut.");
                return null;
            }
            Object roh = abschnitt.get(name);
            if (!(roh instanceof Number zahl) || zahl.longValue() < 0L
                    || zahl.longValue() > Rangwerte.HOECHSTE_ZAHL
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
                klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_ZAHLEN + "." + name + " ist "
                        + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine ganze"
                        + " Zahl von 0 bis " + Rangwerte.HOECHSTE_ZAHL + ". Diese Stufe"
                        + " faellt weg.");
                return null;
            }
            zahlen.put(klein, (int) zahl.longValue());
        }
        return zahlen;
    }

    /** Die Rechte - oder {@code null}, wenn die Stufe wegfaellt. */
    private static List<String> rechte(ConfigurationSection eintrag, String pfad,
                                       List<String> klagen) {
        if (!eintrag.contains(Rangwerte.SCHLUESSEL_RECHTE, true)) {
            return new ArrayList<>();
        }
        Object roh = eintrag.get(Rangwerte.SCHLUESSEL_RECHTE);
        if (!(roh instanceof List<?> zeilen)) {
            klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_RECHTE + " ist "
                    + Wirtschaftsliste.wieGetippt(roh) + " - erwartet wird eine Liste von"
                    + " Rechten, je Zeile eines mit einem Strich davor. Diese Stufe"
                    + " faellt weg.");
            return null;
        }
        List<String> rechte = new ArrayList<>();
        for (Object zeile : zeilen) {
            if (!(zeile instanceof String name) || name.isBlank()) {
                klagen.add(pfad + "." + Rangwerte.SCHLUESSEL_RECHTE + " enthaelt "
                        + Wirtschaftsliste.wieGetippt(zeile) + " - erwartet wird der Name"
                        + " eines Rechtes ohne die Vorsilbe " + Rangwerte.VORSILBE
                        + ". Diese Stufe faellt weg.");
                return null;
            }
            rechte.add(name);
        }
        return rechte;
    }
}
