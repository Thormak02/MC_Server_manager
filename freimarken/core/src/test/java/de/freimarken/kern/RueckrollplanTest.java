package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rueckrollrechnung - jede Entscheidung einzeln.
 *
 * <p><b>Warum diese Proben so genau sind:</b> Ein Rueckweg aendert die Welt, und zwar
 * nicht umkehrbar. Eine Rechnung, die den Zwischenstand statt des Ausgangsstands
 * herstellt, sieht nach Erfolg aus - der Bau steht wieder da, nur anders als vorher -,
 * und niemand merkt es, bis der Besitzer zurueckkommt. Darum ist hier jede Regel ein
 * eigener Satz mit einem Gegenbeispiel daneben.
 */
class RueckrollplanTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");
    private static final String WELT = "freimarken:marktstadt";
    private static final Instant ZEHN_UHR = Instant.parse("2026-11-26T10:00:00Z");

    private static Blockort ort(int x, int y, int z) {
        return new Blockort(WELT, x, y, z);
    }

    /** Eine Zeile mit Nummer, Zeitpunkt in Sekunden nach zehn Uhr und Vorzustand. */
    private static Blockzeile zeile(long id, int sekunden, Blockort wo, Blocktat tat,
                                    String block, String vorzustand) {
        return new Blockzeile(id, ZEHN_UHR.plusSeconds(sekunden), ANNA, wo, tat, true,
                block, vorzustand);
    }

    // -------------------------------------------------- Was nicht zaehlt

    @Test
    @DisplayName("1 - eine leere Liste ergibt einen leeren Plan und ist kein Fehler")
    void leereListe() {
        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of());

        assertTrue(plan.leer());
        assertEquals(List.of(), plan.nummern());
        assertEquals(0, plan.gelesen());
    }

    @Test
    @DisplayName("1b - ohne Liste wirft es, statt einen leeren Plan zu erfinden")
    void ohneListe() {
        assertThrows(IllegalArgumentException.class, () -> Rueckrollplan.aus(null));
    }

    @Test
    @DisplayName("2 - eine MISSLUNGENE Zeile aendert nichts und zaehlt nicht")
    void misslungenZaehltNicht() {
        // Der wertvollste Teil des Blockbuchs fuer die Moderation - und fuer einen
        // Rueckweg nichts: Der Block steht noch, ein Schutz hat abgebrochen.
        Blockzeile abgewiesen = new Blockzeile(1L, ZEHN_UHR, ANNA, ort(0, 64, 0),
                Blocktat.ABBAUEN, false, "stone", "minecraft:stone");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(abgewiesen));

        assertTrue(plan.leer(), plan.setzungen().toString());
        assertEquals(List.of(), plan.nummern(),
                "eine Zeile, die nichts geaendert hat, wird auch nicht verbraucht -"
                        + " sonst verschwaende ein Rueckweg sie aus dem Protokoll");
    }

    @Test
    @DisplayName("2b - BENUTZEN aendert die Welt nicht und bleibt im Buch stehen")
    void benutzenZaehltNicht() {
        Blockzeile truhe = zeile(1L, 0, ort(0, 64, 0), Blocktat.BENUTZEN, "chest", "");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(truhe));

        assertTrue(plan.leer());
        assertEquals(List.of(), plan.nummern(),
                "die Frage \"wer war in meiner Truhe\" wird durch einen Rueckweg nicht"
                        + " beantwortet - die Zeile muss bleiben");
    }

    // -------------------------------------------------- Die tragende Regel

    @Test
    @DisplayName("3 - je Koordinate gilt die AELTESTE Zeile, nicht die neueste")
    void aeltesteJeOrt() {
        // DIE ENTSCHEIDENDE PROBE. Ein Griefer baut Eichenholz ab und setzt Netherrack
        // hin. Richtig ist Eichenholz zurueck - mit der neuesten Zeile kaeme Luft, also
        // der Zwischenstand, und der sieht wie ein geglueckter Rueckweg aus.
        Blockort wo = ort(5, 64, 5);
        Blockzeile abbau = zeile(1L, 0, wo, Blocktat.ABBAUEN, "oak_log",
                "minecraft:oak_log[axis=y]");
        Blockzeile setzung = zeile(2L, 30, wo, Blocktat.SETZEN, "netherrack",
                "minecraft:air");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(abbau, setzung));

        assertEquals(1, plan.setzungen().size(), plan.setzungen().toString());
        assertEquals("minecraft:oak_log[axis=y]", plan.setzungen().get(0).zustand());
        assertTrue(!plan.setzungen().get(0).wegnehmen(),
                "es wird etwas WIEDERHERGESTELLT, nicht weggenommen");
        // UND BEIDE Zeilen sind verbraucht - sonst saehe ein zweiter Lauf die Setzung
        // und stellte Luft her.
        assertEquals(List.of(1L, 2L), plan.nummern());
        assertEquals(2, plan.gelesen());
    }

    @Test
    @DisplayName("3b - die Reihenfolge der Eingabe ist gleichgueltig")
    void eingabereihenfolgeGleichgueltig() {
        // Eine Rechnung, die von der Sortierung ihres Aufrufers abhaengt, ist hier gruen
        // und im Spiel falsch, sobald jemand eine zweite Abfrage dazubaut.
        Blockort wo = ort(5, 64, 5);
        Blockzeile abbau = zeile(1L, 0, wo, Blocktat.ABBAUEN, "oak_log",
                "minecraft:oak_log[axis=y]");
        Blockzeile setzung = zeile(2L, 30, wo, Blocktat.SETZEN, "netherrack",
                "minecraft:air");

        Rueckrollplan.Plan verkehrt = Rueckrollplan.aus(List.of(setzung, abbau));

        assertEquals("minecraft:oak_log[axis=y]", verkehrt.setzungen().get(0).zustand());
        assertEquals(List.of(1L, 2L), verkehrt.nummern(),
                "auch die Nummern stehen nach Zeit, nicht nach Eingabe");
    }

    @Test
    @DisplayName("3c - bei gleicher Millisekunde entscheidet die kleinere Nummer")
    void gleicheMillisekunde() {
        // Eine Sprengung schreibt hunderte Zeilen in einem Augenblick - ohne die Nummer
        // als zweites Merkmal waere die Reihenfolge dem Zufall ueberlassen.
        Blockort wo = ort(1, 64, 1);
        Blockzeile erste = zeile(10L, 0, wo, Blocktat.SPRENGEN, "stone",
                "minecraft:stone");
        Blockzeile zweite = zeile(11L, 0, wo, Blocktat.SETZEN, "tnt", "minecraft:air");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(zweite, erste));

        assertEquals("minecraft:stone", plan.setzungen().get(0).zustand());
    }

    // -------------------------------------------------- Setzung gegen Abbau

    @Test
    @DisplayName("4 - eine SETZUNG wird weggenommen, und zwar auf den Vorzustand")
    void setzungWirdWeggenommen() {
        // Wer in Wasser baut, ueberbaut Wasser - und bekommt Wasser zurueck, nicht Luft.
        // Genau dafuer gibt es die Spalte.
        Blockzeile imWasser = zeile(1L, 0, ort(2, 62, 2), Blocktat.SETZEN, "oak_planks",
                "minecraft:water[level=0]");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(imWasser));

        assertEquals("minecraft:water[level=0]", plan.setzungen().get(0).zustand());
        assertTrue(plan.setzungen().get(0).wegnehmen());
    }

    @Test
    @DisplayName("5 - ohne Vorzustand faellt eine SETZUNG auf Luft zurueck")
    void rueckfallSetzung() {
        // Zeilen aus der Zeit vor dem 26.11.2026 haben keinen Vorzustand - die Spalte
        // gab es nicht. Sie sind grob brauchbar und nicht wertlos.
        Blockzeile alt = zeile(1L, 0, ort(3, 64, 3), Blocktat.SETZEN, "cobblestone", "");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(alt));

        assertEquals(Rueckrollplan.LUFT, plan.setzungen().get(0).zustand());
        assertTrue(plan.setzungen().get(0).wegnehmen());
        assertEquals(1, Rueckrollplan.ohneVorzustand(List.of(alt)),
                "und der Befehl muss sagen, wie viele davon dabei waren");
    }

    @Test
    @DisplayName("5b - ohne Vorzustand faellt ein ABBAU auf den Block ohne Zustand zurueck")
    void rueckfallAbbau() {
        Blockzeile alt = zeile(1L, 0, ort(3, 64, 3), Blocktat.ABBAUEN, "oak_stairs", "");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(alt));

        assertEquals("minecraft:oak_stairs", plan.setzungen().get(0).zustand(),
                "die Treppe kommt wieder, ihre Blickrichtung nicht");
        assertTrue(!plan.setzungen().get(0).wegnehmen());
    }

    @Test
    @DisplayName("5c - MIT Vorzustand kommt die Blickrichtung mit")
    void mitVorzustandKommtDieRichtung() {
        // Das Gegenbeispiel zu 5b, und der ganze Grund fuer die Spalte: Ohne sie steht
        // eine zurueckgerollte Treppe in der falschen Richtung, und das sieht man.
        Blockzeile neu = zeile(1L, 0, ort(3, 64, 3), Blocktat.ABBAUEN, "oak_stairs",
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight]");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(neu));

        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]",
                plan.setzungen().get(0).zustand());
        assertEquals(0, Rueckrollplan.ohneVorzustand(List.of(neu)));
    }

    // -------------------------------------------------- Die Reihenfolge der Setzungen

    @Test
    @DisplayName("6 - erst wegnehmen von OBEN nach unten, dann herstellen von UNTEN nach oben")
    void reihenfolgeDerSetzungen() {
        // ZWEI GRUENDE, und beide sind im Spiel sichtbar:
        //
        // Wegnehmen von oben: Wer einen Turm von unten wegnimmt, laesst den Rest fallen.
        // Sand und Kies kommen dann an einer Stelle an, an der sie nie standen - der
        // Rueckweg hat den Schaden vergroessert.
        //
        // Herstellen von unten: Eine Fackel braucht den Block, an dem sie haengt. Kommt
        // sie zuerst, faellt sie im selben Augenblick wieder ab.
        List<Blockzeile> zeilen = List.of(
                zeile(1L, 0, ort(0, 70, 0), Blocktat.SETZEN, "sand", "minecraft:air"),
                zeile(2L, 1, ort(0, 72, 0), Blocktat.SETZEN, "sand", "minecraft:air"),
                zeile(3L, 2, ort(0, 60, 0), Blocktat.ABBAUEN, "stone", "minecraft:stone"),
                zeile(4L, 3, ort(0, 62, 0), Blocktat.ABBAUEN, "torch",
                        "minecraft:torch"));

        List<Rueckrollplan.Setzung> setzungen = Rueckrollplan.aus(zeilen).setzungen();

        assertEquals(4, setzungen.size());
        // Erst die zwei Wegnahmen, obere zuerst.
        assertEquals(72, setzungen.get(0).ort().y());
        assertEquals(70, setzungen.get(1).ort().y());
        assertTrue(setzungen.get(0).wegnehmen() && setzungen.get(1).wegnehmen());
        // Dann die zwei Wiederherstellungen, untere zuerst.
        assertEquals(60, setzungen.get(2).ort().y());
        assertEquals(62, setzungen.get(3).ort().y());
        assertTrue(!setzungen.get(2).wegnehmen() && !setzungen.get(3).wegnehmen());
    }

    @Test
    @DisplayName("6b - bei gleicher Hoehe entscheiden x und z, damit der Plan wiederholbar ist")
    void gleicheHoehe() {
        // Ohne diese zwei Stufen waere die Reihenfolge innerhalb einer Ebene die der
        // Karte, und die haengt am Zufall des Durchlaufs. Ein Plan, der zweimal anders
        // aussieht, laesst sich nicht vergleichen.
        List<Blockzeile> zeilen = List.of(
                zeile(1L, 0, ort(5, 64, 1), Blocktat.ABBAUEN, "stone", "minecraft:stone"),
                zeile(2L, 1, ort(1, 64, 5), Blocktat.ABBAUEN, "stone", "minecraft:stone"),
                zeile(3L, 2, ort(1, 64, 1), Blocktat.ABBAUEN, "stone",
                        "minecraft:stone"));

        List<Rueckrollplan.Setzung> setzungen = Rueckrollplan.aus(zeilen).setzungen();

        assertEquals(1, setzungen.get(0).ort().x());
        assertEquals(1, setzungen.get(0).ort().z());
        assertEquals(1, setzungen.get(1).ort().x());
        assertEquals(5, setzungen.get(1).ort().z());
        assertEquals(5, setzungen.get(2).ort().x());
    }

    // -------------------------------------------------- Mehrere Orte

    @Test
    @DisplayName("7 - jede Koordinate bekommt GENAU EINE Setzung")
    void eineSetzungJeOrt() {
        // Drei Taten an zwei Orten ergeben zwei Setzungen - und drei verbrauchte Zeilen.
        List<Blockzeile> zeilen = new ArrayList<>();
        zeilen.add(zeile(1L, 0, ort(1, 64, 1), Blocktat.ABBAUEN, "stone",
                "minecraft:stone"));
        zeilen.add(zeile(2L, 1, ort(1, 64, 1), Blocktat.SETZEN, "dirt",
                "minecraft:air"));
        zeilen.add(zeile(3L, 2, ort(2, 64, 2), Blocktat.SPRENGEN, "glass",
                "minecraft:glass"));

        Rueckrollplan.Plan plan = Rueckrollplan.aus(zeilen);

        assertEquals(2, plan.setzungen().size(), plan.setzungen().toString());
        assertEquals(3, plan.nummern().size(),
                "verbraucht wird JEDE Zeile, nicht nur die aelteste je Ort");
        assertEquals(3, plan.gelesen());
    }

    @Test
    @DisplayName("7b - eine Zeile ohne Nummer wird gerechnet, aber nicht verbraucht")
    void ohneNummerNichtVerbraucht() {
        // Eine Zeile, die nur im Arbeitsspeicher steht (OHNE_NUMMER), kann niemand als
        // zurueckgenommen merken. Sie darf trotzdem in die Rechnung - sonst waere eine
        // Probe ohne Ablage nicht moeglich.
        Blockzeile frisch = Blockzeile.neu(ZEHN_UHR, ANNA, ort(9, 64, 9),
                Blocktat.ABBAUEN, true, "stone", "minecraft:stone");

        Rueckrollplan.Plan plan = Rueckrollplan.aus(List.of(frisch));

        assertEquals(1, plan.setzungen().size());
        assertEquals(List.of(), plan.nummern());
    }

    @Test
    @DisplayName("8 - ein null-Eintrag wird uebergangen und reisst nichts ab")
    void nullEintrag() {
        List<Blockzeile> mitLuecke = new ArrayList<>();
        mitLuecke.add(null);
        mitLuecke.add(zeile(1L, 0, ort(0, 64, 0), Blocktat.ABBAUEN, "stone",
                "minecraft:stone"));

        assertEquals(1, Rueckrollplan.aus(mitLuecke).setzungen().size());
        assertEquals(0, Rueckrollplan.ohneVorzustand(mitLuecke));
    }

    @Test
    @DisplayName("9 - eine Setzung ohne Zustand gibt es nicht")
    void setzungBrauchtZustand() {
        // Auch Luft hat einen Zustandstext. Eine leere Zeichenfolge an setBlockData
        // waere ein Wurf mitten im Rueckweg - also hier abgewiesen.
        assertThrows(IllegalArgumentException.class,
                () -> new Rueckrollplan.Setzung(ort(0, 64, 0), "", false));
        assertThrows(IllegalArgumentException.class,
                () -> new Rueckrollplan.Setzung(null, Rueckrollplan.LUFT, false));
    }
}
