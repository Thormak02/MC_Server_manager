package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
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
 * ganz WEGGEFALLENER Sendeaufruf ebenso. Darum zaehlen die Tafeln darunter: wie oft
 * jede Datei sendet ({@link #jedeDateiSendetGenauSoOft}) und wie oft jede reine Methode
 * von {@code ParzellenBefehle} aufgerufen wird
 * ({@link #jedeZeilenmethodeWirdGenauSoOftAufgerufen}). Die erste faengt einen
 * weggefallenen und einen verdoppelten Sendeaufruf, die zweite einen handgeschriebenen
 * Satz anstelle eines Aufrufs und einen Zweig, der die falsche Methode waehlt.
 *
 * <p>Dieselbe Zaehlung tragen seit dem 05.11.2026 die zwei Dateien von Schritt 4:
 * {@code SpielzeitBefehle} ({@link #jedeAbsageDerSpielzeitWirdGenauSoOftAufgerufen}) und
 * {@code Spielzeittext}
 * ({@link #jedeSatzmethodeVonSpielzeittextWirdGenauSoOftAufgerufen}). Beide kamen mit
 * Schritt 4 aus der Farbmenge heraus und standen bis dahin ohne Tafel da - derselbe
 * handgeschriebene Satz, der bei {@code ParzellenBefehle} 356 Proben gruen liess, blieb
 * dort vollstaendig gruen. Fuer {@code Spielzeittext.java} ist diese Zaehlung sogar der
 * EINZIGE Nagel, den es geben kann: Die Datei sendet gar nicht, also sagt
 * {@link #SENDESTELLEN} ueber sie nichts.
 *
 * <p><b>Gelesen wird der Quelltext OHNE Kommentare</b> ({@link Quellen#ohneKommentare}).
 * Vorher nahm {@link #jederSchluesselWirdGesendet} eine Nennung im Javadoc als Benutzung,
 * und ein Schluessel konnte aus dem Programm verschwinden, solange sein Name in einem
 * Kommentar stand. Dasselbe galt fuer jede Zaehlung hier: Ein Javadoc, das eine
 * Sendestelle zitiert, haette mitgezaehlt.
 */
class GrenzeTest {

    /**
     * Die Dateien, die JETZT noch Farbe nennen duerfen - nach Schritt 3.
     *
     * <p>Nach Schritt 1 waren das alle elf Sender: Dort war kein Spielersatz umgezogen,
     * also konnte keine Farbe verschwunden sein. {@code ParzellenBefehle.java} ist mit dem
     * zweiten Zug von Schritt 2 heraus - die groesste Sendeklasse des Projekts nennt keine
     * Farbe mehr, weil jeder ihrer Saetze in {@code sprache-vorlage.yml} steht. Mit
     * Schritt 3 ist {@code AnfragenBefehle.java} gefolgt: Rot, Grau, Gold und Gruen der
     * vier Teleportbefehle stehen jetzt unter {@code anfrage} in derselben Datei. Findet
     * diese Probe in einer der beiden wieder eine Farbe, ist ein Satz nicht umgezogen.
     *
     * <p>Mit Schritt 4 sind ZWEI Dateien auf einmal heraus: {@code SpielzeitBefehle.java}
     * und {@code Spielzeittext.java}. Die zweite war nie darin - sie nannte bis zum
     * 04.11.2026 ueberhaupt keine Farbe, weil der Sendeort grau faerbte; sie kam mit der
     * Naht dieses Schrittes kurz hinein und ging mit dem Umzug wieder heraus. Dass die
     * Anzeige der Spielzeit danach Einzug, Spaltenbreite UND Farbe aus
     * {@code sprache-vorlage.yml} holt, ist damit auf zwei Dateien gleichzeitig
     * festgenagelt.
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
            "Ankunftswache.java", "Aufwaermzeit.java",
            "FreimarkenPlugin.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            "Sprachstand.java", "WarpBefehle.java"));

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
     *
     * <p>Seit Schritt 3 gilt dasselbe fuer {@code AnfragenBefehle.java}: Es ist aus
     * {@link #FARBE_ERLAUBT} heraus und bleibt hier, weil die vier Teleportbefehle ihre
     * Zeilen weiter selbst hinausschicken - 11 Mal, festgenagelt in
     * {@link #SENDESTELLEN}, und je Methode an einen festgenagelten Empfaenger
     * ({@link #SENDEPAARE_IN_ANFRAGENBEFEHLE}).
     *
     * <p>Mit Schritt 4 ebenso {@code SpielzeitBefehle.java}: aus {@link #FARBE_ERLAUBT}
     * heraus, hier geblieben. Seine Zahl in {@link #SENDESTELLEN} ist dabei GEMESSEN
     * gleich geblieben - vier vorher, vier nachher -, und das ist kein Zufall, sondern
     * eine Verschiebung: Die Ueberschrift und die Zeilenschleife in {@code sende} sind
     * dieselben zwei Stellen wie vorher, und die zwei einzeln gesendeten Absagen
     * (Konsole, abwesender Spieler) sind zwei Aufrufe reiner Methoden mit eigenem
     * {@code forEach} geworden. Bleibt die Zahl beim naechsten Umbau gleich, obwohl eine
     * Stelle wegfaellt, ist eine andere hinzugekommen - genau dafuer steht sie da.
     *
     * <p>Dass diese Zahl die Verschiebung UEBERLEBT hat, ist aber auch ihre Grenze: Eine
     * Vier bleibt eine Vier, wenn der Zweig statt der reinen Methode seinen eigenen Satz
     * sendet. Was WELCHE Methode der Zweig ruft, nagelt darum
     * {@link #AUFRUFE_IN_SPIELZEITBEFEHLE} fest, und fuer die Zeilen der Anzeige
     * {@link #AUFRUFE_VON_SPIELZEITTEXT}.
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
     *
     * <p>{@code AnfragenBefehle} stand bis Schritt 3 auf 13 und steht jetzt auf 11: Die
     * dreizehn handgeschriebenen Sendestellen sind elf Aufrufe reiner Methoden geworden.
     * Elf und nicht dreizehn, weil zwei Paare desselben Empfaengers in je EINE Methode
     * gefallen sind: Der Angefragte bekommt Frage und Hinweis jetzt aus einer Liste mit
     * zwei Zeilen, und das {@code if/else} ueber die zwei Quittungen an den Fragenden
     * ist eine Methode mit zwei Rueckgaben. Die Datei sendet weiter selbst, darum bleibt
     * sie in {@link #SENDEN_ERLAUBT} - WER was bekommt, nagelt
     * {@link #SENDEPAARE_IN_ANFRAGENBEFEHLE} fest.
     */
    private static final Map<String, Integer> SENDESTELLEN = Map.ofEntries(
            Map.entry("AnfragenBefehle.java", 11),
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

    /**
     * Wie oft jede reine Absage-Methode von {@code SpielzeitBefehle} AUFGERUFEN wird.
     *
     * <p>Dieselbe Fehlerklasse wie bei {@link #AUFRUFE_IN_PARZELLENBEFEHLE}, und fuer
     * diese Datei stand sie bis zum 05.11.2026 offen: {@code SpielzeitBefehle} ist mit
     * Schritt 4 aus {@link #FARBE_ERLAUBT} heraus, bekam aber keine Tafel.
     * <b>Gemessen gruen blieb darum ein handgeschriebener Satz ANSTELLE des
     * Dateisatzes</b> - ein
     * {@code quelle.getSender().sendMessage(Component.text("Die Konsole spielt nicht."))}
     * statt {@code keineKonsoleZeilen(sprache).forEach(...)}.
     *
     * <p><b>Warum {@link #SENDESTELLEN} diesen Fehler nicht faengt:</b> Die Zahl dort
     * bleibt 4. Der Zweig sendet weiter genau einmal, nur eben seinen eigenen Satz. Probe
     * 31 bleibt gruen, weil {@code Component.text(...)} ohne Farbe auskommt; 31b, weil
     * diese Datei senden DARF; und die zwei Bildproben dieser Absagen bleiben gruen, weil
     * sie die reine Methode unmittelbar aufrufen - {@code SpielzeitBefehleTest} 33g fuer
     * die Konsole, 33h fuer den Abwesenden. Die Methode steht nach so einem Eingriff noch
     * da, nur ruft sie niemand mehr auf. Der Betreiber aendert dann
     * {@code spielzeit.keine-konsole} in der Sprachdatei, und im Spiel aendert sich
     * nichts. Genau das soll die Sprachdatei verhindern.
     *
     * <p>Eine Zahl auf 0 ist der handgeschriebene Satz oder ein ganz weggefallener
     * Aufruf; eine Zahl zu hoch heisst, der Zweig waehlt eine Absage an einer Stelle, an
     * der eine andere hingehoert. {@code nichtLesbarZeilen} statt
     * {@code keineKonsoleZeilen} waere so ein Fall - beide nehmen nur {@link Sprache},
     * das uebersetzt also anstandslos, und die Konsole laese "nicht lesbar" statt des
     * Weges, auf dem sie die Spielzeit doch bekommt.
     */
    private static final Map<String, Integer> AUFRUFE_IN_SPIELZEITBEFEHLE = Map.ofEntries(
            Map.entry("keineKonsoleZeilen", 1),
            Map.entry("nichtDaZeilen", 1),
            Map.entry("nichtLesbarZeilen", 1));

    /**
     * Wie oft jede Satzmethode von {@code Spielzeittext} AUFGERUFEN wird - im eigenen
     * Quelltext und von aussen zusammen.
     *
     * <p><b>"Von aussen" heisst aus den PROGRAMMQUELLEN</b>, nicht aus jeder Datei des
     * Projekts: {@code Quellen.alle()} liest allein {@code src/main/java/de/freimarken/paper},
     * die Proben zaehlen also nicht mit. Wer die Aufrufe aus {@code SpielzeittextTest}
     * und {@code SpielzeitBefehleTest} mitzaehlt, traegt Zahlen ein, die diese Probe nie
     * erreichen kann - und schwaecht dann die Tafel, weil sie scheinbar falsch ist.
     *
     * <p>Der Grund ist derselbe wie eine Tafel hoeher, die Luecke aber groesser: Keine
     * dieser Methoden sendet selbst, also kann {@link #SENDESTELLEN} ueber sie ueberhaupt
     * nichts sagen - {@code Spielzeittext.java} steht gar nicht in
     * {@link #SENDEN_ERLAUBT}, kann dort also keine Zahl haben.
     *
     * <p><b>Was diese Tafel allein faengt, ist gemessen.</b> Die UEBERSCHRIFT ist der
     * Fall: {@code Spielzeittext.kopfFuer} im Zweig durch ein handgeschriebenes
     * {@code Component.text("Spielzeit von " + name)} ersetzt, und der ganze Lauf blieb
     * gruen - bis auf diese Probe. Die 4 von {@code SpielzeitBefehle.java} bleibt, denn
     * gesendet wird weiter genau so oft; eine Farbe nennt der Satz nicht; und die
     * Bildproben der Anzeige rufen {@code kopfFuer} unmittelbar auf und sehen den Zweig
     * nicht. Die Ueberschrift stuende danach wieder im Programm und nicht in
     * {@code sprache-vorlage.yml}.
     *
     * <p>Fuer die ZEILEN unter der Ueberschrift ist die Tafel die zweite Wache und nicht
     * die erste: Nimmt man {@code hinweisZeile(sprache)} aus der Liste von {@code werte},
     * werden ausser dieser Probe auch sieben Bildproben rot - die Anzeige hat dann drei
     * Zeilen statt vier. Dort faengt die Tafel den Fehler nur FRUEHER und mit einer
     * Meldung, die den Namen der Methode nennt.
     *
     * <p>Gezaehlt wird im eigenen Quelltext der nackte Aufruf und auswaerts der mit
     * {@code Spielzeittext.} davor; die Deklaration zaehlt nicht mit. Die Ruecksicht auf
     * das Zeichen davor trennt {@code dauer} von {@code genaueDauer} - sonst stuende die
     * Zahl von {@code dauer} um die Aufrufe der genauen Form zu hoch.
     *
     * <p>{@code zustandZeile} steht mit darin, obwohl es {@code private} ist: Faellt sein
     * Aufruf aus {@code werte}, liest der Spieler drei Zeilen statt vier und erfaehrt
     * nicht mehr, ob seine Zeit gerade gezaehlt wird.
     */
    private static final Map<String, Integer> AUFRUFE_VON_SPIELZEITTEXT = Map.ofEntries(
            Map.entry("dauer", 3),
            Map.entry("genaueDauer", 2),
            Map.entry("hinweisZeile", 1),
            Map.entry("kopfFuer", 1),
            Map.entry("kopfSelbst", 1),
            Map.entry("schreibzugWartet", 1),
            Map.entry("taktAbstand", 1),
            Map.entry("werte", 1),
            Map.entry("zustandZeile", 1));

    /**
     * Welche Ueberschrift in WELCHEM Zweig von {@code /freimarken spielzeit} steht - in
     * Quellreihenfolge, gemessen.
     *
     * <p>Der vierte Nagel, und er schliesst genau die Luecke, die
     * {@link #AUFRUFE_VON_SPIELZEITTEXT} offen laesst: Vertauscht man
     * {@code kopfSelbst} und {@code kopfFuer} zwischen den zwei Zweigen, bleibt dort
     * JEDE Zahl 1 - gerufen wird beides weiter genau einmal. Gemessen am 05.11.2026:
     * der Tausch liess den ganzen Lauf gruen, 796 Proben, keine rot.
     *
     * <p>Im Spiel liest danach, wer {@code /freimarken spielzeit} tippt, "Spielzeit von
     * &lt;sich selbst&gt;" ueber seinen eigenen Zahlen - und wer nach Anna fragt, liest
     * "Deine Spielzeit" ueber Annas Zahlen. Ein echter Spielfehler, kein
     * Schoenheitsfehler.
     *
     * <p>Die Bildproben 33a bis 33i koennen das nicht sehen: Sie geben die Ueberschrift
     * SELBST herein ({@code gesendetesBild(Spielzeittext.kopfSelbst(sprache), ...)}) und
     * erreichen die zwei Zweige nie, weil es {@code Bukkit.getPlayerExact} ohne
     * laufenden Server nicht gibt. Darum liest diese Probe den Quelltext.
     */
    private static final List<String> KOPFPAARE_IN_SPIELZEITBEFEHLE = List.of(
            "zeigeEigene -> kopfSelbst",
            "zeigeFremde -> kopfFuer");

    /**
     * Welche reine Methode von {@code AnfragenBefehle} an WELCHEN Spieler geht - in
     * Quellreihenfolge, gemessen.
     *
     * <p>Der dritte Nagel, und er faengt die Fehlerklasse, die {@code /tpa} eigen ist:
     * <b>ZWEI Empfaenger in EINEM Zweig.</b> {@code frage} schickt eine Zeile an den
     * Fragenden und zwei an den Angefragten, {@code nimmAn} an beide. {@code SendewegTest}
     * ruft die reinen Methoden unmittelbar auf und sieht den Empfaenger darum gar nicht:
     * Vertauscht ein Zweig die zwei Spieler, bleibt jede Tafel dort gruen - und im Spiel
     * liest der Fragende, er moechte zu sich selbst kommen, und der Annehmer, er selbst
     * habe angenommen. Beides ist ein echter Spielfehler und kein Schoenheitsfehler.
     *
     * <p>Dieselbe Tafel faengt auch einen handgeschriebenen Satz anstelle eines Aufrufs:
     * Dann fehlt das Paar, und das ist unabhaengig von {@link #SENDESTELLEN} - ein
     * {@code annehmer.sendMessage(Component.text("..."))} laesst die ZAHL unveraendert.
     *
     * <p>Die Spielernamen sind hier Teil der Zusage. Das ist Absicht und derselbe Zweck
     * wie bei den Tafeln darueber: Wer eine Zeile aendert, soll es im Diff sehen.
     */
    private static final List<String> SENDEPAARE_IN_ANFRAGENBEFEHLE = List.of(
            "nichtDaZeilen -> fragender",
            "beiDirZeilen -> fragender",
            "stehtNochZeilen -> fragender",
            "stellenFragerZeilen -> fragender",
            "stellenAngefragtZeilen -> angefragter",
            "niemandFragtZeilen -> annehmer",
            "fragenderWegZeilen -> annehmer",
            "nimmAnAnnehmerZeilen -> annehmer",
            "nimmAnFragerZeilen -> fragender",
            "weitereZeilen -> annehmer",
            "lehneAbZeilen -> annehmer");

    /**
     * Ein Sendeort mit seinem Empfaenger: der Aufruf einer Zeilen-Methode, und an wen
     * die Liste geht.
     *
     * <p>{@code [^;]} und nicht {@code .}: So kann ein Treffer keine zwei Anweisungen
     * ueberspannen, und ein Aufruf ohne eigenes {@code forEach} - etwa der von
     * {@code niemandFragtZeilen} innerhalb von {@code lehneAbZeilen} - wird nicht dem
     * naechsten Sendeort zugeschlagen.
     */
    private static final Pattern SENDEPAAR = Pattern.compile(
            "(\\w+Zeilen)\\s*\\([^;]*?\\.forEach\\((\\w+)::sendMessage\\)");

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

    @Test
    @DisplayName("31e - jede Zeilen-Methode von /tpa geht an den richtigen Spieler")
    void jedeZeilenmethodeGehtAnDenRichtigenSpieler() {
        String quelle = Quellen.text(datei("AnfragenBefehle.java"));
        List<String> gefunden = new ArrayList<>();
        var paar = SENDEPAAR.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " -> " + paar.group(2));
        }
        // Die LISTE in Quellreihenfolge und keine Menge: Ein verdoppelter Sendeort und
        // zwei getauschte Empfaenger bleiben in einer Menge beide gruen.
        assertEquals(SENDEPAARE_IN_ANFRAGENBEFEHLE, gefunden, "Zwei Empfaenger in einem"
                + " Zweig: SendewegTest ruft die reinen Methoden auf und sieht nicht, WER"
                + " die Liste bekommt. Vertauscht, liest der Fragende die Zeile des"
                + " Angefragten - und umgekehrt.");
    }

    @Test
    @DisplayName("31f - jede Absage von /freimarken spielzeit wird genau so oft gerufen")
    void jedeAbsageDerSpielzeitWirdGenauSoOftAufgerufen() {
        String quelle = Quellen.text(datei("SpielzeitBefehle.java"));
        Map<String, Integer> gezaehlt = new TreeMap<>();
        var deklaration = ZEILENMETHODE.matcher(quelle);
        while (deklaration.find()) {
            String name = deklaration.group(1);
            // Minus eins: die Deklaration selbst - genau wie in 31d.
            gezaehlt.put(name, treffer(Pattern.compile(
                    "(?<![\\w$.])" + name + "\\s*\\("), quelle) - 1);
        }
        assertFalse(gezaehlt.isEmpty(), "die Suche hat die reinen Absagen nicht gefunden -"
                + " dann waere diese Tafel gruen, ohne etwas gesehen zu haben");
        assertEquals(new TreeMap<>(AUFRUFE_IN_SPIELZEITBEFEHLE), gezaehlt,
                "Ein handgeschriebener Satz anstelle des Aufrufs laesst die 4 in"
                        + " SENDESTELLEN unveraendert und nennt keine Farbe, und die"
                        + " Bildproben rufen die reine Methode auf, die noch dasteht -"
                        + " dann aendert der Betreiber den Satz in sprache-vorlage.yml,"
                        + " und im Spiel aendert sich nichts.");
    }

    @Test
    @DisplayName("31g - jede Satzmethode von Spielzeittext wird genau so oft gerufen")
    void jedeSatzmethodeVonSpielzeittextWirdGenauSoOftAufgerufen() {
        String eigen = Quellen.text(datei("Spielzeittext.java"));
        Map<String, Integer> gezaehlt = new TreeMap<>();
        var deklaration = SATZMETHODE.matcher(eigen);
        while (deklaration.find()) {
            String name = deklaration.group(1);
            // Im eigenen Quelltext steht der Aufruf nackt, auswaerts mit dem Klassennamen
            // davor. Minus eins: die Deklaration selbst.
            int anzahl = treffer(Pattern.compile(
                    "(?<![\\w$.])" + name + "\\s*\\("), eigen) - 1;
            for (Path datei : Quellen.alle()) {
                if (!datei.getFileName().toString().equals("Spielzeittext.java")) {
                    anzahl += treffer(Pattern.compile(
                                    "Spielzeittext\\s*[.]\\s*" + name + "\\s*\\("),
                            Quellen.text(datei));
                }
            }
            gezaehlt.put(name, anzahl);
        }
        assertFalse(gezaehlt.isEmpty(), "die Suche hat die Satzmethoden nicht gefunden -"
                + " dann waere diese Tafel gruen, ohne etwas gesehen zu haben");
        assertEquals(new TreeMap<>(AUFRUFE_VON_SPIELZEITTEXT), gezaehlt,
                "Keine dieser Methoden sendet selbst, also sagt SENDESTELLEN ueber sie"
                        + " nichts: Ein weggelassener Aufruf laesst jede Zahl dort"
                        + " unveraendert, und der Spieler liest die Zeile einfach nicht"
                        + " mehr. Eine Zahl zu hoch heisst, ein Satz steht an einer"
                        + " Stelle, an der ein anderer hingehoert.");
    }

    @Test
    @DisplayName("31h - jeder Zweig von /freimarken spielzeit nimmt seine eigene"
            + " Ueberschrift")
    void jederZweigNimmtSeineEigeneUeberschrift() {
        String quelle = Quellen.text(datei("SpielzeitBefehle.java"));
        List<String> namen = new ArrayList<>();
        List<Integer> anfaenge = new ArrayList<>();
        var zweig = ANZEIGEZWEIG.matcher(quelle);
        while (zweig.find()) {
            namen.add(zweig.group(1));
            anfaenge.add(zweig.start());
        }
        assertFalse(namen.isEmpty(), "die Suche hat die Anzeigezweige nicht gefunden -"
                + " dann waere diese Probe gruen, ohne etwas gesehen zu haben");
        // Abschnittsweise und nicht mit einem Muster ueber beides: Ein Regex, der von der
        // Deklaration bis zur Ueberschrift laeuft, ueberspannt den naechsten Zweig, sobald
        // einer seine Ueberschrift verliert - und schreibt sie dann dem falschen zu.
        List<String> gefunden = new ArrayList<>();
        for (int i = 0; i < namen.size(); i++) {
            int bis = i + 1 < anfaenge.size() ? anfaenge.get(i + 1) : quelle.length();
            var kopf = UEBERSCHRIFT.matcher(quelle.substring(anfaenge.get(i), bis));
            if (kopf.find()) {
                gefunden.add(namen.get(i) + " -> " + kopf.group(1));
            }
        }
        // Die LISTE in Quellreihenfolge und keine Menge: Genau der Tausch, den diese
        // Probe faengt, laesst die Menge unveraendert.
        assertEquals(KOPFPAARE_IN_SPIELZEITBEFEHLE, gefunden, "Vertauscht, liest wer"
                + " /freimarken spielzeit tippt seinen eigenen Namen in der Ueberschrift"
                + " - und wer nach einem anderen fragt, liest Deine Spielzeit ueber"
                + " fremden Zahlen. Die Zahlen in AUFRUFE_VON_SPIELZEITTEXT bleiben dabei"
                + " beide 1, und die Bildproben geben die Ueberschrift selbst herein.");
    }

    /** Die Deklaration einer reinen Methode, die Zeilen baut. */
    private static final Pattern ZEILENMETHODE = Pattern.compile(
            "static (?:List<Component>|Component) (\\w+)\\s*\\(");

    /**
     * Die Deklaration einer Satzmethode von {@code Spielzeittext}.
     *
     * <p>Weiter als {@link #ZEILENMETHODE}, weil die halbe Klasse {@code String}
     * zurueckgibt: Der Takt-Abstand und die vier Zahlformen werden in eine andere Zeile
     * EINGESETZT und sind keine Zeile fuer sich. Und ohne Ruecksicht auf {@code private},
     * damit {@code zustandZeile} mitgezaehlt wird.
     */
    private static final Pattern SATZMETHODE = Pattern.compile(
            "static (?:List<Component>|Component|String) (\\w+)\\s*\\(");

    /**
     * Die Deklaration eines Anzeigezweiges von {@code /freimarken spielzeit}.
     *
     * <p>Der Grossbuchstabe nach {@code zeige} trennt die zwei Zweige von der
     * gemeinsamen Sendemethode {@code zeige(...)}, die keine eigene Ueberschrift waehlt,
     * sondern die fertige entgegennimmt.
     */
    private static final Pattern ANZEIGEZWEIG = Pattern.compile(
            "void\\s+(zeige[A-Z]\\w*)\\s*\\(");

    /** Der Aufruf einer Ueberschrift - qualifiziert, weil er auswaerts steht. */
    private static final Pattern UEBERSCHRIFT = Pattern.compile(
            "Spielzeittext\\s*[.]\\s*(kopf\\w+)\\s*\\(");

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
