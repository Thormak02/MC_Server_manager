package de.freimarken.paper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;

/**
 * Ein Server-Stellvertreter unter {@code Bukkit.setServer} - damit eine Probe einen Zweig
 * wirklich DURCHLAUFEN kann.
 *
 * <p><b>Warum es den braucht.</b> Bis hierher trugen rund vierzig Zaehl- und Paartafeln in
 * {@code GrenzeTest} die ganze Last: Sie lesen den QUELLTEXT und sagen, dass ein Zweig
 * seine Methode ruft und an wen er sendet. Was sie nicht sagen koennen, ist, was dabei
 * herauskommt. Der Grund stand in jeder dieser Tafeln als Einschraenkung: Ein Zweig von
 * {@code Schutzwache} laesst sich nicht aufrufen, weil schon das LADEN der Klasse
 * scheitert - ihr Klasseninitialisierer baut {@code HEIKLE_GRUPPEN} aus
 * {@code Tag.FLOWER_POTS}, und {@code Tag} fragt in seinem eigenen Initialisierer
 * {@code Bukkit.getTag}. Ohne Server ist das ein {@code ExceptionInInitializerError},
 * bevor eine Probe ein Zeichen vergleichen kann.
 *
 * <p><b>Ein dynamischer Stellvertreter und keine handgeschriebene Klasse.</b>
 * {@code org.bukkit.Server} hat weit ueber hundert Methoden; eine Klasse, die sie alle
 * aufschreibt, waere nach dem naechsten Paper unbrauchbar und sagte nichts darueber, was
 * die Zweige wirklich brauchen. {@link Proxy} dreht das um: Beantwortet wird NUR, was
 * unten steht, und jede andere Methode wirft mit ihrem eigenen Namen. Was dieser
 * Stellvertreter kann, ist damit genau die Liste dessen, was gemessen gebraucht wurde -
 * und die naechste Probe, die mehr braucht, bekommt den Namen gesagt, statt ein
 * {@code null} zu lesen.
 *
 * <p><b>Einmal je JVM.</b> {@code Bukkit.setServer} wirft beim zweiten Aufruf, und Gradle
 * faehrt alle Probenklassen in EINER JVM. Darum {@link #einmalSetzen}, und darum ist der
 * Stellvertreter zustandsarm: Was eine Probe braucht, gibt sie beim Aufruf mit, statt es
 * hier einzutragen. Nur die Namen der Spieler und die Blockgruppen sind geteilt - sie
 * haengen an {@code static} Tabellen, die eine Probe ueber {@link #nenne} und
 * {@link #gruppe} fuellt.
 *
 * <p><b>Eine Blockgruppe, die niemand angemeldet hat, WIRFT bei {@code isTagged}.</b> Sie
 * liefert nicht einfach {@code false}: Dann liefe eine Probe durch den falschen Zweig und
 * waere gruen, ohne etwas gesehen zu haben. Der Klasseninitialisierer von {@code Tag} legt
 * alle hundert Gruppen an - angelegt werden duerfen sie also stumm, gefragt werden nicht.
 */
final class Serverprobe {

    /** Der Name der Blockgruppe in der Klage, wenn sie niemand angemeldet hat. */
    private static final Map<String, Set<Material>> GRUPPEN = new HashMap<>();

    /** Die Namen hinter den UUIDs - was {@code Bukkit.getOfflinePlayer} ausgibt. */
    private static final Map<UUID, String> NAMEN = new LinkedHashMap<>();

    private Serverprobe() {
    }

    /**
     * Setzt den Stellvertreter, falls noch keiner steht.
     *
     * <p>Jede Probe, die eine Bukkit-Klasse beruehrt, ruft das zuerst. Mehrfach ist
     * ungefaehrlich; ein zweites {@code Bukkit.setServer} waere es nicht.
     */
    static void einmalSetzen() {
        if (Bukkit.getServer() == null) {
            Bukkit.setServer(bau());
        }
    }

    /** Gibt dieser UUID einen Namen - sonst liefert {@code getName()} {@code null}. */
    static void nenne(UUID spieler, String name) {
        NAMEN.put(spieler, name);
    }

    /**
     * Meldet eine Blockgruppe an. Nur eine angemeldete darf gefragt werden.
     *
     * @param pfad der Pfad des Schluessels, etwa {@code "flower_pots"}
     */
    static void gruppe(String pfad, Set<Material> mitglieder) {
        GRUPPEN.put(pfad, Set.copyOf(mitglieder));
    }

    /**
     * Eine Welt, wie die Wachen sie sehen - nur ihr Schluessel zaehlt.
     *
     * <p>Hier und nicht in einer eigenen Datei, weil {@code Probewelt} schon
     * vergeben ist: Jene ist ein {@link Weltgriff} mit Bloecken darin und hat mit
     * Bukkit nichts zu tun. Diese hier ist ein Stellvertreter wie der Server
     * selbst und gehoert darum zu ihm.
     *
     * <p>{@code Portalwache} und {@code Ankunftswache} fragen beide nur
     * {@code welt.key().asString()}. Mehr zu beantworten waere geraten.
     *
     * @param schluessel etwa {@code "minecraft:altfurt"}
     */
    static World welt(String schluessel) {
        NamespacedKey name = NamespacedKey.fromString(schluessel);
        if (name == null) {
            throw new IllegalArgumentException("kein Weltschluessel: "
                    + schluessel);
        }
        return (World) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {World.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "key", "getKey" -> name;
                    case "getName" -> name.getKey();
                    case "toString" -> "Weltprobe " + schluessel;
                    case "hashCode" -> schluessel.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet World."
                                    + methode.getName() + " nicht");
                });
    }

    private static Server bau() {
        return (Server) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {Server.class}, new Serverhand());
    }

    /** Beantwortet, was gemessen gebraucht wird - und nur das. */
    private static final class Serverhand implements InvocationHandler {

        private final Logger protokoll = Logger.getLogger("Serverprobe");

        @Override
        public Object invoke(Object stellvertreter, Method methode, Object[] werte) {
            return switch (methode.getName()) {
                // Fuer den Klasseninitialisierer von Tag - er legt alle Gruppen an.
                case "getTag" -> new Blockgruppe((NamespacedKey) werte[1]);
                // Fuer Schutzwache.namen: der Eigentuemer einer fremden Parzelle.
                case "getOfflinePlayer" -> offlineSpieler(werte[0]);
                case "getLogger" -> protokoll;
                case "isPrimaryThread" -> true;
                case "getName" -> "Serverprobe";
                case "getVersion" -> "Serverprobe";
                case "getBukkitVersion" -> "Serverprobe";
                case "getMinecraftVersion" -> "1.21.4";
                case "toString" -> "Serverprobe";
                case "hashCode" -> System.identityHashCode(stellvertreter);
                case "equals" -> stellvertreter == (werte == null ? null : werte[0]);
                default -> throw new UnsupportedOperationException(
                        "Serverprobe beantwortet " + methode.getName() + " nicht."
                                + " Der Zweig, den du gerade durchlaeufst, braucht sie -"
                                + " also gehoert sie in Serverprobe.Serverhand, mit einer"
                                + " Antwort, die die Probe selbst mitgibt. Ein stilles"
                                + " null waere schlimmer: Dann liefe die Probe durch einen"
                                + " anderen Zweig und waere gruen.");
            };
        }

        private static OfflinePlayer offlineSpieler(Object kennung) {
            UUID spieler = (UUID) kennung;
            return (OfflinePlayer) Proxy.newProxyInstance(
                    Serverprobe.class.getClassLoader(),
                    new Class<?>[] {OfflinePlayer.class},
                    (eigen, methode, werte) -> switch (methode.getName()) {
                        case "getName" -> NAMEN.get(spieler);
                        case "getUniqueId" -> spieler;
                        case "toString" -> "Offlinespieler " + spieler;
                        case "hashCode" -> spieler.hashCode();
                        case "equals" -> eigen == (werte == null ? null : werte[0]);
                        default -> throw new UnsupportedOperationException(
                                "Serverprobe beantwortet OfflinePlayer."
                                        + methode.getName() + " nicht");
                    });
        }
    }

    /**
     * Eine Blockgruppe, die nur beantwortet, was eine Probe angemeldet hat.
     *
     * <p>{@code getKey} und das Anlegen gehen immer - der Initialisierer von {@code Tag}
     * legt jede Gruppe an, die Paper kennt, und darf dabei nicht scheitern. Erst die FRAGE
     * nach einem Mitglied verlangt, dass jemand die Gruppe gefuellt hat.
     */
    private record Blockgruppe(NamespacedKey schluessel) implements Tag<Material> {

        @Override
        public boolean isTagged(Material pruefling) {
            return mitglieder().contains(pruefling);
        }

        @Override
        public Set<Material> getValues() {
            return mitglieder();
        }

        @Override
        public NamespacedKey getKey() {
            return schluessel;
        }

        private Set<Material> mitglieder() {
            Set<Material> angemeldet = GRUPPEN.get(schluessel.getKey());
            if (angemeldet == null) {
                throw new UnsupportedOperationException("Die Blockgruppe "
                        + schluessel.getKey() + " hat niemand angemeldet. Eine leere"
                        + " Gruppe waere die schlechtere Antwort: Der Zweig liefe dann"
                        + " vorbei, und die Probe waere gruen, ohne etwas gesehen zu"
                        + " haben. Melde sie mit Serverprobe.gruppe(\""
                        + schluessel.getKey() + "\", Set.of(...)) an.");
            }
            return angemeldet;
        }
    }
}
