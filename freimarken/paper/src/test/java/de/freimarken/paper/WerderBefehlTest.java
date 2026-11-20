package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Inselgrenze;
import de.freimarken.kern.Inselplan;
import de.freimarken.kern.Inselwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /werder} GETIPPT - vom Wort bis zur Buchung.
 *
 * <p>Dieselbe Bauart wie {@link FreistadtBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest und was im Buch steht.
 *
 * <p><b>Zwei Proben kann nur ein getippter Befehl machen</b>, und beide bewachen eine
 * Entscheidung dieses Schritts: dass {@code /werder hafen} ohne {@code nehmen} eine
 * deutsche Auskunft gibt und nicht Brigadiers englisches "Unknown or incomplete command"
 * (ein passendes Literal verdeckt jedes Geschwister vollstaendig), und dass die
 * HOECHSTZAHL wirklich aus dem RECHT kommt - das war der Wunsch des Nutzers, und im Buch
 * ist davon nichts zu sehen, weil die Zahl von aussen hereinkommt.
 */
@DisplayName("/werder getippt")
class WerderBefehlTest {

    private static final String WELT = "freimarken:hafenwerder";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    private static final long PREIS = Inselwerte.VORGABE_PREIS;

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    private SpeicherKasse kasse;
    private SpeicherLager lager;
    private Inselbuch buch;
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
        buch = new Inselbuch(Inselgrenze.hafenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        anna = spieler("Anna", 1, WerderBefehle.RECHT, WerderBefehle.RECHT_NEHMEN);
        stelle(anna, mitte(1), mitte(1));
    }

    private Spielerprobe spieler(String name, int nummer, String... rechte) {
        return new Spielerprobe(name, Spielerprobe.kennungAus(nummer), rechte)
                .inWelt(Serverprobe.welt(WELT));
    }

    /** Stellt den Spieler an diese Stelle in Hafenwerder. */
    private void stelle(Spielerprobe wer, int x, int z) {
        wer.anOrt(new Location(Serverprobe.welt(WELT), x, 64, z));
    }

    private int mitte(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Befehlsprobe probe() {
        return probe(buch, WELT);
    }

    private Befehlsprobe probe(Inselbuch welches, String weltschluessel) {
        return new Befehlsprobe(List.of(new WerderBefehle(welches, kasse,
                Sprachprobe.spracheAusDemJar(), weltschluessel, "hafenwerder",
                () -> Wirtschaftswerte.VORGABE.inseln().hoechstzahl(),
                () -> Wirtschaftswerte.VORGABE.rangWerbung()).werder()));
    }

    private List<String> gelesen() {
        return anna.chatBild();
    }

    // --------------------------------------------------------------------- Liste

    @Test
    @DisplayName("/werder sagt ab Werk, dass man keine Insel hat - und was eine kostet")
    void leereListe() throws CommandSyntaxException {
        probe().tippe("werder", anna);

        assertEquals(1, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("keine Meerinsel"), gelesen().get(0));
        assertTrue(gelesen().get(0).contains("75.000 Mark"),
                "der Preis steht darin, denn das ist die naechste Frage: "
                        + gelesen().get(0));
    }

    @Test
    @DisplayName("Mit Inseln kommt die Ueberschrift und je eine Zeile")
    void listeMitInseln() throws CommandSyntaxException {
        buch.nehmen(WELT, anna.kennung(), mitte(1), mitte(1), 9);
        buch.nehmen(WELT, anna.kennung(), mitte(2), mitte(-3), 9);

        probe().tippe("werder", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Deine Meerinseln: 2"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("Insel 1/1"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("Insel 2/-3"),
                "die Adresse ist die FELDnummer - mit ihr findet er sie wieder: "
                        + gelesen().get(2));
    }

    @Test
    @DisplayName("/werder und /werder liste tun DASSELBE")
    void nackterBefehlUndListeSindGleich() throws CommandSyntaxException {
        buch.nehmen(WELT, anna.kennung(), mitte(1), mitte(1), 9);
        // DERSELBE Spieler tippt beides: Ein zweiter haette eine andere Liste - keine
        // Insel, also den Satz werder.leer -, und die Probe verglichen zwei
        // Verschiedene statt zwei Wege.
        probe().tippe("werder", anna);
        List<String> ohne = List.copyOf(gelesen());
        anna.vergessen();
        probe().tippe("werder liste", anna);

        assertEquals(2, ohne.size(), "Ueberschrift und eine Zeile: " + ohne);
        assertEquals(ohne, gelesen(),
                "kein zweiter Weg zu einer zweiten Antwort - Zeichen fuer Zeichen"
                        + " dasselbe");
    }

    @Test
    @DisplayName("Die Liste geht UEBERALL - wer sie braucht, steht nicht darauf")
    void listeAuchAusserhalb() throws CommandSyntaxException {
        buch.nehmen(WELT, anna.kennung(), mitte(1), mitte(1), 9);
        anna.anOrt(new Location(Serverprobe.welt(MARKTSTADT), 0, 64, 0));

        probe().tippe("werder", anna);

        assertTrue(gelesen().get(0).contains("Deine Meerinsel"), gelesen().get(0));
        assertFalse(gelesen().get(0).contains("nur in"),
                "die Weltabsage gehoert zu hafen und nicht zur Liste: " + gelesen());
    }

    // ------------------------------------------------------------------ Was hier liegt

    @Test
    @DisplayName("/werder hafen ALLEIN gibt eine deutsche Auskunft")
    void hafenAlleinAntwortetDeutsch() throws CommandSyntaxException {
        // DIE PROBE, DIE KEINE NAHT MACHEN KANN: Ein passendes Literal verdeckt in
        // Brigadier jedes Geschwister vollstaendig (CommandNode.getRelevantNodes). Ohne
        // eigenes .executes am Literal "hafen" endete dieser Befehl in Brigadiers
        // englischem "Unknown or incomplete command" - die Lehre von Teil C3, und sie
        // ist nur an einem getippten Befehl zu sehen.
        probe().tippe("werder hafen", anna);

        assertEquals(2, gelesen().size(),
                "ZWEI Zeilen: die Lage und der Ausweg. Die zweite kommt nur an, weil"
                        + " der Satz ueber bau geholt wird - eineZeile wuerfe: "
                        + gelesen());
        assertTrue(gelesen().get(0).contains("Insel 1/1 ist frei"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("/werder hafen nehmen"),
                gelesen().get(1));
    }

    @Test
    @DisplayName("Im offenen Meer sagt es genau das")
    void imOffenenMeer() throws CommandSyntaxException {
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;
        stelle(anna, imMeer, mitte(1));

        probe().tippe("werder hafen", anna);

        assertTrue(gelesen().get(0).contains("offenes Meer"), gelesen().get(0));
    }

    @Test
    @DisplayName("Auf der eigenen und auf der fremden Insel liest man Verschiedenes")
    void eigeneUndFremdeInsel() throws CommandSyntaxException {
        buch.nehmen(WELT, anna.kennung(), mitte(1), mitte(1), 9);
        probe().tippe("werder hafen", anna);
        String eigene = gelesen().get(0);

        Spielerprobe bert = spieler("Bert", 2, WerderBefehle.RECHT);
        stelle(bert, mitte(1), mitte(1));
        Serverprobe.nenne(anna.kennung(), "Anna");
        probe().tippe("werder hafen", bert);

        assertTrue(eigene.contains("gehoert dir"), eigene);
        assertTrue(bert.chatBild().get(0).contains("Anna"),
                "der Fremde liest den NAMEN - ohne ihn weiss er nicht, wen er fragen"
                        + " muss: " + bert.chatBild());
    }

    // ------------------------------------------------------------------ Nehmen

    @Test
    @DisplayName("/werder hafen nehmen gibt die Insel und bucht ab")
    void nehmenGelingt() throws CommandSyntaxException {
        probe().tippe("werder hafen nehmen", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("gehoert jetzt dir"), gelesen().get(0));
        assertTrue(gelesen().get(1).contains("Es bleiben"),
                "die zweite Zeile nennt den Rest - und sie kommt nur an, weil der Satz"
                        + " ueber bau geholt wird: " + gelesen());
        assertTrue(lager.finde(WELT, 1, 1).isPresent());
        assertEquals(-PREIS, kasse.summeSeit(anna.kennung(), Instant.EPOCH,
                Buchungsgrund.WERDER_GEKAUFT));
    }

    @Test
    @DisplayName("Ausserhalb von Hafenwerder wird nichts genommen")
    void nehmenNurInHafenwerder() throws CommandSyntaxException {
        anna.anOrt(new Location(Serverprobe.welt(MARKTSTADT), mitte(1), 64, mitte(1)));

        probe().tippe("werder hafen nehmen", anna);

        assertTrue(gelesen().get(0).contains("nur in hafenwerder"),
                "und der Satz nennt den Namen OHNE Namensraum - so tippt man ihn: "
                        + gelesen().get(0));
        assertTrue(lager.finde(WELT, 1, 1).isEmpty(), "und nichts vergeben");
        assertEquals(1_000_000L, kasse.guthaben(anna.kennung()));
    }

    @Test
    @DisplayName("DIE HOECHSTZAHL KOMMT AUS DEM RECHT - nicht aus der config.yml allein")
    void hoechstzahlKommtAusDemRecht() throws CommandSyntaxException {
        // DER WUNSCH DES NUTZERS [18.11.2026]: "Nach Rang, wie die Parzellen". Im Buch
        // ist davon nichts zu sehen - die Zahl kommt von aussen herein, und ob der
        // Befehl sie wirklich aus den Rechten holt, sieht NUR ein getippter Befehl.
        //
        // Die config.yml erlaubt eine Insel. Anna hat das Recht auf drei.
        probe().tippe("werder hafen nehmen", anna);
        stelle(anna, mitte(2), mitte(2));
        probe().tippe("werder hafen nehmen", anna);
        assertEquals(1, buch.anzahlVon(WELT, anna.kennung()),
                "ohne Recht bleibt es bei der einen - sonst prueft die Zeile danach"
                        + " nichts: " + gelesen());
        // ZWEI Zeilen seit Teil H: die Absage und darunter die Werbezeile aus
        // hinweise.rang-werbung. Geprueft werden beide und ihre REIHENFOLGE - das
        // Angebot unter der Nachricht, nicht davor.
        assertTrue(gelesen().get(gelesen().size() - 2).contains("mehr erlaubt dein Rang"),
                gelesen().toString());
        assertEquals("&e" + Wirtschaftswerte.VORGABE_RANG_WERBUNG,
                gelesen().get(gelesen().size() - 1),
                "unter der Absage gehoert hinweise.rang-werbung: " + gelesen());

        Spielerprobe reich = spieler("Reich", 3, WerderBefehle.RECHT,
                WerderBefehle.RECHT_NEHMEN, "freimarken.inseln.3");
        stelle(reich, mitte(3), mitte(3));
        probe().tippe("werder hafen nehmen", reich);
        stelle(reich, mitte(4), mitte(4));
        probe().tippe("werder hafen nehmen", reich);

        assertEquals(2, buch.anzahlVon(WELT, reich.kennung()),
                "mit freimarken.inseln.3 geht die zweite durch: " + reich.chatBild());
    }

    @Test
    @DisplayName("Der ZUSCHLAG kommt oben drauf")
    void zuschlagKommtDrauf() throws CommandSyntaxException {
        // freimarken.extra.inseln.1 ist die Mitgliedschaft: Sie LEGT etwas auf den Rang,
        // sie ersetzt ihn nicht. Ohne die zweite Vorsilbe im Befehl waere diese Zeile
        // ohne Wirkung - und niemand sagte es.
        Spielerprobe gold = spieler("Gold", 4, WerderBefehle.RECHT,
                WerderBefehle.RECHT_NEHMEN, "freimarken.extra.inseln.1");
        stelle(gold, mitte(5), mitte(5));
        probe().tippe("werder hafen nehmen", gold);
        stelle(gold, mitte(6), mitte(6));
        probe().tippe("werder hafen nehmen", gold);

        assertEquals(2, buch.anzahlVon(WELT, gold.kennung()),
                "eine aus der config.yml plus eine aus dem Zuschlag: "
                        + gold.chatBild());
    }

    @Test
    @DisplayName("Ohne das Recht zum Nehmen ist der Zweig nicht erreichbar")
    void ohneRechtKeinNehmen() throws CommandSyntaxException {
        Spielerprobe nurSehen = spieler("Schauer", 5, WerderBefehle.RECHT);
        stelle(nurSehen, mitte(1), mitte(1));

        probe().tippe("werder hafen", nurSehen);
        assertEquals(2, nurSehen.chatBild().size(), "sehen darf er");

        // Brigadier laesst den Zweig gar nicht zu: Ein nicht erlaubter Knoten ist fuer
        // diesen Spieler nicht vorhanden, und der Baum antwortet mit seinem eigenen
        // Fehler. Geprueft wird darum, dass NICHTS passiert ist.
        try {
            probe().tippe("werder hafen nehmen", nurSehen);
        } catch (CommandSyntaxException erwartet) {
            // So soll es sein.
        }

        assertTrue(lager.finde(WELT, 1, 1).isEmpty(), "und keine Insel vergeben");
    }

    // ------------------------------------------------------------------ Ohne Welt

    @Test
    @DisplayName("Ohne Inselwelt sagt JEDER Zweig es - und keiner wirft")
    void ohneWelt() throws CommandSyntaxException {
        // Der Befehl wird auch dann angemeldet: Einen Befehl, den es je nach
        // Einstellungsdatei gibt oder nicht, versteht kein Spieler. Also muss jeder
        // Zweig mit einem null-Buch umgehen koennen.
        Befehlsprobe ohne = probe(null, "");

        ohne.tippe("werder", anna);
        ohne.tippe("werder hafen", anna);
        ohne.tippe("werder hafen nehmen", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        for (String zeile : gelesen()) {
            assertTrue(zeile.contains("keine Inselwelt"), zeile);
        }
    }

    @Test
    @DisplayName("Die Konsole bekommt gar nichts - der Befehl ist spielerpflichtig")
    void konsoleIstKeinSpieler() throws CommandSyntaxException {
        Befehlsprobe probe = probe();

        probe.tippeAufDerKonsole("werder");

        assertEquals(List.of(), probe.konsoleBild(),
                "genommen wird fuer jemanden, dem Geld gehoert und der irgendwo STEHT");
    }
}
