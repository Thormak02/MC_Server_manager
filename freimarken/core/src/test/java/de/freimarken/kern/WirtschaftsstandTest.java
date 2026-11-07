package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        return satz(grundpreis, jeFeld, startguthaben, hoechstzahl, gruppenFelder, werbung,
                zeit, Berufswerte.VORGABE, Verdienstwerte.VORGABE);
    }

    private static Wirtschaftswerte satz(long grundpreis, long jeFeld, long startguthaben,
                                         int hoechstzahl, int gruppenFelder, String werbung,
                                         Zeitwerte zeit, Berufswerte berufe) {
        return satz(grundpreis, jeFeld, startguthaben, hoechstzahl, gruppenFelder, werbung,
                zeit, berufe, Verdienstwerte.VORGABE);
    }

    private static Wirtschaftswerte satz(long grundpreis, long jeFeld, long startguthaben,
                                         int hoechstzahl, int gruppenFelder, String werbung,
                                         Zeitwerte zeit, Berufswerte berufe,
                                         Verdienstwerte verdienst) {
        // Die eine Stelle, die den VOLLEN Satz baut, weil sie jeden Teil variiert.
        // Alle anderen Proben nehmen die Abwandler und nennen nur ihren Unterschied.
        return new Wirtschaftswerte(new Preisstaffel(grundpreis, jeFeld), startguthaben,
                hoechstzahl, gruppenFelder, werbung, zeit, berufe, verdienst,
                Marktwerte.VORGABE, Messwerte.VORGABE);
    }

    /**
     * Ein Verdienstsatz, bei dem JEDER Teil von der Vorgabe abweicht.
     *
     * <p>Jeder, und das ist der Zweck: An dieser Methode haengt die Probe, die prueft,
     * dass das Neuladen jede Aenderung NENNT. Ein Teil, der hier gleich bleibt, wird dort
     * nie vermisst - und der Betreiber verstellt ihn, liest keine Zeile darueber und
     * dreht beim naechsten Mal weiter, obwohl es schon gilt.
     */
    private static Verdienstwerte andereverdienste() {
        return Verdienstwerte.VORGABE.mitMinderung(9_000L, 50, 25, 75, 20)
                .mitStundenmenge(Taetigkeit.ABBAUEN, 123)
                .mitStundenmenge(Taetigkeit.SETZEN, 124)
                .mitStundenmenge(Taetigkeit.ERNTEN, 125)
                .mitStundenmenge(Taetigkeit.FISCHEN, 126)
                .mitStundenmenge(Taetigkeit.TOETEN, 127)
                .mitStundenmenge(Taetigkeit.ZUECHTEN, 128)
                .mitSpawngruenden(java.util.Set.of("natural", "raid"))
                .mitSaatgut(java.util.Set.of("wheat"))
                .mitLoehnen(Lohntafel.ausLoehnen(java.util.List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone",
                                99L))));
    }

    /** Ein Berufssatz, bei dem alle vier Zahlen von der Vorgabe abweichen. */
    private static Berufswerte andereberufe() {
        return new Berufswerte(7_500L, Duration.ofHours(48L), 3, 2);
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
    @DisplayName("Alle vier Berufszahlen werden getauscht")
    void berufszahlenWerdenGetauscht() {
        // Anders als bei den Zeitwerten bleibt hier KEINE stehen: Alle vier werden beim
        // Gebrauch gefragt, es wird nichts gebaut und nichts angemeldet. Ohne diese Probe
        // aenderte der Betreiber die Gebuehr, /freimarken neuladen meldete gruen "nichts
        // geaendert", und im Spiel gaelte die alte Zahl weiter.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE,
                andereberufe()));

        assertEquals(7_500L, stand.werte().berufe().wechselgebuehr());
        assertEquals(48L, stand.werte().berufe().sperrfristStunden());
        assertEquals(3, stand.werte().berufe().grundberufe());
        assertEquals(2, stand.werte().berufe().spezialisierungen());

        assertEquals(4, uebernahme.geaendert().size(),
                "vier Zeilen haben sich geaendert: " + uebernahme.geaendert());
        assertTrue(uebernahme.geaendert().contains(
                Berufswerte.PFAD_WECHSELGEBUEHR + ": 5000 -> 7500"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                Berufswerte.PFAD_SPERRFRIST + ": 24 -> 48"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                Berufswerte.PFAD_GRUNDBERUFE + ": 2 -> 3"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                Berufswerte.PFAD_SPEZIALISIERUNGEN + ": 1 -> 2"),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Alle Verdienstzahlen UND die Lohntafel werden getauscht")
    void verdienstzahlenWerdenGetauscht() {
        // Die Lohntafel ist der Hebel, mit dem der Betreiber auf 2.000 bis 5.000 Mark je
        // Spielstunde einstellt - er soll das im Betrieb tun koennen. Ohne diese Probe
        // aenderte er einen Lohn, /freimarken neuladen meldete gruen "nichts geaendert",
        // und im Spiel gaelte der alte Satz weiter.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE,
                Berufswerte.VORGABE, andereverdienste()));

        assertEquals(9_000L, stand.werte().verdienst().tagesdeckel());
        assertEquals(50, stand.werte().verdienst().vollBis());
        assertEquals(25, stand.werte().verdienst().stufe());
        assertEquals(75, stand.werte().verdienst().faktorProzent());
        assertEquals(20, stand.werte().verdienst().bodenProzent());
        assertEquals(1, stand.werte().verdienst().loehne().anzahl());

        // Geprueft wird der TEXT jeder erwarteten Zeile und nicht ihre ZAHL: Eine
        // Zaehltafel laesst sich hochdrehen, und sie wird blind, sobald ein Teil
        // dazukommt - genau dann, wenn man sie braucht. Jeder Teil des Satzes muss hier
        // namentlich auftauchen, sonst wird er beim Neuladen stumm uebernommen.
        assertTrue(uebernahme.geaendert().contains(
                Verdienstwerte.PFAD_TAGESDECKEL + ": 20000 -> 9000"),
                uebernahme.geaendert().toString());
        for (String pfad : new String[] {Verdienstwerte.PFAD_VOLL_BIS,
                                         Verdienstwerte.PFAD_STUFE,
                                         Verdienstwerte.PFAD_FAKTOR,
                                         Verdienstwerte.PFAD_BODEN}) {
            assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                            -> zeile.startsWith(pfad + ":")),
                    pfad + " fehlt in der Meldung: " + uebernahme.geaendert());
        }
        // JEDE Taetigkeit mit ihrer Stundenmenge - sie ist der wirksame Hebel gegen
        // Farmen, und wer sie verstellt, ohne eine Zeile darueber zu lesen, dreht beim
        // naechsten Mal weiter, obwohl sie schon gilt.
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            String pfad = Verdienstwerte.PFAD_STUNDENMENGE + "."
                    + taetigkeit.schluessel();
            assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                            -> zeile.startsWith(pfad + ":")),
                    pfad + " fehlt in der Meldung: " + uebernahme.geaendert());
        }
        // Die zwei Listen MIT dem Unterschied: An der Weissliste haengt, ob eine
        // Spawnerfarm zahlt, am Saatgut, ob ein Acker etwas einbringt. Eine nackte
        // Laenge liesse einen ausgetauschten Namen durch.
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                        -> zeile.startsWith(Verdienstwerte.PFAD_SPAWNGRUENDE + ":")
                        && zeile.contains("weg: ")),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                        -> zeile.startsWith(Verdienstwerte.PFAD_SAATGUT + ":")
                        && zeile.contains("weg: ")),
                uebernahme.geaendert().toString());
        // Die Tafel wird VERGLICHEN und nicht gezaehlt: Eine Zeile mit einem anderen Lohn
        // laesst die Zeilenzahl gleich, und genau das ist die haeufigste Aenderung.
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                        -> zeile.startsWith(Verdienstwerte.PFAD_LOEHNE)
                        && zeile.contains("geaendert")),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Eine Lohntafel mit gleicher Zeilenzahl, aber anderem Lohn faellt auf")
    void andererLohnGleicheZahl() {
        // Der Fall, den eine Zaehlung nicht sieht - und der haeufigste beim Einstellen.
        var eineZeile = Verdienstwerte.VORGABE.mitLoehnen(Lohntafel.ausLoehnen(
                java.util.List.of(new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN,
                        "stone", 40L))));
        var andererLohn = Verdienstwerte.VORGABE.mitLoehnen(Lohntafel.ausLoehnen(
                java.util.List.of(new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN,
                        "stone", 41L))));
        var stand = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitVerdienst(eineZeile));

        var uebernahme = stand.uebernimm(
                Wirtschaftswerte.VORGABE.mitVerdienst(andererLohn));

        assertEquals(41L, stand.werte().verdienst().loehne()
                .pfennige("bergmann", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(1, uebernahme.geaendert().size(), uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().getFirst().contains("1 -> 1 Zeilen (geaendert)"),
                uebernahme.geaendert().getFirst());
    }

    @Test
    @DisplayName("Die Berufszahlen stehen nicht im Block dessen, was nicht angefasst wird")
    void berufszahlenStehenNichtImBlock() {
        // Sie sind neuladbar, also hat dieser Block ueber sie nichts zu sagen. Stuenden
        // sie darin, lernte der Betreiber, dass eine Aenderung erst mit dem Neustart
        // gilt - und wartete auf etwas, das schon passiert ist.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var zeilen = stand.nichtAngefasst(satz(25_000L, 10_000L, 50_000L, 4, 4,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE,
                andereberufe()));

        for (String zeile : zeilen) {
            assertFalse(zeile.contains("berufe."), zeile);
        }
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

    @Test
    @DisplayName("Ein Stand ohne Messwerte kommt nicht zustande")
    void messungIstPflicht() {
        assertThrows(IllegalArgumentException.class,
                () -> Wirtschaftswerte.VORGABE.mitMessung(null));
    }

    @Test
    @DisplayName("Das Neuladen nennt JEDE geaenderte Zeile der Messstelle")
    void neuladenNenntDieMessstelle() {
        // Geprueft wird der TEXT jeder erwarteten Zeile und nicht ihre Zahl: Eine
        // Zaehltafel laesst sich hochdrehen und wird blind, sobald ein Teil dazukommt.
        // Wer eine Messzeile verstellt, ohne eine Zeile darueber zu lesen, dreht beim
        // naechsten Mal weiter, obwohl sie schon gilt.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Set<Lebenszeichen> andere = new LinkedHashSet<>(List.of(Lebenszeichen.ABBAUEN,
                Lebenszeichen.BEWEGEN, Lebenszeichen.BENUTZEN));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitMessung(
                new Messwerte(1, 40, andere)));

        assertTrue(uebernahme.geaendert().contains(
                        Messwerte.PFAD_VERLANGTE_ARTEN + ": 2 -> 1"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(Messwerte.PFAD_STRECKE + ": 8 -> 40"),
                uebernahme.geaendert().toString());
        // Die Liste mit "dazu:" und "weg:", nicht nur ihre Laenge.
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                        -> zeile.startsWith(Messwerte.PFAD_ARTEN + ":")
                        && zeile.contains("dazu: benutzen")
                        && zeile.contains("weg: ")),
                "die Artenliste muss mit dem Unterschied genannt werden: "
                        + uebernahme.geaendert());
        assertEquals(1, stand.werte().messung().verlangteArten());
        assertEquals(40, stand.werte().messung().mindestStrecke());
        assertEquals(andere, stand.werte().messung().arten());
    }

    @Test
    @DisplayName("Eine getauschte Art bei GLEICHER Laenge wird auch genannt")
    void getauschteArtWirdGenannt() {
        // Der Fall, den eine Zaehlung nie sieht: sechs Arten vorher, sechs nachher - und
        // eine andere Messung. Wer kaempfen gegen benutzen tauscht, macht ein Setzmakro
        // taetig, und das darf nicht stumm geschehen.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Set<Lebenszeichen> getauscht = new LinkedHashSet<>(Messwerte.VORGABE_ARTEN);
        getauscht.remove(Lebenszeichen.KAEMPFEN);
        getauscht.add(Lebenszeichen.BENUTZEN);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitMessung(
                Messwerte.VORGABE.mitArten(getauscht, 2)));

        assertEquals(Messwerte.VORGABE_ARTEN.size(), getauscht.size(),
                "die Laengen muessen GLEICH sein, sonst prueft diese Probe die Zaehlung");
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile
                        -> zeile.startsWith(Messwerte.PFAD_ARTEN + ":")
                        && zeile.contains("dazu: benutzen")
                        && zeile.contains("weg: kaempfen")),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Eine unveraenderte Messstelle steht NICHT in der Meldung")
    void unveraendertSchweigt() {
        // Die Gegenprobe: Waere die Zeile immer da, sagte sie nichts. Eine Meldung, in
        // der jede Zeile steht, liest niemand mehr.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertTrue(uebernahme.geaendert().stream().noneMatch(zeile
                        -> zeile.startsWith("messstelle.")),
                uebernahme.geaendert().toString());
    }
}
