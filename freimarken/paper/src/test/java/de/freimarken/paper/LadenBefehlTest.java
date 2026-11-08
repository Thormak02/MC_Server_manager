package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Quader;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.SpeicherAbholfach;
import de.freimarken.kern.SpeicherMiete;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /laden} GETIPPT - vom Wort bis zur Buchung.
 *
 * <p>Dieselbe Bauart wie {@link MarktBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest und was im Buch steht.
 *
 * <p><b>Die tragende Probe ist {@link #mietenNurInDerHalle()}:</b> Nur das MIETEN haengt
 * am Ort, verlaengern und kuendigen gehen von ueberall. Ohne eine Probe, die wirklich
 * tippt, waere diese Trennung eine Behauptung - der Ortsvergleich steht in
 * {@link LadenBefehle} und in keiner Naht, also kann ihn nur ein getippter Befehl sehen.
 */
@DisplayName("/laden getippt")
class LadenBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";

    private static final String HOCHWALD = "freimarken:hochwald";

    /** Die Uhr, die KASSE und MIETBUCH teilen - siehe die Begruendung in MarktBefehlTest. */
    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-07T12:00:00Z"), java.time.ZoneOffset.UTC);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherKasse kasse;
    private SpeicherMiete ablage;
    private SpeicherAbholfach fach;
    private Spielerprobe anna;
    private World altfurt;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(ALTFURT);
        kasse = new SpeicherKasse(1_000_000L, UHR);
        ablage = new SpeicherMiete();
        fach = new SpeicherAbholfach();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                LadenBefehle.RECHT, LadenBefehle.RECHT_MIETEN).inWelt(altfurt);
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("LadenprobeLog-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz);
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

    private static Ladeneinheit einheit(String name, int x) {
        return new Ladeneinheit(name, new Quader(x, 71, 4, x + 4, 75, 8), x + 2, 71, 6);
    }

    private Befehlsprobe probe() {
        return probe(Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 60), einheit("halle-2", 70))));
    }

    private Befehlsprobe probe(Ladenverzeichnis verzeichnis) {
        return probe(verzeichnis, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
    }

    private Befehlsprobe probe(Ladenverzeichnis verzeichnis, Wirtschaftsstand stand) {
        Mietbuch mietbuch = new Mietbuch(stand, verzeichnis, ablage, kasse, UHR,
                protokoll);
        var befehle = new LadenBefehle(verzeichnis, mietbuch, abholbuch(),
                Sprachprobe.spracheAusDemJar(), ALTFURT,
                () -> stand.werte().laeden().dauerTage());
        return new Befehlsprobe(List.of(befehle.laden()));
    }

    /**
     * Ein Abholbuch auf dem Fach dieser Probe [Teil C].
     *
     * <p>Ein eigenes Feld fuer das Fach, damit eine Probe etwas hineinlegen kann, bevor
     * sie den Befehl tippt. Ohne das wuerde der Zweig abholfach nur seinen leeren Fall
     * zeigen - und der sagt ueber den Rest nichts.
     */
    private Abholbuch abholbuch() {
        return new Abholbuch(fach, UHR, protokoll);
    }

    /** Was Anna gelesen hat, als Bild. */
    private List<String> gelesen() {
        return anna.chatBild();
    }

    private long gebucht(Buchungsgrund grund) {
        return kasse.summeSeit(anna.kennung(), Instant.EPOCH, grund);
    }

    // --------------------------------------------------------------------- Liste

    @Test
    @DisplayName("/laden nennt die Einheiten und zuletzt den eigenen Laden")
    void ladenNenntDieEinheiten() throws CommandSyntaxException {
        probe().tippe("laden", anna);

        // Kopf, zwei Einheiten, Standzeile.
        assertEquals(4, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Markthalle"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("halle-1"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("halle-2"), gelesen().get(2));
        assertTrue(gelesen().get(3).contains("noch keinen Laden"), gelesen().get(3));
    }

    @Test
    @DisplayName("/laden und /laden liste tun DASSELBE")
    void nackterBefehlUndListeSindGleich() throws CommandSyntaxException {
        probe().tippe("laden", anna);
        List<String> ohne = List.copyOf(gelesen());

        Spielerprobe zweite = new Spielerprobe("Bert", Spielerprobe.kennungAus(2),
                LadenBefehle.RECHT, LadenBefehle.RECHT_MIETEN).inWelt(altfurt);
        probe().tippe("laden liste", zweite);

        assertEquals(ohne, zweite.chatBild());
    }

    @Test
    @DisplayName("Die LISTE darf man ueberall ansehen - nur mieten ist ein Ort")
    void listeUeberall() throws CommandSyntaxException {
        // Wer wissen will, ob sich der Weg nach Altfurt lohnt, soll nicht erst hingehen
        // muessen. Das ist der Unterschied zwischen einer Auskunft und einer Tat.
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe().tippe("laden liste", anna);

        assertTrue(gelesen().get(0).contains("Markthalle"), gelesen().get(0));
    }

    @Test
    @DisplayName("Nach dem Mieten nennt die letzte Zeile den eigenen Laden")
    void standzeileNenntDenEigenenLaden() throws CommandSyntaxException {
        Befehlsprobe probe = probe();
        probe.tippe("laden mieten halle-1", anna);
        anna.vergessen();

        probe.tippe("laden", anna);

        assertEquals(4, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(3).contains("Dein Laden"), gelesen().get(3));
        assertTrue(gelesen().get(3).contains("halle-1"), gelesen().get(3));
        assertTrue(gelesen().get(3).contains("500 Mark"), gelesen().get(3));
    }

    @Test
    @DisplayName("Ohne Einheiten kommt die Leer-Zeile UND die Standzeile")
    void ohneEinheiten() throws CommandSyntaxException {
        probe(Ladenverzeichnis.ohneEinheiten()).tippe("laden", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("noch keine Ladeneinheiten"),
                gelesen().get(1));
    }

    // --------------------------------------------------------------------- Mieten

    @Test
    @DisplayName("Mieten geht NUR in der Halle - verlaengern und kuendigen ueberall")
    void mietenNurInDerHalle() throws CommandSyntaxException {
        // Die tragende Probe dieses Befehls. Der Ortsvergleich steht in LadenBefehle und
        // in keiner Naht - nur ein getippter Befehl kann ihn sehen.
        Befehlsprobe probe = probe();
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe.tippe("laden mieten halle-1", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Altfurt"), gelesen().get(0));
        assertNull(ablage.fuerEinheit("halle-1"), "kein Vertrag");
        assertEquals(0L, gebucht(Buchungsgrund.LADEN_KAUTION), "kein Geld bewegt");
        anna.vergessen();

        // In der Halle geht es, und danach gehen die zwei anderen Taten von ueberall.
        anna.inWelt(altfurt);
        probe.tippe("laden mieten halle-1", anna);
        anna.vergessen();
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe.tippe("laden verlaengern", anna);
        assertTrue(gelesen().get(0).contains("verlaengert"), gelesen().get(0));
        anna.vergessen();

        probe.tippe("laden kuendigen", anna);
        assertTrue(gelesen().get(0).contains("gekuendigt"), gelesen().get(0));
        assertNull(ablage.fuerEinheit("halle-1"));
    }

    @Test
    @DisplayName("Mieten bucht Kaution und Lizenz und meldet drei Zeilen")
    void mietenBuchtUndMeldet() throws CommandSyntaxException {
        probe().tippe("laden mieten halle-1", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("halle-1"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("55.000 Mark"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("500 Mark"), gelesen().get(2));
        assertEquals(-50_000L, gebucht(Buchungsgrund.LADEN_LIZENZ));
        assertEquals(-5_000L, gebucht(Buchungsgrund.LADEN_KAUTION));
        assertEquals(anna.kennung(), ablage.fuerEinheit("halle-1").mieter());
    }

    @Test
    @DisplayName("Ein Stand, den es nicht gibt, bekommt eine Absage mit Hinweis")
    void unbekannterStand() throws CommandSyntaxException {
        probe().tippe("laden mieten halle-9", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("halle-9"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("/laden"), gelesen().get(1));
        assertEquals(0L, gebucht(Buchungsgrund.LADEN_LIZENZ));
    }

    @Test
    @DisplayName("/laden mieten ohne Namen fuehrt nirgendwohin")
    void mietenOhneNamen() {
        // Brigadier verlangt das Argument. Ohne diese Probe koennte ein Zweig ohne
        // Argument entstehen, der den ersten freien Stand nimmt - und das waere eine Tat
        // ohne Entscheidung.
        Befehlsprobe probe = probe();

        assertThrows(CommandSyntaxException.class, () -> probe.tippe("laden mieten",
                anna));
        assertTrue(gelesen().isEmpty(), gelesen().toString());
    }

    @Test
    @DisplayName("Die Absage nennt die Mietdauer von JETZT, nicht die vom Serverstart")
    void dauerKommtVomStand() {
        // Der Befehl bekommt die Dauer als Abfrage und nicht als Zahl - sonst nennte die
        // Absage "noch zu frueh" nach einem Neuladen eine Zahl, die nicht mehr gilt, und
        // der Spieler koennte sie nicht nachrechnen. Eine Einspritzung, die sie beim
        // Bauen festhielt, blieb ohne diese Probe gruen (08.11.2026).
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Befehlsprobe probe = probe(Ladenverzeichnis.ausEinheiten(
                List.of(einheit("halle-1", 60))), stand);
        try {
            probe.tippe("laden mieten halle-1", anna);
            probe.tippe("laden verlaengern", anna);
            anna.vergessen();

            // Das Neuladen mitten im Spiel - genau das, was /freimarken neuladen tut.
            stand.uebernimm(Wirtschaftswerte.VORGABE.mitLaeden(
                    de.freimarken.kern.Mietwerte.VORGABE.mitDauer(7)));
            probe.tippe("laden verlaengern", anna);
        } catch (CommandSyntaxException unmoeglich) {
            throw new AssertionError(unmoeglich);
        }

        assertTrue(gelesen().get(0).contains("als 7 Tage"),
                "die NEUE Dauer: " + gelesen());
    }

    // ---------------------------------------------------------------- Rechte

    @Test
    @DisplayName("Ohne freimarken.laden.mieten bleibt die Liste, die Taten sind weg")
    void ohneMietrechtNurDieListe() throws CommandSyntaxException {
        // Zwei Rechte und nicht eines: Die Liste ist eine Auskunft, das Mieten bewegt
        // Geld. Plan 9 haengt "Laden mieten" am Rang Siedler.
        Spielerprobe nurGucken = new Spielerprobe("Cora", Spielerprobe.kennungAus(3),
                LadenBefehle.RECHT).inWelt(altfurt);
        Befehlsprobe probe = probe();

        probe.tippe("laden", nurGucken);
        assertFalse(nurGucken.chatBild().isEmpty(), "die Liste darf sie sehen");
        nurGucken.vergessen();

        assertThrows(CommandSyntaxException.class,
                () -> probe.tippe("laden mieten halle-1", nurGucken));
        assertThrows(CommandSyntaxException.class,
                () -> probe.tippe("laden verlaengern", nurGucken));
        assertThrows(CommandSyntaxException.class,
                () -> probe.tippe("laden kuendigen", nurGucken));
        assertNull(ablage.fuerEinheit("halle-1"));
    }

    @Test
    @DisplayName("Ohne freimarken.laden gibt es den Befehl ueberhaupt nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe ohne = new Spielerprobe("Dora", Spielerprobe.kennungAus(4))
                .inWelt(altfurt);
        Befehlsprobe probe = probe();

        assertThrows(CommandSyntaxException.class, () -> probe.tippe("laden", ohne));
    }

    @Test
    @DisplayName("Auf der Konsole gibt es keinen Laden")
    void konsoleHatKeinKonto() throws CommandSyntaxException {
        // Die Konsole steht in keiner Welt und hat kein Konto. Spielerpflichtig wie
        // /markt und /mark.
        probe().tippeAufDerKonsole("laden mieten halle-1");

        assertNull(ablage.fuerEinheit("halle-1"));
        assertEquals(0L, gebucht(Buchungsgrund.LADEN_LIZENZ));
    }

    // --------------------------------------------------------------- Abholfach

    @Test
    @DisplayName("/laden abholfach sagt bei leerem Fach, dass es leer ist")
    void abholfachLeer() throws Exception {
        probe().tippe("laden abholfach", anna);

        assertEquals(List.of("&7Dein Abholfach ist leer."), gelesen());
    }

    @Test
    @DisplayName("/laden abholfach braucht das Mietrecht NICHT")
    void abholfachOhneMietrecht() throws Exception {
        // Darin liegt die Ware von Spielern, deren Laden geraeumt wurde. Haenge der
        // Zweig am Mietrecht, dann sperrte ein Betreiber, der dieses Recht einer Gruppe
        // entzieht, genau diesen Spielern den Zugang zu ihrem eigenen Besitz.
        Spielerprobe ohneMietrecht = new Spielerprobe("Carla",
                Spielerprobe.kennungAus(3), LadenBefehle.RECHT).inWelt(altfurt);

        probe().tippe("laden abholfach", ohneMietrecht);

        assertEquals(List.of("&7Dein Abholfach ist leer."), ohneMietrecht.chatBild());
    }

    @Test
    @DisplayName("/laden abholfach geht von UEBERALL - nicht nur in Altfurt")
    void abholfachVonUeberall() throws Exception {
        // Niemand soll nach Altfurt reisen muessen, um an seine eigenen Sachen zu
        // kommen. Nur das MIETEN haengt am Ort.
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe().tippe("laden abholfach", anna);

        assertEquals(List.of("&7Dein Abholfach ist leer."), gelesen());
    }

    @Test
    @DisplayName("Ein Stapel, den das Spiel nicht lesen kann, BLEIBT liegen")
    void unlesbarerStapelBleibtLiegen() throws Exception {
        // Der ganze Weg einmal durch: Befehl, Abholbuch, Ladenware, Satz. Der Inhalt ist
        // kein echter Gegenstand - ohne laufenden Server gibt es keine Registry, an der
        // sich Bytes in einen ItemStack zurueckverwandeln liessen. Genau das macht diese
        // Probe wertvoll: Sie durchlaeuft den Zweig, in dem ein unlesbarer Stapel LIEGEN
        // bleibt, und das ist die Zusage, die das Versprechen des Fachs haelt ("wird nie
        // geloescht").
        fach.lege(anna.kennung(), "Laden halle-1", UHR.instant(),
                List.of(new Abholgut("64x STONE", "kein-echter-gegenstand")));

        probe().tippe("laden abholfach", anna);

        assertEquals(List.of("&cNicht herausgegeben: 1 Stapel. Das bleibt liegen -"
                + " bitte sag es dem Team."), gelesen());
        assertEquals(1, fach.anzahlFuer(anna.kennung()), "der Stapel liegt noch da");
    }

    @Test
    @DisplayName("/laden zeigt am Ende, was im Abholfach wartet")
    void listeZeigtDasFach() throws Exception {
        // NACH der Standzeile und nur, wenn etwas drin liegt. Ein Fach kann voll sein,
        // ohne dass der Spieler je einen Laden hatte - ein Betreiber kann ihm etwas
        // hineinlegen.
        fach.lege(anna.kennung(), "Laden halle-1", UHR.instant(),
                List.of(new Abholgut("64x STONE", "x"), new Abholgut("12x OAK_LOG", "y")));

        probe().tippe("laden", anna);

        List<String> bild = gelesen();
        assertEquals("&7Abholfach: 2 Stapel. /laden abholfach holt sie.",
                bild.get(bild.size() - 1), bild.toString());
        // Und die Standzeile steht DAVOR - zwei Dinge in einer festen Reihenfolge.
        assertTrue(bild.get(bild.size() - 2).contains("noch keinen Laden"),
                bild.toString());
    }

    @Test
    @DisplayName("/laden sagt NICHTS ueber ein leeres Abholfach")
    void listeSchweigtUeberLeeresFach() throws Exception {
        probe().tippe("laden", anna);

        assertTrue(gelesen().stream().noneMatch(zeile -> zeile.contains("Abholfach")),
                gelesen().toString());
    }

    @Test
    @DisplayName("Auf der Konsole gibt es kein Abholfach")
    void abholfachBrauchtEinenSpieler() throws Exception {
        probe().tippeAufDerKonsole("laden abholfach");

        // Nichts gesendet und nichts geholt: Der Konsole gehoert kein Inventar.
        assertEquals(List.of(), gelesen());
    }

    // ---------------------------------------------------------------- Pflichten

    @Test
    @DisplayName("Ohne Welt ist die Markthalle kein Ort")
    void pflichtenGelten() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Ladenverzeichnis leer = Ladenverzeichnis.ohneEinheiten();
        Mietbuch mietbuch = new Mietbuch(stand, leer, ablage, kasse, UHR, protokoll);
        var sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class, () -> new LadenBefehle(leer,
                mietbuch, abholbuch(), sprache, null, () -> 30));
        assertThrows(IllegalArgumentException.class, () -> new LadenBefehle(leer,
                mietbuch, abholbuch(), sprache, "  ", () -> 30));
    }
}
