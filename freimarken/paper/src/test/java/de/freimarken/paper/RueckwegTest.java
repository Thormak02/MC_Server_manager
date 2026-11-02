package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Rueckweg - ohne Server.
 *
 * <p>Moeglich, weil die Klasse nichts prueft: Ein {@code Location} ohne Welt genuegt, um
 * das Merken, das Tauschen und das Vergessen zu belegen. Ob der Ort noch taugt,
 * entscheidet der Aufrufer unmittelbar vor dem Teleport - und das ist auch die einzige
 * Stelle, an der es stimmen kann.
 */
class RueckwegTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final UUID BERT = UUID.nameUUIDFromBytes("bert".getBytes());

    private static Location ort(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    @Test
    @DisplayName("Das Hin und Her entsteht aus Lesen und Merken, nicht aus einem Tausch")
    void hinUndHer() {
        // Eine eigene Tauschmethode hatte ich zuerst gebaut. Sie waere eine zweite
        // Stelle, die dasselbe tut: Die Reise merkt vor JEDEM Teleport den jetzigen Ort,
        // also legt sie beim Zurueckkehren von selbst den Ausgangspunkt hin. Hier steht
        // der Ablauf, den Reise und der Befehl zusammen ergeben.
        var rueckweg = new Rueckweg();
        Location zuhause = ort(10, 64, 10);
        Location kornmark = ort(500, 70, -200);

        rueckweg.merken(ANNA, zuhause);                      // Reise nach Kornmark
        Location erstesZiel = rueckweg.letzter(ANNA).orElseThrow();   // /zurueck liest
        rueckweg.merken(ANNA, kornmark);                      // und Reise merkt wieder

        assertEquals(zuhause, erstesZiel, "erstes /zurueck fuehrt nach Hause");
        assertEquals(kornmark, rueckweg.letzter(ANNA).orElseThrow(),
                "zweites /zurueck fuehrt zurueck nach Kornmark");
    }

    @Test
    @DisplayName("Ohne gemerkten Ort kommt nichts zurueck")
    void ohneGemerktenOrt() {
        var rueckweg = new Rueckweg();

        assertTrue(rueckweg.letzter(ANNA).isEmpty());
        assertEquals(0, rueckweg.gemerkt(), "und nichts wird dabei angelegt");
    }

    @Test
    @DisplayName("Der gemerkte Ort ist eine Abschrift, kein Verweis")
    void abschriftUndKeinVerweis() {
        // Ein Location ist veraenderlich, und Bukkit gibt bei getLocation() je nach
        // Aufruf dasselbe Objekt zurueck. Ohne clone() wanderte der gemerkte Ort mit dem
        // Spieler mit, und /back fuehrte dorthin, wo er gerade steht.
        var rueckweg = new Rueckweg();
        Location wandernd = ort(10, 64, 10);
        rueckweg.merken(ANNA, wandernd);

        wandernd.setX(999);

        assertEquals(10, rueckweg.letzter(ANNA).orElseThrow().getX(),
                "der gemerkte Ort bleibt, wo er war");
    }

    @Test
    @DisplayName("Auch das Herausgegebene ist eine Abschrift")
    void herausgegebenesIstAbschrift() {
        var rueckweg = new Rueckweg();
        rueckweg.merken(ANNA, ort(10, 64, 10));

        Location gelesen = rueckweg.letzter(ANNA).orElseThrow();
        gelesen.setY(1);

        assertEquals(64, rueckweg.letzter(ANNA).orElseThrow().getY());
    }

    @Test
    @DisplayName("Jeder Spieler hat seinen eigenen Rueckweg")
    void jederSeinen() {
        var rueckweg = new Rueckweg();
        rueckweg.merken(ANNA, ort(1, 64, 1));
        rueckweg.merken(BERT, ort(2, 64, 2));

        assertEquals(1, rueckweg.letzter(ANNA).orElseThrow().getX());
        assertEquals(2, rueckweg.letzter(BERT).orElseThrow().getX());
        assertEquals(2, rueckweg.gemerkt());
    }

    @Test
    @DisplayName("Beim Abmelden wird vergessen")
    void vergessenBeimAbmelden() {
        // Das haelt die Karte so gross wie die Zahl der anwesenden Spieler. Ohne das
        // waechst sie mit jedem Spieler, der je gereist ist, und ein Serverlauf dauert
        // Wochen.
        var rueckweg = new Rueckweg();
        rueckweg.merken(ANNA, ort(1, 64, 1));
        rueckweg.merken(BERT, ort(2, 64, 2));

        rueckweg.vergessen(ANNA);

        assertTrue(rueckweg.letzter(ANNA).isEmpty());
        assertEquals(1, rueckweg.gemerkt());
    }

    @Test
    @DisplayName("Ein neuer Ort ersetzt den alten")
    void neuerOrtErsetzt() {
        var rueckweg = new Rueckweg();
        rueckweg.merken(ANNA, ort(1, 64, 1));
        rueckweg.merken(ANNA, ort(2, 64, 2));

        assertEquals(2, rueckweg.letzter(ANNA).orElseThrow().getX());
        assertEquals(1, rueckweg.gemerkt(), "und nicht zwei Eintraege");
    }
}
