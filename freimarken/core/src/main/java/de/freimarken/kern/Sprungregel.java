package de.freimarken.kern;

/**
 * Wann eine geaenderte Zahl eine zweite Bestaetigung braucht - die eine Stelle, an der
 * ueber "mehr als Faktor 2" entschieden wird.
 *
 * <p>Es gibt zwei Arten von Zahlen in der Einstellungsdatei. Bei der Weltgrenze ist nur
 * der Weg nach OBEN gefaehrlich: Die neu eingeschlossene Flaeche wird vorgeneriert und
 * belegt Platte, und zurueck geht es nie, weil eine Grenze nie nach innen geschoben wird
 * ({@link Grenzregel}). Bei einem Geldwert ist die gefaehrliche Richtung genau umgekehrt:
 * Ein zu billiger Preis macht Mark, und die liegen binnen Minuten auf vielen Konten -
 * zurueckzuholen nur mit einer Ruecksicherung des ganzen Servers. Deshalb gibt es hier
 * beide Richtungen.
 *
 * <p>Verglichen wird IMMER gegen die ausgelieferte Vorgabe und nie gegen den laufenden
 * Wert. Sonst verlangte dieselbe Datei zwei verschiedene Bestaetigungen: Wer beim
 * Neuladen "100000 -&gt; 400000" eintraegt, sieht beim naechsten Start wieder die Vorgabe
 * 25.000 als Ausgangspunkt - und damit gaelte sein Wert ohne sein Zutun nicht mehr. So
 * ist die Pruefung eine reine Funktion der Datei, und der eingetragene Text ueberlebt
 * jeden Neustart.
 */
public final class Sprungregel {

    private Sprungregel() {
    }

    /**
     * Ob {@code neu} mehr als das Doppelte von {@code alt} ist.
     *
     * <p>Genau das Doppelte gilt noch als erlaubt - die Schranke beginnt darueber.
     */
    public static boolean grosserSprungNachOben(long alt, long neu) {
        return neu > 2 * alt;
    }

    /**
     * Ob {@code neu} weniger als die Haelfte von {@code alt} ist.
     *
     * <p>Spiegelbildlich zur Richtung nach oben: Genau die Haelfte ist noch erlaubt.
     * Gerechnet wird {@code 2 * neu < alt} und nicht {@code neu < alt / 2}, weil eine
     * ganzzahlige Division abrundet und die Grenze damit um eins verschoebe.
     */
    public static boolean grosserSprungNachUnten(long alt, long neu) {
        return 2 * neu < alt;
    }

    /** Ob die Aenderung in irgendeine Richtung ueber den Faktor 2 hinausgeht. */
    public static boolean grosserSprung(long alt, long neu) {
        return grosserSprungNachOben(alt, neu) || grosserSprungNachUnten(alt, neu);
    }

    /**
     * Der Text, mit dem ein solcher Sprung bestaetigt wird, etwa {@code "25000 -> 100000"}.
     *
     * <p>Der alte Wert steht mit drin, damit die Bestaetigung nicht fuer jede kuenftige
     * Aenderung weitergilt: Wer sie einmal eintraegt und spaeter eine andere Zahl setzt,
     * bekommt die Rueckfrage erneut.
     */
    public static String sprungText(long alt, long neu) {
        return alt + " -> " + neu;
    }

    /**
     * Ob der eingetragene Text genau diesen Sprung freigibt.
     *
     * @param eingetragen was in der Datei steht, darf {@code null} sein
     */
    public static boolean bestaetigt(String eingetragen, long alt, long neu) {
        return eingetragen != null && eingetragen.trim().equals(sprungText(alt, neu));
    }
}
