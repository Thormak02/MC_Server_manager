package de.freimarken.paper;

import java.util.Set;

/**
 * Was ein Spieler auf dem Rundgang liest - die FESTEN Saetze (Plan 10.2).
 *
 * <p><b>Die Saetze der einzelnen Stationen stehen hier nicht.</b> Sie heissen
 * {@code rundgang.station.<name>.name} und {@code .text} und werden von
 * {@link Stationssatz} geholt - welche Stationen es gibt, steht in der
 * {@code config.yml}, und ein {@code enum} kann das nicht. Hier stehen die Saetze, die
 * den RAHMEN bilden: der Fortschritt und der Abschluss.
 *
 * <p><b>Beim ERSTEN Betreten einer Station liest ein Spieler drei Zeilen:</b> ihren Namen
 * mit dem Fortschritt ({@link #STATION}), ihren eigenen Text (aus
 * {@link Stationssatz.Teil#TEXT}) und - wenn es die letzte war - den Abschluss
 * ({@link #ABGESCHLOSSEN}). Beim zweiten Betreten keine: Ein Stationstext bei jedem
 * Schritt durch dieselbe Tuer waere eine Plage.
 */
enum RundgangSatz implements Satz {

    /**
     * Die Kopfzeile einer gerade entdeckten Station - mit dem Fortschritt.
     *
     * <p>{@code {station}} ist ihr Name (aus {@link Stationssatz.Teil#NAME}),
     * {@code {gesehen}} und {@code {alle}} sind die Zahlen. BEIDE Zahlen, und nicht nur
     * die erste: "Station 2" sagt nicht, wie weit es noch ist - und der Rundgang ist ein
     * Einstieg, bei dem man wissen soll, wann er fertig ist.
     *
     * <p>{@code Art.GEZAEHLT} waere falsch: Gezaehlt wird hier nichts in Einzahl und
     * Mehrzahl, sondern zwei Zahlen stehen in einem Satz. Die Form "2 von 6" braucht
     * keine zweite Fassung.
     */
    STATION("rundgang.station-entdeckt", Art.SATZ, "station", "gesehen", "alle"),

    /**
     * Der Abschluss - die letzte Station ist gesehen.
     *
     * <p>Er kommt GENAU EINMAL, und dafuer sorgt {@code Rundgang.Bescheid}: Der Abschluss
     * ist der Augenblick, in dem eine Rangbedingung umschlaegt, und eine zweite Meldung
     * sieht aus, als waere die erste nicht gewirkt.
     *
     * <p>MEHRZEILIG erlaubt: Hier gehoert hin, was ein Neuling als Naechstes tun soll -
     * Plan 10 nennt "ein sichtbares naechstes Ziel". Das ist mehr als eine Zeile wert.
     */
    ABGESCHLOSSEN("rundgang.abgeschlossen", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    RundgangSatz(String pfad, Art art, String... platzhalter) {
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
        // Keiner von beiden: Eine leere Stationszeile waere ein Schritt, der nichts sagt,
        // und ein leerer Abschluss ein Rundgang, der stumm endet. Leer erlaubt ist im
        // ganzen Projekt nur, was gar keine Zeile SENDET.
        return false;
    }
}
