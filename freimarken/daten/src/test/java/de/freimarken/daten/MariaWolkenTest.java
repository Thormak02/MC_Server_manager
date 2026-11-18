package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.freimarken.kern.Wolkenablage;
import de.freimarken.kern.WolkenVertrag;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Wolkenvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Proben uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaTruheTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code INSERT IGNORE} wirklich NICHT schreibt und 0 Zeilen meldet, dass der
 * Fremdschluessel den Fortschritt einer freigegebenen Insel MITNIMMT, dass eine Insel
 * ohne Standzeile trotzdem eine Stufe haben kann, und dass eine Nummer, die es in
 * {@code parzellen} nicht gibt, am Fremdschluessel auflaeuft statt stumm eine Waisenzeile
 * zu schreiben.
 */
@DisplayName("Der Inselzustand in MariaDB")
class MariaWolkenTest extends WolkenVertrag {

    private static final String WELT = "freimarken:wolkenwerder";

    private static Datenbank datenbank;
    private static MariaWolken wolken;
    private static MariaLager lager;

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
        wolken = new MariaWolken(datenbank);
        lager = new MariaLager(datenbank);
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
        naechstesFeld = 0;
    }

    /** Damit jede frische Insel ihr eigenes Feld bekommt. */
    private int naechstesFeld;

    @Override
    protected Wolkenablage ablage() {
        return wolken;
    }

    @Override
    protected long frischeInsel() {
        // EINE ECHTE INSEL, und das ist hier keine Umstaendlichkeit: Die Tabelle traegt
        // einen Fremdschluessel auf parzellen(id). Ohne ihn gaebe es kein CASCADE - und
        // damit keine Zusage, dass der Fortschritt einer freigegebenen Insel mitgeht.
        return lager.wolkeAnlegen(WELT, naechstesFeld++, 0, UUID.randomUUID(), 0L).id();
    }

    @Test
    @DisplayName("Zweimal erfuellen schreibt EINE Zeile, nicht zwei")
    void zweimalErfuellenEineZeile() throws SQLException {
        long insel = frischeInsel();

        wolken.erfuellen(insel, "erster_stein");
        wolken.erfuellen(insel, "erster_stein");

        assertEquals(1, zeilen("wolke_herausforderung"),
                "INSERT IGNORE laeuft am Hauptschluessel auf; zwei Zeilen hiessen, dass"
                        + " (parzelle_id, schluessel) nicht eindeutig ist - und dann"
                        + " zahlte jede Wiederholung die Belohnung noch einmal");
    }

    @Test
    @DisplayName("Eine Insel ohne Standzeile kann trotzdem eine STUFE haben")
    void stufeOhneStandzeile() throws SQLException {
        // Das Erfuellen schreibt NUR in die Herausforderungstabelle - eine Zeile in
        // wolkeninsel entsteht erst beim Biom oder beim Zaehlen. Genau diese Insel faehlte
        // beim Start, wenn alle() nur ueber eine Tabelle liefe.
        long insel = frischeInsel();

        wolken.erfuellen(insel, "erster_stein");

        assertEquals(0, zeilen("wolkeninsel"), "keine Standzeile");
        assertEquals(1, wolken.stand(insel).stufe());
        assertEquals(1, wolken.alle().size());
    }

    @Test
    @DisplayName("Wird die INSEL geloescht, geht ihr Fortschritt mit - ueber CASCADE")
    void cascade() throws SQLException {
        // Die Zusage des Fremdschluessels, und sie ist der Grund, warum er dasteht: Wer
        // seine Insel freigibt, gibt ihren Fortschritt mit auf. Ohne CASCADE blieben
        // zwei Waisenzeilen stehen, und der naechste Spieler auf diesem Feld erbte sie -
        // samt Erzausbeute.
        long insel = frischeInsel();
        wolken.biomSetzen(insel, "swamp");
        wolken.erfuellen(insel, "erster_stein");

        lager.entfernen(insel);

        assertEquals(0, zeilen("wolkeninsel"));
        assertEquals(0, zeilen("wolke_herausforderung"));
    }

    @Test
    @DisplayName("Eine Insel, die es nicht gibt, laeuft am Fremdschluessel auf")
    void fremdschluesselHaelt() {
        // Die Gegenrichtung: Eine Standzeile ohne Insel waere eine Waise, die niemand
        // mehr findet - und ihre Punkte stuenden in der Rangliste neben Inseln, die es
        // gibt.
        //
        // DIE ZWEITE ZEILE IST DIE WICHTIGE, und sie war rot: Das Biom laeuft von selbst
        // am Fremdschluessel auf, das Erfuellen aber NICHT. INSERT IGNORE macht aus
        // Fehler 1452 eine Warnung und meldet null Zeilen - und null Zeilen hiess
        // vorher "stand schon drin". Eine Insel, die es nicht gibt, bekam damit lautlos
        // ein "schon erfuellt" und keine Belohnung. Darum fragt MariaWolken im
        // Null-Fall nach, ob die Zeile wirklich dasteht (gemessen am 18.11.2026).
        assertThrows(IllegalStateException.class,
                () -> wolken.biomSetzen(999_999L, "swamp"));
        assertThrows(IllegalStateException.class,
                () -> wolken.erfuellen(999_999L, "erster_stein"));
    }

    private static int zeilen(String tabelle) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
                ResultSet ergebnis = anweisung.executeQuery(
                        "SELECT COUNT(*) FROM " + tabelle)) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
