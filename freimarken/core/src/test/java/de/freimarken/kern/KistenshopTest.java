package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
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

    private static Warenprobe stein() {
        return new Warenprobe("Stein", "inhalt-von-stein");
    }

    private static Kistenshop shop(long kauf, long verkauf) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, kauf, verkauf,
                stein());
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
                    71 + richtung[1], 6 + richtung[2], ANNA, 16, 100L, 80L, stein());
            assertEquals(60 + richtung[0], nachbar.kisteX(),
                    "Richtung " + richtung[0] + "/" + richtung[1] + "/" + richtung[2]);
        }

        // Diagonal nicht - das ist genau der Fall, den Lumia ausschliesst.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 61, 72, 6, ANNA, 16, 100L, 80L,
                        stein()));
        // Zwei Bloecke weit auch nicht.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 62, 71, 6, ANNA, 16, 100L, 80L,
                        stein()));
        // Und DIESELBE Stelle ist kein Nachbar: Ein Schild kann nicht in seiner eigenen
        // Kiste stehen, und die Abstandssumme 0 faellt mit derselben Pruefung auf.
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 60, 71, 6, ANNA, 16, 100L, 80L,
                        stein()));
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
                stein());

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
                        stein()));
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop("  ", 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                        stein()));
        assertThrows(IllegalArgumentException.class,
                () -> new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                        null));
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

        assertTrue(shop.offenFuer(ANNA, false, false));
        assertFalse(shop.offenFuer(BERT, false, false));
    }

    @Test
    @DisplayName("Ein Bundshop haengt am Bundrecht und NICHT am Besitzerfeld")
    void bundshopHaengtAmRecht() {
        // DIE TRAGENDE UNTERSCHEIDUNG: Ein Bundshop hat besitzer == null, und
        // gehoert() gibt dort fuer JEDEN false. Wer den Zutritt am Besitz allein
        // prueft, schliesst jeden Bundshop fuer alle zu - auch fuer das Team, das
        // ihn nachfuellen und abbauen muss.
        Kistenshop bundshop = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16,
                100L, 80L, stein());

        assertFalse(bundshop.gehoert(ANNA), "ein Bundshop gehoert NIEMANDEM");
        assertFalse(bundshop.offenFuer(ANNA, false, false), "ohne Bundrecht zu");
        assertTrue(bundshop.offenFuer(ANNA, false, true), "mit Bundrecht offen");
    }

    @Test
    @DisplayName("Das Bundrecht oeffnet NUR Bundshops, nie den Shop eines Spielers")
    void bundrechtOeffnetKeinenSpielershop() {
        // Sonst waere freimarken.shop.bund ein Generalschluessel: Wer Preise des
        // Servers festlegen darf, duerfte damit auch in jede Spielerkiste greifen.
        // Das sind zwei Vollmachten, und die eine ist nicht die andere.
        assertFalse(shop(100L, 80L).offenFuer(BERT, false, true));
    }

    @Test
    @DisplayName("Das Teamrecht oeffnet beides - sonst bleibt ein verlassener Shop"
            + " fuer immer stehen")
    void teamrechtOeffnetAlles() {
        Kistenshop bundshop = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16,
                100L, 80L, stein());

        assertTrue(shop(100L, 80L).offenFuer(BERT, true, false));
        assertTrue(bundshop.offenFuer(BERT, true, false));
    }

    @Test
    @DisplayName("ALLE Shops an einer Kiste muessen offen sein, nicht einer")
    void alleMuessenOffenSein() {
        // An einer DOPPELKISTE koennen zwei Schilder zweier Menschen haengen, und
        // die beiden Haelften teilen EIN Inventar. Wer seine eigene Haelfte oeffnet,
        // kommt an die Ware des anderen - darum genuegt ein eigener Shop nicht.
        Kistenshop meiner = shop(100L, 80L);
        Kistenshop fremder = new Kistenshop(WELT, 60, 71, 7, 61, 71, 7, BERT, 16,
                100L, 80L, stein());

        assertNull(Kistenshop.ersterFremder(List.of(meiner), ANNA, false, false));
        assertEquals(fremder,
                Kistenshop.ersterFremder(List.of(meiner, fremder), ANNA, false,
                        false),
                "gemeldet wird der Shop, dem der Spieler in die Quere kommt - und"
                        + " nicht der eigene, an dem er stehen darf");
    }

    @Test
    @DisplayName("Eine leere Liste ist OFFEN - die Unterscheidung trifft der"
            + " Aufrufer")
    void leereListeIstOffen() {
        // "Hier ist gar keine Shopkiste" und "hier ist eine fremde" sind zwei
        // Lagen, und nur der Aufrufer kann sie trennen: Er allein weiss, ob er
        // ueberhaupt an einer Kiste steht. Gaebe diese Methode bei einer leeren
        // Liste eine Absage, waere jeder Blick in den eigenen Rucksack verboten.
        assertNull(Kistenshop.ersterFremder(List.of(), ANNA, false, false));
    }

    private static Kistenshop mitMenge(int menge) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, menge, 100L, 80L,
                stein());
    }
}
