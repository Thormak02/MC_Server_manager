package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Warenprobe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Kuerzung des Warennamens - der eine Teil von {@link Shopware}, der ohne Server
 * geht.
 *
 * <p>Alles andere dort haengt an Bukkit: ein {@code ItemStack}, ein Blockzustand, ein
 * Inventar. Diese Regel haengt an nichts - sie macht aus einem Materialnamen eine
 * Zeichenkette, die auf eine Schildzeile passt. Darum ist sie herausgezogen und steht
 * hier.
 *
 * <p><b>Was sie NICHT zusichert:</b> dass derselbe Name zweimal vorkommen kann. Das ist
 * ausdruecklich erlaubt und harmlos, aber nur, solange niemand den Namen zum VERGLEICHEN
 * benutzt - welche Ware ein Shop handelt, entscheidet {@code Warenprobe.inhalt}. Diese
 * Zusage haelt {@link ShopwacheTafel}.
 */
@DisplayName("Der Warenname auf dem Schild")
class ShopwareTest {

    @Test
    @DisplayName("Ein kurzer Name bleibt, wie er ist")
    void kurzerNameBleibt() {
        assertEquals("STONE", Shopware.kurzname("STONE", false));
        assertEquals("DIAMOND_PICKAXE", Shopware.kurzname("DIAMOND_PICKAXE", false),
                "fuenfzehn Zeichen passen genau - hier wird nicht gekuerzt");
    }

    @Test
    @DisplayName("Ein langer Name wird auf sein letztes Wortstueck gekuerzt")
    void langerNameWirdAufWortstueckGekuerzt() {
        // DIE ERSTE STUFE, und sie ist wichtiger, als sie aussieht: NETHERITE_PICKAXE hat
        // siebzehn Zeichen, und hart gekuerzt stuende "NETHERITE_PICKA" auf dem Schild.
        // "PICKAXE" ist fuer einen Menschen lesbar und fuer das Programm genauso genau,
        // naemlich gar nicht.
        assertEquals("PICKAXE", Shopware.kurzname("NETHERITE_PICKAXE", false));
        assertEquals("CHESTPLATE", Shopware.kurzname("DIAMOND_CHESTPLATE", false));
        assertTrue(Shopware.kurzname("NETHERITE_PICKAXE", false).length()
                <= Warenprobe.LAENGSTER_NAME);
    }

    @Test
    @DisplayName("Ist auch das letzte Wortstueck zu lang, wird hart gekuerzt")
    void zuLangesWortstueckWirdHartGekuerzt() {
        // Die zweite Stufe - die Rueckfallebene. Ein Name ohne Unterstrich und ueber der
        // Grenze gibt es in Minecraft heute nicht; die Stufe steht da, damit ein neues
        // Material nicht eines Tages eine Warenprobe wirft und einen Shop unanlegbar
        // macht.
        String lang = "A".repeat(40);
        assertEquals(Warenprobe.LAENGSTER_NAME, Shopware.kurzname(lang, false).length());
        assertEquals("A".repeat(Warenprobe.LAENGSTER_NAME),
                Shopware.kurzname(lang, false));
    }

    @Test
    @DisplayName("Jeder gekuerzte Name passt durch Warenprobe - und zwar JEDER")
    void jederNamePasstDurchWarenprobe() {
        // Die Zusage, auf die es ankommt: Warenprobe WIRFT bei einem zu langen Namen, und
        // ein Wurf hier liesse einen Shop nicht entstehen. Darum wird nicht ein Beispiel
        // geprueft, sondern jede Laenge von 1 bis 60, mit und ohne Unterstrich, mit und
        // ohne Zeichen - das sind die vier Wege durch die Methode, alle Laengen entlang.
        for (int laenge = 1; laenge <= 60; laenge++) {
            for (String roh : new String[] {"A".repeat(laenge),
                    "AB_" + "C".repeat(laenge), "A".repeat(laenge) + "_B"}) {
                for (boolean besonders : new boolean[] {false, true}) {
                    String name = Shopware.kurzname(roh, besonders);
                    assertTrue(name.length() <= Warenprobe.LAENGSTER_NAME,
                            roh + " (besonders=" + besonders + ") wurde \"" + name
                                    + "\", also " + name.length() + " Zeichen");
                    // Und er darf nicht LEER werden - Warenprobe weist auch das ab.
                    assertTrue(!name.isBlank(), roh + " wurde leer");
                    // Die eigentliche Zusage: Er geht durch.
                    new Warenprobe(name, "inhalt");
                }
            }
        }
    }

    @Test
    @DisplayName("Das Pluszeichen steht VORNE und kostet ein Zeichen Platz")
    void pluszeichenStehtVorne() {
        // Die halbe Antwort auf ChestShop #305 - "die Verzauberung steht am Ende gar nicht
        // darauf". Ein Kunde soll am Schild SEHEN, dass hier nicht die gewoehnliche Ware
        // liegt, und nicht erst beim Kauf.
        assertEquals("+STONE", Shopware.kurzname("STONE", true));
        assertTrue(Shopware.kurzname("STONE", true).startsWith(Shopware.BESONDERS));

        // Und es kostet Platz: DIAMOND_PICKAXE passt mit fuenfzehn Zeichen genau, mit dem
        // Zeichen davor nicht mehr. Waere das nicht so gerechnet, wuerfe die Warenprobe
        // genau bei der Ware, fuer die der ganze Weg gebaut wurde - verzauberte
        // Ausruestung.
        assertEquals("+PICKAXE", Shopware.kurzname("DIAMOND_PICKAXE", true));
    }

    @Test
    @DisplayName("Besonders und gewoehnlich sind VERSCHIEDENE Namen")
    void besondersUndGewoehnlichSindVerschieden() {
        // Sonst stuende auf zwei Schildern dasselbe, und ein Kunde koennte eine
        // verzauberte Spitzhacke nicht von einer gewoehnlichen unterscheiden, ohne zu
        // klicken. Der Unterschied zwischen A und B ist hier NUR die eine gepruefte
        // Sache - derselbe Materialname, nur das Merkmal wechselt.
        assertNotEquals(Shopware.kurzname("STONE", false),
                Shopware.kurzname("STONE", true));
    }

    @Test
    @DisplayName("Ein Name, der nur aus einem Unterstrich endet, wird nicht leer")
    void nameMitEndendemUnterstrichWirdNichtLeer() {
        // Der Randfall der ersten Stufe: Steht der letzte Unterstrich am ENDE, waere das
        // Wortstueck dahinter leer - und eine leere Warenprobe wirft. Es gibt kein
        // solches Material, aber die Stufe darf an einem nicht sterben.
        String roh = "A".repeat(20) + "_";
        String name = Shopware.kurzname(roh, false);

        assertTrue(!name.isBlank(), "wurde leer: \"" + name + "\"");
        new Warenprobe(name, "inhalt");
    }
}
