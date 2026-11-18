package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Fenster einer Feldrasterwelt - der gemeinsame Rumpf von drei Weltgrenzen.
 *
 * <p>Die drei Klassen {@link Feldgrenze}, {@link Inselgrenze} und {@link Wolkengrenze}
 * haben ihre eigenen Proben, und die bleiben: Sie halten die ZAHLEN ihrer Welt fest. Hier
 * steht, was alle drei gemeinsam haben - und die letzte Probe haelt nach, dass sie den
 * Rumpf wirklich benutzen und nicht wieder selbst rechnen.
 */
@DisplayName("Das Feldfenster")
class FeldfensterTest {

    /** Hafenwerders Raster: Feld 128, Meer 128, Rasterweite 256. */
    private final Feldrechnung rechnung = new Feldrechnung(128, 128);

    @Test
    @DisplayName("Die Wunschkante wird auf ganze Felder und eine GERADE Zahl abgerundet")
    void wunschWirdRaster() {
        // 5000 ist kein Vielfaches von 256. Eine Grenze, die ein Feld anschneidet, liesse
        // Eigentum halb draussen liegen - und ein Warp auf dessen Mitte setzte den
        // Spieler vor die eigene Weltgrenze.
        Feldfenster fenster = Feldfenster.ausKante(rechnung, 5000, "Inselfelder");

        assertEquals(18, fenster.felderJeAchse());
        assertEquals(4736.0, fenster.kante());
        assertEquals(-64.0, fenster.mitte(), "immer der halbe Zwischenraum nach Westen");
        assertEquals(-9, fenster.von());
        assertEquals(8, fenster.bis());
        assertEquals(324, fenster.felderZahl());
        assertEquals(-2432, fenster.kleinsterBlock());
        assertEquals(2303, fenster.groessterBlock());
    }

    @Test
    @DisplayName("Die Kante ist genau die Spanne der Bloecke darin")
    void kanteUndBloeckePassenZusammen() {
        // Die Probe gegen den Fehler um eins, und sie rechnet nicht mit derselben Formel:
        // Was die Grenze als Kante fuehrt, muss die Zahl der Bloecke zwischen ihren
        // Raendern sein. Eine Abweichung hiesse, dass ein Block mehr oder weniger
        // eingeschlossen ist, als die Grenze behauptet.
        for (int wunsch : new int[] {1000, 5000, 6000, 20000}) {
            Feldfenster fenster = Feldfenster.ausKante(rechnung, wunsch, "Felder");
            long bloecke = (long) fenster.groessterBlock() - fenster.kleinsterBlock() + 1;

            assertEquals((long) fenster.kante(), bloecke,
                    "Kante und Blockspanne muessen bei Wunsch " + wunsch
                            + " dasselbe sein");
        }
    }

    @Test
    @DisplayName("Jedes Feld im Fenster liegt vollstaendig drin, keines ausserhalb")
    void jedesFeldLiegtGanzDrin() {
        Feldfenster fenster = Feldfenster.ausKante(rechnung, 5000, "Felder");

        assertTrue(fenster.enthaeltFeld(fenster.von(), fenster.von()));
        assertTrue(fenster.enthaeltFeld(fenster.bis(), fenster.bis()));
        assertFalse(fenster.enthaeltFeld(fenster.von() - 1, 0), "ein Feld zu weit westlich");
        assertFalse(fenster.enthaeltFeld(0, fenster.bis() + 1), "eines zu weit suedlich");

        // Beide Ecken des aeussersten erlaubten Feldes liegen innerhalb der Grenze - der
        // Grund, warum felderFuer abrundet.
        int ersterBlock = rechnung.feldAnfang(fenster.von());
        int letzterBlock = rechnung.feldAnfang(fenster.bis()) + rechnung.feldKante() - 1;
        assertTrue(ersterBlock >= fenster.kleinsterBlock(), "Westkante des Westfeldes");
        assertTrue(letzterBlock <= fenster.groessterBlock(), "Ostkante des Ostfeldes");
    }

    @Test
    @DisplayName("Der Zwischenraum am Ostrand liegt drin, gehoert aber dem Feld DAVOR")
    void zwischenraumGehoertDemFeldDavor() {
        // Die Stelle, an der sich eine Meldung fuer den Spieler verrechnet, wenn sie nur
        // hier fragt: Der letzte Block der Welt liegt innerhalb der Grenze, ist aber kein
        // Eigentum. Wer das wissen will, fragt erst den Plan seiner Welt.
        Feldfenster fenster = Feldfenster.ausKante(rechnung, 5000, "Felder");

        assertTrue(fenster.enthaelt(fenster.groessterBlock(), 0));
        assertEquals(fenster.bis(), rechnung.feldNummer(fenster.groessterBlock()));
        assertFalse(rechnung.imFeld(fenster.groessterBlock()),
                "der aeusserste Block ist Zwischenraum und kein Feld");
    }

    @Test
    @DisplayName("Das Ankunftsfeld wird abgezogen - aber nur, wenn es drin liegt")
    void ankunftsfeldWirdAbgezogen() {
        Feldfenster fenster = Feldfenster.ausKante(rechnung, 5000, "Felder");

        assertEquals(fenster.felderZahl() - 1, fenster.felderOhneAnkunft(0));
        // Ein Ankunftsfeld weit draussen gibt es nicht - und dann darf auch nichts
        // abgezogen werden, sonst zoege eine Welt ein Feld ab, das sie gar nicht hat.
        assertEquals(fenster.felderZahl(), fenster.felderOhneAnkunft(1000));
    }

    @Test
    @DisplayName("Unter zwei Feldern und ueber dem Hoechstmass wird geworfen")
    void grenzenDesFensters() {
        int mindestens = rechnung.mindestKante();

        assertEquals(2, Feldfenster.ausKante(rechnung, mindestens, "Felder")
                .felderJeAchse(), "die kleinste Welt hat genau zwei Felder je Achse");
        var zuKlein = assertThrows(IllegalArgumentException.class,
                () -> Feldfenster.ausKante(rechnung, mindestens - 1, "Inselfelder"));
        assertTrue(zuKlein.getMessage().contains("Inselfelder"),
                "die Meldung nennt die Felder dieser Welt, nicht irgendeine: "
                        + zuKlein.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> Feldfenster.ausKante(rechnung, (int) Grenzregel.HOECHSTMASS + 1,
                        "Felder"));
    }

    @Test
    @DisplayName("Eine ungerade Feldzahl gibt es nicht")
    void ungeradeFeldzahlWirdAbgewiesen() {
        // Nur bei gerader Zahl liegen die Felder symmetrisch um den Ursprung, und nur
        // dann sitzt Feld 0/0 - das Ankunftsfeld jeder dieser Welten - in der Mitte.
        assertThrows(IllegalArgumentException.class, () -> new Feldfenster(rechnung, 3));
        assertThrows(IllegalArgumentException.class, () -> new Feldfenster(rechnung, 1));
        assertThrows(IllegalArgumentException.class, () -> new Feldfenster(rechnung, 0));
        assertThrows(IllegalArgumentException.class, () -> new Feldfenster(null, 2));
    }

    @Test
    @DisplayName("Die Grenze waechst nur nach aussen, und ein Sprung braucht ein Wort")
    void grenzeWaechstNurNachAussen() {
        // Ein bezahltes Feld darf nicht vor die Tuer geraten, weil jemand eine Zahl in
        // einer Datei kleiner macht.
        assertEquals(5000, Feldfenster.wirksameKante(rechnung, 3000, 5000.0, 5000, false),
                "die Welt ist schon weiter - das bleibt sie");
        assertEquals(6000, Feldfenster.wirksameKante(rechnung, 6000, 5000.0, 5000, false),
                "nach aussen geht es ohne Rueckfrage");
        assertEquals(5000, Feldfenster.wirksameKante(rechnung, 40000, 5000.0, 5000, false),
                "ueber das Doppelte nicht ohne Bestaetigung");
        assertEquals(40000,
                Feldfenster.wirksameKante(rechnung, 40000, 5000.0, 5000, true),
                "mit Bestaetigung schon");
    }

    @Test
    @DisplayName("Die drei Weltgrenzen rechnen NICHT selbst - sie halten dieses Fenster")
    void dieDreiWeltenBenutzenDasFenster() {
        // Die Zusicherung, die diese Klasse ueberhaupt rechtfertigt [18.11.2026]. Bis zu
        // diesem Tag stand ihr Rumpf zweimal da, Zeile fuer Zeile gleich. Faengt eine der
        // drei wieder an, selbst zu rechnen, wird diese Probe rot - und nicht erst die
        // Welt, in der eine Grenze mitten durch Eigentum laeuft.
        // Die Rasterrechnung kommt aus dem PLAN der jeweiligen Welt und ist hier nicht
        // abgeschrieben: Sonst pruefte diese Probe zwei Abschriften gegeneinander.
        Feldgrenze frei = Feldgrenze.freistaedte();
        vergleiche(frei.plan().rechnung(), Feldgrenze.FREISTAEDTE_KANTE,
                frei.felderJeAchse(), frei.von(), frei.bis(), frei.kante(), frei.mitte(),
                frei.kleinsterBlock(), frei.groessterBlock(), frei.staedteZahl(),
                "Freistaedte");
        Inselgrenze hafen = Inselgrenze.hafenwerder();
        vergleiche(hafen.plan().rechnung(), Inselgrenze.HAFENWERDER_KANTE,
                hafen.felderJeAchse(), hafen.von(), hafen.bis(), hafen.kante(),
                hafen.mitte(), hafen.kleinsterBlock(), hafen.groessterBlock(),
                hafen.inselnZahl(), "Hafenwerder");
        Wolkengrenze wolken = Wolkengrenze.wolkenwerder();
        vergleiche(wolken.plan().rechnung(), Wolkengrenze.WOLKENWERDER_KANTE,
                wolken.felderJeAchse(), wolken.von(), wolken.bis(), wolken.kante(),
                wolken.mitte(), wolken.kleinsterBlock(), wolken.groessterBlock(),
                wolken.inselnZahl(), "Wolkenwerder");
    }

    private static void vergleiche(Feldrechnung rechnung, int wunsch, int felder, int von,
                                   int bis, double kante, double mitte, int kleinster,
                                   int groesster, long zuVergeben, String welt) {
        Feldfenster fenster = Feldfenster.ausKante(rechnung, wunsch, "Felder");

        assertEquals(fenster.felderJeAchse(), felder, welt + ": Feldzahl");
        assertEquals(fenster.von(), von, welt + ": von");
        assertEquals(fenster.bis(), bis, welt + ": bis");
        assertEquals(fenster.kante(), kante, welt + ": Kante");
        assertEquals(fenster.mitte(), mitte, welt + ": Mitte");
        assertEquals(fenster.kleinsterBlock(), kleinster, welt + ": kleinster Block");
        assertEquals(fenster.groessterBlock(), groesster, welt + ": groesster Block");
        assertEquals(fenster.felderOhneAnkunft(0), zuVergeben,
                welt + ": zu vergebende Felder");
    }
}
