package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherRaenge;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.SpeicherTeam;
import de.freimarken.kern.Teamwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Wache, die aus dem abgelegten Rang Rechte macht.
 *
 * <p>Geprueft wird ueber den {@link Rechtegriff} und nicht ueber einen laufenden Server:
 * Dahinter steht {@code PermissionAttachment}, eine Klasse ohne Schnittstelle, die ein
 * echtes Plugin verlangt. Was hier gemessen wird, ist genau das, was die Wache
 * ENTSCHEIDET - welche Rechtenamen sie vergibt und wann sie sie zuruecknimmt.
 */
@DisplayName("Die Rangwache")
class RangwacheTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    private Griffprobe griff;
    private SpeicherRaenge ablage;

    /** Die Teamrollen - die zweite Quelle der Rechte [Schritt 16a Teil A]. */
    private SpeicherTeam team;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        griff = new Griffprobe();
        ablage = new SpeicherRaenge();
        team = new SpeicherTeam();
    }

    @Test
    @DisplayName("Wer nie aufgestiegen ist, bekommt das Buendel der UNTERSTEN Stufe")
    void neulingBekommtDieUnterste() {
        Spielerprobe anna = spieler(1);

        wache(Rangwerte.VORGABE).beimBeitritt(beitritt(anna));

        // Ein Neuling hat keine Zeile in der Ablage. Bekaeme er darum NICHTS, stuende er
        // ohne jedes Rangrecht da - und im Spiel sieht das aus wie die Regel.
        // freimarken.stufe.reisender ist seit dem 25.11.2026 dabei: Die unterste
        // Stufe ist eine ERREICHTE Stufe, und eine Welt mit ab-rang: reisender muss
        // jedem offenstehen, der ueberhaupt in der Leiter steht.
        assertEquals(Set.of("freimarken.parzellen.1", "freimarken.gruppe.1",
                        "freimarken.inseln.1", "freimarken.wolken.1",
                        "freimarken.freistaedte.1", "freimarken.stufe.reisender"),
                griff.gesetzt(anna));
    }

    @Test
    @DisplayName("Ein Buerger bekommt AUCH, was der Siedler gibt")
    void buergerBehaeltDenLaden() {
        Spielerprobe anna = spieler(2);
        aufstieg(anna, "buerger");

        wache(Rangwerte.VORGABE).beimBeitritt(beitritt(anna));

        // DER FALL, AN DEM DIE ENTSCHEIDUNG HAENGT: buerger nennt laden.mieten nicht noch
        // einmal. Zaehlte nur die erreichte Stufe, naehme der Aufstieg dem Spieler seinen
        // Laden weg - und niemand meldete es, weil die Absage dieselbe ist wie bei einem,
        // der das Recht nie hatte.
        assertTrue(griff.gesetzt(anna).contains("freimarken.laden.mieten"),
                griff.gesetzt(anna).toString());
        assertTrue(griff.gesetzt(anna).contains("freimarken.freistadt.gruenden"));
        assertTrue(griff.gesetzt(anna).contains("freimarken.parzellen.4"),
                "und die Zahl seiner Stufe: " + griff.gesetzt(anna));
    }

    @Test
    @DisplayName("Ein Aufstieg zur Laufzeit wirkt SOFORT, ohne neues Anmelden")
    void aufstiegWirktSofort() {
        Spielerprobe anna = spieler(3);
        Rangwache wache = wache(Rangwerte.VORGABE);
        wache.beimBeitritt(beitritt(anna));
        assertTrue(griff.gesetzt(anna).contains("freimarken.parzellen.1"));

        aufstieg(anna, "siedler");
        wache.vergibNeu(anna.spieler());

        // Ohne diesen Weg waere der Aufstieg eine Nachricht, der nichts folgt: Der
        // Spieler liest "du bist jetzt Siedler" und kann bis zum naechsten Anmelden
        // genau so viel wie vorher.
        assertTrue(griff.gesetzt(anna).contains("freimarken.parzellen.2"),
                griff.gesetzt(anna).toString());
        assertTrue(griff.gesetzt(anna).contains("freimarken.laden.mieten"));
    }

    @Test
    @DisplayName("Das neue Buendel ERSETZT das alte, es kommt nicht dazu")
    void buendelWirdErsetzt() {
        Spielerprobe anna = spieler(4);
        Rangwache wache = wache(Rangwerte.VORGABE);
        wache.beimBeitritt(beitritt(anna));

        aufstieg(anna, "siedler");
        wache.vergibNeu(anna.spieler());

        // Heute folgenlos - Rangzahl nimmt die hoechste. Aber eine Leiter, die je eine
        // Zahl SENKT, wirkte nie, wenn die alte danebenstehen bliebe. Und der Betreiber
        // saehe in der Rechteliste zwei Zahlen fuer dieselbe Grenze.
        assertFalse(griff.gesetzt(anna).contains("freimarken.parzellen.1"),
                "die Zahl des Reisenden muss weg sein: " + griff.gesetzt(anna));
        assertEquals(1, griff.geloest(anna),
                "und zwar, weil der Griff vorher GELOEST wurde");
    }

    @Test
    @DisplayName("Beim Verlassen wird der Griff geloest")
    void beimVerlassenGeloest() {
        Spielerprobe anna = spieler(5);
        Rangwache wache = wache(Rangwerte.VORGABE);
        wache.beimBeitritt(beitritt(anna));

        wache.beimVerlassen(new PlayerQuitEvent(anna.spieler(), Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED));

        // Das Anhaengsel geht ohnehin mit dem Spieler; die Merkliste des Griffs tut es
        // nicht und wuechse sonst um einen Eintrag je Beitritt, solange der Server
        // laeuft.
        assertEquals(1, griff.geloest(anna));
    }

    @Test
    @DisplayName("Ist die Leiter aus, haelt der Griff NICHTS")
    void leiterAusGibtNichts() {
        Spielerprobe anna = spieler(6);
        aufstieg(anna, "buerger");

        wache(Rangwerte.VORGABE.mitAn(false)).beimBeitritt(beitritt(anna));

        // Sonst wirkte raenge.an: false nur auf den AUFSTIEG und nicht auf das, was ein
        // Aufgestiegener schon haelt - der Schalter waere halb an, und eine halb wirkende
        // Einstellung meldet sich nirgends.
        assertEquals(Set.of(), griff.gesetzt(anna));
    }

    @Test
    @DisplayName("Eine LEERE Leiter gibt ebenfalls nichts - und stuerzt nicht ab")
    void leereLeiterGibtNichts() {
        Spielerprobe anna = spieler(7);

        wache(Rangwerte.VORGABE.mitLeiter(List.of())).beimBeitritt(beitritt(anna));

        assertEquals(Set.of(), griff.gesetzt(anna));
    }

    @Test
    @DisplayName("Ein Rang, den die Leiter nicht kennt, faellt zurueck UND wird gemeldet")
    void unbekannterRangWirdGemeldet() {
        Spielerprobe anna = spieler(8);
        aufstieg(anna, "fuerst");
        List<String> gemeldet = new ArrayList<>();

        wache(Rangwerte.VORGABE, protokollNach(gemeldet))
                .beimBeitritt(beitritt(anna));

        // Der Rueckfall allein ist das, was niemand bemerkt: Im Spiel sieht es aus, als
        // waere der Spieler nie aufgestiegen. Darum die Zeile - und sie muss den Rang
        // NENNEN, sonst weiss der Betreiber nicht, welchen er wieder eintragen soll.
        // freimarken.stufe.reisender ist seit dem 25.11.2026 dabei: Die unterste
        // Stufe ist eine ERREICHTE Stufe, und eine Welt mit ab-rang: reisender muss
        // jedem offenstehen, der ueberhaupt in der Leiter steht.
        assertEquals(Set.of("freimarken.parzellen.1", "freimarken.gruppe.1",
                        "freimarken.inseln.1", "freimarken.wolken.1",
                        "freimarken.freistaedte.1", "freimarken.stufe.reisender"),
                griff.gesetzt(anna));
        assertEquals(1, gemeldet.size(), gemeldet.toString());
        assertTrue(gemeldet.get(0).contains("fuerst"), gemeldet.get(0));
    }

    @Test
    @DisplayName("Derselbe unbekannte Rang wird nur EINMAL gemeldet")
    void unbekannterRangNurEinmal() {
        Spielerprobe anna = spieler(9);
        Spielerprobe bert = spieler(10);
        aufstieg(anna, "fuerst");
        aufstieg(bert, "fuerst");
        List<String> gemeldet = new ArrayList<>();
        Rangwache wache = wache(Rangwerte.VORGABE, protokollNach(gemeldet));

        wache.beimBeitritt(beitritt(anna));
        wache.beimBeitritt(beitritt(bert));
        wache.beimBeitritt(beitritt(anna));

        // Sonst stuende die Zeile bei jedem Beitritt jedes Betroffenen erneut im
        // Protokoll - der sicherste Weg, dass sie niemand liest.
        assertEquals(1, gemeldet.size(), gemeldet.toString());
    }

    @Test
    @DisplayName("Eine eigene Leiter gibt eigene Zahlen - nichts ist fest eingebaut")
    void eigeneLeiter() {
        Spielerprobe anna = spieler(11);
        aufstieg(anna, "haendler");
        var eigene = Rangwerte.VORGABE.mitLeiter(List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of("parzellen", 2),
                        List.of()),
                new Rangwerte.Stufe("haendler", Map.of(Bedingungsart.GUTHABEN, 500L),
                        Map.of("parzellen", 7), List.of("shop.bund"))));

        wache(eigene).beimBeitritt(beitritt(anna));

        // Keine Zahl und kein Name aus Plan 9 steht im Programm. Wer hier die Vorgabe
        // fest einbaute, gaebe auf einem Server mit eigenen Raengen Rechte aus, die es
        // dort nicht gibt.
        // Die zwei Stufenrechte kommen aus DIESER Leiter und nicht aus der Vorgabe -
        // gast und haendler gibt es in Plan 9 nicht. Genau das ist hier die
        // Zusicherung: Die Namen entstehen aus der Datei.
        assertEquals(Set.of("freimarken.parzellen.7", "freimarken.shop.bund",
                        "freimarken.stufe.gast", "freimarken.stufe.haendler"),
                griff.gesetzt(anna));
    }

    // ------------------------------------------------------------------------
    // DIE TEAMROLLEN ALS ZWEITE QUELLE [Schritt 16a Teil A]
    //
    // Das Anhaengsel wird ERSETZT und nicht ergaenzt, also gibt es genau EINEN Zug, der
    // die Rechte setzt. Diese vier Proben halten fest, dass er BEIDE Quellen vereint -
    // ohne sie waere die Teamrolle unsichtbar wirkungslos oder, schlimmer, der Loescher
    // der Rangrechte.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rang UND Rolle geben zusammen - keine der beiden verdraengt die andere")
    void rangUndRolleZusammen() {
        Spielerprobe anna = spieler(20);
        aufstieg(anna, "siedler");
        team.gib(anna.spieler().getUniqueId(), "moderator", MITTAGS, null);

        wache(Rangwerte.VORGABE, Teamwerte.VORGABE).beimBeitritt(beitritt(anna));

        // BEIDES in einer Menge. Waere es nur eines, haette der zweite Zug den ersten
        // geloescht - und zwar je nach Reihenfolge der Beitrittszuhoerer mal so und mal
        // anders. Das ist der Fehler, der sich nicht einmal zuverlaessig zeigt.
        Set<String> gesetzt = griff.gesetzt(anna);
        assertTrue(gesetzt.contains("freimarken.parzellen.2"),
                "die Rangzahl des Siedlers: " + gesetzt);
        assertTrue(gesetzt.contains("freimarken.blockbuch"),
                "und das Recht des Moderators: " + gesetzt);
        assertTrue(gesetzt.contains("freimarken.spielzeit.andere"), gesetzt.toString());
    }

    @Test
    @DisplayName("Ist die LEITER aus, bleiben die Rollenrechte trotzdem")
    void leiterAusLaesstDieRolle() {
        Spielerprobe anna = spieler(21);
        aufstieg(anna, "buerger");
        team.gib(anna.spieler().getUniqueId(), "moderator", MITTAGS, null);

        wache(Rangwerte.VORGABE.mitAn(false), Teamwerte.VORGABE)
                .beimBeitritt(beitritt(anna));

        // DER FALL, DER BIS ZUM 24.11.2026 GEBROCHEN WAR: In Rangwache.vergibNeu stand
        // ein frueher Rueckweg, der bei abgeschalteter Leiter Set.of() setzte. Mit
        // Teamrollen daneben haette ein Betreiber die Raenge abgeschaltet, und am
        // naechsten Tag koennte sein Moderator nichts mehr - zwei Einstellungen, von
        // denen jede fuer sich richtig aussieht.
        //
        // Die Probe leiterAusGibtNichts daneben bleibt gruen, weil DORT niemand eine
        // Rolle hat. Sie allein konnte den Fehler darum nicht fangen.
        Set<String> gesetzt = griff.gesetzt(anna);
        assertTrue(gesetzt.contains("freimarken.blockbuch"),
                "die Rolle wirkt weiter: " + gesetzt);
        assertFalse(gesetzt.contains("freimarken.parzellen.4"),
                "aber keine Rangzahl mehr: " + gesetzt);
    }

    @Test
    @DisplayName("Ohne Rollen in der Datei bleiben die Rangrechte")
    void keinTeamLaesstDenRang() {
        Spielerprobe anna = spieler(22);
        aufstieg(anna, "siedler");
        team.gib(anna.spieler().getUniqueId(), "moderator", MITTAGS, null);

        // Die Gegenrichtung der Probe darueber: Diesmal ist das TEAM leer und die Leiter
        // an. Ohne diese Probe koennte der Rueckweg im Rechtebuendel an der falschen
        // Stelle stehen, und es fiele nicht auf.
        wache(Rangwerte.VORGABE, Teamwerte.VORGABE.mitRollen(List.of()))
                .beimBeitritt(beitritt(anna));

        Set<String> gesetzt = griff.gesetzt(anna);
        assertTrue(gesetzt.contains("freimarken.parzellen.2"), gesetzt.toString());
        assertFalse(gesetzt.contains("freimarken.blockbuch"),
                "eine Rolle, die die Datei nicht kennt, gibt nichts: " + gesetzt);
    }

    @Test
    @DisplayName("Eine weggefallene Rolle gibt nichts und wird EINMAL gemeldet")
    void weggefalleneRolleWirdGemeldet() {
        Spielerprobe anna = spieler(23);
        Spielerprobe bert = spieler(24);
        team.gib(anna.spieler().getUniqueId(), "hausmeister", MITTAGS, null);
        team.gib(bert.spieler().getUniqueId(), "hausmeister", MITTAGS, null);
        List<String> gemeldet = new ArrayList<>();
        Rangwache wache = wache(Rangwerte.VORGABE, Teamwerte.VORGABE,
                protokollNach(gemeldet));

        wache.beimBeitritt(beitritt(anna));
        wache.beimBeitritt(beitritt(bert));
        wache.beimBeitritt(beitritt(anna));

        // Der Betroffene verliert still die Rechte - im Spiel sieht es aus, als waere
        // die Rolle nie vergeben worden. Darum die Zeile, und sie muss die ROLLE nennen.
        // Einmal und nicht je Beitritt: sonst liest sie niemand.
        assertEquals(1, gemeldet.size(), gemeldet.toString());
        assertTrue(gemeldet.get(0).contains("hausmeister"), gemeldet.get(0));
        assertTrue(griff.gesetzt(anna).contains("freimarken.parzellen.1"),
                "der Rang wirkt weiter: " + griff.gesetzt(anna));
    }

    @Test
    @DisplayName("Ohne Ablage, Werte, Griff oder Protokoll gibt es keine Wache")
    void riegel() {
        var wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Logger stumm = Logger.getLogger("RangwacheRiegel");

        var sprache = Sprachprobe.spracheAusDemJar();
        var lauf = lauf(wirtschaft);
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(null, team, wirtschaft, griff, lauf, sprache,
                        stumm));
        // Die TEAMABLAGE ist Pflicht wie die Rangablage [Schritt 16a Teil A]: Ohne
        // sie waere der Griff eine Zeile spaeter ein Wurf beim Beitritt, und zwar
        // bei JEDEM - also auch bei dem, der es melden koennte.
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, null, wirtschaft, griff, lauf, sprache,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, team, null, griff, lauf, sprache,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, team, wirtschaft, null, lauf, sprache,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, team, wirtschaft, griff, null, sprache,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, team, wirtschaft, griff, lauf, null,
                        stumm));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangwache(ablage, team, wirtschaft, griff, lauf, sprache,
                        null));
    }

    private Rangwache wache(Rangwerte werte) {
        return wache(werte, Logger.getLogger("RangwacheProbe-" + UUID.randomUUID()));
    }

    private Rangwache wache(Rangwerte werte, Logger protokoll) {
        // OHNE Rollen, damit jede Probe oben genau das messen kann, was sie messen will:
        // die Rechte aus dem RANG. Eine Vorgabe mit fuenf Rollen hier liesse eine Probe,
        // die Set.of() erwartet, von der Rollenliste abhaengen.
        return wache(werte, Teamwerte.VORGABE.mitRollen(List.of()), protokoll);
    }

    private Rangwache wache(Rangwerte werte, Teamwerte rollen) {
        return wache(werte, rollen,
                Logger.getLogger("RangwacheProbe-" + UUID.randomUUID()));
    }

    private Rangwache wache(Rangwerte werte, Teamwerte rollen, Logger protokoll) {
        var wirtschaft = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitRaengen(werte).mitTeam(rollen));
        return new Rangwache(ablage, team, wirtschaft, griff, lauf(wirtschaft),
                Sprachprobe.spracheAusDemJar(), protokoll);
    }

    /**
     * Ein Aufstiegslauf, der NICHTS findet - diese Proben messen das VERGEBEN.
     *
     * <p>Alle vier Messquellen stehen auf leer: keine Spielzeit, keine Parzelle, kein
     * Beruf, kein Guthaben. Damit steht keinem Spieler ein Aufstieg zu, und die Proben
     * hier sehen genau das, was sie messen wollen - welche Rechte aus einem ABGELEGTEN
     * Rang werden. Dass der Aufstieg selbst wirkt, prueft AufstiegslaufTest.
     *
     * <p>Ein null waere der kuerzere Weg und der falsche: Die Wache ruft den Lauf bei
     * jedem Beitritt, und eine Probe, die mit null gruen ist, waere mit einem echten
     * Lauf vielleicht rot.
     */
    private Aufstiegslauf lauf(Wirtschaftsstand wirtschaft) {
        return new Aufstiegslauf(wirtschaft, ablage, new SpeicherSpielzeit(),
                new SpeicherLager(), new SpeicherBerufe(), new SpeicherKasse(0L),
                // Ein Rundgang OHNE Stationen: Diese Proben messen den Rang und nicht
                // den Weg. Dass er ohne Stationen nicht abgeschlossen ist, prueft
                // AufstiegslaufTest ausdruecklich.
                new de.freimarken.kern.Rundgang(new de.freimarken.kern.SpeicherRundgang(),
                        de.freimarken.kern.Rundgangsverzeichnis.ohneStationen(),
                        java.time.Clock.systemUTC()),
                "freimarken:marktstadt", java.time.Clock.systemUTC(),
                Logger.getLogger("AufstiegLeer"));
    }

    private void aufstieg(Spielerprobe wer, String rang) {
        UUID kennung = wer.spieler().getUniqueId();
        assertTrue(ablage.setze(new Rangablage.Antrag(kennung, rang, MITTAGS),
                Rangstand.leer(kennung)), "die Vorbereitung muss durchgehen: " + rang);
    }

    private static Spielerprobe spieler(int nummer) {
        return new Spielerprobe("Spieler" + nummer, Spielerprobe.kennungAus(nummer));
    }

    private static PlayerJoinEvent beitritt(Spielerprobe wer) {
        return new PlayerJoinEvent(wer.spieler(), Component.empty());
    }

    /** Ein Protokoll, das seine Warnungen in eine Liste schreibt. */
    private static Logger protokollNach(List<String> ziel) {
        Logger eigener = Logger.getLogger("RangwacheMitschrift-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                if (satz.getLevel().intValue() >= Level.WARNING.intValue()) {
                    ziel.add(satz.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }

    /**
     * Ein {@link Rechtegriff}, der mitschreibt statt zu haengen.
     *
     * <p>Er haelt sich an dieselbe Zusage wie die echte Fassung - setzen ERSETZT -, denn
     * sonst pruefte die Probe eine Mechanik, die es nicht gibt.
     */
    private static final class Griffprobe implements Rechtegriff {

        private final Map<UUID, Set<String>> gehalten = new LinkedHashMap<>();
        private final Map<UUID, Integer> loesungen = new LinkedHashMap<>();

        @Override
        public void setze(Player spieler, Set<String> rechte) {
            loese(spieler);
            gehalten.put(spieler.getUniqueId(), Set.copyOf(rechte));
        }

        @Override
        public void loese(Player spieler) {
            if (gehalten.remove(spieler.getUniqueId()) != null) {
                loesungen.merge(spieler.getUniqueId(), 1, Integer::sum);
            }
        }

        Set<String> gesetzt(Spielerprobe wer) {
            return gehalten.getOrDefault(wer.spieler().getUniqueId(), Set.of());
        }

        int geloest(Spielerprobe wer) {
            return loesungen.getOrDefault(wer.spieler().getUniqueId(), 0);
        }
    }
}
