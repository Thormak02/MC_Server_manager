package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Schutzwache DURCHLAUFEN - nicht ihren Quelltext lesen.
 *
 * <p><b>Die erste Probe des Projekts, die einen Ereigniszweig wirklich ausfuehrt.</b> Bis
 * hierher stand in jeder Tafel von {@code GrenzeTest} dieselbe Einschraenkung: Welche
 * Zeile ein Spieler liest, entscheidet das Programm am Ort seiner Hand - und das liess
 * sich nicht aufrufen, weil schon das LADEN der Wache ohne Server scheitert. Das Loch ist
 * mit {@link Serverprobe} und {@link Spielerprobe} geschlossen, und was die Tafeln 31r und
 * 31v nur BEHAUPTEN konnten, ist hier gemessen: der richtige Grund, der richtige
 * Empfaenger, die Drossel je Spieler.
 *
 * <p>Die Tafeln bleiben trotzdem stehen, und das ist kein Doppel. Sie fangen eine andere
 * Gattung: einen Zweig, der die Naht nicht mehr ruft, und eine Sendestelle, die
 * verschwindet. Diese Probe hier faengt, was dabei HERAUSKOMMT.
 *
 * <p><b>Gegangen wird durch {@code verweigere}</b> und nicht durch ein nachgebautes
 * {@code BlockBreakEvent}: Jene Methode ist der gemeinsame Hals aller zwanzig Zugaenge -
 * die Drossel, die Zweigwahl und die Sendung liegen alle darin. Ein nachgebautes Ereignis
 * brauchte zusaetzlich einen Block- und einen Weltstellvertreter und pruefte dieselbe
 * Entscheidung ein zweites Mal; dass jeder Zugang wirklich dorthin fuehrt, nagelt
 * {@code GrenzeTest} fest.
 *
 * <p><b>Die erwarteten Bilder kommen aus dem ROTEN Lauf</b> und sind nicht abgeschrieben -
 * dieselbe Hausregel wie in {@code SendewegTest}. Danach sind sie gegen die Tafel von
 * {@code BildGleichTest} gegengelesen, die denselben Wortlaut auf einem ANDEREN Weg holt
 * (Schluessel statt Zweig). Zwei Wege, ein Bild.
 */
class SchutzwacheTest {

    /**
     * Die Tat, mit der diese Proben fahren.
     *
     * <p>Eine Abkuerzung und keine zweite Wahrheit: Was gemessen wird, ist der
     * Bauschutz, und er gilt fuer alle drei Taten gleich, solange niemand Rechte
     * abstuft. Welche Tat an welchem Block liegt, prueft {@code TatTest}.
     */
    private static final Tat BAUEN = Tat.BAUEN;

    /** Die Welt, in der die Wache zustaendig ist - ein Name, kein Weltobjekt. */
    private static final String WELT = "marktstadt";

    private static final UUID EIGENTUEMER = Spielerprobe.kennungAus(1);
    private static final UUID BESUCHER = Spielerprobe.kennungAus(2);
    private static final UUID ZWEITER_BESUCHER = Spielerprobe.kennungAus(3);
    private static final UUID NAMENLOS = Spielerprobe.kennungAus(7);

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
        // Die sieben Blockgruppen aus HEIKLE_GRUPPEN, LEER angemeldet [Teil D4]: Seit
        // 36p ein PlayerInteractEvent wirklich durchlaeuft, fragt tatAn sie - und eine
        // Gruppe, die niemand angemeldet hat, WIRFT. Leer ist hier richtig: Diese Probe
        // klickt auf Stein und auf eine Tuer, und beides steht in keiner von ihnen.
        // Welche Art zu welcher Gruppe gehoert, prueft TatTest mit eigenen Mitgliedern.
        for (String gruppe : java.util.List.of("flower_pots", "candles", "candle_cakes",
                "cauldrons", "beds", "all_signs", "cave_vines")) {
            Serverprobe.gruppe(gruppe, java.util.Set.of());
        }
        Serverprobe.nenne(EIGENTUEMER, "Bert");
        // NAMENLOS bekommt mit Absicht keinen Namen: So geht Probe 36d durch den
        // Rueckfall auf die acht Zeichen der UUID.
    }

    /** Buch und Lager zusammen - das Lager braucht die Probe, um Besitz einzutragen. */
    private record Stand(Besitzbuch buch, SpeicherLager lager) { }

    /**
     * Ein Besitzbuch ohne Datenbank - dieselben Vorgabewerte wie im Spiel.
     *
     * <p>Das Lager kommt mit zurueck, weil {@code Besitzbuch} keinen Zugang dazu
     * gibt und auch keinen geben soll: Eine Parzelle entsteht im Spiel durch
     * {@code /parzelle nehmen}, mit Kasse, Grenzregel und Hoechstzahl. Diese Probe
     * prueft die WACHE und nicht den Kauf, also legt sie den Besitz unter dem Buch
     * hindurch ins Lager - und bleibt damit von jeder Aenderung am Kaufweg
     * unberuehrt.
     */
    private static Stand stand() {
        SpeicherLager lager = new SpeicherLager();
        return new Stand(new Besitzbuch(Stadtgrenze.marktstadt(), lager,
                new SpeicherKasse(Wirtschaftswerte.VORGABE_STARTGUTHABEN),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE)), lager);
    }

    private static Schutzwache wache(Besitzbuch buch) {
        return new Schutzwache(buch, WELT, Sprachprobe.spracheAusDemJar());
    }

    /** Eine Stelle in der Welt. Die Hoehe ist der Wache gleich, sie liest nur x und z. */
    private static Location ort(int x, int z) {
        return new Location(null, x, 64, z);
    }

    /**
     * Die Welt als VOLLER Schluessel - so, wie {@code World.key().asString()} sie nennt.
     *
     * <p>Die Konstante {@link #WELT} daneben ist der Text, mit dem Lager und Besitzbuch
     * rechnen; sie taugt nicht fuer den Vergleich in {@code darfHandeln}, weil
     * {@code NamespacedKey.fromString("marktstadt")} daraus {@code minecraft:marktstadt}
     * macht. Die Proben, die {@code darfHandeln} rufen, fahren darum DURCHGEHEND mit
     * diesem Text - Lager, Wache und Stelle.
     */
    private static final String WELT_SCHLUESSEL = "freimarken:marktstadt";

    /**
     * Eine Stelle in einer ECHTEN Welt - fuer die Proben, die {@code darfHandeln} rufen.
     *
     * <p>Ein Befund beim Bauen von Teil C: {@link #ort} liefert eine Stelle OHNE Welt,
     * und {@code darfHandeln} gibt dafuer sofort {@code true} zurueck - noch vor dem
     * Teamrecht und vor jeder Regel. Die vier Proben von Bauschritt 6 gehen durch
     * {@code verweigere} und merken das nie; eine Probe ueber {@code darfHandeln} waere
     * damit gruen, ohne etwas gesehen zu haben.
     */
    private static Location ortInDerWelt(int x, int z) {
        return new Location(Serverprobe.welt(WELT_SCHLUESSEL), x, 64, z);
    }

    /** Eine Wache auf dem VOLLEN Weltschluessel, mit Ortsregel und Handelsklick. */
    private static Schutzwache wacheMitRegel(Besitzbuch buch,
            Schutzwache.Ortsregel.Urteil immer) {
        return new Schutzwache(buch, WELT_SCHLUESSEL, Sprachprobe.spracheAusDemJar(),
                festeRegel(immer), Schutzwache.KEIN_HANDEL);
    }

    /** Dieselbe Wache OHNE die zwei neuen Fragen - fuer den Vergleich davor. */
    private static Schutzwache wacheOhneRegel(Besitzbuch buch) {
        return new Schutzwache(buch, WELT_SCHLUESSEL, Sprachprobe.spracheAusDemJar());
    }

    /**
     * Die erste Stelle auf der x-Achse, die KEIN Bauland ist - Weg oder Mauer.
     *
     * <p>Gesucht und nicht gerechnet: Die Rasterweite steht in der Konfiguration, und eine
     * Probe mit einer hingeschriebenen Koordinate wuerde beim ersten anderen Raster still
     * durch den falschen Zweig laufen.
     */
    private static int ersterWegX(Rasterplan plan) {
        int mitte = plan.mitteX(0);
        for (int x = mitte; x < mitte + plan.rasterWeite() * 2; x++) {
            if (plan.feldBei(x, plan.mitteZ(0)) != Rasterplan.Feld.PARZELLE) {
                return x;
            }
        }
        throw new IllegalStateException("kein Weg neben Parzelle 0/0 gefunden");
    }

    @Test
    @DisplayName("36 - vier Lagen, vier Handbalken: der Grund kommt aus dem Ort der Hand")
    void vierLagenVierHandbalken() {
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        Rasterplan plan = buch.plan();
        Stadtgrenze grenze = buch.grenze();
        stand.lager().anlegen(WELT, 0, 0, EIGENTUEMER, 25_000L);

        Schutzwache wache = wache(buch);
        List<String> gefunden = new ArrayList<>();

        // Je Fall ein eigener Spieler: Die Drossel gilt je Spieler, und zwei Faelle
        // hintereinander mit demselben wuerden den zweiten verschlucken. Genau das
        // prueft 36c - hier soll es nicht stoeren.
        gefunden.add(balken(wache, 11, ort(plan.mitteX(1), plan.mitteZ(1))));
        gefunden.add(balken(wache, 12, ort(ersterWegX(plan), plan.mitteZ(0))));
        gefunden.add(balken(wache, 13,
                ort(plan.mitteX(grenze.bis() + 1), plan.mitteZ(0))));
        gefunden.add(balken(wache, 14, ort(plan.mitteX(0), plan.mitteZ(0))));

        // Die vier Bilder kommen aus dem ROTEN Lauf dieser Probe und sind gegen
        // die Tafel von BildGleichTest gegengelesen: Dort holt sie derselbe
        // Wortlaut ueber den SCHLUESSEL, hier ueber den ZWEIG. Zwei Wege, ein
        // Bild - und wer einen der vier Zweige vertauscht, bricht diesen hier,
        // ohne den anderen anzufassen.
        assertEquals(List.of(
                "&eDiese Parzelle ist frei - /parzelle nehmen",
                "&eWeg und Mauer gehoeren allen",
                "&eHier endet die Marktstadt",
                "&cDiese Parzelle gehoert &fBert"), gefunden,
                "Die Reihenfolge ist die der vier Lagen: freie Parzelle, Weg,"
                        + " jenseits der Grenze, fremder Besitz. Ein Tausch"
                        + " zwischen zwei Lagen laesst jede Zahltafel"
                        + " unveraendert - der Spieler liest dann auf seinem"
                        + " eigenen Weg, die Parzelle sei frei.");
    }

    /** Laesst einen frischen Spieler abweisen und gibt das Bild seines Handbalkens. */
    private static String balken(Schutzwache wache, int zahl, Location ort) {
        Spielerprobe spieler = new Spielerprobe("Probe" + zahl,
                Spielerprobe.kennungAus(zahl));
        wache.verweigere(spieler.spieler(), BAUEN, ort);
        List<String> bilder = spieler.handbalkenBild();
        assertEquals(1, bilder.size(), "genau EIN Handbalken je Abweisung: " + bilder);
        assertEquals(List.of(), spieler.chatBild(), "ein Handbalken ist keine Chatzeile");
        return bilder.get(0);
    }

    @Test
    @DisplayName("36b - der Handbalken geht an den, dessen Hand abgewiesen wurde")
    void derHandbalkenGehtAnDenAbgewiesenen() {
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        stand.lager().anlegen(WELT, 0, 0, EIGENTUEMER, 25_000L);
        Schutzwache wache = wache(buch);

        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        Spielerprobe unbeteiligter = new Spielerprobe("Dora", ZWEITER_BESUCHER);

        wache.verweigere(besucher.spieler(), BAUEN,
                ort(buch.plan().mitteX(0), buch.plan().mitteZ(0)));

        assertEquals(1, besucher.handbalkenBild().size(),
                "der Abgewiesene liest den Grund");
        assertEquals(List.of(), unbeteiligter.handbalkenBild(),
                "ein Unbeteiligter liest nichts. Ging der Balken an ihn, stuende bei ihm"
                        + " \"Diese Parzelle gehoert Bert\", waehrend der Abgewiesene"
                        + " ratlos vor einem Block steht, der nicht bricht - und jede"
                        + " Zahltafel blieb dabei gruen.");
    }

    @Test
    @DisplayName("36c - die Drossel gilt JE SPIELER, nicht fuer alle zusammen")
    void dieDrosselGiltJeSpieler() {
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        stand.lager().anlegen(WELT, 0, 0, EIGENTUEMER, 25_000L);
        Schutzwache wache = wache(buch);
        Location ort = ort(buch.plan().mitteX(0), buch.plan().mitteZ(0));

        Spielerprobe erster = new Spielerprobe("Cara", BESUCHER);
        Spielerprobe zweiter = new Spielerprobe("Dora", ZWEITER_BESUCHER);

        wache.verweigere(erster.spieler(), BAUEN, ort);
        wache.verweigere(erster.spieler(), BAUEN, ort);

        assertEquals(1, erster.handbalkenBild().size(),
                "zweimal innerhalb der Meldepause ist EIN Hinweis - sonst flackert eine"
                        + " festgehaltene Maustaste den Handbalken voll");

        // Und die andere Richtung, die eine gemeinsame Drossel gruen durchlaufen liesse:
        // Ein ZWEITER Spieler ist von der Pause des ersten nicht betroffen.
        wache.verweigere(zweiter.spieler(), BAUEN, ort);

        assertEquals(1, zweiter.handbalkenBild().size(),
                "eine gemeinsame Drossel macht die Wache fuer ALLE stumm, sobald einer"
                        + " anschlaegt - dann baut ein Spieler gegen eine fremde Parzelle"
                        + " und erfaehrt den Grund nie");
    }

    @Test
    @DisplayName("36d - kennt der Server den Namen nicht, stehen acht Zeichen der UUID da")
    void ohneNamenStehenAchtZeichenDerUuid() {
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        stand.lager().anlegen(WELT, 0, 0, NAMENLOS, 25_000L);
        Schutzwache wache = wache(buch);

        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        wache.verweigere(besucher.spieler(), BAUEN,
                ort(buch.plan().mitteX(0), buch.plan().mitteZ(0)));

        String bild = besucher.handbalkenBild().get(0);
        assertTrue(bild.endsWith(NAMENLOS.toString().substring(0, 8)),
                "statt des Namens stehen acht Zeichen der UUID: " + bild);
    }

    // ================================ Teil C: die zwei zusaetzlichen Fragen

    @Test
    @DisplayName("36e - die ORTSREGEL wird gefragt, und ihr Ja ist endgueltig")
    void ortsregelWirdGefragt() {
        // Die Verdrahtung, nicht die Regel: Dass darfHandeln die Ortsregel ueberhaupt
        // fragt, und dass ihr ERLAUBT das Besitzbuch NICHT mehr zu Wort kommen laesst.
        //
        // Die Stelle ist eine FREMDE Parzelle - ohne die Ortsregel waere sie verboten.
        // Sagt die Regel ja, muss die Hand durchgehen; sonst waere "die Einheit
        // entscheidet" nur ein Kommentar.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        stand.lager().anlegen(WELT_SCHLUESSEL, 0, 0, EIGENTUEMER, 25_000L);
        Location fremd = ortInDerWelt(buch.plan().mitteX(0), buch.plan().mitteZ(0));
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);

        assertFalse(wacheOhneRegel(buch).darfHandeln(besucher.spieler(), BAUEN, fremd),
                "ohne Ortsregel ist die fremde Parzelle verboten");

        assertTrue(wacheMitRegel(buch, Schutzwache.Ortsregel.Urteil.ERLAUBT)
                        .darfHandeln(besucher.spieler(), BAUEN, fremd),
                "das ERLAUBT der Ortsregel ist endgueltig");
    }

    @Test
    @DisplayName("36f - ein VERBOTEN der Ortsregel gilt auch auf EIGENEM Grund")
    void ortsregelVerbietetAuchDemEigentuemer() {
        // Die andere Richtung, und sie ist der Fall, der im Spiel vorkommt: Steht die
        // Markthalle in der Marktstadt - laeden.welt erlaubt das -, liegt eine
        // Ladeneinheit auf einer Parzelle. Dann gilt die Einheit, auch gegen den
        // Eigentuemer der Parzelle darunter.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        stand.lager().anlegen(WELT_SCHLUESSEL, 0, 0, EIGENTUEMER, 25_000L);
        Location eigen = ortInDerWelt(buch.plan().mitteX(0), buch.plan().mitteZ(0));
        Spielerprobe eigentuemer = new Spielerprobe("Bert", EIGENTUEMER);

        assertTrue(wacheOhneRegel(buch).darfHandeln(eigentuemer.spieler(), BAUEN, eigen),
                "auf seiner eigenen Parzelle darf er - sonst prueft diese Probe nichts");

        assertFalse(wacheMitRegel(buch, Schutzwache.Ortsregel.Urteil.VERBOTEN)
                .darfHandeln(eigentuemer.spieler(), BAUEN, eigen));
    }

    @Test
    @DisplayName("36g - der Handbalken kommt von der ORTSREGEL, wenn sie verboten hat")
    void handbalkenKommtVonDerOrtsregel() {
        // Sonst stuende auf dem Balken eines Ladenbesuchers "Diese Parzelle ist noch
        // frei" - eine Auskunft ueber eine Parzelle, die ihn nichts angeht, und zwar in
        // einer Welt, in der es gar keine gibt.
        Stand stand = stand();
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        Schutzwache mitNein = new Schutzwache(stand.buch(), WELT,
                Sprachprobe.spracheAusDemJar(),
                festeRegel(Schutzwache.Ortsregel.Urteil.VERBOTEN),
                Schutzwache.KEIN_HANDEL);

        mitNein.verweigere(besucher.spieler(), BAUEN, ort(0, 0));

        assertEquals("EIGENER GRUND DER ORTSREGEL",
                besucher.handbalkenBild().get(0));
    }

    @Test
    @DisplayName("36h - das Teamrecht gilt AUCH gegen die Ortsregel")
    void teamrechtSchlaegtAuchDieOrtsregel() {
        // Haenge freimarken.bauen.ueberall nur an den Parzellen, muesste ein Betreiber
        // den Laden eines Spielers kuendigen, um darin aufzuraeumen.
        // Die ECHTE Welt, und das ist hier der Unterschied zwischen einer Probe und
        // keiner: Mit der weltlosen Stelle war diese Probe gruen, ohne dass das
        // Teamrecht ueberhaupt gefragt wurde - darfHandeln kehrt bei fehlender Welt
        // sofort um. Sie behauptete dann etwas ueber das Recht und bewies etwas ueber
        // eine Stelle ohne Welt.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        Besitzbuch buch = stand.buch();
        Location drin = ortInDerWelt(buch.plan().mitteX(0), buch.plan().mitteZ(0));
        Spielerprobe team = new Spielerprobe("Team", BESUCHER, Schutzwache.UEBERALL);
        Spielerprobe ohneRecht = new Spielerprobe("Cara", BESUCHER);

        assertTrue(wacheMitRegel(buch, Schutzwache.Ortsregel.Urteil.VERBOTEN)
                .darfHandeln(team.spieler(), BAUEN, drin));
        // Die Gegenprobe am SELBEN Ort und mit derselben Wache: Ohne sie waere auch eine
        // Fassung gruen, die ueberall ja sagt.
        assertFalse(wacheMitRegel(buch, Schutzwache.Ortsregel.Urteil.VERBOTEN)
                .darfHandeln(ohneRecht.spieler(), BAUEN, drin));
    }

    @Test
    @DisplayName("36i - KEINE_ORTSREGEL ist zustaendigkeitsfrei und verbietet nichts")
    void keineOrtsregelIstStill() {
        // Der Stand jedes Servers ohne Markthalle, und die Vorgabe des kurzen
        // Konstruktors. Sagt sie je etwas anderes als NICHT_ZUSTAENDIG, waere jede
        // Parzellenprobe dieser Datei stumm durch einen anderen Zweig gelaufen.
        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                Schutzwache.KEINE_ORTSREGEL.pruefe(null, BAUEN, ort(0, 0)));
        // Und ihr grund() WIRFT, statt einen leeren Balken zu liefern: Wer ihn holt, hat
        // die Reihenfolge in darfHandeln umgestellt und soll es sofort merken.
        assertThrows(IllegalStateException.class,
                () -> Schutzwache.KEINE_ORTSREGEL.grund(null, BAUEN, ort(0, 0)));
    }

    @Test
    @DisplayName("36j - KEIN_HANDEL sagt ueberall nein")
    void keinHandelSagtNein() {
        // Der heutige Stand: Es gibt keine Kistenshops. Die Tuer steht trotzdem schon
        // da - haette sie ein Ja darin, waere der ganze Schutz offen.
        assertFalse(Schutzwache.KEIN_HANDEL.istHandel(
                new Spielerprobe("Cara", BESUCHER).spieler(), null));
    }

    @Test
    @DisplayName("36k - Ortsregel und Handelsklick sind PFLICHT")
    void ortsregelUndHandelSindPflicht() {
        // Ein null waere eine Wache, die bei jedem Klick wirft - und ein Wurf aus einem
        // Blockereignis trifft jeden Spieler, nicht nur den, der geklickt hat.
        Stand stand = stand();
        Sprache sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class, () -> new Schutzwache(stand.buch(),
                WELT, sprache, null, Schutzwache.KEIN_HANDEL));
        assertThrows(IllegalArgumentException.class, () -> new Schutzwache(stand.buch(),
                WELT, sprache, Schutzwache.KEINE_ORTSREGEL, null));
    }

    @Test
    @DisplayName("36l - die Handelstuer fragt NUR den Handelsklick, nicht die Hand")
    void handelstuerFragtNurDenHandelsklick() {
        // Befund der Einspritzungskampagne vom 08.11.2026: Diese Bedingung stand als
        // Zweig in beimKlicken und war damit nur ueber ein PlayerInteractEvent mit einem
        // Schildblock erreichbar - zwei Einspritzungen darauf liefen gruen durch. Jetzt
        // steht sie in einer eigenen Methode und ist ohne Server zu pruefen.
        //
        // DIESE PROBE HAT IHRE ZUSAGE GETAUSCHT [08.11.2026], und zwar mit Grund: Vorher
        // stand hier "die Tuer oeffnet NUR ohne Werkzeug in der Hand". Das sollte
        // heissen: Wer mit einer Axt auf ein Schild klickt, will nicht kaufen.
        // aendertBlock sagt aber bei JEDEM Gegenstand ja, der ein Block ist - und wer
        // Stein, Erde oder Holz verkaufen will, haelt genau das in der Hand. Die Tuer
        // waere also fuer die haeufigste Ware ueberhaupt zu geblieben, und das erst
        // recht beim Linksklick, denn Lumias Verkauf IST ein Linksklick.
        //
        // Der Schutz liegt jetzt in der WIRKUNG und nicht in der Verweigerung: Shopklick
        // setzt nach einem Shopklick beide Ergebnisse auf DENY - der Handel findet statt,
        // die Hand bleibt untaetig. Die halbe Zusage haelt damit weiter, aber ueber
        // istHandel: Die Tuer geht nur an einem SHOPSCHILD auf (ShopklickTest haelt das
        // fest) und an keinem anderen Block.
        Schutzwache.Handelsklick immerJa = (spieler, block) -> true;

        assertTrue(Schutzwache.alsHandelDurchlassen(immerJa, null, null),
                "ein Klick auf einen Shop ist ein Handel - egal, was in der Hand liegt");
        // Die tragende Zusage: Was der Handelsklick ablehnt, geht nicht durch. Die Tuer
        // ist die Stelle, die den ganzen Schutz oeffnet - handeln darf jeder, immer und
        // ueberall, auch im Laden eines anderen -, also darf sie NIE von sich aus ja
        // sagen.
        assertFalse(Schutzwache.alsHandelDurchlassen(Schutzwache.KEIN_HANDEL, null,
                null), "ohne Shop am Block ist es kein Handel");
    }

    @Test
    @DisplayName("36n - die benannte TAT kommt bei der Ortsregel an, und zwar jede")
    void dieTatKommtAn() {
        // DIE NAHT VON TEIL D1 [12.11.2026]. Die Wache gibt die Tat nur weiter, und
        // genau das ist hier zu pruefen: Eine Regel, die sie beachtet - ab Teil D3 die
        // Stadtwache -, kann nur richtig entscheiden, wenn sie ankommt. Eine Wache, die
        // immer BAUEN durchreichte, waere in jeder anderen Probe dieses Projekts gruen:
        // Solange niemand Rechte abstuft, antworten alle drei Taten gleich.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        List<String> mitschrift = new ArrayList<>();
        Schutzwache wache = new Schutzwache(stand.buch(), WELT_SCHLUESSEL,
                Sprachprobe.spracheAusDemJar(),
                schreiberin(mitschrift, Schutzwache.Ortsregel.Urteil.ERLAUBT),
                Schutzwache.KEIN_HANDEL);
        Location irgendwo = ortInDerWelt(0, 0);

        for (Tat tat : Tat.values()) {
            wache.darfHandeln(besucher.spieler(), tat, irgendwo);
        }

        // Die Erwartung wird aus values() GERECHNET und nicht hingeschrieben: Eine
        // vierte Tat waere sonst gebaut, aber auf diesem Weg ungeprueft.
        List<String> erwartet = new ArrayList<>();
        for (Tat tat : Tat.values()) {
            erwartet.add("pruefe:" + tat);
        }
        assertEquals(erwartet, mitschrift,
                "jede Tat muss unveraendert und in der Reihenfolge der Aufrufe"
                        + " ankommen - eine Wache, die sie unterwegs ersetzt, gibt der"
                        + " Stadtwache ab Teil D3 das falsche Recht");
    }

    @Test
    @DisplayName("36o - auch der GRUND wird mit der Tat geholt, nicht ohne sie")
    void derGrundBekommtDieTatAuch() {
        // Der zweite Weg durch dieselbe Schnittstelle, und er war der uebersehene: Die
        // Absage selbst ist eine Sache, die BEGRUENDUNG eine andere. Holte die Wache den
        // Grund ohne die Tat, stuende auf dem Handbalken eines Stadtbuergers an einer
        // Truhe der Satz fuer das Bauen - und die Zahl der Proben bliebe dieselbe.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        List<String> mitschrift = new ArrayList<>();
        Schutzwache wache = new Schutzwache(stand.buch(), WELT_SCHLUESSEL,
                Sprachprobe.spracheAusDemJar(),
                schreiberin(mitschrift, Schutzwache.Ortsregel.Urteil.VERBOTEN),
                Schutzwache.KEIN_HANDEL);

        wache.verweigere(besucher.spieler(), Tat.BEHAELTER,
                ortInDerWelt(0, 0));

        // ZWEI Eintraege, und die Mitschrift nennt den AUFRUF: Eine Liste aus zwei
        // gleichen Taten saehe nicht, WELCHER der beiden Wege sie bekommen hat - und
        // genau einer von beiden war beim ersten Lauf dieser Probe stumm. Dass pruefe
        // zweimal faellt, hat seinen eigenen Grund; er steht an Schutzwache.grund.
        assertEquals(List.of("pruefe:BEHAELTER", "grund:BEHAELTER"), mitschrift,
                "pruefe UND grund bekommen die Tat des Zugangs - und nicht eine"
                        + " Vorgabe");
    }

    /**
     * Eine Ortsregel, die jeden Aufruf MITSCHREIBT - mit Namen und Tat.
     *
     * <p>Geschrieben wird VOR der Antwort: Eine Mitschrift danach saehe einen Aufruf
     * nicht, der wirft - und dieselbe Falle hat dieses Projekt schon einmal gestellt.
     *
     * <p><b>Der Name des Aufrufs gehoert in die Zeile</b> und nicht nur die Tat: Zwei
     * gleiche Taten in einer Liste sagen nicht, WELCHER Weg sie gebracht hat. Beim ersten
     * Lauf von 36o schrieb {@code grund} nicht mit, und eine Liste aus Taten allein haette
     * das als "ein Aufruf zu wenig" gezeigt - nicht als "der falsche fehlt".
     *
     * @param immer was {@code pruefe} antwortet; {@code VERBOTEN} ist der einzige Weg, auf
     *              dem {@code grund} ueberhaupt gerufen wird
     */
    private static Schutzwache.Ortsregel schreiberin(List<String> mitschrift,
            Schutzwache.Ortsregel.Urteil immer) {
        return new Schutzwache.Ortsregel() {
            @Override
            public Urteil pruefe(Player spieler, Tat tat, Location ort) {
                mitschrift.add("pruefe:" + tat);
                return immer;
            }

            @Override
            public Component grund(Player spieler, Tat tat, Location ort) {
                mitschrift.add("grund:" + tat);
                return Component.text("MITGESCHRIEBEN");
            }

            @Override
            public boolean zustaendig(Location ort) {
                return true;
            }
        };
    }

    /**
     * Eine Ortsregel, die ueberall dasselbe sagt - fuer die Proben der VERDRAHTUNG.
     *
     * <p>Was der Ladenschutz WIRKLICH antwortet, prueft {@code LadenschutzTest}. Hier
     * geht es nur darum, dass die Wache die Antwort ueberhaupt holt und richtig
     * einordnet - und dafuer ist eine feste Antwort die klarere Probe als eine echte
     * Regel mit Verzeichnis und Ablage.
     */
    private static Schutzwache.Ortsregel festeRegel(
            Schutzwache.Ortsregel.Urteil immer) {
        return new Schutzwache.Ortsregel() {
            @Override
            public Urteil pruefe(Player spieler, Tat tat, Location ort) {
                return immer;
            }

            @Override
            public Component grund(Player spieler, Tat tat, Location ort) {
                return Component.text("EIGENER GRUND DER ORTSREGEL");
            }

            // Zustaendig, sobald sie ueberhaupt etwas sagt: Eine feste Regel, die
            // NICHT_ZUSTAENDIG antwortet, haelt sich auch nicht fuer zustaendig -
            // sonst liefen die beiden Antworten auseinander, und genau davor warnt
            // das Javadoc von Ortsregel.zustaendig.
            @Override
            public boolean zustaendig(Location ort) {
                return immer != Urteil.NICHT_ZUSTAENDIG;
            }
        };
    }

    @Test
    @DisplayName("36p - ein Rechtsklick mit WERKZEUG auf einen harmlosen Block: beides"
            + " abgesagt")
    void werkzeugklickAufHarmlosemBlock() {
        // FUND DER EINSPRITZUNGSRUNDE ZU TEIL D [12.11.2026], und zugleich die erste
        // Probe dieses Projekts, die beimKlicken WIRKLICH durchlaeuft - moeglich, seit
        // die Serverprobe fuer TatTest getState, getBlockData und die Blockgruppen
        // beantwortet. Die Einschraenkung in GrenzeTest 31x ist damit fuer diesen Zweig
        // aufgehoben.
        //
        // DER FALL: Der Block ist harmlos (Stein - tatAn gibt null), aber in der Hand
        // liegt eine Schaufel. Dann wird der BLOCK trotzdem abgesagt, und zwar weil
        // seine Tat UNBEKANNT ist: Die beiden Listen HEIKEL und HEIKLE_GRUPPEN koennen
        // Luecken haben, und was dort fehlt, soll nicht reagieren. So stand es hier vor
        // den Taten, und die Einspritzung hat gezeigt, dass es von nichts gedeckt war.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        stand.lager().anlegen(WELT_SCHLUESSEL, 0, 0, EIGENTUEMER, 25_000L);
        Besitzbuch buch = stand.buch();
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        Block fremderStein = steinBlock(buch);

        PlayerInteractEvent klick = new PlayerInteractEvent(besucher.spieler(),
                Action.RIGHT_CLICK_BLOCK, new Wesenprobe.Probestapel(
                        Material.DIAMOND_SHOVEL, 1),
                fremderStein, BlockFace.UP);
        wacheOhneRegel(buch).beimKlicken(klick);

        assertEquals(Event.Result.DENY, klick.useItemInHand(),
                "die Schaufel macht aus Gras einen Pfad - die HAND muss abgesagt sein");
        assertEquals(Event.Result.DENY, klick.useInteractedBlock(),
                "und der BLOCK auch, obwohl tatAn ihn nicht kennt: Was die beiden"
                        + " Listen nicht fuehren, soll auf fremdem Grund nicht"
                        + " reagieren. Ohne diese Zeile ist jede Luecke in HEIKEL ein"
                        + " offener Griff, sobald ein Werkzeug in der Hand liegt.");
    }

    @Test
    @DisplayName("36q - eine Tuer, die ERLAUBT ist, bleibt offen - auch mit Axt in der"
            + " Hand")
    void erlaubteTuerBleibtOffen() {
        // DIE ANDERE HAELFTE, und sie ist der Grund, warum beimKlicken ZWEI Fragen
        // stellt: Ein Stadtbuerger mit Benutzenrecht kaeme durch keine Tuer mehr, solange
        // er eine Axt haelt - und eine Axt in der Hand hat fast jeder. Eine einzige
        // Frage, die strengere von beiden, wuerde genau das tun.
        Serverprobe.keineWeltenMehr();
        Stand stand = stand();
        stand.lager().anlegen(WELT_SCHLUESSEL, 0, 0, EIGENTUEMER, 25_000L);
        Besitzbuch buch = stand.buch();
        Spielerprobe besucher = new Spielerprobe("Cara", BESUCHER);
        Block tuer = tuerBlock(buch);
        // Eine Ortsregel wie eine Freistadt, die BENUTZEN hergibt und BAUEN nicht.
        Schutzwache wache = new Schutzwache(buch, WELT_SCHLUESSEL,
                Sprachprobe.spracheAusDemJar(), nurFuer(Tat.BENUTZEN),
                Schutzwache.KEIN_HANDEL);

        PlayerInteractEvent klick = new PlayerInteractEvent(besucher.spieler(),
                Action.RIGHT_CLICK_BLOCK, new Wesenprobe.Probestapel(
                        Material.DIAMOND_AXE, 1),
                tuer, BlockFace.UP);
        wache.beimKlicken(klick);

        assertEquals(Event.Result.DENY, klick.useItemInHand(),
                "die AXT bleibt abgesagt - sie wuerde den Block entrinden");
        assertNotEquals(Event.Result.DENY, klick.useInteractedBlock(),
                "die TUER aber nicht: Ihre Tat ist BENUTZEN, und die gibt diese Regel"
                        + " her. Mit EINER Frage waere sie mit der Axt mitabgesagt - und"
                        + " ein Stadtbuerger kaeme nie durch seine eigene Haustuer.");
    }

    /**
     * Die Welt MIT Bloecken - fuer die zwei Klickproben.
     *
     * <p>{@link #ortInDerWelt} baut eine Welt OHNE Weltgriff, und die hat keine Bloecke:
     * Ihr {@code getBlockAt} klagt, sobald ein Zweig die Art eines Blocks fragt. Das war
     * bis zu den Klickproben richtig - keine andere Probe dieser Datei liest einen Block
     * -, und darum legen diese zwei die Welt mit einer {@link Probewelt} neu an.
     */
    private static World weltMitBloecken(Besitzbuch buch) {
        return Serverprobe.welt(WELT_SCHLUESSEL,
                new Probewelt(buch.plan(), -64));
    }

    /** Ein Block aus Stein auf fremdem Grund - {@code tatAn} kennt ihn nicht. */
    private static Block steinBlock(Besitzbuch buch) {
        World welt = weltMitBloecken(buch);
        Block block = welt.getBlockAt(buch.plan().mitteX(0), 64,
                buch.plan().mitteZ(0));
        block.setType(Material.STONE);
        return block;
    }

    /** Eine Tuer auf fremdem Grund - {@code tatAn} gibt dafuer BENUTZEN. */
    private static Block tuerBlock(Besitzbuch buch) {
        World welt = weltMitBloecken(buch);
        Block block = welt.getBlockAt(buch.plan().mitteX(0), 64, buch.plan().mitteZ(0));
        Serverprobe.oeffenbar(WELT_SCHLUESSEL, block.getX(), block.getY(),
                block.getZ());
        block.setType(Material.OAK_DOOR);
        return block;
    }

    /**
     * Eine Ortsregel, die GENAU EINE Tat hergibt - wie eine Freistadt mit einem Recht.
     *
     * <p>Die Abstufung je Tat ist der ganze Zweck der Tat in der Schnittstelle, und
     * ohne eine Regel, die sie wirklich nutzt, waere sie hier nicht messbar: Der
     * Ladenschutz liest sie bewusst nicht, und die Stadtwache braucht eine ganze
     * Stadtfelderwelt.
     */
    private static Schutzwache.Ortsregel nurFuer(Tat erlaubte) {
        return new Schutzwache.Ortsregel() {
            @Override
            public Urteil pruefe(Player spieler, Tat tat, Location ort) {
                return tat == erlaubte ? Urteil.ERLAUBT : Urteil.VERBOTEN;
            }

            @Override
            public Component grund(Player spieler, Tat tat, Location ort) {
                return Component.text("NUR " + erlaubte);
            }

            @Override
            public boolean zustaendig(Location ort) {
                return true;
            }
        };
    }

}
