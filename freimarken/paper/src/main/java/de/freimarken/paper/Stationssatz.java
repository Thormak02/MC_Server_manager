package de.freimarken.paper;

import java.util.Locale;
import java.util.Set;

/**
 * Der Name oder der Text EINER Rundgangsstation - Saetze ohne Platz in einer Aufzaehlung.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Muenzname}, {@link Berufsname} und
 * {@link Aufgabenname}: Welche Stationen es gibt, steht in der {@code config.yml}, und der
 * Betreiber darf dort eine hinzufuegen. Ein {@code enum} kann das nicht - es waere ein
 * Satz, den niemand anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p><b>ZWEI Teile je Station</b>, und darum ein Record mit einem Teil und nicht zwei
 * Klassen: Der NAME wird in andere Saetze eingesetzt ("Station 2 von 6: {station}"), der
 * TEXT ist die eigene Zeile der Station ("hier waehlst du einen Beruf mit /beruf"). Zwei
 * Records waeren zweimal dieselbe Vorsilbe und zweimal dieselbe Pruefung.
 *
 * <p><b>Und darum sind diese Saetze nicht in {@code Sprachschluessel.alle()}</b>: Jene
 * Liste verlangt, dass JEDER Satz in der ausgelieferten Vorlage steht, und eine Station,
 * die der Betreiber erst morgen eintraegt, kann dort nicht stehen. Geprueft wird
 * stattdessen umgekehrt - beim Start wird gefragt, ob jede Station der
 * {@code config.yml} ihre zwei Zeilen hat.
 *
 * <p><b>Eine Station ohne Zeile faellt NICHT weg</b>, wie beim Katalogposten und anders
 * als beim Beruf: Sie bleibt begehbar und zeigt ihren Pfad. Ein haesslicher Name ist der
 * kleinere Schaden - eine weggefallene Station waere ein Rundgang, den niemand abschliessen
 * kann, und der Spieler suchte den Fehler bei sich.
 *
 * @param station der Name der Station, kleingeschrieben wie in der Datei
 * @param teil    welcher der zwei Saetze gemeint ist
 */
record Stationssatz(String station, Stationssatz.Teil teil) implements Satz {

    /** Die Vorsilbe - sie steht hier und in {@link Sprachschluessel} nirgends sonst. */
    static final String VORSILBE = "rundgang.station.";

    /** Welche zwei Saetze eine Station hat. */
    enum Teil {

        /**
         * Wie die Station heisst - ein WORT, das in andere Saetze eingesetzt wird.
         *
         * <p>{@code Art.WORT} aus demselben Grund wie beim Berufs- und beim Postennamen:
         * Ein Farbtag darin ueberschriebe die Farbe der Zeile, in der er landet, und zwar
         * je Zeile anders.
         */
        NAME("name", Art.WORT),

        /**
         * Was man an dieser Station tut - die eigene Zeile der Station.
         *
         * <p>{@code Art.SATZ} und mehrzeilig erlaubt: Eine Station, die zwei Dinge
         * erklaert, braucht zwei Zeilen. Im Chat wird jede eine eigene.
         */
        TEXT("text", Art.SATZ);

        private final String nachsilbe;
        private final Art art;

        Teil(String nachsilbe, Art art) {
            this.nachsilbe = nachsilbe;
            this.art = art;
        }

        String nachsilbe() {
            return nachsilbe;
        }

        Art art() {
            return art;
        }
    }

    Stationssatz {
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Ein Stationssatz ohne Station");
        }
        if (teil == null) {
            throw new IllegalArgumentException("Ein Stationssatz ohne Teil - Name oder"
                    + " Text, ein drittes gibt es nicht");
        }
        station = station.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String pfad() {
        return VORSILBE + station + "." + teil.nachsilbe();
    }

    @Override
    public Art art() {
        return teil.art();
    }

    @Override
    public Set<String> platzhalter() {
        // KEINER, auch nicht {station}: Der Name der Station steht im NAME-Satz, und ihn
        // in den TEXT einzusetzen hiesse, dass der Betreiber ihn dort doppelt liest.
        return Set.of();
    }

    /**
     * Ob dieser Pfad ein Stationssatz ist - fuer {@link Sprachschluessel}.
     *
     * <p>Ohne diese Frage waere eine Zeile {@code rundgang.station.buergeramt.name} in
     * der Betreiberdatei ein UNBEKANNTER Schluessel, und der Leser klagte darueber: Der
     * Betreiber koennte die Zeile, die das Programm von ihm verlangt, gar nicht setzen.
     * Dieselbe Bauart wie bei {@link Muenzname#passt}.
     */
    static boolean passt(String pfad) {
        if (pfad == null || !pfad.startsWith(VORSILBE)) {
            return false;
        }
        String rest = pfad.substring(VORSILBE.length());
        int punkt = rest.lastIndexOf('.');
        if (punkt <= 0) {
            return false;
        }
        String station = rest.substring(0, punkt);
        String nachsilbe = rest.substring(punkt + 1);
        if (!station.matches(de.freimarken.kern.Rundgangsstation.MUSTER)
                || station.length()
                        > de.freimarken.kern.Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE) {
            return false;
        }
        for (Teil teil : Teil.values()) {
            if (teil.nachsilbe().equals(nachsilbe)) {
                return true;
            }
        }
        return false;
    }

    /** Aus einem passenden Pfad wieder der Satz - siehe {@link #passt}. */
    static Stationssatz ausPfad(String pfad) {
        String rest = pfad.substring(VORSILBE.length());
        int punkt = rest.lastIndexOf('.');
        String nachsilbe = rest.substring(punkt + 1);
        for (Teil teil : Teil.values()) {
            if (teil.nachsilbe().equals(nachsilbe)) {
                return new Stationssatz(rest.substring(0, punkt), teil);
            }
        }
        throw new IllegalArgumentException("Kein Stationssatz: " + pfad
                + " - wer hierher kommt, hat passt() nicht gefragt.");
    }

    @Override
    public boolean darfLeerSein() {
        // Der NAME nicht - er wird in eine Zeile eingesetzt, und ein leerer Name ist eine
        // Luecke mitten im Satz. Der TEXT auch nicht: Eine Station ohne Text ist ein
        // Gebaeude, in dem nichts geschieht, und genau das soll der Rundgang nicht sein.
        return false;
    }
}
