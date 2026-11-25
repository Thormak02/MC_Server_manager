package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede {@link Strafablage} einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link MeldeVertrag} und {@link TeamVertrag}: Die Umsetzung bringt
 * ihre eigene Testklasse mit und erbt diese Pruefungen.
 *
 * <p>Hier stehen alle sieben Versprechen aus dem Klassenkopf von {@link Strafablage}. Das
 * wichtigste ist das FUENFTE, und zwar nicht wegen der Nebenlaeufigkeit, sondern wegen
 * der Richtung: <b>{@link Strafablage#geltende} gibt die, die am laengsten gilt.</b> Eine
 * Ablage, die hier die ERSTE gaebe, liesse einen gebannten Spieler herein, sobald jemand
 * eine zweite, kuerzere Zeile angelegt hat - und niemand sucht den Fehler in der
 * Sortierung einer Abfrage, deren Antwort ja eine Strafe ist.
 */
public abstract class StrafVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    /** Eine Minute spaeter. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(60L);

    /** Zwei Minuten spaeter - fuer die Reihenfolge nach Alter. */
    protected static final Instant NOCH_SPAETER = MITTAGS.plusSeconds(120L);

    /** Eine Stunde - die uebliche Probe-Dauer. */
    protected static final Strafdauer EINE_STUNDE =
            new Strafdauer(Duration.ofHours(1L));

    /** Zehn Minuten - kuerzer als {@link #EINE_STUNDE}. */
    protected static final Strafdauer ZEHN_MINUTEN =
            new Strafdauer(Duration.ofMinutes(10L));

    /** Eine frische, leere Ablage. */
    protected abstract Strafablage ablage();

    @Test
    @DisplayName("Eine gelegte Strafe kommt MIT Nummer zurueck und gilt")
    void gelegteStrafeKommtMitNummer() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "Grief in Altfurt", anna,
                MITTAGS, EINE_STUNDE);

        // DAS ERSTE UND ZWEITE VERSPRECHEN. Ohne die zurueckgegebene Nummer muesste der
        // Aufrufer suchen - und bei zwei gleichzeitigen Strafen gegen denselben Spieler
        // griffe er zur falschen.
        assertTrue(eine.nummer() > 0L, "die Ablage vergibt die Nummer: " + eine.nummer());
        assertEquals(bert, eine.betroffener());
        assertEquals(Strafart.BANN, eine.art());
        assertEquals("Grief in Altfurt", eine.grund());
        assertEquals(Optional.of(anna), eine.verhaengtVon());
        assertEquals(MITTAGS, eine.verhaengtAm(),
                "die Zeit kommt von aussen und nicht aus der Ablage");
        assertEquals(Optional.of(MITTAGS.plusSeconds(3600L)), eine.endet());
        assertFalse(eine.istAufgehoben());
        assertTrue(eine.giltNoch(SPAETER));
    }

    @Test
    @DisplayName("Zwei Strafen bekommen VERSCHIEDENE Nummern")
    void zweiStrafenZweiNummern() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        long erste = ablage.lege(bert, Strafart.RAUSWURF, "erster Vorfall", null,
                MITTAGS, null).nummer();
        long zweite = ablage.lege(bert, Strafart.RAUSWURF, "zweiter Vorfall", null,
                SPAETER, null).nummer();

        // Zwei gleiche Nummern waeren zwei Strafen, von denen die Aufhebung der einen
        // die andere trifft.
        assertTrue(zweite > erste, erste + " dann " + zweite);
    }

    @Test
    @DisplayName("Eine gelegte Strafe ist unter ihrer Nummer wieder zu lesen")
    void gelegteStrafeIstLesbar() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();

        Strafe gelegt = ablage.lege(bert, Strafart.STUMM, "Beleidigung im Chat", anna,
                MITTAGS, ZEHN_MINUTEN);
        Optional<Strafe> gelesen = ablage.lies(gelegt.nummer());

        assertTrue(gelesen.isPresent());
        assertEquals(gelegt, gelesen.get(),
                "was hereingeht, kommt gleich wieder heraus");
    }

    @Test
    @DisplayName("Eine unbekannte Nummer gibt leer und keinen Fehler")
    void unbekannteNummerGibtLeer() {
        // DAS VIERTE VERSPRECHEN: Ein Moderator vertippt sich, und das ist eine
        // Antwort, kein Absturz.
        assertTrue(ablage().lies(9999L).isEmpty());
    }

    @Test
    @DisplayName("Die KONSOLE als Absender wird als null gespeichert und gelesen")
    void konsoleIstNull() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "von der Konsole", null,
                MITTAGS, EINE_STUNDE);

        // Ohne diese Zusage waere eine Strafe von der Konsole eine ohne Absender - und
        // in der Liste stuende eine leere Stelle, wo "Konsole" stehen soll.
        assertTrue(eine.verhaengtVon().isEmpty());
        assertTrue(ablage.lies(eine.nummer()).orElseThrow().verhaengtVon().isEmpty());
    }

    @Test
    @DisplayName("Ein RAUSWURF gilt nie noch - auch nicht im selben Augenblick")
    void rauswurfGiltNie() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.RAUSWURF, "Spam", null, MITTAGS, null);

        assertFalse(eine.giltNoch(MITTAGS),
                "ein Rauswurf ist vorbei, sobald er geschieht");
        assertTrue(ablage.geltende(bert, Strafart.RAUSWURF, MITTAGS).isEmpty(),
                "nach einem geltenden Rauswurf zu fragen ergibt immer leer");
        // Und zwar auch mit einem Zeitpunkt VOR der Tat. In der Tabelle steht fuer
        // einen Rauswurf das Ende gleich dem Anfang, und eine Abfrage, die nur
        // "Ende > jetzt" prueft, gaebe ihn dann als geltend zurueck - die Fassung im
        // Arbeitsspeicher fragt nach der ART und antwortete anders. Genau solche
        // Unterschiede zwischen den Fassungen soll dieser Vertrag finden.
        assertTrue(ablage.geltende(bert, Strafart.RAUSWURF,
                        MITTAGS.minusSeconds(600L)).isEmpty(),
                "auch rueckwaerts gefragt gilt ein Rauswurf nie");
        assertTrue(ablage.geltendeAlle(Strafart.RAUSWURF, 10,
                        MITTAGS.minusSeconds(600L)).isEmpty(),
                "und in keiner Liste der Geltenden");
    }

    @Test
    @DisplayName("Eine abgelaufene Strafe gilt nicht mehr")
    void abgelaufeneGiltNicht() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        ablage.lege(bert, Strafart.STUMM, "zehn Minuten", null, MITTAGS, ZEHN_MINUTEN);

        assertTrue(ablage.geltende(bert, Strafart.STUMM, SPAETER).isPresent(),
                "nach einer Minute gilt sie noch");
        assertTrue(ablage.geltende(bert, Strafart.STUMM,
                        MITTAGS.plusSeconds(601L)).isEmpty(),
                "nach zehn Minuten und einer Sekunde nicht mehr");
    }

    @Test
    @DisplayName("Eine UNBEGRENZTE Strafe gilt auch in zehn Jahren noch")
    void unbegrenzteGiltImmer() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "dauerhaft", null, MITTAGS,
                Strafdauer.UNBEGRENZT);

        assertTrue(eine.unbegrenzt());
        assertTrue(eine.endet().isEmpty(), "leer heisst hier: kein Ende");
        assertTrue(ablage.geltende(bert, Strafart.BANN,
                MITTAGS.plus(Duration.ofDays(3650L))).isPresent());
    }

    @Test
    @DisplayName("geltende gibt die, die am LAENGSTEN gilt - nicht die erste")
    void geltendeGibtDieLaengste() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        // Zuerst die kurze, dann die lange: Eine Ablage, die die erste gefundene gibt,
        // antwortet mit der kurzen - und laesst den Spieler nach zehn Minuten herein,
        // obwohl eine Stunde gegen ihn steht.
        ablage.lege(bert, Strafart.BANN, "zehn Minuten", null, MITTAGS, ZEHN_MINUTEN);
        ablage.lege(bert, Strafart.BANN, "eine Stunde", null, MITTAGS, EINE_STUNDE);

        Optional<Strafe> gefunden = ablage.geltende(bert, Strafart.BANN, SPAETER);

        // DAS FUENFTE VERSPRECHEN.
        assertTrue(gefunden.isPresent());
        assertEquals("eine Stunde", gefunden.get().grund(),
                "von zwei geltenden ist die laengere die Wahrheit");
    }

    @Test
    @DisplayName("Eine UNBEGRENZTE schlaegt jede befristete")
    void unbegrenzteSchlaegtBefristete() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        // Die unbegrenzte ZUERST, damit nicht die Einfuegereihenfolge entscheidet.
        ablage.lege(bert, Strafart.BANN, "ohne Ende", null, MITTAGS,
                Strafdauer.UNBEGRENZT);
        ablage.lege(bert, Strafart.BANN, "eine Stunde", null, MITTAGS, EINE_STUNDE);

        assertEquals("ohne Ende",
                ablage.geltende(bert, Strafart.BANN, SPAETER).orElseThrow().grund(),
                "eine Strafe ohne Ende hat kein Ende, mit dem sie sich vergleichen"
                        + " liesse - sie gewinnt");
    }

    @Test
    @DisplayName("geltende trennt die ARTEN und die SPIELER")
    void geltendeTrenntArtenUndSpieler() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID clara = UUID.randomUUID();

        ablage.lege(bert, Strafart.STUMM, "stumm gestellt", null, MITTAGS, EINE_STUNDE);

        assertTrue(ablage.geltende(bert, Strafart.STUMM, SPAETER).isPresent());
        assertTrue(ablage.geltende(bert, Strafart.BANN, SPAETER).isEmpty(),
                "stumm ist nicht gebannt - sonst sperrt eine Stummschaltung die Tuer");
        assertTrue(ablage.geltende(clara, Strafart.STUMM, SPAETER).isEmpty(),
                "die Strafe des einen trifft nicht den anderen");
    }

    @Test
    @DisplayName("Eine aufgehobene Strafe gilt nicht mehr, steht aber noch da")
    void aufgehobeneGiltNichtStehtAberDa() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "Grief", anna, MITTAGS,
                EINE_STUNDE);
        assertTrue(ablage.hebeAuf(eine.aufgehoben(anna, "Einspruch angenommen",
                SPAETER)));

        assertTrue(ablage.geltende(bert, Strafart.BANN, SPAETER).isEmpty(),
                "aufgehoben heisst: gilt nicht mehr");
        Strafe gelesen = ablage.lies(eine.nummer()).orElseThrow();
        // DAS PROTOKOLL: Gerade die zurueckgenommene Strafe ist die, nach der ein
        // zweiter Moderator fragt.
        assertTrue(gelesen.istAufgehoben());
        assertEquals(Optional.of(SPAETER), gelesen.aufhebung());
        assertEquals(Optional.of(anna), gelesen.aufhebungDurch());
        assertEquals("Einspruch angenommen", gelesen.aufhebungsgrund());
        assertEquals("Grief", gelesen.grund(), "der urspruengliche Grund bleibt stehen");
    }

    @Test
    @DisplayName("Ein ZWEITES Aufheben gibt false und laesst den ersten Grund stehen")
    void zweitesAufhebenGibtFalse() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        UUID clara = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "Grief", anna, MITTAGS,
                EINE_STUNDE);
        assertTrue(ablage.hebeAuf(eine.aufgehoben(anna, "erster Grund", SPAETER)));

        // DAS DRITTE VERSPRECHEN: Der zweite verliert und erfaehrt es. Ohne diesen
        // Riegel stehen zwei Aufhebungsgruende uebereinander, und der erste - der
        // richtige - ist verloren.
        assertFalse(ablage.hebeAuf(eine.aufgehoben(clara, "zweiter Grund",
                NOCH_SPAETER)));
        Strafe gelesen = ablage.lies(eine.nummer()).orElseThrow();
        assertEquals("erster Grund", gelesen.aufhebungsgrund());
        assertEquals(Optional.of(anna), gelesen.aufhebungDurch());
    }

    @Test
    @DisplayName("Eine Fassung OHNE Aufhebung geht nicht durch hebeAuf")
    void fassungOhneAufhebungWirft() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        Strafe eine = ablage.lege(bert, Strafart.BANN, "Grief", null, MITTAGS,
                EINE_STUNDE);

        // Ohne diesen Riegel waere hebeAuf ein allgemeines Schreiben, mit dem sich der
        // GRUND einer stehenden Strafe nachtraeglich aendern liesse - und das Protokoll
        // waere eines, in dem man radieren kann.
        assertThrows(IllegalArgumentException.class, () -> ablage.hebeAuf(eine));
    }

    @Test
    @DisplayName("Eine unbekannte Nummer aufzuheben wirft - und gibt nicht false")
    void unbekannteAufhebenWirft() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        Strafe erfunden = new Strafe(9999L, bert, Strafart.BANN, "erfunden", null,
                MITTAGS, MITTAGS.plusSeconds(3600L), SPAETER, null, "Grund der"
                        + " Aufhebung");

        // Ein Wurf und kein false: false hiesse "versuch es noch einmal", und das
        // koennte nie gelingen.
        assertThrows(IllegalArgumentException.class, () -> ablage.hebeAuf(erfunden));
    }

    @Test
    @DisplayName("Die Vorgeschichte gibt die NEUESTEN zuerst - alle Arten, alle Staende")
    void vorgeschichteNeuesteZuerst() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        ablage.lege(bert, Strafart.RAUSWURF, "erster", null, MITTAGS, null);
        ablage.lege(bert, Strafart.STUMM, "zweiter", null, SPAETER, ZEHN_MINUTEN);
        Strafe dritte = ablage.lege(bert, Strafart.BANN, "dritter", null, NOCH_SPAETER,
                EINE_STUNDE);
        ablage.hebeAuf(dritte.aufgehoben(null, "zurueckgenommen",
                NOCH_SPAETER.plusSeconds(10L)));

        List<Strafe> verlauf = ablage.von(bert, 10);

        // DAS SECHSTE VERSPRECHEN: Bei einer Vorgeschichte ist die letzte Tat die, nach
        // der gefragt wird - anders als bei den Meldungen, wo die aelteste am laengsten
        // wartet.
        assertEquals(3, verlauf.size());
        assertEquals("dritter", verlauf.get(0).grund());
        assertEquals("zweiter", verlauf.get(1).grund());
        assertEquals("erster", verlauf.get(2).grund());
        assertTrue(verlauf.get(0).istAufgehoben(),
                "auch die aufgehobene steht in der Vorgeschichte");
    }

    @Test
    @DisplayName("Die Vorgeschichte haelt die Hoechstzahl ein - und nimmt die neuesten")
    void vorgeschichteHaeltGrenze() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        for (int lauf = 0; lauf < 5; lauf++) {
            ablage.lege(bert, Strafart.RAUSWURF, "Vorfall " + lauf, null,
                    MITTAGS.plusSeconds(lauf * 60L), null);
        }

        List<Strafe> verlauf = ablage.von(bert, 2);

        assertEquals(2, verlauf.size());
        // Die NEUESTEN und nicht die ersten zwei: Eine Begrenzung, die vom alten Ende
        // nimmt, zeigt die Vorgeschichte, die niemanden mehr interessiert.
        assertEquals("Vorfall 4", verlauf.get(0).grund());
        assertEquals("Vorfall 3", verlauf.get(1).grund());
    }

    @Test
    @DisplayName("Die Vorgeschichte trennt die Spieler")
    void vorgeschichteTrenntSpieler() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID clara = UUID.randomUUID();

        ablage.lege(bert, Strafart.RAUSWURF, "Bert war das", null, MITTAGS, null);
        ablage.lege(clara, Strafart.RAUSWURF, "Clara war das", null, SPAETER, null);

        assertEquals(1, ablage.von(bert, 10).size());
        assertEquals("Bert war das", ablage.von(bert, 10).get(0).grund());
    }

    @Test
    @DisplayName("Die Bannliste zeigt nur die GELTENDEN Banne, die neuesten zuerst")
    void bannlisteZeigtGeltende() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();
        UUID clara = UUID.randomUUID();
        UUID dora = UUID.randomUUID();
        UUID erik = UUID.randomUUID();

        ablage.lege(bert, Strafart.BANN, "gilt noch", null, MITTAGS, EINE_STUNDE);
        ablage.lege(clara, Strafart.BANN, "abgelaufen", null, MITTAGS, ZEHN_MINUTEN);
        Strafe frei = ablage.lege(dora, Strafart.BANN, "wird aufgehoben", null, MITTAGS,
                EINE_STUNDE);
        ablage.hebeAuf(frei.aufgehoben(null, "Einspruch", SPAETER));
        ablage.lege(erik, Strafart.STUMM, "nur stumm", null, MITTAGS, EINE_STUNDE);

        List<Strafe> liste = ablage.geltendeAlle(Strafart.BANN, 10,
                MITTAGS.plusSeconds(1800L));

        // Plan 12c.4: die oeffentliche Bannliste. Was abgelaufen oder aufgehoben ist,
        // gehoert nicht hinein - eine Liste, die Entsperrte weiter nennt, ist ein
        // Pranger und keine Auskunft. Und eine Stummschaltung ist kein Bann.
        assertEquals(1, liste.size(), "nur der eine geltende Bann");
        assertEquals("gilt noch", liste.get(0).grund());
    }

    @Test
    @DisplayName("zaehle zaehlt ALLE Strafen der Art - auch abgelaufene und aufgehobene")
    void zaehleZaehltAlle() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        ablage.lege(bert, Strafart.RAUSWURF, "erster", null, MITTAGS, null);
        ablage.lege(bert, Strafart.RAUSWURF, "zweiter", null, SPAETER, null);
        Strafe dritte = ablage.lege(bert, Strafart.BANN, "Bann", null, NOCH_SPAETER,
                EINE_STUNDE);
        ablage.hebeAuf(dritte.aufgehoben(null, "Einspruch",
                NOCH_SPAETER.plusSeconds(10L)));

        // Die Zahl, die ein Moderator liest, bevor er entscheidet: "dritter Rauswurf".
        // Eine Zaehlung, die nur die geltenden nimmt, saehe bei einem
        // Wiederholungstaeter immer eine 0 - und genau dort zaehlt sie.
        assertEquals(2, ablage.zaehle(bert, Strafart.RAUSWURF));
        assertEquals(1, ablage.zaehle(bert, Strafart.BANN),
                "auch die aufgehobene zaehlt mit");
        assertEquals(0, ablage.zaehle(bert, Strafart.STUMM));
    }

    @Test
    @DisplayName("Die Ablage urteilt NICHT - sie nimmt jede Dauer und jeden Absender")
    void ablageUrteiltNicht() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        // DAS SIEBTE VERSPRECHEN: Kein Rechteriegel, keine Hoechstdauer. Das tut das
        // Strafbuch - und zwei Riegel fuer denselben Fall machen sich gegenseitig
        // unpruefbar.
        Strafe lang = ablage.lege(bert, Strafart.BANN, "zehn Jahre", null, MITTAGS,
                new Strafdauer(Duration.ofDays(3650L)));

        assertTrue(lang.giltNoch(SPAETER));
        assertTrue(ablage.lies(lang.nummer()).isPresent());
    }

    @Test
    @DisplayName("Eine Liste von null Zeilen ist keine Liste")
    void nullZeilenWirft() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.von(bert, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.geltendeAlle(Strafart.BANN, 0, MITTAGS));
    }

    @Test
    @DisplayName("Ohne Spieler, Art oder Zeitpunkt gibt es keine Frage")
    void fehlendeAngabenWerfen() {
        Strafablage ablage = ablage();
        UUID bert = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> ablage.geltende(null, Strafart.BANN, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.geltende(bert, null, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.geltende(bert, Strafart.BANN, null));
        assertThrows(IllegalArgumentException.class, () -> ablage.von(null, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.zaehle(bert, null));
    }
}
