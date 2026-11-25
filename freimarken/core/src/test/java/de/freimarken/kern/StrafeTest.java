package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Datensatz einer Strafe - und die Zustaende, die er nicht halten darf.
 *
 * <p>Die Pruefungen im Konstruktor sind der Grund, aus dem die Ablagen keine eigenen
 * brauchen: Was hier nicht entstehen kann, kann auch nicht gespeichert werden.
 */
class StrafeTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant SPAETER = MITTAGS.plusSeconds(60L);
    private static final Strafdauer EINE_STUNDE =
            new Strafdauer(Duration.ofHours(1L));
    private static final UUID BERT = UUID.randomUUID();
    private static final UUID ANNA = UUID.randomUUID();

    @Test
    @DisplayName("Eine frische Strafe gilt, ist nicht aufgehoben und hat ihr Ende")
    void frischeStrafe() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", ANNA, MITTAGS,
                EINE_STUNDE);

        assertTrue(eine.giltNoch(SPAETER));
        assertFalse(eine.istAufgehoben());
        assertFalse(eine.unbegrenzt());
        assertEquals(Optional.of(MITTAGS.plusSeconds(3600L)), eine.endet());
        assertEquals("", eine.aufhebungsgrund());
        assertTrue(eine.aufhebung().isEmpty());
    }

    @Test
    @DisplayName("Ein RAUSWURF bekommt Ende gleich Anfang und gilt nie")
    void rauswurfEndetSofort() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.RAUSWURF, "Spam", ANNA, MITTAGS,
                null);

        assertEquals(Optional.of(MITTAGS), eine.endet());
        assertFalse(eine.giltNoch(MITTAGS), "vorbei, sobald er geschieht");
        assertFalse(eine.unbegrenzt(), "ein Rauswurf ist nicht unbegrenzt - er ist"
                + " vorbei");
        assertEquals(0L, eine.restSekunden(MITTAGS));
    }

    @Test
    @DisplayName("Art und Dauer werden in BEIDE Richtungen geprueft")
    void artUndDauerInBeideRichtungen() {
        // Eine Dauer fuer einen Rauswurf ist genauso falsch wie ein Bann ohne Dauer -
        // und von den zwei Fehlern faellt der zweite sonst nie auf: Er ergaebe
        // stillschweigend einen dauerhaften Bann, und genau der braucht ein Recht.
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.RAUSWURF, "Spam", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, null));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.STUMM, "Chat", ANNA, MITTAGS, null));
    }

    @Test
    @DisplayName("Eine unbegrenzte Strafe hat kein Ende und gilt immer")
    void unbegrenzteStrafe() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "dauerhaft", ANNA, MITTAGS,
                Strafdauer.UNBEGRENZT);

        assertTrue(eine.unbegrenzt());
        assertTrue(eine.endet().isEmpty());
        assertTrue(eine.giltNoch(MITTAGS.plus(Duration.ofDays(9999L))));
        assertEquals(Long.MAX_VALUE, eine.restSekunden(SPAETER),
                "der Aufrufer fragt vorher unbegrenzt() - eine Zahl waere hier eine"
                        + " Zeitangabe, die den Betroffenen hoffen laesst");
    }

    @Test
    @DisplayName("Die KONSOLE ist null und keine erfundene Kennung")
    void konsoleIstNull() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "von der Konsole", null,
                MITTAGS, EINE_STUNDE);

        assertTrue(eine.verhaengtVon().isEmpty());
    }

    @Test
    @DisplayName("Ohne Nummer, Betroffenen, Art oder Zeitpunkt gibt es keine Strafe")
    void fehlendeAngaben() {
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(0L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(-1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, null,
                Strafart.BANN, "Grief", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                null, "Grief", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "Grief", ANNA, null, EINE_STUNDE));
    }

    @Test
    @DisplayName("Der GRUND ist Pflicht - zu kurz, fehlend und zu lang werfen")
    void grundIstPflicht() {
        // Plan 12c.3: Eine Strafe, die niemand erklaert, erzeugt nur Wiederkehr.
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "ab", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "   ", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, null, ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "x".repeat(Strafe.LAENGSTER_GRUND + 1), ANNA, MITTAGS,
                EINE_STUNDE));
    }

    @Test
    @DisplayName("Drei Zeichen sind genug - hax ist ein brauchbarer Grund")
    void dreiZeichenGenuegen() {
        assertEquals("hax", Strafe.neu(1L, BERT, Strafart.BANN, " hax ", ANNA, MITTAGS,
                EINE_STUNDE).grund(), "der Rand wird abgeschnitten");
        assertTrue(Strafe.brauchbarerGrund("hax"));
        assertFalse(Strafe.brauchbarerGrund("ab"));
        assertFalse(Strafe.brauchbarerGrund(null));
    }

    @Test
    @DisplayName("Ein Steuerzeichen im Grund wird abgewiesen und nicht entfernt")
    void steuerzeichenAbgewiesen() {
        // Der Grund steht in der oeffentlichen Bannliste. Ein Abschnittszeichen darin
        // faelschte Farben und liesse ganze Zeilen nachbauen.
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "Grief §cFAKE", ANNA, MITTAGS, EINE_STUNDE));
        assertThrows(IllegalArgumentException.class, () -> Strafe.neu(1L, BERT,
                Strafart.BANN, "Grief\nzweite Zeile", ANNA, MITTAGS, EINE_STUNDE));
        assertFalse(Strafe.brauchbarerGrund("Grief §cFAKE"));
    }

    @Test
    @DisplayName("Aufgehoben heisst: gilt nicht mehr, steht aber noch da")
    void aufgehoben() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", ANNA, MITTAGS,
                EINE_STUNDE);

        Strafe frei = eine.aufgehoben(ANNA, "Einspruch angenommen", SPAETER);

        assertTrue(frei.istAufgehoben());
        assertFalse(frei.giltNoch(SPAETER));
        assertEquals(Optional.of(SPAETER), frei.aufhebung());
        assertEquals(Optional.of(ANNA), frei.aufhebungDurch());
        assertEquals("Einspruch angenommen", frei.aufhebungsgrund());
        assertEquals("Grief", frei.grund(), "der urspruengliche Grund bleibt stehen");
        assertEquals(Optional.of(MITTAGS.plusSeconds(3600L)), frei.endet(),
                "das Ende bleibt stehen - es ist Teil des Protokolls");
    }

    @Test
    @DisplayName("Eine Aufhebung OHNE Grund kann nicht entstehen")
    void aufhebungBrauchtGrund() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", ANNA, MITTAGS,
                EINE_STUNDE);

        // Wer entsperrt, sagt warum - sonst steht in der Vorgeschichte eine
        // zurueckgenommene Strafe, und niemand weiss, ob der Vorwurf falsch war oder
        // der Moderator nachgegeben hat.
        assertThrows(IllegalArgumentException.class,
                () -> eine.aufgehoben(ANNA, "", SPAETER));
        assertThrows(IllegalArgumentException.class,
                () -> eine.aufgehoben(ANNA, "ab", SPAETER));
        assertThrows(IllegalArgumentException.class,
                () -> eine.aufgehoben(ANNA, null, SPAETER));
        assertThrows(IllegalArgumentException.class,
                () -> eine.aufgehoben(ANNA, "Grund", null));
    }

    @Test
    @DisplayName("Ein RAUSWURF laesst sich nicht aufheben")
    void rauswurfNichtAufhebbar() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.RAUSWURF, "Spam", ANNA, MITTAGS,
                null);

        assertThrows(IllegalArgumentException.class,
                () -> eine.aufgehoben(ANNA, "unnoetig", SPAETER));
    }

    @Test
    @DisplayName("Ein Aufhebungsgrund ohne Aufhebung kann nicht entstehen")
    void grundOhneAufhebung() {
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, MITTAGS.plusSeconds(3600L),
                null, null, "Einspruch"));
    }

    @Test
    @DisplayName("Ein Aufheber ohne Aufhebung kann nicht entstehen")
    void aufheberOhneAufhebung() {
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, MITTAGS.plusSeconds(3600L),
                null, ANNA, ""));
    }

    @Test
    @DisplayName("Eine Aufhebung VOR dem Verhaengen kann nicht entstehen")
    void aufhebungVorVerhaengung() {
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, MITTAGS.plusSeconds(3600L),
                MITTAGS.minusSeconds(60L), ANNA, "zu frueh"));
    }

    @Test
    @DisplayName("Ein Ende VOR dem Anfang kann nicht entstehen")
    void endeVorAnfang() {
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, MITTAGS.minusSeconds(60L),
                null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.BANN, "Grief", ANNA, MITTAGS, MITTAGS, null, null, ""),
                "eine Dauer von null ist keine Strafe, sondern eine Zeile im"
                        + " Protokoll, die nie gewirkt hat");
    }

    @Test
    @DisplayName("Ein RAUSWURF mit Dauer kann nicht entstehen")
    void rauswurfMitDauer() {
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.RAUSWURF, "Spam", ANNA, MITTAGS, MITTAGS.plusSeconds(60L),
                null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new Strafe(1L, BERT,
                Strafart.RAUSWURF, "Spam", ANNA, MITTAGS, null, null, null, ""),
                "ein Rauswurf ohne Ende waere ein Bann mit anderem Namen");
    }

    @Test
    @DisplayName("Die Restzeit laeuft ab und wird nie negativ")
    void restzeitLaeuftAb() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.STUMM, "Chat", ANNA, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));

        assertEquals(600L, eine.restSekunden(MITTAGS));
        assertEquals(540L, eine.restSekunden(SPAETER));
        assertEquals(0L, eine.restSekunden(MITTAGS.plusSeconds(600L)),
                "im Augenblick des Ablaufs ist sie abgelaufen - der Grenzfall"
                        + " entscheidet zugunsten des Betroffenen");
        assertEquals(0L, eine.restSekunden(MITTAGS.plusSeconds(9999L)));
    }

    @Test
    @DisplayName("giltNoch ohne Zeitpunkt wirft - es gibt keine Antwort ohne Uhr")
    void giltNochOhneZeitWirft() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", ANNA, MITTAGS,
                EINE_STUNDE);

        assertThrows(IllegalArgumentException.class, () -> eine.giltNoch(null));
    }

    @Test
    @DisplayName("Die Dauer einer gelesenen Strafe kommt aus Anfang und Ende")
    void dauerAusAnfangUndEnde() {
        Strafe eine = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", ANNA, MITTAGS,
                EINE_STUNDE);

        assertEquals(Duration.ofHours(1L), eine.dauer().spanne());
        assertTrue(Strafe.neu(2L, BERT, Strafart.BANN, "dauerhaft", ANNA, MITTAGS,
                Strafdauer.UNBEGRENZT).dauer().unbegrenzt());
    }
}
