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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Freie Truhe DURCHLAUFEN - oeffnen, nehmen, schliessen, abrechnen.
 *
 * <p><b>Ohne Bukkit, und das ist der Zweck der Naht {@link Truhenwache.Truhenhand}.</b>
 * Jeder Griff in eine Kiste geht durch sie; die Attrappe hier haelt zwei Karten (Truhe
 * und Rucksack) und schreibt mit. Was damit geprueft wird, ist genau das, was im Spiel
 * nicht zu sehen ist: die ZAHL, die am Ende im Tagesstand steht.
 *
 * <p><b>Die tragende Probe ist {@link #differenzZaehltNichtDerKlick()}.</b> Sie setzt
 * keinen Klick, sondern nimmt der Truhe etwas weg - genauso, wie es jeder der sieben Wege
 * tut (Linksklick, Rechtsklick, Shift-Klick, Doppelklick, Zahlentaste, Ziehen, Q). Ein
 * Programm, das Klicks zaehlt, haette hier nichts gezaehlt.
 */
@DisplayName("Die Freie Truhe im Spiel")
class TruhenwacheTest {

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
    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 10);
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private final Truhenablage ablage = new SpeicherTruhe();
    private Sprache sprache;

    @BeforeEach
    void laden() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    @Test
    @DisplayName("Was beim Schliessen FEHLT, ist die Entnahme - kein Klick wird gezaehlt")
    void differenzZaehltNichtDerKlick() {
        // DIE TRAGENDE PROBE. Hier wird nichts angeklickt: Der Truhe fehlen am Ende
        // sieben Stueck, und genau sieben stehen im Tagesstand. Ein Programm, das die
        // sieben Wege des Nehmens einzeln kennen muesste, haette hier eine 0 geschrieben.
        Hand hand = hand(Map.of("STONE", 20), Map.of());
        Truhenwache wache = wache(WERTE);

        assertTrue(wache.oeffne(ANNA, false, hand).geoeffnet());
        hand.truhe.put("STONE", 13);
        hand.rucksack.put("STONE", 7);
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(7, ablage.behalten(ANNA, HEUTE));
        assertEquals(List.of(), hand.griffe, "unter dem Deckel wird nichts bewegt");
        assertTrue(bild(zeilen).contains("7 Gegenstaende behalten"), bild(zeilen));
        assertTrue(bild(zeilen).contains("9"), "und der Rest des Tages: " + bild(zeilen));
    }

    @Test
    @DisplayName("Gezaehlt wird JE MATERIAL - Erde hineinlegen deckt Diamanten nicht")
    void jeMaterialGezaehlt() {
        // Ohne die Rechnung je Material waere die Gesamtzahl 0, und das Hineinlegen von
        // Schutt waere der Weg um jeden Deckel: zwanzig Diamanten heraus, zwanzig Erde
        // hinein, Tagesstand null.
        //
        // Die Erde liegt SCHON BEIM OEFFNEN in der Truhe, und daran haengt diese Probe:
        // Gerechnet wird ueber die Materialien des Schnappschusses, und was erst danach
        // hineinkommt, taucht in dieser Rechnung ueberhaupt nicht auf. Nur bei einem
        // Material, das vorher schon da war, wird die Differenz NEGATIV - und erst dann
        // zeigt sich, ob das Programm sie gegen die Diamanten rechnet.
        Hand hand = hand(Map.of("DIAMOND", 20, "DIRT", 5), Map.of("DIRT", 20));
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("DIAMOND", 10);
        hand.truhe.put("DIRT", 25);
        hand.rucksack.put("DIAMOND", 10);
        hand.rucksack.remove("DIRT");
        wache.schliesse(ANNA, hand);

        assertEquals(10, ablage.behalten(ANNA, HEUTE),
                "zehn Diamanten fehlen - die zwanzig Erde zaehlen nicht dagegen");
    }

    @Test
    @DisplayName("Ueber dem Deckel geht der Ueberschuss zurueck in die Truhe")
    void ueberschussGehtZurueck() {
        Hand hand = hand(Map.of("STONE", 64), Map.of());
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("STONE", 34);
        hand.rucksack.put("STONE", 30);
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(16, ablage.behalten(ANNA, HEUTE), "mehr als sechzehn nicht");
        assertEquals(16, hand.rucksack.getOrDefault("STONE", 0),
                "sechzehn bleiben bei ihm - vierzehn sind ihm wieder abgenommen");
        assertEquals(48, hand.truhe.get("STONE"), "und wieder in der Truhe");
        assertEquals(List.of("vomSpieler STONE 14 -> 14", "inDieTruhe STONE 14 -> 14"),
                hand.griffe, "erst nehmen, dann legen - und beides nur einmal");
        assertTrue(bild(zeilen).contains("14 Gegenstaende gehen zurueck"), bild(zeilen));
    }

    @Test
    @DisplayName("Weggeworfene Ware bleibt ANGERECHNET - sonst waere das der Weg um den Deckel")
    void weggeworfenesBleibtAngerechnet() {
        // Wer nimmt, die Sachen vor dem Schliessen wegwirft und dann schliesst, hat sie
        // der Truhe trotzdem entnommen. Rechnete diese Stelle nur das, was sie
        // zurueckbekommt, waere Wegwerfen-und-wieder-aufsammeln ein unbegrenzter Deckel.
        Hand hand = hand(Map.of("STONE", 64), Map.of());
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("STONE", 14);
        // Der Rucksack bleibt LEER: Die fuenfzig Stueck liegen auf dem Boden.

        wache.schliesse(ANNA, hand);

        assertEquals(50, ablage.behalten(ANNA, HEUTE),
                "alle fuenfzig sind aus der Truhe heraus und nicht zurueckgekommen");
        assertEquals(List.of("vomSpieler STONE 34 -> 0"), hand.griffe,
                "versucht wurde es - mehr kann die Wache nicht tun");
        assertEquals(0, ablage.behalten(ANNA, HEUTE.minusDays(1)), "und nur heute");
    }

    @Test
    @DisplayName("HINEINLEGEN zaehlt nicht - und schreibt keine Zeile")
    void hineinlegenZaehltNicht() {
        // Der halbe Zweck dieser Truhe. Ein Deckel darauf machte aus der
        // Gemeinschaftstruhe eine Abgabestelle mit Quote, und eine Zeile "0 Stueck
        // behalten" waere eine Meldung ueber einen Vorgang, der keine braucht.
        Hand hand = hand(Map.of("STONE", 10), Map.of("DIRT", 32));
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("DIRT", 32);
        hand.rucksack.remove("DIRT");
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(0, ablage.behalten(ANNA, HEUTE));
        assertEquals(List.of(), zeilen, "kein Wort ueber eine Gabe");
    }

    @Test
    @DisplayName("Verbotenes kommt zurueck - aber nur, was ER hineingelegt hat")
    void verbotenesKommtZurueck() {
        // NUR das Neue, und das ist eine Entscheidung ueber einen stillen Fehler: Setzt
        // der Betreiber morgen ein Material auf die Liste, laege es womoeglich schon
        // lange in der Truhe - und dann bekaeme der naechste Spieler, der nur
        // hineinsieht, fremde Ware in die Hand.
        Hand hand = hand(new LinkedHashMap<>(Map.of("WRITTEN_BOOK", 1)),
                new LinkedHashMap<>(Map.of("SHULKER_BOX", 2)));
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("SHULKER_BOX", 2);
        hand.rucksack.remove("SHULKER_BOX");
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(List.of("ausDerTruhe SHULKER_BOX 2 -> 2",
                        "zumSpieler SHULKER_BOX 2 -> 2"), hand.griffe,
                "die Shulkerkiste geht zurueck, das Buch bleibt liegen");
        assertEquals(1, hand.truhe.get("WRITTEN_BOOK"),
                "das Buch lag vorher darin - es raeumt der taegliche Lauf");
        assertTrue(bild(zeilen).contains("SHULKER_BOX"), bild(zeilen));
        assertFalse(bild(zeilen).contains("WRITTEN_BOOK"), bild(zeilen));
    }

    @Test
    @DisplayName("Reicht der Ueberschuss nicht fuer alle Materialien, entscheidet die Ordnung")
    void ordnungBeimZuruecklegen() {
        // Eine feste Reihenfolge und keine zufaellige: Sonst gingen bei zwei Materialien
        // heute die Diamanten zurueck und morgen der Stein, und niemand koennte sagen,
        // was zu erwarten ist. Geordnet wird nach dem Materialnamen - DIAMOND vor STONE.
        Hand hand = hand(Map.of("DIAMOND", 20, "STONE", 20), Map.of());
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("DIAMOND", 10);
        hand.truhe.put("STONE", 10);
        hand.rucksack.put("DIAMOND", 10);
        hand.rucksack.put("STONE", 10);
        wache.schliesse(ANNA, hand);

        assertEquals(List.of("vomSpieler DIAMOND 4 -> 4", "inDieTruhe DIAMOND 4 -> 4"),
                hand.griffe, "zwanzig genommen, sechzehn erlaubt - vier zurueck, und zwar"
                        + " die mit dem ersten Namen");
        assertEquals(16, ablage.behalten(ANNA, HEUTE));
    }

    @Test
    @DisplayName("Eine volle Truhe gibt den Rest zurueck - und er bleibt angerechnet")
    void volleTruheGibtZurueck() {
        // Erreichbar, wenn zwischen Oeffnen und Schliessen etwas hineinlaeuft. Was
        // draussen bleibt, gehoert nicht dem Server: Es geht an den Spieler zurueck.
        Hand hand = hand(Map.of("STONE", 64), Map.of());
        hand.truheNimmtHoechstens = 5;
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("STONE", 34);
        hand.rucksack.put("STONE", 30);
        wache.schliesse(ANNA, hand);

        assertEquals(List.of("vomSpieler STONE 14 -> 14", "inDieTruhe STONE 14 -> 5",
                        "zumSpieler STONE 9 -> 9"), hand.griffe);
        assertEquals(25, ablage.behalten(ANNA, HEUTE),
                "dreissig heraus, fuenf zurueck - fuenfundzwanzig angerechnet");
    }

    @Test
    @DisplayName("Wird die Zaehlung EINGESCHALTET, waehrend das Fenster offen steht, wird"
            + " NICHT abgerechnet")
    void neuladenSchaltetEinMittenImFenster() {
        // DIE ANDERE STOERUNGSRICHTUNG, und sie braucht ihren eigenen Satz [gemessen am
        // 10.11.2026: Fall G5 der Einspritzungsrunde blieb ohne ihn gruen]. Ohne Zaehlung
        // durften mehrere GEMEINSAM hineinsehen - es gibt also keinen Schnappschuss, der
        // EINEM gehoert. Wird mitten im Fenster ein Deckel eingetragen, darf die
        // Differenz darum niemandem angerechnet werden: Sonst zahlt der Erste, der
        // schliesst, fuer alle anderen. Der Deckel gilt ab dem naechsten Fenster.
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitTruhe(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of())));
        Truhenwache wache = new Truhenwache(buch(stand), sprache);
        Hand hand = hand(Map.of("STONE", 64), Map.of("STONE", 64));

        wache.oeffne(ANNA, false, hand);
        // Der Betreiber traegt einen Deckel ein und ruft /freimarken neuladen.
        stand.uebernimm(Wirtschaftswerte.VORGABE.mitTruhe(WERTE));
        // In der Truhe fehlen jetzt 64 Stueck - genommen hat sie irgendwer von denen,
        // die gemeinsam hineingesehen haben.
        hand.truhe.remove("STONE");
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(List.of(), zeilen, "kein Wort ueber eine Differenz ohne Besitzer");
        assertEquals(0, ablage.behalten(ANNA, HEUTE), "und kein Tagesstand");
        assertEquals(List.of(), hand.griffe,
                "vor allem: Es wird ihm NICHTS aus dem Rucksack genommen");
    }

    @Test
    @DisplayName("Wird die Zaehlung weggeschaltet, waehrend das Fenster offen steht,"
            + " kostet es niemanden etwas")
    void neuladenMittenImFenster() {
        // GEFUNDEN VON DER LESESUCHE am 10.11.2026, und es ist ein Loch, das es vorher
        // nicht geben konnte: Solange immer nur EINER hineinsah, war die Differenz seine.
        // Jetzt darf ein Zweiter dazukommen, sobald der Betreiber den Deckel wegnimmt -
        // und dann traegt der alte Schnappschuss des Ersten den Griff des Zweiten.
        // Angerechnet bekaeme ihn der Erste, und geschrieben wuerde er, obwohl die
        // Rechenprobe gerade "kein Tagesstand wird geschrieben" gemeldet hat.
        Wirtschaftsstand stand = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitTruhe(WERTE));
        Truhenwache wache = new Truhenwache(buch(stand), sprache);
        Hand hand = hand(Map.of("STONE", 64), Map.of());

        wache.oeffne(ANNA, false, hand);
        // Der Betreiber nimmt Deckel UND Verbote weg und ruft /freimarken neuladen.
        stand.uebernimm(Wirtschaftswerte.VORGABE.mitTruhe(
                new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of())));
        // Bert darf jetzt dazukommen und nimmt die 64 Steine. Anna schliesst danach.
        hand.truhe.remove("STONE");
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(List.of(), zeilen, "kein Wort ueber fremde Griffe");
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "und kein Tagesstand - was beim Oeffnen galt, gilt fuer dieses Fenster");
        assertEquals(List.of(), hand.griffe, "zurueckgelegt wird auch nichts");
    }

    @Test
    @DisplayName("OHNE Deckel und ohne Verbote sehen alle GEMEINSAM hinein")
    void ohneZaehlungGemeinsam() {
        // ENTSCHIEDEN AM 10.11.2026: "man soll auch gemeinsam hereinschauen koennen."
        // Geprueft wird beides - dass sie aufgeht UND dass dann wirklich nichts gezaehlt
        // wird. Eine Truhe, die zu zweit aufgeht und trotzdem rechnet, waere der
        // schlimmste der drei Zustaende: Sie schriebe dem Schliessenden an, was der
        // andere genommen hat.
        Hand hand = hand(Map.of("STONE", 20), Map.of());
        Truhenwache wache = wache(new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of()));

        var antwort = wache.oeffne(ANNA, true, hand);
        hand.truhe.put("STONE", 5);
        List<Component> beimSchliessen = wache.schliesse(ANNA, hand);

        assertTrue(antwort.geoeffnet(), bild(antwort.zeilen()));
        assertFalse(bild(antwort.zeilen()).contains("Jemand sieht gerade"),
                bild(antwort.zeilen()));
        assertEquals(List.of(), beimSchliessen, "ohne Zaehlung schweigt die Bilanz");
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "und in den Tagesstand wird nichts geschrieben");
        assertEquals(List.of(), hand.griffe, "angefasst wird die Truhe nicht");
    }

    @Test
    @DisplayName("NUR Verbote, kein Deckel: gezaehlt wird trotzdem - also einer zur Zeit")
    void verboteOhneDeckelZaehlenAuch() {
        // Die Rueckgabe braucht denselben Unterschied wie der Deckel: Zurueck geht, was
        // NEU hineingekommen ist. Sehen zwei zugleich hinein, bekaeme der Schliessende
        // die Einlage des anderen in die Hand - ein Weg, fremden Besitz zu bekommen.
        Truhenwerte nurVerbote = new Truhenwerte(Truhenwerte.KEIN_DECKEL,
                Set.of("written_book"));
        Hand hand = hand(Map.of(), Map.of("WRITTEN_BOOK", 1));
        Truhenwache wache = wache(nurVerbote);

        var besetzt = wache.oeffne(ANNA, true, hand);
        var allein = wache.oeffne(ANNA, false, hand);
        hand.truhe.put("WRITTEN_BOOK", 1);
        hand.rucksack.remove("WRITTEN_BOOK");
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertTrue(nurVerbote.zaehlt(), "sonst prueft diese Probe nichts");
        assertFalse(besetzt.geoeffnet(), "besetzt bleibt sie zu, auch ohne Deckel");
        assertTrue(allein.geoeffnet(), bild(allein.zeilen()));
        assertTrue(bild(zeilen).contains("WRITTEN_BOOK"), bild(zeilen));
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "Hineinlegen ist keine Entnahme - und ohne Deckel bleibt der Tag leer");
    }

    @Test
    @DisplayName("BESETZT: die Truhe geht nicht auf und es wird nichts gemerkt")
    void besetztGehtNichtAuf() {
        // Die Zusage, auf der die ganze Zaehlung steht. Bei zwei Zuschauern waere die
        // Differenz die Entnahme von zweien - angerechnet bekaeme sie der, der zuletzt
        // schliesst.
        Hand hand = hand(Map.of("STONE", 20), Map.of());
        Truhenwache wache = wache(WERTE);

        var antwort = wache.oeffne(ANNA, true, hand);

        assertFalse(antwort.geoeffnet());
        assertTrue(bild(antwort.zeilen()).contains("Jemand sieht gerade"),
                bild(antwort.zeilen()));
        // Und der entscheidende Teil: KEIN Schnappschuss. Sonst rechnete das naechste
        // Schliessen die Entnahme des ANDEREN gegen diesen Spieler.
        hand.truhe.put("STONE", 0);
        assertEquals(List.of(), wache.schliesse(ANNA, hand));
        assertEquals(0, ablage.behalten(ANNA, HEUTE));
    }

    @Test
    @DisplayName("AM DECKEL geht sie nicht auf - mit der Zahl des Deckels")
    void amDeckelGehtNichtAuf() {
        ablage.merke(ANNA, HEUTE, 16);
        Hand hand = hand(Map.of("STONE", 20), Map.of());

        var antwort = wache(WERTE).oeffne(ANNA, false, hand);

        assertFalse(antwort.geoeffnet());
        assertTrue(bild(antwort.zeilen()).contains("deine 16 Gegenstaende"),
                bild(antwort.zeilen()));
    }

    @Test
    @DisplayName("OHNE Deckel liest er einen eigenen Satz und keine Zahl")
    void ohneDeckelEigenerSatz() {
        Hand hand = hand(Map.of("STONE", 20), Map.of());

        var antwort = wache(WERTE.mitEntnahme(Truhenwerte.KEIN_DECKEL))
                .oeffne(ANNA, false, hand);

        assertTrue(antwort.geoeffnet());
        assertTrue(bild(antwort.zeilen()).contains("nimm, was du brauchst"),
                bild(antwort.zeilen()));
    }

    @Test
    @DisplayName("OHNE Deckel schweigt die Bilanz - und es wird auch nichts geschrieben")
    void ohneDeckelSchweigtDieBilanz() {
        // "7 Stueck behalten" allein sagt einem Spieler nichts, was er nicht in seinem
        // Rucksack sieht - und "heute noch frei" ist ohne Deckel keine Zahl.
        //
        // GESCHRIEBEN WIRD AUCH NICHTS [geaendert 10.11.2026]. Vorher stand hier
        // "gezaehlt wird trotzdem", ohne einen Grund dafuer. Die Zeile im Tagesstand
        // beantwortet genau eine Frage - "wie viel hat er heute schon?" -, und die
        // stellt einzig der Deckel. Ohne ihn ist sie ein Schreiben ohne Leser, das der
        // naechtliche Lauf wieder wegraeumen muss, und ihre Stoerungsmeldung spricht von
        // einem Deckel, den es nicht gibt. Der Preis ist bekannt und klein: Wer einen
        // Deckel mitten am Tag einschaltet, gibt allen den Tag neu.
        Hand hand = hand(Map.of("STONE", 20), Map.of());
        Truhenwache wache = wache(WERTE.mitEntnahme(
                Truhenwerte.KEIN_DECKEL));

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("STONE", 13);
        hand.rucksack.put("STONE", 7);
        List<Component> zeilen = wache.schliesse(ANNA, hand);

        assertEquals(List.of(), zeilen);
        assertEquals(0, ablage.behalten(ANNA, HEUTE),
                "keine Zeile ohne Deckel - gezaehlt wird hier nur noch fuer die"
                        + " Rueckgabe verbotener Gegenstaende");
        assertEquals(List.of(), hand.griffe, "und zurueckgelegt wird nichts");
    }

    @Test
    @DisplayName("Ohne Schnappschuss wird NICHT gerechnet")
    void ohneSchnappschussKeineRechnung() {
        // Ein Schliessen ohne Oeffnen gibt es im Spiel: Ein anderes Plugin bricht das
        // Oeffnen ab, oder der Spieler stand schon vor dem Serverstart daran. Eine
        // erfundene Differenz waere das Schlimmste - sie zaehlte ihm die Entnahme eines
        // anderen an.
        Hand hand = hand(Map.of("STONE", 0), Map.of());

        assertEquals(List.of(), wache(WERTE).schliesse(ANNA, hand));
        assertEquals(0, ablage.behalten(ANNA, HEUTE));
        assertEquals(List.of(), hand.griffe);
    }

    @Test
    @DisplayName("Der Schnappschuss gilt EINMAL - zweimal schliessen rechnet nicht zweimal")
    void schnappschussGiltEinmal() {
        Hand hand = hand(Map.of("STONE", 20), Map.of());
        Truhenwache wache = wache(WERTE);

        wache.oeffne(ANNA, false, hand);
        hand.truhe.put("STONE", 15);
        hand.rucksack.put("STONE", 5);
        wache.schliesse(ANNA, hand);
        wache.schliesse(ANNA, hand);

        assertEquals(5, ablage.behalten(ANNA, HEUTE),
                "aufschlagend geschrieben waere ein zweiter Aufruf eine zweite Entnahme");
    }

    @Test
    @DisplayName("Ohne Buch oder Sprache gibt es keine Wache")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class, () -> new Truhenwache(null, sprache));
        assertThrows(IllegalArgumentException.class,
                () -> new Truhenwache(buch(WERTE), null));
    }

    private Truhenwache wache(Truhenwerte werte) {
        return new Truhenwache(buch(werte), sprache);
    }

    private Truhenbuch buch(Truhenwerte werte) {
        return buch(new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitTruhe(werte)));
    }

    /**
     * Dasselbe Buch an einem Stand, den die Probe noch VERSTELLEN kann - fuer das
     * Neuladen mitten im offenen Fenster.
     */
    private Truhenbuch buch(Wirtschaftsstand stand) {
        return new Truhenbuch(Truhenverzeichnis.ausStellen(List.of(HALLE)), ablage,
                stand, UHR, Logger.getAnonymousLogger());
    }

    private static Hand hand(Map<String, Integer> truhe, Map<String, Integer> rucksack) {
        return new Hand(truhe, rucksack);
    }

    private static String bild(List<Component> zeilen) {
        return Sprachprobe.bild(zeilen);
    }

    /**
     * Zwei Karten und ein Fahrtenbuch - die Attrappe hinter der Naht.
     *
     * <p><b>Sie schreibt VOR dem Bewegen mit</b>, und zwar mit dem Ergebnis in derselben
     * Zeile: So steht im Fahrtenbuch auch ein Griff, der nichts bewegt hat ({@code -> 0}).
     * Ein Mitschreiben nur bei Erfolg liesse genau den Fall unsichtbar, auf den es
     * ankommt - der Rucksack, der nichts mehr hergibt.
     */
    private static final class Hand implements Truhenwache.Truhenhand {

        private final Map<String, Integer> truhe;
        private final Map<String, Integer> rucksack;
        private final List<String> griffe = new ArrayList<>();

        /** Wie viel die Truhe hoechstens noch aufnimmt - fuer die volle Kiste. */
        private int truheNimmtHoechstens = Integer.MAX_VALUE;

        private Hand(Map<String, Integer> truhe, Map<String, Integer> rucksack) {
            this.truhe = new LinkedHashMap<>(truhe);
            this.rucksack = new LinkedHashMap<>(rucksack);
        }

        @Override
        public Map<String, Integer> inhalt() {
            Map<String, Integer> kopie = new LinkedHashMap<>();
            truhe.forEach((material, stueck) -> {
                if (stueck > 0) {
                    kopie.put(material, stueck);
                }
            });
            return kopie;
        }

        @Override
        public int ausDerTruhe(String material, int stueck) {
            return nimm(truhe, "ausDerTruhe", material, stueck);
        }

        @Override
        public int inDieTruhe(String material, int stueck) {
            int moeglich = Math.min(stueck, truheNimmtHoechstens);
            griffe.add("inDieTruhe " + material + " " + stueck + " -> " + moeglich);
            truhe.merge(material, moeglich, Integer::sum);
            return moeglich;
        }

        @Override
        public int vomSpieler(String material, int stueck) {
            return nimm(rucksack, "vomSpieler", material, stueck);
        }

        @Override
        public int zumSpieler(String material, int stueck) {
            griffe.add("zumSpieler " + material + " " + stueck + " -> " + stueck);
            rucksack.merge(material, stueck, Integer::sum);
            return stueck;
        }

        private int nimm(Map<String, Integer> woraus, String was, String material,
                int stueck) {
            int da = woraus.getOrDefault(material, 0);
            int weg = Math.min(stueck, da);
            griffe.add(was + " " + material + " " + stueck + " -> " + weg);
            if (weg >= da) {
                woraus.remove(material);
            } else {
                woraus.put(material, da - weg);
            }
            return weg;
        }
    }
}
