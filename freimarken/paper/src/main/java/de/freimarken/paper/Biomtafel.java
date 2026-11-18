package de.freimarken.paper;

import de.freimarken.kern.Wolkenbiome;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Biome;

/**
 * Macht aus einem Biomnamen ein Biom - die eine Stelle, an der Minecrafts Verzeichnis
 * angefasst wird.
 *
 * <p>Welche Biome es gibt und wie sie heissen, steht im Kern ({@link Wolkenbiome}); dort
 * steht auch der Grund fuer jedes einzelne. Hier steht nur die Umwandlung, und zwar als
 * VERZWEIG und nicht als Verzeichnisgriff: Die Konstanten von {@link Biome} brauchen
 * einen laufenden Server, ein {@code switch} ueber Zeichenketten nicht. Deshalb kann eine
 * Probe jeden Namen pruefen, ohne Minecraft zu starten - und nur wer wirklich ein Biom
 * setzt, kommt hierher.
 *
 * <p><b>Jeder Name aus {@link Wolkenbiome#NAMEN} muss hier einen Zweig haben.</b> Keine
 * Verhaltensprobe kann das sehen (sie braeuchte den Server), darum haelt eine
 * Quelltext-Tafel in {@code GrenzeTest} beide Richtungen gegeneinander: ein Name ohne
 * Zweig waere ein Biom, das sich anbieten aber nicht setzen laesst, ein Zweig ohne Name
 * eine Zeile, die nie greift.
 */
final class Biomtafel {

    private Biomtafel() {
    }

    /**
     * Das Biom zu einem Namen - oder {@code null}, wenn die Tafel ihn nicht kennt.
     *
     * <p>{@code null} und kein Wurf: Gefragt wird mit Namen aus der Einstellungsdatei, und
     * der Aufrufer macht daraus eine Klage mit Pfad. Ein Wurf mitten im Lesen riss schon
     * einmal einen ganzen Serverstart mit.
     */
    static Biome zuBiom(String name) {
        return switch (Wolkenbiome.wieAbgelegt(name)) {
            case "plains" -> Biome.PLAINS;
            case "forest" -> Biome.FOREST;
            case "desert" -> Biome.DESERT;
            case "jungle" -> Biome.JUNGLE;
            case "savanna" -> Biome.SAVANNA;
            case "taiga" -> Biome.TAIGA;
            case "snowy_plains" -> Biome.SNOWY_PLAINS;
            case "swamp" -> Biome.SWAMP;
            case "mushroom_fields" -> Biome.MUSHROOM_FIELDS;
            case "cherry_grove" -> Biome.CHERRY_GROVE;
            default -> null;
        };
    }

    /**
     * Die ganze Tafel als Biome - die Farbtafel, die der Weltgenerator abgibt.
     *
     * <p>ALLE und nicht die eingestellten, und das ist die wichtigste Zeile dieser Klasse:
     * Ein {@code BiomeProvider} wird genau EINMAL beim Laden der Welt gefragt, welche
     * Biome er zurueckgeben kann. Was dort fehlt, laesst sich in dieser Welt nie setzen -
     * ein spaeter angebotenes Biom wirkte also erst, nachdem der Weltordner geloescht
     * wurde. Darum nennt diese Liste die Tafel und nicht die Einstellung.
     */
    static List<Biome> alle() {
        List<Biome> biome = new ArrayList<>(Wolkenbiome.NAMEN.size());
        for (String name : Wolkenbiome.NAMEN) {
            biome.add(zuBiom(name));
        }
        return List.copyOf(biome);
    }
}
