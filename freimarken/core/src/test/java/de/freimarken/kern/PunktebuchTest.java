package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel ueber die Punktzahl und die Rangliste.
 *
 * <p>Die Uhr ist gestellt, und darum laeuft die Abklingzeit hier ohne Warten durch:
 * {@code Clock.fixed} und dann eine zweite Uhr mit einem spaeteren Zeitpunkt - dieselbe
 * Linie wie bei den Mietlaeufen.
 */
@DisplayName("Die Regel ueber die Punkte einer Wolkeninsel")
class PunktebuchTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final Instant JETZT = Instant.parse("2026-11-18T12:00:00Z");

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherWolken staende;
    private Wirtschaftsstand wirtschaft;
    private Wolkenbuch buch;
    private Punktebuch punkte;
    private Parzelle insel;

    @BeforeEach
    void vorher() {
        lager = new SpeicherLager();
        staende = new SpeicherWolken();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager,
                new SpeicherKasse(1_000L), wirtschaft);
        punkte = buchMitUhr(JETZT);
        insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
    }

    private Punktebuch buchMitUhr(Instant wann) {
        return new Punktebuch(buch, staende, wirtschaft,
                Clock.fixed(wann, ZoneOffset.UTC));
    }

    private int aufDerInsel(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Punktebuch.Entscheid beginne() {
        return punkte.beginne(WELT, aufDerInsel(insel.x()), aufDerInsel(insel.z()));
    }

    // ===================================================== Das Zaehlen

    @Test
    @DisplayName("Eine Zaehlung faengt an - mit dem Auftrag fuer DIESES Feld")
    void zaehlungFaengtAn() {
        Punktebuch.Entscheid entscheid = beginne();

        assertTrue(entscheid.angefangen(), entscheid.toString());
        assertEquals(insel.id(), entscheid.auftrag().orElseThrow().inselId());
        assertEquals(64, entscheid.auftrag().orElseThrow().chunkzahl());
        assertTrue(punkte.laeuft(insel.id()));
    }

    @Test
    @DisplayName("Auf derselben Insel faengt keine ZWEITE Zaehlung an")
    void keineZweiteZaehlung() {
        // Zwei Zaehlungen auf einem Feld haetten zwei Teilsummen, und die zweite
        // ueberschriebe die erste. Darum gehoert die Menge der laufenden Auftraege dem
        // Buch und nicht der Hand, die taktet.
        beginne();

        assertEquals(Punktebuch.Bescheid.LAEUFT_SCHON, beginne().bescheid());
    }

    @Test
    @DisplayName("Nach dem Fertigschreiben steht die Punktzahl mit ihrem Zeitpunkt")
    void fertigSchreibt() {
        Zaehlauftrag auftrag = beginne().auftrag().orElseThrow();
        auftrag.nimm(Map.of("diamond_block", 2L));

        long geschrieben = punkte.fertig(auftrag);

        assertEquals(2L * 900L, geschrieben, "zwei Diamantbloecke der Vorgabetabelle");
        assertEquals(2L * 900L, staende.stand(insel.id()).punkte());
        assertEquals(JETZT, staende.stand(insel.id()).gezaehltAm());
        assertFalse(punkte.laeuft(insel.id()), "und die Insel ist wieder frei");
    }

    @Test
    @DisplayName("Ein Abbruch gibt die Insel frei, ohne zu schreiben")
    void abbruchGibtFrei() {
        Zaehlauftrag auftrag = beginne().auftrag().orElseThrow();
        auftrag.nimm(Map.of("diamond_block", 2L));

        punkte.abbrechen(auftrag);

        assertFalse(punkte.laeuft(insel.id()));
        assertEquals(0L, staende.stand(insel.id()).punkte());
        assertFalse(staende.stand(insel.id()).schonGezaehlt());
    }

    @Test
    @DisplayName("Die Abklingzeit haengt an der INSEL und laeuft ab")
    void abklingzeit() {
        // An der Insel und nicht am Spieler: Gezaehlt wird ein Feld, und zehn Mitbauer
        // koennten es sonst zehnmal hintereinander zaehlen lassen.
        punkte.fertig(beginne().auftrag().orElseThrow());

        assertEquals(Punktwerte.VORGABE_ABKLINGZEIT, punkte.wartesekunden(insel.id()));
        Punktebuch.Entscheid zuFrueh = beginne();
        assertEquals(Punktebuch.Bescheid.ZU_FRUEH, zuFrueh.bescheid());
        assertEquals(Punktwerte.VORGABE_ABKLINGZEIT, zuFrueh.wartesekunden());

        Punktebuch spaeter = buchMitUhr(JETZT.plus(
                Duration.ofSeconds(Punktwerte.VORGABE_ABKLINGZEIT)));
        assertEquals(0L, spaeter.wartesekunden(insel.id()));
        assertTrue(spaeter.beginne(WELT, aufDerInsel(insel.x()),
                aufDerInsel(insel.z())).angefangen());
    }

    @Test
    @DisplayName("Ohne Tabelle und ohne Insel wird nicht gezaehlt")
    void absagen() {
        assertEquals(Punktebuch.Bescheid.KEINE_INSEL,
                punkte.beginne(WELT, aufDerInsel(insel.x()) + 70,
                        aufDerInsel(insel.z())).bescheid());

        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitPunkten(
                        Punktwerte.VORGABE.mitTabelle(Map.of()))));
        assertEquals(Punktebuch.Bescheid.AUS, beginne().bescheid());
    }

    @Test
    @DisplayName("Ohne Buch, Staende, Stand oder Uhr gibt es kein Punktebuch")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Punktebuch(null, staende, wirtschaft, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktebuch(buch, null, wirtschaft, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktebuch(buch, staende, null, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktebuch(buch, staende, wirtschaft, null));
        assertThrows(IllegalArgumentException.class, () -> punkte.fertig(null));
        assertThrows(IllegalArgumentException.class, () -> punkte.rangliste(WELT, 0));
    }

    // ===================================================== Die Rangliste

    @Test
    @DisplayName("Die Rangliste sortiert nach Punkten - und nennt die Stufe daneben")
    void ranglisteSortiert() {
        Parzelle berts = buch.nehmen(WELT, bert, 1).insel().orElseThrow();
        staende.punkteSetzen(insel.id(), 100L, JETZT);
        staende.punkteSetzen(berts.id(), 500L, JETZT);
        staende.erfuellen(berts.id(), "erster-stein");

        List<Punktebuch.Platz> liste = punkte.rangliste(WELT, 10);

        assertEquals(2, liste.size());
        assertEquals(1, liste.get(0).rang());
        assertEquals(berts.id(), liste.get(0).insel().id());
        assertEquals(500L, liste.get(0).punkte());
        assertEquals(1, liste.get(0).stufe(), "die Stufe steht neben den Punkten");
        assertEquals(2, liste.get(1).rang());
        assertEquals(100L, liste.get(1).punkte());
    }

    @Test
    @DisplayName("Bei Gleichstand gewinnt die AELTERE Insel")
    void gleichstandNachAlter() {
        // Ohne zweiten Schluessel waere die Reihenfolge bei zwei Inseln mit 0 Punkten dem
        // Zufall der Ablage ueberlassen - und die Liste saehe bei jedem Aufruf anders aus.
        Parzelle zweite = buch.nehmen(WELT, bert, 1).insel().orElseThrow();

        List<Punktebuch.Platz> liste = punkte.rangliste(WELT, 10);

        assertEquals(insel.id(), liste.get(0).insel().id());
        assertEquals(zweite.id(), liste.get(1).insel().id());
        assertEquals(liste, punkte.rangliste(WELT, 10), "und das bleibt so");
    }

    @Test
    @DisplayName("Die Liste wird GEKUERZT - zehn Plaetze sind ein Chatfenster")
    void listeWirdGekuerzt() {
        for (int i = 0; i < 4; i++) {
            buch.nehmen(WELT, UUID.randomUUID(), 1);
        }

        assertEquals(2, punkte.rangliste(WELT, 2).size());
        assertEquals(5, punkte.rangliste(WELT, 10).size());
    }

    @Test
    @DisplayName("Ohne Insel ist die Liste leer und kein Fehler")
    void leereListe() {
        assertEquals(List.of(), punkte.rangliste("freimarken:andere", 10));
    }
}
