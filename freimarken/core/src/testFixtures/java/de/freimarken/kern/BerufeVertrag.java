package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Berufsablage einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link KasseVertrag}, {@link BesitzLagerVertrag} und
 * {@link SpielzeitVertrag}: Die Umsetzung bringt ihre eigene Testklasse mit und erbt diese
 * Pruefungen. Dadurch wird eine Umsetzung, die sich anders verhaelt als die andere, in
 * einem Testlauf sichtbar und nicht im Betrieb.
 *
 * <p>Hier stehen ALLE fuenf Zusagen aus dem Klassenkopf von {@link Berufsablage}. Die
 * wichtigste ist die vierte: <b>Die Sperrfrist haengt am ABLEGEN und nicht am Waehlen.</b>
 * Haengte sie am Waehlen, sperrte die erste Wahl einen neuen Spieler einen Tag lang aus
 * seinem zweiten Grundberuf - kein Absturz, kein Eintrag im Protokoll, nur ein Spieler,
 * der nicht weiterkommt. Genau so eine Luecke ist der Grund, aus dem ein geteilter Vertrag
 * jede Zusage des Kopfes selbst prueft.
 *
 * <p>Die dritte Zusage - <b>ein Verlassen loescht nicht, es legt ab</b> - laesst sich hier
 * nur an ihrer WIRKUNG pruefen: {@link Berufsstand#letztesAblegen} muss nach einem
 * Verlassen stehen und darf nicht mit der Zeile verschwinden. Dass in MariaDB wirklich
 * eine Zeile stehenbleibt, prueft {@code MariaBerufeTest} mit rohem SQL - das kann ein
 * umsetzungsfreier Vertrag nicht.
 */
public abstract class BerufeVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    /**
     * Eine Stunde spaeter - fuer die Frage, ob der Zeitpunkt von aussen kommt.
     *
     * <p><b>Eine Probe, die eine REIHENFOLGE behauptet, muss zwei verschiedene Zeitpunkte
     * nehmen.</b> Die Reihenfolge kommt in MariaDB aus der Spalte {@code geaendert_am};
     * zwei Wahlen mit demselben Zeitpunkt sind dort nicht unterscheidbar, und die Tabelle
     * gibt sie dann nach dem Namen sortiert zurueck. Im Betrieb kann das nicht vorkommen -
     * zwei Wahlen in derselben Millisekunde schliesst die Sperre aus -, in einer Probe mit
     * eingespeister Uhr schon.</p>
     */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    /** Zwei Berufe, deren Rollen die Ablage NICHT kennt und nicht kennen darf. */
    protected static final String BERGMANN = "bergmann";

    protected static final String STEINMETZ = "steinmetz";

    /** Eine frische, leere Ablage. */
    protected abstract Berufsablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler haelt nichts - und hat keine Frist")
    void unbekannterHaeltNichts() {
        UUID niemand = UUID.randomUUID();

        Berufsstand stand = ablage().lies(niemand);

        assertEquals(List.of(), stand.berufe());
        assertNull(stand.letztesAblegen(), "wer nie abgelegt hat, hat keine Frist");
        assertTrue(stand.nochNieGewaehlt());
        assertEquals(niemand, stand.spieler(), "der Spieler muss der sein, der"
                + " hereingegeben wurde");
    }

    @Test
    @DisplayName("Ein gewaehlter Beruf steht danach im Stand")
    void gewaehlterBerufStehtDrin() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.waehle(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                Berufsstand.leer(anna)));

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe());
    }

    @Test
    @DisplayName("Die Reihenfolge der Wahl bleibt erhalten")
    void reihenfolgeDerWahlBleibt() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // Spaeter gewaehlt steht hier ZUERST, und darum in dieser Richtung: bergmann
        // vor steinmetz waere zugleich die alphabetische Reihenfolge, und ein Sortieren
        // in der Ablage waere dieser Probe nicht aufgefallen.
        waehle(ablage, anna, STEINMETZ, MITTAGS);
        waehle(ablage, anna, BERGMANN, SPAETER);

        // Sie ist das, was /beruf stand zeigt. Alphabetisch sortiert waere eine zweite
        // Entscheidung, die dem Spieler niemand gesagt hat.
        assertEquals(List.of(STEINMETZ, BERGMANN), ablage.lies(anna).berufe());
    }

    @Test
    @DisplayName("Die Sperrfrist haengt am Ablegen, nicht am Waehlen")
    void dieFristHaengtAmAblegen() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        waehle(ablage, anna, BERGMANN, MITTAGS);
        waehle(ablage, anna, STEINMETZ, SPAETER);

        // Die vierte Zusage. Stuende hier ein Zeitpunkt, waere die zweite Wahl eines
        // Neulings einen Tag lang gesperrt - und die Absage wuesste nicht einmal, warum.
        assertNull(ablage.lies(anna).letztesAblegen(),
                "zweimal gewaehlt, nie abgelegt: keine Frist");
    }

    @Test
    @DisplayName("Ein Verlassen nimmt den Beruf heraus UND setzt die Frist")
    void verlassenNimmtHerausUndSetztDieFrist() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);

        assertTrue(ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, SPAETER),
                ablage.lies(anna)));

        Berufsstand danach = ablage.lies(anna);
        assertEquals(List.of(), danach.berufe(), "er haelt ihn nicht mehr");
        // Die dritte Zusage an ihrer Wirkung: Ein DELETE liesse den Zeitpunkt mit der
        // Zeile verschwinden, und Aufgeben-und-Warten waere der Weg um Gebuehr UND Frist
        // herum.
        assertEquals(SPAETER, danach.letztesAblegen(),
                "der Zeitpunkt des Ablegens IST die Sperrfrist");
        assertFalse(danach.nochNieGewaehlt(), "eine leere Liste mit Frist ist nicht"
                + " dasselbe wie ein Neuling");
    }

    @Test
    @DisplayName("Der Zeitpunkt kommt von aussen und nicht aus einer eigenen Uhr")
    void derZeitpunktKommtVonAussen() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);

        // Die fuenfte Zusage. Mit eigener Uhr stuende hier der Augenblick des Laufs, und
        // diese Probe liesse sich nur mit Warten schreiben.
        ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                ablage.lies(anna));

        assertEquals(MITTAGS, ablage.lies(anna).letztesAblegen());
    }

    @Test
    @DisplayName("Ein veralteter Stand laesst nichts geschehen - beim Waehlen")
    void veralteterStandWaehltNicht() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        Berufsstand veraltet = ablage.lies(anna);
        waehle(ablage, anna, BERGMANN, MITTAGS);

        // Die zweite Zusage. Ohne sie ergaeben zwei /beruf waehlen in derselben Sekunde
        // drei Grundberufe: Beide lesen den leeren Stand, beide rechnen einen Platz frei,
        // beide schreiben.
        assertFalse(ablage.waehle(new Berufsablage.Antrag(anna, STEINMETZ, SPAETER),
                veraltet), "der erwartete Stand gilt nicht mehr");

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe(),
                "und es ist NICHTS passiert - kein halber Zug");
    }

    @Test
    @DisplayName("Ein veralteter Stand laesst nichts geschehen - beim Verlassen")
    void veralteterStandVerlaesstNicht() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);
        waehle(ablage, anna, STEINMETZ, MITTAGS);
        Berufsstand veraltet = ablage.lies(anna);
        ablage.verlasse(new Berufsablage.Antrag(anna, STEINMETZ, MITTAGS), veraltet);

        // Derselbe Stand ein zweites Mal: Jetzt ist er veraltet, weil das erste Verlassen
        // ihn geaendert hat. Ohne diese Pruefung zahlte ein Spieler zweimal.
        assertFalse(ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, SPAETER),
                veraltet));

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe(),
                "der Bergmann steht noch - das zweite Verlassen hat nicht gegriffen");
    }

    @Test
    @DisplayName("Gleiche Berufe, andere Frist - der Stand gilt trotzdem nicht mehr")
    void gleicheBerufeAndereFrist() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);
        Berufsstand veraltet = ablage.lies(anna);
        ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                ablage.lies(anna));
        waehle(ablage, anna, BERGMANN, MITTAGS);

        // Jetzt stehen in beiden Staenden dieselben Berufe - nur traegt der eine eine
        // Frist und der andere nicht. Verglich die Ablage nur die BERUFE, ginge dieses
        // Verlassen durch, und der Spieler haette seine Sperrfrist uebersprungen: Genau
        // das ist der Grund, aus dem der ganze Stand verglichen wird.
        assertFalse(ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, SPAETER),
                veraltet));

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe(),
                "und es ist nichts passiert");
        assertEquals(MITTAGS, ablage.lies(anna).letztesAblegen(),
                "auch die Frist steht unveraendert");
    }

    @Test
    @DisplayName("Ein abgelegter Beruf laesst sich wieder waehlen")
    void abgelegterBerufLaesstSichWiederWaehlen() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);
        ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                ablage.lies(anna));

        assertTrue(ablage.waehle(new Berufsablage.Antrag(anna, BERGMANN, SPAETER),
                ablage.lies(anna)));

        // Die abgelegte Zeile bleibt in der Datenbank stehen - der Stand muss ihn
        // trotzdem als aktiv zeigen. Eine Umsetzung, die beim Wiederwaehlen an ihrem
        // eigenen Primaerschluessel scheitert, faellt hier auf.
        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe());
        assertNotNull(ablage.lies(anna).letztesAblegen(),
                "die Frist aus dem Ablegen bleibt - sie verschwindet nicht durch eine"
                        + " neue Wahl");
    }

    @Test
    @DisplayName("Zweimal denselben Beruf waehlen ist kein Fehlschlag")
    void zweimalDenselbenWaehlen() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);

        // KEIN false: Der erwartete Stand gilt, und die Ablage steht danach so, wie der
        // Aufrufer es wollte. Die Regel hat diesen Fall vorher als SCHON_GEHALTEN
        // abgewiesen; ein false hier meldete einen Erfolg als Wettlauf, und der Befehl
        // schickte dem Spieler die falsche Absage.
        assertTrue(ablage.waehle(new Berufsablage.Antrag(anna, BERGMANN, SPAETER),
                ablage.lies(anna)));

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe(),
                "und zwar genau einmal");
    }

    @Test
    @DisplayName("Ein schon gehaltener Beruf ruecht in der Reihenfolge nicht nach vorn")
    void zweiteWahlAendertDieReihenfolgeNicht() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, STEINMETZ, MITTAGS);
        waehle(ablage, anna, BERGMANN, SPAETER);

        // Dieselbe Wahl ein zweites Mal, mit einem noch spaeteren Zeitpunkt. Eine Ablage,
        // die den Zeitpunkt dabei fortschreibt, dreht damit die Reihenfolge - und
        // /beruf stand zeigt eine andere als die, in der gewaehlt wurde. Kein Absturz,
        // keine Meldung, nur eine Liste, die niemand nachvollziehen kann.
        waehle(ablage, anna, STEINMETZ, SPAETER.plusSeconds(3600L));

        assertEquals(List.of(STEINMETZ, BERGMANN), ablage.lies(anna).berufe());
    }

    @Test
    @DisplayName("Zwei Spieler stoeren sich nicht")
    void zweiSpielerStoerenSichNicht() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        waehle(ablage, anna, BERGMANN, MITTAGS);
        ablage.verlasse(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                ablage.lies(anna));
        waehle(ablage, bert, BERGMANN, MITTAGS);

        assertEquals(List.of(BERGMANN), ablage.lies(bert).berufe());
        assertNull(ablage.lies(bert).letztesAblegen(),
                "die Frist von Anna ist nicht die von Bert");
    }

    @Test
    @DisplayName("Ein Verlassen, das der erwartete Stand nicht deckt, ist ein Rechenfehler")
    void verlassenBrauchtEinenStandDerDenBerufHaelt() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        waehle(ablage, anna, BERGMANN, MITTAGS);

        // Kein false: Der Stand ist nicht veraltet, er deckt die Tat nur nicht. Ein false
        // schickte den Aufrufer in eine Wiederholung, die nie gelingen kann - und eine
        // Tabelle koennte dem Beruf, den es nicht gibt, ohnehin keine Frist geben.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.verlasse(new Berufsablage.Antrag(anna, STEINMETZ, SPAETER),
                        ablage.lies(anna)));

        assertEquals(List.of(BERGMANN), ablage.lies(anna).berufe(), "und nichts geschah");
        assertNull(ablage.lies(anna).letztesAblegen(), "auch keine Frist");
    }

    @Test
    @DisplayName("Ein Antrag fuer einen anderen Spieler als der Stand wirft")
    void antragUndStandMuessenZusammenpassen() {
        Berufsablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        // Kein false, sondern ein Wurf: Ein false hiesse "der Stand ist veraltet", und
        // der Aufrufer wuerde es erneut versuchen. Hier ist nicht der Stand veraltet,
        // sondern der Aufruf falsch zusammengesetzt - und das ist ein Bauunfall.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.waehle(new Berufsablage.Antrag(anna, BERGMANN, MITTAGS),
                        Berufsstand.leer(bert)));
    }

    /** Waehlt und besteht darauf, dass es geklappt hat - fuer die Vorbereitung. */
    private static void waehle(Berufsablage ablage, UUID wer, String beruf,
                               Instant jetzt) {
        assertTrue(ablage.waehle(new Berufsablage.Antrag(wer, beruf, jetzt),
                        ablage.lies(wer)),
                "die Vorbereitung muss durchgehen: " + beruf);
    }
}
