package de.freimarken.paper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
 * <p><b>Es gab einen AELTEREN Satz derselben Stellvertreter</b>, privat in
 * {@code AufrufstelleTest} und mit demselben {@link Proxy}-Gedanken gebaut, nur
 * frueher. Gefunden wurde er erst, als diese Datei schon stand. Seit dem
 * 06.11.2026 sind beide zusammengelegt: Jene Datei gibt nur noch weiter, und was
 * sie braucht, steht hier. <b>Darum ist dies der EINE Ort</b> - wer eine Antwort
 * ergaenzt, ergaenzt sie fuer alle Proben, und wer eine aendert, aendert sie fuer
 * alle. Die vierunddreissig Proben jener Datei sind dabei das Netz.
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

    /**
     * Wer online ist - was {@code Bukkit.getPlayerExact} findet.
     *
     * <p><b>Geteilt zwischen allen Probenklassen, und darum gibt es {@link #niemandMehrDa}.</b>
     * Gradle faehrt alle Proben in EINER JVM; ein Spieler, den eine Probe hier
     * eintraegt, waere sonst auch in der naechsten noch da, und zwar in einer
     * Reihenfolge, die von der Probenreihenfolge abhaengt. Jede Probe, die
     * {@code getPlayerExact} braucht, raeumt darum vorher auf.
     */
    private static final Map<String, Player> ANWESEND = new LinkedHashMap<>();

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

    /**
     * Die angemeldeten Welten, nach Schluessel - damit {@code Bukkit.getWorld} antwortet.
     *
     * <p>Gefuellt von {@link #welt(String, Weltgriff)}: Wer eine Welt baut, meldet sie
     * damit auch an. Ohne diese Tabelle koennte ein Zweig, der eine Welt ueber ihren
     * Schluessel SUCHT, nicht durchlaufen werden - und genau das tut {@code Chunkmarken}.
     */
    private static final Map<String, World> WELTEN = new LinkedHashMap<>();

    /**
     * Je Welt und Position die Blockdaten, die eine Probe angemeldet hat.
     *
     * <p>Gebraucht seit Teil E2: Die Verdienstwache fragt eine Erntepflanze nach ihrer
     * REIFE, und die steht in den Blockdaten. Ohne Server gibt es keine -
     * {@code Bukkit.createBlockData} wirft, gemessen gegen das echte Jar -, also schiebt
     * die Probe einen Stellvertreter unter.
     */
    private static final Map<String, BlockData> BLOCKDATEN = new LinkedHashMap<>();

    /**
     * Die Datenbehaelter der Chunks, nach Welt und Chunkkoordinate.
     *
     * <p>Sie muessen ueber Aufrufe hinweg BESTEHEN: Eine Marke, die in den Chunk
     * geschrieben wird, soll beim naechsten Lesen noch da sein. Ein Behaelter je Aufruf
     * waere ein Stellvertreter, der jede Probe gruen macht, ohne dass etwas gespeichert
     * wird.
     */
    private static final Map<String, PersistentDataContainer> BEHAELTER =
            new LinkedHashMap<>();

    /**
     * Wie oft in einen Datenbehaelter GESCHRIEBEN wurde.
     *
     * <p>Ohne diesen Zaehler waere "geschrieben wird nur, wenn sich etwas aendert" eine
     * Behauptung ohne Probe: Das Ergebnis ist in beiden Faellen dasselbe Bit, und nur die
     * Zahl der Schreibzuege unterscheidet sie. Im Spiel ist der Unterschied sichtbar -
     * jedes set markiert den Chunk als ungespeichert.
     */
    private static int schreibzuege;

    /**
     * Meldet die Blockdaten an dieser Stelle an.
     *
     * <p>Fuer eine REIFE Pflanze: {@code alter == hoechstalter}. Fuer eine unreife eine
     * kleinere Zahl. Wer nichts anmeldet, bekommt Blockdaten OHNE Alter - so wie Stein
     * und Erde sie haben.
     */
    static void alter(String welt, int x, int y, int z, int alter, int hoechstalter) {
        BLOCKDATEN.put(stelle(welt, x, y, z), reifbar(alter, hoechstalter));
    }

    /** Meldet eine Hoehlenranke an - bei ihr entscheiden die BEEREN und nicht das Alter. */
    static void beeren(String welt, int x, int y, int z, boolean hatBeeren, int alter,
                       int hoechstalter) {
        BLOCKDATEN.put(stelle(welt, x, y, z), ranke(hatBeeren, alter, hoechstalter));
    }

    private static String stelle(String welt, int x, int y, int z) {
        return welt + "/" + x + "/" + y + "/" + z;
    }

    /**
     * Blockdaten MIT Alter.
     *
     * <p>Ein Stellvertreter auf {@code Ageable} und kein echtes BlockData: Jenes gibt es
     * ohne Server nicht. Beantwortet werden die drei Methoden der Schnittstelle, alles
     * andere wirft - auch hier gilt, dass ein stilles null den Zweig danebenlaufen
     * liesse.
     */
    private static BlockData reifbar(int alter, int hoechstalter) {
        return (BlockData) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {Ageable.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getAge" -> alter;
                    case "getMaximumAge" -> hoechstalter;
                    case "toString" -> "Alterprobe " + alter + "/" + hoechstalter;
                    case "hashCode" -> alter * 31 + hoechstalter;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet Ageable." + methode.getName()
                                    + " nicht");
                });
    }

    /**
     * Blockdaten einer Hoehlenranke - Beeren UND Alter.
     *
     * <p>Beides, weil {@code CaveVines} wirklich beides ist: {@code Ageable} UND
     * {@code CaveVinesPlant}. Sein Alter ist die LAENGE der Ranke und nicht ihre Reife -
     * ein Zweig, der das Alter zuerst fragt, zahlt fuer eine Ranke ohne Beeren. Darum
     * traegt dieser Stellvertreter beide Schnittstellen: So kann eine Probe die
     * Reihenfolge wirklich pruefen.
     */
    private static BlockData ranke(boolean hatBeeren, int alter, int hoechstalter) {
        return (BlockData) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {CaveVines.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "hasBerries", "isBerries" -> hatBeeren;
                    case "getAge" -> alter;
                    case "getMaximumAge" -> hoechstalter;
                    case "toString" -> "Rankenprobe beeren=" + hatBeeren;
                    case "hashCode" -> Boolean.hashCode(hatBeeren);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet CaveVines." + methode.getName()
                                    + " nicht");
                });
    }

    /**
     * Blockdaten OHNE Alter - was Stein, Erde und ein Kuerbis haben.
     *
     * <p>Beantwortet nichts ausser sich selbst: Wer mehr fragt, soll es gesagt bekommen.
     * Fuer die Reifefrage ist genau das die richtige Antwort - kein Alter heisst "die
     * Marke ist die Bremse".
     */
    private static BlockData ohneAlter() {
        return (BlockData) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {BlockData.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "toString" -> "Blockdatenprobe ohne Alter";
                    case "hashCode" -> 17;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet BlockData." + methode.getName()
                                    + " nicht. Diese Stelle hat kein Alter angemeldet -"
                                    + " mit Serverprobe.alter(...) bekommt sie eines.");
                });
    }

    /** Raeumt Welten, Chunkdaten und den Schreibzaehler weg. */
    static void keineWeltenMehr() {
        WELTEN.clear();
        BLOCKDATEN.clear();
        BEHAELTER.clear();
        schreibzuege = 0;
    }

    /** Wie oft seit dem letzten Aufraeumen geschrieben wurde. */
    static int schreibzuege() {
        return schreibzuege;
    }

    /** Meldet einen Spieler an - und gibt ihm seinen Namen fuer die UUID mit. */
    static void anmelden(Spielerprobe spieler, String name) {
        ANWESEND.put(name, spieler.spieler());
        nenne(spieler.kennung(), name);
    }

    /** Raeumt die Anwesenden weg - gehoert in das Vorher jeder Probe, die sie nutzt. */
    static void niemandMehrDa() {
        ANWESEND.clear();
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
        return welt(schluessel, null);
    }

    /**
     * Eine Welt MIT Bloecken - fuer die Zweige, die den Boden lesen.
     *
     * <p><b>Die Bloecke kommen aus {@link Probewelt} und nicht aus einem dritten
     * Modell.</b> Jene Klasse ist die Blockquelle, die dieses Projekt fuer seine
     * Proben schon hat: Was nie gesetzt wurde, liefert sie aus derselben Tabelle,
     * aus der auch der Weltgenerator schreibt. Ein eigenes Blockdoppel hier waere
     * ein zweiter Ort fuer dieselbe Frage - und bei der naechsten Aenderung am
     * Stadtboden liefen die zwei auseinander.
     *
     * <p>Ohne Griff wirft {@code getBlockAt}, statt Luft zu liefern: Ein Zweig,
     * der den Boden liest, soll es gesagt bekommen und nicht stumm durch den
     * falschen Fall laufen.
     */
    static World welt(String schluessel, Weltgriff griff) {
        NamespacedKey name = NamespacedKey.fromString(schluessel);
        if (name == null) {
            throw new IllegalArgumentException("kein Weltschluessel: "
                    + schluessel);
        }
        World gebaut = (World) Proxy.newProxyInstance(
                Serverprobe.class.getClassLoader(),
                new Class<?>[] {World.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "key", "getKey" -> name;
                    case "getName" -> name.getKey();
                    case "getChunkAt" -> chunk(schluessel, (Integer) werte[0],
                            (Integer) werte[1]);
                    case "getBlockAt" -> block(griff, (World) eigen,
                            (Integer) werte[0], (Integer) werte[1],
                            (Integer) werte[2]);
                    case "getMinHeight" -> griffOderKlage(griff).unten();
                    case "getHighestBlockYAt" ->
                            griffOderKlage(griff).oben((Integer) werte[0],
                                    (Integer) werte[1]);
                    case "toString" -> "Weltprobe " + schluessel;
                    case "hashCode" -> schluessel.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet World."
                                    + methode.getName() + " nicht");
                });
        // Angemeldet, damit Bukkit.getWorld(schluessel) sie findet: Ein Zweig, der eine
        // Welt ueber ihren Schluessel SUCHT, laesst sich sonst nicht durchlaufen.
        WELTEN.put(schluessel, gebaut);
        return gebaut;
    }

    /**
     * Ein Chunk mit einem Datenbehaelter, der BESTEHT.
     *
     * <p>Mehr als {@code getPersistentDataContainer} fragt kein Zweig - und der Behaelter
     * muss derselbe bleiben: Eine Marke, die hineingeschrieben wird, soll beim naechsten
     * Lesen noch da sein. Genau das ist die Zusage, die der echte Chunk macht, indem er
     * sich mit der Welt speichert.
     */
    private static Chunk chunk(String welt, int x, int z) {
        PersistentDataContainer daten = BEHAELTER.computeIfAbsent(
                welt + "/" + x + "/" + z, wo -> behaelter());
        return (Chunk) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {Chunk.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getPersistentDataContainer" -> daten;
                    case "getX" -> x;
                    case "getZ" -> z;
                    case "getWorld" -> WELTEN.get(welt);
                    case "isLoaded" -> true;
                    case "toString" -> "Chunkprobe " + welt + " " + x + "/" + z;
                    case "hashCode" -> (welt.hashCode() * 31 + x) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet Chunk."
                                    + methode.getName() + " nicht");
                });
    }

    /**
     * Ein Datenbehaelter auf einer Tabelle - nur BYTE_ARRAY, und das mit Absicht.
     *
     * <p>Der echte Behaelter KOPIERT bei jedem Lesen und jedem Schreiben den ganzen Wert.
     * Dieser hier tut dasselbe ({@code clone}), und das ist der Grund, warum er nicht
     * einfach die Tabelle durchreicht: Gaebe er dasselbe Feld heraus, das er haelt, dann
     * schriebe eine Umsetzung, die das Feld aendert und das {@code set} vergisst, trotzdem
     * richtig - und im Spiel waere die Marke nach dem Neustart weg.
     *
     * <p>Jeder andere Datentyp wirft. Ein Bitfeld ist ein Bytefeld, und was ein Zweig
     * wirklich braucht, soll in dieser Liste stehen.
     */
    private static PersistentDataContainer behaelter() {
        Map<NamespacedKey, byte[]> tabelle = new LinkedHashMap<>();
        return (PersistentDataContainer) Proxy.newProxyInstance(
                Serverprobe.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "set" -> {
                        nurBytefeld(werte[1]);
                        tabelle.put((NamespacedKey) werte[0],
                                ((byte[]) werte[2]).clone());
                        schreibzuege++;
                        yield null;
                    }
                    case "get" -> {
                        nurBytefeld(werte[1]);
                        byte[] feld = tabelle.get((NamespacedKey) werte[0]);
                        yield feld == null ? null : feld.clone();
                    }
                    case "has" -> tabelle.containsKey((NamespacedKey) werte[0]);
                    case "remove" -> {
                        tabelle.remove((NamespacedKey) werte[0]);
                        yield null;
                    }
                    case "getKeys" -> Set.copyOf(tabelle.keySet());
                    case "isEmpty" -> tabelle.isEmpty();
                    case "getSize" -> tabelle.size();
                    case "toString" -> "Datenbehaelterprobe " + tabelle.keySet();
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet PersistentDataContainer."
                                    + methode.getName() + " nicht");
                });
    }

    private static void nurBytefeld(Object art) {
        if (art != PersistentDataType.BYTE_ARRAY) {
            throw new UnsupportedOperationException("Dieser Behaelter haelt nur"
                    + " BYTE_ARRAY, gefragt wurde " + art + ". Ein Bitfeld ist ein"
                    + " Bytefeld; wer einen anderen Typ braucht, traegt ihn hier ein.");
        }
    }

    /**
     * Ein Blockzustand - gerade so viel, wie {@code BlockMultiPlaceEvent} braucht.
     *
     * <p>Jenes Ereignis ist die einzige Quelle fuer die ZWEITE Haelfte einer Tuer, eines
     * Bettes und einer grossen Blume: Sein Konstruktor nimmt eine Liste von Zustaenden und
     * holt sich daraus den gesetzten Block ({@code states.get(0).getBlock()}). Ohne diesen
     * Stellvertreter liesse sich das Ereignis nicht bauen - und die Marke der zweiten
     * Haelfte waere eine Behauptung.
     *
     * @param welt die Welt, in der der Zustand liegt - dieselbe, aus der der Block kommt
     */
    static BlockState zustand(World welt, Weltgriff griff, int x, int y, int z) {
        Block block = block(griff, welt, x, y, z);
        return (BlockState) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {BlockState.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getBlock" -> block;
                    case "getWorld" -> welt;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "toString" -> "Zustandsprobe " + x + "/" + y + "/" + z;
                    case "hashCode" -> (x * 31 + y) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet BlockState."
                                    + methode.getName() + " nicht");
                });
    }

    /** Der Griff oder eine Klage mit Grund - nie stumm Luft. */
    private static Weltgriff griffOderKlage(Weltgriff griff) {
        if (griff == null) {
            throw new UnsupportedOperationException("Diese Welt hat keine Bloecke."
                    + " Der Zweig, den du durchlaeufst, liest den Boden - also"
                    + " gehoert ein Weltgriff in Serverprobe.welt(schluessel, griff),"
                    + " am besten eine Probewelt.");
        }
        return griff;
    }

    /**
     * Ein Block, der seine Art kennt und sie annimmt - mehr fragt kein Zweig.
     *
     * <p>{@code getType} liest aus dem Griff, {@code setType} schreibt dorthin
     * zurueck: Ein Zweig, der Boden legt, soll ihn danach auch wiederfinden.
     * Alles andere wirft; was ein Zweig wirklich braucht, soll in dieser Liste
     * stehen und nicht geraten sein.
     */
    private static Block block(Weltgriff griff, World welt, int x, int y, int z) {
        // Die Klage steht seit dem 6.11.2026 an den ZWEI Methoden, die Bloecke lesen, und
        // nicht mehr hier vorn: Ein Zweig, der nur Ort und Welt eines Blocks braucht -
        // etwa die Setzwache, die daraus einen Blockort baut -, bekam sonst eine Klage
        // ueber einen Boden, den er nie anfasst. Wer die ART eines Blocks fragt, bekommt
        // sie weiter.
        return (Block) Proxy.newProxyInstance(Serverprobe.class.getClassLoader(),
                new Class<?>[] {Block.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    // Die Welt gehoert zum Block: Ein Zweig, der einen Blockort
                    // baut, braucht ihren Schluessel - und zwar von DEM Block, den er in
                    // der Hand hat, nicht von einer zweiten Quelle.
                    case "getWorld" -> welt;
                    case "getType" -> griffOderKlage(griff).bei(x, y, z);
                    // Ohne Anmeldung: Blockdaten OHNE Alter. Das ist die ehrliche
                    // Antwort und kein null - Stein HAT Blockdaten, nur keine Reife.
                    case "getBlockData" -> BLOCKDATEN.getOrDefault(
                            stelle(welt.key().asString(), x, y, z), ohneAlter());
                    case "setType" -> {
                        griffOderKlage(griff).setze(x, y, z, (Material) werte[0]);
                        yield null;
                    }
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "toString" -> "Blockprobe " + x + "/" + y + "/" + z;
                    case "hashCode" -> (x * 31 + y) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Serverprobe beantwortet Block."
                                    + methode.getName() + " nicht");
                });
    }

    /**
     * Der anwesende Spieler mit dieser Kennung - oder {@code null}.
     *
     * <p>Durchsucht die Anwesenden, statt eine zweite Tabelle zu fuehren: Zwei Tabellen
     * ueber dieselbe Menge liefen auseinander, und dann faende getPlayerExact jemanden,
     * den getPlayer nicht kennt.
     */
    private static Player nachKennung(UUID gesucht) {
        for (Player einer : ANWESEND.values()) {
            if (gesucht.equals(einer.getUniqueId())) {
                return einer;
            }
        }
        return null;
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
                case "getPlayerExact" -> ANWESEND.get(String.valueOf(werte[0]));
                // getPlayer nimmt in Bukkit einen Namen ODER eine UUID, und
                // Mietwache.imTakt sucht nach der UUID des Mieters. Unterschieden
                // wird am TYP und nicht an der Laenge: Ein Spielername darf keine
                // 36 Zeichen haben, aber sich darauf zu verlassen waere eine
                // Zusage von Mojang und nicht von uns.
                case "getPlayer" -> werte[0] instanceof UUID gesucht
                        ? nachKennung(gesucht)
                        : ANWESEND.get(String.valueOf(werte[0]));
                case "getOnlinePlayers" -> List.copyOf(ANWESEND.values());
                // Ueber den Schluessel, nicht ueber den Namen: Chunkmarken sucht so,
                // und Bukkit.getWorld(NamespacedKey) landet hier.
                case "getWorld" -> WELTEN.get(String.valueOf(werte[0]));
                case "getWorlds" -> List.copyOf(WELTEN.values());
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
