package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rueckweggrenzen;
import de.freimarken.kern.SpeicherBlockbuch;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /freimarken blockbuch zurueck} - jede Absage, jede Reihenfolge, beide Stufen.
 *
 * <p><b>Die wichtigste Probe dieser Datei ist
 * {@link #bestaetigungszeilePasstWoertlichInDenBaum}</b>, und zwar weil sie eine Naht
 * haelt, die keine Tafel sehen kann: Die Vorschau GIBT eine Befehlszeile VOR, und wenn
 * ein Literal im Baum umbenannt wird, gibt sie eine Zeile vor, die es nicht mehr gibt.
 * Diese Probe nimmt die vorgegebene Zeile aus dem Chat und tippt sie in denselben Baum.
 *
 * <p><b>Die Hand ist eine Attrappe und nicht {@code ueberBukkit}</b>, und das ist Pflicht:
 * {@code createBlockData} und {@code setBlockData} schlagen in der Registry nach und
 * werfen ohne laufenden Server - dieselbe Grenze wie bei {@code Material.isSolid},
 * gemessen am 09.11.2026. Die Attrappe schreibt jede Setzung MIT ihrer Reihenfolge mit,
 * denn die Reihenfolge ist die halbe Zusage des Rueckwegs.
 */
@DisplayName("Befehl /freimarken blockbuch zurueck")
class RueckrollBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String NETHER = "freimarken:nether";
    private static final Instant JETZT = Instant.parse("2026-11-26T18:00:00Z");

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final int UNTEN = -64;
    private static final int OBEN = 320;

    private final SpeicherBlockbuch buch = new SpeicherBlockbuch();

    /** Was die Hand gesetzt hat - in der REIHENFOLGE, in der sie es tat. */
    private final List<Blockort> reihenfolge = new ArrayList<>();
    private final Map<Blockort, String> gesetzt = new LinkedHashMap<>();

    /** Zustandstexte, die dieser "Server" nicht kennt - fuer den Rueckfall. */
    private final Set<String> unbekannt = new java.util.HashSet<>();

    private World altfurt;
    private World nether;

    private final Weltgriff griff = new Weltgriff() {
        @Override
        public int unten() {
            return UNTEN;
        }

        @Override
        public int oben(int x, int z) {
            return 64;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return Material.STONE;
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
        }
    };

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(ALTFURT, griff);
        nether = Serverprobe.welt(NETHER, griff);
        reihenfolge.clear();
        gesetzt.clear();
        unbekannt.clear();
    }

    /**
     * Die Hand - sie setzt nichts, sie schreibt mit.
     *
     * <p>Die Welten kommen aus den Namen, die diese Probe kennt, und zwar in BEIDEN
     * Formen: {@code altfurt} und {@code freimarken:altfurt}. Genau das sagt die Naht zu,
     * und genau das tippt ein Betreiber auf der Konsole.
     */
    private final Rueckrollung.Hand hand = new Rueckrollung.Hand() {

        @Override
        public Rueckrollung.Weltlage welt(String getippt) {
            if (getippt == null) {
                return null;
            }
            for (String schluessel : List.of(ALTFURT, NETHER)) {
                if (schluessel.equalsIgnoreCase(getippt)
                        || Blocktext.kurzeWelt(schluessel).equalsIgnoreCase(getippt)) {
                    return new Rueckrollung.Weltlage(schluessel, UNTEN, OBEN);
                }
            }
            return null;
        }

        @Override
        public boolean setze(Blockort ort, String zustand) {
            // MITGESCHRIEBEN WIRD IMMER, auch beim Misserfolg: Eine Mitschrift, die den
            // Fehlschlag verschweigt, liesse eine Probe gruen, die das Merken nach einem
            // Fehlschlag prueft.
            reihenfolge.add(ort);
            if (unbekannt.contains(zustand)) {
                return false;
            }
            gesetzt.put(ort, zustand);
            return true;
        }

        @Override
        public List<String> weltnamen() {
            return List.of("altfurt", "nether");
        }
    };

    /** Werte, in denen altfurt schreibt und der Nether nichts. */
    private static Blockbuchwerte werte() {
        return new Blockbuchwerte(30, 500,
                new Blockbuchwerte.Auswahl(EnumSet.of(Blocktat.SETZEN,
                        Blocktat.ABBAUEN, Blocktat.SPRENGEN), true),
                Map.of(NETHER, new Blockbuchwerte.Auswahl(Set.of(), false)),
                Set.of("chest"), 6, 10);
    }

    private Befehlsprobe probe() {
        return probe(buch, Rueckweggrenzen.VORGABE);
    }

    private Befehlsprobe probe(Blockbuchablage ablage, Rueckweggrenzen grenzen) {
        Rueckrollung rollung = new Rueckrollung(ablage, grenzen, hand,
                Logger.getLogger("Probe"));
        BlockBefehle lesen = new BlockBefehle(ablage, werte(),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                Sprachprobe.spracheAusDemJar(),
                (wer, reichweite) -> null,
                name -> null,
                kennung -> "Anna",
                Logger.getLogger("Probe"));
        RueckrollBefehle zurueck = new RueckrollBefehle(rollung, werte(),
                Sprachprobe.spracheAusDemJar(), () -> JETZT, Logger.getLogger("Probe"));
        return new Befehlsprobe(List.of(
                lesen.blockbuch().then(zurueck.zurueck()).build()));
    }

    /** Ein Moderator mit BEIDEN Rechten - Brigadier prueft auch das Elternliteral. */
    private Spielerprobe moderator() {
        return new Spielerprobe("Mod", Spielerprobe.kennungAus(9),
                BlockBefehle.RECHT, RueckrollBefehle.RECHT)
                .inWelt(altfurt)
                .anOrt(new Location(altfurt, 100, 70, 200));
    }

    /**
     * Wie viele Zeilen der Kasten noch hergibt.
     *
     * <p>Von AUSSEN gefragt und nicht im Speicher nachgesehen: Eine gemerkte
     * Zeile kommt aus {@code imBereich} nicht mehr zurueck, und genau das ist
     * die Zusage, auf die der zweite Lauf sich verlaesst. Ein Blick in die
     * Ablage pruefte dagegen die Ablage.
     */
    private int nochImBuch() {
        return buch.imBereich(ALTFURT, new Quader(0, UNTEN, 0, 400, 100, 400),
                JETZT.minusSeconds(7200), JETZT, 100).size();
    }

    private static Blockort ort(int x, int y, int z) {
        return new Blockort(ALTFURT, x, y, z);
    }

    /** Eine Setzung mit Vorzustand - zurueckgenommen wird sie zu Luft oder Wasser. */
    private void setzte(int x, int y, int z, String ueberbaut, int sekunden) {
        buch.schreibe(List.of(Blockzeile.neu(JETZT.minusSeconds(sekunden), ANNA,
                ort(x, y, z), Blocktat.SETZEN, true, "stone", ueberbaut)));
    }

    /** Ein Abbau mit Vorzustand - zurueckgenommen kommt der Block wieder. */
    private void bauteAb(int x, int y, int z, String vorher, int sekunden) {
        buch.schreibe(List.of(Blockzeile.neu(JETZT.minusSeconds(sekunden), ANNA,
                ort(x, y, z), Blocktat.ABBAUEN, true, "oak_log", vorher)));
    }

    // === Die Vorschau =======================================================

    @Test
    @DisplayName("Die Vorschau nennt Kasten, Zahlen und den fertigen Befehl")
    void vorschauNenntKastenZahlenUndBefehl() throws CommandSyntaxException {
        setzte(101, 70, 200, "minecraft:air", 60);
        bauteAb(99, 69, 200, "minecraft:oak_log[axis=y]", 120);
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe("blockbuch zurueck 10 30", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(3, gelesen.size(), "Kopf, Zahlen, Befehl: " + gelesen);
        assertTrue(gelesen.get(0).contains("altfurt 100/70/200"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("Radius 10"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("30 Minuten"), gelesen.get(0));
        assertTrue(gelesen.get(1).contains("Zeilen: 2"), gelesen.get(1));
        assertTrue(gelesen.get(1).contains("wegnehmen: 1"), gelesen.get(1));
        assertTrue(gelesen.get(1).contains("herstellen: 1"), gelesen.get(1));
        assertTrue(gelesen.get(2).contains("bestaetigen"), gelesen.get(2));
        assertTrue(gesetzt.isEmpty(),
                "EINE VORSCHAU SETZT NICHTS - sonst waere die zweite Stufe Zierde: "
                        + gesetzt);
    }

    @Test
    @DisplayName("Die vorgegebene Zeile passt WOERTLICH in denselben Baum")
    void bestaetigungszeilePasstWoertlichInDenBaum() throws CommandSyntaxException {
        // DIE NAHT, DIE KEINE TAFEL SEHEN KANN. Die Vorschau gibt eine Befehlszeile vor;
        // wird ein Literal im Baum umbenannt, gibt sie eine Zeile vor, die es nicht mehr
        // gibt - und das faellt erst dem Betreiber auf, mitten in einem Grief.
        //
        // Genommen wird die Zeile AUS DEM CHAT und nicht aus Blocktext.bestaetigung:
        // Sonst holte die Probe ihre Erwartung aus der Funktion, die sie prueft.
        setzte(101, 70, 200, "minecraft:air", 60);
        Spielerprobe mod = moderator();
        Befehlsprobe baum = probe();
        baum.tippe("blockbuch zurueck 10 30", mod);
        String vorgegeben = mod.chatBild().get(2);

        int anfang = vorgegeben.indexOf("/freimarken blockbuch ");
        assertTrue(anfang >= 0, "die Zeile muss den ganzen Befehl nennen: " + vorgegeben);
        String getippt = vorgegeben.substring(anfang + "/freimarken ".length()).trim();

        // Derselbe Baum, dieselbe Zeile - und sie muss ausfuehren.
        assertEquals(1, baum.tippe(getippt, moderator()),
                "die vorgegebene Zeile \"" + getippt + "\" muss in denselben Baum"
                        + " passen - sonst gibt die Vorschau einen Befehl vor, den es"
                        + " nicht gibt");
        assertEquals(1, gesetzt.size(), gesetzt.toString());
    }

    @Test
    @DisplayName("Ohne Zeilen im Fenster sagt die Vorschau es - und nennt das Fenster")
    void ohneZeilenSagtDieVorschauEs() throws CommandSyntaxException {
        // Eine Zeile, die ZU ALT ist: Sie liegt im Kasten, aber vor dem Fenster.
        setzte(101, 70, 200, "minecraft:air", 3600);
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch zurueck 10 30", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("30 Minuten"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("nichts"), gelesen.get(0));
    }

    @Test
    @DisplayName("Eine Zeile AUSSERHALB des Kastens zaehlt nicht")
    void zeileAusserhalbDesKastensZaehltNicht() throws CommandSyntaxException {
        setzte(200, 70, 200, "minecraft:air", 60);
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch zurueck 10 30", mod));

        assertTrue(mod.chatBild().get(0).contains("nichts"), mod.chatBild().toString());
    }

    // === Die Grenzen ========================================================

    @Test
    @DisplayName("Ein Radius ueber der Grenze wird abgesagt - mit der Grenze im Satz")
    void radiusUeberDerGrenzeWirdAbgesagt() throws CommandSyntaxException {
        setzte(101, 70, 200, "minecraft:air", 60);
        Spielerprobe mod = moderator();

        assertEquals(0, probe(buch, new Rueckweggrenzen(10, 1440, 5000))
                .tippe("blockbuch zurueck 11 30", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("11"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("10"),
                "der Satz muss die GRENZE nennen - ohne sie raet der Betreiber: "
                        + gelesen.get(0));
        assertTrue(gelesen.get(0).contains("hoechster-radius"),
                "und den Pfad, an dem sie steht: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Radius 0 ist erlaubt und meint genau einen Block")
    void radiusNullMeintEinenBlock() throws CommandSyntaxException {
        setzte(100, 70, 200, "minecraft:air", 60);
        setzte(101, 70, 200, "minecraft:air", 60);
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe("blockbuch zurueck 0 30", mod));

        assertTrue(mod.chatBild().get(1).contains("Zeilen: 1"),
                "nur der Block in der Mitte: " + mod.chatBild());
    }

    @Test
    @DisplayName("Ein Fenster ueber der Grenze wird abgesagt")
    void fensterUeberDerGrenzeWirdAbgesagt() throws CommandSyntaxException {
        Spielerprobe mod = moderator();

        assertEquals(0, probe(buch, new Rueckweggrenzen(50, 60, 5000))
                .tippe("blockbuch zurueck 10 61", mod));

        List<String> gelesen = mod.chatBild();
        assertTrue(gelesen.get(0).contains("61"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("hoechste-minuten"), gelesen.get(0));
    }

    @Test
    @DisplayName("Eine negative Zahl gibt einen SATZ und keine englische Meldung")
    void negativeZahlGibtEinenSatz() throws CommandSyntaxException {
        // Brigadier bekommt die Zahlen OHNE Grenzen. Mit integer(0, 250) antwortete es
        // bei einer -1 auf Englisch und mit einer Meldung, die den eingestellten Wert
        // nicht kennt - geprueft wird darum im Zweig.
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch zurueck -1 30", mod));

        assertTrue(mod.chatBild().get(0).contains("hoechster-radius"),
                "auch die negative Zahl geht durch den eigenen Satz: " + mod.chatBild());
    }

    @Test
    @DisplayName("Zu viele Zeilen sagen AB - und rollen nichts")
    void zuVieleZeilenSagenAb() throws CommandSyntaxException {
        // DER RIEGEL, auf den es ankommt: Ein Lauf in Haeppchen waere nicht bloss
        // unvollstaendig, er waere FALSCH - er nimmt je Block die aelteste Zeile, und ein
        // Schnitt mitten hindurch stellte den Zwischenstand her.
        for (int i = 0; i < 4; i++) {
            setzte(100 + i, 70, 200, "minecraft:air", 60);
        }
        Spielerprobe mod = moderator();

        assertEquals(0, probe(buch, new Rueckweggrenzen(50, 1440, 3))
                .tippe("blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen",
                        mod));

        List<String> gelesen = mod.chatBild();
        assertTrue(gelesen.get(0).contains("Mindestens 4"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("3"), gelesen.get(0));
        assertTrue(gesetzt.isEmpty(),
                "NICHTS darf gesetzt sein - ein Teil-Rueckweg waere falsch: " + gesetzt);
        assertEquals(4, nochImBuch(),
                "und keine Zeile darf gemerkt sein - sonst saehe der naechste Lauf"
                        + " nur noch einen Teil und stellte den Zwischenstand her");
    }

    @Test
    @DisplayName("Genau an der Grenze laeuft es noch")
    void genauAnDerGrenzeLaeuftEsNoch() throws CommandSyntaxException {
        // Die Gegenrichtung zur Probe darueber: Mit > statt >= waere die Grenze um eins
        // verschoben, und beide Proben wuerden das nur zusammen zeigen.
        for (int i = 0; i < 3; i++) {
            setzte(100 + i, 70, 200, "minecraft:air", 60);
        }
        Spielerprobe mod = moderator();

        assertEquals(1, probe(buch, new Rueckweggrenzen(50, 1440, 3))
                .tippe("blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen",
                        mod));

        assertEquals(3, gesetzt.size(), gesetzt.toString());
    }

    // === Welt und Hoehe =====================================================

    @Test
    @DisplayName("Eine unbekannte Welt wird abgesagt - und die Absage nennt die Welten")
    void unbekannteWeltWirdAbgesagt() throws CommandSyntaxException {
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch zurueck 10 30 bei hochwald 1 70 1",
                mod));

        List<String> gelesen = mod.chatBild();
        assertTrue(gelesen.get(0).contains("hochwald"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("altfurt"),
                "die Absage muss die Welten NENNEN - auf der Konsole gibt es keine"
                        + " Vorschlaege: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Der LANGE Weltschluessel ist nicht tippbar - gemessen")
    void langerWeltschluesselIstNichtTippbar() {
        // GEMESSEN am 26.11.2026, und zwar von dieser Probe selbst: Sie stand hier
        // zuerst in der Gegenrichtung ("der Namensraum geht auch") und war rot.
        // StringArgumentType.word() erlaubt nur [0-9a-zA-Z_.+-]; ein Doppelpunkt
        // bricht mit "Expected whitespace to end one argument" ab - auf Englisch.
        //
        // Das bleibt so, und es ist gedeckt: DREI Stellen zeigen dem Betreiber die
        // kurze Form, und keine zeigt ihm die lange. Die Vorschlaege
        // (weltenWerdenVorgeschlagen), die Absage bei unbekannter Welt
        // (unbekannteWeltWirdAbgesagt) und die vorgegebene Bestaetigungszeile
        // (bestaetigungszeilePasstWoertlichInDenBaum) nennen alle nur "altfurt".
        //
        // Diese Probe nagelt es fest: Wer den Befehl auf die lange Form erweitert,
        // braucht ein anderes Argument als word() - und muss hier nachziehen.
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("blockbuch zurueck 10 30 bei"
                        + " freimarken:altfurt 100 70 200", moderator()),
                "ein Doppelpunkt ist fuer Brigadiers word() das Ende des Arguments");
    }

    @Test
    @DisplayName("Eine Hoehe ausserhalb der Welt wird abgesagt, nicht geworfen")
    void hoeheAusserhalbDerWeltWirdAbgesagt() throws CommandSyntaxException {
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch zurueck 10 30 bei altfurt 100 -70 200",
                mod));

        List<String> gelesen = mod.chatBild();
        assertTrue(gelesen.get(0).contains("-70"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("-64"),
                "der Satz muss die Weltgrenzen nennen: " + gelesen.get(0));
    }

    @Test
    @DisplayName("In einer Welt ohne Blockbuch sagt der Befehl, dass dort nichts steht")
    void weltOhneBlockbuchSagtEs() throws CommandSyntaxException {
        Spielerprobe mod = new Spielerprobe("Mod", Spielerprobe.kennungAus(9),
                BlockBefehle.RECHT, RueckrollBefehle.RECHT)
                .inWelt(nether).anOrt(new Location(nether, 10, 70, 10));

        assertEquals(0, probe().tippe("blockbuch zurueck 10 30", mod));

        assertTrue(mod.chatBild().get(0).contains("schreibt das Blockbuch nichts"),
                "\"nichts aufgeschrieben\" ist NICHT \"nichts geschehen\": "
                        + mod.chatBild());
    }

    // === Die Konsole ========================================================

    @Test
    @DisplayName("Die Konsole ohne Ort liest den Weg, der ihr offensteht")
    void konsoleOhneOrtLiestDenWeg() throws CommandSyntaxException {
        Befehlsprobe baum = probe();

        assertEquals(0, baum.tippeAufDerKonsole("blockbuch zurueck 10 30"));

        List<String> gelesen = baum.konsoleBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("bei"),
                "eine GELESENE Absage, die den anderen Zweig nennt: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Die Konsole MIT Ort rollt zurueck - ohne sich einzuloggen")
    void konsoleMitOrtRolltZurueck() throws CommandSyntaxException {
        setzte(101, 70, 200, "minecraft:air", 60);
        Befehlsprobe baum = probe();

        assertEquals(1, baum.tippeAufDerKonsole(
                "blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen"));

        assertEquals(1, gesetzt.size(), gesetzt.toString());
        assertEquals("minecraft:air", gesetzt.get(ort(101, 70, 200)));
    }

    // === Die Ausfuehrung ====================================================

    @Test
    @DisplayName("Bestaetigt werden die Bloecke gesetzt und die Zeilen gemerkt")
    void bestaetigtWirdGesetztUndGemerkt() throws CommandSyntaxException {
        setzte(101, 70, 200, "minecraft:air", 60);
        bauteAb(99, 69, 200, "minecraft:oak_log[axis=y]", 120);
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe(
                "blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen", mod));

        assertEquals("minecraft:air", gesetzt.get(ort(101, 70, 200)),
                "die zurueckgenommene SETZUNG bekommt das Ueberbaute: " + gesetzt);
        assertEquals("minecraft:oak_log[axis=y]", gesetzt.get(ort(99, 69, 200)),
                "der zurueckgenommene ABBAU bekommt den Block samt Zustand: " + gesetzt);
        assertEquals(0, nochImBuch(),
                "beide Zeilen gemerkt - sie kommen nicht wieder");
        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), "nur die Erfolgszeile: " + gelesen);
        assertTrue(gelesen.get(0).contains("2 Bloecke"), gelesen.get(0));
    }

    @Test
    @DisplayName("Erst wegnehmen von oben, dann herstellen von unten")
    void erstWegnehmenVonObenDannHerstellenVonUnten() throws CommandSyntaxException {
        // DIE REIHENFOLGE IST TEIL DER ZUSAGE: Wer einen Turm von unten wegnimmt, laesst
        // den Rest fallen; eine Fackel ohne ihren Block faellt im selben Augenblick ab.
        // Gerechnet wird sie im Rueckrollplan - DASS SIE ANKOMMT, haelt diese Probe.
        setzte(100, 72, 200, "minecraft:air", 60);
        setzte(100, 71, 200, "minecraft:air", 60);
        bauteAb(100, 66, 200, "minecraft:stone", 60);
        bauteAb(100, 65, 200, "minecraft:stone", 60);
        Spielerprobe mod = moderator();

        probe().tippe("blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen", mod);

        assertEquals(List.of(ort(100, 72, 200), ort(100, 71, 200),
                        ort(100, 65, 200), ort(100, 66, 200)), reihenfolge,
                "wegnehmen von OBEN nach unten, dann herstellen von UNTEN nach oben: "
                        + reihenfolge);
    }

    @Test
    @DisplayName("Ein Zustand, den der Server nicht kennt, wird gemeldet UND gemerkt")
    void unbekannterZustandWirdGemeldetUndGemerkt() throws CommandSyntaxException {
        // GEMERKT WIRD TROTZDEM, und das ist die Entscheidung: Sonst versuchte jeder
        // weitere Lauf dasselbe wieder, und ein einziger unbrauchbarer Text blockierte
        // den Kasten fuer immer.
        bauteAb(101, 70, 200, "minecraft:gibtsnichtmehr", 60);
        unbekannt.add("minecraft:gibtsnichtmehr");
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe(
                "blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen", mod));

        assertTrue(gesetzt.isEmpty(), gesetzt.toString());
        assertEquals(0, nochImBuch(),
                "die Zeile ist gemerkt, obwohl der Block nicht kam - sonst"
                        + " versuchte jeder weitere Lauf dasselbe wieder");
        List<String> gelesen = mod.chatBild();
        assertEquals(2, gelesen.size(), "Erfolgszeile UND die Meldung: " + gelesen);
        assertTrue(gelesen.get(1).contains("Nicht gesetzt: 1"), gelesen.get(1));
    }

    @Test
    @DisplayName("Ein zweiter Lauf findet nichts mehr - gemerkt heisst verbraucht")
    void zweiterLaufFindetNichtsMehr() throws CommandSyntaxException {
        setzte(101, 70, 200, "minecraft:air", 60);
        Befehlsprobe baum = probe();
        baum.tippe("blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen",
                moderator());
        gesetzt.clear();
        Spielerprobe mod = moderator();

        assertEquals(0, baum.tippe(
                "blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen", mod));

        assertTrue(gesetzt.isEmpty(),
                "der zweite Lauf darf nichts tun - sonst setzte ein zweimal getippter"
                        + " Befehl den Schaden wieder her: " + gesetzt);
        assertTrue(mod.chatBild().get(0).contains("nichts"), mod.chatBild().toString());
    }

    @Test
    @DisplayName("Zwischen Vorschau und Bestaetigung Gebautes kommt MIT")
    void zwischenVorschauUndBestaetigungGebautesKommtMit()
            throws CommandSyntaxException {
        // ES WIRD ERNEUT GELESEN, und das ist der Grund: Was der Betreiber bestaetigt,
        // ist der KASTEN und das Fenster - nicht eine Liste von Bloecken aus der
        // Vorschau. Arbeitete der Lauf mit jenem Plan, stellte er einen Zustand her, den
        // der Betreiber nicht mehr gesehen hat.
        setzte(101, 70, 200, "minecraft:air", 60);
        Befehlsprobe baum = probe();
        baum.tippe("blockbuch zurueck 10 30", moderator());

        setzte(102, 70, 200, "minecraft:water[level=0]", 30);
        assertEquals(1, baum.tippe(
                "blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen",
                moderator()));

        assertEquals(2, gesetzt.size(),
                "die NEUE Zeile muss mitkommen: " + gesetzt);
        assertEquals("minecraft:water[level=0]", gesetzt.get(ort(102, 70, 200)));
    }

    @Test
    @DisplayName("Eine gestoerte Ablage sagt ab - und setzt nichts")
    void gestoerteAblageSagtAb() throws CommandSyntaxException {
        Spielerprobe mod = moderator();
        Blockbuchablage kaputt = new Blockbuchablage() {
            @Override
            public int schreibe(List<Blockzeile> zeilen) {
                return 0;
            }

            @Override
            public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public List<Blockzeile> vonSpieler(UUID wer, int hoechstens) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public int raeumeAuf(Instant aelterAls, int hoechstens) {
                return 0;
            }

            @Override
            public List<Blockzeile> imBereich(String welt, Quader kasten, Instant von,
                                              Instant bis, int hoechstens) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public int merkeZurueckgenommen(List<Long> nummern) {
                throw new IllegalStateException("Datenbank weg");
            }
        };

        assertEquals(0, probe(kaputt, Rueckweggrenzen.VORGABE)
                .tippe("blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen",
                        mod));

        assertTrue(mod.chatBild().get(0).contains("antwortet nicht"),
                "eine Stoerung darf NICHT wie \"hier steht nichts\" aussehen: "
                        + mod.chatBild());
        assertTrue(gesetzt.isEmpty(), gesetzt.toString());
    }

    // === Die Rechte =========================================================

    @Test
    @DisplayName("Ohne das Rueckrollrecht kommt niemand durch - auch mit Leserecht")
    void ohneRueckrollrechtKommtNiemandDurch() {
        Spielerprobe nurLesen = new Spielerprobe("Leser", Spielerprobe.kennungAus(8),
                BlockBefehle.RECHT).inWelt(altfurt)
                .anOrt(new Location(altfurt, 100, 70, 200));

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("blockbuch zurueck 10 30", nurLesen),
                "das LESEN ist eine Auskunft, dies hier ein Eingriff in fremdes"
                        + " Gebautes - zwei Rechte");
    }

    @Test
    @DisplayName("Ohne das Leserecht auch nicht - Brigadier prueft das Elternliteral")
    void ohneLeserechtAuchNicht() {
        Spielerprobe nurRollen = new Spielerprobe("Roller", Spielerprobe.kennungAus(7),
                RueckrollBefehle.RECHT).inWelt(altfurt)
                .anOrt(new Location(altfurt, 100, 70, 200));

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("blockbuch zurueck 10 30", nurRollen),
                "wer zurueckrollen darf, muss sehen koennen, was er zurueckrollt");
    }

    // === Die Vorschlaege ====================================================

    @Test
    @DisplayName("Die Welten werden vorgeschlagen - in der Form, die der Befehl nimmt")
    void weltenWerdenVorgeschlagen() {
        List<String> vorschlaege = probe().vorschlaege(
                "blockbuch zurueck 10 30 bei ", moderator());

        assertEquals(List.of("altfurt", "nether"), vorschlaege,
                "und zwar KURZ - genau so, wie Blocktext.bestaetigung sie schreibt: "
                        + vorschlaege);
    }

    // === Die Werte ==========================================================

    @Test
    @DisplayName("Die Weltfrage nimmt beide Formen - und keine halbe")
    void weltfrageNimmtBeideFormen() {
        // HERAUSGEZOGEN, WEIL SIE SONST UNPRUEFBAR WAERE: Die Weltsuche steht in
        // Rueckrollung.ueberBukkit, und eine Einspritzung dort lief am 26.11.2026
        // gegen KEINE einzige Probe (Fall V). Die Entscheidung liegt darum in
        // Blocktext.istDieseWelt, und hier ist sie gepruefbar.
        //
        // BEIDE Formen sind Pflicht, und zwar je eine fuer einen der beiden Wege
        // des Befehls: Der getippte Zweig gibt den KURZEN Namen herein (Brigadiers
        // word() kennt keinen Doppelpunkt), die Vorschau am eigenen Standort den
        // LANGEN (World.key().asString()). Eine Suche, die nur eine Form kennt,
        // laesst genau einen der beiden Wege nie eine Welt finden.
        assertTrue(Blocktext.istDieseWelt(ALTFURT, "altfurt"));
        assertTrue(Blocktext.istDieseWelt(ALTFURT, "freimarken:altfurt"));
        assertTrue(Blocktext.istDieseWelt(ALTFURT, "Altfurt"),
                "ein Betreiber, der gross schreibt, hat sich nicht vertippt");
        assertTrue(Blocktext.istDieseWelt(ALTFURT, "  altfurt  "));
        assertFalse(Blocktext.istDieseWelt(ALTFURT, "hochwald"));
        assertFalse(Blocktext.istDieseWelt(ALTFURT, "minecraft:altfurt"),
                "ein ANDERER Namensraum ist eine andere Welt - der kurze Name trifft"
                        + " hier nur, weil er ohne Namensraum getippt wurde");
        assertFalse(Blocktext.istDieseWelt(ALTFURT, ""));
        assertFalse(Blocktext.istDieseWelt(ALTFURT, null));
        assertFalse(Blocktext.istDieseWelt(null, "altfurt"));
    }

    @Test
    @DisplayName("Die Rueckrollung haelt die Grenzen, mit denen sie gebaut wurde")
    void rueckrollungHaeltIhreGrenzen() {
        Rueckweggrenzen eigene = new Rueckweggrenzen(7, 8, 9);
        Rueckrollung rollung = new Rueckrollung(buch, eigene, hand,
                Logger.getLogger("Probe"));

        assertSame(eigene, rollung.grenzen());
        assertFalse(eigene.erlaubterRadius(8));
        assertTrue(eigene.erlaubterRadius(0));
    }
}
