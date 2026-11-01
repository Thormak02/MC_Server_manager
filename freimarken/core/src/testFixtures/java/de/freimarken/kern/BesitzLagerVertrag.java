package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
public abstract class BesitzLagerVertrag {

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

        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));
        Parzelle mit = lager.finde(WELT, 7, 7).orElseThrow();
        assertEquals(Set.of(bert), mit.mitbauer());
        assertTrue(mit.darfBauen(bert));

        lager.mitbauerSetzen(parzelle.id(), Set.of());
        Parzelle ohne = lager.finde(WELT, 7, 7).orElseThrow();
        assertTrue(ohne.mitbauer().isEmpty());
        assertFalse(ohne.darfBauen(bert));
    }

    @Test
    @DisplayName("Eigentuemer und Preis bleiben beim Mitbauer-Setzen unangetastet")
    void mitbauerAendertSonstNichts() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 8, 8, anna, 50_000L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));
        Parzelle danach = lager.finde(WELT, 8, 8).orElseThrow();

        assertEquals(parzelle.id(), danach.id());
        assertEquals(anna, danach.eigentuemer());
        assertEquals(50_000L, danach.preis());
        assertEquals(parzelle.erworbenAm(), danach.erworbenAm());
        // Diese drei sind der eigentliche Grund, warum es diese Pruefung gibt: Eine
        // Umsetzung, die hier feste Werte einsetzt statt die alten durchzutragen, loescht
        // bei jeder Mitbauer-Aenderung still den Gruppenbezug - und zwar nur im Betrieb,
        // wo es keiner sieht.
        assertEquals(parzelle.art(), danach.art());
        assertEquals(parzelle.elternId(), danach.elternId());
        assertEquals(parzelle.zustand(), danach.zustand());
    }

    @Test
    @DisplayName("Anlegen erzeugt eigenstaendigen, laufenden Besitz auf dem Raster")
    void anlegenErzeugtEigenstaendigeParzelle() {
        BesitzLager lager = lager();
        Parzelle neu = lager.anlegen(WELT, 3, 4, anna, 0L);

        assertEquals(Art.PARZELLE, neu.art());
        assertEquals(Zustand.AKTIV, neu.zustand());
        assertEquals(Parzelle.KEIN_ELTERNTEIL, neu.elternId());
        assertFalse(neu.hatElternteil());
        assertTrue(neu.darfBauen(anna));
    }

    @Test
    @DisplayName("Gezaehlt wird genau das, was auch aufgelistet wird")
    void zaehlenUndAuflistenStimmenUeberein() {
        // Die Zahl aus anzahlVon geht in die Preisstaffel und in die Rangobergrenze, die
        // Liste aus vonSpieler in die Anzeige. Laufen sie auseinander, zahlt der Spieler
        // fuer etwas anderes, als er sieht.
        BesitzLager lager = lager();
        lager.anlegen(WELT, 1, 1, anna, 0L);
        lager.anlegen(WELT, 2, 2, anna, 25_000L);
        lager.anlegen(WELT, 3, 3, bert, 0L);

        assertEquals(lager.vonSpieler(WELT, anna).size(), lager.anzahlVon(WELT, anna));
        assertEquals(lager.vonSpieler(WELT, bert).size(), lager.anzahlVon(WELT, bert));
        assertEquals(2, lager.anzahlVon(WELT, anna));
    }

    // --- Gruppen ----------------------------------------------------------------

    @Test
    @DisplayName("Eine neue Gruppe nimmt ihre Felder auf und bekommt eine eigene Nummer")
    void gruppeAnlegen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);

        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        assertNotEquals(Parzelle.KEIN_ELTERNTEIL, gruppe);
        assertEquals(2, lager.kinderVon(gruppe).size());
        assertEquals(gruppe, lager.finde(WELT, 1, 0).orElseThrow().elternId());
        assertEquals(gruppe, lager.finde(WELT, 2, 0).orElseThrow().elternId());
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Die Felder behalten beim Zusammenlegen Eigentuemer, Preis und Lage")
    void gruppeAendertDieFelderNichtSonst() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);

        lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(eins.id(), zwei.id()));

        // Jede Zeile traegt ihren Eigentuemer weiter selbst - nur so bleibt darfBauen
        // eine Abfrage ohne Umweg ueber die Gruppe.
        Parzelle danach = lager.finde(WELT, 2, 0).orElseThrow();
        assertEquals(anna, danach.eigentuemer());
        assertEquals(25_000L, danach.preis());
        assertEquals(zwei.erworbenAm(), danach.erworbenAm());
        assertEquals(2, danach.x());
    }

    @Test
    @DisplayName("Eine Gruppe laesst sich um ein weiteres Feld erweitern")
    void gruppeErweitern() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        long dieselbe = lager.gruppeSetzen(gruppe, WELT, anna, Set.of(), 30_000L,
                List.of(drei.id()));

        assertEquals(gruppe, dieselbe, "Erweitern legt keine zweite Gruppe an");
        assertEquals(3, lager.kinderVon(gruppe).size());
    }

    @Test
    @DisplayName("Mitbauer einer Gruppe stehen auf jedem einzelnen Feld")
    void mitbauerStehenAufJedemFeld() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna,
                Set.of(bert), 20_000L, List.of(eins.id(), zwei.id()));

        // Das ist das Durchschreiben aus Plan 6.1 - ohne es muesste darfBauen bei jedem
        // Block erst die Gruppe nachschlagen.
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().darfBauen(bert));
        assertTrue(lager.finde(WELT, 2, 0).orElseThrow().darfBauen(bert));

        lager.mitbauerSetzen(gruppe, Set.of());
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().darfBauen(bert));
        assertFalse(lager.finde(WELT, 2, 0).orElseThrow().darfBauen(bert));
    }

    @Test
    @DisplayName("Auftrennen gibt die Felder zurueck, ohne sie zu loeschen")
    void auftrennen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        lager.auftrennen(gruppe);

        assertTrue(lager.kinderVon(gruppe).isEmpty());
        assertTrue(lager.finde(WELT, 1, 0).isPresent(), "Das Feld bleibt bestehen");
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
        assertEquals(25_000L, lager.finde(WELT, 2, 0).orElseThrow().preis());
    }

    @Test
    @DisplayName("Ein einzelnes Feld laesst sich loesen, die Gruppe bleibt")
    void ausGruppeLoesen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                30_000L, List.of(eins.id(), zwei.id(), drei.id()));

        lager.ausGruppeLoesen(drei.id());

        assertEquals(2, lager.kinderVon(gruppe).size(), "die Gruppe behaelt zwei Felder");
        assertFalse(lager.finde(WELT, 3, 0).orElseThrow().hatElternteil(),
                "das geloeste Feld ist eigenstaendig");
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil(),
                "die anderen bleiben in der Gruppe");
        assertTrue(lager.finde(WELT, 3, 0).isPresent(), "das Feld bleibt bestehen");
    }

    @Test
    @DisplayName("Ein Feld ohne Gruppe laesst sich nicht loesen")
    void loesenOhneGruppeLaut() {
        // Lieber ein Abbruch als stilles Nichtstun: Die Welt wird nach dieser Zusage
        // umgebaut, und ein Weg, der zurueckkommt, obwohl die Gruppe noch besteht, waere
        // eine Mauer quer durch ein Grundstueck.
        BesitzLager lager = lager();
        Parzelle allein = lager.anlegen(WELT, 1, 0, anna, 0L);

        assertThrows(IllegalStateException.class, () -> lager.ausGruppeLoesen(allein.id()));
        assertThrows(IllegalStateException.class, () -> lager.ausGruppeLoesen(4711L));
    }

    @Test
    @DisplayName("Ein fremdes Feld laesst sich nicht an die eigene Gruppe haengen")
    void fremdesFeldGehtNicht() {
        BesitzLager lager = lager();
        Parzelle meins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle fremdes = lager.anlegen(WELT, 2, 0, bert, 0L);

        assertThrows(IllegalStateException.class, () -> lager.gruppeSetzen(
                Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(meins.id(), fremdes.id())));
        // Und nichts davon ist passiert.
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
        assertFalse(lager.finde(WELT, 2, 0).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Ein Feld laesst sich nicht an zwei Gruppen haengen")
    void feldNurInEinerGruppe() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(eins.id(), zwei.id()));

        assertThrows(IllegalStateException.class, () -> lager.gruppeSetzen(
                Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(drei.id(), zwei.id())));
    }

    @Test
    @DisplayName("Ein Feld einer Gruppe laesst sich nicht einzeln entfernen")
    void gruppeLaesstSichNichtEinzelnEntfernen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        assertThrows(IllegalStateException.class, () -> lager.entfernen(gruppe));
        assertEquals(2, lager.kinderVon(gruppe).size());
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
