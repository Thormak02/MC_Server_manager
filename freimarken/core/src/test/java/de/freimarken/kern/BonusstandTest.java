package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Serie eines Spielers - und was in einen Stand nicht hineinkommt. */
@DisplayName("Der Bonusstand")
class BonusstandTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    @Test
    @DisplayName("Ein leerer Stand hat keinen Tag und keine Serie")
    void leer() {
        Bonusstand stand = Bonusstand.leer(ANNA);

        assertEquals(ANNA, stand.spieler());
        assertEquals(null, stand.letzterTag());
        assertEquals(0, stand.serie());
        assertEquals(0, stand.laengsteSerie());
        assertFalse(stand.schonDagewesen());
    }

    @Test
    @DisplayName("Die erste Zahlung legt Tag, Serie und Bestmarke an")
    void ersteZahlung() {
        Bonusstand nachher = Bonusstand.leer(ANNA).nachZahlung(HEUTE, 1);

        assertEquals(HEUTE, nachher.letzterTag());
        assertEquals(1, nachher.serie());
        assertEquals(1, nachher.laengsteSerie());
        assertTrue(nachher.schonDagewesen());
    }

    @Test
    @DisplayName("Die Bestmarke waechst MIT - das ist die eine Stelle dafuer")
    void bestmarkeWaechstMit() {
        Bonusstand stand = Bonusstand.leer(ANNA).nachZahlung(HEUTE.minusDays(1), 4)
                .nachZahlung(HEUTE, 5);

        assertEquals(5, stand.serie());
        assertEquals(5, stand.laengsteSerie());
    }

    @Test
    @DisplayName("Eine gerissene Serie laesst die Bestmarke STEHEN")
    void bestmarkeBleibtStehen() {
        // Der Grund, warum die Bestmarke gespeichert wird und nicht gerechnet: Aus einem
        // Datum und einer Zahl ist nicht mehr herauszulesen, wie lang eine frueher
        // gerissene Serie war.
        Bonusstand lang = new Bonusstand(ANNA, HEUTE.minusDays(10), 12, 12);

        Bonusstand neu = lang.nachZahlung(HEUTE, 1);

        assertEquals(1, neu.serie());
        assertEquals(12, neu.laengsteSerie(),
                "zwoelf Tage sind geschehen - sie verschwinden nicht, weil die Serie"
                        + " riss");
    }

    @Test
    @DisplayName("Die laufende Serie kann nicht laenger sein als die laengste")
    void laufendeNieLaengerAlsLaengste() {
        // Faellt hier auf und nicht in der Anzeige: Dort nennte eine Zeile zwei Zahlen,
        // von denen die groessere die kleinere ist.
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(ANNA, HEUTE, 5, 4));
    }

    @Test
    @DisplayName("Ein Tag ohne Serie und eine Serie ohne Tag sind halbe Staende")
    void tagUndSerieGehoerenZusammen() {
        // Ohne Tag ist eine Serie nicht fortzusetzen und nicht zu brechen; mit einem Tag
        // und einer 0 behauptet der Stand, am Tag X sei gezahlt worden und die Serie sei
        // null gewesen. Beide Haelften werden verweigert, damit Serienregel sich auf das
        // Paar verlassen kann.
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(ANNA, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(ANNA, HEUTE, 0, 0));
    }

    @Test
    @DisplayName("Ein leerer Stand MIT Bestmarke ist erlaubt")
    void leerMitBestmarke() {
        // Erreichbar ueber eine kuenftige Fassung, die Serien zuruecksetzt, aber die
        // Bestmarke behaelt. Heute entsteht er nicht - verboten ist er trotzdem nicht,
        // denn er widerspricht nichts: Niemand hat heute gezahlt, und die Bestmarke ist
        // Vergangenheit.
        Bonusstand stand = new Bonusstand(ANNA, null, 0, 9);

        assertEquals(9, stand.laengsteSerie());
        assertFalse(stand.schonDagewesen());
    }

    @Test
    @DisplayName("Negative Serien und ein Stand ohne Spieler kommen nicht zustande")
    void schrankenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(ANNA, HEUTE, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(ANNA, HEUTE, 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Bonusstand(null, HEUTE, 1, 1));
    }

    @Test
    @DisplayName("Eine Zahlung ohne Tag oder unter Serientag 1 kommt nicht zustande")
    void zahlungBrauchtBeides() {
        Bonusstand stand = Bonusstand.leer(ANNA);

        assertThrows(IllegalArgumentException.class, () -> stand.nachZahlung(null, 1));
        assertThrows(IllegalArgumentException.class, () -> stand.nachZahlung(HEUTE, 0));
        assertThrows(IllegalArgumentException.class, () -> stand.nachZahlung(HEUTE, -1));
    }

    @Test
    @DisplayName("nachZahlung laesst den Spieler stehen")
    void spielerBleibt() {
        assertEquals(ANNA, Bonusstand.leer(ANNA).nachZahlung(HEUTE, 1).spieler());
    }
}
