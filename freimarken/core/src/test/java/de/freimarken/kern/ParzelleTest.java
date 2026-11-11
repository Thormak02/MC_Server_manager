package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Datensatz, an dem alles haengt. Seine Waechter sind die letzte Stelle, an der ein
 * unmoeglicher Besitz noch auffaellt, bevor er in der Ablage steht.
 */
class ParzelleTest {

    private static final String WELT = "freimarken:marktstadt";

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private Parzelle bauen(long id, long elternId, Zustand zustand, Set<UUID> mitbauer) {
        return new Parzelle(id, WELT, 1, 1, anna, mitbauer, 0L, Instant.EPOCH,
                Art.PARZELLE, elternId, zustand);
    }

    @Test
    @DisplayName("Ohne Art oder ohne Zustand entsteht kein Besitz")
    void artUndZustandSindPflicht() {
        assertThrows(IllegalArgumentException.class, () -> new Parzelle(1L, WELT, 0, 0, anna,
                Set.of(), 0L, Instant.EPOCH, null, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV));
        assertThrows(IllegalArgumentException.class, () -> new Parzelle(1L, WELT, 0, 0, anna,
                Set.of(), 0L, Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, null));
    }

    @Test
    @DisplayName("Eine negative Elternnummer ist ein Fehler")
    void negativeElternnummer() {
        assertThrows(IllegalArgumentException.class,
                () -> bauen(1L, -1L, Zustand.AKTIV, Set.of()));
    }

    @Test
    @DisplayName("Besitz kann nicht sein eigener Elternteil sein - Nummer 0 ohne Eltern aber schon")
    void keinSelbstElternteil() {
        assertThrows(IllegalArgumentException.class,
                () -> bauen(7L, 7L, Zustand.AKTIV, Set.of()));
        // Der Grenzfall, an dem ein zu einfacher Waechter scheitert: id 0 UND elternId 0
        // ist kein Selbstbezug, sondern ein Objekt, das noch keine Nummer bekommen hat.
        Parzelle ohneNummer = bauen(0L, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, Set.of());
        assertFalse(ohneNummer.hatElternteil());
    }

    @Test
    @DisplayName("hatElternteil unterscheidet eigenstaendig von Teil einer Gruppe")
    void hatElternteil() {
        assertFalse(bauen(5L, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, Set.of()).hatElternteil());
        assertTrue(bauen(5L, 4L, Zustand.AKTIV, Set.of()).hatElternteil());
    }

    @Test
    @DisplayName("Gekuendigt laesst weiterbauen, verfallen verweigert auch dem Eigentuemer")
    void zustandEntscheidetVorDemEigentum() {
        Set<UUID> mitbauer = Set.of(bert);
        assertTrue(bauen(1L, 0L, Zustand.AKTIV, mitbauer).darfBauen(anna));
        assertTrue(bauen(1L, 0L, Zustand.AKTIV, mitbauer).darfBauen(bert));

        // Wer raeumen soll, muss seine Kisten noch oeffnen und abbauen koennen.
        assertTrue(bauen(1L, 0L, Zustand.GEKUENDIGT, mitbauer).darfBauen(anna));
        assertTrue(bauen(1L, 0L, Zustand.GEKUENDIGT, mitbauer).darfBauen(bert));

        assertFalse(bauen(1L, 0L, Zustand.VERFALLEN, mitbauer).darfBauen(anna),
                "Auf verfallenem Besitz baut auch der Eigentuemer nicht mehr");
        assertFalse(bauen(1L, 0L, Zustand.VERFALLEN, mitbauer).darfBauen(bert));
    }

    @Test
    @DisplayName("Mitbauer werden weiterhin unveraenderlich kopiert")
    void mitbauerBleibenUnveraenderlich() {
        Set<UUID> veraenderlich = new HashSet<>(Set.of(bert));
        Parzelle parzelle = bauen(1L, 0L, Zustand.AKTIV, veraenderlich);
        veraenderlich.clear();

        assertTrue(parzelle.darfBauen(bert), "Die Kopie darf dem Aufrufer nicht folgen");
        assertThrows(UnsupportedOperationException.class,
                () -> parzelle.mitbauer().add(anna));
    }

    @Test
    @DisplayName("Jede Art und jeder Zustand uebersteht den Weg durch den Datenbankschluessel")
    void rundlaufUeberAlleKonstanten() {
        for (Art art : Art.values()) {
            assertSame(art, Art.ausSchluessel(art.schluessel()), art.name());
        }
        for (Zustand zustand : Zustand.values()) {
            assertSame(zustand, Zustand.ausSchluessel(zustand.schluessel()), zustand.name());
        }
        // ASCII, kein Umlaut - sonst passt der Wert nicht zur ENUM-Spalte.
        assertEquals("gekuendigt", Zustand.GEKUENDIGT.schluessel());
    }

    @Test
    @DisplayName("Eine Freistadt OHNE Namen gibt es nicht - der Riegel greift")
    void freistadtOhneNamenWirft() {
        // Gefunden von der Einspritzungsrunde am 11.11.2026: Der Riegel stand da und
        // war von keiner Probe gedeckt. Alle Proben legten Staedte MIT Namen an - und
        // ein Riegel, den nie etwas ausloest, ist gruen mit und ohne.
        //
        // Was er verhindert: eine Stadt, die ueber keinen Warp erreichbar ist. 400.000
        // Mark fuer ein Feld, das niemand findet.
        var ohne = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                        Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                        Zustand.AKTIV, Parzelle.KEIN_NAME));
        assertTrue(ohne.getMessage().contains("Freistadt"), ohne.getMessage());

        // Auch der leere Name faellt - sonst stuende in der Spalte eine Zeichenkette,
        // und der eindeutige Schluessel (welt, name) liesse genau eine davon zu.
        assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                        Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                        Zustand.AKTIV, "   "));
    }

    @Test
    @DisplayName("Eine Parzelle MIT Namen gibt es auch nicht - die andere Richtung")
    void parzelleMitNamenWirft() {
        // Beide Richtungen, wie bei jeder Bindung in diesem Projekt. Eine Parzelle mit
        // Namen belegte einen Namen im eindeutigen Schluessel, ohne dass je eine Stadt
        // dazu entstuende - und der naechste Gruender bekaeme eine Absage, deren Grund
        // nirgends zu sehen ist.
        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        "neustadt"));
        assertTrue(mit.getMessage().contains("neustadt"), mit.getMessage());

        // Und der kurze Konstruktor kommt durch - er setzt KEIN_NAME und kann darum
        // gar keine Freistadt erzeugen.
        assertEquals(Parzelle.KEIN_NAME, new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L,
                Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL,
                Zustand.AKTIV).name());
    }

    @Test
    @DisplayName("Ein unbekannter Schluessel wirft und nennt den gelesenen Wert")
    void unbekannterSchluessel() {
        var artFehler = assertThrows(IllegalStateException.class,
                () -> Art.ausSchluessel("hochhaus"));
        assertTrue(artFehler.getMessage().contains("hochhaus"), artFehler.getMessage());

        var zustandFehler = assertThrows(IllegalStateException.class,
                () -> Zustand.ausSchluessel("GEKUENDIGT"));
        assertTrue(zustandFehler.getMessage().contains("GEKUENDIGT"),
                "Gross geschrieben ist ein anderer Schluessel - genau das faengt der Riegel");
    }
}
