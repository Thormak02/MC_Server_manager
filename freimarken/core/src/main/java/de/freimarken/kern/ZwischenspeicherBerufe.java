package de.freimarken.kern;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Zwischenspeicher vor einer anderen Berufsablage.
 *
 * <p>Gebraucht wird er seit Teil E: Der Verdienst fragt bei JEDEM abgebauten und JEDEM
 * gesetzten Block, welche Berufe ein Spieler haelt. Eine Datenbankabfrage je Block waere
 * derselbe Fehler, den {@link ZwischenspeicherLager} schon einmal geloest hat - und zwar
 * wortgleich: selbst bei einer lokalen Datenbank spuerbar, bei einem Spieler mit
 * Flaechenwerkzeug unertraeglich.
 *
 * <p>Darum ist diese Klasse die ABSCHRIFT jenes Musters und keine neue Erfindung, mit
 * denselben zwei Vorkehrungen:
 *
 * <ul>
 *   <li><b>Jeder Schreibzug leert den ganzen Zwischenspeicher.</b> Grob, aber
 *       verlaesslich. Hier ist das sogar die tragende Zusage: Ein {@code /beruf waehlen}
 *       geht durch dieselbe Ablage, und der naechste Block rechnet damit sofort mit dem
 *       neuen Beruf. Ohne diese Leerung verdiente ein Spieler bis zu einer halben Minute
 *       lang nach seinem alten Beruf - und das waere der Fehler, den niemand meldet, weil
 *       er sich von selbst behebt.</li>
 *   <li><b>Jeder Eintrag verfaellt nach kurzer Zeit.</b> Kommt ein zweiter Server dazu,
 *       sieht dieser hier eine fremde Aenderung spaetestens nach der Verfallszeit.</li>
 * </ul>
 *
 * <p><b>Kein eigener Vertrag, sondern derselbe.</b> {@code BerufeVertrag} prueft auch
 * diese Fassung: Ein Zwischenspeicher, der sich anders verhaelt als die Ablage dahinter,
 * ist kein Zwischenspeicher, sondern ein zweiter Stand.
 */
public final class ZwischenspeicherBerufe implements Berufsablage {

    /** Wie lange ein gelesener Stand gilt. Dieselbe Zahl wie beim Lager. */
    public static final Duration VERFALL = Duration.ofSeconds(30);

    /** Ab so vielen Eintraegen wird beim Einfuegen nach Abgelaufenem gesucht. */
    private static final int HOECHSTZAHL = 1_000;

    private record Eintrag(Berufsstand stand, Instant bis) {
    }

    private final Berufsablage wahrheit;
    private final Clock uhr;
    private final Duration verfall;
    private final Map<UUID, Eintrag> speicher = new ConcurrentHashMap<>();

    public ZwischenspeicherBerufe(Berufsablage wahrheit) {
        this(wahrheit, Clock.systemUTC(), VERFALL);
    }

    /** Mit eigener Uhr und Verfallszeit - damit Proben den Verfall ohne Warten pruefen. */
    public ZwischenspeicherBerufe(Berufsablage wahrheit, Clock uhr, Duration verfall) {
        if (wahrheit == null || uhr == null || verfall == null) {
            throw new IllegalArgumentException("Ohne Ablage, Uhr und Verfallszeit gibt es"
                    + " keinen Zwischenspeicher");
        }
        this.wahrheit = wahrheit;
        this.uhr = uhr;
        this.verfall = verfall;
    }

    @Override
    public Berufsstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        Eintrag vorhanden = speicher.get(spieler);
        if (vorhanden != null && uhr.instant().isBefore(vorhanden.bis())) {
            return vorhanden.stand();
        }
        if (speicher.size() >= HOECHSTZAHL) {
            raeumeAb();
        }
        Berufsstand frisch = wahrheit.lies(spieler);
        speicher.put(spieler, new Eintrag(frisch, uhr.instant().plus(verfall)));
        return frisch;
    }

    @Override
    public boolean waehle(Antrag antrag, Berufsstand erwartet) {
        try {
            return wahrheit.waehle(antrag, erwartet);
        } finally {
            // Im finally und nicht nur bei Erfolg: Ein Wurf aus der Datenschicht laesst
            // offen, ob geschrieben wurde. Ein Zwischenspeicher, der dann den alten Stand
            // weiterreicht, waere die unauffaelligste Art, falsch zu sein.
            leeren();
        }
    }

    @Override
    public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
        try {
            return wahrheit.verlasse(antrag, erwartet);
        } finally {
            leeren();
        }
    }

    /** Wirft alles weg. Oeffentlich, weil ein Neuladen ihn leeren koennen soll. */
    public void leeren() {
        speicher.clear();
    }

    /** Wie viele Staende gerade gespeichert sind - fuer die Proben. */
    public int groesse() {
        return speicher.size();
    }

    private void raeumeAb() {
        Instant jetzt = uhr.instant();
        speicher.values().removeIf(eintrag -> !jetzt.isBefore(eintrag.bis()));
    }
}
