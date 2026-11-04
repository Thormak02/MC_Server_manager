package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import java.util.Set;

/**
 * Die Saetze von {@code /mark} - und die beiden Formen, die jeder andere Satz borgt.
 *
 * <p>Die kleinste moegliche erste Gruppe, und sie uebt alle vier Arten auf einmal:
 * {@link Art#SATZ} (die Verlaufszeile mit Farbe und Einzug), {@link Art#WORT} als Wert in
 * einem anderen Satz (die sechs Buchungsgruende, die Waehrung) und {@link Art#MUSTER} (die
 * Zeitform). Eine Pruefmaschine mit null Zeilen laeuft leer durch - das ist der Grund, dass
 * ueberhaupt schon eine Gruppe in Schritt 1 umzieht.
 *
 * <p><b>Die Schluessel der Buchungsgruende kommen aus dem AUFZAEHLUNGSNAMEN und nicht aus
 * {@link Buchungsgrund#schluessel()}.</b> {@code PARZELLE_GEKAUFT} traegt dort einen
 * Unterstrich ({@code parzelle_gekauft}, Buchungsgrund.java:42), und der
 * Datenbankschluessel darf sich aendern, ohne eine Betreiberzeile stumm zu entwerten -
 * {@code Buchungsgrund} verlangt das im eigenen Javadoc ("muss die heutige Wortwahl
 * ueberleben").
 */
enum MarkSatz implements Satz {

    /**
     * Die eine Zeile von {@code /mark verlauf} - mit Einzug und Farbe IM Satz.
     *
     * <p>Bis zum 04.11.2026 gab {@code Verlaufszeile.text} eine Zeichenkette zurueck, und
     * {@code ParzellenBefehle:225-226} baute daraus {@code Component.text("  " + text,
     * GRAY)}. Beides steht jetzt in diesem Satz: die zwei Leerzeichen am Anfang und das
     * {@code <gray>} um das Ganze. Waere die Zeile eine Zeichenkette geblieben, haette ein
     * {@code <gold>} darin dem Spieler woertlich im Chat gestanden.
     */
    VERLAUF_ZEILE("mark.verlauf.zeile", Art.SATZ, "zeit", "betrag", "grund", "stand"),

    VERLAUF_GRUND_EROEFFNUNG("mark.verlauf.grund.eroeffnung", Art.WORT),
    VERLAUF_GRUND_BESTAND("mark.verlauf.grund.bestand", Art.WORT),
    VERLAUF_GRUND_PARZELLE_GEKAUFT("mark.verlauf.grund.parzelle-gekauft", Art.WORT),
    VERLAUF_GRUND_PARZELLE_ERSTATTET("mark.verlauf.grund.parzelle-erstattet", Art.WORT),
    VERLAUF_GRUND_ZUSAMMENLEGUNG("mark.verlauf.grund.zusammenlegung", Art.WORT),
    VERLAUF_GRUND_ZUSAMMENLEGUNG_ERSTATTET("mark.verlauf.grund.zusammenlegung-erstattet",
            Art.WORT),

    /**
     * Die Zeitform des Verlaufs - beim LADEN uebersetzt.
     *
     * <p>Beim Laden und nicht beim ersten {@code /mark verlauf}: Ein unbrauchbares Muster
     * ist dann eine Klage mit Zeilennummer und kein Wurf mitten in einem Befehl.
     */
    ZEIT_VERLAUF_MUSTER("zeit.verlauf-muster", Art.MUSTER),

    /**
     * Das Wort fuer einen Geldbetrag - zum Beispiel {@code "{zahl} Mark"}.
     *
     * <p>Nur das WORT ist des Betreibers. Der Tausenderpunkt und {@code Locale.GERMANY}
     * bleiben im Programm: {@code Locale.forLanguageTag} wirft nie, es verwirft eine
     * fehlerhafte Eingabe stumm und liefert {@code ROOT} - eine Klage dazu ist nicht
     * herstellbar, und eine Probe, die nicht rot werden kann, ist keine.
     */
    WAEHRUNG_BETRAG("waehrung.betrag", Art.WORT, "zahl");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    MarkSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    @Override
    public boolean darfLeerSein() {
        return false;
    }

    /**
     * Der Pfad, unter dem das Wort eines Buchungsgrundes steht - aus seinem Namen gebildet.
     *
     * <p>Klein, Unterstrich zu Bindestrich. Dass daraus wirklich ein Satz dieser
     * Aufzaehlung wird, sichert {@code SendewegTest.grundIstVollstaendig} ueber
     * {@code values()} zu - in BEIDE Richtungen, damit weder ein Grund ohne Wort noch ein
     * Wort ohne Grund durchgeht.
     */
    static String grundpfad(Buchungsgrund grund) {
        return "mark.verlauf.grund."
                + grund.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
