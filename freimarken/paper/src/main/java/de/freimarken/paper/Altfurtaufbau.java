package de.freimarken.paper;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Bautenverzeichnis;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Stadtplan;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;

/**
 * Setzt die Platzhalterbauten in Altfurt - die Hand, die dort die Haeuser macht.
 *
 * <p>Bis zum 23.11.2026 war das Handarbeit, und TODO.md sagte es woertlich: "Programm
 * setzt keine Bloecke in Altfurt". Der Mangel daran ist nicht die Muehe, sondern die
 * Haltbarkeit: Ein von Hand gesetztes Haus ueberlebt keinen Weltverlust, und keine Probe
 * haelt, dass der Quader in der {@code config.yml} zu dem passt, was wirklich steht.
 *
 * <p><b>Geschrieben wird der GANZE Quader, auch die Luft darin</b> - dieselbe tragende
 * Entscheidung wie bei {@code Wolkenaufbau}: Damit sind Aufsetzen und Zuruecksetzen
 * dieselbe Hand. Wer nur die Schale setzte und das Innere ausliesse, hinterliesse beim
 * zweiten Lauf, was vorher dort stand - und ein Haus saehe anders aus als das andere.
 *
 * <p>Die Bloecke gehen OHNE Physik in die Welt ({@code Weltgriff#setze}): Bei neun
 * Haeusern sind das fast sechstausend Bloecke, und wenn jeder davon seine Nachbarn
 * benachrichtigt, steht der Server.
 *
 * <p>Der {@code Weltgriff} kommt JE AUFRUF und nicht als Feld - wie bei
 * {@code Wolkenaufbau}: Die Welt kann beim Start gefehlt haben und spaeter da sein, und
 * ein gemerkter Griff auf eine entladene Welt waere ein Griff ins Leere.
 */
final class Altfurtaufbau {

    /**
     * Tuer und Schild setzen - die zweite Bukkit-Naht dieser Klasse.
     *
     * <p>Getrennt vom {@link Weltgriff}, weil es etwas anderes ist: Jener setzt einen
     * Block, dies setzt einen Block MIT DATEN und beschreibt ein Blockwesen. Eine Tuer
     * traegt eine Richtung, eine Haelfte und ein Scharnier; ein Schild traegt Zeilen.
     *
     * <p>Und sie MUSS eine Naht sein - dieselbe Lage wie beim {@code Truhengriff} der
     * Wolkeninsel: {@code Material.createBlockData()} geht ueber
     * {@code Bukkit.getServer()} und wirft ohne laufenden Server. Waere sie keine Naht,
     * liesse sich das Setzen von keiner Probe durchlaufen.
     *
     * <p>Hereingegeben werden eine {@link Richtung}, ein {@link Material} und ZEILEN
     * als Text - keine Blockdaten und keine fertigen Bauteile. Dieselbe Linie wie beim
     * {@code Truhengriff}, der Namen und Anzahlen nimmt: Alles andere zoege Bukkit in
     * die Geometrie des Kerns.
     */
    interface Feingriff {

        /**
         * Setzt eine Tuer: BEIDE Haelften, bei {@code y} die untere.
         *
         * <p>Zwei Bloecke und nicht einer: Eine halbe Tuer ist nichts, was Minecraft
         * stehen laesst. Ohne Physik, wie alles hier - mit Nachbarschaftsmeldungen fiele
         * sie, noch waehrend die Wand daneben entsteht.
         */
        void setzeTuer(int x, int y, int z, Richtung zeigt, Material art);

        /** Haengt ein Wandschild auf und beschreibt seine Vorderseite. */
        void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                List<String> zeilen);

        /**
         * Setzt eine Dachtreppe, die in diese Richtung BERGAUF steigt.
         *
         * <p><b>Warum das eine eigene Naht braucht</b> [23.11.2026]: Der
         * {@link Weltgriff} setzt einen Block OHNE Blockdaten. Eine Treppe bekommt dann
         * die Vorgabe {@code facing=north} - auf den Bildern des ersten Versuchs zeigten
         * ALLE Dachtreppen nach Norden, und eine der zwei Dachseiten lag damit falsch
         * herum. Das war der groesste Mangel der ersten Fassung.
         *
         * <p>Die Richtung zeigt zum FIRST, an Mojangs eigenen Daechern gemessen: Dort
         * traegt die Traufenreihe im Norden {@code facing=south}.
         */
        void setzeDachtreppe(int x, int y, int z, Richtung bergauf, Material art);

        /**
         * Setzt eine Glasscheibe, die sich nach BEIDEN Seiten dieser Achse verbindet.
         *
         * <p>Ohne Blockdaten steht eine Scheibe als Pfosten mitten im Wandfeld, statt die
         * Fuge zu fuellen - dieselbe Lage wie bei der Dachtreppe, und derselbe Grund: Der
         * Weltgriff setzt einen Block, keine Blockdaten, und Physik waehrend des Bauens
         * laesst Tuer und Schild fallen.
         *
         * <p>{@code achse} ist EINE Richtung; die zweite Seite ist ihre Gegenrichtung.
         */
        void setzeFensterscheibe(int x, int y, int z, Richtung achse, Material art);

        /**
         * Legt einen Balken auf diese Achse.
         *
         * <p>Ohne Blockdaten liegt ein Balken mit {@code axis=y} - am Server gemessen.
         * Die geschaelte Laengstextur laeuft dann senkrecht ueber einen waagerechten
         * Balken, und das Band sieht aus wie gestapelte Pfosten.
         */
        void setzeBalkenachse(int x, int y, int z, Richtung achse, Material art);

        /**
         * Setzt eine Leiter, die in diese Richtung ZEIGT.
         *
         * <p>Eine Leiter ohne Blockdaten haengt an der Vorgabeseite - also an der
         * falschen Wand. Dieselbe Falle wie bei Dachtreppe, Glasscheibe und liegendem
         * Balken, und darum derselbe Weg: eine eigene Naht.
         *
         * @param zeigt die Richtung, in die der Kletterer schaut - VON der Wand weg
         */
        void setzeLeiter(int x, int y, int z, Richtung zeigt, Material art);

        /** Der Griff auf die echte Welt. */
        record AufDerWelt(World welt) implements Feingriff {

            @Override
            public void setzeTuer(int x, int y, int z, Richtung zeigt, Material art) {
                // JE HAELFTE EIN EIGENES Blockdatum und nicht zweimal dasselbe Objekt:
                // setBlockData kopiert nicht zwingend, und eine geteilte Fassung haette
                // am Ende zwei obere Haelften.
                BlockData unten = haelfte(art, zeigt, Bisected.Half.BOTTOM);
                BlockData oben = haelfte(art, zeigt, Bisected.Half.TOP);
                if (unten == null || oben == null) {
                    // Kein Wurf: Der Leser hat den Block schon geprueft und beklagt. Das
                    // Haus steht dann, nur die Tuer nicht - und ein Wurf hier liesse den
                    // Betreiber mit einem halben Altfurt und einer Ausnahme im Protokoll
                    // stehen.
                    return;
                }
                welt.getBlockAt(x, y, z).setBlockData(unten, false);
                welt.getBlockAt(x, y + 1, z).setBlockData(oben, false);
            }

            private static BlockData haelfte(Material art, Richtung zeigt,
                    Bisected.Half haelfte) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof Door tuer)) {
                    return null;
                }
                tuer.setFacing(blockseite(zeigt));
                tuer.setHalf(haelfte);
                // LINKS und zu: Ein Platzhalter mit offener Tuer sieht aus wie einer, in
                // dem jemand war.
                tuer.setHinge(Door.Hinge.LEFT);
                tuer.setOpen(false);
                return tuer;
            }

            @Override
            public void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                    List<String> zeilen) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof WallSign wandschild)) {
                    return;
                }
                wandschild.setFacing(blockseite(zeigt));
                Block block = welt.getBlockAt(x, y, z);
                block.setBlockData(wandschild, false);
                if (!(block.getState() instanceof Sign schild)) {
                    // Kann nur vorkommen, wenn zwischen dem Setzen und dieser Zeile etwas
                    // anderes dort steht - dieselbe Lage wie bei der Truhe der Insel.
                    return;
                }
                var seite = schild.getSide(Side.FRONT);
                for (int zeile = 0; zeile < zeilen.size() && zeile < SCHILDZEILEN;
                        zeile++) {
                    seite.line(zeile, Component.text(zeilen.get(zeile)));
                }
                // force=true, applyPhysics=false: Das Schild haengt an der Wand, und eine
                // Physikmeldung liesse es fallen, bevor der Nachbarblock steht.
                schild.update(true, false);
            }

            @Override
            public void setzeDachtreppe(int x, int y, int z, Richtung bergauf,
                    Material art) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof Stairs treppe)) {
                    // Kein Wurf: Wer in der Dachart einen Block eintraegt, der keine
                    // Treppe ist, bekommt ein flaches Dach und keine Ausnahme im
                    // Protokoll. Der Weltgriff hat den Block schon gesetzt.
                    return;
                }
                treppe.setFacing(blockseite(bergauf));
                // UNTEN und GERADE: Die obere Haelfte waere ein Vordach, und eine Ecke
                // (inner/outer) entsteht nur dort, wo zwei Daecher sich treffen - das
                // tun die zwei Seiten eines Satteldachs nie.
                treppe.setHalf(Bisected.Half.BOTTOM);
                treppe.setShape(Stairs.Shape.STRAIGHT);
                welt.getBlockAt(x, y, z).setBlockData(treppe, false);
            }

            @Override
            public void setzeFensterscheibe(int x, int y, int z, Richtung achse,
                    Material art) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof MultipleFacing scheibe)) {
                    // Kein Wurf: Wer in der Palette ein Fenster eintraegt, das keine
                    // Scheibe ist (etwa Glas als Vollblock), bekommt eben einen
                    // Vollblock. Der Weltgriff hat ihn schon gesetzt.
                    return;
                }
                // BEIDE Seiten der Achse: Eine Scheibe, die nur nach einer Seite
                // verbunden ist, endet mitten in der Fuge.
                scheibe.setFace(blockseite(achse), true);
                scheibe.setFace(blockseite(Bauplan.gegenrichtung(achse)), true);
                welt.getBlockAt(x, y, z).setBlockData(scheibe, false);
            }

            @Override
            public void setzeLeiter(int x, int y, int z, Richtung zeigt, Material art) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof Directional leiter)) {
                    // Kein Wurf, dieselbe Linie wie bei der Tuer: Das Haus steht dann,
                    // nur die Leiter nicht.
                    return;
                }
                leiter.setFacing(blockseite(zeigt));
                welt.getBlockAt(x, y, z).setBlockData(leiter, false);
            }

            @Override
            public void setzeBalkenachse(int x, int y, int z, Richtung achse,
                    Material art) {
                BlockData daten = art.createBlockData();
                if (!(daten instanceof Orientable balken)) {
                    // Kein Wurf: Wer in der Palette einen Balken eintraegt, der keine
                    // Achse hat (etwa Bruchstein), bekommt eben einen Block ohne Achse.
                    return;
                }
                balken.setAxis(achse == Richtung.OST || achse == Richtung.WEST
                        ? Axis.X : Axis.Z);
                welt.getBlockAt(x, y, z).setBlockData(balken, false);
            }

            /** Die Bukkit-Seite zu einer Richtung - die EINE Stelle dieser Zuordnung. */
            private static BlockFace blockseite(Richtung zeigt) {
                return switch (zeigt) {
                    case NORD -> BlockFace.NORTH;
                    case OST -> BlockFace.EAST;
                    case SUED -> BlockFace.SOUTH;
                    case WEST -> BlockFace.WEST;
                };
            }
        }
    }

    /** So viele Zeilen traegt ein Schild - mehr nimmt Minecraft nicht an. */
    static final int SCHILDZEILEN = 4;

    private final Baustoffe stoffe;

    Altfurtaufbau(Baustoffe stoffe) {
        if (stoffe == null) {
            throw new IllegalArgumentException("Ohne Baustoffe gibt es keinen Bau");
        }
        this.stoffe = stoffe;
    }

    Baustoffe stoffe() {
        return stoffe;
    }

    /**
     * Ob dieser Bau schon steht - gefragt an EINEM Block.
     *
     * <p>Gefragt wird die SOCKELecke eine Ebene ueber dem Boden und NICHT der Boden selbst:
     * Altfurt ist eine Ebene, dort liegt auf Bodenhoehe ueberall schon etwas. Der Boden
     * koennte also nie zwischen "gebaut" und "unberuehrt" unterscheiden, die Wand sehr
     * wohl - ueber dem Boden einer Ebene ist Luft.
     *
     * <p><b>Es ist EIN Block, und das ist die Grenze dieser Frage</b>: Ein Haus, dem
     * jemand die Rueckwand abgetragen hat, gilt hier als stehend. Das ist die gewollte
     * Richtung - der naechste Start soll nicht ueber die Einrichtung eines Betreibers
     * hinwegbauen. Wer neu setzen will, nimmt diesen einen Block weg.
     */
    boolean stehtSchon(Platzhalterbau bau, Weltgriff griff) {
        if (bau == null || griff == null) {
            throw new IllegalArgumentException("Ohne Bau und Welt gibt es keine Antwort");
        }
        Quader bereich = bau.bereich();
        return griff.bei(bereich.vonX(), bereich.vonY() + 1, bereich.vonZ())
                == stoffe.block(bau.stil(), bau.dach(), Bauplan.Stueck.SOCKEL);
    }

    /** Welchen Weg ein Bau genommen hat. */
    enum Weg {
        /** Eine gespeicherte Struktur wurde gesetzt - Handarbeit, die ueberlebt. */
        STRUKTUR,
        /** Es gab keine Datei: Der gerechnete Bauplan hat gebaut. */
        GERECHNET
    }

    /**
     * Was beim Bauen herauskam.
     *
     * @param klage eine Zeile fuer den Betreiber, oder {@code null} - sie kommt, wenn es
     *              eine Datei GIBT, sie aber nicht passt: Dann baut der Rechenweg, und
     *              der Betreiber muss erfahren, warum seine Handarbeit nicht steht
     */
    record Ergebnis(Weg weg, int bloecke, String klage) {
    }

    /**
     * Baut diesen Bau - aus der gespeicherten Struktur, wenn es eine gibt, sonst
     * gerechnet.
     *
     * <p><b>Die Datei gewinnt</b>, und das ist der ganze Sinn dieser Fassung: Der
     * gerechnete Bauplan erzeugt neun gleichfoermige Haeuser; ein von Hand gebautes Haus
     * sieht aus wie ein Haus. Der Rechenweg ist seitdem der RUECKFALL - er haelt die
     * Stadt am Leben, bis jemand ein Haus gebaut und gesichert hat.
     *
     * <p><b>Welche Datei</b> entscheidet {@link Bauplan#dateilage}: die eigene oder die
     * Vorlage, ungedreht oder gedreht. Beides aus einer Hand, denn beides haengt
     * zusammen.
     *
     * <p>Passt die Struktur nicht in den Quader, wird sie NICHT gesetzt: Ein Haus, das
     * ueber seinen Quader hinausragt, steht in der Nachbarwand oder im Ankunftsweg - und
     * genau dagegen ist jeder Quader geprueft. Stattdessen rechnet der Bauplan, und die
     * Klage nennt beide MaÃŸe.
     */
    Ergebnis baue(Platzhalterbau bau, Weltgriff griff, Feingriff fein,
            List<String> zeilen, Strukturgriff strukturen) {
        if (bau == null || griff == null || fein == null || strukturen == null) {
            throw new IllegalArgumentException("Ohne Bau, Welt, Feingriff und"
                    + " Strukturgriff gibt es nichts zu bauen");
        }
        // WELCHE Datei und WIE gedreht kommt aus EINER Hand - eine eigene Datei steht
        // in der Weltlage und dreht sich nicht, eine Vorlage steht in der Normlage.
        Bauplan.Dateilage lage = Bauplan.dateilage(bau);
        int[] masse = strukturen.groesse(lage.datei());
        if (masse == null) {
            return new Ergebnis(Weg.GERECHNET, setze(bau, griff, fein, zeilen), null);
        }
        String klage = Bauplan.passtNicht(bau, masse[0], masse[1], masse[2]);
        if (klage != null) {
            return new Ergebnis(Weg.GERECHNET, setze(bau, griff, fein, zeilen),
                    bau.schluessel() + ": " + klage + " Es wurde gerechnet statt"
                            + " gesetzt.");
        }
        int gesetzt = strukturen.setze(lage.datei(), bau.bereich(), lage.grad());
        if (gesetzt <= 0) {
            return new Ergebnis(Weg.GERECHNET, setze(bau, griff, fein, zeilen),
                    bau.schluessel() + ": die Datei liess sich nicht setzen - es wurde"
                            + " gerechnet statt gesetzt.");
        }
        return new Ergebnis(Weg.STRUKTUR, gesetzt, null);
    }

    /**
     * Was beim Sichern herauskam.
     *
     * @param gesichert die Namen, deren Quader in eine Datei geschrieben wurden
     * @param schonDa   die Namen, die schon eine Datei HABEN und darum ohne
     *                  Bestaetigung nicht ueberschrieben wurden
     * @param klagen    je Bau eine Zeile, wenn das Schreiben scheiterte
     * @param unbekannt der genannte Name, den es nicht gibt - sonst {@code null}
     */
    record Sicherung(List<String> gesichert, List<String> schonDa, List<String> klagen,
            String unbekannt) {
    }

    /**
     * Schreibt die Quader Altfurts in Strukturdateien - alle oder einen.
     *
     * <p><b>Der Gegenweg zu {@link #baue}</b>: Dort gewinnt die Datei, hier entsteht sie.
     * Wer ein Haus von Hand in den Quader baut, macht es damit haltbar; beim naechsten
     * Start wird es gesetzt statt gerechnet.
     *
     * <p><b>Eine vorhandene Datei wird NICHT ohne Bestaetigung ueberschrieben.</b> Das ist
     * der einzige Schritt an Altfurt, den nichts heilt: Ein gerechnetes Haus kann jederzeit
     * neu gerechnet werden, eine von Hand gebaute Datei nicht wiederhergestellt werden.
     * Steht die Datei schon und fehlt die Bestaetigung, kommt der Name in
     * {@code schonDa} - und der Aufrufer sagt dem Betreiber, wie er sie trotzdem
     * ueberschreibt. Dieselbe Linie wie bei {@code /freimarken besitz freigeben}.
     *
     * <p>Ein unbekannter Name ist KEINE stille Nichtbehandlung: Er kommt als
     * {@code unbekannt} zurueck, damit die Antwort die vorhandenen Namen nennen kann. Ein
     * Tippfehler, der nichts tut und nichts sagt, sieht aus wie ein stummer Fehlschlag.
     *
     * @param name {@code null} sichert alle Bauten des Verzeichnisses
     */
    Sicherung sichere(Bautenverzeichnis verzeichnis, Strukturgriff strukturen, String name,
            boolean bestaetigt) {
        if (verzeichnis == null || strukturen == null) {
            throw new IllegalArgumentException("Ohne Verzeichnis und Strukturgriff gibt es"
                    + " nichts zu sichern");
        }
        List<Platzhalterbau> welche;
        if (name == null) {
            welche = verzeichnis.alle();
        } else {
            Platzhalterbau einer = verzeichnis.mitNamen(name);
            if (einer == null) {
                return new Sicherung(List.of(), List.of(), List.of(), name);
            }
            welche = List.of(einer);
        }
        List<String> gesichert = new ArrayList<>();
        List<String> schonDa = new ArrayList<>();
        List<String> klagen = new ArrayList<>();
        for (Platzhalterbau bau : welche) {
            if (!bestaetigt && strukturen.groesse(bau.schluessel()) != null) {
                schonDa.add(bau.schluessel());
                continue;
            }
            String klage = strukturen.sichere(bau.schluessel(), bau.bereich());
            if (klage != null) {
                klagen.add(klage);
            } else {
                gesichert.add(bau.schluessel());
            }
        }
        return new Sicherung(List.copyOf(gesichert), List.copyOf(schonDa),
                List.copyOf(klagen), null);
    }

    /**
     * Was beim Pflastern herauskam.
     *
     * @param bloecke       wie viele Bloecke gesetzt wurden
     * @param uebersprungen wie viele Bloecke unberuehrt blieben, weil dort Handarbeit
     *                      steht - die Zahl gehoert in den Bericht, denn eine unerwartet
     *                      grosse Zahl heisst, dass ein Quader im Weg liegt
     */
    record Pflasterung(long bloecke, long uebersprungen) {
    }

    /**
     * Pflastert den Boden Altfurts und setzt die Stadtmauer.
     *
     * <p><b>Das ist der Unterschied zwischen neun Haeusern und einer STADT</b>: Bis zum
     * 23.11.2026 setzte dieses Programm Bauten auf eine leere Ebene - der Betreiber
     * nannte sie "viel zu klein und zu dicht beieinander" und verlangte "eine ganze
     * Stadt, mit Strassen usw.". Strassen sind nicht Haeuser, die anders stehen, sondern
     * BODEN, der anders aussieht. Woher welcher Boden kommt, rechnet
     * {@link Stadtplan#lageBei}.
     *
     * <p><b>Gesetzt wird NUR die Bodenebene</b> - und auf der Mauer so viele Ebenen, wie
     * {@link Stadtplan#mauerhoehe} nennt. Altfurt ist {@code art: flach}: Ueber dem Boden
     * ist Luft, und wer sie ueberschriebe, loeschte ein Haus, das darauf steht.
     *
     * <p><b>Die unberuehrten Quader sind die Handarbeit</b>: die neun Bauten der
     * {@code config.yml}, die Ladeneinheiten und die Torwand. Ihr Boden gehoert ihnen -
     * das Pflaster laeuft darum herum. Hereingegeben werden sie als Liste und nicht hier
     * geholt: Diese Klasse kennt die {@code config.yml} nicht, und der Aufrufer weiss,
     * was es an Handarbeit gibt.
     *
     * <p>Ohne Physik, wie alles hier: Dreissigtausend Bloecke, die ihre Nachbarn
     * benachrichtigen, halten den Server an.
     */
    Pflasterung pflastere(Stadtplan plan, Pflasterstoffe pflaster, Weltgriff griff,
            List<Quader> unberuehrt) {
        if (plan == null || pflaster == null || griff == null || unberuehrt == null) {
            throw new IllegalArgumentException("Ohne Plan, Pflaster, Welt und die Liste"
                    + " der unberuehrten Quader gibt es nichts zu pflastern");
        }
        Quader umriss = plan.umriss();
        long gesetzt = 0L;
        long uebersprungen = 0L;
        for (int x = umriss.vonX(); x <= umriss.bisX(); x++) {
            for (int z = umriss.vonZ(); z <= umriss.bisZ(); z++) {
                Stadtplan.Lage lage = plan.lageBei(x, z);
                // DIE STELLE GEHOERT ZUR FRAGE: Das Pflaster ist eine Mischung,
                // und welcher ihrer Bloecke hier liegt, haengt an x und z.
                Material boden = pflaster.block(lage, x, z);
                if (boden == null) {
                    // Jenseits der Mauer: Dort hat der Weltgenerator gearbeitet.
                    continue;
                }
                // Die Bodenebene immer, die Mauer so hoch wie der Plan sagt.
                int ebenen = Math.max(1, plan.mauerhoehe(x, z));
                for (int y = Stadtplan.BODEN_Y; y < Stadtplan.BODEN_Y + ebenen; y++) {
                    if (liegtIn(unberuehrt, x, y, z)) {
                        uebersprungen++;
                        continue;
                    }
                    griff.setze(x, y, z, boden);
                    gesetzt++;
                }
            }
        }
        return new Pflasterung(gesetzt, uebersprungen);
    }

    /**
     * Was auf der Platzkante gewachsen und gegraben wurde.
     *
     * @param baeume   wie viele Baeume stehen
     * @param brunnen  wie viele Brunnen stehen
     * @param bloecke  wie viele Bloecke dafuer gesetzt wurden
     */
    record Platzkante(int baeume, int brunnen, long bloecke) {
    }

    /**
     * Ob das Gruen der Platzkante schon steht - gefragt am ERSTEN Standort.
     *
     * <p><b>Warum es diese Frage gibt</b> [24.11.2026]: Ohne sie setzt jeder Start die
     * Baeume neu. Wer einen faellt, findet ihn nach dem naechsten Neustart wieder - und
     * wer einen umpflanzt, findet zwei. Dieselbe Grenze wie bei
     * {@link #istGepflastert} und {@link #stehtSchon}: Wer das Gruen neu setzen will,
     * nimmt den Stamm am ersten Standort weg.
     *
     * <p><b>Gefragt wird die ganze MISCHUNG</b> und nicht ihr Block an dieser Stelle -
     * aus demselben Grund wie beim Pflaster: Wer die Baumart umstellt, hat trotzdem eine
     * bepflanzte Platzkante.
     *
     * <p>Der erste Standort wird GESUCHT und nicht gerechnet: Wo er liegt, haengt an der
     * Streuung, und eine zweite Rechnung davon liefe irgendwann auseinander.
     */
    boolean stehtPlatzgruen(Stadtplan plan, Platzgruen gruen, Weltgriff griff) {
        if (plan == null || gruen == null || griff == null) {
            throw new IllegalArgumentException("Ohne Plan, Gruen und Welt gibt es keine"
                    + " Antwort");
        }
        if (!gruen.stehtEtwas()) {
            // NICHTS ZU SETZEN heisst: Es steht, was stehen soll. Sonst liefe jeder
            // Start ueber den ganzen Platz, um nichts zu tun.
            return true;
        }
        Quader umriss = plan.umriss();
        for (int x = umriss.vonX(); x <= umriss.bisX(); x++) {
            for (int z = umriss.vonZ(); z <= umriss.bisZ(); z++) {
                Stadtplan.Kantenbau was = Stadtplan.kantenbauBei(x, z);
                if (was == Stadtplan.Kantenbau.KEINER) {
                    continue;
                }
                Material dort = griff.bei(x, Stadtplan.BODEN_Y + 1, z);
                return was == Stadtplan.Kantenbau.BRUNNEN && gruen.brunnenMoeglich()
                        ? dort == gruen.wasser()
                        : gruen.stamm().contains(dort);
            }
        }
        // KEIN STANDORT: Dann gibt es nichts zu setzen, und es steht alles.
        return true;
    }

    /**
     * Setzt die Baeume und Brunnen auf die Platzkante.
     *
     * <p><b>NACH dem Pflaster</b>: Der Stamm steht AUF dem Boden, und wer erst den Baum
     * setzt und dann pflastert, pflastert ihm den Fuss weg. Die Standorte kommen aus
     * {@link Stadtplan#kantenbauBei} - diese Klasse entscheidet nicht, WO etwas steht,
     * sondern nur, WORAUS.
     *
     * <p><b>Ohne Physik, wie alles hier</b>: Ein Laubblock, der seine Nachbarn
     * benachrichtigt, sucht einen Stamm in Reichweite und zerfaellt, wenn er ihn nicht
     * findet - gesetzt wird von innen nach aussen, und waehrend des Setzens hat das Laub
     * am Rand noch keinen. Mit {@code setze} faellt das weg.
     *
     * <p><b>Das Wasser ist ein Quellblock in einer Fassung</b>: Der Rand steht auf allen
     * vier Seiten, also fliesst es nicht. Ohne Rand wuerde es sich ueber den ganzen
     * Marktplatz ergiessen - darum setzt diese Methode einen Brunnen nur, wenn beides
     * eingetragen ist, und sonst an seiner Stelle einen Baum.
     */
    Platzkante setzePlatzgruen(Stadtplan plan, Platzgruen gruen, Weltgriff griff,
            List<Quader> unberuehrt) {
        if (plan == null || gruen == null || griff == null || unberuehrt == null) {
            throw new IllegalArgumentException("Ohne Plan, Gruen, Griff und Liste der"
                    + " Handarbeit kann auf der Platzkante nichts wachsen");
        }
        if (!gruen.stehtEtwas()) {
            return new Platzkante(0, 0, 0L);
        }
        int baeume = 0;
        int brunnen = 0;
        long bloecke = 0L;
        Quader umriss = plan.umriss();
        for (int x = umriss.vonX(); x <= umriss.bisX(); x++) {
            for (int z = umriss.vonZ(); z <= umriss.bisZ(); z++) {
                Stadtplan.Kantenbau was = Stadtplan.kantenbauBei(x, z);
                if (was == Stadtplan.Kantenbau.KEINER) {
                    continue;
                }
                // EIN BRUNNEN OHNE RAND WIRD EIN BAUM: Die Stelle bleibt besetzt, aber
                // sie bleibt nicht leer. Ein Loch mitten in der Baumreihe sieht aus wie
                // ein vergessener Standort.
                if (was == Stadtplan.Kantenbau.BRUNNEN && gruen.brunnenMoeglich()) {
                    bloecke += setzeBrunnen(gruen, griff, unberuehrt, x, z);
                    brunnen++;
                } else {
                    bloecke += setzeBaum(gruen, griff, unberuehrt, x, z);
                    baeume++;
                }
            }
        }
        return new Platzkante(baeume, brunnen, bloecke);
    }

    /**
     * Ein Baum: ein Stamm und eine Laubkrone darum.
     *
     * <p>Die Krone ist keine Kugel, sondern vier Ebenen: zwei weite um die oberen
     * Stammbloecke, eine engere darueber und ein Hut. Die Ecken der weiten Ebenen bleiben
     * frei - ein voller Quader Laub sieht aus wie eine Kiste.
     *
     * <p>Die HOEHE streut um die eingetragene: Zwei gleich hohe Baeume nebeneinander
     * verraten das Programm.
     */
    private long setzeBaum(Platzgruen gruen, Weltgriff griff, List<Quader> unberuehrt,
            int x, int z) {
        int hoehe = gruen.baumhoehe()
                + Stadtplan.mischung(x, z, 3) - 1;
        int fuss = Stadtplan.BODEN_Y + 1;
        long gesetzt = 0L;
        for (int ebene = 0; ebene < hoehe; ebene++) {
            gesetzt += setzeWenn(griff, unberuehrt, x, fuss + ebene, z,
                    waehle(gruen.stamm(), x, fuss + ebene, z));
        }
        int krone = fuss + hoehe - 2;
        for (int ebene = 0; ebene < 4; ebene++) {
            // DIE WEITE KOMMT AUS DEM KERN: Aus ihr folgt der Abstand der Standorte,
            // und eine zweite Zahl hier liesse die Kronen verwachsen, sobald eine von
            // beiden sich aendert.
            int weite = switch (ebene) {
                case 0, 1 -> Stadtplan.KRONE_WEITE;
                case 2 -> Stadtplan.KRONE_WEITE - 1;
                default -> 0;
            };
            for (int dx = -weite; dx <= weite; dx++) {
                for (int dz = -weite; dz <= weite; dz++) {
                    // DIE ECKEN DER WEITEN EBENEN BLEIBEN FREI.
                    if (weite == Stadtplan.KRONE_WEITE
                            && Math.abs(dx) == Stadtplan.KRONE_WEITE
                            && Math.abs(dz) == Stadtplan.KRONE_WEITE) {
                        continue;
                    }
                    // DER STAMM BLEIBT STAMM.
                    if (dx == 0 && dz == 0 && ebene < 3) {
                        continue;
                    }
                    int y = krone + ebene;
                    gesetzt += setzeWenn(griff, unberuehrt, x + dx, y, z + dz,
                            waehle(gruen.laub(), x + dx, y, z + dz));
                }
            }
        }
        return gesetzt;
    }

    /**
     * Ein Brunnen: eine Fassung aus acht Randbloecken, zwei Ebenen hoch, mit Wasser
     * darin.
     *
     * <p>Das Wasser liegt auf der UNTEREN Ebene, der Rand geht eine hoeher - so sieht man
     * in den Brunnen hinein, statt auf eine Pfuetze. Die Fassung ist auf allen vier
     * Seiten geschlossen; nur darum bleibt das Wasser, wo es ist.
     */
    private long setzeBrunnen(Platzgruen gruen, Weltgriff griff, List<Quader> unberuehrt,
            int x, int z) {
        int fuss = Stadtplan.BODEN_Y + 1;
        long gesetzt = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    gesetzt += setzeWenn(griff, unberuehrt, x, fuss, z, gruen.wasser());
                    continue;
                }
                for (int ebene = 0; ebene < 2; ebene++) {
                    gesetzt += setzeWenn(griff, unberuehrt, x + dx, fuss + ebene, z + dz,
                            waehle(gruen.brunnenrand(), x + dx, fuss + ebene, z + dz));
                }
            }
        }
        return gesetzt;
    }

    /** Setzt den Block, wenn dort keine Handarbeit steht - und sagt, ob er gesetzt wurde. */
    private long setzeWenn(Weltgriff griff, List<Quader> unberuehrt, int x, int y, int z,
            Material block) {
        if (liegtIn(unberuehrt, x, y, z)) {
            return 0L;
        }
        griff.setze(x, y, z, block);
        return 1L;
    }

    /** Ein Block aus einer Mischung, fest aus der Stelle gewaehlt. */
    private static Material waehle(List<Material> mischung, int x, int y, int z) {
        return mischung.get(Stadtplan.mischung(x, z + y * 31, mischung.size()));
    }

    /** Ob diese Stelle in einem der Quader liegt, die unberuehrt bleiben. */
    private static boolean liegtIn(List<Quader> quader, int x, int y, int z) {
        for (Quader einer : quader) {
            if (einer.enthaelt(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ob die Stadt schon gepflastert ist - gefragt an EINEM Block.
     *
     * <p>Gefragt wird die MITTE des Marktes und nicht ein Rand: Der Rand liegt bei einem
     * anderen Quartierzuschnitt woanders, die Mitte liegt immer dort. Dieselbe Grenze wie
     * bei {@link #stehtSchon}: Wer neu pflastern will, nimmt diesen einen Block weg.
     *
     * <p><b>Gefragt wird die ganze MISCHUNG und nicht ihr Block an dieser Stelle</b>:
     * Wer die Mischung des Marktes umstellt, hat trotzdem ein gepflastertes Altfurt -
     * und wer von einer aelteren Fassung mit einem einzelnen Stoff kommt, soll nicht
     * ploetzlich dreissigtausend Bloecke neu gesetzt bekommen, nur weil die Streuung
     * an dieser einen Stelle jetzt einen anderen Block waehlt.
     *
     * <p><b>Und die LAGE wird gefragt, nicht angenommen</b> [24.11.2026]: Hier stand
     * {@code mischung(Lage.MARKT)} - die Mitte des Marktes WAR Marktpflaster. Seit dort
     * die vier Kreuzarme zusammentreffen, ist sie Fahrbahn, und die Frage haette von da
     * an bei jedem Start "nein" geantwortet: dreissigtausend Bloecke neu, jedes Mal,
     * und jede Aenderung eines Spielers am Pflaster wieder zugeschuettet. Wer die Lage
     * FRAGT, bleibt richtig, was auch immer dort einmal liegen wird.
     */
    boolean istGepflastert(Stadtplan plan, Pflasterstoffe pflaster, Weltgriff griff) {
        if (plan == null || pflaster == null || griff == null) {
            throw new IllegalArgumentException("Ohne Plan, Pflaster und Welt gibt es"
                    + " keine Antwort");
        }
        Stadtplan.Lage lage = plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z);
        return pflaster.mischung(lage).contains(
                griff.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z));
    }

    /**
     * Setzt diesen Bau - den ganzen Quader, Luft eingerechnet, dann Tuer und Schild.
     *
     * <p><b>Die Reihenfolge ist Teil der Sache</b>: Erst die Schale, dann die Tuer, dann
     * das Schild. Die Schale schreibt an der Tuerstelle Luft und an der Schildstelle
     * auch - wer die Tuer vorher setzte, haette sie danach weggeraeumt. Dieselbe
     * Begruendung wie bei der Truhe der Wolkeninsel: "Die Truhe wird erst zur Truhe, wenn
     * ihr Block steht."
     *
     * @param zeilen was auf dem Schild steht - leer heisst: kein Schild
     * @return wie viele Bloecke gesetzt wurden; Tuer und Schild zaehlen mit
     */
    int setze(Platzhalterbau bau, Weltgriff griff, Feingriff fein,
            List<String> zeilen) {
        if (bau == null || griff == null || fein == null) {
            throw new IllegalArgumentException("Ohne Bau, Welt und Feingriff gibt es"
                    + " nichts zu setzen");
        }
        Quader bereich = bau.bereich();
        int gesetzt = 0;
        for (int x = bereich.vonX(); x <= bereich.bisX(); x++) {
            for (int z = bereich.vonZ(); z <= bereich.bisZ(); z++) {
                for (int y = bereich.vonY(); y <= bereich.bisY(); y++) {
                    Material block = stoffe.block(bau.stil(), bau.dach(),
                            Bauplan.stueckBei(bau, x, y, z));
                    griff.setze(x, y, z, block);
                    gesetzt++;
                    // DIE DACHTREPPE BEKOMMT IHRE RICHTUNG gleich danach, nicht in einem
                    // zweiten Durchgang: Sie liegt auf demselben Block, den der Weltgriff
                    // eben gesetzt hat - und erst der Block macht die Treppe zur Treppe.
                    // Dieselbe Reihenfolge wie bei Tuer und Schild.
                    Richtung bergauf = Bauplan.dachzeigt(bau, x, y, z);
                    if (bergauf != null) {
                        fein.setzeDachtreppe(x, y, z, bergauf, block);
                    }
                    Richtung achse = Bauplan.fensterachse(bau, x, y, z);
                    if (achse != null) {
                        fein.setzeFensterscheibe(x, y, z, achse, block);
                    }
                    Richtung liegend = Bauplan.balkenachse(bau, x, y, z);
                    if (liegend != null) {
                        fein.setzeBalkenachse(x, y, z, liegend, block);
                    }
                    Richtung leiter = Bauplan.leiterzeigt(bau, x, y, z);
                    if (leiter != null) {
                        fein.setzeLeiter(x, y, z, leiter, block);
                    }
                }
            }
        }
        Bauplan.Stelle tuer = Bauplan.tuer(bau);
        if (tuer != null && stoffe.tuer() != Material.AIR) {
            fein.setzeTuer(tuer.x(), tuer.y(), tuer.z(), tuer.zeigt(), stoffe.tuer());
            gesetzt += 2;
        }
        Bauplan.Stelle schild = Bauplan.schild(bau);
        if (schild != null && !zeilen.isEmpty() && stoffe.schild() != Material.AIR) {
            fein.setzeSchild(schild.x(), schild.y(), schild.z(), schild.zeigt(),
                    stoffe.schild(), zeilen);
            gesetzt++;
        }
        return gesetzt;
    }
}
