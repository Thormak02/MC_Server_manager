package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherTruhe;
import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Truhenwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Entscheidungen der Freien Truhe - ohne Bukkit.
 *
 * <p>Drei Zusagen tragen hier alles andere, und jede ist eine Richtung, in der ein Fehler
 * still bleibt:
 * <ul>
 *   <li><b>Der Deckel kommt vom HALTER</b> und nicht aus einem Abbild - sonst wirkt ein
 *       Neuladen nie.</li>
 *   <li><b>Eine Stoerung beim OEFFNEN weist ab, eine beim SCHLIESSEN legt zurueck.</b> Die
 *       sichere Seite ist in den zwei Richtungen verschieden, und beide Male ist die
 *       bequeme Antwort die falsche.</li>
 *   <li><b>Der TAG kommt aus der eingestellten Zone</b> und nicht aus der Systemzeit.</li>
 * </ul>
 */
@DisplayName("Die Entscheidungen der Freien Truhe")
class TruhenbuchTest {

    /**
     * Die Werte, gegen die diese Probe rechnet - 16 Stueck am Tag, zwei Verbote.
     *
     * <p>Eigene Zahlen und NICHT {@link Truhenwerte#VORGABE}: Ab Werk gilt seit dem
     * 10.11.2026 "kein Deckel, nichts verboten", und eine Probe, die am Deckel oder am
     * Verbot rechnet, braucht beides. Haengt sie an der Vorgabe, prueft sie die Vorgabe
     * statt der Regel - und die naechste Entscheidung des Betreibers bricht ein halbes
     * Dutzend Proben, die mit ihr nichts zu tun haben.
     */
    private static final Truhenwerte WERTE =
            new Truhenwerte(16, Set.of("written_book", "shulker_box"));

    private static final String WELT = "freimarken:altfurt";
    private static final Blockort HALLE = new Blockort(WELT, 72, 72, 20);
    private static final UUID ANNA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Mittags, damit der Tag in jeder vernuenftigen Zone derselbe ist. */
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private final List<String> protokollzeilen = new ArrayList<>();
    private final Truhenablage ablage = new SpeicherTruhe();

    @Test
    @DisplayName("Eine eingetragene Stelle ist eine Truhe, jede andere nicht")
    void stelleErkannt() {
        Truhenbuch buch = buch(WERTE, HALLE);

        assertTrue(buch.gibtTruhen());
        assertTrue(buch.istFreieTruhe(HALLE));
        assertFalse(buch.istFreieTruhe(new Blockort(WELT, 72, 73, 20)));
        assertEquals(List.of(HALLE), buch.stellen());
    }

    @Test
    @DisplayName("Ohne Stelle gibt es keine Truhe - die Wache wird dann nicht angemeldet")
    void ohneStelleKeineTruhe() {
        Truhenbuch buch = buch(WERTE);

        assertFalse(buch.gibtTruhen());
        assertEquals(List.of(), buch.stellen());
    }

    @Test
    @DisplayName("Mit leerem Tagesstand geht sie auf - und nennt den ganzen Deckel")
    void zutrittAmAnfang() {
        var zutritt = buch(WERTE, HALLE).zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.OFFEN, zutritt.bescheid());
        assertEquals(16, zutritt.nochFrei());
    }

    @Test
    @DisplayName("OHNE Zaehlung wird die Ablage GAR NICHT gefragt")
    void ohneZaehlungKeineAblage() {
        // Die Attrappe WIRFT bei jeder Frage. Wer ohne Deckel und ohne Verbote trotzdem
        // den Tagesstand liest, haelt eine Truhe zu, die keinen Deckel hat: Eine
        // Stoerung der Datenbank sperrte dann die Gemeinschaftskiste - und zwar mit der
        // Begruendung, der Tagesstand sei nicht lesbar, den niemand braucht.
        var zutritt = buch(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of()),
                stolpernd(), HALLE).zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.OFFEN, zutritt.bescheid());
        assertEquals(-1, zutritt.nochFrei(), "kein Deckel heisst -1 und nicht 0");
    }

    @Test
    @DisplayName("OHNE Deckel wird auch nichts GESCHRIEBEN - die Zeile liest niemand")
    void ohneDeckelKeinTagesstand() {
        // Gelesen wird der Tagesstand einzig vom Deckel; ohne ihn ist die Zeile ein
        // Schreiben ohne Leser, das der naechtliche Lauf wieder wegraeumen muss. Geprueft
        // an der Lage "Verbote ohne Deckel", in der GEZAEHLT wird - ohne Zaehlung kommt
        // merke ohnehin nie dran.
        Truhenbuch buch = buch(new Truhenwerte(Truhenwerte.KEIN_DECKEL,
                Set.of("written_book")), HALLE);

        // Der Tag kommt aus dem BUCH und nicht aus einer eigenen Zahl: Es rechnet nach
        // zeit.zone, und eine getippte Tagesgrenze waere eine zweite Wahrheit.
        LocalDate heute = buch.heute();

        buch.merke(ANNA, 30);

        assertEquals(0, ablage.behalten(ANNA, heute),
                "keine Zeile - und damit auch keine Stoerungsmeldung, die von einem"
                        + " Deckel spricht, den es nicht gibt");

        // Die andere Haelfte an DEMSELBEN Spieler: Mit Deckel wird geschrieben. Ohne
        // diese Zeile waere die darueber auch gruen, wenn merke gar nichts mehr tut.
        buch(new Truhenwerte(16, Set.of()), HALLE).merke(ANNA, 5);

        assertEquals(5, ablage.behalten(ANNA, heute), "mit Deckel gehoert sie dazu");
    }

    @Test
    @DisplayName("Verbote OHNE Deckel fragen die Ablage auch nicht - weder beim Oeffnen"
            + " noch beim Schliessen")
    void verboteOhneDeckelFragenDieAblageNicht() {
        // GEFUNDEN VON DER LESESUCHE am 10.11.2026. Diese Lage laedt die config.yml
        // ausdruecklich ein: ein Verbot eintragen und entnahme-je-tag auf 0 lassen. Dann
        // wird GEZAEHLT (die Rueckgabe braucht den Unterschied), aber es gibt keinen
        // Deckel - und abrechnen(0, schon, genommen) gibt fuer JEDES schon (genommen, 0).
        // Die gelesene Zahl entscheidet also nichts, und eine Stoerung kostete die ganze
        // Truhe: zu beim Oeffnen, alles zurueck beim Schliessen.
        Truhenbuch buch = buch(new Truhenwerte(Truhenwerte.KEIN_DECKEL,
                Set.of("written_book")), stolpernd(), HALLE);

        var zutritt = buch.zutritt(ANNA);
        var bilanz = buch.beimSchliessen(ANNA, 30);

        assertEquals(Truhenbuch.Bescheid.OFFEN, zutritt.bescheid(),
                "eine undeckelte Truhe bleibt offen, auch wenn die Datenbank schweigt");
        assertEquals(-1, zutritt.nochFrei());
        assertEquals(30, bilanz.behalten(), "ohne Deckel behaelt er alles");
        assertEquals(0, bilanz.zurueck(), "und es geht NICHTS zurueck");
        assertFalse(bilanz.gestoert(), "gestoert war nichts - es wurde nichts gefragt");
    }

    @Test
    @DisplayName("Am Deckel bleibt sie ZU - und das ist eine Entscheidung ueber das Spiel")
    void zutrittAmDeckel() {
        // Wer am Deckel ist, soll die Truhe gar nicht aufbekommen. Sonst nimmt er, sieht
        // es im Rucksack liegen, schliesst - und bekommt alles wieder weggenommen. Das
        // sieht aus wie ein Fehler des Servers.
        ablage.merke(ANNA, LocalDate.of(2026, 11, 10), 16);

        var zutritt = buch(WERTE, HALLE).zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.DECKEL, zutritt.bescheid());
        assertEquals(0, zutritt.nochFrei());
    }

    @Test
    @DisplayName("OHNE Deckel geht sie immer auf und meldet MINUS EINS")
    void zutrittOhneDeckel() {
        // Die minus eins ist kein Fehlerwert, sondern die Antwort "unbegrenzt": Truhentext
        // macht daraus einen eigenen Satz, und "noch 0 Stueck" waere das Gegenteil der
        // Wahrheit.
        ablage.merke(ANNA, LocalDate.of(2026, 11, 10), 500);

        var zutritt = buch(WERTE.mitEntnahme(Truhenwerte.KEIN_DECKEL),
                HALLE).zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.OFFEN, zutritt.bescheid());
        assertEquals(-1, zutritt.nochFrei());
    }

    @Test
    @DisplayName("Der angefangene Tag steht in der Zahl beim Oeffnen")
    void zutrittMitAngefangenemTag() {
        // DER ZWEIG, DEN KEINE ANDERE PROBE BETRITT: Jede uebrige oeffnet mit leerem
        // Tagesstand, und bei null schon Genommenen ist "der ganze Deckel" von "der Rest"
        // nicht zu unterscheiden. Wer heute schon zehn geholt hat, muss SECHS lesen -
        // liest er 16, nimmt er 16 und bekommt beim Schliessen zehn davon wieder
        // abgenommen. Die Zahl waere dann eine Einladung zu einem Griff, der nicht gilt.
        Truhenbuch buch = buch(WERTE, HALLE);
        buch.merke(ANNA, 10);

        var zutritt = buch.zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.OFFEN, zutritt.bescheid());
        assertEquals(6, zutritt.nochFrei(), "sechzehn minus die zehn von heute");
    }

    @Test
    @DisplayName("Ein geaenderter Deckel wirkt ohne Neustart - der Halter, kein Abbild")
    void deckelWirktNachDemNeuladen() {
        // DIE FEHLERKLASSE, die am 10.11.2026 zweimal gefunden wurde: Ein Satz im Feld
        // wird nie wieder aufgefrischt, denn ein Neuladen baut keine neuen Objekte - es
        // tauscht die Zahlen IM Halter. Ein Buch mit einem Abbild haette hier weiter 16
        // gesagt.
        Wirtschaftsstand halter = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitTruhe(WERTE));
        Truhenbuch buch = new Truhenbuch(Truhenverzeichnis.ausStellen(List.of(HALLE)),
                ablage, halter, UHR, protokoll());

        halter.uebernimm(Wirtschaftswerte.VORGABE.mitTruhe(
                WERTE.mitEntnahme(32)));

        assertEquals(32, buch.werte().entnahmeJeTag());
        assertEquals(32, buch.zutritt(ANNA).nochFrei());
        // UND BEIM SCHLIESSEN, denn dort faellt die Entscheidung ueber die Ware: Eine
        // Abrechnung, die den Deckel anderswo holt als die Zeile beim Oeffnen, verspricht
        // 32 und nimmt beim Schliessen die Haelfte wieder ab. Zwei Zahlen fuer denselben
        // Deckel sind zwei Deckel.
        var bilanz = buch.beimSchliessen(ANNA, 20);
        assertEquals(20, bilanz.behalten(), "zwanzig liegen unter dem neuen Deckel von 32");
        assertEquals(0, bilanz.zurueck(), "also geht nichts zurueck");
    }

    @Test
    @DisplayName("Der TAG kommt aus der eingestellten Zone, nicht aus der Systemzeit")
    void tagAusDerZone() {
        // EINE ZONE, DIE WEDER DIE VORGABE NOCH UTC IST: Um 22:30 UTC ist es in Berlin
        // (der Vorgabe!) der 10., in UTC der 10. und in Auckland der 11. Mit Berlin waere
        // diese Probe gruen geblieben, waehrend das Buch die Zone gar nicht fragt - der
        // Unterschied muss der GEPRUEFTE sein.
        Clock spaet = Clock.fixed(Instant.parse("2026-11-10T22:30:00Z"), ZoneOffset.UTC);
        Wirtschaftsstand halter = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitZeit(new Zeitwerte(ZoneId.of("Pacific/Auckland"),
                        Zeitwerte.VORGABE.takt(), Zeitwerte.VORGABE.untaetigNach())));
        Truhenbuch buch = new Truhenbuch(Truhenverzeichnis.ausStellen(List.of(HALLE)),
                ablage, halter, spaet, protokoll());

        assertEquals(LocalDate.of(2026, 11, 11), buch.heute(),
                "in Auckland ist es 11:30 am elften - in Berlin und in UTC der zehnte");
    }

    @Test
    @DisplayName("Beim Schliessen bleibt unter dem Deckel alles beim Spieler")
    void schliessenUnterDemDeckel() {
        var bilanz = buch(WERTE, HALLE).beimSchliessen(ANNA, 7);

        assertEquals(7, bilanz.behalten());
        assertEquals(0, bilanz.zurueck());
        assertEquals(0, bilanz.schonVorher());
        assertFalse(bilanz.gestoert());
    }

    @Test
    @DisplayName("Beim Schliessen geht ueber dem Deckel der Ueberschuss zurueck")
    void schliessenUeberDemDeckel() {
        ablage.merke(ANNA, LocalDate.of(2026, 11, 10), 10);

        var bilanz = buch(WERTE, HALLE).beimSchliessen(ANNA, 20);

        assertEquals(6, bilanz.behalten(), "zehn von sechzehn sind verbraucht");
        assertEquals(14, bilanz.zurueck());
        assertEquals(10, bilanz.schonVorher(), "damit rechnet der Aufrufer den Rest aus");
    }

    @Test
    @DisplayName("Der Rest wird aus dem WIRKLICH Behaltenen gerechnet, nicht aus dem Erlaubten")
    void restAusDemWirklichBehaltenen() {
        // Wer seine Ware vor dem Schliessen wegwirft, behaelt mehr, als zurueckgelegt
        // werden konnte - und soll dann auch den kleineren Rest lesen. Rechnete diese
        // Zeile mit dem Erlaubten, nennte die Antwort einen Rest, den es nicht gibt.
        Truhenbuch buch = buch(WERTE, HALLE);

        assertEquals(6, buch.nochFrei(0, 10));
        assertEquals(0, buch.nochFrei(0, 20), "und nie negativ");
        assertEquals(16, buch.nochFrei(0, 0));
        assertEquals(-1, buch(WERTE.mitEntnahme(0), HALLE).nochFrei(0, 10),
                "ohne Deckel gibt es keine Zahl");
    }

    @Test
    @DisplayName("Geschrieben wird AUFSCHLAGEND - und eine 0 gar nicht")
    void merkeSchlaegtAuf() {
        Truhenbuch buch = buch(WERTE, HALLE);

        buch.merke(ANNA, 5);
        buch.merke(ANNA, 4);
        buch.merke(ANNA, 0);
        buch.merke(ANNA, -3);

        assertEquals(9, ablage.behalten(ANNA, LocalDate.of(2026, 11, 10)),
                "zwei Griffe sind zwei Zahlen; eine 0 ist eine Zeile ohne Anlass");
        // UND DAS PROTOKOLL BLEIBT LEER. Daran haengt der Riegel: Die Ablage WIRFT bei
        // einer 0 (ihr Vertrag), der Faenger darunter verschluckt den Wurf und schreibt
        // eine SEVERE-Zeile. Ohne diese Zusage waere die Frage "wird die 0 abgefangen
        // oder weggeworfen" an der Summe nicht zu unterscheiden - und im Betrieb stuende
        // bei jedem Spieler, der nur etwas HINEINLEGT, ein schwerer Fehler im Protokoll.
        assertEquals(List.of(), protokollzeilen, "kein Wurf, keine Zeile");
    }

    @Test
    @DisplayName("STOERUNG beim Oeffnen: die Truhe bleibt zu")
    void stoerungBeimOeffnen() {
        // Die sichere Seite ist hier das Abweisen: Ohne den Tagesstand ist der Deckel
        // offen - und eine Truhe ohne Deckel ist bei einer Stoerung genau das, was man
        // nicht will.
        var zutritt = buch(WERTE, stolpernd(), HALLE).zutritt(ANNA);

        assertEquals(Truhenbuch.Bescheid.GESTOERT, zutritt.bescheid());
        assertEquals(0, zutritt.nochFrei());
        assertTrue(protokollzeilen.stream().anyMatch(zeile -> zeile.startsWith("SEVERE")),
                "der Betreiber muss den Grund lesen: " + protokollzeilen);
    }

    @Test
    @DisplayName("STOERUNG beim Schliessen: ALLES geht zurueck")
    void stoerungBeimSchliessen() {
        // Die andere Richtung, und sie ist die unbequemere: Haette er behalten duerfen,
        // waere die Truhe leer und kein Zaehler gesetzt - der naechste Spieler fand eine
        // leere Truhe und einen offenen Deckel.
        var bilanz = buch(WERTE, stolpernd(), HALLE)
                .beimSchliessen(ANNA, 9);

        assertTrue(bilanz.gestoert());
        assertEquals(0, bilanz.behalten());
        assertEquals(9, bilanz.zurueck());
    }

    @Test
    @DisplayName("STOERUNG beim Schreiben wirft nicht - die Ware ist schon beim Spieler")
    void stoerungBeimSchreiben() {
        // Eine Ausnahme im Ereignisfaden kostet die Kiste, in der die Gegenstaende gerade
        // liegen. Eine verlorene Zeile kostet hoechstens einen offenen Deckel.
        buch(WERTE, stolpernd(), HALLE).merke(ANNA, 5);

        assertTrue(protokollzeilen.stream()
                        .anyMatch(zeile -> zeile.contains("nicht schreibbar")),
                protokollzeilen.toString());
    }

    @Test
    @DisplayName("Geraeumt wird bis VOR heute - eine Stoerung meldet minus eins")
    void raeumen() {
        ablage.merke(ANNA, LocalDate.of(2026, 11, 9), 7);
        ablage.merke(ANNA, LocalDate.of(2026, 11, 10), 3);

        assertEquals(1, buch(WERTE, HALLE).raeume());
        assertEquals(3, ablage.behalten(ANNA, LocalDate.of(2026, 11, 10)),
                "der heutige Tag bleibt stehen");
        assertEquals(-1, buch(WERTE, stolpernd(), HALLE).raeume());
    }

    @Test
    @DisplayName("Ohne Verzeichnis, Ablage, Halter, Uhr oder Protokoll gibt es kein Buch")
    void pflichtenGelten() {
        var leer = Truhenverzeichnis.ohneTruhen();
        var halter = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenbuch(null, ablage, halter, UHR, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenbuch(leer, null, halter, UHR, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenbuch(leer, ablage, null, UHR, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenbuch(leer, ablage, halter, null, protokoll()));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenbuch(leer, ablage, halter, UHR, null));
    }

    private Truhenbuch buch(Truhenwerte werte, Blockort... stellen) {
        return buch(werte, ablage, stellen);
    }

    private Truhenbuch buch(Truhenwerte werte, Truhenablage welche, Blockort... stellen) {
        return new Truhenbuch(Truhenverzeichnis.ausStellen(List.of(stellen)), welche,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitTruhe(werte)), UHR,
                protokoll());
    }

    /**
     * Eine Ablage, die bei jeder Frage wirft - fuer beide Stoerungsrichtungen.
     *
     * <p>Kein Erbe von {@link SpeicherTruhe} (die ist {@code final}), sondern eine eigene
     * Umsetzung: So wirft wirklich JEDE Methode, und keine antwortet versehentlich
     * richtig.
     */
    private static Truhenablage stolpernd() {
        return new Truhenablage() {
            @Override
            public int behalten(UUID spieler, LocalDate tag) {
                throw new IllegalStateException("Entnahmen nicht lesbar (Probe)");
            }

            @Override
            public void merke(UUID spieler, LocalDate tag, int stueck) {
                throw new IllegalStateException("Entnahme nicht schreibbar (Probe)");
            }

            @Override
            public int raeume(LocalDate vorTag) {
                throw new IllegalStateException("Nicht raeumbar (Probe)");
            }
        };
    }

    /** Ein Protokoll, das in eine Liste schreibt - wie in {@code ShophandelTest}. */
    private Logger protokoll() {
        Logger eigener = Logger.getLogger("Truhenbuchprobe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz.getLevel() + ": " + satz.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }
}
