package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Der Katalog des Muenzhauses - was es fuer Goldmark gibt (Plan 2.1, 8.1).
 *
 * <p><b>Verkauft werden GEGENSTAENDE</b>, und das ist die erste Entscheidung dieses
 * Bauschritts [20.11.2026]. Plan 8.3 nennt als Katalogware Kosmetik, Haustiere, Partikel,
 * Rangfarben, zusaetzliche Heimatpunkte und die Namensanzeige - und von all dem hat heute
 * nichts eine Mechanik. Ein Posten darauf waere echte Goldmark fuer ein Recht, das niemand
 * liest; {@code Rangwerte.BEKANNTE_ZAHLEN} sagt dasselbe ueber die Heimatpunkte ("sie
 * kommen mit ihrer Mechanik"). Ein Gegenstand dagegen wirkt am Tag des Kaufs, braucht
 * keine Rechteverwaltung, kein PermissionAttachment (das ueberlebt keinen Neustart) und
 * keine eigene Ablage - er liegt danach im Rucksack, und Minecraft speichert Inventare.
 *
 * <p><b>Was das kostet, steht in der Datei und nicht hier:</b> Preise, Mengen und
 * Bedingungen sind Einstellungen, der ausgelieferte Katalog ist nur eine Vorgabe. Die
 * NAMEN der Posten stehen in der Sprachdatei - dieselbe Trennung wie bei den Berufen und
 * den Herausforderungen: Was es gibt, steht in der {@code config.yml}, wie es heisst, in
 * der {@code sprache.yml}.
 *
 * <p><b>Die Reihenfolge der Datei ist eine Aussage</b> und wird gehalten, darum eine
 * LISTE und keine Karte: So stehen die Posten auch auf der Tafel im Spiel, und ein
 * Betreiber kann das Billige nach vorne nehmen.
 *
 * <p><b>Was ein gekaufter Gegenstand NICHT ist:</b> ersetzbar. Er gehoert dem Spieler wie
 * jeder andere Gegenstand und kann verloren gehen - in Lava, beim Sterben, beim
 * Verschenken. Dafuer ist jeder Posten beliebig oft zu kaufen; eine Grenze je Spieler
 * braeuchte eine Ablage, und zwei Huete sind nun einmal zwei Huete.
 *
 * @param an      ob es das Muenzhaus ueberhaupt gibt. Aus heisst: Der Kontostand und der
 *                Verlauf bleiben zu sehen - gekauft wird nichts
 * @param katalog die Posten in der Reihenfolge der Datei
 */
public record Muenzwerte(boolean an, List<Muenzwerte.Posten> katalog) {

    /**
     * EIN Posten des Katalogs.
     *
     * @param schluessel   der Name in der Datei, kleingeschrieben - er steht in der
     *                     Sprachdatei und im getippten Befehl
     * @param gegenstaende was der Kauf liefert, je Gegenstand eine Anzahl
     * @param preis        was er kostet, in GOLDMARK; 0 heisst gratis
     * @param bedingungen  was erfuellt sein muss - leer heisst: fuer jeden
     */
    public record Posten(String schluessel, Map<String, Integer> gegenstaende, long preis,
                         Map<Bedingungsart, Long> bedingungen) {

        public Posten {
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Ein Posten ohne Schluessel gibt es"
                        + " nicht - an ihm haengt sein Name und der Befehl");
            }
            schluessel = schluessel.trim().toLowerCase(Locale.ROOT);
            if (!schluessel.matches(MUSTER)) {
                // Derselbe Riegel wie beim Beruf und bei der Herausforderung: Der
                // Schluessel steht in der Sprachdatei UND im getippten Befehl. Ein Punkt
                // darin waere in der Sprachdatei eine Schachtelung, ein Leerzeichen im
                // Befehl ein zweites Wort.
                throw new IllegalArgumentException("Der Schluessel \"" + schluessel
                        + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                        + " Unterstrich und Bindestrich, und beginnen muss er mit einem"
                        + " Buchstaben.");
            }
            if (schluessel.length() > HOECHSTE_SCHLUESSELLAENGE) {
                throw new IllegalArgumentException("Der Schluessel \"" + schluessel
                        + "\" ist laenger als " + HOECHSTE_SCHLUESSELLAENGE
                        + " Zeichen - dieselbe Grenze wie bei Rang, Beruf und"
                        + " Herausforderung.");
            }
            if (gegenstaende == null || gegenstaende.isEmpty()) {
                // Der Spiegel von Aufgabenwerte: Dort waere eine Aufgabe ohne Verlangtes
                // "Geld fuer das Tippen eines Befehls". Hier ist es schlimmer - der Preis
                // wuerde abgebucht, geliefert kaeme nichts, und der Bescheid lautete
                // GEKAUFT.
                throw new IllegalArgumentException("Der Posten \"" + schluessel
                        + "\" liefert nichts - das waere Goldmark fuer nichts.");
            }
            if (!brauchbarerPreis(preis)) {
                throw new IllegalArgumentException("Der Preis von \"" + schluessel
                        + "\" ist " + preis + " und liegt nicht zwischen 0 und "
                        + HOECHSTER_PREIS + " Goldmark.");
            }
            Map<String, Integer> abgelegt = new LinkedHashMap<>();
            for (var zeile : gegenstaende.entrySet()) {
                if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                    throw new IllegalArgumentException("Ein Gegenstand ohne Namen in \""
                            + schluessel + "\"");
                }
                if (!brauchbareStueckzahl(zeile.getValue())) {
                    throw new IllegalArgumentException("Die Anzahl von " + zeile.getKey()
                            + " in \"" + schluessel + "\" ist " + zeile.getValue()
                            + " und liegt nicht zwischen 1 und " + HOECHSTE_STUECKZAHL
                            + ".");
                }
                abgelegt.put(zeile.getKey().trim().toLowerCase(Locale.ROOT),
                        zeile.getValue());
            }
            // LinkedHashMap und NICHT Map.copyOf, anders als bei der Herausforderung: Die
            // Gegenstaende stehen in EINER Zeile der Tafel, und Map.copyOf sagt keine
            // Reihenfolge zu. Die Zeile stuende dann bei jedem Lauf der Maschine anders
            // da, und eine Bildprobe darauf waere nicht zu halten.
            gegenstaende = Collections.unmodifiableMap(abgelegt);
            if (summe(abgelegt) > HOECHSTE_STUECKZAHL) {
                // DIE SUMME und nicht nur die Zeile - gemessen am 20.11.2026 in der
                // Pruefrunde zu Teil G: Zwei Zeilen von je 2.000 Stueck bestehen jede
                // fuer sich und passen zusammen in keinen Rucksack. Der Posten stuende
                // dann mit Preis in der Tafel und antwortete jedem - auch bei voellig
                // leerem Rucksack - "dir fehlt Platz fuer 1.696 Stueck".
                throw new IllegalArgumentException("Der Posten \"" + schluessel
                        + "\" liefert zusammen " + summe(abgelegt) + " Stueck - mehr als"
                        + " die " + HOECHSTE_STUECKZAHL + ", die in einen Rucksack"
                        + " passen.");
            }
            Map<Bedingungsart, Long> geprueft = new EnumMap<>(Bedingungsart.class);
            if (bedingungen != null) {
                for (var zeile : bedingungen.entrySet()) {
                    if (zeile.getKey() == null) {
                        throw new IllegalArgumentException("Eine Bedingung ohne Art in \""
                                + schluessel + "\"");
                    }
                    long wert = zeile.getValue() == null ? 0L : zeile.getValue();
                    if (wert < 1L || wert > zeile.getKey().hoechsterWert()) {
                        // Die untere Grenze ist 1 und nicht 0, wie bei der Rangstufe:
                        // Eine 0 ist keine Bedingung, sondern eine Zeile, die nie wirkt.
                        // Wer keine Bedingung will, laesst die Zeile weg.
                        throw new IllegalArgumentException("Die Bedingung "
                                + zeile.getKey().schluessel() + " von \"" + schluessel
                                + "\" ist " + wert + " und liegt nicht zwischen 1 und "
                                + zeile.getKey().hoechsterWert() + ".");
                    }
                    geprueft.put(zeile.getKey(), wert);
                }
            }
            bedingungen = Map.copyOf(geprueft);
        }

        /** Ob dieser Posten jedem offensteht. */
        public boolean ohneBedingung() {
            return bedingungen.isEmpty();
        }

        /** Wie viele Stueck dieser Posten insgesamt liefert. */
        public int stueckzahl() {
            return summe(gegenstaende);
        }

        /**
         * Die Summe einer Gegenstandskarte.
         *
         * <p>Eine eigene Methode, weil sie an ZWEI Stellen gebraucht wird: im Riegel des
         * Konstruktors und hier. Vorher rechnete nur {@link #stueckzahl} die Summe, und
         * jene rief im ganzen Hauptcode niemand - eine Zahl, die nirgends ankommt, ist
         * keine Grenze.
         */
        private static int summe(Map<String, Integer> gegenstaende) {
            int summe = 0;
            for (int stueck : gegenstaende.values()) {
                summe += stueck;
            }
            return summe;
        }
    }

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "muenzhaus.an";

    /** Der Schluessel des Katalogs. */
    public static final String PFAD_KATALOG = "muenzhaus.katalog";

    /** Der Unterschluessel der gelieferten Gegenstaende, je Posten. */
    public static final String SCHLUESSEL_GEGENSTAENDE = "gegenstaende";

    /** Der Unterschluessel des Preises, je Posten. */
    public static final String SCHLUESSEL_PREIS = "preis";

    /** Der Unterschluessel der Bedingungen, je Posten. */
    public static final String SCHLUESSEL_BEDINGUNGEN = "bedingungen";

    /** Was ein Postenschluessel sein darf - wie bei Rang, Beruf und Herausforderung. */
    static final String MUSTER = "[a-z][a-z0-9_-]*";

    /**
     * Wie lang ein Postenschluessel sein darf.
     *
     * <p>Dieselbe Zahl wie bei Rang, Beruf und Herausforderung, aber aus einem anderen
     * Grund: Dort ist sie die Breite einer Datenbankspalte. Der Katalog hat keine Ablage -
     * gekauft wird ein Gegenstand, und der liegt danach im Rucksack. Hier ist die 32 die
     * Grenze des GETIPPTEN Wortes und der Zeile in der Sprachdatei, und sie ist dieselbe,
     * weil ein Betreiber eine Regel lernen soll und nicht vier.
     */
    public static final int HOECHSTE_SCHLUESSELLAENGE = 32;

    /**
     * Wie viele Lagerplaetze ein Spieler hat - die Zahl hinter
     * {@link #HOECHSTE_STUECKZAHL}.
     *
     * <p>Sie steht hier und nicht in der Paper-Haelfte, weil die Grenze hier faellt. Es
     * sind 36: die drei Reihen und die Schnellleiste, ohne Ruestung und zweite Hand -
     * dieselben Plaetze, die {@code Vorratshand} ueber {@code getStorageContents} zaehlt.
     */
    public static final int LAGERPLAETZE = 36;

    /**
     * Wie viele Stueck eine Zeile eines Postens liefern darf.
     *
     * <p><b>Eine eigene Zahl und nicht die 3.456 der Herausforderungen</b>, und der
     * Unterschied ist die Richtung: Jene ist eine Doppelkiste voll, aus der HERAUSgenommen
     * wird. Hier wird HINEINgelegt, und zwar in die Lagerplaetze eines Spielers - 36 Stueck
     * mal 64 Stueck Stapelgroesse. Mehr kann nie ankommen; ein Posten darueber waere auf
     * Dauer unkaufbar und stuende trotzdem mit Preis in der Tafel.
     *
     * <p><b>Geprueft wird die Zeile UND die Summe des Postens</b> [20.11.2026]: Zwei
     * Zeilen von je 2.000 Stueck bestehen jede fuer sich und sind zusammen genau der
     * Zustand, den dieser Absatz ausschliesst.
     *
     * <p>Was sie NICHT faengt, ist die Stapelgroesse: 40 Musikscheiben sind 40 Plaetze.
     * Das braucht Minecrafts Verzeichnis, also einen laufenden Server - und darum steht
     * es im Startbericht ({@code FreimarkenPlugin.nenneUnlieferbarePosten}) und nicht
     * hier.
     */
    public static final int HOECHSTE_STUECKZAHL = LAGERPLAETZE * 64;

    /**
     * Was ein Posten hoechstens kosten darf, in Goldmark.
     *
     * <p>Zum Kurs des Plans (1.000 Mark je Goldmark) ist das eine Milliarde Mark - mehr
     * als zweitausend Freistaedte. Darueber ist ein Preis keine Zahlung mehr, sondern eine
     * Sperre, und fuer eine Sperre gibt es die Bedingung. Die Zahl bleibt, damit ein
     * vertippter Preis ("5000" statt "5") nicht als gueltig durchgeht.
     */
    public static final long HOECHSTER_PREIS = 1_000_000L;

    /**
     * Wie viele Posten der Katalog hoechstens hat.
     *
     * <p>Die Tafel ist eine CHATLISTE, und sie zeigt je Posten eine Zeile. Mehr als
     * vierundsechzig findet dort niemand mehr; das Menue, das mehr tragen koennte, kommt
     * mit Schritt 13.
     */
    public static final int HOECHSTE_POSTENZAHL = 64;

    /**
     * Der ausgelieferte Katalog - vier Posten, nur Kosmetik ohne Spielvorteil.
     *
     * <p>Die Preise folgen Plan 8.1 ("Partikel, Hut, Rangfarbe 2-5 Goldmark"), also 2.000
     * bis 5.000 Mark zum Kurs der Wechselstube - nach Plan 8.2 ein bis zwei Spielstunden.
     * Zwei Posten tragen eine Bedingung, und das ist Absicht: So steht in der
     * ausgelieferten Datei ein Beispiel fuer jede der beiden Gestalten.
     *
     * <p><b>Was hier bewusst NICHT steht:</b> ein Gegenstand, der etwas kann. Der
     * Kuerbishut verdeckt die Sicht, die Blumen sind Deko, die Glocke und die Scheibe sind
     * Zierrat - gemeinsam haben sie, dass niemand mit ihnen schneller verdient. Plan 8.4
     * verbietet genau das: hoehere Verdienstsaetze, guenstigere Preise, Vorteile im
     * Wettbewerb. Ein Katalog, der eine Rueckenplatte verkauft, waere kein Kosmetikladen
     * mehr.
     */
    public static final List<Posten> VORGABE_KATALOG = List.of(
            new Posten("kuerbishut", Map.of("carved_pumpkin", 1), 2L, Map.of()),
            new Posten("blumenkiste", ordnung("rose_bush", 16, "peony", 16, "lilac", 16),
                    2L, Map.of(Bedingungsart.PARZELLEN, 1L)),
            new Posten("musikscheibe", Map.of("music_disc_cat", 1), 5L, Map.of()),
            new Posten("rathausglocke", Map.of("bell", 1), 5L,
                    Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L)));

    /** Was ohne Datei gilt: Das Muenzhaus ist an, mit dem ausgelieferten Katalog. */
    public static final Muenzwerte VORGABE = new Muenzwerte(true, VORGABE_KATALOG);

    public Muenzwerte {
        if (katalog == null) {
            throw new IllegalArgumentException("Ein Muenzhaus ohne Katalog gibt es nicht"
                    + " - eine LEERE Liste ist erlaubt, null nicht");
        }
        if (katalog.size() > HOECHSTE_POSTENZAHL) {
            throw new IllegalArgumentException("Der Katalog hat " + katalog.size()
                    + " Posten, erlaubt sind " + HOECHSTE_POSTENZAHL + ".");
        }
        List<Posten> abgelegt = new ArrayList<>();
        var gesehen = new LinkedHashSet<String>();
        for (Posten posten : katalog) {
            if (posten == null) {
                throw new IllegalArgumentException("Eine leere Zeile im Katalog");
            }
            if (!gesehen.add(posten.schluessel())) {
                // In der Datei kann derselbe Schluessel nicht zweimal stehen - YAML
                // wuerfe den ersten weg. Ueber das Programm gebaut ginge es, und dann
                // waere nicht zu sagen, welcher Preis gilt.
                throw new IllegalArgumentException("Der Posten \"" + posten.schluessel()
                        + "\" steht zweimal im Katalog.");
            }
            abgelegt.add(posten);
        }
        katalog = List.copyOf(abgelegt);
    }

    /** Ob wirklich etwas zu kaufen ist - an UND nicht leer. */
    public boolean greift() {
        return an && !katalog.isEmpty();
    }

    /** Der Posten zu diesem Schluessel - leer, wenn es ihn nicht gibt. */
    public Optional<Posten> posten(String schluessel) {
        if (schluessel == null) {
            return Optional.empty();
        }
        String klein = schluessel.trim().toLowerCase(Locale.ROOT);
        for (Posten posten : katalog) {
            if (posten.schluessel().equals(klein)) {
                return Optional.of(posten);
            }
        }
        return Optional.empty();
    }

    /** Alle Postenschluessel in der Reihenfolge der Datei - fuer Vorschlag und Bericht. */
    public List<String> schluessel() {
        List<String> namen = new ArrayList<>();
        for (Posten posten : katalog) {
            namen.add(posten.schluessel());
        }
        return List.copyOf(namen);
    }

    /**
     * Welche Bedingungsarten in diesem Katalog ueberhaupt vorkommen.
     *
     * <p>Dieselbe Frage wie {@code Rangregel.gebraucht} und zum selben Zweck: Gemessen
     * wird nur, was jemand verlangt. Eine Abfrage, deren Ergebnis niemand liest, ist eine
     * Fehlerquelle ohne Nutzen - sie kann werfen, und zwar im Hauptfaden.
     *
     * <p>In der Reihenfolge der Aufzaehlung, damit zwei Anzeigen dieselbe haben.
     */
    public List<Bedingungsart> gebraucht() {
        List<Bedingungsart> arten = new ArrayList<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            for (Posten posten : katalog) {
                if (posten.bedingungen().containsKey(art)) {
                    arten.add(art);
                    break;
                }
            }
        }
        return List.copyOf(arten);
    }

    /** Der teuerste Posten - fuer die Rechenprobe im Startbericht. */
    public Optional<Posten> teuerster() {
        return katalog.stream().max((eine, andere)
                -> Long.compare(eine.preis(), andere.preis()));
    }

    /** Der guenstigste Posten - fuer die Rechenprobe im Startbericht. */
    public Optional<Posten> guenstigster() {
        return katalog.stream().min((eine, andere)
                -> Long.compare(eine.preis(), andere.preis()));
    }

    /** Dieselben Werte mit anderem Schalter. */
    public Muenzwerte mitAn(boolean anders) {
        return new Muenzwerte(anders, katalog);
    }

    /** Dieselben Werte mit anderem Katalog. */
    public Muenzwerte mitKatalog(List<Posten> andere) {
        return new Muenzwerte(an, andere);
    }

    /**
     * Ob dieser Schluessel taugt - ohne zu werfen.
     *
     * <p>Fuer den Leser der Einstellungsdatei und fuer den Satz des Postennamens: Beide
     * duerfen nicht werfen und brauchen trotzdem genau die Pruefung des Konstruktors.
     */
    public static boolean brauchbarerSchluessel(String schluessel) {
        return schluessel != null && schluessel.matches(MUSTER)
                && schluessel.length() <= HOECHSTE_SCHLUESSELLAENGE;
    }

    /** Ob dieser Preis taugt - 0 ist erlaubt und heisst gratis. */
    public static boolean brauchbarerPreis(long preis) {
        return preis >= 0L && preis <= HOECHSTER_PREIS;
    }

    /** Ob diese Stueckzahl taugt. */
    public static boolean brauchbareStueckzahl(Integer stueck) {
        return stueck != null && stueck >= 1 && stueck <= HOECHSTE_STUECKZAHL;
    }

    /**
     * Drei Gegenstaende in der Reihenfolge, in der sie genannt sind.
     *
     * <p>{@code Map.of} sagt keine Reihenfolge zu, und die Vorgabe soll zeichengleich so
     * in der Tafel stehen wie in der {@code config.yml}.
     */
    private static Map<String, Integer> ordnung(String erster, int ersteZahl,
                                                String zweiter, int zweiteZahl,
                                                String dritter, int dritteZahl) {
        Map<String, Integer> karte = new LinkedHashMap<>();
        karte.put(erster, ersteZahl);
        karte.put(zweiter, zweiteZahl);
        karte.put(dritter, dritteZahl);
        return karte;
    }
}
