package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Datensatz, an dem alles haengt. Seine Waechter sind die letzte Stelle, an der ein
 * unmoeglicher Besitz noch auffaellt, bevor er in der Ablage steht.
 */
class ParzelleTest {

    private static final String WELT = "freimarken:marktstadt";

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private Parzelle bauen(long id, long elternId, Zustand zustand, Set<UUID> mitbauer) {
        return new Parzelle(id, WELT, 1, 1, anna, mitbauer, 0L, Instant.EPOCH,
                Art.PARZELLE, elternId, zustand);
    }

    @Test
    @DisplayName("Ohne Art oder ohne Zustand entsteht kein Besitz")
    void artUndZustandSindPflicht() {
        assertThrows(IllegalArgumentException.class, () -> new Parzelle(1L, WELT, 0, 0, anna,
                Set.of(), 0L, Instant.EPOCH, null, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV));
        assertThrows(IllegalArgumentException.class, () -> new Parzelle(1L, WELT, 0, 0, anna,
                Set.of(), 0L, Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, null));
    }

    @Test
    @DisplayName("Eine negative Elternnummer ist ein Fehler")
    void negativeElternnummer() {
        assertThrows(IllegalArgumentException.class,
                () -> bauen(1L, -1L, Zustand.AKTIV, Set.of()));
    }

    @Test
    @DisplayName("Besitz kann nicht sein eigener Elternteil sein - Nummer 0 ohne Eltern aber schon")
    void keinSelbstElternteil() {
        assertThrows(IllegalArgumentException.class,
                () -> bauen(7L, 7L, Zustand.AKTIV, Set.of()));
        // Der Grenzfall, an dem ein zu einfacher Waechter scheitert: id 0 UND elternId 0
        // ist kein Selbstbezug, sondern ein Objekt, das noch keine Nummer bekommen hat.
        Parzelle ohneNummer = bauen(0L, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, Set.of());
        assertFalse(ohneNummer.hatElternteil());
    }

    @Test
    @DisplayName("hatElternteil unterscheidet eigenstaendig von Teil einer Gruppe")
    void hatElternteil() {
        assertFalse(bauen(5L, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, Set.of()).hatElternteil());
        assertTrue(bauen(5L, 4L, Zustand.AKTIV, Set.of()).hatElternteil());
    }

    @Test
    @DisplayName("Gekuendigt laesst weiterbauen, verfallen verweigert auch dem Eigentuemer")
    void zustandEntscheidetVorDemEigentum() {
        Set<UUID> mitbauer = Set.of(bert);
        assertTrue(bauen(1L, 0L, Zustand.AKTIV, mitbauer).darfBauen(anna));
        assertTrue(bauen(1L, 0L, Zustand.AKTIV, mitbauer).darfBauen(bert));

        // Wer raeumen soll, muss seine Kisten noch oeffnen und abbauen koennen.
        assertTrue(bauen(1L, 0L, Zustand.GEKUENDIGT, mitbauer).darfBauen(anna));
        assertTrue(bauen(1L, 0L, Zustand.GEKUENDIGT, mitbauer).darfBauen(bert));

        assertFalse(bauen(1L, 0L, Zustand.VERFALLEN, mitbauer).darfBauen(anna),
                "Auf verfallenem Besitz baut auch der Eigentuemer nicht mehr");
        assertFalse(bauen(1L, 0L, Zustand.VERFALLEN, mitbauer).darfBauen(bert));
    }

    @Test
    @DisplayName("OHNE Spieler ist die Antwort NEIN - und kein Wurf")
    void ohneSpielerIstEsNein() {
        // GEFUNDEN AM 18.11.2026 von InselbuchTest: Set.of(...).contains(null) ist eine
        // NullPointerException, und zwar auch bei einer LEEREN Mitbauerliste. Genau so
        // fragt aber eine Wache, die einen Handbalken begruendet ("wem gehoert das hier
        // ueberhaupt"), und genau so kommen die Ereignisse ohne Spieler herein.
        //
        // Im Spiel haette die Inselwache daraus eine Klage ueber eine stumme Datenbank
        // gemacht und jedes Bauen abgelehnt - mit einem Grund, der nicht stimmt. Beide
        // Listen stehen hier, weil der Wurf an der LEEREN schon kam.
        assertFalse(bauen(1L, 0L, Zustand.AKTIV, Set.of()).darfBauen(null));
        assertFalse(bauen(1L, 0L, Zustand.AKTIV, Set.of(bert)).darfBauen(null));
    }

    @Test
    @DisplayName("Mitbauer werden weiterhin unveraenderlich kopiert")
    void mitbauerBleibenUnveraenderlich() {
        Set<UUID> veraenderlich = new HashSet<>(Set.of(bert));
        Parzelle parzelle = bauen(1L, 0L, Zustand.AKTIV, veraenderlich);
        veraenderlich.clear();

        assertTrue(parzelle.darfBauen(bert), "Die Kopie darf dem Aufrufer nicht folgen");
        assertThrows(UnsupportedOperationException.class,
                () -> parzelle.mitbauer().add(anna));
    }

    @Test
    @DisplayName("Jede Art und jeder Zustand uebersteht den Weg durch den Datenbankschluessel")
    void rundlaufUeberAlleKonstanten() {
        for (Art art : Art.values()) {
            assertSame(art, Art.ausSchluessel(art.schluessel()), art.name());
        }
        for (Zustand zustand : Zustand.values()) {
            assertSame(zustand, Zustand.ausSchluessel(zustand.schluessel()), zustand.name());
        }
        // ASCII, kein Umlaut - sonst passt der Wert nicht zur ENUM-Spalte.
        assertEquals("gekuendigt", Zustand.GEKUENDIGT.schluessel());
    }

    @Test
    @DisplayName("Eine Freistadt OHNE Namen gibt es nicht - der Riegel greift")
    void freistadtOhneNamenWirft() {
        // Gefunden von der Einspritzungsrunde am 11.11.2026: Der Riegel stand da und
        // war von keiner Probe gedeckt. Alle Proben legten Staedte MIT Namen an - und
        // ein Riegel, den nie etwas ausloest, ist gruen mit und ohne.
        //
        // Was er verhindert: eine Stadt, die ueber keinen Warp erreichbar ist. 400.000
        // Mark fuer ein Feld, das niemand findet.
        var ohne = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                        Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                        Zustand.AKTIV, Parzelle.KEIN_NAME, Parzelle.KEINE_RECHTE,
                        UUID.randomUUID(), Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
        // Auf "Namen" geprueft und nicht auf "Freistadt": Seit Teil E traegt diese
        // Zeile einen zweiten Pflichtwert, die Kontonummer, und SEINE Meldung enthaelt
        // das Wort "Freistadt" ebenfalls. Mit dem groeberen Muster waere diese Probe
        // gruen geblieben, waehrend sie den anderen Riegel prueft - und darum steht
        // oben eine echte Kontonummer: Der Name ist hier der einzige Mangel.
        assertTrue(ohne.getMessage().contains("Namen"), ohne.getMessage());

        // Auch der leere Name faellt - sonst stuende in der Spalte eine Zeichenkette,
        // und der eindeutige Schluessel (welt, name) liesse genau eine davon zu.
        assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                        Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                        Zustand.AKTIV, "   ", Parzelle.KEINE_RECHTE,
                        UUID.randomUUID(), Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
    }

    @Test
    @DisplayName("Nur eine FREISTADT hat Buergerrechte - die andere Richtung auch")
    void buergerrechteNurFuerFreistaedte() {
        // FUND DER EINSPRITZUNGSRUNDE ZU TEIL D [12.11.2026]: Der Riegel im
        // Kompaktkonstruktor war gebaut und von keiner Probe gedeckt. Was er verhindert:
        // Zeilen in freistadt_rechte, die zu einer Marktstadtparzelle gehoeren - ein
        // Betreiber findet sie dort und sucht nach einer Wirkung, die es nicht gibt.
        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        Parzelle.KEIN_NAME, Set.of(Tat.BAUEN),
                        Parzelle.KEINE_KASSE, Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
        assertTrue(mit.getMessage().contains("Freistadt"), mit.getMessage());

        // Die LEERE Menge geht durch - sie ist die Vorgabe und bedeutet nichts anderes
        // als "keine Rechte". Ein Riegel, der auch sie abweist, machte jede
        // Marktstadtparzelle unbaubar.
        assertEquals(Parzelle.KEINE_RECHTE,
                new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        Parzelle.KEIN_NAME, Parzelle.KEINE_RECHTE,
                        Parzelle.KEINE_KASSE,
                        Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT).buergerrechte());

        // Und eine FREISTADT darf sie tragen - sonst prueft die Zeile darueber nur,
        // dass der Riegel immer zuschlaegt.
        Parzelle stadt = new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                "neustadt", Set.of(Tat.BENUTZEN), UUID.randomUUID(),
                Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT);
        assertTrue(stadt.buergerDuerfen(Tat.BENUTZEN));
        assertFalse(stadt.buergerDuerfen(Tat.BAUEN),
                "und nur DAS eine - buergerDuerfen fragt die Menge und nicht, ob"
                        + " ueberhaupt etwas drinsteht");
    }

    @Test
    @DisplayName("Eine Parzelle MIT Namen gibt es auch nicht - die andere Richtung")
    void parzelleMitNamenWirft() {
        // Beide Richtungen, wie bei jeder Bindung in diesem Projekt. Eine Parzelle mit
        // Namen belegte einen Namen im eindeutigen Schluessel, ohne dass je eine Stadt
        // dazu entstuende - und der naechste Gruender bekaeme eine Absage, deren Grund
        // nirgends zu sehen ist.
        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        "neustadt", Parzelle.KEINE_RECHTE,
                        Parzelle.KEINE_KASSE, Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
        assertTrue(mit.getMessage().contains("neustadt"), mit.getMessage());

        // Und der kurze Konstruktor kommt durch - er setzt KEIN_NAME und kann darum
        // gar keine Freistadt erzeugen.
        assertEquals(Parzelle.KEIN_NAME, new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L,
                Instant.EPOCH, Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL,
                Zustand.AKTIV).name());
    }

    @Test
    @DisplayName("Ein unbekannter Schluessel wirft und nennt den gelesenen Wert")
    void unbekannterSchluessel() {
        var artFehler = assertThrows(IllegalStateException.class,
                () -> Art.ausSchluessel("hochhaus"));
        assertTrue(artFehler.getMessage().contains("hochhaus"), artFehler.getMessage());

        var zustandFehler = assertThrows(IllegalStateException.class,
                () -> Zustand.ausSchluessel("GEKUENDIGT"));
        assertTrue(zustandFehler.getMessage().contains("GEKUENDIGT"),
                "Gross geschrieben ist ein anderer Schluessel - genau das faengt der Riegel");
    }

    @Test
    @DisplayName("Nur eine FREISTADT hat eine Kontonummer - und sie MUSS eine haben")
    void kontonummerGehoertZurFreistadt() {
        // Beide Richtungen, wie beim Namen [Teil E1]. Was die erste verhindert: eine
        // Stadt, deren Guthaben unter keiner Nummer liegt - Geld, das niemand erreicht,
        // und kein Satz, der davon erzaehlt.
        var ohne = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L,
                        Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                        Zustand.AKTIV, "neustadt", Parzelle.KEINE_RECHTE,
                        Parzelle.KEINE_KASSE, Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
        assertTrue(ohne.getMessage().contains("Kontonummer"), ohne.getMessage());

        // Und die andere: eine Marktstadtparzelle mit einem Konto. Es waere ein Konto,
        // auf das nie etwas fliesst und das niemand abfragt - und ein Betreiber, der es
        // in der Spalte findet, sucht nach einer Kasse, die es nicht gibt.
        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        Parzelle.KEIN_NAME, Parzelle.KEINE_RECHTE,
                        UUID.randomUUID(), Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT));
        assertTrue(mit.getMessage().contains("Stadtkasse"), mit.getMessage());
    }

    @Test
    @DisplayName("Jeder Wandler nimmt die Kontonummer mit")
    void wandlerBehaltenDieKontonummer() {
        // DIE STELLE, AN DER AM 11.11.2026 DER NAME VERLOREN GING, und die Kontonummer
        // ist der Wert, bei dem derselbe Fehler am teuersten waere: Eine Zeile, die ihre
        // Nummer verliert, verliert ihr Guthaben - lautlos, denn geworfen haette nur die
        // Zeile MIT Nummer, nicht die ohne.
        //
        // Der Compiler faengt das heute, weil es keinen Kurzweg mit dreizehn Argumenten
        // gibt. Diese Probe faengt den Fall, dass jemand einen baut.
        UUID konto = UUID.randomUUID();
        Parzelle stadt = new Parzelle(7L, WELT, 3, -7, anna, Set.of(), 400_000L,
                Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                "neustadt", Parzelle.KEINE_RECHTE, konto, Parzelle.KEINE_REGELN, Parzelle.NIE_BESUCHT);

        assertEquals(konto, stadt.mitMitbauern(Set.of(anna)).kasse(),
                "mitMitbauern - genau der Wandler, den es wegen des verlorenen Namens"
                        + " gibt");
        assertEquals(konto, stadt.mitBuergerrechten(Set.of(Tat.BAUEN)).kasse(),
                "mitBuergerrechten");
        assertEquals(konto, stadt.mitZustand(Zustand.VERFALLEN).kasse(),
                "mitZustand - und der ist fuer den Verfall gebaut, also genau fuer den"
                        + " Augenblick, in dem das Guthaben noch gebraucht wird");
    }


    @Test
    @DisplayName("hatRegeln sagt NEIN bei Leerraum - nicht nur bei null")
    void hatRegelnFragtNichtNurAufNull() {
        // DIE ENTSCHEIDUNG, DIE DIE AUFLAGE TRAEGT [Teil E2]: Ein Text aus drei
        // Leerzeichen ist keine Regel. Ohne die isBlank-Frage waere er eine erfuellte
        // Auflage - und jeder Stadtbesitzer, der die Flaeche halten will, tippt
        // /freistadt regeln und ein Leerzeichen.
        assertFalse(stadtMitRegeln(Parzelle.KEINE_REGELN).hatRegeln(), "null");
        assertFalse(stadtMitRegeln("").hatRegeln(), "leer");
        assertFalse(stadtMitRegeln("   ").hatRegeln(),
                "drei Leerzeichen - genau der Weg, auf dem die Auflage sonst zu"
                        + " umgehen waere");
        assertTrue(stadtMitRegeln("Kein PvP.").hatRegeln(), "und ein echter Text zaehlt");
    }

    @Test
    @DisplayName("Nur eine FREISTADT hat Regeln - aber sie MUSS keine haben")
    void regelnGehoerenZurFreistadt() {
        // Nur EINE Richtung, und das unterscheidet die Regeln von Name und
        // Kontonummer: Eine Freistadt OHNE Regeln ist der Anfangszustand jeder frisch
        // gegruendeten Stadt - genau daran haengt die Auflage.
        assertFalse(stadtMitRegeln(Parzelle.KEINE_REGELN).hatRegeln(),
                "eine Stadt ohne Regeln ist gueltig");

        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        Parzelle.KEIN_NAME, Parzelle.KEINE_RECHTE,
                        Parzelle.KEINE_KASSE, "Kein PvP.", Parzelle.NIE_BESUCHT));
        assertTrue(mit.getMessage().contains("Regeln"), mit.getMessage());
    }

    @Test
    @DisplayName("Nur eine FREISTADT wird besucht - aber sie MUSS nicht besucht sein")
    void besuchGehoertZurFreistadt() {
        // Nur EINE Richtung, wie bei den Regeln: "noch nie besucht" ist der
        // Anfangszustand jeder frisch gegruendeten Stadt, und ihre Frist laeuft ab der
        // GRUENDUNG - erworbenAm steht in derselben Zeile.
        Parzelle frisch = stadtMitRegeln(Parzelle.KEINE_REGELN);
        assertEquals(Parzelle.NIE_BESUCHT, frisch.besucht(),
                "und NIE_BESUCHT ist null und nicht 1970: Die Verfallsregel muss 'noch"
                        + " nie' von 'vor langer Zeit' unterscheiden koennen");

        var mit = assertThrows(IllegalArgumentException.class,
                () -> new Parzelle(1L, WELT, 0, 0, anna, Set.of(), 0L, Instant.EPOCH,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        Parzelle.KEIN_NAME, Parzelle.KEINE_RECHTE,
                        Parzelle.KEINE_KASSE, Parzelle.KEINE_REGELN,
                        java.time.LocalDate.of(2026, 11, 17)));
        assertTrue(mit.getMessage().contains("besucht"), mit.getMessage());
    }

    @Test
    @DisplayName("mitBesuch laesst alles andere stehen")
    void mitBesuchBehaeltDenRest() {
        // Dieselbe Zusage wie bei jedem Wandler, und dieselbe Lehre vom 11.11.2026:
        // Wer die Zeile von Hand neu zusammensetzt, vergisst ein Feld. Hier waere das
        // Feld der Regeltext - und mit ihm die halbe Auflage.
        UUID konto = UUID.randomUUID();
        Parzelle stadt = new Parzelle(7L, WELT, 3, -7, anna, Set.of(anna), 400_000L,
                Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                "neustadt", Set.of(Tat.BAUEN), konto, "Kein PvP.",
                Parzelle.NIE_BESUCHT);

        Parzelle neu = stadt.mitBesuch(java.time.LocalDate.of(2026, 11, 17));

        assertEquals(java.time.LocalDate.of(2026, 11, 17), neu.besucht());
        assertEquals("Kein PvP.", neu.regeln(), "der Regeltext bleibt - mit ihm die"
                + " halbe Auflage");
        assertEquals(konto, neu.kasse());
        assertEquals(Set.of(Tat.BAUEN), neu.buergerrechte());
        assertEquals(Set.of(anna), neu.mitbauer());
        assertEquals("neustadt", neu.name());
    }

    @Test
    @DisplayName("Ein zu langer Regeltext wird abgewiesen")
    void zuLangerRegeltextWirft() {
        // Der Riegel HIER und nicht nur in der Datenbank: Dort waere ein zu langer Text
        // je nach Betriebsart eine Treiberausnahme ohne Satz oder - schlimmer - eine
        // stille Kuerzung. Ein Stadtbesitzer, dessen Regeln mitten im Wort aufhoeren,
        // sucht den Fehler bei sich.
        String grenze = "x".repeat(Parzelle.REGELN_GROESSTENS);
        assertTrue(stadtMitRegeln(grenze).hatRegeln(),
                "genau die Grenze geht durch - ein Riegel, der sie mitnimmt, kostet ein"
                        + " Zeichen, das die Spalte hergibt");

        var fehler = assertThrows(IllegalArgumentException.class,
                () -> stadtMitRegeln(grenze + "x"));
        assertTrue(fehler.getMessage().contains("257"), fehler.getMessage());
    }

    @Test
    @DisplayName("mitRegeln laesst alles andere stehen")
    void mitRegelnBehaeltDenRest() {
        // Dieselbe Zusage wie bei den anderen Wandlern, und dieselbe Lehre vom
        // 11.11.2026: Wer die Zeile von Hand neu zusammensetzt, vergisst ein Feld.
        UUID konto = UUID.randomUUID();
        Parzelle stadt = new Parzelle(7L, WELT, 3, -7, anna, Set.of(anna), 400_000L,
                Instant.EPOCH, Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                "neustadt", Set.of(Tat.BAUEN), konto, "Alte Regel.", Parzelle.NIE_BESUCHT);

        Parzelle neu = stadt.mitRegeln("Neue Regel.");

        assertEquals("Neue Regel.", neu.regeln());
        assertEquals(konto, neu.kasse(), "die Kontonummer bleibt - sonst ist das Geld"
                + " unerreichbar");
        assertEquals(Set.of(Tat.BAUEN), neu.buergerrechte());
        assertEquals(Set.of(anna), neu.mitbauer());
        assertEquals("neustadt", neu.name());
    }

    /**
     * Eine Freistadt mit genau diesem Regeltext - alles andere gleich.
     *
     * <p>Nicht {@code static}, weil {@code anna} ein Feld der Probe ist: Jede
     * Probenmethode bekommt ihre eigene Kennung, und eine gemeinsame waere ein Zustand
     * zwischen Proben.
     */
    private Parzelle stadtMitRegeln(String regeln) {
        return new Parzelle(1L, WELT, 3, -7, anna, Set.of(), 400_000L, Instant.EPOCH,
                Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, "neustadt",
                Parzelle.KEINE_RECHTE, UUID.randomUUID(), regeln, Parzelle.NIE_BESUCHT);
    }

}
