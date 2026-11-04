package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.ZoneId;
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
        return satz(grundpreis, jeFeld, startguthaben, hoechstzahl, gruppenFelder, werbung,
                Zeitwerte.VORGABE);
    }

    private static Wirtschaftswerte satz(long grundpreis, long jeFeld, long startguthaben,
                                         int hoechstzahl, int gruppenFelder, String werbung,
                                         Zeitwerte zeit) {
        return new Wirtschaftswerte(new Preisstaffel(grundpreis, jeFeld), startguthaben,
                hoechstzahl, gruppenFelder, werbung, zeit);
    }

    /** Ein Zeitsatz, bei dem alle drei Werte von der Vorgabe abweichen. */
    private static Zeitwerte anderezeit() {
        return new Zeitwerte(ZoneId.of("Pacific/Auckland"), Duration.ofSeconds(120),
                Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Die neuladbaren Zahlen und der Text werden getauscht")
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
    @DisplayName("Von den Zeitwerten wird nur die Untaetigkeitsgrenze getauscht")
    void nurDieUntaetigkeitWirdNeuGeladen() {
        // Die Zone verschoebe die Tagesgrenze mitten am Tag, der Takt steckt im gestellten
        // Zeitgeber - ein zweiter Zeitgeber schriebe dieselbe Spielzeit doppelt. Die Grenze
        // dagegen wird beim Gebrauch gefragt und kostet nichts.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, anderezeit()));

        assertEquals(ZoneId.of("Europe/Berlin"), stand.werte().zeit().zone(),
                "ein Neuladen darf die Tagesgrenze nicht verschieben");
        assertEquals(60L, stand.werte().zeit().taktSekunden(),
                "ein Neuladen darf den Zeitgeber nicht neu stellen");
        assertEquals(30L, stand.werte().zeit().untaetigMinuten(),
                "die Untaetigkeitsgrenze wird beim Gebrauch gefragt, also ist sie neuladbar");

        assertEquals(1, uebernahme.geaendert().size(), uebernahme.geaendert().toString());
        assertEquals(Zeitwerte.PFAD_UNTAETIG + ": 10 -> 30",
                uebernahme.geaendert().getFirst());
    }

    @Test
    @DisplayName("Zone und Takt werden mit dem gelesenen Wert genannt, wenn er abweicht")
    void zoneUndTaktWerdenGenannt() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var zeilen = stand.nichtAngefasst(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, anderezeit()));

        String zone = zeilen.get(1);
        assertTrue(zone.contains("Europe/Berlin"), "was weiter gilt: " + zone);
        assertTrue(zone.contains("Pacific/Auckland"), "was gelesen wurde: " + zone);
        assertTrue(zone.contains("Neustart"), zone);

        String takt = zeilen.get(2);
        assertTrue(takt.contains("60"), "was weiter gilt: " + takt);
        assertTrue(takt.contains("120"), "was gelesen wurde: " + takt);

        // Keine Zeile darf behaupten, der gelesene Wert STEHE in der Datei. Fehlt die
        // Zeile dort, holt der Leser die Vorgabe stumm aus der Jar und reicht sie herein -
        // die Antwort lautete dann "es gilt weiter America/New_York, in der Datei steht
        // Europe/Berlin", und der Betreiber suchte eine Zeile, die es nicht gibt. Ob
        // gelesen oder zurueckgefallen, sieht allein der Leser; dieser Halter sieht nur
        // einen Wert.
        for (String zeile : zeilen) {
            assertFalse(zeile.contains("in der Datei"),
                    "diese Zeile behauptet eine Dateizeile, die fehlen kann: " + zeile);
        }
    }

    @Test
    @DisplayName("Eine Grenze, die zum LAUFENDEN Takt nicht passt, bleibt stehen statt zu werfen")
    void grenzeOhneDeckungDurchDenLaufendenTaktBleibtStehen() {
        // Die Falle, die erst hier entsteht: Das Paar der DATEI stimmt (60 s Takt brauchen
        // 1 Minute Grenze), das Paar im BETRIEB nicht - der Takt wird nur beim Start
        // gelesen und ist noch 600 s lang. Vorher nahm dieser Weg die Kombination stumm an
        // und warf damit jeden Takt ganz weg, auch die aktiv gespielte Zeit darin; seit
        // der Querpruefung im Konstruktor wuerde er mitten im Befehl werfen. Beides ist
        // falsch: Ein Neuladen bricht nicht ab.
        var langerTakt = new Zeitwerte(ZoneId.of("Europe/Berlin"), Duration.ofSeconds(600),
                Duration.ofMinutes(10));
        var stand = new Wirtschaftsstand(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, langerTakt));
        var ausDerDatei = satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG,
                new Zeitwerte(ZoneId.of("Europe/Berlin"), Duration.ofSeconds(60),
                        Duration.ofMinutes(1)));

        var uebernahme = stand.uebernimm(ausDerDatei);

        assertEquals(10L, stand.werte().zeit().untaetigMinuten(),
                "die alte Grenze bleibt stehen, solange der lange Takt laeuft");
        assertEquals(600L, stand.werte().zeit().taktSekunden(),
                "und der Takt sowieso");
        assertTrue(uebernahme.geaendert().isEmpty(),
                "nicht uebernommen heisst auch nicht als geaendert gemeldet: "
                        + uebernahme.geaendert());

        assertEquals(4, uebernahme.nichtAngefasst().size(),
                "Startguthaben, Zone, Takt und die Grenze: "
                        + uebernahme.nichtAngefasst());
        String zeile = uebernahme.nichtAngefasst().getLast();
        assertTrue(zeile.startsWith(Zeitwerte.PFAD_UNTAETIG), zeile);
        assertTrue(zeile.contains("600"), "der laufende Takt ist der Grund: " + zeile);
        assertTrue(zeile.contains("Mindestens 10"),
                "und die kleinste Zahl, die dazu passt: " + zeile);
    }

    @Test
    @DisplayName("Der Block nennt genau die Schluessel, die ein Neuladen nicht uebernimmt")
    void blockUndListeLaufenNichtAuseinander() {
        // Der Waechter gegen das Auseinanderlaufen: Der Leser der Einstellungsdatei
        // schreibt seine Saetze nach NUR_BEIM_START. Nennte der Block einen Schluessel
        // mehr oder weniger, sagten Leser und Halter in derselben Antwort Verschiedenes -
        // genau der Fehler, der einmal zwei Tagesgrenzen in eine Antwort gebracht hat.
        var zeilen = new Wirtschaftsstand(Wirtschaftswerte.VORGABE).nichtAngefasst(null);

        assertEquals(Wirtschaftsstand.NUR_BEIM_START.size(), zeilen.size(),
                "eine Zeile je Schluessel: " + zeilen);
        for (int i = 0; i < zeilen.size(); i++) {
            assertTrue(zeilen.get(i).startsWith(Wirtschaftsstand.NUR_BEIM_START.get(i)),
                    "Zeile " + i + " gehoert zu "
                            + Wirtschaftsstand.NUR_BEIM_START.get(i) + ": " + zeilen.get(i));
        }
        assertFalse(Wirtschaftsstand.NUR_BEIM_START.contains(Zeitwerte.PFAD_UNTAETIG),
                "die Untaetigkeitsgrenze IST neuladbar - nur das eine unpassende Paar"
                        + " bleibt stehen, und das ist keine Eigenschaft des Schluessels");
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
        assertEquals(3, uebernahme.nichtAngefasst().size(),
                "Startguthaben, Zone und Takt: " + uebernahme.nichtAngefasst());
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

        assertEquals(3, zeilen.size(), "Startguthaben, Zone und Takt: " + zeilen);
        assertTrue(zeilen.getFirst().contains(Wirtschaftswerte.PFAD_STARTGUTHABEN));
        assertTrue(zeilen.getFirst().contains("50000"));
        assertTrue(zeilen.get(1).contains("Europe/Berlin"), zeilen.get(1));
        assertTrue(zeilen.get(2).contains(Zeitwerte.PFAD_TAKT), zeilen.get(2));
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
        assertEquals(ZoneId.of("Europe/Berlin"), Wirtschaftswerte.VORGABE.zeit().zone());
        assertEquals(60L, Wirtschaftswerte.VORGABE.zeit().taktSekunden());
        assertEquals(10L, Wirtschaftswerte.VORGABE.zeit().untaetigMinuten());
    }

    @Test
    @DisplayName("Ein Stand ohne Zeitwerte kommt nicht zustande")
    void zeitIstPflicht() {
        assertThrows(IllegalArgumentException.class,
                () -> satz(25_000L, 10_000L, 50_000L, 4, 4, "", null));
    }
}
