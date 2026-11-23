package de.freimarken.paper;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Quader;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

/**
 * Gespeicherte Bauwerke laden, setzen und sichern - die dritte Bukkit-Naht Altfurts.
 *
 * <p><b>Warum es diese Naht gibt</b> [23.11.2026]: Der gerechnete Bauplan hat genau das
 * erzeugt, was ein gerechneter Bauplan erzeugt - neun gleichfoermige Haeuser. Schoene
 * mittelalterliche Haeuser werden von HAND gebaut. Was dabei bisher fehlte, war die
 * Haltbarkeit: Ein von Hand gesetztes Haus ueberlebt keinen Weltverlust. Diese Naht
 * schliesst beides zusammen - was von Hand steht, wird in eine Datei gesichert, und die
 * Datei wird beim naechsten Start wieder gesetzt.
 *
 * <p><b>Kein eigener NBT-Leser</b>: Paper hat die Schnittstelle schon
 * ({@link StructureManager}), und sie liest genau das Format, in das auch der
 * Strukturblock des Spiels und WorldEdit ({@code //schem save structure}) schreiben. Ein
 * zweiter Leser waere eine zweite Wahrheit ueber ein Dateiformat, das Mojang pflegt.
 *
 * <p><b>Die Naht ist noetig, weil nichts davon ohne Server geht</b>: {@code loadStructure}
 * liest eine Datei ueber den Server, {@code place} setzt Bloecke und Blockwesen. Dieselbe
 * Lage wie beim {@code Truhengriff} der Wolkeninsel und beim {@code Feingriff} fuer Tuer
 * und Schild.
 *
 * <p><b>Hereingegeben werden NAMEN, ein Quader und GRADE</b> - keine Datei, keine
 * {@code Location} und keine {@code StructureRotation}. Alles andere zoege Bukkit in die
 * Geometrie des Kerns.
 */
interface Strukturgriff {

    /** Der Ordner unter dem Serververzeichnis, in dem die Bauwerke liegen. */
    String ORDNER = "bauten";

    /**
     * Die Groesse der gespeicherten Struktur - oder {@code null}, wenn es sie nicht gibt.
     *
     * <p>{@code null} und kein Wurf: Keine Datei zu haben ist der Normalfall. Dann baut
     * der gerechnete Bauplan.
     *
     * @return drei Zahlen - Breite (X), Hoehe (Y), Tiefe (Z), ungedreht
     */
    int[] groesse(String name);

    /**
     * Setzt die Struktur in diesen Quader, um {@code grad} im Uhrzeigersinn gedreht.
     *
     * @return wie viele Bloecke die Struktur umfasst, oder 0 wenn nichts gesetzt wurde
     */
    int setze(String name, Quader bereich, int grad);

    /**
     * Sichert diesen Quader als Struktur unter diesem Namen.
     *
     * @return eine Klage als Text, oder {@code null} bei Erfolg
     */
    String sichere(String name, Quader bereich);

    /** Der Griff auf die echte Welt. */
    record AufDerWelt(World welt, StructureManager verwaltung, File ordner)
            implements Strukturgriff {

        /**
         * Die Datei zu einem Namen - und KEIN Pfad aus dem Namen.
         *
         * <p>Der Name kommt aus der {@code config.yml}, also von einem Menschen. Ein
         * {@code ../} darin fuehrte sonst aus dem Ordner heraus - dieselbe Falle wie beim
         * Weltnamen, der einmal ein {@code shutil.rmtree} in den Nachbarordner geschickt
         * hat. {@link de.freimarken.kern.Platzhalterbau} laesst nur Kleinbuchstaben,
         * Ziffern, Bindestrich und Unterstrich zu; hier wird es ein zweites Mal
         * sichergestellt, weil diese Klasse eine DATEI anfasst.
         */
        private File datei(String name) {
            String sauber = name.trim().toLowerCase(Locale.ROOT);
            if (!sauber.matches(de.freimarken.kern.Platzhalterbau.MUSTER)) {
                return null;
            }
            return new File(ordner, sauber + ".nbt");
        }

        @Override
        public int[] groesse(String name) {
            Structure struktur = lade(name);
            if (struktur == null) {
                return null;
            }
            var masse = struktur.getSize();
            return new int[]{masse.getBlockX(), masse.getBlockY(), masse.getBlockZ()};
        }

        private Structure lade(String name) {
            File datei = datei(name);
            if (datei == null || !datei.isFile()) {
                return null;
            }
            try {
                return verwaltung.loadStructure(datei);
            } catch (IOException | IllegalArgumentException nichtLesbar) {
                // Kein Wurf: Eine kaputte Datei ist eine Fehleingabe des Betreibers, und
                // der gerechnete Bauplan springt dafuer ein. Gemeldet wird es vom
                // Aufrufer, der den Betreiber kennt.
                return null;
            }
        }

        @Override
        public int setze(String name, Quader bereich, int grad) {
            Structure struktur = lade(name);
            if (struktur == null) {
                return 0;
            }
            var masse = struktur.getSize();
            // DIE ECKE WANDERT MIT DER DREHUNG: Minecraft dreht um die gesetzte
            // Stelle, und die Struktur waechst danach in eine andere Richtung. Ohne
            // Ausgleich stuende ein gedrehtes Haus neben seinem Quader.
            //
            // Gerechnet wird der Ausgleich im KERN, wo Proben die vier gemessenen
            // Faelle halten; hier wird nur die Groesse der Datei hereingegeben.
            int breite = masse.getBlockX();
            int tiefe = masse.getBlockZ();
            int x = Bauplan.setzstelleX(bereich, grad, breite, tiefe);
            int z = Bauplan.setzstelleZ(bereich, grad, breite, tiefe);
            Location stelle = new Location(welt, x, bereich.vonY(), z);
            struktur.place(stelle, true, drehung(grad), Mirror.NONE, 0, 1.0f,
                    new Random());
            return masse.getBlockX() * masse.getBlockY() * masse.getBlockZ();
        }

        @Override
        public String sichere(String name, Quader bereich) {
            File datei = datei(name);
            if (datei == null) {
                return "\"" + name + "\" ist kein Name, aus dem eine Datei werden darf.";
            }
            try {
                if (!ordner.isDirectory() && !ordner.mkdirs()) {
                    return "Der Ordner " + ordner + " liess sich nicht anlegen.";
                }
                Structure struktur = verwaltung.createStructure();
                // DIE ZWEITE ECKE IST AUSSCHLIESSEND - am laufenden Server GEMESSEN
                // [23.11.2026]: Ohne das +1 kam ein Quader von 11x14x7 als Struktur von
                // 10x13x6 heraus, bei jeder Groesse genau eine Ebene zu wenig je Achse.
                // Nicht Luft war der Grund (die Datei enthielt 525 Luftbloecke von 780),
                // sondern dass fill die Groesse als DIFFERENZ der beiden Stellen nimmt.
                // Eine fehlende Ebene ist die halbe Wand eines Hauses.
                struktur.fill(new Location(welt, bereich.vonX(), bereich.vonY(),
                                bereich.vonZ()),
                        new Location(welt, bereich.bisX() + 1, bereich.bisY() + 1,
                                bereich.bisZ() + 1), true);
                verwaltung.saveStructure(datei, struktur);
                return null;
            } catch (IOException | IllegalArgumentException nichtSchreibbar) {
                return "\"" + name + "\" liess sich nicht sichern: "
                        + nichtSchreibbar.getMessage();
            }
        }

        /** Die Bukkit-Drehung zu einer Gradzahl - die EINE Stelle dieser Zuordnung. */
        private static StructureRotation drehung(int grad) {
            return switch (grad) {
                case 90 -> StructureRotation.CLOCKWISE_90;
                case 180 -> StructureRotation.CLOCKWISE_180;
                case 270 -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            };
        }
    }
}
