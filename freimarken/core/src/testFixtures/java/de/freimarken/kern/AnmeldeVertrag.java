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
 * Was jede Anmeldeablage einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link RangVertrag} und {@link KasseVertrag}: Die Umsetzung bringt
 * ihre eigene Testklasse mit und erbt diese Pruefungen. Dadurch wird eine Umsetzung, die
 * sich anders verhaelt als die andere, in einem Testlauf sichtbar und nicht im Betrieb.
 *
 * <p>Hier stehen alle vier Zusagen aus dem Klassenkopf von {@link Anmeldeablage}. Die
 * teuerste ist die erste: <b>Die Ablage weiss nicht, welche Fassung gilt.</b> Eine Ablage,
 * die beim Lesen die geltende Fassung selbst einsetzte, meldete nach einer Regelaenderung
 * jeden als zugestimmt - und das ist genau der Nachweis, den es dann nicht mehr gibt.
 *
 * <p>Die dritte Zusage - <b>eine Zeile je Spieler</b> - laesst sich hier nur an ihrer
 * WIRKUNG pruefen: Nach zwei Annahmen gilt die zweite, und die erste ist nicht mehr zu
 * lesen. Dass in MariaDB wirklich nur EINE Zeile steht, prueft
 * {@code MariaAnmeldungenTest} mit rohem SQL - das kann ein umsetzungsfreier Vertrag
 * nicht.
 */
public abstract class AnmeldeVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-21T12:00:00Z");

    /** Eine Stunde spaeter - fuer die Frage, ob der Zeitpunkt von aussen kommt. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    /** Zwei Fassungen, deren Reihenfolge die Ablage nicht kennt und nicht kennen darf. */
    protected static final String ERSTE = "2026-11-21";

    protected static final String ZWEITE = "2026-12-01";

    /** Eine frische, leere Ablage. */
    protected abstract Anmeldeablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat nichts angenommen - und das ist kein Fehler")
    void unbekannterHatNichtsAngenommen() {
        UUID niemand = UUID.randomUUID();

        Anmeldestand stand = ablage().lies(niemand);

        // DIE ZWEITE ZUSAGE. Ein null oder ein Wurf waere im Betrieb ein Spieler, bei
        // dem der Beitritt abbricht - und zwar beim ersten.
        assertNull(stand.fassung(), "wer nie angenommen hat, hat keine Fassung");
        assertNull(stand.angenommenAm());
        assertFalse(stand.jeAngenommen());
        assertFalse(stand.hatAngenommen(ERSTE));
        assertEquals(niemand, stand.spieler(),
                "der Spieler muss der sein, der hereingegeben wurde");
    }

    @Test
    @DisplayName("Eine Annahme steht danach im Stand - mit ihrem Zeitpunkt")
    void annahmeStehtDrin() {
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS)),
                "die erste Annahme ist eine Aenderung");

        Anmeldestand stand = ablage.lies(anna);
        assertEquals(ERSTE, stand.fassung());
        assertEquals(MITTAGS, stand.angenommenAm(),
                "der Zeitpunkt muss der sein, der hereingegeben wurde - die Ablage hat"
                        + " keine eigene Uhr");
        assertTrue(stand.hatAngenommen(ERSTE));
        assertTrue(stand.jeAngenommen());
    }

    @Test
    @DisplayName("Dieselbe Fassung zweimal annehmen aendert nichts - auch nicht die Zeit")
    void zweimalDieselbeFassung() {
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        assertFalse(ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, SPAETER)),
                "dieselbe Fassung ein zweites Mal ist keine neue Zustimmung");

        // DER ZEITPUNKT BLEIBT DER ERSTE. Ihn zu ueberschreiben machte den Nachweis
        // juenger, als er ist - und damit falsch: Zugestimmt hat er mittags.
        assertEquals(MITTAGS, ablage.lies(anna).angenommenAm(),
                "der Zeitpunkt der ERSTEN Zustimmung muss stehen bleiben");
    }

    @Test
    @DisplayName("Eine NEUE Fassung tritt an die Stelle der alten")
    void neueFassungTrittAnDieStelle() {
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        assertTrue(ablage.nimmAn(new Anmeldeablage.Antrag(anna, ZWEITE, SPAETER)),
                "eine andere Fassung ist eine Aenderung");

        // DIE DRITTE ZUSAGE an ihrer Wirkung: eine Zeile je Spieler, kein Verlauf.
        Anmeldestand stand = ablage.lies(anna);
        assertEquals(ZWEITE, stand.fassung());
        assertEquals(SPAETER, stand.angenommenAm());
        assertFalse(stand.hatAngenommen(ERSTE),
                "die alte Fassung gilt danach nicht mehr als angenommen");
        assertTrue(stand.hatAngenommen(ZWEITE));
    }

    @Test
    @DisplayName("Die Ablage setzt NIE die geltende Fassung selbst ein")
    void ablageKenntDieGeltendeFassungNicht() {
        // DIE ERSTE ZUSAGE, und sie ist die teuerste. Setzte die Ablage beim Lesen die
        // geltende Fassung ein, meldete sie nach einer Regelaenderung jeden als
        // zugestimmt - und genau dann gibt es den Nachweis nicht mehr.
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        Anmeldestand stand = ablage.lies(anna);

        assertEquals(ERSTE, stand.fassung(), "abgelegt ist, was angenommen WURDE");
        assertFalse(stand.hatAngenommen(ZWEITE), "gegen eine andere geltende Fassung"
                + " gemessen, hat er NICHT angenommen");
    }

    @Test
    @DisplayName("Zwei Spieler stehen einander nicht im Weg")
    void zweiSpielerGetrennt() {
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        assertTrue(ablage.lies(anna).hatAngenommen(ERSTE));
        assertFalse(ablage.lies(bert).jeAngenommen(),
                "Berts Stand darf von Annas Annahme nichts wissen");
    }

    @Test
    @DisplayName("Grossgeschriebene Fassungen werden kleingeschrieben abgelegt")
    void fassungWirdKleingeschrieben() {
        // EINMAL normalisiert, und zwar im Antrag. Normalisierte die Ablage ein zweites
        // Mal, bogen zwei Stellen denselben Namen zurecht - und eines Tages verschieden.
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.nimmAn(new Anmeldeablage.Antrag(anna, "  2026-AGB2  ", MITTAGS));

        assertEquals("2026-agb2", ablage.lies(anna).fassung());
        assertTrue(ablage.lies(anna).hatAngenommen("2026-AGB2"),
                "die Frage normalisiert ebenso - sonst haengt die Antwort an der"
                        + " Schreibweise in der Einstellungsdatei");
    }

    @Test
    @DisplayName("Ohne Spieler, ohne Zeitpunkt und ohne Fassung wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.lies(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.nimmAn(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldeablage.Antrag(null, ERSTE, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldeablage.Antrag(anna, ERSTE, null));
        // Eine Annahme ohne Fassung waere eine Zustimmung zu nichts.
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldeablage.Antrag(anna, " ", MITTAGS));
    }

    @Test
    @DisplayName("Eine zu lange und eine unerlaubte Fassung werden abgewiesen")
    void unbrauchbareFassungWirdAbgewiesen() {
        // HIER UND NICHT ERST IN DER DATENBANK: Ohne diese Pruefung nahm die
        // Speicherfassung an, was MariaDB abweist - der fluechtige Betrieb verhielte
        // sich anders als der echte, und gefunden wuerde es von einem Spieler.
        Anmeldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        String zuLang = "x".repeat(Anmeldestand.HOECHSTE_FASSUNGSLAENGE + 1);

        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimmAn(new Anmeldeablage.Antrag(anna, zuLang, MITTAGS)));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.nimmAn(new Anmeldeablage.Antrag(anna, "mit leerzeichen",
                        MITTAGS)));

        // UND DIE GEGENRICHTUNG: genau die Hoechstlaenge geht durch. Ohne diese Haelfte
        // waere auch eine Ablage gruen, die JEDE Fassung abweist.
        String genauLang = "x".repeat(Anmeldestand.HOECHSTE_FASSUNGSLAENGE);
        assertTrue(ablage.nimmAn(new Anmeldeablage.Antrag(anna, genauLang, MITTAGS)));
        assertEquals(genauLang, ablage.lies(anna).fassung());
    }

    @Test
    @DisplayName("Die Frage nach der geltenden Fassung braucht eine")
    void frageBrauchtEineGeltendeFassung() {
        // Ein null oder ein leerer Wert hiesse "gegen nichts gemessen", und die Antwort
        // darauf koennte nur falsch sein. Der Riegel steht im Stand, weil ihn jeder
        // Leser derselben Frage braucht.
        Anmeldestand stand = Anmeldestand.leer(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> stand.hatAngenommen(null));
        assertThrows(IllegalArgumentException.class, () -> stand.hatAngenommen(" "));
    }
}
