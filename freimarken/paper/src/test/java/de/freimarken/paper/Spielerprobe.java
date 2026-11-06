package de.freimarken.paper;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

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

    /** In welcher Welt er steht - nur fuer die Wachen, die danach fragen. */
    private String weltschluessel;

    /** Sein Spielmodus. {@code setGameMode} aendert ihn UND schreibt mit. */
    private GameMode modus = GameMode.SURVIVAL;

    private final List<GameMode> moduswechsel = new ArrayList<>();

    Spielerprobe(String name, UUID kennung, String... rechte) {
        this.name = name;
        this.kennung = kennung;
        this.rechte = Set.of(rechte);
    }

    /** Eine UUID, die nur von der Zahl abhaengt - damit zwei Laeufe dieselbe ergeben. */
    static UUID kennungAus(int zahl) {
        return new UUID(0L, zahl);
    }

    /** Stellt ihn in eine Welt - gibt sich selbst zurueck, fuer die Kette. */
    Spielerprobe inWelt(String schluessel) {
        this.weltschluessel = schluessel;
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
     * Der Spieler, wie die Wache ihn sieht.
     *
     * <p>Beantwortet wird NUR, was gemessen gebraucht wird; alles andere wirft mit seinem
     * eigenen Namen. Dieselbe Entscheidung und derselbe Grund wie in {@link Serverprobe}.
     */
    Player spieler() {
        return (Player) Proxy.newProxyInstance(Spielerprobe.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getUniqueId" -> kennung;
                    case "getWorld" -> welt();
                    case "getGameMode" -> modus;
                    case "setGameMode" -> setzeModus(werte[0]);
                    case "getName" -> name;
                    case "hasPermission" -> rechte.contains(String.valueOf(werte[0]));
                    case "sendActionBar" -> merke(handbalken, werte[0]);
                    case "sendMessage" -> merke(chat, werte[0]);
                    case "toString" -> "Spielerprobe " + name;
                    case "hashCode" -> kennung.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Spielerprobe beantwortet Player." + methode.getName()
                                    + " nicht. Der Zweig, den du durchlaeufst, braucht"
                                    + " sie - also gehoert sie hierher, mit einer"
                                    + " Antwort, die die Probe mitgibt.");
                });
    }

    private World welt() {
        if (weltschluessel == null) {
            throw new UnsupportedOperationException("Dieser Spieler steht in keiner"
                    + " Welt. Der Zweig, den du durchlaeufst, fragt danach - also"
                    + " gehoert inWelt(...) in die Probe. Eine erfundene Welt waere"
                    + " schlimmer: Dann haengt das Ergebnis an einem Namen, den die"
                    + " Probe nie genannt hat.");
        }
        return Serverprobe.welt(weltschluessel);
    }

    private Object setzeModus(Object neuer) {
        modus = (GameMode) neuer;
        moduswechsel.add(modus);
        return null;
    }

    private static Object merke(List<Component> liste, Object gesendet) {
        if (!(gesendet instanceof Component zeile)) {
            throw new UnsupportedOperationException("Gesendet wurde kein Component,"
                    + " sondern " + (gesendet == null ? "null" : gesendet.getClass())
                    + ". Der eine Ausgang nach Adventure ist Sprache, und der gibt"
                    + " Components - eine Zeichenkette hier heisst, dass jemand daneben"
                    + " gebaut hat.");
        }
        liste.add(zeile);
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
