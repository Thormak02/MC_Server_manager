package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Welche Rollen ein Spieler hat - der Stand, den jede Ablage zurueckgibt. */
@DisplayName("Der Teamstand eines Spielers")
class TeamstandTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-24T12:00:00Z");

    private static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    private static final UUID ANNA =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID CHEF =
            UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    @DisplayName("Ein leerer Stand ist nicht im Team")
    void leererStand() {
        Teamstand leer = Teamstand.leer(ANNA);

        assertFalse(leer.imTeam());
        assertEquals(List.of(), leer.rollen());
        assertEquals(ANNA, leer.spieler());
    }

    @Test
    @DisplayName("Die Zeilen werden nach ROLLENNAMEN sortiert, nicht nach Zeit")
    void zeilenWerdenNachNamenSortiert() {
        // Die beiden Ordnungen fallen hier AUSEINANDER: Alphabetisch kommt "baumeister"
        // zuerst, zeitlich "moderator". Waeren sie gleich, sagte die Probe nichts - sie
        // waere gruen, egal nach welcher der beiden sortiert wird.
        Teamstand stand = new Teamstand(ANNA, List.of(
                new Teamstand.Zeile("moderator", MITTAGS, null),
                new Teamstand.Zeile("baumeister", SPAETER, null)));

        assertEquals(List.of("baumeister", "moderator"), stand.rollen(),
                "sonst sortieren zwei Fassungen hinter derselben Schnittstelle"
                        + " verschieden, und eine Probe ist mal gruen und mal rot");
    }

    @Test
    @DisplayName("Dieselbe Rolle zweimal wird abgewiesen")
    void doppelteRolleKlagt() {
        // Zweimal dieselbe Rolle gibt keine zweiten Rechte, aber zwei Vergabezeiten -
        // und dann nennt die Auskunft die, die zufaellig vorn steht.
        assertThrows(IllegalArgumentException.class, () -> new Teamstand(ANNA, List.of(
                new Teamstand.Zeile("moderator", MITTAGS, null),
                new Teamstand.Zeile("moderator", SPAETER, CHEF))));
    }

    @Test
    @DisplayName("Ein fehlender Vergeber heisst: die Konsole")
    void fehlenderVergeberIstDieKonsole() {
        Teamstand.Zeile ohne = new Teamstand.Zeile("moderator", MITTAGS, null);
        Teamstand.Zeile mit = new Teamstand.Zeile("moderator", MITTAGS, CHEF);

        // Ein Optional und kein Platzhalter: Eine erfundene Null-UUID stuende spaeter in
        // der Auskunft als Spieler, den es nicht gibt - und jemand suchte ihn.
        assertTrue(ohne.vergeber().isEmpty());
        assertEquals(CHEF, mit.vergeber().orElseThrow());
    }

    @Test
    @DisplayName("Eine Zeile ohne Vergabezeit wird abgewiesen")
    void zeileOhneZeitKlagt() {
        // Ohne sie beantwortet die Auskunft die Frage "seit wann" nicht, und genau die
        // wird gestellt.
        assertThrows(IllegalArgumentException.class,
                () -> new Teamstand.Zeile("moderator", null, null));
    }

    @Test
    @DisplayName("Ein grossgeschriebener Rollenname wird abgewiesen")
    void grossgeschriebeneRolleKlagt() {
        // Normalisiert die Ablage nicht, findet Teamwerte.rolle die Rolle nicht wieder -
        // der Spieler hat sie dann, ohne ihre Rechte zu bekommen. Still.
        assertThrows(IllegalArgumentException.class,
                () -> new Teamstand.Zeile("Moderator", MITTAGS, null));
    }

    @Test
    @DisplayName("hat() findet die Rolle unabhaengig von der Schreibweise")
    void hatFindetDieRolle() {
        Teamstand stand = new Teamstand(ANNA, List.of(
                new Teamstand.Zeile("moderator", MITTAGS, null)));

        assertTrue(stand.hat("moderator"));
        assertTrue(stand.hat("Moderator"), "der Befehl gibt herein, was getippt wurde");
        assertFalse(stand.hat("baumeister"));
        assertFalse(stand.hat(null));
    }

    @Test
    @DisplayName("Rollen, die die Datei nicht mehr kennt, werden genannt")
    void unbekannteRollenWerdenGenannt() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("moderator", List.of("tpa"))));
        Teamstand stand = new Teamstand(ANNA, List.of(
                new Teamstand.Zeile("moderator", MITTAGS, null),
                new Teamstand.Zeile("hausmeister", MITTAGS, null)));

        // Der Betroffene verliert still die Rechte dieser Rolle - im Spiel sieht das aus,
        // als waere sie nie vergeben worden. Darum gehoert sie gemeldet, und darum steht
        // die Frage hier: Die Ablage weiss nicht, was in der Datei steht.
        assertEquals(List.of("hausmeister"), stand.unbekannteRollen(werte));
        assertEquals(List.of(), stand.unbekannteRollen(Teamwerte.VORGABE.mitRollen(
                List.of(new Teamwerte.Rolle("moderator", List.of("tpa")),
                        new Teamwerte.Rolle("hausmeister", List.of("tpa"))))));
    }

    @Test
    @DisplayName("Ohne Werte gelten alle Rollen als unbekannt")
    void ohneWerteSindAlleUnbekannt() {
        Teamstand stand = new Teamstand(ANNA, List.of(
                new Teamstand.Zeile("moderator", MITTAGS, null)));

        // Der sichere Rueckfall: Lieber alles melden als nichts. Eine leere Liste hiesse
        // "alles in Ordnung", und das waere die falsche Auskunft fuer einen Zustand, in
        // dem niemand weiss, was in Ordnung ist.
        assertEquals(List.of("moderator"), stand.unbekannteRollen(null));
    }

    @Test
    @DisplayName("Ohne Spieler oder ohne Zeilenliste gibt es keinen Stand")
    void fehlendeAngabenKlagen() {
        assertThrows(IllegalArgumentException.class, () -> new Teamstand(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Teamstand(ANNA, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Teamstand(ANNA, java.util.Arrays.asList((Teamstand.Zeile) null)));
        assertThrows(IllegalArgumentException.class,
                () -> new Teamstand.Zeile(null, MITTAGS, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Teamstand.Zeile("  ", MITTAGS, null));
    }
}
