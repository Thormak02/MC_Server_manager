package de.freimarken.kern;

import java.util.Locale;

/**
 * Was ein Spieler getan hat, um Geld zu verdienen.
 *
 * <p><b>Eine Aufzaehlung und keine Liste in der Einstellungsdatei</b> - anders als die
 * Berufe. Der Unterschied ist handfest: Ein neuer BERUF ist ein Eintrag in der
 * {@code config.yml}, eine neue TAETIGKEIT braucht einen eigenen Ereigniszweig im
 * Programm. Was man nicht eintragen kann, soll auch nicht aussehen wie ein Eintrag.
 *
 * <p><b>Zwei und nicht zwoelf</b>, und das ist der Schnitt dieses Bauschrittes: Abbauen
 * und Setzen sind die zwei Taetigkeiten, deren Ereignis das Plugin heute schon hoert.
 * Fangen, Toeten, Zuechten und Ernten brauchen sieben weitere Ereignisklassen und kommen
 * mit dem naechsten Schritt - dann als weitere Konstanten hier, und die Lohnliste bekommt
 * je Beruf eine Ebene mehr, ohne dass eine Zeile des Bestehenden umzieht.
 *
 * <p>Der Schluessel ist kleingeschrieben: Er steht in der {@code config.yml} als
 * Ebenenname zwischen Beruf und Material.
 */
public enum Taetigkeit {

    /**
     * Einen gewachsenen Block abgebaut.
     *
     * <p>GEWACHSEN, nicht gesetzt: Ein markierter Block zahlt nichts. Das ist die Regel
     * "ein Block zahlt nur einmal", und sie steht in {@link Blockmarken}.
     */
    ABBAUEN("abbauen"),

    /**
     * Einen Block gesetzt.
     *
     * <p>Zahlt nur die ERSTE Setzung einer Position - danach traegt sie eine Marke, und
     * die bleibt. Sonst waere Setzen-Abbauen-Setzen eine Gelddruckmaschine.
     */
    SETZEN("setzen");

    private final String schluessel;

    Taetigkeit(String schluessel) {
        this.schluessel = schluessel;
    }

    /** Der Ebenenname in der Einstellungsdatei. */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Die Taetigkeit zu einem Schluessel, oder {@code null} fuer einen unbekannten.
     *
     * <p>Kein Wurf: Der Leser der Einstellungsdatei fragt damit, ob eine Ebene eine
     * Taetigkeit ist, und eine unbekannte ist dort eine KLAGE mit Zeilennummer und kein
     * Absturz beim Start.
     */
    public static Taetigkeit ausSchluessel(String text) {
        if (text == null) {
            return null;
        }
        String klein = text.trim().toLowerCase(Locale.ROOT);
        for (Taetigkeit taetigkeit : values()) {
            if (taetigkeit.schluessel.equals(klein)) {
                return taetigkeit;
            }
        }
        return null;
    }
}
