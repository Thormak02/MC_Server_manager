package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /parzelle} GETIPPT - welcher Unterbefehl bei welchem Zweig landet.
 *
 * <p><b>Diese Probe prueft die WEICHE und nicht die Zeilen.</b> Die Zeilen halten
 * {@code AufrufstelleTest} 34 bis 34s und {@code SendewegTest} schon fest: Jenes ruft die
 * Zweige unmittelbar auf, dieses prueft das Bild der reinen Methoden. Was beide nicht
 * koennen, ist der Weg vom getippten Wort zum Zweig - und der ist bei
 * {@code /parzelle} der laengste des Projekts: sieben Unterbefehle, zwei davon mit einem
 * zweiten Wort, einer mit zwei Zahlen dahinter.
 *
 * <p>Eine Zahltafel sieht davon nichts. {@code AUFRUFE_IN_PARZELLENBEFEHLE} zaehlt im
 * Quelltext, dass {@code zeigeListe} einmal gerufen wird; haengt der Aufruf am
 * Unterbefehl {@code nehmen}, bleibt die Zahl eine Eins - und ein Spieler, der
 * {@code /parzelle nehmen} tippt, bekommt seine Liste zu lesen, waehrend die Parzelle
 * unverkauft bleibt.
 *
 * <p><b>Der Boden kommt aus {@link Probewelt}</b> und damit aus derselben Tabelle, aus
 * der auch der Weltgenerator schreibt - kein zweites Blockmodell. Die Welt ist EIN
 * Exemplar fuer Befehl und Spieler, weil {@code inDerMarktstadt} sie mit {@code equals}
 * vergleicht.
 */
class ParzelleBefehlTest {

    private static final String WELT = "freimarken:marktstadt";
    private static final int UNTEN = -64;
    private static final long REICH = 10_000_000L;

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final Probewelt boden = new Probewelt(plan, UNTEN);

    private World welt;
    private SpeicherKasse kasse;
    private Besitzbuch buch;
    private Befehlsprobe baum;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        welt = Serverprobe.welt(WELT, boden);
        kasse = new SpeicherKasse(REICH);
        buch = new Besitzbuch(Stadtgrenze.marktstadt(), new SpeicherLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        ParzellenBefehle befehle = new ParzellenBefehle(buch, welt,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                Logger.getLogger("ParzelleBefehlTest"), Sprachprobe.spracheAusDemJar());
        baum = new Befehlsprobe(List.of(befehle.parzelle()));
    }

    /** Ein Spieler, der auf der Mitte der Parzelle 0/0 steht und ein Konto hat. */
    private Spielerprobe auf(int parzelleX, int parzelleZ, int zahl) {
        Spielerprobe spieler = new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl));
        kasse.eroeffne(spieler.kennung());
        return spieler.inWelt(welt).anOrt(new Location(welt, plan.mitteX(parzelleX),
                UNTEN + 1, plan.mitteZ(parzelleZ)));
    }

    @Test
    @DisplayName("40 - /parzelle und /parzelle info landen beim selben Zweig")
    void parzelleUndInfoLandenGleich() throws CommandSyntaxException {
        Spielerprobe ohneWort = auf(0, 0, 71);
        Spielerprobe mitWort = auf(0, 0, 72);

        assertEquals(1, baum.tippe("parzelle", ohneWort));
        assertEquals(1, baum.tippe("parzelle info", mitWort));

        // Dass BEIDE denselben Zweig treffen, steht nirgends sonst: Im Quelltext sind es
        // zwei getrennte executes, und eine Zahltafel zaehlt zwei Aufrufe von zeigeInfo -
        // egal, ob sie dasselbe tun.
        assertEquals(ohneWort.chatBild(), mitWort.chatBild(),
                "das nackte /parzelle ist die Kurzform von /parzelle info");
        assertFalse(ohneWort.chatBild().isEmpty(), "und beide antworten ueberhaupt");
    }

    @Test
    @DisplayName("40b - jeder Unterbefehl landet bei seinem eigenen Zweig")
    void jederUnterbefehlLandetBeiSeinemZweig() throws CommandSyntaxException {
        // Vier Unterbefehle, vier verschiedene Antworten. Die Bilder selbst sind in
        // AufrufstelleTest festgenagelt; hier zaehlt, dass sie sich UNTERSCHEIDEN -
        // ein vertauschter Aufruf macht zwei davon gleich.
        List<String> info = antwortAuf("parzelle info", 73);
        List<String> liste = antwortAuf("parzelle liste", 74);
        List<String> mitbauer = antwortAuf("parzelle mitbauer", 75);
        List<String> aufgeben = antwortAuf("parzelle aufgeben", 76);

        List<List<String>> alle = List.of(info, liste, mitbauer, aufgeben);
        for (List<String> antwort : alle) {
            assertFalse(antwort.isEmpty(), "jeder Zweig antwortet: " + alle);
        }
        assertEquals(alle.size(), new java.util.HashSet<>(alle).size(),
                "vier Unterbefehle, vier verschiedene Antworten - zwei gleiche heissen,"
                        + " dass ein Aufruf am falschen Wort haengt: " + alle);
    }

    private List<String> antwortAuf(String befehl, int zahl) throws CommandSyntaxException {
        Spielerprobe spieler = auf(0, 0, zahl);
        baum.tippe(befehl, spieler);
        return spieler.chatBild();
    }

    @Test
    @DisplayName("40c - /parzelle nehmen nimmt wirklich, und die Kasse zahlt")
    void nehmenNimmtWirklich() throws CommandSyntaxException {
        Spielerprobe spieler = auf(0, 0, 77);
        long vorher = kasse.guthaben(spieler.kennung());

        assertEquals(1, baum.tippe("parzelle nehmen", spieler));

        // Der Zweig TUT etwas, und das sieht keine Zahltafel: Die erste Parzelle ist
        // gratis, also darf das Guthaben nicht fallen - aber eingetragen sein muss sie.
        // Der Zweig TUT etwas, und das sieht keine Zahltafel: Die Parzelle muss
        // nach dem Befehl in der Ablage stehen, und zwar auf diesen Spieler.
        var eingetragen = buch.zustaendigerBesitz(WELT, plan.mitteX(0),
                plan.mitteZ(0));
        assertTrue(eingetragen.isPresent(), "nach /parzelle nehmen steht sie im"
                + " Buch - sonst liest der Spieler eine Zusage und besitzt nichts");
        assertEquals(spieler.kennung(), eingetragen.get().eigentuemer(),
                "und zwar auf ihn und nicht auf einen anderen");
        assertEquals(vorher, kasse.guthaben(spieler.kennung()),
                "die erste Parzelle ist gratis - faellt hier Geld, kostet die erste"
                        + " Parzelle jeden Neuling sein Startguthaben");
    }

    @Test
    @DisplayName("40d - aufgeben braucht das zweite Wort UND die Nummer")
    void aufgebenBrauchtWortUndNummer() throws CommandSyntaxException {
        Spielerprobe spieler = auf(0, 0, 78);
        baum.tippe("parzelle nehmen", spieler);
        spieler.vergessen();

        // Ohne Nummer gibt es den Zweig nicht - das ist die Sicherung gegen den Vertipper,
        // der eine bezahlte Parzelle kostet. Brigadier verlangt beide Zahlen.
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("parzelle aufgeben bestaetigen", spieler));
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("parzelle aufgeben bestaetigen 0", spieler));
        assertEquals(List.of(), spieler.chatBild(),
                "ein unvollstaendiger Befehl loest keinen Zweig aus");

        assertEquals(1, baum.tippe("parzelle aufgeben bestaetigen 0 0", spieler));
        assertFalse(spieler.chatBild().isEmpty(), "mit Nummer antwortet der Zweig");
    }

    @Test
    @DisplayName("40e - auf der Konsole ist /parzelle wortgleich mit einem Tippfehler")
    void aufDerKonsoleWieEinTippfehler() {
        var versteckt = assertThrows(CommandSyntaxException.class,
                () -> baum.tippeAufDerKonsole("parzelle"));
        var gibtEsNicht = assertThrows(CommandSyntaxException.class,
                () -> baum.tippeAufDerKonsole("parzellex"));

        assertEquals(gibtEsNicht.getMessage(), versteckt.getMessage(),
                "dieselbe Unschoenheit wie bei /mark, und derselbe Grund fuer die"
                        + " eigene Bereitmeldung beim Start");
    }
}
