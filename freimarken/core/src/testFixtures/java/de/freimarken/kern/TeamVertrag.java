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

/**
 * Was jede {@link Teamablage} einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link RangVertrag} und {@link KasseVertrag}: Die Umsetzung
 * bringt ihre eigene Testklasse mit und erbt diese Pruefungen. Eine Umsetzung, die sich
 * anders verhaelt als die andere, wird damit in einem Testlauf sichtbar und nicht im
 * Betrieb.
 *
 * <p>Hier stehen alle fuenf Versprechen aus dem Klassenkopf von {@link Teamablage}. Die
 * wichtigsten zwei sind das zweite und das dritte, und sie sind leicht zu uebersehen,
 * weil beide einen Rueckgabewert pruefen und keinen Zustand: <b>Ein zweites Geben
 * aendert nichts - auch nicht die Vergabezeit.</b> Eine Umsetzung mit
 * {@code ON DUPLICATE KEY UPDATE vergeben_am = ?} wuerde die Spur des ersten Gebens
 * ueberschreiben, und dann ist "seit wann hat er die Rolle" fuer immer falsch
 * beantwortet. Im Spiel faellt das nie auf.
 */
public abstract class TeamVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-24T12:00:00Z");

    /** Eine Stunde spaeter - fuer die Frage, ob die erste Spur stehen bleibt. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    /** Zwei Rollen, deren Verhaeltnis die Ablage nicht kennt und nicht kennen darf. */
    protected static final String MODERATOR = "moderator";

    protected static final String BAUMEISTER = "baumeister";

    /** Eine frische, leere Ablage. */
    protected abstract Teamablage ablage();

    @Test
    @DisplayName("Wer keine Rolle hat, ist nicht im Team - und das ist kein Fehler")
    void unbekannterIstNichtImTeam() {
        UUID niemand = UUID.randomUUID();

        Teamstand stand = ablage().lies(niemand);

        // DAS VIERTE VERSPRECHEN. Diese Frage wird bei JEDEM Beitritt gestellt, und die
        // ueberwiegende Mehrheit der Spieler hat keine Rolle. Ein Wurf waere ein Server,
        // auf dem sich niemand anmelden kann.
        assertFalse(stand.imTeam());
        assertEquals(List.of(), stand.rollen());
        assertEquals(niemand, stand.spieler(),
                "der Spieler muss der sein, der hereingegeben wurde");
    }

    @Test
    @DisplayName("Eine gegebene Rolle steht danach im Stand - mit Zeit und Vergeber")
    void gegebeneRolleStehtDrin() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, bert),
                "eine neue Rolle gibt true zurueck");

        Teamstand stand = ablage.lies(anna);
        assertTrue(stand.imTeam());
        assertEquals(List.of(MODERATOR), stand.rollen());
        Teamstand.Zeile zeile = stand.zeilen().get(0);
        assertEquals(MITTAGS, zeile.seit(),
                "die Zeit kommt von aussen und nicht aus der Ablage");
        assertEquals(bert, zeile.vergeber().orElseThrow(),
                "wer vergeben hat, ist die Antwort auf die Frage, die am haeufigsten"
                        + " gestellt wird");
    }

    @Test
    @DisplayName("Ein zweites Geben aendert nichts - auch nicht die Vergabezeit")
    void zweitesGebenAendertNichts() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID clara = UUID.randomUUID();
        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, bert), "die Vorbereitung");

        assertFalse(ablage.gib(anna, MODERATOR, SPAETER, clara),
                "er hatte sie schon - das ist keine Aenderung");

        // DAS ZWEITE VERSPRECHEN, und es ist der Grund fuer diese ganze Probe: Eine
        // Umsetzung mit ON DUPLICATE KEY UPDATE vergeben_am = ? gibt hier auch false
        // zurueck und hat trotzdem die Spur zerstoert. Geprueft wird darum nicht der
        // Rueckgabewert, sondern was danach dasteht.
        Teamstand.Zeile zeile = ablage.lies(anna).zeilen().get(0);
        assertEquals(MITTAGS, zeile.seit(), "die ERSTE Vergabezeit bleibt stehen");
        assertEquals(bert, zeile.vergeber().orElseThrow(),
                "und der erste Vergeber bleibt auch stehen");
        assertEquals(1, ablage.lies(anna).zeilen().size(),
                "und es ist keine zweite Zeile entstanden");
    }

    @Test
    @DisplayName("Zwei Rollen stehen nebeneinander - eine Zeile je Spieler UND Rolle")
    void zweiRollenNebeneinander() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null), "die erste");
        assertTrue(ablage.gib(anna, BAUMEISTER, SPAETER, null), "die zweite");

        // DAS ERSTE VERSPRECHEN. Eine Umsetzung mit einer Zeile je SPIELER haette hier
        // nur noch die zweite Rolle - und zwei Verwalter, die im selben Augenblick zwei
        // Rollen vergeben, haetten dann einer umsonst gearbeitet.
        assertEquals(List.of(BAUMEISTER, MODERATOR), ablage.lies(anna).rollen(),
                "beide, und nach Namen sortiert");
    }

    @Test
    @DisplayName("Die Konsole darf ohne Vergeber vergeben")
    void konsoleOhneVergeber() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null));

        // Das ist der NORMALFALL beim Aufsetzen eines Servers: Die erste Rolle wird von
        // der Konsole vergeben, weil es noch niemanden gibt, der es koennte. Eine
        // Umsetzung mit NOT NULL auf der Spalte wuerfe hier.
        assertTrue(ablage.lies(anna).zeilen().get(0).vergeber().isEmpty(),
                "leer heisst: die Konsole, und nicht eine erfundene Null-UUID");
    }

    @Test
    @DisplayName("Eine genommene Rolle ist weg - die andere bleibt")
    void genommeneRolleIstWeg() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null), "die Vorbereitung");
        assertTrue(ablage.gib(anna, BAUMEISTER, MITTAGS, null), "die Vorbereitung");

        assertTrue(ablage.nimm(anna, MODERATOR));

        assertEquals(List.of(BAUMEISTER), ablage.lies(anna).rollen(),
                "genommen wird GENAU die genannte Rolle und nicht das ganze Team");
    }

    @Test
    @DisplayName("Eine Rolle nehmen, die er nicht hat, ist kein Fehler")
    void nehmenOhneRolleIstKeinFehler() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // DAS DRITTE VERSPRECHEN. Ein Verwalter, der eine Rolle zweimal nimmt, hat sich
        // vertippt und keinen Fehler gemacht - ein Wurf hier waere eine rote Zeile fuer
        // einen Zustand, der genau der gewuenschte ist.
        assertFalse(ablage.nimm(anna, MODERATOR), "nichts zu nehmen heisst false");
        assertFalse(ablage.lies(anna).imTeam());
    }

    @Test
    @DisplayName("Der letzte Rollenentzug nimmt den Spieler aus der Liste")
    void letzterEntzugRaeumtAuf() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null), "die Vorbereitung");
        assertTrue(ablage.nimm(anna, MODERATOR), "die Vorbereitung");

        // Sonst stuende anna in der Teamliste, obwohl sie keine Rolle mehr hat. Das ist
        // der Unterschied zwischen "keine Rolle" und "eine leere Huelle", und im Spiel
        // waere es ein Moderator, der auf der Teamliste steht und nichts darf.
        assertEquals(List.of(), ablage.alle(),
                "wer keine Rolle mehr hat, ist nicht mehr im Team");
    }

    @Test
    @DisplayName("Die Ablage prueft NICHT, ob es die Rolle gibt")
    void keineRollenpruefungInDerAblage() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // DAS FUENFTE VERSPRECHEN, und es ist bewusst unbequem: "hausmeister" steht in
        // keiner config.yml dieses Projekts, und die Ablage nimmt ihn trotzdem. Sie MUSS
        // ihn nehmen - sonst verlore ein Betreiber, der eine Rollenzeile aus der Datei
        // nimmt, alle Vergaben dieser Rolle, und ein Wiedereintragen nach einem
        // Vertipper holte sie nicht zurueck. Was in der Datei stehen darf, entscheidet
        // Teamwerte, und das ist eine Schicht darueber.
        assertTrue(ablage.gib(anna, "hausmeister", MITTAGS, null));
        assertEquals(List.of("hausmeister"), ablage.lies(anna).rollen());
    }

    @Test
    @DisplayName("Eine Rolle wird kleingeschrieben abgelegt, egal wie sie ankommt")
    void grossUndKleinIstDieselbeRolle() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertTrue(ablage.gib(anna, "Moderator", MITTAGS, null));

        // Zwei Schreibweisen derselben Rolle waeren zwei Rollen: Der Vergleich mit dem
        // Schluessel aus der Datei ginge einmal gut und einmal nicht, und der Spieler
        // haette eine Rolle, die Teamwerte.rolle nicht findet - also eine ohne Rechte.
        assertEquals(List.of(MODERATOR), ablage.lies(anna).rollen());
        assertFalse(ablage.gib(anna, "MODERATOR", SPAETER, null),
                "und sie wird beim zweiten Mal wiedererkannt");
    }

    @Test
    @DisplayName("Zwei Spieler stehen sich nicht im Weg")
    void zweiSpielerGetrennt() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null), "die Vorbereitung");

        assertEquals(List.of(MODERATOR), ablage.lies(anna).rollen());
        assertFalse(ablage.lies(bert).imTeam(), "bert hat keine Rolle");
    }

    @Test
    @DisplayName("Die Teamliste nennt jeden einmal, mit allen seinen Rollen")
    void listeNenntJedenEinmal() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        assertTrue(ablage.gib(anna, MODERATOR, MITTAGS, null), "die Vorbereitung");
        assertTrue(ablage.gib(anna, BAUMEISTER, MITTAGS, null), "die Vorbereitung");
        assertTrue(ablage.gib(bert, MODERATOR, MITTAGS, null), "die Vorbereitung");

        List<Teamstand> alle = ablage.alle();

        // Ein Verwalter mit drei Rollen ist eine Person und nicht drei. Eine Umsetzung,
        // die ihre Zeilen durchreicht, haette hier drei Eintraege - und die Teamliste
        // nennte anna zweimal.
        assertEquals(2, alle.size(), "zwei Personen, nicht drei Zeilen");
        Teamstand ihrer = alle.stream().filter(stand -> stand.spieler().equals(anna))
                .findFirst().orElseThrow();
        assertEquals(List.of(BAUMEISTER, MODERATOR), ihrer.rollen());
    }

    @Test
    @DisplayName("Die Teamliste ist in jeder Fassung gleich sortiert")
    void listeIstStabilSortiert() {
        Teamablage ablage = ablage();
        // Zwei UUIDs, deren Reihenfolge FESTSTEHT, und zwar in der anderen Richtung als
        // die Vergabe: Ohne die zugesagte Sortierung gaebe eine Karte die Reihenfolge
        // der Vergabe zurueck und eine Datenbank die ihres Index. Eine Probe, die beide
        // Fassungen vergleicht, waere dann mal gruen und mal rot.
        UUID frueher = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID spaeter = UUID.fromString("ffffffff-0000-0000-0000-00000000000b");
        assertTrue(ablage.gib(spaeter, MODERATOR, MITTAGS, null), "die Vorbereitung");
        assertTrue(ablage.gib(frueher, MODERATOR, MITTAGS, null), "die Vorbereitung");

        assertEquals(List.of(frueher, spaeter),
                ablage.alle().stream().map(Teamstand::spieler).toList());
    }

    @Test
    @DisplayName("Ohne Spieler, ohne Rolle oder ohne Zeit gibt es nichts zu tun")
    void fehlendeAngabenKlagen() {
        Teamablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.lies(null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.gib(null, MODERATOR, MITTAGS, null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.gib(anna, null, MITTAGS, null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.gib(anna, "  ", MITTAGS, null));
        // Ohne Zeit: Die Zeit kommt von aussen, damit alle Zeiten dieses Projekts aus
        // EINER Uhr stammen. Eine Ablage, die bei fehlender Zeit ihre eigene nimmt,
        // waere die zweite Uhr.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.gib(anna, MODERATOR, null, null));
        assertThrows(IllegalArgumentException.class, () -> ablage.nimm(null, MODERATOR));
        assertThrows(IllegalArgumentException.class, () -> ablage.nimm(anna, null));
    }
}
