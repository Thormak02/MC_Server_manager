package de.freimarken.kern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche Stationen der Rundgang hat - die Liste aus der {@code config.yml} (Plan 10.2).
 *
 * <p>Dieselbe Bauart wie {@link Ladenverzeichnis}: Die REIHENFOLGE der Datei bleibt, denn
 * sie ist der WEG - Plan 10.2 nennt einen "Weg mit Stationen", und in welcher Folge er
 * laeuft, entscheidet der Betreiber. Bei zwei Eintraegen zum selben Namen gewinnt der
 * ERSTE; der zweite wird beim Lesen beklagt, nicht hier - diese Klasse kennt keinen
 * Betreiber.
 *
 * <p><b>Dieses Verzeichnis wird NUR BEIM START gelesen.</b> Es ist kein Wert, sondern ein
 * Ort: Verschiebt ein Neuladen den Bereich einer Station, haette ein Spieler eine Station
 * gesehen, an der er nie war - und eine andere, an der er stand, nicht. Dieselbe
 * Entscheidung wie bei den Ladeneinheiten und den Toren der Welten.
 *
 * <p><b>Ein LEERES Verzeichnis ist der ausgelieferte Zustand und kein Mangel.</b> Die
 * Gebaeude in Altfurt sind Handarbeit; solange keines steht, gibt es keinen Rundgang. Die
 * Folge ist ueberall dieselbe und wird nirgends zu einem Sonderfall: Ohne Stationen wird
 * die Wache nicht angemeldet, {@link Rundgang#abgeschlossen} gibt {@code false}, und eine
 * Rangbedingung darauf ist nicht erfuellbar - darum steht sie ab Werk auch nicht in der
 * Leiter.
 *
 * <p><b>Die REIHENFOLGE ist eine Anzeige und kein Zwang.</b> Jede Station zaehlt, sobald
 * sie betreten wird; wer am Muenzhaus vorbeilaeuft, bevor er im Zunfthaus war, hat das
 * Muenzhaus gesehen. Ein erzwungener Weg muesste den Spieler fuehren und ihm sagen, dass
 * er zurueckgehen soll - Plan 10.2 verlangt "ein Ort, kein Textfenster", und ein Rundgang,
 * der einen abweist, ist ein Textfenster mit anderen Mitteln.
 */
public final class Rundgangsverzeichnis {

    /**
     * So viele Stationen darf ein Rundgang hoechstens haben.
     *
     * <p>Zwoelf, also das Doppelte der sechs aus Plan 10.2. Die Grenze ist keine
     * Rechengrenze, sondern eine ueber den Einstieg: Ein Rundgang mit dreissig Stationen
     * ist kein Einstieg mehr, sondern eine Aufgabe - und er haette einen Spieler, der
     * nach der zwanzigsten aufhoert und nie Siedler wird.
     */
    public static final int HOECHSTENS = 12;

    private final Map<String, Rundgangsstation> stationen;

    private Rundgangsverzeichnis(Map<String, Rundgangsstation> stationen) {
        this.stationen = stationen;
    }

    /**
     * Baut das Verzeichnis - der ERSTE Eintrag zu einem Namen gewinnt.
     *
     * <p>Die Reihenfolge der Liste bleibt die Reihenfolge des Verzeichnisses
     * ({@link LinkedHashMap}), denn sie ist der Weg. Ein {@code null}-Eintrag ist ein
     * Fehler im Programm und keine Fehleingabe - der Leser wirft kaputte Zeilen weg,
     * bevor sie hierher kommen.
     *
     * <p><b>Ueberschneidende Bereiche werden hier NICHT abgewiesen</b>, sondern beim
     * Lesen: Dort gibt es den Betreiber, dem man sagen kann, welche zwei Zeilen sich
     * beissen. Hier waere es eine Ausnahme mitten im Start.
     */
    public static Rundgangsverzeichnis ausStationen(List<Rundgangsstation> stationen) {
        if (stationen == null) {
            throw new IllegalArgumentException("Nichts ist kein Verzeichnis, sondern eine"
                    + " leere Liste");
        }
        if (stationen.size() > HOECHSTENS) {
            throw new IllegalArgumentException("Ein Rundgang hat hoechstens " + HOECHSTENS
                    + " Stationen, nicht " + stationen.size() + ". Mehr ist kein"
                    + " Einstieg mehr, sondern eine Aufgabe.");
        }
        Map<String, Rundgangsstation> karte = new LinkedHashMap<>();
        for (Rundgangsstation station : stationen) {
            if (station == null) {
                throw new IllegalArgumentException("Eine Station in der Liste ist null");
            }
            karte.putIfAbsent(station.schluessel(), station);
        }
        return new Rundgangsverzeichnis(Collections.unmodifiableMap(karte));
    }

    /**
     * Das leere Verzeichnis - der ausgelieferte Zustand.
     *
     * <p>Heisst {@code ohneStationen} und nicht {@code leer}: Daneben steht die Frage
     * {@link #leer()}, und zwei Namen fuer zwei verschiedene Dinge sind besser als einer,
     * den der Uebersetzer trennt und ein Leser nicht. Dieselbe Entscheidung wie bei
     * {@link Ladenverzeichnis#ohneEinheiten}.
     */
    public static Rundgangsverzeichnis ohneStationen() {
        return ausStationen(List.of());
    }

    public int anzahl() {
        return stationen.size();
    }

    /** Ob es gar keinen Rundgang gibt - der ausgelieferte Zustand. */
    public boolean leer() {
        return stationen.isEmpty();
    }

    /** Die Namen in der Reihenfolge der Datei, also in der Reihenfolge des Weges. */
    public List<String> namen() {
        return List.copyOf(stationen.keySet());
    }

    /** Die Stationen in der Reihenfolge der Datei. */
    public List<Rundgangsstation> alle() {
        return List.copyOf(stationen.values());
    }

    /**
     * Die Station mit diesem Namen - oder {@code null}.
     *
     * <p>Gibt {@code null} und wirft nicht: Der Name kann aus der ABLAGE kommen, und dort
     * steht womoeglich eine Station, die der Betreiber inzwischen aus der Datei genommen
     * hat. Das ist kein Fehler, sondern ein Eintrag, der nicht mehr zaehlt.
     */
    public Rundgangsstation mitNamen(String name) {
        if (name == null) {
            return null;
        }
        return stationen.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Die Station an diesem Ort - oder {@code null}, wenn dort keine liegt.
     *
     * <p><b>Die eine Frage, die die Wache stellt.</b> Sie laeuft die Stationen in der
     * Reihenfolge der Datei ab und gibt die ERSTE, die den Ort enthaelt; dass sich keine
     * zwei ueberschneiden, hat der Leser schon abgewiesen. Ohne diese Zusage waere die
     * Antwort von der Reihenfolge der Datei abhaengig - und ein Betreiber, der zwei
     * Zeilen tauscht, bekaeme eine andere Station gezaehlt.
     */
    public Rundgangsstation anDemOrt(int x, int y, int z) {
        for (Rundgangsstation station : stationen.values()) {
            if (station.enthaelt(x, y, z)) {
                return station;
            }
        }
        return null;
    }

    /**
     * Wie viele der GESEHENEN Stationen es heute noch gibt.
     *
     * <p>Die Rechnung hinter {@link Rundgang#abgeschlossen}: Gezaehlt wird nur, was in
     * der Datei steht. Nimmt der Betreiber eine Station heraus, bleiben Zeilen in der
     * Ablage stehen, die auf sie zeigen - sie zaehlen nicht mehr mit, und sie sind kein
     * Fehler. Fuegt er eine HINZU, ist der Rundgang fuer alle wieder offen, und das ist
     * richtig: Ein Weg mit einer neuen Station ist ein neuer Weg.
     */
    public int davonVorhanden(java.util.Set<String> gesehen) {
        if (gesehen == null) {
            throw new IllegalArgumentException("Ohne gesehene Stationen gibt es nichts zu"
                    + " zaehlen - eine leere Menge ist der Normalfall");
        }
        int gezaehlt = 0;
        for (String name : stationen.keySet()) {
            if (gesehen.contains(name)) {
                gezaehlt++;
            }
        }
        return gezaehlt;
    }
}
