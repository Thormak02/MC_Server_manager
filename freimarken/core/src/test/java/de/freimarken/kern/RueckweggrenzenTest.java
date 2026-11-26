package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Grenzen des Rueckwegs - und die eine Stelle, an der 0 etwas anderes heisst.
 *
 * <p><b>Die wichtigste Zusage dieser Datei ist der Unterschied zwischen
 * {@link Rueckweggrenzen#brauchbarerRadius} und
 * {@link Rueckweggrenzen#erlaubterRadius}</b>: Jene Frage gilt der EINSTELLUNG, wo eine 0
 * die stille Form von "der Befehl findet nie etwas" waere; diese dem GETIPPTEN Wert, wo 0
 * genau einen Block meint. Ein Pruefer fuer beides waere eine Zahl, die an einer der
 * beiden Stellen falsch ist.
 */
@DisplayName("Rueckweggrenzen")
class RueckweggrenzenTest {

    @Test
    @DisplayName("Die Vorgabe ist die enge Richtung und in sich brauchbar")
    void vorgabeIstEngUndBrauchbar() {
        Rueckweggrenzen vorgabe = Rueckweggrenzen.VORGABE;

        assertEquals(Rueckweggrenzen.VORGABE_RADIUS, vorgabe.hoechsterRadius());
        assertEquals(Rueckweggrenzen.VORGABE_MINUTEN, vorgabe.hoechsteMinuten());
        assertEquals(Rueckweggrenzen.VORGABE_BLOECKE, vorgabe.hoechsteBloecke());
        assertTrue(vorgabe.hoechsterRadius() < Rueckweggrenzen.HOECHSTER_RADIUS,
                "die Vorgabe darf nicht an der Obergrenze liegen - sonst waere sie"
                        + " keine enge Richtung");
        assertTrue(vorgabe.hoechsteBloecke() < Rueckweggrenzen.HOECHSTE_BLOECKE);
    }

    @Test
    @DisplayName("Eine 0 in der EINSTELLUNG ist keine Grenze")
    void nullInDerEinstellungIstKeineGrenze() {
        assertFalse(Rueckweggrenzen.brauchbarerRadius(0));
        assertFalse(Rueckweggrenzen.brauchbareMinuten(0));
        assertFalse(Rueckweggrenzen.brauchbareBloecke(0));
        assertThrows(IllegalArgumentException.class,
                () -> new Rueckweggrenzen(0, 1440, 5000));
        assertThrows(IllegalArgumentException.class,
                () -> new Rueckweggrenzen(50, 0, 5000));
        assertThrows(IllegalArgumentException.class,
                () -> new Rueckweggrenzen(50, 1440, 0));
    }

    @Test
    @DisplayName("Eine 0 im BEFEHL meint genau einen Block")
    void nullImBefehlMeintEinenBlock() {
        // DER UNTERSCHIED, UM DEN ES GEHT. Ein gemeinsamer Pruefer fuer Einstellung und
        // getippten Wert waere an einer der beiden Stellen falsch: Hier ist 0 der eine
        // Block in der Mitte, in der config.yml waere es "findet nie etwas".
        Rueckweggrenzen grenzen = Rueckweggrenzen.VORGABE;

        assertTrue(grenzen.erlaubterRadius(0));
        assertFalse(grenzen.erlaubteMinuten(0),
                "ein Fenster von 0 Minuten ist dagegen keines - es gibt keinen"
                        + " Augenblick, in dem etwas geschehen sein koennte");
    }

    @Test
    @DisplayName("Ueber der Obergrenze wird geworfen, genau darauf nicht")
    void ueberDerObergrenzeWirdGeworfen() {
        assertTrue(Rueckweggrenzen.brauchbarerRadius(Rueckweggrenzen.HOECHSTER_RADIUS));
        assertFalse(Rueckweggrenzen.brauchbarerRadius(
                Rueckweggrenzen.HOECHSTER_RADIUS + 1));
        assertTrue(Rueckweggrenzen.brauchbareBloecke(Rueckweggrenzen.HOECHSTE_BLOECKE));
        assertFalse(Rueckweggrenzen.brauchbareBloecke(
                Rueckweggrenzen.HOECHSTE_BLOECKE + 1));
        assertTrue(Rueckweggrenzen.brauchbareMinuten(Rueckweggrenzen.HOECHSTE_MINUTEN));
        assertFalse(Rueckweggrenzen.brauchbareMinuten(
                Rueckweggrenzen.HOECHSTE_MINUTEN + 1));
    }

    @Test
    @DisplayName("Die eingestellte Grenze gilt, nicht die des Programms")
    void dieEingestellteGrenzeGilt() {
        // Der Fall, der sonst stumm durchfiele: Ein Betreiber, der 10 eintraegt, soll
        // bei 11 abgewiesen werden - und nicht erst bei 251. Die Frage am WERT ist genau
        // dafuer da.
        Rueckweggrenzen eng = new Rueckweggrenzen(10, 60, 100);

        assertTrue(eng.erlaubterRadius(10));
        assertFalse(eng.erlaubterRadius(11));
        assertTrue(eng.erlaubteMinuten(60));
        assertFalse(eng.erlaubteMinuten(61));
    }

    @Test
    @DisplayName("Der Bericht nennt alle drei Zahlen")
    void berichtNenntAlleDreiZahlen() {
        // Aus den Daten gebaut und nicht abgeschrieben: Der Betreiber soll im Protokoll
        // lesen, was WIRKLICH gilt. Eine Zahl, die der Bericht verschweigt, ist eine
        // Einstellung, von der niemand weiss, dass sie wirkt.
        String bericht = new Rueckweggrenzen(7, 8, 9).wieEingetragen();

        assertTrue(bericht.contains("7"), bericht);
        assertTrue(bericht.contains("8"), bericht);
        assertTrue(bericht.contains("9"), bericht);
    }

    @Test
    @DisplayName("Negative Zahlen sind keine Grenzen")
    void negativeZahlenSindKeineGrenzen() {
        assertFalse(Rueckweggrenzen.brauchbarerRadius(-1));
        assertFalse(Rueckweggrenzen.VORGABE.erlaubterRadius(-1),
                "auch im BEFEHL nicht - ein negativer Radius ist kein Kasten");
        assertFalse(Rueckweggrenzen.VORGABE.erlaubteMinuten(-1));
    }
}
