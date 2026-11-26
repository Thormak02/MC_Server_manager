package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Weltregel;
import de.freimarken.kern.Weltregelwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Weltregeln - was eine Welt an Zerstoerung OHNE Spieler zulaesst.
 *
 * <p><b>Geprueft werden die drei ENTSCHEIDUNGEN und nicht die acht Ereignisse</b>, und
 * das ist eine gemessene Grenze: Ein {@code EntityExplodeEvent} laesst sich ohne
 * laufenden Server nicht bauen, und fuer die Kolbenereignisse gilt dasselbe. Darum sind
 * {@code greift}, {@code uebergriff} und {@code kolbenUeberschreitet} paketsichtbar - der
 * Grund steht im Quelltext der Wache.
 *
 * <p>Damit bleibt eine Luecke, und sie wird hier mit ZWEI Tafeln geschlossen statt
 * uebersehen:
 * <ul>
 *   <li>{@link #achtHakenRichtigEingestellt} - gibt es die Zuhoerer ueberhaupt, und
 *       haengen sie auf der richtigen Ebene? Eine gebaute, gruen geprueefte Regel ohne
 *       Verdrahtung ist unsichtbar wirkungslos.</li>
 *   <li>{@link #jederZweigFragtSeineRegel} - fragt jeder Zweig VOR dem Abbrechen nach
 *       seiner Regel? Ohne diese Tafel koennte ein Zweig seinen Riegel verlieren und
 *       immer abbrechen; keine der Rechnungen hier wuerde davon rot.</li>
 * </ul>
 */
@DisplayName("Die Weltregeln")
class WeltwacheTest {

    private static final String MARKTSTADT = "freimarken:marktstadt";
    private static final String KORNMARK = "freimarken:kornmark";
    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private SpeicherLager lager;
    private Besitzbuch buch;
    private Rasterplan plan;
    private World marktstadt;
    private World kornmark;
    private final List<String> gemeldet = new ArrayList<>();

    @BeforeEach
    void vorher() {
        Serverprobe.einmalSetzen();
        Serverprobe.keineWeltenMehr();
        gemeldet.clear();
        marktstadt = Serverprobe.welt(MARKTSTADT);
        kornmark = Serverprobe.welt(KORNMARK);
        lager = new SpeicherLager();
        buch = buchMit(lager);
        plan = buch.plan();
    }

    private Besitzbuch buchMit(BesitzLager welches) {
        return new Besitzbuch(Stadtgrenze.marktstadt(), welches,
                new SpeicherKasse(Wirtschaftswerte.VORGABE_STARTGUTHABEN),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
    }

    /**
     * Eine Wache mit diesen Regeln fuer die Marktstadt und KEINER fuer die Kornmark.
     *
     * <p>Die Kornmark steht ausdruecklich mit einer leeren Liste darin und fehlt nicht:
     * Eine fehlende Welt folgte der Vorgabe, und hier soll der Unterschied zwischen
     * "hier nicht" und "nicht eingetragen" geprueft werden.
     */
    private Weltwache wache(Weltregel... fuerDieMarktstadt) {
        return wacheMitBuch(buch, fuerDieMarktstadt);
    }

    private Weltwache wacheMitBuch(Besitzbuch welches, Weltregel... fuerDieMarktstadt) {
        Weltregelwerte werte = new Weltregelwerte(Set.of(),
                Map.of(MARKTSTADT, Set.of(fuerDieMarktstadt), KORNMARK, Set.of()));
        return new Weltwache(() -> werte, welches, MARKTSTADT, mitschreiber());
    }

    /** Ein Protokoll, das seine Meldungen BEHAELT - fuer die Klage ueber die Ablage. */
    private Logger mitschreiber() {
        Logger protokoll = Logger.getLogger("WeltwacheProbe");
        protokoll.setUseParentHandlers(false);
        for (Handler alter : protokoll.getHandlers()) {
            protokoll.removeHandler(alter);
        }
        protokoll.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                gemeldet.add(satz.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return protokoll;
    }

    /** Eine Stelle in einer Welt. Die Hoehe ist der Wache gleich, sie liest x und z. */
    private static Location in(World welt, int x, int z) {
        return new Location(welt, x, 64, z);
    }

    /** Die Mitte der Parzelle (px/pz) als Weltstelle. */
    private Location mitteVon(int px, int pz) {
        return in(marktstadt, plan.mitteX(px), plan.mitteZ(pz));
    }

    // === Die weltweiten Regeln ==============================================

    @Test
    @DisplayName("Eine eingetragene Regel greift in ihrer Welt - und nur dort")
    void regelGreiftNurInIhrerWelt() {
        Weltwache wache = wache(Weltregel.EXPLOSION);

        assertTrue(wache.greift(mitteVon(0, 0), Weltregel.EXPLOSION));
        assertFalse(wache.greift(in(kornmark, 0, 0), Weltregel.EXPLOSION),
                "die Kornmark hat eine LEERE Liste - dort ist TNT das Werkzeug, mit dem"
                        + " gearbeitet wird");
    }

    @Test
    @DisplayName("Eine NICHT eingetragene Regel greift auch in ihrer Welt nicht")
    void nichtEingetrageneGreiftNicht() {
        // Die Gegenrichtung. Ohne sie waere ein "alle Regeln ueberall" ebenfalls gruen,
        // und dann verboete ein einziger Eintrag alles.
        Weltwache wache = wache(Weltregel.EXPLOSION);

        assertFalse(wache.greift(mitteVon(0, 0), Weltregel.FEUER));
        assertFalse(wache.greift(mitteVon(0, 0), Weltregel.KOLBEN));
    }

    @Test
    @DisplayName("Eine weltweite Regel greift AUCH auf freiem Grund und auf dem Weg")
    void weltweitGreiftAuchOhneBesitz() {
        // DIE ENTSCHEIDUNG DIESER SORTE, und sie unterscheidet sie von den Grenzregeln:
        // Wer keine Krater will, will sie auch nicht auf einer freien Parzelle und nicht
        // auf dem Weg. Nur den Besitz zu schuetzen waere die halbe Einstellung - ein
        // Spieler kratert dann die Nachbarschaft, und der naechste bekommt eine Parzelle
        // mit einem Loch daneben.
        //
        // Das Lager ist LEER: hier gehoert niemandem etwas.
        Weltwache wache = wache(Weltregel.EXPLOSION);
        Location frei = mitteVon(0, 0);

        assertTrue(buch.zustaendigerBesitz(MARKTSTADT, frei.getBlockX(),
                frei.getBlockZ()).isEmpty(), "zur Sicherheit: wirklich niemandes Grund");
        assertTrue(wache.greift(frei, Weltregel.EXPLOSION),
                "kein Besitz, und die Regel greift trotzdem");
        assertTrue(wache.greift(aufDemWeg(), Weltregel.EXPLOSION),
                "und auf dem Weg, wo nie jemandem etwas gehoeren kann");
    }

    @Test
    @DisplayName("Ohne Ort und ohne Welt greift nichts - und es wirft nicht")
    void ohneOrtGreiftNichts() {
        // Bukkit gibt einen Ort ohne Welt fuer eine entladene Welt. Ein Wurf im Ereignis
        // waere eine rote Konsolenmeldung bei jedem Lavatropfen.
        Weltwache wache = wache(Weltregel.EXPLOSION);

        assertFalse(wache.greift(null, Weltregel.EXPLOSION));
        assertFalse(wache.greift(new Location(null, 0, 64, 0), Weltregel.EXPLOSION));
    }

    // === Die Grenzregeln ====================================================

    @Test
    @DisplayName("Von draussen in fremden Besitz ist ein Uebergriff")
    void vonDraussenHineinIstUebergriff() {
        lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);

        assertTrue(wache.uebergriff(aufDemWeg(), mitteVon(0, 0)),
                "vom Weg in Annas Parzelle");
    }

    @Test
    @DisplayName("Von eigenem Besitz nach draussen ist KEINER")
    void vonDrinnenNachDraussenIstKeiner() {
        // DIE FRAGE IST BEWUSST EINSEITIG: Eine Farm am Parzellenrand laesst Wasser auf
        // den Weg laufen, und das ist niemandes Schaden. Die andere Richtung
        // mitzuverbieten waere naheliegend und falsch.
        lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);

        Location drin = mitteVon(0, 0);
        Location draussen = aufDemWeg();

        assertFalse(wache.uebergriff(drin, draussen), () -> lage(drin, draussen));
    }

    @Test
    @DisplayName("Innerhalb derselben Parzelle ist KEINER")
    void innerhalbDerselbenParzelleIstKeiner() {
        lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);

        Location eine = mitteVon(0, 0);
        Location andere = in(marktstadt, eine.getBlockX() + 1, eine.getBlockZ() + 1);

        assertTrue(buch.zustaendigerBesitz(MARKTSTADT, andere.getBlockX(),
                andere.getBlockZ()).isPresent(), "beide Stellen in derselben Parzelle");
        assertFalse(wache.uebergriff(eine, andere),
                "sonst funktionierte keine Farm auf der eigenen Parzelle");
    }

    @Test
    @DisplayName("Zwei Parzellen DESSELBEN Spielers sind zwei Grundstuecke")
    void zweiParzellenDesselbenSpielersSindZweiGrundstuecke() {
        // Verglichen werden die NUMMERN und nicht die Eigentuemer, und das ist der Fall,
        // der den Unterschied zeigt: Zwischen zwei Parzellen liegt ein Weg, der allen
        // gehoert. Wer dort Lava laufen laesst, schadet der Stadt - auch wenn beide
        // Enden ihm gehoeren.
        Parzelle eine = lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Parzelle andere = lager.anlegen(MARKTSTADT, 1, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);

        assertEquals(eine.eigentuemer(), andere.eigentuemer(),
                "derselbe Spieler - sonst prueft diese Probe etwas anderes");
        assertTrue(eine.id() != andere.id(), "aber zwei Nummern");
        assertTrue(wache.uebergriff(mitteVon(0, 0), mitteVon(1, 0)));
    }

    @Test
    @DisplayName("In einer Welt OHNE Parzellen gibt es keine Grenze zu ueberschreiten")
    void ohneParzellenKeineGrenze() {
        // Und das ist der Grund, warum die config.yml fuer die Freistaedte und die Werder
        // nur die weltweiten Regeln eintraegt: Kolben und Fluessigkeit braeuchten eine
        // Besitzgrenze, die es dort nicht gibt, und wuerden nichts tun.
        // DER RIEGEL BRAUCHT SEINEN ZUSTAND. Vorher standen hier die Koordinaten
        // 0/0 und 1/0 - und die sind AUCH in der Marktstadt Mauer. Damit war die
        // Probe gruen, ob der Weltriegel da war oder nicht.
        //
        // Jetzt dieselben Koordinaten, die in der Marktstadt ein Uebergriff sind:
        // Faellt der Riegel weg, fragt die Wache das Besitzbuch mit der
        // Parzellenwelt, findet Annas Parzelle und bricht ab.
        lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);
        Location drin = mitteVon(0, 0);

        assertTrue(wache.uebergriff(aufDemWeg(), drin),
                "in der Marktstadt IST das ein Uebergriff - sonst prueft der Satz darunter"
                        + " nichts");
        assertFalse(wache.uebergriff(
                        in(kornmark, aufDemWeg().getBlockX(),
                                aufDemWeg().getBlockZ()),
                        in(kornmark, drin.getBlockX(), drin.getBlockZ())),
                "dieselben Koordinaten in einer Welt ohne Raster");
    }

    @Test
    @DisplayName("Weg nach Weg ist KEIN Uebergriff - der haeufigste Fall im Spiel")
    void wegNachWegIstKeinUebergriff() {
        // Wasser, das zwischen zwei Parzellen ueber den Weg laeuft, beruehrt
        // niemandes Besitz. Haengte die Frage an der QUELLE, waere jeder Fluss auf
        // oeffentlichem Grund abgebrochen - und das ist die Haelfte der
        // Marktstadt.
        lager.anlegen(MARKTSTADT, 0, 0, ANNA, 0L);
        Weltwache wache = wache(Weltregel.FLUESSIGKEIT);
        Location weg = aufDemWeg();
        Location daneben = in(marktstadt, weg.getBlockX(), weg.getBlockZ() + 1);

        assertTrue(buch.zustaendigerBesitz(MARKTSTADT, daneben.getBlockX(),
                daneben.getBlockZ()).isEmpty(), "beide Enden gehoeren niemandem");
        assertFalse(wache.uebergriff(weg, daneben));
    }

    @Test
    @DisplayName("Faellt die Ablage aus, wird ABGEBROCHEN - und geklagt")
    void ausfallDerAblageBrichtAb() {
        // Die strenge Richtung, dieselbe Entscheidung wie in Schutzwache.darfHandeln: Ein
        // Netzfehler darf keinen Krater erlauben.
        Weltwache wache = wacheMitBuch(buchMit(new StoerLager()),
                Weltregel.FLUESSIGKEIT);

        assertTrue(wache.uebergriff(aufDemWeg(), mitteVon(0, 0)),
                "bei einem Ausfall gilt die Grenze, nicht der Zweifel");
        assertEquals(1, gemeldet.size(), "genau eine Klage: " + gemeldet);
        assertTrue(gemeldet.get(0).contains("[Weltregeln]")
                        && gemeldet.get(0).contains("Netz ist weg"),
                "die Klage muss die Ursache nennen: " + gemeldet.get(0));
        assertTrue(gemeldet.get(0).contains("IllegalStateException"),
                "und den FEHLERTYP: Die Ablage wirft IllegalStateException,"
                        + " ein Programmfehler etwas anderes. Ohne den Namen"
                        + " liest sich beides gleich, und der Betreiber"
                        + " startet die Datenbank neu, statt einen Fehler zu"
                        + " melden. " + gemeldet.get(0));
    }

    @Test
    @DisplayName("Die Klage ist GEDROSSELT - ein Lavastrom fragt mehrmals je Sekunde")
    void klageIstGedrosselt() {
        // Ohne Drossel schreibt ein ausgefallener Datenbankrechner in Sekunden ein
        // Protokoll voll und verdeckt damit jede andere Meldung.
        Weltwache wache = wacheMitBuch(buchMit(new StoerLager()),
                Weltregel.FLUESSIGKEIT);

        for (int lauf = 0; lauf < 50; lauf++) {
            assertTrue(wache.uebergriff(aufDemWeg(), mitteVon(0, 0)),
                    "abgebrochen wird JEDES Mal - gedrosselt ist nur die Klage");
        }

        assertEquals(1, gemeldet.size(),
                "50 Ausfaelle, eine Zeile im Protokoll: " + gemeldet.size());
    }

    // === Die Kolbenrechnung =================================================

    @Test
    @DisplayName("Der bewegte Block selbst gehoert jemandem")
    void kolbenSchiebtFremdenBlock() {
        lager.anlegen(MARKTSTADT, 0, 0, BERT, 0L);
        Weltwache wache = wache(Weltregel.KOLBEN);
        int randX = ersteParzellenspalte();

        // Der Kolben steht auf dem Weg, der bewegte Block in Berts Parzelle. Die
        // Richtung fuehrt WIEDER HINAUS - damit haengt dieser Fall allein am Block.
        Block kolben = marktstadt.getBlockAt(randX - 1, 64, plan.mitteZ(0));
        Block bewegt = marktstadt.getBlockAt(randX, 64, plan.mitteZ(0));

        assertTrue(wache.kolbenUeberschreitet(kolben, List.of(bewegt), BlockFace.WEST),
                "der bewegte Block gehoert Bert, der Kolben niemandem");
    }

    @Test
    @DisplayName("Oder erst sein ZIELORT - der zweite Teil der Rechnung")
    void kolbenSchiebtAufFremdenGrund() {
        // DER FALL, DEN DER ERSTE NICHT DECKT: Ein Block, der auf dem Weg steht und auf
        // die fremde Parzelle geschoben wird, ueberschreitet die Grenze erst durch die
        // Bewegung. Ohne diese Probe koennte die Haelfte hinter dem ODER fehlen.
        lager.anlegen(MARKTSTADT, 0, 0, BERT, 0L);
        Weltwache wache = wache(Weltregel.KOLBEN);
        int randX = ersteParzellenspalte();

        Block kolben = marktstadt.getBlockAt(randX - 2, 64, plan.mitteZ(0));
        Block bewegt = marktstadt.getBlockAt(randX - 1, 64, plan.mitteZ(0));

        assertTrue(buch.zustaendigerBesitz(MARKTSTADT, randX - 1, plan.mitteZ(0))
                .isEmpty(), "der bewegte Block selbst gehoert niemandem");
        assertTrue(wache.kolbenUeberschreitet(kolben, List.of(bewegt), BlockFace.EAST),
                "aber einen Schritt nach Osten liegt Berts Parzelle");
    }

    @Test
    @DisplayName("Ohne die Regel KOLBEN schiebt derselbe Kolben ungestoert")
    void ohneRegelSchiebtDerKolben() {
        // DER RIEGEL BRAUCHT SEINEN ZUSTAND: Ohne diesen Satz waeren die zwei Proben
        // darueber auch gruen, wenn kolbenUeberschreitet IMMER true gaebe.
        lager.anlegen(MARKTSTADT, 0, 0, BERT, 0L);
        Weltwache wache = wache(Weltregel.EXPLOSION);
        int randX = ersteParzellenspalte();

        Block kolben = marktstadt.getBlockAt(randX - 1, 64, plan.mitteZ(0));
        Block bewegt = marktstadt.getBlockAt(randX, 64, plan.mitteZ(0));

        assertFalse(wache.kolbenUeberschreitet(kolben, List.of(bewegt), BlockFace.WEST));
    }

    @Test
    @DisplayName("Ein Kolben, der nur eigenen Grund bewegt, schiebt")
    void kolbenAufEigenemGrundSchiebt() {
        // Redstone ist Spielinhalt. Eine Regel, die auch den eigenen Kolben anhaelt,
        // naehme mehr, als sie dem Nachbarn gibt.
        lager.anlegen(MARKTSTADT, 0, 0, BERT, 0L);
        Weltwache wache = wache(Weltregel.KOLBEN);
        Location mitte = mitteVon(0, 0);

        Block kolben = marktstadt.getBlockAt(mitte.getBlockX(), 64, mitte.getBlockZ());
        Block bewegt = marktstadt.getBlockAt(mitte.getBlockX() + 1, 64,
                mitte.getBlockZ());

        assertFalse(wache.kolbenUeberschreitet(kolben, List.of(bewegt), BlockFace.EAST));
    }

    // === Das Neuladen =======================================================

    @Test
    @DisplayName("Eine geaenderte Regel wirkt SOFORT - der Halter, kein Abbild")
    void geaenderteRegelWirktSofort() {
        // Ein Schutz, der erst beim naechsten Serverstart wirkt, fehlt genau dann, wenn
        // man ihn braucht - mitten in einem Grief. Darum kommen die Werte als Halter
        // herein; dieselbe Bauart wie beim Wirtschaftsstand.
        Weltregelwerte[] halter = {new Weltregelwerte(Set.of(), Map.of())};
        Weltwache wache = new Weltwache(() -> halter[0], buch, MARKTSTADT,
                mitschreiber());

        assertFalse(wache.greift(mitteVon(0, 0), Weltregel.EXPLOSION),
                "noch nichts eingetragen");

        halter[0] = new Weltregelwerte(Set.of(),
                Map.of(MARKTSTADT, EnumSet.of(Weltregel.EXPLOSION)));

        assertTrue(wache.greift(mitteVon(0, 0), Weltregel.EXPLOSION),
                "ohne Neustart - sonst waere /freimarken neuladen eine Zusage ohne"
                        + " Wirkung");
    }

    // === Der ANSCHLUSS: zwei Tafeln =========================================

    @Test
    @DisplayName("Acht Haken, alle auf HIGH und alle mit ignoreCancelled")
    void achtHakenRichtigEingestellt() {
        // Gelesen aus der ANMERKUNG und nicht aus dem Quelltext: Was zaehlt, ist, was
        // Bukkit sieht.
        int gefunden = 0;
        var ereignisse = new java.util.TreeSet<String>();
        for (var methode : Weltwache.class.getDeclaredMethods()) {
            EventHandler anmerkung = methode.getAnnotation(EventHandler.class);
            if (anmerkung == null) {
                continue;
            }
            gefunden++;
            assertEquals(1, methode.getParameterCount(),
                    methode.getName() + " nimmt nicht genau ein Ereignis");
            ereignisse.add(methode.getParameterTypes()[0].getSimpleName());
            assertEquals(EventPriority.HIGH, anmerkung.priority(),
                    methode.getName() + " laeuft nicht auf HIGH. Diese Wache"
                            + " ENTSCHEIDET - auf MONITOR waere die Entscheidung schon"
                            + " gefallen, und das ist die Ebene, auf der das Blockbuch"
                            + " mitschreibt. Beide duerfen sich nicht vertauschen.");
            assertTrue(anmerkung.ignoreCancelled(),
                    methode.getName() + " hat ignoreCancelled = false - dann entscheidet"
                            + " diese Wache noch einmal ueber ein Ereignis, das ein"
                            + " anderer Schutz schon abgebrochen hat, und fragt das"
                            + " Besitzbuch fuer nichts.");
        }

        assertEquals(9, gefunden,
                "erwartet werden NEUN Haken. Weniger heisst, dass ein Zweig nicht"
                        + " verdrahtet ist; mehr heisst, dass einer dazugekommen ist"
                        + " und hier mitgezaehlt werden muss. Gefunden: "
                        + ereignisse);

        // UND DIE EREIGNISARTEN, nicht nur ihre Zahl: Zwei Zuhoerer auf dasselbe
        // Ereignis waeren ebenfalls neun, und ein vergessenes Ereignis liesse sich
        // mit einem zweiten Haken auf ein schon gedecktes ausgleichen. Die Meldung
        // oben nennt bestimmte EREIGNISSE - also muss die Tafel sie auch pruefen.
        assertEquals(java.util.Set.of(
                        "BlockBurnEvent",
                        "BlockExplodeEvent",
                        "BlockFromToEvent",
                        "BlockPistonExtendEvent",
                        "BlockPistonRetractEvent",
                        "BlockSpreadEvent",
                        "EntityChangeBlockEvent",
                        "EntityExplodeEvent",
                        "HangingBreakEvent"),
                ereignisse,
                "Diese Ereignisse muss die Weltwache abfangen - jedes genau einmal."
                        + " Fehlt eines, kommt die Zerstoerung auf diesem Weg durch;"
                        + " steht eines zu viel darin, gehoert es in diese Tafel."
                        + " HangingBreakEvent deckt Bilderrahmen und Gemaelde: die sind"
                        + " Wesen und stehen in keiner blockList.");
    }

    @Test
    @DisplayName("Jeder Zweig fragt VOR dem Abbrechen nach seiner Regel")
    void jederZweigFragtSeineRegel() {
        // DIE TAFEL, DIE DIE UNERREICHBAREN ZWEIGE DECKT. Ohne sie koennte ein Zuhoerer
        // seinen Riegel verlieren und IMMER abbrechen - auch in einer Welt ohne jeden
        // Eintrag. Keine der Rechnungen oben wuerde davon rot, weil der Riegel in dem
        // Zweig sitzt, den ohne laufenden Server keine Probe bauen kann.
        //
        // Jedes Stueck wird am Ende SEINER Methode abgeschnitten: Ohne diesen Schnitt
        // reichte das letzte bis in kolbenUeberschreitet hinein und faende dort ein
        // Weltregel.KOLBEN, das gar nicht zu ihm gehoert.
        // JE ZWEIG DIE RICHTIGE REGEL, nicht bloss irgendeine: Ein Zweig, der
        // Weltregel.FEUER fragt und Explosionen abbricht, besteht eine Tafel, die
        // nur das Wortstueck "Weltregel." verlangt - und der Betreiber, der feuer
        // eintraegt, bekommt stumm auch den Sprengschutz.
        //
        // Die Tafel traegt darum eine Zeile JE ZUHOERER. Ein neuer Zuhoerer macht
        // sie rot, und das ist die Absicht: Seine Regel gehoert hier eingetragen.
        Map<String, String> erwartet = Map.of(
                "beimSprengen", "Weltregel.EXPLOSION",
                "beimBlocksprengen", "Weltregel.EXPLOSION",
                "beimHaengendenAbreissen", "Weltregel.EXPLOSION",
                "beimAbbrennen", "Weltregel.FEUER",
                "beimAusbreiten", "Weltregel.FEUER",
                "beimWesenblock", "Weltregel.WESEN",
                "beimFliessen", "Weltregel.FLUESSIGKEIT",
                "beimKolbenSchieben", "kolbenUeberschreitet(",
                "beimKolbenZiehen", "kolbenUeberschreitet(");

        String[] stuecke = Quellen.text(wachenDatei()).split("@EventHandler");

        assertEquals(10, stuecke.length,
                "neun Anmerkungen ergeben zehn Stuecke - gefunden: "
                        + (stuecke.length - 1));
        var gesehen = new java.util.TreeSet<String>();
        for (int nummer = 1; nummer < stuecke.length; nummer++) {
            int ende = stuecke[nummer].indexOf("\n    }");
            assertTrue(ende > 0, "Zweig " + nummer + " endet nicht auf der Ebene einer"
                    + " Methode - dann schneidet diese Tafel an der falschen Stelle");
            String rumpf = stuecke[nummer].substring(0, ende);

            String name = null;
            for (String kandidat : erwartet.keySet()) {
                if (rumpf.contains(" " + kandidat + "(")) {
                    name = kandidat;
                }
            }
            assertNotNull(name, "Dieser Zuhoerer steht in keiner Zeile der Tafel -"
                    + " seine Regel ist damit unbeaufsichtigt. Eintragen in erwartet:"
                    + "\n" + rumpf);
            gesehen.add(name);

            assertTrue(rumpf.contains(erwartet.get(name)),
                    name + " fragt nicht " + erwartet.get(name) + ". Entweder bricht"
                            + " er ohne jede Regel ab - dann wirkt er in JEDER Welt,"
                            + " auch in einer ohne Eintrag -, oder er fragt die FALSCHE"
                            + " Regel, und der Betreiber bekommt einen Schutz, den er"
                            + " nicht eingetragen hat:\n" + rumpf);

            // UND DASS ER ETWAS TUT. Ein Zweig, der seine Regel fragt und dann
            // nichts abbricht, besteht jede Textprobe und schuetzt nichts.
            assertTrue(rumpf.contains("setCancelled(true)")
                            || rumpf.contains("blockList().clear()"),
                    name + " fragt seine Regel, bricht aber nichts ab:\n" + rumpf);
        }

        assertEquals(erwartet.keySet(), gesehen,
                "jede Zeile der Tafel muss einen Zuhoerer im Quelltext haben - eine"
                        + " Zeile ohne ihn prueft fuer immer nichts");
    }

    @Test
    @DisplayName("Jede Regel hat mindestens einen Zuhoerer - keine wirkt ins Leere")
    void jedeRegelHatIhrenZuhoerer() {
        // DIE GEGENRICHTUNG, und sie faengt den teureren Fall: Eine Regel in der
        // config.yml, die kein Ereignis abfaengt, laesst sich eintragen und tut nichts.
        // Der Betreiber haelt die Welt dann fuer geschuetzt.
        //
        // Gelesen wird OHNE Kommentare: Sonst genuegte eine Nennung im Javadoc, und die
        // Regel waere wieder stumm wirkungslos.
        String quelle = Quellen.text(wachenDatei());

        for (Weltregel regel : Weltregel.values()) {
            assertTrue(quelle.contains("Weltregel." + regel.name()),
                    "Die Regel " + regel.schluessel() + " steht in der Aufzaehlung und"
                            + " laesst sich in die config.yml eintragen, aber kein Zweig"
                            + " der Weltwache fragt nach ihr. Sie taete nichts - und der"
                            + " Betreiber haelt die Welt fuer geschuetzt.");
        }
    }

    // === Werkzeug ===========================================================

    @Test
    @DisplayName("Die Wache braucht alle vier Angaben")
    void wacheBrauchtAlleAngaben() {
        Logger protokoll = mitschreiber();
        Weltregelwerte werte = Weltregelwerte.VORGABE;

        for (Runnable versuch : List.<Runnable>of(
                () -> new Weltwache(null, buch, MARKTSTADT, protokoll),
                () -> new Weltwache(() -> werte, null, MARKTSTADT, protokoll),
                () -> new Weltwache(() -> werte, buch, null, protokoll),
                () -> new Weltwache(() -> werte, buch, MARKTSTADT, null))) {
            assertThrows(IllegalArgumentException.class, versuch::run);
        }
        assertNotNull(new Weltwache(() -> werte, buch, MARKTSTADT, protokoll));
    }

    private static Path wachenDatei() {
        return Quellen.alle().stream()
                .filter(datei -> datei.getFileName().toString().equals("Weltwache.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Weltwache.java nicht gefunden"));
    }

    /**
     * Die erste x-Spalte der Parzelle (0/0) - und davor liegt Weg.
     *
     * <p>Gesucht und nicht gerechnet: Die Rasterweite steht in der Konfiguration, und
     * eine hingeschriebene Koordinate liefe beim ersten anderen Raster still durch den
     * falschen Zweig. Dieselbe Begruendung wie bei {@code SchutzwacheTest.ersterWegX}.
     */
    private int ersteParzellenspalte() {
        int z = plan.mitteZ(0);
        int x = plan.mitteX(0);
        while (plan.feldBei(x - 1, z) == Rasterplan.Feld.PARZELLE) {
            x--;
        }
        return x;
    }

    /** Eine Stelle, an der NIE jemandem etwas gehoeren kann - Weg oder Mauer. */
    private Location aufDemWeg() {
        return in(marktstadt, ersteParzellenspalte() - 1, plan.mitteZ(0));
    }

    /**
     * Was das Buch an zwei Stellen sagt - und was geklagt wurde.
     *
     * <p>Ohne diese Meldung sagt ein rotes {@code assertFalse} nur "expected: false",
     * und dann ist nicht zu entscheiden, ob das Ziel als besessen GELESEN wurde
     * oder ob die Ablage GEWORFEN hat - denn ein Wurf bricht ab und gibt ebenfalls
     * {@code true}. Eine Zeile in {@code gemeldet} entscheidet das.
     */
    private String lage(Location von, Location nach) {
        return "von " + von.getBlockX() + "/" + von.getBlockZ() + " (Besitz: "
                + buch.zustaendigerBesitz(MARKTSTADT, von.getBlockX(),
                        von.getBlockZ()).map(p -> "#" + p.id()).orElse("keiner")
                + ", Feld " + plan.feldBei(von.getBlockX(), von.getBlockZ())
                + ") nach " + nach.getBlockX() + "/" + nach.getBlockZ()
                + " (Besitz: "
                + buch.zustaendigerBesitz(MARKTSTADT, nach.getBlockX(),
                        nach.getBlockZ()).map(p -> "#" + p.id()).orElse("keiner")
                + ", Feld " + plan.feldBei(nach.getBlockX(), nach.getBlockZ())
                + "). Geklagt: " + gemeldet;
    }

    /**
     * Eine Ablage, die nicht antwortet.
     *
     * <p>Geerbt von {@link Lagerattrappe} und nicht neu geschrieben: Die Attrappe ist die
     * Lagerform, die dieses Projekt fuer seine Proben schon hat. Ein zweites eigenes
     * Lager hier waere ein zweiter Ort fuer denselben Vertrag - und bei der naechsten
     * Methode in {@link BesitzLager} liefen die zwei auseinander.
     */
    private static final class StoerLager extends Lagerattrappe {

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Netz ist weg");
        }
    }

    @Test
    @DisplayName("Ein PROGRAMMfehler ist an der Klage als solcher zu erkennen")
    void klageTrenntProgrammfehlerVomNetzfehler() {
        // DIE ZWEITE STOERUNGSRICHTUNG, und sie ist der teurere Fall: Ein Fehler im
        // Plugin bricht hier genauso ab wie ein Netzausfall - dauerhaft, und ohne
        // dass jemand etwas davon erfaehrt. Gemessen am 26.11.2026: Eine
        // Einspritzung liess ziel.get() auf ein leeres Optional laufen, und im
        // Protokoll stand "Besitz nicht nachsehbar: No value present".
        //
        // Unterscheidbar macht die zwei nur der TYPNAME. Darum steht er in der
        // Zeile, und darum prueft diese Probe ihn gegen den anderen Fall.
        Weltwache wache = wacheMitBuch(buchMit(new MackenLager()),
                Weltregel.FLUESSIGKEIT);

        assertTrue(wache.uebergriff(aufDemWeg(), mitteVon(0, 0)),
                "abgebrochen wird auch beim Programmfehler - die strenge Richtung");
        assertEquals(1, gemeldet.size(), "" + gemeldet);
        assertTrue(gemeldet.get(0).contains("NoSuchElementException"),
                "der Typname macht den Programmfehler sichtbar: "
                        + gemeldet.get(0));
        assertFalse(gemeldet.get(0).contains("IllegalStateException"),
                "und er darf NICHT nach einem Datenbankfehler aussehen: "
                        + gemeldet.get(0));
    }

    /**
     * Eine Ablage mit einem Fehler IM PLUGIN - kein Netzproblem.
     *
     * <p>{@code NoSuchElementException} ist genau der Typ, den ein
     * {@code Optional.get()} auf einem leeren Optional wirft. Am 26.11.2026 war das
     * keine Erfindung, sondern der gemessene Fall.
     */
    private static final class MackenLager extends Lagerattrappe {

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            // Der ECHTE Wurf und kein nachgebauter: Optional.get() auf einem
            // leeren Optional ist genau der Fehler, der am 26.11.2026 auftrat.
            return Optional.of(Optional.<Parzelle>empty().get());
        }
    }

    // === Die zwei neuen Entscheidungen ==================================

    @Test
    @DisplayName("Der fallende Block ist KEIN Wesen - sonst kommt Sand nie unten an")
    void sandfallZaehltNichtAlsWesen() {
        // DER TEUERSTE FEHLER DIESES SCHRITTES, gefunden beim Gegenlesen:
        // EntityChangeBlockEvent traegt auch das LANDEN eines FallingBlock. In der
        // Marktstadt und in Altfurt steht wesen, also waere jeder Sandblock beim
        // Bauen abgebrochen worden - und ein abgebrochenes Landen laesst den Block
        // verschwinden. Auf der EIGENEN Parzelle, weil die Regel weltweit gilt.
        assertFalse(Weltwache.wesenZaehlt(EntityType.FALLING_BLOCK),
                "Sand, Kies, Betonpulver, Amboss und Drachenei sind Bloecke in der"
                        + " Luft, keine Wesen, die etwas zerstoeren");

        // DIE GEGENRICHTUNG, und ohne sie waere ein "gar kein Wesen zaehlt"
        // ebenfalls gruen - dann taete die Regel nichts mehr.
        for (EntityType art : java.util.List.of(EntityType.ENDERMAN,
                EntityType.GOAT, EntityType.SHEEP, EntityType.RABBIT,
                EntityType.ZOMBIE, EntityType.VILLAGER)) {
            assertTrue(Weltwache.wesenZaehlt(art),
                    art + " ist ein Wesen, das Bloecke aendert - genau das, was die"
                            + " Regel wesen meint");
        }
    }

    @Test
    @DisplayName("Nur die EXPLOSION nimmt den Bilderrahmen - nicht jede Ursache")
    void nurDieExplosionNimmtDenRahmen() {
        // Bilderrahmen und Gemaelde sind Wesen und stehen in keiner blockList - die
        // zwei Sprengzweige schuetzen sie darum nicht. Der neunte Zuhoerer tut es.
        //
        // Aber NUR bei Explosion: PHYSICS traegt den Rahmen, dessen Wand ein
        // Spieler abgebaut hat, und der soll fallen duerfen. Braeche dieser Zweig
        // alles ab, waere ein Bilderrahmen in der Marktstadt nicht mehr abnehmbar.
        assertTrue(Weltwache.istSprengursache(
                org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.EXPLOSION));

        int andere = 0;
        for (var ursache
                : org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.values()) {
            if (ursache
                    == org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.EXPLOSION) {
                continue;
            }
            andere++;
            assertFalse(Weltwache.istSprengursache(ursache),
                    ursache + " ist keine Explosion - dieser Zweig darf sie nicht"
                            + " abbrechen");
        }
        assertTrue(andere >= 3,
                "nur " + andere + " andere Ursachen geprueft - dann prueft dieser"
                        + " Satz fast nichts");
    }

    @Test
    @DisplayName("Die Artriegel werfen bei falscher Verdrahtung - laut, nicht stumm")
    void artriegelWerfenBeiFalscherVerdrahtung() {
        // HIER BEKOMMT giltWeltweit IHREN VERBRAUCHER. Vorher stand die Zweiteilung
        // allein darin, welcher Zuhoerer uebergriff ruft - wer eine Regel in der
        // Aufzaehlung korrekt einordnete, aenderte an der Wirkung nichts, und wer
        // sie falsch einordnete, merkte es nie.
        Weltwache wache = wache(Weltregel.EXPLOSION, Weltregel.KOLBEN);
        Location ort = mitteVon(0, 0);

        // ZWEI FESTE PAARUNGEN ZUERST, und zwar bevor die Aufzaehlung befragt wird:
        // Die Schleife unten holt ihre Erwartung aus giltWeltweit - also aus dem
        // Gepruefen. Stellte jemand KOLBEN dort auf weltweit, bliebe sie gruen (die
        // Aufzaehlung selbst haelt WeltregelwerteTest). Diese zwei Saetze haengen an
        // keiner Einstellung und nageln die Sache fest.
        assertThrows(IllegalArgumentException.class,
                () -> wache.weltweitGreift(ort, Weltregel.KOLBEN),
                "kolben ist eine Grenzregel - ein weltweiter Zweig darf sie nicht"
                        + " fragen, sonst griffe sie auch auf freiem Grund");
        assertThrows(IllegalArgumentException.class,
                () -> wache.anGrenzenGreift(ort, Weltregel.EXPLOSION),
                "explosion gilt weltweit - ein Grenzzweig wuerde sie nur noch auf"
                        + " fremdem Grund greifen lassen, und der Krater auf der"
                        + " freien Parzelle bliebe");

        for (Weltregel regel : Weltregel.values()) {
            if (regel.giltWeltweit()) {
                assertThrows(IllegalArgumentException.class,
                        () -> wache.anGrenzenGreift(ort, regel),
                        regel.schluessel() + " gilt weltweit - ein Grenzzweig"
                                + " duerfte sie nicht fragen");
                wache.weltweitGreift(ort, regel);
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> wache.weltweitGreift(ort, regel),
                        regel.schluessel() + " gilt nur an Grenzen - ein"
                                + " weltweiter Zweig duerfte sie nicht fragen");
                wache.anGrenzenGreift(ort, regel);
            }
        }
    }

    @Test
    @DisplayName("Der Rueckzug rechnet GEGEN die Kolbenrichtung")
    void rueckzugRechnetGegenDieKolbenrichtung() {
        // Gemessen an der benutzten paper-api: getRetractLocation() ist
        // getBlock().getRelative(getDirection(), 2). Die Richtung zeigt also AUCH
        // beim Einfahren vom Kolben weg - der Block wandert dagegen. Mit
        // getDirection() prueefte der Zweig einen Ort, den das Ereignis nie
        // beruehrt.
        //
        // Eine Tafel und keine Rechnung: BlockPistonRetractEvent laesst sich ohne
        // laufenden Server nicht bauen. Die Rechnung selbst ist in beiden
        // Richtungen geprueft (kolbenSchiebtFremdenBlock, kolbenSchiebtAufFremdenGrund).
        String[] stuecke = Quellen.text(wachenDatei()).split("@EventHandler");
        String ziehen = null;
        String schieben = null;
        for (String stueck : stuecke) {
            if (stueck.contains(" beimKolbenZiehen(")) {
                ziehen = stueck;
            } else if (stueck.contains(" beimKolbenSchieben(")) {
                schieben = stueck;
            }
        }
        assertNotNull(ziehen, "beimKolbenZiehen nicht gefunden");
        assertNotNull(schieben, "beimKolbenSchieben nicht gefunden");

        assertTrue(ziehen.contains("getOppositeFace()"),
                "beimKolbenZiehen gibt die Kolbenrichtung unverdreht herein. Dann"
                        + " prueft die Rechnung einen Ort, den das Ereignis nie"
                        + " beruehrt, und laesst den echten Zielort ungefragt - ein"
                        + " Klebekolben mitten auf der eigenen Parzelle stuende"
                        + " still.");
        assertFalse(schieben.contains("getOppositeFace()"),
                "beimKolbenSchieben darf NICHT verdrehen: dort wandern die Bloecke"
                        + " mit der Richtung. Beide gleich zu behandeln ist genau"
                        + " der Fehler, nur in die andere Richtung.");
    }
}
