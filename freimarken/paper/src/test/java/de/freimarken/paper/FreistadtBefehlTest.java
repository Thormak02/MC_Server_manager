package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Absteckbuch;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Grundstuecksbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtfeldplan;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /freistadt} GETIPPT - vom Wort bis zur Buchung.
 *
 * <p>Dieselbe Bauart wie {@link LadenBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest und was im Buch steht.
 *
 * <p><b>Die tragende Probe ist {@link #ohneBestaetigenWirdNichtGegruendet()}:</b> Die
 * Rueckfrage steht allein im Befehlsbaum und in keiner Naht - nur ein getippter Befehl
 * kann sehen, dass die erste Stufe wirklich nichts tut.
 */
@DisplayName("/freistadt getippt")
class FreistadtBefehlTest {

    private static final String WELT = "freimarken:freistaedte";

    private static final long PREIS = Freistadtwerte.VORGABE_GRUENDUNGSPREIS;

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-11T12:00:00Z"), java.time.ZoneOffset.UTC);

    private SpeicherKasse kasse;
    private SpeicherLager lager;
    private SpeicherGrundstuecke grundstuecke;
    private Absteckbuch absteckbuch;
    private Spielerprobe anna;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        // Die Anwesenden auch [Teil C3]: Der Kauf schickt dem VERKAEUFER etwas,
        // und dafuer fragt der Zweig den Server, wer online ist. Blieben sie
        // stehen, haenge eine Probe am Erfolg der Probe vor ihr.
        Serverprobe.niemandMehrDa();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        lager = new SpeicherLager();
        grundstuecke = new SpeicherGrundstuecke();
        absteckbuch = new Absteckbuch();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                FreistadtBefehle.RECHT, FreistadtBefehle.RECHT_GRUENDEN,
                FreistadtBefehle.RECHT_GRUNDSTUECK,
                FreistadtBefehle.RECHT_KAUFEN)
                .inWelt(Serverprobe.welt(WELT));
    }

    private Befehlsprobe probe() {
        return probe(new Wirtschaftsstand(Wirtschaftswerte.VORGABE), WELT);
    }

    private Befehlsprobe probe(Wirtschaftsstand stand, String weltschluessel) {
        Freistadtbuch buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                Warpverzeichnis.ausWelten(List.of("kornmark", "freistaedte")), stand);
        // Das Grundstuecksbuch und die Absteckung kommen mit [Teil C2]. Beide hier
        // aus derselben Geometrie wie das Freistadtbuch - zwei waeren zwei Rechnungen
        // darueber, wo ein Feld liegt.
        var befehle = new FreistadtBefehle(buch, kasse, Sprachprobe.spracheAusDemJar(),
                weltschluessel, () -> stand.werte().freistaedte().hoechstzahl(),
                new Grundstuecksbuch(Feldgrenze.freistaedte(), lager, grundstuecke,
                        kasse, stand),
                absteckbuch, Logger.getLogger("FreistadtBefehlTest"));
        return new Befehlsprobe(List.of(befehle.freistadt()));
    }

    /** Was Anna gelesen hat, als Bild. */
    private List<String> gelesen() {
        return anna.chatBild();
    }

    private long gebucht(Buchungsgrund grund) {
        return gebuchtFuer(anna, grund);
    }

    /**
     * Dieselbe Summe fuer einen ANDEREN Spieler [Teil C3].
     *
     * <p>Gebraucht, seit zwei Spieler miteinander handeln: Derselbe Vorgang steht bei
     * beiden im Buch, und bei jedem mit einem anderen Grund und einem anderen
     * Vorzeichen.
     */
    private long gebuchtFuer(Spielerprobe wer, Buchungsgrund grund) {
        return kasse.summeSeit(wer.kennung(), Instant.EPOCH, grund);
    }

    // --------------------------------------------------------------------- Liste

    @Test
    @DisplayName("/freistadt zeigt ab Werk, dass es noch keine Stadt gibt")
    void leereListe() throws CommandSyntaxException {
        probe().tippe("freistadt", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Freistaedte"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("noch keine Freistadt"), gelesen().get(1));
    }

    @Test
    @DisplayName("/freistadt und /freistadt liste tun DASSELBE")
    void nackterBefehlUndListeSindGleich() throws CommandSyntaxException {
        probe().tippe("freistadt", anna);
        List<String> ohne = List.copyOf(gelesen());

        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2),
                FreistadtBefehle.RECHT).inWelt(Serverprobe.welt(WELT));
        probe().tippe("freistadt liste", bert);

        assertEquals(ohne, bert.chatBild());
    }

    @Test
    @DisplayName("Eine gegruendete Stadt steht in der Liste, mit Besitzer und Feld")
    void gegruendeteStadtStehtInDerListe() throws CommandSyntaxException {
        probe().tippe("freistadt gruenden neustadt bestaetigen", anna);
        anna.vergessen();

        probe().tippe("freistadt", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("neustadt"), gelesen().get(1));
        // Die FELDNUMMER, nicht der Name: Ein Stellvertreter ist kein OfflinePlayer des
        // Servers, also nennt Spielername hier die ersten acht Zeichen der Kennung.
        assertTrue(gelesen().get(1).contains("Feld "), gelesen().get(1));
    }

    // ------------------------------------------------------------------ Gruenden

    @Test
    @DisplayName("Ohne bestaetigen wird NICHT gegruendet - nur gefragt")
    void ohneBestaetigenWirdNichtGegruendet() throws CommandSyntaxException {
        probe().tippe("freistadt gruenden neustadt", anna);

        // ZWEI Zeilen: die Zahl und der Weg zum Bestaetigen. Eine Rueckfrage ohne den
        // Befehl, der sie beantwortet, waere eine Sackgasse.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("400.000"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("bestaetigen"), gelesen().get(1));
        assertEquals(1_000_000L, kasse.guthaben(anna.kennung()),
                "die Rueckfrage kostet nichts");
        assertTrue(lager.staedteIn(WELT).isEmpty(), "und gegruendet ist nichts");
    }

    @Test
    @DisplayName("Mit bestaetigen entsteht die Stadt, und das Geld ist gebucht")
    void mitBestaetigenEntstehtDieStadt() throws CommandSyntaxException {
        probe().tippe("freistadt gruenden neustadt bestaetigen", anna);

        // ZWEI Zeilen: die Meldung und der Warp. Ohne die zweite stuende der
        // frischgebackene Besitzer vor seiner Stadt und wuesste nicht, wie er hineinkommt.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("neustadt"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("/warp freistadt neustadt"), gelesen().get(1));

        assertEquals(1, lager.staedteIn(WELT).size());
        assertEquals(1_000_000L - PREIS, kasse.guthaben(anna.kennung()));
        // Negativ im Buch, weil es eine AUSGABE ist - darum auf die Summe gefragt und
        // nicht auf ein Vorzeichen.
        assertEquals(-PREIS, gebucht(Buchungsgrund.FREISTADT_GEGRUENDET));
        assertEquals(0L, gebucht(Buchungsgrund.FREISTADT_ERSTATTET),
                "erstattet wurde nichts - es ist ja nichts schiefgegangen");
    }

    @Test
    @DisplayName("Ein vertippter Name kostet nichts - die Absage kommt erst beim Bestaetigen")
    void vertippterNameKostetNichts() throws CommandSyntaxException {
        probe().tippe("freistadt gruenden neu-stadt-mit-einem-sehr-viel-zu-langen-namen"
                + " bestaetigen", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("3 bis 32"), gelesen().get(0));
        assertEquals(1_000_000L, kasse.guthaben(anna.kennung()));
    }

    @Test
    @DisplayName("Die Absage nennt die Hoechstzahl, die WIRKLICH gilt")
    void absageNenntDieGeltendeZahl() throws CommandSyntaxException {
        // Beim GEBRAUCH gefragt und nicht kopiert: Ein Neuladen muss die Zahl erreichen.
        // Darum traegt die Absage die Zahl aus dem Stand und nicht die der Vorgabe.
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitFreistaedten(Freistadtwerte.VORGABE.mitHoechstzahl(2)));
        probe(stand, WELT).tippe("freistadt gruenden eins bestaetigen", anna);
        probe(stand, WELT).tippe("freistadt gruenden zwei bestaetigen", anna);
        anna.vergessen();

        probe(stand, WELT).tippe("freistadt gruenden drei bestaetigen", anna);

        assertTrue(gelesen().get(0).contains("(2)"), gelesen().get(0));
        assertEquals(2, lager.staedteIn(WELT).size());
    }

    @Test
    @DisplayName("Ohne Recht zum Gruenden gibt es den Zweig nicht")
    void ohneRechtKeinZweig() {
        Spielerprobe gast = new Spielerprobe("Gast", Spielerprobe.kennungAus(9),
                FreistadtBefehle.RECHT).inWelt(Serverprobe.welt(WELT));

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt gruenden neustadt bestaetigen", gast));
        assertTrue(lager.staedteIn(WELT).isEmpty());
    }

    @Test
    @DisplayName("Die Konsole gruendet nicht - sie hat kein Konto")
    void konsoleGruendetNicht() throws CommandSyntaxException {
        probe().tippeAufDerKonsole("freistadt gruenden neustadt bestaetigen");

        assertTrue(lager.staedteIn(WELT).isEmpty());
        assertTrue(gelesen().isEmpty());
    }

    @Test
    @DisplayName("Ohne Freistaedtewelt sagt JEDER Zweig es - statt nichts zu tun")
    void ohneWeltSagtJederZweigEs() throws CommandSyntaxException {
        Befehlsprobe ohneWelt = probe(new Wirtschaftsstand(Wirtschaftswerte.VORGABE), "");

        ohneWelt.tippe("freistadt", anna);
        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Welt der Freistaedte fehlt"),
                gelesen().get(0));

        anna.vergessen();
        ohneWelt.tippe("freistadt gruenden neustadt", anna);
        assertTrue(gelesen().get(0).contains("Welt der Freistaedte fehlt"),
                gelesen().get(0));

        anna.vergessen();
        ohneWelt.tippe("freistadt gruenden neustadt bestaetigen", anna);
        assertTrue(gelesen().get(0).contains("Welt der Freistaedte fehlt"),
                gelesen().get(0));
        assertTrue(lager.staedteIn(WELT).isEmpty());
        assertEquals(1_000_000L, kasse.guthaben(anna.kennung()));
    }

    // ------------------------------------------- Die Grundstuecke [Teil C2]

    /**
     * Anna in ihre eigene Stadt setzen und das Feld merken.
     *
     * <p>Gegruendet wird ueber den BEFEHL, nicht am Lager vorbei: Nur so laeuft
     * dieselbe Kette wie im Spiel, und ein Grundstueck haengt wirklich an der Stadt,
     * die der Befehl angelegt hat.
     */
    private Parzelle annastadtMitAnnaDarin() throws CommandSyntaxException {
        probe().tippe("freistadt gruenden annastadt bestaetigen", anna);
        anna.vergessen();
        Parzelle stadt = lager.staedteIn(WELT).get(0);
        stellDich(anna, stadt, 0, 0);
        return stadt;
    }

    /** Den Spieler auf die Zelle (zx|zz) INNERHALB dieses Feldes stellen. */
    private void stellDich(Spielerprobe wer, Parzelle stadt, int zelleImFeldX,
                           int zelleImFeldZ) {
        Stadtfeldplan plan = Stadtfeldplan.freistaedte();
        wer.anOrt(new org.bukkit.Location(Serverprobe.welt(WELT),
                plan.zellenAnfang(plan.ersteZelle(stadt.x()) + zelleImFeldX) + 3, 64,
                plan.zellenAnfang(plan.ersteZelle(stadt.z()) + zelleImFeldZ) + 3));
    }

    @Test
    @DisplayName("Zwei Ecken, anlegen - und das Grundstueck steht")
    void zweiEckenUndAnlegen() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();

        probe().tippe("freistadt grundstueck ecke", anna);
        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Ecke 1"), gelesen().get(0));
        anna.vergessen();

        stellDich(anna, stadt, 2, 1);
        probe().tippe("freistadt grundstueck ecke", anna);
        // ZWEI Zeilen: die Ecke und die Vorschau. Die Groesse SCHON JETZT - wer sieht,
        // dass sein Zuschnitt zu gross ist, legt ihn nicht erst an.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("6 Zellen"), gelesen().get(1));
        anna.vergessen();

        probe().tippe("freistadt grundstueck anlegen", anna);
        assertTrue(gelesen().get(0).contains("zugeschnitten"), gelesen().get(0));
        assertEquals(1, grundstuecke.inStadt(stadt.id()).size());
        assertEquals(6, grundstuecke.inStadt(stadt.id()).get(0).flaeche().zellen());
    }

    @Test
    @DisplayName("Anlegen ohne Ecken sagt, wie es geht")
    void anlegenOhneEcken() throws CommandSyntaxException {
        annastadtMitAnnaDarin();

        probe().tippe("freistadt grundstueck anlegen", anna);

        // ZWEI Zeilen: die Absage und der Weg. Eine Absage ohne Ausweg ist eine
        // Sackgasse.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("nichts abgesteckt"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("grundstueck ecke"), gelesen().get(1));
    }

    @Test
    @DisplayName("Anlegen mit EINER Ecke sagt, dass die zweite fehlt")
    void anlegenMitEinerEcke() throws CommandSyntaxException {
        annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        anna.vergessen();

        probe().tippe("freistadt grundstueck anlegen", anna);

        assertTrue(gelesen().get(0).contains("zweite Ecke"), gelesen().get(0));
    }

    @Test
    @DisplayName("Nach dem Anlegen sind die Ecken WEG")
    void eckenSindDanachWeg() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        stellDich(anna, stadt, 1, 1);
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck anlegen", anna);
        anna.vergessen();

        // Blieben die Ecken stehen, versuchte dieses anlegen DASSELBE Grundstueck noch
        // einmal - und die Absage "hier liegt schon ein Grundstueck" waere die eigene.
        probe().tippe("freistadt grundstueck anlegen", anna);

        assertTrue(gelesen().get(0).contains("nichts abgesteckt"), gelesen().get(0));
        assertEquals(1, grundstuecke.inStadt(stadt.id()).size());
    }

    @Test
    @DisplayName("Die DRITTE Ecke beginnt neu - und sagt es")
    void dritteEckeBeginntNeu() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        stellDich(anna, stadt, 1, 1);
        probe().tippe("freistadt grundstueck ecke", anna);
        anna.vergessen();

        stellDich(anna, stadt, 5, 5);
        probe().tippe("freistadt grundstueck ecke", anna);

        assertTrue(gelesen().get(0).contains("Neue Absteckung"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("vorige ist weg"), gelesen().get(0));
        // Und jetzt fehlt wieder die zweite.
        anna.vergessen();
        probe().tippe("freistadt grundstueck anlegen", anna);
        assertTrue(gelesen().get(0).contains("zweite Ecke"), gelesen().get(0));
    }

    @Test
    @DisplayName("In einer FREMDEN Stadt wird nicht zugeschnitten")
    void nichtInFremderStadt() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2),
                FreistadtBefehle.RECHT, FreistadtBefehle.RECHT_GRUNDSTUECK)
                .inWelt(Serverprobe.welt(WELT));
        stellDich(bert, stadt, 0, 0);
        probe().tippe("freistadt grundstueck ecke", bert);
        stellDich(bert, stadt, 1, 1);
        probe().tippe("freistadt grundstueck ecke", bert);
        bert.vergessen();

        probe().tippe("freistadt grundstueck anlegen", bert);

        assertTrue(bert.chatBild().get(0).contains("nicht deine Stadt"),
                bert.chatBild().get(0));
        assertTrue(grundstuecke.inStadt(stadt.id()).isEmpty());
    }

    @Test
    @DisplayName("Aufloesen geht nur dem Besitzer - und gibt die Flaeche zurueck")
    void aufloesenNurDemBesitzer() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck anlegen", anna);
        anna.vergessen();

        probe().tippe("freistadt grundstueck aufloesen", anna);

        assertTrue(gelesen().get(0).contains("aufgeloest"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("wieder der Stadt"), gelesen().get(0));
        assertTrue(grundstuecke.inStadt(stadt.id()).isEmpty());
    }

    @Test
    @DisplayName("Wo nichts liegt, ist nichts aufzuloesen")
    void nichtsAufzuloesen() throws CommandSyntaxException {
        annastadtMitAnnaDarin();

        probe().tippe("freistadt grundstueck aufloesen", anna);

        assertTrue(gelesen().get(0).contains("kein Grundstueck"), gelesen().get(0));
    }

    @Test
    @DisplayName("Die Liste nennt die Grundstuecke der Stadt, in der man STEHT")
    void listeNenntDieStadtUnterDenFuessen() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck anlegen", anna);
        anna.vergessen();

        probe().tippe("freistadt grundstueck", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("annastadt"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("1 Zelle"), gelesen().get(1));
        assertEquals(1, grundstuecke.inStadt(stadt.id()).size());
    }

    @Test
    @DisplayName("Die Liste auf einem FREIEN Feld sagt, dass dort keine Stadt steht")
    void listeOhneStadt() throws CommandSyntaxException {
        anna.inWelt(Serverprobe.welt(WELT));
        Stadtfeldplan plan = Stadtfeldplan.freistaedte();
        anna.anOrt(new org.bukkit.Location(Serverprobe.welt(WELT),
                plan.feldMitte(5), 64, plan.feldMitte(5)));

        probe().tippe("freistadt grundstueck", anna);

        assertTrue(gelesen().get(0).contains("keine Stadt"), gelesen().get(0));
    }

    @Test
    @DisplayName("Ohne das Recht gibt es den ganzen Grundstueckszweig nicht")
    void ohneGrundstuecksrechtKeinZweig() throws CommandSyntaxException {
        annastadtMitAnnaDarin();
        Spielerprobe ohne = new Spielerprobe("Ohne", Spielerprobe.kennungAus(8),
                FreistadtBefehle.RECHT).inWelt(Serverprobe.welt(WELT));

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt grundstueck", ohne));
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt grundstueck ecke", ohne));
    }

    @Test
    @DisplayName("Die Konsole steckt nichts ab - sie steht nirgends")
    void konsoleStecktNichtsAb() throws CommandSyntaxException {
        annastadtMitAnnaDarin();

        probe().tippeAufDerKonsole("freistadt grundstueck ecke");
        probe().tippeAufDerKonsole("freistadt grundstueck anlegen");

        assertEquals(0, absteckbuch.absteckende(),
                "eine Absteckung ohne Ort waere eine Marke im Nichts");
    }

    // ------------------------------------- Angebot und Verkauf [Teil C3]

    /** Der Preis, zu dem in diesen Proben gehandelt wird. */
    private static final long ANGEBOTSPREIS = 1_500L;

    /**
     * Annastadt, ein Grundstueck darin, und Anna steht darauf.
     *
     * <p>Angelegt ueber die BEFEHLE und nicht an der Ablage vorbei - wie
     * {@link #annastadtMitAnnaDarin}: Nur so haengt das Grundstueck wirklich an der
     * Stadt, die der Befehl angelegt hat, und nur so ist der Zuschnitt derselbe, den
     * ein Spieler bekaeme.
     *
     * @return die Stadt, damit die Probe Mitspieler hineinstellen kann
     */
    private Parzelle annastadtMitEinemGrundstueck() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitAnnaDarin();
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck ecke", anna);
        probe().tippe("freistadt grundstueck anlegen", anna);
        anna.vergessen();
        return stadt;
    }

    /**
     * Bert - angemeldet, mit Namen und mit dem Recht zu kaufen.
     *
     * <p><b>Angemeldet und nicht nur gebaut:</b> Der Kauf schickt dem Verkaeufer eine
     * Zeile, und der Zweig findet ihn nur ueber {@code Server.getPlayer}. Ohne die
     * Anmeldung blieben die Namen in beiden Saetzen acht Zeichen einer Kennung - und
     * die Probe prueefte den Platzhalter statt der Zusage.
     */
    private Spielerprobe bert() {
        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2),
                FreistadtBefehle.RECHT, FreistadtBefehle.RECHT_GRUNDSTUECK,
                FreistadtBefehle.RECHT_KAUFEN)
                .inWelt(Serverprobe.welt(WELT));
        Serverprobe.anmelden(bert, "Bert");
        Serverprobe.anmelden(anna, "Anna");
        return bert;
    }

    /** Derselbe Stand mit einer Verkaufsgebuehr - fuer die Gebuehrprobe. */
    private Wirtschaftsstand mitGebuehr(int prozent) {
        Freistadtwerte werte = Wirtschaftswerte.VORGABE.freistaedte();
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitFreistaedten(
                new Freistadtwerte(werte.gruendungspreis(), werte.hoechstzahl(),
                        werte.hoechstzellen(), prozent)));
    }

    /**
     * DAS ABNAHMEKRITERIUM aus Plan 15 - ein Spieler verkauft einem anderen Land.
     *
     * <p>Der erste Handel dieses Projekts, bei dem das Geld nicht beim Bund landet.
     * Darum prueft diese Probe ALLE FUENF Folgen und nicht nur den Satz im Chat:
     * beide Meldungen, den Besitzerwechsel, beide Kontostaende, beide Buchungsgruende
     * und das verschwundene Angebot. Jede einzelne davon kann allein falsch sein -
     * ein Kauf, der bucht, aber nicht uebertraegt, sieht im Chat genauso aus.
     */
    @Test
    @DisplayName("Anna bietet, Bert kauft - Geld und Grundstueck wechseln die Seite")
    void einSpielerVerkauftEinemAnderen() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        assertTrue(gelesen().get(0).contains("Zum Verkauf gestellt"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("1.500 Mark"), gelesen().get(0));
        // Der Weg zum Kauf steht dabei: Sonst muesste Anna ihren Kunden erklaeren, wie
        // es geht.
        assertTrue(gelesen().get(1).contains("grundstueck kaufen"), gelesen().get(1));
        anna.vergessen();

        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);
        long annaVorher = kasse.guthaben(anna.kennung());

        probe().tippe("freistadt grundstueck kaufen", bert);

        // 1. Was BERT liest - mit Annas Namen.
        assertTrue(bert.chatBild().get(0).contains("Gekauft!"),
                bert.chatBild().toString());
        assertTrue(bert.chatBild().get(0).contains("Anna"), bert.chatBild().get(0));
        assertTrue(bert.chatBild().get(0).contains("1.500 Mark"),
                bert.chatBild().get(0));
        // 2. Was ANNA liest - mit Berts Namen. Ohne diese Zeile erfuehre ein
        // Verkaeufer nur aus /mark verlauf, dass sein Land weg ist.
        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Verkauft!"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("Bert"), gelesen().get(0));
        // 3. Der BESITZERWECHSEL - das Zweite, was dieses Projekt noch nie hatte.
        assertEquals(bert.kennung(),
                grundstuecke.inStadt(stadt.id()).get(0).eigentuemer());
        // 4. Beide Kontostaende. Ohne Gebuehr bekommt Anna genau, was Bert zahlt.
        assertEquals(1_000_000L - ANGEBOTSPREIS, kasse.guthaben(bert.kennung()));
        assertEquals(annaVorher + ANGEBOTSPREIS, kasse.guthaben(anna.kennung()));
        // 5. Beide Buchungsgruende, und jeder mit seinem Vorzeichen: Eine Ausgabe
        // steht negativ im Buch. Mit demselben Grund auf beiden Seiten koennte
        // keiner von beiden seinen Stand nachrechnen.
        assertEquals(-ANGEBOTSPREIS,
                gebuchtFuer(bert, Buchungsgrund.GRUNDSTUECK_GEKAUFT));
        assertEquals(ANGEBOTSPREIS, gebucht(Buchungsgrund.GRUNDSTUECK_VERKAUFT));
        // 6. Und es liegt nicht mehr aus - ein zweiter Kaeufer findet nichts.
        assertTrue(grundstuecke.angebote(WELT).isEmpty());
    }

    /**
     * Die Gebuehr trifft den, der das Geld BEKOMMT.
     *
     * <p>Wie bei den Kistenshops und nie als Aufschlag auf den Preis: Was im Angebot
     * steht, ist was der Kaeufer zahlt. Bert zahlt darum dieselben 1.500, und Anna
     * bekommt 1.425.
     */
    @Test
    @DisplayName("Die Marktgebuehr nimmt dem VERKAEUFER, nicht dem Kaeufer")
    void gebuehrTrifftDenVerkaeufer() throws CommandSyntaxException {
        Wirtschaftsstand mitFuenf = mitGebuehr(5);
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe(mitFuenf, WELT).tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS,
                anna);
        anna.vergessen();
        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);
        long annaVorher = kasse.guthaben(anna.kennung());

        probe(mitFuenf, WELT).tippe("freistadt grundstueck kaufen", bert);

        long gebuehr = de.freimarken.kern.Shopregel.gebuehrAuf(ANGEBOTSPREIS, 5);
        assertEquals(1_000_000L - ANGEBOTSPREIS, kasse.guthaben(bert.kennung()),
                "der Kaeufer zahlt den Preis aus dem Angebot und keinen Aufschlag");
        assertEquals(annaVorher + ANGEBOTSPREIS - gebuehr,
                kasse.guthaben(anna.kennung()));
        assertEquals(-gebuehr, gebucht(Buchungsgrund.GRUNDSTUECK_GEBUEHR));
        // ZWEI Zeilen fuer Anna: der Erloes und die Gebuehr darunter. Die zweite
        // nennt BEIDES - den Betrag und den Satz, aus dem er kommt.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("75 Mark"), gelesen().get(1));
        assertTrue(gelesen().get(1).contains("5 Prozent"), gelesen().get(1));
    }

    /**
     * Bei 0 Prozent faellt die Gebuehrzeile ganz weg.
     *
     * <p>Nicht dasselbe wie die Probe darueber: Dort steht ein Satz ueber 0, hier
     * steht die VORGABE. Eine Zeile "Abzueglich 0 Mark Marktgebuehr" waere eine
     * Zeile ueber eine Geldbewegung, die nicht stattgefunden hat.
     */
    @Test
    @DisplayName("Ohne Gebuehrsatz liest der Verkaeufer KEINE Gebuehrzeile")
    void ohneGebuehrKeineZeile() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        anna.vergessen();
        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);

        probe().tippe("freistadt grundstueck kaufen", bert);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertEquals(0L, gebucht(Buchungsgrund.GRUNDSTUECK_GEBUEHR),
                "wo keine Gebuehr faellt, steht auch keine Zeile im Buch");
    }

    /**
     * Ein GESCHENK liest sich wie ein Geschenk und nicht wie ein Kauf fuer null.
     *
     * <p>Der Fall des Stadtbesitzers, der Buerger anwirbt. Die Sprache unterscheidet
     * ihn, weil "fuer 0 Mark gekauft" sich wie ein Fehler liest - und weil ein
     * Geschenk etwas anderes IST.
     */
    @Test
    @DisplayName("Preis 0 ist ein Geschenk - eigene Saetze, kein Geld im Buch")
    void preisNullIstEinGeschenk() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten 0", anna);
        assertTrue(gelesen().get(0).contains("Zu verschenken"), gelesen().get(0));
        anna.vergessen();
        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);
        long annaVorher = kasse.guthaben(anna.kennung());

        probe().tippe("freistadt grundstueck kaufen", bert);

        assertTrue(bert.chatBild().get(0).contains("Bekommen!"),
                bert.chatBild().toString());
        assertTrue(bert.chatBild().get(0).contains("geschenkt von"),
                bert.chatBild().get(0));
        assertTrue(gelesen().get(0).contains("Uebergeben!"), gelesen().get(0));
        assertEquals(bert.kennung(),
                grundstuecke.inStadt(stadt.id()).get(0).eigentuemer());
        // KEIN Geld - und darum auch keine Zeile im Buch. Eine Buchung ueber 0 waere
        // ein Eintrag ohne Vorgang.
        assertEquals(1_000_000L, kasse.guthaben(bert.kennung()));
        assertEquals(annaVorher, kasse.guthaben(anna.kennung()));
        assertEquals(0L, gebucht(Buchungsgrund.GRUNDSTUECK_VERKAUFT));
    }

    @Test
    @DisplayName("Ein zurueckgezogenes Angebot ist nicht mehr zu kaufen")
    void zurueckgezogenIstNichtZuKaufen() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        anna.vergessen();

        probe().tippe("freistadt grundstueck zurueckziehen", anna);
        assertTrue(gelesen().get(0).contains("zurueckgezogen"), gelesen().get(0));
        assertTrue(grundstuecke.angebote(WELT).isEmpty());

        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);
        probe().tippe("freistadt grundstueck kaufen", bert);

        assertTrue(bert.chatBild().get(0).contains("nicht zum Verkauf"),
                bert.chatBild().toString());
        assertEquals(anna.kennung(),
                grundstuecke.inStadt(stadt.id()).get(0).eigentuemer());
        assertEquals(1_000_000L, kasse.guthaben(bert.kennung()));
    }

    @Test
    @DisplayName("Am eigenen Grundstueck kauft niemand - und es bleibt im Angebot")
    void amEigenenWirdNichtGekauft() throws CommandSyntaxException {
        annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        anna.vergessen();

        probe().tippe("freistadt grundstueck kaufen", anna);

        assertTrue(gelesen().get(0).contains("dein eigenes Grundstueck"),
                gelesen().get(0));
        // Der Ausweg steht dabei, und er ist der richtige: zurueckziehen, nicht kaufen.
        assertTrue(gelesen().get(1).contains("zurueckziehen"), gelesen().get(1));
        assertEquals(1, grundstuecke.angebote(WELT).size(),
                "eine Absage darf das Angebot nicht abraeumen");
        assertEquals(1_000_000L - PREIS, kasse.guthaben(anna.kennung()));
    }

    /**
     * Zu wenig Geld - und die Absage nennt BEIDE Zahlen.
     *
     * <p>Ohne den Fehlbetrag muesste der Spieler rechnen. Beide tragen die Einheit,
     * seit jeder Betrag dieses Zweigs aus {@code waehrung.betrag} kommt.
     */
    @Test
    @DisplayName("Wem das Geld fehlt, dem sagt die Absage wie viel")
    void zuWenigGeldNenntBeideZahlen() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        // Der Preis liegt UEBER dem Startguthaben - so braucht die Probe keinen
        // Buchungsgrund, um einen Stand herzustellen. Ein Konto kleiner zu buchen
        // verlangte einen Grund, der eine Ausgabe ist, und keiner davon heisst
        // "die Probe stellt etwas her".
        probe().tippe("freistadt grundstueck anbieten 2000000", anna);
        anna.vergessen();
        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);

        probe().tippe("freistadt grundstueck kaufen", bert);

        assertTrue(bert.chatBild().get(0).contains("2.000.000 Mark"),
                bert.chatBild().toString());
        assertTrue(bert.chatBild().get(0).contains("1.000.000 Mark"),
                bert.chatBild().get(0));
        assertEquals(1_000_000L, kasse.guthaben(bert.kennung()),
                "eine Absage kostet nichts");
        assertEquals(anna.kennung(),
                grundstuecke.inStadt(stadt.id()).get(0).eigentuemer());
    }

    @Test
    @DisplayName("Wo kein Grundstueck liegt, ist keines anzubieten")
    void nichtsAnzubieten() throws CommandSyntaxException {
        annastadtMitAnnaDarin();

        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);

        assertTrue(gelesen().get(0).contains("kein Grundstueck"), gelesen().get(0));
    }

    @Test
    @DisplayName("Ein FREMDES Grundstueck bietet niemand an")
    void fremdesWirdNichtAngeboten() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        Spielerprobe bert = bert();
        stellDich(bert, stadt, 0, 0);

        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, bert);

        assertTrue(bert.chatBild().get(0).contains("gehoert jemand anderem"),
                bert.chatBild().toString());
        assertTrue(grundstuecke.angebote(WELT).isEmpty());
    }

    /**
     * Ohne Preis gibt es den Zweig nicht.
     *
     * <p>Dieselbe Sicherung wie bei {@code /parzelle aufgeben bestaetigen} ohne
     * Nummer: Brigadier verlangt das Argument, und ein halber Befehl darf nichts tun.
     * Ein negativer Preis scheitert schon am Argument - PREIS_UNGUELTIG im Kern ist
     * der zweite Riegel fuer jeden anderen Weg ins Buch.
     */
    @Test
    @DisplayName("anbieten braucht einen Preis, und keinen negativen")
    void anbietenBrauchtEinenPreis() throws CommandSyntaxException {
        annastadtMitEinemGrundstueck();

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt grundstueck anbieten", anna));
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt grundstueck anbieten -1", anna));
        assertEquals(List.of(), gelesen(),
                "ein unvollstaendiger Befehl loest keinen Zweig aus");
        assertTrue(grundstuecke.angebote(WELT).isEmpty());
    }

    /**
     * Die Angebotsliste geht ueber ALLE Staedte der Welt.
     *
     * <p>Darum steht sie neben dem Grundstueckszweig und nicht darin: Sie ist eine
     * Auskunft fuer jeden, der etwas sucht - und dafuer soll niemand das Recht
     * brauchen, das dem Stadtbesitzer gehoert.
     */
    @Test
    @DisplayName("/freistadt angebote zeigt die Angebote aller Staedte - auch ohne Recht")
    void angeboteUeberAlleStaedte() throws CommandSyntaxException {
        annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        anna.vergessen();
        Serverprobe.anmelden(anna, "Anna");
        Spielerprobe nurGast = new Spielerprobe("Gast", Spielerprobe.kennungAus(9),
                FreistadtBefehle.RECHT).inWelt(Serverprobe.welt(WELT));

        probe().tippe("freistadt angebote", nurGast);

        assertEquals(2, nurGast.chatBild().size(), nurGast.chatBild().toString());
        assertTrue(nurGast.chatBild().get(1).contains("1.500 Mark"),
                nurGast.chatBild().get(1));
        assertTrue(nurGast.chatBild().get(1).contains("Anna"),
                nurGast.chatBild().get(1));
    }

    @Test
    @DisplayName("Ohne ein einziges Angebot sagt die Liste es - statt leer zu bleiben")
    void keineAngebote() throws CommandSyntaxException {
        annastadtMitEinemGrundstueck();

        probe().tippe("freistadt angebote", anna);

        // ZWEI Zeilen: Wer nur die Ueberschrift sieht, haelt es fuer einen Fehler.
        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("keines"), gelesen().get(1));
    }

    @Test
    @DisplayName("Ohne das Recht zu kaufen gibt es den Kaufzweig nicht")
    void ohneKaufrechtKeinZweig() throws CommandSyntaxException {
        Parzelle stadt = annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);
        Spielerprobe ohne = new Spielerprobe("Ohne", Spielerprobe.kennungAus(7),
                FreistadtBefehle.RECHT, FreistadtBefehle.RECHT_GRUNDSTUECK)
                .inWelt(Serverprobe.welt(WELT));
        stellDich(ohne, stadt, 0, 0);

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("freistadt grundstueck kaufen", ohne));
        // Anbieten darf er trotzdem - es ist dasselbe Literal daneben, und ein Recht,
        // das das Nachbarwort mitsperrt, waere eine Sperre, die niemand gewollt hat.
        assertEquals(anna.kennung(),
                grundstuecke.inStadt(stadt.id()).get(0).eigentuemer());
    }

    @Test
    @DisplayName("Die Konsole kauft nicht - sie steht nirgends und hat kein Konto")
    void konsoleKauftNicht() throws CommandSyntaxException {
        annastadtMitEinemGrundstueck();
        probe().tippe("freistadt grundstueck anbieten " + ANGEBOTSPREIS, anna);

        probe().tippeAufDerKonsole("freistadt grundstueck kaufen");
        probe().tippeAufDerKonsole("freistadt grundstueck anbieten 1");
        probe().tippeAufDerKonsole("freistadt angebote");

        assertEquals(1, grundstuecke.angebote(WELT).size(),
                "die Konsole hat den Preis nicht angetastet");
        assertEquals(ANGEBOTSPREIS,
                grundstuecke.angebote(WELT).get(0).angebotspreis());
    }
}
