package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.List;
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
    private Spielerprobe anna;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        lager = new SpeicherLager();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                FreistadtBefehle.RECHT, FreistadtBefehle.RECHT_GRUENDEN)
                .inWelt(Serverprobe.welt(WELT));
    }

    private Befehlsprobe probe() {
        return probe(new Wirtschaftsstand(Wirtschaftswerte.VORGABE), WELT);
    }

    private Befehlsprobe probe(Wirtschaftsstand stand, String weltschluessel) {
        Freistadtbuch buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                Warpverzeichnis.ausWelten(List.of("kornmark", "freistaedte")), stand);
        var befehle = new FreistadtBefehle(buch, kasse, Sprachprobe.spracheAusDemJar(),
                weltschluessel, () -> stand.werte().freistaedte().hoechstzahl());
        return new Befehlsprobe(List.of(befehle.freistadt()));
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
}
