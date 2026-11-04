package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Schranken der Zeitwerte - und die Zusage, dass die Vorgabe im Programm genau das ist,
 * was die Datei ausliefert.
 */
class ZeitwerteTest {

    private static Zeitwerte satz(long taktSekunden, long untaetigMinuten) {
        return new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(taktSekunden),
                Duration.ofMinutes(untaetigMinuten));
    }

    @Test
    @DisplayName("Die Vorgabe im Programm ist genau das, was die Datei ausliefert")
    void vorgabeStimmt() {
        // Der Rueckfall muss da sein, wo er nicht fehlen kann: saveDefaultConfig()
        // schreibt eine vorhandene Datei nie um.
        assertEquals(ZoneId.of("Europe/Berlin"), Zeitwerte.VORGABE.zone());
        assertEquals(60L, Zeitwerte.VORGABE.taktSekunden());
        assertEquals(10L, Zeitwerte.VORGABE.untaetigMinuten());
    }

    @Test
    @DisplayName("Der Takt darf nicht kuerzer als zehn und nicht laenger als 600 Sekunden sein")
    void taktgrenzen() {
        assertEquals(10L, satz(10L, 10L).taktSekunden());
        assertEquals(600L, satz(600L, 10L).taktSekunden());

        assertThrows(IllegalArgumentException.class, () -> satz(9L, 10L));
        assertThrows(IllegalArgumentException.class, () -> satz(601L, 10L));
        assertThrows(IllegalArgumentException.class, () -> satz(0L, 10L));
        assertThrows(IllegalArgumentException.class, () -> satz(-60L, 10L));
    }

    @Test
    @DisplayName("Eine Untaetigkeitsgrenze von 0 ist erlaubt und heisst: alles zaehlt")
    void nullIstDerAusschalter() {
        // Anders als bei parzellen.hoechstzahl, wo eine 0 verboten ist: Dort hiesse sie
        // "niemand darf eine Parzelle", also etwas, das niemand tippen wollte. Hier ist sie
        // der einzige ehrliche Weg, die Regel abzuschalten.
        Zeitwerte aus = satz(60L, 0L);

        assertEquals(0L, aus.untaetigMinuten());
        assertTrue(aus.untaetigkeitZaehltMit());
        assertFalse(Zeitwerte.VORGABE.untaetigkeitZaehltMit());
    }

    @Test
    @DisplayName("Mehr als ein Tag Untaetigkeit ist keine Grenze mehr")
    void untaetigkeitsgrenzen() {
        assertEquals(1_440L, satz(60L, 1_440L).untaetigMinuten());

        assertThrows(IllegalArgumentException.class, () -> satz(60L, 1_441L));
        assertThrows(IllegalArgumentException.class, () -> satz(60L, -1L));
    }

    @Test
    @DisplayName("Eine Grenze unter der Taktlaenge kommt nicht zustande")
    void grenzeMussDenTaktDecken() {
        // Der Fehlerfall: Beide Zahlen liegen in ihrem Bereich, zusammen werfen sie aktiv
        // gespielte Zeit weg. Die Grenze ist eine Stufe und kein Schnitt - bei 600 s Takt
        // und 1 min Grenze faellt der GANZE Takt, also 540 echte Sekunden, und zwar stumm.
        assertThrows(IllegalArgumentException.class, () -> satz(600L, 1L));
        assertThrows(IllegalArgumentException.class, () -> satz(120L, 1L));

        // Genau eine Taktlaenge genuegt, und aufgerundet wird zugunsten des Spielers: Zu
        // einem Takt von 90 s passen 2 Minuten, 1 Minute nicht.
        assertEquals(600L, satz(600L, 10L).taktSekunden());
        assertEquals(1L, satz(60L, 1L).untaetigMinuten());
        assertFalse(Zeitwerte.brauchbareGrenzeZumTakt(90L, 1L));
        assertTrue(Zeitwerte.brauchbareGrenzeZumTakt(90L, 2L));

        // Die 0 bleibt der Ausschalter und passt zu jedem Takt - sonst gaebe es fuer einen
        // langen Takt keinen Weg, die Regel abzuschalten.
        assertTrue(Zeitwerte.brauchbareGrenzeZumTakt(600L, 0L));
        assertTrue(satz(600L, 0L).untaetigkeitZaehltMit());

        assertFalse(Zeitwerte.brauchbareGrenzeZumTakt(600L, 1L),
                "der Leser muss das VOR dem Konstruktor fragen koennen, um klagen und auf"
                        + " die Vorgabe zurueckfallen zu koennen");
        assertTrue(Zeitwerte.brauchbareGrenzeZumTakt(600L, 10L));
    }

    @Test
    @DisplayName("Halbe Sekunden und halbe Minuten gibt es nicht - es gaebe zwei Wahrheiten")
    void nurGanzeSekundenUndGanzeMinuten() {
        // Ohne diese Schranke haelt der Satz zwei Darstellungen desselben Wertes, und
        // geprueft und angezeigt wird nur die abgeschnittene: Duration.ofSeconds(30) als
        // Grenze kam durch, weil toMinutes() 0 ergibt - danach war untaetigkeitZaehltMit()
        // false (die Regel ist an, mit Schwelle 30 s), untaetigMinuten() aber 0, und der
        // Spielertext schrieb "gezaehlt wird bis 0 min".
        assertThrows(IllegalArgumentException.class, () -> new Zeitwerte(
                Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60), Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new Zeitwerte(
                Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60), Duration.ofSeconds(90)));

        // Dasselbe beim Takt: 10,4 s bestand brauchbarerTakt(10), die Klemme rechnete danach
        // mit 10 x 10,4 s = 104 s und der Zeitgeber mit 200 Ticks, also 10 s.
        assertThrows(IllegalArgumentException.class, () -> new Zeitwerte(
                Zeitwerte.VORGABE_ZONE, Duration.ofMillis(10_400), Duration.ofMinutes(10)));

        // Eine negative Grenze unter einer Minute fiel durch dieselbe Luecke: toMinutes()
        // schneidet zur Null hin ab, also war -1 s dasselbe wie "Regel aus".
        assertThrows(IllegalArgumentException.class, () -> new Zeitwerte(
                Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60), Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("Die Pruefregeln nehmen die getippte Zahl, nicht ihre unteren 32 Bit")
    void pruefregelnNehmenLong() {
        // Dieselbe Begruendung wie bei brauchbareAnzahl: Wer vorher auf int verkuerzt,
        // prueft nicht mehr den getippten Wert.
        assertFalse(Zeitwerte.brauchbarerTakt(Integer.MAX_VALUE + 1L));
        assertFalse(Zeitwerte.brauchbareUntaetigkeit(Integer.MIN_VALUE - 1L));
        assertTrue(Zeitwerte.brauchbarerTakt(60L));
        assertTrue(Zeitwerte.brauchbareUntaetigkeit(0L));
    }

    @Test
    @DisplayName("Ohne Zone, Takt oder Grenze kommt kein Satz zustande")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> new Zeitwerte(null, Duration.ofSeconds(60), Duration.ofMinutes(10)));
        assertThrows(IllegalArgumentException.class,
                () -> new Zeitwerte(Zeitwerte.VORGABE_ZONE, null, Duration.ofMinutes(10)));
        assertThrows(IllegalArgumentException.class,
                () -> new Zeitwerte(Zeitwerte.VORGABE_ZONE, Duration.ofSeconds(60), null));
    }

    @Test
    @DisplayName("Auch UTC ist ein erlaubter Wert - er ist nur meistens der falsche")
    void utcIstErlaubt() {
        // Keine Zahlengrenze faengt UTC statt Europe/Berlin. Dagegen steht allein die
        // Klartextzeile aus Tagesgrenze.tageswechselText.
        Zeitwerte werte = new Zeitwerte(ZoneId.of("UTC"), Duration.ofSeconds(60),
                Duration.ofMinutes(10));

        assertEquals(ZoneId.of("UTC"), werte.zone());
    }
}
