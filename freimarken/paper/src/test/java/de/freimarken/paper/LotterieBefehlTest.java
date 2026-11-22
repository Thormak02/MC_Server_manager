package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Goldkasse;
import de.freimarken.kern.Lotteriebuch;
import de.freimarken.kern.Lotteriewerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLose;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /lotterie} GETIPPT - der Stand, der Zug und jede Absage.
 *
 * <p><b>Eine Zahltafel sagt nichts darueber, ob ein Zweig antwortet.</b> Genau das ist
 * der Zweck dieser Datei: Ohne sie waere {@code /lotterie ziehen} eine Zeile, die
 * uebersetzt und angemeldet ist - und die im Spiel in Brigadiers englischem "Unknown or
 * incomplete command" endet, weil ihr eigenes {@code executes} fehlt.
 *
 * <p><b>Der WUERFEL ist hier festgenagelt</b>, nicht zufaellig: Jede Probe gibt den Wurf
 * vor, den sie braucht. Ein echter Random machte diese Proben manchmal gruen - und zwar
 * genau die, die einen bestimmten Gewinn erwarten.
 */
@DisplayName("/lotterie getippt")
class LotterieBefehlTest {

    private static final UUID ANNA = Spielerprobe.kennungAus(314);

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Mittags - weit weg von jeder Tagesgrenze. */
    private static final Instant MITTAGS =
            LocalDate.of(2026, 11, 22).atTime(12, 0).atZone(BERLIN).toInstant();

    private Spielerprobe anna;
    private Rucksack rucksack;
    private SpeicherKasse mark;
    private Goldkasse gold;
    private SpeicherLose lose;
    private Wirtschaftsstand stand;

    /** Drei Gewinne mit Gewicht 1 - der Wurf waehlt damit eindeutig. */
    private static Lotteriewerte werte() {
        return new Lotteriewerte(true, 1, 3, 500L, 200, 250_000L, List.of(
                new Lotteriewerte.Gewinn("a-mark", Lotteriewerte.Art.MARK, 1, 700L, "",
                        ""),
                new Lotteriewerte.Gewinn("b-ding", Lotteriewerte.Art.GEGENSTAND, 1, 4L,
                        "diamond", "Schoener Stein"),
                new Lotteriewerte.Gewinn("c-gold", Lotteriewerte.Art.GOLDMARK, 1, 3L, "",
                        "")));
    }

    /** Sortiert nach Schluessel: Wurf 0 trifft a-mark, 1 b-ding, 2 c-gold. */
    private static final long WURF_MARK = 0L;

    private static final long WURF_DING = 1L;

    private static final long WURF_GOLD = 2L;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        anna = new Spielerprobe("Anna", ANNA, LotterieBefehle.RECHT);
        rucksack = new Rucksack();
        mark = new SpeicherKasse(0L);
        gold = new Goldkasse(new SpeicherKasse(0L));
        lose = new SpeicherLose();
        mark.eroeffne(ANNA);
        gold.eroeffne(ANNA);
        stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitLotterie(werte())
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
    }

    /**
     * Der Baum mit einem FESTEN Wurf.
     *
     * <p>Eine anonyme Unterklasse von {@link Random}, wie es die Angelproben tun: Der
     * Wurf ist damit die Entscheidung der Probe und nicht die des Zufalls.
     */
    private Befehlsprobe baum(long wurf) {
        Lotteriebuch buch = new Lotteriebuch(mark, gold, lose, stand);
        Random fest = new Random() {
            @Override
            public long nextLong(long grenze) {
                // GEPRUEFT und nicht nur zurueckgegeben: Ein Wurf jenseits der
                // Gewichtsumme waere ein Fehler der PROBE, und er saehe im Ergebnis aus
                // wie ein Fehler des Programms (Bescheid GESTOERT).
                assertTrue(wurf < grenze, "der Wurf " + wurf + " liegt nicht unter der"
                        + " Gewichtsumme " + grenze + " - die Probe ist falsch gebaut");
                return wurf;
            }
        };
        return new Befehlsprobe(List.of(new LotterieBefehle(
                buch, Sprachprobe.spracheAusDemJar(), () -> MITTAGS, fest,
                spieler -> rucksack).lotterie()));
    }

    /**
     * Der Rucksack mit ZAHLEN - dieselbe Naht, die auch das Muenzhaus benutzt.
     *
     * <p>Und der GRUND, warum die Naht in den Befehl hereingegeben wird: Ein echter
     * {@code ItemStack} braucht die Registry des Servers und wirft auf dem
     * Probenklassenpfad ("No RegistryAccess implementation found"). Mit Zahlen ist der
     * Gegenstandsweg durchlaufbar - und er ist der haeufigste Gewinn der Tabelle.
     */
    private static final class Rucksack implements de.freimarken.kern.Vorrat {
        final java.util.Map<String, Integer> inhalt = new java.util.HashMap<>();
        int freiePlaetze = 36;

        @Override
        public int zaehle(String gegenstand) {
            return inhalt.getOrDefault(gegenstand, 0);
        }

        @Override
        public int nimm(String gegenstand, int stueck) {
            int weg = Math.min(zaehle(gegenstand), stueck);
            inhalt.merge(gegenstand, -weg, Integer::sum);
            return weg;
        }

        @Override
        public int lege(String gegenstand, int stueck) {
            int passt = Math.min(stueck, freiePlaetze * 64);
            inhalt.merge(gegenstand, passt, Integer::sum);
            return passt;
        }

        @Override
        public int fehlenderPlatz(java.util.Map<String, Integer> gegenstaende) {
            int summe = 0;
            for (int stueck : gegenstaende.values()) {
                summe += stueck;
            }
            return Math.max(0, summe - freiePlaetze * 64);
        }
    }

    /** Die LETZTE Zeile, die dieser Spieler gelesen hat - chatBild() leert nicht. */
    private static String letzte(Spielerprobe wer) {
        List<String> alles = wer.chatBild();
        assertFalse(alles.isEmpty(), "der Spieler hat gar nichts gelesen");
        return alles.get(alles.size() - 1);
    }

    @Test
    @DisplayName("1 - /lotterie zeigt Kopf, Stand, Staffel und Hinweis")
    void standZeigen() throws CommandSyntaxException {
        assertEquals(1, baum(WURF_MARK).tippe("lotterie", anna));

        assertEquals(List.of(
                "&#c6a15bDie Lotterie von Altfurt",
                "&#73bf96Dein naechstes Los ist gratis. &#6e7a82Heute gezogen: 0 von 3.",
                "&#6e7a82Die Staffel in Mark: 1. gratis - 2. 500 - 3. 1000 - 4. 2000"
                        + " - 5. 4000",
                "&#6e7a82Ziehen mit /lotterie ziehen"),
                anna.chatBild());
        assertEquals(0, lose.lies(ANNA, LocalDate.of(2026, 11, 22)).loseHeute(),
                "der Stand darf nichts zaehlen");
    }

    @Test
    @DisplayName("2 - /lotterie ziehen zahlt Mark aus und nennt das gratis Los GRATIS")
    void ziehenMark() throws CommandSyntaxException {
        assertEquals(1, baum(WURF_MARK).tippe("lotterie ziehen", anna));

        assertEquals(List.of(
                "&#73bf96a-mark: &#c6a15b700 Mark&#73bf96 &#6e7a82(Los: gratis)"),
                anna.chatBild());
        assertEquals(700L, mark.guthaben(ANNA));
        assertEquals(1, lose.lies(ANNA, LocalDate.of(2026, 11, 22)).loseHeute());
    }

    @Test
    @DisplayName("3 - ein Gegenstandsgewinn landet im Rucksack und nennt seinen NAMEN")
    void ziehenGegenstand() throws CommandSyntaxException {
        assertEquals(1, baum(WURF_DING).tippe("lotterie ziehen", anna));

        assertEquals(List.of(
                "&#73bf96Gewonnen: 4 x Schoener Stein &#6e7a82(Los: gratis)"),
                anna.chatBild());
        assertEquals(4, rucksack.zaehle("diamond"),
                "vier Diamanten muessen wirklich im Rucksack liegen");
    }

    @Test
    @DisplayName("4 - ein Goldgewinn geht auf das GOLDkonto")
    void ziehenGold() throws CommandSyntaxException {
        assertEquals(1, baum(WURF_GOLD).tippe("lotterie ziehen", anna));

        assertEquals(List.of(
                "&#73bf96c-gold: &#c6a15b3 Goldmark&#73bf96 &#6e7a82(Los: gratis)"),
                anna.chatBild());
        assertEquals(3L, gold.guthaben(ANNA));
        assertEquals(0L, mark.guthaben(ANNA), "nicht auf das Markkonto");
    }

    @Test
    @DisplayName("5 - das zweite Los kostet, und der Preis steht in der Zeile")
    void zweitesLosKostet() throws CommandSyntaxException {
        mark.gutschreiben(ANNA, 10_000L, Buchungsgrund.BESTAND);
        Befehlsprobe baum = baum(WURF_MARK);
        baum.tippe("lotterie ziehen", anna);
        baum.tippe("lotterie ziehen", anna);

        // DIE LETZTE Zeile, nicht die ganze Liste: chatBild() leert nicht, es zeigt
        // alles, was dieser Spieler in dieser Probe gelesen hat.
        assertEquals("&#73bf96a-mark: &#c6a15b700 Mark&#73bf96 &#6e7a82(Los: 500 Mark)",
                letzte(anna));
    }

    @Test
    @DisplayName("6 - bei zu wenig Geld steht die Absage MIT beiden Zahlen da")
    void zuWenigGeld() throws CommandSyntaxException {
        // Anna zieht zwei Lose: Das erste ist gratis und bringt 700, das zweite kostet
        // 500 und bringt 700. Das dritte kostet 1000, und sie hat 900.
        Befehlsprobe baum = baum(WURF_MARK);
        baum.tippe("lotterie ziehen", anna);
        baum.tippe("lotterie ziehen", anna);
        long vorher = mark.guthaben(ANNA);

        baum.tippe("lotterie ziehen", anna);

        assertEquals("&#d9564dDas naechste Los kostet 1.000 Mark - du hast 900 Mark.",
                letzte(anna));
        assertEquals(vorher, mark.guthaben(ANNA), "nichts abgebucht");
        assertEquals(2, lose.lies(ANNA, LocalDate.of(2026, 11, 22)).loseHeute(),
                "und nichts gezaehlt - das Los bleibt ihr");
    }

    @Test
    @DisplayName("7 - die Tagesgrenze antwortet mit einem eigenen Satz")
    void tagesgrenze() throws CommandSyntaxException {
        mark.gutschreiben(ANNA, 1_000_000L, Buchungsgrund.BESTAND);
        Befehlsprobe baum = baum(WURF_MARK);
        for (int i = 0; i < 3; i++) {
            baum.tippe("lotterie ziehen", anna);
        }

        baum.tippe("lotterie ziehen", anna);

        assertEquals("&#e3c57eDu hast heute alle 3 Lose gezogen - morgen ist wieder"
                + " eines gratis.", letzte(anna));
    }

    @Test
    @DisplayName("8 - ein voller Rucksack kostet kein Los")
    void vollerRucksack() throws CommandSyntaxException {
        rucksack.freiePlaetze = 0;

        assertEquals(1, baum(WURF_DING).tippe("lotterie ziehen", anna));

        assertEquals("&#d9564dIn deinem Rucksack fehlt Platz fuer 4 Stueck - dein Los"
                + " ist nicht verbraucht.", letzte(anna));
        assertEquals(0, lose.lies(ANNA, LocalDate.of(2026, 11, 22)).loseHeute());
    }

    @Test
    @DisplayName("9 - eine ausgeschaltete Lotterie sagt es, und zwar in BEIDEN Zweigen")
    void ausgeschaltet() throws CommandSyntaxException {
        stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitLotterie(werte().mitAn(false))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        // Der Wurf ist hier egal - er darf gar nicht geworfen werden. Die Zahl 0 ist
        // dabei die scharfe: nextLong(0) WIRFT, und genau davor steht der Riegel.
        Befehlsprobe baum = baum(0L);

        baum.tippe("lotterie", anna);
        assertEquals("&#e3c57eIn Altfurt wird gerade nicht gezogen.", letzte(anna));

        baum.tippe("lotterie ziehen", anna);
        assertEquals("&#e3c57eIn Altfurt wird gerade nicht gezogen.", letzte(anna));
    }

    @Test
    @DisplayName("10 - eine LEERE Gewinntabelle wirft nicht, sie antwortet")
    void leereTabelle() throws CommandSyntaxException {
        // DER RIEGEL VOR DEM WURF: Random.nextLong(0) wirft eine
        // IllegalArgumentException. Ohne die Frage nach der Gewichtsumme lase der
        // Spieler eine rote englische Zeile fuer eine Lage, die der Betreiber
        // eingestellt hat.
        stand.uebernimm(Wirtschaftswerte.VORGABE
                .mitLotterie(new Lotteriewerte(true, 1, 3, 500L, 200, 250_000L,
                        List.of()))
                .mitZeit(new Zeitwerte(BERLIN, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));

        assertEquals(1, baum(0L).tippe("lotterie ziehen", anna));

        assertEquals("&#e3c57eIn Altfurt wird gerade nicht gezogen.", letzte(anna));
    }

    @Test
    @DisplayName("11 - die KONSOLE sieht die Staffel, kann aber nicht ziehen")
    void konsole() throws CommandSyntaxException {
        Befehlsprobe baum = baum(WURF_MARK);

        assertEquals(1, baum.tippeAufDerKonsole("lotterie"));
        List<String> gelesen = baum.konsoleBild();
        assertTrue(gelesen.contains("&#c6a15bDie Lotterie von Altfurt"),
                gelesen.toString());
        assertTrue(gelesen.stream().anyMatch(z -> z.contains("Die Staffel in Mark")),
                gelesen.toString());
        assertFalse(gelesen.stream().anyMatch(z -> z.contains("Heute gezogen")),
                "die Konsole hat kein Konto - eine Standzeile waere eine erfundene: "
                        + gelesen);

        assertEquals(1, baum.tippeAufDerKonsole("lotterie ziehen"));
        assertTrue(baum.konsoleBild().stream()
                        .anyMatch(z -> z.contains("Von der Konsole geht das nicht")),
                baum.konsoleBild().toString());
        assertEquals(0, lose.lies(ANNA, LocalDate.of(2026, 11, 22)).loseHeute());
    }

    @Test
    @DisplayName("12 - ohne das Recht geht der Befehl gar nicht")
    void ohneRecht() {
        Spielerprobe ohne = new Spielerprobe("Ohne", Spielerprobe.kennungAus(315));

        assertThrows(CommandSyntaxException.class,
                () -> baum(WURF_MARK).tippe("lotterie", ohne));
    }

    @Test
    @DisplayName("13 - ein unbekannter Zweig endet nicht stumm")
    void unbekannterZweig() {
        // Brigadier wirft hier, und das ist richtig: Der Spieler bekommt die rote Zeile
        // "Unknown or incomplete command". Geprueft wird, dass /lotterie ziehen NICHT so
        // endet - das waere der Fall, wenn dem Zweig sein eigenes executes fehlte.
        assertThrows(CommandSyntaxException.class,
                () -> baum(WURF_MARK).tippe("lotterie zieh", anna));
    }
}
