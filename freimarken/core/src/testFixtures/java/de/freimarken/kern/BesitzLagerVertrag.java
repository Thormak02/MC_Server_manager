package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link BesitzLager} leisten muss.
 *
 * <p>Absichtlich als Vertrag und nicht als Test einer Klasse: Wenn die Datenbankfassung
 * dazukommt, erbt sie genau diese Pruefungen. Damit ist der Umstieg kein Vertrauensakt,
 * sondern ein gruener Testlauf - und ein Unterschied im Verhalten faellt sofort auf,
 * statt im Betrieb.
 */
abstract class BesitzLagerVertrag {

    protected static final String WELT = "freimarken:marktstadt";

    /** Ein leeres Lager der zu pruefenden Art. */
    protected abstract BesitzLager lager();

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    @Test
    @DisplayName("Eine freie Stelle ist leer, eine vergebene wird gefunden")
    void anlegenUndFinden() {
        BesitzLager lager = lager();
        assertTrue(lager.finde(WELT, 0, 0).isEmpty());

        Parzelle neu = lager.anlegen(WELT, 0, 0, anna, 0L);
        assertEquals(0, neu.x());
        assertEquals(0, neu.z());
        assertEquals(anna, neu.eigentuemer());
        assertTrue(neu.mitbauer().isEmpty());

        Parzelle gefunden = lager.finde(WELT, 0, 0).orElseThrow();
        assertEquals(neu.id(), gefunden.id());
        assertEquals(anna, gefunden.eigentuemer());
    }

    @Test
    @DisplayName("Dieselbe Stelle zweimal zu vergeben ist ein Fehler, kein Ueberschreiben")
    void keinStillesUeberschreiben() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 1, 1, anna, 0L);
        assertThrows(IllegalStateException.class, () -> lager.anlegen(WELT, 1, 1, bert, 0L));
        // Anna behaelt ihre Parzelle.
        assertEquals(anna, lager.finde(WELT, 1, 1).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Jede Welt hat ihr eigenes Raster")
    void weltenSindGetrennt() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 2, 2, anna, 0L);
        assertTrue(lager.finde("freimarken:freistaedte", 2, 2).isEmpty());
        assertEquals(0, lager.anzahlVon("freimarken:freistaedte", anna));
    }

    @Test
    @DisplayName("Parzellen eines Spielers werden gezaehlt und sortiert geliefert")
    void nachSpieler() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 5, 5, anna, 0L);
        lager.anlegen(WELT, 1, 9, anna, 25_000L);
        lager.anlegen(WELT, 3, 3, bert, 0L);

        assertEquals(2, lager.anzahlVon(WELT, anna));
        assertEquals(1, lager.anzahlVon(WELT, bert));

        List<Parzelle> annas = lager.vonSpieler(WELT, anna);
        assertEquals(2, annas.size());
        assertEquals(1, annas.get(0).x(), "nach X sortiert");
        assertEquals(5, annas.get(1).x());
    }

    @Test
    @DisplayName("Mitbauer werden gespeichert und wieder gelesen")
    void mitbauer() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 7, 7, anna, 0L);

        Parzelle mit = lager.mitbauerSetzen(parzelle.id(), Set.of(bert));
        assertEquals(Set.of(bert), mit.mitbauer());
        assertEquals(Set.of(bert), lager.finde(WELT, 7, 7).orElseThrow().mitbauer());
        assertTrue(mit.darfBauen(bert));

        Parzelle ohne = lager.mitbauerSetzen(parzelle.id(), Set.of());
        assertTrue(ohne.mitbauer().isEmpty());
        assertFalse(ohne.darfBauen(bert));
    }

    @Test
    @DisplayName("Eigentuemer und Preis bleiben beim Mitbauer-Setzen unangetastet")
    void mitbauerAendertSonstNichts() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 8, 8, anna, 50_000L);
        Parzelle danach = lager.mitbauerSetzen(parzelle.id(), Set.of(bert));

        assertEquals(parzelle.id(), danach.id());
        assertEquals(anna, danach.eigentuemer());
        assertEquals(50_000L, danach.preis());
        assertEquals(parzelle.erworbenAm(), danach.erworbenAm());
    }

    @Test
    @DisplayName("Entfernen gibt die Stelle wirklich frei")
    void entfernen() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 9, 9, anna, 0L);
        lager.entfernen(parzelle.id());

        assertTrue(lager.finde(WELT, 9, 9).isEmpty());
        assertEquals(0, lager.anzahlVon(WELT, anna));
        // Und danach kann sie jemand anders nehmen.
        assertEquals(bert, lager.anlegen(WELT, 9, 9, bert, 0L).eigentuemer());
    }

    @Test
    @DisplayName("Eine unbekannte Nummer zu entfernen ist kein Fehler")
    void entfernenVonNichts() {
        lager().entfernen(999_999L);
    }
}
