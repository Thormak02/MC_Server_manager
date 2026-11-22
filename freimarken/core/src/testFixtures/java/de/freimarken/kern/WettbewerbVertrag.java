package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Wettbewerbsablage} halten muss.
 *
 * <p><b>Hier liegen die ZWEI RIEGEL des ganzen Teils</b>, und darum ist dieser Vertrag
 * laenger als die anderen:
 * <ul>
 *   <li>Die ANMELDUNG gibt beim zweiten Mal {@code false}. Haelt sie nicht, kostet ein
 *       Doppelklick zweimal Startgeld.</li>
 *   <li>Der ABSCHLUSS gibt beim zweiten Mal {@code false}. Haelt er nicht, zahlt jeder
 *       Takt der Spielzeitwache denselben Topf erneut aus - eine Geldquelle, die mit
 *       jedem Takt waechst.</li>
 * </ul>
 * Beide sind EIN Zug und keine Lesung mit anschliessendem Schreiben; die eine Fassung
 * prueft eine HashMap, die andere die Zeilenzahl eines {@code INSERT IGNORE}, und genau
 * an so einer Stelle laufen zwei Umsetzungen auseinander.
 */
public abstract class WettbewerbVertrag {

    /** Ein Lauf, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate LAUF = LocalDate.of(2026, 11, 22);

    protected static final LocalDate GESTERN = LAUF.minusDays(1);

    /** Ein Zeitpunkt fuer den Abschluss - die Ablage entscheidet nichts damit. */
    protected static final Instant JETZT = Instant.parse("2026-11-22T19:00:00Z");

    /** Eine frische, leere Ablage. */
    protected abstract Wettbewerbsablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat keine Zeile - und das ist kein Fehler")
    void unbekannterHatNichts() {
        UUID anna = UUID.randomUUID();

        assertEquals(Optional.empty(), ablage().zeile(anna, LAUF));
        assertEquals(List.of(), ablage().zeilen(LAUF));
    }

    @Test
    @DisplayName("Die erste Anmeldung gibt true und legt die Zeile an")
    void ersteAnmeldung() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(a.meldeAn(anna, LAUF, 250L));

        Punktzeile zeile = a.zeile(anna, LAUF).orElseThrow();
        assertEquals(anna, zeile.spieler());
        assertEquals(LAUF, zeile.lauf());
        assertEquals(0, zeile.punkte(), "angemeldet heisst noch nicht gepunktet");
        assertEquals(250L, zeile.eingezahlt());
    }

    @Test
    @DisplayName("DER RIEGEL: die zweite Anmeldung gibt false und aendert nichts")
    void zweiteAnmeldungIstDerRiegel() {
        // Ohne diesen Riegel kostet ein Doppelklick zweimal Startgeld - der Aufrufer
        // bucht nur bei true ab.
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);
        a.zaehlePunkte(anna, LAUF, 7);

        assertFalse(a.meldeAn(anna, LAUF, 999L), "beim zweiten Mal false");

        Punktzeile zeile = a.zeile(anna, LAUF).orElseThrow();
        assertEquals(250L, zeile.eingezahlt(),
                "und die Einzahlung bleibt die ERSTE - sonst waere der Topf falsch");
        assertEquals(7, zeile.punkte(), "und die Punkte bleiben stehen");
    }

    @Test
    @DisplayName("Nur wer angemeldet ist, sammelt Punkte")
    void ohneAnmeldungKeinePunkte() {
        // Der Riegel dagegen, dass einem Angler ungefragt Startgeld abgebucht wird, nur
        // weil er im Fenster an einem Teich stand.
        Wettbewerbsablage a = ablage();
        UUID fremder = UUID.randomUUID();

        assertEquals(Optional.empty(), a.zaehlePunkte(fremder, LAUF, 5),
                "leer heisst: nicht angemeldet, also nichts gezaehlt");
        assertEquals(Optional.empty(), a.zeile(fremder, LAUF),
                "und es ist KEINE Zeile entstanden");
    }

    @Test
    @DisplayName("Punkte werden AUFSCHLAGEND gezaehlt")
    void punkteSchlagenAuf() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);

        assertEquals(Optional.of(5), a.zaehlePunkte(anna, LAUF, 5));
        assertEquals(Optional.of(8), a.zaehlePunkte(anna, LAUF, 3));
        assertEquals(Optional.of(108), a.zaehlePunkte(anna, LAUF, 100));

        assertEquals(108, a.zeile(anna, LAUF).orElseThrow().punkte(),
                "nicht 100 - aufgeschlagen, nicht gesetzt");
    }

    @Test
    @DisplayName("Null Punkte zaehlen ist erlaubt und aendert nichts")
    void nullPunkte() {
        // Erreichbar ueber punkte-je-fang: 0 mit einer Tabelle - das Buch filtert das
        // vorher ab, aber die Ablage soll nicht werfen.
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 0L);

        assertEquals(Optional.of(0), a.zaehlePunkte(anna, LAUF, 0));
    }

    @Test
    @DisplayName("Jeder LAUF hat seine eigenen Zeilen")
    void laeufeSindGetrennt() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, GESTERN, 100L);
        a.zaehlePunkte(anna, GESTERN, 50);
        a.meldeAn(anna, LAUF, 250L);
        a.zaehlePunkte(anna, LAUF, 7);

        assertEquals(50, a.zeile(anna, GESTERN).orElseThrow().punkte());
        assertEquals(7, a.zeile(anna, LAUF).orElseThrow().punkte());
        assertEquals(100L, a.zeile(anna, GESTERN).orElseThrow().eingezahlt());
        assertEquals(250L, a.zeile(anna, LAUF).orElseThrow().eingezahlt());
        assertEquals(1, a.zeilen(LAUF).size());
        assertEquals(1, a.zeilen(GESTERN).size());
    }

    @Test
    @DisplayName("Spieler und Lauf zusammen sind der Schluessel")
    void beideTeileDesSchluessels() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 10L);
        a.meldeAn(anna, GESTERN, 20L);
        a.meldeAn(bert, LAUF, 30L);
        a.meldeAn(bert, GESTERN, 40L);

        assertEquals(10L, a.zeile(anna, LAUF).orElseThrow().eingezahlt());
        assertEquals(20L, a.zeile(anna, GESTERN).orElseThrow().eingezahlt());
        assertEquals(30L, a.zeile(bert, LAUF).orElseThrow().eingezahlt());
        assertEquals(40L, a.zeile(bert, GESTERN).orElseThrow().eingezahlt());
    }

    @Test
    @DisplayName("Alle Zeilen eines Laufs kommen zurueck - mit ihren Einzahlungen")
    void alleZeilen() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID cora = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);
        a.meldeAn(bert, LAUF, 250L);
        a.meldeAn(cora, GESTERN, 250L);
        a.zaehlePunkte(anna, LAUF, 12);

        List<Punktzeile> zeilen = a.zeilen(LAUF);

        assertEquals(2, zeilen.size(), "cora gehoert zu gestern");
        long summe = 0L;
        int punkte = 0;
        for (Punktzeile zeile : zeilen) {
            summe += zeile.eingezahlt();
            punkte += zeile.punkte();
        }
        assertEquals(500L, summe, "der Topf kommt aus DIESEN Betraegen");
        assertEquals(12, punkte);
    }

    @Test
    @DisplayName("DER ZWEITE RIEGEL: ein Lauf wird nur EINMAL abgeschlossen")
    void abschlussIstDerZweiteRiegel() {
        // Der wichtigere der beiden. Ohne ihn zahlte jeder Takt der Spielzeitwache
        // denselben Topf erneut aus - eine Geldquelle, die mit jedem Takt waechst.
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);

        assertFalse(a.istAbgeschlossen(LAUF), "vorher nicht abgeschlossen");
        assertTrue(a.merkeAbgeschlossen(LAUF, JETZT), "beim ersten Mal true");
        assertTrue(a.istAbgeschlossen(LAUF));
        assertFalse(a.merkeAbgeschlossen(LAUF, JETZT), "beim zweiten Mal false");
        assertFalse(a.merkeAbgeschlossen(LAUF, JETZT.plusSeconds(60)),
                "und auch eine Minute spaeter nicht");
    }

    @Test
    @DisplayName("Jeder Lauf wird fuer sich abgeschlossen")
    void abschlussJeLauf() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);
        a.meldeAn(anna, GESTERN, 250L);

        assertTrue(a.merkeAbgeschlossen(GESTERN, JETZT));

        assertTrue(a.istAbgeschlossen(GESTERN));
        assertFalse(a.istAbgeschlossen(LAUF),
                "der heutige ist davon unberuehrt - sonst fiele ein Lauf aus, weil der"
                        + " von gestern gelaufen ist");
        assertTrue(a.merkeAbgeschlossen(LAUF, JETZT));
    }

    @Test
    @DisplayName("Ein Lauf OHNE Teilnehmer laesst sich abschliessen")
    void abschlussOhneTeilnehmer() {
        // Das Buch tut es nicht (es antwortet NIEMAND_DABEI), aber die Ablage soll
        // nicht werfen: Eine Umsetzung, die nur mit Teilnehmern kann, waere eine
        // verborgene Bedingung.
        Wettbewerbsablage a = ablage();

        assertTrue(a.merkeAbgeschlossen(LAUF, JETZT));
        assertTrue(a.istAbgeschlossen(LAUF));
    }

    @Test
    @DisplayName("Punkte nach dem Abschluss sind moeglich - die Ablage urteilt nicht")
    void punkteNachDemAbschluss() {
        // Die Entscheidung, dass nach dem Abschluss nichts mehr zaehlt, faellt am
        // FENSTER und nicht hier: Wettbewerbswerte.laufAm gibt dann keinen Lauf mehr,
        // und das Buch zaehlt nicht. Eine zweite Regel in der Ablage waere eine zweite
        // Wahrheit darueber, wann ein Lauf vorbei ist.
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);
        a.merkeAbgeschlossen(LAUF, JETZT);

        assertEquals(Optional.of(5), a.zaehlePunkte(anna, LAUF, 5));
    }

    @Test
    @DisplayName("Ein Startgeld von 0 ist erlaubt - die Teilnahme kann gratis sein")
    void gratisTeilnahme() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(a.meldeAn(anna, LAUF, 0L));

        assertEquals(0L, a.zeile(anna, LAUF).orElseThrow().eingezahlt());
        assertFalse(a.meldeAn(anna, LAUF, 0L),
                "und der Riegel haelt auch dann - sonst waere eine gratis Anmeldung"
                        + " beliebig oft moeglich, und jede zaehlte als Teilnehmer");
    }

    @Test
    @DisplayName("Viele Punkte in einem Lauf zaehlen sauber durch")
    void vielePunkte() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();
        a.meldeAn(anna, LAUF, 250L);

        for (int i = 1; i <= 100; i++) {
            assertEquals(Optional.of(i * 3), a.zaehlePunkte(anna, LAUF, 3),
                    "jeder Zug gibt genau seine Summe zurueck");
        }

        assertEquals(300, a.zeile(anna, LAUF).orElseThrow().punkte());
    }

    @Test
    @DisplayName("Ohne Spieler, Lauf oder Zeitpunkt geht nichts")
    void pflichtangaben() {
        Wettbewerbsablage a = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> a.meldeAn(null, LAUF, 0L));
        assertThrows(IllegalArgumentException.class, () -> a.meldeAn(anna, null, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> a.zaehlePunkte(null, LAUF, 1));
        assertThrows(IllegalArgumentException.class,
                () -> a.zaehlePunkte(anna, null, 1));
        assertThrows(IllegalArgumentException.class, () -> a.zeile(null, LAUF));
        assertThrows(IllegalArgumentException.class, () -> a.zeile(anna, null));
        assertThrows(IllegalArgumentException.class, () -> a.zeilen(null));
        assertThrows(IllegalArgumentException.class,
                () -> a.merkeAbgeschlossen(null, JETZT));
        assertThrows(IllegalArgumentException.class,
                () -> a.merkeAbgeschlossen(LAUF, null));
        assertThrows(IllegalArgumentException.class, () -> a.istAbgeschlossen(null));
    }
}
