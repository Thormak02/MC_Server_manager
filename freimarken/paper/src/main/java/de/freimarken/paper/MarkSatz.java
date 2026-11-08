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
     * Der Stand - die erste der zwei Zeilen von {@code /mark}.
     *
     * <p>Mehrfarbig und darum EINE Zeile der Datei: Der Betrag ist golden, der Rest grau,
     * und das anders gefaerbte Stueck ist ein WERT (Bauplan 7). Wer den Satz umstellt,
     * braucht dafuer keinen zweiten Schluessel.
     *
     * <p>Diese vier Saetze kamen mit Zug 2b von Schritt 2 dazu und nicht mit Schritt 1:
     * Sie entstehen in {@code ParzellenBefehle}, und aus dieser Datei musste am Ende jede
     * Farbe verschwinden. Der Bereich entscheidet sich am Befehl, den der Betreiber
     * TIPPT - er tippt {@code /mark}, also sucht er unter {@code mark.} (Bauplan 3).
     */
    STAND("mark.stand", Art.SATZ, "betrag"),

    /** Die zweite Zeile von {@code /mark}: der Hinweis auf den Verlauf. */
    VERLAUF_HINWEIS("mark.verlauf-hinweis", Art.SATZ),

    /** {@code /mark verlauf} ohne eine einzige Buchung. */
    VERLAUF_LEER("mark.verlauf.leer", Art.SATZ),

    /** Der Kopf ueber den Buchungen. */
    VERLAUF_KOPF("mark.verlauf.kopf", Art.SATZ),

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
    VERLAUF_GRUND_BERUF_VERLASSEN("mark.verlauf.grund.beruf-verlassen", Art.WORT),
    VERLAUF_GRUND_BERUF_ERSTATTET("mark.verlauf.grund.beruf-erstattet", Art.WORT),
    VERLAUF_GRUND_BERUF_VERDIENST("mark.verlauf.grund.beruf-verdienst", Art.WORT),
    VERLAUF_GRUND_BUNDESMARKT_VERKAUF("mark.verlauf.grund.bundesmarkt-verkauf",
            Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.TAGESBONUS} im Verlauf. */
    VERLAUF_GRUND_TAGESBONUS("mark.verlauf.grund.tagesbonus", Art.WORT),

    VERLAUF_GRUND_LADEN_LIZENZ("mark.verlauf.grund.laden-lizenz", Art.WORT),
    VERLAUF_GRUND_LADEN_KAUTION("mark.verlauf.grund.laden-kaution", Art.WORT),
    VERLAUF_GRUND_LADEN_KAUTION_ZURUECK("mark.verlauf.grund.laden-kaution-zurueck",
            Art.WORT),
    VERLAUF_GRUND_LADEN_ERSTATTET("mark.verlauf.grund.laden-erstattet", Art.WORT),

    /**
     * Das Wort fuer {@code Buchungsgrund.LADEN_MIETE} [Teil C].
     *
     * <p>Der einzige WIEDERKEHRENDE Grund dieses Bereichs: Er steht einmal je Tag und
     * Laden im Verlauf, und ein Mieter sieht ihn oefter als jeden anderen. Darum ist das
     * Wort kurz.
     */
    VERLAUF_GRUND_LADEN_MIETE("mark.verlauf.grund.laden-miete", Art.WORT),

    /**
     * Die drei Worte des Handels an Kistenshops (Plan 7.4).
     *
     * <p><b>DREI Zeilen fuer EINEN Handel, und das ist die Entscheidung dahinter:</b> Was
     * der Kunde zahlt, was der Verkaeufer bekommt und was der Staat nimmt, sind drei
     * verschiedene Fluesse zwischen drei verschiedenen Konten. Ein stiller Abzug vom
     * Erloes waere nur als Differenz zweier Zahlen zu sehen - und dann ist die Summe
     * aller Buchungen eines Kontos nicht mehr sein Stand.
     *
     * <p><b>WAS gehandelt wurde, steht in keinem dieser Worte.</b> Dieselbe Trennung wie
     * bei {@link #VERLAUF_GRUND_BERUF_VERDIENST} und
     * {@link #VERLAUF_GRUND_BUNDESMARKT_VERKAUF}: Das Geldbuch fuehrt Geld. Was in
     * welcher Kiste lag, ist eine andere Frage und gehoert nicht in eine Zeile, die ein
     * Spieler unter {@code /mark verlauf} liest.
     *
     * <p>{@link #VERLAUF_GRUND_SHOP_GEBUEHR} faellt heute nie: Beide Gebuehrensaetze
     * stehen ab Werk auf 0. Das Wort steht da, damit es nicht fehlt, wenn der erste Satz
     * hochgeht - und damit eine Probe heute schon pruefen kann, dass es da ist.
     */
    VERLAUF_GRUND_SHOP_GEKAUFT("mark.verlauf.grund.shop-gekauft", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.SHOP_VERKAUFT} - siehe oben. */
    VERLAUF_GRUND_SHOP_VERKAUFT("mark.verlauf.grund.shop-verkauft", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.SHOP_GEBUEHR} - siehe oben. */
    VERLAUF_GRUND_SHOP_GEBUEHR("mark.verlauf.grund.shop-gebuehr", Art.WORT),

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
    WAEHRUNG_BETRAG("waehrung.betrag", Art.WORT, "zahl"),

    /**
     * Dasselbe Wort fuer einen Betrag UNTER einer Mark - zum Beispiel {@code "0,40 Mark"}.
     *
     * <p>Ein zweiter Schluessel und nicht derselbe, obwohl das Wort gleich lautet: Der
     * Verdienst wird in PFENNIGEN gerechnet, und der Handbalken zeigt, was die letzten
     * Sekunden gebracht haben - meistens weniger als eine Mark. Mit
     * {@link #WAEHRUNG_BETRAG} stuende dort "0 Mark", und ein Spieler, der arbeitet,
     * laese, dass er nichts verdient.
     *
     * <p>Die Zahlform bleibt im Programm, wie bei jedem Betrag: das Komma und die zwei
     * Stellen dahinter. Nur das WORT ist des Betreibers - wer seine Waehrung umbenennt,
     * muss es an ZWEI Stellen tun, und diese Zeile sagt es ihm.
     */
    WAEHRUNG_PFENNIGE("waehrung.pfennige", Art.WORT, "zahl");

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
