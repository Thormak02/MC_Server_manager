package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.SpeicherBlockbuch;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /freimarken blockbuch} - beide Zweige und jede Absage.
 *
 * <p><b>Diese Probe ist die Deckung der TATWORTE.</b> Jene stehen in
 * {@code Sprachschluessel.familienDeckung()} als selbstbescheinigend - sie werden ueber
 * {@link BlockSatz#tatwort} gewaehlt und nirgends namentlich getippt, also kann
 * {@code GrenzeTest} Probe 30 nichts ueber sie sagen. Was sie deckt, ist
 * {@link #jedeTatGehtDurchDieEchteZeile}: Dort geht JEDE Tat durch die echte Zeile der
 * ausgelieferten Vorlage, und ein Wort, das dort fehlte, waere sichtbar.
 *
 * <p><b>Der angesehene Block kommt aus einer Naht</b> ({@link BlockBefehle.Blickfang}).
 * Ohne sie waere dieser Zweig nicht pruefbar: Der Spielerstellvertreter beantwortet
 * fuenfzehn Methoden und wirft bei jeder anderen, {@code getTargetBlockExact} gehoert
 * nicht dazu. Mit der Naht gibt die Probe einen gestellten Block, und der Zweig, der die
 * Frage stellt, ist derselbe wie im Betrieb.
 */
@DisplayName("Befehl /freimarken blockbuch")
class BlockBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String NETHER = "freimarken:nether";
    private static final Instant WANN = Instant.parse("2026-11-09T13:30:00Z");

    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private final SpeicherBlockbuch buch = new SpeicherBlockbuch();

    private World altfurt;
    private World nether;

    /** Welchen Block der Spieler ansieht - von der Probe gestellt. */
    private Block imBlick;

    /** Welche GETIPPTEN Namen die Suche annimmt - auch mehrere Schreibweisen je Spieler. */
    private final Map<String, UUID> bekannt = new java.util.HashMap<>();

    /**
     * Wie der SERVER einen Spieler schreibt - die Gegenrichtung, und eine EIGENE Karte.
     *
     * <p>Zwei Karten und nicht eine rueckwaerts durchsuchte: Suche und Namensgeber sind
     * zwei verschiedene Dinge, und genau darauf kommt es an. Der Spieler darf "anna"
     * tippen und muss "Anna" lesen - eine Karte, die beide Richtungen bedient, faende
     * bei zwei Schreibweisen die falsche, und die Probe prueefte dann ihre eigene
     * Attrappe statt des Zweigs. Genau das ist hier beim ersten Lauf passiert.
     */
    private final Map<UUID, String> schreibweise = new java.util.HashMap<>();

    private final Weltgriff griff = new Weltgriff() {
        @Override
        public int unten() {
            return -64;
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
        imBlick = altfurt.getBlockAt(62, -60, 8);
        bekannt.clear();
        bekannt.put("Anna", ANNA);
        bekannt.put("Bert", BERT);
        schreibweise.clear();
        schreibweise.put(ANNA, "Anna");
        schreibweise.put(BERT, "Bert");
    }

    /** Werte, in denen altfurt schreibt und der Nether nichts. */
    private static Blockbuchwerte werte() {
        return new Blockbuchwerte(30, 500,
                new Blockbuchwerte.Auswahl(EnumSet.of(Blocktat.SETZEN,
                        Blocktat.ABBAUEN, Blocktat.BENUTZEN), true),
                Map.of(NETHER, new Blockbuchwerte.Auswahl(Set.of(), false)),
                Set.of("chest"), 6, 10);
    }

    private Befehlsprobe probe() {
        return probe(buch, werte());
    }

    private Befehlsprobe probe(Blockbuchablage ablage, Blockbuchwerte werte) {
        BlockBefehle befehle = new BlockBefehle(ablage, werte,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                Sprachprobe.spracheAusDemJar(),
                (wer, reichweite) -> imBlick,
                bekannt::get,
                kennung -> schreibweise.getOrDefault(kennung, "unbekannt"),
                Logger.getLogger("Probe"));
        return new Befehlsprobe(List.of(befehle.blockbuch().build()));
    }

    private Spielerprobe moderator() {
        return new Spielerprobe("Mod", Spielerprobe.kennungAus(9),
                BlockBefehle.RECHT).inWelt(altfurt);
    }

    private void zeileAm(Blockort ort, UUID wer, Blocktat tat, boolean gelungen,
                         String block, int sekunden) {
        buch.schreibe(List.of(Blockzeile.neu(WANN.plusSeconds(sekunden), wer, ort, tat,
                gelungen, block)));
    }

    private static Blockort ort(String welt, int x, int y, int z) {
        return new Blockort(welt, x, y, z);
    }

    @Test
    @DisplayName("Der Block im Blick zeigt seine Zeilen - neueste zuerst")
    void blockImBlickZeigtSeineZeilen() throws CommandSyntaxException {
        Blockort hier = ort(ALTFURT, 62, -60, 8);
        zeileAm(hier, ANNA, Blocktat.SETZEN, true, "stone", 0);
        zeileAm(hier, BERT, Blocktat.ABBAUEN, true, "oak_log", 60);
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe("blockbuch", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(3, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("altfurt 62/-60/8"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("2 Eintraege"), gelesen.get(0));
        assertTrue(gelesen.get(1).contains("Bert"),
                "die NEUESTE Zeile steht vorn: " + gelesen.get(1));
        assertTrue(gelesen.get(2).contains("Anna"), gelesen.get(2));
    }

    @Test
    @DisplayName("Jede Tat geht durch die ECHTE Zeile der Vorlage")
    void jedeTatGehtDurchDieEchteZeile() throws CommandSyntaxException {
        // DIE DECKUNG DER TATWORTE. Sie werden ueber BlockSatz.tatwort gewaehlt und
        // nirgends namentlich getippt - GrenzeTest 30 laesst sie darum als
        // selbstbescheinigend durch, und Sprachschluessel.familienDeckung verweist
        // ausdruecklich hierher. Faellt ein Wort aus der Vorlage, ist DIESE Probe rot.
        //
        // Aus der Aufzaehlung gebaut und nicht drei Zeilen abgeschrieben: Eine vierte
        // Tat waere sonst ungedeckt, und niemand saehe es.
        // JE TAT EINE EIGENE ABFRAGE: Alle drei in EINE Antwort zu legen und dann zu
        // pruefen, dass jedes Wort irgendwo vorkommt, waere zu schwach - dann stuende
        // jedes Wort da, auch wenn zwei am falschen Platz stehen.
        //
        // WAS DIESE PROBE NICHT KANN, und das ist gemessen (Einspritzung C14 am
        // 09.11.2026): Sie faengt ein VERTAUSCHEN in BlockSatz.tatwort nicht. Ihre
        // Erwartung kommt selbst aus tatwort(), also aus der Funktion, die dabei
        // geaendert wird - vertauscht man dort zwei Konstanten, vertauscht sich die
        // Erwartung mit, und der Vergleich stimmt wieder. Was sie haelt, ist "jede Tat
        // bekommt ein Wort aus der Vorlage und keine leere Stelle"; die ZUORDNUNG haelt
        // tatwortZeigtAufDenEigenenPfad, und die holt ihre Erwartung aus dem Schluessel
        // der Tat.
        for (Blocktat tat : Blocktat.values()) {
            SpeicherBlockbuch einzeln = new SpeicherBlockbuch();
            einzeln.schreibe(List.of(Blockzeile.neu(WANN, ANNA,
                    ort(ALTFURT, 62, -60, 8), tat, true, "chest")));
            Spielerprobe mod = moderator();

            probe(einzeln, werte()).tippe("blockbuch", mod);

            String wort = Sprachprobe.spracheAusDemJar()
                    .wort(BlockSatz.tatwort(tat), Einsatz.keine());
            List<String> gelesen = mod.chatBild();
            assertEquals(2, gelesen.size(), "Kopf und eine Zeile: " + gelesen);
            assertTrue(gelesen.get(1).contains(wort),
                    "die Zeile zur Tat " + tat + " muss das Wort \"" + wort + "\""
                            + " tragen - steht dort ein anderes, sind zwei Worte"
                            + " vertauscht, und jedes kommt trotzdem irgendwo vor: "
                            + gelesen.get(1));
        }
    }

    @Test
    @DisplayName("Jede Tat zeigt auf IHREN Pfad - nicht auf den einer anderen")
    void tatwortZeigtAufDenEigenenPfad() {
        // DIE LUECKE, DIE EINE EINSPRITZUNG AUFGEDECKT HAT (C14 am 09.11.2026): In
        // BlockSatz.tatwort TAT_SETZEN und TAT_ABBAUEN getauscht - und alle 2.558 Proben
        // blieben gruen, auch die, die je Tat EINZELN die Zeile liest.
        //
        // DER GRUND IST DIE QUELLE DER ERWARTUNG: Jene Probe holt das erwartete Wort
        // selbst ueber tatwort(), also ueber genau die Funktion, die eingespritzt wurde.
        // Vertauscht man dort zwei Konstanten, vertauscht sich die Erwartung mit, und
        // der Vergleich stimmt wieder. Eine Probe, deren Erwartung aus dem Geprueften
        // kommt, prueft sich selbst.
        //
        // Hier kommt die Erwartung aus dem SCHLUESSEL der Tat, und der ist eine zweite,
        // unabhaengige Quelle: Blocktat.SETZEN traegt "setzen", also MUSS sein Satz auf
        // "blockbuch.tat.setzen" zeigen. Das haelt auch, wenn die Vorlage einmal andere
        // Worte bekommt - geprueft wird die Zuordnung, nicht der Wortlaut.
        for (Blocktat tat : Blocktat.values()) {
            assertEquals("blockbuch.tat." + tat.schluessel(),
                    BlockSatz.tatwort(tat).pfad(),
                    "die Tat " + tat + " traegt den Schluessel \"" + tat.schluessel()
                            + "\", ihr Satz zeigt aber auf einen anderen Pfad - dann"
                            + " sind zwei Konstanten vertauscht, und im Protokoll steht"
                            + " Abreissen, wo gebaut wurde");
        }
    }

    @Test
    @DisplayName("Jedes Tatwort ist ein EIGENES - keine zwei sind gleich")
    void jedesTatwortIstEigen() {
        // Die zweite Haelfte der Deckung: Waeren zwei Worte in der Vorlage gleich - etwa
        // beide "setzte" -, bliebe die Probe darueber gruen, obwohl eine Tat nicht mehr
        // von der anderen zu unterscheiden ist. In einem Protokoll, das jemanden
        // belastet, ist genau das der Unterschied zwischen Bauen und Abreissen.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Set<String> worte = new java.util.LinkedHashSet<>();
        for (Blocktat tat : Blocktat.values()) {
            worte.add(sprache.wort(BlockSatz.tatwort(tat), Einsatz.keine()));
        }

        assertEquals(Blocktat.values().length, worte.size(),
                "jede Tat braucht ihr eigenes Wort, gefunden: " + worte);
    }

    @Test
    @DisplayName("Die Zeit steht in der eingestellten ZONE, nicht in UTC")
    void zeitStehtInDerEingestelltenZone() {
        // DIE LUECKE, DIE EINE EINSPRITZUNG AUFGEDECKT HAT (C13 am 09.11.2026): atZone
        // durch ZoneOffset.UTC ersetzt - alle 2.555 Proben blieben gruen. Die Bildtafel
        // kann es nicht sehen, denn dort wird die Zeit FERTIG eingesetzt; sie prueft die
        // Form der Zeile, nicht die Herkunft des Werts.
        //
        // Der Zeitpunkt ist mit Absicht kurz VOR Mitternacht UTC gewaehlt: In Berlin ist
        // da schon der naechste TAG. Eine Fassung, die UTC rechnet, nennt darum nicht
        // bloss eine andere Stunde, sondern ein anderes Datum - und ein Moderator, der
        // nach dem Vorfall von gestern sucht, findet ihn unter dem falschen Tag.
        Instant kurzVorMitternacht = Instant.parse("2026-11-09T23:30:00Z");
        SpeicherBlockbuch einzeln = new SpeicherBlockbuch();
        einzeln.schreibe(List.of(Blockzeile.neu(kurzVorMitternacht, ANNA,
                ort(ALTFURT, 62, -60, 8), Blocktat.SETZEN, true, "stone")));

        List<net.kyori.adventure.text.Component> zeilen = Blocktext.amOrtZeilen(
                Sprachprobe.spracheAusDemJar(), ort(ALTFURT, 62, -60, 8),
                einzeln.amOrt(ort(ALTFURT, 62, -60, 8), 10),
                Zeitwerte.VORGABE_ZONE, kennung -> "Anna");

        String bild = Sprachprobe.bild(zeilen);
        assertTrue(bild.contains("10.11.2026 00:30"),
                "Berlin liegt im November eine Stunde vor UTC - erwartet wird der"
                        + " 10.11. um 00:30, nicht der 09.11. um 23:30: " + bild);
    }

    @Test
    @DisplayName("Ein ABGEWIESENER Versuch sieht anders aus - nicht nur anders benannt")
    void abgewiesenSiehtAndersAus() throws CommandSyntaxException {
        // Der Fund, bei dem nichts passiert ist, soll beim Durchlesen auffallen. Geprueft
        // wird darum das BILD und nicht ein Wort: Eine Zeile, die sich nur in einem
        // eingesetzten Wort unterscheidet, faellt in zwanzig Zeilen nicht auf.
        Blockort hier = ort(ALTFURT, 62, -60, 8);
        zeileAm(hier, ANNA, Blocktat.SETZEN, true, "stone", 0);
        zeileAm(hier, BERT, Blocktat.SETZEN, false, "stone", 60);
        Spielerprobe mod = moderator();

        probe().tippe("blockbuch", mod);

        List<String> gelesen = mod.chatBild();
        assertEquals(3, gelesen.size(), gelesen.toString());
        String abgewiesen = gelesen.get(1);
        String gelungen = gelesen.get(2);
        assertTrue(abgewiesen.contains("&c"),
                "die abgewiesene Zeile traegt Rot: " + abgewiesen);
        assertTrue(abgewiesen.contains(" x "),
                "und ein Zeichen, das beim Durchlesen auffaellt: " + abgewiesen);
        assertTrue(gelungen.startsWith("&7") && !gelungen.contains("&c"),
                "die gelungene Zeile traegt es nicht: " + gelungen);
    }

    @Test
    @DisplayName("Ohne Zeile sagt der Befehl, WARUM nichts da sein kann")
    void leerSagtWarum() throws CommandSyntaxException {
        // Nicht nur "hier steht nichts": Ohne den Hinweis sucht der Moderator den Fehler
        // im Plugin, obwohl die Welt abgewaehlt oder die Frist um sein kann.
        Spielerprobe mod = moderator();

        // EIN ERFOLG, obwohl nichts gefunden wurde - das ist die Regel dieses Befehls:
        // Er HAT geantwortet, und "hier steht nichts" ist eine gueltige Antwort. Eine 0
        // geben nur die vier Absagen, bei denen er die Frage nicht beantworten KONNTE:
        // kein Block im Blick, Buch abgeschaltet, Ablage gestoert, Name unbekannt.
        assertEquals(1, probe().tippe("blockbuch", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("altfurt 62/-60/8"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("Frist"),
                "der Grund muss dabeistehen: " + gelesen.get(0));
    }

    @Test
    @DisplayName("In einer abgewaehlten Welt sagt der Befehl AUS und nicht leer")
    void abgewaehlteWeltSagtAus() throws CommandSyntaxException {
        // DER UNTERSCHIED, DEN EIN MODERATOR KENNEN MUSS, bevor er jemanden freispricht:
        // Hier steht nichts, weil nichts aufgeschrieben WIRD - nicht weil nichts
        // geschehen ist.
        imBlick = nether.getBlockAt(7, 60, 13);
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("schreibt das Blockbuch nichts"),
                gelesen.get(0));
        assertTrue(gelesen.get(0).contains("blockbuch.welten"),
                "und wo es einzustellen ist: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Ohne Block im Blick nennt die Absage die Reichweite")
    void ohneBlockNenntDieReichweite() throws CommandSyntaxException {
        // Erreichbar bei jedem Blick in den Himmel. Wer auf einen Block weit weg schaut,
        // soll nicht raten, ob das Buch leer ist oder der Blick zu weit ging.
        imBlick = null;
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("6 Bloecke"),
                "die eingestellte Reichweite muss dabeistehen: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Der Spielerzweig zeigt den ORT je Zeile, nicht den Namen")
    void spielerzweigZeigtDenOrt() throws CommandSyntaxException {
        // Die andere Richtung: nicht "wer war hier", sondern "wo war der". Ein Dutzend
        // abgewiesener Versuche an VERSCHIEDENEN Parzellen ist der Fund, den nur sie
        // zeigt - und dafuer muss der Ort je Zeile dastehen.
        zeileAm(ort(ALTFURT, 62, -60, 8), ANNA, Blocktat.SETZEN, false, "stone", 0);
        zeileAm(ort(ALTFURT, 70, -60, 20), ANNA, Blocktat.SETZEN, false, "stone", 60);
        Spielerprobe mod = moderator();

        assertEquals(1, probe().tippe("blockbuch spieler Anna", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(3, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("Anna"), gelesen.get(0));
        assertTrue(gelesen.get(1).contains("altfurt 70/-60/20"),
                "die neueste zuerst, MIT Ort: " + gelesen.get(1));
        assertTrue(gelesen.get(2).contains("altfurt 62/-60/8"), gelesen.get(2));
    }

    @Test
    @DisplayName("Ein unbekannter Name ist eine Absage und KEIN leeres Buch")
    void unbekannterNameWirdAbgesagt() throws CommandSyntaxException {
        // Sonst waere ein Tippfehler ein Freispruch fuer einen Namen, den niemand
        // traegt: Bukkit.getOfflinePlayer(String) gibt NIEMALS null, sondern erfindet
        // eine frische Kennung - und zu der steht nie etwas im Buch.
        Spielerprobe mod = moderator();

        assertEquals(0, probe().tippe("blockbuch spieler annna", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("annna"),
                "der getippte Name muss dastehen: " + gelesen.get(0));
        assertTrue(gelesen.get(0).contains("kennt dieser Server nicht"),
                gelesen.get(0));
    }

    @Test
    @DisplayName("Der Name kommt aus dem Namensgeber, nicht aus der Eingabe")
    void nameKommtAusDemNamensgeber() throws CommandSyntaxException {
        // Wer "anna" tippt, soll "Anna" lesen. Der getippte Name stuende sonst falsch
        // geschrieben in der Ueberschrift einer Auskunft, die jemanden belastet.
        bekannt.put("anna", ANNA);
        zeileAm(ort(ALTFURT, 62, -60, 8), ANNA, Blocktat.SETZEN, true, "stone", 0);
        Spielerprobe mod = moderator();

        probe().tippe("blockbuch spieler anna", mod);

        assertTrue(mod.chatBild().get(0).contains("Anna"),
                "erwartet wird die Schreibweise des Servers: " + mod.chatBild().get(0));
    }

    @Test
    @DisplayName("Die Zeilenzahl der Einstellung wird eingehalten")
    void zeilenzahlWirdEingehalten() throws CommandSyntaxException {
        Blockort hier = ort(ALTFURT, 62, -60, 8);
        for (int nummer = 0; nummer < 8; nummer++) {
            zeileAm(hier, ANNA, Blocktat.SETZEN, true, "block_" + nummer, nummer);
        }
        Blockbuchwerte knapp = new Blockbuchwerte(30, 500,
                new Blockbuchwerte.Auswahl(EnumSet.of(Blocktat.SETZEN), true), Map.of(),
                Set.of(), 6, 3);
        Spielerprobe mod = moderator();

        probe(buch, knapp).tippe("blockbuch", mod);

        List<String> gelesen = mod.chatBild();
        assertEquals(4, gelesen.size(), "Kopf und drei Zeilen: " + gelesen);
        assertTrue(gelesen.get(0).contains("3 Eintraege"),
                "die Ueberschrift nennt die GEZEIGTEN, nicht alle: " + gelesen.get(0));
        assertTrue(gelesen.get(1).contains("block_7"),
                "und es sind die NEUESTEN drei: " + gelesen.get(1));
    }

    @Test
    @DisplayName("Eine gestoerte Ablage sagt ab - und das ist KEIN Freispruch")
    void stoerungSagtAb() throws CommandSyntaxException {
        // Ein Netzfehler darf nicht aussehen wie ein leeres Buch. Die Attrappe wirft
        // schon beim Lesen, also VOR jeder Mitschrift - ein werfender Aufruf waere sonst
        // unsichtbar.
        Blockbuchablage gestoert = new Blockbuchablage() {
            @Override
            public int schreibe(List<Blockzeile> zeilen) {
                return 0;
            }

            @Override
            public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public List<Blockzeile> vonSpieler(UUID spieler, int hoechstens) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public int raeumeAuf(Instant aelterAls, int hoechstens) {
                return 0;
            }
        };
        Spielerprobe mod = moderator();

        assertEquals(0, probe(gestoert, werte()).tippe("blockbuch", mod));

        List<String> gelesen = mod.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("antwortet nicht"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("KEIN Freispruch"),
                "das muss ausdruecklich dastehen: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Ohne Recht ist der Zweig unsichtbar")
    void ohneRechtUnsichtbar() {
        // Brigadier versteckt den Zweig ueber requires, und das ist von "Befehl gibt es
        // nicht" nicht zu unterscheiden - genau so soll es sein: Das Blockbuch nennt,
        // wann jemand nicht da ist und wo er baut.
        Spielerprobe ohne = new Spielerprobe("Gast", Spielerprobe.kennungAus(8))
                .inWelt(altfurt);

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("blockbuch", ohne));
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("blockbuch spieler Anna", ohne));
    }

    @Test
    @DisplayName("Auf der Konsole geht der Spielerzweig, der Ortszweig sagt ab")
    void konsoleKannNachSpielerFragen() throws CommandSyntaxException {
        // Wer keinen Koerper hat, sieht keinen Block an - aber nach einem Spieler fragen
        // kann die Konsole sehr wohl, und fuer die Moderation ist das der bequemere Weg.
        // Die Absage ist GELESEN und nicht ein verstecktes requires: Der Betreiber soll
        // den Grund lesen und nicht "unbekannter Befehl".
        zeileAm(ort(ALTFURT, 62, -60, 8), ANNA, Blocktat.SETZEN, true, "stone", 0);
        Befehlsprobe probe = probe();

        assertEquals(0, probe.tippeAufDerKonsole("blockbuch"));
        List<String> absage = probe.konsoleBild();
        assertEquals(1, absage.size(), absage.toString());
        assertTrue(absage.get(0).contains("blockbuch spieler"),
                "die Absage muss den Weg nennen, der auf der Konsole geht: "
                        + absage.get(0));

        assertEquals(1, probe.tippeAufDerKonsole("blockbuch spieler Anna"));
        assertTrue(probe.konsoleBild().size() > 1,
                "und dieser Zweig antwortet: " + probe.konsoleBild());
    }
}
