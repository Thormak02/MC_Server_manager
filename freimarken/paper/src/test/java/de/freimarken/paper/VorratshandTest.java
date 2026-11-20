package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Platzrechnung - gerechnet, nicht versucht.
 *
 * <p><b>Warum es diese Probe erst seit dem 20.11.2026 gibt</b>: {@code Vorratshand}
 * braucht einen Spieler, und {@code Material.getMaxStackSize} wirft ohne laufenden
 * Server. Die Rechenschleife stand darum in der anonymen Klasse eingeschlossen, und
 * keine der 4453 Proben konnte sie durchlaufen. In der Pruefrunde zu Teil G hat sie
 * genau dort einen Fehler gehabt, der echte Goldmark kostet: Ein im Amboss umbenannter
 * Stapel galt als Aufstockziel, Bukkits {@code addItem} legt dort aber nichts hinein.
 * Die Rechnung meldete "passt", der Kauf buchte ab, die Lieferung kam nicht an - und
 * dann standen zwei Zeilen im Goldbuch fuer einen Kauf, der nie stattgefunden hat.
 *
 * <p>Jetzt kommt die Stapelgroesse als ZAHL herein, und damit rechnet eine Probe sie
 * nach. Was aus Bukkit hereingeholt wird (drei Zahlen je Platz), haelt die
 * Quelltextzusage {@code GrenzeTest 31cx} - mehr ist ohne Server nicht zu haben.
 *
 * <p>Alle Proben rechnen mit {@code Material.STONE} und {@code Material.DIRT} als blossen
 * Aufzaehlungswerten. Keine davon ruft eine Methode, die in Minecrafts Verzeichnis
 * greift.
 */
@DisplayName("Die Platzrechnung des Rucksacks")
class VorratshandTest {

    private static final int STAPEL = 64;

    /** Ein Rucksack mit {@code plaetze} leeren Lagerplaetzen. */
    private static Rucksack leer(int plaetze) {
        return new Rucksack(new Material[plaetze], new int[plaetze],
                new boolean[plaetze]);
    }

    /**
     * Die drei Zahlen je Platz in einem Stueck - genau die, die {@code fehlenderPlatz}
     * aus Bukkit holt.
     */
    private record Rucksack(Material[] arten, int[] mengen, boolean[] aufstockbar) {

        /** Legt einen Stapel OHNE eigene Gestalt auf einen Platz. */
        Rucksack mit(int platz, Material art, int menge) {
            arten[platz] = art;
            mengen[platz] = menge;
            aufstockbar[platz] = true;
            return this;
        }

        /** Legt einen umbenannten, verzauberten oder beschriebenen Stapel auf einen Platz. */
        Rucksack mitGestalt(int platz, Material art, int menge) {
            arten[platz] = art;
            mengen[platz] = menge;
            aufstockbar[platz] = false;
            return this;
        }

        int fehlt(Material art, int stueck) {
            return Vorratshand.nichtUnterzubringen(arten, mengen, aufstockbar, art,
                    stueck, art == null ? 0 : STAPEL);
        }
    }

    @Test
    @DisplayName("Ein umbenannter Stapel ist KEIN Aufstockziel")
    void umbenannterStapelNimmtNichts() {
        // DER BEFUND. Anna hat alle 36 Lagerplaetze belegt; auf dem letzten liegt EIN im
        // Amboss umbenannter Kuerbis. Sie kauft den Kuerbishut (1 Stueck). Die Rechnung
        // sah: Art stimmt, 1 < 64, also passt es - und meldete 0 fehlenden Platz.
        // addItem findet den benannten Stapel nicht (isSimilar vergleicht die
        // Komponenten) und keinen freien Platz: nichts kommt an.
        Rucksack voll = leer(36);
        for (int platz = 0; platz < 35; platz++) {
            voll.mit(platz, Material.STONE, STAPEL);
        }
        voll.mitGestalt(35, Material.CARVED_PUMPKIN, 1);

        assertEquals(1, voll.fehlt(Material.CARVED_PUMPKIN, 1),
                "der umbenannte Stapel nimmt nichts an, und frei ist nichts");
    }

    @Test
    @DisplayName("Derselbe Stapel OHNE eigene Gestalt nimmt es an")
    void blankerStapelNimmtAn() {
        // Die Gegenrichtung, und sie ist Pflicht: Rechnete die Methode jeden
        // angefangenen Stapel als voll, waere die Probe darueber genauso gruen - und ein
        // fast voller Rucksack sagte ab, obwohl die Lieferung in seine Luecken passt.
        Rucksack voll = leer(36);
        for (int platz = 0; platz < 35; platz++) {
            voll.mit(platz, Material.STONE, STAPEL);
        }
        voll.mit(35, Material.CARVED_PUMPKIN, 1);

        assertEquals(0, voll.fehlt(Material.CARVED_PUMPKIN, 1));
    }

    @Test
    @DisplayName("Ein Platz mit eigener Gestalt ist trotzdem BELEGT")
    void gestaltIstKeinFreierPlatz() {
        // Die zweite Haelfte derselben Frage: Nicht aufstockbar heisst nicht leer. Waere
        // er als frei gezaehlt, legte die Rechnung die Lieferung auf einen Platz, auf dem
        // schon etwas liegt.
        Rucksack einer = leer(1).mitGestalt(0, Material.STONE, 1);

        assertEquals(1, einer.fehlt(Material.DIRT, 1));
    }

    @Test
    @DisplayName("Angefangene Stapel werden zuerst gefuellt - die Reihenfolge von addItem")
    void erstAuffuellenDannBelegen() {
        Rucksack einer = leer(1).mit(0, Material.STONE, 60);

        assertEquals(0, einer.fehlt(Material.STONE, 4), "vier passen in die Luecke");

        Rucksack zweiter = leer(1).mit(0, Material.STONE, 60);

        assertEquals(1, zweiter.fehlt(Material.STONE, 5), "das fuenfte nicht");
    }

    @Test
    @DisplayName("Ein Gegenstand, den dieser Server nicht kennt, zaehlt vollstaendig")
    void unbekanntZaehltGanz() {
        assertEquals(7, leer(36).fehlt(null, 7));
    }

    @Test
    @DisplayName("Eine Stapelgroesse von 0 - ein reines Blockmaterial - zaehlt vollstaendig")
    void keinGegenstandZaehltGanz() {
        // So kommt der zweite Befund hier an: Vorratshand.art gibt fuer "potatoes" jetzt
        // null, und die Stapelgroesse ist dann 0. Die Rechnung muss daraus "passt
        // nirgends hin" machen und nicht durch 0 teilen oder alles unterbringen.
        Rucksack frei = leer(36);

        assertEquals(7, Vorratshand.nichtUnterzubringen(frei.arten(), frei.mengen(),
                frei.aufstockbar(), Material.STONE, 7, 0));
    }

    @Test
    @DisplayName("Zwei Zeilen teilen sich die freien Plaetze - eine Frage, eine Kopie")
    void zweiZeilenTeilenDenPlatz() {
        // Das ist der Grund, warum fehlenderPlatz EINE Frage ueber die ganze Lieferung
        // ist und nicht eine je Zeile: Die Arrays sind die KOPIE, und die erste Zeile
        // belegt darin den Platz, den die zweite dann nicht mehr hat. Je Zeile einzeln
        // gefragt, sagten beide "passt" - und zusammen passten sie nicht.
        Rucksack einer = leer(1);

        assertEquals(0, einer.fehlt(Material.STONE, STAPEL), "der eine Platz ist voll");
        assertEquals(1, einer.fehlt(Material.DIRT, 1), "und fuer die zweite Zeile leer"
                + " ist er nicht mehr");
    }

    @Test
    @DisplayName("Mehr als ein Stapel verteilt sich auf mehrere Plaetze")
    void mehrereStapelMehrerePlaetze() {
        assertEquals(0, leer(3).fehlt(Material.STONE, 3 * STAPEL));
        assertEquals(1, leer(3).fehlt(Material.STONE, 3 * STAPEL + 1));
    }

    @Test
    @DisplayName("Null oder weniger Stueck fehlen nie")
    void nichtsFehltNie() {
        assertEquals(0, leer(0).fehlt(Material.STONE, 0));
        assertEquals(0, leer(0).fehlt(Material.STONE, -5));
    }

    @Test
    @DisplayName("Ein Blockmaterial nimmt der naechsten Zeile NICHT den Platz weg")
    void blockmaterialNimmtKeinenPlatz() {
        // Der Riegel "stapel < 1" tut mehr, als die Zahl zu liefern: Ohne ihn belegt die
        // Schleife jeden freien Platz der KOPIE mit 0 Stueck - und die naechste Zeile
        // derselben Lieferung findet dann keinen freien Platz mehr. Die Antwort auf die
        // erste Zeile ist in beiden Faellen 7; die auf die zweite ist es nicht.
        Rucksack zwei = leer(2);

        assertEquals(7, Vorratshand.nichtUnterzubringen(zwei.arten(), zwei.mengen(),
                        zwei.aufstockbar(), Material.STONE, 7, 0),
                "ein Blockmaterial kommt nie an");
        assertEquals(0, zwei.fehlt(Material.DIRT, 2 * STAPEL),
                "und beide Plaetze sind noch frei");
    }
}
