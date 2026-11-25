package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Die AUSGELIEFERTEN Zahlen der zwei Parzellengrenzen.
     *
     * <p>Als Verweis und nicht als Zahl: Der Vergleichsstand dieser Proben ist
     * {@code Wirtschaftswerte.VORGABE}, und eine Probe, die daneben eine eigene Zahl
     * hinschreibt, meldet jede Aenderung der Vorgabe als Fehler. Genau das ist am
     * 20.11.2026 passiert - Teil H senkte die beiden auf die Reisendenzahl, und sechs
     * Proben wurden rot, die mit den Grenzen nichts zu tun haben.
     *
     * <p>Wo eine Probe eine ANDERE Zahl braucht, steht sie weiter am Aufruf.
     */
    private static final int VORGABE_ZAHL = Wirtschaftswerte.VORGABE_HOECHSTZAHL;

    private static final int VORGABE_FELDER = Wirtschaftswerte.VORGABE_GRUPPEN_FELDER;

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
                Marktwerte.VORGABE, Messwerte.VORGABE,
                Bonuswerte.VORGABE, Mietwerte.VORGABE, Shopwerte.VORGABE,
                Truhenwerte.VORGABE, Freistadtwerte.VORGABE, Inselwerte.VORGABE,
                Angelwerte.VORGABE, Wolkenwerte.VORGABE, Rangwerte.VORGABE,
                Wechselwerte.VORGABE, Muenzwerte.VORGABE, Anmeldewerte.VORGABE,
                Lotteriewerte.VORGABE, Wettbewerbswerte.VORGABE, Teamwerte.VORGABE,
                Meldewerte.VORGABE, Strafwerte.VORGABE);
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
                Wirtschaftswerte.PFAD_HOECHSTZAHL + ": "
                        + Wirtschaftswerte.VORGABE_HOECHSTZAHL + " -> 6"),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Das Startguthaben bleibt stehen und wird genannt")
    void startguthabenBleibt() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 999_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var zeilen = stand.nichtAngefasst(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var zeilen = stand.nichtAngefasst(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var stand = new Wirtschaftsstand(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG, langerTakt));
        var ausDerDatei = satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
        var uebernahme = stand.uebernimm(satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER, "anders"));

        assertEquals(1, uebernahme.geaendert().size(), uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().getFirst()
                .startsWith(Wirtschaftswerte.PFAD_RANG_WERBUNG));
    }

    @Test
    @DisplayName("Der Deckel der Freien Truhe und ihre Verbotsliste werden getauscht")
    void truhenzahlenGetauscht() {
        // Beide sind neuladbar, und beide muessen im Bericht stehen: Wer den Deckel
        // verstellt, soll lesen, welche Zahl jetzt gilt, und nicht beim naechsten Mal
        // weiterdrehen. Die Verbotsliste wird VERGLICHEN und nicht gezaehlt - ein
        // getauschter Eintrag laesst die Laenge gleich.
        // Angefangen wird mit einem WIRKLICHEN Verbot: Ab Werk ist nichts verboten, und
        // dann hiesse "written_book ist nachher nicht verboten" nur, dass es vorher auch
        // nicht verboten war - die Zeile unten prueefte nichts.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitTruhe(
                new Truhenwerte(16, Set.of("written_book"))));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitTruhe(
                new Truhenwerte(32, Set.of("elytra"))));

        assertEquals(32, stand.werte().truhe().entnahmeJeTag());
        assertTrue(stand.werte().truhe().istVerboten("elytra"));
        assertFalse(stand.werte().truhe().istVerboten("written_book"),
                "die Liste wird getauscht und nicht ergaenzt");
        assertTrue(uebernahme.geaendert().stream()
                        .anyMatch(zeile -> zeile.startsWith(Truhenwerte.PFAD_ENTNAHME
                                + ": 16 -> 32")),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().stream()
                        .anyMatch(zeile -> zeile.startsWith(Truhenwerte.PFAD_VERBOTEN)),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Die zwei Auflagenschalter werden getauscht - und EINZELN genannt")
    void auflagenschalterGetauscht() {
        // FUND VOM 17.11.2026: Diese zwei fehlten in der Meldung, und damit war ihr
        // Neuladen voellig stumm. Wer das Ladenrecht nach einem Event wieder
        // einschaltet und "nichts geaendert" liest, glaubt das Neuladen wirke nicht -
        // oder die Regel sei kaputt.
        //
        // ANGEFANGEN WIRD MIT BEIDEN AUS, und umgelegt wird nur EINER: Waeren beide
        // gleich, saehe diese Probe nicht, ob der richtige Pfad gemeldet wird - und die
        // zwei stehen als benachbarte boolean im Satz Freistadtwerte.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitFreistaedten(
                Freistadtwerte.VORGABE.mitAuflageTeleporter(false)
                        .mitAuflageLadenrecht(false)));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitFreistaedten(
                Freistadtwerte.VORGABE.mitAuflageTeleporter(false)
                        .mitAuflageLadenrecht(true)));

        assertTrue(stand.werte().freistaedte().auflageLadenrecht());
        assertFalse(stand.werte().freistaedte().auflageTeleporter(),
                "der andere Schalter bleibt, wo er war");
        assertEquals(1, uebernahme.geaendert().size(),
                uebernahme.geaendert().toString());
        assertEquals(Freistadtwerte.PFAD_AUFLAGE_LADENRECHT + ": false -> true",
                uebernahme.geaendert().getFirst(),
                "der WORTLAUT, nicht die Zahl der Zeilen: Der Betreiber hat true in die"
                        + " Datei geschrieben und soll true lesen, keine 1");
    }

    @Test
    @DisplayName("Beide Kurse und der Wechseldeckel werden getauscht - und EINZELN genannt")
    void wechselzahlenGetauscht() {
        // Alle drei sind neuladbar: Sie werden beim Gebrauch gefragt, es wird nichts
        // gebaut und nichts angemeldet. Und alle drei MUESSEN im Bericht stehen - der
        // Kurs ist der Wert, an dem ein Betreiber am ehesten dreht, und ein stumm
        // geaenderter Kurs ist im Spiel von einem kaputten Tausch nicht zu unterscheiden.
        //
        // ALLE DREI auf einmal verstellt, und zwar auf verschiedene Zahlen: Waeren zwei
        // gleich, saehe diese Probe nicht, ob jede Zeile ihren eigenen Pfad nennt.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitWechsel(
                new Wechselwerte(2_000L, 1_500L, 7_000L)));

        assertEquals(2_000L, stand.werte().wechsel().markJeGoldmark());
        assertEquals(1_500L, stand.werte().wechsel().markZurueck());
        assertEquals(7_000L, stand.werte().wechsel().tagesdeckel());
        assertTrue(uebernahme.geaendert().contains(
                        Wechselwerte.PFAD_HIN + ": 1000 -> 2000"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Wechselwerte.PFAD_ZURUECK + ": 800 -> 1500"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Wechselwerte.PFAD_DECKEL + ": 10000 -> 7000"),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Der Muenzhauskatalog wird genannt: Schalter, Bestand UND Preis je Posten")
    void muenzhausGenannt() {
        // DREI GESTALTEN, drei Aufgaben: Der Schalter sagt, ob verkauft wird; die MENGE
        // sagt, welcher Posten dazukam oder wegfiel; und der PREIS je Posten ist der
        // Wert, an dem ein Betreiber am haeufigsten dreht.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var anders = Muenzwerte.VORGABE.mitKatalog(List.of(
                new Muenzwerte.Posten("kuerbishut", Map.of("carved_pumpkin", 1), 7L,
                        Map.of()),
                new Muenzwerte.Posten("neu", Map.of("bell", 1), 3L, Map.of())));

        var uebernahme = stand.uebernimm(
                Wirtschaftswerte.VORGABE.mitMuenzhaus(anders.mitAn(false)));

        assertTrue(uebernahme.geaendert().contains(Muenzwerte.PFAD_AN + ": true -> false"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile ->
                        zeile.startsWith(Muenzwerte.PFAD_KATALOG + ": 4 -> 2 Namen")
                                && zeile.contains("dazu: neu")
                                && zeile.contains("weg: blumenkiste")),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Muenzwerte.PFAD_KATALOG + ".kuerbishut.preis: 2 -> 7"),
                "der Preis je Posten, mit dem Posten im Pfad: "
                        + uebernahme.geaendert());
    }

    @Test
    @DisplayName("Ein neuer Posten bekommt KEINE Preiszeile - es ist sein erster Wert")
    void neuerPostenOhnePreiszeile() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var anders = Muenzwerte.VORGABE.mitKatalog(List.of(
                new Muenzwerte.Posten("neu", Map.of("bell", 1), 3L, Map.of())));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitMuenzhaus(anders));

        assertFalse(uebernahme.geaendert().stream().anyMatch(zeile ->
                        zeile.contains(".neu.preis")),
                "ein neuer Posten hat keinen alten Preis - die Menge nennt ihn schon: "
                        + uebernahme.geaendert());
    }

    @Test
    @DisplayName("Ein unveraendertes Muenzhaus schweigt")
    void unveraendertesMuenzhausSchweigt() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertFalse(uebernahme.geaendert().stream().anyMatch(zeile ->
                        zeile.startsWith("muenzhaus")), uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Die am 20.11. gefundenen stummen Werte werden jetzt genannt")
    void gefundeneStummeWerte() {
        // DER FUND, und darum stehen hier Werte aus fuenf verschiedenen Bereichen: Alle
        // waren neuladbar und wurden in uebernimm NIE genannt - das Neuladen war
        // vollstaendig stumm. Drei davon sind GELD (Miete, Kaution, Lizenz), also genau
        // die Art Wert, fuer die Teil F die Wechselkurse nachgetragen hat.
        //
        // Gehalten wird die Vollstaendigkeit ab jetzt von GrenzeTest 31cw; diese Probe
        // zeigt, dass die Zeilen wirklich entstehen und ihren Pfad nennen.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitLaeden(new Mietwerte(99L, 990L, 97L, 96, 95))
                .mitHandel(new Shopwerte(9, 8, 7, "Bundshop", 6))
                .mitWolken(Wolkenwerte.VORGABE.mitBiompreis(12_345L)));

        for (String pfad : List.of(Mietwerte.PFAD_MIETE, Mietwerte.PFAD_KAUTION,
                Mietwerte.PFAD_LIZENZ, Mietwerte.PFAD_DAUER, Mietwerte.PFAD_AUSFAELLE,
                Shopwerte.PFAD_SPIELER_KAUF, Shopwerte.PFAD_SPIELER_VERKAUF,
                Shopwerte.PFAD_BUND_VERKAUF, Shopwerte.PFAD_ZEILEN,
                Wolkenwerte.PFAD_BIOMPREIS)) {
            assertTrue(uebernahme.geaendert().stream().anyMatch(zeile ->
                            zeile.startsWith(pfad + ":")),
                    pfad + " wird beim Neuladen nicht genannt: "
                            + uebernahme.geaendert());
        }
    }

    @Test
    @DisplayName("Geaenderte Buergerrechte werden genannt - mit ihren Schluesseln")
    void buergerrechteGenannt() {
        // Die Zeile dafuer ist am 20.11.2026 gebaut worden, eine Probe hatte sie nie.
        // Gehalten hat sie allein GrenzeTest 31cw, und die prueft eine NENNUNG: dass der
        // Name PFAD_BUERGERRECHTE im Quelltext vorkommt. Mit
        // nenneMenge(..., taten(alt...), taten(alt...)) waere jene Tafel gruen und diese
        // Meldung wieder stumm - der Betreiber liest "nichts geaendert", und seine neuen
        // Rechte gelten trotzdem.
        //
        // Getauscht wird in BEIDE Richtungen, weil die Vorgabe die leere Menge ist: erst
        // ein Recht dazu, dann dasselbe wieder weg. Eine Probe, die nur hinzufuegt, waere
        // auch mit einem Vergleich gruen, der die neue Menge zweimal nimmt.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var dazu = stand.uebernimm(Wirtschaftswerte.VORGABE.mitFreistaedten(
                Freistadtwerte.VORGABE.mitBuergerrechten(Set.of(Tat.BEHAELTER))));
        var weg = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertEquals(Freistadtwerte.PFAD_BUERGERRECHTE + ": 0 -> 1 Namen,"
                        + " dazu: behaelter",
                zeileZu(dazu, Freistadtwerte.PFAD_BUERGERRECHTE),
                "der WORTLAUT: In der Datei des Betreibers steht behaelter, nicht der"
                        + " Name der Konstante - dafuer gibt es den Uebersetzer taten()");
        assertEquals(Freistadtwerte.PFAD_BUERGERRECHTE + ": 1 -> 0 Namen,"
                        + " weg: behaelter",
                zeileZu(weg, Freistadtwerte.PFAD_BUERGERRECHTE),
                "auch der Weg zurueck muss ankommen - ein Recht, das der Betreiber"
                        + " herausnimmt, ist die Aenderung, die er am ehesten nachprueft");
    }

    @Test
    @DisplayName("Geaenderte Strafwerte werden alle sechs genannt")
    void strafwerteGenannt() {
        // Jeder PFAD_* der Strafwerte in EINER Uebernahme [Schritt 16a Teil B]. Tafel
        // GrenzeTest 31cw haelt, dass jeder Pfad im Quelltext von uebernimm VORKOMMT;
        // diese Probe haelt, dass die Zeile wirklich ENTSTEHT. Beides ist noetig: Ein
        // Vergleich der neuen Menge mit sich selbst waere dort gruen und hier stumm.
        //
        // Die wichtigste der sechs Zeilen ist die Hoechstdauer - sie ist die Grenze
        // zwischen dem, was ein Moderator darf, und dem, was ein Verwalter darf
        // (Plan 12c.2). Wer sie enger zieht, zieht sie, weil gerade etwas geschehen
        // ist, und liest dann "nichts geaendert".
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitStrafen(
                Strafwerte.VORGABE
                        .mitHoechsteStunden(Strafart.STUMM, 12L)
                        .mitHoechsteStunden(Strafart.BANN, 48L)
                        .mitBannlisteOeffentlich(false)
                        .mitAnsagen(false)
                        .mitZeilenJeSeite(7)
                        .mitStummSperrtBefehle(List.of("msg"))));

        for (String pfad : List.of(
                Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + Strafart.STUMM.kuerzel(),
                Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + Strafart.BANN.kuerzel(),
                Strafwerte.PFAD_BANNLISTE_OEFFENTLICH, Strafwerte.PFAD_ANSAGEN,
                Strafwerte.PFAD_ZEILEN_JE_SEITE,
                Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE)) {
            assertTrue(uebernahme.geaendert().stream().anyMatch(zeile ->
                            zeile.startsWith(pfad + ":")),
                    pfad + " wird beim Neuladen nicht genannt: "
                            + uebernahme.geaendert());
        }
    }

    @Test
    @DisplayName("Die Hoechstdauer wird je ART genannt - gerechnet, nicht aufgezaehlt")
    void hoechstdauerJeArtGenannt() {
        // Die Zeilen entstehen in einer Schleife ueber Strafart.values(). Diese Probe
        // laeuft dieselbe Schleife: Eine vierte fortwirkende Art ist damit von selbst
        // mitgeprueft - und eine von Hand geschriebene Liste in uebernimm waere die
        // Stelle, an der sie fehlt.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Strafwerte anders = Strafwerte.VORGABE;
        int erwartet = 0;
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            anders = anders.mitHoechsteStunden(art, 5L);
            erwartet++;
        }

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitStrafen(anders));

        assertTrue(erwartet > 0, "es gibt keine fortwirkende Art - dann ist der ganze"
                + " Riegel gegen den dauerhaften Bann unerreichbar");
        long gezaehlt = uebernahme.geaendert().stream()
                .filter(zeile -> zeile.startsWith(
                        Strafwerte.PFAD_HOECHSTE_STUNDEN + "."))
                .count();
        assertEquals(erwartet, gezaehlt,
                "je fortwirkender Art eine Zeile: " + uebernahme.geaendert());
    }

    @Test
    @DisplayName("Die Befehlssperre wird in BEIDE Richtungen genannt")
    void befehlssperreInBeideRichtungen() {
        // In beide Richtungen, weil die Vorgabe NICHT leer ist: erst alles weg, dann
        // alles wieder her. Eine Probe, die nur hinzufuegt, waere auch mit einem
        // Vergleich gruen, der die neue Menge zweimal nimmt - und dann liest ein
        // Betreiber, der /msg aus der Sperre nimmt, "nichts geaendert". Seine
        // Stummschaltung ist ab da umgehbar.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var weg = stand.uebernimm(Wirtschaftswerte.VORGABE.mitStrafen(
                Strafwerte.VORGABE.mitStummSperrtBefehle(List.of())));
        var her = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertTrue(zeileZu(weg, Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE).contains("weg:"),
                "die weggefallenen Namen muessen in der Zeile stehen: "
                        + zeileZu(weg, Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE));
        assertTrue(zeileZu(her, Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE).contains("dazu:"),
                "und der Weg zurueck auch: "
                        + zeileZu(her, Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE));
    }

    @Test
    @DisplayName("Unveraenderte Strafwerte schweigen")
    void unveraenderteStrafwerteSchweigen() {
        // Die Gegenrichtung, und sie ist Pflicht: Sechs Zeilen, die immer dastehen,
        // waeren mit den Proben darueber genauso gruen - und der Betreiber laese bei
        // jedem Neuladen, die Hoechstdauer habe sich geaendert. Beim dritten Mal liest
        // er die Meldung nicht mehr.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertFalse(uebernahme.geaendert().stream().anyMatch(zeile ->
                        zeile.startsWith("moderation.strafen")),
                uebernahme.geaendert().toString());
    }

    /** Die eine Zeile der Uebernahme zu diesem Pfad - oder ein leerer Text. */
    private static String zeileZu(Wirtschaftsstand.Uebernahme uebernahme, String pfad) {
        return uebernahme.geaendert().stream()
                .filter(zeile -> zeile.startsWith(pfad + ":"))
                .findFirst()
                .orElse("(keine Zeile zu " + pfad + " in "
                        + uebernahme.geaendert() + ")");
    }

    @Test
    @DisplayName("Eine unveraenderte Wechselstube steht NICHT in der Meldung")
    void unveraenderterWechselSchweigt() {
        // Die Gegenrichtung, und sie ist Pflicht: Drei Zeilen, die immer dastehen, waeren
        // mit der Probe darueber genauso gruen - und der Betreiber laese bei jedem
        // Neuladen, der Kurs habe sich geaendert.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertEquals(List.of(), uebernahme.geaendert());
    }

    @Test
    @DisplayName("Der Schalter und die Stufen der Rangleiter werden getauscht")
    void rangleiterGetauscht() {
        // FUND VOM 20.11.2026, beim Bauen von Teil F: Die Rangleiter fehlte in dieser
        // Meldung seit Teil A. Wer eine Stufe eintraegt und "nichts geaendert" liest,
        // glaubt das Neuladen wirke nicht - dasselbe Fehlerbild wie bei den zwei
        // Auflagenschaltern, und dieselbe Antwort.
        //
        // Der Schalter als Ja/Nein, die Leiter als MENGE ihrer Stufennamen: Welche Stufe
        // dazukam und welche wegfiel, ist das, was der Betreiber lesen will.
        var kurz = new Rangwerte(true, Rangwerte.VORGABE.leiter().subList(0, 2));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitRaengen(kurz));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitRaengen(
                new Rangwerte(false, Rangwerte.VORGABE.leiter())));

        assertFalse(stand.werte().raenge().an());
        assertEquals(Rangwerte.VORGABE.leiter().size(),
                stand.werte().raenge().leiter().size());
        assertTrue(uebernahme.geaendert().contains(Rangwerte.PFAD_AN + ": true -> false"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().stream()
                        .anyMatch(zeile -> zeile.startsWith(Rangwerte.PFAD_LEITER)),
                uebernahme.geaendert().toString());
    }

    @Test
    @DisplayName("Zweimal neuladen vergleicht gegen den LAUFENDEN Stand, nicht die Vorgabe")
    void zweimal() {
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        stand.uebernimm(satz(40_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG));
        var zweite = stand.uebernimm(satz(80_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER,
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
                satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER, "  Mehr erlaubt ein Rang.  ")
                        .rangWerbung());
        assertEquals("", satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER, null).rangWerbung());
    }

    @Test
    @DisplayName("Unbrauchbare Werte kommen nie in einen Stand")
    void schrankenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> satz(25_000L, 10_000L, -1L, VORGABE_ZAHL, VORGABE_FELDER, ""));
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
        // EINS und nicht vier [20.11.2026, Teil H]: Diese Zahl ist die Untergrenze fuer
        // JEDEN, und die Staffel 1/2/4 steht in der Rangleiter. Hier stehen die Zahlen
        // als LITERAL und nicht als Verweis auf dieselbe Konstante - diese Probe ist der
        // Zwilling der ausgelieferten config.yml, und ein Verweis pruefte nur, dass eine
        // Konstante sich selbst gleicht.
        assertEquals(1, Wirtschaftswerte.VORGABE.hoechstzahl());
        assertEquals(1, Wirtschaftswerte.VORGABE.gruppenFelder());
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
                () -> satz(25_000L, 10_000L, 50_000L, VORGABE_ZAHL, VORGABE_FELDER, "", null));
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
    @DisplayName("Das Neuladen nennt JEDE geaenderte Zeile des Tagesbonus")
    void neuladenNenntDenTagesbonus() {
        // Der TEXT jeder erwarteten Zeile und nicht ihre Zahl: Wer die Serie verstellt,
        // ohne eine Zeile darueber zu lesen, dreht beim naechsten Mal weiter, obwohl sie
        // schon gilt. Keine Zusammenfassung wie "der Bonus hat sich geaendert" - dann
        // weiss er nicht, WELCHE Zahl jetzt gilt.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitBonus(
                new Bonuswerte(800L, 50L, 3_000L, 4, 15L)));

        assertTrue(uebernahme.geaendert().contains(
                        Bonuswerte.PFAD_GRUNDBETRAG + ": 500 -> 800"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Bonuswerte.PFAD_JE_SERIENTAG + ": 100 -> 50"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Bonuswerte.PFAD_HOECHSTBETRAG + ": 2000 -> 3000"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(Bonuswerte.PFAD_LUECKE + ": 2 -> 4"),
                uebernahme.geaendert().toString());
        assertTrue(uebernahme.geaendert().contains(
                        Bonuswerte.PFAD_MINDEST_MINUTEN + ": 0 -> 15"),
                uebernahme.geaendert().toString());
        assertEquals(800L, stand.werte().bonus().grundbetrag());
        assertEquals(15L, stand.werte().bonus().mindestMinuten());
    }

    @Test
    @DisplayName("Ein unveraenderter Tagesbonus steht NICHT in der Meldung")
    void bonusUnveraendertSchweigt() {
        // Die Gegenprobe: Waere die Zeile immer da, sagte sie nichts. Eine Meldung, in
        // der jede Zeile steht, liest niemand mehr.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertTrue(uebernahme.geaendert().stream().noneMatch(zeile
                        -> zeile.startsWith("tagesbonus.")),
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

    @Test
    @DisplayName("Geliefertes UND Bedingungen eines Postens werden genannt")
    void postenGegenstaendeUndBedingungen() {
        // BEFUND DER PRUEFRUNDE (20.11.2026): Genannt wurde je Posten nur der PREIS. Wer
        // muenzhaus.katalog.blumenkiste.bedingungen.parzellen von 1 auf 3 setzte, las
        // "an den Wirtschaftswerten hat sich nichts geaendert" - und die neue Huerde galt
        // ab dem naechsten /mark muenzhaus. Dasselbe beim Geliefertem: rose_bush 16 -> 1
        // war ein Siebzehntel fuer denselben Preis, stumm.
        var hut = Muenzwerte.VORGABE.posten("kuerbishut").orElseThrow();
        var blumen = Muenzwerte.VORGABE.posten("blumenkiste").orElseThrow();
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var anders = Muenzwerte.VORGABE.mitKatalog(List.of(
                new Muenzwerte.Posten(hut.schluessel(), Map.of("carved_pumpkin", 2),
                        hut.preis(), hut.bedingungen()),
                new Muenzwerte.Posten(blumen.schluessel(), blumen.gegenstaende(),
                        blumen.preis(), Map.of(Bedingungsart.PARZELLEN, 3L))));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitMuenzhaus(anders));

        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile -> zeile.startsWith(
                        Muenzwerte.PFAD_KATALOG + ".kuerbishut."
                                + Muenzwerte.SCHLUESSEL_GEGENSTAENDE + ":")),
                "das GELIEFERTE wird genannt: " + uebernahme.geaendert());
        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile -> zeile.startsWith(
                        Muenzwerte.PFAD_KATALOG + ".blumenkiste."
                                + Muenzwerte.SCHLUESSEL_BEDINGUNGEN + ":")),
                "die BEDINGUNG wird genannt: " + uebernahme.geaendert());
        assertFalse(uebernahme.geaendert().stream().anyMatch(zeile ->
                        zeile.contains("kuerbishut." + Muenzwerte.SCHLUESSEL_PREIS)),
                "der unveraenderte Preis bleibt stumm - sonst sind es Zeilen, die immer"
                        + " dastehen: " + uebernahme.geaendert());
    }

    @Test
    @DisplayName("Die drei Teile einer Rangstufe werden genannt")
    void rangstufeTeileGenannt() {
        // Derselbe Befund eine Ebene tiefer: raenge.leiter nannte nur die MENGE der
        // Stufennamen. Wer rang.leiter.buerger.zahlen von 4 auf 8 Parzellen setzte oder
        // ein Recht herausnahm, las "nichts geaendert" - und der Wert galt.
        //
        // Aufgezaehlt wird der Inhalt NICHT (das waere bei vier Stufen eine Wand),
        // sondern nur, DASS sich die Tabelle geaendert hat - dieselbe Antwort, die die
        // Lohntafel seit Monaten gibt.
        var buerger = Rangwerte.VORGABE.stufe("buerger").orElseThrow();
        var stufen = new java.util.ArrayList<>(Rangwerte.VORGABE.leiter());
        stufen.set(stufen.size() - 1, new Rangwerte.Stufe(buerger.schluessel(),
                Map.of(Bedingungsart.TAETIGE_STUNDEN, 41L),
                Map.of("parzellen", 8, "gruppe", 4, "inseln", 1, "wolken", 1),
                List.of("freistadt.gruenden")));
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitRaengen(new Rangwerte(true, stufen)));

        for (String teil : List.of(Rangwerte.SCHLUESSEL_BEDINGUNGEN,
                Rangwerte.SCHLUESSEL_ZAHLEN, Rangwerte.SCHLUESSEL_RECHTE)) {
            assertTrue(uebernahme.geaendert().stream().anyMatch(zeile ->
                            zeile.startsWith(Rangwerte.PFAD_LEITER + ".buerger." + teil
                                    + ":")),
                    teil + " wird nicht genannt: " + uebernahme.geaendert());
        }
    }

    @Test
    @DisplayName("Eine unveraenderte Rangstufe und ein unveraenderter Posten schweigen")
    void unveraenderteTeileSchweigen() {
        // Die Gegenrichtung fuer beide Nachtraege zusammen: Sechs Zeilen je Stufe und
        // zwei je Posten, die immer dastehen, waeren mit den Proben darueber genauso
        // gruen - und der Betreiber laese bei jedem Neuladen, seine Raenge haetten sich
        // geaendert.
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE);

        assertEquals(List.of(), uebernahme.geaendert());
    }

    @Test
    @DisplayName("Das Verlangte und die Belohnung einer Herausforderung werden genannt")
    void herausforderungTeileGenannt() {
        // Die dritte Liste mit demselben Befund - und hier ist einer der zwei Werte
        // GELD: Eine Belohnung, die sich beim Neuladen stumm aendert, ist genau der Fall,
        // fuer den Teil F die Wechselkurse nachgetragen hat.
        var erster = Aufgabenwerte.VORGABE.aufgabe("erster-stein").orElseThrow();
        var stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var anders = new Aufgabenwerte(true, List.of(
                new Aufgabenwerte.Aufgabe(erster.schluessel(),
                        Map.of("cobblestone", 128), 500L)));

        var uebernahme = stand.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(anders)));

        assertTrue(uebernahme.geaendert().stream().anyMatch(zeile -> zeile.startsWith(
                        Aufgabenwerte.PFAD_LISTE + ".erster-stein."
                                + Aufgabenwerte.SCHLUESSEL_VERLANGT + ":")),
                "das VERLANGTE wird genannt: " + uebernahme.geaendert());
        assertTrue(uebernahme.geaendert().contains(Aufgabenwerte.PFAD_LISTE
                        + ".erster-stein." + Aufgabenwerte.SCHLUESSEL_BELOHNUNG
                        + ": " + erster.belohnung() + " -> 500"),
                "die BELOHNUNG wird mit beiden Zahlen genannt - sie ist Geld: "
                        + uebernahme.geaendert());
    }
}
