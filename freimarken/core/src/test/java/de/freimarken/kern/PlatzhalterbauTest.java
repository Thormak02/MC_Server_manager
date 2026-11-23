package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ein Platzhalterbau")
class PlatzhalterbauTest {

    private static Quader neun() {
        return new Quader(50, -61, 2, 58, -53, 10);
    }

    @Test
    @DisplayName("Der Name wird kleingeschrieben und beschnitten")
    void nameWirdNormalisiert() {
        Platzhalterbau bau = new Platzhalterbau("  BuergerAmt  ", neun(), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);

        assertEquals("buergeramt", bau.schluessel());
    }

    @Test
    @DisplayName("Unter drei Bloecken Kante hat ein Bau kein Inneres")
    void zuKleinWirdAbgewiesen() {
        // Aussen Wand, innen nichts: Die Tuer fuehrte in die Wand dahinter.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("klotz",
                        new Quader(0, -61, 0, 1, -60, 1), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL));

        assertTrue(wurf.getMessage().contains("2x2x2"), wurf.getMessage());
        assertTrue(wurf.getMessage().contains("kein Inneres"), wurf.getMessage());

        // JE ACHSE geprueft, nicht nur insgesamt: Eine flache Platte von 9x1x9 hat 81
        // Bloecke und trotzdem kein Inneres.
        assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("platte",
                        new Quader(0, -61, 0, 8, -61, 8), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL));
        assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("mauer",
                        new Quader(0, -61, 0, 0, -53, 8), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL));
    }

    @Test
    @DisplayName("Genau drei Bloecke Kante gehen - das ist die Grenze")
    void dreiGehtGerade() {
        Platzhalterbau klein = new Platzhalterbau("kiosk",
                new Quader(0, -61, 0, 2, -59, 2), Richtung.OST,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);

        assertEquals(27, klein.bloecke());
    }

    @Test
    @DisplayName("Ein Bau ohne Tuerseite ist ein Klotz")
    void ohneTuerseite() {
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("haus", neun(), null, Baustil.BACKSTEIN,
                        Dachart.ZIEGEL));

        assertTrue(wurf.getMessage().contains("Klotz"), wurf.getMessage());
    }

    @Test
    @DisplayName("Ein Bau ohne Stil oder ohne Dachart hat keine Baustoffe")
    void ohneStilOderDach() {
        IllegalArgumentException ohneStil = assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("haus", neun(), Richtung.OST, null,
                        Dachart.ZIEGEL));
        assertTrue(ohneStil.getMessage().contains("Baustoffe"), ohneStil.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("haus", neun(), Richtung.OST,
                        Baustil.BACKSTEIN, null));
    }

    @Test
    @DisplayName("Der Name muss zum Muster passen")
    void nameMussPassen() {
        for (String falsch : new String[]{"1haus", "haus!", "mein haus", "-haus", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Platzhalterbau(falsch, neun(), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL), falsch);
        }
        // Und die erlaubten gehen wirklich - sonst waere das Muster zu eng, und das
        // faellt an einer Verbotsprobe allein nie auf.
        for (String richtig : new String[]{"haus", "hafen-von-altfurt", "stand_2", "h1"}) {
            assertEquals(richtig,
                    new Platzhalterbau(richtig, neun(), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL).schluessel());
        }
    }

    @Test
    @DisplayName("Dieselbe Groessengrenze wie eine Rundgangsstation")
    void grenzeWieEineStation() {
        // Sie muss dieselbe sein: Jeder Bau soll eine Station werden koennen, und eine
        // Grenze, die nur an einer der zwei Stellen gilt, laesst einen Betreiber einen
        // Bau eintragen, der als Station abgewiesen wird.
        assertEquals(Rundgangsstation.HOECHSTE_BLOECKE, Platzhalterbau.HOECHSTE_BLOECKE);

        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Platzhalterbau("riese",
                        new Quader(0, -61, 0, 64, 3, 64), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL));
        assertTrue(wurf.getMessage().contains("Station"), wurf.getMessage());
    }

    @Test
    @DisplayName("Vertauschte Ecken sind kein Fehler - der Quader sortiert sie")
    void vertauschteEcken() {
        // Die Zusicherung gehoert hierher und nicht nur zu Quader: Ein Bau, der von/bis
        // vertauscht bekommt, muss trotzdem dastehen - der Betreiber hat sich vertippt.
        Platzhalterbau bau = new Platzhalterbau("haus",
                new Quader(58, -53, 10, 50, -61, 2), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);

        assertEquals(50, bau.bereich().vonX());
        assertEquals(-61, bau.bereich().vonY());
        assertEquals(2, bau.bereich().vonZ());
        assertEquals(9 * 9 * 9, bau.bloecke());
    }
}
