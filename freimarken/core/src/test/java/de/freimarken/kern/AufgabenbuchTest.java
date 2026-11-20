package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel ueber die Herausforderungen - abgeben, pruefen, bezahlen.
 *
 * <p>Geprueft wird, was ein Spieler merkt: Sind meine Gegenstaende weg? Ist das Geld da?
 * Steht die Aufgabe? Und vor allem - habe ich noch alles, wenn es NICHT geklappt hat?
 *
 * <p><b>Der {@link Vorrat} ist hier eine Karte</b>, und genau darum laeuft
 * jeder Zweig: Auf einem Server liegt dahinter ein Inventar, dessen
 * {@code removeItem} nicht unteilbar ist. Diese Probe kann den Fall stellen, in dem
 * gezaehlt genug da war und genommen zu wenig - und das ist der Zweig, in dem ein Spieler
 * Gegenstaende verlieren koennte.
 */
@DisplayName("Die Regel ueber die Herausforderungen")
class AufgabenbuchTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    /** Eine Aufgabe mit ZWEI Gegenstaenden - erst damit greift das Zurueckgeben. */
    private static final Aufgabenwerte.Aufgabe ZWEITEILIG = new Aufgabenwerte.Aufgabe(
            "kohlebrenner", mengen("coal", 16, "furnace", 1), 150L);

    private static final Aufgabenwerte.Aufgabe EINFACH = new Aufgabenwerte.Aufgabe(
            "erster-stein", Map.of("cobblestone", 64), 50L);

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherKasse kasse;
    private SpeicherWolken staende;
    private Wirtschaftsstand wirtschaft;
    private Wolkenbuch buch;
    private Aufgabenbuch aufgaben;
    private Kartenvorrat rucksack;
    private Parzelle insel;

    @BeforeEach
    void vorher() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(1_000L, UHR);
        staende = new SpeicherWolken();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(new Aufgabenwerte(true,
                        List.of(EINFACH, ZWEITEILIG)))));
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse, wirtschaft);
        aufgaben = new Aufgabenbuch(buch, staende, kasse, wirtschaft);
        rucksack = new Kartenvorrat();
        insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
    }

    /** Eine Weltkoordinate mitten auf der Insel. */
    private int aufDerInsel(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Aufgabenbuch.Entscheid abgeben(UUID wer, String schluessel) {
        return aufgaben.abgeben(WELT, wer, aufDerInsel(insel.x()),
                aufDerInsel(insel.z()), schluessel, rucksack);
    }

    // ===================================================== Das Abgeben

    @Test
    @DisplayName("Abgeben nimmt die Gegenstaende, traegt ein und zahlt")
    void abgebenGelingt() {
        rucksack.hinein("cobblestone", 100);
        long vorher = kasse.guthaben(anna);

        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "erster-stein");

        assertTrue(entscheid.erteilt(), entscheid.toString());
        assertEquals(50L, entscheid.belohnung());
        assertEquals(1, entscheid.stufe(), "die neue Stufe, nicht die alte");
        assertEquals(36, rucksack.zaehle("cobblestone"), "64 sind weg");
        assertEquals(vorher + 50L, kasse.guthaben(anna));
        assertTrue(staende.stand(insel.id()).hatErfuellt("erster-stein"));
        assertEquals(1, staende.stand(insel.id()).stufe());
    }

    @Test
    @DisplayName("Die Buchung steht im Kontobuch - auch bei Lohn 0")
    void buchungIstDieSpur() {
        // Dieselbe Begruendung wie bei WOLKE_GENOMMEN: Die Zeile mit 0 Mark ist die SPUR.
        // Sie sagt, WANN die Insel ihre Stufe bekommen hat.
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(new Aufgabenwerte(true, List.of(
                        new Aufgabenwerte.Aufgabe("ohne-lohn",
                                Map.of("cobblestone", 1), 0L))))));
        rucksack.hinein("cobblestone", 1);

        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "ohne-lohn");

        assertTrue(entscheid.erteilt());
        assertEquals(0L, entscheid.belohnung());
        assertEquals(1, kasse.verlauf(anna, 10).stream()
                        .filter(zeile -> zeile.grund() == Buchungsgrund.WOLKE_AUFGABE)
                        .count(),
                "ohne diese Zeile fehlte der Vorgang im Kontobuch ganz");
    }

    @Test
    @DisplayName("Fehlt etwas, bleibt ALLES liegen - und der Entscheid sagt WAS")
    void fehltEtwas() {
        rucksack.hinein("coal", 10);
        rucksack.hinein("furnace", 1);

        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "kohlebrenner");

        assertEquals(Aufgabenbuch.Bescheid.FEHLT, entscheid.bescheid());
        assertEquals(Map.of("coal", 6), entscheid.fehlt(),
                "das FEHLENDE und nicht das Verlangte - wer 10 von 16 hat, will die 6");
        assertEquals(10, rucksack.zaehle("coal"), "nichts genommen");
        assertEquals(1, rucksack.zaehle("furnace"), "auch nicht das Vollstaendige");
        assertEquals(1_000L, kasse.guthaben(anna), "und nichts gezahlt");
        assertEquals(0, staende.stand(insel.id()).stufe());
    }

    @Test
    @DisplayName("Was gar nicht da ist, steht mit der GANZEN Menge im Entscheid")
    void garNichtsDa() {
        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "kohlebrenner");

        assertEquals(Aufgabenbuch.Bescheid.FEHLT, entscheid.bescheid());
        assertEquals(mengen("coal", 16, "furnace", 1), entscheid.fehlt());
    }

    @Test
    @DisplayName("Nimmt der Vorrat zu wenig, kommt das Genommene ZURUECK")
    void halbesNehmenGehtZurueck() {
        // DIE TRAGENDE PROBE DIESES BUCHES. Auf einem Server ist removeItem nicht
        // unteilbar: Was ein Spieler zwischen dem Zaehlen und dem Nehmen wegwirft, ist
        // weg. Ohne das Zurueckgeben verlor er die Kohle UND bekaeme die Aufgabe nicht -
        // und das ist die Art Fehler, die ein Spieler als Diebstahl liest.
        rucksack.hinein("coal", 16);
        rucksack.hinein("furnace", 1);
        rucksack.luegeBeim("furnace");

        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "kohlebrenner");

        assertEquals(Aufgabenbuch.Bescheid.NICHT_GENOMMEN, entscheid.bescheid());
        assertEquals(16, rucksack.zaehle("coal"), "die Kohle ist zurueck");
        assertEquals(1, rucksack.zaehle("furnace"), "und der Ofen liegt noch da");
        assertEquals(1_000L, kasse.guthaben(anna), "nichts gezahlt");
        assertEquals(0, staende.stand(insel.id()).stufe(), "nichts eingetragen");
    }

    @Test
    @DisplayName("Sagt die Ablage NEIN, kommt alles zurueck - der Wettlauf")
    void wettlaufGibtZurueck() {
        // Zwei Abgaben im selben Augenblick: Der eindeutige Schluessel in der Ablage
        // laesst den zweiten auflaufen. Das ist der Riegel gegen die doppelte Belohnung -
        // und der zweite Spieler muss seine Gegenstaende behalten.
        rucksack.hinein("cobblestone", 64);
        Aufgabenbuch mitNein = new Aufgabenbuch(buch, new Nickablage(staende, false),
                kasse, wirtschaft);

        Aufgabenbuch.Entscheid entscheid = mitNein.abgeben(WELT, anna,
                aufDerInsel(insel.x()), aufDerInsel(insel.z()), "erster-stein",
                rucksack);

        assertEquals(Aufgabenbuch.Bescheid.SCHON_ERFUELLT, entscheid.bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"));
        assertEquals(1_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Wirft die Ablage, kommt alles zurueck")
    void gestoertGibtZurueck() {
        rucksack.hinein("cobblestone", 64);
        Aufgabenbuch mitWurf = new Aufgabenbuch(buch, new Nickablage(staende, true),
                kasse, wirtschaft);

        Aufgabenbuch.Entscheid entscheid = mitWurf.abgeben(WELT, anna,
                aufDerInsel(insel.x()), aufDerInsel(insel.z()), "erster-stein",
                rucksack);

        assertEquals(Aufgabenbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"),
                "ein stiller Verlust waere die Art Fehler, die als Diebstahl gelesen"
                        + " wird");
        assertEquals(1_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Dieselbe Aufgabe ein zweites Mal: nichts genommen, nichts gezahlt")
    void zweimalAbgeben() {
        rucksack.hinein("cobblestone", 128);
        abgeben(anna, "erster-stein");
        long nachErstem = kasse.guthaben(anna);

        Aufgabenbuch.Entscheid zweiter = abgeben(anna, "erster-stein");

        assertEquals(Aufgabenbuch.Bescheid.SCHON_ERFUELLT, zweiter.bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"), "nur die ersten 64 sind weg");
        assertEquals(nachErstem, kasse.guthaben(anna));
        assertEquals(1, staende.stand(insel.id()).stufe());
    }

    // ===================================================== Wer darf

    @Test
    @DisplayName("Ein MITBAUER darf abgeben - und bekommt den Lohn selbst")
    void mitbauerDarfAbgeben() {
        // Wer eine Insel zusammen aufbaut, soll auch zusammen abgeben koennen. Die
        // Belohnung bekommt, wer abgibt; die STUFE bekommt die Insel - das ist der
        // Unterschied, und er ist die Sache selbst.
        lager.mitbauerSetzen(insel.id(), java.util.Set.of(bert));
        rucksack.hinein("cobblestone", 64);
        long annasStand = kasse.guthaben(anna);

        Aufgabenbuch.Entscheid entscheid = abgeben(bert, "erster-stein");

        assertTrue(entscheid.erteilt());
        assertEquals(50L, kasse.guthaben(bert) - 1_000L, "Bert bekommt den Lohn");
        assertEquals(annasStand, kasse.guthaben(anna), "Anna nicht");
        assertEquals(1, staende.stand(insel.id()).stufe(), "die Stufe gehoert der Insel");
    }

    @Test
    @DisplayName("Auf einer FREMDEN Insel geht es nicht")
    void fremdeInsel() {
        rucksack.hinein("cobblestone", 64);

        Aufgabenbuch.Entscheid entscheid = abgeben(bert, "erster-stein");

        assertEquals(Aufgabenbuch.Bescheid.NICHT_BERECHTIGT, entscheid.bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"));
        assertEquals(0, staende.stand(insel.id()).stufe());
    }

    @Test
    @DisplayName("In der LEERE gibt es nichts abzugeben")
    void inDerLeere() {
        rucksack.hinein("cobblestone", 64);

        Aufgabenbuch.Entscheid entscheid = aufgaben.abgeben(WELT, anna,
                aufDerInsel(insel.x()) + 70, aufDerInsel(insel.z()), "erster-stein",
                rucksack);

        assertEquals(Aufgabenbuch.Bescheid.KEINE_INSEL, entscheid.bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"));
    }

    @Test
    @DisplayName("Die ZWEITE Insel desselben Spielers hat ihre eigene Stufe")
    void jedeInselEigeneStufe() {
        // Der Fortschritt gehoert der INSEL und nicht dem Spieler - und darum zaehlt der
        // Ort, auf dem er steht, und nicht sein Name.
        Parzelle zweite = buch.nehmen(WELT, anna, 2).insel().orElseThrow();
        rucksack.hinein("cobblestone", 128);
        abgeben(anna, "erster-stein");

        Aufgabenbuch.Entscheid aufDerZweiten = aufgaben.abgeben(WELT, anna,
                aufDerInsel(zweite.x()), aufDerInsel(zweite.z()), "erster-stein",
                rucksack);

        assertTrue(aufDerZweiten.erteilt(), "auf der zweiten Insel ist sie NEU");
        assertEquals(1, staende.stand(zweite.id()).stufe());
        assertEquals(1, staende.stand(insel.id()).stufe());
    }

    // ===================================================== Absagen

    @Test
    @DisplayName("Eine Aufgabe, die es nicht gibt")
    void unbekannteAufgabe() {
        Aufgabenbuch.Entscheid entscheid = abgeben(anna, "gibt-es-nicht");

        assertEquals(Aufgabenbuch.Bescheid.GIBT_ES_NICHT, entscheid.bescheid());
        assertTrue(entscheid.aufgabe().isEmpty());
    }

    @Test
    @DisplayName("Abgeschaltet sagt AUS - vor jeder anderen Pruefung")
    void abgeschaltet() {
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(Aufgabenwerte.VORGABE.mitAn(false))));
        rucksack.hinein("cobblestone", 64);

        assertEquals(Aufgabenbuch.Bescheid.AUS, abgeben(anna, "erster-stein").bescheid());
        assertEquals(Aufgabenbuch.Bescheid.AUS,
                aufgaben.tafel(WELT, aufDerInsel(insel.x()),
                        aufDerInsel(insel.z())).bescheid());
        assertEquals(64, rucksack.zaehle("cobblestone"));
    }

    @Test
    @DisplayName("Ohne Buch, Staende, Kasse, Stand oder Vorrat gibt es kein Aufgabenbuch")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenbuch(null, staende, kasse, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenbuch(buch, null, kasse, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenbuch(buch, staende, null, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Aufgabenbuch(buch, staende, kasse, null));
        assertThrows(IllegalArgumentException.class,
                () -> aufgaben.abgeben(WELT, anna, 0, 0, "erster-stein", null));
    }

    // ===================================================== Die Tafel

    @Test
    @DisplayName("Die Tafel nennt jede Aufgabe in der Reihenfolge der Datei")
    void tafelInReihenfolge() {
        rucksack.hinein("cobblestone", 64);
        abgeben(anna, "erster-stein");

        Aufgabenbuch.Tafel tafel = aufgaben.tafel(WELT, aufDerInsel(insel.x()),
                aufDerInsel(insel.z()));

        assertEquals(Aufgabenbuch.Bescheid.ERTEILT, tafel.bescheid());
        assertEquals(List.of("erster-stein", "kohlebrenner"),
                tafel.zeilen().stream().map(z -> z.aufgabe().schluessel()).toList());
        assertTrue(tafel.zeilen().get(0).erfuellt());
        assertFalse(tafel.zeilen().get(1).erfuellt());
        assertEquals(1, tafel.stufe());
    }

    @Test
    @DisplayName("Die Tafel zaehlt nur, was IN DER LISTE steht")
    void tafelZaehltNurDieListe() {
        // Der Unterschied zu Inselstand.stufe(), und er ist gewollt: Jene Zahl zaehlt
        // JEDE Zeile der Ablage - auch eine Aufgabe, die der Betreiber inzwischen
        // herausgenommen hat. Fuer die ERZAUSBEUTE ist das richtig: Ein Spieler verliert
        // seine Stufe nicht, weil eine Zeile aus der Datei verschwindet. In der Tafel
        // waere es falsch - dort stuende "3 von 2".
        staende.erfuellen(insel.id(), "gab-es-mal");
        staende.erfuellen(insel.id(), "und-noch-eins");

        Aufgabenbuch.Tafel tafel = aufgaben.tafel(WELT, aufDerInsel(insel.x()),
                aufDerInsel(insel.z()));

        assertEquals(0, tafel.stufe(), "in der Tafel zaehlen nur die zwei aus der Liste");
        assertEquals(2, staende.stand(insel.id()).stufe(),
                "fuer die Erzausbeute zaehlen beide weiter");
    }

    @Test
    @DisplayName("Die Tafel darf JEDER sehen - auch auf einer fremden Insel")
    void tafelDarfJeder() {
        // Was dort erfuellt ist, ist kein Geheimnis - und wer einem Freund hilft, soll
        // den Stand lesen koennen, ohne eingetragen zu sein.
        Aufgabenbuch.Tafel tafel = aufgaben.tafel(WELT, aufDerInsel(insel.x()),
                aufDerInsel(insel.z()));

        assertEquals(Aufgabenbuch.Bescheid.ERTEILT, tafel.bescheid());
        assertEquals(2, tafel.zeilen().size());
    }

    @Test
    @DisplayName("In der Leere gibt es keine Tafel")
    void tafelInDerLeere() {
        assertEquals(Aufgabenbuch.Bescheid.KEINE_INSEL,
                aufgaben.tafel(WELT, aufDerInsel(insel.x()) + 70,
                        aufDerInsel(insel.z())).bescheid());
    }

    @Test
    @DisplayName("Ein Neuladen wirkt - das Buch haelt den STAND, keine Kopie")
    void neuladenWirkt() {
        assertEquals(2, aufgaben.werte().liste().size());

        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitAufgaben(new Aufgabenwerte(true,
                        List.of(EINFACH)))));

        assertEquals(1, aufgaben.werte().liste().size());
        assertEquals(Aufgabenbuch.Bescheid.GIBT_ES_NICHT,
                abgeben(anna, "kohlebrenner").bescheid(),
                "die herausgenommene Aufgabe gibt es nicht mehr");
    }

    // ===================================================== Werkzeug

    private static Map<String, Integer> mengen(String ersterName, int erste,
                                               String zweiterName, int zweite) {
        Map<String, Integer> karte = new LinkedHashMap<>();
        karte.put(ersterName, erste);
        karte.put(zweiterName, zweite);
        return karte;
    }

    /**
     * Ein Vorrat als Karte - und er kann LUEGEN.
     *
     * <p>Das Luegen ist der Zweck: Auf einem Server zaehlt {@code getStorageContents} und
     * nimmt {@code removeItem}, und zwischen beiden liegt ein Augenblick, in dem ein
     * Spieler etwas wegwerfen kann. Ohne diese Attrappe waere der Zweig, der das
     * Genommene zurueckgibt, von keiner Probe durchlaufbar.
     */
    private static final class Kartenvorrat implements Vorrat {

        private final Map<String, Integer> inhalt = new LinkedHashMap<>();
        private String luegt;

        void hinein(String gegenstand, int stueck) {
            inhalt.merge(gegenstand, stueck, Integer::sum);
        }

        /** Dieser Gegenstand wird gezaehlt, laesst sich aber NICHT nehmen. */
        void luegeBeim(String gegenstand) {
            luegt = gegenstand;
        }

        @Override
        public int zaehle(String gegenstand) {
            return inhalt.getOrDefault(gegenstand, 0);
        }

        @Override
        public int nimm(String gegenstand, int stueck) {
            if (gegenstand.equals(luegt)) {
                return 0;
            }
            int da = zaehle(gegenstand);
            int weg = Math.min(da, stueck);
            inhalt.put(gegenstand, da - weg);
            return weg;
        }

        @Override
        public int lege(String gegenstand, int stueck) {
            hinein(gegenstand, stueck);
            return stueck;
        }

        /**
         * Eine Karte hat unbegrenzt Platz - und das Aufgabenbuch fragt nicht danach.
         *
         * <p>Es legt ausschliesslich zurueck, was es im selben Augenblick genommen hat;
         * der Platz dafuer ist durch das Nehmen selbst frei geworden. Gefragt wird diese
         * Zahl vom {@code Muenzhaus}, und dort hat sie ihre eigenen Proben.
         */
        @Override
        public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
            return 0;
        }
    }

    /**
     * Eine Ablage, die beim Erfuellen NEIN sagt oder WIRFT - sonst die echte.
     *
     * <p>Zwei Faelle, die im Betrieb vorkommen und beide mit demselben Ausgang enden
     * muessen: Der Wettlauf (der eindeutige Schluessel laesst die zweite Abgabe auflaufen)
     * und die stumme Datenbank. In beiden hat der Spieler schon abgegeben.
     */
    private record Nickablage(Wolkenablage echte, boolean wirft) implements Wolkenablage {

        @Override
        public Inselstand stand(long inselId) {
            return echte.stand(inselId);
        }

        @Override
        public List<Inselstand> alle() {
            return echte.alle();
        }

        @Override
        public void biomSetzen(long inselId, String biom) {
            echte.biomSetzen(inselId, biom);
        }

        @Override
        public void punkteSetzen(long inselId, long punkte, Instant wann) {
            echte.punkteSetzen(inselId, punkte, wann);
        }

        @Override
        public boolean erfuellen(long inselId, String schluessel) {
            if (wirft) {
                throw new IllegalStateException("Die Ablage schweigt");
            }
            return false;
        }

        @Override
        public void entfernen(long inselId) {
            echte.entfernen(inselId);
        }
    }
}
