package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Bonusablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code SpielzeitVertrag}: Die eine setzt einen Wert in eine HashMap, die
 * andere schreibt vier Spalten mit {@code ON DUPLICATE KEY} - und genau an so einer Stelle
 * laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Hier liegt kein Geld.</b> Das ist die tragende Zusage dieses Teils, und sie macht
 * diesen Vertrag kurz: Es gibt keine Summenprobe zu halten, keine Doppelzahlung zu
 * verhindern und keine Transaktion zu pruefen. Geht eine Zeile verloren, ist eine Serie zu
 * kurz - mehr nicht.
 */
public abstract class BonusVertrag {

    /** Ein Tag, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    protected static final LocalDate GESTERN = HEUTE.minusDays(1);

    /** Eine frische, leere Ablage. */
    protected abstract Bonusablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat keine Serie - jeder faengt bei nichts an")
    void unbekannterHatNichts() {
        UUID anna = UUID.randomUUID();

        Bonusstand stand = ablage().lies(anna);

        assertEquals(anna, stand.spieler());
        assertNull(stand.letzterTag());
        assertEquals(0, stand.serie());
        assertEquals(0, stand.laengsteSerie());
        assertFalse(stand.schonDagewesen());
    }

    @Test
    @DisplayName("Was geschrieben wurde, kommt zurueck - alle vier Werte")
    void hinUndZurueck() {
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(new Bonusstand(anna, HEUTE, 5, 12));

        Bonusstand stand = ablage.lies(anna);
        // VIER verschiedene Zahlen und ein eigenes Datum: Bei 5 und 5 waere eine
        // Verwechslung von Serie und Bestmarke nicht zu sehen.
        assertEquals(HEUTE, stand.letzterTag());
        assertEquals(5, stand.serie());
        assertEquals(12, stand.laengsteSerie());
    }

    @Test
    @DisplayName("Geschrieben wird SETZEND und nicht aufgeschlagen")
    void setzendUndNichtAddierend() {
        // Der Unterschied zur Spielzeit, und er ist bewusst: Die Serie ist ein ZUSTAND
        // und keine Summe. Zwei Schreibzuege mit derselben Zahl ergeben dieselbe Zahl.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(new Bonusstand(anna, GESTERN, 4, 4));
        ablage.schreibe(new Bonusstand(anna, HEUTE, 5, 5));
        ablage.schreibe(new Bonusstand(anna, HEUTE, 5, 5));

        Bonusstand stand = ablage.lies(anna);
        assertEquals(5, stand.serie(), "nicht 10 und nicht 14 - gesetzt, nicht addiert");
        assertEquals(HEUTE, stand.letzterTag());
    }

    @Test
    @DisplayName("Eine Serie kann auch KLEINER werden - sie reisst ja")
    void serieDarfSinken() {
        // Das ist der Fall, den eine addierende Ablage nie koennte: Nach einer gerissenen
        // Serie steht dort 1, obwohl vorher 12 stand. Die Bestmarke bleibt.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(new Bonusstand(anna, GESTERN.minusDays(5), 12, 12));

        ablage.schreibe(new Bonusstand(anna, HEUTE, 1, 12));

        Bonusstand stand = ablage.lies(anna);
        assertEquals(1, stand.serie());
        assertEquals(12, stand.laengsteSerie());
    }

    @Test
    @DisplayName("Jeder Spieler hat seine eigene Serie")
    void spielerSindGetrennt() {
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.schreibe(new Bonusstand(anna, HEUTE, 5, 5));
        ablage.schreibe(new Bonusstand(bert, GESTERN, 1, 3));

        assertEquals(5, ablage.lies(anna).serie());
        assertEquals(1, ablage.lies(bert).serie());
        assertEquals(3, ablage.lies(bert).laengsteSerie());
        assertEquals(GESTERN, ablage.lies(bert).letzterTag());
    }

    @Test
    @DisplayName("Ein Stand OHNE Tag kommt auch so zurueck")
    void ohneTagBleibtOhneTag() {
        // Erreichbar ueber eine kuenftige Fassung, die Serien zuruecksetzt: Tag null,
        // Serie 0, Bestmarke bleibt. In der Datenbank ist das ein NULL in der Spalte, und
        // ein getDate, das daraus eine 1970er Jahreszahl macht, faellt hier auf.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(new Bonusstand(anna, null, 0, 9));

        Bonusstand stand = ablage.lies(anna);
        assertNull(stand.letzterTag(), "NULL bleibt NULL und wird nicht zu einem Datum");
        assertEquals(0, stand.serie());
        assertEquals(9, stand.laengsteSerie());
    }

    @Test
    @DisplayName("Der TAG kommt unveraendert zurueck - keine Zone verschiebt ihn")
    void tagWandertNicht() {
        // Die Falle, gegen die setObject(LocalDate) steht: Ein java.sql.Date ist ein
        // Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber nach UTC umrechnet -
        // auf einem Rechner in Berlin waere das 23:00 des Vortags, und in der Spalte
        // stuende der falsche Tag. Geprueft mit einem Tag am Monatsanfang, weil dort ein
        // Versatz um einen Tag auch den Monat dreht.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        LocalDate monatsanfang = LocalDate.of(2026, 3, 1);

        ablage.schreibe(new Bonusstand(anna, monatsanfang, 1, 1));

        assertEquals(monatsanfang, ablage.lies(anna).letzterTag());
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Stand geht nichts")
    void pflichtangaben() {
        Bonusablage ablage = ablage();

        assertThrows(IllegalArgumentException.class, () -> ablage.lies(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.schreibe(null));
    }

    @Test
    @DisplayName("Dreissig Tage hintereinander ergeben eine Serie von dreissig")
    void dreissigTage() {
        // Der Weg, den ein Spieler wirklich geht - Serienregel und Ablage zusammen. Hier
        // faellt auf, wenn die Ablage den TAG nicht fortschreibt: Dann stuende die Luecke
        // mit jedem Tag weiter offen, und die Serie bliebe bei 1.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        for (int tag = 0; tag < 30; tag++) {
            LocalDate dieser = HEUTE.minusDays(29L - tag);
            Bonusstand vorher = ablage.lies(anna);
            Serienregel.Urteil urteil = Serienregel.urteile(vorher.letzterTag(), dieser,
                    vorher.serie(), 2);
            assertTrue(urteil.zahlbar(), "Tag " + tag);
            ablage.schreibe(vorher.nachZahlung(dieser, urteil.serie()));
        }

        Bonusstand stand = ablage.lies(anna);
        assertEquals(30, stand.serie());
        assertEquals(30, stand.laengsteSerie());
        assertEquals(HEUTE, stand.letzterTag());
    }

    @Test
    @DisplayName("Eine sehr lange Serie passt in die Zeile")
    void langeSerie() {
        // Ein INT in der Spalte: Wer taeglich spielt, kommt in zehn Jahren auf 3.650 -
        // weit darunter. Geprueft wird der Rand, damit ein zu kleiner Spaltentyp nicht
        // erst nach Jahren auffaellt.
        Bonusablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibe(new Bonusstand(anna, HEUTE, 1_000_000, 1_000_000));

        assertEquals(1_000_000, ablage.lies(anna).serie());
    }
}
