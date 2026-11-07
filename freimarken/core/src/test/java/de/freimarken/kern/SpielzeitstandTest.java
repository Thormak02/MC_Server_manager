package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpielzeitstandTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final LocalDate TAG = LocalDate.of(2026, 11, 3);

    @Test
    @DisplayName("Ein leerer Stand nennt den Tag und zweimal null")
    void leererStand() {
        Spielzeitstand stand = Spielzeitstand.leer(ANNA, TAG);

        assertEquals(0L, stand.sekundenGesamt());
        assertEquals(0L, stand.sekundenHeute());
        assertEquals(TAG, stand.tag());
    }

    @Test
    @DisplayName("Die Dauern sind dieselben Zahlen, nur anders geschrieben")
    void dauern() {
        Spielzeitstand stand = new Spielzeitstand(ANNA, TAG, 7_200L, 1_800L, 0L, 0L);

        assertEquals(Duration.ofHours(2), stand.gesamt());
        assertEquals(Duration.ofMinutes(30), stand.heute());
    }

    @Test
    @DisplayName("Heute kann nicht mehr sein als insgesamt - das waere ein Lesefehler")
    void heuteNieMehrAlsGesamt() {
        // Faellt hier auf und nicht in der Anzeige: Eine Tageszahl ueber der Gesamtzahl
        // heisst, dass zwei verschiedene Zeilen gelesen wurden.
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 101L, 0L, 0L));
    }

    @Test
    @DisplayName("Negative Zeit und fehlende Angaben kommen nicht in einen Stand")
    void schrankenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, -1L, 0L, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 10L, -1L, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(null, TAG, 0L, 0L, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, null, 0L, 0L, 0L, 0L));
    }

    @Test
    @DisplayName("Der leere Stand hat auch keine taetige Zeit")
    void leerIstAuchTaetigLeer() {
        Spielzeitstand stand = Spielzeitstand.leer(ANNA, TAG);

        assertEquals(0L, stand.taetigSekundenGesamt());
        assertEquals(0L, stand.taetigSekundenHeute());
        assertEquals(0, stand.taetigAnteilProzent());
    }

    @Test
    @DisplayName("Die taetigen Dauern sind dieselben Zahlen, nur anders geschrieben")
    void taetigeDauern() {
        Spielzeitstand stand = new Spielzeitstand(ANNA, TAG, 7_200L, 1_800L, 3_600L, 900L);

        assertEquals(Duration.ofHours(1), stand.taetigGesamt());
        assertEquals(Duration.ofMinutes(15), stand.taetigHeute());
        // Und sie sind NICHT dieselben wie die gemessenen - ein vertauschtes
        // Argumentpaar faellt hier auf.
        assertEquals(Duration.ofHours(2), stand.gesamt());
        assertEquals(Duration.ofMinutes(30), stand.heute());
    }

    @Test
    @DisplayName("Taetig kann nicht mehr sein als gemessen - drei Wege, drei Pruefungen")
    void taetigNieMehrAlsGemessen() {
        // Ueber alle Tage.
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 100L, 101L, 0L));
        // Am heutigen Tag - hier stimmt die Gesamtzahl und nur der Tag ist falsch.
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 50L, 100L, 51L));
        // Und der Fall, bei dem BEIDE Paare fuer sich stimmen: 40 taetig heute bei 50
        // heute und 100 gesamt ist erlaubt - aber nicht bei 30 taetig insgesamt. Das
        // heisst, eine Zeile aus der Vergangenheit ist beim Lesen verlorengegangen.
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 50L, 30L, 40L));
    }

    @Test
    @DisplayName("Negative taetige Zeit kommt nicht in einen Stand")
    void keineNegativeTaetigeZeit() {
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 100L, -1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 100L, 10L, -1L));
    }

    @Test
    @DisplayName("Der Anteil wird ABGERUNDET - er ist eine Untergrenze")
    void anteilAbgerundet() {
        // 99,8 Prozent sind 99 und nicht 100: Eine 100 waere die Zusage "alles taetig",
        // und die gibt dieser Stand nicht. Darum abgerundet und nicht gerundet.
        assertEquals(99, new Spielzeitstand(ANNA, TAG, 1_000L, 0L, 998L, 0L)
                .taetigAnteilProzent());
        assertEquals(100, new Spielzeitstand(ANNA, TAG, 1_000L, 0L, 1_000L, 0L)
                .taetigAnteilProzent());
        assertEquals(50, new Spielzeitstand(ANNA, TAG, 1_000L, 0L, 500L, 0L)
                .taetigAnteilProzent());
        // Und eine Null bleibt eine Null, nicht eine 1.
        assertEquals(0, new Spielzeitstand(ANNA, TAG, 1_000L, 0L, 9L, 0L)
                .taetigAnteilProzent());
    }

    @Test
    @DisplayName("Ohne gemessene Zeit ist der Anteil 0 und keine Ausnahme")
    void anteilOhneZeit() {
        // Ein Spieler, von dem nichts gemessen wurde, steht in jeder Liste - und eine
        // Liste darf an ihm nicht abbrechen.
        assertEquals(0, new Spielzeitstand(ANNA, TAG, 0L, 0L, 0L, 0L)
                .taetigAnteilProzent());
    }

    @Test
    @DisplayName("Der Anteil laeuft auch bei sehr grossen Zahlen nicht ueber")
    void anteilOhneUeberlauf() {
        // taetig * 100 ist genau bis Long.MAX_VALUE / 100 noch ein long. Im Spiel ist
        // diese Grenze nicht zu erreichen - es sind knapp drei Milliarden Jahre -, und
        // darum steht sie hier und nicht als Schranke im Programm. Was die Probe haelt,
        // ist der RAND: Wer die Rechnung spaeter umstellt (erst mal 100, dann teilen, in
        // anderer Reihenfolge), bekommt hier eine negative Zahl und eine rote Probe statt
        // einer Prozentangabe unter Null in der Anzeige.
        long sehrVieleSekunden = Long.MAX_VALUE / 100L;
        Spielzeitstand stand = new Spielzeitstand(ANNA, TAG, sehrVieleSekunden, 0L,
                sehrVieleSekunden, 0L);

        assertEquals(100, stand.taetigAnteilProzent());
    }
}
