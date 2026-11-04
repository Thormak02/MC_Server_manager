package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Huterin - drei Tore, die bei JEDER Aenderung rot werden.
 *
 * <p>Sie bewacht die eine Zusage, die kein Typ traegt: Ein Spielersatz entsteht nicht mehr
 * im Quelltext. Ein Verbot waere mit einer Konstante zu umgehen, darum sind es Tore und
 * keine Verbote: Die Menge der Dateien, die Farbe nennen, und die Menge der Dateien, die
 * senden, sind FESTGENAGELT und werden in jedem Bauschritt um dessen Dateien kuerzer. Weil
 * {@code assertEquals} in beide Richtungen prueft, ist jede dieser Proben rot, wenn ein
 * Schritt seine Datei vergisst (Menge zu gross) UND wenn irgendwo neue Farbe auftaucht
 * (Menge zu klein). Jede Aenderung an diesen Listen ist damit absichtlich - das ist der
 * ganze Zweck.
 *
 * <p>Die Begruendung "wer senden will, braucht eine Farbe" ist gemessen FALSCH:
 * {@code Component.text("Das geht so nicht.")} ohne Farbe laesst sich senden und erscheint
 * weiss. Darum gibt es das zweite Tor auf den SENDEWEG und nicht nur das auf die Farbe.
 *
 * <p><b>Die zwei Mengen allein genuegen nicht, und das ist gemessen.</b> Fuer eine Datei,
 * die in BEIDEN Mengen bleiben darf - {@code ParzellenBefehle.java} bleibt im Sendeweg,
 * weil der Zweig die Zeilen selbst hinausschickt -, faengt keines der zwei Tore etwas:
 * {@code spieler.sendMessage(Component.text("Hier gibt es keine Parzellen."))} statt eines
 * Aufrufs der reinen Methode liess beide Mengen unveraendert und 356 Proben gruen, und ein
 * ganz WEGGEFALLENER Sendeaufruf ebenso. Darum zaehlen die zwei Tafeln darunter: wie oft
 * jede Datei sendet ({@link #jedeDateiSendetGenauSoOft}) und wie oft jede reine Methode
 * von {@code ParzellenBefehle} aufgerufen wird
 * ({@link #jedeZeilenmethodeWirdGenauSoOftAufgerufen}). Die erste faengt einen
 * weggefallenen und einen verdoppelten Sendeaufruf, die zweite einen handgeschriebenen
 * Satz anstelle eines Aufrufs und einen Zweig, der die falsche Methode waehlt.
 *
 * <p><b>Gelesen wird der Quelltext OHNE Kommentare</b> ({@link Quellen#ohneKommentare}).
 * Vorher nahm {@link #jederSchluesselWirdGesendet} eine Nennung im Javadoc als Benutzung,
 * und ein Schluessel konnte aus dem Programm verschwinden, solange sein Name in einem
 * Kommentar stand. Dasselbe galt fuer jede Zaehlung hier: Ein Javadoc, das eine
 * Sendestelle zitiert, haette mitgezaehlt.
 */
class GrenzeTest {

    /**
     * Die Dateien, die JETZT noch Farbe nennen duerfen - nach Schritt 2.
     *
     * <p>Nach Schritt 1 waren das alle elf Sender: Dort war kein Spielersatz umgezogen,
     * also konnte keine Farbe verschwunden sein. {@code ParzellenBefehle.java} ist mit dem
     * zweiten Zug von Schritt 2 heraus - die groesste Sendeklasse des Projekts nennt keine
     * Farbe mehr, weil jeder ihrer Saetze in {@code sprache-vorlage.yml} steht. Findet
     * diese Probe dort wieder eine, ist ein Satz nicht umgezogen.
     *
     * <p>Dazu {@code Sprachstand.java}: Dort entsteht die Antwort auf
     * {@code /freimarken neuladen}, und das ist BETREIBERTEXT. Der bleibt nach
     * der Grenze dieses Umbaus im Programm, denn eine Diagnose darf nicht von der Datei
     * abhaengen, deren Zustand sie meldet. {@code Sprachstand.java} wird aus dieser Menge
     * also nie gestrichen.
     *
     * <p>{@code FreimarkenPlugin.java} bleibt ebenfalls darin, und das ist ein Loch, das
     * offen bleibt - nur nicht unbewacht. Seine Betreiberzeilen muessen dort bleiben, also
     * faengt diese Probe einen dort neu geschriebenen Spielersatz nicht. Dafuer gibt es
     * die Tafel in {@link #jedeDateiSendetGenauSoOft}: ein Tor und kein Beweis.
     */
    private static final Set<String> FARBE_ERLAUBT = new TreeSet<>(List.of(
            "AnfragenBefehle.java", "Ankunftswache.java", "Aufwaermzeit.java",
            "FreimarkenPlugin.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            "SpielzeitBefehle.java", "Sprachstand.java", "WarpBefehle.java"));

    /**
     * Die Dateien, die noch senden duerfen.
     *
     * <p>Die elf Sender, dazu {@code Sprache.java} - das ist der EINE Ausgang nach
     * Adventure und soll senden. {@code Sprachstand.java} steht hier NICHT: Es baut die
     * Antwortzeilen und gibt sie zurueck, gesendet werden sie vom Aufrufer. Genau so ist
     * die Antwort ohne Server pruefbar.
     *
     * <p><b>Diese Menge ist NICHT dieselbe wie {@link #FARBE_ERLAUBT}, und sie kuerzt sich
     * auch nicht im selben Schritt.</b> {@code ParzellenBefehle.java} bleibt hier, obwohl
     * es keine Farbe mehr nennt: Der Zweig HOLT die Zeilen bei {@code Sprache} und schickt
     * sie selbst hinaus - 44 Mal, und diese Zahl steht seit dem 04.11.2026 festgenagelt in
     * {@link #SENDESTELLEN} und nicht mehr bloss in diesem Satz. Genau das ist gewollt,
     * denn die reine Methode, die die Zeilen baut, ist der Griff, an dem
     * {@code SendewegTest} sie prueft. Wer diese Zeile streicht, weil "die Datei ist doch
     * umgezogen", macht die Probe rot.
     */
    private static final Set<String> SENDEN_ERLAUBT = new TreeSet<>(List.of(
            "AnfragenBefehle.java", "Ankunftswache.java", "Aufwaermzeit.java",
            "FreimarkenPlugin.java", "ParzellenBefehle.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            "SpielzeitBefehle.java", "Sprache.java", "WarpBefehle.java"));

    /**
     * Wie oft JEDE sendende Datei sendet - nachgezaehlt, nicht geschaetzt.
     *
     * <p>Diese Tafel ist der Nagel unter {@link #SENDEN_ERLAUBT}. Die Menge sagt, WO
     * gesendet werden darf; erst die Zahl sagt, WIE OFT. Ohne sie blieb alles gruen, als
     * ein {@code .forEach(spieler::sendMessage)} in {@code ParzellenBefehle} zu einem
     * leeren {@code .forEach(zeile -> { })} wurde - ein Spieler tippt {@code /mark} und
     * liest GAR NICHTS. Und ebenso, als eine Sendung verdoppelt war und der Spieler
     * "Parzelle 3/-4 ist wieder frei." zweimal las.
     *
     * <p>Die Zahl ist fuer jeden Bauschritt eine SICHTBARE Entscheidung: Wer eine
     * Sendestelle hinzufuegt, hebt sie; wer einen Zweig auf {@code Sprache.sende}
     * umstellt, senkt sie. Beides soll man im Diff sehen.
     *
     * <p>{@code FreimarkenPlugin} stand hier schon allein (39 vor dem 04.11.2026, 40
     * danach: dazu kam die Zeile, die den Sprachteil der Antwort auf
     * {@code /freimarken neuladen} hinausschickt). Dass die groesste Sendeklasse des
     * Projekts keinen solchen Nagel hatte, war das Loch.
     */
    private static final Map<String, Integer> SENDESTELLEN = Map.ofEntries(
            Map.entry("AnfragenBefehle.java", 13),
            Map.entry("Ankunftswache.java", 2),
            Map.entry("Aufwaermzeit.java", 2),
            Map.entry("FreimarkenPlugin.java", 40),
            Map.entry("ParzellenBefehle.java", 44),
            Map.entry("Portalwache.java", 2),
            Map.entry("Reise.java", 7),
            Map.entry("RueckwegBefehle.java", 1),
            Map.entry("Schutzwache.java", 1),
            Map.entry("SpielzeitBefehle.java", 4),
            Map.entry("Sprache.java", 3),
            Map.entry("WarpBefehle.java", 6));

    /**
     * Wie oft jede reine Zeilen-Methode von {@code ParzellenBefehle} AUFGERUFEN wird.
     *
     * <p>Der zweite Nagel, und er faengt eine andere Fehlerklasse als der erste.
     * {@code SendewegTest} ruft diese Methoden unmittelbar auf und prueft ihr Bild - was
     * der ZWEIG mit ihnen tut, prueft es nicht. Gemessen blieb darum gruen: ein
     * handgeschriebenes {@code Component.text(...)} anstelle eines Aufrufs (die Zahl faellt
     * auf 0) und ein Zweig, der {@code nichtDeineParzelleZeilen} waehlt, wo
     * {@code aufgebenGruppeZeilen} hingehoert - dann liest der Spieler auf seinem eigenen
     * Gruppenkind "Das ist nicht deine Parzelle." und sucht den Eigentuemer. Mit dieser
     * Tafel sind beide rot: eine Zahl zu klein, die andere zu gross.
     *
     * <p>Gezaehlt wird der Aufruf und nicht die Deklaration, und auch nicht der Name als
     * Teil eines anderen: {@code bodenZeilen} steckt in {@code gruppenbodenZeilen}, und
     * genau darum steht in der Suche eine Ruecksicht auf das Zeichen davor.
     */
    private static final Map<String, Integer> AUFRUFE_IN_PARZELLENBEFEHLE =
            Map.ofEntries(
                    Map.entry("absageZeilen", 2),
                    Map.entry("aufgebenGruppeZeilen", 1),
                    Map.entry("aufgebenWarnungZeilen", 1),
                    Map.entry("aufgegebenZeilen", 1),
                    Map.entry("aufgetrenntZeilen", 2),
                    Map.entry("auftrennenBestaetigenZeilen", 1),
                    Map.entry("auftrennenWarnungZeilen", 1),
                    Map.entry("aufwandZeilen", 1),
                    Map.entry("ausserhalbZeilen", 1),
                    Map.entry("bodenFehlerZeilen", 1),
                    Map.entry("bodenZeilen", 1),
                    Map.entry("freiZeilen", 1),
                    Map.entry("gruppeZuGrossZeilen", 1),
                    Map.entry("gruppenbodenZeilen", 1),
                    Map.entry("herausgeloestZeilen", 1),
                    Map.entry("herausloesenBestaetigenZeilen", 1),
                    Map.entry("herausloesenWarnungZeilen", 1),
                    Map.entry("infoKopfZeilen", 1),
                    Map.entry("listeZeilen", 1),
                    Map.entry("markZeilen", 1),
                    Map.entry("mitbauerErteiltZeilen", 1),
                    Map.entry("mitbauerKeineParzelleZeilen", 1),
                    Map.entry("mitbauerNichtDrinZeilen", 1),
                    Map.entry("mitbauerSchonDrinZeilen", 1),
                    Map.entry("mitbauerWegErteiltZeilen", 1),
                    Map.entry("mitbauerZeilen", 1),
                    Map.entry("nehmenAusserhalbZeilen", 1),
                    Map.entry("nehmenErteiltZeilen", 1),
                    Map.entry("nehmenGrenzeZeilen", 1),
                    Map.entry("nehmenKeinBaulandZeilen", 1),
                    Map.entry("nehmenSchonVergebenZeilen", 1),
                    Map.entry("nehmenSonstZeilen", 1),
                    Map.entry("nehmenZuWenigZeilen", 1),
                    Map.entry("nichtDeineParzelleZeilen", 1),
                    Map.entry("nichtInDerMarktstadtZeilen", 1),
                    Map.entry("niemandMitDemNamenZeilen", 1),
                    Map.entry("ohneParzelleZeilen", 1),
                    Map.entry("stehtNichtAufZeilen", 1),
                    Map.entry("unbekannterSpielerZeilen", 1),
                    Map.entry("vergebenZeilen", 1),
                    Map.entry("verlaufZeilen", 1),
                    Map.entry("werbungZeilen", 3),
                    Map.entry("zuSchnellZeilen", 1),
                    Map.entry("zusammengelegtZeilen", 1),
                    Map.entry("zusammenlegenWarnungZeilen", 1));

    /** Ein echter Sendeaufruf - nicht die Erwaehnung in einem Kommentar. */
    private static final Pattern SENDEWEG = Pattern.compile(
            "[.]send(Message|ActionBar)\\(|::send(Message|ActionBar)");

    private static final Pattern FARBE = Pattern.compile("NamedTextColor|TextColor");

    @Test
    @DisplayName("32 - die Quellen wurden ueberhaupt gefunden")
    void quellenWurdenGefunden() {
        // Ohne diese Probe laufen die drei Tore leer durch und sind gruen, ohne etwas
        // gesehen zu haben.
        assertNotNull(Quellen.ordner(), "der Quellordner wurde nicht gefunden - die drei"
                + " Tore darueber waeren damit gruen, ohne etwas geprueft zu haben");
        List<Path> dateien = Quellen.alle();
        assertFalse(dateien.isEmpty());
        assertTrue(dateien.stream().anyMatch(pfad
                        -> pfad.getFileName().toString().equals("ParzellenBefehle.java")),
                "die groesste Sendeklasse des Projekts fehlt in der Liste");
    }

    @Test
    @DisplayName("30 - jeder Schluessel wird irgendwo benutzt")
    void jederSchluesselWirdGesendet() {
        // Gesucht wird die QUALIFIZIERTE Nennung, also BundSatz.BEFEHL_MARK und nicht
        // bloss BEFEHL_MARK: Ein nackter Konstantenname traefe auch gewoehnlichen Text.
        Set<String> ohneNennung = new TreeSet<>();
        for (Satz satz : Sprachschluessel.alle()) {
            String bereich = ((Enum<?>) satz).getDeclaringClass().getSimpleName();
            String eigenerBereich = bereich + ".java";
            String marke = bereich + "." + ((Enum<?>) satz).name();
            boolean gefunden = false;
            for (Path datei : Quellen.alle()) {
                String name = datei.getFileName().toString();
                // Die eigene Aufzaehlung zaehlt nicht - dort steht der Schluessel nur
                // deshalb, weil es ihn gibt. Sprachschluessel und Sprachstand zaehlen
                // ebenfalls nicht: Dort bescheinigten sich die mechanischen Familien
                // selbst.
                if (name.equals(eigenerBereich) || name.equals("Sprachschluessel.java")
                        || name.equals("Sprachstand.java")) {
                    continue;
                }
                if (Quellen.text(datei).contains(marke)) {
                    gefunden = true;
                    break;
                }
            }
            if (!gefunden) {
                ohneNennung.add(satz.pfad());
            }
        }
        // assertEquals auf die MENGE: Erlaubt ist genau die eine Familie, deren Pfad
        // mechanisch aus einem Aufzaehlungsnamen entsteht und die darum nirgends
        // namentlich getippt wird. Was sie wirklich deckt, ist SendewegTest - dort geht
        // jeder Buchungsgrund durch die echte Zeile. Wird ein weiterer Satz
        // selbstbescheinigend, ist diese Probe rot.
        assertEquals(new TreeSet<>(Sprachschluessel.familienDeckung()), ohneNennung);
    }

    @Test
    @DisplayName("31 - Farbe steht nur noch in DIESEN Dateien")
    void farbeNurNochInDiesenDateien() {
        assertEquals(FARBE_ERLAUBT, dateienMit(FARBE));
    }

    @Test
    @DisplayName("31b - gesendet wird nur noch in DIESEN Dateien")
    void sendenNurNochInDiesenDateien() {
        assertEquals(SENDEN_ERLAUBT, dateienMit(SENDEWEG));
    }

    @Test
    @DisplayName("31c - jede sendende Datei sendet genau so oft wie festgenagelt")
    void jedeDateiSendetGenauSoOft() {
        Map<String, Integer> gezaehlt = new TreeMap<>();
        for (Path datei : Quellen.alle()) {
            int anzahl = treffer(SENDEWEG, Quellen.text(datei));
            if (anzahl > 0) {
                gezaehlt.put(datei.getFileName().toString(), anzahl);
            }
        }
        // assertEquals auf die TAFEL und nicht je Datei: Eine Zahl zu klein ist eine
        // weggefallene Sendung, eine Zahl zu gross eine verdoppelte oder neue, und ein
        // fehlender Eintrag eine Datei, die ueberhaupt nicht mehr sendet.
        assertEquals(new TreeMap<>(SENDESTELLEN), gezaehlt, "Jede Sendestelle ist eine"
                + " sichtbare Entscheidung. Faellt eine weg, liest der Spieler an dieser"
                + " Stelle GAR NICHTS, und ohne diese Tafel bleibt der ganze Lauf gruen.");
    }

    @Test
    @DisplayName("31d - jede Zeilen-Methode wird genau so oft aufgerufen")
    void jedeZeilenmethodeWirdGenauSoOftAufgerufen() {
        String quelle = Quellen.text(datei("ParzellenBefehle.java"));
        Map<String, Integer> gezaehlt = new TreeMap<>();
        var deklaration = ZEILENMETHODE.matcher(quelle);
        while (deklaration.find()) {
            String name = deklaration.group(1);
            // Minus eins: die Deklaration selbst. Die Ruecksicht auf das Zeichen davor
            // haelt gruppenbodenZeilen von bodenZeilen getrennt.
            gezaehlt.put(name, treffer(Pattern.compile(
                    "(?<![\\w$.])" + name + "\\s*\\("), quelle) - 1);
        }
        assertTrue(gezaehlt.size() > 20, "die Suche hat die reinen Methoden nicht"
                + " gefunden - dann waere diese Tafel gruen, ohne etwas gesehen zu haben:"
                + " " + gezaehlt.keySet());
        assertEquals(new TreeMap<>(AUFRUFE_IN_PARZELLENBEFEHLE), gezaehlt,
                "Welche Methode ein Zweig waehlt, prueft sonst nichts: SendewegTest ruft"
                        + " sie unmittelbar auf. Eine Zahl auf 0 heisst, der Zweig schreibt"
                        + " den Satz jetzt selbst; eine Zahl zu hoch heisst, er waehlt sie"
                        + " an einer Stelle, an der ein anderer Satz hingehoert.");
    }

    /** Die Deklaration einer reinen Methode, die Zeilen baut. */
    private static final Pattern ZEILENMETHODE = Pattern.compile(
            "static (?:List<Component>|Component) (\\w+)\\s*\\(");

    private static Path datei(String name) {
        for (Path datei : Quellen.alle()) {
            if (datei.getFileName().toString().equals(name)) {
                return datei;
            }
        }
        throw new AssertionError(name + " wurde nicht gefunden - eine Probe, die nichts"
                + " liest, ist gruen, ohne etwas geprueft zu haben");
    }

    private static int treffer(Pattern muster, String text) {
        var lauf = muster.matcher(text);
        int anzahl = 0;
        while (lauf.find()) {
            anzahl++;
        }
        return anzahl;
    }

    private static Set<String> dateienMit(Pattern muster) {
        Set<String> dateien = new TreeSet<>();
        for (Path datei : Quellen.alle()) {
            if (muster.matcher(Quellen.text(datei)).find()) {
                dateien.add(datei.getFileName().toString());
            }
        }
        // Eine leere Menge ist hier immer ein Fehler der Suche und nie ein Befund: Dann
        // waere das Tor darueber gruen, ohne etwas gesehen zu haben.
        assertFalse(dateien.isEmpty(), "die Suche hat keine einzige Datei gefunden");
        return dateien;
    }
}
