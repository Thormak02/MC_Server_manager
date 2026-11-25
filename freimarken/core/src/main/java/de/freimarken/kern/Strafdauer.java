package de.freimarken.kern;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Wie lange eine Strafe gilt (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p><b>Eine eigene Sache und kein {@code long} im Befehl</b>, und der Grund ist der
 * Riegel: Plan 12c.2 gibt dem Moderator das <i>zeitliche</i> Bannen und dem Verwalter das
 * <i>dauerhafte</i>. Diese Unterscheidung muss man FRAGEN koennen, bevor etwas geschieht -
 * und zwar ohne Datenbank, ohne Server, in einer Probe. Mit einem {@code long} Sekunden
 * waere "unbegrenzt" eine verabredete Zahl, und eine verabredete Zahl liest irgendwann
 * jemand als Dauer.
 *
 * <p><b>Die gefaehrliche Luecke, gegen die diese Klasse gebaut ist:</b> Ein Moderator ohne
 * das Recht zum dauerhaften Bannen tippt {@code 36500d}. Das ist formal befristet und
 * praktisch endgueltig. Darum zwei Grenzen, und beide sind noetig:
 * <ul>
 *   <li>{@link #LAENGSTE_TAGE} hier - was darueber liegt, ist kein brauchbares Wort
 *       mehr. Die Absage nennt {@code dauerhaft} als den Weg, der offen steht; dann
 *       steht es auch so in der Bannliste.</li>
 *   <li>Die Hoechstdauer aus {@link Strafwerte} - was darueber liegt, braucht dasselbe
 *       Recht wie {@code dauerhaft}. Das ist der eigentliche Riegel, und er steht in
 *       {@link Strafbuch#verhaenge}.</li>
 * </ul>
 * Die erste Grenze allein wuerde nichts schuetzen (3650 Tage sind auch endgueltig), die
 * zweite allein liesse die Rechnung ueberlaufen.
 *
 * @param spanne wie lange; {@code null} heisst <b>unbegrenzt</b>
 */
public record Strafdauer(Duration spanne) {

    /**
     * Die laengste befristete Dauer - 3650 Tage, also zehn Jahre.
     *
     * <p>Nicht {@link Long#MAX_VALUE} und nicht hundert Jahre: Eine Zahl, die man
     * hinschreiben kann, soll eine Dauer bezeichnen, die man absitzen kann. Wer laenger
     * sperren will, tippt {@code dauerhaft} - dann liest der Betroffene in der Bannliste
     * "dauerhaft" und nicht ein Datum im Jahr 2126, das ihn fuer einen Augenblick hoffen
     * laesst.
     */
    public static final long LAENGSTE_TAGE = 3650L;

    /** Das Wort fuer eine Strafe ohne Ende - in der Datei, im Befehl, in der Liste. */
    public static final String WORT_UNBEGRENZT = "dauerhaft";

    /** Eine Strafe ohne Ende. */
    public static final Strafdauer UNBEGRENZT = new Strafdauer(null);

    /**
     * Woerter, die {@code /strafe} als Vervollstaendigung anbietet.
     *
     * <p>Die Auswahl ist der Alltag und nicht die Vollstaendigkeit: zehn Minuten fuer den
     * Streit, eine Stunde zum Abkuehlen, ein Tag und sieben Tage fuer den Wiederholer,
     * dreissig Tage als letzte Stufe vor {@code dauerhaft}. Getippt werden kann jede
     * andere Zahl auch - die Liste ist ein Vorschlag und keine Erlaubnis.
     */
    public static final List<String> VORSCHLAEGE =
            List.of("10m", "1h", "12h", "1d", "7d", "30d", WORT_UNBEGRENZT);

    public Strafdauer {
        if (spanne != null) {
            if (spanne.isZero() || spanne.isNegative()) {
                throw new IllegalArgumentException("Eine Strafe von " + spanne
                        + " ist keine. Null oder weniger heisst: Es geschieht nichts -"
                        + " und das ist kein Zustand, den ein Protokoll halten darf");
            }
            if (spanne.toDays() > LAENGSTE_TAGE) {
                throw new IllegalArgumentException("Hoechstens " + LAENGSTE_TAGE
                        + " Tage, nicht " + spanne.toDays() + ". Wer laenger sperren"
                        + " will, nimmt " + WORT_UNBEGRENZT + " - dann steht es auch so"
                        + " in der Bannliste");
            }
        }
    }

    /** Ob diese Strafe kein Ende hat. */
    public boolean unbegrenzt() {
        return spanne == null;
    }

    /**
     * Wann die Strafe endet, wenn sie zu diesem Zeitpunkt verhaengt wird.
     *
     * <p><b>Leer heisst: KEIN Ende</b> - nicht "unbekannt". Das ist die eine Stelle, an
     * der ein leeres {@link Optional} in dieser Klasse etwas anderes bedeutet als bei
     * {@link #ausWort}, und darum steht es hier: Wer {@code endeAb(...).isEmpty()} liest,
     * liest "dauerhaft gesperrt".
     */
    public Optional<Instant> endeAb(Instant verhaengtAm) {
        if (verhaengtAm == null) {
            throw new IllegalArgumentException("Ohne Verhaengungszeitpunkt gibt es kein"
                    + " Ende");
        }
        return spanne == null ? Optional.empty()
                : Optional.of(verhaengtAm.plus(spanne));
    }

    /**
     * Ob diese Dauer laenger ist als so viele Stunden - DIE FRAGE DES RIEGELS.
     *
     * <p><b>Eine Frage und keine Zahl</b>, und das ist ein behobener Fehler [25.11.2026]:
     * Vorher gab es {@code stunden()}, und der Riegel fragte
     * {@code stunden() > grenze}. {@code Duration.toHours()} SCHNEIDET AB -
     * {@code ofMinutes(59).toHours()} ist 0 - und damit war bei einer Grenze von 0
     * jede Dauer unter einer Stunde erlaubt, obwohl {@code config.yml}, Startbericht
     * und Absage zusagen, dass 0 JEDE Dauer sperrt. Bei der Grenze 720 kam eine Strafe
     * von 720 Stunden und 59 Minuten durch.
     *
     * <p>Dieselbe Falle hat dieses Projekt bei {@link Berufswerte} schon einmal
     * zugemauert (dort wirft der Konstruktor, wenn die Sperrfrist keine ganze Stunde
     * ist). Hier ist der Weg der andere: Die Frage wandert an den WERT, und gerechnet
     * wird mit der ganzen Spanne. Damit kann die Abschneidung an keiner Aufrufstelle
     * mehr auftauchen - es gibt keine Zahl mehr, mit der man sie falsch vergleichen
     * koennte.
     *
     * <p>Eine UNBEGRENZTE Dauer ist laenger als jede Zahl, auch als
     * {@link Long#MAX_VALUE}: Sie hat kein Ende.
     *
     * @param stunden die Grenze; 0 heisst, dass jede Dauer darueber liegt
     */
    public boolean laengerAls(long stunden) {
        if (spanne == null) {
            return true;
        }
        return spanne.compareTo(Duration.ofHours(stunden)) > 0;
    }

    /**
     * Die Dauer zu diesem Wort.
     *
     * <p>Eine Zahl und eine Einheit, oder {@link #WORT_UNBEGRENZT}. Die Einheiten sind
     * {@code s}, {@code m}, {@code h} und {@code d} - Sekunden, Minuten, Stunden, Tage.
     *
     * <p><b>Die Einheit ist Pflicht.</b> Ein blosses {@code 10} waere zehn Minuten oder
     * zehn Tage, je nachdem, von welchem Server der Moderator kommt - und der Unterschied
     * ist der zwischen einer Abkuehlung und einem verlorenen Monat. Eine Zahl ohne
     * Einheit wird darum abgewiesen und nicht geraten.
     *
     * <p>Mehrteilige Angaben ({@code 1d12h}) gibt es nicht. Das ist eine benannte Luecke:
     * Wer anderthalb Tage will, tippt {@code 36h}.
     *
     * @return leer, wenn das Wort keine brauchbare Dauer ist
     */
    public static Optional<Strafdauer> ausWort(String wort) {
        if (wort == null) {
            return Optional.empty();
        }
        String rein = wort.trim().toLowerCase(Locale.ROOT);
        if (rein.isEmpty()) {
            return Optional.empty();
        }
        if (rein.equals(WORT_UNBEGRENZT)) {
            return Optional.of(UNBEGRENZT);
        }
        char einheit = rein.charAt(rein.length() - 1);
        String zahl = rein.substring(0, rein.length() - 1);
        if (zahl.isEmpty()) {
            return Optional.empty();
        }
        for (int stelle = 0; stelle < zahl.length(); stelle++) {
            // Von Hand und nicht mit parseLong: Jenes nimmt ein Vorzeichen an, und
            // "-10m" waere eine negative Dauer, die erst der Konstruktor wirft -
            // gemessen an derselben Falle bei den Marktnummern.
            if (zahl.charAt(stelle) < '0' || zahl.charAt(stelle) > '9') {
                return Optional.empty();
            }
        }
        Duration spanne;
        try {
            long anzahl = Long.parseLong(zahl);
            if (anzahl <= 0L) {
                return Optional.empty();
            }
            // DER FANG UMSCHLIESST AUCH DIE UMRECHNUNG [behoben am 25.11.2026]:
            // Duration.ofDays rechnet mit Math.multiplyExact und wirft eine
            // ArithmeticException ("long overflow"), BEVOR die Grenze darunter
            // greift. Vorher stand der switch ausserhalb des Fangs, und
            // "/strafe bann Bert 999999999999999d Grief" lief als Wurf durch den
            // Befehl hinaus zu Brigadier - der Moderator las eine rote englische
            // Zeile statt der Absage mit den Beispielen. Nur die Einheit s war
            // gefahrlos.
            spanne = switch (einheit) {
                case 's' -> Duration.ofSeconds(anzahl);
                case 'm' -> Duration.ofMinutes(anzahl);
                case 'h' -> Duration.ofHours(anzahl);
                case 'd' -> Duration.ofDays(anzahl);
                default -> null;
            };
        } catch (NumberFormatException | ArithmeticException zuGross) {
            // Mehr, als in einen long oder in eine Duration passt. Beides ist
            // dasselbe fuer den Tippenden: kein brauchbares Wort.
            return Optional.empty();
        }
        if (spanne == null || spanne.toDays() > LAENGSTE_TAGE) {
            return Optional.empty();
        }
        return Optional.of(new Strafdauer(spanne));
    }

    /**
     * Die Dauer zwischen zwei Zeitpunkten - fuer eine Strafe, die aus der Datenbank
     * kommt.
     *
     * <p>Dort steht ein ENDE und keine Spanne, weil nur ein Ende sich abfragen laesst
     * ({@code WHERE laeuft_ab_am > ?}). Beim Lesen wird daraus wieder eine Dauer, und
     * zwar fuer eine einzige Frage: Wie lange galt sie? Zum Vergleichen mit der
     * Hoechstdauer taugt sie NICHT - dafuer nimmt man die Dauer, die der Moderator
     * getippt hat, denn eine halb abgesessene Strafe waere sonst nachtraeglich erlaubt.
     *
     * @param ende {@code null} heisst unbegrenzt
     */
    public static Strafdauer zwischen(Instant von, Instant ende) {
        if (von == null) {
            throw new IllegalArgumentException("Ohne Anfang gibt es keine Dauer");
        }
        if (ende == null) {
            return UNBEGRENZT;
        }
        Duration spanne = Duration.between(von, ende);
        if (spanne.isZero() || spanne.isNegative()) {
            // Eine abgelaufene oder gleichzeitige Strafe - das ist ein Rauswurf, der mit
            // Ende gleich Anfang gespeichert wird. Eine Dauer von null Sekunden darf der
            // Konstruktor nicht halten, also wird daraus eine Sekunde: Die Angabe wird
            // ohnehin nur angezeigt, und "0 Sekunden" liest sich wie ein Fehler.
            return new Strafdauer(Duration.ofSeconds(1L));
        }
        if (spanne.toDays() > LAENGSTE_TAGE) {
            return new Strafdauer(Duration.ofDays(LAENGSTE_TAGE));
        }
        return new Strafdauer(spanne);
    }
}
