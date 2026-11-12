package de.freimarken.kern;

/**
 * Eine abgesteckte Ecke - zwei Blockkoordinaten, ohne Hoehe.
 *
 * <p><b>Ein eigener Typ und keine zwei {@code int}</b>: Ein Zuschnitt nimmt VIER Zahlen,
 * und zwei Paare in einer Reihe sind genau die Stelle, an der jemand X und Z vertauscht.
 * Mit zwei Ecken statt vier Zahlen ist der Tausch nicht mehr moeglich, weil es nichts zu
 * vertauschen gibt.
 *
 * <p><b>Ohne Hoehe</b>, denn ein Grundstueck gehoert von der Weltunterkante bis zum
 * Himmel. Eine Hoehengrenze waere ein zweiter Besitzer ueber dem ersten - und die Frage,
 * wem der Block an der Grenze gehoert, haette dann keine Antwort, die ein Spieler erraten
 * kann.
 *
 * <p><b>Oben im Paket und nicht in einem der zwei Buecher</b>, und das ist ein Nachtrag:
 * Erst stand sie in {@link Grundstuecksbuch}, dann noch einmal in {@link Absteckbuch} -
 * zwei gleiche Typen, zwischen denen jeder Aufrufer von Hand haette umsetzen muessen. Die
 * Absteckung liefert sie, das Grundstuecksbuch nimmt sie; dass es DIESELBE ist, ist die
 * ganze Naht zwischen beiden.
 */
public record Blockecke(int x, int z) {
}
