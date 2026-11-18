package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Die Herausforderungen von Wolkenwerder - und damit die INSEL-STUFE (Plan 4.1).
 *
 * <p><b>Diese Liste ist der Fortschritt dieser Welt.</b> Die Stufe einer Insel ist die
 * Zahl ihrer erfuellten Herausforderungen, und an ihr haengt die Ausbeute des
 * Kobbelsteingenerators ({@link Erzwerte}): Eisen ab Stufe 1, Diamant ab 5, Netherit ab 8.
 * Eine Welt ohne Aufgaben hat damit keinen Fortschritt - der Generator bliebe fuer immer
 * bei Kohle und Kupfer.
 *
 * <p><b>Eine REIHENFOLGE und keine Karte</b>, anders als bei der Erztabelle: Die Liste wird
 * angezeigt, und zwar so, wie der Betreiber sie getippt hat. Von leicht nach schwer ist
 * eine Aussage ueber das Spiel, und die soll er treffen koennen.
 *
 * <p><b>Keine Voraussetzung zwischen den Aufgaben.</b> Wer Diamanten hat, darf die
 * Diamantaufgabe abgeben, auch als erste - und das ist kein Loch, sondern die Folge aus
 * der Welt selbst: Diamant kommt nur aus dem Generator, und der gibt ihn erst ab Stufe 5.
 * Die Erztabelle IST die Reihenfolge; eine zweite daneben waere eine, die ihr eines Tages
 * widerspricht.
 *
 * <p><b>Die Namen der Aufgaben stehen NICHT hier</b>, sondern in der Sprachdatei - dieselbe
 * Teilung wie bei den Berufen: Was es gibt, steht in der {@code config.yml}, wie es heisst,
 * in {@code sprache.yml}. Ein Name hier waere ein Spieltext im falschen Haus, und er waere
 * nicht uebersetzbar.
 *
 * @param an    ob es ueberhaupt Herausforderungen gibt
 * @param liste die Aufgaben in der Reihenfolge der Datei
 */
public record Aufgabenwerte(boolean an, List<Aufgabenwerte.Aufgabe> liste) {

    /**
     * EINE Herausforderung.
     *
     * @param schluessel  der Name in der Datei, kleingeschrieben - er steht in der
     *                    Ablage, in der Sprachdatei und im Befehl
     * @param verlangt    was abzugeben ist, je Gegenstand eine Anzahl
     * @param belohnung   was es dafuer gibt, in Mark; 0 heisst: nur die Stufe
     */
    public record Aufgabe(String schluessel, Map<String, Integer> verlangt,
                          long belohnung) {

        public Aufgabe {
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Eine Herausforderung ohne Schluessel"
                        + " gibt es nicht - an ihm haengt die Zeile in der Ablage");
            }
            schluessel = schluessel.trim().toLowerCase(Locale.ROOT);
            if (!schluessel.matches(MUSTER)) {
                // Derselbe Riegel wie beim Berufsschluessel, und aus demselben Grund: Er
                // steht in der Ablage, in der Sprachdatei UND im getippten Befehl - drei
                // Orte, die alle dasselbe Zeichen lesen muessen. Ein Punkt darin waere in
                // der Sprachdatei eine Schachtelung, ein Leerzeichen im Befehl ein
                // zweites Wort.
                throw new IllegalArgumentException("Der Schluessel \"" + schluessel
                        + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                        + " Unterstrich und Bindestrich, und beginnen muss er mit einem"
                        + " Buchstaben.");
            }
            if (schluessel.length() > HOECHSTE_SCHLUESSELLAENGE) {
                // Die Spalte wolke_herausforderung.schluessel ist VARCHAR(32). Ein
                // laengerer Schluessel liefe erst beim Abgeben auf, und zwar in der
                // Datenbank - also hier. Gemessen wird am FERTIGEN Schluessel: Was
                // getrimmt hineinpasst, soll nicht an seinen Leerzeichen scheitern.
                throw new IllegalArgumentException("Der Schluessel \"" + schluessel
                        + "\" ist laenger als " + HOECHSTE_SCHLUESSELLAENGE
                        + " Zeichen - so lang ist die Spalte in der Ablage.");
            }
            if (verlangt == null || verlangt.isEmpty()) {
                throw new IllegalArgumentException("Die Herausforderung \"" + schluessel
                        + "\" verlangt nichts - das waere Geld fuer das Tippen eines"
                        + " Befehls.");
            }
            if (belohnung < 0L || belohnung > HOECHSTE_BELOHNUNG) {
                throw new IllegalArgumentException("Die Belohnung von \"" + schluessel
                        + "\" ist " + belohnung + " und liegt nicht zwischen 0 und "
                        + HOECHSTE_BELOHNUNG + ".");
            }
            Map<String, Integer> abgelegt = new LinkedHashMap<>();
            for (var zeile : verlangt.entrySet()) {
                if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                    throw new IllegalArgumentException("Ein Gegenstand ohne Namen in \""
                            + schluessel + "\"");
                }
                if (zeile.getValue() == null || zeile.getValue() < 1
                        || zeile.getValue() > HOECHSTE_ANZAHL) {
                    throw new IllegalArgumentException("Die Anzahl von "
                            + zeile.getKey() + " in \"" + schluessel + "\" ist "
                            + zeile.getValue() + " und liegt nicht zwischen 1 und "
                            + HOECHSTE_ANZAHL + ".");
                }
                abgelegt.put(zeile.getKey().trim().toLowerCase(Locale.ROOT),
                        zeile.getValue());
            }
            verlangt = Map.copyOf(abgelegt);
        }
    }

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "wolken.aufgaben.an";

    /** Der Schluessel der Liste. */
    public static final String PFAD_LISTE = "wolken.aufgaben.liste";

    /** Der Unterschluessel der verlangten Gegenstaende, je Aufgabe. */
    public static final String SCHLUESSEL_VERLANGT = "verlangt";

    /** Der Unterschluessel der Belohnung, je Aufgabe. */
    public static final String SCHLUESSEL_BELOHNUNG = "belohnung";

    /**
     * Was ein Schluessel sein darf - dieselbe Form wie beim Beruf, nur mit Unterstrich.
     *
     * <p>Der Unterstrich, weil die Gegenstaende daneben ihn tragen ({@code iron_ingot})
     * und ein Betreiber ihn darum tippt; der Bindestrich bleibt erlaubt, damit beide
     * Schreibweisen gehen. Verboten ist der PUNKT - er waere in der Sprachdatei eine
     * Schachtelung - und das Leerzeichen, das im Befehl ein zweites Wort waere.
     */
    public static final String MUSTER = "[a-z][a-z0-9_-]*";

    /** Ob dieser Schluessel in Ablage, Sprachdatei und Befehl taugt. */
    public static boolean brauchbarerSchluessel(String roh) {
        return roh != null && !roh.isBlank()
                && roh.trim().toLowerCase(Locale.ROOT).matches(MUSTER)
                && roh.trim().length() <= HOECHSTE_SCHLUESSELLAENGE;
    }

    /**
     * So lang darf ein Schluessel sein - so lang ist die Spalte in der Ablage.
     *
     * <p>Die Zahl steht hier und nicht nur im Schema, und das ist dieselbe Naht wie bei
     * den Weltnamen: Ein zu langer Schluessel liefe sonst erst beim Abgeben auf, und zwar
     * als Datenbankfehler mitten im Spiel.
     */
    public static final int HOECHSTE_SCHLUESSELLAENGE = 32;

    /** Hoechstens so viele Stueck eines Gegenstandes - eine Doppeltruhe voll. */
    public static final int HOECHSTE_ANZAHL = 3_456;

    /** Die hoechste Belohnung - dieselbe Obergrenze wie bei jedem Geldwert. */
    public static final long HOECHSTE_BELOHNUNG = Freistadtwerte.HOECHSTER_BETRAG;

    /**
     * Hoechstens so viele Aufgaben.
     *
     * <p>Dieselbe Zahl wie {@link Erzwerte#HOECHSTE_STUFE}: Die Stufe IST die Zahl der
     * erfuellten Aufgaben, und eine Zeile oberhalb der hoechsten Stufe waere eine, deren
     * Erfuellung die Ausbeute nicht mehr aendern kann.
     */
    public static final int HOECHSTE_ZAHL = Erzwerte.HOECHSTE_STUFE;

    /**
     * Die ausgelieferten zehn Herausforderungen - der Fortschritt dieser Welt.
     *
     * <p>Von leicht nach schwer, und jede ist mit dem erreichbar, was die Stufe davor
     * aufschliesst: Kobbelstein von Anfang an, Kohle und Kupfer ab Stufe 0, Eisen ab 1,
     * Gold und Rotstein ab 2, Diamant ab 5. ZEHN und nicht neun, damit auch die letzte
     * Erzzeile ({@code ancient_debris} ab Stufe 8) im Spiel erreicht wird.
     *
     * <p>Nichts davon verlangt etwas, das es nur ausserhalb von Wolkenwerder gibt - keine
     * Beute aus einem Dorf, keinen Nether, keinen Handel. Eine Insel in der Leere ist
     * sonst eine Aufgabe, die nicht zu loesen ist.
     */
    public static final List<Aufgabe> VORGABE_LISTE = List.of(
            new Aufgabe("erster-stein", Map.of("cobblestone", 64), 50L),
            new Aufgabe("holzfaeller", Map.of("oak_log", 64), 100L),
            new Aufgabe("kohlebrenner", Map.of("coal", 16, "furnace", 1), 150L),
            new Aufgabe("baecker", Map.of("bread", 16), 200L),
            new Aufgabe("steinmetz", Map.of("stone_bricks", 64), 250L),
            new Aufgabe("erstes-eisen", Map.of("iron_ingot", 16), 400L),
            new Aufgabe("kupferschmied", Map.of("copper_ingot", 32), 500L),
            new Aufgabe("goldschmied", Map.of("gold_ingot", 16), 800L),
            new Aufgabe("rotsteinkundiger", Map.of("redstone", 32), 1_000L),
            new Aufgabe("diamantschleifer", Map.of("diamond", 4), 2_000L));

    /** Die Werte, die das Programm mitbringt. */
    public static final Aufgabenwerte VORGABE = new Aufgabenwerte(true, VORGABE_LISTE);

    public Aufgabenwerte {
        if (liste == null) {
            throw new IllegalArgumentException("Ohne Liste gibt es keine Aufgabenwerte."
                    + " Eine LEERE Liste ist erlaubt und heisst: keine Herausforderungen"
                    + " und damit keine Stufe ueber 0.");
        }
        if (liste.size() > HOECHSTE_ZAHL) {
            throw new IllegalArgumentException("Es sind " + liste.size()
                    + " Herausforderungen, hoechstens " + HOECHSTE_ZAHL + " sind"
                    + " erlaubt - mehr als die hoechste Stufe kann die Ausbeute nicht"
                    + " mehr aendern.");
        }
        Set<String> gesehen = new LinkedHashSet<>();
        List<Aufgabe> abgelegt = new ArrayList<>();
        for (Aufgabe aufgabe : liste) {
            if (aufgabe == null) {
                throw new IllegalArgumentException("Eine leere Zeile in der"
                        + " Aufgabenliste");
            }
            if (!gesehen.add(aufgabe.schluessel())) {
                // Zwei Zeilen mit demselben Schluessel waeren EINE Zeile in der Ablage:
                // Wer die erste erfuellt, hat die zweite mit erfuellt - und die
                // Belohnung der zweiten bekaeme er nie.
                throw new IllegalArgumentException("Die Herausforderung \""
                        + aufgabe.schluessel() + "\" steht zweimal in der Liste - in der"
                        + " Ablage waere das EINE Zeile.");
            }
            abgelegt.add(aufgabe);
        }
        liste = List.copyOf(abgelegt);
    }

    /** Ob es ueberhaupt etwas abzugeben gibt. */
    public boolean greift() {
        return an && !liste.isEmpty();
    }

    /** Die Aufgabe zu diesem Schluessel - kleingeschrieben gefragt wie abgelegt. */
    public Optional<Aufgabe> aufgabe(String schluessel) {
        if (schluessel == null) {
            return Optional.empty();
        }
        String klein = schluessel.trim().toLowerCase(Locale.ROOT);
        for (Aufgabe aufgabe : liste) {
            if (aufgabe.schluessel().equals(klein)) {
                return Optional.of(aufgabe);
            }
        }
        return Optional.empty();
    }

    /** Alle Schluessel in der Reihenfolge der Datei - fuer Anzeige und Ergaenzung. */
    public List<String> schluessel() {
        List<String> namen = new ArrayList<>();
        for (Aufgabe aufgabe : liste) {
            namen.add(aufgabe.schluessel());
        }
        return List.copyOf(namen);
    }

    /** Dieselben Werte mit einem anderen Schalter. */
    public Aufgabenwerte mitAn(boolean anders) {
        return new Aufgabenwerte(anders, liste);
    }

    /** Dieselben Werte mit einer anderen Liste. */
    public Aufgabenwerte mitListe(List<Aufgabe> andere) {
        return new Aufgabenwerte(an, andere);
    }
}
