package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Geometrie von Hafenwerder.
 *
 * <p>Die Proben rechnen die Kuestenlinie NICHT mit derselben Formel nach, die sie
 * pruefen. Sie fragen stattdessen nach Eigenschaften, die ein Spieler merkt: Steht meine
 * Insel ganz auf meinem Feld? Liegt zwischen mir und dem Nachbarn Wasser? Komme ich vom
 * Strand ins Wasser, ohne zu fallen? Und bekommt jeder dieselbe Insel?
 */
@DisplayName("Die Geometrie von Hafenwerder")
class InselplanTest {

    private final Inselplan plan = Inselplan.hafenwerder();

    @Test
    @DisplayName("Inselfeld 128, Meer 128, Rasterweite 256 - und acht Bloecke Tiefe")
    void dieMasse() {
        assertEquals(128, plan.feldKante());
        assertEquals(128, plan.meerBreite());
        assertEquals(256, plan.rasterWeite());
        assertEquals(56, plan.kuesteRadius());
        assertEquals(4, plan.strandBreite());
        assertEquals(8, plan.meerTiefe(),
                "abgeleitet: halbe Feldkante minus Kuestenradius, also 64 - 56");
        assertEquals(63, plan.ankunftsMitte());
        assertEquals(plan.feldMitte(Inselplan.ANKUNFTSINSEL), plan.ankunftsMitte());
    }

    @Test
    @DisplayName("Das Feld ist das Eigentum - und dazwischen liegt offenes Meer")
    void feldUndMeer() {
        assertEquals(Inselplan.Feld.INSELFELD, plan.feldBei(0, 0));
        assertEquals(Inselplan.Feld.INSELFELD, plan.feldBei(127, 127));
        assertEquals(Inselplan.Feld.OFFENES_MEER, plan.feldBei(128, 63),
                "ein Block weiter, und es gehoert niemandem mehr");
        assertEquals(Inselplan.Feld.OFFENES_MEER, plan.feldBei(63, 128));
        assertEquals(Inselplan.Feld.OFFENES_MEER, plan.feldBei(200, 200));
        assertEquals(Inselplan.Feld.INSELFELD, plan.feldBei(256, 256),
                "und dann faengt das naechste Feld an");
        assertEquals(Inselplan.Feld.INSELFELD, plan.feldBei(-129, -129),
                "im Negativen ebenso: -129 ist der letzte Block des FELDES -1");
        assertEquals(Inselplan.Feld.OFFENES_MEER, plan.feldBei(-128, -129),
                "und von -128 bis -1 liegt dessen Meer - der Zwischenraum gehoert zum"
                        + " Feld davor, auch im Negativen");
    }

    @Test
    @DisplayName("Zwischen zwei Inseln liegt durchgehend Wasser - keine gemeinsame Kante")
    void zwischenZweiInselnIstWasser() {
        // Der erste der drei Gruende aus 6.4: Ohne gemeinsame Kante gibt es keinen
        // Grenzstreit und niemanden, der an die Mauer des Nachbarn baut. Gegangen wird
        // die ganze Strecke von der Mitte einer Insel zur Mitte der naechsten.
        int mitte = plan.feldMitte(0);
        int letzterStrand = -1;
        int ersterStrandDrueben = -1;
        for (int x = mitte; x <= plan.feldMitte(1); x++) {
            if (plan.gestaltBei(x, mitte) != Inselplan.Gestalt.WASSER) {
                if (x < plan.feldAnfang(1)) {
                    letzterStrand = x;
                } else if (ersterStrandDrueben < 0) {
                    ersterStrandDrueben = x;
                }
            }
        }

        assertEquals(118, letzterStrand, "soweit reicht die eigene Insel nach Osten");
        assertEquals(265, ersterStrandDrueben, "und dort faengt die naechste an");
        for (int x = letzterStrand + 1; x < ersterStrandDrueben; x++) {
            assertEquals(Inselplan.Gestalt.WASSER, plan.gestaltBei(x, mitte),
                    "bei " + x + " muesste Wasser sein");
        }
    }

    @Test
    @DisplayName("Die Insel beruehrt ihr Feld nie - rundherum eigenes Wasser")
    void dieInselBeruehrtIhrFeldNie() {
        // Ein Strand, der genau auf der Eigentumsgrenze endet, waere ein Strand, an dem
        // jeder Schritt ins Wasser ein Schritt in fremdes Gebiet ist. Geprueft wird der
        // ganze Rand des Feldes, alle vier Seiten.
        for (int i = 0; i < 128; i++) {
            assertEquals(Inselplan.Gestalt.WASSER, plan.gestaltBei(0, i),
                    "Westrand bei z=" + i);
            assertEquals(Inselplan.Gestalt.WASSER, plan.gestaltBei(127, i),
                    "Ostrand bei z=" + i);
            assertEquals(Inselplan.Gestalt.WASSER, plan.gestaltBei(i, 0),
                    "Nordrand bei x=" + i);
            assertEquals(Inselplan.Gestalt.WASSER, plan.gestaltBei(i, 127),
                    "Suedrand bei x=" + i);
        }
    }

    @Test
    @DisplayName("Jedes Feld traegt DIESELBE Insel - darum passt ein Festpreis")
    void jedesFeldTraegtDieselbeInsel() {
        // Der dritte Grund aus 6.4, und der eigentliche Grund fuer die gebaute Welt:
        // Laege die eine Insel im Flachwasser und die naechste halb im Berg, koennte ein
        // Festpreis von 75.000 Mark nicht fuer beide derselbe sein.
        for (int x = 0; x < 128; x += 7) {
            for (int z = 0; z < 128; z += 7) {
                Inselplan.Gestalt hier = plan.gestaltBei(x, z);
                assertEquals(hier, plan.gestaltBei(x + 256, z),
                        "ein Feld nach Osten, " + x + "/" + z);
                assertEquals(hier, plan.gestaltBei(x, z + 256 * 3),
                        "drei Felder nach Sueden, " + x + "/" + z);
                assertEquals(hier, plan.gestaltBei(x - 256 * 9, z - 256 * 9),
                        "und bis in die aeusserste Ecke der Welt, " + x + "/" + z);
            }
        }
    }

    @Test
    @DisplayName("Die Insel ist spiegelbildlich - auf beiden Achsen")
    void spiegelbildlich() {
        // Darum wird in HALBEN Bloecken gemessen: Bei gerader Feldkante gibt es keinen
        // Mittelblock. Ganze Bloecke von Block 63 aus gezaehlt machten die Insel um einen
        // Block unsymmetrisch - neun Bloecke Hafen auf der einen Seite, acht auf der
        // anderen - und ihren Fuss einen Block lang in fremdem Wasser.
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z += 11) {
                assertEquals(plan.gestaltBei(x, z), plan.gestaltBei(127 - x, z),
                        "Ost gegen West bei " + x + "/" + z);
                assertEquals(plan.gestaltBei(z, x), plan.gestaltBei(z, 127 - x),
                        "Nord gegen Sued bei " + z + "/" + x);
            }
        }
    }

    @Test
    @DisplayName("Der Strand liegt zwischen Wiese und Wasser - ueberall")
    void derStrandLiegtDazwischen() {
        // Kein Grasblock darf unmittelbar am Wasser liegen: Das ist die Zusage, die den
        // Strand ueberhaupt zu einem macht. Geprueft wird jeder Landblock gegen seine
        // vier Nachbarn.
        for (int x = 1; x < 127; x++) {
            for (int z = 1; z < 127; z++) {
                if (plan.gestaltBei(x, z) != Inselplan.Gestalt.LAND) {
                    continue;
                }
                assertNichtAmWasser(x + 1, z);
                assertNichtAmWasser(x - 1, z);
                assertNichtAmWasser(x, z + 1);
                assertNichtAmWasser(x, z - 1);
            }
        }
    }

    @Test
    @DisplayName("Der Strand ist vier Bloecke breit, in der Mitte gemessen")
    void derStrandIstVierBloeckeBreit() {
        int mitte = plan.feldMitte(0);
        int strand = 0;
        for (int x = 0; x < 128; x++) {
            if (plan.gestaltBei(x, mitte) == Inselplan.Gestalt.STRAND) {
                strand++;
            }
        }

        assertEquals(8, strand, "vier Bloecke auf jeder Seite der Insel");
    }

    @Test
    @DisplayName("Der Hang faellt vom Strand bis zum Rand des eigenen Feldes")
    void derHangFaelltBisZumFeldrand() {
        // Die Gestalt der Insel unter Wasser, und sie haengt an EINER Zahl: Der Hang
        // faellt um einen Block, sooft er einen Block nach aussen geht. Deshalb ist der
        // Grund am Feldrand genauso tief wie im offenen Meer - ohne Stufe, ohne
        // Riffwand.
        int mitte = plan.feldMitte(0);

        assertEquals(0, plan.tiefeDerOberflaeche(mitte, mitte), "mitten auf der Insel");
        assertEquals(0, plan.tiefeDerOberflaeche(118, mitte), "der letzte Strandblock");
        assertEquals(1, plan.tiefeDerOberflaeche(119, mitte), "und einen Schritt weiter");
        assertEquals(8, plan.tiefeDerOberflaeche(126, mitte), "am Rand des Feldes");
        assertEquals(8, plan.tiefeDerOberflaeche(200, mitte), "und im offenen Meer");
        assertEquals(8, plan.tiefeDerOberflaeche(0, 0), "auch in der Ecke des Feldes");

        // Streng einen Block je Block: zwischen zwei Nachbarn liegt nie eine Stufe.
        for (int x = 1; x < 256; x++) {
            int hier = plan.tiefeDerOberflaeche(x, mitte);
            int davor = plan.tiefeDerOberflaeche(x - 1, mitte);
            assertTrue(Math.abs(hier - davor) <= 1,
                    "bei " + x + " springt die Tiefe von " + davor + " auf " + hier);
        }
    }

    @Test
    @DisplayName("Tiefe 0 heisst Insel, und Insel heisst Tiefe 0 - beide Richtungen")
    void tiefeNullIstDieWasserlinie() {
        for (int x = 0; x < 128; x += 3) {
            for (int z = 0; z < 128; z += 3) {
                boolean trocken = plan.gestaltBei(x, z) != Inselplan.Gestalt.WASSER;
                assertEquals(trocken, plan.tiefeDerOberflaeche(x, z) == 0,
                        "bei " + x + "/" + z);
                assertEquals(trocken, plan.inselInTiefe(x, z, 0), "bei " + x + "/" + z);
            }
        }
    }

    @Test
    @DisplayName("Der Hang WAECHST nach unten - er wird nie schmaler")
    void derHangWaechstNachUnten() {
        // Darauf beruht, dass die Suche nach dem Gipfel von oben her abbrechen darf:
        // Die erste Tiefe mit Insel ist der Gipfel. Wuerde der Hang irgendwo wieder
        // schmaler, waere die Spalte darunter hohl - und niemand saehe es, weil sie
        // unter Wasser liegt.
        for (int x = 0; x < 128; x += 3) {
            for (int z = 0; z < 128; z += 3) {
                boolean vorher = plan.inselInTiefe(x, z, 0);
                for (int tiefe = 1; tiefe <= plan.meerTiefe(); tiefe++) {
                    boolean jetzt = plan.inselInTiefe(x, z, tiefe);
                    assertTrue(!vorher || jetzt,
                            "bei " + x + "/" + z + " verschwindet die Insel in Tiefe "
                                    + tiefe);
                    vorher = jetzt;
                }
            }
        }
    }

    @Test
    @DisplayName("Unter dem Meeresgrund und ueber der Wasserlinie ist keine Insel")
    void jenseitsDerTiefenIstNichts() {
        int mitte = plan.feldMitte(0);

        assertFalse(plan.inselInTiefe(mitte, mitte, -1),
                "ueber der Wasserlinie faengt die Luft an");
        assertFalse(plan.inselInTiefe(mitte, mitte, plan.meerTiefe() + 1),
                "und unter dem Grund rechnet dieser Plan nicht weiter");
        assertFalse(plan.inselInTiefe(200, 200, 4), "im offenen Meer nie");
    }

    @Test
    @DisplayName("Die Ankunftsinsel ist Feld 0/0 und sonst keines")
    void dieAnkunftsinsel() {
        assertTrue(plan.istAnkunftsinsel(0, 0));
        assertFalse(plan.istAnkunftsinsel(0, 1));
        assertFalse(plan.istAnkunftsinsel(-1, 0));
        assertEquals(Inselplan.Gestalt.LAND,
                plan.gestaltBei(plan.ankunftsMitte(), plan.ankunftsMitte()),
                "und in ihrer Mitte ist Land - dort landet das Tor aus Altfurt");
    }

    @Test
    @DisplayName("Eine Insel, die nicht auf ihr Feld passt, gibt es nicht")
    void riegel() {
        Feldrechnung raster = new Feldrechnung(128, 128);

        assertThrows(IllegalArgumentException.class,
                () -> new Inselplan(raster, 65, 4),
                "Radius 65 im 128er Feld - die Insel laege zum Teil im offenen Meer");
        assertThrows(IllegalArgumentException.class, () -> new Inselplan(raster, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new Inselplan(raster, 56, 0),
                "ohne Strand");
        assertThrows(IllegalArgumentException.class, () -> new Inselplan(raster, 56, 56),
                "ein Strand so breit wie die Insel ist keine Insel");
        assertEquals(0, new Inselplan(raster, 64, 4).meerTiefe(),
                "genau die halbe Feldkante ist erlaubt - dann gibt es aber kein Meer");
    }

    private void assertNichtAmWasser(int x, int z) {
        assertFalse(plan.gestaltBei(x, z) == Inselplan.Gestalt.WASSER,
                "Wiese bei " + x + "/" + z + " liegt unmittelbar am Wasser - dort"
                        + " muesste Strand sein");
    }
}
