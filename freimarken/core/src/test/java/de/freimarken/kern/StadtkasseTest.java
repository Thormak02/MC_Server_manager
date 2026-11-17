package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Geld einer Stadt - die Regeln von Teil E1, gemessen.
 *
 * <p>Zwei Festlegungen tragen diese Klasse, und beide sind von aussen unsichtbar,
 * solange niemand nachzaehlt. Die erste: Eine Stadtkasse braucht eine Kasse OHNE
 * Startguthaben, sonst bekommt jede Stadt bei ihrer ersten Einnahme einen Betrag
 * geschenkt, den niemand eingezahlt hat. Die zweite: Die Kontonummer kommt aus der
 * Stadtzeile und wird nicht aus ihrer {@code id} gerechnet - denn AUTO_INCREMENT
 * vergibt Nummern wieder.
 */
class StadtkasseTest {

    private static final String WELT = "freimarken:freistaedte";
    private static final UUID ANNA = UUID.randomUUID();

    /** Eine Stadt mit einer frisch gezogenen Kontonummer. */
    private static Parzelle stadt(long id, String name) {
        return new Parzelle(id, WELT, 3, 7, ANNA, Set.of(), 400_000L,
                Instant.parse("2026-01-01T00:00:00Z"), Art.FREISTADT,
                Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, name,
                Parzelle.KEINE_RECHTE, UUID.randomUUID(),
                Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT);
    }

    /** Eine gewoehnliche Parzelle - sie hat keine Kasse. */
    private static Parzelle parzelle() {
        return new Parzelle(9L, "freimarken:marktstadt", 0, 0, ANNA, Set.of(), 0L,
                Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV);
    }

    /** Die Kasse der Staedte: ohne Startguthaben, so wie das Plugin sie baut. */
    private static Stadtkasse stadtkasse() {
        return new Stadtkasse(new SpeicherKasse(0L));
    }

    // ------------------------------------- Der Riegel gegen geschenktes Geld

    @Test
    @DisplayName("Eine Kasse MIT Startguthaben wird abgewiesen - beim Bau, nicht spaeter")
    void startguthabenWirdAbgewiesen() {
        // DIE FALLE DIESES BAUSCHRITTS, und sie ist nicht zu sehen, wenn man nur die
        // Buchungen liest: Kasse.guthaben gibt fuer ein UNBEKANNTES Konto das
        // Startguthaben zurueck, und stelleKontoSicher legt jedes fehlende Konto mit
        // diesem Betrag an - gerufen nicht nur von eroeffne, sondern von JEDER Buchung.
        //
        // Mit der Spielerkasse (ab Werk 50.000 Mark) haette die erste Gutschrift an eine
        // Stadt ihr 50.000 Mark geschenkt. Bei 399 Staedten sind das knapp zwanzig
        // Millionen Mark aus dem Nichts - und jede einzelne Buchung haette dabei
        // richtig ausgesehen.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Stadtkasse(new SpeicherKasse(50_000L)));
        assertTrue(fehler.getMessage().contains("50000"), fehler.getMessage());
        assertTrue(fehler.getMessage().contains("Startguthaben"), fehler.getMessage());
    }

    @Test
    @DisplayName("Eine Kasse OHNE Startguthaben kommt durch")
    void ohneStartguthabenGehtDurch() {
        // Die Gegenrichtung, und sie ist noetig: Ein Riegel, der immer zuschlaegt, ist
        // gruen mit und ohne die Regel - und dieser hier wird im Konstruktor gerufen,
        // also bei jedem einzelnen Start des Servers.
        Stadtkasse kasse = stadtkasse();

        assertEquals(0L, kasse.stand(stadt(1L, "annastadt")),
                "und eine frische Stadt hat nichts");
    }

    @Test
    @DisplayName("Ohne Kasse gibt es keine Stadtkasse")
    void ohneKasseWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Stadtkasse(null));
    }

    // ------------------------------------- Die Kontonummer

    @Test
    @DisplayName("Das Konto kommt aus der STADTZEILE und nicht aus ihrer Nummer")
    void kontoKommtAusDerZeile() {
        // DIE ENTSCHEIDUNG, DIE DIESE PROBE FESTHAELT: Zwei Staedte mit DERSELBEN id
        // haben verschiedene Konten, wenn ihre Zeilen verschiedene Nummern tragen.
        //
        // Das ist genau der Fall, den eine aus der id gerechnete Nummer verloren haette:
        // AUTO_INCREMENT vergibt nach einem Neustart wieder, was hinten frei geworden
        // ist. Eine neu gegruendete Stadt haette dann das Konto einer entfernten
        // bekommen - Guthaben und Buchungsverlauf eingeschlossen, und niemand haette es
        // bemerkt, bis jemand den Verlauf liest.
        Parzelle alte = stadt(7L, "altstadt");
        Parzelle neue = stadt(7L, "neustadt");

        assertEquals(7L, alte.id());
        assertEquals(7L, neue.id(), "dieselbe Nummer - so vergibt AUTO_INCREMENT wieder");
        assertFalse(Stadtkasse.konto(alte).equals(Stadtkasse.konto(neue)),
                "und trotzdem verschiedene Konten. Waere die Kontonummer aus der id"
                        + " gerechnet, lieferte diese Zeile zweimal dasselbe Konto -"
                        + " und die neue Stadt erbte das Geld der alten.");
    }

    @Test
    @DisplayName("Dieselbe Zeile gibt immer dasselbe Konto")
    void dieselbeZeileDasselbeKonto() {
        // Die andere Haelfte: Die Nummer darf sich nicht bei jedem Blick aendern. Sonst
        // waere das Guthaben nach dem ersten Lesen unerreichbar.
        Parzelle eine = stadt(3L, "annastadt");

        assertEquals(Stadtkasse.konto(eine), Stadtkasse.konto(eine));
        assertEquals(eine.kasse(), Stadtkasse.konto(eine),
                "und es ist die Nummer AUS DER ZEILE, nicht eine daneben gerechnete");
    }

    @Test
    @DisplayName("Eine gewoehnliche Parzelle hat kein Konto - die Frage selbst wirft")
    void parzelleHatKeinKonto() {
        // Nicht "gibt null zurueck", sondern WIRFT: Ein null an dieser Stelle landete in
        // kasse.gutschreiben, und dort entstuende ein Konto unter der Kennung null -
        // ein Sammelkonto fuer alles, was keine Stadt ist.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> Stadtkasse.konto(parzelle()));
        assertTrue(fehler.getMessage().contains("Stadtkasse"), fehler.getMessage());

        assertThrows(IllegalArgumentException.class, () -> Stadtkasse.konto(null));
    }

    @Test
    @DisplayName("Jede Buchung fragt die Art - nicht nur konto() selbst")
    void jederWegFragtDieArt() {
        // Der Riegel sitzt in konto(), und jede der fuenf Methoden geht durch sie. Diese
        // Probe haelt das fest: Wer eine davon kuenftig an konto() vorbei baut, wird
        // hier rot. Ohne sie waere der Riegel an EINER Stelle geprueft und an vier
        // Stellen zu umgehen.
        Stadtkasse kasse = stadtkasse();
        Parzelle keine = parzelle();

        assertThrows(IllegalArgumentException.class, () -> kasse.stand(keine));
        assertThrows(IllegalArgumentException.class,
                () -> kasse.einzahlen(keine, 100L, Buchungsgrund.STADTKASSE_GEBUEHR));
        assertThrows(IllegalArgumentException.class,
                () -> kasse.abbuchen(keine, 100L, Buchungsgrund.STADTKASSE_ABGEHOBEN));
        assertThrows(IllegalArgumentException.class, () -> kasse.verlauf(keine, 10));
        assertThrows(IllegalArgumentException.class, () -> kasse.eroeffne(keine));
    }

    // ------------------------------------- Das Geld selbst

    @Test
    @DisplayName("Einzahlen, abheben, und was dazwischen im Buch steht")
    void geldFliesstUndStehtImBuch() {
        Stadtkasse kasse = stadtkasse();
        Parzelle annastadt = stadt(1L, "annastadt");

        kasse.einzahlen(annastadt, 2_500L, Buchungsgrund.STADTKASSE_GEBUEHR);
        assertEquals(2_500L, kasse.stand(annastadt));

        assertTrue(kasse.abbuchen(annastadt, 1_000L,
                Buchungsgrund.STADTKASSE_ABGEHOBEN));
        assertEquals(1_500L, kasse.stand(annastadt));

        // DER VERLAUF ist der Grund, aus dem die Stadtkasse ein richtiges Konto ist und
        // keine Zahl an der Stadtzeile: Ein Stadtbesitzer soll sehen, WOHER das Geld
        // kam. Die neueste Zeile zuerst, wie bei jedem Verlauf dieses Projekts.
        List<Buchung> verlauf = kasse.verlauf(annastadt, 10);
        assertEquals(Buchungsgrund.STADTKASSE_ABGEHOBEN, verlauf.get(0).grund());
        assertEquals(Buchungsgrund.STADTKASSE_GEBUEHR, verlauf.get(1).grund());
    }

    @Test
    @DisplayName("Mehr abheben als drin liegt geht NICHT - und hinterlaesst nichts")
    void ungedecktesAbhebenScheitert() {
        Stadtkasse kasse = stadtkasse();
        Parzelle annastadt = stadt(1L, "annastadt");
        kasse.einzahlen(annastadt, 500L, Buchungsgrund.STADTKASSE_GEBUEHR);

        // VORHER gezaehlt und nicht gegen eine feste Zahl geprueft: Im Verlauf steht
        // ausser der Einzahlung auch die EROEFFNUNG - das erste einzahlen() legt das
        // Konto an, und diese Zeile ueber 0 Mark gehoert dazu. Eine Probe, die 1
        // erwartet, prueft die Zahl der bisherigen Buchungen und nicht die Zusage.
        int vorher = kasse.verlauf(annastadt, 100).size();

        assertFalse(kasse.abbuchen(annastadt, 501L,
                Buchungsgrund.STADTKASSE_ABGEHOBEN));
        assertEquals(500L, kasse.stand(annastadt), "nichts ist geschehen");
        assertEquals(vorher, kasse.verlauf(annastadt, 100).size(),
                "und KEINE Zeile dazu - ein abgelehntes Abbuchen ist keine"
                        + " Geldbewegung. Gezaehlt werden Zeilen und nicht der Stand:"
                        + " Eine Zeile ueber 0 Mark liesse den Stand gleich.");
    }

    @Test
    @DisplayName("Zwei Staedte, zwei Kassen - das Geld der einen ist nicht das der anderen")
    void zweiStaedteZweiKassen() {
        // Klingt selbstverstaendlich und ist der Fall, in dem eine aus der id gerechnete
        // Nummer am teuersten gewesen waere. Hier wird gemessen, dass die Trennung
        // wirklich an der Nummer haengt.
        Stadtkasse kasse = stadtkasse();
        Parzelle annastadt = stadt(1L, "annastadt");
        Parzelle bertstadt = stadt(2L, "bertstadt");

        kasse.einzahlen(annastadt, 1_000L, Buchungsgrund.STADTKASSE_GEBUEHR);

        assertEquals(1_000L, kasse.stand(annastadt));
        assertEquals(0L, kasse.stand(bertstadt), "und bei Bert liegt nichts");
        assertTrue(kasse.verlauf(bertstadt, 10).isEmpty());
    }

    @Test
    @DisplayName("eroeffne legt das Konto an - und nur einmal")
    void eroeffnenNurEinmal() {
        Stadtkasse kasse = stadtkasse();
        Parzelle annastadt = stadt(1L, "annastadt");

        assertTrue(kasse.eroeffne(annastadt), "jetzt entstanden");
        assertFalse(kasse.eroeffne(annastadt), "und beim zweiten Mal nicht mehr");
        assertEquals(0L, kasse.stand(annastadt),
                "mit 0 Mark - das ist der ganze Zweck der Kasse ohne Startguthaben");
    }
}
