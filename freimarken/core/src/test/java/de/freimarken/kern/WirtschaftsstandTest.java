package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was ein Neuladen austauscht - und was es stehen laesst.
 *
 * <p>Die Zusage "Neuladen fasst das Startguthaben nicht an" ist eine Pruefung wert und
 * nicht nur einen Kommentar: Es ist der eine Wert, bei dem ein Fehler rueckwirkend
 * aendert, was jeder Spieler mitbringt, der noch nie gebucht hat.
 */
class WirtschaftsstandTest {

    private static Wirtschaftswerte satz(long grundpreis, long jeFeld, long startguthaben,
                                         int hoechstzahl, int gruppenFelder,
                                         String werbung) {
        return new Wirtschaftswerte(new Preisstaffel(grundpreis, jeFeld), startguthaben,
                hoechstzahl, gruppenFelder, werbung);
    }

    @Test
    @DisplayName("Die vier neuladbaren Werte und der Text werden getauscht")
    void tauschtWasErlaubtIst() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(
                satz(40_000L, 15_000L, 50_000L, 6, 9, "Rang Buerger gibt mehr."));

        assertEquals(40_000L, stand.werte().preise().grundpreis());
        assertEquals(15_000L, stand.werte().preise().zusammenlegungJeFeld());
        assertEquals(6, stand.werte().hoechstzahl());
        assertEquals(9, stand.werte().gruppenFelder());
        assertEquals("Rang Buerger gibt mehr.", stand.werte().rangWerbung());

        assertEquals(5, uebernahme.geaendert().size(),
                "fuenf Zeilen haben sich geaendert: " + uebernahme.geaendert());
        assertTrue(uebernahme.geaendert().contains(
                Wirtschaftswerte.PFAD_GRUNDPREIS + ": 25000 -> 40000"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                Wirtschaftswerte.PFAD_HOECHSTZAHL + ": 4 -> 6"),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Das Startguthaben bleibt stehen und wird genannt")
    void startguthabenBleibt() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 999_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG));

        assertEquals(50_000L, stand.werte().startguthaben(),
                "ein Neuladen darf das Startguthaben nicht aendern");
        assertTrue(uebernahme.geaendert().isEmpty(),
                "der Rest war gleich: " + uebernahme.geaendert());

        String zeile = uebernahme.nichtAngefasst().getFirst();
        assertTrue(zeile.contains(Wirtschaftswerte.PFAD_STARTGUTHABEN), zeile);
        assertTrue(zeile.contains("999000"), "die Datei muss genannt werden: " + zeile);
        assertTrue(zeile.contains("50000"), "und was weiter gilt: " + zeile);
        assertTrue(zeile.contains("Neustart"), zeile);
    }

    @Test
    @DisplayName("Ohne Aenderung bleibt die Liste leer - der Block darunter aber nicht")
    void nichtsGeaendert() {
        // Der Block "nicht angefasst" erscheint auch dann. Das ist das Gegenmittel gegen
        // das haeufigste Fehlerbild eines Neuladens: Der Befehl meldet Erfolg, und ein
        // Teil wirkt erst nach dem Neustart.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertTrue(uebernahme.geaendert().isEmpty());
        assertEquals(1, uebernahme.nichtAngefasst().size());
        assertTrue(uebernahme.nichtAngefasst().getFirst().contains("50000"));
    }

    @Test
    @DisplayName("Der Block steht auch bereit, wenn gar nichts uebernommen wurde")
    void blockOhneUebernahme() {
        // Der Fall, in dem eine Klage das Uebernehmen verhindert hat: Gerade dann muss
        // dastehen, dass das Startguthaben ohnehin nicht neuladbar ist - sonst sucht der
        // Betreiber den Fehler an der falschen Zeile.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var zeilen = stand.nichtAngefasst(null);

        assertEquals(1, zeilen.size());
        assertTrue(zeilen.getFirst().contains(Wirtschaftswerte.PFAD_STARTGUTHABEN));
        assertTrue(zeilen.getFirst().contains("50000"));
        assertEquals(Wirtschaftswerte.VORGABE, stand.werte(),
                "ein Blick darf nichts aendern");
    }

    @Test
    @DisplayName("Nur die Zeilen, die sich wirklich geaendert haben, werden genannt")
    void nurEchteAenderungen() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, 4, 4, "anders"));

        assertEquals(1, uebernahme.geaendert().size(), uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().getFirst()
                .startsWith(Wirtschaftswerte.PFAD_RANG_WERBUNG));
    }

    @Test
    @DisplayName("Zweimal neuladen vergleicht gegen den LAUFENDEN Stand, nicht die Vorgabe")
    void zweimal() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        stand.uebernimm(satz(40_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG));
        var zweite = stand.uebernimm(satz(80_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG));

        assertEquals(Wirtschaftswerte.PFAD_GRUNDPREIS + ": 40000 -> 80000",
                zweite.geaendert().getFirst(),
                "die Zeile muss zeigen, was wirklich galt - nicht die Vorgabe");
    }

    @Test
    @DisplayName("Die Rangwerbung wird beim Bauen getrimmt, nicht beim Anzeigen")
    void werbungGetrimmt() {
        // Sonst geschieht es an jeder Stelle, die den Text anzeigt - und eine davon
        // wird es vergessen.
        assertEquals("Mehr erlaubt ein Rang.",
                satz(25_000L, 10_000L, 50_000L, 4, 4, "  Mehr erlaubt ein Rang.  ")
                        .rangWerbung());
        assertEquals("", satz(25_000L, 10_000L, 50_000L, 4, 4, null).rangWerbung());
    }

    @Test
    @DisplayName("Unbrauchbare Werte kommen nie in einen Stand")
    void schrankenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> satz(25_000L, 10_000L, -1L, 4, 4, ""));
        assertThrows(IllegalArgumentException.class,
                () -> satz(25_000L, 10_000L, 50_000L, 0, 4, ""));
        assertThrows(IllegalArgumentException.class,
                () -> satz(25_000L, 10_000L, 50_000L, 4, 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new Wirtschaftsstand(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Wirtschaftsstand(Wirtschaftswerte.VORGABE).uebernimm(null));
    }

    @Test
    @DisplayName("Die Vorgabe im Programm ist genau das, was die Datei ausliefert")
    void vorgabeStimmt() {
        // Der Rueckfall muss da sein, wo er nicht fehlen kann: saveDefaultConfig()
        // schreibt eine vorhandene Datei nie um.
        assertEquals(25_000L, Wirtschaftswerte.VORGABE.preise().grundpreis());
        assertEquals(10_000L, Wirtschaftswerte.VORGABE.preise().zusammenlegungJeFeld());
        assertEquals(50_000L, Wirtschaftswerte.VORGABE.startguthaben());
        assertEquals(4, Wirtschaftswerte.VORGABE.hoechstzahl());
        assertEquals(4, Wirtschaftswerte.VORGABE.gruppenFelder());
        assertEquals("Mehr erlaubt ein hoeherer Rang.",
                Wirtschaftswerte.VORGABE.rangWerbung());
    }
}
