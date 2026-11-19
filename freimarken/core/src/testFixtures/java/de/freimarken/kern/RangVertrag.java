package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Rangablage einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link BerufeVertrag} und {@link KasseVertrag}: Die Umsetzung
 * bringt ihre eigene Testklasse mit und erbt diese Pruefungen. Dadurch wird eine
 * Umsetzung, die sich anders verhaelt als die andere, in einem Testlauf sichtbar und
 * nicht im Betrieb.
 *
 * <p>Hier stehen alle fuenf Zusagen aus dem Klassenkopf von {@link Rangablage}. Die
 * wichtigste ist die zweite: <b>Keine Zeile heisst "unterste Stufe" und nicht "kein
 * Rang".</b> Eine Ablage, die beim Lesen eines Unbekannten {@code null} gaebe oder wuerfe,
 * waere im Betrieb ein Spieler, der beim ersten Beitritt gar nichts darf - und das sieht
 * aus wie die Regel, nicht wie ein Fehler.
 *
 * <p>Die vierte Zusage - <b>eine Zeile je Spieler</b> - laesst sich hier nur an ihrer
 * WIRKUNG pruefen: Nach zwei Aufstiegen gilt der zweite, und der erste ist nicht mehr zu
 * lesen. Dass in MariaDB wirklich nur EINE Zeile steht, prueft {@code MariaRaengeTest}
 * mit rohem SQL - das kann ein umsetzungsfreier Vertrag nicht.
 */
public abstract class RangVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    /** Eine Stunde spaeter - fuer die Frage, ob der Zeitpunkt von aussen kommt. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    /** Zwei Stufen, deren REIHENFOLGE die Ablage nicht kennt und nicht kennen darf. */
    protected static final String SIEDLER = "siedler";

    protected static final String BUERGER = "buerger";

    /** Eine frische, leere Ablage. */
    protected abstract Rangablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat keinen Rang - und das ist kein Fehler")
    void unbekannterHatKeinenRang() {
        UUID niemand = UUID.randomUUID();

        Rangstand stand = ablage().lies(niemand);

        // DIE ZWEITE ZUSAGE. Ein null oder ein Wurf waere im Betrieb ein Spieler, der
        // beim ersten Beitritt gar nichts darf - und das sieht aus wie die Regel.
        assertNull(stand.rang(), "wer nie aufgestiegen ist, hat keinen Rang");
        assertNull(stand.erreichtAm());
        assertFalse(stand.aufgestiegen());
        assertEquals(niemand, stand.spieler(),
                "der Spieler muss der sein, der hereingegeben wurde");
    }

    @Test
    @DisplayName("Ein eingetragener Rang steht danach im Stand - mit seinem Zeitpunkt")
    void eingetragenerRangStehtDrin() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.setze(new Rangablage.Antrag(anna, SIEDLER, MITTAGS),
                Rangstand.leer(anna)));

        Rangstand stand = ablage.lies(anna);
        assertEquals(SIEDLER, stand.rang());
        // Der Zeitpunkt ist die Antwort auf "seit wann bist du Siedler" und damit das,
        // was Teil G abrechnet. Eine Ablage, die ihn selbst setzte, gaebe eine andere
        // Antwort als die Regel, die auf der eingespeisten Uhr geurteilt hat.
        assertEquals(MITTAGS, stand.erreichtAm());
        assertTrue(stand.aufgestiegen());
    }

    @Test
    @DisplayName("Der zweite Aufstieg ersetzt den ersten - EINE Zeile je Spieler")
    void zweiterAufstiegErsetztDenErsten() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        setze(ablage, anna, SIEDLER, MITTAGS);
        setze(ablage, anna, BUERGER, SPAETER);

        // DIE VIERTE ZUSAGE. Mit einer Zeile je erreichter Stufe gaebe es zwei Antworten
        // auf "welchen Rang hat er", und die zweite waere nach einer umsortierten Leiter
        // die falsche.
        Rangstand stand = ablage.lies(anna);
        assertEquals(BUERGER, stand.rang());
        assertEquals(SPAETER, stand.erreichtAm(),
                "und der Zeitpunkt ist der des neuen Ranges, nicht der des alten");
    }

    @Test
    @DisplayName("Die Ablage urteilt NICHT ueber die Richtung - sie kennt die Leiter nicht")
    void keineRichtungInDerAblage() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        setze(ablage, anna, BUERGER, MITTAGS);

        // DIE ERSTE ZUSAGE, und sie ist bewusst unbequem: Vom Buerger auf den Siedler
        // ist ein Abstieg, und die Ablage laesst ihn zu. Sie MUSS ihn zulassen - sie
        // kennt die Reihenfolge nicht und raete sonst. Dass es nicht passiert, ist Sache
        // von Rangregel.aufstieg, und das ist die eine Stelle, an der die Leiter steht.
        assertTrue(ablage.setze(new Rangablage.Antrag(anna, SIEDLER, SPAETER),
                ablage.lies(anna)),
                "eine zweite, halbe Fassung der Aufstiegsregel gehoert nicht hierher");
        assertEquals(SIEDLER, ablage.lies(anna).rang());
    }

    @Test
    @DisplayName("Ein veralteter Stand schreibt NICHT - und veraendert nichts")
    void veralteterStandSchreibtNicht() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        Rangstand veraltet = ablage.lies(anna);

        setze(ablage, anna, SIEDLER, MITTAGS);

        // DIE DRITTE ZUSAGE. Der Tageslauf und ein Befehl im selben Augenblick lesen
        // beide denselben leeren Stand; ohne diesen Riegel schrieben beide, und der
        // zweite ueberschriebe den ersten mit einem Rang, der nie zustand.
        assertFalse(ablage.setze(new Rangablage.Antrag(anna, BUERGER, SPAETER), veraltet),
                "der Stand von vorhin gilt nicht mehr");
        Rangstand jetzt = ablage.lies(anna);
        assertEquals(SIEDLER, jetzt.rang(), "und es ist NICHTS passiert");
        assertEquals(MITTAGS, jetzt.erreichtAm());
    }

    @Test
    @DisplayName("Zwei Spieler stehen sich nicht im Weg")
    void zweiSpielerGetrennt() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        setze(ablage, anna, BUERGER, MITTAGS);

        assertEquals(BUERGER, ablage.lies(anna).rang());
        assertNull(ablage.lies(bert).rang(), "bert ist nie aufgestiegen");
    }

    @Test
    @DisplayName("Ein Stand, der einem anderen gehoert, wird abgewiesen")
    void fremderStandWirdAbgewiesen() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        // Ein Wurf und kein false: false hiesse "versuch es mit dem neuen Stand noch
        // einmal", und das koennte nie gelingen. Das hier ist ein Rechenfehler im
        // Aufrufer, und er soll laut sein.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.setze(new Rangablage.Antrag(anna, SIEDLER, MITTAGS),
                        Rangstand.leer(bert)));
    }

    @Test
    @DisplayName("Ohne erwarteten Stand gibt es kein Schreiben")
    void ohneErwartetenStand() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // Ohne ihn waere es lesen-rechnen-schreiben, und das verliert bei zwei
        // gleichzeitigen Zuegen einen.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.setze(new Rangablage.Antrag(anna, SIEDLER, MITTAGS), null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.setze(null, Rangstand.leer(anna)));
    }

    @Test
    @DisplayName("Ein Rang wird kleingeschrieben abgelegt, egal wie er ankommt")
    void grossUndKleinIstDasselbe() {
        Rangablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.setze(new Rangablage.Antrag(anna, "Siedler", MITTAGS),
                Rangstand.leer(anna)));

        // Zwei Schreibweisen desselben Ranges waeren zwei Raenge: Der Vergleich mit dem
        // Schluessel aus der Datei ginge einmal gut und einmal nicht, und der Spieler
        // haette einen Rang, den die Leiter nicht kennt.
        assertEquals(SIEDLER, ablage.lies(anna).rang());
    }

    @Test
    @DisplayName("Ohne Spieler gibt es nichts zu lesen")
    void ohneSpielerKeinLesen() {
        assertThrows(IllegalArgumentException.class, () -> ablage().lies(null));
    }

    /** Ein Aufstieg, der durchgehen MUSS - sonst sagt die folgende Probe nichts. */
    private static void setze(Rangablage ablage, UUID wer, String rang, Instant jetzt) {
        assertTrue(ablage.setze(new Rangablage.Antrag(wer, rang, jetzt),
                ablage.lies(wer)), "die Vorbereitung muss durchgehen: " + rang);
    }
}
