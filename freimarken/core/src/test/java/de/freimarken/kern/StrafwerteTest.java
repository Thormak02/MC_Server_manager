package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was an den Strafen einstellbar ist - und was nicht entstehen darf.
 *
 * <p>Die wichtigste Probe ist {@link #fehlendeArtWirft()}: Eine Art ohne Hoechstdauer
 * waere eine, fuer die kein Riegel greift - und das faellt erst auf, wenn jemand damit
 * unbegrenzt sperrt.
 */
class StrafwerteTest {

    @Test
    @DisplayName("Die Vorgabe ist brauchbar und nennt beide fortwirkenden Arten")
    void vorgabeIstBrauchbar() {
        Strafwerte werte = Strafwerte.VORGABE;

        assertEquals(168L, werte.hoechsteStundenFuer(Strafart.STUMM));
        assertEquals(720L, werte.hoechsteStundenFuer(Strafart.BANN));
        assertTrue(werte.bannlisteOeffentlich(),
                "Plan 12c.4 macht sie ausdruecklich oeffentlich");
        assertTrue(werte.ansagen());
        assertEquals(10, werte.zeilenJeSeite());
        assertTrue(werte.ungereimtheiten().isEmpty(),
                "die Vorgabe hat keine Ungereimtheit: " + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("Fuer JEDE fortwirkende Art steht eine Hoechstdauer in der Vorgabe")
    void vorgabeIstVollstaendig() {
        // Ueber values() und nicht von Hand: Eine vierte fortwirkende Art macht diese
        // Probe rot, und das ist der Augenblick, in dem die Zahl dazugehoert.
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            assertTrue(Strafwerte.VORGABE.hoechsteStundenFuer(art) > 0L,
                    art + " hat in der Vorgabe keine brauchbare Hoechstdauer");
        }
    }

    @Test
    @DisplayName("Eine fehlende Hoechstdauer wirft - sie waere ein Riegel, der fehlt")
    void fehlendeArtWirft() {
        // DIE PROBE DIESER KLASSE. Vollstaendig und nicht nachsichtig: Eine fehlende
        // Zahl waere eine Art, fuer die kein Riegel greift.
        assertThrows(IllegalArgumentException.class, () -> new Strafwerte(
                Map.of(Strafart.BANN, 720L), true, true, 10, List.of("msg")));
        assertThrows(IllegalArgumentException.class, () -> new Strafwerte(
                Map.of(), true, true, 10, List.of("msg")));
        assertThrows(IllegalArgumentException.class, () -> new Strafwerte(
                null, true, true, 10, List.of("msg")));
    }

    @Test
    @DisplayName("Eine Hoechstdauer fuer eine Art OHNE Dauer wirft")
    void dauerFuerRauswurfWirft() {
        Map<Strafart, Long> mit = new EnumMap<>(Strafart.class);
        mit.put(Strafart.STUMM, 168L);
        mit.put(Strafart.BANN, 720L);
        mit.put(Strafart.RAUSWURF, 1L);

        // Eine Zahl, die nie gefragt wird, ist eine Einstellung, die nichts tut - und
        // ein Betreiber, der sie setzt, glaubt, er habe etwas geregelt.
        assertThrows(IllegalArgumentException.class, () -> new Strafwerte(mit, true,
                true, 10, List.of("msg")));
    }

    @Test
    @DisplayName("Nach der Hoechstdauer einer Art OHNE Dauer zu fragen wirft")
    void frageNachRauswurfdauerWirft() {
        // Eine 0 als Antwort waere eine Grenze, die alles verbietet - und der Riegel
        // wuerde bei einem Rauswurf stumm immer greifen.
        assertThrows(IllegalArgumentException.class,
                () -> Strafwerte.VORGABE.hoechsteStundenFuer(Strafart.RAUSWURF));
        assertThrows(IllegalArgumentException.class,
                () -> Strafwerte.VORGABE.hoechsteStundenFuer(null));
    }

    @Test
    @DisplayName("Eine Hoechstdauer von 0 ist erlaubt und wird gemeldet")
    void nullStundenIstErlaubtUndGemeldet() {
        Strafwerte werte = Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 0L);

        assertEquals(0L, werte.hoechsteStundenFuer(Strafart.BANN));
        assertTrue(werte.ungereimtheiten().stream()
                        .anyMatch(satz -> satz.contains("ist 0")),
                "eine 0 heisst: jeder Bann braucht das Recht fuer dauerhafte Strafen -"
                        + " ein Moderator kann dann gar nicht bannen: "
                        + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("Eine Hoechstdauer ueber zehn Jahren wirft")
    void zuHoheDauerWirft() {
        // Eine Zahl darueber waere eine Hoechstdauer, die keine Dauer mehr erreichen
        // kann - der Riegel waere stumm offen.
        assertThrows(IllegalArgumentException.class, () -> Strafwerte.VORGABE
                .mitHoechsteStunden(Strafart.BANN,
                        Strafwerte.HOECHSTE_STUNDEN_GRENZE + 1L));
        assertThrows(IllegalArgumentException.class, () -> Strafwerte.VORGABE
                .mitHoechsteStunden(Strafart.BANN, -1L));
        assertTrue(Strafwerte.brauchbareStunden(Strafwerte.HOECHSTE_STUNDEN_GRENZE));
        assertFalse(Strafwerte.brauchbareStunden(
                Strafwerte.HOECHSTE_STUNDEN_GRENZE + 1L));
    }

    @Test
    @DisplayName("Eine Hoechstdauer ueber einem Jahr wird gemeldet")
    void praktischDauerhaftWirdGemeldet() {
        Strafwerte werte = Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 20000L);

        assertTrue(werte.ungereimtheiten().stream()
                        .anyMatch(satz -> satz.contains("praktisch dauerhaft")),
                "wer so lange sperren darf, sperrt praktisch dauerhaft - die"
                        + " Unterscheidung aus Plan 12c.2 greift dann nicht mehr: "
                        + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("Eine Seite hat 1 bis 50 Zeilen")
    void seitenlaenge() {
        assertThrows(IllegalArgumentException.class,
                () -> Strafwerte.VORGABE.mitZeilenJeSeite(0));
        assertThrows(IllegalArgumentException.class,
                () -> Strafwerte.VORGABE.mitZeilenJeSeite(Strafwerte.HOECHSTE_ZEILEN
                        + 1));
        assertEquals(1, Strafwerte.VORGABE.mitZeilenJeSeite(1).zeilenJeSeite());
        assertTrue(Strafwerte.brauchbareZeilen(1L));
        assertFalse(Strafwerte.brauchbareZeilen(0L));
    }

    @Test
    @DisplayName("Die Befehlsliste wird vereinheitlicht: ohne Schraegstrich und klein")
    void befehlslisteVereinheitlicht() {
        Strafwerte werte = Strafwerte.VORGABE.mitStummSperrtBefehle(
                List.of("/MSG", "  tell  ", "//w"));

        // Die Form wird EINMAL hergestellt und nicht beim Vergleichen - sonst
        // vergleicht die eine Stelle anders als die andere.
        assertEquals(List.of("msg", "tell", "w"), werte.stummSperrtBefehle());
    }

    @Test
    @DisplayName("Ein doppelter Name kommt nur einmal in die Liste")
    void doppelterNameEinmal() {
        assertEquals(List.of("msg"), Strafwerte.VORGABE
                .mitStummSperrtBefehle(List.of("msg", "/msg", "MSG"))
                .stummSperrtBefehle());
    }

    @Test
    @DisplayName("Ein leerer Name in der Befehlsliste wirft")
    void leererNameWirft() {
        // Er wuerde auf JEDEN Befehl passen oder auf keinen - beides ist kein Zustand.
        assertThrows(IllegalArgumentException.class, () -> Strafwerte.VORGABE
                .mitStummSperrtBefehle(List.of("msg", "")));
        assertThrows(IllegalArgumentException.class, () -> Strafwerte.VORGABE
                .mitStummSperrtBefehle(List.of("msg", "   ")));
        assertThrows(IllegalArgumentException.class, () -> Strafwerte.VORGABE
                .mitStummSperrtBefehle(List.of("msg", "/")));
        assertThrows(IllegalArgumentException.class,
                () -> Strafwerte.VORGABE.mitStummSperrtBefehle(null));
    }

    @Test
    @DisplayName("Eine LEERE Befehlsliste ist erlaubt und wird gemeldet")
    void leereListeIstErlaubtUndGemeldet() {
        Strafwerte werte = Strafwerte.VORGABE.mitStummSperrtBefehle(List.of());

        assertTrue(werte.stummSperrtBefehle().isEmpty());
        assertTrue(werte.ungereimtheiten().stream()
                        .anyMatch(satz -> satz.contains("/msg")),
                "eine Stummschaltung ohne Befehlssperre ist ueber /msg zu umgehen -"
                        + " und der Moderator haelt den Spieler fuer still: "
                        + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("sperrtBefehl erkennt den Befehl in jeder Gestalt")
    void sperrtBefehlErkenntJedeGestalt() {
        Strafwerte werte = Strafwerte.VORGABE;

        assertTrue(werte.sperrtBefehl("/msg Hans hallo"));
        assertTrue(werte.sperrtBefehl("msg"));
        assertTrue(werte.sperrtBefehl("/MSG Hans"));
        assertTrue(werte.sperrtBefehl("  /msg  "));
        // DIE WICHTIGSTE ZEILE: Paper macht jeden Befehl auch unter plugin:befehl
        // erreichbar - ohne diesen Fang waere /minecraft:msg der Weg um jede
        // Stummschaltung herum.
        assertTrue(werte.sperrtBefehl("/minecraft:msg Hans hallo"),
                "der Namensraum ist der Weg um die Sperre herum");
        assertTrue(werte.sperrtBefehl("/minecraft:tell Hans"));
    }

    @Test
    @DisplayName("sperrtBefehl laesst alles andere durch - auch /melden")
    void sperrtBefehlLaesstDurch() {
        Strafwerte werte = Strafwerte.VORGABE;

        // /melden steht ausdruecklich NICHT in der Liste: Wer gerade bestraft wurde,
        // ist nicht derjenige, dem man den Meldeweg nimmt - gerade er braeuchte ihn
        // gegen einen Moderator.
        assertFalse(werte.sperrtBefehl("/melden Hans Grief"));
        assertFalse(werte.sperrtBefehl("/heim"));
        assertFalse(werte.sperrtBefehl(""));
        assertFalse(werte.sperrtBefehl("/"));
        assertFalse(werte.sperrtBefehl(null));
        assertFalse(werte.sperrtBefehl("/msgx Hans"),
                "ein Befehl, der nur so ANFAENGT, ist ein anderer");
    }

    @Test
    @DisplayName("Eine nicht oeffentliche Bannliste wird gemeldet")
    void stilleBannlisteWirdGemeldet() {
        Strafwerte werte = Strafwerte.VORGABE.mitBannlisteOeffentlich(false);

        assertTrue(werte.ungereimtheiten().stream()
                        .anyMatch(satz -> satz.contains("12c.4")),
                "Plan 12c.4: Sie zwingt zu Begruendungen, die standhalten, und genau"
                        + " deshalb gehoert sie dazu: " + werte.ungereimtheiten());
    }

    @Test
    @DisplayName("Eine abgeschaltete Ansage wird gemeldet")
    void stilleAnsageWirdGemeldet() {
        Strafwerte werte = Strafwerte.VORGABE.mitAnsagen(false);

        assertFalse(werte.ansagen());
        assertFalse(werte.ungereimtheiten().isEmpty());
    }

    @Test
    @DisplayName("Die Hoechstdauern sind nach aussen unveraenderlich")
    void hoechstdauernSindFest() {
        Map<Strafart, Long> herein = new EnumMap<>(Strafart.class);
        herein.put(Strafart.STUMM, 168L);
        herein.put(Strafart.BANN, 720L);
        Strafwerte werte = new Strafwerte(herein, true, true, 10, List.of("msg"));

        herein.put(Strafart.BANN, 1L);

        // Eine Karte, die der Aufrufer noch in der Hand hat, waere eine Grenze, die
        // sich hinter dem Riegel aendern laesst.
        assertEquals(720L, werte.hoechsteStundenFuer(Strafart.BANN));
        assertThrows(UnsupportedOperationException.class,
                () -> werte.stummSperrtBefehle().add("x"));
    }
}
