package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Kistenshop und seine Warenprobe - die Schranken, die aus fremden Fehlern kommen.
 *
 * <p>Fast jede Probe hier ist die Antwort auf einen belegten Fehler der Vorbild-Plugins
 * (Plan 7.4). Die Begruendung steht je Probe daneben, nicht gesammelt: Wer eine einzelne
 * Schranke eines Tages loesen will, soll an ihr lesen, was sie verhindert.
 */
@DisplayName("Kistenshop und Warenprobe")
class KistenshopTest {

    private static final String WELT = "freimarken:altfurt";

    private static final UUID ANNA =
            UUID.fromString("0000a44a-0000-0000-0000-000000000001");

    private static final UUID BERT =
            UUID.fromString("0000b55b-0000-0000-0000-000000000002");

    /** Ein dritter Mensch - fuer die Frage, ob eine Freigabe NUR dem Genannten gilt. */
    private static final UUID CORA =
            UUID.fromString("0000c66c-0000-0000-0000-000000000003");

    private static Warenprobe stein() {
        return new Warenprobe("Stein", "inhalt-von-stein");
    }

    private static Kistenshop shop(long kauf, long verkauf) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, kauf, verkauf,
                stein(), Set.of());
    }

    // ==================================================================== Preise

    @Test
    @DisplayName("Der Verkaufspreis darf NIE ueber dem Kaufpreis liegen")
    void keineGelddruckmaschine() {
        // Die wichtigste Schranke dieses Teils: Waere S ueber B, kauft man im selben Shop
        // billig und verkauft teuer zurueck. ChestShop fuehrt dieselbe Sperre und
        // begruendet sie in seiner eigenen Config - "This prevents newbies from creating
        // shops that would be exploited".
        IllegalArgumentException fehler = assertThrows(IllegalArgumentException.class,
                () -> shop(100L, 101L));

        assertTrue(fehler.getMessage().contains("Gelddruckmaschine"),
                fehler.getMessage());
        // GLEICH ist erlaubt: Ein Shop, der zum selben Preis kauft und verkauft, ist ein
        // Umtausch und keine Quelle. Ohne Gebuehr bringt er niemandem etwas, aber er
        // erzeugt auch nichts.
        assertEquals(100L, shop(100L, 100L).verkaufpreis());
    }

    @Test
    @DisplayName("Ein ANKAUFSTAND ohne Kaufpreis darf jeden Verkaufspreis haben")
    void ankaufstandIstKeinRundlauf() {
        // Kaufpreis 0 heisst: Hier wird nichts verkauft. Dann gibt es keinen Rundlauf,
        // und die Schranke darf nicht greifen - sonst waere ein reiner Ankaufstand auf
        // 0 Mark begrenzt.
        Kistenshop ankauf = shop(0L, 5_000L);

        assertFalse(ankauf.verkauftWare());
        assertTrue(ankauf.kauftWare());
        assertEquals(5_000L, ankauf.verkaufpreis());
    }

    @Test
    @DisplayName("Ein Shop ohne BEIDE Preise handelt nichts und wird abgewiesen")
    void ohnePreisKeinShop() {
        // Sonst steht ein Schild an einer Kiste, bei dem einem Kunden nichts geschieht
        // und er nichts erfaehrt.
        assertThrows(IllegalArgumentException.class, () -> shop(0L, 0L));
    }

    @Test
    @DisplayName("Ein negativer Preis und einer ueber der Grenze werden abgewiesen")
    void preisHatGrenzen() {
        assertThrows(IllegalArgumentException.class, () -> shop(-1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> shop(100L, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> shop(Kistenshop.HOECHSTER_PREIS + 1L, 0L));
        // Der Rand geht durch.
        assertEquals(Kistenshop.HOECHSTER_PREIS,
                shop(Kistenshop.HOECHSTER_PREIS, 0L).kaufpreis());
    }

    // ==================================================================== Menge

    @Test
    @DisplayName("Die Menge reicht bis zur vollen Doppelkiste, nicht weiter")
    void mengeHatGrenzen() {
        // 3456 ist eine volle Doppelkiste (54 mal 64) - dieselbe Rechnung wie ChestShops
        // MAX_SHOP_AMOUNT. Mehr kann kein Handel bewegen, weil mehr nicht in die Kiste
        // passt.
        assertEquals(3456, Kistenshop.GROESSTE_MENGE);
        assertEquals(3456, mitMenge(3456).menge());

        assertThrows(IllegalArgumentException.class, () -> mitMenge(3457));
        assertThrows(IllegalArgumentException.class, () -> mitMenge(0));
        assertThrows(IllegalArgumentException.class, () -> mitMenge(-1));
    }

    // ==================================================================== Nachbarschaft

    @Test
    @DisplayName("Die Kiste muss NEBEN dem Schild liegen - nicht diagonal")
    void kisteLiegtNeben() {
        // Lumia erlaubte an, ueber, unter und neben der Kiste und ausdruecklich nicht
        // diagonal. Geprueft wird der Abstand und nicht die Richtung: So sind alle sechs
        // Seiten erlaubt, ohne sie einzeln aufzuzaehlen.
        for (int[] richtung : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}}) {
            Kistenshop nachbar = new Kistenshop(WELT, 60, 71, 6, 60 + richtung[0],
                    71 + richtung[1], 6 + richtung[2], ANNA, 16, 100L, 80L, stein(), Set.of());
            assertEquals(60 + richtung[0], nachbar.kisteX(),
                    "Richtung " + richtung[0] + "/" + richtung[1] + "/" + richtung[2]);
        }

        // Diagonal nicht - das ist genau der Fall, den Lumia ausschliesst.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 61, 72, 6, ANNA, 16, 100L, 80L,
                        stein(), Set.of()));
        // Zwei Bloecke weit auch nicht.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 62, 71, 6, ANNA, 16, 100L, 80L,
                        stein(), Set.of()));
        // Und DIESELBE Stelle ist kein Nachbar: Ein Schild kann nicht in seiner eigenen
        // Kiste stehen, und die Abstandssumme 0 faellt mit derselben Pruefung auf.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 60, 71, 6, ANNA, 16, 100L, 80L,
                        stein(), Set.of()));
    }

    @Test
    @DisplayName("istNachbar trifft die sechs Seiten und nichts sonst")
    void istNachbarIstGenau() {
        assertTrue(Kistenshop.istNachbar(0, 0, 0, 1, 0, 0));
        assertTrue(Kistenshop.istNachbar(0, 0, 0, 0, 0, -1));
        assertFalse(Kistenshop.istNachbar(0, 0, 0, 0, 0, 0), "dieselbe Stelle");
        assertFalse(Kistenshop.istNachbar(0, 0, 0, 1, 1, 0), "diagonal");
        assertFalse(Kistenshop.istNachbar(0, 0, 0, 2, 0, 0), "zwei Bloecke");
        assertFalse(Kistenshop.istNachbar(0, 0, 0, 1, 1, 1), "dreifach diagonal");
    }

    // ==================================================================== Besitz

    @Test
    @DisplayName("Ein Bundshop gehoert niemandem - auch nicht dem, der danach fragt")
    void bundshopGehoertNiemandem() {
        Kistenshop bund = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 80L,
                stein(), Set.of());

        assertTrue(bund.demBund());
        assertFalse(bund.gehoert(ANNA));
        assertFalse(bund.gehoert(UUID.randomUUID()));
        // Und umgekehrt: Ein Spielershop gehoert nicht dem Bund.
        assertFalse(shop(100L, 80L).demBund());
        assertTrue(shop(100L, 80L).gehoert(ANNA));
    }

    @Test
    @DisplayName("Der Schluessel ist die SCHILDSTELLE samt Welt")
    void schluesselIstDieSchildstelle() {
        Kistenshop einer = shop(100L, 80L);

        assertEquals(einer.schluessel(),
                Kistenshop.schluesselVon(WELT, 60, 71, 6));
        // Eine andere Welt ist eine andere Stelle: Zwei Server-Welten koennen dieselben
        // Koordinaten haben.
        assertFalse(einer.schluessel()
                .equals(Kistenshop.schluesselVon("freimarken:marktstadt", 60, 71, 6)));
        assertThrows(IllegalArgumentException.class,
                () -> Kistenshop.schluesselVon(null, 0, 0, 0));
    }

    @Test
    @DisplayName("Ohne Welt und ohne Ware gibt es keinen Shop")
    void pflichtwerte() {
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(null, 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                        stein(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop("  ", 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                        stein(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                        null, Set.of()));
    }

    // ==================================================================== Warenprobe

    @Test
    @DisplayName("Der Warenname passt auf eine SCHILDZEILE - und wird nicht gekuerzt")
    void warennamePasstAufsSchild() {
        // 15 Zeichen ist die Breite einer Minecraft-Schildzeile. Hier wird NICHT
        // gekuerzt, sondern abgewiesen: Ein stillschweigend gekuerzter Name waere zwei
        // Namen fuer dieselbe Ware - einer in der Zeile, einer auf dem Schild.
        String grenze = "x".repeat(Warenprobe.LAENGSTER_NAME);
        assertEquals(grenze, new Warenprobe(grenze, "inhalt").name());

        IllegalArgumentException fehler = assertThrows(IllegalArgumentException.class,
                () -> new Warenprobe(grenze + "x", "inhalt"));
        assertTrue(fehler.getMessage().contains("zwei Namen"), fehler.getMessage());
    }

    @Test
    @DisplayName("Eine Warenprobe ohne Inhalt ist keine - der Name genuegt nicht")
    void warenprobeBrauchtInhalt() {
        // Der Grund, warum die Ware nicht nur ein Name ist: Zwei Spitzhacken heissen
        // gleich und sind verschieden, sobald eine verzaubert ist. Genau das soll
        // handelbar sein (entschieden 08.11.2026), also muss der Inhalt dabei sein.
        assertThrows(IllegalArgumentException.class,
                () -> new Warenprobe("Spitzhacke", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Warenprobe("Spitzhacke", ""));
        assertThrows(IllegalArgumentException.class, () -> new Warenprobe(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Warenprobe("  ", "x"));
    }

    @Test
    @DisplayName("Der Name wird getrimmt, der INHALT nicht")
    void trimmen() {
        Warenprobe probe = new Warenprobe("  Stein  ", "  AAAA  ");

        assertEquals("Stein", probe.name());
        // Wie bei Abholgut: Was die Schicht darueber geschrieben hat, kommt Zeichen fuer
        // Zeichen zurueck. Ein Trimmen waere eine stille Aenderung an Daten, die der
        // Kern nicht versteht - und dann findet der Vergleich mit dem Kisteninhalt die
        // Ware nicht mehr.
        assertEquals("  AAAA  ", probe.inhalt());
    }

    @Test
    @DisplayName("Die Inhaltsgrenze ist dieselbe wie beim Abholfach")
    void inhaltsgrenzeIstGeteilt() {
        // Derselbe Weg, dieselbe Spalte: Beide Texte entstehen aus demselben Stapel und
        // landen in einer MEDIUMTEXT-Spalte. Zwei verschiedene Grenzen waeren eine Ware,
        // die ins Abholfach passt und in keinen Shop.
        assertEquals(Abholgut.LAENGSTER_INHALT, Warenprobe.LAENGSTER_INHALT);

        String grenze = "A".repeat(Warenprobe.LAENGSTER_INHALT);
        assertEquals(grenze, new Warenprobe("Kiste", grenze).inhalt());
        assertThrows(IllegalArgumentException.class,
                () -> new Warenprobe("Kiste", grenze + "A"));
    }

    // ================================================================ Zutritt

    @Test
    @DisplayName("Der Besitzer darf an seinen Shop, ein anderer nicht")
    void besitzerDarf() {
        Kistenshop shop = shop(100L, 80L);

        for (Shopweg weg : Shopweg.values()) {
            assertTrue(shop.offenFuer(ANNA, weg, false, false),
                    "der Besitzer darf auf JEDEM Weg: " + weg);
            assertFalse(shop.offenFuer(BERT, weg, false, false),
                    "ein Fremder auf keinem: " + weg);
        }
    }

    @Test
    @DisplayName("Ein Bundshop haengt am Bundrecht und NICHT am Besitzerfeld")
    void bundshopHaengtAmRecht() {
        // DIE TRAGENDE UNTERSCHEIDUNG: Ein Bundshop hat besitzer == null, und
        // gehoert() gibt dort fuer JEDEN false. Wer den Zutritt am Besitz allein
        // prueft, schliesst jeden Bundshop fuer alle zu - auch fuer das Team, das
        // ihn nachfuellen und abbauen muss.
        Kistenshop bundshop = bundshop();

        assertFalse(bundshop.gehoert(ANNA), "ein Bundshop gehoert NIEMANDEM");
        assertFalse(bundshop.offenFuer(ANNA, Shopweg.OEFFNEN, false, false),
                "ohne Bundrecht zu");
        assertTrue(bundshop.offenFuer(ANNA, Shopweg.OEFFNEN, false, true),
                "mit Bundrecht offen");
    }

    @Test
    @DisplayName("Das Bundrecht oeffnet NUR Bundshops, nie den Shop eines Spielers")
    void bundrechtOeffnetKeinenSpielershop() {
        // Sonst waere freimarken.shop.bund ein Generalschluessel: Wer Preise des
        // Servers festlegen darf, duerfte damit auch in jede Spielerkiste greifen.
        // Das sind zwei Vollmachten, und die eine ist nicht die andere.
        assertFalse(shop(100L, 80L).offenFuer(BERT, Shopweg.OEFFNEN, false, true));
    }

    @Test
    @DisplayName("Das Teamrecht oeffnet beides - sonst bleibt ein verlassener Shop"
            + " fuer immer stehen")
    void teamrechtOeffnetAlles() {
        // Auf JEDEM Weg, auch dem dritten: Wer ueberall bauen darf, raeumt auch den
        // Shop eines Spielers weg, der nicht mehr spielt. Die Freigabe-Ausnahme gilt
        // fuer ihn nicht - er kommt gar nicht erst bis dorthin.
        for (Shopweg weg : Shopweg.values()) {
            assertTrue(shop(100L, 80L).offenFuer(BERT, weg, true, false), "" + weg);
            assertTrue(bundshop().offenFuer(BERT, weg, true, false), "" + weg);
        }
    }

    @Test
    @DisplayName("NACH_STELLE ordnet Welt, dann x, dann y, dann z - jede Achse")
    void nachStelleOrdnetJedeAchse() {
        // BEFUND DER EINSPRITZUNG L12 vom 10.11.2026: x und z liessen sich im
        // Comparator vertauschen, ohne dass eine Probe rot wurde. Die Vertragsprobe der
        // Ablagen aendert nur x - alle ihre Shops liegen bei z=6, und dann entscheidet
        // x auch nach der Vertauschung.
        //
        // Darum Stellen, bei denen JEDE Paarvertauschung die Reihenfolge aendert:
        //   A 1/2/3, B 2/3/1, C 3/1/2 - verschieden in allen drei Achsen, also faellt
        //     eine Vertauschung von x mit y oder z auf;
        //   D 5/1/9 und E 5/9/1 - GLEICHES x, also entscheidet y vor z, und eine
        //     Vertauschung dieser beiden faellt auf. Ohne dieses Paar waere sie blind,
        //     denn bei lauter verschiedenen x entscheidet x allein.
        // Dazu ein Laden in einer ANDEREN Welt, die alphabetisch vor allen steht: Die
        // Welt ist die erste Stufe, und eine Ordnung, die sie uebergeht, mischt zwei
        // Staedte ineinander.
        List<Kistenshop> gemischt = new ArrayList<>(List.of(
                stelle("freimarken:hochwald", 5, 9, 1, "E"),
                stelle("freimarken:hochwald", 3, 1, 2, "C"),
                stelle("freimarken:hochwald", 1, 2, 3, "A"),
                stelle("freimarken:altfurt", 9, 9, 9, "ZUERST"),
                stelle("freimarken:hochwald", 5, 1, 9, "D"),
                stelle("freimarken:hochwald", 2, 3, 1, "B")));

        gemischt.sort(Kistenshop.NACH_STELLE);

        List<String> namen = new ArrayList<>();
        for (Kistenshop shop : gemischt) {
            namen.add(shop.ware().name());
        }
        assertEquals(List.of("ZUERST", "A", "B", "C", "D", "E"), namen,
                "altfurt vor hochwald, dann 1/2/3 vor 2/3/1 vor 3/1/2, und 5/1/9 vor"
                        + " 5/9/1 - bei gleichem x entscheidet y VOR z");
    }

    /** Ein Shop an genau dieser Stelle, mit der Ware als Merkzeichen. */
    private static Kistenshop stelle(String welt, int x, int y, int z, String ware) {
        return new Kistenshop(welt, x, y, z, x, y - 1, z, ANNA, 16, 100L, 80L,
                new Warenprobe(ware, "inhalt-von-" + ware), Set.of());
    }

    @Test
    @DisplayName("Ein FREIGEGEBENER darf oeffnen und abbauen")
    void freigegebenerDarfOeffnenUndAbbauen() {
        // Das Oeffnen ist der ganze Zweck (Plan 7.4: "dass ein Fremder einem Shop auch
        // nichts schenken kann"), das Abbauen kam dazu, weil Preise zu aendern heute nur
        // ueber Schild abbauen und neu beschriften geht.
        Kistenshop shop = shop(100L, 80L).mitFreigaben(Set.of(BERT));

        assertTrue(shop.offenFuer(BERT, Shopweg.OEFFNEN, false, false));
        assertTrue(shop.offenFuer(BERT, Shopweg.ABBAUEN, false, false));
    }

    @Test
    @DisplayName("Ein Freigegebener haengt KEIN zweites Schild an - das Loch bleibt zu")
    void freigegebenerHaengtKeinZweitesSchildAn() {
        // DIE WICHTIGSTE PROBE DER FREIGABE. Der dritte Weg ist das Loch vom
        // 08.11.2026: Wer an einer fremden Kiste ein eigenes Schild anhaengen darf,
        // verkauft fremde Ware auf SEIN Konto - und der Besitzer sieht nur, dass seine
        // Kiste leerer wird. Waere er fuer Freigegebene offen, stuende das Loch fuer
        // genau die Leute wieder auf, denen man gerade Vertrauen geschenkt hat.
        Kistenshop shop = shop(100L, 80L).mitFreigaben(Set.of(BERT));

        assertFalse(shop.offenFuer(BERT, Shopweg.ZWEITES_SCHILD, false, false),
                "derselbe Spieler, derselbe Shop - NUR der Weg ist anders, und er"
                        + " entscheidet");
    }

    @Test
    @DisplayName("Eine Freigabe gilt nur dem Genannten, nicht jedem")
    void freigabeGiltNurDemGenannten() {
        Kistenshop shop = shop(100L, 80L).mitFreigaben(Set.of(BERT));

        assertTrue(shop.offenFuer(BERT, Shopweg.OEFFNEN, false, false));
        assertFalse(shop.offenFuer(CORA, Shopweg.OEFFNEN, false, false),
                "wer nicht eingetragen ist, bleibt draussen");
    }

    @Test
    @DisplayName("Ein neuer Shop hat KEINE Freigaben")
    void neuerShopHatKeineFreigaben() {
        // Die sichere Richtung: Niemand ausser dem Besitzer darf an die Kiste, bis er
        // jemanden ausdruecklich eintraegt.
        Kistenshop shop = shop(100L, 80L);

        assertTrue(shop.freigegeben().isEmpty());
        assertFalse(shop.istFreigegeben(BERT));
    }

    @Test
    @DisplayName("Ein BUNDSHOP kann keine Freigaben haben")
    void bundshopHatKeineFreigaben() {
        // Er gehoert niemandem, also kann ihn auch niemand freigeben. Wer an einen
        // Bundshop darf, entscheidet allein das Recht freimarken.shop.bund - eine
        // Freigabe daneben waere ein zweiter Weg zu derselben Frage.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 80L,
                        stein(), Set.of(BERT)));
    }

    @Test
    @DisplayName("Die Freigabeliste ist unveraenderlich - auch nachtraeglich")
    void freigabelisteIstUnveraenderlich() {
        // Diese Menge wird in offenFuer bei jedem Trichtertakt gefragt. Gaebe der
        // Aufrufer eine Menge herein, die er danach weiter aendert, entschiede ueber den
        // Zutritt zu einer fremden Kiste ein Wert, den niemand mehr ueberblickt.
        Set<UUID> veraenderlich = new java.util.HashSet<>();
        veraenderlich.add(BERT);
        Kistenshop shop = shop(100L, 80L).mitFreigaben(veraenderlich);

        veraenderlich.add(CORA);

        assertFalse(shop.istFreigegeben(CORA),
                "die nachtraegliche Aenderung darf den Shop nicht erreichen");
        assertThrows(UnsupportedOperationException.class,
                () -> shop.freigegeben().add(CORA));
    }

    @Test
    @DisplayName("mitFreigaben laesst alles andere unveraendert - auch den Besitzer")
    void mitFreigabenAendertNurDieListe() {
        // Freigeben aendert nie, wem der Laden gehoert. Eine Fassung, die den Besitzer
        // mitnaehme, verschenkte den Shop statt den Schluessel.
        Kistenshop vorher = shop(100L, 80L);

        Kistenshop nachher = vorher.mitFreigaben(Set.of(BERT));

        assertEquals(vorher.besitzer(), nachher.besitzer());
        assertEquals(vorher.schluessel(), nachher.schluessel());
        assertEquals(vorher.menge(), nachher.menge());
        assertEquals(vorher.kaufpreis(), nachher.kaufpreis());
        assertEquals(vorher.verkaufpreis(), nachher.verkaufpreis());
        assertEquals(vorher.ware(), nachher.ware());
        assertNotEquals(vorher.freigegeben(), nachher.freigegeben(),
                "nur DAS unterscheidet die beiden");
    }

    @Test
    @DisplayName("Ohne Weg wird die Zutrittsfrage abgewiesen")
    void ohneWegWirdAbgewiesen() {
        // Ein null hiesse, dass der Aufrufer nicht weiss, wonach er fragt - und die
        // Freigabe traegt nicht jeden Weg. Stillschweigend den mildesten zu nehmen waere
        // die unsichere Richtung.
        assertThrows(IllegalArgumentException.class,
                () -> shop(100L, 80L).offenFuer(BERT, null, false, false));
    }

    @Test
    @DisplayName("ALLE Shops an einer Kiste muessen offen sein, nicht einer")
    void alleMuessenOffenSein() {
        // An einer DOPPELKISTE koennen zwei Schilder zweier Menschen haengen, und
        // die beiden Haelften teilen EIN Inventar. Wer seine eigene Haelfte oeffnet,
        // kommt an die Ware des anderen - darum genuegt ein eigener Shop nicht.
        Kistenshop meiner = shop(100L, 80L);
        Kistenshop fremder = new Kistenshop(WELT, 60, 71, 7, 61, 71, 7, BERT, 16,
                100L, 80L, stein(), Set.of());

        assertNull(Kistenshop.ersterFremder(List.of(meiner), ANNA, Shopweg.OEFFNEN,
                false, false));
        assertEquals(fremder,
                Kistenshop.ersterFremder(List.of(meiner, fremder), ANNA,
                        Shopweg.OEFFNEN, false, false),
                "gemeldet wird der Shop, dem der Spieler in die Quere kommt - und"
                        + " nicht der eigene, an dem er stehen darf");
    }

    @Test
    @DisplayName("An einer Doppelkiste zaehlt auch die Freigabe der ANDEREN Haelfte")
    void anDerDoppelkisteZaehltJedeHaelfte() {
        // Die Freigabe loest die Doppelkisten-Regel nicht auf: Wer nur an der einen
        // Haelfte freigegeben ist, kommt ueber das gemeinsame Inventar trotzdem an die
        // Ware der anderen - und darum bleibt er draussen.
        Kistenshop meiner = shop(100L, 80L).mitFreigaben(Set.of(CORA));
        Kistenshop fremder = new Kistenshop(WELT, 60, 71, 7, 61, 71, 7, BERT, 16,
                100L, 80L, stein(), Set.of());

        assertEquals(fremder,
                Kistenshop.ersterFremder(List.of(meiner, fremder), CORA,
                        Shopweg.OEFFNEN, false, false),
                "an der zweiten Haelfte ist CORA nicht freigegeben, also bleibt die"
                        + " ganze Kiste zu");
    }

    @Test
    @DisplayName("Eine leere Liste ist OFFEN - die Unterscheidung trifft der"
            + " Aufrufer")
    void leereListeIstOffen() {
        // "Hier ist gar keine Shopkiste" und "hier ist eine fremde" sind zwei
        // Lagen, und nur der Aufrufer kann sie trennen: Er allein weiss, ob er
        // ueberhaupt an einer Kiste steht. Gaebe diese Methode bei einer leeren
        // Liste eine Absage, waere jeder Blick in den eigenen Rucksack verboten.
        assertNull(Kistenshop.ersterFremder(List.of(), ANNA, Shopweg.OEFFNEN, false,
                false));
    }

    /** Ein Bundshop - gehoert niemandem, haengt am Recht. */
    private static Kistenshop bundshop() {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 80L,
                stein(), Set.of());
    }

    private static Kistenshop mitMenge(int menge) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, menge, 100L, 80L,
                stein(), Set.of());
    }
}
