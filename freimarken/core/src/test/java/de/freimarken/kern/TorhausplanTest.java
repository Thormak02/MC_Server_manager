package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Form eines Torhauses und der Ort seiner Portalnischen.
 *
 * <p>Die Erwartungen sind die Zahlen der Dokumentation und nicht aus
 * {@link Torhausplan} geholt: 15 breit, 9 tief, 11 hoch, Tuerme von 4, Durchgang von 7
 * und 5 hoch, Nischen 3 mal 3.
 */
@DisplayName("Die Form eines Torhauses")
class TorhausplanTest {

    /** Das Nordtorhaus der ausgelieferten Stadt. */
    private static Torhausplan nord() {
        return Torhausplan.inDerMauer(Stadtplan.altfurt(), Richtung.NORD);
    }

    @Test
    @DisplayName("Das Torhaus sitzt im aeusseren Mauerring und ist 15 mal 9 mal 11")
    void dasMass() {
        Stadtplan stadt = Stadtplan.altfurt();
        Torhausplan tor = Torhausplan.inDerMauer(stadt, Richtung.NORD);
        Quader b = tor.bereich();

        assertEquals(15, b.bisX() - b.vonX() + 1, "breit");
        assertEquals(9, b.bisZ() - b.vonZ() + 1, "tief");
        assertEquals(11, b.bisY() - b.vonY() + 1, "hoch");
        assertEquals(Stadtplan.BODEN_Y, b.vonY(), "es steht auf dem Boden");
        // DIE AEUSSERE KANTE IST DIE AEUSSERE KANTE DER STADT: Das Torhaus steht IN der
        // Mauer, nicht davor und nicht dahinter.
        assertEquals(stadt.aussenRing(),
                Stadtplan.ring(b.vonX(), b.vonZ()), "aeussere Ecke");
        assertEquals(stadt.aussenRing() - Torhausplan.TIEFE + 1, tor.innenRing(),
                "die Stadtseite");
        // UND ES IST MITTIG AUF DER ACHSE.
        assertEquals(Stadtplan.MITTE_X, (b.vonX() + b.bisX()) / 2, "mittig in x");

        // Alle vier Seiten liegen verschieden, und jede im Mauerring.
        Set<String> ecken = new HashSet<>();
        for (Richtung seite : Richtung.values()) {
            Quader eine = Torhausplan.inDerMauer(stadt, seite).bereich();
            ecken.add(eine.alsText());
            assertEquals(stadt.aussenRing(),
                    Math.max(Stadtplan.ring(eine.vonX(), eine.vonZ()),
                            Stadtplan.ring(eine.bisX(), eine.bisZ())),
                    seite.wort() + " liegt im Mauerring");
        }
        assertEquals(4, ecken.size(), "vier Seiten, vier Orte: " + ecken);
    }

    @Test
    @DisplayName("Der Durchgang ist sieben breit, fuenf hoch und ENDET an der Mauer")
    void derDurchgang() {
        Torhausplan tor = nord();
        Quader b = tor.bereich();
        // Die Stadtseite liegt bei groesserem z (Richtung Mitte), die Aussenseite bei
        // kleinerem - das Nordtorhaus liegt noerdlich der Mitte.
        int innen = b.bisZ();

        // SIEBEN BREIT: x 69..75, und daneben steht Turm.
        for (int x = 69; x <= 75; x++) {
            assertEquals(Torhausplan.Stueck.LUFT,
                    tor.stueckBei(x, Stadtplan.BODEN_Y + 1, innen),
                    "Durchgang bei x " + x);
        }
        assertNotEquals(Torhausplan.Stueck.LUFT,
                tor.stueckBei(68, Stadtplan.BODEN_Y + 1, innen), "links Turm");
        assertNotEquals(Torhausplan.Stueck.LUFT,
                tor.stueckBei(76, Stadtplan.BODEN_Y + 1, innen), "rechts Turm");

        // FUENF HOCH: Ebene 1 bis 5 ist Luft, Ebene 6 ist die Decke.
        for (int h = 1; h <= 5; h++) {
            assertEquals(Torhausplan.Stueck.LUFT,
                    tor.stueckBei(72, Stadtplan.BODEN_Y + h, innen),
                    "Durchgang auf Ebene " + h);
        }
        assertEquals(Torhausplan.Stueck.MAUER,
                tor.stueckBei(72, Stadtplan.BODEN_Y + 6, innen), "die Decke");
        assertEquals(Torhausplan.Stueck.BODEN,
                tor.stueckBei(72, Stadtplan.BODEN_Y, innen), "der Boden");

        // UND ER ENDET: Sechs Bloecke tief, dann Mauer. DAS ist der ganze Sinn - die
        // Stadtmauer bleibt geschlossen.
        for (int q = 0; q < 6; q++) {
            assertEquals(Torhausplan.Stueck.LUFT,
                    tor.stueckBei(72, Stadtplan.BODEN_Y + 1, innen - q),
                    "Durchgang in der Tiefe " + q);
        }
        for (int q = 6; q < 9; q++) {
            assertEquals(Torhausplan.Stueck.MAUER,
                    tor.stueckBei(72, Stadtplan.BODEN_Y + 1, innen - q),
                    "dahinter Mauer, Tiefe " + q);
        }
    }

    @Test
    @DisplayName("Die Nische ist das Tor - drei mal drei, mit Scheibe und Diorama dahinter")
    void dieNische() {
        Torhausplan tor = nord();
        Quader links = tor.nische(true);
        Quader rechts = tor.nische(false);

        // EIN BLOCK DICK, drei hoch, drei tief - genau die Form eines Weltentors.
        assertEquals(1, links.bisX() - links.vonX() + 1, "einen Block dick");
        assertEquals(3, links.bisY() - links.vonY() + 1, "drei hoch");
        assertEquals(3, links.bisZ() - links.vonZ() + 1, "drei tief");
        assertEquals(Stadtplan.BODEN_Y + 1, links.vonY(),
                "sie beginnt auf der Lauf-Ebene - wer hineingeht, springt nicht");
        assertNotEquals(links.alsText(), rechts.alsText(), "zwei verschiedene Nischen");

        // JEDER BLOCK DER NISCHE IST WIRKLICH OFFEN, und unter ihr liegt Boden - sonst
        // klagt die Ankunftspruefung beim Start.
        for (int x = links.vonX(); x <= links.bisX(); x++) {
            for (int y = links.vonY(); y <= links.bisY(); y++) {
                for (int z = links.vonZ(); z <= links.bisZ(); z++) {
                    assertEquals(Torhausplan.Stueck.NISCHE, tor.stueckBei(x, y, z),
                            "Nische bei " + x + "/" + y + "/" + z);
                }
            }
        }
        for (int z = links.vonZ(); z <= links.bisZ(); z++) {
            assertEquals(Torhausplan.Stueck.BODEN,
                    tor.stueckBei(links.vonX(), Stadtplan.BODEN_Y, z),
                    "unter der Nische ist Boden");
        }

        // HINTER DER NISCHE: erst die Scheibe, dann das Diorama. Die Scheibe ist ein
        // Riegel und kein Schmuck - ohne sie waere beim Nether Lava eine Armlaenge weg.
        assertEquals(Torhausplan.Stueck.SCHEIBE,
                tor.stueckBei(links.vonX() - 1, links.vonY(), links.vonZ()),
                "die Scheibe");
        assertEquals(Torhausplan.Stueck.DIORAMA,
                tor.stueckBei(links.vonX() - 2, links.vonY(), links.vonZ()),
                "das Diorama");
        // UND DAHINTER MAUERWERK: Die Aussenflaeche des Turms grenzt an die Gasse
        // am Wehrgang. Ein Diorama bis dorthin liesse seine Lava hinauslaufen.
        assertEquals(Torhausplan.Stueck.MAUER,
                tor.stueckBei(links.vonX() - 3, links.vonY(), links.vonZ()),
                "das Diorama ist nach aussen dicht");
        // Die rechte Nische spiegelt das.
        assertEquals(Torhausplan.Stueck.SCHEIBE,
                tor.stueckBei(rechts.vonX() + 1, rechts.vonY(), rechts.vonZ()),
                "die Scheibe rechts");
        assertEquals(Torhausplan.Stueck.DIORAMA,
                tor.stueckBei(rechts.vonX() + 2, rechts.vonY(), rechts.vonZ()),
                "das Diorama rechts");
        assertEquals(Torhausplan.Stueck.MAUER,
                tor.stueckBei(rechts.vonX() + 3, rechts.vonY(), rechts.vonZ()),
                "und auch rechts dicht");

        // UND DIE NISCHE ZEIGT NACH AUSSEN, in die Wand hinein.
        assertEquals(Richtung.WEST, tor.nischeZeigt(true), "links schaut nach Westen");
        assertEquals(Richtung.OST, tor.nischeZeigt(false), "rechts nach Osten");
        Torhausplan osten = Torhausplan.inDerMauer(Stadtplan.altfurt(), Richtung.OST);
        assertEquals(Richtung.NORD, osten.nischeZeigt(true), "am Osttor nach Norden");
        assertEquals(Richtung.SUED, osten.nischeZeigt(false), "und nach Sueden");
    }

    @Test
    @DisplayName("Jede Nische liegt im Torhaus - und keine im Durchgang")
    void nischenImHaus() {
        Stadtplan stadt = Stadtplan.altfurt();
        for (Richtung seite : Richtung.values()) {
            Torhausplan tor = Torhausplan.inDerMauer(stadt, seite);
            Quader haus = tor.bereich();
            for (boolean links : new boolean[] {true, false}) {
                Quader nische = tor.nische(links);
                assertTrue(nische.vonX() >= haus.vonX() && nische.bisX() <= haus.bisX()
                                && nische.vonY() >= haus.vonY()
                                && nische.bisY() <= haus.bisY()
                                && nische.vonZ() >= haus.vonZ()
                                && nische.bisZ() <= haus.bisZ(),
                        seite.wort() + ": " + nische.alsText() + " liegt nicht in "
                                + haus.alsText());
            }
            // UND DIE ZWEI NISCHEN EINES HAUSES BERUEHREN SICH NICHT.
            assertTrue(!tor.nische(true).schneidet(tor.nische(false)),
                    seite.wort() + ": die Nischen ueberschneiden sich");
        }
    }

    @Test
    @DisplayName("Die Tuerme ragen ueber das Dach und tragen die Brustwehr")
    void dieTuerme() {
        Torhausplan tor = nord();
        Quader b = tor.bereich();
        int oben = Stadtplan.BODEN_Y + Torhausplan.HOEHE - 1;

        // AUF DER OBERSTEN EBENE steht NUR noch Turm - in der Mitte ist Luft.
        assertEquals(Torhausplan.Stueck.LUFT, tor.stueckBei(72, oben, b.bisZ()),
                "ueber dem First ist Luft");
        Set<Torhausplan.Stueck> aufDenTuermen = new HashSet<>();
        for (int x = b.vonX(); x < b.vonX() + Torhausplan.TURM_BREITE; x++) {
            aufDenTuermen.add(tor.stueckBei(x, oben, b.bisZ()));
        }
        assertTrue(aufDenTuermen.contains(Torhausplan.Stueck.ZINNE),
                "die Brustwehr steht: " + aufDenTuermen);
        assertTrue(aufDenTuermen.contains(Torhausplan.Stueck.LUFT),
                "und sie hat Luecken: " + aufDenTuermen);

        // DIE ZINNEN FOLGEN DER REGEL DER STADTMAUER - nicht einer zweiten. Zwei
        // Muster nebeneinander sieht man.
        for (int x = b.vonX(); x <= b.bisX(); x++) {
            for (int z = b.vonZ(); z <= b.bisZ(); z++) {
                if (tor.stueckBei(x, oben, z) == Torhausplan.Stueck.ZINNE) {
                    assertTrue(Stadtplan.zinne(x, z),
                            "Zinne bei " + x + "/" + z + " folgt nicht der Mauerregel");
                }
            }
        }

        // DIE ECKEN sind Pfeiler - anderer Stein, damit die Kante sichtbar ist.
        assertEquals(Torhausplan.Stueck.PFEILER,
                tor.stueckBei(b.vonX(), Stadtplan.BODEN_Y + 1, b.vonZ()), "Ecke 1");
        assertEquals(Torhausplan.Stueck.PFEILER,
                tor.stueckBei(b.bisX(), Stadtplan.BODEN_Y + 1, b.bisZ()), "Ecke 2");
        assertEquals(Torhausplan.Stueck.MAUER,
                tor.stueckBei(b.vonX() + 1, Stadtplan.BODEN_Y + 1, b.vonZ()),
                "daneben nur Mauer");
    }

    @Test
    @DisplayName("Das Dach zeigt bergauf - und von der Stadt sieht man den Giebel")
    void dasDach() {
        Torhausplan tor = nord();
        Quader b = tor.bereich();
        int innen = b.bisZ();

        // DER GIEBEL: Auf der Stadtseite zieht sich das Dach in der BREITE ein. Also
        // wird die Reihe je Ebene kuerzer.
        Map<Integer, Integer> breiten = new java.util.TreeMap<>();
        for (int h = Torhausplan.DACH_AB;
                h < Torhausplan.DACH_AB + Torhausplan.DACH_EBENEN; h++) {
            int wieViele = 0;
            for (int x = b.vonX(); x <= b.bisX(); x++) {
                Torhausplan.Stueck stueck = tor.stueckBei(x, Stadtplan.BODEN_Y + h, innen);
                if (stueck == Torhausplan.Stueck.DACH
                        || stueck == Torhausplan.Stueck.FIRST) {
                    wieViele++;
                }
            }
            breiten.put(h, wieViele);
        }
        assertEquals(3, breiten.size(), "drei Dachebenen");
        assertEquals(7, breiten.get(Torhausplan.DACH_AB), "unten sieben breit");
        assertEquals(5, breiten.get(Torhausplan.DACH_AB + 1), "dann fuenf");
        assertEquals(3, breiten.get(Torhausplan.DACH_AB + 2), "dann drei - der First");

        // DIE TREPPEN ZEIGEN ZUM FIRST, nicht alle nach Norden: Die linke Kante nach
        // Osten, die rechte nach Westen.
        int linkeKante = b.vonX() + Torhausplan.TURM_BREITE;
        int rechteKante = b.bisX() - Torhausplan.TURM_BREITE;
        assertEquals(Richtung.OST,
                tor.dachzeigt(linkeKante, Stadtplan.BODEN_Y + Torhausplan.DACH_AB, innen),
                "linke Kante bergauf nach Osten");
        assertEquals(Richtung.WEST,
                tor.dachzeigt(rechteKante, Stadtplan.BODEN_Y + Torhausplan.DACH_AB, innen),
                "rechte Kante bergauf nach Westen");
        // Am Osttorhaus laeuft es quer dazu.
        Torhausplan osten = Torhausplan.inDerMauer(Stadtplan.altfurt(), Richtung.OST);
        Quader ob = osten.bereich();
        assertEquals(Richtung.SUED,
                osten.dachzeigt(ob.bisX(), Stadtplan.BODEN_Y + Torhausplan.DACH_AB,
                        ob.vonZ() + Torhausplan.TURM_BREITE),
                "am Osttor zeigt die erste Kante nach Sueden");

        // WO KEIN DACH IST, IST AUCH KEINE RICHTUNG.
        assertNull(tor.dachzeigt(72, Stadtplan.BODEN_Y + 1, innen), "im Durchgang");
        assertNull(tor.dachzeigt(b.vonX(), Stadtplan.BODEN_Y + 1, innen), "im Turm");
        assertNull(tor.dachzeigt(0, 0, 0), "weit weg");
    }

    @Test
    @DisplayName("Eine Stelle ausserhalb des Torhauses ist ein Programmfehler")
    void ausserhalbWirft() {
        Torhausplan tor = nord();
        Quader b = tor.bereich();

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> tor.stueckBei(b.vonX() - 1, Stadtplan.BODEN_Y, b.vonZ()))
                .getMessage().contains("liegt nicht im Torhaus"));
        assertThrows(IllegalArgumentException.class,
                () -> tor.stueckBei(b.vonX(), b.bisY() + 1, b.vonZ()));
        assertThrows(IllegalArgumentException.class,
                () -> tor.stueckBei(b.vonX(), Stadtplan.BODEN_Y, b.bisZ() + 1));
    }

    @Test
    @DisplayName("Ohne Seite und ohne Ring gibt es kein Torhaus")
    void ohneSeite() {
        assertThrows(IllegalArgumentException.class, () -> new Torhausplan(null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new Torhausplan(Richtung.NORD, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Torhausplan.inDerMauer(null, Richtung.NORD));
    }

    @Test
    @DisplayName("Jeder Block des Torhauses hat genau ein Stueck")
    void jederBlockEinStueck() {
        Torhausplan tor = nord();
        Quader b = tor.bereich();
        Map<Torhausplan.Stueck, Integer> zahl =
                new EnumMap<>(Torhausplan.Stueck.class);
        for (int x = b.vonX(); x <= b.bisX(); x++) {
            for (int y = b.vonY(); y <= b.bisY(); y++) {
                for (int z = b.vonZ(); z <= b.bisZ(); z++) {
                    zahl.merge(tor.stueckBei(x, y, z), 1, Integer::sum);
                }
            }
        }
        // GEZAEHLT: 15 x 9 x 11 Bloecke, und jeder genau einmal.
        int gesamt = 0;
        for (int wieViele : zahl.values()) {
            gesamt += wieViele;
        }
        assertEquals(15 * 9 * 11, gesamt, "jeder Block einmal");
        // Der Boden ist die ganze Grundflaeche.
        assertEquals(15 * 9, zahl.get(Torhausplan.Stueck.BODEN), "der Boden");
        // Zwei Nischen von je 3 mal 3.
        assertEquals(2 * 3 * 3, zahl.get(Torhausplan.Stueck.NISCHE), "zwei Nischen");
        assertEquals(2 * 3 * 3, zahl.get(Torhausplan.Stueck.SCHEIBE), "zwei Scheiben");
        assertEquals(2 * 3 * 3, zahl.get(Torhausplan.Stueck.DIORAMA),
                "zwei Dioramen von je einer Ebene Tiefe - dahinter ist Mauer");
        // Und es gibt von jedem Stueck etwas - ein Stueck ohne Vorkommen waere eine
        // Form, die nie gebaut wird.
        for (Torhausplan.Stueck stueck : Torhausplan.Stueck.values()) {
            assertTrue(zahl.getOrDefault(stueck, 0) > 0,
                    stueck + " kommt im Torhaus nicht vor");
        }
    }
}
