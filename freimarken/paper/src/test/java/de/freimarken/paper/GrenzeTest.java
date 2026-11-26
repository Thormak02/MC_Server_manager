package de.freimarken.paper;

import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Wirtschaftsstand;
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
            "FreimarkenPlugin.java", "Sprachstand.java",
            // Die Farbwelt [21.11.2026]: Hier wird aus einer ZAHL der Datei eine Farbe,
            // und das ist genau EINE Stelle. Der Unterschied zu einem Farbcode im
            // Quelltext ist der ganze Punkt dieser Tafel: Dort stuende die Farbe eines
            // SATZES im Programm; hier steht der ausgelieferte Wert einer ROLLE, die der
            // Betreiber in der Sprachdatei ueberschreibt. Ohne eine solche Stelle gaebe
            // es keinen Weg von "#a5b4bf" zu einer Farbe.
            "Farbtafel.java"));

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
            "AnfragenBefehle.java",
            // Das Anmeldeformular [21.11.2026]: EINE Sendestelle fuer den
            // Chatweg - dieselben Zeilen, die ins Fenster gegangen waeren, plus
            // den Hinweis, wie man ohne Fenster zustimmt.
            "Anmeldeformular.java",
            "Ankunftswache.java", "Aufwaermzeit.java",
            "BerufeBefehle.java",
            // Der Verfallslauf [Teil E5]: Er warnt den Stadtbesitzer beim Beitritt und
            // meldet ihm die Wiederbelebung. Eine Durchsage, die am Beitritt haengt,
            // weil ein abwesender Besitzer sonst nie erfaehrt, dass seine Stadt
            // ablaeuft.
            "Verfallslauf.java",
            // Die taegliche Ladengebuehr [Teil E6]: EINE Sendestelle - die Zeile an
            // den Haendler, dessen Laden sie gekostet hat. Dass die Ware noch in der
            // Kiste liegt, erfaehrt er nur hier.
            "Ladengebuehrlauf.java",
            // Und der Besuchsstempel [Teil E5]: EINE Sendestelle, die EINE Zeile
            // sendet - dass eine verfallene Stadt wieder aufgewacht ist. Das Stempeln
            // selbst sendet nie; es faellt 1.440 Mal am Tag.
            "Besuchslauf.java",
            // Das Blockbuch: EINE Sendestelle fuer beide Zweige und jede Absage. Sie
            // antwortet dem SENDER und nicht dem Executor - bei
            // /execute as <spieler> run freimarken blockbuch schaut der Spieler den
            // Block an, lesen will es der, der getippt hat.
            "BlockBefehle.java",
            "Bonuswache.java",
            "BundBefehle.java", "LadenBefehle.java", "FreistadtBefehle.java",
            "FreimarkenPlugin.java", "MarktBefehle.java",
            // Teil C: die zwei Beitrittszeilen und die Zeilen des taeglichen
            // Laufs - alle durch dasselbe private sende().
            "Mietwache.java",
            "ParzellenBefehle.java",
            // /portal [25.11.2026]: EINE Sendestelle fuer jede Antwort, dazu die
            // Konsolenabsage - jene kann nicht durch sende() laufen, weil es dort
            // keinen Player gibt. Dieselben zwei wie bei EinsichtBefehle.
            "PortalBefehle.java", "Portalwache.java",
            // /hilfe [21.11.2026]: EINE Sendestelle fuer die ganze Liste - sie geht
            // als Liste von Zeilen heraus, nicht Zeile fuer Zeile.
            "HilfeBefehle.java",
            // /regeln [21.11.2026]: EINE Sendestelle, und sie bedient BEIDE Wege - den
            // Chatzweig und den Rueckfall, wenn das Fenster nicht aufgeht. Das FENSTER
            // selbst sendet nichts: Was darin steht, ist kein Chat.
            "RegelnBefehle.java",
            // /menu [21.11.2026]: EINE Sendestelle fuer die zwei Absagen. Das MENUE
            // selbst sendet nichts - es oeffnet ein Inventar, und was darin steht, ist
            // kein Chat.
            "MenueBefehle.java",
            "Rangwache.java",
            // Die Rundgangswache [21.11.2026]: EINE Sendestelle - was ein
            // Spieler beim ERSTEN Betreten einer Station liest. Beim zweiten
            // schweigt sie, und das ist der haeufigste Fall.
            "Rundgangswache.java",
            // /rang [20.11.2026]: EINE Sendestelle fuer beide Zweige und jede Absage.
            "RangBefehle.java",
            "Reise.java", "RueckrollBefehle.java", "RueckwegBefehle.java", "Schutzwache.java",
            // Plan 7.4: die Zeilen des Anlegens und die Handbalken des Schutzes -
            // zwei Wege, weil Chat und Handbalken zwei verschiedene Aufrufe sind.
            "Shopwache.java",
            // Plan 7.4, der HANDEL: ein sendActionBar im Klickzweig. Ein Handel
            // redet nie im Chat - ein Spieler, der zwanzig Stapel kauft, klickt
            // zwanzigmal, und zwanzig Chatzeilen waeren ein Regen.
            "Shopklick.java",
            // /shop: EINE Sendestelle fuer vier Zweige und alle ihre Absagen.
            "ShopBefehle.java",
            // Plan 7.2, die Freie Truhe: EINE Sendestelle fuer Oeffnen, Abweisen und
            // Abrechnen. Sie redet im CHAT und nicht im Handbalken, anders als der
            // Handel - eine Rueckgabe ueber dem Tagesdeckel muss lesbar bleiben,
            // nachdem die Kiste zu ist.
            "Truhenwache.java",
            // Die Einrichtung der Stadt [11.11.2026]: EIN Handbalken, wenn eine Kiste
            // zubleibt, die niemandem gehoert.
            "Einrichtungswache.java",
            "SpielzeitBefehle.java", "Sprache.java", "WarpBefehle.java",
            // Schritt 10: der Befehl der Meerinseln. EINE Sendestelle, wie bei
            // /freistadt - jeder Zweig geht durch sende().
            "WerderBefehle.java",
            // Schritt 11: /wolke - eine Sendestelle, dieselbe Bauart wie /werder.
            "WolkeBefehle.java",
            // Schritt 14: /lotterie - EINE Sendestelle, wie /werder und /wolke. Jeder
            // Zweig geht durch sende(), auch die Absage an die Konsole.
            "LotterieBefehle.java",
            // Schritt 14: der Angelwettbewerb. ZWEI Dateien, und die zweite ist eine
            // WACHE - die erste im Haus, die sendet. Sie muss es: Die Ansage, dass ein
            // Fenster aufgeht, und die Siegerliste am Ende gehen an alle, und sie
            // entstehen im Takt und nicht auf einen Befehl. Beide haben EINE
            // Sendestelle.
            "WettbewerbBefehle.java", "Wettbewerbswache.java",
            // Schritt 16a Teil A: /team - EINE Sendestelle, wie /werder, /wolke und
            // /lotterie. Jeder der fuenf Zweige geht durch sende(), auch die
            // Absagen.
            "TeamBefehle.java",
            // Schritt 16a Teil C: /melden, /meldung und die Meldewache. DREI
            // Dateien, und die dritte ist eine WACHE - sie liefert die Antworten
            // beim Beitritt aus, denn eine Zeile an einen Abwesenden geht an
            // niemanden. Alle drei haben EINE Sendestelle.
            "MeldeBefehle.java", "MeldungBefehle.java", "Meldewache.java",
            // Schritt 16a Teil B: /strafe samt /bannliste und die Strafwache. Die
            // WACHE sendet an zwei Stellen - die Zeile an den Stummgeschalteten und
            // die an den, dessen Fluesterbefehl gesperrt ist. Die Zeile an der TUER
            // sendet sie nicht: Dort geht sie ueber disallow, denn der Spieler ist
            // noch nicht im Spiel.
            "StrafBefehle.java", "Strafwache.java",
            // Schritt 16a Teil D: /einsehen und seine Wache. Die Wache hat
            // EINE Sendestelle, und sie ist die Zusage des Teils: "hinsehen
            // duerfen, anfassen nicht" muss man LESEN koennen.
            "EinsichtBefehle.java", "Einsichtswache.java"));

    /**
     * Wo ein Satz aus Text werden darf - zwei Dateien, und das ist der ganze Weg
     * nach Adventure.
     *
     * <p>{@code Sprache} baut die Saetze der Aufzaehlungen, {@code Sprachliste} die
     * Klagen ueber eine kaputte {@code sprache.yml}. Jede dritte Datei in dieser Menge
     * schreibt einen Satz selbst - siehe das Javadoc von {@link #MINIMESSAGE_LESEN}.
     */
    private static final Set<String> MINIMESSAGE_ERLAUBT = new TreeSet<>(List.of(
            // Die Farbwelt [21.11.2026]: Die Farbtafel BAUT den Leser - sie liest keinen
            // Satz. Dass sie trotzdem hier steht, liegt am Muster: "MiniMessage" trifft
            // auch den Bauer. Die Datei auszunehmen waere der falsche Weg (eine
            // ausgenommene Datei ist fuer immer blind), und ein engeres Muster waere eine
            // zweite Abschrift der Frage.
            "Farbtafel.java",
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
     * waren die rote Absage und die graue Liste darunter, ebenfalls immer zusammen.
     * {@code RueckwegBefehle} bleibt dabei auf 1: Es hatte nur eine Sendung und hat sie
     * noch.
     *
     * <p><b>Mit Schritt 9 steht sie wieder auf 6, und der Weg dorthin war ein Fehler:</b>
     * Die drei Zweige in die Freistaedte brachten je eine Sendung mit - und zwar genau
     * die Art, vor der der Absatz darueber warnt, naemlich einen Satz, der aus dem
     * Zeilenbau heraus und ins Programm zurueckgewandert war. Einer davon warf im
     * Betrieb, weil sein Satz eine Umbruchmarke traegt. Heute gehen alle sechs ueber die
     * Naht; was die Zahl allein nicht mehr sagt, sagt {@link #AUFRUFE_IN_WARPBEFEHLE}.
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
            Map.entry("AnfragenBefehle.java", 12),
            Map.entry("Anmeldeformular.java", 1),
            Map.entry("Ankunftswache.java", 3),
            Map.entry("Aufwaermzeit.java", 2),
            // EINS, und das ist eine Entscheidung: Alle fuenf Zweige von /beruf laufen
            // durch denselben Sendeweg. Der Listenzweig sendete anfangs selbst - dann
            // stuende hier eine Zwei, und eine zweite Stelle koennte wegfallen, ohne dass
            // die Zahl faellt.
            Map.entry("BerufeBefehle.java", 1),
            // EINE Sendestelle fuer alle vier Zweige von /shop und jede ihrer Absagen:
            // alles geht durch dasselbe private sende(). Eine Zwei hiesse, dass ein
            // Zweig sich einen eigenen Weg gebaut hat - und der waere der erste, dessen
            // Zeilen keine Bildtafel haelt.
            //
            // Die Zahl ist dabei nur die halbe Zusage: Sie sagt nichts darueber, WAS
            // gesendet wird. Den Treffertext halten die Bildtafel und die Zweigproben.
            Map.entry("ShopBefehle.java", 1),
            // EINE Sendestelle fuer BEIDE Zweige des Blockbuchs und fuer jede seiner
            // sieben Absagen: alles geht durch dasselbe private sende(). Eine Zwei
            // hiesse, dass ein Zweig sich einen eigenen Weg gebaut hat - und der waere
            // der erste, dessen Zeilen keine Bildtafel haelt.
            //
            // Geantwortet wird darin dem SENDER und nicht dem Executor: Bei
            // /execute as <spieler> run freimarken blockbuch schaut der Spieler den
            // Block an, lesen will es der, der getippt hat.
            Map.entry("BlockBefehle.java", 1),
            // Teil H: EINE Sendestelle - die Nachricht ueber den gezahlten Bonus.
            Map.entry("Bonuswache.java", 1),
            Map.entry("BundBefehle.java", 2),
            // 44 + 6 fuer besitzzweig() [22.11.2026] + 14 fuer den Altfurtzweig
            // [23.11.2026]: 6 fuer die Benutzung von /freimarken altfurt und 8
            // fuer die Antwort auf sichern - zwei Absagen, Kopf, unbekannter
            // Name, Gesichertes, je ein Schon-da, je eine Klage, "nichts zu tun".
            Map.entry("FreimarkenPlugin.java", 65),
            // EINE Sendestelle, und das BLEIBT so, obwohl Teil B drei Taten dazugebracht
            // hat: Alle vier Zweige von /laden laufen durch dasselbe sende(). Eine Zwei
            // hiesse, dass ein Zweig sich einen eigenen Weg gebaut hat - und der waere
            // der erste, dessen Zeilen keine Tafel haelt.
            Map.entry("LadenBefehle.java", 1),
            Map.entry("FreistadtBefehle.java", 1),
            // ZWEI: die Zeile fuer die Konsole (sie hat keine Hand und steht in keiner
            // Welt) und der eine Sendeweg fuer alle Spielerzeilen. Eine Zahl weniger
            // hiesse, dass ein Zweig von /markt stumm bleibt; eine mehr, dass neben
            // Markttext eine zweite Sendestelle gebaut wurde.
            Map.entry("MarktBefehle.java", 2),
            // Teil C: EINE Stelle fuer drei Zweige - Beitritt (zweimal) und Takt gehen
            // alle durch dasselbe private sende(). Haette jeder Zweig sein eigenes,
            // stuende hier eine 3, und eine verlorene Sendung waere nicht zu sehen.
            Map.entry("Mietwache.java", 1),
            // ZWEI im Verfallslauf [Teil E5]: die Warnung beim Beitritt und - ueber
            // denselben forEach - die Meldung, dass die Stadt verfallen IST. Beide
            // gehen durch Freistadttext.verfallszeilen, aber die Stelle zaehlt, nicht
            // der Satz.
            Map.entry("Verfallslauf.java", 1),
            // EINS im Besuchsstempel: die Wiederbelebung. Das Stempeln sendet nie.
            Map.entry("Besuchslauf.java", 1),
            // EINS im Tageslauf der Ladengebuehr: die Zeile an den Haendler. Das
            // ZAHLEN sendet nie - es faellt taeglich und waere eine Durchsage ohne
            // Anlass.
            Map.entry("Ladengebuehrlauf.java", 1),
            // 47 -> 51 mit dem Muenzhaus [Teil G]: die Tafel, der Goldverlauf, die
            // Rueckfrage und der Kauf.
            Map.entry("ParzellenBefehle.java", 51),
            Map.entry("Portalwache.java", 1),
            Map.entry("HilfeBefehle.java", 1),
            Map.entry("MenueBefehle.java", 1),
            Map.entry("RegelnBefehle.java", 1),
            Map.entry("Rangwache.java", 1),
            Map.entry("Rundgangswache.java", 1),
            // /rang: beide Zweige, die Absage an die Konsole, die Stoerung und "keine
            // Raenge" gehen durch dasselbe private sende(). Eine Zwei hier heisst, dass
            // sich ein Zweig seinen eigenen Weg gebaut hat.
            Map.entry("RangBefehle.java", 1),
            Map.entry("Reise.java", 8),
            // Der RUECKWEG des Blockbuchs: EINE Sendestelle, und das ist die
            // Entscheidung dieses Zweiges. Elf Absagen und zwei Ergebniszeilen gehen
            // alle durch dasselbe private sende() - eine Zwei hier heisst, dass sich
            // ein Zweig seinen eigenen Weg an den Sender gebaut hat.
            Map.entry("RueckrollBefehle.java", 1),
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
            // Plan 7.2, die Freie Truhe: EINE Sendestelle fuer alle drei Lagen -
            // Oeffnen, Abweisen und Abrechnen laufen durch dasselbe private sende(). Eine
            // Zwei hiesse, dass ein Zweig sich einen eigenen Weg gebaut hat, und der
            // waere der erste, dessen Zeilen keine Bildtafel haelt. Die Zahl ist dabei
            // nur die halbe Zusage: WAS gesendet wird, halten die Bildtafel und
            // TruhenwacheTest.
            Map.entry("Truhenwache.java", 1),
            Map.entry("Einrichtungswache.java", 1),
            Map.entry("SpielzeitBefehle.java", 4),
            Map.entry("Sprache.java", 3),
            // 7 seit Teil E3: der Satz, der eine Stadt ohne erfuellte Auflage
            // abweist. Ohne ihn erschien sie als warp.stadt-unbekannt, also als
            // Tippfehler.
            Map.entry("WarpBefehle.java", 7),
            Map.entry("WerderBefehle.java", 1),
            Map.entry("WolkeBefehle.java", 1),
            Map.entry("LotterieBefehle.java", 1),
            Map.entry("WettbewerbBefehle.java", 1),
            Map.entry("Wettbewerbswache.java", 1),
            Map.entry("TeamBefehle.java", 1),
            Map.entry("MeldeBefehle.java", 1),
            Map.entry("MeldungBefehle.java", 1),
            Map.entry("Meldewache.java", 1),
            // /strafe sendet an DREI Stellen: die Antwort an den Absender, die
            // Ansage an alle und die Ansage der Aufhebung. Die Strafwache an ZWEI:
            // Chat und gesperrter Befehl.
            Map.entry("StrafBefehle.java", 3),
            Map.entry("Strafwache.java", 2),
            // Zwei: die eine Sendestelle an den Spieler und die Absage an die
            // Konsole. Letztere geht an den Sender und nicht an einen Spieler
            // - darum kann sie nicht durch sende() laufen.
            Map.entry("EinsichtBefehle.java", 2),
            // NACHGEZAEHLT und nicht abgeschrieben: eine Zeile in sende(), eine in
            // nurSpieler(). Jene geht an den Sender und nicht an einen Player,
            // denn von der Konsole gibt es keinen.
            Map.entry("PortalBefehle.java", 2),
            Map.entry("Einsichtswache.java", 1));

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
            // Teil G: die Tafel des Muenzhauses und der Goldverlauf. Beide tragen die
            // Stoerungswache - darum sind sie Methoden und keine Zeile im Zweig.
            Map.entry("muenzhaustafelZeilen", 1),
            Map.entry("muenzverlaufZeilen", 1),
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
                    // Teil F: /mark tauschen zeigt die Tafel der Wechselstube. Die
                    // zwei Richtungen stehen hier NICHT - sie rufen Tauschtext
                    // unmittelbar, und dessen Methoden zaehlt diese Tafel nicht.
                    // Gedeckt sind sie durch TauschtextTest und MarkBefehlTest.
                    Map.entry("tauschtafelZeilen", 1),
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
            Map.entry("zuSchnellZeilen", 1),
            Map.entry("zutrittZeilen", 1));

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
            Map.entry("zieleZeilen", 1),
            // Die drei Zweige in die Freistaedte, nachgezogen am 11.11. Sie sendeten
            // anfangs mit sprache.eineZeile aus dem Zweig heraus - als einzige in dieser
            // Datei -, und warp.stadt-unbekannt traegt eine Umbruchmarke: Der Zweig warf,
            // statt die Absage zu senden. ZWEI fuer keineStadtweltZeilen, weil zwei
            // Zweige dieselbe Lage haben: /warp freistadt ohne Namen und mit Namen.
            Map.entry("keineStadtweltZeilen", 2),
            Map.entry("welcheStadtZeilen", 1),
            Map.entry("keineStadtZeilen", 1),
            // Teil E3: die Absage an eine Stadt, deren Auflage offen ist. EIN Aufruf -
            // der Riegel sitzt an genau einer Stelle, zwischen dem gefundenen Namen
            // und der Reise.
            Map.entry("ohneAuflageZeilen", 1));

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
            "SCHUTZ_EINRICHTUNG -> HANDBALKEN",
            // Die Stadtwache [11.11.2026]. ALLE SECHS Handbalken, und das ist
            // keine Gewohnheit: Ein Satz, den ein Spieler liest, waehrend er
            // baut, darf den Bau nicht unterbrechen - sendActionBar nimmt ein
            // Component und keine Liste.
            "SCHUTZ_STADT_LEERE -> HANDBALKEN",
            "SCHUTZ_STADT_AUSSERHALB -> HANDBALKEN",
            "SCHUTZ_ANKUNFTSFELD -> HANDBALKEN",
            "SCHUTZ_STADT_FREI -> HANDBALKEN",
            "SCHUTZ_STADT_VERFALLEN -> HANDBALKEN",
            "SCHUTZ_FREMDE_STADT -> HANDBALKEN",
            // Auch dieser ist ein Handbalken, und hier ist es besonders wichtig:
            // Er erscheint, waehrend jemand BAUT - eine zweite Zeile wuerde dort
            // stumm verschwinden, und der Stadtbesitzer wuesste nicht, warum sein
            // Block nicht steht.
            "SCHUTZ_FREMDES_GRUNDSTUECK -> HANDBALKEN",
            "SCHUTZ_BUERGER_DARF_NICHT -> HANDBALKEN",
            "SCHUTZ_STADT_GESTOERT -> HANDBALKEN",
            // Die fuenf Lagen von Hafenwerder [Schritt 10].
            "SCHUTZ_INSEL_MEER -> HANDBALKEN",
            "SCHUTZ_INSEL_ANKUNFT -> HANDBALKEN",
            "SCHUTZ_INSEL_FREI -> HANDBALKEN",
            "SCHUTZ_FREMDE_INSEL -> HANDBALKEN",
            "SCHUTZ_INSEL_GESTOERT -> HANDBALKEN",
            // Schritt 11: drei Lagen in Wolkenwerder. Die fremde Insel und das
            // Gestoert-Wort fehlen hier mit Absicht - die gelten fuer BEIDE
            // Inselwelten und sagen dort Wort fuer Wort dasselbe.
            "SCHUTZ_WOLKE_LEERE -> HANDBALKEN",
            "SCHUTZ_WOLKE_ANKUNFT -> HANDBALKEN",
            "SCHUTZ_WOLKE_FREI -> HANDBALKEN",
            "ANKUNFT_KEIN_SPAWNPUNKT -> SATZ",
            "ANKUNFT_ABENTEUER -> SATZ",
            "ANKUNFT_HEIMGEHOLT -> SATZ",
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
            "BEFEHL_FREISTADT -> WORT",
            "BEFEHL_SHOP -> WORT",
            "BEFEHL_WERDER -> WORT",
            "BEFEHL_WOLKE -> WORT",
            "BEFEHL_RANG -> WORT",
            "BEFEHL_MENU -> WORT",
            "BEFEHL_REGELN -> WORT",
            "BEFEHL_HILFE -> WORT",
            "BEFEHL_LOTTERIE -> WORT",
            "BEFEHL_WETTBEWERB -> WORT",
            "BEFEHL_TEAM -> WORT",
            "BEFEHL_MELDEN -> WORT",
            "BEFEHL_MELDUNG -> WORT", "BEFEHL_STRAFE -> WORT",
            "BEFEHL_BANNLISTE -> WORT",
            "BEFEHL_EINSEHEN -> WORT",
            "BEFEHL_PORTAL -> WORT",
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
                    + "|italic|underlined|strikethrough|obfuscated"
                    // Die acht Rollen der Farbwelt [21.11.2026]. Ohne sie waere genau
                    // der Fehler, gegen den diese Tafel gebaut ist, ab heute wieder
                    // unsichtbar: Ein "<warnung>" + name im Programm nennt keine
                    // Adventure-Klasse und liest keinen Satz - nur dieses Muster sieht
                    // es. Die Namen kommen aus Farbtafel.ROLLEN und sind hier
                    // ausgeschrieben, weil ein Muster kein Java ausfuehrt; die Tafel
                    // 31dd haelt beide Listen gegeneinander.
                    + "|fliesstext|betont|kopf|geld|warnung|bestaetigung|hinweis"
                    + "|gedaempft)>"
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
        // PlayerInteractEvent mit einem Block zu bauen, fuer den tatAn eine Tat nennt -
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

    @Test
    @DisplayName("31v - die Spannen im Stadtfeldgenerator, Zeile fuer Zeile")
    void dieSpannenImStadtfeldgenerator() {
        // Keine Probe kann den Generator laufen lassen - dazu braucht er ein ChunkData
        // vom Server. Also wird der Quelltext gelesen, und zwar die UNTERE und die OBERE
        // Grenze jeder Spanne als TEXT: Genau dort sass der Fehler vom 11.11.2026. Die
        // Wand begann bei Feldboden.BODEN statt bei unten und endete bei BODEN +
        // BARRIERE_HOCH statt bei wandBis() - ein Loch darunter und ein Block zu wenig
        // darueber, und beides sah in einer Zaehlung der Aufrufe voellig richtig aus.
        String quelle = Quellen.text(datei("StadtfelderGenerator.java"));
        List<String> gefunden = new ArrayList<>();
        var spanne = Pattern.compile(
                "setRegion[(]lx, ([^,]+), lz, lx [+] 1, ([^,]+), lz [+] 1,").matcher(quelle);
        while (spanne.find()) {
            gefunden.add(spanne.group(1) + " .. " + spanne.group(2));
        }

        assertEquals(List.of(
                        "unten .. Feldboden.wandBis()",
                        "unten .. unten + 1",
                        "unten + 1 .. Feldboden.ERDE_AB",
                        "Feldboden.ERDE_AB .. Feldboden.BODEN"),
                gefunden,
                "die erste Spanne ist die WAND und muss von der Weltunterkante bis"
                        + " wandBis() reichen; die drei anderen sind Grundgestein, Stein"
                        + " und Erde einer Feldspalte. Steht in der ersten wieder"
                        + " Feldboden.BODEN, ist unter der Wand Luft - und wer am"
                        + " Feldrand einen Block tief graebt und zur Seite tritt, faellt"
                        + " in die Leere.");
    }

    @Test
    @DisplayName("31w - was im Weltenbau wirklich an die Weltgrenze geht")
    void wasImWeltenbauAnDieWeltgrenzeGeht() {
        // Weltenbau braucht einen laufenden Server; keine Probe kann setzeGrenze rufen.
        // Darum steht hier, was den beiden Setzern UEBERGEBEN wird, als Text. Bis zum
        // 11.11.2026 hiess es setSize(wirksam) und setCenter(0.0, 0.0) - fuer die
        // Stadtfelder beides falsch: 6000 um 0/0 schneidet 84 Felder an, und die Mitte
        // der Freistaedtewelt ist -16, nicht 0.
        String quelle = Quellen.text(datei("Weltenbau.java"));
        List<String> gefunden = new ArrayList<>();
        var setzer = Pattern.compile("rand[.](setCenter|setSize)[(]([^;]*)[)];")
                .matcher(quelle);
        while (setzer.find()) {
            gefunden.add(setzer.group(1) + "(" + setzer.group(2) + ")");
        }

        assertEquals(List.of("setCenter(mitte, mitte)", "setSize(kante)"), gefunden,
                "beide Werte muessen aus der abgeleiteten Grenze kommen und nicht aus"
                        + " der Zahl in der Einstellungsdatei. Steht hier wieder"
                        + " setSize(wirksam), laeuft die Weltgrenze der Freistaedte"
                        + " mitten durch Stadtfelder - und ein /warp auf das aeusserste"
                        + " Feld setzt den Spieler VOR die eigene Grenze.");
    }

    @Test
    @DisplayName("31x - das Mindestmass der Weltgrenze steht im Weltenbau genau einmal")
    void dasMindestmassStehtGenauEinmal() {
        // Grenzregel.MINDESTKANTE sind 32 Bloecke. Fuer die Freistaedte gelten 608 - zwei
        // Stadtfelder und eine Leere -, und darunter WIRFT Feldgrenze.ausKante, mitten im
        // Serverstart. Das Mass darf darum nur an der einen Stelle stehen, an der nach
        // der Weltart entschieden wird; alle drei Benutzer (brauchbar, wirksam, klage)
        // bekommen danach dieselbe Variable.
        //
        // Aufgeschrieben wird die ZEILE und nicht ihre Zahl: Eine Zaehlung bliebe gruen,
        // wenn jemand die eine Stelle entfernt und dafuer eine andere einsetzt.
        List<String> zeilen = Quellen.text(datei("Weltenbau.java")).lines()
                .filter(zeile -> zeile.contains("Grenzregel.MINDESTKANTE"))
                .map(String::strip)
                .toList();

        assertEquals(List.of(": Grenzregel.MINDESTKANTE;"), zeilen,
                "steht das Mass wieder unmittelbar in Grenzregel.wirksam oder"
                        + " Grenzregel.brauchbar, prueft der Weltenbau gegen 32 und"
                        + " rechnet gegen 608 - der Server bleibt beim Start stehen.");
    }

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
        //
        // ZWEI FAMILIEN seit dem 19.11.2026: pruefe* UND nenne*. Die zweite kam mit
        // nenneRaenge dazu und waere durch dieses Netz gefallen - dabei ist sie
        // dieselbe Sache: eine Methode, die nur meldet, meldet eben gar nichts, wenn
        // niemand sie ruft. Das Muster faengt beide, und eine dritte Familie muesste
        // hier eingetragen werden - das ist der Preis dafuer, dass es ueberhaupt ein
        // Muster gibt.
        var namen = new java.util.TreeSet<String>();
        var sucher = java.util.regex.Pattern
                .compile("private (?:void|String) ((?:pruefe|nenne)[A-Z][A-Za-z]*)\\(")
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
        // JEDE Gestalt des Namens und nicht nur die eine [geschaerft 18.11.2026]:
        // Gesucht wurde "private final String weltschluessel", und damit war ein Feld,
        // das anders heisst, fuer diese Tafel nicht vorhanden - die Verdienstwache haelt
        // seit Schritt 10 einen inselschluessel. Das Muster trifft jetzt jedes Feld,
        // dessen Name auf "schluessel" endet; ein Waechter, der nur eine Schreibweise
        // kennt, ist dort blind, wo gerade gebaut wird.
        var feldmuster = Pattern.compile(
                "private final String \\w*[Ss]chluessel\\b");
        var verbraucher = new java.util.TreeSet<String>();
        for (java.nio.file.Path datei : Quellen.alle()) {
            if (feldmuster.matcher(Quellen.text(datei)).find()) {
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
                // AUCH DIE FABRIKFORM [18.11.2026]: Mehrere Klassen dieses Projekts
                // entstehen ueber ueberBukkit(...), weil ein Stueck Bukkit darin als
                // Naht steckt - die Angelwache etwa baut dort ihren Stapel. Gesucht
                // wurde nur "new Klasse(", und damit galt sie als "wird gar nicht
                // gebaut": eine Luecke dieser Tafel, keine des Programms. Die PRUEFUNG
                // darunter bleibt dieselbe.
                stelle = plugin.indexOf(klasse + ".ueberBukkit(");
            }
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
    @Test
    @DisplayName("31af - jede Einrichtmethode des Plugins wird auch GERUFEN")
    void jedeEinrichtmethodeWirdGerufen() {
        // DIE ZWEITE HAELFTE VON 31ab, und sie fehlte. Jene Probe sichert die
        // pruefe-Methoden; dieselbe Falle steht aber bei den richte-Methoden offen, und
        // dort ist der Schaden groesser: Eine ungerufene Pruefung meldet einen Fehler
        // nicht, eine ungerufene EINRICHTUNG laesst einen ganzen Bereich gar nicht
        // entstehen. Beim Blockbuch waeren Tabelle, Ablage, Leser, Sammler und Wache
        // gebaut, begruendet, geprueft - und kein Zuhoerer angemeldet. Jede Probe bliebe
        // gruen, und im Spiel waere das Buch fuer immer leer.
        //
        // Quellen.text liest OHNE Kommentare: Ein Aufruf, der nur in einem Javadoc
        // steht, ruft nichts.
        String plugin = plugintext();

        // Die Namen aus dem Quelltext holen und nicht hier auflisten - derselbe Grund
        // wie bei 31ab: Eine Liste von Hand bliebe beim naechsten Zuwachs stehen, und
        // dann ist genau der neue Bereich tot.
        var namen = new java.util.TreeSet<String>();
        var sucher = java.util.regex.Pattern
                .compile("private void (richte[A-Z][A-Za-z]*)\\(")
                .matcher(plugin);
        while (sucher.find()) {
            namen.add(sucher.group(1));
        }
        assertFalse(namen.isEmpty(),
                "keine einzige richte-Methode gefunden - dann prueft diese Probe nichts");

        var tot = new java.util.TreeSet<String>();
        for (String name : namen) {
            if (plugin.split(name + "\\(", -1).length - 1 < 2) {
                tot.add(name);
            }
        }
        assertEquals(java.util.Set.of(), tot,
                "diese Einrichtmethoden werden nirgends gerufen - der ganze Bereich"
                        + " dahinter entsteht nie, und keine Probe sieht es: " + tot);
    }

    @Test
    @DisplayName("31ag - jeder Zeitgeber wird gestartet und VOR der Datenbank beendet")
    void jederZeitgeberWirdGestartetUndVorhalbBeendet() {
        // DREI ZUSAGEN IN EINER PROBE, weil sie dieselbe Naht betreffen: Eine Wache mit
        // starte() und beende() ist ein wiederkehrender Zeitgeber, und davon haengt
        // Geschriebenes ab.
        //
        // Die dritte ist die, die kein Probenlauf je zeigen koennte: beende() MUSS vor
        // datenbank.close() stehen. Steht es danach, laeuft alles weiter wie bisher -
        // nur dass beim Abschalten das Gesammelte verloren geht, weil die Ablage auf
        // eine geschlossene Verbindung schreibt. Sichtbar ist das nur an Zeilen, die
        // fehlen, und zwar genau in der letzten Sekunde vor dem Herunterfahren: der
        // Sekunde, nach der jemand fragt.
        //
        // DIE LISTE DER WACHEN KOMMT AUS DEM QUELLTEXT: Wer starte() UND beende() hat,
        // ist ein Zeitgeber. Eine handgeschriebene Liste bliebe beim naechsten stehen.
        String plugin = plugintext();
        var wachen = new java.util.TreeSet<String>();
        for (java.nio.file.Path datei : Quellen.alle()) {
            String text = Quellen.text(datei);
            if (text.contains("void starte()") && text.contains("void beende()")) {
                String name = datei.getFileName().toString();
                wachen.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        assertFalse(wachen.isEmpty(),
                "keine einzige Wache mit starte() und beende() gefunden - dann prueft"
                        + " diese Probe nichts");

        // Der Koerper von onDisable, und nur er: datenbank.close() steht auch in einem
        // Fehlerzweig beim Aufbau, und dort ist die Reihenfolge eine andere Frage.
        int anfang = plugin.indexOf("public void onDisable()");
        assertTrue(anfang >= 0, "onDisable fehlt in FreimarkenPlugin");
        String abschalten = plugin.substring(anfang);
        int schluss = abschalten.indexOf("datenbank.close()");
        assertTrue(schluss >= 0,
                "onDisable schliesst die Datenbank nicht - dann prueft der Teil dieser"
                        + " Probe nichts, der die Reihenfolge sichert");

        var ungestartet = new java.util.TreeSet<String>();
        var unbeendet = new java.util.TreeSet<String>();
        var zuSpaet = new java.util.TreeSet<String>();
        for (String wache : wachen) {
            String feld = Character.toLowerCase(wache.charAt(0)) + wache.substring(1);
            if (!plugin.contains(feld + ".starte()")) {
                ungestartet.add(wache);
            }
            int beendet = abschalten.indexOf(feld + ".beende()");
            if (beendet < 0) {
                unbeendet.add(wache);
            } else if (beendet > schluss) {
                zuSpaet.add(wache);
            }
        }
        assertEquals(java.util.Set.of(), ungestartet,
                "diese Wachen werden nie gestartet - ihr Zeitgeber laeuft nicht, und"
                        + " alles, was sie sammeln, bleibt fuer immer im"
                        + " Arbeitsspeicher: " + ungestartet);
        assertEquals(java.util.Set.of(), unbeendet,
                "diese Wachen werden in onDisable nicht beendet - ihr letzter Stapel"
                        + " ist beim Herunterfahren verloren, und ihr Zeitgeber laeuft"
                        + " nach dem Abschalten weiter: " + unbeendet);
        assertEquals(java.util.Set.of(), zuSpaet,
                "diese Wachen werden NACH datenbank.close() beendet - sie schreiben"
                        + " dann auf eine geschlossene Verbindung, und das Gesammelte"
                        + " ist verloren. Nichts bricht, nichts wird rot; es fehlen nur"
                        + " Zeilen, und zwar die der letzten Sekunde: " + zuSpaet);
    }

    /** Der Quelltext des Plugins, ohne Kommentare - geteilt von 31ab, 31af und 31ag. */
    private static String plugintext() {
        return Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));
    }
    @Test
    @DisplayName("31aq - jede Ortsregel haengt wirklich an der Schutzwache")
    void jedeOrtsregelHaengtAnDerWache() {
        // DER RIEGEL GEGEN EINE TOTE WACHE, und er hat einen belegten Anlass: Die
        // Ladeneinheiten waren in Teil A reine Geometrie - jeder konnte in jedem Stand
        // bauen -, und in der Stadtfelderwelt griff bis zum 11.11.2026 GAR KEINE Wache.
        // Beide Male war die REGEL richtig gebaut und nur nicht angeschlossen. Kein
        // Probenlauf meldet so etwas: Die Regel hat ihre eigenen Proben, sie sind gruen,
        // und im Spiel geschieht nichts.
        //
        // DIE LISTE KOMMT AUS DEM QUELLTEXT, wie bei 31ab und 31ah: Wer
        // "implements Schutzwache.Ortsregel" schreibt, will geschuetzt haben. Eine
        // handgeschriebene Liste bliebe bei der naechsten Regel stehen - und dann ist
        // genau die ungeschuetzt.
        var regeln = new TreeSet<String>();
        for (Path datei : Quellen.alle()) {
            if (Quellen.text(datei).contains("implements Schutzwache.Ortsregel")) {
                String name = datei.getFileName().toString();
                regeln.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        assertFalse(regeln.isEmpty(), "keine einzige Ortsregel gefunden - dann prueft"
                + " diese Probe nichts");

        String plugin = plugintext();
        var nichtAngeschlossen = new TreeSet<String>();
        for (String regel : regeln) {
            // Gesucht wird das ERZEUGEN - "new Stadtwache(" -, nicht der blosse Name:
            // Ein Feld vom Typ der Regel, das nie besetzt wird, waere sonst ein
            // gueltiger Nachweis.
            if (!plugin.contains("new " + regel + "(")) {
                nichtAngeschlossen.add(regel + " (wird nie gebaut)");
            }
        }
        assertEquals(Set.of(), nichtAngeschlossen,
                "diese Ortsregeln gibt es im Quelltext, aber das Plugin baut sie nie -"
                        + " dann sind sie fertige, gepruefte und WIRKUNGSLOSE Klassen,"
                        + " und in ihrer Welt darf jeder alles: " + nichtAngeschlossen);

        // Und die zweite Richtung: Was gebaut wird, muss auch an die Wache gehen. Die
        // Schutzwache nimmt GENAU EINE Ortsregel; mehrere kommen ueber nacheinander(...)
        // herein. Steht eine Regel im Quelltext, wird aber nicht in diesem Aufruf
        // genannt, haengt sie an nichts.
        int anfang = plugin.indexOf("new Schutzwache(");
        assertTrue(anfang >= 0, "FreimarkenPlugin baut keine Schutzwache mehr");
        int tiefe = 0;
        int ende = anfang;
        for (int i = plugin.indexOf('(', anfang); i < plugin.length(); i++) {
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
        String aufruf = plugin.substring(anfang, ende);
        var nichtUebergeben = new TreeSet<String>();
        String aufrufKlein = aufruf.toLowerCase(java.util.Locale.ROOT);
        for (String regel : regeln) {
            // Entweder unmittelbar gebaut ("new Stadtwache(") oder als spaet gelesenes
            // Feld ("() -> stadtwache") - beide Formen nennen den Namen der Regel in
            // diesem Aufruf, die zweite nur kleingeschrieben.
            //
            // OHNE Ruecksicht auf Gross- und Kleinschreibung, und die Annahme dahinter
            // gehoert benannt: Ein Feld heisst in diesem Projekt wie seine Klasse, nur
            // klein. Trifft das eines Tages nicht mehr zu, wird diese Probe rot und
            // verlangt eine genauere Suche - das ist die sichere Richtung. Umgekehrt
            // waere sie still blind.
            if (!aufrufKlein.contains(regel.toLowerCase(java.util.Locale.ROOT))) {
                nichtUebergeben.add(regel);
            }
        }
        assertEquals(Set.of(), nichtUebergeben,
                "diese Ortsregeln werden gebaut, aber nicht an die Schutzwache"
                        + " uebergeben - die fuenfzehn Ereignisarten der Wache fragen"
                        + " sie dann nie: " + nichtUebergeben);
    }

    @Test
    @DisplayName("31ar - jeder Zugang der Schutzwache benennt die Tat, die zu ihm passt")
    void jederZugangBenenntSeineTat() {
        // DIE TAFEL ZU TEIL D1 [12.11.2026]. Dass ein Zugang UEBERHAUPT eine Tat nennt,
        // haelt der Compiler: darfHandeln nimmt sie als Wert, und ein Aufruf ohne sie
        // uebersetzt nicht. Was kein Compiler und keine Verhaltensprobe faengt, ist die
        // FALSCHE Tat - denn solange niemand Rechte abstuft, antworten alle drei gleich,
        // und jede Probe dieses Projekts bliebe gruen. Erst in einer Freistadt, in der
        // der Stadtbesitzer Tueren freigibt und das Bauen sperrt, zeigt sich der Tausch:
        // Stellt jemand beimAbbauen auf BENUTZEN, bauen seine Buerger die Stadt ab.
        //
        // DIE LISTE DER ZUGAENGE KOMMT AUS DEM QUELLTEXT und nicht aus dieser Tabelle:
        // Gesucht wird jedes @EventHandler. Ein neuer Zugang ist damit sofort rot - und
        // zwar mit der Frage, welche Tat er meint, statt stumm durchzulaufen. Das ist der
        // Unterschied zu einer Tafel, die nur ihre eigenen Zeilen prueft.
        String wache = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Schutzwache.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Schutzwache.java fehlt")));

        // Was jeder Zugang nennen MUSS. Ein tatAn oder tatAmWesen steht fuer "die Tat
        // kommt aus dem Block beziehungsweise aus dem Wesen" - diese zwei Methoden sind
        // in TatTest gemessen, und darum genuegt hier ihr Name.
        var erwartet = new java.util.LinkedHashMap<String, Set<String>>();
        erwartet.put("beimVerlassen", Set.of());
        erwartet.put("beimAbbauen", Set.of("BAUEN"));
        erwartet.put("beimSetzen", Set.of("BAUEN"));
        erwartet.put("beimAusgiessen", Set.of("BAUEN"));
        erwartet.put("beimAufschoepfen", Set.of("BAUEN"));
        erwartet.put("beimAnzuenden", Set.of("BAUEN"));
        erwartet.put("beimBildAbnehmen", Set.of("BAUEN"));
        erwartet.put("beimBildAufhaengen", Set.of("BAUEN"));
        erwartet.put("beimWesenAnklicken", Set.of("tatAmWesen"));
        erwartet.put("beimWesenSchaden", Set.of("BAUEN"));
        erwartet.put("beimFahrzeugSchaden", Set.of("BAUEN"));
        erwartet.put("beimWesenSchoepfen", Set.of("BAUEN"));
        erwartet.put("beimLeinen", Set.of("BAUEN"));
        erwartet.put("beimEntleinen", Set.of("BAUEN"));
        // Der Klickzweig nennt beides: die Tat des BLOCKS aus tatAn, und BAUEN fuer die
        // HAND und fuer den Linksklick. Dass es zwei getrennte Fragen sind, prueft
        // SchutzwacheTest nicht - dafuer braeuchte es ein PlayerInteractEvent; die
        // Begruendung dazu steht in 31x.
        erwartet.put("beimKlicken", Set.of("tatAn", "BAUEN"));

        var gefunden = new java.util.LinkedHashMap<String, Set<String>>();
        int stelle = wache.indexOf("@EventHandler");
        while (stelle >= 0) {
            int kopf = wache.indexOf("public void ", stelle);
            assertTrue(kopf > 0, "nach einem @EventHandler muss eine Methode kommen");
            int klammer = wache.indexOf('(', kopf);
            String name = wache.substring(kopf + "public void ".length(), klammer).trim();
            gefunden.put(name, tatenIn(rumpf(wache, klammer)));
            stelle = wache.indexOf("@EventHandler", klammer);
        }

        // Erst die MENGE der Zugaenge, dann ihr Inhalt: Faellt ein Zugang weg oder kommt
        // einer dazu, soll die Klage das sagen und nicht an einer fehlenden Tat haengen.
        assertEquals(erwartet.keySet(), gefunden.keySet(),
                "die Zugaenge der Schutzwache und die Zeilen dieser Tafel muessen"
                        + " dieselben sein - ein Zugang ohne Zeile ist ein Griff, dessen"
                        + " Recht niemand festgelegt hat");
        assertEquals(erwartet, gefunden,
                "jeder Zugang muss GENAU die Tat nennen, die zu ihm passt. Ein Tausch"
                        + " zwischen zwei Taten laesst jede andere Probe unveraendert:"
                        + " Ohne abgestufte Rechte sagen alle drei dasselbe Nein.");
    }

    /** Der Rumpf der Methode, deren Klammer bei {@code klammer} beginnt. */
    private static String rumpf(String text, int klammer) {
        int anfang = text.indexOf('{', klammer);
        int tiefe = 0;
        for (int i = anfang; i < text.length(); i++) {
            if (text.charAt(i) == '{') {
                tiefe++;
            } else if (text.charAt(i) == '}') {
                tiefe--;
                if (tiefe == 0) {
                    return text.substring(anfang, i);
                }
            }
        }
        throw new AssertionError("Methodenrumpf ab Zeichen " + klammer
                + " hat kein Ende - dann ist der Quelltext nicht lesbar");
    }

    /**
     * Welche Taten in diesem Stueck Quelltext benannt werden.
     *
     * <p>Ueber {@code Tat.values()} und nicht mit drei Namen: Eine vierte Tat waere sonst
     * baubar, ohne dass diese Tafel sie je bemerkt.
     */
    private static Set<String> tatenIn(String stueck) {
        var benannt = new java.util.TreeSet<String>();
        for (Tat tat : Tat.values()) {
            if (stueck.contains("Tat." + tat)) {
                benannt.add(tat.name());
            }
        }
        if (stueck.contains("tatAn(")) {
            benannt.add("tatAn");
        }
        if (stueck.contains("tatAmWesen(")) {
            benannt.add("tatAmWesen");
        }
        return benannt;
    }

    @Test
    @DisplayName("31ah - jede Schluessel-Naht bekommt weltschluessel und nichts anderes")
    void jedeSchluesselnahtBekommtWeltschluessel() {
        // DIE DRITTE HAELFTE DES WELTSCHLUESSELS, und sie fehlte. 31ad verbietet das
        // Handkleben, 31ae das Weglassen bei einem FELD - aber das Blockbuch bekommt den
        // Schluessel ueber eine NAHT (Blockbuchliste.Schluesselmacher), und die hat kein
        // Feld. Gemessen mit einer Einspritzung (B11 am 09.11.2026): "this::weltschluessel"
        // gegen "name -> name" getauscht, und alle 2.516 Proben blieben gruen.
        //
        // Der Schaden ist derselbe wie damals im Ladenbereich: Die config.yml schreibt
        // "altfurt", die Welt traegt "freimarken:altfurt", der Vergleich ist immer
        // ungleich - und das Blockbuch bliebe fuer jede eingetragene Welt stumm leer.
        // Nichts bricht, nichts wird rot, und niemand koennte sagen, warum.
        //
        // DIE LISTE DER NAEHTE KOMMT AUS DEM QUELLTEXT: Wer eine Schnittstelle
        // "Schluesselmacher" erklaert, erwartet eine Umwandlung. Eine handgeschriebene
        // Liste bliebe bei der naechsten Naht stehen, und dann ist genau die
        // ungeschuetzt - derselbe Grund wie bei 31ab, 31ae und 31af.
        var naehte = new java.util.TreeSet<String>();
        for (java.nio.file.Path datei : Quellen.alle()) {
            if (Quellen.text(datei).contains("interface Schluesselmacher")) {
                String name = datei.getFileName().toString();
                naehte.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        assertFalse(naehte.isEmpty(),
                "keine einzige Schluesselmacher-Naht gefunden - dann prueft diese Probe"
                        + " nichts");

        String plugin = plugintext();
        var falschVersorgt = new java.util.TreeSet<String>();
        for (String naht : naehte) {
            // Die Aufrufstelle: der Klassenname, gefolgt von einem Punkt. Gesucht wird
            // die ganze Argumentliste, denn der Macher kann an jeder Stelle stehen.
            int stelle = plugin.indexOf(naht + ".");
            if (stelle < 0) {
                falschVersorgt.add(naht + " (wird nirgends gerufen)");
                continue;
            }
            int klammer = plugin.indexOf('(', stelle);
            if (klammer < 0) {
                falschVersorgt.add(naht + " (kein Aufruf gefunden)");
                continue;
            }
            int tiefe = 0;
            int ende = klammer;
            for (int i = klammer; i < plugin.length(); i++) {
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
            if (!plugin.substring(stelle, ende).contains("weltschluessel")) {
                falschVersorgt.add(naht);
            }
        }
        assertEquals(java.util.Set.of(), falschVersorgt,
                "diese Naehte wandeln einen Weltnamen in einen Schluessel um und"
                        + " bekommen die Umwandlung nicht aus weltschluessel(...). Die"
                        + " config.yml schreibt \"altfurt\", die Welt traegt"
                        + " \"freimarken:altfurt\", und der Vergleich der beiden ist"
                        + " immer ungleich - der ganze Bereich dahinter bleibt dann stumm"
                        + " leer: " + falschVersorgt);
    }
    @Test
    @DisplayName("31ai - wer ein Feld fuellt, wird vor dem gerufen, der es braucht")
    void einrichtungInDerRichtigenReihenfolge() {
        // DER FEHLER, GEGEN DEN DIESE PROBE STEHT: richteBlockbuchEin() baut die
        // Blockraeumung, richteSpielzeitEin() gibt sie dem Takt mit. Standen die beiden
        // in der anderen Reihenfolge, bekaeme der Takt ein null - und weil das ein
        // erlaubter Wert ist (ohne Datenbank gibt es nichts zu raeumen), faellt NICHTS
        // auf. Das Blockbuch wuechse fuer immer, die Frist waere stumm wirkungslos, und
        // keine Probe und keine Meldung sagte es.
        //
        // Geprueft wird die STELLE der Aufrufe in onEnable, nicht die Reihenfolge der
        // Methoden im Quelltext: Was zaehlt, ist, wann sie laufen.
        // DIE LISTE DER MITFAHRER WIRD NICHT VON HAND GEFUEHRT, sondern im Aufruf des
        // Taktes GELESEN. Vorher stand hier genau EIN Paar (Blockbuch und Spielzeit), und
        // am 10.11.2026 ist gemessen worden, was das wert ist: Der vierte Mitfahrer
        // truhenlauf kam dazu, und richteTruheEin() hinter richteSpielzeitEin() zu
        // schieben liess alle Proben gruen - dieselbe Fehlerklasse, gegen die diese Probe
        // gebaut wurde, eine Methode weiter. Ein fuenfter Mitfahrer ist jetzt am Tag
        // seiner Einfuehrung geprueft.
        String plugin = plugintext();
        int anfang = plugin.indexOf("public void onEnable()");
        assertTrue(anfang >= 0, "onEnable fehlt in FreimarkenPlugin");
        String start = plugin.substring(anfang);
        int braucht = start.indexOf("richteSpielzeitEin();");
        assertTrue(braucht >= 0,
                "richteSpielzeitEin() wird in onEnable nicht gerufen - dann prueft diese"
                        + " Probe nichts");
        int takt = start.indexOf(TAKTBAU);
        assertTrue(takt >= 0, "new Spielzeitwache( steht nicht in onEnable");

        List<String> geprueft = new ArrayList<>();
        String taktmethode = methodentext("richteSpielzeitEin");
        for (String roh : start.substring(takt + TAKTBAU.length(),
                start.indexOf(");", takt)).split(",")) {
            String feld = roh.trim();
            if (oertlichIn(taktmethode, feld)) {
                // EIN NAME KANN BEIDES SEIN. buch ist ein Feld der Klasse UND eine
                // oertliche Groesse, die drei richte-Methoden je fuer sich fuehren -
                // auch richteSpielzeitEin selbst. Was dort entsteht, verdeckt das Feld
                // und wird in derselben Methode weitergegeben: Es hat keine
                // Reihenfolge. Ohne diese Frage schrieb die Probe den Namen der
                // erstbesten Methode zu, die ihn irgendwo verwendet.
                continue;
            }
            if (!istFeld(plugin, feld)) {
                // NUR FELDER HABEN EINE REIHENFOLGE. buch zum Beispiel ist ein
                // oertlicher Name, den mehrere richte-Methoden fuehren - jede baut ihr
                // eigenes und gibt es in derselben Methode weiter. Ein solcher Name sagt
                // ueber den Startablauf nichts, und ohne diese Frage haette die Probe
                // ihn der erstbesten Methode zugeschrieben, die ihn verwendet.
                continue;
            }
            String fueller = fuellendeMethode(plugin, feld);
            if (fueller == null || fueller.equals("richteSpielzeitEin")) {
                // Ein Feld, das richteSpielzeitEin SELBST fuellt, steht in der richtigen
                // Reihenfolge, weil es dieselbe Methode ist - und ein Wert, den keine
                // richte-Methode setzt, hat keine Reihenfolge.
                continue;
            }
            // ODER DER FUELLER LAEUFT IN richteSpielzeitEin SELBST [Teil E6]:
            // richteShopsEin wird von dort gerufen und nicht aus onEnable, denn das
            // Shopbuch gehoert zum Takt. Dann ist die Frage nicht "vor
            // richteSpielzeitEin", sondern "vor new Spielzeitwache" - und sie bleibt
            // eine Frage: Wer den Aufruf dahinter schiebt, gibt ein null hinein, und
            // weil null erlaubt ist, faellt es nirgends auf.
            int ruftFueller = taktmethode.indexOf(fueller + "();");
            if (ruftFueller >= 0) {
                assertTrue(ruftFueller < taktmethode.indexOf(TAKTBAU),
                        fueller + "() fuellt das Feld " + feld + ", und es wird in"
                                + " richteSpielzeitEin selbst gerufen - aber ERST NACH"
                                + " new Spielzeitwache(). Dann bekommt der Takt ein"
                                + " null, und weil null dort ein ERLAUBTER Wert ist,"
                                + " faellt nichts auf: Der Mitfahrer laeuft nie.");
                geprueft.add(feld);
                continue;
            }
            int baut = start.indexOf(fueller + "(");
            assertTrue(baut >= 0 && baut < braucht,
                    fueller + "() fuellt das Feld " + feld + ", das new Spielzeitwache()"
                            + " mitbekommt - es MUSS vor richteSpielzeitEin() laufen."
                            + " Andersherum bekommt der Takt ein null, und weil null dort"
                            + " ein ERLAUBTER Wert ist, faellt nichts auf: Der Mitfahrer"
                            + " laeuft nie, und keine Meldung sagt es.");
            geprueft.add(feld);
        }
        assertEquals(List.of("blockraeumung", "truhenlauf", "besuchslauf",
                        "verfallslauf", "ladengebuehrlauf"), geprueft,
                "GEPRUEFT werden muessen alle Mitfahrer, die aus einer eigenen"
                        + " richte-Methode kommen. Diese Liste steht hier, damit ein Lauf"
                        + " ohne Messung nicht gruen meldet: Fiele die Erkennung aus,"
                        + " waere die Schleife leer und die Probe stumm zufrieden."
                        + " Kommt ein Mitfahrer dazu, gehoert er HIER hinein.");
    }

    /** Der Aufruf, aus dem die Mitfahrer des Taktes gelesen werden. */
    private static final String TAKTBAU = "new Spielzeitwache(";

    /**
     * Ob dieser Methodentext den Namen als eigene, oertliche Groesse fuehrt.
     *
     * <p>Erkannt am Typ davor: Eine oertliche Deklaration faengt mit ihrem Typ an und
     * der ist grossgeschrieben, eine Zuweisung an ein Feld mit dem Namen selbst.
     */
    private static boolean oertlichIn(String text, String feld) {
        for (String zeile : text.lines().toList()) {
            String nackt = zeile.strip();
            if (!nackt.isEmpty() && Character.isUpperCase(nackt.charAt(0))
                    && nackt.contains(" " + feld + " = ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ob {@code FreimarkenPlugin} ein FELD dieses Namens hat.
     *
     * <p>Erkannt an der Deklaration ohne Zuweisung auf Klassenebene: Ein oertlicher Name
     * steht immer mit seinem Typ in derselben Zeile wie seine Zuweisung.
     */
    private static boolean istFeld(String plugin, String feld) {
        for (String zeile : plugin.lines().toList()) {
            String nackt = zeile.strip();
            if (nackt.startsWith("private ") && nackt.endsWith(" " + feld + ";")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Die {@code richteXEin}-Methode, die dieses Feld fuellt - oder {@code null}.
     *
     * <p>Gelesen und nicht gepflegt: Eine Liste von Paaren im Quelltext dieser Probe
     * waere eine zweite Abschrift des Startablaufs, und die zweite Abschrift ist immer
     * die, die niemand nachzieht.
     */
    private static String fuellendeMethode(String plugin, String feld) {
        // Mit einem Leerzeichen davor, und das ist kein Schoenheitsfehler: Ohne es
        // endet "mietbuch = " auf "buch = ", und das Feld buch - eine oertliche Groesse
        // von richteSpielzeitEin - waere der Blockbuchmethode zugeschrieben worden.
        String zuweisung = " " + feld + " = ";
        for (int stelle = plugin.indexOf(zuweisung); stelle >= 0;
                stelle = plugin.indexOf(zuweisung, stelle + 1)) {
            int kopf = plugin.lastIndexOf("private void richte", stelle);
            if (kopf < 0) {
                continue;
            }
            // Nur wenn die Zuweisung WIRKLICH in jener Methode steht: Faengt dazwischen
            // schon die naechste an, gehoert sie einer anderen - oder gar keiner.
            if (plugin.substring(kopf + 1, stelle).contains("    private ")) {
                continue;
            }
            return plugin.substring(kopf + "private void ".length(),
                    plugin.indexOf("(", kopf));
        }
        return null;
    }

    @Test
    @DisplayName("31ap - die Stellen der Freien Truhen werden auch GEFUELLT")
    void truhenstellenWerdenGefuellt() {
        // Das Feld traegt die Pruefung, und ein Feld, das niemand fuellt, ist null -
        // dann kehrt pruefeFreieTruhen sofort zurueck und schweigt fuer immer. Genau
        // diese Fehlerklasse hat 31ac und 31ai gefangen; sie hat hier eine dritte
        // Gestalt, weil zwischen Fuellen und Lesen zwei Startschritte liegen.
        //
        // Dass richteTruheEin VOR richteSpielzeitEin laeuft (und damit vor
        // richteShopsEin, das darin steckt), haelt schon 31ai ueber den Mitfahrer
        // truhenlauf.
        assertEquals("richteTruheEin", fuellendeMethode(plugintext(), "truhenstellen"),
                "truhenstellen wird in keiner richte-Methode gesetzt - dann prueft"
                        + " pruefeFreieTruhen nie etwas");
    }

    @Test
    @DisplayName("31ao - die Shopschild-Pruefung fragt anDerKiste und NICHT istShopkiste")
    void truhenpruefungFragtAnDerKiste() {
        // DIE ZEILEN, DIE KEIN LAUF ERREICHT, und eine still umkehrbare Entscheidung:
        // Shopbuch.anDerKiste gibt bei stummer Datenbank eine LEERE Liste ("erfinde
        // keinen Besitzer"), Shopbuch.istShopkiste ein true ("schuetze die Kiste").
        // Beide Antworten sind an ihrer Methode richtig begruendet - hier taugt nur die
        // erste: Mit der zweiten klagte ein Datenbankausfall beim Start ueber JEDE
        // eingetragene Truhenstelle, und eine Klage ueber etwas, das nicht da ist, nimmt
        // der Leser beim naechsten Mal nicht mehr ernst.
        String pruefung = methodentext("pruefeFreieTruhen");

        assertTrue(pruefung.contains("anDerKiste("),
                "ohne diese Frage prueft die Methode gar nichts: " + pruefung);
        assertFalse(pruefung.contains("istShopkiste("),
                "istShopkiste klagt bei stummer Datenbank ueber jede Stelle: "
                        + pruefung);
    }

    @Test
    @DisplayName("31an - die Truhenwache wird angemeldet, sobald eine Stelle eingetragen"
            + " ist")
    void truhenwacheWirdAngemeldet() {
        // DIE ZEILEN, DIE KEIN LAUF ERREICHT: Der Start des Plugins braucht einen
        // laufenden Server. Kehrt sich die Frage nach den Stellen um, wird die Wache NIE
        // angemeldet - und dann ist die Freie Truhe eine gewoehnliche Kiste: kein
        // Tagesdeckel, kein Verbot, kein "einer zur Zeit". Der naechtliche Lauf leert sie
        // weiter, als waere alles in Ordnung, und die Startzeile nennt einen Deckel, den
        // es nicht mehr gibt.
        String start = methodentext("richteTruheEin");

        int lauf = start.indexOf("truhenlauf = new Truhenlauf(");
        int abbruch = start.indexOf("if (!buch.gibtTruhen())");
        int anmelden = start.indexOf("registerEvents(new Truhenwache(");
        assertTrue(lauf >= 0, "der taegliche Lauf wird nicht gebaut: " + start);
        assertTrue(abbruch > lauf,
                "der Lauf entsteht VOR der Abkuerzung: Ohne Stelle gibt es nichts zu"
                        + " leeren, aber die Zeilen von gestern muessen trotzdem geraeumt"
                        + " werden - sonst waeren sie unloeschbar. " + start);
        assertTrue(anmelden > abbruch,
                "die Wache wird angemeldet, und zwar nach der Frage nach den Stellen: "
                        + start);
    }

    /**
     * Der Koerper einer Methode von {@code FreimarkenPlugin} - fuer die Zeilen, die kein
     * Lauf erreicht.
     *
     * <p>Abgeschnitten wird am naechsten Mitglied der Klasse und nicht an einer
     * geschweiften Klammer: Eine Klammer auf vier Leerzeichen steckt auch in jeder auf
     * acht.
     */
    private static String methodentext(String name) {
        String plugin = plugintext();
        int anfang = plugin.indexOf("private void " + name + "(");
        assertTrue(anfang >= 0, name + "() fehlt in FreimarkenPlugin");
        int ende = plugin.indexOf("    private ", anfang + 1);
        return plugin.substring(anfang, ende < 0 ? plugin.length() : ende);
    }
    @Test
    @DisplayName("31aj - jeder Mitfahrer des Spielzeittakts wird auch GERUFEN")
    void jederMitfahrerWirdGerufen() {
        // DIE DRITTE GESTALT DESSELBEN FEHLERS. 31ab sichert die pruefe-Methoden, 31af
        // die richte-Methoden - und hier sitzt sie eine Ebene tiefer: Ein Mitfahrer des
        // Spielzeittakts ist ein FELD, das gehalten, aber nicht gerufen wird.
        //
        // Gemessen mit einer Einspritzung (C5 am 09.11.2026): den Aufruf
        // blockraeumung.imTakt() aus schlag() entfernt, und alle 2.555 Proben blieben
        // gruen. Das Blockbuch wuechse dann fuer immer, die Frist waere stumm
        // wirkungslos, und keine Meldung sagte es - der Zeitgeber laeuft ja, er ruft nur
        // einen weniger.
        //
        // DIE LISTE DER MITFAHRER KOMMT AUS DEM QUELLTEXT: Wer ein Feld der
        // Spielzeitwache ist und eine Methode imTakt() hat, faehrt mit. Eine
        // handgeschriebene Liste bliebe beim naechsten Mitfahrer stehen, und dann ist
        // genau der ungerufen - derselbe Grund wie bei 31ab, 31ae, 31af und 31ah.
        String wache = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Spielzeitwache.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Spielzeitwache.java fehlt")));

        // Die Felder, deren Typ ein imTakt() hat - das macht sie zu Mitfahrern.
        var mitfahrer = new java.util.TreeMap<String, String>();
        var sucher = java.util.regex.Pattern
                .compile("private final ([A-Z][A-Za-z]*) ([a-z][A-Za-z]*);")
                .matcher(wache);
        while (sucher.find()) {
            String typ = sucher.group(1);
            String feld = sucher.group(2);
            for (java.nio.file.Path datei : Quellen.alle()) {
                if (!datei.getFileName().toString().equals(typ + ".java")) {
                    continue;
                }
                // JEDER Rueckgabetyp und nicht nur void - und das ist gemessen: Hier
                // stand zuerst "void imTakt(", und damit fand die Probe die
                // Blockraeumung NICHT, denn jene gibt ein Ergebnis zurueck. Sie blieb
                // gruen, weil zwei andere Mitfahrer void sind: Sie prueefte also zwei
                // und schwieg ueber den dritten, und die Einspritzung C5 kam durch.
                // Ein Waechter, der nur einen Teil sieht, ist schlimmer als keiner -
                // er bescheinigt, was er nie angesehen hat.
                if (java.util.regex.Pattern
                        .compile("[A-Za-z][A-Za-z.<>]* imTakt\\(")
                        .matcher(Quellen.text(datei)).find()) {
                    mitfahrer.put(feld, typ);
                }
            }
        }
        assertFalse(mitfahrer.isEmpty(),
                "kein einziger Mitfahrer mit imTakt() gefunden - dann prueft diese Probe"
                        + " nichts");

        // Der Koerper von schlag(), und nur er: Ein Aufruf in starte() oder beende()
        // waere kein Mitfahren.
        int anfang = wache.indexOf("private void schlag()");
        assertTrue(anfang >= 0, "schlag() fehlt in Spielzeitwache");
        String takt = wache.substring(anfang);
        int schluss = takt.indexOf("catch (Throwable");
        assertTrue(schluss >= 0,
                "der Takt faengt kein Throwable mehr - dann ist nicht nur diese Probe"
                        + " kaputt, sondern eine Ausnahme beendet den Zeitgeber");
        String koerper = takt.substring(0, schluss);

        var stumm = new java.util.TreeSet<String>();
        for (var eintrag : mitfahrer.entrySet()) {
            if (!koerper.contains(eintrag.getKey() + ".imTakt(")) {
                stumm.add(eintrag.getValue());
            }
        }
        assertEquals(java.util.Set.of(), stumm,
                "diese Mitfahrer haelt die Spielzeitwache, ruft sie aber nicht in"
                        + " schlag() - sie sind gebaut, verdrahtet und tot. Der"
                        + " Zeitgeber laeuft weiter, es faellt also nichts auf; was"
                        + " ausbleibt, ist nur ihre Arbeit: " + stumm);
    }

    /**
     * Die Vorsilben dynamischer Rechte - sie lassen sich nicht anmelden.
     *
     * <p>Ein Rang vergibt {@code freimarken.parzellen.5}, und die Zahl dahinter kennt nur
     * der Betreiber. Solche Knoten sind keine angemeldeten Rechte, sondern WERTE: Das
     * Programm liest sie aus den Rechten des Spielers heraus
     * ({@code Rangzahl.hoechsteAus}), statt zu fragen, ob er eines hat. Sie
     * gehoeren darum nicht in {@code plugin.yml} - und was man nicht anmelden kann, darf
     * eine Probe nicht verlangen.
     *
     * <p><b>Seit dem 25.11.2026 sind nicht mehr alle davon WERTE</b>:
     * {@code freimarken.welt.} und {@code freimarken.stufe.} werden GEFRAGT
     * ({@code Zutrittsregel}). Anmelden lassen sie sich trotzdem nicht - der Teil
     * dahinter ist ein frei gewaehlter Schluessel der {@code config.yml}
     * ({@code welten.<name>}, {@code raenge.leiter.<name>}), und eine
     * {@code plugin.yml} kann nicht aufzaehlen, was der Betreiber morgen eintraegt.
     * Die Folge ist gemessen und milder, als sie klingt: {@code DEFAULT_PERMISSION}
     * ist {@code OP}, ein unangemeldetes Recht gilt also fuer Operatoren als
     * gesetzt. Wer es zugeteilt bekommt, kommt durch; jeder OPERATOR kommt durch,
     * auch ohne Zuteilung - und darum sieht ein Spieltest mit Operatorrechten von
     * diesen zwei Riegeln nichts.
     */
    private static final List<String> RECHTE_VORSILBEN = List.of(
            "freimarken.gruppe.", "freimarken.parzellen.",
            "freimarken.extra.gruppe.", "freimarken.extra.parzellen.",
            "freimarken.inseln.", "freimarken.extra.inseln.",
            // Schritt 11: die Obergrenze der Wolkeninseln. Die Vorsilbe heisst nach
            // der SACHE (wolken) und nicht nach dem Befehl (wolke) - wie inseln
            // neben /werder. Sie darf NICHT in die plugin.yml: Die Zahl dahinter ist
            // frei waehlbar, und ein Eintrag machte die Gegenrichtung dieser Tafel
            // rot (meldet an, keiner fragt).
            "freimarken.wolken.", "freimarken.extra.wolken.",
            // Teil H: die Freistaedte. Dieselbe Bauart, derselbe Grund - und hier war
            // die Vorsilbe bis zum 20.11.2026 die EINZIGE, die niemand las: Der Riegel
            // nahm allein die Zahl aus der config.yml.
            "freimarken.freistaedte.", "freimarken.extra.freistaedte.",
            // 25.11.2026: Die zwei GEFRAGTEN Vorsilben - siehe den Klassenkopf
            // oben. freimarken.stufe. und NICHT freimarken.rang.: Jenes ist schon
            // das angemeldete Recht von /rang, und 31cy liesse mit dieser Vorsilbe
            // jedes Stufenrecht ungeprueft durch (sie beginnt dann mit einem
            // angemeldeten Recht plus Punkt).
            "freimarken.welt.", "freimarken.stufe.");

    @Test
    @DisplayName("31am - jeder Sprungschluessel steht in der ausgelieferten config.yml")
    void jederSprungschluesselStehtInDerDatei() {
        // FUND VOM 10.11.2026, beim Vorbereiten einer Spielprobe: Der Markt-Tagesdeckel
        // sollte gesenkt werden, und der Kommentar an jener Zeile nennt dafuer
        // wirtschaft.sprung-bestaetigt.markt-tagesdeckel - den es im Abschnitt
        // sprung-bestaetigt nicht gab. Dasselbe fuer laden-lizenz, bonus-grundbetrag und
        // bonus-je-serientag: vier von neun.
        //
        // Kaputt war dadurch nichts, und das ist der Grund, warum es niemandem auffiel:
        // Die Klage nennt den Schluessel samt fertiger Zeile, und wer sie liest, traegt
        // ihn von Hand ein. Im Probeserver steht laden-lizenz genau so da. Aber die
        // ausgelieferte Datei IST die Dokumentation dieses Projekts - ein Abschnitt, der
        // die Haelfte verschweigt, laesst einen Betreiber glauben, es gebe fuer seinen
        // Wert keine Bestaetigung.
        //
        // Gelesen wird die GESTALT des Aufrufs und nicht eine Liste von Hand: Jeder
        // Geldwert mit Sprungschutz geht durch betrag() oder pflichtbetrag(), und der
        // erste Text darin ist sein Schluessel.
        var muster = Pattern.compile("betrag\\(wurzel,[^\"]*\"([a-z-]+)\"");
        var imProgramm = new java.util.TreeSet<String>();
        for (Path datei : Quellen.alle()) {
            var suche = muster.matcher(Quellen.text(datei));
            while (suche.find()) {
                imProgramm.add(suche.group(1));
            }
        }
        assertTrue(imProgramm.size() >= 5,
                "nur " + imProgramm.size() + " Sprungschluessel im Programm gefunden -"
                        + " dann prueft diese Probe fast nichts: " + imProgramm);

        var inDerDatei = new java.util.TreeSet<String>();
        boolean imAbschnitt = false;
        for (String zeile : ausgelieferte("config.yml").split("\n", -1)) {
            if (zeile.startsWith("  sprung-bestaetigt:")) {
                imAbschnitt = true;
                continue;
            }
            if (imAbschnitt) {
                if (zeile.startsWith("    #")) {
                    continue;
                }
                if (!zeile.startsWith("    ")) {
                    imAbschnitt = false;
                    continue;
                }
                inDerDatei.add(zeile.strip().split(":", 2)[0]);
            }
        }

        assertEquals(imProgramm, inDerDatei,
                "jeder Geldwert mit Sprungschutz braucht seine Zeile im Abschnitt"
                        + " wirtschaft.sprung-bestaetigt - und jede Zeile dort braucht"
                        + " ihren Geldwert. Zu wenig heisst: Ein Betreiber findet die"
                        + " Bestaetigung nicht, die seine Klage von ihm verlangt. Zu"
                        + " viel heisst: Er traegt eine ein, die nie etwas deckt.");
    }

    @Test
    @DisplayName("31al - jedes Recht aus dem Programm steht in der plugin.yml")
    void jedesRechtIstAngemeldet() {
        // FUND VOM 10.11.2026, und er lag ein halbes Dutzend Spieltests lang offen:
        // freimarken.beruf und freimarken.markt waren nicht angemeldet.
        //
        // Bukkits PermissibleBase antwortet auf ein Recht, das kein Plugin angemeldet
        // hat, mit Permission.DEFAULT_PERMISSION - und die ist OP. Ein gewoehnlicher
        // Spieler bekommt also false; der Zweig bleibt ihm verborgen (requires) UND
        // verwehrt. Im Programm stand dabei woertlich "jeder".
        //
        // Dass es niemand merkte, hat einen Grund, der sich wiederholen wird: Jeder
        // Spieltest lief mit einem Operator. Ein Recht mit default true verhaelt sich
        // fuer ihn genauso wie ein fehlendes - der Unterschied zeigt sich nur bei einem
        // Spieler ohne Rechte, und der war nie dabei.
        //
        // Gelesen werden die STRING-LITERALE des Programms und die angemeldeten Namen
        // der ausgelieferten plugin.yml. Beides ist die Wahrheit und keine Abschrift:
        // Eine Tafel von Hand gefuehrter Rechte waere genau die zweite Liste, die
        // irgendwann abweicht.
        var imProgramm = new java.util.TreeSet<String>();
        var muster = Pattern.compile("\"(freimarken\\.[a-z.]+)\"");
        for (Path datei : Quellen.alle()) {
            var suche = muster.matcher(Quellen.text(datei));
            while (suche.find()) {
                String recht = suche.group(1);
                if (RECHTE_VORSILBEN.stream().noneMatch(recht::equals)) {
                    imProgramm.add(recht);
                }
            }
        }
        assertTrue(imProgramm.size() > 10,
                "nur " + imProgramm.size() + " Rechte im Programm gefunden - dann"
                        + " prueft diese Probe fast nichts: " + imProgramm);

        var angemeldet = new java.util.TreeSet<String>();
        for (String zeile : ausgelieferte("plugin.yml").split("\n", -1)) {
            if (zeile.startsWith("  freimarken.") && zeile.endsWith(":")) {
                angemeldet.add(zeile.strip().substring(0, zeile.strip().length() - 1));
            }
        }
        assertTrue(angemeldet.size() > 10,
                "nur " + angemeldet.size() + " Rechte in der plugin.yml gelesen: "
                        + angemeldet);

        var fehlen = new java.util.TreeSet<>(imProgramm);
        fehlen.removeAll(angemeldet);
        // Die VORSILBEN fallen hier heraus und nicht oben: Oben wuerde ein Tippfehler
        // in einer Vorsilbe unbemerkt zu einem fehlenden Recht, hier bleibt er sichtbar.
        fehlen.removeIf(recht -> RECHTE_VORSILBEN.stream().anyMatch(recht::startsWith));
        assertEquals(java.util.Set.of(), fehlen,
                "diese Rechte fragt das Programm, und die plugin.yml meldet sie nicht an"
                        + " - Bukkit gibt sie damit NUR Operatoren, und der Zweig"
                        + " dahinter ist fuer jeden anderen verborgen und verwehrt: "
                        + fehlen);

        var ohneGrund = new java.util.TreeSet<>(angemeldet);
        ohneGrund.removeAll(imProgramm);
        assertEquals(java.util.Set.of(), ohneGrund,
                "diese Rechte meldet die plugin.yml an, und kein Zweig fragt sie - ein"
                        + " Betreiber vergibt sie und nichts geschieht: " + ohneGrund);
    }

    /** Eine ausgelieferte Datei aus dem Jar - fuer die Proben, die sie selbst lesen. */
    private static String ausgelieferte(String name) {
        try (var strom = GrenzeTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(strom, name + " muss im Jar liegen");
            return new String(strom.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException nichtLesbar) {
            throw new IllegalStateException(name + " nicht lesbar", nichtLesbar);
        }
    }

    @Test
    @DisplayName("31ak - kein Dokumentationsblock haengt an einem anderen")
    void keinDokublockOhneDeklaration() {
        // FUND VOM 10.11.2026, und die Lehre steckt nicht im Fehler, sondern darin, wie
        // lange er unsichtbar blieb.
        //
        // In Shopliste.java stand eine neue Methode samt eigenem Javadoc ZWISCHEN dem
        // Doku-Block von bundwort und bundwort selbst. Damit dokumentierte jener Block
        // nichts mehr, und bundwort hatte keine Dokumentation - zwei Verluste aus einer
        // Einfuegung, und der Quelltext sieht an der Stelle vollkommen gewoehnlich aus.
        //
        // Javac MELDET das: -Xlint:all kennt dangling-doc-comments, und die Warnung stand
        // im Bauprotokoll. Gesehen wurde sie trotzdem erst einen Tag spaeter, denn eine
        // Warnung erscheint nur, wenn die Aufgabe wirklich laeuft - und :paper:compileJava
        // war beim naechsten Lauf UP-TO-DATE. Eine Warnung, die nur im ersten Lauf
        // sichtbar ist, ist keine Zusicherung; dieses Projekt uebersetzt ausserdem ohne
        // -Werror, also haette sie den Bau nie angehalten.
        //
        // Diese Probe liest die GESTALT: Ein Block, der mit einer Zeile endet, auf die
        // ein neuer Block folgt, kann unmoeglich an einer Deklaration haengen. Sie liest
        // ALLE drei Module - der naechste Fall entsteht dort, wo gerade gebaut wird.
        List<Path> dateien = Quellen.alleDateien();
        assertTrue(dateien.size() > 100,
                "nur " + dateien.size() + " Quelldateien gefunden - dann prueft diese"
                        + " Probe fast nichts");

        List<String> abgerissen = new ArrayList<>();
        for (Path datei : dateien) {
            String[] zeilen = Quellen.mitKommentaren(datei).split("\n", -1);
            for (int i = 0; i < zeilen.length; i++) {
                // ZWEI Gestalten eines Blockendes, und die zweite war eine Luecke
                // [12.11.2026]: Ein EINZEILIGES Javadoc (/** ... */ in einer Zeile)
                // endet nicht mit einem "*/" auf eigener Zeile. Genau so eines ist in
                // Freistadttext abgerissen, und diese Tafel sah es nicht - gefunden hat
                // es der Uebersetzer im erzwungenen Lauf.
                String hier = zeilen[i].strip();
                boolean blockEndet = hier.equals("*/")
                        || (hier.startsWith("/**") && hier.endsWith("*/"));
                if (!blockEndet) {
                    continue;
                }
                // Die naechste Zeile, die eine DEKLARATION sein koennte. Leerzeilen
                // zaehlen nicht, und ZEILENKOMMENTARE auch nicht - das war die zweite
                // Luecke: Zwischen den zwei Bloecken in Freistadttext stand eine
                // Abschnittsueberschrift mit zwei Schraegstrichen, und die Suche hielt
                // sie fuer den Anfang von Code.
                for (int j = i + 1; j < zeilen.length; j++) {
                    String naechste = zeilen[j].strip();
                    if (naechste.isEmpty() || naechste.startsWith("//")) {
                        continue;
                    }
                    if (naechste.startsWith("/**")) {
                        abgerissen.add(datei.getFileName() + ":" + (i + 1));
                    }
                    break;
                }
            }
        }
        assertEquals(List.of(), abgerissen,
                "an diesen Stellen endet ein Dokumentationsblock, und es folgt ein"
                        + " ZWEITER - der erste haengt damit an keiner Deklaration und"
                        + " dokumentiert nichts, und was er beschrieb, steht ohne"
                        + " Dokumentation da. Javac meldet es als"
                        + " dangling-doc-comments, aber nur im Lauf, in dem wirklich"
                        + " uebersetzt wird: " + abgerissen);
    }

    @Test
    @DisplayName("31at - der Auflagenriegel sitzt VOR der Reise, in reiseInStadt")
    void auflagenriegelVorDerReise() {
        // EINE QUELLTEXTTAFEL UND KEINE VERHALTENSPROBE, und der Grund ist gemessen:
        // Der Erfolgsfall eines Stadtwarps laeuft ohne Server nicht durch - Reise.pruefe
        // fragt ueber Ankunftspunkt.taugt den Boden, und Material.isSolid geht in die
        // Bukkit-Registry, die ohne Server wirft.
        //
        // Was diese Tafel festhaelt, ist die VERDRAHTUNG: Die Auflage wird gefragt, und
        // zwar VOR reise.fuehre. Beides einzeln ist wertlos - eine Regel, die gebaut und
        // gruen geprueft ist und nicht gerufen wird, ist im Spiel unsichtbar
        // wirkungslos; und eine, die NACH der Reise fragt, kommt zu spaet.
        String quelle = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("WarpBefehle.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("WarpBefehle.java fehlt")));
        int riegel = quelle.indexOf("freistadtbuch.darfReisen(");
        int reise = quelle.indexOf("reise.fuehre(spieler, new org.bukkit.Location(");

        assertTrue(riegel > 0, "reiseInStadt fragt die Auflage GAR NICHT - die Regel"
                + " ist dann gebaut, gruen geprueft und im Spiel wirkungslos");
        assertTrue(reise > 0, "die Reise selbst steht nicht mehr da - dann prueft diese"
                + " Tafel die falsche Datei");
        assertTrue(riegel < reise,
                "und der Riegel steht VOR der Reise. Danach waere der Spieler schon in"
                        + " der Stadt, und die Absage kaeme ihm hinterher.");

        // Und die andere Richtung: Zwischen dem Riegel und der Reise steht ein
        // Ausgang. Ohne ihn liest der Abgewiesene seine Absage UND reist trotzdem.
        String dazwischen = quelle.substring(riegel, reise);
        assertTrue(dazwischen.contains("return;"),
                "zwischen Riegel und Reise fehlt das return - dann sendet der Zweig die"
                        + " Absage und reist danach doch: " + dazwischen);
    }


    @Test
    @DisplayName("31au - das Shopbuch bekommt das ECHTE Freistadtbuch, nicht null")
    void shopbuchBekommtDasFreistadtbuch() {
        // DIE EINE ZEILE, DIE KEINE PROBE SIEHT. Jede Shopprobe baut ihr Shopbuch
        // selbst und gibt hinein, was sie messen will; dass der laufende Server dort
        // das echte Buch uebergibt, steht nur in FreimarkenPlugin. Ein null an dieser
        // Stelle liesse den ganzen Riegel lautlos durch - gebaut, gruen geprueft und
        // im Spiel ohne Wirkung. Dasselbe Anliegen wie bei 31at, nur fuer den zweiten
        // Riegel derselben Auflage.
        String quelle = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));
        int bau = quelle.indexOf("new Shopbuch(");
        assertTrue(bau > 0, "der Server baut gar kein Shopbuch mehr - dann prueft diese"
                + " Tafel die falsche Datei");
        int ende = quelle.indexOf("getLogger());", bau);
        assertTrue(ende > bau, "der Bau des Shopbuchs endet nicht mehr am Protokoll -"
                + " diese Tafel liest dann einen Abschnitt, der ihr nicht gehoert");

        String werte = quelle.substring(bau, ende);
        assertTrue(werte.contains("freistadtbuch,"),
                "das Shopbuch bekommt das Freistadtbuch NICHT - der Riegel aus E3b ist"
                        + " dann gebaut, gruen geprueft und im Spiel wirkungslos: "
                        + werte);

        // Und die andere Richtung, denn ein null uebersetzt sich genauso gut: Es darf
        // nicht an die Stelle des Buches geraten.
        assertFalse(werte.contains("null"),
                "an dieser Stelle steht ein null - und null heisst hier 'es gibt keine"
                        + " Freistaedte', also laesst der Riegel jeden Shop durch: "
                        + werte);

        // Die REIHENFOLGE im Start: Das Feld muss gefuellt sein, wenn das Shopbuch
        // entsteht. Beide Zweige stehen in onEnable, und ihre Reihenfolge ist die
        // ganze Zusage - richteShopsEin zuerst gaebe ein stilles null hinein.
        int staedte = quelle.indexOf("richteFreistaedteEin();");
        int spielzeit = quelle.indexOf("richteSpielzeitEin();");
        assertTrue(staedte > 0 && spielzeit > 0,
                "einer der beiden Einrichtungszweige heisst nicht mehr so");
        assertTrue(staedte < spielzeit,
                "richteFreistaedteEin laeuft NACH dem Zweig, aus dem richteShopsEin"
                        + " gerufen wird - dann ist freistadtbuch beim Bau des"
                        + " Shopbuchs noch null, und der Riegel wirkt in diesem Lauf"
                        + " nie");
    }

    @Test
    @DisplayName("31av - die Spannen im Inselgenerator, Zeile fuer Zeile")
    void dieSpannenImInselgenerator() {
        // Dieselbe Tafel wie 31v fuer die Stadtfelder, und aus demselben Grund: Keine
        // Probe kann den Generator laufen lassen - dazu braucht er ein ChunkData vom
        // Server. Also wird der Quelltext gelesen, und zwar die UNTERE und die OBERE
        // Grenze jeder Spanne als TEXT.
        //
        // Hier haengt mehr daran als dort, denn alle vier Spannen rechnen mit demselben
        // GIPFEL: Verrutscht eine, hat die Spalte ein Loch oder einen Block zu viel - und
        // zwar nur in der Tiefe, in der dieser Gipfel liegt. Auf dem Inselinneren waere
        // das nie zu sehen, am Hang unter Wasser schon gar nicht.
        String quelle = Quellen.text(datei("InselnGenerator.java"));
        List<String> gefunden = new ArrayList<>();
        var spanne = Pattern.compile(
                "setRegion[(]lx, ([^,]+), lz, lx [+] 1, ([^,]+), lz [+] 1,").matcher(quelle);
        while (spanne.find()) {
            gefunden.add(spanne.group(1) + " .. " + spanne.group(2));
        }

        assertEquals(List.of(
                        "unten .. unten + 1",
                        "unten + 1 .. gipfel - Inselboden.ERDE_TIEF",
                        "gipfel - Inselboden.ERDE_TIEF .. gipfel",
                        "gipfel + 1 .. Inselboden.MEERESSPIEGEL + 1"),
                gefunden,
                "Grundgestein, Stein, Erde und das Wasser darueber - alle vier vom Gipfel"
                        + " dieser Spalte aus gerechnet. Steht in einer wieder eine feste"
                        + " Hoehe, liegt die Erde am Meeresgrund mitten im Stein und der"
                        + " Grund selbst ist nackter Fels");

        // Und der Riegel vor der Wasserspanne: Auf dem Inselinneren liegt der Gipfel
        // UEBER der Wasserlinie, und eine Spanne von 64 bis 63 waere eine, die oben unter
        // unten liegt. Was setRegion damit tut, ist nirgends zugesagt.
        assertTrue(quelle.contains("if (gipfel < Inselboden.MEERESSPIEGEL) {"),
                "der Riegel vor der Wasserspanne fehlt - dann bekommt setRegion auf jeder"
                        + " Insel eine Spanne, deren Oberkante unter ihrer Unterkante"
                        + " liegt");
    }

    @Test
    @DisplayName("31aw - jede Weltart hat ihr Wort in der Datei und in der Klage")
    void jedeWeltartHatIhrWort() {
        // ZWEI STELLEN JE ART, und keine davon traegt ein Typ: Der Verzweig in
        // Weltenliste.art uebersetzt das Wort aus der Datei, und die Klage zaehlt die
        // erlaubten Worte auf. Eine neue Art ohne Wort ist eine, die niemand eintragen
        // KANN - und das faellt nirgends auf, weil alles uebersetzt und jede Probe gruen
        // bleibt. Eine Art, die in der Klage fehlt, kann der Betreiber nicht erraten.
        //
        // Die Liste kommt aus der AUFZAEHLUNG und nicht aus einer Abschrift: Eine
        // handgeschriebene Liste hier waere die dritte Stelle, an der jemand eine Art
        // vergisst.
        String quelle = Quellen.text(datei("Weltenliste.java"));
        // Die Klage steht ueber mehrere Zeilen und mehrere Stuecke. Zusammengelegt und
        // von Anfuehrung und Plus befreit, steht sie als ein Satz da.
        String satz = quelle.lines()
                .map(String::strip)
                .reduce("", (eines, naechstes) -> eines + " " + naechstes)
                .replace("\"", "")
                .replace("+", "");
        int anfang = satz.indexOf("Erlaubt sind ");
        int ende = satz.indexOf(" - uebersprungen.", anfang);
        assertTrue(anfang > 0 && ende > anfang,
                "die Klage ueber eine unbekannte Art zaehlt die erlaubten Worte nicht"
                        + " mehr auf - dann weiss der Betreiber nicht, was er tippen"
                        + " darf");
        Set<String> inDerKlage = new TreeSet<>(List.of(satz
                .substring(anfang + "Erlaubt sind ".length(), ende)
                .replace(",", " ")
                .trim()
                .split("\\s+")));

        Set<String> erwartet = new TreeSet<>();
        for (Weltangabe.Art art : Weltangabe.Art.values()) {
            String wort = art.name().toLowerCase(java.util.Locale.ROOT);
            erwartet.add(wort);
            assertTrue(quelle.contains("case \"" + wort + "\""),
                    "die Art " + art + " hat kein Wort in Weltenliste.art - sie laesst"
                            + " sich in der config.yml gar nicht eintragen");
        }

        assertEquals(erwartet, inDerKlage,
                "die Klage nennt andere Worte als es Arten gibt - entweder fehlt eine"
                        + " neue Art oder es steht eine darin, die es nicht mehr gibt");
    }

    @Test
    @DisplayName("31ax - der Fangtausch sitzt VOR der Zahlung, und die Wache ist"
            + " angemeldet")
    void derFangtauschSitztVorDerZahlung() {
        // ZWEI ZUSAGEN, DIE KEINE PROBE SEHEN KANN, und beide haengen an derselben
        // Sache: Die Angelwache tauscht den Fang, die Verdienstwache bezahlt ihn - und
        // zwar das Material des Stapels, der dann im Netz liegt. Steht der Tausch
        // DANACH, ist die ganze Tabelle fuer den Verdienst unsichtbar: Die Lohnzeilen zu
        // den neuen Faengen waeren Zeilen, die nie greifen, und niemand saehe es. Keine
        // Probe kann das messen - dazu braeuchte sie den Ereignisbus des Servers.
        //
        // Gelesen wird die STUFE als Wort und nicht ihre Zahl: Die Zahl stuende als
        // Erwartung in dieser Tafel, und Bukkit darf sie aendern.
        Map<String, String> stufen = new TreeMap<>();
        for (String datei : List.of("Angelwache.java", "Verdienstwache.java",
                "Wettbewerbswache.java")) {
            String quelle = Quellen.text(datei(datei));
            int zweig = quelle.indexOf("public void beimFischen(PlayerFishEvent");
            assertTrue(zweig > 0, datei + " hat keinen Zweig beimFischen mehr - dann"
                    + " prueft diese Tafel die falsche Datei");
            int stufe = quelle.lastIndexOf("EventPriority.", zweig);
            assertTrue(stufe > 0, datei + ": vor beimFischen steht keine EventPriority");
            String wort = quelle.substring(stufe + "EventPriority.".length(), zweig)
                    .split("[^A-Z_]")[0];
            stufen.put(datei, wort);
        }

        assertEquals(Map.of("Angelwache.java", "HIGH",
                        "Verdienstwache.java", "MONITOR",
                        // Schritt 14: die Wertung des Angelwettbewerbs. Auch MONITOR,
                        // und aus demselben Grund wie die Zahlung: Sie liest das
                        // Material des Stapels, der NACH dem Tausch im Netz liegt.
                        // Zaehlte sie davor, waere ein Meeresherz ein Kabeljau - und
                        // die 100 Punkte der Punktetabelle waeren Punkte, die niemand
                        // je bekommt.
                        "Wettbewerbswache.java", "MONITOR"),
                stufen,
                "HIGH laeuft VOR MONITOR - der Tausch zuerst, die Zahlung und die"
                        + " Wertung danach. Steht hier etwas anderes, bezahlt die"
                        + " Lohntafel den Fang, den Vanilla gewuerfelt hat, und die"
                        + " Angeltabelle ist fuer Verdienst und Wettbewerb nicht"
                        + " vorhanden");

        // UND DIE ANMELDUNG. Eine Wache, die niemand am Server anmeldet, ist gebaut,
        // gruen geprueft und im Spiel ohne Wirkung - dieselbe Lage wie bei der
        // Truhenwache (31an) und beim Shopbuch (31au).
        String plugin = Quellen.text(datei("FreimarkenPlugin.java"));
        assertTrue(plugin.contains("registerEvents(angelwache, this)"),
                "die Angelwache wird nicht angemeldet - dann tauscht sie nie einen Fang,"
                        + " und kein Protokoll sagt es");
        assertTrue(plugin.contains("registerEvents(wettbewerbswache, this)"),
                "die Wettbewerbswache wird nicht angemeldet - dann zaehlt kein Fang,"
                        + " jeder Lauf endet mit 0 Punkten, und kein Protokoll sagt es");
        // UND SIE IST MITFAHRER DES TAKTS. Das Feld darf null sein, also faellt ein
        // vergessener Aufruf nicht auf: Sie liefe nie, und kein Wettbewerb wuerde je
        // abgerechnet - ohne Startmeldung und ohne Zeile im Protokoll.
        assertTrue(Quellen.text(datei("Spielzeitwache.java"))
                        .contains("wettbewerbswache.imTakt()"),
                "die Wettbewerbswache ist kein Mitfahrer des Spielzeittakts - dann wird"
                        + " kein Fenster angesagt und kein Lauf abgerechnet");
        int bau = plugin.indexOf("Angelwache.ueberBukkit(");
        assertTrue(bau > 0, "der Server baut die Wache nicht mehr ueber ueberBukkit -"
                + " und nur dort steht, dass ein Fang EIN Stueck ist");
    }

    @Test
    @DisplayName("31ay - jedes Biom der Tafel hat seinen Zweig, und jeder Zweig ein Biom")
    void jedesBiomHatSeinenZweig() {
        // Die Naht zwischen Kern und Minecraft, und keine Verhaltensprobe kann sie
        // sehen: Die NAMEN stehen im Kern (Wolkenbiome), weil sie ohne Server lesbar sein
        // muessen - der Leser der config.yml prueft damit jede Zeile. Was daraus ein Biom
        // macht, steht in Biomtafel und braucht einen laufenden Server; eine Probe, die
        // das aufruft, waere ohne Server nicht moeglich.
        //
        // Beide Richtungen zaehlen. Ein Name ohne Zweig waere ein Biom, das der Befehl
        // ANBIETET und das Plugin nicht setzen kann - eine Absage mitten im Spiel, ohne
        // Grund. Ein Zweig ohne Name waere eine Zeile, die nie greift, und ein Biom in
        // der Farbtafel der Welt, das niemand waehlen darf.
        String quelle = Quellen.text(datei("Biomtafel.java"));
        var zweige = new java.util.TreeSet<String>();
        var muster = Pattern.compile("case \"([a-z_]+)\" ->");
        var treffer = muster.matcher(quelle);
        while (treffer.find()) {
            zweige.add(treffer.group(1));
        }

        assertEquals(new java.util.TreeSet<>(de.freimarken.kern.Wolkenbiome.NAMEN),
                zweige,
                "die Namen im Kern und die Zweige im Plugin muessen dieselbe Menge"
                        + " sein - sonst gibt es ein Biom, das sich anbieten aber nicht"
                        + " setzen laesst, oder umgekehrt");

        // Und die GANZE Tafel geht in die Farbtafel der Welt, nicht die eingestellte
        // Auswahl: Jene wird genau einmal beim Anlegen gefragt, und was dort fehlt,
        // laesst sich in dieser Welt nie setzen. Ein spaeter angebotenes Biom wirkte dann
        // erst, nachdem der Weltordner geloescht wurde.
        assertTrue(quelle.contains("for (String name : Wolkenbiome.NAMEN)"),
                "alle() laeuft nicht mehr ueber die ganze Tafel - dann haengt die"
                        + " Farbtafel der Welt an einer Einstellung");
    }

    @Test
    @DisplayName("31az - der Wolkengenerator leert, schaltet Vanilla ab und setzt den Spawn")
    void derWolkengenerator() {
        // Dieselbe Tafel wie 31av fuer die Inseln, und aus demselben Grund: Keine Probe
        // kann einen Generator laufen lassen - dazu braucht er ein ChunkData vom Server.
        // Vier Zusagen stehen darum als TEXT, und jede einzelne waere im Spiel teuer.
        String quelle = Quellen.text(datei("WolkenGenerator.java"));

        // (1) ERST LEEREN. Was danach steht, ist gesetzt worden; ohne das Leeren haengt
        // es davon ab, was Vanilla vorher hineingelegt hat.
        assertTrue(quelle.contains("setRegion(0, unten, 0, 16, oben, 16, Material.AIR)"),
                "der Chunk wird nicht mehr geleert");

        // (2) VANILLA GANZ AUS. Jeder der fuenf Schalter auf true legte eine Landschaft
        // in eine Welt, die aus Leere besteht - und zwar nur in neuen Chunks, also in
        // einer Welt aus zwei Haelften.
        var schalter = new java.util.TreeMap<String, String>();
        var muster = Pattern.compile(
                "public boolean (shouldGenerate\\w+)[(][^)]*[)] [{][^}]*?return (\\w+);",
                Pattern.DOTALL);
        var treffer = muster.matcher(quelle);
        while (treffer.find()) {
            schalter.put(treffer.group(1), treffer.group(2));
        }

        assertEquals(java.util.Map.of(
                        "shouldGenerateNoise", "false",
                        "shouldGenerateSurface", "false",
                        "shouldGenerateCaves", "false",
                        "shouldGenerateDecorations", "false",
                        "shouldGenerateMobs", "false",
                        "shouldGenerateStructures", "false"),
                schalter,
                "alle sechs aus. Bei den Inseln stehen Bewuchs und Tiere auf true, hier"
                        + " waere beides wirkungslos: Wenn ein Chunk entsteht, liegt"
                        + " darin kein Block - die Insel kommt erst danach aus der Hand"
                        + " des Plugins");

        // (3) DER ERSCHEINUNGSPUNKT auf der Ankunftsplattform, einen Block darueber.
        // Liegt er woanders, faellt jeder Ankommende - und Ankunftspunkt findet in acht
        // Ringen keinen festen Block, also fuehrt dorthin GAR KEINE Reise.
        assertTrue(quelle.contains("plan.ankunftsMitte() + 0.5, Wolkenboden.BODEN + 1"),
                "der Erscheinungspunkt sitzt nicht mehr auf der Ankunftsplattform");

        // (4) DER VORTEST vor der Schleife. Die Plattform liegt in vier Chunks, die Welt
        // hat 390.000; ohne ihn fragte jeder einzelne 2.560-mal nach einem Stueck, das es
        // dort nicht gibt.
        assertTrue(quelle.contains("if (!beruehrtAnkunftsfeld(chunkX, chunkZ))"),
                "der billige Vortest ist weg - dann kostet jeder leere Chunk dieser Welt"
                        + " eine Schleife ueber 256 Spalten");
    }


    @Test
    @DisplayName("31ba - der Erztausch: angemeldet, auf HIGH, und die ART wird zuerst"
            + " gefragt")
    void derErztausch() {
        // VIER ZUSAGEN, DIE KEINE VERHALTENSPROBE SEHEN KANN - und jede einzelne waere im
        // Spiel teuer.
        String quelle = Quellen.text(datei("Erzwache.java"));

        // (1) DIE STUFE DER ANMELDUNG. HIGH wie bei der Angelwache: Hier wird GEAENDERT,
        // und wer danach zusieht (MONITOR), soll das Ergebnis sehen. Gelesen wird das
        // WORT und nicht die Zahl - die Zahl stuende als Erwartung in dieser Tafel, und
        // Bukkit darf sie aendern.
        int zweig = quelle.indexOf("public void beimBilden(BlockFormEvent");
        assertTrue(zweig > 0, "Erzwache hat keinen Zweig beimBilden mehr - dann prueft"
                + " diese Tafel die falsche Datei");
        int stufe = quelle.lastIndexOf("EventPriority.", zweig);
        assertTrue(stufe > 0, "vor beimBilden steht keine EventPriority");
        assertEquals("HIGH", quelle.substring(stufe + "EventPriority.".length(), zweig)
                        .split("[^A-Z_]")[0],
                "HIGH laeuft VOR MONITOR - der Tausch zuerst, das Zusehen danach");
        assertTrue(quelle.substring(stufe, zweig).contains("ignoreCancelled = true"),
                "ohne ignoreCancelled legt die Wache ein Erz in einen Block, den ein"
                        + " anderes Plugin gerade verhindert hat");

        // (2) DIE REIHENFOLGE DER TORE. Dasselbe Ereignis bildet auch Schnee und Eis, und
        // in einem Biom wie snowy_plains faellt es waehrend eines Schneesturms fuer JEDEN
        // freien Block. Stuende die Inselabfrage davor, waere das die teuerste Zeile des
        // Servers - und keine Verhaltensprobe koennte es messen, weil beide Reihenfolgen
        // dasselbe Ergebnis geben.
        int art = quelle.indexOf("Erzwerte.istAusgang(", zweig);
        int insel = quelle.indexOf("buch.inselAn(", zweig);
        int welt = quelle.indexOf("weltschluessel.equals(", zweig);
        assertTrue(art > 0 && insel > 0 && welt > 0,
                "einer der drei Tore ist weg: Art " + art + ", Welt " + welt
                        + ", Insel " + insel);
        assertTrue(art < welt && welt < insel,
                "erst die ART, dann die WELT, dann die Insel - gefunden: Art " + art
                        + ", Welt " + welt + ", Insel " + insel);

        // (3) JEDER NAME DER AUSGELIEFERTEN TABELLE IST EIN GEGENSTAND. Material kennt
        // ohne laufenden Server keine Bloecke, aber getMaterial geht - also kann diese
        // Tafel den Tippfehler finden, den sonst erst ein Spieler findet: Ein Name, den
        // es nicht gibt, kostet SEINE Zeile, und zwar still bis zur ersten Klage im
        // Protokoll.
        for (String name : de.freimarken.kern.Erzwerte.VORGABE_TABELLE.keySet()) {
            assertNotNull(org.bukkit.Material.getMaterial(name.toUpperCase(java.util.Locale.ROOT)),
                    "Erzwerte.VORGABE_TABELLE nennt " + name + " - diesen Gegenstand"
                            + " kennt Minecraft nicht, und die Zeile faellt im Spiel"
                            + " stumm weg");
        }

        // (4) UND DIE ANMELDUNG, aus dem Zweig, der die Welt schon geprueft hat. Eine
        // Wache, die niemand am Server anmeldet, ist gebaut, gruen geprueft und im Spiel
        // ohne Wirkung - dieselbe Lage wie bei der Truhenwache (31an) und beim Shopbuch
        // (31au).
        String plugin = Quellen.text(datei("FreimarkenPlugin.java"));
        assertTrue(plugin.contains("registerEvents(new Erzwache("),
                "die Erzwache wird nicht angemeldet - dann gibt der Generator fuer immer"
                        + " nur Kobbelstein");
        assertTrue(plugin.contains("richteErzeEin(welt)"),
                "richteErzeEin wird nicht gerufen - die Wache entsteht dann nie");
        int ruf = plugin.indexOf("richteErzeEin(welt)");
        int wolken = plugin.indexOf("private void richteWolkenEin()");
        int danach = plugin.indexOf("private void", wolken + 20);
        assertTrue(ruf > wolken && ruf < danach,
                "der Ruf steht nicht in richteWolkenEin - dort und nur dort ist"
                        + " geprueft, dass es die Welt und das Buch gibt");
    }


    @Test
    @DisplayName("31bb - jeder Gegenstand der Aufgaben und der Punktetabelle EXISTIERT")
    void jederGegenstandExistiert() {
        // Material kennt ohne laufenden Server keine Bloecke, aber getMaterial geht -
        // also kann diese Tafel den Tippfehler finden, den sonst erst ein Spieler
        // findet. Und er waere teuer: Ein verlangter Gegenstand, den es nicht gibt,
        // laesst die GANZE Herausforderung wegfallen (Aufgabenliste klagt und
        // ueberspringt sie), und eine Punktzeile faellt still weg. Beides merkt der
        // Betreiber erst, wenn ein Spieler fragt, warum eine Aufgabe fehlt.
        for (var aufgabe : de.freimarken.kern.Aufgabenwerte.VORGABE_LISTE) {
            for (String gegenstand : aufgabe.verlangt().keySet()) {
                assertNotNull(org.bukkit.Material.getMaterial(
                                gegenstand.toUpperCase(java.util.Locale.ROOT)),
                        "Die Herausforderung " + aufgabe.schluessel() + " verlangt "
                                + gegenstand + " - diesen Gegenstand kennt Minecraft"
                                + " nicht, und die ganze Aufgabe faellt im Spiel weg");
            }
        }
        for (String block : de.freimarken.kern.Punktwerte.VORGABE_TABELLE.keySet()) {
            assertNotNull(org.bukkit.Material.getMaterial(
                            block.toUpperCase(java.util.Locale.ROOT)),
                    "Die Punktetabelle nennt " + block + " - diesen Block kennt"
                            + " Minecraft nicht, und die Zeile faellt still weg");
        }

        // UND die Namen der ausgelieferten Aufgaben haben ihr Wort in der Vorlage. Fehlt
        // es, steht im Spiel ein Pfad - der Start meldet es, aber in der ausgelieferten
        // Datei ist es ein Fehler beim Abschreiben.
        String vorlage = ausgelieferte("sprache-vorlage.yml");
        for (var aufgabe : de.freimarken.kern.Aufgabenwerte.VORGABE_LISTE) {
            assertTrue(vorlage.contains("    " + aufgabe.schluessel() + ": \""),
                    "zur Herausforderung " + aufgabe.schluessel() + " fehlt die Zeile "
                            + Aufgabenname.VORSILBE + aufgabe.schluessel()
                            + " in der sprache-vorlage.yml");
        }
    }

    @Test
    @DisplayName("31bc - Zaehlung und Biomwechsel sind VERDRAHTET und arbeiten in Happen")
    void zaehlungUndBiomwechselSindVerdrahtet() {
        // ZUSAGEN, DIE KEINE VERHALTENSPROBE SIEHT. Beide Haende entstehen in
        // richteWolkenEin und werden ueber ein Buendel in den Befehl gegeben - eine
        // gebaute, gruen gepruefte Hand, die niemand verdrahtet, ist im Spiel ohne
        // Wirkung (dieselbe Lage wie bei der Truhenwache, 31an).
        String plugin = Quellen.text(datei("FreimarkenPlugin.java"));
        assertTrue(plugin.contains("richtePflegeEin(welt);"),
                "richtePflegeEin wird nicht gerufen - dann gibt es keine Punkte und"
                        + " keinen Biomwechsel");
        int ruf = plugin.indexOf("richtePflegeEin(welt);");
        int wolken = plugin.indexOf("private void richteWolkenEin()");
        int danach = plugin.indexOf("private void", wolken + 20);
        assertTrue(ruf > wolken && ruf < danach,
                "der Ruf steht nicht in richteWolkenEin - dort und nur dort ist"
                        + " geprueft, dass es die Welt, das Buch und die Staende gibt");
        assertTrue(plugin.contains("wolkenpflege, kasse, sprache"),
                "das Buendel geht nicht in den Befehl - dann sagt /wolke punkte fuer"
                        + " immer, dass es keine Punkte gibt");

        // DIE HAPPEN. Ein Feld hat 64 Chunks mit mehr als sechs Millionen Bloecken; in
        // einem Zug gezaehlt stuende der Server. Beide Haende holen ihre Happen aus
        // DERSELBEN Chunkfolge - zwei Abschriften der Rechnung waeren zwei Orte fuer
        // denselben Fehler.
        for (String datei : java.util.List.of("Inselzaehlung.java", "Biomhand.java")) {
            String quelle = Quellen.text(datei(datei));
            assertTrue(quelle.contains("naechsterHappen("),
                    datei + " holt keinen Happen mehr - dann laeuft die Arbeit in EINEM"
                            + " Takt, und der Server steht dabei");
            assertTrue(quelle.contains("abmelden.run()"),
                    datei + " meldet seinen Zeitgeber nicht ab - er schlaegt dann fuer"
                            + " immer weiter");
            assertTrue(quelle.contains("catch (RuntimeException fehler)"),
                    datei + " fasst den Takt nicht in try - eine Ausnahme in einem"
                            + " getakteten Auftrag meldet Bukkit NICHT ab, sie kaeme bei"
                            + " jedem Tick wieder");
        }

        // DER SCHNAPPSCHUSS und die HOEHENGRENZE. Ohne beides liest ein Chunk 98.304
        // Blockobjekte, von denen in dieser Welt fast alle Luft sind.
        String zaehlung = Quellen.text(datei("Inselzaehlung.java"));
        assertTrue(zaehlung.contains("getChunkSnapshot(false, false, false)"),
                "die Zaehlung liest keinen Schnappschuss mehr");
        assertTrue(zaehlung.contains("schnitt.getHighestBlockYAt(x, z)"),
                "die Zaehlung laeuft bis zur Welthoehe statt bis zum hoechsten Block -"
                        + " ueber einer Wolkeninsel ist alles Luft");

        // UND DIE BIOMZELLE: In Schritten von 4, denn so legt Minecraft Biome ab. Ein
        // setBiome je Block waere derselbe Vorgang 64-mal.
        String biome = Quellen.text(datei("Biomhand.java"));
        assertTrue(biome.contains("x += ZELLE") && biome.contains("y += ZELLE")
                        && biome.contains("z += ZELLE"),
                "die Biomhand setzt nicht mehr in Zellenschritten");
    }

    @Test
    @DisplayName("31cq - jede Grenze der Rangleiter hat im Spielmodul einen LESER")
    void jedeRanggrenzeHatEinenLeser() {
        // DIE ZUSAGE STEHT IM JAVADOC VON BEKANNTE_ZAHLEN und hatte bis heute keine
        // Probe: "Ein Name ohne Leser waere ein Recht, das vergeben wird und nichts tut."
        // Genau so sieht es im Spiel aus: Der Betreiber traegt heimatpunkte: 3 ein, das
        // Plugin vergibt freimarken.heimatpunkte.3 - und niemand fragt je danach. Kein
        // Fehler im Protokoll, keine rote Probe, nur eine Zeile ohne Wirkung.
        //
        // Gesucht wird die VORSILBE mit Punkt am Ende, denn genau so liest Rangzahl:
        // Ein Leser, der freimarken.parzellen (ohne Punkt) schriebe, faende auch
        // freimarken.parzellenzahl.
        StringBuilder ohneLeser = new StringBuilder();
        for (String name : new java.util.TreeSet<>(
                de.freimarken.kern.Rangwerte.BEKANNTE_ZAHLEN)) {
            String vorsilbe = "\"" + de.freimarken.kern.Rangwerte.VORSILBE + name
                    + ".\"";
            boolean gefunden = false;
            for (Path datei : Quellen.alle()) {
                if (Quellen.text(datei).contains(vorsilbe)) {
                    gefunden = true;
                    break;
                }
            }
            if (!gefunden) {
                ohneLeser.append(ohneLeser.isEmpty() ? "" : ", ").append(name);
            }
        }
        assertEquals("", ohneLeser.toString(),
                "diese Grenzen stehen in Rangwerte.BEKANNTE_ZAHLEN, aber kein Zweig des"
                        + " Spielmoduls nennt ihre Vorsilbe - ein Rang, der sie hebt,"
                        + " hebt nichts: " + ohneLeser);

        // UND DIE GEGENRICHTUNG: Was Rangzahl liest, muss die Leiter auch vergeben
        // koennen. Eine Vorsilbe ohne Namen in BEKANNTE_ZAHLEN waere eine Grenze, die
        // nur die Rechteverwaltung heben kann und kein Rang - und die Datei wiese jeden
        // Versuch mit "kennt das Programm nicht" ab.
        java.util.regex.Matcher sucher = java.util.regex.Pattern
                .compile("\"freimarken\\.([a-z]+)\\.\"")
                .matcher(Quellen.text(datei("ParzellenBefehle.java"))
                        + Quellen.text(datei("WerderBefehle.java"))
                        + Quellen.text(datei("WolkeBefehle.java"))
                        + Quellen.text(datei("FreistadtBefehle.java")));
        java.util.Set<String> gelesen = new java.util.TreeSet<>();
        while (sucher.find()) {
            if (!"extra".equals(sucher.group(1))) {
                gelesen.add(sucher.group(1));
            }
        }
        assertFalse(gelesen.isEmpty(),
                "keine einzige Vorsilbe gefunden - dann prueft diese Tafel nichts");
        assertEquals(new java.util.TreeSet<>(
                        de.freimarken.kern.Rangwerte.BEKANNTE_ZAHLEN), gelesen,
                "die Grenzen, die Rangzahl LIEST, und die, die ein Rang VERGEBEN kann,"
                        + " sind nicht dieselben. Dann gibt es entweder ein Recht ohne"
                        + " Leser oder eine Grenze, die kein Rang heben kann.");
    }

    @Test
    @DisplayName("31cy - jedes Recht, das eine ausgelieferte Datei NENNT, gibt es auch")
    void jedesGenannteRechtGibtEs() {
        // FUND VOM 20.11.2026, Teil H: Der Kommentar an inseln.hoechstzahl nannte
        // "freimarken.werder.anzahl.<n> setzt die Grenze fuer einen Rang". Gelesen wird
        // freimarken.inseln.<n>. Wer dem Kommentar folgte, vergab ein Recht, nach dem
        // niemand fragt - und sah einen Rang, der nicht wirkt. Rot wurde dabei nichts:
        // Ein Kommentar ist fuer den Uebersetzer Luft.
        //
        // Das ist die Gegenrichtung zu 31al: Jene prueft, dass jedes GEFRAGTE Recht
        // angemeldet ist. Diese prueft, dass jedes GENANNTE Recht gefragt wird.
        var genannt = new java.util.TreeSet<String>();
        // Die NEGATIVE Umschau ist noetig: Ohne sie faende das Muster auch
        // "de.freimarken.paper.FreimarkenPlugin" in der main-Zeile der plugin.yml - ein
        // Klassenname und kein Recht.
        var muster = Pattern.compile(
                "(?<![a-zA-Z.])freimarken\\.[a-z][a-z0-9.]*(?:<[a-z]+>)?");
        for (String name : List.of("config.yml", "plugin.yml", "sprache-vorlage.yml")) {
            var suche = muster.matcher(ausgelieferte(name));
            while (suche.find()) {
                String rein = suche.group();
                int klammer = rein.indexOf('<');
                if (klammer >= 0) {
                    rein = rein.substring(0, klammer);
                }
                while (rein.endsWith(".")) {
                    rein = rein.substring(0, rein.length() - 1);
                }
                // Eine Zahl am Ende ist ein WERT und kein Rechtename:
                // freimarken.parzellen.8 ist die Vorsilbe freimarken.parzellen. mit 8.
                genannt.add(rein.replaceAll("\\.[0-9]+$", ""));
            }
        }
        assertTrue(genannt.size() > 10,
                "nur " + genannt.size() + " Rechte in den Dateien gefunden - dann"
                        + " prueft diese Tafel fast nichts: " + genannt);

        var angemeldet = new java.util.TreeSet<String>();
        for (String zeile : ausgelieferte("plugin.yml").split("\n", -1)) {
            if (zeile.startsWith("  freimarken.") && zeile.endsWith(":")) {
                angemeldet.add(zeile.strip().substring(0, zeile.strip().length() - 1));
            }
        }

        var ohneLeser = new java.util.TreeSet<String>();
        for (String recht : genannt) {
            if (angemeldet.contains(recht)
                    || RECHTE_VORSILBEN.contains(recht + ".")) {
                continue;
            }
            // UNTER einer Vorsilbe steht ein frei gewaehlter Schluessel der
            // config.yml, und eine ausgelieferte Datei darf ihn NENNEN: Der
            // Kommentar bei welten.premium sagt "fuer vogtei also
            // freimarken.welt.vogtei", und das ist die Zeile, die der Betreiber
            // kopiert. Geprueft werden kann hier nur die Vorsilbe - was dahinter
            // steht, weiss erst die Datei, die die Welten fuehrt. Dass vogtei
            // DORT wirklich steht, haelt WeltenlisteTest mit der Namensliste.
            if (RECHTE_VORSILBEN.stream().anyMatch(recht::startsWith)) {
                continue;
            }
            ohneLeser.add(recht);
        }
        assertEquals(java.util.Set.of(), ohneLeser,
                "diese Rechte nennen die ausgelieferten Dateien, und es gibt sie nicht -"
                        + " weder als angemeldetes Recht (plugin.yml) noch als Vorsilbe"
                        + " einer Rangzahl. Wer dem Kommentar folgt, vergibt sie und"
                        + " nichts geschieht: " + ohneLeser);
    }

    @Test
    @DisplayName("31db - der Baum von /rang wird gebaut, angemeldet und misst ueber den"
            + " Aufstiegslauf")
    void rangbefehlIstVerdrahtet() {
        // ZWEI ZUSICHERUNGEN, und keine Verhaltensprobe sieht eine davon:
        //
        // 1. GEBAUT UND ANGEMELDET. Ein Baum, den niemand bei Paper anmeldet, ist der
        //    Fall, den dieses Projekt dreimal hatte (Truhenwache 31an, Shopbuch 31au,
        //    Erzwache): gebaut, gruen geprueft, ohne Wirkung. Hier waere er besonders
        //    bitter - /rang ist der Grund, aus dem die Rangwerbung in der config.yml
        //    seit dem 20.11.2026 ueberhaupt auf einen Befehl verweisen darf.
        // 2. DIE MESSTUER, ueber die BAUZEILE. Wer das Lambda auf eine leere Karte
        //    setzt, bekommt keine rote Probe: Die Auskunft zeigte dann jede Bedingung
        //    als offen, und ein Buerger laese, ihm fehlten vierzig Stunden. Dieselbe
        //    Schaerfung wie bei 31cv.
        String plugin = plugintext();

        assertTrue(plugin.contains("new RangBefehle(new Rangschau(rangablage,"),
                "/rang steht nicht auf der Rangablage und dem geteilten"
                        + " Wirtschaftsstand des Plugins");
        // DIE GRENZE DIESER ZEILE, gemessen am 20.11.2026 (Einspritzungsfall F8): Wer
        // die Anmeldung in ein "if (sprache == null)" huellt, laesst den TEXT stehen -
        // und dieses contains bleibt gruen, waehrend der Befehl im Spiel fehlt. Eine
        // Quelltexttafel sieht Text und keine Erreichbarkeit; javac sagt zu totem Code
        // in einem solchen Zweig nichts. Gefangen wird dieser Fall im STARTLAUF: Ohne
        // Anmeldung fehlt "freimarken:rang" in der Zeile "Befehle angemeldet: ...", und
        // die Konsole bekommt auf /rang Brigadiers englische Zeile statt des eigenen
        // Satzes. Beides ist am 20.11. mit und ohne Einspritzung gemessen worden.
        assertTrue(plugin.contains("verzeichnis.register(rangBefehle().rang(),"),
                "der Baum von /rang wird nicht angemeldet - dann gibt es den Befehl im"
                        + " Spiel nicht, und die Rangwerbung verweist wieder auf etwas,"
                        + " das niemand finden kann");
        var messtuer = java.util.regex.Pattern
                .compile("new Rangschau\\([^;]*messeFuer\\(wer, gebraucht\\)")
                .matcher(plugin);
        assertTrue(messtuer.find(),
                "/rang misst seine Bedingungen nicht ueber den Aufstiegslauf - und das"
                        + " ist die EINE Stelle, die messen darf");
    }

    @Test
    @DisplayName("31dd - die acht Rollen der Farbwelt und die Saetze kennen sich")
    void rollenUndSaetzeKennenSich() {
        // DREI LISTEN FUER EINE SACHE, und sie koennen auseinanderlaufen: Farbtafel.ROLLEN
        // (das Programm), der Block farben: der ausgelieferten Datei (Probe 2b haelt ihn
        // dagegen) und das Muster FARBTAG_IM_SATZ dieser Datei. Das Muster ist von Hand
        // ausgeschrieben, weil ein Muster kein Java ausfuehrt - also haelt diese Tafel es
        // gegen die Liste.
        //
        // Und die andere Richtung: Jede Rolle muss in den ausgelieferten SAETZEN wirklich
        // vorkommen. Eine Rolle, die niemand benutzt, ist eine Einstellung ohne Wirkung -
        // der Betreiber aendert sie und nichts geschieht.
        for (String rolle : Farbtafel.ROLLEN) {
            assertTrue(FARBTAG_IM_SATZ.matcher("<" + rolle + ">").find(),
                    "<" + rolle + "> fehlt im Muster FARBTAG_IM_SATZ - ein Satz mit"
                            + " dieser Rolle im Programm waere damit unsichtbar");
        }

        String vorlage = ausgelieferte("sprache-vorlage.yml");
        var ohneSatz = new TreeSet<String>();
        for (String rolle : Farbtafel.ROLLEN) {
            // Gezaehlt wird das SCHLIESSENDE Tag: Das oeffnende steht auch im Block
            // farben: als Kommentar und im Kopf der Datei, das schliessende nur in einem
            // Satz.
            if (!vorlage.contains("</" + rolle + ">")) {
                ohneSatz.add(rolle);
            }
        }
        assertEquals(new TreeSet<String>(), ohneSatz,
                "diese Rollen benutzt kein ausgelieferter Satz - eine Farbe, die der"
                        + " Betreiber aendern kann, ohne dass sich etwas aendert");
    }

    @Test
    @DisplayName("31dc - der Weg vom NAMEN zur Kennung fragt hasPlayedBefore, und zwar"
            + " an einer Stelle")
    void jederWegZurKennungFragtHasPlayedBefore() {
        // GEFUNDEN AM 20.11.2026, beim Bau von /freimarken geld: Diese Methode stand
        // ZWEIMAL wortgleich im Projekt, privat in BlockBefehle und in ShopBefehle, mit
        // derselben Begruendung in zwei Javadocs. Der Geldzweig haette sie zum dritten
        // Mal gebraucht.
        //
        // Keine Verhaltensprobe kann sie pruefen: Sie ruft Bukkit statisch und laeuft
        // ohne Server nicht. Was sie zusichert, ist aber genau die Art Zusage, die teuer
        // ist - getOfflinePlayer(String) gibt NIEMALS null, sondern erfindet fuer einen
        // unbekannten Namen eine frische Kennung. Ohne hasPlayedBefore() buchte ein
        // Tippfehler auf ein Geisterkonto, das die Kasse samt Startguthaben anlegt.
        var mitSchranke = new TreeSet<String>();
        for (var datei : Quellen.alleDateien()) {
            if (!datei.getFileName().toString().endsWith(".java")) {
                continue;
            }
            if (Quellen.text(datei).contains("hasPlayedBefore()")) {
                mitSchranke.add(datei.getFileName().toString());
            }
        }

        assertEquals(new TreeSet<>(Set.of("Spielername.java")), mitSchranke,
                "Die Schranke gegen die erfundene Kennung gehoert an EINE Stelle. Steht"
                        + " sie nirgends, bucht ein Tippfehler auf ein Konto, das es nie"
                        + " gab; steht sie an zwei Stellen, verliert eine davon sie"
                        + " eines Tages - und niemand legt die beiden nebeneinander.");

        String spielername = Quellen.text(Quellen.alleDateien().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Spielername.java"))
                .findFirst().orElseThrow());
        int methode = spielername.indexOf("kennungAuchAbwesend");
        assertTrue(methode >= 0, "kennungAuchAbwesend fehlt in Spielername");
        assertTrue(spielername.indexOf("hasPlayedBefore()", methode) >= 0,
                "die Schranke steht nicht IN kennungAuchAbwesend - und das ist die"
                        + " Methode, die aus einem getippten Namen eine Kennung macht");
    }

    @Test
    @DisplayName("31dg - die Hand baut das Menue FUER DIESEN Spieler")
    void handNimmtDieGefilterteTafel() {
        // DER FILTER IST GEBAUT UND GRUEN GEPRUEFT - hier haengt er an [21.11.2026].
        // MenuetafelTest 11 bis 13 halten, dass fuerRechte richtig auswaehlt; dass die
        // HAND das Ergebnis nimmt, hielt nichts. Nimmt jemand den Aufruf heraus
        // (Menuetafel sichtbar = tafel), bleibt der ganze Lauf gruen, und das Menue
        // bietet wieder jedem alles an - ein Klick auf einen Befehl, den requires
        // VERBIRGT, endet in Brigadiers englischer Zeile.
        //
        // Gemessen in der Einspritzungsrunde vom 21.11.2026: Dieser Fall war der EINZIGE
        // von zwanzig, den keine Probe fing. Die Hand liegt hinter
        // Bukkit.createInventory, also kommt dort keine hin - darum eine Tafel, wie bei
        // der Truhenwache (31an) und der Rangwache (31cr).
        String hand = Quellen.text(datei("Menuehand.java"));

        assertTrue(hand.contains("tafel.fuerRechte(wer::hasPermission)"),
                "die Hand baut das Menue nicht fuer DIESEN Spieler - dann sieht jeder"
                        + " jeden Eintrag, auch den, den er nicht tippen darf");
        // UND die gefilterte Tafel muss in den HALTER gehen, nicht nur ins Inventar: Die
        // Wache liest sie von dort wieder heraus, und nur deshalb ist ein weggefallenes
        // Fach auch per Klick unbekannt.
        assertTrue(hand.contains("new Menuehalter(sichtbar)"),
                "der Halter bekommt die ungefilterte Tafel - dann findet die Wache den"
                        + " Eintrag doch und fuehrt den Befehl aus");
    }

    @Test
    @DisplayName("31dh - wer Anwesende anmeldet, raeumt sie auch weg")
    void werAnmeldetRaeumtAuchWeg() {
        // DIE TABELLE DER ANWESENDEN IST STATISCH, also lebt ein Eintrag ueber die
        // Klassengrenze hinaus [21.11.2026]. Eine Probe, die "Anna" anmeldet und nicht
        // aufraeumt, laesst eine FREMDE Probendatei mit ihrer eigenen Anna auf den
        // falschen Spieler stossen - und die rote Meldung nennt die Datei, die es
        // ABBEKOMMEN hat, nicht die, die es verursacht. Genau so geschehen: Eine neue
        // Probe in RegelnBefehlTest machte WolkeBefehlTest rot.
        //
        // Die drei Dateien, die anmelden schon vorher riefen, halten die Regel von
        // selbst - sie war gelebte Praxis und nur unverbuergt.
        List<Path> proben = Quellen.alleProben();
        assertFalse(proben.isEmpty(), "der Probenbaum wurde nicht gefunden - dann laeuft"
                + " diese Tafel leer und gruen");

        List<String> ohneAufraeumen = new ArrayList<>();
        for (Path probe : proben) {
            String text = Quellen.text(probe);
            if (text.contains("Serverprobe.anmelden(")
                    && !text.contains("niemandMehrDa()")) {
                ohneAufraeumen.add(probe.getFileName().toString());
            }
        }
        assertEquals(List.of(), ohneAufraeumen, "Diese Probe meldet Anwesende an und"
                + " raeumt sie nicht weg. Serverprobe.niemandMehrDa() gehoert in ihr"
                + " Vorher - sonst findet eine andere Probendatei spaeter den falschen"
                + " Spieler unter einem Namen, den sie selbst vergeben hat.");
    }

    @Test
    @DisplayName("31dk - die Rundgangswache wird angemeldet, und nur bei vorhandenen"
            + " Stationen")
    void rundgangswacheIstVerdrahtet() {
        // OHNE DIESE ZEILEN ist der ganze Rundgang unsichtbar wirkungslos: Die Ablage
        // bliebe leer, niemand saehe eine Station, und eine Rangstufe, die ihn verlangt,
        // waere unerreichbar - ohne dass eine Probe etwas davon merkt. Dieselbe
        // Fehlergestalt wie bei der Truhenwache (31an), der Rangwache (31cr), der
        // Menuewache (31de) und der Anmeldewache (31di).
        String plugin = plugintext();

        assertTrue(plugin.contains("new Rundgang(rundgangsablage, rundgangsstationen,"
                        + " uhr)"),
                "der Rundgang wird nirgends gebaut - dann ist das Feld null, und der"
                        + " Aufstiegslauf kann die Bedingung nicht messen");
        assertTrue(plugin.contains("registerEvents(rundgangswache, this)"),
                "die Rundgangswache wird nicht angemeldet - dann sieht niemand eine"
                        + " Station, und der Rundgang ist nie abgeschlossen");

        // UND NUR BEI VORHANDENEN STATIONEN: Ab Werk gibt es keine, und dann darf kein
        // Zuhoerer an jedem Schritt haengen. Dieselbe Entscheidung wie bei der Torwache.
        int schalter = plugin.indexOf("if (rundgangswache.hatStationen())");
        int anmelden = plugin.indexOf("registerEvents(rundgangswache, this)");
        assertTrue(schalter >= 0 && schalter < anmelden,
                "die Anmeldung muss INNERHALB von if (rundgangswache.hatStationen())"
                        + " stehen - sonst hoert sie jeden Schritt jedes Spielers ab,"
                        + " ohne dass es eine Station gibt");

        // UND DIE ABLAGE IN BEIDEN BETRIEBSARTEN. Fehlte sie in einem Zweig, waere
        // rundgangsablage dort null und der erste Schritt in ein Gebaeude ein Absturz.
        assertTrue(plugin.contains("rundgangsablage = new SpeicherRundgang()"),
                "ohne Datenbank gibt es keine Rundgangsablage");
        assertTrue(plugin.contains("rundgangsablage = new MariaRundgang(datenbank)"),
                "mit Datenbank gibt es keine Rundgangsablage");

        // UND DER RUNDGANG MUSS VOR DEM AUFSTIEGSLAUF STEHEN: Jener nimmt ihn als
        // fuenfte Messquelle, und ein null dort waere ein Wurf beim Start.
        assertTrue(plugin.indexOf("rundgang = new Rundgang(")
                        < plugin.indexOf("aufstiegslauf = new Aufstiegslauf("),
                "der Rundgang muss VOR dem Aufstiegslauf gebaut werden");
    }

    @Test
    @DisplayName("31dl - die Startpruefung der Stationszeilen ist ueberhaupt GEBAUT")
    void stationenOhneWortWerdenGenannt() {
        // DREI STELLEN SAGEN DIESE PRUEFUNG ZU: Stationssatz ("beim Start wird gefragt,
        // ob jede Station ihre zwei Zeilen hat"), der Kommentar der config.yml ("Das
        // Startprotokoll nennt jede Station, die ihre Zeilen nicht hat") und die
        // Rechenprobe des Lesers. Eine Zusage ohne Bau waere hier besonders teuer: Der
        // Betreiber erfaehrt sonst erst von einem Spieler, dass in seinem Zunfthaus ein
        // Pfad steht.
        //
        // NUR DIE EXISTENZ, nicht der Aufruf [21.11.2026]: Dass jede nenne*-Pruefung
        // auch GERUFEN wird, haelt 31ab - generisch und fuer alle. Beides hier zu
        // pruefen war ein doppelter Riegel, und zwei Pruefungen fuer denselben Fall
        // machen sich unpruefbar (gemessen: Die Einspritzung "Aufruf entfernt" wurde von
        // beiden gefangen). Was 31ab NICHT deckt, ist die fehlende Methode selbst: Dann
        // findet sie dort niemand, und die Tafel ist gruen.
        assertTrue(plugintext().contains("private void nenneStationenOhneWort()"),
                "die zugesagte Startpruefung fehlt - drei Kommentare versprechen sie");
    }

    @Test
    @DisplayName("31dn - die Anmeldung entsteht VOR dem Handel und VOR dem Aufstiegslauf")
    void anmeldungStehtVorIhrenVerbrauchern() {
        // GEMESSEN IM STARTLAUF vom 21.11.2026, und nur dort zu sehen: Die Zuweisung
        // stand hinter richteShopsEin(). Shophandel bekam ein null, warf im
        // Konstruktor - und Paper schaltete das ganze Plugin ab ("Error occurred while
        // enabling Freimarken"). Alle 4786 Proben waren gruen: Jede baut den Handel
        // selbst und gibt ihm, was er braucht. Die REIHENFOLGE im onEnable prueft kein
        // Probenlauf, sondern ein Start - oder diese Tafel.
        //
        // Dieselbe Fehlergestalt wie bei der Rangwache und ihrer Ablage (31cr, "die
        // Wache muss NACH der Ablage entstehen"), nur eine Stufe hoeher: Dort ging es um
        // zwei Zeilen derselben Methode, hier um zwei Methoden des Starts.
        String plugin = plugintext();

        int anmeldung = plugin.indexOf("anmeldung = new Anmeldung(");
        assertTrue(anmeldung >= 0, "die Anmeldung wird nirgends gebaut");
        assertTrue(anmeldung < plugin.indexOf("richteShopsEin();"),
                "die Anmeldung muss VOR richteShopsEin() stehen - jener baut den"
                        + " Shophandel, und der verlangt sie im Konstruktor. Dahinter"
                        + " bekommt er null, und Paper schaltet das Plugin ab.");
        assertTrue(anmeldung < plugin.indexOf("regelnBefehle()"),
                "die Anmeldung muss VOR regelnBefehle() stehen - jener Baum braucht sie"
                        + " fuer die Weiche zwischen Lesefenster und Anmeldeformular");
    }

    @Test
    @DisplayName("31dm - das Formular gibt den JA-Knopf als ERSTEN an confirmation")
    void jaKnopfStehtVorn() {
        // DIE EINE ZEILE, DIE KEINE PROBE SEHEN KANN. Die Reihenfolge der zwei Knoepfe
        // ist an der NAHT geprueft (RegelnBefehlTest 9 sieht, was das Formular
        // hereingibt) - aber Formularhand ist die einzige Klasse des Plugins ohne
        // Zeugen: Die Dialog-API stirbt auf dem Probenklassenpfad im
        // Klasseninitialisierer. Vertauscht man hier die Argumente, stimmt der Knopf
        // "Spaeter" zu und "Ich nehme an" schliesst nur - ein Fenster, das das Gegenteil
        // tut, und der ganze Lauf bleibt gruen.
        //
        // Gemessen in der Einspritzungsrunde vom 21.11.2026: Dieser Fall war der EINZIGE
        // von dreissig, den nichts fing. Dieselbe Fehlergestalt wie bei Menuehand
        // (31dg), nur dass der Anschluss hier eine ARGUMENTREIHENFOLGE ist.
        String hand = Quellen.text(datei("Formularhand.java"));

        assertTrue(hand.contains("DialogType.confirmation(ja, nein)"),
                "der JA-Knopf muss der ERSTE sein: DialogType.confirmation(ja, nein)."
                        + " Vertauscht stimmt der Spaeter-Knopf zu.");
        // UND DIE TAT HAENGT AM JA-KNOPF, nicht am anderen: Ein commandTemplate am
        // Nein-Knopf liesse das Schliessen zustimmen.
        int jaKnopf = hand.indexOf("ActionButton ja = ");
        int tat = hand.indexOf("DialogAction.commandTemplate(befehl)");
        int neinKnopf = hand.indexOf("ActionButton nein = ");
        assertTrue(jaKnopf >= 0 && tat > jaKnopf && tat < neinKnopf,
                "DialogAction.commandTemplate muss am JA-Knopf stehen und vor dem"
                        + " Nein-Knopf - sonst tippt das Schliessen den Befehl");
    }

    @Test
    @DisplayName("31di - die Anmeldewache wird angemeldet, und nur bei eingeschalteter"
            + " Anmeldung")
    void anmeldewacheIstVerdrahtet() {
        // OHNE DIESE ZEILE IST DIE GANZE ANMELDUNG UNSICHTBAR WIRKUNGSLOS - und zwar
        // schlimmer als bei den anderen Wachen: Die Ablage bliebe leer, und weil der
        // BUNDESMARKT an genau dieser Ablage haengt, saehe jeder Spieler dort eine
        // Absage, die er nicht aufheben kann. Dieselbe Fehlergestalt wie bei der
        // Truhenwache (31an), der Rangwache (31cr) und der Menuewache (31de).
        String plugin = plugintext();

        assertTrue(plugin.contains("new Anmeldung(anmeldeablage, wirtschaft, uhr)"),
                "die Anmeldung wird nirgends gebaut - dann ist das Feld null, und der"
                        + " erste Klick an einem Bundstand stuerzt ab");
        assertTrue(plugin.contains("registerEvents(new Anmeldewache("),
                "die Anmeldewache wird nicht angemeldet - dann kommt beim Beitritt kein"
                        + " Formular, und niemand kann annehmen, waehrend der"
                        + " Bundesmarkt zu bleibt");

        // UND NUR BEI an(): Aus heisst wirklich aus - kein Zuhoerer am Beitritt, kein
        // Lesen der Ablage. Dieselbe Entscheidung wie bei der Torwache (if hatTore()).
        int schalter = plugin.indexOf("if (anmeldung.an())");
        int anmelden = plugin.indexOf("registerEvents(new Anmeldewache(");
        assertTrue(schalter >= 0 && schalter < anmelden,
                "die Anmeldung der Wache muss INNERHALB von if (anmeldung.an()) stehen -"
                        + " sonst liest sie bei jedem Beitritt eine Ablage, die niemand"
                        + " braucht");

        // UND DIE ABLAGE IN BEIDEN BETRIEBSARTEN. Fehlte sie in einem Zweig, waere
        // anmeldeablage dort null und der erste Beitritt ein Absturz - auf genau dem
        // Server, auf dem man eine neue Fassung ausprobiert.
        assertTrue(plugin.contains("anmeldeablage = new SpeicherAnmeldungen()"),
                "ohne Datenbank gibt es keine Anmeldeablage");
        assertTrue(plugin.contains("anmeldeablage = new MariaAnmeldungen(datenbank)"),
                "mit Datenbank gibt es keine Anmeldeablage");
    }

    @Test
    @DisplayName("31dj - der Befehl des Annehmen-Knopfes ist ein Zweig, den es gibt")
    void knopfbefehlIstEinZweig() {
        // DER KNOPF TIPPT EINEN BEFEHL, und eine getippte Zeichenkette, die ins Leere
        // zeigt, endet in Brigadiers englischem "Unknown or incomplete command" - mitten
        // in der Zustimmung, und ohne dass eine Probe des Formulars es sehen koennte:
        // Jene prueft, WAS der Knopf tippt, nicht ob es den Zweig gibt.
        //
        // Darum baut das Formular den Befehl aus den Konstanten des Befehls
        // (ARGUMENT + " " + ANNEHMEN) statt ihn abzuschreiben, und diese Tafel haelt die
        // andere Seite: dass der Baum genau diesen Zweig traegt.
        String formular = Quellen.text(datei("Anmeldeformular.java"));
        assertTrue(formular.contains("RegelnBefehle.ARGUMENT + \" \"\n            + RegelnBefehle.ANNEHMEN")
                        || formular.contains("RegelnBefehle.ARGUMENT")
                        && formular.contains("RegelnBefehle.ANNEHMEN"),
                "der Befehl des Knopfes muss aus den Konstanten des Befehls gebaut"
                        + " werden und nicht abgeschrieben sein");

        String befehle = Quellen.text(datei("RegelnBefehle.java"));
        assertTrue(befehle.contains("Commands.literal(ANNEHMEN)"),
                "der Baum von /regeln traegt den Zweig ANNEHMEN nicht - dann zeigt der"
                        + " Knopf des Formulars ins Leere");
        assertTrue(befehle.contains(".executes(this::nimmAn)"),
                "der Zweig ANNEHMEN hat kein executes - dann endet der Knopf in"
                        + " Brigadiers englischer Meldung");
    }

    @Test
    @DisplayName("31dq - die gerechnete Stadt ist verdrahtet: Plan, Pflaster, Aufbau")
    void dieStadtIstVerdrahtet() {
        String plugin = Quellen.text(datei("FreimarkenPlugin.java"));

        // DER PLAN WIRD GELESEN UND BENUTZT. Ein Plan, den niemand ruft, rechnet eine
        // Stadt, die nicht entsteht.
        assertTrue(plugin.contains("Stadtliste.ausKonfiguration(getConfig()"),
                "die Stadt-Einstellungen muessen gelesen werden - sonst sind bauen und"
                        + " quartiere Schluessel ohne Wirkung");
        assertTrue(plugin.contains("stadtplan = stadt.plan(masseDerVorlagen("),
                "der gelesene Plan muss im Feld landen - UND die Masse der Vorlagen"
                        + " bekommen: Die config.yml kennt nur Namen, und ein Plan, der"
                        + " die Masse raet, legt Parzellen, in die die Datei nicht"
                        + " passt");
        assertTrue(plugin.contains("strukturen.groesse(name)"),
                "die Masse muessen aus der Strukturverwaltung kommen");
        assertTrue(plugin.contains("Stadtplan.passtInEineZeile(vorlage)"),
                "und eine zu tiefe Vorlage muss geklagt werden - mit DERSELBEN Regel,"
                        + " nach der der Plan sie uebergeht");
        assertTrue(plugin.contains("Pflasterstoffe.ausKonfiguration(getConfig()"),
                "das Pflaster muss gelesen werden - sonst ist bauten.pflaster ein"
                        + " Abschnitt ohne Wirkung");
        assertTrue(plugin.contains("baueStadt(griff, fein, strukturen)"),
                "die Stadt muss beim Einrichten Altfurts gebaut werden - sonst ist der"
                        + " ganze Rechenweg gebaut, gruen geprueft und unsichtbar");
        assertTrue(plugin.contains("altfurtaufbau.pflastere(stadtplan"),
                "das Pflaster muss gesetzt werden");
        assertTrue(plugin.contains("altfurtaufbau.istGepflastert(stadtplan"),
                "und nur, wenn noch nicht gepflastert ist - sonst setzt jeder Start"
                        + " dreissigtausend Bloecke neu");

        // DAS PFLASTER KOMMT VOR DEN HAEUSERN. Es schreibt die Bodenebene, und ein Haus
        // steht darauf: Wer erst die Haeuser setzte, schnitte ihnen die unterste Ebene
        // weg.
        // GEMESSEN AN DER SETZENDEN SCHLEIFE und nicht an der ersten, die ueber
        // haeuser() laeuft: Die erste FILTERT nur (welches Haus trifft Handarbeit)
        // und muss sogar vor dem Pflaster stehen, denn ihr Ergebnis geht in die
        // Schutzliste.
        int pflaster = plugin.indexOf("altfurtaufbau.pflastere(stadtplan");
        int haeuser = plugin.indexOf("for (Platzhalterbau bau : zuBauen)");
        int filter = plugin.indexOf("for (Platzhalterbau bau : stadtplan.haeuser())");
        assertTrue(pflaster > 0 && haeuser > 0 && filter > 0,
                "alle drei Stellen muessen es geben");
        assertTrue(pflaster < haeuser,
                "das Pflaster muss VOR den Haeusern gesetzt werden");
        assertTrue(filter < pflaster,
                "und der Filter vor dem Pflaster - aus ihm kommt die Schutzliste");

        // DIE UNBERUEHRTEN QUADER sind dieselben, die schon den Bauten im Weg stehen
        // duerfen - dazu die Bauten selbst und die Ladeneinheiten. Eine zweite
        // Zusammenstellung waeren zwei Wahrheiten darueber, was Handarbeit ist.
        assertTrue(plugin.contains("new ArrayList<>(torquader)"),
                "die Tore gehoeren zu den unberuehrten Quadern");
        assertTrue(plugin.contains("platzhalterbauten.alle().forEach(bau ->"
                        + " unberuehrt.add(bau.bereich()))"),
                "die eingetragenen Bauten auch");
        assertTrue(plugin.contains("ladenverzeichnis.alle().forEach(einheit ->"),
                "und die Ladeneinheiten");
        assertTrue(plugin.contains("unberuehrteQuader = List.copyOf(unberuehrt)"),
                "und sie muessen im Feld landen");
        assertTrue(plugin.contains("pflasterstoffe, griff, schutz"),
                "das Pflastern muss die Schutzliste bekommen - ohne sie laeuft es"
                        + " ueber die Handarbeit hinweg");
        // UND DIE HAEUSER SIND DARIN: Das Pflaster schreibt die Bodenebene, und die
        // unterste Hausebene IST der Fussboden. Ohne sie bekaeme jedes Zimmer beim
        // Neu-Pflastern Erde, und stehtSchon baut es nicht neu.
        assertTrue(plugin.contains("List<Quader> schutz = new ArrayList<>("
                        + "unberuehrteQuader)")
                        && plugin.contains("zuBauen.forEach(bau -> "
                                + "schutz.add(bau.bereich()))"),
                "die Hausquader muessen zur Schutzliste gehoeren");
        // EIN HAUS, DAS HANDARBEIT TRIFFT, WIRD NICHT GESETZT.
        assertTrue(plugin.contains("bau.bereich().schneidet(fest)")
                        && plugin.contains("imWeg.add(bau.schluessel())"),
                "ein gerechnetes Haus auf Handarbeit muss wegfallen und gemeldet"
                        + " werden");
        // UND EINE AELTERE, GROESSERE STADT MUSS SICH MELDEN.
        assertTrue(plugin.contains("meldeAltereStadt(griff)"),
                "eine Einstellung, die nur halb wirkt, muss es sagen");
        // UND EIN ERSCHEINUNGSPUNKT IM HAUS AUCH: Er liegt ab Werk bei 0/0, und
        // das ist seit der Ringstadt das aeussere Haeuserband. nenneSchlechteAnkunft
        // sieht es NICHT - das Innere eines Hauses ist ein Platz zum Stehen.
        assertTrue(plugin.contains("meldeSpawnImHaus()"),
                "ein Erscheinungspunkt im Haus muss gemeldet werden");
        assertTrue(plugin.contains("altfurtFrisch.setSpawnLocation("),
                "bei einer FRISCH angelegten Welt muss der Punkt auf den Markt"
                        + " gesetzt werden");
        assertTrue(plugin.contains("gebaut.neu().contains(bautenwelt)"),
                "und nur dann - sonst ueberschriebe jeder Start ein"
                        + " /setworldspawn des Betreibers");
        // DIE TORHAEUSER WERDEN GESETZT UND IHRE NISCHEN NACHGERECHNET.
        assertTrue(plugin.contains("baueTorhaeuser(griff, fein)"),
                "die Torhaeuser muessen gesetzt werden - sonst ist die Mauer"
                        + " geschlossen und die Portale stecken darin");
        assertTrue(plugin.contains("pruefeNische(plan, seite, true, tor.links()")
                        && plugin.contains("pruefeNische(plan, seite, false,"
                                + " tor.rechts()"),
                "beide Nischen jedes Torhauses muessen gegen welten:"
                        + " nachgerechnet werden");
        // UND DAS PFLASTER KOMMT VOR DEN TORHAEUSERN: Ein Torhaus ersetzt das
        // Stueck Mauer, auf dem es steht. Umgekehrt waere sein Durchgang danach
        // wieder zugemauert.
        int torhaeuser = plugin.indexOf("baueTorhaeuser(griff, fein)");
        assertTrue(torhaeuser > pflaster,
                "die Torhaeuser muessen NACH dem Pflaster gesetzt werden");

        // UND DER EINSTIEG HAENGT NICHT MEHR AN DEN EINGETRAGENEN BAUTEN ALLEIN: Eine
        // leere haeuser-Liste mit eingeschalteter Stadt muss trotzdem bauen.
        assertTrue(plugin.contains("platzhalterbauten.leer() && stadtplan == null"),
                "ohne eingetragene Bauten, aber mit Stadt, wird gebaut");
    }

    @Test
    @DisplayName("31dr - das Pflaster setzt keine Luft und laeuft nicht ueber Handarbeit")
    void dasPflasterSetztKeineLuft() {
        String aufbau = Quellen.text(datei("Altfurtaufbau.java"));
        String stoffe = Quellen.text(datei("Pflasterstoffe.java"));

        // KEIN EINTRAG FUER AUSSEN: Luft ist ein Block, und auf dem Boden einer Ebene
        // LOESCHT sie ihn. Wer dann darueber laeuft, faellt ins Leere.
        assertFalse(stoffe.contains("Lage.AUSSEN, Material."),
                "AUSSEN darf ab Werk keinen Stoff haben - sonst wird jenseits der Mauer"
                        + " gepflastert, und mit Luft geloescht");
        // OHNE ODER: Der Zweig muss in Altfurtaufbau stehen, denn DORT wird gesetzt.
        // Vorher erfuellte schon der Riegel im Pflasterstoffe-Konstruktor die
        // Zusage, und der ueberspringt nichts.
        assertTrue(aufbau.contains("if (boden == null)"),
                "eine Lage ohne Stoff muss im Pflasterlauf uebersprungen werden");
        assertTrue(stoffe.contains("Lage.AUSSEN")
                        && stoffe.contains("keine Wirkung"),
                "ein Eintrag fuer aussen muss geklagt werden - das Pflaster kommt"
                        + " dort nie hin");

        // DIE HANDARBEIT WIRD GEFRAGT, BEVOR gesetzt wird.
        int frage = aufbau.indexOf("liegtIn(unberuehrt, x, y, z)");
        int setzen = aufbau.indexOf("griff.setze(x, y, z, boden)");
        assertTrue(frage > 0 && setzen > 0, "beide Stellen muessen es geben");
        assertTrue(frage < setzen, "erst fragen, dann setzen");

        // NUR DIE BODENEBENE, plus die Mauer: Wer ueber dem Boden pflasterte, loeschte
        // ein Haus, das darauf steht.
        assertTrue(aufbau.contains("Math.max(1, plan.mauerhoehe(x, z))"),
                "die Hoehe kommt aus dem Plan - eine Zahl hier waere eine zweite"
                        + " Wahrheit ueber die Mauer");
        assertTrue(aufbau.contains("for (int y = Stadtplan.BODEN_Y;"
                        + " y < Stadtplan.BODEN_Y + ebenen; y++)"),
                "gepflastert wird ab der Bodenebene nach oben");
    }

    @Test
    @DisplayName("31dp - die Naht fuer Tuer und Schild ist verdrahtet und setzt ohne Physik")
    void feingriffIstVerdrahtet() {
        // DER EINE TEIL, DEN KEINE PROBE ERREICHT [23.11.2026]: Was
        // Altfurtaufbau.Feingriff.AufDerWelt mit Bukkit tut, laeuft nur auf einem Server -
        // createBlockData() geht ueber Bukkit.getServer(). AltfurtaufbauTest prueft darum
        // gegen eine Mitschrift, und die sagt nichts darueber, ob die ECHTE Fassung je
        // gerufen wird. Eine gebaute, gruen geprueefte Naht ohne Verdrahtung waere
        // unsichtbar wirkungslos - dieselbe Gestalt wie bei 31an, 31au, 31cr und 31de.
        String plugin = plugintext();
        assertTrue(plugin.contains("new Altfurtaufbau.Feingriff.AufDerWelt(welt)"),
                "richteAltfurtEin baut die echte Naht nicht - dann hat jedes Haus ein"
                        + " Loch in der Wand und kein Schild, und alle Proben bleiben"
                        + " gruen");
        assertTrue(plugin.contains("altfurtaufbau.baue(bau, griff, fein,"),
                "die Naht wird gebaut und nicht weitergegeben");
        // UND DIE DRITTE NAHT: Ohne sie waere jedes Haus gerechnet, und Handarbeit
        // ueberlebte keinen Weltverlust.
        assertTrue(plugin.contains("new Strukturgriff.AufDerWelt(welt,"),
                "richteAltfurtEin baut die Strukturnaht nicht - dann gewinnt nie eine"
                        + " gesicherte Datei, und jedes Haus bleibt gerechnet");
        assertTrue(plugin.contains("getServer().getStructureManager()"),
                "die Strukturnaht braucht Papers StructureManager - ein eigener"
                        + " NBT-Leser waere eine zweite Wahrheit ueber ein Dateiformat,"
                        + " das Mojang pflegt");

        // UND DER VERSATZ BEI DREHUNG WIRD NICHT HINTER DER NAHT GERECHNET: Minecraft
        // dreht um die gesetzte Stelle, die Ecke wandert also mit. Die vier Faelle sind
        // am laufenden Server gemessen [23.11.2026] - aber eine Rechnung, die nur ein
        // laufender Server ausfuehrt, kann keine Probe nachmessen. Sie liegt darum im
        // Kern, wo BauplanTest die vier gemessenen Zahlenpaare festhaelt.
        String strukturen = Quellen.text(datei("Strukturgriff.java"));
        assertTrue(strukturen.contains("Bauplan.setzstelleX(bereich, grad,")
                        && strukturen.contains("Bauplan.setzstelleZ(bereich, grad,"),
                "der Versatz kommt nicht aus Bauplan.setzstelleX/Z - dann steht ein"
                        + " gedrehtes Haus neben seinem Quader, und keine Probe sieht es");
        assertFalse(strukturen.contains("bereich.vonX() +")
                        || strukturen.contains("bereich.vonZ() +"),
                "Strukturgriff rechnet den Versatz selbst - dort ist er unpruefbar;"
                        + " hereingegeben wird nur die Groesse aus der Datei");

        // DIE ZWEITE ECKE VON fill IST AUSSCHLIESSEND - am laufenden Server gemessen
        // [23.11.2026]: Ohne das +1 kam ein Quader von 11x14x7 als Struktur von 10x13x6
        // heraus, bei jeder Groesse genau eine Ebene zu wenig je Achse. Eine fehlende
        // Ebene ist die halbe Wand eines Hauses, und gesehen wird es erst, wenn die
        // Datei das naechste Mal gesetzt wird.
        assertTrue(strukturen.contains("bereich.bisX() + 1")
                        && strukturen.contains("bereich.bisY() + 1")
                        && strukturen.contains("bereich.bisZ() + 1"),
                "fill nimmt die Groesse als DIFFERENZ der beiden Stellen - ohne +1 je"
                        + " Achse fehlt der gesicherten Datei eine ganze Ebene");

        // UND WELCHE DATEI, DAS ENTSCHEIDET DER KERN: Eine eigene Datei steht in der
        // Weltlage und dreht sich nicht, eine Vorlage steht in der Normlage. Wer den
        // Namen aus der einen Regel und den Grad aus der anderen nimmt, baut ein
        // verdrehtes Haus - am laufenden Server gesehen, als beides getrennt war.
        String aufbau = Quellen.text(datei("Altfurtaufbau.java"));
        assertTrue(aufbau.contains("Bauplan.Dateilage lage = Bauplan.dateilage(bau)")
                        && aufbau.contains("strukturen.setze(lage.datei(),"
                                + " bau.bereich(), lage.grad())"),
                "Datei und Drehung muessen aus EINER Hand kommen (Bauplan.dateilage)");
        assertFalse(aufbau.contains("Bauplan.drehungsgrade("),
                "die blosse Zuordnung Richtung-zu-Grad darf hier nicht gerufen werden -"
                        + " sie weiss nicht, ob dieser Bau sich ueberhaupt dreht");

        String hand = Quellen.text(datei("Altfurtaufbau.java"));
        // OHNE PHYSIK, beide Male. Mit Nachbarschaftsmeldungen fiele die Tuer, noch
        // waehrend die Wand daneben entsteht, und das Schild fiele von der Wand.
        assertEquals(7,
                hand.split(java.util.regex.Pattern.quote("setBlockData("), -1)
                        .length - 1,
                "sieben Stellen setzen Blockdaten: die zwei Tuerhaelften, das Schild, die"
                        + " Dachtreppe, die Glasscheibe, der liegende Balken und die"
                        + " Leiter");
        assertFalse(hand.contains("setBlockData(unten)")
                        || hand.contains("setBlockData(oben)")
                        || hand.contains("setBlockData(treppe)")
                        || hand.contains("setBlockData(scheibe)")
                        || hand.contains("setBlockData(balken)")
                        || hand.contains("setBlockData(leiter)")
                        || hand.contains("setBlockData(wandschild)"),
                "setBlockData OHNE das zweite Argument wendet Physik an - dann faellt die"
                        + " Tuer, noch waehrend die Wand daneben entsteht");
        assertTrue(hand.contains("schild.update(true, false)"),
                "das Schild braucht update(force, applyPhysics=false) - sonst steht der"
                        + " Text nicht in der Welt, oder die Physik laesst es fallen");
        // BEIDE Haelften, und zwar als EIGENE Blockdaten: setBlockData kopiert nicht
        // zwingend, und eine geteilte Fassung haette am Ende zwei obere Haelften.
        assertTrue(hand.contains("Bisected.Half.BOTTOM")
                        && hand.contains("Bisected.Half.TOP"),
                "eine halbe Tuer ist nichts, was Minecraft stehen laesst");

        // DIE SCHEIBE WIRD NACH BEIDEN SEITEN VERBUNDEN: Eine Scheibe, die nur eine
        // Seite gesetzt bekommt, endet mitten in der Fuge - und die Mitschrift in
        // AltfurtaufbauTest sieht nur den RUF, nicht die zwei Seiten darin.
        assertTrue(hand.contains("scheibe.setFace(blockseite(achse), true)")
                        && hand.contains("scheibe.setFace(blockseite("
                                + "Bauplan.gegenrichtung(achse)), true)"),
                "beide Seiten der Wandachse muessen verbunden werden - sonst steht die"
                        + " Glasscheibe als halber Pfosten im Wandfeld");
        assertTrue(hand.contains("Bauplan.fensterachse(bau, x, y, z)")
                        && hand.contains("fein.setzeFensterscheibe(x, y, z, achse,"
                                + " block)"),
                "die Scheibennaht muss beim Setzen gerufen werden - sonst ist sie gebaut,"
                        + " gruen geprueft und wirkungslos");
        assertTrue(hand.contains("Bauplan.balkenachse(bau, x, y, z)")
                        && hand.contains("fein.setzeBalkenachse(x, y, z, liegend,"
                                + " block)"),
                "und die Balkennaht ebenso - ein liegender Balken mit axis=y sieht aus"
                        + " wie ein gestapelter Pfosten");

        // UND DER SCHILDTEXT KOMMT AUS DER SATZFAMILIE DER STATION und nicht aus einer
        // fuenften: Die Bauten tragen dieselben Namen wie die Stationen.
        assertTrue(plugin.contains("new Stationssatz(bau.schluessel(),"),
                "der Schildtext muss der Stationsname sein - zwei Satzfamilien fuer"
                        + " denselben Namen waeren zwei Stellen, die auseinanderlaufen");
    }

    @Test
    @DisplayName("31do - die Hilfe des Geldzweigs nennt das Buch der Waehrung")
    void geldhilfeNenntDasRichtigeBuch() {
        // DER WORTLAUT IST VON AUSSEN NICHT PRUEFBAR: Die Hilfezeile entsteht in einer
        // anonymen Klasse in FreimarkenPlugin.geldzweig(), und die Attrappe in
        // FreimarkenBefehlTest 44k schreibt nur "geld-hilfe:MARK" mit. Eine Zeile, die
        // das falsche Buch nennt, bleibt dort gruen - und genau so stand sie am
        // 23.11.2026 da: fuer BEIDE Waehrungen "/mark verlauf", obwohl dort nur
        // Markzeilen ankommen.
        String plugin = plugintext();
        assertTrue(plugin.contains("waehrung.verlaufsbefehl()"),
                "die Hilfezeile muss den Verlaufsbefehl der Waehrung lesen");
        assertFalse(plugin.contains("steht danach in /mark verlauf"),
                "die Hilfezeile schreibt den Befehl ab, statt ihn zu lesen - dann nennt"
                        + " sie fuer Goldmark das Markbuch");

        // UND DIE STARTZEILE NENNT BEIDE QUELLEN. Sie sagte bis zum 23.11.2026
        // ausdruecklich "allein durch Tausch", waehrend dieselbe Runde die zweite Quelle
        // gebaut hat. Wer sie liest, laesst einen Spieler 40.000 Mark tauschen, statt ihm
        // 40 Goldmark gutzuschreiben - der Rueckweg kostet ihn zwanzig Prozent.
        assertFalse(plugin.contains("allein durch Tausch"),
                "die Startzeile nennt den Tausch als EINZIGE Quelle der Goldmark, es"
                        + " gibt seit dem 23.11.2026 zwei");
        assertTrue(plugin.contains("Zu bekommen ist sie durch Tausch aus Mark"),
                "die Startzeile muss den Tausch nennen");
        assertTrue(plugin.contains("(/freimarken goldgeld)"),
                "die Startzeile muss die Buchung von Hand als zweite Quelle nennen");

        // Und das Recht, an dem beide Waehrungen haengen, beschreibt sich als beides.
        String rechte = ausgelieferte("plugin.yml");
        assertTrue(rechte.contains("Mark ODER Goldmark"),
                "die description von freimarken.geld nennt nur Mark - wer das Recht"
                        + " verteilt, liest sie, und eine Goldmark ist achthundert Mark");
    }

    @Test
    @DisplayName("31de - die Menuewache wird angemeldet")
    void menuewacheIstVerdrahtet() {
        // DIE EINE ZEILE, AN DER DAS GANZE MENUEGERUEST HAENGT [21.11.2026]. Faellt sie
        // bei einem Umbau weg, bleiben MenuewacheTest, MenuetafelTest und
        // MenueBefehlTest alle gruen - und im Spiel ist /menu eine gewoehnliche Truhe:
        // Der Spieler nimmt Grasblock, Goldbarren, Smaragd und Spitzhacke heraus und
        // behaelt sie, und kein Klick fuehrt mehr einen Befehl aus. Dieselbe Lage wie
        // bei der Truhenwache (31an), beim Shopbuch (31au) und bei der Rangwache
        // (31cr): gebaut, gruen geprueft, nie angemeldet.
        assertTrue(plugintext().contains("registerEvents(new Menuewache("),
                "die Menuewache wird nicht angemeldet - dann ist jeder Gegenstand im"
                        + " Menue eine Ware, und ein Hilfetext laesst sich als Diamant"
                        + " herausnehmen");
    }

    /**
     * Die Farbwoerter, die in einem Kommentar der Sprachdatei nichts mehr zu suchen
     * haben - jedes als ganzes Wort.
     *
     * <p>Die eindeutigen stehen ganz da ({@code aqua}, {@code gelb}, {@code grau},
     * {@code gold}). {@code rot} und {@code weiss} sind zweideutig und stehen nur in
     * einer FARBWENDUNG: bei {@code rot} auch "rot der Kopf", bei {@code weiss} NICHT -
     * "weiss der Spieler nicht, wie weit er ist" ist das Verb, und davon gibt es vier
     * Stellen. Auch "keine Probe wuerde rot" meint keine Farbe. Ein Muster, das beide
     * Woerter blind trifft, waere an harmlosen Stellen rot - und das erste, was jemand
     * dann tut, ist, Zeilen auszunehmen; eine ausgenommene Zeile ist fuer immer blind.
     */
    private static final java.util.regex.Pattern FARBWORT_IM_KOMMENTAR =
            java.util.regex.Pattern.compile(
                    "\\b(aqua|cyan|gelb|gelbe[nmrs]?|grau|graue[nmrs]?|gold|golden"
                            + "|goldene[nmrs]?|gruen|gruene[nmrs]?|blau|blaue[nmrs]?"
                            + "|rote[nmrs]?)\\b"
                            + "|\\b(in|nicht)\\s+(rot|weiss)\\b"
                            + "|\\brot\\s+(der|die|das)\\b"
                            + "|\\b(fast|in|nicht)\\s+weiss\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("31df - kein Kommentar der Sprachdatei nennt mehr eine feste Farbe")
    void keinKommentarNenntEineFesteFarbe() {
        // DIE AUSGELIEFERTE DATEI IST DAS EINZIGE HANDBUCH, das der Betreiber hat - sie
        // wird bei jedem Start neu geschrieben. Ihr Kopf sagt zu: "Ein Satz nennt die
        // Rolle und nie einen Farbwert." Die SAETZE hielten das vom ersten Tag an; die
        // KOMMENTARE daneben nicht, und sie benutzten genau die Woerter, die jetzt
        // ROLLENNAMEN sind: "der Hinweis grau" stand fuer <fliesstext>, "der Kopf rot"
        // fuer <warnung>. Wer das liest, aendert folgerichtig die falsche Rolle - und an
        // seiner Zeile aendert sich nichts.
        //
        // ERST HINTER DEM BLOCK farben: Dort sind die Farbnamen Gegenstand der
        // Erklaerung ("Markgold, aufgehellt" sagt, WOHER der Wert kommt), und genauso im
        // Kopfabsatz, der die Vanillanamen gegen die Rollen stellt.
        List<String> zeilen = List.of(ausgelieferte("sprache-vorlage.yml").split("\n", -1));
        int abZeile = 0;
        for (int stelle = 0; stelle < zeilen.size(); stelle++) {
            if (zeilen.get(stelle).startsWith("  gedaempft:")) {
                abZeile = stelle + 1;
                break;
            }
        }
        assertTrue(abZeile > 0, "der Block farben: endet nicht mehr mit gedaempft -"
                + " dann weiss diese Tafel nicht, wo die Ausnahme aufhoert");

        // DER TREFFERTEXT und keine Zahl: Eine Zahl liesse sich hochdrehen, und wer den
        // Fund behebt, braucht die ZEILE.
        List<String> gefunden = new ArrayList<>();
        for (int stelle = abZeile; stelle < zeilen.size(); stelle++) {
            String zeile = zeilen.get(stelle);
            if (!zeile.strip().startsWith("#")) {
                continue;
            }
            var sucher = FARBWORT_IM_KOMMENTAR.matcher(zeile);
            if (sucher.find()) {
                gefunden.add((stelle + 1) + ": <" + sucher.group() + "> " + zeile.strip());
            }
        }
        assertEquals(List.of(), gefunden, "Ein Kommentar der ausgelieferten Sprachdatei"
                + " nennt eine feste Farbe. Nenne die ROLLE, die der Satz darunter"
                + " traegt (fliesstext, betont, kopf, geld, warnung, bestaetigung,"
                + " hinweis, gedaempft) - sonst aendert der Betreiber die falsche Zeile"
                + " und wundert sich, dass nichts geschieht.");
    }

    @Test
    @DisplayName("31cr - die Rangwache wird gebaut UND angemeldet")
    void rangwacheIstVerdrahtet() {
        // OHNE DIESE ZEILEN IST DIE GANZE LEITER UNSICHTBAR WIRKUNGSLOS: Teil A rechnet
        // aus, wer wohin gehoert, Teil B liest sie aus der Datei, Teil C legt den
        // erreichten Rang ab - und kein Spieler merkt davon etwas. Dieselbe Lage wie bei
        // der Truhenwache (31an), beim Shopbuch (31au) und bei der Erzwache: gebaut,
        // gruen geprueft, nie angemeldet.
        String plugin = plugintext();

        assertTrue(plugin.contains("new Rangwache("),
                "die Rangwache wird nirgends gebaut - der abgelegte Rang wird dann nie"
                        + " zu einem Recht");
        assertTrue(plugin.contains("registerEvents(rangwache, this)"),
                "die Rangwache wird nicht angemeldet - dann bekommt beim Beitritt"
                        + " niemand sein Buendel, und die Leiter ist gebaut, gruen"
                        + " geprueft und ohne Wirkung");

        // UND DIE ABLAGE IN BEIDEN BETRIEBSARTEN. Fehlte sie im fluechtigen Zweig, waere
        // rangablage dort null und der erste Beitritt ein Absturz - auf genau dem
        // Server, auf dem man eine neue Fassung ausprobiert.
        assertTrue(plugin.contains("rangablage = new SpeicherRaenge()"),
                "ohne Datenbank gibt es keine Rangablage - der erste Beitritt stuerzt ab");
        assertTrue(plugin.contains("rangablage = new MariaRaenge(datenbank)"),
                "mit Datenbank gibt es keine Rangablage");

        // DIE WACHE MUSS NACH DER ABLAGE ENTSTEHEN. Beides steht in verschiedenen
        // Schritten des Starts; stuende die Wache zuerst, bekaeme sie null und meldete
        // es erst beim ersten Beitritt - und zwar als Absturz.
        //
        // Geprueft wird die STELLE der Aufrufe in onEnable und nicht die Reihenfolge der
        // Methoden im Quelltext, denn die ist hier GENAU UMGEKEHRT: richteSpielzeitEin
        // steht weiter oben in der Datei und laeuft doch spaeter. Eine Tafel ueber den
        // Dateitext waere darum rot gewesen, obwohl alles stimmt - und das ist dieselbe
        // Falle wie bei 31ai, nur andersherum. Gemessen am 19.11.2026.
        int anfang = plugin.indexOf("public void onEnable()");
        assertTrue(anfang >= 0, "onEnable fehlt in FreimarkenPlugin");
        String start = plugin.substring(anfang);
        int ablage = start.indexOf("richteAblageEin(");
        int wache = start.indexOf("richteSpielzeitEin();");
        assertTrue(ablage >= 0 && wache >= 0,
                "einer der beiden Startschritte wird in onEnable nicht gerufen - dann"
                        + " prueft diese Tafel nichts");
        assertTrue(wache > ablage,
                "richteSpielzeitEin() laeuft vor richteAblageEin() - die Rangwache"
                        + " bekaeme dann eine Ablage, die es noch nicht gibt");
    }

    @Test
    @DisplayName("31cs - was wie ein Ereignisempfaenger aussieht, IST einer")
    void jederEmpfaengerTraegtDieMarke() {
        // DIE LUECKE, DIE 31cr OFFEN LAESST: registerEvents kann durchgehen, und die
        // Methode wird trotzdem nie gerufen - es fehlt nur das @EventHandler. Bukkit
        // meldet nichts, denn eine Wache ohne Empfaenger ist erlaubt. Die Wache ist dann
        // gebaut, gruen geprueft, ANGEMELDET und wirkungslos, und das ist die Gestalt,
        // die dieses Projekt dreimal getroffen hat (31an, 31au, die Erzwache).
        //
        // Gesucht wird in JEDER Datei, die Listener umsetzt - keine Liste von Hand, die
        // bei der naechsten Wache stehen bleibt.
        var ohneMarke = new java.util.TreeSet<String>();
        var gefundene = new java.util.TreeSet<String>();
        java.util.regex.Pattern empfaenger = java.util.regex.Pattern.compile(
                "(@EventHandler[^;{}]*?)?\\s*public void ([a-zA-Z]+)\\("
                        + "\\s*([A-Za-z]+Event)\\s+[a-zA-Z]+\\s*\\)");
        for (Path datei : Quellen.alle()) {
            String text = Quellen.text(datei);
            if (!text.contains("implements Listener")) {
                continue;
            }
            java.util.regex.Matcher sucher = empfaenger.matcher(text);
            while (sucher.find()) {
                String name = datei.getFileName().toString() + "." + sucher.group(2)
                        + "(" + sucher.group(3) + ")";
                gefundene.add(name);
                if (sucher.group(1) == null) {
                    ohneMarke.add(name);
                }
            }
        }

        // ERST DIE ZAHL DER FUNDE: Ein Muster, das nichts mehr trifft, ist eine Tafel,
        // die gruen ist, ohne etwas geprueft zu haben. Die Zahl ist absichtlich eine
        // Untergrenze und keine Gleichheit - jede neue Wache soll sie erhoehen duerfen.
        assertTrue(gefundene.size() >= 20,
                "nur " + gefundene.size() + " Ereignisempfaenger gefunden (" + gefundene
                        + ") - das Muster passt offenbar nicht mehr auf den Quelltext,"
                        + " und eine Tafel, die nur einen Teil sieht, ist schlimmer als"
                        + " keine");

        assertEquals(java.util.Set.of(), ohneMarke,
                "diese Methoden nehmen ein Bukkit-Ereignis und stehen in einer Klasse,"
                        + " die Listener umsetzt - aber ohne @EventHandler ruft sie"
                        + " niemand. Angemeldet wird die Wache trotzdem ohne Klage: "
                        + ohneMarke);
    }

    @Test
    @DisplayName("31ct - die Goldkasse steht auf den GOLDmarktabellen, in beiden Betriebsarten")
    void goldkasseAufEigenenTabellen() {
        // DER SCHLIMMSTE FEHLER DIESES BAUSCHRITTES, und er ist von aussen unsichtbar:
        // Ein new MariaKasse(datenbank, 0L) OHNE Kassentabellen.GOLDMARK legte die
        // Goldmark in die MARKtabellen. Dann addieren sich zwei Waehrungen zu einem
        // Stand - und die tragende Zusage des Buchungsbuchs ("fuer ein eroeffnetes Konto
        // ist die Summe aller Buchungen sein Stand") wuerde diesen Stand BESTAETIGEN.
        // Der Server startet, der Startbericht sagt dasselbe wie sonst, jede Probe bleibt
        // gruen: MariaGoldkasseTest prueft die Kasse unmittelbar und nicht die
        // Verdrahtung. Nur diese Tafel sieht es.
        String plugin = plugintext();

        assertTrue(plugin.contains("goldkasse = new Goldkasse("),
                "die Goldkasse wird nirgends gebaut - dann gibt es die zweite Waehrung"
                        + " nicht");
        assertTrue(plugin.contains("new MariaKasse(datenbank, 0L,"
                        + " Kassentabellen.GOLDMARK)")
                        || plugin.contains("new MariaKasse(datenbank, 0L, Kassentabellen"
                                + ".GOLDMARK)"),
                "die Goldkasse bekommt keine eigenen Tabellen - dann liegt die Goldmark"
                        + " in den MARKtabellen, beide Waehrungen addieren sich zu einem"
                        + " Stand, und die Summenprobe bestaetigt ihn");

        // UND BEIDE BETRIEBSARTEN. Fehlte sie im fluechtigen Zweig, waere goldkasse dort
        // null - und der erste Zugriff ein Absturz auf genau dem Server, auf dem man eine
        // neue Fassung ausprobiert.
        assertTrue(plugin.contains("goldkasse = new Goldkasse(new SpeicherKasse(0L))"),
                "ohne Datenbank gibt es keine Goldkasse");

        // DASS DER RIEGEL UEBERHAUPT UMGELEGT IST, prueft hier NICHTS - und das ist
        // absichtlich [gemessen 19.11.2026]: Hier stand eine Zusicherung gegen
        // "goldkasse = new MariaKasse(...)". Die Einspritzung dazu kommt gar nicht bis zu
        // einer Probe, sie scheitert am UEBERSETZER: "Inkompatible Typen: MariaKasse kann
        // nicht in Goldkasse konvertiert werden". Das Feld ist vom Typ Goldkasse, und
        // damit IST der Typ der Riegel - staerker als jede Tafel.
        //
        // Die Zusicherung war also Quelltext, der nie fallen kann, und zwei Pruefungen
        // fuer denselben Fall machen sich gegenseitig unpruefbar. Sie ist herausgenommen;
        // dieser Absatz bleibt, damit niemand sie "vergessen" nachtraegt.

        // UND DIE STAEDTEKASSE BLEIBT, WO SIE IST. Sie liegt ABSICHTLICH auf den
        // Marktabellen (siehe ihren Klassenkopf): Es ist dieselbe Waehrung, und eine
        // Stadtkennung ist nie eine Spielerkennung. Wer ihr aus Versehen die
        // Goldmarktabellen gibt, verschiebt jedes Stadtguthaben in eine andere Waehrung.
        assertTrue(plugin.contains("new Stadtkasse(new MariaKasse(datenbank, 0L))"),
                "die Staedtekasse liegt nicht mehr auf den Marktabellen - sie SOLL dort"
                        + " liegen, es ist dieselbe Waehrung");
    }

    @Test
    @DisplayName("31cu - die Wechselstube haengt an derselben Markkasse, die /mark zeigt")
    void wechselstubeAnDerRichtigenKasse() {
        // DER SCHLIMMSTE FEHLER DIESES BAUSCHRITTES, und er ist von aussen unsichtbar:
        // Die Wechselstube nimmt eine Kasse und eine Goldkasse. Die Goldkasse kann der
        // Uebersetzer nicht verwechseln - sie hat ihren eigenen Typ, das war die
        // Entscheidung aus Teil E. Die MARKKASSE aber ist einfach eine Kasse, und es
        // gibt mehrere im Plugin: die der Spieler (buch.kasse()) und die der Staedte.
        // Eine falsche hier hiesse, dass /mark tauschen von einem anderen Konto abbucht
        // als /mark anzeigt - der Spieler sieht sein Guthaben unveraendert und hat
        // trotzdem gezahlt. Jede Probe bleibt dabei gruen: Das Wechselbuch tut genau,
        // was man ihm sagt.
        String plugin = plugintext();

        assertTrue(plugin.contains("new Wechselbuch(buch.kasse(), goldkasse, wirtschaft,"),
                "die Wechselstube wird nicht mit der Spielerkasse, der Goldkasse und dem"
                        + " GETEILTEN Wirtschaftsstand gebaut - und jede dieser drei"
                        + " Verwechslungen ist lautlos: eine andere Kasse bucht vom"
                        + " falschen Konto, ein eigener Wirtschaftsstand macht das"
                        + " Neuladen der Kurse stumm");

        // UND SIE WIRD UEBERGEBEN. Das Argument ist Pflicht, aber ein null dort
        // uebersetzt - und dann stuerzt der erste /mark tauschen ab, auf dem Server und
        // nicht in einer Probe.
        // BEIDE Uebergaben in EINER Zusicherung [Teil G]: Mit dem Muenzhaus und der Rucksacknaht als achtem und neuntem
        // Argument heisst die Zeile "wechselbuch, muenzhaus);". Wer hier nur noch
        // "muenzhaus);" naegelte, striche damit die Zusage ueber die Wechselstube - und
        // genau sie ist der Fall, der beim ersten /mark tauschen abstuerzt.
        assertTrue(plugin.contains("wechselbuch, muenzhaus, Vorratshand::fuer);"),
                "Wechselstube, Muenzhaus oder die Rucksacknaht werden gebaut und nicht an"
                        + " die Befehle gegeben - dann gibt es sie, und niemand kann sie"
                        + " benutzen");

        // UND DIE EINE UHR DES PLUGINS. Mit einer zweiten Uhr liefe die Tagesgrenze des
        // Wechseldeckels gegen eine andere Zeit als jede andere Tagesrechnung - und der
        // Unterschied ist in der Nacht am groessten, wenn niemand hinsieht.
        var zweiteUhr = java.util.regex.Pattern
                .compile("new Wechselbuch\\([^)]*Clock\\.")
                .matcher(plugin);
        assertFalse(zweiteUhr.find(),
                "die Wechselstube baut sich eine eigene Uhr - es gibt genau eine im"
                        + " Plugin, und die Tagesgrenze haengt an ihr");
    }

    @Test
    @DisplayName("31cv - das Muenzhaus steht auf derselben Goldkasse und misst an EINER"
            + " Stelle")
    void muenzhausVerdrahtet() {
        // DREI VERDRAHTUNGEN, und jede einzelne ist von aussen unsichtbar:
        //
        // 1. DIE GOLDKASSE. Eine zweite waere ein zweites Konto in derselben Waehrung -
        //    ein Spieler tauschte Goldmark ein und faende sie im Muenzhaus nicht wieder.
        //    Jede Probe bliebe gruen: Beide Kassen tun genau, was man ihnen sagt.
        // 2. DER WIRTSCHAFTSSTAND. Ein eigener machte das Neuladen des Katalogs stumm -
        //    derselbe Fund, den Teil F fuer die Wechselstube aufgeschrieben hat.
        // 3. DIE MESSTUER. Baut sich das Muenzhaus seine eigene Messung, urteilt ein Kauf
        //    eines Tages anders als eine Befoerderung: dieselbe Bedingung, dieselbe Zahl
        //    in der Datei, zwei verschiedene Antworten.
        String plugin = plugintext();

        assertTrue(plugin.contains("new Muenzhaus(goldkasse, wirtschaft,"),
                "das Muenzhaus steht nicht auf der Goldkasse und dem geteilten"
                        + " Wirtschaftsstand des Plugins");
        var eigeneKasse = java.util.regex.Pattern
                .compile("new Muenzhaus\\([^)]*new Goldkasse")
                .matcher(plugin);
        assertFalse(eigeneKasse.find(),
                "das Muenzhaus baut sich eine eigene Goldkasse - dann gibt es zwei"
                        + " Konten in derselben Waehrung");
        // UEBER DIE BAUZEILE und nicht als freistehende Zeile - nachgezogen am
        // 20.11.2026: Vorher waren die drei Zusicherungen voneinander unabhaengig. Wer
        // das Lambda auf "(wer, gebraucht) -> java.util.Map.of()" setzte und irgendwo
        // sonst in der Datei ein aufstiegslauf.messeFuer(wer, gebraucht) stehen liess,
        // blieb gruen - und im Spiel waere dann jeder Posten mit Bedingung dauerhaft
        // gesperrt, auch fuer den, der sie erfuellt: Eine leere Messkarte heisst
        // "gilt nicht". 31cu macht es an derselben Stelle schon richtig.
        var messtuer = java.util.regex.Pattern
                .compile("new Muenzhaus\\([^;]*messeFuer\\(wer, gebraucht\\)")
                .matcher(plugin);
        assertTrue(messtuer.find(),
                "das Muenzhaus misst seine Bedingungen nicht ueber den Aufstiegslauf -"
                        + " und das ist die EINE Stelle, die messen darf");

        // UND DIE MESSTUER GIBT ES GENAU EINMAL. Ein zweiter Messer waere genau der
        // Fall, den Punkt 3 oben beschreibt - und er stuende dann in einer anderen
        // Datei, wo niemand ihn neben diesen legt.
        int messer = 0;
        for (var datei : Quellen.alleDateien()) {
            String text = Quellen.text(datei);
            // GESCHAERFT und nicht ausgenommen: Das blosse Vorkommen der Faelle
            // traf auch MarkSatz.bedingungssatz, das je Art einen SATZ waehlt. Ein
            // MESSER liest die Zahl - er fragt die Kasse nach dem Guthaben.
            if (text.contains("case GUTHABEN -> kasse.guthaben(wer)")) {
                messer++;
            }
        }
        assertEquals(1, messer, "die Bedingungen werden an " + messer + " Stellen"
                + " gemessen - erlaubt ist genau eine (Aufstiegslauf.messe)");
    }

    /**
     * Die Pfade, die in {@code Wirtschaftsstand.uebernimm} mit ABSICHT nicht genannt
     * werden - mit dem Grund je Gruppe.
     *
     * <p><b>Warum es diese Liste gibt</b> [20.11.2026, Teil G]: Dieselbe Luecke ist in
     * diesem Projekt dreimal aufgefallen - die zwei Auflagenschalter (17.11.), die
     * Rangleiter (19.11.) und beim Bau des Muenzhauses ein ganzer Satz weiterer Pfade.
     * Das Fehlerbild ist jedes Mal dasselbe: Der Wert GILT nach dem Neuladen, und der
     * Befehl sagt "nichts geaendert". Keine Verhaltensprobe kann das fangen.
     *
     * <p>Ab jetzt faengt es diese Tafel, und sie prueft in BEIDE Richtungen: Ein neuer
     * Einstellwert ohne Zeile ist rot, und eine Zeile, die nachgetragen wurde, muss hier
     * heraus.
     */
    private static final Set<String> OHNE_NEULADEZEILE = Set.of(
            // Das BLOCKBUCH ist kein Bestandteil der Wirtschaftswerte - es hat seine
            // eigenen Werte und wird beim Start in seine Wache gegeben.
            "Blockbuchwerte.PFAD_BENUTZBAR",
            "Blockbuchwerte.PFAD_FRIST", "Blockbuchwerte.PFAD_HAPPEN",
            "Blockbuchwerte.PFAD_REICHWEITE", "Blockbuchwerte.PFAD_TATEN",
            "Blockbuchwerte.PFAD_WELTEN", "Blockbuchwerte.PFAD_ZEILEN",
            // Die GRENZEN DES RUECKWEGS stehen daneben und werden mit demselben
            // Leser geholt - also auch nur beim Start. Sie gehoeren hierher aus
            // genau demselben Grund, und die Berichtszeile des Lesers sagt den
            // Neustart ausdruecklich.
            "Rueckweggrenzen.PFAD_RADIUS", "Rueckweggrenzen.PFAD_MINUTEN",
            "Rueckweggrenzen.PFAD_BLOECKE",
            // Die STELLEN der Freien Truhen sind kein Wert, sondern eine Liste von
            // Orten: Sie werden beim Start gelesen und in die Wache gegeben.
            "Truhenverzeichnis.PFAD_TRUHEN",
            // Die BERUFSLISTE steckt in der Befehlsvervollstaendigung und wird darum nur
            // beim Start gelesen; der Leser gibt dafuer ausdruecklich keinen Bericht.
            "Berufswerte.PFAD_LISTE",
            // Die WURZEL der Sprungbestaetigung ist kein Wert, sondern der Ort, an dem
            // Bestaetigungen stehen.
            "Wirtschaftswerte.PFAD_BESTAETIGT",
            // Die zwei Unterschluessel einer ERZZEILE stehen in der Tabelle, und die
            // wird als Ganzes verglichen (Erzwerte.PFAD_TABELLE, nenneTabelle): Eine
            // geaenderte Zeile faellt dort auf, weil Erzzeile ein record ist und equals
            // beide Zahlen vergleicht. Eine eigene Zeile je Erz waere eine Wand.
            "Erzwerte.SCHLUESSEL_GEWICHT", "Erzwerte.SCHLUESSEL_AB_STUFE");

    @Test
    @DisplayName("31cw - jeder Einstellwert des Kerns wird beim Neuladen GENANNT")
    void jederEinstellwertWirdGenannt() {
        // PFAD_ UND SCHLUESSEL_ - geweitet am 20.11.2026, Befund der Pruefrunde: Ein
        // Listenwert hat seinen Pfad (muenzhaus.katalog) und darunter je Eintrag
        // UNTERSCHLUESSEL (gegenstaende, preis, bedingungen). Die heissen SCHLUESSEL_*,
        // standen darum ausserhalb dieser Tafel - und fuer drei von ihnen war das
        // Fehlerbild, gegen das die Tafel gebaut ist, genau hier schon da: Wer
        // rang.leiter.buerger.zahlen oder muenzhaus.katalog.blumenkiste.bedingungen
        // aenderte, las "nichts geaendert", und der neue Wert galt.
        var muster = java.util.regex.Pattern
                .compile("public static final String ((?:PFAD|SCHLUESSEL)_[A-Z_]+)\\s*=");
        String stand = null;
        var alle = new TreeSet<String>();
        for (var datei : Quellen.alleDateien()) {
            String name = datei.getFileName().toString();
            if (!name.endsWith(".java")) {
                continue;
            }
            String klasse = name.substring(0, name.length() - ".java".length());
            String text = Quellen.mitKommentaren(datei);
            if (klasse.equals("Wirtschaftsstand")) {
                // OHNE KOMMENTARE, und das ist ein eigener Befund vom 20.11.2026: Mit
                // ihnen genuegte die Zeile "// Muenzwerte.PFAD_AN bleibt absichtlich
                // ungenannt", um gruen zu sein - ohne eine einzige Berichtszeile. Genau
                // dieser Fehler ist in diesem Projekt schon gemessen worden und der
                // Grund, warum es Quellen.text ueberhaupt gibt (siehe dort:
                // "jederSchluesselWirdGesendet nahm eine Nennung im Javadoc als
                // Benutzung").
                stand = Quellen.text(datei);
            }
            var suche = muster.matcher(text);
            while (suche.find()) {
                alle.add(klasse + "." + suche.group(1));
            }
        }
        assertTrue(stand != null, "Wirtschaftsstand.java nicht gefunden");
        assertTrue(alle.size() > 80, "nur " + alle.size() + " Einstellpfade gefunden -"
                + " die Suche laeuft leer, und dann ist diese Tafel blind");

        var ohneZeile = new TreeSet<String>();
        for (String pfad : alle) {
            if (!stand.contains(pfad)) {
                ohneZeile.add(pfad);
            }
        }

        assertEquals(new TreeSet<>(OHNE_NEULADEZEILE), ohneZeile,
                "Ein Einstellwert ohne Zeile in uebernimm wechselt beim Neuladen STUMM:"
                        + " Er gilt, und der Befehl sagt \"nichts geaendert\". Wer einen"
                        + " neuen Wert anlegt, traegt seine Zeile nach - und wer eine"
                        + " nachgetragen hat, nimmt ihn aus OHNE_NEULADEZEILE heraus.");
    }

    @Test
    @DisplayName("31cz - jeder Weltname der Leser steht in der Liste der nicht"
            + " neuladbaren")
    void jederWeltnameStehtInDerListe() {
        // FUND VOM 20.11.2026: Die vier Weltnamen sind nicht neuladbar - die laufenden
        // Namen werden ausschliesslich beim Start zugewiesen -, standen aber in keiner
        // Liste. Fuer eine fehlende Zeile schrieb der Leser darum "es gilt wolkenwerder",
        // eine Geltung, die erst der naechste Start herstellt.
        //
        // Die Pfadkonstanten sind PAKETPRIVAT und liegen je bei ihrem Leser: Ein Weltname
        // ist kein Wirtschaftswert, der Kern kennt ihn nicht. Diese Tafel ist damit die
        // einzige Stelle, die beide Seiten gegeneinander haelt - 31cw sieht sie nicht,
        // denn ihr Muster verlangt public.
        var muster = Pattern.compile(
                "static final String PFAD_WELT\\s*=\\s*\"([a-z][a-z.]*)\"");
        var gefunden = new TreeSet<String>();
        for (var datei : Quellen.alleDateien()) {
            if (!datei.getFileName().toString().endsWith(".java")) {
                continue;
            }
            var suche = muster.matcher(Quellen.text(datei));
            while (suche.find()) {
                gefunden.add(suche.group(1));
            }
        }
        assertTrue(gefunden.size() > 2,
                "nur " + gefunden.size() + " Weltschluessel gefunden - dann laeuft die"
                        + " Suche leer und diese Tafel ist blind: " + gefunden);

        assertEquals(gefunden, new TreeSet<>(Wirtschaftsliste.NUR_BEIM_START_WELTEN),
                "Links die Weltnamen, die ein Leser aus der Datei holt, rechts die Liste,"
                        + " die fehltText und die Sammelzeile fragen. Fehlt einer rechts,"
                        + " behauptet der Bericht fuer ihn eine Geltung, die ein"
                        + " Neuladen nicht herstellt; steht einer rechts zu viel,"
                        + " verschweigt der Bericht eine Geltung, die stimmt.");
    }

    @Test
    @DisplayName("31da - jeder nicht neuladbare Schluessel traegt den Vermerk DIREKT"
            + " ueber sich")
    void nichtNeuladbarerSchluesselTraegtDenVermerk() {
        // FUND VOM 20.11.2026: laeden.welt war der einzige der vier Weltnamen ohne den
        // Vermerk - nicht, weil er fehlte, sondern weil er VERWAIST war. Der Kopfblock der
        // Markthalle stand ohne Leerzeile direkt ueber "freistaedte:", und damit gehoerte
        // er in YAML den Freistaedten. Das ist die YAML-Fassung der Falle, die 31ak fuer
        // Javadoc haelt: Dort reisst ein eingeschobenes Feld die Doku des Nachbarn ab,
        // hier verschiebt ein umgezogener Abschnitt die Doku zum Nachbarn.
        //
        // Geprueft wird der Kommentarblock DIREKT ueber dem Schluessel. Ein Vermerk
        // irgendwo in der Datei genuegt ausdruecklich nicht: Der Betreiber liest die
        // Zeilen ueber seiner Zeile, und sonst keine.
        String[] zeilen = ausgelieferte("config.yml").split("\n", -1);
        var zuZeile = new java.util.LinkedHashMap<String, Integer>();
        var namen = new java.util.ArrayList<String>();
        var tiefen = new java.util.ArrayList<Integer>();
        for (int i = 0; i < zeilen.length; i++) {
            String ohne = zeilen[i].stripLeading();
            if (ohne.isEmpty() || ohne.startsWith("#") || ohne.startsWith("- ")) {
                continue;
            }
            int doppel = ohne.indexOf(':');
            if (doppel <= 0) {
                continue;
            }
            String name = ohne.substring(0, doppel);
            if (name.contains(" ") || name.contains("\"")) {
                continue;
            }
            int tiefe = zeilen[i].length() - ohne.length();
            while (!tiefen.isEmpty() && tiefen.get(tiefen.size() - 1) >= tiefe) {
                tiefen.remove(tiefen.size() - 1);
                namen.remove(namen.size() - 1);
            }
            namen.add(name);
            tiefen.add(tiefe);
            zuZeile.put(String.join(".", namen), i);
        }
        assertTrue(zuZeile.size() > 200,
                "nur " + zuZeile.size() + " Schluessel in der ausgelieferten config.yml"
                        + " gefunden - dann liest diese Tafel die Datei nicht richtig");

        var ohneVermerk = new TreeSet<String>();
        var nichtNeuladbar = new java.util.ArrayList<String>(
                Wirtschaftsstand.NUR_BEIM_START);
        nichtNeuladbar.addAll(Wirtschaftsliste.NUR_BEIM_START_WELTEN);
        for (String pfad : nichtNeuladbar) {
            Integer zeile = zuZeile.get(pfad);
            assertTrue(zeile != null, pfad + " steht nicht in der ausgelieferten"
                    + " config.yml - dann ist er fuer den Betreiber unsichtbar");
            var block = new StringBuilder();
            for (int i = zeile - 1; i >= 0; i--) {
                String ohne = zeilen[i].stripLeading();
                if (!ohne.startsWith("#")) {
                    break;
                }
                block.append(ohne).append('\n');
            }
            if (!block.toString().contains("NUR BEIM START GELESEN")) {
                ohneVermerk.add(pfad);
            }
        }

        assertEquals(new TreeSet<String>(), ohneVermerk,
                "Diese Schluessel uebernimmt ein Neuladen nicht, und der Kommentarblock"
                        + " direkt ueber ihnen sagt es nicht. Wer sie im Betrieb aendert,"
                        + " liest \"uebernommen\" und bekommt den alten Wert: "
                        + ohneVermerk);
    }

    @Test
    @DisplayName("31cx - die Platzrechnung fragt nach GESTALT und nach GEGENSTAND")
    void platzrechnungFragtGestaltUndGegenstand() {
        // ZWEI BEFUNDE DER PRUEFRUNDE ZU TEIL G (20.11.2026), und beide haben dieselbe
        // Wurzel: Die Rechnung vor der Zahlung war OPTIMISTISCHER als Bukkits addItem.
        // Was sie zu viel verspricht, kostet echte Goldmark - abgebucht, halb geliefert,
        // der ganze Preis erstattet, und das Gelieferte bleibt beim Spieler.
        //
        // 1. DIE GESTALT. addItem fuellt angefangene Stapel nur bei AEHNLICHKEIT auf
        //    (firstPartial fragt isSimilar); die Rechnung verglich nur die ART. Ein im
        //    Amboss umbenannter Stapel war fuer sie ein Aufstockziel und fuer Bukkit
        //    keines.
        // 2. DER GEGENSTAND. "potatoes" ist der Feldblock, "potato" der Gegenstand. Der
        //    Leser der config.yml kann das nicht fragen - isItem wirft ohne Server -,
        //    also faellt die Frage an dieser Naht.
        //
        // Beides ist eine Quelltextzusage, weil Vorratshand einen laufenden Server
        // braucht: Die RECHNUNG selbst prueft VorratshandTest, aber was aus Bukkit
        // hereingeholt wird, sieht nur diese Tafel.
        String hand = Quellen.text(Quellen.alleDateien().stream()
                .filter(datei -> datei.getFileName().toString().equals("Vorratshand.java"))
                .findFirst().orElseThrow());

        assertTrue(hand.contains("aufstockbar[platz] = !plaetze[platz].hasItemMeta();"),
                "die Platzrechnung merkt sich nicht, welcher Stapel eine eigene Gestalt"
                        + " hat - dann rechnet sie einen umbenannten Stapel voll, in den"
                        + " addItem nichts legt");
        assertTrue(hand.contains("art == null || !art.isItem()"),
                "die Naht laesst ein reines BLOCKmaterial durch - ein Posten darauf"
                        + " bucht ab und liefert nichts");
    }

    @Test
    @DisplayName("31ea - die Klage ueber einen Sperrer im Kreuzarm ist verdrahtet")
    void sperrerImKreuzarmWirdGeklagt() {
        // DIE GESTALT, GEGEN DIE DIESE TAFEL GEBAUT IST [24.11.2026]: Ein Bau, der
        // einen Kreuzarm beruehrt, ohne ihn ganz zu ueberspannen, SPERRT den Weg von
        // der Marktmitte zum Tor - und zwar unsichtbar fuer jede Probe: Stadtplan und
        // Bauplan rechnen beide richtig, nur die Stadt ist zu. Gemessen in der Welt am
        // 24.11.2026: Zunfthaus und Kaufmannskontor reichten bis z 3, der Arm braucht
        // bis z 4. EIN Block, und der Ost- und der Westarm waren versperrt.
        //
        // Die Meldung ist der einzige Weg, auf dem der Betreiber davon erfaehrt. Eine
        // gebaute, gruen geprueefte Meldung ohne Aufruf ist unsichtbar wirkungslos -
        // dieselbe Gestalt wie bei 31an, 31au, 31cr, 31de und 31dp.
        String plugin = plugintext();
        assertTrue(plugin.contains("private void meldeSperrerImKreuzarm()"),
                "die Meldung ist nicht gebaut");
        assertTrue(plugin.contains("meldeSperrerImKreuzarm();"),
                "die Meldung wird nicht gerufen - dann bleibt ein versperrter Weg"
                        + " stumm, und alle Proben bleiben gruen");
        // SIE FRAGT BEIDE SEITEN: Ob der Bau den Arm beruehrt UND ob er ihn ueberspannt.
        // Ohne die zweite Frage klagte sie auch ueber Kirche und Rathaus, die ihn zu
        // Recht ueberspannen und dafuer eine Laube bekommen.
        assertTrue(plugin.contains("Stadtplan.imKreuzarm("),
                "sie fragt nicht, ob der Bau den Arm beruehrt");
        assertTrue(plugin.contains("Bauplan.ueberspanntKreuzarm(bau)"),
                "sie fragt nicht, ob er ihn ueberspannt - dann klagt sie auch ueber die"
                        + " Stadtkirche und das Bundesratshaus");
        // UND SIE IST EINE KLAGE, kein Hinweis: Ein versperrter Weg zum Tor ist ein
        // Fehler in der Stadt und gehoert nach severe.
        int stelle = plugin.indexOf("private void meldeSperrerImKreuzarm()");
        int ende = plugin.indexOf("private void meldeSpawnImHaus", stelle);
        assertTrue(ende > stelle, "die Meldung steht vor meldeSpawnImHaus");
        assertTrue(plugin.substring(stelle, ende).contains("getLogger().severe("),
                "ein versperrter Weg zum Tor gehoert nach severe, nicht nach info");
    }

    @Test
    @DisplayName("31eb - die drei Ereigniszweige der Strafwache sind verdrahtet")
    void strafwacheIstVerdrahtet() {
        // DIE GESTALT, GEGEN DIE DIESE TAFEL GEBAUT IST [25.11.2026, Teil B]: Die drei
        // Entscheidungen der Strafwache stehen in eigenen Methoden, weil zwei der drei
        // Bukkit-Ereignisse sich ohne laufenden Server nicht bauen lassen (gemessen:
        // AsyncPlayerPreLoginEvent braucht Server.createProfile,
        // PlayerCommandPreprocessEvent braucht Player.getServer). StrafwacheTest
        // prueft die Methoden - und genau darum kann der EREIGNISZWEIG stumm leer
        // werden, ohne dass eine Probe rot wird.
        //
        // Dasselbe Fehlerbild wie bei 31an, 31au, 31cr, 31de, 31dp und 31ea: eine
        // gebaute, gruen gepruefte Regel ohne Anschluss. Hier waere die Folge, dass
        // jeder gebannte Spieler hereinkommt und jeder Stummgeschaltete weiterschreibt.
        String wache = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Strafwache.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Strafwache.java fehlt")));

        // Je Ereignis: der Zuhoerer UND der Aufruf seiner Entscheidung.
        for (String[] paar : new String[][] {
                {"AsyncPlayerPreLoginEvent ereignis", "abweisungFuer("},
                {"AsyncChatEvent ereignis", "absageFuerChat("},
                {"PlayerCommandPreprocessEvent ereignis", "absageFuerBefehl("}}) {
            assertTrue(wache.contains(paar[0]),
                    "der Zuhoerer fuer " + paar[0] + " fehlt - dann wirkt diese"
                            + " Strafart nicht");
            assertTrue(wache.contains(paar[1]),
                    paar[1] + " wird nicht gerufen - die Entscheidung ist gebaut und"
                            + " gruen geprueft, und der Zweig tut nichts");
        }

        // UND SIE GREIFEN EIN: Ein Zuhoerer ohne disallow beziehungsweise
        // setCancelled liest die Strafe nachweislich und laesst den Spieler trotzdem
        // durch - das ist die Gestalt, die eine Methodenprobe niemals sieht.
        assertTrue(wache.contains("ereignis.disallow("),
                "die Tuer weist nicht ab");
        assertEquals(2, wache.split("ereignis.setCancelled\\(true\\)", -1).length - 1,
                "Chat und Befehl brechen ab - genau zweimal setCancelled(true)");

        // UND SIE HOEREN FRUEH GENUG: Auf MONITOR ist die Zeile schon unterwegs, und
        // disallow wirkt dort nicht mehr. Dieselbe Falle, aus der anderen Richtung als
        // bei der Meldewache - jene MUSS auf MONITOR hoeren, weil sie nur ausliefert.
        assertFalse(wache.contains("EventPriority.MONITOR"),
                "eine Wache, die abbricht, darf nicht auf MONITOR hoeren - dort ist"
                        + " nichts mehr zu verhindern");
    }

    @Test
    @DisplayName("31ec - die Ruecksetz-Erinnerung fragt den SCHALTER, nicht einen Weltnamen")
    void ruecksetzErinnerungFragtDenSchalter() {
        // DER FEHLER, GEGEN DEN DIESE TAFEL GEBAUT IST [gefunden 26.11.2026]: Die
        // Warnung bei veralteten Chunks nannte "Kornmark" als festes Wort. Am
        // 25.11.2026 kam die Vogtei dazu - dieselbe Gattung, derselbe Reset - und fiel
        // still durch: Der Betreiber las "Fuer Kornmark ist das Zuruecksetzen faellig"
        // und setzte die Vogtei nicht zurueck.
        //
        // Das ist mehr als eine vergessene Erinnerung. Bei der Vogtei ist der Reset die
        // BEGRUENDUNG dafuer, dass eine ablaufende Mitgliedschaft harmlos ist
        // (Plan 8.5): Dort bleibt kein Gebautes, WEIL zurueckgesetzt wird. Eine
        // Erinnerung, die sie nicht nennt, nimmt dieser Begruendung die Grundlage.
        //
        // Dieselbe Bauart wie 31eb: Die Wirkung ist eine Protokollzeile, und die sieht
        // keine Probe. Was pruefbar bleibt, ist der ANSCHLUSS.
        String plugin = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("FreimarkenPlugin.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FreimarkenPlugin.java fehlt")));
        // ZWEI FENSTER, und das ist nicht Umstand, sondern gemessen [26.11.2026]:
        // Die erste Fassung dieser Tafel suchte den Weltnamen MIT Anfuehrungszeichen
        // ("\"" + welt + "\""). Im Quelltext stehen die aber maskiert - aus
        // \"Kornmark\" wird Backslash-Anfuehrung-K..., und davor steht eben KEINE
        // nackte Anfuehrung. Eine Einspritzung, die genau diesen Fehler wieder
        // einbaute, lief an der Tafel vorbei. Jetzt wird der nackte Name gesucht -
        // und darum muss das Fenster die Kommentare AUSSCHLIESSEN, denn die
        // Begruendung oben nennt Kornmark und Vogtei zu Recht.
        int warnung = plugin.indexOf("getLogger().warning(\"Veraltete Chunks: \"");
        assertTrue(warnung > 0, "die Warnung ueber veraltete Chunks gibt es nicht mehr -"
                + " dann erfaehrt niemand von einem Versionswechsel");

        // Fenster 1: NUR die Argumente der Warnung - dort steht kein Kommentar.
        String argumente = plugin.substring(warnung,
                Math.min(plugin.length(), warnung + 900));
        for (String welt : new String[] {"Kornmark", "kornmark", "Vogtei", "vogtei"}) {
            assertTrue(!argumente.contains(welt),
                    "der Weltname " + welt + " steht im Text der Warnung - genau das"
                            + " war der Fehler vom 26.11.2026: die Vogtei fiel durch"
                            + " eine Zeile, die nur Kornmark nannte, still durch");
        }

        // Fenster 2: was VOR der Warnung steht - dort wird der Schalter gefragt.
        String davor = plugin.substring(Math.max(0, warnung - 800), warnung);
        assertTrue(davor.contains("resetJeVersion()"),
                "die Warnung fragt den Schalter nicht - dann haengt sie wieder an"
                        + " einem festen Weltnamen, und die naechste Farmwelt faellt"
                        + " still durch");
    }

    @Test
    @DisplayName("31ed - jede Tat des Blockbuchs ist in der config.yml angeschlossen")
    void jedeTatIstAngeschlossen() {
        // DER FEHLER, GEGEN DEN DIESE TAFEL GEBAUT IST [gefunden 26.11.2026, beim
        // Bauen des Rueckwegs]: Blocktat.SPRENGEN war angelegt, ihr Wort in der
        // Sprachdatei, zwei Zuhoerer gebaut, ihre Haken in BlockwacheTest gezaehlt,
        // der Rueckrollplan konnte Sprengungen zuruecknehmen - und das Blockbuch
        // schrieb sie NICHT. Die Tat stand weder in Blockbuchwerte.VORGABE_AUSWAHL
        // noch irgendwo in der ausgelieferten config.yml, und die Wache fragt vor
        // jeder Zeile werte.schreibt(...).
        //
        // Nichts bricht, nichts wird rot. Ein TNT-Grief landet in keiner Zeile, und
        // der Rueckweg ist fuer genau den Fall wirkungslos, fuer den er gebaut
        // wurde - das faellt erst dem Betreiber auf, der einen Krater vor sich hat.
        //
        // GEPRUEFT WIRD DER ANSCHLUSS und nicht die Vorgabe: Eine Tat darf in der
        // Vorgabe fehlen, wenn eine WELT sie nennt - genau so steht benutzen da, und
        // zwar mit Begruendung (es ist die haeufigste Tat und fuellt die Tabelle mit
        // Tueroeffnungen). Was NICHT sein darf, ist eine Tat, die nirgends steht:
        // Die gibt es dann im Programm, in der Sprachdatei und in der Wache, und
        // wirkt nie.
        String datei = ausgelieferte("config.yml");
        int anfang = datei.indexOf("blockbuch:");
        assertTrue(anfang >= 0, "der Abschnitt blockbuch: muss in der ausgelieferten"
                + " config.yml stehen");

        // Gelesen werden die LISTEN und nicht der ganze Abschnitt: Ein Tatname in
        // einem Kommentar waere sonst ein Anschluss, und genau das war er nicht -
        // "sprengen" stand am 26.11.2026 in mehreren Kommentaren dieser Datei.
        var listen = new java.util.ArrayList<String>();
        for (String zeile : datei.substring(anfang).split("\n", -1)) {
            String nackt = zeile.strip();
            if (nackt.startsWith("#")) {
                continue;
            }
            int klammer = nackt.indexOf('[');
            if (klammer >= 0 && nackt.endsWith("]")) {
                listen.add(nackt.substring(klammer + 1, nackt.length() - 1));
            }
            // Der Abschnitt endet an der ersten Zeile ohne Einrueckung - sonst
            // zaehlten die Listen JEDES folgenden Abschnitts mit, und die Tafel
            // waere gruen, weil irgendwo irgendeine Liste ein Wort trifft.
            if (!zeile.isBlank() && !zeile.startsWith(" ")
                    && !zeile.startsWith("blockbuch:")) {
                break;
            }
        }
        assertTrue(listen.size() >= 3,
                "nur " + listen.size() + " Listen im Abschnitt blockbuch: gelesen -"
                        + " dann prueft diese Tafel fast nichts: " + listen);

        var fehlen = new java.util.ArrayList<String>();
        for (Blocktat tat : Blocktat.values()) {
            boolean inDerVorgabe = Blockbuchwerte.VORGABE_AUSWAHL.taten()
                    .contains(tat);
            boolean inEinerListe = listen.stream().anyMatch(liste ->
                    java.util.Arrays.stream(liste.split(","))
                            .map(String::strip)
                            .anyMatch(wort -> wort.equals(tat.schluessel())));
            if (!inDerVorgabe && !inEinerListe) {
                fehlen.add(tat.schluessel());
            }
        }
        assertEquals(java.util.List.of(), fehlen,
                "Diese Taten gibt es im Programm, in der Sprachdatei und in der Wache -"
                        + " und sie wirken NIE: Sie stehen weder in"
                        + " Blockbuchwerte.VORGABE_AUSWAHL noch in einer Liste des"
                        + " Abschnitts blockbuch: der ausgelieferten config.yml. Wer eine"
                        + " neue Tat baut, traegt sie in die Vorgabe ein (wenn sie"
                        + " ueberall gelten soll) oder bei der Welt, in der ihre Frage"
                        + " gestellt wird. Ein Kommentar zaehlt nicht: Diese Tafel liest"
                        + " nur die Listen.");
    }

    @Test
    @DisplayName("31ee - die Weltvorschlaege des Rueckwegs sind KURZ wie die Befehlszeile")
    void weltvorschlaegeSindKurz() {
        // DER FALL, DER GEGEN KEINE PROBE LIEF [Einspritzung V, 26.11.2026]: Die
        // Vorschlagsliste steht in Rueckrollung.ueberBukkit, also hinter einem
        // Aufruf, den ohne laufenden Server keine Probe erreicht - gemessen gegen
        // ALLE Paper-Proben, und sie blieben gruen.
        //
        // Was dabei kaputtgeht: Brigadiers word() kennt keinen Doppelpunkt. Gaebe
        // die Liste "freimarken:altfurt" vor, waere jeder Vorschlag unbrauchbar,
        // und die Absage bei unbekannter Welt naennte Namen, die der Befehl nicht
        // annimmt. Die Bestaetigungszeile kuerzt dagegen (Blocktext.bestaetigung,
        // durch Einspritzung S gedeckt) - zwei Stellen, die verschiedene Formen
        // nennen, und eine gibt vor, was die andere ablehnt.
        //
        // Geprueft wird ZEILENWEISE und der Treffer ist die ZEILE: Eine Zaehltafel
        // liesse sich hochdrehen, und bei einer zweiten Vorschlagsliste waere sie
        // blind.
        String quelle = Quellen.text(Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString()
                        .equals("Rueckrollung.java"))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Rueckrollung.java nicht gefunden")));
        int gepruefte = 0;
        for (String zeile : quelle.split("\n", -1)) {
            if (!zeile.contains("namen.add(")) {
                continue;
            }
            gepruefte++;
            assertTrue(zeile.contains("Blocktext.kurzeWelt("),
                    "Diese Zeile legt einen Weltnamen in die Vorschlagsliste, ohne"
                            + " ihn zu kuerzen: " + zeile.strip()
                            + " -- Brigadiers word() kennt keinen Doppelpunkt, also"
                            + " waere jeder Vorschlag mit Namensraum unbrauchbar, und"
                            + " die Absage bei unbekannter Welt naennte Namen, die der"
                            + " Befehl nicht annimmt. Blocktext.bestaetigung kuerzt;"
                            + " beide Stellen muessen dieselbe Form nennen.");
        }
        assertEquals(1, gepruefte,
                "erwartet wird GENAU EINE Zeile, die einen Weltnamen in die Liste"
                        + " legt. Keine heisst, dass diese Tafel ihren Gegenstand nicht"
                        + " mehr findet - dann prueft sie nichts. Mehr heisst, dass eine"
                        + " zweite Liste dazugekommen ist; die gehoert hier mitgezaehlt.");
    }

    @Test
    @DisplayName("31ef - jede Wache, die Listener umsetzt, ist auch ANGEMELDET")
    void jedeWacheIstAngemeldet() {
        // DIE REGEL AN EINER STELLE. Bisher hatte jede Wache ihre eigene
        // handgeschriebene Zeile - 31an (Truhenwache), 31au, 31cr (Rangwache),
        // 31de (Menuewache) und die Erzwache: fuenfmal dieselbe Fehlergestalt,
        // fuenfmal eine neue Zeile hinterher. Eine Wache, die niemand von Hand
        // eintraegt, ist damit stumm nicht angemeldet.
        //
        // 31cs haelt die Stufe darunter (jeder Empfaenger traegt @EventHandler),
        // und zusammen schliessen die zwei die Luecke: gebaut, markiert UND
        // verdrahtet. Eine Wache, die alle drei Stufen besteht, kann noch das
        // Falsche tun - aber sie wird gerufen.
        //
        // Anerkannt werden die zwei Formen, die das Plugin benutzt:
        // registerEvents(new Wache(...)) und registerEvents(wache, this) mit einer
        // Oertlichen, die genauso heisst wie ihre Klasse, nur klein. Eine dritte
        // Form macht diese Tafel ROT und nicht stumm gruen - das ist die richtige
        // Richtung: Dann sieht ein Mensch nach, ob die Wache wirklich dranhaengt.
        String plugin = plugintext();
        var ohneAnmeldung = new java.util.TreeSet<String>();
        var gefundene = new java.util.TreeSet<String>();
        for (Path datei : Quellen.alle()) {
            String text = Quellen.text(datei);
            String name = datei.getFileName().toString().replace(".java", "");
            if (!text.contains("implements Listener")
                    || name.equals("FreimarkenPlugin")) {
                continue;
            }
            gefundene.add(name);
            String klein = name.substring(0, 1).toLowerCase(java.util.Locale.ROOT)
                    + name.substring(1);
            // IM ARGUMENT VON registerEvents UND NICHT IRGENDWO: Ein blankes
            // "new Wache(" galt vorher als Nachweis - damit kaeme eine Wache
            // durch, die irgendwo gebaut und nie angemeldet wird, also genau
            // die Fehlergestalt, die diese Tafel fangen soll. Gesucht wird
            // darum je registerEvents( ein Fenster bis zum Ende des Aufrufs.
            boolean angemeldet = false;
            int stelle = plugin.indexOf("registerEvents(");
            while (stelle >= 0 && !angemeldet) {
                String fenster = plugin.substring(stelle,
                        Math.min(plugin.length(), stelle + 400));
                int schluss = fenster.indexOf(", this)");
                if (schluss > 0) {
                    fenster = fenster.substring(0, schluss);
                }
                String argument = fenster.substring("registerEvents(".length())
                        .strip();
                angemeldet = fenster.contains("new " + name + "(")
                        || argument.equals(klein);
                stelle = plugin.indexOf("registerEvents(", stelle + 1);
            }
            if (angemeldet) {
                continue;
            }
            ohneAnmeldung.add(name);
        }

        // ERST DIE ZAHL DER FUNDE, dieselbe Begruendung wie bei 31cs: Ein Suchmuster,
        // das nichts mehr trifft, ist eine Tafel, die gruen ist, ohne etwas geprueft
        // zu haben. Untergrenze und keine Gleichheit - jede neue Wache darf sie
        // erhoehen.
        assertTrue(gefundene.size() >= 29,
                "nur " + gefundene.size() + " Wachen gefunden (" + gefundene
                        + ") - es waren 29 am 26.11.2026. Das Muster passt offenbar"
                        + " nicht mehr auf den Quelltext, und eine Tafel, die nur einen"
                        + " Teil sieht, ist schlimmer als keine.");

        assertEquals(java.util.Set.of(), ohneAnmeldung,
                "Diese Klassen setzen Listener um, werden aber in FreimarkenPlugin"
                        + " nirgends angemeldet. Sie sind gebaut, haben @EventHandler"
                        + " und werden NIE gerufen - Bukkit meldet dazu nichts, denn"
                        + " eine nicht angemeldete Klasse ist keine Wache, sondern"
                        + " Quelltext. Erkannt werden new <Name>( und registerEvents("
                        + "<name>,  - wird eine Wache auf einem dritten Weg angemeldet,"
                        + " gehoert dieser Weg hier hinein: " + ohneAnmeldung);
    }
}
