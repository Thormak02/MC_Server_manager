package de.freimarken.kern;

import java.time.Duration;

/**
 * Die Stammwerte der Berufe - was ein Aufgeben kostet und wie viele Plaetze es gibt.
 *
 * <p>Vier Zahlen und keine Liste: Die zwoelf Berufe selbst stehen im
 * {@link Berufsverzeichnis}, weil sie beim Neuladen NICHT getauscht werden duerfen - ein
 * Spieler haelt einen Beruf, den es danach nicht mehr gaebe. Diese vier dagegen sind
 * neuladbar, weil sie nur beim naechsten Zug gefragt werden.
 *
 * <p><b>Warum sie in {@link Wirtschaftswerte} haengen und nicht in einem eigenen
 * Halter:</b> Das Kriterium ist nicht "ist es neuladbar", sondern "wo wird es gebraucht".
 * Gebuehr und Sperrfrist werden bei JEDEM {@code /beruf verlassen} gefragt, also gehoeren
 * sie in die Zelle, die beim Gebrauch gefragt wird. Dasselbe Kriterium hat
 * {@link Zeitwerte} dorthin gebracht.
 *
 * <p><b>Die Platzzahlen sind einstellbar und stehen nicht im Programm.</b> Plan 9.1 nennt
 * zwei Grundberufe und eine Spezialisierung, aber als Zahl im Programm waere sie im
 * Dashboard nicht anpassbar - und die Hausregel lautet, dass Raenge und Berufe
 * Dateiinhalt sind, kein {@code switch}. Der Preis ist die Querpruefung gegen die Liste:
 * Mehr Plaetze als Berufe sind nie fuellbar.
 *
 * @param wechselgebuehr   was das Ablegen eines Berufes kostet. Bezahlt wird das
 *                         VERLASSEN und nicht das Waehlen - ein freier Platz ist gratis
 * @param sperrfrist       wie lange nach einem Ablegen kein weiteres geht;
 *                         {@link Duration#ZERO} schaltet die Regel ab. GANZE Stunden
 * @param grundberufe      wie viele Grundberufe ein Spieler gleichzeitig halten darf
 * @param spezialisierungen wie viele Spezialisierungen dazu
 */
public record Berufswerte(long wechselgebuehr, Duration sperrfrist, int grundberufe,
                          int spezialisierungen) {

    /**
     * Die Schluessel in der Einstellungsdatei - hier und nicht an jeder Stelle, die klagt.
     *
     * <p>Dieselbe Begruendung wie bei {@link Zeitwerte#PFAD_ZONE}: Der Leser nennt den
     * Pfad in seiner Klage, der Befehl in der Zeile "alt -&gt; neu". Liefen sie
     * auseinander, suchte der Betreiber an der falschen Zeile.
     */
    public static final String PFAD_WECHSELGEBUEHR = "berufe.wechselgebuehr";

    public static final String PFAD_SPERRFRIST = "berufe.sperrfrist-stunden";

    public static final String PFAD_GRUNDBERUFE = "berufe.hoechstens-grundberufe";

    public static final String PFAD_SPEZIALISIERUNGEN =
            "berufe.hoechstens-spezialisierungen";

    /** Der Abschnitt mit den zwoelf Eintraegen - nicht neuladbar, siehe Klassenkopf. */
    public static final String PFAD_LISTE = "berufe.liste";

    /** Die ausgelieferte Gebuehr fuers Ablegen. */
    public static final long VORGABE_WECHSELGEBUEHR = 5_000L;

    /** Die ausgelieferte Sperrfrist in Stunden. */
    public static final long VORGABE_SPERRFRIST_STUNDEN = 24L;

    /** Wie viele Grundberufe ausgeliefert erlaubt sind - Plan 9.1. */
    public static final int VORGABE_GRUNDBERUFE = 2;

    /** Wie viele Spezialisierungen ausgeliefert erlaubt sind - Plan 9.1. */
    public static final int VORGABE_SPEZIALISIERUNGEN = 1;

    /**
     * Die hoechste erlaubte Gebuehr.
     *
     * <p>Die Haelfte von {@link Long#MAX_VALUE} und nicht das Ganze: Die Gebuehr geht in
     * eine Abbuchung, und die Kasse rechnet damit weiter. Dieselbe Vorsicht wie bei der
     * Preisstaffel - ein ueberlaufender Betrag waere NEGATIV, und ein negativer Betrag
     * ist eine Gutschrift.
     */
    public static final long HOECHSTE_WECHSELGEBUEHR = Long.MAX_VALUE / 2L;

    /**
     * Die laengste erlaubte Sperrfrist in Stunden - dreissig Tage.
     *
     * <p>Darueber ist der Beruf keine Entscheidung mehr, sondern endgueltig. Wer das
     * will, nimmt die dreissig Tage; wer die Regel abschalten will, nimmt die 0.
     */
    public static final long LAENGSTE_SPERRFRIST_STUNDEN = 720L;

    /** Genau das, was ohne Einstellungsdatei gelten wuerde. */
    public static final Berufswerte VORGABE = new Berufswerte(VORGABE_WECHSELGEBUEHR,
            Duration.ofHours(VORGABE_SPERRFRIST_STUNDEN), VORGABE_GRUNDBERUFE,
            VORGABE_SPEZIALISIERUNGEN);

    public Berufswerte {
        if (sperrfrist == null) {
            throw new IllegalArgumentException("Die Sperrfrist ist Pflicht;"
                    + " Duration.ZERO schaltet die Regel ab");
        }
        if (!brauchbareWechselgebuehr(wechselgebuehr)) {
            throw new IllegalArgumentException("Wechselgebuehr " + wechselgebuehr
                    + " liegt nicht zwischen 0 und " + HOECHSTE_WECHSELGEBUEHR);
        }
        // GANZE Stunden, und zwar hier und nicht erst beim Lesen der Datei. Sonst haelt
        // dieser Satz zwei Darstellungen desselben Wertes, von denen nur die
        // abgeschnittene geprueft und angezeigt wird - dieselbe Falle, die Zeitwerte bei
        // Takt und Untaetigkeitsgrenze beschreibt: Duration.ofMinutes(90) kaeme durch,
        // toHours() ergaebe 1, und dann widersprachen sich sperrfristGilt() (die Regel
        // ist AN), sperrfristStunden() (1) und der Spielertext.
        if (sperrfrist.getNano() != 0 || sperrfrist.toSeconds() % 3600L != 0L) {
            throw new IllegalArgumentException("Eine Sperrfrist wird in ganzen Stunden"
                    + " gemessen, hier bleibt ein Rest: " + sperrfrist);
        }
        if (!brauchbareSperrfrist(sperrfrist.toHours())) {
            throw new IllegalArgumentException("Sperrfrist " + sperrfrist.toHours()
                    + " h liegt nicht zwischen 0 und " + LAENGSTE_SPERRFRIST_STUNDEN);
        }
        if (!brauchbareAnzahl(grundberufe)) {
            throw new IllegalArgumentException("Es muss mindestens einen Platz fuer einen"
                    + " Grundberuf geben, hier sind es " + grundberufe
                    + ". Bei 0 koennte niemand einen Beruf waehlen, und das wollte"
                    + " niemand tippen - wer die Berufe ausschalten will, laesst die"
                    + " Liste leer");
        }
        if (!brauchbareAnzahlOderNull(spezialisierungen)) {
            throw new IllegalArgumentException("Die Zahl der Spezialisierungen darf nicht"
                    + " negativ sein, hier ist sie " + spezialisierungen
                    + ". Die 0 ist erlaubt und heisst: keine Spezialisierungen");
        }
    }

    /**
     * Ob aus dieser Zahl eine Gebuehr werden kann.
     *
     * <p>Oeffentlich und auf {@code long}, aus demselben Grund wie
     * {@link Wirtschaftswerte#brauchbareAnzahl}: Die Einstellungsdatei wird an derselben
     * Regel geprueft, nach der hier gerechnet wird, und zwar an der getippten Zahl und
     * nicht an ihren unteren 32 Bit.
     *
     * <p>Die 0 ist erlaubt: Ein Server, der das Aufgeben verschenken will, soll es
     * eintragen koennen, ohne die Sperrfrist mit abzuschalten. Beide Zahlen bremsen
     * getrennt.
     */
    public static boolean brauchbareWechselgebuehr(long mark) {
        return mark >= 0L && mark <= HOECHSTE_WECHSELGEBUEHR;
    }

    /**
     * Ob aus dieser Zahl eine Sperrfrist werden kann.
     *
     * <p>0 ist erlaubt und heisst ausdruecklich: kein Warten - der ehrliche Ausschalter.
     * Dieselbe Abwaegung wie bei {@link Zeitwerte#brauchbareUntaetigkeit}: Eine verbotene
     * 0 hiesse, dass es keinen Weg gibt, die Regel abzuschalten, und dann baut sich der
     * Betreiber den Ausschalter selbst.
     */
    public static boolean brauchbareSperrfrist(long stunden) {
        return stunden >= 0L && stunden <= LAENGSTE_SPERRFRIST_STUNDEN;
    }

    /** Ob aus dieser Zahl eine Platzzahl werden kann, die mindestens einen Platz laesst. */
    public static boolean brauchbareAnzahl(long zahl) {
        return zahl >= 1L && zahl <= Integer.MAX_VALUE;
    }

    /** Wie {@link #brauchbareAnzahl}, aber die 0 ist erlaubt - fuer die Spezialisierungen. */
    public static boolean brauchbareAnzahlOderNull(long zahl) {
        return zahl >= 0L && zahl <= Integer.MAX_VALUE;
    }

    /**
     * Ob diese Platzzahl zu einer Liste dieser Groesse passt.
     *
     * <p>Mehr Plaetze als Berufe sind nie fuellbar, und ein Spieler sieht nur, dass
     * nichts geschieht. Die Pruefung gehoert NICHT in den Kompaktkonstruktor: Dieser Satz
     * kennt die Liste nicht, und er soll sie nicht kennen - sonst waere er beim Neuladen
     * an eine Liste gebunden, die gar nicht neu gelesen wird.
     */
    public static boolean passtZurListe(long plaetze, int inDerListe) {
        return plaetze <= inDerListe;
    }

    /** Die Sperrfrist in ganzen Stunden - die Zahl, die ein Satz einsetzt. */
    public long sperrfristStunden() {
        return sperrfrist.toHours();
    }

    /** Ob ueberhaupt gewartet werden muss. */
    public boolean sperrfristGilt() {
        return !sperrfrist.isZero();
    }
}
