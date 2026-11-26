package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Zutritt je Welt - die Regel, ihre Wirkung und die Klage am Start.
 *
 * <p><b>Warum diese Probe die WIRKUNG prueft und nicht nur die Regel</b>
 * [25.11.2026]: Eine gebaute, gruen geprueefte Schutzregel ohne Verdrahtung ist
 * unsichtbar wirkungslos. Darum laeuft hier {@link Reise#fuehre} wirklich durch, mit der
 * ECHTEN Regel dahinter - und in beide Richtungen: Wer das Recht nicht hat, liest die
 * Absage; wer es hat, kommt an der Schranke vorbei und liest das NAECHSTE Hindernis.
 *
 * <p><b>Dass der Riegel VOR {@link Reise#pruefe} sitzt, ist hier mitgeprueft</b>, und
 * zwar ohne eine einzige Zeile ueber Reihenfolge: Beide Richtungen reisen an denselben
 * unerreichbaren Ort (jenseits der Welthoehe). Sass der Riegel dahinter, laese AUCH der
 * Unberechtigte "ausserhalb der Welthoehe" - und die erste Probe waere rot.
 *
 * <p>Der Ort jenseits der Welthoehe ist kein Kunstgriff, sondern die gemessene Grenze
 * dieses Bauplatzes: {@code Ankunftspunkt.taugt} fragt {@code Material.isSolid}, und das
 * geht in die Bukkit-Registry, die ohne laufenden Server wirft. An genau dieser Stelle
 * ist {@code WarpBefehlTest} 41k gescheitert. Die Hoehenwache davor kommt ohne Registry
 * aus.
 */
class ZutrittsregelTest {

    private static final String MITGLIEDERWELT = "minecraft:vogtei";
    private static final String RANGWELT = "minecraft:nether";

    /**
     * Eine Rangleiter, die AUS ist - es gibt keine Konstante dafuer.
     *
     * <p>Dieselbe Leiter wie {@link Rangwerte#VORGABE}, nur abgeschaltet: So steht
     * fest, dass der Riegel am Schalter haengt und nicht an einer leeren Liste.
     */
    private static final Rangwerte AUS =
            new Rangwerte(false, Rangwerte.VORGABE_LEITER);

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    // -------------------------------------------------- Die Regel, ohne alles

    /** Eine Welt, von der nur die zwei Zugangsfelder zaehlen. */
    private static Weltangabe angabe(String name, boolean premium, String abRang) {
        return new Weltangabe(name, World.Environment.NORMAL, Weltangabe.Art.GEWOEHNLICH,
                null, 0, null, GameMode.SURVIVAL, premium, abRang, false);
    }

    /** Eine Rechtelage als Menge - genau dafuer gibt es {@link Rechtefrage}. */
    private static Rechtefrage mit(String... rechte) {
        Set<String> menge = Set.of(rechte);
        return menge::contains;
    }

    @Test
    @DisplayName("1 - eine Welt ohne Schranke steht jedem offen")
    void ohneSchrankeOffen() {
        assertEquals(Optional.empty(),
                Zutrittsregel.pruefe(angabe("stollenberg", false, ""), mit()));
    }

    @Test
    @DisplayName("2 - premium ohne das Weltrecht sagt ab")
    void premiumOhneRechtSagtAb() {
        Optional<Zutrittsregel.Absage> absage =
                Zutrittsregel.pruefe(angabe("vogtei", true, ""), mit());

        assertTrue(absage.isPresent());
        assertEquals(Zutrittsregel.Grund.NUR_FUER_MITGLIEDER, absage.get().grund());
        assertEquals("", absage.get().rang(),
                "ohne Rangschranke gehoert kein Rangwort in die Absage");
    }

    @Test
    @DisplayName("3 - premium MIT dem Weltrecht laesst durch")
    void premiumMitRechtDurch() {
        assertEquals(Optional.empty(), Zutrittsregel.pruefe(angabe("vogtei", true, ""),
                mit("freimarken.welt.vogtei")));
    }

    @Test
    @DisplayName("4 - das Weltrecht gilt je Welt und nicht fuer alle")
    void weltrechtGiltJeWelt() {
        // Das Gegenbeispiel, ohne das Probe 3 auch mit einem festen Recht gruen waere.
        assertTrue(Zutrittsregel.pruefe(angabe("vogtei", true, ""),
                mit("freimarken.welt.stollenberg")).isPresent());
    }

    @Test
    @DisplayName("5 - ab-rang ohne das Stufenrecht sagt ab und NENNT die Stufe")
    void rangOhneRechtSagtAb() {
        Optional<Zutrittsregel.Absage> absage =
                Zutrittsregel.pruefe(angabe("nether", false, "siedler"), mit());

        assertTrue(absage.isPresent());
        assertEquals(Zutrittsregel.Grund.RANG_ZU_NIEDRIG, absage.get().grund());
        assertEquals("siedler", absage.get().rang(),
                "ohne die Stufe im Wort kann der Satz sie nicht nennen");
    }

    @Test
    @DisplayName("6 - ab-rang MIT dem Stufenrecht laesst durch")
    void rangMitRechtDurch() {
        assertEquals(Optional.empty(),
                Zutrittsregel.pruefe(angabe("nether", false, "siedler"),
                        mit("freimarken.stufe.siedler")));
    }

    @Test
    @DisplayName("7 - beides gesetzt verlangt BEIDES, und fragt die Mitgliedschaft zuerst")
    void beidesGiltUnd() {
        Weltangabe beides = angabe("vogtei", true, "siedler");

        // Nur das Stufenrecht: Die Mitgliedschaft fehlt, und sie wird zuerst gemeldet.
        Optional<Zutrittsregel.Absage> nurStufe =
                Zutrittsregel.pruefe(beides, mit("freimarken.stufe.siedler"));
        assertEquals(Zutrittsregel.Grund.NUR_FUER_MITGLIEDER, nurStufe.orElseThrow().grund());

        // Nur das Weltrecht: Jetzt fehlt der Rang.
        Optional<Zutrittsregel.Absage> nurWelt =
                Zutrittsregel.pruefe(beides, mit("freimarken.welt.vogtei"));
        assertEquals(Zutrittsregel.Grund.RANG_ZU_NIEDRIG, nurWelt.orElseThrow().grund());

        // Beides: offen.
        assertEquals(Optional.empty(), Zutrittsregel.pruefe(beides,
                mit("freimarken.welt.vogtei", "freimarken.stufe.siedler")));
    }

    @Test
    @DisplayName("8 - eine fremde Welt hat keine Schranke")
    void fremdeWeltOhneSchranke() {
        assertEquals(Optional.empty(), Zutrittsregel.pruefe(null, mit()));
    }

    // -------------------------------------------------- Die Rechtenamen

    @Test
    @DisplayName("9 - die zwei Vorsilben stehen als EIN Literal und tragen den Punkt")
    void vorsilbenTragenDenPunkt() {
        assertEquals("freimarken.welt.", Zutrittsregel.VORSILBE_WELT);
        assertEquals("freimarken.stufe.", Zutrittsregel.VORSILBE_STUFE);
        assertEquals("freimarken.welt.vogtei", Zutrittsregel.weltrecht("vogtei"));
        assertEquals("freimarken.stufe.siedler", Zutrittsregel.stufenrecht("siedler"));

        // NICHT freimarken.rang.: Das ist schon das Recht von /rang (plugin.yml), und
        // eine Vorsilbe darueber liesse GrenzeTest 31cy jedes Stufenrecht ungeprueft
        // durch - sie beginnt dann mit einem angemeldeten Recht plus Punkt.
        assertEquals(RangBefehle.RECHT, "freimarken.rang");
        assertFalse(Zutrittsregel.VORSILBE_STUFE.startsWith(RangBefehle.RECHT + "."),
                "die Stufenvorsilbe darf nicht unter dem Recht von /rang liegen");
    }

    // -------------------------------------------------- Die Stufenrechte

    @Test
    @DisplayName("10 - das Rechtebuendel gibt die erreichte Stufe UND jede darunter")
    void buendelGibtDieStrecke() {
        Set<String> alsSiedler = Rechtebuendel.fuer(Rangwerte.VORGABE,
                new Rangstand(Spielerprobe.kennungAus(70), "siedler", Instant.EPOCH), null, null);

        assertTrue(alsSiedler.contains("freimarken.stufe.reisender"),
                "wer Siedler ist, war Reisender: " + alsSiedler);
        assertTrue(alsSiedler.contains("freimarken.stufe.siedler"), alsSiedler.toString());
        assertFalse(alsSiedler.contains("freimarken.stufe.buerger"),
                "nach OBEN darf die Strecke nicht reichen: " + alsSiedler);
    }

    @Test
    @DisplayName("10b - ohne Rangleiter gibt es kein Stufenrecht")
    void ohneLeiterKeinStufenrecht() {
        // Der Riegel, ohne den eine Welt mit ab-rang: auf einem Server OHNE Raenge fuer
        // jeden gesperrt waere, obwohl der Betreiber die Raenge ausgeschaltet hat.
        assertEquals(Set.of(), Rechtebuendel.fuer(AUS,
                new Rangstand(Spielerprobe.kennungAus(70), "siedler", Instant.EPOCH), null, null));
    }

    // -------------------------------------------------- Die Klage am Start

    @Test
    @DisplayName("11 - eine Stufe, die es nicht gibt, wird beim Start beklagt")
    void erfundeneStufeWirdBeklagt() {
        List<String> klagen = Zutrittsregel.unbekannteStufen(
                List.of(angabe("nether", false, "schmied")), Rangwerte.VORGABE);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("welten.nether.ab-rang"), klagen.get(0));
        assertTrue(klagen.get(0).contains("schmied"), klagen.get(0));
        assertTrue(klagen.get(0).contains("reisender, siedler, buerger"),
                "die Klage muss sagen, welche Stufen es GIBT: " + klagen.get(0));
        assertTrue(klagen.get(0).contains("freimarken.stufe.schmied"),
                "und welches Recht niemand bekommt: " + klagen.get(0));
    }

    @Test
    @DisplayName("11b - eine Stufe, die es gibt, wird NICHT beklagt")
    void bekannteStufeOhneKlage() {
        assertEquals(List.of(), Zutrittsregel.unbekannteStufen(
                List.of(angabe("nether", false, "siedler"),
                        angabe("end", false, "buerger"),
                        angabe("vogtei", true, "")),
                Rangwerte.VORGABE));
    }

    @Test
    @DisplayName("11c - ohne Rangleiter wird jede Schranke beklagt")
    void ohneLeiterJedeSchranke() {
        List<String> klagen = Zutrittsregel.unbekannteStufen(
                List.of(angabe("nether", false, "siedler")), AUS);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("raenge.an"), klagen.get(0));
    }

    // -------------------------------------------------- Der Startbericht

    @Test
    @DisplayName("11d - der Startbericht nennt JEDE Schranke und ihr Recht")
    void berichtNenntJedeSchranke() {
        List<String> bericht = Zutrittsregel.bericht(List.of(
                angabe("marktstadt", false, ""),
                angabe("nether", false, "siedler"),
                angabe("end", false, "buerger"),
                angabe("vogtei", true, ""),
                angabe("sonderwelt", true, "buerger")));

        assertEquals(1, bericht.size(), bericht.toString());
        // Jede der vier Schranken, und die offene Welt NICHT: Eine Zeile, die auch
        // die neun offenen Welten aufzaehlt, liest niemand zu Ende.
        assertTrue(bericht.get(0).contains("nether ab Stufe siedler"), bericht.get(0));
        assertTrue(bericht.get(0).contains("end ab Stufe buerger"), bericht.get(0));
        assertTrue(bericht.get(0).contains("vogtei nur mit freimarken.welt.vogtei"),
                bericht.get(0));
        assertTrue(bericht.get(0).contains("sonderwelt nur mit"
                        + " freimarken.welt.sonderwelt UND ab Stufe buerger"),
                "beides gesetzt heisst BEIDES - und der Bericht muss es sagen: "
                        + bericht.get(0));
        assertTrue(!bericht.get(0).contains("marktstadt"),
                "die offene Welt gehoert nicht in die Zeile: " + bericht.get(0));
    }

    @Test
    @DisplayName("11e - ohne Schranke gibt es KEINE Zeile")
    void ohneSchrankeKeineZeile() {
        // Eine Zeile "keine Schranken" bei jedem Start waere eine Zeile, die niemand
        // mehr liest - und dann faellt auch die eine nicht auf, die etwas sagt.
        assertEquals(List.of(), Zutrittsregel.bericht(List.of(
                angabe("marktstadt", false, ""), angabe("altfurt", false, ""))));
    }

    // -------------------------------------------------- Die WIRKUNG, durch Reise

    /**
     * Ein Ziel jenseits der Welthoehe - erreichbar fuer die Probe, nicht fuer den Spieler.
     *
     * <p>{@code getMaxHeight} beantwortet {@link Serverprobe}; {@code Material.isSolid}
     * nicht. Darum endet jede durchgelassene Reise dieser Probe an der Hoehenwache und
     * nicht am Boden.
     */
    private static Location zuHoch(World welt) {
        return new Location(welt, 0.5, 100_000, 0.5);
    }

    /**
     * Ein Weltgriff, der NUR die Hoehe kennt - und bei einem Block wirft.
     *
     * <p>{@code Serverprobe.welt(schluessel)} ohne Griff wirft schon bei
     * {@code getMinHeight}, und die Hoehenwache fragt genau das. Mehr braucht diese
     * Probe nicht: Wer durchgelassen wird, scheitert an der Hoehe und erreicht die
     * Bodenfrage nie. Der Wurf in {@code bei} ist darum kein Mangel, sondern die
     * Zusage - erreicht eine kuenftige Aenderung den Boden, sagt diese Probe es,
     * statt stumm etwas anderes zu pruefen.
     */
    private static final Weltgriff NUR_HOEHE = new Weltgriff() {
        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return 0;
        }

        @Override
        public org.bukkit.Material bei(int x, int y, int z) {
            throw new UnsupportedOperationException("Diese Probe reist an einen"
                    + " Ort jenseits der Welthoehe und liest nie einen Block. Wer"
                    + " hier ankommt, hat die Hoehenwache uebersprungen.");
        }

        @Override
        public void setze(int x, int y, int z, org.bukkit.Material block) {
            throw new UnsupportedOperationException("Diese Probe baut nichts.");
        }
    };

    private Reise reiseNach(World welt, Weltangabe angabe) {
        Map<String, Weltangabe> karte = Map.of(welt.key().asString(), angabe);
        return new Reise(Logger.getLogger("ZutrittsregelTest"), new Rueckweg(),
                new Aufwaermzeit(null, Sprachprobe.spracheAusDemJar()),
                Sprachprobe.spracheAusDemJar(),
                (wer, ziel) -> Zutrittsregel.pruefe(
                        ziel == null ? null : karte.get(ziel.key().asString()),
                        wer::hasPermission));
    }

    @Test
    @DisplayName("12 - WIRKT: ohne das Weltrecht kommt die Absage, nicht die Reise")
    void ohneWeltrechtKeineReise() {
        World vogtei = Serverprobe.welt(MITGLIEDERWELT, NUR_HOEHE);
        Reise reise = reiseNach(vogtei, angabe("vogtei", true, ""));
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(70),
                Aufwaermzeit.RECHT_SOFORT);

        reise.fuehre(spieler.spieler(), zuHoch(vogtei), "Vogtei");

        assertEquals(1, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("nur Mitgliedern offen"),
                spieler.chatBild().get(0));
        assertTrue(spieler.chatBild().get(0).contains("Vogtei"),
                "der Name, unter dem die Reise angetreten wurde: "
                        + spieler.chatBild().get(0));
    }

    @Test
    @DisplayName("12b - WIRKT: ohne das Stufenrecht kommt die Absage und NENNT die Stufe")
    void ohneStufenrechtKeineReise() {
        World nether = Serverprobe.welt(RANGWELT, NUR_HOEHE);
        Reise reise = reiseNach(nether, angabe("nether", false, "siedler"));
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(71),
                Aufwaermzeit.RECHT_SOFORT);

        reise.fuehre(spieler.spieler(), zuHoch(nether), "Nether");

        assertEquals(1, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("Rang siedler"),
                spieler.chatBild().get(0));
    }

    /**
     * Das Gegenbeispiel - und der Zeuge dafuer, dass der Riegel VORNE sitzt.
     *
     * <p>Derselbe unerreichbare Ort wie in 12 und 12b. Wer das Recht hat, liest hier das
     * NAECHSTE Hindernis; wer es nicht hat, liest in 12 die Absage und sieht die
     * Welthoehe nie. Ohne diese dritte Probe waeren 12 und 12b auch dann gruen, wenn die
     * Regel jeden abwiese.
     */
    @Test
    @DisplayName("12c - WIRKT: MIT dem Recht geht es durch bis zum naechsten Hindernis")
    void mitRechtDurchBisZumHindernis() {
        World vogtei = Serverprobe.welt(MITGLIEDERWELT, NUR_HOEHE);
        Reise reise = reiseNach(vogtei, angabe("vogtei", true, ""));
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(72),
                Aufwaermzeit.RECHT_SOFORT, "freimarken.welt.vogtei");

        reise.fuehre(spieler.spieler(), zuHoch(vogtei), "Vogtei");

        assertEquals(1, spieler.chatBild().size(), spieler.chatBild().toString());
        assertTrue(spieler.chatBild().get(0).contains("Welthoehe"),
                "durchgelassen heisst: die naechste Wache antwortet - "
                        + spieler.chatBild().get(0));
    }
}
