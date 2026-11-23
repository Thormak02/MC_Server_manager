package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Losablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@link BonusVertrag}: Die eine ruft {@code merge} auf einer HashMap, die
 * andere schreibt {@code ON DUPLICATE KEY UPDATE anzahl = anzahl + 1} - und genau an so
 * einer Stelle laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Hier liegt Geld</b>, und das ist der Unterschied zum {@link BonusVertrag}, der
 * ausdruecklich keines hat. Diese Zahl ist die einzige Wahrheit darueber, was das naechste
 * Los kostet: Das erste Los am Tag ist gratis und wird mit 0 Mark gebucht, aus dem
 * Buchungsbuch ist die Zahl also nicht zu holen. Darum pruefen die Saetze hier das
 * AUFSCHLAGENDE Zaehlen so genau - ein verschlucktes Los verbilligt das naechste.
 */
public abstract class LosVertrag {

    /** Ein Tag, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate HEUTE = LocalDate.of(2026, 11, 22);

    protected static final LocalDate GESTERN = HEUTE.minusDays(1);

    /** Eine frische, leere Ablage. */
    protected abstract Losablage ablage();

    /**
     * Bringt die Zahl dieses Spielers an diesem Tag auf {@link Integer#MAX_VALUE}.
     *
     * <p>Der Vertrag kann diesen Zustand nicht selbst herstellen - zwei Milliarden Mal
     * {@code zaehleEins} zu rufen ist kein Probenlauf. Wie er hergestellt wird, weiss nur
     * die Umsetzung: die Karte setzt ihren Eintrag, die Datenbank schreibt ihre Zeile.
     *
     * <p>Ein ABSTRAKTER Haken und kein ueberschreibbarer mit Rueckfall: Ein Rueckfall, der
     * den Fall ueberspringt, meldet gruen, und genau deshalb ist die Luecke zweiundzwanzig
     * Tage unbemerkt geblieben.
     */
    protected abstract void saettige(Losablage ablage, UUID spieler, LocalDate tag);

    @Test
    @DisplayName("Bei Integer.MAX_VALUE laeuft die Zahl AN und wirft nicht")
    void saettigungLaeuftAn() {
        // DIE ZUSAGE STEHT AN DER SCHNITTSTELLE, also muss sie JEDE Umsetzung halten
        // [23.11.2026]. Die Karte hielt sie (merge mit MAX_VALUE-Pruefung), die Datenbank
        // nicht: "anzahl = anzahl + 1" auf INT endet im strict mode in "Out of range
        // value" und wurde zu IllegalStateException - dem Wurf, den die Zusage
        // ausdruecklich fuer schlechter erklaert als eine anlaufende Zahl.
        //
        // Erreichbar ist das nur ohne Tagesgrenze (lotterie.hoechste-lose-je-tag: 0, laut
        // config.yml erlaubt). MIT dem Wurf brach Lotteriebuch an dieser Stelle ab und
        // zog NICHT; mit der anlaufenden Zahl zieht es weiter, und der Preis bleibt auf
        // der Stufe der groessten Zahl stehen. Das ist die Richtung, in der nichts
        // kaputtgeht: ein Preis, der nicht mehr steigt, ist kein verlorenes Los.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        saettige(ablage, anna, HEUTE);
        assertEquals(Integer.MAX_VALUE, ablage.lies(anna, HEUTE).loseHeute(),
                "die Vorbereitung selbst muss sitzen - sonst prueft der Satz nichts");

        int nachher = ablage.zaehleEins(anna, HEUTE);

        assertEquals(Integer.MAX_VALUE, nachher,
                "die zurueckgegebene Zahl laeuft an und wird nicht negativ");
        assertEquals(Integer.MAX_VALUE, ablage.lies(anna, HEUTE).loseHeute(),
                "und in der Ablage steht sie auch so");
        // UND SIE IST NICHT NEGATIV - die eigentliche Gefahr: Losstand wuerfe dann mitten
        // im Zug. Eine eigene Zeile, weil ein Ueberlauf auf -2147483648 die zwei
        // Zusicherungen oben zwar schon rot macht, der Grund aber hier benannt gehoert.
        assertTrue(ablage.lies(anna, HEUTE).loseHeute() > 0,
                "eine uebergelaufene Zahl waere negativ, und Losstand wuerfe");
    }

    @Test
    @DisplayName("Ein unbekannter Spieler hat heute kein Los gezogen")
    void unbekannterHatNichts() {
        UUID anna = UUID.randomUUID();

        Losstand stand = ablage().lies(anna, HEUTE);

        assertEquals(anna, stand.spieler());
        assertEquals(HEUTE, stand.tag());
        assertEquals(0, stand.loseHeute());
    }

    @Test
    @DisplayName("Das erste Zaehlen gibt 1 zurueck und legt die Zeile an")
    void erstesLos() {
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        int nachher = ablage.zaehleEins(anna, HEUTE);

        assertEquals(1, nachher, "die ZURUECKGEGEBENE Zahl ist die nach dem Zaehlen");
        assertEquals(1, ablage.lies(anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Gezaehlt wird AUFSCHLAGEND und nicht gesetzt")
    void aufschlagendUndNichtSetzend() {
        // Der Unterschied zum Tagesbonus, und er ist bewusst: Die Zahl ist eine ZAEHLUNG
        // und kein Zustand. Drei Lose sind drei und nicht eines.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertEquals(1, ablage.zaehleEins(anna, HEUTE));
        assertEquals(2, ablage.zaehleEins(anna, HEUTE));
        assertEquals(3, ablage.zaehleEins(anna, HEUTE));

        assertEquals(3, ablage.lies(anna, HEUTE).loseHeute(),
                "nicht 1 - aufgeschlagen, nicht gesetzt");
    }

    @Test
    @DisplayName("Jeder TAG hat seine eigene Zahl")
    void tageSindGetrennt() {
        // Die wichtigste Zusage neben dem Aufschlagen: Ohne sie waere am naechsten Tag
        // kein Los mehr gratis, und niemand koennte die Zahl zuruecksetzen - einen Lauf,
        // der das taete, gibt es nicht und soll es nicht geben.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.zaehleEins(anna, GESTERN);
        ablage.zaehleEins(anna, GESTERN);
        ablage.zaehleEins(anna, HEUTE);

        assertEquals(2, ablage.lies(anna, GESTERN).loseHeute());
        assertEquals(1, ablage.lies(anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Jeder SPIELER hat seine eigene Zahl")
    void spielerSindGetrennt() {
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.zaehleEins(anna, HEUTE);
        ablage.zaehleEins(anna, HEUTE);
        ablage.zaehleEins(bert, HEUTE);

        assertEquals(2, ablage.lies(anna, HEUTE).loseHeute());
        assertEquals(1, ablage.lies(bert, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Spieler und Tag zusammen sind der Schluessel - nicht einer von beiden")
    void beideTeileDesSchluessels() {
        // Vier Felder, die sich paarweise in GENAU einem Teil unterscheiden: Wer nur den
        // Spieler oder nur den Tag als Schluessel nimmt, bekommt hier eine 2, wo eine 1
        // stehen muss.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.zaehleEins(anna, HEUTE);
        ablage.zaehleEins(anna, GESTERN);
        ablage.zaehleEins(bert, HEUTE);
        ablage.zaehleEins(bert, GESTERN);

        assertEquals(1, ablage.lies(anna, HEUTE).loseHeute());
        assertEquals(1, ablage.lies(anna, GESTERN).loseHeute());
        assertEquals(1, ablage.lies(bert, HEUTE).loseHeute());
        assertEquals(1, ablage.lies(bert, GESTERN).loseHeute());
    }

    @Test
    @DisplayName("Der TAG kommt unveraendert zurueck - keine Zone verschiebt ihn")
    void tagWandertNicht() {
        // Die Falle, gegen die setObject(LocalDate) steht: Ein java.sql.Date ist ein
        // Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber nach UTC umrechnet -
        // auf einem Rechner in Berlin waere das 23:00 des Vortags. Geprueft mit einem Tag
        // am Monatsanfang, weil dort ein Versatz um einen Tag auch den Monat dreht.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        LocalDate monatsanfang = LocalDate.of(2026, 3, 1);

        ablage.zaehleEins(anna, monatsanfang);

        assertEquals(1, ablage.lies(anna, monatsanfang).loseHeute());
        assertEquals(monatsanfang, ablage.lies(anna, monatsanfang).tag());
        // UND der Vortag ist leer: Waere der Tag um eine Zone verschoben, stuende die 1
        // hier statt oben, und die Probe darueber waere trotzdem gruen gewesen, wenn sie
        // nur den gelesenen Tag mit dem geschriebenen vergleicht.
        assertEquals(0, ablage.lies(anna, monatsanfang.minusDays(1)).loseHeute(),
                "der Vortag darf nicht mitgezaehlt worden sein");
    }

    @Test
    @DisplayName("Hundert Lose hintereinander zaehlen sauber durch")
    void vieleLose() {
        // HUNDERT und nicht 40.000, obwohl die Spalte ein INT ist und die Tagesgrenze 0
        // (unbegrenzt) sein darf: Jeder Aufruf ist auf MariaDB ein INSERT plus ein SELECT,
        // und 40.000 davon sind achtzigtausend Rundreisen zur Datenbank - eine Probe, die
        // Minuten laeuft, nimmt niemand mehr ernst. Die Breite der Spalte ist am
        // Schemaschritt begruendet; was hier geprueft wird, ist, dass die Zahl ueber viele
        // Zuege hinweg genau mitlaeuft und nicht irgendwann einen Schritt verliert.
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        for (int i = 1; i <= 100; i++) {
            assertEquals(i, ablage.zaehleEins(anna, HEUTE),
                    "jeder Zug gibt genau seine eigene Nummer zurueck");
        }

        assertEquals(100, ablage.lies(anna, HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Tag geht nichts - in BEIDE Richtungen")
    void pflichtangaben() {
        Losablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.lies(null, HEUTE));
        assertThrows(IllegalArgumentException.class, () -> ablage.lies(anna, null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.zaehleEins(null, HEUTE));
        assertThrows(IllegalArgumentException.class, () -> ablage.zaehleEins(anna, null));
    }
}
