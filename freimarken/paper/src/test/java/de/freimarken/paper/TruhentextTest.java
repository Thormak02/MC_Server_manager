package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Truhenwerte;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zeilen der Freien Truhe - durch die ECHTE ausgelieferte Vorlage.
 *
 * <p><b>Das ist die Nahtprobe, und {@code BildGleichTest} ist sie nicht.</b> Jene Tafel
 * ruft die Saetze selbst auf, mit Werten, die sie sich selbst gibt - sie haelt also das
 * BILD eines Satzes fest und nichts darueber, ob der richtige Satz gewaehlt und die
 * richtige Zahl eingesetzt wird. Genau das steht hier: WELCHER Satz bei welcher Lage, in
 * welcher Reihenfolge, und wann gar keiner.
 *
 * <p>Dieselbe Linie wie {@code ShoptextTest} und {@code MiettextTest}.
 */
@DisplayName("Die Zeilen der Freien Truhe")
class TruhentextTest {

    /**
     * Die Werte, gegen die diese Probe rechnet - 16 Stueck am Tag, zwei Verbote.
     *
     * <p>Eigene Zahlen und NICHT {@link Truhenwerte#VORGABE}: Ab Werk gilt seit dem
     * 10.11.2026 "kein Deckel, nichts verboten", und eine Probe, die am Deckel oder am
     * Verbot rechnet, braucht beides. Haengt sie an der Vorgabe, prueft sie die Vorgabe
     * statt der Regel - und die naechste Entscheidung des Betreibers bricht ein halbes
     * Dutzend Proben, die mit ihr nichts zu tun haben.
     */
    private static final Truhenwerte WERTE =
            new Truhenwerte(16, Set.of("written_book", "shulker_box"));

    private Sprache sprache;

    @BeforeEach
    void laden() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    @Test
    @DisplayName("Beim Oeffnen MIT Deckel steht die Zahl des Restes")
    void offenMitDeckel() {
        String bild = Sprachprobe.bild(Truhentext.beimOeffnen(sprache, 11));

        assertTrue(bild.contains("11"), bild);
        assertTrue(bild.contains("Hineinlegen darfst du immer"),
                "die halbe Auskunft waere der halbe Zweck: " + bild);
    }

    @Test
    @DisplayName("Beim Oeffnen OHNE Deckel steht ein eigener Satz und keine Zahl")
    void offenOhneDeckel() {
        // Die minus eins ist die Antwort "unbegrenzt" aus Truhenregel.heuteNochFrei. Wer
        // sie als Zahl einsetzte, liesse den Spieler "noch -1 Gegenstaende" lesen.
        String bild = Sprachprobe.bild(Truhentext.beimOeffnen(sprache, -1));

        assertTrue(bild.contains("nimm, was du brauchst"), bild);
        assertFalse(bild.contains("-1"), bild);
    }

    @Test
    @DisplayName("Am Deckel steht die Zahl des DECKELS und nicht der Rest")
    void amDeckel() {
        // Der Rest ist 0, und eine 0 beantwortet die Frage nicht, die der Spieler jetzt
        // hat: "wie viel darf ich ueberhaupt?"
        String bild = Sprachprobe.bild(Truhentext.amDeckel(sprache,
                WERTE.mitEntnahme(32)));

        assertTrue(bild.contains("32"), bild);
        assertFalse(bild.contains(" 0 "), bild);
    }

    @Test
    @DisplayName("Besetzt und gestoert sind zwei verschiedene Saetze")
    void besetztUndGestoert() {
        // Beide heissen "die Truhe geht nicht auf", und sie duerfen nicht dasselbe sagen:
        // Das eine ist ein Augenblick, das andere ein Fehler des Servers.
        String besetzt = Sprachprobe.bild(Truhentext.besetzt(sprache));
        String gestoert = Sprachprobe.bild(Truhentext.gestoert(sprache));

        assertTrue(besetzt.contains("Jemand sieht gerade"), besetzt);
        assertTrue(gestoert.contains("Serverprotokoll"), gestoert);
        assertFalse(besetzt.equals(gestoert));
    }

    @Test
    @DisplayName("Beim Schliessen unter dem Deckel steht EINE Zeile: die Bilanz")
    void schliessenUnterDemDeckel() {
        List<Component> zeilen = Truhentext.beimSchliessen(sprache, WERTE,
                9, 0, 7);

        assertEquals(1, zeilen.size(), Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen).contains("9 Gegenstaende behalten"),
                Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen).contains("7"), Sprachprobe.bild(zeilen));
    }

    @Test
    @DisplayName("Die RUECKGABE steht VOR der Bilanz - erst der Grund, dann die Zahl")
    void rueckgabeVorDerBilanz() {
        // Umgekehrt liest ein Spieler "16 Gegenstaende behalten" und sucht danach die
        // vierzehn, die fehlen.
        // DREI VERSCHIEDENE ZAHLEN, und das ist der Punkt: Mit behalten = 16 waere das
        // Behaltene genau der Deckel, und ob die Rueckgabezeile den DECKEL oder das
        // Behaltene nennt, waere nicht zu unterscheiden. Beides steht in derselben
        // Einsetzung, ein Vertauschen ist eine Zeile Arbeit - und im Spiel liest ein
        // Spieler dann einen Deckel, den es nicht gibt.
        List<Component> zeilen = Truhentext.beimSchliessen(sprache, WERTE,
                9, 14, 7);

        assertEquals(2, zeilen.size(), Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen.getFirst()).contains("gehen zurueck"),
                Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen.get(1)).contains("behalten"),
                Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen.getFirst()).contains("14"),
                "die Rueckgabe nennt, was zurueckgeht: " + Sprachprobe.bild(zeilen));
        assertTrue(Sprachprobe.bild(zeilen.getFirst()).contains("16"),
                "und den DECKEL, nicht das Behaltene: " + Sprachprobe.bild(zeilen));
        assertFalse(Sprachprobe.bild(zeilen.getFirst()).contains("9"),
                "das Behaltene gehoert in die zweite Zeile: " + Sprachprobe.bild(zeilen));
    }

    @Test
    @DisplayName("Wer nichts genommen hat, liest NICHTS")
    void ohneEntnahmeKeineZeile() {
        // Hineinlegen ist der halbe Zweck dieser Truhe und kein Vorgang, der eine Meldung
        // braucht.
        assertEquals(List.of(), Truhentext.beimSchliessen(sprache, WERTE,
                0, 0, 16));
    }

    @Test
    @DisplayName("OHNE Deckel schweigt die Bilanz - ganz")
    void ohneDeckelSchweigtDieBilanz() {
        // "7 Gegenstaende behalten" allein sagt einem Spieler nichts, was er nicht in
        // seinem Rucksack sieht.
        //
        // DAS PAAR MUSS ZUSAMMENPASSEN: nochFrei == -1 heisst entnahmeJeTag == 0, und
        // zur Laufzeit kommen beide aus derselben Quelle. Vorher stand hier WERTE (Deckel
        // 16) zusammen mit -1 und dazu nur ein .size() - eine Lage, die es nie gibt, und
        // der Satz, den ein Spieler wirklich liest, wurde nie angesehen [gefunden von
        // der Lesesuche am 10.11.2026].
        //
        // Eine Rueckgabe kann es OHNE Deckel gar nicht geben: Truhenbuch.beimSchliessen
        // liest den Tagesstand dann nicht, also entsteht auch die Stoerung nicht, die
        // alles zurueckgelegt hat. Was die Rueckgabezeile sagt, prueft
        // rueckgabeVorDerBilanz - mit einem Deckel, der dazu passt.
        Truhenwerte ohne = new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of());

        assertEquals(List.of(), Truhentext.beimSchliessen(sprache, ohne, 7, 0, -1));
        assertEquals(List.of(), Truhentext.beimSchliessen(sprache, ohne, 0, 0, -1));
    }

    @Test
    @DisplayName("Der Materialname kommt unveraendert durch")
    void materialnameUnveraendert() {
        // Er steht NICHT in der Sprachdatei, aus demselben Grund wie die Materialnamen
        // des Bundesmarktes: Vierzig Worte dafuer waeren eine zweite Abschrift einer
        // Liste, die der Betreiber in der config.yml fuehrt.
        String bild = Sprachprobe.bild(Truhentext.verboten(sprache, "WRITTEN_BOOK"));

        assertTrue(bild.contains("WRITTEN_BOOK"), bild);
        assertTrue(bild.contains("zurueck"), bild);
    }
}
