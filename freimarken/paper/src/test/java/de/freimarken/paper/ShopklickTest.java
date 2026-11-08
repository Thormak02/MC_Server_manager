package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Warenprobe;
import java.util.UUID;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die zwei reinen Bedingungen des Handelsklicks.
 *
 * <p><b>Nur diese zwei, und das ist benannt statt verschwiegen:</b> Der Ereigniszweig
 * selbst braucht ein {@code PlayerInteractEvent}, einen Block, dessen {@code Tag} gesetzt
 * ist, und einen Spieler mit Inventar - also einen laufenden Server. Was von ihm WIRKLICH
 * eine Entscheidung ist, steht darum in zwei statischen Methoden, und die werden hier
 * Zeichen fuer Zeichen gelesen. Derselbe Schnitt wie bei
 * {@code Schutzwache.alsHandelDurchlassen}, und derselbe Grund: Im Zweig blieb jede
 * Einspritzung gruen.
 *
 * <p><b>Diese Klasse beruehrt {@link Schutzwache} NICHT</b>, und das ist gemessen:
 * Deren statischer Block fasst {@code Tag.ALL_SIGNS} an, und das wirft ohne Server
 * ein {@code ExceptionInInitializerError}. Dass die Tuer dieselbe Frage durchreicht,
 * haelt darum {@code SchutzwacheTest} Probe 36l fest - dort steht die
 * Serverumgebung.
 */
@DisplayName("Der Handelsklick")
class ShopklickTest {

    private static final Kistenshop EIN_SHOP = new Kistenshop("freimarken:altfurt", 60,
            71, 6, 61, 71, 6, UUID.randomUUID(), 16, 100L, 80L,
            new Warenprobe("STONE", "inhalt"));

    @Test
    @DisplayName("Die Tuer geht NUR am Schildblock auf - nie an der Kiste")
    void nurAmSchildblock() {
        // DER TEUERSTE FEHLER, DER HIER MOEGLICH WAERE. Ein Shop hat zwei Bloecke: sein
        // Schild und seine Kiste. Nur das SCHILD ist die Bedienung; die Kiste ist der
        // Vorrat, und die oeffnet der Besitzer.
        //
        // Waere diese Methode mit einem Shop allein zufrieden, haette jeder Kunde einen
        // Rechtsklick weit Zugriff auf den ganzen Bestand - denn die Tuer steht VOR der
        // Besitzfrage und ist ein Durchlassen. In der Markthalle liefe das am
        // Ladenschutz vorbei, der genau das verhindert.
        //
        // Die naheliegenden Umsetzungen tun das: Shopbuch.istShopkiste und .anDerKiste
        // antworten beide fuer die KISTE ja, und istShopkiste sagt bei einer Stoerung
        // sogar ja. Darum nimmt diese Methode den Schildblock als EIGENE Tatsache.
        assertTrue(Shopklick.istHandelsschild(true, EIN_SHOP),
                "ein Schild mit einem Shop daran ist ein Handel");
        assertFalse(Shopklick.istHandelsschild(false, EIN_SHOP),
                "KEIN Schildblock, obwohl dort ein Shop steht - das ist die Kiste, und"
                        + " die geht die Handelstuer nichts an");
        assertFalse(Shopklick.istHandelsschild(true, null),
                "ein Schild ohne Shop ist ein gewoehnliches Schild");
        assertFalse(Shopklick.istHandelsschild(false, null));
    }

    @Test
    @DisplayName("Nur die HAUPTHAND handelt - ein Klick kommt bis zu zweimal")
    void nurDieHaupthand() {
        // Ein Klick kann zweimal gemeldet werden, einmal je Hand, wenn die erste Meldung
        // die Benutzung nicht verbraucht hat. Ohne diese Zeile buchte ein Klick zweimal
        // und bewegte zweimal die Ware - der Kunde sieht einen Klick und zahlt zweimal.
        //
        // Der sicher erreichbare Weg dorthin ist der SNEAK-Klick, also genau die Geste,
        // mit der ein Stapel gehandelt wird: Bei gedrueckter Schleichtaste mit etwas in
        // der Hand ueberspringt der Spielstand die Blockbenutzung und versucht nur den
        // Gegenstand; verbraucht der nichts, folgt der Versuch mit der zweiten Hand.
        // Dann waere die Doppelbuchung am teuersten.
        assertTrue(Shopklick.nurHaupthand(EquipmentSlot.HAND));
        assertFalse(Shopklick.nurHaupthand(EquipmentSlot.OFF_HAND),
                "die zweite Hand handelt nicht - sonst zahlt ein Klick zweimal");
        assertFalse(Shopklick.nurHaupthand(null),
                "eine Hand ohne Seite gibt es nur, wo gar nicht geklickt wurde");
        // Und JEDER andere Platz auch nicht. Eine Fassung mit "!= OFF_HAND" waere fuer
        // die zwei Haende richtig und liesse Stiefel, Helm und Koerper durch.
        for (EquipmentSlot platz : EquipmentSlot.values()) {
            if (platz != EquipmentSlot.HAND) {
                assertFalse(Shopklick.nurHaupthand(platz), platz.toString());
            }
        }
    }

    @Test
    @DisplayName("Ohne Shophandel gibt es diese Tuer nicht")
    void ohneHandelKeineTuer() {
        // Eine Tuer, die aufgeht und hinter der nichts ist, waere schlimmer als keine:
        // Jeder duerfte jedes Schild anklicken, und gehandelt wuerde nicht. Darum wirft
        // der Konstruktor - dieselbe Entscheidung wie bei Schutzwache und Shopwache.
        assertThrows(IllegalArgumentException.class, () -> new Shopklick(null, null));
    }
}
