package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer welchen Beruf waehlen und ablegen darf - und in welcher REIHENFOLGE die Regel das
 * entscheidet.
 *
 * <p>Die Reihenfolge ist hier keine Geschmacksfrage, sondern das, was der Spieler zu lesen
 * bekommt. Wer eine Spezialisierung ohne Recht und ohne den Grundberuf waehlt, soll das
 * Recht erfahren und nicht zuerst lernen, welchen Grundberuf er braeuchte - das Recht ist
 * die Schranke, die er nicht selbst aufheben kann. Darum pruefen mehrere Proben nicht nur
 * EINEN Mangel, sondern zwei gleichzeitig und bestehen darauf, welcher gemeldet wird.
 *
 * <p>Alle Groessen kommen von aussen herein, auch {@code jetzt} - gleiche Bauart und
 * gleicher Grund wie {@link SpielzeitregelTest}: Eine Regel mit eigener Uhr liesse sich
 * nur mit Warten pruefen.
 */
class BerufsregelTest {

    private static final UUID ANNA = UUID.randomUUID();

    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    /**
     * Drei Grundberufe, zwei Spezialisierungen - genug, um jeden Fall zu stellen, ohne
     * dass eine Probe die Liste erst erklaeren muss.
     */
    private static final Berufsverzeichnis BUCH = Berufsverzeichnis.ausAngaben(List.of(
            new Berufsangabe("bergmann", null),
            new Berufsangabe("bauer", null),
            new Berufsangabe("schmied", null),
            new Berufsangabe("steinmetz", "bergmann"),
            new Berufsangabe("gaertner", "bauer")));

    /** Zwei Grundberufe, eine Spezialisierung, 5000 Mark Gebuehr, ein Tag Frist. */
    private static final Berufswerte WERTE = new Berufswerte(5_000L,
            Duration.ofHours(24L), 2, 1);

    private static final Berufswerte OHNE_FRIST = new Berufswerte(5_000L, Duration.ZERO,
            2, 1);

    private static final Berufswerte UMSONST = new Berufswerte(0L, Duration.ZERO, 2, 1);

    private static Berufsstand stand(String... berufe) {
        return new Berufsstand(ANNA, List.of(berufe), null);
    }

    private static Berufsstand nachAblegen(Instant abgelegt, String... berufe) {
        return new Berufsstand(ANNA, List.of(berufe), abgelegt);
    }

    private static Berufsregel.Urteil waehle(Berufswerte werte, Berufsstand stand,
                                             String gewuenscht, boolean darfSpezialisieren,
                                             Instant jetzt) {
        return Berufsregel.pruefeWahl(BUCH, werte, stand, gewuenscht, darfSpezialisieren,
                jetzt);
    }

    private static Berufsregel.Urteil verlasse(Berufswerte werte, Berufsstand stand,
                                               String abzulegen, long guthaben,
                                               Instant jetzt) {
        return Berufsregel.pruefeVerlassen(BUCH, werte, stand, abzulegen, guthaben, jetzt);
    }

    // ---------------------------------------------------------------- waehlen

    @Test
    @DisplayName("Ein freier Grundberuf wird angenommen und kostet nichts")
    void grundberufAngenommen() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand(), "bergmann", false, MITTAGS);

        assertTrue(urteil.angenommen());
        assertEquals("bergmann", urteil.beruf());
        // Waehlen ist IMMER kostenlos, auch wenn eine Gebuehr eingestellt ist: Bezahlt
        // wird das Verlassen. Andersherum waere es umgehbar - erst aufgeben, einen Tag
        // warten, dann gratis etwas anderes waehlen.
        assertEquals(0L, urteil.kosten());
        assertFalse(urteil.kostenpflichtig());
        assertEquals(Duration.ZERO, urteil.restfrist());
        assertEquals(2, urteil.erlaubt(), "die Platzgrenze steht im Satz");
    }

    @Test
    @DisplayName("Ein Tippfehler wird als Tippfehler gemeldet, nicht als Regel")
    void unbekannterBeruf() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand(), " Holzfaeller ", true, MITTAGS);

        assertEquals(Berufsregel.Ausgang.UNBEKANNTER_BERUF, urteil.ausgang());
        // Die ROHE Eingabe steht im Satz, nur ohne Leerraum: Der Spieler soll lesen, was
        // er getippt hat. Kleingeschrieben zurueckgegeben saehe es aus wie ein Beruf, den
        // es gibt.
        assertEquals("Holzfaeller", urteil.beruf());
    }

    @Test
    @DisplayName("Keine Eingabe ist auch ein unbekannter Beruf und kein Absturz")
    void keineEingabe() {
        assertEquals(Berufsregel.Ausgang.UNBEKANNTER_BERUF,
                waehle(WERTE, stand(), null, true, MITTAGS).ausgang());
        assertEquals("", waehle(WERTE, stand(), null, true, MITTAGS).beruf());
        assertEquals(Berufsregel.Ausgang.UNBEKANNTER_BERUF,
                waehle(WERTE, stand(), "   ", true, MITTAGS).ausgang());
    }

    @Test
    @DisplayName("Gross getippt ist derselbe Beruf")
    void grossGetippt() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand(), " BERGMANN ", false, MITTAGS);

        assertTrue(urteil.angenommen());
        assertEquals("bergmann", urteil.beruf(), "im Satz steht der aufgeloeste Name");
    }

    @Test
    @DisplayName("Wer ihn schon haelt, waehlt ihn nicht noch einmal")
    void schonGehalten() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand("bergmann"), "bergmann", false,
                MITTAGS);

        assertEquals(Berufsregel.Ausgang.SCHON_GEHALTEN, urteil.ausgang());
        assertEquals("bergmann", urteil.beruf());
    }

    @Test
    @DisplayName("Sind die Grundberufe voll, nennt die Absage die Platzgrenze")
    void grundberufeVoll() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand("bergmann", "bauer"), "schmied",
                false, MITTAGS);

        assertEquals(Berufsregel.Ausgang.GRUNDBERUFE_VOLL, urteil.ausgang());
        assertEquals("schmied", urteil.beruf());
        assertEquals(2, urteil.erlaubt(), "die Zahl, die der Satz einsetzt");
    }

    @Test
    @DisplayName("Ohne das Recht gibt es keine Spezialisierung - und das steht vor allem anderen")
    void spezialisierungOhneRecht() {
        // Er hat weder das Recht NOCH den Grundberuf. Gemeldet wird das Recht: Den
        // Grundberuf koennte er sich holen, das Recht nicht. Eine Meldung ueber bergmann
        // schickte ihn auf einen Weg, der am Ende doch verschlossen ist.
        Berufsregel.Urteil urteil = waehle(WERTE, stand(), "steinmetz", false, MITTAGS);

        assertEquals(Berufsregel.Ausgang.SPEZIALISIERUNG_OHNE_RECHT, urteil.ausgang());
        assertEquals("steinmetz", urteil.beruf());
    }

    @Test
    @DisplayName("Mit Recht, aber ohne den Grundberuf - die Absage nennt den Grundberuf")
    void grundberufFehlt() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand("bauer"), "steinmetz", true,
                MITTAGS);

        assertEquals(Berufsregel.Ausgang.GRUNDBERUF_FEHLT, urteil.ausgang());
        // Im Satz steht der GEBRAUCHTE Beruf, nicht der gewuenschte: Der Spieler soll
        // lesen, was ihm fehlt. "Du brauchst steinmetz" waere kein Satz.
        assertEquals("bergmann", urteil.beruf());
    }

    @Test
    @DisplayName("Der fehlende Grundberuf wird vor der Sperrfrist gemeldet")
    void grundberufFehltVorDerFrist() {
        // Beides liegt an: Die Frist laeuft, und der Grundberuf fehlt. Gemeldet wird der
        // Grundberuf - nach Ablauf der Frist stuende der Spieler sonst wieder vor
        // derselben Absage und haette einen Tag umsonst gewartet.
        Berufsregel.Urteil urteil = waehle(WERTE,
                nachAblegen(MITTAGS, "bauer"), "steinmetz", true,
                MITTAGS.plus(Duration.ofHours(1L)));

        assertEquals(Berufsregel.Ausgang.GRUNDBERUF_FEHLT, urteil.ausgang());
    }

    @Test
    @DisplayName("Eine Spezialisierung mit Recht und Grundberuf wird angenommen")
    void spezialisierungAngenommen() {
        Berufsregel.Urteil urteil = waehle(WERTE, stand("bergmann"), "steinmetz", true,
                MITTAGS);

        assertTrue(urteil.angenommen());
        assertEquals("steinmetz", urteil.beruf());
        assertEquals(1, urteil.erlaubt(), "die Grenze der Spezialisierungen, nicht die"
                + " der Grundberufe");
    }

    @Test
    @DisplayName("Sind die Spezialisierungen voll, nennt die Absage deren Grenze")
    void spezialisierungVoll() {
        Berufsregel.Urteil urteil = waehle(WERTE,
                stand("bergmann", "bauer", "steinmetz"), "gaertner", true, MITTAGS);

        assertEquals(Berufsregel.Ausgang.SPEZIALISIERUNG_VOLL, urteil.ausgang());
        assertEquals(1, urteil.erlaubt());
    }

    @Test
    @DisplayName("Die Plaetze werden JE ROLLE gezaehlt und nicht zusammen")
    void plaetzeJeRolle() {
        // Zwei Grundberufe belegen beide Grundberufsplaetze. Der Platz fuer die
        // Spezialisierung ist davon unberuehrt. Zaehlte ein Zaehler alles zusammen, waere
        // die Absage dieselbe GRUNDBERUFE_VOLL - und niemand meldete es als Fehler, weil
        // der Spieler nur sieht, dass es nicht geht.
        Berufsregel.Urteil urteil = waehle(WERTE, stand("bergmann", "bauer"), "steinmetz",
                true, MITTAGS);

        assertTrue(urteil.angenommen());
    }

    @Test
    @DisplayName("Ohne Spezialisierungsplatz hilft auch das Recht nichts")
    void keineSpezialisierungenEingestellt() {
        Berufswerte keine = new Berufswerte(5_000L, Duration.ZERO, 2, 0);

        assertEquals(Berufsregel.Ausgang.SPEZIALISIERUNG_VOLL,
                waehle(keine, stand("bergmann"), "steinmetz", true, MITTAGS).ausgang());
    }

    @Test
    @DisplayName("Die Sperrfrist steht vor den Plaetzen - sie ist der Grund, warum JETZT nichts geht")
    void fristVorDenPlaetzen() {
        // Ein voller Platz liesse sich durch Ablegen loesen - nur eben nicht, solange die
        // Frist laeuft. Meldete die Regel hier den vollen Platz, legte der Spieler ab und
        // lernte erst dann, dass er einen Tag warten muss.
        Berufsregel.Urteil urteil = waehle(WERTE,
                nachAblegen(MITTAGS, "bergmann", "bauer"), "schmied", false,
                MITTAGS.plus(Duration.ofHours(10L)));

        assertEquals(Berufsregel.Ausgang.SPERRFRIST, urteil.ausgang());
        assertEquals(Duration.ofHours(14L), urteil.restfrist());
        assertEquals("schmied", urteil.beruf());
    }

    @Test
    @DisplayName("Nach Ablauf der Frist geht es weiter")
    void nachDerFrist() {
        Berufsregel.Urteil urteil = waehle(WERTE, nachAblegen(MITTAGS, "bergmann"),
                "bauer", false, MITTAGS.plus(Duration.ofHours(24L)));

        assertTrue(urteil.angenommen());
    }

    // --------------------------------------------------------------- verlassen

    @Test
    @DisplayName("Ein gehaltener Beruf wird abgelegt und kostet die Gebuehr")
    void verlassenAngenommen() {
        Berufsregel.Urteil urteil = verlasse(OHNE_FRIST, stand("bergmann"), "bergmann",
                5_000L, MITTAGS);

        assertTrue(urteil.angenommen());
        assertEquals("bergmann", urteil.beruf());
        assertEquals(5_000L, urteil.kosten());
        assertTrue(urteil.kostenpflichtig());
    }

    @Test
    @DisplayName("Was er nicht haelt, kann er nicht ablegen")
    void nichtGehalten() {
        Berufsregel.Urteil urteil = verlasse(OHNE_FRIST, stand("bergmann"), "bauer",
                5_000L, MITTAGS);

        assertEquals(Berufsregel.Ausgang.NICHT_GEHALTEN, urteil.ausgang());
        assertEquals("bauer", urteil.beruf());
    }

    @Test
    @DisplayName("Ein unbekannter Beruf bleibt ein Tippfehler, auch beim Ablegen")
    void verlassenUnbekannt() {
        Berufsregel.Urteil urteil = verlasse(OHNE_FRIST, stand("bergmann"), "holzfaeller",
                5_000L, MITTAGS);

        assertEquals(Berufsregel.Ausgang.UNBEKANNTER_BERUF, urteil.ausgang());
        assertEquals("holzfaeller", urteil.beruf());
    }

    @Test
    @DisplayName("Ein Grundberuf bleibt, solange seine Spezialisierung steht")
    void traegtSpezialisierung() {
        Berufsregel.Urteil urteil = verlasse(OHNE_FRIST, stand("bergmann", "steinmetz"),
                "bergmann", 5_000L, MITTAGS);

        assertEquals(Berufsregel.Ausgang.TRAEGT_SPEZIALISIERUNG, urteil.ausgang());
        // Im Satz steht die SPEZIALISIERUNG: Sie ist das, was der Spieler zuerst ablegen
        // muss. Der Name des Grundberufes stuende schon in seinem eigenen Befehl.
        assertEquals("steinmetz", urteil.beruf());
    }

    @Test
    @DisplayName("Ein Grundberuf ohne gehaltene Spezialisierung darf weg")
    void grundberufOhneSpezialisierung() {
        // Es GIBT eine Spezialisierung zu bergmann, aber er haelt sie nicht. Fragte die
        // Regel nur die Liste und nicht den Stand, kaeme kein Spieler je von seinem
        // Grundberuf los.
        assertTrue(verlasse(OHNE_FRIST, stand("bergmann"), "bergmann", 5_000L, MITTAGS)
                .angenommen());
    }

    @Test
    @DisplayName("Die Spezialisierung darf weg, auch wenn ihr Grundberuf stehen bleibt")
    void spezialisierungDarfWeg() {
        assertTrue(verlasse(OHNE_FRIST, stand("bergmann", "steinmetz"), "steinmetz",
                5_000L, MITTAGS).angenommen());
    }

    @Test
    @DisplayName("Fehlt das Geld, nennt die Absage die Gebuehr")
    void geldFehlt() {
        Berufsregel.Urteil urteil = verlasse(OHNE_FRIST, stand("bergmann"), "bergmann",
                4_999L, MITTAGS);

        assertEquals(Berufsregel.Ausgang.GELD_FEHLT, urteil.ausgang());
        // Die Gebuehr steht auch in der ABSAGE - sonst erfaehrt der Spieler nicht, wie
        // viel ihm fehlt.
        assertEquals(5_000L, urteil.kosten());
    }

    @Test
    @DisplayName("Genau die Gebuehr reicht")
    void genauDieGebuehrReicht() {
        assertTrue(verlasse(OHNE_FRIST, stand("bergmann"), "bergmann", 5_000L, MITTAGS)
                .angenommen());
    }

    @Test
    @DisplayName("Bei Gebuehr 0 reicht ein leeres Konto")
    void umsonstAblegen() {
        Berufsregel.Urteil urteil = verlasse(UMSONST, stand("bergmann"), "bergmann", 0L,
                MITTAGS);

        assertTrue(urteil.angenommen());
        assertFalse(urteil.kostenpflichtig(), "0 Mark bucht der Befehl nicht");
    }

    @Test
    @DisplayName("Die Frist steht vor dem Geld - Geld laesst sich verdienen, Zeit nicht")
    void fristVorDemGeld() {
        // Beides fehlt. Gemeldet wird die Frist: Der Spieler soll die Zahl erfahren, die
        // er nicht aendern kann, und nicht erst Geld sammeln, um dann abgewiesen zu
        // werden.
        Berufsregel.Urteil urteil = verlasse(WERTE, nachAblegen(MITTAGS, "bergmann"),
                "bergmann", 0L, MITTAGS.plus(Duration.ofHours(6L)));

        assertEquals(Berufsregel.Ausgang.SPERRFRIST, urteil.ausgang());
        assertEquals(Duration.ofHours(18L), urteil.restfrist());
        // Die Gebuehr steht trotzdem im Urteil: Derselbe Satz nennt sie, und zwei
        // Rechnungen fuer dieselbe Gebuehr liefen eines Tages auseinander.
        assertEquals(5_000L, urteil.kosten());
    }

    // --------------------------------------------------------------- restfrist

    @Test
    @DisplayName("Ohne eingestellte Frist ist keine Restzeit uebrig")
    void keineFristEingestellt() {
        assertEquals(Duration.ZERO,
                Berufsregel.restfrist(OHNE_FRIST, nachAblegen(MITTAGS), MITTAGS));
    }

    @Test
    @DisplayName("Wer nie abgelegt hat, hat keine Restzeit")
    void nieAbgelegt() {
        assertEquals(Duration.ZERO, Berufsregel.restfrist(WERTE, stand("bergmann"),
                MITTAGS));
    }

    @Test
    @DisplayName("Die Restzeit ist die Frist minus die vergangene Zeit")
    void restzeitLaeuftAb() {
        assertEquals(Duration.ofHours(24L),
                Berufsregel.restfrist(WERTE, nachAblegen(MITTAGS), MITTAGS));
        assertEquals(Duration.ofHours(14L), Berufsregel.restfrist(WERTE,
                nachAblegen(MITTAGS), MITTAGS.plus(Duration.ofHours(10L))));
        assertEquals(Duration.ZERO, Berufsregel.restfrist(WERTE, nachAblegen(MITTAGS),
                MITTAGS.plus(Duration.ofHours(24L))));
        assertEquals(Duration.ZERO, Berufsregel.restfrist(WERTE, nachAblegen(MITTAGS),
                MITTAGS.plus(Duration.ofDays(9L))));
    }

    @Test
    @DisplayName("Eine zurueckgestellte Uhr sperrt nicht laenger als die Frist")
    void uhrZurueckgestellt() {
        // Der Zeitpunkt liegt in der Zukunft - entweder wurde die Uhr gestellt oder die
        // Ablage liest aus einer anderen Zeitzone. Dann gilt die GANZE Frist und nicht
        // mehr: Alles andere waere eine Sperre ueber Tage wegen einer Zeitumstellung.
        Duration rest = Berufsregel.restfrist(WERTE,
                nachAblegen(MITTAGS.plus(Duration.ofDays(30L))), MITTAGS);

        assertEquals(Duration.ofHours(24L), rest);
    }

    @Test
    @DisplayName("Dieselbe Rechnung fuer die Absage und fuer /beruf stand")
    void eineRechnungFuerBeide() {
        // Oeffentlich und nur einmal gerechnet: Zwei Rechnungen fuer dieselbe Frist
        // liefen eines Tages auseinander, und der Spieler las eine Zahl, nach der er
        // trotzdem abgewiesen wird.
        Berufsstand stand = nachAblegen(MITTAGS, "bergmann");
        Instant jetzt = MITTAGS.plus(Duration.ofHours(10L));

        assertEquals(Berufsregel.restfrist(WERTE, stand, jetzt),
                waehle(WERTE, stand, "bauer", false, jetzt).restfrist());
    }

    // ----------------------------------------------------------------- Pflichten

    @Test
    @DisplayName("Eine Regel ohne ihre Zahlen urteilt nicht")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class, () -> Berufsregel.pruefeWahl(null,
                WERTE, stand(), "bergmann", false, MITTAGS));
        assertThrows(IllegalArgumentException.class, () -> Berufsregel.pruefeWahl(BUCH,
                null, stand(), "bergmann", false, MITTAGS));
        assertThrows(IllegalArgumentException.class, () -> Berufsregel.pruefeWahl(BUCH,
                WERTE, null, "bergmann", false, MITTAGS));
        assertThrows(IllegalArgumentException.class, () -> Berufsregel.pruefeWahl(BUCH,
                WERTE, stand(), "bergmann", false, null));
        assertThrows(IllegalArgumentException.class,
                () -> Berufsregel.pruefeVerlassen(BUCH, WERTE, stand(), "bergmann", 0L,
                        null));
    }

    @Test
    @DisplayName("Ein Urteil ohne Ausgang, Beruf oder Restfrist gibt es nicht")
    void urteilBrauchtAlles() {
        assertThrows(IllegalArgumentException.class, () -> new Berufsregel.Urteil(null,
                "bergmann", 0L, Duration.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> new Berufsregel.Urteil(
                Berufsregel.Ausgang.ANGENOMMEN, null, 0L, Duration.ZERO, 0));
        // Keine Restfrist ist Duration.ZERO und nicht null: Der Satz setzt die Zahl ein,
        // und null stuende als "null Stunden" in der Nachricht.
        assertThrows(IllegalArgumentException.class, () -> new Berufsregel.Urteil(
                Berufsregel.Ausgang.ANGENOMMEN, "bergmann", 0L, null, 0));
    }
}
