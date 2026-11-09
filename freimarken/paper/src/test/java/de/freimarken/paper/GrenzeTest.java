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
 * <p><b>Schritt 6 brachte die Lage, in der eine Zaehltafel von vornherein nichts
 * sagen KANN:</b> {@code Schutzwache} hat VIER Wortlaute und genau EINE Sendestelle.
 * Ihre Zahl in {@link #SENDESTELLEN} ist darum gemessen gleich geblieben - eine Eins,
 * vorher wie nachher -, und sie bleibt eine Eins, was auch vertauscht wird. Dagegen
 * steht fuer die Wahl des Wortlauts EINE Tafel, {@link #WACHENPAARE_IN_SCHUTZWACHE},
 * und sie traegt zwei Fehlerklassen in vier Zeilen: welche Wache welchen Satz waehlt
 * (mit BEIDEN Seiten des Grenzternaers, denn genau dessen Drehung lief in Schritt 5
 * gruen durch) und woher der genannte NAME kommt. Beide sind durch je einen Eingriff
 * belegt, der sonst gruen blieb.
 *
 * <p>Fuer den ganzen Schritt wurden es mehr: {@link #SENDEPAARE_IN_ANKUNFTSWACHE} fuer
 * die zweite Wache, vier Muster fuer ihre Formen, und im Nachzug noch
 * {@link #ARTEN_IN_WACHESATZ}, {@link #DROSSEL_UND_BALKEN_IN_SCHUTZWACHE} und
 * {@link #FARBTAG_ERLAUBT} - jede fuer einen Eingriff, der ohne sie gruen durchlief.
 *
 * <p><b>Der zweite Zug von Schritt 6 zeigte dieselbe Luecke bei ZWEI Sendestellen</b>,
 * und das ist die Lehre daraus: Nicht die ZAHL der Sendestellen entscheidet, ob eine
 * Zaehltafel genuegt, sondern ob zwei Saetze derselben Datei vertauschbar sind.
 * {@code Ankunftswache} hat zwei Saetze und zwei Sendestellen - gemessen zwei vorher
 * und zwei nachher -, aber beide sind grau, gehen an denselben Empfaengertyp und ihre
 * Methoden tragen dieselbe Signatur. Dagegen steht {@link #SENDEPAARE_IN_ANKUNFTSWACHE},
 * belegt durch einen Eingriff, bei dem ausser 31s keine einzige Probe rot wurde.
 * {@code Portalwache} brauchte dagegen KEINE Paartafel: Seine zwei Chatzeilen sind EIN
 * Schluessel mit einer Umbruchmarke geworden, es gibt also nur noch einen Satz, und
 * seine Zahl hier ist von 2 auf 1 gefallen. Was dort zu vertauschen bliebe - die
 * Reihenfolge der zwei Zeilen - liegt jetzt in der Datei und wird von
 * {@code BildGleichTest} 11 in EINER Tabellenzeile gehalten.
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
     * <p>Mit dem ersten Zug von Schritt 6 ist {@code Schutzwache.java} heraus - die
     * erste WACHE und damit der erste Text des Projekts, den ein Spieler liest, ohne
     * etwas getippt zu haben. Seine vier Wortlaute stehen jetzt unter
     * {@code wache.schutz}, und mit ihnen der erste Farbwechsel MITTEN in einem
     * Handbalken. {@code Schutztext.java} war dabei nie in dieser Menge: Die Naht
     * dieses Zuges kam schon ohne Farbe auf die Welt, weil der Umzug im selben Zug
     * folgte. Dass sie eine EIGENE Datei ist, war gemessen und keine Geschmacksfrage -
     * der Klasseninitialisierer der Wache fragt {@code Bukkit.server}, und eine Probe,
     * die eine reine Methode dort aufruft, stirbt im
     * {@code ExceptionInInitializerError}.
     *
     * <p>Mit dem zweiten Zug von Schritt 6 sind die letzten zwei Wachen heraus:
     * {@code Ankunftswache.java} und {@code Portalwache.java}. <b>Damit nennt keine der
     * drei Wachen mehr eine Farbe</b> - und was ein Spieler liest, ohne etwas getippt zu
     * haben, kommt vollstaendig aus der Datei. Dass {@code FreimarkenPlugin.java} weiter
     * Farbe nennt, ist kein Widerspruch dazu: Seine Spielersaetze sind mit
     * Bauschritt 7 umgezogen und mit dem Auszug am 06.11.2026 auch sein
     * Befehlsbaum - was dort noch Farbe nennt, ist Betreibertext und bleibt.
     * Die drei Saetze dieses Zuges stehen unter
     * {@code wache.ankunft} und {@code wache.portal}, und mit dem am Portal auch der
     * ZEILENBAU: Zwei Chatzeilen in einem Schluessel, getrennt von einer Umbruchmarke.
     * Von den je drei Nennungen in beiden Dateien war wieder je eine das
     * {@code import net.kyori.adventure.text.format.NamedTextColor;} - gestrichen
     * werden durfte ein Name erst, als auch sein Import weg war.
     *
     * <p>{@code FreimarkenPlugin.java} bleibt ebenfalls darin, und das ist ein Loch, das
     * offen bleibt - nur nicht unbewacht. Seine Betreiberzeilen muessen dort bleiben, also
     * faengt diese Probe einen dort neu geschriebenen Spielersatz nicht. Dafuer gibt es
     * die Tafel in {@link #jedeDateiSendetGenauSoOft}: ein Tor und kein Beweis.
     *
     * <p><b>Das Loch ist am 06.11.2026 kleiner geworden.</b> Der Befehlsbaum von
     * {@code /freimarken} steht seitdem in {@code BundBefehle.java}, und DIE Datei
     * ist nicht in dieser Menge - ein Farbname dort macht diese Probe sofort rot.
     * Im Plugin bleiben die Betreiberzeilen und die vier Verwaltungszweige, die sie
     * senden; was ein SPIELER liest, liegt nicht mehr dort.
     */
    private static final Set<String> FARBE_ERLAUBT = new TreeSet<>(List.of(
            "FreimarkenPlugin.java", "Sprachstand.java"));

    /**
     * Wo ein Farbtag als ZEICHEN stehen darf - eine Datei, und dort ist er eine
     * Fehlermeldung.
     *
     * <p>{@code Sprachliste} zaehlt dem Betreiber auf, welche Tags er benutzen darf
     * ({@code <gray> <dark_gray> <white> ...}) und welche nicht ({@code <click>},
     * {@code <hover>}). Die stehen dort als Text fuer einen Menschen und gehen durch
     * keinen Satz. Jede zweite Datei in dieser Menge spleisst ein Tag in einen Wert.
     */
    private static final Set<String> FARBTAG_ERLAUBT = new TreeSet<>(List.of(
            "Sprachliste.java"));

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
    // Die Verdienstwache steht hier mit Absicht NICHT: Sie sendet ueber
    // Sprache.sendeHandbalken und damit nicht selbst - jene Methode hatte bis Teil E
    // keinen einzigen Aufrufer und zaehlt in Sprache.java mit. Wer sie eines Tages durch
    // ein eigenes sendActionBar ersetzt, wird von dieser Tafel gefragt, warum.
    private static final Set<String> SENDEN_ERLAUBT = new TreeSet<>(List.of(
            "AnfragenBefehle.java", "Ankunftswache.java", "Aufwaermzeit.java",
            "BerufeBefehle.java", "Bonuswache.java",
            "BundBefehle.java", "LadenBefehle.java", "FreimarkenPlugin.java", "MarktBefehle.java",
            // Teil C: die zwei Beitrittszeilen und die Zeilen des taeglichen
            // Laufs - alle durch dasselbe private sende().
            "Mietwache.java",
            "ParzellenBefehle.java", "Portalwache.java",
            "Reise.java", "RueckwegBefehle.java", "Schutzwache.java",
            // Plan 7.4: die Zeilen des Anlegens und die Handbalken des Schutzes -
            // zwei Wege, weil Chat und Handbalken zwei verschiedene Aufrufe sind.
            "Shopwache.java",
            // Plan 7.4, der HANDEL: ein sendActionBar im Klickzweig. Ein Handel
            // redet nie im Chat - ein Spieler, der zwanzig Stapel kauft, klickt
            // zwanzigmal, und zwanzig Chatzeilen waeren ein Regen.
            "Shopklick.java",
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
     *
     * <p><b>{@code Schutzwache} ist im ersten Zug von Schritt 6 gemessen bei 1
     * geblieben</b>, und diese Eins ist der Grund fuer {@link #WACHENPAARE_IN_SCHUTZWACHE}:
     * Die Wache holt ihren Handbalken jetzt bei {@link Sprache#eineZeile}, schickt
     * ihn aber weiter selbst hinaus. Vier Wortlaute hinter einer Zahl heisst, dass
     * diese Tafel ueber den Umzug gar nichts sagt. {@code Schutztext.java} steht
     * hier NICHT: Es baut die Zeile und gibt sie zurueck, gesendet wird sie vom
     * Aufrufer - genau so ist sie ohne Server pruefbar.
     *
     * <p><b>Der zweite Zug von Schritt 6 hat die zwei Zahlen VERSCHIEDEN bewegt, und
     * das ist dieselbe Entscheidung, nur zweimal anders ausgefallen.</b>
     * {@code Portalwache} ist von 2 auf 1 gefallen: Die rote Absage und der graue Weg
     * darunter gehen immer zusammen hinaus - zwischen den zwei Sendungen steht kein
     * {@code if}, kein {@code return} und keine Schleife, die Abklingzeit wird EINMAL
     * davor gefragt. Sie sind EIN Satz mit einer Umbruchmarke geworden, dieselbe
     * Senkung wie bei {@code warp.kein-ziel}. {@code Ankunftswache} ist dagegen bei 2
     * geblieben: Ihre zwei Saetze haengen an zwei verschiedenen Ereignissen - einem
     * gesetzten Spawnpunkt und einer betretenen Abenteuerwelt - und gehen nie zusammen
     * hinaus. Wer die Eins am Portal wieder steigen sieht, sieht einen Satz, der aus
     * dem Zeilenbau der Datei heraus und in das Programm zurueckgewandert ist; und
     * dass die Zwei der Ankunftswache nichts darueber sagt, WELCHES Ereignis welchen
     * Satz waehlt, ist der Grund fuer {@link #SENDEPAARE_IN_ANKUNFTSWACHE}.
     */
    private static final Map<String, Integer> SENDESTELLEN = Map.ofEntries(
            Map.entry("AnfragenBefehle.java", 11),
            Map.entry("Ankunftswache.java", 2),
            Map.entry("Aufwaermzeit.java", 2),
            // EINS, und das ist eine Entscheidung: Alle fuenf Zweige von /beruf laufen
            // durch denselben Sendeweg. Der Listenzweig sendete anfangs selbst - dann
            // stuende hier eine Zwei, und eine zweite Stelle koennte wegfallen, ohne dass
            // die Zahl faellt.
            Map.entry("BerufeBefehle.java", 1),
            // Teil H: EINE Sendestelle - die Nachricht ueber den gezahlten Bonus.
            Map.entry("Bonuswache.java", 1),
            Map.entry("BundBefehle.java", 2),
            Map.entry("FreimarkenPlugin.java", 40),
            // EINE Sendestelle, und das BLEIBT so, obwohl Teil B drei Taten dazugebracht
            // hat: Alle vier Zweige von /laden laufen durch dasselbe sende(). Eine Zwei
            // hiesse, dass ein Zweig sich einen eigenen Weg gebaut hat - und der waere
            // der erste, dessen Zeilen keine Tafel haelt.
            Map.entry("LadenBefehle.java", 1),
            // ZWEI: die Zeile fuer die Konsole (sie hat keine Hand und steht in keiner
            // Welt) und der eine Sendeweg fuer alle Spielerzeilen. Eine Zahl weniger
            // hiesse, dass ein Zweig von /markt stumm bleibt; eine mehr, dass neben
            // Markttext eine zweite Sendestelle gebaut wurde.
            Map.entry("MarktBefehle.java", 2),
            // Teil C: EINE Stelle fuer drei Zweige - Beitritt (zweimal) und Takt gehen
            // alle durch dasselbe private sende(). Haette jeder Zweig sein eigenes,
            // stuende hier eine 3, und eine verlorene Sendung waere nicht zu sehen.
            Map.entry("Mietwache.java", 1),
            Map.entry("ParzellenBefehle.java", 44),
            Map.entry("Portalwache.java", 1),
            Map.entry("Reise.java", 7),
            Map.entry("RueckwegBefehle.java", 1),
            Map.entry("Schutzwache.java", 1),
            // Plan 7.4: ZWEI, und beide sind eine Entscheidung. Die erste ist das private
            // sende() fuer alle Chatzeilen - Anlegen, Abbauen und jede Absage laufen
            // durch dieselbe Stelle, so wie bei LadenBefehle und Mietwache. Die zweite
            // ist sendActionBar in melde(), und sie MUSS getrennt sein: Ein Handbalken
            // nimmt ein Component und keine Liste, und er geht durch die Drossel.
            // Eine Drei hiesse, dass ein Zweig sich einen eigenen Weg gebaut hat.
            Map.entry("Shopwache.java", 2),
            // Plan 7.4, der HANDEL: EINE Sendestelle, und sie ist ein
            // sendActionBar. Eine Zwei hiesse, dass der Handel sich einen Weg in
            // den Chat gebaut hat - und damit die Entscheidung umgedreht, dass ein
            // Klick im Handbalken antwortet.
            Map.entry("Shopklick.java", 1),
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
            // Drei davon stehen in Beruftext: die Restfrist im Stand, die ganze
            // Frist in der Warnung und die Restfrist in der Absage. Dieselbe Rechnung
            // und nicht eine eigene - sonst liest ein Spieler an einer Stelle
            // "2 h 05 min" und an der anderen "2 Stunden" fuer dieselbe Frist.
            Map.entry("dauer", 8),
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
     * Welche Wache der {@code Schutzwache} welchen Handbalken waehlt - in
     * Quellreihenfolge, gemessen.
     *
     * <p><b>VIER Wortlaute hinter EINER Sendestelle</b>, und das ist genau die Lage,
     * die eine Zaehltafel nicht deckt: {@link #SENDESTELLEN} fuehrt {@code Schutzwache}
     * mit einer Eins, vorher wie nachher, und eine Eins bleibt eine Eins, wenn zwei
     * Gruende die Plaetze tauschen. Alle vier Methoden der Naht tragen ausserdem fast
     * dieselbe Signatur ({@code (Sprache)} dreimal), tauschen also widerstandslos.
     *
     * <p><b>Der Grenzternaer steht mit ZWEI Zeilen darin, und das ist die Lehre aus
     * Schritt 5.</b> Dort liess sich ein Ternaer drehen, ohne dass eine Probe rot wurde;
     * eine Zeile fuer die Wache allein saehe nur die DANN-Seite. Vertauscht liest ein
     * Spieler auf einer freien Parzelle innerhalb der Stadt "Hier endet die
     * Marktstadt" - und draussen, wo nie etwas vergeben wird, die Aufforderung, sie
     * mit {@code /parzelle nehmen} zu holen.
     *
     * <p><b>Die letzte Zeile ist die ANDERE Frage: woher der Name kommt.</b> Der
     * Spieler liest den Namen des EIGENTUEMERS und nicht seinen eigenen - dieselbe
     * Fehlerklasse wie {@link #REISEPAARE_IN_ANFRAGENBEFEHLE}, nur spiegelbildlich.
     * Heute ist der Tausch in {@code grund} nicht moeglich, weil dort gar kein
     * {@code Player} im Blickfeld steht; diese Zeile nagelt fest, dass es so bleibt.
     * Wer die Naht einen Leser mitgeben laesst und {@code spieler.getName()} einsetzt,
     * verliert diesen Treffer und wird rot - ein Besucher laese sonst seinen eigenen
     * Namen und hielte die Parzelle fuer seine.
     *
     * <p>Gepaart wird ueber den Namen der gefragten METHODE, wie bei
     * {@link #HINDERNISPAARE_IN_REISE} und aus demselben Grund: {@code feldBei} steht
     * nur in der Wegwache, {@code enthaelt} nur in der Grenzwache, und ein umgestellter
     * Vergleich macht diese Probe nicht rot.
     */
    private static final List<String> WACHENPAARE_IN_SCHUTZWACHE = List.of(
            "feldBei -> wegUndMauerBalken",
            "enthaelt/dann -> parzelleFreiBalken",
            "enthaelt/sonst -> ausserhalbBalken",
            "fremdeParzelleBalken <- eigentuemer()");

    /**
     * Welches Ereignis der {@code Ankunftswache} welchen Satz waehlt - in
     * Quellreihenfolge, gemessen.
     *
     * <p><b>Zwei Saetze, zwei Sendestellen - und gerade darum eine Paartafel.</b>
     * {@link #SENDESTELLEN} fuehrt diese Datei mit einer Zwei, vorher wie nachher, und
     * eine Zwei bleibt eine Zwei, wenn die zwei Blaetter die Methoden tauschen. Beide
     * Saetze sind grau, gehen an denselben Empfaengertyp und stehen in derselben Datei;
     * beide Methoden der Naht tragen dieselbe Signatur {@code (Sprache)} und tauschen
     * also widerstandslos. Auch {@link #FARBE} und {@link #SENDEWEG} bleiben davon
     * unberuehrt, und {@code SendewegTest} 33ak ruft die zwei Methoden unmittelbar auf -
     * es sieht das waehlende Ereignis gar nicht.
     *
     * <p>Vertauscht liest ein Spieler, der in einem Bett schlaeft, "Hier ist Abenteuer:
     * ansehen und benutzen" - und wer Altfurt betritt, sein Spawnpunkt sei nicht
     * gesetzt worden, obwohl er gar keinen setzen wollte. Das ist dieselbe Fehlerklasse
     * wie {@link #ABBRUCHPAARE_IN_AUFWAERMZEIT}, nur zwischen Ereignissen statt
     * zwischen Wachen.
     *
     * <p>Gepaart wird ueber den NAMEN DES ANLASSES und nicht ueber den der Methode, in
     * der er steht: {@code RESPAWN_ANCHOR} steht nur in der Wache des gesetzten
     * Spawnpunkts, {@code ADVENTURE} nur im Zweig des Spielmodus. Ein umgestellter
     * Vergleich macht diese Probe damit nicht rot, ein vertauschter Satz schon.
     *
     * <p><b>Seit dem 06.11.2026 sieht {@code AnkunftswacheTest} 37 und 37d das
     * waehlende Ereignis doch</b> - es baut die zwei Ereignisse und liest, was beim
     * Spieler ankommt. Diese Tafel bleibt und bewacht weiter, was ein Durchlauf
     * nicht sieht: dass der Anlass {@code RESPAWN_ANCHOR} UEBERHAUPT noch im
     * Quelltext steht. Gemessen am 06.11.2026 machten fuenf Eingriffe an den zwei
     * Wachen nur die Durchlaufproben rot und liessen diese Tafel gruen - umgekehrt
     * faengt sie einen Anlass, der ganz verschwindet.
     */
    private static final List<String> SENDEPAARE_IN_ANKUNFTSWACHE = List.of(
            "RESPAWN_ANCHOR -> keinSpawnpunktZeilen",
            "ADVENTURE -> abenteuerZeilen");

    /**
     * Welche Art jeder Wachensatz traegt - in der Reihenfolge der Aufzaehlung.
     *
     * <p>Eine Tafel und keine Zahl, und das ist gemessen. {@code SprachlisteTest} 21c
     * nagelt die Umbruchsperre an EINEM Handbalken fest, weil sie ihn namentlich nennt;
     * faellt die Art eines der drei anderen auf {@link Satz.Art#SATZ}, bleibt der ganze
     * Lauf gruen - mit einem Eingriff genau dieser Form belegt: 852 Proben, 0 rot.
     * Dann darf der Betreiber dort eine Umbruchmarke setzen, und die zweite Zeile
     * verschwindet stumm ueber dem Gegenstandsgurt.
     *
     * <p>Die LISTE und keine Menge, wie bei den Paartafeln: Ein Tausch zwischen zwei
     * Konstanten liesse die Menge unveraendert.
     */
    private static final List<String> ARTEN_IN_WACHESATZ = List.of(
            "SCHUTZ_WEG_UND_MAUER -> HANDBALKEN",
            "SCHUTZ_PARZELLE_FREI -> HANDBALKEN",
            "SCHUTZ_AUSSERHALB -> HANDBALKEN",
            "SCHUTZ_FREMDE_PARZELLE -> HANDBALKEN",
            // Teil C: die drei Handbalken des Ladenschutzes. HANDBALKEN und nicht SATZ -
            // ein Handbalken nimmt EINE Zeile, und eine Umbruchmarke darin verschwindet
            // stumm. Gerade bei "der Laden von X" waere das schlimm: Der Spieler saehe
            // die Absage und nicht, wem der Stand gehoert.
            "SCHUTZ_FREMDER_LADEN -> HANDBALKEN",
            "SCHUTZ_LADEN_FREI -> HANDBALKEN",
            "SCHUTZ_LADEN_GESTOERT -> HANDBALKEN",
            "ANKUNFT_KEIN_SPAWNPUNKT -> SATZ",
            "ANKUNFT_ABENTEUER -> SATZ",
            "PORTAL_KEIN_AUSGANG -> SATZ");

    /**
     * Welche Art JEDE Konstante von {@code BundSatz} traegt - in
     * Deklarationsreihenfolge.
     *
     * <p>Dieselbe Bauart wie {@link #ARTEN_IN_WACHESATZ} - und hier ist sie noetig,
     * weil die zwei Arten von {@code BundSatz} zwei verschiedene WEGE sind und nicht
     * bloss zwei Formen desselben: Die neun Befehlsbeschreibungen gehen als nackter
     * Text an den Befehlsbaum, die neun Spielerzeilen als Component in den Chat.
     * Bei {@code WacheSatz} liegen HANDBALKEN und SATZ naeher beieinander. Dass mehrere
     * Aufzaehlungen zwei oder drei Arten tragen, ist der Normalfall - diese Tafel
     * steht nicht fuer eine Besonderheit, sondern fuer den Schaden.
     *
     * <p>Beide Richtungen sind im Spiel sichtbar, und zwar verschieden:
     * Wird eine Spielerzeile zu {@code WORT}, ist schon ihr Farbtag beim LADEN
     * eine Klage und es gilt wieder der ausgelieferte Satz; wird eine
     * Befehlsbeschreibung zu {@code SATZ}, wirft {@link Sprache#wort} beim
     * Anmelden der Befehle - und dann steht der Server ohne {@code /help}-Text da.
     *
     * <p>Die LISTE und keine Menge, wie bei den Paartafeln: Ein Tausch zwischen
     * zwei Konstanten liesse die Menge unveraendert.
     */
    private static final List<String> ARTEN_IN_BUNDSATZ = List.of(
            "BEFEHL_FREIMARKEN -> WORT",
            "BEFEHL_PARZELLE -> WORT",
            "BEFEHL_MARK -> WORT",
            "BEFEHL_WARP -> WORT",
            "BEFEHL_ZURUECK -> WORT",
            "BEFEHL_TPA -> WORT",
            "BEFEHL_TPAHERE -> WORT",
            "BEFEHL_TPACCEPT -> WORT",
            "BEFEHL_TPDENY -> WORT",
            "BEFEHL_BERUF -> WORT",
            "BEFEHL_MARKT -> WORT",
            "BEFEHL_LADEN -> WORT",
            "STAND_KOPF -> SATZ",
            "STAND_VERSION -> SATZ",
            "STAND_ABLAGE_FLUECHTIG -> SATZ",
            "STAND_ABLAGE_MARIADB -> SATZ",
            "STAND_BEFEHLE -> SATZ",
            "STAND_BEFEHLE_VERWALTUNG -> SATZ",
            "PREIS_GRATIS -> SATZ",
            "PREIS_KOSTET -> SATZ",
            "PREIS_AUSGEGEBEN -> SATZ");

    /**
     * Wie oft jede Satzmethode von {@code Beruftext} AUFGERUFEN wird - die dritte
     * Zahltafel dieser Art.
     *
     * <p>Derselbe Nagel wie {@link #AUFRUFE_VON_SPIELZEITTEXT} und
     * {@link #AUFRUFE_VON_BUNDTEXT}, gegen dieselbe Fehlerklasse: {@code Beruftext}
     * SENDET nicht, also sagt {@link #SENDESTELLEN} ueber seine Methoden nichts.
     * Schreibt ein Zweig von {@code /beruf} seine Zeile wieder selbst, bleibt die 1 bei
     * {@code BerufeBefehle.java} stehen - und der Betreiber aendert danach seine
     * {@code sprache.yml}, ohne dass sich im Spiel etwas aendert.
     *
     * <p>Die beiden grossen Zahlen sind die interessanten. {@code name} steht zehnmal,
     * und zwar weil JEDER Satz, der einen Beruf nennt, sein Wort aus der Sprachdatei
     * holt: Faellt ein Aufruf weg, liest ein Spieler an genau einer Stelle den
     * technischen Schluessel - "du bist jetzt bergmann". {@code mitNamen} steht viermal,
     * fuer die vier Absagen, die einen Beruf nennen.
     *
     * <p>{@code absageZeilen} steht dreimal und nicht einmal: Der Zweig {@code waehlen},
     * der Zweig {@code verlassen} und die Stelle, an der zwischen Urteil und Buchung
     * etwas anderes gebucht hat. Die dritte ist die unauffaelligste - ohne sie laese ein
     * Spieler, dessen Geld gerade weggebucht wurde, gar nichts.
     */
    private static final Map<String, Integer> AUFRUFE_VON_BERUFTEXT = Map.ofEntries(
            Map.entry("absageZeilen", 3),
            Map.entry("aufgezaehlt", 1),
            Map.entry("gewaehltZeilen", 1),
            Map.entry("listeZeilen", 1),
            Map.entry("mitNamen", 4),
            Map.entry("name", 10),
            Map.entry("standZeilen", 1),
            Map.entry("verlassenZeilen", 1),
            Map.entry("warnungZeilen", 1));

    /**
     * Wie oft jede Methode von {@code Bundtext} AUFGERUFEN wird - nachgezaehlt,
     * nicht geschaetzt.
     *
     * <p>Derselbe Nagel wie {@link #AUFRUFE_VON_SPIELZEITTEXT}, gegen dieselbe
     * Luecke: {@code Bundtext} sendet nicht, also sagt {@link #SENDESTELLEN} ueber
     * seine zwei Methoden gar nichts. Schreibt {@code zeigeStand} seine Zeilen
     * wieder selbst, faellt die Zahl hier auf 0, waehrend die 35 dort unveraendert
     * stehen bleibt - ein Spieler liest dann weiter dieselben Worte, und der
     * Betreiber aendert seine {@code sprache.yml} ohne jede Wirkung.
     *
     * <p>Und die andere Richtung: Ruft ein Zweig die Methode des anderen, steht
     * eine Zahl auf 2 und die andere auf 0.
     */
    private static final Map<String, Integer> AUFRUFE_VON_BUNDTEXT = Map.ofEntries(
            Map.entry("preisZeilen", 1),
            Map.entry("standZeilen", 1));

    /**
     * WOHER jedes Argument der zwei Nahtaufrufe kommt - in Quellreihenfolge,
     * gemessen.
     *
     * <p><b>Erst Bauschritt 7 hat diesen Fehler ueberhaupt moeglich gemacht.</b>
     * Im Zeugen c8bd801 standen die drei Fassungen inline in EINER Zeichenkette,
     * die Ablage in einem Ternaer und das Recht in einem eigenen {@code if} - ein
     * Tausch war nicht formulierbar. Jetzt stehen drei {@code String} nebeneinander,
     * dann zwei {@code boolean}, und im zweiten Aufruf zwei {@code long}. Jeder
     * Tausch innerhalb einer Gruppe uebersetzt fehlerfrei.
     *
     * <p>Und keine andere Probe sieht ihn: {@link #AUFRUFE_VON_BUNDTEXT} zaehlt nur
     * die Aufrufe, {@code SendewegTest} 33am und die Bildtafel rufen die Naht mit
     * EIGENEN Probewerten auf und kennen den Aufrufort nicht, und {@code AufrufstelleTest}
     * erreicht die Zweige ohne Server gar nicht.
     *
     * <p>Im Spiel: Ein Server mit Datenbank sagt rot "Ablage fluechtig" - oder, was
     * schlimmer ist, ein fluechtiger sagt "Ablage MariaDB", die Spieler bauen darauf
     * und verlieren beim Neustart alles. Bei den zwei Betraegen nennt {@code /freimarken}
     * {@code preis} als Preis die Summe des Bisherigen, und der Spieler rechnet falsch.
     */
    private static final List<String> QUELLEN_DER_BUNDNAHT = List.of(
            "standZeilen: stand.version, stand.javaFassung, stand.minecraft,"
                    + " stand.fluechtig, hasPermission",
            "preisZeilen: preisFuerNaechste, gesamtpreisFuer",
            "BundBefehle.Stand: getPluginMeta, Runtime.version,"
                    + " getMinecraftVersion, fluechtig",
            "BundBefehle: spielzeitBefehle.spielzeit, zeigeWelt,"
                    + " zeigeVorgenerierung, neulade, bringeZurParzelle,"
                    + " grenze.von, grenze.bis");

    /**
     * Wer die zwei Listen von {@code /freimarken} bekommt - in Quellreihenfolge.
     *
     * <p>Das Werkzeug stand schon da: {@link #SENDEPAAR} ist seit Schritt 3 in Gebrauch
     * (31e fuer {@code AnfragenBefehle}), nur nicht auf dieser Datei. Ginge das
     * {@code forEach} an einen anderen {@code Audience} - etwa an
     * {@code getServer().getConsoleSender()} -, blieben 35 Sendestellen, 1/1 Aufrufe,
     * 0 Farben und jedes Bild gruen, und der Spieler bekaeme auf {@code /freimarken}
     * keine Antwort.
     */
    private static final List<String> SENDEPAARE_IN_BUNDBEFEHLE = List.of(
            "standZeilen -> empfaenger",
            "preisZeilen -> empfaenger");

    /**
     * Wessen Drossel gilt und wer den Handbalken liest - vier Stellen in
     * {@code Schutzwache}, in Quellreihenfolge.
     *
     * <p>Beides war unsichtbar, solange keine Probe den Zweig DURCHLIEF, und beides
     * ist mit einem Eingriff belegt, der sonst gruen blieb. Geht der Balken an
     * {@code Bukkit.getOnlinePlayers().iterator().next()}, liest ein Unbeteiligter
     * "Diese Parzelle gehoert Bert", waehrend der Abgewiesene nicht erfaehrt, warum
     * sein Block nicht bricht. Und liest die Drossel {@code letzteMeldung.values()}
     * statt des eigenen Schluessels, macht ein einziger Spieler die Wache fuer ALLE
     * zwei Sekunden stumm - beides im Spiel sofort sichtbar, beides von jeder
     * Zahltafel ungesehen.
     *
     * <p><b>Seit dem 06.11.2026 gibt es den echten Durchlauf</b>, und diese Tafel
     * ist nicht mehr der einzige Riegel: {@code SchutzwacheTest} 36c laesst zwei
     * Spieler wirklich abweisen und liest, was bei ihnen ankommt. Moeglich wurde
     * das durch {@code Serverprobe} - einen Stellvertreter unter
     * {@code Bukkit.setServer}, denn der Klasseninitialisierer der Wache fragt
     * ueber {@code Tag.FLOWER_POTS} nach {@code Bukkit.server}.
     *
     * <p><b>Die Tafel bleibt trotzdem, und das ist kein Doppel.</b> Sie liest den
     * Quelltext und faengt damit eine andere Gattung: einen Griff in die Drossel,
     * der ganz verschwindet, und einen Empfaenger, der ein anderer wird. Gemessen
     * am 06.11.2026: Drei Eingriffe - die Grenzfrage verdreht, die Meldepause auf
     * null, der Namensrueckfall halbiert - liessen diese Tafel und 31r GRUEN und
     * machten nur die Durchlaufproben rot. Umgekehrt sieht der Durchlauf nicht,
     * ob ein Zugang die Wache ueberhaupt noch ruft. Zwei Fragen, zwei Riegel.
     */
    private static final List<String> DROSSEL_UND_BALKEN_IN_SCHUTZWACHE = List.of(
            "letzteMeldung.get <- spieler",
            "letzteMeldung.put <- spieler",
            "letzteMeldung.remove <- ereignis.getPlayer()",
            "spieler liest grund");

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
     * Ein Farbtag als Zeichen im Programm - nicht der Name einer Adventure-Klasse.
     *
     * <p><b>Die DRITTE Frage neben {@link #FARBE} und {@link #MINIMESSAGE_LESEN}, und
     * sie stand bis zum 05.11.2026 offen.</b> Ein {@code "<green>" + name} nennt keine
     * Adventure-Klasse und liest keinen Satz - beide Muster sehen es nicht. Gemessen mit
     * einem Eingriff genau dieser Form am Wert von {@link Schutztext}: 852 Proben,
     * 0 rot.
     *
     * <p>Im Spiel ist der Schaden sichtbar. Ein Wert geht ROH als Blatt in den Satz und
     * wird nicht ein zweites Mal gelesen - das ist so gewollt und in {@code Sprache}
     * begruendet, weil sonst ein {@code <red>} aus einer Spielereingabe wirklich rot
     * ankaeme. Die Kehrseite: Das Tag steht dem Spieler woertlich im Handbalken,
     * {@code Diese Parzelle gehoert <green>Bert</green>}.
     *
     * <p>Gesucht werden nur die Namen, die MiniMessage als Farbe oder Schriftform kennt,
     * dazu die Hexform und die zwei verbotenen Tags. Darum trifft es weder
     * {@code <ziel>} in einem Hilfetext noch {@code <p>} im Javadoc - Kommentare liest
     * {@link Quellen#text} ohnehin nicht mit, und genau darauf beruht die enge Menge.
     */
    private static final Pattern FARBTAG_IM_SATZ = Pattern.compile(
            "<(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray"
                    + "|dark_gray|blue|green|aqua|red|light_purple|yellow|white|bold"
                    + "|italic|underlined|strikethrough|obfuscated)>"
                    + "|<#[0-9a-fA-F]{6}>"
                    + "|<(?:click|hover|gradient|rainbow)[:>]");

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
     *
     * <p><b>Das {@code deserialize} traegt eine KLAMMER</b>, und die ist am 08.11.2026
     * nachgetragen worden: Ohne sie traf das Muster auch
     * {@code ItemStack.deserializeBytes} in {@code Ladenware} - ein Zufallstreffer, der
     * nichts mit MiniMessage zu tun hat. Die Datei auszunehmen waere der falsche Weg
     * gewesen: Eine ausgenommene Datei ist fuer immer blind, auch wenn dort eines Tages
     * wirklich ein Satz gebaut wird. MiniMessage heisst die Methode
     * {@code deserialize(String)}, Bukkit nennt sie {@code deserializeBytes} - die
     * Klammer trennt beide genau.
     */
    private static final Pattern MINIMESSAGE_LESEN = Pattern.compile(
            "MiniMessage|deserialize\\(");

    @Test
    @DisplayName("31w - das Plugin stellt den LADENSCHUTZ ein, nicht die leere Regel")
    void pluginStelltDenLadenschutzEin() {
        // Befund der Einspritzungskampagne vom 08.11.2026: Der Tausch von
        // new Ladenschutz(...) gegen Schutzwache.KEINE_ORTSREGEL in FreimarkenPlugin
        // liess alle 2064 Proben gruen - und im Spiel waere damit JEDER Laden offen,
        // ohne dass irgendetwas auffaellt. Das Plugin startet in keiner Probe, also ist
        // der Quelltext das einzige, was diese Zeile halten kann.
        //
        // Festgehalten wird der TEXT und keine Zahl: Eine Zaehltafel laesst sich
        // hochdrehen, und bei einer Fluchtfolge wird sie blind.
        // Quellen.text liest OHNE Kommentare - genau richtig: Ein Name, der nur in
        // einem Javadoc steht, ist keine Verdrahtung.
        String plugin = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "FreimarkenPlugin.java nicht gefunden - dann prueft diese Probe"
                                + " nichts")));

        assertTrue(plugin.contains("new Ladenschutz(ladenverzeichnis, mietablage,"),
                "FreimarkenPlugin muss den Ladenschutz an die Schutzwache geben -"
                        + " sonst ist in der Markthalle jede Hand erlaubt");
        // Die andere Haelfte, und sie ist der eigentliche Fang: Die leere Regel darf dort
        // NICHT stehen. Ohne diese Zeile waere der Tausch unsichtbar.
        assertFalse(plugin.contains("KEINE_ORTSREGEL"),
                "KEINE_ORTSREGEL ist die Antwort fuer einen Server OHNE Markthalle -"
                        + " dieser hat eine");
        // Und der Handelsklick: Seit dem 08.11.2026 steht hier der ECHTE, und die
        // Zusage ist umgedreht - KEIN_HANDEL darf NICHT mehr dastehen. Waere diese
        // Zeile beim Umbau einfach geloescht worden, bewachte nichts mehr, dass
        // die Tuer verdrahtet ist.
        assertFalse(plugin.contains("Schutzwache.KEIN_HANDEL"),
                "KEIN_HANDEL ist die Antwort fuer einen Server OHNE Kistenshops -"
                        + " dieser hat sie");
        // Das FELD wird beim Aufruf gelesen und nicht beim Bauen, und das ist eine
        // Notwendigkeit: Der Shopklick entsteht erst in richteShopsEin, weil sein
        // Shopzugriff diese Wache braucht. Ein Zirkel, den nur ein spaet gelesenes
        // Feld aufloest - und ein null darin heisst nein, also gilt bei stummer
        // Datenbank genau das, was die Startmeldung dort verspricht.
        assertTrue(plugin.contains("shopklick != null"),
                "die Tuer muss das FELD lesen - ein beim Bauen uebergebener"
                        + " Shopklick waere ein Zirkel, und ein null darin wuerfe"
                        + " bei jedem Klick");
        assertTrue(plugin.contains("shopklick = new Shopklick("),
                "und er muss auch gesetzt werden - sonst ist die Tuer fuer immer"
                        + " zu");
    }

    @Test
    @DisplayName("31x - beimKlicken RUFT die Handelstuer, bevor es den Besitz fragt")
    void beimKlickenRuftDieHandelstuer() {
        // Die letzte der einundzwanzig Einspritzungen vom 08.11.2026, die durchlief:
        // Die BEDINGUNG der Handelstuer ist in SchutzwacheTest 36l geprueft, aber dass
        // beimKlicken sie ueberhaupt ruft, war von nichts gedeckt. Wer den Aufruf
        // wegnimmt, schliesst die Tuer - und sobald es Kistenshops gibt, kauft dann
        // niemand mehr im Laden eines anderen.
        //
        // <b>Das ist eine TEXTPRUEFUNG und keine Verhaltensprobe</b>, und das ist
        // benannt statt verschwiegen: beimKlicken durchlaufen heisst, ein
        // PlayerInteractEvent mit einem Block zu bauen, fuer den istHeikel wahr ist -
        // und das braucht von der Serverprobe Antworten auf getState, getBlockData und
        // die Blockgruppen, die heute keine andere Probe verlangt.
        //
        // EINE ZUSAGE JE ZWEIG [08.11.2026], und das ist der Befund an dieser Probe
        // selbst: Ein blosses contains war vom Rechtsklick-Aufruf erfuellt und blieb
        // gruen, auch wenn der Linksklick-Aufruf fehlte oder wieder verschwand. Genau
        // das ist derselbe Mangel wie bei contains("B 100") in der Sprachdatei - eine
        // Textsuche, die nicht sagt, WO der Text steht, bewacht den zweiten Ort nicht.
        //
        // Der Linksklick ist der teurere der zwei: Lumias VERKAUF ist ein Linksklick,
        // und ein Schild ist heikel. Ohne die Tuer dort wird jeder Verkauf auf fremdem
        // Grund und in jeder Markthalle vom Bauschutz abgewiesen - also genau dort, wo
        // Kistenshops stehen.
        String wache = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Schutzwache.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Schutzwache.java nicht gefunden - dann prueft diese Probe"
                                + " nichts")));
        String aufruf = "alsHandelDurchlassen(handel, ereignis.getPlayer(), block)";
        int links = wache.indexOf("Action.LEFT_CLICK_BLOCK");
        int rechts = wache.indexOf("Action.RIGHT_CLICK_BLOCK");
        assertTrue(links > 0 && rechts > links,
                "die zwei Klickzweige muessen in dieser Reihenfolge stehen - sonst"
                        + " schneidet diese Probe an der falschen Stelle: links=" + links
                        + ", rechts=" + rechts);

        assertTrue(wache.substring(links, rechts).contains(aufruf),
                "der LINKSKLICK-Zweig muss die Handelstuer rufen - sonst kann niemand"
                        + " auf fremdem Grund verkaufen, und das ist Lumias ganzer"
                        + " Verkauf");
        assertTrue(wache.substring(rechts).contains(aufruf),
                "der RECHTSKLICK-Zweig muss sie auch rufen - sonst kauft niemand im"
                        + " fremden Laden");
        // Und GENAU zweimal: Ein dritter Aufruf waere ein Zweig, der sich seine eigene
        // Tuerbedingung gebaut hat.
        assertEquals(2, wache.split(java.util.regex.Pattern.quote(aufruf), -1).length - 1,
                "die Tuer wird genau zweimal gerufen - einmal je Klickrichtung");
    }

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
    @DisplayName("31t - ein Farbtag steht als Zeichen nur noch in DIESER Datei")
    void farbtagAlsZeichenNurNochInDieserDatei() {
        assertEquals(FARBTAG_ERLAUBT, dateienMit(FARBTAG_IM_SATZ), "Ein Farbtag in"
                + " einem WERT wirkt nicht - er steht dem Spieler woertlich da. Farbe"
                + " gehoert in den Satz in der Sprachdatei, nicht an den Wert im"
                + " Programm.");
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
        assertEquals(new TreeMap<>(AUFRUFE_VON_SPIELZEITTEXT),
                zaehleSatzmethoden("Spielzeittext.java"),
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

    @Test
    @DisplayName("31r - jede Wache der Schutzwache waehlt ihren eigenen Handbalken")
    void jedeWacheDerSchutzwacheWaehltIhrenHandbalken() {
        String quelle = Quellen.text(datei("Schutzwache.java"));
        List<String> gefunden = new ArrayList<>();
        // Drei Muster fuer drei Formen, in der Reihenfolge des Quelltextes: die eine
        // Wache mit einem if, der Ternaer mit seinen ZWEI Antworten und der Satz mit
        // dem fremden Namen. Ein Muster ueber alles koennte den Ternaer nicht in zwei
        // Zeilen zerlegen - und genau dort sass in Schritt 5 der gruene Tausch.
        var weg = WEGWACHE.matcher(quelle);
        while (weg.find()) {
            gefunden.add(weg.group(1) + " -> " + weg.group(2));
        }
        var grenze = GRENZWACHE.matcher(quelle);
        while (grenze.find()) {
            gefunden.add(grenze.group(1) + "/dann -> " + grenze.group(2));
            gefunden.add(grenze.group(1) + "/sonst -> " + grenze.group(3));
        }
        var fremd = EIGENTUEMERWACHE.matcher(quelle);
        while (fremd.find()) {
            gefunden.add(fremd.group(1) + " <- " + fremd.group(2) + "()");
        }
        // Die Zahl eigens, wie in 31m: Findet ein Muster seine Form nicht mehr, waere
        // der Vergleich darunter zwar auch rot - die Meldung sagte aber nicht, dass ein
        // MUSTER nachzuziehen ist und nicht das Programm.
        assertEquals(4, gefunden.size(), "gesucht werden VIER Stellen - die Wegwache,"
                + " die zwei Seiten des Grenzternaers und der Satz mit dem fremden"
                + " Namen. Fehlt eine, hat ein Muster sie nicht gefunden; dann gehoert"
                + " das Muster nachgezogen und nicht die Probe gestrichen. Fehlt die"
                + " LETZTE Zeile, kann es auch umgekehrt sein: Dann steht im Programm"
                + " statt eigentuemer() etwas anderes - etwa der Leser -, und dann"
                + " gehoert das PROGRAMM geaendert und nicht dieses Muster. GEMESSEN am"
                + " 05.11.2026 mit genau diesem Eingriff: " + gefunden);
        // Die LISTE in Quellreihenfolge und keine Menge: Genau der Tausch, den diese
        // Probe faengt, laesst die Menge unveraendert.
        //
        // Was diese Probe NICHT deckt, und das ist gemessen: Zeigt der Koerper einer
        // Methode in Schutztext auf den falschen Schluessel, bleibt die Wahl hier
        // richtig. Diese NAHTLAGE haelt SendewegTest 33aj, das die vier Wortlaute
        // einzeln ruft und ihr Bild liest. Probe 30 wird dabei mit rot, sagt aber nur
        // die Folge (ein Schluessel wird nirgends mehr geholt) und nicht die Ursache.
        assertEquals(WACHENPAARE_IN_SCHUTZWACHE, gefunden, "Vier Wortlaute gehen durch"
                + " EINE Sendestelle, also bleibt SENDESTELLEN bei einer Eins, was auch"
                + " vertauscht wird. Vertauscht liest ein Spieler auf einer freien"
                + " Parzelle mitten in der Stadt, hier ende die Marktstadt - und"
                + " draussen, er solle sie mit /parzelle nehmen holen. Und in der"
                + " letzten Zeile steht die andere Frage: Der genannte Name gehoert dem"
                + " EIGENTUEMER und nicht dem Leser.");
    }

    @Test
    @DisplayName("31s - jedes Ereignis der Ankunftswache waehlt seinen eigenen Satz")
    void jedesEreignisDerAnkunftswacheWaehltSeinenSatz() {
        String quelle = Quellen.text(datei("Ankunftswache.java"));
        List<String> gefunden = new ArrayList<>();
        var paar = ANKUNFTSANLASS.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " -> " + paar.group(2));
        }
        // Die Zahl eigens, wie in 31m und 31r: Findet das Muster einen Anlass nicht
        // mehr, waere der Vergleich darunter zwar auch rot - die Meldung sagte aber
        // nicht, dass ein MUSTER nachzuziehen ist und nicht das Programm.
        assertEquals(2, gefunden.size(), "gesucht werden ZWEI Anlaesse - der gesetzte"
                + " Spawnpunkt und die betretene Abenteuerwelt. Fehlt einer, hat das"
                + " Muster ihn nicht gefunden; dann gehoert das Muster nachgezogen und"
                + " nicht die Probe gestrichen: " + gefunden);
        // Die LISTE in Quellreihenfolge und keine Menge: Genau der Tausch, den diese
        // Probe faengt, laesst die Menge unveraendert.
        assertEquals(SENDEPAARE_IN_ANKUNFTSWACHE, gefunden, "Zwei Saetze, zwei"
                + " Sendestellen - und die Zwei in SENDESTELLEN bleibt eine Zwei, was"
                + " auch vertauscht wird. Vertauscht liest ein Spieler, der in einem"
                + " Bett schlaeft, Hier ist Abenteuer - und wer Altfurt betritt, sein"
                + " Spawnpunkt sei nicht gesetzt worden, obwohl er keinen setzen"
                + " wollte.");
    }

    @Test
    @DisplayName("31u - jeder Wachensatz traegt die Art, die sein Sendeweg braucht")
    void jederWachensatzTraegtSeineArt() {
        List<String> gefunden = new ArrayList<>();
        for (WacheSatz satz : WacheSatz.values()) {
            gefunden.add(satz.name() + " -> " + satz.art().name());
        }
        // Die Aufzaehlung selbst und kein Quelltext: Die Art ist eine Zusicherung des
        // Vertrags, und so ist auch eine mehrzeilige Deklaration mitgenommen.
        assertEquals(ARTEN_IN_WACHESATZ, gefunden, "Ein Handbalken nimmt EINE Zeile."
                + " Traegt er Art.SATZ, laedt eine Umbruchmarke darin ohne Klage, und"
                + " die zweite Zeile verschwindet stumm. Umgekehrt wirft eine Chatzeile"
                + " mit Art.HANDBALKEN, sobald sie zwei Zeilen hat - dann liest der"
                + " Spieler an dieser Stelle gar nichts.");
    }

    @Test
    @DisplayName("31v - die Schutzwache drosselt je Spieler und sendet an ihn selbst")
    void dieSchutzwacheDrosseltUndSendetJeSpieler() {
        String quelle = Quellen.text(datei("Schutzwache.java"));
        List<String> gefunden = new ArrayList<>();
        var drossel = DROSSELWACHE.matcher(quelle);
        while (drossel.find()) {
            gefunden.add("letzteMeldung." + drossel.group(1) + " <- "
                    + drossel.group(2));
        }
        var balken = BALKENWACHE.matcher(quelle);
        while (balken.find()) {
            gefunden.add(balken.group(1) + " liest " + balken.group(2));
        }
        // Die Zahl eigens, wie in 31m, 31r und 31s: Findet ein Muster seine Form nicht
        // mehr, sagt die Meldung sonst nicht, dass ein MUSTER nachzuziehen ist.
        assertEquals(4, gefunden.size(), "gesucht werden VIER Stellen - die drei"
                + " Griffe in die Drossel und der Handbalken selbst. Fehlt eine, hat ein"
                + " Muster sie nicht gefunden; dann gehoert das Muster nachgezogen und"
                + " nicht die Probe gestrichen: " + gefunden);
        assertEquals(DROSSEL_UND_BALKEN_IN_SCHUTZWACHE, gefunden, "Die Drossel gilt JE"
                + " SPIELER und der Balken geht an den, dessen Hand abgewiesen wurde."
                + " Eine gemeinsame Drossel macht die Wache fuer alle stumm, sobald"
                + " einer anschlaegt; ein fremder Empfaenger laesst den Abgewiesenen"
                + " ratlos vor einem Block stehen, der nicht bricht.");
    }

    /**
     * Zaehlt die Aufrufe jeder Satzmethode EINER Satzdatei - der eine Zaehler.
     *
     * <p>Drei Tafeln benutzen ihn ({@code Spielzeittext}, {@code Bundtext},
     * {@code Beruftext}), und bis zum 06.11.2026 stand die Schleife dreimal da. Das ist
     * genau die Falle, gegen die diese Tafeln gebaut sind: Die dritte Abschrift bekommt
     * die naechste Verschaerfung nicht mit, und dann zaehlt eine Tafel anders als die
     * anderen zwei.
     *
     * <p>Im eigenen Quelltext steht der Aufruf nackt, auswaerts mit dem Klassennamen
     * davor. Minus eins: die Deklaration selbst. Die Ruecksicht auf das Zeichen VOR dem
     * Namen trennt {@code dauer} von {@code genaueDauer} - sonst stuende die Zahl von
     * {@code dauer} um die Aufrufe der genauen Form zu hoch.
     */
    private static Map<String, Integer> zaehleSatzmethoden(String dateiname) {
        String eigen = Quellen.text(datei(dateiname));
        String klasse = dateiname.substring(0, dateiname.length() - ".java".length());
        Map<String, Integer> gezaehlt = new TreeMap<>();
        var deklaration = SATZMETHODE.matcher(eigen);
        while (deklaration.find()) {
            String name = deklaration.group(1);
            int anzahl = treffer(Pattern.compile(
                    "(?<![\\w$.])" + name + "\\s*\\("), eigen) - 1;
            for (Path andere : Quellen.alle()) {
                if (!andere.getFileName().toString().equals(dateiname)) {
                    anzahl += treffer(Pattern.compile(
                                    klasse + "\\s*[.]\\s*" + name + "\\s*\\("),
                            Quellen.text(andere));
                }
            }
            gezaehlt.put(name, anzahl);
        }
        assertFalse(gezaehlt.isEmpty(), "die Suche hat die Satzmethoden von " + dateiname
                + " nicht gefunden - dann waere diese Tafel gruen, ohne etwas gesehen zu"
                + " haben");
        return gezaehlt;
    }

    @Test
    @DisplayName("31aa - jede Satzmethode von Beruftext wird genau so oft gerufen")
    void jedeSatzmethodeVonBeruftextWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_VON_BERUFTEXT),
                zaehleSatzmethoden("Beruftext.java"),
                "Beruftext sendet nicht, also sagt SENDESTELLEN ueber seine Methoden"
                        + " nichts: Schreibt ein Zweig von /beruf seine Zeilen wieder"
                        + " selbst, bleibt die 1 dort stehen, und der Spieler liest einen"
                        + " Satz, den seine sprache.yml nicht mehr aendert. Eine zu"
                        + " niedrige Zahl bei name heisst, dass an einer Stelle der"
                        + " technische Schluessel steht - \"du bist jetzt bergmann\".");
    }

    @Test
    @DisplayName("31w - jede Satzmethode von Bundtext wird genau so oft gerufen")
    void jedeSatzmethodeVonBundtextWirdGenauSoOftAufgerufen() {
        assertEquals(new TreeMap<>(AUFRUFE_VON_BUNDTEXT),
                zaehleSatzmethoden("Bundtext.java"),
                "Bundtext sendet nicht, also sagt SENDESTELLEN ueber seine zwei"
                        + " Methoden nichts: Schreibt ein Zweig seine Zeilen wieder"
                        + " selbst, bleibt die Zahl dort stehen, und der Spieler"
                        + " liest einen Satz, den seine sprache.yml nicht mehr"
                        + " aendert.");
    }

    @Test
    @DisplayName("31x - jeder Bundsatz traegt die Art, die sein Weg braucht")
    void jederBundsatzTraegtSeineArt() {
        List<String> gefunden = new ArrayList<>();
        for (BundSatz satz : BundSatz.values()) {
            gefunden.add(satz.name() + " -> " + satz.art().name());
        }
        // Die Aufzaehlung selbst und kein Quelltext, wie in 31u: Die Art ist eine
        // Zusicherung des Vertrags, und so ist auch eine mehrzeilige Deklaration
        // mitgenommen.
        assertEquals(ARTEN_IN_BUNDSATZ, gefunden, "Die zwei Arten von BundSatz"
                + " sind zwei verschiedene Wege, und beide Richtungen sind"
                + " im Spiel sichtbar: Eine Spielerzeile mit Art.WORT laedt wegen"
                + " ihres Farbtags gar nicht erst, und eine Befehlsbeschreibung mit"
                + " Art.SATZ laesst Sprache.wort beim Anmelden der Befehle werfen."
                + " Die Reihenfolge zaehlt mit - ein Tausch zwischen zwei"
                + " Konstanten liesse eine Menge unveraendert.");
    }

    @Test
    @DisplayName("31y - jeder Nahtaufruf von /freimarken nimmt seine Quellen in der"
            + " richtigen Reihenfolge")
    void jederNahtaufrufNimmtSeineQuellenInDerRichtigenReihenfolge() {
        String quelle = Quellen.text(datei("BundBefehle.java"));
        List<String> gefunden = new ArrayList<>();
        var aufruf = BUNDAUFRUF.matcher(quelle);
        while (aufruf.find()) {
            List<String> quellen = new ArrayList<>();
            var marke = BUNDQUELLE.matcher(aufruf.group(2));
            while (marke.find()) {
                quellen.add(marke.group());
            }
            gefunden.add(aufruf.group(1) + ": " + String.join(", ", quellen));
        }
        // Und die zweite Stelle, eine Datei weiter: der Bau des Standwerts.
        var bau = STANDBAU.matcher(Quellen.text(datei("FreimarkenPlugin.java")));
        while (bau.find()) {
            List<String> quellen = new ArrayList<>();
            var marke = STANDQUELLE.matcher(bau.group(2));
            while (marke.find()) {
                quellen.add(marke.group());
            }
            gefunden.add(bau.group(1) + ": " + String.join(", ", quellen));
        }
        // Die Zahl eigens, wie in 31m, 31r und 31s: Findet das Muster seine Form
        // nicht mehr, sagt die Meldung sonst nicht, dass ein MUSTER nachzuziehen
        // ist - eine leere Liste gegen eine leere Liste bliebe gruen.
        assertEquals(4, gefunden.size(), "gesucht werden ZWEI Nahtaufrufe in"
                + " BundBefehle und ZWEI Zusammenbauten in FreimarkenPlugin."
                + " Fehlt einer,"
                + " hat das Muster ihn nicht"
                + " gefunden; dann gehoert das Muster nachgezogen und nicht die"
                + " Probe gestrichen: " + gefunden);
        assertEquals(QUELLEN_DER_BUNDNAHT, gefunden, "Drei String, zwei"
                        + " boolean und zwei long stehen hier nebeneinander, und"
                        + " jeder Tausch innerhalb einer Gruppe uebersetzt"
                        + " fehlerfrei. Die Naht selbst prueft SendewegTest mit"
                        + " eigenen Probewerten und sieht den Aufrufort nicht.");
    }

    @Test
    @DisplayName("31z - die zwei Antworten von /freimarken gehen an den, der gefragt hat")
    void dieAntwortenVonFreimarkenGehenAnDenFragenden() {
        String quelle = Quellen.text(datei("BundBefehle.java"));
        List<String> gefunden = new ArrayList<>();
        var paar = SENDEPAAR.matcher(quelle);
        while (paar.find()) {
            gefunden.add(paar.group(1) + " -> " + paar.group(2));
        }
        // Die LISTE in Quellreihenfolge und keine Menge, wie in 31e: Ein
        // verdoppelter Sendeort und zwei getauschte Empfaenger bleiben in einer
        // Menge beide gruen.
        assertEquals(SENDEPAARE_IN_BUNDBEFEHLE, gefunden, "Dieselbe Tafel"
                + " wie fuer /tpa, nur spaeter: Ginge eine der zwei Listen an"
                + " einen anderen Audience, blieben die 35 Sendestellen, die"
                + " Aufrufzahlen und alle Bilder gruen - und der Spieler bekaeme"
                + " auf /freimarken keine Antwort.");
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
     * Die Deklaration einer Satzmethode einer Nahtdatei.
     *
     * <p>Weiter als {@link #ZEILENMETHODE}, weil bei {@code Spielzeittext} die halbe
     * Klasse {@code String} zurueckgibt: Der Takt-Abstand und die vier Zahlformen
     * werden in eine andere Zeile EINGESETZT und sind keine Zeile fuer sich. Und ohne
     * Ruecksicht auf {@code private}, damit {@code zustandZeile} mitgezaehlt wird.
     *
     * <p>Seit Bauschritt 7 zaehlt 31w mit demselben Muster auch {@code Bundtext},
     * obwohl dort keine Methode {@code String} liefert. Das ist Absicht: Das weitere
     * Muster bleibt rot, falls dort je ein Wortstueck entsteht - das engere waere
     * stumm.
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

    /**
     * Die Wegwache der {@code Schutzwache} mit dem Handbalken, den sie waehlt.
     *
     * <p>{@code [^;]} und nicht {@code .}, wie bei {@link #HINDERNISWACHE}: So kann
     * ein Treffer keine zwei Anweisungen ueberspannen und nicht in die Antwort der
     * naechsten Wache laufen.
     */
    private static final Pattern WEGWACHE = Pattern.compile(
            "(feldBei)[^;]*?Schutztext[.](\\w+)\\s*\\(");

    /**
     * Der Grenzternaer mit BEIDEN Antworten - erst die dann-Seite, dann die sonst-Seite.
     *
     * <p>Beide Seiten in EINEM Muster, und das ist der ganze Zweck: Ein Muster, das nur
     * die erste nimmt, sagt nichts darueber, was auf der anderen steht - und genau
     * dieser Tausch lief in Schritt 5 gruen durch. Der Ternaer traegt bis zu seinem
     * Semikolon keines, also kann {@code [^;]} ihn ganz lesen und nicht weiter.
     */
    private static final Pattern GRENZWACHE = Pattern.compile(
            "(enthaelt)[^;]*?[?][^;]*?Schutztext[.](\\w+)\\s*\\([^;]*?:[^;]*?"
                    + "Schutztext[.](\\w+)\\s*\\(");

    /**
     * Der Handbalken, der einen FREMDEN Namen einsetzt - mit der Quelle des Namens.
     *
     * <p>Gesucht wird der Weg vom Aufruf zum Wert und nicht bloss der Aufruf: Der
     * Name muss aus {@code eigentuemer()} kommen. Ein {@code spieler.getName()} an
     * derselben Stelle laesst dieses Muster leer laufen, und dann ist 31r schon an
     * der Zahl der Treffer rot.
     */
    private static final Pattern EIGENTUEMERWACHE = Pattern.compile(
            "Schutztext[.](\\w+)\\s*\\([^;]*?(eigentuemer)\\s*\\(\\s*\\)");

    /**
     * Ein Anlass der {@code Ankunftswache} mit dem Satz, den er waehlt.
     *
     * <p>{@code [^;]} und nicht {@code .}, wie bei {@link #HINDERNISWACHE}: So kann ein
     * Treffer keine zwei Anweisungen ueberspannen. Das ist hier mehr als Vorsicht - es
     * ist der Grund, warum das Muster genau zwei Treffer findet und nicht drei:
     * {@code RESPAWN_ANCHOR} steht ein zweites Mal in der Wache {@code gesperrt}, dort
     * aber in einem Zweig, dem unmittelbar ein Semikolon folgt.
     *
     * <p>Der Name des Anlasses und nicht der der umgebenden Methode: Eine Suche von der
     * Deklaration bis zur Wahl muesste abschnittsweise arbeiten (wie 31h und 31l), und
     * die zwei reinen Methoden der Naht waeren selbst Abschnitte - sie nennen ihren
     * eigenen Namen und wuerden sich selbst zugeordnet.
     */
    private static final Pattern ANKUNFTSANLASS = Pattern.compile(
            "(RESPAWN_ANCHOR|ADVENTURE)[^;]*?(\\w+Zeilen)\\s*\\(");

    /** Ein Griff in die Drossel, mit dem Spieler, dessen Schluessel er nimmt. */
    private static final Pattern DROSSELWACHE = Pattern.compile(
            "letzteMeldung\\.(get|put|remove)\\(\\s*([\\w.()]+)\\.getUniqueId\\(\\)");

    /**
     * Ein Nahtaufruf von {@code Bundtext} mit allem, was in seinen Klammern steht.
     *
     * <p>{@code [^;]} und nicht {@code .}: So kann ein Treffer keine zweite
     * Anweisung ueberspannen. Das {@code .forEach} am Ende ist der Anker, an dem
     * die sparsame Suche haltmacht - ohne ihn endete sie an der ersten inneren
     * Klammer.
     */
    private static final Pattern BUNDAUFRUF = Pattern.compile(
            "Bundtext\\s*[.]\\s*(\\w+)\\s*\\(([^;]*?)\\)\\s*[.]\\s*forEach");

    /**
     * Die Quellen, die am Aufrufort vorkommen DUERFEN - ein enger Wortvorrat.
     *
     * <p>Nicht jeder Bezeichner, sondern genau die sieben, die die Reihenfolge
     * entscheiden. Ein weiter Vorrat faende auch {@code sprache} und {@code besessen},
     * und {@code besessen} steht INNERHALB der zwei Preisaufrufe - die Liste
     * stuende dann voller Glieder, die nichts zusichern.
     */
    private static final Pattern BUNDQUELLE = Pattern.compile(
            "stand[.]version|stand[.]javaFassung|stand[.]minecraft"
                    + "|stand[.]fluechtig|hasPermission|preisFuerNaechste"
                    + "|gesamtpreisFuer");

    /**
     * Der Bau des Standwerts im Plugin - vier Angaben nebeneinander.
     *
     * <p><b>Der Auszug hat die Gefahr VERSCHOBEN, nicht beseitigt.</b> Vorher
     * standen die drei Fassungen und der Ablagezustand als Argumente am
     * Nahtaufruf; jetzt stehen sie als Komponenten eines {@code record}.
     * Drei {@code String} nebeneinander tauschen so widerstandslos wie vorher,
     * nur eine Datei weiter. Darum liest diese Probe BEIDE Stellen.
     *
     * <p><b>Gierig und nicht sparsam</b>, anders als {@link #BUNDAUFRUF}: Dort
     * macht das {@code .forEach} am Ende die Suche fest, hier gibt es keinen
     * solchen Anker. Sparsam gesucht endete der Treffer an der ersten inneren
     * Klammer - bei {@code getPluginMeta()} -, und die Tafel sah nur die erste
     * der vier Angaben. Gemessen am 06.11.2026. Gierig endet sie an der letzten
     * Klammer vor dem Semikolon, und darum steht der Bau des Standwerts im
     * Plugin als EIGENE Anweisung.
     */
    private static final Pattern STANDBAU = Pattern.compile(
            "new (BundBefehle(?:[.]Stand)?)\\(([^;]*)\\)");

    /**
     * Was in den zwei Zusammenbauten vorkommen DARF - wieder ein enger Vorrat.
     *
     * <p>Die vier Angaben des Standwerts, die vier Griffe der Verwaltungszweige,
     * der Spielzeitzweig und die zwei Grenzen. <b>Die Griffe stehen mit drin,</b>
     * weil der Auszug gerade dort eine neue Gelegenheit geschaffen hat: Vorher
     * stand jeder Zweig an seinem Wort im Baum, jetzt gehen vier gleich geformte
     * Lambdas in einen Konstruktor. Vertauscht sagt {@code /freimarken welt} die
     * Vorgenerierung auf, und jede Zahltafel bleibt gruen.
     */
    private static final Pattern STANDQUELLE = Pattern.compile(
            "getPluginMeta|Runtime[.]version|getMinecraftVersion|fluechtig"
                    + "|spielzeitBefehle[.]spielzeit|zeigeWelt"
                    + "|zeigeVorgenerierung|neulade|bringeZurParzelle"
                    + "|grenze[.]von|grenze[.]bis");

    /** Der Handbalken mit seinem Empfaenger und dem Wortlaut, den er bekommt. */
    private static final Pattern BALKENWACHE = Pattern.compile(
            "(\\w+)\\.sendActionBar\\((\\w+)\\(");

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
    @Test
    @DisplayName("31ab - jede Startpruefung des Plugins wird auch GERUFEN")
    void jedeStartpruefungWirdGerufen() {
        // DER FEHLER, DEN DIESE PROBE FAENGT, ist am 09.11.2026 wirklich passiert: Die
        // Pruefung der Ladeneinheiten war fertig gebaut, mit Javadoc und Klagetexten -
        // und niemand rief sie. Ein Probenlauf meldet so etwas nie: Die Methode ist
        // richtig, sie ist nur tot. Erst im Spiel faellt es auf, und zwar dadurch, dass
        // ein Fehler NICHT gemeldet wird - also gar nicht.
        //
        // FreimarkenPlugin ist ohne laufenden Server nicht zu starten, pruefeTore und
        // pruefeLadeneinheiten haben darum keine Verhaltensprobe. Was der Quelltext
        // hergibt, ist diese eine Zusage - und sie ist die wichtigste von ihnen.
        //
        // Quellen.text liest OHNE Kommentare: Ein Aufruf, der nur in einem Javadoc
        // steht, ruft nichts.
        String plugin = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));

        // Die Namen aus dem Quelltext holen und nicht hier auflisten: Eine Liste von
        // Hand bliebe beim naechsten Zuwachs stehen, und dann ist die neue Pruefung
        // genauso tot wie die alte - nur dass jetzt eine gruene Probe daneben steht.
        var namen = new java.util.TreeSet<String>();
        var sucher = java.util.regex.Pattern
                .compile("private (?:void|String) (pruefe[A-Z][A-Za-z]*)\\(")
                .matcher(plugin);
        while (sucher.find()) {
            namen.add(sucher.group(1));
        }
        assertFalse(namen.isEmpty(),
                "keine einzige pruefe-Methode gefunden - dann prueft diese Probe nichts");

        var tot = new java.util.TreeSet<String>();
        for (String name : namen) {
            // Zweimal: die Deklaration und mindestens ein Aufruf. Eine Methode, die nur
            // einmal im Text steht, ist nur erklaert.
            if (plugin.split(name + "\\(", -1).length - 1 < 2) {
                tot.add(name);
            }
        }
        assertEquals(java.util.Set.of(), tot,
                "diese Pruefmethoden werden nirgends gerufen - sie sind gebaut,"
                        + " begruendet und tot: " + tot);
    }
    @Test
    @DisplayName("31ac - der Startwert wird beim Anlegen WIRKLICH gesetzt")
    void startwertWirdBeimAnlegenGesetzt() {
        // DIE EINSPRITZUNG, DIE KEINE PROBE FING (gemessen am 09.11.2026, Fall S6):
        // bauplan.seed(...) aus Weltenbau entfernt - alle 2.418 Proben blieben gruen.
        // Der Grund ist, dass Weltenbau ohne laufenden Server nicht zu betreten ist:
        // WorldCreator, createWorld, getServer. Es gibt dort keine Verhaltensprobe und
        // kann keine geben.
        //
        // Was dann passiert, ist das Schlimmste aus der Reihe der stillen Fehler: Der
        // Schluessel welten.<name>.startwert steht in der config.yml, der Leser liest
        // ihn, die Weltenliste klagt sogar ueber doppelte Werte - und das Gelaende wird
        // trotzdem gewuerfelt. Der Betreiber glaubt an eine wiederholbare Welt und misst
        // seine Tore und Ladeneinheiten in eine ein, die er nie wieder herstellen kann.
        // Genau diese Lage gab es am 09.11.2026 schon einmal, und sie ist der Grund,
        // warum es den Startwert ueberhaupt gibt.
        //
        // Quellen.text liest OHNE Kommentare: Eine Zusage im Javadoc setzt keinen Seed.
        String bau = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString().equals("Weltenbau.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Weltenbau.java fehlt")));

        assertTrue(bau.contains("bauplan.seed(angabe.startwert())"),
                "ohne diese Zeile ist welten.<name>.startwert eine Zahl, die nie wirkt -"
                        + " und jeder eingemessene Quader haengt wieder an einer Welt,"
                        + " die niemand wiederherstellen kann");
        // Und die zweite Haelfte derselben Sache: Eine BESTEHENDE Welt behaelt ihren
        // Startwert, WorldCreator.seed wird fuer sie stillschweigend uebergangen. Ohne
        // diesen Vergleich glaubte der Betreiber, eine Aenderung in der Datei habe
        // gewirkt - dieselbe Falle wie bei der Umgebung, die daneben schon geprueft wird.
        assertTrue(bau.contains("welt.getSeed()"),
                "die Abweichung zwischen Datei und bestehender Welt MUSS gemeldet"
                        + " werden - sonst wirkt eine Aenderung scheinbar und nicht");
    }
    @Test
    @DisplayName("31ad - ein Weltschluessel wird nie von Hand zusammengesetzt")
    void weltschluesselNieVonHand() {
        // DER FEHLER, DEN DIESE PROBE FAENGT, ist am 09.11.2026 IM SPIEL gefunden
        // worden - keine der 2.421 Proben sah ihn:
        //
        // Die config.yml schreibt NAMEN (laeden.welt: altfurt), die Welt traegt
        // SCHLUESSEL (World.key().asString() = freimarken:altfurt). Drei Stellen
        // verglichen das eine mit dem anderen und bekamen immer "ungleich". Sichtbar war
        // nur eine davon: "/laden mieten" sagte "Die Markthalle steht in Altfurt - dort
        // wird gemietet", mitten in Altfurt. Die anderen zwei schwiegen, und das ist das
        // Schlimmere - der Ladenschutz hielt sich ueberall fuer nicht zustaendig (also
        // durfte in jedem gemieteten Laden jeder bauen), und Ladenware fand die Ladenwelt
        // nie, raeumte also keine Truhe.
        //
        // WARUM KEINE PROBE ES SAH: Jede Probe dieser Klassen gibt den Schluessel schon
        // fertig herein ("freimarken:altfurt"). Sie prueft damit eine andere Eingabe als
        // die Wirklichkeit - die Naht zwischen Datei und Welt liegt davor, in
        // FreimarkenPlugin, und das ist ohne Server nicht zu betreten.
        //
        // MarktBefehle machte es seit jeher richtig, aber von Hand: "freimarken:" + name.
        // Genau diese Handarbeit ist der Fehler - sie wird an der naechsten Stelle
        // vergessen, und dann schweigt es.
        String plugin = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));

        assertFalse(plugin.contains("\"freimarken:\" +"),
                "ein Weltschluessel gehoert nicht von Hand zusammengesetzt - dafuer gibt"
                        + " es weltschluessel(name), und nur so gilt dieselbe Regel auch"
                        + " an der naechsten Stelle");
        assertTrue(plugin.contains("new NamespacedKey(this, name).asString()"),
                "die eine Stelle, an der aus einem Namen ein Schluessel wird, MUSS es"
                        + " geben - sonst steht die Handarbeit nur woanders");
    }
    @Test
    @DisplayName("31ae - jeder Verbraucher eines Weltschluessels bekommt ihn umgewandelt")
    void jederVerbraucherBekommtDenSchluessel() {
        // DIE ANDERE HAELFTE VON 31ad, und sie fehlte: Jene Probe verbietet das
        // Handkleben ("freimarken:" + name). Diese hier verbietet das WEGLASSEN - und
        // das war der Fehler, der am 09.11.2026 im Spiel auffiel.
        //
        // Gemessen mit einer Einspritzung (W2): LadenBefehle bekam wieder ladenwelt
        // statt weltschluessel(ladenwelt) - alle 2.422 Proben blieben gruen, und der
        // ganze Ladenbereich war wieder tot (Mieten abgewiesen, Ladenschutz ueberall
        // nicht zustaendig, keine Truhe gefunden). Ein Waechter, der nur die eine
        // Haelfte haelt, ist kein Waechter.
        //
        // DIE LISTE DER VERBRAUCHER KOMMT AUS DEM QUELLTEXT: Wer ein Feld
        // "private final String weltschluessel" hat, erwartet einen Schluessel. Eine
        // handgeschriebene Liste bliebe beim naechsten Verbraucher stehen, und dann ist
        // genau der ungeschuetzt - derselbe Grund wie bei 31ab.
        //
        // NICHT geprueft wird jedes Vorkommen eines Weltnamens: "welten.get(ladenwelt)"
        // ist richtig, denn jene Karte ist nach NAMEN geschluesselt, und
        // "... in " + ladenwelt + ..." ist eine Meldung. Der Name selbst ist nicht
        // falsch - falsch ist, ihn dort zu uebergeben, wo ein Schluessel erwartet wird.
        var verbraucher = new java.util.TreeSet<String>();
        for (java.nio.file.Path datei : Quellen.alle()) {
            if (Quellen.text(datei).contains("private final String weltschluessel")) {
                String name = datei.getFileName().toString();
                verbraucher.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        assertFalse(verbraucher.isEmpty(),
                "keine einzige Klasse mit einem weltschluessel-Feld gefunden - dann"
                        + " prueft diese Probe nichts");

        String plugin = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));

        var ohneUmwandlung = new java.util.TreeSet<String>();
        for (String klasse : verbraucher) {
            int stelle = plugin.indexOf("new " + klasse + "(");
            if (stelle < 0) {
                ohneUmwandlung.add(klasse + " (wird gar nicht gebaut)");
                continue;
            }
            // Die Argumentliste: von der oeffnenden Klammer bis zu ihrer schliessenden.
            int tiefe = 0;
            int ende = stelle;
            for (int i = plugin.indexOf('(', stelle); i < plugin.length(); i++) {
                if (plugin.charAt(i) == '(') {
                    tiefe++;
                } else if (plugin.charAt(i) == ')') {
                    tiefe--;
                    if (tiefe == 0) {
                        ende = i;
                        break;
                    }
                }
            }
            if (!plugin.substring(stelle, ende).contains("weltschluessel(")) {
                ohneUmwandlung.add(klasse);
            }
        }
        assertEquals(java.util.Set.of(), ohneUmwandlung,
                "diese Klassen erwarten einen Weltschluessel und bekommen ihn nicht aus"
                        + " weltschluessel(name): Die config.yml schreibt \"altfurt\","
                        + " die Welt traegt \"freimarken:altfurt\", und der Vergleich der"
                        + " beiden ist immer ungleich - eine Absage, die niemand"
                        + " versteht, oder ein Schutz, der schweigend nicht greift: "
                        + ohneUmwandlung);
    }
}
