package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.BlockbuchVertrag;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Blockbuchvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaAbholfachTest}.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass der
 * Zeitpunkt ohne Zonenreise hin und zurueck kommt, dass die Spalte {@code gelungen} als
 * {@code TINYINT} wirklich beide Werte haelt, dass die erste Tat eines Spielers OHNE
 * Kassenzeile durchgeht - die Begruendung dafuer, dass diese Tabelle keinen
 * Fremdschluessel hat -, und dass die Grenze wirklich im {@code SQL} steht und nicht erst
 * in Java abgeschnitten wird.
 */
@DisplayName("Blockbuch in MariaDB")
class MariaBlockbuchTest extends BlockbuchVertrag {

    private static Datenbank datenbank;
    private static MariaBlockbuch buch;

    @BeforeAll
    static void verbinden() throws SQLException {
        String url = System.getProperty("freimarken.db.url", "");
        Assumptions.assumeFalse(url.isBlank(),
                "Keine Zugangsdaten - data/freimarken/datenbank.properties fehlt. "
                        + "Erst skripte/mariadb_einrichten.bat ausfuehren.");

        datenbank = new Datenbank(url,
                System.getProperty("freimarken.db.benutzer"),
                System.getProperty("freimarken.db.passwort"));
        datenbank.migriere();
        buch = new MariaBlockbuch(datenbank);
    }

    @AfterAll
    static void trennen() {
        if (datenbank != null) {
            datenbank.close();
        }
    }

    @BeforeEach
    void leeren() throws SQLException {
        datenbank.leereAlles();
    }

    @Override
    protected Blockbuchablage ablage() {
        return buch;
    }

    @Test
    @DisplayName("Der Zeitpunkt kommt auf die Millisekunde zurueck")
    void zeitpunktOhneZonenreise() {
        // Eine Zeit mit Millisekunden UND nahe an einer Tagesgrenze: Eine Fassung, die
        // ueber eine Zone rechnet, verschiebt sie um Stunden - und dann raeumt die Frist
        // einen Tag zu frueh oder zu spaet auf. Eine Fassung, die auf DATETIME ohne
        // Bruchteile schreibt, verliert die 123, und damit den Tiebreaker, der
        // gleichzeitige Zeilen stabil haelt.
        Instant knappNachMitternacht = Instant.parse("2026-11-09T00:00:00.123Z");
        UUID anna = UUID.randomUUID();

        buch.schreibe(List.of(Blockzeile.neu(knappNachMitternacht, anna, ORT,
                Blocktat.ABBAUEN, true, "deepslate")));

        assertEquals(knappNachMitternacht, buch.amOrt(ORT, 20).get(0).zeitpunkt());
    }

    @Test
    @DisplayName("Die Spalte haelt UTC - gelesen am ROHEN Text, nicht ueber den Treiber")
    void spalteHaeltUtc() throws SQLException {
        // DIE PROBE, DIE GEFEHLT HAT [11.11.2026]. Die Probe darueber schreibt und liest
        // durch denselben Treiber - schreibt der in Ortszeit und liest er in Ortszeit,
        // kommt dieselbe Sekunde heraus, und in der Spalte steht trotzdem etwas anderes
        // als in buchungen. Gefunden wurde es nicht von einer Probe, sondern beim Lesen
        // zweier Tabellen nebeneinander.
        Instant wann = Instant.parse("2026-11-09T00:00:00.123Z");

        buch.schreibe(List.of(Blockzeile.neu(wann, UUID.randomUUID(), ORT,
                Blocktat.ABBAUEN, true, "deepslate")));

        assertEquals("2026-11-09 00:00:00.123",
                rohesDatum(datenbank, "zeitpunkt", "blockbuch"),
                "in der Spalte steht UTC - sonst liegt jede Gegenueberstellung mit"
                        + " buchungen um den Zonenversatz daneben");
    }

    @Test
    @DisplayName("Die erste Tat eines Spielers OHNE Kassenzeile geht durch")
    void ersteTatOhneKasse() {
        // DIE PROBE ZUR BEGRUENDUNG IM SCHEMASCHRITT. Ein Fremdschluessel auf kassen
        // liesse den Schreibzug scheitern, sobald ein Spieler noch keine Kassenzeile hat
        // - also beim allerersten Block eines neuen Spielers, und das ist der haeufigste
        // Fall von allen. leereAlles() hat gerade jede Kassenzeile geloescht, diese UUID
        // ist also garantiert unbekannt.
        UUID nochNieHier = UUID.randomUUID();

        assertEquals(1, buch.schreibe(List.of(Blockzeile.neu(WANN, nochNieHier, ORT,
                Blocktat.SETZEN, true, "dirt"))));
        assertEquals(1, buch.vonSpieler(nochNieHier, 20).size());
    }

    @Test
    @DisplayName("gelungen haelt BEIDE Werte - und verwechselt sie nicht")
    void gelungenHaeltBeideWerte() {
        // TINYINT(1) ist die Stelle, an der ein false stumm zu einer 0 und wieder zu
        // einem false werden muss. Eine Fassung, die die Spalte nicht setzt, bekommt von
        // der Datenbank ebenfalls eine 0 - und dann sieht JEDE Zeile wie ein abgewiesener
        // Versuch aus. Darum werden beide Werte in EINEM Schreibzug geprueft: Kommen sie
        // unterschiedlich zurueck, ist die Spalte wirklich beschrieben worden.
        UUID anna = UUID.randomUUID();
        Blockort zweiter = new Blockort(ORT.welt(), ORT.x() + 1, ORT.y(), ORT.z());

        buch.schreibe(List.of(
                Blockzeile.neu(WANN, anna, ORT, Blocktat.SETZEN, true, "gelungen"),
                Blockzeile.neu(WANN, anna, zweiter, Blocktat.SETZEN, false,
                        "abgewiesen")));

        assertTrue(buch.amOrt(ORT, 20).get(0).gelungen());
        assertFalse(buch.amOrt(zweiter, 20).get(0).gelungen(),
                "dieselbe Welt, derselbe Spieler, dieselbe Zeit, dieselbe Tat - die"
                        + " beiden Zeilen unterscheiden sich NUR im Ausgang");
    }

    @Test
    @DisplayName("Die Grenze steht im SQL - gemessen an mehr Zeilen, als sie zulaesst")
    void grenzeStehtImSql() {
        // Dass die Grenze WIRKT, prueft der Vertrag. Hier geht es darum, dass sie im
        // LIMIT steht und nicht erst in Java abgeschnitten wird: Bei fuenfzig Zeilen
        // waere der Unterschied unsichtbar, bei Zehntausenden ist er der ganze Grund fuer
        // diese Pflichtangabe. Nachweisen laesst sich das ohne Messgeraet nicht - was
        // bleibt, ist die Zusage, dass auch bei mehr Zeilen als dem Happen genau die
        // verlangte Zahl zurueckkommt.
        UUID anna = UUID.randomUUID();
        List<Blockzeile> viele = new ArrayList<>();
        for (int nummer = 0; nummer < 50; nummer++) {
            viele.add(Blockzeile.neu(WANN.plusSeconds(nummer), anna, ORT,
                    Blocktat.ABBAUEN, true, "block_" + nummer));
        }
        assertEquals(50, buch.schreibe(viele));

        assertEquals(List.of("block_49", "block_48", "block_47"),
                bloecke(buch.amOrt(ORT, 3)),
                "drei von fuenfzig - und zwar die drei neuesten");
        assertEquals(3, buch.vonSpieler(anna, 3).size());
    }

    @Test
    @DisplayName("Aufraeumen geht in mehreren Happen durch einen grossen Bestand")
    void aufraeumenInMehrerenHappen() {
        // Der Fall, fuer den der Happen gebaut ist: Am ersten faelligen Tageswechsel
        // gehen Wochen auf einmal weg. Geprueft wird, dass mehrere Schlaege wirklich
        // vorankommen - ein DELETE mit LIMIT, das immer dieselben Zeilen auswaehlt und
        // nicht loescht, liefe fuer immer und die Tabelle waechst weiter.
        UUID anna = UUID.randomUUID();
        List<Blockzeile> viele = new ArrayList<>();
        for (int nummer = 0; nummer < 25; nummer++) {
            viele.add(Blockzeile.neu(WANN.plusSeconds(nummer), anna, ORT,
                    Blocktat.ABBAUEN, true, "block_" + nummer));
        }
        buch.schreibe(viele);
        Instant alleFaellig = WANN.plusSeconds(1000);

        assertEquals(10, buch.raeumeAuf(alleFaellig, 10));
        assertEquals(10, buch.raeumeAuf(alleFaellig, 10));
        assertEquals(5, buch.raeumeAuf(alleFaellig, 10),
                "der letzte Schlag findet nur noch den Rest");
        assertEquals(0, buch.raeumeAuf(alleFaellig, 10));
        assertTrue(buch.amOrt(ORT, 20).isEmpty());
    }
    /**
     * Liest den ROHEN Text einer Zeitspalte - ohne den Treiber dazwischen.
     *
     * <p>{@code getTimestamp} rechnet, und genau das soll hier nicht geschehen: Ein Hin
     * und Zurueck durch denselben Treiber beweist nichts ueber den Inhalt der Spalte.
     */
    private static String rohesDatum(Datenbank bank, String spalte, String tabelle)
            throws SQLException {
        try (var anfrage = bank.verbindung().prepareStatement(
                "SELECT CAST(" + spalte + " AS CHAR) FROM " + tabelle + " LIMIT 1");
                var ergebnis = anfrage.executeQuery()) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            return ergebnis.getString(1);
        }
    }

}
