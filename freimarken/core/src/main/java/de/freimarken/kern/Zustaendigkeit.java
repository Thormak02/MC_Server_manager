package de.freimarken.kern;

import java.util.Optional;

/**
 * Wem gehoert dieser Block - OHNE einen Spieler zu fragen.
 *
 * <p><b>Die Frage, die der Bauschutz nicht stellen kann.</b> {@code darfHandeln} und die
 * vier {@code darfBauen} fragen "darf DIESER SPIELER hier handeln" und brauchen dafuer
 * einen {@code Player}. Eine TNT-Ladung hat keinen - der Zuender ist laengst weg oder war
 * ein Creeper -, ein Lavastrom hat keinen, ein Kolben schon gar nicht. Fuer die
 * Weltregeln ist die Frage darum eine andere: "gehoert dieser Block jemandem, und wem".
 *
 * <p><b>Eine Umsetzung je Bauwelt</b>, und sie kommt aus demselben Buch, das auch
 * {@code darfBauen} beantwortet - sonst gaebe es zwei Vorstellungen davon, wem ein Block
 * gehoert, und eine davon waere eines Tages falsch:
 *
 * <ul>
 *   <li>Marktstadt - {@link Besitzbuch#zustaendigerBesitz}, Art {@code parzelle}</li>
 *   <li>Freistaedte - {@link Freistadtbuch#zustaendigeEinheit}, Art {@code grundstueck}
 *       oder {@code stadtfeld}; das Grundstueck STICHT die Stadt</li>
 *   <li>Hafenwerder - {@code Inselbuch.inselAn}, Art {@code insel}</li>
 *   <li>Wolkenwerder - {@code Wolkenbuch.inselAn}, Art {@code wolke}</li>
 * </ul>
 *
 * <p><b>Eine Welt kann auch GANZ gebaut sein.</b> Altfurt ist die gebaute Stadt: kein
 * Raster, keine Einheiten, Spielmodus Abenteuer. Dort gibt es keine Grenze, die
 * ueberschritten werden koennte - und deshalb ist die ehrliche Antwort kein leeres
 * {@link Optional}, sondern ein Verbot. Darum {@link #ganzGebaut()}: Ohne diese Frage
 * waere eine ganz gebaute Welt ungeschuetzt, weil nirgends jemandem etwas "gehoert".
 *
 * <p><b>Eine Stoerung der Ablage wird NICHT gefangen.</b> Alle vier Buecher werfen
 * durch - bei {@code Grundstuecksbuch.anStelle} steht es ausdruecklich im Javadoc ("die
 * Wache faengt das und antwortet NEIN"). Diese Schnittstelle traegt dieselbe
 * Entscheidung weiter: Der Aufrufer faengt und bricht ab. Ein Netzfehler darf keinen
 * Krater erlauben.
 */
public interface Zustaendigkeit {

    /**
     * Die Einheit, die diesen Block regelt - oder leer fuer Weg, Mauer, Meer und Leere.
     *
     * @param welt  der Weltschluessel, wie {@code World.key().asString()} ihn liefert
     * @param blockX Weltkoordinate
     * @param blockZ Weltkoordinate
     */
    Optional<Einheit> fuer(String welt, int blockX, int blockZ);

    /**
     * Gilt in dieser Welt ALLES als gebaut?
     *
     * <p>Dann ist jede Bewegung von Bloecken ein Eingriff, und es wird nicht nach einer
     * Grenze gefragt. Siehe den Kopf.
     */
    default boolean ganzGebaut() {
        return false;
    }

    /**
     * Eine Welt, in der alles gebaut ist - Altfurt.
     *
     * <p>{@link #fuer} gibt leer, und das ist kein Widerspruch: Die Frage "wem gehoert
     * das" hat dort keine Antwort, weil es keine Einheiten gibt. Entschieden wird ueber
     * {@link #ganzGebaut()}, und ein Aufrufer, der das uebersieht, bekommt kein stummes
     * "niemandem" - er bekommt eine Welt, in der er die Frage falsch stellt.
     */
    static Zustaendigkeit ganzGebauteWelt() {
        return new Zustaendigkeit() {
            @Override
            public Optional<Einheit> fuer(String welt, int blockX, int blockZ) {
                return Optional.empty();
            }

            @Override
            public boolean ganzGebaut() {
                return true;
            }

            @Override
            public String toString() {
                return "ganz gebaute Welt";
            }
        };
    }
}
