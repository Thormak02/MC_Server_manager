package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Schranken der Berufswerte - und die Zusage, dass die Vorgabe im Programm genau das
 * ist, was die Einstellungsdatei ausliefert.
 *
 * <p>Gleiche Bauart und gleicher Grund wie {@link ZeitwerteTest}: Der Rueckfall muss da
 * sein, wo er nicht fehlen kann, denn {@code saveDefaultConfig()} schreibt eine vorhandene
 * Datei nie um.
 */
class BerufswerteTest {

    private static Berufswerte satz(long gebuehr, long stunden, int grund, int spezial) {
        return new Berufswerte(gebuehr, Duration.ofHours(stunden), grund, spezial);
    }

    @Test
    @DisplayName("Die Vorgabe im Programm ist genau das, was die Datei ausliefert")
    void vorgabeStimmt() {
        assertEquals(5_000L, Berufswerte.VORGABE.wechselgebuehr());
        assertEquals(Duration.ofHours(24L), Berufswerte.VORGABE.sperrfrist());
        assertEquals(2, Berufswerte.VORGABE.grundberufe());
        assertEquals(1, Berufswerte.VORGABE.spezialisierungen());
    }

    @Test
    @DisplayName("Die Pfade heissen so, wie sie in der Datei stehen")
    void pfadeStimmen() {
        // Sie stehen hier, damit der Leser der Datei und die Probe denselben Text lesen.
        // Ein Tippfehler im Pfad faellt sonst nie auf: Der Leser nimmt die Vorgabe, und
        // die eingetragene Zahl wirkt einfach nicht.
        assertEquals("berufe.wechselgebuehr", Berufswerte.PFAD_WECHSELGEBUEHR);
        assertEquals("berufe.sperrfrist-stunden", Berufswerte.PFAD_SPERRFRIST);
        assertEquals("berufe.hoechstens-grundberufe", Berufswerte.PFAD_GRUNDBERUFE);
        assertEquals("berufe.hoechstens-spezialisierungen",
                Berufswerte.PFAD_SPEZIALISIERUNGEN);
        assertEquals("berufe.liste", Berufswerte.PFAD_LISTE);
    }

    @Test
    @DisplayName("Die Wechselgebuehr darf 0 sein, aber nicht negativ und nicht ueberlaufen")
    void gebuehrgrenzen() {
        assertEquals(0L, satz(0L, 24L, 2, 1).wechselgebuehr());
        assertEquals(Berufswerte.HOECHSTE_WECHSELGEBUEHR,
                satz(Berufswerte.HOECHSTE_WECHSELGEBUEHR, 24L, 2, 1).wechselgebuehr());

        assertThrows(IllegalArgumentException.class, () -> satz(-1L, 24L, 2, 1));
        // Die Haelfte von Long.MAX_VALUE ist die Grenze, weil die Kasse die Gebuehr
        // addiert: Eine Zahl dicht am Rand liefe beim Buchen ueber und ergaebe ein
        // negatives Guthaben.
        assertThrows(IllegalArgumentException.class,
                () -> satz(Berufswerte.HOECHSTE_WECHSELGEBUEHR + 1L, 24L, 2, 1));
    }

    @Test
    @DisplayName("Eine Sperrfrist von 0 schaltet die Regel ab und sagt das auch")
    void keineSperrfrist() {
        Berufswerte ohne = satz(5_000L, 0L, 2, 1);

        assertEquals(0L, ohne.sperrfristStunden());
        assertFalse(ohne.sperrfristGilt(), "0 Stunden heisst: keine Frist");
    }

    @Test
    @DisplayName("Die Sperrfrist reicht bis 30 Tage, danach nicht mehr")
    void sperrfristgrenzen() {
        assertEquals(720L, satz(5_000L, Berufswerte.LAENGSTE_SPERRFRIST_STUNDEN, 2, 1)
                .sperrfristStunden());
        assertTrue(satz(5_000L, 1L, 2, 1).sperrfristGilt());

        assertThrows(IllegalArgumentException.class,
                () -> satz(5_000L, Berufswerte.LAENGSTE_SPERRFRIST_STUNDEN + 1L, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> satz(5_000L, -1L, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufswerte(5_000L, null, 2, 1));
    }

    @Test
    @DisplayName("Eine Sperrfrist wird in ganzen Stunden gemessen")
    void nurGanzeStunden() {
        // Die Datei nennt STUNDEN. Eine halbe Stunde koennte nur aus dem Programm kommen,
        // und dann stimmt die angezeigte Restzeit nicht mit der gerechneten ueberein -
        // der Spieler sieht 0 Stunden und darf trotzdem nicht.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufswerte(5_000L, Duration.ofMinutes(90L), 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufswerte(5_000L, Duration.ofSeconds(1L), 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufswerte(5_000L, Duration.ofHours(1L).plusNanos(1L), 2, 1));
    }

    @Test
    @DisplayName("Mindestens ein Grundberuf - bei 0 koennte niemand etwas waehlen")
    void grundberufsplaetze() {
        assertEquals(1, satz(5_000L, 24L, 1, 1).grundberufe());

        assertThrows(IllegalArgumentException.class, () -> satz(5_000L, 24L, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> satz(5_000L, 24L, -1, 1));
    }

    @Test
    @DisplayName("Keine Spezialisierung ist erlaubt, eine negative Zahl nicht")
    void spezialisierungsplaetze() {
        // Die 0 ist ein echter Betriebsfall: Ein Server ohne Spezialisierungen laesst die
        // Zahl auf 0 und braucht das Recht nicht zu verteilen.
        assertEquals(0, satz(5_000L, 24L, 2, 0).spezialisierungen());

        assertThrows(IllegalArgumentException.class, () -> satz(5_000L, 24L, 2, -1));
    }

    @Test
    @DisplayName("Mehr Plaetze als Berufe in der Liste passt nicht zusammen")
    void plaetzePassenZurListe() {
        // Kein Wurf, sondern eine Frage: Der Leser der Datei meldet es und nimmt die
        // kleinere Zahl. Ein Wurf startete den Server nicht mehr, nur weil jemand einen
        // Beruf aus der Liste genommen hat.
        assertTrue(Berufswerte.passtZurListe(2L, 3));
        assertTrue(Berufswerte.passtZurListe(3L, 3));
        assertFalse(Berufswerte.passtZurListe(4L, 3));
        assertTrue(Berufswerte.passtZurListe(0L, 0));
    }

    @Test
    @DisplayName("Die Fragen fuer den Leser der Datei antworten wie die Schranken")
    void fragenUndSchrankenStimmenUeberein() {
        // Zwei Wege zur selben Tat: Der Leser fragt, der Satz wirft. Antwortete die Frage
        // anders als die Schranke, meldete die Datei eine brauchbare Zahl und der Bau
        // des Satzes brachte den Start zum Stehen.
        assertTrue(Berufswerte.brauchbareWechselgebuehr(0L));
        assertFalse(Berufswerte.brauchbareWechselgebuehr(-1L));
        assertTrue(Berufswerte.brauchbareSperrfrist(720L));
        assertFalse(Berufswerte.brauchbareSperrfrist(721L));
        assertTrue(Berufswerte.brauchbareAnzahl(1L));
        assertFalse(Berufswerte.brauchbareAnzahl(0L));
        assertTrue(Berufswerte.brauchbareAnzahlOderNull(0L));
        assertFalse(Berufswerte.brauchbareAnzahlOderNull(-1L));
        // Eine Zahl jenseits von int kommt aus einer Datei, in der jemand 99999999999
        // getippt hat - sie darf nicht stillschweigend abgeschnitten werden.
        assertFalse(Berufswerte.brauchbareAnzahl(Integer.MAX_VALUE + 1L));
        assertFalse(Berufswerte.brauchbareAnzahlOderNull(Integer.MAX_VALUE + 1L));
    }
}
