package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Muenzhaus;
import de.freimarken.kern.Muenzwerte;
import de.freimarken.kern.Rangregel;
import de.freimarken.kern.Wechselbuch;
import de.freimarken.kern.Wechselwerte;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zeilen des Muenzhauses - die WAHL, die EINHEIT und die Zahl aus der DATEI.
 *
 * <p>Welche Worte dastehen, prueft {@code BildGleichTest} zeichengleich gegen die
 * ausgelieferte Vorlage. Hier stehen drei andere Fragen:
 * <ul>
 *   <li>WELCHER Satz zu welchem Bescheid und zu welchem Zustand einer Katalogzeile
 *       gehoert - das entscheidet das Programm.
 *   <li>Welche EINHEIT an welcher Zahl steht. Preise sind GOLDMARK, Kurse sind MARK, und
 *       beides ist im Programm ein {@code long}.
 *   <li>Welche ZAHL bei einer fehlenden Bedingung dasteht: die aus der
 *       {@code config.yml} und nicht die gemessene Differenz in Sekunden.
 * </ul>
 */
@DisplayName("Die Zeilen des Muenzhauses")
class MuenztextTest {

    private static final Sprache SPRACHE = Sprachprobe.spracheAusDemJar();

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());

    private static Muenzwerte.Posten hut() {
        return Muenzwerte.VORGABE.posten("kuerbishut").orElseThrow();
    }

    private static Muenzwerte.Posten blumen() {
        return Muenzwerte.VORGABE.posten("blumenkiste").orElseThrow();
    }

    private static Muenzhaus.Entscheid entscheid(Muenzhaus.Bescheid bescheid,
                                                 Muenzwerte.Posten posten, long stand,
                                                 List<Rangregel.Bedingungsstand> fehlend,
                                                 int platz) {
        return new Muenzhaus.Entscheid(bescheid, Optional.ofNullable(posten),
                posten == null ? 0L : posten.preis(), stand, fehlend, platz);
    }

    private static Wechselbuch.Auskunft auskunft() {
        return new Wechselbuch.Auskunft(Wechselwerte.VORGABE, 12L, 0L, 12L);
    }

    private static Muenzhaus.Tafel tafel(Muenzwerte werte, long goldmark,
                                         List<Muenzhaus.Zeile> zeilen) {
        return new Muenzhaus.Tafel(werte, goldmark, zeilen);
    }

    @Test
    @DisplayName("Die Tafel nennt den Stand, beide Kurse UND den sichtbaren Wechseldeckel")
    void tafelNenntDenDeckel() {
        // DAS IST DER AUFTRAG VON PLAN 2.1 AM ORT: Kontostand, Wechsel und Wechseldeckel
        // sichtbar. Die drei Zeilen kommen von der Wechselstube - abgeschrieben waeren
        // sie eine zweite Fassung derselben Auskunft.
        List<Component> zeilen = Muenztext.tafelZeilen(SPRACHE,
                tafel(Muenzwerte.VORGABE, 12L, List.of()), auskunft());

        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.contains("&6Muenzhaus"), bild);
        assertTrue(bild.contains("12 Goldmark"), "der Goldmarkstand: " + bild);
        assertTrue(bild.contains("1.000 Mark"), "der Hinwegkurs: " + bild);
        assertTrue(bild.contains("800 Mark"), "der Rueckwegkurs: " + bild);
        assertTrue(bild.contains("10.000 Mark"), "der Deckel in Mark: " + bild);
    }

    @Test
    @DisplayName("Je Posten genau EINE Zeile, mit Name, Preis und Inhalt darin")
    void eineZeileJePosten() {
        // Die Zeilenzahl ist die Zusage: Kopf, Stand, Kurse, Deckel, vier Posten, Hilfe.
        // Mit dem Inhalt in eigenen Zeilen waere diese Tafel bei vier Posten zwischen
        // sieben und siebenundzwanzig Zeilen lang.
        List<Muenzhaus.Zeile> zeilen = new java.util.ArrayList<>();
        for (Muenzwerte.Posten posten : Muenzwerte.VORGABE.katalog()) {
            zeilen.add(new Muenzhaus.Zeile(posten, List.of(), true));
        }

        List<Component> gebaut = Muenztext.tafelZeilen(SPRACHE,
                tafel(Muenzwerte.VORGABE, 50L, zeilen), auskunft());

        assertEquals(9, gebaut.size(), Sprachprobe.bild(gebaut));
        String bild = Sprachprobe.bild(gebaut);
        assertTrue(bild.contains("Kuerbishut"), "der Name aus der Sprachdatei: " + bild);
        assertTrue(bild.contains("1x carved_pumpkin"),
                "der INHALT mit dem technischen Namen: " + bild);
        assertTrue(bild.contains("16x rose_bush, 16x peony, 16x lilac"),
                "drei Gegenstaende in der Reihenfolge der Datei: " + bild);
    }

    @Test
    @DisplayName("Die drei Zustaende einer Katalogzeile haben drei verschiedene Bilder")
    void dreiZustaendeDreiBilder() {
        var frei = new Muenzhaus.Zeile(hut(), List.of(), true);
        var zuTeuer = new Muenzhaus.Zeile(hut(), List.of(), false);
        var gesperrt = new Muenzhaus.Zeile(hut(),
                List.of(new Rangregel.Bedingungsstand(Bedingungsart.PARZELLEN, 1L, 0L)), true);

        Set<String> bilder = new LinkedHashSet<>();
        for (Muenzhaus.Zeile zeile : List.of(frei, zuTeuer, gesperrt)) {
            bilder.add(Sprachprobe.bild(Muenztext.tafelZeilen(SPRACHE,
                    tafel(Muenzwerte.VORGABE, 2L, List.of(zeile)), auskunft())));
        }

        assertEquals(3, bilder.size(), "drei Zustaende, drei Zeilen: " + bilder);
    }

    @Test
    @DisplayName("Die Bedingung zuerst: ein gesperrter Posten zeigt nicht 'zu teuer'")
    void bedingungVorDemGeld() {
        // Wer beides nicht hat, soll die groessere Huerde lesen - Geld kann er sich
        // holen, die Spielstunden nicht.
        var beides = new Muenzhaus.Zeile(hut(),
                List.of(new Rangregel.Bedingungsstand(Bedingungsart.PARZELLEN, 1L, 0L)), false);

        String bild = Sprachprobe.bild(Muenztext.tafelZeilen(SPRACHE,
                tafel(Muenzwerte.VORGABE, 0L, List.of(beides)), auskunft()));

        assertTrue(bild.contains("noch nicht frei"), bild);
    }

    @Test
    @DisplayName("Ist das Muenzhaus aus, steht das statt des Katalogs - und der Stand bleibt")
    void ausStattKatalog() {
        String bild = Sprachprobe.bild(Muenztext.tafelZeilen(SPRACHE,
                tafel(Muenzwerte.VORGABE.mitAn(false), 12L, List.of()), auskunft()));

        assertTrue(bild.contains("nichts verkauft"), bild);
        assertFalse(bild.contains("Der Katalog ist leer"),
                "aus und leer sind zwei verschiedene Aussagen: " + bild);
        assertTrue(bild.contains("12 Goldmark"), "der Stand bleibt zu sehen: " + bild);
    }

    @Test
    @DisplayName("Ein leerer Katalog sagt das - und nicht, das Haus sei zu")
    void leerStattAus() {
        String bild = Sprachprobe.bild(Muenztext.tafelZeilen(SPRACHE,
                tafel(new Muenzwerte(true, List.of()), 12L, List.of()), auskunft()));

        assertTrue(bild.contains("Der Katalog ist leer"), bild);
        assertFalse(bild.contains("nichts verkauft"), bild);
    }

    @Test
    @DisplayName("Jeder Bescheid bekommt seine EIGENE Zeile")
    void jederBescheidEigeneZeile() {
        // Die Probe gegen das Vertauschen, das ein Schalter ohne default NICHT faengt:
        // Zwei Bescheide mit demselben Satz uebersetzen tadellos.
        Set<String> bilder = new LinkedHashSet<>();
        for (Muenzhaus.Bescheid bescheid : Muenzhaus.Bescheid.values()) {
            var entscheid = switch (bescheid) {
                case UNBEKANNT, AUS, GESTOERT -> entscheid(bescheid, null, 0L, List.of(),
                        0);
                case ERSTATTET -> entscheid(bescheid, hut(), 10L, List.of(), 1);
                case BEDINGUNG_FEHLT -> entscheid(bescheid, blumen(), 10L,
                        List.of(new Rangregel.Bedingungsstand(Bedingungsart.PARZELLEN, 1L, 0L)),
                        0);
                case KEIN_PLATZ -> entscheid(bescheid, hut(), 10L, List.of(), 1);
                case ZU_WENIG_GOLDMARK -> entscheid(bescheid, hut(), 1L, List.of(), 0);
                case BEREIT, GEKAUFT -> entscheid(bescheid, hut(), 10L, List.of(), 0);
            };

            String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE,
                    "kuerbishu", entscheid));

            assertTrue(bilder.add(bild), bescheid + " sagt dasselbe wie ein anderer"
                    + " Bescheid: " + bild);
        }
        assertEquals(Muenzhaus.Bescheid.values().length, bilder.size());
    }

    @Test
    @DisplayName("Die Rueckfrage nennt Preis, Inhalt, den Stand danach UND den Befehl")
    void rueckfrageNenntAlles() {
        // Ohne den Befehl ist die Rueckfrage eine Sackgasse: Der Spieler liest, was es
        // kostet, und findet nicht, wie er zustimmt.
        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "kuerbishut",
                entscheid(Muenzhaus.Bescheid.BEREIT, hut(), 10L, List.of(), 0)));

        assertTrue(bild.contains("2 Goldmark"), "der Preis in GOLDMARK: " + bild);
        assertTrue(bild.contains("1x carved_pumpkin"), "der Inhalt: " + bild);
        assertTrue(bild.contains("8 Goldmark"), "der Stand DANACH: " + bild);
        assertTrue(bild.contains("/mark muenzhaus kaufen kuerbishut bestaetigen"),
                "der Befehl mit dem SCHLUESSEL, nicht mit dem Namen: " + bild);
    }

    @Test
    @DisplayName("Die Absage nennt, was der Spieler GETIPPT hat")
    void unbekanntNenntDasGetippte() {
        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "kuerbishu",
                entscheid(Muenzhaus.Bescheid.UNBEKANNT, null, 0L, List.of(), 0)));

        assertTrue(bild.contains("kuerbishu"), "ohne das Getippte sucht er falsch: "
                + bild);
    }

    @Test
    @DisplayName("Jede Bedingungsart hat ihr Wort in der ausgelieferten Vorlage - beide Formen")
    void jedeBedingungsartHatIhrWort() {
        // DIE DECKUNG DER DRITTEN MECHANISCHEN FAMILIE: Die fuenf Saetze werden ueber
        // MarkSatz.bedingungssatz gewaehlt und nirgends namentlich getippt - Tafel 30
        // laesst sie darum nur durch, weil Sprachschluessel.familienDeckung sie nennt.
        // Was sie wirklich deckt, ist diese Probe: Jede Art geht durch die echte Zeile,
        // und zwar in BEIDEN Zahlformen.
        Set<String> bilder = new LinkedHashSet<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            for (long zahl : new long[] {1L, 2L}) {
                var fehlend = new Rangregel.Bedingungsstand(art, art.inMesseinheit(zahl), 0L);
                String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                        entscheid(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, blumen(), 10L,
                                List.of(fehlend), 0)));

                assertTrue(bild.contains(String.valueOf(zahl)),
                        art + " mit " + zahl + ": " + bild);
                assertTrue(bilder.add(bild), art + " mit " + zahl
                        + " sagt dasselbe wie eine andere Art: " + bild);
            }
        }
        assertEquals(Bedingungsart.values().length * 2, bilder.size());
    }

    @Test
    @DisplayName("Die Zahl der Bedingung ist die aus der DATEI, nicht die in Sekunden")
    void zahlAusDerDatei() {
        // GEMESSEN GEGEN DIE DATEI und nicht gegen den Fehlend-Wert: Rangregel rechnet in
        // Sekunden, und ungerechnet gezeigt stuende hier "noch 18.000 Stunden". Eine
        // Probe, die ihre Erwartung aus inMesseinheit holt, faengt genau das nicht.
        var fehlend = new Rangregel.Bedingungsstand(Bedingungsart.TAETIGE_STUNDEN,
                5L * Bedingungsart.SEKUNDEN_JE_STUNDE, 0L);

        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, blumen(), 10L,
                        List.of(fehlend), 0)));

        assertTrue(bild.contains("5 Stunden"), bild);
        assertFalse(bild.contains("18000"), "die Sekunden gehoeren nicht in die Zeile: "
                + bild);
        assertFalse(bild.contains("18.000"), bild);
    }

    @Test
    @DisplayName("Mehrere fehlende Bedingungen stehen in der Reihenfolge der Aufzaehlung")
    void mehrereBedingungenInReihenfolge() {
        var zeit = new Rangregel.Bedingungsstand(Bedingungsart.TAETIGE_STUNDEN,
                5L * Bedingungsart.SEKUNDEN_JE_STUNDE, 0L);
        var parzellen = new Rangregel.Bedingungsstand(Bedingungsart.PARZELLEN, 1L, 0L);

        List<Component> zeilen = Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, blumen(), 10L,
                        List.of(zeit, parzellen), 0));

        assertEquals(3, zeilen.size(), "eine Kopfzeile und zwei Bedingungen: "
                + Sprachprobe.bild(zeilen));
        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.indexOf("Stunden") < bild.indexOf("Parzelle"),
                "die Reihenfolge kommt aus der Aufzaehlung: " + bild);
    }

    @Test
    @DisplayName("Der Goldverlauf zeigt GOLDMARK - an beiden Zahlen jeder Zeile")
    void goldverlaufZeigtGoldmark() {
        // DIE FALLE DIESES BAUSCHRITTES: Die Verlaufszeile hat drei Geldstellen, und zwei
        // davon stehen in mitVorzeichen. Bleibt eine beim Markwort, widerspricht sich
        // EINE Zeile in sich - um das Achthundertfache.
        var buchung = new Buchung(1L, Instant.parse("2026-11-20T13:37:00Z"), ANNA,
                Buchungsgrund.MUENZHAUS_KAUF, -2L, 8L);
        var konto = new Muenzhaus.Konto(8L, List.of(buchung));

        String bild = Sprachprobe.bild(Muenztext.verlaufZeilen(SPRACHE, konto, BERLIN));

        assertTrue(bild.contains("-2 Goldmark"), "der Betrag: " + bild);
        assertTrue(bild.contains("(Stand 8 Goldmark)"), "der Stand: " + bild);
        assertFalse(bild.contains("Mark  ") || bild.contains(" Mark)"),
                "nirgends das Markwort: " + bild);
        assertTrue(bild.contains("Im Muenzhaus gekauft"), "das Wort des Grundes: " + bild);
    }

    @Test
    @DisplayName("Ein leeres Goldbuch sagt es, statt eine leere Liste zu zeigen")
    void leeresGoldbuch() {
        String bild = Sprachprobe.bild(Muenztext.verlaufZeilen(SPRACHE,
                new Muenzhaus.Konto(0L, List.of()), BERLIN));

        assertTrue(bild.contains("Noch keine Goldmark bewegt"), bild);
        assertTrue(bild.contains("0 Goldmark"), "der Kopf nennt den Stand: " + bild);
    }

    @Test
    @DisplayName("Eine MARKbuchung im Goldverlauf wirft, statt zu luegen")
    void markbuchungImGoldverlaufWirft() {
        // Der Riegel in Verlaufszeile: Die Zahl waere richtig und die Waehrung falsch,
        // und keine Probe wuerde davon rot - darum wirft die Zeile.
        var markbuchung = new Buchung(1L, Instant.parse("2026-11-20T13:37:00Z"), ANNA,
                Buchungsgrund.PARZELLE_GEKAUFT, -25_000L, 25_000L);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Muenztext.verlaufZeilen(SPRACHE,
                        new Muenzhaus.Konto(8L, List.of(markbuchung)), BERLIN));
    }

    @Test
    @DisplayName("Der Preis steht in Goldmark, die Kurse in Mark - am fertigen Bild")
    void einheitenAmBild() {
        String gekauft = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.GEKAUFT, hut(), 8L, List.of(), 0)));

        assertTrue(gekauft.contains("2 Goldmark"), gekauft);
        assertTrue(gekauft.contains("8 Goldmark"), gekauft);
        assertFalse(gekauft.contains(" Mark"), "kein Markwort in einem Goldmarkpreis: "
                + gekauft);
    }

    @Test
    @DisplayName("Zu wenig Goldmark nennt Preis, Stand und den Unterschied")
    void zuWenigNenntDreiZahlen() {
        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.ZU_WENIG_GOLDMARK,
                        Muenzwerte.VORGABE.posten("musikscheibe").orElseThrow(), 2L,
                        List.of(), 0)));

        assertTrue(bild.contains("5 Goldmark"), "der Preis: " + bild);
        assertTrue(bild.contains("2 Goldmark"), "der Stand: " + bild);
        assertTrue(bild.contains("3 Goldmark"), "was fehlt: " + bild);
    }

    @Test
    @DisplayName("Kein Platz nennt die Stueckzahl - in beiden Zahlformen")
    void keinPlatzNenntStueck() {
        String eins = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.KEIN_PLATZ, hut(), 10L, List.of(), 1)));
        String viele = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "x",
                entscheid(Muenzhaus.Bescheid.KEIN_PLATZ, blumen(), 10L, List.of(), 48)));

        assertTrue(eins.contains("1 Stueck"), eins);
        assertTrue(viele.contains("48 Stueck"), viele);
        assertFalse(eins.equals(viele));
    }

    @Test
    @DisplayName("Vom GELD redet nur die Erstattung - der blosse Blick nicht")
    void nurDieErstattungRedetVomGeld() {
        // BEFUND DER PRUEFRUNDE (20.11.2026): Beide Stoerungen hatten denselben Satz, und
        // der sprach vom Geld. Wer nur /mark muenzhaus tippte und auf eine schweigende
        // Datenbank traf, las "Dein Geld ist zurueck" - und suchte danach eine Buchung,
        // die es nicht gibt. Dieselbe Zeile stand auch an der RUECKFRAGE, wo nie etwas
        // abgebucht wird.
        String blick = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "kuerbishut",
                entscheid(Muenzhaus.Bescheid.GESTOERT, null, 0L, List.of(), 0)));
        String erstattet = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE,
                "kuerbishut", entscheid(Muenzhaus.Bescheid.ERSTATTET, hut(), 10L,
                        List.of(), 1)));

        assertFalse(blick.contains("Geld"), "der blosse Blick hat kein Geld bewegt: "
                + blick);
        assertTrue(blick.contains("nichts passiert"), blick);
        assertTrue(erstattet.contains("Geld ist zurueck"), erstattet);
    }

    @Test
    @DisplayName("Die zwei Stoerungswachen der Auskuenfte sagen NICHT, das Geld sei zurueck")
    void auskunftswachenRedenNichtVomGeld() {
        // Die beiden Wachen in ParzellenBefehle gehoeren zu reinen Auskuenften - Tafel
        // und Goldverlauf. Sie waehlen denselben Satz wie die Stoerung vor der Zahlung,
        // und genau das ist die Zusage dieser Probe.
        String tafel = Sprachprobe.bild(ParzellenBefehle.muenzhaustafelZeilen(SPRACHE,
                Optional.empty(), Optional.of(auskunft())));
        String verlauf = Sprachprobe.bild(ParzellenBefehle.muenzverlaufZeilen(SPRACHE,
                Optional.empty(), BERLIN));

        assertFalse(tafel.contains("Geld"), tafel);
        assertFalse(verlauf.contains("Geld"), verlauf);
        assertTrue(tafel.contains("nichts passiert"), tafel);
        assertTrue(verlauf.contains("nichts passiert"), verlauf);
    }

    @Test
    @DisplayName("Die Bedingung guthaben ist ein BETRAG - mit Tausenderpunkt und Waehrung")
    void guthabenAlsBetrag() {
        // BEFUND DER PRUEFRUNDE (20.11.2026): Die Zahl stand nackt da, und "Mark" stand
        // fest in der Sprachdatei. Derselbe Betrag hiess zwei Zeilen hoeher
        // "50.000 Mark" und in dieser einen Zeile "50000 Mark" - und wer seine Waehrung
        // umbenannte, erreichte diese Zeile nicht.
        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "kuerbishut",
                entscheid(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, hut(), 0L,
                        List.of(new Rangregel.Bedingungsstand(Bedingungsart.GUTHABEN, 50_000L,
                                0L)), 0)));

        assertTrue(bild.contains("50.000 Mark"), "der Betrag kommt aus der Waehrung: "
                + bild);
        assertFalse(bild.contains("50000"), "nackt steht er nirgends mehr: " + bild);
    }

    @Test
    @DisplayName("Eine Bedingung, die KEIN Geld ist, bleibt nackt")
    void stundenBleibenNackt() {
        // Die Gegenrichtung: Mit dem Betrag an JEDER Bedingung stuende "- 5 Mark Stunden
        // taetiger Zeit" da. Die Probe darueber waere auch dann gruen.
        String bild = Sprachprobe.bild(Muenztext.entscheidZeilen(SPRACHE, "kuerbishut",
                entscheid(Muenzhaus.Bescheid.BEDINGUNG_FEHLT, hut(), 0L,
                        List.of(new Rangregel.Bedingungsstand(Bedingungsart.TAETIGE_STUNDEN,
                                18_000L, 0L)), 0)));

        assertTrue(bild.contains("5 Stunden"), bild);
        assertFalse(bild.contains("Mark"), "Stunden sind kein Geld: " + bild);
    }
}
