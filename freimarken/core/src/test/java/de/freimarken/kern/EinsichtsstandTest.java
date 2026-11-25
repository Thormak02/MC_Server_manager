package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer gerade einsieht - und vor allem: wer NICHT MEHR.
 *
 * <p><b>Die tragende Probe ist {@link #fristLaeuftVonSelbstAb()}.</b> Ohne Frist haette
 * ein Moderator nach einem einzigen {@code /einsehen} dauerhaft Zugriff auf jede fremde
 * Kiste des Servers - und zwar ohne es zu merken, denn im Spiel sieht man einen Modus
 * nicht.
 */
class EinsichtsstandTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BERT = UUID.randomUUID();

    private static Einsichtsstand stand() {
        return stand(Einsichtswerte.VORGABE);
    }

    private static Einsichtsstand stand(Einsichtswerte werte) {
        return new Einsichtsstand(() -> werte);
    }

    @Test
    @DisplayName("Wer nichts getan hat, sieht nicht ein")
    void ohneEinschaltenKeineEinsicht() {
        assertFalse(stand().schaut(ANNA, MITTAGS));
    }

    @Test
    @DisplayName("Eingeschaltet sieht er ein - und die Antwort nennt das Ende")
    void eingeschaltetSiehtEin() {
        Einsichtsstand stand = stand();

        Instant ende = stand.schalteEin(ANNA, MITTAGS);

        assertEquals(MITTAGS.plusSeconds(300L), ende);
        assertTrue(stand.schaut(ANNA, MITTAGS));
        assertTrue(stand.schaut(ANNA, MITTAGS.plusSeconds(299L)));
    }

    @Test
    @DisplayName("DIE FRIST: sie laeuft von selbst ab, ohne Takt")
    void fristLaeuftVonSelbstAb() {
        // DIE PROBE DIESES TEILS. Die Frist ist als ZEITPUNKT gebaut und nicht als
        // Zaehler: Ein Zaehler braeuchte einen Takt, der ihn herunterzaehlt, und ein
        // Takt, der ausfaellt, liesse die Einsicht offen. Ein Zeitpunkt laeuft ab -
        // auch wenn nichts mehr laeuft.
        Einsichtsstand stand = stand();
        stand.schalteEin(ANNA, MITTAGS);

        assertFalse(stand.schaut(ANNA, MITTAGS.plusSeconds(300L)),
                "im Augenblick des Ablaufs ist sie abgelaufen - der Grenzfall"
                        + " entscheidet GEGEN den Zugriff");
        assertFalse(stand.schaut(ANNA, MITTAGS.plusSeconds(99999L)));
    }

    @Test
    @DisplayName("Die Frist kommt aus der DATEI und nicht aus dem Programm")
    void fristAusDerDatei() {
        Einsichtsstand stand = stand(Einsichtswerte.VORGABE.mitDauer(30L));

        assertEquals(MITTAGS.plusSeconds(30L), stand.schalteEin(ANNA, MITTAGS));
        assertFalse(stand.schaut(ANNA, MITTAGS.plusSeconds(30L)));
    }

    @Test
    @DisplayName("Ein ERNEUTES Einschalten verlaengert")
    void erneutesEinschaltenVerlaengert() {
        // Der Weg fuer eine Untersuchung, die laenger dauert als die Frist: ein
        // Tastendruck und eine Zeile im Protokoll.
        Einsichtsstand stand = stand();
        stand.schalteEin(ANNA, MITTAGS);

        Instant ende = stand.schalteEin(ANNA, MITTAGS.plusSeconds(200L));

        assertEquals(MITTAGS.plusSeconds(500L), ende);
        assertTrue(stand.schaut(ANNA, MITTAGS.plusSeconds(499L)),
                "die alte Frist waere hier abgelaufen");
    }

    @Test
    @DisplayName("Die neue Frist gilt fuer die NAECHSTE Einsicht, nicht die laufende")
    void neueFristGiltNichtRueckwirkend() {
        // Eine Einsicht, die laenger gilt, weil jemand eine Datei geaendert hat, waere
        // ein Zustand, den niemand eingeschaltet hat.
        Einsichtswerte[] jetzt = {Einsichtswerte.VORGABE.mitDauer(60L)};
        Einsichtsstand stand = new Einsichtsstand(() -> jetzt[0]);
        stand.schalteEin(ANNA, MITTAGS);

        jetzt[0] = Einsichtswerte.VORGABE.mitDauer(3600L);

        assertFalse(stand.schaut(ANNA, MITTAGS.plusSeconds(60L)),
                "die laufende Einsicht behaelt ihr Ende");
        assertEquals(MITTAGS.plusSeconds(3660L),
                stand.schalteEin(ANNA, MITTAGS.plusSeconds(60L)),
                "die naechste bekommt die neue Frist");
    }

    @Test
    @DisplayName("Ausschalten sagt, ob sie vorher AN war")
    void ausschaltenSagtDenVorzustand() {
        // Ein "ist jetzt aus" auf etwas, das nie an war, laesst einen Moderator
        // glauben, er habe eben noch eingesehen - und das ist die Annahme, mit der man
        // eine fremde Kiste fuer die eigene haelt.
        Einsichtsstand stand = stand();

        assertFalse(stand.schalteAus(ANNA), "sie war nie an");
        stand.schalteEin(ANNA, MITTAGS);
        assertTrue(stand.schalteAus(ANNA), "jetzt war sie an");
        assertFalse(stand.schaut(ANNA, MITTAGS));
        assertFalse(stand.schalteAus(ANNA), "und jetzt wieder nicht");
    }

    @Test
    @DisplayName("Die Einsicht gilt je SPIELER")
    void einsichtGiltJeSpieler() {
        Einsichtsstand stand = stand();

        stand.schalteEin(ANNA, MITTAGS);

        assertTrue(stand.schaut(ANNA, MITTAGS));
        assertFalse(stand.schaut(BERT, MITTAGS),
                "die Einsicht des einen ist nicht die des anderen");
    }

    @Test
    @DisplayName("Das Ende ist abfragbar, solange sie gilt")
    void endeIstAbfragbar() {
        Einsichtsstand stand = stand();
        stand.schalteEin(ANNA, MITTAGS);

        assertEquals(MITTAGS.plusSeconds(300L),
                stand.endeFuer(ANNA, MITTAGS).orElseThrow());
        assertTrue(stand.endeFuer(ANNA, MITTAGS.plusSeconds(300L)).isEmpty(),
                "nach dem Ablauf gibt es kein Ende mehr");
        assertTrue(stand.endeFuer(BERT, MITTAGS).isEmpty());
    }

    @Test
    @DisplayName("Vergessen raeumt den Spieler weg - auch mitten in der Frist")
    void vergessenRaeumtWeg() {
        // Beim Verlassen des Servers. Mehr als Aufraeumen: Wer wiederkommt, soll NICHT
        // weiter einsehen - ein Modus, der eine Abwesenheit uebersteht, ist einer, den
        // man beim Beitritt nicht mehr erwartet.
        Einsichtsstand stand = stand();
        stand.schalteEin(ANNA, MITTAGS);

        stand.vergiss(ANNA);

        assertFalse(stand.schaut(ANNA, MITTAGS));
        assertEquals(0, stand.anzahl(MITTAGS));
    }

    @Test
    @DisplayName("Abgelaufene werden weggeraeumt - die Tabelle waechst nicht")
    void abgelaufeneWerdenGeraeumt() {
        // Dieselbe Begruendung, die an Schutzwache.letzteMeldung steht: Sonst waechst
        // die Tabelle mit jedem Moderator, der je eingesehen hat, und niemand sieht es.
        Einsichtsstand stand = stand(Einsichtswerte.VORGABE.mitDauer(10L));
        stand.schalteEin(ANNA, MITTAGS);
        stand.schalteEin(BERT, MITTAGS);

        assertEquals(2, stand.anzahl(MITTAGS));
        assertEquals(0, stand.anzahl(MITTAGS.plusSeconds(10L)),
                "abgelaufene zaehlen nicht mit");
        // Und sie sind auch wirklich weg - eine Zahl, die abgelaufene mitzaehlt, waere
        // eine, die nie sinkt.
        assertEquals(0, stand.anzahl(MITTAGS),
                "auch rueckwaerts gefragt sind sie geraeumt");
    }

    @Test
    @DisplayName("Auch schaut() raeumt den abgelaufenen Eintrag weg")
    void schautRaeumtAuch() {
        // schaut() wird bei JEDEM Klick auf einen Behaelter gerufen - es ist die
        // Stelle, an der das Aufraeumen nichts kostet.
        Einsichtsstand stand = stand(Einsichtswerte.VORGABE.mitDauer(10L));
        stand.schalteEin(ANNA, MITTAGS);

        assertFalse(stand.schaut(ANNA, MITTAGS.plusSeconds(10L)));
        assertEquals(0, stand.anzahl(MITTAGS));
    }

    @Test
    @DisplayName("Ein null-Lieferant gibt die Vorgabe und keinen Wurf")
    void nullLieferantGibtVorgabe() {
        Einsichtsstand stand = new Einsichtsstand(() -> null);

        assertEquals(Einsichtswerte.VORGABE, stand.werte());
        assertEquals(MITTAGS.plusSeconds(300L), stand.schalteEin(ANNA, MITTAGS));
    }

    @Test
    @DisplayName("Ohne Werte gibt es keinen Stand")
    void ohneWerteWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Einsichtsstand(null));
    }

    @Test
    @DisplayName("Fehlende Angaben werfen oder antworten mit nein")
    void fehlendeAngaben() {
        Einsichtsstand stand = stand();

        assertThrows(IllegalArgumentException.class,
                () -> stand.schalteEin(null, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> stand.schalteEin(ANNA, null));
        assertThrows(IllegalArgumentException.class, () -> stand.schalteAus(null));
        assertThrows(IllegalArgumentException.class, () -> stand.anzahl(null));
        // schaut() antwortet dagegen mit NEIN und wirft nicht: Es laeuft im
        // Ereignisweg, und ein Wurf dort waere eine Ausnahme bei jedem Klick auf eine
        // Kiste. Von zwei Fehlern der, der nichts oeffnet.
        assertFalse(stand.schaut(null, MITTAGS));
        assertFalse(stand.schaut(ANNA, null));
    }
}
