package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Rundgangsablage einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link RangVertrag} und {@link AnmeldeVertrag}: Die Umsetzung
 * bringt ihre eigene Testklasse mit und erbt diese Pruefungen. Dadurch wird eine
 * Umsetzung, die sich anders verhaelt als die andere, in einem Testlauf sichtbar und
 * nicht im Betrieb.
 *
 * <p>Hier stehen alle vier Zusagen aus dem Klassenkopf von {@link Rundgangsablage}. Die
 * tragende ist die DRITTE: <b>{@code merke} sagt, ob es NEU war.</b> An dieser Antwort
 * haengt, ob der Spieler den Stationstext liest - eine Ablage, die immer {@code true}
 * gibt, zeigte ihn bei jedem Schritt durch dieselbe Tuer.
 */
public abstract class RundgangVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-21T12:00:00Z");

    /** Eine Stunde spaeter - fuer die Frage, ob der Zeitpunkt von aussen kommt. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    protected static final String BUERGERAMT = "buergeramt";

    protected static final String ZUNFTHAUS = "zunfthaus";

    /** Eine frische, leere Ablage. */
    protected abstract Rundgangsablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat nichts gesehen - und das ist kein Fehler")
    void unbekannterHatNichtsGesehen() {
        UUID niemand = UUID.randomUUID();

        Set<String> gesehen = ablage().gesehen(niemand);

        // Ein null oder ein Wurf waere im Betrieb ein Spieler, bei dem der erste Schritt
        // in ein Gebaeude abbricht.
        assertEquals(Set.of(), gesehen, "wer nirgends war, hat eine leere Menge");
    }

    @Test
    @DisplayName("Eine gemerkte Station steht danach drin - und merke sagt, dass sie neu war")
    void gemerkteStationStehtDrin() {
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.merke(anna, BUERGERAMT, MITTAGS),
                "die erste Station ist neu");

        assertEquals(Set.of(BUERGERAMT), ablage.gesehen(anna));
    }

    @Test
    @DisplayName("Dieselbe Station zweimal ist NICHT neu")
    void zweimalDieselbeStation() {
        // DIE TRAGENDE ZUSAGE. An dieser Antwort haengt, ob der Spieler den
        // Stationstext liest - bei jedem Schritt durch dieselbe Tuer waere es eine Plage.
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.merke(anna, BUERGERAMT, MITTAGS);

        assertFalse(ablage.merke(anna, BUERGERAMT, SPAETER),
                "ein zweiter Schritt in dieselbe Station ist nichts Neues");
        assertEquals(Set.of(BUERGERAMT), ablage.gesehen(anna),
                "und er legt keine zweite Zeile an");
    }

    @Test
    @DisplayName("Zwei Stationen stehen nebeneinander")
    void zweiStationenNebeneinander() {
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.merke(anna, BUERGERAMT, MITTAGS));
        assertTrue(ablage.merke(anna, ZUNFTHAUS, SPAETER));

        assertEquals(Set.of(BUERGERAMT, ZUNFTHAUS), ablage.gesehen(anna));
    }

    @Test
    @DisplayName("Zwei Spieler stehen einander nicht im Weg")
    void zweiSpielerGetrennt() {
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.merke(anna, BUERGERAMT, MITTAGS);

        assertEquals(Set.of(BUERGERAMT), ablage.gesehen(anna));
        assertEquals(Set.of(), ablage.gesehen(bert),
                "Berts Stand darf von Annas Schritt nichts wissen");
    }

    @Test
    @DisplayName("Grossgeschriebene Namen werden kleingeschrieben abgelegt")
    void nameWirdKleingeschrieben() {
        // EINMAL normalisiert, und zwar in der gemeinsamen Pruefung. Normalisierte jede
        // Umsetzung selbst, bogen zwei Stellen denselben Namen zurecht - und eines Tages
        // verschieden.
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.merke(anna, "  BUERGERAMT  ", MITTAGS));

        assertEquals(Set.of(BUERGERAMT), ablage.gesehen(anna));
        assertFalse(ablage.merke(anna, "Buergeramt", SPAETER),
                "derselbe Name in anderer Schreibweise ist dieselbe Station");
    }

    @Test
    @DisplayName("Die gelesene Menge laesst sich nicht von aussen aendern")
    void gelesenesIstUnveraenderlich() {
        // Wer die Menge veraenderte, aenderte den Stand, ohne durch merke zu gehen - und
        // damit ohne die Antwort "war es neu". Beide Umsetzungen geben darum eine Kopie.
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.merke(anna, BUERGERAMT, MITTAGS);

        Set<String> gelesen = ablage.gesehen(anna);

        assertThrows(UnsupportedOperationException.class, () -> gelesen.add(ZUNFTHAUS));
    }

    @Test
    @DisplayName("Ohne Spieler, Zeitpunkt oder Namen wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.gesehen(null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(null, BUERGERAMT, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(anna, BUERGERAMT, null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(anna, " ", MITTAGS));
    }

    @Test
    @DisplayName("Ein zu langer Stationsname wird abgewiesen")
    void zuLangerNameWirdAbgewiesen() {
        // HIER UND NICHT ERST IN DER DATENBANK: Ohne diese Pruefung nahm die
        // Speicherfassung an, was MariaDB abweist.
        Rundgangsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        String zuLang = "x".repeat(Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE + 1);

        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(anna, zuLang, MITTAGS));

        // UND DIE GEGENRICHTUNG: genau die Hoechstlaenge geht durch. Ohne diese Haelfte
        // waere auch eine Ablage gruen, die JEDEN Namen abweist.
        String genauLang = "x".repeat(Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE);
        assertTrue(ablage.merke(anna, genauLang, MITTAGS));
        assertEquals(Set.of(genauLang), ablage.gesehen(anna));
    }
}
