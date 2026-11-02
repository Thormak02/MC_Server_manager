package de.freimarken.paper;

import de.freimarken.kern.Grenzregel;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Legt die Welten aus der Liste an und setzt ihre Grenzen.
 *
 * <p>Die Reihenfolge der Datei bleibt die Reihenfolge der Erzeugung. Das ist kein Zufall:
 * Die Marktstadt steht oben, und wenn an ihr etwas schiefgeht, faellt es auf, bevor
 * Minuten in die uebrigen Welten geflossen sind.
 *
 * <p>Die Grenze der Marktstadt setzt diese Klasse NICHT. Sie rundet auf ganze Rasterzellen
 * und liefert die {@code Stadtgrenze}, aus der die Parzellennummern kommen - das bleibt im
 * Plugin, wo auch das Besitzbuch entsteht. Hier steht die Grenze fuer alle anderen, und
 * dafuer gilt dieselbe Regel ohne die Rundung.
 */
final class Weltenbau {

    /**
     * Der Schluessel, unter dem jede Welt sich selbst ausweist.
     *
     * <p>Liegt im {@code PersistentDataContainer} der Welt und damit in ihrer level.dat -
     * er ueberlebt also den Neustart, und genau darum geht es.
     */
    private static final String AUSWEIS = "ausweis";

    private final JavaPlugin plugin;
    private final MarktstadtGenerator raster;
    private final Logger protokoll;

    Weltenbau(JavaPlugin plugin, MarktstadtGenerator raster, Logger protokoll) {
        this.plugin = plugin;
        this.raster = raster;
        this.protokoll = protokoll;
    }

    /**
     * Was beim Bauen herauskam.
     *
     * @param welten die Welten unter ihrem Namen; wer nicht entstehen konnte, fehlt
     * @param neu    welche davon Freimarken zum ersten Mal gesehen hat - nur die bekommen
     *               einen Erscheinungspunkt und einen Grenzmittelpunkt gesetzt, sonst
     *               ueberschriebe jeder Start, was der Betreiber mit /setworldspawn und
     *               /worldborder center eingestellt hat
     */
    record Ergebnis(Map<String, World> welten, Set<String> neu) {
    }

    /** Legt alle Welten an, die es noch nicht gibt, und setzt die Grenzen. */
    Ergebnis baueAlle(List<Weltangabe> angaben) {
        Map<String, World> welten = new LinkedHashMap<>();
        Set<String> neu = new LinkedHashSet<>();
        for (Weltangabe angabe : angaben) {
            try {
                baueEine(angabe, welten, neu);
            } catch (RuntimeException fehler) {
                // Eine Welt reisst die anderen nicht mit - und vor allem nicht den Rest
                // des Starts. Flog hier etwas, kam onEnable nie bis zur Schutzwache: Die
                // Marktstadt stand geladen da, voller Parzellen, und jeder durfte
                // ueberall bauen. Sieben Welten weiter unten in der Liste reichen dafuer.
                protokoll.log(Level.SEVERE, "Welt " + angabe.name() + " konnte nicht"
                        + " angelegt werden - uebersprungen.", fehler);
            }
        }
        return new Ergebnis(welten, neu);
    }

    private void baueEine(Weltangabe angabe, Map<String, World> welten, Set<String> neu) {
        long begonnen = System.currentTimeMillis();
        World welt = baue(angabe);
        if (welt == null) {
            protokoll.severe("Welt " + angabe.name() + " konnte nicht angelegt werden.");
            return;
        }
        if (welt.getEnvironment() != angabe.umgebung()) {
            // Lesbar, sobald die Welt geladen ist, und sonst nirgends: Die Umgebung steht
            // in ihrer level.dat, und WorldCreator.environment() wird fuer eine
            // bestehende Welt stillschweigend uebergangen. Ohne diese Zeile glaubte das
            // Plugin an einen Nether, der eine Oberwelt ist.
            protokoll.severe("Welt " + angabe.name() + " ist " + welt.getEnvironment()
                    + ", die Liste sagt " + angabe.umgebung() + " - die Umgebung einer"
                    + " bestehenden Welt ist nicht mehr zu aendern. Es gilt, was in der"
                    + " Welt steht.");
        }
        boolean frisch = frischUndAusgewiesen(welt, angabe);
        welten.put(angabe.name(), welt);
        if (frisch) {
            neu.add(angabe.name());
        }
        protokoll.info("Welt " + welt.key().asString() + " bereit in "
                + (System.currentTimeMillis() - begonnen) + " ms (" + angabe.art()
                + ", " + angabe.umgebung() + (frisch ? ", neu angelegt" : "") + ")");
        if (angabe.art() != Weltangabe.Art.RASTER) {
            setzeGrenze(welt, angabe, frisch);
        }
    }

    /**
     * Vergleicht den Ausweis, den die Welt traegt, mit der Liste - und sagt, ob Freimarken
     * sie heute zum ersten Mal sieht.
     *
     * <p>Warum ein Ausweis in der Welt und nicht {@code getWorld(...) == null}: Diese
     * Welten stehen in keiner Serverliste, sie entstehen hier. Vor dem Anlegen gibt es
     * sie also bei JEDEM Start nicht, und "neu" hiesse "jeder Start" - der
     * Erscheinungspunkt, den der Betreiber mit {@code /setworldspawn} gesetzt hat, waere
     * nach jedem Neustart wieder weg.
     *
     * <p>Welten, die vor dem 02.11.2026 entstanden sind, tragen keinen Ausweis. Die
     * gelten einmalig als neu und bekommen ihn dabei; ein von Hand gesetzter
     * Erscheinungspunkt geht also genau einmal verloren, nicht bei jedem Start.
     */
    private boolean frischUndAusgewiesen(World welt, Weltangabe angabe) {
        NamespacedKey schluessel = new NamespacedKey(plugin, AUSWEIS);
        var daten = welt.getPersistentDataContainer();
        String stand = daten.get(schluessel, PersistentDataType.STRING);
        String soll = angabe.art().name();
        if (stand == null) {
            daten.set(schluessel, PersistentDataType.STRING, soll);
            return true;
        }
        if (!stand.equals(soll)) {
            // Mehr als klagen ist von hier aus nicht moeglich: Der Ausweis liegt in der
            // level.dat und ist erst lesbar, wenn die Welt geladen IST. Den teuren Fall -
            // die Marktstadt als gewoehnliche Welt - fange deshalb nicht ich ab, sondern
            // Weltenliste, bevor hier eine Welt angefasst wird.
            protokoll.severe("Welt " + angabe.name() + " wurde als " + stand + " angelegt,"
                    + " die Liste sagt " + soll + ". Was im Boden liegt, bleibt wie es"
                    + " ist; neue Chunks folgen der Liste. Entweder den Eintrag"
                    + " zuruecknehmen oder die Welt neu anlegen.");
        }
        return false;
    }

    private World baue(Weltangabe angabe) {
        NamespacedKey schluessel = new NamespacedKey(plugin, angabe.name());
        World vorhanden = plugin.getServer().getWorld(schluessel);
        if (vorhanden != null) {
            return vorhanden;
        }
        WorldCreator bauplan = WorldCreator.ofKey(schluessel)
                .environment(angabe.umgebung());
        switch (angabe.art()) {
            case RASTER -> bauplan.generator(raster)
                    .biomeProvider(raster.getDefaultBiomeProvider(null))
                    .generateStructures(false);
            case LEER -> bauplan.generator(new Leere()).generateStructures(false);
            // Gewoehnlich heisst: gar nichts angeben. Jeder eigene Generator schaltet das
            // Gelaende ab, das hier ausdruecklich gewollt ist - Erze, Baeume, Tiere.
            case GEWOEHNLICH -> bauplan.generateStructures(true);
            default -> throw new IllegalStateException("Unbekannte Art: " + angabe.art());
        }
        return bauplan.createWorld();
    }

    /**
     * Setzt die Grenze einer gewoehnlichen Welt - nach derselben Regel wie die Marktstadt.
     *
     * <p>Bei jedem Start neu, aus demselben Grund: Die Grenze steht nicht in der level.dat,
     * sondern in einer eigenen Datei unter {@code data/minecraft/world_border.dat}. Ein
     * Sicherungsmuster, das sie dort nicht sucht, findet sie nicht - und eine
     * wiederhergestellte Welt haette gar keine.
     */
    private void setzeGrenze(World welt, Weltangabe angabe, boolean frisch) {
        WorldBorder rand = welt.getWorldBorder();
        if (!angabe.hatGrenze()) {
            // Auch das ist eine Auskunft, und ohne sie fehlte zu dieser Welt jede Zeile:
            // Wer sich bei kante vertippt hat, liest die Klage von Weltenliste und sucht
            // hier, was daraus geworden ist.
            protokoll.info("Weltgrenze " + welt.key().asString() + ": keine eingetragen -"
                    + " es bleibt, was in der Welt steht (" + (long) rand.getSize() + ").");
            return;
        }
        if (!Grenzregel.brauchbar(angabe.kante())) {
            // Soll Weltenliste abgefangen haben. Ein Riegel, kein Rueckfall: Eine
            // unbrauchbare Kante an setSize zu geben, wirft - und zwar mitten im Start.
            protokoll.severe("welten." + angabe.name() + ".kante ist " + angabe.kante()
                    + " und damit keine Grenze - es wird keine gesetzt.");
            return;
        }
        double inDerWelt = rand.getSize();
        boolean bestaetigt = Grenzregel.sprungText(angabe.kante(), inDerWelt)
                .equals(angabe.sprungBestaetigt());
        long wirksam = Grenzregel.wirksam(angabe.kante(), inDerWelt,
                Grenzregel.MINDESTKANTE, angabe.kante(), bestaetigt);
        String klage = Grenzregel.klage("welten." + angabe.name(), angabe.kante(),
                inDerWelt, wirksam, Grenzregel.MINDESTKANTE);
        if (klage != null) {
            protokoll.severe(klage);
        }
        if (frisch) {
            // Nur bei einer frischen Welt. Die Kante wird nie kleiner - aber ein
            // verschobener Mittelpunkt schiebt die Grenze genauso nach innen, und wer ihn
            // mit /worldborder center gesetzt hat, hat das so gewollt.
            rand.setCenter(0.0, 0.0);
        }
        rand.setSize(wirksam);
        // Eine Mauer, keine Falle: Wer heute weit draussen ausloggt, soll beim naechsten
        // Einloggen nicht am Grenzschaden sterben.
        rand.setDamageAmount(0.0);
        protokoll.info("Weltgrenze " + welt.key().asString() + ": Kante " + wirksam
                + " um " + (long) rand.getCenter().getX() + "/"
                + (long) rand.getCenter().getZ());
        holeErscheinungspunktHerein(welt, rand, frisch);
    }

    /**
     * Zieht den Erscheinungspunkt einer frisch angelegten Welt hinter die Grenze.
     *
     * <p>Minecraft sucht ihn irgendwo um 0/0 und weiss nichts von einer Grenze, die es
     * beim Suchen noch nicht gab. Bei einer kleinen Welt kann er damit draussen liegen -
     * der erste Spieler erschiene ausserhalb seiner eigenen Welt.
     *
     * <p>Nur bei einer frischen Welt, aus demselben Grund wie der Mittelpunkt: Spaeter
     * ist der Erscheinungspunkt die Entscheidung des Betreibers.
     */
    private void holeErscheinungspunktHerein(World welt, WorldBorder rand, boolean frisch) {
        if (!frisch || rand.isInside(welt.getSpawnLocation())) {
            return;
        }
        Location mitte = rand.getCenter();
        int x = mitte.getBlockX();
        int z = mitte.getBlockZ();
        welt.setSpawnLocation(x, welt.getHighestBlockYAt(x, z) + 1, z);
        protokoll.info("Erscheinungspunkt von " + welt.key().asString() + " lag ausserhalb"
                + " der Grenze - jetzt bei " + x + "/" + z + ".");
    }

    /**
     * Leere - fuer Wolkenwerder, dessen Startinsel mit Schritt 11 kommt.
     *
     * <p>Alle Schalter aus, und keine Methode ueberschrieben, die Bloecke setzt. Was
     * entsteht, ist nichts; wer dort erscheint, faellt. Deshalb steht Wolkenwerder in der
     * ausgelieferten Liste noch nicht drin.
     */
    private static final class Leere extends ChunkGenerator {

        @Override
        public boolean shouldGenerateNoise(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures(WorldInfo welt, Random zufall, int x, int z) {
            return false;
        }
    }
}
