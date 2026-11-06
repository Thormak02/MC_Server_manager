package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Portalwache DURCHLAUFEN - beide Riegel, beide Richtungen.
 *
 * <p>Der dritte Zug nach {@code SchutzwacheTest} und {@code AnkunftswacheTest}. Diese
 * Wache hat eine Eigenheit, die keine andere teilt: <b>Zwei verschiedene Ereignisse
 * bewachen denselben Weg</b>, weil nicht dokumentiert ist, welches eine Portalreise
 * wirklich verhindert - und ein Spieler in der Haupt-Nether ist nicht zurueckzuholen.
 * Dass WIRKLICH beide abbrechen, stand bisher nirgends; {@code SENDESTELLEN} zaehlt eine
 * Sendestelle und sagt ueber den zweiten Riegel nichts, weil beide Zweige dieselbe private
 * Methode rufen.
 *
 * <p><b>Die Abklingzeit ist hier acht Sekunden und nicht zwei</b>, und der Grund steht im
 * Javadoc der Wache: Das Betreten feuert, SOLANGE der Spieler im Portalblock steht. Ohne
 * Drossel liest er die zwei Zeilen in jedem Tick. Dass sie je Spieler gilt, ist dieselbe
 * Frage wie bei der Schutzwache - und hier erstmals am laufenden Zweig gemessen.
 */
class PortalwacheTest {

    private static final String EIGENE = "minecraft:altfurt";
    private static final String FREMDE = "minecraft:world_nether";

    /**
     * Die Absage am Portal - ZWEI Chatzeilen aus EINEM Schluessel.
     *
     * <p>Aus dem ROTEN Lauf und gegen die Tafel von {@code BildGleichTest}
     * gegengelesen. Als Liste und nicht als zwei Erwartungen: Die zwei Zeilen
     * gehen immer zusammen hinaus, und genau das haelt diese Form fest.
     */
    private static final List<String> ABSAGE = List.of(
            "&cHier fuehrt kein Portal hinaus.",
            "&7Nether und Ende erreichst du mit /warp nether und /warp end -"
                    + " oder durch ein Tor am Bundesmarkt.");

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    private static Portalwache wache() {
        return new Portalwache(Set.of(EIGENE), Sprachprobe.spracheAusDemJar());
    }

    /** Der Ort des Portals. Die Wache liest ihn nicht - sie fragt die Welt des Spielers. */
    private static Location portal() {
        return new Location(null, 0, 64, 0);
    }

    /**
     * Das Betreten eines Portals, wie Paper es der Wache gibt.
     *
     * <p>Mit {@link PortalType}, obwohl die Wache ihn nicht liest: Der
     * zweistellige Konstruktor ist veraltet, und dieses Projekt baut ohne
     * Verwarnung. {@code NETHER} ist der Fall, um den es hier ueberhaupt geht.
     */
    private static EntityPortalEnterEvent portalEreignis(Spielerprobe spieler) {
        return new EntityPortalEnterEvent(spieler.spieler(), portal(),
                PortalType.NETHER);
    }

    @Test
    @DisplayName("38 - in einer eigenen Welt wird das Betreten abgebrochen und erklaert")
    void inEigenerWeltAbgebrochenUndErklaert() {
        Portalwache wache = wache();
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(51))
                .inWelt(EIGENE);
        var ereignis = portalEreignis(spieler);

        wache.beimBetreten(ereignis);

        assertTrue(ereignis.isCancelled(), "das Portal darf nicht greifen");
        assertEquals(List.of(), spieler.handbalkenBild(),
                "das sind Chatzeilen und kein Handbalken - sie sollen stehen bleiben");
        assertEquals(ABSAGE, spieler.chatBild());
    }

    @Test
    @DisplayName("38b - in einer fremden Welt greift die Wache gar nicht")
    void inFremderWeltGreiftNichts() {
        Portalwache wache = wache();
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(52))
                .inWelt(FREMDE);
        var ereignis = portalEreignis(spieler);

        wache.beimBetreten(ereignis);

        // Die andere Richtung, und sie ist die wichtigere: Griffe die Wache ueberall,
        // waere jedes Portal auf dem ganzen Server tot - auch in Welten, die einem
        // anderen Plugin gehoeren.
        assertFalse(ereignis.isCancelled(), "fremde Welten gehen uns nichts an");
        assertEquals(List.of(), spieler.chatBild());
    }

    @Test
    @DisplayName("38c - der zweite Riegel haelt auch: die Portalreise selbst")
    void derZweiteRiegelHaeltAuch() {
        Portalwache wache = wache();
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(53))
                .inWelt(EIGENE);
        var ereignis = new PlayerPortalEvent(spieler.spieler(), portal(), portal());

        wache.beiDerPortalreise(ereignis);

        assertTrue(ereignis.isCancelled(), "zwei Riegel fuer einen Weg - der zweite"
                + " kostet nichts und ist der, der im Ernstfall haelt");
        assertEquals(ABSAGE, spieler.chatBild());

        Spielerprobe fremder = new Spielerprobe("Dora", Spielerprobe.kennungAus(54))
                .inWelt(FREMDE);
        var woanders = new PlayerPortalEvent(fremder.spieler(), portal(), portal());

        wache.beiDerPortalreise(woanders);

        assertFalse(woanders.isCancelled());
        assertEquals(List.of(), fremder.chatBild());
    }

    @Test
    @DisplayName("38d - die Abklingzeit gilt JE SPIELER, und abgebrochen wird trotzdem")
    void dieAbklingzeitGiltJeSpieler() {
        Portalwache wache = wache();
        Spielerprobe erster = new Spielerprobe("Cara", Spielerprobe.kennungAus(55))
                .inWelt(EIGENE);
        Spielerprobe zweiter = new Spielerprobe("Dora", Spielerprobe.kennungAus(56))
                .inWelt(EIGENE);

        wache.beimBetreten(portalEreignis(erster));
        var nochmal = portalEreignis(erster);
        wache.beimBetreten(nochmal);

        // ZWEI Chatzeilen sind EINE Erklaerung: keinAusgangZeilen gibt rote Absage
        // und grauen Weg aus einem Schluessel, und 33al haelt das fest.
        assertEquals(2, erster.chatBild().size(),
                "zweimal innerhalb der acht Sekunden ist EINE Erklaerung - das Ereignis"
                        + " feuert, solange er im Portalblock steht");
        // Und das ist der Unterschied zur Meldung: Der ABBRUCH wird nicht gedrosselt.
        // Waere er es, truege die zweite Sekunde im Portal den Spieler in die Nether.
        assertTrue(nochmal.isCancelled(), "gedrosselt wird die Erklaerung, nicht der"
                + " Riegel - sonst reist er beim zweiten Tick doch");

        wache.beimBetreten(portalEreignis(zweiter));

        assertEquals(2, zweiter.chatBild().size(),
                "ein zweiter Spieler ist von der Pause des ersten nicht betroffen -"
                        + " sonst steht einer stumm vor einem Portal, das nicht geht");
    }
}
