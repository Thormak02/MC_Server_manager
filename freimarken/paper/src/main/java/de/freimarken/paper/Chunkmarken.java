package de.freimarken.paper;

import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Die Blockmarken IM CHUNK - ein Bit je Blockposition, in der Weltdatei.
 *
 * <p><b>Warum nicht in der Datenbank</b>, und das ist die tragende Entscheidung dieses
 * Schrittes:
 * <ul>
 *   <li><b>Die Marke liegt in derselben Datei wie der Block.</b> Ein Weltzuruecksetzer
 *       nimmt beide mit - Kornmark wird bei jeder neuen Minecraft-Fassung neu erzeugt, und
 *       die Marken darin sollen genau dann verschwinden. Ein Absturz ebenso: Was der
 *       Server nicht gespeichert hat, hat weder Block noch Marke. Eine Marke in der
 *       Datenbank liefe nach jedem Rueckspielen einer Weltsicherung auseinander, und zwar
 *       stumm.</li>
 *   <li><b>Eine Zeile je Block waere nicht zu halten.</b> Gerechnet fuer eine
 *       Parzellenstadt: 18 bis 44 Millionen Zeilen und ein bis zwei Gigabyte - und jeder
 *       gesetzte Block ein Schreibzug ueber die EINE Verbindung, die dieses Projekt im
 *       Hauptfaden haelt. Der Zwischenspeicher davor ist gegen das Gegenteil gebaut: Er
 *       leert bei jedem Schreibzug alles, weil Schreibzuege selten sind.</li>
 *   <li><b>Ohne Datenbank laeuft es trotzdem.</b> Ein Server ohne {@code datenbank.url}
 *       vergisst Parzellen und Guthaben beim Neustart, die Marken aber nicht - sie liegen
 *       im Chunk. Das ist kein Zufall, sondern die Folge derselben Wahl.</li>
 * </ul>
 *
 * <p><b>Ein Schluessel je ABSCHNITT, nicht je Chunk.</b> Ein Chunk ist 16 mal 16 Bloecke
 * breit und bis zu 24 Abschnitte hoch; ein Abschnitt hat 4096 Bloecke, also 512 Byte
 * Bitfeld. Paper kopiert bei JEDEM Lesen und JEDEM Schreiben den ganzen Wert - ein Feld
 * fuer den ganzen Chunk waere 12 KB je Blockereignis. Je Abschnitt sind es 512 Byte, und
 * angelegt wird ein Abschnitt erst, wenn in ihm die erste Marke steht.
 *
 * <p><b>Kein Zwischenspeicher davor</b>, und das ist gemessen abgewaegt: Zwei Kopien von
 * 512 Byte je gesetztem Block sind Mikrosekunden und keine Netzrunde. Ein Zwischenspeicher
 * brauchte dagegen eine Rueckschreibregel samt Chunkentladen, und ein nicht
 * zurueckgeschriebenes Feld waere eine verlorene Marke - also Geld. Ein Speicher fuer eine
 * Last, die es noch nicht gibt, ist derselbe Fehler wie ein Index fuer eine Abfrage, die es
 * noch nicht gibt.
 *
 * <p><b>Geschrieben wird nur, wenn sich etwas aendert.</b> Steht das Bit schon, geht kein
 * {@code set} an den Chunk - sonst markierte jedes zweite Setzen den Chunk als
 * ungespeichert, und ein Spieler, der eine Mauer nachbessert, liesse den Server dauernd
 * schreiben.
 */
final class Chunkmarken implements Blockmarken {

    /**
     * Der Bereich der Schluessel in der Weltdatei - {@code freimarken:marken_4}.
     *
     * <p>Fest und nicht aus dem Plugin genommen ({@code new NamespacedKey(plugin, ...)}):
     * Der Schluessel steht in JEDER Weltdatei, und er darf sich nie aendern - auch dann
     * nicht, wenn das Plugin einmal anders heisst. Ausserdem braucht diese Klasse so keine
     * {@code JavaPlugin}-Instanz und ist ohne Server pruefbar.
     */
    static final String BEREICH = "freimarken";

    /** Die Vorsilbe des Schluessels; dahinter steht die Abschnittsnummer. */
    static final String VORSILBE = "marken_";

    /** 4096 Bloecke, ein Bit je Block - 512 Byte. */
    static final int FELDGROESSE = Blockort.BLOECKE_JE_ABSCHNITT / 8;

    /**
     * Wie aus einem Weltschluessel eine Welt wird.
     *
     * <p>Eine Naht und kein {@code Bukkit.getWorld} mitten in der Rechnung: Sonst waere
     * diese Klasse nur mit einem gestellten Server pruefbar, und die Bitrechnung - das
     * Einzige, was hier wirklich schiefgehen kann - bliebe ungeprueft.
     */
    interface Weltsucher {
        World weltZu(String schluessel);
    }

    /**
     * Die Fassung fuer den Betrieb - sie sucht die Welt bei Bukkit.
     *
     * <p>Ueber den SCHLUESSEL und nicht ueber den Namen: {@code Bukkit.getWorld(String)}
     * nimmt den Namen, und ein Weltschluessel ist nicht ihr Name - {@code getWorld("")}
     * fuer {@code minecraft:marktstadt} liefert null. Dieser Unterschied faellt nicht auf,
     * er antwortet nur immer null, und dann waere jede Marke ein Wurf.
     */
    static Chunkmarken ueberBukkit() {
        return new Chunkmarken(schluessel -> {
            NamespacedKey name = NamespacedKey.fromString(schluessel);
            return name == null ? null : Bukkit.getWorld(name);
        });
    }

    private final Weltsucher sucher;

    Chunkmarken(Weltsucher sucher) {
        if (sucher == null) {
            throw new IllegalArgumentException("Ohne Weltsucher gibt es keine Marken");
        }
        this.sucher = sucher;
    }

    @Override
    public boolean istMarkiert(Blockort ort) {
        byte[] feld = feld(behaelter(pflicht(ort)), ort.abschnittY());
        return feld != null && gesetzt(feld, ort.stelleImAbschnitt());
    }

    @Override
    public boolean markiere(Blockort ort) {
        PersistentDataContainer behaelter = behaelter(pflicht(ort));
        int abschnitt = ort.abschnittY();
        byte[] feld = feld(behaelter, abschnitt);
        if (feld != null && gesetzt(feld, ort.stelleImAbschnitt())) {
            return false;
        }
        byte[] neu = feld == null ? new byte[FELDGROESSE] : feld;
        setze(neu, ort.stelleImAbschnitt());
        behaelter.set(schluessel(abschnitt), PersistentDataType.BYTE_ARRAY, neu);
        return true;
    }

    /**
     * Mehrere Positionen - ein Schreibzug je Abschnitt.
     *
     * <p>Das ist der ganze Grund, aus dem der Vertrag diese Methode fuehrt: Ein Bett, eine
     * Tuer und eine grosse Blume liegen in EINEM Abschnitt, und der Standardweg der
     * Schnittstelle schriebe das 512-Byte-Feld zweimal.
     */
    @Override
    public int markiereAlle(List<Blockort> orte) {
        if (orte == null) {
            throw new IllegalArgumentException("Nichts zu markieren heisst eine leere"
                    + " Liste, nicht null");
        }
        // Nach Abschnitt gebuendelt, in der Reihenfolge des Eingangs - so bleibt die
        // Antwort bei gleichen Eingaben dieselbe.
        Map<Abschnitt, List<Blockort>> gebuendelt = new LinkedHashMap<>();
        for (Blockort ort : orte) {
            gebuendelt.computeIfAbsent(Abschnitt.von(pflicht(ort)),
                    wo -> new ArrayList<>()).add(ort);
        }
        int neue = 0;
        for (Map.Entry<Abschnitt, List<Blockort>> eintrag : gebuendelt.entrySet()) {
            neue += markiereBuendel(eintrag.getKey(), eintrag.getValue());
        }
        return neue;
    }

    private int markiereBuendel(Abschnitt wo, List<Blockort> orte) {
        PersistentDataContainer behaelter = behaelter(orte.getFirst());
        byte[] feld = feld(behaelter, wo.abschnittY());
        byte[] neu = feld == null ? new byte[FELDGROESSE] : feld;
        int neue = 0;
        for (Blockort ort : orte) {
            int stelle = ort.stelleImAbschnitt();
            if (!gesetzt(neu, stelle)) {
                setze(neu, stelle);
                neue++;
            }
        }
        if (neue > 0) {
            behaelter.set(schluessel(wo.abschnittY()), PersistentDataType.BYTE_ARRAY, neu);
        }
        return neue;
    }

    /** Welt, Chunk und Abschnitt - der Schluessel eines Bitfeldes. */
    private record Abschnitt(String welt, int chunkX, int chunkZ, int abschnittY) {
        static Abschnitt von(Blockort ort) {
            return new Abschnitt(ort.welt(), ort.chunkX(), ort.chunkZ(),
                    ort.abschnittY());
        }
    }

    /**
     * Der Behaelter des Chunks, in dem dieser Block liegt.
     *
     * <p>Der Chunk muss geladen sein, und im Blockereignis ist er das - dort ist eben
     * etwas passiert. {@code getChunkAt} LAEDT ihn andernfalls, und das ist hier die
     * richtige Antwort: Eine dritte Antwort "weiss nicht" koennte Teil E nicht benutzen,
     * und sie waere die unsichere Richtung - wer nicht nachsehen kann, duerfte nicht
     * zahlen.
     */
    private PersistentDataContainer behaelter(Blockort ort) {
        World welt = sucher.weltZu(ort.welt());
        if (welt == null) {
            // Unerreichbar aus einem Blockereignis - dort kommt der Ort aus einem Block,
            // der in dieser Welt liegt. Wer trotzdem hier landet, hat den Ort selbst
            // gebaut, und ein stilles false hiesse "schon markiert": Teil E zahlte dann
            // nie, und niemand wuesste warum.
            throw new IllegalStateException("Die Welt " + ort.welt() + " ist nicht"
                    + " geladen - fuer sie gibt es keine Blockmarken. Der Ort kommt"
                    + " nicht aus einem Blockereignis dieser Welt.");
        }
        Chunk chunk = welt.getChunkAt(ort.chunkX(), ort.chunkZ());
        return chunk.getPersistentDataContainer();
    }

    private byte[] feld(PersistentDataContainer behaelter, int abschnitt) {
        return behaelter.get(schluessel(abschnitt), PersistentDataType.BYTE_ARRAY);
    }

    /**
     * Der Schluessel eines Abschnitts.
     *
     * <p>Die Abschnittsnummer ist unter Null negativ ({@code marken_-4} fuer die tiefste
     * Blockschicht), und das ist erlaubt: Ein {@link NamespacedKey} nimmt Kleinbuchstaben,
     * Ziffern, Punkt, Bindestrich, Unterstrich und Schraegstrich.
     */
    private static NamespacedKey schluessel(int abschnitt) {
        return new NamespacedKey(BEREICH, VORSILBE + abschnitt);
    }

    private static boolean gesetzt(byte[] feld, int stelle) {
        return (feld[stelle >> 3] & (1 << (stelle & 7))) != 0;
    }

    private static void setze(byte[] feld, int stelle) {
        feld[stelle >> 3] |= (byte) (1 << (stelle & 7));
    }

    private static Blockort pflicht(Blockort ort) {
        if (ort == null) {
            throw new IllegalArgumentException("Ohne Ort gibt es keine Marke");
        }
        return ort;
    }
}
