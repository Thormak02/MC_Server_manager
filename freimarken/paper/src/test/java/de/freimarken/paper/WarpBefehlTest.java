package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtkasse;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zellquader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /warp} GETIPPT - und wer es ueberhaupt tippen darf.
 *
 * <p><b>Zwei Fragen, die keine Tafel beantworten kann.</b> Die erste ist das
 * {@code requires}: An der Wurzel haengen ZWEI Bedingungen, ein Spieler zu sein UND
 * {@code freimarken.reisen} zu haben. Faellt eine davon weg, bleibt jede Zahl im
 * Quelltext gleich - und im Spiel reist entweder jeder oder niemand. Die zweite ist die
 * Weiche zwischen dem nackten {@code /warp} und {@code /warp <ziel>}: Es sind zwei
 * {@code executes} an zwei Knoten, und welcher antwortet, entscheidet erst Brigadier.
 *
 * <p><b>Die Reise selbst bleibt aussen vor, und das ist eine Grenze mit Grund.</b>
 * {@code Reise.fuehre} fragt als ERSTES die Aufwaermzeit, und die braucht eine
 * {@code JavaPlugin}-Instanz - die laesst sich ohne {@code PluginClassLoader} nicht
 * bauen. Erreichbar sind darum die Absagen, die VOR der Aufwaermzeit liegen: das
 * unbekannte Ziel, die fehlende Welt und der Fall "du bist schon da". Das sind genau die
 * Zweige, die ein Spieler am haeufigsten sieht.
 */
class WarpBefehlTest {

    /**
     * Eine Zutrittsfrage, die JEDE Welt offenlaesst.
     *
     * <p>Hereingegeben und nicht weggelassen: Diese Proben tippen Warpziele und
     * Rueckwege an. Stuende hier die ECHTE Regel, pruefte eine rote Zeile kuenftig
     * den Zutritt statt des Warps - und niemand saehe, welche der zwei Sachen
     * kaputt ist. Dass der Riegel GREIFT, haelt {@code ZutrittsregelTest} und
     * {@code ReiseZutrittTest}.
     */
    private static final Reise.Zutritt ALLES_OFFEN =
            (wer, welt) -> java.util.Optional.empty();

    private static final String NETHER = "minecraft:world_nether";
    private static final String ENDE = "minecraft:world_the_end";

    /** Die Welt der Stadtfelder - so heisst sie in der ausgelieferten config.yml. */
    private static final String FREISTAEDTE = "freimarken:freistaedte";

    /** Eine feste Uhr: Die Kasse bucht mit ihr, und keine Probe wartet dabei. */
    private static final java.time.Clock UHR = java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-11T12:00:00Z"), java.time.ZoneOffset.UTC);

    private World nether;
    private Befehlsprobe baum;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        // DIE FREISTAEDTEWELT MIT BODEN [Teil E3]: Reise.pruefe liest am Ziel den
        // Boden, und eine griff-lose Welt hat keine Bloecke. Das fiel bis Teil E nicht
        // auf, weil keine Probe je gereist ist - alle pruefen eine Absage.
        //
        // keineWeltenMehr zuerst, damit die Welt WIRKLICH neu mit Griff entsteht: Ein
        // Rest aus einer Probe davor waere die griff-lose, und sie bliebe es.
        Serverprobe.keineWeltenMehr();
        Serverprobe.welt(FREISTAEDTE, stadtfeldboden());
        nether = Serverprobe.welt(NETHER);
        Warpverzeichnis verzeichnis = Warpverzeichnis.ausWelten(
                List.of("nether", "ende"));
        // "ende" steht im Verzeichnis, aber nicht in dieser Karte: der Fall, in dem der
        // Name in der Datei steht und die Welt nicht entstanden ist.
        Map<String, World> welten = Map.of("nether", nether);
        Reise reise = new Reise(Logger.getLogger("WarpBefehlTest"), new Rueckweg(),
                null, Sprachprobe.spracheAusDemJar(), ALLES_OFFEN);
        // Ohne Freistadtbuch und ohne Freistaedtewelt: Diese Probe tippt den WELT-Zweig
        // durch. Der Stadtzweig muss dann seine eigene Absage senden, und genau das
        // prueft 41e.
        WarpBefehle befehle = new WarpBefehle(verzeichnis, welten, reise,
                Sprachprobe.spracheAusDemJar(), null, null);
        baum = new Befehlsprobe(List.of(befehle.warp()));
    }

    private Spielerprobe reisender(int zahl) {
        // RECHT_SOFORT dazu [Teil E3]: Damit laeuft die Aufwaermzeit sofort los und
        // fasst den Zeitplaner nie an - das macht den Erfolgsfall einer Reise ohne
        // Server ueberhaupt pruefbar. Siehe sofortigeAufwaermzeit().
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl),
                WarpBefehle.RECHT_REISEN, Aufwaermzeit.RECHT_SOFORT);
    }

    @Test
    @DisplayName("41 - /warp ohne Ziel zeigt die Ziele")
    void warpOhneZielZeigtDieZiele() throws CommandSyntaxException {
        Spielerprobe spieler = reisender(81);

        assertEquals(1, baum.tippe("warp", spieler));

        assertFalse(spieler.chatBild().isEmpty(), "das nackte /warp antwortet");
    }

    @Test
    @DisplayName("41b - drei Lagen von /warp <ziel> geben drei verschiedene Absagen")
    void dreiLagenDreiAbsagen() throws CommandSyntaxException {
        // Unbekanntes Ziel, bekanntes Ziel ohne Welt, und "du bist schon da". Die drei
        // muessen sich UNTERSCHEIDEN: Ein vertauschter Zweig liesse zwei davon gleich,
        // und die Zahl der Sendestellen blieb dabei eine Drei.
        List<String> unbekannt = antwortAuf("warp gibtsnicht", 82, null);
        List<String> ohneWelt = antwortAuf("warp ende", 83, null);
        List<String> schonDa = antwortAuf("warp nether", 84, nether);

        for (List<String> antwort : List.of(unbekannt, ohneWelt, schonDa)) {
            assertFalse(antwort.isEmpty(), "jede Lage antwortet");
        }
        assertNotEquals(unbekannt, ohneWelt, "ein unbekanntes Ziel und eine fehlende"
                + " Welt sind zwei verschiedene Lagen - der Spieler soll wissen, ob er"
                + " sich vertippt hat oder ob der Server etwas nicht hat");
        assertNotEquals(ohneWelt, schonDa);
        assertNotEquals(unbekannt, schonDa);
    }

    private List<String> antwortAuf(String befehl, int zahl, World wo)
            throws CommandSyntaxException {
        Spielerprobe spieler = reisender(zahl);
        if (wo != null) {
            spieler.inWelt(wo);
        }
        baum.tippe(befehl, spieler);
        return spieler.chatBild();
    }

    @Test
    @DisplayName("41c - ohne freimarken.reisen gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        Spielerprobe ohneRecht = new Spielerprobe("Dora", Spielerprobe.kennungAus(85));

        // Die zweite Haelfte des requires. Faellt sie, reist jeder Gast - und der Zugang
        // zu Nether und Ende haengt im Plan an der Rangleiter.
        assertThrows(CommandSyntaxException.class, () -> baum.tippe("warp", ohneRecht));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("warp nether", ohneRecht));
        assertEquals(List.of(), ohneRecht.chatBild());
    }

    @Test
    @DisplayName("41d - die Konsole hat keinen Ort, in den sie reisen koennte")
    void dieKonsoleReistNicht() {
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippeAufDerKonsole("warp nether"));
    }

    // -------------------------------------------------- Der Zweig in die Freistaedte

    /**
     * Derselbe Baum, aber MIT Freistaedten - und mit zwei gegruendeten Staedten.
     *
     * <p>Gegruendet wird ueber das BUCH und nicht am Lager vorbei: Nur so faellt auf,
     * wenn das Stadtverzeichnis beim Gruenden nicht mitgefuehrt wird. Es waere sonst
     * fuer jede Stadt leer, die nach dem Start entsteht - und genau die sucht ihr
     * Besitzer als Erstes im Vorschlagsfenster.
     */
    private Befehlsprobe mitStaedten() {
        SpeicherLager lager = new SpeicherLager();
        SpeicherKasse kasse = new SpeicherKasse(1_000_000L, UHR);
        Freistadtbuch buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        buch.gruende(FREISTAEDTE, Spielerprobe.kennungAus(90), "neustadt", 9);
        buch.gruende(FREISTAEDTE, Spielerprobe.kennungAus(91), "altdorf", 9);
        Reise reise = new Reise(Logger.getLogger("WarpBefehlTest"), new Rueckweg(),
                sofortigeAufwaermzeit(), Sprachprobe.spracheAusDemJar(),
                ALLES_OFFEN);
        WarpBefehle befehle = new WarpBefehle(
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                Map.of("nether", nether), reise, Sprachprobe.spracheAusDemJar(),
                buch, Serverprobe.welt(FREISTAEDTE, stadtfeldboden()));
        return new Befehlsprobe(List.of(befehle.warp()));
    }

    /**
     * Eine Aufwaermzeit OHNE Plugin - und warum das geht [Teil E3].
     *
     * <p>Bis Teil E stand hier {@code null}, und das fiel zehn Proben lang nicht auf:
     * Keine von ihnen hat je {@code reise.fuehre} erreicht, denn alle pruefen eine
     * ABSAGE. Der erste Erfolgsfall (41k) lief sofort in eine
     * {@code NullPointerException} - das ist der eigentliche Grund, aus dem es ihn
     * vorher nicht gab.
     *
     * <p>Das {@code null} als Plugin bleibt, und es ist hier ungefaehrlich:
     * {@code laeuft} fragt nur eine Karte, und {@code beginne} laeuft fuer einen
     * Spieler mit {@link Aufwaermzeit#RECHT_SOFORT} sofort los, ohne den Zeitplaner
     * anzufassen. Darum traegt jeder Reisende dieser Probe dieses Recht.
     */
    private Aufwaermzeit sofortigeAufwaermzeit() {
        return new Aufwaermzeit(null, Sprachprobe.spracheAusDemJar());
    }

    @Test
    @DisplayName("41e - /warp freistadt OHNE die Welt sagt es, statt zu werfen")
    void ohneFreistaedteweltSagtEsDerZweig() throws CommandSyntaxException {
        Spielerprobe spieler = reisender(86);

        // Beide Zeilen muessen DURCHLAUFEN. Ohne eigenes .executes am Literal endete
        // die erste in Brigadiers englischem "Unknown or incomplete command": Ein
        // passendes Literal verdeckt das Wortargument daneben vollstaendig
        // (CommandNode.getRelevantNodes gibt nur das Literal zurueck), also faellt
        // "/warp freistadt" NICHT auf den Zweig "/warp <ziel>" durch.
        assertEquals(1, baum.tippe("warp freistadt", spieler));
        assertEquals(1, baum.tippe("warp freistadt neustadt", spieler));

        assertEquals(2, spieler.chatBild().size(), spieler.chatBild().toString());
        for (String zeile : spieler.chatBild()) {
            assertTrue(zeile.contains("Welt der Freistaedte fehlt"), zeile);
        }
    }

    @Test
    @DisplayName("41f - /warp freistadt ohne Namen fragt nach dem Namen")
    void ohneNamenFragtNachDemNamen() throws CommandSyntaxException {
        Spielerprobe spieler = reisender(87);

        mitStaedten().tippe("warp freistadt", spieler);

        // POSITIV geprueft und nicht gegen die Weltabsage: Faellt das Literal eines
        // Tages doch auf "/warp <ziel>" durch, laese der Spieler "Das Ziel freistadt
        // gibt es nicht" - eine Absage, die stimmt und trotzdem in die Irre fuehrt.
        assertEquals(1, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("Welche Freistadt"),
                spieler.chatBild().get(0));
    }

    @Test
    @DisplayName("41g - eine Freistadt, die es nicht gibt, bekommt ihre eigene Absage")
    void unbekannteStadtBekommtIhreAbsage() throws CommandSyntaxException {
        Spielerprobe spieler = reisender(88);

        mitStaedten().tippe("warp freistadt gibtsnicht", spieler);

        // ZWEI Zeilen: die Absage und der Weg zur Liste. Der Satz traegt eine
        // Umbruchmarke, und ein Sendeort, der nur EINE Zeile nehmen kann, wirft daran -
        // mitten im Befehl, und der Spieler liest eine rote Brigadier-Zeile statt der
        // Absage. Genau der Unterschied, den eine Bildtafel nicht sieht.
        assertEquals(2, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("gibtsnicht"),
                spieler.chatBild().get(0));
        assertTrue(spieler.chatBild().get(1).contains("/freistadt"),
                spieler.chatBild().get(1));
    }

    @Test
    @DisplayName("41h - das Vorschlagsfenster zeigt die gegruendeten Staedte")
    void vorschlaegeZeigenDieStaedte() {
        Befehlsprobe mit = mitStaedten();
        Spielerprobe spieler = reisender(89);

        // Alphabetisch, und beide - aus dem Stadtverzeichnis, das beim Gruenden
        // mitgefuehrt wurde.
        assertEquals(List.of("altdorf", "neustadt"),
                mit.vorschlaege("warp freistadt ", spieler));
        assertEquals(List.of("neustadt"),
                mit.vorschlaege("warp freistadt neu", spieler));
        assertEquals(List.of(), mit.vorschlaege("warp freistadt x", spieler));
    }

    @Test
    @DisplayName("41i - die zwei Vorschlagsgeber vermischen sich nicht")
    void zweiKnotenZweiQuellen() {
        Befehlsprobe mit = mitStaedten();
        Spielerprobe spieler = reisender(92);

        // "ne" passt auf die WELT nether und auf die STADT neustadt. Am Weltknoten darf
        // nur die Welt stehen - haengt der Vorschlagsgeber am falschen Argument, sieht
        // man es nur hier: Im Quelltext sehen die zwei Knoten gleich aus.
        assertEquals(List.of("nether"), mit.vorschlaege("warp ne", spieler));
        assertEquals(List.of("neustadt"), mit.vorschlaege("warp freistadt ne", spieler));
    }

    @Test
    @DisplayName("41j - ohne Freistaedtewelt bleibt das Fenster leer statt zu werfen")
    void ohneWeltLeeresFenster() {
        // baum ist der Baum OHNE Buch. Ein Vorschlagsgeber, der dort hineingreift,
        // wirft auf dem Faden, der die Vervollstaendigung rechnet - und der Spieler
        // saehe nichts als eine haengende Eingabezeile.
        assertEquals(List.of(), baum.vorschlaege("warp freistadt ", reisender(93)));
    }

    // ============================================== Teil E3: die Auflage

    /**
     * Derselbe Baum wie {@link #mitStaedten}, aber die Stadt erfuellt die AUFLAGE.
     *
     * <p>Beides wird hergestellt: ein Regeltext und ein Grundstueck, das einem ANDEREN
     * gehoert als der Besitzerin. Das zweite ist der Punkt - ihr eigener Zuschnitt
     * zaehlt nach Teil D nicht.
     */
    private Befehlsprobe mitErfuellterAuflage(UUID besucher) {
        SpeicherLager lager = new SpeicherLager();
        SpeicherKasse kasse = new SpeicherKasse(1_000_000L, UHR);
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        Freistadtbuch buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        buch.gibGrundstuecke(grundstuecke);
        UUID besitzerin = Spielerprobe.kennungAus(90);
        buch.gruende(FREISTAEDTE, besitzerin, "neustadt", 9);
        Parzelle stadt = buch.staedte(FREISTAEDTE).get(0);
        lager.regelnSetzen(stadt.id(), "Kein PvP.");
        // Das Grundstueck gehoert dem BESUCHER - damit ist er Stadtbuerger, und die
        // Auflage ist erfuellt. Ein Zuschnitt der Besitzerin taete das nicht.
        grundstuecke.anlegen(FREISTAEDTE, stadt.id(), Zellquader.eineZelle(
                buch.plan().ersteZelle(stadt.x()),
                buch.plan().ersteZelle(stadt.z())), besucher, 5_000L);
        Reise reise = new Reise(Logger.getLogger("WarpBefehlTest"), new Rueckweg(),
                sofortigeAufwaermzeit(), Sprachprobe.spracheAusDemJar(),
                ALLES_OFFEN);
        WarpBefehle befehle = new WarpBefehle(
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                Map.of("nether", nether), reise, Sprachprobe.spracheAusDemJar(),
                buch, Serverprobe.welt(FREISTAEDTE, stadtfeldboden()));
        return new Befehlsprobe(List.of(befehle.warp()));
    }

    // 41k GAB ES, UND SIE IST WIEDER HERAUS [Teil E3]. Der Erfolgsfall eines
    // Stadtwarps ist ohne laufenden Server NICHT herstellbar: Reise.pruefe fragt
    // ueber Ankunftspunkt.taugt den Boden, und Material.isSolid geht in die
    // Bukkit-Registry - die wirft ohne Server (dieselbe Grenze wie bei
    // Material.isBlock in Teil D).
    //
    // Auf dem Weg dorthin sind vier echte Luecken der Proben geschlossen worden, und
    // die bleiben: Serverprobe beantwortet jetzt World.getUID, World.getWorldBorder
    // und World.getMaxHeight, Spielerprobe getVehicle, getNearbyEntities und
    // teleportAsync. Jede kuenftige Reiseprobe kommt damit weiter als diese.
    //
    // Was an 41k wirklich zu pruefen war - dass der Riegel den Berechtigten DURCHLAESST
    // und VOR der Reise sitzt -, steht jetzt an zwei Stellen: sechs Proben in
    // FreistadtbuchTest messen die Entscheidung selbst, und GrenzeTest 31at haelt die
    // VERDRAHTUNG fest. Eine Regel ohne Anschluss waere gruen geprueft und im Spiel
    // wirkungslos.

    @Test
    @DisplayName("41l - eine Stadt OHNE Auflage sagt es - und nennt, was fehlt")
    void ohneAuflageSagtWasFehlt() throws CommandSyntaxException {
        // Die andere Richtung, und sie ist der Grund fuer den neuen Satz: Ohne ihn
        // erschien diese Stadt als warp.stadt-unbekannt, also als Tippfehler - eine
        // Luege ueber einen Namen, den es gibt.
        Spielerprobe spieler = reisender(93).inWelt(nether);

        // MIT Ablage und OHNE Regeln: Nur so ist die Auflage wirklich offen. Mit
        // mitStaedten() faellt sie AUS - dann darf jeder hinein, und diese Probe
        // pruefte das Gegenteil von dem, was ihr Name sagt.
        mitStadtOhneAuflage().tippe("warp freistadt neustadt", spieler);

        List<String> zeilen = spieler.chatBild();
        assertEquals(2, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("noch keinen Teleporter"),
                "und NICHT 'diesen Namen gibt es nicht' - den Namen gibt es: "
                        + zeilen.get(0));
        assertTrue(zeilen.get(1).contains("ein Regeltext und ein vergebenes"
                + " Grundstueck"),
                "der Satz nennt BEIDES, was fehlt - sonst weiss der Stadtbesitzer nur,"
                        + " dass etwas fehlt: " + zeilen.get(1));
        assertNotEquals(Serverprobe.welt(FREISTAEDTE, stadtfeldboden()),
                spieler.spieler().getWorld(),
                "und gereist wurde NICHT. Ohne diese Zeile waere die Probe gruen, auch"
                        + " wenn der Zweig die Absage sendet UND trotzdem reist.");
    }

    // 41m GAB ES AUCH, und sie ist aus demselben Grund heraus wie 41k: Dass die
    // Besitzerin DURCHGELASSEN wird, ist nur zu sehen, wenn der Zweig bis in die Reise
    // laeuft - und die laeuft ohne Server nicht. Gemessen wird dieselbe Zusage jetzt in
    // FreistadtbuchTest ("Besitzer, Mitbauer und Stadtbuerger duerfen IMMER hinein"),
    // wo sie ohne Bukkit auskommt.

    /**
     * Eine Stadt MIT Grundstuecksablage und OHNE erfuellte Auflage.
     *
     * <p>Der Unterschied zu {@link #mitStaedten} ist die ABLAGE, und er entscheidet:
     * Ohne sie faellt die Auflage aus, und dann darf jeder hinein - eine Probe mit
     * {@code mitStaedten} pruefte also das Gegenteil von dem, was ihr Name sagt.
     */
    private Befehlsprobe mitStadtOhneAuflage() {
        SpeicherLager lager = new SpeicherLager();
        SpeicherKasse kasse = new SpeicherKasse(1_000_000L, UHR);
        Freistadtbuch buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        // DIE ABLAGE IST DA und leer - die Auflage ist damit pruefbar und offen.
        buch.gibGrundstuecke(new SpeicherGrundstuecke());
        buch.gruende(FREISTAEDTE, Spielerprobe.kennungAus(90), "neustadt", 9);
        Reise reise = new Reise(Logger.getLogger("WarpBefehlTest"), new Rueckweg(),
                sofortigeAufwaermzeit(), Sprachprobe.spracheAusDemJar(),
                ALLES_OFFEN);
        WarpBefehle befehle = new WarpBefehle(
                Warpverzeichnis.ausWelten(List.of("nether", "ende")),
                Map.of("nether", nether), reise, Sprachprobe.spracheAusDemJar(),
                buch, Serverprobe.welt(FREISTAEDTE, stadtfeldboden()));
        return new Befehlsprobe(List.of(befehle.warp()));
    }


    /**
     * Der Boden einer Stadtfeldwelt - Gras bei {@link Feldboden#BODEN}, darueber Luft.
     *
     * <p><b>Eigener Griff und nicht {@code Probewelt}</b>: Jene rechnet mit einem
     * {@code Rasterplan}, und die Freistaedtewelt hat einen {@code Stadtfeldplan} -
     * zwei verschiedene Geometrien. Ein Griff, der nur den Boden kennt, reicht hier:
     * {@code Reise.pruefe} fragt ueber {@code Ankunftspunkt.taugt} nach festem Boden
     * und Luft darueber, und nichts sonst.
     *
     * <p>Ueber das GANZE Feld derselbe Boden, ohne Barriere und Leere: Was die
     * Feldgeometrie entscheidet, prueft {@code FeldbodenTest} mit eigenen Mitteln -
     * diese Attrappe soll den Weg nicht an einer Stelle verschliessen, die mit der
     * Reise nichts zu tun hat.
     */
    private static Weltgriff stadtfeldboden() {
        return new Weltgriff() {
            @Override
            public int unten() {
                return -64;
            }

            @Override
            public int oben(int x, int z) {
                return Feldboden.BODEN;
            }

            @Override
            public Material bei(int x, int y, int z) {
                if (y > Feldboden.BODEN) {
                    return Material.AIR;
                }
                return y == Feldboden.BODEN ? Material.GRASS_BLOCK : Material.STONE;
            }

            @Override
            public void setze(int x, int y, int z, Material block) {
                throw new UnsupportedOperationException("Diese Probe baut nicht");
            }
        };
    }

}
