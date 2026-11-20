package de.freimarken.paper;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die EINE Stelle, an der die Zeile aus {@code hinweise.rang-werbung} entsteht.
 *
 * <p>Der Satz selbst ist keiner dieses Plugins: Er steht in der {@code config.yml} und
 * gehoert dem Betreiber. Aus der Sprachdatei kommt nur die FARBE - je Bereich ein
 * Schluessel ({@code parzelle.rang-werbung}, {@code werder.rang-werbung},
 * {@code wolke.rang-werbung}, {@code freistadt.rang-werbung}), damit der Betreiber den
 * Hinweis an der Freistadt anders setzen kann als an der Parzelle.
 *
 * <p><b>Warum es diese Klasse gibt</b> [20.11.2026, Schritt 12 Teil H]: Bis dahin stand
 * die Regel in {@code ParzellenBefehle.werbungZeilen} und galt fuer zwei der fuenf
 * Grenzen. Mit Teil H haengen alle fuenf am Rang, und damit gab es drei weitere Absagen,
 * an denen dieselbe Zeile haengen muss. Eine Abschrift je Bereich waere genau der Fehler,
 * den dieses Projekt schon mehrfach hatte: Drei von vier Wegen kennen einen Hinweis, und
 * auf dem vierten wirkt er wie ein Zufall.
 *
 * <p><b>Die Regel in einem Satz:</b> Eine leere Einstellung gibt KEINE Zeile. Nicht eine
 * leere - eine leere Zeile im Chat sieht wie ein Fehler aus, und eine Absage, auf die ein
 * Absatz folgt, liest sich wie eine abgeschnittene Nachricht.
 *
 * <p>Angehaengt wird die Zeile dort, wo eine Grenze ABWEIST (und bei den Parzellen
 * ausserdem in den zwei Ansichten, die "du bist am Limit" sagen - das steht dort). In
 * Gelb und nicht in Rot: Die Absage darueber ist die Nachricht, das hier ist das
 * Angebot. Zwei rote Zeilen hintereinander liest niemand als zwei Dinge.
 */
final class Rangwerbung {

    private Rangwerbung() {
    }

    /**
     * Die Werbezeile - einmal, wenn sie gesetzt ist, sonst keine.
     *
     * @param satz    der Schluessel des Bereichs, der die Farbe traegt
     * @param werbung der Wortlaut aus {@code hinweise.rang-werbung}, moeglicherweise leer
     */
    static List<Component> zeilen(Sprache sprache, Satz satz, String werbung) {
        if (werbung == null || werbung.isEmpty()) {
            return List.of();
        }
        return sprache.bau(satz, 1L, Einsatz.von("werbung", werbung));
    }

    /**
     * Eine Absage und darunter die Werbezeile.
     *
     * <p>Fuer die Zweige, deren Antwort EIN Ausdruck ist - ein {@code switch} ueber alle
     * Bescheide. Dort waere eine Liste zum Anhaengen ein Block, und der {@code switch}
     * verloere seinen Riegel: Er uebersetzt sich nur deshalb nicht, wenn ein Bescheid
     * fehlt, weil jeder Zweig einen WERT liefert.
     */
    static List<Component> darunter(Sprache sprache, Satz satz, String werbung,
                                    List<Component> absage) {
        List<Component> werbezeilen = zeilen(sprache, satz, werbung);
        if (werbezeilen.isEmpty()) {
            return absage;
        }
        List<Component> zusammen = new ArrayList<>(absage);
        zusammen.addAll(werbezeilen);
        return List.copyOf(zusammen);
    }
}
