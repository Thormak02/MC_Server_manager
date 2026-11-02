package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die offenen Teleportanfragen - ohne Server und ohne Uhr. */
class AnfragenTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final UUID BERT = UUID.nameUUIDFromBytes("bert".getBytes());
    private static final UUID CARO = UUID.nameUUIDFromBytes("caro".getBytes());

    private static final long SEKUNDE = 1_000_000_000L;

    private static Anfragen mitEinerMinute() {
        return new Anfragen(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Eine Anfrage steht, bis sie angenommen wird")
    void anfrageStehtUndWirdAngenommen() {
        var anfragen = mitEinerMinute();

        assertTrue(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0));
        assertEquals(1, anfragen.offeneFuer(BERT, SEKUNDE));
        assertEquals(0, anfragen.offeneFuer(ANNA, SEKUNDE), "Bert hat Anna nicht gefragt");

        var angenommen = anfragen.annehmen(BERT, 2 * SEKUNDE).orElseThrow();

        assertEquals(ANNA, angenommen.von());
        assertEquals(BERT, angenommen.an());
        assertEquals(0, anfragen.offene(), "danach steht sie nicht mehr");
    }

    @Test
    @DisplayName("Dieselbe Anfrage zweimal zu stellen fuegt nichts hinzu")
    void zweimalDieselbeAnfrage() {
        // Damit der Fragende liest, dass sie schon steht, statt zu glauben, er habe noch
        // einmal gefragt - und der Angefragte nicht zwei Meldungen bekommt.
        var anfragen = mitEinerMinute();

        assertTrue(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0));
        assertFalse(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, SEKUNDE));
        assertEquals(1, anfragen.offene());
    }

    @Test
    @DisplayName("Sich selbst zu fragen geht nicht")
    void sichSelbst() {
        var anfragen = mitEinerMinute();

        assertFalse(anfragen.stellen(ANNA, ANNA, Anfragen.Richtung.ZU_DIR, 0));
        assertEquals(0, anfragen.offene());
    }

    @Test
    @DisplayName("Zwei Fragende an denselben Spieler bleiben beide stehen")
    void zweiFragendeBleibenBeide() {
        // Die neueste zu nehmen und die uebrige wegzuwerfen waere einfacher - aber dann
        // waere eine Anfrage lautlos verschwunden, und der Fragende haette nie erfahren,
        // dass seine Frage nie ankam.
        var anfragen = mitEinerMinute();

        anfragen.stellen(ANNA, CARO, Anfragen.Richtung.ZU_DIR, 0);
        anfragen.stellen(BERT, CARO, Anfragen.Richtung.ZU_DIR, SEKUNDE);

        assertEquals(2, anfragen.offeneFuer(CARO, 2 * SEKUNDE));
        assertEquals(BERT, anfragen.neueste(CARO, 2 * SEKUNDE).orElseThrow().von(),
                "die neueste zuerst");

        anfragen.annehmen(CARO, 2 * SEKUNDE);

        assertEquals(1, anfragen.offeneFuer(CARO, 2 * SEKUNDE), "Annas Frage steht noch");
        assertEquals(ANNA, anfragen.neueste(CARO, 2 * SEKUNDE).orElseThrow().von());
    }

    @Test
    @DisplayName("Ablehnen raeumt alle Anfragen an diesen Spieler weg")
    void ablehnenRaeumtAlleWeg() {
        var anfragen = mitEinerMinute();
        anfragen.stellen(ANNA, CARO, Anfragen.Richtung.ZU_DIR, 0);
        anfragen.stellen(BERT, CARO, Anfragen.Richtung.ZU_DIR, 0);
        anfragen.stellen(CARO, ANNA, Anfragen.Richtung.ZU_DIR, 0);

        assertEquals(2, anfragen.ablehnenAlle(CARO, SEKUNDE));
        assertEquals(0, anfragen.offeneFuer(CARO, SEKUNDE));
        assertEquals(1, anfragen.offeneFuer(ANNA, SEKUNDE), "Caros eigene Frage bleibt");
    }

    @Test
    @DisplayName("Nach der Gueltigkeit ist die Anfrage weg")
    void anfrageLaeuftAb() {
        var anfragen = new Anfragen(Duration.ofSeconds(60));
        anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0);

        assertEquals(1, anfragen.offeneFuer(BERT, 59 * SEKUNDE));
        assertEquals(0, anfragen.offeneFuer(BERT, 60 * SEKUNDE), "genau am Rand schon weg");
        assertEquals(0, anfragen.offene(), "und aus der Liste geraeumt");
    }

    @Test
    @DisplayName("Abgelaufenes wird bei jeder Frage weggeraeumt")
    void aufraeumen() {
        // Ohne das waechst die Liste mit jeder Anfrage, die je gestellt wurde, und ein
        // Serverlauf dauert Wochen.
        var anfragen = new Anfragen(Duration.ofSeconds(10));
        for (int i = 0; i < 50; i++) {
            anfragen.stellen(UUID.nameUUIDFromBytes(("spieler" + i).getBytes()), BERT,
                    Anfragen.Richtung.ZU_DIR, i * SEKUNDE);
        }

        assertTrue(anfragen.offene() <= 11,
                "nur die letzten zehn Sekunden: " + anfragen.offene());
    }

    @Test
    @DisplayName("Beim Abmelden wird alles vergessen, in beide Richtungen")
    void vergessenBeimAbmelden() {
        // Sonst koennte jemand eine Anfrage annehmen, deren Fragender nicht mehr da ist -
        // und der Befehl muesste einen Fehler melden, wo gar keine Anfrage haette stehen
        // duerfen.
        var anfragen = mitEinerMinute();
        anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0);
        anfragen.stellen(CARO, ANNA, Anfragen.Richtung.ZU_DIR, 0);
        anfragen.stellen(BERT, CARO, Anfragen.Richtung.ZU_DIR, 0);

        anfragen.vergessen(ANNA);

        assertEquals(1, anfragen.offene());
        assertEquals(BERT, anfragen.neueste(CARO, SEKUNDE).orElseThrow().von());
    }

    @Test
    @DisplayName("Gegenseitige Anfragen bleiben beide stehen")
    void gegenseitig() {
        // Der einzige Fall, den keine der untersuchten Fremdumsetzungen belegt behandelt:
        // Anna fragt Bert, Bert fragt Anna. Beide Anfragen sind echt, beide Seiten
        // entscheiden fuer sich - die Karte darf dabei nicht durcheinanderkommen.
        var anfragen = mitEinerMinute();

        assertTrue(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0));
        assertTrue(anfragen.stellen(BERT, ANNA, Anfragen.Richtung.ZU_DIR, 0));
        assertEquals(2, anfragen.offene());

        assertEquals(ANNA, anfragen.annehmen(BERT, SEKUNDE).orElseThrow().von(),
                "Bert nimmt Annas Frage an");
        assertEquals(BERT, anfragen.neueste(ANNA, SEKUNDE).orElseThrow().von(),
                "Berts Frage an Anna steht weiter");
    }

    @Test
    @DisplayName("Die Richtung sagt, wer wandert und wer bleibt")
    void richtung() {
        // Der ganze Unterschied zwischen /tpa und /tpahere. In BEIDEN Faellen stimmt der
        // zu, der bewegt wird: bei ZU_DIR der Fragende (er hat gefragt), bei HIERHER der
        // Eingeladene (er nimmt an). Deshalb zieht /tpahere niemanden gegen seinen Willen.
        var anfragen = mitEinerMinute();
        anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0);
        var ichKomme = anfragen.annehmen(BERT, SEKUNDE).orElseThrow();

        assertEquals(ANNA, ichKomme.wandert(), "/tpa bewegt den Fragenden");
        assertEquals(BERT, ichKomme.bleibt());

        anfragen.stellen(ANNA, BERT, Anfragen.Richtung.HIERHER, SEKUNDE);
        var kommDu = anfragen.annehmen(BERT, 2 * SEKUNDE).orElseThrow();

        assertEquals(BERT, kommDu.wandert(), "/tpahere bewegt den Eingeladenen");
        assertEquals(ANNA, kommDu.bleibt());
    }

    @Test
    @DisplayName("Beide Richtungen an dieselbe Person sind zwei Anfragen")
    void beideRichtungen() {
        // "Komm zu mir" und "Ich komme zu dir" sind zwei verschiedene Fragen - und beide
        // darf man stellen, ohne dass die eine die andere verdraengt.
        var anfragen = mitEinerMinute();

        assertTrue(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.ZU_DIR, 0));
        assertTrue(anfragen.stellen(ANNA, BERT, Anfragen.Richtung.HIERHER, 0));
        assertEquals(2, anfragen.offeneFuer(BERT, SEKUNDE));
    }

    @Test
    @DisplayName("Wo keine Anfrage ist, kommt nichts zurueck")
    void keineAnfrage() {
        var anfragen = mitEinerMinute();

        assertTrue(anfragen.neueste(BERT, 0).isEmpty());
        assertTrue(anfragen.annehmen(BERT, 0).isEmpty());
        assertEquals(0, anfragen.ablehnenAlle(BERT, 0));
    }
}
