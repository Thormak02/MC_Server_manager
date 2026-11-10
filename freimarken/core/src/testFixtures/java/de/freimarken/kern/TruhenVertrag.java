package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Truhenablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code SpielzeitVertrag}: Die eine ruft {@code Map.merge}, die andere
 * schreibt mit {@code ON DUPLICATE KEY UPDATE stueck = stueck + ?} - und genau an so
 * einer Stelle laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Die tragende Zusage ist das AUFSCHLAGEN.</b> Wer dreimal am Tag an die Truhe
 * geht, soll dreimal zaehlen; ein setzendes Schreiben verloere die vorigen Griffe, und
 * der Deckel waere so oft offen, wie man die Truhe schliesst. Das ist der Unterschied zur
 * {@link Bonusablage}, und er ist in beiden Richtungen gepruefter Vertrag.
 */
public abstract class TruhenVertrag {

    /** Ein Tag, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate HEUTE = LocalDate.of(2026, 11, 10);

    protected static final LocalDate GESTERN = HEUTE.minusDays(1);

    /** Eine frische, leere Ablage. */
    protected abstract Truhenablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat heute nichts behalten")
    void unbekannterHatNichts() {
        assertEquals(0, ablage().behalten(UUID.randomUUID(), HEUTE));
    }

    @Test
    @DisplayName("Geschrieben wird AUFSCHLAGEND - drei Griffe sind drei Zahlen")
    void aufschlagendUndNichtSetzend() {
        // DIE TRAGENDE PROBE DIESER ABLAGE. Setzend geschrieben stuende nach dem dritten
        // Griff eine 4 da statt einer 12 - und der Deckel waere so oft offen, wie man
        // die Truhe schliesst.
        Truhenablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.merke(anna, HEUTE, 5);
        ablage.merke(anna, HEUTE, 3);
        ablage.merke(anna, HEUTE, 4);

        assertEquals(12, ablage.behalten(anna, HEUTE));
    }

    @Test
    @DisplayName("Jeder Spieler hat seinen eigenen Tag")
    void jederSeinenEigenen() {
        Truhenablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.merke(anna, HEUTE, 16);

        assertEquals(16, ablage.behalten(anna, HEUTE));
        assertEquals(0, ablage.behalten(bert, HEUTE),
                "sonst sperrt der erste Griff eines Spielers alle anderen aus");
    }

    @Test
    @DisplayName("Jeder TAG hat seine eigene Zahl - morgen faengt bei nichts an")
    void jederTagSeineZahl() {
        // Der ganze Zweck des Deckels: Er gilt je TAG. Ohne den Tag im Schluessel waere
        // er ein Deckel fuer das ganze Leben.
        Truhenablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.merke(anna, GESTERN, 16);

        assertEquals(16, ablage.behalten(anna, GESTERN));
        assertEquals(0, ablage.behalten(anna, HEUTE));
    }

    @Test
    @DisplayName("Geraeumt wird AUSSCHLIESSEND - der heutige Tag bleibt stehen")
    void raeumenLaesstHeuteStehen() {
        // Einschliessend geraeumt waere der laufende Tag weg, und jeder duerfte noch
        // einmal nehmen - und zwar genau in dem Augenblick, in dem der taegliche Lauf
        // die Truhe leert. Dann steht sie voll, und der Deckel ist offen.
        //
        // DREI ALTE ZEILEN UND ZWEI SPIELER, nicht eine Zeile: Mit einer einzigen alten
        // Zeile ist "raeumt ALLE alten" von "raeumt die erste alte" nicht zu
        // unterscheiden. Eine Ablage, die je Nacht nur eine Zeile wegnimmt, waechst
        // weiter und wird nie wieder leer - und die Zahl im Protokoll des Laufes stimmte
        // trotzdem.
        Truhenablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.merke(anna, GESTERN, 7);
        ablage.merke(anna, GESTERN.minusDays(1), 5);
        ablage.merke(bert, GESTERN, 2);
        ablage.merke(anna, HEUTE, 3);

        int weg = ablage.raeume(HEUTE);

        assertEquals(3, weg, "alle drei Zeilen von vor heute");
        assertEquals(0, ablage.behalten(anna, GESTERN));
        assertEquals(0, ablage.behalten(anna, GESTERN.minusDays(1)));
        assertEquals(0, ablage.behalten(bert, GESTERN));
        assertEquals(3, ablage.behalten(anna, HEUTE), "heute bleibt unangetastet");
    }

    @Test
    @DisplayName("Raeumen ohne alte Zeilen loescht nichts und meldet 0")
    void raeumenOhneAlte() {
        Truhenablage ablage = ablage();
        ablage.merke(UUID.randomUUID(), HEUTE, 1);

        assertEquals(0, ablage.raeume(HEUTE));
    }

    @Test
    @DisplayName("Ohne Spieler, ohne Tag und ohne Stueck gibt es keine Zeile")
    void pflichtenGelten() {
        Truhenablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.behalten(null, HEUTE));
        assertThrows(IllegalArgumentException.class, () -> ablage.behalten(anna, null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(null, HEUTE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.merke(anna, null, 1));
        assertThrows(IllegalArgumentException.class, () -> ablage.merke(anna, HEUTE, 0));
        assertThrows(IllegalArgumentException.class, () -> ablage.merke(anna, HEUTE, -3));
        assertThrows(IllegalArgumentException.class, () -> ablage.raeume(null));
    }
}
