package de.freimarken.daten;

import de.freimarken.kern.Grundstuecksablage;
import de.freimarken.kern.GrundstuecksablageVertrag;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * Derselbe Vertrag wie bei der Fassung im Arbeitsspeicher - gegen die echte Datenbank.
 *
 * <p>Dieselbe Bauart wie {@link MariaLagerTest}: Ohne Zugangsdaten uebersprungen, mit
 * ihnen wirklich gefahren. Stillschweigend durchwinken waere das Schlimmste - eine gruene
 * Suite, die nie eine Datenbank gesehen hat.
 *
 * <p><b>Was NUR hier geprueft wird:</b> {@code fk_grundstueck_stadt} zeigt auf
 * {@code parzellen.id}, also muss die Stadt wirklich dastehen. Darum legt
 * {@link #stadtAnlegen} eine echte Freistadt an - und wenn der Fremdschluessel eines
 * Tages fehlt, faellt das nirgends sonst auf.
 */
@DisplayName("Grundstuecke in MariaDB")
class MariaGrundstueckeTest extends GrundstuecksablageVertrag {

    private static Datenbank datenbank;
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
        lager = new MariaLager(datenbank);
    }

    @AfterAll
    static void trennen() {
        if (datenbank != null) {
            datenbank.close();
        }
    }

    /**
     * Vor JEDER Probe geleert - gerufen vom Vertrag, VOR dem Anlegen der Staedte.
     *
     * <p>Nicht als eigenes {@code @BeforeEach}: Das liefe NACH dem des Vertrags, also
     * nach dem Anlegen - und raeumte genau die Staedte weg, an denen die Grundstuecke
     * dieser Probe haengen sollen.
     */
    @Override
    protected void leeren() {
        try {
            datenbank.leereAlles();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Das Probenschema liess sich nicht leeren",
                    fehler);
        }
    }

    @Override
    protected Grundstuecksablage ablage() {
        return new MariaGrundstuecke(datenbank);
    }

    /**
     * Eine ECHTE Freistadt in {@code parzellen} - der Fremdschluessel verlangt sie.
     *
     * <p>Mit einem eigenen Namen je Aufruf: {@code uq_besitz_name (welt, name)} laesst
     * denselben Namen je Welt nur einmal, und diese Methode wird mehrmals je Probe
     * gerufen.
     */
    @Override
    protected long stadtAnlegen(String welt, int feldX, int feldZ) {
        return lager.freistadtAnlegen(welt, feldX, feldZ, UUID.randomUUID(), 400_000L,
                "stadt-" + feldX + "-" + Math.abs(feldZ)).id();
    }
}
