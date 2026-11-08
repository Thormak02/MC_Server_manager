package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Mietablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code BonusVertrag}: Die eine sucht den Mieter in einer Schleife, die
 * andere hat einen eindeutigen Schluessel auf der Spalte - und genau an so einer Stelle
 * laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Das zweite Versprechen ist der Kern dieses Vertrags</b>: ein Laden je Spieler, und
 * gewacht von der Ablage. Die Proben dazu sind {@link #zweiterLadenGehtNicht},
 * {@link #dieselbeEinheitGehtImmer} und {@link #nachDemKuendigenWiederFrei} - sie sagen
 * zusammen, dass die Sperre greift, dass sie das Verlaengern nicht trifft und dass sie
 * sich wieder loest.
 */
public abstract class MietVertrag {

    /** Ein Tag, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    protected static final LocalDate IN_DREISSIG = HEUTE.plusDays(29);

    /** Eine frische, leere Ablage. */
    protected abstract Mietablage ablage();

    @Test
    @DisplayName("Eine freie Einheit und ein Spieler ohne Laden sind null, kein Fehler")
    void leerIstKeinFehler() {
        Mietablage ablage = ablage();

        assertNull(ablage.fuerEinheit("stand-1"));
        assertNull(ablage.fuerMieter(UUID.randomUUID()));
        assertTrue(ablage.alle().isEmpty());
    }

    @Test
    @DisplayName("Was geschrieben wurde, kommt zurueck - alle sechs Werte")
    void hinUndZurueck() {
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // Sechs verschiedene Werte: Beginn und Ende sind nicht derselbe Tag, und Miete
        // und Kaution sind keine der ueblichen Vorgaben und auch nicht gleich. Bei
        // gleichen Werten waere ein Vertauschen zweier Spalten nicht zu sehen - und
        // miete und kaution liegen in der Zeile nebeneinander.
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 777L, 4_321L));

        Mietvertrag vertrag = ablage.fuerEinheit("stand-1");
        assertNotNull(vertrag);
        assertEquals("stand-1", vertrag.einheit());
        assertEquals(anna, vertrag.mieter());
        assertEquals(HEUTE, vertrag.beginn());
        assertEquals(IN_DREISSIG, vertrag.laeuftBis());
        assertEquals(777L, vertrag.miete());
        assertEquals(4_321L, vertrag.kaution());
    }

    @Test
    @DisplayName("Der Vertrag ist auch ueber den MIETER zu finden")
    void ueberDenMieter() {
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));
        ablage.schreibe(new Mietvertrag("stand-2", bert, HEUTE, IN_DREISSIG, 500L, 5_000L));

        // Beide Richtungen und ZWEI Mieter: Mit nur einem waere eine Fassung gruen, die
        // immer den ersten Vertrag liefert.
        assertEquals("stand-1", ablage.fuerMieter(anna).einheit());
        assertEquals("stand-2", ablage.fuerMieter(bert).einheit());
        assertEquals(anna, ablage.fuerEinheit("stand-1").mieter());
        assertEquals(bert, ablage.fuerEinheit("stand-2").mieter());
    }

    @Test
    @DisplayName("Ein ZWEITER Laden fuer denselben Spieler wird abgewiesen")
    void zweiterLadenGehtNicht() {
        // Plan 7.1: "Ein Laden je Spieler, damit nicht drei Leute die Halle besitzen."
        // Gewacht von der ABLAGE und nicht vom Befehl - zwei gleichzeitig abgeschickte
        // Fenster bekaemen von einem Befehl, der erst fragt und dann schreibt, beide ein
        // Ja.
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertThrows(IllegalStateException.class, () -> ablage.schreibe(
                new Mietvertrag("stand-2", anna, HEUTE, IN_DREISSIG, 500L, 5_000L)));

        // Und der erste Vertrag steht unveraendert da: Ein abgewiesener Schreibzug darf
        // nichts halb erledigt haben.
        assertEquals("stand-1", ablage.fuerMieter(anna).einheit());
        assertNull(ablage.fuerEinheit("stand-2"));
        assertEquals(1, ablage.alle().size());
    }

    @Test
    @DisplayName("Die EIGENE Einheit darf immer wieder geschrieben werden - verlaengern")
    void dieselbeEinheitGehtImmer() {
        // Die Gegenprobe zur Sperre: Verlaengern ist ein zweiter Schreibzug auf dieselbe
        // Einheit durch denselben Mieter, und eine Sperre, die nur "schon ein Vertrag?"
        // fragt, wuerde genau das abweisen. Dann koennte niemand verlaengern.
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        LocalDate spaeter = IN_DREISSIG.plusDays(30);
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, spaeter, 500L, 5_000L));

        assertEquals(spaeter, ablage.fuerEinheit("stand-1").laeuftBis());
        assertEquals(1, ablage.alle().size(), "gesetzt und nicht ein zweites Mal angelegt");
    }

    @Test
    @DisplayName("Nach dem Kuendigen darf derselbe Spieler woanders mieten")
    void nachDemKuendigenWiederFrei() {
        // Die Sperre muss sich LOESEN. Eine Fassung, die den Spieler dauerhaft merkt,
        // waere bei den beiden Proben darueber gruen und wuerde jeden Spieler, der einmal
        // einen Laden hatte, fuer immer aussperren.
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertTrue(ablage.loesche("stand-1"));
        ablage.schreibe(new Mietvertrag("stand-2", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertEquals("stand-2", ablage.fuerMieter(anna).einheit());
        assertNull(ablage.fuerEinheit("stand-1"));
    }

    @Test
    @DisplayName("Ein zweiter Spieler kann dieselbe Einheit nicht uebernehmen")
    void einheitIstBelegt() {
        // Der Schluessel ist die Einheit, und ein zweiter Schreibzug SETZT. Dass daraus
        // keine stille Uebernahme wird, besorgt der Befehl - hier steht nur, was die
        // Ablage tut: Sie haelt genau EINEN Vertrag je Einheit, nie zwei.
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        ablage.schreibe(new Mietvertrag("stand-1", bert, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertEquals(1, ablage.alle().size());
        assertEquals(bert, ablage.fuerEinheit("stand-1").mieter());
        assertNull(ablage.fuerMieter(anna), "Anna hat keinen Laden mehr");
    }

    @Test
    @DisplayName("Gross- und Kleinschreibung sind derselbe Laden")
    void grossUndKleinGleich() {
        // Die Einheitennamen kommen aus der config.yml, und dort tippt ein Mensch. Ohne
        // diese Zusage waere "Stand-1" ein zweiter, freier Laden an derselben Stelle.
        Mietablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(new Mietvertrag("Stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertNotNull(ablage.fuerEinheit("stand-1"));
        assertNotNull(ablage.fuerEinheit("STAND-1"));
        assertEquals("stand-1", ablage.alle().get(0).einheit());
    }

    @Test
    @DisplayName("alle() liefert zweimal hintereinander dieselbe Reihenfolge")
    void reihenfolgeIstStabil() {
        // Das dritte Versprechen. Der taegliche Lauf aus Teil C bucht Geld ab; nach einem
        // Absturz mitten im Lauf ist ohne stabile Reihenfolge nicht zu sagen, wer schon
        // gezahlt hat. Angelegt wird in UMGEKEHRTER Ordnung, damit eine Fassung, die
        // einfach die Einfuegereihenfolge liefert, auffaellt.
        Mietablage ablage = ablage();
        ablage.schreibe(new Mietvertrag("stand-3", UUID.randomUUID(), HEUTE,
                IN_DREISSIG, 500L, 5_000L));
        ablage.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE,
                IN_DREISSIG, 500L, 5_000L));
        ablage.schreibe(new Mietvertrag("stand-2", UUID.randomUUID(), HEUTE,
                IN_DREISSIG, 500L, 5_000L));

        List<String> ersteRunde = ablage.alle().stream().map(Mietvertrag::einheit).toList();
        List<String> zweiteRunde = ablage.alle().stream().map(Mietvertrag::einheit).toList();

        assertEquals(List.of("stand-1", "stand-2", "stand-3"), ersteRunde);
        assertEquals(ersteRunde, zweiteRunde);
    }

    @Test
    @DisplayName("Loeschen meldet, ob es etwas gab - und das zweite Mal wirft es nicht")
    void loeschenMeldetUndWiederholtSich() {
        Mietablage ablage = ablage();
        ablage.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE,
                IN_DREISSIG, 500L, 5_000L));

        assertTrue(ablage.loesche("stand-1"));
        assertFalse(ablage.loesche("stand-1"), "zweimal angekommen ist kein Fehler");
        assertFalse(ablage.loesche("gibt-es-nicht"));
        assertNull(ablage.fuerEinheit("stand-1"));
    }

    @Test
    @DisplayName("Die TAGE kommen unveraendert zurueck - keine Zone verschiebt sie")
    void tageWandernNicht() {
        // Dieselbe Falle wie beim BonusVertrag: Ein java.sql.Date ist ein
        // Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber nach UTC umrechnet -
        // auf einem Rechner in Berlin waere das 23:00 des Vortags. Geprueft mit zwei
        // Monatsanfaengen, weil dort ein Versatz um einen Tag auch den Monat dreht; und
        // mit ZWEI verschiedenen Tagen, damit ein Vertauschen von Beginn und Ende
        // auffaellt.
        Mietablage ablage = ablage();
        LocalDate maerzanfang = LocalDate.of(2026, 3, 1);
        LocalDate aprilanfang = LocalDate.of(2026, 4, 1);

        ablage.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), maerzanfang,
                aprilanfang, 500L, 5_000L));

        Mietvertrag vertrag = ablage.fuerEinheit("stand-1");
        assertEquals(maerzanfang, vertrag.beginn());
        assertEquals(aprilanfang, vertrag.laeuftBis());
    }

    @Test
    @DisplayName("Eine hohe Miete passt in die Zeile")
    void hoheMiete() {
        // Ein BIGINT in der Spalte. Geprueft wird der erlaubte Rand, damit ein zu kleiner
        // Spaltentyp nicht erst auffaellt, wenn ein Betreiber die Miete hochsetzt.
        Mietablage ablage = ablage();

        ablage.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                Mietwerte.HOECHSTER_BETRAG, 5_000L));

        assertEquals(Mietwerte.HOECHSTER_BETRAG, ablage.fuerEinheit("stand-1").miete());
    }

    @Test
    @DisplayName("Eine Kaution von 0 kommt als 0 zurueck")
    void keineKautionBleibtKeine() {
        // "Ohne Kaution gemietet" muss die Zeile ueberleben: Beim Kuendigen entscheidet
        // genau diese Zahl, was zurueckgeht. Eine Fassung, die 0 als "nicht gesetzt"
        // behandelt und die Vorgabe einsetzt, schenkte dem Spieler 5.000 Mark.
        Mietablage ablage = ablage();

        ablage.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                500L, 0L));

        Mietvertrag vertrag = ablage.fuerEinheit("stand-1");
        assertEquals(0L, vertrag.kaution());
        assertFalse(vertrag.mitKaution());
    }

    @Test
    @DisplayName("Ohne Einheit, ohne Spieler und ohne Vertrag geht nichts")
    void pflichtangaben() {
        Mietablage ablage = ablage();

        assertThrows(IllegalArgumentException.class, () -> ablage.fuerEinheit(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.fuerEinheit("  "));
        assertThrows(IllegalArgumentException.class, () -> ablage.fuerMieter(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.schreibe(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.loesche(null));
    }
}
