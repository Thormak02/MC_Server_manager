package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Mietvertrag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die WAHL des richtigen Satzes - das Einzige, was in {@link Miettext} selbst liegt.
 *
 * <p>Der Wortlaut steht in der Sprachdatei und wird hier nicht abgeschrieben: Das BILD
 * haelt die festgenagelte Tafel in {@code BildGleichTest}. Geprueft wird, WELCHER Satz
 * zutrifft, mit welchen Zahlen und in welcher Reihenfolge - denn genau das weiss nur das
 * Programm.
 *
 * <p>Diese Datei muss es geben, und das ist ein Befund aus Teil A: Die Tafel ruft die
 * SAETZE unmittelbar und geht nicht durch die Naht. Eine verdrehte Reihenfolge und ein
 * weggelassener Wert blieben darum beide gruen.
 */
@DisplayName("Die Zeilen der Ladenmiete")
class MiettextTest {

    private static final UUID ANNA =
            UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    private static final Mietvertrag HALLE_1 = new Mietvertrag("halle-1", ANNA, HEUTE,
            HEUTE.plusDays(29), 500L, 5_000L);

    private Sprache sprache;

    @BeforeEach
    void vorher() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    private List<String> bild(List<Component> zeilen) {
        List<String> bilder = new ArrayList<>();
        zeilen.forEach(zeile -> bilder.add(Sprachprobe.bild(zeile)));
        return bilder;
    }

    private List<String> mieten(Mietbuch.Abschluss abschluss, String getippt) {
        return bild(Miettext.mietenZeilen(sprache, abschluss, getippt));
    }

    private List<String> verlaengern(Mietbuch.Bescheid bescheid, Mietvertrag vertrag,
                                     long restTage, int dauer) {
        return bild(Miettext.verlaengernZeilen(sprache,
                new Mietbuch.Verlaengerung(bescheid, vertrag, restTage), dauer));
    }

    private static Mietbuch.Abschluss gemietet(long kosten, boolean lizenz) {
        return new Mietbuch.Abschluss(Mietbuch.Bescheid.GEMIETET, HALLE_1, kosten,
                lizenz, 0L);
    }

    // ------------------------------------------------------------------- mieten

    @Test
    @DisplayName("Ein Abschluss sind DREI Zeilen, in dieser Reihenfolge")
    void abschlussHatDreiZeilen() {
        // Drei, weil ein Abschluss drei verschiedene Dinge bindet: den Ort, das Geld
        // jetzt, das Geld taeglich. Die Reihenfolge ist die, in der ein Spieler fragt.
        List<String> zeilen = mieten(gemietet(55_000L, true), "halle-1");

        assertEquals(3, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("gemietet"), zeilen.get(0));
        assertTrue(zeilen.get(1).contains("Abgebucht"), zeilen.get(1));
        assertTrue(zeilen.get(2).contains("Taeglich"), zeilen.get(2));
    }

    @Test
    @DisplayName("Die Kopfzeile nennt Laden, Enddatum und die ZAHL der Tage")
    void kopfzeileNenntAlleDrei() {
        List<String> zeilen = mieten(gemietet(55_000L, true), "halle-1");

        assertTrue(zeilen.get(0).contains("halle-1"), zeilen.get(0));
        assertTrue(zeilen.get(0).contains("06.12.2026"),
                "der letzte gueltige Tag, aus laden.tag-muster: " + zeilen.get(0));
        assertTrue(zeilen.get(0).contains("30 Tage"),
                "30 und nicht 29: laeuftBis ist eingeschlossen, also gilt der Vertrag an"
                        + " 30 Tagen. " + zeilen.get(0));
    }

    @Test
    @DisplayName("Beim ERSTEN Laden nennt die Kostenzeile die Lizenz, sonst nicht")
    void lizenzNurBeimErsten() {
        // Die Verzweigung ist die ganze Aufgabe dieser Stelle. 50.000 Mark Unterschied:
        // Wer 55.000 abgebucht sieht und 5.000 erwartet hat, soll in DERSELBEN Zeile
        // lesen, warum.
        String erstmals = mieten(gemietet(55_000L, true), "halle-1").get(1);
        assertTrue(erstmals.contains("55.000 Mark"), erstmals);
        assertTrue(erstmals.contains("50.000 Mark"),
                "die Lizenz ist 55.000 minus die Kaution 5.000: " + erstmals);
        assertTrue(erstmals.contains("Ladenlizenz"), erstmals);

        String weiter = mieten(gemietet(5_000L, false), "halle-1").get(1);
        assertTrue(weiter.contains("5.000 Mark"), weiter);
        assertFalse(weiter.contains("Ladenlizenz"),
                "ohne geflossene Lizenz darf sie nicht in der Zeile stehen - sonst"
                        + " glaubt der Spieler, er haette sie geschenkt bekommen: "
                        + weiter);
    }

    @Test
    @DisplayName("Die Lizenz wird aus KOSTEN minus Kaution gerechnet, nicht aus der Datei")
    void lizenzKommtAusDemGebuchten() {
        // Zwischen der Buchung und dieser Zeile kann ein Neuladen liegen. Was gezahlt
        // wurde, steht in kosten und in der Vertragszeile - nicht in der config.yml.
        // Darum eine Kaution, die KEINE der Vorgaben ist.
        // Die Lizenz in diesem Fall ist 30.000 und NICHT die ausgelieferte 50.000, und
        // das ist der Zweck der Zahlen: Eine Einspritzung, die hier stumpf 50.000
        // einsetzte, kam an einem Gegenbeispiel mit 50.000 vorbei - gemessen am
        // 08.11.2026. Auch die Kaution ist keine Vorgabe.
        Mietvertrag eigenartig = new Mietvertrag("halle-1", ANNA, HEUTE,
                HEUTE.plusDays(29), 500L, 1_234L);
        String zeile = bild(Miettext.mietenZeilen(sprache,
                new Mietbuch.Abschluss(Mietbuch.Bescheid.GEMIETET, eigenartig, 31_234L,
                        true, 0L),
                "halle-1")).get(1);

        assertTrue(zeile.contains("30.000 Mark"),
                "31.234 minus 1.234: " + zeile);
        assertTrue(zeile.contains("31.234 Mark"), zeile);
    }

    @Test
    @DisplayName("Die Tageszeile nennt die VEREINBARTE Miete")
    void tageszeileNenntDieVereinbarteMiete() {
        Mietvertrag alt = new Mietvertrag("halle-1", ANNA, HEUTE, HEUTE.plusDays(29),
                300L, 5_000L);
        String zeile = bild(Miettext.mietenZeilen(sprache,
                new Mietbuch.Abschluss(Mietbuch.Bescheid.GEMIETET, alt, 5_000L, false,
                        0L),
                "halle-1")).get(2);

        assertTrue(zeile.contains("300 Mark"), zeile);
    }

    @Test
    @DisplayName("Eine Absage darf einen HINWEIS in einer zweiten Zeile tragen")
    void absagenDuerfenEinenHinweisTragen() {
        // Der Befund der zweiten Probenrunde: Drei Absagen tragen absichtlich einen
        // Hinweis darunter, und sprache.eineZeile wirft daran. Absagen gehen darum ueber
        // sprache.bau. Geprueft wird der ZWECK und nicht die Zahl: Die zweite Zeile nennt
        // den Befehl, mit dem es weitergeht.
        List<String> unbekannt = mieten(new Mietbuch.Abschluss(
                Mietbuch.Bescheid.KEINE_EINHEIT, null, 0L, false, 0L), "halle-9");
        assertEquals(2, unbekannt.size(), unbekannt.toString());
        assertTrue(unbekannt.get(1).contains("/laden"),
                "der Hinweis nennt den Befehl: " + unbekannt.get(1));

        List<String> schon = mieten(new Mietbuch.Abschluss(
                Mietbuch.Bescheid.SCHON_EIN_LADEN, HALLE_1, 0L, false, 0L), "halle-2");
        assertEquals(2, schon.size(), schon.toString());
        assertTrue(schon.get(1).contains("/laden kuendigen"), schon.get(1));

        List<String> keiner = bild(Miettext.kuendigenZeilen(sprache,
                new Mietbuch.Kuendigung(Mietbuch.Bescheid.KEIN_LADEN, null, 0L)));
        assertEquals(2, keiner.size(), keiner.toString());
        assertTrue(keiner.get(1).contains("/laden"), keiner.get(1));
    }

    @Test
    @DisplayName("Absagen ohne Hinweis sind EINE Zeile")
    void absagenOhneHinweisSindEine() {
        for (Mietbuch.Bescheid bescheid : List.of(Mietbuch.Bescheid.SCHON_VERMIETET,
                Mietbuch.Bescheid.ZU_WENIG_GELD, Mietbuch.Bescheid.GESTOERT)) {
            List<String> zeilen = mieten(new Mietbuch.Abschluss(bescheid, null, 55_000L,
                    true, 50_000L), "halle-9");
            assertEquals(1, zeilen.size(), bescheid + ": " + zeilen);
        }
    }

    @Test
    @DisplayName("Die Absage zum unbekannten Stand nennt den GETIPPTEN Namen")
    void unbekannterStandNenntDasGetippte() {
        // Den getippten und nicht einen normierten: Der Spieler soll sehen, was er
        // geschrieben hat - sonst sucht er den Tippfehler nicht.
        String zeile = mieten(new Mietbuch.Abschluss(Mietbuch.Bescheid.KEINE_EINHEIT,
                null, 0L, false, 0L), "hale-1").get(0);

        assertTrue(zeile.contains("hale-1"), zeile);
    }

    @Test
    @DisplayName("Die Absage 'schon ein Laden' nennt SEINEN Laden, nicht den gewuenschten")
    void schonEinLadenNenntSeinen() {
        // Sonst sucht er, welchen er eigentlich hat. Das ist genau der Unterschied, den
        // die Bildtafel nicht sehen kann: Dort steht nur, wie der Satz einen Namen
        // einsetzt - nicht, WELCHEN.
        String zeile = mieten(new Mietbuch.Abschluss(Mietbuch.Bescheid.SCHON_EIN_LADEN,
                HALLE_1, 0L, false, 0L), "halle-2").get(0);

        assertTrue(zeile.contains("halle-1"), zeile);
        assertFalse(zeile.contains("halle-2"),
                "der gewuenschte Stand gehoert nicht in diese Zeile: " + zeile);
    }

    @Test
    @DisplayName("Die Geldabsage rechnet aus, wie viel FEHLT")
    void geldabsageRechnetDenRest() {
        // Die eine Rechnung, die das Programm dem Spieler abnimmt. Drei verschiedene
        // Zahlen, damit ein Vertauschen auffaellt.
        String zeile = mieten(new Mietbuch.Abschluss(Mietbuch.Bescheid.ZU_WENIG_GELD,
                null, 55_000L, true, 12_300L), "halle-1").get(0);

        assertTrue(zeile.contains("kostet 55.000 Mark"), zeile);
        assertTrue(zeile.contains("du hast 12.300 Mark"), zeile);
        assertTrue(zeile.contains("fehlen 42.700 Mark"),
                "55.000 minus 12.300 - ein Spieler soll nicht subtrahieren muessen: "
                        + zeile);
    }

    @Test
    @DisplayName("Ein Bescheid einer ANDEREN Tat bricht, statt etwas zu erfinden")
    void fremderBescheidWirft() {
        // Der switch ist ein AUSDRUCK und nennt jeden Bescheid. Diese Probe haelt fest,
        // dass die fremden nicht stillschweigend eine Zeile bekommen - sonst stuende
        // eines Tages "Du hast halle-1 gemietet" nach einer Kuendigung.
        assertThrows(IllegalArgumentException.class, () -> Miettext.mietenZeilen(sprache,
                new Mietbuch.Abschluss(Mietbuch.Bescheid.GEKUENDIGT, HALLE_1, 0L, false,
                        0L),
                "halle-1"));
        assertThrows(IllegalArgumentException.class, () -> Miettext.verlaengernZeilen(
                sprache,
                new Mietbuch.Verlaengerung(Mietbuch.Bescheid.GEMIETET, HALLE_1, 0L), 30));
        assertThrows(IllegalArgumentException.class, () -> Miettext.kuendigenZeilen(
                sprache,
                new Mietbuch.Kuendigung(Mietbuch.Bescheid.VERLAENGERT, "halle-1", 0L)));
    }

    // -------------------------------------------------------------- verlaengern

    @Test
    @DisplayName("Die Verlaengerung nennt Laden, neues Ende und Resttage")
    void verlaengerungNenntDreiDinge() {
        String zeile = verlaengern(Mietbuch.Bescheid.VERLAENGERT,
                HALLE_1.bis(HEUTE.plusDays(59)), 59L, 30).get(0);

        assertTrue(zeile.contains("halle-1"), zeile);
        assertTrue(zeile.contains("05.01.2027"), zeile);
        assertTrue(zeile.contains("59 Tage"), zeile);
    }

    @Test
    @DisplayName("Die Absage 'noch zu frueh' nennt BEIDE Zahlen")
    void nochZuFruehNenntBeide() {
        // "noch 40 Tage" allein sagt nicht, dass bei 30 Tagen Mietdauer erst ab Tag 11
        // verlaengert werden kann. Die Dauer kommt von aussen und wird hier NICHT aus dem
        // Vertrag gerechnet: Sie steht in der config.yml und kann sich geaendert haben.
        String zeile = verlaengern(Mietbuch.Bescheid.NOCH_ZU_FRUEH, HALLE_1, 40L, 30)
                .get(0);

        assertTrue(zeile.contains("noch 40 Tage"), zeile);
        assertTrue(zeile.contains("als 30 Tage"), zeile);
        assertTrue(zeile.contains("halle-1"), zeile);
    }

    @Test
    @DisplayName("Die Dauer in der Absage kommt von AUSSEN, nicht aus dem Vertrag")
    void dauerKommtVonAussen() {
        // Die Gegenprobe: Mit derselben Verlaengerung und einer anderen Dauer muss eine
        // andere Zahl dastehen. Eine Fassung, die die Dauer aus dem Vertrag rechnete,
        // waere bei der Probe darueber gruen und nennte hier dieselbe 30.
        String zeile = verlaengern(Mietbuch.Bescheid.NOCH_ZU_FRUEH, HALLE_1, 40L, 7)
                .get(0);

        assertTrue(zeile.contains("als 7 Tage"), zeile);
    }

    // ---------------------------------------------------------------- kuendigen

    @Test
    @DisplayName("Mit Kaution sind es ZWEI Zeilen, ohne nur eine")
    void kuendigungHatEineOderZweiZeilen() {
        List<String> mit = bild(Miettext.kuendigenZeilen(sprache,
                new Mietbuch.Kuendigung(Mietbuch.Bescheid.GEKUENDIGT, "halle-1",
                        5_000L)));
        assertEquals(2, mit.size(), mit.toString());
        assertTrue(mit.get(0).contains("halle-1"), mit.get(0));
        assertTrue(mit.get(1).contains("5.000 Mark"), mit.get(1));

        // Ohne hinterlegte Kaution: "0 Mark zurueck" sieht nach einem Fehler aus.
        List<String> ohne = bild(Miettext.kuendigenZeilen(sprache,
                new Mietbuch.Kuendigung(Mietbuch.Bescheid.GEKUENDIGT, "halle-1", 0L)));
        assertEquals(1, ohne.size(), ohne.toString());
    }

    @Test
    @DisplayName("Kein Laden und Stoerung sind je eine Zeile - in beiden Taten")
    void geteilteAbsagen() {
        // KEIN_LADEN und GESTOERT treffen zwei Taten. Beide Wege muessen dieselbe Zeile
        // liefern, sonst liest ein Spieler zwei verschiedene Saetze fuer dasselbe.
        assertEquals(
                bild(Miettext.kuendigenZeilen(sprache, new Mietbuch.Kuendigung(
                        Mietbuch.Bescheid.KEIN_LADEN, null, 0L))),
                verlaengern(Mietbuch.Bescheid.KEIN_LADEN, null, 0L, 30));
        assertEquals(
                bild(Miettext.kuendigenZeilen(sprache, new Mietbuch.Kuendigung(
                        Mietbuch.Bescheid.GESTOERT, null, 0L))),
                verlaengern(Mietbuch.Bescheid.GESTOERT, null, 0L, 30));
    }

    // -------------------------------------------------------------------- Stand

    @Test
    @DisplayName("Die Standzeile nennt Laden, Ende, Resttage und die vereinbarte Miete")
    void standzeileNenntVier() {
        String zeile = Sprachprobe.bild(Miettext.standZeile(sprache, HALLE_1, 29L));

        assertTrue(zeile.contains("halle-1"), zeile);
        assertTrue(zeile.contains("06.12.2026"), zeile);
        assertTrue(zeile.contains("29 Tage"), zeile);
        assertTrue(zeile.contains("500 Mark"), zeile);
    }

    @Test
    @DisplayName("Ohne Laden kommt der Hinweis, wie man mietet")
    void ohneLadenKommtDerHinweis() {
        // Eine Zeile und nicht nichts: Ohne sie waere /laden mieten nur in der Hilfe zu
        // finden.
        String zeile = Sprachprobe.bild(Miettext.standZeile(sprache, null, 0L));

        assertTrue(zeile.contains("/laden mieten"), zeile);
        assertFalse(zeile.isBlank());
    }

    @Test
    @DisplayName("Die Resttage kommen von AUSSEN - hier wird keine Zone gerechnet")
    void resttageKommenVonAussen() {
        // Welcher Tag heute ist, haengt an der eingestellten Zone. Eine Fassung, die hier
        // rechnete, braeuchte eine zweite Uhr - und die waere an einer Tagesgrenze ein
        // anderer Tag als die des Mietbuchs.
        assertTrue(Sprachprobe.bild(Miettext.standZeile(sprache, HALLE_1, 7L))
                .contains("7 Tage"));
    }

    @Test
    @DisplayName("Die Zeile zur falschen Welt kommt AUS DER NAHT")
    void falscheWeltKommtAusDerNaht() {
        // Sonst waere sie die einzige Zeile dieses Bereichs, die eine Probe nicht ohne
        // Server holen kann - und genau solche Zeilen bleiben beim Umbenennen stehen.
        List<String> zeilen = bild(Miettext.falscheWeltZeilen(sprache));

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Altfurt"), zeilen.get(0));
    }

    @Test
    @DisplayName("Jede Zeile ist GENAU EINE Zeile - ausser den Absagen mit Hinweis")
    void jedeZeileIstEine() {
        // Eine Umbruchmarke in einer ZUSAGE verschoebe das Bild stumm - dort sichert
        // sprache.eineZeile zu, dass genau eine Zeile hinausgeht. Die Absagen tragen
        // ihren Hinweis absichtlich und gehen darum ueber sprache.bau; sie stehen hier
        // nicht drin, sondern in absagenDuerfenEinenHinweisTragen.
        List<Component> zusagen = new ArrayList<>();
        zusagen.addAll(Miettext.mietenZeilen(sprache, gemietet(55_000L, true),
                "halle-1"));
        zusagen.addAll(Miettext.kuendigenZeilen(sprache, new Mietbuch.Kuendigung(
                Mietbuch.Bescheid.GEKUENDIGT, "halle-1", 5_000L)));
        zusagen.addAll(Miettext.verlaengernZeilen(sprache, new Mietbuch.Verlaengerung(
                Mietbuch.Bescheid.VERLAENGERT, HALLE_1, 30L), 30));
        zusagen.add(Miettext.standZeile(sprache, HALLE_1, 29L));

        for (Component zeile : zusagen) {
            String text = Sprachprobe.bild(zeile);
            assertEquals(1, text.split(System.lineSeparator()).length, text);
        }
        assertEquals(7, zusagen.size(), "alle Zusagen geprueft, nicht nur ein paar");
    }

    @Test
    @DisplayName("Die Listen sind unveraenderlich - der Sendeort kann nichts anhaengen")
    void listenSindFest() {
        List<Component> ausMieten = Miettext.mietenZeilen(sprache,
                gemietet(55_000L, true), "halle-1");
        List<Component> ausKuendigen = Miettext.kuendigenZeilen(sprache,
                new Mietbuch.Kuendigung(Mietbuch.Bescheid.GEKUENDIGT, "halle-1",
                        5_000L));

        assertThrows(UnsupportedOperationException.class,
                () -> ausMieten.add(Component.empty()));
        assertThrows(UnsupportedOperationException.class,
                () -> ausKuendigen.add(Component.empty()));
    }
}
