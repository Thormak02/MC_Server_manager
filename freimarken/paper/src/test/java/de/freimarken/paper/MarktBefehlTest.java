package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Ankaufliste;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.List;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /markt} GETIPPT - das Sortiment und der heutige Rest.
 *
 * <p>Dieselbe Bauart wie {@link BerufBefehlTest}: ein echter Brigadier-Baum, ein
 * Spielerstellvertreter, und geprueft wird, was der Spieler liest.
 *
 * <p><b>Diese Probendatei ist am 10.11.2026 kleiner geworden, und das ist der Punkt.</b>
 * Zehn Proben pruefen hier nicht mehr - sie gehoerten zu {@code /markt verkaufen}, und
 * den gibt es nicht mehr: Verkauft wird am SCHILD (Plan 7.1, entschieden 08.11.2026).
 * Was sie gehalten haben, haelt jetzt der Ankaufstand des Bundes, und zwar namentlich
 * dieselben Zusagen:
 *
 * <ul>
 *   <li><b>Nur ganze Mark.</b> Der Befehl liess Stuecke liegen, die keine ganze Mark
 *       ergaben. Am Schild ist die Menge FEST - also muss sie aufgehen, und ein Stand mit
 *       einer unebenen Menge entsteht gar nicht
 *       ({@code ShopbuchTest.unebeneMengeWirdAbgewiesen}).</li>
 *   <li><b>Kurz vor dem Deckel wird nur das Freie gekauft</b> -
 *       {@code ShophandelTest.deckelBeschneidetDenStapel}.</li>
 *   <li><b>Der Deckel zaehlt NUR Verkaeufe</b> -
 *       {@code ShophandelTest.deckelZaehltNurVerkaeufe}.</li>
 *   <li><b>Was der Bund nicht kauft</b> - {@code ShophandelTest.ausDemSortimentGenommen}
 *       und {@code ShopbuchTest.wareNichtImSortiment}.</li>
 * </ul>
 *
 * <p>Geblieben ist die LISTE - und mit ihr die Zahl, die sie verspricht: Was heute noch
 * zu holen ist, kommt aus derselben Stelle wie die Entscheidung des Standes
 * ({@link Markttag}). Zwei Abschriften derselben Abfrage liefen auseinander, und dann
 * verspricht die Liste etwas, das der Stand nicht hergibt.
 */
@DisplayName("/markt getippt")
class MarktBefehlTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String HOCHWALD = "freimarken:hochwald";

    /**
     * Die Uhr, die KASSE und BEFEHL teilen.
     *
     * <p>Dieselbe, und das ist keine Sparsamkeit: Der heutige Rest fragt die Kasse nach
     * den Buchungen SEIT dem Tagesbeginn. Haette die Kasse die Systemuhr und der Befehl
     * eine gestellte, laege eine vorgebuchte Zeile je nach Tageszeit vor oder nach jener
     * Grenze - und die Probe waere nachts rot und mittags gruen. Gefunden hat das genau
     * diese Probe, und zwar nachts.
     */
    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-07T12:00:00Z"), java.time.ZoneOffset.UTC);

    private SpeicherKasse kasse;
    private Spielerprobe anna;
    private World altfurt;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(ALTFURT);
        kasse = new SpeicherKasse(0L, UHR);
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1),
                MarktBefehle.RECHT).inWelt(altfurt);
    }

    /** Das Sortiment dieser Proben: Stein zu 20 Pfennigen, Diamant zu 11 Mark. */
    private static Marktwerte werte(long deckel) {
        return Marktwerte.VORGABE.mitDeckel(deckel)
                .mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                        new Ankaufliste.Ankauf("stone", 20L),
                        new Ankaufliste.Ankauf("diamond", 1_100L))));
    }

    private Befehlsprobe probe(long deckel) {
        return probe(kasse, werte(deckel));
    }

    private Befehlsprobe probe(Kasse welche, Marktwerte werte) {
        Wirtschaftsstand stand = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitMarkt(werte));
        var befehle = new MarktBefehle(stand, welche, UHR,
                Sprachprobe.spracheAusDemJar());
        return new Befehlsprobe(List.of(befehle.markt()));
    }

    /** Was Anna gelesen hat, als Bild. */
    private List<String> gelesen() {
        return anna.chatBild();
    }

    @Test
    @DisplayName("/markt nennt das Sortiment und was heute noch frei ist")
    void marktNenntDasSortiment() throws CommandSyntaxException {
        probe(5_000L).tippe("markt", anna);

        assertEquals(3, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(0).contains("Der Bundesmarkt kauft"), gelesen().get(0));
        // Die Reihenfolge der Datei, nicht alphabetisch - der Betreiber ordnet.
        assertTrue(gelesen().get(1).contains("stone")
                && gelesen().get(1).contains("diamond"), gelesen().get(1));
        assertTrue(gelesen().get(2).contains("5.000 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Das Wort liste zeigt dasselbe wie /markt allein")
    void listeWieOhneWort() throws CommandSyntaxException {
        // Zwei Wege zu einer Antwort, und sie muessen dieselbe geben: Das Wort gehoert in
        // die Vervollstaendigung, damit sichtbar ist, was es gibt - und /markt allein
        // soll nicht mit einer Hilfe antworten, wenn es nur eine Antwort gibt. Zwei
        // Spieler und nicht einer, weil eine Spielerprobe ihren Chat nicht leert.
        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2),
                MarktBefehle.RECHT).inWelt(altfurt);

        probe(5_000L).tippe("markt", anna);
        probe(5_000L).tippe("markt liste", bert);

        assertEquals(anna.chatBild(), bert.chatBild());
        assertEquals(3, bert.chatBild().size(), bert.chatBild().toString());
    }

    @Test
    @DisplayName("Die Liste darf man ueberall ansehen - sie nennt nur Preise")
    void listeUeberall() throws CommandSyntaxException {
        // Wer wissen will, ob sich der Weg nach Altfurt lohnt, soll nicht erst hingehen
        // muessen. Das ist der Unterschied zwischen einer Auskunft und einer Tat - und
        // seit dem 10.11.2026 ist die Auskunft alles, was dieser Befehl kann.
        anna.inWelt(Serverprobe.welt(HOCHWALD));

        probe(5_000L).tippe("markt liste", anna);

        assertTrue(gelesen().get(0).contains("Der Bundesmarkt kauft"), gelesen().get(0));
    }

    @Test
    @DisplayName("VERKAUFEN gibt es nicht mehr - der Zweig ist weg, nicht nur stumm")
    void verkaufenGibtEsNichtMehr() {
        // DIE PROBE ZUR ENTSCHEIDUNG. Verkauft wird am Schild (Plan 7.1), und das muss am
        // Befehlsbaum zu sehen sein: Ein Zweig, der noch da waere und nur nichts taete,
        // liesse jeden, der die alte Hilfe gelesen hat, ins Leere tippen - und die
        // naechste Fassung baute ihn aus Gewohnheit wieder voll.
        //
        // Brigadier wirft bei einem Wort, das es nicht gibt. Dass es auch bei einem
        // VERSTECKTEN Zweig wirft, ist hier ohne Belang: requires steht an der Wurzel,
        // und Anna hat das Recht - die Wurzel antwortet ihr ja.
        Befehlsprobe baum = probe(5_000L);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("markt verkaufen", anna));
    }

    @Test
    @DisplayName("Der heutige Rest zaehlt NUR Verkaeufe - Arbeit schmaelert ihn nicht")
    void restZaehltNurVerkaeufe() throws CommandSyntaxException {
        // Mit einem gemeinsamen Grund waere jede der zwei Quellen durch die andere zu
        // verschliessen: Wer vormittags arbeitet, koennte nachmittags nicht verkaufen.
        // Dieselbe Zusage prueft ShophandelTest am Stand; hier steht sie fuer die ZAHL,
        // die die Liste verspricht - beide fragen Markttag.
        kasse.gutschreiben(anna.kennung(), 10_000L, Buchungsgrund.BERUF_VERDIENST);

        probe(100L).tippe("markt", anna);

        assertTrue(gelesen().get(2).contains("100 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Was heute schon verkauft ist, fehlt im Rest")
    void restSinktMitDemVerkauften() throws CommandSyntaxException {
        kasse.gutschreiben(anna.kennung(), 40L, Buchungsgrund.BUNDESMARKT_VERKAUF);

        probe(100L).tippe("markt", anna);

        assertTrue(gelesen().get(2).contains("60 Mark"), gelesen().get(2));
    }

    @Test
    @DisplayName("Ein leeres Sortiment sagt es - und nennt keinen Rest")
    void leeresSortiment() throws CommandSyntaxException {
        // Eine zulaessige Einstellung: Wer die Liste leer laesst, schaltet den Ankauf ab.
        // Dann ist auch die Zeile ueber den heutigen Rest sinnlos - sie verspraeche Geld
        // fuer Ware, die niemand nimmt.
        probe(kasse, Marktwerte.VORGABE.mitAnkauf(
                Ankaufliste.ausAnkaeufen(List.of()))).tippe("markt", anna);

        assertEquals(2, gelesen().size(), gelesen().toString());
        assertTrue(gelesen().get(1).contains("kauft derzeit nichts"), gelesen().get(1));
    }

    @Test
    @DisplayName("Auf der Konsole gibt es keine Liste - sie hat kein Heute")
    void konsoleHatKeinHeute() throws CommandSyntaxException {
        // Die zweite Zeile nennt den heutigen Rest EINES SPIELERS, und den hat die
        // Konsole nicht. Eine eigene uebersetzte Zeile braucht das nicht: Wer auf der
        // Konsole tippt, ist der Betreiber.
        Befehlsprobe baum = probe(5_000L);

        baum.tippeAufDerKonsole("markt");

        assertEquals(List.of("Der Bundesmarkt braucht einen Spieler."),
                baum.konsoleBild());
    }

    @Test
    @DisplayName("Wirtschaftsstand, Kasse, Uhr und Sprache sind Pflicht")
    void pflichtenGelten() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        var uhr = java.time.Clock.systemUTC();
        var sprache = Sprachprobe.spracheAusDemJar();

        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(null, kasse, uhr, sprache));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, null, uhr, sprache));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, null, sprache));
        assertThrows(IllegalArgumentException.class,
                () -> new MarktBefehle(stand, kasse, uhr, null));
        // VIER und nicht sechs: Der Weltschluessel ist mit /markt verkaufen gegangen -
        // der Bundesmarkt ist jetzt ein Ort, weil seine Schilder dort stehen -, und das
        // Protokoll, weil dieser Befehl nichts mehr bucht, was scheitern koennte.
    }
}
