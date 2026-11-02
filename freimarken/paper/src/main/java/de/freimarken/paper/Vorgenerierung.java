package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Grenzregel;
import io.papermc.paper.world.chunk.ChunkGenerationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Was eine Welt zum Vorgenerieren braucht - und wie weit sie ist.
 *
 * <p>Vorgeneriert wird mit <b>Chunky</b>, einem fremden Plugin
 * [entschieden 02.11.2026]. Diese Klasse erzeugt die Befehle dafuer und sieht nach, was
 * davon angekommen ist. Sie generiert selbst keinen einzigen Chunk.
 *
 * <p>Warum es sie trotzdem gibt, obwohl ein Fremdplugin die Arbeit macht: Chunky nimmt
 * Mitte und Radius als Zahlen, und die falschen Zahlen sieht man nicht. Die Marktstadt
 * liegt um -3,5/-3,5 - wer Mitte und Radius vom Spawn nimmt, laesst nach Plan Abschnitt 4
 * den ganzen Westring aus, und das faellt erst auf, wenn ein Spieler hinlaeuft und das
 * Gelaende waehrend des Spiels entsteht. Genau diese Zahlen rechnet {@link Chunkfeld} aus
 * der Grenze, die wirklich gesetzt ist.
 *
 * <p>Angestossen wird nie von selbst, sondern nur auf Befehl [entschieden 02.11.2026]:
 * Die Vorgenerierung laeuft Stunden und belegt Gigabyte. Ein Start, der das von sich aus
 * beginnt, ueberrascht den Betreiber zur schlechtesten denkbaren Zeit - beim ersten Start
 * nach einer neuen Welt.
 */
final class Vorgenerierung {

    private Vorgenerierung() {
    }

    /** Ein Punkt der Stichprobe mit dem, was dort wirklich auf der Platte liegt. */
    record Befund(Chunkfeld.Punkt punkt, ChunkGenerationState zustand) {
    }

    /**
     * Der Stand einer Welt.
     *
     * @param welt  der Schluesseltext der Welt, etwa {@code freimarken:marktstadt}
     * @param feld  das Chunkfeld aus ihrer gemessenen Grenze
     * @param probe neun Punkte und was dort liegt
     */
    record Stand(String welt, Chunkfeld feld, List<Befund> probe) {

        /** Ob die Stichprobe durchweg erzeugte Chunks gefunden hat. */
        boolean siehtVollstaendigAus() {
            return probe.stream().allMatch(b -> b.zustand() == ChunkGenerationState.GENERATED);
        }

        /** Wie viele der neun Punkte fertig erzeugt sind. */
        long fertig() {
            return probe.stream()
                    .filter(b -> b.zustand() == ChunkGenerationState.GENERATED)
                    .count();
        }

        /**
         * Ob Chunks da sind, aber aus einer aelteren Minecraft-Fassung.
         *
         * <p>Der Fall, auf den Plan Abschnitt 13 zielt: „Versionswechsel - Kornmark
         * zuruecksetzen, neuer Startwert, neu vorgenerieren." Veraltete Chunks sind das
         * Zeichen dafuer, und ohne diese Abfrage muesste man sich erinnern.
         *
         * <p>Nur fuer die Stichprobe, nicht fuer die ganze Welt: Dass neun Punkte
         * aktuell sind, heisst nicht, dass es alle sind - und umgekehrt.
         */
        boolean veraltet() {
            return probe.stream().anyMatch(b -> b.zustand() == ChunkGenerationState.OUTDATED);
        }

        /** Die Punkte, an denen etwas fehlt - kurz, mit Chunkkoordinate. */
        List<String> luecken() {
            return probe.stream()
                    .filter(b -> b.zustand() != ChunkGenerationState.GENERATED)
                    .map(b -> b.punkt().name() + " (" + b.punkt().chunkX() + "/"
                            + b.punkt().chunkZ() + "): " + b.zustand())
                    .toList();
        }
    }

    /**
     * Der Befehl, mit dem Chunky diese Welt vorgeneriert.
     *
     * <p>Alles in EINER Zeile, nicht in fuenf. Chunky kennt beide Wege - erst
     * {@code world}, {@code center}, {@code shape}, {@code radius} setzen und dann
     * {@code start}, oder alles an {@code start} uebergeben. Die fuenf Zeilen hinterlassen
     * einen Zustand, der zwischen zwei Welten stehenbleibt: Wer die vierte vergisst,
     * generiert mit dem Radius der vorigen Welt, und das meldet niemand.
     *
     * <p>Jedes Stueck der Zeile hat einen Grund, der nicht in ihr steht:
     *
     * <ul>
     *   <li><b>Der Weltname</b> ist der von Bukkit, nicht der Schluessel. Chunky gibt die
     *       Eingabe roh an {@code Server#getWorld(String)}; im Quelltext gibt es keine
     *       Behandlung von Doppelpunkten oder Namensraeumen. {@code freimarken:marktstadt}
     *       findet es also nur, wenn das zufaellig auch der Bukkit-Name ist - deshalb
     *       prueft {@link #chunkyName} das nach, statt es anzunehmen.
     *   <li><b>{@code square}</b> ist keine Zierde. Eine Weltgrenze ist ein Quadrat; ein
     *       Kreis gleichen Radius liesse die vier Ecken aus. Das sieht man weder am
     *       Fortschritt noch an der Platte, sondern erst, wenn ein Spieler in die Ecke
     *       laeuft.
     *   <li><b>Der Mittelpunkt</b> ist NICHT der Spawn. Die Marktstadt liegt um -3,5/-3,5,
     *       ihr Spawn in der Mitte von Parzelle 0/0 bei 32/32. Chunky liest den
     *       Mittelpunkt als Gleitkommazahl, also geht die halbe Zahl unveraendert durch -
     *       und damit ergibt sich genau der Radius 1.495, den Plan Abschnitt 4 nennt. Wer
     *       stattdessen den Spawn nimmt, braucht 1.530; wer dann 1.495 tippt, laesst den
     *       ganzen Westring aus.
     *   <li><b>Der Radius</b> ist der zum genannten Mittelpunkt und kommt aus der
     *       GEMESSENEN Grenze, nicht aus der Einstellungsdatei.
     * </ul>
     */
    static String chunkyBefehl(String weltname, Chunkfeld feld, double mitteX, double mitteZ) {
        return "/chunky start " + weltname + " square " + zahl(mitteX) + " " + zahl(mitteZ)
                + " " + feld.radiusUm(mitteX, mitteZ);
    }

    /**
     * Eine Zahl so, wie Chunky sie liest.
     *
     * <p>{@code Locale.ROOT} ist hier kein Feinschliff: Auf einem deutschen System
     * schriebe {@code String.valueOf} nichts Falsches, aber eine Formatierung mit
     * Vorgabesprache machte aus -3.5 ein "-3,5", und das liest Chunkys Zahlenleser nicht.
     * Ganze Werte ohne ".0", damit die Zeile lesbar bleibt.
     */
    private static String zahl(double wert) {
        return wert == Math.rint(wert) && Math.abs(wert) < 1e15
                ? String.valueOf((long) wert)
                : String.format(Locale.ROOT, "%s", wert);
    }

    /**
     * Der Name, unter dem Chunky diese Welt findet - oder {@code null}, wenn es keinen gibt.
     *
     * <p>Gemessen und nicht angenommen. Chunky loest Weltnamen roh ueber
     * {@code Server#getWorld(String)} auf, ohne jede Namensraumbehandlung. Die
     * Freimarken-Welten sind aber namensraeumig ({@code freimarken:marktstadt}), und was
     * {@code World#getName()} dafuer zurueckgibt, ist eine Eigenschaft des Servers, nicht
     * eine Zusage. Deshalb wird hier beides zusammengefuehrt: Der Name taugt nur, wenn
     * genau DIESE Welt wieder herauskommt. Sonst zeigt der Befehl keine Zeile zum
     * Kopieren, sondern sagt, dass es keine gibt - das ist die ehrlichere Antwort als ein
     * Befehl, der die falsche Welt vorgeneriert oder ins Leere laeuft.
     */
    static String chunkyName(World welt, Server server) {
        String name = welt.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return welt.equals(server.getWorld(name)) ? name : null;
    }

    /**
     * Ob diese Welt eine Grenze hat, hinter der etwas vorzugenerieren waere.
     *
     * <p>Vor jedem {@link #stand} zu fragen. Eine Welt ohne Grenze fuehrt das
     * Hoechstmass, und das sind rund 14 Billionen Chunks - die Stichprobe laege dann auf
     * der Weltkante bei 30 Millionen Bloecken, und die Zahl im Befehl waere eine
     * Beleidigung statt einer Auskunft.
     */
    static boolean hatGrenze(World welt) {
        return welt.getWorldBorder().getSize() < Grenzregel.HOECHSTMASS;
    }

    /**
     * Sieht nach, was von einer Welt schon erzeugt ist - ohne einen Chunk zu laden.
     *
     * <p>{@code getChunkGenerationStateAsync} liest nur den Kopf der Regionsdatei. Die
     * blockierende Schwester {@code isChunkGenerated} wuerde im Hauptfaden auf die Platte
     * warten, und bei 72 Punkten auf einem Netzlaufwerk sind das sichtbare Ruckler.
     */
    static CompletableFuture<Stand> stand(World welt) {
        Chunkfeld feld = feldVon(welt);
        List<Chunkfeld.Punkt> punkte = feld.stichprobe();
        List<CompletableFuture<Befund>> fragen = new ArrayList<>(punkte.size());
        for (Chunkfeld.Punkt punkt : punkte) {
            fragen.add(welt.getChunkGenerationStateAsync(punkt.chunkX(), punkt.chunkZ())
                    .thenApply(zustand -> new Befund(punkt, zustand)));
        }
        return CompletableFuture.allOf(fragen.toArray(CompletableFuture[]::new))
                .thenApply(nichts -> new Stand(welt.key().asString(), feld,
                        fragen.stream().map(CompletableFuture::join).toList()));
    }

    /**
     * Das Chunkfeld aus der GEMESSENEN Grenze.
     *
     * <p>Gemessen und nicht gerechnet: Was in der Einstellungsdatei steht, muss nicht
     * gelten - die Grenze wandert nur nach aussen, und ein kleinerer Wert in der Datei
     * landet nur im Protokoll. Vorzugenerieren ist aber, was wirklich eingeschlossen ist.
     */
    static Chunkfeld feldVon(World welt) {
        WorldBorder rand = welt.getWorldBorder();
        return Chunkfeld.ausGrenze(rand.getCenter().getX(), rand.getCenter().getZ(),
                rand.getSize());
    }
}
