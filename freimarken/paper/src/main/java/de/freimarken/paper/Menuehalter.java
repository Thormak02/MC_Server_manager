package de.freimarken.paper;

import java.util.Optional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * WORAN das Plugin sein eigenes Menue erkennt - die EINE Stelle dafuer.
 *
 * <p><b>Warum ein Halter und nicht der Titel:</b> Ein Titelvergleich waere eine Falle mit
 * Ansage - der Titel kommt aus der Sprachdatei, der Betreiber darf ihn aendern, und ein
 * Spieler kann eine Truhe so nennen. Der Halter ist dagegen ein Objekt, das nur dieses
 * Plugin baut; niemand kann ihn nachmachen.
 *
 * <p><b>Warum {@code Bukkit.createInventory} und nicht {@code MenuType}</b> [21.11.2026]:
 * Plan 11.2 nennt die MenuType-API, und fuer die Sondermenues (Amboss, Lesepult, Webstuhl)
 * ist sie auch der Weg - sie liefert dort typisierte Ansichten. Fuer eine schlichte Truhe
 * kann sie das Entscheidende aber nicht: Ihr {@code InventoryViewBuilder} nimmt Titel,
 * Ort und Reichweite, aber KEINEN Halter (im API-Jar 26.3.231 nachgesehen). Ein Menue
 * ohne Halter liesse sich nur am Titel wiedererkennen - siehe oben. Darum die aeltere
 * Tuer, und zwar mit Begruendung statt stillschweigend.
 *
 * <p>ZWEI Leser fragen diese Klasse, und das ist der Grund, warum sie eine ist:
 * {@link Menuewache} will wissen, welche Tafel offen ist, und {@link Messwache} will
 * wissen, dass sie dieses Oeffnen NICHT als taetige Zeit zaehlt - ein offenes Hauptmenue
 * waere sonst bezahlte Anwesenheit.
 */
final class Menuehalter implements InventoryHolder {

    private final Menuetafel tafel;
    private Inventory inventar;

    Menuehalter(Menuetafel tafel) {
        if (tafel == null) {
            throw new IllegalArgumentException("Ein Menue ohne Tafel gibt es nicht");
        }
        this.tafel = tafel;
    }

    /** Was in diesem Menue steht. */
    Menuetafel tafel() {
        return tafel;
    }

    /**
     * Das Inventar, das Bukkit fuer diesen Halter gebaut hat.
     *
     * <p>Wird unmittelbar nach dem Bauen gesetzt: Bukkit verlangt den Halter ZUERST, und
     * das Inventar entsteht erst daraus. Wirft, wenn jemand vorher fragt - ein
     * {@code null} hier waere ein Absturz an einer anderen Stelle, und dort suchte ihn
     * niemand.
     */
    @Override
    public Inventory getInventory() {
        if (inventar == null) {
            throw new IllegalStateException("Dieser Menuehalter hat noch kein Inventar -"
                    + " es entsteht erst beim Bauen und wird danach gesetzt");
        }
        return inventar;
    }

    void setzeInventar(Inventory gebaut) {
        this.inventar = gebaut;
    }

    /**
     * Die Tafel hinter diesem Inventar - oder leer, wenn es keines des Plugins ist.
     *
     * <p>{@code getHolder(false)} und nicht {@code getHolder()}: Jenes laedt fuer ein
     * Blockinventar notfalls den Chunk nachbdem - dieselbe Entscheidung wie in
     * {@code Shopware.bloeckeVon}, und aus demselben Grund (ein Ereignis darf keine Welt
     * nachladen).
     */
    static Optional<Menuetafel> tafelVon(Inventory inventar) {
        if (inventar == null) {
            return Optional.empty();
        }
        return inventar.getHolder(false) instanceof Menuehalter halter
                ? Optional.of(halter.tafel())
                : Optional.empty();
    }

    /** Ob dieses Inventar ein Menue des Plugins ist. */
    static boolean istMenue(Inventory inventar) {
        return tafelVon(inventar).isPresent();
    }
}
