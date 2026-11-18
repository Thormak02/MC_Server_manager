package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Preis, Obergrenze und Biome einer Wolkeninsel. */
@DisplayName("Die Wolkenwerte")
class WolkenwerteTest {

    @Test
    @DisplayName("Ab Werk gratis, eine Insel je Spieler, alle zehn Biome")
    void vorgabe() {
        // Plan 6.2: "Skyblock-Insel | Wolkenwerder | gratis" - der zweite der zwei
        // gratis Einstiege. Steht hier eine Zahl, ist der Einstieg zu.
        assertEquals(0L, Wolkenwerte.VORGABE.preis());
        assertEquals(1, Wolkenwerte.VORGABE.hoechstzahl());
        assertEquals(Wolkenbiome.NAMEN, Wolkenwerte.VORGABE.biome(),
                "ab Werk wird keines zurueckgehalten");
        assertTrue(Wolkenwerte.VORGABE.biomwahl());
    }

    @Test
    @DisplayName("Die Obergrenze ist die Zahl der Felder, die es wirklich gibt")
    void obergrenzeKommtAusDerWelt() {
        // Als ZAHL eingetragen und nicht als Aufruf, weil die Weltkante aus der Datei
        // kommt und wachsen kann - eine Obergrenze, die mitwaechst, waere keine. Diese
        // Probe ist die Verbindung zwischen der Zahl und der Welt: Wer das Raster
        // aendert, wird hier rot und nicht erst beim Betreiber, der 1443 eintraegt und
        // eine Klage bekommt.
        assertEquals(Wolkengrenze.wolkenwerder().inselnZahl(), Wolkenwerte.HOECHSTE_ZAHL);
    }

    @Test
    @DisplayName("Die 0 heisst zweierlei - und beides ist erlaubt")
    void dieNullHeisstZweierlei() {
        assertEquals(0L, Wolkenwerte.VORGABE.mitPreis(0L).preis(),
                "Preis 0: gratis, der ausgelieferte Zustand");
        assertEquals(0, Wolkenwerte.VORGABE.mitHoechstzahl(0).hoechstzahl(),
                "Hoechstzahl 0: es wird keine vergeben - so wird Wolkenwerder"
                        + " zugemacht, ohne das Recht anzufassen");
        assertFalse(Wolkenwerte.VORGABE.mitBiomen(List.of()).biomwahl(),
                "eine leere Biomliste: keine Wahl");
    }

    @Test
    @DisplayName("Ausserhalb der Grenzen wird abgewiesen")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitPreis(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitPreis(Wolkenwerte.HOECHSTER_BETRAG + 1));
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitHoechstzahl(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitHoechstzahl(Wolkenwerte.HOECHSTE_ZAHL + 1));
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitBiomen(null));
    }

    @Test
    @DisplayName("Ein Biom, das es nicht gibt, wird abgewiesen - mit der Liste dazu")
    void unbekanntesBiomWirdAbgewiesen() {
        // Der Riegel gegen den Namen, der durchrutscht: Ein Biom in dieser Liste, das
        // Minecraft nicht kennt, waere eines, das der Befehl anbietet und das Plugin
        // nicht setzen kann - eine Absage mitten im Spiel, ohne Grund.
        var wurf = assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitBiomen(List.of("plains", "mondwueste")));

        assertTrue(wurf.getMessage().contains("mondwueste"), wurf.getMessage());
        assertTrue(wurf.getMessage().contains("swamp"),
                "die Meldung nennt, was erlaubt ist: " + wurf.getMessage());
    }

    @Test
    @DisplayName("Ein doppeltes Biom wird abgewiesen, Gross und klein ist egal")
    void doppeltUndGrossgeschrieben() {
        assertThrows(IllegalArgumentException.class,
                () -> Wolkenwerte.VORGABE.mitBiomen(List.of("swamp", "SWAMP")),
                "zweimal angeboten macht es nicht waehlbarer, aber die Auswahl laenger");
        Wolkenwerte gross = Wolkenwerte.VORGABE.mitBiomen(List.of("  Swamp  "));

        assertEquals(List.of("swamp"), gross.biome(),
                "beschnitten und klein abgelegt, wie jeder Name im Projekt");
        assertTrue(gross.bietetBiom("SWAMP"), "und so auch gefragt");
        assertFalse(gross.bietetBiom("plains"));
    }

    @Test
    @DisplayName("Die Biomliste laesst sich von aussen nicht aendern")
    void biomlisteWirdKopiert() {
        var meine = new java.util.ArrayList<String>();
        meine.add("plains");
        Wolkenwerte werte = Wolkenwerte.VORGABE.mitBiomen(meine);

        meine.add("swamp");

        assertEquals(List.of("plains"), werte.biome(),
                "sonst waere die Liste, die bei jeder Biomwahl gelesen wird, von aussen"
                        + " veraenderlich - und zwar aus einem anderen Faden");
    }

    @Test
    @DisplayName("Jeder Abwandler laesst seine Nachbarn stehen")
    void abwandlerLassenNachbarnStehen() {
        Wolkenwerte eigene = new Wolkenwerte(5_000L, 7, List.of("swamp"),
                java.util.Map.of("ice", 1), Erzwerte.VORGABE.mitAn(false),
                Aufgabenwerte.VORGABE, Punktwerte.VORGABE, 500L);

        assertEquals(7, eigene.mitPreis(1L).hoechstzahl());
        assertEquals(List.of("swamp"), eigene.mitPreis(1L).biome());
        assertEquals(5_000L, eigene.mitHoechstzahl(9).preis());
        assertEquals(5_000L, eigene.mitBiomen(List.of("taiga")).preis());
        assertEquals(7, eigene.mitBiomen(List.of("taiga")).hoechstzahl());
        assertEquals(java.util.Map.of("ice", 1),
                eigene.mitBiomen(List.of("taiga")).starttruhe(),
                "und die Truhe bleibt auch stehen");
        assertEquals(5_000L, eigene.mitStarttruhe(java.util.Map.of()).preis());
        assertEquals(List.of("swamp"), eigene.mitStarttruhe(java.util.Map.of()).biome());
        assertEquals(eigene.erze(), eigene.mitPreis(1L).erze(),
                "und die Erztabelle bleibt bei JEDEM Abwandler stehen - sie ist der"
                        + " Reiz dieser Welt, und ein Preiswechsel darf sie nicht"
                        + " zuruecksetzen");
        assertEquals(Erzwerte.VORGABE, eigene.mitErzen(Erzwerte.VORGABE).erze());
        assertEquals(5_000L, eigene.mitErzen(Erzwerte.VORGABE).preis());
        assertEquals(500L, eigene.mitPunkten(Punktwerte.VORGABE).biompreis(),
                "und der Biompreis bleibt bei JEDEM Abwandler stehen");
        assertEquals(7, eigene.mitBiompreis(1L).hoechstzahl());
    }

    @Test
    @DisplayName("Ohne Erzwerte ist es ein Programmfehler, nicht ein stiller Generator")
    void ohneErzwerte() {
        // Ein null hier waere ein Wurf bei JEDEM gebildeten Block - und zwar erst im
        // Spiel. Wer den Generator abschalten will, nimmt mitAn(false).
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwerte(0L, 1, List.of("plains"), java.util.Map.of(),
                        null, Aufgabenwerte.VORGABE, Punktwerte.VORGABE, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwerte(0L, 1, List.of("plains"), java.util.Map.of(),
                        Erzwerte.VORGABE, null, Punktwerte.VORGABE, 0L),
                "und ohne Aufgabenliste gibt es keine Stufe");
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwerte(0L, 1, List.of("plains"), java.util.Map.of(),
                        Erzwerte.VORGABE, Aufgabenwerte.VORGABE, null, 0L),
                "und ohne Punktetabelle keine Rangliste");
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwerte(0L, 1, List.of("plains"), java.util.Map.of(),
                        Erzwerte.VORGABE, Aufgabenwerte.VORGABE, Punktwerte.VORGABE,
                        -1L),
                "und ein negativer Biompreis ist keiner");
        assertEquals(Erzwerte.VORGABE, Wolkenwerte.VORGABE.erze(),
                "ab Werk ist der Generator an - er ist der Grund fuer diese Welt");
    }

    @Test
    @DisplayName("Die Wolkenwerte liegen im EINEN Wirtschaftssatz")
    void imWirtschaftssatz() {
        // Der Grund, aus dem sie dort liegen: Ein Neuladen tauscht den ganzen Satz als
        // EIN Stueck.
        assertEquals(Wolkenwerte.VORGABE, Wirtschaftswerte.VORGABE.wolken());

        Wirtschaftswerte anders = Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitHoechstzahl(3));

        assertEquals(3, anders.wolken().hoechstzahl());
        assertEquals(Wirtschaftswerte.VORGABE.angeln(), anders.angeln(),
                "und der Nachbarblock bleibt stehen - ein Wandler, der ihn vergisst,"
                        + " setzte beim Inselwechsel die ganze Angeltabelle zurueck");
        assertEquals(Wirtschaftswerte.VORGABE.inseln(), anders.inseln());
    }
}
