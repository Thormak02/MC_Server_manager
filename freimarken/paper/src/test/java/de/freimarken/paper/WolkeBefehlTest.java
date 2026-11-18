package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkengrenze;
import de.freimarken.kern.Wolkenwerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /wolke} GETIPPT - vom Wort bis zur Insel.
 *
 * <p>Dieselbe Bauart wie {@link WerderBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest und was im Buch steht.
 *
 * <p><b>Drei Proben kann nur ein getippter Befehl machen</b>, und jede bewacht eine
 * Entscheidung dieses Schritts: dass {@code /wolke mitbauer} ohne Richtung eine deutsche
 * Auskunft gibt und nicht Brigadiers englisches "Unknown or incomplete command" (ein
 * passendes Literal verdeckt jedes Geschwister vollstaendig), dass die HOECHSTZAHL
 * wirklich aus dem RECHT kommt, und dass ein fehlgeschlagenes AUFSETZEN den Spieler
 * erreicht - denn dann gehoert ihm ein Feld aus Luft.
 */
@DisplayName("/wolke getippt")
class WolkeBefehlTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    private SpeicherKasse kasse;
    private SpeicherLager lager;
    private Wolkenbuch buch;
    private Spielerprobe anna;
    private Spielerprobe bert;

    /** Was der Aufsetzer gesetzt hat - und ob er es durfte. */
    private final List<String> aufgesetzt = new ArrayList<>();
    private boolean aufsetzenGelingt = true;

    /**
     * Wohin gereist wurde - oder {@code null}.
     *
     * <p>MITGESCHRIEBEN und nicht wirklich gereist, und das ist ein Fund dieser Probe:
     * Hinter {@code Reise.fuehre} liegt {@code Material.isSolid()}, und das wirft ohne
     * laufenden Server ("No RegistryAccess implementation found"). Ein erfolgreiches
     * Reisen ist damit von keiner Probe durchlaufbar - und {@code /wolke nehmen} reist
     * IMMER. Darum ist die Reise eine Naht, und hier steht ihre Mitschrift.
     */
    private Location gereist;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
        Serverprobe.nenne(Spielerprobe.kennungAus(1), "Anna");
        Serverprobe.nenne(Spielerprobe.kennungAus(2), "Bert");
    }

    @BeforeEach
    void vorher() throws CommandSyntaxException {
        Serverprobe.keineWeltenMehr();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        lager = new SpeicherLager();
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        aufgesetzt.clear();
        aufsetzenGelingt = true;
        gereist = null;
        // RECHT_SOFORT dazu: Damit laeuft die Aufwaermzeit sofort los, und der Zweig
        // ist ohne laufenden Zeitplaner ueberhaupt durchlaufbar.
        anna = spieler("Anna", 1, WolkeBefehle.RECHT, WolkeBefehle.RECHT_NEHMEN,
                Aufwaermzeit.RECHT_SOFORT);
        bert = spieler("Bert", 2, WolkeBefehle.RECHT, WolkeBefehle.RECHT_NEHMEN,
                Aufwaermzeit.RECHT_SOFORT);
    }

    // --------------------------------------------------------------------- Liste

    @Test
    @DisplayName("/wolke sagt ab Werk, dass man keine Insel hat")
    void nackterBefehl() throws CommandSyntaxException {
        probe().tippe("wolke", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("keine Wolkeninsel"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("/wolke nehmen"),
                "die Zeile MUSS den Weg nennen: " + gelesen().get(0));
    }

    @Test
    @DisplayName("Nach dem Nehmen zeigt die Liste Kopf, Feld und die Obergrenze")
    void listeMitInsel() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        anna.vergessen();

        probe.tippe("wolke", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Deine Wolkeninsel"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("Insel "), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("Mehr als eine Insel"), gelesen().get(2));
    }

    @Test
    @DisplayName("Die Liste nennt die Mitbauer in der Zeile ihrer Insel")
    void listeMitMitbauern() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        stelleAufDieEigeneInsel(anna);
        probe.tippe("wolke mitbauer dazu Bert", anna);
        anna.vergessen();

        probe.tippe("wolke", anna);

        assertTrue(gelesen().get(1).contains("Mitbauer: "), gelesen().get(1));
        assertTrue(gelesen().get(1).contains("Bert"), gelesen().get(1));
    }

    @Test
    @DisplayName("Die Liste geht UEBERALL - auch aus der Marktstadt heraus")
    void listeGehtUeberall() throws CommandSyntaxException {
        // Wer wissen will, wo seine Insel liegt, steht gerade nicht darauf - er kann es
        // gar nicht, wenn er noch keine hat.
        anna.inWelt(Serverprobe.welt("freimarken:marktstadt"));

        probe().tippe("wolke", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertFalse(gelesen().get(0).contains("Wolkenwelt"),
                "keine Absage wegen der Welt: " + gelesen().get(0));
    }

    // --------------------------------------------------------------------- Nehmen

    @Test
    @DisplayName("/wolke nehmen gibt eine Insel, setzt sie auf und sagt GRATIS")
    void nehmen() throws CommandSyntaxException {
        probe().tippe("wolke nehmen", anna);

        assertEquals(1, lager.anzahlVon(WELT, anna.kennung()));
        Parzelle insel = buch.inselnVon(WELT, anna.kennung()).get(0);
        assertEquals(List.of(insel.x() + "/" + insel.z()), aufgesetzt,
                "die Bloecke muessen stehen - ohne sie faellt der Spieler bei der"
                        + " Ankunft");
        assertTrue(gereist != null,
                "und er wird HINGEBRACHT: Ohne Reise stuende er weiter auf der"
                        + " Ankunftsplattform und wuesste nicht, wohin");
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("gehoert jetzt dir"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("Truhe"), gelesen().get(1));
        assertEquals(1_000_000L, kasse.guthaben(anna.kennung()), "gratis");
    }

    @Test
    @DisplayName("Gelingt das AUFSETZEN nicht, liest der Spieler es")
    void aufsetzenMisslingt() throws CommandSyntaxException {
        // Der Fall, den es in keinem anderen Bereich gibt: Das Feld ist eingetragen, die
        // Bloecke stehen nicht. Ohne diese Zeile reist der Spieler hin und faellt - und
        // er haelt es fuer einen Fehler des Servers, nicht fuer einen behebbaren Zustand.
        aufsetzenGelingt = false;

        probe().tippe("wolke nehmen", anna);

        assertEquals(1, lager.anzahlVon(WELT, anna.kennung()),
                "die Insel GEHOERT ihm trotzdem - das Buch hat geschrieben");
        // VIER Zeilen: Beide Saetze sind zweizeilig. Erst die Zusage, dann die
        // Einschraenkung - und in dieser Reihenfolge, denn die Zusage ist wahr.
        assertEquals(4, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(2).contains("liess sich nicht aufsetzen"),
                gelesen().get(2));
        assertTrue(gelesen().get(3).contains("zuruecksetzen"), gelesen().get(3));
        assertTrue(gereist == null,
                "und er wird NICHT hingebracht - dort ist nichts, worauf er stehen"
                        + " koennte");
    }

    @Test
    @DisplayName("Die zweite Insel kostet die Absage - mit der Zahl aus dem RECHT")
    void hoechstzahlAusDemRecht() throws CommandSyntaxException {
        // Die Probe, die es im Buch nicht geben kann: Dort kommt die Zahl von aussen
        // herein. Hier wird sie aus den RECHTEN des Spielers gelesen - und ohne diese
        // Probe hatte die Rechnung keinen einzigen getippten Durchlauf (der Fund vom
        // 18.11.2026 bei den Meerinseln).
        Spielerprobe reich = spieler("Reich", 3, WolkeBefehle.RECHT,
                WolkeBefehle.RECHT_NEHMEN, Aufwaermzeit.RECHT_SOFORT,
                "freimarken.wolken.3");
        Befehlsprobe probe = probe();

        probe.tippe("wolke nehmen", reich);
        probe.tippe("wolke nehmen", reich);
        probe.tippe("wolke nehmen", reich);

        assertEquals(3, lager.anzahlVon(WELT, reich.kennung()),
                "drei Rechte, drei Inseln: " + reich.chatBild());
        reich.vergessen();
        probe.tippe("wolke nehmen", reich);
        assertTrue(reich.chatBild().get(0).contains("schon 3 Wolkeninseln"),
                reich.chatBild().get(0));
    }

    @Test
    @DisplayName("Der Zuschlag legt auf das Rangrecht auf")
    void zuschlag() throws CommandSyntaxException {
        Spielerprobe mitExtra = spieler("Extra", 4, WolkeBefehle.RECHT,
                WolkeBefehle.RECHT_NEHMEN, Aufwaermzeit.RECHT_SOFORT,
                "freimarken.wolken.1", "freimarken.extra.wolken.1");
        Befehlsprobe probe = probe();

        probe.tippe("wolke nehmen", mitExtra);
        probe.tippe("wolke nehmen", mitExtra);

        assertEquals(2, lager.anzahlVon(WELT, mitExtra.kennung()),
                "eine aus dem Rang, eine aus der Mitgliedschaft: "
                        + mitExtra.chatBild());
    }

    @Test
    @DisplayName("Ohne das Recht zum Nehmen ist der Zweig unerreichbar")
    void ohneRecht() throws CommandSyntaxException {
        Spielerprobe nurLesen = spieler("Gast", 5, WolkeBefehle.RECHT);
        Befehlsprobe probe = probe();

        // Brigadier WIRFT, und das ist der Fund dieser Probe: Der Zweig ist fuer ihn
        // nicht vorhanden, also ist "nehmen" ein unbekanntes Argument. Der Spieler liest
        // die englische Fehlermeldung des Servers - das ist richtig, denn er soll den
        // Zweig gar nicht kennen. Ohne Recht ist er VERBORGEN und verwehrt.
        org.junit.jupiter.api.Assertions.assertThrows(CommandSyntaxException.class,
                () -> probe.tippe("wolke nehmen", nurLesen));

        assertEquals(0, lager.anzahlVon(WELT, nurLesen.kennung()));
        assertTrue(nurLesen.chatBild().isEmpty(),
                "und von uns liest er nichts: " + nurLesen.chatBild());
    }

    @Test
    @DisplayName("Mit einem PREIS wird abgebucht und die Buchung steht im Buch")
    void mitPreis() throws CommandSyntaxException {
        Wolkenbuch teuer = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWolken(
                        Wolkenwerte.VORGABE.mitPreis(5_000L))));

        probe(teuer, WELT).tippe("wolke nehmen", anna);

        assertEquals(995_000L, kasse.guthaben(anna.kennung()));
        assertEquals(1, kasse.verlauf(anna.kennung(), 10).stream()
                .filter(b -> b.grund() == Buchungsgrund.WOLKE_GENOMMEN).count(),
                "die Buchung ist die Spur, wann die Insel genommen wurde");
        assertTrue(gelesen().get(0).contains("5.000 Mark abgebucht"), gelesen().get(0));
    }

    // --------------------------------------------------------------------- Heim

    @Test
    @DisplayName("/wolke heim ohne Insel sagt es - mit dem Weg zu einer")
    void heimOhneInsel() throws CommandSyntaxException {
        probe().tippe("wolke heim", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("keine Wolkeninsel"), gelesen().get(0));
    }

    @Test
    @DisplayName("/wolke heim bringt den Spieler auf seine Insel")
    void heim() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        Parzelle insel = buch.inselnVon(WELT, anna.kennung()).get(0);
        anna.inWelt(Serverprobe.welt("freimarken:marktstadt"));
        anna.anOrt(new Location(Serverprobe.welt("freimarken:marktstadt"), 0, 64, 0));

        gereist = null;

        probe.tippe("wolke heim", anna);

        assertTrue(gereist != null, "es muss eine Reise gegeben haben");
        assertEquals(WELT, gereist.getWorld().key().asString());
        assertEquals(buch.plan().feldMitte(insel.x()) + 0.5, gereist.getX(),
                "mitten auf die Wiese");
        assertEquals(buch.plan().feldMitte(insel.z()) + 0.5, gereist.getZ());
        assertEquals(Wolkenboden.BODEN + 1, gereist.getY(), "einen Block darueber");
    }

    // --------------------------------------------------------------------- Mitbauer

    @Test
    @DisplayName("/wolke mitbauer ALLEIN gibt die Liste - nicht Brigadiers englischen Satz")
    void mitbauerAlleinIstLesbar() throws CommandSyntaxException {
        // Ein passendes Literal verdeckt in Brigadier jedes Geschwister vollstaendig.
        // Ohne eigenes .executes endete dieser Befehl in "Unknown or incomplete command"
        // - und nur ein getippter Befehl faengt das.
        probe().tippe("wolke mitbauer", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("keine Wolkeninsel"), gelesen().get(0));
    }

    @Test
    @DisplayName("Mitbauer dazu und weg - auf der Insel, auf der man steht")
    void mitbauerDazuUndWeg() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        stelleAufDieEigeneInsel(anna);
        anna.vergessen();

        probe.tippe("wolke mitbauer dazu Bert", anna);
        assertTrue(gelesen().get(0).contains("Bert darf jetzt"), gelesen().get(0));

        anna.vergessen();
        probe.tippe("wolke mitbauer weg Bert", anna);
        assertTrue(gelesen().get(0).contains("Bert darf nicht mehr"), gelesen().get(0));
    }

    @Test
    @DisplayName("Einen Namen, den der Server nicht kennt, sagt der Befehl")
    void unbekannterName() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        stelleAufDieEigeneInsel(anna);
        anna.vergessen();

        probe.tippe("wolke mitbauer dazu Niemand", anna);

        assertTrue(gelesen().get(0).contains("Niemand kennt dieser Server nicht"),
                gelesen().get(0));
    }

    @Test
    @DisplayName("Sich selbst eintragen sagt der Befehl ab")
    void sichSelbst() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        stelleAufDieEigeneInsel(anna);
        anna.vergessen();

        probe.tippe("wolke mitbauer dazu Anna", anna);

        assertTrue(gelesen().get(0).contains("Dich selbst"), gelesen().get(0));
    }

    @Test
    @DisplayName("Auf einer fremden Insel sagt der Befehl, wem sie gehoert")
    void fremdeInsel() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("wolke nehmen", anna);
        Parzelle insel = buch.inselnVon(WELT, anna.kennung()).get(0);
        bert.anOrt(new Location(Serverprobe.welt(WELT, wolkenboden()),
                buch.plan().feldMitte(insel.x()), Wolkenboden.BODEN,
                buch.plan().feldMitte(insel.z())));

        probe.tippe("wolke mitbauer dazu Anna", bert);

        // Der Name steht in seiner eigenen Farbe, also liegt ein Farbcode zwischen
        // "gehoert" und "Anna" - geprueft werden darum beide Stuecke.
        assertTrue(bert.chatBild().get(0).contains("Anna"), bert.chatBild().get(0));
        assertTrue(bert.chatBild().get(0).contains("Mitbauer setzt nur der Eigentuemer"),
                bert.chatBild().get(0));
    }

    // --------------------------------------------------------------------- Ohne Welt

    @Test
    @DisplayName("Ohne Wolkenwelt sagt JEDER Zweig es - und keiner wirft")
    void ohneWelt() throws CommandSyntaxException {
        Befehlsprobe ohne = probe(null, "");

        ohne.tippe("wolke", anna);
        ohne.tippe("wolke nehmen", anna);
        ohne.tippe("wolke heim", anna);
        ohne.tippe("wolke mitbauer dazu Bert", anna);

        assertEquals(4, gelesen().size(), gelesen().toString());
        for (String zeile : gelesen()) {
            assertTrue(zeile.contains("keine Wolkenwelt"), zeile);
        }
    }

    @Test
    @DisplayName("Auf der Konsole antwortet /wolke gar nicht")
    void konsoleBekommtNichts() throws CommandSyntaxException {
        Befehlsprobe probe = probe();

        probe.tippeAufDerKonsole("wolke");

        assertEquals(List.of(), probe.konsoleBild(),
                "genommen wird fuer jemanden, der irgendwohin gebracht werden kann");
    }

    // --------------------------------------------------------------------- Werkzeug

    private Spielerprobe spieler(String name, int nummer, String... rechte) {
        return new Spielerprobe(name, Spielerprobe.kennungAus(nummer), rechte)
                .inWelt(Serverprobe.welt(WELT, wolkenboden()))
                .anOrt(new Location(Serverprobe.welt(WELT, wolkenboden()), 0, Wolkenboden.BODEN, 0));
    }

    /** Stellt den Spieler mitten auf seine erste Insel. */
    private void stelleAufDieEigeneInsel(Spielerprobe wer) {
        Parzelle insel = buch.inselnVon(WELT, wer.kennung()).get(0);
        wer.anOrt(new Location(Serverprobe.welt(WELT, wolkenboden()),
                buch.plan().feldMitte(insel.x()), Wolkenboden.BODEN,
                buch.plan().feldMitte(insel.z())));
    }

    private Befehlsprobe probe() {
        return probe(buch, WELT);
    }

    private Befehlsprobe probe(Wolkenbuch welches, String weltschluessel) {
        return new Befehlsprobe(List.of(new WolkeBefehle(welches, kasse,
                Sprachprobe.spracheAusDemJar(), this::setzeAuf, this::merkeReise,
                weltschluessel,
                () -> Wirtschaftswerte.VORGABE.wolken().hoechstzahl()).wolke()));
    }

    /** Die zweite Naht: Sie merkt sich, WOHIN gereist werden sollte. */
    private void merkeReise(org.bukkit.entity.Player wer, Location ziel) {
        gereist = ziel;
    }

    /** Die Naht: Sie merkt sich, WAS gesetzt werden sollte - und darf scheitern. */
    private boolean setzeAuf(Parzelle insel) {
        if (!aufsetzenGelingt) {
            return false;
        }
        aufgesetzt.add(insel.x() + "/" + insel.z());
        return true;
    }

    private List<String> gelesen() {
        return anna.chatBild();
    }

    /**
     * Der Boden einer Wolkenwelt - Gras auf {@link Wolkenboden#BODEN}, darueber Luft.
     *
     * <p>Ein Fund dieser Probe: {@code Reise.pruefe} fragt ueber
     * {@code Ankunftspunkt.taugt} nach festem Boden und Luft darueber. Ohne Griff wirft
     * die Probenwelt - "diese Welt hat keine Bloecke" -, und zwar in JEDEM Zweig, der
     * reist. Im Spiel ist der Boden da, weil das Aufsetzen ihn gesetzt hat.
     *
     * <p>UEBERALL derselbe Boden und nicht nur auf den Inseln: Was die Geometrie
     * entscheidet, prueft {@code WolkenplanTest} mit eigenen Mitteln - diese Attrappe
     * soll den Weg nicht an einer Stelle verschliessen, die mit dem Befehl nichts zu tun
     * hat.
     */
    private static Weltgriff wolkenboden() {
        return new Weltgriff() {
            @Override
            public int unten() {
                return -64;
            }

            @Override
            public int oben(int x, int z) {
                return Wolkenboden.BODEN;
            }

            @Override
            public Material bei(int x, int y, int z) {
                if (y > Wolkenboden.BODEN) {
                    return Material.AIR;
                }
                return y == Wolkenboden.BODEN ? Material.GRASS_BLOCK : Material.DIRT;
            }

            @Override
            public void setze(int x, int y, int z, Material block) {
                throw new UnsupportedOperationException("Diese Probe setzt nicht - das"
                        + " tut WolkenaufbauTest");
            }
        };
    }
}
