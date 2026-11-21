package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Menuetafel - reine Daten, ohne Server.
 *
 * <p>Die Tafel ist die Haelfte des Menues, die in einer Probe laufen KANN: Sie rechnet
 * mit Zahlen und Zeichenketten. Was daraus im Spiel wird, baut {@code Menuehand} hinter
 * einer Naht, und die laeuft in keiner Probe ({@code Bukkit.createInventory},
 * {@code new ItemStack}).
 *
 * <p><b>Was hier NICHT geprueft werden kann:</b> ob ein Gegenstand auch ein GEGENSTAND
 * ist. {@code Material.isItem} greift in Minecrafts Verzeichnis und wirft ohne Server;
 * gemessen am 21.11.2026. Geprueft wird darum der NAME ({@code Material.getMaterial}
 * liest nur die Aufzaehlung) - ein Tippfehler faellt damit auf, ein Block, der kein
 * Gegenstand ist, erst in der Hand, und die sagt es dem Betreiber im Protokoll.
 */
@DisplayName("Die Menuetafel")
class MenuetafelTest {

    private static Menuetafel.Eintrag eintrag(int fach, String befehl) {
        return new Menuetafel.Eintrag(fach, MenueSatz.PARZELLE_NAME,
                MenueSatz.PARZELLE_ERKLAERUNG, "stone", befehl);
    }

    @Test
    @DisplayName("1 - das Hauptmenue nennt elf Befehle, jeder in seinem Fach")
    void dasHauptmenueNenntElfBefehle() {
        assertEquals(List.of("parzelle", "mark", "rang", "beruf", "markt", "laden",
                        "freistadt", "werder", "wolke", "hilfe", "regeln"),
                Menuetafel.HAUPT.befehle());
        assertEquals(27, Menuetafel.HAUPT.faecher());
    }

    @Test
    @DisplayName("2 - imFach findet den Eintrag und sonst nichts")
    void imFachFindetDenEintrag() {
        assertEquals(Optional.of("parzelle"),
                Menuetafel.HAUPT.imFach(10).map(Menuetafel.Eintrag::befehl));
        // Fach 0 ist frei, und das ist Absicht: Die Eintraege stehen in der Mitte.
        assertTrue(Menuetafel.HAUPT.imFach(0).isEmpty());
        assertTrue(Menuetafel.HAUPT.imFach(-1).isEmpty());
        assertTrue(Menuetafel.HAUPT.imFach(99).isEmpty());
    }

    @Test
    @DisplayName("3 - jeder Gegenstand des Hauptmenues ist ein bekannter Name")
    void jederGegenstandIstBekannt() {
        var unbekannt = new TreeSet<String>();
        for (Menuetafel.Eintrag eintrag : Menuetafel.HAUPT.eintraege()) {
            if (Material.getMaterial(
                    eintrag.gegenstand().toUpperCase(Locale.ROOT)) == null) {
                unbekannt.add(eintrag.gegenstand());
            }
        }
        // EIN TIPPFEHLER WAERE SONST UNSICHTBAR: Die Hand findet das Material nicht,
        // schreibt eine Zeile ins Protokoll und das Menue bleibt zu - gesehen hat das
        // dann der erste Spieler, der /menu tippt.
        assertEquals(new TreeSet<String>(), unbekannt);
    }

    @Test
    @DisplayName("4 - zwei Eintraege auf einem Fach werden abgewiesen")
    void zweiEintraegeAufEinemFachWerdenAbgewiesen() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel(MenueSatz.TITEL, 3,
                        List.of(eintrag(4, "mark"), eintrag(4, "rang"))));

        assertTrue(fehler.getMessage().contains("Fach 4"), fehler.getMessage());
    }

    @Test
    @DisplayName("5 - ein Fach ausserhalb der Zeilen wird abgewiesen")
    void fachAusserhalbWirdAbgewiesen() {
        // 2 Zeilen sind 18 Faecher, also 0 bis 17. Fach 18 waere im Spiel unsichtbar.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel(MenueSatz.TITEL, 2, List.of(eintrag(18, "mark"))));

        assertTrue(fehler.getMessage().contains("18 Faecher"), fehler.getMessage());
    }

    @Test
    @DisplayName("6 - ein Befehl MIT Schraegstrich wird abgewiesen")
    void befehlMitSchraegstrichWirdAbgewiesen() {
        // performCommand nimmt den Befehl OHNE Schraegstrich. Mit einem waere es
        // "//mark", und der Spieler laese "unbekannter Befehl" auf einen Klick.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> eintrag(4, "/mark"));

        assertTrue(fehler.getMessage().contains("performCommand"), fehler.getMessage());
    }

    @Test
    @DisplayName("7 - ein Eintrag ohne Befehl TUT nichts, ist aber erlaubt")
    void eintragOhneBefehlTutNichts() {
        // Eine Zierde oder eine Ueberschrift im Menue: sichtbar, nicht anklickbar.
        var zierde = eintrag(4, "");

        assertFalse(zierde.tut());
        assertEquals(List.of(), new Menuetafel(MenueSatz.TITEL, 3,
                List.of(zierde)).befehle());
    }

    @Test
    @DisplayName("8 - keine Zeile und sieben Zeilen werden abgewiesen")
    void falscheZeilenzahlWirdAbgewiesen() {
        // Ein Truheninventar hat 1 bis 6 Zeilen. Alles andere wirft im Spiel beim Bauen,
        // und dort steht dann keine Zeile, die sagt warum.
        //
        // DIE 0-ZEILE OHNE EINTRAG [21.11.2026]: Mit einem Eintrag kam das Gegenbeispiel
        // nie beim Zeilen-Riegel an - faellt der weg, wirft zwei Zeilen weiter der
        // Fach-Riegel ("Fach 0 liegt ausserhalb eines Menues mit 0 Zeilen"), und die
        // Probe prueft nur den TYP der Ausnahme. Sie war mit und ohne den Riegel gruen.
        // Ohne Eintrag laeuft die Fach-Schleife leer, und nur noch der Zeilen-Riegel
        // kann werfen.
        assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel(MenueSatz.TITEL, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel(MenueSatz.TITEL, 7, List.of(eintrag(0, "mark"))));
    }

    @Test
    @DisplayName("10 - jeder Eintrag zeigt auf die Saetze SEINES Befehls")
    void jederEintragZeigtAufSeineSaetze() {
        // DIE PAARUNG, und nicht die Spalten [21.11.2026]. Probe 1 vergleicht die
        // Befehle, Probe 3 die Gegenstaende, VorlageTest die MENGE der Satzpfade - jede
        // Spalte einzeln. Vertauschte jemand MARK_NAME/MARK_ERKLAERUNG mit
        // RANG_NAME/RANG_ERKLAERUNG, blieb alles gruen, und im Spiel liest der Spieler
        // auf dem Goldbarren "Dein Rang..." und landet in /mark. Der Pfad nennt die
        // Paarung selbst (menue.eintrag.<befehl>.name), also ist sie pruefbar - der
        // Hilfeplan hat genau diese Probe seit seinem ersten Tag.
        //
        // Die Abweichungen werden GESAMMELT: So nennt der rote Lauf gleich das
        // vertauschte Paar und nicht nur das erste.
        List<String> falsch = new ArrayList<>();
        for (Menuetafel.Eintrag eintrag : Menuetafel.HAUPT.eintraege()) {
            List<String> erwartet = List.of("menue.eintrag." + eintrag.befehl() + ".name",
                    "menue.eintrag." + eintrag.befehl() + ".erklaerung");
            List<String> gebaut = List.of(eintrag.name().pfad(),
                    eintrag.erklaerung().pfad());
            if (!erwartet.equals(gebaut)) {
                falsch.add("Fach " + eintrag.fach() + " (" + eintrag.befehl() + "): "
                        + gebaut + " statt " + erwartet);
            }
        }
        assertEquals(List.of(), falsch, "Ein Eintrag zeigt auf die Saetze eines ANDEREN"
                + " Befehls - der Spieler liest einen Namen und bekommt einen anderen"
                + " Befehl.");
    }

    @Test
    @DisplayName("11 - fuerRechte laesst weg, was der Leser nicht tippen darf")
    void fuerRechteLaesstWeg() {
        // DER RIEGEL, DEN DAS MENUE BIS ZUM 21.11.2026 NICHT HATTE. /hilfe filtert jede
        // Zeile mit dem Recht des Befehls; das Menue zeigte dieselben Befehle
        // ungefiltert. Ein Betreiber, der freimarken.werder wegnimmt, sah in /hilfe
        // richtig kein /werder - im Menue lag der Prismarinblock weiter da, und der
        // Klick endete in Brigadiers englischem "Unknown or incomplete command", weil
        // requires den Knoten VERBIRGT und nicht absagt.
        Menuetafel nurJeder = Menuetafel.HAUPT.fuerRechte(recht -> false);
        assertEquals(List.of("parzelle", "mark"), nurJeder.befehle(),
                "ohne jedes Recht bleiben genau die zwei Eintraege, deren Befehl JEDER"
                        + " tippen darf");
        assertEquals(3, nurJeder.zeilen(), "die Zeilenzahl muss bleiben - sonst findet"
                + " der Spieler seinen Eintrag je nach Rechtelage an einer anderen"
                + " Stelle");
        assertEquals(MenueSatz.TITEL, nurJeder.titel());

        // DIE GEGENRICHTUNG: Mit allem bleibt alles. Ohne sie waere auch ein Filter
        // gruen, der immer alles wegwirft.
        assertEquals(Menuetafel.HAUPT.befehle(),
                Menuetafel.HAUPT.fuerRechte(recht -> true).befehle());

        // EIN Recht gibt genau EINEN Eintrag dazu - und zwar seinen.
        assertEquals(List.of("parzelle", "mark", "werder"),
                Menuetafel.HAUPT.fuerRechte(WerderBefehle.RECHT::equals).befehle());
    }

    @Test
    @DisplayName("12 - das weggefilterte Fach ist auch per Klick nicht zu erreichen")
    void weggefiltertesFachIstUnbekannt() {
        // WARUM DER FILTER IN DER TAFEL STEHT und nicht in der Hand: Die Hand legt die
        // gefilterte Tafel in den Halter, und die Wache liest sie von dort. Ein Fach,
        // das wegfiel, findet in imFach keinen Eintrag mehr - sonst klickte der Spieler
        // auf das leere Fach 19 und bekaeme doch /werder.
        Menuetafel gefiltert = Menuetafel.HAUPT.fuerRechte(recht -> false);

        assertEquals(Optional.empty(), gefiltert.imFach(19),
                "Fach 19 (Hafenwerder) ist weggefallen, wird per Klick aber noch"
                        + " gefunden - dann fuehrt die Wache den Befehl doch aus");
        assertEquals(Optional.of("mark"),
                gefiltert.imFach(11).map(Menuetafel.Eintrag::befehl),
                "Fach 11 (/mark) darf JEDER, es muss bleiben");
    }

    @Test
    @DisplayName("13 - ein Befehl, den der Hilfeplan nicht kennt, wird abgewiesen")
    void unbekannterBefehlWirdAbgewiesen() {
        // OHNE DEN PLAN GIBT ES KEIN RECHT, nach dem fuerRechte fragen koennte - der
        // Eintrag waere jedem angeboten. Der Riegel steht im Konstruktor und nicht in
        // der Hand: Dort faellt er beim Bauen der Tafel auf, also in dieser Probe, und
        // nicht erst, wenn ein Spieler /menu tippt.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> eintrag(4, "gibtsnicht"));
        assertTrue(fehler.getMessage().contains("Hilfeplan"), fehler.getMessage());

        // UND DIE GEGENRICHTUNG: Die Zierde ohne Befehl geht durch - sie fuehrt nichts
        // aus, also gibt es nichts zu erlauben.
        assertEquals(Hilfeplan.JEDER, eintrag(4, "").recht());
    }

    @Test
    @DisplayName("14 - der Gegenstandsname wird klein und ohne Rand abgelegt")
    void gegenstandsnameWirdNormalisiert() {
        // Die Regel war gebaut (Menuehand.artname) und wurde NIRGENDS gerufen. Ohne sie
        // kommt " Grass_Block" unveraendert bei matchMaterial an, jenes macht daraus
        // _GRASS_BLOCK und liefert null: SEVERE im Protokoll, und jeder Spieler liest
        // nur "Das Menue liess sich nicht oeffnen".
        assertEquals("grass_block", new Menuetafel.Eintrag(4, MenueSatz.PARZELLE_NAME,
                MenueSatz.PARZELLE_ERKLAERUNG, "  Grass_Block ", "mark").gegenstand());
    }

    @Test
    @DisplayName("9 - ohne Titel und ohne Gegenstand wird abgewiesen")
    void ohneTitelUndOhneGegenstandWirdAbgewiesen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel(null, 3, List.of(eintrag(0, "mark"))));
        assertThrows(IllegalArgumentException.class,
                () -> new Menuetafel.Eintrag(0, MenueSatz.PARZELLE_NAME,
                        MenueSatz.PARZELLE_ERKLAERUNG, " ", "mark"));
    }
}
