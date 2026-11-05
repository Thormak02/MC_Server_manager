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
 * <p><b>Der erste Teil von Schritt 5 brachte die Lehre, die eine Zaehlung nicht geben
 * kann.</b> {@code Reise} und {@code Aufwaermzeit} sind aus der Farbmenge heraus, aber
 * ihre Zahlen in {@link #SENDESTELLEN} sind gemessen GLEICH geblieben - sieben und zwei,
 * vorher wie nachher. Dagegen stehen hier sechs neue Tafeln in vier Fehlerklassen,
 * und jede faengt eine eigene, nachgewiesen durch je einen Eingriff, der sonst gruen
 * blieb:
 *
 * <ul>
 *   <li>{@link #AUFRUFE_IN_REISE} und {@link #AUFRUFE_IN_AUFWAERMZEIT} zaehlen auch die
 *       {@code sende...} und nicht nur die {@code ...Zeilen}. Das musste so sein, weil
 *       das {@code forEach} hier INNERHALB der Sendemethode steht: Ein ganz
 *       weggefallener Aufruf im Zweig laesst {@link #SENDESTELLEN} unveraendert, und der
 *       Spieler liest gar nichts.</li>
 *   <li>{@link #HINDERNISPAARE_IN_REISE} und {@link #ABBRUCHPAARE_IN_AUFWAERMZEIT} paaren
 *       eine WACHE mit ihrer Antwort. Beide Bereiche haben zwei Saetze, die sich in genau
 *       einem Wort unterscheiden; ein Tausch zwischen ihren Wachen laesst jede Zahl
 *       gleich, und {@code SendewegTest} geht die Aufzaehlung durch und sieht die
 *       Bedingung nicht.</li>
 *   <li>{@link #REISEPAARE_IN_ANFRAGENBEFEHLE} haelt die eine Stelle des Projekts, an der
 *       der Empfaenger eines Satzes und der darin genannte Name von ZWEI verschiedenen
 *       Spielern kommen - und seit dem Nachzug auch die zwei Ternaere darueber, die
 *       diese Richtung ausrechnen.</li>
 *   <li>{@link #ZWEIGPAARE_IN_REISE} paart einen ZWEIG mit dem Sendeweg, den er ruft.
 *       Das ist eine Lage HOEHER als die zwei Punkte darueber, und sie stand nirgends:
 *       Alle drei Sendewege von {@code Reise} tragen dieselbe Signatur und tauschen
 *       darum widerstandslos die Plaetze.</li>
 * </ul>
 *
 * <p><b>Der zweite Teil von Schritt 5 brauchte dagegen KEINE Paartafel, und das gehoert
 * aufgeschrieben, damit niemand eine leere nachbaut.</b> {@code WarpBefehle} und
 * {@code RueckwegBefehle} senden an GENAU EINEN Empfaenger, immer den Tipper - es gibt
 * keinen zweiten Spieler zu vertauschen. Und ihre vier Zweige waehlen Methoden mit
 * verschiedenen Signaturen, ein Tausch uebersetzt also nicht einmal. Die eine Falle dieser
 * zwei Dateien ist ein vertauschter WERT: In der Absage "Die Welt ... gibt es gerade
 * nicht." sind die getippte Eingabe und der aufgeloeste Weltname gleichzeitig greifbar und
 * vom selben Typ. Dagegen steht keine Tafel hier, sondern {@code AufrufstelleTest} 34r -
 * die Probe laeuft den ECHTEN Zweig mit {@code /warp KORNMARK} durch und liest mit, dass
 * "kornmark" im Satz steht. Diese zwei Befehle sind die ersten des Projekts, deren Zweige
 * ohne Server WIRKLICH aufrufbar sind: In allen Absagen fassen sie {@code Reise} nicht an.
 * Hier stehen darum nur zwei Zaehltafeln ({@link #AUFRUFE_IN_WARPBEFEHLE},
 * {@link #AUFRUFE_IN_RUECKWEGBEFEHLE}) fuer das eine, was eine durchlaufende Probe nicht
 * sieht: einen handgeschriebenen Satz mit demselben Wortlaut.
 *
 * <p><b>Gelesen wird der Quelltext OHNE Kommentare</b> ({@link Quellen#ohneKommentare}).
 * Vorher nahm {@link #jederSchluesselWirdGesendet} eine Nennung im Javadoc als Benutzung,
 * und ein Schluessel konnte aus dem Programm verschwinden, solange sein Name in einem
 * Kommentar stand. Dasselbe galt fuer jede Zaehlung hier: Ein Javadoc, das eine
 * Sendestelle zitiert, haette mitgezaehlt.
 */
class GrenzeTest {

    /**
     * Die Dateien, die JETZT noch Farbe nennen duerfen - nach Schritt 5, erster Teil.
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
     * <p>Mit dem ersten Teil von Schritt 5 sind wieder ZWEI heraus: {@code Reise.java}
     * und {@code Aufwaermzeit.java}, also das REISEN selbst. Sie waren die zwei Dateien,
     * die kein Befehl sind und durch die trotzdem jeder Teleport laeuft - ihre Saetze
     * stehen jetzt unter {@code reise} und gelten damit fuer alle fuenf Befehle, die sie
     * ausloesen. Hier lag auch die laengste Strecke ohne jeden Zeugen: Bis zu diesem
     * Schritt nannte KEINE Probe des Projekts einen ihrer vierzehn Wortlaute, und einen
     * Stellvertreter gibt es fuer sie nicht ({@code Aufwaermzeit} braucht einen
     * {@code JavaPlugin}, und das ist eine KLASSE). Der Massstab war allein der
     * committete Stand.
     *
     * <p>Mit dem ZWEITEN Teil von Schritt 5 sind die letzten zwei Reisedateien heraus:
     * {@code WarpBefehle.java} und {@code RueckwegBefehle.java}, also die zwei BEFEHLE
     * des Reisens. Damit stehen von den sieben Saetzen dieser zwei Dateien auch der
     * ZEILENBAU und die beiden TRENNER der Weltenliste in
     * {@code sprache-vorlage.yml}: {@code warp.ziele} sind drei Chatzeilen in einem
     * Schluessel, {@code warp.kein-ziel} zwei. Diese zwei Dateien waren ausserdem die
     * letzte Strecke dieses Bereichs ohne jeden Zeugen - bis zu diesem Zug nannte KEINE
     * Probe des Projekts einen ihrer Wortlaute.
     *
     * <p><b>Der Import zaehlt mit, und das ist hier der Grund fuer eine zweite
     * Zeile:</b> Von den elf bzw. drei Nennungen in {@code Reise} und
     * {@code Aufwaermzeit} war je eine das
     * {@code import net.kyori.adventure.text.format.NamedTextColor;}, und dasselbe gilt
     * fuer die sieben bzw. zwei in {@code WarpBefehle} und {@code RueckwegBefehle}. Wer
     * alle Farben aus dem Rumpf nimmt, den Import aber stehen laesst, bleibt in dieser
     * Menge, und die Probe bleibt gruen - gestrichen werden durfte ein Name erst, als
     * auch sein Import weg war.
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
            "Ankunftswache.java", "FreimarkenPlugin.java", "Portalwache.java",
            "Schutzwache.java", "Sprachstand.java"));

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
     * Wo ein Satz aus Text werden darf - zwei Dateien, und das ist der ganze Weg
     * nach Adventure.
     *
     * <p>{@code Sprache} baut die Saetze der Aufzaehlungen, {@code Sprachliste} die
     * Klagen ueber eine kaputte {@code sprache.yml}. Jede dritte Datei in dieser Menge
     * schreibt einen Satz selbst - siehe das Javadoc von {@link #MINIMESSAGE_LESEN}.
     */
    private static final Set<String> MINIMESSAGE_ERLAUBT = new TreeSet<>(List.of(
            "Sprache.java",
            "Sprachliste.java"));

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
     *
     * <p><b>{@code Reise} (7) und {@code Aufwaermzeit} (2) sind beim ersten Teil von
     * Schritt 5 GEMESSEN gleich geblieben</b>, und das ist der ganze Grund, warum dieser
     * Schritt sechs neue Tafeln braucht. Die neun handgeschriebenen Sendestellen sind
     * neun Aufrufe reiner Methoden geworden, einer je Stelle - keine ist weggefallen und
     * keine hinzugekommen. Eine Sieben bleibt aber eine Sieben, wenn ein Zweig die
     * falsche Methode waehlt oder seinen Satz wieder selbst schreibt.
     *
     * <p>Welcher ZWEIG welchen Sendeweg ruft, nagelt {@link #ZWEIGPAARE_IN_REISE} fest -
     * und diese Tafel war ein Nachzug, weil die Aufzaehlung hier den Fall "ein Zweig
     * waehlt die falsche Methode" zu decken BEHAUPTETE und ihn nicht deckte. Welche
     * Wache welches Hindernis waehlt, nagelt {@link #HINDERNISPAARE_IN_REISE} fest;
     * welches Ereignis welchen Abbruchgrund, {@link #ABBRUCHPAARE_IN_AUFWAERMZEIT}; und
     * wie oft jede Methode der Naht gerufen wird, {@link #AUFRUFE_IN_REISE} und
     * {@link #AUFRUFE_IN_AUFWAERMZEIT}.
     *
     * <p><b>{@code WarpBefehle} ist beim zweiten Teil von Schritt 5 von 6 auf 3
     * gefallen</b>, und das ist die Gegenprobe zum Absatz darueber: Hier war die Zahl
     * wirklich zu senken. Drei der sechs handgeschriebenen Sendungen waren die
     * Ueberschrift, die Liste und der Hinweis des nackten {@code /warp} - sie gehen immer
     * zusammen hinaus und sind EIN Satz mit zwei Umbruchmarken geworden; zwei weitere
     * waren die rote Absage und die graue Liste darunter, ebenfalls immer zusammen. Wer
     * die Zahl wieder steigen sieht, sieht einen Satz, der aus dem Zeilenbau der Datei
     * heraus und in das Programm zurueckgewandert ist. {@code RueckwegBefehle} bleibt
     * dabei auf 1: Es hatte nur eine Sendung und hat sie noch.
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
            Map.entry("WarpBefehle.java", 3));

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
     * Wie oft jede Methode der Naht von {@code Reise} AUFGERUFEN wird.
     *
     * <p>Dieselbe Fehlerklasse wie bei {@link #AUFRUFE_IN_PARZELLENBEFEHLE}, aber mit
     * einem Zusatz, den es dort nicht gibt: In dieser Tafel stehen nicht nur die
     * {@code ...Zeilen}, sondern auch die drei {@code sende...}. <b>Ohne sie waere ein
     * ganz WEGGEFALLENER Aufruf gruen</b>, und das ist gemessen: Nimmt man das
     * {@code sendeSchonDa(sprache, spieler, name)} aus dem Zweig heraus, bleibt
     * {@link #SENDESTELLEN} bei 7 - das {@code forEach} steckt ja INNERHALB von
     * {@code sendeSchonDa} und ist noch da. Ein Spieler tippt dann {@code /warp} in die
     * Welt, in der er steht, und liest GAR NICHTS.
     *
     * <p>Die 2 bei {@code hindernisZeilen} ist keine Dublette, sondern der Kern dieser
     * Datei: {@code Reise.pruefe} laeuft ZWEIMAL - vor der Wartezeit und wenn es losgeht
     * -, und beide Aufrufer schicken ihr Ergebnis durch dieselbe Methode. Faellt eine der
     * zwei weg, prueft die Reise nur noch einmal, und in den drei Sekunden dazwischen
     * kann der Boden abgebaut worden sein. Keine andere Probe faengt das: Gesendet wird
     * weiter, nur eben seltener.
     *
     * <p>Gezaehlt wird im eigenen Quelltext, wie in 31d und 31f, und die Deklaration
     * zaehlt nicht mit. Die Ruecksicht auf das Zeichen davor trennt
     * {@code zuSchnellZeilen} von {@code zuSchnellHintereinander}.
     */
    private static final Map<String, Integer> AUFRUFE_IN_REISE = Map.ofEntries(
            Map.entry("hindernisZeilen", 2),
            Map.entry("keinPlatzAmErscheinungspunktZeilen", 1),
            Map.entry("leineReisstZeilen", 1),
            Map.entry("nichtGeklapptZeilen", 1),
            Map.entry("schonDaZeilen", 1),
            Map.entry("sendeKeinPlatzAmErscheinungspunkt", 1),
            Map.entry("sendeNichtGeklappt", 1),
            Map.entry("sendeSchonDa", 1),
            Map.entry("zuSchnellZeilen", 1));

    /** Dasselbe fuer {@code Aufwaermzeit} - zwei Saetze, zwei Sendewege. */
    private static final Map<String, Integer> AUFRUFE_IN_AUFWAERMZEIT = Map.ofEntries(
            Map.entry("abbruchZeilen", 1),
            Map.entry("sendeAbbruch", 1),
            Map.entry("sendeWartezeit", 1),
            Map.entry("wartezeitZeilen", 1));

    /**
     * Dasselbe fuer {@code WarpBefehle} - drei Antworten, drei Aufrufe.
     *
     * <p>Hier ist die Tafel der NACHTRAG zu einer Zahl, die FALLEN musste:
     * {@link #SENDESTELLEN} ist fuer diese Datei von 6 auf 3 gegangen, weil zwei Gruppen
     * von Zeilen zu je einem Schluessel mit Umbruchmarken geworden sind. Eine 3 bleibt
     * aber eine 3, wenn ein Zweig statt des Aufrufs wieder sein eigenes
     * {@code spieler.sendMessage(Component.text(...))} schreibt: Beides ist genau EIN
     * Treffer im Sendemuster. Faellt eine Zahl hier auf 0, ist genau das geschehen - und
     * der Betreiber aendert danach seine Zeile in {@code sprache-vorlage.yml}, waehrend
     * im Spiel alles bleibt, wie es war.
     *
     * <p>Keine {@code sende...} in dieser Tafel, anders als bei {@link #AUFRUFE_IN_REISE}:
     * Hier steht das {@code forEach} im ZWEIG und nicht innerhalb einer Sendemethode, also
     * sieht {@link #SENDESTELLEN} einen ganz weggefallenen Aufruf schon selbst.
     */
    private static final Map<String, Integer> AUFRUFE_IN_WARPBEFEHLE = Map.ofEntries(
            Map.entry("keinZielZeilen", 1),
            Map.entry("weltFehltZeilen", 1),
            Map.entry("zieleZeilen", 1));

    /**
     * Dasselbe fuer {@code RueckwegBefehle} - EIN Satz und EIN Wortstueck.
     *
     * <p>{@code letzterOrtWort} steht mit darin, und das ist der eigentliche Grund fuer
     * diese Tafel: Es ist kein gesendeter Satz, sondern das {@code Art.WORT}, das als
     * Reiseziel in vier FREMDE Saetze geht. {@link #SENDESTELLEN} kann darueber nichts
     * sagen - die Zahl dieser Datei ist 1 und war 1 -, und {@link #SENDEWEG} sieht es
     * nicht, weil es nichts sendet. Faellt sein Aufruf weg und steht die Wendung wieder
     * als Zeichenkette im {@code reise.fuehre(...)}, faengt das sonst allein
     * {@link #jederSchluesselWirdGesendet}, und zwar mit der Meldung "Schluessel wird
     * nirgends benutzt" statt mit dem Namen der Stelle.
     */
    private static final Map<String, Integer> AUFRUFE_IN_RUECKWEGBEFEHLE = Map.ofEntries(
            Map.entry("keinOrtGemerktZeilen", 1),
            Map.entry("letzterOrtWort", 1));

    /**
     * Welche WACHE in {@code Reise.pruefe} welches {@code Hindernis} waehlt - gemessen.
     *
     * <p>Der Nagel, den eine Zaehltafel nicht schlagen kann, und der Grund steht in den
     * Saetzen selbst: Von den fuenf Hindernissen unterscheiden sich zwei in GENAU EINEM
     * Wort - "ausserhalb der Welthoehe" gegen "ausserhalb der Weltgrenze" -, und sie
     * teilen die ersten 57 Zeichen. <b>Vertauscht man die zwei zwischen ihren Wachen,
     * bleibt in {@link #AUFRUFE_IN_REISE} jede Zahl gleich</b>, denn gerufen wird weiter
     * genau zweimal {@code hindernisZeilen}; und {@code SendewegTest} 33ac bleibt
     * ebenfalls gruen, weil es die Aufzaehlung durchgeht und gar nicht sieht, welche
     * BEDINGUNG eine Konstante liefert. Im Spiel liest dann ein Spieler, der einen Ort
     * jenseits der Weltgrenze anspringt, der Ort liege ausserhalb der Welthoehe - und
     * sucht an der falschen Stelle.
     *
     * <p>Gepaart wird mit der BEDINGUNG und nicht bloss in Quellreihenfolge: Eine blosse
     * Liste der fuenf Konstanten waere auch dann gruen, wenn zwei ganze
     * {@code if}-Bloecke die Plaetze tauschen - was kein Fehler ist -, aber sie sagte
     * nichts darueber, WELCHE Frage welche Antwort bekommt. Der Name der gefragten
     * Methode ist dafuer der haltbarste Griff: {@code getMaxHeight} steht nur in der
     * Hoehenwache, {@code getWorldBorder} nur in der Grenzwache.
     */
    private static final List<String> HINDERNISPAARE_IN_REISE = List.of(
            "isWorldLoaded -> WELT_NICHT_GELADEN",
            "getVehicle -> ZU_PFERD",
            "getMaxHeight -> AUSSERHALB_DER_WELTHOEHE",
            "getWorldBorder -> AUSSERHALB_DER_WELTGRENZE",
            "taugt -> KEIN_PLATZ_MEHR");

    /**
     * Welches Ereignis von {@code Aufwaermzeit} welchen {@code Abbruchgrund} mitgibt.
     *
     * <p>Dieselbe Fehlerklasse wie eine Tafel hoeher, und dieselben zwei Saetze, die sich
     * nur im Anfang unterscheiden: Sie teilen die letzten 39 Zeichen
     * (" die Reise nach {ziel} ist abgebrochen."). Vertauscht liest ein Spieler, der
     * geschlagen wurde, "Bewegt" - und sucht einen Fehler in der Steuerung, wo ein Kampf
     * war.
     *
     * <p><b>Die Tafel traegt ZWEI Paare, und die Suche findet DREI Zweige.</b> Das ist
     * die zweite Zusage dieser Probe: {@code beimAbmelden} bricht ABSICHTLICH ohne
     * Meldung ab - es liest sie niemand mehr -, und dass dort kein dritter Grund
     * auftaucht, ist damit festgenagelt. Wer einen hinzufuegt, aendert das Spielbild und
     * bekommt eine rote Probe statt eines stummen Satzes an einen abgemeldeten Spieler.
     */
    private static final List<String> ABBRUCHPAARE_IN_AUFWAERMZEIT = List.of(
            "beimSchritt -> BEWEGT",
            "beiSchaden -> SCHADEN");

    /**
     * Welcher ZWEIG von {@code Reise.fuehre} welchen Sendeweg ruft - gemessen.
     *
     * <p>Die Luecke, die die Tafeln darueber offen liessen, und sie liegt eine Lage
     * HOEHER als sie: {@link #AUFRUFE_IN_REISE} zaehlt, WIE OFT jeder Sendeweg gerufen
     * wird, {@link #HINDERNISPAARE_IN_REISE} paart die Wachen INNERHALB von
     * {@code pruefe}. <b>Welcher Zweig welchen Sendeweg ruft, stand nirgends.</b> Alle
     * drei Sendewege tragen die Signatur {@code (Sprache, Player, String)} und tauschen
     * darum widerstandslos die Plaetze. Gemessen am 05.11.2026: zwei solche Tausche
     * liessen den ganzen Lauf gruen, je 836 Proben, keine rot.
     *
     * <p>Der schlimmere der zwei geht so: Ein Spieler, der schon in der Zielwelt steht,
     * liest "In kornmark ist am Erscheinungspunkt kein Platz zum Stehen. Das ist
     * nichts, was du beheben kannst - melde es." Und der echte Betreiberfehler meldet
     * sich als "Du bist schon in kornmark." - niemand meldet mehr etwas, und der
     * Erscheinungspunkt bleibt kaputt.
     *
     * <p>Gepaart wird ueber den Namen der gefragten WACHE, wie bei
     * {@link #HINDERNISPAARE_IN_REISE} und aus demselben Grund: Ein umgestellter
     * Vergleich macht diese Probe nicht rot, ein vertauschter Sendeweg schon.
     * {@code getWorld} steht in der Weltwache, {@code isEmpty} in der Ankunftswache,
     * {@code geklappt} in der Auswertung des Teleports.
     */
    private static final List<String> ZWEIGPAARE_IN_REISE = List.of(
            "getWorld -> sendeSchonDa",
            "isEmpty -> sendeKeinPlatzAmErscheinungspunkt",
            "geklappt -> sendeNichtGeklappt");

    /**
     * Wer reist und wessen Name genannt wird - an der einen Aufrufstelle mit ZWEI
     * Spielern.
     *
     * <p>{@code Reise} und {@code Aufwaermzeit} senden an GENAU EINEN Empfaenger, immer
     * den Reisenden. Innerhalb dieser zwei Dateien ist darum nichts zu vertauschen: ein
     * Wert, ein Leser. <b>Vertauschbar ist es allein an der Aufrufstelle in
     * {@code AnfragenBefehle}</b>, wo {@code /tpaccept} aus der Anfrage ausrechnet, wer
     * wandert und wer bleibt, und dann {@code reise.fuehre(wandert, bleibt.getLocation(),
     * bleibt.getName())} ruft: Gesendet wird an den WANDERNDEN, genannt wird der Name des
     * BLEIBENDEN. Vertauscht liest Anna "Nach 3 Sekunden geht es nach Anna" - ihren
     * eigenen Namen -, und ausserdem wird der falsche Spieler teleportiert.
     *
     * <p><b>Die zwei Ternaere stehen MIT in der Tafel, und das war ein Nachzug.</b> Der
     * Aufruf allein genuegt nicht: Dreht man zwei Zeilen hoeher die Zuweisungen,
     * bleibt {@code reise.fuehre(wandert, bleibt.getLocation(), bleibt.getName())}
     * buchstaeblich dasselbe - gemessen am 05.11.2026 lief der ganze Lauf dann gruen
     * durch, 836 Proben. Im Spiel wird danach der FALSCHE Spieler teleportiert.
     *
     * <p>Keine der sonstigen Proben faengt das. Der Zweig ist ohne laufenden Server nicht
     * aufrufbar ({@code Bukkit.getPlayer}, und fuer {@code Reise} gibt es keinen
     * Stellvertreter), {@code AufrufstelleTest} 34p/34q erreichen nur die Sendewege, und
     * jede Zaehltafel bleibt unveraendert - gerufen wird {@code fuehre} weiter genau
     * einmal. Darum liest diese Probe den Quelltext.
     *
     * <p><b>Nur EINE Zeile, und das ist Absicht.</b> Die vier anderen Aufrufer von
     * {@code fuehre} ({@code WarpBefehle}, {@code Torwache}, {@code RueckwegBefehle},
     * {@code FreimarkenPlugin}) geben je EINEN Spieler und eine Aufschrift mit, die
     * nicht von einem zweiten Spieler kommt - dort ist nichts zu tauschen. Eine Zeile
     * fuer sie waere eine Tafel ohne Fehlerklasse, und sie waere ausserdem rot geworden,
     * als der zweite Zug dieses Schrittes die Aufschrift von {@code /zurueck} in die
     * Sprachdatei geholt hat - darum traegt sie weiter nur die eine Zeile.
     */
    private static final List<String> REISEPAARE_IN_ANFRAGENBEFEHLE = List.of(
            "wandert = annehmerWandert ? annehmer : fragender",
            "bleibt = annehmerWandert ? fragender : annehmer",
            "wandert reist, Ort von bleibt, Name von bleibt");

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

    /**
     * Das LESEN eines Satzes aus MiniMessage - nicht die Farbe darin.
     *
     * <p>Zwei Fragen und darum zwei Muster: {@link #FARBE} bewacht den Namen einer
     * Adventure-Klasse, dieses hier die Stelle, an der aus Text ein Satz wird. Gemessen
     * am 05.11.2026 liess sich das auseinanderhalten - ein handgeschriebenes
     * {@code Sprache.MINIMESSAGE.deserialize("<yellow>...</yellow>")} mitten in
     * {@code Reise} liess {@link #FARBE} kalt, weil darin kein
     * {@code NamedTextColor} steht, und der ganze Lauf blieb gruen.
     *
     * <p><b>Das ist der Riegel gegen den stummen Rueckfall.</b> Wer einen Satz wieder
     * selbst schreibt und den Aufruf von {@code sprache.bau} daneben stehen laesst,
     * taeuscht jede Zaehltafel: Der Schluessel ist weiter genannt, die Zahlen stimmen,
     * die Bildproben sind gruen, weil der Text zeichengleich ist - und der Betreiber
     * aendert seine {@code sprache.yml}, ohne dass im Spiel etwas geschieht. Einen
     * Satz bauen kann man aber nicht, ohne ihn zu lesen.
     */
    private static final Pattern MINIMESSAGE_LESEN = Pattern.compile(
            "MiniMessage|deserialize");

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
        // Die Differenz VOR dem Vergleich: Ein assertEquals auf zwei Mengen von ueber
        // zwanzig Pfaden ist im Terminal abgeschnitten, und der Schuldige steht dann
        // irgendwo in der Mitte. Nach dem Nachzug von Schritt 5 traegt diese Probe mehr
        // Gewicht als vorher: Sie ist fuer die Saetze ohne durchlaufende Probe der eine
        // Waechter dafuer, dass der Schluessel ueberhaupt benutzt wird.
        Set<String> erlaubt = new TreeSet<>(Sprachschluessel.familienDeckung());
        Set<String> ueberzaehlig = new TreeSet<>(ohneNennung);
        ueberzaehlig.removeAll(erlaubt);
        assertEquals(erlaubt, ohneNennung, "nirgends im Programm genannt und nicht"
                + " erlaubt: " + ueberzaehlig + " - ein Satz, den kein Zweig holt,"
                + " steht in der Sprachdatei und wirkt nicht.");
    }

    @Test
    @DisplayName("31 - Farbe steht nur noch in DIESEN Dateien")
    void farbeNurNochInDiesenDateien() {
        assertEquals(FARBE_ERLAUBT, dateienMit(FARBE));
    }

    @Test
    @DisplayName("31q - ein Satz wird nur noch in DIESEN Dateien gelesen")
    void minimessageNurNochInDiesenDateien() {
        assertEquals(MINIMESSAGE_ERLAUBT, dateienMit(MINIMESSAGE_LESEN));
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

    @Test
    @DisplayName("31i - jede Methode der Naht von Reise wird genau so oft gerufen")
    void jedeNahtmethodeVonReiseWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_IN_REISE), nahtaufrufe("Reise.java"),
                "Eine Zahl auf 0 heisst, der Zweig schreibt den Satz wieder selbst ODER"
                        + " der Aufruf ist ganz weggefallen - und im zweiten Fall liest"
                        + " der Spieler GAR NICHTS, waehrend die Zahl in SENDESTELLEN bei"
                        + " 7 bleibt, weil das forEach innerhalb der Sendemethode steht."
                        + " Die 2 bei hindernisZeilen ist kein Versehen: Reise.pruefe"
                        + " laeuft zweimal, vor der Wartezeit und wenn es losgeht."
                        + " Welche der zwei weggefallen ist, sagt diese Tafel NICHT -"
                        + " beide ergeben hindernisZeilen=1; faellt die zweite weg,"
                        + " prueft die Reise den Zielort nicht mehr, nachdem drei"
                        + " Sekunden vergangen sind.");
    }

    @Test
    @DisplayName("31j - jede Methode der Naht von Aufwaermzeit wird genau so oft gerufen")
    void jedeNahtmethodeVonAufwaermzeitWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_IN_AUFWAERMZEIT),
                nahtaufrufe("Aufwaermzeit.java"), "Dieselbe Fehlerklasse wie in 31i:"
                        + " Eine 0 heisst, der Zweig schreibt den Satz wieder selbst"
                        + " ODER der Aufruf ist ganz weggefallen - WELCHER Eintrag es"
                        + " ist, steht im Vergleich darunter und nicht in diesem Satz."
                        + " Keine andere Probe wird dabei rot.");
    }

    @Test
    @DisplayName("31n - jede Methode der Naht von WarpBefehle wird genau so oft gerufen")
    void jedeNahtmethodeVonWarpBefehlenWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_IN_WARPBEFEHLE),
                nahtaufrufe("WarpBefehle.java"), "Die Zahl in SENDESTELLEN ist fuer diese"
                        + " Datei von 6 auf 3 gefallen, weil zwei Gruppen von Zeilen zu je"
                        + " einem Schluessel mit Umbruchmarken geworden sind. Eine 3"
                        + " bleibt aber eine 3, wenn ein Zweig wieder sein eigenes"
                        + " Component.text schreibt - dann steht hier eine 0, und der"
                        + " Betreiber aendert seine Zeile in der Sprachdatei, waehrend im"
                        + " Spiel nichts geschieht.");
    }

    @Test
    @DisplayName("31o - jede Methode der Naht von RueckwegBefehle wird genau so oft"
            + " gerufen")
    void jedeNahtmethodeVonRueckwegBefehlenWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_IN_RUECKWEGBEFEHLE),
                nahtaufrufe("RueckwegBefehle.java"), "letzterOrtWort ist der Grund fuer"
                        + " diese Tafel: Es sendet nichts, also sagt SENDESTELLEN (1,"
                        + " vorher wie nachher) darueber nichts, und das Sendemuster"
                        + " sieht es nicht. Faellt sein Aufruf weg, steht die Aufschrift"
                        + " deinen letzten Ort wieder als Zeichenkette im Programm.");
    }

    @Test
    @DisplayName("31p - jeder Zweig von Reise ruft seinen eigenen Sendeweg")
    void jederZweigRuftSeinenEigenenSendeweg() {
        String quelle = Quellen.text(datei("Reise.java"));
        List<String> gefunden = new ArrayList<>();
        var paar = ZWEIGWACHE.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " -> sende" + paar.group(2));
        }
        // Die LISTE in Quellreihenfolge und keine Menge: Genau der Tausch, den diese
        // Probe faengt, laesst die Menge unveraendert.
        assertEquals(ZWEIGPAARE_IN_REISE, gefunden, "Vertauscht liest ein Spieler, der"
                + " schon in der Zielwelt steht, den Satz fuer den kaputten"
                + " Erscheinungspunkt - und der echte Betreiberfehler meldet sich als"
                + " Du bist schon in kornmark, sodass ihn niemand mehr meldet. Die"
                + " Zahlen in AUFRUFE_IN_REISE bleiben dabei alle gleich: gerufen wird"
                + " jeder Sendeweg weiter genau einmal.");
    }

    @Test
    @DisplayName("31k - jede Wache von Reise waehlt ihr eigenes Hindernis")
    void jedeWacheWaehltIhrEigenesHindernis() {
        String quelle = Quellen.text(datei("Reise.java"));
        List<String> gefunden = new ArrayList<>();
        var paar = HINDERNISWACHE.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " -> " + paar.group(2));
        }
        // Die LISTE in Quellreihenfolge und keine Menge: Eine Menge waere auch dann
        // vollstaendig, wenn eine Wache zweimal dastuende und eine andere gar nicht.
        assertEquals(HINDERNISPAARE_IN_REISE, gefunden, "Vertauscht liest ein Spieler,"
                + " der einen Ort jenseits der Weltgrenze anspringt, der Ort liege"
                + " ausserhalb der Welthoehe - die zwei Saetze teilen 57 Zeichen und"
                + " unterscheiden sich in einem Wort. Die Zahlen in AUFRUFE_IN_REISE"
                + " bleiben dabei gleich, und SendewegTest 33ac geht die Aufzaehlung"
                + " durch und sieht die Bedingung gar nicht.");
    }

    @Test
    @DisplayName("31l - jedes Ereignis der Aufwaermzeit gibt seinen eigenen Abbruchgrund")
    void jedesEreignisGibtSeinenEigenenAbbruchgrund() {
        String quelle = Quellen.text(datei("Aufwaermzeit.java"));
        List<String> namen = new ArrayList<>();
        List<Integer> anfaenge = new ArrayList<>();
        var zweig = ABBRUCHZWEIG.matcher(quelle);
        while (zweig.find()) {
            namen.add(zweig.group(1));
            anfaenge.add(zweig.start());
        }
        // Das Abmelden MUSS mitgelesen werden, denn die zweite Zusage dieser Probe ist,
        // dass es stumm bleibt. Ohne diese Zeile waere eine Suche, die es uebersieht,
        // gruen - und ein dort neu hinzugefuegter Grund fiele niemandem auf.
        assertTrue(namen.contains("beimAbmelden"), "die Suche hat das Abmelden nicht"
                + " gefunden - dann sagt diese Probe nichts darueber, dass es stumm"
                + " bleibt: " + namen);
        // Abschnittsweise und nicht mit einem Muster ueber alles, genau wie in 31h: Ein
        // Regex von der Deklaration bis zur Wahl ueberspannt den naechsten Zweig, sobald
        // einer seine Wahl verliert - und schreibt sie dann dem falschen zu.
        List<String> gefunden = new ArrayList<>();
        for (int i = 0; i < namen.size(); i++) {
            int bis = i + 1 < anfaenge.size() ? anfaenge.get(i + 1) : quelle.length();
            var wahl = ABBRUCHWAHL.matcher(quelle.substring(anfaenge.get(i), bis));
            if (wahl.find()) {
                gefunden.add(namen.get(i) + " -> " + wahl.group(1));
            }
        }
        assertEquals(ABBRUCHPAARE_IN_AUFWAERMZEIT, gefunden, "Zwei Paare bei drei"
                + " Zweigen: Vertauscht liest ein geschlagener Spieler Bewegt und sucht"
                + " den Fehler in seiner Steuerung. Ein DRITTES Paar heisst, dass das"
                + " Abmelden jetzt eine Meldung schickt - an einen Spieler, der nicht"
                + " mehr da ist.");
    }

    @Test
    @DisplayName("31m - bei /tpaccept reist der eine, und genannt wird der andere")
    void derReisendeUndDerGenannteSindNichtVertauscht() {
        String quelle = Quellen.text(datei("AnfragenBefehle.java"));
        List<String> gefunden = new ArrayList<>();
        // Erst die RICHTUNG, dann die Reise - in dieser Reihenfolge stehen sie auch im
        // Quelltext. Der Aufruf allein genuegt nicht: Dreht man die zwei Ternaere,
        // bleibt er buchstaeblich derselbe.
        var richtung = REISERICHTUNG.matcher(quelle);
        while (richtung.find()) {
            gefunden.add(richtung.group(1) + " = annehmerWandert ? "
                    + richtung.group(2) + " : " + richtung.group(3));
        }
        var paar = REISEPAAR.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " reist, Ort von " + paar.group(2)
                    + ", Name von " + paar.group(3));
        }
        assertEquals(3, gefunden.size(), "gesucht werden DREI Stellen - die zwei"
                + " Ternaere, die die Richtung ausrechnen, und der Aufruf, der sie"
                + " benutzt. Fehlt eine, hat ein Muster sie nicht gefunden, und dann"
                + " waere diese Probe gruen, ohne sie gesehen zu haben. Ist der Code"
                + " umgestellt worden, gehoert das Muster nachgezogen und nicht die"
                + " Probe gestrichen.");
        assertEquals(REISEPAARE_IN_ANFRAGENBEFEHLE, gefunden, "Gesendet wird an den"
                + " WANDERNDEN, genannt wird der Name des BLEIBENDEN. Vertauscht liest"
                + " Anna ihren eigenen Namen in Nach 3 Sekunden geht es nach ... - und"
                + " teleportiert wird der falsche Spieler. Keine Zaehltafel sieht das:"
                + " fuehre wird weiter genau einmal gerufen.");
    }

    /**
     * Wie oft jede Methode der Naht einer Datei in ihr selbst gerufen wird.
     *
     * <p>Zwei Proben teilen sich diesen Weg (31i und 31j), damit die zweite nicht eine
     * Abschrift der ersten ist. Gezaehlt werden die {@code ...Zeilen} UND die
     * {@code sende...}: Ohne die zweiten waere ein ganz weggefallener Aufruf gruen, weil
     * das {@code forEach} innerhalb der Sendemethode steht und {@link #SENDESTELLEN}
     * damit unveraendert bleibt.
     */
    private static Map<String, Integer> nahtaufrufe(String name) {
        String quelle = Quellen.text(datei(name));
        Map<String, Integer> gezaehlt = new TreeMap<>();
        var deklaration = NAHTMETHODE.matcher(quelle);
        while (deklaration.find()) {
            String methode = deklaration.group(1);
            // Minus eins: die Deklaration selbst. Die Ruecksicht auf das Zeichen davor
            // haelt zuSchnellZeilen von zuSchnellHintereinander getrennt - genau wie in
            // 31d bodenZeilen von gruppenbodenZeilen.
            gezaehlt.put(methode, treffer(Pattern.compile(
                    "(?<![\\w$.])" + methode + "\\s*\\("), quelle) - 1);
        }
        assertFalse(gezaehlt.isEmpty(), "die Suche hat die Naht von " + name + " nicht"
                + " gefunden - dann waere diese Tafel gruen, ohne etwas gesehen zu haben");
        return gezaehlt;
    }

    /**
     * Die Deklaration einer Methode der Naht - eine Zeilen-Methode oder ein Sendeweg.
     *
     * <p>Weiter als {@link #ZEILENMETHODE}, und zwar um die {@code sende...}: Fuer
     * {@code Reise} und {@code Aufwaermzeit} liegt das {@code forEach} INNERHALB dieser
     * Methoden, also sagt {@link #SENDESTELLEN} nichts darueber, ob sie ueberhaupt noch
     * gerufen werden. Erkannt wird an der Namenskonvention und nicht am Rueckgabetyp -
     * {@code static void} allein traefe auch jede andere stille Methode.
     *
     * <p>Die dritte Form, {@code ...Wort}, kam mit {@code RueckwegBefehle.letzterOrtWort}
     * dazu: Ein Griff, der ein {@code Art.WORT} aus der Datei holt, damit es als WERT in
     * einen fremden Satz geht. Er sendet nichts und baut keine Zeile, ist aber dieselbe
     * Naht - und ohne ihn in dieser Suche saehe keine Tafel, dass er noch gerufen wird.
     * Der Name folgt {@code ParzellenBefehle.preisWort}. Dass {@code Reise} und
     * {@code Aufwaermzeit} heute keinen solchen Griff haben, macht diese Erweiterung fuer
     * sie zu einer blossen VERSCHAERFUNG: Entsteht dort einer, sind 31i oder 31j rot, bis
     * er in der Tafel steht.
     */
    private static final Pattern NAHTMETHODE = Pattern.compile(
            "static [\\w<>]+ (\\w+Zeilen|\\w+Wort|sende[A-Z]\\w*)\\s*\\(");

    /**
     * Eine Wache von {@code Reise.pruefe} mit dem Hindernis, das sie liefert.
     *
     * <p>{@code [^;]} und nicht {@code .}, aus demselben Grund wie bei
     * {@link #SENDEPAAR}: So kann ein Treffer nicht von einer Wache in die Antwort der
     * naechsten laufen - zwischen zwei Wachen steht immer ein Semikolon, naemlich das
     * der Rueckgabe.
     *
     * <p>Gefragt wird nach dem Namen der METHODE, die die Wache aufruft, und nicht nach
     * der ganzen Bedingung: {@code getMaxHeight} steht nur in der Hoehenwache,
     * {@code getWorldBorder} nur in der Grenzwache, und ein umgestellter Vergleich macht
     * diese Probe damit nicht rot. {@code getMaxHeight} und nicht
     * {@code getMinHeight}, weil die Hoehenwache beide nennt und der zweite der naehere
     * ist.
     */
    private static final Pattern HINDERNISWACHE = Pattern.compile(
            "(isWorldLoaded|getVehicle|getMaxHeight|getWorldBorder|taugt)[^;]*?"
                    + "return Optional[.]of\\(Hindernis[.](\\w+)\\)");

    /**
     * Die Wache eines Zweiges von {@code Reise.fuehre} mit dem Sendeweg, den sie ruft.
     *
     * <p>{@code [^;]} wie bei {@link #HINDERNISWACHE} und aus demselben Grund: So kann
     * ein Treffer keine zwei Anweisungen ueberspannen. Der Kommentar, der in der
     * Auswertung des Teleports zwischen der Wache und dem Aufruf steht, stoert dabei
     * nicht - er traegt kein Semikolon.
     */
    private static final Pattern ZWEIGWACHE = Pattern.compile(
            "(getWorld|isEmpty|geklappt)[^;]*?sende(\\w+)\\s*\\(");

    /** Ein Ereignis der {@code Aufwaermzeit} - die zwei, die abbrechen, und das stumme. */
    private static final Pattern ABBRUCHZWEIG = Pattern.compile(
            "void\\s+(bei\\w+)\\s*\\(");

    /**
     * Die Wahl eines Abbruchgrundes - QUALIFIZIERT, also mit Punkt.
     *
     * <p>Damit trifft dieses Muster weder die Aufzaehlung selbst noch den Parameter
     * {@code Abbruchgrund grund}, sondern nur die Stelle, an der ein Zweig sich fuer
     * einen entscheidet.
     */
    private static final Pattern ABBRUCHWAHL = Pattern.compile(
            "Abbruchgrund[.](\\w+)");

    /** Die Weiterreise bei {@code /tpaccept}: Empfaenger, Ort und Name in einem Aufruf. */
    private static final Pattern REISEPAAR = Pattern.compile(
            "reise\\s*[.]\\s*fuehre\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*[.]\\s*"
                    + "getLocation\\s*\\(\\s*\\)\\s*,\\s*(\\w+)\\s*[.]\\s*getName"
                    + "\\s*\\(\\s*\\)\\s*\\)");

    /**
     * Die Richtung von {@code /tpaccept}: wer wandert und wer bleibt.
     *
     * <p>Zwei Zeilen und nicht eine, weil genau ihr Tausch der Fehler ist. Der Name
     * der Bedingung steht mit im Muster: {@code annehmerWandert} ist die eine Stelle,
     * an der die Richtung entschieden wird, und ein zweiter Ternaer mit einem anderen
     * Namen waere eine zweite Stelle - die soll diese Probe sehen.
     */
    private static final Pattern REISERICHTUNG = Pattern.compile(
            "Player\\s+(wandert|bleibt)\\s*=\\s*annehmerWandert\\s*[?]"
                    + "\\s*(\\w+)\\s*:\\s*(\\w+)\\s*;");

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
