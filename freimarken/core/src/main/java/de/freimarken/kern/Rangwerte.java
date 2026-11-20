package de.freimarken.kern;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Die Rangleiter - wer wodurch aufsteigt und was er dann darf (Plan 9, Schritt 12).
 *
 * <p><b>Ein Rang ist ein BUENDEL RECHTE und keine Zahl im Programm</b> [entschieden
 * 18.11.2026, Nutzer: "weiter nach dem grundsatz immer frei einstellbar, zb. auch welcher
 * rank wie viele gibt"]. Was ein Rang gibt, steht in {@link Stufe#zahlen} und
 * {@link Stufe#rechte}; das Programm vergibt daraus Rechte, und die eine Stelle, die sie
 * wieder zu einer Obergrenze macht, ist {@code Rangzahl} im Plugin. Damit bleibt die
 * Leiter da, wo der Betreiber sie aendern kann, und keine Zahl aus Abschnitt 9 des Plans
 * steht im Quelltext.
 *
 * <p><b>Die Leiter wird IN DER REIHENFOLGE DER DATEI gestiegen.</b> Der erste Eintrag ist
 * der Rang, den jeder beim ersten Beitritt hat; danach gilt der hoechste Rang, fuer den
 * dieser UND jeder darunter erfuellt ist. Kein Ueberspringen: Ein Spieler, der die
 * Buergerstunden hat, aber die Siedlerbedingung nie erfuellt hat, bleibt Reisender - und
 * das ist keine Haerte, sondern die einzige Lesart, in der eine LEITER eine Leiter ist.
 * Wer Ueberspringen will, gibt der oberen Stufe die Bedingungen der unteren mit.
 *
 * <p><b>Kein Abstieg.</b> Diese Klasse rechnet nur aus, was einem Spieler ZUSTEHT; das
 * Buch darueber behaelt den einmal erreichten Rang. Sonst verloere ein Buerger seinen
 * Rang, weil er seine Parzelle verkauft hat - und mit ihm die Grenze, unter der sein
 * uebriger Besitz liegt.
 *
 * @param an     ob es ueberhaupt Raenge gibt; aus heisst: jeder behaelt, was die
 *               config.yml ohnehin erlaubt
 * @param leiter die Stufen von unten nach oben, in der Reihenfolge der Datei
 */
public record Rangwerte(boolean an, List<Rangwerte.Stufe> leiter) {

    /**
     * EINE Stufe der Leiter.
     *
     * @param schluessel  der Name in der Datei, kleingeschrieben - er steht in der
     *                    Ablage, in der Sprachdatei und im Bericht
     * @param bedingungen was erfuellt sein muss; LEER heisst: steht jedem zu (so ist der
     *                    unterste Rang gebaut)
     * @param zahlen      welche Obergrenze dieser Rang hebt, je Name eine Zahl
     * @param rechte      welche Rechte er gibt, ohne die Vorsilbe {@code freimarken.}
     */
    public record Stufe(String schluessel, Map<Bedingungsart, Long> bedingungen,
                        Map<String, Integer> zahlen, List<String> rechte) {

        public Stufe {
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Eine Rangstufe ohne Schluessel gibt"
                        + " es nicht - an ihm haengt die Zeile in der Ablage");
            }
            schluessel = schluessel.trim().toLowerCase(Locale.ROOT);
            if (!schluessel.matches(MUSTER)) {
                throw new IllegalArgumentException("Der Rang \"" + schluessel
                        + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                        + " Unterstrich und Bindestrich, und beginnen muss er mit einem"
                        + " Buchstaben.");
            }
            if (schluessel.length() > HOECHSTE_SCHLUESSELLAENGE) {
                // Dieselbe Naht wie beim Berufs- und beim Aufgabenschluessel: Die Spalte
                // in der Ablage ist VARCHAR(32), und ein laengerer Name liefe erst beim
                // Aufstieg auf - als Datenbankfehler mitten im Spiel.
                throw new IllegalArgumentException("Der Rang \"" + schluessel
                        + "\" ist laenger als " + HOECHSTE_SCHLUESSELLAENGE
                        + " Zeichen - so lang ist die Spalte in der Ablage.");
            }

            Map<Bedingungsart, Long> gepruefteBedingungen = new EnumMap<>(Bedingungsart.class);
            if (bedingungen != null) {
                for (var zeile : bedingungen.entrySet()) {
                    if (zeile.getKey() == null) {
                        throw new IllegalArgumentException("Eine Bedingung ohne Art in \""
                                + schluessel + "\"");
                    }
                    long wert = zeile.getValue() == null ? 0L : zeile.getValue();
                    if (wert < 1L || wert > zeile.getKey().hoechsterWert()) {
                        // Eine 0 ist keine Bedingung, sondern eine Zeile, die nie wirkt -
                        // und sie sieht im Betrieb genauso aus wie eine erfuellte. Wer
                        // keine Bedingung will, laesst die Zeile weg.
                        throw new IllegalArgumentException("Die Bedingung "
                                + zeile.getKey().schluessel() + " von \"" + schluessel
                                + "\" ist " + wert + " und liegt nicht zwischen 1 und "
                                + zeile.getKey().hoechsterWert() + ".");
                    }
                    gepruefteBedingungen.put(zeile.getKey(), wert);
                }
            }
            bedingungen = Map.copyOf(gepruefteBedingungen);

            Map<String, Integer> gepruefteZahlen = new LinkedHashMap<>();
            if (zahlen != null) {
                for (var zeile : zahlen.entrySet()) {
                    String name = zeile.getKey() == null ? ""
                            : zeile.getKey().trim().toLowerCase(Locale.ROOT);
                    if (!BEKANNTE_ZAHLEN.contains(name)) {
                        // Ein frei getippter Name ergaebe ein Recht, das niemand liest -
                        // der Betreiber saehe eine Zeile in der Datei und im Spiel keine
                        // Wirkung. Was es gibt, steht in BEKANNTE_ZAHLEN, und dort kommt
                        // eine Grenze dazu, sobald sie einen Leser hat.
                        throw new IllegalArgumentException("Die Grenze \"" + name
                                + "\" in \"" + schluessel + "\" kennt das Programm"
                                + " nicht. Es gibt: " + String.join(", ", BEKANNTE_ZAHLEN)
                                + ".");
                    }
                    int wert = zeile.getValue() == null ? -1 : zeile.getValue();
                    if (wert < 0 || wert > HOECHSTE_ZAHL) {
                        throw new IllegalArgumentException("Die Grenze " + name + " von \""
                                + schluessel + "\" ist " + zeile.getValue()
                                + " und liegt nicht zwischen 0 und " + HOECHSTE_ZAHL
                                + ".");
                    }
                    gepruefteZahlen.put(name, wert);
                }
            }
            zahlen = Map.copyOf(gepruefteZahlen);

            List<String> gepruefteRechte = new ArrayList<>();
            Set<String> gesehen = new LinkedHashSet<>();
            if (rechte != null) {
                for (String recht : rechte) {
                    String name = recht == null ? ""
                            : recht.trim().toLowerCase(Locale.ROOT);
                    if (name.startsWith(VORSILBE)) {
                        // Mit Vorsilbe getippt waere das Recht freimarken.freimarken.x -
                        // und das gibt es nicht. Lieber eine Klage mit der Loesung darin
                        // als ein stillschweigendes Abschneiden: Wer die Vorsilbe
                        // schreibt, hat eine Vorstellung davon, wie das Recht heisst, und
                        // die soll berichtigt werden.
                        throw new IllegalArgumentException("Das Recht \"" + name
                                + "\" in \"" + schluessel + "\" traegt die Vorsilbe "
                                + VORSILBE + " schon - sie kommt vom Programm. Schreib "
                                + name.substring(VORSILBE.length()) + ".");
                    }
                    if (!name.matches(RECHTEMUSTER)) {
                        throw new IllegalArgumentException("Das Recht \"" + name
                                + "\" in \"" + schluessel + "\" taugt nicht: erlaubt sind"
                                + " Kleinbuchstaben, Ziffern und Bindestrich, getrennt"
                                + " durch Punkte.");
                    }
                    if (name.length() > HOECHSTE_RECHTELAENGE) {
                        throw new IllegalArgumentException("Das Recht \"" + name
                                + "\" in \"" + schluessel + "\" ist laenger als "
                                + HOECHSTE_RECHTELAENGE + " Zeichen.");
                    }
                    if (gesehen.add(name)) {
                        gepruefteRechte.add(name);
                    }
                }
            }
            rechte = List.copyOf(gepruefteRechte);
        }

        /** Ob diese Stufe jedem zusteht - der unterste Rang. */
        public boolean ohneBedingung() {
            return bedingungen.isEmpty();
        }

        /** Die Grenze dieses Ranges, falls er sie setzt. */
        public Optional<Integer> zahl(String name) {
            if (name == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(zahlen.get(name.trim().toLowerCase(Locale.ROOT)));
        }
    }

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "raenge.an";

    /** Der Schluessel der Leiter. */
    public static final String PFAD_LEITER = "raenge.leiter";

    /** Der Unterschluessel der Bedingungen, je Stufe. */
    public static final String SCHLUESSEL_BEDINGUNGEN = "bedingungen";

    /** Der Unterschluessel der Grenzen, je Stufe. */
    public static final String SCHLUESSEL_ZAHLEN = "zahlen";

    /** Der Unterschluessel der Rechte, je Stufe. */
    public static final String SCHLUESSEL_RECHTE = "rechte";

    /** Die Vorsilbe aller Rechte dieses Plugins - sie kommt vom Programm. */
    public static final String VORSILBE = "freimarken.";

    /** Was ein Rangschluessel sein darf - dieselbe Form wie Beruf und Aufgabe. */
    public static final String MUSTER = "[a-z][a-z0-9_-]*";

    /** Was ein Rechtename sein darf - Punkte trennen die Stufen. */
    public static final String RECHTEMUSTER = "[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*";

    /** So lang darf ein Rangschluessel sein - so lang ist die Spalte in der Ablage. */
    public static final int HOECHSTE_SCHLUESSELLAENGE = 32;

    /** So lang darf ein Rechtename sein. */
    public static final int HOECHSTE_RECHTELAENGE = 48;

    /**
     * Die hoechste Zahl, die eine Grenze haben darf.
     *
     * <p>Zehntausend Parzellen sind kein Rang, sondern ein Vertipper. Die Schranke ist
     * absichtlich weit: Sie soll die zusaetzliche Null fangen und nicht dem Betreiber
     * vorschreiben, wie gross seine Welt ist.
     */
    public static final int HOECHSTE_ZAHL = 10_000;

    /** Hoechstens so viele Stufen - mehr ist keine Leiter mehr, sondern eine Liste. */
    public static final int HOECHSTE_STUFEN = 16;

    /**
     * Die Grenzen, die es gibt - und zwar die, die HEUTE einen Leser haben.
     *
     * <p>Jeder Name hier wird im Plugin zu {@code freimarken.<name>.<zahl>}, und genau
     * diese Vorsilben liest {@code Rangzahl} an den vier Grenzen (Parzellen,
     * Gruppenfelder, Meerinseln, Wolkeninseln). Ein Name ohne Leser waere ein Recht, das
     * vergeben wird und nichts tut.
     *
     * <p><b>{@code freistaedte} kam mit Teil H</b> [20.11.2026]: Der Riegel dort nahm
     * bis dahin nur die Zahl aus der {@code config.yml}, obwohl der Satz dem Spieler
     * schon sagte "so viele, wie dein Rang erlaubt" - eine Zusage ohne Bau. Heimatpunkte
     * und eigene Warps (Plan 9) gibt es noch gar nicht; sie kommen mit ihrer Mechanik und
     * nicht vorher, denn ein Recht ohne Leser ist genau das, was dieser Absatz verhindern
     * soll.
     */
    public static final Set<String> BEKANNTE_ZAHLEN =
            Set.of("parzellen", "gruppe", "inseln", "wolken", "freistaedte");

    /**
     * Die ausgelieferte Leiter - drei Stufen nach Plan 9.
     *
     * <p>Die Bedingungen sind TAETIGE Zeit und Taten, nicht blosse Anwesenheit
     * (Entscheidung vom 18.11.2026). Die Stundenzahlen des Plans (5 und 40) bleiben als
     * Groesse stehen, meinen aber jetzt gemessene Taetigkeit - damit sind sie strenger
     * als dort und nicht schwaecher.
     *
     * <p>Die Zahlen sind die der Rangtafel in Plan 9: Parzellen 1/2/4, Zusammenlegen
     * 1/2/4. Meerinseln, Wolkeninseln und Freistaedte bleiben auf allen Stufen bei 1,
     * weil der Plan dort keine Staffel nennt - der Betreiber kann sie in der Datei
     * aufziehen, ohne dass eine Zeile Programm zu aendern waere.
     *
     * <p><b>Jede Stufe nennt JEDE Grenze</b>, auch wo sie nichts hebt [Teil H]: Eine
     * Stufe, die eine Grenze nicht nennt, gibt dort 0, und dann entscheidet allein die
     * Datei. Das ist erlaubt und manchmal gewollt - aber es sieht einer vergessenen Zeile
     * zum Verwechseln aehnlich. Ausgeliefert steht darum ueberall eine Zahl.
     */
    public static final List<Stufe> VORGABE_LEITER = List.of(
            new Stufe("reisender", Map.of(),
                    Map.of("parzellen", 1, "gruppe", 1, "inseln", 1, "wolken", 1,
                            "freistaedte", 1),
                    List.of()),
            new Stufe("siedler",
                    Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L,
                            Bedingungsart.PARZELLEN, 1L),
                    Map.of("parzellen", 2, "gruppe", 2, "inseln", 1, "wolken", 1,
                            "freistaedte", 1),
                    List.of("laden.mieten")),
            new Stufe("buerger",
                    Map.of(Bedingungsart.TAETIGE_STUNDEN, 40L,
                            Bedingungsart.PARZELLEN, 1L,
                            Bedingungsart.BERUF, 1L),
                    Map.of("parzellen", 4, "gruppe", 4, "inseln", 1, "wolken", 1,
                            "freistaedte", 1),
                    List.of("freistadt.gruenden", "beruf.spezialisierung")));

    /** Die Werte, die das Programm mitbringt. */
    public static final Rangwerte VORGABE = new Rangwerte(true, VORGABE_LEITER);

    public Rangwerte {
        if (leiter == null) {
            throw new IllegalArgumentException("Ohne Leiter gibt es keine Rangwerte."
                    + " Eine LEERE Leiter ist erlaubt und heisst: keine Raenge.");
        }
        if (leiter.size() > HOECHSTE_STUFEN) {
            throw new IllegalArgumentException("Es sind " + leiter.size()
                    + " Rangstufen, hoechstens " + HOECHSTE_STUFEN + " sind erlaubt.");
        }
        Set<String> gesehen = new LinkedHashSet<>();
        List<Stufe> abgelegt = new ArrayList<>();
        for (Stufe stufe : leiter) {
            if (stufe == null) {
                throw new IllegalArgumentException("Eine leere Zeile in der Rangleiter");
            }
            if (!gesehen.add(stufe.schluessel())) {
                // Zwei Stufen mit demselben Schluessel waeren EINE Zeile in der Ablage:
                // Der Aufstieg auf die zweite waere nicht von der ersten zu unterscheiden.
                throw new IllegalArgumentException("Der Rang \"" + stufe.schluessel()
                        + "\" steht zweimal in der Leiter - in der Ablage waere das EINE"
                        + " Zeile.");
            }
            abgelegt.add(stufe);
        }
        if (!abgelegt.isEmpty() && !abgelegt.get(0).ohneBedingung()) {
            // Der unterste Rang ist der, den jeder beim ersten Beitritt hat. Haette er
            // eine Bedingung, stuende ein neuer Spieler auf KEINER Stufe - und damit
            // ohne jedes Recht, das die Leiter vergibt.
            throw new IllegalArgumentException("Die unterste Stufe \""
                    + abgelegt.get(0).schluessel() + "\" hat Bedingungen. Sie ist der"
                    + " Rang, den jeder beim ersten Beitritt hat, und muss ohne"
                    + " Bedingung zustehen.");
        }
        leiter = List.copyOf(abgelegt);
    }

    /** Ob ueberhaupt Raenge vergeben werden. */
    public boolean greift() {
        return an && !leiter.isEmpty();
    }

    /** Die Stufe zu diesem Schluessel - kleingeschrieben gefragt wie abgelegt. */
    public Optional<Stufe> stufe(String schluessel) {
        if (schluessel == null) {
            return Optional.empty();
        }
        String klein = schluessel.trim().toLowerCase(Locale.ROOT);
        for (Stufe stufe : leiter) {
            if (stufe.schluessel().equals(klein)) {
                return Optional.of(stufe);
            }
        }
        return Optional.empty();
    }

    /** Die Nummer dieser Stufe in der Leiter, von 0 an - oder -1. */
    public int nummer(String schluessel) {
        if (schluessel == null) {
            return -1;
        }
        String klein = schluessel.trim().toLowerCase(Locale.ROOT);
        for (int stelle = 0; stelle < leiter.size(); stelle++) {
            if (leiter.get(stelle).schluessel().equals(klein)) {
                return stelle;
            }
        }
        return -1;
    }

    /** Der Rang, den jeder beim ersten Beitritt hat. */
    public Optional<Stufe> unterste() {
        return leiter.isEmpty() ? Optional.empty() : Optional.of(leiter.get(0));
    }

    /** Die Stufe ueber dieser - leer, wenn es keine gibt. */
    public Optional<Stufe> naechste(String schluessel) {
        int stelle = nummer(schluessel);
        if (stelle < 0 || stelle + 1 >= leiter.size()) {
            return Optional.empty();
        }
        return Optional.of(leiter.get(stelle + 1));
    }

    /**
     * Was ein erreichter Rang insgesamt gibt - Zahlen und Rechte in einem Satz.
     *
     * @param zahlen je Grenze die hoechste Zahl, die diese Stufe oder eine darunter gibt
     * @param rechte alle Rechte von unten bis hierher, in der Reihenfolge der Leiter und
     *               ohne Doppelte, OHNE die Vorsilbe {@code freimarken.}
     */
    public record Buendel(Map<String, Integer> zahlen, List<String> rechte) {

        public Buendel {
            zahlen = Map.copyOf(zahlen == null ? Map.of() : zahlen);
            rechte = List.copyOf(rechte == null ? List.of() : rechte);
        }

        /** Das Buendel eines Spielers, fuer den die Leiter nichts hergibt. */
        public static Buendel leer() {
            return new Buendel(Map.of(), List.of());
        }

        /** Ob es ueberhaupt etwas zu vergeben gibt. */
        public boolean leerIst() {
            return zahlen.isEmpty() && rechte.isEmpty();
        }
    }

    /**
     * Alles, was dieser Rang gibt - KUMULATIV von der untersten Stufe bis hierher.
     *
     * <p><b>Warum kumulativ und nicht nur die erreichte Stufe</b> [19.11.2026]: Die
     * ausgelieferte Leiter beweist den Fall selbst. {@code siedler} gibt
     * {@code laden.mieten}, {@code buerger} nennt es nicht noch einmal - er nennt, was
     * NEU dazukommt. Zaehlte nur die erreichte Stufe, naehme der Aufstieg zum Buerger dem
     * Spieler seinen Laden weg. Ein Aufstieg, der etwas wegnimmt, ist genau der Abstieg,
     * den {@link Rangregel#aufstieg} vorn verbietet - er kaeme nur durch die Hintertuer
     * herein, und niemand meldete ihn als Fehler, weil die Absage im Spiel dieselbe ist
     * wie bei einem Spieler, der das Recht nie hatte.
     *
     * <p><b>Bei den Zahlen gilt die hoechste</b>, nicht die der erreichten Stufe. Das ist
     * dieselbe Regel, die {@code Rangzahl} ohnehin anwendet: Es sind Rechte, und die
     * hoechste Zahl gewinnt. Eine Stufe, die weniger gibt als die darunter, hebt damit
     * nichts - sie nimmt aber auch nichts weg. Dass das fast immer ein Vertipper ist,
     * meldet {@link #ungereimtheiten}.
     *
     * <p>Ein unbekannter Schluessel gibt ein LEERES Buendel und wirft nicht: Er kommt aus
     * der Ablage, und dort kann eine Stufe stehen, die aus der Datei genommen wurde. Wer
     * den Rueckfall auf die unterste Stufe will, fragt {@link Rangstand#stufeIn} - das ist
     * die eine Stelle dafuer.
     */
    public Buendel buendel(String schluessel) {
        int bis = nummer(schluessel);
        if (bis < 0 || !greift()) {
            return Buendel.leer();
        }
        // TreeMap, nicht HashMap: Map.copyOf sagt keine Reihenfolge zu, und sie ist je
        // Lauf der Maschine eine andere. Ohne feste Ordnung stuenden dieselben Rechte mal
        // so und mal so herum, und jede Probe darueber waere mal gruen und mal rot.
        // Derselbe Grund wie bei den Ungereimtheiten.
        Map<String, Integer> hoechste = new TreeMap<>();
        List<String> gesammelt = new ArrayList<>();
        Set<String> gesehen = new LinkedHashSet<>();
        for (int stelle = 0; stelle <= bis; stelle++) {
            Stufe stufe = leiter.get(stelle);
            for (var zeile : stufe.zahlen().entrySet()) {
                hoechste.merge(zeile.getKey(), zeile.getValue(), Math::max);
            }
            for (String recht : stufe.rechte()) {
                if (gesehen.add(recht)) {
                    gesammelt.add(recht);
                }
            }
        }
        return new Buendel(hoechste, gesammelt);
    }

    /** Alle Schluessel von unten nach oben - fuer Anzeige und Ergaenzung. */
    public List<String> schluessel() {
        List<String> namen = new ArrayList<>();
        for (Stufe stufe : leiter) {
            namen.add(stufe.schluessel());
        }
        return List.copyOf(namen);
    }

    /**
     * Was an dieser Leiter unstimmig ist - Saetze fuer den Startbericht, keine Fehler.
     *
     * <p><b>Warum eine Liste und kein Wurf:</b> Eine Leiter, die oben weniger gibt als
     * unten, ist fast immer ein Vertipper - aber eben nur fast immer, und ein Wurf naehme
     * dem Betreiber die Entscheidung. Gemeldet wird sie trotzdem, denn im Spiel sieht
     * niemand den Unterschied zwischen "der Rang gibt weniger" und "der Rang wirkt
     * nicht": {@link #buendel} nimmt die hoechste Zahl von der untersten Stufe bis zur
     * erreichten, und {@code Rangzahl} nimmt noch einmal die hoechste aus Recht UND
     * Datei. Ein Rueckschritt faellt darum an zwei Stellen durch und nirgends auf.
     */
    public List<String> ungereimtheiten() {
        List<String> saetze = new ArrayList<>();
        for (int stelle = 1; stelle < leiter.size(); stelle++) {
            Stufe unten = leiter.get(stelle - 1);
            Stufe oben = leiter.get(stelle);
            // SORTIERT, und das ist keine Schoenheit: Map.copyOf sagt keine Reihenfolge
            // zu, und sie ist je Lauf der Maschine eine andere. Ohne Sortierung stuenden
            // dieselben zwei Saetze mal so und mal so herum - und jede Probe darauf waere
            // mal gruen und mal rot, ohne dass sich etwas geaendert haette.
            for (String name : new java.util.TreeSet<>(unten.zahlen().keySet())) {
                Integer untenWert = unten.zahlen().get(name);
                Integer obenWert = oben.zahlen().get(name);
                if (obenWert != null && obenWert < untenWert) {
                    saetze.add("Der Rang " + oben.schluessel() + " gibt "
                            + name + ": " + obenWert + ", der Rang darunter ("
                            + unten.schluessel() + ") gibt " + untenWert
                            + " - die obere Zahl ist wirkungslos: Ein Rang gibt auch,"
                            + " was die Stufen darunter geben, und dort gilt die"
                            + " hoechste.");
                }
            }
            // Die Bedingungen in der Reihenfolge der Aufzaehlung, aus demselben Grund.
            for (Bedingungsart art : Bedingungsart.values()) {
                Long untenWert = unten.bedingungen().get(art);
                Long obenWert = oben.bedingungen().get(art);
                if (untenWert != null && obenWert != null && obenWert < untenWert) {
                    saetze.add("Der Rang " + oben.schluessel() + " verlangt "
                            + art.schluessel() + ": " + obenWert
                            + ", der Rang darunter (" + unten.schluessel() + ") verlangt "
                            + untenWert + " - die obere Bedingung ist damit"
                            + " wirkungslos.");
                }
            }
        }
        return List.copyOf(saetze);
    }

    /** Dieselben Werte mit einer anderen Leiter. */
    public Rangwerte mitLeiter(List<Stufe> andere) {
        return new Rangwerte(an, andere);
    }

    /** Dieselben Werte mit einem anderen Schalter. */
    public Rangwerte mitAn(boolean anders) {
        return new Rangwerte(anders, leiter);
    }
}
