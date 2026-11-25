package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Datensatz einer Meldung - was er annimmt und was er abweist. */
@DisplayName("Eine Meldung")
class MeldungTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final Instant SPAETER = MITTAGS.plusSeconds(60L);

    private static final UUID ANNA =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID BERT =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final UUID MODERATOR =
            UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static Meldung offene() {
        return Meldung.neu(1L, ANNA, BERT, "baut auf meiner Parzelle", "altfurt",
                10, -60, -20, MITTAGS);
    }

    @Test
    @DisplayName("1 - eine frische Meldung ist OFFEN, ohne Bearbeiter und ungelesen")
    void frischeMeldung() {
        Meldung eine = offene();

        // Die Fabrikmethode ist der einzige Weg zu einer offenen Meldung: Fuenf Felder
        // haben beim Anlegen genau einen erlaubten Wert, und wer sie hinschreiben muss,
        // schreibt irgendwann einen anderen hin.
        assertEquals(Meldestand.OFFEN, eine.stand());
        assertTrue(eine.wirdBearbeitetVon().isEmpty());
        assertEquals("", eine.vermerk());
        assertTrue(eine.erledigt().isEmpty());
        assertFalse(eine.gelesen());
        assertFalse(eine.warteteAufLesen(), "es gibt noch nichts zu lesen");
    }

    @Test
    @DisplayName("2 - niemand meldet sich selbst")
    void selbstmeldungKlagt() {
        // Hier und nicht nur im Buch: Haette die Ablage diesen Zustand halten koennen,
        // liesse sich der Riegel im Buch herausnehmen, ohne dass etwas rot wird.
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, ANNA, "mich selbst", "altfurt", 0, -60, 0,
                        MITTAGS));
    }

    @Test
    @DisplayName("3 - ein zu kurzer Grund wird abgewiesen, ein dreistelliger geht")
    void grundlaenge() {
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, "ab", "altfurt", 0, -60, 0, MITTAGS));
        // DREI Zeichen genuegen, und das ist bewusst niedrig: "hax" ist eine brauchbare
        // Meldung. Eine hoehere Grenze erzieht niemanden, sie verhindert die Meldung.
        assertEquals("hax", Meldung.neu(1L, ANNA, BERT, "hax", "altfurt", 0, -60, 0,
                MITTAGS).grund());
    }

    @Test
    @DisplayName("4 - ein zu langer Grund wird abgewiesen")
    void zuLangerGrund() {
        String lang = "x".repeat(Meldung.LAENGSTER_GRUND + 1);

        // Die Grenze steht hier UND in der Datenbankspalte. Ohne die Pruefung bricht der
        // strict mode von MariaDB mitten in einer Meldung ab, und der Melder glaubt, es
        // sei nichts passiert.
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, lang, "altfurt", 0, -60, 0, MITTAGS));
        assertEquals(Meldung.LAENGSTER_GRUND,
                Meldung.neu(1L, ANNA, BERT, "x".repeat(Meldung.LAENGSTER_GRUND),
                        "altfurt", 0, -60, 0, MITTAGS).grund().length(),
                "genau die Grenze geht noch");
    }

    @Test
    @DisplayName("5 - ein Abschnittszeichen im Grund wird ABGEWIESEN, nicht entfernt")
    void abschnittszeichenKlagt() {
        // Abgewiesen und nicht entfernt: Ein Grund, der heimlich anders aussieht als
        // getippt, ist in einem Protokoll das Letzte, was man will. Und das
        // Abschnittszeichen ist der Weg, in einer Chatzeile Farben zu faelschen - eine
        // Meldung wird einem Moderator gezeigt, und ein Melder soll darin keine Zeilen
        // nachbauen koennen.
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, "harmlos§cFEHLER", "altfurt",
                        0, -60, 0, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, "zwei\nZeilen", "altfurt", 0, -60, 0,
                        MITTAGS));
    }

    @Test
    @DisplayName("6 - der Grund wird am Rand beschnitten")
    void grundWirdBeschnitten() {
        assertEquals("Griefing", Meldung.neu(1L, ANNA, BERT, "   Griefing  ", "altfurt",
                0, -60, 0, MITTAGS).grund());
    }

    @Test
    @DisplayName("7 - eine Meldung ohne Welt wird abgewiesen")
    void ohneWeltKlagt() {
        // Ohne Welt kann kein Moderator hinspringen, und das Hinspringen ist der Zweck
        // des Ortes - der Plan sagt woertlich "springen hin".
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, "Vorfall", "  ", 0, -60, 0, MITTAGS));
        assertEquals("altfurt", Meldung.neu(1L, ANNA, BERT, "Vorfall", "ALTFURT",
                0, -60, 0, MITTAGS).welt(), "und sie wird kleingeschrieben");
    }

    @Test
    @DisplayName("8 - eine Nummer von 0 oder darunter wird abgewiesen")
    void nummerMussPositivSein() {
        // Die Nummer ist die ANREDE fuer /meldung. Eine 0 waere eine, die niemand
        // nennen kann.
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(0L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(-1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS));
    }

    @Test
    @DisplayName("9 - eine OFFENE Meldung mit Bearbeiter wird abgewiesen")
    void offeneMitBearbeiterKlagt() {
        // Der Stand und der Bearbeiter sind EIN Paar: Wer sie auseinanderlaufen laesst,
        // bekommt eine Liste, in der "offen" steht und doch jemand daran arbeitet.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.OFFEN, MODERATOR, "", null, false));
    }

    @Test
    @DisplayName("10 - IN_ARBEIT oder erledigt OHNE Bearbeiter wird abgewiesen")
    void bearbeitetOhneBearbeiterKlagt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.IN_ARBEIT, null, "", null, false));
        // Ohne Bearbeiter ist nicht zu sagen, wer angesprochen werden kann, wenn der
        // Melder nachfragt.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.ABGESCHLOSSEN, null, "fertig", SPAETER,
                        false));
    }

    @Test
    @DisplayName("11 - erledigt OHNE Zeitpunkt und offen MIT Zeitpunkt klagen beide")
    void erledigungszeitpunktPasstZumStand() {
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.ABGEWIESEN, MODERATOR, "nichts dran", null,
                        false));
        // Und die Gegenrichtung: Eine unerledigte Meldung mit Erledigungszeitpunkt waere
        // eine, deren Liegezeit sich nicht rechnen laesst.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.IN_ARBEIT, MODERATOR, "", SPAETER, false));
    }

    @Test
    @DisplayName("12 - eine unbeantwortete Meldung kann nicht gelesen sein")
    void unbeantwortetNichtGelesen() {
        // Sonst stuende in der Ablage eine Meldung, die als gelesen gilt und keine
        // Antwort hat - und die Antwort, die spaeter kommt, erreichte den Melder nie.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldung(1L, ANNA, BERT, "Vorfall", "altfurt", 0, -60, 0,
                        MITTAGS, Meldestand.OFFEN, null, "", null, true));
        assertThrows(IllegalArgumentException.class, () -> offene().alsGelesen());
    }

    @Test
    @DisplayName("13 - inArbeitVon setzt Stand und Bearbeiter, sonst nichts")
    void inArbeitVon() {
        Meldung genommen = offene().inArbeitVon(MODERATOR, SPAETER);

        assertEquals(Meldestand.IN_ARBEIT, genommen.stand());
        assertEquals(MODERATOR, genommen.wirdBearbeitetVon().orElseThrow());
        assertEquals(MITTAGS, genommen.gemeldetAm(),
                "der Meldezeitpunkt bleibt - er ist die Antwort auf 'wie lange liegt sie'");
        assertEquals("baut auf meiner Parzelle", genommen.grund());
        assertTrue(genommen.erledigt().isEmpty());
    }

    @Test
    @DisplayName("14 - erledigt() setzt Vermerk, Zeitpunkt und macht die Antwort UNGELESEN")
    void erledigt() {
        Meldung fertig = offene().inArbeitVon(MODERATOR, SPAETER)
                .erledigt(Meldestand.ABGESCHLOSSEN, MODERATOR, "angesprochen", SPAETER);

        assertEquals(Meldestand.ABGESCHLOSSEN, fertig.stand());
        assertEquals("angesprochen", fertig.vermerk());
        assertEquals(SPAETER, fertig.erledigt().orElseThrow());
        assertTrue(fertig.warteteAufLesen(), "der Melder hat eine Antwort zu lesen");
    }

    @Test
    @DisplayName("15 - eine ERNEUT erledigte Meldung gilt wieder als ungelesen")
    void erneutErledigtIstWiederUngelesen() {
        Meldung gelesen = offene()
                .erledigt(Meldestand.ABGEWIESEN, MODERATOR, "erster Blick", SPAETER)
                .alsGelesen();
        assertFalse(gelesen.warteteAufLesen(), "die Vorbereitung");

        Meldung nochmal = gelesen.erledigt(Meldestand.ABGESCHLOSSEN, MODERATOR,
                "doch etwas dran", SPAETER.plusSeconds(60L));

        // Eine erneut erledigte Meldung hat eine NEUE Antwort, und die hat der Melder
        // nicht gelesen. Bliebe gelesen=true stehen, erfuehre er von der Korrektur nie -
        // und das ist der Fall, in dem ihm zuerst nicht geglaubt wurde.
        assertTrue(nochmal.warteteAufLesen());
        assertEquals("doch etwas dran", nochmal.vermerk());
    }

    @Test
    @DisplayName("16 - erledigt() nach OFFEN oder IN_ARBEIT wird abgewiesen")
    void erledigtNurNachBeantwortet() {
        assertThrows(IllegalArgumentException.class,
                () -> offene().erledigt(Meldestand.OFFEN, MODERATOR, "x", SPAETER));
        assertThrows(IllegalArgumentException.class,
                () -> offene().erledigt(Meldestand.IN_ARBEIT, MODERATOR, "x", SPAETER));
        assertThrows(IllegalArgumentException.class,
                () -> offene().erledigt(null, MODERATOR, "x", SPAETER));
    }

    @Test
    @DisplayName("17 - gemeldetVon trifft nur den Melder")
    void gemeldetVon() {
        Meldung eine = offene();

        assertTrue(eine.gemeldetVon(ANNA));
        assertFalse(eine.gemeldetVon(BERT), "der Betroffene ist nicht der Melder");
        assertFalse(eine.gemeldetVon(null));
    }

    @Test
    @DisplayName("18 - die Staende sagen, was mit ihnen geht")
    void staende() {
        assertTrue(Meldestand.OFFEN.inBearbeitung());
        assertTrue(Meldestand.IN_ARBEIT.inBearbeitung());
        assertFalse(Meldestand.ABGESCHLOSSEN.inBearbeitung());
        assertFalse(Meldestand.ABGEWIESEN.inBearbeitung());

        assertFalse(Meldestand.OFFEN.beantwortet());
        assertFalse(Meldestand.IN_ARBEIT.beantwortet());
        assertTrue(Meldestand.ABGESCHLOSSEN.beantwortet());
        // ABGEWIESEN ist beantwortet: Der Melder bekommt eine Antwort, nur eine andere.
        // Waere es das nicht, lase er bei einer unbegruendeten Meldung gar nichts - und
        // genau dann meldet er beim zweiten Mal nicht mehr.
        assertTrue(Meldestand.ABGEWIESEN.beantwortet());
    }
}
