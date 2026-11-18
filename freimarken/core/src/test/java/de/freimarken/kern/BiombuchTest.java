package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Regel ueber den Biomwechsel - sie kostet, und zwar jedes Mal. */
@DisplayName("Die Regel ueber das Biom einer Wolkeninsel")
class BiombuchTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherKasse kasse;
    private SpeicherWolken staende;
    private Wirtschaftsstand wirtschaft;
    private Wolkenbuch buch;
    private Biombuch biome;
    private Parzelle insel;

    @BeforeEach
    void vorher() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(10_000L, UHR);
        staende = new SpeicherWolken();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse, wirtschaft);
        biome = new Biombuch(buch, staende, kasse, wirtschaft);
        insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
    }

    private int aufDerInsel(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Biombuch.Entscheid setze(UUID wer, String biom) {
        return biome.setze(WELT, wer, aufDerInsel(insel.x()), aufDerInsel(insel.z()),
                biom);
    }

    @Test
    @DisplayName("Das Biom wird gesetzt und bezahlt")
    void gesetztUndBezahlt() {
        long vorher = kasse.guthaben(anna);

        Biombuch.Entscheid entscheid = setze(anna, "swamp");

        assertTrue(entscheid.erteilt(), entscheid.toString());
        assertEquals(Wolkenwerte.VORGABE_BIOMPREIS, entscheid.kosten());
        assertEquals("swamp", staende.stand(insel.id()).biom());
        assertEquals(vorher - Wolkenwerte.VORGABE_BIOMPREIS, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("JEDER Wechsel kostet - auch der auf dasselbe Biom")
    void jederWechselKostet() {
        // DIE AUSDRUECKLICHE VORGABE, und sie hat zwei Gruende: Hinter dem Wechsel steht
        // das Umsetzen der Biomzellen eines ganzen Feldes, und ein Riegel "du hast dieses
        // Biom schon" waere eine Falle - nach einem abgebrochenen Umsetzen kaeme man nie
        // wieder an die Umsetzung heran.
        setze(anna, "swamp");
        long nachDemErsten = kasse.guthaben(anna);

        Biombuch.Entscheid zweiter = setze(anna, "swamp");

        assertTrue(zweiter.erteilt(), "dasselbe Biom ist erlaubt");
        assertEquals(nachDemErsten - Wolkenwerte.VORGABE_BIOMPREIS,
                kasse.guthaben(anna), "und kostet wieder");
    }

    @Test
    @DisplayName("Ein Biom, das nicht angeboten wird, kostet nichts")
    void nichtAngeboten() {
        Biombuch.Entscheid entscheid = setze(anna, "the_end");

        assertEquals(Biombuch.Bescheid.NICHT_ANGEBOTEN, entscheid.bescheid());
        assertEquals(10_000L, kasse.guthaben(anna));
        assertFalse(staende.stand(insel.id()).hatBiom());
    }

    @Test
    @DisplayName("Ohne Biomwahl geht es gar nicht")
    void keineWahl() {
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitBiomen(List.of())));

        assertEquals(Biombuch.Bescheid.KEINE_WAHL, setze(anna, "swamp").bescheid());
        assertEquals(10_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Nur der EIGENTUEMER - ein Mitbauer darf nicht")
    void nurDerEigentuemer() {
        // Anders als beim Abgeben einer Herausforderung: Ein Biomwechsel kostet Geld und
        // stellt die ganze Insel um. Ein Mitbauer duerfte damit das Spiel eines anderen
        // aendern - und zwar auf dessen Rechnung.
        lager.mitbauerSetzen(insel.id(), Set.of(bert));

        Biombuch.Entscheid entscheid = setze(bert, "swamp");

        assertEquals(Biombuch.Bescheid.NICHT_EIGENTUEMER, entscheid.bescheid());
        assertEquals(Wolkenwerte.VORGABE_BIOMPREIS, entscheid.kosten(),
                "der Preis steht im Entscheid, damit die Absage ihn nennen kann");
        assertFalse(staende.stand(insel.id()).hatBiom());
    }

    @Test
    @DisplayName("In der Leere gibt es kein Biom zu wechseln")
    void inDerLeere() {
        Biombuch.Entscheid entscheid = biome.setze(WELT, anna,
                aufDerInsel(insel.x()) + 70, aufDerInsel(insel.z()), "swamp");

        assertEquals(Biombuch.Bescheid.KEINE_INSEL, entscheid.bescheid());
        assertEquals(10_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Reicht das Geld nicht, bleibt das Biom - und der Preis steht dabei")
    void zuWenigMark() {
        SpeicherKasse arm = new SpeicherKasse(100L, UHR);
        Biombuch mitArmerKasse = new Biombuch(buch, staende, arm, wirtschaft);

        Biombuch.Entscheid entscheid = mitArmerKasse.setze(WELT, anna,
                aufDerInsel(insel.x()), aufDerInsel(insel.z()), "swamp");

        assertEquals(Biombuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(Wolkenwerte.VORGABE_BIOMPREIS, entscheid.kosten());
        assertEquals(100L, arm.guthaben(anna), "nichts abgebucht");
        assertFalse(staende.stand(insel.id()).hatBiom());
    }

    @Test
    @DisplayName("Schweigt die Ablage NACH dem Abbuchen, kommt der Preis zurueck")
    void gestoertErstattet() {
        // Der Fall, der ohne Erstattung Geld verschluckt: Die Kasse hat gebucht, die
        // Ablage nicht geschrieben. Zwei Zeilen im Kontobuch sind besser als ein Spieler,
        // der fuer nichts bezahlt hat.
        Biombuch mitWurf = new Biombuch(buch, new Stummablage(staende), kasse,
                wirtschaft);

        Biombuch.Entscheid entscheid = mitWurf.setze(WELT, anna,
                aufDerInsel(insel.x()), aufDerInsel(insel.z()), "swamp");

        assertEquals(Biombuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(10_000L, kasse.guthaben(anna), "alles zurueck");
        assertEquals(1, kasse.verlauf(anna, 10).stream()
                        .filter(zeile -> zeile.grund()
                                == Buchungsgrund.WOLKE_BIOM_ERSTATTET)
                        .count(),
                "und die Erstattung hat ihren eigenen Grund: " + kasse.verlauf(anna, 10));
    }

    @Test
    @DisplayName("Ein Biom wird kleingeschrieben gesucht und gesetzt")
    void biomWirdNormalisiert() {
        assertTrue(setze(anna, "  SWAMP  ").erteilt());
        assertEquals("swamp", staende.stand(insel.id()).biom());
    }

    @Test
    @DisplayName("Ohne gewaehltes Biom gilt die VORGABE des Programms")
    void ohneWahlGiltDieVorgabe() {
        assertEquals(Wolkenbiome.VORGABE, biome.biomVon(insel.id()));

        setze(anna, "taiga");

        assertEquals("taiga", biome.biomVon(insel.id()));
    }

    @Test
    @DisplayName("Ohne Buch, Staende, Kasse oder Stand gibt es kein Biombuch")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Biombuch(null, staende, kasse, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Biombuch(buch, null, kasse, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Biombuch(buch, staende, null, wirtschaft));
        assertThrows(IllegalArgumentException.class,
                () -> new Biombuch(buch, staende, kasse, null));
    }

    /** Eine Ablage, die beim Biomsetzen WIRFT - sonst die echte. */
    private record Stummablage(Wolkenablage echte) implements Wolkenablage {

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
            throw new IllegalStateException("Die Ablage schweigt");
        }

        @Override
        public void punkteSetzen(long inselId, long punkte, Instant wann) {
            echte.punkteSetzen(inselId, punkte, wann);
        }

        @Override
        public boolean erfuellen(long inselId, String schluessel) {
            return echte.erfuellen(inselId, schluessel);
        }

        @Override
        public void entfernen(long inselId) {
            echte.entfernen(inselId);
        }
    }
}
