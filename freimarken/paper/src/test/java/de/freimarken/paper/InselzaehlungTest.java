package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Punktebuch;
import de.freimarken.kern.Punktwerte;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherWolken;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkengrenze;
import de.freimarken.kern.Wolkenwerte;
import de.freimarken.kern.Zaehlauftrag;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Hand, die eine Insel zaehlt - der TAKT, nicht die Rechnung.
 *
 * <p>Geprueft wird genau das, was die Regel im Kern nicht kann: Dass die Zaehlung in
 * Happen laeuft, dass sie sich am Ende ABMELDET, dass sie bei einem Fehler EINMAL klagt
 * und die Insel freigibt - und dass sie nicht zweimal schreibt.
 *
 * <p>Die Taktquelle ist gestellt: Die Schlaege liegen in einer Liste, und die Probe loest
 * sie aus. Mit Bukkits Zeitgeber waere diese Probe eine, die drei Sekunden wartet.
 */
@DisplayName("Die Blockzaehlung einer Wolkeninsel")
class InselzaehlungTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final Instant JETZT = Instant.parse("2026-11-18T12:00:00Z");

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private final List<Runnable> schlaege = new ArrayList<>();
    private final List<String> gesehen = new ArrayList<>();

    private int abmeldungen;
    private SpeicherWolken staende;
    private Wirtschaftsstand wirtschaft;
    private Wolkenbuch buch;
    private Punktebuch punktebuch;
    private Logger protokoll;
    private long inselId;
    private Zaehlauftrag auftrag;

    @BeforeEach
    void vorher() {
        protokollzeilen.clear();
        schlaege.clear();
        gesehen.clear();
        abmeldungen = 0;
        protokoll = eigenesProtokoll();
        staende = new SpeicherWolken();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        SpeicherLager lager = new SpeicherLager();
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager,
                new SpeicherKasse(1_000L), wirtschaft);
        punktebuch = new Punktebuch(buch, staende, wirtschaft,
                Clock.fixed(JETZT, ZoneOffset.UTC));
        var insel = buch.nehmen(WELT, UUID.randomUUID(), 1).insel().orElseThrow();
        inselId = insel.id();
        auftrag = punktebuch.beginne(WELT, buch.plan().feldMitte(insel.x()),
                buch.plan().feldMitte(insel.z())).auftrag().orElseThrow();
    }

    @Test
    @DisplayName("Ein Chunk je Takt: 64 Schlaege, dann abgemeldet und geschrieben")
    void einChunkJeTakt() {
        // Die Happen sind der ganze Zweck dieser Hand: In einem Zug waere das eine
        // Sekunde Stillstand fuer alle.
        List<Long> gemeldet = new ArrayList<>();
        zaehlung((x, z) -> Map.of("diamond_block", 1L)).zaehle(auftrag, gemeldet::add);

        for (int takt = 0; takt < 63; takt++) {
            schlag();
            assertEquals(0, abmeldungen, "nach " + (takt + 1) + " Takten ist sie fertig,"
                    + " obwohl 64 Chunks zu zaehlen sind");
            assertEquals(List.of(), gemeldet);
        }
        schlag();

        assertEquals(1, abmeldungen, "die Hand meldet ihren Zeitgeber ab");
        assertEquals(List.of(64L * 900L), gemeldet,
                "64 Chunks mit je einem Diamantblock");
        assertEquals(64L * 900L, staende.stand(inselId).punkte());
        assertEquals(JETZT, staende.stand(inselId).gezaehltAm());
        assertFalse(punktebuch.laeuft(inselId), "und die Insel ist frei");
    }

    @Test
    @DisplayName("Die HAPPENGROESSE kommt aus den Punktwerten - 64 in EINEM Takt")
    void happengroesseAusDenWerten() {
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitPunkten(
                        Punktwerte.VORGABE.mitChunksJeTakt(64))));
        List<Long> gemeldet = new ArrayList<>();
        zaehlung((x, z) -> Map.of()).zaehle(auftrag, gemeldet::add);

        schlag();

        assertEquals(1, abmeldungen);
        assertEquals(List.of(0L), gemeldet);
        assertEquals(64, gesehen.size(), "alle 64 Chunks in einem Takt");
    }

    @Test
    @DisplayName("Jeder Chunk wird GENAU EINMAL gelesen")
    void jederChunkEinmal() {
        zaehlung((x, z) -> Map.of()).zaehle(auftrag, punkte -> { });

        while (abmeldungen == 0) {
            schlag();
        }

        assertEquals(64, gesehen.size());
        assertEquals(64, new java.util.LinkedHashSet<>(gesehen).size(),
                "ein doppelt gelesener Chunk zaehlte seine Bloecke zweimal");
    }

    @Test
    @DisplayName("Wirft die Chunkschau, wird EINMAL geklagt und freigegeben")
    void wurfWirdEinmalGeklagt() {
        // Eine Ausnahme in einem getakteten Auftrag meldet Bukkit NICHT ab - sie kaeme
        // bei jedem Tick wieder, und das Protokoll waere in Minuten voll. Ohne die
        // Freigabe waere die Insel ausserdem bis zum naechsten Neustart "in Zaehlung".
        List<Long> gemeldet = new ArrayList<>();
        zaehlung((x, z) -> {
            throw new IllegalStateException("Chunk nicht lesbar");
        }).zaehle(auftrag, gemeldet::add);

        schlag();
        schlag();

        assertEquals(1, klagen().size(), klagen().toString());
        assertTrue(klagen().get(0).contains("abgebrochen"), klagen().get(0));
        assertEquals(List.of(), gemeldet, "und nichts gemeldet");
        assertFalse(staende.stand(inselId).schonGezaehlt(), "und nichts geschrieben");
        assertFalse(punktebuch.laeuft(inselId), "aber die Insel ist frei");
    }

    @Test
    @DisplayName("Ein Schlag nach dem Ende schreibt NICHT zweimal")
    void keinZweitesSchreiben() {
        // Der Riegel gegen die doppelte Zeile: Ein Taktgeber, der sofort schlaegt, kommt
        // mit demselben Lauf ein zweites Mal.
        List<Long> gemeldet = new ArrayList<>();
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitPunkten(
                        Punktwerte.VORGABE.mitChunksJeTakt(64))));
        zaehlung((x, z) -> Map.of("glass", 1L)).zaehle(auftrag, gemeldet::add);

        schlag();
        schlag();
        schlag();

        assertEquals(1, gemeldet.size(), "nur EINE Meldung: " + gemeldet);
        assertEquals(64L * 2L, staende.stand(inselId).punkte());
    }

    @Test
    @DisplayName("Ohne Buch, Schau, Taktquelle oder Auftrag gibt es keine Zaehlung")
    void riegel() {
        Inselzaehlung.Chunkschau schau = (x, z) -> Map.of();
        Inselzaehlung.Taktquelle takt = (schlag, abstand) -> () -> { };

        assertThrows(IllegalArgumentException.class,
                () -> new Inselzaehlung(null, schau, takt, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselzaehlung(punktebuch, null, takt, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselzaehlung(punktebuch, schau, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselzaehlung(punktebuch, schau, takt, protokoll)
                        .zaehle(null, punkte -> { }));
    }

    // ===================================================== Werkzeug

    /** Eine Hand mit gestellter Taktquelle - die Schlaege loest die Probe aus. */
    private Inselzaehlung zaehlung(Inselzaehlung.Chunkschau schau) {
        return new Inselzaehlung(punktebuch, (chunkX, chunkZ) -> {
            gesehen.add(chunkX + "/" + chunkZ);
            return schau.zaehle(chunkX, chunkZ);
        }, (schlag, abstand) -> {
            schlaege.add(schlag);
            return () -> abmeldungen++;
        }, protokoll);
    }

    /** Ein Takt - alle gestellten Zeitgeber schlagen einmal. */
    private void schlag() {
        for (Runnable schlag : List.copyOf(schlaege)) {
            schlag.run();
        }
    }

    private List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("ZaehlungsProbe-" + UUID.randomUUID());
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
}
