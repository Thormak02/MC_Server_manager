package de.freimarken.paper;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Ein Spieler-Stellvertreter, der mitschreibt, was bei ihm ankommt.
 *
 * <p>Das Gegenstueck zu {@link Serverprobe}: Jener macht das LADEN einer Wache moeglich,
 * dieser macht ihr Ergebnis lesbar. Zusammen koennen die Proben einen Ereigniszweig
 * durchlaufen, statt seinen Quelltext zu lesen.
 *
 * <p><b>Getrennte Listen fuer Handbalken und Chat, und das ist kein Ordnungssinn.</b>
 * {@code sendActionBar} und {@code sendMessage} gehen an denselben Menschen, aber an zwei
 * verschiedene Stellen seines Bildschirms: Der Handbalken steht ueber dem Gegenstandsgurt
 * und ueberschreibt sich selbst, eine Chatzeile bleibt stehen. Eine gemeinsame Liste
 * liesse eine Wache, die ihren Handbalken als Chatzeile schickt, gruen durchlaufen - und
 * im Spiel waere das ein vollgeschriebener Chat statt eines stillen Hinweises.
 *
 * <p><b>Die UUID kommt von aussen und wird nicht gewuerfelt.</b> Eine Probe, die die
 * Drossel prueft, braucht zwei Spieler, die sich unterscheiden, und einen Lauf, der
 * morgen dasselbe tut.
 */
final class Spielerprobe {

    private final UUID kennung;
    private final String name;
    private final Set<String> rechte;
    private final List<Component> handbalken = new ArrayList<>();
    private final List<Component> chat = new ArrayList<>();

    /**
     * In welcher Welt er steht - und zwar als OBJEKT, einmal gebaut.
     *
     * <p>Das ist nicht Sparsamkeit: {@code ParzellenBefehle.inDerMarktstadt}
     * vergleicht die Welt des Spielers mit {@code equals} gegen die Welt, die der
     * Befehl im Konstruktor bekam. Ein Stellvertreter antwortet darauf mit
     * Identitaet - gaebe {@code getWorld()} bei jedem Aufruf ein neues Exemplar,
     * stuende der Spieler nie in der Marktstadt, und der Zweig kehrte stumm um.
     */
    private World welt;

    /** Wo er steht. Ein Zweig, der danach fragt, braucht ihn gesetzt. */
    private Location ort;

    /**
     * Was er in der Haupthand haelt - oder {@code null}.
     *
     * <p>Gebraucht seit dem Bundesmarkt: {@code /markt verkaufen} liest die Hand und
     * nimmt Stuecke daraus. Ein {@code null} heisst LEERE Hand, und der Stellvertreter
     * gibt dann einen Stapel Luft zurueck - so wie Bukkit es tut.
     */
    private ItemStack inDerHand;

    /** Sein Spielmodus. {@code setGameMode} aendert ihn UND schreibt mit. */
    private GameMode modus = GameMode.SURVIVAL;

    private final List<GameMode> moduswechsel = new ArrayList<>();

    /**
     * Eine zweite Liste, in die die Chatzeilen AUCH geschrieben werden.
     *
     * <p>Fuer Proben, die mehrere Spieler in EINE Mitschrift lesen -
     * {@code AufrufstelleTest} tut das, weil es die REIHENFOLGE ueber zwei
     * Empfaenger hinweg prueft. Die eigene Liste bleibt daneben gefuellt; eine
     * geteilte Mitschrift ist eine Ergaenzung und kein Ersatz.
     */
    private List<Component> geteilt;

    /**
     * Der Stellvertreter, EINMAL gebaut.
     *
     * <p>Nicht Sparsamkeit: Ein neues Exemplar je Aufruf waere mit
     * {@code equals} nicht mit sich selbst gleich, und mehrere Zweige dieses
     * Projekts vergleichen Spieler so - {@code angefragter.equals(fragender)}
     * in {@code AnfragenBefehle} zum Beispiel. Dort wuerde eine Selbstanfrage
     * sonst nicht erkannt.
     */
    private Player stellvertreter;

    Spielerprobe(String name, UUID kennung, String... rechte) {
        this.name = name;
        this.kennung = kennung;
        this.rechte = Set.of(rechte);
    }

    /** Eine UUID, die nur von der Zahl abhaengt - damit zwei Laeufe dieselbe ergeben. */
    static UUID kennungAus(int zahl) {
        return new UUID(0L, zahl);
    }

    /** Stellt ihn in eine Welt ohne Bloecke - gibt sich selbst zurueck. */
    Spielerprobe inWelt(String schluessel) {
        return inWelt(Serverprobe.welt(schluessel));
    }

    /**
     * Stellt ihn in DIESE Welt - fuer jeden Zweig, der sie mit {@code equals}
     * gegen seine eigene prueft.
     */
    Spielerprobe inWelt(World wo) {
        this.welt = wo;
        return this;
    }

    /** Schreibt seine Chatzeilen AUCH in diese Liste - gibt sich selbst zurueck. */
    Spielerprobe schreibtNach(List<Component> mitschrift) {
        this.geteilt = mitschrift;
        return this;
    }

    /**
     * Gibt ihm etwas in die Hand - gibt sich selbst zurueck, fuer die Kette.
     *
     * <p>Ein {@link Wesenprobe.Probestapel} und kein echter {@code ItemStack}: Jener
     * laesst sich ohne laufenden Server nicht bauen, und seine Menge nicht setzen -
     * gemessen gegen das echte Jar.
     */
    Spielerprobe mitInDerHand(Material art, int menge) {
        this.inDerHand = new Wesenprobe.Probestapel(art, menge);
        return this;
    }

    /** Was er jetzt noch in der Hand haelt - fuer die Probe NACH dem Verkauf. */
    int mengeInDerHand() {
        return inDerHand == null ? 0 : inDerHand.getAmount();
    }

    /** Stellt ihn an einen Ort - gibt sich selbst zurueck, fuer die Kette. */
    Spielerprobe anOrt(Location wo) {
        this.ort = wo;
        return this;
    }

    /** Setzt seinen Spielmodus OHNE Mitschrift - das ist der Ausgangsstand. */
    Spielerprobe imModus(GameMode anfang) {
        this.modus = anfang;
        return this;
    }

    /** Jeder Spielmodus, in den ihn ein Zweig gesetzt hat, in der Reihenfolge. */
    List<GameMode> moduswechsel() {
        return List.copyOf(moduswechsel);
    }

    UUID kennung() {
        return kennung;
    }

    /**
     * Das Inventar, wie ein Zweig es sieht - beantwortet wird NUR die Haupthand.
     *
     * <p>Derselbe Grundsatz wie beim Spieler selbst: Was gemessen gebraucht wird, wird
     * beantwortet; alles andere wirft mit seinem eigenen Namen. Ein stilles null waere
     * schlimmer - dann liefe die Probe durch einen anderen Zweig und waere gruen, ohne
     * etwas gesehen zu haben.
     */
    private PlayerInventory inventar() {
        return (PlayerInventory) Proxy.newProxyInstance(
                Spielerprobe.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    // Eine LEERE Hand ist ein Stapel Luft - so gibt Bukkit es zurueck,
                    // und der Zweig prueft darauf.
                    case "getItemInMainHand" -> inDerHand == null
                            ? new Wesenprobe.Probestapel(Material.AIR, 0)
                            : inDerHand;
                    // Ein LEERES Inventar mit 36 Plaetzen [Teil C]: Ladenware zaehlt
                    // damit die freien Plaetze fuer /laden abholfach. 36 ist die Zahl
                    // des Spielerinventars ohne Ruestung und zweite Hand - genau das,
                    // was getStorageContents in Bukkit liefert.
                    case "getStorageContents" -> new org.bukkit.inventory.ItemStack[36];
                    case "toString" -> "Inventarprobe " + name;
                    case "hashCode" -> kennung.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Spielerprobe beantwortet PlayerInventory."
                                    + methode.getName() + " nicht. Der Zweig, den du"
                                    + " gerade durchlaeufst, braucht sie - also gehoert"
                                    + " sie hierher, mit einer Antwort, die die Probe"
                                    + " selbst mitgibt.");
                });
    }

    /**
     * Der Spieler, wie die Wache ihn sieht.
     *
     * <p>Beantwortet wird NUR, was gemessen gebraucht wird; alles andere wirft mit seinem
     * eigenen Namen. Dieselbe Entscheidung und derselbe Grund wie in {@link Serverprobe}.
     */
    Player spieler() {
        if (stellvertreter != null) {
            return stellvertreter;
        }
        stellvertreter = (Player) Proxy.newProxyInstance(
                Spielerprobe.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getUniqueId" -> kennung;
                    case "getWorld" -> welt();
                    case "getLocation" -> ortOderKlage();
                    case "getGameMode" -> modus;
                    case "setGameMode" -> setzeModus(werte[0]);
                    case "getName" -> name;
                    case "hasPermission" -> rechte.contains(String.valueOf(werte[0]));
                    case "getEffectivePermissions" -> wirksameRechte();
                    // Sichtbar ist jeder: Unsichtbarkeit kommt im Spiel von
                    // einem anderen Plugin, und keine Probe stellt sie bisher
                    // her. Wer sie braucht, macht daraus eine Menge.
                    case "canSee" -> true;
                    case "sendActionBar" -> merke(handbalken, werte[0]);
                    case "sendMessage" -> merke(chat, werte[0]);
                    case "getInventory" -> inventar();
                    case "toString" -> "Spielerprobe " + name;
                    case "hashCode" -> kennung.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Spielerprobe beantwortet Player." + methode.getName()
                                    + " nicht. Der Zweig, den du durchlaeufst, braucht"
                                    + " sie - also gehoert sie hierher, mit einer"
                                    + " Antwort, die die Probe mitgibt.");
                });
        return stellvertreter;
    }

    private World welt() {
        if (welt == null) {
            throw new UnsupportedOperationException("Dieser Spieler steht in keiner"
                    + " Welt. Der Zweig, den du durchlaeufst, fragt danach - also"
                    + " gehoert inWelt(...) in die Probe. Eine erfundene Welt waere"
                    + " schlimmer: Dann haengt das Ergebnis an einem Namen, den die"
                    + " Probe nie genannt hat.");
        }
        return welt;
    }

    /**
     * Die gesetzten Rechte als Eintraege - fuer jeden Zweig, der sie DURCHSUCHT.
     *
     * <p>{@code hasPermission} genuegt nicht ueberall: Die Zuschlaege auf die
     * Hoechstzahl stehen als {@code freimarken.extra.parzellen.3} im Recht selbst,
     * und {@code ParzellenBefehle.hoechsteAus} liest die ZAHL aus dem Namen. Dazu
     * muss es die Rechte aufzaehlen koennen.
     *
     * <p>{@code PermissionAttachmentInfo} ist eine Klasse und keine Schnittstelle,
     * also kein Stellvertreter: Sie wird gebaut. Der Anhang und der Traeger bleiben
     * {@code null} - gelesen werden nur Name und Wert.
     */
    private Set<PermissionAttachmentInfo> wirksameRechte() {
        Set<PermissionAttachmentInfo> eintraege = new LinkedHashSet<>();
        for (String recht : rechte) {
            eintraege.add(new PermissionAttachmentInfo(null, recht, null, true));
        }
        return eintraege;
    }

    private Location ortOderKlage() {
        if (ort == null) {
            throw new UnsupportedOperationException("Dieser Spieler steht nirgends."
                    + " Der Zweig fragt nach seinem Ort - also gehoert anOrt(...) in"
                    + " die Probe. Ein erfundener Ort waere schlimmer: Dann haengt"
                    + " das Ergebnis an einer Koordinate, die die Probe nie genannt"
                    + " hat.");
        }
        // Wie im Spiel eine Kopie: Ein Zweig, der den Ort veraendert, soll den
        // des Spielers nicht mit veraendern.
        return ort.clone();
    }

    private Object setzeModus(Object neuer) {
        modus = (GameMode) neuer;
        moduswechsel.add(modus);
        return null;
    }

    private Object merke(List<Component> liste, Object gesendet) {
        if (!(gesendet instanceof Component zeile)) {
            throw new UnsupportedOperationException("Gesendet wurde kein Component,"
                    + " sondern " + (gesendet == null ? "null" : gesendet.getClass())
                    + ". Der eine Ausgang nach Adventure ist Sprache, und der gibt"
                    + " Components - eine Zeichenkette hier heisst, dass jemand daneben"
                    + " gebaut hat.");
        }
        liste.add(zeile);
        if (geteilt != null && liste == chat) {
            // NUR der Chat, nicht der Handbalken: Eine geteilte Mitschrift
            // dient der Reihenfolge von CHATzeilen; ein Handbalken darin stuende
            // in einer Reihe, in die er nicht gehoert.
            geteilt.add(zeile);
        }
        return null;
    }

    /** Die Handbalken in der Reihenfolge, in der sie ankamen - als {@code &}-Bild. */
    List<String> handbalkenBild() {
        return handbalken.stream().map(Sprachprobe::bild).toList();
    }

    /** Die Chatzeilen in der Reihenfolge, in der sie ankamen - als {@code &}-Bild. */
    List<String> chatBild() {
        return chat.stream().map(Sprachprobe::bild).toList();
    }

    /** Vergisst alles Angekommene - fuer den naechsten Fall derselben Probe. */
    void vergessen() {
        handbalken.clear();
        chat.clear();
    }
}
